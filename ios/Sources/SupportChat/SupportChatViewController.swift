import SafariServices
import UIKit
import WebKit

/// Hosts the shared SupportChat chat widget in a WKWebView and bridges it to the native SDK.
final class SupportChatChatViewController: UIViewController {
    private var webView: WKWebView!
    private let errorView = ErrorView()
    private let spinner = UIActivityIndicatorView(style: .medium)
    private var widgetReady = false

    override func loadView() {
        let contentController = WKUserContentController()
        contentController.add(WeakMessageHandler(self), name: "supportchat")

        let config = WKWebViewConfiguration()
        config.userContentController = contentController
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.dataDetectorTypes = []

        webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.isOpaque = false
        webView.backgroundColor = .systemBackground
        webView.scrollView.backgroundColor = .systemBackground
        // The widget handles the top safe area itself (viewport-fit=cover + env()).
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        // The page never scrolls as a whole (only the message list does), so iOS can't
        // shove the header off-screen when the keyboard appears.
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.allowsLinkPreview = false
        #if DEBUG
        if #available(iOS 16.4, *) { webView.isInspectable = true }
        #endif

        let root = UIView()
        root.backgroundColor = .systemBackground
        for sub in [webView!, errorView, spinner] as [UIView] {
            sub.translatesAutoresizingMaskIntoConstraints = false
            root.addSubview(sub)
        }
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: root.topAnchor),
            // Ends at the keyboard (or the bottom safe area when it's hidden).
            webView.bottomAnchor.constraint(equalTo: root.keyboardLayoutGuide.topAnchor),
            webView.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            errorView.centerXAnchor.constraint(equalTo: root.centerXAnchor),
            errorView.centerYAnchor.constraint(equalTo: root.centerYAnchor),
            errorView.leadingAnchor.constraint(greaterThanOrEqualTo: root.leadingAnchor, constant: 24),
            spinner.centerXAnchor.constraint(equalTo: root.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: root.centerYAnchor),
        ])
        errorView.isHidden = true
        errorView.onRetry = { [weak self] in self?.load() }
        errorView.onClose = { [weak self] in self?.closeTapped() }
        view = root
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        SupportChat.openChats.add(self)
        load()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        send("visibility", ["visible": true])
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        send("visibility", ["visible": false])
    }

    // MARK: Loading

    private var widgetURL: URL? {
        guard let config = SupportChat.configuration else { return nil }
        var components = URLComponents(
            url: config.serverURL.appendingPathComponent("widget/frame.html"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "appId", value: config.appId),
            URLQueryItem(name: "host", value: "ios"),
            URLQueryItem(name: "v", value: SupportChat.version),
        ]
        return components?.url
    }

    private func load() {
        guard let url = widgetURL else { return }
        widgetReady = false
        errorView.isHidden = true
        webView.isHidden = false
        spinner.startAnimating()
        webView.load(URLRequest(url: url, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 20))
    }

    private func showError() {
        spinner.stopAnimating()
        webView.isHidden = true
        errorView.isHidden = false
    }

    // MARK: Bridge

    /// Sends a message to the widget (`window.SupportChat.receive`).
    func send(_ type: String, _ payload: [String: Any] = [:]) {
        guard widgetReady, isViewLoaded else { return }
        let message: [String: Any] = ["source": "supportchat", "type": type, "payload": payload]
        guard
            let data = try? JSONSerialization.data(withJSONObject: message),
            let json = String(data: data, encoding: .utf8)
        else { return }
        webView.evaluateJavaScript("window.SupportChat && window.SupportChat.receive(\(json));", completionHandler: nil)
    }

    fileprivate func handle(_ body: Any) {
        guard
            let message = body as? [String: Any],
            message["source"] as? String == "supportchat",
            let type = message["type"] as? String
        else { return }
        let payload = message["payload"] as? [String: Any] ?? [:]

        switch type {
        case "ready":
            widgetReady = true
            spinner.stopAnimating()
            send("boot", SupportChat.bootPayload())
            if view.window != nil { send("visibility", ["visible": true]) }
        case "session":
            if let visitorId = payload["visitorId"] as? String {
                SupportChat.didStartSession(
                    visitorId: visitorId,
                    token: payload["token"] as? String,
                    expiresAt: payload["expiresAt"] as? String
                )
            }
        case "unread":
            SupportChat.setUnread(payload["count"] as? Int ?? 0)
        case "message":
            if let msg = payload["message"] as? [String: Any], let text = msg["body"] as? String {
                SupportChat.delegate?.supportChat(didReceiveMessage: text, from: msg["authorName"] as? String)
            }
        case "link":
            if let string = payload["url"] as? String, let url = URL(string: string) { open(url) }
        case "close":
            closeTapped()
        case "error":
            let error = SupportChatError(code: payload["code"] as? String ?? "unknown", message: payload["message"] as? String)
            SupportChat.delegate?.supportChat(didFailWith: error)
        default:
            break
        }
    }

    private func closeTapped() {
        if let delegate = SupportChat.delegate, !delegate.supportChatShouldDismiss(self) { return }
        if let nav = navigationController, nav.viewControllers.first !== self {
            nav.popViewController(animated: true)
        } else if presentingViewController != nil {
            (navigationController ?? self).dismiss(animated: true)
        }
    }

    /// Only web, mail and phone links leave the chat; other schemes (other apps' deep links) are ignored.
    private func open(_ url: URL) {
        switch url.scheme?.lowercased() {
        case "http", "https":
            present(SFSafariViewController(url: url), animated: true)
        case "mailto", "tel":
            UIApplication.shared.open(url)
        default:
            break
        }
    }

    fileprivate func isWidgetURL(_ url: URL?) -> Bool {
        guard let url, let base = SupportChat.configuration?.serverURL else { return false }
        return url.scheme == base.scheme && url.host == base.host && url.port == base.port
            && url.path.contains("/widget/")
    }
}

// MARK: - WKNavigationDelegate, WKUIDelegate

extension SupportChatChatViewController: WKNavigationDelegate, WKUIDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        let url = navigationAction.request.url
        // Every frame stays on the widget; anything else is opened outside (or ignored).
        if isWidgetURL(url) || url?.scheme == "about" {
            return .allow
        }
        // Anything else (links, redirects) leaves the chat and opens outside.
        if let url { open(url) }
        return .cancel
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        showError()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { showError() }
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        if let http = navigationResponse.response as? HTTPURLResponse, http.statusCode >= 400, navigationResponse.isForMainFrame {
            showError()
            return .cancel
        }
        return .allow
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        load()
    }

    // target=_blank links
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if let url = navigationAction.request.url { open(url) }
        return nil
    }
}

// MARK: - Helpers

/// WKUserContentController retains its handlers; this breaks the cycle.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: SupportChatChatViewController?

    init(_ target: SupportChatChatViewController) {
        self.target = target
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        // Only the widget page itself may talk to the SDK, never an embedded frame.
        guard message.frameInfo.isMainFrame else { return }
        MainActor.assumeIsolated { target?.handle(message.body) }
    }
}

/// Shown only when the widget page itself can't load (offline, server down).
private final class ErrorView: UIStackView {
    var onRetry: (() -> Void)?
    var onClose: (() -> Void)?

    init() {
        super.init(frame: .zero)
        axis = .vertical
        alignment = .center
        spacing = 12

        let icon = UIImageView(image: UIImage(systemName: "wifi.exclamationmark"))
        icon.tintColor = .secondaryLabel
        icon.preferredSymbolConfiguration = .init(pointSize: 36)

        let label = UILabel()
        label.text = NSLocalizedString("Couldn't load the chat.", comment: "SupportChat error")
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.numberOfLines = 0

        var retryConfig = UIButton.Configuration.filled()
        retryConfig.title = NSLocalizedString("Try again", comment: "SupportChat retry")
        retryConfig.cornerStyle = .capsule
        let retry = UIButton(configuration: retryConfig, primaryAction: UIAction { [weak self] _ in self?.onRetry?() })

        var closeConfig = UIButton.Configuration.plain()
        closeConfig.title = NSLocalizedString("Close", comment: "SupportChat close")
        let close = UIButton(configuration: closeConfig, primaryAction: UIAction { [weak self] _ in self?.onClose?() })

        [icon, label, retry, close].forEach(addArrangedSubview)
    }

    @available(*, unavailable)
    required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}
