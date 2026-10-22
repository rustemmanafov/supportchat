import Foundation

/// The two REST calls the native layer needs (unread badge while the chat is closed).
/// Everything else happens inside the shared web widget.
struct API {
    let serverURL: URL
    var session: URLSession = .shared

    struct Session {
        let token: String
        let expiresAt: String
        let visitorId: String
        let unread: Int
    }

    func createSession(appId: String, payload: [String: Any]) async throws -> Session {
        var body = payload
        body["appId"] = appId
        let json = try await request("POST", "/v1/session", body: body)
        guard
            let token = json["token"] as? String,
            let expiresAt = json["expiresAt"] as? String,
            let visitor = json["visitor"] as? [String: Any],
            let visitorId = visitor["id"] as? String
        else { throw SupportChatError(code: "invalid_response", message: nil) }
        return Session(token: token, expiresAt: expiresAt, visitorId: visitorId, unread: json["unread"] as? Int ?? 0)
    }

    func registerDevice(pushToken: String, sandbox: Bool, token: String) async throws {
        _ = try await request("POST", "/v1/devices", body: [
            "platform": "ios",
            "token": pushToken,
            "environment": sandbox ? "sandbox" : "production",
        ], token: token)
    }

    func unregisterDevice(pushToken: String, token: String) async throws {
        _ = try await request("DELETE", "/v1/devices", body: ["platform": "ios", "token": pushToken], token: token)
    }

    func unread(token: String) async throws -> Int {
        let json = try await request("GET", "/v1/unread", token: token)
        return json["unread"] as? Int ?? 0
    }

    private func request(_ method: String, _ path: String, body: [String: Any]? = nil, token: String? = nil) async throws -> [String: Any] {
        var req = URLRequest(url: serverURL.appendingPathComponent(path))
        req.httpMethod = method
        req.timeoutInterval = 15
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("supportchat-ios/\(SupportChat.version)", forHTTPHeaderField: "X-SupportChat-Client")
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { req.httpBody = try JSONSerialization.data(withJSONObject: body) }

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: req)
        } catch {
            throw SupportChatError(code: "network_error", message: error.localizedDescription)
        }
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let error = json["error"] as? [String: Any]
            throw SupportChatError(code: error?["code"] as? String ?? "http_\(status)", message: error?["message"] as? String)
        }
        return json
    }
}
