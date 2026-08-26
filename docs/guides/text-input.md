---
schema_version: 2
document_id: guide.text-input-editing
doc_type: guide
owner:
  kind: capability
  id: text.input
version_lane: released
capability_ids:
  - text.input
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-text-core
  - viewcompose-renderer-android
sample_ids:
  - guide.text-input-editing
  - guide.text-input-ime
task: Edit, validate, and submit native-backed text using one state plus explicit input and IME policy.
success_checks:
  - TextField and SearchBar keep text, selection, composition, and history in caller-owned TextFieldState.
  - InputTransformation applies only to platform-proposed edits while application edits remain atomic.
  - Search and undo actions read the latest state synchronously.
  - Keyboard, autofill, line, and action policy describe one coherent input purpose.
failure_checks:
  - Application code mirrors TextFieldState into a second String callback path.
  - A changing TextFieldState instance is reconstructed on each composition.
  - Programmatic validation is expected to run through InputTransformation.
  - An unhandled IME action is consumed or active composition is treated as restorable state.
---

# Edit and validate text

Use one stable `TextFieldState` for each logical editor. It is the authoritative owner of the rich
document, directional selection, active IME composition, and undo/redo history. This task covers
ordinary fields and search submission; the durable state and Android bridge invariants are defined
by the [text input architecture](../architecture/text-input.md).

## Bind editable state

`rememberTextFieldState` preserves the document and selection through host recreation. Read its
observable properties directly instead of copying text into another callback-owned value.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-editing" sample_id="guide.text-input-editing" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.EditableSearchForm(onSearch: (String) -> Unit) {
    val query = rememberTextFieldState()
    val name = rememberTextFieldState()

    Column {
        SearchBar(
            state = query,
            placeholder = "Search",
            onSearch = onSearch,
        )
        TextField(
            state = name,
            label = "Display name",
            supportingText = "Up to 24 characters",
            inputTransformation = InputTransformation.maxCodePoints(24),
        )
        Button(
            text = "Undo",
            enabled = name.canUndo,
            onClick = { name.undo() },
        )
    }
}
```

`SearchBar` selects the Search IME action only when `onSearch` is present and passes the latest
`state.text`. It does not debounce, clear, or submit by itself. `TextField` selects appearance,
input purpose, and line behavior without creating a second editor state.

## Separate user and application edits

`InputTransformation` evaluates only platform-proposed edits. Compose transformations with `then`
when order matters; a later transformation sees the result of the earlier one. `maxCodePoints`
counts Unicode code points and therefore does not split a valid surrogate pair.

Application changes use one explicit `state.edit { replace(0, length, replacement); selectAll() }`
transaction.

One `edit` call publishes one state change and creates one undo unit. Selection-only changes do not
add history. Do not route programmatic edits through an input policy: application validation and
platform input filtering have different ownership.

## Choose the component level

- Use `TextField` for labeled application forms and resolved design-system defaults.
- Use `SearchBar` for a single-line query with optional Search submission.
- Use `BasicTextField` only when a design system has already resolved a complete
  `BasicTextFieldStyle`; it intentionally performs no theme or component-Local lookup.

Use [rich and received content](./text-input-rich-text.md) when annotations, inline attachments,
clipboard, drop, or IME content must survive editing.

## Configure keyboard and IME actions

`TextFieldInputProfile` couples keyboard options and autofill semantics; `TextFieldLinePolicy`
separately owns visual line behavior. Use these values instead of password, email, number, or
text-area component wrappers.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-ime" sample_id="guide.text-input-ime" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.EmailSubmissionField(onSubmit: (String) -> Unit) {
    val email = rememberTextFieldState()

    TextField(
        state = email,
        label = "Email",
        inputProfile = TextFieldInputProfile(
            keyboardOptions = TextFieldKeyboardOptions(
                keyboardType = TextFieldType.Email,
                imeAction = TextFieldImeAction.Done,
            ),
            autofillHints = TextFieldInputProfile.Email.autofillHints,
        ),
        onKeyboardAction = { action ->
            if (action == TextFieldImeAction.Done) {
                onSubmit(email.text)
                true
            } else {
                false
            }
        },
    )
}
```

Return `true` only for an action the application handled; `false` preserves native fallback. Keep
the profile stable unless product state changes because changing input type or editor options may
restart the active native connection. Equal recomposition must preserve selection and composition.

## Verify the task

Compile with `./gradlew :samples:tutorials:compileDebugKotlin`, then verify:

1. type and select text; recomposition must not move the cursor or end active composition;
2. exceed the code-point limit through the keyboard; the proposed edit must be rejected without a
   transient invalid value;
3. invoke Search; the callback must receive the latest visible query;
4. perform an application edit and undo it; document and selection must restore as one snapshot;
5. confirm the expected keyboard, autofill category, and action; submit must read the latest text
   once while unhandled actions keep native fallback;
6. recreate the Activity; document and selection restore, while composition and undo history do
   not.

A parallel `String`, lost cursor, stale submit value, transformation of an application edit, or
restored IME session is a failed integration.
