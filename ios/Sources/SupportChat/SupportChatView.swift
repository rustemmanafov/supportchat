import SwiftUI

/// SwiftUI wrapper around the chat screen.
///
/// ```swift
/// .sheet(isPresented: $showChat) { SupportChatChatView() }
/// .fullScreenCover(isPresented: $showChat) { SupportChatChatView() }
/// ```
public struct SupportChatChatView: UIViewControllerRepresentable {
    public init() {}

    public func makeUIViewController(context: Context) -> UIViewController {
        SupportChat.makeChatViewController()
    }

    public func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
