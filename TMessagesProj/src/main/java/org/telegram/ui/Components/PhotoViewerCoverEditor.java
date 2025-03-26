package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.GallerySheet;
import org.telegram.ui.Stories.recorder.TimelineView;

public class PhotoViewerCoverEditor extends FrameLayout {

    public ActionBar actionBar;

    public TimelineView timelineView;
    public ButtonWithCounterView button;
    public EditCoverButton openGalleryButton;

    private VideoPlayer videoPlayer;
    private long time = -1;
    private float aspectRatio = 1.39f;

    private GallerySheet gallerySheet;

    public PhotoViewerCoverEditor(Context context, Theme.ResourcesProvider resourcesProvider, PhotoViewer photoViewer, BlurringShader.BlurManager blurManager) {
        super(context);

        actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.EditorSetCoverTitle));
        actionBar.setItemsColor(0xFFFFFFFF, false);
        actionBar.setItemsBackgroundColor(0x22ffffff, false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && close != null) {
                    AndroidUtilities.runOnUIThread(close);
                }
            }
        });
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        timelineView = new TimelineView(context, null, null, resourcesProvider, blurManager);
        timelineView.setCover();
        addView(timelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TimelineView.heightDp(), Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 16 + 48 + 10));

        button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.EditorSetCoverSave), false);
        addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 10, 10, 10, 10));

        openGalleryButton = new EditCoverButton(context, photoViewer, getString(R.string.EditorSetCoverGallery), true);
        openGalleryButton.setOnClickListener(v -> {
            if (gallerySheet == null) {
                gallerySheet = new GallerySheet(context, resourcesProvider, getString(R.string.VideoChooseCover), true, aspectRatio);
                gallerySheet.setOnDismissListener(() -> {
                    gallerySheet = null;
                });
                gallerySheet.setOnGalleryImage(onGalleryListener);
            }
            gallerySheet.show();
        });
        addView(openGalleryButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 60, 0, 60, 16 + 48 + 10 + 60));

        timelineView.setDelegate(new TimelineView.TimelineDelegate() {
            private Runnable betterSeek = () -> videoPlayer.seekTo(time, false);
            @Override
            public void onVideoLeftChange(boolean released, float left) {
                if (videoPlayer == null) return;
                final long duration = videoPlayer.getDuration();
                final float regionLength = 2.8f / Math.max(60, duration);
                time = (long) ((left + regionLength * (left / (1f - regionLength))) * duration);
                videoPlayer.seekTo(time, !released);
                if (!released) {
                    AndroidUtilities.cancelRunOnUIThread(betterSeek);
                    AndroidUtilities.runOnUIThread(betterSeek, 120);
                }
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
        if (entry.coverSavedPosition >= 0) {
            player.seekTo(time = entry.coverSavedPosition, false);
        } else {
            time = player.getCurrentPosition();
        }
        timelineView.setVideo(false, player.getCurrentUri().getPath(), player.getDuration(), player.player.getVolume());
        final long duration = player.getDuration();
        final float regionLength = 2.8f / Math.max(60, duration);
        float left = (float) time / Math.max(1, player.getDuration()) * (1f - regionLength);
        timelineView.setVideoLeft(left);
        timelineView.setVideoRight(left + regionLength);
        timelineView.setCoverVideo(0, duration);
        timelineView.normalizeScrollByVideo();
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

    public Runnable close;
    public void setOnClose(Runnable listener) {
        close = listener;
    }

    public long getTime() {
        return time;
    }

    public void destroy() {
        videoPlayer = null;
        timelineView.setVideo(false, null, 0, 0);
    }

}
