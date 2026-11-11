package com.stellapp.disc

object AppInfo {
    /** Folder name used under Music/ and Movies/ for downloads. */
    const val FOLDER = "Stellar Disc"

    /** GitHub repo used for the update checker and problem reports. */
    const val REPO = "courier-iot/Stellar-Disc-Player"
    const val REPO_URL = "https://github.com/$REPO"
    const val SUPPORT_URL = "$REPO_URL/issues"

    const val SUPPORTED_SITES_URL = "https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md"
    const val USER_AGENT = "StellarDisc (+$REPO_URL)"
}
