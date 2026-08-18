package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

public final class KidModeConfig {

    public static final boolean VIDEO_PLAYBACK_BLOCKED = true;

    private KidModeConfig() {
    }

    public static boolean isVideoPlaybackBlocked() {
        return VIDEO_PLAYBACK_BLOCKED;
    }

    public static boolean shouldBlockVideoPlayback(MessageObject messageObject) {
        return isVideoPlaybackBlocked() && messageObject != null && (messageObject.isVideo() || messageObject.isRoundVideo() || messageObject.type == MessageObject.TYPE_VIDEO);
    }

    public static boolean shouldBlockVideoPlayback(TLRPC.BotInlineResult inlineResult) {
        if (!isVideoPlaybackBlocked() || inlineResult == null) {
            return false;
        }
        return "video".equals(inlineResult.type) || MessageObject.isVideoDocument(inlineResult.document);
    }
}
