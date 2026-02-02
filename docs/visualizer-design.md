# Music Visualizer Design (Rubato)

## Goals
- Add a visually rich, configurable music visualizer that overlays the now-playing cover art.
- Visualizer is an option when the user taps the cover image (alongside existing actions).
- Visualizer is single-sided: bars originate at the bottom of the cover and extend upward (no mirrored top/bottom by default).
- Once visualizer mode is active, show a configuration button at the top-left of the cover area, mirroring the existing info icon size/placement in the player UI.
- Provide multiple configuration options (style, density, color, smoothing, etc.) with sensible defaults.

## Non-Goals (for initial version)
- Full-screen visualizer page.
- GPU shader pipeline (may be a future enhancement).
- Recording or exporting audio/visualizations.

## UX & Interaction
### Entry
- Tap on the now-playing cover (existing behavior) ? show cover actions panel.
- Add a new action: **Visualizer** (icon: equalizer/graphic_eq).
- Selecting Visualizer switches the cover area into “Visualizer mode” with bars drawn over the album art.

### Visualizer Mode
- The album art remains visible in the background.
- Bars are drawn from the bottom of the album art upward (single-sided), semi-transparent to preserve readability.
- A **Visualizer Settings** button appears top-left (same size + padding as info icon) only when visualizer is active.
- Tapping the cover action panel again should allow switching back to Cover or Lyrics (if applicable) without leaving the player.

### Exit
- Toggle back to “Cover” or “Lyrics.”
- If playback stops, visualizer pauses and fades to low amplitude (no jitter).

## Configuration Options
A compact settings dialog (bottom sheet or modal) with live preview:
- **Style**: Bars (default), Line waveform, Dots (optional)
- **Bar count**: 24–96 (default: 48)
- **Bar width/spacing**: auto (default) or manual sliders
- **Amplitude scaling**: Low / Normal / High
- **Smoothing / decay**: 0.1–0.9 (default: 0.6)
- **Color**:
  - Accent (default)
  - Gradient (top?bottom)
  - Album-art dominant color (optional, if already computed)
- **Opacity**: 20–80% (default: 55%)
- **Peak caps**: On/Off
- **FPS limit**: 30 / 45 / 60 (default: 45)
- **Show on lock screen**: Off (for now; future)

All options persisted in `Preferences` (new keys under `visualizer_*`).

## Technical Architecture
### Data Source
- Use `android.media.audiofx.Visualizer` with the current player’s **audio session ID**.
- For Media3, `MediaController` / `MediaBrowser` implements `Player` and exposes `getAudioSessionId()`.
- Only enable when local playback is active; disable when casting (audio session not local).

### Visualizer Pipeline
- **VisualizerManager** (new):
  - Owns `Visualizer` instance.
  - Exposes a listener callback providing FFT or waveform data (byte arrays) at fixed intervals.
  - Handles lifecycle (create, enable, disable, release).

- **VisualizerView** (new custom view):
  - Draws bars/line/dots based on processed amplitudes.
  - Uses `Choreographer` or a `ValueAnimator` for frame scheduling.
  - Maintains a small ring buffer for smoothing/decay.

### Rendering
- Draw over the album art inside `PlayerCoverFragment` using a view overlay or a stacked layout:
  - `ImageView` (album art)
  - `VisualizerView` (match size)
- Bars originate at the bottom edge of the cover area.

### Modes
- Add a simple enum: `CoverMode` = `COVER`, `LYRICS`, `VISUALIZER`.
- Persist last selected mode (optional) but default to Cover on app restart.

## Placement in UI
- Modify `inner_fragment_player_cover.xml` to include `VisualizerView` on top of the album art, initially `GONE`.
- Add a “Visualizer” action button in the existing overlay group.
- Add a top-left settings icon (hidden unless mode == VISUALIZER).

## Performance Considerations
- Use `Visualizer.setCaptureSize()` with a small capture size (256/512) and reuse arrays.
- Cap frame rate to 45 fps by default.
- Use a precomputed float array for bars; avoid allocations in `onDraw`.
- Avoid expensive color calculations per frame (cache gradient shaders).

## Permissions
- Visualizer attached to the app’s own audio session should not require `RECORD_AUDIO`.
- If a device requires it, show a one-time prompt with explanation and allow user to disable visualizer.

## Settings Storage
- `Preferences` new keys:
  - `visualizer_enabled`
  - `visualizer_mode` (bars/line/dots)
  - `visualizer_bar_count`, `visualizer_opacity`, `visualizer_smoothing`, `visualizer_scale`
  - `visualizer_color_mode` (accent/gradient/album)
  - `visualizer_peak_caps`, `visualizer_fps`

## Telemetry & Logging
- If local telemetry is enabled, record:
  - Visualizer enabled/disabled
  - Selected mode + fps
  - Device support errors (Visualizer init failure)

## Implementation Plan (Phased)
1. **Baseline**
   - Create `VisualizerManager` and `VisualizerView`.
   - Add visualizer mode toggle on cover.
   - Draw basic bars from bottom.

2. **Config UI**
   - Settings sheet with core options (bar count, opacity, smoothing, color).
   - Persist and restore.

3. **Enhancements**
   - Gradient colors, peak caps, waveform option.
   - Polished transitions and fade-in/out.

## Acceptance Criteria
- Visualizer appears as an option on cover tap and overlays album art.
- Bars are single-sided from the bottom and animate smoothly.
- Config button appears only when visualizer is active.
- Settings persist between sessions.
- Visualizer disables automatically when casting or when session ID is unavailable.
- No noticeable UI jank on mid-tier devices (target < 2ms per frame for draw).

## Risks / Open Questions
- Some OEMs restrict Visualizer usage; may need fallback to simple animation.
- Audio session ID availability on all playback types (local vs cast).
- Ensure visualizer doesn’t conflict with battery optimizations or background playback.
