# Verification screenshots

Captured on the `deck34` Android emulator against a real local ComfyUI server
and a real AutoDL.Art test workflow.

## v2.2 redesign

The `redesign/` folder contains the light and dark Iris design-system screens:

| File | Screen |
|---|---|
| `redesign/light-create.png` | Create — light |
| `redesign/light-generate.png` | Generate — light |
| `redesign/dark-create.png` | Create — dark |
| `redesign/dark-generate.png` | Generate — dark |
| `redesign/dark-board.png` | Board / storyboard — dark |
| `redesign/dark-settings.png` | Settings — dark |
| `redesign/dark-app-settings.png` | App settings — dark |
| `redesign/dark-cloud-settings.png` | Cloud settings — dark |

## Feature verification

| File | What it shows |
|---|---|
| `iris-connect.png` | Iris connect screen and cloud-only entry |
| `iris-local.png` | Local ComfyUI workspace |
| `iris-localgen.png` | Local workflow parameters |
| `iris-result2.png` | Real local generation result |
| `iris-tasks2.png` | Task center with completed/cancelled jobs |
| `iris-cloud.png` | AutoDL cloud workflow catalogue |
| `iris-cloudsettings.png` | Token, default video workflow and cache controls |
| `iris-cost-confirm.png` | Price estimate and submit confirmation |
| `iris-cloud-prompt.png` | Cloud video prompt + reference image |
| `iris-cloud-task-done.png` | Real AutoDL video task completed |
| `iris-real-video-player.png` | Real AutoDL MP4 playback |
| `iris-audio-workflow.png` | Audio/TTS workflow fields |
| `iris-cloud-spend.png` | Local estimate ledger |
| `iris-storyboard-create.png` | Storyboard creation |
| `iris-storyboard-done.png` | Completed shot + extracted last frame |
| `iris-storyboard-next-shot.png` | Next shot auto-filled with previous last frame |
| `iris-visionapplied.png` | AI vision rewrite applied to the prompt |
| `iris-appsettings.png` | App/interface settings |
| `iris-launcher.png` | Launcher icon |

## Storyboard workspace polish

Captured on `deck34` against a previously completed real storyboard project, so
these also verify the one-time legacy tail-frame migration.

| File | What it shows |
|---|---|
| `iris-board-detail-polished.png` | Optional tail-frame relay switch, tail frame added to project assets, result + tail frame on the shot card |
| `iris-board-chain-off.png` | Relay disabled — automatic references are cleared, manual/asset references remain |
| `iris-board-next-shot.png` | Next shot opens with the returned tail frame as its reference |
| `iris-board-results-tab.png` | The returned tail frame appears in the app Results library |

## Cloud workspace fixes

Captured on the `deck34` emulator with the real AutoDL.Art catalogue plus a
local OpenAI-compatible mock provider (`10.0.2.2:18080`) used to verify model
pulling and reference-image edits end to end.

| File | What it shows |
|---|---|
| `iris-cloud-two-sections.png` | Cloud models grouped under `AutoDL.Art` and `Custom OpenAI API` sections, with provider cards above |
| `iris-cloud-fix-services.png` | Per-provider enable switches; unused providers are marked Hidden |
| `iris-reference-input.png` | An edit model exposes the Reference images upload slot and provider-specific subtitle/notes |
| `iris-cloud-confirm.png` | Confirmation dialog names the real provider in the billing note |

The AutoDL test used the user-provided test token; it is not stored in the
repository. Actual billing is determined by the provider that runs the job.
