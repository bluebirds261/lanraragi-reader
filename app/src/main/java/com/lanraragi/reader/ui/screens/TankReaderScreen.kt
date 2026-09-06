package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.ErrorBox
import com.lanraragi.reader.ui.LoadingBox
import com.lanraragi.reader.ui.LoadingImage
import com.lanraragi.reader.ui.edgeSwipeBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 卷内一页：所属档案 + 该档案内页序号 + 绝对 URL。 */
data class TankPage(
    val arcid: String,
    val localIndex: Int,
    val url: String,
)

/** A11 整卷阅读：把卷内各档案的页依次拼成一条连续页流，跨档案续读。 */
class TankReaderViewModel(
    private val container: AppContainer,
    private val tankId: String,
) : ViewModel() {

    data class UiState(
        val title: String = "",
        val pages: List<TankPage> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val currentPage: Int = 0,
    )

    private val repository = container.repository
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val tank = repository.getTankoubonFull(tankId)
                val pages = mutableListOf<TankPage>()
                for (arcid in tank.archives) {
                    val urls = runCatching { repository.getPageUrls(arcid) }.getOrElse { emptyList() }
                    urls.forEachIndexed { i, url -> pages += TankPage(arcid, i, url) }
                }
                val start = (tank.progress - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                _state.update {
                    it.copy(
                        title = tank.name,
                        pages = pages,
                        loading = false,
                        currentPage = start,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载整卷失败") }
            }
        }
    }

    fun reportPage(page: Int) {
        if (_state.value.pages.isEmpty()) return
        val p = page.coerceIn(0, _state.value.pages.size - 1)
        _state.update { it.copy(currentPage = p) }
        viewModelScope.launch {
            runCatching { repository.updateTankoubonProgress(tankId, p + 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankReaderScreen(container: AppContainer, tankId: String, navController: NavController) {
    val vm: TankReaderViewModel = viewModel { TankReaderViewModel(container, tankId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)) }
    }

    // 滚动位置变化 → 记录全局进度（LaunchedEffect 天然节流：每次变化取消上一次 delay）。
    LaunchedEffect(currentPage) {
        if (state.pages.isNotEmpty()) {
            delay(600)
            vm.reportPage(currentPage)
        }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(
                title = state.title,
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error!!, onRetry = { vm.reportPage(0) })
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.pages, key = { _, p -> "${p.arcid}_${p.localIndex}" }) { index, page ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingImage(
                                model = page.url,
                                contentDescription = "第 ${index + 1} 页",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }
    }
}
