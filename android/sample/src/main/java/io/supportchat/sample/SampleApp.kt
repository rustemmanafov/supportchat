package io.supportchat.sample

import android.app.Application
import io.supportchat.sdk.SupportChat

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SupportChat.configure(this, appId = "demo", serverUrl = BuildConfig.SUPPORTCHAT_SERVER)
    }
}
