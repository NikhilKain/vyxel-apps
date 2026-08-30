package com.vythera.vyxelapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotExtractTest {

    private val base = "https://raw.githubusercontent.com/owner/repo/HEAD"

    @Test
    fun `extracts markdown and html images`() {
        val md = """
            # My App
            ![screenshot](docs/screenshot1.png)
            <img src="docs/screen2.png" width="200"/>
        """.trimIndent()
        val shots = extractScreenshots(md, base)
        assertTrue(shots.any { it.endsWith("docs/screenshot1.png") })
        assertTrue(shots.any { it.endsWith("docs/screen2.png") })
    }

    @Test
    fun `resolves relative paths against the raw base`() {
        val shots = extractScreenshots("![s](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)", base)
        assertEquals("$base/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png", shots.single())
    }

    @Test
    fun `keeps absolute and protocol-relative urls`() {
        val abs = extractScreenshots("![s](https://example.com/shots/a.png)", base)
        assertEquals("https://example.com/shots/a.png", abs.single())
        val proto = extractScreenshots("![s](//example.com/shots/b.jpg)", base)
        assertEquals("https://example.com/shots/b.jpg", proto.single())
    }

    @Test
    fun `drops badges shields and svg logos - the empty images users saw`() {
        val md = """
            [![Build](https://travis-ci.org/o/r.svg)](x)
            ![License](https://img.shields.io/badge/license-MIT-blue.svg)
            ![codecov](https://codecov.io/gh/o/r/badge.svg)
            <img src="assets/logo.png"/>
            ![real](screenshots/home.png)
        """.trimIndent()
        val shots = extractScreenshots(md, base)
        assertTrue(shots.any { it.endsWith("screenshots/home.png") })
        assertFalse(shots.any { it.contains("shields.io") })
        assertFalse(shots.any { it.contains("travis") })
        assertFalse(shots.any { it.contains("codecov") })
        assertFalse(shots.any { it.endsWith(".svg") })
        assertFalse(shots.any { it.contains("logo") })
    }

    @Test
    fun `screenshot-like urls are ranked ahead of incidental images`() {
        val md = """
            ![diagram](docs/architecture.png)
            ![shot](docs/screenshots/main.png)
        """.trimIndent()
        val shots = extractScreenshots(md, base)
        assertTrue(shots.first().contains("screenshots/main.png"))
    }

    /**
     * Regression: GitHub's drag-and-drop attachment URLs carry no file extension.
     * Requiring one silently dropped entire galleries — this is verbatim what
     * rumboalla/apkupdater's README embeds, and none of it used to survive.
     */
    @Test
    fun `keeps extension-less github attachment urls`() {
        val md = """
            ![](https://github.com/rumboalla/apkupdater/workflows/Android%20Build/badge.svg)
            ![1](https://github.com/rumboalla/apkupdater/assets/21153554/b5b4943b-e12a-43e2-a056-26d6f06f9bc4)
            ![2](https://github.com/rumboalla/apkupdater/assets/21153554/c4679c1b-09d4-429d-9160-77d4d33b0a0f)
        """.trimIndent()
        val shots = extractScreenshots(md, base)
        assertEquals(2, shots.size)
        assertTrue(shots.all { it.contains("/assets/21153554/") })
        assertFalse(shots.any { it.contains("badge.svg") })
    }

    @Test
    fun `keeps user-images and user-attachments hosts`() {
        val md = """
            ![a](https://user-images.githubusercontent.com/12345/screenshot-thing)
            ![b](https://github.com/user-attachments/assets/2b7c1f80-1111-2222-3333-444455556666)
        """.trimIndent()
        assertEquals(2, extractScreenshots(md, base).size)
    }

    @Test
    fun `still rejects avatar and badge githubusercontent urls`() {
        val md = """
            ![avatar](https://avatars.githubusercontent.com/u/3427627?v=4)
            ![u](https://githubusercontent.com/u/12345/thing.png)
        """.trimIndent()
        assertTrue(extractScreenshots(md, base).isEmpty())
    }

    @Test
    fun `blank readme yields nothing`() {
        assertTrue(extractScreenshots("", base).isEmpty())
        assertTrue(extractScreenshots("Just text, no images.", base).isEmpty())
    }

    @Test
    fun `caps at eight images`() {
        val md = (1..20).joinToString("\n") { "![s](shots/$it.png)" }
        assertEquals(8, extractScreenshots(md, base).size)
    }
}
