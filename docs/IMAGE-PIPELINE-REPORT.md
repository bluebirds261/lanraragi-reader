# 图片链路：缩略图 / 预览图 / 阅读页各是什么图

日期：2026-09-15。回答「详情页与阅读器里正在阅读的图片、预览图、缩略图，是原图还是跟服务器
给画廊生成的封面一样的 thumb」。App 侧 `D:\program\lanraragi-reader-merged`，
服务端 `D:\program\LANraragi-v.0.9.81\LANraragi-v.0.9.81`（0.9.81）。**只读分析**。

结论一句话：**阅读器看到的是归档里的原始页字节（服务端默认不加工）；详情页与列表卡片看到的
是服务端生成的同一张缩略图**（500×1000 外接框内等比缩放、JPEG，封面页 q80 / 页缩略图 q50）。
两者不是同一种图，共用同一份缓存键。

## 速查表

| 场景 | App 侧来源 | 服务端端点 | 原图还是服务端缩略图 | 尺寸/格式 |
| --- | --- | --- | --- | --- |
| 阅读器当前页（远程，默认配置） | `PageSource.RemotePage.url`（`PageSource.kt:104`）→ Coil | `GET /api/archives/{id}/page?path=…` | **原图字节**（归档内条目原样） | 原始分辨率、原格式 |
| 阅读器当前页（远程，`enableresize=1` 且超阈值） | 同上 | 同上 | 服务端重编码 | 宽 ≤1064、强制 JPEG、q=`readerquality`(50) |
| 阅读器当前页（已整本下载） | `SavedArchivePageSource` 读本地归档副本 | 无 | **原图字节** | 原始 |
| 阅读器当前页（SAF 本地 `local_`） | `SafArchive/FolderPageSource` + `ArchiveFileReader.openImageStream` | 无 | **原图字节** | 原始 |
| 阅读器时间线条 / 拖动时中央预览 | `ReaderScreen.kt:342-351,1944,2103` | `GET /api/archives/{id}/thumbnail?page=N+1` | 服务端**页缩略图**，失败自动回退整页原图 | ≤500×1000、jpg |
| 详情页封面 | `ArchiveCover → RemoteThumbnailImage → ThumbnailRepository`（`DetailScreen.kt:2217`） | `GET /api/archives/{id}/thumbnail?no_fallback=true` | **服务端缩略图**（封面页 hq） | ≤500×1000、jpg q80 |
| 图库网格/列表卡片 | 同上（`ArchiveCard.kt:66-74`） | 同上（**同 URL、同缓存 key**） | 服务端缩略图 | 同上 |
| 单行本封面（`TANK_…`） | `RemoteThumbnailImage` 直连分支 | `GET /api/tankoubons/{id}/thumbnail` | 服务端缩略图 | ≤500×1000、hq=1 |
| 详情页预览网格 / 选封面网格 | `PreviewPageImage` / `CoverPickerSheet` | `/api/archives/{id}/thumbnail?page=N` | 服务端页缩略图（选封面网格无回退） | ≤500×1000 |
| 本地档案 `local_` 封面（详情页/下载页） | `ArchivePageModel(uri, 0, thumbnail=true)` | 无 | **原图第 1 页字节**（派生缓存只改变体名，不缩放） | 原始 |
| 离线档案封面 | `offlineCache.coverUri`（下载时抓的 `cover.img`） | 抓取时来自 `/api/archives/{id}/thumbnail` | 服务端缩略图 | ≤500×1000 |

## 关键事实

### 阅读页是原图

- 服务端 `/api/archives/{id}/page` 只在 `enable_resize` 打开时才重编码，且**仅当体积超过
  `sizethreshold`**（默认 1000KB）才处理：缩到宽 1064、强制 JPEG、质量 `readerquality`（默认 50）
  （`Model/Archive.pm:260-279`、`Model/Reader.pm:31-41`、`Utils/Vips.pm:217-224`）。
  `enableresize` 默认 `"0"`（`Model/Config.pm:209`），因此**默认配置下原样下发归档内条目**，
  Content-Type 按原扩展名。
- `/api/info` 的 `server_resizes_images` 就是 `enable_resize`（`Controller/Api/Other.pm:35`），
  它**只描述阅读页的下发压缩，与缩略图无关**。App 把它读成 `ServerCapabilities.resizesImages`
  （`ServerCapabilities.kt:73`），但**全工程没有任何调用点读取该属性**。
- App 侧不做传输层处理：页请求没有显式 `.size(...)`；本地归档用
  `ArchiveFileReader.openImageStream` 原样读字节。只有 Coil 的**解码**会按组件约束算
  `inSampleSize`（`fitWidth/fitHeight/fitScreen` 会采样，`original` 不采样），
  这不改变传输字节。
- **不存在「先低清再换原图」的两段式**：阅读区始终用整页模型。`PageSource.thumbnailModel`
  在生产代码里没有任何调用者，只被测试引用。

### 缩略图是服务端生成的同一张

- 生成：`Utils/Archive.pm:49-62`，质量 50（`use_hq` 时 80）；缩放用 `fit_resize(500, 1000)`
  外接框等比不裁剪（`Utils/Vips.pm:199-204`、`Utils/ImageMagickResizer.pm:83-85`）。
  **尺寸不可配**，0.9.81 没有 `thumbnailsize` 之类的配置项。
- 落盘：文件系统 `thumbdir`（默认 `./thumb`）。封面 `thumb/<id前2位>/<id>.jpg`，
  页缩略图 `thumb/<id前2位>/<id>/<N>.jpg`，单行本 `thumb/TA/<tankid>.jpg`
  （`Model/Archive.pm:202-204`、`Model/Tankoubon.pm:836-838`）。Redis 只存 job/hash 元数据。
- 封面页恒 `use_hq=1`（q80）；页缩略图只有 `hqthumbpages=1` 时才 hq。
- 单行本与普通档案**同一套生成器、同一尺寸**；差别只是 hq=1、输出到 `TA/`、源取
  `zrangebyscore` 排序第一的成员档案第 1 页（`Utils/Minion.pm:58-91`）。
- 端点参数只有 `page` 与 `no_fallback`。**没有 `noflip`**；`force` 只属于
  `POST /api/archives/{id}/files/thumbnails`。缺失时 `no_fallback=true` → 202 + job 轮询
  （App 正是这么用的，`OkHttpThumbnailGateway.kt:20-48`），否则回落到 `noThumb.png`。

### 列表与详情共用同一张、同一份缓存

- URL 完全一致（`ApiClient.thumbnailUrl`，无 query），差异只在本地显示框
  （网格自适应、列表行 112dp、详情 140dp）。
- 缓存键是 `remote-cover|server=…|arcid=…|revision=N`，同时作 memory 与 disk key。
  因为显式设了 `memoryCacheKey`，Coil 直接采用而不拼尺寸：**磁盘条目在列表与详情之间
  共享同一次下载；内存位图在小尺寸解码后不会直接给大尺寸复用，会按需重新解码
  （字节来自磁盘缓存，不重新联网）**。

### 本地档案没有生成缩略图

- `local_` 的「封面」就是归档里第 1 张图的**原始字节**；`thumbnail=true` 只改变派生缓存的
  变体名（`local-derived:cover:` vs `local-derived:page:`），不缩放。
  派生缓存只是字节级 LRU 落盘（上限 64MB），不是缩略图产物。
- 图库列表里的 `local_` 卡片仍走远程缩略图组件（`LibraryResultsContent` 不传 offlineCover），
  会被服务端按 40 位 id 契约拒绝 —— **推断**，未真机实测，表现为占位图标。

## 不确定 / 未验证

1. **运行中服务端的实际配置**（`enableresize` / `hqthumbpages` / `jxlthumbpages`）无法从代码
   确定，需要 `/api/info` 与 `/api/config`，或直接看服务器 `thumb/` 目录。因此「这台服务器上
   阅读器看到的是原图还是 1064 宽 JPEG」取决于实际配置，代码层面两条分支都已就位。
2. `local_` 卡片在图库列表里的实际表现（错误码、是否占位）为推断。
3. Coil 的内存尺寸校验与 `inSampleSize` 行为读自 Gradle 缓存里的 `coil-*-2.7.0-sources.jar`，
   未做运行时验证。
