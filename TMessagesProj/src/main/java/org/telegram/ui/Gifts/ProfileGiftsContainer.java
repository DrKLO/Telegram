package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.Utilities.clamp01;
import static org.telegram.ui.Stars.StarGiftSheet.getGiftName;
import static org.telegram.ui.Stars.StarGiftSheet.isMineWithActions;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class ProfileGiftsContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final BaseFragment fragment;
    private final int currentAccount;
    private final long dialogId;
    private final StarsController.GiftsList list;
    public final StarsController.GiftsCollections collections;
    private final Theme.ResourcesProvider resourcesProvider;
    private int backgroundColor;

    private final ViewPagerFixed viewPager;
    private final ViewPagerFixed.TabsView tabsView;

    private final FrameLayout buttonContainer;
    private final View buttonShadow;
    private final CharSequence sendGiftsToFriendsText, addGiftsText;
    private final ButtonWithCounterView button;
    private int buttonContainerHeightDp;

    private final LinearLayout checkboxLayout;
    private final TextView checkboxTextView;
    private final CheckBox2 checkbox;
    private final FrameLayout bulletinContainer;

    private int checkboxRequestId = -1;

    public ItemOptions currentMenu;

    protected int processColor(int color) {
        return color;
    }

    private CharSequence addCollectionTabText;
    private void fillTabs(boolean animated) {
        if (viewPager == null || tabsView == null) return;
        viewPager.fillTabs(animated);
        checkScrollToCollection();
    }

    public static class Page extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        private final ProfileGiftsContainer parent;
        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        public boolean isCollection;
        @Nullable
        public StarsController.GiftsList list;

        private final UniversalRecyclerView listView;
        private final ItemTouchHelper reorder;

        private boolean reordering;

        public void update(boolean animated) {
            if (listView == null || listView.adapter == null) return;
            final boolean atTop = !listView.canScrollVertically(-1);
            listView.adapter.update(animated);
            if (atTop) {
                listView.scrollToPosition(0);
            }
        }

        public Page(ProfileGiftsContainer parent, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(parent.getContext());

            final Context context = parent.getContext();
            this.parent = parent;
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            listView = new UniversalRecyclerView(context, currentAccount, 0, false, this::fillItems, this::onItemClick, this::onItemLongPress, resourcesProvider, 3, LinearLayoutManager.VERTICAL);
            listView.adapter.setApplyBackground(false);
            listView.setSelectorType(9);
            listView.setSelectorDrawableColor(0);
            listView.setPadding(dp(9), 0, dp(9), dp(30));
            listView.setClipToPadding(false);
            listView.setClipChildren(false);
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (isAttachedToWindow() && (!listView.canScrollVertically(1) || isLoadingVisible())) {
                        list.load();
                    }
                    parent.updateTabsY();
                }
            });
            DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
                @Override
                protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    super.onMoveAnimationUpdate(holder);
                    parent.updateTabsY();
                }
                @Override
                protected void onAddAnimationUpdate(RecyclerView.ViewHolder holder) {
                    super.onAddAnimationUpdate(holder);
                    parent.updateTabsY();
                }
                @Override
                protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                    super.onChangeAnimationUpdate(holder);
                    parent.updateTabsY();
                }
                @Override
                protected void onRemoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    super.onRemoveAnimationUpdate(holder);
                    parent.updateTabsY();
                }
            };
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            listView.setItemAnimator(itemAnimator);

            reorder = new ItemTouchHelper(new ItemTouchHelper.Callback() {
                private TL_stars.SavedStarGift getSavedGift(RecyclerView.ViewHolder holder) {
                    if (holder.itemView instanceof GiftSheet.GiftCell) {
                        final GiftSheet.GiftCell cell = (GiftSheet.GiftCell) holder.itemView;
                        return cell.getSavedGift();
                    }
                    return null;
                }
                private boolean isPinnedAndSaved(TL_stars.SavedStarGift gift) {
                    return gift != null && gift.pinned_to_top && !gift.unsaved;
                }

                private boolean canReorder(TL_stars.SavedStarGift gift) {
                    if (!reordering) return false;
                    if (list == parent.list) {
                        return gift != null && gift.pinned_to_top;
                    } else {
                        return true;
                    }
                }

                @Override
                public boolean isLongPressDragEnabled() {
                    return reordering;
                }

                @Override
                public boolean isItemViewSwipeEnabled() {
                    return reordering;
                }

                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    final TL_stars.SavedStarGift savedStarGift = getSavedGift(viewHolder);
                    if (canReorder(savedStarGift)) {
                        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
                    }
                    return makeMovementFlags(0, 0);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    if (list == null || !reordering) {
                        return false;
                    }
                    if (!canReorder(getSavedGift(viewHolder)) || !canReorder(getSavedGift(target))) {
                        return false;
                    }
                    final int fromPosition = viewHolder.getAdapterPosition();
                    final int toPosition = target.getAdapterPosition();
                    if (isCollection) {
                        list.reorder(fromPosition, toPosition);
                        parent.collections.updateIcon(list.collectionId);
                    } else {
                        list.reorderPinned(fromPosition, toPosition);
                    }
                    listView.adapter.notifyItemMoved(fromPosition, toPosition);
                    listView.adapter.updateWithoutNotify();
                    if (isCollection) {
                        parent.fillTabs(true);
                    }
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment instanceof ProfileActivity && ((ProfileActivity) lastFragment).giftsView != null) {
                        ((ProfileActivity) lastFragment).giftsView.update();
                    }
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

                }

                @Override
                public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                    if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        if (list != null) {
                            list.reorderDone();
                        }
                    } else {
                        if (listView != null) {
                            listView.cancelClickRunnables(false);
                        }
                        if (viewHolder != null) {
                            viewHolder.itemView.setPressed(true);
                        }
                    }
                    super.onSelectedChanged(viewHolder, actionState);
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    viewHolder.itemView.setPressed(false);
                }
            });
            reorder.attachToRecyclerView(listView);
            updateEmptyView();
        }

        public void bind(boolean isCollection, StarsController.GiftsList list) {
            if (this.list != null) {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
            }
            this.isCollection = isCollection;
            this.list = list;
            if (list != null) {
                list.load();
            }
            update(false);
            if (this.list != null) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
            }
            if (emptyView2Layout != null) {
                emptyView2Layout.setVisibility(parent.collections.isMine() ? View.VISIBLE : View.GONE);
            }
        }

        private int visibleHeight = AndroidUtilities.displaySize.y;
        public void setVisibleHeight(int height) {
            this.visibleHeight = height;
            final float alpha = clamp01(ilerp((float) visibleHeight, dp(150), dp(220)));
            final float scale = lerp(0.6f, 1.0f, alpha);
            if (emptyView1Layout != null) {
                emptyView1Layout.setAlpha(alpha);
                emptyView1Layout.setScaleX(scale);
                emptyView1Layout.setScaleY(scale);
            }
            if (emptyView1 != null) {
                emptyView1.setTranslationY(-(getMeasuredHeight() - visibleHeight) / 2.0f);
            }
            if (emptyView2Layout != null) {
                emptyView2Layout.setAlpha(alpha);
                emptyView2Layout.setScaleX(scale);
                emptyView2Layout.setScaleY(scale);
            }
            if (emptyView2 != null) {
                emptyView2.setTranslationY(-(getMeasuredHeight() - visibleHeight) / 2.0f);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setVisibleHeight(visibleHeight);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (list != null) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (list != null) {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
            }
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starUserGiftsLoaded) {
                if (args[1] != list)
                    return;

                update(true);
                if (list != null && isAttachedToWindow() && (!listView.canScrollVertically(1) || isLoadingVisible())) {
                    list.load();
                }
            }
        }

        private FrameLayout emptyView1;
        private LinearLayout emptyView1Layout;
        private TextView emptyView1Title;
        private TextView emptyView1Button;

        private FrameLayout emptyView2;
        private LinearLayout emptyView2Layout;
        private TextView emptyView2Title;
        private TextView emptyView2Subtitle;
        private ButtonWithCounterView emptyView2Button;
        private void updateEmptyView() {
            if (emptyView1 != null) {
                removeView(emptyView1);
            }
            if (emptyView2 != null) {
                removeView(emptyView2);
            }
            if (parent.list == list) {
                emptyView2 = null;
                emptyView2Title = null;
                emptyView2Subtitle = null;
                emptyView2Button = null;
                emptyView2Layout = null;

                emptyView1 = new FrameLayout(getContext());

                emptyView1Layout = new LinearLayout(getContext());
                emptyView1Layout.setOrientation(LinearLayout.VERTICAL);
                emptyView1.addView(emptyView1Layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                BackupImageView emptyView1Image = new BackupImageView(getContext());
                emptyView1Image.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(120), dp(120)));
                emptyView1Layout.addView(emptyView1Image, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

                emptyView1Title = new TextView(getContext());
                emptyView1Title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                emptyView1Title.setTypeface(AndroidUtilities.bold());
                emptyView1Title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                emptyView1Title.setText(LocaleController.getString(R.string.ProfileGiftsNotFoundTitle));
                emptyView1Layout.addView(emptyView1Title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

                emptyView1Button = new TextView(getContext());
                emptyView1Button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                emptyView1Button.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                emptyView1Button.setText(LocaleController.getString(R.string.ProfileGiftsNotFoundButton));
                emptyView1Button.setOnClickListener(v -> {
                    if (list != null) {
                        list.resetFilters();
                    }
                });
                emptyView1Button.setPadding(dp(10), dp(4), dp(10), dp(4));
                emptyView1Button.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 4, 4));
                ScaleStateListAnimator.apply(emptyView1Button);
                emptyView1Layout.addView(emptyView1Button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

                addView(emptyView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                listView.setEmptyView(emptyView1);
            } else {
                emptyView1 = null;
                emptyView1Title = null;
                emptyView1Button = null;
                emptyView1Layout = null;

                emptyView2 = new FrameLayout(getContext());

                emptyView2Layout = new LinearLayout(getContext());
                emptyView2Layout.setOrientation(LinearLayout.VERTICAL);
                emptyView2.addView(emptyView2Layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                emptyView2Title = new TextView(getContext());
                emptyView2Title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                emptyView2Title.setTypeface(AndroidUtilities.bold());
                emptyView2Title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                emptyView2Title.setText(getString(R.string.Gift2CollectionEmptyTitle));
                emptyView2Layout.addView(emptyView2Title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

                emptyView2Subtitle = new TextView(getContext());
                emptyView2Subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                emptyView2Subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                emptyView2Subtitle.setText(getString(R.string.Gift2CollectionEmptyText));
                emptyView2Layout.addView(emptyView2Subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

                emptyView2Button = new ButtonWithCounterView(getContext(), resourcesProvider);
                emptyView2Button.setText(getString(R.string.Gift2CollectionEmptyButton), false);
                emptyView2Layout.addView(emptyView2Button, LayoutHelper.createLinear(200, 44, Gravity.CENTER_HORIZONTAL, 0, 19, 0, 12));
                emptyView2Button.setOnClickListener(v -> {
                    parent.addGifts();
                });

                addView(emptyView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, -12, 0, 0));
                listView.setEmptyView(emptyView2);

                if (emptyView2Layout != null) {
                    emptyView2Layout.setVisibility(parent.collections.isMine() ? View.VISIBLE : View.GONE);
                }
            }
        }

        private boolean isLoadingVisible() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                if (listView.getChildAt(i) instanceof FlickerLoadingView)
                    return true;
            }
            return false;
        }

        private void setReordering(boolean reordering) {
            if (this.reordering == reordering) return;
            this.reordering = reordering;
            parent.updatedReordering(parent.isReordering());
            for (int i = 0; i < listView.getChildCount(); ++i) {
                final View child = listView.getChildAt(i);
                if (child instanceof GiftSheet.GiftCell) {
                    ((GiftSheet.GiftCell) child).setReordering(reordering, true);
                }
            }
            if (listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
            if (reordering) {
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment instanceof ProfileActivity) {
                    ((ProfileActivity) lastFragment).scrollToSharedMedia(false);
                    AndroidUtilities.runOnUIThread(() -> {
                        ((ProfileActivity) lastFragment).scrollToSharedMedia(true);
                    });
                }
            }
        }

        public void resetReordering() {
            if (!reordering) return;
            if (list != null) {
                list.sendPinnedOrder();
            }
            setReordering(false);
        }

        private boolean hasTabs;
        public void setHasTabs(boolean hasTabs) {
            if (this.hasTabs == hasTabs) return;
            this.hasTabs = hasTabs;
            final boolean atTop = !listView.canScrollVertically(-1);
            listView.adapter.update(true);
            if (atTop) {
                listView.scrollToPosition(0);
            }
        }

        public float getTabsHeight() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                final View child = listView.getChildAt(i);
                final int position = listView.getChildAdapterPosition(child);
                if (child instanceof GiftSheet.GiftCell) {
                    if (position == 0) {
                        return Math.max(0, listView.getPaddingTop() + child.getY());
                    }
                } else {
                    if (position == 0) {
                        return Math.max(0, listView.getPaddingTop() + child.getY() + child.getHeight() * child.getAlpha());
                    }
                }
            }
            return 0;
        }

        public boolean isReordering() {
            return reordering;
        }

        public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (list == null)
                return;
            if (list.hasFilters() && list.gifts.size() <= 0 && list.endReached && !list.loading)
                return;
            final int spanCount = Math.max(1, list == null || list.totalCount == 0 ? 3 : Math.min(3, list.totalCount));
            if (list != null) {
                int spanCountLeft = 3;
                for (TL_stars.SavedStarGift userGift : list.gifts) {
                    items.add(
                        GiftSheet.GiftCell.Factory.asStarGift(0, userGift, true, false, isCollection)
                            .setReordering(reordering && (list == parent.list ? userGift.pinned_to_top : true))
                    );
                    spanCountLeft--;
                    if (spanCountLeft == 0) {
                        spanCountLeft = 3;
                    }
                }
                if (list.loading || !list.endReached) {
                    for (int i = 0; i < (spanCountLeft <= 0 ? 3 : spanCountLeft); ++i) {
                        items.add(UItem.asFlicker(1 + i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                    }
                }
            }
            if (parent.list == list) {
                items.add(UItem.asSpace(dp(20)));
                if (parent.dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    items.add(TextFactory.asText(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Gravity.CENTER, 14, LocaleController.getString(R.string.ProfileGiftsInfo), true, dp(24)));
                }
                items.add(UItem.asSpace(dp(24 + 48 + 10)));
            } else if (!items.isEmpty()) {
                items.add(UItem.asSpace(dp(24 + 48 + 10)));
            }

            if (!items.isEmpty()) {
                items.add(0, UItem.asSpace(dp(hasTabs ? 42 : 12)));
            }

            if (listView.getSpanCount() != spanCount) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (listView != null) {
                        listView.setSpanCount(spanCount);
                    }
                });
            }
        }

        public void onItemClick(UItem item, View view, int position, float x, float y) {
            if (list == null) return;
            if (item.object instanceof TL_stars.SavedStarGift) {
                final TL_stars.SavedStarGift userGift = (TL_stars.SavedStarGift) item.object;
                if (reordering) {
                    if (isCollection) return;
                    if (!(userGift.gift instanceof TL_stars.TL_starGiftUnique)) {
                        return;
                    }
                    final boolean newPinned = !userGift.pinned_to_top;
                    if (newPinned && userGift.unsaved) {
                        userGift.unsaved = false;

                        final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                        req.stargift = list.getInput(userGift);
                        req.unsave = userGift.unsaved;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagInvokeAfter);
                    }
                    if (list.togglePinned(userGift, newPinned, true)) {
                        BulletinFactory.of(parent.fragment)
                            .createSimpleBulletin(R.raw.chats_infotip, LocaleController.formatPluralStringComma("GiftsPinLimit", MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit))
                            .show();
                    }
                    if (newPinned) {
                        listView.scrollToPosition(0);
                    }
                } else {
                    new StarGiftSheet(getContext(), currentAccount, parent.dialogId, resourcesProvider)
                        .setOnGiftUpdatedListener(() -> {
                            update(false);
                        })
                        .setOnBoughtGift((boughtGift, dialogId) -> {
                            list.gifts.remove(userGift);
                            update(true);

                            if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                BulletinFactory.of(parent.fragment)
                                    .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftTitle), formatString(R.string.BoughtResoldGiftText, boughtGift.title + " #" + LocaleController.formatNumber(boughtGift.num, ',')))
                                    .hideAfterBottomSheet(false)
                                    .show();
                            } else {
                                BulletinFactory.of(parent.fragment)
                                    .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftToTitle), formatString(R.string.BoughtResoldGiftToText, DialogObject.getShortName(currentAccount, dialogId)))
                                    .hideAfterBottomSheet(false)
                                    .show();
                            }
                            if (LaunchActivity.instance != null) {
                                LaunchActivity.instance.getFireworksOverlay().start(true);
                            }
                        })
                        .set(userGift, list)
                        .show();
                }
            }
        }

        public boolean onItemLongPress(UItem item, View view, int position, float x, float y) {
            if (list == null) return false;
            if (view instanceof GiftSheet.GiftCell && item.object instanceof TL_stars.SavedStarGift) {
                final GiftSheet.GiftCell cell = (GiftSheet.GiftCell) view;
                final TL_stars.SavedStarGift savedStarGift = (TL_stars.SavedStarGift) item.object;
                final ItemOptions o = ItemOptions.makeOptions(parent.fragment, view, true);
                parent.currentMenu = o;
                if (parent.collections.isMine() && (isCollection || parent.collections.getCollections().size() > 0 || true)) {
                    final ItemOptions so = o.makeSwipeback();
                    so.add(R.drawable.ic_ab_back, getString(R.string.Back), () -> {
                        o.closeSwipeback();
                    });
                    so.addGap();

                    final ScrollView collectionsScrollView = new ScrollView(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(
                                widthMeasureSpec,
                                MeasureSpec.makeMeasureSpec(Math.min(dp(260), MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.getMode(heightMeasureSpec))
                            );
                        }
                    };
                    final LinearLayout collectionsLayout = new LinearLayout(getContext());
                    collectionsScrollView.addView(collectionsLayout);
                    collectionsLayout.setOrientation(LinearLayout.VERTICAL);
                    so.addView(collectionsScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    if (parent.collections.getCollections().size() + 1 < MessagesController.getInstance(currentAccount).config.stargiftsCollectionsLimit.get()) {
                        final ActionBarMenuSubItem subitem = new ActionBarMenuSubItem(getContext(), false, false, resourcesProvider);
                        subitem.setPadding(dp(18), 0, dp(18), 0);
                        subitem.setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
                        subitem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), .12f));
                        subitem.setTextAndIcon(getString(R.string.Gift2NewCollection), R.drawable.menu_folder_add);
                        subitem.setOnClickListener(v -> {
                            o.dismiss();
                            parent.openEnterNameAlert(null, name -> {
                                parent.collections.createCollection(name, collection -> {
                                    parent.collections.addGift(collection.collection_id, savedStarGift, true);
                                    parent.fillTabs(true);
                                    parent.tabsView.scrollToTab(collection.collection_id, 1 + parent.collections.indexOf(collection.collection_id));
                                    if (parent.fragment instanceof ProfileActivity) {
                                        ((ProfileActivity) parent.fragment).scrollToSharedMedia(true);
                                    }
                                    parent.updateTabsShown(true);

                                    BulletinFactory.of(parent.fragment)
                                        .createSimpleMultiBulletin(
                                            savedStarGift.gift.getDocument(),
                                            AndroidUtilities.replaceTags(formatString(R.string.Gift2AddedToCollection, getGiftName(savedStarGift.gift), collection.title))
                                        )
                                        .show();
                                });
                            });
                        });
                        collectionsLayout.addView(subitem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }
                    for (final TL_stars.TL_starGiftCollection collection : parent.collections.getCollections()) {
                        final StarsController.GiftsList list = parent.collections.getListById(collection.collection_id);
                        final boolean contains = list.contains(savedStarGift);
                        final ActionBarMenuSubItem subitem = new ActionBarMenuSubItem(getContext(), 2, false, false, resourcesProvider);
                        subitem.setChecked(contains);
                        subitem.setPadding(dp(18), 0, dp(18), 0);
                        subitem.setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
                        subitem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), .12f));
                        if (collection.icon != null) {
                            AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, collection.icon) {
                                @Override
                                public int getIntrinsicHeight() {
                                    return dp(24);
                                }
                                @Override
                                public int getIntrinsicWidth() {
                                    return dp(24);
                                }
                            };
                            drawable.addViewListening(subitem.getImageView());
                            subitem.setTextAndIcon(collection.title, 0, drawable);
                        } else {
                            subitem.setTextAndIcon(collection.title, R.drawable.msg_folders);
                        }
                        subitem.setOnClickListener(v -> {
                            if (!contains) {
                                parent.collections.addGift(collection.collection_id, savedStarGift, true);
                                BulletinFactory.of(parent.fragment)
                                    .createSimpleMultiBulletin(
                                        savedStarGift.gift.getDocument(),
                                        AndroidUtilities.replaceTags(formatString(R.string.Gift2AddedToCollection, getGiftName(savedStarGift.gift), collection.title))
                                    )
                                    .show();
                            } else {
                                parent.collections.removeGift(collection.collection_id, savedStarGift);
                                BulletinFactory.of(parent.fragment)
                                    .createSimpleMultiBulletin(
                                        savedStarGift.gift.getDocument(),
                                        AndroidUtilities.replaceTags(formatString(R.string.Gift2RemovedFromCollection, getGiftName(savedStarGift.gift), collection.title))
                                    )
                                    .show();
                            }
                            o.dismiss();
                            parent.updateTabsShown(true);
                        });
                        collectionsLayout.addView(subitem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    o.add(R.drawable.msg_addfolder, getString(R.string.Gift2AddToCollection), () -> {
                        o.openSwipeback(so);
                    });
                    o.addGap();
                }
                if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    if (parent.canReorder() && !isCollection && (!savedStarGift.unsaved || !savedStarGift.pinned_to_top)) {
                        o.add(savedStarGift.pinned_to_top ? R.drawable.msg_unpin : R.drawable.msg_pin, savedStarGift.pinned_to_top ? getString(R.string.Gift2Unpin) : getString(R.string.Gift2Pin), () -> {
                            if (savedStarGift.unsaved) {
                                savedStarGift.unsaved = false;
                                cell.setStarsGift(savedStarGift, true, false);

                                final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                                req.stargift = list.getInput(savedStarGift);
                                req.unsave = savedStarGift.unsaved;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagInvokeAfter);
                            }

                            final boolean newPinned = !savedStarGift.pinned_to_top;
                            if (list.togglePinned(savedStarGift, newPinned, false)) {
                                new UnpinSheet(getContext(), parent.dialogId, savedStarGift, resourcesProvider, () -> {
                                    ((GiftSheet.GiftCell) view).setPinned(newPinned, true);
                                    listView.scrollToPosition(0);
                                    return BulletinFactory.of(parent.fragment);
                                }).show();
                                return;
                            } else if (newPinned) {
                                BulletinFactory.of(parent.fragment)
                                    .createSimpleBulletin(R.raw.ic_pin, getString(R.string.Gift2PinnedTitle), getString(R.string.Gift2PinnedSubtitle))
                                    .show();
                            } else {
                                BulletinFactory.of(parent.fragment)
                                    .createSimpleBulletin(R.raw.ic_unpin, getString(R.string.Gift2Unpinned))
                                    .show();
                            }
                            ((GiftSheet.GiftCell) view).setPinned(newPinned, true);
                            listView.scrollToPosition(0);
                        });
                        o.addIf(savedStarGift.pinned_to_top, R.drawable.tabs_reorder, getString(R.string.Gift2Reorder), () -> {
                            setReordering(true);
                        });
                    } else if (parent.canReorder() && isCollection) {
                        o.add(R.drawable.tabs_reorder, getString(R.string.Gift2Reorder), () -> {
                            setReordering(true);
                        });
                    }

                    final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
                    final String link;
                    if (savedStarGift.gift.slug != null) {
                        link = MessagesController.getInstance(currentAccount).linkPrefix + "/nft/" + savedStarGift.gift.slug;
                    } else {
                        link = null;
                    }
                    if (isMineWithActions(currentAccount, DialogObject.getPeerDialogId(gift.owner_id))) {
                        final boolean worn = StarGiftSheet.isWorn(currentAccount, gift);
                        o.add(worn ? R.drawable.menu_takeoff : R.drawable.menu_wear, getString(worn ? R.string.Gift2Unwear : R.string.Gift2Wear), () -> {
                            new StarGiftSheet(getContext(), currentAccount, parent.dialogId, resourcesProvider) {
                                @Override
                                public BulletinFactory getBulletinFactory() {
                                    return BulletinFactory.of(parent.fragment);
                                }
                            }
                                .set(savedStarGift, null)
                                .toggleWear(false);
                        });
                    }
                    o.addIf(link != null, R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
                        AndroidUtilities.addToClipboard(link);
                        BulletinFactory.of(parent.fragment)
                            .createCopyLinkBulletin(false)
                            .show();
                    });
                    o.addIf(link != null, R.drawable.msg_share, getString(R.string.ShareFile), () -> {
                        new StarGiftSheet(getContext(), currentAccount, parent.dialogId, resourcesProvider) {
                            @Override
                            public BulletinFactory getBulletinFactory() {
                                return BulletinFactory.of(parent.fragment);
                            }
                        }
                            .set(savedStarGift, null)
                            .onSharePressed(null);
                    });
                } else if (parent.canReorder() && isCollection) {
                    o.add(R.drawable.tabs_reorder, getString(R.string.Gift2Reorder), () -> {
                        setReordering(true);
                    });
                }
                if (isMineWithActions(currentAccount, parent.dialogId)) {
                    o.add(savedStarGift.unsaved ? R.drawable.msg_message : R.drawable.menu_hide_gift, getString(savedStarGift.unsaved ? R.string.Gift2ShowGift : R.string.Gift2HideGift), () -> {
                        if (!isCollection && savedStarGift.pinned_to_top && !savedStarGift.unsaved) {
                            cell.setPinned(false, true);
                            list.togglePinned(savedStarGift, false, false);
                        }

                        savedStarGift.unsaved = !savedStarGift.unsaved;
                        cell.setStarsGift(savedStarGift, true, isCollection);
                        parent.collections.updateGiftsUnsaved(savedStarGift, savedStarGift.unsaved);

                        final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                        req.stargift = list.getInput(savedStarGift);
                        req.unsave = savedStarGift.unsaved;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                    });
                }
                if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
                    final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                    final boolean canTransfer = DialogObject.getPeerDialogId(gift.owner_id) == selfId;
                    o.addIf(canTransfer, R.drawable.menu_transfer, getString(R.string.Gift2TransferOption), () -> {
                        new StarGiftSheet(getContext(), currentAccount, parent.dialogId, resourcesProvider) {
                            @Override
                            public BulletinFactory getBulletinFactory() {
                                return BulletinFactory.of(parent.fragment);
                            }
                        }
                            .set(savedStarGift, null)
                            .openTransfer();
                    });
                }
                if (parent.collections.isMine() && isCollection) {
                    o.add(R.drawable.msg_removefolder, getString(R.string.Gift2RemoveFromCollection), true, () -> {
                        parent.collections.removeGift(list.collectionId, savedStarGift);
                        o.dismiss();
                        parent.updateTabsShown(true);

                        final TL_stars.TL_starGiftCollection collection = parent.collections.findById(list.collectionId);
                        if (collection != null) {
                            BulletinFactory.of(parent.fragment)
                                .createSimpleMultiBulletin(
                                    savedStarGift.gift.getDocument(),
                                    AndroidUtilities.replaceTags(formatString(R.string.Gift2RemovedFromCollection, getGiftName(savedStarGift.gift), collection.title))
                                )
                                .show();
                        }
                    }).makeMultiline(false).cutTextInFancyHalf();
                }
                if (o.getItemsCount() <= 0) {
                    return false;
                }
                o.setGravity(Gravity.RIGHT);
                o.setBlur(true);
                o.allowMoveScrim();
                final int min = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                o.animateToSize(min - dp(32), (int) (min * .6f));
                o.hideScrimUnder();
                o.forceBottom(true);
                o.show();
                ((GiftSheet.GiftCell) view).imageView.getImageReceiver().startAnimation(true);
                return true;
            }
            return false;
        }

        public void updateColors() {
            if (emptyView1 != null) {
                emptyView1Title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                emptyView1Button.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                emptyView1Button.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 4, 4));
            } else {
                emptyView2Title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                emptyView2Subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                emptyView2Button.updateColors();
            }
        }
    }

    public boolean canScroll(boolean forward) {
        if (forward)
            return viewPager.getCurrentPosition() >= collections.getCollections().size();
        return viewPager.getCurrentPosition() <= 0;
    }

    public ProfileGiftsContainer(
        BaseFragment fragment,
        Context context,
        int currentAccount,
        long did,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.fragment = fragment;

        this.currentAccount = currentAccount;
        if (DialogObject.isEncryptedDialog(did)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(did));
            if (encryptedChat != null) {
                this.dialogId = encryptedChat.user_id;
            } else {
                this.dialogId = did;
            }
        } else {
            this.dialogId = did;
        }
        StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
        this.list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        this.collections = StarsController.getInstance(currentAccount).getProfileGiftCollectionsList(dialogId, true);
        this.collections.all = list;
        this.list.shown = true;
        if (fragment instanceof ProfileActivity && ((ProfileActivity) fragment).openGiftsUpgradable) {
            this.list.setFilters(StarsController.GiftsList.INCLUDE_TYPE_UPGRADABLE_FLAG);
        } else {
            this.list.resetFilters();
        }
        this.list.load();
        this.resourcesProvider = resourcesProvider;

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                updateButton();
                if (fragment instanceof ProfileActivity) {
                    ((ProfileActivity) fragment).updateSelectedMediaTabText();
                }
                updateTabsY();
            }

            @Override
            protected void onTabScrollEnd(int position) {
                super.onTabScrollEnd(position);
                updateButton();
                if (fragment instanceof ProfileActivity) {
                    ((ProfileActivity) fragment).updateSelectedMediaTabText();
                }
            }

            @Override
            protected boolean canScroll(MotionEvent e) {
                return !isReordering();
            }

            @Override
            protected void addMoreTabs() {
                if (canAdd() && tabsView != null) {
                    if (addCollectionTabText == null) {
                        final SpannableStringBuilder sb = new SpannableStringBuilder("+ " + getString(R.string.Gift2NewCollection));
                        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.poll_add_plus);
                        span.spaceScaleX = .8f;
                        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        addCollectionTabText = sb;
                    }
                    tabsView.addTab(-1, addCollectionTabText);
                }
            }
        };
        viewPager.setAllowDisallowInterceptTouch(true);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 1 + collections.getCollections().size();
            }

            @Override
            public View createView(int viewType) {
                if (viewType == -1) {
                    return null;
                }
                return new Page(ProfileGiftsContainer.this, currentAccount, resourcesProvider);
            }

            @Override
            public int getItemId(int position) {
                if (position == 0) return -2;
                return collections.getCollections().get(position - 1).collection_id;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                final Page page = (Page) view;
                final StarsController.GiftsList thisList;
                final boolean isCollection;
                if (viewType == 0) {
                    isCollection = false;
                    thisList = list;
                } else {
                    isCollection = true;
                    thisList = collections.getListByIndex(position - 1);
                }
                page.bind(isCollection, thisList);
                page.setVisibleHeight(visibleHeight);
                page.setHasTabs(!collections.getCollections().isEmpty());
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0)
                    return 0;
                return 1;
            }

            @Override
            public CharSequence getItemTitle(int position) {
                if (position == 0) {
                    return getString(R.string.Gift2CollectionAll);
                }

                final TL_stars.TL_starGiftCollection collection = collections.getCollections().get(position - 1);
                if (collection == null) {
                    return null;
                }
                final SpannableStringBuilder sb = new SpannableStringBuilder(collection.title);
                if (collection.icon != null) {
                    final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    textPaint.setTextSize(dp(16));
                    final SpannableStringBuilder emoji = new SpannableStringBuilder("e ");
                    final AnimatedEmojiSpan span = new AnimatedEmojiSpan(collection.icon, textPaint.getFontMetricsInt());
                    emoji.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.insert(0, emoji);
                }
                return sb;
            }

            @Override
            public boolean canReorder(int position) {
                if (position == 0)
                    return false;
                return true;
            }

            @Override
            public void applyReorder(ArrayList<Integer> itemIds) {
                final ArrayList<Integer> collectionIds = new ArrayList<>();
                for (final int itemId : itemIds) {
                    if (itemId == -1 || itemId == -2) continue;
                    collectionIds.add(itemId);
                }
                collections.reorder(collectionIds);

                final Page current = getCurrentPage();
                if (current != null) {
                    final int position =
                        !current.isCollection ?
                            0 :
                            1 + collections.indexOf(current.list.collectionId);
                    tabsView.selectTab(position, position, 0.0f);
                }

                AndroidUtilities.cancelRunOnUIThread(sendCollectionsOrder);
                AndroidUtilities.runOnUIThread(sendCollectionsOrder, 1000);
            }
        });
        addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        tabsView = viewPager.createTabsView(true, 9);
        tabsView.tabMarginDp = 12;
        tabsView.setPreTabClick((id, pos) -> {
            resetReordering();
            if (id == -1) {
                createCollection();
                return true;
            }
            return false;
        });
        tabsView.setOnTabLongClick((page, view) -> {
            if (page == -1 || page == -2 || page == 0 || reorderingCollections)
                return false;

            int _index = -1;
            TL_stars.TL_starGiftCollection _collection = null;
            for (int i = 0; i < collections.getCollections().size(); ++i) {
                if (collections.getCollections().get(i).collection_id == page) {
                    _index = i;
                    _collection = collections.getCollections().get(i);
                    break;
                }
            }
            final int index = _index;
            final TL_stars.TL_starGiftCollection collection = _collection;

            final String username = DialogObject.getPublicUsername(MessagesController.getInstance(currentAccount).getUserOrChat(dialogId));
            final boolean isMine = collections.isMine();
            if (TextUtils.isEmpty(username) && !isMine) {
                return false;
            }

            currentMenu = ItemOptions.makeOptions(fragment, view)
                .setScrimViewBackground(new Drawable() {
                    private final Drawable bg = Theme.createRoundRectDrawable(dp(16), dp(16), backgroundColor);
                    private final Rect bgBounds = new Rect();
                    @Override
                    public void draw(@NonNull Canvas canvas) {
                        bgBounds.set(getBounds());
                        bgBounds.inset(dp(2), dp(8));
                        bg.setBounds(bgBounds);
                        bg.draw(canvas);
                    }
                    @Override
                    public void setAlpha(int alpha) {
                        bg.setAlpha(alpha);
                    }
                    @Override
                    public void setColorFilter(@Nullable ColorFilter colorFilter) {}
                    @Override
                    public int getOpacity() {
                        return PixelFormat.TRANSPARENT;
                    }
                })
                .addIf(isMine, R.drawable.menu_gift_add, getString(R.string.Gift2CollectionsAdd), this::addGifts)
                .addIf(!TextUtils.isEmpty(username), R.drawable.msg_share, getString(R.string.Gift2CollectionsShare), () -> {
                    final String link = MessagesController.getInstance(currentAccount).linkPrefix + "/" + username + "/c/" + collection.collection_id;
                    new ShareAlert(context, null, link, false, link, false, resourcesProvider) {
                        @Override
                        protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                            if (!showToast) return;
                            final BulletinFactory bulletinFactory = BulletinFactory.of(fragment);
                            if (bulletinFactory != null) {
                                if (dids.size() == 1) {
                                    long did = dids.keyAt(0);
                                    if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                                        bulletinFactory.createSimpleBulletin(R.raw.saved_messages, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.GiftCollectionSharedToSavedMessages)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                    } else if (did < 0) {
                                        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                                        bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.GiftCollectionSharedTo, topic != null ? topic.title : chat.title)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                    } else {
                                        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                                        bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.GiftCollectionSharedTo, user.first_name)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                    }
                                } else {
                                    bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatPluralString("GiftCollectionSharedToManyChats", dids.size(), dids.size()))).hideAfterBottomSheet(false).show();
                                }
                                try {
                                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                        .show();
                })
                .addIf(isMine, R.drawable.msg_edit, getString(R.string.Gift2CollectionsRename), () -> {
                    openEnterNameAlert(collection.title, newName -> {
                        collections.rename(collection.collection_id, newName);
                        collection.title = newName;
                        fillTabs(true);
                    });
                })
                .addIf(isMine, R.drawable.tabs_reorder, getString(R.string.Gift2CollectionsReorder), () -> {
                    setReorderingCollections(true);
                })
                .addIf(isMine, R.drawable.msg_delete, getString(R.string.Gift2CollectionsDelete), true, () -> {
                    if (index != -1) {
                        collections.removeCollection(collection.collection_id);
                        fillTabs(true);
                        tabsView.scrollToTab(-1, index >= collections.getCollections().size() ? (1 + index - 1) : (1 + index));
                        updateTabsShown(true);
                    }
                });
            currentMenu.show();
            return true;
        });
//        tabsView.setBackgroundColor(backgroundColor);
        addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.TOP));

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        bulletinContainer = new FrameLayout(context);

        checkboxLayout = new LinearLayout(context);
        checkboxLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        checkboxLayout.setClipToPadding(false);
        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkboxLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
        checkbox = new CheckBox2(context, 24, resourcesProvider);
        checkbox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        checkbox.setDrawUnchecked(true);
        checkbox.setChecked(false, false);
        checkbox.setDrawBackgroundAsArc(10);
        checkboxLayout.addView(checkbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        checkboxTextView = new TextView(context);
        checkboxTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        checkboxTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        checkboxTextView.setText(LocaleController.getString(R.string.Gift2ChannelNotify));
        checkboxLayout.addView(checkboxTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
        buttonContainer.addView(checkboxLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER, 0, 1f / AndroidUtilities.density + 6, 0, 6));
        ScaleStateListAnimator.apply(checkboxLayout, 0.025f, 1.5f);
        checkboxLayout.setOnClickListener(v -> {
            checkbox.setChecked(!checkbox.isChecked(), true);
            final boolean willBeNotified = checkbox.isChecked();
            BulletinFactory.of(bulletinContainer, resourcesProvider)
                .createSimpleBulletinDetail(willBeNotified ? R.raw.silent_unmute : R.raw.silent_mute, getString(willBeNotified ? R.string.Gift2ChannelNotifyChecked : R.string.Gift2ChannelNotifyNotChecked))
                .show();

            list.chat_notifications_enabled = willBeNotified;
            if (checkboxRequestId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkboxRequestId, true);
                checkboxRequestId = -1;
            }
            final TL_stars.toggleChatStarGiftNotifications req = new TL_stars.toggleChatStarGiftNotifications();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.enabled = willBeNotified;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                checkboxRequestId = -1;
                if (err != null) {
                    BulletinFactory.of(bulletinContainer, resourcesProvider).showForError(err);
                }
            }));
        });
        if (list.chat_notifications_enabled != null) {
            checkbox.setChecked(list.chat_notifications_enabled, false);
        }

        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        final boolean sendToSpecificDialog = dialogId < 0 || user != null && !UserObject.isUserSelf(user) && !UserObject.isBot(user);

        final SpannableStringBuilder sb = new SpannableStringBuilder("G " + (sendToSpecificDialog ? (dialogId < 0 ? getString(R.string.ProfileGiftsSendChannel) : formatString(R.string.ProfileGiftsSendUser, DialogObject.getShortName(dialogId))) : getString(R.string.ProfileGiftsSend)));
        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_gift_simple);
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sendGiftsToFriendsText = sb;

        final SpannableStringBuilder sb2 = new SpannableStringBuilder("+ " + getString(R.string.ProfileGiftsAdd));
        final ColoredImageSpan span2 = new ColoredImageSpan(R.drawable.filled_add_album);
        sb2.setSpan(span2, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        addGiftsText = sb2;

        button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(sendGiftsToFriendsText, false);
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10 + 1f / AndroidUtilities.density, 10, 10));
        button.setOnClickListener(v -> {
            if (!collections.isMine() || viewPager.getCurrentPosition() == 0) {
                if (sendToSpecificDialog) {
                    new GiftSheet(getContext(), currentAccount, dialogId, null, null)
                        .setBirthday(BirthdayController.getInstance(currentAccount).isToday(dialogId))
                        .show();
                } else {
                    UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STAR_GIFT, 0, BirthdayController.getInstance(currentAccount).getState());
                }
            } else {
                addGifts();
            }
        });

        button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
        checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
        buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;

        addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        updateColors();
        updateTabsShown(false);
    }

    public void updateTabsShown(boolean animated) {
        final boolean shown = !collections.getCollections().isEmpty();
//        if (animated) {
//            tabsView.animate()
//                .translationY(shown ? 0 : dp(-42))
//                .setDuration(200)
//                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
//                .start();
//            viewPager.animate()
//                .translationY(shown ? dp(30) : 0)
//                .setDuration(200)
//                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
//                .start();
//        } else {
//            tabsView.animate().cancel();
//            tabsView.setTranslationY(shown ? 0 : dp(-42));
//            viewPager.animate().cancel();
//            viewPager.setTranslationY(shown ? dp(30) : 0);
//        }
        if (viewPager.getViewPages() != null) {
            final View[] views = viewPager.getViewPages();
            for (View view : views) {
                if (view instanceof Page) {
                    ((Page) view).setHasTabs(shown);
                }
            }
        }
    }

    public float getTabsHeight() {
        float h = 0;
        if (viewPager.getViewPages() != null) {
            final View[] views = viewPager.getViewPages();
            for (View view : views) {
                if (view instanceof Page) {
                    h += (1.0f - (float) view.getTranslationX() / view.getWidth()) * ((Page) view).getTabsHeight();
                }
            }
        }
        return h;
    }

    public void updateTabsY() {
        if (tabsView == null) return;
        final float ty = Math.min(0, getTabsHeight() - dp(42));
        final float alpha = clamp01(ilerp(ty, -dp(42), 0));
        tabsView.setTranslationY(ty);
        tabsView.setAlpha(alpha);
    }

    protected void updatedReordering(boolean reordering) {

    }

    private boolean reorderingCollections;

    public boolean isReordering() {
        if (reorderingCollections)
            return true;
        final Page currentPage = getCurrentPage();
        return currentPage != null && currentPage.isReordering();
    }

    public void setReordering(boolean reordering) {
        final Page currentPage = getCurrentPage();
        if (currentPage != null) {
            currentPage.setReordering(reordering);
        }
    }

    private int pendingScrollToCollectionId;
    public void scrollToCollectionId(int collectionId) {
        pendingScrollToCollectionId = collectionId;
        checkScrollToCollection();
    }

    private void checkScrollToCollection() {
        if (pendingScrollToCollectionId <= 0) return;
        int index = -1;
        TL_stars.TL_starGiftCollection collection = null;
        final ArrayList<TL_stars.TL_starGiftCollection> collections = this.collections.getCollections();
        for (int i = 0; i < collections.size(); ++i) {
            if (collections.get(i).collection_id == pendingScrollToCollectionId) {
                collection = collections.get(i);
                index = i;
                break;
            }
        }
        if (index >= 0 && collection != null) {
            pendingScrollToCollectionId = 0;
            tabsView.scrollToTab(collection.collection_id, 1 + index);
        }
    }

    private final Runnable sendCollectionsOrder = () -> {
        ProfileGiftsContainer.this.collections.sendOrder();
    };

    public void setReorderingCollections(boolean reordering) {
        if (this.reorderingCollections == reordering) return;
        this.reorderingCollections = reordering;
        updatedReordering(isReordering());
        tabsView.setReordering(reordering);
        if (reordering) {
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment instanceof ProfileActivity) {
                ((ProfileActivity) lastFragment).scrollToSharedMedia(false);
                AndroidUtilities.runOnUIThread(() -> {
                    ((ProfileActivity) lastFragment).scrollToSharedMedia(true);
                });
            }
        }
        if (!reordering) {
            AndroidUtilities.cancelRunOnUIThread(sendCollectionsOrder);
            AndroidUtilities.runOnUIThread(sendCollectionsOrder);
        }
    }

    public void resetReordering() {
        final Page currentPage = getCurrentPage();
        if (currentPage != null) {
            currentPage.resetReordering();
        }
        setReorderingCollections(false);
    }

    public boolean canAdd() {
        if (!collections.isMine()) return false;
        return collections.getCollections().size() < MessagesController.getInstance(currentAccount).config.stargiftsCollectionsLimit.get();
    }

    private boolean shouldHideButton(int page) {
        if (page == 0) return false;
        final int index = page - 1;
        if (index < 0 || index >= collections.getCollections().size()) return true;
        final StarsController.GiftsList list = collections.getListByIndex(index);
        if (list == null) return true;
        return list.gifts.isEmpty();
    }

    public void updateButton() {
        if (viewPager == null) return;
        float ty;
        if (viewPager.getCurrentPosition() == viewPager.getNextPosition()) {
            final float hide = shouldHideButton(viewPager.getCurrentPosition()) ? 1.0f : 0.0f;
            ty = (dp(10 + 48 + 10) + 2) * hide;
        } else {
            final float hide = (
                (shouldHideButton(viewPager.getCurrentPosition()) ? 1.0f : 0.0f) * viewPager.getCurrentPositionAlpha() +
                (shouldHideButton(viewPager.getNextPosition()) ? 1.0f : 0.0f) * viewPager.getNextPositionAlpha()
            );
            ty = (dp(10 + 48 + 10) + 2) * hide;
        }
        ty += -buttonContainer.getTop() + Math.max(dp(240), visibleHeight) - dp(buttonContainerHeightDp) - 1;
        bulletinContainer.setTranslationY(ty - dp(200));
        buttonContainer.setTranslationY(ty);
        button.setText(!collections.isMine() || viewPager.getPositionAnimated() < 0.5f ? sendGiftsToFriendsText : addGiftsText, true);
        Bulletin.updateCurrentPosition();
    }

    public int getBottomOffset() {
        float ty = buttonContainer.getTranslationY();
        ty -= -buttonContainer.getTop() + Math.max(dp(240), visibleHeight) - dp(buttonContainerHeightDp) - 1;
        if (visibleHeight < dp(240)) {
            ty += Math.min(dp(240) - visibleHeight, dp(buttonContainerHeightDp));
        }
        return (int) (dp(buttonContainerHeightDp) - ty);
    }

    public boolean canFilter() {
        return true;
    }

    public boolean canFilterHidden() {
        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) return true;
        if (dialogId >= 0) return false;
        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        return ChatObject.canUserDoAction(chat, ChatObject.ACTION_POST);
    }

    public boolean canReorder() {
        if (dialogId >= 0) return dialogId == 0 || dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        return ChatObject.canUserDoAction(chat, ChatObject.ACTION_POST);
    }

    public boolean canSwitchNotify() {
        if (dialogId >= 0) return false;
        return list.chat_notifications_enabled != null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((Long) args[0] != dialogId) return;

            button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
            checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
            buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;
            if (list.chat_notifications_enabled != null) {
                checkbox.setChecked(list.chat_notifications_enabled, true);
            }
        } else if (id == NotificationCenter.starUserGiftCollectionsLoaded) {
            if ((Long) args[0] != dialogId) return;
            fillTabs(true);
            updateTabsShown(true);
        } else if (id == NotificationCenter.updateInterfaces) {
            button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
            checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
            buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;
            setVisibleHeight(visibleHeight);
        }
    }

    public Page getCurrentPage() {
        final View view = viewPager.getCurrentView();
        if (view == null) {
            return null;
        }
        return (Page) view;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftCollectionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        final Page currentPage = getCurrentPage();
        if (currentPage != null) {
            currentPage.update(false);
        }
        fillTabs(false);
        updateTabsShown(false);
        if (list != null) {
            list.shown = true;
            list.load();
        }
        if (collections != null) {
            collections.shown = true;
            collections.load();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        final Page currentPage = getCurrentPage();
        resetReordering();
        if (currentPage != null) {
            currentPage.resetReordering();
        }
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftCollectionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        if (list != null) {
            list.shown = false;
        }
        if (collections != null) {
            collections.shown = false;
        }
    }

    public StarsController.GiftsList getCurrentList() {
        Page currentPage = getCurrentPage();
        if (currentPage != null) {
            return currentPage.list;
        }
        return list;
    }

    public int getGiftsCount() {
        final Page page = getCurrentPage();
        if (page == null || page.list == list) {
            if (list != null && list.totalCount > 0) return list.totalCount;
        } else {
            if (page.list != null && page.list.totalCount > 0) return page.list.totalCount;
        }
        if (dialogId >= 0) {
            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            return userFull != null ? userFull.stargifts_count : 0;
        } else {
            final TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            return chatFull != null ? chatFull.stargifts_count : 0;
        }
    }

    private final static HashMap<Pair<Integer, Long>, CharSequence> cachedLastEmojis = new HashMap<>();
    public CharSequence getLastEmojis(Paint.FontMetricsInt fontMetricsInt) {
        if (list == null) return "";
        final Pair<Integer, Long> key = new Pair<>(UserConfig.selectedAccount, dialogId);
        if (list.gifts.isEmpty()) {
            if (list.loading) {
                final CharSequence cached = cachedLastEmojis.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            return "";
        }

        final HashSet<Long> giftsIds = new HashSet<>();
        final ArrayList<TLRPC.Document> gifts = new ArrayList<>();
        for (int i = 0; gifts.size() < 3 && i < list.gifts.size(); ++i) {
            final TL_stars.SavedStarGift gift = list.gifts.get(i);
            final TLRPC.Document doc = gift.gift.getDocument();
            if (doc == null) continue;
            if (giftsIds.contains(doc.id)) continue;
            giftsIds.add(doc.id);
            gifts.add(doc);
        }

        if (gifts.isEmpty()) return "";
        final SpannableStringBuilder ssb = new SpannableStringBuilder(" ");
        for (int i = 0; i < gifts.size(); ++i) {
            final SpannableStringBuilder emoji = new SpannableStringBuilder("x");
            final TLRPC.Document sticker = gifts.get(i);
            final AnimatedEmojiSpan span = new AnimatedEmojiSpan(sticker, .9f, fontMetricsInt);
            emoji.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(emoji);
        }

        cachedLastEmojis.put(key, ssb);
        return ssb;
    }

    public long getLastEmojisHash() {
        if (list == null || list.gifts.isEmpty()) return 0;

        long hash = 0;
        int giftsCount = 0;
        final HashSet<Long> giftsIds = new HashSet<>();
        for (int i = 0; giftsCount < 3 && i < list.gifts.size(); ++i) {
            final TL_stars.SavedStarGift gift = list.gifts.get(i);
            final TLRPC.Document doc = gift.gift.getDocument();
            if (doc == null) continue;
            giftsIds.add(doc.id);
            hash = Objects.hash(hash, doc.id);
            giftsCount++;
        }

        return hash;
    }

    private int visibleHeight = AndroidUtilities.displaySize.y;
    public void setVisibleHeight(int height) {
        visibleHeight = height;
        updateButton();
        if (viewPager != null) {
            for (View view : viewPager.getViewPages()) {
                if (view instanceof Page) {
                    ((Page) view).setVisibleHeight(visibleHeight);
                }
            }
        }
    }

    public RecyclerListView getCurrentListView() {
        final Page currentPage = getCurrentPage();
        if (currentPage != null) {
            return currentPage.listView;
        }
        return null;
    }

    public static class TextFactory extends UItem.UItemFactory<LinkSpanDrawable.LinksTextView> {
        static { setup(new TextFactory()); }

        @Override
        public LinkSpanDrawable.LinksTextView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new LinkSpanDrawable.LinksTextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        heightMeasureSpec
                    );
                }
            };
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final LinkSpanDrawable.LinksTextView textView = (LinkSpanDrawable.LinksTextView) view;
            textView.setGravity(item.intValue);
            textView.setTextColor((int) item.longValue);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, item.floatValue);
            textView.setTypeface(item.checked ? null : AndroidUtilities.bold());
            textView.setPadding(item.pad, 0, item.pad, 0);
            textView.setText(item.text);
        }

        public static UItem asBoldText(int color, int gravity, float textSizeDp, CharSequence text) {
            return asText(color, gravity, textSizeDp, text, true, 0);
        }

        public static UItem asText(int color, int gravity, float textSizeDp, CharSequence text) {
            return asText(color, gravity, textSizeDp, text, false, 0);
        }

        public static UItem asText(int color, int gravity, float textSizeDp, CharSequence text, boolean bold, int padding) {
            UItem item = UItem.ofFactory(TextFactory.class);
            item.text = text;
            item.intValue = gravity;
            item.longValue = color;
            item.floatValue = textSizeDp;
            item.pad = padding;
            item.checked = bold;
            return item;
        }
    }

    public void updateColors() {
        setBackgroundColor(backgroundColor = Theme.blendOver(
            Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)
        ));
//        tabsView.setBackgroundColor(backgroundColor);
        button.updateColors();
        button.setBackground(Theme.createRoundRectDrawable(dp(8), processColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))));
        View[] pages = viewPager.getViewPages();
        if (pages != null) {
            for (int i = 0; i < pages.length; ++i) {
                if (pages[i] != null) {
                    ((Page) pages[i]).updateColors();
                }
            }
        }
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        checkboxTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        checkboxLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
    }

    public static class UnpinSheet extends BottomSheet {
        long selectedGift = 0;
        public UnpinSheet(Context context, long dialogId, TL_stars.SavedStarGift newPinned, Theme.ResourcesProvider resourcesProvider, Utilities.Callback0Return<BulletinFactory> whenDone) {
            super(context, false, resourcesProvider);
            fixNavigationBar();

            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
            titleView.setText(getString(R.string.Gift2UnpinAlertTitle));
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 12, 22, 0));

            final TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText, false, resourcesProvider);
            subtitleView.setText(getString(R.string.Gift2UnpinAlertSubtitle));
            layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 4.33f, 22, 10));

            final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);

            final StarsController.GiftsList giftsList = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
            final UniversalRecyclerView listView = new UniversalRecyclerView(context, currentAccount, 0, (items, adapter) -> {
                for (TL_stars.SavedStarGift g : giftsList.gifts) {
                    if (g.pinned_to_top) {
                        items.add(PeerColorActivity.GiftCell.Factory.asGiftCell(g).setChecked(selectedGift == g.gift.id).setSpanCount(1));
                    }
                }
            }, (item, view, position, x, y) -> {
                final long id = ((TL_stars.SavedStarGift) item.object).gift.id;
                if (selectedGift == id) {
                    selectedGift = 0;
                } else {
                    selectedGift = id;
                }
                button.setEnabled(selectedGift != 0);
                if (view.getParent() instanceof ViewGroup) {
                    final ViewGroup p = (ViewGroup) view.getParent();
                    for (int i = 0; i < p.getChildCount(); ++i) {
                        final View child = p.getChildAt(i);
                        if (child instanceof PeerColorActivity.GiftCell) {
                            ((PeerColorActivity.GiftCell) child).setSelected(selectedGift == ((PeerColorActivity.GiftCell) child).getGiftId(), true);
                        }
                    }
                }
            }, null, resourcesProvider) {
                @Override
                public Integer getSelectorColor(int position) {
                    return 0;
                }
            };
            listView.setSpanCount(3);
            listView.setOverScrollMode(OVER_SCROLL_NEVER);
            listView.setScrollEnabled(false);
            layout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 11, 0, 11, 0));

            button.setText(getString(R.string.Gift2UnpinAlertButton), false);
            layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 22, 9, 22, 9));
            button.setEnabled(false);
            button.setOnClickListener(v -> {
                final ArrayList<TL_stars.SavedStarGift> pinned = giftsList.getPinned();
                int index = -1;
                TL_stars.SavedStarGift replacing = null;
                for (int i = 0; i < pinned.size(); ++i) {
                    if (pinned.get(i).gift.id == selectedGift) {
                        index = i;
                        replacing = pinned.get(i);
                        break;
                    }
                }
                if (replacing == null) return;

                replacing.pinned_to_top = false;
                pinned.set(index, newPinned);
                newPinned.pinned_to_top = true;
                giftsList.setPinned(pinned);

                dismiss();
                whenDone.run().createSimpleBulletin(R.raw.ic_pin, formatString(R.string.Gift2ReplacedPinTitle, getGiftName(newPinned.gift)), formatString(R.string.Gift2ReplacedPinSubtitle, getGiftName(replacing.gift))).show();
            });

            setCustomView(layout);
        }
    }

    private void openEnterNameAlert(String editing, Utilities.Callback<String> whenDone) {
        final Context context = getContext();

        Activity activity = AndroidUtilities.findActivity(context);
        View currentFocus = activity != null ? activity.getCurrentFocus() : null;
        final boolean adaptive = false;
        AlertDialog[] dialog = new AlertDialog[1];
        AlertDialog.Builder builder;
        if (adaptive) {
            builder = new AlertDialogDecor.Builder(context, resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(context, resourcesProvider);
        }
        TextView[] positiveButton = new TextView[1];

        if (editing != null) {
            builder.setTitle(getString(R.string.Gift2EditCollectionNameTitle));
        } else {
            builder.setTitle(getString(R.string.Gift2NewCollectionTitle));
            builder.setMessage(getString(R.string.Gift2NewCollectionText));
        }
        final int MAX_LENGTH = 12;
        EditTextCaption editText = new EditTextCaption(context, resourcesProvider) {
            AnimatedColor limitColor = new AnimatedColor(this);
            private int limitCount;
            AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true); {
                limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
                limit.setTextSize(dp(15.33f));
                limit.setCallback(this);
                limit.setGravity(Gravity.RIGHT);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == limit || super.verifyDrawable(who);
            }

            @Override
            protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                super.onTextChanged(text, start, lengthBefore, lengthAfter);

                if (limit != null) {
                    limitCount = MAX_LENGTH - text.length();
                    limit.cancelAnimation();
                    limit.setText(limitCount > 4 ? "" : "" + limitCount);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                limit.setTextColor(limitColor.set(Theme.getColor(limitCount < 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, resourcesProvider)));
                limit.setBounds(getScrollX(), 0, getScrollX() + getWidth(), getHeight());
                limit.draw(canvas);
            }
        };
        editText.lineYFix = true;
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String text = editText.getText().toString();
                    if (text.length() <= 0 || text.length() > MAX_LENGTH) {
                        AndroidUtilities.shakeView(editText);
                        return true;
                    }

                    whenDone.run(text);

                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                    if (currentFocus != null) {
                        currentFocus.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(AndroidUtilities.getCurrentKeyboardLanguage(), true);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setHintText(getString(R.string.Gift2NewCollectionHint));
        editText.setFocusable(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, dp(6), 0, dp(6));

        editText.addTextChangedListener(new TextWatcher() {
            boolean ignoreTextChange;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) {
                    return;
                }
                if (s.length() > MAX_LENGTH) {
                    ignoreTextChange = true;
                    s.delete(MAX_LENGTH, s.length());
                    AndroidUtilities.shakeView(editText);
                    try {
                        editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignore) {}
                    ignoreTextChange = false;
                }
            }
        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        editText.setText(editing);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(dp(292));

        builder.setPositiveButton(getString(editing != null ? R.string.Edit : R.string.Create), (dialogInterface, i) -> {
            String text = editText.getText().toString();
            if (text.length() <= 0 || text.length() > MAX_LENGTH) {
                AndroidUtilities.shakeView(editText);
                return;
            }

            whenDone.run(text);

            dialogInterface.dismiss();
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        dialog[0] = builder.create();
        if (currentMenu != null && currentMenu.actionBarPopupWindow != null) {
            currentMenu.actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        AndroidUtilities.requestAdjustNothing(activity, fragment.getClassGuid());
        dialog[0].setOnDismissListener(d -> {
            AndroidUtilities.hideKeyboard(editText);
            AndroidUtilities.requestAdjustResize(activity, fragment.getClassGuid());
        });
        dialog[0].setOnShowListener(d -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        dialog[0].show();
        dialog[0].setDismissDialogByButtons(false);
        View button = dialog[0].getButton(DialogInterface.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            positiveButton[0] = (TextView) button;
        }
        editText.setSelection(editText.getText().length());
    }

    public void createCollection() {
        openEnterNameAlert(null, name -> {
            collections.createCollection(name, collection -> {
                fillTabs(true);
                tabsView.scrollToTab(collection.collection_id, 1 + collections.indexOf(collection.collection_id));
                if (fragment instanceof ProfileActivity) {
                    ((ProfileActivity) fragment).scrollToSharedMedia(true);
                }
                updateTabsShown(true);
            });
        });
    }

    public void addGifts() {
        final Page currentPage = getCurrentPage();
        if (currentPage == null || currentPage.list == null || !currentPage.isCollection) return;
        final int collectionId = currentPage.list.collectionId;
        new SelectGiftsBottomSheet(fragment, dialogId, collectionId, gifts -> {
            collections.addGifts(collectionId, gifts, true);
            currentPage.update(true);
            fillTabs(true);
            updateTabsShown(true);

            final TL_stars.TL_starGiftCollection collection = collections.findById(collectionId);
            if (collection != null) {
                if (gifts.size() > 1) {
                    final TL_stars.SavedStarGift firstGift = gifts.get(0);
                    final Bulletin bulletin = BulletinFactory.of(fragment)
                        .createSimpleMultiBulletin(
                            firstGift.gift.getDocument(),
                            AndroidUtilities.replaceTags(formatPluralStringComma("Gift2AddedToCollectionMany", gifts.size(), collection.title))
                        );
                    bulletin.hideAfterBottomSheet = false;
                    bulletin.show();
                } else if (gifts.size() == 1) {
                    final TL_stars.SavedStarGift gift = gifts.get(0);
                    final Bulletin bulletin = BulletinFactory.of(fragment)
                        .createSimpleMultiBulletin(
                            gift.gift.getDocument(),
                            AndroidUtilities.replaceTags(formatString(R.string.Gift2AddedToCollection, getGiftName(gift.gift), collection.title))
                        );
                    bulletin.hideAfterBottomSheet = false;
                    bulletin.show();
                }
            }
        }).show();
    }

    public static class SelectGiftsBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

        private final long dialogId;
        private final int collectionId;
        private final StarsController.GiftsList list;
        private final HashSet<Long> selectedGiftIds = new HashSet<>();

        private final ExtendedGridLayoutManager layoutManager;

        private final FrameLayout buttonContainer;
        private final ButtonWithCounterView button;

        private ItemOptions lastMenu;

        public SelectGiftsBottomSheet(
            BaseFragment fragment,
            long dialogId,
            int collectionId,

            Utilities.Callback<ArrayList<TL_stars.SavedStarGift>> whenSelected
        ) {
            super(fragment, false, false, ActionBarType.SLIDING);

            ignoreTouchActionBar = false;
            headerMoveTop = dp(12);

            fixNavigationBar();
            setSlidingActionBar();

            this.dialogId = dialogId;
            this.collectionId = collectionId;
            this.list = new StarsController.GiftsList(currentAccount, dialogId);

            final ActionBarMenu menu = actionBar.createMenu();
            final ActionBarMenuItem other = menu.addItem(1, R.drawable.ic_ab_other);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == 1) {
                        if (lastMenu != null) {
                            lastMenu.dismiss();
                        }
                        final ItemOptions o = lastMenu = ItemOptions.makeOptions(container, resourcesProvider, other);
                        final boolean hiddenFilters;

                        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            hiddenFilters = true;
                        } else if (dialogId >= 0) {
                            hiddenFilters = false;
                        } else {
                            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                            hiddenFilters = ChatObject.canUserDoAction(chat, ChatObject.ACTION_POST);
                        }

                        final ActionBarMenuSubItem sorting = o.add();
                        o.addGap();
                        final ActionBarMenuSubItem unlimited = o.addChecked();
                        unlimited.setText(getString(R.string.Gift2FilterUnlimited));
                        final ActionBarMenuSubItem limited = o.addChecked();
                        limited.setText(getString(R.string.Gift2FilterLimited));
                        final ActionBarMenuSubItem upgradable = o.addChecked();
                        upgradable.setText(getString(R.string.Gift2FilterUpgradable));
                        final ActionBarMenuSubItem unique = o.addChecked();
                        unique.setText(getString(R.string.Gift2FilterUnique));
                        final ActionBarMenuSubItem displayed, hidden;
                        if (hiddenFilters) {
                            o.addGap();
                            displayed = o.addChecked();
                            displayed.setText(getString(R.string.Gift2FilterDisplayed));
                            hidden = o.addChecked();
                            hidden.setText(getString(R.string.Gift2FilterHidden));
                        } else {
                            displayed = null;
                            hidden = null;
                        }

                        final Runnable update = () -> {
                            if (sorting != null) {
                                sorting.setTextAndIcon(getString(list.sort_by_date ? R.string.Gift2FilterSortByValue : R.string.Gift2FilterSortByDate), list.sort_by_date ? R.drawable.menu_sort_value : R.drawable.menu_sort_date);
                            }

                            unlimited.setChecked(list.isInclude_unlimited());
                            limited.setChecked(list.isInclude_limited());
                            upgradable.setChecked(list.isInclude_upgradable());
                            unique.setChecked(list.isInclude_unique());

                            if (hiddenFilters) {
                                displayed.setChecked(list.isInclude_displayed());
                                hidden.setChecked(list.isInclude_hidden());
                            }
                        };
                        update.run();

                        if (sorting != null) {
                            sorting.setOnClickListener(v -> {
                                list.sort_by_date = !list.sort_by_date;
                                update.run();
                                list.invalidate(true);
                            });
                        }
                        ProfileGiftsContainer.setGiftFilterOptionsClickListeners(unlimited, list, update, StarsController.GiftsList.INCLUDE_TYPE_UNLIMITED_FLAG);
                        ProfileGiftsContainer.setGiftFilterOptionsClickListeners(limited, list, update, StarsController.GiftsList.INCLUDE_TYPE_LIMITED_FLAG);
                        ProfileGiftsContainer.setGiftFilterOptionsClickListeners(upgradable, list, update, StarsController.GiftsList.INCLUDE_TYPE_UPGRADABLE_FLAG);
                        ProfileGiftsContainer.setGiftFilterOptionsClickListeners(unique, list, update, StarsController.GiftsList.INCLUDE_TYPE_UNIQUE_FLAG);
                        if (hiddenFilters) {
                            ProfileGiftsContainer.setGiftFilterOptionsClickListeners(displayed, list, update, StarsController.GiftsList.INCLUDE_VISIBILITY_DISPLAYED_FLAG);
                            ProfileGiftsContainer.setGiftFilterOptionsClickListeners(hidden, list, update, StarsController.GiftsList.INCLUDE_VISIBILITY_HIDDEN_FLAG);
                        }
                        o
                            .setOnTopOfScrim()
                            .setDismissWithButtons(false)
                            .setDimAlpha(0)
                            .show();
                    } else if (id == -1) {
                        dismiss();
                    }
                }
            });

            buttonContainer = new FrameLayout(getContext());
            buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            buttonContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

            View buttonShadow = new View(getContext());
            buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

            button = new ButtonWithCounterView(getContext(), resourcesProvider);
            button.setText(getString(R.string.Gift2CollectionAddGiftsButton), false);
            button.setEnabled(false);
            button.setOnClickListener(v -> {
                if (selectedGiftIds.isEmpty()) return;

                final ArrayList<TL_stars.SavedStarGift> selectedGifts = new ArrayList<>();
                for (long msg_id : selectedGiftIds) {
                    TL_stars.SavedStarGift gift = null;
                    for (TL_stars.SavedStarGift g : list.gifts) {
                        if (g.msg_id != 0 && g.msg_id == msg_id || g.saved_id == msg_id) {
                            gift = g;
                            break;
                        }
                    }

                    if (gift != null) {
                        selectedGifts.add(gift);
                    }
                }

                whenSelected.run(selectedGifts);
                dismiss();
            });
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10 + (1.0f / AndroidUtilities.density), 10, 10));

            layoutManager = new ExtendedGridLayoutManager(getContext(), 3);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (adapter == null)
                        return layoutManager.getSpanCount();
                    final UItem item = adapter.getItem(position - 1);
                    if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                        return layoutManager.getSpanCount();
                    return item.spanCount;
                }
            });
            this.recyclerListView.setPadding(backgroundPaddingLeft + dp(9), 0, backgroundPaddingLeft + dp(9), 0);
            this.recyclerListView.setSelectorType(9);
            this.recyclerListView.setSelectorDrawableColor(0);
            this.recyclerListView.setLayoutManager(layoutManager);
            this.recyclerListView.setOnItemClickListener((view, position) -> {
                if (adapter == null)
                    return;
                final UItem item = adapter.getItem(position - 1);
                if (item != null && item.object instanceof TL_stars.SavedStarGift) {
                    TL_stars.SavedStarGift g = (TL_stars.SavedStarGift) item.object;
                    final long id = g.msg_id == 0 ? g.saved_id : g.msg_id;
                    if (selectedGiftIds.contains(id)) {
                        selectedGiftIds.remove(id);
                        ((GiftSheet.GiftCell) view).setChecked(false, true);
                    } else {
                        selectedGiftIds.add(id);
                        ((GiftSheet.GiftCell) view).setChecked(true, true);
                    }

                    button.setEnabled(selectedGiftIds.size() > 0);
                    button.setCount(selectedGiftIds.size(), true);
                }
            });
            this.recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (isLoadingVisible()) {
                        list.load();
                    }
                }
            });
            final DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            this.recyclerListView.setItemAnimator(itemAnimator);

            adapter.update(true);

            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starUserGiftsLoaded) {
                if (this.adapter != null) {
                    this.adapter.update(true);
                    if (isLoadingVisible()) {
                        list.load();
                    }
                }
            }
        }

        private boolean isLoadingVisible() {
            if (this.recyclerListView == null || !this.recyclerListView.isAttachedToWindow())
                return false;
            for (int i = 0; i < this.recyclerListView.getChildCount(); ++i) {
                if (this.recyclerListView.getChildAt(i) instanceof FlickerLoadingView)
                    return true;
            }
            return false;
        }

        @Override
        public void dismiss() {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
            super.dismiss();
        }

        @Override
        protected CharSequence getTitle() {
            return getString(R.string.Gift2CollectionAddGiftsTitle);
        }

        private UniversalAdapter adapter;
        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, this::fillItems, resourcesProvider);
            adapter.setApplyBackground(false);
            return adapter;
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (list == null) return;

            items.add(UItem.asSpace(dp(16)));

            if (list.loading && list.gifts.isEmpty()) {
                items.add(UItem.asFlicker(1, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(2, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(3, FlickerLoadingView.STAR_GIFT).setSpanCount(1));

                items.add(UItem.asFlicker(4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));

                items.add(UItem.asFlicker(7, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(8, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(9, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            } else {
                int spanCountLeft = 3;
                for (TL_stars.SavedStarGift g : list.gifts) {
                    if (g.collection_id.contains(collectionId))
                        continue;
                    items.add(
                        GiftSheet.GiftCell.Factory.asStarGift(0, g, true, true, false)
                            .setChecked(selectedGiftIds.contains(g.msg_id == 0 ? g.saved_id : g.msg_id))
                            .setSpanCount(1)
                    );
                    spanCountLeft--;
                    if (spanCountLeft == 0) {
                        spanCountLeft = 3;
                    }
                }
                if (list.loading || !list.endReached) {
                    for (int i = 0; i < (spanCountLeft <= 0 ? 3 : spanCountLeft); ++i) {
                        items.add(UItem.asFlicker(1 + i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                    }
                }
            }

            items.add(UItem.asSpace(dp(10 + 48 + 10)));
        }
    }

    public static void setGiftFilterOptionsClickListeners(View view, StarsController.GiftsList list, Runnable update, int flag) {
        view.setOnClickListener(v -> {
            list.toggleTypeIncludeFlag(flag);
            update.run();
        });
        view.setOnLongClickListener(v -> {
            list.forceTypeIncludeFlag(flag, true);
            update.run();
            return true;
        });
    }
}
