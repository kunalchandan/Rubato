# TODO (Consolidated)
Last reviewed: 2026-02-02

## In progress
- Offline-first UX: cached metadata lists (playlists/artists/albums/genres/songs) and cover art prefetch are in place; remaining gaps include auditing every surface + bottom sheets to disable network-only actions while offline (home/library refresh + scan/download guards are now in place).
- Local sources + multi-source UI: local library merging is partially integrated; Jellyfin browse merge still incomplete for some surfaces (home/genres/etc.) and needs full validation.
- Android Auto: media button handling added; AAOS MediaBrowser tests now pass; DHU validation still pending (requires a physical device/head unit server), and PR #391 comparison is pending a link.
- Home screen widgets: Now Playing 2x4 + circular widget implemented; circle widget validated on emulator home screen, but 2x4 still needs pinning/UX validation (launcher Add failed due to no free space).
- Music visualizer: cover action option + overlay + config UI implemented; still needs emulator validation and perf/battery profiling.
- Jellyfin + Subsonic dedupe: library-level dedupe now applied when merging album/artist/playlist catalog data; needs validation with mixed libraries.
- Jellyfin integration: metadata sync + search index + tagged stream/cover support in place; browse/search UI now wired for albums/artists/playlists via cached Jellyfin data, with album/artist/playlist pages loading from cache. Still needs validation with Navidrome/Subsonic and deeper Jellyfin browse coverage.
- QA/validation: API 16 launch verified; Pixel 9 + Automotive AAOS tests run; still need multi-AVD coverage (API 29/33/35 + tablet) and DHU Android Auto validation.

## Not started / needs work
- Home playlists visibility: ensure playlists appear on Home when selected in the reorganize screen (still reported missing).

## Done recently
- App id/package/deeplink updated to `one.chandan.rubato`; version bumped to 4.0.0.
- Android Auto: hardened MediaBrowser queue reconstruction + explicit media button handling; AutomotiveMediaBrowserTest timeout stabilized.
- Fixed empty local folder crashes by hardening MediaStore/SAF parsing and null-safe local source binding.
- Add music sources header cleanup: hide Subsonic app-bar header when no servers exist.
- Queue model now tracks separate Play Next + Coming Up queues; play-next inserts stay ahead of queued items.
- Download notifications now show title + artist, accurate x/y progress, and open album/player on tap (no options sheet).
- Metadata sync status popup now shows sync start time while active and totals more cached metadata (album tracks, playlist songs, info, lyrics, Jellyfin).
- Theme picker includes more options (Lagoon, Dune, Obsidian, Aurora) with light/dark sections + custom color picker.
- Offline banner + metadata sync status icon in Downloaded banner.
- Metadata sync caches playlists, artists, albums, album tracks, genres, and a unified `songs_all` list; cover art is prefetched and cached; cache-only loads used when offline.
- Non-downloaded songs are greyed out in offline lists.
- Offline search now uses a unified Room search index seeded from Subsonic metadata; cached-response fallback still used if the index is empty.
- Offline search now includes playlists (indexed + cached fallback) with a dedicated search section.
- Playlist cover persistence: deterministic collage selection + cached composite generation from playlist songs.
- Local telemetry: Room-backed `telemetry_event` storage with an opt-in switch and “local only” disclosure in Settings.
- Jellyfin server dialog now uses a dropdown populated with the available music libraries (no manual typing).
- Music Sources screen uses card sections for Subsonic/Jellyfin/Local and supports multiple sources per type + local folder picker.
- Album art rounding increased; Discovery cards and Flashback cards rounded; Flashback decades stylized with fonts + gradients.
- Primary app icons + title now use the accent color.
- Browse Genres: added sort by Most Songs.
- Genre pages: added Related genres row (computed from cached metadata).
- Visualizer design doc created (`docs/visualizer-design.md`).
- Widgets added: Now Playing 2x4 and circular control widgets.
- Swipe-to-queue configuration: left/right action settings + trail labels + green trail + haptics (now consistent across playlists too).
- Performance profiling: ran per-screen gfxinfo traces (Home/Library/Downloaded) and applied first optimizations (shared RecyclerView pools on Home/Library, Downloaded list cache sizing + diff updates).

