package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vythera.vyxelapps.expressive.ui.StoreViewModel
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

/**
 * Live console for a module flash.
 *
 * The root manager's own stdout is the content, in a monospaced list that follows the
 * tail. This is deliberate rather than a progress spinner: module installers print
 * their compatibility checks and their reasons for refusing, and a spinner-and-verdict
 * UI throws all of that away — so a module that declines to install on your kernel
 * looks identical to one that crashed.
 *
 * Reboot is offered on success and never performed automatically.
 */
@Composable
fun ModuleInstallSheet(
    state: StoreViewModel.ModuleInstallState,
    onDismiss: () -> Unit,
    onReboot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.item ?: return
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val s = com.vythera.vyxelapps.LocalStrings.current
    val listState = rememberLazyListState()

    // Follow the tail as lines arrive, which is what makes it read as a console
    // rather than a log you have to chase.
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            // Swallows taps so the screen behind cannot be operated through the
            // sheet — and, while a flash is running, so it cannot be dismissed.
            .clickable(enabled = !state.running, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .glassSurface(MaterialTheme.colorScheme.surfaceContainer, VyxelShapeTokens.Card)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            state.running -> xs.moduleInstalling
                            state.success == true -> xs.moduleInstalled
                            else -> xs.moduleInstallFailed
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.success == false) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.running) ContainedLoadingIndicator()
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .clip(VyxelShapeTokens.Card)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(state.lines) { _, line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Reboot only once it worked, and only as an offer.
                if (state.success == true) {
                    SheetAction(
                        label = xs.moduleReboot,
                        modifier = Modifier.weight(1f),
                        primary = true,
                        onClick = onReboot,
                    )
                }
                SheetAction(
                    label = if (state.running) s.cancel else xs.moduleCloseConsole,
                    modifier = Modifier.weight(1f),
                    primary = false,
                    enabled = !state.running,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .clip(VyxelShapeTokens.Pill)
            .background(
                if (primary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                primary -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
