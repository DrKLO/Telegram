/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.SampleSizeBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.util.Matrix;
import com.googlecode.mp4parser.util.Path;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatorListenerAdapterProxy;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoViewerCaptionEnterView;
import org.telegram.ui.Components.PickerBottomLayoutViewer;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayoutPhoto;
import org.telegram.ui.Components.VideoSeekBarView;
import org.telegram.ui.Components.VideoTimelineView;

import java.io.File;
import java.util.List;

@TargetApi(16)
public class VideoEditorActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private TextureView textureView;
    private MediaPlayer videoPlayer;
    private VideoSeekBarView videoSeekBarView;
    private VideoTimelineView videoTimelineView;
    private ImageView muteItem;
    private ImageView captionItem;
    private ImageView compressItem;
    private ImageView playButton;
    private ActionBarMenuItem captionDoneItem;
    private PhotoViewerCaptionEnterView captionEditText;
    private PickerBottomLayoutViewer pickerView;

    private ChatActivity parentChatActivity;
    private MentionsAdapter mentionsAdapter;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private AnimatorSet mentionListAnimation;
    private boolean allowMentions;

    private boolean created;
    private boolean playerPrepared;
    private boolean muteVideo;
    private boolean needCompressVideo;

    private String oldTitle;

    private String videoPath;
    private float lastProgress;
    private boolean needSeek;
    private VideoEditorActivityDelegate delegate;
    private CharSequence currentCaption;

    private final Object sync = new Object();
    private Thread thread;

    private int rotationValue;
    private int originalWidth;
    private int originalHeight;
    private int resultWidth;
    private int resultHeight;
    private int bitrate;
    private int originalBitrate;
    private float videoDuration;
    private long startTime;
    private long endTime;
    private long audioFramesSize;
    private long videoFramesSize;
    private int estimatedSize;
    private long esimatedDuration;
    private long originalSize;

    public interface VideoEditorActivityDelegate {
        void didFinishEditVideo(String videoPath, long startTime, long endTime, int resultWidth, int resultHeight, int rotationValue, int originalWidth, int originalHeight, int bitrate, long estimatedSize, long estimatedDuration, String caption);
    }

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            boolean playerCheck;

            while (true) {
                synchronized (sync) {
                    try {
                        playerCheck = videoPlayer != null && videoPlayer.isPlaying();
                    } catch (Exception e) {
                        playerCheck = false;
                        FileLog.e("tmessages", e);
                    }
                }
                if (!playerCheck) {
                    break;
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (videoPlayer != null && videoPlayer.isPlaying()) {
                            float startTime = videoTimelineView.getLeftProgress() * videoDuration;
                            float endTime = videoTimelineView.getRightProgress() * videoDuration;
                            if (startTime == endTime) {
                                startTime = endTime - 0.01f;
                            }
                            float progress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                            float lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                            progress = videoTimelineView.getLeftProgress() + lrdiff * progress;
                            if (progress > lastProgress) {
                                videoSeekBarView.setProgress(progress);
                                lastProgress = progress;
                            }
                            if (videoPlayer.getCurrentPosition() >= endTime) {
                                try {
                                    videoPlayer.pause();
                                    onPlayComplete();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    }
                });
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            synchronized (sync) {
                thread = null;
            }
        }
    };

    public VideoEditorActivity(Bundle args) {
        super(args);
        videoPath = args.getString("videoPath");
    }

    @Override
    public boolean onFragmentCreate() {
        if (created) {
            return true;
        }
        if (videoPath == null || !processOpenVideo()) {
            return false;
        }
        videoPlayer = new MediaPlayer();
        videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayComplete();
                    }
                });
            }
        });
        videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                playerPrepared = true;
                if (videoTimelineView != null && videoPlayer != null) {
                    videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                }
            }
        });
        try {
            videoPlayer.setDataSource(videoPath);
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        created = true;

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
        }
        if (videoPlayer != null) {
            try {
                videoPlayer.stop();
                videoPlayer.release();
                videoPlayer = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (captionEditText != null) {
            captionEditText.onDestroy();
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        needCompressVideo = preferences.getBoolean("compress_video", true);

        actionBar.setBackgroundColor(Theme.ACTION_BAR_VIDEO_EDIT_COLOR);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
        actionBar.setSubtitleColor(0xffffffff);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
                        closeCaptionEnter(false);
                        return;
                    }
                    finishFragment();
                } else if (id == 1) {
                    closeCaptionEnter(true);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        captionDoneItem = menu.addItemWithWidth(1, R.drawable.ic_done, AndroidUtilities.dp(56));
        captionDoneItem.setVisibility(View.GONE);

        fragmentView = new SizeNotifierFrameLayoutPhoto(context) {

            int lastWidth;

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
                        closeCaptionEnter(false);
                        return false;
                    }
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(widthSize, heightSize);
                heightSize = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight();

                measureChildWithMargins(captionEditText, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int inputFieldHeight = captionEditText.getMeasuredHeight();

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child.getVisibility() == GONE || child == captionEditText) {
                        continue;
                    }
                    if (captionEditText.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(320), heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else if (child == textureView) {
                        int width = widthSize;
                        int height = heightSize - AndroidUtilities.dp(14 + 152);

                        int vwidth = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
                        int vheight = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
                        float wr = (float) width / (float) vwidth;
                        float hr = (float) height / (float) vheight;
                        float ar = (float) vwidth / (float) vheight;

                        if (wr > hr) {
                            width = (int) (height * ar);
                        } else {
                            height = (int) (width / ar);
                        }

                        child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }

                if (lastWidth != widthSize) {
                    videoTimelineView.clearFrames();
                    lastWidth = widthSize;
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();
                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? captionEditText.getEmojiPadding() : 0;

                int heightSize = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight();

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin;
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = (heightSize - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = heightSize - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (child == mentionListView) {
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        if (!captionEditText.isPopupShowing() && !captionEditText.isKeyboardVisible() && captionEditText.getEmojiPadding() == 0) {
                            childTop += AndroidUtilities.dp(400);
                        } else {
                            childTop -= captionEditText.getMeasuredHeight();
                        }
                    } else if (child == captionEditText) {
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        if (!captionEditText.isPopupShowing() && !captionEditText.isKeyboardVisible() && captionEditText.getEmojiPadding() == 0) {
                            childTop += AndroidUtilities.dp(400);
                        }
                    } else if (child == pickerView) {
                        if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
                            childTop += AndroidUtilities.dp(400);
                        }
                    } else if (captionEditText.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow) {
                            childTop = captionEditText.getTop() - child.getMeasuredHeight() + AndroidUtilities.dp(1);
                        } else {
                            childTop = captionEditText.getBottom();
                        }
                    } else if (child == textureView) {
                        childLeft = (r - l - textureView.getMeasuredWidth()) / 2;
                        childTop = AndroidUtilities.dp(14);
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }
        };
        fragmentView.setBackgroundColor(0xff000000);
        SizeNotifierFrameLayoutPhoto frameLayout = (SizeNotifierFrameLayoutPhoto) fragmentView;
        frameLayout.setWithoutWindow(true);

        pickerView = new PickerBottomLayoutViewer(context);
        pickerView.setBackgroundColor(0);
        pickerView.updateSelectedCount(0, false);
        frameLayout.addView(pickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        pickerView.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFragment();
            }
        });
        pickerView.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                synchronized (sync) {
                    if (videoPlayer != null) {
                        try {
                            videoPlayer.stop();
                            videoPlayer.release();
                            videoPlayer = null;
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
                if (delegate != null) {
                    if (compressItem.getVisibility() == View.GONE || compressItem.getVisibility() == View.VISIBLE && !needCompressVideo) {
                        delegate.didFinishEditVideo(videoPath, startTime, endTime, originalWidth, originalHeight, rotationValue, originalWidth, originalHeight, muteVideo ? -1 : originalBitrate, estimatedSize, esimatedDuration, currentCaption != null ? currentCaption.toString() : null);
                    } else {
                        delegate.didFinishEditVideo(videoPath, startTime, endTime, resultWidth, resultHeight, rotationValue, originalWidth, originalHeight, muteVideo ? -1 : bitrate, estimatedSize, esimatedDuration, currentCaption != null ? currentCaption.toString() : null);
                    }
                }
                finishFragment();
            }
        });

        LinearLayout itemsLayout = new LinearLayout(context);
        itemsLayout.setOrientation(LinearLayout.HORIZONTAL);
        pickerView.addView(itemsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        captionItem = new ImageView(context);
        captionItem.setScaleType(ImageView.ScaleType.CENTER);
        captionItem.setImageResource(TextUtils.isEmpty(currentCaption) ? R.drawable.photo_text : R.drawable.photo_text2);
        captionItem.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(captionItem, LayoutHelper.createLinear(56, 48));
        captionItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captionEditText.setFieldText(currentCaption);
                captionDoneItem.setVisibility(View.VISIBLE);
                videoSeekBarView.setVisibility(View.GONE);
                videoTimelineView.setVisibility(View.GONE);
                pickerView.setVisibility(View.GONE);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) captionEditText.getLayoutParams();
                layoutParams.bottomMargin = 0;
                captionEditText.setLayoutParams(layoutParams);
                layoutParams = (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
                layoutParams.bottomMargin = 0;
                mentionListView.setLayoutParams(layoutParams);
                captionEditText.openKeyboard();
                oldTitle = actionBar.getSubtitle();
                actionBar.setTitle(LocaleController.getString("VideoCaption", R.string.VideoCaption));
                actionBar.setSubtitle(null);
            }
        });

        compressItem = new ImageView(context);
        compressItem.setScaleType(ImageView.ScaleType.CENTER);
        compressItem.setImageResource(needCompressVideo ? R.drawable.hd_off : R.drawable.hd_on);
        compressItem.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        compressItem.setVisibility(originalHeight != resultHeight || originalWidth != resultWidth ? View.VISIBLE : View.GONE);
        itemsLayout.addView(compressItem, LayoutHelper.createLinear(56, 48));
        compressItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                needCompressVideo = !needCompressVideo;
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("compress_video", needCompressVideo);
                editor.commit();
                compressItem.setImageResource(needCompressVideo ? R.drawable.hd_off : R.drawable.hd_on);
                updateVideoInfo();
            }
        });
        if (Build.VERSION.SDK_INT < 18) {
            try {
                MediaCodecInfo codecInfo = MediaController.selectCodec(MediaController.MIME_TYPE);
                if (codecInfo == null) {
                    compressItem.setVisibility(View.GONE);
                } else {
                    String name = codecInfo.getName();
                    if (name.equals("OMX.google.h264.encoder") ||
                            name.equals("OMX.ST.VFM.H264Enc") ||
                            name.equals("OMX.Exynos.avc.enc") ||
                            name.equals("OMX.MARVELL.VIDEO.HW.CODA7542ENCODER") ||
                            name.equals("OMX.MARVELL.VIDEO.H264ENCODER") ||
                            name.equals("OMX.k3.video.encoder.avc") || //fix this later
                            name.equals("OMX.TI.DUCATI1.VIDEO.H264E")) { //fix this later
                        compressItem.setVisibility(View.GONE);
                    } else {
                        if (MediaController.selectColorFormat(codecInfo, MediaController.MIME_TYPE) == 0) {
                            compressItem.setVisibility(View.GONE);
                        }
                    }
                }
            } catch (Exception e) {
                compressItem.setVisibility(View.GONE);
                FileLog.e("tmessages", e);
            }
        }

        muteItem = new ImageView(context);
        muteItem.setScaleType(ImageView.ScaleType.CENTER);
        muteItem.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(muteItem, LayoutHelper.createLinear(56, 48));
        muteItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muteVideo = !muteVideo;
                updateMuteButton();
            }
        });

        videoTimelineView = new VideoTimelineView(context);
        videoTimelineView.setVideoPath(videoPath);
        videoTimelineView.setDelegate(new VideoTimelineView.VideoTimelineViewDelegate() {
            @Override
            public void onLeftProgressChanged(float progress) {
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                try {
                    if (videoPlayer.isPlaying()) {
                        videoPlayer.pause();
                        playButton.setImageResource(R.drawable.video_edit_play);
                    }
                    videoPlayer.setOnSeekCompleteListener(null);
                    videoPlayer.seekTo((int) (videoDuration * progress));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                needSeek = true;
                videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
                updateVideoInfo();
            }

            @Override
            public void onRifhtProgressChanged(float progress) {
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                try {
                    if (videoPlayer.isPlaying()) {
                        videoPlayer.pause();
                        playButton.setImageResource(R.drawable.video_edit_play);
                    }
                    videoPlayer.setOnSeekCompleteListener(null);
                    videoPlayer.seekTo((int) (videoDuration * progress));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                needSeek = true;
                videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
                updateVideoInfo();
            }
        });
        frameLayout.addView(videoTimelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 67));

        videoSeekBarView = new VideoSeekBarView(context);
        videoSeekBarView.setDelegate(new VideoSeekBarView.SeekBarDelegate() {
            @Override
            public void onSeekBarDrag(float progress) {
                if (progress < videoTimelineView.getLeftProgress()) {
                    progress = videoTimelineView.getLeftProgress();
                    videoSeekBarView.setProgress(progress);
                } else if (progress > videoTimelineView.getRightProgress()) {
                    progress = videoTimelineView.getRightProgress();
                    videoSeekBarView.setProgress(progress);
                }
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    try {
                        videoPlayer.seekTo((int) (videoDuration * progress));
                        lastProgress = progress;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else {
                    lastProgress = progress;
                    needSeek = true;
                }
            }
        });
        frameLayout.addView(videoSeekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.BOTTOM, 11, 0, 11, 112));

        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (textureView == null || !textureView.isAvailable() || videoPlayer == null) {
                    return;
                }
                try {
                    Surface s = new Surface(textureView.getSurfaceTexture());
                    videoPlayer.setSurface(s);
                    if (playerPrepared) {
                        videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (videoPlayer == null) {
                    return true;
                }
                videoPlayer.setDisplay(null);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        frameLayout.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 14, 0, 140));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageResource(R.drawable.video_edit_play);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                    playButton.setImageResource(R.drawable.video_edit_play);
                } else {
                    try {
                        playButton.setImageDrawable(null);
                        lastProgress = 0;
                        if (needSeek) {
                            videoPlayer.seekTo((int) (videoDuration * videoSeekBarView.getProgress()));
                            needSeek = false;
                        }
                        videoPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                            @Override
                            public void onSeekComplete(MediaPlayer mp) {
                                float startTime = videoTimelineView.getLeftProgress() * videoDuration;
                                float endTime = videoTimelineView.getRightProgress() * videoDuration;
                                if (startTime == endTime) {
                                    startTime = endTime - 0.01f;
                                }
                                lastProgress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                                float lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                                lastProgress = videoTimelineView.getLeftProgress() + lrdiff * lastProgress;
                                videoSeekBarView.setProgress(lastProgress);
                            }
                        });
                        videoPlayer.start();
                        synchronized (sync) {
                            if (thread == null) {
                                thread = new Thread(progressRunnable);
                                thread.start();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }
        });
        frameLayout.addView(playButton, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 0, 0, 0, 70));

        if (captionEditText != null) {
            captionEditText.onDestroy();
        }
        captionEditText = new PhotoViewerCaptionEnterView(context, frameLayout, null);
        captionEditText.setDelegate(new PhotoViewerCaptionEnterView.PhotoViewerCaptionEnterViewDelegate() {
            @Override
            public void onCaptionEnter() {
                closeCaptionEnter(true);
            }

            @Override
            public void onTextChanged(CharSequence text) {
                if (mentionsAdapter != null && captionEditText != null && parentChatActivity != null && text != null) {
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), captionEditText.getCursorPosition(), parentChatActivity.messages);
                }
            }

            @Override
            public void onWindowSizeChanged(int size) {
                int height = AndroidUtilities.dp(36 * Math.min(3, mentionsAdapter.getItemCount()) + (mentionsAdapter.getItemCount() > 3 ? 18 : 0));
                if (size - ActionBar.getCurrentActionBarHeight() * 2 < height) {
                    allowMentions = false;
                    if (mentionListView != null && mentionListView.getVisibility() == View.VISIBLE) {
                        mentionListView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    allowMentions = true;
                    if (mentionListView != null && mentionListView.getVisibility() == View.INVISIBLE) {
                        mentionListView.setVisibility(View.VISIBLE);
                    }
                }
                fragmentView.requestLayout();
            }
        });
        frameLayout.addView(captionEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, -400));
        captionEditText.onCreate();

        mentionListView = new RecyclerListView(context);
        mentionListView.setTag(5);
        mentionLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        mentionLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mentionListView.setLayoutManager(mentionLayoutManager);
        mentionListView.setBackgroundColor(0x7f000000);
        mentionListView.setVisibility(View.GONE);
        mentionListView.setClipToPadding(true);
        mentionListView.setOverScrollMode(ListView.OVER_SCROLL_NEVER);
        frameLayout.addView(mentionListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 110, Gravity.LEFT | Gravity.BOTTOM));

        mentionListView.setAdapter(mentionsAdapter = new MentionsAdapter(context, true, 0, new MentionsAdapter.MentionsAdapterDelegate() {
            @Override
            public void needChangePanelVisibility(boolean show) {
                if (show) {
                    FrameLayout.LayoutParams layoutParams3 = (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
                    int height = 36 * Math.min(3, mentionsAdapter.getItemCount()) + (mentionsAdapter.getItemCount() > 3 ? 18 : 0);
                    layoutParams3.height = AndroidUtilities.dp(height);
                    layoutParams3.topMargin = -AndroidUtilities.dp(height);
                    mentionListView.setLayoutParams(layoutParams3);

                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                        mentionListAnimation = null;
                    }

                    if (mentionListView.getVisibility() == View.VISIBLE) {
                        mentionListView.setAlpha(1.0f);
                        return;
                    } else {
                        mentionLayoutManager.scrollToPositionWithOffset(0, 10000);
                    }
                    if (allowMentions) {
                        mentionListView.setVisibility(View.VISIBLE);
                        mentionListAnimation = new AnimatorSet();
                        mentionListAnimation.playTogether(
                                ObjectAnimator.ofFloat(mentionListView, "alpha", 0.0f, 1.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionListAnimation = null;
                                }
                            }
                        });
                        mentionListAnimation.setDuration(200);
                        mentionListAnimation.start();
                    } else {
                        mentionListView.setAlpha(1.0f);
                        mentionListView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                        mentionListAnimation = null;
                    }

                    if (mentionListView.getVisibility() == View.GONE) {
                        return;
                    }
                    if (allowMentions) {
                        mentionListAnimation = new AnimatorSet();
                        mentionListAnimation.playTogether(
                                ObjectAnimator.ofFloat(mentionListView, "alpha", 0.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionListView.setVisibility(View.GONE);
                                    mentionListAnimation = null;
                                }
                            }
                        });
                        mentionListAnimation.setDuration(200);
                        mentionListAnimation.start();
                    } else {
                        mentionListView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onContextSearch(boolean searching) {

            }

            @Override
            public void onContextClick(TLRPC.BotInlineResult result) {

            }
        }));
        mentionsAdapter.setAllowNewMentions(false);

        mentionListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Object object = mentionsAdapter.getItem(position);
                int start = mentionsAdapter.getResultStartPosition();
                int len = mentionsAdapter.getResultLength();
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user != null) {
                        captionEditText.replaceWithText(start, len, "@" + user.username + " ");
                    }
                } else if (object instanceof String) {
                    captionEditText.replaceWithText(start, len, object + " ");
                }
            }
        });

        mentionListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (getParentActivity() == null) {
                    return false;
                }
                Object object = mentionsAdapter.getItem(position);
                if (object instanceof String) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                    builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mentionsAdapter.clearRecentHashtags();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                    return true;
                }
                return false;
            }
        });

        updateVideoInfo();
        updateMuteButton();

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (captionDoneItem.getVisibility() != View.GONE) {
            closeCaptionEnter(true);
        }
    }

    public void setParentChatActivity(ChatActivity chatActivity) {
        parentChatActivity = chatActivity;
    }

    private void closeCaptionEnter(boolean apply) {
        if (apply) {
            currentCaption = captionEditText.getFieldCharSequence();
        }
        actionBar.setSubtitle(oldTitle);
        captionDoneItem.setVisibility(View.GONE);
        pickerView.setVisibility(View.VISIBLE);
        videoSeekBarView.setVisibility(View.VISIBLE);
        videoTimelineView.setVisibility(View.VISIBLE);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) captionEditText.getLayoutParams();
        layoutParams.bottomMargin = -AndroidUtilities.dp(400);
        captionEditText.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
        layoutParams.bottomMargin = -AndroidUtilities.dp(400);
        mentionListView.setLayoutParams(layoutParams);

        actionBar.setTitle(muteVideo ? LocaleController.getString("AttachGif", R.string.AttachGif) : LocaleController.getString("AttachVideo", R.string.AttachVideo));
        captionItem.setImageResource(TextUtils.isEmpty(currentCaption) ? R.drawable.photo_text : R.drawable.photo_text2);
        if (captionEditText.isPopupShowing()) {
            captionEditText.hidePopup();
        } else {
            captionEditText.closeKeyboard();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        }
    }

    public void updateMuteButton() {
        if (videoPlayer != null) {
            float volume = muteVideo ? 0.0f : 1.0f;
            if (videoPlayer != null) {
                videoPlayer.setVolume(volume, volume);
            }
        }
        if (muteVideo) {
            actionBar.setTitle(LocaleController.getString("AttachGif", R.string.AttachGif));
            muteItem.setImageResource(R.drawable.volume_off);
            if (captionItem.getVisibility() == View.VISIBLE) {
                needCompressVideo = true;
                compressItem.setImageResource(R.drawable.hd_off);
                compressItem.setClickable(false);
                compressItem.setAlpha(0.8f);
                compressItem.setEnabled(false);
            }
        } else {
            actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
            muteItem.setImageResource(R.drawable.volume_on);
            if (captionItem.getVisibility() == View.VISIBLE) {
                compressItem.setClickable(true);
                compressItem.setAlpha(1.0f);
                compressItem.setEnabled(true);
            }
        }
    }

    private void onPlayComplete() {
        if (playButton != null) {
            playButton.setImageResource(R.drawable.video_edit_play);
        }
        if (videoSeekBarView != null && videoTimelineView != null) {
            videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
        }
        try {
            if (videoPlayer != null) {
                if (videoTimelineView != null) {
                    videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void updateVideoInfo() {
        if (actionBar == null) {
            return;
        }
        esimatedDuration = (long) Math.ceil((videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * videoDuration);

        int width;
        int height;

        if (compressItem.getVisibility() == View.GONE || compressItem.getVisibility() == View.VISIBLE && !needCompressVideo) {
            width = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
            height = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
            estimatedSize = (int) (originalSize * ((float) esimatedDuration / videoDuration));
        } else {
            width = rotationValue == 90 || rotationValue == 270 ? resultHeight : resultWidth;
            height = rotationValue == 90 || rotationValue == 270 ? resultWidth : resultHeight;

            estimatedSize = (int) ((audioFramesSize + videoFramesSize) * ((float) esimatedDuration / videoDuration));
            estimatedSize += estimatedSize / (32 * 1024) * 16;
        }

        if (videoTimelineView.getLeftProgress() == 0) {
            startTime = -1;
        } else {
            startTime = (long) (videoTimelineView.getLeftProgress() * videoDuration) * 1000;
        }
        if (videoTimelineView.getRightProgress() == 1) {
            endTime = -1;
        } else {
            endTime = (long) (videoTimelineView.getRightProgress() * videoDuration) * 1000;
        }

        String videoDimension = String.format("%dx%d", width, height);
        int minutes = (int) (esimatedDuration / 1000 / 60);
        int seconds = (int) Math.ceil(esimatedDuration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, ~%s", minutes, seconds, AndroidUtilities.formatFileSize(estimatedSize));
        actionBar.setSubtitle(String.format("%s, %s", videoDimension, videoTimeSize));
    }

    public void setDelegate(VideoEditorActivityDelegate videoEditorActivityDelegate) {
        delegate = videoEditorActivityDelegate;
    }

    private boolean processOpenVideo() {
        try {
            File file = new File(videoPath);
            originalSize = file.length();

            IsoFile isoFile = new IsoFile(videoPath);
            List<Box> boxes = Path.getPaths(isoFile, "/moov/trak/");
            TrackHeaderBox trackHeaderBox = null;
            boolean isAvc = true;
            boolean isMp4A = true;

            Box boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/mp4a/");
            if (boxTest == null) {
                isMp4A = false;
            }

            if (!isMp4A) {
                return false;
            }

            boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/avc1/");
            if (boxTest == null) {
                isAvc = false;
            }

            for (Box box : boxes) {
                TrackBox trackBox = (TrackBox) box;
                long sampleSizes = 0;
                long trackBitrate = 0;
                try {
                    MediaBox mediaBox = trackBox.getMediaBox();
                    MediaHeaderBox mediaHeaderBox = mediaBox.getMediaHeaderBox();
                    SampleSizeBox sampleSizeBox = mediaBox.getMediaInformationBox().getSampleTableBox().getSampleSizeBox();
                    for (long size : sampleSizeBox.getSampleSizes()) {
                        sampleSizes += size;
                    }
                    videoDuration = (float) mediaHeaderBox.getDuration() / (float) mediaHeaderBox.getTimescale();
                    trackBitrate = (int) (sampleSizes * 8 / videoDuration);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                TrackHeaderBox headerBox = trackBox.getTrackHeaderBox();
                if (headerBox.getWidth() != 0 && headerBox.getHeight() != 0) {
                    trackHeaderBox = headerBox;
                    originalBitrate = bitrate = (int) (trackBitrate / 100000 * 100000);
                    if (bitrate > 900000) {
                        bitrate = 900000;
                    }
                    videoFramesSize += sampleSizes;
                } else {
                    audioFramesSize += sampleSizes;
                }
            }
            if (trackHeaderBox == null) {
                return false;
            }

            Matrix matrix = trackHeaderBox.getMatrix();
            if (matrix.equals(Matrix.ROTATE_90)) {
                rotationValue = 90;
            } else if (matrix.equals(Matrix.ROTATE_180)) {
                rotationValue = 180;
            } else if (matrix.equals(Matrix.ROTATE_270)) {
                rotationValue = 270;
            }
            resultWidth = originalWidth = (int) trackHeaderBox.getWidth();
            resultHeight = originalHeight = (int) trackHeaderBox.getHeight();

            if (resultWidth > 640 || resultHeight > 640) {
                float scale = resultWidth > resultHeight ? 640.0f / resultWidth : 640.0f / resultHeight;
                resultWidth *= scale;
                resultHeight *= scale;
                if (bitrate != 0) {
                    bitrate *= Math.max(0.5f, scale);
                    videoFramesSize = (long) (bitrate / 8 * videoDuration);
                }
            }

            if (!isAvc && (resultWidth == originalWidth || resultHeight == originalHeight)) {
                return false;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }

        videoDuration *= 1000;
        updateVideoInfo();
        return true;
    }
}
