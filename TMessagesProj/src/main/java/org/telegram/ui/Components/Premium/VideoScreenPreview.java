package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.PremiumPreviewFragment;

import java.io.File;
import java.net.URLEncoder;

public class VideoScreenPreview extends FrameLayout implements PagerHeaderView, NotificationCenter.NotificationCenterDelegate {

    private final SvgHelper.SvgDrawable svgIcon;
    Paint phoneFrame1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint phoneFrame2 = new Paint(Paint.ANTI_ALIAS_FLAG);

    boolean fromTop = false;
    File file;
    float aspectRatio;
    String attachFileName;
    ImageReceiver imageReceiver = new ImageReceiver(this);

    Runnable nextCheck;

    private void checkVideo() {
        if (file != null && file.exists() || SharedConfig.streamMedia) {
            if (file != null && file.exists()) {
                if ((NotificationCenter.getGlobalInstance().getCurrentHeavyOperationFlags() & 512) != 0) {
                    if (nextCheck != null) {
                        AndroidUtilities.cancelRunOnUIThread(nextCheck);
                    }
                    AndroidUtilities.runOnUIThread(nextCheck = this::checkVideo, 300);
                    return;
                }

                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(ApplicationLoader.applicationContext, Uri.fromFile(file));
                    int width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    int height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    retriever.release();
                    aspectRatio = width / (float) height;
                } catch (Exception e) {
                    aspectRatio = 0.671f;
                }
            } else {
                aspectRatio = 0.671f;
            }

            if (allowPlay) {
                runVideoPlayer();
            }
        }
        nextCheck = null;
    }

    int currentAccount;
    int type;
    boolean visible;
    boolean attached;
    boolean play;
    boolean allowPlay;
    boolean firstFrameRendered;

    float progress;
    VideoPlayer videoPlayer;
    AspectRatioFrameLayout aspectRatioFrameLayout;
    TextureView textureView;

    RoundedBitmapDrawable roundedBitmapDrawable;
    CellFlickerDrawable.DrawableInterface cellFlickerDrawable;
    private float roundRadius;
    StarParticlesView.Drawable starDrawable;
    SpeedLineParticles.Drawable speedLinesDrawable;
    HelloParticles.Drawable helloParticlesDrawable;
    private final static float[] speedScaleVideoTimestamps = new float[]{0.02f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.02f};
    private MatrixParticlesDrawable matrixParticlesDrawable;

    private TLRPC.Document document;

    public VideoScreenPreview(Context context, SvgHelper.SvgDrawable svgDrawable, int currentAccount, int type) {
        super(context);
        this.currentAccount = currentAccount;
        this.type = type;
        this.svgIcon = svgDrawable;

        phoneFrame1.setColor(Color.BLACK);
        phoneFrame2.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_premiumGradient2), Color.BLACK, 0.5f));
        imageReceiver.setLayerNum(Integer.MAX_VALUE);
        setVideo();

        if (type == PremiumPreviewFragment.PREMIUM_FEATURE_UPLOAD_LIMIT) {
            matrixParticlesDrawable = new MatrixParticlesDrawable();
            matrixParticlesDrawable.init();
        } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_PROFILE_BADGE ||
                type == PremiumPreviewFragment.PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT ||
                type == PremiumPreviewFragment.PREMIUM_FEATURE_ADS ||
                type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_AVATARS ||
                type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI ||
                type == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
            starDrawable = new StarParticlesView.Drawable(40);
            starDrawable.speedScale = 3;
            starDrawable.type = type;

            if (type == PremiumPreviewFragment.PREMIUM_FEATURE_ADS) {
                starDrawable.size1 = 14;
                starDrawable.size2 = 18;
                starDrawable.size3 = 18;
            } else {
                starDrawable.size1 = 14;
                starDrawable.size2 = 16;
                starDrawable.size3 = 15;
            }
            starDrawable.k1 = starDrawable.k2 = starDrawable.k3 = 0.98f;
            starDrawable.speedScale = 4;
            starDrawable.colorKey = Theme.key_premiumStartSmallStarsColor2;
            starDrawable.init();
        } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_DOWNLOAD_SPEED) {
            speedLinesDrawable = new SpeedLineParticles.Drawable(200);
            speedLinesDrawable.init();
        } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS) {
            helloParticlesDrawable = new HelloParticles.Drawable(25);
            helloParticlesDrawable.init();
        } else {
            int particlesCount = 100;
            if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH) {
                particlesCount = 800;
            } else if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
                particlesCount = 400;
            }
            starDrawable = new StarParticlesView.Drawable(particlesCount);
            starDrawable.colorKey = Theme.key_premiumStartSmallStarsColor2;
            starDrawable.size1 = 8;
            starDrawable.size1 = 6;
            starDrawable.size1 = 4;
            starDrawable.k1 = starDrawable.k2 = starDrawable.k3 = 0.98f;
            starDrawable.useRotate = true;
            starDrawable.speedScale = 4;
            starDrawable.checkBounds = true;
            starDrawable.checkTime = true;
            starDrawable.useBlur = true;
            starDrawable.roundEffect = false;
            starDrawable.init();
        }

        if (type == PremiumPreviewFragment.PREMIUM_FEATURE_UPLOAD_LIMIT || type == PremiumPreviewFragment.PREMIUM_FEATURE_ADS || type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI) {
            fromTop = true;
        }


        aspectRatioFrameLayout = new AspectRatioFrameLayout(context) {

            Path clipPath = new Path();

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                clipPath.reset();
                if (fromTop) {
                    AndroidUtilities.rectTmp.set(0, -roundRadius, getMeasuredWidth(), getMeasuredHeight());
                } else {
                    AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), (int) (getMeasuredHeight() + roundRadius));
                }
                float rad = roundRadius - AndroidUtilities.dp(3);
                clipPath.addRoundRect(AndroidUtilities.rectTmp, rad, rad, Path.Direction.CW);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipPath(clipPath);
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };

        aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        textureView = new TextureView(context);
        aspectRatioFrameLayout.addView(textureView);
        setWillNotDraw(false);
        addView(aspectRatioFrameLayout);
    }

    private void setVideo() {
        TLRPC.TL_help_premiumPromo premiumPromo = MediaDataController.getInstance(currentAccount).getPremiumPromo();
        String typeString = PremiumPreviewFragment.featureTypeToServerString(type);
        if (premiumPromo != null) {
            int index = -1;
            for (int i = 0; i < premiumPromo.video_sections.size(); i++) {
                if (premiumPromo.video_sections.get(i).equals(typeString)) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                TLRPC.Document document = premiumPromo.videos.get(index);
                Drawable drawable = null;
                for (int i = 0; i < document.thumbs.size(); i++) {
                    if (document.thumbs.get(i) instanceof TLRPC.TL_photoStrippedSize) {
                        roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), ImageLoader.getStrippedPhotoBitmap(document.thumbs.get(i).bytes, "b"));
                        CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();
                        flickerDrawable.repeatProgress = 4f;
                        flickerDrawable.progress = 3.5f;
                        flickerDrawable.frameInside = true;
                        cellFlickerDrawable = flickerDrawable.getDrawableInterface(this, svgIcon);
                        CombinedDrawable combinedDrawable = new CombinedDrawable(roundedBitmapDrawable, cellFlickerDrawable) {
                            @Override
                            public void setBounds(int left, int top, int right, int bottom) {
                                if (fromTop) {
                                    super.setBounds(left, (int) (top - roundRadius), right, bottom);
                                } else {
                                    super.setBounds(left, top, right, (int) (bottom + roundRadius));
                                }
                            }
                        };
                        combinedDrawable.setFullsize(true);
                        drawable = combinedDrawable;
                    }
                }
                attachFileName = FileLoader.getAttachFileName(document);
                imageReceiver.setImage(null, null, drawable, null, premiumPromo, 1);
                FileLoader.getInstance(currentAccount).loadFile(document, premiumPromo, FileLoader.PRIORITY_HIGH, 0);
                this.document = document;
                Utilities.globalQueue.postRunnable(() -> {
                    File file = FileLoader.getInstance(currentAccount).getPathToAttach(document);
                    AndroidUtilities.runOnUIThread(() -> {
                        this.file = file;
                        checkVideo();
                    });
                });

            }
        }
    }

    int size;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        int size = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.9f);
        float h = size;
        float w = size * 0.671f;

        float horizontalPadding = (measuredWidth - w) / 2f;
        roundRadius = size * 0.0671f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            aspectRatioFrameLayout.invalidateOutline();
        }
        if (fromTop) {
            AndroidUtilities.rectTmp.set(horizontalPadding, 0, measuredWidth - horizontalPadding, h);
        } else {
            AndroidUtilities.rectTmp.set(horizontalPadding, measuredHeight - h, measuredWidth - horizontalPadding, measuredHeight);
        }
        aspectRatioFrameLayout.getLayoutParams().width = (int) AndroidUtilities.rectTmp.width();
        aspectRatioFrameLayout.getLayoutParams().height = (int) AndroidUtilities.rectTmp.height();
        ((MarginLayoutParams) aspectRatioFrameLayout.getLayoutParams()).leftMargin = (int) AndroidUtilities.rectTmp.left;
        ((MarginLayoutParams) aspectRatioFrameLayout.getLayoutParams()).topMargin = (int) AndroidUtilities.rectTmp.top;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int sizeInternal = getMeasuredWidth() << 16 + getMeasuredHeight();

        int size = (int) (getMeasuredHeight() * 0.9f);
        float h = size;
        float w = size * 0.671f;
        float horizontalPadding = (getMeasuredWidth() - w) / 2f;

        if (fromTop) {
            AndroidUtilities.rectTmp.set(horizontalPadding, -roundRadius, getMeasuredWidth() - horizontalPadding, h);
        } else {
            AndroidUtilities.rectTmp.set(horizontalPadding, getMeasuredHeight() - h, getMeasuredWidth() - horizontalPadding, getMeasuredHeight() + roundRadius);
        }

        if (this.size != sizeInternal) {
            this.size = sizeInternal;
            if (matrixParticlesDrawable != null) {
                matrixParticlesDrawable.drawingRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                matrixParticlesDrawable.excludeRect.set(AndroidUtilities.rectTmp);
                matrixParticlesDrawable.excludeRect.inset(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            }
            if (starDrawable != null) {
                if (type == PremiumPreviewFragment.PREMIUM_FEATURE_PROFILE_BADGE ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ADS ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_AVATARS ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
                    starDrawable.rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    starDrawable.rect.inset(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
                } else {
                    int getParticlesWidth = (int) (AndroidUtilities.rectTmp.width() * 0.4f);
                    starDrawable.rect.set(
                            AndroidUtilities.rectTmp.centerX() - getParticlesWidth,
                            AndroidUtilities.rectTmp.centerY() - getParticlesWidth,
                            AndroidUtilities.rectTmp.centerX() + getParticlesWidth,
                            AndroidUtilities.rectTmp.centerY() + getParticlesWidth);
                    starDrawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                }
                starDrawable.resetPositions();
                starDrawable.excludeRect.set(AndroidUtilities.rectTmp);
                starDrawable.excludeRect.inset(AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            }
            if (speedLinesDrawable != null) {
                speedLinesDrawable.rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                speedLinesDrawable.screenRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                speedLinesDrawable.rect.inset(AndroidUtilities.dp(100), AndroidUtilities.dp(100));
                speedLinesDrawable.rect.offset(0, getMeasuredHeight() * 0.1f);
                speedLinesDrawable.resetPositions();
            }
            if (helloParticlesDrawable != null) {
                helloParticlesDrawable.rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                helloParticlesDrawable.screenRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                helloParticlesDrawable.rect.inset(AndroidUtilities.dp(0), getMeasuredHeight() * 0.1f);
                helloParticlesDrawable.resetPositions();
            }
        }
    }


    @Override
    protected void dispatchDraw(Canvas canvas) {
        if ((starDrawable != null || speedLinesDrawable != null || helloParticlesDrawable != null || matrixParticlesDrawable != null) && progress < 0.5f) {
            float s = (float) Math.pow(1f - progress, 2f);
            canvas.save();
            canvas.scale(s, s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            if (matrixParticlesDrawable != null) {
                matrixParticlesDrawable.onDraw(canvas);
            } else if (starDrawable != null) {
                starDrawable.onDraw(canvas);
            } else if (speedLinesDrawable != null) {
                float videoSpeedScale = 0.2f;

                if (videoPlayer != null) {
                    float p = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                    p = Utilities.clamp(p, 1f, 0);
                    float step = 1f / (speedScaleVideoTimestamps.length - 1);
                    int fromIndex = (int) (p / step);
                    int toIndex = fromIndex + 1;
                    float localProgress = (p - fromIndex * step) / step;
                    if (toIndex < speedScaleVideoTimestamps.length) {
                        videoSpeedScale = speedScaleVideoTimestamps[fromIndex] * (1f - localProgress) + speedScaleVideoTimestamps[toIndex] * localProgress;
                    } else {
                        videoSpeedScale = speedScaleVideoTimestamps[fromIndex];
                    }
                }
                float progressSpeedScale = 0.1f + 0.9f * (1f - Utilities.clamp(progress / 0.1f, 1f, 0));
                speedLinesDrawable.speedScale = 150 * progressSpeedScale * videoSpeedScale;
                speedLinesDrawable.onDraw(canvas);
            } else if (helloParticlesDrawable != null) {
                helloParticlesDrawable.onDraw(canvas);
            }
            canvas.restore();
            invalidate();
        }
        int size = (int) (getMeasuredHeight() * 0.9f);
        float h = size;
        float w = size * 0.671f;
        float horizontalPadding = (getMeasuredWidth() - w) / 2f;
        roundRadius = size * 0.0671f;
        if (fromTop) {
            AndroidUtilities.rectTmp.set(horizontalPadding, -roundRadius, getMeasuredWidth() - horizontalPadding, h);
        } else {
            AndroidUtilities.rectTmp.set(horizontalPadding, getMeasuredHeight() - h, getMeasuredWidth() - horizontalPadding, getMeasuredHeight() + roundRadius);
        }
        AndroidUtilities.rectTmp.inset(-AndroidUtilities.dp(3), -AndroidUtilities.dp(3));
        AndroidUtilities.rectTmp.inset(-AndroidUtilities.dp(3), -AndroidUtilities.dp(3));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, roundRadius + AndroidUtilities.dp(3), roundRadius + AndroidUtilities.dp(3), phoneFrame2);
        AndroidUtilities.rectTmp.inset(AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, roundRadius, roundRadius, phoneFrame1);

        if (fromTop) {
            AndroidUtilities.rectTmp.set(horizontalPadding, 0, getMeasuredWidth() - horizontalPadding, h);
        } else {
            AndroidUtilities.rectTmp.set(horizontalPadding, getMeasuredHeight() - h, getMeasuredWidth() - horizontalPadding, getMeasuredHeight());
        }

        roundRadius -= AndroidUtilities.dp(3);
        if (roundedBitmapDrawable != null) {
            roundedBitmapDrawable.setCornerRadius(roundRadius);
        }
        if (cellFlickerDrawable != null) {
            cellFlickerDrawable.radius = roundRadius;
        }
        if (fromTop) {
            imageReceiver.setRoundRadius(0, 0, (int) roundRadius, (int) roundRadius);
        } else {
            imageReceiver.setRoundRadius((int) roundRadius, (int) roundRadius, 0, 0);
        }
        if (!firstFrameRendered) {
            imageReceiver.setImageCoords(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top, AndroidUtilities.rectTmp.width(), AndroidUtilities.rectTmp.height());
            imageReceiver.draw(canvas);
        }
        super.dispatchDraw(canvas);

        if (!fromTop) {
            canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getImageY() + AndroidUtilities.dp(12), AndroidUtilities.dp(6), phoneFrame1);
        }

    }

    @Override
    public void setOffset(float translationX) {
        boolean localVisible;
        boolean localAllowPlay;
        if (translationX < 0) {
            float p = (-translationX / (float) getMeasuredWidth());
            setAlpha(0.5f + Utilities.clamp(1f - p, 1f, 0) * 0.5f);
            setRotationY(50 * p);
            invalidate();
            if (fromTop) {
                setTranslationY(-getMeasuredHeight() * 0.3f * p);
            } else {
                setTranslationY(getMeasuredHeight() * 0.3f * p);
            }
            progress = Math.abs(p);
            localVisible = p < 1f;
            localAllowPlay = p < 0.1f;
        } else {
            float p = (-translationX / (float) getMeasuredWidth());
            invalidate();
            setRotationY(50 * p);
            if (fromTop) {
                setTranslationY(getMeasuredHeight() * 0.3f * p);
            } else {
                setTranslationY(-getMeasuredHeight() * 0.3f * p);
            }
            localVisible = p > -1f;
            localAllowPlay = p > -0.1f;
            progress = Math.abs(p);
        }
        if (localVisible != visible) {
            visible = localVisible;
            updateAttachState();
        }
        if (localAllowPlay != allowPlay) {
            allowPlay = localAllowPlay;
            imageReceiver.setAllowStartAnimation(allowPlay);
            if (allowPlay) {
                imageReceiver.startAnimation();
                runVideoPlayer();
            } else {
                stopVideoPlayer();
                imageReceiver.stopAnimation();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        updateAttachState();
        if (!firstFrameRendered) {
            checkVideo();
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        updateAttachState();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
        if (helloParticlesDrawable != null) {
            helloParticlesDrawable.recycle();
            helloParticlesDrawable = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            final String path = (String) args[0];
            if (attachFileName != null && attachFileName.equals(path)) {
                file = (File) args[1];
                checkVideo();
            }
        }
    }

    private void updateAttachState() {
        boolean localPlay = visible && attached;
        if (play != localPlay) {
            play = localPlay;
            if (play) {
                imageReceiver.onAttachedToWindow();
            } else {
                imageReceiver.onDetachedFromWindow();
            }
        }
    }

    private void runVideoPlayer() {
        if (file != null || SharedConfig.streamMedia) {
            if (videoPlayer != null) {
                return;
            }
            aspectRatioFrameLayout.setAspectRatio(aspectRatio, 0);
            videoPlayer = new VideoPlayer();
            videoPlayer.setTextureView(textureView);
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (playbackState == ExoPlayer.STATE_ENDED) {
                        videoPlayer.seekTo(0);
                        videoPlayer.play();
                    } else if (playbackState == ExoPlayer.STATE_IDLE) {
                        videoPlayer.play();
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
                    if (!firstFrameRendered) {
                        textureView.setAlpha(0);
                        textureView.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                firstFrameRendered = true;
                                invalidate();
                            }
                        }).setDuration(200);
                    }
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }

            });

            Uri uri;
            if (file != null && file.exists()) {
                uri = Uri.fromFile(file);
            } else {
                try {
                    String params = "?account=" + currentAccount +
                            "&id=" + document.id +
                            "&hash=" + document.access_hash +
                            "&dc=" + document.dc_id +
                            "&size=" + document.size +
                            "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                            "&rid=" + FileLoader.getInstance(currentAccount).getFileReference(MediaDataController.getInstance(currentAccount).getPremiumPromo()) +
                            "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                            "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
                    uri = Uri.parse("tg://" + attachFileName + params);
                } catch (Exception exception) {
                    uri = null;
                }
            }

            if (uri == null) {
                return;
            }

            videoPlayer.preparePlayer(uri, "other");
            videoPlayer.setPlayWhenReady(true);
            if (!firstFrameRendered) {
                imageReceiver.stopAnimation();
                textureView.setAlpha(0);
            }
            videoPlayer.seekTo(lastFrameTime + 60);
            videoPlayer.play();
        }
    }

    long lastFrameTime;

    private void stopVideoPlayer() {
        if (videoPlayer != null) {
            lastFrameTime = videoPlayer.getCurrentPosition();
            videoPlayer.setTextureView(null);
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
    }
}
