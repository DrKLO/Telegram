package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorHeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorSearchCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MultiContactsSelectorBottomSheet extends BottomSheetWithRecyclerListView {
    private static MultiContactsSelectorBottomSheet instance;

    public interface SelectorListener {
        void onUserSelected(List<Long> ids);
    }

    public static void open(Boolean bots, Boolean premium, int maxCount, SelectorListener selectorListener) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return;
        }
        if (instance != null) {
            return;
        }
        MultiContactsSelectorBottomSheet sheet = new MultiContactsSelectorBottomSheet(fragment, true, maxCount, bots, premium, selectorListener);
        sheet.show();
        instance = sheet;
    }

    private static final int BOTTOM_HEIGHT_DP = 60;

    private final ButtonWithCounterView actionButton;
    private final SelectorSearchCell searchField;
    private final View sectionCell;
    private final SelectorHeaderCell headerView;
    private final SelectorBtnCell buttonContainer;

    private final ArrayList<SelectorAdapter.Item> oldItems = new ArrayList<>();
    private final ArrayList<SelectorAdapter.Item> items = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();
    private final List<TLRPC.TL_contact> contacts = new ArrayList<>();
    private final List<TLRPC.TL_topPeer> hints = new ArrayList<>();
    private final List<TLRPC.User> foundUsers = new ArrayList<>();
    private final Map<String, List<TLRPC.TL_contact>> contactsMap = new HashMap<>();
    private final List<String> contactsLetters = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> allSelectedObjects = new LinkedHashMap<>();
    private String query;
    private SelectorAdapter selectorAdapter;
    private int listPaddingTop = AndroidUtilities.dp(56 + 64);
    private int lastRequestId = -1;
    private float recipientsBtnExtraSpace;
    private ReplacementSpan recipientsBtnSpaceSpan;
    private int maxCount;
    private SelectorListener selectorListener;

    private Boolean filterBots;
    private Boolean filterPremium;
    private boolean filter(TLRPC.User user) {
        if (user == null)
            return false;
        if (filterBots != null && UserObject.isBot(user) != filterBots)
            return false;
        if (filterPremium != null && user != null && user.premium != filterPremium)
            return false;
        return true;
    }

    private final Runnable remoteSearchRunnable = new Runnable() {
        @Override
        public void run() {
            final String finalQuery = query;
            if (finalQuery != null) {
                loadData(finalQuery);
            }
        }
    };

    private void loadData(String query) {
        if (lastRequestId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastRequestId, true);
            lastRequestId = -1;
        }
        BoostRepository.searchContactsLocally(query, filterBots != null && filterBots, users -> {
            HashSet<Long> foundUserIds = new HashSet<>();
            foundUsers.clear();
            if (users != null) {
                for (TLRPC.User user : users) {
                    if (user != null && !foundUserIds.contains(user.id) && filter(user)) {
                        foundUsers.add(user);
                        foundUserIds.add(user.id);
                    }
                }
            }
            if (filterBots != null && filterBots) {
                lastRequestId = BoostRepository.searchContacts(query, true, newUsers -> {
                    if (newUsers != null) {
                        for (TLRPC.User user : newUsers) {
                            if (user != null && !foundUserIds.contains(user.id) && filter(user)) {
                                foundUsers.add(user);
                                foundUserIds.add(user.id);
                            }
                        }
                    }
                    updateList(true, true);
                });
            } else {
                updateList(true, true);
            }
        });
    }

    private void createRecipientsBtnSpaceSpan() {
        recipientsBtnSpaceSpan = new ReplacementSpan() {
            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                return (int) recipientsBtnExtraSpace;
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {

            }
        };
    }

    public MultiContactsSelectorBottomSheet(BaseFragment fragment, boolean needFocus, int maxCount, Boolean bot, Boolean premium, SelectorListener selectorListener) {
        super(fragment, needFocus, false, false, fragment.getResourceProvider());
        this.maxCount = maxCount;
        this.filterBots = bot;
        this.filterPremium = premium;
        this.selectorListener = selectorListener;
        actionBar.setTitle(getTitle());

        headerView = new SelectorHeaderCell(getContext(), resourcesProvider) {
            @Override
            protected int getHeaderHeight() {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    return dp(48);
                } else {
                    return dp(54);
                }
            }
        };
        headerView.setOnCloseClickListener(this::dismiss);
        headerView.setText(getTitle());
        headerView.setCloseImageVisible(false);
        headerView.backDrawable.setRotation(0f, false);

        createRecipientsBtnSpaceSpan();

        searchField = new SelectorSearchCell(getContext(), resourcesProvider, null) {
            private boolean isKeyboardVisible;

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                listPaddingTop = getMeasuredHeight() + dp(64);
                selectorAdapter.notifyChangedLast();
                if (isKeyboardVisible != isKeyboardVisible()) {
                    isKeyboardVisible = isKeyboardVisible();
                    if (isKeyboardVisible) {
                        scrollToTop(true);
                    }
                }
            }
        };
        searchField.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        searchField.setOnSearchTextChange(this::onSearch);
        searchField.setHintText(LocaleController.getString(R.string.Search), false);

        sectionCell = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawColor(getThemedColor(Theme.key_graySection));
            }
        };

        containerView.addView(headerView, 0, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        containerView.addView(searchField, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        containerView.addView(sectionCell, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, null);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(10), dp(10), dp(10), dp(10));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        actionButton = new ButtonWithCounterView(getContext(), resourcesProvider) {
            @Override
            protected float calculateCounterWidth(float width, float percent) {
                boolean needUpdateActionBtn = recipientsBtnExtraSpace == 0;
                recipientsBtnExtraSpace = width;
                if (needUpdateActionBtn) {
                    createRecipientsBtnSpaceSpan();
                    updateActionButton(false);
                }
                return width;
            }
        };
        actionButton.setOnClickListener(v -> next());
        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        selectorAdapter.setData(items, recyclerListView);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(BOTTOM_HEIGHT_DP));
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
                long id = user.id;
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                    allSelectedObjects.put(id, user);
                }
                if (selectedIds.size() == maxCount + 1) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return;
                }
                searchField.updateSpans(true, selectedIds, () -> {
                    updateList(true, false);
                }, null);
                updateList(true, false);
                clearSearchAfterSelect();
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

        searchField.setText("");
        searchField.spansContainer.removeAllSpans(false);
        searchField.updateSpans(false, selectedIds, () -> {
            updateList(true, false);
        }, null);
        headerView.setText(getTitle());
        updateActionButton(false);

        contacts.addAll(ContactsController.getInstance(currentAccount).contacts);
        contactsMap.putAll(ContactsController.getInstance(currentAccount).usersSectionsDict);
        contactsLetters.addAll(ContactsController.getInstance(currentAccount).sortedUsersSectionsArray);
        hints.addAll(MediaDataController.getInstance(currentAccount).hints);
        if (filterBots != null && filterBots) {
            hints.addAll(MediaDataController.getInstance(currentAccount).webapps);
        }
        updateList(false, true);
        fixNavigationBar();
    }

    @Override
    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {
        float minTop = AndroidUtilities.statusBarHeight + (headerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(40)) / 2f;
        float fromY = Math.max(top, minTop) + AndroidUtilities.dp(8);
        headerView.setTranslationY(fromY);
        searchField.setTranslationY(headerView.getTranslationY() + headerView.getMeasuredHeight());
        sectionCell.setTranslationY(searchField.getTranslationY() + searchField.getMeasuredHeight());
        recyclerListView.setTranslationY(headerView.getMeasuredHeight() + searchField.getMeasuredHeight() + sectionCell.getMeasuredHeight() - AndroidUtilities.dp(8));
    }

    private void next() {
        if (selectedIds.size() == 0 || selectorListener == null) {
            return;
        }
        List<Long> selectedUsers = new ArrayList<>();
        for (TLRPC.User object : allSelectedObjects.values()) {
            if (selectedIds.contains(object.id)) {
                selectedUsers.add(object.id);
            }
        }
        selectorListener.onUserSelected(selectedUsers);
        dismiss();
    }

    public void scrollToTop(boolean animate) {
        if (animate) {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .6f);
            linearSmoothScroller.setTargetPosition(1);
            linearSmoothScroller.setOffset(AndroidUtilities.dp(36));
            recyclerListView.getLayoutManager().startSmoothScroll(linearSmoothScroller);
        } else {
            recyclerListView.scrollToPosition(0);
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        instance = null;
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
    }

    private void showMaximumUsersToast() {
        String text = LocaleController.formatPluralString("BotMultiContactsSelectorLimit", maxCount);
        BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, text).show(true);
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
    }

    private void updateList(boolean animated, boolean notify) {
        updateItems(animated, notify);
        updateCheckboxes(animated);
        updateActionButton(animated);
    }

    private void updateCheckboxes(boolean animated) {
        int visibleItemsFrom = -1;
        int visibleItemsTo = 0;
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof SelectorUserCell) {
                int position = recyclerListView.getChildAdapterPosition(child);
                if (position <= 0) {
                    continue;
                }
                if (visibleItemsFrom == -1) {
                    visibleItemsFrom = position;
                }
                visibleItemsTo = position;
                if (position - 1 < 0 || position - 1 >= items.size())
                    continue;
                SelectorAdapter.Item item = items.get(position - 1);
                SelectorUserCell cell = (SelectorUserCell) child;
                cell.setChecked(item.checked, animated);
                if (item.chat != null) {
                    cell.setCheckboxAlpha(selectorAdapter.getParticipantsCount(item.chat) > 200 ? .3f : 1f, animated);
                } else {
                    cell.setCheckboxAlpha(1f, animated);
                }
            }
        }
        if (animated) {
            selectorAdapter.notifyItemRangeChanged(0, visibleItemsFrom);
            selectorAdapter.notifyItemRangeChanged(visibleItemsTo, selectorAdapter.getItemCount() - visibleItemsTo);
        }
    }

    private void updateActionButton(boolean animated) {
        actionButton.setShowZero(false);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        if (selectedIds.size() == 0) {
            stringBuilder.append("d").setSpan(recipientsBtnSpaceSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (filterBots != null && filterBots) {
                stringBuilder.append(LocaleController.getString(maxCount > 1 ? R.string.ChooseBots : R.string.ChooseBot));
            } else {
                stringBuilder.append(LocaleController.getString(R.string.ChooseUsers));
            }
        } else {
            stringBuilder.append(LocaleController.getString(R.string.GiftPremiumProceedBtn));
        }
        actionButton.setCount(selectedIds.size(), true);
        actionButton.setText(stringBuilder, animated, false);
        actionButton.setEnabled(true);
    }

    private void onSearch(String text) {
        this.query = text;
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
        AndroidUtilities.runOnUIThread(remoteSearchRunnable, 100);
    }

    private void clearSearchAfterSelect() {
        if (isSearching()) {
            query = null;
            searchField.setText("");
            AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
            updateItems(true, true);
        }
    }

    private void updateSectionCell(boolean animated) {
        if (selectedIds == null) {
            return;
        }
        if (selectedIds.size() > 0) {
            selectorAdapter.setTopSectionClickListener(v -> {
                selectedIds.clear();
                searchField.spansContainer.removeAllSpans(true);
                updateList(true, false);
            });
        } else {
            selectorAdapter.setTopSectionClickListener(null);
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
        if (isSearching()) {
            for (TLRPC.User foundedUser : foundUsers) {
                h += dp(56);
                items.add(SelectorAdapter.Item.asUser(foundedUser, selectedIds.contains(foundedUser.id)));
            }
        } else {
            if (!hints.isEmpty()) {
                List<SelectorAdapter.Item> userItems = new ArrayList<>();
                for (TLRPC.TL_topPeer hint : hints) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(hint.peer.user_id);
                    if (user.self || user.bot || UserObject.isService(user.id) || UserObject.isDeleted(user)) {
                        continue;
                    }
                    if (!filter(user)) {
                        continue;
                    }
                    h += dp(56);
                    userItems.add(SelectorAdapter.Item.asUser(user, selectedIds.contains(user.id)));
                }
                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(SelectorAdapter.Item.asTopSection(LocaleController.getString(R.string.GiftPremiumFrequentContacts)));
                    items.addAll(userItems);
                }
            }
            final long self = UserConfig.getInstance(currentAccount).getClientUserId();
            if (filterBots != null && filterBots) {
                List<SelectorAdapter.Item> userItems = new ArrayList<>();
                for (TLRPC.Dialog dialog : MessagesController.getInstance(currentAccount).getAllDialogs()) {
                    if (dialog.id < 0) continue;
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog.id);
                    if (!filter(user)) {
                        continue;
                    }
                    h += dp(56);
                    userItems.add(SelectorAdapter.Item.asUser(user, selectedIds.contains(user.id)));
                }
                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(SelectorAdapter.Item.asTopSection(LocaleController.getString(R.string.SearchApps)));
                    items.addAll(userItems);
                }
            }
            for (String contactLetter : contactsLetters) {
                List<SelectorAdapter.Item> userItems = new ArrayList<>();
                List<TLRPC.TL_contact> contacts = contactsMap.get(contactLetter);
                if (contacts == null) continue;

                for (final TLRPC.TL_contact contact : contacts) {
                    if (contact.user_id == self) {
                        continue;
                    }
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (!filter(user)) {
                        continue;
                    }
                    h += dp(56);
                    userItems.add(SelectorAdapter.Item.asUser(user, selectedIds.contains(user.id)));
                }

                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(SelectorAdapter.Item.asLetter(contactLetter.toUpperCase()));
                    items.addAll(userItems);
                }
            }
        }

        if (items.isEmpty()) {
            items.add(SelectorAdapter.Item.asNoUsers());
            h += dp(150);
        }
        int minHeight = (int) (AndroidUtilities.displaySize.y * 0.6f);
        items.add(SelectorAdapter.Item.asPad(Math.max(0, minHeight - h)));

        updateSectionCell(animated);

        if (notify && selectorAdapter != null) {
            if (animated) {
                selectorAdapter.setItems(oldItems, items);
            } else {
                selectorAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateItems(false, true);
    }

    @Override
    protected CharSequence getTitle() {
        if (filterBots != null && filterBots) {
            return LocaleController.getString(maxCount > 1 ? R.string.ChooseBots : R.string.ChooseBot);
        }
        return LocaleController.getString(R.string.ChooseUsers);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        selectorAdapter = new SelectorAdapter(getContext(), true, resourcesProvider);
        selectorAdapter.setGreenSelector(true);
        return selectorAdapter;
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(searchField.getEditText());
        super.dismiss();
    }
}
