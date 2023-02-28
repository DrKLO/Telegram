package org.lilchill.RozZzmiThemer;

import kotlin.math.roundToInt

class ColorsUtils {
    companion object {
        fun getColorTints(themeProps: ThemeModel): Map<String, String> {
            val ac_500 = getRgb(themeProps.color)
            val ac_200: List<Int>
            val ac_300: List<Int>
            val ac_700: List<Int>
            val ac_800: List<Int>

            if (themeProps.isDefault) {
                // default theme
                ac_200 = ac_500.map { getLighter(it, 0.8) }
                ac_300 = ac_500.map { getLighter(it, 0.2) }
                ac_700 = ac_500.map { getDarker(it, 0.2) }
                ac_800 = ac_500.map { getDarker(it, 0.4) }
            } else {
                // soza theme
                ac_200 = ac_500.map { getLighter(it, 0.8) }
                ac_300 = ac_500.map { getLighter(it, 0.6) }

                ac_700 = ac_500.map { getDarker(it, 0.30) }
                ac_800 = ac_500.map { getDarker(it, 0.7) }
            }

            return mapOf(
                "ac_200" to getHex(ac_200),
                "ac_300" to getHex(ac_300),
                "ac_500" to getHex(ac_500),
                "ac_700" to getHex(ac_700),
                "ac_800" to getHex(ac_800),
            )
        }

        // make color lighter
        private fun getLighter(n: Int, factor: Double): Int {
            return (n + (factor * (255 - n))).roundToInt()
        }

        // make color darker
        private fun getDarker(n: Int, factor: Double): Int {
            return (n * (1 - factor)).roundToInt()
        }

        // hex(example: FFFFFF) to rgb list
        private fun getRgb(accent: String): List<Int> {
            val r: Int = accent.substring(0, 2).toInt(16) // 16 for hex
            val g: Int = accent.substring(2, 4).toInt(16) // 16 for hex
            val b: Int = accent.substring(4, 6).toInt(16) // 16 for hex

            return listOf(r, g, b)
        }

        // rgb list to hex(#FFFFFF)
        private fun getHex(rgb: List<Int>): String {
            return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2])
        }
    }
}