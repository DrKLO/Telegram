package org.telegram.ui.Stories.recorder;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.C;
import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.VideoEditTextureView;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

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

    private VideoPlayer audioPlayer;

//    private VideoTimelinePlayView videoTimelineView;
    private TimelineView timelineView;

    private final Paint snapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final long MAX_DURATION = 59_500L;
    public static final long MIN_DURATION = 1_000L;

    private final HashMap<Integer, Bitmap> partsBitmap = new HashMap<>();
    private final HashMap<Integer, ButtonBounce> partsBounce = new HashMap<>();

    private final BlurringShader.BlurManager blurManager;

    public PreviewView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);

        this.blurManager = blurManager;

        snapPaint.setStrokeWidth(AndroidUtilities.dp(1));
        snapPaint.setStyle(Paint.Style.STROKE);
        snapPaint.setColor(0xffffffff);
        snapPaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x40000000);
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
            setupParts(null);
            gradientPaint.setShader(null);
            setupAudio((StoryEntry) null, false);
            return;
        }
        if (entry.isVideo) {
            setupImage(entry);
            setupVideoPlayer(entry, whenReady, seekTo);
            if (entry.gradientTopColor != 0 || entry.gradientBottomColor != 0) {
                setupGradient();
            } else {
                entry.setupGradient(this::setupGradient);
            }
        } else {
            setupVideoPlayer(null, whenReady, 0);
            setupImage(entry);
            setupGradient();
        }
        setupParts(entry);
        applyMatrix();
        setupAudio(entry, false);
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

                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });
            audioPlayer.preparePlayer(Uri.fromFile(new File(entry.audioPath)), "other");
            audioPlayer.setVolume(entry.audioVolume);

            if (videoPlayer != null && getDuration() > 0) {
                long startPos = (long) (entry.left * getDuration());
                videoPlayer.seekTo(startPos);
                timelineView.setProgress(startPos);
            }
            updateAudioPlayer(true);
        }
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
                long scrollDuration = Math.min(entry != null && entry.isVideo ? getDuration() : entry.audioDuration, TimelineView.MAX_SCROLL_DURATION);
                entry.audioRight = entry.audioDuration == 0 ? 1 : Math.min(1, Math.min(scrollDuration, TimelineView.MAX_SELECT_DURATION) / (float) entry.audioDuration);
            }
        }
        setupAudio(entry, animated);
    }

    private void seekTo(long position) {
        if (videoPlayer != null) {
            videoPlayer.seekTo(position, false);
        } else if (audioPlayer != null) {
            audioPlayer.seekTo(position, false);
        }
        updateAudioPlayer(true);
    }

    public void setVideoTimelineView(TimelineView timelineView) {
        this.timelineView = timelineView;
        if (timelineView != null) {
            timelineView.setDelegate(new TimelineView.TimelineDelegate() {
                @Override
                public void onProgressDragChange(boolean dragging) {
                    updatePauseReason(-4, dragging);
                }

                @Override
                public void onProgressChange(long progress, boolean fast) {
                    if (!fast) {
                        seekTo(progress);
                    } else if (videoPlayer != null) {
                        videoPlayer.seekTo(progress, true);
                    } else if (audioPlayer != null) {
                        audioPlayer.seekTo(progress, false);
                    }
                }

                @Override
                public void onVideoLeftChange(float left) {
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
                public void onVideoRightChange(float right) {
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
                    if (audioPlayer != null) {
                        audioPlayer.setVolume(volume);
                    }
                }
            });
        }
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
                }, rw, rh, false);
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

    private void setupVideoPlayer(StoryEntry entry, Runnable whenReady, long seekTo) {
        if (entry == null) {
            if (videoPlayer != null) {
                videoPlayer.pause();
                videoPlayer.releasePlayer(true);
                videoPlayer = null;
            }
            if (textureView != null) {
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
                timelineView.setVideo(null, 1);
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
//                    if (whenReadyFinal[0] != null && entry != null && entry.width > 0 && entry.height > 0) {
//                        post(whenReadyFinal[0]);
//                        whenReadyFinal[0] = null;
//                    }
                }

                @Override
                public void onRenderedFirstFrame() {
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
                    } else if (textureView != null) {
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
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

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
            textureView.updateUiBlurManager(blurManager);
            textureView.setAlpha(whenReady == null ? 0f : 1f);
            textureView.setOpaque(false);
            applyMatrix();
            addView(textureView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

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
            videoPlayer.setMute(entry.muted);
            updateAudioPlayer(true);

            timelineView.setVideo(entry.getOriginalFile().getAbsolutePath(), getDuration());
            timelineView.setVideoLeft(entry.left);
            timelineView.setVideoRight(entry.right);
            if (timelineView != null && seekTo > 0) {
                timelineView.setProgress(seekTo);
            }
        }
    }

    public long release() {
        if (audioPlayer != null) {
            audioPlayer.pause();
            audioPlayer.releasePlayer(true);
            audioPlayer = null;
        }
        if (videoPlayer != null) {
            long t = videoPlayer.getCurrentPosition();
            videoPlayer.pause();
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
            return t;
        }
        return 0;
    }

    public void setupParts(StoryEntry entry) {
        if (entry == null) {
            for (Bitmap bitmap : partsBitmap.values()) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            partsBitmap.clear();
            partsBounce.clear();
            return;
        }
        final int rw = getMeasuredWidth() <= 0 ? AndroidUtilities.displaySize.x : getMeasuredWidth();
        final int rh = (int) (rw * 16 / 9f);
        for (int i = 0; i < entry.parts.size(); ++i) {
            StoryEntry.Part part = entry.parts.get(i);
            if (part == null) {
                continue;
            }
            Bitmap bitmap = partsBitmap.get(part.id);
            if (bitmap != null) {
                continue;
            }
            String path = part.file.getPath();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            options.inJustDecodeBounds = false;
            options.inSampleSize = StoryEntry.calculateInSampleSize(options, rw, rh);
            bitmap = BitmapFactory.decodeFile(path, options);
            partsBitmap.put(part.id, bitmap);
        }
        for (Iterator<Map.Entry<Integer, Bitmap>> i = partsBitmap.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<Integer, Bitmap> e = i.next();
            boolean found = false;
            for (int j = 0; j < entry.parts.size(); ++j) {
                if (entry.parts.get(j).id == e.getKey()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                i.remove();
                partsBounce.remove(e.getKey());
            }
        }
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
            } else {
                updateAudioPlayer(pos < lastPos);
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
        if (audioPlayer == null || videoPlayer != null || timelineView == null) {
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

    private void updateAudioPlayer(boolean updateSeek) {
        if (audioPlayer == null || entry == null) {
            return;
        }

        if (videoPlayer == null) {
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

        final long pos = videoPlayer.getCurrentPosition();
        final long duration = (long) ((entry.audioRight - entry.audioLeft) * entry.audioDuration);
        boolean shouldPlaying = videoPlayer.isPlaying() && pos >= entry.audioOffset && pos <= entry.audioOffset + duration;
        long audioPos = pos - entry.audioOffset + (long) (entry.audioLeft * entry.audioDuration);
        if (audioPlayer.isPlaying() != shouldPlaying) {
            audioPlayer.setPlayWhenReady(shouldPlaying);
            audioPlayer.seekTo(audioPos);
        } else if (updateSeek && Math.abs(audioPlayer.getCurrentPosition() - audioPos) > 120) {
            audioPlayer.seekTo(audioPos);
        }
    }

    private Runnable onErrorListener;
    public void whenError(Runnable listener) {
        onErrorListener = listener;
    }

    public void mute(boolean value) {
        if (videoPlayer == null) {
            return;
        }
        videoPlayer.setMute(value);
    }

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
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

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), gradientPaint);
        if (draw && entry != null) {
            float alpha = this.thumbAlpha.set(bitmap != null);
            if (thumbBitmap != null && (1f - alpha) > 0) {
                matrix.set(entry.matrix);
                matrix.preScale((float) entry.width / thumbBitmap.getWidth(), (float) entry.height / thumbBitmap.getHeight());
                matrix.postScale((float) getWidth() / entry.resultWidth, (float) getHeight() / entry.resultHeight);
                bitmapPaint.setAlpha(0xFF);
                canvas.drawBitmap(thumbBitmap, matrix, bitmapPaint);
            }
            if (bitmap != null) {
                matrix.set(entry.matrix);
                matrix.preScale((float) entry.width / bitmap.getWidth(), (float) entry.height / bitmap.getHeight());
                matrix.postScale((float) getWidth() / entry.resultWidth, (float) getHeight() / entry.resultHeight);
                bitmapPaint.setAlpha((int) (0xFF * alpha));
                canvas.drawBitmap(bitmap, matrix, bitmapPaint);
            }
        }
        super.dispatchDraw(canvas);
        if (draw && entry != null) {
            float trash = trashT.set(!inTrash);
            for (int i = 0; i < entry.parts.size(); ++i) {
                StoryEntry.Part part = entry.parts.get(i);
                if (part == null) {
                    continue;
                }
                Bitmap bitmap = partsBitmap.get(part.id);
                if (bitmap == null) {
                    continue;
                }
                float scale = 1f;
                ButtonBounce bounce = partsBounce.get(part.id);
                if (bounce != null) {
                    scale = bounce.getScale(.05f);
                }
                matrix.set(part.matrix);
                canvas.save();
                if (scale != 1) {
                    tempVertices[0] = part.width / 2f;
                    tempVertices[1] = part.height / 2f;
                    matrix.mapPoints(tempVertices);
                    canvas.scale(scale, scale, tempVertices[0] / entry.resultWidth * getWidth(), tempVertices[1] / entry.resultHeight * getHeight());
                }
                if (trashPartIndex == part.id) {
                    float trashScale = AndroidUtilities.lerp(.2f, 1f, trash);
                    canvas.scale(trashScale, trashScale, trashCx, trashCy);
                }
                matrix.preScale((float) part.width / bitmap.getWidth(), (float) part.height / bitmap.getHeight());
                matrix.postScale((float) getWidth() / entry.resultWidth, (float) getHeight() / entry.resultHeight);
                canvas.drawBitmap(bitmap, matrix, bitmapPaint);
                canvas.restore();
            }
        }
    }

    public VideoEditTextureView getTextureView() {
        return textureView;
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

    public void applyMatrix() {
        if (entry == null) {
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
            textureView.setTransform(matrix);
            textureView.invalidate();
        }
        invalidate();
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
    private float[] tempPoint = new float[4];
    private IStoryPart activePart;
    private boolean isPart;
    private float Tx, Ty;
    private boolean activePartPressed;

    private int trashPartIndex;
    private boolean inTrash;
    private AnimatedFloat trashT = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
    private float trashCx, trashCy;

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
            Tx = Ty = 0;
            rotationDiff = 0;
            snappedRotation = false;
            snappedToCenterAndScaled = false;
            doNotSpanRotation = false;
            activePart = findPartAt(touch.x, touch.y);
            if (isPart = (activePart instanceof StoryEntry.Part)) {
                entry.parts.remove(activePart);
                entry.parts.add((StoryEntry.Part) activePart);
                trashPartIndex = activePart.id;
                invalidate();
                allowWithSingleTouch = true;
                ButtonBounce bounce = partsBounce.get(activePart.id);
                if (bounce == null) {
                    partsBounce.put(activePart.id, bounce = new ButtonBounce(this));
                }
                bounce.setPressed(true);
                activePartPressed = true;
                onEntityDragStart();
                onEntityDraggedTop(false);
                onEntityDraggedBottom(false);
            } else {
                trashPartIndex = -1;
            }
            touchMatrix.set(activePart.matrix);
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && activePart != null) {
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
                        try {
                            performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {}
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
                Tx += (tx - ltx);
                Ty += (ty - lty);
            }
            if (activePartPressed && MathUtils.distance(0, 0, Tx, Ty) > AndroidUtilities.touchSlop) {
                ButtonBounce bounce = partsBounce.get(activePart.id);
                if (bounce != null) {
                    bounce.setPressed(false);
                }
                activePartPressed = false;
            }
            finalMatrix.set(touchMatrix);
            matrix.set(touchMatrix);
            extractPointsData(matrix);
            float rotDiff = Math.round(angle / 90f) * 90f - angle;
            if (allowRotation && !doNotSpanRotation) {
                if (Math.abs(rotDiff) < 3.5f) {
                    finalMatrix.postRotate(rotDiff, cx, cy);
                    if (!snappedRotation) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {}
                        snappedRotation = true;
                    }
                } else {
                    snappedRotation = false;
                }
            }
            boolean trash = isPart && MathUtils.distance(touch.x, touch.y, getWidth() / 2f, getHeight() - AndroidUtilities.dp(76)) < AndroidUtilities.dp(35);
            if (trash != inTrash) {
                onEntityDragTrash(trash);
                inTrash = trash;
            }
            if (trash) {
                trashCx = touch.x;
                trashCy = touch.y;
            }
            if (isPart) {
                onEntityDraggedTop(cy - h / 2f < AndroidUtilities.dp(66) / (float) getHeight() * entry.resultHeight);
                onEntityDraggedBottom(cy + h / 2f > entry.resultHeight - AndroidUtilities.dp(64 + 50) / (float) getHeight() * entry.resultHeight);
            }

            activePart.matrix.set(finalMatrix);
            entry.editedMedia = true;
            applyMatrix();
            invalidate();
        }
        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            Tx = Ty = 0;
            if (!(activePart instanceof StoryEntry.Part) || ev.getPointerCount() <= 1) {
                ButtonBounce bounce = partsBounce.get(activePart.id);
                if (bounce != null) {
                    bounce.setPressed(false);
                }
                activePartPressed = false;
                allowWithSingleTouch = false;
                onEntityDragEnd(inTrash && ev.getAction() == MotionEvent.ACTION_UP);
                activePart = null;
                inTrash = false;
                onEntityDraggedTop(false);
                onEntityDraggedBottom(false);
            }
            isPart = false;
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

    public void deleteCurrentPart() {
        if (activePart != null) {
            entry.parts.remove(activePart);
            setupParts(entry);
        }
    }

    private Matrix tempMatrix;
    private float[] tempVertices = new float[2];
    private IStoryPart findPartAt(float x, float y) {
        for (int i = entry.parts.size() - 1; i >= 0; --i) {
            IStoryPart part = entry.parts.get(i);
            tempVertices[0] = x / getWidth() * entry.resultWidth;
            tempVertices[1] = y / getHeight() * entry.resultHeight;
            if (tempMatrix == null) {
                tempMatrix = new Matrix();
            }
            part.matrix.invert(tempMatrix);
            tempMatrix.mapPoints(tempVertices);
            if (tempVertices[0] >= 0 && tempVertices[0] <= part.width && tempVertices[1] >= 0 && tempVertices[1] <= part.height) {
                return part;
            }
        }
        return entry;
    }

    private long tapTime;
    private float tapX, tapY;
    private boolean tapTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            tapTime = System.currentTimeMillis();
            tapX = ev.getX();
            tapY = ev.getY();
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
        if (!(activePart instanceof StoryEntry.Part)) {
            result = additionalTouchEvent(ev) || result;
            tapTouchEvent(ev);
        }
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
        updateAudioPlayer(true);
    }

    // ignores actual player and other reasons to pause a video
    // (so that play button doesn't make it play when f.ex. popup is on)
    public boolean isPlaying() {
        return !pauseLinks.contains(-9982);
    }
    public void play(boolean play) {
        updatePauseReason(-9982, !play);
    }
}
