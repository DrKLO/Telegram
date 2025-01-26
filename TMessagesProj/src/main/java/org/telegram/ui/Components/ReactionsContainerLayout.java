package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.PremiumLockIconView;
import org.telegram.ui.Components.Reactions.CustomEmojiReactionsWindow;
import org.telegram.ui.Components.Reactions.HwEmojis;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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

    public final static int TYPE_DEFAULT = 0;
    public final static int TYPE_STORY = 1;
    public static final int TYPE_STORY_LIKES = 2;
    public static final int TYPE_TAGS = 3;
    public static final int TYPE_STICKER_SET_EMOJI = 4;
    public final static int TYPE_MESSAGE_EFFECTS = 5;

    private final static int ALPHA_DURATION = 150;
    private final static float SIDE_SCALE = 0.6f;
    private final static float CLIP_PROGRESS = 0.25f;
    public final RecyclerListView recyclerListView;
    public final float durationScale;

    private static final int VIEW_TYPE_REACTION = 0;
    private static final int VIEW_TYPE_PREMIUM_BUTTON = 1;
    private static final int VIEW_TYPE_CUSTOM_EMOJI_BUTTON = 2;
    private static final int VIEW_TYPE_CUSTOM_REACTION = 3;

    public ArrayList<InnerItem> items = new ArrayList<>();
    public ArrayList<InnerItem> oldItems = new ArrayList<>();

    class InnerItem extends AdapterWithDiffUtils.Item {
        ReactionsLayoutInBubble.VisibleReaction reaction;

        public InnerItem(int viewType, ReactionsLayoutInBubble.VisibleReaction reaction) {
            super(viewType, false);
            this.reaction = reaction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InnerItem innerItem = (InnerItem) o;
            if (viewType == innerItem.viewType && (viewType == VIEW_TYPE_REACTION || viewType == VIEW_TYPE_CUSTOM_REACTION)) {
                return reaction != null && reaction.equals(innerItem.reaction);
            }
            return viewType == innerItem.viewType;
        }
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
            rightShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float leftAlpha, rightAlpha;
    private float transitionProgress = 1f;
    public RectF rect = new RectF();
    private Path mPath = new Path();
    public float radius = dp(72);
    private float bigCircleRadius = dp(8);
    private float smallCircleRadius = bigCircleRadius / 2;
    public int bigCircleOffset = dp(36);
    public MessageObject messageObject;
    public boolean hitLimit;
    private int currentAccount;
    private long waitingLoadingChatId;
    private boolean isTop;

    private boolean mirrorX;
    private boolean isFlippedVertically;
    private float flipVerticalProgress;
    private long lastUpdate;

    ValueAnimator cancelPressedAnimation;
    FrameLayout premiumLockContainer;
    FrameLayout customReactionsContainer;

    private List<ReactionsLayoutInBubble.VisibleReaction> visibleReactionsList = new ArrayList<>(20);
    private List<TLRPC.TL_availableReaction> premiumLockedReactions = new ArrayList<>(10);
    public List<ReactionsLayoutInBubble.VisibleReaction> allReactionsList = new ArrayList<>(20);

    private LinearLayoutManager linearLayoutManager;
    private Adapter listAdapter;
    RectF rectF = new RectF();

    private boolean hasStar = false;
    final HashSet<ReactionsLayoutInBubble.VisibleReaction> selectedReactions = new HashSet<>();
    final HashSet<ReactionsLayoutInBubble.VisibleReaction> alwaysSelectedReactions = new HashSet<>();

    private int[] location = new int[2];

    private ReactionsContainerDelegate delegate;

    private Rect shadowPad = new Rect();
    private Drawable shadow;
    private final boolean animationEnabled;

    private List<String> triggeredReactions = new ArrayList<>();
    Theme.ResourcesProvider resourcesProvider;
    private ReactionsLayoutInBubble.VisibleReaction pressedReaction;
    private int pressedReactionPosition;
    private float pressedProgress;
    private float cancelPressedProgress;
    private float pressedViewScale;
    private float otherViewsScale;
    private boolean clicked;
    long lastReactionSentTime;
    BaseFragment fragment;
    private PremiumLockIconView premiumLockIconView;
    private InternalImageView customEmojiReactionsIconView;
    private float customEmojiReactionsEnterProgress;
    CustomEmojiReactionsWindow reactionsWindow;
    ValueAnimator pullingDownBackAnimator;

    public ReactionHolderView nextRecentReaction;

    public boolean channelReactions;

    float pullingLeftOffset;

    HashSet<View> lastVisibleViews = new HashSet<>();
    HashSet<View> lastVisibleViewsTmp = new HashSet<>();
    private boolean allReactionsAvailable;
    private boolean showExpandableReactions;
    private boolean allReactionsIsDefault;
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ChatScrimPopupContainerLayout parentLayout;
    private boolean animatePopup;
    public final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private final int type;
    public boolean skipEnterAnimation;
    public boolean isHiddenNextReaction = true;
    private Runnable onSwitchedToLoopView;
    public boolean hasHint;
    public TextView hintView;
    public int hintViewWidth, hintViewHeight;
    public float bubblesOffset;
    private float miniBubblesOffset;

    public ReactionsContainerLayout(int type, BaseFragment fragment, @NonNull Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.type = type;
        durationScale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        selectedPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        starSelectedPaint.setColor(Theme.getColor(Theme.key_reactionStarSelector, resourcesProvider));
        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;
        this.fragment = fragment;

        nextRecentReaction = new ReactionHolderView(context, false);
        nextRecentReaction.setVisibility(View.GONE);
        nextRecentReaction.touchable = false;
        nextRecentReaction.pressedBackupImageView.setVisibility(View.GONE);

        addView(nextRecentReaction);

        animationEnabled = SharedConfig.animationsEnabled() && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW;

        shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow).mutate();
        shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = dp(7);
        shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));

        recyclerListView = new RecyclerListView(context) {

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (pressedReaction != null && (child instanceof ReactionHolderView) && ((ReactionHolderView) child).currentReaction.equals(pressedReaction)) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (ev.getAction() == MotionEvent.ACTION_UP && getPullingLeftProgress() > 0.95f) {
                        showCustomEmojiReactionDialog();
                    } else {
                        animatePullingBack();
                    }
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        recyclerListView.setClipChildren(false);
        recyclerListView.setClipToPadding(false);
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {

            @Override
            public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (dx < 0 && pullingLeftOffset != 0) {
                    float oldProgress = getPullingLeftProgress();
                    pullingLeftOffset += dx;
                    float newProgress = getPullingLeftProgress();
                    boolean b1 = oldProgress > 1f;
                    boolean b2 = newProgress > 1f;
                    if (b1 != b2) {
                        try {
                            recyclerListView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        } catch (Exception ignore) {}
                    }
                    if (pullingLeftOffset < 0) {
                        dx = (int) pullingLeftOffset;
                        pullingLeftOffset = 0;
                    } else {
                        dx = 0;
                    }
                    if (customReactionsContainer != null) {
                        customReactionsContainer.invalidate();
                    }
                    recyclerListView.invalidate();
                }
                int scrolled = super.scrollHorizontallyBy(dx, recycler, state);
                if (dx > 0 && scrolled == 0 && recyclerListView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING && showCustomEmojiReaction()) {
                    if (pullingDownBackAnimator != null) {
                        pullingDownBackAnimator.removeAllListeners();
                        pullingDownBackAnimator.cancel();
                    }

                    float oldProgress = getPullingLeftProgress();
                    float k = 0.6f;
                    if (oldProgress > 1f) {
                        k = 0.05f;
                    }

                    pullingLeftOffset += dx * k;
                    float newProgress = getPullingLeftProgress();
                    boolean b1 = oldProgress > 1f;
                    boolean b2 = newProgress > 1f;
                    if (b1 != b2) {
                        try {
                            recyclerListView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        } catch (Exception ignore) {}
                    }
                    if (customReactionsContainer != null) {
                        customReactionsContainer.invalidate();
                    }
                    recyclerListView.invalidate();
                }
                return scrolled;
            }
        };
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                if (!showCustomEmojiReaction()) {
                    int position = parent.getChildAdapterPosition(view);
                    if (position == 0) {
                        outRect.left = dp(6);
                    }
                    outRect.right = dp(4);
                    if (position == listAdapter.getItemCount() - 1) {
                        if (showUnlockPremiumButton() || showCustomEmojiReaction()) {
                            outRect.right = dp(2);
                        } else {
                            outRect.right = dp(6);
                        }
                    }
                } else {
                    outRect.right = outRect.left = 0;
                }

            }
        });
        recyclerListView.setLayoutManager(linearLayoutManager);
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(listAdapter = new Adapter());
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
                    setChildScale(ch1, s1);

                    View ch2 = recyclerView.getChildAt(recyclerView.getChildCount() - 1);
                    ch2.getLocationInWindow(location);
                    int ch2X = location[0];

                    int dX2 = rX + recyclerView.getWidth() - (ch2X + ch2.getWidth());
                    float s2 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX2, 0f) / ch2.getWidth())) * sideDiff;
                    if (Float.isNaN(s2)) s2 = 1f;
                    setChildScale(ch2, s2);
                }
                for (int i = 1; i < recyclerListView.getChildCount() - 1; i++) {
                    View ch = recyclerListView.getChildAt(i);
                    setChildScale(ch, 1f);
                }
                invalidate();
            }
        });
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int i = parent.getChildAdapterPosition(view);
                if (i == 0)
                    outRect.left = dp(8);
                if (i == listAdapter.getItemCount() - 1) {
                    outRect.right = dp(8);
                }
            }
        });
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (delegate != null && view instanceof ReactionHolderView) {
                ReactionHolderView reactionHolderView = (ReactionHolderView) view;
                delegate.onReactionClicked(this, reactionHolderView.currentReaction, false, false);
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position) -> {
            if (type == TYPE_MESSAGE_EFFECTS) {
                return false;
            }
            if (delegate != null && view instanceof ReactionHolderView) {
                ReactionHolderView reactionHolderView = (ReactionHolderView) view;
                delegate.onReactionClicked(this, reactionHolderView.currentReaction, true, false);
                return true;
            }
            return false;
        });
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setClipChildren(false);
        setClipToPadding(false);
        invalidateShaders();

        int size = recyclerListView.getLayoutParams().height - recyclerListView.getPaddingTop() - recyclerListView.getPaddingBottom();
        nextRecentReaction.getLayoutParams().width = size - dp(12);
        nextRecentReaction.getLayoutParams().height = size;

        if (type == TYPE_STORY_LIKES || type == TYPE_STICKER_SET_EMOJI) {
            bgPaint.setColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.13f));
        } else {
            bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        }
        MediaDataController.getInstance(currentAccount).preloadDefaultReactions();
    }

    public boolean showExpandableReactions() {
        return showExpandableReactions;
    }

    public List<ReactionsLayoutInBubble.VisibleReaction> getVisibleReactionsList() {
        return visibleReactionsList;
    }

    public int getWindowType() {
        if (type == TYPE_STICKER_SET_EMOJI) {
            return SelectAnimatedEmojiDialog.TYPE_STICKER_SET_EMOJI;
        }
        if (type == TYPE_TAGS) {
            return SelectAnimatedEmojiDialog.TYPE_TAGS;
        }
        if (type == TYPE_MESSAGE_EFFECTS) {
            return SelectAnimatedEmojiDialog.TYPE_EFFECTS;
        }
        if (showExpandableReactions) {
            return SelectAnimatedEmojiDialog.TYPE_EXPANDABLE_REACTIONS;
        }
        return SelectAnimatedEmojiDialog.TYPE_REACTIONS;
    }

    private void animatePullingBack() {
        if (pullingLeftOffset != 0) {
            if (pullingDownBackAnimator != null) {
                pullingDownBackAnimator.cancel();
            }
            pullingDownBackAnimator = ValueAnimator.ofFloat(pullingLeftOffset, 0);
            pullingDownBackAnimator.addUpdateListener(animation -> {
                pullingLeftOffset = (float) pullingDownBackAnimator.getAnimatedValue();
                if (customReactionsContainer != null) {
                    customReactionsContainer.invalidate();
                }
                invalidate();
            });
            pullingDownBackAnimator.setDuration(150);
            pullingDownBackAnimator.start();
        }
    }

    public void setOnSwitchedToLoopView(Runnable onSwitchedToLoopView) {
        this.onSwitchedToLoopView = onSwitchedToLoopView;
    }

    public void dismissWindow() {
        if (reactionsWindow != null) {
            reactionsWindow.dismiss();
        }
    }

    public CustomEmojiReactionsWindow getReactionsWindow() {
        return reactionsWindow;
    }

    private void showCustomEmojiReactionDialog() {
        if (reactionsWindow != null) {
            return;
        }
        reactionsWindow = new CustomEmojiReactionsWindow(type, fragment, allReactionsList, selectedReactions, this, resourcesProvider);
        invalidateLoopViews();
        reactionsWindow.onDismissListener(() -> {
            reactionsWindow = null;
            invalidateLoopViews();
            if (delegate != null) {
                delegate.onEmojiWindowDismissed();
            }
        });
        onShownCustomEmojiReactionDialog();
        //animatePullingBack();
    }

    protected void onShownCustomEmojiReactionDialog() {

    }

    public void invalidateLoopViews() {
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof ReactionHolderView) {
                ((ReactionHolderView) child).loopImageView.invalidate();
            }
        }
    }

    public boolean showCustomEmojiReaction() {
        return allReactionsAvailable || showExpandableReactions;
    }

    private boolean showUnlockPremiumButton() {
        return !premiumLockedReactions.isEmpty() && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked();
    }

    private void showUnlockPremium(float x, float y) {
        PremiumFeatureBottomSheet bottomSheet = new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS, true);
        bottomSheet.show();
    }

    private void setChildScale(View child, float scale) {
        if (child instanceof ReactionHolderView) {
            ((ReactionHolderView) child).sideScale = scale;
        } else {
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }

    public void setDelegate(ReactionsContainerDelegate delegate) {
        this.delegate = delegate;
    }

    public boolean isFlippedVertically() {
        return isFlippedVertically;
    }

    public void setFlippedVertically(boolean flippedVertically) {
        isFlippedVertically = flippedVertically;
        invalidate();
    }

    public void setMirrorX(boolean mirrorX) {
        this.mirrorX = mirrorX;
        invalidate();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setVisibleReactionsList(List<ReactionsLayoutInBubble.VisibleReaction> visibleReactionsList, boolean animated) {
        this.visibleReactionsList.clear();
        if (showCustomEmojiReaction()) {
            int i = 0;
            int n = (AndroidUtilities.displaySize.x - dp(36)) / dp(34);
            if (n > 7) {
                n = 7;
            }
            if (n < 1) {
                n = 1;
            }
            for (; i < Math.min(visibleReactionsList.size(), n); i++) {
                this.visibleReactionsList.add(visibleReactionsList.get(i));
            }
            if (i < visibleReactionsList.size()) {
                nextRecentReaction.setReaction(visibleReactionsList.get(i), -1);
            }
        } else {
            this.visibleReactionsList.addAll(visibleReactionsList);
        }
        allReactionsIsDefault = true;
        for (int i = 0; i < this.visibleReactionsList.size(); i++) {
            if (this.visibleReactionsList.get(i).documentId != 0) {
                allReactionsIsDefault = false;
            }
        }
        allReactionsList.clear();
        allReactionsList.addAll(visibleReactionsList);
        // checkPremiumReactions(this.visibleReactionsList);
        int size = getLayoutParams().height - (int) getTopOffset() - getPaddingTop() - getPaddingBottom();
        if (size * visibleReactionsList.size() < dp(200)) {
            getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        listAdapter.updateItems(animated);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        long dt = Math.min(16, System.currentTimeMillis() - lastUpdate);
        lastUpdate = System.currentTimeMillis();

        if (isFlippedVertically && flipVerticalProgress != 1f) {
            flipVerticalProgress = Math.min(1f, flipVerticalProgress + dt / 220f);
            invalidate();
        } else if (!isFlippedVertically && flipVerticalProgress != 0f) {
            flipVerticalProgress = Math.max(0f, flipVerticalProgress - dt / 220f);
            invalidate();
        }

        if (hintView != null) {
            hintView.setTranslationY(-expandSize());
        }

        float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;

        lastVisibleViewsTmp.clear();
        lastVisibleViewsTmp.addAll(lastVisibleViews);
        lastVisibleViews.clear();

        if (prepareAnimation) {
            invalidate();
        }

        if (pressedReaction != null && type != TYPE_MESSAGE_EFFECTS) {
            if (pressedProgress != 1f) {
                pressedProgress += 16f / (pressedReaction.isStar ? ViewConfiguration.getLongPressTimeout() : 1500f);
                if (pressedProgress >= 1f) {
                    pressedProgress = 1f;
                }
                invalidate();
            }
        }


        if (pressedReaction != null && pressedReaction.isStar) {
            pressedViewScale = 1f;
            otherViewsScale = 1f;
        } else {
            pressedViewScale = 1 + 2 * pressedProgress;
            otherViewsScale = 1 - 0.15f * pressedProgress;
        }

        int s = canvas.save();
        float pivotX = LocaleController.isRTL || mirrorX ? getWidth() * 0.125f : getWidth() * 0.875f;

        if (transitionProgress != 1f) {
            float sc = transitionProgress;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        float lt = 0, rt = 1;
        if (LocaleController.isRTL || mirrorX) {
            rt = Math.max(CLIP_PROGRESS, transitionProgress);
        } else {
            lt = (1f - Math.max(CLIP_PROGRESS, transitionProgress));
        }
        float pullingLeftOffsetProgress = getPullingLeftProgress();
        float expandSize = expandSize();
        if (chatScrimPopupContainerLayout != null) {
            chatScrimPopupContainerLayout.setExpandSize(expandSize);
        }
        float transitionLeftOffset = (getWidth() - getPaddingRight()) * Math.min(1f, lt);
        float hintHeight = getTopOffset();
        rect.set(getPaddingLeft() + transitionLeftOffset, getPaddingTop() + recyclerListView.getMeasuredHeight() * (1f - otherViewsScale) - expandSize, (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom() + expandSize);
        radius = ((rect.height() - hintHeight) - expandSize * 2f) / 2f;

        if (type != TYPE_STORY) {
            shadow.setAlpha((int) (Utilities.clamp(1f - (customEmojiReactionsEnterProgress / 0.05f), 1f, 0f) * 255));
            shadow.setBounds((int) (getPaddingLeft() + (getWidth() - getPaddingRight() + shadowPad.right) * lt - shadowPad.left), getPaddingTop() - shadowPad.top - (int) expandSize, (int) ((getWidth() - getPaddingRight() + shadowPad.right) * rt), getHeight() - getPaddingBottom() + shadowPad.bottom + (int) expandSize);
            shadow.draw(canvas);
        }

        canvas.restoreToCount(s);

        if (!skipDraw) {
            s = canvas.save();
            if (transitionProgress != 1f) {
                float sc = transitionProgress;
                canvas.scale(sc, sc, pivotX, getHeight() / 2f);
            }
            if (type == TYPE_STORY || delegate.drawBackground()) {
                delegate.drawRoundRect(canvas, rect, radius, getX(), getY(), 255, false);
            } else {
                canvas.drawRoundRect(rect, radius, radius, bgPaint);
            }

            if (hasStar) {
                boolean isStarSelected = false;
                for (ReactionsLayoutInBubble.VisibleReaction r : selectedReactions) {
                    if (r.isStar) {
                        isStarSelected = true;
                        break;
                    }
                }
                if (!isStarSelected) {
                    canvas.drawRoundRect(rect, radius, radius, getStarGradientPaint(rect, Utilities.clamp01(1f - getPullingLeftProgress())));
                }
            }

            canvas.restoreToCount(s);
        }

        mPath.rewind();
        mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        s = canvas.save();
        if (transitionProgress != 1f) {
            float sc = transitionProgress;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        if (transitionProgress != 0 && (getAlpha() == 1f || type == TYPE_MESSAGE_EFFECTS)) {
            int delay = 0;
            int lastReactionX = 0;
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                View child = recyclerListView.getChildAt(i);
                if (transitionProgress != 1f && allowSmoothEnterTransition()) {
                    float childCenterX = child.getLeft() + child.getMeasuredWidth() / 2f;
                    delay = (int) (200 * ((Math.abs(childCenterX / (float) recyclerListView.getMeasuredWidth() - 0.8f))));
                }
                if (child instanceof ReactionHolderView) {
                    ReactionHolderView view = (ReactionHolderView) recyclerListView.getChildAt(i);
                    checkPressedProgress(canvas, view);
                    if (child.getLeft() > lastReactionX) {
                        lastReactionX = child.getLeft();
                    }
                    if (skipEnterAnimation || (view.hasEnterAnimation && view.enterImageView.getImageReceiver().getLottieAnimation() == null)) {
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
                } else {
                    if (child == premiumLockContainer) {
                        if (child.getX() + child.getMeasuredWidth() / 2f > 0 && child.getX() + child.getMeasuredWidth() / 2f < recyclerListView.getWidth()) {
                            if (!lastVisibleViewsTmp.contains(child)) {
                                if (transitionProgress != 1f) {
                                    premiumLockIconView.resetAnimation();
                                }
                                premiumLockIconView.play(delay);
                                delay += 30;
                            }
                            lastVisibleViews.add(child);
                        } else {
                            premiumLockIconView.resetAnimation();
                        }
                    }
                    if (child == customReactionsContainer) {
                        if (child.getX() + child.getMeasuredWidth() / 2f > 0 && child.getX() + child.getMeasuredWidth() / 2f < recyclerListView.getWidth()) {
                            if (!lastVisibleViewsTmp.contains(child)) {
                                if (transitionProgress != 1f) {
                                    customEmojiReactionsIconView.resetAnimation();
                                }
                                customEmojiReactionsIconView.play(delay, LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS) || SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE);
                                delay += 30;
                            }
                            lastVisibleViews.add(child);
                        } else {
                            customEmojiReactionsIconView.resetAnimation();
                        }
                    }
                    checkPressedProgressForOtherViews(child);
                }
            }
            if (pullingLeftOffsetProgress > 0) {
                float progress = getPullingLeftProgress();
                int reactionSize = nextRecentReaction.getMeasuredWidth() - dp(2);
                int left = lastReactionX + reactionSize;
                float leftProgress = Utilities.clamp(left / (float) (getMeasuredWidth() - nextRecentReaction.getMeasuredWidth()), 1f, 0f);
                float pullingOffsetX = leftProgress * progress * reactionSize;

                if (nextRecentReaction.getTag() == null) {
                    nextRecentReaction.setTag(1f);
                    nextRecentReaction.resetAnimation();
                    nextRecentReaction.play(0);
                }
                float scale = Utilities.clamp(progress, 1f, 0f);
                nextRecentReaction.setScaleX(scale);
                nextRecentReaction.setScaleY(scale);
                float additionalOffset = 0;
                if (type != TYPE_STORY && type != TYPE_STORY_LIKES) {
                    additionalOffset = - dp(20);
                } else {
                    additionalOffset = - dp(8);
                }
                nextRecentReaction.setTranslationX(recyclerListView.getX() + left - pullingOffsetX + additionalOffset);
                if (nextRecentReaction.getVisibility() != View.VISIBLE) {
                    nextRecentReaction.setVisibility(View.VISIBLE);
                }
            } else {
                if (nextRecentReaction.getVisibility() != View.GONE && isHiddenNextReaction) {
                    nextRecentReaction.setVisibility(View.GONE);
                }
                if (nextRecentReaction.getTag() != null) {
                    nextRecentReaction.setTag(null);
                }
            }
        }

        if (skipDraw && reactionsWindow != null) {
            int alpha = (int) (Utilities.clamp(1f - (customEmojiReactionsEnterProgress / 0.2f), 1f, 0f) * (1f - customEmojiReactionsEnterProgress) * 255);
            canvas.save();
            drawBubbles(canvas, br, cPr, sr, alpha);
            canvas.restore();
            return;
        }

        boolean showCustomEmojiReaction = showCustomEmojiReaction();
        if (!showCustomEmojiReaction) {
            canvas.clipPath(mPath);
        }
        canvas.translate((LocaleController.isRTL || mirrorX ? -1 : 1) * getWidth() * (1f - transitionProgress), 0);
        recyclerListView.setTranslationX(-transitionLeftOffset);
        super.dispatchDraw(canvas);

        if (!showCustomEmojiReaction) {
            if (leftShadowPaint != null) {
                float p = Utilities.clamp(leftAlpha * transitionProgress, 1f, 0f);
                leftShadowPaint.setAlpha((int) (p * 0xFF));
                canvas.drawRect(rect, leftShadowPaint);
            }
            if (rightShadowPaint != null) {
                float p = Utilities.clamp(rightAlpha * transitionProgress, 1f, 0f);
                rightShadowPaint.setAlpha((int) (p * 0xFF));
                canvas.drawRect(rect, rightShadowPaint);
            }
        }
        canvas.restoreToCount(s);

        drawBubbles(canvas, br, cPr, sr, 255);
        invalidate();
    }

    public void drawBubbles(Canvas canvas) {
        float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;
        int alpha = type == TYPE_MESSAGE_EFFECTS ? 0xFF : (int) (Utilities.clamp((customEmojiReactionsEnterProgress / 0.2f), 1f, 0f) * (1f - customEmojiReactionsEnterProgress) * 255);
        drawBubbles(canvas, br, cPr, sr, alpha);
    }

    private void drawBubbles(Canvas canvas, float br, float cPr, float sr, int alpha) {
        if (type == TYPE_STORY) {
            return;
        }
        canvas.save();
        if (isTop) {
            canvas.clipRect(0, 0, getMeasuredWidth(), AndroidUtilities.lerp(rect.top, getMeasuredHeight(), CubicBezierInterpolator.DEFAULT.getInterpolation(flipVerticalProgress)) - (int) Math.ceil(rect.height() / 2f * (1f - transitionProgress)) + 1);
        } else {
            canvas.clipRect(0, AndroidUtilities.lerp(rect.bottom, 0, CubicBezierInterpolator.DEFAULT.getInterpolation(flipVerticalProgress)) - (int) Math.ceil(rect.height() / 2f * (1f - transitionProgress)) - 1, getMeasuredWidth(), AndroidUtilities.lerp(getMeasuredHeight() + dp(8), getPaddingTop() - expandSize(), CubicBezierInterpolator.DEFAULT.getInterpolation(flipVerticalProgress)));
        }
        float cx = LocaleController.isRTL || mirrorX ? bigCircleOffset : getWidth() - bigCircleOffset;
        cx += bubblesOffset;
        float cy = isTop ? getPaddingTop() - expandSize() : getHeight() - getPaddingBottom() + expandSize();
        int sPad = dp(3);
        shadow.setAlpha(alpha);
        bgPaint.setAlpha(alpha);
        shadow.setBounds((int) (cx - br - sPad * cPr), (int) (cy - br - sPad * cPr), (int) (cx + br + sPad * cPr), (int) (cy + br + sPad * cPr));
        shadow.draw(canvas);
        if (delegate.drawBackground()) {
            rectF.set(cx - br, cy - br, cx + br, cy + br);
            delegate.drawRoundRect(canvas, rectF, br, getX(), getY(), alpha, false);
        } else {
            canvas.drawCircle(cx, cy, br, bgPaint);
        }

        cx = LocaleController.isRTL || mirrorX ? bigCircleOffset - bigCircleRadius : getWidth() - bigCircleOffset + bigCircleRadius;
        cx += bubblesOffset + miniBubblesOffset;
        cy = isTop ? getPaddingTop() - expandSize() - dp(16) : getHeight() - smallCircleRadius - sPad + expandSize();
        cy = AndroidUtilities.lerp(cy, smallCircleRadius + sPad - expandSize(), CubicBezierInterpolator.DEFAULT.getInterpolation(flipVerticalProgress));
        sPad = -dp(1);
        shadow.setBounds((int) (cx - br - sPad * cPr), (int) (cy - br - sPad * cPr), (int) (cx + br + sPad * cPr), (int) (cy + br + sPad * cPr));
        shadow.draw(canvas);
        if (delegate.drawBackground()) {
            rectF.set(cx - sr, cy - sr, cx + sr, cy + sr);
            delegate.drawRoundRect(canvas, rectF, sr, getX(), getY(), alpha, false);
        } else {
            canvas.drawCircle(cx, cy, sr, bgPaint);
        }
        canvas.restore();

        shadow.setAlpha(255);
        bgPaint.setAlpha(255);
    }

    public void setMiniBubblesOffset(float miniBubblesOffset) {
        this.miniBubblesOffset = miniBubblesOffset;
    }

    private void checkPressedProgressForOtherViews(View view) {
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
        view.setScaleX(otherViewsScale);
        view.setScaleY(otherViewsScale);
    }

    private void invalidateEmojis() {
        if (type != TYPE_STICKER_SET_EMOJI) return;
        invalidate();
        recyclerListView.invalidate();
        recyclerListView.invalidateViews();
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof ReactionHolderView) {
                ((ReactionHolderView) child).enterImageView.invalidate();
                ((ReactionHolderView) child).loopImageView.invalidate();
            } else {
                child.invalidate();
            }
        }
    }

    private void checkPressedProgress(Canvas canvas, ReactionHolderView view) {
        float pullingOffsetX = 0;
        if (pullingLeftOffset != 0) {
            float progress = getPullingLeftProgress();
            float leftProgress = Utilities.clamp(view.getLeft() / (float) (getMeasuredWidth() - dp(34)), 1f, 0f);
            pullingOffsetX = leftProgress * progress * dp(46);
        }
        if (view.currentReaction.equals(pressedReaction)) {
            View imageView = view.loopImageView.getVisibility() == View.VISIBLE ? view.loopImageView : view.enterImageView;
            view.setPivotX(view.getMeasuredWidth() >> 1);
            view.setPivotY(imageView.getY() + imageView.getMeasuredHeight());
            view.setScaleX(pressedViewScale);
            view.setScaleY(pressedViewScale);

            if (!clicked) {
                if (cancelPressedAnimation == null) {
                    view.pressedBackupImageView.setVisibility(View.VISIBLE);
                    view.pressedBackupImageView.setAlpha(1f);
                    if (view.pressedBackupImageView.getImageReceiver().hasBitmapImage() || (view.pressedBackupImageView.animatedEmojiDrawable != null && view.pressedBackupImageView.animatedEmojiDrawable.getImageReceiver() != null && view.pressedBackupImageView.animatedEmojiDrawable.getImageReceiver().hasBitmapImage())) {
                        imageView.setAlpha(0f);
                    }
                } else {
                    view.pressedBackupImageView.setAlpha(1f - cancelPressedProgress);
                    imageView.setAlpha(cancelPressedProgress);
                }
                if (pressedProgress == 1f) {
                    clicked = true;
                    if (System.currentTimeMillis() - lastReactionSentTime > 300) {
                        lastReactionSentTime = System.currentTimeMillis();
                        delegate.onReactionClicked(view, view.currentReaction, true, false);
                    }
                }
            }

            canvas.save();
            float x = recyclerListView.getX() + view.getX();
            float additionalWidth = (view.getMeasuredWidth() * view.getScaleX() - view.getMeasuredWidth()) / 2f;
            if (x - additionalWidth < 0 && view.getTranslationX() >= 0) {
                view.setTranslationX(-(x - additionalWidth) - pullingOffsetX);
            } else if (x + view.getMeasuredWidth() + additionalWidth > getMeasuredWidth() && view.getTranslationX() <= 0) {
                view.setTranslationX(getMeasuredWidth() - x - view.getMeasuredWidth() - additionalWidth - pullingOffsetX);
            } else {
                view.setTranslationX(0 - pullingOffsetX);
            }
            x = recyclerListView.getX() + view.getX();
            canvas.translate(x, recyclerListView.getY() + view.getY());
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
                view.setPivotX(view.getMeasuredWidth() - pullingOffsetX);
                view.setTranslationX(translationX - pullingOffsetX);
            }
            view.setPivotY(view.enterImageView.getY() + view.enterImageView.getMeasuredHeight());
            view.setScaleX(otherViewsScale);
            view.setScaleY(otherViewsScale);
//            view.enterImageView.setScaleX(view.sideScale);
//            view.enterImageView.setScaleY(view.sideScale);
            view.pressedBackupImageView.setVisibility(View.INVISIBLE);

            view.enterImageView.setAlpha(1f);
        }
    }

    public float getPullingLeftProgress() {
        return Utilities.clamp(pullingLeftOffset / dp(42), 2f, 0f);
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
        int dp = dp(24);
        float cy = getHeight() / 2f;
        int clr = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
        leftShadowPaint.setShader(new LinearGradient(0, cy, dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        rightShadowPaint.setShader(new LinearGradient(getWidth(), cy, getWidth() - dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        invalidate();
    }

    public void setTransitionProgress(float transitionProgress) {
        this.transitionProgress = transitionProgress;
        if (parentLayout != null) {
            parentLayout.setReactionsTransitionProgress(animatePopup && allowSmoothEnterTransition() ? transitionProgress : 1);
        }
        invalidate();
    }

    public void setMessage(MessageObject message, TLRPC.ChatFull chatFull, boolean animated) {
        this.messageObject = message;
        int chosenCount = 0;
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.reactions != null) {
            for (TLRPC.ReactionCount reactionCount : messageObject.messageOwner.reactions.results) {
                if (!(reactionCount.reaction instanceof TLRPC.TL_reactionPaid)) {
                    chosenCount++;
                }
            }
        }
        hitLimit = type == TYPE_DEFAULT && messageObject != null && chosenCount >= MessagesController.getInstance(currentAccount).getChatMaxUniqReactions(messageObject.getDialogId());
        channelReactions = type == TYPE_DEFAULT && messageObject != null && ChatObject.isChannelAndNotMegaGroup(MessagesController.getInstance(currentAccount).getChat(-messageObject.getDialogId()));
        TLRPC.ChatFull reactionsChat = chatFull;
        List<ReactionsLayoutInBubble.VisibleReaction> visibleReactions = new ArrayList<>();
        if (message != null && message.isForwardedChannelPost()) {
            reactionsChat = MessagesController.getInstance(currentAccount).getChatFull(-message.getFromChatId());
            if (reactionsChat == null) {
                waitingLoadingChatId = -message.getFromChatId();
                MessagesController.getInstance(currentAccount).loadFullChat(-message.getFromChatId(), 0, true);
                setVisibility(View.INVISIBLE);
                return;
            }
        }
        hasStar = false;
        if (type == TYPE_TAGS) {
            allReactionsAvailable = UserConfig.getInstance(currentAccount).isPremium();
            fillRecentReactionsList(visibleReactions);
        } else if (type == TYPE_MESSAGE_EFFECTS) {
            allReactionsAvailable = true;
            fillRecentReactionsList(visibleReactions);
        } else if (hitLimit) {
            allReactionsAvailable = false;
            if (reactionsChat != null && reactionsChat.paid_reactions_available) {
                hasStar = true;
                visibleReactions.add(ReactionsLayoutInBubble.VisibleReaction.asStar());
            }
            for (TLRPC.ReactionCount result : messageObject.messageOwner.reactions.results) {
                visibleReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromTL(result.reaction));
            }
        } else if (reactionsChat != null) {
            if (reactionsChat != null && reactionsChat.paid_reactions_available) {
                hasStar = true;
                visibleReactions.add(ReactionsLayoutInBubble.VisibleReaction.asStar());
            }
            if (reactionsChat.available_reactions instanceof TLRPC.TL_chatReactionsAll) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(reactionsChat.id);
                if (chat != null && !ChatObject.isChannelAndNotMegaGroup(chat)) {
                    allReactionsAvailable = true;
                } else {
                    allReactionsAvailable = false;
                }
                fillRecentReactionsList(visibleReactions);
            } else if (reactionsChat.available_reactions instanceof TLRPC.TL_chatReactionsSome) {
                TLRPC.TL_chatReactionsSome reactionsSome = (TLRPC.TL_chatReactionsSome) reactionsChat.available_reactions;
                for (TLRPC.Reaction s : reactionsSome.reactions) {
                    for (TLRPC.TL_availableReaction a : MediaDataController.getInstance(currentAccount).getEnabledReactionsList()) {
                        if (s instanceof TLRPC.TL_reactionEmoji && a.reaction.equals(((TLRPC.TL_reactionEmoji) s).emoticon)) {
                            visibleReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromTL(s));
                            break;
                        } else if (s instanceof TLRPC.TL_reactionCustomEmoji) {
                            visibleReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromTL(s));
                            break;
                        }
                    }
                }
            }
        } else {
            allReactionsAvailable = true;
            fillRecentReactionsList(visibleReactions);
        }
        filterReactions(visibleReactions);
        showExpandableReactions = !hitLimit && (!allReactionsAvailable && visibleReactions.size() > 16 || allReactionsAvailable && !UserConfig.getInstance(currentAccount).isPremium() && MessagesController.getInstance(currentAccount).premiumFeaturesBlocked());
        if (type == TYPE_TAGS && !UserConfig.getInstance(currentAccount).isPremium()) {
            showExpandableReactions = false;
        }
        if (type == TYPE_STICKER_SET_EMOJI) {
            showExpandableReactions = true;
        }
        setVisibleReactionsList(visibleReactions, animated);

        if (message != null && message.messageOwner.reactions != null && message.messageOwner.reactions.results != null) {
            for (int i = 0; i < message.messageOwner.reactions.results.size(); i++) {
                if (message.messageOwner.reactions.results.get(i).chosen) {
                    selectedReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromTL(message.messageOwner.reactions.results.get(i).reaction));
                }
            }
        }
    }

    public void setStoryItem(TL_stories.StoryItem storyItem) {
        selectedReactions.clear();
        if (storyItem != null && storyItem.sent_reaction != null) {
            selectedReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromTL(storyItem.sent_reaction));
        }
        listAdapter.notifyDataSetChanged();
    }


    public void setSelectedReaction(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        selectedReactions.clear();
        if (visibleReaction != null) {
            selectedReactions.add(visibleReaction);
        }
        listAdapter.notifyDataSetChanged();
    }

    public void setSelectedReactionAnimated(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        selectedReactions.clear();
        if (visibleReaction != null) {
            selectedReactions.add(visibleReaction);
        }
        updateSelected(true);
    }

    public void setSelectedReactions(ArrayList<MessageObject> messages) {
        selectedReactions.clear();
        for (int a = 0; a < messages.size(); ++a) {
            MessageObject message = messages.get(a);
            if (message != null && message.messageOwner.reactions != null && message.messageOwner.reactions.results != null) {
                for (int i = 0; i < message.messageOwner.reactions.results.size(); i++) {
                    if (message.messageOwner.reactions.results.get(i).chosen) {
                        selectedReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromTL(message.messageOwner.reactions.results.get(i).reaction));
                    }
                }
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    public HashSet<ReactionsLayoutInBubble.VisibleReaction> getSelectedReactions() {
        return selectedReactions;
    }

    public String getSelectedEmoji() {
        if (selectedReactions.isEmpty()) {
            return "";
        }
        String result = null;
        ReactionsLayoutInBubble.VisibleReaction visibleReaction = selectedReactions.iterator().next();
        if (visibleReaction.documentId != 0) {
            TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, visibleReaction.documentId);
            if (document != null) {
                result = MessageObject.findAnimatedEmojiEmoticon(document, null);
            }
        }
        if (TextUtils.isEmpty(result)) {
            result = visibleReaction.emojicon;
        }
        if (TextUtils.isEmpty(result)) {
            result = "👍";
        }
        return result;
    }

    public static HashSet<ReactionsLayoutInBubble.VisibleReaction> getInclusiveReactions(ArrayList<MessageObject> messages) {
        LongSparseArray<ReactionsLayoutInBubble.VisibleReaction> arr = new LongSparseArray<>();
        HashSet<Long> messageReactions = new HashSet<>();
        boolean firstMessage = true;
        for (int k = 0; k < messages.size(); ++k) {
            MessageObject message = messages.get(k);
            messageReactions.clear();
            if (message != null && message.messageOwner.reactions != null && message.messageOwner.reactions.results != null) {
                for (int i = 0; i < message.messageOwner.reactions.results.size(); i++) {
                    if (message.messageOwner.reactions.results.get(i).chosen) {
                        ReactionsLayoutInBubble.VisibleReaction reaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(message.messageOwner.reactions.results.get(i).reaction);
                        if (firstMessage || arr.indexOfKey(reaction.hash) >= 0) {
                            messageReactions.add(reaction.hash);
                            arr.put(reaction.hash, reaction);
                        }
                    }
                }
            }
            firstMessage = false;
            for (int j = 0; j < arr.size(); ++j) {
                if (!messageReactions.contains(arr.keyAt(j))) {
                    arr.removeAt(j);
                    j--;
                }
            }
        }
        HashSet<ReactionsLayoutInBubble.VisibleReaction> selectedReactions = new HashSet<>();
        for (int j = 0; j < arr.size(); ++j) {
            if (arr.valueAt(j) != null) {
                selectedReactions.add(arr.valueAt(j));
            }
        }
        return selectedReactions;
    }

    public void setSelectedReactionsInclusive(ArrayList<MessageObject> messages) {
        selectedReactions.clear();
        selectedReactions.addAll(getInclusiveReactions(messages));
        updateSelected(true);
    }

    public void setSelectedReactionInclusive(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        selectedReactions.clear();
        if (visibleReaction != null) {
            selectedReactions.add(visibleReaction);
        }
        updateSelected(true);
    }

    public void setSelectedEmojis(ArrayList<String> emojis) {
        selectedReactions.clear();
        for (String emoji : emojis) {
            ReactionsLayoutInBubble.VisibleReaction reaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(emoji);
            if (reaction != null) {
                selectedReactions.add(reaction);
                alwaysSelectedReactions.add(reaction);
            }
        }
        updateSelected(true);
    }

    private void updateSelected(boolean animated) {
        AndroidUtilities.forEachViews(recyclerListView, child -> {
            int position = recyclerListView.getChildAdapterPosition(child);
            if (position < 0 || position >= items.size()) return;
            if (child instanceof ReactionHolderView) {
                ((ReactionHolderView) child).updateSelected(items.get(position).reaction, animated);
            }
        });
    }

    private void filterReactions(List<ReactionsLayoutInBubble.VisibleReaction> visibleReactions) {
        HashSet<ReactionsLayoutInBubble.VisibleReaction> set = new HashSet<>();
        for (int i = 0; i < visibleReactions.size(); i++) {
            if (set.contains(visibleReactions.get(i))) {
                i--;
                visibleReactions.remove(i);
            } else {
                set.add(visibleReactions.get(i));
            }
        }
    }

    private void fillRecentReactionsList(List<ReactionsLayoutInBubble.VisibleReaction> visibleReactions) {
        HashSet<ReactionsLayoutInBubble.VisibleReaction> hashSet = new HashSet<>();
        int added = 0;
        if (type == TYPE_STICKER_SET_EMOJI) {
            for (ReactionsLayoutInBubble.VisibleReaction visibleReaction : selectedReactions) {
                if (!hashSet.contains(visibleReaction)) {
                    hashSet.add(visibleReaction);
                    visibleReactions.add(visibleReaction);
                    added++;
                    if (added >= 8) return;
                }
            }
            List<TLRPC.TL_availableReaction> enabledReactions = MediaDataController.getInstance(currentAccount).getEnabledReactionsList();
            for (int i = 0; i < enabledReactions.size(); i++) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(enabledReactions.get(i));
                if (!hashSet.contains(visibleReaction)) {
                    hashSet.add(visibleReaction);
                    visibleReactions.add(visibleReaction);
                    added++;
                    if (added >= 8) return;
                }
            }
            return;
        }
        if (!allReactionsAvailable || type == TYPE_STICKER_SET_EMOJI) {
            if (type == TYPE_TAGS) {
                ArrayList<TLRPC.Reaction> topReactions = MediaDataController.getInstance(currentAccount).getSavedReactions();
                for (int i = 0; i < topReactions.size(); i++) {
                    ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(topReactions.get(i));
                    if (!hashSet.contains(visibleReaction)) {
                        hashSet.add(visibleReaction);
                        visibleReactions.add(visibleReaction);
                        added++;
                    }
                    if (added == 16) {
                        break;
                    }
                }
            } else {
                //fill default reactions
                List<TLRPC.TL_availableReaction> enabledReactions = MediaDataController.getInstance(currentAccount).getEnabledReactionsList();
                for (int i = 0; i < enabledReactions.size(); i++) {
                    ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(enabledReactions.get(i));
                    visibleReactions.add(visibleReaction);
                }
            }
            return;
        }

        if (type == TYPE_MESSAGE_EFFECTS) {
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            TLRPC.messages_AvailableEffects effects = messagesController.getAvailableEffects();
            if (effects != null) {
                for (int i = 0; i < effects.effects.size(); i++) {
                    ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(effects.effects.get(i));
                    if (!hashSet.contains(visibleReaction)) {
                        hashSet.add(visibleReaction);
                        visibleReactions.add(visibleReaction);
                        added++;
                    }
                }
            }
            return;
        }

        ArrayList<TLRPC.Reaction> topReactions;
        if (type == TYPE_TAGS) {
            topReactions = MediaDataController.getInstance(currentAccount).getSavedReactions();
        } else {
            topReactions = MediaDataController.getInstance(currentAccount).getTopReactions();
        }
        if (type == TYPE_TAGS) {
            TLRPC.TL_messages_savedReactionsTags savedTags = MessagesController.getInstance(currentAccount).getSavedReactionTags(0);
            if (savedTags != null) {
                for (int i = 0; i < savedTags.tags.size(); i++) {
                    ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(savedTags.tags.get(i).reaction);
                    if (!hashSet.contains(visibleReaction)) {
                        hashSet.add(visibleReaction);
                        visibleReactions.add(visibleReaction);
                        added++;
                    }
                }
            }
            for (int i = 0; i < topReactions.size(); i++) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(topReactions.get(i));
                if (!hashSet.contains(visibleReaction)) {
                    hashSet.add(visibleReaction);
                    visibleReactions.add(visibleReaction);
                    added++;
                }
            }
        } else {
            for (int i = 0; i < topReactions.size(); i++) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(topReactions.get(i));
                if (!hashSet.contains(visibleReaction) && (type == TYPE_TAGS || UserConfig.getInstance(currentAccount).isPremium() || visibleReaction.documentId == 0)) {
                    hashSet.add(visibleReaction);
                    visibleReactions.add(visibleReaction);
                    added++;
                }
                //            if (added == 16) {
                //                break;
                //            }
            }
        }

        if (type != TYPE_TAGS || UserConfig.getInstance(currentAccount).isPremium()) {
            ArrayList<TLRPC.Reaction> recentReactions = MediaDataController.getInstance(currentAccount).getRecentReactions();
            for (int i = 0; i < recentReactions.size(); i++) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(recentReactions.get(i));
                if (!hashSet.contains(visibleReaction)) {
                    hashSet.add(visibleReaction);
                    visibleReactions.add(visibleReaction);
                }
            }

            //fill default reactions
            List<TLRPC.TL_availableReaction> enabledReactions = MediaDataController.getInstance(currentAccount).getEnabledReactionsList();
            for (int i = 0; i < enabledReactions.size(); i++) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(enabledReactions.get(i));
                if (!hashSet.contains(visibleReaction)) {
                    hashSet.add(visibleReaction);
                    visibleReactions.add(visibleReaction);
                }
            }
        }
    }

    private void checkPremiumReactions(List<TLRPC.TL_availableReaction> reactions) {
        premiumLockedReactions.clear();
        if (UserConfig.getInstance(currentAccount).isPremium()) {
            return;
        }
        try {
            for (int i = 0; i < reactions.size(); i++) {
                if (reactions.get(i).premium) {
                    premiumLockedReactions.add(reactions.remove(i));
                    i--;
                }
            }
        } catch (Exception e) {
            return;
        }
    }

    public void startEnterAnimation(boolean animatePopup) {
        this.animatePopup = animatePopup;
        setTransitionProgress(0);
        setAlpha(1f);
        notificationsLocker.lock();
        ObjectAnimator animator;
        if (allowSmoothEnterTransition()) {
            animator = ObjectAnimator.ofFloat(this, ReactionsContainerLayout.TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(250);
            animator.setInterpolator(new OvershootInterpolator(0.5f));
        } else {
            animator = ObjectAnimator.ofFloat(this, ReactionsContainerLayout.TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(250);
            animator.setInterpolator(new OvershootInterpolator(0.5f));
        }
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                notificationsLocker.unlock();
            }
        });
        animator.start();
    }

    public int getTotalWidth() {
        int itemsCount = getItemsCount();
        int width;
        if (!showCustomEmojiReaction()) {
            width = dp(36) * itemsCount + dp(2) * (itemsCount - 1) + dp(16);
        } else {
            width = dp(36) * itemsCount - dp(4);
        }
        return width;
    }

    public int getHintTextWidth() {
        return hintViewWidth;
    }

    public int getItemsCount() {
        return visibleReactionsList.size() + (showCustomEmojiReaction() ? 1 : 0) + 1;
    }

    public void setCustomEmojiEnterProgress(float progress) {
        customEmojiReactionsEnterProgress = progress;
        if (chatScrimPopupContainerLayout != null) {
            chatScrimPopupContainerLayout.setPopupAlpha(1f - progress);
        }
        invalidate();
    }

    public void dismissParent(boolean animated) {
        if (reactionsWindow != null) {
            reactionsWindow.dismiss(animated);
            reactionsWindow = null;
        }
    }

    public void onReactionClicked(View emojiView, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress) {
        if (delegate != null) {
            delegate.onReactionClicked(emojiView, visibleReaction, longpress, true);
        }
        if (type == TYPE_MESSAGE_EFFECTS) {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {}
        }
    }

    private boolean prepareAnimation;

    public void prepareAnimation(boolean b) {
        prepareAnimation = b;
        invalidate();
    }

    public void setCustomEmojiReactionsBackground(boolean isNeed) {
        if (isNeed) {
            customEmojiReactionsIconView.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(28), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector), 40)));
        } else {
            customEmojiReactionsIconView.setBackground(null);
        }
    }

    boolean skipDraw;

    public void setSkipDraw(boolean b) {
        if (skipDraw != b) {
            skipDraw = b;
            if (!skipDraw) {
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    if (recyclerListView.getChildAt(i) instanceof ReactionHolderView) {
                        ReactionHolderView holderView = (ReactionHolderView) recyclerListView.getChildAt(i);
                        if (holderView.hasEnterAnimation && (holderView.loopImageView.getImageReceiver().getLottieAnimation() != null || holderView.loopImageView.getImageReceiver().getAnimation() != null)) {
                            holderView.loopImageView.setVisibility(View.VISIBLE);
                            holderView.enterImageView.setVisibility(View.INVISIBLE);
                            if (holderView.shouldSwitchToLoopView) {
                                holderView.switchedToLoopView = true;
                            }
                        }
                        holderView.invalidate();
                    }
                }
            }
            invalidate();
        }
    }

    public void onCustomEmojiWindowOpened() {
        if (pullingDownBackAnimator != null) {
            pullingDownBackAnimator.cancel();
        }
        pullingLeftOffset = 0f;
        if (customReactionsContainer != null) {
            customReactionsContainer.invalidate();
        }
        invalidate();
    }


    public void onCustomEmojiWindowClosing() {
        if (pullingDownBackAnimator != null) {
            pullingDownBackAnimator.cancel();
        }
        pullingLeftOffset = 0f;
        if (customReactionsContainer != null) {
            customReactionsContainer.invalidate();
        }
        invalidate();
    }

    public void clearRecentReactions() {
        AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.ClearRecentReactionsAlertTitle))
                .setMessage(LocaleController.getString(R.string.ClearRecentReactionsAlertMessage))
                .setPositiveButton(LocaleController.getString(R.string.ClearButton), (dialog, which) -> {
                    MediaDataController.getInstance(currentAccount).clearRecentReactions();
                    List<ReactionsLayoutInBubble.VisibleReaction> visibleReactions = new ArrayList<>();
                    fillRecentReactionsList(visibleReactions);
                    setVisibleReactionsList(visibleReactions, true);
                    lastVisibleViews.clear();
                    reactionsWindow.setRecentReactions(visibleReactions);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    ChatScrimPopupContainerLayout chatScrimPopupContainerLayout;

    public void setChatScrimView(ChatScrimPopupContainerLayout chatScrimPopupContainerLayout) {
        this.chatScrimPopupContainerLayout = chatScrimPopupContainerLayout;
    }

    public ReactionsContainerDelegate getDelegate() {
        return delegate;
    }

    public void setCurrentAccount(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public void setFragment(BaseFragment lastFragment) {
        fragment = lastFragment;
    }

    public void reset() {
        isHiddenNextReaction = true;
        pressedReactionPosition = 0;
        pressedProgress = 0;
        pullingLeftOffset = 0;
        pressedReaction = null;
        clicked = false;
        AndroidUtilities.forEachViews(recyclerListView, view -> {
            if (view instanceof ReactionHolderView) {
                ReactionHolderView reactionHolderView = (ReactionHolderView) view;
                reactionHolderView.pressed = false;
                reactionHolderView.loopImageView.setAlpha(1f);
                if (skipEnterAnimation) {
                    reactionHolderView.loopImageView.setScaleX(reactionHolderView.enterScale * (reactionHolderView.selected ? 0.76f : 1f));
                    reactionHolderView.loopImageView.setScaleY(reactionHolderView.enterScale * (reactionHolderView.selected ? 0.76f : 1f));
                } else {
                    reactionHolderView.resetAnimation();
                }
            }

        });
        lastVisibleViews.clear();
        recyclerListView.invalidate();
        if (customReactionsContainer != null) {
            customReactionsContainer.invalidate();
        }
        invalidate();
    }

    public void setHint(CharSequence storyReactionsHint) {
        hasHint = true;
        if (hintView == null) {
            hintView = new LinkSpanDrawable.LinksTextView(getContext(), resourcesProvider);
            hintView.setPadding(dp(8), 0, dp(8), 0);
            hintView.setClickable(true);
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            if (type == TYPE_STORY || type == TYPE_STORY_LIKES || type == TYPE_STICKER_SET_EMOJI) {
                hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                hintView.setAlpha(0.5f);
            } else {
                hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            }
            hintView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0, 0));
        }
        hintView.setText(storyReactionsHint);
        hintMeasured = false;

        ((LayoutParams) nextRecentReaction.getLayoutParams()).topMargin = dp(20);
        ((LayoutParams) recyclerListView.getLayoutParams()).topMargin = dp(20);
    }


    private boolean hintMeasured;
    public void measureHint() {
        if (hintMeasured || !hasHint || getMeasuredWidth() <= 0) return;

        int maxWidth = Math.min(dp(320), getMeasuredWidth() - dp(16));
        StaticLayout layout = new StaticLayout(hintView.getText(), hintView.getPaint(), maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        hintViewHeight = layout.getHeight();
        hintViewWidth = 0;
        for (int i = 0; i < layout.getLineCount(); ++i) {
            hintViewWidth = Math.max(hintViewWidth, (int) Math.ceil(layout.getLineWidth(i)));
        }
        if (layout.getLineCount() > 1 && !hintView.getText().toString().contains("\n")) {
            maxWidth = HintView2.cutInFancyHalf(hintView.getText(), hintView.getPaint());
            layout = new StaticLayout(hintView.getText(), hintView.getPaint(), maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            hintViewHeight = layout.getHeight();
            hintViewWidth = 0;
            for (int i = 0; i < layout.getLineCount(); ++i) {
                hintViewWidth = Math.max(hintViewWidth, (int) Math.ceil(layout.getLineWidth(i)));
            }
            hintView.setPadding(dp(24), 0, dp(24), 0);
            hintView.setWidth(dp(48) + maxWidth);
        } else {
            hintView.setWidth(dp(16) + maxWidth);
        }

        int margin = Math.max(dp(20), dp(7) + hintViewHeight);
        if (type == TYPE_STORY || type == TYPE_STORY_LIKES) {
            margin = dp(20);
        } else {
            getLayoutParams().height = dp(52) + margin + dp(22);
        }
        ((LayoutParams) nextRecentReaction.getLayoutParams()).topMargin = margin;
        ((LayoutParams) recyclerListView.getLayoutParams()).topMargin = margin;
        hintMeasured = true;
    }

    public void setTop(boolean isTop) {
        this.isTop = isTop;
    }

    public float getTopOffset() {
        return hasHint ? ((LayoutParams) recyclerListView.getLayoutParams()).topMargin : 0;
    }

    public void setBubbleOffset(float v) {
        bubblesOffset = v;
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
        private final boolean recyclerReaction;
        public BackupImageView enterImageView;
        public BackupImageView loopImageView;
        public BackupImageView pressedBackupImageView;
        private ImageReceiver preloadImageReceiver = new ImageReceiver();
        public ReactionsLayoutInBubble.VisibleReaction currentReaction;
        public PremiumLockIconView lockIconView;
        public float sideScale = 1f;
        private boolean isEnter;
        public boolean hasEnterAnimation;
        public boolean shouldSwitchToLoopView;
        public boolean switchedToLoopView;
        public boolean selected;
        public boolean drawSelected = true;
        public int position;
        public boolean waitingAnimation;
        public StarsReactionsSheet.Particles particles;

        Runnable playRunnable = new Runnable() {
            @Override
            public void run() {
                if (enterImageView.getImageReceiver().getLottieAnimation() != null && !enterImageView.getImageReceiver().getLottieAnimation().isRunning() && !enterImageView.getImageReceiver().getLottieAnimation().isGeneratingCache()) {
                    enterImageView.getImageReceiver().getLottieAnimation().start();
                }
                waitingAnimation = false;
            }
        };

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (currentReaction != null) {
                if (currentReaction.emojicon != null) {
                    info.setText(currentReaction.emojicon);
                    info.setEnabled(true);
                } else {
                    info.setText(LocaleController.getString(R.string.AccDescrCustomEmoji));
                    info.setEnabled(true);
                }
            }
        }

        ReactionHolderView(Context context, boolean recyclerReaction) {
            super(context);
            this.recyclerReaction = recyclerReaction;
            enterImageView = new BackupImageView(context) {

                @Override
                protected ImageReceiver createImageReciever() {
                    return new ImageReceiver(this) {
                        @Override
                        protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                            if (drawable instanceof RLottieDrawable) {
                                RLottieDrawable rLottieDrawable = (RLottieDrawable) drawable;
                                rLottieDrawable.setCurrentFrame(0, false, true);
                            }
                            return super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                        }
                    };
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    if (imageReceiver.getLottieAnimation() != null && !waitingAnimation) {
                        imageReceiver.getLottieAnimation().start();
                    }
                    if (shouldSwitchToLoopView && !switchedToLoopView && imageReceiver.getLottieAnimation() != null && imageReceiver.getLottieAnimation().isLastFrame() && loopImageView.imageReceiver.getLottieAnimation() != null && loopImageView.imageReceiver.getLottieAnimation().hasBitmap()) {
                        switchedToLoopView = true;
                        loopImageView.imageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                        loopImageView.setVisibility(View.VISIBLE);
                        if (onSwitchedToLoopView != null) {
                            onSwitchedToLoopView.run();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            enterImageView.setVisibility(View.INVISIBLE);
                        });
                    }
                    invalidate();
                }

                @Override
                public void invalidate() {
                    if (HwEmojis.grabIfWeakDevice(this, ReactionsContainerLayout.this)) {
                        return;
                    }
                    super.invalidate();
                    ReactionsContainerLayout.this.invalidate();
                }

                @Override
                public void invalidate(Rect dirty) {
                    if (HwEmojis.grabIfWeakDevice(this, ReactionsContainerLayout.this)) {
                        return;
                    }
                    super.invalidate(dirty);
                    ReactionsContainerLayout.this.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (HwEmojis.grabIfWeakDevice(this)) {
                        return;
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            loopImageView = new BackupImageView(context) {

                @Override
                protected void onDraw(Canvas canvas) {
                    checkPlayLoopImage();
                    super.onDraw(canvas);
                }

                @Override
                protected ImageReceiver createImageReciever() {
                    return new ImageReceiver(this) {

                        @Override
                        protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                            boolean rez = super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                            if (rez) {
                                if (drawable instanceof RLottieDrawable) {
                                    RLottieDrawable rLottieDrawable = (RLottieDrawable) drawable;
                                    rLottieDrawable.setCurrentFrame(0, false, true);
                                    rLottieDrawable.stop();
                                }
                            }
                            return rez;
                        }
                    };
                }

                @Override
                public void invalidate() {
                    if (HwEmojis.grabIfWeakDevice(this)) {
                        return;
                    }
                    super.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (HwEmojis.grabIfWeakDevice(this)) {
                        return;
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            enterImageView.getImageReceiver().setAutoRepeat(0);
            enterImageView.getImageReceiver().setAllowStartLottieAnimation(false);

            pressedBackupImageView = new BackupImageView(context) {

                @Override
                protected void onDraw(Canvas canvas) {
                    ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
                    if (imageReceiver != null && imageReceiver.getLottieAnimation() != null) {
                        imageReceiver.getLottieAnimation().start();
                    }
                    super.onDraw(canvas);
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    ReactionsContainerLayout.this.invalidate();
                }
            };
            addView(enterImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            addView(pressedBackupImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            addView(loopImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            if (type == TYPE_STICKER_SET_EMOJI) {
                LayoutTransition layoutTransition = new LayoutTransition();
                layoutTransition.setDuration(100);
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
                setLayoutTransition(layoutTransition);
            }
            enterImageView.setLayerNum(Integer.MAX_VALUE);
            loopImageView.setLayerNum(Integer.MAX_VALUE);
            loopImageView.imageReceiver.setAutoRepeat(0);
            loopImageView.imageReceiver.setAllowStartAnimation(false);
            loopImageView.imageReceiver.setAllowStartLottieAnimation(false);
            pressedBackupImageView.setLayerNum(Integer.MAX_VALUE);
        }

        public boolean isLocked;
        public float enterScale = 1f;
        public ValueAnimator enterAnimator;

        public void updateSelected(ReactionsLayoutInBubble.VisibleReaction react, boolean animated) {
            boolean wasSelected = selected;
            selected = selectedReactions.contains(react);
            if (selected != wasSelected) {
                if (!animated) {
                    loopImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
                    loopImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                    enterImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
                    enterImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                } else {
                    loopImageView.animate().scaleX(enterScale * (selected ? 0.76f : 1f)).scaleY(enterScale * (selected ? 0.76f : 1f)).setDuration(240).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    enterImageView.animate().scaleX(enterScale * (selected ? 0.76f : 1f)).scaleY(enterScale * (selected ? 0.76f : 1f)).setDuration(240).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                }

                requestLayout();
                invalidate();
            }
        }

        private void setReaction(ReactionsLayoutInBubble.VisibleReaction react, int position) {
            updateSelected(react, false);
            if (currentReaction != null && currentReaction.equals(react)) {
                this.position = position;
                updateImage(react);
                return;
            }

            final boolean userIsPremium = UserConfig.getInstance(currentAccount).isPremium();
            isLocked = type == TYPE_TAGS && !userIsPremium || type == TYPE_MESSAGE_EFFECTS && react.premium && !userIsPremium;
            if (isLocked && lockIconView == null) {
                lockIconView = new PremiumLockIconView(getContext(), PremiumLockIconView.TYPE_STICKERS_PREMIUM_LOCKED);
                lockIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                lockIconView.setImageReceiver(loopImageView.getImageReceiver());
                addView(lockIconView, LayoutHelper.createFrame(18, 18, Gravity.CENTER, 8, 8, 0, 0));
            }
            if (lockIconView != null) {
                lockIconView.setVisibility(isLocked ? View.VISIBLE : View.GONE);
            }

            resetAnimation();
            currentReaction = react;
            hasEnterAnimation = currentReaction.isStar || (currentReaction.emojicon != null && (showCustomEmojiReaction() || allReactionsIsDefault)) && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS);
            if (type == TYPE_STICKER_SET_EMOJI || currentReaction.isEffect) {
                hasEnterAnimation = false;
            }
            if (currentReaction.isStar || currentReaction.emojicon != null) {
                updateImage(react);

                pressedBackupImageView.setAnimatedEmojiDrawable(null);
                if (enterImageView.getImageReceiver().getLottieAnimation() != null) {
                    enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                }
                if (lockIconView != null) {
                    lockIconView.setAnimatedEmojiDrawable(null);
                }
            } else {
                pressedBackupImageView.getImageReceiver().clearImage();
                loopImageView.getImageReceiver().clearImage();
                AnimatedEmojiDrawable pressedDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, currentReaction.documentId);
                AnimatedEmojiDrawable loopDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, currentReaction.documentId);
                if (type == TYPE_STORY || type == TYPE_STORY_LIKES || type == TYPE_STICKER_SET_EMOJI) {
                    pressedDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                    loopDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                } else {
                    pressedDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
                    loopDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
                }
                pressedBackupImageView.setAnimatedEmojiDrawable(pressedDrawable);
                loopImageView.setAnimatedEmojiDrawable(loopDrawable);
                if (lockIconView != null) {
                    lockIconView.setAnimatedEmojiDrawable(loopDrawable);
                }
            }
            setFocusable(true);
            shouldSwitchToLoopView = hasEnterAnimation;// && !allReactionsIsDefault;
            if (!hasEnterAnimation) {
                enterImageView.setVisibility(View.GONE);
                loopImageView.setVisibility(View.VISIBLE);
                switchedToLoopView = true;
            } else {
                switchedToLoopView = false;
                enterImageView.setVisibility(View.VISIBLE);
                loopImageView.setVisibility(View.GONE);
            }
//            if (selected) {
//                loopImageView.getLayoutParams().width = loopImageView.getLayoutParams().height = dp(26);
//                enterImageView.getLayoutParams().width = enterImageView.getLayoutParams().height = dp(26);
//            } else {
                loopImageView.getLayoutParams().width = loopImageView.getLayoutParams().height = dp(34);
                enterImageView.getLayoutParams().width = enterImageView.getLayoutParams().height = dp(34);
//            }
        }

        private void updateImage(ReactionsLayoutInBubble.VisibleReaction react) {
            if (react != null && react.isStar) {
                enterImageView.getImageReceiver().setImageBitmap(new RLottieDrawable(R.raw.star_reaction, "star_reaction", dp(30), dp(30)));
                loopImageView.getImageReceiver().setImageBitmap(getContext().getResources().getDrawable(R.drawable.star_reaction));
                if (particles == null) {
                    particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 45 : 18);
                }
            } else if (type == TYPE_STICKER_SET_EMOJI && react != null && react.emojicon != null) {
                enterImageView.getImageReceiver().setImageBitmap(Emoji.getEmojiDrawable(react.emojicon));
                loopImageView.getImageReceiver().setImageBitmap(Emoji.getEmojiDrawable(react.emojicon));
            } else if (currentReaction.isEffect) {
                TLRPC.Document document = MessagesController.getInstance(currentAccount).getEffectDocument(currentReaction.documentId);
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
//                if (!LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS)) {
                    loopImageView.getImageReceiver().setImage(ImageLocation.getForDocument(document), "60_60_firstframe", null, null, hasEnterAnimation ? null : svgThumb, 0, "tgs", currentReaction, 0);
//                } else {
//                    loopImageView.getImageReceiver().setImage(ImageLocation.getForDocument(document), ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, hasEnterAnimation ? null : svgThumb, 0, "tgs", currentReaction, 0);
//                }
            } else if (currentReaction.emojicon != null) {
                TLRPC.TL_availableReaction defaultReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(currentReaction.emojicon);
                if (defaultReaction != null) {
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(defaultReaction.activate_animation, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    if (!LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS) || type == TYPE_STICKER_SET_EMOJI) {
                        if (SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW || type == TYPE_STICKER_SET_EMOJI) {
                            loopImageView.getImageReceiver().setImage(ImageLocation.getForDocument(defaultReaction.select_animation), "60_60_firstframe", null, null, hasEnterAnimation ? null : svgThumb, 0, "tgs", currentReaction, 0);
                        } else {
                            enterImageView.getImageReceiver().setImage(ImageLocation.getForDocument(defaultReaction.appear_animation), ReactionsUtils.APPEAR_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", react, 0);
                            loopImageView.getImageReceiver().setImage(ImageLocation.getForDocument(defaultReaction.select_animation), "60_60_firstframe", null, null, hasEnterAnimation ? null : svgThumb, 0, "tgs", currentReaction, 0);
                        }
                    } else {
                        enterImageView.getImageReceiver().setImage(ImageLocation.getForDocument(defaultReaction.appear_animation), ReactionsUtils.APPEAR_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", react, 0);
                        loopImageView.getImageReceiver().setImage(ImageLocation.getForDocument(defaultReaction.select_animation), ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, hasEnterAnimation ? null : svgThumb, 0, "tgs", currentReaction, 0);
                    }
                    if (enterImageView.getImageReceiver().getLottieAnimation() != null) {
                        enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false, true);
                    }
                    pressedBackupImageView.getImageReceiver().setImage(ImageLocation.getForDocument(defaultReaction.select_animation), ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", react, 0);

                    preloadImageReceiver.setAllowStartLottieAnimation(false);
                    MediaDataController.getInstance(currentAccount).preloadImage(preloadImageReceiver, ImageLocation.getForDocument(defaultReaction.around_animation), ReactionsEffectOverlay.getFilterForAroundAnimation());
                }
                if (lockIconView != null) {
                    lockIconView.setImageReceiver(loopImageView.getImageReceiver());
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            resetAnimation();
            preloadImageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            preloadImageReceiver.onDetachedFromWindow();
        }

        public boolean play(int delay) {
            if (!animationEnabled) {
                resetAnimation();
                isEnter = true;
                if (!hasEnterAnimation) {
                    loopImageView.setVisibility(VISIBLE);
                    loopImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                    loopImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
                }
                return false;
            }
            AndroidUtilities.cancelRunOnUIThread(playRunnable);
            if (hasEnterAnimation) {
                if (enterImageView.getImageReceiver().getLottieAnimation() != null && !enterImageView.getImageReceiver().getLottieAnimation().isGeneratingCache() && !isEnter) {
                    isEnter = true;
                    if (delay == 0) {
                        waitingAnimation = false;
                        enterImageView.getImageReceiver().getLottieAnimation().stop();
                        enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                        playRunnable.run();

                    } else {
                        waitingAnimation = true;
                        enterImageView.getImageReceiver().getLottieAnimation().stop();
                        enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                        AndroidUtilities.runOnUIThread(playRunnable, delay);
                    }
                    return true;
                }
                if (enterImageView.getImageReceiver().getLottieAnimation() != null && isEnter && !enterImageView.getImageReceiver().getLottieAnimation().isRunning() && !enterImageView.getImageReceiver().getLottieAnimation().isGeneratingCache()) {
                    enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(enterImageView.getImageReceiver().getLottieAnimation().getFramesCount() - 1, false);
                }
                loopImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                loopImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
            } else {
                if (!isEnter) {
                    enterScale = 0f;
                    loopImageView.setScaleX(0);
                    loopImageView.setScaleY(0);
                    enterAnimator = ValueAnimator.ofFloat(0, 1f);
                    enterAnimator.addUpdateListener(anm -> {
                        enterScale = (float) anm.getAnimatedValue();
                        loopImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                        loopImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
                    });
                    enterAnimator.setDuration(150);
                    enterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    enterAnimator.setStartDelay((long) (delay * durationScale));
                    enterAnimator.start();
                    isEnter = true;
                }
            }
            return false;
        }

        public void resetAnimation() {
            if (hasEnterAnimation) {
                AndroidUtilities.cancelRunOnUIThread(playRunnable);
                if (enterImageView.getImageReceiver().getLottieAnimation() != null && !enterImageView.getImageReceiver().getLottieAnimation().isGeneratingCache()) {
                    enterImageView.getImageReceiver().getLottieAnimation().stop();
                    if (animationEnabled) {
                        enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false, true);
                    } else {
                        enterImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(enterImageView.getImageReceiver().getLottieAnimation().getFramesCount() - 1, false, true);
                    }
                }
                loopImageView.setVisibility(View.INVISIBLE);
                enterImageView.setVisibility(View.VISIBLE);
                switchedToLoopView = false;
                loopImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                loopImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
            } else {
                loopImageView.animate().cancel();
                if (skipEnterAnimation) {
                    loopImageView.setScaleY(enterScale * (selected ? 0.76f : 1f));
                    loopImageView.setScaleX(enterScale * (selected ? 0.76f : 1f));
                } else {
                    loopImageView.setScaleY(0);
                    loopImageView.setScaleX(0);
                }
            }
            isEnter = false;
        }

        Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (Exception ignored) {}
                pressedReactionPosition = visibleReactionsList.indexOf(currentReaction);
                pressedReaction = currentReaction;
                ReactionsContainerLayout.this.invalidate();
            }
        };

        float pressedX, pressedY;
        boolean pressed;
        boolean touchable = true;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!touchable || cancelPressedAnimation != null) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
                if (sideScale == 1f && !isLocked && type != TYPE_TAGS && type != TYPE_STICKER_SET_EMOJI && type != TYPE_MESSAGE_EFFECTS) {
                    AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
            }
            float touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2f;
            boolean cancelByMove = event.getAction() == MotionEvent.ACTION_MOVE && (Math.abs(pressedX - event.getX()) > touchSlop || Math.abs(pressedY - event.getY()) > touchSlop);
            if (cancelByMove || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressed && (pressedReaction == null || pressedProgress > 0.8f) && delegate != null) {
                    clicked = true;
                    if (System.currentTimeMillis() - lastReactionSentTime > 300) {
                        lastReactionSentTime = System.currentTimeMillis();
                        delegate.onReactionClicked(this, currentReaction, pressedProgress > 0.8f, false);
                    }
                }
                if (!clicked) {
                    cancelPressed();
                }

                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                pressed = false;
            }
            return true;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (selected && drawSelected) {
                canvas.drawCircle(getMeasuredWidth() >> 1, getMeasuredHeight() >> 1, (getMeasuredWidth() >> 1) - dp(1), currentReaction != null && currentReaction.isStar ? starSelectedPaint : selectedPaint);
            }
            if (loopImageView.animatedEmojiDrawable != null && loopImageView.animatedEmojiDrawable.getImageReceiver() != null) {
                if (position == 0) {
                    loopImageView.animatedEmojiDrawable.getImageReceiver().setRoundRadius(dp(6), 0, 0, dp(6));
                } else {
                    loopImageView.animatedEmojiDrawable.getImageReceiver().setRoundRadius(selected ? dp(6) : 0);
                }
            }
            if (currentReaction != null && currentReaction.isStar && particles != null && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS)) {
                final int sz = (int) (getHeight() * .7f);
                AndroidUtilities.rectTmp.set(getWidth() / 2f - sz / 2f, getHeight() / 2f - sz / 2f, getWidth() / 2f + sz / 2f, getHeight() / 2f + sz / 2f);
                RLottieDrawable lottieDrawable = enterImageView.getImageReceiver().getLottieAnimation();
                final int startframe = 30, dur = 30;
                particles.setVisible(lottieDrawable != null && lottieDrawable.getCurrentFrame() > startframe ? Utilities.clamp01((float) (lottieDrawable.getCurrentFrame() - startframe) / dur) : 0f);
                particles.setBounds(AndroidUtilities.rectTmp);
                particles.process();
                particles.draw(canvas, 0xFFF5B90E);
                invalidate();
            }
            super.dispatchDraw(canvas);
        }

        public void checkPlayLoopImage() {
            ImageReceiver imageReceiver = loopImageView.animatedEmojiDrawable != null ? loopImageView.animatedEmojiDrawable.getImageReceiver() : loopImageView.imageReceiver;
            if (imageReceiver != null && imageReceiver.getLottieAnimation() != null) {
                if (reactionsWindow != null || pressed || !allReactionsIsDefault) {
                    imageReceiver.getLottieAnimation().start();
                } else {
                    if (imageReceiver.getLottieAnimation().getCurrentFrame() <= 2) {
                        imageReceiver.getLottieAnimation().stop();
                    }
                }
            }
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
        void onReactionClicked(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent);

        default void hideMenu() {

        }

        default void drawRoundRect(Canvas canvas, RectF rect, float radius, float offsetX, float offsetY, int alpha, boolean isWindow) {

        }

        default boolean needEnterText() {
            return false;
        }

        default void onEmojiWindowDismissed() {

        }

        default boolean drawBackground() {
            return false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        if (type == TYPE_MESSAGE_EFFECTS) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.availableEffectsUpdate);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        if (type == TYPE_MESSAGE_EFFECTS) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableEffectsUpdate);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == waitingLoadingChatId && getVisibility() != View.VISIBLE && !(chatFull.available_reactions instanceof TLRPC.TL_chatReactionsNone)) {
                setMessage(messageObject, null, true);
                setVisibility(View.VISIBLE);
                startEnterAnimation(false);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            invalidateEmojis();
        } else if (id == NotificationCenter.availableEffectsUpdate) {
            setMessage(messageObject, null, true);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getAlpha() < 0.5f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void setAlpha(float alpha) {
        if (getAlpha() != alpha && alpha == 0) {
            lastVisibleViews.clear();
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                if (recyclerListView.getChildAt(i) instanceof ReactionHolderView) {
                    ReactionHolderView view = (ReactionHolderView) recyclerListView.getChildAt(i);
                    view.resetAnimation();
                }
            }
        }
        super.setAlpha(alpha);
    }

    @Override
    public void setTranslationX(float translationX) {
        if (translationX != getTranslationX()) {
            super.setTranslationX(translationX);
        }
    }

    private class InternalImageView extends ImageView {

        boolean isEnter;
        ValueAnimator valueAnimator;

        public InternalImageView(Context context) {
            super(context);
        }

        public void play(int delay, boolean animated) {
            isEnter = true;
            invalidate();
            if (valueAnimator != null) {
                valueAnimator.removeAllListeners();
                valueAnimator.cancel();
            }

            if (animated) {
                valueAnimator = ValueAnimator.ofFloat(getScaleX(), 1f);
                valueAnimator.setInterpolator(AndroidUtilities.overshootInterpolator);
                valueAnimator.addUpdateListener(animation -> {
                    float s = (float) animation.getAnimatedValue();
                    setScaleX(s);
                    setScaleY(s);
                    customReactionsContainer.invalidate();
                });
                valueAnimator.setStartDelay((long) (delay * durationScale));
                valueAnimator.setDuration(300);
                valueAnimator.start();
            } else {
                setScaleX(1f);
                setScaleY(1f);
            }
        }

        public void resetAnimation() {
            isEnter = false;
            setScaleX(0);
            setScaleY(0);
            customReactionsContainer.invalidate();
            if (valueAnimator != null) {
                valueAnimator.cancel();
            }
        }
    }

    private class CustomReactionsContainer extends FrameLayout {

        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CustomReactionsContainer(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int color;
            if (type == TYPE_STORY || type == TYPE_STORY_LIKES || type == TYPE_STICKER_SET_EMOJI) {
                color = ColorUtils.setAlphaComponent(Color.WHITE, 30);
            } else {
                color = ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider), Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 0.7f);
            }
            backgroundPaint.setColor(color);

            float cy = getMeasuredHeight() / 2f;
            float cx = getMeasuredWidth() / 2f;
            View child = getChildAt(0);

            float sizeHalf = (getMeasuredWidth() - AndroidUtilities.dpf2(6)) / 2f;

            float expandSize = expandSize();
            AndroidUtilities.rectTmp.set(cx - sizeHalf, cy - sizeHalf - expandSize, cx + sizeHalf, cy + sizeHalf + expandSize);
            canvas.save();
            canvas.scale(child.getScaleX(), child.getScaleY(), cx, cy);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, sizeHalf, sizeHalf, backgroundPaint);
            canvas.restore();

            canvas.save();
            canvas.translate(0, expandSize);
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }

    public float expandSize() {
        return (int) (getPullingLeftProgress() * dp(6));
    }

    public void setParentLayout(ChatScrimPopupContainerLayout layout) {
        parentLayout = layout;
    }

    public static boolean allowSmoothEnterTransition() {
        return SharedConfig.deviceIsHigh();
    }

    public class Adapter extends AdapterWithDiffUtils {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                default:
                case VIEW_TYPE_REACTION:
                case VIEW_TYPE_CUSTOM_REACTION:
                    view = new ReactionHolderView(getContext(), true);
                    break;
                case VIEW_TYPE_PREMIUM_BUTTON:
                    premiumLockContainer = new FrameLayout(getContext());
                    premiumLockIconView = new PremiumLockIconView(getContext(), PremiumLockIconView.TYPE_REACTIONS);
                    premiumLockIconView.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), Theme.getColor(Theme.key_dialogBackground), 0.7f));
                    premiumLockIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
                    premiumLockIconView.setScaleX(0f);
                    premiumLockIconView.setScaleY(0f);
                    premiumLockIconView.setPadding(dp(1), dp(1), dp(1), dp(1));
                    premiumLockContainer.addView(premiumLockIconView, LayoutHelper.createFrame(26, 26, Gravity.CENTER));
                    premiumLockIconView.setOnClickListener(v -> {
                        int[] position = new int[2];
                        v.getLocationOnScreen(position);
                        showUnlockPremium(position[0] + v.getMeasuredWidth() / 2f, position[1] + v.getMeasuredHeight() / 2f);
                    });
                    view = premiumLockContainer;
                    break;
                case VIEW_TYPE_CUSTOM_EMOJI_BUTTON:
                    customReactionsContainer = new CustomReactionsContainer(getContext());
                    customEmojiReactionsIconView = new InternalImageView(getContext());
                    customEmojiReactionsIconView.setImageResource(R.drawable.msg_reactions_expand);
                    customEmojiReactionsIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    if (type == TYPE_STORY || type == TYPE_STORY_LIKES || type == TYPE_STICKER_SET_EMOJI) {
                        customEmojiReactionsIconView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
                    } else {
                        customEmojiReactionsIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
                    }
                    customEmojiReactionsIconView.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(28), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector), 40)));
                    customEmojiReactionsIconView.setPadding(dp(2), dp(2), dp(2), dp(2));
                    customEmojiReactionsIconView.setContentDescription(LocaleController.getString(R.string.AccDescrExpandPanel));
                    customReactionsContainer.addView(customEmojiReactionsIconView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
                    customEmojiReactionsIconView.setOnClickListener(v -> {
                        showCustomEmojiReactionDialog();
                    });
                    view = customReactionsContainer;
                    break;
            }

            int size = getLayoutParams().height - (int) getTopOffset() - getPaddingTop() - getPaddingBottom();
            view.setLayoutParams(new RecyclerView.LayoutParams(size - dp(12), size));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_TYPE_REACTION || holder.getItemViewType() == VIEW_TYPE_CUSTOM_REACTION) {
                ReactionHolderView h = (ReactionHolderView) holder.itemView;
                h.setScaleX(1);
                h.setScaleY(1);
                h.setReaction(items.get(position).reaction, position);
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == VIEW_TYPE_REACTION || holder.getItemViewType() == VIEW_TYPE_CUSTOM_REACTION) {
                int position = holder.getAdapterPosition();
                if (position >= 0 && position < items.size()) {
                    ((ReactionHolderView) holder.itemView).updateSelected(items.get(position).reaction, false);
                }
            }
            super.onViewAttachedToWindow(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        public void updateItems(boolean animated) {
            oldItems.clear();
            oldItems.addAll(items);
            items.clear();
            for (int i = 0; i < visibleReactionsList.size(); i++) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = visibleReactionsList.get(i);
                items.add(new InnerItem(visibleReaction.emojicon == null ? VIEW_TYPE_CUSTOM_REACTION : VIEW_TYPE_REACTION, visibleReaction));
            }
            if (showUnlockPremiumButton()) {
                items.add(new InnerItem(VIEW_TYPE_PREMIUM_BUTTON, null));
            }
            if (showCustomEmojiReaction()) {
                items.add(new InnerItem(VIEW_TYPE_CUSTOM_EMOJI_BUTTON, null));
            }
            if (animated) {
                setItems(oldItems, items);
            } else {
                super.notifyDataSetChanged();
            }
        }
    }

    public boolean paused = false;
    public boolean pausedExceptSelected;
    public void setPaused(boolean paused, boolean exceptSelected) {
        if (this.paused == paused) return;
        this.paused = paused;
        pausedExceptSelected = exceptSelected;
        if (reactionsWindow != null && reactionsWindow.getSelectAnimatedEmojiDialog() != null) {
            reactionsWindow.getSelectAnimatedEmojiDialog().setPaused(this.paused, this.pausedExceptSelected);
        }
    }


    private Paint starSelectedGradientPaint;
    private Matrix starSelectedGradientMatrix;
    private LinearGradient starSelectedGradient;
    private Paint getStarGradientPaint(RectF bounds, float alpha) {
        if (starSelectedGradientPaint == null) starSelectedGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (starSelectedGradientMatrix == null) starSelectedGradientMatrix = new Matrix();
        if (starSelectedGradient == null) {
            final int color = Theme.getColor(Theme.key_reactionStarSelector, resourcesProvider);
            starSelectedGradient = new LinearGradient(0, 0, dp(64), 0, new int[] { color, Theme.multAlpha(color, 0) }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
            starSelectedGradientPaint.setShader(starSelectedGradient);
        }
        starSelectedGradientMatrix.reset();
        starSelectedGradientMatrix.postTranslate(bounds.left, bounds.top);
        starSelectedGradient.setLocalMatrix(starSelectedGradientMatrix);
        starSelectedGradientPaint.setAlpha((int) (0xFF * alpha));
        return starSelectedGradientPaint;
    }

}