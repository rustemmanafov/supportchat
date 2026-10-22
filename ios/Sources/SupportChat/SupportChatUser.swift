import Foundation

/// The signed-in user of the host app.
///
/// `id` is your own stable user id. When identity verification is on, also pass
/// `userHash = HMAC-SHA256(identitySecret, id)` computed on **your backend**.
public struct SupportChatUser: Equatable, Sendable {
    public var id: String
    public var name: String?
    public var email: String?
    public var phone: String?
    public var attributes: [String: SupportChatAttribute]

    public init(
        id: String,
        name: String? = nil,
        email: String? = nil,
        phone: String? = nil,
        attributes: [String: SupportChatAttribute] = [:]
    ) {
        self.id = id
        self.name = name
        self.email = email
        self.phone = phone
        self.attributes = attributes
    }

    var json: [String: Any] {
        var out: [String: Any] = ["id": id]
        if let name { out["name"] = name }
        if let email { out["email"] = email }
        if let phone { out["phone"] = phone }
        if !attributes.isEmpty { out["attributes"] = attributes.mapValues(\.json) }
        return out
    }
}

/// A custom attribute value shown to agents (plan, city, order count, …).
public enum SupportChatAttribute: Equatable, Sendable, ExpressibleByStringLiteral, ExpressibleByIntegerLiteral,
    ExpressibleByFloatLiteral, ExpressibleByBooleanLiteral
{
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)

    public init(stringLiteral value: String) { self = .string(value) }
    public init(integerLiteral value: Int) { self = .int(value) }
    public init(floatLiteral value: Double) { self = .double(value) }
    public init(booleanLiteral value: Bool) { self = .bool(value) }

    var json: Any {
        switch self {
        case .string(let v): return v
        case .int(let v): return v
        case .double(let v): return v
        case .bool(let v): return v
        }
    }
}

/// Errors reported by the widget or the SDK.
public struct SupportChatError: Error, Equatable, Sendable, CustomStringConvertible {
    /// Machine-readable code, e.g. `invalid_user_hash`, `network_error`, `app_not_found`.
    public let code: String
    public let message: String?

    public var description: String { message.map { "\(code): \($0)" } ?? code }
}
