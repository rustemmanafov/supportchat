package io.supportchat.sdk

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The two REST calls the native layer needs (unread badge while the chat is closed).
 * Everything else happens inside the shared web widget. Blocking: call off the main thread.
 */
internal class Api(private val serverUrl: String) {
    data class Session(val token: String, val expiresAt: Long, val visitorId: String, val unread: Int)

    fun createSession(appId: String, payload: JSONObject): Session {
        val body = JSONObject(payload.toString()).put("appId", appId)
        val json = request("POST", "/v1/session", body, null)
        return Session(
            token = json.getString("token"),
            expiresAt = parseIso(json.optString("expiresAt")),
            visitorId = json.getJSONObject("visitor").getString("id"),
            unread = json.optInt("unread", 0),
        )
    }

    fun registerDevice(pushToken: String, token: String) {
        request("POST", "/v1/devices", JSONObject().put("platform", "android").put("token", pushToken), token)
    }

    fun unregisterDevice(pushToken: String, token: String) {
        val query = "?platform=android&token=" + java.net.URLEncoder.encode(pushToken, "UTF-8")
        request("DELETE", "/v1/devices$query", null, token)
    }

    fun unread(token: String): Int = request("GET", "/v1/unread", null, token).optInt("unread", 0)

    private fun request(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        val connection = try {
            URL(serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw SupportChatError("network_error", e.message)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-SupportChat-Client", "supportchat-android/${BuildConfig.SDK_VERSION}")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (status !in 200..299) {
                val error = json.optJSONObject("error")
                throw SupportChatError(error?.optString("code") ?: "http_$status", error?.optString("message"))
            }
            return json
        } catch (e: IOException) {
            throw SupportChatError("network_error", e.message)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun parseIso(value: String?): Long {
            if (value.isNullOrEmpty()) return 0L
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(value)?.time ?: 0L
            }.getOrDefault(0L)
        }
    }
}
