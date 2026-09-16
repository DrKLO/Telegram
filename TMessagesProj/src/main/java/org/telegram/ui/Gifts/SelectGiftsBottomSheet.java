package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;

public class SelectGiftsBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private final long dialogId;
    private final int collectionId;
    private final StarsController.GiftsList list;
    private final HashSet<Long> selectedGiftIds = new HashSet<>();

    private final ExtendedGridLayoutManager layoutManager;

    private final FrameLayout buttonContainer;
    private final FrameLayout buttonContainerInternal;
    private final ButtonWithCounterView button;


    private ItemOptions lastMenu;

    private final BlurredBackgroundSourceColor iBlur3SourceButtonColor;
    private final BlurredBackgroundDrawableViewFactory iBlur3ButtonFactory;
    private final BlurredBackgroundSourceColor iBlur3SourceBackgroundColor;
    private final BlurredBackgroundDrawableViewFactory iBlur3BackgroundFactory;

    public SelectGiftsBottomSheet(
            BaseFragment fragment,
            long dialogId,
            int collectionId,

            Utilities.Callback<ArrayList<TL_stars.SavedStarGift>> whenSelected
    ) {
        super(fragment.getParentActivity(), fragment, new Params.Builder()
            .actionBarType(ActionBarType.SLIDING)
            .edgeToEdge(EdgeToEdge.V2)
            .resourcesProvider(fragment.getResourceProvider())
            .build());

        final Context context = getContext();

        iBlur3SourceButtonColor = new BlurredBackgroundSourceColor();
        iBlur3SourceButtonColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        iBlur3ButtonFactory = new BlurredBackgroundDrawableViewFactory(iBlur3SourceButtonColor);
        iBlur3SourceBackgroundColor = new BlurredBackgroundSourceColor();
        iBlur3SourceBackgroundColor.setColor(getThemedColor(Theme.key_windowBackgroundGray));
        iBlur3BackgroundFactory = new BlurredBackgroundDrawableViewFactory(iBlur3SourceBackgroundColor);

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);

        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
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

        button = new ButtonWithCounterView(getContext(), resourcesProvider);
        button.setText(getString(R.string.Gift2CollectionAddGiftsButton), false);
        button.setEnabled(false);
        button.setStateListAnimator(null);
        button.setRound();

        buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        buttonContainerInternal = new FrameLayout(context);
        buttonContainerInternal.setPadding(dp(8), dp(8), dp(8), dp(8));
        buttonContainerInternal.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        buttonContainerInternal.setOnClickListener(v -> {
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
        buttonContainerInternal.setBackground(iBlur3ButtonFactory.create(buttonContainerInternal)
                .setColorProvider(BlurredBackgroundProviderImpl.premiumButton(resourcesProvider))
                .setRadius(dp(28))
                .setPadding(dp(5)));
        ScaleStateListAnimator.apply(buttonContainerInternal, 0.02f, 1.5f);
        buttonContainer.addView(buttonContainerInternal, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48 + 8 + 8, Gravity.BOTTOM, 4, 0, 4, 0));

        BlurredBackgroundWithFadeDrawable fade = new BlurredBackgroundWithFadeDrawable(iBlur3BackgroundFactory.create(buttonContainer));
        fade.setFadeHeight(dp(40), true);
        fade.setAlpha(220);
        buttonContainer.setBackground(fade);
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

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
        this.recyclerListView.setClipToPadding(false);
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
                    item.checked = false;
                } else {
                    selectedGiftIds.add(id);
                    ((GiftSheet.GiftCell) view).setChecked(true, true);
                    item.checked = true;
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


        checkUi_insets();
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

    private void checkUi_insets() {
        recyclerListView.setPadding(
            insets.left + backgroundPaddingLeft + dp(9), 0,
            insets.right + backgroundPaddingLeft + dp(9), insets.bottom);
        buttonContainer.setPadding(
            insets.left + backgroundPaddingLeft, 0,
            insets.right + backgroundPaddingLeft, insets.bottom);
    }

    private Insets insets = Insets.NONE;

    @NonNull
    protected WindowInsetsCompat onApplyWindowInsetsToRoot(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        this.insets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        checkUi_insets();

        return WindowInsetsCompat.CONSUMED;
    }
}
