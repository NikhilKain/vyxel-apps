package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.Platform
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.ui.theme.SourceColors
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles

val SourceId.brandColor: Color
    get() = when (this) {
        SourceId.GitHub -> SourceColors.GitHub
        SourceId.GitLab -> SourceColors.GitLab
        SourceId.FDroid -> SourceColors.FDroid
        SourceId.IzzyOnDroid -> SourceColors.IzzyOnDroid
        SourceId.Codeberg -> SourceColors.Codeberg
        SourceId.Flathub -> SourceColors.Flathub
        SourceId.WinGet -> SourceColors.WinGet
        SourceId.Aurora -> SourceColors.Aurora
        SourceId.Aptoide -> SourceColors.Aptoide
        SourceId.ApkPure -> SourceColors.ApkPure
        SourceId.MagiskAlt -> SourceColors.MagiskAlt
        SourceId.Googlers -> SourceColors.Googlers
        SourceId.XposedRepo -> SourceColors.MagiskAlt
        SourceId.MagiskLegacy -> SourceColors.Googlers
    }

val Platform.icon: ImageVector
    get() = when (this) {
        Platform.Android -> Icons.Filled.Android
        Platform.Linux -> Icons.Filled.Laptop
        Platform.Windows -> Icons.Filled.DesktopWindows
        // A module is a package that plugs into the system, not a device it runs on.
        Platform.Module -> Icons.Filled.Extension
    }

/** Small brand-tinted pill identifying where an entry came from. */
@Composable
fun SourceBadge(
    source: SourceId,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val color = source.brandColor
    Row(
        modifier = modifier
            .clip(VyxelShapeTokens.Chip)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 6.dp else 7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = source.displayName,
            style = VyxelTextStyles.Overline,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
    }
}

/**
 * Filter chip for the source picker. Animates both its fill and its border so the
 * selected state reads clearly without a layout shift.
 */
@Composable
fun SourceFilterChip(
    source: SourceId,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brand = source.brandColor
    val container by animateColorAsState(
        targetValue = if (selected) brand.copy(alpha = 0.22f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = VyxelMotion.fade(),
        label = "chipContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) brand else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = VyxelMotion.fade(),
        label = "chipBorder",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = VyxelMotion.bouncy(),
        label = "chipDot",
    )

    Row(
        modifier = modifier
            .clip(VyxelShapeTokens.Chip)
            .background(container)
            .border(BorderStroke(1.dp, borderColor), VyxelShapeTokens.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                .clip(CircleShape)
                .background(brand)
        )
        Text(
            text = source.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = source.platform.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
    }
}
