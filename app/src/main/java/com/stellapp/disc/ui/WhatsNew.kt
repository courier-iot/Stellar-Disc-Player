package com.stellapp.disc.ui

/** Shown once after each update. Keep the newest version first. */
object WhatsNew {
    val notes: Map<String, String> = linkedMapOf(
        "2.8.0" to """
            ADDED
            • Favourites: tap ♥ in Now Playing or a song's menu; they collect in a Favourites playlist
            • Volume levelling (Settings → Sound) so quiet and loud tracks play at a similar volume
            • Long tracks (20 min+) remember where you stopped
            • Bluetooth auto-play (Settings → Sound)
            • Undo after removing from the queue or a playlist, or deleting a playlist
            • Select several songs at once in the library (SELECT)
            • Uplink spots whether a link is a playlist or a single track and picks the right mode
            • Uplink "Wi-Fi only" downloads
            • Listening stats (Settings → Stats)
            • Gestures: swipe down to close Now Playing, swipe up on the disc for lyrics, double-tap to play/pause, long-press for the queue, slide the right edge for volume, swipe the mini player to skip or up to open
            • This "What's new" sheet

            CHANGED
            • Your YouTube sign-in is now encrypted on the phone
            • Nothing is sent to Android's cloud backup any more (use Settings → Backup)
            • yt-dlp options that can run programs are blocked
            • Swap turns itself off after 10 idle minutes, blocks repeated wrong codes, and resumes transfers if Wi-Fi drops
            • Crash reports leave out file names and paths
            • Deleted files go to your phone's trash (Android 11+)
        """.trimIndent(),
        "2.7.0" to """
            ADDED
            • A rolling 150-song random rotation
            • Downloads resume after the app closes
            • Landscape layout, predictive back, haptics

            CHANGED
            • Better battery use, Android 15 support, smoother transitions
        """.trimIndent(),
    )

    fun forVersion(version: String): String? = notes[version]
}
