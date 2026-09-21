package de.h4b1ts.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The whole palette in one place.
 *
 * Two rules carry the design:
 *
 * 1. Colour is the reward, monochrome is the restriction. The accent only ever
 *    marks something that was earned.
 * 2. [Void] is semantic, not a theme colour. Pure black means "blocked", is used
 *    nowhere else, and stays black in light mode too — the shield is a state, not
 *    a surface. On an OLED panel those pixels are then literally off.
 *
 * The greys carry a slight cool bias rather than being neutral, so they read as
 * chosen instead of inherited.
 *
 * Light mode cannot use the neon accents: no bright neon clears 4.5:1 against
 * white on any hue (lime manages 1.2:1). Each accent therefore has a darkened
 * sibling that carries the same identity at readable contrast.
 */
object H4Colors {

    /** Semantic, never themed: the blocked state. */
    val Void = Color(0xFF000000)

    // -- dark ------------------------------------------------------------
    val Ink = Color(0xFF0E0E14)
    val Surface = Color(0xFF171720)
    val Line = Color(0xFF2B2B36)
    val TextMuted = Color(0xFF9A9AAE)
    val TextPrimary = Color(0xFFF5F5F8)

    /** Lime, 16.2:1 on [Ink]. */
    val Volt = Color(0xFFC6FF00)

    /** Magenta, 6.0:1 on [Ink]. */
    val Pulse = Color(0xFFFF2BD6)

    val Warn = Color(0xFFFFB43D)
    val Alert = Color(0xFFFF8A7E)

    // -- light -----------------------------------------------------------
    val Paper = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF6F6F8)
    val LineLight = Color(0xFFE3E3EA)
    val TextMutedLight = Color(0xFF6E6E7E)
    val TextPrimaryLight = Color(0xFF16161E)

    /** Volt at 6.2:1 on white. Not neon any more, and it cannot be. */
    val VoltInk = Color(0xFF4A6B00)

    /** Pulse at 7.6:1 on white. */
    val PulseInk = Color(0xFF9E0080)

    val WarnInk = Color(0xFF8A5200)
    val AlertInk = Color(0xFFC3352B)

    // -- shared ----------------------------------------------------------

    /**
     * Works on both grounds, but only for borders and icons: 4.0:1 on white is
     * under the threshold for body text.
     */
    val Mid = Color(0xFF7E7E8F)
}
