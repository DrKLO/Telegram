package org.telegram.messenger.auto;

import androidx.annotation.Nullable;

final class AutoUiFeedback {

    private static String pendingMainScreenToast;

    private AutoUiFeedback() {
    }

    static synchronized void showOnMainScreen(String text) {
        pendingMainScreenToast = text;
    }

    @Nullable
    static synchronized String consumeMainScreenToast() {
        String text = pendingMainScreenToast;
        pendingMainScreenToast = null;
        return text;
    }
}
