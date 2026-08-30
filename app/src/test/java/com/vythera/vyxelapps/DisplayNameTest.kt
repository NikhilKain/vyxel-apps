package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unresolved entries reach the hero carousel, so a raw package id there is the
 * most visible text in the app. These are the shapes that actually turned up.
 */
class DisplayNameTest {

    private fun item(name: String) =
        AppItem(id = "x", source = SourceId.GitHub, name = name)

    @Test
    fun lowercasePackageIdBecomesAName() {
        assertEquals("Unlock", item("com.sweak.unlock").displayName)
    }

    /** "com.KaraWilson.reader" reached the hero — package ids are not all lowercase. */
    @Test
    fun mixedCasePackageIdBecomesAName() {
        assertEquals("Reader", item("com.KaraWilson.reader").displayName)
    }

    @Test
    fun underscoresBecomeSpaces() {
        assertEquals("Video Player", item("org.example.video_player").displayName)
    }

    @Test
    fun realNamesAreLeftAlone() {
        assertEquals("NewPipe", item("NewPipe").displayName)
        assertEquals("Nova Video Player", item("Nova Video Player").displayName)
        // Two segments is a name, not an id — "Chompass - Calorie" style entries
        // and things like "org.mozilla" should not be truncated.
        assertEquals("org.mozilla", item("org.mozilla").displayName)
    }
}
