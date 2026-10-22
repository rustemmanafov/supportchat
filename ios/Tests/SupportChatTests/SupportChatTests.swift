import XCTest
@testable import SupportChat

@MainActor
final class SupportChatTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SupportChat.configure(appId: "test-\(UUID().uuidString)", serverURL: URL(string: "http://127.0.0.1:9")!)
        SupportChat.logout()
    }

    func testAnonymousBootPayload() {
        let payload = SupportChat.bootPayload()
        XCTAssertEqual(payload["platform"] as? String, "ios")
        XCTAssertNil(payload["user"])
        XCTAssertNil(payload["visitorId"])
    }

    func testIdentifyAddsUserAndHash() {
        SupportChat.identify(SupportChatUser(id: "42", name: "Aysel", attributes: ["plan": "pro", "orders": 3]), userHash: "abc")
        let payload = SupportChat.bootPayload()
        let user = payload["user"] as? [String: Any]
        XCTAssertEqual(user?["id"] as? String, "42")
        XCTAssertEqual(user?["name"] as? String, "Aysel")
        XCTAssertEqual((user?["attributes"] as? [String: Any])?["orders"] as? Int, 3)
        XCTAssertEqual(payload["userHash"] as? String, "abc")
    }

    func testSessionIsStoredAndLogoutClearsIt() {
        SupportChat.didStartSession(visitorId: "vis_1", token: "tok", expiresAt: "2999-01-01T00:00:00.000Z")
        XCTAssertEqual(SupportChat.bootPayload()["visitorId"] as? String, "vis_1")
        XCTAssertEqual(SupportChat.storage?.validToken, "tok")
        SupportChat.logout()
        XCTAssertNil(SupportChat.bootPayload()["visitorId"])
        XCTAssertNil(SupportChat.storage?.token)
    }

    func testPushHelpers() {
        SupportChat.setDeviceToken(Data([0xde, 0xad, 0xbe, 0xef]), sandbox: true)
        XCTAssertEqual(SupportChat.storage?.pushToken, "deadbeef")
        XCTAssertTrue(SupportChat.isSupportChatNotification(["supportchat": "1", "aps": [:]]))
        XCTAssertFalse(SupportChat.isSupportChatNotification(["aps": [:]]))
        XCTAssertFalse(SupportChat.handleNotification(["other": true]))
        SupportChat.logout()
        XCTAssertEqual(SupportChat.storage?.pushToken, "deadbeef", "the device token survives logout")
    }

    func testUnreadNotification() {
        let exp = expectation(forNotification: SupportChat.unreadCountDidChangeNotification, object: nil) { note in
            note.userInfo?["count"] as? Int == 5
        }
        SupportChat.setUnread(5)
        wait(for: [exp], timeout: 1)
        XCTAssertEqual(SupportChat.unreadCount, 5)
    }
}
