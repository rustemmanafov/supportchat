# Changelog

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
