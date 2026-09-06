package com.lanraragi.reader.data

import android.content.Context
import java.io.File

/**
 * F4 数据迁移框架：为每个 DataStore 提供 schema_version 版本链。
 *
 * 各 Repository 在 init 里用 [register] 登记自己负责的迁移步骤（纯数据搬运），
 * 启动时调用 [runPending] 按版本递增执行所有未跑过的步骤。每个步骤执行前先做
 * best-effort 文件备份（filesDir/datastore → filesDir/migration_backups/v<N>/），
 * 保证旧数据可回退、不因迁移丢失。
 */
object DataMigration {

    /** version → 迁移步骤（步骤闭包内自行读写所属 DataStore）。 */
    private val steps = mutableMapOf<Int, suspend () -> Unit>()

    fun register(version: Int, step: suspend () -> Unit) {
        steps[version] = step
    }

    fun latestVersion(): Int = steps.keys.maxOrNull() ?: 1

    /**
     * 按版本链执行所有 > currentVersion 的已登记迁移，返回迁移后版本号。
     * 步骤抛异常时中断并向上传播（由调用方决定重试），已备份的旧文件保留。
     */
    suspend fun runPending(context: Context, currentVersion: Int): Int {
        var v = currentVersion
        for (target in (currentVersion + 1)..latestVersion()) {
            val step = steps[target] ?: continue
            backup(context, target)
            step()
            v = target
        }
        return v
    }

    /** 迁移前把 filesDir/datastore 下所有文件复制到 migration_backups/v<N>/，best-effort。 */
    private fun backup(context: Context, version: Int) {
        runCatching {
            val dsDir = File(context.filesDir, "datastore")
            if (!dsDir.exists()) return@runCatching
            val outDir = File(context.filesDir, "migration_backups/v$version")
            outDir.mkdirs()
            dsDir.listFiles()?.forEach { f ->
                runCatching { f.copyTo(File(outDir, f.name), overwrite = true) }
            }
        }
    }
}
