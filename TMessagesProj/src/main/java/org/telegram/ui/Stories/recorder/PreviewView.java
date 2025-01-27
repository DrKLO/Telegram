package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getBitmapFromSurface;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.Paint.Views.RoundView;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.VideoEditTextureView;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.HashSet;

public class PreviewView extends FrameLayout {

    private Bitmap bitmap;
    private Bitmap thumbBitmap;

    private StoryEntry entry;
    private VideoPlayer videoPlayer;
    private int videoWidth, videoHeight;
    private VideoEditTextureView textureView;
    public TextureView filterTextureView;
    private PhotoFilterView photoFilterView;
    public Runnable invalidateBlur;

    private RoundView roundView;
    private VideoPlayer roundPlayer;
    private int roundPlayerWidth, roundPlayerHeight;

    private VideoPlayer audioPlayer;

    private CollageLayoutView2 collage;
//    private VideoTimelinePlayView videoTimelineView;
    private TimelineView timelineView;

    private final Paint snapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final long MAX_DURATION = 59_500L;
    public static final long MIN_DURATION = 1_000L;

    private final BlurringShader.BlurManager blurManager;
    private final TextureViewHolder textureViewHolder;

    public PreviewView(Context context, BlurringShader.BlurManager blurManager, TextureViewHolder textureViewHolder) {
        super(context);

        this.blurManager = blurManager;
        this.textureViewHolder = textureViewHolder;

        snapPaint.setStrokeWidth(dp(1));
        snapPaint.setStyle(Paint.Style.STROKE);
        snapPaint.setColor(0xffffffff);
        snapPaint.setShadowLayer(dp(3), 0, dp(1), 0x40000000);
    }

    protected void onTimeDrag(boolean dragStart, long time, boolean dragEnd) {}

    public long getDuration() {
        if (entry != null && entry.fileDuration >= 0) {
            return (long) (1000 * entry.fileDuration);
        }
        if (videoPlayer != null && videoPlayer.getDuration() != C.TIME_UNSET) {
            return videoPlayer.getDuration();
        }
        return 1L;
    }

    public void set(StoryEntry entry) {
        set(entry, null, 0);
    }

    public void set(StoryEntry entry, Runnable whenReady, long seekTo) {
        this.entry = entry;
        if (entry == null) {
            setupVideoPlayer(null, whenReady, seekTo);
            setupImage(null);
            setupCollage(null);
            setupWallpaper(null, false);
            gradientPaint.setShader(null);
            setupAudio((StoryEntry) null, false);
            setupRound(null, null, false);
            return;
        }
        if (entry.isCollage()) {
            setupImage(null);
            setupVideoPlayer(null, whenReady, seekTo);
            setupCollage(entry);
        } else if (entry.isVideo) {
            setupImage(entry);
            setupCollage(null);
            setupVideoPlayer(entry, whenReady, seekTo);
            if (entry.gradientTopColor != 0 || entry.gradientBottomColor != 0) {
                setupGradient();
            } else {
                entry.setupGradient(this::setupGradient);
            }
        } else {
            setupCollage(null);
            setupVideoPlayer(null, whenReady, 0);
            setupImage(entry);
            setupGradient();
        }
        applyMatrix();
        setupWallpaper(entry, false);
        setupAudio(entry, false);
        setupRound(entry, null, false);
    }

    public void setCollageView(CollageLayoutView2 collage) {
        this.collage = collage;
    }

    public boolean isCollage() {
        return collage != null && entry != null && entry.isCollage();
    }

    // set without video for faster transition
    public void preset(StoryEntry entry) {
        this.entry = entry;
        if (entry == null) {
            setupImage(null);
            setupWallpaper(null, false);
            gradientPaint.setShader(null);
            setupAudio((StoryEntry) null, false);
            setupRound(null, null, false);
            return;
        }
        if (entry.isVideo) {
            setupImage(entry);
            if (entry.gradientTopColor != 0 || entry.gradientBottomColor != 0) {
                setupGradient();
            } else {
                entry.setupGradient(this::setupGradient);
            }
        } else {
            setupImage(entry);
            setupGradient();
        }
        applyMatrix();
        setupWallpaper(entry, false);
        setupAudio(entry, false);
        setupRound(entry, null, false);
    }

    public void setupAudio(StoryEntry entry, boolean animated) {
        if (audioPlayer != null) {
            audioPlayer.pause();
            audioPlayer.releasePlayer(true);
            audioPlayer = null;
        }
        if (entry == null) {
            return;
        }
        if (timelineView != null) {
            timelineView.setAudio(entry.audioPath, entry.audioAuthor, entry.audioTitle, entry.audioDuration, entry.audioOffset, entry.audioLeft, entry.audioRight, entry.audioVolume, animated);
        }
        if (entry.audioPath != null) {
            audioPlayer = new VideoPlayer();
            audioPlayer.allowMultipleInstances = true;
            audioPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    AndroidUtilities.cancelRunOnUIThread(updateAudioProgressRunnable);
                    if (audioPlayer != null && audioPlayer.isPlaying()) {
                        AndroidUtilities.runOnUIThread(updateAudioProgressRunnable);
                    }
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {

                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

                }

                @Override
                public void onRenderedFirstFrame() {

                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                    invalidateTextureViewHolder();
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });
            audioPlayer.preparePlayer(Uri.fromFile(new File(entry.audioPath)), "other");
            checkVolumes();

            if (videoPlayer != null && getDuration() > 0) {
                long startPos = (long) (entry.left * getDuration());
                videoPlayer.seekTo(startPos);
                timelineView.setProgress(startPos);
            }
            updateAudioPlayer(true);
        }
        onAudioChanged();
    }

    public void setupAudio(MessageObject messageObject, boolean animated) {
        if (entry != null) {
            entry.editedMedia = true;
            if (messageObject == null || messageObject.messageOwner == null) {
                entry.audioPath = null;
                entry.audioAuthor = null;
                entry.audioTitle = null;
                entry.audioDuration = entry.audioOffset = 0;
                entry.audioLeft = 0;
                entry.audioRight = 1;
            } else {
                entry.audioPath = messageObject.messageOwner.attachPath;
                entry.audioAuthor = null;
                entry.audioTitle = null;
                TLRPC.Document audioDocument = messageObject.getDocument();
                if (audioDocument != null) {
                    for (TLRPC.DocumentAttribute attr : audioDocument.attributes) {
                        if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                            entry.audioAuthor = attr.performer;
                            if (!TextUtils.isEmpty(attr.title)) {
                                entry.audioTitle = attr.title;
                            }
                            entry.audioDuration = (long) (attr.duration * 1000);
                            break;
                        } else if (attr instanceof TLRPC.TL_documentAttributeFilename) {
                            entry.audioTitle = attr.file_name;
                        }
                    }
                }
                entry.audioOffset = 0;
                if (entry.isVideo) {
                    entry.audioOffset = (long) (entry.left * getDuration());
                }
                entry.audioLeft = 0;
                long duration;
                if (isCollage() && collage.hasVideo()) {
                    duration = collage.getDuration();
                } else if (entry.isVideo) {
                    duration = getDuration();
                } else {
                    duration = entry.audioDuration;
                }
                entry.audioRight = entry.audioDuration == 0 ? 1 : Math.min(1, Math.min(duration, TimelineView.MAX_SELECT_DURATION) / (float) entry.audioDuration);
            }
        }
        setupAudio(entry, animated);
    }

    public void onAudioChanged() {}

    private long finalSeekPosition;
    private boolean slowerSeekScheduled;
    private Runnable slowerSeek = () -> {
        seekTo(finalSeekPosition);
        slowerSeekScheduled = false;
    };
    private void seekTo(long position) {
        seekTo(position, false);
    }

    public void seekTo(long position, boolean fast) {
        if (videoPlayer != null) {
            videoPlayer.seekTo(position, fast);
        } else if (isCollage()) {
            collage.seekTo(position, fast);
        } else if (roundPlayer != null) {
            roundPlayer.seekTo(position, fast);
        } else if (audioPlayer != null) {
            audioPlayer.seekTo(position, fast);
        }
        updateAudioPlayer(true);
        updateRoundPlayer(true);
        if (fast) {
            if (!slowerSeekScheduled || Math.abs(finalSeekPosition - position) > 450) {
                slowerSeekScheduled = true;
                AndroidUtilities.cancelRunOnUIThread(this.slowerSeek);
                AndroidUtilities.runOnUIThread(this.slowerSeek, 60);
            }
            finalSeekPosition = position;
        }
    }

    public long getCurrentPosition() {
        if (videoPlayer != null) {
            return videoPlayer.getCurrentPosition();
        } else if (roundPlayer != null) {
            return roundPlayer.getCurrentPosition();
        } else if (audioPlayer != null) {
            return audioPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void getCoverBitmap(Utilities.Callback<Bitmap> whenBitmapDone, View ...views) {
        int w = (int) (dp(26) * AndroidUtilities.density);
        int h = (int) (dp(30.33f) * AndroidUtilities.density);
        int r = (int) (dp(4) * AndroidUtilities.density);

        Bitmap[] bitmaps = new Bitmap[ views.length ];
        for (int i = 0; i < views.length; ++i) {
            if (views[i] == null || views[i].getWidth() < 0 || views[i].getHeight() <= 0) continue;
            if (views[i] == this && textureView != null) {
                bitmaps[i] = textureView.getBitmap();
            } else if (views[i] instanceof TextureView) {
                bitmaps[i] = ((TextureView) views[i]).getBitmap();
            } else if (views[i] instanceof ViewGroup && ((ViewGroup) views[i]).getChildCount() > 0) {
                bitmaps[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmaps[i]);
                canvas.save();
                final float s = Math.max((float) w / views[i].getWidth(), (float) h / views[i].getHeight());
                canvas.scale(s, s);
                views[i].draw(canvas);
                canvas.restore();
            }
        }

        Utilities.globalQueue.postRunnable(() -> {
            Bitmap cover = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cover);
            Path clipPath = new Path();
            RectF clipRect = new RectF();
            clipRect.set(0, 0, cover.getWidth(), cover.getHeight());
            clipPath.addRoundRect(clipRect, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);

            for (int i = 0; i < bitmaps.length; ++i) {
                if (bitmaps[i] == null) continue;
                canvas.save();
                canvas.translate(cover.getWidth() / 2f, cover.getHeight() / 2f);
                final float s = Math.max((float) cover.getWidth() / bitmaps[i].getWidth(), (float) cover.getHeight() / bitmaps[i].getHeight());
                canvas.scale(s, s);
                canvas.translate(-bitmaps[i].getWidth() / 2f, -bitmaps[i].getHeight() / 2f);
                canvas.drawBitmap(bitmaps[i], 0, 0, null);
                canvas.restore();
                AndroidUtilities.recycleBitmap(bitmaps[i]);
            }

            Utilities.stackBlurBitmap(cover, 1);

            AndroidUtilities.runOnUIThread(() -> {
                whenBitmapDone.run(cover);
            });
        });
    }

    public void seek(long position) {
        seekTo(position);
        if (timelineView != null) {
            timelineView.setProgress(0);
        }
    }

    public void setVideoTimelineView(TimelineView timelineView) {
        this.timelineView = timelineView;
        if (timelineView != null) {
            timelineView.setDelegate(new TimelineView.TimelineDelegate() {
                @Override
                public void onProgressDragChange(boolean dragging) {
                    if (isCollage()) {
                        collage.forceNotRestorePosition();
                    }
                    updatePauseReason(-4, dragging);
                }

                @Override
                public void onProgressChange(long progress, boolean fast) {
                    if (!fast) {
                        seekTo(progress);
                    } else if (videoPlayer != null) {
                        videoPlayer.seekTo(progress, true);
                    } else if (isCollage()) {
                        collage.seekTo(progress, true);
                    } else if (audioPlayer != null) {
                        audioPlayer.seekTo(progress, false);
                    }
                }

                @Override
                public void onVideoVolumeChange(float volume) {
                    if (entry == null) {
                        return;
                    }
                    entry.videoVolume = volume;
                    checkVolumes();
                }

                @Override
                public void onVideoLeftChange(boolean released, float left) {
                    if (entry == null) {
                        return;
                    }
                    entry.left = left;
                    entry.editedMedia = true;
                    if (videoPlayer != null && videoPlayer.getDuration() != C.TIME_UNSET) {
                        seekTo((long) (left * videoPlayer.getDuration()));
                    }
                }

                @Override
                public void onVideoRightChange(boolean released, float right) {
                    if (entry == null) {
                        return;
                    }
                    entry.right = right;
                    entry.editedMedia = true;
                }

                @Override
                public void onAudioLeftChange(float left) {
                    if (entry == null) {
                        return;
                    }
                    entry.audioLeft = left;
                    entry.editedMedia = true;
                    updateAudioPlayer(true);
                }

                @Override
                public void onAudioRightChange(float right) {
                    if (entry == null) {
                        return;
                    }
                    entry.audioRight = right;
                    entry.editedMedia = true;
                    updateAudioPlayer(true);
                }

                @Override
                public void onAudioOffsetChange(long offset) {
                    if (entry == null) {
                        return;
                    }
                    entry.audioOffset = offset;
                    entry.editedMedia = true;
                    updateAudioPlayer(true);
                }

                @Override
                public void onAudioRemove() {
                    setupAudio((MessageObject) null, true);
                }

                @Override
                public void onAudioVolumeChange(float volume) {
                    if (entry == null) {
                        return;
                    }
                    entry.audioVolume = volume;
                    entry.editedMedia = true;
                    checkVolumes();
                }

                @Override
                public void onRoundLeftChange(float left) {
                    if (entry == null) {
                        return;
                    }
                    entry.roundLeft = left;
                    entry.editedMedia = true;
                    updateRoundPlayer(true);
                }

                @Override
                public void onRoundRightChange(float right) {
                    if (entry == null) {
                        return;
                    }
                    entry.roundRight = right;
                    entry.editedMedia = true;
                    updateRoundPlayer(true);
                }

                @Override
                public void onRoundOffsetChange(long offset) {
                    if (entry == null) {
                        return;
                    }
                    entry.roundOffset = offset;
                    entry.editedMedia = true;
                    updateRoundPlayer(true);
                }

                @Override
                public void onRoundRemove() {
                    setupRound(null, null, true);
                    PreviewView.this.onRoundRemove();
                }

                @Override
                public void onRoundVolumeChange(float volume) {
                    if (entry == null) {
                        return;
                    }
                    entry.roundVolume = volume;
                    entry.editedMedia = true;
                    checkVolumes();
                }

                @Override
                public void onRoundSelectChange(boolean selected) {
                    PreviewView.this.onRoundSelectChange(selected);
                }

                @Override
                public void onVideoVolumeChange(int i, float volume) {
                    if (entry != null && entry.collageContent != null && i >= 0 && i < entry.collageContent.size()) {
                        entry.collageContent.get(i).videoVolume = volume;
                    }
                }

                @Override
                public void onVideoLeftChange(int i, float left) {
                    if (entry != null && entry.collageContent != null && i >= 0 && i < entry.collageContent.size()) {
                        entry.collageContent.get(i).videoLeft = left;
                    }
                }

                @Override
                public void onVideoRightChange(int i, float right) {
                    if (entry != null && entry.collageContent != null && i >= 0 && i < entry.collageContent.size()) {
                        entry.collageContent.get(i).videoRight = right;
                    }
                }

                @Override
                public void onVideoOffsetChange(int i, long offset) {
                    if (entry != null && entry.collageContent != null && i >= 0 && i < entry.collageContent.size()) {
                        entry.collageContent.get(i).videoOffset = offset;
                    }
                }

                @Override
                public void onVideoSelected(int i) {
                    if (collage != null) {
                        collage.highlight(i);
                    }
                }
            });
        }
    }

    public void onRoundRemove() {
        // Override
    }

    public void onRoundSelectChange(boolean selected) {
        // Override
    }

    private void setupImage(StoryEntry entry) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
        if (thumbBitmap != null && !thumbBitmap.isRecycled()) {
            thumbBitmap.recycle();
        }
        thumbBitmap = null;
        if (entry != null) {
            final int rw = getMeasuredWidth() <= 0 ? AndroidUtilities.displaySize.x : getMeasuredWidth();
            final int rh = (int) (rw * 16 / 9f);
            long imageId = -1L;
            if (entry.isVideo) {
                if (entry.blurredVideoThumb != null) {
                    bitmap = entry.blurredVideoThumb;
                }
                if (bitmap == null && entry.thumbPath != null && entry.thumbPath.startsWith("vthumb://")) {
                    imageId = Long.parseLong(entry.thumbPath.substring(9));

                    if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            Uri uri;
                            if (entry.isVideo) {
                                uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, imageId);
                            } else {
                                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
                            }
                            bitmap = getContext().getContentResolver().loadThumbnail(uri, new Size(rw, rh), null);
                        } catch (Exception ignore) {}
                    }
                }
            }
            if (imageId < 0 && entry.isVideo && entry.thumbPath == null) {
                invalidate();
                return;
            }
            if (bitmap == null) {
                File file = entry.getOriginalFile();
                if (file == null) {
                    return;
                }
                String path = file.getPath();

                final long imageIdFinal = imageId;
                bitmap = StoryEntry.getScaledBitmap(opts -> {
                    if (entry.isVideo) {
                        if (entry.thumbPath != null) {
                            return BitmapFactory.decodeFile(entry.thumbPath, opts);
                        } else {
                            try {
                                return MediaStore.Video.Thumbnails.getThumbnail(getContext().getContentResolver(), imageIdFinal, MediaStore.Video.Thumbnails.MINI_KIND, opts);
                            } catch (Throwable e) {
                                invalidate();
                                return null;
                            }
                        }
                    } else {
                        return BitmapFactory.decodeFile(path, opts);
                    }
                }, rw, rh, false, true);
                if (entry != null && blurManager != null && bitmap != null) {
                    blurManager.resetBitmap();
                    blurManager.setFallbackBlur(entry.buildBitmap(0.2f, bitmap), 0);
                    if (invalidateBlur != null) {
                        invalidateBlur.run();
                    }
                }
                return;
            }
            if (!entry.isDraft && entry.isVideo && bitmap != null) {
                entry.width = bitmap.getWidth();
                entry.height = bitmap.getHeight();
                entry.setupMatrix();
            }
        }
        if (entry != null && blurManager != null && bitmap != null) {
            blurManager.resetBitmap();
            blurManager.setFallbackBlur(entry.buildBitmap(0.2f, bitmap), 0);
            if (invalidateBlur != null) {
                invalidateBlur.run();
            }
        }
        invalidate();
    }

    private void setupCollage(StoryEntry entry) {
        if (timelineView != null) {
            timelineView.setCollage(entry != null ? entry.collageContent : null);
        }
    }

    private void setupGradient() {
        final int height = getMeasuredHeight() > 0 ? getMeasuredHeight() : AndroidUtilities.displaySize.y;
        if (entry.gradientTopColor == 0 || entry.gradientBottomColor == 0) {
            if (bitmap != null) {
                DominantColors.getColors(true, bitmap, true, colors -> {
                    entry.gradientTopColor = gradientTop = colors[0];
                    entry.gradientBottomColor = gradientBottom = colors[1];
                    gradientPaint.setShader(new LinearGradient(0, 0, 0, height, colors, new float[]{0, 1}, Shader.TileMode.CLAMP));
                    invalidate();

                    if (textureView != null) {
                        textureView.updateUiBlurGradient(gradientTop, gradientBottom);
                    }
                    if (photoFilterView != null) {
                        photoFilterView.updateUiBlurGradient(gradientTop, gradientBottom);
                    }
                });
            } else if (thumbBitmap != null) {
                DominantColors.getColors(true, thumbBitmap, true, colors -> {
                    entry.gradientTopColor = gradientTop = colors[0];
                    entry.gradientBottomColor = gradientBottom = colors[1];
                    gradientPaint.setShader(new LinearGradient(0, 0, 0, height, colors, new float[]{0, 1}, Shader.TileMode.CLAMP));
                    invalidate();

                    if (textureView != null) {
                        textureView.updateUiBlurGradient(gradientTop, gradientBottom);
                    }
                    if (photoFilterView != null) {
                        photoFilterView.updateUiBlurGradient(gradientTop, gradientBottom);
                    }
                });
            } else {
                gradientPaint.setShader(null);
            }
        } else {
            gradientPaint.setShader(new LinearGradient(0, 0, 0, height, new int[] { gradientTop = entry.gradientTopColor, gradientBottom = entry.gradientBottomColor }, new float[]{0, 1}, Shader.TileMode.CLAMP));
            if (textureView != null) {
                textureView.updateUiBlurGradient(gradientTop, gradientBottom);
            }
            if (photoFilterView != null) {
                photoFilterView.updateUiBlurGradient(gradientTop, gradientBottom);
            }
        }

        invalidate();
    }

    public void setupVideoPlayer(StoryEntry entry, Runnable whenReady, long seekTo) {
        if (entry == null || entry.isCollage()) {
            if (videoPlayer != null) {
                videoPlayer.pause();
                videoPlayer.releasePlayer(true);
                videoPlayer = null;
            }
            if (textureViewHolder != null && textureViewHolder.active) {
                textureViewHolder.setTextureView(null);
            } else if (textureView != null) {
                textureView.clearAnimation();
                textureView.animate().alpha(0).withEndAction(() -> {
                    if (textureView != null) {
                        textureView.release();
                        removeView(textureView);
                        textureView = null;
                    }
                }).start();
            }
            if (timelineView != null) {
                timelineView.setVideo(false, null, 1, 0);
            }
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
            if (whenReady != null) {
                AndroidUtilities.runOnUIThread(whenReady);
            }
        } else {
            if (videoPlayer != null) {
                videoPlayer.releasePlayer(true);
                videoPlayer = null;
            }

            final Runnable[] whenReadyFinal = new Runnable[] { whenReady };

            videoPlayer = new VideoPlayer();
            videoPlayer.allowMultipleInstances = true;
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (videoPlayer == null) {
                        return;
                    }
                    if (videoPlayer != null && videoPlayer.isPlaying()) {
                        AndroidUtilities.runOnUIThread(updateProgressRunnable);
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
                    }
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {
                    if (onErrorListener != null) {
                        onErrorListener.run();
                    }
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    if (entry != null) {
                        entry.hdrInfo = videoPlayer.getHDRStaticInfo(entry.hdrInfo);
                        if (textureView != null) {
                            textureView.setHDRInfo(entry.hdrInfo);
                        }
                    }

                    videoWidth = (int) (width * pixelWidthHeightRatio);
                    videoHeight = (int) (height * pixelWidthHeightRatio);
                    if (entry != null && (entry.width != videoWidth || entry.height != videoHeight)) {
                        entry.width = videoWidth;
                        entry.height = videoHeight;
                        entry.setupMatrix();
                    }
                    applyMatrix();
                    if (textureView != null) {
                        textureView.setVideoSize(videoWidth, videoHeight);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    if (textureViewHolder != null && textureViewHolder.active) {
                        textureViewHolder.activateTextureView(videoWidth, videoHeight);
                    }
                    if (whenReadyFinal[0] != null) {
                        post(whenReadyFinal[0]);
                        whenReadyFinal[0] = null;
                        if (bitmap != null) {
                            bitmap.recycle();
                            if (entry.blurredVideoThumb == bitmap) {
                                entry.blurredVideoThumb = null;
                            }
                            bitmap = null;
                            invalidate();
                        }
                    } else if (textureView != null && !(textureViewHolder != null && textureViewHolder.active)) {
                        textureView.animate().alpha(1f).setDuration(180).withEndAction(() -> {
                            if (bitmap != null) {
                                bitmap.recycle();
                                if (entry.blurredVideoThumb == bitmap) {
                                    entry.blurredVideoThumb = null;
                                }
                                bitmap = null;
                                invalidate();
                            }
                        }).start();
                    }
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                    invalidateTextureViewHolder();
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });

            if (textureView != null) {
                textureView.clearAnimation();
                textureView.release();
                removeView(textureView);
                textureView = null;
            }

            textureView = new VideoEditTextureView(getContext(), videoPlayer);
            blurManager.resetBitmap();
            textureView.updateUiBlurManager(entry.isRepostMessage ? null : blurManager);
            textureView.setOpaque(false);
            applyMatrix();
            if (textureViewHolder != null && textureViewHolder.active) {
                textureViewHolder.setTextureView(textureView);
            } else {
                textureView.setAlpha(whenReady == null ? 0f : 1f);
                addView(textureView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
            }

            entry.detectHDR((hdrInfo) -> {
                if (textureView != null) {
                    textureView.setHDRInfo(hdrInfo);
                }
            });

            Uri uri = Uri.fromFile(entry.getOriginalFile());
            videoPlayer.preparePlayer(uri, "other");
            videoPlayer.setPlayWhenReady(pauseLinks.isEmpty());
            videoPlayer.setLooping(true);
            if (entry.isEditSaved) {
                seekTo = (long) ((entry.left * entry.duration) + seekTo);
            }
            if (seekTo > 0) {
                videoPlayer.seekTo(seekTo);
            }
            checkVolumes();
            updateAudioPlayer(true);

            final boolean isRound = entry.isRepostMessage && entry.messageObjects != null && entry.messageObjects.size() == 1 && entry.messageObjects.get(0).type == MessageObject.TYPE_ROUND_VIDEO;
            timelineView.setVideo(isRound, entry.getOriginalFile().getAbsolutePath(), getDuration(), entry.videoVolume);
            timelineView.setVideoLeft(entry.left);
            timelineView.setVideoRight(entry.right);
            if (timelineView != null && seekTo > 0) {
                timelineView.setProgress(seekTo);
            }
        }
    }

    public static class TextureViewHolder {
        private TextureView textureView;
        private Utilities.Callback<TextureView> whenTextureViewReceived;
        private Utilities.Callback2<Integer, Integer> whenTextureViewActive;
        public boolean textureViewActive;
        public int videoWidth, videoHeight;

        public boolean active;

        public void setTextureView(TextureView textureView) {
            if (this.textureView == textureView) return;
            if (this.textureView != null) {
                ViewParent parent = this.textureView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(this.textureView);
                }
                this.textureView = null;
            }
            textureViewActive = false;
            this.textureView = textureView;
            if (whenTextureViewReceived != null) {
                whenTextureViewReceived.run(this.textureView);
            }
        }

        public void activateTextureView(int w, int h) {
            textureViewActive = true;
            videoWidth = w;
            videoHeight = h;
            if (whenTextureViewActive != null) {
                whenTextureViewActive.run(videoWidth, videoHeight);
            }
        }

        public void takeTextureView(Utilities.Callback<TextureView> whenReceived, Utilities.Callback2<Integer, Integer> whenActive) {
            whenTextureViewReceived = whenReceived;
            whenTextureViewActive = whenActive;
            if (textureView != null && whenTextureViewReceived != null) {
                whenTextureViewReceived.run(textureView);
            }
            if (textureViewActive && whenTextureViewActive != null) {
                whenTextureViewActive.run(videoWidth, videoHeight);
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == textureView && entry != null && entry.isRepostMessage) {
            return false;
        }
        if ((child == textureView || child == filterTextureView) && entry != null && entry.crop != null) {
            canvas.save();

            canvas.scale((float) getWidth() / entry.resultWidth, (float) getHeight() / entry.resultHeight);
            canvas.concat(entry.matrix);
            if (entry.crop != null) {
                canvas.translate(entry.width / 2.0f, entry.height / 2.0f);
                canvas.rotate(-entry.orientation);
                int w = entry.width, h = entry.height;
                if (((entry.orientation + entry.crop.transformRotation) / 90) % 2 == 1) {
                    w = entry.height;
                    h = entry.width;
                }
                canvas.clipRect(
                    -w * entry.crop.cropPw / 2.0f, -h * entry.crop.cropPh / 2.0f,
                    +w * entry.crop.cropPw / 2.0f, +h * entry.crop.cropPh / 2.0f
                );
                canvas.rotate(entry.orientation);
                canvas.translate(-entry.width / 2.0f, -entry.height / 2.0f);
            }
            canvas.concat(invertMatrix);
            canvas.scale(1.0f / ((float) getWidth() / entry.resultWidth), 1.0f / ((float) getHeight() / entry.resultHeight));

            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setupRound(StoryEntry entry, RoundView roundView, boolean animated) {
        if (entry == null || entry.round == null) {
            if (roundPlayer != null) {
                roundPlayer.pause();
                roundPlayer.releasePlayer(true);
                roundPlayer = null;
            }
            if (timelineView != null) {
                timelineView.setRoundNull(animated);
            }
            this.roundView = null;
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
        } else {
            if (roundPlayer != null) {
                roundPlayer.releasePlayer(true);
                roundPlayer = null;
            }

            roundPlayer = new VideoPlayer();
            roundPlayer.allowMultipleInstances = true;
            roundPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (roundPlayer == null) {
                        return;
                    }
                    if (roundPlayer != null && roundPlayer.isPlaying()) {
                        AndroidUtilities.runOnUIThread(updateRoundProgressRunnable);
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(updateRoundProgressRunnable);
                    }
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {

                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    roundPlayerWidth = width;
                    roundPlayerHeight = height;
                    if (PreviewView.this.roundView != null) {
                        PreviewView.this.roundView.resizeTextureView(width, height);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {

                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });

            Uri uri = Uri.fromFile(entry.round);
            roundPlayer.preparePlayer(uri, "other");
            checkVolumes();
            attachRoundView(roundView);
            timelineView.setRound(entry.round.getAbsolutePath(), entry.roundDuration, entry.roundOffset, entry.roundLeft, entry.roundRight, entry.roundVolume, animated);
            updateRoundPlayer(true);
        }
    }

    public void attachRoundView(RoundView roundView) {
        this.roundView = roundView;
        if (roundView != null && roundPlayer != null) {
            roundPlayer.setTextureView(roundView.textureView);
        }
    }

    public long release() {
        if (audioPlayer != null) {
            audioPlayer.pause();
            audioPlayer.releasePlayer(true);
            audioPlayer = null;
        }
        long t = 0;
        if (roundPlayer != null) {
            t = roundPlayer.getCurrentPosition();
            roundPlayer.pause();
            roundPlayer.releasePlayer(true);
            roundPlayer = null;
        }
        if (videoPlayer != null) {
            t = videoPlayer.getCurrentPosition();
            videoPlayer.pause();
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
        return t;
    }

    public void setFilterTextureView(TextureView view, PhotoFilterView photoFilterView) {
        if (filterTextureView != null) {
            removeView(filterTextureView);
            filterTextureView = null;
        }
        this.photoFilterView = photoFilterView;
        filterTextureView = view;
        if (photoFilterView != null) {
            photoFilterView.updateUiBlurGradient(gradientTop, gradientBottom);
        }
        if (filterTextureView != null) {
            addView(filterTextureView);
        }
    }

    private long lastPos;
    private long seekedLastTime;
    private final Runnable updateProgressRunnable = () -> {
        if (videoPlayer == null || timelineView == null) {
            return;
        }

        long pos = videoPlayer.getCurrentPosition();
        if (getDuration() > 1) {
            final float progress = pos / (float) getDuration();
            if (!timelineView.isDragging() && (progress < entry.left || progress > entry.right) && System.currentTimeMillis() - seekedLastTime > MIN_DURATION / 2) {
                seekedLastTime = System.currentTimeMillis();
                videoPlayer.seekTo(pos = (long) (entry.left * getDuration()));
                updateAudioPlayer(true);
                updateRoundPlayer(true);
            } else {
                updateAudioPlayer(pos < lastPos);
                updateRoundPlayer(pos < lastPos);
            }
            timelineView.setProgress(videoPlayer.getCurrentPosition());
        } else {
            timelineView.setProgress(videoPlayer.getCurrentPosition());
        }
        if (videoPlayer.isPlaying()) {
            AndroidUtilities.cancelRunOnUIThread(this.updateProgressRunnable);
            AndroidUtilities.runOnUIThread(this.updateProgressRunnable, (long) (1000L / AndroidUtilities.screenRefreshRate));
        }
        lastPos = pos;
    };

    private final Runnable updateAudioProgressRunnable = () -> {
        if (audioPlayer == null || videoPlayer != null || roundPlayer != null || timelineView == null || isCollage()) {
            return;
        }

        long pos = audioPlayer.getCurrentPosition();
        if (entry != null && (pos < entry.audioLeft * entry.audioDuration || pos > entry.audioRight * entry.audioDuration) && System.currentTimeMillis() - seekedLastTime > MIN_DURATION / 2) {
            seekedLastTime = System.currentTimeMillis();
            audioPlayer.seekTo(pos = (long) (entry.audioLeft * entry.audioDuration));
        }
        timelineView.setProgress(pos);

        if (audioPlayer.isPlaying()) {
            AndroidUtilities.cancelRunOnUIThread(this.updateAudioProgressRunnable);
            AndroidUtilities.runOnUIThread(this.updateAudioProgressRunnable, (long) (1000L / AndroidUtilities.screenRefreshRate));
        }
    };

    private final Runnable updateRoundProgressRunnable = () -> {
        if (roundPlayer == null || videoPlayer != null || isCollage() || timelineView == null) {
            return;
        }

        long pos = roundPlayer.getCurrentPosition();
        if (entry != null && (pos < entry.roundLeft * entry.roundDuration || pos > entry.roundRight * entry.roundDuration) && System.currentTimeMillis() - seekedLastTime > MIN_DURATION / 2) {
            seekedLastTime = System.currentTimeMillis();
            roundPlayer.seekTo(pos = (long) (entry.roundLeft * entry.roundDuration));
            updateAudioPlayer(true);
        }
        timelineView.setProgress(pos);

        if (roundPlayer.isPlaying()) {
            AndroidUtilities.cancelRunOnUIThread(this.updateRoundProgressRunnable);
            AndroidUtilities.runOnUIThread(this.updateRoundProgressRunnable, (long) (1000L / AndroidUtilities.screenRefreshRate));
        }
    };

    public void updateAudioPlayer(boolean updateSeek) {
        if (audioPlayer == null || entry == null) {
            return;
        }

        if (videoPlayer == null && roundPlayer == null && !isCollage()) {
            audioPlayer.setPlayWhenReady(pauseLinks.isEmpty());
            audioPlayer.setLooping(true);

            long pos = audioPlayer.getCurrentPosition();
            if (updateSeek && audioPlayer.getDuration() != C.TIME_UNSET) {
                final float progress = pos / (float) audioPlayer.getDuration();
                if ((progress < entry.audioLeft || progress > entry.audioRight) && System.currentTimeMillis() - seekedLastTime > MIN_DURATION / 2) {
                    seekedLastTime = System.currentTimeMillis();
                    audioPlayer.seekTo(pos = -entry.audioOffset);
                }
            }
            return;
        }

        final long pos;
        final boolean playing;
        if (isCollage()) {
            pos = collage.getPositionWithOffset();
            playing = collage.isPlaying();
        } else {
            VideoPlayer player = videoPlayer != null ? videoPlayer : roundPlayer;
            pos = player.getCurrentPosition();
            playing = player.isPlaying();
        }

        final long duration = (long) ((entry.audioRight - entry.audioLeft) * entry.audioDuration);
        boolean shouldPlaying = playing && pos >= entry.audioOffset && pos <= entry.audioOffset + duration;
        long audioPos = pos - entry.audioOffset + (long) (entry.audioLeft * entry.audioDuration);
        if (audioPlayer.isPlaying() != shouldPlaying) {
            audioPlayer.setPlayWhenReady(shouldPlaying);
            audioPlayer.seekTo(audioPos);
        } else if (updateSeek && Math.abs(audioPlayer.getCurrentPosition() - audioPos) > (isCollage() ? 300 : 120)) {
            audioPlayer.seekTo(audioPos);
        }
    }

    public void updateRoundPlayer(boolean updateSeek) {
        if (roundPlayer == null || entry == null) {
            return;
        }

        if (videoPlayer == null && !isCollage()) {
            roundPlayer.setPlayWhenReady(pauseLinks.isEmpty());
            roundPlayer.setLooping(true);
            if (roundView != null) {
                roundView.setShown(true, false);
            }

            long pos = roundPlayer.getCurrentPosition();
            if (updateSeek && roundPlayer.getDuration() != C.TIME_UNSET) {
                final float progress = pos / (float) roundPlayer.getDuration();
                if ((progress < entry.roundLeft || progress > entry.roundRight) && System.currentTimeMillis() - seekedLastTime > MIN_DURATION / 2) {
                    seekedLastTime = System.currentTimeMillis();
                    roundPlayer.seekTo(pos = -entry.roundOffset);
                }
            }
            return;
        }

        final long pos;
        final boolean playing;
        if (isCollage()) {
            pos = collage.getPositionWithOffset();
            playing = collage.isPlaying();
        } else {
            pos = videoPlayer.getCurrentPosition();
            playing = videoPlayer.isPlaying();
        }

        final long duration = (long) ((entry.roundRight - entry.roundLeft) * entry.roundDuration);
        boolean shouldPlayingInSeek = pos >= entry.roundOffset && pos <= entry.roundOffset + duration;
        boolean shouldPlaying = playing && shouldPlayingInSeek;
        long roundPos = pos - entry.roundOffset + (long) (entry.roundLeft * entry.roundDuration);
        if (roundView != null) {
            roundView.setShown(shouldPlayingInSeek, true);
        }
        if (roundPlayer.isPlaying() != shouldPlaying) {
            roundPlayer.setPlayWhenReady(shouldPlaying);
            roundPlayer.seekTo(roundPos);
        } else if (updateSeek && Math.abs(roundPlayer.getCurrentPosition() - roundPos) > (isCollage() ? 300 : 120)) {
            roundPlayer.seekTo(roundPos);
        }
    }

    private Runnable onErrorListener;
    public void whenError(Runnable listener) {
        onErrorListener = listener;
    }

    public boolean isMuted;
    public void mute(boolean value) {
        isMuted = value;
        checkVolumes();
    }
    public void checkVolumes() {
        if (videoPlayer != null) {
            videoPlayer.setVolume(isMuted || (entry != null && entry.muted) ? 0 : (entry != null ? entry.videoVolume : 1f));
        }
        if (roundPlayer != null) {
            roundPlayer.setVolume(isMuted ? 0 : (entry != null ? entry.roundVolume : 1f));
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(isMuted ? 0 : (entry != null ? entry.audioVolume : 1f));
        }
        if (collage != null) {
            collage.setMuted(isMuted);
        }
    }

    private AnimatedFloat wallpaperDrawableCrossfade = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Drawable lastWallpaperDrawable;
    private Drawable wallpaperDrawable;
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int gradientTop, gradientBottom;

    private final Matrix matrix = new Matrix();

    private final float[] vertices = new float[2];
    private float cx, cy, angle, w, h;

    private void extractPointsData(Matrix matrix) {
        if (entry == null) {
            return;
        }

        vertices[0] = entry.width / 2f;
        vertices[1] = entry.height / 2f;
        matrix.mapPoints(vertices);
        cx = vertices[0];
        cy = vertices[1];

        vertices[0] = entry.width;
        vertices[1] = entry.height / 2f;
        matrix.mapPoints(vertices);
        angle = (float) Math.toDegrees(Math.atan2(vertices[1] - cy, vertices[0] - cx));
        w = 2 * MathUtils.distance(cx, cy, vertices[0], vertices[1]);

        vertices[0] = entry.width / 2f;
        vertices[1] = entry.height;
        matrix.mapPoints(vertices);
        h = 2 * MathUtils.distance(cx, cy, vertices[0], vertices[1]);
    }

    private boolean draw = true;
    public void setDraw(boolean draw) {
        this.draw = draw;
        invalidate();
    }

    private final AnimatedFloat thumbAlpha = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT);
    public boolean drawForThemeToggle = false;

    public void drawBackground(Canvas canvas) {
        if (wallpaperDrawable != null) {
            if (drawForThemeToggle) {
                Path path = new Path();
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                path.addRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(path);
            }
            boolean drawableReady = true;
            if (wallpaperDrawable instanceof MotionBackgroundDrawable) {
                drawableReady = ((MotionBackgroundDrawable) wallpaperDrawable).getPatternBitmap() != null;
            }
            float crossfadeAlpha = !drawableReady ? 0f : wallpaperDrawableCrossfade.set(1f);
            if (lastWallpaperDrawable != null && crossfadeAlpha < 1) {
                lastWallpaperDrawable.setAlpha((int) (0xFF * (1f - crossfadeAlpha)));
                StoryEntry.drawBackgroundDrawable(canvas, lastWallpaperDrawable, getWidth(), getHeight());
            }
            wallpaperDrawable.setAlpha((int) (0xFF * crossfadeAlpha));
            StoryEntry.drawBackgroundDrawable(canvas, wallpaperDrawable, getWidth(), getHeight());
            if (drawForThemeToggle) {
                canvas.restore();
            }
        } else {
            canvas.drawRect(0, 0, getWidth(), getHeight(), gradientPaint);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawBackground(canvas);
        if (cropEditorDrawing != null) {
            cropEditorDrawing.contentView.drawImage(canvas, true);
        } else if (draw && entry != null && !isCollage()) {
            float alpha = this.thumbAlpha.set(bitmap == null);
            if (thumbBitmap != null && alpha > 0.0f) {
                canvas.save();
                canvas.scale((float) getWidth() / entry.resultWidth, (float) getHeight() / entry.resultHeight);
                canvas.concat(entry.matrix);
                if (entry.crop != null) {
                    canvas.translate(entry.width / 2.0f, entry.height / 2.0f);
                    canvas.rotate(-entry.orientation);
                    int w = entry.width, h = entry.height;
                    if (((entry.orientation + entry.crop.transformRotation) / 90) % 2 == 1) {
                        w = entry.height;
                        h = entry.width;
                    }
                    canvas.clipRect(
                        -w * entry.crop.cropPw / 2.0f, -h * entry.crop.cropPh / 2.0f,
                        +w * entry.crop.cropPw / 2.0f, +h * entry.crop.cropPh / 2.0f
                    );
                    canvas.scale(entry.crop.cropScale, entry.crop.cropScale);
                    canvas.translate(entry.crop.cropPx * w, entry.crop.cropPy * h);
                    canvas.rotate(entry.crop.cropRotate + entry.crop.transformRotation);
                    if (entry.crop.mirrored) {
                        canvas.scale(-1, 1);
                    }
                    canvas.rotate(entry.orientation);
                    canvas.translate(-entry.width / 2.0f, -entry.height / 2.0f);
                }
                canvas.scale((float) entry.width / thumbBitmap.getWidth(), (float) entry.height / thumbBitmap.getHeight());
                bitmapPaint.setAlpha(0xFF);
                canvas.drawBitmap(thumbBitmap, 0, 0, bitmapPaint);
                canvas.restore();
            }
            if (bitmap != null) {
                canvas.save();
                canvas.scale((float) getWidth() / entry.resultWidth, (float) getHeight() / entry.resultHeight);
                canvas.concat(entry.matrix);
                if (entry.crop != null) {
                    canvas.translate(entry.width / 2.0f, entry.height / 2.0f);
                    canvas.rotate(-entry.orientation);
                    int w = entry.width, h = entry.height;
                    if (((entry.orientation + entry.crop.transformRotation) / 90) % 2 == 1) {
                        w = entry.height;
                        h = entry.width;
                    }
                    canvas.clipRect(
                        -w * entry.crop.cropPw / 2.0f, -h * entry.crop.cropPh / 2.0f,
                        +w * entry.crop.cropPw / 2.0f, +h * entry.crop.cropPh / 2.0f
                    );
                    canvas.scale(entry.crop.cropScale, entry.crop.cropScale);
                    canvas.translate(entry.crop.cropPx * w, entry.crop.cropPy * h);
                    canvas.rotate(entry.crop.cropRotate + entry.crop.transformRotation);
                    if (entry.crop.mirrored) {
                        canvas.scale(-1, 1);
                    }
                    canvas.rotate(entry.orientation);
                    canvas.translate(-entry.width / 2.0f, -entry.height / 2.0f);
                }
                canvas.scale((float) entry.width / bitmap.getWidth(), (float) entry.height / bitmap.getHeight());
                bitmapPaint.setAlpha((int) (0xFF * (1.0f - alpha)));
                canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                canvas.restore();
            }
        }
        super.dispatchDraw(canvas);
    }

    public int getContentWidth() {
        if (entry == null) return 1;
        return entry.width;
    }

    public int getContentHeight() {
        if (entry == null) return 1;
        return entry.height;
    }

    public void drawContent(Canvas canvas) {
        if (textureView != null) {
            canvas.save();
//            textureView.getTransform(matrix);
            canvas.scale((float) getContentWidth() / getWidth(), (float) getContentHeight() / getHeight());
            canvas.concat(transformBackMatrix);
            textureView.draw(canvas);
            canvas.restore();
        } else if (bitmap != null && entry != null) {
//            matrix.set(entry.matrix);
            matrix.reset();
            matrix.preScale((float) entry.width / bitmap.getWidth(), (float) entry.height / bitmap.getHeight());
            bitmapPaint.setAlpha(0xFF);
            canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        }
    }

    public void getContentMatrix(Matrix matrix) {
        if (entry == null) {
            matrix.reset();
            return;
        }
//        matrix.
    }

    public VideoEditTextureView getTextureView() {
        return textureView;
    }

    protected void invalidateTextureViewHolder() {

    }

    public Pair<Integer, Integer> getPaintSize() {
        if (entry == null) {
            return new Pair<>(1080, 1920);
        }
        return new Pair<>(entry.resultWidth, entry.resultHeight);
    }

    public Bitmap getPhotoBitmap() {
        return bitmap;
    }

    public int getOrientation() {
        return entry == null ? 0 : entry.orientation;
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == View.GONE) {
            set(null);
        }
    }

    private final Matrix invertMatrix = new Matrix();
    private final Matrix transformMatrix = new Matrix();
    private final Matrix transformBackMatrix = new Matrix();
    public void applyMatrix() {
        if (entry == null || entry.isRepostMessage) {
            return;
        }
        if (textureView != null) {
            matrix.set(entry.matrix);
            matrix.preScale(
                    1f / getWidth() * (entry.width < 0 ? videoWidth : entry.width),
                    1f / getHeight() * (entry.height < 0 ? videoHeight : entry.height)
            );
            matrix.postScale(
                    (float) getWidth() / entry.resultWidth,
                    (float) getHeight() / entry.resultHeight
            );
            transformBackMatrix.reset();
            transformMatrix.invert(transformBackMatrix);
            textureView.setTransform(matrix);
            textureView.invalidate();
        }
        invalidate();
//
//        invertMatrix.reset();
//        entry.matrix.invert(invertMatrix);
//        if (entry == null || entry.isRepostMessage) {
//            return;
//        }
//        if (textureView != null) {
//            setTextureViewTransform(false, textureView);
//            textureView.invalidate();
//        }
//        invalidate();
    }

    public void setTextureViewTransform(boolean applyRotation, TextureView view) {
        if (entry == null || view == null) {
            return;
        }
        invalidate();
        transformMatrix.reset();
        transformMatrix.postScale(
            1f / getWidth() * (entry.width < 0 ? videoWidth : entry.width),
            1f / getHeight() * (entry.height < 0 ? videoHeight : entry.height)
        );
        if (entry.crop != null) {
            transformMatrix.preTranslate(entry.width / 2.0f, entry.height / 2.0f);
            transformMatrix.preRotate(-entry.orientation);
            int w = entry.width, h = entry.height;
            if (((entry.orientation + entry.crop.transformRotation) / 90) % 2 == 1) {
                w = entry.height;
                h = entry.width;
            }
//            canvas.clipRect(
//                    -w * entry.crop.cropPw / 2.0f, -h * entry.crop.cropPh / 2.0f,
//                    +w * entry.crop.cropPw / 2.0f, +h * entry.crop.cropPh / 2.0f
//            );
            transformMatrix.preScale(entry.crop.cropScale, entry.crop.cropScale);
            transformMatrix.preTranslate(entry.crop.cropPx * w, entry.crop.cropPy * h);
            transformMatrix.preRotate(entry.crop.cropRotate + entry.crop.transformRotation);
            if (entry.crop.mirrored) {
                transformMatrix.preScale(-1, 1);
            }
            transformMatrix.preRotate(entry.orientation);
            transformMatrix.preTranslate(-entry.width / 2.0f, -entry.height / 2.0f);
        }
        transformMatrix.preConcat(entry.matrix);
        transformMatrix.preScale(
            (float) getWidth() / entry.resultWidth,
            (float) getHeight() / entry.resultHeight
        );
        transformBackMatrix.reset();
        transformMatrix.invert(transformBackMatrix);
        view.setTransform(transformMatrix);
    }

    private boolean allowCropping = true;
    public void setAllowCropping(boolean allow) {
        this.allowCropping = allow;
    }

    private final PointF lastTouch = new PointF();
    private final PointF touch = new PointF();
    private float lastTouchDistance;
    private double lastTouchRotation;
    private boolean multitouch;
    private boolean allowWithSingleTouch, allowRotation;
    private Matrix touchMatrix = new Matrix(), finalMatrix = new Matrix();
    private float rotationDiff;
    private boolean snappedToCenterAndScaled, snappedRotation;
    private boolean doNotSpanRotation;
    private boolean moving;

    public boolean additionalTouchEvent(MotionEvent ev) {
        return false;
    }

    private boolean touchEvent(MotionEvent ev) {
        if (!allowCropping) {
            return false;
        }
        final boolean currentMultitouch = ev.getPointerCount() > 1;
        float distance = 0;
        double rotation = 0;
        if (currentMultitouch) {
            touch.x = (ev.getX(0) + ev.getX(1)) / 2f;
            touch.y = (ev.getY(0) + ev.getY(1)) / 2f;
            distance = MathUtils.distance(ev.getX(0), ev.getY(0), ev.getX(1), ev.getY(1));
            rotation = Math.atan2(ev.getY(1) - ev.getY(0), ev.getX(1) - ev.getX(0));
        } else {
            touch.x = ev.getX(0);
            touch.y = ev.getY(0);
        }
        if (multitouch != currentMultitouch) {
            lastTouch.x = touch.x;
            lastTouch.y = touch.y;
            lastTouchDistance = distance;
            lastTouchRotation = rotation;
            multitouch = currentMultitouch;
        }
        if (entry == null) {
            return false;
        }

        final float scale = (entry.resultWidth / (float) getWidth());
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            rotationDiff = 0;
            snappedRotation = false;
            snappedToCenterAndScaled = false;
            doNotSpanRotation = false;
            invalidate();
            moving = true;
            touchMatrix.set(entry.matrix);
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && moving && entry != null) {
            float tx = touch.x * scale, ty = touch.y * scale;
            float ltx = lastTouch.x * scale, lty = lastTouch.y * scale;
            if (ev.getPointerCount() > 1) {
                if (lastTouchDistance != 0) {
                    float scaleFactor = distance / lastTouchDistance;
                    touchMatrix.postScale(scaleFactor, scaleFactor, tx, ty);
                }
                float rotate = (float) Math.toDegrees(rotation - lastTouchRotation);
                rotationDiff += rotate;
                if (!allowRotation) {
                    allowRotation = Math.abs(rotationDiff) > 20f;
                    if (!allowRotation) {
                        extractPointsData(touchMatrix);
                        allowRotation = Math.round(angle / 90f) * 90f - angle > 20f;
                    }
                    if (!snappedRotation) {
                        AndroidUtilities.vibrateCursor(this);
                        snappedRotation = true;
                    }
                }
                if (allowRotation) {
                    touchMatrix.postRotate(rotate, tx, ty);
                }
                allowWithSingleTouch = true;
            }
            if (ev.getPointerCount() > 1 || allowWithSingleTouch) {
                touchMatrix.postTranslate(tx - ltx, ty - lty);
            }
            finalMatrix.set(touchMatrix);
            matrix.set(touchMatrix);
            extractPointsData(matrix);
            float rotDiff = Math.round(angle / 90f) * 90f - angle;
            if (allowRotation && !doNotSpanRotation) {
                if (Math.abs(rotDiff) < 3.5f) {
                    finalMatrix.postRotate(rotDiff, cx, cy);
                    if (!snappedRotation) {
                        AndroidUtilities.vibrateCursor(this);
                        snappedRotation = true;
                    }
                } else {
                    snappedRotation = false;
                }
            }

            entry.matrix.set(finalMatrix);
            entry.editedMedia = true;
            applyMatrix();
            invalidate();
        }
        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            if (ev.getPointerCount() <= 1) {
                allowWithSingleTouch = false;
                onEntityDraggedTop(false);
                onEntityDraggedBottom(false);
            }
            moving = false;
            allowRotation = false;
            rotationDiff = 0;

            snappedToCenterAndScaled = false;
            snappedRotation = false;
            invalidate();
        }
        lastTouch.x = touch.x;
        lastTouch.y = touch.y;
        lastTouchDistance = distance;
        lastTouchRotation = rotation;
        return true;
    }

    private long tapTime;
    private boolean tapTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            tapTime = System.currentTimeMillis();
            return true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            if (System.currentTimeMillis() - tapTime <= ViewConfiguration.getTapTimeout() && onTap != null) {
                onTap.run();
            }
            tapTime = 0;
            return true;
        } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {
            tapTime = 0;
        }
        return false;
    }

    private Runnable onTap;
    public void setOnTapListener(Runnable listener) {
        onTap = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (allowCropping) {
            touchEvent(ev);
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean result = touchEvent(ev);
        result = additionalTouchEvent(ev) || result;
        tapTouchEvent(ev);
        if (result) {
            if (ev.getPointerCount() <= 1) {
                return super.dispatchTouchEvent(ev) || true;
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void onEntityDraggedTop(boolean value) {

    }

    public void onEntityDraggedBottom(boolean value) {

    }

    public void onEntityDragEnd(boolean delete) {

    }

    public void onEntityDragStart() {

    }

    public void onEntityDragTrash(boolean enter) {

    }

    private final HashSet<Integer> pauseLinks = new HashSet<>();
    public void updatePauseReason(int reasonId, boolean pause) {
        if (pause) {
            pauseLinks.add(reasonId);
        } else {
            pauseLinks.remove(reasonId);
        }
        if (videoPlayer != null) {
            videoPlayer.setPlayWhenReady(pauseLinks.isEmpty());
        }
        if (collage != null) {
            collage.setPlaying(pauseLinks.isEmpty());
        }
        updateAudioPlayer(true);
        updateRoundPlayer(true);
    }

    // ignores actual player and other reasons to pause a video
    // (so that play button doesn't make it play when f.ex. popup is on)
    public boolean isPlaying() {
        return !pauseLinks.contains(-9982);
    }
    public void play(boolean play) {
        updatePauseReason(-9982, !play);
    }

    public static Drawable getBackgroundDrawable(Drawable prevDrawable, int currentAccount, long dialogId, boolean isDark) {
        if (dialogId == Long.MIN_VALUE) {
            return null;
        }
        TLRPC.WallPaper wallpaper = null;
        if (dialogId >= 0) {
            TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            if (userFull != null) {
                wallpaper = userFull.wallpaper;
            }
        } else {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            if (chatFull != null) {
                wallpaper = chatFull.wallpaper;
            }
        }
        return getBackgroundDrawable(prevDrawable, currentAccount, wallpaper, isDark);
    }

    public static Drawable getBackgroundDrawable(Drawable prevDrawable, int currentAccount, TLRPC.WallPaper wallpaper, boolean isDark) {
        if (wallpaper != null && TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(wallpaper))) {
            return ChatBackgroundDrawable.getOrCreate(prevDrawable, wallpaper, isDark);
        }

        EmojiThemes theme = null;
        if (wallpaper != null && wallpaper.settings != null) {
            theme = ChatThemeController.getInstance(currentAccount).getTheme(wallpaper.settings.emoticon);
        }
        if (theme != null) {
            return getBackgroundDrawableFromTheme(currentAccount, theme, 0, isDark);
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }
        if (isDark) {
            themeInfo = Theme.getTheme(nightThemeName);
        } else {
            themeInfo = Theme.getTheme(dayThemeName);
        }
        SparseIntArray currentColors = new SparseIntArray();
        final String[] wallpaperLink = new String[1];
        final SparseIntArray themeColors;
        if (themeInfo.assetName != null) {
            themeColors = Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink);
        } else {
            themeColors = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
        }
        int[] defaultColors = Theme.getDefaultColors();
        if (defaultColors != null) {
            for (int i = 0; i < defaultColors.length; ++i) {
                currentColors.put(i, defaultColors[i]);
            }
        }
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (accent != null) {
            accent.fillAccentColors(themeColors, currentColors);
        } else if (themeColors != null) {
            for (int i = 0; i < themeColors.size(); ++i) {
                currentColors.put(themeColors.keyAt(i), themeColors.valueAt(i));
            }
        }
        Theme.BackgroundDrawableSettings bg = Theme.createBackgroundDrawable(themeInfo, currentColors, wallpaperLink[0], 0, true);
        return bg.themedWallpaper != null ? bg.themedWallpaper : bg.wallpaper;
    }

    public void setupWallpaper(StoryEntry entry, boolean animated) {
        lastWallpaperDrawable = wallpaperDrawable;
        if (wallpaperDrawable != null) {
            wallpaperDrawable.setCallback(null);
        }
        if (entry == null) {
            wallpaperDrawable = null;
            return;
        }
        long dialogId = entry.backgroundWallpaperPeerId;
        if (entry.backgroundWallpaperEmoticon != null) {
            wallpaperDrawable = entry.backgroundDrawable = getBackgroundDrawableFromTheme(entry.currentAccount, entry.backgroundWallpaperEmoticon, entry.isDark);
        } else if (dialogId != Long.MIN_VALUE) {
            wallpaperDrawable = entry.backgroundDrawable = getBackgroundDrawable(wallpaperDrawable, entry.currentAccount, dialogId, entry.isDark);
        } else {
            wallpaperDrawable = null;
            return;
        }
        if (lastWallpaperDrawable != wallpaperDrawable) {
            if (animated) {
                wallpaperDrawableCrossfade.set(0, true);
            } else {
                lastWallpaperDrawable = null;
            }
        }
        if (wallpaperDrawable != null) {
            wallpaperDrawable.setCallback(this);
        }
        if (blurManager != null) {
            if (wallpaperDrawable != null) {
                if (wallpaperDrawable instanceof BitmapDrawable) {
                    blurManager.setFallbackBlur(((BitmapDrawable) wallpaperDrawable).getBitmap(), 0);
                } else {
                    int w = wallpaperDrawable.getIntrinsicWidth();
                    int h = wallpaperDrawable.getIntrinsicHeight();
                    if (w <= 0 || h <= 0) {
                        w = 1080;
                        h = 1920;
                    }
                    float scale = Math.max(100f / w, 100f / h);
                    if (scale > 1) {
                        w *= scale;
                        h *= scale;
                    }
                    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    wallpaperDrawable.setBounds(0, 0, w, h);
                    wallpaperDrawable.draw(new Canvas(bitmap));
                    blurManager.setFallbackBlur(bitmap, 0, true);
                }
            } else {
                blurManager.setFallbackBlur(null, 0);
            }
        }
        invalidate();
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, String emoticon, boolean isDark) {
        return getBackgroundDrawableFromTheme(currentAccount, emoticon, isDark, false);
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, String emoticon, boolean isDark, boolean preview) {
        EmojiThemes theme = ChatThemeController.getInstance(currentAccount).getTheme(emoticon);
        if (theme == null) {
            return Theme.getCachedWallpaper();
        }
        return getBackgroundDrawableFromTheme(currentAccount, theme, 0, isDark, preview);
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, EmojiThemes chatTheme, int prevPhase, boolean isDark) {
        return getBackgroundDrawableFromTheme(currentAccount, chatTheme, prevPhase, isDark, false);
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, EmojiThemes chatTheme, int prevPhase, boolean isDark, boolean preview) {
        Drawable drawable;
        if (chatTheme.isAnyStub()) {
            Theme.ThemeInfo themeInfo = EmojiThemes.getDefaultThemeInfo(isDark);
            SparseIntArray currentColors = chatTheme.getPreviewColors(currentAccount, isDark ? 1 : 0);
            String wallpaperLink = chatTheme.getWallpaperLink(isDark ? 1 : 0);
            Theme.BackgroundDrawableSettings settings = Theme.createBackgroundDrawable(themeInfo, currentColors, wallpaperLink, prevPhase, false);
            drawable = settings.wallpaper;
            drawable = new ColorDrawable(Color.BLACK);
        } else {
            SparseIntArray currentColors = chatTheme.getPreviewColors(currentAccount, isDark ? 1 : 0);
            int backgroundColor = currentColors.get(Theme.key_chat_wallpaper, Theme.getColor(Theme.key_chat_wallpaper));
            int gradientColor1 = currentColors.get(Theme.key_chat_wallpaper_gradient_to1, Theme.getColor(Theme.key_chat_wallpaper_gradient_to1));
            int gradientColor2 = currentColors.get(Theme.key_chat_wallpaper_gradient_to2, Theme.getColor(Theme.key_chat_wallpaper_gradient_to2));
            int gradientColor3 = currentColors.get(Theme.key_chat_wallpaper_gradient_to3, Theme.getColor(Theme.key_chat_wallpaper_gradient_to3));

            MotionBackgroundDrawable motionDrawable = new MotionBackgroundDrawable();
            motionDrawable.isPreview = preview;
            motionDrawable.setPatternBitmap(chatTheme.getWallpaper(isDark ? 1 : 0).settings.intensity);
            motionDrawable.setColors(backgroundColor, gradientColor1, gradientColor2, gradientColor3, 0,true);
            motionDrawable.setPhase(prevPhase);
            int patternColor = motionDrawable.getPatternColor();
            final boolean isDarkTheme = isDark;
            chatTheme.loadWallpaper(isDark ? 1 : 0, pair -> {
                if (pair == null) {
                    return;
                }
                long themeId = pair.first;
                Bitmap bitmap = pair.second;
                if (themeId == chatTheme.getTlTheme(isDark ? 1 : 0).id && bitmap != null) {
                    int intensity = chatTheme.getWallpaper(isDarkTheme ? 1 : 0).settings.intensity;
                    motionDrawable.setPatternBitmap(intensity, bitmap);
                    motionDrawable.setPatternColorFilter(patternColor);
                    motionDrawable.setPatternAlpha(1f);
                }
            });
            drawable = motionDrawable;
        }
        return drawable;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return wallpaperDrawable == who || super.verifyDrawable(who);
    }

    private CropEditor cropEditorDrawing;
    public void setCropEditorDrawing(CropEditor cropEditor) {
        if (cropEditorDrawing != cropEditor) {
            cropEditorDrawing = cropEditor;
            invalidate();
        }
    }
}
