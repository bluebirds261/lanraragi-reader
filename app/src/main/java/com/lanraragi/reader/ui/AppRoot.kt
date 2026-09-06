package com.lanraragi.reader.ui

import androidx.activity.compose.BackHandler
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
import com.lanraragi.reader.ui.screens.CheckinScreen
import com.lanraragi.reader.ui.screens.DetailScreen
import com.lanraragi.reader.ui.screens.FavoritesScreen
import com.lanraragi.reader.ui.screens.HistoryScreen
import com.lanraragi.reader.ui.screens.LocalDetailScreen
import com.lanraragi.reader.ui.screens.NavigationScreen
import com.lanraragi.reader.ui.screens.OfflineScreen
import com.lanraragi.reader.ui.screens.ReaderScreen
import com.lanraragi.reader.ui.screens.SearchScreen
import com.lanraragi.reader.ui.screens.ServerSetupScreen
import com.lanraragi.reader.ui.screens.StatisticsScreen
import com.lanraragi.reader.ui.screens.StatsScreen
import com.lanraragi.reader.ui.screens.TankoubonBrowseScreen
import com.lanraragi.reader.ui.screens.TankReaderScreen

object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
    const val DETAIL = "detail"
    const val READER = "reader"
    const val SEARCH = "search"
    const val STATS = "stats"
    const val OFFLINE = "offline"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val CHECKIN = "checkin"
    const val STATISTICS = "statistics"
    const val NAVIGATION = "navigation"
    const val CATEGORY = "category"
    const val TANKOUBONS = "tankoubons"
    const val TANK_READER = "tank_reader"

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
    val start = if (s.baseUrl.isBlank()) Routes.SETUP else Routes.MAIN
    var mainTab by rememberSaveable { mutableIntStateOf(0) }

    // E7 深链/分享：消费 DeepLinkBus 中的 arcid，跳转到详情页
    LaunchedEffect(Unit) {
        com.lanraragi.reader.ui.screens.DeepLinkBus.arcid.collect { arcid ->
            if (!arcid.isNullOrBlank()) {
                com.lanraragi.reader.ui.screens.DeepLinkBus.arcid.value = null
                navController.navigate(Routes.detail(arcid))
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            ServerSetupScreen(container) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
            }
        }
        composable(Routes.MAIN) {
            MainScreen(container, navController, mainTab) { mainTab = it }
        }

        // ----------------- 【设置 / 导航页面】 -----------------
        composable(Routes.NAVIGATION) {
            BackHandler {
                navigateBackToMain(navController)
            }
            NavigationScreen(container, navController, mainTab) { mainTab = it }
        }

        // ----------------- 【下载 / 离线页面】 -----------------
        composable(Routes.OFFLINE) {
            BackHandler {
                navigateBackToMain(navController)
            }
            OfflineScreen(container, navController)
        }

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
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(container, navController)
        }
        composable(Routes.STATS) {
            StatsScreen(container, navController)
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
            TankReaderScreen(container, entry.arguments?.getString("id").orEmpty(), navController)
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(container, navController)
        }
        composable(Routes.HISTORY) {
            HistoryScreen(container, navController)
        }
        composable(Routes.CHECKIN) {
            CheckinScreen(container, navController)
        }
        composable(Routes.STATISTICS) {
            StatisticsScreen(container, navController)
        }
    }
}