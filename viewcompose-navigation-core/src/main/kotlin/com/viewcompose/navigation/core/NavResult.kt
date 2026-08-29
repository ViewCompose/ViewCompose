package com.viewcompose.navigation.core

/**
 * Typed, stable key used to return one value to a surviving navigation entry.
 *
 * [name] and [typeId] are persisted with pending results and therefore must remain stable across
 * process recreation and application upgrades. [encoder] must produce a platform-neutral
 * [NavValue]; [decoder] must either return the matching value or throw for an incompatible value.
 * Keys compare by [name] and [typeId], not by codec function identity.
 *
 * @param T application-facing result type
 * @property name non-blank mailbox key within one destination entry
 * @property typeId non-blank stable codec identity
 * @param encoder converts an application value to the closed navigation value set
 * @param decoder converts a matching navigation value back to the application type
 */
class NavResultKey<T>(
    val name: String,
    val typeId: String,
    private val encoder: (T) -> NavValue,
    private val decoder: (NavValue) -> T,
) {
    init {
        require(name.isNotBlank()) { "Navigation result key names must not be blank." }
        require(typeId.isNotBlank()) { "Navigation result type IDs must not be blank." }
    }

    /** Encodes [value] into an immutable payload carrying this key's stable identities. */
    fun encode(value: T): NavResultPayload {
        return NavResultPayload(
            keyName = name,
            typeId = typeId,
            value = encoder(value),
        )
    }

    /**
     * Decodes [payload] after verifying that its key and codec identities match this key.
     *
     * @throws IllegalArgumentException when the payload belongs to another key or codec
     * @throws RuntimeException when the configured decoder rejects the stored [NavValue]
     */
    fun decode(payload: NavResultPayload): T {
        require(payload.keyName == name) {
            "Navigation result '${payload.keyName}' does not match key '$name'."
        }
        require(payload.typeId == typeId) {
            "Navigation result '$name' uses type '${payload.typeId}', expected '$typeId'."
        }
        return decoder(payload.value)
    }

    /** Compares the persisted key and codec identities. */
    override fun equals(other: Any?): Boolean {
        return other is NavResultKey<*> && name == other.name && typeId == other.typeId
    }

    /** Returns the structural hash of [name] and [typeId]. */
    override fun hashCode(): Int = 31 * name.hashCode() + typeId.hashCode()

    /** Returns a diagnostic representation without codec function identities. */
    override fun toString(): String = "NavResultKey(name=$name, typeId=$typeId)"

    /** Built-in codecs for every non-null scalar [NavValue] variant. */
    companion object {
        /** Creates a string result key. */
        fun text(name: String): NavResultKey<String> = NavResultKey(
            name = name,
            typeId = "kotlin.String",
            encoder = NavValue::Text,
            decoder = { value -> (value as NavValue.Text).value },
        )

        /** Creates a 32-bit integer result key. */
        fun int(name: String): NavResultKey<Int> = NavResultKey(
            name = name,
            typeId = "kotlin.Int",
            encoder = NavValue::IntValue,
            decoder = { value -> (value as NavValue.IntValue).value },
        )

        /** Creates a 64-bit integer result key. */
        fun long(name: String): NavResultKey<Long> = NavResultKey(
            name = name,
            typeId = "kotlin.Long",
            encoder = NavValue::LongValue,
            decoder = { value -> (value as NavValue.LongValue).value },
        )

        /** Creates a Boolean result key. */
        fun boolean(name: String): NavResultKey<Boolean> = NavResultKey(
            name = name,
            typeId = "kotlin.Boolean",
            encoder = NavValue::BooleanValue,
            decoder = { value -> (value as NavValue.BooleanValue).value },
        )

        /** Creates a 32-bit floating-point result key. */
        fun float(name: String): NavResultKey<Float> = NavResultKey(
            name = name,
            typeId = "kotlin.Float",
            encoder = NavValue::FloatValue,
            decoder = { value -> (value as NavValue.FloatValue).value },
        )

        /** Creates a 64-bit floating-point result key. */
        fun double(name: String): NavResultKey<Double> = NavResultKey(
            name = name,
            typeId = "kotlin.Double",
            encoder = NavValue::DoubleValue,
            decoder = { value -> (value as NavValue.DoubleValue).value },
        )
    }
}

/**
 * Persistable result value carried by a result-bearing Pop command.
 *
 * @property keyName stable destination-mailbox key
 * @property typeId stable codec identity checked before typed decoding
 * @property value platform-neutral encoded value
 */
data class NavResultPayload(
    val keyName: String,
    val typeId: String,
    val value: NavValue,
) {
    init {
        require(keyName.isNotBlank()) { "Navigation result payload keys must not be blank." }
        require(typeId.isNotBlank()) { "Navigation result payload type IDs must not be blank." }
    }
}

/**
 * Idempotent post-commit instruction to deliver [payload] to [targetEntryId].
 *
 * [transactionId] is monotonic only within one live controller. Platform executors use it to avoid
 * replaying the same delivery when an active transition plan is reconciled; it is not a persisted
 * application identity.
 *
 * @property transactionId positive owning transaction identity
 * @property targetEntryId surviving entry that receives the result
 * @property payload encoded result value
 */
data class NavResultDelivery(
    val transactionId: Long,
    val targetEntryId: NavEntryId,
    val payload: NavResultPayload,
) {
    init {
        require(transactionId > 0L) { "Navigation result transaction IDs must be positive." }
    }
}
