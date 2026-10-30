package com.brian.grokbar.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun dottedVersionsIgnoreALeadingV() {
        assertEquals(AppVersion.parse("1.0"), AppVersion.parse("v1.0.0"))
        assertTrue(AppVersion.parse("1.1")!! > AppVersion.parse("1.0.9")!!)
    }

    @Test
    fun newerReleaseWithAnApkCanBeInstalled() {
        val offer = decideUpdate("1.0", release("v1.1.0", apk = APK))
        assertEquals("Release 1.1.0 is available.", offer.message)
        assertEquals(APK, offer.apkUrl)
    }

    @Test
    fun sameReleaseStaysPut() {
        val offer = decideUpdate("1.0", release("v1.0.0", apk = APK))
        assertEquals("You're on 1.0.0.", offer.message)
        assertNull(offer.apkUrl)
    }

    @Test
    fun newerReleaseWithoutAnApkDoesNotInstall() {
        val offer = decideUpdate("1.0", release("v1.2.0", apk = null))
        assertEquals("Release 1.2.0 is available, but it has no APK.", offer.message)
        assertNull(offer.apkUrl)
        assertEquals(PAGE, offer.pageUrl)
    }

    @Test
    fun missingReleaseIsReported() {
        assertEquals("No release has been published.", decideUpdate("1.0", null).message)
    }

    @Test
    fun apkMatchingTheVersionWins() {
        val json = """
            {"tag_name":"v1.2.0","html_url":"$PAGE","assets":[
              {"name":"notes.txt","browser_download_url":"https://github.com/notes"},
              {"name":"other.apk","browser_download_url":"https://github.com/other.apk"},
              {"name":"grok-bar-1.2.0.apk","browser_download_url":"$APK"}
            ]}
        """.trimIndent()
        assertEquals(APK, parseGithubRelease(json)?.apkUrl)
    }

    private fun release(tag: String, apk: String?): RemoteRelease {
        return RemoteRelease(tag, AppVersion.parse(tag), PAGE, apk)
    }

    private companion object {
        const val PAGE = "https://github.com/bhwithun/grok-bar/releases/tag/v1.2.0"
        const val APK = "https://github.com/bhwithun/grok-bar/releases/download/v1.2.0/grok-bar-1.2.0.apk"
    }
}
