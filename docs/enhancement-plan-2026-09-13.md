# lanraragi-reader 增强改进计划报告

日期：2026-09-13
参考代码库：`D:\program\LANraragi-v.0.9.81`（服务端 0.9.81，契约权威 `tools/openapi.yaml`）、`D:\program\JHenTai-8.0.16`（Flutter 漫画客户端）
目标代码库：`D:\program\lanraragi-reader-merged`（Kotlin/Compose）

**APP 定位**：LANraragi 服务器的专属阅读器，同时是强大完善的云端/本地阅读器。服务器档案绝大多数下载自 E-Hentai/nHentai、需要补全元数据，因此**元数据刮削器 + EhTagTranslation 翻译规则带来的元数据匹配是一级刚需**——这些元数据既是档案标签，也供搜索匹配；E-Hentai 账号侧功能（收藏夹同步）不在定位内，列入移除。

---

## 一、现状基线（lanraragi-reader 已具备）

- **连接**：多服务器 Profile、API Key 加密存储、连通性测试、Shinobu 状态/重扫。
- **库**：网格/列表、无限分页、排序（标题/最近阅读/添加日期/作者）、分类筛选、命名空间标签筛选面板、筛选预设、搜索历史、封面预取、批量多选。
- **分类**：分类 CRUD、书签分类；Tankoubon API 已接入但 UI 受 feature flag（默认关）。（E-Hentai 收藏同步已列入移除，见 D1）
- **阅读器**：单页/连续/双页、LTR/RTL、横竖屏独立配置、点击翻页区、捏合缩放、音量键、适应模式、背景色、亮度、自动滚动、常亮、5 页缩略图时间线、ToC（接了 `/archives/{id}/toc`）、进度 outbox 回传。
- **下载**：队列+并发+优先级+前台服务、页图离线缓存（LRU）、原档导出（SAF）、Room 持久化、ETag/Last-Modified 续传。
- **本地库**：SAF 多目录扫描、增量索引、本地详情。
- **元数据**：CanonicalTag、标签中文翻译词库、标签统计、标签 FTS 知识库、EH/nHentai 刮削、服务端插件协调。

结论：基础盘相当完整。改进空间主要在（A）服务端 0.9.8+ 新能力未接入、（B）阅读器深度交互与图像处理、（C）下载/同步的健壮性与 UX。

---

## 二、A 组：接入 LANraragi 0.9.81 未用能力（优先做，契约明确）

| # | 改进项 | 服务端依据 | 现状差距 | 建议 |
|---|---|---|---|---|
| A1 | **随机推荐 / 随机阅读** | `GET /search/random`（category/filter/count/newonly/hidecompleted/groupby_tanks） | 完全未接 | 底栏第 4 键呼出「续读/随机」共用抽屉：随机模式每次取 3 本（count=3），**范围继承库页当前筛选规则**——未筛选时全部档案随机，应用搜索/筛选后在命中范围内随机，不足 3 本全量展示；点卡片直接进阅读器 |
| A2 | **hidecompleted / newonly 深度利用** | `/search` 参数 `hidecompleted`、`newonly`、`untaggedonly`、`sortby=lastread` | 有 newonly/untaggedonly，缺 hidecompleted | 筛选面板加"隐藏读完"；配合 ServerInfo `server_tracks_progress` 探测可用性 |
| A3 | **启动时读 `/info` 做能力探测** | ServerInfo：`server_tracks_progress`、`archives_per_page`、`has_password`、`excluded_namespaces`、`total_pages_read` | 部分接入 | 缓存 ServerInfo，按能力开关 UI（进度回传、lastread 排序、排除命名空间隐藏） |
| A4 | **整本页缩略图（页面时间线升级）** | `POST /archives/{id}/files/thumbnails`（202+minion job，`notes->progress/pages` 可做进度条） | 时间线只有 5 页 | 打开时间线时请求整本页缩略图，用 minion job 轮询显示生成进度 |
| A5 | **Stamps 页面批注** | `/archives/{id}/stamps` CRUD（0.9.8 新增） | 未接 | 二期可选：书签页/笔记功能 |
| A6 | **标签云与统计** | `GET /database/stats`（minweight、hide_excluded_namespaces） | 有本地 TagStats，但未用服务端权重 | 标签筛选页加服务端权重排序/词云，尊重 excluded_namespaces |
| A7 | **重复检测** | `POST /minion/find_duplicates/queue` + `/minion/{id}/detail` | 未接 | 设置/工具页发起任务，展示结果对，跳转对比 |
| A8 | **插件异步执行** | `POST /plugins/queue` + minion 轮询（现用同步 `/plugins/use`） | 同步易超时 | 刮削等长任务切异步队列 + 进度 UI |
| A9 | **搜索语法增强** | filter 支持 `"短语"`、`?`/`*` 通配、`-排除`、tag 末尾 `$` 精确 | 仅模糊搜索 | 搜索框透传服务端语法，加语法提示；`start=-1` 全量拉取用于批量操作 |
| A10 | **数据库备份/恢复** | `GET/POST /database/backup`（异步 job 版） | 未接 | 工具页一键备份/恢复 |
| A11 | **全清 New 标记** | `DELETE /database/isnew` | 未接 | 库页批量操作加"全部清除 New" |
| A12 | **OPDS/PSE 兼容层（可选）** | `/opds`、`/opds/{id}/pse` | 未接 | 低优先级；除非想支持第三方阅读器互通 |
| A13 | **Tankoubon 转正** | tank 进度/缩略图/`groupby_tanks` | API 已接、flag 默认关 | 补齐 tank 进度回传与搜索分组后默认开启 |

## 三、B 组：借鉴 JHenTai 的阅读器与 UX 增强

| # | 改进项 | JHenTai 参考实现 | 现状差距 | 建议 |
|---|---|---|---|---|
| B1 | **更多阅读模式** | 9 种 ReadDirection：横向连续卷轴（LTR/RTL）、适配宽度翻页模式 | 缺横向连续、缺"适配宽度"翻页 | 新增 `left/right2right 连续卷轴` 与 `fitWidth 翻页`，布局策略用接口抽象 |
| B2 | **3x3 自定义点击分区** | `model/tap_zone_config.dart` + 设置页，行列比例可调、逐格映射动作 | 现为固定左/中/右三区 | 固化三区升级为可配置九宫格（列宽/行高比例 + 每格动作：上/下页/菜单/无） |
| B3 | **横竖屏分别记忆更多设置** | 方向、点击区、显示区宽度比例按 orientation 独立记忆 | 已有阅读模式分横竖屏 | 把适应模式/点击区/预取数也纳入 per-orientation 配置 |
| B4 | **预取按"屏高倍数"模型** | `preloadDistance`（屏高倍数）+ `preloadPageCount` 双参数 | 固定"页数"预取 | 连续滚动模式改按屏高倍数预取，翻页模式保留页数 |
| B5 | **自动翻页/自动滚动样式** | AutoModeStyle（翻页 vs 滚动）、间隔可配 | 只有自动滚动 | 加自动翻页模式与间隔/样式设置 |
| B6 | **图像处理：裁边/显示区宽度比例** | `imageRegionWidthRatio` 伪裁边 | 无 | 阅读器加"显示区域宽度比例"滑杆，实现轻量裁边 |
| B7 | **页面内嵌缩略图条 + 跳页对话框** | 页码 slider + 缩略图条 + jump dialog | 有 5 页时间线 | 升级为整本缩略图条（依赖 A4）+ 精确跳页输入 |
| B8 | **进度定时 flush** | 内存缓存 + 每 5 秒 flush | outbox 机制已有 | 对齐确认：翻页进度本地即时落库、服务端 outbox 批量回传（现状接近，补 5 秒合并） |
| B9 | **按路由记忆列表形态** | 每页独立 list/grid/waterfall 设置（7 种列表样式） | 全局 grid/list | 库/历史/下载页各自记忆视图与密度；瀑布流可选 |
| B10 | **应用锁 / 安全** | 应用锁、R18G 遮罩 | 无 | PIN/生物识别锁 + 启动遮罩，配合现有 EncryptedPrefs |
| B11 | **配置云同步（类型化+版本号）** | 按类型导出/导入配置（进度/历史/快速搜索/屏蔽规则），带版本号 | 已有 JSON 配置导入导出 | 升级为按数据类型分文件 + schema 版本号，便于迁移与合并 |
| B12 | **快速搜索（命名条件）** | quick_search：保存命名搜索并手势呼出 | 有筛选预设 | 库页搜索框右侧按键改为**预设搜索规则快捷展板**：点击弹出已保存预设面板一键应用，长按管理；预设覆盖 filter 全语法（配合 A9） |
| B13 | **大 JSON isolate 解析** | isolate_service 防大 JSON 卡 UI | 未确认 | 库全量拉取/备份解析放 Dispatchers.Default 已够；备份 A10 时注意 |

## 四、C 组：下载与同步健壮性（借鉴 JHenTai 下载服务）

1. **并发 × 速率双参数**：现在只有 `downloadConcurrency`；JHenTai 用 并发数 + 每秒任务数（rate limiter）双参数防打爆服务器。建议加 `maxTasksPerSecond`（默认 2/s）。
2. **逐图状态机 + CAS 恢复**：下载任务以 per-image 状态机持久化，恢复用 CAS（`fromStatusIndex` 防竞态），启动自动恢复（`restoreTasksAutomatically`）。当前 Room 已有任务持久化，补 CAS 语义与启动自动恢复开关。
3. **失败分类处理**：按 HTTP 状态分类（超时重试 / 5xx 指数退避 / 特定错误自动暂停整队），重试上限可配（JHenTai：5 次）。
4. **下载优先级到分组**：已有优先级，补"分类/分组整体设优先级"。
5. **暂停/恢复感知的速度统计**：SpeedComputer 滑窗计算，暂停时清零。
6. **存储门禁（已确认方案）**：存储根目录配置化——现有离线缓存硬编码在 `filesDir/offline`（`OfflineCacheManager.kt:60`），需迁移为配置根（扩展现有可空 `downloadDirUri` 为统一 `storageRoot`，单一权威 flow）。**门禁只拦批量产物**（离线页缓存/原档保存/本地扫描目录）；内部状态（Room 历史/进度、DataStore 设置、Coil 封面缓存）不拦截也不可拦截，否则进度丢失。未配置时 `DownloadManager`/`OfflineCacheManager`/`SavedArtifact` 写入口统一拒绝并引导到配置；SAF 授权被系统回收或存储介质移除映射为"存储不可用"错误类（并入 C3）+ 下载自动暂停 + 重新授权恢复流程；老安装视为已配置（现位置即默认目录），强制存储配置步骤仅对新装首启生效（向导见 UI 规划 2.4 节）。

## 五、D 组：元数据刮削与标签翻译（定位核心，一级刚需）

本 APP 的档案绝大多数下载自 E-Hentai/nHentai 且元数据不全，"刮削补全 + 翻译匹配"是核心链路：刮削得到的元数据既是档案标签（服务端 `PUT /archives/{id}/metadata` 的 title/tags/summary），也直接供搜索匹配使用。

**服务端统一中文为目标形态（已确认的设计）**：服务端"标签规则"先行——插件入库前改写（`enable_tagrules`，`Model/Plugins.pm:275-278` → `Utils/Tags.pm rewrite_tags`，逐条替换、不保留原文）；规则未命中的英文元数据（服务器插件刮到规则之外的 tag）由 **APP 负责二次翻译与回写覆盖**：APP 按下载的翻译词典把服务端英文 tag 译为中文，经 `PUT /archives/{id}/metadata` 覆盖写回。库中仍会过渡性存在英文（词典无条目、历史数据），显示与搜索按 D5 兜底。

| # | 改进项 | 现状差距 | 建议 |
|---|---|---|---|
| D1 | **移除 EH 收藏夹同步** | `ui/screens/EhFavoritesSyncScreen.kt`、`data/favorites/EhFavoriteCategorySync.kt`、`eh_favorites_sync` 路由及入口 | 整体删除（UI、路由、同步逻辑、相关设置项与导入导出字段）；由它生成的"EH 收藏分类"保留为普通分类；`FavoritesScreen` 属"收藏图片"功能，一并移除（见 D7） |
| D2 | **刮削器强化** | 已有 EH/nHentai provider 与服务端插件协调（同步执行） | 标题清洗 + 多策略匹配（日文原题/罗马音/发布名/长度比阈值），多候选列表供人工确认；tags 经 D6 翻译管线转中文（无词典条目保留英文原文）后随 diff 预览（title/tags/summary）确认写回 `PUT /archives/{id}/metadata`；批量执行走 A8 异步插件队列 |
| D3 | **EhTagTranslation 翻译规则对齐** | 已有 `TagTranslationStore` 词库下载与 TagChip 译名显示 | 数据源与规则对齐 JHenTai 所用的 EhTagTranslation 项目（同源 tags/翻译数据，含命名空间与 uid 映射），支持自动/手动更新；刮削回来的 EH tag 自动进入同一翻译管线 |
| D4 | **翻译驱动的搜索匹配** | `tag_dictionary`/`tag_dictionary_fts` 已建，但搜索未走词典归一化 | 搜索与标签筛选先把输入归一化：译名/别名 → 库内实际形态的 tag（目标形态为中文，见 D6），再发服务器 `filter`；搜索联想、标签筛选面板、详情页标签跳转共用该映射；FTS 建索引时纳入别名 |
| D5 | **双语过渡态兼容（CanonicalTag 双向身份）** | 词典映射为"英→中"单向；CanonicalTag 以原始字符串为身份 | 回写完成前的过渡态与词典缺失兜底：英文残留 tag 显示时走译名兜底、搜索支持双向归一化（中↔英）；同一档案混有英/中 tag 时经词典双向映射合并身份，标签统计与去重不分裂；D6 回写后逐步收敛为纯中文 |
| D6 | **服务端元数据中文化（翻译回写）** | 无 | 扫描（全库/分类/单本）英文 tag → 按 EhTagTranslation 词典映射（保留 `female:` 等命名空间前缀，无条目保留原文，按 CanonicalTag 去重合并）→ diff 预览 → 经 `PUT /archives/{id}/metadata` **全量覆盖回写**（该端点为覆盖式，必须整体写回 title/tags/summary 防止丢字段）；逐本排队执行避免压垮服务器；标题/简介不自动翻译回写（人工在工作台处理）；可选"新档案入库自动回写"开关 |
| D7 | **完全移除收藏图片功能** | `ui/screens/FavoritesScreen.kt`、`favorites` 路由、导航页「收藏的图片」入口、阅读器 `downloadCurrentPage()` 与「保存此页到收藏」（单页保存唯一入口）、下载页 `FavoriteSection` 与收藏目录、`DownloadTaskType.PAGE` | 整体删除，**不做替代的单页保存入口**；PAGE 任务类型随删（Room 已有任务启动清理，legacy 导入映射同步删）；已保存图片残留应用专属目录（卸载自动清除）。档案红心收藏（`FavoritesRepository`/书签分类）为另一套系统，不受影响 |

## 六、建议实施顺序

**第一批（低风险高价值，契约明确）**
A1 随机推荐（「续读/随机」共用抽屉，各 3 本）、A2 hidecompleted、A3 ServerInfo 能力探测（`ServerCapabilities` 接为其门控载体）、A11 全清 New、C1 速率限制、C3 失败分类；D1 移除 EH 收藏夹同步、D7 完全移除收藏图片功能（不做替代的单页保存入口）。同步落地 UI 决策：底栏第 3 键改为导航页（设置收进导航页作为选项）、库页搜索框右侧改为预设搜索快捷展板、移除打卡功能与全部 UI、首启向导改造（Komikku 卡片式五步：主题/存储(强制)/权限/服务器(可跳过)/收尾，成功卡即 A3 落点）、无入口功能处置（SearchScreen/StatsScreen/TagStatsScreen 接线、死代码与死 flag 删除、孤儿设置项接线、「标记未读」入口，详见 `docs/orphan-feature-disposal-2026-09-13.md`）；多服务器 profile 管理 UI 排第三批。**后续修订（2026-09-13 第五轮）**：底栏第 3 键最终为**设置页**，导航页整体并入设置页并删除（统计/历史/分类/单行本收进「浏览」组，URL 下载并入「工具」组），落地与真机验证见 `docs/acceptance-report-2026-09-13.md`「二·补五」。

**第二批（阅读器体验）**
B1 横向连续/fitWidth 模式、B2 九宫格点击区、B4 屏高倍数预取、B6 显示区比例、A4 整本页缩略图 + B7 缩略图条。

**第三批（后台与任务 + 元数据链路）**
A8 插件异步、A7 重复检测、A10 备份恢复、C2 CAS 断点续传与自动恢复、C4 分组优先级；D2 刮削器强化（依赖 A8 异步编排）、D3 翻译数据源对齐、D4 翻译驱动搜索匹配、D6 翻译回写（依赖 D3 词典与 D2 工作台）。

**第四批（可选/大件）**
B10 应用锁、B11 配置同步升级、B12 快速搜索、A13 Tankoubon 转正、A5 Stamps、A12 OPDS。

## 七、约束与验证

- 所有新端点以 `tools/openapi.yaml` 为契约来源，实现时逐字段核对（0.9.81 对 `search/random` 的 groupby_tanks 默认值等有 hotfix，勿凭记忆）。
- 元数据层遵守 D 组链路：**服务端目标形态为中文**——服务端标签规则先改写入库，APP 刮削与"翻译回写"（D6）负责把规则未命中的英文 tag 译为中文后覆盖回写（`PUT /archives/{id}/metadata` 为覆盖式，须全量写回防丢字段）；标题/简介不自动翻译回写；过渡期英文残留按 D5 兜底。
- 进度相关功能需先探测 `server_tracks_progress`，未开启时 UI 降级。
- 遵循项目现有模式：Compose + MutableStateFlow + repository + AppContainer；新功能走 feature flag；图标控件中文 contentDescription；IO 走 Dispatchers.IO。
- 每批完成后跑 `.\gradlew.bat :app:assembleDebug`；手势/滚动/服务端集成项标记为待设备验证。
