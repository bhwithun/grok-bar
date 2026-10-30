package com.brian.grokbar.update

import java.net.HttpURLConnection
import java.net.URL

object GithubReleases {
    const val REPO = "bhwithun/grok-bar"

    fun latest(): RemoteRelease? {
        val connection = (URL("$API/releases/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GrokBar")
        }
        return try {
            val code = connection.responseCode
            if (code == 404) return null
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException(httpMessage(code, body))
            }
            parseGithubRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpMessage(code: Int, body: String): String {
        if (code == 403 && body.contains("rate limit", ignoreCase = true)) {
            return "GitHub rate limit reached. Try again later."
        }
        return "GitHub releases returned HTTP $code."
    }

    private const val API = "https://api.github.com/repos/$REPO"
}
