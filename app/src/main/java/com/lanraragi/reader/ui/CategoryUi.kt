package com.lanraragi.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.model.Category
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private enum class SystemCategoryKind {
    FAVORITES,
    DUPLICATE,
}

private val protectedCategoryNames =
    mapOf(
        "favorite" to SystemCategoryKind.FAVORITES,
        "favorites" to SystemCategoryKind.FAVORITES,
        "favourite" to SystemCategoryKind.FAVORITES,
        "favourites" to SystemCategoryKind.FAVORITES,
        "收藏" to SystemCategoryKind.FAVORITES,
        "收藏夹" to SystemCategoryKind.FAVORITES,
        "收藏夾" to SystemCategoryKind.FAVORITES,
        "最爱" to SystemCategoryKind.FAVORITES,
        "最愛" to SystemCategoryKind.FAVORITES,
        "duplicate" to SystemCategoryKind.DUPLICATE,
        "duplicates" to SystemCategoryKind.DUPLICATE,
        "dupe" to SystemCategoryKind.DUPLICATE,
        "dupes" to SystemCategoryKind.DUPLICATE,
        "重复" to SystemCategoryKind.DUPLICATE,
        "重複" to SystemCategoryKind.DUPLICATE,
    )

private fun normalizedCategoryName(name: String): String =
    name
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

private fun systemCategoryKind(name: String?): SystemCategoryKind? =
    name
        ?.let(::normalizedCategoryName)
        ?.let(protectedCategoryNames::get)

fun categoryDisplayName(name: String?): String =
    when (systemCategoryKind(name)) {
        SystemCategoryKind.FAVORITES -> "收藏"
        SystemCategoryKind.DUPLICATE -> "重复"
        null -> name.orEmpty()
    }

fun isProtectedCategoryName(name: String?): Boolean =
    systemCategoryKind(name) != null

fun categoryNameError(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "分类名称不能为空"
        isProtectedCategoryName(trimmed) -> "不能使用系统分类名称"
        else -> null
    }
}

object CategoryRefreshBus {
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.update { it + 1 }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementSheet(
    categories: List<Category>,
    selectedCategoryIds: Set<String>? = null,
    protectedCategoryIds: Set<String> = emptySet(),
    loading: Boolean,
    busy: Boolean,
    onToggle: ((String) -> Unit)? = null,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onValidationError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedCategoryIds == null) "分类管理" else "分类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                loading && categories.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                categories.isEmpty() -> {
                    Text(
                        "暂无分类",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }

                else -> categories.forEach { category ->
                    val protected =
                        category.id in protectedCategoryIds || isProtectedCategoryName(category.name)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (selectedCategoryIds != null && onToggle != null) {
                            Checkbox(
                                checked = category.id in selectedCategoryIds,
                                enabled = !busy,
                                onCheckedChange = { onToggle(category.id) },
                            )
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                categoryDisplayName(category.name),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (category.search.isNotBlank()) {
                                    "动态分类"
                                } else {
                                    "${category.archives.size} 个档案"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (protected) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "系统分类不可修改",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp).size(18.dp),
                            )
                        } else {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    renameTarget = category
                                    renameName = category.name
                                },
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "重命名分类")
                            }
                            IconButton(
                                enabled = !busy,
                                onClick = { deleteTarget = category },
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除分类",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                enabled = !busy,
                onClick = {
                    newName = ""
                    showCreate = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建分类")
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) showCreate = false
            },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    enabled = !busy,
                    label = { Text("分类名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val error = categoryNameError(newName)
                        if (error != null) {
                            onValidationError(error)
                        } else {
                            onCreate(newName.trim())
                            showCreate = false
                        }
                    },
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showCreate = false }) {
                    Text("取消")
                }
            },
        )
    }

    renameTarget?.let { category ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) renameTarget = null
            },
            title = { Text("重命名分类") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    enabled = !busy,
                    label = { Text("分类名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val error = categoryNameError(renameName)
                        if (error != null) {
                            onValidationError(error)
                        } else {
                            onRename(category.id, renameName.trim())
                            renameTarget = null
                        }
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) deleteTarget = null
            },
            title = { Text("删除分类") },
            text = { Text("确定要删除分类「${categoryDisplayName(category.name)}」吗？") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        onDelete(category.id)
                        deleteTarget = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}
