package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.GallerySheet;
import org.telegram.ui.Stories.recorder.TimelineView;

public class PhotoViewerCoverEditor extends FrameLayout {

    public TimelineView timelineView;
    public ButtonWithCounterView button;
    public EditCoverButton openGalleryButton;

    private VideoPlayer videoPlayer;
    private long time = -1;
    private float aspectRatio = 1.39f;

    private GallerySheet gallerySheet;

    public PhotoViewerCoverEditor(Context context, Theme.ResourcesProvider resourcesProvider, PhotoViewer photoViewer, BlurringShader.BlurManager blurManager) {
        super(context);

        timelineView = new TimelineView(context, null, null, resourcesProvider, blurManager);
        timelineView.setCover();
        addView(timelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TimelineView.heightDp(), Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 16 + 48 + 10));

        button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText("Save Cover", false);
        addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 10, 10, 10, 10));

        openGalleryButton = new EditCoverButton(context, photoViewer, "Choose from Gallery", true);
        openGalleryButton.setOnClickListener(v -> {
            if (gallerySheet == null) {
                gallerySheet = new GallerySheet(context, resourcesProvider, aspectRatio);
                gallerySheet.setOnDismissListener(() -> {
                    gallerySheet = null;
                });
                gallerySheet.setOnGalleryImage(onGalleryListener);
            }
            gallerySheet.show();
        });
        addView(openGalleryButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 60, 0, 60, 16 + 48 + 10 + 60));

        timelineView.setDelegate(new TimelineView.TimelineDelegate() {
            @Override
            public void onVideoLeftChange(boolean released, float left) {
                if (videoPlayer == null) return;
                final long _duration = videoPlayer.getDuration();
                time = (long) ((left + 0.04f * (left / (1f - 0.04f))) * _duration);
                videoPlayer.seekTo(time, !released);
            }
        });
    }

    public void set(MediaController.PhotoEntry entry, VideoPlayer player, Theme.ResourcesProvider resourcesProvider) {
        button.updateColors(resourcesProvider);

        if (entry.width > 0 && entry.height > 0) {
            aspectRatio = Utilities.clamp((float) entry.height / entry.width, 1.39f, 0.85f);
        } else {
            aspectRatio = 1.39f;
        }

        this.videoPlayer = player;
        time = entry.customThumb && entry.customThumbSavedPosition >= 0 ? entry.customThumbSavedPosition : player.getCurrentPosition();
        if (entry.customThumb) {
            player.seekTo(time, false);
        }
        timelineView.setVideo(false, player.getCurrentUri().getPath(), player.getDuration(), player.player.getVolume());
        float left = (float) time / Math.max(1, player.getDuration()) * (1f - 0.04f);
        timelineView.setVideoLeft(left);
        timelineView.setVideoRight(left + 0.04f);
        timelineView.setCoverVideo(0, player.getDuration());
    }

    public void closeGallery() {
        if (gallerySheet != null) {
            gallerySheet.dismiss();
            gallerySheet = null;
        }
    }

    private Utilities.Callback<MediaController.PhotoEntry> onGalleryListener;
    public void setOnGalleryImage(Utilities.Callback<MediaController.PhotoEntry> listener) {
        onGalleryListener = listener;
    }

    public long getTime() {
        return time;
    }

    public void destroy() {
        videoPlayer = null;
        timelineView.setVideo(false, null, 0, 0);
    }

}
