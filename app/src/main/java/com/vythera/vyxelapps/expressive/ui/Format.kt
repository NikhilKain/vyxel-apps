package com.vythera.vyxelapps.expressive.ui

import java.util.concurrent.TimeUnit

/** Compact count: 1200 -> "1.2k", 3_400_000 -> "3.4M". */
fun formatCount(value: Long): String = when {
    value <= 0L -> ""
    value < 1_000L -> value.toString()
    value < 1_000_000L -> {
        val k = value / 100L / 10.0
        if (k >= 100) "${k.toInt()}k" else "${trimZero(k)}k"
    }
    else -> {
        val m = value / 100_000L / 10.0
        if (m >= 100) "${m.toInt()}M" else "${trimZero(m)}M"
    }
}

private fun trimZero(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

/** Byte size for download buttons. */
fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> String.format("%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

/** Coarse relative time — precise enough for "when was this updated". */
fun formatRelativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val delta = System.currentTimeMillis() - epochMillis
    if (delta < 0) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 31 -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}
