# Changelog

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
