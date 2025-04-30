package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CrossOutDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GroupCallFullscreenAdapter;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.GroupCallActivity;
import org.webrtc.GlGenericDrawer;
import org.webrtc.RendererCommon;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class GroupCallMiniTextureView extends FrameLayout implements GroupCallStatusIcon.Callback {

    public VoIPTextureView textureView;

    public boolean showingInFullscreen;
    public GroupCallGridCell primaryView;
    public GroupCallFullscreenAdapter.GroupCallUserCell secondaryView;
    public GroupCallGridCell tabletGridView;
    public boolean animateToScrimView;
    private boolean showingAsScrimView;
    boolean isFullscreenMode;
    public boolean animateToFullscreen;
    private boolean updateNextLayoutAnimated;

    boolean attached;
    public ChatObject.VideoParticipant participant;
    GroupCallRenderersContainer parentContainer;

    ArrayList<GroupCallMiniTextureView> attachedRenderers;

    Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    LinearGradient gradientShader;
    boolean animateEnter;
    ChatObject.Call call;
    GroupCallActivity activity;

    boolean useSpanSize;
    float spanCount;
    int gridItemsCount;

    FrameLayout infoContainer;

    int currentAccount;
    private final SimpleTextView nameView;
    private int lastSize;

    private TextView stopSharingTextView;
    private TextView noRtmpStreamTextView;

    public boolean forceDetached;

    private boolean invalidateFromChild;
    private boolean checkScale;
    float progressToSpeaking;

    Paint speakingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RLottieImageView micIconView;
    private final ImageView screencastIcon;

    public boolean hasVideo;
    public float progressToNoVideoStub = 1f;
    private NoVideoStubLayout noVideoStubLayout;
    ValueAnimator noVideoStubAnimator;
    private boolean lastLandscapeMode;

    float pinchScale;
    float pinchCenterX;
    float pinchCenterY;
    float pinchTranslationX;
    float pinchTranslationY;
    boolean inPinchToZoom;

    private float progressToBackground;
    ImageReceiver imageReceiver = new ImageReceiver();

    ArrayList<Runnable> onFirstFrameRunnables = new ArrayList<>();

    private GroupCallStatusIcon statusIcon;
    private boolean swipeToBack;
    private float swipeToBackDy;

    Bitmap thumb;
    Paint thumbPaint;
    private boolean videoIsPaused;
    private float videoIsPausedProgress;
    private CrossOutDrawable pausedVideoDrawable;
    private Drawable castingScreenDrawable;
    float overlayIconAlpha;

    ImageView blurredFlippingStub;

    public boolean drawFirst;

    private boolean postedNoRtmpStreamCallback;
    private Runnable noRtmpStreamCallback = () -> {
        if (textureView.renderer.isFirstFrameRendered()) {
            return;
        }
        textureView.animate().cancel();
        textureView.animate().alpha(0f).setDuration(150).start();
        noRtmpStreamTextView.animate().cancel();
        noRtmpStreamTextView.animate().alpha(1f).setDuration(150).start();
    };

    public GroupCallMiniTextureView(GroupCallRenderersContainer parentContainer, ArrayList<GroupCallMiniTextureView> attachedRenderers, ChatObject.Call call, GroupCallActivity activity) {
        super(parentContainer.getContext());
        this.call = call;
        this.currentAccount = activity.getCurrentAccount();
        pausedVideoDrawable = new CrossOutDrawable(parentContainer.getContext(), R.drawable.calls_video, -1);
        pausedVideoDrawable.setCrossOut(true, false);
        pausedVideoDrawable.setOffsets(-dp(4), dp(6), dp(6));
        pausedVideoDrawable.setStrokeWidth(AndroidUtilities.dpf2(3.4f));

        castingScreenDrawable = parentContainer.getContext().getResources().getDrawable(R.drawable.screencast_big).mutate();

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(13));
        textPaint.setColor(Color.WHITE);

        TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint2.setTypeface(AndroidUtilities.bold());
        textPaint2.setTextSize(dp(15));
        textPaint2.setColor(Color.WHITE);

        String videoOnPauseString = LocaleController.getString(R.string.VoipVideoOnPause);
        StaticLayout staticLayout = new StaticLayout(LocaleController.getString(R.string.VoipVideoScreenSharingTwoLines), textPaint, dp(400), Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(call.chatId);
        String text = LocaleController.formatPluralString("Participants", MessagesController.getInstance(currentAccount).groupCallVideoMaxParticipants);
        StaticLayout noVideoLayout = new StaticLayout(LocaleController.formatString("VoipVideoNotAvailable", R.string.VoipVideoNotAvailable, text), textPaint, dp(400), Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
        String sharingScreenString = LocaleController.getString(R.string.VoipVideoScreenSharing);

        float textW = textPaint.measureText(videoOnPauseString);
        float textW3 = textPaint2.measureText(sharingScreenString);

        this.textureView = new VoIPTextureView(parentContainer.getContext(), false, false, true, true) {

            float overlayIconAlphaFrom;

            @Override
            public void animateToLayout() {
                super.animateToLayout();
                overlayIconAlphaFrom = overlayIconAlpha;
            }

            @Override
            protected void updateRendererSize() {
                super.updateRendererSize();
                if (blurredFlippingStub != null && blurredFlippingStub.getParent() != null) {
                    blurredFlippingStub.getLayoutParams().width = textureView.renderer.getMeasuredWidth();
                    blurredFlippingStub.getLayoutParams().height = textureView.renderer.getMeasuredHeight();
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (!renderer.isFirstFrameRendered() || (renderer.getAlpha() != 1f && blurRenderer.getAlpha() != 1f) || videoIsPaused) {
                    if (progressToBackground != 1f) {
                        progressToBackground += 16f / 150f;
                        if (progressToBackground > 1f) {
                            progressToBackground = 1f;
                        } else {
                            invalidate();
                        }
                    }
                    if (thumb != null) {
                        canvas.save();
                        canvas.scale(currentThumbScale, currentThumbScale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
                        if (thumbPaint == null) {
                            thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            thumbPaint.setFilterBitmap(true);
                        }
                        canvas.drawBitmap(thumb, (getMeasuredWidth() - thumb.getWidth()) / 2f, (getMeasuredHeight() - thumb.getHeight()) / 2f, thumbPaint);
                        canvas.restore();
                    } else {
                        imageReceiver.setImageCoords(currentClipHorizontal, currentClipVertical, getMeasuredWidth() - currentClipHorizontal * 2, getMeasuredHeight() - currentClipVertical * 2);
                        imageReceiver.setAlpha(progressToBackground);
                        imageReceiver.draw(canvas);
                    }
                    if (participant == call.videoNotAvailableParticipant) {
                        if (showingInFullscreen || !parentContainer.inFullscreenMode) {
                            float iconSize = dp(48);
                            float x = (getMeasuredWidth() - iconSize) / 2f;
                            float y = getMeasuredHeight() / 2 - iconSize;
                            textPaint.setAlpha(255);

                            canvas.save();
                            canvas.translate(x - dp(400) / 2f + iconSize / 2f, y + iconSize + dp(10));
                            noVideoLayout.draw(canvas);
                            canvas.restore();
                        }
                        if (stopSharingTextView.getVisibility() != INVISIBLE) {
                            stopSharingTextView.setVisibility(INVISIBLE);
                        }
                    } else if (participant.presentation && participant.participant.self) {
                        if (stopSharingTextView.getVisibility() != VISIBLE) {
                            stopSharingTextView.setVisibility(VISIBLE);
                            stopSharingTextView.setScaleX(1.0f);
                            stopSharingTextView.setScaleY(1.0f);
                        }
                        float progressToFullscreen = drawFirst ? 0 : parentContainer.progressToFullscreenMode;
                        int size = dp(33);
                        if (animateToFullscreen || showingInFullscreen) {
                            size += (dp(10) + dp(39) * parentContainer.progressToFullscreenMode);
                        } else {
                            size += dp(10) * Math.max(1.0f - parentContainer.progressToFullscreenMode, showingAsScrimView || animateToScrimView ? parentContainer.progressToScrimView : 0.0f);
                        }

                        int x = (getMeasuredWidth() - size) / 2;
                        float smallProgress;
                        float smallProgress2;
                        float scrimProgress = (showingAsScrimView || animateToScrimView ? parentContainer.progressToScrimView : 0);
                        if (showingInFullscreen) {
                            smallProgress = smallProgress2 = progressToFullscreen;
                        } else {
                            smallProgress = animateToFullscreen ? parentContainer.progressToFullscreenMode : scrimProgress;
                            smallProgress2 = showingAsScrimView || animateToScrimView ? parentContainer.progressToScrimView : parentContainer.progressToFullscreenMode;
                        }
                        int y = (int) ((getMeasuredHeight() - size) / 2 - dp(28) - (dp(17) + dp(74) * (showingInFullscreen || animateToFullscreen ? parentContainer.progressToFullscreenMode : 0.0f)) * smallProgress + dp(17) * smallProgress2);
                        castingScreenDrawable.setBounds(x, y, x + size, y + size);
                        castingScreenDrawable.draw(canvas);

                        if (parentContainer.progressToFullscreenMode > 0 || scrimProgress > 0) {
                            float alpha = Math.max(parentContainer.progressToFullscreenMode, scrimProgress) * smallProgress;
                            textPaint2.setAlpha((int) (255 * alpha));
                            if (animateToFullscreen || showingInFullscreen) {
                                stopSharingTextView.setAlpha(alpha * (1.0f - scrimProgress));
                            } else {
                                stopSharingTextView.setAlpha(0.0f);
                            }
                            canvas.drawText(sharingScreenString, x - textW3 / 2f + size / 2f, y + size + dp(32), textPaint2);
                        } else {
                            stopSharingTextView.setAlpha(0.0f);
                        }
                        stopSharingTextView.setTranslationY(y + size + dp(72) + swipeToBackDy - currentClipVertical);
                        stopSharingTextView.setTranslationX((getMeasuredWidth() - stopSharingTextView.getMeasuredWidth()) / 2f - currentClipHorizontal);
                        if (parentContainer.progressToFullscreenMode < 1 && scrimProgress < 1) {
                            textPaint.setAlpha((int) (255 * (1.0 - Math.max(parentContainer.progressToFullscreenMode, scrimProgress))));
                            canvas.save();
                            canvas.translate(x - dp(400) / 2f + size / 2f, y + size + dp(10));
                            staticLayout.draw(canvas);
                            canvas.restore();
                        }
                    } else {
                        if (stopSharingTextView.getVisibility() != INVISIBLE) {
                            stopSharingTextView.setVisibility(INVISIBLE);
                        }
                        activity.cellFlickerDrawable.draw(canvas, GroupCallMiniTextureView.this);
                    }
                    invalidate();
                }

                noRtmpStreamTextView.setTranslationY((getMeasuredHeight() - noRtmpStreamTextView.getMeasuredHeight()) / 2f + swipeToBackDy - currentClipVertical);
                noRtmpStreamTextView.setTranslationX((getMeasuredWidth() - noRtmpStreamTextView.getMeasuredWidth()) / 2f - currentClipHorizontal);

                if (blurredFlippingStub != null && blurredFlippingStub.getParent() != null) {
                    blurredFlippingStub.setScaleX(textureView.renderer.getScaleX());
                    blurredFlippingStub.setScaleY(textureView.renderer.getScaleY());
                }
                super.dispatchDraw(canvas);

                float y = getMeasuredHeight() - currentClipVertical - dp(80);

                if (participant != call.videoNotAvailableParticipant) {
                    canvas.save();
                    if ((showingInFullscreen || animateToFullscreen) && !GroupCallActivity.isLandscapeMode && !GroupCallActivity.isTabletMode) {
                        y -= dp(90) * parentContainer.progressToFullscreenMode * (1f - parentContainer.progressToHideUi);
                    }
                    canvas.translate(0, y);
                    canvas.drawPaint(gradientPaint);
                    canvas.restore();
                }


                if (videoIsPaused || videoIsPausedProgress != 0) {
                    if (videoIsPaused && videoIsPausedProgress != 1f) {
                        videoIsPausedProgress += 16 / 250f;
                        if (videoIsPausedProgress > 1f) {
                            videoIsPausedProgress = 1f;
                        } else {
                            invalidate();
                        }
                    } else if (!videoIsPaused && videoIsPausedProgress != 0f) {
                        videoIsPausedProgress -= 16 / 250f;
                        if (videoIsPausedProgress < 0f) {
                            videoIsPausedProgress = 0f;
                        } else {
                            invalidate();
                        }
                    }

                    float a = videoIsPausedProgress;
                    a *= (isInAnimation() ? (overlayIconAlphaFrom * (1f - animationProgress) + overlayIconAlpha * animationProgress) : overlayIconAlpha);

                    if (a > 0) {
                        float iconSize = dp(48);
                        float x = (getMeasuredWidth() - iconSize) / 2f;
                        y = (getMeasuredHeight() - iconSize) / 2f;
                        if (participant == call.videoNotAvailableParticipant) {
                            y -= iconSize / 2.5f;
                        }
                        AndroidUtilities.rectTmp.set((int) x, (int) y, (int) (x + iconSize), (int) (y + iconSize));
                        if (a != 1) {
                            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (255 * a), Canvas.ALL_SAVE_FLAG);
                        } else {
                            canvas.save();
                        }
                        pausedVideoDrawable.setBounds((int) AndroidUtilities.rectTmp.left, (int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, (int) AndroidUtilities.rectTmp.bottom);
                        pausedVideoDrawable.draw(canvas);
                        canvas.restore();

                        a *= parentContainer.progressToFullscreenMode;
                        if (a > 0 && participant != call.videoNotAvailableParticipant) {
                            textPaint.setAlpha((int) (255 * a));
                            canvas.drawText(videoOnPauseString, x - textW / 2f + iconSize / 2f, y + iconSize + dp(16), textPaint);
                        }
                    }
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (inPinchToZoom && child == textureView.renderer) {
                    canvas.save();
                    canvas.scale(pinchScale, pinchScale, pinchCenterX, pinchCenterY);
                    canvas.translate(pinchTranslationX, pinchTranslationY);
                    boolean b = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return b;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public void invalidate() {
                super.invalidate();
                invalidateFromChild = true;
                GroupCallMiniTextureView.this.invalidate();
                invalidateFromChild = false;
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                if (attached && checkScale && renderer.rotatedFrameHeight != 0 && renderer.rotatedFrameWidth != 0) {
                    if (showingAsScrimView) {
                        textureView.scaleType = SCALE_TYPE_FIT;
                    } else if (showingInFullscreen) {
                        textureView.scaleType = SCALE_TYPE_FIT;
                    } else if (parentContainer.inFullscreenMode) {
                        textureView.scaleType = SCALE_TYPE_FILL;
                    } else if (participant.presentation) {
                        textureView.scaleType = SCALE_TYPE_FIT;
                    } else {
                        textureView.scaleType = SCALE_TYPE_ADAPTIVE;
                    }
                    checkScale = false;
                }
                super.onLayout(changed, left, top, right, bottom);

                if (renderer.rotatedFrameHeight != 0 && renderer.rotatedFrameWidth != 0 && participant != null) {
                    participant.setAspectRatio(renderer.rotatedFrameWidth, renderer.rotatedFrameHeight, call);
                }
            }

            @Override
            public void requestLayout() {
                GroupCallMiniTextureView.this.requestLayout();
                super.requestLayout();
            }

            @Override
            protected void onFirstFrameRendered() {
                invalidate();
                if (call != null && call.call.rtmp_stream) {
                    if (postedNoRtmpStreamCallback) {
                        AndroidUtilities.cancelRunOnUIThread(noRtmpStreamCallback);
                        postedNoRtmpStreamCallback = false;

                        noRtmpStreamTextView.animate().cancel();
                        noRtmpStreamTextView.animate().alpha(0f).setDuration(150).start();

                        textureView.animate().cancel();
                        textureView.animate().alpha(1f).setDuration(150).start();
                    }
                }

                if (!videoIsPaused) {
                    if (renderer.getAlpha() != 1f) {
                        renderer.animate().setDuration(300).alpha(1f);
                    }
                }

                if (blurRenderer != null && blurRenderer.getAlpha() != 1f) {
                    blurRenderer.animate().setDuration(300).alpha(1f);
                }

                if (blurredFlippingStub != null && blurredFlippingStub.getParent() != null) {
                    if (blurredFlippingStub.getAlpha() == 1f) {
                        blurredFlippingStub.animate().alpha(0f).setDuration(300).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (blurredFlippingStub.getParent() != null) {
                                    textureView.removeView(blurredFlippingStub);
                                }
                            }
                        }).start();
                    } else {
                        if (blurredFlippingStub.getParent() != null) {
                            textureView.removeView(blurredFlippingStub);
                        }
                    }

                }
                if (renderer.rotatedFrameHeight != 0 && renderer.rotatedFrameWidth != 0 && participant != null) {
                    participant.setAspectRatio(renderer.rotatedFrameWidth, renderer.rotatedFrameHeight, call);
                }
            }
        };
        textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        this.parentContainer = parentContainer;
        this.attachedRenderers = attachedRenderers;
        this.activity = activity;

        textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                for (int i = 0; i < onFirstFrameRunnables.size(); i++) {
                    AndroidUtilities.cancelRunOnUIThread(onFirstFrameRunnables.get(i));
                    onFirstFrameRunnables.get(i).run();
                }
                onFirstFrameRunnables.clear();
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }
        });

        textureView.attachBackgroundRenderer();

        setClipChildren(false);
        textureView.renderer.setAlpha(0f);
        addView(textureView);

        noVideoStubLayout = new NoVideoStubLayout(getContext());
        addView(noVideoStubLayout);

        nameView = new SimpleTextView(parentContainer.getContext());
        nameView.setTextSize(13);
        nameView.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.9f)));
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setFullTextMaxLines(1);
        nameView.setBuildFullLayout(true);
        infoContainer = new FrameLayout(parentContainer.getContext());
        infoContainer.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 32, 0, 8, 0));
        addView(infoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32));
        speakingPaint.setStyle(Paint.Style.STROKE);
        speakingPaint.setStrokeWidth(dp(2));
        speakingPaint.setColor(Theme.getColor(Theme.key_voipgroup_speakingText));
        infoContainer.setClipChildren(false);

        micIconView = new RLottieImageView(parentContainer.getContext());
        addView(micIconView, LayoutHelper.createFrame(24, 24, 0, 4, 6, 4, 0));

        screencastIcon = new ImageView(parentContainer.getContext());
        addView(screencastIcon, LayoutHelper.createFrame(24, 24, 0, 4, 6, 4, 0));
        screencastIcon.setPadding(dp(4), dp(4), dp(4), dp(4));
        screencastIcon.setImageDrawable(ContextCompat.getDrawable(parentContainer.getContext(), R.drawable.voicechat_screencast));
        screencastIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));

        final Drawable rippleDrawable = Theme.createSimpleSelectorRoundRectDrawable(dp(19), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100));
        stopSharingTextView = new TextView(parentContainer.getContext()) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (Math.abs(stopSharingTextView.getAlpha() - 1.0f) > 0.001f) {
                    return false;
                }
                return super.onTouchEvent(event);
            }
        };
        stopSharingTextView.setText(LocaleController.getString(R.string.VoipVideoScreenStopSharing));
        stopSharingTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        stopSharingTextView.setTypeface(AndroidUtilities.bold());
        stopSharingTextView.setPadding(dp(21), 0, dp(21), 0);
        stopSharingTextView.setTextColor(0xffffffff);
        stopSharingTextView.setBackground(rippleDrawable);
        stopSharingTextView.setGravity(Gravity.CENTER);
        stopSharingTextView.setOnClickListener(v -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().stopScreenCapture();
            }
            stopSharingTextView.animate().alpha(0.0f).scaleX(0.0f).scaleY(0.0f).setDuration(180).start();
        });
        addView(stopSharingTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.LEFT | Gravity.TOP));

        noRtmpStreamTextView = new TextView(parentContainer.getContext());
        noRtmpStreamTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        noRtmpStreamTextView.setPadding(dp(21), 0, dp(21), 0);
        noRtmpStreamTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_lastSeenText));
        noRtmpStreamTextView.setBackground(rippleDrawable);
        noRtmpStreamTextView.setGravity(Gravity.CENTER);
        noRtmpStreamTextView.setAlpha(0f);
        if (ChatObject.canManageCalls(chat)) {
            noRtmpStreamTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.NoRtmpStreamFromAppOwner)));
        } else {
            noRtmpStreamTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("NoRtmpStreamFromAppViewer", R.string.NoRtmpStreamFromAppViewer, chat.title)));
        }
        addView(noRtmpStreamTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    }

    private Rect rect = new Rect();

    public boolean isInsideStopScreenButton(float x, float y) {
        stopSharingTextView.getHitRect(rect);
        return rect.contains((int) x, (int) y);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (attached) {
            float y = textureView.getY() + textureView.getMeasuredHeight() - textureView.currentClipVertical - infoContainer.getMeasuredHeight();
            y += swipeToBackDy;
            if (showingAsScrimView || animateToScrimView) {
                infoContainer.setAlpha(1f - parentContainer.progressToScrimView);
                micIconView.setAlpha(1f - parentContainer.progressToScrimView);
            } else if (showingInFullscreen || animateToFullscreen) {
                if (!GroupCallActivity.isLandscapeMode && !GroupCallActivity.isTabletMode) {
                    y -= dp(90) * parentContainer.progressToFullscreenMode * (1f - parentContainer.progressToHideUi);
                }
                infoContainer.setAlpha(1f);
                micIconView.setAlpha(1f);
            } else if (secondaryView != null) {
                infoContainer.setAlpha(1f - parentContainer.progressToFullscreenMode);
                micIconView.setAlpha(1f - parentContainer.progressToFullscreenMode);
            } else {
                infoContainer.setAlpha(1f);
                micIconView.setAlpha(1f);
            }

            if (showingInFullscreen || animateToFullscreen) {
                nameView.setFullAlpha(parentContainer.progressToFullscreenMode);
            } else {
                nameView.setFullAlpha(0f);
            }
            micIconView.setTranslationX(infoContainer.getX());
            micIconView.setTranslationY(y - dp(2));

            if (screencastIcon.getVisibility() == View.VISIBLE) {
                screencastIcon.setTranslationX(textureView.getMeasuredWidth() - 2 * textureView.currentClipHorizontal - dp(32));
                screencastIcon.setTranslationY(y - dp(2));
                screencastIcon.setAlpha(Math.min(1f - parentContainer.progressToFullscreenMode, 1f - parentContainer.progressToScrimView));
            }
            infoContainer.setTranslationY(y);
            infoContainer.setTranslationX(drawFirst ? 0 : dp(6) * parentContainer.progressToFullscreenMode);
        }
        super.dispatchDraw(canvas);

        if (attached) {
            if (statusIcon != null) {
                if (statusIcon.isSpeaking && progressToSpeaking != 1f) {
                    progressToSpeaking += 16f / 300f;
                    if (progressToSpeaking > 1f) {
                        progressToSpeaking = 1f;
                    } else {
                        invalidate();
                    }
                } else if (!statusIcon.isSpeaking && progressToSpeaking != 0) {
                    progressToSpeaking -= 16f / 300f;
                    if (progressToSpeaking < 0) {
                        progressToSpeaking = 0;
                    } else {
                        invalidate();
                    }
                }
            }

            float selectionProgress = progressToSpeaking * (1f - parentContainer.progressToFullscreenMode) * (1f - parentContainer.progressToScrimView);
            if (progressToSpeaking > 0) {
                speakingPaint.setAlpha((int) (255 * selectionProgress));

                float scale = 0.9f + 0.1f * Math.max(0, 1f - Math.abs(swipeToBackDy) / dp(300));
                canvas.save();
                AndroidUtilities.rectTmp.set(textureView.getX() + textureView.currentClipHorizontal, textureView.getY() + textureView.currentClipVertical, textureView.getX() + textureView.getMeasuredWidth() - textureView.currentClipHorizontal, textureView.getY() + textureView.getMeasuredHeight() - textureView.currentClipVertical);
                canvas.scale(scale, scale, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
                canvas.translate(0, swipeToBackDy);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, textureView.roundRadius, textureView.roundRadius, speakingPaint);
                canvas.restore();
            }
        }
    }

    public void getRenderBufferBitmap(GlGenericDrawer.TextureCallback callback) {
        textureView.renderer.getRenderBufferBitmap(callback);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (swipeToBack && (child == textureView || child == noVideoStubLayout)) {
            float scale = 0.9f + 0.1f * Math.max(0, 1f - Math.abs(swipeToBackDy) / dp(300));
            canvas.save();
            canvas.scale(scale, scale, child.getX() + child.getMeasuredWidth() / 2f, child.getY() + child.getMeasuredHeight() / 2f);
            canvas.translate(0, swipeToBackDy);
            boolean b = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return b;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        FrameLayout.LayoutParams layoutParams = (LayoutParams) infoContainer.getLayoutParams();
        int lastLeft = layoutParams.leftMargin;
        float nameScale = 1f;
        if (call.call.rtmp_stream) {
            nameScale = 0f;
        }

        if (lastLandscapeMode != GroupCallActivity.isLandscapeMode) {
            checkScale = true;
            lastLandscapeMode = GroupCallActivity.isLandscapeMode;
        }
        layoutParams.leftMargin = layoutParams.rightMargin = dp(2);

        if (updateNextLayoutAnimated) {
            nameView.animate().scaleX(nameScale).scaleY(nameScale).start();
            micIconView.animate().scaleX(nameScale).scaleY(nameScale).start();

        } else {
            nameView.animate().cancel();
            nameView.setScaleX(nameScale);
            nameView.setScaleY(nameScale);

            micIconView.animate().cancel();
            micIconView.setScaleX(nameScale);
            micIconView.setScaleY(nameScale);
            infoContainer.animate().cancel();
        }

        updateNextLayoutAnimated = false;

        if (showingInFullscreen) {
            updateSize(0);
            overlayIconAlpha = 1f;
            if (GroupCallActivity.isTabletMode) {
                int w = MeasureSpec.getSize(widthMeasureSpec);
                w -= dp(GroupCallActivity.TABLET_LIST_SIZE + 8);
                int h = MeasureSpec.getSize(heightMeasureSpec);
                h -= dp(4);
                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            } else if (!GroupCallActivity.isLandscapeMode) {
                int h = MeasureSpec.getSize(heightMeasureSpec);
                if (!call.call.rtmp_stream) {
                    h -= dp(92);
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            } else {
                int w = MeasureSpec.getSize(widthMeasureSpec);
                if (!call.call.rtmp_stream) {
                    w -= dp(92);
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
            }
        } else if (showingAsScrimView) {
            overlayIconAlpha = 1f;
            int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec)) - dp(14) * 2;
            super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size + getPaddingBottom(), MeasureSpec.EXACTLY));
        } else if (useSpanSize) {
            overlayIconAlpha = 1f;
            int spanCountTotal;
            if (GroupCallActivity.isTabletMode && tabletGridView != null) {
                spanCountTotal = 6;
            } else {
                spanCountTotal = GroupCallActivity.isLandscapeMode ? 6 : 2;
            }
            float listSize;
            if (tabletGridView != null) {
                listSize = MeasureSpec.getSize(widthMeasureSpec) - dp(GroupCallActivity.TABLET_LIST_SIZE + 16 + 8);
            } else if (GroupCallActivity.isTabletMode) {
                listSize = dp(GroupCallActivity.TABLET_LIST_SIZE);
            } else {
                listSize = MeasureSpec.getSize(widthMeasureSpec) - dp(14) * 2 + (GroupCallActivity.isLandscapeMode ? -dp(90) : 0);
            }
            float w = listSize * (spanCount / (float) spanCountTotal);
            float h;
            if (tabletGridView != null) {
                h = tabletGridView.getItemHeight() - dp(4);
                w -= dp(4);
            } else {
                if (GroupCallActivity.isTabletMode) {
                    h = listSize / 2f;
                } else {
                    h = listSize / (float) (GroupCallActivity.isLandscapeMode ? 3 : 2);
                }
                w -= dp(2);
            }
            float layoutContainerW = w;
            layoutParams = (LayoutParams) infoContainer.getLayoutParams();
            if (screencastIcon.getVisibility() == View.VISIBLE) {
                layoutContainerW -= dp(28);
            }
            updateSize((int) layoutContainerW);
            layoutParams.width = (int) (layoutContainerW - layoutParams.leftMargin * 2);

            super.onMeasure(MeasureSpec.makeMeasureSpec((int) w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) h, MeasureSpec.EXACTLY));
        } else {
            overlayIconAlpha = 0f;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        int size = MeasureSpec.getSize(heightMeasureSpec) + (MeasureSpec.getSize(widthMeasureSpec) << 16);
        if (lastSize != size) {
            lastSize = size;
            gradientShader = new LinearGradient(0, 0, 0, dp(120), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 120), Shader.TileMode.CLAMP);
            gradientPaint.setShader(gradientShader);
        }

        nameView.setPivotX(0);
        nameView.setPivotY(nameView.getMeasuredHeight() / 2f);
    }

    public static GroupCallMiniTextureView getOrCreate(ArrayList<GroupCallMiniTextureView> attachedRenderers, GroupCallRenderersContainer renderersContainer, GroupCallGridCell primaryView, GroupCallFullscreenAdapter.GroupCallUserCell secondaryView, GroupCallGridCell tabletGridView, ChatObject.VideoParticipant participant, ChatObject.Call call, GroupCallActivity activity) {
        GroupCallMiniTextureView renderer = null;
        for (int i = 0; i < attachedRenderers.size(); i++) {
            if (participant.equals(attachedRenderers.get(i).participant)) {
                renderer = attachedRenderers.get(i);
                break;
            }
        }
        if (renderer == null) {
            renderer = new GroupCallMiniTextureView(renderersContainer, attachedRenderers, call, activity);
        }
        if (primaryView != null) {
            renderer.setPrimaryView(primaryView);
        }
        if (secondaryView != null) {
            renderer.setSecondaryView(secondaryView);
        }
        if (tabletGridView != null) {
            renderer.setTabletGridView(tabletGridView);
        }
        return renderer;
    }

    public void setTabletGridView(GroupCallGridCell tabletGridView) {
        if (this.tabletGridView != tabletGridView) {
            this.tabletGridView = tabletGridView;
            updateAttachState(true);
        }
    }

    public void setPrimaryView(GroupCallGridCell primaryView) {
        if (this.primaryView != primaryView) {
            this.primaryView = primaryView;
            checkScale = true;
            updateAttachState(true);
        }
    }

    public void setSecondaryView(GroupCallFullscreenAdapter.GroupCallUserCell secondaryView) {
        if (this.secondaryView != secondaryView) {
            this.secondaryView = secondaryView;
            checkScale = true;
            updateAttachState(true);
        }
    }

    public void setShowingAsScrimView(boolean showing, boolean animated) {
        this.showingAsScrimView = showing;
        updateAttachState(animated);
    }

    public void setShowingInFullscreen(boolean showing, boolean animated) {
        if (this.showingInFullscreen != showing) {
            this.showingInFullscreen = showing;
            checkScale = true;
            updateAttachState(animated);
        }
    }

    public void setFullscreenMode(boolean fullscreenMode, boolean animated) {
        if (isFullscreenMode != fullscreenMode) {
            isFullscreenMode = fullscreenMode;
            updateAttachState((primaryView != null || tabletGridView != null) && animated);
        }
    }

    public void updateAttachState(boolean animated) {
        if (forceDetached) {
            return;
        }
        if (call.call.rtmp_stream) {
            int padding = dp(showingInFullscreen ? 36 : 21);
            noRtmpStreamTextView.setPadding(padding, 0, padding, 0);
        }
        if (participant == null && (primaryView != null || secondaryView != null || tabletGridView != null)) {
            if (primaryView != null) {
                participant = primaryView.getParticipant();
            } else if (tabletGridView != null) {
                participant = tabletGridView.getParticipant();
            } else {
                participant = secondaryView.getVideoParticipant();
            }
        }
        boolean forceRequestLayout = false;
        if (attached && !showingInFullscreen) {
            boolean needDetach = VoIPService.getSharedInstance() == null;
            if (GroupCallActivity.paused || participant == null || (secondaryView == null && (!ChatObject.Call.videoIsActive(participant.participant, participant.presentation, call) || !call.canStreamVideo && participant != call.videoNotAvailableParticipant))) {
                needDetach = true;
            }
            if (needDetach || (primaryView == null && secondaryView == null && tabletGridView == null) && !showingAsScrimView && !animateToScrimView) {
                attached = false;

                saveThumb();

                if (textureView.currentAnimation == null && needDetach) {
                    GroupCallMiniTextureView viewToRemove = this;
                    parentContainer.detach(viewToRemove);
                    animate().scaleX(0.5f).scaleY(0.5f).alpha(0).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            viewToRemove.setScaleX(1f);
                            viewToRemove.setScaleY(1f);
                            viewToRemove.setAlpha(1f);
                            parentContainer.removeView(viewToRemove);
                            release();
                        }
                    }).setDuration(150).start();
                } else {
                    if (parentContainer.inLayout) {
                        View viewToRemove = this;
                        AndroidUtilities.runOnUIThread(() -> parentContainer.removeView(viewToRemove));
                    } else {
                        parentContainer.removeView(this);
                    }
                    parentContainer.detach(this);
                    release();
                }

                if (participant.participant.self) {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().setLocalSink(null, participant.presentation);
                    }
                } else {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().removeRemoteSink(participant.participant, participant.presentation);
                    }
                }

                invalidate();

                if (noVideoStubAnimator != null) {
                    noVideoStubAnimator.removeAllListeners();
                    noVideoStubAnimator.cancel();
                }
            }
        } else if (!attached) {
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            if (primaryView != null || secondaryView != null || tabletGridView != null || showingInFullscreen) {
                if (primaryView != null) {
                    participant = primaryView.getParticipant();
                } else if (secondaryView != null) {
                    participant = secondaryView.getVideoParticipant();
                } else if (tabletGridView != null) {
                    participant = tabletGridView.getParticipant();
                }

                boolean videoActive;
                if (participant.participant.self) {
                    videoActive = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getVideoState(participant.presentation) == Instance.VIDEO_STATE_ACTIVE;
                } else {
                    videoActive = (call.canStreamVideo || participant == call.videoNotAvailableParticipant) && ChatObject.Call.videoIsActive(participant.participant, participant.presentation, call);
                }
                if (showingInFullscreen || (!VoIPService.getSharedInstance().isFullscreen(participant.participant, participant.presentation) && !VoIPService.getSharedInstance().isFullscreen(participant.participant, participant.presentation) && videoActive)) {
                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
                        for (int i = 0; i < attachedRenderers.size(); i++) {
                            if (attachedRenderers.get(i).participant.equals(participant)) {
                                throw new RuntimeException("try add two same renderers");
                            }
                        }
                    }
                    forceRequestLayout = true;
                    attached = true;

                    if (activity.statusIconPool.size() > 0) {
                        statusIcon = activity.statusIconPool.remove(activity.statusIconPool.size() - 1);
                    } else {
                        statusIcon = new GroupCallStatusIcon();
                    }
                    statusIcon.setCallback(this);
                    statusIcon.setImageView(micIconView);
                    updateIconColor(false);

                    if (getParent() == null) {
                        parentContainer.addView(this, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.TOP));
                        parentContainer.attach(this);
                    }

                    checkScale = true;
                    animateEnter = false;
                    animate().setListener(null).cancel();
                    if (textureView.currentAnimation == null && ((secondaryView != null && primaryView == null)) && !hasImage()) {
                        setScaleX(0.5f);
                        setScaleY(0.5f);
                        setAlpha(0);
                        animateEnter = true;
                        invalidate();
                        animate().scaleX(1f).scaleY(1f).alpha(1f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                animateEnter = false;
                                invalidate();
                            }
                        }).setDuration(100).start();
                        invalidate();
                    } else {
                        setScaleY(1f);
                        setScaleX(1f);
                        setAlpha(1f);
                    }
                    animated = false;

                    loadThumb();
                    screencastIcon.setVisibility(participant.presentation && !call.call.rtmp_stream ? VISIBLE : GONE);
                }
            }
        }
        if (participant == call.videoNotAvailableParticipant) {
            if (nameView.getVisibility() != INVISIBLE) {
                nameView.setVisibility(INVISIBLE);
                micIconView.setVisibility(INVISIBLE);
            }
        } else {
            if (nameView.getVisibility() != VISIBLE) {
                nameView.setVisibility(VISIBLE);
                micIconView.setVisibility(VISIBLE);
            }
        }

        if (attached) {
            int size;
            float spanCount = 1f;
            boolean useSpanSize = false;
            int gridItemsCount = 0;

            boolean useTablet = GroupCallActivity.isTabletMode && (!parentContainer.inFullscreenMode || (secondaryView == null && primaryView == null));
            if (showingInFullscreen) {
                size = LayoutHelper.MATCH_PARENT;
            } else if (secondaryView != null && primaryView == null && !parentContainer.inFullscreenMode) {
                size = 0;
            } else if (showingAsScrimView) {
                size = LayoutHelper.MATCH_PARENT;
            } else if (secondaryView != null && primaryView == null) {
                size = dp(80);
            } else if (tabletGridView != null && useTablet) {
                if (tabletGridView != null) {
                    useSpanSize = true;
                    size = LayoutHelper.MATCH_PARENT;
                    spanCount = tabletGridView.spanCount;
                    gridItemsCount = tabletGridView.gridAdapter.getItemCount();
                } else {
                    size = dp(46);
                }
            } else if (primaryView != null && secondaryView == null || !isFullscreenMode) {
                if (primaryView != null) {
                    useSpanSize = true;
                    size = LayoutHelper.MATCH_PARENT;
                    spanCount = primaryView.spanCount;
                } else {
                    size = dp(46);
                }
            } else if (primaryView != null) {
                size = dp(80);
            } else {
                size = 0;
            }
            MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();
            if (size != 0 && (layoutParams.height != size || forceRequestLayout || this.useSpanSize != useSpanSize || (useSpanSize && this.spanCount != spanCount || this.gridItemsCount != gridItemsCount))) {
                layoutParams.height = size;
                layoutParams.width = useSpanSize ? LayoutHelper.MATCH_PARENT : size;
                this.useSpanSize = useSpanSize;
                this.spanCount = spanCount;
                checkScale = true;
                if (animated) {
                    textureView.animateToLayout();
                    updateNextLayoutAnimated = true;
                } else {
                    textureView.requestLayout();
                }
                AndroidUtilities.runOnUIThread(this::requestLayout);
                parentContainer.requestLayout();
                invalidate();
            }

            if (participant.participant.self && !participant.presentation && VoIPService.getSharedInstance() != null) {
                textureView.renderer.setMirror(VoIPService.getSharedInstance().isFrontFaceCamera());
                textureView.renderer.setRotateTextureWithScreen(true);
                textureView.renderer.setUseCameraRotation(true);
            } else {
                textureView.renderer.setMirror(false);
                textureView.renderer.setRotateTextureWithScreen(true);
                textureView.renderer.setUseCameraRotation(false);
            }
            textureView.updateRotation();

            if (participant.participant.self) {
                textureView.renderer.setMaxTextureSize(720);
            } else {
                textureView.renderer.setMaxTextureSize(0);
            }

            boolean hasVideoLocal = true;

            if (!ChatObject.Call.videoIsActive(participant.participant, participant.presentation, call) || !call.canStreamVideo && participant != call.videoNotAvailableParticipant) {
                noVideoStubLayout.avatarImageReceiver.setCurrentAccount(currentAccount);
                long peerId = MessageObject.getPeerId(participant.participant.peer);
                ImageLocation imageLocation;
                ImageLocation thumbLocation;
                Object parentObject;
                if (DialogObject.isUserDialog(peerId)) {
                    TLRPC.User currentUser = AccountInstance.getInstance(currentAccount).getMessagesController().getUser(peerId);
                    noVideoStubLayout.avatarDrawable.setInfo(currentAccount, currentUser);
                    imageLocation = ImageLocation.getForUser(currentUser, ImageLocation.TYPE_BIG);
                    thumbLocation = ImageLocation.getForUser(currentUser, ImageLocation.TYPE_SMALL);
                    parentObject = currentUser;
                } else {
                    TLRPC.Chat currentChat = AccountInstance.getInstance(UserConfig.selectedAccount).getMessagesController().getChat(-peerId);
                    noVideoStubLayout.avatarDrawable.setInfo(currentAccount, currentChat);
                    imageLocation = ImageLocation.getForChat(currentChat, ImageLocation.TYPE_BIG);
                    thumbLocation = ImageLocation.getForChat(currentChat, ImageLocation.TYPE_SMALL);
                    parentObject = currentChat;
                }

                Drawable thumb = noVideoStubLayout.avatarDrawable;
                if (thumbLocation != null) {
                    BitmapDrawable drawable = ImageLoader.getInstance().getImageFromMemory(thumbLocation.location, null, "50_50");
                    if (drawable != null) {
                        thumb = drawable;
                    }
                }
                noVideoStubLayout.avatarImageReceiver.setImage(imageLocation, null, thumb, null, parentObject, 0);
                noVideoStubLayout.backgroundImageReceiver.setImage(imageLocation, "50_50_b", new ColorDrawable(Theme.getColor(Theme.key_voipgroup_listViewBackground)), null, parentObject, 0);
                hasVideoLocal = false;
            }

            boolean skipNoStubTransition = animated && secondaryView != null && !showingInFullscreen && !hasVideoLocal;

            if (hasVideoLocal != hasVideo && !skipNoStubTransition) {
                hasVideo = hasVideoLocal;

                if (noVideoStubAnimator != null) {
                    noVideoStubAnimator.removeAllListeners();
                    noVideoStubAnimator.cancel();
                }
                if (animated) {
                    if (!hasVideo && noVideoStubLayout.getVisibility() != View.VISIBLE) {
                        noVideoStubLayout.setVisibility(View.VISIBLE);
                        noVideoStubLayout.setAlpha(0);
                    }
                    noVideoStubAnimator = ValueAnimator.ofFloat(progressToNoVideoStub, hasVideo ? 0 : 1f);
                    noVideoStubAnimator.addUpdateListener(valueAnimator1 -> {
                        progressToNoVideoStub = (float) valueAnimator1.getAnimatedValue();
                        noVideoStubLayout.setAlpha(progressToNoVideoStub);
                        textureView.invalidate();
                    });
                    noVideoStubAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            progressToNoVideoStub = hasVideo ? 0f : 1f;
                            noVideoStubLayout.setAlpha(progressToNoVideoStub);
                            noVideoStubLayout.setVisibility(hasVideo ? View.GONE : View.VISIBLE);
                            textureView.invalidate();
                        }
                    });
                    noVideoStubAnimator.start();
                } else {
                    progressToNoVideoStub = hasVideo ? 0f : 1f;
                    noVideoStubLayout.setVisibility(hasVideo ? View.GONE : View.VISIBLE);
                    noVideoStubLayout.setAlpha(progressToNoVideoStub);
                    textureView.invalidate();
                }

                if (hasVideo) {
                    noVideoStubLayout.updateMuteButtonState(false);
                }
            }

            if (participant.participant.self && VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().setLocalSink(textureView.renderer, participant.presentation);
            }

            statusIcon.setParticipant(participant.participant, animated);
            if (noVideoStubLayout.getVisibility() == View.VISIBLE) {
                noVideoStubLayout.updateMuteButtonState(true);
            }

            boolean pausedInternal = false;
            if (participant.presentation) {
                if (participant.participant.presentation != null && participant.participant.presentation.paused) {
                    pausedInternal = true;
                }
            } else {
                if (participant.participant.video != null && participant.participant.video.paused) {
                    pausedInternal = true;
                }
            }
            if (videoIsPaused != pausedInternal) {
                videoIsPaused = pausedInternal;
                textureView.renderer.animate().alpha(videoIsPaused ? 0 : 1f).setDuration(250).start();
                textureView.invalidate();
            }

            if (GroupCallActivity.paused || !hasVideo) {
                if (participant.participant.self) {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().setLocalSink(null, participant.presentation);
                    }
                } else if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().removeRemoteSink(participant.participant, participant.presentation);
                    VoIPService.getSharedInstance().removeRemoteSink(participant.participant, participant.presentation);
                }
                if (GroupCallActivity.paused && textureView.renderer.isFirstFrameRendered()) {
                    saveThumb();
                    textureView.renderer.clearFirstFrame();
                    textureView.renderer.setAlpha(0f);
                    textureView.blurRenderer.setAlpha(0f);
                }
            } else {
                if (!textureView.renderer.isFirstFrameRendered()) {
                    loadThumb();
                }
                if (participant.participant.self) {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().setLocalSink(textureView.renderer, participant.presentation);
                    }
                } else if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().addRemoteSink(participant.participant, participant.presentation, textureView.renderer, null);
                    VoIPService.getSharedInstance().addRemoteSink(participant.participant, participant.presentation, textureView.renderer, null);

                    if (call != null && call.call.rtmp_stream && !textureView.renderer.isFirstFrameRendered()) {
                        if (!postedNoRtmpStreamCallback) {
                            AndroidUtilities.runOnUIThread(noRtmpStreamCallback, 15000);
                            postedNoRtmpStreamCallback = true;
                        }
                    }
                }
            }

            updateIconColor(true);
        }

        updateInfo();
    }

    private void loadThumb() {
        if (thumb != null) {
            return;
        }
        thumb = call.thumbs.get(participant.presentation ? participant.participant.presentationEndpoint : participant.participant.videoEndpoint);
        textureView.setThumb(thumb);

        if (thumb == null) {
            long peerId = MessageObject.getPeerId(participant.participant.peer);

            if (participant.participant.self && participant.presentation) {
                imageReceiver.setImageBitmap(new MotionBackgroundDrawable(0xff212E3A, 0xff2B5B4D, 0xff245863, 0xff274558, true));
            } else {
                if (peerId > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                    ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                    int color = user != null ? AvatarDrawable.getColorForId(user.id) : ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f);
                    GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{ColorUtils.blendARGB(color, Color.BLACK, 0.2f), ColorUtils.blendARGB(color, Color.BLACK, 0.4f)});
                    imageReceiver.setImage(imageLocation, "50_50_b", gradientDrawable, null, user, 0);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                    ImageLocation imageLocation = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL);
                    int color = chat != null ? AvatarDrawable.getColorForId(chat.id) : ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f);
                    GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{ColorUtils.blendARGB(color, Color.BLACK, 0.2f), ColorUtils.blendARGB(color, Color.BLACK, 0.4f)});
                    imageReceiver.setImage(imageLocation, "50_50_b", gradientDrawable, null, chat, 0);
                }
            }
        }
    }


    public void updateInfo() {
        if (!attached) {
            return;
        }

        String name = null;

        long peerId = MessageObject.getPeerId(participant.participant.peer);
        if (DialogObject.isUserDialog(peerId)) {
            TLRPC.User currentUser = AccountInstance.getInstance(currentAccount).getMessagesController().getUser(peerId);
            name = UserObject.getUserName(currentUser);
        } else {
            TLRPC.Chat currentChat = AccountInstance.getInstance(currentAccount).getMessagesController().getChat(-peerId);
            if (currentChat != null) {
                name = currentChat.title;
            }
        }

        nameView.setText(name);
    }

    public boolean hasImage() {
        return textureView.stubVisibleProgress == 1f;
    }

    public void updatePosition(ViewGroup listView, ViewGroup tabletGridListView, RecyclerListView fullscreenListView, GroupCallRenderersContainer renderersContainer) {
        if (showingAsScrimView || animateToScrimView || forceDetached) {
            return;
        }
        drawFirst = false;
        float progressToFullscreen = renderersContainer.progressToFullscreenMode;
        if (animateToFullscreen || showingInFullscreen) {
            if (primaryView != null || tabletGridView != null) {
                GroupCallGridCell callUserCell = tabletGridView != null ? tabletGridView : primaryView;
                ViewGroup fromListView = tabletGridView != null ? tabletGridListView : listView;
                float fromX = callUserCell.getX() + fromListView.getX() - getLeft() - renderersContainer.getLeft();
                float fromY = callUserCell.getY() + dp(2) + fromListView.getY() - getTop() - renderersContainer.getTop();

                float toX = 0;
                float toY = 0;

                setTranslationX(fromX * (1f - progressToFullscreen) + toX * progressToFullscreen);
                setTranslationY(fromY * (1f - progressToFullscreen) + toY * progressToFullscreen);
            } else {
                setTranslationX(0);
                setTranslationY(0);
            }

            textureView.setRoundCorners(dp(8));

            if (secondaryView != null) {
                secondaryView.setAlpha(progressToFullscreen);
            }
            if (!showingInFullscreen && primaryView == null && tabletGridView == null) {
                setAlpha(progressToFullscreen);
            } else if (!animateEnter) {
                setAlpha(1f);
            }
        } else if (secondaryView != null) {
            if (secondaryView.isRemoving(fullscreenListView)) {
                setAlpha(secondaryView.getAlpha());
            } else if (primaryView == null) {
                if (attached && !animateEnter) {
                    setAlpha(progressToFullscreen);
                }
                secondaryView.setAlpha(progressToFullscreen);
                progressToFullscreen = 1f;
            } else {
                secondaryView.setAlpha(1f);
                if (attached && !animateEnter) {
                    setAlpha(1f);
                }
            }

            setTranslationX(secondaryView.getX() + fullscreenListView.getX() - getLeft());
            setTranslationY(dp(2) * (1f - progressToFullscreen) + secondaryView.getY() + fullscreenListView.getY() - getTop());
            textureView.setRoundCorners(dp(13) * progressToFullscreen + dp(8) * (1f - progressToFullscreen));
        } else if (primaryView != null || tabletGridView != null) {
            GroupCallGridCell callUserCell;
            ViewGroup fromListView;
            if (tabletGridView != null && primaryView != null) {
                boolean useTablet = GroupCallActivity.isTabletMode && !parentContainer.inFullscreenMode;
                callUserCell = useTablet ? tabletGridView : primaryView;
                fromListView = useTablet ? tabletGridListView : listView;
            } else {
                callUserCell = tabletGridView != null ? tabletGridView : primaryView;
                fromListView = tabletGridView != null ? tabletGridListView : listView;
            }
            setTranslationX(callUserCell.getX() + fromListView.getX() - getLeft() - renderersContainer.getLeft());
            setTranslationY(callUserCell.getY() + dp(2) + fromListView.getY() - getTop() - renderersContainer.getTop());
            textureView.setRoundCorners(dp(8));

            if (attached && !animateEnter) {
                if (!GroupCallActivity.isTabletMode) {
                    drawFirst = true;
                    setAlpha((1f - progressToFullscreen) * callUserCell.getAlpha());
                } else if (primaryView != null && tabletGridView == null) {
                    setAlpha(progressToFullscreen * callUserCell.getAlpha());
                }
            }
        }
    }

    public boolean isAttached() {
        return attached;
    }

    public void release() {
        textureView.renderer.release();
        if (statusIcon != null) {
            activity.statusIconPool.add(statusIcon);
            statusIcon.setCallback(null);
            statusIcon.setImageView(null);
        }
        statusIcon = null;
    }

    public boolean isFullyVisible() {
        if (showingInFullscreen || animateToFullscreen) {
            return false;
        }
        return attached && textureView.renderer.isFirstFrameRendered() && getAlpha() == 1;
    }

    public boolean isVisible() {
        if (showingInFullscreen || animateToFullscreen) {
            return false;
        }
        return attached && textureView.renderer.isFirstFrameRendered();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!invalidateFromChild) {
            textureView.invalidate();
        }
        if (primaryView != null) {
            primaryView.invalidate();
            if (activity.getScrimView() == primaryView) {
                activity.getContainerView().invalidate();
            }
        }
        if (secondaryView != null) {
            secondaryView.invalidate();
            if (secondaryView.getParent() != null) {
                ((View) secondaryView.getParent()).invalidate();
            }
        }
        if (getParent() != null) {
            ((View) getParent()).invalidate();
        }
    }


    public void forceDetach(boolean removeSink) {
        GroupCallMiniTextureView viewToRemove = this;
        forceDetached = true;
        attached = false;
        parentContainer.detach(viewToRemove);

        if (removeSink) {
            if (participant.participant.self) {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().setLocalSink(null, participant.presentation);
                }
            } else {
                if (VoIPService.getSharedInstance() != null && !RTMPStreamPipOverlay.isVisible()) {
                    VoIPService.getSharedInstance().removeRemoteSink(participant.participant, participant.presentation);
                }
            }
        }

        saveThumb();

        if (noVideoStubAnimator != null) {
            noVideoStubAnimator.removeAllListeners();
            noVideoStubAnimator.cancel();
        }

        textureView.renderer.release();
    }

    public void saveThumb() {
        if (participant != null && textureView.renderer.getMeasuredHeight() != 0 && textureView.renderer.getMeasuredWidth() != 0) {
            getRenderBufferBitmap((bitmap, rotation1) -> {
                if (bitmap != null && bitmap.getPixel(0, 0) != Color.TRANSPARENT) {
                    Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(bitmap.getWidth(), bitmap.getHeight()) / 180));
                    AndroidUtilities.runOnUIThread(() -> call.thumbs.put(participant.presentation ? participant.participant.presentationEndpoint : participant.participant.videoEndpoint, bitmap));
                }
            });
        }
    }

    public void setViews(GroupCallGridCell primaryView, GroupCallFullscreenAdapter.GroupCallUserCell secondaryView, GroupCallGridCell tabletGrid) {
        this.primaryView = primaryView;
        this.secondaryView = secondaryView;
        this.tabletGridView = tabletGrid;
    }

    public void setAmplitude(double value) {
        statusIcon.setAmplitude(value);
        noVideoStubLayout.setAmplitude(value);
    }

    public void setZoom(boolean inPinchToZoom, float pinchScale, float pinchCenterX, float pinchCenterY, float pinchTranslationX, float pinchTranslationY) {
        if (this.pinchScale != pinchScale || this.pinchCenterX != pinchCenterX || this.pinchCenterY != pinchCenterY || this.pinchTranslationX != pinchTranslationX || this.pinchTranslationY != pinchTranslationY) {
            this.inPinchToZoom = inPinchToZoom;
            this.pinchScale = pinchScale;
            this.pinchCenterX = pinchCenterX;
            this.pinchCenterY = pinchCenterY;
            this.pinchTranslationX = pinchTranslationX;
            this.pinchTranslationY = pinchTranslationY;
            textureView.invalidate();
        }
    }

    public void setSwipeToBack(boolean swipeToBack, float swipeToBackDy) {
        if (this.swipeToBack != swipeToBack || this.swipeToBackDy != swipeToBackDy) {
            this.swipeToBack = swipeToBack;
            this.swipeToBackDy = swipeToBackDy;
            textureView.invalidate();
            invalidate();
        }
    }

    public void runOnFrameRendered(Runnable runnable) {
        if (textureView.renderer.isFirstFrameRendered()) {
            runnable.run();
        } else {
            AndroidUtilities.runOnUIThread(runnable, 250);
            onFirstFrameRunnables.add(runnable);
        }
    }

    int lastIconColor;
    int animateToColor;
    int lastSpeakingFrameColor;
    ValueAnimator colorAnimator;

    @Override
    public void onStatusChanged() {
        invalidate();
        updateIconColor(true);
        if (noVideoStubLayout.getVisibility() == View.VISIBLE) {
            noVideoStubLayout.updateMuteButtonState(true);
        }
    }

    private void updateIconColor(boolean animated) {
        if (statusIcon == null) {
            return;
        }
        int newColor;
        int newSpeakingFrameColor;
        if (statusIcon.isMutedByMe()) {
            newSpeakingFrameColor = newColor = Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon);
        } else if (statusIcon.isSpeaking()) {
            newSpeakingFrameColor = newColor = Theme.getColor(Theme.key_voipgroup_speakingText);
        } else {
            newSpeakingFrameColor = Theme.getColor(Theme.key_voipgroup_speakingText);
            newColor = Color.WHITE;
        }

        if (animateToColor == newColor) {
            return;
        }
        if (colorAnimator != null) {
            colorAnimator.removeAllListeners();
            colorAnimator.cancel();
        }

        if (!animated) {
            // micIconView.setColorFilter(new PorterDuffColorFilter(animateToColor = lastIconColor = newColor, PorterDuff.Mode.MULTIPLY));
            speakingPaint.setColor(lastSpeakingFrameColor = newSpeakingFrameColor);
        } else {
            int colorFrom = lastIconColor;
            int colorFromSpeaking = lastSpeakingFrameColor;
            animateToColor = newColor;
            colorAnimator = ValueAnimator.ofFloat(0, 1f);
            colorAnimator.addUpdateListener(valueAnimator -> {
                float v = (float) valueAnimator.getAnimatedValue();
                lastIconColor = ColorUtils.blendARGB(colorFrom, newColor, v);
                lastSpeakingFrameColor = ColorUtils.blendARGB(colorFromSpeaking, newSpeakingFrameColor, v);
                speakingPaint.setColor(lastSpeakingFrameColor);
                if (progressToSpeaking > 0) {
                    invalidate();
                }
            });
            colorAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animateToColor = lastIconColor = newColor;
                    lastSpeakingFrameColor = newSpeakingFrameColor;
                    speakingPaint.setColor(lastSpeakingFrameColor);
                    if (progressToSpeaking > 0) {
                        invalidate();
                    }
                }
            });
            colorAnimator.start();
        }
    }

    public void runDelayedAnimations() {
        for (int i = 0; i < onFirstFrameRunnables.size(); i++) {
            onFirstFrameRunnables.get(i).run();
        }
        onFirstFrameRunnables.clear();
    }

    int collapseSize;
    int fullSize;

    public void updateSize(int collapseSize) {
        int fullSize = parentContainer.getMeasuredWidth() - dp(6);
        if ((this.collapseSize != collapseSize && collapseSize > 0) || (this.fullSize != fullSize && fullSize > 0)) {
            if (collapseSize != 0) {
                this.collapseSize = collapseSize;
            }
            if (fullSize != 0) {
                this.fullSize = fullSize;
            }
            nameView.setFullLayoutAdditionalWidth(fullSize - collapseSize, 0);
        }
    }

    private class NoVideoStubLayout extends View {

        public ImageReceiver avatarImageReceiver = new ImageReceiver();
        public ImageReceiver backgroundImageReceiver = new ImageReceiver();
        AvatarDrawable avatarDrawable = new AvatarDrawable();

        BlobDrawable tinyWaveDrawable;
        BlobDrawable bigWaveDrawable;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float amplitude;
        float animateToAmplitude;
        float animateAmplitudeDiff;
        float wavesEnter = 0f;
        float cx, cy;
        float speakingProgress;

        public NoVideoStubLayout(@NonNull Context context) {
            super(context);

            tinyWaveDrawable = new BlobDrawable(9);
            bigWaveDrawable = new BlobDrawable(12);

            tinyWaveDrawable.minRadius = dp(76);
            tinyWaveDrawable.maxRadius = dp(92);
            tinyWaveDrawable.generateBlob();

            bigWaveDrawable.minRadius = dp(80);
            bigWaveDrawable.maxRadius = dp(95);
            bigWaveDrawable.generateBlob();

            paint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_listeningText), Theme.getColor(Theme.key_voipgroup_speakingText), speakingProgress));
            paint.setAlpha((int) (255 * 0.4f));

            backgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.5f)));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            float size = dp(157);
            cx = getMeasuredWidth() >> 1;
            cy = (getMeasuredHeight() >> 1) + (GroupCallActivity.isLandscapeMode ? 0 : -getMeasuredHeight() * 0.12f);
            avatarImageReceiver.setRoundRadius((int) (size / 2f));
            avatarImageReceiver.setImageCoords(cx - size / 2, cy - size / 2, size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            AndroidUtilities.rectTmp.set(textureView.getX() + textureView.currentClipHorizontal, textureView.getY() + textureView.currentClipVertical, textureView.getX() + textureView.getMeasuredWidth() - textureView.currentClipHorizontal, textureView.getY() + textureView.getMeasuredHeight() + textureView.currentClipVertical);
            backgroundImageReceiver.setImageCoords(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top, AndroidUtilities.rectTmp.width(), AndroidUtilities.rectTmp.height());
            backgroundImageReceiver.setRoundRadius((int) textureView.roundRadius);
            backgroundImageReceiver.draw(canvas);

            canvas.drawRoundRect(AndroidUtilities.rectTmp, textureView.roundRadius, textureView.roundRadius, backgroundPaint);

            if (animateToAmplitude != amplitude) {
                amplitude += animateAmplitudeDiff * 16;
                if (animateAmplitudeDiff > 0) {
                    if (amplitude > animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                } else {
                    if (amplitude < animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                }
            }

            if (switchProgress != 1f) {
                if (prevState != null) {
                    switchProgress += 16 / 220f;
                }
                if (switchProgress >= 1.0f) {
                    switchProgress = 1f;
                    prevState = null;
                }
            }

            float scale = 1f + 0.8f * amplitude;
            canvas.save();
            canvas.scale(scale, scale, cx, cy);

            if (currentState != null) {
                currentState.update((int) (cy - dp(100)), (int) (cx - dp(100)), dp(200), 16, amplitude);
            }
            bigWaveDrawable.update(amplitude, 1f);
            tinyWaveDrawable.update(amplitude, 1f);

            for (int i = 0; i < 2; i++) {
                float alpha;
                if (i == 0 && prevState != null) {
                    paint.setShader(prevState.shader);
                    alpha = 1f - switchProgress;
                } else if (i == 1 && currentState != null) {
                    paint.setShader(currentState.shader);
                    alpha = switchProgress;
                } else {
                    continue;
                }

                paint.setAlpha((int) (76 * alpha));
                bigWaveDrawable.draw(cx, cy, canvas, paint);
                tinyWaveDrawable.draw(cx, cy, canvas, paint);
            }
            canvas.restore();

            scale = 1f + 0.2f * amplitude;
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
            avatarImageReceiver.draw(canvas);
            canvas.restore();

            invalidate();
        }

        private GroupCallActivity.WeavingState[] states = new GroupCallActivity.WeavingState[3];
        private GroupCallActivity.WeavingState currentState;
        private GroupCallActivity.WeavingState prevState;

        int muteButtonState = -1;
        private final static int MUTE_BUTTON_STATE_MUTE = 1;
        private final static int MUTE_BUTTON_STATE_UNMUTE = 0;
        private final static int MUTED_BY_ADMIN = 2;
        float switchProgress = 1f;

        private void updateMuteButtonState(boolean animated) {
            int newButtonState;
            if (statusIcon.isMutedByMe() || statusIcon.isMutedByAdmin()) {
                newButtonState = MUTED_BY_ADMIN;
            } else if (statusIcon.isSpeaking()) {
                newButtonState = MUTE_BUTTON_STATE_MUTE;
            } else {
                newButtonState = MUTE_BUTTON_STATE_UNMUTE;
            }
            if (newButtonState == muteButtonState) {
                return;
            }
            muteButtonState = newButtonState;

            if (states[muteButtonState] == null) {
                states[muteButtonState] = new GroupCallActivity.WeavingState(muteButtonState);
                if (muteButtonState == MUTED_BY_ADMIN) {
                    states[muteButtonState].shader = new LinearGradient(0, 400, 400, 0, new int[]{Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2)}, null, Shader.TileMode.CLAMP);
                } else if (muteButtonState == MUTE_BUTTON_STATE_MUTE) {
                    states[muteButtonState].shader = new RadialGradient(200, 200, 200, new int[]{Theme.getColor(Theme.key_voipgroup_muteButton), Theme.getColor(Theme.key_voipgroup_muteButton3)}, null, Shader.TileMode.CLAMP);
                } else {
                    states[muteButtonState].shader = new RadialGradient(200, 200, 200, new int[]{Theme.getColor(Theme.key_voipgroup_unmuteButton2), Theme.getColor(Theme.key_voipgroup_unmuteButton)}, null, Shader.TileMode.CLAMP);
                }
            }
            if (states[muteButtonState] != currentState) {
                prevState = currentState;
                currentState = states[muteButtonState];
                if (prevState == null || !animated) {
                    switchProgress = 1;
                    prevState = null;
                } else {
                    switchProgress = 0;
                }
            }
            invalidate();
        }

        public void setAmplitude(double value) {
            float amplitude = (float) value / 80f;
            if (amplitude > 1f) {
                amplitude = 1f;
            } else if (amplitude < 0) {
                amplitude = 0;
            }
            animateToAmplitude = amplitude;
            animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            avatarImageReceiver.onAttachedToWindow();
            backgroundImageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            avatarImageReceiver.onDetachedFromWindow();
            backgroundImageReceiver.onDetachedFromWindow();
        }
    }

    public String getName() {
        long peerId = MessageObject.getPeerId(participant.participant.peer);
        if (DialogObject.isUserDialog(peerId)) {
            TLRPC.User currentUser = AccountInstance.getInstance(UserConfig.selectedAccount).getMessagesController().getUser(peerId);
            return UserObject.getUserName(currentUser);
        } else {
            TLRPC.Chat currentChat = AccountInstance.getInstance(UserConfig.selectedAccount).getMessagesController().getChat(-peerId);
            return currentChat.title;
        }
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    ValueAnimator flipAnimator;
    boolean flipHalfReached;

    public void startFlipAnimation() {
        if (flipAnimator != null) {
            return;
        }
        flipHalfReached = false;

        if (blurredFlippingStub == null) {
            blurredFlippingStub = new ImageView(getContext());
        } else {
            blurredFlippingStub.animate().cancel();
        }
        if (textureView.renderer.isFirstFrameRendered()) {
            Bitmap bitmap = textureView.blurRenderer.getBitmap(100, 100);
            if (bitmap != null) {
                Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
                Drawable drawable = new BitmapDrawable(bitmap);
                blurredFlippingStub.setBackground(drawable);
            }
            blurredFlippingStub.setAlpha(0f);
        } else {
            blurredFlippingStub.setAlpha(1f);
        }

        if (blurredFlippingStub.getParent() == null) {
            textureView.addView(blurredFlippingStub);
        }
        ((LayoutParams) blurredFlippingStub.getLayoutParams()).gravity = Gravity.CENTER;

        flipAnimator = ValueAnimator.ofFloat(0, 1f);
        flipAnimator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            float rotation;
            boolean halfReached = false;
            if (v < 0.5f) {
                rotation = v;
            } else {
                halfReached = true;
                rotation = v - 1f;
            }

            if (halfReached && !flipHalfReached) {
                blurredFlippingStub.setAlpha(1f);
                flipHalfReached = true;
                textureView.renderer.clearImage();
            }

            rotation *= 180;
            blurredFlippingStub.setRotationY(rotation);
            textureView.renderer.setRotationY(rotation);
        });

        flipAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                flipAnimator = null;
                textureView.setRotationY(0);

                if (!flipHalfReached) {
                    textureView.renderer.clearImage();
                    //
                }
            }
        });
        flipAnimator.setDuration(400);
        flipAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        flipAnimator.start();
    }
}
