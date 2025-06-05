package org.telegram.ui.Components;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.util.Consumer;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.WallpaperCell;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.WallpapersListActivity;

import java.util.ArrayList;

public class ChatAttachAlertColorsLayout extends ChatAttachAlert.AttachAlertLayout {

    public RecyclerListView gridView;
    private int itemSize = AndroidUtilities.dp(80);
    GridLayoutManager layoutManager;
    Adapter adapter;
    private int itemsPerRow = 3;
    Consumer<Object> wallpaperConsumer;


    public ChatAttachAlertColorsLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);

        gridView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - AndroidUtilities.dp(80)) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - AndroidUtilities.dp(80)) {
                    return false;
                }
                return super.onInterceptTouchEvent(e);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                PhotoViewer.getInstance().checkCurrentImageVisibility();
            }
        };
        gridView.setAdapter(adapter = new Adapter(context));
        gridView.setClipToPadding(false);
        gridView.setItemAnimator(null);
        gridView.setLayoutAnimation(null);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            boolean parentPinnedToTop;

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (gridView.getChildCount() <= 0) {
                    return;
                }
                parentAlert.updateLayout(ChatAttachAlertColorsLayout.this, true, dy);
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13) + (parentAlert.selectedMenuItem != null ? AndroidUtilities.dp(parentAlert.selectedMenuItem.getAlpha() * 26) : 0);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(7)) {
                            gridView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(7));
                        }
                    }
                }
            }
        });
        layoutManager = new GridLayoutManager(context, itemSize) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (gridView.getPaddingTop() - AndroidUtilities.dp(7));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        };
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
//                if (position == adapter.getItemCount() - 1) {
//                    return layoutManager.getSpanCount();
//                }
                return itemSize + (position % itemsPerRow != itemsPerRow - 1 ? AndroidUtilities.dp(5) : 0);
            }
        });
        gridView.setLayoutManager(layoutManager);
    }

    @Override
    public void scrollToTop() {
        gridView.smoothScrollToPosition(0);
    }

    @Override
    public int needsActionBar() {
        return 1;
    }

    @Override
    public int getListTopPadding() {
        return gridView.getPaddingTop();
    }

    public int currentItemTop = 0;

    @Override
    public int getCurrentItemTop() {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(currentItemTop = gridView.getPaddingTop());
            return Integer.MAX_VALUE;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = AndroidUtilities.dp(7);
        if (top >= AndroidUtilities.dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        gridView.setTopGlowOffset(newOffset);
        return currentItemTop = newOffset;
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(56);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
        invalidate();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        if (AndroidUtilities.isTablet()) {
            itemsPerRow = 4;
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            itemsPerRow = 4;
        } else {
            itemsPerRow = 3;
        }
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

        int itemSize = (availableWidth - AndroidUtilities.dp(6 * 2) - AndroidUtilities.dp(5 * 2)) / itemsPerRow;
        if (this.itemSize != itemSize) {
            this.itemSize = itemSize;
            adapter.notifyDataSetChanged();
        }
        layoutManager.setSpanCount(Math.max(1, itemSize * itemsPerRow + AndroidUtilities.dp(5) * (itemsPerRow - 1)));
        int rows = (int) Math.ceil((adapter.getItemCount() - 1) / (float) itemsPerRow);
        int contentSize = rows * itemSize + (rows - 1) * AndroidUtilities.dp(5);
        int newSize = Math.max(0, availableHeight - contentSize - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(48 + 12));
        int paddingTop;
        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            paddingTop = (int) (availableHeight / 3.5f);
        } else {
            paddingTop = (availableHeight / 5 * 2);
        }
        paddingTop -= AndroidUtilities.dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }
        if (gridView.getPaddingTop() != paddingTop) {
            gridView.setPadding(AndroidUtilities.dp(6), paddingTop, AndroidUtilities.dp(6), AndroidUtilities.dp(48));
        }
    }


    public void setDelegate(Consumer<Object> wallpaperConsumer) {
        this.wallpaperConsumer = wallpaperConsumer;
    }

    public void updateColors(boolean isDark) {
        adapter.wallpapers.clear();
        WallpapersListActivity.fillDefaultColors(adapter.wallpapers, isDark);
        adapter.notifyDataSetChanged();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {


        private Context mContext;
        private final ArrayList<Object> wallpapers = new ArrayList<>();

        public Adapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return wallpapers.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            WallpaperCell view = new WallpaperCell(mContext, 1) {
                @Override
                protected void onWallpaperClick(Object wallPaper, int index) {
                    if (wallpaperConsumer != null) {
                        wallpaperConsumer.accept(wallPaper);
                    }
                }
            };
            view.drawStubBackground = false;
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            WallpaperCell wallpaperCell = (WallpaperCell) holder.itemView;
            wallpaperCell.setParams(1, false, false);
            wallpaperCell.setSize(itemSize);

            Object object = wallpapers.get(position);
            wallpaperCell.setWallpaper(WallpapersListActivity.TYPE_COLOR, 0, object, null, null, false);
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        try {
            parentAlert.actionBar.getTitleTextView().setBuildFullLayout(true);
        } catch (Exception ignore) {}
        parentAlert.actionBar.setTitle(LocaleController.getString(R.string.SelectColor));
        layoutManager.scrollToPositionWithOffset(0, 0);
    }
}

