# Changelog

## 2.3.0 — 2026-11-03

### Added

- Phase A foundation: semantic `ReferenceRole` / `ProjectAsset` models, persisted through storyboards, history and task requests.
- Reference-role defaults on local and cloud image fields, with provider rule metadata.
- Skill Runtime metadata on `AiSkillDef`: scopes, input sources, execution and output policies.
- Multi-image vision requests that send every reference with its role/order in one request.
- AI vision rewrite now treats references as hidden context, fuses all enabled references, and describes start→end transitions instead of captioning images.
- Multi-stage vision skill: hidden visual analysis → planner/rewrite, with the analysis never shown or quoted into the final prompt.
- Skill editor exposes description, scopes, input sources, execution and output policies.
- Tag profiles (AUTO / Danbooru / Illustrious / Pony / Custom) shape the AI tag skill's quality conventions.
- AI reasoning controls are provider/model-aware; inapplicable protocol fields are stripped from requests.
- Retired the built-in English-expand and translate skills; users can recreate them as custom skills.
- Independent "auto-save drafts" and "auto-restore drafts" settings.
- Project workspace core: project-owned assets with semantic roles, durable copies, and role labels.
- Shot reordering, duplication and insertion; project assets can become a shot's manual reference or be cleared.
- Storyboard project/shot ids persist on generation tasks; Task Detail links back to the board.
- Project settings dialog: rename, default workflow, aspect ratio, key=value default parameters, and a live shot status overview.
- Project aspect-ratio/default-value model fields; per-shot workflow and portable parameter memory for Regenerate.
- Shot multi-reference: select several project assets and fill consecutive image inputs in one Generate session.
- History results can be added to a storyboard project with a chosen semantic role.
- Official provider adapters: OpenAI Images (GPT Image 1, DALL·E 3), Google Gemini/Imagen image models, and Replicate image/video models.
- Replicate input parameters are discovered from each model's OpenAPI input schema.
- Custom OpenAI-compatible provider with editable base URL, token and model list; image parameters are inferred automatically.
- Cloud provider registry metadata and a Cloud services list with per-provider detail routing and credential settings.
- Create's Cloud tab aggregates workflows from every registered provider and labels cards with the provider name.
- Cloud confirmation, cancellation and spend tracking now treat every non-ComfyUI provider as hosted cloud work.
- Version `2.3.0` (versionCode 26).

## 2.2.4 — 2026-11-03

### Added

- App shell starts immediately; ComfyUI auto-recovery runs in the background with a short-timeout probe, and health checks update per server.
- VPN/Tailscale-aware discovery hint and CGNAT subnet handling.
- Workflow presets remember their original server and skip non-portable image/audio references on other servers.
- Storyboard shots lock batch size to 1 and can record manual vs automatic reference images.

### Fixed

- Cloud spend is recorded only after the provider returns a remote task id, with recovery deduplication.
- Local ComfyUI video/audio downloads stream to disk; partial result downloads are reported.
- Local QUEUED cancel only succeeds after ComfyUI confirms queue removal; RUNNING cancel warns that `/interrupt` is server-wide and surfaces failures instead of silently marking CANCELLED.
- Result viewer, gallery save and share use one media-type resolver (AVI/3GP/MKV included); failed writes clean up their MediaStore entry.
- API-format workflows expose INT/FLOAT/BOOLEAN/COMBO parameters and skip linked inputs.
- Deleting a server clears its saved last URL; deleting a running storyboard shot/project is blocked and orphan frame files are collected.
- External notification/share intents are consumed once per Activity creation, and sharing no longer requires a local ComfyUI session.
- AI API keys and GitHub tokens are masked; non-local HTTP AI endpoints show a clear warning.
- Recent-result reuse keeps the original server and source field metadata.
- Version `2.2.4` (versionCode 25).

## 2.2.3 — 2026-11-03

### Changed

- Every cloud submission now passes one confirmation gate; retries and bulk retries show run count, per-run estimate and total estimate.
- Cross-workflow parameter reuse maps values by field role/input instead of raw node IDs, reports unmatched values, and never guesses between positive/negative prompts.

### Fixed

- Running storyboard shots can no longer be reopened or resubmitted; the next shot waits for the running one, deleted shots rebase downstream references, and tail frames use the actual final frame.
- History keeps media referenced by storyboards and writes its index atomically; pruning waits for in-flight downloads.
- Cloud result downloads use the long-timeout client and stream directly to disk instead of buffering whole videos in memory.
- Image preprocessing now scales exactly to the configured limit and fails clearly when an image cannot be decoded.
- Clearing the cloud input cache keeps files referenced by queued tasks, drafts, history and presets.
- Bulk "Cancel queued" warns when cloud jobs were already submitted.
- Recent results are filtered by server/cloud before the display limit.
- Transient Keystore decrypt failures no longer delete saved API tokens.
- Version `2.2.3` (versionCode 24).

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
