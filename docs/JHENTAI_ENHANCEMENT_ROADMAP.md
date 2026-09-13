# LANraragi Reader 增强开发路线图

> 调研基线：JHenTai `8.0.16+333` 与 LANraragi Reader `main@e0dbbab`
>
> 调研日期：2026-09-07
>
> 服务端契约：LANraragi `0.9.81/tools/openapi.yaml`

## 1. 文档目的与结论

本文基于 `D:\program\JHenTai-8.0.16\JHenTai-8.0.16` 的真实 Flutter/Dart 源码，为 `D:\program\lanraragi-reader-merged` 制定后续增强路径。目标不是复制 JHenTai 的界面或 E-Hentai 业务，而是吸收其中已经被复杂阅读、下载和本地图库场景验证过的工程思路，再按 LANraragi API、Android SAF、Jetpack Compose 和当前项目状态重新实现。

核心结论：

1. **先拆状态边界，再继续加功能。** 当前 `DetailScreen.kt`、`LibraryScreen.kt`、`SettingsScreen.kt` 和 `ReaderScreen.kt` 分别约 3234、2808、1932、1304 行。阅读、图库和详情的新功能已开始共享状态，但主要逻辑仍集中在大文件中，继续直接叠加会放大手势冲突、状态重复和回归风险。
2. **下一条主线应是“统一资源目录 + 本地优先”。** 服务器档案、离线原档和 SAF 本地档案需要共享统一身份、封面、页源、阅读进度和本地可用性描述，同时严格禁止本地档案隐式进入上传路径。
3. **下载队列必须从“持久化显示状态”升级为“可恢复任务”。** 当前任务列表能写入 JSON，但任务执行体是内存闭包，进程重启后不能从持久化记录重建工作。这是下载可靠性的首要缺口。
4. **本地扫描已有变更签名，但仍会递归遍历全树生成签名。** 它减少了压缩包解析，却没有消除大型图库每次检查时的 O(N) SAF 元数据遍历；后续应改为分层指纹和增量索引。
5. **缩略图优化要围绕 LANraragi 的 202 异步生成协议。** 只重复请求 `/thumbnail` 可能持续得到占位图。客户端需要识别 `no_fallback=true` 返回的任务，跟踪 Minion 完成后再稳定刷新缓存键。
6. **JHenTai 值得借鉴的是职责分离和生命周期控制。** 它把阅读布局、图片解析、预加载、进度写入、下载状态、数据库迁移分开；其站点配额、图片解锁流程和 ArchiveBot 不适用于本项目。
7. **E-Hentai 收藏槽位、tag namespace 和标签知识库应提升为 P1 主线。** App 计划从 E-Hentai、nHentai 刮削元数据时，namespace 已不只是展示颜色，而是候选匹配、标签去重、中文翻译、补全排序、冲突合并和回写审计的共同主键。

建议以七个增量版本推进：基础数据层、阅读器可靠性、资源与本地图库、元数据与标签智能、持久下载、图库与历史、安全与设备体验。不要把所有增强合并成一次大改。

## 2. 调研范围与证据

### 2.1 JHenTai 源码证据

本次检查了 422 个 Dart 文件，并重点阅读以下实现：

| 能力 | 源码位置 | 可借鉴点 |
| --- | --- | --- |
| 服务初始化 | `lib/src/main.dart:59`、`:118`，`lib/src/service/jh_service.dart` | 显式依赖、有序初始化、初始化错误隔离 |
| 阅读设置 | `lib/src/setting/read_setting.dart:13` | 阅读方向、方向独立配置、自动模式、在线/本地预加载距离 |
| 阅读协调 | `lib/src/pages/read/read_page_logic.dart:55` | 页面解析、系统 UI、缩略图同步、进度定时落盘的统一协调者 |
| 布局策略 | `lib/src/pages/read/layout/*` | 单页、连续、双页布局各自拥有导航和可见页判定 |
| 页面缓存 | `lib/src/widget/cached_page_view.dart:7` | 相邻页保活和隐式预加载 |
| 下载服务 | `lib/src/service/gallery_download/gallery_download_service.dart:69` | 任务优先级、并发执行、可恢复状态、页面级进度 |
| 下载设置 | `lib/src/setting/download_setting.dart:28`、`:36` | 并发上限和启动时恢复策略分开配置 |
| 本地图库 | `lib/src/service/local_gallery_service.dart:22` | 图片目录识别、自然排序、下载目录与外部图库隔离 |
| 阅读历史 | `lib/src/service/history_service.dart:12`、`read_progress_service.dart` | 历史与进度分开存储，更新可独立通知 |
| E-H 收藏分类 | `lib/src/setting/favorite_setting.dart:18` | 十个固定槽位以 index 保持身份，名称和数量可同步更新 |
| namespace 模型 | `lib/src/enum/eh_namespace.dart`、`model/gallery_tag.dart` | namespace 别名、中文名称、标签状态与投票状态分层 |
| 标签翻译 | `lib/src/service/tag_translation_service.dart:41` | EhTagTranslation 数据入库、原文/译文联合查询、查询词定位 |
| 补全排序 | `lib/src/service/tag_search_order_service.dart:29` | 标签词频表、namespace 权重和文本匹配评分组合 |
| 数据库 | `lib/src/database/database.dart:69` | Drift schema v25、逐版本迁移、索引化查询 |
| 安全与性能 | `lib/src/setting/security_setting.dart`、`performance_setting.dart` | 生物认证、截屏控制、图片缓存和帧率设置 |
| 自适应布局 | `lib/src/pages/home_page.dart`、`lib/src/pages/layout/*` | 手机、平板、桌面入口分流与主从视图 |

服务端还确认了 `lib/LANraragi/Plugin/Metadata/EHentai.pm` 和 `nHentai.pm` 的现有刮削能力：E-H plugin 支持 source URL、gid/token、标题和封面 hash 匹配；nHentai plugin 支持 source URL、数字 ID、文件名 ID 和标题候选。二者都返回新标签/标题，由调用方决定如何应用。

JHenTai 目录不包含 `.git`，也没有完整 8.0.0 至 8.0.16 的 changelog。`pubspec.yaml` 标记为 `8.0.16+333`，而 `CLAUDE.md` 仍写 `8.0.14+328`。因此本文只根据 8.0.16 源码快照得出结论，不虚构版本演进历史。

JHenTai 仅有两个可见单元测试：点击区域配置和下载分组偏好。它的功能设计可作参考，但测试覆盖率不能作为本项目标准。

### 2.2 LANraragi Reader 当前基线

当前 `main` 已包含此前要求的多数功能：

- 阅读器已有横向、纵向分页、连续模式、双页、上下/左右方向、缩放、自动翻页、五页缩略图时间线、目录和设置抽屉。
- 阅读器进入时不主动展开底栏；左右翻页与缩略图滑动行为已分开。
- 本地档案先进入详情页；阅读时优先使用离线原档；本地档案不会进入自动下载或服务器操作。
- 目录编辑调用服务器 ToC 接口，阅读器新增目录默认当前页。
- 下载任务已统一到 `DownloadManager`，具备并发上限、暂停、恢复、重试、前台服务和活动任务角标。
- 离线缓存保存服务器原档、封面和元数据，并按访问时间执行 LRU 淘汰。
- SAF 本地扫描已有持久化索引和根签名；归档读取已有源文件缓存、列表 LRU、互斥锁和 Coil Fetcher。
- 最近阅读最多保留三个本地或云端会话，并能恢复页码。
- 分类管理、筛选抽屉、预设抽屉、连接页服务器卡片、自定义缓存上限等已经存在。
- 已有 `TagRules` 的 namespace 分组/颜色、`TagTranslationRepository` 的中文词库下载、服务端标签统计补全，以及元数据插件列表和 Minion 任务入口。

主要结构性缺口：

| 缺口 | 当前表现 | 后果 |
| --- | --- | --- |
| UI 文件过大 | 四个核心 Screen 合计超过 9000 行 | 修改冲突高，状态归属不清，难做局部测试 |
| 无结构化本地数据库 | 历史、任务、本地索引、离线索引分别写 JSON | 缺少事务、索引、迁移、并发一致性和损坏恢复 |
| 下载执行体不可恢复 | `DownloadManager.works` 保存 suspend 闭包 | 重启后的 WAITING 任务没有可执行工作描述 |
| 历史与“最近三本”耦合 | `HistoryRepository` 落盘前直接裁剪到 3 条 | 历史页无法保留完整历史，统计信息永久丢失 |
| 本地变更检查仍是全树扫描 | `buildRootSignature()` 递归每个节点 | SAF 大目录刷新仍慢，文件提供器调用量高 |
| 封面异步生成未闭环 | 普通 thumbnail URL 直接交给 Coil | 占位图可能被稳定缓存，真实 thumb 到达后不刷新 |
| 插件刮削未闭环 | 异步插件完成后直接重读档案元数据 | plugin API 不修改数据库；未读取 job detail result，也没有预览和确认回写 |
| 标签知识分散 | `TagRules`、翻译 JSON、服务器 TagStat 各自维护 | namespace 别名、中文检索、词频与补全排序不能形成一致结果 |
| 缺少刮削溯源与冲突模型 | 只有直接编辑 title/tags/summary | 无法说明标签来自何处，也无法防止覆盖用户手工元数据 |
| 阅读职责集中 | 页面来源、进度、系统 UI、手势和控制条在同一文件 | 修复一个阅读行为容易影响其他模式 |
| 自动化测试薄弱 | 当前未发现 `app/src/test` 或 `app/src/androidTest` 测试 | 合并后主要依赖人工验证，回归成本持续上升 |

## 3. 目标架构

目标不是引入重量级“洁净架构”模板，而是为真实共享状态建立最少但清晰的边界：

```text
Compose Route / Screen
        |
        v
Feature ViewModel ---- Reader/Library/Download UI state
        |
        v
Use case / Coordinator ---- lifecycle, debounce, policy, capability checks
        |
        +---- CatalogRepository ----- LANraragi API
        +---- PageSource ------------ remote / offline archive / SAF archive / folder
        +---- DownloadCoordinator --- durable queue + foreground worker
        +---- ReadingRepository ----- history + recent sessions + pending progress
        +---- AssetRepository ------- cover/page cache and revision keys
        +---- MetadataRepository ---- scraper providers + preview + write-back
        +---- TagKnowledgeRepository  namespace + translation + ranking
        |
        v
Room database + DataStore preferences + owned files + SAF URIs
```

状态归属规则：

- **Room**：需要查询、事务、恢复和迁移的数据，例如下载任务、阅读历史、本地索引、离线资源目录。
- **DataStore**：用户偏好，例如阅读方向、自动翻页秒数、并发数、缓存上限。
- **文件系统**：归档、页缓存、封面派生文件；数据库只保存身份、状态、路径、尺寸和修订值。
- **ViewModel StateFlow**：当前页面的短生命周期 UI 状态。
- **Compose remember**：纯视觉瞬时状态，例如抽屉开关、输入框草稿；不能作为业务事实来源。

建议新增顶层包：

```text
data/db/                 Room database, entity, dao, migration
data/catalog/            remote/local/offline resource catalog
data/download/           durable task specs, coordinator, runner registry
data/reader/             PageSource implementations, progress writer, prefetcher
data/assets/             cover/page model, thumbnail resolver, cache policy
data/metadata/           scraper providers, matcher, patch merger, provenance
data/tags/               namespace registry, translation dictionary, FTS ranking
domain/model/            ArchiveIdentity, SourceKind, LocalAvailability
ui/reader/               route, surface, controls, sheets, gestures
ui/library/              route, list/grid, filters, selection
ui/download/             route, queue, saved resources
```

## 4. 功能区块 A：数据层与状态治理

### 构想

建立一个小型 Room 数据库，先承接最容易损坏或最需要查询的 JSON 状态，不一次性镜像全部 LANraragi 元数据。以稳定身份模型统一服务器、本地和离线资源。

### 具体实现路径

1. 在 `gradle/libs.versions.toml` 和 `app/build.gradle.kts` 增加 Room、KSP 和测试依赖。
2. 新增 `data/db/ReaderDatabase.kt`，初始 schema 只包含：
   - `reading_history`：完整历史，主键为 source key，含最后页、总页数、首次/最后阅读时间。
   - `recent_session`：最多三条的显示投影，或直接由 history 查询 `ORDER BY lastReadAt DESC LIMIT 3`。
   - `download_task`：类型、payload、状态、已完成字节、总字节、重试次数、优先级、错误和时间戳。
   - `local_archive`：SAF URI、稳定 ID、指纹、标题、页数、封面 entry、上次验证时间。
   - `saved_artifact`：服务器 arcid、本地原档路径/URI、资源修订、字节数、最后访问时间。
   - `metadata_scrape_job`：档案、Provider、输入、状态、候选、错误和限流时间。
   - `metadata_provenance`：字段/标签、来源站点、来源 ID/URL、置信度、抓取时间和数据版本。
   - `local_metadata`：SAF 本地档案的标题、标签、summary、刮削来源和用户覆盖，不触发服务器上传。
   - `tag_dictionary`、`tag_frequency` 和 FTS 表：namespace、canonical key、中文名、说明、外链与词频。
   - `eh_favorite_slot`、`eh_favorite_mapping`：E-H 固定槽位与 LANraragi 分类的可选映射。
3. 新增 `domain/model/ArchiveIdentity.kt`：
   - `Remote(arcid)`
   - `Local(uriHash, uri)`
   - `Tank(tankId)`
   不再靠 `arcid.startsWith("local_")` 在各处推断能力。
4. 新增 `ArchiveCapabilities`，明确 `canUpload`、`canEditMetadata`、`canEditToc`、`canDeleteServerCopy`、`canCache`。本地来源的 `canUpload` 默认且永久为 false，只有显式“上传副本”用例临时开放。
5. 编写一次性迁移器，把 `history.json`、`download_manager/tasks.json`、`local/index.json`、`offline/index.json` 导入 Room。迁移成功后写版本标记；至少保留旧文件一个发布周期，不在首个迁移版本直接删除。
6. `AppContainer.kt` 由主 agent 统一接线，保持构造顺序显式，不照搬 GetX 生命周期容器。

### 优先级、依赖与风险

- 优先级：**P0**。
- 依赖：无；它是后续下载、历史和本地索引工作的前置条件。
- 风险：迁移过程中 JSON 损坏、重复导入、Room schema 导出遗漏。
- 控制：幂等 migration marker、导入事务、损坏文件隔离为 `.invalid`、Room schema 提交版本库。

### 验收标准

- 从当前版本升级后，最近阅读、离线档案、本地扫描结果和下载记录均不丢失。
- 强杀进程或写入并发发生时，不出现半个 JSON 导致全部数据无法加载。
- 同一档案的服务器副本和本地副本身份可区分，也能通过 `saved_artifact` 建立关联。
- 本地档案在任何批量服务器操作中都由 capability 层排除，而不是仅靠 UI 过滤。

## 5. 功能区块 B：阅读器内核与交互

### 构想

吸收 JHenTai “阅读协调器 + 独立布局逻辑”的思路，将 Compose 阅读器拆为统一会话状态和多种渲染策略。所有模式共享页源、进度、预加载和控制层，但各布局独立计算可见页和翻页动作。

### 具体实现路径

1. 从 `ReaderScreen.kt` 提取：
   - `ui/reader/ReaderRoute.kt`：导航参数、沉浸式窗口进入/退出。
   - `ui/reader/ReaderViewModel.kt`：唯一 `ReaderUiState` 和用户动作。
   - `ui/reader/ReaderSurface.kt`：按模式选择连续、横向分页、纵向分页、双页。
   - `ui/reader/ReaderControls.kt`：底栏、五页缩略图、页滑条。
   - `ui/reader/ReaderSheets.kt`：目录、自动翻页、阅读设置。
   - `ui/reader/ZoomablePage.kt`：缩放、平移、双击和父级 pager 手势仲裁。
2. 新增 `data/reader/PageSource.kt`：
   - `RemotePageSource`
   - `SavedArchivePageSource`
   - `SafArchivePageSource`
   - `SafFolderPageSource`
   每个实现暴露 `pageCount`、`pageModel(index)`、`thumbnailModel(index)`、`revision` 和错误恢复方法。
3. 新增 `ReaderSessionCoordinator`，按“已保存原档 > SAF 本地资源 > 服务器页”选择来源。来源切换不能改变档案身份和进度键。
4. 每种布局实现统一接口：`currentAnchorPage`、`jumpTo(page)`、`step(forward)`、`visiblePages`。双页模式明确封面单独显示规则和奇偶配对，不把屏号当页号存储。
5. 缩放手势规则：
   - `scale > 1 + epsilon` 或双指手势开始后，页面拥有手势，暂停 pager/列表翻页。
   - 以触点中心缩放，按实际缩放后图片边界约束 offset。
   - 归一倍率后将未消耗的单指拖动交还父布局。
   - 模型 revision 变化、离开页面或切换阅读模式时重置 transform。
6. 新增 `ReaderPrefetchCoordinator`：以 `visiblePages` 为中心，根据在线/本地独立设置建立有界窗口；取消离开窗口的远程请求；缩略图与全图采用不同尺寸和优先级。
7. 自动翻页继续使用“秒/屏”，默认 3 秒；分页模式按定时器翻页，连续模式按 viewport 高度换算恒定滚动速度。用户触摸、缩放、打开抽屉、到达末页或生命周期暂停时停止。
8. 新增 `ProgressWriter`：UI 只提交稳定可见页；300-800ms 去抖，本地事务先写，服务器能力允许时再同步；`onStop`/`onCleared` 强制 flush；预加载绝不更新进度。
9. ToC 读写移入共享 `CatalogRepository`，详情页和阅读器使用同一 Flow。处理 423 锁定、离线只读和服务器不支持三种状态。
10. 保持现有控制体验：进入不弹底栏、翻页不弹底栏、操作时重置隐藏计时、缩略图滑动不跳页、点击才跳页。

### 可选增强

- 纵向连续模式的图片间距和内容宽度百分比。
- 竖屏/横屏分别保存阅读模式、方向和双页封面规则。
- 自定义 3x3 点击区域，先实现数据模型与预览测试，再开放 UI。
- 状态信息开关：页码、电量、时间；不作为 P0/P1 阻塞项。

### 优先级、依赖与风险

- 优先级：**P0（拆分与手势）/ P1（预加载与方向独立配置）**。
- 依赖：区块 A 的 `ArchiveIdentity`；PageSource 可先不依赖 Room。
- 风险：Compose pointer input 消费顺序、Pager key 改变导致位置丢失、双页页码偏移。
- 控制：纯 Kotlin 页号映射测试、Compose 手势测试、真机横竖屏和大图验证。

### 验收标准

- 1x 时单指左右/上下翻页稳定；放大后可连续平移且不会误翻页；缩回 1x 后翻页恢复。
- 横向、纵向、连续、双页在跳页、自动翻页、恢复进度时指向同一实际页。
- 切换方向或旋转设备后，当前实际页不变。
- 离线与远程模式的预加载数量分别生效，快速滑动后过期请求能取消。
- 退出、锁屏、强杀恢复后进度误差不超过一页。

## 6. 功能区块 C：封面、缩略图与图片缓存

### 构想

用类型化图片模型和稳定修订键替代散落 URL 拼接。封面获取分为“解析真实资源”和“交给 Coil 解码缓存”两步，正确处理 LANraragi 后台生成缩略图的 202 状态。

### 具体实现路径

1. 新增 `data/assets/CoverModel.kt`：`RemoteCover(arcid, revision)`、`SavedCover(file, revision)`、`ArchiveEntryCover(uri, entry, fingerprint)`、`FolderCover(uri, fingerprint)`。
2. 新增 `ThumbnailRepository`：
   - 真实封面解析直接请求 `/api/archives/{id}/thumbnail?no_fallback=true`，不通过比较图片内容猜测是否为占位图。
   - 200 直接返回；202 把 jobid 交给现有 `JobTracker`，完成后只递增该 arcid 的 cover revision。
   - 生成期间 UI 使用客户端占位或已有磁盘缓存，不再额外请求并缓存服务器占位图。
   - 对同一 arcid 做 single-flight，避免列表中重复排队。
3. 使用 `/api/archives/{id}/thumbnail?page=N` 提供阅读器时间线缩略图，不用全尺寸 page URL 生成小图。
4. 扩展 `ArchiveImageFetcher` 支持封面 entry 派生缓存。缓存键至少包含 URI、文件大小/mtime 指纹、entry 名、页号和目标尺寸。
5. 列表与下载页使用固定 `model` 和稳定 item key；刷新真实封面时只改变 revision，不添加时间戳查询参数。
6. 将内存缓存、Coil 磁盘缓存、归档源副本缓存和离线原档计入分别可观测的空间类别。清理操作明确“图片派生缓存”和“已保存档案”不是同一件事。
7. 图片错误分为认证、服务器生成中、源文件失效、解码失败、网络失败；UI 提供针对性重试，不把所有错误显示为同一占位。

### 优先级、依赖与风险

- 优先级：**P1**。
- 依赖：LANraragi `thumbnail`、`minion` API；区块 A 的资源修订字段。
- 风险：错误识别占位图、202 任务风暴、缓存键变化造成磁盘膨胀。
- 控制：job single-flight、失败退避、旧 revision 延迟清理、空间指标。

### 验收标准

- 首次没有 thumb 的档案能自动等待后台生成并替换占位封面，无需重启页面。
- 同一屏同一 arcid 最多触发一个生成任务。
- 下载页上下往返滚动时，已加载本地封面不重复打开/复制整个归档。
- 修改或替换本地归档后旧封面失效；未变动资源继续命中缓存。

## 7. 功能区块 D：持久下载与“已保存”资源

### 构想

当前“原档下载”和“离线缓存”已经共享队列，但仍可能产生两份物理文件和两套入口。下一步把它们统一为一个 `SavedArtifact`：下载一次即可本地阅读，也可按用户命令导出；下载任务本身使用可序列化 payload 恢复。

### 具体实现路径

1. 用 `DownloadTaskSpec` 取代闭包：
   - `SaveArchive(arcid, title, expectedRevision)`
   - `ExportArtifact(artifactId, destinationUri)`
   - `SavePage(arcid, page, destinationUri)`
   runner registry 根据 spec 重建执行逻辑。
2. `DownloadCoordinator` 从 Room 查询 WAITING 任务，通过单个前台 `CoroutineWorker` 或前台服务运行有界协程池。并发数从 DataStore Flow 读取，范围继续限制为 1-8；降低上限不取消正在运行任务。
3. 原档写入 `.part`，记录已完成字节、ETag/Last-Modified 和总长度。恢复时发送 Range；服务器未返回 206 或修订变化则安全重下，绝不能把 200 内容追加到 partial。
4. 状态机使用明确转换：
   `WAITING -> RUNNING -> COMPLETED`，以及 `RUNNING -> PAUSED/RETRY_WAIT/FAILED/CANCELED`。每次转换用事务/CAS 条件更新，避免暂停与完成竞争。
5. 自动重试只处理超时、连接中断、502/503/504；429 尊重 `Retry-After`；401/403 等待连接配置修复；404 标记源已不存在。手动重试重置错误但保留合法 partial。
6. 增加任务优先级和手动重排。默认用户当前打开档案高于后台批量保存；不默认实现 JHenTai 的多分片下载，先保证单连接断点续传正确。
7. 下载页展示速度、已完成/总字节、ETA 和重试原因。右上功能菜单继续提供刷新、清理记录、全部暂停、全部开始；“删除”明确区分删除任务记录与删除已保存文件。
8. 前台通知从 Room 状态聚合活动数；只有 WAITING/RUNNING/RETRY_WAIT 时显示数量角标。
9. `OfflineCacheManager` 收缩为 `SavedArtifactRepository`；LRU 只淘汰未固定（unpinned）资源。正在阅读、下载或导出的文件持有 lease，不允许被淘汰。

### 优先级、依赖与风险

- 优先级：**P1**。
- 依赖：区块 A Room schema；Android WorkManager/前台服务策略。
- 风险：服务器 Range 支持差异、系统限制长任务、暂停与文件写入竞争、迁移后重复文件。
- 控制：响应码验证、临时文件原子替换、任务 lease、按内容指纹去重。

### 验收标准

- 下载到 30% 后强杀 App，重启能从持久 spec 自动或手动继续，不出现“任务不可用”。
- 并发上限在 1、2、8 下均严格生效；修改上限后队列不会丢任务。
- 同一服务器档案执行“离线保存”和“下载原档”不会保留两份相同私有原档。
- 阅读已保存档案全程不请求 page API；导出失败不损坏私有副本。
- LRU 不删除正在阅读、固定保存或执行中的资源。

## 8. 功能区块 E：本地图库与归档读取

### 构想

把 SAF 本地图库视为长期目录而不是每次刷新临时重建的列表。扫描结果持久化到节点级索引，只重新处理发生变化的目录或归档。

### 具体实现路径

1. `LocalScanManager` 改为增量 `LocalLibraryIndexer`：每个根、目录、文件保存 `documentId/uri/name/mime/size/mtime/childSignature`。
2. App 启动直接展示 Room 索引；只有以下事件触发检查：用户刷新、扫描目录集合变化、应用恢复且距上次检查超过阈值、下载/删除由 App 自身完成。
3. 首轮只计算根目录直接子项签名；仅对签名变化的子树递归。对 mtime 永远为 0 的 provider，用子项集合摘要兜底，但节流且可取消。
4. 单个目录包含图片即为图库，不要求绝对没有子目录；规则改为可解释的“当前目录图片数达到阈值”并避免父子重复收录。
5. 用自然排序统一目录、ZIP 和 RAR 页序；名称相同时使用 entry index 保持稳定。
6. `ArchiveFileReader` 保留当前源文件租约和互斥设计，并继续强化：
   - ZIP/CBZ 使用可随机访问的源副本和 `ZipFile`。
   - RAR/CBR 因 Junrar 随机访问成本高，按需提取当前页与相邻页到派生缓存。
   - 不缓存已经消费过的 `InputStream`；只缓存文件、entry 描述或可重新打开的 source。
7. 本地封面、页数和首个合法 entry 在索引阶段写入，详情页不再每次调用 `getImages()` 计算页数。
8. URI 权限失效时保留条目但标记 `UNAVAILABLE`，UI 引导重新授权；重新授权后按 identity 尝试关联原记录与进度。
9. 上传始终是显式、单本、有确认的独立动作；扫描、打开详情、阅读、分类和下载页绝不调用 upload。

### 优先级、依赖与风险

- 优先级：**P1**。
- 依赖：区块 A 的 local tables、区块 C 的本地封面模型。
- 风险：不同 DocumentsProvider 元数据质量差、RAR 解码耗时、嵌套目录重复、URI 权限撤销。
- 控制：provider 兼容测试集、超时/取消、增量事务、不可用状态而非静默删除。

### 验收标准

- 10,000 个文件的未变更图库二次启动不做全量归档解析，首屏直接来自索引。
- 新增、删除或替换一个归档时只更新对应子树和封面。
- 快速滚动本地图库后返回原位置，封面不反复闪烁或重新解压。
- 读取 ZIP/RAR/文件夹的页序一致；连续快速翻页不会泄漏文件句柄。
- 无网络环境下，本地详情、阅读、历史和分类筛选仍可工作；不会产生上传请求。

## 9. 功能区块 F：图库、搜索、分类与预设

### 构想

将图库页面从“同时持有所有业务逻辑的大 Screen”拆为查询状态、资源列表、筛选抽屉和批量操作。服务器分类是 LANraragi 的真实实体；本地来源和下载状态是客户端筛选维度，不伪装成服务器分类。

### 具体实现路径

1. 从 `LibraryScreen.kt` 提取 `LibraryViewModel`、`ArchiveGrid`、`ArchiveList`、`FilterSheet`、`PresetSheet`、`SelectionBar` 和 `CategoryManagementSheet`。
2. 定义 `LibraryQuery`：搜索词、标签、服务器分类 ID、来源、保存状态、新档案、未标记、排序、方向、视图模式。所有 UI 控件只修改这一个对象。
3. 服务器分类通过 `CategoryRepository` 暴露单一 Flow。创建、改名、删除成功后更新本地快照；“重复”“收藏”及 bookmark-linked/locked 分类由 ID 和服务器状态保护，不只按中文名称判断。
4. 预设保存完整 `LibraryQuery`，加 schema version；预设抽屉显示应用、重命名、覆盖和删除动作。
5. 首页刷新先取消旧分页请求，重置游标，再滚动到顶端；滚动动作由 refresh success event 触发，避免请求失败也突然跳顶。
6. 批量操作根据 `ArchiveCapabilities` 生成可用动作。本地项不能出现在服务器删除、服务器分类、元数据插件和自动上传集合中。
7. 分页列表使用稳定 arcid/source key；筛选变化使用 `flatMapLatest` 或显式 generation id 丢弃旧响应，避免后返回的旧请求覆盖新查询。
8. 平板宽度下采用列表/详情双栏，但保持手机导航路径可恢复；不照搬 JHenTai 的 GetX side route。

### 优先级、依赖与风险

- 优先级：**P2**。
- 依赖：区块 A identity/capabilities；区块 C 封面；分类 API。
- 风险：动态分类缓存过期、分页竞态、筛选预设升级、批量操作部分成功。
- 控制：mutation 后局部更新并后台校准、请求 generation、批量结果逐项报告。

### 验收标准

- 分类增删改在详情页和首页筛选抽屉同步出现，无需重启。
- 受保护分类不能改名或删除；服务端 423 能显示明确原因。
- 快速切换筛选不会短暂显示旧结果。
- 刷新成功后回到顶部，刷新失败保持当前位置。
- 混合选择服务器和本地条目时，危险的服务器动作不会作用于本地条目。

## 10. 功能区块 G：元数据刮削、E-H 收藏与标签知识库（重点）

### 目标与产品边界

建立独立“元数据工作台”，从 E-Hentai、ExHentai 和 nHentai 获取标题、标签、分类、来源地址、上传时间等信息，经候选匹配和差异预览后回写 LANraragi。标签中文翻译和补全排序使用同一个 canonical namespace/key 知识库；E-Hentai 的十个收藏槽位作为稳定外部分类源，可选择映射到 LANraragi 静态分类。

这条主线必须遵守四个边界：

1. 刮削结果默认先预览，不直接覆盖服务器元数据。
2. 本地 SAF 档案可以保存客户端侧刮削结果，但不会因为刮削而自动上传 LANraragi。
3. 中文翻译默认仅影响展示和搜索，不把中文译名替换写入服务器 canonical tag。
4. 外部站点凭据、Cookie 和 API Key 彼此隔离，不进入普通 DataStore、日志、备份或任务 payload。

### G1. Canonical namespace 与标签模型

新增 `domain/model/CanonicalTag.kt`：

```text
CanonicalTag
  namespace: String?       // lowercase canonical namespace
  key: String              // canonical source value
  raw: String              // source原值，用于审计/重解析
  displayNameZh: String?   // 展示翻译，不参与服务器写回
  source: TagSource        // EHENTAI / NHENTAI / LANRARAGI / USER
  confidence: Float?
```

新增 `data/tags/TagNamespaceRegistry.kt`，集中管理：

- E-Hentai 主 namespace：`parody`、`character`、`group`、`artist`、`female`、`male`、`mixed`、`other`、`language`、`cosplayer`、`reclass`、`temp`。
- 元数据 namespace：`category`、`uploader`、`timestamp`、`source`。
- LANraragi/用户扩展：`date`、`date_added`、`series`、`event` 及未知 namespace。
- namespace 的英文名、中文名、缩写/中文别名、默认颜色、展示顺序、补全权重和是否默认隐藏。
- `source`、`timestamp` 等机器标签默认从标签云隐藏，但完整保留，不能在格式化时丢弃。

规范化规则：

1. namespace 转小写并 trim；标签值执行 Unicode NFKC、首尾空格清理和连续空白折叠。
2. 保留原始值 `raw`；只对明确维护的 namespace alias 做映射，未知 namespace 原样保留。
3. 去重键使用 `(namespace, normalizedKey)`，不使用中文译名去重。
4. nHentai 返回的 `type=tag` 按 LANraragi 兼容方式保存为无 namespace 标签；其他 type 保留 namespace。
5. `series` 与 `parody` 不自动互转；它们可能来自不同数据源并具有不同语义，只在 UI 中允许用户配置显示分组。
6. 服务端写回仍使用 `namespace:key` 英文 canonical 形式，保证 LANraragi 插件、搜索和其他客户端兼容。

当前 `TagRules` 的颜色和顺序迁入 registry，`TagChip.kt` 只消费 registry 结果，不再保存业务常量。

### G2. Scraper Provider 架构

新增 `data/metadata/ScraperProvider.kt`：

```text
interface ScraperProvider {
  id
  capabilities
  match(request): List<ScrapeCandidate>
  fetch(candidate): ScrapedMetadata
  rateLimitPolicy
}
```

首期实现三个 Provider：

1. `ServerPluginScraperProvider`
   - 默认优先用于 LANraragi 服务器档案。
   - 通过 `GET /api/plugins/metadata` 发现 `ehplugin`、`nhplugin` 和其他 metadata plugin，不硬编码服务器必定安装。
   - 明确 URL/ID 时把它作为 one-shot `arg`；没有明确来源时才允许插件按标题/封面匹配。
   - 短任务调用 `POST /api/plugins/use` 获取预览结果。
   - 长任务调用 `POST /api/plugins/queue`，先轮询 `/api/minion/{jobid}`，完成后必须读取 `/api/minion/{jobid}/detail` 的结构化 `result`。
   - 这两个 plugin endpoint **不会修改档案数据库**；Provider 只返回 `MetadataPatch`，确认后再调用 metadata PUT。
   - 在 `LanraragiRepository.kt` 增加 `getMinionJobDetail()` 和 plugin result parser；不要继续把任意 JSON `notes/result` 压成 `String` 字段。
   - 为 `ehplugin`、`nhplugin` 的 `new_tags/title/summary/error` 建立固定 fixture，同时保留未知字段以兼容其他 metadata plugin。
2. `EhentaiScraperProvider`
   - 用于服务器未安装插件、处理本地档案或用户选择 App 直连时。
   - 明确 E-H/ExH URL 时解析 gid/token，再使用 metadata API 获取 namespaced tags 和标题。
   - 标题搜索、封面反查和 ExH 内容访问是可选能力，需独立 Cookie 会话、严格限流和失败退避；不能把搜索第一页第一项直接当成可靠匹配。
   - 公开 E-H 与登录态 ExH 作为不同 capability，UI 清楚显示当前可用范围。
3. `NhentaiScraperProvider`
   - 明确 URL/数字 ID 优先；文件名 `{id}` 模式次之；标题搜索只产生候选，不自动选中。
   - 外部接口不视为稳定契约，所有 URL、响应解析和反爬错误封装在 Provider 内，使用 feature flag 便于单独停用。
   - 将站点 tag type 映射到 canonical namespace，并保留 source URL。

Provider 选择顺序：用户显式指定 > 已存在 `source:` 标签 > 可用服务器插件 > App 原生 Provider。设置中允许“优先服务器刮削/优先 App 直连”，默认服务器插件优先，避免在手机保存额外站点登录态，也复用 LANraragi 已配置的登录插件。

### G3. 候选匹配与置信度

新增 `MetadataMatchEngine`，按可靠性从高到低收集证据：

1. 用户输入的完整来源 URL 或明确 gid/token/数字 ID。
2. 档案已有 `source:` 标签。
3. 文件名中的站点 ID，例如 nHentai `{123456}` 或配置规则中的 E-H gid。
4. 标题规范化后的完全匹配，结合 artist/group/language 交集。
5. 封面 hash/感知 hash 候选。
6. 标题模糊搜索候选。

`ScrapeCandidate` 保存 source ID、URL、标题、封面、标签摘要、匹配证据、置信度和 Provider。规则建议：

- exact source/ID 可以默认选中，但仍显示差异预览。
- 标题完全匹配只进入“高置信候选”，除非唯一且附加标签一致，否则不自动写回。
- 模糊标题和单纯第一搜索结果永不自动应用。
- 批量刮削只自动处理达到用户配置阈值的项目；其余进入“待确认”队列。

不要把 E-H 与 nHentai 的数字 ID 放在同一无来源字段中；统一键必须是 `(providerId, sourceId)`。

### G4. MetadataPatch、冲突合并与安全回写

新增 `MetadataPatch`：

- `titleCandidate`、可选原文/日文标题。
- `addTags`、`removeTags`，以 canonical tag 为单位。
- `summaryCandidate`、`sourceUrl`、`uploader`、`postedAt`、`category`。
- 每个字段的来源、置信度和抓取时间。

新增 `MetadataPatchMerger`，默认策略：

1. 标签采用集合并集，保留用户标签和其他 Provider 标签；同 canonical key 去重。
2. `source:` 使用 Provider + source ID 去重，可同时保留 E-H 与 nHentai 来源。
3. 标题、summary 不静默覆盖；当前为空可默认勾选，否则用户在 diff 中选择。
4. 刮削器不得自动删除无法识别的 namespace 或无 namespace 标签。
5. 用户可在预览中逐字段、逐 namespace、逐标签接受或拒绝。

LANraragi `PUT /api/archives/{id}/metadata` 会覆盖传入的旧值，因此回写必须执行：

1. 刮削前保存 base metadata fingerprint。
2. 用户确认时重新 GET 最新元数据。
3. 如果 fingerprint 改变，基于最新值重新合并并提示冲突。
4. PUT 完整 title/tags/summary。
5. 再 GET 校验并写入 `metadata_provenance`。

服务器返回 423 时保留 patch 为“待应用”，不能丢弃刮削结果。对本地 SAF 档案，patch 写入 Room 的 `local_metadata`/provenance；只有用户显式上传生成服务器 arcid 后，才允许再次预览并回写服务器。

### G5. 标签中文翻译知识库

当前 `TagTranslationRepository` 将整个精简映射写入 JSON 并放进内存。升级路径：

1. 新增 `TagDictionaryUpdater`，下载 EhTagTranslation 数据到临时文件，记录 URL、ETag/Last-Modified、数据时间戳、校验值和 schema version。
2. 在 IO dispatcher 解析到 staging tables；完整校验通过后用 Room 事务替换当前版本，失败继续使用旧词库。
3. `tag_dictionary` 保存 namespace、key、中文短名、完整名称、简介和 links；namespace 中文名来自 registry，不重复散落在 UI。
4. 建立 FTS 索引，同时搜索英文 key、中文名、namespace 英文/中文别名和简介。
5. 支持三种显示方式：仅原文、中文优先、原文 + 中文；默认中文优先，长按/详情可查看 canonical 原文。
6. 翻译缺失时回退 canonical key；用户本地别名放独立 override 表，不修改上游词库。
7. 自动更新默认低频执行，支持手动更新、查看版本/条目数、清除后回退原文。
8. 打包或在线分发 EhTagTranslation、tag-count 等第三方数据前，逐项核对许可证、署名和再分发要求；版本与来源在设置页可查看。

### G6. 标签补全与排序规则

新增 `TagSuggestionRepository` 和 `TagSuggestionRanker`，合并三类数据：

- LANraragi `/api/database/stats` 的当前库 `weight`。
- e-hentai-tag-count 等全局词频数据。
- 用户最近选择、搜索和手工输入的本地使用频率。

排序分数由以下稳定维度组成，不把原始数量直接相加：

1. 匹配质量：canonical 前缀 > 中文前缀 > canonical 子串 > 中文/简介子串。
2. namespace 权重：沿用 JHenTai 的可配置默认权重，但不把女性/男性等成人内容 namespace 硬编码到 UI 组件。
3. 词频：对服务端和全局 count 使用 `log1p` 归一化，避免超高频标签吞没文本匹配。
4. 个人使用：近期选择提供小幅加权，不覆盖明显更好的前缀匹配。
5. 惩罚：`temp`、弃用标签、低置信翻译和不完整输入降权。

查询解析支持：

- `artist:name`、`作者:中文名` 等 namespace 英文/中文输入。
- `-tag`、`~tag`、带空格的引号标签，并只替换光标所在 token。
- 同时显示中文名、canonical tag、namespace 色条和库内使用次数。
- 用户可在设置中调整 namespace 展示顺序；补全排序权重提供“恢复默认”，不直接暴露难懂的浮点参数。

目标是 50,000-100,000 词条规模下，10 条补全的本地查询 p95 小于 50ms；输入防抖 80-150ms，旧查询通过 `flatMapLatest` 取消。

### G7. E-Hentai 收藏分类与 LANraragi 映射

E-Hentai 收藏分类不是普通字符串列表，而是十个固定身份槽位。新增：

```text
EhFavoriteSlot(slotIndex 0..9, remoteName, remoteCount, color, updatedAt)
EhFavoriteMapping(slotIndex, lanraragiCategoryId, mode)
```

具体路径：

1. 首期支持手工命名十个槽位和导入 E-H 收藏分类快照；槽位身份始终用 index，不用可变名称。
2. 加入可选 `EhAccountConnector` 后，采用加密 Cookie jar 和显式登录/退出，读取远端十个名称、颜色/序号和数量。日志只记录槽位 index 与数量。
3. 收藏条目通过 E-H gid/token/source URL 与已刮削档案关联；没有 source identity 的条目先进入匹配队列，不按相似标题直接归类。
4. 用户可把一个 E-H 槽位映射到一个 LANraragi 静态分类。第一阶段仅支持**单向、显式同步到 LANraragi**：显示将添加、保持和无法匹配的数量，确认后逐项调用分类 API。
5. 不因 E-H 远端取消收藏而自动从 LANraragi 分类删除；删除同步和双向同步留到后续版本，并要求单独确认与审计日志。
6. E-H 收藏槽位可作为首页筛选项，但 UI 要标明“E-H 收藏映射”，避免与 LANraragi 原生分类混淆。
7. 服务器 bookmark-linked 分类仍按 LANraragi ID 保护；它与 E-H 收藏槽位不存在隐式等价关系。

### G8. UI 与任务体验

新增 `ui/metadata/MetadataWorkbenchSheet.kt`，从详情页“元数据”入口打开：

- Provider/来源 URL 选择。
- 候选列表和置信度证据。
- 当前值与刮削值的字段级 diff。
- 标签按 namespace 分组的新增/保留/冲突状态。
- “仅保存本地”“应用到服务器”“稍后处理”动作。

批量刮削使用独立 `MetadataJobCoordinator`，任务状态进入统一任务中心，但与下载并发分开：每 Provider 默认并发 1，并尊重 Provider cooldown、429 和 Retry-After。批量任务可以暂停、继续和重试；应用到服务器仍需满足用户设定的置信阈值和合并规则。

### 优先级、依赖与风险

- 优先级：**P1 核心主线**。G1、G2、G4、G5、G6 优先；G7 的只读槽位/单向映射为 P1-P2。
- 依赖：区块 A Room/identity；LANraragi metadata/plugin/minion/category API；SecurePrefs；区块 C 封面 hash 可提高匹配质量。
- 风险：错误匹配覆盖元数据、plugin result 解析差异、外部站点接口变化和限流、词库体积、Cookie 泄漏、跨来源同名标签误合并。
- 控制：默认预览、来源身份、乐观冲突检查、Provider 隔离、速率限制、词库事务切换、敏感字段红线，并遵守数据源许可与站点访问规则。

### 验收标准

- 明确 E-H 或 nHentai URL 能得到 namespaced 标签、标题和 source tag，并在确认前不修改服务器。
- 服务器插件同步与异步路径都能取得结构化结果；异步路径使用 job detail，而不是假定任务会自动回写。
- 回写前服务器元数据被其他客户端修改时，App 能检测冲突并重新显示 diff。
- 中文、英文、namespace 中文别名都能补全到同一 canonical tag；写回服务器仍为 canonical 英文标签。
- 补全排序优先正确的前缀匹配，再综合 LANraragi 权重、全局词频和个人使用，不因大词频产生明显错误置顶。
- 词库更新中断或数据损坏后仍可使用上一个完整版本。
- E-H 十个收藏槽位改名后映射不丢失；单向同步前有数量预览，且不会自动删除 LANraragi 分类成员。
- 对本地 SAF 档案执行刮削只更新客户端本地元数据，不发生上传或服务器 metadata PUT。

## 11. 功能区块 H：阅读历史、最近会话与进度同步

### 构想

“完整历史”和“首页最近三本”是两个不同产品概念。完整历史长期保存；首页只是对历史的 `LIMIT 3` 投影。进度以本地写入为主，服务器同步为可重试副作用。

### 具体实现路径

1. `ReadingHistoryDao` 保存每个 `ArchiveIdentity` 的最后页、页数、标题快照、封面引用、来源和时间。
2. `observeRecentSessions(limit = 3)` 动态查询最近三本，不再裁剪底层历史。
3. 每次进入阅读器记录一次 open event；稳定翻页只更新进度和 `lastReadAt`。首页点击封面传入 identity 和保存页，Reader 再与最新服务器进度合并。
4. 合并规则：
   - 本地/离线：只采用本地进度。
   - 服务器跟踪进度：同设备最新本地写入优先，后台同步成功后校准；首次设备可使用服务器进度。
   - 服务器关闭进度：不调用 progress API。
5. `PendingProgressStore` 迁入 Room outbox，以 arcid 去重，只保留最新页；连接恢复、App 启动和显式同步时 flush。
6. 删除历史只删除历史，不删除收藏、分类、离线资源或服务器进度；清除最近显示可作为独立命令。
7. 保证 API 页号转换只在 repository 边界进行，UI 和数据库统一使用 0 起页索引。

### 优先级、依赖与风险

- 优先级：**P1**。
- 依赖：区块 A database/identity；ServerCapabilities。
- 风险：本地与服务器页号基准混淆、多个设备互相覆盖、频繁进度请求。
- 控制：边界转换测试、时间戳/最后写入策略、去抖和 outbox 合并。

### 验收标准

- 首页始终仅显示三个最近不同档案，但历史页可查看更早记录。
- 云端、离线和 SAF 本地档案都能从上次实际页恢复。
- 快速翻 50 页不会发送 50 次 progress 请求；最终服务器页正确。
- 服务器不可用时本地进度不丢，恢复连接后只补推每本最新一条。

## 12. 功能区块 I：设置、连接、安全与设备适配

### 构想

设置继续使用 DataStore，但改为按领域拆分的类型化偏好；连接凭据和普通偏好分开。只引入与 Android LANraragi 阅读真正相关的 JHenTai 能力。

### 具体实现路径

1. 将 `AppSettings` 拆为 `ReaderPreferences`、`DownloadPreferences`、`LibraryPreferences`、`AppearancePreferences`，Repository 可共享同一 DataStore，但 ViewModel 不必订阅全部字段。
2. 缓存上限内部改用 `Long bytes`，UI 支持 GB/MB 自定义并做磁盘可用空间校验；`0` 明确定义为不自动淘汰，而不是无限制文案含糊。
3. 阅读方向、阅读模式、双页首封面、内容宽度可选择全局或横竖屏分别设置。
4. 连接页服务器卡片继续作为唯一编辑入口；API Key 保持在 `SecurePrefs`，日志、错误报告和导出配置永不包含密钥。
5. 可选 P2 安全设置：生物认证解锁、最近任务遮罩、禁止截屏。默认关闭禁止截屏，避免影响用户正常保存页面。
6. 使用 WindowSizeClass（必要时加折叠屏 posture）实现 phone/compact tablet/expanded tablet；详情双栏优先于纯放大手机布局。
7. 音量键翻页、硬件键盘方向键/PageUp/PageDown 通过同一 `ReaderAction` 分发，不直接操作 Pager。
8. 设置导入导出只包含非敏感偏好；带版本号并允许预览差异。

### 优先级、依赖与风险

- 优先级：**P2**；设置拆分可在区块 B/D 同步渐进完成。
- 依赖：SecurePrefs、DataStore、WindowSizeClass。
- 风险：偏好迁移丢默认值、横竖屏配置过度复杂、生物认证生命周期问题。
- 控制：默认值兼容测试、逐项迁移、简单模式默认开启、高级设置折叠。

### 验收标准

- 升级后所有现有阅读、下载、主题和扫描目录设置保持不变。
- 自定义缓存上限接受合法数值，拒绝负数和溢出，并正确触发 LRU。
- API Key 不出现在日志、普通 DataStore、配置导出或截图测试夹具中。
- 手机和平板主要页面无控件重叠；旋转后阅读页和详情页状态保持。

## 13. 功能区块 J：可观测性、性能与测试

### 构想

建立能验证阅读、下载、本地索引和 API 边界的自动化测试。日志以事件和耗时为中心，不记录敏感内容。

### 具体实现路径

1. 增加统一 `AppLogger`，事件至少包含：thumbnail job、page source selection、prefetch cancel、download transition、scan delta、progress flush。服务器地址只记录 host hash，绝不记录 API Key。
2. 增加轻量性能指标：图库首屏时间、封面缓存命中率、本地扫描节点数/耗时、归档首图时间、下载重试次数、Reader dropped frame 采样。
3. 单元测试：
   - 页号/双页屏号映射。
   - `ArchiveIdentity` 与 capability 矩阵。
   - 下载状态机、并发限制、重试和 Range 响应处理。
   - LRU lease、缓存上限与迁移。
   - 本地节点指纹、自然排序和增量差异。
   - 最近三本查询、完整历史和 pending progress 合并。
4. MockWebServer 集成测试：thumbnail 200/202、Minion 完成、401/423/429/5xx、子路径反代、下载 200/206 和中断恢复。
5. Compose 测试：底栏不自动出现、翻页不唤起、缩略图滑动不跳页、点击跳页、抽屉操作重置计时、分类和预设抽屉。
6. 真机矩阵：小内存设备、Android 8（minSdk 26）、当前 targetSdk 设备、横竖屏、离线、慢 NAS、10k 本地条目、ZIP/RAR/CBZ/CBR。
7. 性能回归可在后期加入 Macrobenchmark/Baseline Profile，不作为 Room 和 Reader 拆分的前置依赖。

### 建议性能门槛

- 已有本地索引时，下载页/本地图库首屏不等待全量扫描。
- 1000 个图库条目的筛选状态更新不触发全列表图片 model 重建。
- 未变更的 10k 文件 SAF 根检查记录“重扫 0 个归档”。
- 快速翻页 100 页后，网络中只保留当前预加载窗口相关请求。
- 进程重启后，持久下载状态和文件字节一致，无重复追加。

### 优先级、依赖与风险

- 优先级：**P0 起步并贯穿所有阶段**。
- 风险：大规模重构后补测试会非常昂贵。
- 控制：每个阶段先加入待迁移行为的 characterization test，再移动代码。

## 14. LANraragi 0.9.81 API 依赖

所有网络行为以 `tools/openapi.yaml` 为准，歧义再检查 `lib/LANraragi` 控制器。关键契约：

| 功能 | API | 客户端要求 |
| --- | --- | --- |
| 档案列表/筛选 | `GET /api/archives` | 取消旧分页请求，处理服务端排序和分类 |
| 元数据读取 | `GET /api/archives/{id}/metadata` | 刮削前、确认回写前和回写后各获取一次必要快照 |
| 元数据回写 | `PUT /api/archives/{id}/metadata` | 传入字段会覆盖旧值；必须基于最新快照合并并处理 423 |
| 页列表 | `GET /api/archives/{id}/files` | 保留服务端返回路径编码，不重复编码 |
| 封面 | `GET /api/archives/{id}/thumbnail` | 支持 `page` 和 `no_fallback`; 处理 202 job |
| 页面 | `GET /api/archives/{id}/page?path=...` | 只用于显示，预加载不得更新进度 |
| 原档 | `GET /api/archives/{id}/download` | 验证 Range/206 后才追加 partial |
| 进度 | `PUT /api/archives/{id}/progress/{page}` | 检查 server capability；边界统一页号 |
| 目录 | archive ToC endpoints | 详情和阅读器共用 repository；处理 423 |
| 分类 | `/api/categories` 与成员端点 | 按 ID 保护锁定/bookmark linked 分类 |
| 插件发现 | `GET /api/plugins/metadata` | 按 namespace/capability 发现，不假定 eh/nh 插件存在 |
| 同步刮削 | `POST /api/plugins/use` | 返回 plugin data，但不会修改档案数据库 |
| 异步刮削 | `POST /api/plugins/queue` | 返回 jobid；任务结束也不会自动应用 metadata |
| 后台任务 | `/api/minion/{jobid}` | 查询状态、notes 和错误；统一退避 |
| 后台任务结果 | `/api/minion/{jobid}/detail` | 读取 plugin 的结构化 result，不能从 basic status 猜测 |
| 标签统计 | `GET /api/database/stats` | 提供当前库 tag weight，参与本地补全排序 |
| 服务器能力 | `GET /api/info` | 决定进度、认证和兼容行为 |

不允许根据 JHenTai 的 E-Hentai 请求格式推断 LANraragi 参数。

## 15. 明确不照搬的能力

| JHenTai 能力 | 决策 | 原因 |
| --- | --- | --- |
| E-Hentai 阅读页图片 URL 两阶段解析 | 不采用 | LANraragi 已提供稳定 REST 页接口；元数据 Provider 独立实现 |
| 配额、re-unlock、ArchiveBot | 不采用 | 是 E-Hentai 站点限制，不属于 LANraragi |
| GetX 全局 singleton 与 update ID | 不采用 | 当前项目应保留 ViewModel + StateFlow + Compose |
| Flutter 桌面/iOS 路由 | 不采用 | 当前产品是 Android 客户端 |
| E-Hentai 收藏槽位和 tag namespace | 重点采用领域模型 | 用于刮削身份、中文词库、补全排序和可选分类映射，但不冒充 LANraragi 原生分类 |
| 多 isolate 分片下载 | 暂缓 | 先验证 LANraragi Range 与单连接恢复，避免 NAS 压力 |
| 超分辨率 | P3 候选 | 成本、模型分发、功耗和缓存治理尚未解决 |
| 云配置同步 | P3 候选 | 需先定义敏感字段排除和冲突策略 |

JHenTai 采用 Apache-2.0。若未来不是“按思路重写”，而是直接移植具体实现或实质性代码，应补充项目许可证与 NOTICE/归属文件；当前路线图不包含源码复制。

## 16. 分阶段交付顺序

### R0：基线保护与可测试边界（P0）

- 建立测试依赖、Fake API/PageSource、关键现状测试。
- 引入 `ArchiveIdentity`、capability 矩阵和统一页号规则。
- 建立 canonical namespace/tag、MetadataPatch 和 plugin result fixtures。
- 将 Reader 纯 UI 组件从大文件提取，但不改变行为。
- 建立 Room schema v1 和幂等迁移框架。

退出条件：当前 APK 构建通过；现有阅读底栏、缩放、最近三本、下载队列和分类行为有测试保护。

### R1：阅读器可靠性（P0/P1）

- PageSource、布局适配器、ProgressWriter、PrefetchCoordinator。
- 修复并锁定缩放/平移/父 Pager 手势仲裁。
- 统一详情与阅读器 ToC；支持横竖屏偏好。

退出条件：四种阅读模式、三种来源、旋转和强杀恢复通过矩阵测试。

### R2：资源目录与本地图库（P1）

- SavedArtifact、类型化图片 model、thumbnail 202 跟踪。
- 本地索引迁入 Room，节点级增量扫描。
- 本地封面派生缓存、RAR 邻页提取缓存和 URI 失效恢复。

退出条件：大图库首屏、未变更复检、单文件变化和滚动缓存达到性能门槛。

### R3：元数据与标签智能（P1）

- CanonicalTag、namespace registry、Room FTS 标签知识库。
- ServerPlugin、E-Hentai、nHentai Provider 和候选匹配引擎。
- plugin sync/queue/detail result 闭环、diff 预览和冲突安全回写。
- 中文翻译事务更新、英文/中文联合补全和词频排序。
- E-H 收藏十槽位只读同步与 LANraragi 分类单向映射。

退出条件：明确 URL 刮削、候选确认、服务器冲突、词库回滚和收藏映射均通过集成测试；任何路径都不自动上传本地档案。

### R4：持久下载（P1）

- 可序列化 DownloadTaskSpec、Room 状态机、前台协调器。
- 原档与离线保存物理合并、Range 恢复、优先级、速度和 ETA。
- LRU pin/lease 与存储清理语义。

退出条件：下载中强杀、断网、429、服务器重启、空间不足均有明确可恢复结果。

### R5：图库与历史体验（P2）

- Library/Detail/Settings 大文件拆分。
- 单一 LibraryQuery、动态分类、版本化预设和批量 capability。
- 完整历史 + 最近三本投影 + Room progress outbox。
- 平板主从布局。

退出条件：混合本地/云端图库的筛选、批量操作、历史恢复和分类同步通过集成测试。

### R6：安全、设备与性能收尾（P2/P3）

- 生物认证/最近任务遮罩/可选截屏保护。
- 硬件键盘、折叠屏和高级阅读设置。
- Macrobenchmark、Baseline Profile、诊断页。
- 评估超分辨率和非敏感配置同步，不默认承诺实现。

## 17. 优先 backlog

| ID | 项目 | 优先级 | 规模 | 前置 |
| --- | --- | --- | --- | --- |
| A01 | ArchiveIdentity + capability | P0 | M | 无 |
| A02 | Room schema 与 JSON 幂等迁移 | P0 | L | A01 |
| T01 | 页号/双页映射与下载状态机测试 | P0 | M | 无 |
| R01 | ReaderScreen 行为保持式拆分 | P0 | L | T01 |
| R02 | PageSource + local-first resolver | P0 | L | A01, R01 |
| R03 | 缩放/平移手势仲裁 | P0 | M | R01 |
| R04 | ProgressWriter + Room outbox | P1 | M | A02, R02 |
| R05 | 有界预加载协调器 | P1 | M | R02 |
| C01 | Thumbnail 200/202/job 闭环 | P1 | M | A01 |
| C02 | 类型化 CoverModel 与稳定 revision key | P1 | M | C01 |
| M01 | CanonicalTag + namespace registry | P1 | M | A01 |
| M02 | MetadataPatch、provenance 与冲突合并 | P1 | L | A02, M01 |
| M03 | Server plugin sync/queue/detail 闭环 | P1 | M | M02 |
| M04 | E-Hentai 原生 Provider 与匹配候选 | P1 | L | M01, M02 |
| M05 | nHentai 原生 Provider 与匹配候选 | P1 | L | M01, M02 |
| T02 | Tag dictionary Room/FTS 事务更新 | P1 | L | A02, M01 |
| T03 | 中文/英文补全解析和多因子排序 | P1 | L | T02 |
| F01 | E-H 收藏槽位与只读同步 | P1 | L | A02, M01 |
| F02 | E-H 槽位到 LANraragi 分类单向映射 | P2 | M | F01, G01 |
| L01 | 本地索引迁入 Room | P1 | L | A02 |
| L02 | 分层指纹与增量扫描 | P1 | L | L01 |
| L03 | 本地封面/归档页派生缓存 | P1 | M | C02, L01 |
| D01 | 可序列化任务 spec 与 runner registry | P1 | L | A02 |
| D02 | 持久前台下载协调器 | P1 | L | D01 |
| D03 | SavedArtifact 去重、pin、lease | P1 | L | D02 |
| H01 | 完整历史 + 最近三本查询 | P1 | M | A02 |
| G01 | LibraryQuery 与请求 generation | P2 | M | A01 |
| G02 | Library/Detail/Settings 拆分 | P2 | L | G01 |
| U01 | 平板主从布局 | P2 | L | G02 |
| S01 | 安全与配置导入导出 | P2 | M | A02 |

## 18. 主 agent / 子 agent 实施框架

后续实现沿用“主 agent 设计和集中验收，子 agent 只交付有界补丁”的要求。

### 主 agent 固定职责

1. 先确定状态唯一来源、公共接口、页号基准和迁移方案。
2. 将工作拆成互不重叠的 write set；`AppContainer.kt`、Gradle、数据库版本和大 Screen 集成由主 agent 持有。
3. 子 agent 不构建、不测试、不自行宣称验收，只报告修改文件和实现假设。
4. 主 agent 逐个审查 diff，集中运行一次构建、单元测试和必要的设备测试。
5. 每个版本只合并一个可回滚主题，不同时重写 Reader、Download 和 Library。

### 推荐子 agent write set

| 包 | 子 agent 修改范围 | 主 agent 集成点 |
| --- | --- | --- |
| DB schema | `data/db/**` | Gradle、Application、迁移触发 |
| Reader domain | `data/reader/**`、`domain/model/**` | ReaderViewModel/AppContainer |
| Reader UI | `ui/reader/components/**` | ReaderRoute 与旧 Screen 删除 |
| Asset pipeline | `data/assets/**`、Fetcher 新文件 | Coil ImageLoader 注册 |
| Metadata providers | `data/metadata/providers/**` | Provider registry、网络凭据和回写入口 |
| Tag knowledge | `data/tags/**` | Room schema、Search/TagChip 接线 |
| E-H favorites | `data/favorites/**` | SecurePrefs、连接页和 CategoryRepository |
| Local index | `data/local/**` | 设置目录 Flow、下载页/图库接线 |
| Download engine | `data/download/**` | 前台服务、通知、旧任务迁移 |
| Tests | 对应 feature 的 test 文件 | 主 agent 统一执行和修正 |

禁止两个子 agent 同时修改 `ReaderScreen.kt`、`LibraryScreen.kt`、`AppContainer.kt`、`SettingsRepository.kt` 或 Gradle 文件。需要拆大文件时，先由主 agent 建立新接口和目标文件，再分派只写新文件的任务。

## 19. 集中验收矩阵

| 场景 | 自动测试 | 真机/服务器验证 |
| --- | --- | --- |
| 远程阅读 | MockWebServer、页号、progress outbox | LANraragi 0.9.81、慢网、断网 |
| 离线原档 | PageSource、LRU lease | 飞行模式、强杀恢复 |
| SAF 文件夹/ZIP/RAR | 排序、指纹、URI 状态 | 不同文件管理器/provider |
| 手势 | Compose pointer 测试 | 双指缩放、单指平移、边缘翻页 |
| 双页/旋转 | 映射单测 | 横竖屏、奇偶页、封面单页 |
| 下载 | 状态机、Range 200/206、并发 | 前台通知、锁屏、进程回收 |
| 缩略图 | 200/202/job single-flight | 未生成 thumb 的真实档案 |
| 元数据刮削 | plugin fixtures、候选评分、patch merge | E-H/nH 明确 URL、错误匹配、429 |
| 标签知识库 | FTS、中文/英文 token、排序稳定性 | 大词库更新、离线补全、旧库回滚 |
| E-H 收藏 | 槽位 identity、映射 diff | 登录失效、改名、单向分类同步 |
| 分类/ToC | 423、局部缓存更新 | 创建、改名、删除、同步显示 |
| 历史 | LIMIT 3、完整历史、去重 | 云端/本地混合恢复 |
| 升级 | JSON fixture -> Room migration | 从当前正式 APK 覆盖安装 |

集中验证命令基线：

```powershell
$env:JAVA_HOME='<JDK 21 安装路径>'
$env:HTTP_PROXY=''
$env:HTTPS_PROXY=''
.\gradlew.bat testDebugUnitTest :app:assembleDebug
```

手势、SAF provider、前台下载、系统状态栏和 LANraragi 真实任务队列仍必须使用连接设备验证，不能只凭 JVM 测试验收。

## 20. 主要风险与回滚原则

1. **Room 迁移风险**：旧 JSON 至少保留一个版本；Room 导入失败回退旧 repository，只读展示也优于清空。
2. **下载文件风险**：所有写入使用 `.part` 和原子替换；数据库完成状态必须晚于文件 fsync/关闭。
3. **SAF 风险**：权限失效标记 unavailable，不立即删除记录和进度。
4. **缓存风险**：派生缓存可删除，用户主动保存的原档不可被“清理图片缓存”删除。
5. **服务端兼容风险**：能力按 `/api/info` 和 HTTP 响应探测；不以版本字符串硬编码唯一分支。
6. **重构风险**：先做 characterization tests 和行为保持式提取，再改逻辑；每阶段可单独 revert。
7. **并发风险**：下载状态、LRU lease、thumbnail job 和扫描任务都必须有单一协调者，禁止多个 Screen 直接写文件状态。
8. **隐私风险**：本地档案默认永不上传；API Key 不进入日志、Room、普通 DataStore、备份或报告。
9. **元数据覆盖风险**：plugin endpoint 只返回候选，任何自动应用都必须经过最新快照合并、置信阈值和可追溯 provenance。
10. **外部站点风险**：E-H/nH Provider 独立限流并可远程/本地停用；接口变化不能拖垮 LANraragi 浏览和本地阅读。
11. **翻译污染风险**：中文译名只用于显示和检索，canonical 英文 tag 是服务端写回主键。
12. **账户风险**：E-H Cookie 只进入加密凭据仓，退出后清除；收藏同步首期只读远端、单向写 LANraragi。
13. **数据许可风险**：标签翻译和词频数据记录来源、版本与许可证；未确认允许再分发时只提供用户主动下载，不随 APK 打包。

## 21. 最终建议

最先执行的四个工作包应是：

1. **A01 + T01 + R01**：建立身份/capability、关键测试并拆 Reader，先降低继续开发的冲突面。
2. **A02 + M01 + M02**：引入 Room，先确定 canonical tag、patch、provenance 和覆盖冲突规则。
3. **M03 + T02 + T03**：修复服务器插件结果闭环，并完成中文词库与标签补全排序主链路。
4. **H01 + R04 + R02 + C01 + L01**：修正完整历史/最近三本，统一 PageSource、真实 thumb 和本地增量目录。

E-H/nH 原生 Provider 与 E-H 收藏同步应在 M03 的服务器插件链路稳定后加入，再实施 D01-D03 的持久下载。这样先把“如何匹配、如何合并、如何安全写回”做对，避免多个刮削来源各自形成不可兼容的数据格式，同时让现有阅读器、分类、离线原档和下载并发功能继续可用。
