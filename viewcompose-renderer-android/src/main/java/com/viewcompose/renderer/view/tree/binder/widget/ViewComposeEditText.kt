package com.viewcompose.renderer.view.tree

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.ReceivedContent
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.renderer.view.container.PagerPageHostLayout

/**
 * Android EditText host for ViewCompose text input, intercepting selection, IME, read-only, and
 * receive-content events.
 */
internal class ViewComposeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatEditText(context, attrs) {
    private var initializedController: AndroidTextFieldController? = null
    private var readOnlyMode: Boolean = false
    private var visibilityCoordinator: FocusedEditorVisibilityCoordinator? = null

    internal val textController: AndroidTextFieldController
        get() = checkNotNull(initializedController) {
            "Text controller is unavailable during EditText construction."
        }

    init {
        initializedController = AndroidTextFieldController(this)
    }

    override fun onSelectionChanged(
        selStart: Int,
        selEnd: Int,
    ) {
        super.onSelectionChanged(selStart, selEnd)
        initializedController?.onSelectionChanged()
    }

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): InputConnection? {
        if (readOnlyMode) return null
        initializedController?.configureEditorInfo(outAttrs)
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        val receiveContentConnection = InputConnectionCompat.createWrapper(
            this,
            connection,
            outAttrs,
        )
        return initializedController?.wrapInputConnection(receiveContentConnection)
            ?: receiveContentConnection
    }

    override fun onCheckIsTextEditor(): Boolean {
        return !readOnlyMode && super.onCheckIsTextEditor()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (
            readOnlyMode &&
            (
                event.isPrintingKey ||
                    keyCode == KeyEvent.KEYCODE_DEL ||
                    keyCode == KeyEvent.KEYCODE_FORWARD_DEL
                )
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (
            readOnlyMode &&
            (
                id == android.R.id.cut ||
                    id == android.R.id.paste ||
                    id == android.R.id.pasteAsPlainText
                )
        ) {
            return false
        }
        return super.onTextContextMenuItem(id)
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?,
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            visibilityCoordinator = FocusedEditorVisibilityCoordinator.activate(this)
        } else {
            visibilityCoordinator?.deactivate(this)
            visibilityCoordinator = null
        }
        initializedController?.onFocusChanged(focused)
    }

    override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean): Boolean {
        val pageBoundary = nearestPagerPageBoundary()
            ?: return super.requestRectangleOnScreen(rectangle, immediate)
        return pageBoundary.requestDescendantRectangleOnScreen(
            descendant = this,
            rectangle = rectangle,
            immediate = immediate,
        )
    }

    private fun nearestPagerPageBoundary(): PagerPageHostLayout? {
        var ancestor = parent
        while (ancestor is View) {
            if (ancestor is PagerPageHostLayout) return ancestor
            ancestor = ancestor.parent
        }
        return null
    }

    override fun onDetachedFromWindow() {
        visibilityCoordinator?.deactivate(this)
        visibilityCoordinator = null
        super.onDetachedFromWindow()
    }

    internal fun setReadOnlyMode(readOnly: Boolean) {
        readOnlyMode = readOnly
    }
}

/**
 * Coordinates bidirectional synchronization between TextFieldState and EditText while isolating
 * recursive updates caused by framework callbacks.
 */
internal class AndroidTextFieldController(
    private val view: ViewComposeEditText,
) {
    private var state: TextFieldState? = null
    private var inputTransformation: InputTransformation? = null
    private var onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null
    private var imeAction: TextFieldImeAction = TextFieldImeAction.Default
    private var onFocusChange: ((Boolean) -> Unit)? = null
    private var receiveContent: ReceiveContentConfiguration =
        ReceiveContentConfiguration.Default
    private var readOnly: Boolean = false
    private var installedMimeTypes: Set<String>? = null
    private var applyingFrameworkValue: Boolean = false
    private var inputMutationDepth: Int = 0
    private var batchEditDepth: Int = 0
    private var pendingInputSync: Boolean = false

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int,
        ) = Unit

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int,
        ) = Unit

        override fun afterTextChanged(s: Editable?) {
            requestInputSync()
        }
    }

    init {
        view.addTextChangedListener(textWatcher)
        view.setOnEditorActionListener { _, _, _ ->
            onKeyboardAction?.invoke(imeAction) ?: false
        }
    }

    fun bind(spec: InputViewBinder.TextFieldSpec) {
        state = spec.state
        inputTransformation = spec.inputTransformation
        onKeyboardAction = spec.onKeyboardAction
        imeAction = spec.imeAction
        onFocusChange = spec.onFocusChange
        receiveContent = spec.receiveContent
        readOnly = spec.readOnly
        installReceiveContentListener(spec.receiveContent)
        if (InputViewBinder.readTextFieldValue(view) != spec.value) {
            applyFrameworkValue(spec.value)
        }
    }

    fun updateEditorConfiguration(
        inputType: Int,
        editorOptions: Int,
    ) {
        val changed = view.inputType != inputType || view.imeOptions != editorOptions
        if (!changed) return
        applyingFrameworkValue = true
        try {
            if (view.inputType != inputType) {
                view.inputType = inputType
            }
            if (view.imeOptions != editorOptions) {
                view.imeOptions = editorOptions
            }
        } finally {
            applyingFrameworkValue = false
        }
        if (view.isFocused) {
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.restartInput(view)
        }
    }

    fun onSelectionChanged() {
        requestInputSync()
    }

    fun onFocusChanged(focused: Boolean) {
        onFocusChange?.invoke(focused)
    }

    fun wrapInputConnection(connection: InputConnection): InputConnection {
        return ObservingInputConnection(
            target = connection,
            controller = this,
        )
    }

    fun configureEditorInfo(editorInfo: android.view.inputmethod.EditorInfo) {
        EditorInfoCompat.setContentMimeTypes(
            editorInfo,
            receiveContent.mimeTypes.toTypedArray(),
        )
    }

    private fun requestInputSync() {
        if (applyingFrameworkValue || state == null) return
        if (inputMutationDepth > 0 || batchEditDepth > 0) {
            pendingInputSync = true
            return
        }
        syncFromView()
    }

    private fun syncFromView() {
        val currentState = state ?: return
        pendingInputSync = false
        val proposed = InputViewBinder.readTextFieldValue(view)
        val accepted = currentState.updateFromInput(
            proposedValue = proposed,
            inputTransformation = inputTransformation,
        )
        if (accepted != proposed) {
            applyFrameworkValue(accepted)
        }
    }

    private fun applyFrameworkValue(value: com.viewcompose.text.TextFieldValue) {
        applyingFrameworkValue = true
        try {
            InputViewBinder.applyTextFieldValue(view, value)
        } finally {
            applyingFrameworkValue = false
        }
    }

    private fun installReceiveContentListener(configuration: ReceiveContentConfiguration) {
        if (installedMimeTypes == configuration.mimeTypes) return
        installedMimeTypes = configuration.mimeTypes
        ViewCompat.setOnReceiveContentListener(
            view,
            configuration.mimeTypes.toTypedArray(),
        ) { _, payload ->
            handleReceiveContent(payload)
        }
    }

    private fun handleReceiveContent(payload: ContentInfoCompat): ContentInfoCompat? {
        val currentState = state ?: return payload
        if (readOnly || !view.isEnabled) return payload
        val converted = AndroidTextDocumentAdapter.convertContent(
            context = view.context,
            payload = payload,
        )
        val receivedDocument = converted.document ?: return payload
        val transformed = receiveContent.transformation?.transform(
            ReceivedContent(
                document = receivedDocument,
                source = converted.source,
                mimeTypes = converted.mimeTypes,
                platformItemCount = converted.platformItemCount,
            ),
        ) ?: if (receiveContent.transformation == null) {
            receivedDocument
        } else {
            return payload
        }
        val current = currentState.value
        val insertionStart = current.selection.min
        val nextDocument = current.document.replace(
            range = current.selection,
            replacement = transformed,
        )
        val proposed = TextFieldValue(
            document = nextDocument,
            selection = TextRange(insertionStart + transformed.text.length),
            composition = null,
        )
        val accepted = currentState.updateFromInput(
            proposedValue = proposed,
            inputTransformation = inputTransformation,
        )
        applyFrameworkValue(accepted)
        return AndroidTextDocumentAdapter.remainingContent(
            payload = payload,
            consumedIndices = converted.consumedIndices,
        )
    }

    private inline fun <T> inputMutation(block: () -> T): T {
        inputMutationDepth += 1
        pendingInputSync = true
        return try {
            block()
        } finally {
            inputMutationDepth -= 1
            if (inputMutationDepth == 0 && batchEditDepth == 0) {
                syncFromView()
            }
        }
    }

    private fun beginBatchEdit() {
        batchEditDepth += 1
    }

    private fun endBatchEdit() {
        batchEditDepth = (batchEditDepth - 1).coerceAtLeast(0)
        if (batchEditDepth == 0 && pendingInputSync) {
            syncFromView()
        }
    }

    private class ObservingInputConnection(
        target: InputConnection,
        private val controller: AndroidTextFieldController,
    ) : InputConnectionWrapper(target, false) {
        override fun beginBatchEdit(): Boolean {
            controller.beginBatchEdit()
            val accepted = super.beginBatchEdit()
            if (!accepted) {
                controller.endBatchEdit()
            }
            return accepted
        }

        override fun endBatchEdit(): Boolean {
            return try {
                super.endBatchEdit()
            } finally {
                controller.endBatchEdit()
            }
        }

        override fun commitText(
            text: CharSequence?,
            newCursorPosition: Int,
        ): Boolean = controller.inputMutation {
            super.commitText(text, newCursorPosition)
        }

        override fun setComposingText(
            text: CharSequence?,
            newCursorPosition: Int,
        ): Boolean = controller.inputMutation {
            super.setComposingText(text, newCursorPosition)
        }

        override fun setComposingRegion(
            start: Int,
            end: Int,
        ): Boolean = controller.inputMutation {
            super.setComposingRegion(start, end)
        }

        override fun finishComposingText(): Boolean = controller.inputMutation {
            super.finishComposingText()
        }

        override fun setSelection(
            start: Int,
            end: Int,
        ): Boolean = controller.inputMutation {
            super.setSelection(start, end)
        }

        override fun deleteSurroundingText(
            beforeLength: Int,
            afterLength: Int,
        ): Boolean = controller.inputMutation {
            super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun deleteSurroundingTextInCodePoints(
            beforeLength: Int,
            afterLength: Int,
        ): Boolean = controller.inputMutation {
            super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
        }

        override fun commitCompletion(text: CompletionInfo?): Boolean = controller.inputMutation {
            super.commitCompletion(text)
        }

        override fun commitCorrection(correctionInfo: CorrectionInfo): Boolean =
            controller.inputMutation {
                super.commitCorrection(correctionInfo)
            }

        override fun sendKeyEvent(event: KeyEvent): Boolean = controller.inputMutation {
            super.sendKeyEvent(event)
        }
    }
}
