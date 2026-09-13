# 无入口功能处置清单

日期：2026-09-13（决策已确认）
来源：全库盘点（feature flag 全集 / 无跳转路由 / 零引用组件与 VM / 孤儿 Settings 字段 / 零调用 API 包装 / 半成品架构）
与《功能增强规划》《UI/UX 增强规划》的批次对应关系见文末。

---

## 一、决策总表

| # | 功能项 | 代码位置 | 决策 | 落点 |
|---|---|---|---|---|
| 1 | SearchScreen 搜索页（含搜索历史、标签补全，`SearchBus` 唯一生产者） | `ui/screens/SearchScreen.kt`、`data/SearchHistoryRepository.kt`、`ui/screens/LibraryBus.kt` | **接线**：库页搜索框点按跳入 SearchScreen | UI 规划 2.3 / 第一批 10 |
| 2 | StatsScreen 服务器统计页（含 VM，完整） | `ui/screens/StatsScreen.kt` | **并入统计页**作"服务器统计"分区（与本地使用统计并列，接 A3 `total_pages_read`） | UI 规划 七 / 第一批 10 |
| 3 | TagStatsScreen 标签词云 | `ui/screens/TagStatsScreen.kt`（连路由都未注册） | **接线到统计页**（A6 `/database/stats` 的 UI 落点） | UI 规划 七 / 第一批 10 |
| 4 | CheckinScreen 打卡 | `ui/screens/HistoryScreen.kt:190-290`；**注意 `MainScreen.kt:376` 启动时自动打卡调用需一并拆除** | 已定删除（维持） | 规划已含（第一批 5） |
| 5 | OfflineScreen 离线缓存列表 | `ui/screens/OfflineScreen.kt` | 已定退役，职责由下载页缓存管理卡承接（维持） | 规划已含（第三批 17） |
| 6 | `d2_multi_select` flag | `data/FeatureFlags.kt` + 实验室开关 | **删除**（零消费点，多选已全量上线）。后续「实验室」页整体移除、`FeatureFlags` 文件与其余下两个开关一并删除，见验收报告「二·补六」 | 第一批 10 |
| 7 | `ui/reader/ReaderViewModel.kt` 原型薄封装 | 零引用，与 ReaderScreen 内同名类无关 | **删除** | 第一批 10 |
| 8 | `data/reader/ReaderLayoutAdapter.kt` | 零引用原型 | **删除** | 第一批 10 |
| 9 | ~~CategoryUi 版 `CategoryManagementSheet`~~ → 实为 `ui/screens/CategoryManager.kt` 的 `CategoryManagerSheet` | ~~`ui/CategoryUi.kt:112`，被 CategoryBrowseScreen 私有同名实现遮蔽~~ | **删除**（保留同文件活代码：工具函数与 `CategoryRefreshBus`） | 第一批 10 |
| 10 | repo 零调用包装：`getTankoubon`（非 full）；`setArchiveNew` | `LanraragiRepository.kt:454-461` / `:372-378` | **getTankoubon 删除**；**setArchiveNew 接为「标记未读」入口**（库/详情操作菜单，服务端 `PUT isnew` 现成；如不需要可改删除） | 第一批 10 |
| 11 | 多服务器 profiles 半成品（数据模型+迁移 V3+读取齐全，无切换/增删 UI，`saveServer` 只维护 profiles[0]） | `SettingsRepository.kt:228-229,303-306,347-350` | **继续做**：连接设置页加 profile 管理 UI（列表/新增/切换/删除），`setActiveProfile` 补齐 | 第三批 19 |
| 12a | `coverPrefetchCount`（封面预取数，设置项在但零消费方） | `SettingsRepository.kt:192,260,464` | **接线**到封面加载器 | 第一批 10 |
| 12b | `bottomBarColor`（底栏未选中色，实际硬编码 `0xFF94A3B8` 于 `MainScreen.kt:631`） | `SettingsRepository.kt:212,284,505` | **接线**（玻璃底栏组件化时一并接） | 第一批 2+10 |
| 12c | `floatingButtonColor`（详情 FAB 不取色） | `SettingsRepository.kt:211,283,504` | 已定接线修复（玻璃组件化时读取） | 规划已含（第一批 2） |
| 12d | `hideInGallery`（只写/删 `.nomedia`，无 UI 语义） | `SettingsRepository.kt:218,290,572-587` | ~~**明确语义**："本地库过滤含 .nomedia 的目录"，接线到 `LocalLibraryIndexer`/`LocalScanManager` 扫描过滤~~ → **改为：维持 `.nomedia` 写/删语义，扫描器不过滤**（见下方验收勘误） | 第一批 10 |
| 13 | `ServerCapabilities` 能力门控类（注释称供 A 系列入口显隐，从未被用；同文件 `refreshServerInfo` 是活的） | `data/ServerCapabilities.kt:11-41` | **接为 A3 能力门控载体**：tank/toc/stamps 等入口按服务器版本与 `server_tracks_progress` 显隐 | 第一批 10 + A3 |

## 二、顺手修复（同批）

1. `previewColumns` 默认值不一致：data class 默认 4 vs DataStore 回退 3（`SettingsRepository.kt:71 vs 258`）——统一为 4。
2. tank 列表分页：`api.getTankoubons` 支持 `page` 参数但 repo 包装从不传（`LanraragiRepository.kt:447`）——接线分页。
3. `a11_tankoubons` 门控口径统一：`DetailScreen.kt:703-722` 的单行本关联入口不受 flag 门控（flag 关时详情可见、导航无入口）——与导航入口同口径。

## 三、明确不动

- 双下载体系并存（`DownloadManager` legacy 门面 + `download/DownloadCoordinator`）：有意设计（RoomDownloadStores 注释明示 compatibility facade），本清单不收敛，留待后续架构项。
- `ReaderFrameSampler`：已接线（ReaderScreen → DiagnosticsFacade → 诊断页性能指标），非孤儿。
- `PrefetchCoordinator`/`PagePrefetcher`/`ReaderSession`/`ReaderGestureArbiter`：活跃。
- Manifest：无孤儿组件；深链已接线。

## 四、批次对应

- **第一批 10**：处置清单 1、2、3、6、7、8、9、10、12a-d、13、顺手修复 1-3。
- **第三批 19**：处置清单 11（多服务器 profile 管理 UI）。
- 已决项（4、5、12c）在主规划原批次执行。

---

## 五、验收勘误（2026-09-13 真机/代码验收后补记）

本清单有两处前提与实际代码不符，落地时按代码事实执行；详见 `docs/acceptance-report-2026-09-13.md`。

1. **第 9 项指向的文件写错了。** `ui/CategoryUi.kt:112` 的 `CategoryManagementSheet` 并不是「被遮蔽的重复实现」，
   而是**活代码**：`DetailScreen.kt`（import + 调用）与 `LibraryScreen.kt`（import + 调用）都在用；
   `CategoryBrowseScreen.kt:475` 那个私有实现叫 `CategoryManageSheet`（名字不同，不存在同名遮蔽）。
   真正零引用的是同名不同字的 `ui/screens/CategoryManager.kt` 里的 `CategoryManagerSheet`——**已按此删除该文件**，
   `CategoryUi.kt` 的活代码原样保留。
   附带发现：审计曾据「零引用」把 `ui/library/LibraryPresentation.kt` 的
   `LibraryMasterDetail`/`LibraryItems`/`LibraryEmptyDetail` 判为传递性死代码，**该判断也是错的**：
   它们经 `AdaptiveLayoutHost` 在 ≥840dp 宽时生效（真机 `wm size 3000x1400` 验证：图库右侧出现
   「选择一个档案查看详情」占位，点击卡片后原地显示详情面板 + 「打开详情」）。
2. **第 12d 项的语义定反了。** 该项要求「本地库过滤含 `.nomedia` 的目录」。经与用户确认，
   `hideInGallery` 的真实目的恰恰相反：**只让系统相册/MediaStore 不索引下载目录里的图片，
   APP 自己的 SAF 目录树扫描不读 `.nomedia`，本地书架不受影响**。
   因此 12d **不做扫描过滤**，维持「写/删 `.nomedia`」语义；本轮只修了同处的真实缺陷——
   `SettingsRepository.updateNoMediaFile` 原先在主线程做 SAF（ContentProvider）往返，
   现已包进 `withContext(Dispatchers.IO)`，并在代码里写明「不要改成扫描过滤」的原因。
   另注：`.nomedia` 只对用户可见的 SAF 目录有意义；默认存储根是应用私有目录，系统相册本来就看不到，
   此时该开关是安全的空操作。
