package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.install.DownloadState
import com.vythera.vyxelapps.expressive.ui.formatSize
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens

/** What the button should offer, independent of any in-flight download. */
enum class InstallAction { Install, Update, Open, Unavailable }

/**
 * The store's primary action control.
 *
 * Rather than swapping in a separate progress bar, the button itself becomes the
 * progress track: a tinted fill sweeps across the same pill while the label
 * cross-fades. That keeps the control in one place through the whole
 * download -> install -> open journey instead of making it jump around.
 */
@Composable
fun InstallButton(
    action: InstallAction,
    state: DownloadState,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, pressedScale = 0.94f)

    val container = when {
        state is DownloadState.Failed -> scheme.errorContainer
        action == InstallAction.Open -> scheme.secondaryContainer
        action == InstallAction.Update -> scheme.tertiaryContainer
        action == InstallAction.Unavailable -> scheme.surfaceContainerHigh
        else -> scheme.primaryContainer
    }
    val onContainer = when {
        state is DownloadState.Failed -> scheme.onErrorContainer
        action == InstallAction.Open -> scheme.onSecondaryContainer
        action == InstallAction.Update -> scheme.onTertiaryContainer
        action == InstallAction.Unavailable -> scheme.onSurfaceVariant
        else -> scheme.onPrimaryContainer
    }

    val busy = state is DownloadState.Connecting ||
        state is DownloadState.Downloading ||
        state is DownloadState.Installing

    val progress = (state as? DownloadState.Downloading)?.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = VyxelMotion.smooth(),
        label = "installProgress",
    )

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(if (compact) 38.dp else 48.dp)
            .defaultMinSize(minWidth = if (compact) 96.dp else 132.dp)
            .clip(VyxelShapeTokens.Pill)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = action != InstallAction.Unavailable,
            ) {
                // Haptic on the store's primary action.
                //
                // Individually imperceptible; collectively it is a large part of
                // what separates an app that feels built from one that feels
                // assembled. Cancelling gets the lighter tick — confirming and
                // undoing should not feel identical.
                haptics.performHapticFeedback(
                    if (busy) HapticFeedbackType.TextHandleMove
                    else HapticFeedbackType.LongPress
                )
                when {
                    busy -> onCancel()
                    action == InstallAction.Open -> onOpen()
                    else -> onInstall()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Progress fill rides inside the same pill.
        if (state is DownloadState.Downloading && progress >= 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .align(Alignment.CenterStart)
                    .background(onContainer.copy(alpha = 0.20f))
            )
        }

        AnimatedContent(
            targetState = ButtonFace.of(action, state),
            transitionSpec = {
                (fadeIn(VyxelMotion.fade(160)) + scaleIn(VyxelMotion.expressive(), initialScale = 0.82f))
                    .togetherWith(
                        fadeOut(VyxelMotion.fade(120)) +
                            scaleOut(VyxelMotion.expressive(), targetScale = 1.12f)
                    )
            },
            label = "installFace",
        ) { face ->
            Row(
                modifier = Modifier.padding(horizontal = if (compact) 14.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when (face) {
                    ButtonFace.Working -> {
                        PulsingDot(onContainer)
                        Text(
                            text = workingLabel(state),
                            style = MaterialTheme.typography.labelLarge,
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = face.icon,
                            contentDescription = null,
                            tint = onContainer,
                            modifier = Modifier.size(if (compact) 16.dp else 19.dp),
                        )
                        Text(
                            text = face.label(),
                            style = MaterialTheme.typography.labelLarge,
                            color = onContainer,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private enum class ButtonFace(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Install(Icons.Filled.Download),
    Update(Icons.Filled.Upgrade),
    Open(Icons.Filled.Launch),
    Done(Icons.Filled.Check),
    Retry(Icons.Filled.ErrorOutline),
    Unavailable(Icons.Filled.ErrorOutline),
    Working(Icons.Filled.Download);

    /**
     * Resolved per composition, not stored on the enum: the enum is a process-wide
     * singleton, so a baked-in label would keep whatever language was active when the
     * class first loaded, even after the user picks another one.
     */
    @Composable
    fun label(): String {
        val s = com.vythera.vyxelapps.LocalStrings.current
        val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
        return when (this) {
            Install -> s.install
            Update -> s.updateLabel
            Open -> xs.openLabel
            Done -> xs.installedLabel
            Retry -> s.retry
            Unavailable -> xs.noApkShort
            Working -> ""
        }
    }

    companion object {
        fun of(action: InstallAction, state: DownloadState): ButtonFace = when {
            state is DownloadState.Failed -> Retry
            state is DownloadState.Installed -> Done
            state is DownloadState.Connecting -> Working
            state is DownloadState.Downloading -> Working
            state is DownloadState.Installing -> Working
            action == InstallAction.Open -> Open
            action == InstallAction.Update -> Update
            action == InstallAction.Unavailable -> Unavailable
            else -> Install
        }
    }
}

@Composable
private fun workingLabel(state: DownloadState): String {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    return when (state) {
        is DownloadState.Connecting -> xs.startingLabel
        is DownloadState.Installing -> xs.installingLabel
        is DownloadState.Downloading -> when {
            state.progress < 0f -> formatSize(state.bytesRead)
            else -> "${(state.progress * 100).toInt()}%"
        }
        else -> xs.workingLabel
    }
}

/** Small breathing dot that signals activity without a spinner's visual weight. */
@Composable
private fun PulsingDot(color: Color) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(620, easing = VyxelMotion.StandardEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "dotScale",
    )
    Box(
        Modifier
            .size(9.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
    )
}
