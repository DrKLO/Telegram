/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

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
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoViewerCaptionEnterView;
import org.telegram.ui.Components.PickerBottomLayoutViewer;
import org.telegram.ui.Components.RadialProgressView;
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
    private RadialProgressView progressView;
    private PhotoViewerCaptionEnterView captionEditText;
    private PickerBottomLayoutViewer pickerView;

    private MessageObject videoPreviewMessageObject;
    private boolean tryStartRequestPreviewOnFinish;
    private boolean loadInitialVideo;
    private boolean inPreview;
    private int previewViewEnd;
    private boolean requestingPreview;

    private QualityChooseView qualityChooseView;
    private PickerBottomLayoutViewer qualityPicker;

    private ChatActivity parentChatActivity;
    private MentionsAdapter mentionsAdapter;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private AnimatorSet mentionListAnimation;
    private boolean firstCaptionLayout;
    private boolean allowMentions;

    private boolean created;
    private boolean playerPrepared;
    private boolean muteVideo;

    private int selectedCompression;
    private int compressionsCount = -1;
    private int previousCompression;

    private String currentSubtitle;

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
                        FileLog.e(e);
                    }
                }
                if (!playerCheck) {
                    break;
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (videoPlayer != null && videoPlayer.isPlaying()) {
                            float startTime;
                            float endTime;
                            float lrdiff;
                            if (inPreview) {
                                startTime = 0;
                                endTime = previewViewEnd;
                                lrdiff = 1.0f;
                            } else {
                                startTime = videoTimelineView.getLeftProgress() * videoDuration;
                                endTime = videoTimelineView.getRightProgress() * videoDuration;
                                lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                            }
                            if (startTime == endTime) {
                                startTime = endTime - 0.01f;
                            }
                            float progress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                            if (!inPreview) {
                                progress = videoTimelineView.getLeftProgress() + lrdiff * progress;
                            }
                            if (progress > lastProgress) {
                                videoSeekBarView.setProgress(progress);
                                lastProgress = progress;
                            }
                            int position = videoPlayer.getCurrentPosition();
                            if (videoPlayer.getCurrentPosition() >= endTime) {
                                try {
                                    videoPlayer.pause();
                                    onPlayComplete();
                                    try {
                                        getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }
                    }
                });
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            synchronized (sync) {
                thread = null;
            }
        }
    };

    private class QualityChooseView extends View {

        private Paint paint;
        private TextPaint textPaint;

        private int circleSize;
        private int gapSize;
        private int sideSide;
        private int lineSize;

        private boolean moving;
        private boolean startMoving;
        private float startX;

        private int startMovingQuality;

        public QualityChooseView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(12));
            textPaint.setColor(0xffcdcdcd);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                for (int a = 0; a < compressionsCount; a++) {
                    int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                    if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                        startMoving = a == selectedCompression;
                        startX = x;
                        startMovingQuality = selectedCompression;
                        break;
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (startMoving) {
                    if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.5f, true)) {
                        moving = true;
                        startMoving = false;
                    }
                } else if (moving) {
                    for (int a = 0; a < compressionsCount; a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        int diff = lineSize / 2 + circleSize / 2 + gapSize;
                        if (x > cx - diff && x < cx + diff) {
                            if (selectedCompression != a) {
                                selectedCompression = a;
                                didChangedCompressionLevel(false);
                                invalidate();
                            }
                            break;
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!moving) {
                    for (int a = 0; a < compressionsCount; a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                            if (selectedCompression != a) {
                                selectedCompression = a;
                                didChangedCompressionLevel(true);
                                invalidate();
                            }
                            break;
                        }
                    }
                } else {
                    if (selectedCompression != startMovingQuality) {
                        requestVideoPreview(1);
                    }
                }
                startMoving = false;
                moving = false;
            }
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            circleSize = AndroidUtilities.dp(12);
            gapSize = AndroidUtilities.dp(2);
            sideSide = AndroidUtilities.dp(18);
            lineSize = (getMeasuredWidth() - circleSize * compressionsCount - gapSize * 8 - sideSide * 2) / (compressionsCount - 1);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(6);
            for (int a = 0; a < compressionsCount; a++) {
                int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                if (a <= selectedCompression) {
                    paint.setColor(0xff53aeef);
                } else {
                    paint.setColor(0xff222222);
                }
                String text;
                if (a == compressionsCount - 1) {
                    text = originalHeight + "p";
                } else if (a == 0) {
                    text = "240p";
                } else if (a == 1) {
                    text = "360p";
                } else if (a == 2) {
                    text = "480p";
                } else {
                    text = "720p";
                }
                float width = textPaint.measureText(text);
                canvas.drawCircle(cx, cy, a == selectedCompression ? AndroidUtilities.dp(8) : circleSize / 2, paint);
                canvas.drawText(text, cx - width / 2, cy - AndroidUtilities.dp(16), textPaint);
                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    canvas.drawRect(x, cy - AndroidUtilities.dp(1), x + lineSize, cy + AndroidUtilities.dp(2), paint);
                }
            }
        }
    }

    public VideoEditorActivity(Bundle args) {
        super(args);
        videoPath = args.getString("videoPath");
    }

    private void destroyPlayer() {
        if (videoPlayer != null) {
            try {
                if (videoPlayer != null) {
                    videoPlayer.stop();
                }
            } catch (Exception ignore) {

            }
            try {
                if (videoPlayer != null) {
                    videoPlayer.release();
                }
            } catch (Exception ignore) {

            }
            videoPlayer = null;
        }
    }

    private boolean reinitPlayer(String path) {
        destroyPlayer();
        if (playButton != null) {
            playButton.setImageResource(R.drawable.video_edit_play);
        }
        lastProgress = 0;
        videoPlayer = new MediaPlayer();
        videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                playerPrepared = true;
                previewViewEnd = videoPlayer.getDuration();
                if (videoTimelineView != null && videoPlayer != null) {
                    if (inPreview) {
                        videoPlayer.seekTo(0);
                    } else {
                        videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                    }
                }
            }
        });
        try {
            videoPlayer.setDataSource(path);
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        float volume = muteVideo ? 0.0f : 1.0f;
        if (videoPlayer != null) {
            videoPlayer.setVolume(volume, volume);
        }
        inPreview = !path.equals(videoPath);
        if (textureView != null) {
            try {
                Surface s = new Surface(textureView.getSurfaceTexture());
                videoPlayer.setSurface(s);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        if (created) {
            return true;
        }
        if (videoPath == null || !processOpenVideo()) {
            return false;
        }
        if (!reinitPlayer(videoPath)) {
            return false;
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FilePreparingFailed);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileNewChunkAvailable);

        created = true;

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        try {
            getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
        }
        if (videoPlayer != null) {
            try {
                videoPlayer.stop();
                videoPlayer.release();
                videoPlayer = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (captionEditText != null) {
            captionEditText.onDestroy();
        }
        requestVideoPreview(0);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FilePreparingFailed);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileNewChunkAvailable);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.ACTION_BAR_VIDEO_EDIT_COLOR);
        actionBar.setTitleColor(0xffffffff);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
        actionBar.setSubtitleColor(0xffffffff);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (pickerView.getVisibility() != View.VISIBLE) {
                        closeCaptionEnter(false);
                        return;
                    }
                    finishFragment();
                } else if (id == 1) {
                    closeCaptionEnter(true);
                }
            }
        });

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
                if (!AndroidUtilities.isTablet()) {
                    heightSize = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight();
                } else {
                    heightSize = AndroidUtilities.dp(424);
                }

                measureChildWithMargins(captionEditText, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int inputFieldHeight = captionEditText.getMeasuredHeight();

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child.getVisibility() == GONE || child == captionEditText) {
                        continue;
                    }
                    if (captionEditText.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(320), MeasureSpec.getSize(heightMeasureSpec) - inputFieldHeight), MeasureSpec.EXACTLY));
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
                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? captionEditText.getEmojiPadding() : 0;

                int heightSize;
                if (!AndroidUtilities.isTablet()) {
                    heightSize = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight();
                } else {
                    heightSize = AndroidUtilities.dp(424);
                }

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
                        if (pickerView.getVisibility() == VISIBLE || firstCaptionLayout && !captionEditText.isPopupShowing() && !captionEditText.isKeyboardVisible() && captionEditText.getEmojiPadding() == 0) {
                            childTop += AndroidUtilities.dp(400);
                        } else {
                            childTop -= captionEditText.getMeasuredHeight();
                        }
                    } else if (child == captionEditText) {
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        if (pickerView.getVisibility() == VISIBLE || firstCaptionLayout && !captionEditText.isPopupShowing() && !captionEditText.isKeyboardVisible() && captionEditText.getEmojiPadding() == 0) {
                            childTop += AndroidUtilities.dp(400);
                        } else {
                            firstCaptionLayout = false;
                        }
                    } else if (captionEditText.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            childTop = captionEditText.getTop() - child.getMeasuredHeight() + AndroidUtilities.dp(1);
                        } else {
                            childTop = captionEditText.getBottom();
                        }
                    } else if (child == textureView) {
                        childLeft = (r - l - textureView.getMeasuredWidth()) / 2;
                        if (AndroidUtilities.isTablet()) {
                            childTop = (heightSize - AndroidUtilities.dp(14 + 152) - textureView.getMeasuredHeight()) / 2 + AndroidUtilities.dp(14);
                        } else {
                            childTop = (heightSize - AndroidUtilities.dp(14 + 152) - textureView.getMeasuredHeight()) / 2 + AndroidUtilities.dp(14);
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }
        };
        fragmentView.setBackgroundColor(0xff000000);
        SizeNotifierFrameLayoutPhoto frameLayout = (SizeNotifierFrameLayoutPhoto) fragmentView;
        frameLayout.setWithoutWindow(true);
        fragmentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

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
                            FileLog.e(e);
                        }
                    }
                }
                if (delegate != null) {
                    if (muteVideo) {

                    }
                    if (compressItem.getVisibility() == View.GONE || compressItem.getVisibility() == View.VISIBLE && selectedCompression == compressionsCount - 1) {
                        delegate.didFinishEditVideo(videoPath, startTime, endTime, originalWidth, originalHeight, rotationValue, originalWidth, originalHeight, muteVideo ? -1 : originalBitrate, estimatedSize, esimatedDuration, currentCaption != null ? currentCaption.toString() : null);
                    } else {
                        if (muteVideo) {
                            selectedCompression = 1;
                            updateWidthHeightBitrateForCompression();
                        }
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
        captionItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(captionItem, LayoutHelper.createLinear(56, 48));
        captionItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captionEditText.setFieldText(currentCaption);
                pickerView.setVisibility(View.GONE);
                firstCaptionLayout = true;
                if (!AndroidUtilities.isTablet()) {
                    videoSeekBarView.setVisibility(View.GONE);
                    videoTimelineView.setVisibility(View.GONE);
                }
                captionEditText.openKeyboard();
                actionBar.setTitle(muteVideo ? LocaleController.getString("GifCaption", R.string.GifCaption) : LocaleController.getString("VideoCaption", R.string.VideoCaption));
                actionBar.setSubtitle(null);
            }
        });

        compressItem = new ImageView(context);
        compressItem.setScaleType(ImageView.ScaleType.CENTER);
        compressItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        compressItem.setVisibility(compressionsCount > 1 ? View.VISIBLE : View.GONE);
        itemsLayout.addView(compressItem, LayoutHelper.createLinear(56, 48));
        compressItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityView(true);
                requestVideoPreview(1);
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
                FileLog.e(e);
            }
        }

        muteItem = new ImageView(context);
        muteItem.setScaleType(ImageView.ScaleType.CENTER);
        muteItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
//        muteItem.setVisibility(videoDuration >= 30000 ? View.GONE : View.VISIBLE);
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
                        try {
                            getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    videoPlayer.setOnSeekCompleteListener(null);
                    videoPlayer.seekTo((int) (videoDuration * progress));
                } catch (Exception e) {
                    FileLog.e(e);
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
                        try {
                            getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    videoPlayer.setOnSeekCompleteListener(null);
                    videoPlayer.seekTo((int) (videoDuration * progress));
                } catch (Exception e) {
                    FileLog.e(e);
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
                try {
                    videoPlayer.seekTo((int) (videoDuration * progress));
                    lastProgress = progress;
                } catch (Exception e) {
                    FileLog.e(e);
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
                        if (inPreview) {
                            videoPlayer.seekTo(0);
                        } else {
                            videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
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

        progressView = new RadialProgressView(context);
        progressView.setProgressColor(0xffffffff);
        progressView.setBackgroundResource(R.drawable.circle_big);
        progressView.setVisibility(View.INVISIBLE);
        frameLayout.addView(progressView, LayoutHelper.createFrame(54, 54, Gravity.CENTER, 0, 0, 0, 70));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageResource(R.drawable.video_edit_play);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoPlayer == null || !playerPrepared || requestingPreview || loadInitialVideo) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                    playButton.setImageResource(R.drawable.video_edit_play);
                    try {
                        getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
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
                                if (inPreview) {
                                    float startTime = 0;
                                    float endTime = 1.0f;
                                    lastProgress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                                } else {
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
                            }
                        });
                        videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                try {
                                    getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                onPlayComplete();
                            }
                        });
                        videoPlayer.start();
                        try {
                            getParentActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        synchronized (sync) {
                            if (thread == null) {
                                thread = new Thread(progressRunnable);
                                thread.start();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        });
        frameLayout.addView(playButton, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 0, 0, 0, 70));

        if (captionEditText != null) {
            captionEditText.onDestroy();
        }
        captionEditText = new PhotoViewerCaptionEnterView(context, frameLayout, null);
        captionEditText.setForceFloatingEmoji(AndroidUtilities.isTablet());
        captionEditText.setDelegate(new PhotoViewerCaptionEnterView.PhotoViewerCaptionEnterViewDelegate() {

            private int previousSize;
            private int[] location = new int[2];
            private int previousY;

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
                fragmentView.getLocationInWindow(location);
                if (previousSize != size || previousY != location[1]) {
                    fragmentView.requestLayout();
                    previousSize = size;
                    previousY = location[1];
                }
            }
        });
        frameLayout.addView(captionEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));
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
        mentionListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
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
                        mentionListAnimation.addListener(new AnimatorListenerAdapter() {
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
                        mentionListAnimation.addListener(new AnimatorListenerAdapter() {
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

        if (compressionsCount > 1) {
            qualityPicker = new PickerBottomLayoutViewer(context);
            qualityPicker.setBackgroundColor(0);
            qualityPicker.updateSelectedCount(0, false);
            qualityPicker.setTranslationY(AndroidUtilities.dp(120));
            qualityPicker.doneButton.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
            frameLayout.addView(qualityPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
            qualityPicker.cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectedCompression = previousCompression;
                    didChangedCompressionLevel(false);
                    showQualityView(false);
                    requestVideoPreview(2);
                }
            });
            qualityPicker.doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showQualityView(false);
                    requestVideoPreview(2);
                }
            });

            qualityChooseView = new QualityChooseView(context);
            qualityChooseView.setTranslationY(AndroidUtilities.dp(120));
            qualityChooseView.setVisibility(View.INVISIBLE);
            frameLayout.addView(qualityChooseView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 90, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 44));
        }

        updateVideoInfo();
        updateMuteButton();

        return fragmentView;
    }

    private void didChangedCompressionLevel(boolean request) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("compress_video2", selectedCompression);
        editor.commit();
        updateWidthHeightBitrateForCompression();
        updateVideoInfo();
        if (request) {
            requestVideoPreview(1);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (pickerView.getVisibility() == View.GONE) {
            closeCaptionEnter(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (textureView != null) {
            try {
                if (playerPrepared && !videoPlayer.isPlaying()) {
                    videoPlayer.seekTo((int) (videoSeekBarView.getProgress() * videoDuration));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void showQualityView(final boolean show) {
        if (show) {
            previousCompression = selectedCompression;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        if (show) {
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(152)),
                    ObjectAnimator.ofFloat(videoTimelineView, "translationY", 0, AndroidUtilities.dp(152)),
                    ObjectAnimator.ofFloat(videoSeekBarView, "translationY", 0, AndroidUtilities.dp(152)));
        } else {
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(qualityChooseView, "translationY", 0, AndroidUtilities.dp(120)),
                    ObjectAnimator.ofFloat(qualityPicker, "translationY", 0, AndroidUtilities.dp(120)));
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AnimatorSet animatorSet = new AnimatorSet();
                if (show) {
                    qualityChooseView.setVisibility(View.VISIBLE);
                    qualityPicker.setVisibility(View.VISIBLE);
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(qualityChooseView, "translationY", 0),
                            ObjectAnimator.ofFloat(qualityPicker, "translationY", 0));
                } else {
                    qualityChooseView.setVisibility(View.INVISIBLE);
                    qualityPicker.setVisibility(View.INVISIBLE);
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(pickerView, "translationY", 0),
                            ObjectAnimator.ofFloat(videoTimelineView, "translationY", 0),
                            ObjectAnimator.ofFloat(videoSeekBarView, "translationY", 0));
                }
                animatorSet.setDuration(200);
                animatorSet.setInterpolator(new AccelerateInterpolator());
                animatorSet.start();
            }
        });
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    public void setParentChatActivity(ChatActivity chatActivity) {
        parentChatActivity = chatActivity;
    }

    private void closeCaptionEnter(boolean apply) {
        if (apply) {
            currentCaption = captionEditText.getFieldCharSequence();
        }
        pickerView.setVisibility(View.VISIBLE);
        if (!AndroidUtilities.isTablet()) {
            videoSeekBarView.setVisibility(View.VISIBLE);
            videoTimelineView.setVisibility(View.VISIBLE);
        }

        actionBar.setTitle(muteVideo ? LocaleController.getString("AttachGif", R.string.AttachGif) : LocaleController.getString("AttachVideo", R.string.AttachVideo));
        actionBar.setSubtitle(muteVideo ? null : currentSubtitle);
        captionItem.setImageResource(TextUtils.isEmpty(currentCaption) ? R.drawable.photo_text : R.drawable.photo_text2);
        if (captionEditText.isPopupShowing()) {
            captionEditText.hidePopup();
        }
        captionEditText.closeKeyboard();
    }

    private void requestVideoPreview(int request) {
        if (videoPreviewMessageObject != null) {
            MediaController.getInstance().cancelVideoConvert(videoPreviewMessageObject);
        }
        boolean wasRequestingPreview = requestingPreview && !tryStartRequestPreviewOnFinish;
        requestingPreview = false;
        loadInitialVideo = false;
        progressView.setVisibility(View.INVISIBLE);
        if (request == 1) {
            if (selectedCompression == compressionsCount - 1) {
                tryStartRequestPreviewOnFinish = false;
                if (!wasRequestingPreview) {
                    reinitPlayer(videoPath);
                } else {
                    playButton.setImageDrawable(null);
                    progressView.setVisibility(View.VISIBLE);
                    loadInitialVideo = true;
                }
            } else {
                destroyPlayer();
                if (videoPreviewMessageObject == null) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = 0;
                    message.message = "";
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.action = new TLRPC.TL_messageActionEmpty();
                    videoPreviewMessageObject = new MessageObject(message, null, false);
                    videoPreviewMessageObject.messageOwner.attachPath = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), "video_preview.mp4").getAbsolutePath();
                    videoPreviewMessageObject.videoEditedInfo = new VideoEditedInfo();
                    videoPreviewMessageObject.videoEditedInfo.rotationValue = rotationValue;
                    videoPreviewMessageObject.videoEditedInfo.originalWidth = originalWidth;
                    videoPreviewMessageObject.videoEditedInfo.originalHeight = originalHeight;
                    videoPreviewMessageObject.videoEditedInfo.originalPath = videoPath;
                }
                long start = videoPreviewMessageObject.videoEditedInfo.startTime = startTime;
                long end = videoPreviewMessageObject.videoEditedInfo.endTime = endTime;
                if (start == -1) {
                    start = 0;
                }
                if (end == -1) {
                    end = (long) (videoDuration * 1000);
                }
                if (end - start > 5000000) {
                    videoPreviewMessageObject.videoEditedInfo.endTime = start + 5000000;
                }
                videoPreviewMessageObject.videoEditedInfo.bitrate = bitrate;
                videoPreviewMessageObject.videoEditedInfo.resultWidth = resultWidth;
                videoPreviewMessageObject.videoEditedInfo.resultHeight = resultHeight;
                if (!MediaController.getInstance().scheduleVideoConvert(videoPreviewMessageObject, true)) {
                    tryStartRequestPreviewOnFinish = true;
                }
                if (videoPlayer == null) {
                    requestingPreview = true;
                    playButton.setImageDrawable(null);
                    progressView.setVisibility(View.VISIBLE);
                }
            }
        } else {
            tryStartRequestPreviewOnFinish = false;
            if (request == 2) {
                reinitPlayer(videoPath);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.FilePreparingFailed) {
            MessageObject messageObject = (MessageObject) args[0];
            if (loadInitialVideo) {
                loadInitialVideo = false;
                progressView.setVisibility(View.INVISIBLE);
                reinitPlayer(videoPath);
            } else if (tryStartRequestPreviewOnFinish) {
                destroyPlayer();
                tryStartRequestPreviewOnFinish = !MediaController.getInstance().scheduleVideoConvert(videoPreviewMessageObject, true);
            } else if (messageObject == videoPreviewMessageObject) {
                requestingPreview = false;
                progressView.setVisibility(View.INVISIBLE);
                playButton.setImageResource(R.drawable.video_edit_play);
            }
        } else if (id == NotificationCenter.FileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject == videoPreviewMessageObject) {
                String finalPath = (String) args[1];
                long finalSize = (Long) args[2];
                if (finalSize != 0) {
                    requestingPreview = false;
                    progressView.setVisibility(View.INVISIBLE);
                    reinitPlayer(finalPath);
                }
            }
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
            actionBar.setSubtitle(null);
            muteItem.setImageResource(R.drawable.volume_off);
            if (compressItem.getVisibility() == View.VISIBLE) {
                compressItem.setClickable(false);
                compressItem.setAlpha(0.5f);
                compressItem.setEnabled(false);
            }
            videoTimelineView.setMaxProgressDiff(30000.0f / videoDuration);
        } else {
            actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
            actionBar.setSubtitle(currentSubtitle);
            muteItem.setImageResource(R.drawable.volume_on);
            if (compressItem.getVisibility() == View.VISIBLE) {
                compressItem.setClickable(true);
                compressItem.setAlpha(1.0f);
                compressItem.setEnabled(true);
            }
            videoTimelineView.setMaxProgressDiff(1.0f);
        }
    }

    private void onPlayComplete() {
        if (playButton != null) {
            playButton.setImageResource(R.drawable.video_edit_play);
        }
        if (videoSeekBarView != null && videoTimelineView != null) {
            if (inPreview) {
                videoSeekBarView.setProgress(0);
            } else {
                videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
            }
        }
        try {
            if (videoPlayer != null) {
                if (videoTimelineView != null) {
                    if (inPreview) {
                        videoPlayer.seekTo(0);
                    } else {
                        videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void updateVideoInfo() {
        if (actionBar == null) {
            return;
        }

        if (selectedCompression == 0) {
            compressItem.setImageResource(R.drawable.video_240);
        } else if (selectedCompression == 1) {
            compressItem.setImageResource(R.drawable.video_360);
        } else if (selectedCompression == 2) {
            compressItem.setImageResource(R.drawable.video_480);
        } else if (selectedCompression == 3) {
            compressItem.setImageResource(R.drawable.video_720);
        } else if (selectedCompression == 4) {
            compressItem.setImageResource(R.drawable.video_1080);
        }

        esimatedDuration = (long) Math.ceil((videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * videoDuration);

        int width;
        int height;

        if (compressItem.getVisibility() == View.GONE || compressItem.getVisibility() == View.VISIBLE && selectedCompression == compressionsCount - 1) {
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
        currentSubtitle = String.format("%s, %s", videoDimension, videoTimeSize);
        actionBar.setSubtitle(muteVideo ? null : currentSubtitle);
    }

    public void setDelegate(VideoEditorActivityDelegate videoEditorActivityDelegate) {
        delegate = videoEditorActivityDelegate;
    }

    private void updateWidthHeightBitrateForCompression() {
        if (compressionsCount == -1) {
            if (originalWidth > 1280 || originalHeight > 1280) {
                compressionsCount = 5;
            } else if (originalWidth > 848 || originalHeight > 848) {
                compressionsCount = 4;
            } else if (originalWidth > 640 || originalHeight > 640) {
                compressionsCount = 3;
            } else if (originalWidth > 480 || originalHeight > 480) {
                compressionsCount = 2;
            } else {
                compressionsCount = 1;
            }
        }
        if (selectedCompression >= compressionsCount) {
            selectedCompression = compressionsCount - 1;
        }
        if (selectedCompression != compressionsCount - 1) {
            float maxSize;
            int targetBitrate;
            switch (selectedCompression) {
                case 0:
                    maxSize = 432.0f;
                    targetBitrate = 400000;
                    break;
                case 1:
                    maxSize = 640.0f;
                    targetBitrate = 900000;
                    break;
                case 2:
                    maxSize = 848.0f;
                    targetBitrate = 1100000;
                    break;
                case 3:
                default:
                    targetBitrate = 1600000;
                    maxSize = 1280.0f;
                    break;
            }
            float scale = originalWidth > originalHeight ? maxSize / originalWidth : maxSize / originalHeight;
            resultWidth = Math.round(originalWidth * scale / 2) * 2;
            resultHeight = Math.round(originalHeight * scale / 2) * 2;
            if (bitrate != 0) {
                bitrate = Math.min(targetBitrate, (int) (originalBitrate / scale));
                videoFramesSize = (long) (bitrate / 8 * videoDuration / 1000);
            }
        }
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

            for (int b = 0; b < boxes.size(); b++) {
                Box box = boxes.get(b);
                TrackBox trackBox = (TrackBox) box;
                long sampleSizes = 0;
                long trackBitrate = 0;
                try {
                    MediaBox mediaBox = trackBox.getMediaBox();
                    MediaHeaderBox mediaHeaderBox = mediaBox.getMediaHeaderBox();
                    SampleSizeBox sampleSizeBox = mediaBox.getMediaInformationBox().getSampleTableBox().getSampleSizeBox();
                    long[] sizes = sampleSizeBox.getSampleSizes();
                    for (int a = 0; a < sizes.length; a++) {
                        sampleSizes += sizes[a];
                    }
                    videoDuration = (float) mediaHeaderBox.getDuration() / (float) mediaHeaderBox.getTimescale();
                    trackBitrate = (int) (sampleSizes * 8 / videoDuration);
                } catch (Exception e) {
                    FileLog.e(e);
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

            videoDuration *= 1000;

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            selectedCompression = preferences.getInt("compress_video2", 1);
            updateWidthHeightBitrateForCompression();

            if (!isAvc && (resultWidth == originalWidth || resultHeight == originalHeight)) {
                return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }

        updateVideoInfo();
        return true;
    }
}
