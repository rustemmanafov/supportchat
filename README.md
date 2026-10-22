# SupportChat SDKs

Add live support chat to your app in a few lines. Your users write to your support team
from inside your iOS app, Android app or website; your team answers from the SupportChat inbox.

You need a SupportChat account (it gives you an **App ID** and a **server URL**).

## iOS (Swift Package Manager, iOS 15+)

Xcode → *File → Add Package Dependencies…* → `https://github.com/rustemmanafov/supportchat`

```swift
import SupportChat

SupportChat.configure(appId: "YOUR_APP_ID", serverURL: URL(string: "https://YOUR_SERVER")!)
SupportChat.identify(SupportChatUser(id: user.id, name: user.name), userHash: hashFromYourBackend)
SupportChat.present()                      // or SupportChatView() in SwiftUI
```

## Android (minSdk 23, no AndroidX)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement { repositories { maven("https://jitpack.io") } }

// app/build.gradle.kts
dependencies { implementation("com.github.rustemmanafov:supportchat:1.0.1") }
```

```kotlin
SupportChat.configure(this, appId = "YOUR_APP_ID", serverUrl = "https://YOUR_SERVER")
SupportChat.identify(SupportChatUser(id = user.id, name = user.name), userHash = hashFromYourBackend)
SupportChat.open(context)
```

## Web

```html
<script>window.SupportChatSettings = { appId: 'YOUR_APP_ID' };</script>
<script async src="https://YOUR_SERVER/sdk.js"></script>
```

or `npm i @supportchat/web` → `await loadSupportChat({ serverUrl, appId })`.

## Docs

Step-by-step guide (push notifications, user verification, unread badges, logout):
[docs/INTEGRATION.md](docs/INTEGRATION.md)

## License

The SDKs are MIT licensed. The SupportChat service is a commercial product.
