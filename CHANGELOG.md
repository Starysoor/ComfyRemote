# Changelog

## 2.2.2 — 2026-11-03

### Added

- Setting to choose a workflow when reusing history/task parameters; off (default) returns straight to the original platform and workflow.

### Fixed

- Reusing a cloud (AutoDL) result no longer asks for a local ComfyUI server.
- Version `2.2.2` (versionCode 23).

## 2.2.1 — 2026-11-03

### Added

- Cloud workflow cards now show the unique AutoDL workflow ID under the name.
- Cloud workflow descriptions are shown in the list and in the Generate workflow card; the full AutoDL detail `content` is available in Workflow notes.
- Debug logging of cloud media metadata (`IrisCloud`) to diagnose missing reference images.

### Fixed

- Prevent submitting a cloud task while reference images/audio are still being prepared.
- Normalize every cloud reference image to JPEG, so HEIC/AVIF/GIF/null-MIME images cannot be sent with an invalid MIME type.
- Show a clear note that multi-image references do not guarantee an identical first frame; use a first/last-frame workflow for exact first-frame control.
- Version `2.2.1` (versionCode 22).

## 2.2.0 — 2026-11-03

### Added

- Iris design system: light/dark palettes, typography, surfaces, buttons, chips, inputs, progress and settings components.
- Edge-to-edge layout with theme-aware system bar contrast.
- Resource-based localization (`values` + `values-zh`, 452 keys) with plurals for count strings.
- Redesigned Create, Generate, Tasks, History, Settings, Cloud Settings, AI Settings, Update and Board screens.
- Verification screenshots under `docs/verification/redesign/`.

### Changed

- Screen code split into focused sections/components; removed the old inline `L10n` helper.
- `AppStrings` now resolves through application resources (no static Activity/Context).
- Version `2.2.0` (versionCode 21).

### Fixed

- Count strings now use Android plurals.
- Removed unused action strings and launcher color resource.
- Compose modifier parameter/extension warnings.
- Replaced platform `ExifInterface` with AndroidX, explicit locales for formatted sizes, and removed an obsolete SDK check.

## 2.1.0 — 2026-11-02

### Added

- AutoDL.Art hosted ComfyUI workflow support: 25 workflows, including H3 image/video, first/last frame, multi-image/multi-audio, lip-sync and IndexTTS2.
- Cloud price parsing (`billing_config`), per-run estimate, submit confirmation, local spend estimate and cloud cancel warnings.
- Audio reference inputs via file picker and `data:audio/...;base64,...`.
- Storyboard / multi-shot projects: shot list, shared style prompt, automatic last-frame extraction and next-shot reference chaining.
- AI vision rewrite: sends the actual image to a multimodal OpenAI-compatible model and rewrites the prompt.
- Encrypted secrets (AI key, AutoDL token, GitHub token) using Android Keystore AES/GCM with legacy plaintext migration.
- `CloudProvider` abstraction and generic cloud generation backend.
- Gradle wrapper.
- Verification screenshots under `docs/verification/`.

### Changed

- Rebranded to **Iris（灵瞳）**; launcher icon uses the supplied artwork, with adaptive and monochrome variants.
- Compact Create/Tasks layouts; recent result and setup counts configurable.
- Consolidated Settings: appearance, creation defaults, cloud submit, notifications, cloud services and cache.
- Task delete actions reduced to one confirmation point.
- Version `2.1.0` (versionCode 20).
- Repository renamed from `Starysoor/ComfyRemote` to `Starysoor/Iris`.

### Fixed

- Required image inputs now block generation when empty; optional inputs remain warnings.
- Cloud amount limits (min/max) are checked before submit.
- Video/audio results use media-aware history, thumbnails, playback and gallery export.
- Task/history reuse preserves the original server and actual seed.

## 2.0.0 — 2026-11-02

- Initial Iris rebrand, AutoDL cloud workflows, video results, AI vision rewrite, app settings and compact UI.
