package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.Scroller;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Stories.StoriesViewPager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

public class TimelineView extends View {

    // milliseconds when timeline goes out of the box
    public long getMaxScrollDuration() {
        if (collageTracks.isEmpty()) {
            return 120 * 1000L;
        } else {
            return 70 * 1000L;
        }
    }
    // minimal allowed duration to select
    public static final long MIN_SELECT_DURATION = 1 * 1000L;
    // maximum allowed duration to select
    public static final long MAX_SELECT_DURATION = (long) (59 * 1000L);

    interface TimelineDelegate {
        default void onProgressDragChange(boolean dragging) {};
        default void onProgressChange(long progress, boolean fast) {};

        default void onVideoLeftChange(float left) {};
        default void onVideoRightChange(float right) {};
        default void onVideoVolumeChange(float volume) {};

        default void onVideoLeftChange(int i, float left) {};
        default void onVideoRightChange(int i, float right) {};
        default void onVideoVolumeChange(int i, float volume) {};
        default void onVideoOffsetChange(int i, long offset) {};
        default void onVideoSelected(int i) {};

        default void onAudioOffsetChange(long offset) {};
        default void onAudioLeftChange(float left) {};
        default void onAudioRightChange(float right) {};
        default void onAudioVolumeChange(float volume) {};
        default void onAudioRemove() {};

        default void onRoundOffsetChange(long offset) {};
        default void onRoundLeftChange(float left) {};
        default void onRoundRightChange(float right) {};
        default void onRoundVolumeChange(float volume) {};
        default void onRoundRemove() {};

        default void onRoundSelectChange(boolean selected) {};
    }

    private TimelineDelegate delegate;
    private Runnable onTimelineClick;
    public void setOnTimelineClick(Runnable click) {
        this.onTimelineClick = click;
    }

    private long progress;
    private long scroll;

    private class Track {
        int index;
        boolean isRound;
        VideoThumbsLoader thumbs;
        String path;
        long duration;
        long offset;
        float left, right;
        float volume;

        final RectF bounds = new RectF();

        private final AnimatedFloat selectedT = new AnimatedFloat(TimelineView.this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);

        private void setupThumbs(boolean force) {
            if (getMeasuredWidth() <= 0 || thumbs != null && !force) {
                return;
            }
            if (thumbs != null) {
                thumbs.destroy();
                thumbs = null;
            }
            thumbs = new VideoThumbsLoader(isRound, path, w - px - px, dp(38), duration > 2 ? duration : null, getMaxScrollDuration(), coverStart, coverEnd, () -> {
                if (thumbs != null && thumbs.getDuration() > 0) {
                    duration = thumbs.getDuration();
                    sortCollage();
                }
            });
        }

        private void setupWaveform(boolean force) {
            if (index < 0 || index >= collageWaveforms.size())
                return;
            AudioWaveformLoader waveform = collageWaveforms.get(index);
            if (getMeasuredWidth() <= 0 || waveform != null && !force) {
                return;
            }
            if (waveform != null) {
                waveform.destroy();
                waveform = null;
            }
            waveform = new AudioWaveformLoader(path, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            collageWaveforms.set(index, waveform);
        }
    }

    private Track videoTrack;

    private int collageSelected = 0;
    private final ArrayList<AudioWaveformLoader> collageWaveforms = new ArrayList<>();
    private final ArrayList<Track> collageTracks = new ArrayList<>();
    private Track collageMain;
    private final Paint collageFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path collageClipPath = new Path();
    private final Path selectedCollageClipPath = new Path();

    private boolean hasRound;
    private String roundPath;
    private boolean roundSelected;
    private long roundDuration;
    private long roundOffset;
    private float roundLeft;
    private float roundRight;
    private float roundVolume;
    private VideoThumbsLoader roundThumbs;

    private boolean hasAudio;
    private String audioPath;
    private boolean audioSelected;
    private long audioOffset;
    private long audioDuration;
    private float audioLeft;
    private float audioRight;
    private boolean waveformIsLoaded;
    private float audioVolume;
    private boolean resetWaveform;
    private AudioWaveformLoader waveform;

    private long getBaseDuration() {
        if (videoTrack != null) {
            return Math.max(1, videoTrack.duration);
        }
        if (collageMain != null) {
            return Math.max(1, collageMain.duration);
        }
        if (hasRound) {
            return Math.max(1, roundDuration);
        }
        return Math.max(1, audioDuration);
    }

    private final AnimatedFloat roundT = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat roundSelectedT = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final AnimatedFloat audioT = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat audioSelectedT = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final AnimatedFloat waveformMax = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final AnimatedFloat timelineWaveformLoaded = new AnimatedFloat(this, 0, 600, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat timelineWaveformMax = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat openT = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    public boolean open = true;
    public void setOpen(boolean open, boolean animated) {
        if (this.open == open && animated) return;
        this.open = open;
        if (!animated) {
            openT.set(open, true);
        }
        invalidate();
    }

    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer backgroundBlur;
    private final BlurringShader.StoryBlurDrawer audioBlur;
    private final BlurringShader.StoryBlurDrawer audioWaveformBlur;

    private final RectF timelineBounds = new RectF();
    private final Path timelineClipPath = new Path();
    private final Text timelineText;
    private final Drawable timelineIcon;
    private final WaveformPath timelineWaveformPath = new WaveformPath();

    private final RectF videoBounds = new RectF();
    private final Paint videoFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path videoClipPath = new Path();
    private final Path selectedVideoClipPath = new Path();

    private final RectF roundBounds = new RectF();
    private final Path roundClipPath = new Path();

    private final Paint regionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint regionCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint regionHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF audioBounds = new RectF();
    private final Path audioClipPath = new Path();
    private final Paint waveformPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WaveformPath waveformPath = new WaveformPath();

    private final Paint audioDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable audioIcon;
    private final TextPaint audioAuthorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout audioAuthor;
    private float audioAuthorWidth, audioAuthorLeft;
    private final TextPaint audioTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout audioTitle;
    private float audioTitleWidth, audioTitleLeft;

    private final LinearGradient ellipsizeGradient = new LinearGradient(0, 0, 16, 0, new int[] { 0x00ffffff, 0xffffffff }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
    private final Matrix ellipsizeMatrix = new Matrix();
    private final Paint ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Scroller scroller = new Scroller(getContext());

    private final ViewGroup container;
    private final View previewContainer;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean isCover;
    public void setCover() {
        isCover = true;
    }

    private final Runnable onLongPress;

    public TimelineView(Context context, ViewGroup container, View previewContainer, Theme.ResourcesProvider resourcesProvider, BlurringShader.BlurManager blurManager) {
        super(context);

        this.container = container;
        this.previewContainer = previewContainer;
        this.resourcesProvider = resourcesProvider;

        audioDotPaint.setColor(0x7fffffff);
        audioAuthorPaint.setTextSize(dp(12));
        audioAuthorPaint.setTypeface(AndroidUtilities.bold());
        audioAuthorPaint.setColor(0xffffffff);
        audioTitlePaint.setTextSize(dp(12));
        audioTitlePaint.setColor(0xffffffff);
        waveformPaint.setColor(0x40ffffff);

        ellipsizePaint.setShader(ellipsizeGradient);
        ellipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        regionPaint.setColor(0xffffffff);
        regionPaint.setShadowLayer(dp(1), 0, dp(1), 0x1a000000);
        regionCutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        regionHandlePaint.setColor(0xff000000);
        progressWhitePaint.setColor(0xffffffff);
        progressShadowPaint.setColor(0x26000000);

        timelineText = new Text(LocaleController.getString(R.string.StoryTimeline), 12, AndroidUtilities.bold());
        timelineIcon = getContext().getResources().getDrawable(R.drawable.timeline).mutate();
        timelineIcon.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));

        audioIcon = getContext().getResources().getDrawable(R.drawable.filled_widget_music).mutate();
        audioIcon.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));

        this.blurManager = blurManager;
        backgroundBlur =    new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_BACKGROUND);
        audioBlur =         new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_AUDIO_BACKGROUND);
        audioWaveformBlur = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_AUDIO_WAVEFORM_BACKGROUND);

        onLongPress = () -> {
            if (pressType == 2 && hasAudio) {
                SliderView slider =
                    new SliderView(getContext(), SliderView.TYPE_VOLUME)
                        .setMinMax(0, 1.5f)
                        .setValue(audioVolume)
                        .setOnValueChange(volume -> {
                            audioVolume = volume;
                            if (delegate != null) {
                                delegate.onAudioVolumeChange(volume);
                            }
                        });
                final long videoScrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());
                final float uiRight = Math.min(w - px - ph, px + ph + (audioOffset - scroll + lerp(audioRight, 1, audioSelectedT.get()) * audioDuration) / (float) videoScrollDuration * sw);
                ItemOptions itemOptions = ItemOptions.makeOptions(container, resourcesProvider, this)
                    .addView(slider)
                    .addSpaceGap()
                    .add(R.drawable.msg_delete, LocaleController.getString(R.string.StoryAudioRemove), () -> {
                        if (delegate != null) {
                            delegate.onAudioRemove();
                        }
                    })
                    .setGravity(Gravity.RIGHT)
                    .forceTop(true)
                    .translate(-(w - uiRight) + dp(18), audioBounds.top)
                    .show();
                itemOptions.setBlurBackground(blurManager, -previewContainer.getX(), -previewContainer.getY());

                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception e) {}
            } else if (pressType == 1 && hasRound) {
                SliderView slider =
                    new SliderView(getContext(), SliderView.TYPE_VOLUME)
                        .setMinMax(0, 1.5f)
                        .setValue(roundVolume)
                        .setOnValueChange(volume -> {
                            roundVolume = volume;
                            if (delegate != null) {
                                delegate.onRoundVolumeChange(volume);
                            }
                        });
                final long videoScrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());
                final float uiRight = Math.min(w - px - ph, px + ph + (roundOffset - scroll + lerp(roundRight, 1, roundSelectedT.get()) * roundDuration) / (float) videoScrollDuration * sw);
                ItemOptions itemOptions = ItemOptions.makeOptions(container, resourcesProvider, this)
                    .addView(slider)
                    .addSpaceGap()
                    .add(R.drawable.msg_delete, LocaleController.getString(R.string.StoryRoundRemove), () -> {
                        if (delegate != null) {
                            delegate.onRoundRemove();
                        }
                    })
                    .setGravity(Gravity.RIGHT)
                    .forceTop(true)
                    .translate(-(w - uiRight) + dp(18), roundBounds.top)
                    .show();
                itemOptions.setBlurBackground(blurManager, -previewContainer.getX(), -previewContainer.getY());

                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception e) {}
            } else if (pressType == 0 && videoTrack != null) {
                SliderView slider =
                    new SliderView(getContext(), SliderView.TYPE_VOLUME)
                        .setMinMax(0, 1.5f)
                        .setValue(videoTrack.volume)
                        .setOnValueChange(volume -> {
                            videoTrack.volume = volume;
                            if (delegate != null) {
                                delegate.onVideoVolumeChange(volume);
                            }
                        });
                ItemOptions itemOptions = ItemOptions.makeOptions(container, resourcesProvider, this)
                    .addView(slider)
                    .setGravity(Gravity.RIGHT)
                    .forceTop(true)
                    .translate(dp(18), videoBounds.top)
                    .show();
                itemOptions.setBlurBackground(blurManager, -previewContainer.getX(), -previewContainer.getY());
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception e) {}
            } else if (pressType == 3 && pressCollageIndex >= 0 && pressCollageIndex < collageTracks.size()) {
                final Track track = collageTracks.get(pressCollageIndex);
                SliderView slider =
                    new SliderView(getContext(), SliderView.TYPE_VOLUME)
                        .setMinMax(0, 1.5f)
                        .setValue(track.volume)
                        .setOnValueChange(volume -> {
                            track.volume = volume;
                            if (delegate != null) {
                                delegate.onVideoVolumeChange(track.index, volume);
                            }
                        });
                ItemOptions itemOptions = ItemOptions.makeOptions(container, resourcesProvider, this)
                        .addView(slider)
                        .setGravity(Gravity.RIGHT)
                        .forceTop(true)
                        .translate(dp(18), track.bounds.top)
                        .show();
                itemOptions.setBlurBackground(blurManager, -previewContainer.getX(), -previewContainer.getY());
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception e) {}
            }
        };
    }

    public void setDelegate(TimelineDelegate delegate) {
        this.delegate = delegate;
    }

    private long coverStart = -1, coverEnd = -1;
    public void setCoverVideo(long videoStart, long videoEnd) {
        coverStart = videoStart;
        coverEnd = videoEnd;
        if (videoTrack != null) {
            videoTrack.setupThumbs(true);
        }
    }

    public void setVideo(boolean isRound, String videoPath, long videoDuration, float videoVolume) {
        if (TextUtils.equals(videoTrack == null ? null : videoTrack.path, videoPath)) {
            return;
        }
        if (videoTrack != null) {
            if (videoTrack.thumbs != null) {
                videoTrack.thumbs.destroy();
                videoTrack.thumbs = null;
            }
            videoTrack = null;
        }
        if (videoPath != null) {
            scroll = 0;
            videoTrack = new Track();
            videoTrack.isRound = isRound;
            videoTrack.path = videoPath;
            videoTrack.duration = videoDuration;
            videoTrack.volume = videoVolume;
            videoTrack.setupThumbs(false);
        } else {
            videoTrack = null;
            scroll = 0;
        }
        if (!hasRound) {
            roundSelected = false;
        }
        progress = 0;
        invalidate();
    }

    public void setCollage(ArrayList<StoryEntry> entries) {
        for (int i = 0; i < collageTracks.size(); ++i) {
            Track track = collageTracks.get(i);
            if (track != null && track.thumbs != null) {
                track.thumbs.destroy();
            }
        }
        collageTracks.clear();
        for (int i = 0; i < collageWaveforms.size(); ++i) {
            AudioWaveformLoader waveform = collageWaveforms.get(i);
            if (waveform != null) {
                waveform.destroy();
            }
        }
        collageWaveforms.clear();
        timelineWaveformMax.set(1, true);
        if (entries != null) {
            for (int i = 0; i < entries.size(); ++i) {
                collageWaveforms.add(null);
                StoryEntry entry = entries.get(i);
                if (entry.isVideo) {
                    final Track track = new Track();
                    track.index = i;
                    track.isRound = false;
                    track.path = entry.file.getAbsolutePath();
                    track.duration = entry.duration;
                    track.offset = entry.videoOffset;
                    track.volume = entry.videoVolume;
                    track.left = entry.videoLeft;
                    track.right = entry.videoRight;
                    track.setupThumbs(false);
                    track.setupWaveform(false);
                    collageTracks.add(track);
                }
            }
        }
        sortCollage();
        collageSelected = 0;
    }

    public void sortCollage() {
        Collections.sort(collageTracks, (a, b) -> (int) (b.duration - a.duration));
        collageMain = collageTracks.isEmpty() ? null : collageTracks.get(0);
        if (collageMain != null && collageMain.offset != 0) {
//            collageMain.offset = 0;
//            if (delegate != null) {
//                delegate.onVideoOffsetChange(collageMain.index, 0);
//            }
        }
    }

    public void setRoundNull(boolean animated) {
        setRound(null, 0, 0, 0, 0, 0, animated);
    }

    public void setRound(String roundPath, long roundDuration, long offset, float left, float right, float volume, boolean animated) {
        if (TextUtils.equals(this.roundPath, roundPath)) {
            return;
        }
        if (roundThumbs != null) {
            roundThumbs.destroy();
            roundThumbs = null;
        }
        final long hadRoundDuration = this.roundDuration;
        if (roundPath != null) {
            this.roundPath = roundPath;
            this.roundDuration = roundDuration;
            this.roundOffset = offset - (long) (left * roundDuration);
            this.roundLeft = left;
            this.roundRight = right;
            this.roundVolume = volume;
            setupRoundThumbs();
            if (videoTrack == null) {
                audioSelected = false;
                roundSelected = true;
            }
        } else {
            this.roundPath = null;
            this.roundDuration = 1;
            roundSelected = false;
        }
        hasRound = this.roundPath != null;
        if (hadRoundDuration != roundDuration && videoTrack == null && waveform != null) {
            resetWaveform = true;
            setupAudioWaveform();
        }
        if (hasAudio && hasRound && videoTrack == null) {
            audioLeft = 0;
            audioRight = Utilities.clamp((float) roundDuration / audioDuration, 1, 0);
        }
        if (!animated) {
            roundSelectedT.set(roundSelected, true);
            audioSelectedT.set(audioSelected, true);
            roundT.set(hasRound, true);
        }
        invalidate();
    }

    public void selectRound(boolean select) {
        if (select && hasRound) {
            roundSelected = true;
            audioSelected = false;
        } else {
            roundSelected = false;
            audioSelected = hasAudio && videoTrack == null;
        }
        invalidate();
    }

    private void setupRoundThumbs() {
        if (getMeasuredWidth() <= 0 || this.roundThumbs != null || videoTrack != null && videoTrack.duration < 1) {
            return;
        }
        this.roundThumbs = new VideoThumbsLoader(false, roundPath, w - px - px, dp(38), roundDuration > 2 ? roundDuration : null, videoTrack != null ? videoTrack.duration : getMaxScrollDuration(), -1, -1, () -> {
            if (this.roundThumbs != null && this.roundThumbs.getDuration() > 0) {
                roundDuration = this.roundThumbs.getDuration();
            }
        });
    }

    private final AnimatedFloat loopProgress = new AnimatedFloat(0, this, 0, 340, CubicBezierInterpolator.EASE_OUT_QUINT);
    private long loopProgressFrom = -1;
    public void setProgress(long progress) {
        if (
            videoTrack != null && progress < this.progress && progress <= videoTrack.duration * videoTrack.left + 240 && this.progress + 240 >= videoTrack.duration * videoTrack.right ||
            hasAudio && !hasRound && videoTrack == null && progress < this.progress && progress <= audioDuration * audioLeft + 240 && this.progress + 240 >= audioDuration * audioRight ||
            hasRound && videoTrack == null && progress < this.progress && progress <= roundDuration * audioLeft + 240 && this.progress + 240 >= roundDuration * audioRight
        ) {
            loopProgressFrom = -1;
            loopProgress.set(1, true);
        }
        this.progress = progress;
        invalidate();
    }

    public void setVideoLeft(float left) {
        if (videoTrack == null) return;
        videoTrack.left = left;
        invalidate();
    }

    public void setVideoRight(float right) {
        if (videoTrack == null) return;
        videoTrack.right = right;
        invalidate();
    }

    public void setAudio(String audioPath, String audioAuthorText, String audioTitleText, long duration, long offset, float left, float right, float volume, boolean animated) {
        if (!TextUtils.equals(this.audioPath, audioPath)) {
            if (waveform != null) {
                waveform.destroy();
                waveform = null;
                waveformIsLoaded = false;
            }
            this.audioPath = audioPath;
            setupAudioWaveform();
        }
        this.audioPath = audioPath;
        hasAudio = !TextUtils.isEmpty(audioPath);
        if (!hasAudio) {
            audioSelected = false;
            audioAuthorText = null;
            audioTitleText = null;
        }
        if (TextUtils.isEmpty(audioAuthorText)) {
            audioAuthorText = null;
        }
        if (TextUtils.isEmpty(audioTitleText)) {
            audioTitleText = null;
        }
        if (hasAudio) {
            audioDuration = duration;
            audioOffset = offset - (long) (left * duration);
            audioLeft = left;
            audioRight = right;
            audioVolume = volume;
            if (audioAuthorText != null) {
                audioAuthor = new StaticLayout(audioAuthorText, audioAuthorPaint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                audioAuthorWidth = audioAuthor.getLineCount() > 0 ? audioAuthor.getLineWidth(0) : 0;
                audioAuthorLeft  = audioAuthor.getLineCount() > 0 ? audioAuthor.getLineLeft(0) : 0;
            } else {
                audioAuthorWidth = 0;
                audioAuthor = null;
            }
            if (audioTitleText != null) {
                audioTitle = new StaticLayout(audioTitleText, audioTitlePaint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                audioTitleWidth = audioTitle.getLineCount() > 0 ? audioTitle.getLineWidth(0) : 0;
                audioTitleLeft  = audioTitle.getLineCount() > 0 ? audioTitle.getLineLeft(0) : 0;
            } else {
                audioTitleWidth = 0;
                audioTitle = null;
            }
        }
        if (!animated) {
            audioT.set(hasAudio, true);
        }
        invalidate();
    }

    private void setupAudioWaveform() {
        if (getMeasuredWidth() <= 0 || this.waveform != null && !resetWaveform) {
            return;
        }
        this.waveform = new AudioWaveformLoader(audioPath, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        waveformIsLoaded = false;
        waveformMax.set(1, true);
    }

    private static final int HANDLE_PROGRESS = 0;

    private static final int HANDLE_VIDEO_SCROLL = 1;
    private static final int HANDLE_VIDEO_LEFT = 2;
    private static final int HANDLE_VIDEO_RIGHT = 3;
    private static final int HANDLE_VIDEO_REGION = 4;

    private static final int HANDLE_AUDIO_SCROLL = 5;
    private static final int HANDLE_AUDIO_LEFT = 6;
    private static final int HANDLE_AUDIO_RIGHT = 7;
    private static final int HANDLE_AUDIO_REGION = 8;

    private static final int HANDLE_ROUND_SCROLL = 9;
    private static final int HANDLE_ROUND_LEFT = 10;
    private static final int HANDLE_ROUND_RIGHT = 11;
    private static final int HANDLE_ROUND_REGION = 12;

    private static final int HANDLE_COLLAGE_LEFT = 13;
    private static final int HANDLE_COLLAGE_RIGHT = 14;
    private static final int HANDLE_COLLAGE_REGION = 15;
    private static final int HANDLE_COLLAGE_SCROLL = 16;

    private int detectHandle(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        final long scrollWidth = Math.min(getBaseDuration(), getMaxScrollDuration());
        final float progressT = (Utilities.clamp(progress, getBaseDuration(), 0) + (collageMain != null ? collageMain.offset + collageMain.left * collageMain.duration : (videoTrack == null ? audioOffset : 0)) - scroll) / (float) scrollWidth;
        final float progressX = px + ph + sw * progressT;
        if (!isCover && x >= progressX - dp(12) && x <= progressX + dp(12)) {
            return HANDLE_PROGRESS;
        }

        final boolean isInVideo = videoTrack != null && y > h - py - getVideoHeight() - dp(2);
        final boolean isInCollage = !collageTracks.isEmpty() && y > h - py - getVideoHeight() - dp(4) - getCollageHeight() - dp(4) - dp(2) && y < h - py - getVideoHeight() - dp(2);
        final boolean isInRound = hasRound && y > h - py - getVideoHeight() - dp(4) - getCollageHeight() - dp(collageTracks.isEmpty() ? 0 : 4) - getRoundHeight() - dp(4) - dp(2) && y < h - py - getVideoHeight() - dp(2) - getCollageHeight() - dp(collageTracks.isEmpty() ? 0 : 4);

        if (isInCollage) {
            for (int i = 0; i < collageTracks.size(); ++i) {
                Track track = collageTracks.get(i);
                AndroidUtilities.rectTmp.set(track.bounds);
                AndroidUtilities.rectTmp.inset(-dp(2), -dp(2));
                if (AndroidUtilities.rectTmp.contains(x, y)) {
                    float startX = px + ph + (track.offset) / (float) scrollWidth * sw;
                    float leftX = px + ph + (track.offset + track.left * track.duration) / (float) scrollWidth * sw;
                    float rightX = px + ph + (track.offset + track.right * track.duration) / (float) scrollWidth * sw;
                    float endX = px + ph + (track.offset + track.duration) / (float) scrollWidth * sw;

                    pressHandleCollageIndex = i;
                    if (x >= leftX - dp(10 + 5) && x <= leftX + dp(5)) {
                        return HANDLE_COLLAGE_LEFT;
                    } else if (x >= rightX - dp(5) && x <= rightX + dp(10 + 5)) {
                        return HANDLE_COLLAGE_RIGHT;
                    } else if (x >= leftX && x <= rightX && (track.left > 0.01f || track.right < .99f)) {
                        return HANDLE_COLLAGE_REGION;
                    } else if (x >= startX && x <= endX) {
                        return HANDLE_COLLAGE_SCROLL;
                    } else {
                        return -1;
                    }
                }
            }
        } else if (isInVideo) {
            if (isCover) {
                return HANDLE_VIDEO_REGION;
            }

            final float leftX = px + ph + (videoTrack.left * videoTrack.duration - scroll) / (float) scrollWidth * sw;
            final float rightX = px + ph + (videoTrack.right * videoTrack.duration - scroll) / (float) scrollWidth * sw;

            if (x >= leftX - dp(10 + 5) && x <= leftX + dp(5)) {
                return HANDLE_VIDEO_LEFT;
            } else if (x >= rightX - dp(5) && x <= rightX + dp(10 + 5)) {
                return HANDLE_VIDEO_RIGHT;
            } else if (x >= leftX && x <= rightX && (videoTrack.left > 0.01f || videoTrack.right < .99f)) {
                return HANDLE_VIDEO_REGION;
            }
        } else if (isInRound) {
            float leftX = px + ph + (roundOffset + roundLeft * roundDuration - scroll) / (float) scrollWidth * sw;
            float rightX = px + ph + (roundOffset + roundRight * roundDuration - scroll) / (float) scrollWidth * sw;
            if (roundSelected || videoTrack == null) {
                if (x >= leftX - dp(10 + 5) && x <= leftX + dp(5)) {
                    return HANDLE_ROUND_LEFT;
                } else if (x >= rightX - dp(5) && x <= rightX + dp(10 + 5)) {
                    return HANDLE_ROUND_RIGHT;
                } else if (x >= leftX && x <= rightX) {
                    if (videoTrack == null) {
                        return HANDLE_ROUND_REGION;
                    } else {
                        return HANDLE_ROUND_SCROLL;
                    }
                }
                leftX = px + ph + (roundOffset - scroll) / (float) scrollWidth * sw;
                rightX = px + ph + (roundOffset + roundDuration - scroll) / (float) scrollWidth * sw;
            }
            if (x >= leftX && x <= rightX) {
                return HANDLE_ROUND_SCROLL;
            }
        } else if (hasAudio) {
            float leftX = px + ph + (audioOffset + audioLeft * audioDuration - scroll) / (float) scrollWidth * sw;
            float rightX = px + ph + (audioOffset + audioRight * audioDuration - scroll) / (float) scrollWidth * sw;
            if (audioSelected || videoTrack == null && !hasRound) {
                if (x >= leftX - dp(10 + 5) && x <= leftX + dp(5)) {
                    return HANDLE_AUDIO_LEFT;
                } else if (x >= rightX - dp(5) && x <= rightX + dp(10 + 5)) {
                    return HANDLE_AUDIO_RIGHT;
                } else if (x >= leftX && x <= rightX) {
                    if (videoTrack == null) {
                        return HANDLE_AUDIO_REGION;
                    } else {
                        return HANDLE_AUDIO_SCROLL;
                    }
                }
                leftX = px + ph + (audioOffset - scroll) / (float) scrollWidth * sw;
                rightX = px + ph + (audioOffset + audioDuration - scroll) / (float) scrollWidth * sw;
            }
            if (x >= leftX && x <= rightX) {
                return HANDLE_AUDIO_SCROLL;
            }
        }

        if (videoTrack != null && videoTrack.duration > getMaxScrollDuration() && isInVideo) {
            return HANDLE_VIDEO_SCROLL;
        }

        return -1;
    }

    public boolean onBackPressed() {
        if (audioSelected) {
            audioSelected = false;
            if (hasRound && videoTrack == null) {
                roundSelected = true;
                if (delegate != null) {
                    delegate.onRoundSelectChange(true);
                }
            }
            return true;
        }
        return false;
    }

    public boolean isDragging() {
        return dragged;
    }

    private Runnable askExactSeek;
    private boolean setProgressAt(float x, boolean fast) {
        if (videoTrack == null && !hasAudio && collageTracks.isEmpty()) {
            return false;
        }

        final long scrollWidth = Math.min(getBaseDuration(), getMaxScrollDuration());
        final float t = (x - px - ph) / sw;
        long offset = 0;
        if (collageMain != null) {
            offset = (long) (collageMain.offset + collageMain.left * collageMain.duration);
        }
        long progress = (long) Utilities.clamp(t * scrollWidth - (collageMain != null ? offset : (videoTrack == null ? audioOffset : 0)) + scroll, getBaseDuration(), 0);
        if (videoTrack != null && (progress / (float) videoTrack.duration < videoTrack.left || progress / (float) videoTrack.duration > videoTrack.right)) {
            return false;
        }
        if (collageMain != null && (progress < 0 || progress >= (long) ((collageMain.right - collageMain.left) * collageMain.duration))) {
            return false;
        }
        if (hasAudio && videoTrack == null && collageTracks.isEmpty() && (progress / (float) audioDuration < audioLeft || progress / (float) audioDuration > audioRight)) {
            return false;
        }
        this.progress = progress;
        invalidate();
        if (delegate != null) {
            delegate.onProgressChange(progress, fast);
        }
        if (askExactSeek != null) {
            AndroidUtilities.cancelRunOnUIThread(askExactSeek);
            askExactSeek = null;
        }
        if (fast) {
            AndroidUtilities.runOnUIThread(askExactSeek = () -> {
                if (delegate != null) {
                    delegate.onProgressChange(progress, false);
                }
            }, 150);
        }
        return true;
    }

    private float getVideoHeight() {
        if (videoTrack == null)
            return 0;
        float videoSelected = videoTrack.selectedT.get();
        return lerp(dp(28), dp(38), videoSelected);
    }

    private float getCollageHeight() {
        if (collageTracks.isEmpty())
            return 0;
        float h = 0;
        for (int i = 0; i < collageTracks.size(); ++i) {
            if (h > 0) {
                h += dp(4);
            }
            float selected = collageTracks.get(i).selectedT.get();
            h += lerp(dp(28), dp(38), selected);
        }
        return h;
    }

    private float getAudioHeight() {
        float audioSelected = this.audioSelectedT.set(this.audioSelected);
        return lerp(dp(28), dp(38), audioSelected);
    }

    private float getRoundHeight() {
        if (!hasRound)
            return 0;
        float roundSelected = this.roundSelectedT.set(this.roundSelected);
        return lerp(dp(28), dp(38), roundSelected);
    }

    private long lastTime;
    private long pressTime;
    private float lastX;
    private int pressHandle = -1;
    private int pressHandleCollageIndex = -1;
    private int pressType = -1;
    private int pressCollageIndex = -1;
    private boolean draggingProgress, dragged;
    private boolean hadDragChange;
    private VelocityTracker velocityTracker;
    private boolean scrollingVideo = true;
    private int scrollingCollage = -1;
    private boolean scrolling = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (videoTrack == null && collageTracks.isEmpty() && !hasAudio && !hasRound) {
            return false;
        }

        final float top = h - getTimelineHeight();
        if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
            return false;
        }

        final long now = System.currentTimeMillis();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (askExactSeek != null) {
                AndroidUtilities.cancelRunOnUIThread(askExactSeek);
                askExactSeek = null;
            }
            scroller.abortAnimation();
            pressHandleCollageIndex = -1;
            pressHandle = detectHandle(event);
            pressType = -1;
            pressCollageIndex = -1;
            int y = h - py;
            if (pressType == -1 && !open && timelineBounds.contains(event.getX(), event.getY())) {
                pressType = 10;
                pressHandle = -1;
            }
            if (pressType == -1 && videoTrack != null) {
                if (event.getY() < y && event.getY() > y - getVideoHeight() - dp(2)) {
                    pressType = 0;
                }
                y -= getVideoHeight() + dp(4);
            }
            if (pressType == -1 && !collageTracks.isEmpty()) {
                for (int i = 0; i < collageTracks.size(); ++i) {
                    final Track track = collageTracks.get(i);
                    final float selected = track.selectedT.get();
                    final float h = lerp(dp(28), dp(38), selected);
                    if (event.getY() < y && event.getY() > y - h - dp(2)) {
                        pressType = 3;
                        pressCollageIndex = i; break;
                    }
                    y -= h + dp(4);
                }
            }
            if (pressType == -1 && hasRound) {
                if (event.getY() < y && event.getY() > y - getRoundHeight() - dp(2)) {
                    pressType = 1;
                }
                y -= getRoundHeight() + dp(4);
            }
            if (pressType == -1 && hasAudio) {
                if (event.getY() < y && event.getY() > y - getAudioHeight() - dp(2)) {
                    pressType = 2;
                }
                y -= getAudioHeight() + dp(4);
            }
            pressTime = System.currentTimeMillis();
            draggingProgress = pressHandle == HANDLE_PROGRESS || pressHandle == -1 || pressHandle == HANDLE_VIDEO_SCROLL;
            hadDragChange = false;
            if (pressHandle == HANDLE_VIDEO_SCROLL || pressHandle == HANDLE_AUDIO_SCROLL || pressHandle == HANDLE_AUDIO_REGION) {
                velocityTracker = VelocityTracker.obtain();
            } else if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            dragged = false;
            lastX = event.getX();
            if (!isCover) {
                AndroidUtilities.cancelRunOnUIThread(this.onLongPress);
                AndroidUtilities.runOnUIThread(this.onLongPress, ViewConfiguration.getLongPressTimeout());
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            final float Δx = event.getX() - lastX;
            final boolean allowDrag = open && (dragged || Math.abs(Δx) > AndroidUtilities.touchSlop);
            if (allowDrag) {
                final long videoScrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());
                if (videoTrack != null && pressHandle == HANDLE_VIDEO_SCROLL) {
                    scroll = (long) Utilities.clamp(scroll - Δx / sw * videoScrollDuration, videoTrack.duration - videoScrollDuration, 0);
                    invalidate();
                    dragged = true;
                    draggingProgress = false;
                } else if (videoTrack != null && (pressHandle == HANDLE_VIDEO_LEFT || pressHandle == HANDLE_VIDEO_RIGHT || pressHandle == HANDLE_VIDEO_REGION)) {
                    float d = Δx / sw * (videoScrollDuration / (float) videoTrack.duration);
                    if (pressHandle == HANDLE_VIDEO_LEFT) {
                        videoTrack.left = Utilities.clamp(videoTrack.left + d, videoTrack.right - MIN_SELECT_DURATION / (float) videoTrack.duration, 0);
                        if (delegate != null) {
                            delegate.onVideoLeftChange(videoTrack.left);
                        }
                        if (videoTrack.right - videoTrack.left > MAX_SELECT_DURATION / (float) videoTrack.duration) {
                            videoTrack.right = Math.min(1, videoTrack.left + MAX_SELECT_DURATION / (float) videoTrack.duration);
                            if (delegate != null) {
                                delegate.onVideoRightChange(videoTrack.right);
                            }
                        }
                    } else if (pressHandle == HANDLE_VIDEO_RIGHT) {
                        videoTrack.right = Utilities.clamp(videoTrack.right + d, 1, videoTrack.left + MIN_SELECT_DURATION / (float) videoTrack.duration);
                        if (delegate != null) {
                            delegate.onVideoRightChange(videoTrack.right);
                        }
                        if (videoTrack.right - videoTrack.left > MAX_SELECT_DURATION / (float) videoTrack.duration) {
                            videoTrack.left = Math.max(0, videoTrack.right - MAX_SELECT_DURATION / (float) videoTrack.duration);
                            if (delegate != null) {
                                delegate.onVideoLeftChange(videoTrack.left);
                            }
                        }
                    } else if (pressHandle == HANDLE_VIDEO_REGION) {
                        if (d > 0) {
                            d = Math.min(1 - videoTrack.right, d);
                        } else {
                            d = Math.max(-videoTrack.left, d);
                        }
                        videoTrack.left += d;
                        videoTrack.right += d;
                        if (delegate != null) {
                            delegate.onVideoLeftChange(videoTrack.left);
                            delegate.onVideoRightChange(videoTrack.right);
                        }
                    }
                    if (progress / (float) videoTrack.duration < videoTrack.left || progress / (float) videoTrack.duration > videoTrack.right) {
                        progress = (long) (videoTrack.left * videoTrack.duration);
                        if (delegate != null) {
                            delegate.onProgressChange(progress, false);
                        }
                    }
                    invalidate();
                    dragged = true;
                    draggingProgress = false;
                } else if (pressHandle == HANDLE_AUDIO_LEFT || pressHandle == HANDLE_AUDIO_RIGHT || pressHandle == HANDLE_AUDIO_REGION) {
                    float d = Δx / sw * (videoScrollDuration / (float) audioDuration);
                    if (pressHandle == HANDLE_AUDIO_LEFT) {
                        float maxValue = audioRight - minAudioSelect() / (float) audioDuration;
                        float minValue = Math.max(0, scroll - audioOffset) / (float) audioDuration;
                        if (videoTrack != null) {
                            minValue = Math.max(minValue, (videoTrack.left * videoTrack.duration + scroll - audioOffset) / (float) audioDuration);
                        } else if (collageMain != null) {
                            minValue = Math.max(minValue, (collageMain.left * collageMain.duration + scroll - audioOffset) / (float) audioDuration);
                        } else if (hasRound) {
                            minValue = Math.max(minValue, (roundLeft * roundDuration + scroll - audioOffset) / (float) audioDuration);
                        } else {
                            minValue = Math.max(minValue, audioRight - MAX_SELECT_DURATION / (float) audioDuration);
                            if (!hadDragChange && d < 0 && audioLeft <= (audioRight - MAX_SELECT_DURATION / (float) audioDuration)) {
                                pressHandle = HANDLE_AUDIO_REGION;
                            }
                        }
                        float wasAudioLeft = audioLeft;
                        audioLeft = Utilities.clamp(audioLeft + d, maxValue, minValue);
                        if (Math.abs(wasAudioLeft - audioLeft) > 0.01f) {
                            hadDragChange = true;
                        }
                        if (delegate != null) {
                            delegate.onAudioOffsetChange(audioOffset + (long) (audioLeft * audioDuration));
                        }
                        if (delegate != null) {
                            delegate.onAudioLeftChange(audioLeft);
                        }
                    } else if (pressHandle == HANDLE_AUDIO_RIGHT) {
                        float maxValue = Math.min(1, Math.max(0, scroll - audioOffset + videoScrollDuration) / (float) audioDuration);
                        float minValue = audioLeft + minAudioSelect() / (float) audioDuration;
                        if (videoTrack != null) {
                            maxValue = Math.min(maxValue, (videoTrack.right * videoTrack.duration + scroll - audioOffset) / (float) audioDuration);
                        } else if (collageMain != null) {
                            maxValue = Math.min(maxValue, (collageMain.right * collageMain.duration + scroll - audioOffset) / (float) audioDuration);
                        } else if (hasRound) {
                            maxValue = Math.min(maxValue, (roundRight * roundDuration + scroll - audioOffset) / (float) audioDuration);
                        } else {
                            maxValue = Math.min(maxValue, audioLeft + MAX_SELECT_DURATION / (float) audioDuration);
                            if (!hadDragChange && d > 0 && audioRight >= (audioLeft + MAX_SELECT_DURATION / (float) audioDuration)) {
                                pressHandle = HANDLE_AUDIO_REGION;
                            }
                        }
                        float wasAudioRight = audioRight;
                        audioRight = Utilities.clamp(audioRight + d, maxValue, minValue);
                        if (Math.abs(wasAudioRight - audioRight) > 0.01f) {
                            hadDragChange = true;
                        }
                        if (delegate != null) {
                            delegate.onAudioRightChange(audioRight);
                        }
                    }
                    if (pressHandle == HANDLE_AUDIO_REGION) {
                        float minLeft = Math.max(0, scroll - audioOffset) / (float) audioDuration;
                        float maxRight = Math.min(1, Math.max(0, scroll - audioOffset + videoScrollDuration) / (float) audioDuration);
//                        if (collageMain != null) {
//                            minLeft = Math.max(minLeft, (collageMain.left * collageMain.duration + scroll - audioOffset) / (float) audioDuration);
//                            maxRight = Math.min(maxRight, (collageMain.right * collageMain.duration + scroll - audioOffset) / (float) audioDuration);
//                        }
                        if (d > 0) {
                            d = Math.min(Math.max(0, maxRight - audioRight), d);
                        } else {
                            d = Math.max(Math.min(0, minLeft - audioLeft), d);
                        }
                        audioLeft += d;
                        audioRight += d;

                        if (delegate != null) {
                            delegate.onAudioLeftChange(audioLeft);
                            delegate.onAudioOffsetChange(audioOffset + (long) (audioLeft * audioDuration));
                            delegate.onAudioRightChange(audioRight);
                        }
                        if (delegate != null) {
                            delegate.onProgressDragChange(true);
                        }
                    }
                    if (videoTrack == null && !hasRound) {
                        progress = (long) (audioLeft * audioDuration);
                        if (delegate != null) {
                            delegate.onProgressDragChange(true);
                            delegate.onProgressChange(progress, false);
                        }
                    }
                    invalidate();
                    dragged = true;
                    draggingProgress = false;
                } else if (pressHandle == HANDLE_ROUND_LEFT || pressHandle == HANDLE_ROUND_RIGHT || pressHandle == HANDLE_ROUND_REGION) {
                    float d = Δx / sw * (videoScrollDuration / (float) roundDuration);
                    if (pressHandle == HANDLE_ROUND_LEFT) {
                        float maxValue = roundRight - minAudioSelect() / (float) roundDuration;
                        float minValue = Math.max(0, scroll - roundOffset) / (float) roundDuration;
                        if (videoTrack != null) {
                            minValue = Math.max(minValue, (videoTrack.left * videoTrack.duration + scroll - roundOffset) / (float) roundDuration);
                        } else if (collageMain != null) {
                            minValue = Math.max(minValue, (collageMain.left * collageMain.duration + scroll - roundOffset) / (float) roundDuration);
                        } else {
                            minValue = Math.max(minValue, roundRight - MAX_SELECT_DURATION / (float) roundDuration);
                            if (!hadDragChange && d < 0 && roundLeft <= (roundRight - MAX_SELECT_DURATION / (float) roundDuration)) {
                                pressHandle = HANDLE_AUDIO_REGION;
                            }
                        }
                        float wasAudioLeft = roundLeft;
                        roundLeft = Utilities.clamp(roundLeft + d, maxValue, minValue);
                        if (Math.abs(wasAudioLeft - roundLeft) > 0.01f) {
                            hadDragChange = true;
                        }
                        if (delegate != null) {
                            delegate.onRoundOffsetChange(roundOffset + (long) (roundLeft * roundDuration));
                        }
                        if (delegate != null) {
                            delegate.onRoundLeftChange(roundLeft);
                        }
                    } else if (pressHandle == HANDLE_ROUND_RIGHT) {
                        float maxValue = Math.min(1, Math.max(0, scroll - roundOffset + videoScrollDuration) / (float) roundDuration);
                        float minValue = roundLeft + minAudioSelect() / (float) roundDuration;
                        if (videoTrack != null) {
                            maxValue = Math.min(maxValue, (videoTrack.right * videoTrack.duration + scroll - roundOffset) / (float) roundDuration);
                        } if (collageMain != null) {
                            maxValue = Math.min(maxValue, (collageMain.right * collageMain.duration + scroll - roundOffset) / (float) roundDuration);
                        } else {
                            maxValue = Math.min(maxValue, roundLeft + MAX_SELECT_DURATION / (float) roundDuration);
                            if (!hadDragChange && d > 0 && roundRight >= (roundLeft + MAX_SELECT_DURATION / (float) roundDuration)) {
                                pressHandle = HANDLE_AUDIO_REGION;
                            }
                        }
                        float wasAudioRight = roundRight;
                        roundRight = Utilities.clamp(roundRight + d, maxValue, minValue);
                        if (Math.abs(wasAudioRight - roundRight) > 0.01f) {
                            hadDragChange = true;
                        }
                        if (delegate != null) {
                            delegate.onRoundRightChange(roundRight);
                        }
                    }
                    if (pressHandle == HANDLE_ROUND_REGION) {
                        float minLeft = Math.max(0, scroll - roundOffset) / (float) roundDuration;
                        float maxRight = Math.min(1, Math.max(0, scroll - roundOffset + videoScrollDuration) / (float) roundDuration);
//                        if (collageMain != null) {
//                            minLeft = Math.max(minLeft, (collageMain.left * collageMain.duration + scroll - roundOffset) / (float) roundDuration);
//                            maxRight = Math.min(maxRight, (collageMain.right * collageMain.duration + scroll - roundOffset) / (float) roundDuration);
//                        }
                        if (d > 0) {
                            d = Math.min(maxRight - roundRight, d);
                        } else {
                            d = Math.max(minLeft - roundLeft, d);
                        }
                        roundLeft += d;
                        roundRight += d;

                        if (delegate != null) {
                            delegate.onRoundLeftChange(roundLeft);
                            delegate.onRoundOffsetChange(roundOffset + (long) (roundLeft * roundDuration));
                            delegate.onRoundRightChange(roundRight);
                        }
                        if (delegate != null) {
                            delegate.onProgressDragChange(true);
                        }
                    }
                    if (videoTrack == null) {
                        progress = (long) (roundLeft * roundDuration);
                        if (delegate != null) {
                            delegate.onProgressDragChange(true);
                            delegate.onProgressChange(progress, false);
                        }
                    }
                    invalidate();
                    dragged = true;
                    draggingProgress = false;
                } else if ((pressHandleCollageIndex >= 0 && pressHandleCollageIndex < collageTracks.size()) && (pressHandle == HANDLE_COLLAGE_LEFT || pressHandle == HANDLE_COLLAGE_RIGHT || pressHandle == HANDLE_COLLAGE_REGION)) {
                    final Track track = collageTracks.get(pressHandleCollageIndex);
                    float d = Δx / sw * (videoScrollDuration / (float) track.duration);
                    if (pressHandle == HANDLE_COLLAGE_LEFT) {
                        float maxValue = track.right - minAudioSelect() / (float) track.duration;
                        float minValue = Math.max(0, scroll - track.offset) / (float) track.duration;
                        if (track == collageMain) {
                            minValue = Math.max(minValue, track.right - MAX_SELECT_DURATION / (float) track.duration);
                            if (!hadDragChange && d < 0 && track.left <= (track.right - MAX_SELECT_DURATION / (float) track.duration)) {
                                pressHandle = HANDLE_COLLAGE_REGION;
                            }
                        }
//                        else if (collageMain != null) {
//                            minValue = Math.max(minValue, (collageMain.left * collageMain.duration + scroll - track.offset) / (float) track.duration);
//                        }
                        float wasTrackLeft = track.left;
                        track.left = Utilities.clamp(track.left + d, maxValue, minValue);
                        if (Math.abs(wasTrackLeft - track.left) > 0.01f) {
                            hadDragChange = true;
                        }
                        if (delegate != null) {
                            delegate.onVideoOffsetChange(track.index, track.offset);
                        }
                        if (delegate != null) {
                            delegate.onVideoLeftChange(track.index, track.left);
                        }
                    } else if (pressHandle == HANDLE_COLLAGE_RIGHT) {
                        float maxValue = Math.min(1, Math.max(0, scroll - track.offset + videoScrollDuration) / (float) track.duration);
                        float minValue = track.left + minAudioSelect() / (float) track.duration;
                        if (track == collageMain) {
                            maxValue = Math.min(maxValue, track.left + MAX_SELECT_DURATION / (float) track.duration);
                            if (!hadDragChange && d > 0 && track.right >= (track.left + MAX_SELECT_DURATION / (float) track.duration)) {
                                pressHandle = HANDLE_COLLAGE_REGION;
                            }
                        }
//                        else if (collageMain != null) {
//                            maxValue = Math.min(maxValue, (collageMain.right * collageMain.duration + scroll - track.offset) / (float) track.duration);
//                        }
                        float wasTrackRight = track.right;
                        track.right = Utilities.clamp(track.right + d, maxValue, minValue);
                        if (Math.abs(wasTrackRight - track.right) > 0.01f) {
                            hadDragChange = true;
                        }
                        if (delegate != null) {
                            delegate.onVideoRightChange(track.index, track.right);
                        }
                    }
                    if (pressHandle == HANDLE_COLLAGE_REGION) {
                        float minLeft = Math.max(0, scroll - track.offset) / (float) track.duration;
                        float maxRight = Math.min(1, Math.max(0, scroll - track.offset + videoScrollDuration) / (float) track.duration);
//                        if (track != collageMain) {
//                            minLeft = Math.max(minLeft, (collageMain.left * collageMain.duration + scroll - track.offset) / (float) track.duration);
//                            maxRight = Math.min(maxRight, (collageMain.right * collageMain.duration + scroll - track.offset) / (float) track.duration);
//                        }
                        if (d > 0) {
                            d = Math.min(maxRight - track.right, d);
                        } else {
                            d = Math.max(minLeft - track.left, d);
                        }
                        track.left += d;
                        track.right += d;

                        if (delegate != null) {
                            delegate.onVideoLeftChange(track.index, track.left);
                            delegate.onVideoOffsetChange(track.index, track.offset);
                            delegate.onVideoRightChange(track.index, track.right);
                        }
                        if (delegate != null) {
                            delegate.onProgressDragChange(true);
                        }
                    }
//                    if (collageMain == track) {
//                        progress = (long) (track.left * track.duration);
//                        if (delegate != null) {
//                            delegate.onProgressDragChange(true);
//                            delegate.onProgressChange(progress, false);
//                        }
//                    }
                    invalidate();
                    dragged = true;
                    draggingProgress = false;
                } else if (pressHandle == HANDLE_AUDIO_SCROLL) {
                    float d = Δx / sw * videoScrollDuration;
                    moveAudioOffset(d);
                    dragged = true;
                    draggingProgress = false;
                } else if (pressHandle == HANDLE_ROUND_SCROLL) {
                    float d = Δx / sw * videoScrollDuration;
                    moveRoundOffset(d);
                    dragged = true;
                    draggingProgress = false;
                } else if ((pressHandleCollageIndex >= 0 && pressHandleCollageIndex < collageTracks.size()) && pressHandle == HANDLE_COLLAGE_SCROLL) {
                    final Track track = collageTracks.get(pressHandleCollageIndex);
                    float d = Δx / sw * videoScrollDuration;
                    moveCollageOffset(track, d);
                    dragged = true;
                    draggingProgress = false;
                } else if (draggingProgress) {
                    setProgressAt(event.getX(), now - lastTime < 350);
                    if (!dragged && delegate != null) {
                        delegate.onProgressDragChange(true);
                    }
                    dragged = true;
                }
                lastX = event.getX();
            }
            if (dragged) {
                AndroidUtilities.cancelRunOnUIThread(this.onLongPress);
            }
            if ((pressHandle == HANDLE_VIDEO_SCROLL || pressHandle == HANDLE_AUDIO_SCROLL || pressHandle == HANDLE_AUDIO_REGION) && velocityTracker != null) {
                velocityTracker.addMovement(event);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            AndroidUtilities.cancelRunOnUIThread(this.onLongPress);
            scroller.abortAnimation();
            boolean scrollStopped = true;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (System.currentTimeMillis() - pressTime <= ViewConfiguration.getTapTimeout() && !dragged || !open) {
                    if (!open) {
                        if (pressType == 10) {
                            if (onTimelineClick != null) {
                                onTimelineClick.run();
                            }
                        }
                    } else if (isCover && videoTrack != null) {
                        float d = videoTrack.right - videoTrack.left;
                        videoTrack.left = (event.getX() - px - ph) / sw * (1 - d);
                        videoTrack.right = videoTrack.left + d;
                        if (delegate != null) {
                            delegate.onVideoLeftChange(videoTrack.left);
                            delegate.onVideoRightChange(videoTrack.right);
                        }
                        invalidate();
                    } else if (pressType == 3 && (audioSelected || roundSelected ? -1 : collageSelected) != pressCollageIndex) {
                        audioSelected = false;
                        roundSelected = false;
                        collageSelected = pressCollageIndex;
                        if (delegate != null && pressCollageIndex >= 0 && pressCollageIndex < collageTracks.size()) {
                            Track track = collageTracks.get(pressCollageIndex);
                            delegate.onVideoSelected(track.index);
                        }
                        invalidate();
                    } else if (pressType == 2 && !audioSelected) {
                        audioSelected = true;
                        roundSelected = false;
                        if (delegate != null) {
                            delegate.onRoundSelectChange(false);
                        }
                        invalidate();
                    } else if (pressType == 1 && !roundSelected) {
                        audioSelected = false;
                        roundSelected = true;
                        if (delegate != null) {
                            delegate.onRoundSelectChange(true);
                        }
                        invalidate();
                    } else if (pressType != 2 && audioSelected) {
                        audioSelected = false;
                        roundSelected = false;
                        if (delegate != null) {
                            delegate.onRoundSelectChange(false);
                        }
                        invalidate();
                    } else if (pressType != 1 && roundSelected) {
                        audioSelected = false;
                        roundSelected = false;
                        if (delegate != null) {
                            delegate.onRoundSelectChange(false);
                        }
                        invalidate();
                    } else {
                        long wasProgress = progress;
                        if (setProgressAt(event.getX(), false) && Math.abs(progress - wasProgress) > 400) {
                            loopProgressFrom = wasProgress;
                            loopProgress.set(1, true);
                            invalidate();
                        }
                    }
                } else if (pressHandle == HANDLE_COLLAGE_SCROLL && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    final int velocity = (int) velocityTracker.getXVelocity();
                    scrollingVideo = true;
                    if (videoTrack != null && Math.abs(velocity) > dp(100)) {
                        final long videoScrollDuration = Math.min(videoTrack.duration, getMaxScrollDuration());
                        final int scrollX = (int) (px + scroll / (float) videoScrollDuration * sw);
                        final int maxScrollX = (int) (px + (videoTrack.duration - videoScrollDuration) / (float) videoScrollDuration * sw);
                        scrolling = true;
                        scroller.fling(wasScrollX = scrollX, 0, -velocity, 0, px, maxScrollX, 0, 0);
                        scrollStopped = false;
                    }
                } else if (pressHandle == HANDLE_VIDEO_SCROLL && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    final int velocity = (int) velocityTracker.getXVelocity();
                    scrollingVideo = true;
                    if (videoTrack != null && Math.abs(velocity) > dp(100)) {
                        final long videoScrollDuration = Math.min(videoTrack.duration, getMaxScrollDuration());
                        final int scrollX = (int) (px + scroll / (float) videoScrollDuration * sw);
                        final int maxScrollX = (int) (px + (videoTrack.duration - videoScrollDuration) / (float) videoScrollDuration * sw);
                        scrolling = true;
                        scroller.fling(wasScrollX = scrollX, 0, -velocity, 0, px, maxScrollX, 0, 0);
                        scrollStopped = false;
                    }
                } else if ((pressHandle == HANDLE_AUDIO_SCROLL || pressHandle == HANDLE_AUDIO_REGION && !dragged) && audioSelected && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(videoTrack != null ? 1000 : 1500);
                    final int velocity = (int) velocityTracker.getXVelocity();
                    scrollingVideo = false;
                    if (Math.abs(velocity) > dp(100)) {
                        final long videoScrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());
                        final int scrollX = (int) (px + ph + audioOffset / (float) videoScrollDuration * sw);
                        final long mx, mn;
                        if (videoTrack != null) {
                            mx = (long) ((videoTrack.right * videoTrack.duration) - (0 * audioDuration));
                            mn = (long) ((videoTrack.left * videoTrack.duration) - (1 * audioDuration));
                        } else if (hasRound) {
                            mx = (long) ((roundRight * roundDuration) - (0 * audioDuration));
                            mn = (long) ((roundLeft * roundDuration) - (1 * audioDuration));
                        } else {
                            mx = 0;
                            mn = (long) -(audioDuration - Math.min(getBaseDuration(), getMaxScrollDuration()));
                        }
                        scrolling = true;
                        scroller.fling(wasScrollX = scrollX, 0, velocity, 0, (int) (px + ph + mn / (float) videoScrollDuration * sw), (int) (px + ph + mx / (float) videoScrollDuration * sw), 0, 0);
                        scrollStopped = false;
                    }
                } else if ((pressHandle == HANDLE_ROUND_SCROLL || pressHandle == HANDLE_ROUND_REGION && !dragged) && roundSelected && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(videoTrack != null ? 1000 : 1500);
                    final int velocity = (int) velocityTracker.getXVelocity();
                    scrollingVideo = false;
                    if (Math.abs(velocity) > dp(100)) {
                        final long videoScrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());
                        final int scrollX = (int) (px + ph + roundOffset / (float) videoScrollDuration * sw);
                        final long mx, mn;
                        if (videoTrack != null) {
                            mx = (long) ((videoTrack.right * videoTrack.duration) - (0 * roundDuration));
                            mn = (long) ((videoTrack.left * videoTrack.duration) - (1 * roundDuration));
                        } else {
                            mx = 0;
                            mn = (long) -(roundDuration - Math.min(getBaseDuration(), getMaxScrollDuration()));
                        }
                        scrolling = true;
                        scroller.fling(wasScrollX = scrollX, 0, velocity, 0, (int) (px + ph + mn / (float) videoScrollDuration * sw), (int) (px + ph + mx / (float) videoScrollDuration * sw), 0, 0);
                        scrollStopped = false;
                    }
                }
            }
            if (askExactSeek != null) {
                AndroidUtilities.cancelRunOnUIThread(askExactSeek);
                askExactSeek = null;
            }
            if (dragged && scrollStopped && delegate != null) {
                delegate.onProgressDragChange(false);
            }
            dragged = false;
            draggingProgress = false;
            pressTime = -1;
            pressHandle = -1;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        lastTime = System.currentTimeMillis();
        return true;
    }

    private long minAudioSelect() {
        return (long) Math.max(MIN_SELECT_DURATION, Math.min(getBaseDuration(), MAX_SELECT_DURATION) * 0.15f);
    }

    private void moveAudioOffset(final float d) {
        if (videoTrack == null && !hasRound) {
            long wasAudioOffset = audioOffset;
            audioOffset = Utilities.clamp(audioOffset + (long) d, 0, (long) -(audioDuration - Math.min(getBaseDuration(), getMaxScrollDuration())));
            long rd = audioOffset - wasAudioOffset;
            audioLeft = Utilities.clamp(audioLeft - (float) rd / audioDuration, 1, 0);
            audioRight = Utilities.clamp(audioRight - (float) rd / audioDuration, 1, 0);
            if (delegate != null) {
                delegate.onAudioLeftChange(audioLeft);
                delegate.onAudioRightChange(audioRight);
            }
        } else if (audioSelected) {
            final float L = videoTrack != null ? videoTrack.left * videoTrack.duration : roundLeft * roundDuration;
            final float R = videoTrack != null ? videoTrack.right * videoTrack.duration : roundRight * roundDuration;
            final float D = videoTrack != null ? (videoTrack.right - videoTrack.left) * videoTrack.duration : (roundRight - roundLeft) * roundDuration;
            long mx = (long) (R - (audioRight * audioDuration));
            long mn = (long) (L - (audioLeft * audioDuration));
            final float wasDuration = Math.min(audioRight - audioLeft, D / (float) audioDuration);
            if (audioOffset + (long) d > mx) {
                audioRight = Utilities.clamp((R - audioOffset - (long) d) / (float) audioDuration, 1, wasDuration);
                audioLeft = Utilities.clamp(audioRight - wasDuration, 1, 0);
                long mmx = (long) (R - (audioRight * audioDuration));
                long mmn = (long) (L - (audioLeft * audioDuration));
                if (mmx < mmn) {
                    long t = mmx;
                    mmx = mmn;
                    mmn = t;
                }
                audioOffset = Utilities.clamp(audioOffset + (long) d, mmx, mmn);
                if (delegate != null) {
                    delegate.onAudioLeftChange(audioLeft);
                    delegate.onAudioRightChange(audioRight);
                }
            } else if (audioOffset + (long) d < mn) {
                audioLeft = Utilities.clamp((L - audioOffset - (long) d) / (float) audioDuration, 1 - wasDuration, 0);
                audioRight = Utilities.clamp(audioLeft + wasDuration, 1, 0);
                long mmx = (long) (R - (audioRight * audioDuration));
                long mmn = (long) (L - (audioLeft * audioDuration));
                if (mmx < mmn) {
                    long t = mmx;
                    mmx = mmn;
                    mmn = t;
                }
                audioOffset = Utilities.clamp(audioOffset + (long) d, mmx, mmn);
                if (delegate != null) {
                    delegate.onAudioLeftChange(audioLeft);
                    delegate.onAudioRightChange(audioRight);
                }
            } else {
                audioOffset += (long) d;
            }
        } else {
            audioOffset = Utilities.clamp(audioOffset + (long) d, (long) (getBaseDuration() - audioDuration * audioRight), (long) (-audioLeft * audioDuration));
        }
        invalidate();
        if (delegate != null) {
            delegate.onAudioOffsetChange(audioOffset + (long) (audioLeft * audioDuration));
        }
        if (!dragged && delegate != null) {
            delegate.onProgressDragChange(true);

            long progressToStart;
            if (videoTrack != null) {
                progressToStart = Utilities.clamp(audioOffset + (long) (audioLeft * audioDuration), (long) (videoTrack.right * videoTrack.duration), (long) (videoTrack.left * videoTrack.duration));
            } else if (hasRound) {
                progressToStart = Utilities.clamp(audioOffset + (long) (audioLeft * audioDuration), (long) (roundRight * roundDuration), (long) (roundLeft * roundDuration));
            } else {
                progressToStart = Utilities.clamp((long) (audioLeft * audioDuration), audioDuration, 0);
            }
            if (videoTrack != null && Math.abs(progress - progressToStart) > 400) {
                loopProgressFrom = progress;
                loopProgress.set(1, true);
            }
            delegate.onProgressChange(progress = progressToStart, false);
        } else if (dragged || scrolling) {
            if (videoTrack != null) {
                progress = Utilities.clamp(audioOffset + (long) (audioLeft * audioDuration), (long) (videoTrack.right * videoTrack.duration), (long) (videoTrack.left * videoTrack.duration));
            } else if (hasRound && videoTrack != null) {
                progress = Utilities.clamp(audioOffset + (long) (audioLeft * audioDuration), (long) (roundRight * videoTrack.duration), (long) (roundLeft * videoTrack.duration));
            } else {
                progress = Utilities.clamp((long) (audioLeft * audioDuration), audioDuration, 0);
            }
            if (delegate != null) {
                delegate.onProgressChange(progress, false);
            }
        }
    }

    private void moveRoundOffset(final float d) {
        if (videoTrack == null) {
            long wasAudioOffset = roundOffset;
            roundOffset = Utilities.clamp(roundOffset + (long) d, 0, (long) -(roundDuration - Math.min(getBaseDuration(), getMaxScrollDuration())));
            long rd = roundOffset - wasAudioOffset;
            roundLeft = Utilities.clamp(roundLeft - (float) rd / roundDuration, 1, 0);
            roundRight = Utilities.clamp(roundRight - (float) rd / roundDuration, 1, 0);
            if (delegate != null) {
                delegate.onRoundLeftChange(roundLeft);
                delegate.onRoundRightChange(roundRight);
            }
        } else if (roundSelected) {
            long mx = (long) ((videoTrack.right * videoTrack.duration) - (roundRight * roundDuration));
            long mn = (long) ((videoTrack.left * videoTrack.duration) - (roundLeft * roundDuration));
            final float wasDuration = Math.min(roundRight - roundLeft, (videoTrack.right - videoTrack.left) * videoTrack.duration / (float) roundDuration);
            if (roundOffset + (long) d > mx) {
                roundRight = Utilities.clamp((videoTrack.right * videoTrack.duration - roundOffset - (long) d) / (float) roundDuration, 1, wasDuration);
                roundLeft = Utilities.clamp(roundRight - wasDuration, 1, 0);
                long mmx = (long) ((videoTrack.right * videoTrack.duration) - (roundRight * roundDuration));
                long mmn = (long) ((videoTrack.left * videoTrack.duration) - (roundLeft * roundDuration));
                if (mmx < mmn) {
                    long t = mmx;
                    mmx = mmn;
                    mmn = t;
                }
                roundOffset = Utilities.clamp(roundOffset + (long) d, mmx, mmn);
                if (delegate != null) {
                    delegate.onRoundLeftChange(roundLeft);
                    delegate.onRoundRightChange(roundRight);
                }
            } else if (roundOffset + (long) d < mn) {
                roundLeft = Utilities.clamp((videoTrack.left * videoTrack.duration - roundOffset - (long) d) / (float) roundDuration, 1 - wasDuration, 0);
                roundRight = Utilities.clamp(roundLeft + wasDuration, 1, 0);
                long mmx = (long) ((videoTrack.right * videoTrack.duration) - (roundRight * roundDuration));
                long mmn = (long) ((videoTrack.left * videoTrack.duration) - (roundLeft * roundDuration));
                if (mmx < mmn) {
                    long t = mmx;
                    mmx = mmn;
                    mmn = t;
                }
                roundOffset = Utilities.clamp(roundOffset + (long) d, mmx, mmn);
                if (delegate != null) {
                    delegate.onRoundLeftChange(roundLeft);
                    delegate.onRoundRightChange(roundRight);
                }
            } else {
                roundOffset += (long) d;
            }
        } else {
            roundOffset = Utilities.clamp(roundOffset + (long) d, (long) (getBaseDuration() - roundDuration * roundRight), (long) (-roundLeft * roundDuration));
        }
        invalidate();
        if (delegate != null) {
            delegate.onRoundOffsetChange(roundOffset + (long) (roundLeft * roundDuration));
        }
        if (!dragged && delegate != null) {
            delegate.onProgressDragChange(true);

            long progressToStart;
            if (videoTrack != null) {
                progressToStart = Utilities.clamp(roundOffset + (long) (roundLeft * roundDuration), (long) (videoTrack.right * videoTrack.duration), (long) (videoTrack.left * videoTrack.duration));
            } else {
                progressToStart = Utilities.clamp((long) (roundLeft * roundDuration), roundDuration, 0);
            }
            if (videoTrack != null && Math.abs(progress - progressToStart) > 400) {
                loopProgressFrom = progress;
                loopProgress.set(1, true);
            }
            delegate.onProgressChange(progress = progressToStart, false);
        } else if (dragged || scrolling) {
            if (videoTrack != null) {
                progress = Utilities.clamp(roundOffset + (long) (roundLeft * roundDuration), (long) (videoTrack.right * videoTrack.duration), (long) (videoTrack.left * videoTrack.duration));
            } else {
                progress = Utilities.clamp((long) (roundLeft * roundDuration), roundDuration, 0);
            }
            if (delegate != null) {
                delegate.onProgressChange(progress, false);
            }
        }
    }

    private void moveCollageOffset(Track track, final float d) {
        if (track == null) return;
        if (collageMain == track || collageMain == null) {
//            long wasAudioOffset = track.offset;
//            track.offset = Utilities.clamp(track.offset + (long) d, 0, (long) -(track.duration - Math.min(getBaseDuration(), getMaxScrollDuration())));
//            long rd = track.offset - wasAudioOffset;
//            track.left = Utilities.clamp(track.left - (float) rd / track.duration, 1, 0);
//            track.right = Utilities.clamp(track.right - (float) rd / track.duration, 1, 0);
//            if (delegate != null) {
//                delegate.onVideoLeftChange(track.index, track.left);
//                delegate.onVideoRightChange(track.index, track.right);
//            }
        } else if (collageSelected == collageTracks.indexOf(track)) {
            long mx = (long) ((1.0f/*collageMain.right*/ * collageMain.duration) - (track.right * track.duration));
            long mn = (long) ((0.0f/*collageMain.left*/ * collageMain.duration) - (track.left * track.duration));
            final float wasDuration = Math.min(track.right - track.left, (collageMain.right - collageMain.left) * collageMain.duration / (float) track.duration);
            if (track.offset + (long) d > mx) {
                track.right = Utilities.clamp((collageMain.right * collageMain.duration - track.offset - (long) d) / (float) track.duration, 1, wasDuration);
                track.left = Utilities.clamp(track.right - wasDuration, 1, 0);
                long mmx = (long) ((collageMain.right * collageMain.duration) - (track.right * track.duration));
                long mmn = (long) ((collageMain.left * collageMain.duration) - (track.left * track.duration));
                if (mmx < mmn) {
                    long t = mmx;
                    mmx = mmn;
                    mmn = t;
                }
                track.offset = Utilities.clamp(track.offset + (long) d, mmx, mmn);
                if (delegate != null) {
                    delegate.onVideoLeftChange(track.index, track.left);
                    delegate.onVideoRightChange(track.index, track.right);
                }
            } else if (track.offset + (long) d < mn) {
                track.left = Utilities.clamp((collageMain.left * collageMain.duration - track.offset - (long) d) / (float) track.duration, 1 - wasDuration, 0);
                track.right = Utilities.clamp(track.left + wasDuration, 1, 0);
                long mmx = (long) ((collageMain.right * collageMain.duration) - (track.right * track.duration));
                long mmn = (long) ((collageMain.left * collageMain.duration) - (track.left * track.duration));
                if (mmx < mmn) {
                    long t = mmx;
                    mmx = mmn;
                    mmn = t;
                }
                track.offset = Utilities.clamp(track.offset + (long) d, mmx, mmn);
                if (delegate != null) {
                    delegate.onVideoLeftChange(track.index, track.left);
                    delegate.onVideoRightChange(track.index, track.right);
                }
            } else {
                track.offset += (long) d;
            }
        } else {
            track.offset = Utilities.clamp(track.offset + (long) d, (long) (getBaseDuration() - track.duration * track.right), (long) (-track.left * track.duration));
        }
        invalidate();
        if (delegate != null) {
            delegate.onVideoOffsetChange(track.index, track.offset);
        }
        if (!dragged && delegate != null) {
            delegate.onProgressDragChange(true);

            long progressToStart;
            if (collageMain != track && collageMain != null) {
                progressToStart = Utilities.clamp(track.offset + (long) (track.left * track.duration), (long) (collageMain.right * collageMain.duration), (long) (collageMain.left * collageMain.duration));
            } else {
                progressToStart = Utilities.clamp((long) (track.left * track.duration), track.duration, 0);
            }
            if (collageMain != track && collageMain != null && Math.abs(progress - progressToStart) > 400) {
                loopProgressFrom = progress;
                loopProgress.set(1, true);
            }
            delegate.onProgressChange(progress = progressToStart, false);
        } else if (dragged || scrolling) {
            if (collageMain != track && collageMain != null) {
                progress = Utilities.clamp(track.offset + (long) (track.left * track.duration), (long) (collageMain.right * collageMain.duration), (long) (collageMain.left * collageMain.duration));
            } else {
                progress = Utilities.clamp((long) (track.left * track.duration), track.duration, 0);
            }
            if (delegate != null) {
                delegate.onProgressChange(progress, false);
            }
        }
    }

    private int wasScrollX;
    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int scrollX = scroller.getCurrX();
            final long videoScrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());
            if (scrollingVideo) {
                scroll = (long) Math.max(0, ((scrollX - px - ph) / (float) sw * videoScrollDuration));
            } else {
                if (!audioSelected) {
                    scroller.abortAnimation();
                    return;
                }
                final float d = ((scrollX - px - ph) / (float) sw * videoScrollDuration) - ((wasScrollX - px - ph) / (float) sw * videoScrollDuration);
                moveAudioOffset(d);
            }
            invalidate();
            wasScrollX = scrollX;
        } else if (scrolling) {
            scrolling = false;
            if (delegate != null) {
                delegate.onProgressDragChange(false);
            }
        }
    }

    static class WaveformPath extends Path {
        private final int ph = dp(10);
        private final float[] waveformRadii = new float[8];

        private ArrayList<AudioWaveformLoader> lastWaveforms;
        private ArrayList<Integer> lastWaveformCounts;
        private ArrayList<Float> lastWaveformLoaded;
        private long lastScrollDuration;
        private float lastAudioHeight;
        private float lastMaxBar;
        private float lastAudioSelected;
        private float lastBottom;
        private float lastStart;
        private float lastLeft;
        private float lastRight;

        WaveformPath() {
            waveformRadii[0] = waveformRadii[1] = waveformRadii[2] = waveformRadii[3] = dp(2);
            waveformRadii[4] = waveformRadii[5] = waveformRadii[6] = waveformRadii[7] = 0;
        }

        private boolean eqCount(ArrayList<Integer> counts, ArrayList<AudioWaveformLoader> waveforms) {
            if (counts == null && waveforms == null) return true;
            if (counts == null || waveforms == null) return false;
            if (counts.size() != waveforms.size()) return false;
            for (int i = 0; i < counts.size(); ++i) {
                if (counts.get(i) != (waveforms.get(i) == null ? 0 : waveforms.get(i).getCount()))
                    return false;
            }
            return true;
        }

        private boolean eqLoadedCounts(ArrayList<Float> loadedCounts, ArrayList<AudioWaveformLoader> waveforms) {
            if (loadedCounts == null && waveforms == null) return true;
            if (loadedCounts == null || waveforms == null) return false;
            if (loadedCounts.size() != waveforms.size()) return false;
            for (int i = 0; i < loadedCounts.size(); ++i) {
                if (loadedCounts.get(i) != (waveforms.get(i) == null ? 0 : waveforms.get(i).animatedLoaded.set(waveforms.get(i).getLoadedCount())))
                    return false;
            }
            return true;
        }

        public static int getMaxBar(ArrayList<AudioWaveformLoader> waveforms) {
            if (waveforms == null) return 0;
            int sum = 0;
            for (int i = 0; i < waveforms.size(); ++i) {
                if (waveforms.get(i) == null) continue;
                sum += waveforms.get(i).getMaxBar();
            }
            return sum;
        }

        public static int getMaxLoadedCount(ArrayList<AudioWaveformLoader> waveforms) {
            if (waveforms == null) return 0;
            int max = 0;
            for (int i = 0; i < waveforms.size(); ++i) {
                if (waveforms.get(i) == null) continue;
                max = Math.max(waveforms.get(i).getLoadedCount(), max);
            }
            return max;
        }

        public void check(
            float start, float left, float right,
            float audioSelected,
            long scrollDuration,
            float audioHeight,
            float maxBar,
            float bottom,
            AudioWaveformLoader waveform
        ) {
            if (waveform == null) {
                rewind();
                return;
            }
            final float animatedLoaded = waveform == null ? 0 : waveform.animatedLoaded.set(waveform.getLoadedCount());
            if (
                lastScrollDuration != scrollDuration ||
                Math.abs(lastAudioHeight - audioHeight) > 1f ||
                Math.abs(lastMaxBar - maxBar) > 0.01f ||
                Math.abs(lastAudioSelected - audioSelected) > 0.1f ||
                Math.abs(lastBottom - bottom) > 1f ||
                Math.abs(lastStart - start) > 1f ||
                Math.abs(lastLeft - left) > 1f ||
                Math.abs(lastRight - right) > 1f ||
                (waveform != null) != (lastWaveformCounts != null && lastWaveformCounts.size() == 1) ||
                Math.abs((lastWaveformLoaded == null || lastWaveformLoaded.isEmpty() ? 0 : lastWaveformLoaded.get(0)) - animatedLoaded) > 0.01f
            ) {
                if (lastWaveformCounts == null) {
                    lastWaveformCounts = new ArrayList<>();
                } else lastWaveformCounts.clear();
                lastWaveformCounts.add(waveform.getCount());
                if (lastWaveformLoaded == null) {
                    lastWaveformLoaded = new ArrayList<>();
                } else lastWaveformLoaded.clear();
                lastWaveformLoaded.add(animatedLoaded);
                layout(
                    lastStart = start, lastLeft = left, lastRight = right,
                    lastAudioSelected = audioSelected,
                    lastMaxBar = maxBar,
                    lastAudioHeight = audioHeight,
                    lastBottom = bottom,
                    waveform == null ? 0 : waveform.animatedLoaded.set(waveform.getLoadedCount()),
                    waveform
                );
            }
        }

        public void check(
            float start, float left, float right,
            float audioSelected,
            float audioHeight,
            float maxBar,
            float bottom,
            ArrayList<AudioWaveformLoader> waveforms
        ) {
            if (waveforms == null || waveforms.isEmpty()) {
                rewind();
                return;
            }
            if (Math.abs(lastAudioHeight - audioHeight) > 1f ||
                Math.abs(lastMaxBar - maxBar) > 0.01f ||
                Math.abs(lastAudioSelected - audioSelected) > 0.1f ||
                Math.abs(lastBottom - bottom) > 1f ||
                Math.abs(lastStart - start) > 1f ||
                Math.abs(lastLeft - left) > 1f ||
                Math.abs(lastRight - right) > 1f ||
                eqCount(lastWaveformCounts, waveforms) ||
                eqLoadedCounts(lastWaveformLoaded, waveforms)
            ) {
                if (lastWaveformCounts == null) {
                    lastWaveformCounts = new ArrayList<>();
                } else lastWaveformCounts.clear();
                for (int i = 0; i < waveforms.size(); ++i) {
                    lastWaveformCounts.add(waveforms.get(i) == null ? 0 : waveforms.get(i).getCount());
                }
                if (lastWaveformLoaded == null) {
                    lastWaveformLoaded = new ArrayList<>();
                } else lastWaveformLoaded.clear();
                for (int i = 0; i < waveforms.size(); ++i) {
                    lastWaveformLoaded.add(waveforms.get(i) == null ? 0 : waveforms.get(i).animatedLoaded.set(waveforms.get(i).getLoadedCount()));
                }
                layout(
                    lastStart = start, lastLeft = left, lastRight = right,
                    lastAudioSelected = audioSelected,
                    lastMaxBar = maxBar,
                    lastAudioHeight = audioHeight,
                    lastBottom = bottom,
                    lastWaveformLoaded,
                    waveforms
                );
            }
        }

        private void layout(
            float start, float left, float right,
            float audioSelected,
            float maxBar,
            float audioHeight,
            float bottom,
            ArrayList<Float> animatedLoaded,
            ArrayList<AudioWaveformLoader> waveforms
        ) {
            rewind();
            final float barWidth = Math.round(dpf2(3.3333f));
            int maxCount = 0;
            for (int i = 0; i < waveforms.size(); ++i) {
                if (waveforms.get(i) != null) {
                    maxCount = Math.max(maxCount, waveforms.get(i).getCount());
                }
            }
            int from = Math.max(0, (int) ((left - ph - start) / barWidth));
            int to = Math.min(maxCount - 1, (int) Math.ceil((right + ph - start) / barWidth));
            for (int i = from; i <= to; ++i) {
                float x = start + i * barWidth + dp(2);
                int sum = 0;
                for (int j = 0; j < waveforms.size(); ++j) {
                    short bar = waveforms.get(j) == null || i >= waveforms.get(j).getCount() ? 0 : waveforms.get(j).getBar(i);
                    if (i < animatedLoaded.get(j) && i + 1 > animatedLoaded.get(j)) {
                        bar *= (animatedLoaded.get(j) - i);
                    } else if (i > animatedLoaded.get(j)) {
                        bar = 0;
                    }
                    sum += bar;
                }
                float h = maxBar <= 0 ? 0 : sum / (float) maxBar * audioHeight * .6f;
                if (x < left || x > right) {
                    h *= audioSelected;
                    if (h <= 0) {
                        continue;
                    }
                }
                h = Math.max(h, lerp(dpf2(0.66f), dpf2(1.5f), audioSelected));
                AndroidUtilities.rectTmp.set(
                    x,
                    lerp(bottom - h, bottom - (audioHeight + h) / 2f, audioSelected),
                    x + dpf2(1.66f),
                    lerp(bottom, bottom - (audioHeight - h) / 2f, audioSelected)
                );
                addRoundRect(AndroidUtilities.rectTmp, waveformRadii, Path.Direction.CW);
            }
        }

        private void layout(
            float start, float left, float right,
            float audioSelected,
            float maxBar,
            float audioHeight,
            float bottom,
            float animatedLoaded,
            AudioWaveformLoader waveform
        ) {
            rewind();
            final float barWidth = Math.round(dpf2(3.3333f));
            int maxCount = waveform.getCount();
            int from = Math.max(0, (int) ((left - ph - start) / barWidth));
            int to = Math.min(maxCount - 1, (int) Math.ceil((right + ph - start) / barWidth));
            for (int i = from; i <= to; ++i) {
                float x = start + i * barWidth + dp(2);
                int sum = waveform.getBar(i);
                float h = maxBar <= 0 ? 0 : sum / (float) maxBar * audioHeight * .6f;
                if (i < animatedLoaded && i + 1 > animatedLoaded) {
                    h *= (animatedLoaded - i);
                } else if (i > animatedLoaded) {
                    h = 0;
                }
                if (x < left || x > right) {
                    h *= audioSelected;
                    if (h <= 0) {
                        continue;
                    }
                }
                h = Math.max(h, lerp(dpf2(0.66f), dpf2(1.5f), audioSelected));
                AndroidUtilities.rectTmp.set(
                        x,
                        lerp(bottom - h, bottom - (audioHeight + h) / 2f, audioSelected),
                        x + dpf2(1.66f),
                        lerp(bottom, bottom - (audioHeight - h) / 2f, audioSelected)
                );
                addRoundRect(AndroidUtilities.rectTmp, waveformRadii, Path.Direction.CW);
            }
        }
    }

    final float[] selectedVideoRadii = new float[8];

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final Paint blurPaint = backgroundBlur.getPaint(1f);
        final float open = this.openT.set(this.open);
        final long scrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());

        if (open < 1) {
            timelineBounds.set(px, h - py - dp(28), w - px, h - py);
            timelineClipPath.rewind();
            timelineClipPath.addRoundRect(timelineBounds, dp(8), dp(8), Path.Direction.CW);
            canvas.saveLayerAlpha(timelineBounds, (int) (0xFF * (1.0f - open)), Canvas.ALL_SAVE_FLAG);
            canvas.clipPath(timelineClipPath);
            if (blurManager.hasRenderNode()) {
                backgroundBlur.drawRect(canvas);
                canvas.drawColor(0x33000000);
            } else if (blurPaint == null) {
                canvas.drawColor(0x40000000);
            } else {
                canvas.drawRect(timelineBounds, blurPaint);
                canvas.drawColor(0x33000000);
            }
            if (!collageWaveforms.isEmpty() && blurManager != null && blurManager.hasRenderNode()) {
                final float maxBar = timelineWaveformMax.set(WaveformPath.getMaxBar(collageWaveforms));
                final float start = px + ph + (audioOffset - scroll) / (float) scrollDuration * sw;
                timelineWaveformPath.check(start, timelineBounds.left, timelineBounds.right, 0.0f, dp(28), maxBar, timelineBounds.bottom, collageWaveforms);
                canvas.saveLayerAlpha(timelineBounds, (int) (0xFF * 0.4f), Canvas.ALL_SAVE_FLAG);
                canvas.clipPath(timelineWaveformPath);
                audioWaveformBlur.drawRect(canvas);
                canvas.restore();
            } else if (!collageWaveforms.isEmpty()) {
                Paint paint = audioWaveformBlur.getPaint(.4f);
                if (paint == null) {
                    paint = waveformPaint;
                    paint.setAlpha((int) (0x40));
                }
                final float maxBar = timelineWaveformMax.set(WaveformPath.getMaxBar(collageWaveforms));
//                final float animatedLoaded = timelineWaveformLoaded.set(WaveformPath.getMaxLoadedCount(collageWaveforms));
                final float start = px + ph + (audioOffset - scroll) / (float) scrollDuration * sw;
                timelineWaveformPath.check(start, timelineBounds.left, timelineBounds.right, 0.0f, dp(28), maxBar, timelineBounds.bottom, collageWaveforms);
                canvas.drawPath(timelineWaveformPath, paint);
            }
            final float w = timelineText.getCurrentWidth() + dp(3.66f) + timelineIcon.getIntrinsicWidth();
            int x = (int) (timelineBounds.centerX() - w / 2.0f), cy = (int) timelineBounds.centerY();
            timelineIcon.setBounds(x, cy - timelineIcon.getIntrinsicHeight() / 2, x + timelineIcon.getIntrinsicWidth(), cy + timelineIcon.getIntrinsicHeight() / 2);
            timelineIcon.setAlpha((int) (0xFF * 0.75f));
            timelineIcon.draw(canvas);
            timelineText.draw(canvas, timelineBounds.centerX() - w / 2.0f + timelineIcon.getIntrinsicWidth() + dp(3.66f), cy, 0xFFFFFFFF, 0.75f);
            canvas.restore();
        }

        if (open > 0) {
            boolean restore = false;
            if (open < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * open), Canvas.ALL_SAVE_FLAG);
                restore = true;
            }

            float videoHeight = 0;
            float videoT = videoTrack != null ? 1 : 0;
            float videoSelected = videoTrack != null ? videoTrack.selectedT.set(!audioSelected && !roundSelected) : 0;

            float bottom = h - py;
            float regionTop = 0, regionBottom = 0;
            float left = 0, right = 0;

            final float p = dp(4);
            final float video = videoSelected;
            // draw video thumbs
            if (videoTrack != null) {
                canvas.save();
                videoHeight = getVideoHeight();
                left += (videoTrack.left * videoTrack.duration) * video;
                right += (videoTrack.right * videoTrack.duration) * video;
                final float videoStartX = (videoTrack.duration <= 0 ? 0 : px + ph - scroll / (float) scrollDuration * sw) - ph;
                final float videoEndX = (videoTrack.duration <= 0 ? 0 : px + ph + (videoTrack.duration - scroll) / (float) scrollDuration * sw) + ph;
                videoBounds.set(videoStartX, bottom - videoHeight, videoEndX, bottom);
                bottom -= videoHeight + p * videoT;
                regionTop += videoBounds.top * video;
                regionBottom += videoBounds.bottom * video;
                videoClipPath.rewind();
                videoClipPath.addRoundRect(videoBounds, dp(8), dp(8), Path.Direction.CW);
                canvas.clipPath(videoClipPath);
                if (videoTrack.thumbs != null) {
                    float x = videoStartX;
                    final int frameWidth = videoTrack.thumbs.getFrameWidth();
                    final int fromFrame = (int) Math.max(0, Math.floor((videoStartX - px) / frameWidth));
                    final int toFrame = (int) Math.min(videoTrack.thumbs.count, Math.ceil(((videoEndX - videoStartX) - px) / frameWidth) + 1);

                    final int y = (int) videoBounds.top;

                    boolean allLoaded = videoTrack.thumbs.frames.size() >= toFrame;
                    boolean fullyCovered = frameWidth != 0 && allLoaded && !videoTrack.isRound;
                    if (fullyCovered) {
                        for (int i = fromFrame; i < Math.min(videoTrack.thumbs.frames.size(), toFrame); ++i) {
                            VideoThumbsLoader.BitmapFrame frame = videoTrack.thumbs.frames.get(i);
                            if (frame.bitmap == null) {
                                fullyCovered = false;
                                break;
                            }
                        }
                    }

                    if (!fullyCovered) {
                        if (blurManager.hasRenderNode()) {
                            backgroundBlur.drawRect(canvas);
                            canvas.drawColor(0x33000000);
                        } else if (blurPaint == null) {
                            canvas.drawColor(0x40000000);
                        } else {
                            canvas.drawRect(videoBounds, blurPaint);
                            canvas.drawColor(0x33000000);
                        }
                    }

                    if (frameWidth != 0) {
                        for (int i = fromFrame; i < Math.min(videoTrack.thumbs.frames.size(), toFrame); ++i) {
                            VideoThumbsLoader.BitmapFrame frame = videoTrack.thumbs.frames.get(i);
                            if (frame.bitmap != null) {
                                videoFramePaint.setAlpha((int) (0xFF * frame.getAlpha()));
                                canvas.drawBitmap(frame.bitmap, x, y - (int) ((frame.bitmap.getHeight() - videoHeight) / 2f), videoFramePaint);
                            }
                            x += frameWidth;
                        }
                    }

                    if (!allLoaded) {
                        videoTrack.thumbs.load();
                    }
                }
                selectedVideoClipPath.rewind();

                if (!isCover) {
                    AndroidUtilities.rectTmp.set(
                            px + ph + (videoTrack.left * videoTrack.duration - scroll) / (float) scrollDuration * sw - (videoTrack.left <= 0 ? ph : 0),
                            h - py - videoHeight,
                            px + ph + (videoTrack.right * videoTrack.duration - scroll) / (float) scrollDuration * sw + (videoTrack.right >= 1 ? ph : 0),
                            h - py
                    );
                    selectedVideoClipPath.addRoundRect(
                            AndroidUtilities.rectTmp,
                            selectedVideoRadii,
                            Path.Direction.CW
                    );
                    canvas.clipPath(selectedVideoClipPath, Region.Op.DIFFERENCE);
                    canvas.drawColor(0x50000000);
                }
                canvas.restore();
            }

            float collageHeight = 0;
            float collageT = 0;
            if (!collageTracks.isEmpty()) {
                collageT = 1;
                collageHeight = getCollageHeight();

                for (int i = 0; i < collageTracks.size(); ++i) {
                    final Track track = collageTracks.get(i);

                    final float selected = track.selectedT.set(!this.audioSelected && !this.roundSelected && collageSelected == i);
                    float _left, _right;
                    if (track != collageMain) {
                        _left = px + ph + (track.offset - scroll + lerp(track.left, 0, selected) * track.duration) / (float) scrollDuration * sw;
                        _right = px + ph + (track.offset - scroll + lerp(track.right, 1, selected) * track.duration) / (float) scrollDuration * sw;
                    } else {
                        _left = px + ph + (track.offset - scroll) / (float) scrollDuration * sw;
                        _right = px + ph + (track.offset - scroll + track.duration) / (float) scrollDuration * sw;
                    }

                    canvas.save();
                    final float height = lerp(dp(28), dp(38), selected);
                    track.bounds.set(_left - ph, bottom - height, _right + ph, bottom);
                    regionTop += track.bounds.top * selected;
                    regionBottom += track.bounds.bottom * selected;
                    left += (track.offset + track.left * track.duration) * selected;
                    right += (track.offset + track.right * track.duration) * selected;

                    collageClipPath.rewind();
                    collageClipPath.addRoundRect(track.bounds, dp(8), dp(8), Path.Direction.CW);
                    canvas.clipPath(collageClipPath);
                    if (track.thumbs != null) {
                        final float trackStartX = (track.duration <= 0 ? 0 : px + ph + (track.offset - scroll) / (float) scrollDuration * sw) - ph;
                        final float trackEndX = (track.duration <= 0 ? 0 : px + ph + (track.offset + track.duration - scroll) / (float) scrollDuration * sw) + ph;

                        float x = trackStartX;
                        final int frameWidth = track.thumbs.getFrameWidth();
                        final float L;
//                        if (videoTrack != null) {
                            L = px + ph + (track.offset - scroll) / (float) scrollDuration * sw;
//                        } else {
//                            L = px;
//                        }
                        final int fromFrame = (int) Math.max(0, Math.floor((trackStartX - L) / frameWidth));
                        final int toFrame = (int) Math.min(track.thumbs.count, Math.ceil((trackEndX - trackStartX) / frameWidth) + 1);

                        final int y = (int) track.bounds.top;

                        boolean allLoaded = track.thumbs.frames.size() >= toFrame;
                        boolean fullyCovered = allLoaded;
                        if (fullyCovered) {
                            for (int j = fromFrame; j < Math.min(track.thumbs.frames.size(), toFrame); ++j) {
                                final VideoThumbsLoader.BitmapFrame frame = track.thumbs.frames.get(j);
                                if (frame.bitmap == null) {
                                    fullyCovered = false;
                                    break;
                                }
                            }
                        }

                        if (!fullyCovered) {
                            if (blurManager.hasRenderNode()) {
                                backgroundBlur.drawRect(canvas);
                                canvas.drawColor(0x33000000);
                            } else if (blurPaint == null) {
                                canvas.drawColor(0x40000000);
                            } else {
                                canvas.drawRect(track.bounds, blurPaint);
                                canvas.drawColor(0x33000000);
                            }
                        }

                        if (frameWidth != 0) {
                            for (int j = fromFrame; j < Math.min(track.thumbs.frames.size(), toFrame); ++j) {
                                VideoThumbsLoader.BitmapFrame frame = track.thumbs.frames.get(j);
                                if (frame.bitmap != null) {
                                    collageFramePaint.setAlpha((int) (0xFF * frame.getAlpha()));
                                    canvas.drawBitmap(frame.bitmap, x, y - (int) ((frame.bitmap.getHeight() - height) / 2f), collageFramePaint);
                                }
                                x += frameWidth;
                            }
                        }

                        if (!allLoaded) {
                            track.thumbs.load();
                        }
                    }
                    selectedCollageClipPath.rewind();

                    if (!isCover) {
                        AndroidUtilities.rectTmp.set(
                            px + ph + (track.left * track.duration - scroll + track.offset) / (float) scrollDuration * sw - (track.left <= 0 ? ph : 0),
                            track.bounds.top,
                            px + ph + (track.right * track.duration - scroll + track.offset) / (float) scrollDuration * sw + (track.right >= 1 ? ph : 0),
                            track.bounds.bottom
                        );
                        selectedCollageClipPath.addRoundRect(AndroidUtilities.rectTmp, selectedVideoRadii, Path.Direction.CW);
                        canvas.clipPath(selectedCollageClipPath, Region.Op.DIFFERENCE);
                        canvas.drawColor(0x50000000);
                    }
                    canvas.restore();

                    bottom -= height + p * collageT;
                }
            }

            float roundT = this.roundT.set(hasRound);
            float roundSelected = this.roundSelectedT.set(hasRound && this.roundSelected);
            final float roundHeight = getRoundHeight() * roundT;
            final float round = roundT * (videoTrack != null || hasAudio || !collageTracks.isEmpty() ? roundSelected : 1);
            if (roundT > 0) {
                left += (roundOffset + roundLeft * roundDuration) * round;
                right += (roundOffset + roundRight * roundDuration) * round;

                float _left, _right;
                if (videoTrack != null) {
                    _left = px + ph + (roundOffset - scroll + lerp(roundLeft, 0, roundSelected) * roundDuration) / (float) scrollDuration * sw;
                    _right = px + ph + (roundOffset - scroll + lerp(roundRight, 1, roundSelected) * roundDuration) / (float) scrollDuration * sw;
                } else {
                    _left = px + ph + (roundOffset - scroll) / (float) scrollDuration * sw;
                    _right = px + ph + (roundOffset - scroll + roundDuration) / (float) scrollDuration * sw;
                }

                roundBounds.set(_left - ph, bottom - roundHeight, _right + ph, bottom);
                bottom -= roundHeight + p * roundT;
                regionTop += roundBounds.top * round;
                regionBottom += roundBounds.bottom * round;

                roundClipPath.rewind();
                roundClipPath.addRoundRect(roundBounds, dp(8), dp(8), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(roundClipPath);
                if (roundThumbs != null) {
                    final float roundStartX = (roundDuration <= 0 ? 0 : px + ph + (roundOffset - scroll) / (float) scrollDuration * sw) - ph;
                    final float roundEndX = (roundDuration <= 0 ? 0 : px + ph + (roundOffset + roundDuration - scroll) / (float) scrollDuration * sw) + ph;

                    float x = roundStartX;
                    final int frameWidth = roundThumbs.getFrameWidth();
                    final float L;
                    if (videoTrack != null) {
                        L = px + ph + (roundOffset - scroll) / (float) scrollDuration * sw;
                    } else {
                        L = px;
                    }
                    final int fromFrame = (int) Math.max(0, Math.floor((roundStartX - L) / frameWidth));
                    final int toFrame = (int) Math.min(roundThumbs.count, Math.ceil((roundEndX - roundStartX) / frameWidth) + 1);

                    final int y = (int) roundBounds.top;

                    boolean allLoaded = roundThumbs.frames.size() >= toFrame;
                    boolean fullyCovered = allLoaded;
                    if (fullyCovered) {
                        for (int i = fromFrame; i < Math.min(roundThumbs.frames.size(), toFrame); ++i) {
                            VideoThumbsLoader.BitmapFrame frame = roundThumbs.frames.get(i);
                            if (frame.bitmap == null) {
                                fullyCovered = false;
                                break;
                            }
                        }
                    }

                    if (!fullyCovered) {
                        if (blurManager.hasRenderNode()) {
                            backgroundBlur.drawRect(canvas);
                            canvas.drawColor(0x33000000);
                        } else if (blurPaint == null) {
                            canvas.drawColor(0x40000000);
                        } else {
                            canvas.drawRect(roundBounds, blurPaint);
                            canvas.drawColor(0x33000000);
                        }
                    }

                    if (frameWidth != 0) {
                        for (int i = fromFrame; i < Math.min(roundThumbs.frames.size(), toFrame); ++i) {
                            VideoThumbsLoader.BitmapFrame frame = roundThumbs.frames.get(i);
                            if (frame.bitmap != null) {
                                videoFramePaint.setAlpha((int) (0xFF * frame.getAlpha()));
                                canvas.drawBitmap(frame.bitmap, x, y - (int) ((frame.bitmap.getHeight() - roundHeight) / 2f), videoFramePaint);
                            }
                            x += frameWidth;
                        }
                    }

                    if (!allLoaded) {
                        roundThumbs.load();
                    }
                }
                selectedVideoClipPath.rewind();
                AndroidUtilities.rectTmp.set(
                        px + ph + (roundLeft * roundDuration - scroll + roundOffset) / (float) scrollDuration * sw - (roundLeft <= 0 ? ph : 0) - ph * (1f - roundSelected),
                        roundBounds.top,
                        px + ph + (roundRight * roundDuration - scroll + roundOffset) / (float) scrollDuration * sw + (roundRight >= 1 ? ph : 0) + ph * (1f - roundSelected),
                        roundBounds.bottom
                );
                selectedVideoClipPath.addRoundRect(
                        AndroidUtilities.rectTmp,
                        selectedVideoRadii,
                        Path.Direction.CW
                );
                canvas.clipPath(selectedVideoClipPath, Region.Op.DIFFERENCE);
                canvas.drawColor(0x50000000);
                canvas.restore();
            }

            // draw audio
            float audioT = this.audioT.set(hasAudio);
            float audioSelected = this.audioSelectedT.set(hasAudio && this.audioSelected);
            final float audioHeight = getAudioHeight() * audioT;
            final float audio = audioT * (videoTrack != null || hasRound || !collageTracks.isEmpty() ? audioSelected : 1);
            if (audioT > 0) {
                left += (audioOffset + audioLeft * audioDuration) * audio;
                right += (audioOffset + audioRight * audioDuration) * audio;

                final Paint audioBlurPaint = audioBlur.getPaint(audioT);
                canvas.save();
                float _left, _right;
                if (videoTrack != null || hasRound || !collageTracks.isEmpty()) {
                    _left = px + ph + (audioOffset - scroll + lerp(audioLeft, 0, audioSelected) * audioDuration) / (float) scrollDuration * sw;
                    _right = px + ph + (audioOffset - scroll + lerp(audioRight, 1, audioSelected) * audioDuration) / (float) scrollDuration * sw;
                } else {
                    _left = px + ph + (audioOffset - scroll) / (float) scrollDuration * sw;
                    _right = px + ph + (audioOffset - scroll + audioDuration) / (float) scrollDuration * sw;
                }

                audioBounds.set(_left - ph, bottom - audioHeight, _right + ph, bottom);
                bottom -= audioHeight + p * audioT;
                regionTop += audioBounds.top * audio;
                regionBottom += audioBounds.bottom * audio;
                audioClipPath.rewind();
                audioClipPath.addRoundRect(audioBounds, dp(8), dp(8), Path.Direction.CW);
                canvas.clipPath(audioClipPath);

                if (blurManager != null && blurManager.hasRenderNode()) {
                    backgroundBlur.drawRect(canvas);
                    canvas.drawColor(Theme.multAlpha(0x33000000, audioT));
                } else if (audioBlurPaint == null) {
                    canvas.drawColor(Theme.multAlpha(0x40000000, audioT));
                } else {
                    canvas.drawRect(audioBounds, audioBlurPaint);
                    canvas.drawColor(Theme.multAlpha(0x33000000, audioT));
                }

                if (waveform != null && blurManager != null && blurManager.hasRenderNode()) {
                    final float maxBar = waveformMax.set(waveform.getMaxBar(), !waveformIsLoaded);
                    waveformIsLoaded = waveform.getLoadedCount() > 0;
                    final float start = px + ph + (audioOffset - scroll) / (float) scrollDuration * sw;
                    waveformPath.check(start, _left, _right, audioSelected, scrollDuration, audioHeight, maxBar, audioBounds.bottom, waveform);
                    canvas.saveLayerAlpha(audioBounds, (int) (0xFF * 0.4f), Canvas.ALL_SAVE_FLAG);
                    canvas.clipPath(waveformPath);
                    audioWaveformBlur.drawRect(canvas);
                    canvas.restore();
                } else if (waveform != null && audioBlurPaint != null) {
                    Paint paint = audioWaveformBlur.getPaint(.4f * audioT);
                    if (paint == null) {
                        paint = waveformPaint;
                        paint.setAlpha((int) (0x40 * audioT));
                    }
                    final float maxBar = waveformMax.set(waveform.getMaxBar(), !waveformIsLoaded);
                    waveformIsLoaded = waveform.getLoadedCount() > 0;
                    final float start = px + ph + (audioOffset - scroll) / (float) scrollDuration * sw;
                    waveformPath.check(start, _left, _right, audioSelected, scrollDuration, audioHeight, maxBar, audioBounds.bottom, waveform);
                    canvas.drawPath(waveformPath, paint);
                }

                if (audioSelected < 1) {
                    final float tleft = px + ph + (audioOffset - scroll + audioLeft * audioDuration) / (float) scrollDuration * sw;
                    final float tright = px + ph + (audioOffset - scroll + audioRight * audioDuration) / (float) scrollDuration * sw;

                    final float textCx = (Math.max(px, tleft) + Math.min(w - px, tright)) / 2f;
                    final float textCy = audioBounds.centerY();
                    final float textMaxWidth = Math.max(0, Math.min(w - px, tright) - Math.max(px, tleft) - dp(24));
                    float textWidth = dpf2(13) + (audioAuthor == null && audioTitle == null ? 0 : dpf2(3.11f) + audioAuthorWidth + dpf2(3.66f + 2 + 4) + audioTitleWidth);
                    final boolean fit = textWidth < textMaxWidth;

                    float x = textCx - Math.min(textWidth, textMaxWidth) / 2f;
                    audioIcon.setBounds((int) x, (int) (textCy - dp(13) / 2f), (int) (x + dp(13)), (int) (textCy + dp(13) / 2f));
                    audioIcon.setAlpha((int) (0xFF * (1f - audioSelected)));
                    audioIcon.draw(canvas);
                    x += dpf2(13 + 3.11f);
                    canvas.saveLayerAlpha(0, 0, w, h, 0xFF, Canvas.ALL_SAVE_FLAG);
                    final float x2 = Math.min(tright, w) - dp(12);
                    canvas.clipRect(x, 0, x2, h);
                    if (audioAuthor != null) {
                        canvas.save();
                        canvas.translate(x - audioAuthorLeft, textCy - audioAuthor.getHeight() / 2f);
                        audioAuthorPaint.setAlpha((int) (0xFF * (1f - audioSelected) * audioT));
                        audioAuthor.draw(canvas);
                        canvas.restore();
                        x += audioAuthorWidth;
                    }
                    if (audioAuthor != null && audioTitle != null) {
                        x += dpf2(3.66f);
                        int wasAlpha = audioDotPaint.getAlpha();
                        audioDotPaint.setAlpha((int) (wasAlpha * (1f - audioSelected)));
                        canvas.drawCircle(x + dp(1), textCy, dp(1), audioDotPaint);
                        audioDotPaint.setAlpha(wasAlpha);
                        x += dpf2(2);
                        x += dpf2(4);
                    }
                    if (audioTitle != null) {
                        canvas.save();
                        canvas.translate(x - audioTitleLeft, textCy - audioTitle.getHeight() / 2f);
                        audioTitlePaint.setAlpha((int) (0xFF * (1f - audioSelected) * audioT));
                        audioTitle.draw(canvas);
                        canvas.restore();
                    }
                    if (!fit) {
                        ellipsizeMatrix.reset();
                        ellipsizeMatrix.postScale(dpf2(8) / 16, 1);
                        ellipsizeMatrix.postTranslate(x2 - dp(8), 0);
                        ellipsizeGradient.setLocalMatrix(ellipsizeMatrix);
                        canvas.drawRect(x2 - dp(8), audioBounds.top, x2, audioBounds.bottom, ellipsizePaint);
                    }
                    canvas.restore();
                }
                canvas.restore();
            }

            // draw region
            float leftX = px + ph + (left - scroll) / (float) scrollDuration * sw;
            float rightX = px + ph + (right - scroll) / (float) scrollDuration * sw;
            float progressAlpha = !collageTracks.isEmpty() ? collageT : (hasAudio && videoTrack == null ? audioT : Math.max(videoT, roundT));
            if (audioT > 0. || roundT > 0. || videoT > 0. || collageT > 0.) {
                drawRegion(canvas, blurPaint, regionTop, regionBottom, leftX, rightX, (videoTrack != null || hasRound || !collageTracks.isEmpty() ? 1 : lerp(.6f, 1f, audioSelected) * audioT) * progressAlpha);
                if (videoTrack != null && (hasAudio || hasRound) && (audioSelected > 0 || roundSelected > 0)) {
                    drawRegion(
                        canvas,
                        blurPaint,
                        h - py - videoHeight,
                        h - py,
                        ph + px + (videoTrack.left * videoTrack.duration - scroll) / (float) scrollDuration * sw,
                        ph + px + (videoTrack.right * videoTrack.duration - scroll) / (float) scrollDuration * sw,
                        .8f
                    );
                } else if (collageMain != null && collageTracks.size() > 1) {
                    drawRegion(
                        canvas,
                        blurPaint,
                        collageMain.bounds.top,
                        collageMain.bounds.bottom,
                        ph + px + (collageMain.offset + collageMain.left * collageMain.duration - scroll) / (float) scrollDuration * sw,
                        ph + px + (collageMain.offset + collageMain.right * collageMain.duration - scroll) / (float) scrollDuration * sw,
                        .8f
                    );
                }

                // draw progress
                float loopT = loopProgress.set(0);
                final float y1 = h - getContentHeight() + py - dpf2(2.3f);
                final float y2 = h - py + dpf2(4.3f);
                if (loopT > 0) {
                    final long end;
                    if (loopProgressFrom != -1) {
                        end = loopProgressFrom;
                    } else if (videoTrack != null) {
                        end = (long) (videoTrack.duration * videoTrack.right);
                    } else if (collageMain != null) {
                        end = (long) (collageMain.duration * (collageMain.right - collageMain.left));
                    } else if (hasRound) {
                        end = (long) (roundDuration * roundRight);
                    } else {
                        end = (long) (audioDuration * audioRight);
                    }
                    drawProgress(canvas, y1, y2, end, loopT * progressAlpha);
                }
                drawProgress(canvas, y1, y2, progress, (1f - loopT) * progressAlpha);
            }

            if (restore) {
                canvas.restore();
            }
        }

        if (dragged) {
            long Δd = (long) (dp(32) / (float) sw * scrollDuration * (1f / (1000f / AndroidUtilities.screenRefreshRate)));
            if (pressHandle == HANDLE_VIDEO_REGION && videoTrack != null) {
                int direction = 0;
                if (videoTrack.left < (scroll / (float) videoTrack.duration)) {
                    direction = -1;
                } else if (videoTrack.right > ((scroll + scrollDuration) / (float) videoTrack.duration)) {
                    direction = +1;
                }
                long wasScroll = scroll;
                scroll = Utilities.clamp(scroll + direction * Δd, videoTrack.duration - scrollDuration, 0);
                progress += direction * Δd;
                float d = (scroll - wasScroll) / (float) videoTrack.duration;
                if (d > 0) {
                    d = Math.min(1 - videoTrack.right, d);
                } else {
                    d = Math.max(0 - videoTrack.left, d);
                }
                videoTrack.left = Utilities.clamp(videoTrack.left + d, 1, 0);
                videoTrack.right = Utilities.clamp(videoTrack.right + d, 1, 0);
                if (delegate != null) {
                    delegate.onVideoLeftChange(videoTrack.left);
                    delegate.onVideoRightChange(videoTrack.right);
                }
                invalidate();
            } else if (pressHandle == HANDLE_AUDIO_REGION) {
                int direction = 0;
                if (audioLeft < ((-audioOffset + 100) / (float) audioDuration)) {
                    direction = -1;
                } else if (audioRight >= ((-audioOffset + scrollDuration - 100) / (float) audioDuration)) {
                    direction = +1;
                }
                if (direction != 0) {
                    long wasOffset = audioOffset;
                    if (this.audioSelected && videoTrack != null) {
                        audioOffset = Utilities.clamp(audioOffset - direction * Δd, (long) ((videoTrack.right * videoTrack.duration) - (audioLeft * audioDuration)), (long) ((videoTrack.left * videoTrack.duration) - (audioRight * audioDuration)));
                    } else if (this.roundSelected && hasRound) {
                        audioOffset = Utilities.clamp(audioOffset - direction * Δd, (long) ((roundRight * roundDuration) - (audioLeft * audioDuration)), (long) ((roundLeft * roundDuration) - (audioRight * audioDuration)));
                    } else{
                        audioOffset = Utilities.clamp(audioOffset - direction * Δd, 0, (long) -(audioDuration - Math.min(getBaseDuration(), getMaxScrollDuration())));
                    }
                    float d = -(audioOffset - wasOffset) / (float) audioDuration;
                    if (d > 0) {
                        d = Math.min(1 - audioRight, d);
                    } else {
                        d = Math.max(0 - audioLeft, d);
                    }
                    if (videoTrack == null) {
                        progress = (long) Utilities.clamp(progress + d * audioDuration, audioDuration, 0);
                    }
                    audioLeft = Utilities.clamp(audioLeft + d, 1, 0);
                    audioRight = Utilities.clamp(audioRight + d, 1, 0);
                    if (delegate != null) {
                        delegate.onAudioLeftChange(audioLeft);
                        delegate.onAudioRightChange(audioRight);
                        delegate.onProgressChange(progress, false);
                    }
                    invalidate();
                }
            }
        }

    }

    private void drawRegion(Canvas canvas, Paint blurPaint, float top, float bottom, float left, float right, float alpha) {
        if (alpha <= 0) {
            return;
        }

        AndroidUtilities.rectTmp.set(left - dp(10), top, right + dp(10), bottom);
        canvas.saveLayerAlpha(0, 0, w, h, 0xFF, Canvas.ALL_SAVE_FLAG);
        regionPaint.setAlpha((int) (0xFF * alpha));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), regionPaint);
        AndroidUtilities.rectTmp.inset(dp(isCover ? 2.5f : 10), dp(2));
        if (isCover) {
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(3), dp(3), regionCutPaint);
        } else {
            canvas.drawRect(AndroidUtilities.rectTmp, regionCutPaint);
        }

        final float hw = dp(2), hh = dp(10);
        Paint handlePaint = blurPaint != null ? blurPaint : regionHandlePaint;
        regionHandlePaint.setAlpha(0xFF);
        handlePaint.setAlpha((int) (0xFF * alpha));
        AndroidUtilities.rectTmp.set(
                left - (dp(isCover ? 2 : 10) - hw) / 2f,
                (top + bottom - hh) / 2f,
                left - (dp(isCover ? 2 : 10) + hw) / 2f,
                (top + bottom + hh) / 2f
        );
        if (!isCover) {
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), handlePaint);
            if (blurPaint != null && !isCover) {
                regionHandlePaint.setAlpha((int) (0x30 * alpha));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), regionHandlePaint);
            }
        }
        AndroidUtilities.rectTmp.set(
                right + (dp(isCover ? 2.5f : 10) - hw) / 2f,
                (top + bottom - hh) / 2f,
                right + (dp(isCover ? 2.5f : 10) + hw) / 2f,
                (top + bottom + hh) / 2f
        );
        if (!isCover) {
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), handlePaint);
            if (blurPaint != null) {
                regionHandlePaint.setAlpha((int) (0x30 * alpha));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), regionHandlePaint);
            }
        }

        canvas.restore();
    }

    private void drawProgress(Canvas canvas, float y1, float y2, long progress, float scale) {
        if (isCover) return;

        final long scrollDuration = Math.min(getBaseDuration(), getMaxScrollDuration());

        final float progressT = (Utilities.clamp(progress, getBaseDuration(), 0) + (collageMain != null ? collageMain.offset + collageMain.left * collageMain.duration : (videoTrack == null ? audioOffset : 0)) - scroll) / (float) scrollDuration;
        final float progressX = px + ph + sw * progressT;

        final float yd = (y2 - y1) / 2;
        y1 += yd / 2f * (1f - scale);
        y2 -= yd / 2f * (1f - scale);
        progressShadowPaint.setAlpha((int) (0x26 * scale));
        progressWhitePaint.setAlpha((int) (0xFF * scale));

        AndroidUtilities.rectTmp.set(progressX - dpf2(1.5f), y1, progressX + dpf2(1.5f), y2);
        AndroidUtilities.rectTmp.inset(-dpf2(0.66f), -dpf2(0.66f));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), progressShadowPaint);
        AndroidUtilities.rectTmp.set(progressX - dpf2(1.5f), y1, progressX + dpf2(1.5f), y2);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), progressWhitePaint);
    }

    private int sw;
    private int w, h, ph, px, py;

    public static int heightDp() {
        final int maxCollageCount = 9;
        return 5 + 38 + 4 + 28 + 4 + 28 + 5 + (maxCollageCount - 1) * 28 + (maxCollageCount > 0 ? 32 : 0) + 5 * 4;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        audioAuthorPaint.setTextSize(dp(12));
        audioTitlePaint.setTextSize(dp(12));
        setPadding(px = dp(12), py = dp(5), dp(12), dp(5));
        setMeasuredDimension(w = MeasureSpec.getSize(widthMeasureSpec), h = dp(heightDp()));
        ph = dp(10);
        sw = w - 2 * ph - 2 * px;
        if (videoTrack != null && videoTrack.path != null && videoTrack.thumbs == null) {
            videoTrack.setupThumbs(false);
        }
        if (!collageTracks.isEmpty()) {
            for (Track track : collageTracks) {
                if (track.path != null && track.thumbs == null) {
                    track.setupThumbs(false);
                    track.setupWaveform(false);
                }
            }
        }
        if (audioPath != null && this.waveform == null) {
            setupAudioWaveform();
        }
    }

    private class VideoThumbsLoader {

        private long duration;
        private volatile long frameIterator;
        private int count;

        private final ArrayList<BitmapFrame> frames = new ArrayList<>();
        private MediaMetadataRetriever metadataRetriever;

        private volatile int frameWidth;
        private volatile int frameHeight;
        private final boolean isRound;

        private boolean destroyed;

        public VideoThumbsLoader(boolean isRound, String path, int uiWidth, int uiHeight, Long overrideDuration) {
            this(isRound, path, uiWidth, uiHeight, overrideDuration, getMaxScrollDuration(), -1, -1, null);
        }

        public VideoThumbsLoader(boolean isRound, String path, int uiWidth, int uiHeight, Long overrideDuration, long maxDuration) {
            this(isRound, path, uiWidth, uiHeight, overrideDuration, maxDuration, -1, -1, null);
        }

        public VideoThumbsLoader(boolean isRound, String path, int uiWidth, int uiHeight, Long overrideDuration, long maxDuration, long startFrom, long endTo, Runnable inited) {
            this.isRound = isRound;
            metadataRetriever = new MediaMetadataRetriever();
            Utilities.themeQueue.postRunnable(() -> {
                long duration = getMaxScrollDuration();
                int width = 0;
                int height = 0;
                try {
                    metadataRetriever.setDataSource(path);
                    String value;
                    value = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (value != null) {
                        this.duration = duration = Long.parseLong(value);
                    }
                    value = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    if (value != null) {
                        width = Integer.parseInt(value);
                    }
                    value = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    if (value != null) {
                        height = Integer.parseInt(value);
                    }
                    value = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    if (value != null) {
                        int orientation = Integer.parseInt(value);
                        if (orientation == 90 || orientation == 270) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
                    }
                } catch (Exception e) {
                    metadataRetriever = null;
                    FileLog.e(e);
                }
                if (overrideDuration != null) {
                    this.duration = duration = overrideDuration;
                }
                if (startFrom != -1 && endTo != -1) {
                    duration = endTo - startFrom;
                }
                float aspectRatio = 1;
                if (width != 0 && height != 0) {
                    aspectRatio = width / (float) height;
                }
                aspectRatio = Utilities.clamp(aspectRatio, 4 / 3f, 9f / 16f);
                frameHeight = Math.max(1, uiHeight);
                frameWidth = Math.max(1, (int) Math.ceil(uiHeight * aspectRatio));
                final float uiScrollWidth = Math.max(duration, maxDuration) / (float) maxDuration * uiWidth;
                count = (int) Math.ceil(uiScrollWidth / frameWidth);
                frameIterator = (long) (duration / (float) count);
                nextFrame = -frameIterator;
                if (startFrom != -1) {
                    nextFrame = startFrom - frameIterator;
                }
                load();
                if (inited != null) {
                    AndroidUtilities.runOnUIThread(inited);
                }
            });
        }

        public int getFrameWidth() {
            return frameWidth;
        }

        public long getDuration() {
            return duration;
        }

        public BitmapFrame getFrameAt(int index) {
            if (index < 0 || index >= frames.size()) {
                return null;
            }
            return frames.get(index);
        }

        public int getLoadedCount() {
            return frames.size();
        }

        public int getCount() {
            return count;
        }

        private long nextFrame;
        private boolean loading = false;

        // (ui) load() -> (themeQueue) retrieveFrame() -> (ui) receiveFrame()
        public void load() {
            if (loading || metadataRetriever == null || frames.size() >= count) {
                return;
            }
            loading = true;
            nextFrame += frameIterator;
            Utilities.themeQueue.cancelRunnable(this::retrieveFrame);
            Utilities.themeQueue.postRunnable(this::retrieveFrame);
        }

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        private Path clipPath;
        private void retrieveFrame() {
            if (metadataRetriever == null) {
                return;
            }

            Bitmap bitmap = null;
            try {
                bitmap = metadataRetriever.getFrameAtTime(nextFrame * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bitmap != null) {
                    Bitmap scaledBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(scaledBitmap);
                    float scale = Math.max((float) frameWidth / bitmap.getWidth(), (float) frameHeight / bitmap.getHeight());
                    Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    Rect dest = new Rect(
                        (int) ((scaledBitmap.getWidth() -  bitmap.getWidth()  * scale) / 2f),
                        (int) ((scaledBitmap.getHeight() - bitmap.getHeight() * scale) / 2f),
                        (int) ((scaledBitmap.getWidth() +  bitmap.getWidth()  * scale) / 2f),
                        (int) ((scaledBitmap.getHeight() + bitmap.getHeight() * scale) / 2f)
                    );
                    if (isRound) {
                        if (clipPath == null) {
                            clipPath = new Path();
                        }
                        clipPath.rewind();
                        clipPath.addCircle(frameWidth / 2f, frameHeight / 2f, Math.min(frameWidth, frameHeight) / 2f, Path.Direction.CW);
                        canvas.clipPath(clipPath);
                    }
                    canvas.drawBitmap(bitmap, src, dest, bitmapPaint);
                    bitmap.recycle();
                    bitmap = scaledBitmap;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            final Bitmap finalBitmap = bitmap;
            AndroidUtilities.runOnUIThread(() -> receiveFrame(finalBitmap));
        };

        private void receiveFrame(Bitmap bitmap) {
            if (!loading || destroyed) {
                return;
            }
            frames.add(new BitmapFrame(bitmap));
            loading = false;
            invalidate();
        }

        public void destroy() {
            destroyed = true;
            Utilities.themeQueue.cancelRunnable(this::retrieveFrame);
            for (BitmapFrame frame : frames) {
                if (frame.bitmap != null) {
                    frame.bitmap.recycle();
                }
            }
            frames.clear();
            if (metadataRetriever != null) {
                try {
                    metadataRetriever.release();
                } catch (Exception e) {
                    metadataRetriever = null;
                    FileLog.e(e);
                }
            }
        }

        public class BitmapFrame {
            public Bitmap bitmap;
            private final AnimatedFloat alpha = new AnimatedFloat(0, TimelineView.this, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);

            public BitmapFrame(Bitmap bitmap) {
                this.bitmap = bitmap;
            }

            public float getAlpha() {
                return alpha.set(1);
            }
        }
    }

    private class AudioWaveformLoader {
        private final AnimatedFloat animatedLoaded = new AnimatedFloat(TimelineView.this, 0, 600, CubicBezierInterpolator.EASE_OUT_QUINT);

        private final int count;
        private int loaded = 0;
        private final short[] data;
        private short max;

        private final MediaExtractor extractor;
        private MediaFormat inputFormat;
        private long duration;

        private final Object lock = new Object();
        private boolean stop = false;

        private FfmpegAudioWaveformLoader waveformLoader;

        public AudioWaveformLoader(String path, int uiWidth) {

            extractor = new MediaExtractor();
            String mime = null;
            try {
                extractor.setDataSource(path);

                int numTracks = extractor.getTrackCount();
                for (int i = 0; i < numTracks; ++i) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        inputFormat = format;
                        break;
                    }
                }

                if (inputFormat != null) {
                    duration = inputFormat.getLong(MediaFormat.KEY_DURATION) / 1_000_000L;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            final float videoScrollWidth = Math.min(videoTrack != null ? videoTrack.duration : (!collageTracks.isEmpty() ? getBaseDuration() : (hasRound ? roundDuration : duration * 1000)), getMaxScrollDuration());
            final float uiScrollWidth = (duration * 1000) / videoScrollWidth * uiWidth;
            final int sampleWidth = Math.round(dpf2(3.3333f));
            count = Math.min(Math.round(uiScrollWidth / sampleWidth), 4000);
            data = new short[count];

            if (duration > 0 && inputFormat != null) {
                if ("audio/mpeg".equals(mime) || "audio/mp3".equals(mime) || "audio/mp4a".equals(mime) || "audio/mp4a-latm".equals(mime)) {
                    waveformLoader = new FfmpegAudioWaveformLoader(path, count, this::receiveData);
                } else {
                    Utilities.phoneBookQueue.postRunnable(this::run);
                }
            }
        }

        private void run() {
            try {
                final int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                final int skip = 4;
                final int barWidth = (int) Math.round(duration * sampleRate / (float) count / (1 + skip));
                final long chunkTime = 2500;
                String mime = inputFormat.getString(MediaFormat.KEY_MIME);
                final MediaCodec decoder = MediaCodec.createDecoderByType(mime);
                if (decoder == null) {
                    return;
                }
                decoder.configure(inputFormat, null, null, 0);
                decoder.start();

                ByteBuffer[] inputBuffers = decoder.getInputBuffers();
                ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
                int outputBufferIndex = -1;

                int chunkindex = 0;
                short[] chunk = new short[32];

                int index = 0, count = 0;
                short peak = 0;
                boolean end = false;

                do {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int inputBufferIndex = decoder.dequeueInputBuffer(chunkTime);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer;
                        if (Build.VERSION.SDK_INT < 21) {
                            inputBuffer = inputBuffers[inputBufferIndex];
                        } else {
                            inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        }
                        int size = extractor.readSampleData(inputBuffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            end = true;
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }

                    if (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer;
                        if (Build.VERSION.SDK_INT < 21) {
                            outputBuffer = outputBuffers[outputBufferIndex];
                        } else {
                            outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                        }
                        // Ensure that the data is placed at the start of the buffer
                        outputBuffer.position(0);
                    }

                    outputBufferIndex = decoder.dequeueOutputBuffer(info, chunkTime);
                    while (outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER && !end) {
                        if (outputBufferIndex >= 0) {
                            ByteBuffer outputBuffer;
                            if (Build.VERSION.SDK_INT < 21) {
                                outputBuffer = outputBuffers[outputBufferIndex];
                            } else {
                                outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                            }
                            if (outputBuffer != null && info.size > 0) {
                                while (outputBuffer.remaining() > 0) {
                                    byte a = outputBuffer.get();
                                    byte b = outputBuffer.get();

                                    short value = (short) (((b & 0xFF) << 8) | (a & 0xFF));

                                    if (count >= barWidth) {
                                        chunk[index - chunkindex] = peak;
                                        index++;
                                        if (index - chunkindex >= chunk.length || index >= this.count) {
                                            short[] dataToSend = chunk;
                                            int length = index - chunkindex;
                                            chunk = new short[chunk.length];
                                            chunkindex = index;
                                            AndroidUtilities.runOnUIThread(() -> receiveData(dataToSend, length));
                                        }
                                        peak = 0;
                                        count = 0;
                                        if (index >= data.length) {
                                            break;
                                        }
                                    }

                                    if (peak < value) {
                                        peak = value;
                                    }
                                    count++;

                                    if (outputBuffer.remaining() < skip * 2)
                                        break;
                                    outputBuffer.position(outputBuffer.position() + skip * 2);
                                }
                            }
                            decoder.releaseOutputBuffer(outputBufferIndex, false);

                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                end = true;
                                break;
                            }

                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = decoder.getOutputBuffers();
                        }
                        outputBufferIndex = decoder.dequeueOutputBuffer(info, chunkTime);
                    }
                    synchronized (lock) {
                        if (stop) {
                            break;
                        }
                    }
                } while (!end && index < this.count);

                decoder.stop();
                decoder.release();
                extractor.release();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void receiveData(short[] data, int len) {
            for (int i = 0; i < len; ++i) {
                if (loaded + i >= this.data.length) {
                    break;
                }
                this.data[loaded + i] = data[i];
                if (max < data[i]) {
                    max = data[i];
                }
            }
            loaded += len;
            invalidate();
        }

        public void destroy() {
            if (waveformLoader != null) {
                waveformLoader.destroy();
            }
            Utilities.phoneBookQueue.cancelRunnable(this::run);
            synchronized (lock) {
                stop = true;
            }
        }

        public short getMaxBar() {
            return max;
        }

        public short getBar(int index) {
            return data[index];
        }

        public int getLoadedCount() {
            return loaded;
        }

        public int getCount() {
            return count;
        }
    }

    public int getTimelineHeight() {
        return lerp(py + dp(28) + py, getContentHeight(), openT.get());
    }

    public int getContentHeight() {
        return (int) (py + (videoTrack != null ? getVideoHeight() + dp(4) : 0) + (collageTracks.isEmpty() ? 0 : getCollageHeight() + dp(4)) + (hasRound ? getRoundHeight() + dp(4) : 0) + (hasAudio ? getAudioHeight() + dp(4) : 0) + py);
    }
}
