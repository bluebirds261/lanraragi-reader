package com.lanraragi.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lanraragi.reader.ui.AppRoot
import com.lanraragi.reader.ui.SplashCoverOverlay
import com.lanraragi.reader.ui.screens.DeepLinkBus
import com.lanraragi.reader.ui.theme.LanraragiReaderTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    /** 本次会话是否已通过认证 */
    private val isUnlocked = MutableStateFlow(false)

    /** 每次回到前台自增，用于在锁屏占位页重新触发认证弹窗 */
    private var resumeTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 动态申请通知权限，保证后台下载进度通知可见
        requestNotificationPermission()

        val container = (application as LanraragiApplication).container

        // 核心优化：在 Activity 级别直接监听设置与解锁状态，第一时间应用隐藏预览逻辑。
        // 这样做可以绕过 Compose 渲染循环，极大加快对“切后台”动作的响应速度。
        lifecycleScope.launch {
            combine(
                container.settingsRepository.settings,
                isUnlocked
            ) { settings, unlocked ->
                val authActive = settings.authEnabled || settings.biometricEnabled
                val maskRecents = settings.blurInRecents || (authActive && !unlocked)
                maskRecents to settings.screenshotProtectionEnabled
            }.collect { (maskRecents, screenshotProtection) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setRecentsScreenshotEnabled(!maskRecents)
                    if (screenshotProtection) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                } else if (screenshotProtection) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val unlocked by isUnlocked.collectAsStateWithLifecycle()

            val darkTheme = when (settings?.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            LanraragiReaderTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor == true,
            ) {
                val authEnabled = settings?.authEnabled == true
                val biometricEnabled = settings?.biometricEnabled == true
                val needsAuth = authEnabled || biometricEnabled

                // 开屏封面（若已设置）盖在最上层：冷启动约 2 秒后淡出、点按可跳过。
                // 放在这个位置是为了让它同时盖住「设置加载中的 logo 占位」与「身份验证占位页」，
                // 避免先闪一下 logo 再出现封面。
                Box(Modifier.fillMaxSize()) {
                when {
                    // 设置尚未加载：显示 logo 淡入 splash
                    settings == null -> {
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = visible,
                                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(600))
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = "LANraragi Reader",
                                    modifier = Modifier.size(96.dp),
                                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                                )
                            }
                        }
                    }

                    // 需要认证且本次会话未解锁：显示锁屏占位并弹出系统认证
                    needsAuth && !unlocked -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "请完成身份验证以继续",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // resumeTick 变化（冷启动 / 每次回到前台）都会重新触发认证
                        LaunchedEffect(resumeTick) {
                            if (!unlocked) {
                                showAuthPrompt(
                                    allowBiometric = biometricEnabled,
                                    allowCredential = authEnabled,
                                )
                            }
                        }
                    }

                    else -> {
                        // E7 深链/分享：解析 intent 中的 archive URL
                        LaunchedEffect(Unit) {
                            val arcid = extractArcIdFromIntent(intent)
                            if (arcid != null) DeepLinkBus.arcid.value = arcid
                        }
                        AppRoot()
                    }
                }

                    SplashCoverOverlay(container)
                }
            }
        }
    }

    /**
     * 弹出系统认证：
     * - 仅开启密码验证 → 系统解锁界面（PIN / 图案 / 密码）
     * - 仅开启生物认证 → 指纹等强生物认证（设备不支持时回退到系统解锁）
     * - 两者都开 → 优先指纹，可回退系统解锁
     */
    private fun showAuthPrompt(allowBiometric: Boolean, allowCredential: Boolean) {
        if (isUnlocked.value) return

        var authenticators = 0
        if (allowBiometric) {
            authenticators = authenticators or BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        if (allowCredential) {
            authenticators = authenticators or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        if (authenticators == 0) {
            isUnlocked.value = true
            return
        }

        // 检查设备上是否有可用的认证硬件与凭据
        val biometricManager = BiometricManager.from(this)
        val authResult = biometricManager.canAuthenticate(authenticators)
        
        if (authResult != BiometricManager.BIOMETRIC_SUCCESS) {
            // 如果请求的认证方式不可用（例如：设备没有指纹传感器，或用户未设置 PIN 码），
            // 直接放行，防止用户被锁死在应用外。
            isUnlocked.value = true
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isUnlocked.value = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // 常见的错误：用户取消、超时、指纹多次尝试失败。
                // 认证失败或取消时不设 isUnlocked，让锁定界面维持显示。
            }
        }

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("安全认证")
            .setSubtitle("请完成验证以继续使用 LANraragi Reader")
            .setAllowedAuthenticators(authenticators)

        // 核心修复：仅使用生物识别（不含系统密码）时，必须提供 negativeButton。
        // 如果 authenticators 包含 DEVICE_CREDENTIAL，则禁止调用 setNegativeButtonText，
        // 否则会抛出 IllegalArgumentException。
        val hasCredential = (authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0
        if (!hasCredential) {
            promptBuilder.setNegativeButtonText("取消")
        }

        try {
            val promptInfo = promptBuilder.build()
            val biometricPrompt = BiometricPrompt(this, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // 万一配置仍有问题或底层抛异常，放行以防应用崩溃/不可用
            isUnlocked.value = true
        }
    }

    /** Android 13+（API 33）动态申请通知权限，保证后台下载进度通知可见。 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        resumeTick++
    }

    override fun onStop() {
        super.onStop()
        // 离开应用时立即重新锁定，确保下次切回时第一时间隐藏内容
        isUnlocked.value = false
        // 阅读进度兜底回传：切后台后进程可能被系统回收，来不及走阅读器的退出路径，
        // 这里在应用级作用域补推一次（失败保留在 outbox，下次启动继续重试）。
        val container = (application as LanraragiApplication).container
        container.applicationScope.launch {
            try {
                container.progressWriter.flush()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 忽略：待回传任务仍留在 outbox。
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkBus.arcid.value = extractArcIdFromIntent(intent)
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 2001
    }

    /** E7：从 intent 中提取档案 arcid（URL 路径 /api/archives/<sha1> 或文本中的链接） */
    private fun extractArcIdFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.dataString
            ?: intent.data?.toString()
            ?: return null
        // 匹配 40 位 hex arcid（/archives/<arcid> 或 /api/archives/<arcid>）
        val re = Regex("archives/([0-9a-fA-F]{40})")
        return re.find(text)?.groupValues?.getOrNull(1)
    }
}
