package com.example.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_PROGRESS_ID = "download_progress_channel"
        const val CHANNEL_COMPLETE_ID = "download_complete_channel"
        const val BASE_NOTIFICATION_ID = 1100

        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MEDIA_TYPE = "media_type"

        const val PROGRESS = "progress"
        const val KEY_BYTES_READ = "bytes_read"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED_BPS = "speed_bps"
        const val KEY_SAVED_URI = "saved_uri"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"
        const val PREFS_DOWNLOADS = "media_player_downloads_prefs"
        const val PAUSED_FLAG_PREFIX = "paused_"
    }

    init {
        createChannels()
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: id.toString()
        val url = inputData.getString(KEY_URL) ?: return Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to "رابط التحميل غير صالح")
        )
        val rawFileName = inputData.getString(KEY_FILE_NAME) ?: "media_${System.currentTimeMillis()}"
        val fileName = sanitizeFileName(rawFileName)
        val mediaType = inputData.getString(KEY_MEDIA_TYPE) ?: MediaType.VIDEO.name
        val notificationId = BASE_NOTIFICATION_ID + (downloadId.hashCode() and 0x0FFF)

        val prefs = applicationContext.getSharedPreferences(PREFS_DOWNLOADS, Context.MODE_PRIVATE)
        prefs.edit().remove("$PAUSED_FLAG_PREFIX$downloadId").apply()

        try {
            setForeground(
                createForegroundInfo(
                    notificationId = notificationId,
                    progress = 0,
                    indeterminate = true,
                    fileName = fileName,
                    subtitle = "جاري الاتصال بالخادم..."
                )
            )
        } catch (_: Exception) {
            // Continue safely even if OS restricts FGS promotion on background start
        }

        return try {
            val outcome = downloadToScopedStorage(
                downloadId = downloadId,
                url = url,
                fileName = fileName,
                mediaType = mediaType,
                notificationId = notificationId
            )
            val isPaused = prefs.getBoolean("$PAUSED_FLAG_PREFIX$downloadId", false)
            if (isStopped && isPaused) {
                notificationManager.cancel(notificationId)
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "PAUSED"))
            }
            if (isStopped) {
                notificationManager.cancel(notificationId)
                deletePartFile(downloadId)
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "تم إلغاء التحميل"))
            }

            notificationManager.cancel(notificationId)
            showCompletionNotification(notificationId + 5000, fileName, outcome.savedUri)

            Result.success(
                workDataOf(
                    KEY_SAVED_URI to outcome.savedUri,
                    KEY_FILE_NAME to fileName,
                    KEY_BYTES_READ to outcome.totalBytes,
                    KEY_TOTAL_BYTES to outcome.totalBytes,
                    KEY_MIME_TYPE to outcome.mimeType
                )
            )
        } catch (e: Exception) {
            val isPaused = prefs.getBoolean("$PAUSED_FLAG_PREFIX$downloadId", false)
            notificationManager.cancel(notificationId)
            if (isPaused) {
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "PAUSED"))
            } else {
                deletePartFile(downloadId)
                val msg = e.localizedMessage ?: "تعذر تحميل الملف"
                showFailureNotification(notificationId + 5000, fileName, msg)
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
            }
        }
    }

    private data class DownloadOutcome(
        val savedUri: String,
        val totalBytes: Long,
        val mimeType: String
    )

    private fun downloadToScopedStorage(
        downloadId: String,
        url: String,
        fileName: String,
        mediaType: String,
        notificationId: Int
    ): DownloadOutcome {
        val partialDir = File(applicationContext.cacheDir, "partial_downloads").apply {
            if (!exists()) mkdirs()
        }
        val partFile = File(partialDir, "$downloadId.part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "MediaPlayerAndroid/1.0")
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("خطأ من الخادم: HTTP ${resp.code}")
            }

            val body = resp.body ?: throw IllegalStateException("لا توجد بيانات في الاستجابة")
            val contentType = resp.header("Content-Type")?.substringBefore(";")?.trim()
            if (contentType != null && contentType.startsWith("text/html", ignoreCase = true)) {
                throw IllegalStateException("الرابط يشير إلى صفحة ويب وليس ملف وسائط مباشر")
            }

            val isResumed206 = resp.code == 206 && existingBytes > 0L
            val offsetBytes = if (isResumed206) existingBytes else 0L
            val bodyLength = body.contentLength()
            val totalBytes = if (bodyLength > 0) offsetBytes + bodyLength else -1L
            val resolvedMime = resolveMimeType(contentType, fileName, mediaType)

            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var totalBytesRead = offsetBytes
            var lastUpdateMs = System.currentTimeMillis()
            var lastBytesSnapshot = totalBytesRead

            body.byteStream().use { input ->
                FileOutputStream(partFile, isResumed206).use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            output.flush()
                            return DownloadOutcome("", totalBytesRead, resolvedMime)
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastUpdateMs
                        if (elapsed >= 400L) {
                            val deltaBytes = totalBytesRead - lastBytesSnapshot
                            val speedBps = if (elapsed > 0) (deltaBytes * 1000L) / elapsed else 0L
                            val progressPct = if (totalBytes > 0) {
                                ((totalBytesRead * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0

                            publishProgress(
                                notificationId = notificationId,
                                progress = progressPct,
                                indeterminate = totalBytes <= 0,
                                bytesRead = totalBytesRead,
                                totalBytes = totalBytes,
                                speedBps = speedBps,
                                fileName = fileName
                            )

                            lastUpdateMs = now
                            lastBytesSnapshot = totalBytesRead
                        }
                    }
                    output.flush()
                }
            }

            if (isStopped) {
                return DownloadOutcome("", totalBytesRead, resolvedMime)
            }

            val savedUri = saveFileToDownloads(partFile, fileName, resolvedMime)
            partFile.delete()
            return DownloadOutcome(savedUri, totalBytesRead, resolvedMime)
        }
    }

    private fun saveFileToDownloads(sourceFile: File, fileName: String, mimeType: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MediaPlayer")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("تعذر إنشاء الملف في مجلد التنزيلات")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("تعذر فتح مسار الحفظ")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                return uri.toString()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(publicDir, "MediaPlayer")
            val destDir = try {
                if (!targetDir.exists()) targetDir.mkdirs()
                if (targetDir.canWrite()) targetDir else {
                    applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: applicationContext.filesDir
                }
            } catch (_: Exception) {
                applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: applicationContext.filesDir
            }
            if (!destDir.exists()) destDir.mkdirs()
            val targetFile = File(destDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            return Uri.fromFile(targetFile).toString()
        }
    }

    private fun publishProgress(
        notificationId: Int,
        progress: Int,
        indeterminate: Boolean,
        bytesRead: Long,
        totalBytes: Long,
        speedBps: Long,
        fileName: String
    ) {
        setProgressAsync(
            workDataOf(
                PROGRESS to progress,
                KEY_BYTES_READ to bytesRead,
                KEY_TOTAL_BYTES to totalBytes,
                KEY_SPEED_BPS to speedBps
            )
        )

        val sizeText = if (totalBytes > 0) {
            "${formatBytes(bytesRead)} / ${formatBytes(totalBytes)}"
        } else {
            formatBytes(bytesRead)
        }
        val speedText = if (speedBps > 0) " • ${formatBytes(speedBps)}/ث" else ""
        val pctText = if (!indeterminate) "$progress% • " else ""
        val subtitle = "$pctText$sizeText$speedText"

        try {
            notificationManager.notify(
                notificationId,
                buildProgressNotification(progress, indeterminate, fileName, subtitle)
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+; continue download silently
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        progress: Int,
        indeterminate: Boolean,
        fileName: String,
        subtitle: String
    ): ForegroundInfo {
        val notification = buildProgressNotification(progress, indeterminate, fileName, subtitle)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildProgressNotification(
        progress: Int,
        indeterminate: Boolean,
        fileName: String,
        subtitle: String
    ) = NotificationCompat.Builder(applicationContext, CHANNEL_PROGRESS_ID)
        .setContentTitle("جاري تحميل: $fileName")
        .setContentText(subtitle)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress, indeterminate)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "إلغاء",
            WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        )
        .build()

    private fun showCompletionNotification(notificationId: Int, fileName: String, savedUri: String) {
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
            putExtra(KEY_SAVED_URI, savedUri)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETE_ID)
            .setContentTitle("اكتمل التحميل")
            .setContentText("$fileName • اضغط للتشغيل في المكتبة")
            .setSmallIcon(R.drawable.ic_stat_download)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    private fun showFailureNotification(notificationId: Int, fileName: String, reason: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETE_ID)
            .setContentTitle("فشل تحميل: $fileName")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    private fun deletePartFile(downloadId: String) {
        try {
            File(applicationContext.cacheDir, "partial_downloads/$downloadId.part").delete()
        } catch (_: Exception) {
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                applicationContext.getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات تقدم التحميل المباشر"
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                applicationContext.getString(R.string.download_complete_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات اكتمال أو تعذر التحميل"
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    private fun resolveMimeType(serverMime: String?, fileName: String, mediaType: String): String {
        if (!serverMime.isNullOrBlank() && serverMime != "application/octet-stream") {
            return serverMime
        }
        return when {
            fileName.endsWith(".mp3", true) -> "audio/mpeg"
            fileName.endsWith(".m4a", true) -> "audio/mp4"
            fileName.endsWith(".aac", true) -> "audio/aac"
            fileName.endsWith(".wav", true) -> "audio/wav"
            fileName.endsWith(".ogg", true) -> "audio/ogg"
            fileName.endsWith(".mkv", true) -> "video/x-matroska"
            fileName.endsWith(".webm", true) -> "video/webm"
            fileName.endsWith(".mp4", true) -> "video/mp4"
            mediaType == MediaType.AUDIO.name -> "audio/mpeg"
            else -> "video/mp4"
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "media_${System.currentTimeMillis()}.mp4" }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
