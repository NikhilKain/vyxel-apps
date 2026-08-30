package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search deduplication, which has to tell "the same app twice" from "two apps with
 * similar names" — and gets it wrong in opposite directions if the rule is only
 * package-based or only name-based.
 *
 * The rule under test lives in `CatalogRepository.rank`, but the decision it makes is
 * pure, so it is restated here rather than standing up a repository with a Context.
 * If that logic changes, this is the contract it has to keep.
 */
class SearchDedupeTest {

    private fun dedupe(items: List<AppItem>): List<AppItem> {
        val seenPackages = HashSet<String>()
        val seenNames = HashSet<String>()
        val out = mutableListOf<AppItem>()
        for (item in items) {
            val packageKey = item.packageName?.lowercase()
            val nameKey = item.name.lowercase().filter { it.isLetterOrDigit() }
            val duplicate = when {
                packageKey != null -> !seenPackages.add(packageKey)
                else -> nameKey in seenNames
            }
            if (duplicate) continue
            seenNames += nameKey
            out += item
        }
        return out
    }

    private fun item(source: SourceId, name: String, pkg: String? = null) =
        AppItem(id = "$source:${pkg ?: name}", source = source, name = name, packageName = pkg)

    /**
     * The regression this rule was written for.
     *
     * A GitHub repo called "Instagram-" sorts ahead of Aptoide on source trust. Under
     * name-based dedupe it claimed the normalised name "instagram" and the real
     * Instagram — a different app, with a package id — was dropped entirely, so the
     * query returned neither.
     */
    @Test
    fun aRepoWithASimilarNameDoesNotEatTheRealApp() {
        val results = dedupe(
            listOf(
                item(SourceId.GitHub, "Instagram-"),
                item(SourceId.Aptoide, "Instagram", "com.instagram.android"),
            )
        )
        assertEquals(2, results.size)
        assertTrue(results.any { it.packageName == "com.instagram.android" })
    }

    /** The same package from two sources is one app, and collapses. */
    @Test
    fun samePackageFromTwoSourcesCollapses() {
        val results = dedupe(
            listOf(
                item(SourceId.FDroid, "Signal", "org.thoughtcrime.securesms"),
                item(SourceId.IzzyOnDroid, "Signal", "org.thoughtcrime.securesms"),
            )
        )
        assertEquals(1, results.size)
        assertEquals(SourceId.FDroid, results.single().source)
    }

    /**
     * The case name-matching exists for: a bare repo has no package id, so it can
     * only be recognised as the same project by its title.
     */
    @Test
    fun anUnpackagedRepoCollapsesIntoThePackagedBuild() {
        val results = dedupe(
            listOf(
                item(SourceId.FDroid, "AntennaPod", "de.danoeh.antennapod"),
                item(SourceId.GitHub, "AntennaPod"),
            )
        )
        assertEquals(1, results.size)
        assertEquals("de.danoeh.antennapod", results.single().packageName)
    }

    /** Different packages sharing a title are different apps and both survive. */
    @Test
    fun twoDifferentPackagesWithOneTitleBothSurvive() {
        val results = dedupe(
            listOf(
                item(SourceId.Aptoide, "Instagram", "com.instagram.android"),
                item(SourceId.Aptoide, "Instagram", "com.instagram.airwave"),
            )
        )
        assertEquals(2, results.size)
    }

    /** Two unpackaged repos with the same name are still one row. */
    @Test
    fun twoUnpackagedReposWithOneNameCollapse() {
        val results = dedupe(
            listOf(
                item(SourceId.GitHub, "Tachiyomi"),
                item(SourceId.GitLab, "Tachiyomi"),
            )
        )
        assertEquals(1, results.size)
    }
}
