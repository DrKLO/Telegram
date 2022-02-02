package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class ReactionsContainerLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    public final static Property<ReactionsContainerLayout, Float> TRANSITION_PROGRESS_VALUE = new Property<ReactionsContainerLayout, Float>(Float.class, "transitionProgress") {
        @Override
        public Float get(ReactionsContainerLayout reactionsContainerLayout) {
            return reactionsContainerLayout.transitionProgress;
        }

        @Override
        public void set(ReactionsContainerLayout object, Float value) {
            object.setTransitionProgress(value);
        }
    };

    private final static Random RANDOM = new Random();

    private final static int ALPHA_DURATION = 150;
    private final static float SIDE_SCALE = 0.6f;
    private final static float SCALE_PROGRESS = 0.75f;
    private final static float CLIP_PROGRESS = 0.25f;
    public final RecyclerListView recyclerListView;

    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint leftShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
            rightShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float leftAlpha, rightAlpha;
    private float transitionProgress = 1f;
    private RectF rect = new RectF();
    private Path mPath = new Path();
    private float radius = AndroidUtilities.dp(72);
    private float bigCircleRadius = AndroidUtilities.dp(8);
    private float smallCircleRadius = bigCircleRadius / 2;
    private int bigCircleOffset = AndroidUtilities.dp(36);
    private MessageObject messageObject;
    private int currentAccount;
    private long waitingLoadingChatId;
    ValueAnimator cancelPressedAnimation;

    private List<TLRPC.TL_availableReaction> reactionsList = Collections.emptyList();

    private LinearLayoutManager linearLayoutManager;
    private RecyclerView.Adapter listAdapter;

    private int[] location = new int[2];

    private ReactionsContainerDelegate delegate;

    private Rect shadowPad = new Rect();
    private Drawable shadow;
    private final boolean animationEnabled;

    private List<String> triggeredReactions = new ArrayList<>();
    Theme.ResourcesProvider resourcesProvider;
    private String pressedReaction;
    private int pressedReactionPosition;
    private float pressedProgress;
    private float cancelPressedProgress;
    private float pressedViewScale;
    private float otherViewsScale;
    private boolean clicked;

    public ReactionsContainerLayout(@NonNull Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;

        animationEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true) && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW;

        shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow).mutate();
        shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = AndroidUtilities.dp(7);
        shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));

        recyclerListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (pressedReaction != null && ((ReactionHolderView) child).currentReaction.equals(pressedReaction)) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int position = parent.getChildAdapterPosition(view);
                if (position == 0) {
                    outRect.left = AndroidUtilities.dp(6);
                }
                outRect.right = AndroidUtilities.dp(4);
                if (position == listAdapter.getItemCount() - 1) {
                    outRect.right = AndroidUtilities.dp(6);
                }
            }
        });
        recyclerListView.setLayoutManager(linearLayoutManager);
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(listAdapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ReactionHolderView hv = new ReactionHolderView(context);
                int size = getLayoutParams().height - getPaddingTop() - getPaddingBottom();
                hv.setLayoutParams(new RecyclerView.LayoutParams(size - AndroidUtilities.dp(12), size));
                return new RecyclerListView.Holder(hv);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ReactionHolderView h = (ReactionHolderView) holder.itemView;
                h.setScaleX(1);
                h.setScaleY(1);
                h.setReaction(reactionsList.get(position));
            }

            @Override
            public int getItemCount() {
                return reactionsList.size();
            }
        });
        recyclerListView.addOnScrollListener(new LeftRightShadowsListener());
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getChildCount() > 2) {
                    float sideDiff = 1f - SIDE_SCALE;

                    recyclerView.getLocationInWindow(location);
                    int rX = location[0];

                    View ch1 = recyclerView.getChildAt(0);
                    ch1.getLocationInWindow(location);
                    int ch1X = location[0];

                    int dX1 = ch1X - rX;
                    float s1 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX1, 0f) / ch1.getWidth())) * sideDiff;
                    if (Float.isNaN(s1)) s1 = 1f;
                    ((ReactionHolderView) ch1).sideScale = s1;

                    View ch2 = recyclerView.getChildAt(recyclerView.getChildCount() - 1);
                    ch2.getLocationInWindow(location);
                    int ch2X = location[0];

                    int dX2 = rX + recyclerView.getWidth() - (ch2X + ch2.getWidth());
                    float s2 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX2, 0f) / ch2.getWidth())) * sideDiff;
                    if (Float.isNaN(s2)) s2 = 1f;
                    ((ReactionHolderView) ch2).sideScale = s2;
                }
                for (int i = 1; i < recyclerListView.getChildCount() - 1; i++) {
                    View ch = recyclerListView.getChildAt(i);
                    ((ReactionHolderView) ch).sideScale = 1f;
                }
                invalidate();
            }
        });
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int i = parent.getChildAdapterPosition(view);
                if (i == 0)
                    outRect.left = AndroidUtilities.dp(8);
                if (i == listAdapter.getItemCount() - 1)
                    outRect.right = AndroidUtilities.dp(8);
            }
        });
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        invalidateShaders();

        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
    }

    public void setDelegate(ReactionsContainerDelegate delegate) {
        this.delegate = delegate;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setReactionsList(List<TLRPC.TL_availableReaction> reactionsList) {
        this.reactionsList = reactionsList;
        int size = getLayoutParams().height - getPaddingTop() - getPaddingBottom();
        if (size * reactionsList.size() < AndroidUtilities.dp(200)) {
            getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        listAdapter.notifyDataSetChanged();

    }

    HashSet<View> lastVisibleViews = new HashSet<>();
    HashSet<View> lastVisibleViewsTmp = new HashSet<>();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        lastVisibleViewsTmp.clear();
        lastVisibleViewsTmp.addAll(lastVisibleViews);
        lastVisibleViews.clear();

        if (pressedReaction != null) {
            if (pressedProgress != 1f) {
                pressedProgress += 16f / 2000f;
                if (pressedProgress >= 1f) {
                    pressedProgress = 1f;
                }
                invalidate();
            }
        }

        float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;

        pressedViewScale = 1 + 2 * pressedProgress;
        otherViewsScale = 1 - 0.15f * pressedProgress;

        int s = canvas.save();
        float pivotX = LocaleController.isRTL ? getWidth() * 0.125f : getWidth() * 0.875f;

        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        float lt = 0, rt = 1;
        if (LocaleController.isRTL) {
            rt = Math.max(CLIP_PROGRESS, transitionProgress);
        } else {
            lt = (1f - Math.max(CLIP_PROGRESS, transitionProgress));
        }
        rect.set(getPaddingLeft() + (getWidth() - getPaddingRight()) * lt, getPaddingTop() + recyclerListView.getMeasuredHeight() * (1f - otherViewsScale), (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom());
        radius = rect.height() / 2f;
        shadow.setBounds((int) (getPaddingLeft() + (getWidth() - getPaddingRight() + shadowPad.right) * lt - shadowPad.left), getPaddingTop() - shadowPad.top, (int) ((getWidth() - getPaddingRight() + shadowPad.right) * rt), getHeight() - getPaddingBottom() + shadowPad.bottom);
        shadow.draw(canvas);
        canvas.restoreToCount(s);

        s = canvas.save();
        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        canvas.restoreToCount(s);

        mPath.rewind();
        mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        s = canvas.save();
        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        if (transitionProgress != 0) {
            int delay = 0;
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                ReactionHolderView view = (ReactionHolderView) recyclerListView.getChildAt(i);
                checkPressedProgress(canvas, view);
                if (view.backupImageView.getImageReceiver().getLottieAnimation() == null) {
                    continue;
                }
                if (view.getX() + view.getMeasuredWidth() / 2f > 0 && view.getX() + view.getMeasuredWidth() / 2f < recyclerListView.getWidth()) {
                    if (!lastVisibleViewsTmp.contains(view)) {
                        view.play(delay);
                        delay += 30;
                    }
                    lastVisibleViews.add(view);
                } else if (!view.isEnter) {
                    view.resetAnimation();
                }
            }
        }

        canvas.clipPath(mPath);
        canvas.translate((LocaleController.isRTL ? -1 : 1) * getWidth() * (1f - transitionProgress), 0);
        super.dispatchDraw(canvas);

        if (leftShadowPaint != null) {
            leftShadowPaint.setAlpha((int) (leftAlpha * transitionProgress * 0xFF));
            canvas.drawRect(rect, leftShadowPaint);
        }
        if (rightShadowPaint != null) {
            rightShadowPaint.setAlpha((int) (rightAlpha * transitionProgress * 0xFF));
            canvas.drawRect(rect, rightShadowPaint);
        }
        canvas.restoreToCount(s);

        canvas.save();
        canvas.clipRect(0, rect.bottom, getMeasuredWidth(), getMeasuredHeight());
        float cx = LocaleController.isRTL ? bigCircleOffset : getWidth() - bigCircleOffset, cy = getHeight() - getPaddingBottom();
        int sPad = AndroidUtilities.dp(3);
        shadow.setBounds((int) (cx - br - sPad * cPr), (int) (cy - br - sPad * cPr), (int) (cx + br + sPad * cPr), (int) (cy + br + sPad * cPr));
        shadow.draw(canvas);
        canvas.drawCircle(cx, cy, br, bgPaint);

        cx = LocaleController.isRTL ? bigCircleOffset - bigCircleRadius : getWidth() - bigCircleOffset + bigCircleRadius;
        cy = getHeight() - smallCircleRadius - sPad;
        sPad = -AndroidUtilities.dp(1);
        shadow.setBounds((int) (cx - br - sPad * cPr), (int) (cy - br - sPad * cPr), (int) (cx + br + sPad * cPr), (int) (cy + br + sPad * cPr));
        shadow.draw(canvas);
        canvas.drawCircle(cx, cy, sr, bgPaint);
        canvas.restore();
    }

    private void checkPressedProgress(Canvas canvas, ReactionHolderView view) {
        if (view.currentReaction.reaction.equals(pressedReaction)) {
            view.setPivotX(view.getMeasuredWidth() >> 1);
            view.setPivotY(view.backupImageView.getY() + view.backupImageView.getMeasuredHeight());
            view.setScaleX(pressedViewScale);
            view.setScaleY(pressedViewScale);

            if (!clicked) {
                if (cancelPressedAnimation == null) {
                    view.pressedBackupImageView.setVisibility(View.VISIBLE);
                    view.pressedBackupImageView.setAlpha(1f);
                    if (view.pressedBackupImageView.getImageReceiver().hasBitmapImage()) {
                        view.backupImageView.setAlpha(0f);
                    }
                } else {
                    view.pressedBackupImageView.setAlpha(1f - cancelPressedProgress);
                    view.backupImageView.setAlpha(cancelPressedProgress);
                }
                if (pressedProgress == 1f) {
                    clicked = true;
                    delegate.onReactionClicked(view, view.currentReaction, true);
                }
            }

            canvas.save();
            canvas.translate(recyclerListView.getX() + view.getX(), recyclerListView.getY() + view.getY());
            canvas.scale(view.getScaleX(), view.getScaleY(), view.getPivotX(), view.getPivotY());
            view.draw(canvas);
            canvas.restore();
        } else {
            int position = recyclerListView.getChildAdapterPosition(view);
            float translationX;
            translationX = (view.getMeasuredWidth() * (pressedViewScale - 1f)) / 3f - view.getMeasuredWidth() * (1f - otherViewsScale) * (Math.abs(pressedReactionPosition - position) - 1);

            if (position < pressedReactionPosition) {
                view.setPivotX(0);
                view.setTranslationX(-translationX);
            } else {
                view.setPivotX(view.getMeasuredWidth());
                view.setTranslationX(translationX);
            }
            view.setPivotY(view.backupImageView.getY() + view.backupImageView.getMeasuredHeight());
            view.setScaleX(otherViewsScale);
            view.setScaleY(otherViewsScale);
            view.backupImageView.setScaleX(view.sideScale);
            view.backupImageView.setScaleY(view.sideScale);
            view.pressedBackupImageView.setVisibility(View.INVISIBLE);

            view.backupImageView.setAlpha(1f);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateShaders();
    }

    /**
     * Invalidates shaders
     */
    private void invalidateShaders() {
        int dp = AndroidUtilities.dp(24);
        float cy = getHeight() / 2f;
        int clr = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
        leftShadowPaint.setShader(new LinearGradient(0, cy, dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        rightShadowPaint.setShader(new LinearGradient(getWidth(), cy, getWidth() - dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        invalidate();
    }

    public void setTransitionProgress(float transitionProgress) {
        this.transitionProgress = transitionProgress;
        invalidate();
    }

    public void setMessage(MessageObject message, TLRPC.ChatFull chatFull) {
        this.messageObject = message;
        TLRPC.ChatFull reactionsChat = chatFull;
        List<TLRPC.TL_availableReaction> l;
        if (message.isForwardedChannelPost()) {
            reactionsChat = MessagesController.getInstance(currentAccount).getChatFull(-message.getFromChatId());
            if (reactionsChat == null) {
                waitingLoadingChatId = -message.getFromChatId();
                MessagesController.getInstance(currentAccount).loadFullChat(-message.getFromChatId(), 0, true);
                setVisibility(View.INVISIBLE);
                return;
            }
        }
        if (reactionsChat != null) {
            l = new ArrayList<>(reactionsChat.available_reactions.size());
            for (String s : reactionsChat.available_reactions) {
                for (TLRPC.TL_availableReaction a : MediaDataController.getInstance(currentAccount).getEnabledReactionsList()) {
                    if (a.reaction.equals(s)) {
                        l.add(a);
                        break;
                    }
                }
            }
        } else {
            l = MediaDataController.getInstance(currentAccount).getEnabledReactionsList();
        }
        setReactionsList(l);
    }

    public void startEnterAnimation() {
        setTransitionProgress(0);
        setAlpha(1f);
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, ReactionsContainerLayout.TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(400);
        animator.setInterpolator(new OvershootInterpolator(1.004f));
        animator.start();
    }

    public int getTotalWidth() {
        return AndroidUtilities.dp(36) * reactionsList.size() + AndroidUtilities.dp(16);
    }

    public int getItemsCount() {
        return reactionsList.size();
    }

    private final class LeftRightShadowsListener extends RecyclerView.OnScrollListener {
        private boolean leftVisible, rightVisible;
        private ValueAnimator leftAnimator, rightAnimator;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            boolean l = linearLayoutManager.findFirstVisibleItemPosition() != 0;
            if (l != leftVisible) {
                if (leftAnimator != null)
                    leftAnimator.cancel();
                leftAnimator = startAnimator(leftAlpha, l ? 1 : 0, aFloat -> {
                    leftShadowPaint.setAlpha((int) ((leftAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> leftAnimator = null);

                leftVisible = l;
            }

            boolean r = linearLayoutManager.findLastVisibleItemPosition() != listAdapter.getItemCount() - 1;
            if (r != rightVisible) {
                if (rightAnimator != null)
                    rightAnimator.cancel();
                rightAnimator = startAnimator(rightAlpha, r ? 1 : 0, aFloat -> {
                    rightShadowPaint.setAlpha((int) ((rightAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> rightAnimator = null);

                rightVisible = r;
            }
        }

        private ValueAnimator startAnimator(float fromAlpha, float toAlpha, Consumer<Float> callback, Runnable onEnd) {
            ValueAnimator a = ValueAnimator.ofFloat(fromAlpha, toAlpha).setDuration((long) (Math.abs(toAlpha - fromAlpha) * ALPHA_DURATION));
            a.addUpdateListener(animation -> callback.accept((Float) animation.getAnimatedValue()));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
            a.start();
            return a;
        }
    }

    public final class ReactionHolderView extends FrameLayout {
        public BackupImageView backupImageView;
        public BackupImageView pressedBackupImageView;
        public TLRPC.TL_availableReaction currentReaction;
        public float sideScale = 1f;
        private boolean isEnter;

        Runnable playRunnable = new Runnable() {
            @Override
            public void run() {
                if (backupImageView.getImageReceiver().getLottieAnimation() != null && !backupImageView.getImageReceiver().getLottieAnimation().isRunning() && !backupImageView.getImageReceiver().getLottieAnimation().isGeneratingCache()) {
                    backupImageView.getImageReceiver().getLottieAnimation().start();
                }

            }
        };


        ReactionHolderView(Context context) {
            super(context);
            backupImageView = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    ReactionsContainerLayout.this.invalidate();
                }
            };
            backupImageView.getImageReceiver().setAutoRepeat(0);
            backupImageView.getImageReceiver().setAllowStartLottieAnimation(false);

            pressedBackupImageView = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    ReactionsContainerLayout.this.invalidate();
                }
            };
            addView(backupImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            addView(pressedBackupImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
        }

        private void setReaction(TLRPC.TL_availableReaction react) {
            if (currentReaction != null && currentReaction.reaction.equals(react.reaction)) {
                return;
            }
            resetAnimation();
            currentReaction = react;
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(currentReaction.activate_animation, Theme.key_windowBackgroundGray, 1.0f);
            backupImageView.getImageReceiver().setImage(ImageLocation.getForDocument(currentReaction.appear_animation), "60_60_nolimit", null, null, svgThumb, 0, "tgs", react, 0);
            pressedBackupImageView.getImageReceiver().setImage(ImageLocation.getForDocument(currentReaction.select_animation), "60_60_nolimit", null, null, svgThumb, 0, "tgs", react, 0);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            resetAnimation();
        }

        public boolean play(int delay) {
            if (!animationEnabled) {
                resetAnimation();
                isEnter = true;
                return false;
            }
            AndroidUtilities.cancelRunOnUIThread(playRunnable);
            if (backupImageView.getImageReceiver().getLottieAnimation() != null && !backupImageView.getImageReceiver().getLottieAnimation().isGeneratingCache() && !isEnter) {
                isEnter = true;
                if (delay == 0) {
                    backupImageView.getImageReceiver().getLottieAnimation().stop();
                    backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    playRunnable.run();
                } else {
                    backupImageView.getImageReceiver().getLottieAnimation().stop();
                    backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    AndroidUtilities.runOnUIThread(playRunnable, delay);
                }
                return true;
            }
            if (backupImageView.getImageReceiver().getLottieAnimation() != null && isEnter && !backupImageView.getImageReceiver().getLottieAnimation().isRunning() && !backupImageView.getImageReceiver().getLottieAnimation().isGeneratingCache()) {
                backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(backupImageView.getImageReceiver().getLottieAnimation().getFramesCount() - 1, false);
            }
            return false;
        }

        public void resetAnimation() {
            AndroidUtilities.cancelRunOnUIThread(playRunnable);
            if (backupImageView.getImageReceiver().getLottieAnimation() != null && !backupImageView.getImageReceiver().getLottieAnimation().isGeneratingCache()) {
                backupImageView.getImageReceiver().getLottieAnimation().stop();
                if (animationEnabled) {
                    backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false, true);
                } else {
                    backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(backupImageView.getImageReceiver().getLottieAnimation().getFramesCount() - 1, false, true);
                }
            }
            isEnter = false;
        }

        Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                pressedReactionPosition = reactionsList.indexOf(currentReaction);
                pressedReaction = currentReaction.reaction;
                ReactionsContainerLayout.this.invalidate();
            }
        };
        float pressedX, pressedY;
        boolean pressed;
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (cancelPressedAnimation != null) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
                if (sideScale == 1f) {
                    AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
            }
            float touchSlop =  ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2f;
            boolean cancelByMove = event.getAction() == MotionEvent.ACTION_MOVE && (Math.abs(pressedX - event.getX()) > touchSlop || Math.abs(pressedY - event.getY()) > touchSlop);
            if (cancelByMove || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressed && (pressedReaction == null || pressedProgress > 0.8f) && delegate != null) {
                    clicked = true;
                    delegate.onReactionClicked(this, currentReaction,  pressedProgress > 0.8f);
                }
                if (!clicked) {
                    cancelPressed();
                }

                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                pressed = false;
            }
            return true;
        }
    }

    private void cancelPressed() {
        if (pressedReaction != null) {
            cancelPressedProgress = 0f;
            float fromProgress = pressedProgress;
            cancelPressedAnimation = ValueAnimator.ofFloat(0, 1f);
            cancelPressedAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    cancelPressedProgress = (float) valueAnimator.getAnimatedValue();
                    pressedProgress = fromProgress * (1f - cancelPressedProgress);
                    ReactionsContainerLayout.this.invalidate();
                }
            });
            cancelPressedAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    cancelPressedAnimation = null;
                    pressedProgress = 0;
                    pressedReaction = null;
                    ReactionsContainerLayout.this.invalidate();
                }
            });
            cancelPressedAnimation.setDuration(150);
            cancelPressedAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            cancelPressedAnimation.start();
        }
    }

    public interface ReactionsContainerDelegate {
        void onReactionClicked(View v, TLRPC.TL_availableReaction reaction, boolean longpress);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == waitingLoadingChatId && getVisibility() != View.VISIBLE && !chatFull.available_reactions.isEmpty()) {
                setMessage(messageObject, null);
                setVisibility(View.VISIBLE);
                startEnterAnimation();
            }
        }
    }

}