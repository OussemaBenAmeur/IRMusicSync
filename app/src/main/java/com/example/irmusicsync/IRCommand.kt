package com.example.irmusicsync

data class IRCommand(
    val color: Color = Color.RED
) {
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
        // Keep the app focused on a few palettes that read clearly on simple IR controllers.
        val NEON_SEQUENCE = listOf(
            Color.PURPLE,
            Color.PINK,
            Color.TURQUOISE,
            Color.BLUE,
            Color.LIGHT_GREEN
        )

        val SUNSET_SEQUENCE = listOf(
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.PINK,
            Color.WHITE
        )

        val ICE_SEQUENCE = listOf(
            Color.BLUE,
            Color.TURQUOISE,
            Color.WHITE,
            Color.LIGHT_GREEN,
            Color.PURPLE
        )
    }
}
