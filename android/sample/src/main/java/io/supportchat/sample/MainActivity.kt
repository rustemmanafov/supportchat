package io.supportchat.sample

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.supportchat.sdk.SupportChat
import io.supportchat.sdk.SupportChatError
import io.supportchat.sdk.SupportChatUser

class MainActivity : Activity(), SupportChat.Listener {
    private lateinit var openButton: Button
    private lateinit var loginButton: Button
    private var loggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openButton = Button(this).apply { setOnClickListener { SupportChat.open(this@MainActivity) } }
        loginButton = Button(this).apply { setOnClickListener { toggleLogin() } }
        // In a real app FirebaseMessagingService.onMessageReceived calls SupportChat.handlePush(this, message.data).
        val pushButton = Button(this).apply {
            text = "Simulate push"
            setOnClickListener {
                SupportChat.handlePush(this@MainActivity, mapOf(
                    "supportchat" to "1", "title" to "Leyla", "body" to "Sifarişiniz yoldadır 🚚", "badge" to "1",
                ))
            }
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Android 15 draws apps edge-to-edge; keep the content clear of the system bars.
            fitsSystemWindows = true
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply { text = "SupportChat Demo"; textSize = 28f })
            addView(openButton)
            addView(loginButton)
            addView(pushButton)
        })
        render()
    }

    override fun onStart() {
        super.onStart()
        SupportChat.addListener(this)
        render()
    }

    override fun onStop() {
        SupportChat.removeListener(this)
        super.onStop()
    }

    private fun toggleLogin() {
        if (loggedIn) {
            SupportChat.logout()
        } else {
            // In production, userHash comes from your backend.
            SupportChat.identify(SupportChatUser(id = "demo-aysel", name = "Aysel Məmmədova", email = "aysel@example.com",
                attributes = mapOf("plan" to "pro")))
        }
        loggedIn = !loggedIn
        render()
    }

    private fun render() {
        val unread = SupportChat.unreadCount
        openButton.text = if (unread > 0) "Open chat ($unread)" else "Open chat"
        loginButton.text = if (loggedIn) "Log out" else "Log in as Aysel"
    }

    override fun onUnreadCountChanged(count: Int) = render()

    override fun onError(error: SupportChatError) {
        Toast.makeText(this, "SupportChat: ${error.code}", Toast.LENGTH_SHORT).show()
    }
}
