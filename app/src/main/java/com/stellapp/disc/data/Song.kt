package com.stellapp.disc.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** All downloaded videos are grouped under this pseudo-album. */
const val VIDEO_ALBUM_ID = -5L

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val dateAdded: Long,
    val isVideo: Boolean = false,
    /** File size in bytes, used by the Swap song checker. */
    val size: Long = 0L,
    /** Full file path, used for folders and M3U playlists. */
    val path: String = "",
    /** Saved by Uplink or Swap into a Stellar Disc folder. */
    val inAppFolder: Boolean = false,
    /** Set for tracks streamed with Uplink (not downloaded): the original page link. */
    val streamPage: String? = null,
    val artUrl: String? = null,
    val streamMime: String? = null,
) {
    /** Key for cover-art caching: per album for music, per file for videos. */
    val artKey: Long get() = if (isVideo) -1000L - id else albumId

    val folder: String get() = path.substringBeforeLast('/', "")

    val isStream: Boolean get() = streamPage != null

    /** Music lists show audio plus videos you downloaded, but not every phone video. */
    val showsInMusic: Boolean get() = !isVideo || inAppFolder
}

fun Song.toMediaItem(): MediaItem {
    val art: Uri? = when {
        MetaStore.hasCover(artKey) -> MetaStore.coverFile(artKey)?.let { Uri.fromFile(it) }
        artUrl != null -> Uri.parse(artUrl)
        !isVideo && albumId >= 0 -> MusicRepository.albumArtUri(albumId)
        else -> null
    }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply { if (art != null) setArtworkUri(art) }
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .apply { if (streamMime != null) setMimeType(streamMime) }
        .setMediaMetadata(meta)
        .build()
}

/** Fallback used when the player holds a track the library hasn't loaded yet. */
fun MediaItem.toSong(): Song = Song(
    id = mediaId.toLongOrNull() ?: -1L,
    uri = localConfiguration?.uri ?: Uri.EMPTY,
    title = mediaMetadata.title?.toString() ?: "Unknown signal",
    artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
    album = mediaMetadata.albumTitle?.toString() ?: "Unknown album",
    albumId = -1L,
    durationMs = 0L,
    track = 0,
    dateAdded = 0L,
)
