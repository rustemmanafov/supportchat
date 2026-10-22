import Foundation

/// Per-app persistence of the visitor id and the short-lived session token.
struct Storage {
    private let defaults: UserDefaults
    private let prefix: String

    init(appId: String, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.prefix = "supportchat.\(appId)."
    }

    var visitorId: String? {
        get { defaults.string(forKey: prefix + "visitorId") }
        nonmutating set { defaults.set(newValue, forKey: prefix + "visitorId") }
    }

    var token: String? {
        get { defaults.string(forKey: prefix + "token") }
        nonmutating set { defaults.set(newValue, forKey: prefix + "token") }
    }

    var tokenExpiry: Date? {
        get { defaults.object(forKey: prefix + "tokenExpiry") as? Date }
        nonmutating set { defaults.set(newValue, forKey: prefix + "tokenExpiry") }
    }

    /// APNs device token (hex) and the visitor it was last registered for.
    var pushToken: String? {
        get { defaults.string(forKey: prefix + "pushToken") }
        nonmutating set { defaults.set(newValue, forKey: prefix + "pushToken") }
    }

    var pushRegisteredFor: String? {
        get { defaults.string(forKey: prefix + "pushRegisteredFor") }
        nonmutating set { defaults.set(newValue, forKey: prefix + "pushRegisteredFor") }
    }

    /// The token if it is still valid for at least a minute.
    var validToken: String? {
        guard let token, let expiry = tokenExpiry, expiry.timeIntervalSinceNow > 60 else { return nil }
        return token
    }

    func clear() {
        // The APNs token belongs to the device, not the visitor: keep it for the next visitor.
        for key in ["visitorId", "token", "tokenExpiry", "pushRegisteredFor"] { defaults.removeObject(forKey: prefix + key) }
    }
}
