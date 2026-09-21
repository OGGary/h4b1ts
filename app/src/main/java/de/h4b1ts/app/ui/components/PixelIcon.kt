package de.h4b1ts.app.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.h4b1ts.app.R

/**
 * The icon set: 16x16 pixel art, two ink colours on a transparent ground.
 *
 * They live in `drawable-nodpi` rather than a density bucket so Android never
 * resamples them at decode time. A 16 px source dropped into plain `drawable`
 * would be treated as mdpi and blown up with bilinear filtering before it ever
 * reached the screen, which is how pixel art turns to mush.
 */
enum class H4Icon(@DrawableRes val res: Int, val label: String) {
    ARROW_LEFT(R.drawable.ic_arrow_left, "Back"),
    ARROW_RIGHT(R.drawable.ic_arrow_right, "Forward"),

    FACE_ANGEL(R.drawable.ic_face_angel, "Angel"),
    FACE_ANGRY(R.drawable.ic_face_angry, "Angry"),
    FACE_DEMON(R.drawable.ic_face_demon, "Demon"),
    FACE_EVIL(R.drawable.ic_face_evil, "Gloating"),
    FACE_GRINNING(R.drawable.ic_face_grinning, "Grinning"),
    FACE_HAPPY(R.drawable.ic_face_happy, "Happy"),
    FACE_LAUGHING(R.drawable.ic_face_laughing, "Laughing"),
    FACE_NEUTRAL(R.drawable.ic_face_neutral, "Neutral"),
    FACE_SAD(R.drawable.ic_face_sad, "Sad"),
    FACE_FROWN(R.drawable.ic_face_frown, "Frowning"),
    FACE_SLEEPING(R.drawable.ic_face_sleeping, "Sleeping"),
    FACE_SMILING(R.drawable.ic_face_smiling, "Smiling"),
    FACE_SURPRISED(R.drawable.ic_face_surprised, "Surprised"),
    FACE_TONGUE(R.drawable.ic_face_tongue, "Tongue out"),
    FACE_GROSSED(R.drawable.ic_face_grossed, "Grossed out"),

    HAND_FIST(R.drawable.ic_hand_fist, "Fist"),
    HAND_MIDDLE_FINGER(R.drawable.ic_hand_middle_finger, "Middle finger"),
    HAND_POINT(R.drawable.ic_hand_point, "Pointing"),
    HAND_HORNS(R.drawable.ic_hand_horns, "Horns"),
    HAND_THUMBS_DOWN(R.drawable.ic_hand_thumbs_down, "Thumbs down"),
    HAND_THUMBS_UP(R.drawable.ic_hand_thumbs_up, "Thumbs up"),
    HAND_WAVE(R.drawable.ic_hand_wave, "Wave"),
    HANDS_CLAP(R.drawable.ic_hands_clap, "Applause"),
    POOP(R.drawable.ic_poop, "Poop"),

    ALARM(R.drawable.ic_alarm, "Alarm"),
    BULB(R.drawable.ic_bulb, "Idea"),
    CLIPBOARD(R.drawable.ic_clipboard, "Checklist"),
    CLOCK(R.drawable.ic_clock, "Clock"),
    FILE(R.drawable.ic_file, "Document"),
    FILE_ADD(R.drawable.ic_file_add, "New document"),
    FOLDER(R.drawable.ic_folder, "Folder"),
    GEAR(R.drawable.ic_gear, "Settings"),
    HOURGLASS(R.drawable.ic_hourglass, "Hourglass"),
    PHONE(R.drawable.ic_phone, "Phone"),
    PIN_BOARD(R.drawable.ic_pin_board, "Board"),
    SPEECH_BUBBLE(R.drawable.ic_speech_bubble, "Conversation"),
}

/** The one size every icon is drawn at unless a caller says otherwise. */
val H4IconSize = 20.dp

/**
 * Draws a [H4Icon] in theme colours.
 *
 * Every icon in the set ships as pure black lines over a pure white interior. That
 * survives light mode and disappears entirely in dark mode, so the two source
 * colours are remapped at draw time rather than baked in: black becomes [ink],
 * white becomes [fill]. Doing it here instead of with a `-night` resource
 * qualifier is what lets the app's own Light/Dark/System setting drive the icons —
 * a qualifier would follow the system and contradict the user's choice.
 *
 * [FilterQuality.None] keeps the upscale nearest-neighbour. Without it the 16 px
 * source is interpolated up to 20 dp and every edge goes soft.
 */
@Composable
fun PixelIcon(
    icon: H4Icon,
    modifier: Modifier = Modifier,
    size: Dp = H4IconSize,
    ink: Color = MaterialTheme.colorScheme.onBackground,
    fill: Color = Color.Transparent,
    contentDescription: String? = icon.label,
) {
    val source = ImageBitmap.imageResource(icon.res)
    val recoloured = remember(source, ink, fill) { source.recolour(ink, fill) }

    Image(
        bitmap = recoloured,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

/**
 * Convenience for the common case of an icon that means the same thing as the
 * text beside it, where repeating the label to a screen reader is noise.
 */
@Composable
fun PixelIconDecoration(
    icon: H4Icon,
    modifier: Modifier = Modifier,
    size: Dp = H4IconSize,
    ink: Color = MaterialTheme.colorScheme.onBackground,
    fill: Color = Color.Transparent,
) = PixelIcon(icon, modifier, size, ink, fill, contentDescription = null)

/**
 * Swaps the two source inks. Pixels are compared on the colour channels only:
 * the transparent ground is stored as transparent black, so testing alpha first
 * is what stops it being repainted as an outline.
 */
private fun ImageBitmap.recolour(ink: Color, fill: Color): ImageBitmap {
    val pixels = toPixelMap()
    val inkArgb = ink.toArgb()
    val fillArgb = fill.toArgb()
    val out = IntArray(width * height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val source = pixels[x, y]
            out[y * width + x] = when {
                source.alpha == 0f -> 0
                // Black is the linework, anything lighter is the interior. A
                // midpoint test rather than an equality test so the set keeps
                // working if an icon is ever saved with anti-aliased edges.
                source.red + source.green + source.blue < 1.5f -> inkArgb
                else -> fillArgb
            }
        }
    }

    return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
