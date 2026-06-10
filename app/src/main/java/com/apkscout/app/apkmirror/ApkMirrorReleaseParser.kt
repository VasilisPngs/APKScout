package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.ApkFormat

object ApkMirrorReleaseParser {
    private val titleRegex = Regex(
        pattern = """<title[^>]*>(.*?)</title>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val headingRegex = Regex(
        pattern = """<h1[^>]*>(.*?)</h1>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val anchorRegex = Regex(
        pattern = """<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val versionCodeRegexes = listOf(
        Regex("""\bversion\s*code\b[^0-9]{0,140}([0-9]{1,})""", RegexOption.IGNORE_CASE),
        Regex("""\bversioncode\b[^0-9]{0,100}([0-9]{1,})""", RegexOption.IGNORE_CASE)
    )

    fun parse(
        html: String,
        releaseUrl: String
    ): ApkMirrorReleaseMetadata? {
        val title = findTitle(html) ?: return null

        return ApkMirrorReleaseMetadata(
            title = title,
            versionCode = findVersionCode(html),
            releaseUrl = releaseUrl
        )
    }

    fun parseVariantLinks(html: String): List<ApkMirrorVariantLink> {
        return anchorRegex
            .findAll(html)
            .mapNotNull { match ->
                val href = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val label = match.groupValues.getOrNull(2)?.stripHtml()?.decodeBasicEntities()?.cleanSpaces().orEmpty()
                val url = normalizeVariantUrl(href) ?: return@mapNotNull null

                ApkMirrorVariantLink(
                    url = url,
                    label = label.ifBlank { "APKMirror variant" },
                    format = inferFormat(url)
                )
            }
            .distinctBy { it.url }
            .take(40)
            .toList()
    }

    private fun findTitle(html: String): String? {
        val raw = titleRegex.find(html)?.groupValues?.getOrNull(1)
            ?: headingRegex.find(html)?.groupValues?.getOrNull(1)
            ?: return null

        val cleaned = raw
            .stripHtml()
            .decodeBasicEntities()
            .replace(Regex("""\s+APK Download.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+APKMirror\s*$""", RegexOption.IGNORE_CASE), "")
            .cleanSpaces()

        return cleaned.ifBlank { null }
    }

    private fun findVersionCode(html: String): Long? {
        return versionCodeRegexes
            .firstNotNullOfOrNull { regex ->
                regex.find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
            }
    }

    private fun normalizeVariantUrl(href: String): String? {
        val clean = href.substringBefore("#").trim()

        if (clean.isBlank()) return null
        if (!clean.contains("/apk/", ignoreCase = true)) return null
        if (!clean.endsWith("/")) return null

        val lower = clean.lowercase()
        val looksLikeVariant = lower.contains("-apk-download/") ||
            lower.contains("-apkm-download/") ||
            lower.contains("-apks-download/") ||
            lower.contains("-xapk-download/") ||
            lower.contains("-bundle-download/")

        if (!looksLikeVariant) return null

        return when {
            clean.startsWith("https://www.apkmirror.com/apk/") -> clean
            clean.startsWith("/apk/") -> "https://www.apkmirror.com$clean"
            else -> null
        }
    }

    private fun inferFormat(url: String): ApkFormat {
        val lower = url.lowercase()

        return when {
            "-apkm-download/" in lower -> ApkFormat.APKM
            "-apks-download/" in lower -> ApkFormat.APKS
            "-xapk-download/" in lower -> ApkFormat.XAPK
            "-bundle-download/" in lower -> ApkFormat.BUNDLE
            "-apk-download/" in lower -> ApkFormat.APK
            else -> ApkFormat.UNKNOWN
        }
    }

    private fun String.stripHtml(): String {
        return replace(Regex("""<[^>]+>"""), " ")
    }

    private fun String.decodeBasicEntities(): String {
        return replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun String.cleanSpaces(): String {
        return replace(Regex("""\s+"""), " ").trim()
    }
}
