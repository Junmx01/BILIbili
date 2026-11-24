package io.github.sodarx.biliplay.core

import java.util.UUID

object DownloadNames {
    /** File extension for an untouched (not transcoded) copy of a Bilibili audio stream. */
    fun originalExtension(mime: String): String =
        when (mime.substringBefore(';').trim().lowercase()) {
            "audio/flac",
            "audio/x-flac" -> "flac"
            "audio/mpeg" -> "mp3"
            else -> "m4a"
        }

    /** MIME type to use for a saved download, judged from its file name or URI. */
    fun mimeOf(nameOrUri: String): String {
        val name = nameOrUri.substringBefore('?').lowercase()
        return when {
            name.endsWith(".m4a") || name.endsWith(".mp4") -> "audio/mp4"
            name.endsWith(".flac") -> "audio/flac"
            else -> "audio/mpeg"
        }
    }

    fun privateFile(track: Track, job: UUID, extension: String = "mp3"): String {
        val video =
            track.bvid.takeIf { Regex("BV[0-9A-Za-z]{10}").matches(it) }
                ?: "av${track.aid.coerceAtLeast(0)}"
        return "${video}_p${track.page.coerceAtLeast(1)}_$job.$extension"
    }
}
