package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
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
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter.Item;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorHeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorSearchCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserSelectorBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {
    private static UserSelectorBottomSheet instance;

    public static void open() {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return;
        }
        if (instance != null) {
            return;
        }
        UserSelectorBottomSheet sheet = new UserSelectorBottomSheet(fragment, true);
        sheet.show();
        instance = sheet;
    }

    public static boolean handleIntent(Intent intent, Browser.Progress progress) {
        Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null) {
                if ((scheme.equals("http") || scheme.equals("https"))) {
                    String host = data.getHost().toLowerCase();
                    if (host.equals("telegram.me") || host.equals("t.me") || host.equals("telegram.dog")) {
                        String path = data.getPath();
                        if (path != null) {
                            if (path.startsWith("/premium_multigift")) {
                                open();
                                return true;
                            }
                        }
                    }
                } else if (scheme.equals("tg")) {
                    String url = data.toString();
                    if (url.startsWith("tg:premium_multigift") || url.startsWith("tg://premium_multigift")) {
                        open();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final int BOTTOM_HEIGHT_DP = 60;

    private final ButtonWithCounterView actionButton;
    private final SelectorSearchCell searchField;
    private final View sectionCell;
    private final SelectorHeaderCell headerView;
    private final SelectorBtnCell buttonContainer;

    private final ArrayList<Item> oldItems = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();
    private final List<TLRPC.TL_contact> contacts = new ArrayList<>();
    private final List<TLRPC.TL_topPeer> hints = new ArrayList<>();
    private final List<TLRPC.User> foundedUsers = new ArrayList<>();
    private final Map<String, List<TLRPC.TL_contact>> contactsMap = new HashMap<>();
    private final List<String> contactsLetters = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> allSelectedObjects = new LinkedHashMap<>();
    private String query;
    private SelectorAdapter selectorAdapter;
    private int listPaddingTop = AndroidUtilities.dp(56 + 64);
    private final List<TLRPC.TL_premiumGiftCodeOption> paymentOptions = new ArrayList<>();
    private boolean isHintSearchText = false;
    private int lastRequestId;
    private float recipientsBtnExtraSpace;
    private ReplacementSpan recipientsBtnSpaceSpan;

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
        lastRequestId = BoostRepository.searchContacts(lastRequestId, query, arg -> {
            foundedUsers.clear();
            foundedUsers.addAll(arg);
            updateList(true, true);
        });
    }

    private void checkEditTextHint() {
        if (selectedIds.size() > 0) {
            if (!isHintSearchText) {
                isHintSearchText = true;
                AndroidUtilities.runOnUIThread(() -> searchField.setHintText(LocaleController.getString("Search", R.string.Search), true), 10);
            }
        } else {
            if (isHintSearchText) {
                isHintSearchText = false;
                AndroidUtilities.runOnUIThread(() -> searchField.setHintText(LocaleController.getString("GiftPremiumUsersSearchHint", R.string.GiftPremiumUsersSearchHint), true), 10);
            }
        }
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

    public UserSelectorBottomSheet(BaseFragment fragment, boolean needFocus) {
        super(fragment, needFocus, false, false, fragment.getResourceProvider());

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
        searchField.setHintText(LocaleController.getString("GiftPremiumUsersSearchHint", R.string.GiftPremiumUsersSearchHint), false);

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
                if (selectedIds.size() == 11) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return;
                }
                checkEditTextHint();
                searchField.updateSpans(true, selectedIds, () -> {
                    checkEditTextHint();
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
            checkEditTextHint();
            updateList(true, false);
        }, null);
        headerView.setText(getTitle());
        updateActionButton(false);
        initContacts(false);
        initHints(false);
        updateList(false, true);
        fixNavigationBar();
        BoostRepository.loadGiftOptions(null, arg -> {
            paymentOptions.clear();
            paymentOptions.addAll(arg);
        });
    }

    private void initContacts(boolean needUpdate) {
        if (contacts.isEmpty()) {
            contacts.addAll(ContactsController.getInstance(currentAccount).contacts);
            contactsMap.putAll(ContactsController.getInstance(currentAccount).usersSectionsDict);
            contactsLetters.addAll(ContactsController.getInstance(currentAccount).sortedUsersSectionsArray);
            if (needUpdate) {
                updateItems(true, true);
            }
        }
    }

    private void initHints(boolean needUpdate) {
        if (hints.isEmpty()) {
            hints.addAll(MediaDataController.getInstance(currentAccount).hints);
            if (needUpdate) {
                updateItems(true, true);
            }
        }
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
        if (selectedIds.size() == 0 || paymentOptions.isEmpty()) {
            return;
        }
        List<TLRPC.User> selectedUsers = new ArrayList<>();
        for (TLRPC.User object : allSelectedObjects.values()) {
            if (selectedIds.contains(object.id)) {
                selectedUsers.add(object);
            }
        }
        AndroidUtilities.hideKeyboard(searchField.getEditText());
        List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(paymentOptions, selectedUsers.size());
        options = BoostRepository.filterGiftOptionsByBilling(options);
        PremiumPreviewGiftToUsersBottomSheet.show(selectedUsers, options);
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
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.giftsToUserSent);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadHints);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.giftsToUserSent);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadHints);
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        instance = null;
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
    }

    private void showMaximumUsersToast() {
        String text = LocaleController.getString("BoostingSelectUpToWarningUsers", R.string.BoostingSelectUpToWarningUsers);
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
                Item item = items.get(position - 1);
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
            stringBuilder.append(LocaleController.getString("GiftPremiumChooseRecipientsBtn", R.string.GiftPremiumChooseRecipientsBtn));
        } else {
            stringBuilder.append(LocaleController.getString("GiftPremiumProceedBtn", R.string.GiftPremiumProceedBtn));
        }
        actionButton.setCount(selectedIds.size(), true);
        actionButton.setText(stringBuilder, animated, false);
        actionButton.setEnabled(true);
    }

    private void onSearch(String text) {
        this.query = text;
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
        AndroidUtilities.runOnUIThread(remoteSearchRunnable, 350);
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
                checkEditTextHint();
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
            for (TLRPC.User foundedUser : foundedUsers) {
                h += dp(56);
                items.add(Item.asUser(foundedUser, selectedIds.contains(foundedUser.id)));
            }
        } else {
            if (!hints.isEmpty()) {
                List<Item> userItems = new ArrayList<>();
                for (TLRPC.TL_topPeer hint : hints) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(hint.peer.user_id);
                    if (user.self || user.bot || UserObject.isService(user.id) || UserObject.isDeleted(user)) {
                        continue;
                    }
                    h += dp(56);
                    userItems.add(Item.asUser(user, selectedIds.contains(user.id)));
                }
                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(Item.asTopSection(LocaleController.getString("GiftPremiumFrequentContacts", R.string.GiftPremiumFrequentContacts)));
                    items.addAll(userItems);
                }
            }
            for (String contactLetter : contactsLetters) {
                List<Item> userItems = new ArrayList<>();
                for (TLRPC.TL_contact contact : contactsMap.get(contactLetter)) {
                    long myUid = UserConfig.getInstance(currentAccount).getClientUserId();
                    if (contact.user_id == myUid) {
                        continue;
                    }
                    h += dp(56);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    userItems.add(Item.asUser(user, selectedIds.contains(user.id)));
                }

                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(Item.asLetter(contactLetter.toUpperCase()));
                    items.addAll(userItems);
                }
            }
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateItems(false, true);
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString("GiftTelegramPremiumTitle", R.string.GiftTelegramPremiumTitle);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter() {
        selectorAdapter = new SelectorAdapter(getContext(), resourcesProvider);
        selectorAdapter.setGreenSelector(true);
        return selectorAdapter;
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(searchField.getEditText());
        super.dismiss();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.giftsToUserSent) {
            dismiss();
        } else if (id == NotificationCenter.contactsDidLoad) {
            AndroidUtilities.runOnUIThread(() -> initContacts(true));
        } else if (id == NotificationCenter.reloadHints) {
            AndroidUtilities.runOnUIThread(() -> initHints(true));
        }
    }
}
