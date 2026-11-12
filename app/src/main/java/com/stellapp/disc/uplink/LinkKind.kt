package com.stellapp.disc.uplink

/** What a pasted link points at, so Uplink can pick "whole playlist" or "just this one" by itself. */
enum class LinkKind {
    /** A playlist, album, set or channel page: grab everything. */
    COLLECTION,
    /** One song or video. */
    SINGLE,
    /** A single item that's opened inside a playlist (e.g. YouTube watch?v=…&list=…). */
    SINGLE_IN_LIST,
    UNKNOWN;

    companion object {
        fun of(url: String): LinkKind {
            val u = url.trim().lowercase()
            if (!u.startsWith("http")) return UNKNOWN
            val host = u.substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("m.")
            val path = "/" + u.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
            val query = u.substringAfter('?', "")
            fun has(p: String) = Regex("""(^|&)$p=""").containsMatchIn(query)

            return when {
                host.endsWith("youtube.com") || host == "youtu.be" || host == "music.youtube.com" -> when {
                    path.startsWith("/playlist") || path.startsWith("/browse/") -> COLLECTION
                    Regex("""^/(@[^/]+|c/[^/]+|channel/[^/]+|user/[^/]+)(/(videos|streams|shorts|playlists|releases))?/?$""").matches(path) -> COLLECTION
                    (path.startsWith("/watch") || host == "youtu.be" || path.startsWith("/shorts/")) && has("list") -> SINGLE_IN_LIST
                    path.startsWith("/watch") || host == "youtu.be" || path.startsWith("/shorts/") || path.startsWith("/live/") -> SINGLE
                    else -> UNKNOWN
                }
                host.endsWith("soundcloud.com") -> when {
                    path.contains("/sets/") -> COLLECTION
                    Regex("""^/[^/]+/(tracks|albums|popular-tracks|reposts|likes)/?$""").matches(path) -> COLLECTION
                    Regex("""^/[^/]+/?$""").matches(path) -> COLLECTION // an artist page
                    Regex("""^/[^/]+/[^/]+/?$""").matches(path) -> SINGLE
                    else -> UNKNOWN
                }
                host.endsWith("bandcamp.com") -> when {
                    path.startsWith("/album/") -> COLLECTION
                    path.startsWith("/track/") -> SINGLE
                    path == "/" || path == "/music" -> COLLECTION
                    else -> UNKNOWN
                }
                host.endsWith("vimeo.com") -> when {
                    path.startsWith("/showcase/") || path.startsWith("/album/") || path.startsWith("/channels/") -> COLLECTION
                    Regex("""^/\d+/?$""").matches(path) -> SINGLE
                    else -> UNKNOWN
                }
                has("list") || path.contains("/playlist") || path.contains("/album") -> COLLECTION
                else -> UNKNOWN
            }
        }
    }
}
