package com.soorkie.adblockvpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Early-2000s skeuomorphic palette (Windows XP "Luna" + Aqua flavours).
 */
object SkeuoColors {
    // Window / desktop chrome
    val Desktop = Color(0xFF3A6EA5)               // classic XP teal-blue desktop
    val WindowBg = Color(0xFFECE9D8)              // XP "Bliss" beige-grey
    val PanelTop = Color(0xFFFFFFFF)
    val PanelBottom = Color(0xFFD4D0C8)           // classic Win9x face
    val PanelBorderLight = Color(0xFFFFFFFF)
    val PanelBorderShadow = Color(0xFF808080)
    val PanelBorderDark = Color(0xFF404040)

    // Title bar – Luna blue gradient (toned down, softer/less saturated)
    val TitleBarTop = Color(0xFF5A7FB8)
    val TitleBarMid = Color(0xFF6F96C9)
    val TitleBarBottom = Color(0xFF3E5A85)
    val TitleBarText = Color(0xFFFFFFFF)
    val TitleBarShadow = Color(0xFF2A3A55)

    // Buttons (silver pill, Luna)
    val BtnTop = Color(0xFFFDFDFD)
    val BtnMid = Color(0xFFE5E5E5)
    val BtnBottom = Color(0xFFB8B8B8)
    val BtnBorder = Color(0xFF6B6B6B)

    // Primary "Start" button (green Luna)
    val GoTop = Color(0xFFCFF2B0)
    val GoMid = Color(0xFF7FCB54)
    val GoBottom = Color(0xFF3E8C1A)
    val GoBorder = Color(0xFF255D0A)

    // Stop / destructive (red Luna)
    val StopTop = Color(0xFFFFC8B8)
    val StopMid = Color(0xFFE96A4A)
    val StopBottom = Color(0xFFB02000)
    val StopBorder = Color(0xFF6E1500)

    // Sunken inset (table well)
    val InsetBg = Color(0xFFFFFFFF)
    val InsetAlt = Color(0xFFEEF3FA)              // alt row, faint blue
    val InsetBorderDark = Color(0xFF6E6E6E)
    val InsetBorderLight = Color(0xFFFFFFFF)

    // Status banners
    val OkBannerTop = Color(0xFFE7F4D7)
    val OkBannerBottom = Color(0xFFB6D98E)
    val OkBannerBorder = Color(0xFF5E8A2E)
    val WarnBannerTop = Color(0xFFFFE9A8)
    val WarnBannerBottom = Color(0xFFE8B440)
    val WarnBannerBorder = Color(0xFF8A5A00)

    val Text = Color(0xFF1A1A1A)
    val TextMuted = Color(0xFF555555)
    val Link = Color(0xFF0033AA)
}

/** Tahoma-ish system sans-serif for body, monospace for the table grid lines feel. */
val SkeuoFont: FontFamily = FontFamily.SansSerif

/** Theme wrapper – overrides Material3 colors so any leftover Material widgets blend in. */
@Composable
fun SkeuoTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = SkeuoColors.TitleBarTop,
        onPrimary = SkeuoColors.TitleBarText,
        primaryContainer = SkeuoColors.PanelBottom,
        onPrimaryContainer = SkeuoColors.Text,
        secondary = SkeuoColors.GoMid,
        onSecondary = Color.White,
        secondaryContainer = SkeuoColors.OkBannerTop,
        onSecondaryContainer = SkeuoColors.Text,
        background = SkeuoColors.WindowBg,
        onBackground = SkeuoColors.Text,
        surface = SkeuoColors.WindowBg,
        onSurface = SkeuoColors.Text,
        surfaceVariant = SkeuoColors.PanelBottom,
        onSurfaceVariant = SkeuoColors.Text,
        error = SkeuoColors.StopBottom,
        onError = Color.White,
        errorContainer = SkeuoColors.WarnBannerTop,
        onErrorContainer = SkeuoColors.Text,
        outline = SkeuoColors.PanelBorderShadow,
        outlineVariant = SkeuoColors.PanelBorderShadow,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Bevel / chrome modifiers
// ---------------------------------------------------------------------------

/** Two-tone "raised" 3D bevel like classic Win9x/XP buttons & panels. */
fun Modifier.skeuoRaisedBevel(
    light: Color = SkeuoColors.PanelBorderLight,
    shadow: Color = SkeuoColors.PanelBorderShadow,
    dark: Color = SkeuoColors.PanelBorderDark,
): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    // outer dark
    drawLine(dark, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), strokeWidth = 1f)
    drawLine(dark, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), strokeWidth = 1f)
    // inner shadow
    drawLine(shadow, Offset(1f, h - 1.5f), Offset(w - 1f, h - 1.5f), strokeWidth = 1f)
    drawLine(shadow, Offset(w - 1.5f, 1f), Offset(w - 1.5f, h - 1f), strokeWidth = 1f)
    // top-left highlight
    drawLine(light, Offset(0f, 0.5f), Offset(w - 1f, 0.5f), strokeWidth = 1f)
    drawLine(light, Offset(0.5f, 0f), Offset(0.5f, h - 1f), strokeWidth = 1f)
}

/** Inverse "sunken" bevel – used for the data table well / text fields. */
fun Modifier.skeuoSunkenBevel(
    light: Color = SkeuoColors.PanelBorderLight,
    shadow: Color = SkeuoColors.PanelBorderShadow,
    dark: Color = SkeuoColors.PanelBorderDark,
): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    // top/left dark = sunken
    drawLine(dark, Offset(0f, 0.5f), Offset(w, 0.5f), strokeWidth = 1f)
    drawLine(dark, Offset(0.5f, 0f), Offset(0.5f, h), strokeWidth = 1f)
    drawLine(shadow, Offset(1f, 1.5f), Offset(w - 1f, 1.5f), strokeWidth = 1f)
    drawLine(shadow, Offset(1.5f, 1f), Offset(1.5f, h - 1f), strokeWidth = 1f)
    // bottom/right light
    drawLine(light, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), strokeWidth = 1f)
    drawLine(light, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), strokeWidth = 1f)
}

/** Glossy vertical gradient – top half lighter, bottom half darker, with a faint highlight. */
fun Modifier.skeuoGlossy(
    top: Color,
    mid: Color,
    bottom: Color,
): Modifier = this.drawWithCache {
    val gradient = Brush.verticalGradient(
        0f to top,
        0.45f to mid,
        0.55f to mid,
        1f to bottom,
    )
    val highlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.5f to Color.White.copy(alpha = 0.0f),
    )
    onDrawBehind {
        drawRect(brush = gradient, size = Size(size.width, size.height))
        // glossy upper highlight band
        drawRect(
            brush = highlight,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, size.height * 0.5f),
        )
    }
}

// ---------------------------------------------------------------------------
// Composable building blocks
// ---------------------------------------------------------------------------

/** Classic XP/Aqua title bar with chunky white text + subtle shadow. */
@Composable
fun SkeuoTitleBar(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .skeuoGlossy(
                top = SkeuoColors.TitleBarMid,
                mid = SkeuoColors.TitleBarTop,
                bottom = SkeuoColors.TitleBarBottom,
            )
            .skeuoRaisedBevel(
                light = Color(0xFF6FA8FF),
                shadow = SkeuoColors.TitleBarBottom,
                dark = Color(0xFF000022),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // faux drop shadow for the title text
        Text(
            text = title,
            color = SkeuoColors.TitleBarShadow,
            fontFamily = SkeuoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 1.dp, top = 1.dp),
        )
        Text(
            text = title,
            color = SkeuoColors.TitleBarText,
            fontFamily = SkeuoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

/** Beveled "window" panel with the WindowBg face. Use for grouping content. */
@Composable
fun SkeuoPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(10.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(SkeuoColors.WindowBg)
            .skeuoRaisedBevel()
            .padding(padding),
    ) {
        content()
    }
}

/** Sunken white "well" – e.g. for the data table. */
@Composable
fun SkeuoWell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(SkeuoColors.InsetBg)
            .skeuoSunkenBevel(),
    ) {
        content()
    }
}

enum class SkeuoButtonStyle { Default, Go, Stop, Link }

/** Glossy pill button with bevel + pressed state. */
@Composable
fun SkeuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SkeuoButtonStyle = SkeuoButtonStyle.Default,
    enabled: Boolean = true,
) {
    val (top, mid, bottom, border, fg) = when (style) {
        SkeuoButtonStyle.Default -> Quint(
            SkeuoColors.BtnTop, SkeuoColors.BtnMid, SkeuoColors.BtnBottom,
            SkeuoColors.BtnBorder, SkeuoColors.Text,
        )
        SkeuoButtonStyle.Go -> Quint(
            SkeuoColors.GoTop, SkeuoColors.GoMid, SkeuoColors.GoBottom,
            SkeuoColors.GoBorder, Color.White,
        )
        SkeuoButtonStyle.Stop -> Quint(
            SkeuoColors.StopTop, SkeuoColors.StopMid, SkeuoColors.StopBottom,
            SkeuoColors.StopBorder, Color.White,
        )
        SkeuoButtonStyle.Link -> Quint(
            Color.Transparent, Color.Transparent, Color.Transparent,
            Color.Transparent, SkeuoColors.Link,
        )
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    if (style == SkeuoButtonStyle.Link) {
        Box(
            modifier = modifier
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                    onClick()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = fg,
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        return
    }

    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, border), shape)
            .background(if (pressed) bottom else Color.Transparent, shape)
            .then(
                if (pressed) Modifier.skeuoGlossy(top = bottom, mid = mid, bottom = top)
                else Modifier.skeuoGlossy(top = top, mid = mid, bottom = bottom)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        // text with subtle drop shadow for the colored buttons
        if (style != SkeuoButtonStyle.Default) {
            Text(
                text = text,
                color = Color.Black.copy(alpha = 0.35f),
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 1.dp, top = 1.dp),
            )
        }
        Text(
            text = text,
            color = fg,
            fontFamily = SkeuoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

/** LED-style status dot. */
@Composable
fun SkeuoLed(on: Boolean, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val core = if (on) Color(0xFF40D040) else Color(0xFFB04040)
    val rim = if (on) Color(0xFF0A4A0A) else Color(0xFF4A0A0A)
    val highlight = Color.White.copy(alpha = 0.85f)
    Box(
        modifier = modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            // rim
            drawCircle(rim, radius = r, center = c)
            // core gradient
            drawCircle(
                brush = Brush.radialGradient(
                    0f to core.copy(alpha = 1f),
                    1f to core.copy(alpha = 0.55f),
                    center = c,
                    radius = r,
                ),
                radius = r - 1.2f,
                center = c,
            )
            // glossy highlight
            drawCircle(
                brush = SolidColor(highlight),
                radius = r * 0.45f,
                center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                alpha = 0.9f,
            )
            // outer thin ring
            drawCircle(rim, radius = r, center = c, style = Stroke(width = 1f))
        },
    )
}

/** Group box / fieldset – panel with an inset etched border and a label tab. */
@Composable
fun SkeuoGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(SkeuoColors.WindowBg)
                .skeuoSunkenBevel()
                .padding(12.dp)
                .padding(top = 6.dp),
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .background(SkeuoColors.WindowBg)
                .padding(horizontal = 6.dp),
        ) {
            Text(
                text = title,
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = SkeuoColors.TitleBarTop,
            )
        }
    }
}

private data class Quint(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val border: Color,
    val fg: Color,
)

// Force Material widgets in dialogs to use legible black-on-white where needed.
@Composable
fun SkeuoContentColor(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides SkeuoColors.Text, content = content)
}

val SkeuoBodyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = SkeuoFont,
        color = SkeuoColors.Text,
        fontSize = 13.sp,
    )

val SkeuoLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(
        fontFamily = SkeuoFont,
        fontWeight = FontWeight.Bold,
        color = SkeuoColors.Text,
        fontSize = 12.sp,
    )

@Suppress("unused")
@Composable
fun SkeuoSpacerRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Suppress("unused")
fun darkSchemePlaceholder() = darkColorScheme()
