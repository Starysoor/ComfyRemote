# Iris · 灵瞳

[中文](#中文) | [English](#english)

[![Release](https://img.shields.io/github/v/release/Starysoor/Iris?display_name=tag)](https://github.com/Starysoor/Iris/releases)
[![CI](https://github.com/Starysoor/Iris/actions/workflows/test.yml/badge.svg)](https://github.com/Starysoor/Iris/actions/workflows/test.yml)

One lens, every model. Android client for local ComfyUI **and** hosted cloud workflows — pick a model, write a prompt, generate images, video or audio.

**v2.2.4** · [AutoDL cloud guide](docs/AutoDL.md) · [Screenshots](docs/verification)

<a href="docs/verification/redesign/light-create.png"><img src="docs/verification/redesign/light-create.png" width="200"/></a>
<a href="docs/verification/redesign/light-generate.png"><img src="docs/verification/redesign/light-generate.png" width="200"/></a>
<a href="docs/verification/redesign/dark-board.png"><img src="docs/verification/redesign/dark-board.png" width="200"/></a>
<a href="docs/verification/redesign/dark-cloud-settings.png"><img src="docs/verification/redesign/dark-cloud-settings.png" width="200"/></a>

## English

**Iris** turns your phone into a remote for generation models running on your PC or in the cloud:

- **Local + cloud workflows** — browse saved ComfyUI workflows from a local server, or AutoDL.Art hosted workflows (MiniMax H3 video, Wan2.2 motion transfer and more) from one Create screen
- **Workflow conversion** — converts ComfyUI canvas JSON to API format automatically and discovers prompts, seeds, reference images and advanced parameters
- **Cloud video + audio** — image-to-video, first/last-frame, multi-image/multi-audio, lip-sync and TTS workflows; results are downloaded immediately because hosted URLs expire
- **Cost guardrails** — platform price, per-run estimate and confirmation before a paid submit; local spend estimate and cancel warnings
- **Storyboard** — chain multiple cloud video shots; the previous shot's last frame automatically becomes the next shot's reference
- **Vision-aware AI** — the built-in “AI vision rewrite” actually sends your image to a multimodal model, then rewrites the prompt around what it sees
- **AI prompt skills** — expand / tag / translate / negative / vision skills via any OpenAI-compatible endpoint (DeepSeek, Zhipu, OpenAI, Qwen, Moonshot, Ollama); custom skills and per-workflow bindings
- **Live progress** — foreground service, node-level progress, latent preview, cancel, task recovery after process death, local batch queue
- **History** — full prompts + seeds, image/video/audio thumbnails and playback, one-tap reuse, A/B comparison for images, image → video handoff
- **Multi-server** — saved servers, LAN discovery, health/queue probing, least-busy switching
- **Configurable UI** — compact layout, recent-result count, theme, default batch/seed/queue, upload size and more in Settings
- **Iris design system** — purpose-built light/dark palettes, typography, surfaces, chips, inputs and edge-to-edge navigation
- **Encrypted secrets** — AI keys, AutoDL token and GitHub token are stored with an Android Keystore-backed AES/GCM key

## 中文

**Iris（灵瞳）**把手机变成生成模型的遥控器：本地 ComfyUI 与云端工作流，一个入口完成。

### 核心能力

- **本地 + 云端**：创作页一键切换本地 ComfyUI 与 AutoDL.Art 托管工作流（MiniMax H3 视频、Wan2.2 动作迁移等）
- **云端视频 + 音频**：图生视频、首尾帧、多图参考、多图多音频、对口型与 TTS；云端结果是临时链接，成功后立即下载到手机
- **费用护栏**：提交前显示平台计价、本次预估并二次确认；本地记录预估消耗；取消云端任务会提示平台侧可能继续计费
- **分镜**：把多个云端视频镜头串成项目，上一镜的尾帧自动成为下一镜参考图
- **真正看图改写的 AI**：内置「AI 看图改写」会把参考图作为图片输入发给多模态模型，再结合你的修改要求重写提示词；不是纯文本猜测
- **AI 提示词技能**：扩写、标签、翻译、负面、看图改写等，兼容任意 OpenAI 风格接口（DeepSeek、智谱、OpenAI、Qwen、Moonshot、Ollama），支持自定义技能与按工作流绑定
- **实时反馈**：前台服务保活、节点进度、latent 预览、取消、进程重启恢复、本地批量队列
- **结果库**：完整提示词 + 实际 seed、图片/视频/音频缩略图与播放、一键复用、图片 A/B 对比、图片一键转云端视频
- **多服务器**：保存地址、局域网发现、健康/队列探测、最空闲切换
- **可定制界面**：紧凑布局、最近结果数量、主题、默认批量/种子/队列、上传尺寸等，全部集中在设置页
- **Iris 设计系统**：自建浅色/深色配色、字体、卡片、Chip、输入框和 edge-to-edge 导航
- **密钥加密**：AI Key、AutoDL Token、GitHub Token 使用 Android Keystore 的 AES/GCM 加密存储

### 连接方式

| 场景 | 地址 |
|---|---|
| 模拟器 | `http://10.0.2.2:8188` |
| 手机与电脑同一 Wi-Fi | `http://<电脑局域网IP>:8188` |
| 任意网络 | 电脑与手机都装 [Tailscale](https://tailscale.com)，填电脑的 Tailscale IP |
| 云端（无需本地显卡） | 设置 → 云端服务，填 AutoDL.Art 的 ComfyUI 分组 Token |

ComfyUI 需以 `--listen 0.0.0.0` 启动。连接页可直接选择“仅使用云端模型”。

### 云端计费提醒

AutoDL.Art 的托管工作流按次/按秒计费。应用会在提交前显示平台价与本次预估并请求确认，设置中可关闭确认（不建议）。云端任务目前没有公开的远程取消接口，取消只会停止手机端跟踪，平台侧可能继续执行并计费；本地“预估消耗”只是记录，不是账单。

## Build

```bash
# Requires JDK 17+ and an Android SDK (compileSdk 34) + Gradle 8.7 wrapper
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # converter + generation + cloud/storyboard tests
./gradlew :app:lintDebug              # Android lint
```

On Windows PowerShell use `.\gradlew.bat`. If your Windows user profile path contains non-ASCII characters, set an ASCII `GRADLE_USER_HOME` so Gradle test workers can launch.

Release builds are produced by GitHub Actions on `v*` tags (signing key provided via repo secrets).
`tools/ai_mock.mjs` runs a local mock OpenAI server for keyless testing of the AI expansion flow.
`tools/fetch_fixtures.mjs` refreshes the real ComfyUI workflow fixtures.

> The technical package name (`com.comfyremote.app`) is intentionally retained after the Iris rebrand so existing installs can still receive in-app updates. Change `applicationId` only when you are ready for a clean install.

### Documentation

- [AutoDL cloud workflows, parameters and billing](docs/AutoDL.md)
- [Verification screenshots](docs/verification)
- [v2.2 redesign screenshots](docs/verification/redesign)
- [Changelog](CHANGELOG.md)

The repository was renamed from `Starysoor/ComfyRemote` to `Starysoor/Iris`; GitHub redirects old URLs, and the in-app updater points at the new repository.

## License

[MIT](LICENSE)
