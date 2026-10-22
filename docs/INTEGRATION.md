# SupportChat-i öz tətbiqinizə qoşmaq

Bu sənəd SupportChat-i öz tətbiqinə əlavə edən developerlər üçündür. Nəticədə tətbiqinizin
istifadəçiləri birbaşa tətbiqin içindən dəstək komandanıza yaza biləcək, komandanız isə
**Inbox** paneldən cavab verəcək.

Sizə SupportChat-i idarə edən tərəfdən 3 şey lazımdır:

| Nə | Nümunə | Harada istifadə olunur |
|---|---|---|
| **Server ünvanı** | `https://chat.example.com` | SDK-da |
| **App ID** | `shop` | SDK-da |
| **Identity secret** | `3RwMu…` | **yalnız sizin backend-də** (heç vaxt tətbiqə qoymayın) |

Dəstək komandanız üçün Inbox: `https://chat.example.com/inbox/` (email və şifrə ilə giriş).

---

## 1. iOS (Swift, iOS 15+)

**Əlavə edin:** Xcode → File → Add Package Dependencies → `https://github.com/rustemmanafov/supportchat` → versiya `1.0.0` → `SupportChat`.

```swift
import SupportChat

// AppDelegate.application(_:didFinishLaunchingWithOptions:)
SupportChat.configure(appId: "shop", serverURL: URL(string: "https://chat.example.com")!)
```

**İstifadəçi login olanda** (hər tətbiq açılışında da çağırın):

```swift
SupportChat.identify(
    SupportChatUser(id: user.id, name: user.fullName, email: user.email,
               attributes: ["plan": "premium", "orders": 12]),
    userHash: hashFromYourBackend          // bax: bölmə 4
)
```

**Chat-i açın:**

```swift
SupportChat.present()                                   // UIKit, istənilən yerdən
.fullScreenCover(isPresented: $showChat) {         // SwiftUI
    SupportChatChatView().ignoresSafeArea()
}
```

**Oxunmamış mesaj nişanı:**

```swift
@ObservedObject var chat = SupportChat.state      // chat.unreadCount
```

**Logout:** `SupportChat.logout()`

**Push bildirişləri** (istəyə bağlı):

1. Xcode → Signing & Capabilities → **Push Notifications** əlavə edin.
2. Apple Developer-də **.p8 açar** yaradın və SupportChat admininə verin (Inbox → Settings → Push).
3. AppDelegate-ə 4 sətir əlavə edin:

```swift
func application(_ app: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
    SupportChat.setDeviceToken(token)
}
func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification) async -> UNNotificationPresentationOptions {
    await SupportChat.foregroundPresentationOptions(for: n.request.content.userInfo)
}
func userNotificationCenter(_ c: UNUserNotificationCenter, didReceive r: UNNotificationResponse) async {
    await SupportChat.handleNotification(r.notification.request.content.userInfo)   // chat-i açır
}
```

**Kamera:** chat-də fayl seçərkən kamera da təklif olunur, ona görə `Info.plist`-ə
`NSCameraUsageDescription` əlavə edin.

---

## 2. Android (Kotlin/Java, minSdk 23)

**Əlavə edin** (JitPack ilə):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement { repositories { maven("https://jitpack.io") } }

// app/build.gradle.kts
dependencies { implementation("com.github.rustemmanafov:supportchat:1.0.0") }
```

SDK-nın AndroidX və ya başqa kitabxana asılılığı yoxdur, ona görə tətbiqinizdəki heç nə ilə toqquşmur.

```kotlin
// Application.onCreate()
SupportChat.configure(this, appId = "shop", serverUrl = "https://chat.example.com")

// login-dən sonra (hər açılışda da)
SupportChat.identify(SupportChatUser(id = user.id, name = user.name, email = user.email,
                           attributes = mapOf("plan" to "premium")), userHash = hashFromBackend)

// chat-i açmaq
SupportChat.open(context)

// oxunmamış nişan
SupportChat.addListener(object : SupportChat.Listener {
    override fun onUnreadCountChanged(count: Int) { badge.text = "$count" }
})

// logout
SupportChat.logout()
```

**Push (FCM)**, istəyə bağlı. Firebase artıq tətbiqinizdə varsa:

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = SupportChat.setPushToken(token)
    override fun onMessageReceived(message: RemoteMessage) {
        if (SupportChat.handlePush(this, message.data)) return   // SupportChat bildirişini göstərir
        // ... sizin digər bildirişləriniz
    }
}
```

Firebase Console → Project settings → Service accounts → **JSON açarı** yaradın və SupportChat
admininə verin. Android 13+ üçün `POST_NOTIFICATIONS` icazəsini istəyin.
`SupportChat.notificationIcon = R.drawable.ic_stat_chat` ilə öz ikonunuzu təyin edə bilərsiniz.

---

## 3. Veb sayt (HTML, React, Vue, Angular, …)

**Ən sadə yol:** `</body>`-dən əvvəl əlavə edin:

```html
<script>
  window.SupportChatSettings = { appId: 'shop' };
</script>
<script async src="https://chat.example.com/sdk.js"></script>
```

**npm ilə:**

```js
import { loadSupportChat } from '@supportchat/web';
const SupportChat = await loadSupportChat({ serverUrl: 'https://chat.example.com', appId: 'shop' });
```

```js
SupportChat('identify', { id: user.id, name: user.name, email: user.email }, userHash);
SupportChat('open');                              // öz düymənizdən açmaq
SupportChat('on', 'unread', (n) => setBadge(n));
SupportChat('logout');
```

Chat Shadow DOM və iframe içində işləyir, ona görə saytınızın CSS-i chat-ə təsir etmir,
chat də saytınızı sındırmır.

---

## 4. `userHash` (təhlükəsizlik üçün vacibdir)

`userHash` başqasının adından yazmağın qarşısını alır. Onu **öz serverinizdə** hesablayın və
login cavabında tətbiqə qaytarın:

```js
// Node.js
const userHash = crypto.createHmac('sha256', process.env.SUPPORTCHAT_SECRET).update(String(user.id)).digest('hex');
```
```python
# Python
user_hash = hmac.new(SUPPORTCHAT_SECRET.encode(), str(user.id).encode(), hashlib.sha256).hexdigest()
```
```php
// PHP
$userHash = hash_hmac('sha256', (string)$user->id, getenv('SUPPORTCHAT_SECRET'));
```
```csharp
// C#
var userHash = Convert.ToHexString(new HMACSHA256(Encoding.UTF8.GetBytes(secret))
    .ComputeHash(Encoding.UTF8.GetBytes(userId))).ToLower();
```

Login olmamış istifadəçilər də yaza bilər (anonim). Login olduqdan sonra onların köhnə
yazışması avtomatik olaraq hesablarına keçir.

---

## 5. Görünüşü dəyişmək (kodsuz, dərhal)

Inbox → **Settings**: rəng, künc radiusu, mövqe, açıq/qaranlıq tema, operator adı, bütün
mətnlər (az/en/ru, istənilən başqa dil əlavə oluna bilər). **Save & publish** düyməsini
basdığınız anda açıq olan bütün chat-lər (veb, iOS, Android) yenilənir. App Store və ya
Google Play-ə yeni versiya göndərmək lazım deyil.

## 6. Tez-tez verilən suallar

- **Tətbiqin ölçüsünü artırırmı?** Çox az: Android paketi ~50 KB-dır, iOS SDK-nın da heç bir asılılığı yoxdur. Chat ekranı serverdən yüklənir.
- **İnternet kəsiləndə nə olur?** Mesaj "göndərilir" kimi qalır və bağlantı bərpa olunanda öz-özünə gedir. Eyni mesaj iki dəfə getmir.
- **İstifadəçi chat-i bağlayanda cavab gəlsə?** Push qoşulubsa bildiriş gəlir, nişandakı say da artır.
- **Bir neçə tətbiq üçün bir server olarmı?** Bəli. Hər tətbiqin öz App ID-si, öz dizaynı və öz komandası olur.
