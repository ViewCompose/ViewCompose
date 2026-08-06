package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.renderer.view.tree.patch.InputNodePatchApplier

/**
 * Registers binder and patch descriptors for text input, toggle, and slider nodes.
 * Registers binder/patch descriptors for text input, toggle, and slider nodes.
 */
internal fun MutableList<NodeBinderDescriptor>.addInputNodeBinderDescriptors() {
    val textFieldPatch = patchDescriptor<TextFieldNodeProps, TextFieldNodePatch>(
        factory = { previous, next -> TextFieldNodePatch(previous, next) },
        apply = { view, patch ->
            InputNodePatchApplier.applyTextFieldPatch(
                view = view as ViewComposeEditText,
                patch = patch,
            )
        },
    )
    val togglePatch = patchDescriptor<ToggleNodeProps, ToggleNodePatch>(
        factory = { previous, next -> ToggleNodePatch(previous, next) },
        apply = { view, patch ->
            InputNodePatchApplier.applyTogglePatch(
                view = view as android.widget.CompoundButton,
                patch = patch,
            )
        },
    )
    val sliderPatch = patchDescriptor<SliderNodeProps, SliderNodePatch>(
        factory = { previous, next -> SliderNodePatch(previous, next) },
        apply = { view, patch ->
            InputNodePatchApplier.applySliderPatch(
                view = view as android.widget.SeekBar,
                patch = patch,
            )
        },
    )

    add(
        descriptor(
            nodeType = NodeType.TextField,
            bind = { view, node ->
                InputViewBinder.bindTextField(
                    view = view as ViewComposeEditText,
                    spec = InputViewBinder.readTextFieldSpec(node),
                )
            },
            patch = textFieldPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.Checkbox,
            bind = { view, node ->
                InputViewBinder.bindCheckbox(
                    view = view as android.widget.CheckBox,
                    spec = InputViewBinder.readToggleSpec(node),
                )
            },
            patch = togglePatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.Switch,
            bind = { view, node ->
                InputViewBinder.bindSwitch(
                    view = view as android.widget.Switch,
                    spec = InputViewBinder.readToggleSpec(node),
                )
            },
            patch = togglePatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.RadioButton,
            bind = { view, node ->
                InputViewBinder.bindRadioButton(
                    view = view as android.widget.RadioButton,
                    spec = InputViewBinder.readToggleSpec(node),
                )
            },
            patch = togglePatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.Slider,
            bind = { view, node ->
                InputViewBinder.bindSlider(
                    view = view as android.widget.SeekBar,
                    spec = InputViewBinder.readSliderSpec(node),
                )
            },
            patch = sliderPatch,
        ),
    )
}
