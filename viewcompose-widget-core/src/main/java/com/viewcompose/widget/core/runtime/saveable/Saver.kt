package com.viewcompose.widget.core

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.SnapshotMutationPolicy
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.structuralEqualityPolicy

/**
 * Converts a state holder or domain value to and from a host-saveable representation.
 */
class Saver<Original, Saveable>(
    val save: (Original) -> Saveable,
    val restore: (Saveable) -> Original,
)

@Suppress("UNCHECKED_CAST")
fun <T> autoSaver(): Saver<T, Any?> {
    return Saver(
        save = { value ->
            listOf(
                AUTO_SAVER_MARKER,
                AUTO_SAVER_FORMAT_VERSION,
                if (value is MutableState<*>) MUTABLE_STATE_KIND else VALUE_KIND,
                if (value is MutableState<*>) value.value else value,
            )
        },
        restore = { saved ->
            val restored = when {
                saved !is List<*> ||
                    saved.size != AUTO_SAVER_ENVELOPE_SIZE ||
                    saved[0] != AUTO_SAVER_MARKER ||
                    saved[1] != AUTO_SAVER_FORMAT_VERSION -> saved
                saved[2] == MUTABLE_STATE_KIND -> mutableStateOf(saved[3])
                saved[2] == VALUE_KIND -> saved[3]
                else -> error("Unknown autoSaver value kind: ${saved[2]}")
            }
            restored as T
        },
    )
}

fun <T> listSaver(
    save: (T) -> List<Any?>,
    restore: (List<Any?>) -> T,
): Saver<T, List<Any?>> {
    return Saver(
        save = save,
        restore = restore,
    )
}

fun <T> mapSaver(
    save: (T) -> Map<String, Any?>,
    restore: (Map<String, Any?>) -> T,
): Saver<T, Map<String, Any?>> {
    return Saver(
        save = save,
        restore = restore,
    )
}

fun <T, Saveable> mutableStateSaver(
    valueSaver: Saver<T, Saveable>,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
): Saver<MutableState<T>, Saveable> {
    return Saver(
        save = { state -> valueSaver.save(state.value) },
        restore = { saved ->
            mutableStateOf(
                value = valueSaver.restore(saved),
                policy = policy,
            )
        },
    )
}

private const val AUTO_SAVER_MARKER =
    "com.viewcompose.widget.core.runtime.saveable.AutoSaver"
private const val AUTO_SAVER_FORMAT_VERSION = 1
private const val AUTO_SAVER_ENVELOPE_SIZE = 4
private const val VALUE_KIND = 0
private const val MUTABLE_STATE_KIND = 1
