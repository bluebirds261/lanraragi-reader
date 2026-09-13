package com.lanraragi.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 开屏封面（用户自选的一张图片，冷启动时全屏展示 [DISPLAY_DURATION_MS]）。
 *
 * **为什么是「拷进私有目录」而不是记住图库 URI：**
 * 系统照片选择器（`PickVisualMedia`）返回的 URI 只在当次授权内可读，无法
 * `takePersistableUriPermission`；若只记 URI，第二次冷启动就读不到了。因此选图后立刻把
 * 图片落盘到 `filesDir/splash/cover.jpg`：不依赖任何运行时权限，也不怕用户在图库里删原图。
 *
 * **落盘格式与尺寸：**
 * - API 28+（`ImageDecoder`）：按 EXIF 摆正后缩到最长边 [MAX_EDGE_PX]，再以 JPEG 落盘。
 *   相册原图动辄 4–10 MB、4000px 级，开屏只需要铺满屏幕；缩到 1600px 后通常 200–600 KB，
 *   冷启动解码更快，也不会在私有目录里压着一张大图。
 * - API 26/27：不做二次编码（`BitmapFactory` 不认 EXIF，重编码反而会把照片转错方向），
 *   原样落盘，展示时由 Coil 按 EXIF 摆正（Coil 的解码器会处理方向）。
 *
 * **写入是「先写临时文件再改名」**：中途失败（解码报错、空间不足）不会破坏已有封面，
 * 用户仍能看到上一张。
 */
class SplashCoverStore(private val context: Context) {

    companion object {
        /**
         * 开屏停留时长。取 2.0 秒：短于 1 秒基本看不清画面、等于白设；长于 3 秒开始明显挡手。
         * 2 秒足够看清且不打断操作，并且随时可以点按跳过。
         */
        const val DISPLAY_DURATION_MS = 2_000L

        /** 落盘最长边（px）。1080–1440 宽的手机屏幕上足够清晰，又能把体积压到几百 KB。 */
        private const val MAX_EDGE_PX = 1_600

        private const val JPEG_QUALITY = 92
        internal const val DIR_NAME = "splash"
        internal const val FILE_NAME = "cover.jpg"
        private const val TEMP_FILE_NAME = "$FILE_NAME.tmp"

        /**
         * 按最长边等比缩放的目标尺寸。抽成纯函数以便在 JVM 单测里直接验证（不依赖 Android 运行时）。
         * 已经小于上限时原样返回，绝不放大。
         */
        internal fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
            if (width <= 0 || height <= 0) return 0 to 0
            val longest = maxOf(width, height)
            if (longest <= maxEdge) return width to height
            val scale = maxEdge.toFloat() / longest
            return (width * scale).roundToInt().coerceAtLeast(1) to
                (height * scale).roundToInt().coerceAtLeast(1)
        }
    }

    private val directory = File(context.filesDir, DIR_NAME)
    private val coverFile = File(directory, FILE_NAME)
    private val tempFile = File(directory, TEMP_FILE_NAME)

    /**
     * 封面版本：`0` 表示未设置，非 0 表示已设置。
     *
     * 值取文件的最后修改时间，直接当作 Coil 的缓存键版本使用——文件名固定不变，
     * 若不换键，「更换封面」后 Coil 会继续命中内存缓存里的旧图。
     */
    private val _version = MutableStateFlow(readVersion())
    val version: StateFlow<Long> = _version.asStateFlow()

    /** 供 Coil 加载的本地文件；未设置时该文件不存在。 */
    fun coverFile(): File = coverFile

    /** 从图库选中的图片导入为开屏封面。失败时保持原有封面不变。 */
    suspend fun importFrom(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            tempFile.delete()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                writeDownscaled(uri)
            } else {
                writeOriginal(uri)
            }
            if (tempFile.length() <= 0L) error("图片内容为空")
            replaceCover()
            _version.value = readVersion()
        }
    }

    /** 移除开屏封面（下一次冷启动不再有开屏图）。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        tempFile.delete()
        coverFile.delete()
        _version.value = readVersion()
    }

    private fun replaceCover() {
        if (tempFile.renameTo(coverFile)) return
        // 个别设备上 rename 可能因目标已存在而失败：退回「复制 + 删除」。
        tempFile.copyTo(coverFile, overwrite = true)
        tempFile.delete()
    }

    private fun readVersion(): Long = if (coverFile.isFile) coverFile.lastModified() else 0L

    /** API 28+：解码时按 EXIF 摆正并等比缩到上限，再落 JPEG。 */
    private fun writeDownscaled(uri: Uri) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // 硬件位图不能 compress，必须显式要软件位图。
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (width, height) = scaledSize(info.size.width, info.size.height, MAX_EDGE_PX)
            if (width > 0 && height > 0 && (width != info.size.width || height != info.size.height)) {
                decoder.setTargetSize(width, height)
            }
        }
        try {
            tempFile.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    error("图片编码失败")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** API 26/27：原样落盘，方向交给展示端的 Coil 处理。 */
    private fun writeOriginal(uri: Uri) {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选图片")
        input.use { source ->
            tempFile.outputStream().use { target -> source.copyTo(target) }
        }
    }
}
