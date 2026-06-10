package com.apkscout.app.apkmirror

object ApkMirrorReleaseParser {
    private val titleRegex = Regex(
        pattern = """<title[^>]*>(.*?)</title>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val headingRegex = Regex(
        pattern = """<h1[^>]*>(.*?)</h1>""",
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

    private fun findTitle(html: String): String? {
        val raw = titleRegex.find(html)?.groupValues?.getOrNull(1)
            ?: headingRegex.find(html)?.groupValues?.getOrNull(1)
            ?: return null

        val cleaned = raw
            .stripHtml()
            .decodeBasicEntities()
            .replace(Regex("""\s+APK Download.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+APKMirror\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

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
}
