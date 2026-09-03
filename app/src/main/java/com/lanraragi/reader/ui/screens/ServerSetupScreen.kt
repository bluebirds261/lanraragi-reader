package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.normalizeBaseUrl
import com.lanraragi.reader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServerSetupViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val url: String = "",
        val key: String = "",
        val name: String = "",
        val testing: Boolean = false,
        val saving: Boolean = false,
        val message: String? = null,
        val success: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            _state.update { it.copy(url = s.baseUrl, key = s.apiKey, name = s.serverName) }
        }
    }

    fun onUrlChange(v: String) = _state.update { it.copy(url = v, success = false, message = null) }
    fun onKeyChange(v: String) = _state.update { it.copy(key = v, success = false, message = null) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v, success = false, message = null) }

    fun test() {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, message = null, success = false) }
            ApiClient.config.baseUrl = normalizeBaseUrl(_state.value.url)
            ApiClient.config.apiKey = _state.value.key.trim()
            try {
                val stats = container.repository.testConnection()
                _state.update {
                    it.copy(
                        testing = false,
                        success = true,
                        message = "连接成功：共 ${stats.total_archives} 个档案",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(testing = false, message = e.message ?: "连接失败") }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            container.settingsRepository.saveServer(_state.value.url, _state.value.key, _state.value.name)
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }
}

@Composable
fun ServerSetupScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: ServerSetupViewModel = viewModel { ServerSetupViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "欢迎使用",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "LANraragi Reader",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "连接到您的 LANraragi 服务器以开始阅读。",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "在下方输入服务器地址（hostname）与 API Key。\n" +
                "API Key 可在 LANraragi 网页端的「设置 → 服务器设置」中找到。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.url,
            onValueChange = vm::onUrlChange,
            label = { Text("服务器地址") },
            placeholder = { Text("manga.example.com") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Link, null) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.key,
            onValueChange = vm::onKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("默认为 LANraragi") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::onNameChange,
            label = { Text("服务器名称") },
            placeholder = { Text("默认为 LANraragi") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Badge, null) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.message!!,
                color = if (state.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = vm::test,
                enabled = !state.testing && state.url.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                if (state.testing) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("测试连接")
                }
            }
            Button(
                onClick = { vm.save(onDone) },
                enabled = !state.saving && state.url.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.saving) "保存中…" else "保存并进入")
            }
        }
    }
}
