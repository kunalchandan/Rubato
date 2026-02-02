# CHANGELOG (Rubato fork)
All changes since upstream Tempo.
Last updated: 2026-02-02

## Branding + UI refresh
- App name updated to Rubato; package/app id/deeplink updated to `one.chandan.rubato`.
- Splash screen + icons updated for Rubato branding.
- Download page renamed to "Downloaded"; banner layout adjusted (Shuffle all + Filter).
- Rounded/squircle cover art across grids/lists; placeholders now rounded too.
- Discovery + Flashback cards restyled with larger radii, fonts, and gradient backgrounds per decade.
- Accent color applied consistently to primary icons + title.
- Theme picker overhaul: light/dark grouping, additional themes, custom color picker.
- Settings search bar with live filtering (name + description).

## Core library, playback, and offline-first
- Local metadata cache (Room) for playlists, artists, albums, genres, album tracks, and a unified `songs_all` list.
- Offline UI behavior: cached lists stay visible when offline, non-downloaded songs greyed out, and cache-only loads when offline.
- Metadata sync status UI with download logs and cache size reporting.
- Cover art prefetch + cache, including background metadata fetches.
- Offline search index backed by Room, seeded from cached Subsonic metadata.
- Offline search expanded to include playlists, with cached fallback when index is empty.
- Queue model split into Play Next (priority) and Coming Up.
- Swipe-to-queue: configurable left/right actions (play next/add to queue/like/none), green trail + haptics.
- Download notifications: now show title + artist, x/y queue progress, and open album/player on tap.
- Per-screen gfxinfo traces captured (Home/Library/Downloaded) with targeted RecyclerView optimizations.
- Shared RecyclerView pools for heavy lists; list caching tuned for Downloaded.
- Optional local telemetry (SQLite/Room) for performance/action events with opt-in and “local only” disclosure.
- Playlist cover persistence: deterministic collage caching for playlists with no explicit cover.
- Metadata sync now includes lyrics and additional non-audio metadata where available.
- Search suggestions show images and subtitles (type labels).

## Multi-source music
- Music Sources screen with cards for Subsonic, Jellyfin, and Local sources.
- Local sources: folder picker + multiple folders.
- Jellyfin: server dialog with library dropdown, metadata sync + search index + stream/cover support.
- Source dedupe: canonical signature dedupe across sources (library-level merge where wired).

## Android Auto / Media
- MediaLibraryService hardened for AA queue reconstruction when session metadata is missing.
- Explicit media button handling (play/pause/next/prev/stop) in MediaLibraryService callback.
- Automotive MediaBrowser instrumentation test stabilized (timeouts adjusted).

## Widgets
- Home screen widgets: Now Playing 2x4 widget + circular control widget.
- Widget configuration + update hooks.

## QA / test automation
- Managed device matrix for multi-AVD coverage.
- UI smoke + interaction tests, Android Auto test, and macrobenchmarks documented in `docs/AUTOMATED_TESTS.md`.

## Notes / partial work (tracked in TODO)
- Jellyfin browse UI wiring and full library merge validation still in progress.
- DHU Android Auto validation requires a physical device/head unit server.
