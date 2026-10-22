package io.supportchat.sdk

import org.json.JSONObject

/**
 * The signed-in user of the host app.
 *
 * `id` is your own stable user id. When identity verification is on, also pass
 * `userHash = HMAC-SHA256(identitySecret, id)` computed on **your backend**.
 *
 * Attribute values may be String, Number or Boolean.
 */
data class SupportChatUser @JvmOverloads constructor(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val attributes: Map<String, Any?> = emptyMap(),
) {
    internal fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        name?.let { put("name", it) }
        email?.let { put("email", it) }
        phone?.let { put("phone", it) }
        if (attributes.isNotEmpty()) put("attributes", attributesJson(attributes))
    }
}

/** Error reported by the widget or the SDK. `code` is machine-readable, e.g. `invalid_user_hash`. */
data class SupportChatError(val code: String, override val message: String? = null) : Exception(message ?: code)

internal fun attributesJson(attributes: Map<String, Any?>): JSONObject = JSONObject().apply {
    for ((key, value) in attributes) {
        when (value) {
            null -> put(key, JSONObject.NULL)
            is String, is Boolean, is Int, is Long, is Double, is Float -> put(key, value)
            is Number -> put(key, value.toDouble())
            else -> put(key, value.toString())
        }
    }
}
