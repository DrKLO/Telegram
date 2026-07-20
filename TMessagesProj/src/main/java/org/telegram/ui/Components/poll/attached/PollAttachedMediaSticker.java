package org.telegram.ui.Components.poll.attached;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.poll.PollAttachedMedia;

public class PollAttachedMediaSticker extends PollAttachedMedia {
    public final TLRPC.Document sticker;
    public final Object parent;
    public final boolean isEmoji;

    public PollAttachedMediaSticker(TLRPC.Document sticker, Object parent) {
        this.sticker = sticker;
        this.parent = parent;
        this.isEmoji = MessageObject.isAnimatedEmoji(sticker);
        setupImageReceiver(imageReceiver);
    }

    private void setupImageReceiver(ImageReceiver imageReceiver) {
        final boolean isWebpSticker = MessageObject.isStickerDocument(sticker) || MessageObject.isVideoSticker(sticker);
        final boolean isAnimatedSticker = MessageObject.isAnimatedStickerDocument(sticker, true);

        final Drawable thumb = DocumentObject.getSvgThumb(sticker, Theme.key_chat_serviceBackground, 1.0f);
        imageReceiver.setImage(ImageLocation.getForDocument(sticker), "38_38", thumb, sticker.size,
            isWebpSticker ? "webp" : null, parent, 0);
    }

    @Override
    protected void draw(Canvas canvas, int w, int h) {
        imageReceiver.setImageCoords(0, 0, w, h);
        imageReceiver.draw(canvas);
    }
}
