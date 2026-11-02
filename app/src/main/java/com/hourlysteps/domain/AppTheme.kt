package com.hourlysteps.domain

/**
 * A colour theme, as ARGB. Every theme keeps the black background that suits a watch screen, and
 * picks the accents around it. The rest of each palette is worked out from those accents so a new
 * theme only has to choose four colours.
 */
enum class AppTheme(
    val label: String,
    val description: String,
    /** Outer ring and the main accent: today's total. */
    val today: Long,
    /** Inner ring: this slot. */
    val slot: Long,
    /** Outer ring once the daily goal is met, and other moments of success. */
    val goalMet: Long,
    /** The unfilled part of a ring, and the background of list rows. */
    val track: Long,
) {
    VIOLET("Violet", "Light purple and violet", 0xFFE3D2FF, 0xFFA98BFF, 0xFFFFCC80, 0xFF2D2640),
    CLASSIC("Classic", "Lavender and cyan", 0xFFD0BCFF, 0xFF4DD0E1, 0xFFFFCC80, 0xFF2E2A36),
    OCEAN("Ocean", "Sky blue and teal", 0xFF90CAF9, 0xFF2DD4BF, 0xFFFFE082, 0xFF1C2C36),
    FOREST("Forest", "Leaf green and lime", 0xFF9CD6A0, 0xFFD4E157, 0xFFFFD54F, 0xFF22302A),
    SUNSET("Sunset", "Coral and amber", 0xFFFF9E80, 0xFFFFC44D, 0xFFFFF176, 0xFF36281F),
    ROSE("Rose", "Soft pink and raspberry", 0xFFFFC2DC, 0xFFFF6FA8, 0xFFFFE082, 0xFF36252E),
    GRAPHITE("Graphite", "White and steel", 0xFFEEEEEE, 0xFF90A4AE, 0xFFFFD54F, 0xFF2A2C2E);

    val onAccent: Long get() = DARK_TEXT

    /** Selected rows and Plan A days. */
    val primaryContainer: Long get() = mix(slot, BLACK, 0.6f)
    val onPrimaryContainer: Long get() = mix(today, WHITE, 0.5f)

    val secondaryContainer: Long get() = mix(today, BLACK, 0.7f)
    val onSecondaryContainer: Long get() = mix(today, WHITE, 0.5f)

    /** Plan B days, which have to stand apart from Plan A. */
    val tertiaryContainer: Long get() = mix(goalMet, BLACK, 0.65f)
    val onTertiaryContainer: Long get() = mix(goalMet, WHITE, 0.5f)

    val surfaceLow: Long get() = mix(track, BLACK, 0.3f)
    val surfaceHigh: Long get() = mix(track, WHITE, 0.08f)

    companion object {
        val DEFAULT = VIOLET

        private const val BLACK = 0xFF000000
        private const val WHITE = 0xFFFFFFFF
        private const val DARK_TEXT = 0xFF1C1B1F

        /** [amount] of the way from [from] to [to], channel by channel. */
        fun mix(from: Long, to: Long, amount: Float): Long {
            fun channel(shift: Int): Long {
                val a = (from shr shift) and 0xFF
                val b = (to shr shift) and 0xFF
                return (a + (b - a) * amount).toLong() and 0xFF
            }
            return 0xFF000000 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
