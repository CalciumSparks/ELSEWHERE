package com.elsewhere.location.data

import android.content.Context
import com.elsewhere.location.core.CacheBackupRecord
import com.elsewhere.location.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps best-effort snapshots of a target app's *external* cache and writes
 * them back. Android does not allow a shell-level Shizuku process to read
 * another app's private internal cache, so this never pretends to be a full
 * application-data backup.
 *
 * Cache clearing itself is performed by the user in the system App info page;
 * a shell identity does not hold INTERNAL_DELETE_CACHE_FILES on modern Android.
 */
class CacheBackupRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val shizukuManager: ShizukuManager,
) {

    private fun backupDirectory(): File? {
        val dir = context.getExternalFilesDir("cache-backups") ?: return null
        return dir.takeIf { it.exists() || it.mkdirs() }
    }

    private fun targetExternalRoot(packageName: String): File? {
        val ownFiles = context.getExternalFilesDir(null) ?: return null
        val androidData = ownFiles.parentFile?.parentFile ?: return null
        return File(androidData, packageName)
    }

    fun backupFile(record: CacheBackupRecord): File? {
        val dir = backupDirectory() ?: return null
        return File(dir, record.fileName).takeIf { it.exists() && it.isFile }
    }

        suspend fun restore(record: CacheBackupRecord): Result<Unit> = withContext(Dispatchers.IO) {
        if (!shizukuManager.status.value.ready) {
            return@withContext Result.failure(IllegalStateException("Shizuku 服务未就绪。"))
        }
        val file = backupFile(record)
            ?: return@withContext Result.failure(IllegalStateException("备份文件已不存在。"))
        val targetRoot = targetExternalRoot(record.packageName)
            ?: return@withContext Result.failure(IllegalStateException("无法解析外部存储目录。"))
        shizukuManager.forceStop(record.packageName)
        val command = buildString {
            append("mkdir -p ").append(shellQuote(targetRoot.absolutePath)).append(" && ")
            append("tar -xzf ").append(shellQuote(file.absolutePath))
            append(" -C ").append(shellQuote(targetRoot.absolutePath))
            append(" && chmod -R a+rwX ").append(shellQuote(File(targetRoot, "cache").absolutePath))
        }
        val result = shizukuManager.runShell(command, timeoutMs = 120_000)
        if (result.success) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    result.output.ifBlank { "恢复失败，Shizuku 未返回结果。" },
                ),
            )
        }
    }

    fun backupSize(record: CacheBackupRecord): Long = backupFile(record)?.length() ?: 0L

    suspend fun backupExternalCache(app: TargetAppInfo): CacheBackupRecord? {
        val dir = backupDirectory() ?: return null
        val targetRoot = targetExternalRoot(app.packageName) ?: return null
        val targetCache = File(targetRoot, "cache")

        val fileName = "${app.packageName}-${System.currentTimeMillis()}.tgz"
        val destination = File(dir, fileName)
        val command = buildString {
            append("if [ -d ").append(shellQuote(targetCache.absolutePath)).append(" ]; then ")
            append("tar -czf ").append(shellQuote(destination.absolutePath))
            append(" -C ").append(shellQuote(targetRoot.absolutePath)).append(" cache; ")
            append("echo BACKUP_DONE; else echo NO_EXTERNAL_CACHE; fi")
        }
        val result = shizukuManager.runShell(command, timeoutMs = 120_000)
        if (!result.success || !destination.exists() || destination.length() <= 0) return null
        val record = CacheBackupRecord(
            id = "backup-${System.currentTimeMillis()}",
            packageName = app.packageName,
            appLabel = app.label,
            fileName = fileName,
            sizeBytes = destination.length(),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        replaceBackupRecord(record)
        return record
    }

    private suspend fun replaceBackupRecord(record: CacheBackupRecord) {
        runCatching {
            settingsRepository.update { current ->
                current.backups
                    .filter { it.packageName == record.packageName }
                    .forEach { old -> backupFile(old)?.delete() }
                current.copy(
                    backups = current.backups.filterNot { it.packageName == record.packageName } + record,
                    lastBackupAtEpochMs = record.createdAtEpochMs,
                )
            }
        }
    }

    fun backupForPackage(packageName: String, backups: List<CacheBackupRecord>): CacheBackupRecord? =
        backups.filter { it.packageName == packageName }
            .maxByOrNull { it.createdAtEpochMs }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
