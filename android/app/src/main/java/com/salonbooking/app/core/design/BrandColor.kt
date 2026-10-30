package com.salonbooking.app.core.design

import kotlin.math.pow

/** Business brand colours as 0xRRGGBB integers. Plain Kotlin so it can be unit tested. */
object BrandColor {

    private const val WHITE = 0xFFFFFF
    private const val CHARCOAL = 0x1A1A1A
    private val HEX = Regex("^#([0-9a-fA-F]{6})$")

    /** Parses "#RRGGBB"; returns null for anything else. */
    fun parse(hex: String?): Int? = hex?.let { HEX.matchEntire(it.trim()) }?.groupValues?.get(1)?.toInt(16)

    /** White text unless it fails WCAG AA (4.5:1) on [background], then charcoal. */
    fun readableTextOn(background: Int): Int =
        if (contrastRatio(WHITE, background) >= 4.5) WHITE else CHARCOAL

    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
