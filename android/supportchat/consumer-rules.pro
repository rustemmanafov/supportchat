# The widget calls these from JavaScript; R8 must not rename or strip them.
-keepclassmembers class io.supportchat.sdk.** {
    @android.webkit.JavascriptInterface <methods>;
}
