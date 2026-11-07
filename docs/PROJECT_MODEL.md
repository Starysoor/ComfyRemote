# Iris · 项目数据模型（Iris Studio P0）

Iris v2.4 起，创作工作台的持久化中心从「分镜」（`storyboards.json`）升级为「项目」（`projects.json`）。本文描述新模型的实体、ID 语义、存储与迁移/回滚/垃圾回收规则，以及 Room 的评估结论。对应 Epic Issue #57 的 P0 阶段。

## 实体与 ID

全部实体 ID 均为 UUID v4（`UUID.randomUUID().toString()`），生成后永不复用。每个 ID 只标识一个实体类型：

| 实体 | 模型 | 说明 |
|---|---|---|
| 项目 | `VideoProject`（`data/project/ProjectModels.kt`） | 一个自包含的视频创作工作 |
| 镜头 | `ProjectShot` | 项目内有序创作单元；live 字段（status/taskId/resultPath/lastFramePath）镜像最近一次生成 |
| 版本 | `ShotTake` | 一次生成尝试的完整快照（模型/参数/参考/结果/费用），P0 每次生成自动记录一条，A/B 多版本 UI 在 P1 |
| 素材 | `ProjectAsset`（`data/reference/ReferenceModels.kt`） | 项目级可复用媒体；P0 新增可选 `kind`（IMAGE/VIDEO/AUDIO/SUBTITLE）与 `tags` |
| 生成任务 | `GenerationTask.taskId` | 复用现有任务引擎 ID，通过 `shot.taskId` 与 `GenerationRequest.storyboardProjectId/storyboardShotId` 弱关联（纯字符串，无外键） |

**为 P2 时间线预留的 ID 方案**：`Timeline` / `TimelineClip` / `Track` / `ProjectTask` / `ExportJob` 届时同样使用 UUID，且 clip 前缀将保留 `tlc:` 命名空间用于调试。P0 不实现时间线数据结构。

## 身份规则（必须遵守）

- **时间线片段 ≠ 镜头 ≠ 生成任务 ≠ 文件**。未来一个 Take 的结果文件可被多个时间线片段引用；删除镜头绝不能误删仍被引用的媒体文件。
- Take 是历史记录：重复生成同一镜头会产生多条 Take，`selectedTakeId` 指向产生当前 live 结果的那条；复制镜头会重置生成历史（不复制 Take）。
- 素材文件为 app-owned（`filesDir/storyboards/` 目录沿用旧媒体根目录，存量文件不迁移不失效），索引只存路径与元数据。

## 存储

- 单文件 `filesDir/projects.json`，信封带版本号：`{"version":2,"projects":[...]}`。字段读取全部容错（opt* + 默认值），未知字段忽略，缺 ID 的行丢弃。
- codec（`ProjectCodec`）与 store（`ProjectStore`）分离，codec 为纯函数，可在 JVM 测试。
- 写入走与 legacy `StoryboardStore` 完全一致的原子写配方：`.tmp` 文件 + `fd.sync()` + `Files.move(ATOMIC_MOVE)`（不支持时降级），**写盘成功后才发布内存 StateFlow**。
- 写前损坏防护：`rawProjectCount()` 统计原始行数，与 `decode()` 成功行数不一致（或文件不是合法 JSON 信封）时**拒绝写入**，防止用可读部分覆盖掉可恢复数据；错误通过 `persistenceError: StateFlow<String?>` 暴露给 UI。

## 迁移与回滚（storyboards.json → projects.json）

`ProjectMigration.migrateIfNeeded(filesDir)`（`data/project/ProjectMigration.kt`）在项目 UI 上线时（P0-2）由 `AppContainer` 调用，语义：

1. `projects.json` 已存在且可解析 → no-op（迁移已完成）。
2. `projects.json` 损坏且 legacy 锚点存在 → 从 legacy 重新迁移覆盖（备份先做）。
3. 首次迁移：先复制 `storyboards.json` → `storyboards.backup-<epoch>.json`（保留最新 2 份），再解码旧数据 → 逐字段映射（**UUID、时间戳、路径全部原样保留**；每个有过生成的镜头生成一条确定性 Take，ID 为 `<shotId>-take-1`）→ 编码后回读自检（项目数/镜头数/素材数一致）→ 原子写 `projects.json`。
4. **提交点是 projects.json 的原子写**。提交前任何一步失败或崩溃：旧文件字节不变、不产生半成品、下次启动重跑即可（备份覆盖复制，幂等）。
5. `storyboards.json` 迁移后**冻结不更新**，永久保留作为回滚锚点。回滚 = 换回旧版 APK，旧版从冻结快照继续（注意：迁移之后新建的数据不存在于快照中，见下）。

**降级注意**：从 v2.4+ 回退到 v2.3.x 后，旧版仍可读冻结快照；但迁移后新增/修改的项目只存在于 `projects.json`，旧版不可见。旧版执行删除类操作时其 GC 只保护快照中的引用（10 分钟宽限后可能清理迁移后新建的未引用媒体）。

## 垃圾回收语义

- 触发点：删除项目 / 删除镜头 / 删除素材（成功落盘后）。
- 保护集合 `referencedMediaPaths()`：所有 shot 的 `resultPath`/`lastFramePath`/`referencePath`、全部 Take 的 `resultPath`、全部素材 `path`。
- 删除规则：**先提交索引、再删文件**；仅清理「未被引用 **且** `lastModified` 早于 10 分钟宽限期」的文件（宽限防止清理仍在生成流程中的文件）。
- 跨 Store 保护：`AppContainer` 将 legacy store 与 project store 的引用集**并集**注入 `HistoryStore` 的 protectedMedia 钩子，结果页 History 的媒体清理永不破坏任一侧仍在引用的文件。

## Room 评估（P0 结论）

**结论：P0/P1 维持 JSON 方案，P2 时间线落地时重评。**

现状配方已覆盖的关键能力：事务性（原子写 + 失败不发布内存态）、损坏防护（写前行数校验 + 拒绝覆盖）、幂等恢复（迁移提交点 + 容错读）、JVM 可测性（纯函数 codec + File 注入）。当前数据规模（索引 < 数百项目）单文件读写性能足够，且全部 56 个测试类无需 Android 设备即可运行，回归成本低。

引入 Room 的代价：新增依赖与 schema 版本管理；与现有 8+ 个 Store 并存形成双存储期；事务语义需要重新验证（尤其「先写索引再删文件」与进程死亡恢复）；测试需要 Robolectric/仪器环境。而 P0/P1 的查询模式（按项目取全部镜头/素材）并没有关系查询压力。

**P2 重评触发条件**：时间线引入多轨片段与同一媒体多引用后，出现「按引用关系反向查询/批量级联更新」需求，或单项目 shots+takes+clips 规模让 `projects.json` 写放大明显时，将项目索引与关系迁移至 Room（媒体文件仍为 app-owned 文件，库中只存路径/校验/元数据）。

## 相关代码

- 模型/codec/store/迁移：`app/src/main/java/com/comfyremote/app/data/project/`
- legacy 分镜（迁移源，冻结后保留）：`app/src/main/java/com/comfyremote/app/data/storyboard/`
- 回归测试：`app/src/test/java/com/comfyremote/app/data/project/`
- AppContainer 接线（protectedMedia 并集）：`app/src/main/java/com/comfyremote/app/AppContainer.kt`
