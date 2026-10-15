/* SPDX-License-Identifier: GPL-2.0-or-later */
package org.telegram.ui.amegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmeGramPaletteTest {
    @Test
    public void textAndAccentMeetWcagAaInEverySupportedPalette() {
        for (boolean dark : new boolean[]{false, true}) {
            for (boolean oled : new boolean[]{false, true}) {
                for (int background : new int[]{AmeGramPalette.background(dark, oled), AmeGramPalette.surface(dark, oled)}) {
                    assertContrast(AmeGramPalette.text(dark), background);
                    assertContrast(AmeGramPalette.secondaryText(dark), background);
                    assertContrast(AmeGramPalette.accessibleAccent(dark), background);
                }
            }
        }
    }

    @Test
    public void oledOnlyChangesTheDarkPalette() {
        assertEquals(AmeGramPalette.background(false, false), AmeGramPalette.background(false, true));
        assertEquals(AmeGramPalette.surface(false, false), AmeGramPalette.surface(false, true));
        assertEquals(0xFF000000, AmeGramPalette.background(true, true));
        assertEquals(0xFF000000, AmeGramPalette.surface(true, true));
        assertNotEquals(AmeGramPalette.background(true, false), AmeGramPalette.background(true, true));
    }

    private static void assertContrast(int foreground, int background) {
        double light = luminance(foreground), dark = luminance(background);
        double ratio = (Math.max(light, dark) + 0.05) / (Math.min(light, dark) + 0.05);
        assertTrue("Text contrast must be at least 4.5:1; got " + ratio, ratio >= 4.5);
    }

    private static double luminance(int color) {
        return channel(color >> 16) * 0.2126 + channel(color >> 8) * 0.7152 + channel(color) * 0.0722;
    }

    private static double channel(int value) {
        double srgb = (value & 255) / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}
