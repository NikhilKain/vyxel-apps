package com.vythera.vyxelapps

import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.vythera.vyxelapps.installer.ApkVerifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.vythera.vyxelapps.updater.AppScanResult
import com.vythera.vyxelapps.updater.ScanLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2

// ─────────────────────────────────────────────────────────────────────────────
// AppImage  — async image with shimmer loading + error fallback
// Drop-in replacement for all AsyncImage calls across the app.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppImage(
    url                : String?,
    contentDescription : String?      = null,
    modifier           : Modifier     = Modifier,
    contentScale       : ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(300)
                .build()
        },
        contentDescription = contentDescription,
        contentScale       = contentScale,
        modifier           = modifier,
        loading            = {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(20.dp),
                    color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    strokeWidth = 2.dp
                )
            }
        },
        error = {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter            = painterResource(R.drawable.ic_android_logo),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier           = Modifier.fillMaxSize().padding(6.dp)
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR  — M3 CenterAlignedTopAppBar
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(modifier: Modifier = Modifier) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter            = painterResource(R.drawable.skpic),
                    contentDescription = "Logo",
                    modifier           = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Text(
                    "Vyxel Apps",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors   = TopAppBarDefaults.topAppBarColors(
            containerColor    = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.statusBarSpace()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATION TYPES
// ─────────────────────────────────────────────────────────────────────────────
enum class NotifType { UPDATE, INSTALL, UNINSTALL, INFO }
data class AppNotification(val title: String, val body: String, val type: NotifType)

// ─────────────────────────────────────────────────────────────────────────────
// ANALOG CLOCK  — isolated composable so only it recomposes every second
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AnalogClock(accent: Color, modifier: Modifier = Modifier) {
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000L); clockMillis = System.currentTimeMillis() }
    }
    val cal    = java.util.Calendar.getInstance().apply { timeInMillis = clockMillis }
    val secF   = cal.get(java.util.Calendar.SECOND).toFloat()
    val minF   = cal.get(java.util.Calendar.MINUTE) + secF / 60f
    val hrF    = cal.get(java.util.Calendar.HOUR)   + minF / 60f
    val minDeg = minF * 6f
    val hrDeg  = hrF  * 30f
    // CYBERPUNK: a proper instrument dial — twin rings, 12 hour ticks and a
    // second hand — instead of the single faint circle. The reference art's clock
    // is a readable HUD gauge, not a suggestion of one.
    val cyberDial = LocalTheme.current.isCyberpunk()
    val secDeg = secF * 6f
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f; val cy = size.height / 2f
        val center = Offset(cx, cy)
        if (cyberDial) {
            drawCircle(accent.copy(alpha = 0.85f), r * 0.97f, center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
            drawCircle(accent.copy(alpha = 0.28f), r * 0.86f, center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            for (i in 0 until 12) {
                val a  = java.lang.Math.toRadians(i * 30.0 - 90.0)
                val ca = java.lang.Math.cos(a).toFloat(); val sa = java.lang.Math.sin(a).toFloat()
                val major = i % 3 == 0
                val inner = if (major) r * 0.72f else r * 0.79f
                drawLine(
                    color       = accent.copy(alpha = if (major) 0.95f else 0.45f),
                    start       = Offset(cx + ca * inner,      cy + sa * inner),
                    end         = Offset(cx + ca * r * 0.86f,  cy + sa * r * 0.86f),
                    strokeWidth = if (major) 2.4f else 1.2f
                )
            }
            // Sweeping second hand, in the complementary neon.
            val sAng = java.lang.Math.toRadians(secDeg.toDouble() - 90.0)
            drawLine(
                color       = CyberpunkTheme.accentAlt.copy(alpha = 0.9f),
                start       = center,
                end         = Offset(cx + java.lang.Math.cos(sAng).toFloat() * r * 0.78f,
                                     cy + java.lang.Math.sin(sAng).toFloat() * r * 0.78f),
                strokeWidth = 1.8f,
                cap         = androidx.compose.ui.graphics.StrokeCap.Round
            )
        } else {
            drawCircle(color = accent.copy(alpha = 0.50f), radius = r * 0.92f, center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        }
        val hAng = java.lang.Math.toRadians(hrDeg.toDouble() - 90.0)
        val hLen = r * 0.52f
        drawLine(color = accent, strokeWidth = 11f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            start = Offset(cx - hLen * 0.18f * java.lang.Math.cos(hAng).toFloat(), cy - hLen * 0.18f * java.lang.Math.sin(hAng).toFloat()),
            end   = Offset(cx + hLen * java.lang.Math.cos(hAng).toFloat(), cy + hLen * java.lang.Math.sin(hAng).toFloat()))
        val mAng = java.lang.Math.toRadians(minDeg.toDouble() - 90.0)
        val mLen = r * 0.70f
        drawLine(color = Color.White, strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            start = Offset(cx - mLen * 0.14f * java.lang.Math.cos(mAng).toFloat(), cy - mLen * 0.14f * java.lang.Math.sin(mAng).toFloat()),
            end   = Offset(cx + mLen * java.lang.Math.cos(mAng).toFloat(), cy + mLen * java.lang.Math.sin(mAng).toFloat()))
        drawCircle(color = Color.White, radius = 4f, center = center)
        drawCircle(color = accent,      radius = 2.5f, center = center)
    }
}

// DISCOVER HEADER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DiscoverHeader(
    profile          : UserProfile,
    onProfileClick   : () -> Unit
) {
    val t = LocalTheme.current
    val s = LocalStrings.current
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    // Greeting without the invented name. Addressing someone as "User" is worse
    // than not addressing them: it advertises that the field is a placeholder.
    // The emoji goes with it — it is the sort of ornament that reads as filler.
    fun greetingLine(greeting: String, name: String) =
        if (name.isBlank()) greeting else "$greeting, $name"
    val timeGreeting = when (hour) {
        in 0..11 -> s.goodMorning
        in 12..16 -> s.goodAfternoon
        else -> s.goodEvening
    }
    val isGlass       = LocalIsLiquidGlass.current
    val headerBase = if (t.isDark) Color(
        red   = t.accent.red   * 0.18f,
        green = t.accent.green * 0.18f,
        blue  = t.accent.blue  * 0.18f,
        alpha = 1f
    ) else t.accentContainer
    val avatarBg      = if (isGlass) t.accent.copy(alpha = 0.20f) else t.accent.copy(alpha = 0.25f)
    val onHeader      = if (isGlass) (if (t.isDark) GlassTokens.darkTextPrimary else GlassTokens.lightTextPrimary)
                        else if (t.isDark) Color.White else t.onAccentContainer
    val onHeaderMid   = onHeader.copy(alpha = 0.75f)
    val onHeaderSub   = onHeader.copy(alpha = 0.52f)
    val context = LocalContext.current

    val headerBgMod = if (isGlass) {
        val fill  = if (t.isDark) GlassTokens.darkGlassFill   else GlassTokens.lightGlassFill
        val cTop  = if (t.isDark) GlassTokens.darkChromeTop.copy(0.25f) else GlassTokens.lightChromeTop.copy(0.25f)
        Modifier
            .fillMaxWidth()
            .background(fill)
            .drawBehind {
                // Subtle chrome bottom edge
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, cTop),
                        startY = size.height - 1.dp.toPx(), endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size    = Size(size.width, 1.dp.toPx())
                )
            }
            .statusBarSpace()
    } else if (t.isCyberpunk()) {
        // CYBERPUNK: no tinted header slab. accent*0.18 painted a solid #002B2E
        // block behind the wordmark with a hard edge at the search bar — on a
        // true-black canvas that read as a washed-out teal band and flattened the
        // neon. The website's hero sits directly on the background, so we stay
        // transparent and let the grid, corner bloom and particles show through.
        Modifier
            .fillMaxWidth()
            .statusBarSpace()
    } else {
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(headerBase, headerBase.copy(alpha = 0.85f), t.bgPrimary),
                    startY = 0f,
                    endY   = 700f
                )
            )
            .statusBarSpace()
    }

    val isCyber = t.isCyberpunk()
    Box(modifier = headerBgMod) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // CYBERPUNK: parked in the empty column to the RIGHT of the
                // wordmark and BELOW the telegram button. At top=32/end=45 the
                // bright hands cut through the last letters of DISCOVER; pulling it
                // to the top instead made it collide with the telegram button. This
                // slot is clear of both.
                .padding(
                    top = if (isCyber) 62.dp else 32.dp,
                    end = if (isCyber) 10.dp else 45.dp
                )
        ) {
            if (isGlass) {
                GlassSurface(
                    modifier = Modifier.size(110.dp),
                    shape    = CircleShape
                ) {
                    AnalogClock(
                        accent   = t.accent,
                        modifier = Modifier
                            .size(110.dp)
                            .align(Alignment.Center)
                    )
                }
            } else {
                AnalogClock(
                    accent   = t.accent,
                    // …and dimmed, so where it does sit behind the type it reads as
                    // a background HUD ring instead of competing with it.
                    modifier = Modifier
                        .size(if (isCyber) 96.dp else 110.dp)
                        // Readable now that it's a real dial, but still held back
                        // from competing with the wordmark beside it.
                        .then(if (isCyber) Modifier.graphicsLayer { alpha = 0.72f } else Modifier)
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // CYBERPUNK: chamfered ID-badge frame instead of a plain circle.
                    val avatarShape = if (isCyber) CyberCutSmall else CircleShape
                    Box(
                        modifier         = Modifier
                            .size(36.dp)
                            .clip(avatarShape)
                            .background(avatarBg)
                            .then(
                                if (isCyber) Modifier.border(1.dp, t.accent.copy(alpha = 0.55f), avatarShape)
                                else Modifier
                            )
                            .clickable {onProfileClick()},
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.photoUri.isNotEmpty()) {
                            AsyncImage(
                                model              = profile.photoUri,
                                contentDescription = null,
                                modifier           = Modifier
                                    .fillMaxSize()
                                    .clip(avatarShape),
                                contentScale       = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Rounded.Person, null, tint = t.onAccentContainer, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (isCyber) {
                        // Greeting sits in its own HUD readout, as in the reference.
                        Box(
                            modifier = Modifier
                                .clip(CyberCutSmall)
                                .background(t.accent.copy(alpha = 0.07f))
                                .border(1.dp, t.accent.copy(alpha = 0.38f), CyberCutSmall)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text     = greetingLine(timeGreeting, profile.name).uppercase(),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = t.accent.copy(alpha = 0.92f),
                                    maxLines = 1
                                )
                                // Second readout line, as in the reference art.
                                Text(
                                    text     = LocalCyberStrings.current.readyToExplore.uppercase(),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = t.accentAlt.copy(alpha = 0.75f),
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Text(
                            text = greetingLine(timeGreeting, profile.name),
                            fontSize   = 12.sp,
                            color      = onHeaderMid,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                val openTelegram = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vyxelapps_announcement"))
                    )
                }
                val cyberTelegram = rememberPackPainter("cyber_telegram")
                if (isCyber && cyberTelegram != null) {
                    // CYBERPUNK: the neon-framed icon is self-contained, so it's
                    // shown bare (chamfered clip, no button container) at full size.
                    Image(
                        painter            = cyberTelegram,
                        contentDescription = "Telegram",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(46.dp)
                            .clip(CyberCutSmall)
                            .clickable { openTelegram() }
                    )
                } else {
                    FilledIconButton(
                        onClick  = { openTelegram() },
                        modifier = Modifier.size(44.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = onHeader.copy(0.12f),
                            contentColor   = onHeaderMid
                        )
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = "Telegram",
                            modifier = Modifier.size(37.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
            }
            // NEON-PUNK: gradient-filled header text (color must stay Unspecified
            // or it overrides the brush)
            val npHeaderGradient = LocalTheme.current.isNeonPunk()
            when {
                // CYBERPUNK: glitching gradient wordmark, sitting in its own pool
                // of light. The glyphs are gradient-filled rather than haloed
                // (blurred text shadows force a software path), so the bloom has
                // to come from behind them.
                LocalTheme.current.isCyberpunk() -> GlitchText(
                    text     = s.discoverTitle.uppercase(),
                    style    = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.cyberBloom(color = CyberpunkTheme.accent, alpha = 0.20f, scale = 1.15f)
                )
                else -> Text(
                    s.discoverTitle,
                    style      = if (npHeaderGradient)
                        MaterialTheme.typography.displaySmall.copy(brush = npTextGradient())
                    else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (npHeaderGradient) Color.Unspecified else onHeader
                )
            }
            Text(
                if (isCyber) s.openSourceApps.uppercase() else s.openSourceApps,
                style = if (npHeaderGradient)
                    MaterialTheme.typography.headlineSmall.copy(brush = npTextGradient(alpha = 0.72f))
                else MaterialTheme.typography.headlineSmall,
                color = if (npHeaderGradient) Color.Unspecified else onHeaderSub
            )
            if (isCyber) {
                // Underscore rule + ticks closing off the hero block.
                CyberSectionRule(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN BACKGROUNDS  —  M3 Expressive mesh-blob system
// Each screen gets a distinct radial-gradient layout that reacts to the current
// AppThemeColors (Monet / manual accent included automatically).
// ─────────────────────────────────────────────────────────────────────────────

enum class ScreenBg { HOME, SEARCH, INSTALLED, PROFILE, SETTINGS }

@Composable
fun ScreenBackground(screen: ScreenBg, modifier: Modifier = Modifier) {
    if (LocalIsLiquidGlass.current) {
        // Background rendered at the HomeScreen outer level; nothing to do here
        return
    }
    if (LocalTheme.current.isNeonPunk()) { NeonPunkBackground(screen, modifier); return }   // NEON-PUNK
    // CYBERPUNK: grid + scanlines + particles background
    if (LocalTheme.current.isCyberpunk()) { CyberpunkBackground(screen, LocalCyberpunkFx.current, modifier); return }
    val t = LocalTheme.current
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter            = painterResource(R.drawable.saturn),
            contentDescription = null,
            alpha              = 0.07f,
            colorFilter        = ColorFilter.tint(t.accent, BlendMode.SrcAtop),
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.BottomCenter)
                .offset(y = 40.dp)
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                val w = size.width
                val h = size.height
                when (screen) {

                    ScreenBg.HOME -> {
                        // Primary blob — top-right (accent1 from wallpaper)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accent.copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(w * 0.88f, h * 0.10f),
                                radius = w * 0.72f
                            )
                        )
                        // Secondary blob — bottom-left (accent2 from wallpaper)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentAlt.copy(alpha = 0.12f), Color.Transparent),
                                center = Offset(w * 0.10f, h * 0.78f),
                                radius = w * 0.58f
                            )
                        )
                        // Tertiary blob — mid-center (accent3 from wallpaper)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentTertiary.copy(alpha = 0.09f), Color.Transparent),
                                center = Offset(w * 0.50f, h * 0.46f),
                                radius = w * 0.50f
                            )
                        )
                        // Container highlight — top-left
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentContainer.copy(alpha = 0.06f), Color.Transparent),
                                center = Offset(w * 0.08f, h * 0.18f),
                                radius = w * 0.38f
                            )
                        )
                    }

                    ScreenBg.SEARCH -> {
                        // Primary spotlight — top-center (accent1)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accent.copy(alpha = 0.15f), Color.Transparent),
                                center = Offset(w * 0.50f, 0f),
                                radius = w * 0.75f
                            )
                        )
                        // Tertiary blob — bottom-right (accent3)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentTertiary.copy(alpha = 0.10f), Color.Transparent),
                                center = Offset(w * 0.88f, h * 0.85f),
                                radius = w * 0.46f
                            )
                        )
                        // Secondary blob — mid-left (accent2)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentAlt.copy(alpha = 0.07f), Color.Transparent),
                                center = Offset(w * 0.08f, h * 0.52f),
                                radius = w * 0.38f
                            )
                        )
                    }

                    ScreenBg.INSTALLED -> {
                        // Diagonal sweep — upper-left corner (accent)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(t.accent.copy(0.22f), Color.Transparent),
                                start  = Offset(0f, 0f),
                                end    = Offset(w * 0.65f, h * 0.35f)
                            )
                        )
                        // Bold blob — top-right (accentAlt)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentAlt.copy(0.24f), Color.Transparent),
                                center = Offset(w * 0.92f, h * 0.04f),
                                radius = w * 0.55f
                            )
                        )
                        // Mid-left blob (accentTertiary)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentTertiary.copy(0.20f), Color.Transparent),
                                center = Offset(w * 0.08f, h * 0.48f),
                                radius = w * 0.44f
                            )
                        )
                        // Mid-right blob (accent)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accent.copy(0.15f), Color.Transparent),
                                center = Offset(w * 0.88f, h * 0.52f),
                                radius = w * 0.36f
                            )
                        )
                        // Bottom-left sweep (accentContainer)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentContainer.copy(0.26f), Color.Transparent),
                                center = Offset(w * 0.10f, h * 0.88f),
                                radius = w * 0.52f
                            )
                        )
                        // Bottom-right blob (accentAlt)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentAlt.copy(0.18f), Color.Transparent),
                                center = Offset(w * 0.85f, h * 0.90f),
                                radius = w * 0.42f
                            )
                        )
                        // Center accent dot
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentTertiary.copy(0.12f), Color.Transparent),
                                center = Offset(w * 0.50f, h * 0.55f),
                                radius = w * 0.30f
                            )
                        )
                    }

                    ScreenBg.PROFILE -> {
                        // Primary blob — top-center avatar zone (accent1)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accent.copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(w * 0.50f, h * 0.14f),
                                radius = w * 0.65f
                            )
                        )
                        // Tertiary blob — bottom-right (accent3)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentTertiary.copy(alpha = 0.11f), Color.Transparent),
                                center = Offset(w * 0.88f, h * 0.80f),
                                radius = w * 0.50f
                            )
                        )
                        // Secondary blob — bottom-left (accent2)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentAlt.copy(alpha = 0.08f), Color.Transparent),
                                center = Offset(w * 0.10f, h * 0.76f),
                                radius = w * 0.42f
                            )
                        )
                    }

                    ScreenBg.SETTINGS -> {
                        // Primary blob — top-right (accent1)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accent.copy(alpha = 0.11f), Color.Transparent),
                                center = Offset(w * 0.88f, h * 0.07f),
                                radius = w * 0.50f
                            )
                        )
                        // Tertiary blob — mid-screen (accent3)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentTertiary.copy(alpha = 0.09f), Color.Transparent),
                                center = Offset(w * 0.50f, h * 0.45f),
                                radius = w * 0.44f
                            )
                        )
                        // Secondary blob — mid-left (accent2)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentAlt.copy(alpha = 0.07f), Color.Transparent),
                                center = Offset(w * 0.08f, h * 0.48f),
                                radius = w * 0.38f
                            )
                        )
                        // Container blob — bottom-center
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(t.accentContainer.copy(alpha = 0.06f), Color.Transparent),
                                center = Offset(w * 0.50f, h * 0.88f),
                                radius = w * 0.42f
                            )
                        )
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME SEARCH BAR  — M3 SearchBar (collapsed / inactive state)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSearchBar(
    onSearchClick : () -> Unit = {},
    modifier      : Modifier   = Modifier
) {
    val s = LocalStrings.current
    if (LocalIsLiquidGlass.current) {
        GlassSearchBar(
            onSearchClick = onSearchClick,
            onFilterClick = onSearchClick,
            modifier      = modifier
        )
        return
    }
    // Deliberately NOT an M3 SearchBar: it embeds a real editable field, and
    // EMUI 8 re-focuses the first editable field on every window-focus event,
    // firing onExpandedChange(true) → search screen + keyboard open in a loop
    // (user report: startInputReason=3 repeating). A Surface has no IME target.
    val cyberBar = LocalTheme.current.isCyberpunk()   // CYBERPUNK: notched glowing search bar
    val searchShape = if (cyberBar) remember { CyberPanelShape(cut = 8.dp, notch = 4.dp) }
                      else CircleShape
    Surface(
        onClick  = onSearchClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .then(if (cyberBar) Modifier.neonGlowGradient(searchShape, glow = 14.dp) else Modifier)
            // Must match the panel's 8dp chamfer and 4dp notches.
            .then(if (cyberBar) Modifier.cyberDoubleEdge(cut = 8.dp, inset = 4.dp, notch = 4.dp) else Modifier),
        shape = searchShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                s.searchHint,
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // CYBERPUNK: chamfered, rimmed filter control — the reference frames
            // it like the rest of the HUD; a round tonal disc read as Material.
            FilledTonalIconButton(
                onClick  = onSearchClick,
                shape    = if (cyberBar) CyberCutSmall else IconButtonDefaults.filledShape,
                colors   = if (cyberBar) IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = LocalTheme.current.accentAlt.copy(alpha = 0.10f),
                    contentColor   = LocalTheme.current.accentAlt
                ) else IconButtonDefaults.filledTonalIconButtonColors(),
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (cyberBar) Modifier.border(1.2.dp, LocalTheme.current.accentAlt.copy(alpha = 0.65f), CyberCutSmall)
                        else Modifier
                    )
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_filter_logo),
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME SOURCE CHIPS  — M3 FilterChip
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeSourceChipsRow(
    selectedSource : AppSource?,
    onSourceSelect : (AppSource?) -> Unit,
    modifier       : Modifier = Modifier
) {
    val chips = remember {
        listOf(
            Triple(null,               "All Sources",  R.drawable.all),
            Triple(AppSource.GITHUB,   "GitHub",       R.drawable.github),
            Triple(AppSource.FDROID,   "F-Droid",      R.drawable.fdroid),
            Triple(AppSource.GITLAB,   "GitLab",       R.drawable.gitlab),
            Triple(AppSource.CODEBERG, "Codeberg",     R.drawable.codeberg),
            Triple(AppSource.IZZY,     "IzzyOnDroid",  R.drawable.ic_izzy_logo),
            Triple(AppSource.FLATHUB,  "Flathub",      R.drawable.flathub),
            Triple(AppSource.WINGET,   "Winget",       R.drawable.winget)
        )
    }
    val isGlass = LocalIsLiquidGlass.current
    val t       = LocalTheme.current

    LazyRow(
        modifier              = modifier,
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = chips) { (src, label, iconRes) ->
            val selected = selectedSource == src

            if (isGlass) {
                val accent   = MaterialTheme.colorScheme.primary
                val onAccent = MaterialTheme.colorScheme.onPrimary
                val textCol  = if (selected) onAccent
                               else if (t.isDark) GlassTokens.darkTextSecondary else GlassTokens.lightTextSecondary
                val chipMod  = Modifier
                    .height(38.dp)
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(accent) else Modifier)
                    .clickable { onSourceSelect(src) }
                Box(modifier = chipMod) {
                    // Glass surface for unselected chips
                    if (!selected) {
                        GlassSurface(
                            modifier = Modifier.matchParentSize(),
                            shape    = CircleShape
                        ) {}
                    }
                    Row(
                        modifier              = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxHeight(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter            = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier           = Modifier.size(24.dp)
                        )
                        Text(
                            label,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color      = textCol
                        )
                    }
                }
            } else {
                // CYBERPUNK: chamfered chips, selected one gets a gradient neon glow
                val cyberChip = LocalTheme.current.isCyberpunk()
                val cyberSel  = selected && cyberChip
                val chipShape = if (cyberChip) CyberCutSmall else CircleShape
                FilterChip(
                    selected    = selected,
                    onClick     = { onSourceSelect(src) },
                    label       = {
                        Text(
                            label,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Image(
                            painter            = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp)
                        )
                    },
                    shape    = chipShape,
                    modifier = Modifier.height(40.dp)
                        .then(if (cyberSel) Modifier.neonGlowGradient(chipShape, glow = 12.dp) else Modifier)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FEATURED CARD
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FeaturedCard(
    apps: List<GitHubRepo>,
    seed: Int = 0,
    /**
     * CDN-pinned promos, shown first and never filtered.
     *
     * Deliberately exempt from the [LocalApkAbsentIds] filter: that set is built by a
     * background probe of the catalog, and a freshly promoted app is usually not in it
     * yet, so applying it would silently drop the pin on first launch.
     */
    pinned: List<GitHubRepo> = emptyList(),
    /**
     * Overline text per pinned repo id, replacing "FEATURED".
     *
     * A promo slot should say what it is. Keyed by id rather than carried on
     * [GitHubRepo] so the shared model doesn't grow a field only the home hero reads.
     */
    pinnedLabels: Map<Long, String> = emptyMap(),
    onAppClick: (GitHubRepo) -> Unit
) {
    val absentIds  = LocalApkAbsentIds.current
    val pinnedIds  = remember(pinned) { pinned.map { it.id }.toSet() }
    val pool       = (if (absentIds.isEmpty()) apps else apps.filterNot { it.id in absentIds })
        .filterNot { it.id in pinnedIds }
    if (pool.isEmpty() && pinned.isEmpty()) return
    val t          = LocalTheme.current
    val context    = LocalContext.current
    val isGlass    = LocalIsLiquidGlass.current
    // Deterministic pick so returning from a detail keeps the same five cards.
    val featApps   = remember(pool, seed, pinned) {
        pinned + pool.shuffled(kotlin.random.Random(seed.toLong())).take(5)
    }
    val pagerState = rememberPagerState(pageCount = { featApps.size })

    LaunchedEffect(Unit) {
        while (true) {
            delay(5500)
            val next = (pagerState.currentPage + 1) % featApps.size
            pagerState.animateScrollToPage(next, animationSpec = tween(900, easing = EaseInOutCubic))
        }
    }

    // Pager dot indicator color
    val dotActive   = if (isGlass) (if (t.isDark) GlassTokens.darkAccent else GlassTokens.lightAccent)
                      else MaterialTheme.colorScheme.primary
    val dotInactive = if (isGlass) (if (t.isDark) GlassTokens.darkChromeTop else GlassTokens.lightChromeTop)
                      else MaterialTheme.colorScheme.outlineVariant

    Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val repo = featApps[page]
            val overline = pinnedLabels[repo.id]?.uppercase() ?: "FEATURED"

            if (isGlass) {
                // ── Glass featured card ───────────────────────────────────────
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(200.dp)
                        .clickable { onAppClick(repo) },
                    shape    = RoundedCornerShape(24.dp)
                ) {
                    // Subtle accent gradient tint over the glass
                    val accent = if (t.isDark) GlassTokens.darkAccent else GlassTokens.lightAccent
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.04f))
                                )
                            )
                    )
                    // NEON-PUNK: neon lights sweeping around the hero banner's edge
                    if (LocalTheme.current.isNeonPunk()) {
                        Box(Modifier.fillMaxSize().then(npHeroBorder(RoundedCornerShape(24.dp))))
                    }
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left: text content
                        val textPri = if (t.isDark) GlassTokens.darkTextPrimary else GlassTokens.lightTextPrimary
                        val textSec = if (t.isDark) GlassTokens.darkTextSecondary else GlassTokens.lightTextSecondary
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.20f))
                                        .border(1.dp, accent.copy(0.35f), RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        overline,
                                        fontSize      = 10.sp,
                                        fontWeight    = FontWeight.ExtraBold,
                                        color         = accent,
                                        letterSpacing = 1.sp,
                                        modifier      = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    repo.displayName,
                                    fontSize   = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = textPri,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(5.dp))
                                if (!repo.description.isNullOrEmpty()) {
                                    Text(
                                        repo.description,
                                        fontSize   = 12.sp,
                                        color      = textSec,
                                        maxLines   = 2,
                                        lineHeight = 16.sp,
                                        overflow   = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            val buttonLabel = when (repo.source) {
                                AppSource.GITHUB   -> "View on GitHub"
                                AppSource.GITLAB   -> "View on GitLab"
                                AppSource.FDROID   -> "View on F-Droid"
                                AppSource.CODEBERG -> "View on Codeberg"
                                AppSource.FLATHUB  -> "View on Flathub"
                                AppSource.WINGET   -> "View on Winget"
                                AppSource.IZZY     -> "View on IzzyOnDroid"
                                AppSource.APTOIDE  -> "View on Aptoide"
                                AppSource.MODULE   -> "View module"
                                null               -> "View App"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.18f))
                                    .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(50))
                                    .clickable {
                                        if (repo.html_url.isNotEmpty()) {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.html_url))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(buttonLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        // Right: glass icon frame
                        Box(
                            modifier         = Modifier
                                .width(130.dp)
                                .fillMaxHeight()
                                .padding(end = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(82.dp, 114.dp)
                                    .offset(y = 16.dp)
                                    .graphicsLayer { rotationZ = -12f }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (t.isDark) GlassTokens.darkGlassFill else GlassTokens.lightGlassFill)
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                if (t.isDark) GlassTokens.darkChromeTop else GlassTokens.lightChromeTop,
                                                if (t.isDark) GlassTokens.darkChromeBot else GlassTokens.lightChromeBot
                                            )
                                        ),
                                        RoundedCornerShape(20.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .offset(y = (-16).dp)
                                    .graphicsLayer { rotationZ = -12f }
                                    .clip(MaterialTheme.shapes.large)
                            ) {
                                AsyncImage(
                                    model = remember(repo.iconUrlOrNull) {
                                        ImageRequest.Builder(context)
                                            .data(repo.iconUrlOrNull)
                                            .crossfade(300).build()
                                    },
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize(),
                                    contentScale       = ContentScale.Crop,
                                    error              = painterResource(R.drawable.ic_android_logo),
                                    placeholder        = painterResource(R.drawable.ic_android_logo)
                                )
                            }
                        }
                    }
                }
            } else {
            // CYBERPUNK: chamfered HUD panel + gradient neon glow; other themes keep 24dp
            val cyber      = LocalTheme.current.isCyberpunk()
            val featRim    = remember(repo.id) { cyberRimFor(repo.id.toInt()) }
            val featShape  = if (cyber) remember { CyberPanelShape(cut = 16.dp, notch = 5.dp) }
                             else RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(200.dp)
                    // Colored elevation shadow is on here: it's the expensive bit,
                    // but the featured card is a single instance per screen, not a
                    // list item, so it can afford a real two-tone bloom.
                    .then(if (cyber) Modifier.neonGlowGradient(featShape, glow = 30.dp, shadowOn = true, rim = featRim) else Modifier)
                    .clip(featShape)
                    .clickable { onAppClick(repo) }
                    // Parallel inner edge above the fill, inside the neon rim,
                    // matching the panel's 16dp chamfer and 5dp notches.
                    .then(
                        if (cyber) Modifier.cyberDoubleEdge(cut = 16.dp, inset = 6.dp, notch = 5.dp, rim = featRim)
                        else Modifier
                    )
            ) {
                // Monet gradient: primary → dark/white depending on theme.
                // CYBERPUNK: a dark HUD panel instead. The full-bleed
                // primary→primaryContainer fill made this the brightest object on
                // a true-black screen, so the neon rim had nothing to contrast
                // against. The website's panels are dark (--surface) and let the
                // cyan/magenta border and gradient type carry the colour.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (cyber) Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.colorScheme.surface
                                )
                            ) else Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        )
                )
                if (cyber) {
                    // Whisper of cyan→magenta across the panel so it still reads
                    // as neon-lit rather than plain grey.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(t.accent.copy(alpha = 0.10f), t.accentAlt.copy(alpha = 0.07f))
                                )
                            )
                    )
                }
                // Dark scrim for depth — unnecessary over the already-dark
                // cyberpunk panel, where it would only mute the accent wash.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (cyber) 0.10f else 0.28f))
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    // ── Left: text content ────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Transparent FEATURED badge — CYBERPUNK gets the
                            // reference's chamfered, magenta-rimmed tag.
                            Surface(
                                shape    = if (cyber) CyberCutSmall else RoundedCornerShape(6.dp),
                                color    = if (cyber) t.accentAlt.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.20f),
                                modifier = if (cyber)
                                    Modifier.border(1.dp, t.accentAlt.copy(alpha = 0.70f), CyberCutSmall)
                                else Modifier
                            ) {
                                Text(
                                    overline,
                                    fontSize      = 10.sp,
                                    fontWeight    = FontWeight.ExtraBold,
                                    color         = if (cyber) t.accentAlt else Color.White,
                                    letterSpacing = 1.sp,
                                    modifier      = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // App name — big bold; uppercase in cyberpunk to match
                            // the reference's wordmark treatment.
                            Text(
                                text       = if (cyber) repo.displayName.uppercase() else repo.displayName,
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.cyberGlitchName(repo.id.toInt())   // CYBERPUNK (featured — single instance)
                            )
                            Spacer(Modifier.height(5.dp))
                            // About / description
                            if (!repo.description.isNullOrEmpty()) {
                                Text(
                                    text       = repo.description,
                                    fontSize   = 12.sp,
                                    color      = Color.White.copy(alpha = 0.78f),
                                    maxLines   = 2,
                                    lineHeight = 16.sp,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // M3 Expressive pill button — opens html_url in browser
                        val buttonLabel = when (repo.source) {
                            AppSource.GITHUB   -> "View on GitHub"
                            AppSource.GITLAB   -> "View on GitLab"
                            AppSource.FDROID   -> "View on F-Droid"
                            AppSource.CODEBERG -> "View on Codeberg"
                            AppSource.FLATHUB  -> "View on Flathub"
                            AppSource.WINGET   -> "View on Winget"
                            AppSource.IZZY     -> "View on IzzyOnDroid"
                            AppSource.APTOIDE  -> "View on Aptoide"
                            AppSource.MODULE   -> "View module"
                            null               -> "View App"
                        }
                        Button(
                            onClick = {
                                if (repo.html_url.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.html_url))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                            },
                            // CYBERPUNK: outlined chamfered CTA rather than a
                            // translucent white pill — the reference's buttons are
                            // rimmed frames, and a light fill on a dark HUD panel
                            // pulled focus away from the neon.
                            shape          = if (cyber) CyberCutSmall else MaterialTheme.shapes.extraLarge,
                            colors         = ButtonDefaults.buttonColors(
                                containerColor = if (cyber) t.accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.22f),
                                contentColor   = if (cyber) t.accent else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier       = Modifier
                                .height(34.dp)
                                .then(
                                    if (cyber) Modifier.border(1.2.dp, t.accent.copy(alpha = 0.75f), CyberCutSmall)
                                    else Modifier
                                )
                        ) {
                            Text(
                                if (cyber) buttonLabel.uppercase() else buttonLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier           = Modifier.size(13.dp)
                            )
                        }
                    }

                    // ── Right: tilted decorative boxes ────────────────────────
                    Box(
                        modifier         = Modifier
                            .width(130.dp)
                            .fillMaxHeight()
                            .padding(end = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Large dark box — behind, shifted down
                        Surface(
                            modifier = Modifier
                                .size(82.dp, 114.dp)
                                .offset(y = 16.dp)
                                .graphicsLayer { rotationZ = -12f },
                            shape = RoundedCornerShape(20.dp),
                            color = if (t.isDark) Color.Black.copy(alpha = 0.55f)
                                    else Color(0xFF1C1C1C).copy(alpha = 0.20f)
                        ) {}
                        // Small logo box — in front, shifted up
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .offset(y = (-16).dp)
                                .graphicsLayer { rotationZ = -12f },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            AsyncImage(
                                model = remember(repo.iconUrlOrNull) {
                                    ImageRequest.Builder(context)
                                        .data(repo.iconUrlOrNull)
                                        .crossfade(300)
                                        .build()
                                },
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Crop,
                                error              = painterResource(R.drawable.ic_android_logo),
                                placeholder        = painterResource(R.drawable.ic_android_logo)
                            )
                        }
                    }
                }
            }
            } // end else (non-glass)
        }

        // Pager indicator dots
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(featApps.size) { i ->
                val sel = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(4.dp)
                        .width(if (sel) 18.dp else 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (sel) dotActive else dotInactive)
                )
            }
        }
    }
}

// DockItem removed — NavigationBarItem handles tab item rendering natively in M3

// ─────────────────────────────────────────────────────────────────────────────
// HERO BANNER  — unchanged logic, improved visual shape
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroBanner(apps: List<GitHubRepo>, onAppClick: (GitHubRepo) -> Unit) {
    if (apps.isEmpty()) return
    val t       = LocalTheme.current
    val context = LocalContext.current

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    val bannerApps = remember(apps) { apps.shuffled().take(7) }
    val pagerState = rememberPagerState(pageCount = { bannerApps.size })

    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            val next = (pagerState.currentPage + 1) % bannerApps.size
            pagerState.animateScrollToPage(next, animationSpec = tween(1400, easing = EaseInOutCubic))
        }
    }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
        ) { page ->
            val repo = bannerApps[page]

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.60f)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = 2.dp,
                            bottomEnd = 32.dp
                        )
                    )   // M3 extraLarge shape
                    .clickable { onAppClick(repo) }
            ) {
                // Base surface colour (no vivid gradients — uses theme surface)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                // Owner avatar as subtle hero image
                AsyncImage(
                    model = remember(repo.iconUrlOrNull) {
                        ImageRequest.Builder(context)
                            .data(repo.iconUrlOrNull)
                            .crossfade(400)
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Scrim for text legibility
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                ),
                                startY = 300f
                            )
                        )

                )
                // Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label   = {
                            Text(
                                "FEATURED",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        repo.displayName,
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                    if (!repo.description.isNullOrEmpty()) {
                        Text(
                            repo.description,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = Color.White.copy(0.80f),
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(bannerApps.size) { i ->
                val selected = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(4.dp)
                        .width(if (selected) 24.dp else 6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────
// M3 TAG  — M3 SuggestionChip for language / platform labels
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun M3Tag(
    label    : String,
    emoji    : String? = null,
    modifier : Modifier = Modifier
) {
    SuggestionChip(
        onClick  = {},
        label    = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (emoji != null) Text(emoji, style = MaterialTheme.typography.labelSmall)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// APP CARD  — M3 ElevatedCard
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Generated stand-in for an app with no usable artwork.
 *
 * A deterministic two-tone tile with the app's initial: the same app always gets
 * the same colours, so a grid of them still reads as one designed system rather
 * than a row of identical grey placeholders.
 */
@Composable
fun RepoMonogram(repo: GitHubRepo, modifier: Modifier = Modifier) {
    val seed = (repo.full_name.ifBlank { repo.name }).hashCode()
    val hue  = ((seed % 360) + 360) % 360
    val top  = Color.hsl(hue.toFloat(), 0.52f, 0.42f)
    val bot  = Color.hsl(((hue + 38) % 360).toFloat(), 0.55f, 0.28f)
    val letter = repo.displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(top, bot))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
fun AppCard(repo: GitHubRepo, isInstalled: Boolean = false, modifier: Modifier = Modifier.width(280.dp), onClick: () -> Unit) {
    val context   = LocalContext.current
    val isGlass   = LocalIsLiquidGlass.current
    val t         = LocalTheme.current
    val platforms = remember(repo.id) { detectPlatformLabels(repo) }
    val imageModel = remember(repo.iconUrlOrNull) {
        ImageRequest.Builder(context)
            .data(repo.iconUrlOrNull)
            .crossfade(300)
            .build()
    }

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon — glass-aware avatar background
            val avatarBg = if (isGlass)
                (if (t.isDark) GlassTokens.darkGlassFill else GlassTokens.lightGlassFill)
            else MaterialTheme.colorScheme.surfaceContainerHighest
            Box(
                modifier = Modifier
                    // CYBERPUNK: the icon sits on a lit pad — one radial gradient,
                    // cheap enough per list item, and it stops the artwork from
                    // floating on dead black.
                    .then(
                        if (t.isCyberpunk())
                            Modifier.cyberBloom(color = CyberpunkTheme.accent, alpha = 0.30f, scale = 1.45f)
                        else Modifier
                    )
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center,
            ) {
                // The monogram sits underneath rather than being an `error`
                // painter, so it covers every no-artwork case at once: a filtered
                // personal avatar, a source that ships no icons, and a failed
                // load. A generic robot in a grid of real icons is the thing that
                // makes a catalogue look scraped.
                RepoMonogram(repo)
                if (repo.iconUrlOrNull != null) {
                    AsyncImage(
                        model              = imageModel,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val ownerColor = if (isGlass)
                    (if (t.isDark) GlassTokens.darkAccent else GlassTokens.lightAccent)
                else MaterialTheme.colorScheme.primary
                val nameColor = if (isGlass)
                    (if (t.isDark) GlassTokens.darkTextPrimary else GlassTokens.lightTextPrimary)
                else MaterialTheme.colorScheme.onSurface
                val descColor = if (isGlass)
                    (if (t.isDark) GlassTokens.darkTextSecondary else GlassTokens.lightTextSecondary)
                else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    "@${repo.owner.login}",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = ownerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    repo.displayName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = nameColor,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (!repo.description.isNullOrEmpty()) {
                    Text(
                        repo.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = descColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (repo.stargazers_count > 0) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Rounded.Star, null, tint = StarGold, modifier = Modifier.size(12.dp))
                            Text(
                                formatStars(repo.stargazers_count),
                                style = MaterialTheme.typography.labelSmall,
                                color = descColor
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (platform in platforms) {
                            if (platform.iconRes != null) {
                                Icon(
                                    painter            = painterResource(platform.iconRes),
                                    contentDescription = null,
                                    modifier           = Modifier.size(14.dp),
                                    tint               = descColor
                                )
                            } else {
                                Text(platform.emoji, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (isGlass) {
        GlassSurface(
            modifier = modifier
                .clip(RoundedCornerShape(GlassTokens.cardRadius))
                .clickable { onClick() },
            shape    = RoundedCornerShape(GlassTokens.cardRadius)
        ) { cardContent() }
    } else {
        // CYBERPUNK: notched HUD panel with a gradient neon rim. The rim pair is
        // seeded from the repo id, so a scrolling row lights up in orange/yellow,
        // green/cyan, violet/magenta … instead of every card being cyan→magenta.
        val cyber = t.isCyberpunk()
        val rim   = remember(repo.id) { cyberRimFor(repo.id.toInt()) }
        val cardShape = if (cyber) remember { CyberPanelShape(cut = 11.dp, notch = 4.dp) }
                        else MaterialTheme.shapes.large
        ElevatedCard(
            onClick   = onClick,
            modifier  = modifier
                .height(120.dp)
                .then(if (cyber) Modifier.neonGlowGradient(
                    cardShape, shadowOn = false, light = true,   // 2-stroke rim → smooth in long lists
                    rim = rim
                ) else Modifier),
            shape     = cardShape,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp, pressedElevation = 8.dp),
            colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (cyber) {
                // Parallel inner edge line that breaks partway round the panel,
                // tracking the same notches and the same two neons as the rim.
                Box(Modifier.cyberDoubleEdge(cut = 11.dp, inset = 4.dp, notch = 4.dp, rim = rim)) {
                    cardContent()
                }
            } else cardContent()
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// APP LIST TILE  — M3 Card + ListItem
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppListTile(
    repo          : GitHubRepo,
    isInstalled   : Boolean = false,
    tileIndex     : Int     = 0,
    useTileColors : Boolean = false,
    onClick       : () -> Unit
) {
    val context   = LocalContext.current
    val isGlass   = LocalIsLiquidGlass.current
    // Tile accent colors are disabled in Liquid Glass mode (uniform glass look)
    val tileColor = if (isGlass || !useTileColors) null else tileColors[tileIndex % tileColors.size]
    val glowColor = tileColor?.vivid()

    val tileContent: @Composable () -> Unit = {
        ListItem(
            headlineContent = {
                Text(
                    repo.displayName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            },
            overlineContent = {
                Text(
                    "@${repo.owner.login}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            supportingContent = if (!repo.description.isNullOrEmpty() || repo.stargazers_count > 0 || isInstalled) ({
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!repo.description.isNullOrEmpty()) {
                        Text(
                            repo.description,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (repo.stargazers_count > 0) {
                            Icon(Icons.Rounded.Star, null, tint = StarGold, modifier = Modifier.size(11.dp))
                            Text(formatStars(repo.stargazers_count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!repo.language.isNullOrEmpty()) {
                            Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(repo.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isInstalled) {
                            SuggestionChip(
                                onClick = {},
                                label   = { Text("Installed", style = MaterialTheme.typography.labelSmall) },
                                colors  = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = GreenOk.copy(alpha = 0.12f),
                                    labelColor     = GreenOk
                                )
                            )
                        }
                        if (repo.source != null && repo.source != AppSource.GITHUB) {
                            SourceBadge(source = repo.source)
                        }
                    }
                }
            }) else null,
            leadingContent = {
                AsyncImage(
                    model              = remember(repo.iconUrlOrNull) {
                        ImageRequest.Builder(context)
                            .data(repo.iconUrlOrNull)
                            .crossfade(200)
                            .build()
                    },
                    contentDescription = null,
                    modifier           = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(tileColor?.copy(0.22f) ?: MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentScale       = ContentScale.Crop,
                    error              = painterResource(R.drawable.ic_android_logo),
                    placeholder        = painterResource(R.drawable.ic_android_logo)
                )
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = tileColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }

    val cyber = LocalTheme.current.isCyberpunk()
    if (isGlass) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.cardRadius))
                .clickable { onClick() },
            shape    = RoundedCornerShape(GlassTokens.cardRadius)
        ) { tileContent() }
    } else if (cyber) {
        // CYBERPUNK: same notched HUD panel + varied rim + parallel line as the
        // home app cards, so category lists (See-All from a collection / source
        // tile) match the rest of the app instead of being plain M3 cards.
        val rim   = remember(repo.id) { cyberRimFor(repo.id.toInt()) }
        val shape = remember { CyberPanelShape(cut = 11.dp, notch = 4.dp) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .neonGlowGradient(shape, shadowOn = false, light = true, rim = rim)
                .cyberDoubleEdge(cut = 11.dp, inset = 4.dp, notch = 4.dp, rim = rim)
                .clickable { onClick() }
        ) { tileContent() }
    } else {
        Card(
            onClick  = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        glowColor != null -> Modifier.glowBorder(glowColor, cornerRadius = 12.dp)
                        else              -> Modifier
                    }
                ),
            shape    = MaterialTheme.shapes.large,
            colors   = CardDefaults.cardColors(
                containerColor = tileColor?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceContainer
            ),
            border   = if (glowColor == null) null else BorderStroke(1.dp, glowColor.copy(alpha = 0.55f))
        ) { tileContent() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// APP ROW — horizontal scroll of AppCards
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppRow(
    title        : String,
    apps         : List<GitHubRepo>,
    installed    : Set<Long> = emptySet(),
    refreshToken : Int       = 0,
    onAppClick   : (GitHubRepo) -> Unit
) {
    val t = LocalTheme.current
    // Drop GitHub repos verified to have no installable APK.
    val absentIds = LocalApkAbsentIds.current
    val visibleApps = if (absentIds.isEmpty()) apps else apps.filterNot { it.id in absentIds }
    if (visibleApps.isEmpty()) return

    // Seeded by (refreshToken, title): stable across recomposition — the row no
    // longer reshuffles when the home tab is disposed for a detail and rebuilt —
    // yet distinct per row and reshuffled on an actual refresh.
    val displayList  = remember(visibleApps, refreshToken) {
        visibleApps.shuffled(kotlin.random.Random(refreshToken.toLong() * 31 + title.hashCode()))
    }
    val useInfinite  = displayList.size >= 4
    // Keep virtual count small (≤2000) so LazyRow position maths stay fast
    val virtualCount = if (useInfinite) (displayList.size * 80).coerceAtMost(2_000) else displayList.size
    val startIndex   = remember(displayList.size, useInfinite) {
        if (useInfinite) {
            val mid = virtualCount / 2
            mid - (mid % displayList.size)
        } else 0
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    Column(modifier = Modifier.padding(top = 20.dp)) {
        if (t.isCyberpunk()) {
            // CYBERPUNK: uppercase title with a HUD rule trailing off to the right,
            // like the reference art's section headers. The rule takes the leftover
            // width so it always runs to the screen edge.
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // titleMedium, not titleLarge: Orbitron + uppercase + letterspacing
                // makes a long section name eat the whole row, leaving the rule no
                // width to draw into. Smaller type also matches the reference art's
                // header proportions.
                // No weight on the title: an unweighted child is measured first and
                // takes its intrinsic width, then the rule fills whatever is left.
                // Giving both weight(1f) split the row 50/50 and ellipsised every
                // heading ("CURATED COLL…").
                Text(
                    title.uppercase(),
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                CyberSectionRule(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
            }
        } else {
            Text(
                title,
                style    = MaterialTheme.typography.titleLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        LazyRow(
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                count = virtualCount,
                key   = { idx -> "${displayList[idx % displayList.size].id}_$idx" }
            ) { index ->
                val repo = displayList[index % displayList.size]
                AppCard(
                    repo        = repo,
                    isInstalled = installed.contains(repo.id),
                    onClick     = { onAppClick(repo) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PLATFORM GRID — for filtered views
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PlatformGrid(
    platform  : AppPlatform,
    apps      : List<GitHubRepo>,
    installed : Set<Long> = emptySet(),
    onAppClick: (GitHubRepo) -> Unit
) {
    val t = LocalTheme.current
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(platform.emoji, fontSize = 20.sp)
            Text(
                "${platform.label} Apps",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEachIndexed { index, repo ->
                AppListTile(
                    repo        = repo,
                    isInstalled = installed.contains(repo.id),
                    tileIndex   = index,
                    onClick     = { onAppClick(repo) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEARCH SCREEN
// ─────────────────────────────────────────────────────────────────────────────
// Random full-width/half-width layout for the search results grid. Hoist the
// result when the grid's scroll position must survive screen disposal —
// a re-rolled pattern would shift every item under a restored scroll offset.
@Composable
fun rememberBentoPattern(): List<Boolean> = remember {
    buildList {
        var slots = 0
        while (slots < 80) {
            when ((0..4).random()) {
                0, 1 -> { add(true);  slots += 1 }            // full-width (2/5 chance)
                else -> { add(false); add(false); slots += 2 } // two halves (3/5 chance)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query                : String,
    results              : List<GitHubRepo>,
    platform             : AppPlatform,
    selectedSubCategories: Set<String>,
    installed            : Set<Long>        = emptySet(),
    suggestions          : List<GitHubRepo> = emptyList(),
    // Preloaded multi-source apps (F-Droid/GitLab/Winget/…). Used so a source
    // chip tapped with no text query still surfaces that store's apps — the
    // live search only fires with text, so `results` is empty until then.
    sourcePool           : List<GitHubRepo> = emptyList(),
    recentSearches       : List<String>     = emptyList(),
    onRecentClick        : (String) -> Unit = {},
    onClearRecent        : () -> Unit       = {},
    isSearching          : Boolean          = false,
    // Hoist all three from the caller — this screen is disposed while an app
    // detail is open, so local state would lose scroll position/layout on back.
    gridState            : LazyGridState        = rememberLazyGridState(),
    bentoPattern         : List<Boolean>        = rememberBentoPattern(),
    scrollResetKey       : MutableState<String> = remember { mutableStateOf("") },
    onQueryChange        : (String) -> Unit,
    onPlatformChange     : (AppPlatform) -> Unit,
    onSubCategoryToggle  : (String) -> Unit,
    onAppClick           : (GitHubRepo) -> Unit,
    isFilterMenuOpen     : Boolean,
    activeSubMenuPlatform: AppPlatform?,
    onToggleFilterMenu   : (Boolean) -> Unit,
    onSetSubMenuPlatform : (AppPlatform?) -> Unit
) {
    val t       = LocalTheme.current
    val s       = LocalStrings.current
    val isGlass = LocalIsLiquidGlass.current
    var selectedSource  by remember { mutableStateOf<AppSource?>(null) }
    // localPlatform seeds from ViewModel state so it persists across navigation
    var localPlatform   by remember(platform) { mutableStateOf(platform) }
    var screenEntered   by remember { mutableStateOf(false) }
    val focusRequester  = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Animate in on first composition, then open keyboard.
    // Skipped on Android 8.x and older: legacy IMEs (EMUI 8 especially) re-request
    // input on every window resize while a field holds focus, so a force-focused
    // field re-opens the keyboard endlessly. Those users tap the field themselves.
    LaunchedEffect(Unit) {
        screenEntered = true
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O_MR1) {
            delay(320) // wait for slide-in animation
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val displayResults = run {
        // With no text query the live search never fires, so `results` is empty.
        // Fall back to the preloaded multi-source pool so a source chip (F-Droid,
        // GitLab, …) actually surfaces that store's apps instead of doing nothing.
        var list = if (query.isBlank() && selectedSource != null && results.isEmpty())
                       sourcePool
                   else results
        // Filter by source chip
        list = when {
            selectedSource == null -> list
            selectedSource == AppSource.GITHUB -> list.filter { it.source == null || it.source == AppSource.GITHUB }
            else -> list.filter { it.source == selectedSource }
        }
        // Platform filter — only re-filter when the query has text. When blank,
        // onSearch() already filtered by AppSource→platform, so double-filtering
        // would incorrectly drop repos whose name/description don't contain the keyword.
        // Match on heuristic labels OR the store's own platform (Winget→Windows,
        // Flathub→Linux, …); labels alone missed apps that don't say "windows"/"linux"
        // in their name/description, which made the filter look broken.
        if (localPlatform != AppPlatform.ALL && query.isNotBlank()) {
            list = list.filter { repo ->
                detectPlatformLabels(repo).contains(localPlatform) ||
                repo.source in platformSourceSet(localPlatform)
            }
        }
        list
    }

    Box(modifier = Modifier.fillMaxSize().background(
        if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background
    )) {
        ScreenBackground(ScreenBg.SEARCH)
    Column(modifier = Modifier.fillMaxSize()) {

        // ── TOP: search bar — slides down from above on enter ────────────────
        AnimatedVisibility(
            visible = screenEntered,
            enter   = slideInVertically(tween(380, easing = EaseOutCubic)) { -it } + fadeIn(tween(300))
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarSpace()
        ) {
            val isFiltered = localPlatform != AppPlatform.ALL

            val barModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp)

            val glassTextSec = if (t.isDark) GlassTokens.darkTextSecondary else GlassTokens.lightTextSecondary
            val accent        = MaterialTheme.colorScheme.primary
            val onAccent      = MaterialTheme.colorScheme.onPrimary

            // Shared row content — rendered the same either way
            @Composable
            fun BarContent() {
                Row(
                    modifier              = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Rounded.Search, null,
                        modifier = Modifier.size(20.dp),
                        tint     = if (isGlass) glassTextSec.copy(0.70f)
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f)
                    )
                    Spacer(Modifier.width(10.dp))
                    TextField(
                        value         = query,
                        onValueChange = onQueryChange,
                        modifier      = Modifier.weight(1f).focusRequester(focusRequester),
                        singleLine    = true,
                        placeholder   = {
                            Text(
                                s.searchHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isGlass) glassTextSec.copy(0.55f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f)
                            )
                        },
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor     = Color.Transparent,
                            unfocusedContainerColor   = Color.Transparent,
                            focusedIndicatorColor     = Color.Transparent,
                            unfocusedIndicatorColor   = Color.Transparent,
                            focusedTextColor          = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor        = MaterialTheme.colorScheme.onSurface,
                            cursorColor               = accent,
                            focusedPlaceholderColor   = Color.Transparent,
                            unfocusedPlaceholderColor = Color.Transparent
                        )
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.Clear, null,
                                tint     = if (isGlass) glassTextSec else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (isGlass) {
                        // Glass-on-glass filter button — matches the home screen style
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = if (isFiltered) 0.85f else 0.25f))
                                .border(1.5.dp, accent.copy(alpha = if (isFiltered) 1.0f else 0.60f), CircleShape)
                                .clickable { onToggleFilterMenu(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_filter_logo), null,
                                modifier = Modifier.size(18.dp),
                                tint     = if (isFiltered) onAccent else accent
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick  = { onToggleFilterMenu(true) },
                            modifier = Modifier.padding(end = 6.dp).size(38.dp),
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isFiltered) accent else MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Icon(painterResource(R.drawable.ic_filter_logo), null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (isGlass) {
                GlassSearchShell(modifier = barModifier) { BarContent() }
            } else {
                Surface(
                    modifier = barModifier.clip(CircleShape),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.surfaceContainerHigh
                ) { BarContent() }
            }
        }
        } // AnimatedVisibility — search bar

        // ── Source filter pills — filter results by source ───────────────────
        AnimatedVisibility(
            visible = screenEntered,
            enter   = fadeIn(tween(360))
        ) {
            HomeSourceChipsRow(
                selectedSource = selectedSource,
                onSourceSelect = { selectedSource = it }
            )
        }

        // ── BOTTOM: Bento grid results — rises from below on enter ────────────
        // Scroll to top only when the filters genuinely change; re-entering the
        // screen with the same filters (e.g. back from an app detail) keeps the
        // previous position because scrollResetKey outlives this composition.
        LaunchedEffect(query, localPlatform, selectedSource) {
            val key = "$query|$localPlatform|$selectedSource"
            if (scrollResetKey.value != key) {
                scrollResetKey.value = key
                gridState.scrollToItem(0)
            }
        }
        AnimatedVisibility(
            visible = screenEntered,
            enter   = slideInVertically(tween(440, easing = EaseOutCubic)) { it / 2 } + fadeIn(tween(360))
        ) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            state                 = gridState,
            contentPadding        = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            if (query.isBlank() && displayResults.isEmpty()) {
                // Recent searches — tappable chips above the trending suggestions
                if (recentSearches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, end = 6.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    s.recentSearches,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    s.clearAll,
                                    style    = MaterialTheme.typography.labelMedium,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onClearRecent() }
                                )
                            }
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentSearches.forEach { term ->
                                    AssistChip(
                                        onClick = { onRecentClick(term) },
                                        label   = {
                                            Text(term, style = MaterialTheme.typography.labelMedium)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.History, null,
                                                modifier = Modifier.size(15.dp),
                                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                // No text + no platform-filtered results → show trending suggestions
                if (suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            s.trendingApps,
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp, top = 8.dp)
                        )
                    }
                    items(
                        count = suggestions.size,
                        key   = { suggestions[it].id },
                        span  = { idx -> if (idx < bentoPattern.size && bentoPattern[idx]) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                    ) { idx ->
                        val repo = suggestions[idx]
                        AppCard(
                            repo        = repo,
                            isInstalled = installed.contains(repo.id),
                            modifier    = Modifier.fillMaxWidth(),
                            onClick     = { onAppClick(repo) }
                        )
                    }
                }
            } else if (query.isBlank()) {
                // No text but platform filter has results — show them directly
                items(
                    count = displayResults.size,
                    key   = { displayResults[it].id },
                    span  = { idx -> if (idx < bentoPattern.size && bentoPattern[idx]) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                ) { idx ->
                    val repo = displayResults[idx]
                    AppCard(
                        repo        = repo,
                        isInstalled = installed.contains(repo.id),
                        modifier    = Modifier.fillMaxWidth(),
                        onClick     = { onAppClick(repo) }
                    )
                }
            } else {
                if (isSearching && displayResults.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                Text(s.searching, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    items(
                        count = displayResults.size,
                        key   = { displayResults[it].id },
                        span  = { idx -> if (idx < bentoPattern.size && bentoPattern[idx]) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                    ) { idx ->
                        val repo = displayResults[idx]
                        AppCard(
                            repo        = repo,
                            isInstalled = installed.contains(repo.id),
                            modifier    = Modifier.fillMaxWidth(),
                            onClick     = { onAppClick(repo) }
                        )
                    }
                    if (displayResults.isNotEmpty() && isSearching) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                    Text(s.fetchingMore, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (displayResults.isEmpty() && !isSearching) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                    val filterText = if (localPlatform != AppPlatform.ALL) " — ${localPlatform.label}" else ""
                                    Text("${s.noResultsFound}$filterText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    Text(s.tryDifferent, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
        } // AnimatedVisibility — bento grid
    }

    // ── Platform filter bottom sheet ─────────────────────────────────────────
    if (isFilterMenuOpen) {
        ModalBottomSheet(
            onDismissRequest = { onToggleFilterMenu(false) },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = MaterialTheme.colorScheme.surface,
            shape            = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "Filter by Platform",
                    style         = MaterialTheme.typography.labelMedium,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier      = Modifier.padding(bottom = 10.dp)
                )
                val filterPlatforms = listOf(
                    AppPlatform.ALL,
                    AppPlatform.ANDROID,
                    AppPlatform.WINDOWS,
                    AppPlatform.LINUX,
                    AppPlatform.IOS,
                    AppPlatform.TV
                )
                filterPlatforms.forEach { p ->
                    val isSelected = localPlatform == p
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable {
                                localPlatform = p
                                onPlatformChange(p)
                                onToggleFilterMenu(false)
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (p == AppPlatform.ALL) {
                            Icon(
                                Icons.Rounded.Apps, null,
                                modifier = Modifier.size(22.dp),
                                tint     = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (p.iconRes != null) {
                            Icon(
                                painterResource(p.iconRes), null,
                                modifier = Modifier.size(22.dp),
                                tint     = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(p.emoji, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            s.platformLabel(p),
                            style      = MaterialTheme.typography.bodyLarge,
                            color      = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
    } // Box
} // SearchScreen


// ─────────────────────────────────────────────────────────────────────────────
// INSTALLED SCREEN
// ─────────────────────────────────────────────────────────────────────────────
private enum class UpdateCheckPhase { IDLE, CHECKING, FOUND, NOT_FOUND }

@Composable
fun InstalledScreen(
    installHistory        : List<InstallHistoryEntry>,
    installStates         : Map<Long, InstallState>,
    updates               : List<UpdateInfo>    = emptyList(),
    scanResults           : List<AppScanResult> = emptyList(),
    isScanning            : Boolean             = false,
    onAppClick            : (GitHubRepo) -> Unit,
    onCheckUpdates        : () -> Unit          = {},
    onUpdateAll           : () -> Unit          = {},
    onClearRemoved        : () -> Unit          = {},
    onScanAll             : () -> Unit          = {},
    onUpdateScanResult    : (AppScanResult) -> Unit = {},
    onOpenScanResult      : (AppScanResult) -> Unit = {},
    onTrackApp            : () -> Unit          = {},
    trackedApps           : List<TrackedApp>    = emptyList(),
    onRemoveTracked       : (String) -> Unit    = {},
    isCheckingUpdates     : Boolean             = false
) {
    val t = LocalTheme.current
    val s = LocalStrings.current

    // One card per app — latest install entry wins
    val entries = remember(installHistory) {
        installHistory
            .groupBy { it.repoId }
            .mapValues { (_, v) -> v.maxByOrNull { it.installedAt }!! }
            .values
            .sortedBy { it.repoName }
    }

    val updateCount  = updates.count { u -> entries.any { it.repoId == u.repoId } }
    val removedCount = entries.count { entry -> installStates[entry.repoId]?.isInstalled == false }

    var checkPhase by remember(updateCount) {
        mutableStateOf(if (updateCount > 0) UpdateCheckPhase.FOUND else UpdateCheckPhase.IDLE)
    }

    LaunchedEffect(isCheckingUpdates) {
        if (!isCheckingUpdates && checkPhase == UpdateCheckPhase.CHECKING) {
            checkPhase = if (updateCount > 0) UpdateCheckPhase.FOUND else UpdateCheckPhase.NOT_FOUND
        }
    }

    val isGlass = LocalIsLiquidGlass.current
    Box(modifier = Modifier.fillMaxSize().background(
        if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background
    )) {
        ScreenBackground(ScreenBg.INSTALLED)
    Column(modifier = Modifier.fillMaxSize()) {

        // Header — transparent so ScreenBackground blobs show through
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarSpace()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(s.navInstalled, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            entries.isEmpty() -> s.noAppsInstalled
                            updateCount > 0   -> "$updateCount update${if (updateCount != 1) "s" else ""} available"
                            else              -> "${entries.size} app${if (entries.size != 1) "s" else ""} installed"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (updateCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (removedCount > 0) {
                    TextButton(onClick = onClearRemoved) {
                        Text(
                            "${s.clearRemoved} ($removedCount)",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        LazyColumn(
            // bottom must clear BOTH the nav bar AND the floating "Enter Manually"
            // FAB (anchored 80dp up + ~40dp tall). At 110dp the FAB sat on top of
            // the last scan result; 172dp lets it scroll fully into view.
            contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 172.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Check for Updates  |  Search All Sources — side-by-side row ─────────
            item {
                val btnLabel = when (checkPhase) {
                    UpdateCheckPhase.IDLE      -> s.checkForUpdates
                    UpdateCheckPhase.CHECKING  -> s.checkingForUpdates
                    UpdateCheckPhase.FOUND     -> "${s.updateAll} ($updateCount)"
                    UpdateCheckPhase.NOT_FOUND -> s.noUpdatesFound
                }
                val isEnabled = checkPhase == UpdateCheckPhase.IDLE || checkPhase == UpdateCheckPhase.FOUND
                val updateOnClick: () -> Unit = {
                    when (checkPhase) {
                        UpdateCheckPhase.IDLE  -> { checkPhase = UpdateCheckPhase.CHECKING; onCheckUpdates() }
                        UpdateCheckPhase.FOUND -> onUpdateAll()
                        else                   -> {}
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isGlass) {
                        GlassButton(
                            onClick        = updateOnClick,
                            enabled        = isEnabled,
                            // Tight padding: half-width button must fit "Check for updates"
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                            modifier       = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (checkPhase == UpdateCheckPhase.CHECKING) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White.copy(0.45f), strokeWidth = 2.dp)
                            } else {
                                Icon(when (checkPhase) {
                                    UpdateCheckPhase.FOUND     -> Icons.Rounded.Update
                                    UpdateCheckPhase.NOT_FOUND -> Icons.Rounded.CheckCircle
                                    else                       -> Icons.Rounded.Refresh
                                }, null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(btnLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        GlassButton(
                            onClick        = onScanAll,
                            enabled        = !isScanning,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                            modifier       = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White.copy(0.45f), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.ManageSearch, null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(s.scanAllSources, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick        = updateOnClick,
                            enabled        = isEnabled,
                            modifier       = Modifier.weight(1f).height(50.dp),
                            shape          = MaterialTheme.shapes.large,
                            // Tight padding: half-width button must fit "Check for updates"
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor         = MaterialTheme.colorScheme.primary,
                                contentColor           = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                disabledContentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        ) {
                            if (checkPhase == UpdateCheckPhase.CHECKING) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), strokeWidth = 2.dp)
                            } else {
                                Icon(when (checkPhase) {
                                    UpdateCheckPhase.FOUND     -> Icons.Rounded.Update
                                    UpdateCheckPhase.NOT_FOUND -> Icons.Rounded.CheckCircle
                                    else                       -> Icons.Rounded.Refresh
                                }, null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(btnLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick        = onScanAll,
                            enabled        = !isScanning,
                            modifier       = Modifier.weight(1f).height(50.dp),
                            shape          = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.ManageSearch, null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(s.scanAllSources, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }

            // ── Tracked apps section ──────────────────────────────────────────────
            if (trackedApps.isNotEmpty()) {
                item(key = "tracked_header") {
                    Text(
                        s.trackedAppsSection,
                        modifier   = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(trackedApps, key = { "tracked:${it.id}" }) { tracked ->
                    TrackedAppCard(
                        tracked  = tracked,
                        onRemove = { onRemoveTracked(tracked.id) }
                    )
                }
            }

            if (updateCount > 0) {
                item {
                    GlassCard(
                        modifier       = Modifier.fillMaxWidth(),
                        shape          = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NewReleases, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                            Text(
                                "$updateCount update${if (updateCount != 1) "s" else ""} available",
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Empty state or app list
            if (entries.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.InstallMobile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                            Text(s.nothingInstalled, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                s.nothingInstalledDesc,
                                style     = MaterialTheme.typography.bodyMedium,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(items = entries, key = { it.repoId }) { entry ->
                    val iState  = installStates[entry.repoId]
                    val repo    = iState?.repo ?: GitHubRepo(
                        id        = entry.repoId,
                        name      = entry.repoName,
                        full_name = "${entry.ownerLogin}/${entry.repoName}",
                        owner     = RepoOwner(login = entry.ownerLogin)
                    )
                    val update  = updates.firstOrNull { it.repoId == entry.repoId }
                    val isStillInstalled = iState?.isInstalled == true

                    InstalledAppCard(
                        repo        = repo,
                        entry       = entry,
                        isInstalled = isStillInstalled,
                        update      = update,
                        onClick     = { onAppClick(repo) },
                        onUpdate    = { onAppClick(repo) }
                    )
                }
            }

            // ── Open-source apps detected on device ──────────────────────────────
            if (isScanning || scanResults.isNotEmpty()) {
                item(key = "scan_header") {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            s.updatesFromAllSources,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "${scanResults.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(scanResults, key = { "${it.source.name}:${it.packageName}" }) { result ->
                    AppScanResultCard(
                        result   = result,
                        onClick  = { onOpenScanResult(result) },
                        onUpdate = { onUpdateScanResult(result) }
                    )
                }
            }

        }
    }
        // ── Floating "Enter Manually" FAB (bottom-right, above nav bar) ──────────
        Box(
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 80.dp)
        ) {
            if (isGlass) {
                GlassButton(onClick = onTrackApp) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.enterManually, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick  = onTrackApp,
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.enterManually, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InstalledAppCard(
    repo        : GitHubRepo,
    entry       : InstallHistoryEntry,
    isInstalled : Boolean,
    update      : UpdateInfo?,
    onClick     : () -> Unit,
    onUpdate    : () -> Unit = {}
) {
    val s = LocalStrings.current

    GlassCard(
        onClick        = onClick,
        modifier       = Modifier.fillMaxWidth(),
        containerColor = if (update != null) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation      = if (update != null) 4.dp else 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model              = repo.iconUrlOrNull,
                    contentDescription = null,
                    modifier           = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    error              = painterResource(R.drawable.ic_android_logo),
                    placeholder        = painterResource(R.drawable.ic_android_logo)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        repo.displayName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (update != null) MaterialTheme.colorScheme.onPrimaryContainer
                                     else MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        repo.owner.login,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = if (update != null) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.75f)
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when {
                    update != null -> Button(
                        onClick        = onUpdate,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape          = MaterialTheme.shapes.large,
                        colors         = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Update, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.updateLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    isInstalled -> FilledTonalButton(
                        onClick        = {},
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors         = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.installedStatus, style = MaterialTheme.typography.labelSmall)
                    }
                    else -> OutlinedButton(
                        onClick        = {},
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(s.removedStatus, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "v${entry.tagName.trimStart('v', 'V')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (update != null) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (update != null) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null,
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(12.dp))
                    Text(
                        update.latestTag,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.weight(1f))
                val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                Text(
                    fmt.format(java.util.Date(entry.installedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (update != null) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppScanResultCard(result: AppScanResult, onClick: () -> Unit, onUpdate: () -> Unit) {
    val s = LocalStrings.current
    GlassCard(
        modifier       = Modifier.fillMaxWidth().clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model              = result.iconUrl.ifEmpty { null },
                contentDescription = null,
                modifier           = Modifier.size(40.dp).clip(CircleShape),
                error              = painterResource(R.drawable.ic_android_logo),
                placeholder        = painterResource(R.drawable.ic_android_logo)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.appName,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    if (result.hasUpdate)
                        "${result.currentVersion} → ${result.newVersion} · ${result.source.name}"
                    else
                        "${result.currentVersion} · ${result.source.name}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (result.hasUpdate && result.link !is ScanLink.Empty) {
                Button(
                    onClick        = onUpdate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.updateLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            } else if (!result.hasUpdate) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// APP DETAIL SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    repo                  : GitHubRepo,
    installState          : InstallState,
    isFavourite           : Boolean,
    translatedDesc        : String?,
    translatedReadme      : String?  = null,
    isTranslating         : Boolean,
    translatedReleaseBody : String?  = null,
    isTranslatingRelease  : Boolean  = false,
    state                 : UiState,
    screenshots           : List<String>  = emptyList(),
    readme                : String?       = null,
    onInstall             : () -> Unit,
    onDownloadOnly        : () -> Unit,
    onUninstall           : () -> Unit,
    onCancelDownload      : () -> Unit,
    onTranslate           : () -> Unit,
    onTranslateRelease    : () -> Unit = {},
    onToggleFavourite     : () -> Unit,
    onIgnoreVersion       : () -> Unit    = {},
    onCompare             : () -> Unit    = {},
    /** Hides or restores this app across every source, in both shells. */
    onToggleHidden        : (Boolean) -> Unit = {},
    onSelectRelease       : (Release) -> Unit = {},
    onSelectAsset         : (ReleaseAsset) -> Unit = {},
    onBack                : () -> Unit
) {
    val t       = LocalTheme.current
    val context = LocalContext.current
    val s = LocalStrings.current
    var screenshotFullscreen by remember { mutableStateOf<Int?>(null) }
    var readmeExpanded       by remember { mutableStateOf(false) }
    var showReleaseHistory   by remember { mutableStateOf(false) }
    val isGlass = LocalIsLiquidGlass.current

    Box(modifier = Modifier.fillMaxSize().background(
        if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background
    )) {
        ScreenBackground(ScreenBg.HOME)
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title          = {
                Text(
                    repo.displayName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.cyberGlitchName(repo.id.toInt())   // CYBERPUNK (detail — single instance)
                )
            },
            navigationIcon = {
                // CYBERPUNK: chamfered, rimmed frames around the bar icons — the
                // reference art brackets every control rather than floating bare
                // glyphs on the background.
                val cyberBar = LocalTheme.current.isCyberpunk()
                IconButton(
                    onClick  = onBack,
                    modifier = if (cyberBar) Modifier
                        .padding(start = 6.dp)
                        .clip(CyberCutSmall)
                        .neonGlow(LocalTheme.current.accent, CyberCutSmall, glow = 10.dp, shadowOn = false)
                    else Modifier
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                val cyberBar = LocalTheme.current.isCyberpunk()
                val actionMod: @Composable (Color) -> Modifier = { c ->
                    if (cyberBar) Modifier
                        .padding(horizontal = 3.dp)
                        .clip(CyberCutSmall)
                        .neonGlow(c, CyberCutSmall, glow = 10.dp, shadowOn = false)
                    else Modifier
                }
                IconButton(modifier = actionMod(LocalTheme.current.accent), onClick = {
                    val shareText = "${repo.displayName} by @${repo.owner.login}\n\n${repo.description ?: ""}\n\n${repo.html_url}"
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick  = onToggleFavourite,
                    modifier = actionMod(LocalTheme.current.accentAlt)
                ) {
                    Icon(
                        imageVector        = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint               = if (isFavourite) RedDanger
                                             else if (cyberBar) LocalTheme.current.accentAlt
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors   = TopAppBarDefaults.topAppBarColors(
                containerColor             = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.statusBarSpace()
        )

        LazyColumn(
            contentPadding      = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // App icon + info header card (overlaps gradient)
            item {
                GlassCard(
                    modifier       = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    shape          = MaterialTheme.shapes.extraLarge,
                    elevation      = 4.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Square rounded icon box
                        Box(
                            modifier         = Modifier
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model              = ImageRequest.Builder(LocalContext.current)
                                    .data(repo.iconUrlOrNull)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier           = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.large),
                                contentScale       = ContentScale.Crop
                            )
                        }
                        // Name + stats
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "@${repo.owner.login}",
                                style    = MaterialTheme.typography.labelLarge,
                                color    = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                repo.displayName,
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis
                            )
                            if (installState.isInstalled) {
                                Spacer(Modifier.height(4.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label   = {
                                        Text(s.installedBadge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    },
                                    colors  = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = GreenOk.copy(0.15f),
                                        labelColor     = GreenOk
                                    )
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // Stars | Forks | Language
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Star, null, tint = StarGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    formatStars(repo.stargazers_count),
                                    style      = MaterialTheme.typography.labelLarge,
                                    color      = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                VerticalDivider(modifier = Modifier.height(14.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Icon(Icons.Rounded.ForkRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(formatStars(repo.forks_count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!repo.language.isNullOrBlank()) {
                                    VerticalDivider(modifier = Modifier.height(14.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    Text(repo.language, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val updated = relativeUpdated(repo.updated_at)
                                if (updated.isNotBlank()) {
                                    VerticalDivider(modifier = Modifier.height(14.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    Icon(Icons.Rounded.Update, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(updated, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // About section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    SectionHeader(s.about)
                    GlassCard(
                        shape          = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // ── Short description (one-liner from source) ──
                            val displayedDesc = translatedDesc ?: (repo.description ?: s.noDescAvailable)
                            Text(
                                displayedDesc,
                                style      = MaterialTheme.typography.bodyMedium,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )

                            // ── Full description from README ──────────────
                            if (!readme.isNullOrBlank()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color    = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                                )
                                Text(
                                    s.descriptionHeader,
                                    style      = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                val readmeText = translatedReadme ?: readme
                                val preview    = 600
                                val isLong     = readmeText.length > preview
                                val shownText  = if (readmeExpanded || !isLong) readmeText
                                                 else readmeText.take(preview).trimEnd() + "…"
                                Text(
                                    shownText,
                                    style      = MaterialTheme.typography.bodySmall,
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                                if (isLong) {
                                    TextButton(
                                        onClick        = { readmeExpanded = !readmeExpanded },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (readmeExpanded) s.showLess else s.readMore,
                                            style      = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // ── Translate button ──────────────────────────
                            if (repo.description != null) {
                                Spacer(Modifier.height(6.dp))
                                GlassButton(
                                    onClick  = { if (!isTranslating) onTranslate() },
                                    enabled  = !isTranslating
                                ) {
                                    if (isTranslating) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Translate, null, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (translatedDesc != null) s.translatedRedo
                                        else "${s.translateTo} ${state.settings.language}",
                                        style      = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // ── Screenshots ───────────────────────────────
                            if (screenshots.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    s.screenshots,
                                    style      = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(screenshots) { index, url ->
                                        AsyncImage(
                                            model              = url,
                                            contentDescription = null,
                                            modifier           = Modifier
                                                .width(140.dp)
                                                .height(240.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .clickable { screenshotFullscreen = index },
                                            contentScale       = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Release section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    when {
                        installState.isLoadingRelease -> {
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color       = MaterialTheme.colorScheme.primary
                                )
                                Text(s.checkingRelease, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        installState.error != null -> {
                            Text(installState.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        installState.release != null -> {
                            SectionHeader(s.release)
                            GlassCard(
                                shape          = MaterialTheme.shapes.extraLarge,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier              = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        SuggestionChip(
                                            onClick = {},
                                            label   = {
                                                Text(
                                                    installState.release.tag_name.ifBlank { "Latest" },
                                                    style      = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                labelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            border = null
                                        )
                                        if (installState.apkAsset != null) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    formatBytes(installState.apkAsset.size),
                                                    style      = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color      = MaterialTheme.colorScheme.primary
                                                )
                                                if (installState.release.prerelease) {
                                                    Text(
                                                        s.preReleaseBadge,
                                                        style      = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color      = MaterialTheme.colorScheme.tertiary
                                                    )
                                                } else {
                                                    Text(s.stableRelease, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }

                                    if (!installState.release.body.isNullOrBlank()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            s.whatsNew,
                                            style      = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        val displayedBody = translatedReleaseBody ?: installState.release.body.orEmpty()
                                        Text(
                                            displayedBody.take(500) +
                                                    if (displayedBody.length > 500) "…" else "",
                                            style      = MaterialTheme.typography.bodySmall,
                                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 19.sp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        GlassButton(
                                            onClick  = { if (!isTranslatingRelease) onTranslateRelease() },
                                            enabled  = !isTranslatingRelease
                                        ) {
                                            if (isTranslatingRelease) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Rounded.Translate, null, modifier = Modifier.size(15.dp))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (translatedReleaseBody != null) "Retranslate notes"
                                                else "Translate notes",
                                                style      = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    TextButton(
                                        onClick = { onIgnoreVersion() },
                                        shape   = MaterialTheme.shapes.small
                                    ) {
                                        Icon(
                                            Icons.Rounded.VisibilityOff, null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(s.skipVersion, style = MaterialTheme.typography.labelSmall)
                                    }

                                    if (installState.apkAsset == null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(s.noApkFound, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Release / Asset selectors  — two dropdowns side by side.
            // List EVERY asset (apk, exe, dmg, zip, tar, …) — APKs first so the
            // installable variant is the default, then all other downloadables.
            val releaseAssets = installState.release?.assets ?: emptyList()
            val allApkAssets  = releaseAssets.filter { it.isApk() }
            val allOtherAssets = releaseAssets.filter { !it.isApk() }
            val allAssets     = allApkAssets + allOtherAssets
            if (installState.releases.size > 1 || allAssets.isNotEmpty()) {
                item {
                    var showReleaseMenu by remember { mutableStateOf(false) }
                    var showAssetMenu   by remember { mutableStateOf(false) }
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Release picker
                        if (installState.releases.size > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                GlassCard(
                                    onClick  = { showReleaseMenu = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Tag, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            installState.release?.tag_name?.ifBlank { "Release" } ?: "Release",
                                            style      = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface,
                                            maxLines   = 1,
                                            overflow   = TextOverflow.Ellipsis,
                                            modifier   = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                DropdownMenu(
                                    expanded         = showReleaseMenu,
                                    onDismissRequest = { showReleaseMenu = false },
                                    containerColor   = if (isGlass) (if (t.isDark) GlassTokens.darkPopup else GlassTokens.lightPopup) else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    installState.releases.forEach { rel ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(rel.tag_name.ifBlank { "Release" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                                        if (rel.prerelease) {
                                                            Text(
                                                                "β",
                                                                style      = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color      = MaterialTheme.colorScheme.tertiary
                                                            )
                                                        }
                                                    }
                                                    if (!rel.published_at.isNullOrBlank()) {
                                                        Text(rel.published_at.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                if (installState.release?.tag_name == rel.tag_name) {
                                                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            onClick = { onSelectRelease(rel); showReleaseMenu = false }
                                        )
                                    }
                                }
                            }
                        }

                        // Unified asset picker — lists EVERY asset (apk/exe/dmg/zip/…)
                        if (allAssets.isNotEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                GlassCard(
                                    onClick  = { if (allAssets.size > 1) showAssetMenu = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                installState.apkAsset?.name?.let { if (it.length > 20) it.take(17) + "…" else it } ?: "Select file",
                                                style      = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color      = MaterialTheme.colorScheme.onSurface,
                                                maxLines   = 1,
                                                overflow   = TextOverflow.Ellipsis
                                            )
                                            installState.apkAsset?.let { a ->
                                                val abi = detectAssetAbi(a.name)
                                                when {
                                                    abi != null   -> Text(abi, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    a.size > 0     -> Text(formatBytes(a.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        if (allAssets.size > 1)
                                            Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (allAssets.size > 1) {
                                    DropdownMenu(
                                        expanded         = showAssetMenu,
                                        onDismissRequest = { showAssetMenu = false },
                                        containerColor   = if (isGlass) (if (t.isDark) GlassTokens.darkPopup else GlassTokens.lightPopup) else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        allAssets.forEach { asset ->
                                            val abi = detectAssetAbi(asset.name)
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(asset.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            if (asset.isApk()) Text("APK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                            if (abi != null) Text(abi, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                            if (asset.size > 0) Text(formatBytes(asset.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    if (installState.apkAsset?.name == asset.name)
                                                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                },
                                                onClick = { onSelectAsset(asset); showAssetMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Action buttons (includes download progress when active)
            item {
                Column(
                    modifier            = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val canInstall = installState.apkAsset != null &&
                            installState.apkAsset!!.isApk() &&
                            installState.downloadProgress == null &&
                            !installState.isVerifying &&
                            !installState.isInstalled
                    // Any selected non-APK asset (exe/dmg/zip/source archive/…) is downloadable.
                    val canDownloadNonApk = installState.apkAsset != null &&
                            !installState.apkAsset!!.isApk() &&
                            installState.downloadProgress == null &&
                            !installState.isVerifying

                    if (installState.isVerifying) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                LocalIntegrityStrings.current.checking,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (installState.downloadProgress != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(s.downloading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${(installState.downloadProgress * 100).toInt()}%",
                                    style      = MaterialTheme.typography.bodyMedium,
                                    color      = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress   = { installState.downloadProgress },
                                    modifier   = Modifier
                                        .weight(1f)
                                        .clip(MaterialTheme.shapes.small),
                                    color      = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(RedDanger.copy(0.12f))
                                        .clickable { onCancelDownload() },
                                    Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Close, "Cancel",
                                        tint     = RedDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else if (canInstall) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassOutlinedButton(
                                onClick  = onDownloadOnly,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.download, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            }
                            GlassButton(
                                onClick  = onInstall,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Icon(Icons.Rounded.InstallMobile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.install, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (canDownloadNonApk) {
                        GlassButton(
                            onClick  = onDownloadOnly,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.download, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    val viewUrl = when {
                        repo.html_url.isNotBlank() -> repo.html_url
                        repo.source == AppSource.FDROID   -> "https://f-droid.org/packages/${repo.full_name}/"
                        repo.source == AppSource.FLATHUB  -> "https://flathub.org/apps/${repo.full_name}"
                        repo.source == AppSource.WINGET   -> "https://winget.run/pkg/${repo.full_name}"
                        else -> ""
                    }
                    if (viewUrl.isNotBlank()) {
                        val sourceLabel = when (repo.source) {
                            AppSource.GITLAB   -> "GitLab"
                            AppSource.CODEBERG -> "Codeberg"
                            AppSource.FDROID   -> "F-Droid"
                            AppSource.IZZY     -> "IzzyOnDroid"
                            AppSource.FLATHUB  -> "Flathub"
                            AppSource.WINGET   -> "Winget"
                            else               -> "GitHub"
                        }
                        GlassOutlinedButton(
                            onClick  = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewUrl))) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${s.viewOn} $sourceLabel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    GlassOutlinedButton(
                        onClick  = onCompare,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.CompareArrows, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.compareWithApp, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    }

                    // Hiding is keyed on the package, so an entry that never resolved
                    // to one has nothing to hide by — offering the button there would
                    // give a control that silently does nothing.
                    if (repo.packageId.isNotBlank()) {
                        val isHidden = repo.packageId in state.hiddenPackages
                        GlassOutlinedButton(
                            onClick  = { onToggleHidden(!isHidden) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(
                                if (isHidden) Icons.Rounded.Visibility
                                else Icons.Rounded.VisibilityOff,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isHidden) "Unhide from all sources"
                                else "Hide from all sources",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (installState.isInstalled) {
                        GlassButton(
                            onClick        = onUninstall,
                            modifier       = Modifier.fillMaxWidth().height(52.dp),
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.uninstall, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        if (installState.apkAsset != null) {
                            GlassOutlinedButton(
                                onClick  = onInstall,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.reinstallUpdate, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // About the Author
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        s.aboutTheAuthor,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        modifier   = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    GlassCard(shape = MaterialTheme.shapes.extraLarge) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model              = repo.iconUrlOrNull,
                                contentDescription = null,
                                modifier           = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape),
                                contentScale       = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    repo.owner.login,
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                                val devLabel = when (repo.source) {
                                    AppSource.GITLAB   -> "GitLab Developer"
                                    AppSource.CODEBERG -> "Codeberg Developer"
                                    AppSource.FDROID   -> "F-Droid Developer"
                                    AppSource.IZZY     -> "IzzyOnDroid Developer"
                                    AppSource.FLATHUB  -> "Flathub Developer"
                                    AppSource.WINGET   -> "Winget Developer"
                                    // Aptoide entries are store listings, not repos —
                                    // the name here is the signing publisher, and
                                    // calling them "developers" of a repo they have
                                    // none of read as a mislabel.
                                    AppSource.APTOIDE  -> "Publisher"
                                    AppSource.MODULE   -> "Module Author"
                                    else               -> "GitHub Developer"
                                }
                                Text(devLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    // Only repo hosts have a /<login> profile page.
                                    // For anything else the app's own listing URL is
                                    // the closest thing to "more about this author";
                                    // guessing a github.com path for an Aptoide
                                    // publisher lands on a 404.
                                    val target = when (repo.source) {
                                        AppSource.GITHUB ->
                                            "https://github.com/${repo.owner.login}"
                                        else -> repo.html_url.takeIf { it.isNotBlank() }
                                            ?: repo.sourceCodeUrl.takeIf { it.isNotBlank() }
                                    }
                                    target?.let {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Vyxel Trust Score
            installState.trustScore?.let { trust ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SectionHeader(s.trustScoreTitle)
                        GlassCard(shape = MaterialTheme.shapes.extraLarge) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                TrustScoreBar(trust)
                            }
                        }
                    }
                }
            }

            // Integrity — only after a download has actually been verified
            installState.verification?.let { v ->
                item { IntegrityCard(v) }
            }

            // Smart Install recommendation
            installState.smartInstall?.let { smart ->
                item {
                    val smartColor = if (smart.isOptimal) GreenOk else StarGold
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        val smartRow: @Composable () -> Unit = {
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                FilledTonalIconButton(
                                    onClick  = {},
                                    colors   = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = smartColor.copy(0.18f),
                                        contentColor   = smartColor
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.Verified, null, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        s.bestPackageHint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        smart.reason,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = smartColor
                                    )
                                }
                            }
                        }
                        if (isGlass) {
                            GlassColoredSurface(
                                modifier = Modifier.fillMaxWidth(),
                                gradient = Brush.linearGradient(listOf(smartColor.copy(0.18f), smartColor.copy(0.09f))),
                                shape    = MaterialTheme.shapes.extraLarge
                            ) { smartRow() }
                        } else {
                            OutlinedCard(
                                shape  = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.outlinedCardColors(containerColor = smartColor.copy(0.08f)),
                                border = BorderStroke(1.dp, smartColor.copy(0.28f))
                            ) { smartRow() }
                        }
                    }
                }
            }

            // Developer Mode
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        s.developerMode,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        modifier   = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    GlassCard(shape = MaterialTheme.shapes.extraLarge) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val baseUrl = when {
                                repo.html_url.isNotBlank() -> repo.html_url
                                repo.source == AppSource.FDROID  -> "https://f-droid.org/packages/${repo.full_name}/"
                                repo.source == AppSource.FLATHUB -> "https://flathub.org/apps/${repo.full_name}"
                                else -> ""
                            }
                            val devLinks = when (repo.source) {
                                    AppSource.GITHUB, AppSource.IZZY -> listOf(
                                        Triple(Icons.Rounded.Code,      s.viewSourceCode, baseUrl),
                                        Triple(Icons.Rounded.BugReport, s.openIssues,     if (baseUrl.isNotBlank()) "$baseUrl/issues" else ""),
                                        Triple(Icons.Rounded.History,   s.releaseHistory, if (baseUrl.isNotBlank()) "$baseUrl/releases" else "")
                                    )
                                    AppSource.GITLAB -> listOf(
                                        Triple(Icons.Rounded.Code,      s.viewSourceCode, baseUrl),
                                        Triple(Icons.Rounded.BugReport, s.openIssues,     if (baseUrl.isNotBlank()) "$baseUrl/-/issues" else ""),
                                        Triple(Icons.Rounded.History,   s.releaseHistory, if (baseUrl.isNotBlank()) "$baseUrl/-/releases" else "")
                                    )
                                    AppSource.CODEBERG -> listOf(
                                        Triple(Icons.Rounded.Code,      s.viewSourceCode, baseUrl),
                                        Triple(Icons.Rounded.BugReport, s.openIssues,     if (baseUrl.isNotBlank()) "$baseUrl/issues" else ""),
                                        Triple(Icons.Rounded.History,   s.releaseHistory, if (baseUrl.isNotBlank()) "$baseUrl/releases" else "")
                                    )
                                    AppSource.FDROID -> listOf(
                                        Triple(Icons.Rounded.Code,    "${s.viewOn} F-Droid", baseUrl),
                                        Triple(Icons.Rounded.History, s.releaseHistory,      baseUrl)
                                    )
                                    AppSource.FLATHUB -> listOf(
                                        Triple(Icons.Rounded.Code,    "${s.viewOn} Flathub", baseUrl),
                                        Triple(Icons.Rounded.History, s.releaseHistory,      "$baseUrl#versions")
                                    )
                                    else -> listOf(
                                        Triple(Icons.Rounded.Code, s.viewSourceCode, baseUrl)
                                    )
                                }
                            devLinks.forEachIndexed { i, (icon, label, url) ->
                                val isHistory = label == s.releaseHistory
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isHistory && installState.releases.isNotEmpty())
                                                Modifier.clickable { showReleaseHistory = true }
                                            else if (url.isNotBlank()) Modifier.clickable {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                )
                                            } else Modifier
                                        )
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    FilledTonalIconButton(
                                        onClick  = {},
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(icon, null, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        label,
                                        style    = MaterialTheme.typography.bodyMedium,
                                        color    = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (isHistory && installState.releases.isNotEmpty())
                                            Icons.Rounded.ChevronRight else Icons.Rounded.OpenInBrowser,
                                        null,
                                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (i < devLinks.lastIndex) HorizontalDivider(
                                    color    = MaterialTheme.colorScheme.outlineVariant.copy(0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    screenshotFullscreen?.let { idx ->
        FullScreenImageViewer(
            urls         = screenshots,
            initialIndex = idx,
            onDismiss    = { screenshotFullscreen = null }
        )
    }
    if (showReleaseHistory) {
        ReleaseHistorySheet(
            releases        = installState.releases,
            currentTag      = installState.release?.tag_name,
            onSelectRelease = { onSelectRelease(it); showReleaseHistory = false },
            onDismiss       = { showReleaseHistory = false }
        )
    }
    } // Column
    } // Box

// ─────────────────────────────────────────────────────────────────────────────
// ReleaseHistorySheet — in-app list of all releases (tag, date, notes, assets)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReleaseHistorySheet(
    releases        : List<Release>,
    currentTag      : String?,
    onSelectRelease : (Release) -> Unit,
    onDismiss       : () -> Unit
) {
    val s       = LocalStrings.current
    val isGlass = LocalIsLiquidGlass.current
    val t       = LocalTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape    = RoundedCornerShape(24.dp),
            color    = if (isGlass) (if (t.isDark) GlassTokens.darkPopup else GlassTokens.lightPopup)
                       else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(s.releaseHistory, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(items = releases, key = { it.tag_name + it.published_at }) { rel ->
                        val selected = rel.tag_name == currentTag
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRelease(rel) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    rel.tag_name.ifBlank { rel.name ?: "Release" },
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (rel.prerelease) {
                                    Text("PRE-RELEASE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Spacer(Modifier.weight(1f))
                                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            if (!rel.published_at.isNullOrBlank()) {
                                Text(rel.published_at.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val notes = rel.body?.trim()
                            if (!notes.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    notes.take(400) + if (notes.length > 400) "…" else "",
                                    style      = MaterialTheme.typography.bodySmall,
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                            val n = rel.assets.size
                            if (n > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("$n ${if (n == 1) "file" else "files"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.35f), modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEE ALL SCREEN
// ─────────────────────────────────────────────────────────────────────────────
private fun Color.vivid(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
        ), hsv
    )
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
}

/**
 * Same hue, but light enough to read as TEXT on a panel of that colour.
 *
 * [vivid] drives saturation to 1.0, which leaves darker hues (the purple media
 * tile, the deep green Magisk tile) barely distinguishable from their own
 * background — the title vanished. Dropping saturation while pinning value gives
 * the pale neon ink the reference art uses: light cyan, light pink, light green.
 */
private fun Color.neonInk(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
        ), hsv
    )
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 0.38f, 1f)))
}

private fun Modifier.glowBorder(color: Color, cornerRadius: Dp, glowRadius: Dp = 7.dp): Modifier =
    drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint()
            @Suppress("DEPRECATION")
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.dp.toPx()
                this.color = color.copy(alpha = 0.55f).toArgb()
                maskFilter = android.graphics.BlurMaskFilter(
                    glowRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            val r = cornerRadius.toPx()
            canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }

private val tileColors = listOf(
    Color(0xFF0891B2), Color(0xFF6D28D9), Color(0xFF065F46), Color(0xFFB45309),
    Color(0xFF1D4ED8), Color(0xFF9333EA), Color(0xFFDB2777), Color(0xFFDC2626),
    Color(0xFF0D9488), Color(0xFF7C3AED), Color(0xFF059669), Color(0xFF0EA5E9),
    Color(0xFFEA580C), Color(0xFF16A34A), Color(0xFF7C3AED), Color(0xFFC026D3)
)

@Composable
fun SeeAllScreen(
    title         : String,
    apps          : List<GitHubRepo>,
    installed     : Set<Long>    = emptySet(),
    isLoading     : Boolean,
    useTileColors : Boolean      = false,
    // Hoist from the caller — this screen is disposed while an app detail is
    // open, so a local state would lose the scroll position on back.
    listState     : LazyListState = rememberLazyListState(),
    onLoadMore    : () -> Unit,
    onAppClick    : (GitHubRepo) -> Unit,
    onBack        : () -> Unit
) {
    val isGlass = LocalIsLiquidGlass.current
    Box(modifier = Modifier.fillMaxSize().background(
        if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background
    )) {
        ScreenBackground(ScreenBg.SEARCH)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .statusBarSpace()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        when {
            apps.isEmpty() && isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            apps.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.displaySmall)
                        Text("Could not load apps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onLoadMore) { Text("Retry") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items = apps) { index, repo ->
                        AppListTile(
                            repo           = repo,
                            isInstalled    = installed.contains(repo.id),
                            tileIndex      = index,
                            useTileColors  = useTileColors,
                            onClick        = { onAppClick(repo) }
                        )
                    }
                    item {
                        if (isLoading) {
                            Box(
                                modifier        = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            LaunchedEffect(Unit) { onLoadMore() }
                            Spacer(Modifier.height(110.dp))
                        }
                    }
                }
            }
        }
    }
    } // Box
}

// ─────────────────────────────────────────────────────────────────────────────
// TRUST SCORE BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TrustScoreBar(trust: TrustScore) {
    val color = trust.safeColor
    val s = LocalStrings.current
    val trustLabel = when {
        trust.score >= 85 -> s.trustHighlyTrusted
        trust.score >= 65 -> s.trustTrusted
        trust.score >= 45 -> s.trustModerate
        trust.score >= 25 -> s.trustLow
        else              -> s.trustUnverified
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        s.trustScoreTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        trustLabel,
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(color.copy(0.12f))
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${trust.score}",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LinearProgressIndicator(
                progress   = { trust.score / 100f },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small),
                color      = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrustChip(
                    label = if (trust.daysSinceUpdate < 90) s.trustActive else s.trustInactive,
                    ok    = trust.daysSinceUpdate < 90
                )
                TrustChip(
                    label = if (trust.stars >= 100) "${formatStars(trust.stars)} ${s.trustStars}" else s.trustLowStars,
                    ok    = trust.stars >= 100
                )
                TrustChip(
                    label = if (trust.releaseCount > 0) "${trust.releaseCount} ${s.trustReleases}" else s.trustNoReleases,
                    ok    = trust.releaseCount > 0
                )
            }
        }
}

/**
 * Section heading. In Cyberpunk it becomes the reference art's HUD header —
 * "»" lead-in, uppercase Orbitron, and a rule with tick marks running to the
 * right edge. Every other theme gets the plain titleLarge it always had.
 */
@Composable
fun SectionHeader(
    title    : String,
    modifier : Modifier = Modifier
) {
    if (LocalTheme.current.isCyberpunk()) {
        Row(
            modifier          = modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("» ", style = MaterialTheme.typography.titleMedium, color = LocalTheme.current.accentAlt)
            Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            CyberSectionRule(modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
    } else {
        Text(
            title,
            style    = MaterialTheme.typography.titleLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.padding(bottom = 8.dp, start = 4.dp)
        )
    }
}

/**
 * Shown once a downloaded APK has been through the verifier — the concrete
 * answer to "is this really the app it says it is", next to the Trust Score's
 * softer repo-reputation signal.
 */
@Composable
fun IntegrityCard(v: ApkVerifier.Result) {
    val s  = LocalIntegrityStrings.current
    val ok = v.isSafeToInstall
    val accent = when {
        !ok                                              -> MaterialTheme.colorScheme.error
        v.verdict == ApkVerifier.Verdict.SIGNATURE_MATCH -> GreenOk
        else                                             -> StarGold
    }
    val headline = when (v.verdict) {
        ApkVerifier.Verdict.SIGNATURE_MATCH -> s.verified
        ApkVerifier.Verdict.NEW_INSTALL     -> s.firstInstall
        else                                -> s.failed
    }
    val subtitle = when (v.verdict) {
        ApkVerifier.Verdict.SIGNATURE_MATCH -> s.sameSigner
        ApkVerifier.Verdict.NEW_INSTALL     -> s.noPrior
        else                                -> v.message()
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        SectionHeader(s.title)
        GlassCard(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier         = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (ok) Icons.Rounded.Verified else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint     = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            headline,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else accent
                            )
                        }
                    }
                }

                if (v.packageName.isNotEmpty())  IntegrityRow(s.packageLabel,  v.packageName)
                if (v.signerSha256.isNotEmpty()) IntegrityRow(s.signerLabel,   v.shortSigner + "…")
                if (v.fileSha256.isNotEmpty())   IntegrityRow(s.fileHashLabel, v.shortFileHash + "…")
            }
        }
    }
}

@Composable
private fun IntegrityRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style      = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun TrustChip(label: String, ok: Boolean) {
    SuggestionChip(
        onClick = {},
        label   = {
            Text(
                "${if (ok) "✓" else "✗"} $label",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (ok) GreenOk.copy(0.12f) else RedDanger.copy(0.12f),
            labelColor     = if (ok) GreenOk else RedDanger
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled      = true,
            borderColor  = if (ok) GreenOk.copy(0.3f) else RedDanger.copy(0.3f),
            borderWidth  = 0.5.dp
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SOURCES ROW  — horizontal browsable source tiles
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SourcesRow(
    gitlabCount       : Int,
    codebergCount     : Int,
    fdroidCount       : Int,
    flathubCount      : Int,
    wingetCount       : Int,
    izzyCount         : Int,
    customRepos       : List<CustomRepo>       = emptyList(),
    onSourceClick     : (AppSource) -> Unit,
    onCustomRepoClick : (CustomRepo) -> Unit   = {}
) {
    val t       = LocalTheme.current
    val s       = LocalStrings.current
    val context = LocalContext.current
    val sources = listOf(
        Triple(AppSource.GITHUB,   R.drawable.github,      -1),
        Triple(AppSource.FDROID,   R.drawable.fdroid,      fdroidCount),
        Triple(AppSource.GITLAB,   R.drawable.gitlab,      gitlabCount),
        Triple(AppSource.CODEBERG, R.drawable.codeberg,    codebergCount),
        Triple(AppSource.IZZY,     R.drawable.ic_izzy_logo, izzyCount),
        Triple(AppSource.FLATHUB,  R.drawable.flathub,     flathubCount),
        Triple(AppSource.WINGET,   R.drawable.winget,      wingetCount)
    )

    Column(modifier = Modifier.padding(top = 20.dp)) {
        if (t.isCyberpunk()) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.browseBySource.uppercase(),
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                CyberSectionRule(modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
        } else {
            Text(
                s.browseBySource,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── User-added custom repos appear FIRST ──
            items(items = customRepos, key = { it.id }) { repo ->
                // Stable hue derived from the repo's UUID — consistent across recompositions
                val hue      = (repo.id.hashCode().and(0x7FFFFFFF) % 360).toFloat()
                val base     = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.82f)))
                val darkEnd  = Color(base.red * 0.45f, base.green * 0.45f, base.blue * 0.45f)
                val lightEnd = Color(
                    (base.red   + 0.30f).coerceAtMost(1f),
                    (base.green + 0.30f).coerceAtMost(1f),
                    (base.blue  + 0.30f).coerceAtMost(1f)
                )
                val cyberRepo = LocalTheme.current.isCyberpunk()
                GlassColoredSurface(
                    modifier    = Modifier.width(130.dp).height(96.dp),
                    gradient    = if (cyberRepo) remember(base) { cyberTileBrush(base.vivid()) }
                                  else Brush.horizontalGradient(listOf(lightEnd, darkEnd)),
                    shape       = if (cyberRepo) CyberCutSmall
                                  else RoundedCornerShape(16.dp),
                    onClick     = { onCustomRepoClick(repo) },
                    cyberAccent = if (cyberRepo) base.vivid() else null,
                    cyberCorners = cyberRepo,
                    cyberCut    = 7.dp    // CyberCutSmall
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 10.dp, end = 8.dp),
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model              = ImageRequest.Builder(context)
                                    .data(repo.iconUri.ifEmpty { R.drawable.android_icon })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier           = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale       = ContentScale.Fit,
                                fallback           = painterResource(R.drawable.android_icon),
                                error              = painterResource(R.drawable.android_icon)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(repo.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.myRepo, fontSize = 11.sp, color = Color.White.copy(0.75f), maxLines = 1)
                        }
                    }
                    Box(
                        modifier         = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // ── Built-in sources follow ──
            items(items = sources) { (source, emoji, count) ->
                val base = Color(source.colorHex)
                val darkEnd  = Color(base.red * 0.45f, base.green * 0.45f, base.blue * 0.45f)
                val lightEnd = Color(
                    (base.red   + 0.30f).coerceAtMost(1f),
                    (base.green + 0.30f).coerceAtMost(1f),
                    (base.blue  + 0.30f).coerceAtMost(1f)
                )
                val countText = when {
                    count < 0  -> s.openSourceLabel
                    count == 0 -> s.browseLabel
                    else       -> "$count apps"
                }
                val cyberSrc = LocalTheme.current.isCyberpunk()
                GlassColoredSurface(
                    modifier    = Modifier.width(130.dp).height(96.dp),
                    // Same dark-core / lit-rim fill as the collection tiles.
                    gradient    = if (cyberSrc) remember(base) { cyberTileBrush(base.vivid()) }
                                  else Brush.horizontalGradient(listOf(lightEnd, darkEnd)),
                    shape       = if (cyberSrc) CyberCutSmall
                                  else RoundedCornerShape(16.dp),
                    onClick     = { onSourceClick(source) },
                    cyberAccent = if (cyberSrc) base.vivid() else null,
                    cyberCorners = cyberSrc,
                    cyberCut    = 7.dp    // CyberCutSmall
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 10.dp, end = 8.dp),
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter            = painterResource(emoji),
                                contentDescription = null,
                                modifier           = Modifier.size(32.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(source.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(countText,    fontSize = 11.sp, color = Color.White.copy(0.75f), maxLines = 1)
                        }
                    }
                    Box(
                        modifier         = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SOURCE BADGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SourceBadge(source: AppSource?, modifier: Modifier = Modifier) {
    val s = source ?: return
    if (s == AppSource.GITHUB) return
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(4.dp),
        color    = Color(s.colorHex)
    ) {
        Text(
            s.label,
            fontSize   = 8.sp,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FULL-SCREEN IMAGE VIEWER
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullScreenImageViewer(
    urls         : List<String>,
    initialIndex : Int,
    onDismiss    : () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties       = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { urls.size })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model              = urls[page],
                    contentDescription = null,
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentScale       = ContentScale.Fit
                )
            }

            // Page indicator
            Row(
                modifier              = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(urls.size) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(4.dp)
                            .width(if (i == pagerState.currentPage) 20.dp else 6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (i == pagerState.currentPage) Color.White else Color.White.copy(
                                    0.4f
                                )
                            )
                    )
                }
            }

            // Close button
            Surface(
                modifier  = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarSpace()
                    .padding(12.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                shape = CircleShape,
                color = Color.White.copy(0.15f)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    "Close",
                    tint     = Color.White,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLLECTIONS ROW  — horizontal LazyRow of vertical cards
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CollectionsRow(onCollectionClick: (AppCollection) -> Unit) {
    val t = LocalTheme.current
    val s = LocalStrings.current

    // Per-collection: gradient colors + emoji tilt (°)
    val configs = remember {
        listOf(
            listOf(Color(0xFF0891B2), Color(0xFF083344)) to 12f,
            listOf(Color(0xFF6D28D9), Color(0xFF1E1035)) to -18f,
            listOf(Color(0xFF065F46), Color(0xFF032B20)) to  22f,
            listOf(Color(0xFFB45309), Color(0xFF1C0A00)) to  -8f,
            listOf(Color(0xFF1D4ED8), Color(0xFF1E3A5F)) to -15f,
            listOf(Color(0xFF9333EA), Color(0xFF2E1065)) to  10f,
            listOf(Color(0xFFDB2777), Color(0xFF500724)) to -22f,
            listOf(Color(0xFFDC2626), Color(0xFF450A0A)) to  16f,
            listOf(Color(0xFF0D9488), Color(0xFF042F2E)) to -10f,
            listOf(Color(0xFF7C3AED), Color(0xFF1E1B4B)) to   8f,
            listOf(Color(0xFF059669), Color(0xFF022C22)) to -14f,
            listOf(Color(0xFF0EA5E9), Color(0xFF0C4A6E)) to  18f,
            listOf(Color(0xFFEA580C), Color(0xFF431407)) to  -6f,
        )
    }

    Column(modifier = Modifier.padding(top = 20.dp)) {
        if (t.isCyberpunk()) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "»" lead-in mark, as in the reference art's section headers.
                Text(
                    "» ",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.accentAlt
                )
                Text(
                    s.curatedCollections.uppercase(),
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                CyberSectionRule(modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
        } else {
            Text(
                s.curatedCollections,
                style    = MaterialTheme.typography.titleLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(count = COLLECTIONS.size) { index ->
                val col = COLLECTIONS[index]
                val (gradient, tilt) = configs.getOrElse(index) {
                    listOf(Color(0xFF6D28D9), Color(0xFF1E1035)) to 0f
                }
                val (localTitle, localSubtitle) = s.localizeCollection(col.title)
                CollectionCard(
                    col           = col,
                    localTitle    = localTitle,
                    localSubtitle = localSubtitle,
                    gradient      = gradient,
                    emojiRotation = tilt,
                    decorStyle    = index % 4,
                    onClick       = { onCollectionClick(col) }
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(
    col           : AppCollection,
    localTitle    : String,
    localSubtitle : String,
    gradient      : List<Color>,
    emojiRotation : Float,
    decorStyle    : Int,
    onClick       : () -> Unit
) {
    // CYBERPUNK: chamfered HUD panel with a neon rim in the card's own color
    val cyber     = LocalTheme.current.isCyberpunk()
    // Irregular outline (chamfers + mid-edge notches), matching the reference.
    val cardShape = if (cyber) CyberCutLarge
                    else RoundedCornerShape(22.dp)
    // The tile's identity colour, brightened to neon — shared by the rim, the
    // city tint, the title and the arrow so the whole tile reads as one hue.
    val tileNeon  = remember(gradient) { gradient.firstOrNull()?.vivid() ?: Color.White }
    // Legible-on-panel variant, used for the title only.
    val tileInk   = remember(gradient) { gradient.firstOrNull()?.neonInk() ?: Color.White }
    GlassColoredSurface(
        modifier    = Modifier.width(120.dp).height(185.dp),
        // Dark core with the hue glowing at the rim, per the reference — the flat
        // linear wash made the tile the brightest object on a black screen.
        gradient    = if (cyber) remember(tileNeon) { cyberTileBrush(tileNeon) }
                      else Brush.linearGradient(gradient),
        shape       = cardShape,
        onClick     = onClick,
        cyberAccent = if (cyber) gradient.firstOrNull()?.vivid() else null,
        cyberCorners = cyber
    ) {
        if (cyber) {
            // CYBERPUNK: a greyscale neon-city plate behind the tile, multiplied by
            // the tile's own hue so it reads as *that* colour rather than importing
            // the photo's palette — the green Magisk tile gets a green city, the
            // pink media tile a pink one. Modulate is what does that: grey × hue.
            // Kept very faint so it's texture, not subject matter.
            rememberPackPainter("cyber_city_tile")?.let { tilePlate ->
            Image(
                painter            = tilePlate,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                alpha              = 0.30f,
                colorFilter        = ColorFilter.tint(
                    gradient.firstOrNull()?.vivid() ?: Color.White,
                    BlendMode.Modulate
                ),
                modifier = Modifier.matchParentSize()
            )
            }
        }
        // Decorative background shape, varies per card
        when (decorStyle) {
            0 -> Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-24).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.08f))
            )
            1 -> Box(
                modifier = Modifier
                    .size(70.dp, 100.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-28).dp)
                    .graphicsLayer { rotationZ = 30f }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.07f))
            )
            2 -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-8).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.09f))
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 32.dp, y = 30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.06f))
                )
            }
            // 3 → clean gradient, no decoration
        }

        // Content: emoji + text stacked from the top
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (col.iconRes != null) {
                // Offscreen layer + BlendMode.Screen makes black PNG pixels transparent
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(col.iconRes),
                        contentDescription = null,
                        modifier           = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                rotationZ = emojiRotation
                                blendMode = BlendMode.Screen
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        col.emoji,
                        fontSize = 26.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = emojiRotation }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // CYBERPUNK: the title takes the tile's own neon colour, as in the
            // reference art — white on a tinted panel reads as a generic card,
            // whereas the lit hue ties the type to the tile's identity.
            Text(
                if (cyber) localTitle.uppercase() else localTitle,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = if (cyber) tileInk else Color.White,
                maxLines   = 2,
                lineHeight = 16.sp,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                localSubtitle,
                fontSize   = 10.sp,
                color      = Color.White.copy(0.65f),
                maxLines   = 2,
                lineHeight = 13.sp,
                overflow   = TextOverflow.Ellipsis
            )
        }
        // Arrow circle anchored to bottom-right of card
        Box(
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(32.dp)
                .clip(CircleShape)
                // CYBERPUNK: an outlined ring in the tile's colour rather than a
                // white disc — matches the reference and stops the arrow reading
                // as the brightest thing on the tile.
                .background(if (cyber) tileNeon.copy(0.10f) else Color.White.copy(0.22f))
                .then(if (cyber) Modifier.border(1.2.dp, tileNeon.copy(0.75f), CircleShape) else Modifier)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint               = if (cyber) tileNeon else Color.White,
                modifier           = Modifier.size(14.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREENSHOTS PAGER
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScreenshotsPager(urls: List<String>) {
    if (urls.isEmpty()) return
    val t          = LocalTheme.current
    val pagerState = rememberPagerState(pageCount = { urls.size })
    var fullscreenIndex by remember { mutableStateOf<Int?>(null) }

    Column {
        HorizontalPager(
            state       = pagerState,
            modifier    = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp),
            pageSpacing = 8.dp
        ) { page ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { fullscreenIndex = page },
                shape    = MaterialTheme.shapes.large,
                color    = MaterialTheme.colorScheme.surface
            ) {
                AsyncImage(
                    model              = urls[page],
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit
                )
            }
        }
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(urls.size) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(4.dp)
                        .width(if (i == pagerState.currentPage) 16.dp else 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }

    fullscreenIndex?.let { idx ->
        FullScreenImageViewer(
            urls         = urls,
            initialIndex = idx,
            onDismiss    = { fullscreenIndex = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FLOATING DOCK  —  pill-shaped, icon+text row, pill-fill highlight
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FloatingNavBar(
    selectedTab  : VAppTab,
    onTabSelect  : (VAppTab) -> Unit,
    updateCount  : Int = 0
) {
    if (LocalIsLiquidGlass.current) {
        GlassNavBar(selectedTab = selectedTab, onTabSelect = onTabSelect, updateCount = updateCount)
        return
    }
    // CYBERPUNK: dedicated neon HUD dock
    if (LocalTheme.current.isCyberpunk()) {
        CyberNavBar(selectedTab = selectedTab, onTabSelect = onTabSelect, updateCount = updateCount)
        return
    }
    val s     = LocalStrings.current
    val items = listOf(
        Triple(VAppTab.HOME,      Icons.Rounded.Home,     s.navHome),
        Triple(VAppTab.INSTALLED, Icons.Rounded.Download, s.navInstalled),
        Triple(VAppTab.PROFILE,   Icons.Rounded.Person,   s.navProfile),
        Triple(VAppTab.SETTINGS,  Icons.Rounded.Settings, s.navSettings)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            shape           = RoundedCornerShape(32.dp),
            color           = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
            tonalElevation  = 6.dp
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { (tab, icon, label) ->
                    val selected  = selectedTab == tab
                    val hasUpdate = tab == VAppTab.INSTALLED && updateCount > 0
                    val interactionSource = remember { MutableInteractionSource() }
                    val tabWeight by animateFloatAsState(
                        targetValue = if (selected) 1.8f else 1f,
                        animationSpec = tween(220),
                        label = "tabWeight"
                    )

                    Box(
                        modifier         = Modifier
                            .weight(tabWeight)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication        = null
                            ) { onTabSelect(tab) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BadgedBox(badge = {
                                if (hasUpdate) Badge { Text("$updateCount", style = MaterialTheme.typography.labelSmall) }
                            }) {
                                Icon(
                                    imageVector        = icon,
                                    contentDescription = label,
                                    modifier           = Modifier.size(22.dp),
                                    tint               = if (selected) MaterialTheme.colorScheme.onPrimary
                                                         else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(
                                visible = selected,
                                enter   = expandHorizontally(tween(220)) + fadeIn(tween(160)),
                                exit    = shrinkHorizontally(tween(180)) + fadeOut(tween(120))
                            ) {
                                Text(
                                    label,
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onPrimary,
                                    maxLines   = 1,
                                    softWrap   = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CYBERPUNK: neon HUD dock  — sharp frame, bracket corners, animated scan sweep
//  + flickering rail, glowing selected tab. Reads LocalCyberClock in the draw
//  phase so only this small node redraws (no recomposition). Removal: delete this
//  function + the "// CYBERPUNK" hook in FloatingNavBar.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CyberNavBar(
    selectedTab : VAppTab,
    onTabSelect : (VAppTab) -> Unit,
    updateCount : Int = 0
) {
    val s       = LocalStrings.current
    val cyan    = CyberpunkTheme.accent        // #00F0FF
    val magenta = CyberpunkTheme.accentAlt     // #FF2D78
    val muted   = CyberpunkTheme.textSecondary
    val clock   = LocalCyberClock.current
    val fxOn    = LocalCyberpunkFx.current

    val items = listOf(
        Triple(VAppTab.HOME,      Icons.Rounded.Home,     s.navHome),
        Triple(VAppTab.INSTALLED, Icons.Rounded.Download, s.navInstalled),
        Triple(VAppTab.PROFILE,   Icons.Rounded.Person,   s.navProfile),
        Triple(VAppTab.SETTINGS,  Icons.Rounded.Settings, s.navSettings)
    )
        // Notched HUD panel instead of the near-square CyberCutMedium — the bar
        // read as a plain rounded rectangle before.
    val barShape = remember { CyberPanelShape(cut = 15.dp, notch = 5.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0A0F1E), Color(0xFF03060E)))
                )
                // animated overlay: scan sweep + flicker rail
                .drawBehind {
                    val w = size.width; val h = size.height
                    val t = clock?.value ?: 0f
                    if (fxOn) {
                        // moving vertical scan highlight sweeping left→right
                        val sweepX = ((t * 0.16f) % 1f) * (w + 200f) - 100f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, cyan.copy(alpha = 0.12f), Color.Transparent),
                                startX = sweepX - 70f, endX = sweepX + 70f
                            )
                        )
                        // flickering top rail (CRT)
                        val fl = 0.5f + 0.5f * kotlin.math.sin(t * 38f)
                        drawRect(cyan.copy(alpha = 0.20f + 0.28f * fl), size = Size(w, 1.6.dp.toPx()))
                    }
                }
                .neonGlowGradient(barShape, glow = 20.dp, shadowOn = true)
                // Parallel inner HUD line, replacing the old hand-drawn brackets.
                .cyberDoubleEdge(cut = 15.dp, inset = 5.dp, notch = 5.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEachIndexed { index, (tab, icon, label) ->
                    val selected  = selectedTab == tab
                    val hasUpdate = tab == VAppTab.INSTALLED && updateCount > 0
                    val inter     = remember { MutableInteractionSource() }
                    // alternate neon per dock position (cyan / magenta), not enum ordinal
                    val glowC    = if (index % 2 == 0) cyan else magenta
                    val tabShape = CyberCutSmall

                    val selMod = if (selected) {
                        Modifier
                            .clip(tabShape)
                            .background(
                                Brush.verticalGradient(listOf(glowC.copy(alpha = 0.30f), glowC.copy(alpha = 0.08f)))
                            )
                            .neonGlow(glowC, tabShape, glow = 14.dp, shadowOn = false)
                    } else {
                        Modifier.clip(tabShape)
                    }

                    // Icon stacked over an ALWAYS-visible label, equal widths — the
                    // reference art labels every destination. The old dock revealed
                    // the label only for the selected tab and grew that tab to 2×
                    // width, which made the row shift under the finger on every tap.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(selMod)
                            .clickable(interactionSource = inter, indication = null) { onTabSelect(tab) }
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            BadgedBox(badge = {
                                if (hasUpdate) Badge(containerColor = magenta, contentColor = Color.White) {
                                    Text("$updateCount", style = MaterialTheme.typography.labelSmall)
                                }
                            }) {
                                Icon(
                                    imageVector        = icon,
                                    contentDescription = label,
                                    modifier           = Modifier.size(20.dp),
                                    tint               = if (selected) glowC else muted
                                )
                            }
                            Text(
                                label.uppercase(),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = if (selected) glowC else muted,
                                maxLines   = 1,
                                softWrap   = false
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────
fun detectAssetAbi(name: String): String? {
    val l = name.lowercase()
    return when {
        "arm64-v8a" in l || "aarch64" in l -> "arm64-v8a"
        "armeabi-v7a" in l || "armv7" in l || "arm32" in l -> "armeabi-v7a"
        "x86_64" in l || "_x64-" in l -> "x86_64"
        "x86" in l -> "x86"
        "universal" in l -> "universal"
        else -> null
    }
}

// Which stores are inherently a given platform. Mirrors the AppSource→platform
// mapping in AppViewModel.onSearch() so the search-screen filter and the initial
// blank-query fetch agree on what counts as e.g. "Windows".
fun platformSourceSet(p: AppPlatform): Set<AppSource> = when (p) {
    AppPlatform.ANDROID -> setOf(AppSource.GITHUB, AppSource.IZZY, AppSource.FDROID)
    AppPlatform.WINDOWS -> setOf(AppSource.WINGET)
    AppPlatform.LINUX   -> setOf(AppSource.FLATHUB, AppSource.CODEBERG, AppSource.GITLAB)
    AppPlatform.TV      -> setOf(AppSource.GITHUB, AppSource.IZZY)
    else                -> emptySet()
}

fun detectPlatformLabels(repo: GitHubRepo): List<AppPlatform> {
    val text = "${repo.displayName} ${repo.description ?: ""}".lowercase()
    val lang = repo.language?.lowercase() ?: ""
    val platforms = mutableListOf<AppPlatform>()

    // Android — source is the most reliable signal
    val isAndroid = repo.source == AppSource.FDROID ||
        repo.source == AppSource.IZZY ||
        "android" in text || "apk" in text ||
        lang == "kotlin" || lang == "java" || lang == "dart"
    if (isAndroid) platforms.add(AppPlatform.ANDROID)

    // Windows — Winget packages are Windows-only
    val isWindows = repo.source == AppSource.WINGET ||
        "windows" in text || "win32" in text || "winforms" in text || "uwp" in text ||
        lang == "c#" || lang == "autohotkey" || lang == "powershell" || lang == "visual basic .net"
    if (isWindows) platforms.add(AppPlatform.WINDOWS)

    // Linux — Flathub packages are desktop-Linux-first
    val isLinux = repo.source == AppSource.FLATHUB ||
        "linux" in text || "debian" in text || "ubuntu" in text ||
        "flatpak" in text || "snap" in text || "gtk" in text || "kde" in text
    if (isLinux) platforms.add(AppPlatform.LINUX)

    // iOS / macOS
    val isIOS = "ios" in text || "iphone" in text || "ipad" in text ||
        "macos" in text || "mac os" in text || "swiftui" in text ||
        lang == "swift" || lang == "objective-c"
    if (isIOS) platforms.add(AppPlatform.IOS)

    // TV
    val isTV = "android tv" in text || " tv " in text || "firetv" in text ||
        "fire tv" in text || "leanback" in text || "television" in text
    if (isTV) platforms.add(AppPlatform.TV)

    return platforms.distinct()
}


fun formatStars(count: Int): String =
    if (count >= 1000) "${count / 1000}.${(count % 1000) / 100}k" else count.toString()

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000     -> "${bytes / 1_000} KB"
    else               -> "$bytes B"
}

// "3d ago" / "5mo ago" from an ISO-8601 timestamp; blank when unparseable so the
// caller can simply skip rendering.
fun relativeUpdated(iso: String): String {
    if (iso.isBlank()) return ""
    val ms = runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(iso.take(19))?.time ?: return ""
    }.getOrNull() ?: return ""
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
    return when {
        days < 0    -> ""
        days == 0   -> "today"
        days == 1   -> "1d ago"
        days < 30   -> "${days}d ago"
        days < 365  -> "${days / 30}mo ago"
        else        -> "${days / 365}y ago"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackedAppCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TrackedAppCard(tracked: TrackedApp, onRemove: () -> Unit) {
    val s       = LocalStrings.current
    val isGlass = LocalIsLiquidGlass.current
    GlassCard(
        modifier       = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.TrackChanges,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tracked.appName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    tracked.repoFullName,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isGlass) {
                GlassButton(onClick = onRemove, modifier = Modifier.height(34.dp)) {
                    Text(s.removeTracking, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(onClick = onRemove) {
                    Text(s.removeTracking, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackAppScreen  — list of all device-installed apps to pick one to track
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TrackAppScreen(
    onAppSelected : (packageName: String, appName: String) -> Unit,
    onDismiss     : () -> Unit
) {
    val s       = LocalStrings.current
    val context = LocalContext.current
    val isGlass = LocalIsLiquidGlass.current

    // Load all installed apps (non-system) sorted by name
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var searchQuery   by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        installedApps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
                .filter { it.applicationInfo != null && (it.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { pkg ->
                    val label = try { pkg.applicationInfo!!.loadLabel(pm).toString() } catch (_: Exception) { pkg.packageName }
                    Pair(pkg.packageName, label)
                }
                .sortedBy { it.second.lowercase() }
        }
    }

    val filtered = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.second.contains(searchQuery, ignoreCase = true) ||
            it.first.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background)
    ) {
        ScreenBackground(ScreenBg.SEARCH)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarSpace()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back)
                }
                Text(
                    s.selectInstalledApp,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                placeholder   = { Text(s.searchHint) },
                shape         = CircleShape,
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) }
            )
            LazyColumn(
                contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (installedApps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                } else {
                    items(filtered, key = { it.first }) { (pkg, name) ->
                        val t            = LocalTheme.current
                        val textColor    = if (isGlass) t.textPrimary   else MaterialTheme.colorScheme.onSurface
                        val subTextColor = if (isGlass) t.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant

                        var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(pkg) {
                            icon = withContext(Dispatchers.IO) {
                                runCatching {
                                    val drawable = context.packageManager.getApplicationIcon(pkg)
                                    val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                    android.graphics.Canvas(bmp).also { canvas ->
                                        drawable.setBounds(0, 0, 96, 96)
                                        drawable.draw(canvas)
                                    }
                                    bmp.asImageBitmap()
                                }.getOrNull()
                            }
                        }

                        GlassCard(
                            onClick        = { onAppSelected(pkg, name) },
                            modifier       = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val loadedIcon = icon
                                if (loadedIcon != null) {
                                    Image(
                                        bitmap             = loadedIcon,
                                        contentDescription = null,
                                        modifier           = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pkg, style = MaterialTheme.typography.labelSmall, color = subTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = subTextColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackRepoSearchScreen  — repo search results for tracking a specific app
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TrackRepoSearchScreen(
    appName        : String,
    packageName    : String,
    results        : List<GitHubRepo>,
    isSearching    : Boolean,
    onQueryChange  : (String) -> Unit,
    onRepoSelected : (GitHubRepo) -> Unit,
    onEnterManually: () -> Unit,
    onDismiss      : () -> Unit
) {
    val s       = LocalStrings.current
    val isGlass = LocalIsLiquidGlass.current
    var query   by remember(appName) { mutableStateOf(appName) }
    val focusReq = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background)
    ) {
        ScreenBackground(ScreenBg.SEARCH)
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarSpace()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(s.trackAppTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        packageName,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            }

            // Search field
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it; onQueryChange(it) },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .focusRequester(focusReq),
                placeholder   = { Text(s.searchHint) },
                shape         = CircleShape,
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                trailingIcon  = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = ""; onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, null, modifier = Modifier.size(18.dp))
                    }
                }) else null
            )

            // "Enter Manually" at the very top above results
            if (isGlass) {
                GlassButton(
                    onClick  = onEnterManually,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.enterManually, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick  = onEnterManually,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    shape    = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.enterManually, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Results list
            LazyColumn(
                contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier            = Modifier.weight(1f)
            ) {
                if (results.isEmpty() && isSearching) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Text(s.searching, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (results.isEmpty() && !isSearching) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                Text(s.noResultsFound, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(results, key = { it.id }) { repo ->
                        GlassCard(
                            onClick        = { onRepoSelected(repo) },
                            modifier       = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppImage(
                                    url      = repo.iconUrlOrNull.orEmpty(),
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(repo.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (repo.source != null && repo.source != AppSource.GITHUB) {
                                            Text(
                                                repo.source.name,
                                                style   = MaterialTheme.typography.labelSmall,
                                                color   = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        repo.full_name,
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!repo.description.isNullOrEmpty()) {
                                        Text(
                                            repo.description,
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NavBarTextColorPicker  — HSV color wheel for selecting nav bar text/icon color
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NavBarTextColorPicker(
    initialColorHex : String,
    onColorChange   : (Color) -> Unit,
    onSave          : (String) -> Unit,
    onDismiss       : () -> Unit
) {
    val isGlass = LocalIsLiquidGlass.current
    val s       = LocalStrings.current

    val initColor = remember(initialColorHex) {
        if (initialColorHex.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(
                if (initialColorHex.startsWith("#")) initialColorHex else "#$initialColorHex"
            )) } catch (_: Exception) { Color.White }
        } else Color.White
    }
    val initHsv = remember(initColor) {
        FloatArray(3).also { hsv ->
            android.graphics.Color.colorToHSV(
                android.graphics.Color.argb(255,
                    (initColor.red * 255).toInt(),
                    (initColor.green * 255).toInt(),
                    (initColor.blue * 255).toInt()), hsv)
        }
    }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var bri by remember { mutableStateOf(initHsv[2].coerceAtLeast(0.15f)) }

    val currentColor by remember(hue, sat, bri) {
        derivedStateOf { Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri))) }
    }

    LaunchedEffect(currentColor) { onColorChange(currentColor) }

    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier       = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    s.navTextColorLabel,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )

                val wheelDp   = 256.dp
                val wheelPx   = with(density) { wheelDp.toPx() }
                val outerR    = wheelPx / 2f
                val ringThick = outerR * 0.22f
                val innerR    = outerR - ringThick
                val svHalf    = innerR * 0.68f

                fun handleTouch(x: Float, y: Float) {
                    val dx   = x - outerR
                    val dy   = y - outerR
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist >= innerR - 4f) {
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0f) angle += 360f
                        hue = angle
                    } else if (abs(dx) <= svHalf && abs(dy) <= svHalf) {
                        sat = ((dx + svHalf) / (svHalf * 2f)).coerceIn(0f, 1f)
                        bri = (1f - (dy + svHalf) / (svHalf * 2f)).coerceIn(0.05f, 1f)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(wheelDp)
                        .pointerInput(Unit) {
                            detectTapGestures { pos -> handleTouch(pos.x, pos.y) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ -> handleTouch(change.position.x, change.position.y) }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        // Hue ring via native SweepGradient
                        val hueArgb = IntArray(361) { i ->
                            android.graphics.Color.HSVToColor(floatArrayOf(i.toFloat() % 360f, 1f, 1f))
                        }
                        val huePos = FloatArray(361) { i -> i / 360f }
                        drawIntoCanvas { canvas ->
                            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            p.shader = android.graphics.SweepGradient(cx, cy, hueArgb, huePos)
                            p.style  = android.graphics.Paint.Style.STROKE
                            p.strokeWidth = ringThick
                            canvas.nativeCanvas.drawCircle(cx, cy, outerR - ringThick / 2f, p)
                        }

                        // SV square — sat (left→right) × value (top→bottom)
                        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        val bL = cx - svHalf; val bT = cy - svHalf
                        val bS = svHalf * 2f
                        drawRect(
                            brush   = Brush.horizontalGradient(listOf(Color.White, hueColor), bL, bL + bS),
                            topLeft = Offset(bL, bT), size = Size(bS, bS)
                        )
                        drawRect(
                            brush   = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), bT, bT + bS),
                            topLeft = Offset(bL, bT), size = Size(bS, bS)
                        )

                        // Hue ring indicator
                        val hRad = Math.toRadians(hue.toDouble())
                        val rR   = outerR - ringThick / 2f
                        val iX   = cx + (rR * cos(hRad)).toFloat()
                        val iY   = cy + (rR * sin(hRad)).toFloat()
                        drawCircle(Color.White, 9.dp.toPx(), Offset(iX, iY))
                        drawCircle(hueColor,   7.dp.toPx(), Offset(iX, iY))

                        // SV indicator
                        val svX = bL + sat * bS
                        val svY = bT + (1f - bri) * bS
                        drawCircle(Color.White,        8.dp.toPx(), Offset(svX, svY))
                        drawCircle(currentColor,       6.dp.toPx(), Offset(svX, svY))
                    }
                }

                // Color preview swatch
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.4f), MaterialTheme.shapes.medium)
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large) {
                        Text(s.cancel, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick  = {
                            hue = 0f; sat = 0f; bri = 1f
                            onColorChange(Color.White)
                        },
                        modifier = Modifier.weight(1f),
                        shape    = MaterialTheme.shapes.large
                    ) {
                        Text(s.reset, style = MaterialTheme.typography.labelMedium)
                    }
                    if (isGlass) {
                        GlassButton(
                            onClick  = { onSave("#%06X".format(0xFFFFFF and currentColor.toArgb())) },
                            modifier = Modifier.weight(1f)
                        ) { Text(s.save, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    } else {
                        Button(
                            onClick  = { onSave("#%06X".format(0xFFFFFF and currentColor.toArgb())) },
                            modifier = Modifier.weight(1f),
                            shape    = MaterialTheme.shapes.large
                        ) { Text(s.save, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EnterRepoSheet  — bottom sheet to enter repo URL (optionally pre-filled pkg)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterRepoSheet(
    prefilledPackage : String = "",
    prefilledAppName : String = "",
    onConfirm        : (TrackedApp) -> Unit,
    onDismiss        : () -> Unit
) {
    val s           = LocalStrings.current
    var repoUrl     by remember { mutableStateOf("") }
    var pkgName     by remember { mutableStateOf(prefilledPackage) }
    var appName     by remember { mutableStateOf(prefilledAppName) }
    val focusReq    = remember { FocusRequester() }

    val parseRepoFullName: (String) -> String = { url ->
        val trimmed = url.trim().trimEnd('/')
        val githubPattern = Regex("(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+/[\\w.-]+)")
        val gitlabPattern = Regex("(?:https?://)?(?:www\\.)?gitlab\\.com/([\\w.-]+/[\\w.-]+)")
        githubPattern.find(trimmed)?.groupValues?.get(1)
            ?: gitlabPattern.find(trimmed)?.groupValues?.get(1)
            ?: trimmed.substringAfter("github.com/").substringAfter("gitlab.com/").let { if (it.contains("/")) it else "" }
    }

    val canConfirm = repoUrl.isNotBlank() && pkgName.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(s.trackAppTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value         = appName,
                onValueChange = { appName = it },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("App Name") },
                singleLine    = true
            )
            OutlinedTextField(
                value         = pkgName,
                onValueChange = { pkgName = it },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text(s.packageNameLabel) },
                singleLine    = true,
                placeholder   = { Text("com.example.app") }
            )
            OutlinedTextField(
                value         = repoUrl,
                onValueChange = { repoUrl = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusReq),
                label         = { Text(s.repoUrlLabel) },
                placeholder   = { Text(s.repoUrlPlaceholder) },
                singleLine    = true
            )

            LaunchedEffect(Unit) {
                if (prefilledPackage.isEmpty()) focusReq.requestFocus()
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(s.cancel)
                }
                Button(
                    onClick  = {
                        val fullName = parseRepoFullName(repoUrl)
                        onConfirm(TrackedApp(
                            packageName  = pkgName.trim(),
                            appName      = appName.trim().ifBlank { pkgName.trim() },
                            repoFullName = fullName,
                            repoUrl      = repoUrl.trim()
                        ))
                    },
                    enabled  = canConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(s.trackLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}