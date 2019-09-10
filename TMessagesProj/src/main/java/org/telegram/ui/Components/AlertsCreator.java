/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Cells.AccountSelectCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LanguageSelectActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.NotificationsCustomSettingsActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.ProfileNotificationsActivity;
import org.telegram.ui.ReportOtherActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class AlertsCreator {

    public static Dialog processError(int currentAccount, TLRPC.TL_error error, BaseFragment fragment, TLObject request, Object... args) {
        if (error.code == 406 || error.text == null) {
            return null;
        }
        if (request instanceof TLRPC.TL_account_saveSecureValue || request instanceof TLRPC.TL_account_getAuthorizationForm) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else if ("APP_VERSION_OUTDATED".equals(error.text)) {
                showUpdateAppAlert(fragment.getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
            } else {
                showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
            }
        } else if (request instanceof TLRPC.TL_channels_joinChannel ||
                request instanceof TLRPC.TL_channels_editAdmin ||
                request instanceof TLRPC.TL_channels_inviteToChannel ||
                request instanceof TLRPC.TL_messages_addChatUser ||
                request instanceof TLRPC.TL_messages_startBot ||
                request instanceof TLRPC.TL_channels_editBanned ||
                request instanceof TLRPC.TL_messages_editChatDefaultBannedRights ||
                request instanceof TLRPC.TL_messages_editChatAdmin ||
                request instanceof TLRPC.TL_messages_migrateChat) {
            if (fragment != null) {
                AlertsCreator.showAddUserAlert(error.text, fragment, args != null && args.length > 0 ? (Boolean) args[0] : false, request);
            } else {
                if (error.text.equals("PEER_FLOOD")) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 1);
                }
            }
        } else if (request instanceof TLRPC.TL_messages_createChat) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                AlertsCreator.showFloodWaitAlert(error.text, fragment);
            } else {
                AlertsCreator.showAddUserAlert(error.text, fragment, false, request);
            }
        } else if (request instanceof TLRPC.TL_channels_createChannel) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                AlertsCreator.showFloodWaitAlert(error.text, fragment);
            } else {
                AlertsCreator.showAddUserAlert(error.text, fragment, false, request);
            }
        } else if (request instanceof TLRPC.TL_messages_editMessage) {
            if (!error.text.equals("MESSAGE_NOT_MODIFIED")) {
                if (fragment != null) {
                    showSimpleAlert(fragment, LocaleController.getString("EditMessageError", R.string.EditMessageError));
                } else {
                    showSimpleToast(fragment, LocaleController.getString("EditMessageError", R.string.EditMessageError));
                }
            }
        } else if (request instanceof TLRPC.TL_messages_sendMessage ||
                request instanceof TLRPC.TL_messages_sendMedia ||
                request instanceof TLRPC.TL_messages_sendInlineBotResult ||
                request instanceof TLRPC.TL_messages_forwardMessages ||
                request instanceof TLRPC.TL_messages_sendMultiMedia ||
                request instanceof TLRPC.TL_messages_sendScheduledMessages) {
            switch (error.text) {
                case "PEER_FLOOD":
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 0);
                    break;
                case "USER_BANNED_IN_CHANNEL":
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 5);
                    break;
                case "SCHEDULE_TOO_MUCH":
                    showSimpleToast(fragment, LocaleController.getString("MessageScheduledLimitReached", R.string.MessageScheduledLimitReached));
                    break;
            }
        } else if (request instanceof TLRPC.TL_messages_importChatInvite) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else if (error.text.equals("USERS_TOO_MUCH")) {
                showSimpleAlert(fragment, LocaleController.getString("JoinToGroupErrorFull", R.string.JoinToGroupErrorFull));
            } else {
                showSimpleAlert(fragment, LocaleController.getString("JoinToGroupErrorNotExist", R.string.JoinToGroupErrorNotExist));
            }
        } else if (request instanceof TLRPC.TL_messages_getAttachedStickers) {
            if (fragment != null && fragment.getParentActivity() != null) {
                Toast.makeText(fragment.getParentActivity(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text, Toast.LENGTH_SHORT).show();
            }
        } else if (request instanceof TLRPC.TL_account_confirmPhone || request instanceof TLRPC.TL_account_verifyPhone || request instanceof TLRPC.TL_account_verifyEmail) {
            if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID") || error.text.contains("CODE_INVALID") || error.text.contains("CODE_EMPTY")) {
                return showSimpleAlert(fragment, LocaleController.getString("InvalidCode", R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED") || error.text.contains("EMAIL_VERIFY_EXPIRED")) {
                return showSimpleAlert(fragment, LocaleController.getString("CodeExpired", R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                return showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else {
                return showSimpleAlert(fragment, error.text);
            }
        } else if (request instanceof TLRPC.TL_auth_resendCode) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                return showSimpleAlert(fragment, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                return showSimpleAlert(fragment, LocaleController.getString("InvalidCode", R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                return showSimpleAlert(fragment, LocaleController.getString("CodeExpired", R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                return showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else if (error.code != -1000) {
                return showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
            }
        } else if (request instanceof TLRPC.TL_account_sendConfirmPhoneCode) {
            if (error.code == 400) {
                return showSimpleAlert(fragment, LocaleController.getString("CancelLinkExpired", R.string.CancelLinkExpired));
            } else if (error.text != null) {
                if (error.text.startsWith("FLOOD_WAIT")) {
                    return showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
                } else {
                    return showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
                }
            }
        } else if (request instanceof TLRPC.TL_account_changePhone) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidCode", R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString("CodeExpired", R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else {
                showSimpleAlert(fragment, error.text);
            }
        } else if (request instanceof TLRPC.TL_account_sendChangePhoneCode) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidCode", R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString("CodeExpired", R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else if (error.text.startsWith("PHONE_NUMBER_OCCUPIED")) {
                showSimpleAlert(fragment, LocaleController.formatString("ChangePhoneNumberOccupied", R.string.ChangePhoneNumberOccupied, (String) args[0]));
            } else {
                showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
            }
        } else if (request instanceof TLRPC.TL_updateUserName) {
            switch (error.text) {
                case "USERNAME_INVALID":
                    showSimpleAlert(fragment, LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                    break;
                case "USERNAME_OCCUPIED":
                    showSimpleAlert(fragment, LocaleController.getString("UsernameInUse", R.string.UsernameInUse));
                    break;
                default:
                    showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
                    break;
            }
        } else if (request instanceof TLRPC.TL_contacts_importContacts) {
            if (error == null || error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else {
                showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
            }
        } else if (request instanceof TLRPC.TL_account_getPassword || request instanceof TLRPC.TL_account_getTmpPassword) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleToast(fragment, getFloodWaitString(error.text));
            } else {
                showSimpleToast(fragment, error.text);
            }
        } else if (request instanceof TLRPC.TL_payments_sendPaymentForm) {
            switch (error.text) {
                case "BOT_PRECHECKOUT_FAILED":
                    showSimpleToast(fragment, LocaleController.getString("PaymentPrecheckoutFailed", R.string.PaymentPrecheckoutFailed));
                    break;
                case "PAYMENT_FAILED":
                    showSimpleToast(fragment, LocaleController.getString("PaymentFailed", R.string.PaymentFailed));
                    break;
                default:
                    showSimpleToast(fragment, error.text);
                    break;
            }
        } else if (request instanceof TLRPC.TL_payments_validateRequestedInfo) {
            switch (error.text) {
                case "SHIPPING_NOT_AVAILABLE":
                    showSimpleToast(fragment, LocaleController.getString("PaymentNoShippingMethod", R.string.PaymentNoShippingMethod));
                    break;
                default:
                    showSimpleToast(fragment, error.text);
                    break;
            }
        }

        return null;
    }

    public static Toast showSimpleToast(BaseFragment baseFragment, final String text) {
        if (text == null) {
            return null;
        }
        Context context;
        if (baseFragment != null && baseFragment.getParentActivity() != null) {
            context = baseFragment.getParentActivity();
        } else {
            context = ApplicationLoader.applicationContext;
        }
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        toast.show();
        return toast;
    }

    public static AlertDialog showUpdateAppAlert(final Context context, final String text, boolean updateApp) {
        if (context == null || text == null) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        if (updateApp) {
            builder.setNegativeButton(LocaleController.getString("UpdateApp", R.string.UpdateApp), (dialog, which) -> Browser.openUrl(context, BuildVars.PLAYSTORE_APP_URL));
        }
        return builder.show();
    }

    public static AlertDialog.Builder createLanguageAlert(LaunchActivity activity, final TLRPC.TL_langPackLanguage language) {
        if (language == null) {
            return null;
        }
        language.lang_code = language.lang_code.replace('-', '_').toLowerCase();
        language.plural_code = language.plural_code.replace('-', '_').toLowerCase();
        if (language.base_lang_code != null) {
            language.base_lang_code = language.base_lang_code.replace('-', '_').toLowerCase();
        }

        SpannableStringBuilder spanned;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LocaleController.LocaleInfo currentInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        String str;
        if (currentInfo.shortName.equals(language.lang_code)) {
            builder.setTitle(LocaleController.getString("Language", R.string.Language));
            str = LocaleController.formatString("LanguageSame", R.string.LanguageSame, language.name);
            builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
            builder.setNeutralButton(LocaleController.getString("SETTINGS", R.string.SETTINGS), (dialog, which) -> activity.presentFragment(new LanguageSelectActivity()));
        } else {
            if (language.strings_count == 0) {
                builder.setTitle(LocaleController.getString("LanguageUnknownTitle", R.string.LanguageUnknownTitle));
                str = LocaleController.formatString("LanguageUnknownCustomAlert", R.string.LanguageUnknownCustomAlert, language.name);
                builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
            } else {
                builder.setTitle(LocaleController.getString("LanguageTitle", R.string.LanguageTitle));
                if (language.official) {
                    str = LocaleController.formatString("LanguageAlert", R.string.LanguageAlert, language.name, (int) Math.ceil(language.translated_count / (float) language.strings_count * 100));
                } else {
                    str = LocaleController.formatString("LanguageCustomAlert", R.string.LanguageCustomAlert, language.name, (int) Math.ceil(language.translated_count / (float) language.strings_count * 100));
                }
                builder.setPositiveButton(LocaleController.getString("Change", R.string.Change), (dialogInterface, i) -> {
                    String key;
                    if (language.official) {
                        key = "remote_" + language.lang_code;
                    } else {
                        key = "unofficial_" + language.lang_code;
                    }
                    LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().getLanguageFromDict(key);
                    if (localeInfo == null) {
                        localeInfo = new LocaleController.LocaleInfo();
                        localeInfo.name = language.native_name;
                        localeInfo.nameEnglish = language.name;
                        localeInfo.shortName = language.lang_code;
                        localeInfo.baseLangCode = language.base_lang_code;
                        localeInfo.pluralLangCode = language.plural_code;
                        localeInfo.isRtl = language.rtl;
                        if (language.official) {
                            localeInfo.pathToFile = "remote";
                        } else {
                            localeInfo.pathToFile = "unofficial";
                        }
                    }
                    LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, UserConfig.selectedAccount);
                    activity.rebuildAllFragments(true);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            }
        }

        spanned = new SpannableStringBuilder(AndroidUtilities.replaceTags(str));

        int start = TextUtils.indexOf(spanned, '[');
        int end;
        if (start != -1) {
            end = TextUtils.indexOf(spanned, ']', start + 1);
            if (start != -1 && end != -1) {
                spanned.delete(end, end + 1);
                spanned.delete(start, start + 1);
            }
        } else {
            end = -1;
        }

        if (start != -1 && end != -1) {
            spanned.setSpan(new URLSpanNoUnderline(language.translations_url) {
                @Override
                public void onClick(View widget) {
                    builder.getDismissRunnable().run();
                    super.onClick(widget);
                }
            }, start, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        final TextView message = new TextView(activity);
        message.setText(spanned);
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        message.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        message.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection));
        message.setPadding(AndroidUtilities.dp(23), 0, AndroidUtilities.dp(23), 0);
        message.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        builder.setView(message);

        return builder;
    }

    public static boolean checkSlowMode(Context context, int currentAccount, long did, boolean few) {
        int lowerId = (int) did;
        if (lowerId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
            if (chat != null && chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
                if (!few) {
                    TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
                    if (chatFull == null) {
                        chatFull = MessagesStorage.getInstance(currentAccount).loadChatInfo(chat.id, new CountDownLatch(1), false, false);
                    }
                    if (chatFull != null && chatFull.slowmode_next_send_date >= ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                        few = true;
                    }
                }
                if (few) {
                    AlertsCreator.createSimpleAlert(context, chat.title, LocaleController.getString("SlowmodeSendError", R.string.SlowmodeSendError)).show();
                    return true;
                }
            }
        }
        return false;
    }

    public static AlertDialog.Builder createSimpleAlert(Context context, final String text) {
        return createSimpleAlert(context, null, text);
    }

    public static AlertDialog.Builder createSimpleAlert(Context context, final String title, final String text) {
        if (text == null) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title == null ? LocaleController.getString("AppName", R.string.AppName) : title);
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        return builder;
    }

    public static Dialog showSimpleAlert(BaseFragment baseFragment, final String text) {
        return showSimpleAlert(baseFragment, null, text);
    }

    public static Dialog showSimpleAlert(BaseFragment baseFragment, final String title, final String text) {
        if (text == null || baseFragment == null || baseFragment.getParentActivity() == null) {
            return null;
        }
        AlertDialog.Builder builder = createSimpleAlert(baseFragment.getParentActivity(), title, text);
        Dialog dialog = builder.create();
        baseFragment.showDialog(dialog);
        return dialog;
    }

    public static void showBlockReportSpamAlert(BaseFragment fragment, long dialog_id, TLRPC.User currentUser, TLRPC.Chat currentChat, TLRPC.EncryptedChat encryptedChat, boolean isLocation, TLRPC.ChatFull chatInfo, MessagesStorage.IntCallback callback) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AccountInstance accountInstance = fragment.getAccountInstance();
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        CharSequence reportText;
        CheckBoxCell[] cells;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(fragment.getCurrentAccount());
        boolean showReport = preferences.getBoolean("dialog_bar_report" + dialog_id, false);
        if (currentUser != null) {
            builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, UserObject.getFirstName(currentUser)));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserAlert", R.string.BlockUserAlert, UserObject.getFirstName(currentUser))));
            reportText = LocaleController.getString("BlockContact", R.string.BlockContact);

            cells = new CheckBoxCell[2];
            LinearLayout linearLayout = new LinearLayout(fragment.getParentActivity());
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            for (int a = 0; a < 2; a++) {
                if (a == 0 && !showReport) {
                    continue;
                }
                cells[a] = new CheckBoxCell(fragment.getParentActivity(), 1);
                cells[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cells[a].setTag(a);
                if (a == 0) {
                    cells[a].setText(LocaleController.getString("DeleteReportSpam", R.string.DeleteReportSpam), "", true, false);
                } else if (a == 1) {
                    cells[a].setText(LocaleController.formatString("DeleteThisChat", R.string.DeleteThisChat), "", true, false);
                }
                cells[a].setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                cells[a].setOnClickListener(v -> {
                    Integer num = (Integer) v.getTag();
                    cells[num].setChecked(!cells[num].isChecked(), true);
                });
            }
            builder.setCustomViewOffset(12);
            builder.setView(linearLayout);
        } else {
            cells = null;
            if (currentChat != null && isLocation) {
                builder.setTitle(LocaleController.getString("ReportUnrelatedGroup", R.string.ReportUnrelatedGroup));
                if (chatInfo != null && chatInfo.location instanceof TLRPC.TL_channelLocation) {
                    TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatInfo.location;
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ReportUnrelatedGroupText", R.string.ReportUnrelatedGroupText, location.address)));
                } else {
                    builder.setMessage(LocaleController.getString("ReportUnrelatedGroupTextNoAddress", R.string.ReportUnrelatedGroupTextNoAddress));
                }
            } else {
                builder.setTitle(LocaleController.getString("ReportSpamTitle", R.string.ReportSpamTitle));
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    builder.setMessage(LocaleController.getString("ReportSpamAlertChannel", R.string.ReportSpamAlertChannel));
                } else {
                    builder.setMessage(LocaleController.getString("ReportSpamAlertGroup", R.string.ReportSpamAlertGroup));
                }
            }
            reportText = LocaleController.getString("ReportChat", R.string.ReportChat);
        }
        builder.setPositiveButton(reportText, (dialogInterface, i) -> {
            if (currentUser != null) {
                accountInstance.getMessagesController().blockUser(currentUser.id);
            }
            if (cells == null || cells[0] != null && cells[0].isChecked()) {
                accountInstance.getMessagesController().reportSpam(dialog_id, currentUser, currentChat, encryptedChat, currentChat != null && isLocation);
            }
            if (cells == null || cells[1].isChecked()) {
                if (currentChat != null) {
                    if (ChatObject.isNotInChat(currentChat)) {
                        accountInstance.getMessagesController().deleteDialog(dialog_id, 0);
                    } else {
                        accountInstance.getMessagesController().deleteUserFromChat((int) -dialog_id, accountInstance.getMessagesController().getUser(accountInstance.getUserConfig().getClientUserId()), null);
                    }
                } else {
                    accountInstance.getMessagesController().deleteDialog(dialog_id, 0);
                }
                callback.run(1);
            } else {
                callback.run(0);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    public static void showCustomNotificationsDialog(BaseFragment parentFragment, long did, int globalType, ArrayList<NotificationsSettingsActivity.NotificationException> exceptions, int currentAccount, MessagesStorage.IntCallback callback) {
        showCustomNotificationsDialog(parentFragment, did, globalType, exceptions, currentAccount, callback, null);
    }

    public static void showCustomNotificationsDialog(BaseFragment parentFragment, long did, int globalType, ArrayList<NotificationsSettingsActivity.NotificationException> exceptions, int currentAccount, MessagesStorage.IntCallback callback, MessagesStorage.IntCallback resultCallback) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        boolean enabled;
        boolean defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(did);

        String[] descriptions = new String[]{
                LocaleController.getString("NotificationsTurnOn", R.string.NotificationsTurnOn),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)),
                did == 0 && parentFragment instanceof NotificationsCustomSettingsActivity ? null : LocaleController.getString("NotificationsCustomize", R.string.NotificationsCustomize),
                LocaleController.getString("NotificationsTurnOff", R.string.NotificationsTurnOff)
        };

        int[] icons = new int[]{
                R.drawable.notifications_on,
                R.drawable.notifications_mute1h,
                R.drawable.notifications_mute2d,
                R.drawable.notifications_settings,
                R.drawable.notifications_off
        };

        final LinearLayout linearLayout = new LinearLayout(parentFragment.getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());

        for (int a = 0; a < descriptions.length; a++) {
            if (descriptions[a] == null) {
                continue;
            }
            TextView textView = new TextView(parentFragment.getParentActivity());
            Drawable drawable = parentFragment.getParentActivity().getResources().getDrawable(icons[a]);
            if (a == descriptions.length - 1) {
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogRedIcon), PorterDuff.Mode.MULTIPLY));
            } else {
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            textView.setTag(a);
            textView.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            textView.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            textView.setCompoundDrawablePadding(AndroidUtilities.dp(26));
            textView.setText(descriptions[a]);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            textView.setOnClickListener(v -> {
                int i = (Integer) v.getTag();
                if (i == 0) {
                    if (did != 0) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        SharedPreferences.Editor editor = preferences.edit();
                        if (defaultEnabled) {
                            editor.remove("notify2_" + did);
                        } else {
                            editor.putInt("notify2_" + did, 0);
                        }
                        MessagesStorage.getInstance(currentAccount).setDialogFlags(did, 0);
                        editor.commit();
                        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(did);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                        }
                        NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did);
                        if (resultCallback != null) {
                            if (defaultEnabled) {
                                resultCallback.run(0);
                            } else {
                                resultCallback.run(1);
                            }
                        }
                    } else {
                        NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(globalType, 0);
                    }
                } else if (i == 3) {
                    if (did != 0) {
                        Bundle args = new Bundle();
                        args.putLong("dialog_id", did);
                        parentFragment.presentFragment(new ProfileNotificationsActivity(args));
                    } else {
                        parentFragment.presentFragment(new NotificationsCustomSettingsActivity(globalType, exceptions));
                    }
                } else {
                    int untilTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                    if (i == 1) {
                        untilTime += 60 * 60;
                    } else if (i == 2) {
                        untilTime += 60 * 60 * 48;
                    } else if (i == 4) {
                        untilTime = Integer.MAX_VALUE;
                    }

                    if (did != 0) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        SharedPreferences.Editor editor = preferences.edit();
                        long flags;
                        if (i == 4) {
                            if (!defaultEnabled) {
                                editor.remove("notify2_" + did);
                                flags = 0;
                            } else {
                                editor.putInt("notify2_" + did, 2);
                                flags = 1;
                            }
                        } else {
                            editor.putInt("notify2_" + did, 3);
                            editor.putInt("notifyuntil_" + did, untilTime);
                            flags = ((long) untilTime << 32) | 1;
                        }
                        NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(did);
                        MessagesStorage.getInstance(currentAccount).setDialogFlags(did, flags);
                        editor.commit();
                        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(did);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                            if (i != 4 || defaultEnabled) {
                                dialog.notify_settings.mute_until = untilTime;
                            }
                        }
                        NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did);
                        if (resultCallback != null) {
                            if (i == 4 && !defaultEnabled) {
                                resultCallback.run(0);
                            } else {
                                resultCallback.run(1);
                            }
                        }
                    } else {
                        if (i == 4) {
                            NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(globalType, Integer.MAX_VALUE);
                        } else {
                            NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(globalType, untilTime);
                        }
                    }
                }
                if (callback != null) {
                    callback.run(i);
                }
                builder.getDismissRunnable().run();
            });
        }
        builder.setTitle(LocaleController.getString("Notifications", R.string.Notifications));
        builder.setView(linearLayout);
        parentFragment.showDialog(builder.create());
    }

    public static AlertDialog showSecretLocationAlert(Context context, int currentAccount, final Runnable onSelectRunnable, boolean inChat) {
        ArrayList<String> arrayList = new ArrayList<>();
        int providers = MessagesController.getInstance(currentAccount).availableMapProviders;
        if ((providers & 1) != 0) {
            arrayList.add(LocaleController.getString("MapPreviewProviderTelegram", R.string.MapPreviewProviderTelegram));
        }
        if ((providers & 2) != 0) {
            arrayList.add(LocaleController.getString("MapPreviewProviderGoogle", R.string.MapPreviewProviderGoogle));
        }
        if ((providers & 4) != 0) {
            arrayList.add(LocaleController.getString("MapPreviewProviderYandex", R.string.MapPreviewProviderYandex));
        }
        arrayList.add(LocaleController.getString("MapPreviewProviderNobody", R.string.MapPreviewProviderNobody));
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString("ChooseMapPreviewProvider", R.string.ChooseMapPreviewProvider))
                .setItems(arrayList.toArray(new String[0]), (dialog, which) -> {
                    SharedConfig.setSecretMapPreviewType(which);
                    if (onSelectRunnable != null) {
                        onSelectRunnable.run();
                    }
                });
        if (!inChat) {
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        }
        AlertDialog dialog = builder.show();
        if (inChat) {
            dialog.setCanceledOnTouchOutside(false);
        }
        return dialog;
    }

    private static void updateDayPicker(NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker yearPicker) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MONTH, monthPicker.getValue());
        calendar.set(Calendar.YEAR, yearPicker.getValue());
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
    }

    private static void checkPickerDate(NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker yearPicker) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH);
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        if (currentYear > yearPicker.getValue()) {
            yearPicker.setValue(currentYear);
            //yearPicker.finishScroll();
        }
        if (yearPicker.getValue() == currentYear) {
            if (currentMonth > monthPicker.getValue()) {
                monthPicker.setValue(currentMonth);
                //monthPicker.finishScroll();
            }
            if (currentMonth == monthPicker.getValue()) {
                if (currentDay > dayPicker.getValue()) {
                    dayPicker.setValue(currentDay);
                    //dayPicker.finishScroll();
                }
            }
        }
    }

    public static AlertDialog createSupportAlert(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }
        final TextView message = new TextView(fragment.getParentActivity());
        Spannable spanned = new SpannableString(Html.fromHtml(LocaleController.getString("AskAQuestionInfo", R.string.AskAQuestionInfo).replace("\n", "<br>")));
        URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
        for (int i = 0; i < spans.length; i++) {
            URLSpan span = spans[i];
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            spanned.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL()) {
                @Override
                public void onClick(View widget) {
                    fragment.dismissCurrentDialig();
                    super.onClick(widget);
                }
            };
            spanned.setSpan(span, start, end, 0);
        }
        message.setText(spanned);
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        message.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        message.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection));
        message.setPadding(AndroidUtilities.dp(23), 0, AndroidUtilities.dp(23), 0);
        message.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));

        AlertDialog.Builder builder1 = new AlertDialog.Builder(fragment.getParentActivity());
        builder1.setView(message);
        builder1.setTitle(LocaleController.getString("AskAQuestion", R.string.AskAQuestion));
        builder1.setPositiveButton(LocaleController.getString("AskButton", R.string.AskButton), (dialogInterface, i) -> performAskAQuestion(fragment));
        builder1.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder1.create();
    }

    private static void performAskAQuestion(BaseFragment fragment) {
        int currentAccount = fragment.getCurrentAccount();
        final SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        int uid = preferences.getInt("support_id", 0);
        TLRPC.User supportUser = null;
        if (uid != 0) {
            supportUser = MessagesController.getInstance(currentAccount).getUser(uid);
            if (supportUser == null) {
                String userString = preferences.getString("support_user", null);
                if (userString != null) {
                    try {
                        byte[] datacentersBytes = Base64.decode(userString, Base64.DEFAULT);
                        if (datacentersBytes != null) {
                            SerializedData data = new SerializedData(datacentersBytes);
                            supportUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                            if (supportUser != null && supportUser.id == 333000) {
                                supportUser = null;
                            }
                            data.cleanup();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        supportUser = null;
                    }
                }
            }
        }
        if (supportUser == null) {
            final AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.show();
            TLRPC.TL_help_getSupport req = new TLRPC.TL_help_getSupport();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    final TLRPC.TL_help_support res = (TLRPC.TL_help_support) response;
                    AndroidUtilities.runOnUIThread(() -> {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("support_id", res.user.id);
                        SerializedData data = new SerializedData();
                        res.user.serializeToStream(data);
                        editor.putString("support_user", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                        editor.commit();
                        data.cleanup();
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(res.user);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true);
                        MessagesController.getInstance(currentAccount).putUser(res.user, false);
                        Bundle args = new Bundle();
                        args.putInt("user_id", res.user.id);
                        fragment.presentFragment(new ChatActivity(args));
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }
            });
        } else {
            MessagesController.getInstance(currentAccount).putUser(supportUser, true);
            Bundle args = new Bundle();
            args.putInt("user_id", supportUser.id);
            fragment.presentFragment(new ChatActivity(args));
        }
    }

    public static void createClearOrDeleteDialogAlert(BaseFragment fragment, boolean clear, TLRPC.Chat chat, TLRPC.User user, boolean secret, MessagesStorage.BooleanCallback onProcessRunnable) {
        createClearOrDeleteDialogAlert(fragment, clear, false, false, chat, user, secret, onProcessRunnable);
    }

    public static void createClearOrDeleteDialogAlert(BaseFragment fragment, boolean clear, boolean admin, boolean second, TLRPC.Chat chat, TLRPC.User user, boolean secret, MessagesStorage.BooleanCallback onProcessRunnable) {
        if (fragment == null || fragment.getParentActivity() == null || chat == null && user == null) {
            return;
        }
        int account = fragment.getCurrentAccount();

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        int selfUserId = UserConfig.getInstance(account).getClientUserId();

        CheckBoxCell[] cell = new CheckBoxCell[1];

        TextView messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        boolean clearingCache = ChatObject.isChannel(chat) && !TextUtils.isEmpty(chat.username);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell[0] != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell[0].getMeasuredHeight() + AndroidUtilities.dp(7));
                }
            }
        };
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        if (clear) {
            if (clearingCache) {
                textView.setText(LocaleController.getString("ClearHistoryCache", R.string.ClearHistoryCache));
            } else {
                textView.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory));
            }
        } else {
            if (admin) {
                if (ChatObject.isChannel(chat)) {
                    if (chat.megagroup) {
                        textView.setText(LocaleController.getString("DeleteMegaMenu", R.string.DeleteMegaMenu));
                    } else {
                        textView.setText(LocaleController.getString("ChannelDeleteMenu", R.string.ChannelDeleteMenu));
                    }
                } else {
                    textView.setText(LocaleController.getString("DeleteMegaMenu", R.string.DeleteMegaMenu));
                }
            } else {
                textView.setText(LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser));
            }
        }
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        boolean canRevokeInbox = user != null && !user.bot && user.id != selfUserId && MessagesController.getInstance(account).canRevokePmInbox;
        int revokeTimeLimit;
        if (user != null) {
            revokeTimeLimit = MessagesController.getInstance(account).revokeTimePmLimit;
        } else {
            revokeTimeLimit = MessagesController.getInstance(account).revokeTimeLimit;
        }
        boolean canDeleteInbox = !secret && user != null && canRevokeInbox && revokeTimeLimit == 0x7fffffff;
        final boolean[] deleteForAll = new boolean[1];

        if (!second && canDeleteInbox) {
            cell[0] = new CheckBoxCell(context, 1);
            cell[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (clear) {
                cell[0].setText(LocaleController.formatString("ClearHistoryOptionAlso", R.string.ClearHistoryOptionAlso, UserObject.getFirstName(user)), "", false, false);
            } else {
                cell[0].setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
            }
            cell[0].setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell[0].setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                deleteForAll[0] = !deleteForAll[0];
                cell1.setChecked(deleteForAll[0], true);
            });
        }

        if (user != null) {
            if (user.id == selfUserId) {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED_SMALL);
                imageView.setImage(null, null, avatarDrawable, user);
            } else {
                avatarDrawable.setInfo(user);
                imageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);
            }
        } else if (chat != null) {
            avatarDrawable.setInfo(chat);
            imageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable, chat);
        }

        if (second) {
            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("DeleteAllMessagesAlert", R.string.DeleteAllMessagesAlert)));
        } else {
            if (clear) {
                if (user != null) {
                    if (secret) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithSecretUser", R.string.AreYouSureClearHistoryWithSecretUser, UserObject.getUserName(user))));
                    } else {
                        if (user.id == selfUserId) {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("AreYouSureClearHistorySavedMessages", R.string.AreYouSureClearHistorySavedMessages)));
                        } else {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithUser", R.string.AreYouSureClearHistoryWithUser, UserObject.getUserName(user))));
                        }
                    }
                } else if (chat != null) {
                    if (!ChatObject.isChannel(chat) || chat.megagroup && TextUtils.isEmpty(chat.username)) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChat", R.string.AreYouSureClearHistoryWithChat, chat.title)));
                    } else if (chat.megagroup) {
                        messageTextView.setText(LocaleController.getString("AreYouSureClearHistoryGroup", R.string.AreYouSureClearHistoryGroup));
                    } else {
                        messageTextView.setText(LocaleController.getString("AreYouSureClearHistoryChannel", R.string.AreYouSureClearHistoryChannel));
                    }
                }
            } else {
                if (admin) {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            messageTextView.setText(LocaleController.getString("MegaDeleteAlert", R.string.MegaDeleteAlert));
                        } else {
                            messageTextView.setText(LocaleController.getString("ChannelDeleteAlert", R.string.ChannelDeleteAlert));
                        }
                    } else {
                        messageTextView.setText(LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
                    }
                } else {
                    if (user != null) {
                        if (secret) {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithSecretUser", R.string.AreYouSureDeleteThisChatWithSecretUser, UserObject.getUserName(user))));
                        } else {
                            if (user.id == selfUserId) {
                                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("AreYouSureDeleteThisChatSavedMessages", R.string.AreYouSureDeleteThisChatSavedMessages)));
                            } else {
                                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithUser", R.string.AreYouSureDeleteThisChatWithUser, UserObject.getUserName(user))));
                            }
                        }
                    } else if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("MegaLeaveAlertWithName", R.string.MegaLeaveAlertWithName, chat.title)));
                        } else {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelLeaveAlertWithName", R.string.ChannelLeaveAlertWithName, chat.title)));
                        }
                    } else {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteAndExitName", R.string.AreYouSureDeleteAndExitName, chat.title)));
                    }
                }
            }
        }

        String actionText;
        if (second) {
            actionText = LocaleController.getString("DeleteAll", R.string.DeleteAll);
        } else {
            if (clear) {
                if (clearingCache) {
                    actionText = LocaleController.getString("ClearHistoryCache", R.string.ClearHistoryCache);
                } else {
                    actionText = LocaleController.getString("ClearHistory", R.string.ClearHistory);
                }
            } else {
                if (admin) {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            actionText = LocaleController.getString("DeleteMega", R.string.DeleteMega);
                        } else {
                            actionText = LocaleController.getString("ChannelDelete", R.string.ChannelDelete);
                        }
                    } else {
                        actionText = LocaleController.getString("DeleteMega", R.string.DeleteMega);
                    }
                } else {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            actionText = LocaleController.getString("LeaveMegaMenu", R.string.LeaveMegaMenu);
                        } else {
                            actionText = LocaleController.getString("LeaveChannelMenu", R.string.LeaveChannelMenu);
                        }
                    } else {
                        actionText = LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser);
                    }
                }
            }
        }
        builder.setPositiveButton(actionText, (dialogInterface, i) -> {
            if (user != null && !clearingCache && !second && deleteForAll[0]) {
                MessagesStorage.getInstance(fragment.getCurrentAccount()).getMessagesCount(user.id, (count) -> {
                    if (count >= 50) {
                        createClearOrDeleteDialogAlert(fragment, clear, admin, true, chat, user, secret, onProcessRunnable);
                    } else {
                        if (onProcessRunnable != null) {
                            onProcessRunnable.run(deleteForAll[0]);
                        }
                    }
                });
                return;
            }
            if (onProcessRunnable != null) {
                onProcessRunnable.run(second || deleteForAll[0]);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    public interface DatePickerDelegate {
        void didSelectDate(int year, int month, int dayOfMonth);
    }

    public static AlertDialog.Builder createDatePickerDialog(Context context, int minYear, int maxYear, int currentYearDiff, int selectedDay, int selectedMonth, int selectedYear, String title, final boolean checkMinDate, final DatePickerDelegate datePickerDelegate) {
        if (context == null) {
            return null;
        }

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);

        final NumberPicker monthPicker = new NumberPicker(context);
        final NumberPicker dayPicker = new NumberPicker(context);
        final NumberPicker yearPicker = new NumberPicker(context);

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.3f));
        dayPicker.setOnScrollListener((view, scrollState) -> {
            if (checkMinDate && scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                checkPickerDate(dayPicker, monthPicker, yearPicker);
            }
        });

        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        linearLayout.addView(monthPicker, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.3f));
        monthPicker.setFormatter(value -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.MONTH, value);
            return calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
        });
        monthPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateDayPicker(dayPicker, monthPicker, yearPicker));
        monthPicker.setOnScrollListener((view, scrollState) -> {
            if (checkMinDate && scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                checkPickerDate(dayPicker, monthPicker, yearPicker);
            }
        });

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        final int currentYear = calendar.get(Calendar.YEAR);
        yearPicker.setMinValue(currentYear + minYear);
        yearPicker.setMaxValue(currentYear + maxYear);
        yearPicker.setValue(currentYear + currentYearDiff);
        linearLayout.addView(yearPicker, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.4f));
        yearPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateDayPicker(dayPicker, monthPicker, yearPicker));
        yearPicker.setOnScrollListener((view, scrollState) -> {
            if (checkMinDate && scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                checkPickerDate(dayPicker, monthPicker, yearPicker);
            }
        });
        updateDayPicker(dayPicker, monthPicker, yearPicker);
        if (checkMinDate) {
            checkPickerDate(dayPicker, monthPicker, yearPicker);
        }

        if (selectedDay != -1) {
            dayPicker.setValue(selectedDay);
            monthPicker.setValue(selectedMonth);
            yearPicker.setValue(selectedYear);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Set", R.string.Set), (dialog, which) -> {
            if (checkMinDate) {
                checkPickerDate(dayPicker, monthPicker, yearPicker);
            }
            datePickerDelegate.didSelectDate(yearPicker.getValue(), monthPicker.getValue(), dayPicker.getValue());
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder;
    }

    private static boolean checkScheduleDate(TextView button, boolean reminder, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        int day = dayPicker.getValue();
        int hour = hourPicker.getValue();
        int minute = minutePicker.getValue();

        Calendar calendar = Calendar.getInstance();
        long systemTime = System.currentTimeMillis();
        calendar.setTimeInMillis(systemTime);
        int currentYear = calendar.get(Calendar.YEAR);
        int currentDay = calendar.get(Calendar.DAY_OF_YEAR);

        calendar.setTimeInMillis(System.currentTimeMillis() + (long) day * 24 * 3600 * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        long currentTime = calendar.getTimeInMillis();

        if (currentTime <= systemTime + 60000L) {
            calendar.setTimeInMillis(systemTime + 60000L);
            if (currentDay != calendar.get(Calendar.DAY_OF_YEAR)) {
                dayPicker.setValue(day = 1);
            }
            hourPicker.setValue(hour = calendar.get(Calendar.HOUR_OF_DAY));
            minutePicker.setValue(minute = calendar.get(Calendar.MINUTE));
        }
        int selectedYear = calendar.get(Calendar.YEAR);

        calendar.setTimeInMillis(System.currentTimeMillis() + (long) day * 24 * 3600 * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        if (button != null) {
            long time = calendar.getTimeInMillis();
            int num;
            if (day == 0) {
                num = 0;
            } else if (currentYear == selectedYear) {
                num = 1;
            } else {
                num = 2;
            }
            if (reminder) {
                num += 3;
            }
            button.setText(LocaleController.getInstance().formatterScheduleSend[num].format(time));
        }
        return currentTime - systemTime > 60000L;
    }

    public interface ScheduleDatePickerDelegate {
        void didSelectDate(boolean notify, int scheduleDate);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, boolean reminder, final ScheduleDatePickerDelegate datePickerDelegate) {
        return createScheduleDatePickerDialog(context, reminder, -1, datePickerDelegate, null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, boolean reminder, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable) {
        return createScheduleDatePickerDialog(context, reminder, -1, datePickerDelegate, cancelRunnable);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, boolean reminder, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable) {
        if (context == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, 1);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context);
        dayPicker.setTextOffset(AndroidUtilities.dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context);
        hourPicker.setItemCount(5);
        hourPicker.setTextOffset(-AndroidUtilities.dp(10));
        final NumberPicker minutePicker = new NumberPicker(context);
        minutePicker.setItemCount(5);
        minutePicker.setTextOffset(-AndroidUtilities.dp(34));

        LinearLayout container = new LinearLayout(context) {

            boolean ignoreLayout = false;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                ignoreLayout = true;
                int count;
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    count = 3;
                } else {
                    count = 5;
                }
                dayPicker.setItemCount(count);
                hourPicker.setItemCount(count);
                minutePicker.setItemCount(count);
                dayPicker.getLayoutParams().height = AndroidUtilities.dp(54) * count;
                hourPicker.getLayoutParams().height = AndroidUtilities.dp(54) * count;
                minutePicker.getLayoutParams().height = AndroidUtilities.dp(54) * count;
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        container.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(reminder ? LocaleController.getString("SetReminder", R.string.SetReminder) : LocaleController.getString("ScheduleMessage", R.string.ScheduleMessage));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 12, 22, 4));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);

        TextView buttonTextView = new TextView(context);

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0);
        dayPicker.setMaxValue(365);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return LocaleController.getString("MessageScheduleToday", R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                if (year == currentYear) {
                    return LocaleController.getInstance().formatterScheduleDay.format(date);
                } else {
                    return LocaleController.getInstance().formatterScheduleYear.format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> checkScheduleDate(buttonTextView, reminder, dayPicker, hourPicker, minutePicker);
        dayPicker.setOnValueChangedListener(onValueChangeListener);

        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f));
        hourPicker.setFormatter(value -> String.format("%02d", value));
        hourPicker.setOnValueChangedListener(onValueChangeListener);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(0);
        minutePicker.setFormatter(value -> String.format("%02d", value));
        linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f));
        minutePicker.setOnValueChangedListener(onValueChangeListener);

        if (currentDate > 0) {
            currentDate *= 1000;
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            int days = (int) ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
            calendar.setTimeInMillis(currentDate);
            if (days >= 0) {
                minutePicker.setValue(calendar.get(Calendar.MINUTE));
                hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
                dayPicker.setValue(days);
            }
        }
        final boolean[] canceled = {true};

        checkScheduleDate(buttonTextView, reminder, dayPicker, hourPicker, minutePicker);

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            canceled[0] = false;
            boolean setSeconds = checkScheduleDate(null, reminder, dayPicker, hourPicker, minutePicker);
            calendar.setTimeInMillis(System.currentTimeMillis() + (long) dayPicker.getValue() * 24 * 3600 * 1000);
            calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            calendar.set(Calendar.MINUTE, minutePicker.getValue());
            if (setSeconds) {
                calendar.set(Calendar.SECOND, 0);
            }
            datePickerDelegate.didSelectDate(true, (int) (calendar.getTimeInMillis() / 1000));
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        builder.show().setOnDismissListener(dialog -> {
            if (cancelRunnable != null && canceled[0]) {
                cancelRunnable.run();
            }
        });
        return builder;
    }

    public static Dialog createMuteAlert(Context context, final long dialog_id) {
        if (context == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setTitle(LocaleController.getString("Notifications", R.string.Notifications));
        CharSequence[] items = new CharSequence[]{
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 8)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)),
                LocaleController.getString("MuteDisable", R.string.MuteDisable)
        };
        builder.setItems(items, (dialogInterface, i) -> {
                    int setting;
                    if (i == 0) {
                        setting = NotificationsController.SETTING_MUTE_HOUR;
                    } else if (i == 1) {
                        setting = NotificationsController.SETTING_MUTE_8_HOURS;
                    } else if (i == 2) {
                        setting = NotificationsController.SETTING_MUTE_2_DAYS;
                    } else {
                        setting = NotificationsController.SETTING_MUTE_FOREVER;
                    }
                    NotificationsController.getInstance(UserConfig.selectedAccount).setDialogNotificationsSettings(dialog_id, setting);
                }
        );
        return builder.create();
    }

    public static void createReportAlert(final Context context, final long dialog_id, final int messageId, final BaseFragment parentFragment) {
        if (context == null || parentFragment == null) {
            return;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setTitle(LocaleController.getString("ReportChat", R.string.ReportChat));
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("ReportChatSpam", R.string.ReportChatSpam),
                LocaleController.getString("ReportChatViolence", R.string.ReportChatViolence),
                LocaleController.getString("ReportChatChild", R.string.ReportChatChild),
                LocaleController.getString("ReportChatPornography", R.string.ReportChatPornography),
                LocaleController.getString("ReportChatOther", R.string.ReportChatOther)
        };
        builder.setItems(items, (dialogInterface, i) -> {
                    if (i == 4) {
                        Bundle args = new Bundle();
                        args.putLong("dialog_id", dialog_id);
                        args.putLong("message_id", messageId);
                        parentFragment.presentFragment(new ReportOtherActivity(args));
                        return;
                    }
                    TLObject req;
                    TLRPC.InputPeer peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer((int) dialog_id);
                    if (messageId != 0) {
                        TLRPC.TL_messages_report request = new TLRPC.TL_messages_report();
                        request.peer = peer;
                        request.id.add(messageId);
                        if (i == 0) {
                            request.reason = new TLRPC.TL_inputReportReasonSpam();
                        } else if (i == 1) {
                            request.reason = new TLRPC.TL_inputReportReasonViolence();
                        } else if (i == 2) {
                            request.reason = new TLRPC.TL_inputReportReasonChildAbuse();
                        } else if (i == 3) {
                            request.reason = new TLRPC.TL_inputReportReasonPornography();
                        }
                        req = request;
                    } else {
                        TLRPC.TL_account_reportPeer request = new TLRPC.TL_account_reportPeer();
                        request.peer = peer;
                        if (i == 0) {
                            request.reason = new TLRPC.TL_inputReportReasonSpam();
                        } else if (i == 1) {
                            request.reason = new TLRPC.TL_inputReportReasonViolence();
                        } else if (i == 2) {
                            request.reason = new TLRPC.TL_inputReportReasonChildAbuse();
                        } else if (i == 3) {
                            request.reason = new TLRPC.TL_inputReportReasonPornography();
                        }
                        req = request;
                    }
                    ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {

                    });
                    Toast.makeText(context, LocaleController.getString("ReportChatSent", R.string.ReportChatSent), Toast.LENGTH_SHORT).show();
                }
        );
        BottomSheet sheet = builder.create();
        parentFragment.showDialog(sheet);
    }

    private static String getFloodWaitString(String error) {
        int time = Utilities.parseInt(error);
        String timeString;
        if (time < 60) {
            timeString = LocaleController.formatPluralString("Seconds", time);
        } else {
            timeString = LocaleController.formatPluralString("Minutes", time / 60);
        }
        return LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString);
    }

    public static void showFloodWaitAlert(String error, final BaseFragment fragment) {
        if (error == null || !error.startsWith("FLOOD_WAIT") || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        int time = Utilities.parseInt(error);
        String timeString;
        if (time < 60) {
            timeString = LocaleController.formatPluralString("Seconds", time);
        } else {
            timeString = LocaleController.formatPluralString("Minutes", time / 60);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static void showSendMediaAlert(int result, final BaseFragment fragment) {
        if (result == 0) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        if (result == 1) {
            builder.setMessage(LocaleController.getString("ErrorSendRestrictedStickers", R.string.ErrorSendRestrictedStickers));
        } else if (result == 2) {
            builder.setMessage(LocaleController.getString("ErrorSendRestrictedMedia", R.string.ErrorSendRestrictedMedia));
        } else if (result == 3) {
            builder.setMessage(LocaleController.getString("ErrorSendRestrictedPolls", R.string.ErrorSendRestrictedPolls));
        } else if (result == 4) {
            builder.setMessage(LocaleController.getString("ErrorSendRestrictedStickersAll", R.string.ErrorSendRestrictedStickersAll));
        } else if (result == 5) {
            builder.setMessage(LocaleController.getString("ErrorSendRestrictedMediaAll", R.string.ErrorSendRestrictedMediaAll));
        } else if (result == 6) {
            builder.setMessage(LocaleController.getString("ErrorSendRestrictedPollsAll", R.string.ErrorSendRestrictedPollsAll));
        }

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static void showAddUserAlert(String error, final BaseFragment fragment, boolean isChannel, TLObject request) {
        if (error == null || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        switch (error) {
            case "PEER_FLOOD":
                builder.setMessage(LocaleController.getString("NobodyLikesSpam2", R.string.NobodyLikesSpam2));
                builder.setNegativeButton(LocaleController.getString("MoreInfo", R.string.MoreInfo), (dialogInterface, i) -> MessagesController.getInstance(fragment.getCurrentAccount()).openByUserName("spambot", fragment, 1));
                break;
            case "USER_BLOCKED":
            case "USER_BOT":
            case "USER_ID_INVALID":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString("ChannelUserCantAdd", R.string.ChannelUserCantAdd));
                } else {
                    builder.setMessage(LocaleController.getString("GroupUserCantAdd", R.string.GroupUserCantAdd));
                }
                break;
            case "USERS_TOO_MUCH":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString("ChannelUserAddLimit", R.string.ChannelUserAddLimit));
                } else {
                    builder.setMessage(LocaleController.getString("GroupUserAddLimit", R.string.GroupUserAddLimit));
                }
                break;
            case "USER_NOT_MUTUAL_CONTACT":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString("ChannelUserLeftError", R.string.ChannelUserLeftError));
                } else {
                    builder.setMessage(LocaleController.getString("GroupUserLeftError", R.string.GroupUserLeftError));
                }
                break;
            case "ADMINS_TOO_MUCH":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString("ChannelUserCantAdmin", R.string.ChannelUserCantAdmin));
                } else {
                    builder.setMessage(LocaleController.getString("GroupUserCantAdmin", R.string.GroupUserCantAdmin));
                }
                break;
            case "BOTS_TOO_MUCH":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString("ChannelUserCantBot", R.string.ChannelUserCantBot));
                } else {
                    builder.setMessage(LocaleController.getString("GroupUserCantBot", R.string.GroupUserCantBot));
                }
                break;
            case "USER_PRIVACY_RESTRICTED":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString("InviteToChannelError", R.string.InviteToChannelError));
                } else {
                    builder.setMessage(LocaleController.getString("InviteToGroupError", R.string.InviteToGroupError));
                }
                break;
            case "USERS_TOO_FEW":
                builder.setMessage(LocaleController.getString("CreateGroupError", R.string.CreateGroupError));
                break;
            case "USER_RESTRICTED":
                builder.setMessage(LocaleController.getString("UserRestricted", R.string.UserRestricted));
                break;
            case "YOU_BLOCKED_USER":
                builder.setMessage(LocaleController.getString("YouBlockedUser", R.string.YouBlockedUser));
                break;
            case "CHAT_ADMIN_BAN_REQUIRED":
            case "USER_KICKED":
                builder.setMessage(LocaleController.getString("AddAdminErrorBlacklisted", R.string.AddAdminErrorBlacklisted));
                break;
            case "CHAT_ADMIN_INVITE_REQUIRED":
                builder.setMessage(LocaleController.getString("AddAdminErrorNotAMember", R.string.AddAdminErrorNotAMember));
                break;
            case "USER_ADMIN_INVALID":
                builder.setMessage(LocaleController.getString("AddBannedErrorAdmin", R.string.AddBannedErrorAdmin));
                break;
            case "CHANNELS_ADMIN_PUBLIC_TOO_MUCH":
                builder.setMessage(LocaleController.getString("PublicChannelsTooMuch", R.string.PublicChannelsTooMuch));
                break;
            case "CHANNELS_ADMIN_LOCATED_TOO_MUCH":
                builder.setMessage(LocaleController.getString("LocatedChannelsTooMuch", R.string.LocatedChannelsTooMuch));
                break;
            case "CHANNELS_TOO_MUCH":
                if (request instanceof TLRPC.TL_channels_createChannel) {
                    builder.setMessage(LocaleController.getString("ChannelTooMuch", R.string.ChannelTooMuch));
                } else {
                    builder.setMessage(LocaleController.getString("ChannelTooMuchJoin", R.string.ChannelTooMuchJoin));
                }
                break;
            default:
                builder.setMessage(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error);
                break;
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static Dialog createColorSelectDialog(Activity parentActivity, final long dialog_id, final int globalType, final Runnable onSelect) {
        int currentColor;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        if (dialog_id != 0) {
            if (preferences.contains("color_" + dialog_id)) {
                currentColor = preferences.getInt("color_" + dialog_id, 0xff0000ff);
            } else {
                if ((int) dialog_id < 0) {
                    currentColor = preferences.getInt("GroupLed", 0xff0000ff);
                } else {
                    currentColor = preferences.getInt("MessagesLed", 0xff0000ff);
                }
            }
        } else if (globalType == NotificationsController.TYPE_PRIVATE) {
            currentColor = preferences.getInt("MessagesLed", 0xff0000ff);
        } else if (globalType == NotificationsController.TYPE_GROUP) {
            currentColor = preferences.getInt("GroupLed", 0xff0000ff);
        } else {
            currentColor = preferences.getInt("ChannelLed", 0xff0000ff);
        }
        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        String[] descriptions = new String[]{LocaleController.getString("ColorRed", R.string.ColorRed),
                LocaleController.getString("ColorOrange", R.string.ColorOrange),
                LocaleController.getString("ColorYellow", R.string.ColorYellow),
                LocaleController.getString("ColorGreen", R.string.ColorGreen),
                LocaleController.getString("ColorCyan", R.string.ColorCyan),
                LocaleController.getString("ColorBlue", R.string.ColorBlue),
                LocaleController.getString("ColorViolet", R.string.ColorViolet),
                LocaleController.getString("ColorPink", R.string.ColorPink),
                LocaleController.getString("ColorWhite", R.string.ColorWhite)};
        final int[] selectedColor = new int[]{currentColor};
        for (int a = 0; a < 9; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(TextColorCell.colors[a], TextColorCell.colors[a]);
            cell.setTextAndValue(descriptions[a], currentColor == TextColorCell.colorsToSave[a]);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                int count = linearLayout.getChildCount();
                for (int a1 = 0; a1 < count; a1++) {
                    RadioColorCell cell1 = (RadioColorCell) linearLayout.getChildAt(a1);
                    cell1.setChecked(cell1 == v, true);
                }
                selectedColor[0] = TextColorCell.colorsToSave[(Integer) v.getTag()];
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("LedColor", R.string.LedColor));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Set", R.string.Set), (dialogInterface, which) -> {
            final SharedPreferences preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
            SharedPreferences.Editor editor = preferences1.edit();
            if (dialog_id != 0) {
                editor.putInt("color_" + dialog_id, selectedColor[0]);
            } else if (globalType == NotificationsController.TYPE_PRIVATE) {
                editor.putInt("MessagesLed", selectedColor[0]);
            } else if (globalType == NotificationsController.TYPE_GROUP) {
                editor.putInt("GroupLed", selectedColor[0]);
            } else {
                editor.putInt("ChannelLed", selectedColor[0]);
            }
            editor.commit();
            if (onSelect != null) {
                onSelect.run();
            }
        });
        builder.setNeutralButton(LocaleController.getString("LedDisabled", R.string.LedDisabled), (dialog, which) -> {
            final SharedPreferences preferences12 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
            SharedPreferences.Editor editor = preferences12.edit();
            if (dialog_id != 0) {
                editor.putInt("color_" + dialog_id, 0);
            } else if (globalType == NotificationsController.TYPE_PRIVATE) {
                editor.putInt("MessagesLed", 0);
            } else if (globalType == NotificationsController.TYPE_GROUP) {
                editor.putInt("GroupLed", 0);
            } else {
                editor.putInt("ChannelLed", 0);
            }
            editor.commit();
            if (onSelect != null) {
                onSelect.run();
            }
        });
        if (dialog_id != 0) {
            builder.setNegativeButton(LocaleController.getString("Default", R.string.Default), (dialog, which) -> {
                final SharedPreferences preferences13 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences13.edit();
                editor.remove("color_" + dialog_id);
                editor.commit();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        return builder.create();
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final long dialog_id, final boolean globalGroup, final boolean globalAll, final Runnable onSelect) {
        String prefix;
        if (dialog_id != 0) {
            prefix = "vibrate_";
        } else {
            prefix = globalGroup ? "vibrate_group" : "vibrate_messages";
        }
        return createVibrationSelectDialog(parentActivity, dialog_id, prefix, onSelect);
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final long dialog_id, final String prefKeyPrefix, final Runnable onSelect) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        final int[] selected = new int[1];
        String[] descriptions;
        if (dialog_id != 0) {
            selected[0] = preferences.getInt(prefKeyPrefix + dialog_id, 0);
            if (selected[0] == 3) {
                selected[0] = 2;
            } else if (selected[0] == 2) {
                selected[0] = 3;
            }
            descriptions = new String[]{
                    LocaleController.getString("VibrationDefault", R.string.VibrationDefault),
                    LocaleController.getString("Short", R.string.Short),
                    LocaleController.getString("Long", R.string.Long),
                    LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled)
            };
        } else {
            selected[0] = preferences.getInt(prefKeyPrefix, 0);
            if (selected[0] == 0) {
                selected[0] = 1;
            } else if (selected[0] == 1) {
                selected[0] = 2;
            } else if (selected[0] == 2) {
                selected[0] = 0;
            }
            descriptions = new String[]{
                    LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled),
                    LocaleController.getString("VibrationDefault", R.string.VibrationDefault),
                    LocaleController.getString("Short", R.string.Short),
                    LocaleController.getString("Long", R.string.Long),
                    LocaleController.getString("OnlyIfSilent", R.string.OnlyIfSilent)
            };
        }

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                selected[0] = (Integer) v.getTag();

                final SharedPreferences preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences1.edit();
                if (dialog_id != 0) {
                    if (selected[0] == 0) {
                        editor.putInt(prefKeyPrefix + dialog_id, 0);
                    } else if (selected[0] == 1) {
                        editor.putInt(prefKeyPrefix + dialog_id, 1);
                    } else if (selected[0] == 2) {
                        editor.putInt(prefKeyPrefix + dialog_id, 3);
                    } else if (selected[0] == 3) {
                        editor.putInt(prefKeyPrefix + dialog_id, 2);
                    }
                } else {
                    if (selected[0] == 0) {
                        editor.putInt(prefKeyPrefix, 2);
                    } else if (selected[0] == 1) {
                        editor.putInt(prefKeyPrefix, 0);
                    } else if (selected[0] == 2) {
                        editor.putInt(prefKeyPrefix, 1);
                    } else if (selected[0] == 3) {
                        editor.putInt(prefKeyPrefix, 3);
                    } else if (selected[0] == 4) {
                        editor.putInt(prefKeyPrefix, 4);
                    }
                }
                editor.commit();
                builder.getDismissRunnable().run();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        builder.setTitle(LocaleController.getString("Vibrate", R.string.Vibrate));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createLocationUpdateDialog(final Activity parentActivity, TLRPC.User user, final MessagesStorage.IntCallback callback) {
        final int[] selected = new int[1];

        String[] descriptions = new String[]{
                LocaleController.getString("SendLiveLocationFor15m", R.string.SendLiveLocationFor15m),
                LocaleController.getString("SendLiveLocationFor1h", R.string.SendLiveLocationFor1h),
                LocaleController.getString("SendLiveLocationFor8h", R.string.SendLiveLocationFor8h),
        };

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleTextView = new TextView(parentActivity);
        if (user != null) {
            titleTextView.setText(LocaleController.formatString("LiveLocationAlertPrivate", R.string.LiveLocationAlertPrivate, UserObject.getFirstName(user)));
        } else {
            titleTextView.setText(LocaleController.getString("LiveLocationAlertGroup", R.string.LiveLocationAlertGroup));
        }
        titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, 8));

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                int num = (Integer) v.getTag();
                selected[0] = num;
                int count = linearLayout.getChildCount();
                for (int a1 = 0; a1 < count; a1++) {
                    View child = linearLayout.getChildAt(a1);
                    if (child instanceof RadioColorCell) {
                        ((RadioColorCell) child).setChecked(child == v, true);
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTopImage(new ShareLocationDrawable(parentActivity, 0), Theme.getColor(Theme.key_dialogTopBackground));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("ShareFile", R.string.ShareFile), (dialog, which) -> {
            int time;
            if (selected[0] == 0) {
                time = 15 * 60;
            } else if (selected[0] == 1) {
                time = 60 * 60;
            } else {
                time = 8 * 60 * 60;
            }
            callback.run(time);
        });
        builder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static AlertDialog.Builder createContactsPermissionDialog(final Activity parentActivity, final MessagesStorage.IntCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTopImage(R.drawable.permissions_contacts, Theme.getColor(Theme.key_dialogTopBackground));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString("ContactsPermissionAlert", R.string.ContactsPermissionAlert)));
        builder.setPositiveButton(LocaleController.getString("ContactsPermissionAlertContinue", R.string.ContactsPermissionAlertContinue), (dialog, which) -> callback.run(1));
        builder.setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), (dialog, which) -> callback.run(0));
        return builder;
    }

    public static Dialog createFreeSpaceDialog(final LaunchActivity parentActivity) {
        final int[] selected = new int[1];

        if (SharedConfig.keepMedia == 2) {
            selected[0] = 3;
        } else if (SharedConfig.keepMedia == 0) {
            selected[0] = 1;
        } else if (SharedConfig.keepMedia == 1) {
            selected[0] = 2;
        } else if (SharedConfig.keepMedia == 3) {
            selected[0] = 0;
        }

        String[] descriptions = new String[]{
                LocaleController.formatPluralString("Days", 3),
                LocaleController.formatPluralString("Weeks", 1),
                LocaleController.formatPluralString("Months", 1),
                LocaleController.getString("LowDiskSpaceNeverRemove", R.string.LowDiskSpaceNeverRemove)
        };

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleTextView = new TextView(parentActivity);
        titleTextView.setText(LocaleController.getString("LowDiskSpaceTitle2", R.string.LowDiskSpaceTitle2));
        titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, 8));

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                int num = (Integer) v.getTag();
                if (num == 0) {
                    selected[0] = 3;
                } else if (num == 1) {
                    selected[0] = 0;
                } else if (num == 2) {
                    selected[0] = 1;
                } else if (num == 3) {
                    selected[0] = 2;
                }
                int count = linearLayout.getChildCount();
                for (int a1 = 0; a1 < count; a1++) {
                    View child = linearLayout.getChildAt(a1);
                    if (child instanceof RadioColorCell) {
                        ((RadioColorCell) child).setChecked(child == v, true);
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("LowDiskSpaceTitle", R.string.LowDiskSpaceTitle));
        builder.setMessage(LocaleController.getString("LowDiskSpaceMessage", R.string.LowDiskSpaceMessage));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> SharedConfig.setKeepMedia(selected[0]));
        builder.setNeutralButton(LocaleController.getString("ClearMediaCache", R.string.ClearMediaCache), (dialog, which) -> parentActivity.presentFragment(new CacheControlActivity()));
        return builder.create();
    }

    public static Dialog createPrioritySelectDialog(Activity parentActivity, final long dialog_id, final int globalType, final Runnable onSelect) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        final int[] selected = new int[1];
        String[] descriptions;
        if (dialog_id != 0) {
            selected[0] = preferences.getInt("priority_" + dialog_id, 3);
            if (selected[0] == 3) {
                selected[0] = 0;
            } else if (selected[0] == 4) {
                selected[0] = 1;
            } else if (selected[0] == 5) {
                selected[0] = 2;
            } else if (selected[0] == 0) {
                selected[0] = 3;
            } else {
                selected[0] = 4;
            }
            descriptions = new String[]{
                    LocaleController.getString("NotificationsPrioritySettings", R.string.NotificationsPrioritySettings),
                    LocaleController.getString("NotificationsPriorityLow", R.string.NotificationsPriorityLow),
                    LocaleController.getString("NotificationsPriorityMedium", R.string.NotificationsPriorityMedium),
                    LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh),
                    LocaleController.getString("NotificationsPriorityUrgent", R.string.NotificationsPriorityUrgent)
            };
        } else {
            if (dialog_id == 0) {
                if (globalType == NotificationsController.TYPE_PRIVATE) {
                    selected[0] = preferences.getInt("priority_messages", 1);
                } else if (globalType == NotificationsController.TYPE_GROUP) {
                    selected[0] = preferences.getInt("priority_group", 1);
                } else if (globalType == NotificationsController.TYPE_CHANNEL) {
                    selected[0] = preferences.getInt("priority_channel", 1);
                }
            }
            if (selected[0] == 4) {
                selected[0] = 0;
            } else if (selected[0] == 5) {
                selected[0] = 1;
            } else if (selected[0] == 0) {
                selected[0] = 2;
            } else {
                selected[0] = 3;
            }
            descriptions = new String[]{
                    LocaleController.getString("NotificationsPriorityLow", R.string.NotificationsPriorityLow),
                    LocaleController.getString("NotificationsPriorityMedium", R.string.NotificationsPriorityMedium),
                    LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh),
                    LocaleController.getString("NotificationsPriorityUrgent", R.string.NotificationsPriorityUrgent)
            };
        }

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                selected[0] = (Integer) v.getTag();

                final SharedPreferences preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences1.edit();
                if (dialog_id != 0) {
                    int option;
                    if (selected[0] == 0) {
                        option = 3;
                    } else if (selected[0] == 1) {
                        option = 4;
                    } else if (selected[0] == 2) {
                        option = 5;
                    } else if (selected[0] == 3) {
                        option = 0;
                    } else {
                        option = 1;
                    }
                    editor.putInt("priority_" + dialog_id, option);
                } else {
                    int option;
                    if (selected[0] == 0) {
                        option = 4;
                    } else if (selected[0] == 1) {
                        option = 5;
                    } else if (selected[0] == 2) {
                        option = 0;
                    } else {
                        option = 1;
                    }
                    if (globalType == NotificationsController.TYPE_PRIVATE) {
                        editor.putInt("priority_messages", option);
                        selected[0] = preferences.getInt("priority_messages", 1);
                    } else if (globalType == NotificationsController.TYPE_GROUP) {
                        editor.putInt("priority_group", option);
                        selected[0] = preferences.getInt("priority_group", 1);
                    } else if (globalType == NotificationsController.TYPE_CHANNEL) {
                        editor.putInt("priority_channel", option);
                        selected[0] = preferences.getInt("priority_channel", 1);
                    }
                }
                editor.commit();
                builder.getDismissRunnable().run();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        builder.setTitle(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createPopupSelectDialog(Activity parentActivity, final int globalType, final Runnable onSelect) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        final int[] selected = new int[1];
        if (globalType == NotificationsController.TYPE_PRIVATE) {
            selected[0] = preferences.getInt("popupAll", 0);
        } else if (globalType == NotificationsController.TYPE_GROUP) {
            selected[0] = preferences.getInt("popupGroup", 0);
        } else {
            selected[0] = preferences.getInt("popupChannel", 0);
        }
        String[] descriptions = new String[]{
                LocaleController.getString("NoPopup", R.string.NoPopup),
                LocaleController.getString("OnlyWhenScreenOn", R.string.OnlyWhenScreenOn),
                LocaleController.getString("OnlyWhenScreenOff", R.string.OnlyWhenScreenOff),
                LocaleController.getString("AlwaysShowPopup", R.string.AlwaysShowPopup)
        };

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setTag(a);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                selected[0] = (Integer) v.getTag();

                final SharedPreferences preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences1.edit();
                if (globalType == NotificationsController.TYPE_PRIVATE) {
                    editor.putInt("popupAll", selected[0]);
                } else if (globalType == NotificationsController.TYPE_GROUP) {
                    editor.putInt("popupGroup", selected[0]);
                } else {
                    editor.putInt("popupChannel", selected[0]);
                }
                editor.commit();
                builder.getDismissRunnable().run();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        builder.setTitle(LocaleController.getString("PopupNotification", R.string.PopupNotification));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createSingleChoiceDialog(Activity parentActivity, final String[] options, final String title, final int selected, final DialogInterface.OnClickListener listener) {
        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        for (int a = 0; a < options.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options[a], selected == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                int sel = (Integer) v.getTag();
                builder.getDismissRunnable().run();
                listener.onClick(null, sel);
            });
        }

        builder.setTitle(title);
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static AlertDialog.Builder createTTLAlert(final Context context, final TLRPC.EncryptedChat encryptedChat) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
        final NumberPicker numberPicker = new NumberPicker(context);
        numberPicker.setMinValue(0);
        numberPicker.setMaxValue(20);
        if (encryptedChat.ttl > 0 && encryptedChat.ttl < 16) {
            numberPicker.setValue(encryptedChat.ttl);
        } else if (encryptedChat.ttl == 30) {
            numberPicker.setValue(16);
        } else if (encryptedChat.ttl == 60) {
            numberPicker.setValue(17);
        } else if (encryptedChat.ttl == 60 * 60) {
            numberPicker.setValue(18);
        } else if (encryptedChat.ttl == 60 * 60 * 24) {
            numberPicker.setValue(19);
        } else if (encryptedChat.ttl == 60 * 60 * 24 * 7) {
            numberPicker.setValue(20);
        } else if (encryptedChat.ttl == 0) {
            numberPicker.setValue(0);
        }
        numberPicker.setFormatter(value -> {
            if (value == 0) {
                return LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever);
            } else if (value >= 1 && value < 16) {
                return LocaleController.formatTTLString(value);
            } else if (value == 16) {
                return LocaleController.formatTTLString(30);
            } else if (value == 17) {
                return LocaleController.formatTTLString(60);
            } else if (value == 18) {
                return LocaleController.formatTTLString(60 * 60);
            } else if (value == 19) {
                return LocaleController.formatTTLString(60 * 60 * 24);
            } else if (value == 20) {
                return LocaleController.formatTTLString(60 * 60 * 24 * 7);
            }
            return "";
        });
        builder.setView(numberPicker);
        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), (dialog, which) -> {
            int oldValue = encryptedChat.ttl;
            which = numberPicker.getValue();
            if (which >= 0 && which < 16) {
                encryptedChat.ttl = which;
            } else if (which == 16) {
                encryptedChat.ttl = 30;
            } else if (which == 17) {
                encryptedChat.ttl = 60;
            } else if (which == 18) {
                encryptedChat.ttl = 60 * 60;
            } else if (which == 19) {
                encryptedChat.ttl = 60 * 60 * 24;
            } else if (which == 20) {
                encryptedChat.ttl = 60 * 60 * 24 * 7;
            }
            if (oldValue != encryptedChat.ttl) {
                SecretChatHelper.getInstance(UserConfig.selectedAccount).sendTTLMessage(encryptedChat, null);
                MessagesStorage.getInstance(UserConfig.selectedAccount).updateEncryptedChatTTL(encryptedChat);
            }
        });
        return builder;
    }

    public interface AccountSelectDelegate {
        void didSelectAccount(int account);
    }

    public static AlertDialog createAccountSelectDialog(Activity parentActivity, final AccountSelectDelegate delegate) {
        if (UserConfig.getActivatedAccountsCount() < 2) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        final Runnable dismissRunnable = builder.getDismissRunnable();
        final AlertDialog[] alertDialog = new AlertDialog[1];

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            TLRPC.User u = UserConfig.getInstance(a).getCurrentUser();
            if (u != null) {
                AccountSelectCell cell = new AccountSelectCell(parentActivity);
                cell.setAccount(a, false);
                cell.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                cell.setOnClickListener(v -> {
                    if (alertDialog[0] != null) {
                        alertDialog[0].setOnDismissListener(null);
                    }
                    dismissRunnable.run();
                    AccountSelectCell cell1 = (AccountSelectCell) v;
                    delegate.didSelectAccount(cell1.getAccountNumber());
                });
            }
        }

        builder.setTitle(LocaleController.getString("SelectAccount", R.string.SelectAccount));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return alertDialog[0] = builder.create();
    }

//    public static AlertDialog createExpireDateAlert(final Context context, final boolean month, final int[] result, final Runnable callback) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(context);
//        builder.setTitle(month ? LocaleController.getString("PaymentCardExpireDateMonth", R.string.PaymentCardExpireDateMonth) : LocaleController.getString("PaymentCardExpireDateYear", R.string.PaymentCardExpireDateYear));
//        final NumberPicker numberPicker = new NumberPicker(context);
//        final int currentYear;
//        if (month) {
//            numberPicker.setMinValue(1);
//            numberPicker.setMaxValue(12);
//            currentYear = 0;
//        } else {
//            Calendar rightNow = Calendar.getInstance();
//            currentYear = rightNow.get(Calendar.YEAR);
//            numberPicker.setMinValue(0);
//            numberPicker.setMaxValue(30);
//        }
//        numberPicker.setFormatter(new NumberPicker.Formatter() {
//            @Override
//            public String format(int value) {
//                if (month) {
//                    return String.format(Locale.US, "%02d", value);
//                } else {
//                    return String.format(Locale.US, "%02d", value + currentYear);
//                }
//            }
//        });
//        builder.setView(numberPicker);
//        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                result[0] = month ? numberPicker.getValue() : ((numberPicker.getValue() + currentYear) % 100);
//                callback.run();
//            }
//        });
//        return builder.create();
//    }

    public interface PaymentAlertDelegate {
        void didPressedNewCard();
    }

    public static void createDeleteMessagesAlert(BaseFragment fragment, TLRPC.User user, TLRPC.Chat chat, TLRPC.EncryptedChat encryptedChat, TLRPC.ChatFull chatInfo, long mergeDialogId, MessageObject selectedMessage, SparseArray<MessageObject>[] selectedMessages, MessageObject.GroupedMessages selectedGroup, boolean scheduled, int loadParticipant, Runnable onDelete) {
        if (fragment == null || user == null && chat == null && encryptedChat == null) {
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        int currentAccount = fragment.getCurrentAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        int count;
        if (selectedGroup != null) {
            count = selectedGroup.messages.size();
        } else if (selectedMessage != null) {
            count = 1;
        } else {
            count = selectedMessages[0].size() + selectedMessages[1].size();
        }

        long dialogId;
        if (encryptedChat != null) {
            dialogId = ((long) encryptedChat.id) << 32;
        } else if (user != null) {
            dialogId = user.id;
        } else {
            dialogId = -chat.id;
        }

        final boolean[] checks = new boolean[3];
        final boolean[] deleteForAll = new boolean[1];
        TLRPC.User actionUser = null;
        boolean canRevokeInbox = user != null && MessagesController.getInstance(currentAccount).canRevokePmInbox;
        int revokeTimeLimit;
        if (user != null) {
            revokeTimeLimit = MessagesController.getInstance(currentAccount).revokeTimePmLimit;
        } else {
            revokeTimeLimit = MessagesController.getInstance(currentAccount).revokeTimeLimit;
        }
        boolean hasDeleteForAllCheck = false;
        boolean hasNotOut = false;
        int myMessagesCount = 0;
        boolean canDeleteInbox = encryptedChat == null && user != null && canRevokeInbox && revokeTimeLimit == 0x7fffffff;
        if (chat != null && chat.megagroup && !scheduled) {
            boolean canBan = ChatObject.canBlockUsers(chat);
            int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (selectedMessage != null) {
                if (selectedMessage.messageOwner.action == null || selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionEmpty ||
                        selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser ||
                        selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink ||
                        selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    actionUser = MessagesController.getInstance(currentAccount).getUser(selectedMessage.messageOwner.from_id);
                }
                boolean hasOutgoing = !selectedMessage.isSendError() && selectedMessage.getDialogId() == mergeDialogId && (selectedMessage.messageOwner.action == null || selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) && selectedMessage.isOut() && (currentDate - selectedMessage.messageOwner.date) <= revokeTimeLimit;
                if (hasOutgoing) {
                    myMessagesCount++;
                }
            } else {
                int from_id = -1;
                for (int a = 1; a >= 0; a--) {
                    int channelId = 0;
                    for (int b = 0; b < selectedMessages[a].size(); b++) {
                        MessageObject msg = selectedMessages[a].valueAt(b);
                        if (from_id == -1) {
                            from_id = msg.messageOwner.from_id;
                        }
                        if (from_id < 0 || from_id != msg.messageOwner.from_id) {
                            from_id = -2;
                            break;
                        }
                    }
                    if (from_id == -2) {
                        break;
                    }
                }
                for (int a = 1; a >= 0; a--) {
                    for (int b = 0; b < selectedMessages[a].size(); b++) {
                        MessageObject msg = selectedMessages[a].valueAt(b);
                        if (a == 1) {
                            if (msg.isOut() && msg.messageOwner.action == null) {
                                if ((currentDate - msg.messageOwner.date) <= revokeTimeLimit) {
                                    myMessagesCount++;
                                }
                            }
                        }
                    }
                }
                if (from_id != -1) {
                    actionUser = MessagesController.getInstance(currentAccount).getUser(from_id);
                }
            }
            if (actionUser != null && actionUser.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                if (loadParticipant == 1 && !chat.creator) {
                    final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(activity, 3)};

                    TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
                    req.channel = MessagesController.getInputChannel(chat);
                    req.user_id = MessagesController.getInstance(currentAccount).getInputUser(actionUser);
                    int requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog[0].dismiss();
                        } catch (Throwable ignore) {

                        }
                        progressDialog[0] = null;
                        int loadType = 2;
                        if (response != null) {
                            TLRPC.TL_channels_channelParticipant participant = (TLRPC.TL_channels_channelParticipant) response;
                            if (!(participant.participant instanceof TLRPC.TL_channelParticipantAdmin || participant.participant instanceof TLRPC.TL_channelParticipantCreator)) {
                                loadType = 0;
                            }
                        }
                        createDeleteMessagesAlert(fragment, user, chat, encryptedChat, chatInfo, mergeDialogId, selectedMessage, selectedMessages, selectedGroup, scheduled, loadType, onDelete);
                    }));
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressDialog[0] == null) {
                            return;
                        }
                        progressDialog[0].setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true));
                        fragment.showDialog(progressDialog[0]);
                    }, 1000);
                    return;
                }
                FrameLayout frameLayout = new FrameLayout(activity);
                int num = 0;
                for (int a = 0; a < 3; a++) {
                    if ((loadParticipant == 2 || !canBan) && a == 0) {
                        continue;
                    }
                    CheckBoxCell cell = new CheckBoxCell(activity, 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setTag(a);
                    if (a == 0) {
                        cell.setText(LocaleController.getString("DeleteBanUser", R.string.DeleteBanUser), "", false, false);
                    } else if (a == 1) {
                        cell.setText(LocaleController.getString("DeleteReportSpam", R.string.DeleteReportSpam), "", false, false);
                    } else if (a == 2) {
                        cell.setText(LocaleController.formatString("DeleteAllFrom", R.string.DeleteAllFrom, ContactsController.formatName(actionUser.first_name, actionUser.last_name)), "", false, false);
                    }
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                    frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 48 * num, 0, 0));
                    cell.setOnClickListener(v -> {
                        if (!v.isEnabled()) {
                            return;
                        }
                        CheckBoxCell cell13 = (CheckBoxCell) v;
                        Integer num1 = (Integer) cell13.getTag();
                        checks[num1] = !checks[num1];
                        cell13.setChecked(checks[num1], true);
                    });
                    num++;
                }
                builder.setView(frameLayout);
            } else if (!hasNotOut && myMessagesCount > 0) {
                hasDeleteForAllCheck = true;
                FrameLayout frameLayout = new FrameLayout(activity);
                CheckBoxCell cell = new CheckBoxCell(activity, 1);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                if (chat != null && hasNotOut) {
                    cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                } else {
                    cell.setText(LocaleController.getString("DeleteMessagesOption", R.string.DeleteMessagesOption), "", false, false);
                }
                cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                cell.setOnClickListener(v -> {
                    CheckBoxCell cell12 = (CheckBoxCell) v;
                    deleteForAll[0] = !deleteForAll[0];
                    cell12.setChecked(deleteForAll[0], true);
                });
                builder.setView(frameLayout);
                builder.setCustomViewOffset(9);
            } else {
                actionUser = null;
            }
        } else if (!scheduled && !ChatObject.isChannel(chat) && encryptedChat == null) {
            int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && !user.bot || chat != null) {
                if (selectedMessage != null) {
                    boolean hasOutgoing = !selectedMessage.isSendError() && (selectedMessage.messageOwner.action == null || selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionEmpty || selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) && (selectedMessage.isOut() || canRevokeInbox || ChatObject.hasAdminRights(chat)) && (currentDate - selectedMessage.messageOwner.date) <= revokeTimeLimit;
                    if (hasOutgoing) {
                        myMessagesCount++;
                    }
                    hasNotOut = !selectedMessage.isOut();
                } else {
                    for (int a = 1; a >= 0; a--) {
                        for (int b = 0; b < selectedMessages[a].size(); b++) {
                            MessageObject msg = selectedMessages[a].valueAt(b);
                            if (!(msg.messageOwner.action == null || msg.messageOwner.action instanceof TLRPC.TL_messageActionEmpty || msg.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall)) {
                                continue;
                            }
                            if ((msg.isOut() || canRevokeInbox) || chat != null && ChatObject.canBlockUsers(chat)) {
                                if ((currentDate - msg.messageOwner.date) <= revokeTimeLimit) {
                                    myMessagesCount++;
                                    if (!hasNotOut && !msg.isOut()) {
                                        hasNotOut = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (myMessagesCount > 0) {
                hasDeleteForAllCheck = true;
                FrameLayout frameLayout = new FrameLayout(activity);
                CheckBoxCell cell = new CheckBoxCell(activity, 1);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                if (canDeleteInbox) {
                    cell.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
                } else if (chat != null && (hasNotOut || myMessagesCount == count)) {
                    cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                } else {
                    cell.setText(LocaleController.getString("DeleteMessagesOption", R.string.DeleteMessagesOption), "", false, false);
                }
                cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                cell.setOnClickListener(v -> {
                    CheckBoxCell cell1 = (CheckBoxCell) v;
                    deleteForAll[0] = !deleteForAll[0];
                    cell1.setChecked(deleteForAll[0], true);
                });
                builder.setView(frameLayout);
                builder.setCustomViewOffset(9);
            }
        }
        final TLRPC.User userFinal = actionUser;
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
            ArrayList<Integer> ids = null;
            if (selectedMessage != null) {
                ids = new ArrayList<>();
                ArrayList<Long> random_ids = null;
                if (selectedGroup != null) {
                    for (int a = 0; a < selectedGroup.messages.size(); a++) {
                        MessageObject messageObject = selectedGroup.messages.get(a);
                        ids.add(messageObject.getId());
                        if (encryptedChat != null && messageObject.messageOwner.random_id != 0 && messageObject.type != 10) {
                            if (random_ids == null) {
                                random_ids = new ArrayList<>();
                            }
                            random_ids.add(messageObject.messageOwner.random_id);
                        }
                    }
                } else {
                    ids.add(selectedMessage.getId());
                    if (encryptedChat != null && selectedMessage.messageOwner.random_id != 0 && selectedMessage.type != 10) {
                        random_ids = new ArrayList<>();
                        random_ids.add(selectedMessage.messageOwner.random_id);
                    }
                }
                MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids, encryptedChat, dialogId, selectedMessage.messageOwner.to_id.channel_id, deleteForAll[0], scheduled);
            } else {
                for (int a = 1; a >= 0; a--) {
                    ids = new ArrayList<>();
                    for (int b = 0; b < selectedMessages[a].size(); b++) {
                        ids.add(selectedMessages[a].keyAt(b));
                    }
                    ArrayList<Long> random_ids = null;
                    int channelId = 0;
                    if (!ids.isEmpty()) {
                        MessageObject msg = selectedMessages[a].get(ids.get(0));
                        if (channelId == 0 && msg.messageOwner.to_id.channel_id != 0) {
                            channelId = msg.messageOwner.to_id.channel_id;
                        }
                    }
                    if (encryptedChat != null) {
                        random_ids = new ArrayList<>();
                        for (int b = 0; b < selectedMessages[a].size(); b++) {
                            MessageObject msg = selectedMessages[a].valueAt(b);
                            if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                random_ids.add(msg.messageOwner.random_id);
                            }
                        }
                    }
                    MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids, encryptedChat, dialogId, channelId, deleteForAll[0], scheduled);
                    selectedMessages[a].clear();
                }
            }
            if (userFinal != null) {
                if (checks[0]) {
                    MessagesController.getInstance(currentAccount).deleteUserFromChat(chat.id, userFinal, chatInfo);
                }
                if (checks[1]) {
                    TLRPC.TL_channels_reportSpam req = new TLRPC.TL_channels_reportSpam();
                    req.channel = MessagesController.getInputChannel(chat);
                    req.user_id = MessagesController.getInstance(currentAccount).getInputUser(userFinal);
                    req.id = ids;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                    });
                }
                if (checks[2]) {
                    MessagesController.getInstance(currentAccount).deleteUserChannelHistory(chat, userFinal, 0);
                }
            }
            if (onDelete != null) {
                onDelete.run();
            }
        });

        if (count == 1) {
            builder.setTitle(LocaleController.getString("DeleteSingleMessagesTitle", R.string.DeleteSingleMessagesTitle));
        } else {
            builder.setTitle(LocaleController.formatString("DeleteMessagesTitle", R.string.DeleteMessagesTitle, LocaleController.formatPluralString("messages", count)));
        }

        if (chat != null && hasNotOut) {
            if (hasDeleteForAllCheck && myMessagesCount != count) {
                builder.setMessage(LocaleController.formatString("DeleteMessagesTextGroupPart", R.string.DeleteMessagesTextGroupPart, LocaleController.formatPluralString("messages", myMessagesCount)));
            } else if (count == 1) {
                builder.setMessage(LocaleController.getString("AreYouSureDeleteSingleMessage", R.string.AreYouSureDeleteSingleMessage));
            } else {
                builder.setMessage(LocaleController.getString("AreYouSureDeleteFewMessages", R.string.AreYouSureDeleteFewMessages));
            }
        } else if (hasDeleteForAllCheck && !canDeleteInbox && myMessagesCount != count) {
            if (chat != null) {
                builder.setMessage(LocaleController.formatString("DeleteMessagesTextGroup", R.string.DeleteMessagesTextGroup, LocaleController.formatPluralString("messages", myMessagesCount)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteMessagesText", R.string.DeleteMessagesText, LocaleController.formatPluralString("messages", myMessagesCount), UserObject.getFirstName(user))));
            }
        } else {
            if (chat != null && chat.megagroup) {
                if (count == 1) {
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteSingleMessageMega", R.string.AreYouSureDeleteSingleMessageMega));
                } else {
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteFewMessagesMega", R.string.AreYouSureDeleteFewMessagesMega));
                }
            } else {
                if (count == 1) {
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteSingleMessage", R.string.AreYouSureDeleteSingleMessage));
                } else {
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteFewMessages", R.string.AreYouSureDeleteFewMessages));
                }
            }
        }

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }
}
