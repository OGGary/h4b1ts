package de.h4b1ts.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.R

/**
 * Pixelbasel (SIL OFL, © GGBotNet) drawn on a 16 px em: 64 font units per pixel,
 * 9 px caps, 7 px x-height.
 *
 * Two consequences drive everything below.
 *
 * The same file is registered for every weight on purpose. A pixel font has no
 * bold cut, so asking for one makes the renderer smear the glyphs sideways by a
 * fraction of a pixel — which is exactly the thing a pixel font must not do.
 * Pointing all four weights at the one file makes [FontWeight.Bold] resolve to a
 * real face instead of a synthesised one. Weight therefore no longer carries
 * hierarchy anywhere in the app; size, colour and letter spacing do, which is how
 * these screens were already built.
 */
val Pixelbasel = FontFamily(
    Font(R.font.pixelbasel, FontWeight.Normal),
    Font(R.font.pixelbasel, FontWeight.Medium),
    Font(R.font.pixelbasel, FontWeight.SemiBold),
    Font(R.font.pixelbasel, FontWeight.Bold),
)

/**
 * Caps land at 0.5625 em against Roboto's 0.71, so like-for-like sizes read about
 * a fifth smaller. Every size here is the old one plus two, which restores the
 * apparent size without growing the layouts enough to break a row.
 *
 * Line heights are looser than Material's defaults. Blocky glyphs with flat tops
 * and bottoms leave no optical air of their own, so the leading has to supply it.
 */
val H4Typography = Typography(
    displayLarge = pixel(42.sp, 52.sp),
    displayMedium = pixel(36.sp, 44.sp),
    displaySmall = pixel(32.sp, 40.sp),

    headlineLarge = pixel(32.sp, 40.sp),
    headlineMedium = pixel(28.sp, 36.sp),
    headlineSmall = pixel(24.sp, 32.sp),

    titleLarge = pixel(20.sp, 28.sp),
    titleMedium = pixel(18.sp, 26.sp),
    titleSmall = pixel(16.sp, 24.sp),

    bodyLarge = pixel(16.sp, 26.sp),
    bodyMedium = pixel(15.sp, 24.sp),
    bodySmall = pixel(14.sp, 22.sp),

    labelLarge = pixel(15.sp, 22.sp, tracking = 0.5.sp),
    labelMedium = pixel(14.sp, 20.sp, tracking = 0.5.sp),
    labelSmall = pixel(13.sp, 18.sp, tracking = 0.5.sp),
)

private fun pixel(
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    tracking: androidx.compose.ui.unit.TextUnit = 0.sp,
) = TextStyle(
    fontFamily = Pixelbasel,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
)
