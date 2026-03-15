package org.telegram.messenger.auto;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;

final class AutoDialogPreviewFormatter {

    private static final int MAX_COMPACT_CHANNEL_PREVIEW = 120;

    @Nullable
    String format(@NonNull MessageObject messageObject) {
        CharSequence text = messageObject.messageTextShort;
        if (TextUtils.isEmpty(text)) {
            text = messageObject.caption;
        }
        if (TextUtils.isEmpty(text)) {
            text = messageObject.messageText;
        }
        if (TextUtils.isEmpty(text) && messageObject.messageOwner != null) {
            text = messageObject.messageOwner.message;
        }
        if (!TextUtils.isEmpty(text)) {
            return text.toString();
        }
        if (!messageObject.isMediaEmpty()) {
            return "[media]";
        }
        return null;
    }

    @NonNull
    String formatCompactChannelPreview(@NonNull MessageObject messageObject) {
        return compact(format(messageObject));
    }

    @NonNull
    String compact(@Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            return "No messages yet";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        while (normalized.contains("  ")) {
            normalized = normalized.replace("  ", " ");
        }
        if (normalized.length() <= MAX_COMPACT_CHANNEL_PREVIEW) {
            return normalized;
        }
        return normalized.substring(0, MAX_COMPACT_CHANNEL_PREVIEW - 3) + "...";
    }
}
