package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.translitSafe;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter.Item;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorCountryCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorSearchCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorHeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SelectorBottomSheet extends BottomSheetWithRecyclerListView {

    public interface SelectedObjectsListener {
        void onChatsSelected(List<TLRPC.Chat> chats, boolean animated);

        void onUsersSelected(List<TLRPC.User> users);

        void onCountrySelected(List<TLRPC.TL_help_country> countries);

        default void onShowToast(String text) {

        }
    }

    private static final int BOTTOM_HEIGHT_DP = 60;
    public static final int TYPE_COUNTRY = 3;
    public static final int TYPE_CHANNEL = 2;
    public static final int TYPE_USER = 1;

    private final ButtonWithCounterView actionButton;
    private final SelectorSearchCell searchField;
    private final GraySectionCell sectionCell;
    private final SelectorHeaderCell headerView;
    private final SelectorBtnCell buttonContainer;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Item> oldItems = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();
    private final HashSet<Long> openedIds = new HashSet<>();
    private final ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();
    private final ArrayList<TLRPC.InputPeer> users = new ArrayList<>();
    private final Map<String, List<TLRPC.TL_help_country>> countriesMap = new HashMap<>();
    private final List<String> countriesLetters = new ArrayList<>();
    private final List<TLRPC.TL_help_country> countriesList = new ArrayList<>();
    private final HashMap<Long, TLObject> allSelectedObjects = new LinkedHashMap<>();
    private final AnimatedFloat statusBarT;
    private String query;
    private SelectorAdapter selectorAdapter;
    private int listPaddingTop = AndroidUtilities.dp(56 + 78);
    private final TLRPC.Chat currentChat;
    private int type;
    private Runnable onCloseClick;
    private int top;
    private SelectedObjectsListener selectedObjectsListener;
    private final Runnable remoteSearchRunnable = new Runnable() {
        @Override
        public void run() {
            final String finalQuery = query;
            if (finalQuery != null) {
                loadData(type, false, finalQuery);
            }
        }
    };

    public SelectorBottomSheet(BaseFragment fragment, boolean needFocus, long dialogId) {
        super(fragment, needFocus, false);
        backgroundPaddingLeft = 0;
        this.currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);

        ((ViewGroup) actionBar.getParent()).removeView(actionBar); //we used custom action bar

        statusBarT = new AnimatedFloat(containerView, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        headerView = new SelectorHeaderCell(getContext(), resourcesProvider);
        headerView.setOnCloseClickListener(this::dismiss);
        headerView.setText(getTitle());
        headerView.setCloseImageVisible(true);
        headerView.backDrawable.setRotation(0f, false);

        searchField = new SelectorSearchCell(getContext(), resourcesProvider, null) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                listPaddingTop = getMeasuredHeight() + AndroidUtilities.dp(78);
                selectorAdapter.notifyChangedLast();
            }
        };
        searchField.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        searchField.setOnSearchTextChange(this::onSearch);

        sectionCell = new GraySectionCell(getContext(), resourcesProvider);
        updateSection();

        containerView.addView(headerView, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        containerView.addView(searchField, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        containerView.addView(sectionCell, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 32, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, null);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(10), dp(10), dp(10), dp(10));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        actionButton = new ButtonWithCounterView(getContext(), resourcesProvider);
        actionButton.setOnClickListener(v -> save(false));
        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        selectorAdapter.setData(items, recyclerListView);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.dp(BOTTOM_HEIGHT_DP));
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(searchField.getEditText());
                }
            }
        });
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof SelectorUserCell) {
                TLRPC.User user = ((SelectorUserCell) view).getUser();
                TLRPC.Chat chat = ((SelectorUserCell) view).getChat();
                long id = user != null ? user.id : -chat.id;
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                    allSelectedObjects.put(id, user != null ? user : chat);
                }
                if ((selectedIds.size() == 11 && type == TYPE_USER) || (selectedIds.size() == (BoostRepository.giveawayAddPeersMax() + 1) && type == TYPE_CHANNEL)) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return;
                }
                searchField.updateSpans(true, selectedIds, () -> updateList(true, false), null);
                updateList(true, false);
                if (chat != null && !ChatObject.isPublic(chat) && selectedIds.contains(id)) {
                    BoostDialogs.showPrivateChannelAlert(chat, getBaseFragment().getContext(), resourcesProvider, () -> {
                        selectedIds.remove(id);
                        searchField.updateSpans(true, selectedIds, () -> updateList(true, false), null);
                        updateList(true, false);
                    }, this::clearSearchAfterSelectChannel);
                } else {
                    if (chat != null) {
                        clearSearchAfterSelectChannel();
                    }
                }
            }
            if (view instanceof SelectorCountryCell) {
                SelectorCountryCell countryCell = (SelectorCountryCell) view;
                long id = countryCell.getCountry().default_name.hashCode();
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                }
                if (selectedIds.size() == (BoostRepository.giveawayCountriesMax() + 1) && type == TYPE_COUNTRY) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return;
                }
                searchField.updateSpans(true, selectedIds, () -> updateList(true, false), countriesList);
                if (isSearching()) {
                    query = null;
                    searchField.setText("");
                    updateList(false, false);
                    updateList(true, true);
                } else {
                    updateList(true, false);
                }
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int position = parent.getChildAdapterPosition(view);
                if (position == items.size()) {
                    outRect.bottom = listPaddingTop;
                }
            }
        });

        updateList(false, true);
        loadData(TYPE_USER, true, null);
        loadData(TYPE_COUNTRY, true, null);
    }

    private void clearSearchAfterSelectChannel() {
        if (isSearching()) {
            query = null;
            searchField.setText("");
            AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
            peers.clear();
            peers.addAll(BoostRepository.getMyChannels(currentChat.id));
            updateList(false, false);
            updateList(true, true);
        }
    }

    private void save(boolean force) {
        if (selectedIds.size() == 0 && !force) {
            return;
        }
        switch (type) {
            case TYPE_CHANNEL:
                List<TLRPC.Chat> selectedChats = new ArrayList<>();
                for (TLObject object : allSelectedObjects.values()) {
                    if (object instanceof TLRPC.Chat && selectedIds.contains(-((TLRPC.Chat) object).id)) {
                        selectedChats.add((TLRPC.Chat) object);
                    }
                }
                if (selectedObjectsListener != null) {
                    selectedObjectsListener.onChatsSelected(selectedChats, true);
                }
                break;
            case TYPE_USER:
                List<TLRPC.User> selectedUsers = new ArrayList<>();
                for (TLObject object : allSelectedObjects.values()) {
                    if (object instanceof TLRPC.User && selectedIds.contains(((TLRPC.User) object).id)) {
                        selectedUsers.add((TLRPC.User) object);
                    }
                }
                if (selectedObjectsListener != null) {
                    selectedObjectsListener.onUsersSelected(selectedUsers);
                }
                break;
            case TYPE_COUNTRY:
                List<TLRPC.TL_help_country> selectedCountries = new ArrayList<>();
                for (TLRPC.TL_help_country country : countriesList) {
                    long id = country.default_name.hashCode();
                    if (selectedIds.contains(id)) {
                        selectedCountries.add(country);
                    }
                }
                if (selectedObjectsListener != null) {
                    selectedObjectsListener.onCountrySelected(selectedCountries);
                }
                break;
        }
    }

    public void scrollToTop(boolean animate) {
        if (animate) {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .6f);
            linearSmoothScroller.setTargetPosition(1);
            linearSmoothScroller.setOffset(AndroidUtilities.dp(38));
            recyclerListView.getLayoutManager().startSmoothScroll(linearSmoothScroller);
        } else {
            recyclerListView.scrollToPosition(0);
        }
    }

    private void loadData(int localType, boolean preCache, String query) {
        switch (localType) {
            case TYPE_CHANNEL:
                BoostRepository.searchChats(currentChat.id, 0, query, 50, peers -> {
                    if (!isSearching()) {
                        return;
                    }
                    this.peers.clear();
                    this.peers.addAll(peers);
                    updateList(true, true);
                    scrollToTop(true);
                });
                break;
            case TYPE_USER:
                BoostRepository.loadChatParticipants(currentChat.id, 0, query, 0, 50, peers -> {
                    if (preCache) {
                        users.addAll(peers);
                    }
                    if (type == TYPE_USER) {
                        this.peers.clear();
                        this.peers.addAll(peers);
                        updateList(true, true);
                        scrollToTop(true);
                    }
                });
                break;
            case TYPE_COUNTRY:
                BoostRepository.loadCountries(arg -> {
                    if (preCache) {
                        countriesMap.putAll(arg.first);
                        countriesLetters.addAll(arg.second);
                        countriesMap.forEach((s, list) -> countriesList.addAll(list));
                    }
                    if (type == TYPE_COUNTRY) {
                        updateList(true, true);
                        scrollToTop(true);
                    }
                });
                break;
        }
    }

    public boolean hasChanges() {
        if ((selectedIds.size() != openedIds.size() || !openedIds.containsAll(selectedIds) || !selectedIds.containsAll(openedIds))) {
            BoostDialogs.showUnsavedChanges(type, getContext(), resourcesProvider, () -> save(true), () -> {
                selectedIds.clear();
                openedIds.clear();
                dismiss();
            });
            return true;
        }
        return false;
    }

    @Override
    public void dismiss() {
        if (onCloseClick != null) {
            onCloseClick.run();
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
    }

    public void setSelectedObjectsListener(SelectedObjectsListener selectedObjectsListener) {
        this.selectedObjectsListener = selectedObjectsListener;
    }

    public int getTop() {
        return Math.max(0, top - (statusBarT.get() == 1 ? AndroidUtilities.statusBarHeight : 0));
    }

    public void setOnCloseClick(Runnable onCloseClick) {
        this.onCloseClick = onCloseClick;
    }

    public void prepare(List<TLObject> selectedObjects, int type) {
        this.type = type;
        query = null;
        openedIds.clear();
        selectedIds.clear();
        peers.clear();
        allSelectedObjects.clear();

        switch (type) {
            case TYPE_CHANNEL:
                peers.addAll(BoostRepository.getMyChannels(currentChat.id));
                break;
            case TYPE_USER:
                peers.addAll(users);
                break;
            case TYPE_COUNTRY:

                break;
        }

        if (selectedObjects != null) {
            for (TLObject selectedItem : selectedObjects) {
                long id = 0;
                if (selectedItem instanceof TLRPC.TL_inputPeerChat) {
                    id = -((TLRPC.TL_inputPeerChat) selectedItem).chat_id;
                }
                if (selectedItem instanceof TLRPC.TL_inputPeerChannel) {
                    id = -((TLRPC.TL_inputPeerChannel) selectedItem).channel_id;
                }
                if (selectedItem instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) selectedItem).id;
                }
                if (selectedItem instanceof TLRPC.User) {
                    id = ((TLRPC.User) selectedItem).id;
                }
                if (selectedItem instanceof TLRPC.TL_help_country) {
                    id = ((TLRPC.TL_help_country) selectedItem).default_name.hashCode();
                }
                selectedIds.add(id);
                allSelectedObjects.put(id, selectedItem);
            }
        }

        openedIds.addAll(selectedIds);

        searchField.setText("");
        searchField.spansContainer.removeAllSpans(false);
        searchField.updateSpans(false, selectedIds, () -> updateList(true, false), countriesList);

        updateSection();
        updateList(false, true);
        headerView.setText(getTitle());
        updateActionButton(false);
        scrollToTop(false);
    }

    private void updateSection() {
        String text;
        switch (type) {
            case TYPE_CHANNEL:
                text = LocaleController.formatPluralString("BoostingSelectUpToGroupChannelPlural", (int) BoostRepository.giveawayAddPeersMax());
                sectionCell.setLayerHeight(32);
                break;
            case TYPE_USER:
                boolean isChannel = ChatObject.isChannelAndNotMegaGroup(currentChat);
                text = LocaleController.formatPluralStringComma(isChannel ? "Subscribers" : "Members", Math.max(0, selectorAdapter.getParticipantsCount(currentChat) - 1));
                sectionCell.setLayerHeight(32);
                break;
            case TYPE_COUNTRY:
                text = LocaleController.formatPluralString("BoostingSelectUpToCountriesPlural", (int) BoostRepository.giveawayCountriesMax());
                sectionCell.setLayerHeight(1);
                break;
            default:
                text = "";
        }
        sectionCell.setText(text);
    }

    private void showMaximumUsersToast() {
        String text = "";
        switch (type) {
            case TYPE_CHANNEL:
                text = LocaleController.formatPluralString("BoostingSelectUpToWarningChannelsGroupsPlural", (int) BoostRepository.giveawayAddPeersMax());
                break;
            case TYPE_USER:
                text = LocaleController.getString("BoostingSelectUpToWarningUsers", R.string.BoostingSelectUpToWarningUsers);
                break;
            case TYPE_COUNTRY:
                text = LocaleController.formatPluralString("BoostingSelectUpToWarningCountriesPlural", (int) BoostRepository.giveawayCountriesMax());
                break;
        }
        if (selectedObjectsListener != null) {
            selectedObjectsListener.onShowToast(text);
        }
    }

    private void updateList(boolean animated, boolean notify) {
        updateItems(animated, notify);
        updateCheckboxes(animated);
        updateActionButton(animated);
    }

    private void updateCheckboxes(boolean animated) {
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof SelectorUserCell) {
                int position = recyclerListView.getChildAdapterPosition(child);
                if (position < 0) {
                    continue;
                }
                Item item = items.get(position - 1);
                SelectorUserCell cell = (SelectorUserCell) child;
                cell.setChecked(item.checked, animated);
                if (item.chat != null) {
                    cell.setCheckboxAlpha(selectorAdapter.getParticipantsCount(item.chat) > 200 ? .3f : 1f, animated);
                } else {
                    cell.setCheckboxAlpha(1f, animated);
                }
            }
            if (child instanceof SelectorCountryCell) {
                SelectorCountryCell cell = (SelectorCountryCell) child;
                long id = cell.getCountry().default_name.hashCode();
                cell.setChecked(selectedIds.contains(id), true);
            }
        }
    }

    @Override
    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {
        this.top = top;
        float minTop = AndroidUtilities.statusBarHeight + (headerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(40)) / 2f;
        float fromY = Math.max(top, minTop);
        headerView.setTranslationY(fromY);
        searchField.setTranslationY(headerView.getTranslationY() + headerView.getMeasuredHeight());
        sectionCell.setTranslationY(searchField.getTranslationY() + searchField.getMeasuredHeight());
        recyclerListView.setTranslationY(headerView.getMeasuredHeight() + searchField.getMeasuredHeight() + sectionCell.getMeasuredHeight() - AndroidUtilities.dp(16));
        drawFilledStatusBar(canvas, top);
    }

    private void drawFilledStatusBar(Canvas canvas, int top) {
        backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        top = Math.max(0, top);
        top = AndroidUtilities.lerp(top, 0, statusBarT.set(top < AndroidUtilities.statusBarHeight));
        AndroidUtilities.rectTmp.set(backgroundPaddingLeft, top, containerView.getWidth() - backgroundPaddingLeft, containerView.getHeight() + dp(14));
        final float r = dp(14) * (1f - statusBarT.get());
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
    }

    private void updateActionButton(boolean animated) {
        actionButton.setShowZero(false);
        String text;
        switch (type) {
            case TYPE_COUNTRY:
            case TYPE_CHANNEL:
                text = LocaleController.getString("Save", R.string.Save);
                break;
            case TYPE_USER:
                text = LocaleController.getString("BoostingSaveRecipients", R.string.BoostingSaveRecipients);
                break;
            default:
                text = "";
        }
        actionButton.setText(text, animated);
        actionButton.setCount(selectedIds.size(), animated);
        actionButton.setEnabled(selectedIds.size() > 0);
    }

    private void onSearch(String text) {
        this.query = text;
        switch (type) {
            case TYPE_CHANNEL:
                if (!isSearching()) {
                    AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
                    peers.clear();
                    peers.addAll(BoostRepository.getMyChannels(currentChat.id));
                    updateItems(false, true);
                    scrollToTop(true);
                } else {
                    AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
                    AndroidUtilities.runOnUIThread(remoteSearchRunnable, 350);
                }
                break;
            case TYPE_USER:
                AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
                AndroidUtilities.runOnUIThread(remoteSearchRunnable, 350);
                break;
            case TYPE_COUNTRY:
                updateItems(false, true);
                scrollToTop(true);
                break;
        }
    }

    private void updateSectionCell(boolean animated) {
        if (selectedIds.size() > 0 && type != TYPE_COUNTRY) {
            sectionCell.setRightText(LocaleController.getString(R.string.UsersDeselectAll), true, v -> {
                selectedIds.clear();
                searchField.spansContainer.removeAllSpans(true);
                updateList(true, false);
            });
        } else {
            if (animated) {
                sectionCell.setRightText(null);
            } else {
                sectionCell.setRightText(null, null);
            }
        }
    }

    private boolean isSearching() {
        return !TextUtils.isEmpty(query);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateItems(boolean animated, boolean notify) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        int h = 0;
        if (type == TYPE_COUNTRY) {
            for (String countriesLetter : countriesLetters) {
                List<Item> countryItems = new ArrayList<>();
                for (TLRPC.TL_help_country country : countriesMap.get(countriesLetter)) {
                    if (isSearching()) {
                        String q = translitSafe(query).toLowerCase();
                        if (!matchLocal(country, q)) {
                            continue;
                        }
                    }
                    h += dp(44);
                    long id = country.default_name.hashCode();
                    countryItems.add(Item.asCountry(country, selectedIds.contains(id)));
                }

                if (!countryItems.isEmpty()) {
                    h += dp(32);
                    items.add(Item.asLetter(countriesLetter.toUpperCase()));
                    items.addAll(countryItems);
                }
            }
        }

        for (TLRPC.InputPeer peer : peers) {
            h += dp(56);
            items.add(Item.asPeer(peer, selectedIds.contains(DialogObject.getPeerDialogId(peer))));
        }
        if (items.isEmpty()) {
            items.add(Item.asNoUsers());
            h += dp(150);
        }
        int minHeight = (int) (AndroidUtilities.displaySize.y * 0.6f);
        items.add(Item.asPad(Math.max(0, minHeight - h)));

        updateSectionCell(animated);

        if (notify && selectorAdapter != null) {
            if (animated) {
                selectorAdapter.setItems(oldItems, items);
            } else {
                selectorAdapter.notifyDataSetChanged();
            }
        }
    }

    private boolean matchLocal(TLObject obj, String q) {
        if (TextUtils.isEmpty(q)) {
            return true;
        }
        if (obj instanceof TLRPC.TL_help_country) {
            TLRPC.TL_help_country chat = (TLRPC.TL_help_country) obj;
            String name = translitSafe(chat.default_name).toLowerCase();
            if (name.startsWith(q) || name.contains(" " + q)) {
                return true;
            }
            String username = translitSafe(chat.iso2).toLowerCase();
            if (username.startsWith(q) || username.contains(" " + q)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateItems(false, true);
    }

    @Override
    protected CharSequence getTitle() {
        switch (type) {
            case TYPE_CHANNEL:
                return LocaleController.getString("BoostingAddChannelOrGroup", R.string.BoostingAddChannelOrGroup);
            case TYPE_USER:
                return LocaleController.getString("GiftPremium", R.string.GiftPremium);
            case TYPE_COUNTRY:
                return LocaleController.getString("BoostingSelectCountry", R.string.BoostingSelectCountry);
        }
        return "";
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return selectorAdapter = new SelectorAdapter(getContext(), resourcesProvider);
    }
}
