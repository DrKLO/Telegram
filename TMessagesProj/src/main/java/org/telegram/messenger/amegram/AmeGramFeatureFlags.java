/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 Umi.
 */
package org.telegram.messenger.amegram;

/** Compile-time availability gates. Add a separate, default-off preference per experiment. */
public final class AmeGramFeatureFlags {
    public static final boolean CUSTOM_APP_ICONS_AVAILABLE = false;
    public static final boolean AME_LAB_AVAILABLE = false;
    public static final boolean LIQUID_GLASS_ALLOWED = false;

    private AmeGramFeatureFlags() {}
}
