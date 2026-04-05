package org.telegram.ui.Components.poll.attached;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.Components.poll.PollAttachedMedia;

public class PollAttachedMediaGallery extends PollAttachedMedia {
    public final MediaController.PhotoEntry photoEntry;
    public final SendMessagesHelper.SendingMediaInfo sendingMediaInfo;

    public PollAttachedMediaGallery(SendMessagesHelper.SendingMediaInfo sendingMediaInfo) {
        this.sendingMediaInfo = sendingMediaInfo;
        this.photoEntry = sendingMediaInfo.originalPhotoEntry;
        imageReceiver.setRoundRadius(dp(7));
        setupImageReceiver(imageReceiver);
    }

    private void setupImageReceiver(ImageReceiver imageReceiver) {
        imageReceiver.setOrientation(0, true);

        ImageLocation location = null;
        if (photoEntry.coverPath != null) {
            location = ImageLocation.getForPath(photoEntry.coverPath);
        } else if (photoEntry.thumbPath != null) {
            location = ImageLocation.getForPath(photoEntry.thumbPath);
        } else if (photoEntry.path != null) {
            if (photoEntry.isVideo && !photoEntry.isLivePhoto) {
                location = ImageLocation.getForPath("vthumb://" + photoEntry.imageId + ":" + photoEntry.path);
            } else {
                location = ImageLocation.getForPath("thumb://" + photoEntry.imageId + ":" + photoEntry.path);
                imageReceiver.setOrientation(photoEntry.orientation, photoEntry.invert, true);
            }
        } else {
            imageReceiver.clearImage();
        }

        if (location != null) {
            imageReceiver.setImage(location, null, null, null, null, 0);
        } else {
            imageReceiver.clearImage();
        }
    }

    @Override
    protected void draw(Canvas canvas, int w, int h) {
        imageReceiver.setImageCoords(0, 0, w, h);
        imageReceiver.draw(canvas);
    }
}
