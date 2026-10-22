import SupportChat
import SwiftUI
import UserNotifications

/// Push wiring: 4 small hooks. Everything else is inside the SDK.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Simulator reaches the Mac's localhost directly.
        SupportChat.configure(appId: "demo", serverURL: URL(string: "http://localhost:4000")!)
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { application.registerForRemoteNotifications() }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        SupportChat.setDeviceToken(deviceToken)
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        await SupportChat.foregroundPresentationOptions(for: notification.request.content.userInfo)
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        await SupportChat.handleNotification(response.notification.request.content.userInfo)
    }
}

@main
struct SupportChatExampleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

struct ContentView: View {
    @ObservedObject private var chat = SupportChat.state
    @State private var showChat = false
    @State private var loggedIn = false

    var body: some View {
        NavigationView {
            List {
                Section("Chat") {
                    Button {
                        showChat = true
                    } label: {
                        HStack {
                            Label("Open chat", systemImage: "bubble.left.and.bubble.right.fill")
                            Spacer()
                            if chat.unreadCount > 0 {
                                Text("\(chat.unreadCount)")
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(.red))
                            }
                        }
                    }
                    Button("Present with UIKit API") { SupportChat.present() }
                }
                Section("User") {
                    if loggedIn {
                        Button("Log out", role: .destructive) {
                            SupportChat.logout()
                            loggedIn = false
                        }
                    } else {
                        Button("Log in as Aysel") {
                            // In production, userHash comes from your backend.
                            SupportChat.identify(SupportChatUser(id: "demo-aysel", name: "Aysel Məmmədova", email: "aysel@example.com",
                                                       attributes: ["plan": "pro", "orders": 3]))
                            loggedIn = true
                        }
                    }
                }
            }
            .navigationTitle("SupportChat Demo")
        }
        .fullScreenCover(isPresented: $showChat) {
            SupportChatChatView().ignoresSafeArea()
        }
    }
}
