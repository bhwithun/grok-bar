package com.brian.grokbar.update

/** Numeric version from a release tag or the installed versionName. */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val count = maxOf(parts.size, other.parts.size)
        for (index in 0 until count) {
            val diff = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int {
        val end = parts.indexOfLast { it != 0 }.coerceAtLeast(0)
        return parts.take(end + 1).hashCode()
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        private val pattern = Regex("""^v?(\d+(?:\.\d+)*)$""", RegexOption.IGNORE_CASE)

        fun parse(raw: String): AppVersion? {
            val core = raw.trim().substringBefore('-').substringBefore('+')
            val match = pattern.matchEntire(core) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toInt() }
            return AppVersion(numbers)
        }
    }
}

data class RemoteRelease(
    val tag: String,
    val version: AppVersion?,
    val pageUrl: String,
    val apkUrl: String?
)

data class UpdateOffer(
    val message: String,
    val pageUrl: String? = null,
    val apkUrl: String? = null
)

fun parseGithubRelease(json: String): RemoteRelease? {
    val tag = jsonString(json, "tag_name")?.trim().orEmpty()
    if (tag.isEmpty()) return null
    val version = AppVersion.parse(tag)
    val page = jsonString(json, "html_url").orEmpty()
    return RemoteRelease(
        tag = tag,
        version = version,
        pageUrl = page,
        apkUrl = version?.let { pickApkUrl(json, it) }
    )
}

fun decideUpdate(installedName: String, remote: RemoteRelease?): UpdateOffer {
    if (remote == null) return UpdateOffer("No release has been published.")
    val published = remote.version
        ?: return UpdateOffer(
            "Latest release ${remote.tag} isn't a version number.",
            remote.pageUrl.takeIf { it.isNotBlank() }
        )
    val installed = AppVersion.parse(installedName)
        ?: return UpdateOffer(
            "Couldn't compare this build. Latest release is $published.",
            remote.pageUrl.takeIf { it.isNotBlank() }
        )
    return when (published.compareTo(installed)) {
        in 1..Int.MAX_VALUE -> UpdateOffer(
            message = if (remote.apkUrl.isNullOrBlank()) {
                "Release $published is available, but it has no APK."
            } else {
                "Release $published is available."
            },
            pageUrl = remote.pageUrl.takeIf { it.isNotBlank() },
            apkUrl = remote.apkUrl?.takeIf { it.isNotBlank() }
        )
        0 -> UpdateOffer("You're on $published.")
        else -> UpdateOffer("This build is newer than the published release $published.")
    }
}

private fun pickApkUrl(json: String, version: AppVersion): String? {
    val apks = apkAssets(json).filter { (name, url) ->
        name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")
    }
    if (apks.isEmpty()) return null
    val versionText = version.toString()
    apks.firstOrNull { (name, _) -> name.contains(versionText) }?.second?.let { return it }
    apks.singleOrNull()?.second?.let { return it }
    return apks.singleOrNull { (name, _) -> name.contains("grok-bar", ignoreCase = true) }?.second
}

private fun apkAssets(json: String): List<Pair<String, String>> {
    val assetsAt = json.indexOf("\"assets\"")
    if (assetsAt < 0) return emptyList()
    val start = json.indexOf('[', assetsAt)
    if (start < 0) return emptyList()
    val end = matchingBracket(json, start, '[', ']') ?: return emptyList()
    return jsonObjects(json.substring(start, end + 1)).mapNotNull { obj ->
        val name = jsonString(obj, "name") ?: return@mapNotNull null
        val url = jsonString(obj, "browser_download_url") ?: return@mapNotNull null
        name to url
    }
}

internal fun jsonString(body: String, key: String): String? {
    val match = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""").find(body) ?: return null
    return unescapeJson(match.groupValues[1])
}

private fun jsonObjects(array: String): List<String> {
    val objects = mutableListOf<String>()
    var index = 0
    while (index < array.length) {
        if (array[index] == '{') {
            val end = matchingBracket(array, index, '{', '}') ?: break
            objects += array.substring(index, end + 1)
            index = end + 1
        } else {
            index++
        }
    }
    return objects
}

private fun matchingBracket(text: String, openAt: Int, open: Char, close: Char): Int? {
    var depth = 0
    var index = openAt
    var inString = false
    var escaped = false
    while (index < text.length) {
        val ch = text[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                inString = false
            }
        } else {
            when (ch) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        index++
    }
    return null
}

private fun unescapeJson(raw: String): String = buildString {
    var index = 0
    while (index < raw.length) {
        val ch = raw[index]
        if (ch != '\\' || index + 1 >= raw.length) {
            append(ch)
            index++
            continue
        }
        when (val escaped = raw[index + 1]) {
            '\\', '/' -> append(escaped)
            '"' -> append('"')
            'n' -> append('\n')
            't' -> append('\t')
            else -> append(escaped)
        }
        index += 2
    }
}
