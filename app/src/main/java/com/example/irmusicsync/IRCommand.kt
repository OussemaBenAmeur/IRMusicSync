package com.example.irmusicsync

class IRCommand {
    var color: Color = Color.RED

    enum class Color {
        OFF,
        RED,
        GREEN,
        BLUE,
        WHITE,
        LIGHT_GREEN,
        VERY_LIGHT_GREEN,
        TURQUOISE,
        ORANGE,
        YELLOW,
        PURPLE,
        LIGHT_PURPLE,
        PINK
    }

    companion object {
        // Electronic Music / Party optimized color sequences
        val PARTY_SEQUENCE = listOf(
            Color.RED, Color.BLUE, Color.GREEN, Color.PURPLE,
            Color.PINK, Color.ORANGE, Color.YELLOW, Color.WHITE, Color.TURQUOISE
        )

        val NEON_ELECTRONIC_SEQUENCE = listOf(
            Color.PURPLE, Color.PINK, Color.TURQUOISE,
            Color.LIGHT_GREEN, Color.BLUE, Color.LIGHT_PURPLE
        )

        val BASS_COLORS_SEQUENCE = listOf(
            Color.RED, Color.PURPLE, Color.BLUE, Color.ORANGE, Color.PINK
        )

        val RAVE_SEQUENCE = listOf(
            Color.GREEN, Color.PURPLE, Color.YELLOW,
            Color.PINK, Color.TURQUOISE, Color.WHITE
        )

        val FESTIVAL_SEQUENCE = listOf(
            Color.ORANGE, Color.YELLOW, Color.PINK,
            Color.TURQUOISE, Color.LIGHT_GREEN, Color.PURPLE
        )

        val CLUB_SEQUENCE = listOf(
            Color.BLUE, Color.PURPLE, Color.RED, Color.WHITE, Color.PINK
        )

        // Legacy sequences for backward compatibility
        val RAINBOW_SEQUENCE = listOf(
            Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN,
            Color.LIGHT_GREEN, Color.TURQUOISE, Color.BLUE,
            Color.PURPLE, Color.PINK, Color.WHITE
        )

        val WARM_SEQUENCE = listOf(
            Color.RED, Color.ORANGE, Color.YELLOW, Color.PINK, Color.LIGHT_PURPLE
        )

        val COOL_SEQUENCE = listOf(
            Color.BLUE, Color.TURQUOISE, Color.GREEN, Color.LIGHT_GREEN, Color.PURPLE
        )

        val NATURE_SEQUENCE = listOf(
            Color.GREEN, Color.LIGHT_GREEN, Color.VERY_LIGHT_GREEN,
            Color.TURQUOISE, Color.BLUE, Color.YELLOW
        )

        val ENERGY_SEQUENCE = listOf(
            Color.RED, Color.ORANGE, Color.YELLOW, Color.WHITE,
            Color.PINK, Color.BLUE   // ✅ fixed
        )
    }
}
