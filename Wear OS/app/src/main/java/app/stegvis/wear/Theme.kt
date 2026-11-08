package app.stegvis.wear

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

// Tokens from design/DESIGN.md.
val Bg = Color(0xFF08070E)
val Surface = Color(0xFF17161E)
val Surface2 = Color(0xFF24232D)
val Ink = Color(0xFFF5F1EA)
val Muted = Color(0xFF9F9DB2)
val Coral = Color(0xFFFF704F)
val CoralOn = Color(0xFF08070E)
val Green = Color(0xFF8BD36F)
val Gold = Color(0xFFDDA63C)
val Purple = Color(0xFF7C60BB)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Outfit = FontFamily(Font(R.font.outfit, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val CoralDeep = Color(0xFFEA6242)
val CoralSoft = Color(0xFFFF9C82)
val Sage = Color(0xFF7CAE5C)
val Track = Color(0xFF26252F)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val OutfitLight = FontFamily(Font(R.font.outfit, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val DmSans = FontFamily(
    Font(R.font.dmsans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.dmsans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.dmsans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

object Type {
    val hero = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = Ink)
    val display = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Ink)
    val number = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink)
    val title = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
    val body = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink)
    val caption = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = Muted)
}

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.Center,
    maxLines: Int = 3,
) {
    Text(
        text, modifier = modifier, style = style, color = if (color == Color.Unspecified) style.color else color,
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Pill button: 48 dp high, at least 120 dp wide (design). */
@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    minWidth: Int = 120,
) {
    Box(
        modifier
            .height(48.dp)
            .defaultMinSize(minWidth = minWidth.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (!enabled && primary) Surface else if (primary) Coral else Surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, Type.title, color = if (!enabled) Muted else if (primary) CoralOn else Ink, maxLines = 1)
    }
}

/** Error line: a small coral "!" badge plus the text, so the color is not the only signal. */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Coral), contentAlignment = Alignment.Center) {
            Txt("!", Type.caption, color = CoralOn, maxLines = 1)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Txt(text, Type.caption, color = Coral, modifier = Modifier.weight(1f, fill = false))
    }
}

/** Small status chip, e.g. "Offline". */
@Composable
fun StatusPill(label: String, color: Color = Muted) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Surface2).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Txt(label, Type.caption, color = color, maxLines = 1)
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** True when the user turned animations off (animator duration scale 0). */
fun Context.reducedMotion(): Boolean =
    try { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }

fun vibrateGoal(context: Context) {
    try {
        if (!Vault(sharedKv(context)).haptics) return
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60, 90, 120), -1))
    } catch (e: Exception) {
        // Haptics are a bonus; never crash for them.
    }
}

