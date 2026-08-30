package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId

/**
 * A place apps can come from.
 *
 * Implementations must be safe to call concurrently and should fail by throwing —
 * the repository isolates failures per source so one dead endpoint never blanks
 * the whole catalog.
 */
interface AppSource {
    val id: SourceId

    /** Curated / popular entries used to build the home rails. */
    suspend fun featured(): List<AppItem>

    /** Free-text search within this source. */
    suspend fun search(query: String): List<AppItem>

    /**
     * Fills in details that are too expensive to fetch for a whole list — most
     * notably resolving a repo's latest release into a concrete APK URL.
     * Returns [item] unchanged when there is nothing more to fetch.
     */
    suspend fun resolve(item: AppItem): AppItem = item
}
