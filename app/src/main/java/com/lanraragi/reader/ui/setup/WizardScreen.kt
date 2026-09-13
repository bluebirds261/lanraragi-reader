package com.lanraragi.reader.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.data.model.ServerInfo
import com.lanraragi.reader.data.normalizeBaseUrl
import com.lanraragi.reader.data.refreshServerInfo
import com.lanraragi.reader.ui.screens.LibraryRefreshBus
import com.lanraragi.reader.di.AppContainer
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- 主题预览卡的固定色板（默认=定制蓝紫色板 / 自定义=多彩提示） ----------------

private val SchemeDefaultTop = Color(0xFF1E2835)
private val SchemeDefaultAccent = Color(0xFFE94560)
private val SchemeDefaultSecond = Color(0xFF3F51B5)
private val SchemeDefaultThird = Color(0xFF9C27B0)

private val SchemeCustomTop = Color(0xFF37474F)
private val SchemeCustomAccent = Color(0xFF0084FF)
private val SchemeCustomSecond = Color(0xFF00BFA5)
private val SchemeCustomThird = Color(0xFFFF6D00)

private const val SUCCESS_GREEN = 0xFF4CAF50

/**
 * 首启向导（Komikku 卡片式五步）：主题 → 存储(强制) → 权限 → 服务器连接(可跳过) → 收尾。
 * 所有设置写入均走 [AppContainer.settingsRepository]，服务器逻辑沿用旧 ServerSetupScreen 流程。
 */
class WizardViewModel(private val container: AppContainer) : ViewModel() {

    /** 服务器连接表单与测试状态。 */
    data class ServerForm(
        val url: String = "",
        val key: String = "",
        val name: String = "",
        val testing: Boolean = false,
        val saving: Boolean = false,
        val testedOk: Boolean = false,
        val success: Boolean = false,
        val message: String? = null,
    )

    private val _server = MutableStateFlow(ServerForm())
    val server = _server.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { container.runtimeSettingsReady.await() }
            val s = container.settingsRepository.settings.first()
            _server.update { it.copy(url = s.baseUrl, key = s.apiKey, name = s.serverName) }
        }
    }

    fun showNotice(text: String) { _notice.value = text }

    fun clearNotice() { _notice.value = null }

    fun onUrlChange(v: String) = _server.update { it.copy(url = v, testedOk = false, success = false, message = null) }
    fun onKeyChange(v: String) = _server.update { it.copy(key = v, testedOk = false, success = false, message = null) }
    fun onNameChange(v: String) = _server.update { it.copy(name = v) }

    /** 连接测试：照抄旧 ServerSetupScreen 的 test() 流程，失败按四类映射为建议文案。 */
    fun testConnection() {
        viewModelScope.launch {
            _server.update { it.copy(testing = true, message = null, success = false) }
            ApiClient.config.baseUrl = normalizeBaseUrl(_server.value.url)
            ApiClient.config.apiKey = _server.value.key.trim()
            try {
                val stats = container.repository.testConnection()
                refreshServerInfo(container.repository)
                val info = ApiClient.config.serverInfo.value
                if (info != null && info.name.isNotBlank() && _server.value.name.isBlank()) {
                    _server.update { it.copy(name = info.name) }
                }
                _server.update {
                    it.copy(testing = false, testedOk = true, success = true, message = "连接成功：共 ${stats.total_archives} 个档案")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiClient.config.serverInfo.value = null
                _server.update {
                    it.copy(
                        testing = false,
                        testedOk = false,
                        success = false,
                        message = describeTestError(
                            e.message ?: "连接失败",
                            usesHttp = normalizeBaseUrl(_server.value.url).startsWith("http://"),
                        ),
                    )
                }
            }
        }
    }

    /** 连接失败四类映射：明文拦截 / 证书校验 / 无法连接 / API Key 不正确。 */
    private fun describeTestError(raw: String, usesHttp: Boolean): String {
        val m = raw.lowercase()
        return when {
            "cleartext" in m -> "明文流量被系统拦截：Android 默认禁止 http 明文传输，请改用 https 地址，或为该域名配置明文例外。"
            "ssl" in m || "handshake" in m || "证书" in m -> "证书校验失败：服务器可能使用了自签名证书，请在系统中安装并信任证书后重试。"
            "401" in m || "403" in m || "鉴权" in m || "api key" in m || "无权限" in m ->
                "API Key 不正确：请在 LANraragi 网页端「设置 → 服务器设置」中核对后重新填写。"
            "超时" in m || "timeout" in m || "timed out" in m || "无法解析" in m || "unknownhost" in m ||
                "unable to resolve" in m || "无法连接" in m ->
                "无法连接到服务器：请检查地址是否正确，并确认本机与服务器在同一网络。"
            "网络错误" in m && usesHttp -> "明文流量可能被系统拦截：Android 默认禁止 http 明文传输，请改用 https 地址，或为该域名配置明文例外。"
            else -> raw.ifBlank { "连接失败，请检查地址后重试" }
        }
    }

    /** 保存服务器配置（照抄旧 saveServer 流程），完成后回调。 */
    fun saveServerConfig(onSaved: () -> Unit) {
        viewModelScope.launch {
            _server.update { it.copy(saving = true) }
            container.settingsRepository.saveServer(_server.value.url, _server.value.key, _server.value.name)
            refreshServerInfo(container.repository)
            // 编辑模式向导可能在图库已存在的情况下更换服务器，必须让图库重新拉一次，
            // 否则返回主界面看到的还是旧服务器（或空）的列表。
            LibraryRefreshBus.tick.value++
            _server.update { it.copy(saving = false) }
            onSaved()
        }
    }

    /** 标记向导完成并进入主界面（正常收尾与「跳过服务器」共用）。 */
    fun finishOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            container.settingsRepository.markOnboardingCompleted()
            onDone()
        }
    }

    // ---- Step1 主题 ----

    fun setTheme(theme: String) { viewModelScope.launch { container.settingsRepository.setTheme(theme) } }

    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { container.settingsRepository.saveDynamicColor(enabled) } }

    // ---- Step2 存储 ----

    /** 保存存储根目录；原目录若同时是本地扫描目录，则把新目录延续并入扫描集合。传 null 表示使用默认目录。 */
    fun saveStorageRoot(uri: String?) {
        viewModelScope.launch {
            val prev = container.settingsRepository.settings.first()
            if (uri == null) {
                container.settingsRepository.saveStorageRoot(null)
                return@launch
            }
            // 原根目录若同时被勾选为扫描目录，换成新目录后要把它一并并入扫描集合，
            // 否则「存储根 = 扫描目录」的自定义布局会在换目录后丢扫描范围。
            val keepScan = prev.storageRootUri != null && prev.storageRootUri in prev.extraScanDirUris
            container.settingsRepository.saveStorageRoot(uri)
            if (keepScan) container.settingsRepository.addExtraScanDirUri(uri)
        }
    }

    /** 勾选/取消「同时作为本地扫描目录」。 */
    fun setScanRootIncluded(uri: String, include: Boolean) {
        viewModelScope.launch {
            if (include) container.settingsRepository.addExtraScanDirUri(uri) else container.settingsRepository.removeExtraScanDirUri(uri)
        }
    }

    // ---- Step3 权限 ----

    fun setCaptureLogcat(enabled: Boolean) { viewModelScope.launch { container.settingsRepository.setCaptureLogcat(enabled) } }

    // ---- Step5 还原备份 ----

    /** 从 SAF 选中的 JSON 文件还原非敏感配置。 */
    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IOException("无法读取配置文件")
                container.settingsRepository.importNonSensitiveConfig(payload)
                showNotice("配置还原成功")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showNotice(e.message ?: "配置还原失败")
            }
        }
    }
}

/** 把 SAF tree URI 转成可读的文件夹描述（primary 卷显示为「内部存储/路径」）。 */
private fun describeTreeUri(uriString: String): String = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
    val parts = docId.split(':', limit = 2)
    val path = parts.getOrNull(1).orEmpty()
    when (parts.firstOrNull()) {
        "primary" -> if (path.isBlank()) "内部存储" else "内部存储/$path"
        else -> docId
    }
}.getOrDefault(uriString)

@Composable
fun OnboardingWizard(container: com.lanraragi.reader.di.AppContainer, onDone: () -> Unit) {
    val vm: WizardViewModel = viewModel { WizardViewModel(container) }
    val server by vm.server.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val serverInfo by ApiClient.config.serverInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var storageConfirmed by rememberSaveable { mutableStateOf(false) }
    var customSchemePicked by rememberSaveable { mutableStateOf(false) }
    var confirmUntestedSave by remember { mutableStateOf(false) }
    val permissionVersion = remember { mutableIntStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionVersion.intValue++
        if (!granted) vm.showNotice("通知权限未授予，可稍后在系统设置中开启")
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                vm.saveStorageRoot(uri.toString())
            } catch (e: Exception) {
                vm.showNotice("无法获取文件夹访问权限：${e.message ?: "未知错误"}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(context, uri)
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearNotice()
        }
    }

    // 从系统设置页返回后刷新权限/电池优化状态的显示。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionVersion.intValue++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val batteryWhitelisted = remember(permissionVersion.intValue) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    val legacyNotification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    val notificationGranted = remember(permissionVersion.intValue) {
        legacyNotification ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    val storageRootUri = settings?.storageRootUri
    val scanDirUris = settings?.extraScanDirUris ?: emptySet()
    val storageConfigured = storageRootUri != null || storageConfirmed

    val onPrimaryClick: () -> Unit = {
        when (step) {
            3 -> when {
                server.url.isBlank() -> step = 4
                server.testedOk -> vm.saveServerConfig { step = 4 }
                else -> confirmUntestedSave = true
            }
            4 -> vm.finishOnboarding(onDone)
            else -> step += 1
        }
    }
    val primaryEnabled = when (step) {
        1 -> storageConfigured
        3 -> !server.testing && !server.saving
        else -> true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                WizardHeader()
                StepDots(
                    current = step,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp),
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(260)) { full -> if (forward) full / 5 else -full / 5 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(200)) { full -> if (forward) -full / 5 else full / 5 } + fadeOut(tween(200)))
                    },
                    label = "wizardStep",
                    modifier = Modifier.weight(1f),
                ) { current ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (current) {
                            0 -> ThemeStep(
                                theme = settings?.theme ?: "system",
                                dynamicColor = settings?.dynamicColor == true,
                                customPicked = customSchemePicked,
                                onThemeChange = vm::setTheme,
                                onSchemeSelect = { scheme ->
                                    when (scheme) {
                                        "dynamic" -> vm.setDynamicColor(true)
                                        "custom" -> {
                                            customSchemePicked = true
                                            vm.setDynamicColor(false)
                                        }
                                        else -> {
                                            customSchemePicked = false
                                            vm.setDynamicColor(false)
                                        }
                                    }
                                },
                            )
                            1 -> StorageStep(
                                rootUri = storageRootUri,
                                scanUris = scanDirUris,
                                onPickFolder = { storageLauncher.launch(null) },
                                onUseDefault = {
                                    storageConfirmed = true
                                    vm.saveStorageRoot(null)
                                },
                                onScanRootChange = { include ->
                                    val root = storageRootUri
                                    if (root != null) vm.setScanRootIncluded(root, include)
                                },
                                onOpenGuide = { showGuide = true },
                            )
                            2 -> PermissionStep(
                                notificationGranted = notificationGranted,
                                legacyNotification = legacyNotification,
                                batteryWhitelisted = batteryWhitelisted,
                                captureLogcat = settings?.captureLogcat == true,
                                onRequestNotification = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        try {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } catch (e: Exception) {
                                            vm.showNotice("无法发起权限请求：${e.message ?: "未知错误"}")
                                        }
                                    }
                                },
                                onOpenBatterySettings = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (e: Exception) {
                                        vm.showNotice("无法打开电池优化设置：${e.message ?: "设备不支持"}")
                                    }
                                },
                                onCaptureLogcatChange = vm::setCaptureLogcat,
                            )
                            3 -> ServerStep(
                                form = server,
                                serverInfo = serverInfo,
                                onUrlChange = vm::onUrlChange,
                                onKeyChange = vm::onKeyChange,
                                onNameChange = vm::onNameChange,
                                onTest = vm::testConnection,
                                onSkip = { vm.finishOnboarding(onDone) },
                            )
                            else -> FinishStep(
                                onOpenGuide = { showGuide = true },
                                onPickBackup = {
                                    try {
                                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                                    } catch (e: Exception) {
                                        vm.showNotice("无法打开文件选择器：${e.message ?: "设备不支持"}")
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (step == 1 && !storageConfigured) {
                    Text(
                        "请先选择文件夹或使用默认目录后继续",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
                    )
                }
                Button(
                    onClick = onPrimaryClick,
                    enabled = primaryEnabled,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        if (step == 4) "开始使用" else "下一步",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            // 内嵌指南页：返回后停留在向导当前步。
            if (showGuide) {
                GuideScreen(onBack = { showGuide = false })
            }
        }
    }

    if (confirmUntestedSave) {
        AlertDialog(
            onDismissRequest = { confirmUntestedSave = false },
            title = { Text("尚未测试连接") },
            text = { Text("尚未测试连接，仍要保存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUntestedSave = false
                    vm.saveServerConfig { step = 4 }
                }) { Text("仍要保存") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUntestedSave = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun WizardHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AutoStories,
                contentDescription = "应用图标",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("欢迎！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "首先调整一些设置。稍后也可以在设置中修改。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepDots(current: Int, count: Int = 5, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

// ---------------- Step1 主题 ----------------

@Composable
private fun ThemeStep(
    theme: String,
    dynamicColor: Boolean,
    customPicked: Boolean,
    onThemeChange: (String) -> Unit,
    onSchemeSelect: (String) -> Unit,
) {
    val selectedScheme = when {
        dynamicColor -> "dynamic"
        customPicked -> "custom"
        else -> "default"
    }
    val dynamicScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(LocalContext.current) else null

    StepLabel("外观主题")
    Text(
        "选择明暗模式与配色方案，修改立即生效，稍后可在「设置 → 外观」中调整。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    ThemeSegmentedControl(
        options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
        selected = theme,
        onSelect = onThemeChange,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SchemeCard(
            title = "默认",
            subtitle = "定制蓝紫色板",
            selected = selectedScheme == "default",
            enabled = true,
            topColor = SchemeDefaultTop,
            blockColors = listOf(SchemeDefaultAccent, SchemeDefaultSecond, SchemeDefaultThird),
            onClick = { onSchemeSelect("default") },
            modifier = Modifier.weight(1f),
        )
        SchemeCard(
            title = "动态",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Material You 取色" else "需要 Android 12+",
            selected = selectedScheme == "dynamic",
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            topColor = dynamicScheme?.surfaceVariant ?: MaterialTheme.colorScheme.surfaceVariant,
            blockColors = listOf(
                dynamicScheme?.primary ?: MaterialTheme.colorScheme.primary,
                dynamicScheme?.secondary ?: MaterialTheme.colorScheme.secondary,
                dynamicScheme?.tertiary ?: MaterialTheme.colorScheme.tertiary,
            ),
            onClick = { onSchemeSelect("dynamic") },
            modifier = Modifier.weight(1f),
        )
        SchemeCard(
            title = "自定义",
            subtitle = "可在 设置→外观 自定义颜色",
            selected = selectedScheme == "custom",
            enabled = true,
            topColor = SchemeCustomTop,
            blockColors = listOf(SchemeCustomAccent, SchemeCustomSecond, SchemeCustomThird),
            onClick = { onSchemeSelect("custom") },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 明暗三态分段选择。 */
@Composable
private fun ThemeSegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 主题预览卡：圆角卡内画色块 mockup，选中描边 + 右上勾选角标。 */
@Composable
private fun SchemeCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    topColor: Color,
    blockColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(topColor),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                blockColors.forEach { color ->
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(color))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ---------------- Step2 存储（强制） ----------------

@Composable
private fun StorageStep(
    rootUri: String?,
    scanUris: Set<String>,
    onPickFolder: () -> Unit,
    onUseDefault: () -> Unit,
    onScanRootChange: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
) {
    StepLabel("存储位置")
    Text(
        "选择一个文件夹，用于存放离线页缓存与下载的原档保存，也可以把它作为本地漫画的扫描根目录。\n推荐使用一个专门的文件夹。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "已选文件夹",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                rootUri?.let(::describeTreeUri) ?: "默认目录",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPickFolder,
            shape = CircleShape,
            modifier = Modifier.weight(1f),
        ) { Text("选择文件夹") }
        OutlinedButton(
            onClick = onUseDefault,
            shape = CircleShape,
            modifier = Modifier.weight(1f),
        ) { Text("使用默认目录") }
    }
    Spacer(Modifier.height(4.dp))
    val scanIncluded = rootUri != null && rootUri in scanUris
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = rootUri != null) { onScanRootChange(!scanIncluded) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = scanIncluded,
            onCheckedChange = onScanRootChange,
            enabled = rootUri != null,
        )
        Column {
            Text("同时作为本地扫描目录", style = MaterialTheme.typography.bodyMedium)
            if (rootUri == null) {
                Text(
                    "先选择一个文件夹后可用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    TextButton(onClick = onOpenGuide) { Text("从旧版升级？查看存储指南") }
}

// ---------------- Step3 权限 ----------------

@Composable
private fun PermissionStep(
    notificationGranted: Boolean,
    legacyNotification: Boolean,
    batteryWhitelisted: Boolean,
    captureLogcat: Boolean,
    onRequestNotification: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onCaptureLogcatChange: (Boolean) -> Unit,
) {
    StepLabel("权限")
    Text(
        "授予以下权限可获得完整体验，全部可以稍后在系统设置中修改。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    PermissionRow(
        title = "通知权限",
        description = "用于下载进度与任务状态通知" + if (legacyNotification) "（系统默认允许）" else "",
        granted = notificationGranted,
        onClick = onRequestNotification,
    )
    PermissionRow(
        title = "后台运行",
        description = "加入电池优化白名单，避免长时间下载或备份任务被系统中断（可选）",
        granted = batteryWhitelisted,
        onClick = onOpenBatterySettings,
    )
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("本地崩溃日志捕获", style = MaterialTheme.typography.bodyLarge)
            Text(
                "开启后应用崩溃时在本地记录日志，便于排查问题",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = captureLogcat, onCheckedChange = onCaptureLogcatChange)
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (granted) "已开启" else "未开启",
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

// ---------------- Step4 服务器连接（可跳过） ----------------

@Composable
private fun ServerStep(
    form: WizardViewModel.ServerForm,
    serverInfo: ServerInfo?,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onTest: () -> Unit,
    onSkip: () -> Unit,
) {
    StepLabel("服务器连接")
    Text(
        "连接 LANraragi 服务器以同步书架与阅读进度；也可以先跳过，稍后在「设置 → 连接」中配置。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = form.url,
        onValueChange = onUrlChange,
        label = { Text("服务器地址") },
        placeholder = { Text("manga.example.com") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = form.key,
        onValueChange = onKeyChange,
        label = { Text("API Key") },
        placeholder = { Text("默认为 LANraragi") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = form.name,
        onValueChange = onNameChange,
        label = { Text("服务器名称（可选）") },
        placeholder = { Text("测试成功后自动回填") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
    )
    form.message?.let {
        Spacer(Modifier.height(12.dp))
        Text(
            it,
            color = if (form.success) Color(SUCCESS_GREEN) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (form.testedOk && serverInfo != null) {
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = "连接成功",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("服务器信息", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                ServerInfoLine("名称", serverInfo.name.ifBlank { "（未命名）" })
                if (serverInfo.motd.isNotBlank()) ServerInfoLine("公告", serverInfo.motd)
                ServerInfoLine("版本", serverInfo.version_name.ifBlank { serverInfo.version }.ifBlank { "未知" })
                ServerInfoLine("密码保护", if (serverInfo.has_password) "已启用" else "未启用")
                ServerInfoLine("进度跟踪", if (serverInfo.server_tracks_progress) "服务器支持" else "服务器不支持")
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(
        onClick = onTest,
        enabled = !form.testing && !form.saving && form.url.isNotBlank(),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (form.testing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("测试中…")
        } else {
            Text("测试连接")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onSkip) { Text("跳过，先使用本地书架") }
    }
}

@Composable
private fun ServerInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

// ---------------- Step5 收尾 ----------------

@Composable
private fun FinishStep(onOpenGuide: () -> Unit, onPickBackup: () -> Unit) {
    StepLabel("准备完成")
    Text(
        "一切就绪！以下功能也可以稍后在应用内随时使用。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    ActionCard(
        icon = Icons.AutoMirrored.Filled.MenuBook,
        iconDescription = "入门指南",
        title = "入门指南",
        description = "快速了解连接服务器、导入本地漫画、阅读器手势与元数据刮削。",
        onClick = onOpenGuide,
    )
    Spacer(Modifier.height(12.dp))
    ActionCard(
        icon = Icons.Filled.Restore,
        iconDescription = "还原备份",
        title = "还原备份",
        description = "从备份 JSON 文件还原阅读偏好与界面设置（不含服务器密钥）。",
        onClick = onPickBackup,
    )
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    iconDescription: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = iconDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "打开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
