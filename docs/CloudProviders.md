# Cloud services · 云端服务配置指南

> Iris v2.3.0 · 2026-11-04 · [Release](https://github.com/Starysoor/Iris/releases) · [Changelog](../../CHANGELOG.md)

Iris supports local ComfyUI and multiple hosted generation APIs. To use a cloud provider, open **Settings → Cloud services**, select a provider, and configure its own credentials, endpoint and models. Cloud workflows are available in **Create → Cloud**. A local ComfyUI server is not required for cloud-only usage.

Iris 同时支持本地 ComfyUI 和多种云端生成接口。打开 **设置 → 云端服务**，选择厂商后填写该厂商独立的 Token/API Key、Endpoint 与模型，然后前往 **创作 → 云端**。只使用云端模型无需连接本地 ComfyUI。

## Supported integrations / 支持的接入方式

| Provider | Main use | Notes |
|---|---|---|
| AutoDL.Art hosted ComfyUI | Existing hosted ComfyUI workflows, images, video, audio | Uses ComfyUI workflow-group credentials; see [AutoDL guide](../AutoDL.md) |
| AutoDL Models | Hosted image/video model APIs | Separate from hosted ComfyUI workflow credentials |
| OpenAI Images | Image generation and editing | Uses OpenAI API key and supported image models |
| Google Gemini | Image generation | Uses Google Gemini API key and supported image models |
| Replicate | Image/video prediction models | Parameters are discovered from the model input schema where supported |
| SiliconFlow | Image/video model APIs | Available models and request formats may change |
| Custom OpenAI-compatible | User-provided compatible image endpoint | URL, token and models are user-controlled |
| Custom JSON template | User-defined JSON request + optional async status API | Configure JSON placeholders, response paths and optional health URL |

The list represents **integrations in the app**, not guaranteed access to every named model. Providers may change model IDs, pricing and supported API fields. Check your own account entitlement and provider's current API documentation before a paid request.

支持上述接入类型不意味着每个账号都能使用所有模型。提交付费任务前请以厂商实时文档、模型可用性和账户报价为准。

## Quick setup / 快速配置

1. Select the provider in **Cloud services / 云端服务**. Enter its **API Key/Token** and optional Base URL. Do not reuse the hosted ComfyUI group token for AutoDL Models.
2. Edit the model list or enable/disable models when your account requires it. Use **Test connection / 测试连接** to check the available health signal.
3. In **Create / 创作**, switch to **Cloud / 云端**, choose a model/workflow and fill in the prompt, media and other inputs.
4. Inspect any visible price estimate and the confirmation prompt. Only then submit. Keep the returned task ID for troubleshooting.

**Custom JSON template:** exact placeholders such as `{{steps}}` can preserve JSON numeric/boolean types when the field is typed. A placeholder embedded in a larger string stays a string. Async templates need a task-ID path and a status URL. Configure a safe GET-only `healthUrl` for a real network check; without one, valid local configuration is **not** proof the endpoint is reachable.

**自定义 JSON 模板：** 请分别配置提交 URL、请求 JSON、任务 ID 字段、状态查询 URL、状态字段和结果 URL 字段。若没有安全的健康检查地址，“配置有效”不等于“服务可用”，测试连接也不应发送付费生成请求。

## Recovery, billing and cancellation / 恢复、计费与取消

- Async tasks retain submission metadata and can be polled after app process recreation when their task identifiers were persisted. Replicate uses the Endpoint selected at submission time for that task's subsequent status requests.
- Synchronous image results and completed metadata are persisted to reduce loss between receiving provider output and creating the History record.
- Hosted output URLs can expire; Iris attempts to copy outputs into app-owned media storage after completion.
- **Local cancel is not a promise of remote cancellation.** Some providers continue charging after the app stops tracking a task.
- Estimates and spend history are **not official provider bills**. Use the provider dashboard as the source of truth.
- If storage fails just after remote acceptance and before the task index is saved, automatic recovery may still be unavailable. Record the remote task ID shown by an error and check the provider dashboard before submitting again.
- Credentials are normally encrypted via Android Keystore. If the Keystore is unavailable, a compatibility fallback may retain a credential locally without encryption. Do not enter secrets on an untrusted device.

云端任务成功提交不代表可以立即取消远端计费。若出现网络超时或恢复记录写盘失败，请先核对厂商后台是否已受理任务，**不要直接重复付费提交**。部分真实收费场景已经验收；未覆盖的接口行为按各厂商最新官方文档和账号权限判断。

## Official API references / 官方资料

- [OpenAI image generation](https://developers.openai.com/api/docs/guides/image-generation)
- [Google Gemini API](https://ai.google.dev/gemini-api/docs)
- [Replicate API](https://replicate.com/docs/reference/http)
- [SiliconFlow API](https://api-docs.siliconflow.cn/)
- [AutoDL.Art guide in this repository](../AutoDL.md)

## QA scope / 验收范围

The v2.3.0 release candidate passed unit tests, Android lint, Debug/Release builds and Android 34 emulator startup / force-stop / relaunch smoke checks. Local MockWebServer tests cover Replicate submission, Endpoint snapshot polling, post-restart recovery and result persistence **without spending money**. The emulator smoke checks do not simulate a fully paid multi-provider task.

- [CI run](https://github.com/Starysoor/Iris/actions/runs/41685790939)
- [Android emulator smoke](https://github.com/Starysoor/Iris/actions/runs/41685791109)
- [Release review](https://github.com/Starysoor/Iris/pull/39)
