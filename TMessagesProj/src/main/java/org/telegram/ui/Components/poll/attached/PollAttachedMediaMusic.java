package org.telegram.ui.Components.poll.attached;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.poll.PollAttachedMedia;

public class PollAttachedMediaMusic extends PollAttachedMedia {
    public final MessageObject messageObject;
    private final RadialProgress2 radialProgress;

    public PollAttachedMediaMusic(MessageObject messageObject) {
        this.messageObject = messageObject;
        this.radialProgress = new RadialProgress2(null);


        final TLRPC.Document document = messageObject.getDocument();
        if (MessageObject.isDocumentHasThumb(document)) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(22), true, null, false);
            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(44), true, thumb, true);
            radialProgress.setImageOverlay(image, thumb, document, messageObject);
        } else {
            String artworkUrl = MessageObject.getArtworkUrl(document, true);
            if (!TextUtils.isEmpty(artworkUrl)) {
                radialProgress.setImageOverlay(artworkUrl);
            } else {
                radialProgress.setImageOverlay(null, null, null);
            }
        }

        radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
    }

    @Override
    public void attach(View parent) {
        super.attach(parent);
        radialProgress.setParent(parent);
        radialProgress.onAttachedToWindow();
        radialProgress.setIcon(MediaActionDrawable.ICON_PLAY, false, false);
    }

    @Override
    public void detach() {
        super.detach();
        radialProgress.onDetachedFromWindow();
    }

    @Override
    protected void draw(Canvas canvas, int w, int h) {
        radialProgress.setCircleRadius(w / 2);
        radialProgress.setProgressRect(0, 0, w, h);
        radialProgress.draw(canvas);
    }
}
