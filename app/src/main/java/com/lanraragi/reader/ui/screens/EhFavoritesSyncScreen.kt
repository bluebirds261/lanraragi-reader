package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lanraragi.reader.data.favorites.EhFavoriteCategoryPreviewResult
import com.lanraragi.reader.data.favorites.EhFavoriteCategorySyncResult
import com.lanraragi.reader.data.favorites.EhFavoriteMapping
import com.lanraragi.reader.data.favorites.EhFavoriteSlot
import com.lanraragi.reader.data.favorites.EhFavoriteSlots
import com.lanraragi.reader.data.favorites.EhFavoriteSyncState
import com.lanraragi.reader.data.model.Category
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.library.EhFavoriteSyncPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EhFavoritesSyncScreen(
    container: AppContainer,
    navController: NavController,
) {
    val syncState by container.ehFavoritesRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(container.ehFavoriteCredentials.currentCookie() != null) }
    var cookie by rememberSaveable { mutableStateOf("") }
    var selectedSlot by rememberSaveable { mutableStateOf(0) }
    var selectedCategoryId by rememberSaveable { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var preview by remember { mutableStateOf<EhFavoriteCategoryPreviewResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun loadMappingAndPreview() {
        selectedCategoryId = container.ehFavoriteStore.read(selectedSlot)?.lanraragiCategoryId.orEmpty()
        preview = if (selectedCategoryId.isBlank()) {
            EhFavoriteCategoryPreviewResult.NoMapping
        } else {
            container.ehFavoriteCategorySync.preview(selectedSlot)
        }
    }

    LaunchedEffect(Unit) {
        try {
            val bookmarkId = container.repository.getBookmarkCategoryId()
            categories = container.repository.getCategories()
                .filter { it.id.isNotBlank() && it.search.isBlank() && it.id != bookmarkId }
            container.ehFavoritesRepository.loadCached()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = error.message ?: "加载收藏设置失败"
        }
    }
    LaunchedEffect(selectedSlot, syncState) { loadMappingAndPreview() }

    val snapshot = when (val state = syncState) {
        is EhFavoriteSyncState.Ready -> state.snapshot
        is EhFavoriteSyncState.Failed -> state.previous
        else -> null
    }
    val slots = snapshot?.normalizedSlots ?: EhFavoriteSlots.normalize(emptyList())

    Scaffold(
        topBar = { AppTopBar(title = "E-H 收藏同步", onBack = { navController.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "account") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (signedIn) "E-Hentai 已连接" else "连接 E-Hentai", style = MaterialTheme.typography.titleMedium)
                    if (!signedIn) {
                        OutlinedTextField(
                            value = cookie,
                            onValueChange = { cookie = it },
                            label = { Text("Cookie") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                busy = true
                                message = null
                                scope.launchUi(
                                    onError = { error -> message = error.message ?: "登录失败" },
                                    onFinally = { busy = false },
                                ) {
                                    container.ehFavoriteCredentials.saveCookie(cookie)
                                    signedIn = true
                                    cookie = ""
                                    container.ehFavoritesRepository.sync()
                                }
                            },
                            enabled = cookie.isNotBlank() && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("保存凭据并同步") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    busy = true
                                    scope.launchUi(
                                        onError = { error -> message = error.message ?: "同步失败" },
                                        onFinally = { busy = false },
                                    ) { container.ehFavoritesRepository.sync() }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("刷新收藏") }
                            OutlinedButton(
                                onClick = {
                                    busy = true
                                    scope.launchUi(onFinally = { busy = false }) {
                                        container.ehFavoritesRepository.signOut()
                                        signedIn = false
                                    }
                                },
                                enabled = !busy,
                            ) { Text("退出") }
                        }
                    }
                    if (busy || syncState is EhFavoriteSyncState.Loading) {
                        CircularProgressIndicator()
                    }
                    if (syncState is EhFavoriteSyncState.Failed) {
                        Text((syncState as EhFavoriteSyncState.Failed).failure.message, color = MaterialTheme.colorScheme.error)
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            item(key = "slots-heading") { Text("收藏槽位", style = MaterialTheme.typography.titleMedium) }
            item(key = "slots") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(slots, key = EhFavoriteSlot::slotIndex) { slot ->
                        FilterChip(
                            selected = slot.slotIndex == selectedSlot,
                            onClick = { selectedSlot = slot.slotIndex },
                            label = { Text("${slot.slotIndex + 1}. ${slot.remoteName.ifBlank { "收藏" }} (${slot.remoteCount})") },
                        )
                    }
                }
            }

            item(key = "mapping") {
                CategoryMappingChooser(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    enabled = !busy,
                    onSelect = { selectedCategoryId = it },
                    onSave = {
                        if (selectedCategoryId.isNotBlank()) {
                            busy = true
                            scope.launchUi(
                                onError = { error -> message = error.message ?: "保存映射失败" },
                                onFinally = { busy = false },
                            ) {
                                container.ehFavoriteCategorySync.saveMapping(
                                    EhFavoriteMapping(selectedSlot, selectedCategoryId),
                                )
                                preview = container.ehFavoriteCategorySync.preview(selectedSlot)
                            }
                        }
                    },
                )
            }

            preview?.let { result ->
                item(key = "preview-$selectedSlot") {
                    EhFavoriteSyncPanel(
                        result = result,
                        onConfirm = { selectedPreview ->
                            busy = true
                            scope.launchUi(
                                onError = { error -> message = error.message ?: "分类同步失败" },
                                onFinally = { busy = false },
                            ) {
                                when (val applied = container.ehFavoriteCategorySync.confirmAdditiveSync(selectedPreview)) {
                                    is EhFavoriteCategorySyncResult.Applied -> {
                                        message = "已添加 ${applied.added.size} 项，保留 ${applied.kept.size} 项，未关联 ${applied.unmatched.size} 项"
                                        preview = container.ehFavoriteCategorySync.preview(selectedSlot)
                                    }
                                    is EhFavoriteCategorySyncResult.Retryable -> {
                                        message = "已添加 ${applied.added.size} 项，仍有 ${applied.remaining.size} 项待重试：${applied.failure.message}"
                                    }
                                }
                            }
                        },
                        onChangeMapping = { selectedCategoryId = "" },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryMappingChooser(
    categories: List<Category>,
    selectedCategoryId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onSave: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LANraragi 分类映射", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && categories.isNotEmpty()) {
                Text(selected?.name ?: "选择静态分类")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name.ifBlank { category.id }) },
                        onClick = {
                            onSelect(category.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text("同步只会向该分类添加精确匹配项，不会删除任何成员。", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onSave,
            enabled = enabled && selectedCategoryId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存映射并生成预览") }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchUi(
    onError: (Exception) -> Unit = {},
    onFinally: () -> Unit = {},
    block: suspend () -> Unit,
) = launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onError(error)
    } finally {
        onFinally()
    }
}
