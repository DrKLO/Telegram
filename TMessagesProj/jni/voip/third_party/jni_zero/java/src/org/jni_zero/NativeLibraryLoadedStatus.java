// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

import org.chromium.build.BuildConfig;

/**
 * Exposes native library loading status.
 */
public class NativeLibraryLoadedStatus {
    /**
     * Interface for querying native method availability.
     */
    public interface NativeLibraryLoadedStatusProvider {
        boolean areNativeMethodsReady();
    }

    private static NativeLibraryLoadedStatusProvider sProvider;

    public static class NativeNotLoadedException extends RuntimeException {
        public NativeNotLoadedException(String s) {
            super(s);
        }
    }

    public static void checkLoaded() {
        if (sProvider == null) return;

        if (!sProvider.areNativeMethodsReady()) {
            throw new NativeNotLoadedException(
                    "Native method called before the native library was ready.");
        }
    }

    public static void setProvider(NativeLibraryLoadedStatusProvider statusProvider) {
        sProvider = statusProvider;
    }

    public static NativeLibraryLoadedStatusProvider getProviderForTesting() {
        return sProvider;
    }
}
