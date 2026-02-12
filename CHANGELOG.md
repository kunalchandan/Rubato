# CHANGELOG (Rubato fork)
All changes since the upstream base.
Last updated: 2026-02-12

## Recent updates (2026-02-12)
- Introduced domain models and source adapters to unify multi-source media metadata.
- Added a unified LibraryRepository with cross-source aggregation, dedupe, and search indexing.
- Added a sync orchestrator with delta policies, offline gating, and cover-art prefetch/storage reporting.
- Refactored playback queue building and media-item mapping for unified source IDs.

## Recent updates (2026-02-06)
- Android Auto: removed the top-level "Made for you" browse page and disabled AA search commands to eliminate the split-screen "For you / What do you want to listen to?" surface.
- Android Auto: artist fallback artwork now embeds the Material Symbol icon data, ensuring Artists uses the correct icon when no artist art exists.
- Fixed metadata sync crashes from oversized cache rows by chunking large cached lists and capping payload sizes.
- Added cache chunk retrieval/cleanup helpers in Room DAO and CacheRepository.
- Subsonic sync now uses consistent request timeouts and no longer blocks on ping/scan status.
- Added scan-status timeout logging to keep sync progress visible even when the server is slow.

## Recent updates (2026-02-05)
- AA headers now show icons again (Recently Played, Made for you, Genres, etc.) via the AA artwork provider.
- AA song artwork now falls back to album art when track art is missing, restoring covers in AA song lists.
- AA artist cards no longer duplicate the artist name as a subtitle; artwork URIs now use the AA artwork provider.
- Android Auto UI simplified to a single library-style root with Recently Played + Made for you grid entries.
- Android Auto "Made for you" now surfaces multiple "Mix from <song>" tiles with cover art overlay badges.
- Added AA artwork content provider to serve mix-overlay covers and log art requests.
- AA song lists now fall back to search index when cache is local-only, improving multi-source coverage.
- AA browse logging expanded (AutoDiag) to validate node counts + artwork fetches.
- Fixed AA crash risk by moving chronology DB reads off the main thread during AA browse.
- Regenerated phone (light/dark), Android Auto (light/dark), and tablet (7"/10") screenshots with extended load delays.
- Screenshot automation now enforces a capture delay and dismisses ANR dialogs on slow devices.

## Recent updates (2026-02-04)
- Album page now reuses a single tracks LiveData seeded from the full Album object, fixing missing song lists when navigating from Library > Album.
- Home/Library navigation now preserves fragment state + scroll position across tab switches and back navigation (no forced refresh).
- Metadata sync status: new "Sync now" action in the popup dialog.
- Cache guard expanded for `songs_all` payloads to prevent deletes; sync counts now persist for large libraries.
- Discovery mix: if both local + remote tracks exist, the first item is forced to be remote to avoid local-leading bias.
- Library "See all albums" now orders remote sources ahead of local and sorts by title/artist for stable ordering.

## Recent updates (2026-02-03)
- Downloaded page actions now mirror album controls with Play/Shuffle/Filter; Play uses the on-screen order (group-aware).
- Metadata sync watchdog: detects stalls, logs them, and restarts; progress timestamps now update with sync activity.
- Subsonic sync hardened: per-album timeouts + failure logging for album track fetches.
- New Releases now fall back to Subsonic "newest" if the year-based list has no remote items.
- Visualizer: FFT-based capture + smoothing; settings preview is stable and slider values are clamped to valid steps.
- Home/Library/Genre/Filter no longer refresh on every sync tick; refresh happens only on sync completion to prevent UI churn.
- Horizontal adapters now update synchronously when unfiltered to avoid item loss from async filter races.
- Album tracks now fall back to local + cached Subsonic + cached Jellyfin metadata matching when direct lookup fails.
- Album/Song toolbars now show "Album |" / "Song |" prefixes; album download is a visible toolbar action (no overflow).
- Jellyfin add flow split into two steps: credentials -> library selection, with explicit Save.
- Home "New Releases" sorting hardened against null dates to prevent crashes.

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
- Optional local telemetry (SQLite/Room) for performance/action events with opt-in and "local only" disclosure.
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

