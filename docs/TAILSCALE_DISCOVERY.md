# Iris · ComfyUI / Tailscale 发现

## 连接方式

Iris 的“搜索 ComfyUI”包含三类彼此独立的来源：

1. LAN 和当前 VPN 网段的有限 /24 扫描（仅默认端口 8188）。
2. 以前保存过的 ComfyUI URL，包括跨网段的 Tailscale IP、MagicDNS、HTTPS 反向代理地址。
3. **可选的 Tailscale 设备列表**：连接页 > “启用 Tailnet 设备发现”。配置一个仅授予 `devices:core:read` 权限的 Tailscale API 访问令牌后，Iris 会调用 `GET https://api.tailscale.com/api/v2/tailnet/-/devices?fields=default`，取回设备的 Tailscale IPv4 和 MagicDNS 地址，探测可访问的 ComfyUI `/system_stats`。不需要预先在 Iris 保存这些节点。

使用第 3 种方法需要有权限创建 Tailscale 访问令牌的账户。请在 Tailscale 的 **Trust credentials** 中选择 **Devices > Core > Read**，不要授予写入或管理权限。令牌可能有有效期，失效后需要重新配置。参考 Tailscale 官方文档：
https://tailscale.com/docs/reference/trust-credentials

## 重要限制

- Tailscale API 返回的是设备目录；**“出现在列表”不等于“手机能访问该设备的 8188 端口”**。设备必须已连接 Tailnet，ACL/Grants 放行相应流量，并且 ComfyUI 监听可被 Tailnet 访问的地址（例如 `0.0.0.0:8188`）。Iris 只显示实际通过 ComfyUI 健康验证的设备。
- 无 Tailscale 令牌时，不访问 Tailscale 官方 API，也不能可靠枚举所有未保存的 Tailnet 节点；仍可直接填写 IP 或 MagicDNS，并在连接成功后自动保存。
- 不扫描整个 `100.64.0.0/10`；那会产生数百万探测，既无效也不安全。
- 最多读取 128 台设备，每台通过 Tailscale IPv4 / MagicDNS 发起有限数量的健康探测。对于非默认端口，使用手动服务器地址。
- API 令牌保存在 Iris 现有本地凭据库中，只用于固定域名 `api.tailscale.com` 的只读请求；不会作为 ComfyUI 的 Authorization Header。关闭 Tailnet 设备发现会清除保存的令牌。
- 读取设备列表出错不会禁止局域网、VPN 或已保存服务器扫描，应用启动也不会等待 Tailscale。
- Android 模拟器 / CI 测试只使用模拟的 JSON 和 HTTP 响应；是否能够连接实际设备还取决于用户的 Tailnet、DNS 和 ACL 配置。

## 验收范围

- 冷启动立即呈现主应用，不等待离线 ComfyUI。
- 启动自动恢复采用短超时的健康探测；手动连接/断开不会被过时自动连接结果覆盖。
- LAN /24 能发现同网段的 ComfyUI；保存过的跨网段 Tailnet 地址会被直接检查。
- 配置 read-only Tailscale 访问令牌后，可以发现以前未保存的 Tailnet 设备；仅对通过 ComfyUI 健康验证的地址展示可选项。
- 没有令牌、令牌被拒绝或 Tailscale API 不可用时，回退为 LAN/VPN/已保存扫描，并在 UI 显示范围/权限提示。
