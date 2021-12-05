package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.StickersSearchAdapter;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetCell2;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.StickerEmojiCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TrendingStickersLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public abstract static class Delegate {

        private String[] lastSearchKeyboardLanguage = new String[0];

        public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary) {
        }

        public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
        }

        public boolean onListViewInterceptTouchEvent(RecyclerListView listView, MotionEvent event) {
            return false;
        }

        public boolean onListViewTouchEvent(RecyclerListView listView, RecyclerListView.OnItemClickListener onItemClickListener, MotionEvent event) {
            return false;
        }

        public String[] getLastSearchKeyboardLanguage() {
            return lastSearchKeyboardLanguage;
        }

        public void setLastSearchKeyboardLanguage(String[] language) {
            lastSearchKeyboardLanguage = language;
        }

        public boolean canSendSticker() {
            return false;
        }

        public void onStickerSelected(TLRPC.Document sticker, Object parent, boolean clearsInputField, boolean notify, int scheduleDate) {
        }

        public boolean canSchedule() {
            return false;
        }

        public boolean isInScheduleMode() {
            return false;
        }
    }

    private final int currentAccount = UserConfig.selectedAccount;

    private final Delegate delegate;
    private final TLRPC.StickerSetCovered[] primaryInstallingStickerSets;
    private final LongSparseArray<TLRPC.StickerSetCovered> installingStickerSets;
    private final LongSparseArray<TLRPC.StickerSetCovered> removingStickerSets;

    private final View shadowView;
    private final SearchField searchView;
    private final RecyclerListView listView;
    private final GridLayoutManager layoutManager;
    private final TrendingStickersAdapter adapter;
    private final StickersSearchAdapter searchAdapter;
    private final FrameLayout searchLayout;

    private BaseFragment parentFragment;
    private RecyclerListView.OnScrollListener onScrollListener;

    private int topOffset;
    private boolean motionEventCatchedByListView;
    private boolean shadowVisible;
    private boolean ignoreLayout;
    private boolean wasLayout;
    private boolean loaded;
    private long hash;
    ValueAnimator glueToTopAnimator;
    private boolean gluedToTop;
    private boolean scrollFromAnimator;
    private TLRPC.StickerSetCovered scrollToSet;
    private final Theme.ResourcesProvider resourcesProvider;

    public TrendingStickersLayout(@NonNull Context context, Delegate delegate) {
        this(context, delegate, new TLRPC.StickerSetCovered[10], new LongSparseArray<>(), new LongSparseArray<>(), null, null);
    }

    public TrendingStickersLayout(@NonNull Context context, Delegate delegate, TLRPC.StickerSetCovered[] primaryInstallingStickerSets, LongSparseArray<TLRPC.StickerSetCovered> installingStickerSets, LongSparseArray<TLRPC.StickerSetCovered> removingStickerSets, TLRPC.StickerSetCovered scrollToSet, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.delegate = delegate;
        this.primaryInstallingStickerSets = primaryInstallingStickerSets;
        this.installingStickerSets = installingStickerSets;
        this.removingStickerSets = removingStickerSets;
        this.scrollToSet = scrollToSet;
        this.resourcesProvider = resourcesProvider;
        this.adapter = new TrendingStickersAdapter(context);

        final StickersSearchAdapter.Delegate searchAdapterDelegate = new StickersSearchAdapter.Delegate() {
            @Override
            public void onSearchStart() {
                searchView.getProgressDrawable().startAnimation();
            }

            @Override
            public void onSearchStop() {
                searchView.getProgressDrawable().stopAnimation();
            }

            @Override
            public void setAdapterVisible(boolean visible) {
                boolean changed = false;
                if (visible && listView.getAdapter() != searchAdapter) {
                    listView.setAdapter(searchAdapter);
                    changed = true;
                } else if (!visible && listView.getAdapter() != adapter) {
                    listView.setAdapter(adapter);
                    changed = true;
                }
                if (changed && listView.getAdapter().getItemCount() > 0) {
                    layoutManager.scrollToPositionWithOffset(0, -listView.getPaddingTop() + AndroidUtilities.dp(58) + topOffset, false);
                }
            }

            @Override
            public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary) {
                delegate.onStickerSetAdd(stickerSet, primary);
            }

            @Override
            public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                delegate.onStickerSetRemove(stickerSet);
            }

            @Override
            public int getStickersPerRow() {
                return adapter.stickersPerRow;
            }

            @Override
            public String[] getLastSearchKeyboardLanguage() {
                return delegate.getLastSearchKeyboardLanguage();
            }

            @Override
            public void setLastSearchKeyboardLanguage(String[] language) {
                delegate.setLastSearchKeyboardLanguage(language);
            }
        };
        searchAdapter = new StickersSearchAdapter(context, searchAdapterDelegate, primaryInstallingStickerSets, installingStickerSets, removingStickerSets, resourcesProvider);

        searchLayout = new FrameLayout(context);
        searchLayout.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        searchView = new SearchField(context, true, resourcesProvider) {
            @Override
            public void onTextChange(String text) {
                searchAdapter.search(text);
            }
        };
        searchView.setHint(LocaleController.getString("SearchTrendingStickersHint", R.string.SearchTrendingStickersHint));
        searchLayout.addView(searchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        listView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = delegate.onListViewInterceptTouchEvent(this, event);
                return super.onInterceptTouchEvent(event) || result;
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                motionEventCatchedByListView = true;
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (glueToTopAnimator != null) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public void requestLayout() {
                if (!ignoreLayout) {
                    super.requestLayout();
                }
            }

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= topOffset + AndroidUtilities.dp(58);
            }
        };
        final RecyclerListView.OnItemClickListener trendingOnItemClickListener = (view, position) -> {
            final TLRPC.StickerSetCovered pack;
            if (listView.getAdapter() == searchAdapter) {
                pack = searchAdapter.getSetForPosition(position);
            } else {
                if (position < adapter.totalItems) {
                    pack = adapter.positionsToSets.get(position);
                } else {
                    pack = null;
                }
            }
            if (pack != null) {
                showStickerSet(pack.set);
            }
        };
        listView.setOnTouchListener((v, event) -> delegate.onListViewTouchEvent(listView, trendingOnItemClickListener, event));
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setClipToPadding(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(layoutManager = new FillLastGridLayoutManager(context, 5, AndroidUtilities.dp(58), listView) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            protected boolean isLayoutRTL() {
                return LocaleController.isRTL;
            }

            @Override
            protected boolean shouldCalcLastItemHeight() {
                return listView.getAdapter() == searchAdapter;
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (scrollFromAnimator) {
                    return super.scrollVerticallyBy(dy, recycler, state);
                }
                if (glueToTopAnimator != null) {
                    return 0;
                }
                if (gluedToTop) {
                    int minPosition = 1;
                    for (int i = 0; i < getChildCount(); i++) {
                        int p = listView.getChildAdapterPosition(getChildAt(i));
                        if (p < minPosition) {
                            minPosition = p;
                            break;
                        }
                    }
                    if (minPosition == 0) {
                        View minView = layoutManager.findViewByPosition(minPosition);
                        if (minView != null && minView.getTop() - dy > AndroidUtilities.dp(58)) {
                            dy = minView.getTop() - AndroidUtilities.dp(58);
                        }
                    }
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }
        });
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (listView.getAdapter() == adapter) {
                    if (adapter.cache.get(position) instanceof Integer || position >= adapter.totalItems) {
                        return adapter.stickersPerRow;
                    }
                    return 1;
                } else {
                    return searchAdapter.getSpanSize(position);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (onScrollListener != null) {
                    onScrollListener.onScrolled(listView, dx, dy);
                }
                if (dy > 0 && listView.getAdapter() == adapter && loaded && !adapter.loadingMore && !adapter.endReached) {
                    final int threshold = (adapter.stickersPerRow + 1) * 10;
                    if (layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - threshold - 1) {
                        adapter.loadMoreStickerSets();
                    }
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(recyclerView, newState);
                }
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(trendingOnItemClickListener);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        shadowView = new View(context);
        shadowView.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        shadowView.setAlpha(0.0f);
        final FrameLayout.LayoutParams shadowViewParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight());
        shadowViewParams.topMargin = AndroidUtilities.dp(58);
        addView(shadowView, shadowViewParams);

        addView(searchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP));

        updateColors();

        final NotificationCenter notificationCenter = NotificationCenter.getInstance(currentAccount);
        notificationCenter.addObserver(this, NotificationCenter.stickersDidLoad);
        notificationCenter.addObserver(this, NotificationCenter.featuredStickersDidLoad);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!wasLayout) {
            wasLayout = true;
            adapter.refreshStickerSets();
            if (scrollToSet != null) {
                Integer pos  = adapter.setsToPosition.get(scrollToSet);
                if (pos != null) {
                    layoutManager.scrollToPositionWithOffset(pos, -listView.getPaddingTop() + AndroidUtilities.dp(58));
                }
            }
        }
    }

    private float highlightProgress = 1f;
    Paint paint = new Paint();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (highlightProgress != 0 && scrollToSet != null) {
            highlightProgress -= 16f / 3000f;
            if (highlightProgress < 0) {
                highlightProgress = 0;
            } else {
                invalidate();
            }
            Integer pos = adapter.setsToPosition.get(scrollToSet);
            if (pos != null) {
                View view1 = layoutManager.findViewByPosition(pos);
                int t = -1, b = -1;
                if (view1 != null) {
                    t = (int) view1.getY();
                    b = (int) view1.getY() + view1.getMeasuredHeight();

                }
                View view2 = layoutManager.findViewByPosition(pos + 1);
                if (view2 != null) {
                    if (view1 == null) {
                        t = (int) view2.getY();
                    }
                    b = (int) view2.getY() + view2.getMeasuredHeight();
                }

                if (view1 != null || view2 != null) {
                    paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                    float p = highlightProgress < 0.06f ? highlightProgress / 0.06f : 1f;
                    paint.setAlpha((int) (255 * 0.1f * p));
                    canvas.drawRect(0, t, getMeasuredWidth(), b, paint);
                }
            }

        }

        super.dispatchDraw(canvas);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLastItemInAdapter();
        wasLayout = false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        motionEventCatchedByListView = false;
        boolean result = super.dispatchTouchEvent(ev);
        if (!motionEventCatchedByListView) {
            MotionEvent e = MotionEvent.obtain(ev);
            listView.dispatchTouchEvent(e);
            e.recycle();
        }
        return result;
    }

    private void showStickerSet(TLRPC.StickerSet pack) {
        showStickerSet(pack, null);
    }

    public void showStickerSet(TLRPC.StickerSet pack, TLRPC.InputStickerSet inputStickerSet) {
        if (pack != null) {
            inputStickerSet = new TLRPC.TL_inputStickerSetID();
            inputStickerSet.access_hash = pack.access_hash;
            inputStickerSet.id = pack.id;
        }
        if (inputStickerSet != null) {
            showStickerSet(inputStickerSet);
        }
    }

    private void showStickerSet(TLRPC.InputStickerSet inputStickerSet) {
        final StickersAlert.StickersAlertDelegate stickersAlertDelegate;
        if (delegate.canSendSticker()) {
            stickersAlertDelegate = new StickersAlert.StickersAlertDelegate() {
                @Override
                public void onStickerSelected(TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean clearsInputField, boolean notify, int scheduleDate) {
                    delegate.onStickerSelected(sticker, parent, clearsInputField, notify, scheduleDate);
                }

                @Override
                public boolean canSchedule() {
                    return delegate.canSchedule();
                }

                @Override
                public boolean isInScheduleMode() {
                    return delegate.isInScheduleMode();
                }
            };
        } else {
            stickersAlertDelegate = null;
        }
        final StickersAlert stickersAlert = new StickersAlert(getContext(), parentFragment, inputStickerSet, null, stickersAlertDelegate, resourcesProvider);
        stickersAlert.setShowTooltipWhenToggle(false);
        stickersAlert.setInstallDelegate(new StickersAlert.StickersAlertInstallDelegate() {
            @Override
            public void onStickerSetInstalled() {
                if (listView.getAdapter() == adapter) {
                    for (int i = 0; i < adapter.sets.size(); i++) {
                        final TLRPC.StickerSetCovered setCovered = adapter.sets.get(i);
                        if (setCovered.set.id == inputStickerSet.id) {
                            adapter.installStickerSet(setCovered, null);
                            break;
                        }
                    }
                } else {
                    searchAdapter.installStickerSet(inputStickerSet);
                }
            }

            @Override
            public void onStickerSetUninstalled() {
            }
        });
        parentFragment.showDialog(stickersAlert);
    }

    public void recycle() {
        final NotificationCenter notificationCenter = NotificationCenter.getInstance(currentAccount);
        notificationCenter.removeObserver(this, NotificationCenter.stickersDidLoad);
        notificationCenter.removeObserver(this, NotificationCenter.featuredStickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == MediaDataController.TYPE_IMAGE) {
                if (loaded) {
                    updateVisibleTrendingSets();
                } else {
                    adapter.refreshStickerSets();
                }
            }
        } else if (id == NotificationCenter.featuredStickersDidLoad) {
            if (hash != MediaDataController.getInstance(currentAccount).getFeaturesStickersHashWithoutUnread()) {
                loaded = false;
            }
            if (loaded) {
                updateVisibleTrendingSets();
            } else {
                adapter.refreshStickerSets();
            }
        }
    }

    public void setOnScrollListener(RecyclerListView.OnScrollListener onScrollListener) {
        this.onScrollListener = onScrollListener;
    }

    public void setParentFragment(BaseFragment parentFragment) {
        this.parentFragment = parentFragment;
    }

    public void setContentViewPaddingTop(int paddingTop) {
        paddingTop += AndroidUtilities.dp(58);
        if (listView.getPaddingTop() != paddingTop) {
            ignoreLayout = true;
            listView.setPadding(0, paddingTop, 0, 0);
            ignoreLayout = false;
        }
    }

    private void updateLastItemInAdapter() {
        final RecyclerView.Adapter adapter = listView.getAdapter();
        adapter.notifyItemChanged(adapter.getItemCount() - 1);
    }

    public int getContentTopOffset() {
        return topOffset;
    }

    public boolean update() {
        if (listView.getChildCount() <= 0) {
            topOffset = listView.getPaddingTop();
            listView.setTopGlowOffset(topOffset);
            searchLayout.setTranslationY(topOffset);
            shadowView.setTranslationY(topOffset);
            setShadowVisible(false);
            return true;
        }
        View child = listView.getChildAt(0);
        for (int i = 1; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view.getTop() < child.getTop()) {
                child = view;
            }
        }
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(58);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        setShadowVisible(top < 0);
        if (topOffset != newOffset) {
            topOffset = newOffset;
            listView.setTopGlowOffset(topOffset + AndroidUtilities.dp(58));
            searchLayout.setTranslationY(topOffset);
            shadowView.setTranslationY(topOffset);
            return true;
        }
        return false;
    }

    private void updateVisibleTrendingSets() {
        final RecyclerListView.Adapter listAdapter = listView.getAdapter();
        if (listAdapter != null) {
            listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount(), listAdapter == adapter ? TrendingStickersAdapter.PAYLOAD_ANIMATED : StickersSearchAdapter.PAYLOAD_ANIMATED);
        }
    }

    private void setShadowVisible(boolean visible) {
        if (shadowVisible != visible) {
            shadowVisible = visible;
            shadowView.animate().alpha(visible ? 1f : 0f).setDuration(200).start();
        }
    }

    public void updateColors() {
        if (listView.getAdapter() == adapter) {
            adapter.updateColors(listView);
        } else {
            searchAdapter.updateColors(listView);
        }
    }

    public void getThemeDescriptions(List<ThemeDescription> descriptions, ThemeDescription.ThemeDescriptionDelegate delegate) {
        searchView.getThemeDescriptions(descriptions);
        adapter.getThemeDescriptions(descriptions, listView, delegate);
        searchAdapter.getThemeDescriptions(descriptions, listView, delegate);
        descriptions.add(new ThemeDescription(shadowView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine));
        descriptions.add(new ThemeDescription(searchLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
    }

    public void glueToTop(boolean glue) {
        gluedToTop = glue;
        if (glue) {
            if (getContentTopOffset() > 0 && glueToTopAnimator == null) {
                int startFrom = getContentTopOffset();
                glueToTopAnimator = ValueAnimator.ofFloat(0, 1f);
                glueToTopAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    int dy = 0;

                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        int currentDy = (int) (startFrom * (float) valueAnimator.getAnimatedValue());
                        scrollFromAnimator = true;
                        listView.scrollBy(0, currentDy - dy);
                        scrollFromAnimator = false;
                        dy = currentDy;
                    }
                });
                glueToTopAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        glueToTopAnimator = null;
                    }
                });
                glueToTopAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                glueToTopAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                glueToTopAnimator.start();
            }
        } else {
            if (glueToTopAnimator != null) {
                glueToTopAnimator.removeAllListeners();
                glueToTopAnimator.cancel();
                glueToTopAnimator = null;
            }
        }
    }

    private class TrendingStickersAdapter extends RecyclerListView.SelectionAdapter {

        public static final int PAYLOAD_ANIMATED = 0;

        private static final int ITEM_SECTION = -1;

        private final Context context;
        private final SparseArray<Object> cache = new SparseArray<>();
        private final ArrayList<TLRPC.StickerSetCovered> sets = new ArrayList<>();
        private final SparseArray<TLRPC.StickerSetCovered> positionsToSets = new SparseArray<>();
        private final HashMap<TLRPC.StickerSetCovered, Integer> setsToPosition = new HashMap<>();
        private final ArrayList<TLRPC.StickerSetCovered> otherPacks = new ArrayList<>();

        private boolean loadingMore;
        private boolean endReached;

        private int stickersPerRow = 5;
        private int totalItems;

        public TrendingStickersAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return totalItems + 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 5;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == getItemCount() - 1) {
                return 3; // fill view
            }
            Object object = cache.get(position);
            if (object != null) {
                if (object instanceof TLRPC.Document) {
                    return 0; // sticker cell
                } else if (object.equals(ITEM_SECTION)) {
                    return 4; // section cell
                } else {
                    return 2; // set info cell
                }
            }
            return 1; // empty cell
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    StickerEmojiCell stickerCell = new StickerEmojiCell(context, false) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    stickerCell.getImageView().setLayerNum(3);
                    view = stickerCell;
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new FeaturedStickerSetInfoCell(context, 17, true, true, resourcesProvider);
                    ((FeaturedStickerSetInfoCell) view).setAddOnClickListener(v -> {
                        final FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) v.getParent();
                        TLRPC.StickerSetCovered pack = cell.getStickerSet();
                        if (installingStickerSets.indexOfKey(pack.set.id) >= 0 || removingStickerSets.indexOfKey(pack.set.id) >= 0) {
                            return;
                        }
                        if (cell.isInstalled()) {
                            removingStickerSets.put(pack.set.id, pack);
                            delegate.onStickerSetRemove(pack);
                        } else {
                            installStickerSet(pack, cell);
                        }
                    });
                    break;
                case 3:
                    view = new View(context);
                    break;
                case 4:
                    view = new GraySectionCell(context, resourcesProvider);
                    break;
                case 5:
                    final FeaturedStickerSetCell2 stickerSetCell = new FeaturedStickerSetCell2(context, resourcesProvider);
                    stickerSetCell.setAddOnClickListener(v -> {
                        final FeaturedStickerSetCell2 cell = (FeaturedStickerSetCell2) v.getParent();
                        TLRPC.StickerSetCovered pack = cell.getStickerSet();
                        if (installingStickerSets.indexOfKey(pack.set.id) >= 0 || removingStickerSets.indexOfKey(pack.set.id) >= 0) {
                            return;
                        }
                        if (cell.isInstalled()) {
                            removingStickerSets.put(pack.set.id, pack);
                            delegate.onStickerSetRemove(pack);
                        } else {
                            installStickerSet(pack, cell);
                        }
                    });
                    stickerSetCell.getImageView().setLayerNum(3);
                    view = stickerSetCell;
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    ((StickerEmojiCell) holder.itemView).setSticker(sticker, positionsToSets.get(position), false);
                    break;
                case 1:
                    ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(82));
                    break;
                case 2:
                case 5:
                    bindStickerSetCell(holder.itemView, position, false);
                    break;
                case 4:
                    ((GraySectionCell) holder.itemView).setText(LocaleController.getString("OtherStickers", R.string.OtherStickers));
                    break;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
            if (payloads.contains(PAYLOAD_ANIMATED)) {
                final int type = holder.getItemViewType();
                if (type == 2 || type == 5) {
                    bindStickerSetCell(holder.itemView, position, true);
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        private void bindStickerSetCell(View view, int position, boolean animated) {
            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            boolean unread = false;
            final TLRPC.StickerSetCovered stickerSetCovered;
            if (position < totalItems) {
                stickerSetCovered = sets.get((Integer) cache.get(position));
                final ArrayList<Long> unreadStickers = mediaDataController.getUnreadStickerSets();
                unread = unreadStickers != null && unreadStickers.contains(stickerSetCovered.set.id);
                if (unread) {
                    mediaDataController.markFaturedStickersByIdAsRead(stickerSetCovered.set.id);
                }
            } else {
                stickerSetCovered = sets.get((Integer) cache.get(position));
            }
            mediaDataController.preloadStickerSetThumb(stickerSetCovered);
            boolean forceInstalled = false;
            for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
                if (primaryInstallingStickerSets[i] != null) {
                    final TLRPC.TL_messages_stickerSet s = MediaDataController.getInstance(currentAccount).getStickerSetById(primaryInstallingStickerSets[i].set.id);
                    if (s != null && !s.set.archived) {
                        primaryInstallingStickerSets[i] = null;
                        continue;
                    }
                    if (primaryInstallingStickerSets[i].set.id == stickerSetCovered.set.id) {
                        forceInstalled = true;
                        break;
                    }
                }
            }
            final boolean isSetInstalled = mediaDataController.isStickerPackInstalled(stickerSetCovered.set.id);
            boolean installing = installingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
            boolean removing = removingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
            if (installing && isSetInstalled) {
                installingStickerSets.remove(stickerSetCovered.set.id);
                installing = false;
            } else if (removing && !isSetInstalled) {
                removingStickerSets.remove(stickerSetCovered.set.id);
            }
            final FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) view;
            cell.setStickerSet(stickerSetCovered, unread, animated, 0, 0, forceInstalled);
            cell.setAddDrawProgress(!forceInstalled && installing, animated);
            cell.setNeedDivider(position > 0 && (cache.get(position - 1) == null || !cache.get(position - 1).equals(ITEM_SECTION)));
        }

        private void installStickerSet(TLRPC.StickerSetCovered pack, View view) {
            for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
                if (primaryInstallingStickerSets[i] != null) {
                    final TLRPC.TL_messages_stickerSet s = MediaDataController.getInstance(currentAccount).getStickerSetById(primaryInstallingStickerSets[i].set.id);
                    if (s != null && !s.set.archived) {
                        primaryInstallingStickerSets[i] = null;
                        break;
                    }
                    if (primaryInstallingStickerSets[i].set.id == pack.set.id) {
                        return;
                    }
                }
            }

            boolean primary = false;
            for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
                if (primaryInstallingStickerSets[i] == null) {
                    primaryInstallingStickerSets[i] = pack;
                    primary = true;
                    break;
                }
            }
            if (!primary && view != null) {
                if (view instanceof FeaturedStickerSetCell2) {
                    ((FeaturedStickerSetCell2) view).setDrawProgress(true, true);
                } else if (view instanceof FeaturedStickerSetInfoCell) {
                    ((FeaturedStickerSetInfoCell) view).setAddDrawProgress(true, true);
                }
            }
            installingStickerSets.put(pack.set.id, pack);
            if (view != null) {
                delegate.onStickerSetAdd(pack, primary);
            } else {
                for (int i = 0, size = positionsToSets.size(); i < size; i++) {
                    final TLRPC.StickerSetCovered item = positionsToSets.get(i);
                    if (item != null && item.set.id == pack.set.id) {
                        notifyItemChanged(i, PAYLOAD_ANIMATED);
                        break;
                    }
                }
            }
        }

        public void refreshStickerSets() {
            int width = getMeasuredWidth();
            if (width != 0) {
                stickersPerRow = Math.max(5, width / AndroidUtilities.dp(72));
                if (layoutManager.getSpanCount() != stickersPerRow) {
                    layoutManager.setSpanCount(stickersPerRow);
                    loaded = false;
                }
            }
            if (loaded) {
                return;
            }
            cache.clear();
            positionsToSets.clear();
            setsToPosition.clear();
            sets.clear();
            totalItems = 0;
            int num = 0;

            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            ArrayList<TLRPC.StickerSetCovered> packs = new ArrayList<>(mediaDataController.getFeaturedStickerSets());
            final int otherStickersSectionPosition = packs.size();
            packs.addAll(otherPacks);

            for (int a = 0; a < packs.size(); a++) {
                TLRPC.StickerSetCovered pack = packs.get(a);
                if (pack.covers.isEmpty() && pack.cover == null) {
                    continue;
                }

                if (a == otherStickersSectionPosition) {
                    cache.put(totalItems++, ITEM_SECTION);
                }

                sets.add(pack);
                positionsToSets.put(totalItems, pack);
                setsToPosition.put(pack, totalItems);
                cache.put(totalItems++, num++);

                int count;
                if (!pack.covers.isEmpty()) {
                    count = (int) Math.ceil(pack.covers.size() / (float) stickersPerRow);
                    for (int b = 0; b < pack.covers.size(); b++) {
                        cache.put(b + totalItems, pack.covers.get(b));
                    }
                } else {
                    count = 1;
                    cache.put(totalItems, pack.cover);
                }
                for (int b = 0; b < count * stickersPerRow; b++) {
                    positionsToSets.put(totalItems + b, pack);
                }
                totalItems += count * stickersPerRow;
            }
            if (totalItems != 0) {
                loaded = true;
                hash = mediaDataController.getFeaturesStickersHashWithoutUnread();
            }
            notifyDataSetChanged();
        }

        public void loadMoreStickerSets() {
            if (!loaded || loadingMore || endReached) {
                return;
            }
            loadingMore = true;
            final TLRPC.TL_messages_getOldFeaturedStickers req = new TLRPC.TL_messages_getOldFeaturedStickers();
            req.offset = otherPacks.size();
            req.limit = 40;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loadingMore = false;
                if (error == null && response instanceof TLRPC.TL_messages_featuredStickers) {
                    final TLRPC.TL_messages_featuredStickers stickersResponse = (TLRPC.TL_messages_featuredStickers) response;
                    final List<TLRPC.StickerSetCovered> packs = stickersResponse.sets;
                    if (packs.size() < 40) {
                        endReached = true;
                    }
                    if (!packs.isEmpty()) {
                        if (otherPacks.isEmpty()) {
                            cache.put(totalItems++, ITEM_SECTION);
                        }
                        otherPacks.addAll(packs);
                        int num = sets.size();
                        for (int a = 0; a < packs.size(); a++) {
                            TLRPC.StickerSetCovered pack = packs.get(a);
                            if (pack.covers.isEmpty() && pack.cover == null) {
                                continue;
                            }
                            sets.add(pack);
                            positionsToSets.put(totalItems, pack);
                            cache.put(totalItems++, num++);

                            int count;
                            if (!pack.covers.isEmpty()) {
                                count = (int) Math.ceil(pack.covers.size() / (float) stickersPerRow);
                                for (int b = 0; b < pack.covers.size(); b++) {
                                    cache.put(b + totalItems, pack.covers.get(b));
                                }
                            } else {
                                count = 1;
                                cache.put(totalItems, pack.cover);
                            }
                            for (int b = 0; b < count * stickersPerRow; b++) {
                                positionsToSets.put(totalItems + b, pack);
                            }
                            totalItems += count * stickersPerRow;
                        }
                        notifyDataSetChanged();
                    }
                } else {
                    endReached = true;
                }
            }));
        }

        public void updateColors(RecyclerListView listView) {
            for (int i = 0, size = listView.getChildCount(); i < size; i++) {
                final View child = listView.getChildAt(i);
                if (child instanceof FeaturedStickerSetInfoCell) {
                    ((FeaturedStickerSetInfoCell) child).updateColors();
                } else if (child instanceof FeaturedStickerSetCell2) {
                    ((FeaturedStickerSetCell2) child).updateColors();
                }
            }
        }

        public void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView, ThemeDescription.ThemeDescriptionDelegate delegate) {
            FeaturedStickerSetInfoCell.createThemeDescriptions(descriptions, listView, delegate);
            FeaturedStickerSetCell2.createThemeDescriptions(descriptions, listView, delegate);
            GraySectionCell.createThemeDescriptions(descriptions, listView);
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
