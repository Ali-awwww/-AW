package com.example.media

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryPersistence(context: Context) {

    private val prefs = context.getSharedPreferences("media_player_storage", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "playback_history_json"
        private const val KEY_DOWNLOADED_ITEMS = "downloaded_media_json"
        private const val KEY_VIDEOS = "library_videos_json"
        private const val KEY_AUDIOS = "library_audios_json"
        private const val KEY_PLAYLISTS = "playlists_json"
        private const val KEY_DOWNLOAD_TASKS = "download_tasks_json"
        private const val MAX_HISTORY = 30
    }

    fun loadHistory(): List<PlaybackHistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val itemObj = obj.getJSONObject("item")
                    add(
                        PlaybackHistoryEntry(
                            item = itemObj.toMediaItem(),
                            lastPositionMs = obj.optLong("lastPositionMs", 0L),
                            durationMs = obj.optLong("durationMs", 0L),
                            playedAt = obj.optLong("playedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHistory(entries: List<PlaybackHistoryEntry>) {
        val arr = JSONArray()
        entries.take(MAX_HISTORY).forEach { entry ->
            val obj = JSONObject().apply {
                put("item", entry.item.toJson())
                put("lastPositionMs", entry.lastPositionMs)
                put("durationMs", entry.durationMs)
                put("playedAt", entry.playedAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun loadDownloadedMedia(): List<MediaItemData> = loadMediaList(KEY_DOWNLOADED_ITEMS)

    fun saveDownloadedMedia(items: List<MediaItemData>) = saveMediaList(KEY_DOWNLOADED_ITEMS, items)

    fun loadVideos(): List<MediaItemData> = loadMediaList(KEY_VIDEOS)

    fun saveVideos(items: List<MediaItemData>) = saveMediaList(KEY_VIDEOS, items)

    fun loadAudios(): List<MediaItemData> = loadMediaList(KEY_AUDIOS)

    fun saveAudios(items: List<MediaItemData>) = saveMediaList(KEY_AUDIOS, items)

    fun loadPlaylists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                    val items = buildList {
                        for (j in 0 until itemsArr.length()) {
                            add(itemsArr.getJSONObject(j).toMediaItem())
                        }
                    }
                    val cpObj = obj.optJSONObject("checkpoint")
                    val checkpoint = cpObj?.let {
                        PlaylistCheckpoint(
                            playlistId = it.optString("playlistId"),
                            trackId = it.optString("trackId"),
                            trackTitle = it.optString("trackTitle"),
                            positionMs = it.optLong("positionMs", 0L),
                            durationMs = it.optLong("durationMs", 0L),
                            updatedAt = it.optLong("updatedAt", 0L)
                        )
                    }
                    add(
                        Playlist(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description", ""),
                            items = items,
                            checkpoint = checkpoint,
                            sourceTreeUri = obj.optString("sourceTreeUri").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val itemsArr = JSONArray().apply {
                pl.items.forEach { put(it.toJson()) }
            }
            val cpObj = pl.checkpoint?.let { cp ->
                JSONObject().apply {
                    put("playlistId", cp.playlistId)
                    put("trackId", cp.trackId)
                    put("trackTitle", cp.trackTitle)
                    put("positionMs", cp.positionMs)
                    put("durationMs", cp.durationMs)
                    put("updatedAt", cp.updatedAt)
                }
            }
            val plObj = JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("description", pl.description)
                put("items", itemsArr)
                put("checkpoint", cpObj)
                put("sourceTreeUri", pl.sourceTreeUri ?: "")
            }
            arr.put(plObj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun loadDownloads(): List<DownloadItem> {
        val raw = prefs.getString(KEY_DOWNLOAD_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val savedStatus = runCatching {
                        DownloadStatus.valueOf(obj.optString("status"))
                    }.getOrDefault(DownloadStatus.FAILED)
                    // Recover interrupted in-flight tasks cleanly as PAUSED after process kill
                    val restoredStatus = when (savedStatus) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DownloadStatus.PAUSED
                        else -> savedStatus
                    }
                    add(
                        DownloadItem(
                            id = obj.optString("id"),
                            url = obj.optString("url"),
                            fileName = obj.optString("fileName"),
                            progress = obj.optDouble("progress", 0.0).toFloat(),
                            bytesDownloaded = obj.optLong("bytesDownloaded", 0L),
                            totalBytes = obj.optLong("totalBytes", -1L),
                            speedBytesPerSec = 0L,
                            status = restoredStatus,
                            mediaType = runCatching {
                                MediaType.valueOf(obj.optString("mediaType"))
                            }.getOrDefault(MediaType.VIDEO),
                            savedUri = obj.optString("savedUri").takeIf { it.isNotBlank() },
                            errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                            supportsResume = obj.optBoolean("supportsResume", true),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDownloads(items: List<DownloadItem>) {
        val arr = JSONArray()
        items.take(50).forEach { dl ->
            val obj = JSONObject().apply {
                put("id", dl.id)
                put("url", dl.url)
                put("fileName", dl.fileName)
                put("progress", dl.progress.toDouble())
                put("bytesDownloaded", dl.bytesDownloaded)
                put("totalBytes", dl.totalBytes)
                put("status", dl.status.name)
                put("mediaType", dl.mediaType.name)
                put("savedUri", dl.savedUri ?: "")
                put("errorMessage", dl.errorMessage ?: "")
                put("supportsResume", dl.supportsResume)
                put("timestamp", dl.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DOWNLOAD_TASKS, arr.toString()).apply()
    }

    private fun loadMediaList(key: String): List<MediaItemData> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getJSONObject(i).toMediaItem())
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMediaList(key: String, items: List<MediaItemData>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun MediaItemData.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artistOrDesc", artistOrDesc)
        put("mediaUri", mediaUri)
        put("artworkUri", artworkUri)
        put("durationMs", durationMs)
        put("mediaType", mediaType.name)
        put("category", category)
        put("isFavorite", isFavorite)
        put("isLocal", isLocal)
        put("subtitleUri", subtitleUri ?: "")
    }

    private fun JSONObject.toMediaItem(): MediaItemData = MediaItemData(
        id = optString("id"),
        title = optString("title"),
        artistOrDesc = optString("artistOrDesc"),
        mediaUri = optString("mediaUri"),
        artworkUri = optString("artworkUri"),
        durationMs = optLong("durationMs", 0L),
        mediaType = runCatching { MediaType.valueOf(optString("mediaType")) }.getOrDefault(MediaType.AUDIO),
        category = optString("category", "General"),
        isFavorite = optBoolean("isFavorite", false),
        isLocal = optBoolean("isLocal", false),
        subtitleUri = optString("subtitleUri").takeIf { it.isNotBlank() }
    )
}
