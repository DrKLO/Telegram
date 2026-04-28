package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class ChatAttachAlertEmojiLayout extends ChatAttachAlert.AttachAlertLayout {

    private final EmojiView emojiView;
    private final RecyclerListView gridView;
    private final LinearLayoutManager layoutManager;
    private final View tabsView;
    private final boolean sticker;

    public ChatAttachAlertEmojiLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider, boolean stickers) {
        super(alert, context, resourcesProvider);
        this.sticker = stickers;

        occupyNavigationBar = true;
        emojiView = new EmojiView(alert.baseFragment, !stickers, stickers, false, getContext(), true, null, null, false, resourcesProvider, false, true);
        emojiView.shouldLightenBackground = false;
        emojiView.setAllow(!stickers, stickers, false, false);
        emojiView.forceHideBackspaceButton();
        emojiView.forceHideSettingsButton();
        emojiView.setDisableStickerEditor();
        addView(emojiView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));


        tabsView = emojiView.getTabsForType(stickers ? EmojiView.Type.STICKERS : EmojiView.Type.EMOJIS);
        gridView = emojiView.getListViewForType(stickers ? EmojiView.Type.STICKERS : EmojiView.Type.EMOJIS);
        gridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertEmojiLayout.this, true, dy);
                checkTopTabPosition();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = dp(13) + (parentAlert.selectedMenuItem != null ? dp(parentAlert.selectedMenuItem.getAlpha() * 26) : 0);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > dp(7)) {
                            gridView.smoothScrollBy(0, holder.itemView.getTop() - dp(7));
                        }
                    }
                }
            }
        });
        layoutManager = (LinearLayoutManager) gridView.getLayoutManager();

        /*
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
        */
        checkTopTabPosition();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkTopTabPosition();
    }

    public void setDelegate(EmojiView.EmojiViewDelegate delegate) {
        emojiView.setDelegate(delegate);
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
        int top = child.getTop() - dp(36);
        int newOffset = dp(7);
        if (top >= dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        gridView.setTopGlowOffset(newOffset);
        return currentItemTop = newOffset;
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + dp(56);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
        invalidate();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

        int paddingTop;
        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            paddingTop = (int) (availableHeight / 3.5f);
        } else {
            paddingTop = (availableHeight / 5 * 2);
        }
        paddingTop -= dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }

        paddingTop += dp(36);

        if (gridView.getPaddingTop() != paddingTop) {
            gridView.setPadding(dp(6), paddingTop, dp(6), dp(48));
        }
    }

    private void checkTopTabPosition() {
        int t = getCurrentItemTop();
        tabsView.setTranslationY(Math.max(0, t));
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        try {
            parentAlert.actionBar.getTitleTextView().setBuildFullLayout(true);
        } catch (Exception ignore) {}
        parentAlert.actionBar.setTitle(LocaleController.getString(sticker ?
            R.string.SelectSticker : R.string.SelectEmoji));
        layoutManager.scrollToPositionWithOffset(0, 0);
    }
}

