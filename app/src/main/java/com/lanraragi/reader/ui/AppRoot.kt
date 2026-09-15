package com.lanraragi.reader.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lanraragi.reader.LanraragiApplication
import com.lanraragi.reader.di.AppContainer
import kotlinx.coroutines.flow.collect
import com.lanraragi.reader.ui.screens.CategoryBrowseScreen
import com.lanraragi.reader.ui.screens.DetailScreen
import com.lanraragi.reader.ui.screens.DiagnosticsScreen
import com.lanraragi.reader.ui.screens.HistoryScreen
import com.lanraragi.reader.ui.screens.LocalDetailScreen
import com.lanraragi.reader.ui.screens.ReaderScreen
import com.lanraragi.reader.ui.screens.StatisticsScreen
import com.lanraragi.reader.ui.screens.TankoubonBrowseScreen
import com.lanraragi.reader.ui.setup.GuideScreen
import com.lanraragi.reader.ui.setup.OnboardingWizard
import com.lanraragi.reader.ui.tools.WritebackScreen
import com.lanraragi.reader.data.diagnostics.DiagnosticReportContext
import com.lanraragi.reader.data.diagnostics.DiagnosticReportCodec
import com.lanraragi.reader.data.diagnostics.DiagnosticProducers
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.db.ReaderDatabase
import com.lanraragi.reader.data.catalog.isTankArchiveId

object Routes {
    const val SETUP = "setup"
    /** 编辑模式向导：从设置再次进入首启向导（复用同一组件，不改变首启判定）。 */
    const val WIZARD_EDIT = "wizard_edit"
    const val MAIN = "main"
    const val DETAIL = "detail"
    const val READER = "reader"
    const val HISTORY = "history"
    const val STATISTICS = "statistics"
    const val GUIDE = "guide"
    const val CATEGORY = "category"
    const val TANKOUBONS = "tankoubons"
    const val TANK_READER = "tank_reader"
    const val DIAGNOSTICS = "diagnostics"
    const val WRITEBACK = "writeback"

    fun detail(arcid: String) = "$DETAIL/$arcid"
    fun reader(arcid: String) = "$READER/$arcid"
    fun reader(arcid: String, page: Int) = "$READER/$arcid?page=$page"
    fun category(id: String) = "$CATEGORY/$id"
    fun tankoubon(id: String) = "$TANKOUBONS/$id"
    fun tankReader(id: String) = "$TANK_READER/$id"
}

/** 辅助函数：统一处理按返回键/滑动返回时回退到主界面的逻辑 */
private fun navigateBackToMain(navController: NavController) {
    // 优先尝试正常的出栈返回
    val popped = navController.popBackStack()
    // 如果出栈失败，或者当前栈顶页面不是 MAIN，则强制重定向回 MAIN
    if (!popped || navController.currentDestination?.route != Routes.MAIN) {
        navController.navigate(Routes.MAIN) {
            popUpTo(Routes.MAIN) { inclusive = true }
            launchSingleTop = true
        }
    }
}

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as LanraragiApplication).container }
}

@Composable
fun AppRoot() {
    val container = rememberAppContainer()
    val startupStartedAt = remember { System.nanoTime() }
    val settings = container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null).value

    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        val elapsedMs = (System.nanoTime() - startupStartedAt) / 1_000_000L
        container.diagnostics.firstScreenReady(elapsedMs)
        DiagnosticProducers.reader(container.diagnostics).success("app_ready", elapsedMs)
    }
    // 首启向导只在「尚未完成引导」时进入。baseUrl 为空是合法状态（仅本地书架）：
    // 向导第 4 步的「跳过，先使用本地书架」不写 baseUrl，若这里仍以 baseUrl 为空判定，
    // 用户每次冷启动都会被弹回向导，永远进不了主界面。
    //
    // 用 remember 把首次判定钉死：NavHost 的 graph 以 startDestination 为 key，
    // 若它在运行中从 SETUP 变成 MAIN（向导收尾会写 onboardingCompleted），整张图会被重建、
    // 返回栈与 ViewModelStore 一并清空——LibraryViewModel.onCleared 会取消共享的图库请求，
    // 于是「保存服务器后进入主界面」那一刻图库停在空列表且不报错。
    // 完成引导后的跳转由向导自己的 navigate(MAIN){popUpTo(SETUP)} 负责，不需要改这里的值。
    val start = remember { if (s.onboardingCompleted) Routes.MAIN else Routes.SETUP }
    var mainTab by rememberSaveable { mutableIntStateOf(0) }

    // E7 深链/分享：消费 DeepLinkBus 中的 arcid，跳转到详情页
    LaunchedEffect(Unit) {
        com.lanraragi.reader.ui.screens.DeepLinkBus.arcid.collect { arcid ->
            if (!arcid.isNullOrBlank()) {
                com.lanraragi.reader.ui.screens.DeepLinkBus.arcid.value = null
                // 深链可能是单行本 id：档案详情端点要求 40 位 arcid，TANK_ 只能进单行本阅读器。
                if (isTankArchiveId(arcid)) {
                    navController.navigate(Routes.tankReader(arcid))
                } else {
                    navController.navigate(Routes.detail(arcid))
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            OnboardingWizard(
                container = container,
                onDone = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }

        // ----------------- 【编辑模式向导（设置 → 引导）】 -----------------
        // 复用同一 OnboardingWizard：各步初值都来自当前 settings，只有主动改动才写回；
        // onDone 只做返回，不再 popUpTo(SETUP)（该路由不在当前返回栈中），
        // markOnboardingCompleted 对已完成引导的用户是幂等空操作。
        composable(Routes.WIZARD_EDIT) {
            OnboardingWizard(
                container = container,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(container, navController, mainTab) { mainTab = it }
        }

        // ----------------- 【元数据中文化向导（设置 → 工具）】 -----------------
        composable(Routes.WRITEBACK) {
            WritebackScreen(container, onBack = { navController.popBackStack() })
        }

        // ----------------- 【使用指南】 -----------------
        composable(Routes.GUIDE) {
            GuideScreen(onBack = { navController.popBackStack() })
        }

        // 【离线缓存页 → 下载页】的路由已于第六轮删除：OfflineScreen 退役后该路由既无
        // 内部 navigate() 调用方，也没有 navDeepLink/自定义 scheme，外部无法抵达。

        composable("${Routes.DETAIL}/{arcid}") { entry ->
            val arcid = entry.arguments?.getString("arcid").orEmpty()
            if (arcid.startsWith("local_")) {
                LocalDetailScreen(container, arcid, navController)
            } else {
                DetailScreen(container, arcid, navController)
            }
        }
        composable("${Routes.READER}/{arcid}?page={page}") { entry ->
            ReaderScreen(
                container,
                entry.arguments?.getString("arcid").orEmpty(),
                navController,
                initialPage = entry.arguments?.getString("page")?.toIntOrNull(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CATEGORY) {
            CategoryBrowseScreen(container, navController, null)
        }
        composable("${Routes.CATEGORY}/{id}") { entry ->
            CategoryBrowseScreen(container, navController, entry.arguments?.getString("id"))
        }
        composable(Routes.TANKOUBONS) {
            TankoubonBrowseScreen(container, navController, null)
        }
        composable("${Routes.TANKOUBONS}/{id}") { entry ->
            TankoubonBrowseScreen(container, navController, entry.arguments?.getString("id"))
        }
        composable("${Routes.TANK_READER}/{id}") { entry ->
            ReaderScreen(
                container = container,
                tankId = entry.arguments?.getString("id").orEmpty(),
                navController = navController,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(container, navController)
        }
        composable(Routes.STATISTICS) {
            StatisticsScreen(container, navController)
        }
        composable(Routes.DIAGNOSTICS) {
            BackHandler { navigateBackToMain(navController) }
            var pendingReport by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            val reportContext = diagnosticReportContext(container)
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                val report = pendingReport
                pendingReport = null
                if (uri != null && report != null) {
                    runCatching {
                        container.context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(report.toByteArray(Charsets.UTF_8))
                        } ?: error("无法打开导出目标")
                    }
                }
            }
            DiagnosticsScreen(
                diagnostics = container.diagnostics,
                reportContext = reportContext,
                onExportToSaf = {
                    pendingReport = DiagnosticReportCodec.encode(
                        container.diagnostics.exportReport(
                            diagnosticReportContext(container),
                        ),
                    )
                    exportLauncher.launch("lanraragi-reader-diagnostics.json")
                },
                onBack = { navigateBackToMain(navController) },
            )
        }
    }
}

private fun diagnosticReportContext(container: AppContainer): DiagnosticReportContext {
    val tasks = container.durableDownloadCoordinator.tasks.value
    val taskSummary = buildMap {
        put("total", tasks.size.toString())
        tasks.groupingBy { it.state.name.lowercase() }
            .eachCount()
            .toSortedMap()
            .forEach { (state, count) -> put(state, count.toString()) }
    }
    val usage = container.offlineCache.usage.value
    val packageInfo = container.context.packageManager.getPackageInfo(container.context.packageName, 0)
    return DiagnosticReportContext(
        appVersion = packageInfo.versionName ?: "unknown",
        deviceSummary = mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT.toString(),
        ),
        serverScope = ApiClient.config.baseUrl.takeIf(String::isNotBlank),
        taskSummary = taskSummary,
        cacheSummary = mapOf(
            "offlineBytes" to usage.totalBytes.toString(),
            "offlineArchives" to usage.count.toString(),
            "localArchives" to container.localScanManager.localArchives.value.size.toString(),
        ),
        roomSchemaSummary = mapOf("version" to ReaderDatabase.CURRENT_SCHEMA_VERSION.toString()),
    )
}
