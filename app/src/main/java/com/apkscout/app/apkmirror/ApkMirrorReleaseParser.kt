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

    private val architectureValues = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "armeabi",
        "x86_64",
        "x86",
        "universal",
        "noarch"
    )

    private val dpiRegex = Regex("""\b(nodpi|alldpi|\d{2,4}\s*-\s*\d{2,4}dpi|\d{2,4}dpi)\b""", RegexOption.IGNORE_CASE)

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
                val context = html.contextAround(match.range.first, match.range.last)

                ApkMirrorVariantLink(
                    url = url,
                    label = label.ifBlank { "APKMirror variant" },
                    format = inferFormat(url),
                    architectures = findArchitectures(context),
                    dpi = findDpi(context),
                    minSdk = findMinSdk(context)
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

    private fun findArchitectures(text: String): List<String> {
        val lower = text.lowercase()

        return architectureValues
            .filter { it in lower }
            .distinct()
    }

    private fun findDpi(text: String): String? {
        return dpiRegex
            .find(text)
            ?.value
            ?.lowercase()
            ?.replace(Regex("""\s+"""), "")
    }

    private fun findMinSdk(text: String): Int? {
        val normalized = text.stripHtml().decodeBasicEntities().cleanSpaces()

        Regex("""Android\s+([0-9]+)(?:\.[0-9]+)?\+""", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { major ->
                return androidMajorToSdk(major)
            }

        Regex("""min(?:imum)?\s*SDK[^0-9]{0,40}([0-9]{1,2})""", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        return null
    }

    private fun androidMajorToSdk(major: Int): Int? {
        return when (major) {
            1 -> 1
            2 -> 5
            3 -> 11
            4 -> 14
            5 -> 21
            6 -> 23
            7 -> 24
            8 -> 26
            9 -> 28
            10 -> 29
            11 -> 30
            12 -> 31
            13 -> 33
            14 -> 34
            15 -> 35
            16 -> 36
            else -> null
        }
    }

    private fun String.contextAround(
        start: Int,
        end: Int
    ): String {
        val safeStart = (start - 1_200).coerceAtLeast(0)
        val safeEnd = (end + 1_200).coerceAtMost(length)

        return substring(safeStart, safeEnd)
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
