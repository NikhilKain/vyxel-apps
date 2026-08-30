package com.vythera.vyxelapps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vythera.vyxelapps.api.GitHubRateLimit
import kotlinx.coroutines.delay

/**
 * How much of the GitHub API budget is left, as GitHub reports it.
 *
 * Lives in the shared package and is drawn with plain Material 3 so both shells can
 * show the identical control — the two settings screens otherwise have no widget
 * vocabulary in common.
 *
 * Two bars rather than one, because GitHub meters two budgets that behave nothing
 * alike and the difference is exactly what a user hitting a limit needs to see:
 *
 *  - **Search** — 10/minute anonymous, 30/minute with a token. Spent by typing in the
 *    search box. Refills in under a minute, so running dry is a brief annoyance.
 *  - **Core** — 60 per **hour** anonymous, 5000 with a token. Spent by opening an
 *    app's page to look up its release. This is the one that actually bites: browsing
 *    the home rows and opening a handful of apps can empty it, after which further
 *    apps report no APK until the hour turns over.
 *
 * Nothing is shown until GitHub has actually answered — an invented "10/10 left" would
 * be a guess, and the honest state before the first request is that we do not know.
 */
@Composable
fun GitHubQuotaMeter(modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val buckets by GitHubRateLimit.buckets.collectAsStateWithLifecycle()

    // Ticks once a second so the countdown counts down. Keyed on whether anything is
    // being displayed at all, so a settings screen with no readings yet isn't waking
    // up every second to redraw nothing.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasAny = buckets.isNotEmpty()
    LaunchedEffect(hasAny) {
        while (hasAny) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    // An elapsed window is stale, not empty: the budget it described has already
    // refilled, so showing "0 left" from it would be actively misleading.
    val search = buckets[GitHubRateLimit.SEARCH]?.takeUnless { it.isExpired(now) }
    val core = buckets[GitHubRateLimit.CORE]?.takeUnless { it.isExpired(now) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (search == null && core == null) {
            Text(
                text = s.quotaUnknown,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        search?.let {
            QuotaBar(s.searchQuotaLabel, it, now, s.quotaResets)
        }
        if (search != null && core != null) Spacer(Modifier.height(10.dp))
        core?.let {
            QuotaBar(s.hourlyQuotaLabel, it, now, s.quotaResets)
        }
    }
}

@Composable
private fun QuotaBar(
    label: String,
    bucket: GitHubRateLimit.Bucket,
    now: Long,
    resetsTemplate: String,
) {
    val fraction by animateFloatAsState(bucket.fraction, label = "quota")
    // Amber before it runs out, not after: a bar that only turns red once the budget
    // is gone tells the user something they have already discovered the hard way.
    val barColor = when {
        bucket.remaining == 0 -> MaterialTheme.colorScheme.error
        bucket.fraction <= 0.25f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${bucket.remaining} / ${bucket.limit}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = barColor,
            )
        }
        Spacer(Modifier.height(5.dp))
        // Hand-drawn rather than a LinearProgressIndicator: the M3 indicator renders a
        // stop-dot and a gap at full value, which reads as a defect on a bar whose
        // normal state is exactly full.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (fraction > 0f) {
                Spacer(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .background(barColor),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = resetsTemplate.replace("%s", formatWait(bucket.secondsUntilReset(now))),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
