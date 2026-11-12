package com.stellapp.disc.playback

/** Where to put a removed queue item back: after its old neighbour, else before the other one. */
object UndoPlacement {
    fun position(queueIds: List<String>, before: String?, after: String?, originalIndex: Int): Int {
        if (before != null) {
            val i = queueIds.indexOf(before)
            if (i >= 0) return i + 1
        }
        if (after != null) {
            val i = queueIds.indexOf(after)
            if (i >= 0) return i
        }
        return originalIndex.coerceIn(0, queueIds.size)
    }
}
