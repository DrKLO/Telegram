package org.telegram.ui.Stories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CanvasButton;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

public class DialogStoriesCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public final static int TYPE_DIALOGS = 0;
    public final static int TYPE_ARCHIVE= 1;
    private static final float COLLAPSED_DIS = 18;
    private static final float ITEM_WIDTH = 70;
    private static final int FAKE_TOP_PADDING = 4;

    private final int type;
    public final static int HEIGHT_IN_DP = 81;
    private final Drawable addNewStoryDrawable;
    private final DefaultItemAnimator miniItemAnimator;
    private int addNewStoryLastColor;
    int currentAccount;
    public RecyclerListView recyclerListView;
    public RadialProgress radialProgress;

    RecyclerListView listViewMini;
    StoriesController storiesController;
    ArrayList<Item> oldItems = new ArrayList<>();
    ArrayList<Item> oldMiniItems = new ArrayList<>();
    ArrayList<Item> items = new ArrayList<>();
    ArrayList<Item> miniItems = new ArrayList<>();
    Adapter adapter = new Adapter(false);
    Adapter miniAdapter = new Adapter(true);
    Paint grayPaint = new Paint();
    Paint addCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    CanvasButton miniItemsClickArea = new CanvasButton(this);

    private HintView2 premiumHint;

    public static final int COLLAPSED_SIZE = 28;

    boolean updateOnIdleState;
    private int clipTop;

    private final static int EXPANDED_STATE = 0;
    private final static int TRANSITION_STATE = 1;
    private final static int COLLAPSED_STATE = 2;
    public int currentCellWidth;

    float collapsedProgress = -1;

    int currentState = -1;

    ArrayList<StoryCell> viewsDrawInParent = new ArrayList<>();
    ArrayList<Long> animateToDialogIds = new ArrayList<>();
    DefaultItemAnimator itemAnimator;
    LinearLayoutManager layoutManager;
    AnimatedTextView titleView;
    boolean drawCircleForce;
    ArrayList<Runnable> afterNextLayout = new ArrayList<>();
    private float collapsedProgress1 = -1;
    private float collapsedProgress2;
    BaseFragment fragment;
    private CharSequence currentTitle;
    private boolean hasOverlayText;
    private int overlayTextId;
    private SpannableStringBuilder uploadingString;
    private ValueAnimator textAnimator;
    private Runnable animationRunnable;
    public boolean allowGlobalUpdates = true;
    private boolean lastUploadingCloseFriends;
    private float overscrollPrgoress;
    private int overscrollSelectedPosition;
    private StoryCell overscrollSelectedView;
    private ActionBar actionBar;
    private StoriesUtilities.EnsureStoryFileLoadedObject globalCancelable;

    public DialogStoriesCell(@NonNull Context context, BaseFragment fragment, int currentAccount, int type) {
        super(context);
        this.type = type;
        this.currentAccount = currentAccount;
        this.fragment = fragment;
        storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
        recyclerListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (viewsDrawInParent.contains(child)) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                for (int i = 0; i < afterNextLayout.size(); i++) {
                    afterNextLayout.get(i).run();
                }
                afterNextLayout.clear();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && (collapsedProgress1 > 0.2f || DialogStoriesCell.this.getAlpha() == 0)) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        recyclerListView.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setClipChildren(false);
        miniItemsClickArea.setDelegate(() -> {
           onMiniListClicked();
        });
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                invalidate();
                checkLoadMore();
                if (premiumHint != null) {
                    premiumHint.hide();
                }
            }
        });
        itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setDurations(150);
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        RecyclerListView.OnItemClickListener itemClickListener = (view, position) -> {
            StoryCell cell = (StoryCell) view;
            openStoryForCell(cell, false);
        };
        recyclerListView.setOnItemClickListener(itemClickListener);
        recyclerListView.setOnItemLongClickListener((view, position) -> {
            if (collapsedProgress == 0 && overscrollPrgoress == 0) {
                onUserLongPressed(view, ((StoryCell) view).dialogId);
            }
            return false;
        });

        recyclerListView.setAdapter(adapter);
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, FAKE_TOP_PADDING, 0, 0));

        titleView = new AnimatedTextView(getContext(), true, true, false);
        titleView.setGravity(Gravity.LEFT);
        titleView.setTextColor(getTextColor());
        titleView.setEllipsizeByGradient(true);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        titleView.setTextSize(AndroidUtilities.dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        titleView.setAlpha(0f);

        grayPaint.setColor(0xffD5DADE);
        grayPaint.setStyle(Paint.Style.STROKE);
        grayPaint.setStrokeWidth(AndroidUtilities.dp(1));
        addNewStoryDrawable = ContextCompat.getDrawable(getContext(), R.drawable.msg_mini_addstory);

        listViewMini = new RecyclerListView(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                viewsDrawInParent.clear();
                for (int i = 0; i < getChildCount(); i++) {
                    StoryCell storyCell = (StoryCell) getChildAt(i);
                    storyCell.position = getChildAdapterPosition(storyCell);
                    storyCell.drawInParent = true;
                    storyCell.isFirst = storyCell.position == 0;
                    storyCell.isLast = storyCell.position == miniItems.size() - 1;
                    viewsDrawInParent.add(storyCell);
                }

                Collections.sort(viewsDrawInParent, comparator);
                for (int i = 0; i < viewsDrawInParent.size(); i++) {
                    StoryCell cell = viewsDrawInParent.get(i);
                    int restoreCount = canvas.save();
                    canvas.translate(cell.getX(), cell.getY());
                    if (cell.getAlpha() != 1f) {
                        canvas.saveLayerAlpha((float) -AndroidUtilities.dp(4), -AndroidUtilities.dp(4), AndroidUtilities.dp(50), AndroidUtilities.dp(50), (int) (255 * cell.getAlpha()), Canvas.ALL_SAVE_FLAG);
                    }
                    canvas.scale(cell.getScaleX(), cell.getScaleY(), AndroidUtilities.dp(14), cell.getCy());
                    cell.draw(canvas);
                    canvas.restoreToCount(restoreCount);
                }
            }

            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
                if (premiumHint != null) {
                    premiumHint.hide();
                }
            }


            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return false;
            }
        };
        listViewMini.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        listViewMini.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildLayoutPosition(view);
                outRect.setEmpty();
                if (p == 1) {
                    outRect.left = -AndroidUtilities.dp(85) + AndroidUtilities.dp(29 + COLLAPSED_DIS - 14);
                } else if (p == 2) {
                    outRect.left = -AndroidUtilities.dp(85) + AndroidUtilities.dp(29 + COLLAPSED_DIS - 14);
                }
            }
        });
        miniItemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return 0.6f;
            }
        };
        miniItemAnimator.setDelayAnimations(false);
        miniItemAnimator.setSupportsChangeAnimations(false);
        listViewMini.setItemAnimator(miniItemAnimator);
        listViewMini.setAdapter(miniAdapter);
        listViewMini.setClipChildren(false);
        addView(listViewMini,  LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, FAKE_TOP_PADDING, 0, 0));
        setClipChildren(false);
        setClipToPadding(false);

        updateItems(false, false);
    }

    public void onMiniListClicked() {

    }

    private void openStoryForCell(StoryCell cell, boolean overscroll) {
        if (cell == null) {
            return;
        }
        if (cell.isSelf && !storiesController.hasSelfStories()) {
            if (!MessagesController.getInstance(currentAccount).storiesEnabled()) {
                showPremiumHint();
            } else {
                openStoryRecorder();
            }
            return;
        }
        if (!storiesController.hasStories(cell.dialogId) && !storiesController.hasUploadingStories(cell.dialogId)) {
            return;
        }
        TL_stories.PeerStories userStories = storiesController.getStories(cell.dialogId);
        long startFromDialogId = cell.dialogId;
        if (globalCancelable != null) {
            globalCancelable.cancel();
            globalCancelable = null;
        }

        Runnable runnable = () -> {
            if (fragment == null || fragment.getParentActivity() == null) {
                return;
            }
            int position = cell.position;

            ArrayList<Long> peerIds = new ArrayList<>();
            boolean allStoriesIsRead = true;

            for (int i = 0; i < items.size(); i++) {
                long dialogId = items.get(i).dialogId;
                if (dialogId != UserConfig.getInstance(currentAccount).clientUserId && storiesController.hasUnreadStories(dialogId)) {
                    allStoriesIsRead = false;
                    break;
                }
            }

            boolean onlySelfStories = false;
            boolean onlyUnreadStories = false;
            if (cell.isSelf && (!allStoriesIsRead || items.size() == 1)) {
                peerIds.add(cell.dialogId);
                onlySelfStories = true;
            } else {
                boolean isUnreadStory = !cell.isSelf && storiesController.hasUnreadStories(cell.dialogId);
                if (isUnreadStory) {
                    onlyUnreadStories = true;
                    for (int i = 0; i < items.size(); i++) {
                        long dialogId = items.get(i).dialogId;
                        if (!cell.isSelf && storiesController.hasUnreadStories(dialogId)) {
                            peerIds.add(dialogId);
                        }
                        if (dialogId == cell.dialogId) {
                            position = peerIds.size() - 1;
                        }
                    }
                } else {
                    for (int i = 0; i < items.size(); i++) {
                        long dialogId = items.get(i).dialogId;
                        if (storiesController.hasStories(dialogId)) {
                            peerIds.add(items.get(i).dialogId);
                        } else if (i <= position) {
                            position--;
                        }
                    }
                }
            }
            StoryViewer storyViewer = fragment.getOrCreateStoryViewer();
            storyViewer.doOnAnimationReady(() -> {
                storiesController.setLoading(startFromDialogId, false);
            });
            boolean finalOnlySelfStories = onlySelfStories;
            storyViewer.open(getContext(), null, peerIds, position, null, null, StoriesListPlaceProvider.of(recyclerListView).with(forward -> {
                if (finalOnlySelfStories) {
                    return;
                }
                if (forward) {
                    boolean hidden = type == TYPE_ARCHIVE;
                    storiesController.loadNextStories(hidden);
                }
            }).setPaginationParaments(type == TYPE_ARCHIVE, onlyUnreadStories, onlySelfStories), false);
        };
        if (overscroll) {
            runnable.run();
        } else {
            globalCancelable = cell.cancellable = StoriesUtilities.ensureStoryFileLoaded(userStories, runnable);
            if (globalCancelable != null) {
                storiesController.setLoading(cell.dialogId, true);
            }
        }

    }

    private void checkLoadMore() {
        if (layoutManager.findLastVisibleItemPosition() + 10 > items.size()) {
            boolean hidden = type == TYPE_ARCHIVE;
            storiesController.loadNextStories(hidden);
        }
    }

    public void updateItems(boolean animated, boolean force) {
        if ((currentState == TRANSITION_STATE || overscrollPrgoress != 0) && !force) {
            updateOnIdleState = true;
            return;
        }
        oldItems.clear();
        oldItems.addAll(items);
        oldMiniItems.clear();
        oldMiniItems.addAll(miniItems);
        items.clear();
        if (type != TYPE_ARCHIVE) {
            items.add(new Item(UserConfig.getInstance(currentAccount).getClientUserId()));
        }

        ArrayList<TL_stories.PeerStories> allStories = type == TYPE_ARCHIVE ? storiesController.getHiddenList() : storiesController.getDialogListStories();
        for (int i = 0; i < allStories.size(); i++) {
            long dialogId = DialogObject.getPeerDialogId(allStories.get(i).peer);
            if (dialogId != UserConfig.getInstance(currentAccount).getClientUserId()) {
                items.add(new Item(dialogId));
            }
        }
        int size = items.size();
        if (!storiesController.hasSelfStories()) {
            size--;
        }
        int totalCount;
        boolean hidden = type == TYPE_ARCHIVE;
        totalCount = Math.max(1, Math.max(storiesController.getTotalStoriesCount(hidden), size));

        if (storiesController.hasOnlySelfStories()) {
            if (storiesController.hasUploadingStories(UserConfig.getInstance(currentAccount).getClientUserId())) {
                String str = LocaleController.getString("UploadingStory", R.string.UploadingStory);
                int index = str.indexOf("…");
                if (index > 0) {
                    if (uploadingString == null) {
                        SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(str);
                        UploadingDotsSpannable dotsSpannable = new UploadingDotsSpannable();
                        spannableStringBuilder.setSpan(dotsSpannable, spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
                        dotsSpannable.setParent(titleView, true);
                        uploadingString = spannableStringBuilder;
                    }
                    currentTitle = uploadingString;
                } else {
                    currentTitle = str;
                }
            } else {
                currentTitle = LocaleController.getString("MyStory", R.string.MyStory);
            }
        } else {
            currentTitle = LocaleController.formatPluralString("Stories", totalCount);
        }

        if (!hasOverlayText) {
            titleView.setText(currentTitle, animated);
        }

        miniItems.clear();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).dialogId == UserConfig.getInstance(currentAccount).clientUserId && !shouldDrawSelfInMini()) {
                continue;
            } else {
                miniItems.add(items.get(i));
                if (miniItems.size() >= 3) {
                    break;
                }
            }
        }

        if (animated) {
            if (currentState == COLLAPSED_STATE) {
                listViewMini.setItemAnimator(miniItemAnimator);
                recyclerListView.setItemAnimator(null);
            } else {
                recyclerListView.setItemAnimator(itemAnimator);
                listViewMini.setItemAnimator(null);
            }
        } else {
            recyclerListView.setItemAnimator(null);
            listViewMini.setItemAnimator(null);
        }
        adapter.setItems(oldItems, items);
        miniAdapter.setItems(oldMiniItems, miniItems);

        oldItems.clear();
        invalidate();
    }

    private boolean shouldDrawSelfInMini() {
        long dialogId = UserConfig.getInstance(currentAccount).clientUserId;
        if (storiesController.hasUnreadStories(dialogId) || (storiesController.hasSelfStories() && storiesController.getDialogListStories().size() <= 3)) {
            return true;
        }
        return false;
    }

    Comparator<StoryCell> comparator = (o1, o2) -> o2.position - o1.position;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        if (clipTop > 0) {
            canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
        }
        float y = AndroidUtilities.lerp(0, getMeasuredHeight() - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(4), collapsedProgress1);
        recyclerListView.setTranslationY(y);
        listViewMini.setTranslationY(y);
        listViewMini.setTranslationX(AndroidUtilities.dp(68));

        for (int i = 0; i < viewsDrawInParent.size(); i++) {
            viewsDrawInParent.get(i).drawInParent = false;
        }
        viewsDrawInParent.clear();
        int animateFromPosition = -1;
        boolean crossfade = false;
        if (currentState == TRANSITION_STATE && !animateToDialogIds.isEmpty()) {
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                StoryCell cell = (StoryCell) recyclerListView.getChildAt(i);
                if (cell.dialogId == animateToDialogIds.get(0)) {
                    animateFromPosition = recyclerListView.getChildAdapterPosition(cell);
                }
            }
        } else if (currentState == COLLAPSED_STATE) {
            animateFromPosition = 0;
        }
        float lastViewRight = 0;
        if (currentState >= 0 && currentState != COLLAPSED_STATE) {
            if (animateFromPosition == -1) {
                crossfade = true;
                animateFromPosition = layoutManager.findFirstCompletelyVisibleItemPosition();
                if (animateFromPosition == -1) {
                    animateFromPosition = layoutManager.findFirstVisibleItemPosition();
                }
            }

            recyclerListView.setAlpha(1f - Utilities.clamp(collapsedProgress / K, 1f, 0));
            overscrollSelectedPosition = -1;
            if (overscrollPrgoress != 0) {
                int minUnreadPosition = -1;
                int minPosition = -1;
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    View child = recyclerListView.getChildAt(i);
                    if (child.getX() < 0 || child.getX() + child.getMeasuredWidth() > getMeasuredWidth()) {
                        continue;
                    }
                    int position = recyclerListView.getChildAdapterPosition(child);
                    if (position < 0) {
                        continue;
                    }
//                    if ((minUnreadPosition == -1 || position < minUnreadPosition) &&  storiesController.hasUnreadStories(items.get(position).dialogId)) {
//                        minUnreadPosition = position;
//                    }

                    if ((minPosition == -1 || position < minPosition) && items.get(position).dialogId != UserConfig.getInstance(currentAccount).clientUserId) {
                        minPosition = position;
                        overscrollSelectedView = (StoryCell) child;
                    }
                }
                overscrollSelectedPosition = minPosition;
            }
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                StoryCell cell = (StoryCell) recyclerListView.getChildAt(i);
                cell.setProgressToCollapsed(collapsedProgress, collapsedProgress2, overscrollPrgoress, overscrollSelectedPosition == cell.position);
                float ovescrollSelectProgress = Utilities.clamp((overscrollPrgoress - 0.5f) / 0.5f, 1f, 0f);
                float overScrollOffset = AndroidUtilities.dp(16) * ovescrollSelectProgress;
                float overscrollAlpha = (float) (0.5 + 0.5f * (1f - ovescrollSelectProgress));
                if (collapsedProgress > 0) {
                    float toX = 0;
                    int adapterPosition = recyclerListView.getChildAdapterPosition(cell);
                    boolean drawInParent = adapterPosition >= animateFromPosition && adapterPosition <= animateFromPosition + 2;
                    if (crossfade) {
                        int p = adapterPosition - animateFromPosition;
                        if (p >= 0 && p < animateToDialogIds.size()) {
                            long dialogId = animateToDialogIds.get(p);
                            cell.setCrossfadeTo(dialogId);
                        } else {
                            cell.setCrossfadeTo(-1);
                        }
                    } else {
                        cell.setCrossfadeTo(-1);
                    }
                    cell.drawInParent = drawInParent;
                    cell.isFirst = adapterPosition == animateFromPosition;
                    cell.isLast = adapterPosition >= animateFromPosition + animateToDialogIds.size() - 1;
                    if (adapterPosition <= animateFromPosition) {
                        toX = 0;
                    } else if (adapterPosition == animateFromPosition + 1) {
                        toX = AndroidUtilities.dp(COLLAPSED_DIS);
                    } else {
                        toX = AndroidUtilities.dp(COLLAPSED_DIS) * 2;
                    }
                    toX += AndroidUtilities.dp(68);
                    cell.setTranslationX(AndroidUtilities.lerp(0, toX - cell.getLeft(), CubicBezierInterpolator.EASE_OUT.getInterpolation(collapsedProgress)));
                    if (drawInParent) {
                        viewsDrawInParent.add(cell);
                    }
                } else if (recyclerListView.getItemAnimator() == null || !recyclerListView.getItemAnimator().isRunning()) {
                    if (overscrollPrgoress > 0) {
                        if (cell.position < overscrollSelectedPosition) {
                            cell.setTranslationX(-overScrollOffset);
                            cell.setTranslationY(0);
                            cell.setAlpha(overscrollAlpha);
                        } else if (cell.position > overscrollSelectedPosition) {
                            cell.setTranslationX(overScrollOffset);
                            cell.setTranslationY(0);
                            cell.setAlpha(overscrollAlpha);
                        } else {
                            cell.setTranslationX(0);
                            cell.setTranslationY(-overScrollOffset / 2f);
                            cell.setAlpha(1f);
                        }
                    } else {
                        cell.setTranslationX(0);
                        cell.setTranslationY(0);
                        cell.setAlpha(1f);
                    }
                }
                if (cell.drawInParent) {
                    float right = recyclerListView.getX() + cell.getX() + cell.getMeasuredWidth() / 2f + AndroidUtilities.dp(ITEM_WIDTH) / 2f;
                    if (lastViewRight == 0 || right > lastViewRight) {
                        lastViewRight = right;
                    }
                }
            }
        } else {
            for (int i = 0; i < listViewMini.getChildCount(); i++) {
                StoryCell cell = (StoryCell) listViewMini.getChildAt(i);
                float right = listViewMini.getX() + cell.getX() + cell.getMeasuredWidth();
                if (lastViewRight == 0 || right > lastViewRight) {
                    lastViewRight = right;
                }
            }
        }

        if (premiumHint != null) {
            float x = AndroidUtilities.lerp(37 - 8, 68 + 14 - 8, CubicBezierInterpolator.EASE_OUT.getInterpolation(collapsedProgress));
            if (recyclerListView.getChildCount() > 0) {
                x += recyclerListView.getChildAt(0).getLeft();
            }
            premiumHint.setJoint(0, x);
        }

        float progress = Math.min(collapsedProgress, collapsedProgress2);
        if (progress != 0) {
            float offset = (titleView.getMeasuredHeight() - titleView.getTextHeight()) / 2f;
            titleView.setTranslationY(y + AndroidUtilities.dp(14) - offset + AndroidUtilities.dp(FAKE_TOP_PADDING));
            int cellWidth = AndroidUtilities.dp(72);
            lastViewRight += -cellWidth + AndroidUtilities.dp(6) + getAvatarRight(cellWidth, collapsedProgress) + AndroidUtilities.dp(12);
            // float toX = AndroidUtilities.dp(28) * Math.min(1, animateToCount) + AndroidUtilities.dp(14) * Math.max(0, animateToCount - 1);
            titleView.setTranslationX(lastViewRight);
            titleView.getDrawable().setRightPadding(lastViewRight + actionBar.menu.getItemsMeasuredWidth(false) * progress);
            titleView.setAlpha(progress);
            titleView.setVisibility(View.VISIBLE);
        } else {
            titleView.setVisibility(View.GONE);
        }

        super.dispatchDraw(canvas);
        if (currentState >= 0 && currentState != COLLAPSED_STATE) {
            Collections.sort(viewsDrawInParent, comparator);
            for (int i = 0; i < viewsDrawInParent.size(); i++) {
                StoryCell cell = viewsDrawInParent.get(i);
                canvas.save();
                canvas.translate(recyclerListView.getX() + cell.getX(), recyclerListView.getY() + cell.getY());
                cell.draw(canvas);
                canvas.restore();
            }
        }
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateItems(false, false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
        ellipsizeSpanAnimator.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
        ellipsizeSpanAnimator.onDetachedFromWindow();
        if (globalCancelable != null) {
            globalCancelable.cancel();
            globalCancelable = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        titleView.setTextSize(AndroidUtilities.dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20));
        currentCellWidth = AndroidUtilities.dp(ITEM_WIDTH);
        AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85 + FAKE_TOP_PADDING), MeasureSpec.EXACTLY));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesUpdated) {
            if (allowGlobalUpdates) {
                updateItems(getVisibility() == View.VISIBLE, false);
                AndroidUtilities.runOnUIThread(() -> {
                    checkLoadMore();
                });
            }
        }
    }

    boolean collapsed;
    float K = 0.3f;
    ValueAnimator valueAnimator;

    public void setProgressToCollapse(float progress) {
        setProgressToCollapse(progress, true);
    }

    public void setProgressToCollapse(float progress, boolean animated) {
        if (collapsedProgress1 == progress) {
            return;
        }

        collapsedProgress1 = progress;
        checkCollapsedProgres();

        boolean newCollapsed = progress > K;
        if (newCollapsed != collapsed) {
            collapsed = newCollapsed;
            if (valueAnimator != null) {
                valueAnimator.removeAllListeners();
                valueAnimator.cancel();
                valueAnimator = null;
            }
            if (animated) {
                valueAnimator = ValueAnimator.ofFloat(collapsedProgress2, newCollapsed ? 1f : 0);
            } else {
                collapsedProgress2 = newCollapsed ? 1f : 0;
                checkCollapsedProgres();
            }
            if (valueAnimator != null) {
                valueAnimator.addUpdateListener(animation -> {
                    collapsedProgress2 = (float) animation.getAnimatedValue();
                    checkCollapsedProgres();
                });
                valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        collapsedProgress2 = newCollapsed ? 1f : 0;
                        checkCollapsedProgres();
                    }
                });
                valueAnimator.setDuration(450);
                valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                valueAnimator.start();
            }
        }
    }

    private void checkCollapsedProgres() {
        collapsedProgress = 1f - AndroidUtilities.lerp(1f - collapsedProgress1, 1f, 1f - collapsedProgress2);
        updateCollapsedProgress();

        int state = EXPANDED_STATE;
        if (collapsedProgress == 1f) {
            state = COLLAPSED_STATE;
        } else if (collapsedProgress != 0) {
            state = TRANSITION_STATE;
        }
        updateCurrentState(state);
        invalidate();
    }

    public float getCollapsedProgress() {
        return collapsedProgress;
    }

    public void updateCollapsedProgress() {

    }

    public void scrollToFirstCell() {
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    public void updateColors() {
        StoriesUtilities.updateColors();
        int color = getTextColor();

        titleView.setTextColor(color);
        AndroidUtilities.forEachViews(recyclerListView, view -> {
            StoryCell cell = (StoryCell) view;
            cell.invalidate();
            cell.textView.setTextColor(color);
        });
        AndroidUtilities.forEachViews(listViewMini, view -> {
            StoryCell cell = (StoryCell) view;
            cell.invalidate();
        });
    }

    private int getTextColor() {
        if (type == TYPE_DIALOGS) {
            return Theme.getColor(Theme.key_actionBarDefaultTitle);
        } else {
            return Theme.getColor(Theme.key_actionBarDefaultArchivedTitle);
        }
    }

    public boolean scrollTo(long currentDialogId) {
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).dialogId == currentDialogId) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            if (position < layoutManager.findFirstCompletelyVisibleItemPosition()) {
                layoutManager.scrollToPositionWithOffset(position, 0);
                return true;
            } else if (position > layoutManager.findLastCompletelyVisibleItemPosition()) {
                layoutManager.scrollToPositionWithOffset(position, 0, true);
                return true;
            }
        }
        return false;
    }

    public void afterNextLayout(Runnable r) {
        afterNextLayout.add(r);
    }

    public boolean isExpanded() {
        return currentState == EXPANDED_STATE || currentState == TRANSITION_STATE;
    }

    public boolean isFullExpanded() {
        return currentState == EXPANDED_STATE;
    }

    public boolean scrollToFirst() {
        if (layoutManager.findFirstVisibleItemPosition() != 0) {
            recyclerListView.smoothScrollToPosition(0);
            return true;
        }
        return false;
    }

    public void onUserLongPressed(View view, long dialogId) {

    }

    public void openStoryRecorder() {
        final StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
        if (storyLimit != null) {
            fragment.showDialog(new LimitReachedBottomSheet(fragment, getContext(), storyLimit.getLimitReachedType(), currentAccount, null));
            return;
        }

        StoryCell cell = null;
        for (int i = 0 ; i < recyclerListView.getChildCount(); i++) {
            StoryCell storyCell = (StoryCell) recyclerListView.getChildAt(i);
            if (storyCell.isSelf) {
                cell = storyCell;
                break;
            }
        }
        if (cell == null) {
            return;
        }
        StoryRecorder.getInstance(fragment.getParentActivity(), currentAccount)
                .open(StoryRecorder.SourceView.fromStoryCell(cell));
    }

    EllipsizeSpanAnimator ellipsizeSpanAnimator = new EllipsizeSpanAnimator(this);

    public void setTitleOverlayText(String titleOverlayText, int textId) {
        boolean hasEllipsizedText = false;
        if (titleOverlayText != null) {
            hasOverlayText = true;
            if (overlayTextId != textId) {
                overlayTextId = textId;
                String title = LocaleController.getString(titleOverlayText, textId);
                CharSequence textToSet = title;
                if (!TextUtils.isEmpty(title)) {
                    int index = TextUtils.indexOf(textToSet, "...");
                    if (index >= 0) {
                        SpannableString spannableString = SpannableString.valueOf(textToSet);
                        ellipsizeSpanAnimator.wrap(spannableString, index);
                        hasEllipsizedText = true;
                        textToSet = spannableString;
                    }
                }
                titleView.setText(textToSet, true);
            }
        } else {
            hasOverlayText = false;
            overlayTextId = 0;
            titleView.setText(currentTitle, true);
        }
        if (hasEllipsizedText) {
            ellipsizeSpanAnimator.addView(titleView);
        } else {
            ellipsizeSpanAnimator.removeView(titleView);
        }
    }

    public void setClipTop(int clipTop) {
        if (clipTop < 0) {
            clipTop = 0;
        }
        if (this.clipTop != clipTop) {
            this.clipTop = clipTop;
            invalidate();
        }
    }

    public void openSelfStories() {
        if (!storiesController.hasSelfStories()) {
            return;
        }
        ArrayList<Long> peerIds = new ArrayList<>();
        peerIds.add(UserConfig.getInstance(currentAccount).clientUserId);
        fragment.getOrCreateStoryViewer().open(getContext(), null, peerIds, 0, null, null, StoriesListPlaceProvider.of(listViewMini), false);
    }

    public void onResume() {
        storiesController.checkExpiredStories();
        for (int i = 0; i < items.size(); i++) {
            TL_stories.PeerStories stories = storiesController.getStories(items.get(i).dialogId);
            if (stories != null) {
                storiesController.preloadUserStories(stories);
            }
        }
    }

    public void setOverscoll(float storiesOverscroll) {
        overscrollPrgoress = storiesOverscroll / AndroidUtilities.dp(90);
        invalidate();
        recyclerListView.invalidate();
        if (overscrollPrgoress != 0) {
            setClipChildren(false);
            recyclerListView.setClipChildren(false);
            ((ViewGroup) getParent()).setClipChildren(false);
        } else {
            ((ViewGroup) getParent()).setClipChildren(true);
        }
    }

    public void openOverscrollSelectedStory() {
        openStoryForCell(overscrollSelectedView, true);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public void setActionBar(ActionBar actionBar) {
        this.actionBar = actionBar;
    }

    public float overscrollProgress() {
        return overscrollPrgoress;
    }

    private class Adapter extends AdapterWithDiffUtils {

        boolean mini;

        public Adapter(boolean mini) {
            this.mini = mini;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            StoryCell storyCell = new StoryCell(parent.getContext());
            storyCell.mini = mini;
            if (mini) {
                storyCell.setProgressToCollapsed(1f, 1f, 0f, false);
            }
            return new RecyclerListView.Holder(storyCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            StoryCell cell = (StoryCell) holder.itemView;
            cell.position = position;
            if (mini) {
                cell.setDialogId(miniItems.get(position).dialogId);
            } else {
                cell.setDialogId(items.get(position).dialogId);
            }
        }

        @Override
        public int getItemCount() {
            return mini ? miniItems.size() : items.size();
        }


        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    private class Item extends AdapterWithDiffUtils.Item {

        final long dialogId;

        public Item(long dialogId) {
            super(0, false);
            this.dialogId = dialogId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Item)) return false;
            Item item = (Item) o;
            return dialogId == item.dialogId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialogId);
        }
    }


    public StoryCell findStoryCell(long dialogId) {
        RecyclerListView parent = recyclerListView;
        if (currentState == COLLAPSED_STATE) {
            parent = listViewMini;
        }
        for (int i = 0; i < parent.getChildCount(); ++i) {
            View child = parent.getChildAt(i);
            if (child instanceof StoryCell) {
                StoryCell storyCell = (StoryCell) child;
                if (storyCell.dialogId == dialogId) {
                    return storyCell;
                }
            }
        }
        return null;
    }



    public class StoryCell extends FrameLayout {
        public boolean drawInParent;
        public int position;
        public boolean isLast;
        public boolean isFirst;
        public StoriesUtilities.EnsureStoryFileLoadedObject cancellable;
        TLRPC.User user;
        TLRPC.Chat chat;

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        public ImageReceiver avatarImage = new ImageReceiver(this);
        public ImageReceiver crossfageToAvatarImage = new ImageReceiver(this);
        AvatarDrawable crossfadeAvatarDrawable = new AvatarDrawable();
        public boolean drawAvatar = true;
        FrameLayout textViewContainer;
        SimpleTextView textView;
        long dialogId;
        boolean isSelf;
        boolean isFail;
        boolean crossfadeToDialog;
        long crossfadeToDialogId;

        float progressToCollapsed;
        float progressToCollapsed2;
        private float cx, cy;
        private boolean mini;
        public final StoriesUtilities.AvatarStoryParams params = new StoriesUtilities.AvatarStoryParams(true);
        float textAlpha = 1f;
        float textAlphaTransition = 1f;

        public RadialProgress radialProgress;
        private Drawable verifiedDrawable;
        private float bounceScale = 1f;
        private boolean isUploadingState;
        private float overscrollProgress;
        private boolean selectedForOverscroll;
        boolean progressWasDrawn;

        private final AnimatedFloat failT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        public StoryCell(Context context) {
            super(context);
            params.isArchive = type == TYPE_ARCHIVE;
            avatarImage.setInvalidateAll(true);
            avatarImage.setAllowLoadingOnAttachedOnly(true);

            textViewContainer = new FrameLayout(getContext());
            textViewContainer.setClipChildren(false);
            if (!mini) {
                setClipChildren(false);
            }
            createTextView();
            addView(textViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            avatarImage.setRoundRadius(AndroidUtilities.dp(48) / 2);
            crossfageToAvatarImage.setRoundRadius(AndroidUtilities.dp(48) / 2);
        }

        private void createTextView() {
            textView = new SimpleTextView(getContext());
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(11);
            textView.setTextColor(getTextColor());
            NotificationCenter.listenEmojiLoading(textView);
           // textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setMaxLines(1);
            //textView.setSingleLine(true);

            textViewContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 1, 0));
            avatarImage.setRoundRadius(AndroidUtilities.dp(48) / 2);
            crossfageToAvatarImage.setRoundRadius(AndroidUtilities.dp(48) / 2);
        }

        public void setDialogId(long dialogId) {
            boolean animated = this.dialogId == dialogId;
            if (!animated) {
                if (cancellable != null) {
                    storiesController.setLoading(this.dialogId, false);
                    cancellable.cancel();
                    cancellable = null;
                }
            }
            this.dialogId = dialogId;

            isSelf = dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
            isFail = storiesController.isLastUploadingFailed(dialogId);
            TLObject object;
            if (dialogId > 0) {
                object = user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                chat = null;
            } else {
                object = chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                user = null;
            }

            if (object == null) {
                textView.setText("");
                avatarImage.clearImage();
                return;
            }
            avatarDrawable.setInfo(object);
            avatarImage.setForUserOrChat(object, avatarDrawable);
            if (mini) {
                return;
            }
            textView.setRightDrawable(null);
            if (storiesController.isLastUploadingFailed(dialogId)) {
                textView.setText(LocaleController.getString("FailedStory", R.string.FailedStory));
                isUploadingState = false;
            } else if (!Utilities.isNullOrEmpty(storiesController.getUploadingStories(dialogId))) {
                StoriesUtilities.applyUploadingStr(textView, true, false);
                isUploadingState = true;
            } else if (storiesController.getEditingStory(dialogId) != null) {
                StoriesUtilities.applyUploadingStr(textView, true, false);
                isUploadingState = true;
            } else {
                if (isSelf) {
                    if (animated && isUploadingState && !mini) {
                        View oldTextView = textView;
                        createTextView();
                        if (textAnimator != null) {
                            textAnimator.cancel();
                            textAnimator = null;
                        }
                        textAnimator = ValueAnimator.ofFloat(0, 1f);
                        textAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                                float progress = (float) animation.getAnimatedValue();
                                oldTextView.setAlpha(1f - progress);
                                oldTextView.setTranslationY(-AndroidUtilities.dp(5) * progress);

                                textView.setAlpha(progress);
                                textView.setTranslationY(AndroidUtilities.dp(5) * (1f - progress));
                            }
                        });
                        textAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                textAnimator = null;
                                AndroidUtilities.removeFromParent(oldTextView);
                            }
                        });
                        textAnimator.setDuration(150);
                        textView.setAlpha(0);
                        textView.setTranslationY(AndroidUtilities.dp(5));
                        animationRunnable = () -> {
                            if (textAnimator != null) {
                                textAnimator.start();
                            }
                            animationRunnable = null;
                        };
                    }
                    AndroidUtilities.runOnUIThread(animationRunnable, 500);
                    isUploadingState = false;
                    textView.setText(LocaleController.getString("MyStory", R.string.MyStory));//, animated);
                } else if (user != null) {
                    String name = user.first_name == null ? "" : user.first_name.trim();
                    int index = name.indexOf(" ");
                    if (index > 0) {
                        name = name.substring(0, index);
                    }
                    if (user.verified) {
                        if (verifiedDrawable == null) {
                            verifiedDrawable = createVerifiedDrawable();
                        }
                        CharSequence text = name;
                        text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
                        textView.setText(text);
                        textView.setRightDrawable(verifiedDrawable);
                    } else {
                        CharSequence text = name;
                        text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
                        textView.setText(text);
                        textView.setRightDrawable(null);
                    }//, false);
                } else {
                    CharSequence text = chat.title;
                    text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
                    textView.setText(text);//, false);
                    textView.setRightDrawable(null);
                }
            }

        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(mini ? AndroidUtilities.dp(ITEM_WIDTH) : currentCellWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(HEIGHT_IN_DP), MeasureSpec.EXACTLY));
        }

        float getCy() {
            float size = AndroidUtilities.dp(48);
            float collapsedSize = AndroidUtilities.dp(COLLAPSED_SIZE);

            float finalSize = AndroidUtilities.lerp(size, collapsedSize, progressToCollapsed);
            float radius = finalSize / 2f;

            float y = AndroidUtilities.lerp(AndroidUtilities.dp(5), (ActionBar.getCurrentActionBarHeight() - collapsedSize) / 2f, collapsedProgress1);
            return y + radius;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float size = AndroidUtilities.dp(48);
            float collapsedSize = AndroidUtilities.dp(COLLAPSED_SIZE);
            float overscrollSize = AndroidUtilities.dp(8) *  Utilities.clamp(overscrollPrgoress / 0.5f, 1f, 0);
            if (selectedForOverscroll) {
                overscrollSize += AndroidUtilities.dp(16) * Utilities.clamp((overscrollPrgoress - 0.5f) / 0.5f, 1f, 0f);
            }

            float finalSize = AndroidUtilities.lerp(size + overscrollSize, collapsedSize, progressToCollapsed);
            float radius = finalSize / 2f;

            float fromX = getMeasuredWidth() / 2f - radius;
            float x = AndroidUtilities.lerp(fromX, 0, progressToCollapsed);
            float y = AndroidUtilities.lerp(AndroidUtilities.dp(5), (ActionBar.getCurrentActionBarHeight() - collapsedSize) / 2f, progressToCollapsed);

            float progressHalf = Utilities.clamp(progressToCollapsed / 0.5f, 1f, 0f);

            params.drawSegments = true;
            if (!params.forceAnimateProgressToSegments) {
                params.progressToSegments = 1f - collapsedProgress2;
            }
            params.originalAvatarRect.set(x, y, x + finalSize, y + finalSize);
            avatarImage.setAlpha(1f);
            avatarImage.setRoundRadius((int) radius);

            cx = x + radius;
            cy = y + radius;
            if (type == TYPE_DIALOGS) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));
            } else {
                backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultArchived));
            }
            if (progressToCollapsed != 0) {
                canvas.drawCircle(cx, cy, radius + AndroidUtilities.dp(3), backgroundPaint);
            }

            canvas.save();
            canvas.scale(bounceScale, bounceScale, cx, cy);
            if (radialProgress == null) {
                radialProgress = DialogStoriesCell.this.radialProgress;
            }
            ArrayList<StoriesController.UploadingStory> uploadingOrEditingStories = storiesController.getUploadingAndEditingStories(dialogId);
            boolean hasUploadingStories = (uploadingOrEditingStories != null && !uploadingOrEditingStories.isEmpty());
            boolean drawProgress = hasUploadingStories || (progressWasDrawn && radialProgress != null && radialProgress.getAnimatedProgress() < 0.98f);
            if (drawProgress) {
                float uploadingProgress = 0;
                boolean closeFriends;
                if (!hasUploadingStories) {
                    uploadingProgress = 1f;
                    closeFriends = lastUploadingCloseFriends;
                } else {
                    for (int i = 0; i < uploadingOrEditingStories.size(); i++) {
                        uploadingProgress += uploadingOrEditingStories.get(i).progress;
                    }
                    uploadingProgress = uploadingProgress / uploadingOrEditingStories.size();
                    lastUploadingCloseFriends = closeFriends = uploadingOrEditingStories.get(uploadingOrEditingStories.size() - 1).isCloseFriends();
                }
                invalidate();
                if (radialProgress == null) {
                    if (DialogStoriesCell.this.radialProgress != null) {
                        radialProgress = DialogStoriesCell.this.radialProgress;
                    } else {
                        DialogStoriesCell.this.radialProgress = radialProgress = new RadialProgress(this);
                        radialProgress.setBackground(null, true, false);
                    }
                }
                if (drawAvatar) {
                    canvas.save();
                    canvas.scale(params.getScale(), params.getScale(), params.originalAvatarRect.centerX(), params.originalAvatarRect.centerY());
                    avatarImage.setImageCoords(params.originalAvatarRect);
                    avatarImage.draw(canvas);
                    canvas.restore();
                }
                radialProgress.setDiff(0);
                Paint paint = closeFriends ?
                        StoriesUtilities.getCloseFriendsPaint(avatarImage) :
                        StoriesUtilities.getUnreadCirclePaint(avatarImage, true);
                paint.setAlpha(255);
                radialProgress.setPaint(paint);
                radialProgress.setProgressRect(
                        (int) (avatarImage.getImageX() - AndroidUtilities.dp(3)), (int) (avatarImage.getImageY() - AndroidUtilities.dp(3)),
                        (int) (avatarImage.getImageX2() + AndroidUtilities.dp(3)), (int) (avatarImage.getImageY2() + AndroidUtilities.dp(3))
                );
                radialProgress.setProgress(Utilities.clamp(uploadingProgress, 1f, 0), progressWasDrawn);
                if (avatarImage.getVisible()) {
                    radialProgress.draw(canvas);
                }
                progressWasDrawn = true;
                drawCircleForce = true;
                invalidate();
            } else {
                float failT = this.failT.set(isFail);
                if (drawAvatar) {
                    if (progressWasDrawn) {
                        animateBounce();
                        params.forceAnimateProgressToSegments = true;
                        params.progressToSegments = 0f;
                        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
                        valueAnimator.addUpdateListener(animation -> {
                            params.progressToSegments = AndroidUtilities.lerp(0, 1f - collapsedProgress2, (float) animation.getAnimatedValue());
                            invalidate();
                        });
                        valueAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                params.forceAnimateProgressToSegments = false;
                            }
                        });
                        valueAnimator.setDuration(100);
                        valueAnimator.start();
                    }
                    failT *= params.progressToSegments;

                    params.animate = !progressWasDrawn;
                    params.progressToArc = getArcProgress(cx, radius);
                    params.isLast = isLast;
                    params.isFirst = isFirst;
                    params.alpha = 1f - failT;

                    if (!isSelf && crossfadeToDialog) {
                        params.crossfadeToDialog = crossfadeToDialogId;
                        params.crossfadeToDialogProgress = progressToCollapsed2;
                    } else {
                        params.crossfadeToDialog = 0;
                    }
                    if (isSelf) {
                        StoriesUtilities.drawAvatarWithStory(dialogId, canvas, avatarImage, storiesController.hasSelfStories(), params);
                    } else {
                        StoriesUtilities.drawAvatarWithStory(dialogId, canvas, avatarImage, storiesController.hasStories(dialogId), params);
                    }


                    if (failT > 0) {
                        final Paint paint = StoriesUtilities.getErrorPaint(avatarImage);
                        paint.setStrokeWidth(AndroidUtilities.dp(2));
                        paint.setAlpha((int) (0xFF * failT));
                        canvas.drawCircle(x + finalSize / 2, y + finalSize / 2, (finalSize / 2 + AndroidUtilities.dp(4)) * params.getScale(), paint);
                    }
                }
                progressWasDrawn = false;
                if (drawAvatar) {
                    canvas.save();
                    float s = 1f - progressHalf;
                    canvas.scale(s, s, cx + AndroidUtilities.dp(16), cy + AndroidUtilities.dp(16));
                    drawPlus(canvas, cx, cy, 1f);
                    drawFail(canvas, cx, cy, failT);
                    canvas.restore();
                }
            }
            canvas.restore();

            if (crossfadeToDialog && progressToCollapsed2 > 0) {
                crossfageToAvatarImage.setImageCoords(x, y, finalSize, finalSize);
                crossfageToAvatarImage.setAlpha(progressToCollapsed2);
                crossfageToAvatarImage.draw(canvas);
            }
            textViewContainer.setTranslationY(y + finalSize + AndroidUtilities.dp(7) * (1f - progressToCollapsed));
            textViewContainer.setTranslationX(x - fromX);
            if (!mini) {
                float p;
                if (isSelf) {
                    textAlpha = 1f;
                } else {
                    if (params.progressToSate != 1f) {
                        p = params.currentState == StoriesUtilities.STATE_READ ? params.progressToSate : (1f - params.progressToSate);
                    } else {
                        p = params.currentState == StoriesUtilities.STATE_READ ? 1f : 0f;
                    }
                    textAlpha = params.globalState == StoriesUtilities.STATE_READ ? 0.7f : 1f;//AndroidUtilities.lerp(1f, 0.7f, p);
                }
                textViewContainer.setAlpha(textAlphaTransition * textAlpha);
            }
            super.dispatchDraw(canvas);
        }

        private void animateBounce() {
            AnimatorSet animatorSet = new AnimatorSet();
            ValueAnimator inAnimator = ValueAnimator.ofFloat(1, 1.05f);
            inAnimator.setDuration(100);
            inAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);

            ValueAnimator outAnimator = ValueAnimator.ofFloat(1.05f, 1f);
            outAnimator.setDuration(250);
            outAnimator.setInterpolator(new OvershootInterpolator());

            ValueAnimator.AnimatorUpdateListener updater = animation -> {
                bounceScale = (float) animation.getAnimatedValue();
                invalidate();
            };
            setClipInParent(false);
            inAnimator.addUpdateListener(updater);
            outAnimator.addUpdateListener(updater);
            animatorSet.playSequentially(inAnimator, outAnimator);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    bounceScale = 1f;
                    invalidate();
                    setClipInParent(true);
                }
            });
            animatorSet.start();

            if (animationRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animationRunnable.run();
                animationRunnable = null;
            }
        }

        private void setClipInParent(boolean clip) {
            if (getParent() != null) {
                ((ViewGroup) getParent()).setClipChildren(clip);
            }
            if (getParent() != null && getParent().getParent() != null && getParent().getParent().getParent() != null) {
                ((ViewGroup) getParent().getParent().getParent()).setClipChildren(clip);
            }
        }

        private float getArcProgress(float cx, float radius) {
            if (isLast || overscrollPrgoress > 0) {
                return 0;
            }
            float p = CubicBezierInterpolator.EASE_OUT.getInterpolation(progressToCollapsed);
            float distance = AndroidUtilities.lerp(getMeasuredWidth(), AndroidUtilities.dp(COLLAPSED_DIS), p);
            radius += AndroidUtilities.dpf2(3.5f);
            if (distance < radius * 2) {
                //double cosA = (distance / 2f) / radius;
                return (float) Math.toDegrees(Math.acos((distance / 2f) / radius)) * 2;
            } else {
                return 0;
            }
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            if (pressed && params.buttonBounce == null) {
                params.buttonBounce = new ButtonBounce(this, 1.5f, 5f);
            }
            if (params.buttonBounce != null) {
                params.buttonBounce.setPressed(pressed);
            }
        }

        @Override
        public void invalidate() {
            if (mini || drawInParent && getParent() != null) {
                if (getParent() == listViewMini) {
                    listViewMini.invalidate();
                } else {
                    DialogStoriesCell.this.invalidate();
                }
            }
            super.invalidate();
        }

        @Override
        public void invalidate(int l, int t, int r, int b) {
            if (mini || drawInParent && getParent() != null) {
                if (getParent() == listViewMini) {
                    listViewMini.invalidate();
                }
                DialogStoriesCell.this.invalidate();
            }
            super.invalidate(l, t, r, b);
        }

        public void drawPlus(Canvas canvas, float cx, float cy, float alpha) {
            if (!isSelf || storiesController.hasStories(dialogId) || !Utilities.isNullOrEmpty(storiesController.getUploadingStories(dialogId))) {
                return;
            }
            float cx2 = cx + AndroidUtilities.dp(16);
            float cy2 = cy + AndroidUtilities.dp(16);
            addCirclePaint.setColor(Theme.multAlpha(getTextColor(), alpha));
            if (type == TYPE_DIALOGS) {
                backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefault), alpha));
            } else {
                backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultArchived), alpha));
            }
            canvas.drawCircle(cx2, cy2, AndroidUtilities.dp(11), backgroundPaint);
            canvas.drawCircle(cx2, cy2, AndroidUtilities.dp(9), addCirclePaint);

            int newDrawableColor = type == TYPE_DIALOGS ? Theme.getColor(Theme.key_actionBarDefault) : Theme.getColor(Theme.key_actionBarDefaultArchived);
            if (newDrawableColor != addNewStoryLastColor) {
                addNewStoryDrawable.setColorFilter(new PorterDuffColorFilter(addNewStoryLastColor = newDrawableColor, PorterDuff.Mode.MULTIPLY));
            }
            addNewStoryDrawable.setAlpha((int) (0xFF * alpha));
            addNewStoryDrawable.setBounds(
                    (int) (cx2 - addNewStoryDrawable.getIntrinsicWidth() / 2f),
                    (int) (cy2 - addNewStoryDrawable.getIntrinsicHeight() / 2f),
                    (int) (cx2 + addNewStoryDrawable.getIntrinsicWidth() / 2f),
                    (int) (cy2 + addNewStoryDrawable.getIntrinsicHeight() / 2f)
            );
            addNewStoryDrawable.draw(canvas);
        }

        public void drawFail(Canvas canvas, float cx, float cy, float alpha) {
            if (alpha <= 0) {
                return;
            }
            float cx2 = cx + AndroidUtilities.dp(17);
            float cy2 = cy + AndroidUtilities.dp(17);
            addCirclePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedBold), alpha));
            if (type == TYPE_DIALOGS) {
                backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefault), alpha));
            } else {
                backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultArchived), alpha));
            }
            float r = AndroidUtilities.dp(9) * CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(alpha);
            canvas.drawCircle(cx2, cy2, r + AndroidUtilities.dp(2), backgroundPaint);
            canvas.drawCircle(cx2, cy2, r, addCirclePaint);

            addCirclePaint.setColor(Theme.multAlpha(getTextColor(), alpha));

            AndroidUtilities.rectTmp.set(cx2 - AndroidUtilities.dp(1), cy2 - AndroidUtilities.dpf2(4.6f), cx2 + AndroidUtilities.dp(1), cy2 + AndroidUtilities.dpf2(1.6f));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), addCirclePaint);

            AndroidUtilities.rectTmp.set(cx2 - AndroidUtilities.dp(1), cy2 + AndroidUtilities.dpf2(2.6f), cx2 + AndroidUtilities.dp(1), cy2 + AndroidUtilities.dpf2(2.6f + 2));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), addCirclePaint);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            avatarImage.onAttachedToWindow();
            crossfageToAvatarImage.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            avatarImage.onDetachedFromWindow();
            crossfageToAvatarImage.onDetachedFromWindow();
            params.onDetachFromWindow();
            if (cancellable != null) {
                cancellable.cancel();
                cancellable = null;
            }
        }

        public void setProgressToCollapsed(float progressToCollapsed, float progressToCollapsed2, float overscrollProgress, boolean selectedForOverscroll) {
            if (this.progressToCollapsed != progressToCollapsed || this.progressToCollapsed2 != progressToCollapsed2 || this.overscrollProgress != overscrollProgress || this.selectedForOverscroll != selectedForOverscroll) {
                this.selectedForOverscroll = selectedForOverscroll;
                this.progressToCollapsed = progressToCollapsed;
                this.progressToCollapsed2 = progressToCollapsed2;
                float progressHalf = Utilities.clamp(progressToCollapsed / 0.5f, 1f, 0f);
                float size = AndroidUtilities.dp(48);
                float collapsedSize = AndroidUtilities.dp(COLLAPSED_SIZE);
                invalidate();
                recyclerListView.invalidate();
            }
            textAlphaTransition = mini ? 0 : 1f - Utilities.clamp(collapsedProgress / K, 1f, 0);
            textViewContainer.setAlpha(textAlphaTransition * textAlpha);
        }

        public void setCrossfadeTo(long dialogId) {
            if (crossfadeToDialogId != dialogId) {
                this.crossfadeToDialogId = dialogId;
                crossfadeToDialog = dialogId != -1;
                if (crossfadeToDialog) {
                    TLObject object;
                    if (dialogId > 0) {
                        object = user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        chat = null;
                    } else {
                        object = chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        user = null;
                    }
                    if (object != null) {
                        crossfadeAvatarDrawable.setInfo(object);
                        crossfageToAvatarImage.setForUserOrChat(object, crossfadeAvatarDrawable);
                    }
                } else {
                    crossfageToAvatarImage.clearImage();
                }
            }
        }
    }


    private Drawable createVerifiedDrawable() {
        Drawable verifyDrawable = ContextCompat.getDrawable(getContext(), R.drawable.verified_area).mutate();
        Drawable checkDrawable = ContextCompat.getDrawable(getContext(), R.drawable.verified_check).mutate();
        CombinedDrawable combinedDrawable = new CombinedDrawable(
                verifyDrawable,
                checkDrawable
        ) {

            int lastColor;
            @Override
            public void draw(Canvas canvas) {
                int color = type == TYPE_DIALOGS ? Theme.getColor(Theme.key_actionBarDefault) : Theme.getColor(Theme.key_actionBarDefaultArchived);
                if (lastColor != color) {
                    lastColor = color;
                    int textColor = type == TYPE_DIALOGS ? Theme.getColor(Theme.key_actionBarDefaultTitle) : Theme.getColor(Theme.key_actionBarDefaultArchivedTitle);//Theme.getColor(Theme.key_actionBarDefaultTitle);
                    verifyDrawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(textColor , color, 0.1f), PorterDuff.Mode.MULTIPLY));
                    checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
                super.draw(canvas);
            }
        };
        combinedDrawable.setFullsize(true);
        return combinedDrawable;
    }

    private void updateCurrentState(int state) {
        if (this.currentState == state) {
            return;
        }
        int prevState = this.currentState;
        this.currentState = state;
        if (currentState != TRANSITION_STATE && updateOnIdleState) {
            AndroidUtilities.runOnUIThread(() -> {
                updateItems(true, false);
            });
        }
        if (currentState == EXPANDED_STATE) {
            AndroidUtilities.forEachViews(recyclerListView, view -> {
                view.setAlpha(1f);
                view.setTranslationX(0);
                view.setTranslationY(0);
            });
            listViewMini.setVisibility(View.INVISIBLE);
            recyclerListView.setVisibility(View.VISIBLE);
            checkExpanded();
        } else if (currentState == TRANSITION_STATE) {
            animateToDialogIds.clear();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).dialogId != UserConfig.getInstance(currentAccount).getClientUserId() || shouldDrawSelfInMini()) {
                    animateToDialogIds.add(items.get(i).dialogId);
                    if (animateToDialogIds.size() == 3) {
                        break;
                    }
                }
            }
            listViewMini.setVisibility(View.INVISIBLE);
            recyclerListView.setVisibility(View.VISIBLE);
        } else if (currentState == COLLAPSED_STATE) {
            listViewMini.setVisibility(View.VISIBLE);
            recyclerListView.setVisibility(View.INVISIBLE);
            layoutManager.scrollToPositionWithOffset(0,  0);
            MessagesController.getInstance(currentAccount).getStoriesController().scheduleSort();
            if (globalCancelable != null) {
                globalCancelable.cancel();
                globalCancelable = null;
            }
        }
        invalidate();
    }

    static float getAvatarRight(int width, float progressToCollapsed) {
        float size = AndroidUtilities.dp(48);
        float collapsedSize = AndroidUtilities.dp(COLLAPSED_SIZE);
        float finalSize = AndroidUtilities.lerp(size, collapsedSize, progressToCollapsed);
        float radius = finalSize / 2f;

        float fromX = width / 2f - radius;
        float x = AndroidUtilities.lerp(fromX, 0, progressToCollapsed);
        return x + radius * 2;
    }

    private long checkedStoryNotificationDeletion;
    private void checkExpanded() {
        if (System.currentTimeMillis() < checkedStoryNotificationDeletion) {
            return;
        }
//        NotificationsController.getInstance(currentAccount).processIgnoreStories();
        checkedStoryNotificationDeletion = System.currentTimeMillis() + 1000L * 60;
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (premiumHint != null) {
            premiumHint.setTranslationY(translationY);
        }
    }

    public HintView2 getPremiumHint() {
        return premiumHint;
    }

    private HintView2 makePremiumHint() {
        if (premiumHint != null) {
            return premiumHint;
        }
        premiumHint = new HintView2(getContext(), HintView2.DIRECTION_TOP)
            .setBgColor(Theme.getColor(Theme.key_undo_background))
            .setMultilineText(true)
            .setTextAlign(Layout.Alignment.ALIGN_CENTER)
            .setJoint(0, 37 - 8);
        Spannable text = AndroidUtilities.replaceSingleTag(LocaleController.getString("StoriesPremiumHint2").replace('\n', ' '), Theme.key_undo_cancelColor, 0, () -> {
            if (premiumHint != null) {
                premiumHint.hide();
            }
            fragment.presentFragment(new PremiumPreviewFragment("stories"));
        });
        ClickableSpan[] spans = text.getSpans(0, text.length(), ClickableSpan.class);
        if (spans != null && spans.length >= 1) {
            int start = text.getSpanStart(spans[0]);
            int end = text.getSpanEnd(spans[0]);
            text.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        premiumHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, premiumHint.getTextPaint()));
        premiumHint.setText(text);
        premiumHint.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8), 0);
        if (getParent() instanceof FrameLayout) {
            ((FrameLayout) getParent()).addView(premiumHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150, Gravity.LEFT | Gravity.TOP));
        }
        return premiumHint;
    }

    public void showPremiumHint() {
        makePremiumHint();
        if (premiumHint != null) {
            if (premiumHint.shown()) {
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
            }
            premiumHint.show();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentState == COLLAPSED_STATE) {
            int k = miniItems.size();
            int width = AndroidUtilities.dp(COLLAPSED_SIZE * k - COLLAPSED_DIS * Math.max(0,  k - 1));
            miniItemsClickArea.setRect((int) listViewMini.getX(), (int) listViewMini.getY(), (int) (listViewMini.getX() + width), (int) (listViewMini.getY() + listViewMini.getHeight()));
            if (miniItemsClickArea.checkTouchEvent(event)) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
