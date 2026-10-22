# Changelog

## SDKs 1.0.1 — 2026-10-22

- iOS and Android only open http(s), mailto and tel links from the chat; other schemes are ignored.
- iOS accepts bridge messages only from the widget's main frame; sub-frames cannot navigate away.
- Works with the server's resume keys (the SDK stores whatever the widget hands it).

## Server 1.2.0 — 2026-10-22

- Security review fixes: SMTP header injection guard and email validation, webhook SSRF protection
  (https only, internal addresses refused at connect time, no redirects), SSE tickets instead of
  credentials in URLs, live streams closed on logout/password change/removal, app key and identity
  secret rotation, secret visitor resume keys, deleted App IDs reserved, quota counts only answered
  conversations, per-network limits, bounded attributes, abandoned upload cleanup, async scrypt,
  per-account login limit, IPv6 /64 rate keys, CSP and security headers on every page,
  encrypted off-site backups with retention and erasure.

## Server 1.1.0 — 2026-10-22

- Self-service signup, workspace onboarding (Install panel with ready code), plans and limits
  (operators, conversations/month, storage, push, branding), owner console with MRR.
- Email notifications via a built-in SMTP client; password reset by email; daily automatic backups.
- Marketing site with live pricing, FAQ and draft legal pages; demo moved to /demo.
- SDKs unchanged (1.0.0).

## 1.0.0 — 2026-10-22

First release.

- Server: REST + SSE realtime, SQLite, live widget config, identity verification (HMAC),
  webhooks, rate limits, migrations, online backups. Zero runtime dependencies.
- Agents: email/password accounts, admin and agent roles, assignment, "Mine" view.
- Files: images, PDF, text up to 10 MB, content-checked, sandboxed serving.
- Push: APNs (HTTP/2, ES256) and FCM HTTP v1, sent only when the visitor is offline.
- Web SDK: one script tag or `@supportchat/web` on npm, Shadow DOM + iframe isolation, typings.
- iOS SDK: Swift Package (iOS 15+), UIKit + SwiftUI, unread state, push helpers, badge sync.
- Android SDK: minSdk 23, no AndroidX, notifications, file picker, JitPack/Maven publishing.
- Deploy: Dockerfile, docker compose with Caddy (automatic HTTPS), nginx and systemd samples.
