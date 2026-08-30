package com.vythera.vyxelapps

/**
 * Version comparison shared by the update checker and the multi-source scanner.
 *
 * The old implementations parsed with `split(".").mapNotNull { it.toIntOrNull() }`,
 * which silently *drops* any component carrying a suffix: "1.0.7-beta" parsed as
 * [1, 0] and therefore compared as older than "1.0.6" — a real release the user
 * would never be notified about. Pre-release parts are now kept and ranked by
 * semver rules (1.0.7-beta < 1.0.7), and a leading "v"/"release-" is tolerated.
 */

private val LEADING_JUNK    = Regex("^[^0-9]*")
private val SEPARATORS      = Regex("[._+\\s]")
private val PRE_SEPARATORS  = Regex("[._+\\s-]")

private data class ParsedVersion(
    val numbers    : List<Int>,
    val preRelease : List<String>,
    val raw        : String
)

private fun parseVersion(raw: String): ParsedVersion {
    val cleaned = raw.trim().replace(LEADING_JUNK, "")
    // Everything from the first '-' on is a pre-release qualifier (semver).
    val dash    = cleaned.indexOf('-')
    val core    = if (dash >= 0) cleaned.substring(0, dash) else cleaned
    val pre     = if (dash >= 0) cleaned.substring(dash + 1) else ""

    val numbers = core.split(SEPARATORS)
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    val preParts = pre.split(PRE_SEPARATORS).filter { it.isNotEmpty() }

    return ParsedVersion(numbers, preParts, raw.trim())
}

/** Semver identifier ordering: numeric < alphanumeric, numerics compare as numbers. */
private fun comparePreRelease(a: List<String>, b: List<String>): Int {
    // No pre-release outranks any pre-release: 1.0.0 > 1.0.0-rc1
    if (a.isEmpty() && b.isEmpty()) return 0
    if (a.isEmpty()) return 1
    if (b.isEmpty()) return -1

    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrNull(i) ?: return -1   // shorter set of identifiers is lower
        val y = b.getOrNull(i) ?: return 1
        val xn = x.toIntOrNull()
        val yn = y.toIntOrNull()
        val cmp = when {
            xn != null && yn != null -> xn.compareTo(yn)
            xn != null               -> -1   // numeric identifiers rank below alphanumeric
            yn != null               -> 1
            // alpha < beta < rc falls out of plain lexical ordering
            else                     -> x.compareTo(y, ignoreCase = true)
        }
        if (cmp != 0) return cmp
    }
    return 0
}

/** Negative if [a] is older than [b], 0 if equivalent, positive if newer. */
fun compareVersions(a: String, b: String): Int {
    val pa = parseVersion(a)
    val pb = parseVersion(b)

    // Nothing numeric on either side (date tags, code names): fall back to text.
    if (pa.numbers.isEmpty() && pb.numbers.isEmpty())
        return pa.raw.compareTo(pb.raw, ignoreCase = true)
    if (pa.numbers.isEmpty()) return -1
    if (pb.numbers.isEmpty()) return 1

    for (i in 0 until maxOf(pa.numbers.size, pb.numbers.size)) {
        val x = pa.numbers.getOrElse(i) { 0 }
        val y = pb.numbers.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return comparePreRelease(pa.preRelease, pb.preRelease)
}

fun isVersionNewerThan(latest: String, current: String): Boolean =
    compareVersions(latest, current) > 0
