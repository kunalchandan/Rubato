# Architecture and Implementation Plan (Consolidated)
Last updated: 2026-02-02

This document consolidates the architecture changes report and the implementation plan. It focuses on the structural gaps that block scale, reliability, multi-source features (Subsonic + Jellyfin + Local), offline-first UX, Android Auto robustness, widgets, performance, and testability.

## Executive summary
Tempo's architecture was centered on a single Subsonic server and online-first flows. The expanded scope requires:
1) source-agnostic domain models and a unified data layer,
2) a first-class sync + cache pipeline (metadata and artwork),
3) consistent offline behavior across all surfaces,
4) explicit separation of UI state from network logic,
5) queue/playback management that can span multiple sources,
6) deterministic performance instrumentation and regression gates.

Without these changes, the app will keep accumulating one-off fixes and edge-case regressions (especially around offline mode, multi-source dedupe, and Android Auto).

## Goals
- Reduce crash/ANR risk in core playback, queue, and repository flows.
- Improve security for network traffic and stored credentials.
- Standardize build configuration to be IDE- and CI-safe.
- Make large UI surfaces easier to test and maintain.

## Scope (from review)
- Build/Gradle configuration
- Threading and DB access patterns in repositories
- Media/Downloader services and playback UI fragments
- Security configuration and credential storage
- Test coverage for core utilities

## Guiding principles
- Minimize user-visible regressions.
- Prefer incremental refactors with measurable checkpoints.
- Keep behavior stable while changing internals.

## Current state (from codebase)
- Subsonic-specific models (Child/AlbumID3/ArtistID3) are passed across UI, repositories, and MediaManager.
- CacheRepository stores JSON payloads, but it is not a structured, source-aware data layer.
- Metadata sync exists, but is mostly a procedural pipeline; the offline UX depends on ad-hoc fallback paths.
- Search index exists in Room, but it is not fully unified for all source types and does not drive all search surfaces.
- MediaManager and queue logic are mutable and multi-threaded without a centralized concurrency model.
- Android Auto uses a MediaLibraryService + AutomotiveRepository with synchronous DB access.
- Widgets and visualizer exist, but they bypass a consistent UI state model.

## Review findings (priority)
High priority
- Kotlin sources exist but the app module does not apply the Kotlin Android/parcelize plugins; builds can become IDE-dependent or fail in CI. Add org.jetbrains.kotlin.android and kotlin-parcelize to app/build.gradle and remove the manual compiler/runtime wiring if you standardize on plugins.
- UI-thread blocking risk from manual Thread + join() patterns in repository reads/writes; this can ANR if called from UI or Media callbacks. Move to Room async APIs (LiveData/Flow), coroutines/Dispatchers, or a shared executor.
- Potential NPE when queue is empty: getLastPlayedMediaIndex() / getLastPlayedMediaTimestamp() dereference queueDao.getLastPlayed() without null checks. Guard nulls and return safe defaults.
- Cleartext traffic is globally allowed and credentials/tokens are stored in plain SharedPreferences; this is high security exposure on hostile networks. Consider per-server cleartext opt-in and EncryptedSharedPreferences/Keystore for secrets.

Medium priority
- fallbackToDestructiveMigration() can wipe user data even though auto migrations exist; consider gating this to debug builds or removing for release.
- MoreExecutors.directExecutor() is used for MediaBrowser futures that update UI; callbacks may run off the main thread. Use a main-thread executor or Handler/lifecycleScope to marshal UI work.
- Handlers post delayed work without lifecycle cancellation in some fragments; this can update a detached view. Clear callbacks in onStop/onDestroyView.
- Static mutable queues in MediaManager are not synchronized; concurrent mutations (media callbacks + UI actions) can race. Consider synchronization or moving to a single-threaded queue manager.
- Network error handling often logs or silently ignores failures; add structured logging and/or user feedback for key flows (queue sync, scrobble, download export).

Maintainability / rework candidates
- Large "god fragments" mix data fetching, presentation, and navigation; splitting into smaller controllers or feature-specific view models will reduce regression risk.
- Hardcoded UI strings bypass localization and consistency; move to strings.xml.
- All product flavors share the same applicationId; if you intend side-by-side installs (e.g., F-Droid vs Play), add applicationIdSuffix or distinct ids.
- Gradle properties include legacy flags (constraints, unique package names, etc.); prune to reduce noise and future incompatibility.

Testing and QA gaps
- Instrumentation coverage is solid for UI flows, but core logic lacks unit tests (e.g., dedupe, search index, offline cache). Consider adding JVM tests around utilities and repositories.

## Required architecture changes (grouped by area)

### 1) Source abstraction + domain model layer (highest priority)
Problem:
UI and repositories are coupled to Subsonic models; Jellyfin and Local are retrofitted via tagged IDs and custom parsing.

Required changes:
- Introduce domain models: MediaSong, MediaAlbum, MediaArtist, MediaPlaylist, MediaImage.
- Add a MediaSource registry:
  - id, type (SUBSONIC/JELLYFIN/LOCAL), displayName, status, auth state.
- Implement SourceAdapter per source (mapping source DTO -> domain model).
- Define a canonical identifier schema: sourceType:sourceId:externalId.
- Make UI + ViewModels consume domain models only.

Outcome:
You can merge, dedupe, and present sources consistently without spreading source-specific logic across UI and services.

### 2) Unified repository + aggregation layer
Problem:
Each repository is source-specific and handles offline fallbacks independently; aggregation is fragmented.

Required changes:
- Add a LibraryRepository that aggregates:
  - browse lists, album/artist/playlist pages, recommendations, recent, etc.
  - handles dedupe + source preference rules.
- Move offline handling to the aggregation layer (not per UI surface).
- Centralize error handling (network down vs server unreachable vs auth failure).
- Store per-source paging state and refresh timestamps.

Outcome:
Offline behavior and multi-source merging become consistent and testable.

### 3) Offline-first data contract
Problem:
Offline requirements are scattered; some UI surfaces still assume network availability.

Required changes:
- Define a formal offline contract:
  - Which metadata is required offline (lists, album/artist/playlist entries, art).
  - Which actions are disabled (refresh, download from server, etc.).
- Add a single OfflinePolicy check used by all ViewModels and action handlers.
- Ensure all lists can render from cache, even with partial data.

Outcome:
Predictable offline UX across Home/Library/Search/Downloaded/Player/Auto.

### 4) Sync + cache pipeline refactor
Problem:
Metadata sync is a procedural pipeline; artwork prefetch is side-effected and hard to validate.

Required changes:
- Split into source-specific sync providers:
  - SubsonicSyncProvider, JellyfinSyncProvider, LocalSyncProvider.
- Introduce a SyncOrchestrator:
  - schedules and serializes syncs,
  - supports incremental sync (delta) per source,
  - exposes progress + storage usage to UI.
- Use WorkManager for reliable background sync scheduling.
- Move artwork prefetching into a dedicated ArtPrefetcher queue with retry and throttling.

Outcome:
Reliable, observable, and measurable sync that can scale to large libraries.

### 5) Search architecture consolidation
Problem:
Search is partially unified; some surfaces still hit network and others rely on cached fallback.

Required changes:
- Single SearchRepository with:
  - offline search (Room index),
  - online search per source,
  - merged result ranking and dedupe.
- Search index schema should store sourceType, sourceId, displayName, album/artist relationships.
- Standardize search results to domain model + source badge.

Outcome:
Search works consistently online and offline, and does not duplicate results.

### 6) Playback + queue manager re-architecture
Problem:
MediaManager uses mutable global state and multiple threads; queue inserts race with UI operations.

Required changes:
- Create a QueueManager service with:
  - PlayNext queue + ComingUp queue as first-class,
  - single-threaded executor or coroutine scope,
  - state flow observable by UI + Auto.
- Standardize MediaItem building from domain model (single builder).
- Persist queue state in a normalized schema (source-aware IDs).

Outcome:
Queue behavior becomes deterministic, testable, and stable under heavy UI interactions.

### 7) Android Auto / MediaLibraryService hardening
Problem:
Auto relies on AutomotiveRepository and synchronous DB lookups; queue reconstruction can fail.

Required changes:
- Build a dedicated AutoLibraryRepository based on domain models.
- Use async DB access (Room + background executor).
- Guarantee MediaBrowserTree uses cached metadata and falls back to stable MediaItems.
- Add structured logging and Auto-specific health checks.

Outcome:
Android Auto remains stable even with partial data and offline states.

### 8) Telemetry / profiling pipeline
Problem:
Local telemetry exists but is not integrated with performance workflows.

Required changes:
- Define a stable Event schema (screen, action, duration, source, error).
- Add per-screen performance markers for startup + list rendering.
- Provide a local export function for diagnostics.

Outcome:
Performance regressions are measurable and traceable to actions.

### 9) UI architecture + state ownership
Problem:
Large fragments mix UI + data fetching; state updates are not centralized.

Required changes:
- Move to ViewModel-driven state:
  - UI observes immutable state (loading, error, offline, list).
- Break large fragments into smaller UI controllers or screens.
- Introduce consistent design system tokens (spacing, radii, icon size).

Outcome:
UI becomes testable and changes are less likely to regress in other surfaces.

### 10) Build + test architecture
Problem:
Instrumentation tests exist but are not robust on multiple devices; unit tests are missing.

Required changes:
- Add JVM tests for domain mapping, dedupe, search index, sync logic.
- Use managed device matrix for CI coverage (API 29/33/35 + tablet + automotive).
- Add device-agnostic smoke tests for main flows.

Outcome:
New functionality can ship with confidence and fewer regressions.

## Implementation plan (phased)

### Phase 0 - Planning and guardrails
Deliverables
- Choose threading strategy (Java executors vs Kotlin coroutines) and document it.
- Decide on cleartext policy (global vs per-server opt-in).
- Decide on flavor applicationId strategy (side-by-side installs vs single ID).

Tasks
- Add a short ADR in docs describing chosen threading model and why.
- Define success metrics for performance (no UI-blocking DB access, stable queue restore).

### Phase 1 - Build and config hygiene
Targets
- app/build.gradle
- gradle.properties

Tasks
- Apply Kotlin Android + parcelize plugins explicitly; remove manual compiler/runtime wiring if plugins are used.
- Review gradle.properties flags and remove deprecated/unused settings.
- Confirm flavor applicationId strategy and align productFlavors with that decision.

Checks
- Clean build (Debug + Release).
- Run instrumentation smoke tests.

### Phase 2 - Safety and stability fixes (high risk)
Targets
- app/src/main/java/one/chandan/rubato/repository/QueueRepository.java
- app/src/main/java/one/chandan/rubato/database/AppDatabase.java
- app/src/main/AndroidManifest.xml
- app/src/main/res/xml/network_security_config.xml
- app/src/main/java/one/chandan/rubato/util/Preferences.kt

Tasks
- Guard nulls in queue restore (last played index/timestamp).
- Remove or restrict fallbackToDestructiveMigration for release builds.
- Move credential storage to EncryptedSharedPreferences (or Keystore-backed store).
- Restrict cleartext traffic (per-server opt-in or debug-only).

Checks
- Unit tests for queue restore edge cases (empty DB).
- Manual QA: login, playback, queue restore, offline mode.

### Phase 3 - Threading and repository refactor
Targets
- app/src/main/java/one/chandan/rubato/repository/*
- app/src/main/java/one/chandan/rubato/util/DownloadUtil.java

Tasks
- Replace ad-hoc Thread + join() with a shared executor or coroutines.
- Ensure all DB access off main thread; use LiveData/Flow where possible.
- Centralize executor(s) and add lifecycle-aware cancellation where needed.

Checks
- StrictMode enabled in debug to detect disk/network on main thread.
- Run UI smoke tests + macrobenchmarks (if relevant).

### Phase 4 - UI fragment decomposition
Targets
- app/src/main/java/one/chandan/rubato/ui/fragment/HomeTabMusicFragment.java
- app/src/main/java/one/chandan/rubato/ui/fragment/PlayerCoverFragment.java
- app/src/main/java/one/chandan/rubato/ui/fragment/PlayerBottomSheetFragment.java

Tasks
- Split large fragments into feature-specific controllers or smaller fragments.
- Move data fetching and state to ViewModels; keep fragments focused on rendering.
- Ensure all Handler callbacks are cleared on stop/destroy.

Checks
- Regression test main navigation and playback UI flows.
- Add new fragment-level tests for key interactions.

### Phase 5 - Test coverage expansion
Targets
- app/src/main/java/one/chandan/rubato/util/LibraryDedupeUtil.java
- app/src/main/java/one/chandan/rubato/util/SearchIndexBuilder.java
- app/src/main/java/one/chandan/rubato/util/MetadataSyncManager.java

Tasks
- Add JVM tests for dedupe, search index, metadata sync logic.
- Add failure mode tests (empty inputs, nulls, offline states).

Checks
- CI pipeline includes unit tests + existing instrumentation suite.

## Risks and mitigations
- Behavior regression in playback/queue: add feature flags and staged rollout.
- Data loss from migrations: remove destructive fallback in release.
- Security changes may break legacy servers: allow per-server opt-in with warning.
- Offline UX remains inconsistent and brittle if OfflinePolicy is not enforced.
- Jellyfin/local integrations continue to be incomplete and ad hoc without domain models.

## Recommended implementation order (staged)
1) Domain models + Source registry (unblocks everything else).
2) Aggregation Repository + OfflinePolicy.
3) Sync Orchestrator + ArtPrefetcher (WorkManager-based).
4) Unified search repository + index improvements.
5) QueueManager + MediaItem builder refactor.
6) AutoLibraryRepository + MediaLibraryService stabilization.
7) Telemetry + performance markers.
8) UI decomposition + design tokens.
9) Tests + CI matrix expansion.
10) Phases 1-5 from the implementation plan, executed in order to reduce risk.

## Decisions (2026-02-02)
- Domain model shape: wrap Subsonic models rather than replacing them (Subsonic is canonical).
- Dedupe policy: normalize title/artist/album/year (plus any available fields), and select the most detailed record if fields are missing.
- Sync frequency: at least once per app launch, plus background sync once per day.
- Source preference: prefer most detailed metadata, streaming from local, then Subsonic, then Jellyfin/other. If servers track plays, post play information.
- Cleartext policy: HTTP is acceptable for self-hosted servers, but prefer HTTPS when available.
- Flavor strategy: consolidate to a single applicationId/flavor unless a concrete distribution need reappears.
- Kotlin build: standardize plugin setup in Gradle (kotlin-android + kotlin-parcelize).

## Pending decisions
- None currently. Revisit if distribution targets change.
