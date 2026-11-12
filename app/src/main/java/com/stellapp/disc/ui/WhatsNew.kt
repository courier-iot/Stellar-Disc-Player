package com.stellapp.disc.ui

/** Shown once after each update. Keep the newest version first. */
object WhatsNew {
    val notes: Map<String, String> = linkedMapOf(
        "3.0.1" to """
            CHANGED
            • Fixed: fullscreen video went black after rotating the phone
        """.trimIndent(),
        "3.0.0" to """
            ADDED
            • ♥ and shuffle buttons on the media notification and lock screen
            • The home screen widget resizes: small, wide and large, with a ♥ button
            • Pure black backgrounds for OLED screens (Settings → Look)
            • A–Z letter rail on the Songs list: drag it to jump (A–Z and Artist sorts)
            • Settings → Gestures & tips lists every swipe and shortcut
            • Launch screen with the disc, and a themed icon for Android 13+ home screens

            CHANGED
            • Friendlier screens when there's no music, playlists, favourites or downloads yet
            • The welcome tour covers swipes and favourites
            • Fullscreen video no longer shows a frozen second picture after rotating
        """.trimIndent(),
        "2.9.0" to """
            ADDED
            • Swap transfers are encrypted; the key is only in the QR code (both phones need v2.9)
            • Backups now include favourites, pins, play counts, listening history, lyrics, tag edits and source links, matched by song on a new phone
            • Settings → Uplink engine: "Strict yt-dlp options" and a GitHub fallback for YouTube's solver scripts
            • Clear messages when the downloader can't start (for example on phones with 16 KB memory pages)

            CHANGED
            • yt-dlp uses the solver built into its official release instead of downloading scripts at run time
            • If your YouTube sign-in gets reset by a system update, the app tells you instead of failing quietly
            • The random rotation keeps refilling even when playback starts without opening the app
            • Deleting several files works on Android 10
            • Leftover partial downloads are cleaned up every time the app starts
            • Undo puts a queue item back next to its old neighbours
        """.trimIndent(),
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
