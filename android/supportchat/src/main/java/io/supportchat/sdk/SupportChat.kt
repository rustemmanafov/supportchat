package io.supportchat.sdk

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Entry point of the SupportChat Android SDK.
 *
 * ```kotlin
 * SupportChat.configure(this, appId = "YOUR_APP_ID", serverUrl = "https://chat.example.com")
 * SupportChat.identify(SupportChatUser(id = "42", name = "Aysel"), userHash = hashFromYourBackend)
 * SupportChat.open(context)
 * ```
 *
 * All methods are safe to call from any thread; listeners are invoked on the main thread.
 */
object SupportChat {
    const val VERSION = BuildConfig.SDK_VERSION

    /** Receives chat events. All methods are optional. */
    interface Listener {
        fun onUnreadCountChanged(count: Int) {}

        /** An agent message arrived while the chat screen was not visible. */
        fun onMessage(body: String, author: String?) {}

        fun onError(error: SupportChatError) {}
    }

    internal class Config(val appId: String, val serverUrl: String)

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "supportchat-io").apply { isDaemon = true } }
    private val listeners = CopyOnWriteArrayList<Listener>()
    internal val openChats: MutableSet<SupportChatActivity> = Collections.newSetFromMap(WeakHashMap())

    @Volatile internal var config: Config? = null
    @Volatile internal var storage: Storage? = null
    @Volatile internal var user: SupportChatUser? = null
    @Volatile internal var userHash: String? = null
    @Volatile internal var locale: String? = null
    private val pendingAttributes = mutableMapOf<String, Any?>()
    private var lifecycleRegistered = false

    /** Current unread count (agent messages the visitor hasn't seen). */
    @JvmStatic
    @Volatile
    var unreadCount: Int = 0
        private set

    @JvmStatic
    val isConfigured: Boolean get() = config != null

    // ---- setup ------------------------------------------------------------

    /** Call once, e.g. in `Application.onCreate()`. */
    @JvmStatic
    @JvmOverloads
    fun configure(context: Context, appId: String, serverUrl: String, locale: String? = null) {
        val app = context.applicationContext
        config = Config(appId, serverUrl.trimEnd('/'))
        storage = Storage(app, appId)
        this.locale = locale
        registerLifecycle(app)
        refreshUnreadCount()
    }

    /** Logs a user in (or switches user). The anonymous history on this device is merged into the user. */
    @JvmStatic
    @JvmOverloads
    fun identify(user: SupportChatUser, userHash: String? = null) {
        this.user = user
        this.userHash = userHash
        storage?.token = null
        rebootOpenChats()
        refreshUnreadCount(force = true)
    }

    /** Updates custom attributes of the current visitor without re-identifying. */
    @JvmStatic
    fun update(attributes: Map<String, Any?>) {
        val payload = JSONObject().put("attributes", attributesJson(attributes))
        onMain { openChats.toList().forEach { it.send("update", payload) } }
        val current = user
        if (current != null) {
            user = current.copy(attributes = current.attributes + attributes)
        } else {
            synchronized(pendingAttributes) { pendingAttributes.putAll(attributes) }
        }
    }

    /** Logs out: forgets this device's visitor; the next chat starts a fresh anonymous one. */
    @JvmStatic
    fun logout() {
        val config = config
        val storage = storage
        val pushToken = storage?.pushToken
        val token = storage?.validToken
        if (config != null && pushToken != null && token != null && storage.pushRegisteredFor != null) {
            io.execute { runCatching { Api(config.serverUrl).unregisterDevice(pushToken, token) } }
        }
        user = null
        userHash = null
        synchronized(pendingAttributes) { pendingAttributes.clear() }
        storage?.clear()
        setUnread(0)
        rebootOpenChats()
    }

    @JvmStatic
    fun setLocale(locale: String?) {
        this.locale = locale
        rebootOpenChats()
    }

    // ---- UI ---------------------------------------------------------------

    /** Opens the full-screen chat. */
    @JvmStatic
    fun open(context: Context) {
        check(config != null) { "Call SupportChat.configure(context, appId, serverUrl) first" }
        val intent = Intent(context, SupportChatActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** An Intent for the chat screen, e.g. for a notification's PendingIntent. */
    @JvmStatic
    fun intent(context: Context): Intent = Intent(context, SupportChatActivity::class.java)

    // ---- push notifications -----------------------------------------------

    /** Small icon for chat notifications (a monochrome drawable). Defaults to a chat bubble. */
    @JvmStatic
    var notificationIcon: Int = 0

    /** Name of the notification channel shown in system settings. */
    @JvmStatic
    var notificationChannelName: String = "Chat messages"

    /** Pass the FCM registration token (from `FirebaseMessagingService.onNewToken` / `getToken()`). */
    @JvmStatic
    fun setPushToken(token: String) {
        val storage = storage ?: return
        if (storage.pushToken != token) storage.pushRegisteredFor = null
        storage.pushToken = token
        registerPushIfNeeded()
    }

    /** True when an FCM data payload came from SupportChat. */
    @JvmStatic
    fun isSupportChatPush(data: Map<String, String>): Boolean = data["supportchat"] == "1"

    /**
     * Shows the chat notification for a SupportChat push (call from `onMessageReceived` with
     * `remoteMessage.data`). Returns false for other payloads so you can handle them yourself.
     * On Android 13+ the host app must have the POST_NOTIFICATIONS permission.
     */
    @JvmStatic
    fun handlePush(context: Context, data: Map<String, String>): Boolean {
        if (!isSupportChatPush(data)) return false
        data["badge"]?.toIntOrNull()?.let { setUnread(it) }
        // The message is already on screen.
        if (openChats.any { it.isOnScreen }) return true

        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, notificationChannelName, NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val intent = Intent(app, SupportChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = data["body"].orEmpty()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(app, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(app).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
        }
        builder
            .setSmallIcon(if (notificationIcon != 0) notificationIcon else R.drawable.supportchat_ic_notification)
            .setContentTitle(data["title"])
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(Notification.CATEGORY_MESSAGE)
        data["badge"]?.toIntOrNull()?.let { builder.setNumber(it) }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            // Missing permission or a bad custom icon must never crash the host app.
            notifyError(SupportChatError("notification_failed", e.message))
        }
        return true
    }

    internal const val CHANNEL_ID = "supportchat_messages"
    internal const val NOTIFICATION_ID = 0x50B7

    internal fun registerPushIfNeeded() {
        val config = config ?: return
        val storage = storage ?: return
        io.execute {
            val pushToken = storage.pushToken ?: return@execute
            val visitorId = storage.visitorId ?: return@execute
            val token = storage.validToken ?: return@execute
            if (storage.pushRegisteredFor == visitorId) return@execute
            try {
                Api(config.serverUrl).registerDevice(pushToken, token)
                storage.pushRegisteredFor = visitorId
            } catch (e: SupportChatError) {
                notifyError(e)
            }
        }
    }

    // ---- listeners & unread -----------------------------------------------

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Fetches the unread count from the server (cheap, in the background). Called automatically on
     * configure and when the app comes to the foreground, only for devices that already chatted.
     */
    @JvmStatic
    fun refreshUnreadCount() = refreshUnreadCount(force = false)

    // ---- internal ---------------------------------------------------------

    internal fun bootPayload(): JSONObject = JSONObject().apply {
        put("platform", "android")
        put("showCloseButton", true)
        storage?.visitorId?.let { put("visitorId", it) }
        user?.let { put("user", it.toJson()) }
        userHash?.let { put("userHash", it) }
        locale?.let { put("locale", it) }
        synchronized(pendingAttributes) {
            if (pendingAttributes.isNotEmpty()) put("attributes", attributesJson(pendingAttributes))
        }
    }

    internal fun didStartSession(visitorId: String, token: String?, expiresAt: String?) {
        storage?.visitorId = visitorId
        if (token != null) storage?.token = token
        if (expiresAt != null) storage?.tokenExpiry = Api.parseIso(expiresAt)
        registerPushIfNeeded()
    }

    internal fun setUnread(count: Int) {
        if (unreadCount == count) return
        unreadCount = count
        onMain { listeners.forEach { it.onUnreadCountChanged(count) } }
    }

    internal fun notifyMessage(body: String, author: String?) =
        onMain { listeners.forEach { it.onMessage(body, author) } }

    internal fun notifyError(error: SupportChatError) = onMain { listeners.forEach { it.onError(error) } }

    private fun rebootOpenChats() = onMain { openChats.toList().forEach { it.send("boot", bootPayload()) } }

    private fun refreshUnreadCount(force: Boolean) {
        val config = config ?: return
        val storage = storage ?: return
        // Never create anonymous visitors for people who have not chatted yet.
        if (storage.visitorId == null && !(force && user != null)) return
        io.execute {
            val api = Api(config.serverUrl)
            try {
                val token = storage.validToken
                val count = token?.let { runCatching { api.unread(it) }.getOrNull() }
                if (count != null) {
                    setUnread(count)
                    return@execute
                }
                val session = api.createSession(config.appId, bootPayload())
                storage.visitorId = session.visitorId
                storage.token = session.token
                storage.tokenExpiry = session.expiresAt
                setUnread(session.unread)
                registerPushIfNeeded()
            } catch (e: SupportChatError) {
                notifyError(e)
            } catch (e: Exception) {
                notifyError(SupportChatError("unknown", e.message))
            }
        }
    }

    private fun registerLifecycle(app: Context) {
        if (lifecycleRegistered || app !is Application) return
        lifecycleRegistered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) refreshUnreadCount()
            }
            override fun onActivityStopped(activity: Activity) {
                started = maxOf(0, started - 1)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
