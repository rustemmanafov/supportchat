import Foundation
import UIKit
import UserNotifications

/// Receives chat events. All methods are optional.
@MainActor
public protocol SupportChatDelegate: AnyObject {
    func supportChat(didChangeUnreadCount count: Int)
    /// An agent message arrived while the chat screen was not visible.
    func supportChat(didReceiveMessage body: String, from author: String?)
    func supportChat(didFailWith error: SupportChatError)
    /// The user tapped the widget's close button. Return `false` to handle it yourself.
    func supportChatShouldDismiss(_ controller: UIViewController) -> Bool
}

public extension SupportChatDelegate {
    func supportChat(didChangeUnreadCount count: Int) {}
    func supportChat(didReceiveMessage body: String, from author: String?) {}
    func supportChat(didFailWith error: SupportChatError) {}
    func supportChatShouldDismiss(_ controller: UIViewController) -> Bool { true }
}

/// Entry point of the SupportChat iOS SDK.
///
/// ```swift
/// SupportChat.configure(appId: "YOUR_APP_ID", serverURL: URL(string: "https://chat.example.com")!)
/// SupportChat.identify(SupportChatUser(id: "42", name: "Aysel"), userHash: hashFromYourBackend)
/// SupportChat.present()
/// ```
@MainActor
public enum SupportChat {
    nonisolated public static let version = "1.0.1"

    /// Posted on the main thread whenever the unread count changes. `userInfo["count"]` is an `Int`.
    public static let unreadCountDidChangeNotification = Notification.Name("SupportChatUnreadCountDidChange")

    public static weak var delegate: SupportChatDelegate?

    /// Observable state for SwiftUI (`@ObservedObject var chat = SupportChat.state`).
    public static let state = SupportChatState()

    public static var unreadCount: Int { state.unreadCount }

    public static var isConfigured: Bool { configuration != nil }

    // MARK: Setup

    /// Call once, e.g. in `application(_:didFinishLaunchingWithOptions:)`.
    public static func configure(appId: String, serverURL: URL, locale: String? = nil) {
        configuration = Configuration(appId: appId, serverURL: serverURL)
        self.locale = locale
        storage = Storage(appId: appId)
        observeForeground()
        Task { await refreshUnreadCount() }
    }

    /// Logs a user in (or switches user). The anonymous history on this device is merged into the user.
    public static func identify(_ user: SupportChatUser, userHash: String? = nil) {
        self.user = user
        self.userHash = userHash
        storage?.token = nil
        rebootOpenChats()
        Task { await refreshUnreadCount(force: true) }
    }

    /// Updates profile fields / attributes of the current visitor without re-identifying.
    public static func update(attributes: [String: SupportChatAttribute]) {
        let payload: [String: Any] = ["attributes": attributes.mapValues(\.json)]
        for chat in openChats.allObjects { chat.send("update", payload) }
        if var user {
            user.attributes.merge(attributes) { $1 }
            self.user = user
        } else {
            pendingAttributes.merge(attributes) { $1 }
        }
    }

    /// Logs out: forgets this device's visitor; the next chat starts a fresh anonymous one.
    public static func logout() {
        if let configuration, let storage, let pushToken = storage.pushToken, let token = storage.validToken,
           storage.pushRegisteredFor != nil {
            let api = API(serverURL: configuration.serverURL)
            Task { try? await api.unregisterDevice(pushToken: pushToken, token: token) }
        }
        user = nil
        userHash = nil
        pendingAttributes = [:]
        storage?.clear()
        setUnread(0)
        rebootOpenChats()
    }

    public static func setLocale(_ locale: String?) {
        self.locale = locale
        rebootOpenChats()
    }

    // MARK: UI

    /// A ready-to-present chat screen. Push it, present it, or embed it as a child.
    public static func makeChatViewController() -> UIViewController {
        precondition(configuration != nil, "Call SupportChat.configure(appId:serverURL:) first")
        return SupportChatChatViewController()
    }

    /// Presents the chat full screen from the top-most view controller (or `from`).
    public static func present(from presenter: UIViewController? = nil, animated: Bool = true) {
        guard configuration != nil else {
            assertionFailure("Call SupportChat.configure(appId:serverURL:) first")
            return
        }
        guard let presenter = presenter ?? topViewController() else { return }
        let chat = makeChatViewController()
        chat.modalPresentationStyle = .fullScreen
        presenter.present(chat, animated: animated)
    }

    // MARK: Push notifications

    /// When true (default) the app icon badge follows the chat's unread count.
    public static var syncsAppIconBadge = true

    /// Pass the token from `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`.
    /// `sandbox` must match the app's `aps-environment` (development builds use the sandbox).
    public static func setDeviceToken(_ deviceToken: Data, sandbox: Bool = isDebugBuild) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        guard let storage else { return }
        if storage.pushToken != hex { storage.pushRegisteredFor = nil }
        storage.pushToken = hex
        pushSandbox = sandbox
        Task { await registerPushIfNeeded() }
    }

    /// True when a notification payload came from SupportChat.
    public static func isSupportChatNotification(_ userInfo: [AnyHashable: Any]) -> Bool {
        userInfo["supportchat"] as? String == "1"
    }

    /// Opens the chat for a tapped SupportChat notification. Returns false for other notifications.
    /// Call from `userNotificationCenter(_:didReceive:withCompletionHandler:)`.
    @discardableResult
    public static func handleNotification(_ userInfo: [AnyHashable: Any]) -> Bool {
        guard isSupportChatNotification(userInfo) else { return false }
        if openChats.allObjects.contains(where: { $0.view.window != nil }) { return true }
        present()
        return true
    }

    /// Presentation options for a SupportChat notification arriving in the foreground: hidden while
    /// the chat is on screen (the message is already visible), a banner otherwise.
    public static func foregroundPresentationOptions(for userInfo: [AnyHashable: Any]) -> UNNotificationPresentationOptions {
        guard isSupportChatNotification(userInfo) else { return [] }
        if openChats.allObjects.contains(where: { $0.view.window != nil }) { return [] }
        return [.banner, .list, .sound]
    }

    nonisolated public static var isDebugBuild: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    // MARK: Unread

    /// Fetches the unread count from the server (cheap). Called automatically on launch and when
    /// the app becomes active, but only for devices that already have a conversation.
    public static func refreshUnreadCount() async {
        await refreshUnreadCount(force: false)
    }

    // MARK: - Internal state

    struct Configuration {
        let appId: String
        let serverURL: URL
    }

    static var configuration: Configuration?
    static var storage: Storage?
    static var user: SupportChatUser?
    static var userHash: String?
    static var locale: String?
    static var pendingAttributes: [String: SupportChatAttribute] = [:]
    static let openChats = NSHashTable<SupportChatChatViewController>.weakObjects()
    static var pushSandbox = isDebugBuild
    private static var foregroundObserver: NSObjectProtocol?

    static func bootPayload() -> [String: Any] {
        var payload: [String: Any] = [
            "platform": "ios",
            "showCloseButton": true,
        ]
        if let visitorId = storage?.visitorId { payload["visitorId"] = visitorId }
        if let user { payload["user"] = user.json }
        if let userHash { payload["userHash"] = userHash }
        if let locale { payload["locale"] = locale }
        if !pendingAttributes.isEmpty { payload["attributes"] = pendingAttributes.mapValues(\.json) }
        return payload
    }

    static func didStartSession(visitorId: String, token: String?, expiresAt: String?) {
        storage?.visitorId = visitorId
        if let token { storage?.token = token }
        if let expiresAt { storage?.tokenExpiry = ISO8601DateFormatter.supportChat.date(from: expiresAt) }
        Task { await registerPushIfNeeded() }
    }

    /// Registers the APNs token for the current visitor once (again after login/logout).
    static func registerPushIfNeeded() async {
        guard let configuration, let storage, let pushToken = storage.pushToken,
              let visitorId = storage.visitorId, storage.pushRegisteredFor != visitorId,
              let token = storage.validToken
        else { return }
        do {
            try await API(serverURL: configuration.serverURL)
                .registerDevice(pushToken: pushToken, sandbox: pushSandbox, token: token)
            storage.pushRegisteredFor = visitorId
        } catch let error as SupportChatError {
            delegate?.supportChat(didFailWith: error)
        } catch {}
    }

    static func setUnread(_ count: Int) {
        guard state.unreadCount != count else { return }
        state.unreadCount = count
        // UNUserNotificationCenter needs a real app bundle (not e.g. a unit-test runner).
        if syncsAppIconBadge, Bundle.main.bundleURL.pathExtension == "app" {
            if #available(iOS 16.0, *) {
                UNUserNotificationCenter.current().setBadgeCount(count) { _ in }
            } else {
                UIApplication.shared.applicationIconBadgeNumber = count
            }
        }
        delegate?.supportChat(didChangeUnreadCount: count)
        NotificationCenter.default.post(name: unreadCountDidChangeNotification, object: nil, userInfo: ["count": count])
    }

    private static func rebootOpenChats() {
        for chat in openChats.allObjects { chat.send("boot", bootPayload()) }
    }

    private static func observeForeground() {
        guard foregroundObserver == nil else { return }
        foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { _ in
            Task { @MainActor in await refreshUnreadCount() }
        }
    }

    private static func refreshUnreadCount(force: Bool) async {
        guard let configuration, let storage else { return }
        // Never create anonymous visitors for people who have not chatted yet.
        guard storage.visitorId != nil || (force && user != nil) else { return }
        let api = API(serverURL: configuration.serverURL)
        do {
            if let token = storage.validToken, let count = try? await api.unread(token: token) {
                setUnread(count)
                return
            }
            let session = try await api.createSession(appId: configuration.appId, payload: bootPayload())
            didStartSession(visitorId: session.visitorId, token: session.token, expiresAt: session.expiresAt)
            setUnread(session.unread)
        } catch let error as SupportChatError {
            delegate?.supportChat(didFailWith: error)
        } catch {
            delegate?.supportChat(didFailWith: SupportChatError(code: "network_error", message: error.localizedDescription))
        }
    }

    static func topViewController(_ base: UIViewController? = nil) -> UIViewController? {
        let root = base ?? UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .sorted { ($0.activationState == .foregroundActive ? 0 : 1) < ($1.activationState == .foregroundActive ? 0 : 1) }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?.rootViewController
        if let nav = root as? UINavigationController { return topViewController(nav.visibleViewController) ?? nav }
        if let tab = root as? UITabBarController, let selected = tab.selectedViewController { return topViewController(selected) }
        if let presented = root?.presentedViewController { return topViewController(presented) }
        return root
    }
}

/// SwiftUI-friendly observable state.
@MainActor
public final class SupportChatState: ObservableObject {
    @Published public internal(set) var unreadCount: Int = 0
    nonisolated init() {}
}

extension ISO8601DateFormatter {
    nonisolated(unsafe) static let supportChat: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}
