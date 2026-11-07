package org.telegram.messenger.car;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Resolves the audio attachment that lets Android Auto play a received voice note straight
 * from the message, rather than only rendering "Voice message" as text.
 *
 * Kept separate from {@link HomeScreen} so the decision can be unit tested: it is pure
 * apart from the FileProvider lookup, and the car host is not involved.
 */
final class CarVoiceAttachment {

    /** Telegram records voice notes as Ogg/Opus. */
    static final String MIME_TYPE = "audio/ogg";

    private CarVoiceAttachment() {
    }

    /**
     * Whether the recording may be disclosed to the car at all.
     *
     * <p>Separate from {@link #resolveUri} so the caller can tell "not allowed" apart from
     * "not downloaded yet": the second is worth fetching on demand, the first must not
     * trigger any work.
     */
    static boolean isDisclosureAllowed(boolean previewAllowed, boolean locked) {
        return previewAllowed && !locked;
    }

    /**
     * Resolves a {@code content://} URI for a voice note, or null when it must not or cannot
     * be attached.
     *
     * <p>Attaching audio is a stronger disclosure than the text body: when previews are
     * suppressed the body already reads as a generic placeholder, so playing the actual
     * recording aloud in the car would leak precisely what the user asked to hide. Both
     * privacy flags therefore gate the attachment, mirroring how NotificationsController
     * guards the equivalent MessagingStyle attachment.
     *
     * @param authority      FileProvider authority, i.e. applicationId + ".provider"
     * @param file           local file for the note, from FileLoader#getPathToMessage
     * @param previewAllowed preview[0] as reported by getShortStringForMessage
     * @param locked         passcode is set or pending, so content must stay hidden
     */
    @Nullable
    static Uri resolveUri(Context context, String authority, @Nullable File file,
                          boolean previewAllowed, boolean locked) {
        if (!isDisclosureAllowed(previewAllowed, locked)) {
            return null;
        }
        // Not downloaded yet, or a zero-length placeholder from an interrupted download.
        if (file == null || !file.exists() || file.length() == 0) {
            return null;
        }
        try {
            return FileProvider.getUriForFile(context, authority, file);
        } catch (IllegalArgumentException | NullPointerException e) {
            // The file resolved outside every configured provider path, or the authority
            // is not registered. Fall back to text-only rather than failing the message.
            return null;
        }
    }
}
