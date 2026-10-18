package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceArrows;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLink;
import static org.telegram.messenger.AndroidUtilities.replaceSingleTag;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Cells.TextCell.applyNewSpan;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_ACCOUNTS;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.BusinessChatbotController;
import org.telegram.ui.Business.ChatbotsActivity;
import org.telegram.ui.Business.LocationActivity;
import org.telegram.ui.Business.OpeningHoursActivity;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Objects;

public class UserInfoActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private EditTextCell firstNameEdit;
    private EditTextCell lastNameEdit;
    private EditTextCell bioEdit;

    private int bioInfoHash = Integer.MIN_VALUE;
    private CharSequence bioInfo;
    private CharSequence birthdayInfo;

    private ArrayList<TL_account.TL_connectedBot> bots = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.EditAccountInfo2);
    }

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    public UniversalRecyclerView listView;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.updatedChatbot);
        getContactsController().loadPrivacySettings();
        BusinessChatbotController.getInstance(currentAccount).load(null);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.updatedChatbot);
        super.onFragmentDestroy();
        if (!wasSaved) {
            processDone(false);
        }
    }

    private void openBioSettings() {
        presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_BIO, true));
    }

    private void updateBioInfo() {
        final int oldBioInfoHash = bioInfoHash;
        final boolean hasBio = bioEdit != null && !TextUtils.isEmpty(bioEdit.getText());
        if (!hasBio) {
            bioInfo = getString(R.string.EditProfileBioInfo2);
            bioInfoHash = Objects.hash(0);
        } else {
            final ArrayList<TLRPC.PrivacyRule> bioRules = getContactsController().getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_BIO);
            if (bioRules == null) {
                bioInfo = getString(R.string.Loading);
                bioInfoHash = Objects.hash(1);
            } else {
                int type = -1, currentType = -1;
                Boolean premium = null, miniapps = null;
                boolean hadAllowContacts = false;
                int currentPlus = 0, currentMinus = 0;
                for (int a = 0; a < bioRules.size(); a++) {
                    final TLRPC.PrivacyRule rule = bioRules.get(a);
                    if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                        final TLRPC.TL_privacyValueAllowChatParticipants privacyValueAllowChatParticipants = (TLRPC.TL_privacyValueAllowChatParticipants) rule;
                        for (int b = 0, N = privacyValueAllowChatParticipants.chats.size(); b < N; b++) {
                            final TLRPC.Chat chat = getMessagesController().getChat(privacyValueAllowChatParticipants.chats.get(b));
                            if (chat != null) {
                                currentPlus += Math.max(0, chat.participants_count - 1);
                            }
                        }
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                        final TLRPC.TL_privacyValueDisallowChatParticipants privacyValueDisallowChatParticipants = (TLRPC.TL_privacyValueDisallowChatParticipants) rule;
                        for (int b = 0, N = privacyValueDisallowChatParticipants.chats.size(); b < N; b++) {
                            final TLRPC.Chat chat = getMessagesController().getChat(privacyValueDisallowChatParticipants.chats.get(b));
                            if (chat != null) {
                                currentMinus += Math.max(0, chat.participants_count - 1);
                            }
                        }
                    } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                        final TLRPC.TL_privacyValueAllowUsers privacyValueAllowUsers = (TLRPC.TL_privacyValueAllowUsers) rule;
                        currentPlus += privacyValueAllowUsers.users.size();
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                        final TLRPC.TL_privacyValueDisallowUsers privacyValueDisallowUsers = (TLRPC.TL_privacyValueDisallowUsers) rule;
                        currentMinus += privacyValueDisallowUsers.users.size();
                    } else if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                        type = 0;
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll && !hadAllowContacts) {
                        type = 1;
                    } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                        hadAllowContacts = true;
                        type = 2;
                    } else if (type == -1) {
                        if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                            type = 0;
                        } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll && !hadAllowContacts) {
                            type = 1;
                        } else {
                            type = 2;
                        }
                    }
                }
                if (type == PrivacyControlActivity.TYPE_EVERYBODY || type == -1 && (currentMinus > 0 || miniapps != null && !miniapps)) {
                    currentType = PrivacyControlActivity.TYPE_EVERYBODY;
                } else if (type == PrivacyControlActivity.TYPE_CONTACTS || type == -1 && currentMinus > 0 && currentPlus > 0) {
                    currentType = PrivacyControlActivity.TYPE_CONTACTS;
                } else if (type == PrivacyControlActivity.TYPE_NOBODY || type == -1 && (currentPlus > 0 || miniapps != null && miniapps)) {
                    currentType = PrivacyControlActivity.TYPE_NOBODY;
                }

                if (currentType == PrivacyControlActivity.TYPE_EVERYBODY) {
                    if (currentMinus <= 0) {
                        bioInfo = replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.EditProfileBioInfoEveryone), this::openBioSettings), true);
                    } else {
                        bioInfo = replaceArrows(AndroidUtilities.replaceSingleTag(formatString(R.string.EditProfileBioInfoEveryoneExcept, currentMinus), this::openBioSettings), true);
                    }
                } else if (currentType == PrivacyControlActivity.TYPE_CONTACTS) {
                    if (currentMinus <= 0 && currentPlus <= 0) {
                        bioInfo = replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.EditProfileBioInfoContacts), this::openBioSettings), true);
                    } else {
                        String extra = "";
                        if (currentPlus > 0) {
                            extra += "+" + currentPlus;
                        }
                        if (currentMinus > 0) {
                            if (extra.length() > 0) extra += ", ";
                            extra += "-" + currentMinus;
                        }
                        bioInfo = replaceArrows(AndroidUtilities.replaceSingleTag(formatString(R.string.EditProfileBioInfoContactsExtra, extra), this::openBioSettings), true);
                    }
                } else if (currentType == PrivacyControlActivity.TYPE_EVERYBODY) {
                    if (currentPlus <= 0) {
                        bioInfo = replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.EditProfileBioInfoNobody), this::openBioSettings), true);
                    } else {
                        bioInfo = replaceArrows(AndroidUtilities.replaceSingleTag(formatString(R.string.EditProfileBioInfoNobodyExcept, currentPlus), this::openBioSettings), true);
                    }
                } else {
                    bioInfo = AndroidUtilities.replaceSingleTag(getString(R.string.EditProfileBioInfoUnknown), this::openBioSettings);
                }
                bioInfoHash = Objects.hash(10 + currentType, currentPlus, currentMinus);
            }
        }
        if (oldBioInfoHash != bioInfoHash && listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public View createView(Context context) {
        firstNameEdit = new EditTextCell(context, getString(R.string.EditProfileFirstName), false, false, -1, resourceProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                super.onTextChanged(newText);
                checkDone(true);
            }
        };
        firstNameEdit.setDivider(true);
        firstNameEdit.hideKeyboardOnEnter();
        lastNameEdit = new EditTextCell(context, getString(R.string.EditProfileLastName), false, false, -1, resourceProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                super.onTextChanged(newText);
                checkDone(true);
            }
        };
        lastNameEdit.hideKeyboardOnEnter();
        bioEdit = new EditTextCell(context, getString(R.string.EditProfileBioHint2), true, false, getMessagesController().getAboutLimit(), resourceProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                super.onTextChanged(newText);
                checkDone(true);
                updateBioInfo();
            }
        };
        bioEdit.setShowLimitWhenEmpty(true);
        updateBioInfo();
        bioInfo = AndroidUtilities.replaceSingleTag(getString(R.string.EditProfileBioInfo2), this::openBioSettings);

        super.createView(context);
        this.listView = super.listView;
        listView.setSections();
        listView.setClipToPadding(false);
        actionBar.setAdaptiveBackground(listView);
        if (parentLayout != null && parentLayout.isRightLayout()) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_close);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
            if (id == -1) {
                if (onBackPressed(true)) {
                    finishFragment();
                }
            } else if (id == done_button) {
                processDone(true);
            }
            }
        });
        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, dp(56), LocaleController.getString(R.string.Done));
        checkDone(false);

        setValue();

        return fragmentView;
    }

    private static final int BUTTON_BIRTHDAY = 1;
    private static final int BUTTON_REMOVE_BIRTHDAY = 2;
    private static final int BUTTON_CHANNEL = 3;
    private static final int BUTTON_HOURS = 4;
    private static final int BUTTON_LOCATION = 5;
    private static final int BUTTON_AI = 6;
    private static final int INFO_PHONE = 7;
    private static final int INFO_USERNAME = 8;
    private static final int INFO_BIRTHDAY = 9;
    private static final int BUTTON_ADD_ACCOUNT = 10;
    private static final int BUTTON_LOGOUT = 11;

    private final ArrayList<Integer> accountNumbers = new ArrayList<>();
    private void updateAccounts() {
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && currentAccount != a) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });
    }

    @Keep
    public int firstNameRow;
    @Keep
    public int lastNameRow;
    @Keep
    public int bioRow;
    @Keep
    public int birthdayRow;
    @Keep
    public int numberRow;
    @Keep
    public int usernameRow;
    @Keep
    public int channelRow;
    @Keep
    public int addAccountRow;
    @Keep
    public int logoutRow;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        addAccountRow = -1;
        numberRow = -1;
        updateAccounts();

        items.add(UItem.asHeader(getString(R.string.EditProfileName)));
        firstNameRow = items.size();
        items.add(UItem.asCustom(firstNameEdit));
        lastNameRow = items.size();
        items.add(UItem.asCustom(lastNameEdit));
        items.add(UItem.asShadow(-1, null));
        bioRow = items.size();
        items.add(UItem.asCustom(bioEdit));
        items.add(UItem.asShadow(bioInfo));

        final TLRPC.User user = getUserConfig().getCurrentUser();
        items.add(UItem.asHeader(getString(R.string.EditAccountInfoHeader)));
        if (user != null) {
            numberRow = items.size();
            items.add(SettingsActivity.SettingCell.Factory.of(INFO_PHONE, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.settings_calls, PhoneFormat.getInstance().format("+" + user.phone), getString(R.string.TapToChangePhone)));
        }
        usernameRow = items.size();
        if (UserObject.getPublicUsername(user) != null) {
            items.add(SettingsActivity.SettingCell.Factory.of(INFO_USERNAME, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.filled_chatlist_mention, "@" + UserObject.getPublicUsername(user), getString(R.string.Username)));
        } else {
            items.add(SettingsActivity.SettingCell.Factory.of(INFO_USERNAME, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.filled_chatlist_mention, getString(R.string.AddUsername), null));
        }
        birthdayRow = items.size();
        if (birthday != null) {
            items.add(SettingsActivity.SettingCell.Factory.of(INFO_BIRTHDAY, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.filled_birthday, birthdayString(birthday), getString(R.string.ContactBirthday)));
        } else {
            items.add(SettingsActivity.SettingCell.Factory.of(INFO_BIRTHDAY, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.filled_birthday, getString(R.string.AddBirthday), null));
        }
        if (!getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY)) {
            ArrayList<TLRPC.PrivacyRule> rules = getContactsController().getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY);
            if (rules != null && birthdayInfo == null) {
                String string = getString(R.string.EditProfileBirthdayInfoContacts);
                if (!rules.isEmpty()) {
                    for (int i = 0; i < rules.size(); ++i) {
                        if (rules.get(i) instanceof TLRPC.TL_privacyValueAllowContacts) {
                            string = getString(R.string.EditProfileBirthdayInfoContacts);
                            break;
                        }
                        if (rules.get(i) instanceof TLRPC.TL_privacyValueAllowAll || rules.get(i) instanceof TLRPC.TL_privacyValueDisallowAll) {
                            string = getString(R.string.EditProfileBirthdayInfo);
                        }
                    }
                }
                birthdayInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(string, () -> {
                    presentFragment(new PrivacyControlActivity(PrivacyControlActivity.PRIVACY_RULES_TYPE_BIRTHDAY));
                }), true);
            }
        }
        items.add(UItem.asShadow(birthdayInfo));

        channelRow = items.size();
        if (channel == null) {
            items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_CHANNEL, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.msg_filled_menu_channels, getString(R.string.EditProfileChannelTitle), null, getString(R.string.EditProfileChannelAdd)));
        } else {
            items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_CHANNEL, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.msg_filled_menu_channels, getString(R.string.EditProfileChannelTitle), channel.title));
        }
        if (hadHours) {
            items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_HOURS, IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom, R.drawable.filled_premium_hours, getString(R.string.EditProfileHours)));
        }
        if (hadLocation) {
            items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_LOCATION, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.filled_location, getString(R.string.EditProfileLocation)));
        }
        if (bots != null && !bots.isEmpty()) {
            final StringBuilder value = new StringBuilder();
            for (final TL_account.TL_connectedBot bot : bots) {
                final TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);
                if (botUser != null) {
                    if (value.length() > 0) value.append(", ");
                    value.append(UserObject.getUserName(botUser));
                }
            }
            items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_AI, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.premium_ai_editor, getString(R.string.EditProfileChatAutomation), value));
        } else {
            items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_AI, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.premium_ai_editor, applyNewSpan(getString(R.string.EditProfileChatAutomation))));
        }
        items.add(UItem.asShadow(-3, getString(R.string.EditProfileChatAutomationInfo)));
        final boolean hasAddAccount = UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT;
        if (hasAddAccount) {
            addAccountRow = items.size();
            items.add(InfoCell.Factory.of(BUTTON_ADD_ACCOUNT, R.drawable.outline_add_account, getString(R.string.AddAccount), null, 0).accent());
        }
        if (!accountNumbers.isEmpty()) {
            if (!hasAddAccount) {
                items.add(UItem.asHeader(getString(R.string.SettingsAccounts)));
            }
            for (int i = 0; i < accountNumbers.size(); ++i) {
                items.add(SettingsActivity.AccountCell.Factory.of(i, accountNumbers.get(i)));
            }
            if (!UserConfig.hasPremiumOnAccounts()) {
                final int moreAccounts = Math.max(0, UserConfig.getMaxAccountCount() - UserConfig.getActivatedAccountsCount());
                items.add(UItem.asShadow(
                    TextUtils.concat(
                        moreAccounts > 0 ? LocaleController.formatPluralStringComma("AddAccountInfo1", moreAccounts) + " " : "",
                        replaceSingleTag(LocaleController.formatPluralStringComma("AddAccountInfo2", UserConfig.getMaxAccountCount()), () -> {
                            presentFragment(new PremiumPreviewFragment("add_account"));
                        })
                    )
                ));
            } else {
                items.add(UItem.asShadow(null));
            }
        }
        logoutRow = items.size();
        items.add(InfoCell.Factory.of(BUTTON_LOGOUT, R.drawable.msg_leave, getString(R.string.LogOut), null, 0).red());
        items.add(UItem.asShadow(-4, null));
    }

    public static String birthdayString(TL_account.TL_birthday birthday) {
        if (birthday == null) {
            return "—";
        }
        if ((birthday.flags & 1) != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, birthday.year);
            calendar.set(Calendar.MONTH, birthday.month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, birthday.day);
            return LocaleController.getInstance().getFormatterBoostExpired().format(calendar.getTimeInMillis());
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.MONTH, birthday.month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, birthday.day);
            return LocaleController.getInstance().getFormatterDayMonth().format(calendar.getTimeInMillis());
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_ADD_ACCOUNT) {
            int freeAccounts = 0;
            Integer availableAccount = null;
            for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
                if (!UserConfig.getInstance(a).isClientActivated()) {
                    freeAccounts++;
                    if (availableAccount == null) {
                        availableAccount = a;
                    }
                }
            }
            if (!UserConfig.hasPremiumOnAccounts()) {
                freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
            }
            if (freeAccounts > 0 && availableAccount != null) {
                presentFragment(new LoginActivity(availableAccount));
            } else if (!UserConfig.hasPremiumOnAccounts()) {
                showDialog(new LimitReachedBottomSheet(this, getContext(), TYPE_ACCOUNTS, currentAccount, null));
            }
        } else if (item.instanceOf(SettingsActivity.AccountCell.Factory.class)) {
            final int account = item.intValue;
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.switchToAccount(account, true);
            }
        } else if (item.id == BUTTON_BIRTHDAY || item.id == INFO_BIRTHDAY) {
            showDialog(AlertsCreator.createBirthdayPickerDialog(
                getContext(),
                getString(R.string.EditProfileBirthdayTitle),
                getString(R.string.EditProfileBirthdayButton),
                birthday,
                selectedBirthday -> {
                    birthday = selectedBirthday;
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                    checkDone(true);
                },
                null,
                false, birthday != null, getResourceProvider()
            ).create());
        } else if (item.id == BUTTON_REMOVE_BIRTHDAY) {
            birthday = null;
            if (listView != null) {
                listView.adapter.update(true);
            }
            checkDone(true);
        } else if (item.id == BUTTON_CHANNEL) {
            presentFragment(new ChooseChannelFragment(
                channels,
                (channel == null ? 0 : channel.id), chat -> {
                    if (channel == chat) return;
                    channel = chat;
                    if (chat != null) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.EditProfileChannelSet)).show();
                    }
                    checkDone(true);
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                }
            ));
//            showDialog(new ChooseChannel(this, channels, (channel == null ? 0 : channel.id), chat -> {
//                if (channel == chat) return;
//                channel = chat;
//                checkDone(true);
//                if (listView != null) {
//                    listView.adapter.update(true);
//                }
//            }));
        } else if (item.id == BUTTON_LOCATION) {
            presentFragment(new LocationActivity());
        } else if (item.id == BUTTON_HOURS) {
            presentFragment(new OpeningHoursActivity());
        } else if (item.id == BUTTON_AI) {
            presentFragment(new ChatbotsActivity());
        } else if (item.id == INFO_PHONE) {
            presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));
        } else if (item.id == INFO_USERNAME) {
            presentFragment(new ChangeUsernameActivity());
        } else if (item.id == BUTTON_LOGOUT) {
            presentFragment(new LogoutActivity());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            setValue();
        } else if (id == NotificationCenter.updateInterfaces) {
            if (listView != null) {
                listView.adapter.update(true);
            }
        } else if (id == NotificationCenter.privacyRulesUpdated) {
            updateBioInfo();
            if (listView != null) {
                listView.adapter.update(true);
            }
        } else if (id == NotificationCenter.updatedChatbot) {
            final TL_account.connectedBots bots = BusinessChatbotController.getInstance(currentAccount).getValue();
            this.bots = bots != null && bots.connected_bots != null ? bots.connected_bots : new ArrayList<>();
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        channels.invalidate();
        channels.subscribe(() -> {
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
        channels.fetch();
        birthdayInfo = null;
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private String currentFirstName;
    private String currentLastName;
    private String currentBio;
    private TL_account.TL_birthday currentBirthday;
    private long currentChannel;

    private TL_account.TL_birthday birthday;
    private TLRPC.Chat channel;

    private boolean hadHours, hadLocation;

    private AdminedChannelsFetcher channels = new AdminedChannelsFetcher(currentAccount, true);

    private boolean valueSet;
    private void setValue() {
        if (valueSet) return;

        final long selfId = getUserConfig().getClientUserId();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(selfId);
        if (userFull == null) {
            getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, getClassGuid());
            return;
        }

        TLRPC.User user = userFull.user;
        if (user == null) {
            user = getUserConfig().getCurrentUser();
        }

        if (user == null) {
            return;
        }

        firstNameEdit.setText(currentFirstName = user.first_name);
        lastNameEdit.setText(currentLastName = user.last_name);
        bioEdit.setText(currentBio = userFull.about);
        birthday = currentBirthday = userFull.birthday;
        if ((userFull.flags2 & 64) != 0) {
            currentChannel = userFull.personal_channel_id;
            channel = getMessagesController().getChat(currentChannel);
        } else {
            currentChannel = 0;
            channel = null;
        }
        hadHours = userFull.business_work_hours != null;
        hadLocation = userFull.business_location != null;
        checkDone(true);

        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        valueSet = true;
    }

    public boolean hasChanges() {
        return (
            !TextUtils.equals(currentFirstName == null ? "" : currentFirstName, firstNameEdit.getText().toString()) ||
            !TextUtils.equals(currentLastName == null ? "" : currentLastName, lastNameEdit.getText().toString()) ||
            !TextUtils.equals(currentBio == null ? "" : currentBio, bioEdit.getText().toString()) ||
            !birthdaysEqual(currentBirthday, birthday) ||
            currentChannel != (channel != null ? channel.id : 0)
        );
    }

    public static boolean birthdaysEqual(TL_account.TL_birthday a, TL_account.TL_birthday b) {
        return !((a == null) == (b != null) || a != null && (a.day != b.day || a.month != b.month || a.year != b.year));
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

    private boolean wasSaved = false;
    private int shiftDp = -4;
    private void processDone(boolean error) {
        if (doneButtonDrawable.getProgress() > 0f) return;

        if (error && TextUtils.isEmpty(firstNameEdit.getText())) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(firstNameEdit, shiftDp = -shiftDp);
            return;
        }

        doneButtonDrawable.animateToProgress(1f);
        TLRPC.User user = getUserConfig().getCurrentUser();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
        if (user == null || userFull == null) return;

        ArrayList<TLObject> requests = new ArrayList<TLObject>();

        if (
            !TextUtils.isEmpty(firstNameEdit.getText()) &&
            (
                !TextUtils.equals(currentFirstName, firstNameEdit.getText().toString()) ||
                !TextUtils.equals(currentLastName, lastNameEdit.getText().toString()) ||
                !TextUtils.equals(currentBio, bioEdit.getText().toString())
            )
        ) {
            TL_account.updateProfile req1 = new TL_account.updateProfile();

            req1.flags |= 1;
            req1.first_name = user.first_name = firstNameEdit.getText().toString();

            req1.flags |= 2;
            req1.last_name = user.last_name = lastNameEdit.getText().toString();

            req1.flags |= 4;
            req1.about = userFull.about = bioEdit.getText().toString();
            userFull.flags = TextUtils.isEmpty(userFull.about) ? (userFull.flags & ~2) : (userFull.flags | 2);

            requests.add(req1);
        }

        TL_account.TL_birthday oldBirthday = userFull != null ? userFull.birthday : null;
        if (!birthdaysEqual(currentBirthday, birthday)) {
            TL_account.updateBirthday req = new TL_account.updateBirthday();
            if (birthday != null) {
                userFull.flags2 |= 32;
                userFull.birthday = birthday;
                req.flags |= 1;
                req.birthday = birthday;
            } else {
                userFull.flags2 &=~ 32;
                userFull.birthday = null;
            }
            requests.add(req);
            getMessagesController().invalidateContentSettings();

            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumPromoUpdated);
        }

        if (currentChannel != (channel != null ? channel.id : 0)) {
            TL_account.updatePersonalChannel req = new TL_account.updatePersonalChannel();
            req.channel = MessagesController.getInputChannel(channel);
            if (channel != null) {
                userFull.flags |= 64;
                if (userFull.personal_channel_id != channel.id) {
                    userFull.personal_channel_message = 0;
                }
                userFull.personal_channel_id = channel.id;
            } else {
                userFull.flags &=~ 64;
                userFull.personal_channel_message = 0;
                userFull.personal_channel_id = 0;
            }
            requests.add(req);
        }

        if (requests.isEmpty()) {
            finishFragment();
            return;
        }

        final int[] requestsReceived = new int[] { 0 };
        for (int i = 0; i < requests.size(); ++i) {
            final TLObject req = requests.get(i);
            getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (err != null) {
                    doneButtonDrawable.animateToProgress(0f);
                    if (req instanceof TL_account.updateBirthday && err.text != null && err.text.startsWith("FLOOD_WAIT_")) {
                        if (getContext() != null) {
                            showDialog(
                                new AlertDialog.Builder(getContext(), resourceProvider)
                                    .setTitle(getString(R.string.PrivacyBirthdayTooOftenTitle))
                                    .setMessage(getString(R.string.PrivacyBirthdayTooOftenMessage))
                                    .setPositiveButton(getString(R.string.OK), null)
                                    .create()
                            );
                        }
                    } else {
                        BulletinFactory.showError(err);
                    }
                    if (req instanceof TL_account.updateBirthday) {
                        if (oldBirthday != null) {
                            userFull.flags |= 32;
                        } else {
                            userFull.flags &=~ 32;
                        }
                        userFull.birthday = oldBirthday;
                        getMessagesStorage().updateUserInfo(userFull, false);
                    }
                } else if (res instanceof TLRPC.TL_boolFalse) {
                    doneButtonDrawable.animateToProgress(0f);
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                } else {
                    wasSaved = true;
                    requestsReceived[0]++;
                    if (requestsReceived[0] == requests.size()) {
                        finishFragment();
                    }
                }
            }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);
        }
        getMessagesStorage().updateUserInfo(userFull, false);
        getUserConfig().saveConfig(true);

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
    }

    public static class AdminedChannelsFetcher {
        public final int currentAccount;
        public final boolean for_personal;
        public AdminedChannelsFetcher(int currentAccount, boolean for_personal) {
            this.currentAccount = currentAccount;
            this.for_personal = for_personal;
        }

        public boolean loaded, loading;
        public final ArrayList<TLRPC.Chat> chats = new ArrayList<>();

        public void invalidate() {
            loaded = false;
        }

        public void fetch() {
            if (loaded || loading) return;
            loading = true;
            TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
            req.for_personal = for_personal;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.messages_Chats) {
                    chats.clear();
                    chats.addAll(((TLRPC.messages_Chats) res).chats);
                }
                MessagesController.getInstance(currentAccount).putChats(chats, false);
                loading = false;
                loaded = true;
                for (Runnable callback : callbacks) {
                    callback.run();
                }
                callbacks.clear();
            }));
        }

        private ArrayList<Runnable> callbacks = new ArrayList<>();
        public void subscribe(Runnable whenDone) {
            if (loaded)
                whenDone.run();
            else
                callbacks.add(whenDone);
        }
    }

    private static class InfoCell extends LinearLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final ImageView iconView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final TextView subtitleView;
        private final ImageView icon2View;

        private boolean accent;
        private boolean red;

        public InfoCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(HORIZONTAL);

            this.resourcesProvider = resourcesProvider;

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            addView(iconView, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT, 12, 0, 12, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setPadding(0, dp(10), 0, dp(10));
            addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0, 32, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.LEFT, 0, 0, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.LEFT, 0, 4.33f, 0, 0));

            icon2View = new ImageView(context);
            icon2View.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon2View, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 12, 0, 12, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        private void updateColors() {
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(red ? Theme.key_text_RedBold : accent ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            icon2View.setColorFilter(new PorterDuffColorFilter(Theme.getColor(red ? Theme.key_text_RedBold : accent ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            titleView.setTextColor(Theme.getColor(red ? Theme.key_text_RedRegular : accent ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(red ? Theme.key_text_RedRegular : accent ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        }

        public void set(int icon, CharSequence title, CharSequence subtitle, boolean accent, boolean red, int icon2) {
            this.accent = accent;
            this.red = red;
            iconView.setImageResource(icon);
            if (icon2 != 0) {
                icon2View.setVisibility(View.VISIBLE);
                icon2View.setImageResource(icon2);
            } else {
                icon2View.setVisibility(View.GONE);
            }
            titleView.setText(title);
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
            final int p = dp(TextUtils.isEmpty(subtitle) ? 15 : 10);
            textLayout.setPadding(0, p, 0, p);
            updateColors();
        }

        public static class Factory extends UItem.UItemFactory<InfoCell> {
            static { setup(new Factory()); }

            @Override
            public InfoCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new InfoCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((InfoCell) view).set(item.iconResId, item.text, item.subtext, item.accent, item.red, item.intValue);
            }

            public static UItem of(int id, int icon, CharSequence title, CharSequence subtitle, int icon2) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.iconResId = icon;
                item.text = title;
                item.subtext = subtitle;
                item.intValue = icon2;
                return item;
            }
        }
    }

    private static class ChooseChannelFragment extends UniversalFragment {

        private AdminedChannelsFetcher channels;
        private long selectedChannel;
        private Utilities.Callback<TLRPC.Chat> whenSelected;

        private String query;

        public ChooseChannelFragment(
            AdminedChannelsFetcher channels,
            long selectedChannel,
            Utilities.Callback<TLRPC.Chat> whenSelected
        ) {
            super();
            this.channels = channels;
            this.selectedChannel = selectedChannel;
            this.whenSelected = whenSelected;
            channels.subscribe(() -> {
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
        }

        private ActionBarMenuItem searchItem;

        @Override
        public View createView(Context context) {
            searchItem = actionBar.createMenu().addItem(0, R.drawable.outline_header_search, getResourceProvider()).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {}
                @Override
                public void onSearchCollapse() {
                    query = null;
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                }

                @Override
                public void onTextChanged(EditText editText) {
                    query = editText.getText().toString();
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                }
            });
            searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
            searchItem.setContentDescription(LocaleController.getString(R.string.Search));
            searchItem.setVisibility(View.GONE);

            super.createView(context);

            listView.setSections();
            actionBar.setAdaptiveBackground(listView);

            return fragmentView;
        }

        @Override
        protected CharSequence getTitle() {
            return getString(R.string.EditProfileChannelTitle);
        }

        private final static int BUTTON_REMOVE = 1;
        private final static int BUTTON_CREATE = 2;

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (TextUtils.isEmpty(query) && selectedChannel != 0) {
                items.add(UItem.asButton(BUTTON_REMOVE, R.drawable.msg_archive_hide, getString(R.string.EditProfileChannelHide)).red());
                items.add(UItem.asShadow(null));
            }
            if (TextUtils.isEmpty(query)) {
                items.add(UItem.asHeader(getString(R.string.EditProfileChannelSelect)));
            }
            int count = 0;
            for (TLRPC.Chat chat : channels.chats) {
                if (chat == null || ChatObject.isMegagroup(chat)) continue;
                count++;
                if (!TextUtils.isEmpty(query)) {
                    String lq = query.toLowerCase(), lq2 = AndroidUtilities.translitSafe(lq);
                    String c = chat.title.toLowerCase(), c2 = AndroidUtilities.translitSafe(c);
                    if (!(
                        c.startsWith(lq) || c.contains(" " + lq) ||
                        c2.startsWith(lq2) || c2.contains(" " + lq2)
                    )) {
                        continue;
                    }
                }
                items.add(UItem.asFilterChat(true, -chat.id).setChecked(selectedChannel == chat.id));
            }
            if (TextUtils.isEmpty(query) && count == 0) {
                items.add(UItem.asButton(BUTTON_CREATE, R.drawable.msg_channel_create, getString(R.string.EditProfileChannelStartNew)).accent());
            }
            items.add(UItem.asShadow(null));
            if (searchItem != null) {
                searchItem.setVisibility(count > 5 ? View.VISIBLE : View.GONE);
            }
        }

        private boolean invalidateAfterPause = false;

        @Override
        public void onResume() {
            super.onResume();
            if (invalidateAfterPause) {
                channels.invalidate();
                channels.subscribe(() -> {
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                });
                invalidateAfterPause = false;
            }
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == BUTTON_REMOVE) {
                whenSelected.run(null);
                finishFragment();
            } else if (item.id == BUTTON_CREATE) {
                invalidateAfterPause = true;
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                    Bundle args = new Bundle();
                    args.putInt("step", 0);
                    presentFragment(new ChannelCreateActivity(args));
                } else {
                    presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                    preferences.edit().putBoolean("channel_intro", true).apply();
                }
            } else if (item.viewType == UniversalAdapter.VIEW_TYPE_FILTER_CHAT_CHECK) {
                finishFragment();
                whenSelected.run(getMessagesController().getChat(-item.dialogId));
            }
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }
}
