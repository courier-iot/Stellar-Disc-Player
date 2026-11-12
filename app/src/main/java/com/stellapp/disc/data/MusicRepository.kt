package com.stellapp.disc.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.stellapp.disc.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicRepository {

    private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

    fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

    /**
     * All music on the device, plus videos. With [allVideos] every video on the phone is included,
     * otherwise only the ones saved into Movies/Stellar Disc.
     */
    suspend fun loadSongs(context: Context, allVideos: Boolean): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        out += try { loadAudio(context) } catch (e: Exception) { emptyList() }
        out += try { loadVideos(context, allVideos) } catch (e: Exception) { emptyList() }
        out
    }

    @Suppress("DEPRECATION")
    private fun loadAudio(context: Context): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val hasGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = listOfNotNull(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            if (hasGenre) MediaStore.Audio.Media.GENRE else null,
        ).toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val out = ArrayList<Song>()
        val appFolder = "/${AppInfo.FOLDER}/"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val genreCol = if (hasGenre) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

            while (c.moveToNext()) {
                val duration = c.getLong(durCol)
                if (duration in 1 until 5_000) continue // skip tiny clips and blips
                val id = c.getLong(idCol)
                val path = c.getString(dataCol) ?: ""
                out += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol).clean("Unknown signal"),
                    artist = c.getString(artistCol).clean("Unknown artist"),
                    album = c.getString(albumCol).clean("Unknown album"),
                    albumId = c.getLong(albumIdCol),
                    durationMs = duration,
                    track = c.getInt(trackCol),
                    dateAdded = c.getLong(addedCol),
                    size = c.getLong(sizeCol),
                    path = path,
                    inAppFolder = path.contains(appFolder),
                    genre = if (genreCol >= 0) (c.getString(genreCol) ?: "").trim() else "",
                )
            }
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun loadVideos(context: Context, allVideos: Boolean): List<Song> {
        val collection: Uri
        var selection: String? = null
        var args: Array<String>? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            if (!allVideos) {
                selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                args = arrayOf("${Environment.DIRECTORY_MOVIES}/${AppInfo.FOLDER}%")
            }
        } else {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            if (!allVideos) {
                selection = "${MediaStore.Video.Media.DATA} LIKE ?"
                args = arrayOf("%/${Environment.DIRECTORY_MOVIES}/${AppInfo.FOLDER}/%")
            }
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
        )
        val appFolder = "/${Environment.DIRECTORY_MOVIES}/${AppInfo.FOLDER}/"
        val out = ArrayList<Song>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(dataCol) ?: ""
                val own = path.contains(appFolder) || !allVideos
                out += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol).clean("Unknown signal"),
                    artist = c.getString(artistCol).clean(if (own) "Unknown artist" else "Video"),
                    album = if (own) "${AppInfo.FOLDER} videos" else path.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Videos" },
                    albumId = VIDEO_ALBUM_ID,
                    durationMs = c.getLong(durCol),
                    track = 0,
                    dateAdded = c.getLong(addedCol),
                    isVideo = true,
                    size = c.getLong(sizeCol),
                    path = path,
                    inAppFolder = own,
                )
            }
        }
        return out
    }

    private fun String?.clean(fallback: String): String =
        if (this.isNullOrBlank() || this == "<unknown>") fallback else this
}
