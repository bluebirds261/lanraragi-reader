# LANraragi Reader (Android)

一个为自托管漫画/本子管理服务器 [LANraragi](https://github.com/Difegue/LANraragi) 编写的 Android 客户端，通过其官方 REST API 完成对接。部署在 Linux NAS 上的 LANraragi 只需开放 HTTP(S) 端口，即可用本 App 在手机上浏览、搜索、阅读与离线缓存。

## 功能

- **服务器连接**：填 NAS 上 LANraragi 的反代地址（hostname/带路径前缀均可，自动补 https://）+ API Key，一键测试连通性。
- **图库浏览**：网格 / 列表双视图一键切换；封面网格无限滚动分页、下拉刷新、排序（标题/最近阅读/添加日期/作者）、正序/倒序、分类筛选、仅新 / 未标记筛选。列表视图展示标题、作者与标签。
- **搜索与标签筛选**：按标题或标签模糊搜索（防抖）；筛选面板内按命名空间（作者 artist / 原作 / 角色 / 语言 / 日期 date / 社团…）分组点选标签。
- **标签规则（借鉴 JHenTai/E-Hentai）**：标签按命名空间自动着色（作者橙 / 角色绿 / 原作红 / 语言紫…），详情页、筛选面板、列表视图统一用同一套命名空间中文名 + 颜色 + 排序规则，一眼区分标签类型。
- **详情页**：元数据、着色标签（点击即可按标签过滤）、阅读进度、收藏、删除。
- **阅读器**：
  - 上下翻页（竖屏连续）与左右翻页（横屏单页，捏合/双击缩放）一键切换；支持多页（双页）对开。
  - 阅读方向：右→左（日漫）与左→右。
  - 点击左右 1/3 区域翻页、中间呼出/隐藏工具栏（可关闭）；音量键翻页（可关闭）；阅读时屏幕常亮（可关闭）。
  - 图片适应模式：适应宽度 / 适应高度 / 适应屏幕 / 原始尺寸，阅读中底栏一键循环切换。
  - 阅读器背景色：黑 / 深灰 / 灰 / 白。
  - 点击唤出工具栏：底栏「上一页 / 滑杆跳页 / 下一页」+ 翻页方式、方向、适应模式切换。
  - 阅读进度自动回传服务器（`/progress`），下次进入可继续阅读。
- **离线缓存**：把整本漫画的每一页下载到本地，无网络也能读；可删除单本或清空。
- **下载原档**：把 zip/cbz/rar 原档保存到手机。
- **统计**：服务器档案数 / 页数 / 标签数等。

## 技术栈

Kotlin + Jetpack Compose (Material 3) · Retrofit + OkHttp（动态 baseUrl + Bearer 鉴权拦截器）· Coil（图片加载，复用鉴权 OkHttp）· kotlinx.serialization · DataStore（设置/收藏）· Navigation Compose。

## 目录结构

```
lanraragi-reader/
├── app/src/main/java/com/lanraragi/reader/
│   ├── data/
│   │   ├── api/        # Retrofit 接口、OkHttp/Coil 客户端、鉴权+动态 host 拦截器
│   │   ├── model/      # Archive / ServerStats / CachedArchive 等
│   │   ├── SettingsRepository.kt     # 服务器地址、API Key、阅读偏好（DataStore）
│   │   ├── FavoritesRepository.kt    # 收藏（DataStore）
│   │   ├── OfflineCacheManager.kt    # 离线缓存（页图下载 + 索引）
│   │   ├── LanraragiRepository.kt    # 业务仓库（解析 + 错误处理）
│   │   └── JsonHelpers.kt            # 兼容不同版本的响应结构
│   ├── di/AppContainer.kt            # 简易服务定位（无 Hilt）
│   └── ui/
│       ├── AppRoot.kt                # 导航路由
│       ├── components/               # 封面卡片 / 顶栏 / 状态视图
│       └── screens/                  # 配置/图库/详情/阅读器/离线/统计/设置
```

## 构建

环境要求：JDK 17、Android SDK（compileSdk 35，Android Studio 会自动下载）。

1. 用 **Android Studio（Koala 及以上）** 打开本目录 `lanraragi-reader/`，等待 Gradle 同步（会自动下载 Gradle 8.9 与依赖）。
2. 连接真机或启动模拟器，点击 Run。
3. 命令行方式：先 `gradle wrapper --gradle-version 8.9` 生成 wrapper，再 `./gradlew :app:assembleDebug`，产物在 `app/build/outputs/apk/debug/`。

> 提示：App 会访问内网 HTTP 明文地址，已在 `AndroidManifest.xml` 与 `res/xml/network_security_config.xml` 中开启 cleartext。

> 已内置国内镜像加速：依赖/插件走阿里云 `maven.aliyun.com`，Gradle 发行包走腾讯云 `mirrors.cloud.tencent.com/gradle`，均保留官方源自动回退（见 `settings.gradle.kts` 与 `gradle/wrapper/gradle-wrapper.properties`）。

## 对接配置

1. 在 NAS 上确认 LANraragi 已启动并可访问（默认端口 3000，例如 `http://192.168.1.10:3000`）。
2. 打开 LANraragi 网页端 → **设置 → 服务器设置**，查看/复制 **API Key**（默认通常是 `LANraragi`，建议改成自己的）。
3. 打开本 App，填入：
   - 服务器地址：`http://192.168.1.10:3000`（若走反向代理可带路径前缀，如 `https://nas.example.com/lanraragi`）
   - API Key：你的 key
4. 点「测试连接」，看到「连接成功：共 N 个档案…」后保存即可。

## 用到的 API（对应关系）

| 功能 | 端点 |
| --- | --- |
| 列表/搜索/筛选/分页 | `GET /api/archives?start=&filter=&sortby=&order=&category=&newonly=&untagged=` |
| 元数据（含 pages、tags、progress） | `GET /api/archives/{id}/metadata` |
| 封面缩略图 | `GET /api/archives/{id}/thumbnail` |
| 单页图片 | `GET /api/archives/{id}/page?path=<已编码页路径>` |
| 回传阅读进度 | `POST /api/archives/{id}/progress/{page}`（0 起） |
| 下载原档 | `GET /api/archives/{id}/download` |
| 删除档案 | `DELETE /api/archives/{id}` |
| 分类 / 标签 | `GET /api/categories` · `GET /api/tags` |
| 统计 | `GET /api/database/stats` |

鉴权：每个请求带 `Authorization: Bearer <APIKey>`，由 OkHttp 拦截器统一注入。

## 说明与已知限制

- **明文 HTTP**：仅内网 HTTP 已放行；若走公网请务必使用 HTTPS 反向代理。
- **页路径编码**：LANraragi 的 `metadata.pages` 返回已 URL 编码的路径，客户端原样透传给 `/page?path=`，兼容含空格/特殊字符的文件名。
- **进度为 0 起**：与 LANraragi 网页端一致。
- **反向代理子路径**：支持 `https://host/lanraragi` 形式的前缀。
- **离线缓存**：以「页图」形式缓存（不解析 rar/zip 原档），因此对 zip/cbz/rar/cbr 均通用；缓存任务在 App 进程内运行，进程被杀后需重新触发（后续可用 WorkManager 升级为后台任务）。
- **下载原档**：保存到 `Android/data/com.lanraragi.reader/files/Download/LANraragi/`，扩展名按文件魔数自动识别（zip/rar/7z/gz）。

## License

仅作个人学习/自用示例，与 LANraragi 官方无关。LANraragi 及其 API 版权归原作者所有。
