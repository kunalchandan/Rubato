# Jellyfin Support Investigation and Design

Status: partial implementation
Date: 2026-02-02
Owner: Codex (implementation + remaining design)

## Goals
- Add Jellyfin as a first-class music source alongside Subsonic and local folders.
- Provide a unified library and search experience across sources.
- Preserve offline-first UX by caching metadata, art, and playlists per source.

## Non-goals (initial phase)
- Video support, TV, and photos.
- Server-side management (library edits, metadata editing, remote image selection).
- Full parity with all Subsonic features (e.g., radios, podcasts).

## Current app architecture (relevant pieces)
- Network: Subsonic Retrofit clients live in App.getSubsonicClientInstance(...).
- Caching: CacheRepository persists serialized payloads in Room (CachedResponse).
- Offline: Repository fallbacks read cached payloads when offline.
- Metadata sync: MetadataSyncManager pulls playlists/artists/albums/tracks, prefetches art/lyrics, and stores cache keys (e.g., artists_all, albums_all, album_tracks_<id>, playlist_songs_<id>, songs_all).
- Search index: Room table `library_search_entry` stores a unified, normalized search index across sources for offline search.

## Implemented so far
- Jellyfin server auth + library selection (dropdown) with saved server config.
- Jellyfin metadata sync pipeline:
  - Fetch artists/albums/songs/playlists via `/Users/{userId}/Items` and playlist entries via `/Playlists/{playlistId}/Items`.
  - Cache keys per server (`jf_<serverId>_*`) plus playlist entry caches for tagged playlist ids.
  - Populate `library_search_entry` with Jellyfin entries (source = `jellyfin`).
- Tagged id scheme: `jellyfin:<serverId>:<itemId>` used for search results and cache keys.
- Playback + artwork URL resolution for tagged ids:
  - Stream: `/Audio/{itemId}/stream?api_key=<token>`
  - Artwork: `/Items/{itemId}/Images/Primary?api_key=<token>`
- Metadata sync stage includes Jellyfin in the UI log/status.

## Known gaps (still pending)
- Jellyfin browse UI integration (artist/album/playlist pages).
- Jellyfin search results should open browse pages (currently only playback is supported from search).
- Library-level dedupe across Subsonic/Jellyfin beyond search results.

## Jellyfin API research (what is available)
Primary references are the Jellyfin OpenAPI spec and official docs.

Auth and session
- POST /Users/AuthenticateByName authenticates a user and returns AuthenticationResult.
- AuthenticationResult includes AccessToken (token to reuse for API calls).
- OpenAPI security scheme indicates an Authorization header is used for API key auth.

Library / discovery
- GET /UserViews lists the user library "views" (music libraries, etc.).
- GET /Items is the primary query endpoint for items (artists, albums, songs, playlists).
- GET /Items/Latest returns recent items (useful for incremental sync).
- GET /Items/{itemId} returns item details.
- GET /Playlists/{playlistId}/Items returns playlist entries.

Media assets
- GET /Items/{itemId}/Images (and image-type variants) serves artwork.
- GET /Audio/{itemId}/stream (and container variants) serves audio streams.

API docs
- Jellyfin servers host Swagger docs at /api-docs/swagger for interactive testing.

## Proposed architecture

### 1) Source model
Introduce a unified MediaSource model and registry:
- type: SUBSONIC, JELLYFIN, LOCAL
- id: stable id (server id, or local source id)
- displayName
- auth/connection data

Create a JellyfinServer entity (Room) similar to Server, with:
- serverId, serverName, baseUrl, username
- accessToken, userId, deviceId (or client id)
- lastValidatedAt, lastSyncAt

### 2) Jellyfin network layer
- Retrofit client with baseUrl per server.
- Add an interceptor to attach Authorization header (token from AuthenticateByName).
- Implement service interfaces aligned with the OpenAPI endpoints:
  - User auth: /Users/AuthenticateByName
  - Library root: /UserViews
  - Items query: /Items, /Items/Latest, /Items/{itemId}
  - Playlists: /Playlists/{playlistId}/Items
  - Images: /Items/{itemId}/Images
  - Stream: /Audio/{itemId}/stream

### 3) Data model mapping
Option A (low refactor): Map Jellyfin responses into existing Subsonic models used by UI:
- ItemDto -> Child (for songs)
- Album/Artist -> AlbumID3/ArtistID3
- Use custom wrapper metadata for jellyfin-specific fields (tags, provider ids)

Option B (medium refactor, recommended): Introduce app-level domain models:
- MediaSong, MediaAlbum, MediaArtist, MediaPlaylist
- Each includes sourceId + sourceType + externalId
- UI adapters accept domain models; mapping to Media3 remains in MediaManager

### 4) Metadata caching and offline flow
- Keep CacheRepository as the storage layer (Room-backed JSON). Add per-source key prefixing:
  - jf_<serverId>_artists_all
  - jf_<serverId>_albums_all
  - jf_<serverId>_album_tracks_<albumId>
  - jf_<serverId>_playlists
  - jf_<serverId>_playlist_songs_<playlistId>
  - jf_<serverId>_songs_all
- Update repositories to read cached Jellyfin data when offline or when server is unreachable.
- Cover art caching: prefetch images using /Items/{itemId}/Images and store in Glide cache.
- Populate `library_search_entry` with Jellyfin artists/albums/songs so offline search uses the unified index.

### 5) Metadata sync integration
Refactor MetadataSyncManager into source-specific sync providers:
- SubsonicMetadataSyncProvider (existing logic)
- JellyfinMetadataSyncProvider (new)
- LocalMetadataSyncProvider (optional for local indexing)

Jellyfin sync outline:
1) Authenticate or refresh token if needed.
2) Fetch /UserViews for music libraries.
3) For each library view:
   - Fetch artists, albums, and songs via /Items with IncludeItemTypes filters.
   - Fetch playlist list and entries via /Playlists/{playlistId}/Items.
4) Store cache keys per source.
5) Prefetch cover art and artist images.
6) Optional: use /Items/Latest for incremental updates to reduce full resync costs.

### 6) Search integration
- Extend SearchingRepository to query across sources.
- For Jellyfin: use /Items with search/filters and cache results for offline suggestions.

### 7) Downloads and playback
- Use /Audio/{itemId}/stream for playback when online.
- For downloads, use the same streaming URL with auth header and DownloadUtil.
- Persist download metadata with sourceId to allow offline playback resolution.

### 8) UI changes
- Music Sources screen: enable Jellyfin card and add flow for adding a server.
- Library, Home, Search: show merged results (by source) with source badges.
- Offline banner + metadata status should aggregate across sources (partial/full icons).

## Implementation phases

Phase 0: API validation and auth
- Add Jellyfin server model and auth flow (AuthenticateByName).
- Validate token usage and server connectivity.
Status: done

Phase 1: Read-only library
- Fetch /UserViews and list artists/albums/songs with /Items.
- Render in existing lists (source-labeled).
Status: data fetch + caching done; UI integration pending

Phase 2: Playback + playlists
- Add stream playback via /Audio/{itemId}/stream.
- Add playlists via /Playlists/{playlistId}/Items.
Status: stream + playlist caching done; playlist UI integration pending

Phase 3: Offline sync
- Integrate Jellyfin into MetadataSyncManager provider model.
- Prefetch images and store metadata per source.
Status: metadata sync + search index done; cover art prefetch is wired via tagged ids

Phase 4: Multi-source UX
- Dedupe/merge policies (optional): by external provider id, then by name+artist+album.
- Unified queue behavior across sources.
Status: search-level dedupe done (canonical signature + source preference); library-level dedupe pending

## Risks and open questions
- Token expiration / refresh behavior (document server responses and retry policy).
- Large libraries: need pagination and incremental sync to avoid long first sync.
- Dedupe correctness across sources.
- Artwork URLs may require token or image tag parameters; cache must include those.
- Some servers may not expose artist images; fallback to album art.

## Suggested next steps
- Implement JellyfinServer persistence and authentication flow.
- Add a minimal JellyfinRepository (UserViews + Items) and log results.
- Prototype sync provider that stores jf_<serverId>_artists_all and jf_<serverId>_albums_all.
