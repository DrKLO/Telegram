package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter.Item;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorHeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorSearchCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PrivacyControlActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserSelectorBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    public int type;

    public static final int TYPE_PREMIUM = 0;
    public static final int TYPE_STARS = 1;
    public static final int TYPE_STAR_GIFT = 2;
    public static final int TYPE_TRANSFER = 3;
    public static final int TYPE_CALL = 4;

    private static UserSelectorBottomSheet instance;

    public static UserSelectorBottomSheet open() {
        return open(0, null);
    }

    public static UserSelectorBottomSheet open(long userId, BirthdayController.BirthdayState birthdayState) {
        return open(TYPE_PREMIUM, userId, birthdayState);
    }

    public static UserSelectorBottomSheet open(int type, long userId, BirthdayController.BirthdayState birthdayState) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return null;
        }
        if (instance != null) {
            return instance;
        }
        final int finalType = type;
        UserSelectorBottomSheet sheet = new UserSelectorBottomSheet(fragment.getContext(), fragment.getCurrentAccount(), userId, birthdayState, type, true, fragment.getResourceProvider()) {
            @Override
            protected int getType() {
                return finalType;
            }
        };
        if (fragment != null) {
            if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
                sheet.makeAttached(fragment);
            }
            fragment.showDialog(sheet);
        } else {
            sheet.show();
        }
        return instance = sheet;
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
    private CheckBox2 videoCheckbox;
    private final SelectorBtnCell buttonContainer;
    private final FrameLayout bulletinContainer;

    private final ArrayList<Item> oldItems = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();
    private final List<TLRPC.TL_contact> contacts = new ArrayList<>();
    private final List<TLRPC.TL_topPeer> hints = new ArrayList<>();
    private final ArrayList<TLObject> searchResult = new ArrayList<>();
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

    private long userId;
    private BirthdayController.BirthdayState birthdays;

    private final Runnable remoteSearchRunnable = new Runnable() {
        @Override
        public void run() {
            final String finalQuery = query;
            if (finalQuery != null) {
                search(finalQuery);
            }
        }
    };

    private int runningRequest = -1;
    private void cancelSearch() {
        if (runningRequest >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(runningRequest, true);
            runningRequest = -1;
        }
    }

    private void search(String query) {
        cancelSearch();
        final TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        runningRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            searchResult.clear();
            runningRequest = -1;
            if (res instanceof TLRPC.TL_contacts_found) {
                final TLRPC.TL_contacts_found r = (TLRPC.TL_contacts_found) res;
                final MessagesController m = MessagesController.getInstance(currentAccount);
                m.putUsers(r.users, false);
                m.putChats(r.chats, false);

                final HashSet<Long> dialogIds = new HashSet<>();
                for (TLRPC.Peer peer : r.my_results) {
                    final long did = DialogObject.getPeerDialogId(peer);
                    if (dialogIds.contains(did)) continue;
                    final TLObject obj = m.getUserOrChat(did);
                    if (obj == null) continue;
                    searchResult.add(obj);
                    dialogIds.add(did);
                }
                for (TLRPC.Peer peer : r.results) {
                    final long did = DialogObject.getPeerDialogId(peer);
                    if (dialogIds.contains(did)) continue;
                    final TLObject obj = m.getUserOrChat(did);
                    if (obj == null) continue;
                    searchResult.add(obj);
                    dialogIds.add(did);
                }
            }
            updateList(true, true);
        }));
    }

    private void checkEditTextHint() {
        if (!selectedIds.isEmpty() || type == TYPE_STARS || type == TYPE_STAR_GIFT || type == TYPE_TRANSFER || type == TYPE_CALL) {
            if (!isHintSearchText) {
                isHintSearchText = true;
                AndroidUtilities.runOnUIThread(() -> searchField.setHintText(getString(R.string.Search), true), 10);
            }
        } else {
            if (isHintSearchText) {
                isHintSearchText = false;
                AndroidUtilities.runOnUIThread(() -> searchField.setHintText(getString(R.string.GiftPremiumUsersSearchHint), true), 10);
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

    public UserSelectorBottomSheet(Context context, int currentAccount, long userId, BirthdayController.BirthdayState state, int type, boolean needFocus, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, needFocus, false, false, resourcesProvider);
        this.currentAccount = currentAccount;
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        drawDoubleNavigationBar = false;

        this.type = type;
        this.birthdays = state;
        if (selectorAdapter != null) {
            selectorAdapter.setNeedChecks2(needChecks());
        }
//        if (birthdays != null && !birthdays.today.isEmpty() && type == TYPE_PREMIUM) {
//            for (TLRPC.User user : birthdays.today) {
//                selectedIds.add(user.id);
//                allSelectedObjects.put(user.id, user);
//            }
//        }
        this.userId = userId;
        if (userId != 0 && !selectedIds.contains(userId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
            selectedIds.add(user.id);
            allSelectedObjects.put(user.id, user);
        }

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
        searchField.setHintText(getString(!selectedIds.isEmpty() || type == TYPE_STARS || type == TYPE_STAR_GIFT || type == TYPE_TRANSFER || type == TYPE_CALL ? R.string.Search : R.string.GiftPremiumUsersSearchHint), false);

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
        if (type == TYPE_CALL) {
            LinearLayout videoLayout = new LinearLayout(context);
            videoLayout.setPadding(dp(12), dp(4), dp(12), dp(4));
            videoLayout.setClipToPadding(false);
            videoLayout.setOrientation(LinearLayout.HORIZONTAL);
            videoLayout.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 6, 6));
            videoCheckbox = new CheckBox2(context, 24, resourcesProvider);
            videoCheckbox.setColor(Theme.key_featuredStickers_addButton, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            videoCheckbox.setDrawUnchecked(true);
            videoCheckbox.setChecked(false, false);
            videoCheckbox.setDrawBackgroundAsArc(10);
            videoLayout.addView(videoCheckbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            TextView videoTextView = new TextView(context);
            videoTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            videoTextView.setText(LocaleController.getString(R.string.ConferenceCallWithVideo));
            videoLayout.addView(videoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
            ScaleStateListAnimator.apply(videoLayout, 0.025f, 1.5f);
            videoLayout.setOnClickListener(v -> {
                videoCheckbox.setChecked(!videoCheckbox.isChecked(), true);
            });
            buttonContainer.addView(videoLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 8));
        }
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
        if (type == TYPE_CALL) {
            buttonContainer.setAlpha(0.0f);
            buttonContainer.setVisibility(View.GONE);
        }
        actionButton.setOnClickListener(v -> next());
        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        if (type == TYPE_CALL) {
            containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        }

        bulletinContainer = new FrameLayout(getContext());
        containerView.addView(bulletinContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 300, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(68)));

        selectorAdapter.setData(items, recyclerListView);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(type != TYPE_STARS ? BOTTOM_HEIGHT_DP : 0));
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(searchField.getEditText());
                }
            }
        });
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof TextCell) {
                if (type == TYPE_CALL) {
                    if (onShareCallLinkListener != null) {
                        onShareCallLinkListener.run();
                        dismiss();
                    }
                } else {
                    openBirthdaySetup();
                }
                return;
            }
            if (view instanceof SelectorUserCell) {
                TLRPC.User user = ((SelectorUserCell) view).getUser();
                TLRPC.Chat chat = ((SelectorUserCell) view).getChat();
                if (user == null && chat == null && type == TYPE_TRANSFER) {
                    if (onUserSelectedListener != null) {
                        onUserSelectedListener.run(-99L);
                    }
                    return;
                }
                if (user == null && chat == null) return;
                long id = user != null ? user.id : -chat.id;
                if (type == TYPE_TRANSFER) {
                    if (onUserSelectedListener != null) {
                        onUserSelectedListener.run(id);
                    }
                    return;
                } else if (type == TYPE_STARS) {
                    if (searchField != null) {
                        AndroidUtilities.hideKeyboard(searchField.getEditText());
                    }
                    StarsIntroActivity.GiftStarsSheet sheet = new StarsIntroActivity.GiftStarsSheet(getContext(), resourcesProvider, user, this::dismiss);
                    if (!AndroidUtilities.isTablet()) {
                        sheet.makeAttached(attachedFragment);
                    }
                    sheet.show();
                    return;
                }
                if (type == TYPE_PREMIUM || type == TYPE_STAR_GIFT) {
                    if (UserObject.areGiftsDisabled(id)) {
                        BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(id)))).show();
                        return;
                    }
                    List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(paymentOptions, 1);
                    options = BoostRepository.filterGiftOptionsByBilling(options);
                    new GiftSheet(getContext(), currentAccount, id, options, this::dismiss)
                        .setBirthday(birthdays != null && birthdays.contains(id))
                        .show();
                    return;
                }
                if (type == TYPE_CALL && selectedIds.isEmpty()) {
                    selectedIds.add(id);
                    if (onUsersSelectedListener != null) {
                        onUsersSelectedListener.run(videoCheckbox != null && videoCheckbox.isChecked(), selectedIds);
                        onUsersSelectedListener = null;
                    }
                    dismiss();
                    return;
                }
                final boolean wasButtonVisible = type != TYPE_CALL || !selectedIds.isEmpty();
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                    allSelectedObjects.put(id, user);
                }
                if (selectedIds.size() == getLimit() + 1) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return;
                }
                final boolean isButtonVisible = type != TYPE_CALL || !selectedIds.isEmpty();
                if (wasButtonVisible != isButtonVisible) {
                    buttonContainer.setVisibility(View.VISIBLE);
                    buttonContainer.animate()
                        .alpha(isButtonVisible ? 1.0f : 0.0f)
                        .translationY(isButtonVisible ? 0 : dp(12))
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .setDuration(320)
                        .withEndAction(!isButtonVisible ? () -> buttonContainer.setVisibility(View.GONE) : null)
                        .start();
                    selectorAdapter.setCallButtonsVisible(!isButtonVisible);
                }
                checkEditTextHint();
                searchField.updateSpans(true, selectedIds, () -> {
                    checkEditTextHint();
                    updateList(true, false);
                }, null);
                updateList(true, true);
                clearSearchAfterSelect();
            }
        });
        if (type == TYPE_CALL) {
            recyclerListView.setOnItemLongClickListener((view, position, x, y) -> {
                if (view instanceof SelectorUserCell) {
                    TLRPC.User user = ((SelectorUserCell) view).getUser();
                    TLRPC.Chat chat = ((SelectorUserCell) view).getChat();
                    long id = user != null ? user.id : -chat.id;
                    final boolean wasButtonVisible = type != TYPE_CALL || !selectedIds.isEmpty();
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id);
                    } else {
                        selectedIds.add(id);
                        allSelectedObjects.put(id, user);
                    }
                    if (selectedIds.size() == getLimit() + 1) {
                        selectedIds.remove(id);
                        showMaximumUsersToast();
                        return true;
                    }
                    final boolean isButtonVisible = type != TYPE_CALL || !selectedIds.isEmpty();
                    if (wasButtonVisible != isButtonVisible) {
                        buttonContainer.setVisibility(View.VISIBLE);
                        buttonContainer.animate()
                            .alpha(isButtonVisible ? 1.0f : 0.0f)
                            .translationY(isButtonVisible ? 0 : dp(12))
                            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                            .setDuration(320)
                            .withEndAction(!isButtonVisible ? () -> buttonContainer.setVisibility(View.GONE) : null)
                            .start();
                        selectorAdapter.setCallButtonsVisible(!isButtonVisible);
                    }
                    checkEditTextHint();
                    searchField.updateSpans(true, selectedIds, () -> {
                        checkEditTextHint();
                        updateList(true, false);
                    }, null);
                    updateList(true, true);
                    clearSearchAfterSelect();
                    return true;
                }
                return false;
            });
        }
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
        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
        updateActionButton(false);
        initContacts(false);
        initHints(false);
        updateList(false, true);
        if (type == TYPE_PREMIUM || type == TYPE_STAR_GIFT) {
            BoostRepository.loadGiftOptions(currentAccount, null, arg -> {
                paymentOptions.clear();
                paymentOptions.addAll(arg);
                if (actionButton.isLoading()) {
                    actionButton.setLoading(false);
                    if (recyclerListView.isAttachedToWindow()) {
                        next();
                    }
                }
            });
        }
        if (type == TYPE_PREMIUM || type == TYPE_STAR_GIFT) {
            StarsController.getInstance(currentAccount).loadStarGifts();
        }
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
        final float minTop = AndroidUtilities.statusBarHeight - dp(8);
        final float fromY = Math.max(top, minTop) + dp(8);
        headerView.setTranslationY(fromY);
        searchField.setTranslationY(headerView.getTranslationY() + headerView.getMeasuredHeight());
        sectionCell.setTranslationY(searchField.getTranslationY() + searchField.getMeasuredHeight());
        recyclerListView.setTranslationY(headerView.getMeasuredHeight() + searchField.getMeasuredHeight() + sectionCell.getMeasuredHeight() - AndroidUtilities.dp(8));
    }

    private void next() {
        if (selectedIds.size() == 0 || paymentOptions.isEmpty() && (type != TYPE_PREMIUM && type != TYPE_STAR_GIFT && type != TYPE_CALL)) {
            return;
        }
        List<TLRPC.User> selectedUsers = new ArrayList<>();
        for (TLRPC.User object : allSelectedObjects.values()) {
            if (selectedIds.contains(object.id)) {
                selectedUsers.add(object);
            }
        }
        AndroidUtilities.hideKeyboard(searchField.getEditText());
        if (type == TYPE_STARS) {
            return;
        }
        if (type == TYPE_CALL) {
            if (onUsersSelectedListener != null) {
                onUsersSelectedListener.run(videoCheckbox != null && videoCheckbox.isChecked(), selectedIds);
                onUsersSelectedListener = null;
            }
            dismiss();
            return;
        }
        List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(paymentOptions, selectedUsers.size());
        options = BoostRepository.filterGiftOptionsByBilling(options);
        if (selectedUsers.size() == 1) {
            final long userId = selectedUsers.get(0).id;
            if (UserObject.areGiftsDisabled(userId)) {
                BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(userId)))).show();
                return;
            }
            new GiftSheet(getContext(), currentAccount, userId, options, this::dismiss)
                .setBirthday(birthdays != null && birthdays.contains(userId))
                .show();
        }
    }

    public void scrollToTop(boolean animate) {
        if (animate) {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .6f);
            linearSmoothScroller.setTargetPosition(1);
            linearSmoothScroller.setOffset(dp(36));
            recyclerListView.getLayoutManager().startSmoothScroll(linearSmoothScroller);
        } else {
            recyclerListView.scrollToPosition(0);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.giftsToUserSent);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadHints);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.giftsToUserSent);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadHints);
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        instance = null;
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
    }

    protected int getLimit() {
        if (type == TYPE_CALL) {
            return Math.max(0, MessagesController.getInstance(currentAccount).conferenceCallSizeLimit - excludeUserIds.size() - 1);
        } else {
            return 10;
        }
    }

    private void showMaximumUsersToast() {
        String text;
        if (type == TYPE_CALL) {
            text = formatPluralStringComma("UserSelectorLimit", getLimit());
        } else {
            text = getString(R.string.BoostingSelectUpToWarningUsers);
        }
        BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, text).show(true);
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
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
                if (position - 1 < 0 || position - 1 >= items.size()) {
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
        if (type == TYPE_CALL) {
            stringBuilder.append(getString(R.string.CallInviteMembersButton));
        } else if (selectedIds.size() == 0) {
            if (LocaleController.isRTL) {
                stringBuilder.append(getString("GiftPremiumChooseRecipientsBtn", R.string.GiftPremiumChooseRecipientsBtn));
                stringBuilder.append("d").setSpan(recipientsBtnSpaceSpan, stringBuilder.length() - 1, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                stringBuilder.append("d").setSpan(recipientsBtnSpaceSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                stringBuilder.append(getString("GiftPremiumChooseRecipientsBtn", R.string.GiftPremiumChooseRecipientsBtn));
            }
        } else {
            stringBuilder.append(getString("GiftPremiumProceedBtn", R.string.GiftPremiumProceedBtn));
        }
        actionButton.setCount(selectedIds.size(), true);
        actionButton.setText(stringBuilder, animated, false);
        actionButton.setEnabled(selectedIds.size() > 0);
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

    private boolean isSearching() {
        return !TextUtils.isEmpty(query);
    }

    private Item decorate(Item item) {
        if (type == TYPE_CALL) {
            if (item.user == null) return item;
            final long userId = item.user.id;
            return item.withCall(v -> {
                selectedIds.add(userId);
                if (onUsersSelectedListener != null) {
                    onUsersSelectedListener.run(false, selectedIds);
                    onUsersSelectedListener = null;
                }
                dismiss();
            }, v -> {
                selectedIds.add(userId);
                if (onUsersSelectedListener != null) {
                    onUsersSelectedListener.run(true, selectedIds);
                    onUsersSelectedListener = null;
                }
                dismiss();
            });
        } else {
            return item.withOptions(openOptions(item.user));
        }
    }

    private int addSection(ArrayList<SelectorAdapter.Item> items, CharSequence title, ArrayList<TLRPC.User> users, boolean addSelectAll) {
        addSelectAll = false;
        int h = 0;
        if (users.isEmpty()) {
            return h;
        }
        List<Item> userItems = new ArrayList<>();
        int count = 0;
        boolean allSelected = true;
        for (TLRPC.User user : users) {
            if (user == null || user.bot || UserObject.isService(user.id)) continue;
            if (user.id == userId) {
                continue;
            }
            if (excludeUserIds.contains(user.id)) continue;
            if (!selectedIds.contains(user.id)) {
                allSelected = false;
            }
            count++;
            h += dp(56);
            userItems.add(decorate(Item.asUser(user, selectedIds.contains(user.id))));
        }
        if (userItems.isEmpty()) {
            return h;
        }
        h += dp(32);
        Item header = Item.asTopSection(title);
        if (addSelectAll && count > 1) {
            final boolean finalAllSelected = allSelected;
            header.withRightText(getString(allSelected ? R.string.DeselectAll : R.string.SelectAll), v -> {
                if (finalAllSelected) {
                    for (TLRPC.User user : users) {
                        selectedIds.remove(user.id);
                        allSelectedObjects.remove(user.id);
                    }
                } else {
                    for (TLRPC.User user : users) {
                        if (!selectedIds.contains(user.id)) {
                            selectedIds.add(user.id);
                            allSelectedObjects.put(user.id, user);
                        }
                    }
                }
                checkEditTextHint();
                searchField.updateSpans(true, selectedIds, () -> {
                    checkEditTextHint();
                    updateList(true, false);
                }, null);
                updateList(true, true);
                clearSearchAfterSelect();
            });
        }
        items.add(header);
        items.addAll(userItems);
        return h;
    }

    private Drawable tonIcon;

    @SuppressLint("NotifyDataSetChanged")
    public void updateItems(boolean animated, boolean notify) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        int h = 0;
        if (isSearching()) {
            for (TLObject peer : searchResult) {
                long did;
                if (peer instanceof TLRPC.User) {
                    final TLRPC.User user = (TLRPC.User) peer;
                    if (user.bot || UserObject.isService(user.id)) continue;
                    did = user.id;
                    h += dp(56);
                    if (excludeUserIds.contains(user.id)) continue;
                    items.add(decorate(Item.asUser(user, selectedIds.contains(did))));
                } else if (peer instanceof TLRPC.Chat) {
                    final TLRPC.Chat chat = (TLRPC.Chat) peer;
                    if (type != TYPE_TRANSFER) continue;
                    if (!ChatObject.isChannelAndNotMegaGroup(chat)) continue;
                    did = -chat.id;
                    h += dp(56);
                    if (excludeUserIds.contains(-chat.id)) continue;
                    items.add(Item.asChat(chat, selectedIds.contains(did)));
                }
            }
        } else {
            if (includeTonOption && type == TYPE_TRANSFER) {
                if (tonIcon == null) {
                    final CombinedDrawable icon = new CombinedDrawable(
                        Theme.createCircleDrawable(dp(46), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)),
                        getContext().getResources().getDrawable(R.drawable.ton).mutate()
                    );
                    icon.setIconSize(dp(24), dp(24));
                    tonIcon = icon;
                }
                items.add(Item.asCustomUser(2, tonIcon, getString(R.string.Gift2ExportTONTitle), tonDays > 0 ? formatPluralString("Gift2ExportTONUnlocksIn", tonDays) : ""));
            }
            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
            if (userFull == null) {
                MessagesController.getInstance(currentAccount).loadFullUser(UserConfig.getInstance(currentAccount).getCurrentUser(), 0, true);
            }
            if ((type == TYPE_PREMIUM || type == TYPE_STAR_GIFT) && userFull != null && userFull.birthday == null) {
                h += dp(50);
                items.add(Item.asButton(1, R.drawable.menu_birthday, getString(R.string.GiftsBirthdaySetup)));
            }
            if (onShareCallLinkListener != null && type == TYPE_CALL) {
                items.add(Item.asButton(3, R.drawable.msg2_link2, getString(R.string.VoipConferenceShareLink)));
            }
            if (birthdays != null) {
                h += addSection(items, getString(R.string.BirthdayToday), birthdays.today, true);
                h += addSection(items, getString(R.string.BirthdayYesterday), birthdays.yesterday, true);
                h += addSection(items, getString(R.string.BirthdayTomorrow), birthdays.tomorrow, true);
            }
            if (type == TYPE_PREMIUM || type == TYPE_STAR_GIFT) {
                final TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
                if (currentUser != null) {
                    items.add(Item.asTopSection(getString(R.string.Gift2MyselfSection)));
                    final Item item = Item.asUser(currentUser, selectedIds.contains(currentUser.id));
                    item.subtext = getString(R.string.Gift2Myself);
                    items.add(item);
                }
            }
            Item topSection = null;
            final ArrayList<Long> selected = new ArrayList<>();
            if (!hints.isEmpty()) {
                final List<Item> userItems = new ArrayList<>();
                for (final TLRPC.TL_topPeer hint : hints) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(hint.peer.user_id);
                    if (user == null || user.id == userId || user.self || user.bot || UserObject.isService(user.id) || UserObject.isDeleted(user)) {
                        continue;
                    }
                    if (birthdays != null && birthdays.contains(user.id)) {
                        continue;
                    }
                    if (excludeUserIds.contains(user.id)) continue;
                    if (selectedIds.contains(user.id)) selected.add(user.id);
                    h += dp(56);
                    userItems.add(decorate(Item.asUser(user, selectedIds.contains(user.id))));
                }
                if (!userItems.isEmpty()) {
                    h += dp(32);
                    topSection = Item.asTopSection(getString(R.string.GiftPremiumFrequentContacts));
                    items.add(topSection);
                    items.addAll(userItems);
                }
            }
            for (final String contactLetter : contactsLetters) {
                final List<Item> userItems = new ArrayList<>();
                for (final TLRPC.TL_contact contact : contactsMap.get(contactLetter)) {
                    final long myUid = UserConfig.getInstance(currentAccount).getClientUserId();
                    if (contact.user_id == myUid || contact.user_id == userId) {
                        continue;
                    }
                    if (birthdays != null && birthdays.contains(contact.user_id)) {
                        continue;
                    }
                    if (excludeUserIds.contains(contact.user_id)) continue;
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (user == null || user.bot || UserObject.isService(user.id)) continue;
                    h += dp(56);
                    if (selectedIds.contains(user.id)) selected.add(user.id);
                    userItems.add(decorate(Item.asUser(user, selectedIds.contains(user.id))));
                }

                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(Item.asLetter(contactLetter.toUpperCase()));
                    items.addAll(userItems);
                }
            }
            if (topSection != null && selected.size() > 0 && !selectedIds.isEmpty()) {
                topSection.withRightText(getString(R.string.DeselectAll), v -> {
                    for (long userId : selected) {
                        selectedIds.remove(userId);
                        allSelectedObjects.remove(userId);
                    }
                    checkEditTextHint();
                    searchField.updateSpans(true, selectedIds, () -> {
                        checkEditTextHint();
                        updateList(true, false);
                    }, null);
                    updateList(true, true);
                    clearSearchAfterSelect();
                });
            }
        }

        if (items.isEmpty()) {
            items.add(Item.asNoUsers());
            h += dp(150);
        }
        int minHeight = (int) (AndroidUtilities.displaySize.y * 0.6f);
        items.add(Item.asPad(Math.max(0, minHeight - h)));

        if (notify && selectorAdapter != null) {
            if (animated) {
                selectorAdapter.setItems(oldItems, items);
            } else {
                selectorAdapter.notifyDataSetChanged();
            }
        }
    }

    public View.OnClickListener openOptions(TLRPC.User user) {
        if (type == TYPE_TRANSFER) {
            return null;
        }
        return (View view) -> {
            ItemOptions.makeOptions(container, resourcesProvider, (View) view.getParent())
                .add(R.drawable.profile_discuss, LocaleController.getString(R.string.SendMessage), () -> {
                    BaseFragment fragment = getBaseFragment();
                    if (user == null || fragment == null) return;
//                    BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
//                    bottomSheetParams.transitionFromLeft = true;
//                    bottomSheetParams.allowNestedScroll = false;
                    Bundle args = new Bundle();
                    args.putLong("user_id", user.id);
//                    fragment.showAsSheet(new ChatActivity(args), bottomSheetParams);
                    fragment.presentFragment(new ChatActivity(args));
                })
                .add(R.drawable.msg_openprofile, LocaleController.getString(R.string.OpenProfile), () -> {
                    BaseFragment fragment = getBaseFragment();
                    if (user == null || fragment == null) return;
//                    BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
//                    bottomSheetParams.transitionFromLeft = true;
//                    bottomSheetParams.allowNestedScroll = false;
                    Bundle args = new Bundle();
                    args.putLong("user_id", user.id);
//                    fragment.showAsSheet(new ProfileActivity(args), bottomSheetParams);
                    fragment.presentFragment(new ProfileActivity(args));
                })
                .show();
        };
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateItems(false, true);
    }

    private String customTitle;
    public void setTitle(String title) {
        customTitle = title;

        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
        if (headerView != null) {
            headerView.setText(getTitle());
        }
    }

    private Runnable onShareCallLinkListener;
    public UserSelectorBottomSheet setOnShareCallLinkListener(Runnable listener) {
        onShareCallLinkListener = listener;
        updateItems(false, true);
        return this;
    }

    private Utilities.Callback<Long> onUserSelectedListener;
    public void setOnUserSelector(Utilities.Callback<Long> listener) {
        onUserSelectedListener = listener;
    }

    private final HashSet<Long> excludeUserIds = new HashSet<>();
    public UserSelectorBottomSheet exceptUsers(long ...ids) {
        for (long id : ids)
            excludeUserIds.add(id);
        updateItems(false, true);
        return this;
    }
    public UserSelectorBottomSheet exceptUsers(Collection<Long> ids) {
        excludeUserIds.addAll(ids);
        updateItems(false, true);
        return this;
    }

    private Utilities.Callback2<Boolean, HashSet<Long>> onUsersSelectedListener;
    public UserSelectorBottomSheet setOnUsersSelector(Utilities.Callback2<Boolean, HashSet<Long>> listener) {
        onUsersSelectedListener = listener;
        return this;
    }

    private boolean includeTonOption;
    private int tonDays;
    public void addTONOption(int days) {
        includeTonOption = true;
        tonDays = days;
        updateItems(false, true);
    }


    @Override
    protected CharSequence getTitle() {
        if (customTitle != null) {
            return customTitle;
        }
        switch (type) {
            case TYPE_CALL:
                return getString(R.string.VoipConferenceAddPeople);
            case TYPE_STARS:
                return getString(R.string.GiftStarsTitle);
            case TYPE_STAR_GIFT:
            case TYPE_PREMIUM:
                return getString(R.string.GiftTelegramPremiumOrStarsTitle);
        }
        return getString(R.string.GiftTelegramPremiumTitle);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        selectorAdapter = new SelectorAdapter(getContext(), false, resourcesProvider);
        selectorAdapter.setGreenSelector(true);
        return selectorAdapter;
    }

    protected boolean needChecks() {
        return type == TYPE_CALL;
    }

    protected int getType() {
        return TYPE_PREMIUM;
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
        } else if (id == NotificationCenter.userInfoDidLoad) {
            AndroidUtilities.runOnUIThread(() -> updateItems(true, true));
        }
    }

    private void openBirthdaySetup() {
        AlertsCreator.createBirthdayPickerDialog(getContext(), getString(R.string.EditProfileBirthdayTitle), getString(R.string.EditProfileBirthdayButton), null, birthday -> {
            TL_account.updateBirthday req = new TL_account.updateBirthday();
            req.flags |= 1;
            req.birthday = birthday;
            TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
            TL_account.TL_birthday oldBirthday = userFull != null ? userFull.birthday : null;
            if (userFull != null) {
                userFull.flags2 |= 32;
                userFull.birthday = birthday;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_boolTrue) {
                    BulletinFactory.of(bulletinContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.PrivacyBirthdaySetDone))
                            .setDuration(Bulletin.DURATION_PROLONG)
                            .show();
                } else {
                    if (userFull != null) {
                        if (oldBirthday == null) {
                            userFull.flags2 &=~ 32;
                        } else {
                            userFull.flags2 |= 32;
                        }
                        userFull.birthday = oldBirthday;
                        MessagesStorage.getInstance(currentAccount).updateUserInfo(userFull, false);
                    }
                    if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT_")) {
                        if (getContext() != null) {
                            new AlertDialog.Builder(getContext(), resourcesProvider)
                                .setTitle(getString(R.string.PrivacyBirthdayTooOftenTitle))
                                .setMessage(getString(R.string.PrivacyBirthdayTooOftenMessage))
                                .setPositiveButton(getString(R.string.OK), null)
                                .show();
                        }
                    } else {
                        BulletinFactory.of(bulletinContainer, resourcesProvider)
                                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError))
                                .show();
                    }
                }
            }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);

            MessagesController.getInstance(currentAccount).invalidateContentSettings();
            MessagesController.getInstance(currentAccount).removeSuggestion(0, "BIRTHDAY_SETUP");
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.newSuggestionsAvailable);
            updateItems(true, true);
        }, () -> {
            if (getBaseFragment() == null) {
                return;
            }
            BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
            params.transitionFromLeft = true;
            params.allowNestedScroll = false;
            getBaseFragment().showAsSheet(new PrivacyControlActivity(PrivacyControlActivity.PRIVACY_RULES_TYPE_BIRTHDAY), params);
        }, resourcesProvider).show();
    }
}
