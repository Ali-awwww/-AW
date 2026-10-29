package com.example.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
        
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MEDIA_TYPE = "media_type"
        
        const val PROGRESS = "progress"
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "download_${System.currentTimeMillis()}"
        val mediaType = inputData.getString(KEY_MEDIA_TYPE) ?: "VIDEO"

        setForeground(createForegroundInfo(0, fileName))

        return try {
            downloadFile(url, fileName)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun downloadFile(url: String, fileName: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("Failed to download file: $response")

        val body = response.body ?: throw Exception("Response body is null")
        val totalBytes = body.contentLength()
        val inputStream = body.byteStream()

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        
        val file = File(downloadsDir, fileName)
        val outputStream = FileOutputStream(file)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L

        inputStream.use { input ->
            outputStream.use { output ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalBytes > 0) {
                        val progress = (totalBytesRead * 100 / totalBytes).toInt()
                        updateProgress(progress, fileName)
                    }
                }
            }
        }
    }

    private fun updateProgress(progress: Int, fileName: String) {
        setProgressAsync(workDataOf(PROGRESS to progress))
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(progress, fileName)
        )
    }

    private fun createForegroundInfo(progress: Int, fileName: String): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification(progress, fileName))
    }

    private fun createNotification(progress: Int, fileName: String) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("جاري تحميل: $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Notifications for media downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    init {
        createChannel()
    }
}
