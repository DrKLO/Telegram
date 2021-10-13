package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.CrossOutDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GroupCallFullscreenAdapter;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.GroupCallActivity;

import java.util.ArrayList;

import static org.telegram.ui.GroupCallActivity.TRANSITION_DURATION;
import static org.telegram.ui.GroupCallActivity.isLandscapeMode;

@SuppressLint("ViewConstructor")
public class GroupCallRenderersContainer extends FrameLayout {

    private final int touchSlop;
    public boolean inFullscreenMode;
    public float progressToFullscreenMode;
    public long fullscreenPeerId;
    public ChatObject.VideoParticipant fullscreenParticipant;
    public boolean hasPinnedVideo;
    public long lastUpdateTime;
    public float progressToScrimView;
    public int listWidth;
    ValueAnimator fullscreenAnimator;
    public boolean inLayout;

    private LongSparseIntArray attachedPeerIds = new LongSparseIntArray();

    int animationIndex;

    public GroupCallMiniTextureView fullscreenTextureView;
    private GroupCallMiniTextureView outFullscreenTextureView;
    private final RecyclerView listView;
    private final RecyclerView fullscreenListView;
    private final ArrayList<GroupCallMiniTextureView> attachedRenderers;

    private final FrameLayout speakingMembersToast;
    private final AvatarsImageView speakingMembersAvatars;
    private final TextView speakingMembersText;
    private boolean showSpeakingMembersToast;
    private long speakingToastPeerId;
    private float showSpeakingMembersToastProgress;

    private float speakingMembersToastChangeProgress = 1f;
    private float speakingMembersToastFromLeft;
    private float speakingMembersToastFromTextLeft;
    private float speakingMembersToastFromRight;
    private boolean animateSpeakingOnNextDraw = true;

    private boolean drawRenderesOnly;
    private boolean drawFirst;
    private boolean notDrawRenderes;


    boolean uiVisible = true;

    float progressToHideUi;

    Drawable topShadowDrawable;
    CrossOutDrawable pinDrawable;
    TextView pinTextView;
    TextView unpinTextView;
    View pinContainer;

    boolean hideUiRunnableIsScheduled;
    Runnable hideUiRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canHideUI()) {
                AndroidUtilities.runOnUIThread(hideUiRunnable, 3000);
                return;
            }
            hideUiRunnableIsScheduled = false;
            setUiVisible(false);
        }
    };

    ChatObject.Call call;
    GroupCallActivity groupCallActivity;

    private final ImageView backButton;
    private final ImageView pinButton;
    private final View topShadowView;

    private float pinchStartCenterX;
    private float pinchStartCenterY;
    private float pinchStartDistance;
    private float pinchTranslationX;
    private float pinchTranslationY;
    private boolean isInPinchToZoomTouchMode;

    private float pinchCenterX;
    private float pinchCenterY;

    private int pointerId1, pointerId2;

    float pinchScale = 1f;
    private boolean zoomStarted;
    private boolean canZoomGesture;
    ValueAnimator zoomBackAnimator;

    long tapTime;
    boolean tapGesture;
    float tapX, tapY;
    boolean swipeToBackGesture;
    boolean maybeSwipeToBackGesture;
    float swipeToBackDy;
    ValueAnimator swipeToBackAnimator;

    public UndoView[] undoView = new UndoView[2];

    public boolean swipedBack;
    private boolean isTablet;

    public GroupCallRenderersContainer(@NonNull Context context, RecyclerView listView, RecyclerView fullscreenListView, ArrayList<GroupCallMiniTextureView> attachedRenderers, ChatObject.Call call, GroupCallActivity groupCallActivity) {
        super(context);
        this.listView = listView;
        this.fullscreenListView = fullscreenListView;
        this.attachedRenderers = attachedRenderers;
        this.call = call;
        this.groupCallActivity = groupCallActivity;

        backButton = new ImageView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(ActionBar.getCurrentActionBarHeight(), MeasureSpec.EXACTLY));
            }
        };
        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setColor(Color.WHITE);
        backButton.setImageDrawable(backDrawable);
        backButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        backButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        backButton.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, 55)));
        topShadowView = new View(context);
        topShadowDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.45f))});
        topShadowView.setBackgroundDrawable(topShadowDrawable);
        addView(topShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120));

        addView(backButton, LayoutHelper.createFrame(56, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        backButton.setOnClickListener(view -> onBackPressed());

        pinButton = new ImageView(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                pinContainer.invalidate();
                GroupCallRenderersContainer.this.invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(ActionBar.getCurrentActionBarHeight(), MeasureSpec.EXACTLY));
            }
        };

        final Drawable pinRippleDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100));
        pinContainer = new View(context) {

            @Override
            protected void drawableStateChanged() {
                super.drawableStateChanged();
                pinRippleDrawable.setState(getDrawableState());
            }

            @Override
            public boolean verifyDrawable(Drawable drawable) {
                return pinRippleDrawable == drawable || super.verifyDrawable(drawable);
            }

            @Override
            public void jumpDrawablesToCurrentState() {
                super.jumpDrawablesToCurrentState();
                pinRippleDrawable.jumpToCurrentState();
            }


            @Override
            protected void dispatchDraw(Canvas canvas) {
                float w = pinTextView.getMeasuredWidth() * (1f - pinDrawable.getProgress()) + unpinTextView.getMeasuredWidth() * pinDrawable.getProgress();
                canvas.save();
                pinRippleDrawable.setBounds(0, 0, AndroidUtilities.dp(50) + (int) w, getMeasuredHeight());
                pinRippleDrawable.draw(canvas);
                super.dispatchDraw(canvas);
            }
        };
        pinContainer.setOnClickListener(view -> {
            if (inFullscreenMode) {
                hasPinnedVideo = !hasPinnedVideo;
                pinDrawable.setCrossOut(hasPinnedVideo, true);
                requestLayout();
            }
        });
        pinRippleDrawable.setCallback(pinContainer);

        addView(pinContainer);

        pinDrawable = new CrossOutDrawable(context, R.drawable.msg_pin_filled, null);
        pinDrawable.setOffsets(-AndroidUtilities.dp(1), AndroidUtilities.dp(2), AndroidUtilities.dp(1));
        pinButton.setImageDrawable(pinDrawable);
        pinButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        addView(pinButton, LayoutHelper.createFrame(56, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));


        pinTextView = new TextView(context);
        pinTextView.setTextColor(Color.WHITE);
        pinTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        pinTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        pinTextView.setText(LocaleController.getString("CallVideoPin", R.string.CallVideoPin));

        unpinTextView = new TextView(context);
        unpinTextView.setTextColor(Color.WHITE);
        unpinTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        unpinTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        unpinTextView.setText(LocaleController.getString("CallVideoUnpin", R.string.CallVideoUnpin));


        addView(pinTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        addView(unpinTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        Drawable toastBackgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(18), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_listViewBackground), (int) (255 * 0.8f)));
        speakingMembersToast = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (speakingMembersToastChangeProgress == 1f) {
                    toastBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    speakingMembersAvatars.setTranslationX(0);
                    speakingMembersText.setTranslationX(0);
                } else {
                    float progress = CubicBezierInterpolator.DEFAULT.getInterpolation(speakingMembersToastChangeProgress);
                    float offset = (speakingMembersToastFromLeft - getLeft()) * (1f - progress);
                    float offsetText = (speakingMembersToastFromTextLeft - speakingMembersText.getLeft()) * (1f - progress);
                    toastBackgroundDrawable.setBounds((int) offset, 0, getMeasuredWidth() + (int) ((speakingMembersToastFromRight - getRight()) * (1f - progress)), getMeasuredHeight());
                    speakingMembersAvatars.setTranslationX(offset);
                    speakingMembersText.setTranslationX(-offsetText);
                }
                toastBackgroundDrawable.draw(canvas);
                super.dispatchDraw(canvas);
            }
        };

        speakingMembersAvatars = new AvatarsImageView(context, true);
        speakingMembersAvatars.setStyle(AvatarsImageView.STYLE_GROUP_CALL_TOOLTIP);

        speakingMembersToast.setClipChildren(false);
        speakingMembersToast.setClipToPadding(false);
        speakingMembersToast.addView(speakingMembersAvatars, LayoutHelper.createFrame(100, 32, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));


        speakingMembersText = new TextView(context);
        speakingMembersText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        speakingMembersText.setTextColor(Color.WHITE);
        speakingMembersText.setLines(1);
        speakingMembersText.setEllipsize(TextUtils.TruncateAt.END);
        speakingMembersToast.addView(speakingMembersText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        addView(speakingMembersToast, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        ViewConfiguration configuration = ViewConfiguration.get(getContext());

        touchSlop = configuration.getScaledTouchSlop();

        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    GroupCallRenderersContainer.this.invalidate();
                }
            };
            undoView[a].setHideAnimationType(2);
            undoView[a].setAdditionalTranslationY(AndroidUtilities.dp(10));
            addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 16, 0, 0, 8));
        }

        pinContainer.setVisibility(View.GONE);
        setIsTablet(GroupCallActivity.isTabletMode);
    }

    protected void onBackPressed() {

    }

    public void setIsTablet(boolean tablet) {
        if (isTablet != tablet) {
            isTablet = tablet;
            FrameLayout.LayoutParams lp = (LayoutParams) backButton.getLayoutParams();
            lp.gravity = tablet ? (Gravity.RIGHT | Gravity.BOTTOM) : (Gravity.LEFT | Gravity.TOP);
            lp.rightMargin = tablet ? AndroidUtilities.dp(GroupCallActivity.TABLET_LIST_SIZE + 8) : 0;
            lp.bottomMargin = tablet ? -AndroidUtilities.dp(8) : 0;
            if (isTablet) {
                backButton.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.msg_calls_minimize));
            } else {
                BackDrawable backDrawable = new BackDrawable(false);
                backDrawable.setColor(Color.WHITE);
                backButton.setImageDrawable(backDrawable);
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (drawFirst) {
            if (child instanceof GroupCallMiniTextureView) {
                if (((GroupCallMiniTextureView) child).drawFirst) {
                    float listTop = listView.getY() - getTop();
                    float listBottom = listTop + listView.getMeasuredHeight() - listView.getTranslationY();
                    canvas.save();
                    canvas.clipRect(0, listTop, getMeasuredWidth(), listBottom);
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
            }
            return true;
        }
        if (child == undoView[0] || child == undoView[1]) {
            return true;
        }
        if (child instanceof GroupCallMiniTextureView) {
            GroupCallMiniTextureView textureView = (GroupCallMiniTextureView) child;

            if (textureView == fullscreenTextureView || textureView == outFullscreenTextureView || notDrawRenderes || textureView.drawFirst) {
                return true;
            }
            if (textureView.primaryView != null) {
                float listTop = listView.getY() - getTop();
                float listBottom = listTop + listView.getMeasuredHeight() - listView.getTranslationY();
                float progress = progressToFullscreenMode;
                if (textureView.secondaryView == null) {
                    progress = 0f;
                }
                canvas.save();
                canvas.clipRect(0, listTop * (1f - progress), getMeasuredWidth(), listBottom * (1f - progress) + getMeasuredHeight() * progress);
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            } else if (GroupCallActivity.isTabletMode) {
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            } else {
                return super.drawChild(canvas, child, drawingTime);
            }
        }
        if (drawRenderesOnly) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (GroupCallActivity.isTabletMode) {
            drawRenderesOnly = true;
            super.dispatchDraw(canvas);
            drawRenderesOnly = false;
        }

        drawFirst = true;
        super.dispatchDraw(canvas);
        drawFirst = false;

        if (outFullscreenTextureView != null || fullscreenTextureView != null) {
            float listTop = listView.getY() - getTop();
            float listBottom = listTop + listView.getMeasuredHeight() - listView.getTranslationY();
            float progress = progressToFullscreenMode;
            canvas.save();
            if (!GroupCallActivity.isTabletMode && fullscreenTextureView != null && !fullscreenTextureView.forceDetached && fullscreenTextureView.primaryView != null) {
                canvas.clipRect(0, listTop * (1f - progress), getMeasuredWidth(), listBottom * (1f - progress) + getMeasuredHeight() * progress);
            } else if (GroupCallActivity.isTabletMode) {
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            }
            if (outFullscreenTextureView != null && outFullscreenTextureView.getParent() != null) {
                canvas.save();
                canvas.translate(outFullscreenTextureView.getX(), outFullscreenTextureView.getY());
                outFullscreenTextureView.draw(canvas);
                canvas.restore();
            }
            if (fullscreenTextureView != null && fullscreenTextureView.getParent() != null) {
                if (fullscreenTextureView.getAlpha() != 1f) {
                    AndroidUtilities.rectTmp.set(fullscreenTextureView.getX(), fullscreenTextureView.getY(), fullscreenTextureView.getX() + fullscreenTextureView.getMeasuredWidth(), fullscreenTextureView.getY() + fullscreenTextureView.getMeasuredHeight());
                    canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (255 * fullscreenTextureView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                } else {
                    canvas.save();
                }
                boolean swipeToBack = swipeToBackGesture || swipeToBackAnimator != null;
                if (swipeToBack) {
                    canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - ((isLandscapeMode || GroupCallActivity.isTabletMode) ? 0 : AndroidUtilities.dp(90)));
                }
                canvas.translate(fullscreenTextureView.getX(), fullscreenTextureView.getY());
                fullscreenTextureView.setSwipeToBack(swipeToBack, swipeToBackDy);
                fullscreenTextureView.setZoom(zoomStarted || zoomBackAnimator != null, pinchScale, pinchCenterX, pinchCenterY, pinchTranslationX, pinchTranslationY);
                fullscreenTextureView.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }
        for (int i = 0; i < 2; i++) {
            if (undoView[i].getVisibility() == View.VISIBLE) {
                canvas.save();
                float offset = isLandscapeMode ? 0 : -AndroidUtilities.dp(90) * (1f - progressToHideUi);
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - (isLandscapeMode ? 0 : AndroidUtilities.dp(90)) + offset - AndroidUtilities.dp(18));
                if (isTablet) {
                    canvas.translate(undoView[i].getX() - AndroidUtilities.dp(8), undoView[i].getY() - AndroidUtilities.dp(8));
                } else {
                    canvas.translate(undoView[i].getX() - AndroidUtilities.dp(8), undoView[i].getY() - (isLandscapeMode ? 0 : AndroidUtilities.dp(90)) + offset - AndroidUtilities.dp(26));
                }
                if (undoView[i].getAlpha() != 1f) {
                    canvas.saveLayerAlpha(0, 0, undoView[i].getMeasuredWidth(), undoView[i].getMeasuredHeight(), (int) (255 * undoView[i].getAlpha()), Canvas.ALL_SAVE_FLAG);
                } else {
                    canvas.save();
                }
                canvas.scale(undoView[i].getScaleX(), undoView[i].getScaleY(), undoView[i].getMeasuredWidth() / 2f, undoView[i].getMeasuredHeight() / 2f);
                undoView[i].draw(canvas);
                canvas.restore();
                canvas.restore();
            }
        }
        float a = progressToFullscreenMode * (1f - progressToHideUi);
        if (replaceFullscreenViewAnimator != null && outFullscreenTextureView != null && fullscreenTextureView != null) {
            float shadowAlpha = a;
            if (outFullscreenTextureView.hasVideo != fullscreenTextureView.hasVideo) {
                if (!fullscreenTextureView.hasVideo) {
                    shadowAlpha *= (1f - fullscreenTextureView.getAlpha());
                } else {
                    shadowAlpha *= fullscreenTextureView.getAlpha();
                }
            } else if (!fullscreenTextureView.hasVideo) {
                shadowAlpha = 0;
            }
            topShadowDrawable.setAlpha((int) (255 * shadowAlpha));
        } else if (fullscreenTextureView != null) {
            topShadowDrawable.setAlpha((int) (255 * a * (1f - fullscreenTextureView.progressToNoVideoStub)));
        } else {
            topShadowDrawable.setAlpha((int) (255 * a));
        }

        backButton.setAlpha(a);
        pinButton.setAlpha(a);

        float x1 = getMeasuredWidth() - pinTextView.getMeasuredWidth();
        float x2 = getMeasuredWidth() - unpinTextView.getMeasuredWidth();
        float pinY = (ActionBar.getCurrentActionBarHeight() - pinTextView.getMeasuredHeight()) / 2f - AndroidUtilities.dp(1);
        float pinX = x2 * pinDrawable.getProgress() + x1 * (1f - pinDrawable.getProgress()) - AndroidUtilities.dp(21);
        if (GroupCallActivity.isTabletMode) {
            pinX -= AndroidUtilities.dp(GroupCallActivity.TABLET_LIST_SIZE + 8);
        } else {
            pinX -= (GroupCallActivity.isLandscapeMode ? AndroidUtilities.dp(180) : 0);
        }
        pinTextView.setTranslationX(pinX);
        unpinTextView.setTranslationX(pinX);
        pinTextView.setTranslationY(pinY);
        unpinTextView.setTranslationY(pinY);

        pinContainer.setTranslationX(pinX - AndroidUtilities.dp(36f));
        pinContainer.setTranslationY((ActionBar.getCurrentActionBarHeight() - pinContainer.getMeasuredHeight()) / 2f);

        pinButton.setTranslationX(pinX - AndroidUtilities.dp(44f));

        pinTextView.setAlpha(a * (1f - pinDrawable.getProgress()));
        unpinTextView.setAlpha(a * pinDrawable.getProgress());
        pinContainer.setAlpha(a);

        if (speakingMembersToastChangeProgress != 1) {
            speakingMembersToastChangeProgress += 16 / 220f;
            if (speakingMembersToastChangeProgress > 1f) {
                speakingMembersToastChangeProgress = 1f;
            } else {
                invalidate();
            }
            speakingMembersToast.invalidate();
        }


        if (showSpeakingMembersToast && showSpeakingMembersToastProgress != 1f) {
            showSpeakingMembersToastProgress += 16 / 150f;
            if (showSpeakingMembersToastProgress > 1f) {
                showSpeakingMembersToastProgress = 1f;
            } else {
                invalidate();
            }
        } else if (!showSpeakingMembersToast && showSpeakingMembersToastProgress != 0) {
            showSpeakingMembersToastProgress -= 16 / 150f;
            if (showSpeakingMembersToastProgress < 0) {
                showSpeakingMembersToastProgress = 0;
            } else {
                invalidate();
            }
        }


        if (isLandscapeMode) {
            speakingMembersToast.setTranslationY(AndroidUtilities.dp(16));
        } else {
            speakingMembersToast.setTranslationY(ActionBar.getCurrentActionBarHeight() * (1f - progressToHideUi) + AndroidUtilities.dp(8) + AndroidUtilities.dp(8) * progressToHideUi);
        }
        speakingMembersToast.setAlpha(showSpeakingMembersToastProgress * progressToFullscreenMode);
        speakingMembersToast.setScaleX(0.5f + 0.5f * showSpeakingMembersToastProgress);
        speakingMembersToast.setScaleY(0.5f + 0.5f * showSpeakingMembersToastProgress);

        final boolean isTablet = GroupCallActivity.isTabletMode;

        if (GroupCallActivity.isTabletMode) {
            notDrawRenderes = true;
            super.dispatchDraw(canvas);
            notDrawRenderes = false;
        } else {
            super.dispatchDraw(canvas);
        }

        if (fullscreenListView.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < fullscreenListView.getChildCount(); i++) {
                GroupCallFullscreenAdapter.GroupCallUserCell child = (GroupCallFullscreenAdapter.GroupCallUserCell) fullscreenListView.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && child.getAlpha() != 0) {
                    canvas.save();
                    canvas.translate(child.getX() + fullscreenListView.getX(), child.getY() + fullscreenListView.getY());
                    canvas.scale(child.getScaleX(), child.getScaleY(), child.getMeasuredWidth() / 2f, child.getMeasuredHeight() / 2f);
                    child.drawOverlays(canvas);
                    canvas.restore();
                }
            }
        }
    }

    ValueAnimator replaceFullscreenViewAnimator;

    public void requestFullscreen(ChatObject.VideoParticipant videoParticipant) {
        if ((videoParticipant == null && fullscreenParticipant == null) || (videoParticipant != null && videoParticipant.equals(fullscreenParticipant))) {
            return;
        }
//        if (videoParticipant != null && fullscreenParticipant != null && fullscreenTextureView != null) {
//            if (!fullscreenTextureView.hasVideo && MessageObject.getPeerId(fullscreenParticipant.participant.peer) == MessageObject.getPeerId(videoParticipant.participant.peer)) {
//                fullscreenTextureView.participant = videoParticipant;
//                fullscreenParticipant = videoParticipant;
//                fullscreenTextureView.updateAttachState(true);
//                return;
//            }
//        }
        long peerId = videoParticipant == null ? 0 : MessageObject.getPeerId(videoParticipant.participant.peer);
        if (fullscreenTextureView != null) {
            fullscreenTextureView.runDelayedAnimations();
        }

        if (replaceFullscreenViewAnimator != null) {
            replaceFullscreenViewAnimator.cancel();
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && fullscreenParticipant != null) {
            service.requestFullScreen(fullscreenParticipant.participant, false, fullscreenParticipant.presentation);
        }
        fullscreenParticipant = videoParticipant;
        if (service != null && fullscreenParticipant != null) {
            service.requestFullScreen(fullscreenParticipant.participant, true, fullscreenParticipant.presentation);
        }
        fullscreenPeerId = peerId;

        boolean oldInFullscreen = inFullscreenMode;
        lastUpdateTime = System.currentTimeMillis();

        if (videoParticipant == null) {
            if (inFullscreenMode) {
                if (fullscreenAnimator != null) {
                    fullscreenAnimator.cancel();
                }
                inFullscreenMode = false;

                if ((fullscreenTextureView.primaryView == null && fullscreenTextureView.secondaryView == null && fullscreenTextureView.tabletGridView == null) || !ChatObject.Call.videoIsActive(fullscreenTextureView.participant.participant, fullscreenTextureView.participant.presentation, call)) {
                    fullscreenTextureView.forceDetach(true);
                    if (fullscreenTextureView.primaryView != null) {
                        fullscreenTextureView.primaryView.setRenderer(null);
                    }
                    if (fullscreenTextureView.secondaryView != null) {
                        fullscreenTextureView.secondaryView.setRenderer(null);
                    }
                    if (fullscreenTextureView.tabletGridView != null) {
                        fullscreenTextureView.tabletGridView.setRenderer(null);
                    }
                    final GroupCallMiniTextureView removingMiniView = fullscreenTextureView;
                    removingMiniView.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (removingMiniView.getParent() != null) {
                                removeView(removingMiniView);
                                removingMiniView.release();
                            }
                        }
                    }).setDuration(GroupCallActivity.TRANSITION_DURATION).start();
                } else {
                    fullscreenTextureView.setShowingInFullscreen(false, true);
                }
            }
            backButton.setEnabled(false);
            hasPinnedVideo = false;
        } else {
            GroupCallMiniTextureView textureView = null;
            for (int i = 0; i < attachedRenderers.size(); i++) {
                if (attachedRenderers.get(i).participant.equals(videoParticipant)) {
                    textureView = attachedRenderers.get(i);
                    break;
                }
            }

            if (textureView != null) {
                if (fullscreenAnimator != null) {
                    fullscreenAnimator.cancel();
                }
                if (!inFullscreenMode) {
                    inFullscreenMode = true;
                    clearCurrentFullscreenTextureView();
                    fullscreenTextureView = textureView;
                    fullscreenTextureView.setShowingInFullscreen(true, true);
                    invalidate();
                    pinDrawable.setCrossOut(hasPinnedVideo, false);
                } else {
                    hasPinnedVideo = false;
                    pinDrawable.setCrossOut(hasPinnedVideo, false);
                    fullscreenTextureView.forceDetach(false);
                    textureView.forceDetach(false);
                    final GroupCallMiniTextureView removingMiniView = textureView;

                    GroupCallMiniTextureView newSmallTextureView = null;
                    if (!isTablet && (fullscreenTextureView.primaryView != null || fullscreenTextureView.secondaryView != null || fullscreenTextureView.tabletGridView != null)) {
                        newSmallTextureView = new GroupCallMiniTextureView(this, attachedRenderers, call, groupCallActivity);
                        newSmallTextureView.setViews(fullscreenTextureView.primaryView, fullscreenTextureView.secondaryView, fullscreenTextureView.tabletGridView);
                        newSmallTextureView.setFullscreenMode(inFullscreenMode, false);
                        newSmallTextureView.updateAttachState(false);
                        if (fullscreenTextureView.primaryView != null) {
                            fullscreenTextureView.primaryView.setRenderer(newSmallTextureView);
                        }
                        if (fullscreenTextureView.secondaryView != null) {
                            fullscreenTextureView.secondaryView.setRenderer(newSmallTextureView);
                        }
                        if (fullscreenTextureView.tabletGridView != null) {
                            fullscreenTextureView.tabletGridView.setRenderer(newSmallTextureView);
                        }
                    }

                    GroupCallMiniTextureView newFullscreenTextureView = new GroupCallMiniTextureView(this, attachedRenderers, call, groupCallActivity);
                    newFullscreenTextureView.participant = textureView.participant;
                    newFullscreenTextureView.setViews(textureView.primaryView, textureView.secondaryView, textureView.tabletGridView);
                    newFullscreenTextureView.setFullscreenMode(inFullscreenMode, false);
                    newFullscreenTextureView.updateAttachState(false);
                    newFullscreenTextureView.textureView.renderer.setAlpha(1f);
                    newFullscreenTextureView.textureView.blurRenderer.setAlpha(1f);

                    if (textureView.primaryView != null) {
                        textureView.primaryView.setRenderer(newFullscreenTextureView);
                    }
                    if (textureView.secondaryView != null) {
                        textureView.secondaryView.setRenderer(newFullscreenTextureView);
                    }
                    if (textureView.tabletGridView != null) {
                        textureView.tabletGridView.setRenderer(newFullscreenTextureView);
                    }

                    newFullscreenTextureView.animateEnter = true;
                    newFullscreenTextureView.setAlpha(0);
                    outFullscreenTextureView = fullscreenTextureView;
                    replaceFullscreenViewAnimator = ObjectAnimator.ofFloat(newFullscreenTextureView, View.ALPHA, 0f, 1f);
                    replaceFullscreenViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            replaceFullscreenViewAnimator = null;
                            newFullscreenTextureView.animateEnter = false;
                            if (outFullscreenTextureView != null) {
                                if (outFullscreenTextureView.getParent() != null) {
                                    removeView(outFullscreenTextureView);
                                    removingMiniView.release();
                                }
                                outFullscreenTextureView = null;
                            }
                        }
                    });
                    if (newSmallTextureView != null) {
                        newSmallTextureView.setAlpha(0);
                        newSmallTextureView.setScaleX(0.5f);
                        newSmallTextureView.setScaleY(0.5f);
                        newSmallTextureView.animateEnter = true;
                    }

                    GroupCallMiniTextureView finalNewSmallTextureView = newSmallTextureView;
                    newFullscreenTextureView.runOnFrameRendered(() -> {
                        if (replaceFullscreenViewAnimator != null) {
                            replaceFullscreenViewAnimator.start();
                        }

                        removingMiniView.animate().scaleX(0.5f).scaleY(0.5f).alpha(0).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (removingMiniView.getParent() != null) {
                                    removeView(removingMiniView);
                                    removingMiniView.release();
                                }
                            }
                        }).setDuration(100).start();

                        if (finalNewSmallTextureView != null) {
                            finalNewSmallTextureView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    finalNewSmallTextureView.animateEnter = false;
                                }
                            }).start();
                        }
                    });

                    clearCurrentFullscreenTextureView();
                    fullscreenTextureView = newFullscreenTextureView;
                    fullscreenTextureView.setShowingInFullscreen(true, false);
                    update();
                }
            } else {
                if (inFullscreenMode) {
                    if (fullscreenTextureView.primaryView != null || fullscreenTextureView.secondaryView != null | fullscreenTextureView.tabletGridView != null) {
                        fullscreenTextureView.forceDetach(false);
                        GroupCallMiniTextureView newSmallTextureView = new GroupCallMiniTextureView(this, attachedRenderers, call, groupCallActivity);
                        newSmallTextureView.setViews(fullscreenTextureView.primaryView, fullscreenTextureView.secondaryView, fullscreenTextureView.tabletGridView);
                        newSmallTextureView.setFullscreenMode(inFullscreenMode, false);
                        newSmallTextureView.updateAttachState(false);
                        if (fullscreenTextureView.primaryView != null) {
                            fullscreenTextureView.primaryView.setRenderer(newSmallTextureView);
                        }
                        if (fullscreenTextureView.secondaryView != null) {
                            fullscreenTextureView.secondaryView.setRenderer(newSmallTextureView);
                        }
                        if (fullscreenTextureView.tabletGridView != null) {
                            fullscreenTextureView.tabletGridView.setRenderer(newSmallTextureView);
                        }

                        newSmallTextureView.setAlpha(0);
                        newSmallTextureView.setScaleX(0.5f);
                        newSmallTextureView.setScaleY(0.5f);
                        newSmallTextureView.animateEnter = true;
                        newSmallTextureView.runOnFrameRendered(() -> newSmallTextureView.animate().alpha(1f).scaleY(1f).scaleX(1f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                newSmallTextureView.animateEnter = false;
                            }
                        }).setDuration(150).start());
                    } else {
                        fullscreenTextureView.forceDetach(true);
                    }


                    GroupCallMiniTextureView newFullscreenTextureView = new GroupCallMiniTextureView(this, attachedRenderers, call, groupCallActivity);
                    newFullscreenTextureView.participant = videoParticipant;
                    newFullscreenTextureView.setFullscreenMode(inFullscreenMode, false);
                    newFullscreenTextureView.setShowingInFullscreen(true, false);

                    newFullscreenTextureView.animateEnter = true;
                    newFullscreenTextureView.setAlpha(0);
                    outFullscreenTextureView = fullscreenTextureView;
                    replaceFullscreenViewAnimator = ValueAnimator.ofFloat(0f, 1f);
                    replaceFullscreenViewAnimator.addUpdateListener(valueAnimator -> {
                        newFullscreenTextureView.setAlpha((Float) valueAnimator.getAnimatedValue());
                        invalidate();
                    });
                    replaceFullscreenViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            replaceFullscreenViewAnimator = null;
                            newFullscreenTextureView.animateEnter = false;
                            if (outFullscreenTextureView != null) {
                                if (outFullscreenTextureView.getParent() != null) {
                                    removeView(outFullscreenTextureView);
                                    outFullscreenTextureView.release();
                                }
                                outFullscreenTextureView = null;
                            }
                        }
                    });
                    replaceFullscreenViewAnimator.start();

                    clearCurrentFullscreenTextureView();
                    fullscreenTextureView = newFullscreenTextureView;
                    fullscreenTextureView.setShowingInFullscreen(true, false);
                    fullscreenTextureView.updateAttachState(false);
                    update();
                } else {
                    inFullscreenMode = true;
                    clearCurrentFullscreenTextureView();
                    fullscreenTextureView = new GroupCallMiniTextureView(this, attachedRenderers, call, groupCallActivity);
                    fullscreenTextureView.participant = videoParticipant;
                    fullscreenTextureView.setFullscreenMode(inFullscreenMode, false);
                    fullscreenTextureView.setShowingInFullscreen(true, false);
                    // fullscreenTextureView.textureView.renderer.setAlpha(1f);
                    fullscreenTextureView.setShowingInFullscreen(true, false);

                    replaceFullscreenViewAnimator = ObjectAnimator.ofFloat(fullscreenTextureView, View.ALPHA, 0f, 1f);
                    replaceFullscreenViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            replaceFullscreenViewAnimator = null;
                            fullscreenTextureView.animateEnter = false;
                            if (outFullscreenTextureView != null) {
                                if (outFullscreenTextureView.getParent() != null) {
                                    removeView(outFullscreenTextureView);
                                    outFullscreenTextureView.release();
                                }
                                outFullscreenTextureView = null;
                            }
                        }
                    });
                    replaceFullscreenViewAnimator.start();
                    invalidate();
                    pinDrawable.setCrossOut(hasPinnedVideo, false);
                }
            }
            backButton.setEnabled(true);
        }


        if (oldInFullscreen != inFullscreenMode) {
            if (!inFullscreenMode) {
                setUiVisible(true);
                if (hideUiRunnableIsScheduled) {
                    hideUiRunnableIsScheduled = false;
                    AndroidUtilities.cancelRunOnUIThread(hideUiRunnable);
                }
            } else {
                backButton.setVisibility(View.VISIBLE);
                pinButton.setVisibility(View.VISIBLE);
                unpinTextView.setVisibility(View.VISIBLE);
                pinContainer.setVisibility(View.VISIBLE);
            }
            onFullScreenModeChanged(true);
            fullscreenAnimator = ValueAnimator.ofFloat(progressToFullscreenMode, inFullscreenMode ? 1f : 0);
            fullscreenAnimator.addUpdateListener(valueAnimator -> {
                progressToFullscreenMode = (float) valueAnimator.getAnimatedValue();
                update();
            });
            GroupCallMiniTextureView textureViewFinal = fullscreenTextureView;
            textureViewFinal.animateToFullscreen = true;
            int currentAccount = groupCallActivity.getCurrentAccount();
            swipedBack = swipeToBackGesture;
            animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
            fullscreenAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                    fullscreenAnimator = null;
                    textureViewFinal.animateToFullscreen = false;
                    if (!inFullscreenMode) {
                        clearCurrentFullscreenTextureView();
                        fullscreenTextureView = null;
                        fullscreenPeerId = 0;
                    }
                    progressToFullscreenMode = inFullscreenMode ? 1f : 0;
                    update();
                    onFullScreenModeChanged(false);
                    if (!inFullscreenMode) {
                        backButton.setVisibility(View.GONE);
                        pinButton.setVisibility(View.GONE);
                        unpinTextView.setVisibility(View.GONE);
                        pinContainer.setVisibility(View.GONE);
                    }
                }
            });

            fullscreenAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            fullscreenAnimator.setDuration(TRANSITION_DURATION);
            fullscreenTextureView.textureView.synchOrRunAnimation(fullscreenAnimator);
        }

        animateSwipeToBack(fullscreenParticipant == null);
    }

    private void clearCurrentFullscreenTextureView() {
        if (fullscreenTextureView != null) {
            fullscreenTextureView.setSwipeToBack(false, 0);
            fullscreenTextureView.setZoom(false, 1f, 0, 0, 0, 0);
        }
    }

    protected void update() {
        invalidate();
    }

    protected void onFullScreenModeChanged(boolean startAnimaion) {

    }

    private void setUiVisible(boolean uiVisible) {
        if (this.uiVisible != uiVisible) {
            this.uiVisible = uiVisible;
            onUiVisibilityChanged();

            if (uiVisible && inFullscreenMode) {
                if (!hideUiRunnableIsScheduled) {
                    hideUiRunnableIsScheduled = true;
                    AndroidUtilities.runOnUIThread(hideUiRunnable, 3000);
                }
            } else {
                hideUiRunnableIsScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(hideUiRunnable);
            }
            if (fullscreenTextureView != null) {
                fullscreenTextureView.requestLayout();
            }
        }
    }

    protected void onUiVisibilityChanged() {

    }

    protected boolean canHideUI() {
        return inFullscreenMode;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if ((maybeSwipeToBackGesture || swipeToBackGesture) && (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            maybeSwipeToBackGesture = false;
            if (swipeToBackGesture) {
                if (ev.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(swipeToBackDy) > AndroidUtilities.dp(120)) {
                    groupCallActivity.fullscreenFor(null);
                } else {
                    animateSwipeToBack(false);
                }
            }
            invalidate();
        }

        if (!inFullscreenMode || (!maybeSwipeToBackGesture && !swipeToBackGesture && !tapGesture && !canZoomGesture && !isInPinchToZoomTouchMode && !zoomStarted && ev.getActionMasked() != MotionEvent.ACTION_DOWN) || fullscreenTextureView == null) {
            finishZoom();
            return false;
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            maybeSwipeToBackGesture = false;
            swipeToBackGesture = false;
            canZoomGesture = false;
            isInPinchToZoomTouchMode = false;
            zoomStarted = false;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && swipeToBackAnimator != null) {
            maybeSwipeToBackGesture = false;
            swipeToBackGesture = true;
            tapY = ev.getY() - swipeToBackDy;
            swipeToBackAnimator.removeAllListeners();
            swipeToBackAnimator.cancel();
            swipeToBackAnimator = null;
        } else if (swipeToBackAnimator != null) {
            finishZoom();
            return false;
        }
        if (fullscreenTextureView.isInsideStopScreenButton(ev.getX(), ev.getY())) {
            return false;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && !swipeToBackGesture) {
            AndroidUtilities.rectTmp.set(0, ActionBar.getCurrentActionBarHeight(), fullscreenTextureView.getMeasuredWidth() + (isLandscapeMode && uiVisible ? -AndroidUtilities.dp(90) : 0), fullscreenTextureView.getMeasuredHeight() + (!isLandscapeMode && uiVisible ? -AndroidUtilities.dp(90) : 0));
            if (AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY())) {
                tapTime = System.currentTimeMillis();
                tapGesture = true;
                maybeSwipeToBackGesture = true;
                tapX = ev.getX();
                tapY = ev.getY();
            }
        } else if ((maybeSwipeToBackGesture || swipeToBackGesture || tapGesture) && ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(tapX - ev.getX()) > touchSlop || Math.abs(tapY - ev.getY()) > touchSlop) {
                tapGesture = false;
            }
            if (maybeSwipeToBackGesture && !zoomStarted && Math.abs(tapY - ev.getY()) > touchSlop * 2) {
                tapY = ev.getY();
                maybeSwipeToBackGesture = false;
                swipeToBackGesture = true;
            } else if (swipeToBackGesture) {
                swipeToBackDy = ev.getY() - tapY;
                invalidate();
            }

            if (maybeSwipeToBackGesture && Math.abs(tapX - ev.getX()) > touchSlop * 4) {
                maybeSwipeToBackGesture = false;
            }
        }
        if (tapGesture && ev.getActionMasked() == MotionEvent.ACTION_UP && System.currentTimeMillis() - tapTime < 200) {
            boolean confirmAction = false;
            tapGesture = false;
            if (showSpeakingMembersToast) {
                AndroidUtilities.rectTmp.set(speakingMembersToast.getX(), speakingMembersToast.getY(), speakingMembersToast.getX() + speakingMembersToast.getWidth(), speakingMembersToast.getY() + speakingMembersToast.getHeight());
                if (call != null && AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY())) {
                    boolean found = false;
                    for (int i = 0; i < call.visibleVideoParticipants.size(); i++) {
                        if (speakingToastPeerId == MessageObject.getPeerId(call.visibleVideoParticipants.get(i).participant.peer)) {
                            found = true;
                            confirmAction = true;
                            groupCallActivity.fullscreenFor(call.visibleVideoParticipants.get(i));
                        }
                    }
                    if (!found) {
                        TLRPC.TL_groupCallParticipant participant = call.participants.get(speakingToastPeerId);
                        groupCallActivity.fullscreenFor(new ChatObject.VideoParticipant(participant, false, false));
                        confirmAction = true;
                    }
                }
            }

            if (!confirmAction) {
                setUiVisible(!uiVisible);
            }
            swipeToBackDy = 0;
            invalidate();
        }

        if (!fullscreenTextureView.hasVideo || swipeToBackGesture) {
            finishZoom();
            return tapGesture || swipeToBackGesture || maybeSwipeToBackGesture;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                View renderer = fullscreenTextureView.textureView.renderer;
                AndroidUtilities.rectTmp.set(renderer.getX(), renderer.getY(), renderer.getX() + renderer.getMeasuredWidth(), renderer.getY() + renderer.getMeasuredHeight());
                AndroidUtilities.rectTmp.inset((renderer.getMeasuredHeight() * fullscreenTextureView.textureView.scaleTextureToFill - renderer.getMeasuredHeight()) / 2, (renderer.getMeasuredWidth() * fullscreenTextureView.textureView.scaleTextureToFill - renderer.getMeasuredWidth()) / 2);
                if (!GroupCallActivity.isLandscapeMode) {
                    AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                    AndroidUtilities.rectTmp.bottom = Math.min(AndroidUtilities.rectTmp.bottom, fullscreenTextureView.getMeasuredHeight() - AndroidUtilities.dp(90));
                } else {
                    AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                    AndroidUtilities.rectTmp.right = Math.min(AndroidUtilities.rectTmp.right, fullscreenTextureView.getMeasuredWidth() - AndroidUtilities.dp(90));
                }
                canZoomGesture = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
                if (!canZoomGesture) {
                    finishZoom();
                    return maybeSwipeToBackGesture;
                }
            }
            if (!isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                pinchScale = 1f;

                pointerId1 = ev.getPointerId(0);
                pointerId2 = ev.getPointerId(1);
                isInPinchToZoomTouchMode = true;
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (pointerId1 == ev.getPointerId(i)) {
                    index1 = i;
                }
                if (pointerId2 == ev.getPointerId(i)) {
                    index2 = i;
                }
            }
            if (index1 == -1 || index2 == -1) {
                getParent().requestDisallowInterceptTouchEvent(false);
                finishZoom();
                return maybeSwipeToBackGesture;
            }
            pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
            if (pinchScale > 1.005f && !zoomStarted) {
                pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                pinchScale = 1f;
                pinchTranslationX = 0f;
                pinchTranslationY = 0f;
                getParent().requestDisallowInterceptTouchEvent(true);
                zoomStarted = true;//
                isInPinchToZoomTouchMode = true;
            }

            float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
            float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;

            float moveDx = pinchStartCenterX - newPinchCenterX;
            float moveDy = pinchStartCenterY - newPinchCenterY;
            pinchTranslationX = -moveDx / pinchScale;
            pinchTranslationY = -moveDy / pinchScale;
            invalidate();
        } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            getParent().requestDisallowInterceptTouchEvent(false);
            finishZoom();
        }
        return canZoomGesture || tapGesture || maybeSwipeToBackGesture;
    }

    private void animateSwipeToBack(boolean aplay) {
        if (swipeToBackGesture) {
            swipeToBackGesture = false;
            swipeToBackAnimator = aplay ? ValueAnimator.ofFloat(swipeToBackDy, 0) : ValueAnimator.ofFloat(swipeToBackDy, 0);
            swipeToBackAnimator.addUpdateListener(valueAnimator -> {
                swipeToBackDy = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            swipeToBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    swipeToBackAnimator = null;
                    swipeToBackDy = 0;
                    invalidate();
                }
            });
            swipeToBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);

            swipeToBackAnimator.setDuration(aplay ? TRANSITION_DURATION : 200);
            swipeToBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            if (fullscreenTextureView != null) {
                fullscreenTextureView.textureView.synchOrRunAnimation(swipeToBackAnimator);
            } else {
                swipeToBackAnimator.start();
            }
            lastUpdateTime = System.currentTimeMillis();
        }
        maybeSwipeToBackGesture = false;
    }

    private void finishZoom() {
        if (zoomStarted) {
            zoomStarted = false;
            zoomBackAnimator = ValueAnimator.ofFloat(1f, 0);

            float fromScale = pinchScale;
            float fromTranslateX = pinchTranslationX;
            float fromTranslateY = pinchTranslationY;
            zoomBackAnimator.addUpdateListener(valueAnimator -> {
                float v = (float) valueAnimator.getAnimatedValue();
                pinchScale = fromScale * v + 1f * (1f - v);
                pinchTranslationX = fromTranslateX * v;
                pinchTranslationY = fromTranslateY * v;
                invalidate();
            });

            zoomBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    zoomBackAnimator = null;
                    pinchScale = 1f;
                    pinchTranslationX = 0;
                    pinchTranslationY = 0;
                    invalidate();
                }
            });
            zoomBackAnimator.setDuration(TRANSITION_DURATION);
            zoomBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            zoomBackAnimator.start();
            lastUpdateTime = System.currentTimeMillis();
        }
        canZoomGesture = false;
        isInPinchToZoomTouchMode = false;

    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    public void delayHideUi() {
        if (hideUiRunnableIsScheduled) {
            AndroidUtilities.cancelRunOnUIThread(hideUiRunnable);
            AndroidUtilities.runOnUIThread(hideUiRunnable, 3000);
        }
    }

    public boolean isUiVisible() {
        return uiVisible;
    }

    public void setProgressToHideUi(float progressToHideUi) {
        if (this.progressToHideUi != progressToHideUi) {
            this.progressToHideUi = progressToHideUi;
            invalidate();
            if (fullscreenTextureView != null) {
                fullscreenTextureView.invalidate();
            }
        }
    }

    public void setAmplitude(TLRPC.TL_groupCallParticipant participant, float v) {
        for (int i = 0; i < attachedRenderers.size(); i++) {
            if (MessageObject.getPeerId(attachedRenderers.get(i).participant.participant.peer) == MessageObject.getPeerId(participant.peer)) {
                attachedRenderers.get(i).setAmplitude(v);
            }
        }
    }

    public boolean isAnimating() {
        return fullscreenAnimator != null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (GroupCallActivity.isTabletMode) {
            ((MarginLayoutParams) topShadowView.getLayoutParams()).rightMargin = AndroidUtilities.dp(GroupCallActivity.TABLET_LIST_SIZE + 8);
        } else if (GroupCallActivity.isLandscapeMode) {
            ((MarginLayoutParams) topShadowView.getLayoutParams()).rightMargin = AndroidUtilities.dp(90);
        } else {
            ((MarginLayoutParams) topShadowView.getLayoutParams()).rightMargin = 0;
        }

        pinContainer.getLayoutParams().height = AndroidUtilities.dp(40);
        pinTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.UNSPECIFIED), heightMeasureSpec);
        unpinTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.UNSPECIFIED), heightMeasureSpec);
        pinContainer.getLayoutParams().width = AndroidUtilities.dp(46) + (!hasPinnedVideo ? pinTextView.getMeasuredWidth() : unpinTextView.getMeasuredWidth());

        ((MarginLayoutParams) speakingMembersToast.getLayoutParams()).rightMargin = GroupCallActivity.isLandscapeMode ? AndroidUtilities.dp(45) : 0;

        for (int a = 0; a < 2; a++) {
            MarginLayoutParams lp = (MarginLayoutParams) undoView[a].getLayoutParams();
            if (isTablet) {
                lp.rightMargin = AndroidUtilities.dp(8 + 16 + GroupCallActivity.TABLET_LIST_SIZE);
            } else {
                lp.rightMargin = isLandscapeMode ? AndroidUtilities.dp(180) : 0;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean autoPinEnabled() {
        return !hasPinnedVideo && (System.currentTimeMillis() - lastUpdateTime) > 2000 && !swipeToBackGesture && !isInPinchToZoomTouchMode;
    }

    long lastUpdateTooltipTime;
    Runnable updateTooltipRunnbale;

    public void setVisibleParticipant(boolean animated) {
        if (!inFullscreenMode || isTablet || fullscreenParticipant == null || fullscreenAnimator != null || call == null) {
            if (showSpeakingMembersToast) {
                showSpeakingMembersToast = false;
                showSpeakingMembersToastProgress = 0f;
            }
            return;
        }
        int speakingIndex = 0;
        int currenAccount = groupCallActivity.getCurrentAccount();
        if (System.currentTimeMillis() - lastUpdateTooltipTime < 500) {
            if (updateTooltipRunnbale == null) {
                AndroidUtilities.runOnUIThread(updateTooltipRunnbale = () -> {
                    updateTooltipRunnbale = null;
                    setVisibleParticipant(true);
                }, System.currentTimeMillis() - lastUpdateTooltipTime + 50);
            }
            return;
        }
        lastUpdateTooltipTime = System.currentTimeMillis();
        SpannableStringBuilder spannableStringBuilder = null;
        for (int i = 0; i < call.currentSpeakingPeers.size(); i++) {
            long key = call.currentSpeakingPeers.keyAt(i);
            TLRPC.TL_groupCallParticipant participant = call.currentSpeakingPeers.get(key);
            if (participant.self || participant.muted_by_you || MessageObject.getPeerId(fullscreenParticipant.participant.peer) == MessageObject.getPeerId(participant.peer)) {
                continue;
            }
            long peerId = MessageObject.getPeerId(participant.peer);
            long diff = SystemClock.uptimeMillis() - participant.lastSpeakTime;
            boolean newSpeaking = diff < 500;
            if (newSpeaking) {
                if (spannableStringBuilder == null) {
                    spannableStringBuilder = new SpannableStringBuilder();
                }
                if (speakingIndex == 0) {
                    speakingToastPeerId = MessageObject.getPeerId(participant.peer);
                }
                if (speakingIndex < 3) {
                    TLRPC.User user = peerId > 0 ? MessagesController.getInstance(currenAccount).getUser(peerId) : null;
                    TLRPC.Chat chat = peerId <= 0 ? MessagesController.getInstance(currenAccount).getChat(peerId) : null;
                    if (user == null && chat == null) {
                        continue;
                    }
                    speakingMembersAvatars.setObject(speakingIndex, currenAccount, participant);
                    if (speakingIndex != 0) {
                        spannableStringBuilder.append(", ");
                    }
                    if (user != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            spannableStringBuilder.append(UserObject.getFirstName(user), new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0);
                        } else {
                            spannableStringBuilder.append(UserObject.getFirstName(user));
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            spannableStringBuilder.append(chat.title, new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0);
                        } else {
                            spannableStringBuilder.append(chat.title);
                        }
                    }
                }
                speakingIndex++;
                if (speakingIndex == 3) {
                    break;
                }
            }
        }
        boolean show;
        if (speakingIndex == 0) {
            show = false;
        } else {
            show = true;
        }

        if (!showSpeakingMembersToast && show) {
            animated = false;
        } else if (!show && showSpeakingMembersToast) {
            showSpeakingMembersToast = show;
            invalidate();
            return;
        } else if (showSpeakingMembersToast && show) {
            speakingMembersToastFromLeft = speakingMembersToast.getLeft();
            speakingMembersToastFromRight = speakingMembersToast.getRight();
            speakingMembersToastFromTextLeft = speakingMembersText.getLeft();
            speakingMembersToastChangeProgress = 0;
        }

        if (!show) {
            showSpeakingMembersToast = show;
            invalidate();
            return;
        }
        String s = LocaleController.getPluralString("MembersAreSpeakingToast", speakingIndex);
        int replaceIndex = s.indexOf("un1");
        SpannableStringBuilder spannableStringBuilder1 = new SpannableStringBuilder(s);
        spannableStringBuilder1.replace(replaceIndex, replaceIndex + 3, spannableStringBuilder);
        speakingMembersText.setText(spannableStringBuilder1);

        int leftMargin;
        if (speakingIndex == 0) {
            leftMargin = 0;
        } else if (speakingIndex == 1) {
            leftMargin = AndroidUtilities.dp(32 + 8);
        } else if (speakingIndex == 2) {
            leftMargin = AndroidUtilities.dp(32 + 24 + 8);
        } else {
            leftMargin = AndroidUtilities.dp(32 + 24 + 24 + 8);
        }
        ((LayoutParams) speakingMembersText.getLayoutParams()).leftMargin = leftMargin;
        ((LayoutParams) speakingMembersText.getLayoutParams()).rightMargin = AndroidUtilities.dp(16);

        showSpeakingMembersToast = show;
        invalidate();

        while (speakingIndex < 3) {
            speakingMembersAvatars.setObject(speakingIndex, currenAccount, null);
            speakingIndex++;
        }

        speakingMembersAvatars.commitTransition(animated);
    }

    public UndoView getUndoView() {
        if (undoView[0].getVisibility() == View.VISIBLE) {
            UndoView old = undoView[0];
            undoView[0] = undoView[1];
            undoView[1] = old;
            old.hide(true, 2);
            removeView(undoView[0]);
            addView(undoView[0]);
        }
        return undoView[0];
    }

    public boolean isVisible(TLRPC.TL_groupCallParticipant participant) {
        long peerId = MessageObject.getPeerId(participant.peer);
        return attachedPeerIds.get(peerId) > 0;
    }

    public void attach(GroupCallMiniTextureView view) {
        attachedRenderers.add(view);
        long peerId = MessageObject.getPeerId(view.participant.participant.peer);
        attachedPeerIds.put(peerId, attachedPeerIds.get(peerId, 0) + 1);
    }

    public void detach(GroupCallMiniTextureView view) {
        attachedRenderers.remove(view);
        long peerId = MessageObject.getPeerId(view.participant.participant.peer);
        attachedPeerIds.put(peerId, attachedPeerIds.get(peerId, 0) - 1);
    }

    public void setGroupCall(ChatObject.Call call) {
        this.call = call;
    }
}
