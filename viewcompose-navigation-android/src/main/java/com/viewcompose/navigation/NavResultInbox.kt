package com.viewcompose.navigation

import android.os.Bundle
import android.os.Looper
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavResultDelivery
import com.viewcompose.navigation.core.NavResultKey
import com.viewcompose.navigation.core.NavResultPayload
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.runtime.mutableStateOf
import java.util.Collections

/**
 * Stable, destination-owned FIFO mailbox for navigation results.
 *
 * One inbox belongs to one retained destination entry. Pending payloads survive native
 * presentation disposal, configuration recreation, and Android saved-state restoration. Permanent
 * entry removal destroys the inbox with its owner. Reads participate in ViewCompose observation;
 * delivery and consumption are serialized on the Android main thread and copy the pending list.
 *
 * A result is addressed by [NavResultKey.name]. The oldest value for that name is decoded before it
 * is removed, so a codec mismatch leaves the payload available for diagnostics. [NavResultEffect]
 * is the lifecycle-safe default; [peek] and [consume] support explicit acknowledgement policies.
 *
 * Applications cannot construct or deliver to an inbox directly. The owning [NavHost] delivers
 * only a committed Core instruction and suppresses replay when a transition plan is reconciled.
 * This API is Alpha with the Navigation artifact.
 */
class NavResultInbox internal constructor(
    restoredState: Bundle?,
) {
    private var nextSequence = 1L
    private var lastRuntimeTransactionId = 0L
    private val mutablePending = mutableStateOf(
        immutableRecords(decodeRecords(restoredState).also { records ->
            nextSequence = (records.maxOfOrNull(PendingResultRecord::sequence) ?: 0L) + 1L
        }),
    )

    /** Number of unconsumed values across every key. Reading it observes mailbox changes. */
    @get:MainThread
    val pendingCount: Int
        get() {
            requireMainThread()
            return mutablePending.value.size
        }

    /** Returns whether at least one unconsumed value exists for [key]. */
    @MainThread
    fun <T> hasResult(key: NavResultKey<T>): Boolean {
        requireMainThread()
        return mutablePending.value.any { record -> record.payload.keyName == key.name }
    }

    /**
     * Decodes the oldest unconsumed value for [key] without removing it, or returns `null`.
     *
     * @throws IllegalArgumentException when the stored key uses a different stable codec identity
     * @throws RuntimeException when [NavResultKey.decode] rejects the stored value
     */
    @MainThread
    fun <T> peek(key: NavResultKey<T>): T? {
        requireMainThread()
        return pendingRecord(key)?.value
    }

    /**
     * Decodes and removes the oldest value for [key], or returns `null` when none is pending.
     *
     * Decoding completes before state changes, so an incompatible codec never acknowledges or loses
     * a payload. Successful removal schedules destination recomposition when the inbox was observed.
     *
     * @throws IllegalArgumentException when the stored key uses a different stable codec identity
     * @throws RuntimeException when [NavResultKey.decode] rejects the stored value
     */
    @MainThread
    fun <T> consume(key: NavResultKey<T>): T? {
        requireMainThread()
        val pending = pendingRecord(key) ?: return null
        return consumePending(key, pending.sequence)?.value
    }

    @MainThread
    internal fun deliver(delivery: NavResultDelivery) {
        requireMainThread()
        if (delivery.transactionId == lastRuntimeTransactionId) return
        check(delivery.transactionId > lastRuntimeTransactionId) {
            "Navigation result transaction ${delivery.transactionId} is older than " +
                "$lastRuntimeTransactionId for this inbox."
        }
        lastRuntimeTransactionId = delivery.transactionId
        mutablePending.value = immutableRecords(
            mutablePending.value + PendingResultRecord(
                sequence = nextSequence++,
                payload = delivery.payload,
            ),
        )
    }

    @MainThread
    internal fun <T> pendingRecord(key: NavResultKey<T>): PendingNavResult<T>? {
        requireMainThread()
        val record = mutablePending.value.firstOrNull { pending ->
            pending.payload.keyName == key.name
        } ?: return null
        return PendingNavResult(
            sequence = record.sequence,
            value = key.decode(record.payload),
        )
    }

    @MainThread
    internal fun <T> consumePending(
        key: NavResultKey<T>,
        sequence: Long,
    ): PendingNavResult<T>? {
        requireMainThread()
        val records = mutablePending.value
        val index = records.indexOfFirst { record ->
            record.sequence == sequence && record.payload.keyName == key.name
        }
        if (index < 0) return null
        val record = records[index]
        val decoded = key.decode(record.payload)
        mutablePending.value = immutableRecords(
            buildList(records.size - 1) {
                addAll(records.subList(0, index))
                addAll(records.subList(index + 1, records.size))
            },
        )
        return PendingNavResult(sequence, decoded)
    }

    @MainThread
    internal fun saveState(): Bundle {
        requireMainThread()
        return Bundle().apply {
            putInt(KEY_FORMAT_VERSION, FORMAT_VERSION)
            putParcelableArrayList(
                KEY_RECORDS,
                ArrayList(mutablePending.value.map(::encodeRecord)),
            )
        }
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Navigation results must be accessed on the Android main thread."
        }
    }
}

internal data class PendingNavResult<T>(
    val sequence: Long,
    val value: T,
)

private data class PendingResultRecord(
    val sequence: Long,
    val payload: NavResultPayload,
)

private fun immutableRecords(records: List<PendingResultRecord>): List<PendingResultRecord> {
    return Collections.unmodifiableList(ArrayList(records))
}

private fun encodeRecord(record: PendingResultRecord): Bundle {
    return Bundle().apply {
        putLong(KEY_SEQUENCE, record.sequence)
        putString(KEY_NAME, record.payload.keyName)
        putString(KEY_TYPE_ID, record.payload.typeId)
        when (val value = record.payload.value) {
            NavValue.Null -> putInt(KEY_VALUE_KIND, VALUE_NULL)
            is NavValue.Text -> {
                putInt(KEY_VALUE_KIND, VALUE_TEXT)
                putString(KEY_VALUE, value.value)
            }
            is NavValue.IntValue -> {
                putInt(KEY_VALUE_KIND, VALUE_INT)
                putInt(KEY_VALUE, value.value)
            }
            is NavValue.LongValue -> {
                putInt(KEY_VALUE_KIND, VALUE_LONG)
                putLong(KEY_VALUE, value.value)
            }
            is NavValue.BooleanValue -> {
                putInt(KEY_VALUE_KIND, VALUE_BOOLEAN)
                putBoolean(KEY_VALUE, value.value)
            }
            is NavValue.FloatValue -> {
                putInt(KEY_VALUE_KIND, VALUE_FLOAT)
                putFloat(KEY_VALUE, value.value)
            }
            is NavValue.DoubleValue -> {
                putInt(KEY_VALUE_KIND, VALUE_DOUBLE)
                putDouble(KEY_VALUE, value.value)
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun decodeRecords(state: Bundle?): List<PendingResultRecord> {
    if (state == null || state.getInt(KEY_FORMAT_VERSION, -1) != FORMAT_VERSION) return emptyList()
    val encoded = state.getParcelableArrayList<Bundle>(KEY_RECORDS) ?: return emptyList()
    if (encoded.size > MAX_RESTORED_RESULT_COUNT) return emptyList()
    val records = encoded.map { bundle -> decodeRecord(bundle) ?: return emptyList() }
    if (records.map(PendingResultRecord::sequence).distinct().size != records.size) {
        return emptyList()
    }
    return records.sortedBy(PendingResultRecord::sequence)
}

private fun decodeRecord(bundle: Bundle): PendingResultRecord? {
    val sequence = bundle.getLong(KEY_SEQUENCE, -1L)
        .takeIf { value -> value in 1L until Long.MAX_VALUE }
        ?: return null
    val name = bundle.getString(KEY_NAME)?.takeIf(String::isNotBlank) ?: return null
    val typeId = bundle.getString(KEY_TYPE_ID)?.takeIf(String::isNotBlank) ?: return null
    val value = when (bundle.getInt(KEY_VALUE_KIND, -1)) {
        VALUE_NULL -> NavValue.Null
        VALUE_TEXT -> bundle.getString(KEY_VALUE)?.let(NavValue::Text) ?: return null
        VALUE_INT -> NavValue.IntValue(bundle.getInt(KEY_VALUE))
        VALUE_LONG -> NavValue.LongValue(bundle.getLong(KEY_VALUE))
        VALUE_BOOLEAN -> NavValue.BooleanValue(bundle.getBoolean(KEY_VALUE))
        VALUE_FLOAT -> NavValue.FloatValue(bundle.getFloat(KEY_VALUE))
        VALUE_DOUBLE -> NavValue.DoubleValue(bundle.getDouble(KEY_VALUE))
        else -> return null
    }
    return PendingResultRecord(
        sequence = sequence,
        payload = NavResultPayload(name, typeId, value),
    )
}

private const val FORMAT_VERSION = 1
private const val MAX_RESTORED_RESULT_COUNT = 10_000
private const val KEY_FORMAT_VERSION = "formatVersion"
private const val KEY_RECORDS = "records"
private const val KEY_SEQUENCE = "sequence"
private const val KEY_NAME = "name"
private const val KEY_TYPE_ID = "typeId"
private const val KEY_VALUE_KIND = "valueKind"
private const val KEY_VALUE = "value"
private const val VALUE_NULL = 0
private const val VALUE_TEXT = 1
private const val VALUE_INT = 2
private const val VALUE_LONG = 3
private const val VALUE_BOOLEAN = 4
private const val VALUE_FLOAT = 5
private const val VALUE_DOUBLE = 6
