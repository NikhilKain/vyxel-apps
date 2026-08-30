package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.source.moduleFamily
import com.vythera.vyxelapps.expressive.data.source.readableModuleName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Module repositories publish names inconsistently, and the Alt Repo index publishes
 * none at all — its second phase often comes back empty for a module whose
 * `module.prop` moved or 404s. Every id below is a real one from those indexes.
 */
class ModuleNamingTest {

    @Test
    fun keepsAPublishedName() {
        assertEquals(
            "Universal GMS Doze",
            readableModuleName("Universal GMS Doze", "universal-gms-doze"),
        )
    }

    @Test
    fun recoversANameFromAnEmptyOne() {
        assertEquals("Abootloop", readableModuleName("", "abootloop"))
    }

    /** Underscore-separated ids are the Alt Repo's house style. */
    @Test
    fun splitsUnderscores() {
        assertEquals(
            "1 MARS SOM BASE GEAR FIRST",
            readableModuleName("", "1_MARS_SOM_BASE-GEAR_FIRST"),
        )
    }

    /** Camel case is split, and acronyms the author capitalised are left alone. */
    @Test
    fun splitsCamelCaseAndKeepsAcronyms() {
        assertEquals("Ad Guard DNS4 Magisk", readableModuleName("", "AdGuardDNS4Magisk"))
    }

    /** A name that is just the id again is treated as no name at all. */
    @Test
    fun nameEqualToIdIsRecovered() {
        assertEquals("Abootloop", readableModuleName("abootloop", "abootloop"))
    }

    /** Reverse-DNS ids drop the author's domain, which carries nothing for a reader. */
    @Test
    fun reverseDnsKeepsTheMeaningfulTail() {
        assertEquals("Privacykit", readableModuleName("com.sal.privacykit", "com.sal.privacykit"))
    }

    /** A short final segment needs its qualifier to mean anything. */
    @Test
    fun shortTailKeepsItsQualifier() {
        assertEquals("Statusbarmod Hook", readableModuleName("", "com.statusbarmod.hook"))
    }

    /**
     * A *published* one-dot name is a real name style and is left alone.
     *
     * `app.lawnchair` and `node.js` are things people genuinely call their projects,
     * so only two dots or a slash marks a name as an address. The guard applies to
     * names that differ from the id — a name that merely repeats the id is no name,
     * whatever its shape, and is recovered by the case below.
     */
    @Test
    fun publishedSingleDotNameIsLeftAlone() {
        assertEquals("app.lawnchair", readableModuleName("app.lawnchair", "lawnchair-module"))
    }

    /** Repeating the id is not publishing a name, even in a plausible name shape. */
    @Test
    fun nameRepeatingADottedIdIsStillRecovered() {
        assertEquals("Lawnchair", readableModuleName("app.lawnchair", "app.lawnchair"))
    }

    @Test
    fun ownerRepoIdsUseTheRepoHalf() {
        assertEquals("Safetynet Fix", readableModuleName("", "kdrag0n/safetynet-fix"))
    }

    // ---- family inference ---------------------------------------------------

    @Test
    fun detectsLsposed() {
        assertEquals("LSPosed", moduleFamily("Some Hook", "An LSPosed module that hooks apps"))
    }

    /** A module naming both is a Zygisk module saying what it needs. */
    @Test
    fun zygiskWinsOverMagisk() {
        assertEquals("Zygisk", moduleFamily("X", "A Zygisk module, install with Magisk"))
    }

    @Test
    fun detectsKernelSu() {
        assertEquals("KernelSU", moduleFamily("X", "Built for KernelSU"))
    }

    @Test
    fun defaultsToMagisk() {
        assertEquals("Magisk", moduleFamily("Debloater", "Removes system apps"))
    }
}
