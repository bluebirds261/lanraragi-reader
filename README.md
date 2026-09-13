# LANraragi Reader (Android)

一个为自托管漫画/同人志管理服务器 [LANraragi](https://github.com/Difegue/LANraragi) 编写的 Android 客户端，通过其官方 REST API 完成对接。部署在 Linux NAS 上的 LANraragi 只需开放 HTTP(S) 端口，即可用本 App 在手机上浏览、搜索、阅读与离线缓存。

> **English** — A native Android client for self-hosted [LANraragi](https://github.com/Difegue/LANraragi) servers: Jetpack Compose UI, full REST API integration (library / search / reader / progress), offline archive caching, and a metadata-scraping + Chinese-localization workbench. Talks only to your own server; ships no content of its own.

**状态**：`beta-0.1.1` · Kotlin 2.4 / Jetpack Compose (Material 3) / AGP 9.3 / Gradle 9.6 · minSdk 26（compileSdk 37）· 对接 LANraragi 0.9.81 · 单元测试 57 类 / 283 例全通过（另有 Room 迁移等仪器化测试）

> ⚠️ **免责声明**：本项目只是一个客户端，不提供、不托管、不索引任何内容，全部数据来自你自己部署的服务器。请遵守所在地法律法规，仅用于访问你有权访问的内容。

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
- **开屏封面**：设置 → 外观 → 「开屏封面」可从系统图库选一张图片作为启动画面（约 2 秒后淡出，期间点按可跳过；不设置则显示 App 图标）。
- **统计**：服务器档案数 / 页数 / 标签数等。

## 技术栈

Kotlin + Jetpack Compose (Material 3) · Retrofit + OkHttp（动态 baseUrl + Bearer 鉴权拦截器）· Coil（图片加载，复用鉴权 OkHttp）· kotlinx.serialization · DataStore（设置/收藏）· Navigation Compose。

## 目录结构

```
lanraragi-reader-merged/
├── app/src/main/java/com/lanraragi/reader/
│   ├── data/            # api(Retrofit/OkHttp/Bearer 拦截器) · model · catalog(库网关/排序)
│   │                    # assets(缩略图仓库) · db(Room v9) · download · history · local(SAF 本地库)
│   │                    # metadata(刮削/中文回写) · reader(页面源) · security · storage · tags
│   ├── domain/          # 与 UI 无关的领域逻辑
│   ├── di/AppContainer.kt            # 简易服务定位（无 Hilt）
│   └── ui/
│       ├── AppRoot.kt                # 导航路由表（Routes）
│       ├── adaptive/                 # 响应式骨架（>840dp）
│       ├── components/               # 封面卡片 / 顶栏 / 玻璃组件 / 分段控件 / 状态视图
│       ├── screens/                  # 图库 / 详情 / 阅读器 / 下载 / 设置 / 统计 / 历史 / 分类 / 单行本
│       ├── settings/ setup/ tools/ theme/ reader/ library/
├── app/src/test/         # JVM 单元测试
├── app/src/androidTest/  # 仪器化测试（Room 迁移、Android 正则守卫）
├── app/schemas/          # Room 导出的 schema（迁移基线）
├── docs/                 # 规划、验收报告、功能处置文档
└── gradle/libs.versions.toml         # 版本目录（AGP / Kotlin / KSP / Compose BOM / Room…）
```

## 构建

> **⚠ 本项目用的是很新的工具链，不是"打开即用"的常规项目。** 请按下表准备环境；
> 尤其**不要**执行 `gradle wrapper --gradle-version 8.x` 之类"降级 wrapper"的操作——
> AGP 9.x 无法在 Gradle 8 上运行，同步会直接失败。

| 组件 | 本项目要求 | 说明 |
|---|---|---|
| Gradle | **9.6.0**（wrapper 自带，勿改动） | `gradle/wrapper/gradle-wrapper.properties` 指向腾讯云镜像；换源见下方 FAQ |
| Android Gradle Plugin | **9.3.0** | 已钉在「当前 Android Studio 支持的上限」内。**不要随手升级**：高于 Studio 支持上限时，同步会直接报 `The project is using an incompatible version (AGP x.y.z) … Latest supported version is AGP a.b.c` |
| Kotlin / KSP / Room | 2.4.10 / 2.3.10 / 2.8.4 | 由 `gradle/libs.versions.toml` 固定，勿单独升级 |
| JDK | **17 以上，推荐 21** | Gradle 9.x 最低 17；Android Studio 自带的 JBR 21 即可 |
| compileSdk / targetSdk | **37（Android 17）** | 必须在 SDK Manager 安装 **Android 17 (API 37)** 平台并接受许可。本机 SDK 目录名为 `android-37.0`（预览版命名）。装不上就把它降到 36，见 FAQ |
| Android SDK 路径 | `local.properties` 或 `ANDROID_HOME` | **源码包里故意不含 `local.properties`**（它记录的是打包者的本机路径）。请自行创建或在 IDE 里指定 |

**方式一：Android Studio**

1. 打开本目录（**不要**再套一层子目录）。
2. 首次同步会下载 Gradle 9.6 与全部依赖（约 1.2 GB，国内镜像加速，实测 4–5 分钟）。
3. 连接真机或启动模拟器，点 Run。

**方式二：命令行（不需要 Studio 的版本匹配，只要 JDK + SDK 齐备）**

```bat
:: Windows
set JAVA_HOME=<JDK 21 的路径>
set ANDROID_HOME=<Android SDK 路径>       :: 或者建好 local.properties
gradlew.bat :app:assembleDebug --console=plain
:: 产物：app\build\outputs\apk\debug\app-debug.apk
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk21
./gradlew :app:assembleDebug --console=plain
```

`local.properties`（放在项目根目录，Windows 路径里的 `\` 要写成 `\\` 或 `/`）：

```properties
sdk.dir=D\:\\Android\\Sdk
```

### 构建常见报错对照

| 报错 | 原因 | 处理 |
|---|---|---|
| `Failed to find target with hash string 'android-37'` / `compileSdk 37` 未安装 | 没装 Android 17 (API 37) 平台 | SDK Manager 安装 API 37；或把 `app/build.gradle.kts` 里 `compileSdk`/`targetSdk` 改成 **36**（代码未使用任何 37 专有 API），并删掉 `gradle.properties` 的 `android.suppressUnsupportedCompileSdk=37` |
| `SDK location not found` | 缺 `local.properties` / `ANDROID_HOME` | 见上 |
| `The project is using an incompatible version (AGP x.y.z) … Latest supported version is AGP a.b.c` | 项目的 AGP 高于当前 Android Studio 的支持上限 | 把 `gradle/libs.versions.toml` 里 `agp` 改成报错信息中提示的那个版本（如 `9.4.0` → `9.3.0`）后重新同步；或升级 Android Studio。**只改这一行即可，其余依赖无需调整**（本项目实测 AGP 9.3.0 与 Gradle 9.6 / Kotlin 2.4.10 / KSP 2.3.10 / compileSdk 37 组合可正常编译） |
| `Could not find io.github.kyant0:backdrop:2.0.1` | 到 Maven Central 的网络不通 | 项目已内置阿里云 central 镜像；检查代理/网络后重试 |
| `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`（只在跑 `test`/`build` 时出现，`assembleDebug` 正常） | **中文用户名 + Windows**：Gradle 以 UTF-8 写测试 worker 的 argfile，JVM 启动器却按系统 GBK 读取 | 加参数：`gradlew.bat build "-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1g -Dfile.encoding=GBK"`；或把 `GRADLE_USER_HOME` 指到纯 ASCII 路径（如 `D:\gradle-home`） |
| 同步长时间卡在下载 Gradle 发行包 | 腾讯云镜像不可达 | 改 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 为 `https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip`（`-bin` 比 `-all` 小很多） |

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
| 回传阅读进度 | `PUT /api/archives/{id}/progress/{page}`（0 起） |
| 下载原档 | `GET /api/archives/{id}/download` |
| 删除档案 | `DELETE /api/archives/{id}` |
| 分类 / 标签 | `GET /api/categories` · `GET /api/tags` |
| 统计 | `GET /api/database/stats` |

鉴权：每个请求带 `Authorization: Bearer <base64(API Key)>`（LANraragi 的契约是把 key 做一次 base64，不是直接把明文 key 放进 Bearer），由 OkHttp 拦截器统一注入。

## 说明与已知限制

- **明文 HTTP**：仅内网 HTTP 已放行；若走公网请务必使用 HTTPS 反向代理。
- **页路径编码**：LANraragi 的 `metadata.pages` 返回已 URL 编码的路径，客户端原样透传给 `/page?path=`，兼容含空格/特殊字符的文件名。
- **进度为 0 起**：与 LANraragi 网页端一致。
- **反向代理子路径**：支持 `https://host/lanraragi` 形式的前缀。
- **离线缓存**：以「页图」形式缓存（不解析 rar/zip 原档），因此对 zip/cbz/rar/cbr 均通用；缓存任务在 App 进程内运行，进程被杀后需重新触发（后续可用 WorkManager 升级为后台任务）。
- **下载原档**：保存到 `Android/data/com.lanraragi.reader/files/Download/LANraragi/`，扩展名按文件魔数自动识别（zip/rar/7z/gz）。
- **开屏封面**：选图后会把图片**拷进应用私有目录**（`filesDir/splash/cover.jpg`），因此不需要任何存储权限，也不依赖图库原图是否还在。Android 13+ 走系统照片选择器（无权限弹窗）；Android 8/9 由 AndroidX 自动回退到「文档选择器（仅图片）」。落盘时会按 EXIF 摆正并等比缩到最长边 1600px（通常 200–600 KB）。移除封面会删除该副本。

## License

仅作个人学习/自用示例，与 LANraragi 官方无关。LANraragi 及其 API 版权归原作者所有。
