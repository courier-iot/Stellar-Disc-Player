package com.stellapp.disc.uplink

/**
 * Turns yt-dlp's raw error text into one sentence a non-technical person can act on.
 * The raw text is still shown behind "Details" on the job card.
 */
object PlainErrors {

    private val rules: List<Pair<Regex, String>> = listOf(
        rx("sign in to confirm|confirm you.?re not a bot|cookies.*(needed|required)|use --cookies") to
            "The site thinks we're a bot. Connect your account in Settings → Uplink engine, then try again.",
        rx("age.?restrict|confirm your age|inappropriate for some users") to
            "Age-restricted. Connect your account in Settings → Uplink engine to unlock it.",
        rx("private video|this video is private|playlist does not exist|is private") to
            "That one's private. Only its owner can reach it.",
        rx("members.?only|join this channel|premium|subscriber") to
            "Members-only content. It needs an account that has access.",
        rx("not available in your country|geo.?restrict|blocked it in your country|not made this video available") to
            "Blocked in your region. The site won't send it here.",
        rx("video unavailable|has been removed|no longer available|account.*terminated|404") to
            "Signal lost: it's been removed or is unavailable.",
        rx("is live|live event|premieres in|will begin in") to
            "It's live or hasn't started yet. Try again once it's finished.",
        rx("unsupported url|no suitable extractor|is not a valid url") to
            "Unknown frequency: that site or link type isn't supported.",
        rx("requested format is not available|no video formats found|no formats found") to
            "That quality isn't on offer. Try a lower quality or Data saver.",
        rx("http error 429|too many requests|rate.?limit") to
            "The site is rate-limiting us. Wait a few minutes and try again.",
        rx("http error 403|forbidden") to
            "The site refused the request. Try again, or connect your account in Settings.",
        rx("no space left|enospc|disk full|not enough space") to
            "Your phone's storage is full. Free up some space and try again.",
        rx("unable to download webpage|failed to resolve|name or service not known|network is unreachable|timed out|connection (reset|refused)|no address associated") to
            "No connection. Check your internet and try again.",
        rx("ffmpeg|postprocess|conversion failed") to
            "Downloaded, but converting it failed. Try a different quality.",
        rx("js.?runtime|jsc|nsig|signature extraction|n challenge") to
            "YouTube changed its locks. Settings → Uplink engine → update yt-dlp, then try again.",
    )

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** A plain sentence for [raw], or null when there's no good match (show [raw] as-is). */
    fun explain(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return rules.firstOrNull { (re, _) -> re.containsMatchIn(raw) }?.second
    }
}
