package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.ui.formatCount
import com.vythera.vyxelapps.expressive.ui.formatRelativeTime
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

/**
 * Tall card used inside home rails.
 *
 * The corner radius animates on press alongside the scale — that shape shift is the
 * cheapest way to make a tap feel physical, and it's the core Material 3 Expressive
 * gesture.
 */
@Composable
fun AppRailCard(
    item: AppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 34.dp else 26.dp,
        animationSpec = VyxelMotion.expressive(),
        label = "cardCorner",
    )
    val scale by rememberPressScale(interaction)

    Column(
        modifier = modifier
            .width(168.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(corner))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        AppIcon(item = item, size = 64.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.displaySummary.ifBlank { item.author.orEmpty() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        MetaRow(item)
    }
}

/** Row used in search results and list views. */
@Composable
fun AppListRow(
    item: AppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, pressedScale = 0.98f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(item = item, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.verified) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = com.vythera.vyxelapps.expressive.ui
                            .LocalExpressiveStrings.current.verified,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.displaySummary.ifBlank { item.packageName.orEmpty() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            MetaRow(item)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Source badge plus whichever popularity signal this source actually provides. */
@Composable
private fun MetaRow(item: AppItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceBadge(item.source, compact = true)

        when {
            item.stars > 0 -> MetaStat(Icons.Filled.Star, formatCount(item.stars.toLong()))
            item.installs > 0 -> MetaStat(Icons.Filled.Download, formatCount(item.installs))
            item.updatedAt > 0 -> Text(
                text = formatRelativeTime(item.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MetaStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    if (label.isBlank()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

/** Placeholder card shown while a rail is loading. */
@Composable
fun AppRailCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(168.dp)
            .clip(VyxelShapeTokens.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(VyxelShapeTokens.Icon).shimmer())
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(8.dp)).shimmer())
    }
}
