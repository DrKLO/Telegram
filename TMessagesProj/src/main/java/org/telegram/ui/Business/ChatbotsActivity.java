package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class ChatbotsActivity extends BaseFragment {

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    private UniversalRecyclerView listView;

    private SpannableStringBuilder introText;

    private SearchAdapterHelper searchHelper;

    private FrameLayout editTextContainer;
    private EditTextBoldCursor editText;
    private View editTextDivider;

    private FrameLayout emptyView;
    private TextView emptyViewText;
    private ImageView emptyViewLoading;

    private BusinessRecipientsHelper recipientsHelper;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.BusinessBots));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        checkDone(false);

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.HORIZONTAL);
        editText = new EditTextBoldCursor(getContext());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setBackgroundDrawable(null);
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setPadding(0, 0, 0, 0);
        editText.setSingleLine(true);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setHint(getString(R.string.BusinessBotLink));
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(dp(19));
        editText.setCursorWidth(1.5f);
        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                scheduledLoading = false;
                AndroidUtilities.cancelRunOnUIThread(search);
                if (TextUtils.isEmpty(editText.getText())) {
                    lastQuery = null;
                    searchHelper.clear();
                    listView.adapter.update(true);
                } else {
                    AndroidUtilities.runOnUIThread(search);
                }
                updateSearchLoading();
                return true;
            }
            return false;
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                scheduleSearch();
            }
        });
        editTextContainer = new FrameLayout(context);
        editTextContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 21, 15, 21, 15));
        editTextContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        editTextDivider = new View(context);
        editTextDivider.setBackgroundColor(getThemedColor(Theme.key_divider));
        editTextContainer.addView(editTextDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, (LocaleController.isRTL ? 0 : 21), 0, (LocaleController.isRTL ? 21 : 0), 0));

        emptyView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(58), MeasureSpec.EXACTLY));
            }
        };
        emptyView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        emptyViewText = new TextView(context);
        emptyViewText.setText(getString(R.string.BusinessBotNotFound));
        emptyViewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyViewText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.addView(emptyViewText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        emptyViewLoading = new ImageView(context);
        CircularProgressDrawable progressDrawable = new CircularProgressDrawable(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2)) {
            @Override
            public int getIntrinsicWidth() {
                return (int) (size + thickness * 2);
            }
            @Override
            public int getIntrinsicHeight() {
                return (int) (size + thickness * 2);
            }
        };
        emptyViewLoading.setScaleType(ImageView.ScaleType.CENTER);
        emptyViewLoading.setImageDrawable(progressDrawable);
        emptyView.addView(emptyViewLoading, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        emptyViewLoading.setAlpha(0f);
        emptyViewLoading.setTranslationY(dp(8));

        introText = AndroidUtilities.replaceSingleTag(getString(R.string.BusinessBotsInfo), () -> {
            Browser.openUrl(getContext(), LocaleController.getString(R.string.BusinessBotsInfoLink));
        });
        int arrowIndex = introText.toString().indexOf(">");
        if (arrowIndex >= 0) {
            ColoredImageSpan span = new ColoredImageSpan(R.drawable.arrow_newchat);
            span.setColorKey(Theme.key_chat_messageLinkIn);
            introText.setSpan(span, arrowIndex, arrowIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        searchHelper = new SearchAdapterHelper(true);
        searchHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged(int searchId) {
                AndroidUtilities.runOnUIThread(() -> {
                    listView.adapter.update(true);
                    updateSearchLoading();
                });
            }
        });

        recipientsHelper = new BusinessRecipientsHelper(this, () -> {
            listView.adapter.update(true);
            checkDone(true);
        });
        recipientsHelper.setValue(currentBot == null ? null : currentBot.recipients);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView = contentView;
    }

    private boolean wasLoading;
    private void updateSearchLoading() {
        if (wasLoading != (searchHelper.isSearchInProgress() || scheduledLoading || foundBots.size() > 0)) {
            final boolean loading = wasLoading = searchHelper.isSearchInProgress() || scheduledLoading || foundBots.size() > 0;
            emptyViewText.animate().alpha(loading ? 0f : 1f).translationY(loading ? -dp(8) : 0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            emptyViewLoading.animate().alpha(loading ? 1f : 0f).translationY(loading ? 0 : dp(8)).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }

    private boolean scheduledLoading;
    private void scheduleSearch() {
        scheduledLoading = false;
        AndroidUtilities.cancelRunOnUIThread(search);
        if (TextUtils.isEmpty(editText.getText())) {
            lastQuery = null;
            searchHelper.clear();
        } else {
            scheduledLoading = true;
            AndroidUtilities.runOnUIThread(search, 800);
        }
        listView.adapter.update(true);
        updateSearchLoading();
    }
    private String lastQuery;
    private int searchId = 0;
    private Runnable search = () -> {
        final String query = editText.getText().toString();
        if (lastQuery != null && TextUtils.equals(lastQuery, query))
            return;
        scheduledLoading = false;
        if (TextUtils.isEmpty(query)) {
            lastQuery = null;
            searchHelper.clear();
            listView.adapter.update(true);
        } else {
            searchHelper.queryServerSearch(lastQuery = query, true, false, true, false, false, 0, false, 0, searchId++, 0);
        }
    };

    public TL_account.connectedBots currentValue;
    public TL_account.TL_connectedBot currentBot;

    public boolean exclude;
    public TL_account.TL_businessBotRights rights = TL_account.TL_businessBotRights.makeDefault();

    private boolean shownUsernamePermissionsAlert;
    private boolean shownGiftsPermissionsAlert;

    private TLRPC.User selectedBot = null;
    private LongSparseArray<TLRPC.User> foundBots = new LongSparseArray<>();

    private static int ids = 0;
    private static final int RADIO_EXCLUDE = --ids;
    private static final int RADIO_INCLUDE = --ids;
    private static final int BUTTON_DELETE = --ids;

    private static final int PERMISSION_MESSAGES = --ids;
    private static final int PERMISSION_MESSAGES_READ = --ids;
    private static final int PERMISSION_MESSAGES_REPLY = --ids;
    private static final int PERMISSION_MESSAGES_MARK_AS_READ = --ids;
    private static final int PERMISSION_MESSAGES_DELETE_SENT = --ids;
    private static final int PERMISSION_MESSAGES_DELETE_RECEIVED = --ids;

    private static final int PERMISSION_PROFILE = --ids;
    private static final int PERMISSION_PROFILE_NAME = --ids;
    private static final int PERMISSION_PROFILE_BIO = --ids;
    private static final int PERMISSION_PROFILE_PICTURE = --ids;
    private static final int PERMISSION_PROFILE_USERNAME = --ids;

    private static final int PERMISSION_GIFTS = --ids;
    private static final int PERMISSION_GIFTS_VIEW = --ids;
    private static final int PERMISSION_GIFTS_SELL = --ids;
    private static final int PERMISSION_GIFTS_SETTINGS = --ids;
    private static final int PERMISSION_GIFTS_TRANSFER = --ids;
    private static final int PERMISSION_GIFTS_TRANSFER_STARS = --ids;

    private static final int PERMISSION_STORIES = --ids;

    private void checkAlert(int id, boolean newValue, Runnable toggle) {
        if (!shownUsernamePermissionsAlert && id == PERMISSION_PROFILE_USERNAME && newValue) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.BusinessBotPermissionsWarning))
                .setMessage(AndroidUtilities.replaceTags(formatString(R.string.BusinessBotPermissionsUsernamesWarningText, UserObject.getPublicUsername(selectedBot))))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Allow), (di, w) -> {
                    shownUsernamePermissionsAlert = true;
                    toggle.run();
                })
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
            return;
        }
        if (
            !shownGiftsPermissionsAlert && newValue && (
                id == PERMISSION_GIFTS_SELL ||
                id == PERMISSION_GIFTS_SETTINGS ||
                id == PERMISSION_GIFTS_TRANSFER ||
                id == PERMISSION_GIFTS_TRANSFER_STARS
            )
        ) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.BusinessBotPermissionsWarning))
                .setMessage(AndroidUtilities.replaceTags(formatString(R.string.BusinessBotPermissionsGiftsWarningText, UserObject.getPublicUsername(selectedBot))))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Allow), (di, w) -> {
                    shownGiftsPermissionsAlert = true;
                    toggle.run();
                })
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
            return;
        }
        toggle.run();
    }

    private int shakeDp = -4;

    private boolean expandedMessagesSection = true;
    private boolean expandedProfileSection = false;
    private boolean expandedGiftsSection = false;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(introText, "RestrictedEmoji", "🤖"));

        if (selectedBot != null) {
            items.add(UItem.asAddChat(selectedBot.id).setChecked(true).setCloseIcon(this::clear));
        } else {
            adapter.whiteSectionStart();
            boolean needDivider = false;
            items.add(UItem.asCustom(editTextContainer));
            foundBots.clear();
            for (int i = 0; i < searchHelper.getLocalServerSearch().size(); ++i) {
                TLObject obj = searchHelper.getLocalServerSearch().get(i);
                if (!(obj instanceof TLRPC.User)) continue;
                TLRPC.User user = (TLRPC.User) obj;
                if (!user.bot) continue;
                items.add(UItem.asAddChat(user.id));
                foundBots.put(user.id, user);
                needDivider = true;
            }
            for (int i = 0; i < searchHelper.getGlobalSearch().size(); ++i) {
                TLObject obj = searchHelper.getGlobalSearch().get(i);
                if (!(obj instanceof TLRPC.User)) continue;
                TLRPC.User user = (TLRPC.User) obj;
                if (!user.bot) continue;
                items.add(UItem.asAddChat(user.id));
                foundBots.put(user.id, user);
                needDivider = true;
            }
            if (foundBots.size() <= 0 && (!TextUtils.isEmpty(editText.getText().toString()) || searchHelper.isSearchInProgress() || scheduledLoading)) {
                items.add(UItem.asCustom(emptyView));
                needDivider = true;
            }
            editTextDivider.setVisibility(needDivider ? View.VISIBLE : View.GONE);
            adapter.whiteSectionEnd();
        }
        items.add(UItem.asShadow(getString(R.string.BusinessBotLinkInfo)));
        if (selectedBot != null) {
            items.add(UItem.asHeader(getString(R.string.BusinessBotChats)));
            items.add(UItem.asRadio(RADIO_EXCLUDE, getString(R.string.BusinessChatsAllPrivateExcept)).setChecked(exclude));
            items.add(UItem.asRadio(RADIO_INCLUDE, getString(R.string.BusinessChatsOnlySelected)).setChecked(!exclude));
            items.add(UItem.asShadow(null));
            recipientsHelper.fillItems(items);
            items.add(UItem.asShadow(getString(R.string.BusinessBotChatsInfo)));
            items.add(UItem.asHeader(getString(R.string.BusinessBotPermissions)));
            items.add(
                UItem.asExpandableSwitch(PERMISSION_MESSAGES, getString(R.string.BusinessBotPermissionsMessagesSection),
                        (1 + (rights.reply ? 1 : 0) + (rights.read_messages ? 1 : 0) + (rights.delete_sent_messages ? 1 : 0) + (rights.delete_received_messages ? 1 : 0)) + "/5"
                    )
                    .setChecked(rights.reply && rights.read_messages && rights.delete_received_messages && rights.delete_sent_messages)
                    .setCollapsed(!expandedMessagesSection)
                    .setClickCallback(v -> {
                        if (rights.reply && rights.read_messages && rights.delete_received_messages && rights.delete_sent_messages) {
                            rights.reply = rights.read_messages = rights.delete_received_messages = rights.delete_sent_messages = false;
                        } else {
                            rights.reply = rights.read_messages = rights.delete_received_messages = rights.delete_sent_messages = true;
                        }
                        listView.adapter.update(true);
                        checkDone(true);
                    })
            );
            if (expandedMessagesSection) {
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_MESSAGES_READ, getString(R.string.BusinessBotPermissionsMessagesRead))
                        .setChecked(true)
                        .setEnabled(false)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_MESSAGES_REPLY, getString(R.string.BusinessBotPermissionsMessagesReply))
                        .setChecked(rights.reply)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_MESSAGES_MARK_AS_READ, getString(R.string.BusinessBotPermissionsMessagesMarkAsRead))
                        .setChecked(rights.read_messages)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_MESSAGES_DELETE_SENT, getString(R.string.BusinessBotPermissionsMessagesDeleteSent))
                        .setChecked(rights.delete_sent_messages)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_MESSAGES_DELETE_RECEIVED, getString(R.string.BusinessBotPermissionsMessagesDeleteReceived))
                        .setChecked(rights.delete_received_messages)
                        .setPad(1)
                );
            }
            items.add(
                UItem.asExpandableSwitch(PERMISSION_PROFILE, getString(R.string.BusinessBotPermissionsProfileSection),
                        ((rights.edit_name ? 1 : 0) + (rights.edit_bio ? 1 : 0) + (rights.edit_profile_photo ? 1 : 0) + (rights.edit_username ? 1 : 0)) + "/4"
                    )
                    .setChecked(rights.edit_name && rights.edit_bio && rights.edit_profile_photo && rights.edit_username)
                    .setCollapsed(!expandedProfileSection)
                    .setClickCallback(v -> {
                        if (rights.edit_name && rights.edit_bio && rights.edit_profile_photo && rights.edit_username) {
                            rights.edit_name = rights.edit_bio = rights.edit_profile_photo = rights.edit_username = false;
                            listView.adapter.update(true);
                            checkDone(true);
                        } else {
                            checkAlert(PERMISSION_PROFILE_USERNAME, true, () -> {
                                rights.edit_name = rights.edit_bio = rights.edit_profile_photo = rights.edit_username = true;
                                listView.adapter.update(true);
                                checkDone(true);
                            });
                        }
                    })
            );
            if (expandedProfileSection) {
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_PROFILE_NAME, getString(R.string.BusinessBotPermissionsProfileName))
                        .setChecked(rights.edit_name)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_PROFILE_BIO, getString(R.string.BusinessBotPermissionsProfileBio))
                        .setChecked(rights.edit_bio)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_PROFILE_PICTURE, getString(R.string.BusinessBotPermissionsProfilePicture))
                        .setChecked(rights.edit_profile_photo)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_PROFILE_USERNAME, getString(R.string.BusinessBotPermissionsProfileUsername))
                        .setChecked(rights.edit_username)
                        .setPad(1)
                );
            }
            items.add(
                UItem.asExpandableSwitch(PERMISSION_GIFTS, getString(R.string.BusinessBotPermissionsGiftsSection),
                        ((rights.view_gifts ? 1 : 0) + (rights.sell_gifts ? 1 : 0) + (rights.change_gift_settings ? 1 : 0) + (rights.transfer_and_upgrade_gifts ? 1 : 0) + (rights.transfer_stars ? 1 : 0)) + "/5"
                    )
                    .setChecked(rights.view_gifts && rights.sell_gifts && rights.change_gift_settings && rights.transfer_and_upgrade_gifts && rights.transfer_stars)
                    .setCollapsed(!expandedGiftsSection)
                    .setClickCallback(v -> {
                        if (rights.view_gifts && rights.sell_gifts && rights.change_gift_settings && rights.transfer_and_upgrade_gifts && rights.transfer_stars) {
                            rights.view_gifts = rights.sell_gifts = rights.change_gift_settings = rights.transfer_and_upgrade_gifts = rights.transfer_stars = false;
                            listView.adapter.update(true);
                            checkDone(true);
                        } else {
                            checkAlert(PERMISSION_GIFTS_SELL, true, () -> {
                                rights.view_gifts = rights.sell_gifts = rights.change_gift_settings = rights.transfer_and_upgrade_gifts = rights.transfer_stars = true;
                                listView.adapter.update(true);
                                checkDone(true);
                            });
                        }
                    })
            );
            if (expandedGiftsSection) {
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_GIFTS_VIEW, getString(R.string.BusinessBotPermissionsGiftsView))
                        .setChecked(rights.view_gifts)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_GIFTS_SELL, getString(R.string.BusinessBotPermissionsGiftsSell))
                        .setChecked(rights.sell_gifts)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_GIFTS_SETTINGS, getString(R.string.BusinessBotPermissionsGiftsSettings))
                        .setChecked(rights.change_gift_settings)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_GIFTS_TRANSFER, getString(R.string.BusinessBotPermissionsGiftsTransfer))
                        .setChecked(rights.transfer_and_upgrade_gifts)
                        .setPad(1)
                );
                items.add(
                    UItem.asRoundCheckbox(PERMISSION_GIFTS_TRANSFER_STARS, getString(R.string.BusinessBotPermissionsGiftsTransferStars))
                        .setChecked(rights.transfer_stars)
                        .setPad(1)
                );
            }
            items.add(
                UItem.asExpandableSwitch(PERMISSION_STORIES, getString(R.string.BusinessBotPermissionsStories), "")
                    .setChecked(rights.manage_stories)
                    .setClickCallback(v -> {
                        rights.manage_stories = !rights.manage_stories;
                        listView.adapter.update(true);
                        checkDone(true);
                    })
            );
            items.add(UItem.asShadow(-4, null));
            items.add(UItem.asShadow(-5, null));
            items.add(UItem.asShadow(-6, null));
            items.add(UItem.asShadow(-7, null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (recipientsHelper.onClick(item)) {
            return;
        }
        if (item.id == RADIO_EXCLUDE) {
            recipientsHelper.setExclude(exclude = true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_INCLUDE) {
            recipientsHelper.setExclude(exclude = false);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == BUTTON_DELETE) {
            selectedBot = null;
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_USER_ADD) {
            TLRPC.User bot = foundBots.get(item.dialogId);
            if (bot == null) return;
            if (!bot.bot_business) {
                showDialog(
                    new AlertDialog.Builder(getContext(), resourceProvider)
                        .setTitle(getString(R.string.BusinessBotNotSupportedTitle))
                        .setMessage(AndroidUtilities.replaceTags(getString(R.string.BusinessBotNotSupportedMessage)))
                        .setPositiveButton(getString(R.string.OK), null)
                        .create()
                );
                return;
            }
            selectedBot = bot;
            AndroidUtilities.hideKeyboard(editText);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_MESSAGES) {
            ((TextCheckCell2) view).setChecked(expandedMessagesSection = !expandedMessagesSection);
            listView.adapter.update(true);
//            listView.smoothScrollBy(0, dp(200));
        } else if (item.id == PERMISSION_MESSAGES_READ) {
            AndroidUtilities.shakeViewSpring(view, shakeDp = -shakeDp);
        } else if (item.id == PERMISSION_MESSAGES_REPLY) {
            ((CheckBoxCell) view).setChecked(rights.reply = !rights.reply, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_MESSAGES_MARK_AS_READ) {
            ((CheckBoxCell) view).setChecked(rights.read_messages = !rights.read_messages, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_MESSAGES_DELETE_SENT) {
            ((CheckBoxCell) view).setChecked(rights.delete_sent_messages = !rights.delete_sent_messages, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_MESSAGES_DELETE_RECEIVED) {
            ((CheckBoxCell) view).setChecked(rights.delete_received_messages = !rights.delete_received_messages, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_PROFILE) {
            ((TextCheckCell2) view).setChecked(expandedProfileSection = !expandedProfileSection);
            listView.adapter.update(true);
//            listView.smoothScrollBy(0, dp(200));
        } else if (item.id == PERMISSION_PROFILE_NAME) {
            ((CheckBoxCell) view).setChecked(rights.edit_name = !rights.edit_name, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_PROFILE_BIO) {
            ((CheckBoxCell) view).setChecked(rights.edit_bio = !rights.edit_bio, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_PROFILE_PICTURE) {
            ((CheckBoxCell) view).setChecked(rights.edit_profile_photo = !rights.edit_profile_photo, true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == PERMISSION_PROFILE_USERNAME) {
            checkAlert(item.id, !rights.edit_username, () -> {
                ((CheckBoxCell) view).setChecked(rights.edit_username = !rights.edit_username, true);
                listView.adapter.update(true);
                checkDone(true);
            });
        } else if (item.id == PERMISSION_GIFTS) {
            ((TextCheckCell2) view).setChecked(expandedGiftsSection = !expandedGiftsSection);
            listView.adapter.update(true);
//            listView.smoothScrollBy(0, dp(200));
        } else if (item.id == PERMISSION_GIFTS_VIEW) {
            checkAlert(item.id, !rights.view_gifts, () -> {
                ((CheckBoxCell) view).setChecked(rights.view_gifts = !rights.view_gifts, true);
                listView.adapter.update(true);
                checkDone(true);
            });
        } else if (item.id == PERMISSION_GIFTS_SELL) {
            checkAlert(item.id, !rights.sell_gifts, () -> {
                ((CheckBoxCell) view).setChecked(rights.sell_gifts = !rights.sell_gifts, true);
                listView.adapter.update(true);
                checkDone(true);
            });
        } else if (item.id == PERMISSION_GIFTS_SETTINGS) {
            checkAlert(item.id, !rights.change_gift_settings, () -> {
                ((CheckBoxCell) view).setChecked(rights.change_gift_settings = !rights.change_gift_settings, true);
                listView.adapter.update(true);
                checkDone(true);
            });
        } else if (item.id == PERMISSION_GIFTS_TRANSFER) {
            checkAlert(item.id, !rights.transfer_and_upgrade_gifts, () -> {
                ((CheckBoxCell) view).setChecked(rights.transfer_and_upgrade_gifts = !rights.transfer_and_upgrade_gifts, true);
                listView.adapter.update(true);
                checkDone(true);
            });
        } else if (item.id == PERMISSION_GIFTS_TRANSFER_STARS) {
            checkAlert(item.id, !rights.transfer_stars, () -> {
                ((CheckBoxCell) view).setChecked(rights.transfer_stars = !rights.transfer_stars, true);
                listView.adapter.update(true);
                checkDone(true);
            });
        } else if (item.id == PERMISSION_STORIES) {
            checkAlert(item.id, !rights.manage_stories, () -> {
                rights.manage_stories = !rights.manage_stories;
                listView.adapter.update(true);
                checkDone(true);
            });
        }
    }

    private void clear(View view) {
        selectedBot = null;
        listView.adapter.update(true);
        checkDone(true);
    }

    private void processDone() {
        if (doneButtonDrawable.getProgress() > 0f) return;

        if (!hasChanges()) {
            finishFragment();
            return;
        }

        if (!recipientsHelper.validate(listView)) {
            return;
        }

        ArrayList<TLObject> requests = new ArrayList<>();

        if (currentBot != null && (selectedBot == null || currentBot.bot_id != selectedBot.id)) {
            TL_account.updateConnectedBot req = new TL_account.updateConnectedBot();
            req.deleted = true;
            req.bot = getMessagesController().getInputUser(currentBot.bot_id);
            req.recipients = new TL_account.TL_inputBusinessBotRecipients();
            requests.add(req);
        }

        if (selectedBot != null) {
            TL_account.updateConnectedBot req = new TL_account.updateConnectedBot();
            req.deleted = false;
            req.rights = rights;
            req.bot = getMessagesController().getInputUser(selectedBot);
            req.recipients = recipientsHelper.getBotInputValue();
            requests.add(req);

            if (currentBot != null) {
                currentBot.bot_id = selectedBot.id;
                currentBot.recipients = recipientsHelper.getBotValue();
                currentBot.rights = rights;
            }
        }

        if (requests.isEmpty()) {
            finishFragment();
            return;
        }

        final int[] requestsReceived = new int[] { 0 };
        for (int i = 0; i < requests.size(); ++i) {
            getConnectionsManager().sendRequest(requests.get(i), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (err != null) {
                    doneButtonDrawable.animateToProgress(0f);
                    BulletinFactory.showError(err);
                } else if (res instanceof TLRPC.TL_boolFalse) {
                    doneButtonDrawable.animateToProgress(0f);
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                } else {
                    if (res instanceof TLRPC.Updates) {
                        Utilities.stageQueue.postRunnable(() -> {
                            MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                        });
                    }
                    requestsReceived[0]++;
                    if (requestsReceived[0] == requests.size()) {
                        BusinessChatbotController.getInstance(currentAccount).invalidate(true);
                        getMessagesController().clearFullUsers();
                        finishFragment();
                    }
                }
            }));
        }
    }

    private boolean loading;
    private boolean valueSet;
    private void setValue() {
        if (loading || valueSet) return;
        loading = true;
        BusinessChatbotController.getInstance(currentAccount).load(bots -> {
            currentValue = bots;
            currentBot = currentValue == null || currentValue.connected_bots.isEmpty() ? null : currentValue.connected_bots.get(0);
            selectedBot = currentBot == null ? null : getMessagesController().getUser(currentBot.bot_id);
            rights = currentBot != null ? TL_account.TL_businessBotRights.clone(currentBot.rights) : TL_account.TL_businessBotRights.makeDefault();
            exclude = currentBot != null ? currentBot.recipients.exclude_selected : true;
            if (recipientsHelper != null) {
                recipientsHelper.setValue(currentBot == null ? null : currentBot.recipients);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            checkDone(true);
            valueSet = true;
        });
    }

    public boolean hasChanges() {
        if (!valueSet) return false;
        if ((selectedBot != null) != (currentBot != null)) return true;
        if ((selectedBot == null ? 0 : selectedBot.id) != (currentBot == null ? 0 : currentBot.bot_id)) return true;
        if (selectedBot != null) {
            if (!rights.equals(currentBot.rights)) {
                return true;
            }
            if (recipientsHelper != null && recipientsHelper.hasChanges()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasChanges()) {
            if (invoked) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
                builder.setMessage(LocaleController.getString(R.string.BusinessBotUnsavedChanges));
                builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
                builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
                showDialog(builder.create());
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void checkDone(boolean animated) {
        if (doneButton == null) return;
        final boolean hasChanges = hasChanges();
        doneButton.setEnabled(hasChanges);
        if (animated) {
            doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
        } else {
            doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        setValue();
        return super.onFragmentCreate();
    }
}
