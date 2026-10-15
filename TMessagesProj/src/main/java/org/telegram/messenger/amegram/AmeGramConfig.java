/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 Umi.
 */
package org.telegram.messenger.amegram;

import org.telegram.messenger.BuildConfig;

/** AmeGram identity; upstream protocol and source namespaces remain intact. */
public final class AmeGramConfig {
    public static final String VERSION = BuildConfig.BUILD_VERSION_STRING;
    public static final String APPLICATION_ID = "dev.umi.amegram";
    public static final String DEVELOPER = "Umi";

    private AmeGramConfig() {}
}
