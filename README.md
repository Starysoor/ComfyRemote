# ComfyRemote

安卓手机远程操控电脑 ComfyUI 的客户端：选择工作流 → 填提示词 → 出图，全程无需碰电脑画布。

## 功能

- **工作流列表**：自动拉取 ComfyUI 保存的全部工作流（UI 格式画布 JSON）
- **智能字段识别**：把画布工作流转换为 API 格式，自动识别出可编辑项——提示词、种子、参考图片、高级参数（步数/CFG/采样器/LoRA 强度等），工作流里的 MarkdownNote 使用说明也会展示
- **种子控制**：每次生成自动回填本次种子，关闭"随机"开关即可精确复现
- **生成过程**：前台服务保活（锁屏不中断）+ 实时节点进度 + latent 预览 + 完成通知，WebSocket 断线自动降级轮询 `/history`
- **本地历史**：生成图落盘保存，缩略图浏览、一键按原参数重新生成
- **图片查看**：全屏双指缩放、保存到相册、系统分享
- **图生图**：相册多选 / 拍照直传到 ComfyUI input
- **多服务器**：记住最近用过的服务器地址

## 连接方式

| 场景 | 地址 |
|---|---|
| 模拟器 | `http://10.0.2.2:8188` |
| 手机与电脑同一 Wi-Fi | `http://<电脑局域网 IP>:8188` |
| 任意网络（推荐） | 电脑与手机都装 [Tailscale](https://tailscale.com)，填电脑的 Tailscale IP，如 `http://100.x.x.x:8188` |

ComfyUI 需以 `--listen 0.0.0.0` 启动；防火墙入站规则建议只放行 Tailscale 网段（100.64.0.0/10）和局域网专用网络。

## 构建

```bash
# 需要 JDK 17+，Android SDK (compileSdk 34)
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 转换器单元测试（基于真实工作流 fixtures）
```

Release 签名需要 `keystore.properties` + 密钥文件（不入库，仅本地保留）。

## 刷新测试 fixtures

在运行中的 ComfyUI 环境执行：

```bash
node tools/fetch_fixtures.mjs
```

## 技术要点

- UI 格式 → API 格式转换器：处理"widget 转连线"的占位值、新版 `"COMBO"` 类型、`control_after_generate` 种子控件、Reroute/旁路穿透、动态 AUTOGROW 输入
- Kotlin + Jetpack Compose (Material 3) + OkHttp + 协程，零第三方图片库
