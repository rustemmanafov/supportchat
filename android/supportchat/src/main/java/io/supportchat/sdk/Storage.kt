package io.supportchat.sdk

import android.content.Context
import android.content.SharedPreferences

/** Per-app persistence of the visitor id and the short-lived session token. */
internal class Storage(context: Context, appId: String) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("io.supportchat.sdk.$appId", Context.MODE_PRIVATE)

    var visitorId: String?
        get() = prefs.getString("visitorId", null)
        set(value) = prefs.edit().putString("visitorId", value).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var tokenExpiry: Long
        get() = prefs.getLong("tokenExpiry", 0L)
        set(value) = prefs.edit().putLong("tokenExpiry", value).apply()

    /** FCM token and the visitor it was last registered for. */
    var pushToken: String?
        get() = prefs.getString("pushToken", null)
        set(value) = prefs.edit().putString("pushToken", value).apply()

    var pushRegisteredFor: String?
        get() = prefs.getString("pushRegisteredFor", null)
        set(value) = prefs.edit().putString("pushRegisteredFor", value).apply()

    /** The token if it is still valid for at least a minute. */
    val validToken: String?
        get() = token?.takeIf { tokenExpiry - System.currentTimeMillis() > 60_000 }

    /** Forgets the visitor. The FCM token belongs to the device and is kept. */
    fun clear() {
        prefs.edit()
            .remove("visitorId").remove("token").remove("tokenExpiry").remove("pushRegisteredFor")
            .apply()
    }
}
