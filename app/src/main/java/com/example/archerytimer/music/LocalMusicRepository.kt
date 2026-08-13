package com.example.archerytimer.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.example.archerytimer.communication.MusicTrackMetadata

data class LocalTrack(val metadata: MusicTrackMetadata, val uri: android.net.Uri)

class LocalMusicRepository(private val context: Context) {
    private val tracksById = linkedMapOf<Long, LocalTrack>()

    fun loadTracks(): Result<List<MusicTrackMetadata>> = runCatching {
        if (!hasPermission()) error("未授予本地音频读取权限")
        tracksById.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val metadata = MusicTrackMetadata(
                    trackId = id,
                    title = cursor.getString(titleColumn) ?: "未知歌曲",
                    artist = cursor.getString(artistColumn) ?: "未知艺术家",
                    durationMs = cursor.getLong(durationColumn),
                )
                tracksById[id] = LocalTrack(metadata, ContentUris.withAppendedId(collection, id))
            }
        }
        tracksById.values.map(LocalTrack::metadata)
    }

    fun find(trackId: Long): LocalTrack? = tracksById[trackId]

    fun allTracks(): List<LocalTrack> = tracksById.values.toList()

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
