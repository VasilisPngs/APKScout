package com.apkscout.app.apkmirror

object ApkMirrorSearchParser {
    private val hrefRegex = Regex("""href=["']([^"']+)["']""")

    fun parseReleaseLinks(html: String): List<String> {
        return hrefRegex
            .findAll(html)
            .map { it.groupValues[1].trim() }
            .mapNotNull { href -> normalizeReleaseUrl(href) }
            .distinct()
            .filterNot { it.contains("/download/", ignoreCase = true) }
            .filterNot { it.contains("/variant", ignoreCase = true) }
            .take(20)
            .toList()
    }

    private fun normalizeReleaseUrl(href: String): String? {
        val clean = href.substringBefore("#").trim()

        if (clean.isBlank()) return null
        if (!clean.contains("/apk/", ignoreCase = true)) return null
        if (!clean.endsWith("/")) return null

        return when {
            clean.startsWith("https://www.apkmirror.com/apk/") -> clean
            clean.startsWith("/apk/") -> "https://www.apkmirror.com$clean"
            else -> null
        }
    }
}
