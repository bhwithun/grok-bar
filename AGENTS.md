# Grok Bar - Agent Instructions

Pure Android app (Kotlin, Jetpack Compose, Material 3) bootstrapped from `_template`.

## Purpose

Home-screen widget plus in-app screen that mirrors the Grok Settings → Usage gauge:
percent used, reset date, and a segmented progress bar for Grok Build, Chat, Voice, Imagine, and Automations.

## Package

`com.brian.grokbar`

```
com.brian.grokbar/
├── GrokBarApp.kt
├── MainActivity.kt
├── LoginActivity.kt
├── data/           # AppStorage, usage models, grok.com / CLI billing fetch
├── navigation/
├── ui/components/  # UsageGaugeCard
├── ui/screens/
├── ui/theme/       # Dark-only, electric blue #1A5EFF
├── viewmodel/
├── widget/         # Glance widget + bitmap gauge renderer
├── work/           # Periodic refresh
└── util/
```

## Data sources

1. grok.com session cookies via WebView sign-in → `GetGrokCreditsConfig` (gRPC-web protobuf)
2. Optional Grok Build bearer token → `https://cli-chat-proxy.grok.com/v1/billing?format=credits`

Never hardcode preference keys; use `AppStorage.Keys`.

## Widget

Glance `UsageWidget` draws the official-style gauge (electric-blue opacity steps, 1px gaps, unused track) as a bitmap. Refresh through `WidgetUpdater` after fetches and from `RefreshUsageWorker`.

## Theme

Dark-only. Keep color tokens in `Theme.kt`.
