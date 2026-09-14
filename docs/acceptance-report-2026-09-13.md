# lanraragi-reader 三份 2026-09-13 规划文档 · 最终验收报告

日期：2026-09-13
范围：`docs/enhancement-plan-2026-09-13.md`、`docs/ui-ux-enhancement-plan-2026-09-13.md`、`docs/orphan-feature-disposal-2026-09-13.md` 所述改动
验收对象：`<项目根目录>`（Kotlin / Compose）
真机：OnePlus PHK110（Android 16 / SDK 36），服务器 `https://<自建 LANraragi 地址>:8088`（LANraragi 0.9.81，2297 个档案，设有网页密码）
服务器契约权威：LANraragi 0.9.81 源码内的 `tools/openapi.yaml` 与 `lib/LANraragi`

---

## 一、验收结论

| 项目 | 结果 |
|---|---|
| `:app:assembleDebug` | **通过**（APK 24,773,694 字节，含全部新代码） |
| `:app:testDebugUnitTest` | **通过**：56 个测试类 / **276 用例 / 0 失败 / 0 错误 / 0 跳过** |
| `:app:connectedDebugAndroidTest`（真机） | **通过**：**11 用例 / 0 失败**（8 例经 Gradle 跑批，含 Room v8→v9 迁移；另 3 例正则守卫用 `am instrument` 手工执行以避免卸载应用，3/3 通过） |
| Room schema | `app/schemas/.../9.json` 已生成，`eh_*` 三表已不在其中；真机在既有 v8 数据库上完成迁移并正常启动 |
| 真机功能回归 | 见第六节（24 项已验证 / 6 项无法在本机验证） |
| 结论 | **可交付**。发现并修复 **7 个阻断级缺陷**、**19 个高价值缺陷**；报告第七/九节列出的未完成项已全部落地（见「二·补三」「二·补四」「二·补五」「二·补六」），包括按用户裁决实现 TANK_ 路由、能力门控与 FAB 按压动效、「导航页合并进设置」，以及移除「实验室」页并把两个实验开关转正。仅剩**单行本相关行为无法真机验证**（服务器 0 个单行本）与**视觉观感无法自动核验**（本机无视觉模型） |

**最重要的一句话**：本轮真机验收发现阅读器在实际使用中**根本无法翻页**、以及**所有需要鉴权的写接口（进度回传、元数据回写、分类、备份、Minion 排队）在该服务器上全部 401**。这两条都不是本次三份文档的新增改动引入的，而是此前「只写代码、未上真机」积累下来的存量缺陷——正是本次验收要解决的问题。此外用户实测反馈的「保存服务器后首页空列表」经排查定位到一条三段式缺陷链（导航图被重建 → 共享请求被页面级 ViewModel 取消 → 空态无重试入口），已在「二·补」中记录并修复。

---

## 二、阻断级缺陷（已修复并真机验证）

### D-1 阅读器锁死在起始页：任何翻页/跳页都被立刻回滚

- 位置：`ui/screens/ReaderScreen.kt`（`pendingInitialPage` 与会话状态收集器）
- 机理：`readerSession.state` 的收集器每次都把会话页强制对齐到 `pendingInitialPage`：

  ```kotlin
  val resolvedPage = source?.let { pendingInitialPage.coerceIn(0, (it.pageCount - 1).coerceAtLeast(0)) } ?: session.currentPage
  if (source != null && session.currentPage != resolvedPage) readerSession.setPage(resolvedPage)
  ```

  而 `pendingInitialPage` **从来没有清除时机**。`reportPage()` 先把页面写到会话，收集器随即把它拽回起始页，
  因此左滑、点缩略图、音量键三条路径全部无效，UI 上页码永远停在 `1 / 18`。
- 修复：新增 `pendingInitialPageApplied` 一次性闩；只在「期望初始页刚确定」时强制对齐一次，
  且在 `pendingInitialPage` 被重新推导（元数据加载 / tank 进度）处重新武装；
  元数据晚于会话就绪时同步把会话页也对齐，避免用户第一次翻页被吃掉。
- 真机验证：`2/18 → 3/18 → 4/18`（滑动）、`18/18 → 17/18`（上一页按钮）、`续读` 卡片带 `?page=N` 直接落在正确页。

### D-2 鉴权头格式错误：所有受保护写接口 401

- 位置：`data/api/ApiClient.kt`（`ServerInterceptor`）
- 机理：契约要求 `Authorization: Bearer <base64(api_key)>`
  （`openapi.yaml` `api_key` securityScheme 的 description 明写；服务端 `lib/LANraragi/Utils/Login.pm:is_logged_in_api`
  逐字节比较 `"Bearer " . encode_base64($key, "")`），而实现发送的是**原始 API Key**。
  在未设网页密码（`enable_pass == 0`）的服务器上恰好也能通过，所以问题长期隐藏；
  这台服务器设了密码，于是写接口全部 401。
- 影响面（真机实测 401）：进度回传 `PUT /api/archives/{id}/progress/{page}`、
  全库清 New `DELETE /api/database/isnew`；按契约同样受影响的还有元数据回写（D2/D6 的核心链路）、
  分类增删改、备份/恢复、插件与 Minion 排队、重复检测、页缩略图生成。
  只读接口多数在 OpenAPI 里未声明 security，因此「书架能看、什么都改不了」。
- 修复：新增 `authorizationHeader()`，用 `java.util.Base64.getEncoder()`（与 Perl `encode_base64` 字母表/填充一致，
  且不依赖 Android 运行时，单测可直接跑）编码后再拼接；空 Key 保持 `Bearer `。
- 真机验证：`PUT .../progress/1` → **200**，`PUT .../progress/17` → **200**，`DELETE /api/database/isnew` → 200。

### D-3 「跳过服务器」后每次冷启动都被弹回向导

- 位置：`ui/AppRoot.kt` 起始终点判定
- 机理：`val start = if (s.baseUrl.isBlank() || !s.onboardingCompleted) Routes.SETUP else Routes.MAIN`。
  向导第 4 步「跳过，先使用本地书架」只写 `onboardingCompleted`、不写 `baseUrl`，
  于是下次冷启动 `baseUrl` 仍为空 → 又回向导，**「仅本地」路径永远进不去**——正是 UI 规划 2.4 列为待修的缺口。
- 修复：`val start = if (s.onboardingCompleted) Routes.MAIN else Routes.SETUP`。
- 配套：`LanraragiLibraryRemoteGateway` 增加 `serverConfigured` 判据（`AppContainer` 注入
  `ApiClient.config.isConfigured`）。未配置服务器时不再打网络、也不再让图库被「尚未配置服务器地址」的
  错误态占满，本地书架照常可用；**已配置但连不通的服务器仍照常抛错**，保留错误提示与重试。

---

## 二·补 用户复现反馈后追加修复：保存服务器后首页空列表（D-4）

现象（用户实测）：在服务器卡片里填好地址、测试连接成功、保存并进入主界面后，**首页显示「没有找到档案。」且一直不恢复**（冷启动后才正常）。

排查证据（真机 logcat）：图库确实发出了请求，但序列是

```
GET /api/search?start=100 → 200
GET /api/search?start=200 → HTTP FAILED: Canceled   （随后被重试拦截器连续重试，仍 Canceled）
...之后不再有任何 search 请求，页面停在空态且 error=null
```

说明这一轮加载**被中途取消**，且没有任何后续重试。根因是一条链，三个环节都已修复：

1. **导航图被重建（触发点，本次改动引入的隐患）**
   `AppRoot` 用 `val start = if (s.onboardingCompleted) MAIN else SETUP` 作为 `NavHost` 的
   `startDestination`。NavHost 的 graph 以 `startDestination` 为 key，向导收尾写入
   `onboardingCompleted` 会让它从 `SETUP` 变成 `MAIN`，**整张图连同返回栈与 ViewModelStore 一起重建**，
   于是主界面刚起来的加载被销毁路径打断。改为 `remember { ... }` 只在首次组合判定一次；
   完成引导后的跳转仍由向导自己的 `navigate(MAIN){popUpTo(SETUP)}` 负责。

2. **共享的图库请求被页面级 ViewModel 取消（放大器）**
   `LibraryViewModel.onCleared()` 调用 `container.libraryRequests.cancel()`，但
   `libraryRequests` 是 `AppContainer` 级（进程级）单例。页面被回收就取消了共享请求，
   协调器停在 `loading=false / items=[] / error=null`，UI 正好渲染成「没有找到档案」且永不重试。
   改为 `onCleared` 不再取消（旧 job 由下次 `refresh()` 接管）。

3. **空态没有任何重试入口（用户无法自救）**
   `PullToRefreshBox` 只包在「有数据」的分支里，空态是它外面的一个普通 `Column`——
   没有可滚动子节点，下拉刷新根本不会触发。空态动作里补了 **「重新加载」**（调用 `vm.refresh()`）。
   此外 `LibraryRefreshBus.tick.collect` 未 `drop(1)`，StateFlow 会重放历史 tick 并在 init 首次加载之外
   再发一次 `refresh()`，两次互相取消；已按同文件 `CategoryRefreshBus.revision.drop(1)` 的口径统一。

顺带修掉一个同类漏项：**保存/切换/新增服务器后没有刷新图库**（`SettingsScreen.save/switchProfile/addProfile`
与向导 `saveServerConfig` 均只刷新了 `/info`）。现在四处都会 `LibraryRefreshBus.tick.value++`，
换服务器后返回首页即可看到新服务器的档案。

真机验证：冷启动图库正常；空态出现「重新加载」；清除搜索词后列表自动恢复；
保存服务器后图库会重新拉取（48 次分页请求全部 200）。

---

## 二·补二 用户反馈的第二批问题（已修复）

### D-5「添加日期」排序不按真实日期生效

**根因（服务端契约）**：LANraragi 的档案 JSON **根本没有 `dateadded` 字段**。
`lib/LANraragi/Utils/Database.pm` 的 `build_json` 只输出
`arcid / title / filename / tags / summary / isnew / extension / progress / pagecount / lastreadtime / size / toc`；
「添加日期」是以**标签** `date_added:<epoch 秒>` 的形式存放的（同文件 `add_timestamp_tag`，
`set_tags($id, "date_added:$date", 1)`）。

于是链路上出现三处问题：

1. `Archive.dateadded` 解析的字段不存在 → 恒为 0；
2. `MixedLibraryRepository.entryComparator` 在主键并列时退回 `sourceKey`(arcid) 排序 →
   **把服务端已经按 `sortby=date_added` 排好的顺序整个打乱**，表现就是「排序规则完全没生效」；
3. 卡片列表视图因此永远显示「添加日期未知」（`ArchiveCard` 里 `dateadded > 0` 才格式化）。

**修复**：

- `LibraryGatewayAdapters` 新增 `parseDateAddedMillis(tags)`：从标签里取 `date_added:<epoch>`，
  对「已是毫秒」的历史数据做量级兜底，统一返回 epoch 毫秒；远端行由它填充，
  本地行（`LocalArchiveEntity.lastVerifiedAt`）同样按毫秒填充，本地档案不再显示「添加日期未知」。
- `entryComparator` 去掉 `sourceKey` 兜底：并列返回 0，依赖 Kotlin `sortedWith` 的**稳定排序**
  保持服务器返回顺序。这样即使服务器没开 `usedateadded`（没有时间戳标签），
  列表也仍然是服务端 `sortby=date_added` 的正确顺序，而不是退化成按 arcid 排列。
- 单位统一：`LibraryEntry.dateAdded` 用毫秒，`Archive.dateadded` 与 `ArchiveCard` 沿用秒，
  在 `toLegacyArchive()` 与 `LocalScanManager` 两处换算（此前 `LocalScanManager` 把毫秒直接
  塞进秒字段，本地档案会显示成 +57000 年）。
- 新增回归测试 `LibraryDateAddedSortTest`（7 例）：秒/毫秒解析、命名空间大小写与相似前缀、
  非法值与重复值、**按真实时间戳倒序**、以及**时间戳未知时必须保持服务器顺序**。

**真机验证**：列表视图下选择「添加日期 + 倒序」→ 顶部依次 `2026-09-01 01:11 → 01:10 → 2026-08-31 21:04 → 20:58`；
切「升序」→ 回到最早 `2026-03-08 21:16 → 23:29`（升序）。卡片不再显示「添加日期未知」。

### D-6 筛选面板的呈现形式重做

按用户要求把**视图模式移到网格大小上方**，并重排这几组选项的呈现（`LibraryScreen.FilterSheet`）：

| 之前 | 之后 |
|---|---|
| 「排序字段」6 个 Chip，独立一块 | 标题行 **排序** + 右侧 **升序/倒序** 分段控件（方向是二选一，不该长得像筛选项）；字段 Chip 紧随其下 |
| 「排序方向」2 个 Chip，独立一块 | 合并进排序标题行，少一个区块 |
| 「网格大小」7 个松散 Chip | 与「视图模式」交换位置；改为**等宽分段控件 2–8**，标题右侧实时显示「N 列」 |
| 「视图模式」3 个 Chip（在网格大小之后） | **上移到网格大小之前**（先定「怎么显示」，再定「一行几本」）；改为**等宽分段控件**（图标+文字） |
| 列表视图下列数仍可点但无效果 | 列表视图下**置灰**并在标题右侧说明「列表视图不适用」，避免出现「改了没反应」的控件 |

同时新增公共组件 `ui/components/SegmentedControl.kt`（胶囊轨道 + primaryContainer 选中填充 +
160ms 填充色过渡 + `Role.RadioButton`/`selected` 语义 + `enabled` 置灰），
与下载页/元数据中文化页那两份私有实现同一视觉语言——避免继续复制第四份。
（那两份私有实现本次未迁移，留作后续「组件出仓」批次。）

**真机验证**：面板顺序为 排序(+升降序分段) → 字段 Chip → 视图模式分段 → 网格大小分段；
切到「列表」后网格大小显示「列表视图不适用」且不可点，切回「松散网格」恢复可点并显示「2 列」。

---

## 二·补三 第三轮：清理报告「未完成」清单（已完成项）

本节对应本报告第七、九节列出的未完成/未验证项，逐条落地。

### D-7 元数据工作台**一打开就崩溃**（Android 专属正则，最高优先级）

- **现象**：详情页 → 编辑 → 编辑元数据 → **应用直接闪退**。
- **崩栈**：`java.lang.ExceptionInInitializerError` →
  `NativeMetadataProviders.<clinit>(MetadataCandidateProvider.kt:72)` →
  `Caused by: java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 32`。
- **根因**：`NHentaiMetadataProvider.BRACED_FILE_ID = Regex("(?:^|[\\s_-])\\{([1-9][0-9]{0,8})}")`
  末尾的 `}` 闭合的是 `\{`、不是量词，属于**未转义的字面 `}`**。
  宿主 JVM（OpenJDK）接受这种写法（已实测：同样的 pattern 在 JVM 上编译通过），
  但 **Android 的 `java.util.regex` 实现（Harmony/ICU）会直接拒绝**——所以单元测试全绿、真机必崩。
  `MetadataCandidateProvider.LEADING_GALLERY_IDENTIFIER` 有同型写法，一并修复
  （同时把 `]` 也转义，两引擎下语义一致）。
- **影响面**：这不是边缘路径——元数据刮削是本 APP 的定位核心（文档 D2/D6），而这个工作台
  在修复前 **100% 无法打开**。属于「只写代码、未上真机」最有代表性的一例。
- **修复**：两处 literals 转义为 `\\}` / `\\]`，并在代码里写明原因，避免后人再改回去。
- **永久守卫**：新增真机（instrumented）测试
  `androidTest/.../metadata/providers/MetadataProviderRegexInstrumentedTest.kt`——
  在 **Android 运行时**触碰 `NativeMetadataProviders.all` 并跑一次候选匹配，
  把每个正则编译一遍。**真机实测 3/3 通过**。
  （JVM 单测无法覆盖这类问题，只能放在 androidTest。）
- **真机验证**：工作台可正常打开；输入关键词刮削 → 命中
  `E-Hentai · 25% · 标题一致` 与 `nHentai · 25% · 标题一致` 两个候选，并给出 标题/标签 的 diff 预览
  （置信度低于 80% 阈值，符合既定的不自动采用策略）。**未执行任何写回**。

### D-8 封面破缓存从全局改为「按档案」

`CoverChangeBus` 原是一个全局计数器，展示层把它追加到**每一张**封面 URL 上，
于是一次「设为封面」会让全库封面的内存/磁盘缓存键同时失效、整屏重新下载。
现改为 `arcid -> 版本号` 映射（`versions` / `versionOf(arcid)` / `notifyChanged(arcid)`），
只有被替换的那一张封面破缓存；同时删除了 `ui/screens/LibraryBus.kt` 里同名但从未被递增的重复 `CoverChangeBus`。
新增单测 `CoverChangeBusTest`（4 例，含「只影响该档案」「重复变更只递增该档案」）。

### D-9 下载健壮性（文档 C3 收尾）

- **重试上限可配**：新增设置 `downloadMaxRetries`（1–10，默认 5）；`DownloadRetryPolicy` 改为
  可运行时更新（`@Volatile var` + `DownloadCoordinator.setMaxRetries`），由 `AppContainer` 的
  单一权威 flow 推送；设置→数据 新增「下载重试上限：N」滑杆（含中文说明）；
  任务卡失败行显示真实上限（`已重试 N/上限 次`）；导出/导入同步补齐该字段。
- **认证失败自动暂停整队**：401/403 仍让该任务落 FAILED，但**同时暂停整个队列**
  （复用 UI「暂停全部」的同一条代码路径），并发出一条去重的中文提示横幅
  「认证失败，已自动暂停整队」（401/403 两种文案，含受影响任务数），
  避免整队继续重复注定失败的请求。429/5xx/网络/存储失败语义不变。
- 新增单测覆盖到上限后的行为（`retryPolicyStopsAtConfiguredRetryLimit`）。

### D-10 进度回传的有界退避重试

此前失败进度只能等「下次翻页」或「下次启动」；用户若就此停止阅读，进度会长期滞留本地。
现 `OutboxProgressWriter` 在一次 flush 后若**确有任务写失败**（`attempted > succeeded`；
被跳过、注定不会成功的任务不计入），就在应用级作用域排一次静默重试：
30s → 60s → 120s → 240s → 480s → 900s → 900s → 900s，最多 8 次（约 1 小时）后停下；
任何一次成功、或用户侧新动作（翻页/退出/切后台）都会取消等待中的重试并清零计数。
单飞由「一个 Job 内部推进阶梯 + 共用 flush Mutex」保证，不叠加定时器；三处 catch 均先重抛 `CancellationException`。
退避参数可注入，便于测试。

### D-11 玻璃组件 API 补全 + 接线（UI 规划 1.2/1.4）

- `LiquidGlassSearchBar` 补 `readOnly`（只读展示模式，整条胶囊可点）、`capsuleClickable`、
  `clearContentDescription`；库页浮动搜索胶囊改用该组件，行为逐项对齐
  （搜索图标 + 「搜索标题或标签…」占位 + 当前词 + 非空时「清除搜索词」+ 整条胶囊点按 → SearchScreen）。
- `LiquidGlassFab` 补 `onLongClick`（复用同一 `interactionSource`，按压动画不变）与 10 个玻璃常量参数
  （默认值 = 原来的硬编码值，其他调用方不受影响）；详情页阅读 FAB 改用该组件，
  传入与原实现一致的常量（`blur 4.dp` / `lens(10,64)` / 无 vibrancy / 无内阴影 / 无表面着色），
  长按菜单照常工作。
- 结果：四个玻璃组件**全部有真实调用方**，不再有「已创建但无法接线」的死组件。
- 已知取舍（待用户裁决）：FAB 原先的**按压期动态效果**（背景扭曲/模糊与折射随按压变化、±3dp 视差）
  没有搬迁到组件里——接入后按压期间玻璃是静态的。静止状态完全一致。

**真机验证**：库页胶囊图标语义存在、整条胶囊点按进入 SearchScreen；
详情 FAB 显示「继续阅读 · 第 5 页」且长按弹出「从第 5 页开始 / 从头阅读」。

### D-12 `hideInGallery` 语义定型 + 主线程 SAF IO 修复

用户确认该开关的真实意图是「**让系统相册不扫描下载目录里的图片，但 APP 扫描本地库不跳过**」——
即**维持**现有「写/删 `.nomedia`」语义，**不做**扫描过滤（与本报告第七节及 orphan 文档原决策相反，已在
orphan 文档第五节补记勘误）。本轮只修同处的真实缺陷：`updateNoMediaFile` 原先在**主线程**做
SAF（ContentProvider）往返，现已包进 `withContext(Dispatchers.IO)`，并在代码里写明
「不要改成扫描过滤」的原因。`.nomedia` 只对用户可见的 SAF 根有意义，默认私有目录下是安全空操作。

### D-13 文档勘误

- `docs/orphan-feature-disposal-2026-09-13.md`：修正第 9 项的错误前提（真正零引用的是
  `ui/screens/CategoryManager.kt`，`CategoryUi.kt` 是活代码），并新增「第五节 验收勘误」记录
  第 9/12d 两项的更正与理由。
- `docs/ui-ux-enhancement-plan-2026-09-13.md:355`：把「删除死代码」清单里的错误文件名与
  `hideInGallery` 的口径一并更正。

### 本轮新解除的「真机无法验证」项

| 原状态 | 本轮验证方式 | 结论 |
|---|---|---|
| 平板 rail / master-detail | `adb shell wm size 3000x1400`（使宽度 ≥840dp 断点） | **master-detail 生效**：图库右侧出现「选择一个档案查看详情」占位，点卡片后原地显示详情面板 + 「打开详情」。**底栏→侧栏 rail 未实现**（属第四批第 22 项） |
| 横屏双页/刘海 | `user_rotation=1` 强制横屏 | 横屏单页模式自动双页：翻页 `1/41 → 3/41 → 5/41`（每次 +2），页码与计数正常 |
| 元数据工作台全链路 | 真机打开 + 刮削（只读，不写回） | 打开正常、候选与 diff 预览正常（见 D-7） |
| 视觉观感（无视觉模型） | — | 仍无法自动核验；但组件接线的**结构与语义**已用 uiautomator 逐项比对 |

设备侧副作用：`wm size` / `user_rotation` 已全部复位（`wm size` 恢复 1240×2772、`accelerometer_rotation=1`）；
为验证正则守卫新增的 instrumented 测试用 `am instrument` 手工执行（**未**走 Gradle 的 `connectedAndroidTest`），
因此**没有再次卸载应用、用户数据保持完好**（已验证 `settings.preferences_pb` 仍在）。

---

## 二·补四 第四轮：按用户裁决落地（TANK_ / 能力门控 / FAB 动效）

用户在读完服务端源码解读后裁定：**实现 TANK_ 路由**、**能力门控只接「对 UI 不可见」的两项**、
**把按压动效搬进组件**。本轮据此落地。

### D-14 A3 能力门控（按用户选定范围：只做不可见的两项）

- **进度回传门控**：`OutboxProgressWriter` 新增 `progressSupported` 判据，为 false 时 `record()` 不入队、
  `flush()` 不发请求（返回 `attempted == succeeded == 0`，因此不排重试），**已入队任务不丢弃**——
  服务器将来开放进度后仍可补传。`AppContainer` 注入该判据；阅读器的整卷进度
  （`updateTankoubonProgress` 防抖 + 退出兜底）也走同一判据。
- **⚠️ 判据本身在真机上被修正过（值得记录）**：最初按「`server_tracks_progress == false` 就不回传」实现，
  结果真机回归发现**进度回传被整体关掉了**（服务端本机 200 的正常写入消失了）。
  回查服务端源码后确认判定条件不是单看该字段——两个进度端点写的都是
  （`Controller/Api/Archive.pm:450`、`Controller/Api/Tankoubon.pm:245`）：
  ```perl
  if ( enable_localprogress && !enable_authprogress ) { 拒绝 }
  ```
  而 `/info` 的映射是 `server_tracks_progress = !enable_localprogress`、
  `authenticated_progress = enable_authprogress`，因此
  **接受 ⟺ `server_tracks_progress || authenticated_progress`**。
  本服务器恰好是「开了本地进度 + 要求进度鉴权」（`false || true`）→ 服务端照常接受，
  而旧判据把它误关。已按上式修正，并补 4 组组合的单测；
  真机复测：会话内 `PUT /progress/5` → 200、退出 `PUT /progress/6` → 200，恢复正常。
  ——这正是「真机验收」不可替代的又一例证。
- **同一门控覆盖整卷进度**：与单档案进度同判据（两端点条件完全一致）。
- **`archives_per_page` 结论：不需要接线。** 核对实现后确认 APP 的 `/api/search` 分页用的是
  **行偏移**（`start = offset`，且 `offset += received.size`，用服务端实际返回条数推进），
  从不假设固定页大小；`LibraryRequestCoordinator` 的 `pageSize` 只作用于本地切页，不参与远端请求。
  因此服务端把 `archives_per_page` 设成任何值都不会让 APP 分页错位——这一项无落点，如实记录而不硬造。
- **新增单测** `ServerCapabilitiesTest`（6 例）：乐观默认、进度接受的四种字段组合、
  `authenticated_progress` 语义、版本门控（0.8.9 / 0.9.81 / info 为空），
  以及「不支持时不入队也不发请求」「支持时正常入队并发送」两条写入口径。

### D-15 详情页 FAB 的按压动效已搬进 `LiquidGlassFab`

上一轮接入组件时把「静止态完全一致、但按压期玻璃是静态的」作为已知取舍上报，用户选择**补齐动效**。
现已把 `LiquidGlassVisual` 的按压期行为逐项移植进组件，并由**同一个 `interactionSource`** 驱动
（不新增第二个交互源，`combinedClickable`/`onLongClick` 原样保留）：

| 旧实现（`LiquidGlassVisual`） | 组件内对应 |
|---|---|
| 按指针位置 ±3dp 视差 | `translationX/Y`，乘 `dynamicsProgress` |
| `blur × (1 − 0.25p)` | 同式，p 改为 `dynamicsProgress` |
| `lens 折射量 × (1 + 0.5p)` | 同式（静止态 `1f * x` 与旧值逐位一致） |
| `onDrawBackdrop` 背景扭曲 `translate(−nx·18, −ny·14)` + `scale(1−0.13p, 1+0.075p)` 绕按点 | 同式移植，含 `p <= 0f` 时直接 `drawBackdrop()` 的静止分支 |
| 按压挤压 `0.965/1.018` 弹性 + alpha | 原有实现，未改（仍由原 `pressProgress` 驱动） |

新增 `pressDynamics: Boolean = true` 开关（默认开＝完整实现）；所有既有参数与默认值保持不变。
`onDrawSurface = {}` **没有**做成随开关变化——那会在静止态抹掉其他调用方的默认 2% 白色表面；
本调用点用 `surfaceTint = null` + 不传 `activeColor` 已复现旧像素。
顺带核实：旧的 `blurRadius > 0` / `lensHeight > 0` 守卫是空操作（vendored backdrop 2.0.1 的
`blur()`/`lens()` 自身早退），`lensHeight = 32.dp` 只是开关、量值被丢弃，
组件 `refractionHeight = 10.dp` 就是旧的有效值，故无需新增无意义参数。

### D-16 TANK_（单行本）路由

用户裁定实现后落地，让 `/api/search` 默认返回的 `TANK_*` 条目成为一等公民：

- **建模**：`Archive` 追加 `archive_count`（服务端 `build_tank_json` 的成员档案数，即「N 卷」；
  注意 tank 的 `pagecount` 是成员页数之和，不是卷数）；`LibraryEntry` 追加 `volumeCount`（末位默认参数，兼容既有构造）。
- **身份**：网关层用既有的 `ArchiveIdentity.fromArchiveId` 把 `TANK_*` 映射为
  `ArchiveIdentity.Tankoubon`（而非 `Remote`），并新增 `isTankArchiveId` /
  `archiveActionTargets`（批量操作前剔除 `local_` 与 `TANK_`，并统计跳过数）。
- **导航**：库页网格/紧凑网格/列表/平板主从面板统一走 `openLibraryEntry` → 单行本进
  `Routes.tankReader`，普通档案照旧；卡片显示 **「单行本 · N 卷」** 角标（沿用既有角标语言，
  `volumeCount == 0` 时完全不渲染）。
- **批量操作**：标已读/标记未读/删除/加入分类/缓存/刮削/收藏 六条服务器变更路径全部剔除单行本，
  并通过既有 Snackbar 文案报告「已跳过 N 个单行本（不支持该操作）」；普通档案的措辞与计数逐字不变。
- **封面（子代理判断有误，已纠正）**：实现过程中子代理认为「单行本没有服务器缩略图」并跳过了预热；
  核对后确认 **`/api/tankoubons/{id}/thumbnail` 是存在的正式端点**（`openapi.yaml:3731`、
  `Tankoubon.pm:212 serve_tankoubon_thumbnail`，导航页的「单行本」卷墙本来就在用它）。
  已改为：`RemoteThumbnailImage` 对 `TANK_*` 走该端点且**不进档案缩略图仓库**
  （后者会请求 `/api/archives/TANK_xxx/thumbnail` 并让服务端排一个注定失败的缩略图任务）、
  `ArchiveCard` 的兜底封面 URL 同步、库页预取也照常预热而不是跳过。
- **卡片信息对齐**：tank 分支的 legacy 映射补齐 `progress / isnew / dateadded`，
  使单行本卡片与普通档案一样有进度条、「新」角标与日期行。
- **其余能拿到 TANK_ id 的入口一并兜住**（子代理标注为越界、由主代理补）：随机抽屉
  （`searchRandom` 结果含 tank）、深链/分享（`AppRoot`）、分类浏览、历史页——
  四处都在 `isTankArchiveId` 判定后改走单行本阅读器。
- **新增单测** `LibraryTankEntryTest`（6 例）：身份映射（含大小写不敏感与相似前缀否定）、
  批量目标剔除与跳过计数、`archive_count` 解析与缺省 0。

**无法真机验证的声明**：当前服务器 `GET /api/tankoubons` 返回空列表（**0 个单行本**），
因此单行本卡片的渲染、进入单行本阅读器、跳过提示与 tank 封面端点**均未在真机上跑过**，
只由上述单测与代码审阅保证。等你建了单行本我可以立刻补真机验收。

### 第四轮新解除的「真机无法验证」项

| 原状态 | 验证方式 | 结论 |
|---|---|---|
| 通知权限 / 电池优化白名单 | `dumpsys package` + `deviceidle whitelist` 对照权限设置页 | 「通知权限：已允许」与 `POST_NOTIFICATIONS: granted=true` 一致；「电池优化白名单：未加入白名单，点按前往系统设置」与设备不在 whitelist 一致。**系统设置跳转本身未点按** |
| 仅本地书架降级 | 新增单测 `LibraryRemoteGatewayGateTest`（用动态代理实现 `LanraragiApi`，任何方法调用即计数并抛错） | 未配置服务器 → **0 次 API 调用且返回空列表**；已配置 → 确实发起调用。从「静态看没问题」升级为代码级闭环 |
| 详情 FAB 接入组件后是否可用 | 真机点按 + 长按 | 文案「继续阅读 · 第 5 页」、点按进阅读器、长按弹「从第 5 页开始 / 从头阅读」均正常。**按压动效手感无法自动核验** |
| 库页搜索胶囊接入组件后是否可用 | 真机点按 | 图标语义「搜索」、占位「搜索标题或标签…」、**整条胶囊点按进入 SearchScreen** 均正常 |

### 第四轮最终验证状态（末次 17:18）

- `:app:assembleDebug`、`:app:testDebugUnitTest`（**56 类 / 276 用例 / 0 失败**）、
  `:app:assembleDebugAndroidTest` 全部通过。
- 真机回归（本轮改动后重跑）：冷启动图库正常；元数据工作台可打开（耗时最低的崩栈守卫）；
  详情 FAB 点按与长按正常；阅读器翻页 `5/41 → 6/41` 正常；
  **会话内 `PUT /progress/5` → 200、退出 `PUT /progress/6` → 200**（能力判据修正后复测通过）。
- instrumented 正则守卫 3/3 通过（以 `am instrument` 手工执行，未卸载应用、数据完好）。

---

## 二·补五 第五轮：导航页合并进设置（D-17）

用户指令：「把导航页合并进设置里，去除冗余的代码和UI/UX，不确定的向我汇报」，并按请示选定三项方案：
①底栏第 3 键改为**设置**（不再有独立导航页）；②导航页的统计/历史/分类/单行本收进设置新增的**「浏览」组**，
「用服务器下载链接」并入**「工具」组**；③删除导航页顶部的**服务器品牌卡**。

### 落地内容

- **底栏第 3 键**：`MainScreen` 的 `mainTabs[2]` 标签由「导航」改为「设置」、图标 `Menu` → `Settings`；
  该 tab 直接渲染 `SettingsScreen`（不再走 `Routes.SETTINGS` 路由），`onBack` 传 `null`，
  于是顶级只有居中标题、进入子分区后才出现返回箭头。
- **设置首页新增「浏览」组**：统计 / 历史 / 分类 / 单行本（单行本入口此轮仍受 `FeatureFlags.TANKOUBONS` 门控，
  下一轮「二·补六」随实验开关一并转正为常驻）。
  导航页其余条目均已在本页去重后不再重复出现：下载＝底栏第 2 键、设置＝本页、诊断＝「调试」分区。
- **「用服务器下载链接」迁入「工具」组**：行 + URL 对话框逐行搬移（`container.repository.downloadFromUrl`），
  仅新增一条副标题「直接把直链 zip 交给服务器下载」；逻辑未改。
- **删除**：`ui/screens/NavigationScreen.kt`（227 行）、`Routes.NAVIGATION`、`Routes.SETTINGS`
  及其两个 `composable` 块、`MainTabBus.NAVIGATION_TAB` 与 `MainTabBus.requestNavigationTab()`。
- **顺手清理零引用**：`LibraryScreen` 的 `onOpenDrawer` 参数（导航页退役后全仓无调用点，
  库页顶栏也不再有抽屉入口）。
- **返回键分层（新增，必须做）**：设置不再是可出栈路由，子分区里按返回键原本会被 `MainScreen`
  的顶层 `BackHandler` 直接切回图库。现补 `BackHandler(enabled = section != SettingsSection.MAIN)`：
  子分区 → 设置首页，再按一次才由主壳切回图库。

### 真机验证（末次 17:37）

| 检查项 | 结果 |
|---|---|
| 底栏四键语义 | `首页 / 下载 / 设置 / 续读·随机`（读屏语义齐全） |
| 设置 tab 顶级 | 居中标题「设置」、无返回箭头；列表＝连接/阅读/外观/工具/数据/安全/权限/调试/实验室/关于 + 浏览 + 引导（当时的形态；「实验室」已在「二·补六」移除） |
| 「浏览」组 | 统计 / 历史 / 分类 三项在列（单行本按 flag 关闭隐藏，符合预期） |
| 三入口导航 | 统计→「本地使用统计」、历史→历史列表、分类→分类列表；**返回均回到设置 tab 且保留滚动位置** |
| 返回键分层 | 连接子分区按返回 → **设置首页**（未再直跳图库）；设置首页按返回 → 图库 |
| 「用服务器下载链接」 | 工具组行可见；点按弹出同名对话框（字段「下载链接（直链 zip）」+ 取消/确定），取消正常关闭 |
| 元数据工作台 | 从工具组可打开（第 4 步向导首屏正常，无 `PatternSyntaxException`） |
| 回归：图库 | 2297 本正常加载（第 3 轮 D-4 修复未回退） |
| 回归：阅读器与进度 | 翻页 5→6；横屏双页步进下左翻 → 4；`PUT /progress/5,6,4` 均 **200**；重开详情显示「已读到第 4 页」，与最后阅读页一致 |
| 构建 | `:app:assembleDebug` 通过；`:app:testDebugUnitTest` **56 类 / 276 用例 / 0 失败**（本轮无新增/删除用例） |
| 崩溃 | `logcat -b crash` 全程无 `FATAL`/`Exception` |

**需用户裁量的取舍（本轮一并汇报）**

1. 品牌卡删除后，服务器名称/地址在全 App 只剩「设置 → 连接 → 服务器」卡片一处（一屏内可达，信息未丢）。
2. 「浏览」组放在 10 个设置分区**之后**、「引导」组之前：保持了设置页既有肌肉记忆（配置分区仍在最上）。
   若你更希望把浏览类入口当一级导航，可整组上移到列表最前。
3. `Routes.OFFLINE` 路由同样已无任何 `navigate()` 调用方（无外链 scheme，无法从外部触达），
   但它是更早一轮按「保留兼容」刻意留下的，本轮未动；如需一并清理请给一句确认。
4. ~~单行本入口在 `a11_tankoubons` 关闭时不出现在设置里~~：**已作废**——实验开关随「实验室」页整体移除，
   单行本入口常驻（见「二·补六」）。

---

## 二·补六 第六轮：移除「实验室」页，实验开关全部转正（D-18）

用户指令：「去除实验室页面，里面的选项都默认开启」。

### 现状与做法

「实验室」页只有两个实验开关，且默认关闭：`a5_thumbnails`（缩略图体系）、`a11_tankoubons`（单行本/卷）。
两者的实际消费点共 4 处：

| 消费点 | 开关关闭时的行为 |
|---|---|
| `RemoteThumbnailImage` 的封面渲染路径 | 走遗留直连 URL（允许服务端回退图） |
| 库页封面预取（`enqueueCoverPrefetch`） | 走遗留直连 URL 预取 |
| 详情页 ⋮「加入卷」菜单项 | 整项隐藏 |
| 设置页「浏览 → 单行本」入口 | 整行隐藏 |

**做法：整条链路删除，而不是把开关默认值翻成 true。** 若只翻默认值，设备上已存的 `false`（或将来任何写入）
仍然能压制功能，且留下一个没有任何 UI 可以修改的存储项。删除清单：

- 文件：`data/FeatureFlags.kt`
- 存储：`Settings.featureFlags` 字段、DataStore `feature_flags` 键、`SettingsRepository.setFeatureFlag()`
  （`featureFlags` 从未进入导出/导入的 `nonSensitiveConfig()`，故配置迁移不受影响）
- UI：`SettingsSection.LABS` 枚举项与列表行、`LabsSection` 组合函数、`SettingsViewModel.setFeatureFlag()`、
  `UiState.featureFlags`、`AppContainer.thumbnailEnabled` 流
- 门控转常驻：缩略图仓库成为**唯一**封面路径；「加入卷」与「单行本」入口常驻

### 真机验证（末次 17:52）

| 检查项 | 结果 |
|---|---|
| 设置列表 | 连接/阅读/外观/工具/数据/安全/权限/调试/关于 + 浏览 + 引导（**已无「实验室」**） |
| 「浏览」组 | 统计 / 历史 / 分类 / **单行本**（原先被开关隐藏，现常驻） |
| 单行本页 | `GET /api/tankoubons?page=0` → 200（空库），空态提示「暂无单行本 / 点右上「+」新建，或在档案详情页点「加入卷」」 |
| 详情页 ⋮ 菜单 | 编辑目录 / 编辑元数据 / 运行服务器插件 / **加入卷** / 标记为未读 / 删除 |
| 「加入卷」表 | 空态指向「设置页「浏览 → 单行本」点右上「+」新建」（本轮修正的死循环文案） |
| **缩略图仓库首次真机跑通** | 冷启动后 `GET /api/archives/{id}/thumbnail?no_fallback=true` **全部 200**（78–121 KB 真实图片）。此前开关关闭，该仓库路径从未在真机上启用过 |
| 构建 / 单测 | `:app:assembleDebug` 通过；**56 类 / 276 用例 / 0 失败**（本轮无用例增减） |
| 崩溃 | `logcat -b crash` 无 `FATAL` |

### 顺带修正

- 单行本浏览页空态从干巴巴的「暂无单行本」改为「暂无单行本 / 点右上「+」新建，或在档案详情页点「加入卷」」，
  与详情页提示互为闭环（此前详情页写的是「请在导航页『单行本』中创建」，而导航页已被删除）。

### 需要知悉的取舍

1. **缩略图两条路径的语义差别**：仓库路径带 `no_fallback=true`，只接受**真实**缩略图，未就绪时显示客户端占位图；
   遗留路径允许服务端回退图并被缓存。本服务器缩略图齐全（实测全 200），转正无副作用；若换成关闭缩略图生成的
   服务器，仓库路径会走 202 排队 → 轮询 → 超时占位图（不再把服务端兜底图当真封面缓存），这是设计意图。
2. `RemoteThumbnailImage(enabled: Boolean? = null)` 参数保留（默认 true），作为将来单点关闭的钩子；当前无调用方使用。
3. 服务器仍是 **0 个单行本**，因此「单行本」入口现在是常驻的**空页面**（含新建引导），
   真实卷墙/卷详情/卷阅读器的真机验收仍需等有数据。

---

## 二·补七 第七轮：源码包「无法 sync / build」的排查（D-19）

用户反馈：**之前打包的源码，朋友解压后一直无法 sync、build。**

### 排查结论：包本身没问题，**包里的构建说明是错的**

对旧包 `lanraragi-reader-source.zip` 做了逐项核对：

| 检查 | 结果 |
|---|---|
| 文件完整性 | 254 个 git 跟踪文件**一个不缺**（`git ls-files` 与 zip 条目差集为空），另含 17 个未跟踪但需要带的文件 |
| 构建输入 | `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts` / `gradle/libs.versions.toml` / wrapper（jar+properties）/ `AndroidManifest.xml` / `app/schemas` **全部在包内** |
| 资源引用 | manifest 引用的 `@mipmap/ic_launcher(_round)`、`@string/app_name`、`@xml/network_security_config`、`@style/Theme.LanraragiReader` 在包内**均有对应文件** |
| 机器相关信息 | **无泄漏**：不含 `local.properties`、不含 `build/`、`.gradle/`、`.idea/`、`.kotlin/`、APK、构建日志；`gradle.properties` 与当前工作区**逐字节一致**（无 `org.gradle.java.home` 之类硬编码路径） |
| 文本编码 | 逐条按 UTF-8 解码核对，中文注释**未损坏**（首轮比对出现的乱码是 Windows PowerShell 5.1 用 GBK 读 UTF-8 文件造成的假象） |
| **冷缓存构建** | 解压到干净目录 + **全新空 `GRADLE_USER_HOME`**，只提供 JDK 21 与 `ANDROID_HOME`：**BUILD SUCCESSFUL in 4m30s**，下载 1.19 GB 依赖（阿里云镜像 + 腾讯云 Gradle 9.6.0 均可达，`io.github.kyant0:backdrop:2.0.1` / `shapes:1.2.0` 从 Maven Central 正常解析） |

**根因：`README.md` 的「构建」章节停留在几个月前的老工具链，而且其中一条建议会直接把项目改坏。**

| README 原文（错误） | 项目实况 |
|---|---|
| 「环境要求：JDK 17、Android SDK（**compileSdk 35**）」 | `compileSdk = 37`（Android 17 预览平台，本机 SDK 目录名 `android-37.0`） |
| 「用 **Android Studio（Koala 及以上）** 打开」 | 需要 **canary/nightly** 渠道 Studio——AGP **9.4.0**，stable 版 Studio 同步直接报版本不兼容 |
| 「会自动下载 **Gradle 8.9** 与依赖」 | wrapper 是 **Gradle 9.6.0**（腾讯云镜像） |
| 「命令行方式：先 `gradle wrapper --gradle-version 8.9` 生成 wrapper，再 `./gradlew …`」 | **这条会把 wrapper 降级成 Gradle 8.9，而 AGP 9.4 无法运行在 Gradle 8 上 → 之后怎么 sync/build 都失败**。朋友极可能是照此操作后陷入死循环 |
| 目录树写 `lanraragi-reader/`，并列出 `OfflineCacheManager`/`screens` 旧结构 | 实际根目录是 `lanraragi-reader-merged/`，包内已是 `data/catalog · data/assets · data/history · ui/settings · ui/tools · domain` 等新结构 |

### 修复

1. **重写 `README.md`「构建」章节**：新增「组件 / 本项目要求 / 说明」表（Gradle 9.6.0、AGP 9.4.0、Kotlin 2.4.10、KSP 2.3.10、JDK 17+（推荐 21）、compileSdk/targetSdk 37）、
   明确 **禁止降级 wrapper** 的警告、Studio 与纯命令行两条路径、`local.properties` 写法，以及**构建报错对照表**
   （`Failed to find target with hash string 'android-37'`、`SDK location not found`、AGP 版本不兼容、
   `Could not find io.github.kyant0:backdrop`、中文用户名导致 worker `ClassNotFoundException`、镜像不可达时如何换源）。
2. **顺带修正 README 里两处与代码不符的接口描述**：进度回传是 **`PUT`**（原写 `POST`）；
   鉴权头是 **`Bearer <base64(API Key)>`**（原写明文 key）。
3. **修正目录树**为真实结构。
4. **`.gitignore` 补两条**（`/build-*.log`、`.verify/`），避免下次打包把本地构建日志与验收临时目录一起塞进包里。
5. **重新打包** `D:\program\lanraragi-reader-source-20260913.zip`（**269 个条目 / 737 KB**）：
   打包规则改为「`git ls-files -co --exclude-standard`（即**遵守 .gitignore** 的工作区实际文件）」，
   并显式排除 `local.properties`、`app/release/`、`*.log`、`.verify/`、`**/build/**`；
   校验「包内不含 apk / local.properties / build / .verify / .log」= **0 命中**。

### 新包的真机/冷机验证

把新包解压到干净目录，**只给 JDK 21 + `ANDROID_HOME`，GRADLE_USER_HOME 指向纯 ASCII 路径，不加任何 `-Dfile.encoding` 特调**：

- `:app:assembleDebug` **成功**，产物 `app/build/outputs/apk/debug/app-debug.apk` 存在
- `:app:testDebugUnitTest` **56 类 / 276 用例 / 0 失败**（顺带证明：把 Gradle 用户目录放到 ASCII 路径即可绕开中文用户名下的 worker argfile 编码陷阱，无需改 `gradle.properties`）

### 额外交付：可直接安装的 APK

考虑到朋友的首次构建仍可能卡在 Studio 渠道/预览 SDK 上，另构建并签名了一份 release APK：
`D:\program\lanraragi-reader-release-20260913.apk`（16.1 MB，`app-release.apk` 的副本，
用 **Android debug 证书**签名——`apksigner verify` 确认 `CN=Android Debug`，可直接侧载安装）。

### 给朋友的「最短路径」

1. 只想要能跑：直接装上面那个 APK。
2. 想自己编译：装 **JDK 21** + **Android 17 (API 37) 平台**，命令行 `gradlew.bat :app:assembleDebug`（不需要 Studio 版本匹配）；
   Studio 同步则必须用 canary/nightly 渠道。
3. 装不上 API 37：把 `app/build.gradle.kts` 的 `compileSdk`/`targetSdk` 改成 36，并删掉 `gradle.properties` 里的
   `android.suppressUnsupportedCompileSdk=37`（全仓代码未使用任何 API 37 专有符号，已在第七轮核验）。

---

## 二·补八 第八轮：开屏封面 + 代码完整性 + 依赖整理（D-20）

用户指令：「在设置里添加一个允许从本地图库选择一张图片做开屏封面的选项，显示时间你自己定；
完成后检查项目代码完整性，修复 bug，但不进行真机验证；最后处理所有依赖项冲突、更新 .gitignore 并打包源码」。

### 1. 新功能：开屏封面

**入口**：设置 → 外观 → 「开屏封面」（选择图片 / 更换图片 / 移除，带 54×96 实时预览）。

**展示时长定为 2.0 秒**（`SplashCoverStore.DISPLAY_DURATION_MS`）：短于 1 秒基本看不清画面、等于白设；
长于 3 秒开始明显挡手。2 秒足够看清且随时可点按跳过。

**关键设计取舍**

| 决策 | 原因 |
|---|---|
| 选图后**拷贝到私有目录** `filesDir/splash/cover.jpg`，而不是记住图库 URI | 系统照片选择器（`PickVisualMedia`）返回的 URI 只在当次授权内可读、**无法** `takePersistableUriPermission`；只记 URI 的话第二次冷启动就读不到图。拷贝后既不需要任何存储权限，也不怕用户在图库里删掉原图 |
| API 28+ 用 `ImageDecoder` 按 EXIF 摆正并缩到最长边 1600px 再落 JPEG（质量 92） | 相册原图动辄 4–10 MB / 4000px 级，开屏只需铺满屏幕；缩后通常 200–600 KB，冷启动解码更快，也不在私有目录压一张大图 |
| API 26/27 原样落盘，方向交给展示端 Coil 处理 | `BitmapFactory` 不认 EXIF，重编码反而会把照片转错方向；Coil 的解码器会按 EXIF 摆正 |
| **先写临时文件再改名**（`cover.jpg.tmp` → `cover.jpg`） | 解码失败/空间不足时不会破坏已有封面，用户仍看到上一张 |
| Coil 缓存键带版本号（`splash-cover-<lastModified>`） | 文件名固定不变，不换键的话「更换封面」后会继续命中内存缓存里的旧图 |
| 遮罩放在 `MainActivity` 的 `setContent` 根部、`when` 之上 | 同时盖住「设置加载中的 logo 占位」与「身份验证占位页」，避免先闪一下 logo 再出现封面 |
| 用 `remember` 而非 `rememberSaveable` | Activity 已声明 `orientation/screenSize` 等 configChanges，旋转不重建 Activity，因此不会重放；从后台回前台同样不重放 |

**新增文件**：`data/SplashCoverStore.kt`、`ui/components/SplashCoverOverlay.kt`、
`app/src/test/.../SplashCoverScaleTest.kt`（7 例）。
**改动文件**：`di/AppContainer.kt`（+`splashCoverStore`）、`MainActivity.kt`（根部遮罩）、
`ui/screens/SettingsScreen.kt`（UiState 字段、VM 两个方法、外观分区新行 + 移除确认对话框、`InterfaceSection` 增加 `container` 参数）。

**未做真机验证**（按用户要求）：图片选择、落盘、2 秒淡出与点按跳过均为静态实现 + 单测覆盖纯逻辑，
真机行为待用户自行验证。

### 2. 代码完整性检查与修复（不跑真机）

对**全部三个 source set**（main / test / androidTest）做了 `--rerun-tasks` 全量重编译，逐条处理告警：

| 位置 | 告警 | 处理 |
|---|---|---|
| `EHentaiMetadataProvider` / `NHentaiMetadataProvider` / `TagKnowledgeTransaction` | `data class` 的 primary constructor 是 internal，却生成了**公开的 `copy()`**，等于开了一条绕过构造校验的旁路。**Kotlin 2.4 告警、2.5 起直接编译报错** | 三处改为普通 `class`（全仓无 `.copy()`/相等性使用，行为等价），并写明原因 |
| `WizardScreen.saveStorageRoot` | `Condition is always 'true'`（`keepScan && uri != null` 中的非空判断恒真） | 改成 `uri == null` 提前返回的显式分支，语义不变、可读性更好 |
| `ServerMetadataPluginCoordinator.parsePluginJobDetail` | 2 处多余的安全调用 | 改为 `?: return` 的早退链，形状不匹配时仍回落到共享解析器 |
| `ReaderScreen`（离线缓存标题/标签/TOC） | 4 处多余的安全调用 | 去掉 `cached?.`，用已判空的 `cached` |
| `SettingsScreen` | `diskCache` 属 Coil 实验 API 未 opt-in | 加 `@OptIn(ExperimentalCoilApi::class)` |
| `SettingsScreen` / `WritebackScreen` | 3 处多余的非空断言 `!!` | 改为局部 val / 智能转换 |
| `MetadataRepositoryTest` / `MetadataSnapshotCodecTest` / `NativeMetadataProvidersTest` | 8 处多余的非空断言与安全调用 | 同上 |

**结果：三个 source set 编译告警数 14 → 0。**

### 3. 依赖整理

`debugRuntimeClasspath` 共 405 处 `x -> y` 版本抬升，其中**我们直接声明的模块有 6 处「声明值与实际解析值不一致」**——
声明与落地脱节会让后续排查依赖问题时误判。已把 version catalog 对齐到实际解析结果：

| 模块 | 原声明 | 实际解析 | 现在声明 |
|---|---|---|---|
| `androidx.core:core-ktx` | 1.13.1 | **1.16.0** | 1.16.0 |
| `androidx.lifecycle:*`（含 runtime/viewmodel-compose） | 2.8.6 | **2.9.4** | 2.9.4 |
| `kotlinx-coroutines-android` / `-test` | 1.8.1 | **1.9.0** | 1.9.0 |
| `kotlinx-serialization-json` | 1.7.2 | **1.7.3** | 1.7.3 |
| `io.github.kyant0:shapes`（写死在 build.gradle.kts） | 1.2.0 | **1.2.1** | 1.2.1（并移入 catalog） |
| `androidx.compose.foundation:foundation`（写死、无版本） | — | BOM 1.12.0 | 移入 catalog，交给 BOM |

用 `dependencyInsight` 逐个复核：5 个代表模块的 selected 版本与声明值**完全一致**；其余抬升均来自传递依赖
（属于正常收敛，不需要干预）。`checkDebugDuplicateClasses` 随 `assembleDebug` 通过，无重复类。
`debugAndroidTestRuntimeClasspath` 亦核对：test core 1.6.1 / runner 1.6.2 / ext-junit 1.2.1 / junit 4.13.2 与声明一致。

### 4. `.gitignore` 与打包

- 新增忽略：`/build-*.log`、`/deps-*.txt`、`/dependency-*.txt`、`.verify/`、`/dist/`、`/*.zip`、
  `.idea/caches/`、`.idea/libraries/`、`*.apk.idsig`、`.kotlin/**`
- 顺手清掉仓库根目录 35 个历史 `build-*.log`（本地构建日志，未跟踪、不进包）
- 重新打包 `D:\program\lanraragi-reader-source-20260913.zip`：规则为
  `git ls-files -co --exclude-standard`（尊重 .gitignore 的工作区实际文件）再显式排除
  `local.properties` / `app/release/` / `*.log` / `.verify/` / `**/build/**`，
  校验「apk / local.properties / build / .verify / .log」= **0 命中**

### 本轮验证状态（无真机）

- `:app:assembleDebug`、`:app:testDebugUnitTest`（**57 类 / 283 用例 / 0 失败**，较上一轮 +1 类 +7 例）、
  `:app:assembleDebugAndroidTest` 全部通过
- main / test / androidTest 三个 source set 编译告警 **0**
- 依赖：直接声明模块的 declared == resolved；无重复类

---

## 二·补九 第九轮：AGP 版本与 Android Studio 支持上限对齐（D-21）

**现象**：用 Android Studio 打开项目、同步即被拦截：

```
The project is using an incompatible version (AGP 9.4.0) of the Android Gradle plugin.
Latest supported version is AGP 9.3.0
```

**原因**：Studio 的 AGP 兼容性检查发生在 Gradle 之前——项目声明的 AGP 高于当前 Studio 渠道的实现上限时，
同步直接失败，命令行构建却不受影响（这也解释了为什么本机能编、用 IDE 的人不能编）。
与第七轮朋友那份「解压后无法 sync、build」是同一类问题的另一种表现。

**处理**：`gradle/libs.versions.toml` 的 `agp` 由 `9.4.0` 改为 **`9.3.0`**（只改这一行），
并在该行上方写明「必须落在 Android Studio 支持上限内；将来升级 Studio 后可改回 9.4.0」。
README 同步更新：工具链表 AGP 列改 9.3.0、Android Studio 说明去掉 canary/nightly 限定、
FAQ 中「AGP 不兼容」一条改为可执行指引（照报错里的版本号改 catalog 那一行，其余依赖无需调整）。

**实测（AGP 9.3.0 + Gradle 9.6.0 + Kotlin 2.4.10 + KSP 2.3.10 + Room 2.8.4 + compileSdk/targetSdk 37）**：

- `:app:assembleDebug` 通过（APK 24,822,850 字节）
- `:app:testDebugUnitTest` 通过：**57 类 / 283 用例 / 0 失败**
- `:app:assembleDebugAndroidTest` 通过（androidTest APK 正常产出）
- 编译告警 **0**

**遗留提示**：若同步随后报 `Failed to find target with hash string 'android-37'`，那是另一个独立问题
（缺 Android 17 预览平台），按 README FAQ 降到 compileSdk 36 即可。

---

## 二·补十 第十轮：库页顶栏合并为一条液态玻璃胶囊（D-22）

用户指令：「去除搜索栏右侧的按键，把搜索栏延长与左侧按键合并，保持原搜索栏的上下高度，
为搜索栏提供与底栏一样的液态玻璃效果」。

### 改动

| 项 | 之前 | 之后 |
|---|---|---|
| 顶栏结构 | `[筛选键] 8dp [搜索胶囊] 8dp [预设搜索键]` 三个独立元素 | **一条液态玻璃胶囊**，内部两个点击区：左侧排序/筛选键 + 右侧搜索区 |
| 搜索区宽度 | 受右侧按键挤压 | 右端延伸到胶囊内边距（真机节点右缘 946px → **1170px**） |
| 高度 | 搜索胶囊 40.dp（但整行被右侧按键撑到 84px） | 仍 40.dp；整行回落到 70px（真机实测上下高度未变） |
| 玻璃质感 | `vibrancy + blur(8dp) + lens(10dp,18dp)`（缺高光/内外阴影/表面白，观感比底栏"薄"） | **与底栏同一份配方**：`vibrancy + blur(4dp) + lens(24dp,24dp) + Highlight.Ambient(0.5f) + Shadow(16dp,25%) + InnerShadow(6dp,0.25) + 2% 白表面 + 离屏合成` |
| 右侧「预设搜索」键 | 打开 `PresetQuickPanel` | **已删除**（连同 `PresetQuickPanel` 组件）；预设改从「排序与筛选」面板右上角「预设」图标进入 |

### 实现要点

- 新增 `ui/components/glass/LiquidGlassSurface.kt`：`Modifier.liquidGlassCapsule(backdrop, activeColor)` ——
  把底栏的玻璃参数**抽成全 App 唯一一份**。此前底栏（MainScreen）、批量操作条（`LiquidGlassBar`）、
  搜索胶囊各自抄参数，已经出现漂移；现在后两者都改为调用它，顶栏胶囊也用它。
- `LiquidGlassSearchBar` 新增 `leading` 槽位：合并进胶囊左侧的独立按键（自带点击区与语义），
  `capsuleClickable` 的语义随之从「整条胶囊」收窄为「搜索区」——这样一个玻璃壳里可以有两个互不干扰的点击区。
- 删除 `PresetQuickPanel`（288 行）及其状态；**预设功能未丢失**：`FilterSheet → 预设图标 → PresetPanel` 仍可进入。
- `ui-ux-enhancement-plan-2026-09-13.md` 的 3.3 节（搜索框右侧按键）与第 84、371 行加了修订说明。

### 真机结构验证（OnePlus PHK110；仅结构与点击，**玻璃观感无法自动核验**）

同一台设备、同一显示设置下对比新旧 uiautomator 节点：

| 节点 | 旧 | 新 |
|---|---|---|
| `排序与筛选` 图标 | `[63,209][133,279]` | `[63,195][133,265]`（同宽，高度 70px 不变） |
| `搜索标题或标签…` | `[336,209][946,279]` | `[294,195][1170,265]` |
| `预设搜索` | `[1072,202][1156,286]` | **节点消失** |

三个顶栏节点现在落在同一条水平带 `y ∈ [195,265]` 内且首尾相接 → 视觉上是一条连续胶囊。
点击验证：点左侧键 → 「排序与筛选」面板；点右侧搜索区 → 搜索页；返回回到图库；
筛选面板右上「预设」图标 → 「已保存预设」面板（预设入口闭环）。

### 验证状态

- `:app:assembleDebug`、`:app:testDebugUnitTest`（**57 类 / 283 用例 / 0 失败**）、`:app:assembleDebugAndroidTest` 全通过，编译告警 0
- 真机：上述结构对比 + 两处点击区 + 预设入口，共 5 项通过

**未删除但已无引用**：`ui/components/glass/LiquidGlassButton.kt`（库页那个预设按键是它唯一的使用点，
DetailScreen 用的是同文件内自己的私有实现）。保留为组件库成员，如需一并清理请示下。

---

## 二·补十一 第十一轮：顶栏改为与底栏同一套悬浮关系（D-23）

用户指令：「为顶栏（现在即搜索栏）提供底栏一样的悬浮效果，略微增强描边使轮廓更明显，
适当增加顶栏的上下高度，缩小顶栏与下面画廊的间隔」。

### 关键认知：此前的「悬浮」是假的

顶栏虽然画在内容之上，但 `Scaffold` 的 `topBar` 预留了 `状态栏 + 60.dp` 的**实心空档**，
画廊只能从空档下方开始——**内容永远不会经过胶囊下方**，玻璃自然采不到卡片，
所以看起来是「嵌在顶部的一条」，而不是底栏那种「浮在内容上的玻璃」。
底栏的做法正相反：内容照常滚到底，只靠 `contentPadding` 保证首屏不被挡。

### 改动

| 项 | 之前 | 之后 |
|---|---|---|
| 悬浮关系 | `Scaffold.topBar` 预留 `状态栏+60dp`，内容被顶下去 | `topBar` 只留状态栏；画廊用 `contentPadding.top` 让位，**内容从胶囊底下穿过**（与底栏一致） |
| 胶囊高度 | 40.dp | **48.dp**（左侧筛选键的点击区同步 48×48） |
| 顶栏↔画廊间隔 | 26.dp | **8.dp** |
| 描边 | 无 | `liquidGlassCapsule(outline = true)`：1.dp 描边，深色主题 20% 白、浅色主题 10% 黑（按 `surface.luminance()` 判定，跟随主题状态而非系统） |

新增 `topBarCapsuleHeight / topBarVerticalPadding / topBarToContentGap` 三个常量，
`topBarClearance` 由它们推导，列表与网格共用；`LiquidGlassSearchBar` 新增 `height: Dp = 48.dp` 参数。

### 真机结构验证（OnePlus PHK110 / 560dpi = 3.5×）

| 指标 | 数值 |
|---|---|
| 胶囊带 | `y ∈ [160, 328]` = **168px = 48dp** ✓（改前 140px = 40dp） |
| 胶囊横向 | `x ∈ [28, 1212]` → 左右各 8dp 内缩 |
| 内部两个点击区 | 筛选键 `[28,160][196,328]`（48×48dp）、搜索区 `[196,160][1212,328]` |
| 静止态首行 | 首行卡片顶 = 356px（状态栏 139 + 62dp）→ 与胶囊底 328px 相差 **28px = 8dp** ✓（改前 26dp） |
| **内容穿过胶囊** | 滚动一格后首行卡片 bounds = `[42,55][606,967]`，顶边 **55px 已在胶囊带内**，与胶囊带重叠整个 168px ✓（改前内容被裁在 391px 以下，不可能重叠） |

**无法自动核验**：1.dp 描边的观感、玻璃折射/高光的实际视觉效果（本机无视觉模型）。

### 已知副作用

下拉刷新的转圈指示器由 `PullToRefreshBox` 默认绘在容器顶部——容器顶部现在就在状态栏下方，
因此该指示器出现时会**在胶囊后方**（下拉过程中会逐渐滑到胶囊下方才露出来）。
如需让它固定出现在胶囊下方，需要给 `PullToRefreshBox` 传自定义 `indicator`（改一处即可），尚未改。

---

## 二·补十二 第十二轮：底栏交互 + 下拉指示器 + 搜索页按 JHenTai 重做（D-24）

用户指令四条：①底栏激活色与「水滴」要停在与当前页面一致的位置（在首页点第 4 键再返回后要回到首页键上）；
②双击底栏「首页」键回顶；③下拉刷新的转圈指示器固定在顶栏胶囊下方；④点搜索栏展开的搜索页参考 JHenTai。

### ① 水滴 / 激活色跟随当前页面

**根因**：`indicatorPosition`（水滴横向位置，同时通过 `roundToInt()` 决定哪一格显示激活色）只被
`LaunchedEffect(selectedIndex)` 拉回。点/拖到第 4 键会把它移到 3，但页面并没有切走，`selectedIndex`
不变 → 该 effect 不重跑 → 水滴与激活色滞留在第 4 键。

**修复**：effect 改为 `LaunchedEffect(selectedIndex, showRecentList)`——抽屉打开时保持现状（水滴停在第 4 键
作为「抽屉已开」的提示），抽屉关闭后回落 `selectedIndex`。

**真机复现 + 验证**（PHK110 / 1080×2376 / density 480）：在底栏把滑块从第 1 格拖到第 4 格 →
抽屉打开、**「首页」的 `selected` 语义消失**（激活色确实跑到第 4 键，复现了用户看到的现象）→
关闭抽屉后 **`selected` 回到「首页」** ✓

### ② 双击「首页」键回顶

实现放在**覆盖当前选中项的透明拖拽层**的 `onClick` 里，用两次点击的时间戳（`HOME_DOUBLE_TAP_MS = 300`）
判断双击，而不是 `combinedClickable(onDoubleClick=…)`：前者实测**永远收不到第二下**——第一下会切页并触发重组，
手势检测器随之被重置（本项目专门插桩验证过）。

**真机验证**：滚动到首卡 y=783（底栏展开）→ 双击「首页」键 → 首卡回到 **y=1001**（= 顶部基线）✓

### ③ 下拉刷新指示器固定在胶囊下方

`PullToRefreshBox` 默认把指示器画在容器顶部——顶栏改为悬浮后容器顶部就是状态栏下方，转圈会从胶囊背后升起。
新增 `state = rememberPullToRefreshState()` + 自定义 `indicator`，把 `PullToRefreshDefaults.Indicator`
放在 `padding(top = topBarClearance)`（= 胶囊底 + 8dp）。

`PullToRefreshDefaults.Indicator` 的签名（M3 1.4.0 的 `Indicator-2poqoh4`：state / isRefreshing / modifier /
containerColor / color / threshold）先用 `javap` 从缓存 AAR 里核对过再写。
**该项为代码级验证**：指示器位置无法用 uiautomator 观测（无文本/语义节点）。

### ④ 搜索页按 JHenTai 重做

参考本机 `D:\program\JHenTai-8.0.16` 的 `lib/src/pages/search/…`（mobile_v2 搜索页 + `search_page_mixin.dart`），
采用它的核心范式：**搜索框承载完整查询、点标签是「追加」而不是立刻跳走、底部统一「搜索」动作**。

| 之前 | 之后 |
|---|---|
| `OutlinedTextField` + 历史/热门/命名空间分组三个互不相关的区块 | 查询框（承载 `ns:value$` 标签 token + 关键词）→ 已选标签 chips（可逐个摘除）→ 命名空间横排 → 二列标签面板 / 输入时的联想列表 → 底部「搜索」动作条（左侧显示已选标签数） |
| 点标签立即 `FilterBus` 过滤或立即提交并返回 | 点标签把 `namespace:value$` **追加进查询串**，可继续加、可删；按底部「搜索」或回车一起提交（与 `LibraryFilterContext.toServerFilter()` 同口径，逗号连接 = AND） |
| 热门标签按 weight | 热门标签排除「日期 / 时间戳」这类机器元数据（它们每个档案都有、weight 极高会霸屏），并按标签自身判断，因此对**中文命名空间**的库同样有效 |
| 命名空间列表来自内置英文词表 | 命名空间列表来自**服务器实际返回的标签**（本库的命名空间是中文「作者/原作/角色…」，用英文词表筛出来的分组点进去是空的） |
| — | 空查询时联想/映射/历史保留；「直接搜索『关键词』」入口保留；词典映射点击也是追加 |

### 本轮发现的**服务端**问题（不是代码 bug）

搜索页的标签面板在这台服务器上是空的。插桩抓到原始响应：

```
tagstats len=541 head=[{"namespace":"date_added","text":"1788366586","weight":"2"}, … ]
tagstats parsed=9
```

`/api/database/stats` 只返回 **9 条** date_added 记录。查服务端源码：该端点读的是 Redis 有序集合
`LRR_STATS`（`LANraragi/Model/Stats.pm::build_tag_stats` → `zrangebyscore("LRR_STATS", minweight, +inf)`），
而这个集合只在服务端扫描/重建索引时写入（`Stats.pm:101/176` 的 `zincrby`）。你这台库的该集合几乎是空的
（很可能是库直接拷进去或 Redis 被清过），因此**所有依赖标签统计的页面都会退化**（旧搜索页的「热门标签」
与「标签筛选」分组同样受影响，这不是本轮引入的）。

APP 侧的应对：面板为空时给出可执行的排查指引（去「设置 → 连接 → 立即重扫服务器文件夹」重扫一次，
或在服务端重建标签索引），并保证**关键词/标签直接输入搜索仍然完全可用**。

### 本轮验证状态

- `:app:assembleDebug`、`:app:testDebugUnitTest`（57 类 / 283 例 / 0 失败）、`:app:assembleDebugAndroidTest` 全通过，编译告警 0
- 真机：①复现+修复 ②双击回顶 ④搜索页结构 + 端到端提交（输入关键词 → 底部「搜索」→ 库页按该条件刷新）共 4 项通过
- ③为代码级验证（指示器位置无法用 uiautomator 观测）

---

## 三、高价值缺陷（已修复）

| # | 缺陷 | 修复要点 | 验证 |
|---|---|---|---|
| 1 | 阅读进度只在**冷启动**时回传（`progressWriter.flush()` 全 App 仅一处调用），本次会话进度要等下次启动才上服务器 | `OutboxProgressWriter` 增加 5 秒合并窗口（文档 B8）的 `scheduleFlush()`；阅读器翻页时调用；`onCleared` 与 `MainActivity.onStop` 各补一次立即 flush（走应用级作用域） | 真机 logcat：会话中 `PUT /progress/N` → 200；退出阅读器 `PUT /progress/17` → 200 |
| 2 | 下载页「服务器任务」区**永远为空**：`JobTracker.track()` 全仓从未被调用 | `LanraragiRepository.onJobQueued` 回调，在 A4/A7/A8/A10 五处入队点上报，`AppContainer` 接到 `jobTracker.track`；同时给 JobTracker 加轮询上限/连续失败收敛/结束任务过期清理，避免新增的协程泄漏 | 真机：工具页触发重复检测 → 下载页出现「服务器任务（1）· 重复检测 · 进行中」，并可见结果行 |
| 3 | A3「启动时读 `/info`」未落地：`refreshServerInfo` 只在向导与设置页调用，统计页「累计阅读页数」恒为**未知** | `AppContainer.init` 在设置就绪后、服务器已配置时拉一次 `/info` | 真机统计页：累计阅读页数 **1779**、服务器 `LANraragi · 版本 Atomica` |
| 4 | 「隐藏读完」双重过滤且口径不一致：客户端按 `progress >= pageCount`（100%），服务端按 `progress/pagecount > 0.85`（`Model/Search.pm:186`） | 客户端兜底过滤改为 `> 0.85f`，与服务端同语义并修正注释 | 静态 + 单测 |
| 5 | 随机抽屉不继承库页**标签**筛选（`LibraryFilterContext` 无 `tags`），只选了标签时会在全库随机 | 上下文增加 `tags`，并新增 `LibraryFilterContext.toServerFilter()`（关键词 + 标签 `tag$` 精确匹配，等同 `LibraryQuery.remoteRequest`） | 真机：`GET /api/search/random?filter=parody%3Agetter%20robo&count=3`，返回即当前命中范围 |
| 6 | 恢复备份**未等 minion 任务**就报「恢复成功，已重置整库」并清空离线缓存 | `restoreBackup` 返回 jobid；`SettingsScreen` 轮询到终态再报成功，失败/超时如实报错；备份路径同样校验任务结果 | 静态（未对生产库执行恢复） |
| 7 | 下载重试**无上限**，永久失败任务无限弹跳 | `DownloadRetryPolicy.maxRetries`（默认 5，文档 C3）；到上限落 FAILED；新增用例 `retryPolicyStopsAtConfiguredRetryLimit` | 单测 |
| 8 | 设置导出/导入遗漏 `downloadRatePerSecond`、`storageRootUri`、`tagTranslationAutoUpdate` | 三处补齐（存储根仅在导入值非空时覆盖，避免把「已配置」清空触发门禁） | 单测（`SettingsProjectionTest` 等） |
| 9 | `previewColumns` 默认值三处不一致（data class 4 / DataStore 回退 3 / DetailScreen 3） | 统一为 4（清单「顺手修复 1」） | 真机设置页显示 4 |
| 10 | `FilterPreset` 不含「隐藏读完」：应用预设后开关残留，高亮却判定「与预设一致」，随机范围不可解释 | `FilterPreset.hideCompleted` + apply/save/`presetMatchesCurrent` 全覆盖 | 静态 |
| 11 | 续读卡片进度取错数据源（取自离线缓存元数据 → 未缓存档案永不显示进度），跳转不带 `?page=N` | 改用 `HistoryEntry.page/pageCount`；有进度时 `Routes.reader(arcid, page)`；`RecentCard` 页号 `+1` 换算为人类页号 | 真机：抽屉三张卡分别显示 `第 18 页 / 第 2 页 / 第 1 页` |
| 12 | A11「全部清除 New」只对**已加载页**逐本循环（N 次请求） | 新增契约端点 `DELETE /api/database/isnew`（`clearAllNew`）+ 仓库包装；库页全局动作改单次调用，成功/失败均有中文提示，且仅在结果集含 New 时显示计数 | 真机：`DELETE /api/database/isnew` → 200 |
| 13 | 底栏**选中的 tab 完全无无障碍语义**（透明拖拽层覆盖其上，uiautomator 记为 NAF） | 在该层补齐 `contentDescription`/`role`/`selected`，与 `SingleTabItem` 同口径 | 真机：四个 tab 全部可被读屏识别 |
| 14 | 详情 FAB 恒为「开始阅读」，无长按入口；4.1 要求双态 + 长按菜单 | 有进度显示「继续阅读 · 第 N 页」；长按弹「从第 N 页开始 / 从头阅读」 | 真机：`继续阅读 · 第 17 页`；长按出现两项菜单 |
| 15 | 阅读器功能行缺 ☀ 亮度键（5.1） | 功能行新增「阅读亮度」按钮，复用既有 `readerBrightness` 状态与 `vm.setReaderBrightness`，弹出滑杆 + 「跟随系统」 | 真机：功能行 5 键含 `阅读亮度` |
| 16 | 沉浸式只隐藏状态栏（5.7） | `ReaderRouteEffects` 改为 `systemBars()` 并原样恢复 | 静态（仅阅读器路由调用） |
| 17 | 主题两套 scheme 未覆写 tertiary/error；设置页无动态色开关（只有向导能开，开了关不掉） | 补全 tertiary/error 系列；外观组新增「动态取色（Material You）」开关（API<31 禁用并说明） | 真机：外观页出现该开关 |
| 18 | `Routes.GUIDE` 无任何导航入口（指南只能看一次）；向导无法从设置重进 | 设置新增「引导」组：「入门指南」→ `Routes.GUIDE`；「重新运行设置向导」→ 新增 `Routes.WIZARD_EDIT`（复用同一组件，`onDone` 只返回） | 真机：两项均可用，返回后回到设置 |
| 19 | 其他 | 存储门禁横幅补「重新选择文件夹 / 使用默认目录」CTA；导航页补顶栏标题「导航」；抽屉切换交叉淡入 + 空态/失败态改 `EmptyBox` + 重试；时间线 Slider 加 `stateDescription`；库页批量栏加「标记未读」；`a11_tankoubons` 门控口径统一；tank 列表分页接线；`TagTranslationStore` 自动更新闩改为「开关打开后本次进程内即生效」；`OfflineCacheManager.downloadTo` / `StorageRoot.probeWritable` 不再吞 `CancellationException`；清理 `AppRoot` 过期常量与不可达的设置顶栏菜单分支 | 真机 + 静态 |

---

## 四、死代码与数据清理

| 项 | 结果 |
|---|---|
| `ui/reader/ReaderViewModel.kt`（零引用原型） | 已删除（文档清单 #7） |
| `data/reader/ReaderLayoutAdapter.kt` | 已删除；同步移除 `ReaderReliabilityTest` 中仅用于钉该契约的 1 个用例（其余 5 个用例与覆盖不变，映射逻辑另有 `ReaderPageMappingTest` 覆盖） |
| `ui/screens/CategoryManager.kt`（`CategoryManagerSheet`） | 已删除。**文档清单 #9 的前提有误**：`ui/CategoryUi.kt:112` 的 `CategoryManagementSheet` 并非被遮蔽的死代码，而是被详情页与库页真实调用；真正零引用的是这个同名不同字的文件 |
| EH 收藏遗留 Room 存储层 | 已删除 3 个实体 + `EhFavoriteDao` + 数据库注册；Room **8 → 9**，新增 `MIGRATION_8_9`（`DROP TABLE IF EXISTS` 三表，其余表不动，未使用 destructive fallback） |
| EH 收藏同步（D1）/ 收藏图片（D7）/ 打卡 | UI、路由、入口、数据层均已彻底移除，全仓 grep 零命中；`DownloadTaskType.PAGE` 与 `FavoriteSection` 不再存在，Room 启动清库 + legacy 导入映射同步跳过 |
| `getRandomArchive()` 旧包装 | 已删除（随机入口统一收敛到抽屉后无调用方） |
| `ui/screens/NavigationScreen.kt`（导航页，227 行）+ `Routes.NAVIGATION` / `Routes.SETTINGS` 两个路由与常量 + `MainTabBus.NAVIGATION_TAB` / `requestNavigationTab()` | 已删除（第五轮：导航页合并进设置）。全仓 grep 对以上标识符零命中 |
| `LibraryScreen(onOpenDrawer = …)` 参数 | 已删除。导航页退役后该参数在库页内**从未被调用**（`grep onOpenDrawer` 仅剩声明与调用点），属零引用参数 |
| `data/FeatureFlags.kt` + `Settings.featureFlags` + DataStore `feature_flags` + `setFeatureFlag`（仓库/VM）+ `UiState.featureFlags` + `LabsSection` + `SettingsSection.LABS` | 已删除（第六轮：两个实验开关转正）。全仓 grep 对以上标识符仅剩 4 处说明性注释 |

---

## 五、与文档批次的对照

**第一批（UI 规划第十节 1–10）：9 项完成，1 项部分完成**

1. 主题（亮色全套 + Typography）✅（另补 tertiary/error 与设置页动态色开关）
2. 玻璃组件出仓 + 删 `FloatingActionBar` + FAB 读色 ✅
   注意：`LiquidGlassButton`（库页预设按键）与 `LiquidGlassBar`（库页批量栏）已接线；
   `LiquidGlassSearchBar`、`LiquidGlassFab` 仍为零调用，**原因是组件 API 缺口**而非遗漏：
   前者硬编码为**可编辑** TextField 且 `onClick` 只在 `value.isEmpty()` 分支生效，无法表达库页「只读展示 + 整条胶囊点按跳转」的行为；
   后者没有 `onLongClick` 槽位且玻璃常量写死为底栏样式，接入会丢掉 4.1 要求的长按入口并改变 FAB 观感。
   两者要接线需先给组件加 `readOnly`（并让 `onClick` 覆盖整条胶囊）/ `onLongClick` + 常量参数化。
3. 底栏第 3 键导航页 + 第 4 键续读/随机抽屉 ✅
4. 库页预设快捷展板 + FilterSheet「隐藏读完」✅（另补按键激活态与「再次点击已选预设取消」）
5. 移除打卡 ✅ 6. 移除 EH 收藏同步 ✅ 7. 移除收藏图片（不做替代入口）✅
8. 阅读器局部修复（双击动画、单页重试、页码 overlay、续读 Snackbar、3.5s 自动隐藏）✅
9. 首启向导五步 + 入门指南 + 运行时存储门禁 ✅（补设置侧重进入口与存储恢复 CTA）
10. 无入口功能处置 ✅（详见第四节；`hideInGallery` 与 `ServerCapabilities` 按用户决定暂不接线）

**第二批半成品（按用户要求补齐）**：FAB 双态 + 长按、功能行亮度键、沉浸式 systemBars、
时间线 Slider 无障碍、抽屉交叉淡入 + `EmptyBox`、底栏 tab 语义、主题补全 + 动态色开关、
存储门禁恢复 CTA、指南/向导入口、导航页标题、库页「标记未读」、tank 分页、`a11_tankoubons` 口径统一 —— 均已完成。

---

## 六、真机验证清单（OnePlus PHK110 / Android 16）

**已验证（27 项）**

1. 覆盖安装既有 v8 数据库 → 应用正常启动，无 Room 迁移异常
2. 图库从服务器加载 2297 本，封面/标题/筛选均正常
3. 底栏四键语义（首页/下载/**设置**/续读随机），选中的 tab 也可被读屏识别
4. 第 3 键 → **设置页**（顶级无返回箭头；含原导航页全部入口的「浏览」组，列表无「首页/随机一本/打卡/收藏的图片/同步 EH 收藏夹」）。原「第 3 键 → 导航页」已在第五轮被本项取代
5. 设置新增「引导」组：入门指南可打开（四张卡齐全）、重新运行设置向导可进入并返回
6. 外观页「动态取色（Material You）」开关存在
7. 库页搜索胶囊点按 → SearchScreen（热门标签、点击标签回填筛选）
8. FilterSheet 含「隐藏读完」；「全部标为已读」单次调用服务端端点 → 200
9. 预设搜索展板打开、展示已保存预设与「保存当前筛选为预设」
10. 续读/随机抽屉：两枚胶囊、续读 3 张卡带进度（第 18/2/1 页）、随机 3 本 + 「换一批」
11. 随机请求携带库页当前 filter（`filter=parody%3Agetter%20robo&count=3`）
12. 详情页：FAB「继续阅读 · 第 17 页」、长按菜单两项、无收藏图片入口
13. 阅读器：页码 overlay、顶栏（返回/标题/页码/更多操作）、功能行 5 键含亮度、底栏 5 页时间线
14. 阅读器更多操作仅三项（复制链接/设为封面/查看信息），**无「保存此页到收藏」**
15. **阅读器翻页正常**（滑动 2→3→4，上一页 18→17）
16. 续读卡片带 `?page=N` 直达正确页
17. 阅读中进度按合并窗口回传 → 200；退出阅读器立即回传 → 200
18. 统计页三分区：本地使用统计 / 服务器统计（档案总数 2297、**累计阅读页数 1779**、标签数 5493、服务器名与版本）/ 标签词云（5493 词条 · 23 命名空间）
19. 工具页触发重复检测 → 下载页「服务器任务（1）· 重复检测 · 进行中」并显示结果
20. 下载页工作台：状态汇总卡、缓存管理卡、并发/速率设置行
21. 真机 instrumented 测试 8 项全通过（含 v1→v9 迁移用例）
22. **设置页「浏览」组**：统计/历史/分类三项可进入，返回回到设置 tab 并保留滚动位置；「用服务器下载链接」在工具组内弹窗正常
23. **设置子分区返回键分层**：子分区 → 设置首页 → 图库（不再从子分区直跳图库）
24. **阅读进度端到端一致**：翻页 `PUT /progress/5,6,4` → 200，重开详情「已读到第 4 页」与最后阅读页一致
25. **「实验室」页已移除**：设置列表不再有该项；「浏览」组中单行本入口常驻；详情页 ⋮ 菜单中「加入卷」常驻
26. **缩略图仓库路径首次真机跑通**（原 `a5_thumbnails` 开关关闭时从未启用）：`GET /api/archives/{id}/thumbnail?no_fallback=true` 全部 200
27. 单行本页空态与新引导文案（「点右上「+」新建，或在档案详情页点「加入卷」」）在真机显示正确

**本机无法验证（需人工/后续确认）**
1. **仅本地书架**路径：需清空应用数据（会丢失现有服务器 profile），仅做代码级验证
2. 存储门禁的「重新选择文件夹」CTA 与 SAF 授权回收恢复流程：本机存储为默认目录（未配置 SAF 根），横幅不出现
3. 玻璃批量栏/搜索胶囊的视觉观感、批量栏 7 键在窄屏的折行：无可用视觉校验工具（本机未配置视觉模型）
4. 平板 rail/master-detail、横屏刘海与双页中线对齐
5. 通知权限、电池优化白名单、崩溃日志开关的系统侧行为
6. 元数据工作台的多候选匹配/diff 确认与「翻译回写」全链路（会写生产服务器数据，未执行）

---

## 七、按用户决定**刻意未做**的事项

| 项 | 决定 | 说明 |
|---|---|---|
| `groupby_tanks` / `TANK_` 卡片路由 | **已裁决并落地**（见「二·补四」D-16） | 原为待裁决项；用户选择实现 `TANK_` → 单行本阅读器路由 |
| `ServerCapabilities` 能力门控 | **已裁决并落地**（见「二·补四」D-14） | 原为待裁决项；用户选择只做两处 UI 不可见门控（进度回传） |
| 全局封面版本破缓存 | **已修**（见 D-8） | 改为按 arcid 记录 |
| C3「特定错误自动暂停整队」 | **已修**（见 D-9） | 401/403 暂停整队 + 中文提示横幅 |
| 重试上限的用户可配设置 | **已修**（见 D-9） | 设置→数据「下载重试上限：N」（1–10，默认 5）+ 导出/导入 |
| 进度回传的重试策略 | **已修**（见 D-10） | 有界指数退避（30s→900s，最多 8 次） |
| `LiquidGlassSearchBar` / `LiquidGlassFab` 接线 | **已修**（见 D-11） | 组件 API 补全并接入；仅剩 FAB 按压动效取舍待裁决 |
| `hideInGallery` 真实语义 | **已定案**（见 D-12） | 维持 `.nomedia` 写删、不做扫描过滤；主线程 SAF IO 已修 |
| 文档勘误 | **已修**（见 D-13） | orphan 文档第 9/12d 项 + UI 规划第 355 行 |

---

## 八、环境问题（重要，会影响后续任何人跑测试）

`C:\Users\<中文用户名>\.gradle` 位于非 ASCII 路径，而 Gradle 以 UTF-8 写出测试 worker 的
`@argfile` 类路径、JVM 启动器却按系统 ANSI 代码页（GBK）读取，导致所有 classpath 条目解析失败，
表现为 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`，
**`:app:testDebugUnitTest` 整体失败、且与代码无关**（`:app:assembleDebug` 不受影响）。

可用两种方式绕过（本次验收采用后者，不改动项目文件）：

```powershell
# 方式一：让守护进程按系统代码页写 argfile
.\gradlew.bat :app:testDebugUnitTest --no-configuration-cache `
  "-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1g -Dfile.encoding=GBK"
```

```powershell
# 方式二：把 Gradle 用户目录搬到纯 ASCII 路径（需真实目录，junction 会被 Gradle 规范化回原路径）
# 并把依赖缓存一并迁移；之后测试与构建均正常
```

另：本机 `git` 不在 PATH，需用 `C:\Program Files\Git\cmd\git.exe`。

### 8.1 验收过程中对真机状态的影响（须知悉）

运行 `:app:connectedDebugAndroidTest` 后，Gradle 会**卸载被测应用**，因此 `com.lanraragi.reader`
的应用数据（服务器 profile、API Key、阅读历史、进度、本地库索引、设置）**已被清空**，
设备当前处于「全新安装 → 首启向导第 1 步」状态。API Key 存于 `shared_prefs/secure_secrets.xml`
（由 Android Keystore 加密），卸载即不可恢复，**需要重新填写**：

- 服务器地址：`https://<自建 LANraragi 地址>:8088`（profile 名称原为 `<自定义名称>`）
- API Key：在 LANraragi 网页端「设置 → 服务器设置」中查看

另外一条环境经验：从设备取二进制文件不能用 `adb shell run-as ... cat > file`（stdout 会把 `\n` 翻译成 `\r\n`，
DataStore 的 protobuf 会因此变成 `CorruptionException: Unable to parse preferences proto`），
应使用 `adb exec-out`（本次即因此无法把先前抓取的设置文件回填）。

---

## 九、遗留与建议（按优先级）

1. ~~**给 `ServerCapabilities` 接线**~~：**已完成**（「二·补四」D-14，进度回传按 `/info` 能力门控）。
2. ~~**`TANK_` 决策**~~：**已完成**（「二·补四」D-16，实现了 `TANK_` → 单行本阅读器路由；
   仍因服务器 0 个单行本而无法真机验收）。
3. ~~**补 `LiquidGlassSearchBar.readOnly` 与 `LiquidGlassFab.onLongClick`**~~：**已完成**（D-11 / D-15）。
4. **进度回传的重试策略**：目前失败任务只留在 outbox、等下次翻页或下次启动；可考虑指数退避的后台重试。
   （**注**：重试策略已在 D-10 落地为有界退避 30s→900s/最多 8 次，此项仅为「后台常驻重试」的进阶设想。）
5. **文档勘误**：`docs/orphan-feature-disposal-2026-09-13.md` 第 9 项（`CategoryUi.kt:112`）的前提与代码不符；
   `docs/ui-ux-enhancement-plan-2026-09-13.md:355` 与 `docs/JHENTAI_ENHANCEMENT_ROADMAP.md:189`
   仍提到已删除的 `ReaderViewModel` / `ReaderLayoutAdapter` / `CategoryManager`。
6. **性能观感**：该服务器单页图片 2–5 MB、耗时 7 秒左右，阅读器预取窗口会并发拉取多页；
   若在弱网下出现首屏偏慢，可考虑结合 `readerFitMode` / `server_resizes_images` 做质量降级。
7. **`Routes.OFFLINE` 是唯一剩下的不可达路由**（无 `navigate()` 调用方、无外链 scheme），
   与它当初「保留兼容」的初衷已经不符；清理需一句确认（第五轮未动）。
8. **平板/横屏的底栏形态**（rail 或 master-detail）仍未实现（原「批次 4」），当前横屏沿用底栏。
