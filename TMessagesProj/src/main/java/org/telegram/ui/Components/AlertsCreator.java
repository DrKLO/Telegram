/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.OneUIUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.TimezonesController;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Cells.AccountSelectCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.LanguageSelectActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;
import org.telegram.ui.NotificationsCustomSettingsActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.PrivacyControlActivity;
import org.telegram.ui.ProfileNotificationsActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.ThemePreviewActivity;
import org.telegram.ui.TooManyCommunitiesActivity;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AlertsCreator {
    public final static int PERMISSIONS_REQUEST_TOP_ICON_SIZE = 72;
    public final static int NEW_DENY_DIALOG_TOP_ICON_SIZE = 52;

    public static Dialog createForgotPasscodeDialog(Context ctx) {
        return new AlertDialog.Builder(ctx)
                .setTitle(LocaleController.getString(R.string.ForgotPasscode))
                .setMessage(LocaleController.getString(R.string.ForgotPasscodeInfo))
                .setPositiveButton(LocaleController.getString(R.string.Close), null)
                .create();
    }

    public static Dialog createLocationRequiredDialog(Context ctx, boolean friends) {
        return new AlertDialog.Builder(ctx)
                .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionNoLocationFriends)))
                .setTopAnimation(R.raw.permission_request_location, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        ctx.startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                .create();
    }

    public static Dialog createBackgroundActivityDialog(Context ctx) {
        return new AlertDialog.Builder(ctx)
                .setTitle(LocaleController.getString(R.string.AllowBackgroundActivity))
                .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(OneUIUtilities.isOneUI() ? Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? R.string.AllowBackgroundActivityInfoOneUIAboveS :
                        R.string.AllowBackgroundActivityInfoOneUIBelowS : R.string.AllowBackgroundActivityInfo)))
                .setTopAnimation(R.raw.permission_request_apk, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        ctx.startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                .setOnDismissListener(dialog -> SharedConfig.BackgroundActivityPrefs.increaseDismissedCount())
                .create();
    }

    public static Dialog createWebViewPermissionsRequestDialog(Context ctx, Theme.ResourcesProvider resourcesProvider, String[] systemPermissions, @RawRes int animationId, String title, String titleWithHint, Consumer<Boolean> callback) {
        boolean showSettings = false;
        if (systemPermissions != null && ctx instanceof Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Activity activity = (Activity) ctx;
            for (String perm : systemPermissions) {
                if (activity.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED && activity.shouldShowRequestPermissionRationale(perm)) {
                    showSettings = true;
                    break;
                }
            }
        }
        AtomicBoolean gotCallback = new AtomicBoolean();
        boolean finalShowSettings = showSettings;
        return new AlertDialog.Builder(ctx, resourcesProvider)
                .setTopAnimation(animationId, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                .setMessage(AndroidUtilities.replaceTags(showSettings ? titleWithHint : title))
                .setPositiveButton(LocaleController.getString(showSettings ? R.string.PermissionOpenSettings : R.string.BotWebViewRequestAllow), (dialogInterface, i) -> {
                    if (finalShowSettings) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            ctx.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        gotCallback.set(true);
                        callback.accept(true);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.BotWebViewRequestDontAllow), (dialog, which) -> {
                    gotCallback.set(true);
                    callback.accept(false);
                })
                .setOnDismissListener(dialog -> {
                    if (!gotCallback.get()) {
                        callback.accept(false);
                    }
                })
                .create();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Dialog createApkRestrictedDialog(Context ctx, Theme.ResourcesProvider resourcesProvider) {
        return new AlertDialog.Builder(ctx, resourcesProvider)
                .setMessage(LocaleController.getString(R.string.ApkRestricted))
                .setTopAnimation(R.raw.permission_request_apk, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                    try {
                        ctx.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.getPackageName())));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                .create();
    }

    public static Dialog processError(int currentAccount, TLRPC.TL_error error, BaseFragment fragment, TLObject request, Object... args) {
        if (error == null || error.code == 406 || error.text == null) {
            return null;
        }
        if (request instanceof TLRPC.TL_messages_sendMessage && error.text.contains("PRIVACY_PREMIUM_REQUIRED")) {
            TLRPC.TL_messages_sendMessage req = (TLRPC.TL_messages_sendMessage) request;
            long dialogId = DialogObject.getPeerDialogId(req.peer);
            String username = "";
            if (dialogId >= 0) {
                username = UserObject.getFirstName(MessagesController.getInstance(currentAccount).getUser(dialogId));
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (chat != null) {
                    username = chat.title;
                }
            }
            if (fragment == null) {
                fragment = LaunchActivity.getLastFragment();
            }
            showSimpleAlert(fragment, LocaleController.getString(R.string.MessagePremiumErrorTitle), LocaleController.formatString(R.string.MessagePremiumErrorMessage, username));
            MessagesController.getInstance(currentAccount).invalidateUserPremiumBlocked(dialogId, 0);
        } else if (request instanceof TLRPC.TL_messages_initHistoryImport || request instanceof TLRPC.TL_messages_checkHistoryImportPeer || request instanceof TLRPC.TL_messages_checkHistoryImport || request instanceof TLRPC.TL_messages_startHistoryImport) {
            TLRPC.InputPeer peer;
            if (request instanceof TLRPC.TL_messages_initHistoryImport) {
                peer = ((TLRPC.TL_messages_initHistoryImport) request).peer;
            } else if (request instanceof TLRPC.TL_messages_startHistoryImport) {
                peer = ((TLRPC.TL_messages_startHistoryImport) request).peer;
            } else {
                peer = null;
            }
            if (error.text.contains("USER_IS_BLOCKED")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorUserBlocked));
            } else if (error.text.contains("USER_NOT_MUTUAL_CONTACT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportMutualError));
            } else if (error.text.contains("IMPORT_PEER_TYPE_INVALID")) {
                if (peer instanceof TLRPC.TL_inputPeerUser) {
                    showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorChatInvalidUser));
                } else {
                    showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorChatInvalidGroup));
                }
            } else if (error.text.contains("CHAT_ADMIN_REQUIRED")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorNotAdmin));
            } else if (error.text.startsWith("IMPORT_FORMAT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorFileFormatInvalid));
            } else if (error.text.startsWith("PEER_ID_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorPeerInvalid));
            } else if (error.text.contains("IMPORT_LANG_NOT_FOUND")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportErrorFileLang));
            } else if (error.text.contains("IMPORT_UPLOAD_FAILED")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ImportFailedToUpload));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showFloodWaitAlert(error.text, fragment);
            } else {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ImportErrorTitle), LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text);
            }
        } else if (request instanceof TLRPC.TL_account_saveSecureValue || request instanceof TLRPC.TL_account_getAuthorizationForm) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidPhoneNumber));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else if ("APP_VERSION_OUTDATED".equals(error.text)) {
                showUpdateAppAlert(fragment.getParentActivity(), LocaleController.getString(R.string.UpdateAppAlert), true);
            } else {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text);
            }
        } else if (request instanceof TLRPC.TL_channels_joinChannel ||
                request instanceof TLRPC.TL_channels_editAdmin ||
                request instanceof TLRPC.TL_channels_inviteToChannel ||
                request instanceof TLRPC.TL_messages_addChatUser ||
                request instanceof TLRPC.TL_messages_startBot ||
                request instanceof TLRPC.TL_channels_editBanned ||
                request instanceof TLRPC.TL_messages_editChatDefaultBannedRights ||
                request instanceof TLRPC.TL_messages_editChatAdmin ||
                request instanceof TLRPC.TL_messages_migrateChat ||
                request instanceof TLRPC.TL_phone_inviteToGroupCall) {
            if (fragment != null && error.text.equals("CHANNELS_TOO_MUCH")) {
                if (fragment.getParentActivity() != null) {
                    fragment.showDialog(new LimitReachedBottomSheet(fragment, fragment.getParentActivity(), LimitReachedBottomSheet.TYPE_TO0_MANY_COMMUNITIES, currentAccount, null));
                } else {
                    if (request instanceof TLRPC.TL_channels_joinChannel || request instanceof TLRPC.TL_channels_inviteToChannel) {
                        fragment.presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_JOIN));
                    } else {
                        fragment.presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_EDIT));
                    }
                }
                return null;
            } else if (fragment != null) {
                showAddUserAlert(error.text, fragment, args != null && args.length > 0 ? (Boolean) args[0] : false, request);
            } else {
                if (error.text.equals("PEER_FLOOD")) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 1);
                }
            }
        } else if (request instanceof TLRPC.TL_messages_createChat) {
            if (error.text.equals("CHANNELS_TOO_MUCH")) {
                if (fragment.getParentActivity() != null) {
                    fragment.showDialog(new LimitReachedBottomSheet(fragment, fragment.getParentActivity(), LimitReachedBottomSheet.TYPE_TO0_MANY_COMMUNITIES, currentAccount, null));
                } else {
                    fragment.presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_CREATE));
                }
                return null;
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showFloodWaitAlert(error.text, fragment);
            } else {
                showAddUserAlert(error.text, fragment, false, request);
            }
        } else if (request instanceof TLRPC.TL_channels_createChannel) {
            if (error.text.equals("CHANNELS_TOO_MUCH")) {
                if (fragment.getParentActivity() != null) {
                    fragment.showDialog(new LimitReachedBottomSheet(fragment, fragment.getParentActivity(), LimitReachedBottomSheet.TYPE_TO0_MANY_COMMUNITIES, currentAccount, null));
                } else {
                    fragment.presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_CREATE));
                }
                return null;
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showFloodWaitAlert(error.text, fragment);
            } else {
                showAddUserAlert(error.text, fragment, false, request);
            }
        } else if (request instanceof TLRPC.TL_messages_editMessage) {
            if (!error.text.equals("MESSAGE_NOT_MODIFIED")) {
                if (fragment != null) {
                    showSimpleAlert(fragment, LocaleController.getString(R.string.EditMessageError));
                } else {
                    showSimpleToast(null, LocaleController.getString(R.string.EditMessageError));
                }
            }
        } else if (request instanceof TLRPC.TL_messages_sendMessage ||
                request instanceof TLRPC.TL_messages_sendMedia ||
                request instanceof TLRPC.TL_messages_sendInlineBotResult ||
                request instanceof TLRPC.TL_messages_forwardMessages ||
                request instanceof TLRPC.TL_messages_sendMultiMedia ||
                request instanceof TLRPC.TL_messages_sendScheduledMessages) {
            long dialogId = 0;
            if (request instanceof TLRPC.TL_messages_sendMessage) {
                dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendMessage) request).peer);
            } else if (request instanceof TLRPC.TL_messages_sendMedia) {
                dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendMedia) request).peer);
            } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
                dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendInlineBotResult) request).peer);
            } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
                dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_messages_forwardMessages) request).to_peer);
            } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
                dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendMultiMedia) request).peer);
            } else if (request instanceof TLRPC.TL_messages_sendScheduledMessages) {
                dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendScheduledMessages) request).peer);
            }
            if (error.text != null && error.text.startsWith("CHAT_SEND_") && error.text.endsWith("FORBIDDEN")) {
                String errorText = error.text;
                TLRPC.Chat chat = dialogId < 0 ? MessagesController.getInstance(currentAccount).getChat(-dialogId) : null;
                switch (error.text) {
                    case "CHAT_SEND_PLAIN_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_PLAIN);
                        break;
                    case "CHAT_SEND_PHOTOS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_PHOTO);
                        break;
                    case "CHAT_SEND_VIDEOS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_VIDEO);
                        break;
                    case "CHAT_SEND_ROUNDVIDEOS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_ROUND);
                        break;
                    case "CHAT_SEND_DOCS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_DOCUMENTS);
                        break;
                    case "CHAT_SEND_VOICES_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_VOICE);
                        break;
                    case "CHAT_SEND_AUDIOS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_MUSIC);
                        break;
                    case "CHAT_SEND_STICKERS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_STICKERS);
                        break;
                    case "CHAT_SEND_GIFS_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_GIFS);
                        break;
                    case "CHAT_SEND_POLL_FORBIDDEN":
                        errorText = ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_POLLS);
                        break;

                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, errorText);
            } else {
                switch (error.text) {
                    case "PEER_FLOOD":
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 0);
                        break;
                    case "USER_BANNED_IN_CHANNEL":
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 5);
                        break;
                    case "SCHEDULE_TOO_MUCH":
                        showSimpleToast(fragment, LocaleController.getString(R.string.MessageScheduledLimitReached));
                        break;
                }
            }
        } else if (request instanceof TLRPC.TL_messages_importChatInvite) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else if (error.text.equals("USERS_TOO_MUCH")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.JoinToGroupErrorFull));
            } else if (error.text.equals("CHANNELS_TOO_MUCH")) {
                if (fragment.getParentActivity() != null) {
                    fragment.showDialog(new LimitReachedBottomSheet(fragment, fragment.getParentActivity(), LimitReachedBottomSheet.TYPE_TO0_MANY_COMMUNITIES, currentAccount, null));
                } else {
                    fragment.presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_JOIN));
                }
            } else if (error.text.equals("INVITE_HASH_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ExpiredLink), LocaleController.getString(R.string.InviteExpired));
            } else {
                showSimpleAlert(fragment, LocaleController.getString(R.string.JoinToGroupErrorNotExist));
            }
        } else if (request instanceof TLRPC.TL_messages_getAttachedStickers) {
            if (fragment != null && fragment.getParentActivity() != null) {
                Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text, Toast.LENGTH_SHORT).show();
            }
        } else if (request instanceof TLRPC.TL_account_confirmPhone || request instanceof TLRPC.TL_account_verifyPhone || request instanceof TLRPC.TL_account_verifyEmail) {
            if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID") || error.text.contains("CODE_INVALID") || error.text.contains("CODE_EMPTY")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED") || error.text.contains("EMAIL_VERIFY_EXPIRED")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else {
                return showSimpleAlert(fragment, error.text);
            }
        } else if (request instanceof TLRPC.TL_auth_resendCode) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidPhoneNumber));
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else if (error.code != -1000) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text);
            }
        } else if (request instanceof TLRPC.TL_account_sendConfirmPhoneCode) {
            if (error.code == 400) {
                return showSimpleAlert(fragment, LocaleController.getString(R.string.CancelLinkExpired));
            } else {
                if (error.text.startsWith("FLOOD_WAIT")) {
                    return showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
                } else {
                    return showSimpleAlert(fragment, LocaleController.getString(R.string.ErrorOccurred));
                }
            }
        } else if (request instanceof TLRPC.TL_account_changePhone) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidPhoneNumber));
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else if (error.text.contains("FRESH_CHANGE_PHONE_FORBIDDEN")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.FreshChangePhoneForbiddenTitle), LocaleController.getString(R.string.FreshChangePhoneForbidden));
            } else {
                showSimpleAlert(fragment, error.text);
            }
        } else if (request instanceof TLRPC.TL_account_sendChangePhoneCode) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                LoginActivity.needShowInvalidAlert(fragment, (String) args[0], false);
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else if (error.text.startsWith("PHONE_NUMBER_OCCUPIED")) {
                showSimpleAlert(fragment, LocaleController.formatString("ChangePhoneNumberOccupied", R.string.ChangePhoneNumberOccupied, args[0]));
            } else if (error.text.startsWith("PHONE_NUMBER_BANNED")) {
                LoginActivity.needShowInvalidAlert(fragment, (String) args[0], true);
            } else {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ErrorOccurred));
            }
        } else if (request instanceof TLRPC.TL_account_updateUsername) {
            switch (error.text) {
                case "USERNAME_INVALID":
                    showSimpleAlert(fragment, LocaleController.getString(R.string.UsernameInvalid));
                    break;
                case "USERNAME_OCCUPIED":
                    showSimpleAlert(fragment, LocaleController.getString(R.string.UsernameInUse));
                    break;
                default:
                    showSimpleAlert(fragment, LocaleController.getString(R.string.ErrorOccurred));
                    break;
            }
        } else if (request instanceof TLRPC.TL_contacts_importContacts) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString(R.string.FloodWait));
            } else {
                showSimpleAlert(fragment, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text);
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
                    showSimpleToast(fragment, LocaleController.getString(R.string.PaymentPrecheckoutFailed));
                    break;
                case "PAYMENT_FAILED":
                    showSimpleToast(fragment, LocaleController.getString(R.string.PaymentFailed));
                    break;
                default:
                    showSimpleToast(fragment, error.text);
                    break;
            }
        } else if (request instanceof TLRPC.TL_payments_validateRequestedInfo) {
            switch (error.text) {
                case "SHIPPING_NOT_AVAILABLE":
                    showSimpleToast(fragment, LocaleController.getString(R.string.PaymentNoShippingMethod));
                    break;
                default:
                    showSimpleToast(fragment, error.text);
                    break;
            }
        } else if (request instanceof TLRPC.TL_payments_assignPlayMarketTransaction) {
            showSimpleAlert(fragment, LocaleController.getString(R.string.PaymentConfirmationError) + "\n" + error.text);
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
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        if (updateApp) {
            builder.setNegativeButton(LocaleController.getString(R.string.UpdateApp), (dialog, which) -> Browser.openUrl(context, BuildVars.PLAYSTORE_APP_URL));
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
            builder.setTitle(LocaleController.getString(R.string.Language));
            str = LocaleController.formatString("LanguageSame", R.string.LanguageSame, language.name);
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.setNeutralButton(LocaleController.getString(R.string.SETTINGS), (dialog, which) -> activity.presentFragment(new LanguageSelectActivity()));
        } else {
            if (language.strings_count == 0) {
                builder.setTitle(LocaleController.getString(R.string.LanguageUnknownTitle));
                str = LocaleController.formatString("LanguageUnknownCustomAlert", R.string.LanguageUnknownCustomAlert, language.name);
                builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            } else {
                builder.setTitle(LocaleController.getString(R.string.LanguageTitle));
                if (language.official) {
                    str = LocaleController.formatString("LanguageAlert", R.string.LanguageAlert, language.name, (int) Math.ceil(language.translated_count / (float) language.strings_count * 100));
                } else {
                    str = LocaleController.formatString("LanguageCustomAlert", R.string.LanguageCustomAlert, language.name, (int) Math.ceil(language.translated_count / (float) language.strings_count * 100));
                }
                builder.setPositiveButton(LocaleController.getString(R.string.Change), (dialogInterface, i) -> {
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
                    LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, UserConfig.selectedAccount, null);
                    activity.rebuildAllFragments(true);
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            }
        }

        spanned = new SpannableStringBuilder(AndroidUtilities.replaceTags(str));

        int start = TextUtils.indexOf(spanned, '[');
        int end;
        if (start != -1) {
            end = TextUtils.indexOf(spanned, ']', start + 1);
            if (end != -1) {
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
        message.setPadding(dp(23), 0, dp(23), 0);
        message.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        builder.setView(message);

        return builder;
    }

    public static boolean checkSlowMode(Context context, int currentAccount, long did, boolean few) {
        if (DialogObject.isChatDialog(did)) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            if (chat != null && chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
                if (!few) {
                    TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
                    if (chatFull == null) {
                        chatFull = MessagesStorage.getInstance(currentAccount).loadChatInfo(chat.id, ChatObject.isChannel(chat), new CountDownLatch(1), false, false);
                    }
                    if (chatFull != null && chatFull.slowmode_next_send_date >= ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                        few = true;
                    }
                }
                if (few) {
                    createSimpleAlert(context, chat.title, LocaleController.getString(R.string.SlowmodeSendError)).show();
                    return true;
                }
            }
        }
        return false;
    }

    public static AlertDialog.Builder createNoAccessAlert(Context context, String title, String message, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        Map<String, Integer> colorsReplacement = new HashMap<>();
        colorsReplacement.put("info1.**", Theme.getColor(Theme.key_dialogTopBackground, resourcesProvider));
        colorsReplacement.put("info2.**", Theme.getColor(Theme.key_dialogTopBackground, resourcesProvider));
        builder.setTopAnimation(R.raw.not_available, AlertsCreator.NEW_DENY_DIALOG_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground, resourcesProvider), colorsReplacement);
        builder.setTopAnimationIsNew(true);
        builder.setPositiveButton(LocaleController.getString(R.string.Close), null);
        builder.setMessage(message);
        return builder;
    }

    public static AlertDialog.Builder createSimpleAlert(Context context, final String text) {
        return createSimpleAlert(context, null, text);
    }

    public static AlertDialog.Builder createSimpleAlert(Context context, final String title, final String text) {
        return createSimpleAlert(context, title, text, null);
    }

    public static AlertDialog.Builder createSimpleAlert(Context context, final String title, final String text, Theme.ResourcesProvider resourcesProvider) {
        return createSimpleAlert(context, title, text, null, null, resourcesProvider);
    }

    public static AlertDialog.Builder createSimpleAlert(Context context, final String title, final String text, String positiveButton, Runnable positive, Theme.ResourcesProvider resourcesProvider) {
        if (context == null || text == null) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title == null ? LocaleController.getString(R.string.AppName) : title);
        builder.setMessage(text);
        if (positiveButton == null) {
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        } else {
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            builder.setPositiveButton(positiveButton, (dialog, which) -> {
                dialog.dismiss();
                if (positive != null) {
                    positive.run();
                }
            });
        }
        return builder;
    }

    public static Dialog showSimpleAlert(BaseFragment baseFragment, final String text) {
        return showSimpleAlert(baseFragment, null, text);
    }

    public static Dialog showSimpleAlert(BaseFragment baseFragment, final String title, final String text) {
        return showSimpleAlert(baseFragment, title, text, null);
    }

    public static Dialog showSimpleAlert(BaseFragment baseFragment, final String title, final String text, Theme.ResourcesProvider resourcesProvider) {
        if (text == null || baseFragment == null || baseFragment.getParentActivity() == null) {
            return null;
        }
        AlertDialog.Builder builder = createSimpleAlert(baseFragment.getParentActivity(), title, text, resourcesProvider);
        Dialog dialog = builder.create();
        baseFragment.showDialog(dialog);
        return dialog;
    }

    public static void showBlockReportSpamReplyAlert(ChatActivity fragment, MessageObject messageObject, long peerId, Theme.ResourcesProvider resourcesProvider, Runnable hideDim) {
        if (fragment == null || fragment.getParentActivity() == null || messageObject == null) {
            return;
        }

        AccountInstance accountInstance = fragment.getAccountInstance();
        TLRPC.User user = peerId > 0 ? accountInstance.getMessagesController().getUser(peerId) : null;
        TLRPC.Chat chat = peerId < 0 ? accountInstance.getMessagesController().getChat(-peerId) : null;
        if (user == null && chat == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
        builder.setDimEnabled(hideDim == null);
        builder.setOnPreDismissListener(di -> {
            if (hideDim != null) {
                hideDim.run();
            }
        });

        builder.setTitle(LocaleController.getString(R.string.BlockUser));
        if (user != null) {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserReplyAlert", R.string.BlockUserReplyAlert, UserObject.getFirstName(user))));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserReplyAlert", R.string.BlockUserReplyAlert, chat.title)));
        }

        CheckBoxCell[] cells = new CheckBoxCell[1];
        LinearLayout linearLayout = new LinearLayout(fragment.getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        cells[0] = new CheckBoxCell(fragment.getParentActivity(), 1, resourcesProvider);
        cells[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
        cells[0].setTag(0);
        cells[0].setText(LocaleController.getString(R.string.DeleteReportSpam), "", true, false);

        cells[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
        linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        cells[0].setOnClickListener(v -> {
            Integer num = (Integer) v.getTag();
            cells[num].setChecked(!cells[num].isChecked(), true);
        });

        builder.setView(linearLayout);

        builder.setPositiveButton(LocaleController.getString(R.string.BlockAndDeleteReplies), (dialogInterface, i) -> {
            if (user != null) {
                accountInstance.getMessagesStorage().deleteUserChatHistory(fragment.getDialogId(), user.id);
            } else {
                accountInstance.getMessagesStorage().deleteUserChatHistory(fragment.getDialogId(), -chat.id);
            }
            TLRPC.TL_contacts_blockFromReplies request = new TLRPC.TL_contacts_blockFromReplies();
            request.msg_id = messageObject.getId();
            request.delete_message = true;
            request.delete_history = true;
            if (cells[0].isChecked()) {
                request.report_spam = true;
                if (fragment.getParentActivity() != null) {
                    if (fragment instanceof ChatActivity) {
                        UndoView undoView = fragment.getUndoView();
                        if (undoView != null) {
                            undoView.showWithAction(0, UndoView.ACTION_REPORT_SENT, null);
                        }
                    } else if (fragment != null) {
                        BulletinFactory.of(fragment).createReportSent(resourcesProvider).show();
                    } else {
                        Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.ReportChatSent), Toast.LENGTH_SHORT).show();
                    }
                }
            }
            accountInstance.getConnectionsManager().sendRequest(request, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    accountInstance.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void showBlockReportSpamAlert(BaseFragment fragment, long dialog_id, TLRPC.User currentUser, TLRPC.Chat currentChat, TLRPC.EncryptedChat encryptedChat, boolean isLocation, TLRPC.ChatFull chatInfo, MessagesStorage.IntCallback callback, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AccountInstance accountInstance = fragment.getAccountInstance();
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
        CharSequence reportText;
        CheckBoxCell[] cells;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(fragment.getCurrentAccount());
        boolean showReport = encryptedChat != null || preferences.getBoolean("dialog_bar_report" + dialog_id, false);
        if (currentUser != null) {
            builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, UserObject.getFirstName(currentUser)));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserAlert", R.string.BlockUserAlert, UserObject.getFirstName(currentUser))));
            reportText = LocaleController.getString(R.string.BlockContact);

            cells = new CheckBoxCell[2];
            LinearLayout linearLayout = new LinearLayout(fragment.getParentActivity());
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            for (int a = 0; a < 2; a++) {
                if (a == 0 && !showReport) {
                    continue;
                }
                cells[a] = new CheckBoxCell(fragment.getParentActivity(), 1, resourcesProvider);
                cells[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cells[a].setTag(a);
                if (a == 0) {
                    cells[a].setText(LocaleController.getString(R.string.DeleteReportSpam), "", true, false);
                } else {
                    cells[a].setText(LocaleController.formatString("DeleteThisChat", R.string.DeleteThisChat), "", true, false);
                }
                cells[a].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
                linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                cells[a].setOnClickListener(v -> {
                    Integer num = (Integer) v.getTag();
                    cells[num].setChecked(!cells[num].isChecked(), true);
                });
            }
            builder.setView(linearLayout);
        } else {
            cells = null;
            if (currentChat != null && isLocation) {
                builder.setTitle(LocaleController.getString(R.string.ReportUnrelatedGroup));
                if (chatInfo != null && chatInfo.location instanceof TLRPC.TL_channelLocation) {
                    TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatInfo.location;
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ReportUnrelatedGroupText", R.string.ReportUnrelatedGroupText, location.address)));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.ReportUnrelatedGroupTextNoAddress));
                }
            } else {
                builder.setTitle(LocaleController.getString(R.string.ReportSpamTitle));
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    builder.setMessage(LocaleController.getString(R.string.ReportSpamAlertChannel));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.ReportSpamAlertGroup));
                }
            }
            reportText = LocaleController.getString(R.string.ReportChat);
        }
        builder.setPositiveButton(reportText, (dialogInterface, i) -> {
            if (currentUser != null) {
                accountInstance.getMessagesController().blockPeer(currentUser.id);
            }
            if (cells == null || cells[0] != null && cells[0].isChecked()) {
                accountInstance.getMessagesController().reportSpam(dialog_id, currentUser, currentChat, encryptedChat, currentChat != null && isLocation);
            }
            if (cells == null || cells[1].isChecked()) {
                if (currentChat != null) {
                    if (ChatObject.isNotInChat(currentChat)) {
                        accountInstance.getMessagesController().deleteDialog(dialog_id, 0);
                    } else {
                        accountInstance.getMessagesController().deleteParticipantFromChat(-dialog_id, accountInstance.getMessagesController().getUser(accountInstance.getUserConfig().getClientUserId()));
                    }
                } else {
                    accountInstance.getMessagesController().deleteDialog(dialog_id, 0);
                }
                callback.run(1);
            } else {
                callback.run(0);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void showCustomNotificationsDialog(BaseFragment parentFragment, long did, int topicId, int globalType, ArrayList<NotificationsSettingsActivity.NotificationException> exceptions, ArrayList<NotificationsSettingsActivity.NotificationException> autoExceptions, int currentAccount, MessagesStorage.IntCallback callback) {
        showCustomNotificationsDialog(parentFragment, did, topicId, globalType, exceptions, autoExceptions, currentAccount, callback, null);
    }

    public static void showCustomNotificationsDialog(BaseFragment parentFragment, long did, int topicId, int globalType, ArrayList<NotificationsSettingsActivity.NotificationException> exceptions, ArrayList<NotificationsSettingsActivity.NotificationException> autoExceptions, int currentAccount, MessagesStorage.IntCallback callback, MessagesStorage.IntCallback resultCallback) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        boolean enabled;
        boolean defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(did, false, false);

        String[] descriptions = new String[]{
                LocaleController.getString(R.string.NotificationsTurnOn),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)),
                did == 0 && parentFragment instanceof NotificationsCustomSettingsActivity ? null : LocaleController.getString(R.string.NotificationsCustomize),
                LocaleController.getString(R.string.NotificationsTurnOff)
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
                textView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedRegular), PorterDuff.Mode.MULTIPLY));
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
            textView.setPadding(dp(24), 0, dp(24), 0);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            textView.setCompoundDrawablePadding(dp(26));
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
                        NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did, topicId);
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
                        parentFragment.presentFragment(new NotificationsCustomSettingsActivity(globalType, exceptions, autoExceptions));
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
                    NotificationsController.getInstance(currentAccount).muteUntil(did, topicId, untilTime);
                    if (did != 0 && resultCallback != null) {
                        if (i == 4 && !defaultEnabled) {
                            resultCallback.run(0);
                        } else {
                            resultCallback.run(1);
                        }
                    }
                    if (did == 0) {
                        NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(globalType, Integer.MAX_VALUE);
                    }
                }

                if (callback != null) {
                    callback.run(i);
                }
                builder.getDismissRunnable().run();
                int setting = -1;
                if (i == 0) {
                    setting = NotificationsController.SETTING_MUTE_UNMUTE;
                } else if (i == 1) {
                    setting = NotificationsController.SETTING_MUTE_HOUR;
                } else if (i == 2) {
                    setting = NotificationsController.SETTING_MUTE_2_DAYS;
                } else if (i == 4) {
                    setting = NotificationsController.SETTING_MUTE_FOREVER;
                }
                if (setting >= 0) {
                    if (BulletinFactory.canShowBulletin(parentFragment)) {
                        BulletinFactory.createMuteBulletin(parentFragment, setting).show();
                    }
                }
            });
        }
        builder.setTitle(LocaleController.getString(R.string.Notifications));
        builder.setView(linearLayout);
        parentFragment.showDialog(builder.create());
    }

    public static AlertDialog showSecretLocationAlert(Context context, int currentAccount, final Runnable onSelectRunnable, boolean inChat, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<String> arrayList = new ArrayList<>();
        ArrayList<Integer> types = new ArrayList<>();
        int providers = MessagesController.getInstance(currentAccount).availableMapProviders;
        if ((providers & 1) != 0) {
            arrayList.add(LocaleController.getString(R.string.MapPreviewProviderTelegram));
            types.add(0);
        }
        if ((providers & 2) != 0) {
            arrayList.add(LocaleController.getString(R.string.MapPreviewProviderGoogle));
            types.add(1);
        }
        if ((providers & 4) != 0) {
            arrayList.add(LocaleController.getString(R.string.MapPreviewProviderYandex));
            types.add(3);
        }
        arrayList.add(LocaleController.getString(R.string.MapPreviewProviderNobody));
        types.add(2);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.MapPreviewProviderTitle));
        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setView(linearLayout);

        for (int a = 0; a < arrayList.size(); a++) {
            RadioColorCell cell = new RadioColorCell(context, resourcesProvider);
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(arrayList.get(a), SharedConfig.mapPreviewType == types.get(a));
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                Integer which = (Integer) v.getTag();
                SharedConfig.setSecretMapPreviewType(types.get(which));
                if (onSelectRunnable != null) {
                    onSelectRunnable.run();
                }
                builder.getDismissRunnable().run();
            });
        }
        if (!inChat) {
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
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

        int year, month;

        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH);
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        yearPicker.setMinValue(currentYear);
        year = yearPicker.getValue();

        monthPicker.setMinValue(year == currentYear ? currentMonth : 0);
        month = monthPicker.getValue();

        dayPicker.setMinValue(year == currentYear && month == currentMonth ? currentDay : 1);
    }

    public static void showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean ask) {
        showOpenUrlAlert(fragment, url, punycode, true, ask, false, null, null);
    }

    public static void showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean ask, Theme.ResourcesProvider resourcesProvider) {
        showOpenUrlAlert(fragment, url, punycode, true, ask, false, null, resourcesProvider);
    }

    public static void showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean tryTelegraph, boolean ask, Browser.Progress progress, Theme.ResourcesProvider resourcesProvider) {
        showOpenUrlAlert(fragment, url, punycode, tryTelegraph, ask, false, progress, resourcesProvider);
    }

    public static void showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean tryTelegraph, boolean ask, boolean forceNotInternalForApps, Browser.Progress progress, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        long inlineReturn = (fragment instanceof ChatActivity) ? ((ChatActivity) fragment).getInlineReturn() : 0;
        final String scheme = url == null ? null : Uri.parse(url).getScheme();
        if (Browser.isInternalUrl(url, null) || !ask || "mailto".equalsIgnoreCase(scheme)) {
            Browser.openUrl(fragment.getParentActivity(), Uri.parse(url), inlineReturn == 0, tryTelegraph, forceNotInternalForApps && checkInternalBotApp(url), progress, null, false, true);
        } else {
            String urlFinal;
            if (punycode) {
                try {
                    Uri uri = Uri.parse(url);
                    urlFinal = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
                } catch (Exception e) {
                    FileLog.e(e, false);
                    urlFinal = url;
                }
            } else {
                urlFinal = url;
            }
            Runnable open = () -> Browser.openUrl(fragment.getParentActivity(), Uri.parse(url), inlineReturn == 0, tryTelegraph, progress);
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
            builder.setTitle(LocaleController.getString(R.string.OpenUrlTitle));
            AlertDialog[] dialog = new AlertDialog[1];
            SpannableString link = new SpannableString(urlFinal);
            link.setSpan(new URLSpan(urlFinal) {
                @Override
                public void onClick(View widget) {
                    open.run();
                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                }
            }, 0, link.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.OpenUrlAlert2));
            int index = stringBuilder.toString().indexOf("%1$s");
            if (index >= 0) {
                stringBuilder.replace(index, index + 4, link);
            }
            builder.setMessage(stringBuilder);
            builder.setMessageTextViewClickable(false);
            builder.setPositiveButton(LocaleController.getString(R.string.Open), (dialogInterface, i) -> open.run());
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            fragment.showDialog(dialog[0] = builder.create());
        }
    }

    private static boolean checkInternalBotApp(String url) {
        String path = Uri.parse(url).getPath();
        return path.matches("^/\\w*/[^\\d]*(?:\\?startapp=.*?|)$");
    }

    public static AlertDialog createSupportAlert(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }
        final LinkSpanDrawable.LinksTextView message = new LinkSpanDrawable.LinksTextView(fragment.getParentActivity(), fragment.getResourceProvider());
        Spannable spanned = new SpannableString(Html.fromHtml(LocaleController.getString(R.string.AskAQuestionInfo).replace("\n", "<br>")));
        URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
        for (int i = 0; i < spans.length; i++) {
            URLSpan span = spans[i];
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            spanned.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL()) {
                @Override
                public void onClick(View widget) {
                    fragment.dismissCurrentDialog();
                    super.onClick(widget);
                }
            };
            spanned.setSpan(span, start, end, 0);
        }
        message.setText(spanned);
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        message.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink, resourcesProvider));
        message.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection, resourcesProvider));
        message.setPadding(dp(23), 0, dp(23), 0);
        message.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));

        AlertDialog.Builder builder1 = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
        builder1.setView(message);
        builder1.setTitle(LocaleController.getString(R.string.AskAQuestion));
        builder1.setPositiveButton(LocaleController.getString(R.string.AskButton), (dialogInterface, i) -> performAskAQuestion(fragment));
        builder1.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        return builder1.create();
    }

    private static void performAskAQuestion(BaseFragment fragment) {
        int currentAccount = fragment.getCurrentAccount();
        final SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        long uid = AndroidUtilities.getPrefIntOrLong(preferences, "support_id2", 0);
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
            final AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.setCanCancel(false);
            progressDialog.show();
            TLRPC.TL_help_getSupport req = new TLRPC.TL_help_getSupport();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    final TLRPC.TL_help_support res = (TLRPC.TL_help_support) response;
                    AndroidUtilities.runOnUIThread(() -> {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putLong("support_id2", res.user.id);
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
                        args.putLong("user_id", res.user.id);
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
            args.putLong("user_id", supportUser.id);
            fragment.presentFragment(new ChatActivity(args));
        }
    }

    public static void createImportDialogAlert(BaseFragment fragment, String title, String message, TLRPC.User user, TLRPC.Chat chat, Runnable onProcessRunnable) {
        if (fragment == null || fragment.getParentActivity() == null || chat == null && user == null) {
            return;
        }
        int account = fragment.getCurrentAccount();

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        long selfUserId = UserConfig.getInstance(account).getClientUserId();

        TextView messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context);
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(12));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(LocaleController.getString(R.string.ImportMessages));

        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        if (user != null) {
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageView.setImage(null, null, avatarDrawable, user);
            } else if (user.id == selfUserId) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                imageView.setImage(null, null, avatarDrawable, user);
            } else {
                avatarDrawable.setScaleSize(1f);
                avatarDrawable.setInfo(account, user);
                imageView.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            avatarDrawable.setInfo(account, chat);
            imageView.setForUserOrChat(chat, avatarDrawable);
        }

        messageTextView.setText(AndroidUtilities.replaceTags(message));
        /*if (chat != null) {
            if (TextUtils.isEmpty(title)) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToChatNoTitle", R.string.ImportToChatNoTitle, chat.title)));
            } else {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToChat", R.string.ImportToChat, title, chat.title)));
            }
        } else {
            if (TextUtils.isEmpty(title)) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToUserNoTitle", R.string.ImportToUserNoTitle, ContactsController.formatName(user.first_name, user.last_name))));
            } else {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ImportToUser", R.string.ImportToUser, title, ContactsController.formatName(user.first_name, user.last_name))));
            }
        }*/

        builder.setPositiveButton(LocaleController.getString(R.string.Import), (dialogInterface, i) -> {
            if (onProcessRunnable != null) {
                onProcessRunnable.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
    }

    public static void createBotLaunchAlert(BaseFragment fragment, TLRPC.User user, Runnable onConfirm, Runnable onDismiss) {
        Context context = fragment.getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinkSpanDrawable.LinksTextView messageTextView = new LinkSpanDrawable.LinksTextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(messageTextView);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context);
        builder.setCustomViewOffset(6);
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(18));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(user.first_name);
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 1));

        if (user != null) {
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageView.setImage(null, null, avatarDrawable, user);
            } else {
                avatarDrawable.setScaleSize(1f);
                avatarDrawable.setInfo(fragment.getCurrentAccount(), user);
                imageView.setForUserOrChat(user, avatarDrawable);
            }
        }

        builder.setPositiveButton(LocaleController.getString(R.string.Start), (dialogInterface, i) -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog, false, d -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        messageTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.BotWebViewStartPermission), () -> {
            if (dialog != null) {
                dialog.dismiss();
            }
            Browser.openUrl(context, getString(R.string.BotWebViewStartPermissionLink));
        }));
    }

    public static void createBotLaunchAlert(BaseFragment fragment, AtomicBoolean allowWrite, TLRPC.User user, Runnable loadBotSheet) {
        if (fragment == null) {
            return;
        }
        Context context = fragment.getContext();
        CheckBoxCell[] cell = new CheckBoxCell[1];
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinkSpanDrawable.LinksTextView messageTextView = new LinkSpanDrawable.LinksTextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(messageTextView);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell[0] != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell[0].getMeasuredHeight() + dp(7));
                }
            }
        };
        builder.setCustomViewOffset(6);
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(18));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setLines(1);
        titleView.setMaxLines(1);
        titleView.setSingleLine(true);
        titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(user.first_name);

        TextView subtitleView = new TextView(context);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setLines(1);
        subtitleView.setMaxLines(1);
        subtitleView.setSingleLine(true);
        subtitleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("user_id", user.id);
            if (fragment.getMessagesController().checkCanOpenChat(args, fragment)) {
                fragment.presentFragment(new ChatActivity(args));
            }

            builder.getDismissRunnable().run();
        });
        SpannableString ssb = SpannableString.valueOf(LocaleController.getString(R.string.MoreAboutThisBot) + "  ");
        ColoredImageSpan img = new ColoredImageSpan(R.drawable.attach_arrow_right);
        img.setTopOffset(1);
        img.setSize(dp(10));
        ssb.setSpan(img, ssb.length() - 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        subtitleView.setText(ssb);

        frameLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 0, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 28, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 1));

        if (allowWrite != null) {
            allowWrite.set(true);

            cell[0] = new CheckBoxCell(context, 1, fragment.getResourceProvider());
            cell[0].allowMultiline();
            cell[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            cell[0].setText(AndroidUtilities.replaceTags(LocaleController.formatString("OpenUrlOption2", R.string.OpenUrlOption2, UserObject.getUserName(user))), "", true, false);
            cell[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            cell[0].setChecked(true, false);
            frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell[0].setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                allowWrite.set(!allowWrite.get());
                cell1.setChecked(allowWrite.get(), true);
            });
        }

        if (user != null) {
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageView.setImage(null, null, avatarDrawable, user);
            } else {
                avatarDrawable.setScaleSize(1f);
                avatarDrawable.setInfo(fragment.getCurrentAccount(), user);
                imageView.setForUserOrChat(user, avatarDrawable);
            }
        }

        builder.setPositiveButton(LocaleController.getString(R.string.Start), (dialogInterface, i) -> loadBotSheet.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);

        messageTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.BotWebViewStartPermission), () -> {
            if (dialog != null) {
                dialog.dismiss();
            }
            Browser.openUrl(context, getString(R.string.BotWebViewStartPermissionLink));
        }));
    }

    public static void createClearOrDeleteDialogAlert(BaseFragment fragment, boolean clear, TLRPC.Chat chat, TLRPC.User user, boolean secret, boolean canDeleteHistory, MessagesStorage.BooleanCallback onProcessRunnable) {
        createClearOrDeleteDialogAlert(fragment, clear, false, false, chat, user, secret, false, canDeleteHistory, onProcessRunnable, null);
    }

    public static void createClearOrDeleteDialogAlert(BaseFragment fragment, boolean clear, TLRPC.Chat chat, TLRPC.User user, boolean secret, boolean checkDeleteForAll, boolean canDeleteHistory, MessagesStorage.BooleanCallback onProcessRunnable) {
        createClearOrDeleteDialogAlert(fragment, clear, chat != null && chat.creator, false, chat, user, secret, checkDeleteForAll, canDeleteHistory, onProcessRunnable, null);
    }

    public static void createClearOrDeleteDialogAlert(BaseFragment fragment, boolean clear, TLRPC.Chat chat, TLRPC.User user, boolean secret, boolean checkDeleteForAll, boolean canDeleteHistory, MessagesStorage.BooleanCallback onProcessRunnable, Theme.ResourcesProvider resourcesProvider) {
        createClearOrDeleteDialogAlert(fragment, clear, chat != null && chat.creator, false, chat, user, secret, checkDeleteForAll, canDeleteHistory, onProcessRunnable, resourcesProvider);
    }

    public static void createClearOrDeleteDialogAlert(BaseFragment fragment, boolean clear, boolean admin, boolean second, TLRPC.Chat chat, TLRPC.User user, boolean secret, boolean checkDeleteForAll, boolean canDeleteHistory, MessagesStorage.BooleanCallback onProcessRunnable, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null || (chat == null && user == null)) {
            return;
        }
        int account = fragment.getCurrentAccount();

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        long selfUserId = UserConfig.getInstance(account).getClientUserId();

        CheckBoxCell[] cell = new CheckBoxCell[1];

        TextView messageTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(messageTextView);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        boolean clearingCache = !canDeleteHistory && ChatObject.isChannel(chat) && ChatObject.isPublic(chat);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell[0] != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell[0].getMeasuredHeight() + dp(7));
                }
            }
        };
        builder.setCustomViewOffset(6);
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(18));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        if (clear) {
            if (clearingCache) {
                textView.setText(LocaleController.getString(R.string.ClearHistoryCache));
            } else {
                textView.setText(LocaleController.getString(R.string.ClearHistory));
            }
        } else {
            if (admin) {
                if (ChatObject.isChannel(chat)) {
                    if (chat.megagroup) {
                        textView.setText(LocaleController.getString(R.string.DeleteMegaMenu));
                    } else {
                        textView.setText(LocaleController.getString(R.string.ChannelDeleteMenu));
                    }
                } else {
                    textView.setText(LocaleController.getString(R.string.DeleteMegaMenu));
                }
            } else {
                if (chat != null) {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            textView.setText(LocaleController.getString(R.string.LeaveMegaMenu));
                        } else {
                            textView.setText(LocaleController.getString(R.string.LeaveChannelMenu));
                        }
                    } else {
                        textView.setText(LocaleController.getString(R.string.LeaveMegaMenu));
                    }
                } else {
                    textView.setText(LocaleController.getString(R.string.DeleteChatUser));
                }
            }
        }
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 1));

        boolean canRevokeInbox = user != null && !user.bot && user.id != selfUserId && MessagesController.getInstance(account).canRevokePmInbox;
        int revokeTimeLimit;
        if (user != null) {
            revokeTimeLimit = MessagesController.getInstance(account).revokeTimePmLimit;
        } else {
            revokeTimeLimit = MessagesController.getInstance(account).revokeTimeLimit;
        }
        boolean canDeleteInbox = !secret && user != null && canRevokeInbox && revokeTimeLimit == 0x7fffffff;
        final boolean[] deleteForAll = new boolean[1];
        boolean deleteChatForAll = false;

        boolean lastMessageIsJoined = false;
        ArrayList<MessageObject> dialogMessages = user != null ? MessagesController.getInstance(account).dialogMessage.get(user.id) : null;
        if (dialogMessages != null && dialogMessages.size() == 1 && dialogMessages.get(0) != null && dialogMessages.get(0).messageOwner != null && (dialogMessages.get(0).messageOwner.action instanceof TLRPC.TL_messageActionUserJoined || dialogMessages.get(0).messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp)) {
            lastMessageIsJoined = true;
        }

        if (user != null && user.bot) {
            cell[0] = new CheckBoxCell(context, 1, resourcesProvider);
            cell[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            cell[0].setText(LocaleController.getString(R.string.BlockBot), "", false, false);
            cell[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            cell[0].setChecked(deleteForAll[0] = true, false);
            frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell[0].setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                deleteForAll[0] = !deleteForAll[0];
                cell1.setChecked(deleteForAll[0], true);
            });
        } else if (!second && (secret && !clear || canDeleteInbox) && !UserObject.isDeleted(user) && !lastMessageIsJoined || (deleteChatForAll = checkDeleteForAll && !clear && chat != null && chat.creator)) {
            cell[0] = new CheckBoxCell(context, 1, resourcesProvider);
            cell[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (deleteChatForAll) {
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    cell[0].setText(LocaleController.getString(R.string.DeleteChannelForAll), "", false, false);
                } else {
                    cell[0].setText(LocaleController.getString(R.string.DeleteGroupForAll), "", false, false);
                }
            } else if (clear) {
                cell[0].setText(LocaleController.formatString("ClearHistoryOptionAlso", R.string.ClearHistoryOptionAlso, UserObject.getFirstName(user)), "", false, false);
            } else {
                cell[0].setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
            }
            cell[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell[0].setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                deleteForAll[0] = !deleteForAll[0];
                cell1.setChecked(deleteForAll[0], true);
            });
        }

        if (user != null) {
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageView.setImage(null, null, avatarDrawable, user);
            } else if (user.id == selfUserId) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                imageView.setImage(null, null, avatarDrawable, user);
            } else {
                avatarDrawable.setScaleSize(1f);
                avatarDrawable.setInfo(fragment.getCurrentAccount(), user);
                imageView.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            avatarDrawable.setInfo(fragment.getCurrentAccount(), chat);
            imageView.setForUserOrChat(chat, avatarDrawable);
        }

        if (second) {
            if (UserObject.isUserSelf(user)) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.DeleteAllMessagesSavedAlert)));
            } else {
                if (chat != null && ChatObject.isChannelAndNotMegaGroup(chat)) {
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.DeleteAllMessagesChannelAlert)));
                } else {
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.DeleteAllMessagesAlert)));
                }
            }
        } else {
            if (clear) {
                if (user != null) {
                    if (secret) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithSecretUser", R.string.AreYouSureClearHistoryWithSecretUser, UserObject.getUserName(user))));
                    } else {
                        if (user.id == selfUserId) {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AreYouSureClearHistorySavedMessages)));
                        } else {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithUser", R.string.AreYouSureClearHistoryWithUser, UserObject.getUserName(user))));
                        }
                    }
                } else {
                    if (!ChatObject.isChannel(chat) || chat.megagroup && !ChatObject.isPublic(chat)) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChat", R.string.AreYouSureClearHistoryWithChat, chat.title)));
                    } else if (chat.megagroup) {
                        messageTextView.setText(LocaleController.getString(R.string.AreYouSureClearHistoryGroup));
                    } else {
                        messageTextView.setText(LocaleController.getString(R.string.AreYouSureClearHistoryChannel));
                    }
                }
            } else {
                if (admin) {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            messageTextView.setText(LocaleController.getString(R.string.AreYouSureDeleteAndExit));
                        } else {
                            messageTextView.setText(LocaleController.getString(R.string.AreYouSureDeleteAndExitChannel));
                        }
                    } else {
                        messageTextView.setText(LocaleController.getString(R.string.AreYouSureDeleteAndExit));
                    }
                } else {
                    if (user != null) {
                        if (secret) {
                            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithSecretUser", R.string.AreYouSureDeleteThisChatWithSecretUser, UserObject.getUserName(user))));
                        } else {
                            if (user.id == selfUserId) {
                                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AreYouSureDeleteThisChatSavedMessages)));
                            } else {
                                if (user.bot && !user.support) {
                                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AreYouSureDeleteThisChatWithBotWithCheckmark, UserObject.getUserName(user))));
                                } else {
                                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureDeleteThisChatWithUser", R.string.AreYouSureDeleteThisChatWithUser, UserObject.getUserName(user))));
                                }
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
            actionText = LocaleController.getString(R.string.DeleteAll);
        } else {
            if (clear) {
                if (clearingCache) {
                    actionText = LocaleController.getString(R.string.ClearHistoryCache);
                } else {
                    actionText = LocaleController.getString(R.string.ClearForMe);
                }
            } else {
                if (admin) {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            actionText = LocaleController.getString(R.string.DeleteMega);
                        } else {
                            actionText = LocaleController.getString(R.string.ChannelDelete);
                        }
                    } else {
                        actionText = LocaleController.getString(R.string.DeleteMega);
                    }
                } else {
                    if (ChatObject.isChannel(chat)) {
                        if (chat.megagroup) {
                            actionText = LocaleController.getString(R.string.LeaveMegaMenu);
                        } else {
                            actionText = LocaleController.getString(R.string.LeaveChannelMenu);
                        }
                    } else {
                        actionText = LocaleController.getString(R.string.DeleteChatUser);
                    }
                }
            }
        }
        builder.setPositiveButton(actionText, (dialogInterface, i) -> {
            if (!clearingCache && !second && !secret) {
                if (UserObject.isUserSelf(user)) {
                    createClearOrDeleteDialogAlert(fragment, clear, admin, true, chat, user, false, checkDeleteForAll, canDeleteHistory, onProcessRunnable, resourcesProvider);
                    return;
                } else if (user != null && deleteForAll[0]) {
                    MessagesStorage.getInstance(fragment.getCurrentAccount()).getMessagesCount(user.id, (count) -> {
                        if (count >= 50) {
                            createClearOrDeleteDialogAlert(fragment, clear, admin, true, chat, user, false, checkDeleteForAll, canDeleteHistory, onProcessRunnable, resourcesProvider);
                        } else {
                            if (onProcessRunnable != null) {
                                onProcessRunnable.run(deleteForAll[0]);
                            }
                        }
                    });
                    return;
                }
            }
            if (onProcessRunnable != null) {
                onProcessRunnable.run(second || deleteForAll[0]);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void createClearDaysDialogAlert(BaseFragment fragment, int days, TLRPC.User user, TLRPC.Chat chat, boolean canDeleteHistory, MessagesStorage.BooleanCallback onProcessRunnable, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null || (user == null && chat == null)) {
            return;
        }
        int account = fragment.getCurrentAccount();

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        long selfUserId = UserConfig.getInstance(account).getClientUserId();

        CheckBoxCell[] cell = new CheckBoxCell[1];

        TextView messageTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(messageTextView);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell[0] != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell[0].getMeasuredHeight());
                }
            }
        };
        builder.setView(frameLayout);

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);

        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 11, 24, 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 48, 24, 18));


        if (days == -1) {
            textView.setText(LocaleController.formatString("ClearHistory", R.string.ClearHistory));
            if (user != null) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithUser", R.string.AreYouSureClearHistoryWithUser, UserObject.getUserName(user))));
            } else {
                if (canDeleteHistory) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChannel", R.string.AreYouSureClearHistoryWithChannel, chat.title)));
                    } else {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChat", R.string.AreYouSureClearHistoryWithChat, chat.title)));
                    }
                } else if (chat.megagroup) {
                    messageTextView.setText(LocaleController.getString(R.string.AreYouSureClearHistoryGroup));
                } else {
                    messageTextView.setText(LocaleController.getString(R.string.AreYouSureClearHistoryChannel));
                }
            }
        } else {
            textView.setText(LocaleController.formatPluralString("DeleteDays", days));
            messageTextView.setText(LocaleController.getString(R.string.DeleteHistoryByDaysMessage));
        }
        final boolean[] deleteForAll = new boolean[]{false};

        if (chat != null && canDeleteHistory && ChatObject.isPublic(chat)) {
            deleteForAll[0] = true;
        }
        if ((user != null && user.id != selfUserId) || (chat != null && canDeleteHistory && !ChatObject.isPublic(chat) && !ChatObject.isChannelAndNotMegaGroup(chat))) {
            cell[0] = new CheckBoxCell(context, 1, resourcesProvider);
            cell[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (chat != null) {
                cell[0].setText(LocaleController.getString(R.string.DeleteMessagesOptionAlsoChat), "", false, false);
            } else {
                cell[0].setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
            }

            cell[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            frameLayout.addView(cell[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));

            cell[0].setChecked(false, false);
            cell[0].setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                deleteForAll[0] = !deleteForAll[0];
                cell1.setChecked(deleteForAll[0], true);
            });
        }

        String deleteText = LocaleController.getString(R.string.Delete);
        if (chat != null && canDeleteHistory && ChatObject.isPublic(chat) && !ChatObject.isChannelAndNotMegaGroup(chat)) {
            deleteText = LocaleController.getString(R.string.ClearForAll);
        }
        builder.setPositiveButton(deleteText, (dialogInterface, i) -> {
            onProcessRunnable.run(deleteForAll[0]);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void createCallDialogAlert(BaseFragment fragment, TLRPC.User user, boolean videoCall) {
        if (fragment == null || fragment.getParentActivity() == null || user == null || UserObject.isDeleted(user) || UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId() == user.id) {
            return;
        }

        final int account = fragment.getCurrentAccount();
        final Context context = fragment.getParentActivity();
        final FrameLayout frameLayout = new FrameLayout(context);

        final String title;
        final String message;
        if (videoCall) {
            title = LocaleController.getString(R.string.VideoCallAlertTitle);
            message = LocaleController.formatString("VideoCallAlert", R.string.VideoCallAlert, UserObject.getUserName(user));
        } else {
            title = LocaleController.getString(R.string.CallAlertTitle);
            message = LocaleController.formatString("CallAlert", R.string.CallAlert, UserObject.getUserName(user));
        }

        TextView messageTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(messageTextView);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        messageTextView.setText(AndroidUtilities.replaceTags(message));

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(12));
        avatarDrawable.setScaleSize(1f);
        avatarDrawable.setInfo(fragment.getCurrentAccount(), user);

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        imageView.setForUserOrChat(user, avatarDrawable);
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(title);
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        AlertDialog dialog = new AlertDialog.Builder(context).setView(frameLayout)
                .setPositiveButton(LocaleController.getString(R.string.Call), (dialogInterface, i) -> {
                    final TLRPC.UserFull userFull = fragment.getMessagesController().getUserFull(user.id);
                    VoIPHelper.startCall(user, videoCall, userFull != null && userFull.video_calls_available, fragment.getParentActivity(), userFull, fragment.getAccountInstance());
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        fragment.showDialog(dialog);
    }

    public static void createChangeBioAlert(String currentBio, long peerId, Context context, int currentAccount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(peerId > 0 ? LocaleController.getString(R.string.UserBio) : LocaleController.getString(R.string.DescriptionPlaceholder));
        builder.setMessage(peerId > 0 ? LocaleController.getString(R.string.VoipGroupBioEditAlertText) : LocaleController.getString(R.string.DescriptionInfo));
        FrameLayout dialogView = new FrameLayout(context);
        dialogView.setClipChildren(false);

        if (peerId < 0) {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId);
            if (chatFull == null) {
                MessagesController.getInstance(currentAccount).loadFullChat(-peerId, ConnectionsManager.generateClassGuid(), true);
            }
        }

        NumberTextView checkTextView = new NumberTextView(context);
        EditText editTextView = new EditText(context);
        editTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        editTextView.setHint(peerId > 0 ? LocaleController.getString(R.string.UserBio) : LocaleController.getString(R.string.DescriptionPlaceholder));
        editTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editTextView.setBackground(Theme.createEditTextDrawable(context, true));

        editTextView.setMaxLines(4);
        editTextView.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);

        InputFilter[] inputFilters = new InputFilter[1];
        int maxSymbolsCount = peerId > 0 ? 70 : 255;
        inputFilters[0] = new CodepointsLengthInputFilter(maxSymbolsCount) {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                CharSequence result = super.filter(source, start, end, dest, dstart, dend);
                if (result != null && source != null && result.length() != source.length()) {
                    Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        v.vibrate(200);
                    }
                    AndroidUtilities.shakeView(checkTextView);
                }
                return result;
            }
        };
        editTextView.setFilters(inputFilters);

        checkTextView.setCenterAlign(true);
        checkTextView.setTextSize(15);
        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        checkTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dialogView.addView(checkTextView, LayoutHelper.createFrame(20, 20, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT, 0, 14, 21, 0));
        editTextView.setPadding(LocaleController.isRTL ? dp(24) : 0, dp(8), LocaleController.isRTL ? 0 : dp(24), dp(8));
        editTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                int count = maxSymbolsCount - Character.codePointCount(s, 0, s.length());
                if (count < 30) {
                    checkTextView.setNumber(count, checkTextView.getVisibility() == View.VISIBLE);
                    AndroidUtilities.updateViewVisibilityAnimated(checkTextView, true);
                } else {
                    AndroidUtilities.updateViewVisibilityAnimated(checkTextView, false);
                }
            }
        });
        AndroidUtilities.updateViewVisibilityAnimated(checkTextView, false, 0, false);
        editTextView.setText(currentBio);
        editTextView.setSelection(editTextView.getText().toString().length());

        builder.setView(dialogView);
        DialogInterface.OnClickListener onDoneListener = (dialogInterface, i) -> {
            if (peerId > 0) {
                final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
                final String newName = editTextView.getText().toString().replace("\n", " ").replaceAll(" +", " ").trim();
                if (userFull != null) {
                    String currentName = userFull.about;
                    if (currentName == null) {
                        currentName = "";
                    }
                    if (currentName.equals(newName)) {
                        AndroidUtilities.hideKeyboard(editTextView);
                        dialogInterface.dismiss();
                        return;
                    }
                    userFull.about = newName;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, peerId, userFull);
                }

                final TLRPC.TL_account_updateProfile req = new TLRPC.TL_account_updateProfile();
                req.about = newName;
                req.flags |= 4;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_BIO_CHANGED, peerId);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            } else {
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId);
                final String newAbout = editTextView.getText().toString();
                if (chatFull != null) {
                    String currentName = chatFull.about;
                    if (currentName == null) {
                        currentName = "";
                    }
                    if (currentName.equals(newAbout)) {
                        AndroidUtilities.hideKeyboard(editTextView);
                        dialogInterface.dismiss();
                        return;
                    }
                    chatFull.about = newAbout;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_BIO_CHANGED, peerId);
                MessagesController.getInstance(currentAccount).updateChatAbout(-peerId, newAbout, chatFull);
            }
            dialogInterface.dismiss();
        };
        builder.setPositiveButton(LocaleController.getString(R.string.Save), onDoneListener);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setOnPreDismissListener(dialogInterface -> AndroidUtilities.hideKeyboard(editTextView));
        dialogView.addView(editTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 12, 23, 21));
        editTextView.requestFocus();
        AndroidUtilities.showKeyboard(editTextView);

        AlertDialog dialog = builder.create();
        editTextView.setOnEditorActionListener((textView, i, keyEvent) -> {
            if ((i == EditorInfo.IME_ACTION_DONE || (peerId > 0 && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) && dialog.isShowing()) {
                onDoneListener.onClick(dialog, 0);
                return true;
            }
            return false;
        });

        dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
        dialog.show();
        dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));

    }

    public static void createChangeNameAlert(long peerId, Context context, int currentAccount) {
        String currentName;
        String currentLastName = null;
        if (DialogObject.isUserDialog(peerId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
            currentName = user.first_name;
            currentLastName = user.last_name;
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
            currentName = chat.title;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(peerId > 0 ? LocaleController.getString(R.string.VoipEditName) : LocaleController.getString(R.string.VoipEditTitle));
        LinearLayout dialogView = new LinearLayout(context);
        dialogView.setOrientation(LinearLayout.VERTICAL);

        EditText firstNameEditTextView = new EditText(context);
        firstNameEditTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        firstNameEditTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        firstNameEditTextView.setMaxLines(1);
        firstNameEditTextView.setLines(1);
        firstNameEditTextView.setSingleLine(true);
        firstNameEditTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        firstNameEditTextView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameEditTextView.setImeOptions(peerId > 0 ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        firstNameEditTextView.setHint(peerId > 0 ? LocaleController.getString(R.string.FirstName) : LocaleController.getString(R.string.VoipEditTitleHint));
        firstNameEditTextView.setBackground(Theme.createEditTextDrawable(context, true));
        firstNameEditTextView.setPadding(0, dp(8), 0, dp(8));
        firstNameEditTextView.requestFocus();

        EditText lastNameEditTextView = null;
        if (peerId > 0) {
            lastNameEditTextView = new EditText(context);
            lastNameEditTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            lastNameEditTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            lastNameEditTextView.setMaxLines(1);
            lastNameEditTextView.setLines(1);
            lastNameEditTextView.setSingleLine(true);
            lastNameEditTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            lastNameEditTextView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            lastNameEditTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            lastNameEditTextView.setHint(LocaleController.getString(R.string.LastName));
            lastNameEditTextView.setBackground(Theme.createEditTextDrawable(context, true));
            lastNameEditTextView.setPadding(0, dp(8), 0, dp(8));
        }

        AndroidUtilities.showKeyboard(firstNameEditTextView);

        dialogView.addView(firstNameEditTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 12, 23, 21));
        if (lastNameEditTextView != null) {
            dialogView.addView(lastNameEditTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 12, 23, 21));
        }

        firstNameEditTextView.setText(currentName);
        firstNameEditTextView.setSelection(firstNameEditTextView.getText().toString().length());

        if (lastNameEditTextView != null) {
            lastNameEditTextView.setText(currentLastName);
            lastNameEditTextView.setSelection(lastNameEditTextView.getText().toString().length());
        }


        builder.setView(dialogView);
        EditText finalLastNameEditTextView = lastNameEditTextView;
        DialogInterface.OnClickListener onDoneListener = (dialogInterface, i) -> {
            if (firstNameEditTextView.getText() == null) {
                return;
            }
            if (peerId > 0) {
                TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(peerId);

                String newFirst = firstNameEditTextView.getText().toString();
                String newLast = finalLastNameEditTextView.getText().toString();
                String oldFirst = currentUser.first_name;
                String oldLast = currentUser.last_name;
                if (oldFirst == null) {
                    oldFirst = "";
                }
                if (oldLast == null) {
                    oldLast = "";
                }
                if (oldFirst.equals(newFirst) && oldLast.equals(newLast)) {
                    dialogInterface.dismiss();
                    return;
                }
                TLRPC.TL_account_updateProfile req = new TLRPC.TL_account_updateProfile();
                req.flags = 3;
                currentUser.first_name = req.first_name = newFirst;
                currentUser.last_name = req.last_name = newLast;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                if (user != null) {
                    user.first_name = req.first_name;
                    user.last_name = req.last_name;
                }
                UserConfig.getInstance(currentAccount).saveConfig(true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                });
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_NAME_CHANGED, peerId);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                String newFirst = firstNameEditTextView.getText().toString();
                if (chat.title != null && chat.title.equals(newFirst)) {
                    dialogInterface.dismiss();
                    return;
                }
                chat.title = newFirst;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT_NAME);
                MessagesController.getInstance(currentAccount).changeChatTitle(-peerId, newFirst);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_NAME_CHANGED, peerId);
            }
            dialogInterface.dismiss();
        };
        builder.setPositiveButton(LocaleController.getString(R.string.Save), onDoneListener);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setOnPreDismissListener(dialogInterface -> {
            AndroidUtilities.hideKeyboard(firstNameEditTextView);
            AndroidUtilities.hideKeyboard(finalLastNameEditTextView);
        });
        AlertDialog dialog = builder.create();

        dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
        dialog.show();
        dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));

        TextView.OnEditorActionListener actionListener = (textView, i, keyEvent) -> {
            if ((i == EditorInfo.IME_ACTION_DONE || keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) && dialog.isShowing()) {
                onDoneListener.onClick(dialog, 0);
                return true;
            }
            return false;
        };
        if (lastNameEditTextView != null) {
            lastNameEditTextView.setOnEditorActionListener(actionListener);
        } else {
            firstNameEditTextView.setOnEditorActionListener(actionListener);
        }

    }

    public static void showChatWithAdmin(BaseFragment fragment, TLRPC.User user, String chatWithAdmin, boolean isChannel, int chatWithAdminDate) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(fragment.getParentActivity());
        builder.setTitle(isChannel ? LocaleController.getString(R.string.ChatWithAdminChannelTitle) : LocaleController.getString(R.string.ChatWithAdminGroupTitle), true);
        LinearLayout linearLayout = new LinearLayout(fragment.getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView messageTextView = new TextView(fragment.getParentActivity());
        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 21, 0, 21, 8));
        messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ChatWithAdminMessage", R.string.ChatWithAdminMessage, chatWithAdmin, LocaleController.formatDateAudio(chatWithAdminDate, false))));


        TextView buttonTextView = new TextView(fragment.getParentActivity());
        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setText(LocaleController.getString(R.string.IUnderstand));

        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 12, 16, 8));

        builder.setCustomView(linearLayout);
        BottomSheet bottomSheet = builder.show();

        buttonTextView.setOnClickListener((v) -> {
            bottomSheet.dismiss();
        });
    }

    public static void showDiscardTopicDialog(BaseFragment baseFragment, Theme.ResourcesProvider resourcesProvider, Runnable onDiscard) {
        if (baseFragment == null || baseFragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getParentActivity(), resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.DiscardTopic));
        builder.setMessage(LocaleController.getString(R.string.DiscardTopicMessage));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(LocaleController.getString(R.string.Discard), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                onDiscard.run();
            }
        });
        baseFragment.showDialog(builder.create());
    }

    public static void createContactInviteDialog(BaseFragment parentFragment, String fisrtName, String lastName, String phone) {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ContactNotRegisteredTitle));
        builder.setMessage(LocaleController.formatString("ContactNotRegistered", R.string.ContactNotRegistered, ContactsController.formatName(fisrtName, lastName)));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Invite), (dialog, which) -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", phone, null));
                intent.putExtra("sms_body", ContactsController.getInstance(parentFragment.getCurrentAccount()).getInviteText(1));
                parentFragment.getParentActivity().startActivityForResult(intent, 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        parentFragment.showDialog(builder.create());
    }

    public static ActionBarPopupWindow createSimplePopup(BaseFragment fragment, View popupView, View anhcorView, float x, float y) {
        if (fragment == null || anhcorView == null || popupView == null) {
            return null;
        }
        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(popupView, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupView.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        float viewX = 0, viewY = 0;
        View child = anhcorView;
        while (child != anhcorView.getRootView()) {
            viewX += child.getX();
            viewY += child.getY();
            child = (View) child.getParent();
            if (child == null) {
                break;
            }
        }
        popupWindow.showAtLocation(anhcorView.getRootView(), 0, (int) (viewX + x - popupView.getMeasuredWidth() / 2f), (int) (viewY + y - popupView.getMeasuredHeight() / 2f));
        popupWindow.dimBehind();
        return popupWindow;
    }

    public static void checkRestrictedInviteUsers(int currentAccount, TLRPC.Chat currentChat, TLRPC.TL_messages_invitedUsers invitedUsers) {
        if (invitedUsers == null || invitedUsers.missing_invitees.isEmpty() || currentChat == null) {
            return;
        }
        ArrayList<TLRPC.User> users = new ArrayList<>();
        ArrayList<Long> pmLockedUsers = new ArrayList<>();
        ArrayList<Long> inviteLockedUsers = new ArrayList<>();
        for (TLRPC.TL_missingInvitee missing_invitee : invitedUsers.missing_invitees) {
            TLRPC.User user = null;
            if (invitedUsers.updates != null) {
                for (int i = 0; i < invitedUsers.updates.users.size(); ++i) {
                    TLRPC.User u = invitedUsers.updates.users.get(i);
                    if (u.id == missing_invitee.user_id) {
                        user = u;
                        break;
                    }
                }
            }
            if (user == null) {
                user = MessagesController.getInstance(currentAccount).getUser(missing_invitee.user_id);
            }
            if (user == null) {
                continue;
            }
            users.add(user);
            if (missing_invitee.premium_required_for_pm) {
                pmLockedUsers.add(user.id);
            }
            if (missing_invitee.premium_would_allow_invite) {
                inviteLockedUsers.add(user.id);
            }
        }
        if (users.isEmpty())
            return;
        AndroidUtilities.runOnUIThread(() -> {
            if (!LaunchActivity.isActive) return;
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null && lastFragment.getParentActivity() != null) {
                LimitReachedBottomSheet restrictedUsersBottomSheet = new LimitReachedBottomSheet(lastFragment, lastFragment.getParentActivity(), LimitReachedBottomSheet.TYPE_ADD_MEMBERS_RESTRICTED, currentAccount, null);
                restrictedUsersBottomSheet.setRestrictedUsers(currentChat, users, pmLockedUsers, inviteLockedUsers);
                restrictedUsersBottomSheet.show();
            }
        }, 200);
    }

    public interface BlockDialogCallback {
        void run(boolean report, boolean delete);
    };

    public static void createBlockDialogAlert(BaseFragment fragment, int count, boolean reportSpam, TLRPC.User user, BlockDialogCallback onProcessRunnable) {
        if (fragment == null || fragment.getParentActivity() == null || count == 1 && user == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        CheckBoxCell[] cell = new CheckBoxCell[2];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setView(linearLayout);

        String actionText;
        if (count == 1) {
            String name = ContactsController.formatName(user.first_name, user.last_name);
            builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, name));
            actionText = LocaleController.getString(R.string.BlockUser);
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUserMessage", R.string.BlockUserMessage, name)));
        } else {
            builder.setTitle(LocaleController.formatString("BlockUserTitle", R.string.BlockUserTitle, LocaleController.formatPluralString("UsersCountTitle", count)));
            actionText = LocaleController.getString(R.string.BlockUsers);
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BlockUsersMessage", R.string.BlockUsersMessage, LocaleController.formatPluralString("UsersCount", count))));
        }

        final boolean[] checks = new boolean[]{true, true};

        for (int a = 0; a < cell.length; a++) {
            if (a == 0 && !reportSpam) {
                continue;
            }
            int num = a;
            cell[a] = new CheckBoxCell(context, 1);
            cell[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (a == 0) {
                cell[a].setText(LocaleController.getString(R.string.ReportSpamTitle), "", true, false);
            } else {
                cell[a].setText(count == 1 ? LocaleController.getString(R.string.DeleteThisChatBothSides) : LocaleController.getString(R.string.DeleteTheseChatsBothSides), "", true, false);
            }
            cell[a].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            linearLayout.addView(cell[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            cell[a].setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                checks[num] = !checks[num];
                cell1.setChecked(checks[num], true);
            });
        }

        builder.setPositiveButton(actionText, (dialogInterface, i) -> onProcessRunnable.run(checks[0], checks[1]));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public interface DatePickerDelegate {
        void didSelectDate(int year, int month, int dayOfMonth);
    }

    public static BottomSheet createTimezonePickerDialog(Context context, String title, String timezoneId, final Utilities.Callback<String> whenPicked) {
        final int currentAccount = UserConfig.selectedAccount;
        if (TimezonesController.getInstance(currentAccount).getTimezones().isEmpty()) {
            return null;
        }

        ArrayList<TLRPC.TL_timezone> timezones = new ArrayList<>(TimezonesController.getInstance(currentAccount).getTimezones());
        Collections.sort(timezones, (a, b) -> a.utc_offset - b.utc_offset);

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors();

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, null);
        builder.setApplyBottomPadding(false);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1f);

        final NumberPicker picker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return TimezonesController.getInstance(currentAccount).getTimezoneName(timezones.get(value), true);
            }
        };
        picker.setAllItemsCount(24);
        picker.setItemCount(8);
        picker.setTextColor(datePickerColors.textColor);
        picker.setGravity(Gravity.CENTER);
        picker.setMinValue(0);
        picker.setMaxValue(timezones.size() - 1);
        for (int i = 0; i < timezones.size(); ++i) {
            if (TextUtils.equals(timezoneId, timezones.get(i).id)) {
                picker.setValue(i);
                break;
            }
        }

        linearLayout.addView(picker, LayoutHelper.createLinear(0, 54 * 8, 1f));
        picker.setFormatter(value -> {
            return TimezonesController.getInstance(currentAccount).getTimezoneName(timezones.get(value), true);
        });

        LinearLayout container = new LinearLayout(context) {
            boolean ignoreLayout = false;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                ignoreLayout = true;
                picker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * 8;
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

        FrameLayout titleLayout = new FrameLayout(context);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        BottomSheet[] sheet = new BottomSheet[1];

        ButtonWithCounterView button = new ButtonWithCounterView(context, null);
        button.setText(LocaleController.getString(R.string.Select), false);
        button.setOnClickListener(v -> sheet[0].dismiss());
        container.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 12, 16, 12));

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setOnDismissListener(dialog -> {
            whenPicked.run(timezones.get(picker.getValue()).id);
        });
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);

        return sheet[0] = builder.create();
    }

    public static BottomSheet createTimePickerDialog(Context context, String title, int time, int minTime, int maxTime, final Utilities.Callback<Integer> whenPicked) {
        if (context == null) {
            return null;
        }

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors();

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, null);
        builder.setApplyBottomPadding(false);

        final NumberPicker hourPicker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Hours", value);
            }
        };

        LinearLayout linearLayout = new LinearLayout(context) {
            private final Text separatorText = new Text(":", 18);
            private boolean isAM;
            private Text ampmText;
            @Override
            protected void dispatchDraw(Canvas canvas) {
                separatorText.draw(canvas, (getWidth() - separatorText.getCurrentWidth()) / 2f, getHeight() / 2f, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 1f);
                if (!LocaleController.is24HourFormat) {
                    final boolean isAM = (hourPicker.getValue() % 24) < 12;
                    if (this.isAM != isAM || ampmText == null) {
                        this.isAM = isAM;
                        ampmText = new Text(isAM ? "AM" : "PM", 18);
                    }
                    ampmText.draw(canvas, getWidth() / 2f + dp(43), getHeight() / 2f + dp(1), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 1f);
                }
                super.dispatchDraw(canvas);
            }
        };
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1f);
        hourPicker.setAllItemsCount(24);
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setGravity(Gravity.RIGHT);
        hourPicker.setTextOffset(-dp(12));

        final NumberPicker minutePicker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Minutes", value);
            }
        };
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setAllItemsCount(60);
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setGravity(Gravity.LEFT);
        minutePicker.setTextOffset(dp(12));
        final Utilities.Callback<Boolean> checkValue = (byChange) -> {
            int minTimeMinutes = minTime % 60;
            int minTimeHours = (minTime - minTimeMinutes) / 60;
            int maxTimeMinutes = maxTime % 60;
            int maxTimeHours = (maxTime - maxTimeMinutes) / 60;
            if (maxTimeMinutes == 0 && maxTimeHours > 0) {
                maxTimeHours--;
                maxTimeMinutes = 59;
            }

            int hour, minutes;
            if (byChange) {
                hour = hourPicker.getValue();
                minutes = minutePicker.getValue();
            } else {
                minutes = time % 60;
                hour = (time - minutes) / 60;
                if (hour == 24) {
                    hour--;
                    minutes = 59;
                }
            }
            hourPicker.setMinValue(minTimeHours);
            hourPicker.setMaxValue(maxTimeHours);
            if (hour > maxTimeHours) {
                hourPicker.setValue(hour = maxTimeHours);
            } else if (hour < minTimeHours) {
                hourPicker.setValue(hour = minTimeHours);
            }
            if (hour <= minTimeHours) {
                minutePicker.setMinValue(minTimeMinutes);
                minutePicker.setMaxValue(minTimeHours == maxTimeHours ? maxTimeMinutes : 59);
            } else if (hour >= maxTimeHours) {
                minutePicker.setMinValue(minTimeHours == maxTimeHours ? minTimeMinutes : 0);
                minutePicker.setMaxValue(maxTimeMinutes);
            } else {
                if (minTimeHours == maxTimeHours) {
                    minutePicker.setMinValue(minTimeMinutes);
                    minutePicker.setMaxValue(maxTimeMinutes);
                } else {
                    minutePicker.setMinValue(0);
                    minutePicker.setMaxValue(59);
                }
            }
            if (minutes > minutePicker.getMaxValue()) {
                minutePicker.setValue(minutes = minutePicker.getMaxValue());
            } else if (minutes < minutePicker.getMinValue()) {
                minutePicker.setValue(minutes = minutePicker.getMinValue());
            }
            if (!byChange) {
                hourPicker.setValue(hour);
                minutePicker.setValue(minutes);
            }
            linearLayout.invalidate();
        };

        linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        hourPicker.setFormatter(value -> {
            int h = value % (LocaleController.is24HourFormat ? 24 : 12);
            if ((value % 12) == 0 && !LocaleController.is24HourFormat) {
                h = 12;
            }
            String str = String.format("%02d", h);
            if (value >= 24) {
                return LocaleController.formatString(R.string.BusinessHoursNextDayPicker, str);
            }
            return str;
        });
        hourPicker.setOnValueChangedListener((p, v, o) -> checkValue.run(true));

        linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        minutePicker.setFormatter(value -> String.format("%02d", value));
        minutePicker.setOnValueChangedListener((p, v, o) -> checkValue.run(true));
        checkValue.run(false);

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
                hourPicker.setItemCount(count);
                minutePicker.setItemCount(count);
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        BottomSheet[] sheet = new BottomSheet[1];

        ButtonWithCounterView button = new ButtonWithCounterView(context, null);
        button.setText(LocaleController.getString(R.string.Select), false);
        button.setOnClickListener(v -> sheet[0].dismiss());
        container.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 12, 16, 12));

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setOnDismissListener(dialog -> {
            whenPicked.run(hourPicker.getValue() * 60 + minutePicker.getValue());
        });
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);

        return sheet[0] = builder.create();
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
        builder.setPositiveButton(LocaleController.getString(R.string.Set), (dialog, which) -> {
            if (checkMinDate) {
                checkPickerDate(dayPicker, monthPicker, yearPicker);
            }
            datePickerDelegate.didSelectDate(yearPicker.getValue(), monthPicker.getValue(), dayPicker.getValue());
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        return builder;
    }

    public static boolean checkScheduleDate(TextView button, TextView infoText, int type, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        return checkScheduleDate(button, infoText, 0, type, dayPicker, hourPicker, minutePicker);
    }

    public static boolean checkScheduleDate(TextView button, TextView infoText, long maxDate, int type, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        int day = dayPicker.getValue();
        int hour = hourPicker.getValue();
        int minute = minutePicker.getValue();
        Calendar calendar = Calendar.getInstance();

        long systemTime = System.currentTimeMillis();
        calendar.setTimeInMillis(systemTime);
        int currentYear = calendar.get(Calendar.YEAR);
        int currentDay = calendar.get(Calendar.DAY_OF_YEAR);

        int maxDay = 0, maxHour = 0, maxMinute = 0;
        if (maxDate > 0) {
            maxDate *= 1000;
            calendar.setTimeInMillis(systemTime + maxDate);
            calendar.set(Calendar.HOUR_OF_DAY, maxHour = 23);
            calendar.set(Calendar.MINUTE, maxMinute = 59);
            calendar.set(Calendar.SECOND, 59);
            maxDay = 7; // ???
            maxDate = calendar.getTimeInMillis();
        }

        calendar.setTimeInMillis(systemTime + 60000L);
        int minDay = 1;
        int minHour = calendar.get(Calendar.HOUR_OF_DAY);
        int minMinute = calendar.get(Calendar.MINUTE);

        calendar.setTimeInMillis(System.currentTimeMillis() + (long) day * 24 * 3600 * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        long currentTime = calendar.getTimeInMillis();
        calendar.setTimeInMillis(currentTime);

        dayPicker.setMinValue(0);
        if (maxDate > 0) {
            dayPicker.setMaxValue(maxDay);
        }
        day = dayPicker.getValue();

        hourPicker.setMinValue(day == 0 ? minHour : 0);
        if (maxDate > 0) {
            hourPicker.setMaxValue(day == maxDay ? maxHour : 23);
        }
        hour = hourPicker.getValue();

        minutePicker.setMinValue(day == 0 && hour == minHour ? minMinute : 0);
        if (maxDate > 0) {
            minutePicker.setMaxValue(day == maxDay && hour == maxHour ? maxMinute : 59);
        }
        minute = minutePicker.getValue();
        if (currentTime <= systemTime + 60000L) {
            calendar.setTimeInMillis(systemTime + 60000L);
        } else if (maxDate > 0 && currentTime > maxDate) {
            calendar.setTimeInMillis(maxDate);
        }
        int selectedYear = calendar.get(Calendar.YEAR);

        calendar.setTimeInMillis(System.currentTimeMillis() + (long) day * 24 * 3600 * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        long time = calendar.getTimeInMillis();
        if (button != null) {
            int num;
            if (day == 0) {
                num = 0;
            } else if (currentYear == selectedYear) {
                num = 1;
            } else {
                num = 2;
            }
            if (type == 1) {
                num += 3;
            } else if (type == 2) {
                num += 6;
            } else if (type == 3) {
                num += 9;
            }
            button.setText(LocaleController.getInstance().getFormatterScheduleSend(num).format(time));
        }
        if (infoText != null) {
            int diff = (int) ((time - systemTime) / 1000);
            String t;
            if (diff > 24 * 60 * 60) {
                t = LocaleController.formatPluralString("DaysSchedule", Math.round(diff / (24 * 60 * 60.0f)));
            } else if (diff >= 60 * 60) {
                t = LocaleController.formatPluralString("HoursSchedule", Math.round(diff / (60 * 60.0f)));
            } else if (diff >= 60) {
                t = LocaleController.formatPluralString("MinutesSchedule", Math.round(diff / 60.0f));
            } else {
                t = LocaleController.formatPluralString("SecondsSchedule", diff);
            }
            if (infoText.getTag() != null) {
                infoText.setText(LocaleController.formatString("VoipChannelScheduleInfo", R.string.VoipChannelScheduleInfo, t));
            } else {
                infoText.setText(LocaleController.formatString("VoipGroupScheduleInfo", R.string.VoipGroupScheduleInfo, t));
            }
        }
        return currentTime - systemTime > 60000L;
    }

    public interface ScheduleDatePickerDelegate {
        void didSelectDate(boolean notify, int scheduleDate);
    }

    public static class ScheduleDatePickerColors {

        public final int textColor;
        public final int backgroundColor;

        public final int iconColor;
        public final int iconSelectorColor;

        public final int subMenuTextColor;
        public final int subMenuBackgroundColor;
        public final int subMenuSelectorColor;

        public final int buttonTextColor;
        public final int buttonBackgroundColor;
        public final int buttonBackgroundPressedColor;

        private ScheduleDatePickerColors() {
            this(null);
        }

        public ScheduleDatePickerColors(Theme.ResourcesProvider rp) {
            this(rp != null ? rp.getColorOrDefault(Theme.key_dialogTextBlack) : Theme.getColor(Theme.key_dialogTextBlack),
                    rp != null ? rp.getColorOrDefault(Theme.key_dialogBackground) : Theme.getColor(Theme.key_dialogBackground),
                    rp != null ? rp.getColorOrDefault(Theme.key_sheet_other) : Theme.getColor(Theme.key_sheet_other),
                    rp != null ? rp.getColorOrDefault(Theme.key_player_actionBarSelector) : Theme.getColor(Theme.key_player_actionBarSelector),
                    rp != null ? rp.getColorOrDefault(Theme.key_actionBarDefaultSubmenuItem) : Theme.getColor(Theme.key_actionBarDefaultSubmenuItem),
                    rp != null ? rp.getColorOrDefault(Theme.key_actionBarDefaultSubmenuBackground) : Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground),
                    rp != null ? rp.getColorOrDefault(Theme.key_listSelector) : Theme.getColor(Theme.key_listSelector),
                    rp != null ? rp.getColorOrDefault(Theme.key_featuredStickers_buttonText) : Theme.getColor(Theme.key_featuredStickers_buttonText),
                    rp != null ? rp.getColorOrDefault(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_featuredStickers_addButton),
                    rp != null ? rp.getColorOrDefault(Theme.key_featuredStickers_addButtonPressed) : Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
        }

        public ScheduleDatePickerColors(int textColor, int backgroundColor, int iconColor, int iconSelectorColor, int subMenuTextColor, int subMenuBackgroundColor, int subMenuSelectorColor) {
            this(textColor, backgroundColor, iconColor, iconSelectorColor, subMenuTextColor, subMenuBackgroundColor, subMenuSelectorColor, Theme.getColor(Theme.key_featuredStickers_buttonText), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
        }

        public ScheduleDatePickerColors(int textColor, int backgroundColor, int iconColor, int iconSelectorColor, int subMenuTextColor, int subMenuBackgroundColor, int subMenuSelectorColor, int buttonTextColor, int buttonBackgroundColor, int buttonBackgroundPressedColor) {
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.iconColor = iconColor;
            this.iconSelectorColor = iconSelectorColor;
            this.subMenuTextColor = subMenuTextColor;
            this.subMenuBackgroundColor = subMenuBackgroundColor;
            this.subMenuSelectorColor = subMenuSelectorColor;
            this.buttonTextColor = buttonTextColor;
            this.buttonBackgroundColor = buttonBackgroundColor;
            this.buttonBackgroundPressedColor = buttonBackgroundPressedColor;
        }
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate, Theme.ResourcesProvider resourcesProvider) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null, resourcesProvider);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate, final ScheduleDatePickerColors datePickerColors) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null, datePickerColors, null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable, Theme.ResourcesProvider resourcesProvider) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, cancelRunnable, resourcesProvider);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable) {
        return createScheduleDatePickerDialog(context, dialogId, currentDate, datePickerDelegate, cancelRunnable, new ScheduleDatePickerColors(), null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable, Theme.ResourcesProvider resourcesProvider) {
        return createScheduleDatePickerDialog(context, dialogId, currentDate, datePickerDelegate, cancelRunnable, new ScheduleDatePickerColors(resourcesProvider), resourcesProvider);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable, final ScheduleDatePickerColors datePickerColors, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) {
            return null;
        }

        long selfUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context, resourcesProvider);
        dayPicker.setTextColor(datePickerColors.textColor);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Hours", value);
            }
        };
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setAllItemsCount(24);
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setTextOffset(-dp(10));
        final NumberPicker minutePicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Minutes", value);
            }
        };
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setAllItemsCount(60);
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setTextOffset(-dp(34));

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
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        if (dialogId == selfUserId) {
            titleView.setText(LocaleController.getString(R.string.SetReminder));
        } else {
            titleView.setText(LocaleController.getString(R.string.ScheduleMessage));
        }
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        if (DialogObject.isUserDialog(dialogId) && dialogId != selfUserId) {
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
            if (user != null && !user.bot && user.status != null && user.status.expires > 0) {
                String name = UserObject.getFirstName(user);
                if (name.length() > 10) {
                    name = name.substring(0, 10) + "\u2026";
                }

                ActionBarMenuItem optionsButton = new ActionBarMenuItem(context, null, 0, datePickerColors.iconColor, false, resourcesProvider);
                optionsButton.setLongClickEnabled(false);
                optionsButton.setSubMenuOpenSide(2);
                optionsButton.setIcon(R.drawable.ic_ab_other);
                optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(datePickerColors.iconSelectorColor, 1));
                titleLayout.addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 8, 5, 0));
                optionsButton.addSubItem(1, LocaleController.formatString("ScheduleWhenOnline", R.string.ScheduleWhenOnline, name));
                optionsButton.setOnClickListener(v -> {
                    optionsButton.toggleSubMenu();
                    optionsButton.setPopupItemsColor(datePickerColors.subMenuTextColor, false);
                    optionsButton.setupPopupRadialSelectors(datePickerColors.subMenuSelectorColor);
                    optionsButton.redrawPopup(datePickerColors.subMenuBackgroundColor);
                });
                optionsButton.setDelegate(id -> {
                    if (id == 1) {
                        datePickerDelegate.didSelectDate(true, 0x7ffffffe);
                        builder.getDismissRunnable().run();
                    }
                });
                optionsButton.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
            }
        }

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0);
        dayPicker.setMaxValue(365);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return LocaleController.getString(R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                if (year == currentYear) {
                    return (
                        LocaleController.getInstance().getFormatterWeek().format(date) +
                        ", " +
                        LocaleController.getInstance().getFormatterScheduleDay().format(date)
                    );
                } else {
                    return LocaleController.getInstance().getFormatterScheduleYear().format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            checkScheduleDate(buttonTextView, null, selfUserId == dialogId ? 1 : 0, dayPicker, hourPicker, minutePicker);
        };
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

        if (currentDate > 0 && currentDate != 0x7FFFFFFE) {
            currentDate *= 1000;
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
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

        checkScheduleDate(buttonTextView, null, selfUserId == dialogId ? 1 : 0, dayPicker, hourPicker, minutePicker);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(datePickerColors.buttonBackgroundColor, 8));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            canceled[0] = false;
            boolean setSeconds = checkScheduleDate(null, null, selfUserId == dialogId ? 1 : 0, dayPicker, hourPicker, minutePicker);
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
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setOnDismissListener(dialog -> {
            if (cancelRunnable != null && canceled[0]) {
                cancelRunnable.run();
            }
        });
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        return builder;
    }

    public static BottomSheet.Builder createDatePickerDialog(Context context, String title, String button, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate) {
        if (context == null) {
            return null;
        }

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors();
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context);
        dayPicker.setTextColor(datePickerColors.textColor);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Hours", value);
            }
        };
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setTextOffset(-dp(10));
        final NumberPicker minutePicker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Minutes", value);
            }
        };
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setTextOffset(-dp(34));

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
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(title);

        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0);
        dayPicker.setMaxValue(365);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return LocaleController.getString(R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                if (year == currentYear) {
                    return LocaleController.getInstance().getFormatterScheduleDay().format(date);
                } else {
                    return LocaleController.getInstance().getFormatterScheduleYear().format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker);
        };
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

        if (currentDate > 0 && currentDate != 0x7FFFFFFE) {
            currentDate *= 1000;
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            int days = (int) ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
            calendar.setTimeInMillis(currentDate);
            if (days >= 0) {
                minutePicker.setValue(calendar.get(Calendar.MINUTE));
                hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
                dayPicker.setValue(days);
            }
        }

        checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(8), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor));
        buttonTextView.setText(button);
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            boolean setSeconds = checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker);
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
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        return builder;
    }

    public static BottomSheet.Builder createBirthdayPickerDialog(Context context, String title, String button, TLRPC.TL_birthday currentBirthday, final Utilities.Callback<TLRPC.TL_birthday> whenSelectedBirthday, Runnable addPrivacyText, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context, resourcesProvider);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker monthPicker = new NumberPicker(context, resourcesProvider);
        monthPicker.setItemCount(5);
        monthPicker.setTextOffset(-dp(10));
        final NumberPicker yearPicker = new NumberPicker(context, resourcesProvider);
        yearPicker.setItemCount(5);
        yearPicker.setTextOffset(-dp(24));

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
                monthPicker.setItemCount(count);
                yearPicker.setItemCount(count);
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                monthPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                yearPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setGravity(Gravity.CENTER);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        Calendar calendar = Calendar.getInstance();
        int minYear = calendar.get(Calendar.YEAR) - 149;
        calendar.setTimeInMillis(System.currentTimeMillis());
        int thisDay = calendar.get(Calendar.DAY_OF_MONTH);
        int thisMonth = calendar.get(Calendar.MONTH);
        int thisYear = calendar.get(Calendar.YEAR);
        final int undefinedYear = thisYear + 1;

        Runnable check = () -> {
            if (yearPicker.getValue() == undefinedYear) {
                dayPicker.setMinValue(1);
                try {
                    dayPicker.setMaxValue(YearMonth.of(2024, 1 + monthPicker.getValue()).lengthOfMonth());
                } catch (Exception e) {
                    FileLog.e(e);
                    dayPicker.setMaxValue(31);
                }
                monthPicker.setMinValue(0);
                monthPicker.setMaxValue(11);
            } else if (yearPicker.getValue() == thisYear) {
                monthPicker.setMinValue(0);
                monthPicker.setMaxValue(thisMonth);
                if (monthPicker.getValue() == thisMonth) {
                    dayPicker.setMinValue(1);
                    dayPicker.setMaxValue(1 + thisDay - 1);
                } else {
                    dayPicker.setMinValue(1);
                    try {
                        dayPicker.setMaxValue(YearMonth.of(yearPicker.getValue(), 1 + monthPicker.getValue()).lengthOfMonth());
                    } catch (Exception e) {
                        FileLog.e(e);
                        dayPicker.setMaxValue(31);
                    }
                }
            } else {
                dayPicker.setMinValue(1);
                try {
                    dayPicker.setMaxValue(YearMonth.of(yearPicker.getValue(), 1 + monthPicker.getValue()).lengthOfMonth());
                } catch (Exception e) {
                    FileLog.e(e);
                    dayPicker.setMaxValue(31);
                }
                monthPicker.setMinValue(0);
                monthPicker.setMaxValue(11);
            }
        };

        long currentTime = System.currentTimeMillis();

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.25f));
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(31);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> "" + value);
        final NumberPicker.OnScrollListener onScrollListener = (view, state) -> {
            if (state == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) check.run();
        };
        dayPicker.setOnScrollListener(onScrollListener);

        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setWrapSelectorWheel(false);
        linearLayout.addView(monthPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        monthPicker.setFormatter(value -> {
            switch (value) {
                case 0: return LocaleController.getString(R.string.January);
                case 1: return LocaleController.getString(R.string.February);
                case 2: return LocaleController.getString(R.string.March);
                case 3: return LocaleController.getString(R.string.April);
                case 4: return LocaleController.getString(R.string.May);
                case 5: return LocaleController.getString(R.string.June);
                case 6: return LocaleController.getString(R.string.July);
                case 7: return LocaleController.getString(R.string.August);
                case 8: return LocaleController.getString(R.string.September);
                case 9: return LocaleController.getString(R.string.October);
                case 10: return LocaleController.getString(R.string.November);
                case 11:
                default: {
                    return LocaleController.getString(R.string.December);
                }
            }
        });
        monthPicker.setOnScrollListener(onScrollListener);

        yearPicker.setMinValue(minYear);
        yearPicker.setMaxValue(undefinedYear);
        yearPicker.setWrapSelectorWheel(false);
        yearPicker.setFormatter(value -> value == undefinedYear ? "—" : String.format("%02d", value));
        linearLayout.addView(yearPicker, LayoutHelper.createLinear(0, 54 * 5, 0.25f));
        yearPicker.setOnScrollListener(onScrollListener);

        if (currentBirthday != null) {
            dayPicker.setValue(currentBirthday.day);
            monthPicker.setValue(currentBirthday.month - 1);
            if ((currentBirthday.flags & 1) != 0) {
                yearPicker.setValue(currentBirthday.year);
            } else {
                yearPicker.setValue(undefinedYear);
            }
        } else {
            dayPicker.setValue(calendar.get(Calendar.DAY_OF_MONTH));
            monthPicker.setValue(calendar.get(Calendar.MONTH));
            yearPicker.setValue(undefinedYear);
        }

        check.run();

        if (addPrivacyText != null) {
            FrameLayout frameLayout = new FrameLayout(context);
            LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(context);
            textView.setPadding(dp(8), 0, dp(8), 0);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setGravity(Gravity.CENTER);
            frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));;
            container.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final int currentAccount = UserConfig.selectedAccount;
            Runnable setText = () -> {
                ArrayList<TLRPC.PrivacyRule> rules = ContactsController.getInstance(currentAccount).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY);
                String string = getString(R.string.EditProfileBirthdayInfoContacts);
                if (rules != null && !rules.isEmpty()) {
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
                textView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(string, () -> {
                    if (rules == null) return;
                    BaseFragment fragment = LaunchActivity.getLastFragment();
                    if (fragment == null) return;
                    BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                    bottomSheetParams.transitionFromLeft = true;
                    bottomSheetParams.allowNestedScroll = false;
                    fragment.showAsSheet(new PrivacyControlActivity(PrivacyControlActivity.PRIVACY_RULES_TYPE_BIRTHDAY), bottomSheetParams);
                }), true, dp(8f / 3f), dp(.66f)));
            };
            setText.run();

            NotificationCenter.getInstance(currentAccount).listen(frameLayout, NotificationCenter.privacyRulesUpdated, args -> setText.run());
            ContactsController.getInstance(currentAccount).loadPrivacySettings();
        }

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setText(button);
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider)));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            TLRPC.TL_birthday b = new TLRPC.TL_birthday();
            b.day   = dayPicker.getValue();
            b.month = 1 + monthPicker.getValue();
            if (yearPicker.getValue() != undefinedYear) {
                b.flags |= 1;
                b.year = yearPicker.getValue();
            }
            builder.getDismissRunnable().run();
            whenSelectedBirthday.run(b);
        });

        builder.setCustomView(container);
        return builder;
    }


    public interface StatusUntilDatePickerDelegate {
        void didSelectDate(int date);
    }

    public static BottomSheet.Builder createStatusUntilDatePickerDialog(Context context, long currentDate, final StatusUntilDatePickerDelegate delegate) {
        if (context == null) {
            return null;
        }

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors();
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context);
        dayPicker.setTextColor(datePickerColors.textColor);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Hours", value);
            }
        };
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setTextOffset(-dp(10));
        final NumberPicker minutePicker = new NumberPicker(context) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Minutes", value);
            }
        };
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setTextOffset(-dp(34));

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
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.SetEmojiStatusUntilTitle));

        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);
        int currentDayYear = calendar.get(Calendar.DAY_OF_YEAR);

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0);
        dayPicker.setMaxValue(365);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return LocaleController.getString(R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                int yearDay = calendar.get(Calendar.DAY_OF_YEAR);
                if (year == currentYear && yearDay < currentDayYear + 7) {
                    return LocaleController.getInstance().getFormatterWeek().format(date) + ", " + LocaleController.getInstance().getFormatterScheduleDay().format(date);
                } else if (year == currentYear) {
                    return LocaleController.getInstance().getFormatterScheduleDay().format(date);
                } else {
                    return LocaleController.getInstance().getFormatterScheduleYear().format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker);
        };
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

        if (currentDate > 0 && currentDate != 0x7FFFFFFE) {
            currentDate *= 1000;
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            int days = (int) ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
            calendar.setTimeInMillis(currentDate);
            if (days >= 0) {
                minutePicker.setValue(calendar.get(Calendar.MINUTE));
                hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
                dayPicker.setValue(days);
            }
        }

        checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(8), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor));
        buttonTextView.setText(LocaleController.getString(R.string.SetEmojiStatusUntilButton));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            boolean setSeconds = checkScheduleDate(null, null, 0, dayPicker, hourPicker, minutePicker);
            calendar.setTimeInMillis(System.currentTimeMillis() + (long) dayPicker.getValue() * 24 * 3600 * 1000);
            calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            calendar.set(Calendar.MINUTE, minutePicker.getValue());
            if (setSeconds) {
                calendar.set(Calendar.SECOND, 0);
            }
            delegate.didSelectDate((int) (calendar.getTimeInMillis() / 1000));
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        return builder;
    }

    public static BottomSheet.Builder createAutoDeleteDatePickerDialog(Context context, int type, Theme.ResourcesProvider resourcesProvider, final ScheduleDatePickerDelegate datePickerDelegate) {
        if (context == null) {
            return null;
        }

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors(resourcesProvider);
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        int[] values = new int[]{
                0,
                60 * 24,
                2 * 60 * 24,
                3 * 60 * 24,
                4 * 60 * 24,
                5 * 60 * 24,
                6 * 60 * 24,
                7 * 60 * 24,
                2 * 7 * 60 * 24,
                3 * 7 * 60 * 24,
                31 * 60 * 24,
                2 * 31 * 60 * 24,
                3 * 31 * 60 * 24,
                4 * 31 * 60 * 24,
                5 * 31 * 60 * 24,
                6 * 31 * 60 * 24,
                365 * 60 * 24
        };

        final NumberPicker numberPicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int index) {
                if (values[index] == 0) {
                    return LocaleController.getString(R.string.AutoDeleteNever);
                } else if (values[index] < 7 * 60 * 24) {
                    return LocaleController.formatPluralString("Days", values[index] / (60 * 24));
                } else if (values[index] < 31 * 60 * 24) {
                    return LocaleController.formatPluralString("Weeks", values[index] / (60 * 24));
                } else if (values[index] < 365 * 60 * 24) {
                    return LocaleController.formatPluralString("Months", values[index] / (7 * 60 * 24));
                } else {
                    return LocaleController.formatPluralString("Years", values[index] * 5 / 31 * 60 * 24);
                }
            }
        };
        numberPicker.setMinValue(0);
        numberPicker.setMaxValue(values.length - 1);
        numberPicker.setTextColor(datePickerColors.textColor);
        numberPicker.setValue(0);
        numberPicker.setFormatter(index -> {
            if (values[index] == 0) {
                return LocaleController.getString(R.string.AutoDeleteNever);
            } else if (values[index] < 7 * 60 * 24) {
                return LocaleController.formatPluralString("Days", values[index] / (60 * 24));
            } else if (values[index] < 31 * 60 * 24) {
                return LocaleController.formatPluralString("Weeks", values[index] / (7 * 60 * 24));
            } else if (values[index] < 365 * 60 * 24) {
                return LocaleController.formatPluralString("Months", values[index] / (31 * 60 * 24));
            } else {
                return LocaleController.formatPluralString("Years", values[index] / (365 * 60 * 24));
            }
        });

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
                numberPicker.setItemCount(count);
                numberPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.AutoDeleteAfteTitle));

        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        AnimatedTextView buttonTextView = new AnimatedTextView(context, true, true, false) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(numberPicker, LayoutHelper.createLinear(0, 54 * 5, 1f));

        buttonTextView.setPadding(0, 0, 0, 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(dp(14));
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(8), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setText(LocaleController.getString(R.string.DisableAutoDeleteTimer));

        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            try {
                if (newVal == 0) {
                    buttonTextView.setText(LocaleController.getString(R.string.DisableAutoDeleteTimer));
                } else {
                    buttonTextView.setText(LocaleController.getString(R.string.SetAutoDeleteTimer));
                }
            } catch (Exception ignore) {

            }
        };
        numberPicker.setOnValueChangedListener(onValueChangeListener);

        buttonTextView.setOnClickListener(v -> {
            int time = values[numberPicker.getValue()];
            datePickerDelegate.didSelectDate(true, time);
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        return builder;
    }

    public static BottomSheet.Builder createSoundFrequencyPickerDialog(Context context, int notifyMaxCount, int notifyDelay, final SoundFrequencyDelegate delegate) {
        return createSoundFrequencyPickerDialog(context, notifyMaxCount, notifyDelay, delegate, null);
    }

    public static BottomSheet.Builder createSoundFrequencyPickerDialog(Context context, int notifyMaxCount, int notifyDelay, final SoundFrequencyDelegate delegate, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) {
            return null;
        }

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors(resourcesProvider);
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker times = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int index) {
                return LocaleController.formatPluralString("Times", index + 1);
            }
        };
        times.setMinValue(0);
        times.setMaxValue(10);
        times.setTextColor(datePickerColors.textColor);
        times.setValue(notifyMaxCount - 1);
        times.setWrapSelectorWheel(false);
        times.setFormatter(index -> LocaleController.formatPluralString("Times", index + 1));

        final NumberPicker minutes = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int index) {
                return LocaleController.formatPluralString("Times", index + 1);
            }
        };
        minutes.setMinValue(0);
        minutes.setMaxValue(10);
        minutes.setTextColor(datePickerColors.textColor);
        minutes.setValue(notifyDelay / 60 - 1);
        minutes.setWrapSelectorWheel(false);
        minutes.setFormatter(index -> LocaleController.formatPluralString("Minutes", index + 1));

        NumberPicker divider = new NumberPicker(context, resourcesProvider);
        divider.setMinValue(0);
        divider.setMaxValue(0);
        divider.setTextColor(datePickerColors.textColor);
        divider.setValue(0);
        divider.setWrapSelectorWheel(false);
        divider.setFormatter(index -> LocaleController.getString(R.string.NotificationsFrequencyDivider));

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
                times.setItemCount(count);
                times.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutes.setItemCount(count);
                minutes.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                divider.setItemCount(count);
                divider.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.NotfificationsFrequencyTitle));

        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(times, LayoutHelper.createLinear(0, 54 * 5, 0.4f));
        linearLayout.addView(divider, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.2f, Gravity.CENTER_VERTICAL));
        linearLayout.addView(minutes, LayoutHelper.createLinear(0, 54 * 5, 0.4f));

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(8), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor));
        buttonTextView.setText(LocaleController.getString(R.string.AutoDeleteConfirm));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));

        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {

        };
        times.setOnValueChangedListener(onValueChangeListener);
        minutes.setOnValueChangedListener(onValueChangeListener);

        buttonTextView.setOnClickListener(v -> {
            int time = times.getValue() + 1;
            int minute = (minutes.getValue() + 1) * 60;
            delegate.didSelectValues(time, minute);
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        return builder;
    }

    public static BottomSheet.Builder createMuteForPickerDialog(Context context, Theme.ResourcesProvider resourcesProvider, final ScheduleDatePickerDelegate datePickerDelegate) {
        if (context == null) {
            return null;
        }

        ScheduleDatePickerColors datePickerColors = new ScheduleDatePickerColors(resourcesProvider);
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        int[] values = new int[]{
                30,
                60,
                60 * 2,
                60 * 3,
                60 * 8,
                60 * 24,
                2 * 60 * 24,
                3 * 60 * 24,
                4 * 60 * 24,
                5 * 60 * 24,
                6 * 60 * 24,
                7 * 60 * 24,
                2 * 7 * 60 * 24,
                3 * 7 * 60 * 24,
                31 * 60 * 24,
                2 * 31 * 60 * 24,
                3 * 31 * 60 * 24,
                4 * 31 * 60 * 24,
                5 * 31 * 60 * 24,
                6 * 31 * 60 * 24,
                365 * 60 * 24
        };

        final NumberPicker numberPicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int index) {
                if (values[index] == 0) {
                    return LocaleController.getString(R.string.MuteNever);
                } else if (values[index] < 60) {
                    return LocaleController.formatPluralString("Minutes", values[index]);
                } else if (values[index] < 60 * 24) {
                    return LocaleController.formatPluralString("Hours", values[index] / 60);
                } else if (values[index] < 7 * 60 * 24) {
                    return LocaleController.formatPluralString("Days", values[index] / (60 * 24));
                } else if (values[index] < 31 * 60 * 24) {
                    return LocaleController.formatPluralString("Weeks", values[index] / (7 * 60 * 24));
                } else if (values[index] < 365 * 60 * 24) {
                    return LocaleController.formatPluralString("Months", values[index] / (31 * 60 * 24));
                } else {
                    return LocaleController.formatPluralString("Years", values[index] / (365 * 60 * 24));
                }
            }
        };
        numberPicker.setMinValue(0);
        numberPicker.setMaxValue(values.length - 1);
        numberPicker.setTextColor(datePickerColors.textColor);
        numberPicker.setValue(0);
        numberPicker.setFormatter(index -> {
            if (values[index] == 0) {
                return LocaleController.getString(R.string.MuteNever);
            } else if (values[index] < 60) {
                return LocaleController.formatPluralString("Minutes", values[index]);
            } else if (values[index] < 60 * 24) {
                return LocaleController.formatPluralString("Hours", values[index] / 60);
            } else if (values[index] < 7 * 60 * 24) {
                return LocaleController.formatPluralString("Days", values[index] / (60 * 24));
            } else if (values[index] < 31 * 60 * 24) {
                return LocaleController.formatPluralString("Weeks", values[index] / (7 * 60 * 24));
            } else if (values[index] < 365 * 60 * 24) {
                return LocaleController.formatPluralString("Months", values[index] / (31 * 60 * 24));
            } else {
                return LocaleController.formatPluralString("Years", values[index] / (365 * 60 * 24));
            }
        });

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
                numberPicker.setItemCount(count);
                numberPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.MuteForAlert));
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(numberPicker, LayoutHelper.createLinear(0, 54 * 5, 1f));
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {

        };
        numberPicker.setOnValueChangedListener(onValueChangeListener);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(8), datePickerColors.buttonBackgroundColor, datePickerColors.buttonBackgroundPressedColor));
        buttonTextView.setText(LocaleController.getString(R.string.AutoDeleteConfirm));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            int time = values[numberPicker.getValue()] * 60;
            datePickerDelegate.didSelectDate(true, time);
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);

        return builder;
    }

    private static void checkMuteForButton(NumberPicker dayPicker, NumberPicker hourPicker, TextView buttonTextView, boolean animated) {
        StringBuilder stringBuilder = new StringBuilder();
        if (dayPicker.getValue() != 0) {
            stringBuilder.append(dayPicker.getValue()).append(LocaleController.getString(R.string.SecretChatTimerDays));
        }
        if (hourPicker.getValue() != 0) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(" ");
            }
            stringBuilder.append(hourPicker.getValue()).append(LocaleController.getString(R.string.SecretChatTimerHours));
        }
        if (stringBuilder.length() == 0) {
            buttonTextView.setText(LocaleController.getString(R.string.ChooseTimeForMute));
            if (buttonTextView.isEnabled()) {
                buttonTextView.setEnabled(false);
                if (animated) {
                    buttonTextView.animate().alpha(0.5f);
                } else {
                    buttonTextView.setAlpha(0.5f);
                }
            }
        } else {
            buttonTextView.setText(LocaleController.formatString("MuteForButton", R.string.MuteForButton, stringBuilder.toString()));
            if (!buttonTextView.isEnabled()) {
                buttonTextView.setEnabled(true);
                if (animated) {
                    buttonTextView.animate().alpha(1f);
                } else {
                    buttonTextView.setAlpha(1f);
                }
            }
        }
    }

    private static void checkCalendarDate(long minDate, NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker yearPicker) {
        int month, year;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(minDate);
        int minYear = calendar.get(Calendar.YEAR);
        int minMonth = calendar.get(Calendar.MONTH);
        int minDay = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.setTimeInMillis(System.currentTimeMillis());
        int maxYear = calendar.get(Calendar.YEAR);
        int maxMonth = calendar.get(Calendar.MONTH);
        int maxDay = calendar.get(Calendar.DAY_OF_MONTH);

        yearPicker.setMaxValue(maxYear);
        yearPicker.setMinValue(minYear);
        year = yearPicker.getValue();

        monthPicker.setMaxValue(year == maxYear ? maxMonth : 11);
        monthPicker.setMinValue(year == minYear ? minMonth : 0);
        month = monthPicker.getValue();

        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);

        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        dayPicker.setMaxValue(year == maxYear && month == maxMonth ? Math.min(maxDay, daysInMonth) : daysInMonth);
        dayPicker.setMinValue(year == minYear && month == minMonth ? minDay : 1);
    }

    public static BottomSheet.Builder createCalendarPickerDialog(Context context, long minDate, final MessagesStorage.IntCallback callback, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context, resourcesProvider);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker monthPicker = new NumberPicker(context, resourcesProvider);
        monthPicker.setItemCount(5);
        monthPicker.setTextOffset(-dp(10));
        final NumberPicker yearPicker = new NumberPicker(context, resourcesProvider);
        yearPicker.setItemCount(5);
        yearPicker.setTextOffset(-dp(24));

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
                monthPicker.setItemCount(count);
                yearPicker.setItemCount(count);
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                monthPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                yearPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
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

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.ChooseDate));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.25f));
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(31);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> "" + value);
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            checkCalendarDate(minDate, dayPicker, monthPicker, yearPicker);
        };
        dayPicker.setOnValueChangedListener(onValueChangeListener);

        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setWrapSelectorWheel(false);
        linearLayout.addView(monthPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        monthPicker.setFormatter(value -> {
            switch (value) {
                case 0: return LocaleController.getString(R.string.January);
                case 1: return LocaleController.getString(R.string.February);
                case 2: return LocaleController.getString(R.string.March);
                case 3: return LocaleController.getString(R.string.April);
                case 4: return LocaleController.getString(R.string.May);
                case 5: return LocaleController.getString(R.string.June);
                case 6: return LocaleController.getString(R.string.July);
                case 7: return LocaleController.getString(R.string.August);
                case 8: return LocaleController.getString(R.string.September);
                case 9: return LocaleController.getString(R.string.October);
                case 10: return LocaleController.getString(R.string.November);
                case 11:
                default: {
                    return LocaleController.getString(R.string.December);
                }
            }
        });
        monthPicker.setOnValueChangedListener(onValueChangeListener);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(minDate);
        int minYear = calendar.get(Calendar.YEAR);
        calendar.setTimeInMillis(System.currentTimeMillis());
        int maxYear = calendar.get(Calendar.YEAR);

        yearPicker.setMinValue(minYear);
        yearPicker.setMaxValue(maxYear);
        yearPicker.setWrapSelectorWheel(false);
        yearPicker.setFormatter(value -> String.format("%02d", value));
        linearLayout.addView(yearPicker, LayoutHelper.createLinear(0, 54 * 5, 0.25f));
        yearPicker.setOnValueChangedListener(onValueChangeListener);

        dayPicker.setValue(31);
        monthPicker.setValue(12);
        yearPicker.setValue(maxYear);

        checkCalendarDate(minDate, dayPicker, monthPicker, yearPicker);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setText(LocaleController.getString(R.string.JumpToDate));
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider)));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            checkCalendarDate(minDate, dayPicker, monthPicker, yearPicker);
            calendar.set(Calendar.YEAR, yearPicker.getValue());
            calendar.set(Calendar.MONTH, monthPicker.getValue());
            calendar.set(Calendar.DAY_OF_MONTH, dayPicker.getValue());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.SECOND, 0);
            callback.run((int) (calendar.getTimeInMillis() / 1000));
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        return builder;
    }

    public static BottomSheet createMuteAlert(BaseFragment fragment, final long dialog_id, long topicId, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(fragment.getParentActivity(), false, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.Notifications), true);
        CharSequence[] items = new CharSequence[]{
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 8)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)),
                LocaleController.getString(R.string.MuteDisable)
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
                    NotificationsController.getInstance(UserConfig.selectedAccount).setDialogNotificationsSettings(dialog_id, topicId, setting);
                    if (BulletinFactory.canShowBulletin(fragment)) {
                        BulletinFactory.createMuteBulletin(fragment, setting, 0, resourcesProvider).show();
                    }
                }
        );
        return builder.create();
    }

    public static BottomSheet createMuteAlert(BaseFragment fragment, ArrayList<Long> dialog_ids, int topicId, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(fragment.getParentActivity(), false, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.Notifications), true);
        CharSequence[] items = new CharSequence[]{
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 8)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)),
                LocaleController.getString(R.string.MuteDisable)
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
                    if (dialog_ids != null) {
                        for (int j = 0; j < dialog_ids.size(); ++j) {
                            long dialog_id = dialog_ids.get(j);
                            NotificationsController.getInstance(UserConfig.selectedAccount).setDialogNotificationsSettings(dialog_id, topicId, setting);
                        }
                    }
                    if (BulletinFactory.canShowBulletin(fragment)) {
                        BulletinFactory.createMuteBulletin(fragment, setting, 0, resourcesProvider).show();
                    }
                }
        );
        return builder.create();
    }

    public static void sendReport(TLRPC.InputPeer peer, int type, String message, ArrayList<Integer> messages, int storyId) {
        TLRPC.ReportReason reason = null;
        if (type == AlertsCreator.REPORT_TYPE_SPAM) {
            reason = new TLRPC.TL_inputReportReasonSpam();
        } else if (type == AlertsCreator.REPORT_TYPE_FAKE_ACCOUNT) {
            reason = new TLRPC.TL_inputReportReasonFake();
        } else if (type == AlertsCreator.REPORT_TYPE_VIOLENCE) {
            reason = new TLRPC.TL_inputReportReasonViolence();
        } else if (type == AlertsCreator.REPORT_TYPE_CHILD_ABUSE) {
            reason = new TLRPC.TL_inputReportReasonChildAbuse();
        } else if (type == AlertsCreator.REPORT_TYPE_PORNOGRAPHY) {
            reason = new TLRPC.TL_inputReportReasonPornography();
        } else if (type == AlertsCreator.REPORT_TYPE_ILLEGAL_DRUGS) {
            reason = new TLRPC.TL_inputReportReasonIllegalDrugs();
        } else if (type == AlertsCreator.REPORT_TYPE_PERSONAL_DETAILS) {
            reason = new TLRPC.TL_inputReportReasonPersonalDetails();
        } else if (type == AlertsCreator.REPORT_TYPE_OTHER) {
            reason = new TLRPC.TL_inputReportReasonOther();
        }
        if (reason == null) {
            return;
        }
        if (storyId != 0) {
            TL_stories.TL_stories_report request = new TL_stories.TL_stories_report();
            request.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(peer.user_id);
            request.id.add(storyId);
            request.message = message;
            request.reason = reason;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> {

            });
        } else {
            TLRPC.TL_messages_report request = new TLRPC.TL_messages_report();
            request.peer = peer;
            request.id.addAll(messages);
            request.message = message;
            request.reason = reason;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> {

            });
        }
    }

    public static void createReportAlert(final Context context, final long dialog_id, final int messageId, final BaseFragment parentFragment, Runnable hideDim) {
        createReportAlert(context, dialog_id, messageId, 0, parentFragment, null, hideDim);
    }

    public final static int REPORT_TYPE_SPAM = 0;
    public final static int REPORT_TYPE_VIOLENCE = 1;
    public final static int REPORT_TYPE_CHILD_ABUSE = 2;
    public final static int REPORT_TYPE_ILLEGAL_DRUGS = 3;
    public final static int REPORT_TYPE_PERSONAL_DETAILS = 4;
    public final static int REPORT_TYPE_PORNOGRAPHY = 5;
    public final static int REPORT_TYPE_FAKE_ACCOUNT = 6;
    public final static int REPORT_TYPE_OTHER = 100;

    public static void createReportAlert(final Context context, final long dialog_id, final int messageId, final int storyId, final BaseFragment parentFragment, Theme.ResourcesProvider resourcesProvider, Runnable hideDim) {
        if (context == null || parentFragment == null) {
            return;
        }

        final TLRPC.User user = MessagesController.getInstance(parentFragment.getCurrentAccount()).getUser(dialog_id);
        final boolean isBot = user != null && user.bot;

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, resourcesProvider);
        builder.setDimBehind(hideDim == null);
        builder.setOnPreDismissListener(di -> {
            if (hideDim != null) {
                hideDim.run();
            }
        });
        builder.setTitle(LocaleController.getString(R.string.ReportChat), true);
        CharSequence[] items;
        int[] icons;
        int[] types;
        if (messageId != 0) {

            items = new CharSequence[]{
                    LocaleController.getString(R.string.ReportChatSpam),
                    LocaleController.getString(R.string.ReportChatViolence),
                    LocaleController.getString(R.string.ReportChatChild),
                    LocaleController.getString(R.string.ReportChatIllegalDrugs),
                    LocaleController.getString(R.string.ReportChatPersonalDetails),
                    LocaleController.getString(R.string.ReportChatPornography),
                    LocaleController.getString(R.string.ReportChatOther)
            };
            icons = new int[]{
                    R.drawable.msg_clearcache,
                    R.drawable.msg_report_violence,
                    R.drawable.msg_block2,
                    R.drawable.msg_report_drugs,
                    R.drawable.msg_report_personal,
                    R.drawable.msg_report_xxx,
                    R.drawable.msg_report_other
            };
            types = new int[]{
                    REPORT_TYPE_SPAM,
                    REPORT_TYPE_VIOLENCE,
                    REPORT_TYPE_CHILD_ABUSE,
                    REPORT_TYPE_ILLEGAL_DRUGS,
                    REPORT_TYPE_PERSONAL_DETAILS,
                    REPORT_TYPE_PORNOGRAPHY,
                    REPORT_TYPE_OTHER
            };
        } else {
            items = new CharSequence[]{
                    LocaleController.getString(R.string.ReportChatSpam),
                    LocaleController.getString(R.string.ReportChatFakeAccount),
                    LocaleController.getString(R.string.ReportChatViolence),
                    LocaleController.getString(R.string.ReportChatChild),
                    LocaleController.getString(R.string.ReportChatIllegalDrugs),
                    LocaleController.getString(R.string.ReportChatPersonalDetails),
                    LocaleController.getString(R.string.ReportChatPornography),
                    LocaleController.getString(R.string.ReportChatOther)
            };
            icons = new int[]{
                    R.drawable.msg_clearcache,
                    R.drawable.msg_report_fake,
                    R.drawable.msg_report_violence,
                    R.drawable.msg_block2,
                    R.drawable.msg_report_drugs,
                    R.drawable.msg_report_personal,
                    R.drawable.msg_report_xxx,
                    R.drawable.msg_report_other
            };
            types = new int[]{
                    REPORT_TYPE_SPAM,
                    REPORT_TYPE_FAKE_ACCOUNT,
                    REPORT_TYPE_VIOLENCE,
                    REPORT_TYPE_CHILD_ABUSE,
                    REPORT_TYPE_ILLEGAL_DRUGS,
                    REPORT_TYPE_PERSONAL_DETAILS,
                    REPORT_TYPE_PORNOGRAPHY,
                    REPORT_TYPE_OTHER
            };
        }
        builder.setItems(items, icons, (dialogInterface, i) -> {
            int type = types[i];
            if (messageId == 0 && (type == REPORT_TYPE_SPAM || type == REPORT_TYPE_VIOLENCE || type == REPORT_TYPE_CHILD_ABUSE || type == REPORT_TYPE_PORNOGRAPHY || type == REPORT_TYPE_ILLEGAL_DRUGS || type == REPORT_TYPE_PERSONAL_DETAILS) && parentFragment instanceof ChatActivity && !isBot) {
                ((ChatActivity) parentFragment).openReportChat(type);
                return;
            } else if (messageId == 0 && (type == REPORT_TYPE_OTHER || type == REPORT_TYPE_FAKE_ACCOUNT) || messageId != 0 && type == REPORT_TYPE_OTHER) {
                if (parentFragment instanceof ChatActivity) {
                    AndroidUtilities.requestAdjustNothing(parentFragment.getParentActivity(), parentFragment.getClassGuid());
                }
                parentFragment.showDialog(new ReportAlert(context, type, resourcesProvider) {

                    @Override
                    public void dismissInternal() {
                        super.dismissInternal();
                        if (parentFragment instanceof ChatActivity) {
                            ((ChatActivity) parentFragment).checkAdjustResize();
                        }
                    }

                    @Override
                    protected void onSend(int type, String message) {
                        ArrayList<Integer> ids = new ArrayList<>();
                        if (messageId != 0) {
                            ids.add(messageId);
                        }
                        TLRPC.InputPeer peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(dialog_id);
                        sendReport(peer, type, message, ids, storyId);
                        if (parentFragment instanceof ChatActivity) {
                            UndoView undoView = ((ChatActivity) parentFragment).getUndoView();
                            if (undoView != null) {
                                undoView.showWithAction(0, UndoView.ACTION_REPORT_SENT, null);
                            }
                        } else {
                            AndroidUtilities.runOnUIThread(() -> {
                                BulletinFactory bulletinFactory = BulletinFactory.global();
                                if (bulletinFactory != null) {
                                    bulletinFactory.createReportSent(resourcesProvider).show();
                                }
                            });
                        }
                    }
                });
                return;
            }
            TLObject req;
            TLRPC.InputPeer peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(dialog_id);
            if (storyId != 0) {
                TL_stories.TL_stories_report request = new TL_stories.TL_stories_report();
                request.id.add(storyId);
                request.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(dialog_id);
                request.message = "";
                if (type == REPORT_TYPE_SPAM) {
                    request.reason = new TLRPC.TL_inputReportReasonSpam();
                } else if (type == REPORT_TYPE_FAKE_ACCOUNT) {
                    request.reason = new TLRPC.TL_inputReportReasonFake();
                } else if (type == REPORT_TYPE_VIOLENCE) {
                    request.reason = new TLRPC.TL_inputReportReasonViolence();
                } else if (type == REPORT_TYPE_CHILD_ABUSE) {
                    request.reason = new TLRPC.TL_inputReportReasonChildAbuse();
                } else if (type == REPORT_TYPE_PORNOGRAPHY) {
                    request.reason = new TLRPC.TL_inputReportReasonPornography();
                } else if (type == REPORT_TYPE_ILLEGAL_DRUGS) {
                    request.reason = new TLRPC.TL_inputReportReasonIllegalDrugs();
                } else if (type == REPORT_TYPE_PERSONAL_DETAILS) {
                    request.reason = new TLRPC.TL_inputReportReasonPersonalDetails();
                }
                req = request;
            } else if (messageId != 0) {
                TLRPC.TL_messages_report request = new TLRPC.TL_messages_report();
                request.peer = peer;
                request.id.add(messageId);
                request.message = "";
                if (type == REPORT_TYPE_SPAM) {
                    request.reason = new TLRPC.TL_inputReportReasonSpam();
                } else if (type == REPORT_TYPE_VIOLENCE) {
                    request.reason = new TLRPC.TL_inputReportReasonViolence();
                } else if (type == REPORT_TYPE_CHILD_ABUSE) {
                    request.reason = new TLRPC.TL_inputReportReasonChildAbuse();
                } else if (type == REPORT_TYPE_PORNOGRAPHY) {
                    request.reason = new TLRPC.TL_inputReportReasonPornography();
                } else if (type == REPORT_TYPE_ILLEGAL_DRUGS) {
                    request.reason = new TLRPC.TL_inputReportReasonIllegalDrugs();
                } else if (type == REPORT_TYPE_PERSONAL_DETAILS) {
                    request.reason = new TLRPC.TL_inputReportReasonPersonalDetails();
                }
                req = request;
            } else {
                TLRPC.TL_account_reportPeer request = new TLRPC.TL_account_reportPeer();
                request.peer = peer;
                request.message = "";
                if (type == REPORT_TYPE_SPAM) {
                    request.reason = new TLRPC.TL_inputReportReasonSpam();
                } else if (type == REPORT_TYPE_FAKE_ACCOUNT) {
                    request.reason = new TLRPC.TL_inputReportReasonFake();
                } else if (type == REPORT_TYPE_VIOLENCE) {
                    request.reason = new TLRPC.TL_inputReportReasonViolence();
                } else if (type == REPORT_TYPE_CHILD_ABUSE) {
                    request.reason = new TLRPC.TL_inputReportReasonChildAbuse();
                } else if (type == REPORT_TYPE_PORNOGRAPHY) {
                    request.reason = new TLRPC.TL_inputReportReasonPornography();
                } else if (type == REPORT_TYPE_ILLEGAL_DRUGS) {
                    request.reason = new TLRPC.TL_inputReportReasonIllegalDrugs();
                } else if (type == REPORT_TYPE_PERSONAL_DETAILS) {
                    request.reason = new TLRPC.TL_inputReportReasonPersonalDetails();
                }
                req = request;
            }
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {

            });
            if (parentFragment instanceof ChatActivity) {
                UndoView undoView = ((ChatActivity) parentFragment).getUndoView();
                if (undoView != null) {
                    undoView.showWithAction(0, UndoView.ACTION_REPORT_SENT, null);
                }
            } else {
                BulletinFactory.of(parentFragment).createReportSent(resourcesProvider).show();
            }
        });
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
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static void showSendMediaAlert(int result, final BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        if (result == 0 || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.UnableForward));
        if (result == 1) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedStickers));
        } else if (result == 2) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedMedia));
        } else if (result == 3) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPolls));
        } else if (result == 4) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedStickersAll));
        } else if (result == 5) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedMediaAll));
        } else if (result == 6) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPollsAll));
        } else if (result == 7) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPrivacyVoiceMessages));
        } else if (result == 8) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPrivacyVideoMessages));
        } else if (result == 9) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedVideoAll));
        } else if (result == 10) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPhotoAll));
        } else if (result == 11) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedVideo));
        } else if (result == 12) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPhoto));
        } else if (result == 13) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedVoiceAll));
        }  else if (result == 14) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedVoice));
        } else if (result == 15) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedRoundAll));
        }  else if (result == 16) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedRound));
        }  else if (result == 17) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedDocumentsAll));
        }  else if (result == 18) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedDocuments));
        }  else if (result == 19) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedMusicAll));
        }  else if (result == 20) {
            builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedMusic));
        }

        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static void showAddUserAlert(String error, final BaseFragment fragment, boolean isChannel, TLObject request) {
        if (error == null || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        switch (error) {
            case "PEER_FLOOD":
                builder.setMessage(LocaleController.getString(R.string.NobodyLikesSpam2));
                builder.setNegativeButton(LocaleController.getString(R.string.MoreInfo), (dialogInterface, i) -> MessagesController.getInstance(fragment.getCurrentAccount()).openByUserName("spambot", fragment, 1));
                break;
            case "USER_BLOCKED":
            case "USER_BOT":
            case "USER_ID_INVALID":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString(R.string.ChannelUserCantAdd));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.GroupUserCantAdd));
                }
                break;
            case "USERS_TOO_MUCH":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString(R.string.ChannelUserAddLimit));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.GroupUserAddLimit));
                }
                break;
            case "USER_NOT_MUTUAL_CONTACT":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString(R.string.ChannelUserLeftError));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.GroupUserLeftError));
                }
                break;
            case "ADMINS_TOO_MUCH":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString(R.string.ChannelUserCantAdmin));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.GroupUserCantAdmin));
                }
                break;
            case "BOTS_TOO_MUCH":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString(R.string.ChannelUserCantBot));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.GroupUserCantBot));
                }
                break;
            case "USER_PRIVACY_RESTRICTED":
                if (isChannel) {
                    builder.setMessage(LocaleController.getString(R.string.InviteToChannelError));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.InviteToGroupError));
                }
                break;
            case "USERS_TOO_FEW":
                builder.setMessage(LocaleController.getString(R.string.CreateGroupError));
                break;
            case "USER_RESTRICTED":
                builder.setMessage(LocaleController.getString(R.string.UserRestricted));
                break;
            case "YOU_BLOCKED_USER":
                builder.setMessage(LocaleController.getString(R.string.YouBlockedUser));
                break;
            case "CHAT_ADMIN_BAN_REQUIRED":
            case "USER_KICKED":
                if (request instanceof TLRPC.TL_channels_inviteToChannel) {
                    builder.setMessage(LocaleController.getString(R.string.AddUserErrorBlacklisted));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.AddAdminErrorBlacklisted));
                }
                break;
            case "CHAT_ADMIN_INVITE_REQUIRED":
                builder.setMessage(LocaleController.getString(R.string.AddAdminErrorNotAMember));
                break;
            case "USER_ADMIN_INVALID":
                builder.setMessage(LocaleController.getString(R.string.AddBannedErrorAdmin));
                break;
            case "CHANNELS_ADMIN_PUBLIC_TOO_MUCH":
                builder.setMessage(LocaleController.getString(R.string.PublicChannelsTooMuch));
                break;
            case "CHANNELS_ADMIN_LOCATED_TOO_MUCH":
                builder.setMessage(LocaleController.getString(R.string.LocatedChannelsTooMuch));
                break;
            case "CHANNELS_TOO_MUCH":
                builder.setTitle(LocaleController.getString(R.string.ChannelTooMuchTitle));
                if (request instanceof TLRPC.TL_channels_createChannel) {
                    builder.setMessage(LocaleController.getString(R.string.ChannelTooMuch));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.ChannelTooMuchJoin));
                }
                break;
            case "USER_CHANNELS_TOO_MUCH":
                builder.setTitle(LocaleController.getString(R.string.ChannelTooMuchTitle));
                builder.setMessage(LocaleController.getString(R.string.UserChannelTooMuchJoin));
                break;
            case "USER_ALREADY_PARTICIPANT":
                builder.setTitle(LocaleController.getString(R.string.VoipGroupVoiceChat));
                builder.setMessage(LocaleController.getString(R.string.VoipGroupInviteAlreadyParticipant));
                break;
            default:
                builder.setMessage(LocaleController.getString(R.string.ErrorOccurred) + "\n" + error);
                break;
        }
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static Dialog createColorSelectDialog(Activity parentActivity, final long dialog_id, final int topicId, final int globalType, final Runnable onSelect) {
        return createColorSelectDialog(parentActivity, dialog_id, topicId, globalType, onSelect, null);
    }

    public static Dialog createColorSelectDialog(Activity parentActivity, final long dialog_id, final long topicId, final int globalType, final Runnable onSelect, Theme.ResourcesProvider resourcesProvider) {
        int currentColor;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        String key = NotificationsController.getSharedPrefKey(dialog_id, topicId);
        if (dialog_id != 0) {
            if (preferences.contains("color_" + key)) {
                currentColor = preferences.getInt("color_" + key, 0xff0000ff);
            } else {
                if (DialogObject.isChatDialog(dialog_id)) {
                    currentColor = preferences.getInt("GroupLed", 0xff0000ff);
                } else {
                    currentColor = preferences.getInt("MessagesLed", 0xff0000ff);
                }
            }
        } else if (globalType == NotificationsController.TYPE_PRIVATE) {
            currentColor = preferences.getInt("MessagesLed", 0xff0000ff);
        } else if (globalType == NotificationsController.TYPE_GROUP) {
            currentColor = preferences.getInt("GroupLed", 0xff0000ff);
        } else if (globalType == NotificationsController.TYPE_STORIES) {
            currentColor = preferences.getInt("StoriesLed", 0xff0000ff);
        } else if (globalType == NotificationsController.TYPE_REACTIONS_STORIES || globalType == NotificationsController.TYPE_REACTIONS_MESSAGES) {
            currentColor = preferences.getInt("ReactionsLed", 0xff0000ff);
        } else {
            currentColor = preferences.getInt("ChannelLed", 0xff0000ff);
        }
        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        String[] descriptions = new String[]{LocaleController.getString(R.string.ColorRed),
                LocaleController.getString(R.string.ColorOrange),
                LocaleController.getString(R.string.ColorYellow),
                LocaleController.getString(R.string.ColorGreen),
                LocaleController.getString(R.string.ColorCyan),
                LocaleController.getString(R.string.ColorBlue),
                LocaleController.getString(R.string.ColorViolet),
                LocaleController.getString(R.string.ColorPink),
                LocaleController.getString(R.string.ColorWhite)};
        final int[] selectedColor = new int[]{currentColor};
        for (int a = 0; a < 9; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity, resourcesProvider);
            cell.setPadding(dp(4), 0, dp(4), 0);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.LedColor));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Set), (dialogInterface, which) -> {
            final SharedPreferences preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
            SharedPreferences.Editor editor = preferences1.edit();
            if (dialog_id != 0) {
                editor.putInt("color_" + key, selectedColor[0]);
                NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannel(dialog_id, topicId);
            } else {
                if (globalType == NotificationsController.TYPE_PRIVATE) {
                    editor.putInt("MessagesLed", selectedColor[0]);
                } else if (globalType == NotificationsController.TYPE_GROUP) {
                    editor.putInt("GroupLed", selectedColor[0]);
                } else if (globalType == NotificationsController.TYPE_STORIES) {
                    editor.putInt("StoriesLed", selectedColor[0]);
                } else if (globalType == NotificationsController.TYPE_REACTIONS_STORIES || globalType == NotificationsController.TYPE_REACTIONS_MESSAGES) {
                    editor.putInt("ReactionLed", selectedColor[0]);
                } else {
                    editor.putInt("ChannelLed", selectedColor[0]);
                }
                NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(globalType);
            }
            editor.commit();
            if (onSelect != null) {
                onSelect.run();
            }
        });
        builder.setNeutralButton(LocaleController.getString(R.string.LedDisabled), (dialog, which) -> {
            final SharedPreferences preferences12 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
            SharedPreferences.Editor editor = preferences12.edit();
            if (dialog_id != 0) {
                editor.putInt("color_" + dialog_id, 0);
            } else if (globalType == NotificationsController.TYPE_PRIVATE) {
                editor.putInt("MessagesLed", 0);
            } else if (globalType == NotificationsController.TYPE_GROUP) {
                editor.putInt("GroupLed", 0);
            } else if (globalType == NotificationsController.TYPE_STORIES) {
                editor.putInt("StoriesLed", 0);
            } else if (globalType == NotificationsController.TYPE_REACTIONS_STORIES || globalType == NotificationsController.TYPE_REACTIONS_MESSAGES) {
                editor.putInt("ReactionsLed", 0);
            } else {
                editor.putInt("ChannelLed", 0);
            }
            editor.commit();
            if (onSelect != null) {
                onSelect.run();
            }
        });
        if (dialog_id != 0) {
            builder.setNegativeButton(LocaleController.getString(R.string.Default), (dialog, which) -> {
                final SharedPreferences preferences13 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences13.edit();
                editor.remove("color_" + key);
                editor.commit();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        return builder.create();
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final long dialogId, long topicId, final boolean globalGroup, final boolean globalAll, final Runnable onSelect) {
        return createVibrationSelectDialog(parentActivity, dialogId, topicId, globalGroup, globalAll, onSelect, null);
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final long dialogId, long topicId, final boolean globalGroup, final boolean globalAll, final Runnable onSelect, Theme.ResourcesProvider resourcesProvider) {
        String prefix;
        if (dialogId != 0) {
            prefix = "vibrate_" + dialogId;
        } else {
            prefix = globalGroup ? "vibrate_group" : "vibrate_messages";
        }
        return createVibrationSelectDialog(parentActivity, dialogId, topicId, prefix, onSelect, resourcesProvider);
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final long dialogId, long topicId, final String prefKeyPrefix, final Runnable onSelect) {
        return createVibrationSelectDialog(parentActivity, dialogId, topicId, prefKeyPrefix, onSelect, null);
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final long dialogId, long topicId, final String prefKeyPrefix, final Runnable onSelect, Theme.ResourcesProvider resourcesProvider) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        final int[] selected = new int[1];
        String[] descriptions;
        if (dialogId != 0) {
            selected[0] = preferences.getInt(prefKeyPrefix, 0);
            if (selected[0] == 3) {
                selected[0] = 2;
            } else if (selected[0] == 2) {
                selected[0] = 3;
            }
            descriptions = new String[]{
                    LocaleController.getString(R.string.VibrationDefault),
                    LocaleController.getString(R.string.Short),
                    LocaleController.getString(R.string.Long),
                    LocaleController.getString(R.string.VibrationDisabled)
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
                    LocaleController.getString(R.string.VibrationDisabled),
                    LocaleController.getString(R.string.VibrationDefault),
                    LocaleController.getString(R.string.Short),
                    LocaleController.getString(R.string.Long),
                    LocaleController.getString(R.string.OnlyIfSilent)
            };
        }

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity, resourcesProvider);
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                selected[0] = (Integer) v.getTag();

                final SharedPreferences preferences1 = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences1.edit();
                if (dialogId != 0) {
                    if (selected[0] == 0) {
                        editor.putInt(prefKeyPrefix, 0);
                    } else if (selected[0] == 1) {
                        editor.putInt(prefKeyPrefix, 1);
                    } else if (selected[0] == 2) {
                        editor.putInt(prefKeyPrefix, 3);
                    } else if (selected[0] == 3) {
                        editor.putInt(prefKeyPrefix, 2);
                    }
                    NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannel(dialogId, topicId);
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
                    if (prefKeyPrefix.equals("vibrate_channel")) {
                        NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_CHANNEL);
                    } else if (prefKeyPrefix.equals("vibrate_group")) {
                        NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_GROUP);
                    } else if (prefKeyPrefix.equals("vibrate_react")) {
                        NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_REACTIONS_MESSAGES);
                    } else {
                        NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(NotificationsController.TYPE_PRIVATE);
                    }
                }
                editor.commit();
                builder.getDismissRunnable().run();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        builder.setTitle(LocaleController.getString(R.string.Vibrate));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createLocationUpdateDialog(final Activity parentActivity, boolean expand, TLRPC.User user, final MessagesStorage.IntCallback callback, Theme.ResourcesProvider resourcesProvider) {
        final int[] selected = new int[1];

        String[] descriptions = new String[]{
                getString(R.string.SendLiveLocationFor15m),
                getString(R.string.SendLiveLocationFor1h),
                getString(R.string.SendLiveLocationFor8h),
                getString(R.string.SendLiveLocationForever)
        };

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, 0, 0, dp(4));

        TextView titleTextView = new TextView(parentActivity);
        if (expand) {
            titleTextView.setText(LocaleController.getString(R.string.LiveLocationAlertExpandMessage));
        } else if (user != null) {
            titleTextView.setText(LocaleController.formatString(R.string.LiveLocationAlertPrivate, UserObject.getFirstName(user)));
        } else {
            titleTextView.setText(LocaleController.getString(R.string.LiveLocationAlertGroup));
        }
        int textColor = resourcesProvider != null ? resourcesProvider.getColorOrDefault(Theme.key_dialogTextBlack) : Theme.getColor(Theme.key_dialogTextBlack);
        titleTextView.setTextColor(textColor);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, (expand ? 4 : 0), 24, 8));

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity, resourcesProvider);
            cell.heightDp = 42;
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setTag(a);
            int color1 = resourcesProvider != null ? resourcesProvider.getColorOrDefault(Theme.key_radioBackground) : Theme.getColor(Theme.key_radioBackground);
            int color2 = resourcesProvider != null ? resourcesProvider.getColorOrDefault(Theme.key_dialogRadioBackgroundChecked) : Theme.getColor(Theme.key_dialogRadioBackgroundChecked);
            cell.setCheckColor(color1, color2);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
        if (expand) {
            builder.setTitle(getString(R.string.LiveLocationAlertExpandTitle));
        } else {
            int topImageColor = resourcesProvider != null ? resourcesProvider.getColorOrDefault(Theme.key_dialogTopBackground) : Theme.getColor(Theme.key_dialogTopBackground);
            builder.setTopImage(new ShareLocationDrawable(parentActivity, 0), topImageColor);
        }
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.ShareFile), (dialog, which) -> {
            int time;
            if (selected[0] == 0) {
                time = 15 * 60;
            } else if (selected[0] == 1) {
                time = 60 * 60;
            } else if (selected[0] == 2) {
                time = 8 * 60 * 60;
            } else {
                time = 0x7FFFFFFF;
            }
            callback.run(time);
        });
        builder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        return builder.create();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static AlertDialog.Builder createBackgroundLocationPermissionDialog(Activity activity, TLRPC.User selfUser, Runnable cancelRunnable, Theme.ResourcesProvider resourcesProvider) {
        if (activity == null || Build.VERSION.SDK_INT < 29) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        String svg = AndroidUtilities.readRes(Theme.getCurrentTheme().isDark() ? R.raw.permission_map_dark : R.raw.permission_map);
        String pinSvg = AndroidUtilities.readRes(Theme.getCurrentTheme().isDark() ? R.raw.permission_pin_dark : R.raw.permission_pin);
        FrameLayout frameLayout = new FrameLayout(activity);
        frameLayout.setClipToOutline(true);
        frameLayout.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + dp(6), dp(6));
            }
        });

        View background = new View(activity);
        background.setBackground(SvgHelper.getDrawable(svg));
        frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        View pin = new View(activity);
        pin.setBackground(SvgHelper.getDrawable(pinSvg));
        frameLayout.addView(pin, LayoutHelper.createFrame(60, 82, Gravity.CENTER, 0, 0, 0, 0));

        BackupImageView imageView = new BackupImageView(activity);
        imageView.setRoundRadius(dp(26));
        imageView.setForUserOrChat(selfUser, new AvatarDrawable(selfUser));
        frameLayout.addView(imageView, LayoutHelper.createFrame(52, 52, Gravity.CENTER, 0, 0, 0, 11));

        builder.setTopView(frameLayout);
        float aspectRatio = 354f / 936f;
        builder.setTopViewAspectRatio(aspectRatio);
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionBackgroundLocation)));
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialog, which) -> {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 30);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), ((dialog, which) -> cancelRunnable.run()));
        return builder;
    }

    public static AlertDialog.Builder createGigagroupConvertAlert(Activity activity, DialogInterface.OnClickListener onProcess, DialogInterface.OnClickListener onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        String svg = AndroidUtilities.readRes(R.raw.gigagroup);
        FrameLayout frameLayout = new FrameLayout(activity);
        if (Build.VERSION.SDK_INT >= 21) {
            frameLayout.setClipToOutline(true);
            frameLayout.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + dp(6), dp(6));
                }
            });
        }
        float aspectRatio = 372f / 936f;
        View background = new View(activity);
        background.setBackground(new BitmapDrawable(SvgHelper.getBitmap(svg, dp(320), dp(320 * aspectRatio), false)));
        frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, -1, -1, -1, -1));

        builder.setTopView(frameLayout);
        builder.setTopViewAspectRatio(aspectRatio);
        builder.setTitle(LocaleController.getString(R.string.GigagroupAlertTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.GigagroupAlertText)));
        builder.setPositiveButton(LocaleController.getString(R.string.GigagroupAlertLearnMore), onProcess);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), onCancel);
        return builder;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static AlertDialog.Builder createDrawOverlayPermissionDialog(Activity activity, DialogInterface.OnClickListener onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        String svg = AndroidUtilities.readRes(R.raw.pip_video_request);

        FrameLayout frameLayout = new FrameLayout(activity);
        frameLayout.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR, new int[]{0xFF22364F, 0xFF22526A}));
        frameLayout.setClipToOutline(true);
        frameLayout.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + dp(6), AndroidUtilities.dpf2(6));
            }
        });

        float aspectRatio = 472f / 936f;
        View background = new View(activity);
        background.setBackground(new BitmapDrawable(SvgHelper.getBitmap(svg, dp(320), dp(320 * aspectRatio), false)));
        frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, -1, -1, -1, -1));

        builder.setTopView(frameLayout);
        builder.setTitle(LocaleController.getString(R.string.PermissionDrawAboveOtherAppsTitle));
        builder.setMessage(LocaleController.getString(R.string.PermissionDrawAboveOtherApps));
        builder.setPositiveButton(LocaleController.getString(R.string.Enable), (dialogInterface, i) -> {
            if (activity != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName())));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        });
        builder.notDrawBackgroundOnTopView(true);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), onCancel);
        builder.setTopViewAspectRatio(aspectRatio);
        return builder;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static AlertDialog.Builder createDrawOverlayGroupCallPermissionDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        String svg = AndroidUtilities.readRes(R.raw.pip_voice_request);

        GroupCallPipButton button = new GroupCallPipButton(context, 0, true);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                button.setTranslationY(getMeasuredHeight() * 0.28f - button.getMeasuredWidth() / 2f);
                button.setTranslationX(getMeasuredWidth() * 0.82f - button.getMeasuredWidth() / 2f);
            }
        };
        frameLayout.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR, new int[]{0xFF192A3D, 0xFF19514E}));
        frameLayout.setClipToOutline(true);
        frameLayout.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + dp(6), AndroidUtilities.dpf2(6));
            }
        });


        float aspectRatio = 540f / 936f;
        View background = new View(context);
        background.setBackground(new BitmapDrawable(SvgHelper.getBitmap(svg, dp(320), dp(320 * aspectRatio), false)));
        frameLayout.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, -1, -1, -1, -1));

        frameLayout.addView(button, LayoutHelper.createFrame(117, 117));

        builder.setTopView(frameLayout);
        builder.setTitle(LocaleController.getString(R.string.PermissionDrawAboveOtherAppsGroupCallTitle));
        builder.setMessage(LocaleController.getString(R.string.PermissionDrawAboveOtherAppsGroupCall));
        builder.setPositiveButton(LocaleController.getString(R.string.Enable), (dialogInterface, i) -> {
            if (context != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                        Activity activity = AndroidUtilities.findActivity(context);
                        if (activity instanceof LaunchActivity) {
                            activity.startActivityForResult(intent, 105);
                        } else {
                            context.startActivity(intent);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        builder.notDrawBackgroundOnTopView(true);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setTopViewAspectRatio(aspectRatio);
        return builder;
    }

    public static AlertDialog.Builder createContactsPermissionDialog(Activity parentActivity, MessagesStorage.IntCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTopAnimation(R.raw.permission_request_contacts, PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.ContactsPermissionAlert)));
        builder.setPositiveButton(LocaleController.getString(R.string.ContactsPermissionAlertContinue), (dialog, which) -> callback.run(1));
        builder.setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), (dialog, which) -> callback.run(0));
        return builder;
    }

    public static Dialog createFreeSpaceDialog(final LaunchActivity parentActivity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString(R.string.LowDiskSpaceTitle));
        builder.setMessage(LocaleController.getString(R.string.LowDiskSpaceMessage2));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.LowDiskSpaceButton), (dialog, which) -> parentActivity.presentFragment(new CacheControlActivity()));
        return builder.create();
    }

    public static Dialog createPrioritySelectDialog(Activity parentActivity, final long dialog_id, int topicId, final int globalType, final Runnable onSelect) {
        return createPrioritySelectDialog(parentActivity, dialog_id, topicId, globalType, onSelect, null);
    }

    public static Dialog createPrioritySelectDialog(Activity parentActivity, final long dialog_id, long topicId, final int globalType, final Runnable onSelect, Theme.ResourcesProvider resourcesProvider) {
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
                    LocaleController.getString(R.string.NotificationsPrioritySettings),
                    LocaleController.getString(R.string.NotificationsPriorityLow),
                    LocaleController.getString(R.string.NotificationsPriorityMedium),
                    LocaleController.getString(R.string.NotificationsPriorityHigh),
                    LocaleController.getString(R.string.NotificationsPriorityUrgent)
            };
        } else {
            if (globalType == NotificationsController.TYPE_PRIVATE) {
                selected[0] = preferences.getInt("priority_messages", 1);
            } else if (globalType == NotificationsController.TYPE_GROUP) {
                selected[0] = preferences.getInt("priority_group", 1);
            } else if (globalType == NotificationsController.TYPE_CHANNEL) {
                selected[0] = preferences.getInt("priority_channel", 1);
            } else if (globalType == NotificationsController.TYPE_STORIES) {
                selected[0] = preferences.getInt("priority_stories", 1);
            } else if (globalType == NotificationsController.TYPE_REACTIONS_MESSAGES || globalType == NotificationsController.TYPE_REACTIONS_STORIES) {
                selected[0] = preferences.getInt("priority_react", 1);
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
                    LocaleController.getString(R.string.NotificationsPriorityLow),
                    LocaleController.getString(R.string.NotificationsPriorityMedium),
                    LocaleController.getString(R.string.NotificationsPriorityHigh),
                    LocaleController.getString(R.string.NotificationsPriorityUrgent)
            };
        }

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity, resourcesProvider);
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
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
                    NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannel(dialog_id, topicId);
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
                    } else if (globalType == NotificationsController.TYPE_STORIES) {
                        editor.putInt("priority_stories", option);
                        selected[0] = preferences.getInt("priority_stories", 1);
                    } else if (globalType == NotificationsController.TYPE_REACTIONS_MESSAGES || globalType == NotificationsController.TYPE_REACTIONS_STORIES) {
                        editor.putInt("priority_react", option);
                        selected[0] = preferences.getInt("priority_react", 1);
                    }
                    NotificationsController.getInstance(UserConfig.selectedAccount).deleteNotificationChannelGlobal(globalType);
                }
                editor.commit();
                builder.getDismissRunnable().run();
                if (onSelect != null) {
                    onSelect.run();
                }
            });
        }
        builder.setTitle(LocaleController.getString(R.string.NotificationsImportance));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
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
                LocaleController.getString(R.string.NoPopup),
                LocaleController.getString(R.string.OnlyWhenScreenOn),
                LocaleController.getString(R.string.OnlyWhenScreenOff),
                LocaleController.getString(R.string.AlwaysShowPopup)
        };

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setTag(a);
            cell.setPadding(dp(4), 0, dp(4), 0);
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
        builder.setTitle(LocaleController.getString(R.string.PopupNotification));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createSingleChoiceDialog(Activity parentActivity, final String[] options, final String title, final int selected, final DialogInterface.OnClickListener listener) {
        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        for (int a = 0; a < options.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(dp(4), 0, dp(4), 0);
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
        builder.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
        return builder.create();
    }

    public static AlertDialog.Builder createTTLAlert(final Context context, final TLRPC.EncryptedChat encryptedChat, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.MessageLifetime));
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
                return LocaleController.getString(R.string.ShortMessageLifetimeForever);
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
        builder.setNegativeButton(LocaleController.getString(R.string.Done), (dialog, which) -> {
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
                AccountSelectCell cell = new AccountSelectCell(parentActivity, false);
                cell.setAccount(a, false);
                cell.setPadding(dp(14), 0, dp(14), 0);
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

        builder.setTitle(LocaleController.getString(R.string.SelectAccount));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
        return alertDialog[0] = builder.create();
    }

    public interface PaymentAlertDelegate {
        void didPressedNewCard();
    }

    public static void createDeleteMessagesAlert(BaseFragment fragment, TLRPC.User user, TLRPC.Chat chat, TLRPC.EncryptedChat encryptedChat, TLRPC.ChatFull chatInfo, long mergeDialogId, MessageObject selectedMessage, SparseArray<MessageObject>[] selectedMessages, MessageObject.GroupedMessages selectedGroup, int topicId, int mode, TLRPC.ChannelParticipant[] channelParticipants, Runnable onDelete, Runnable hideDim, Theme.ResourcesProvider resourcesProvider) {
        final boolean scheduled = mode == ChatActivity.MODE_SCHEDULED;
        final boolean isSavedMessages = mode == ChatActivity.MODE_SAVED;
        final boolean quickReplies = mode == ChatActivity.MODE_QUICK_REPLIES;
        if (fragment == null || user == null && chat == null && encryptedChat == null) {
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        int currentAccount = fragment.getCurrentAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        builder.setDimAlpha(hideDim != null ? .5f : .6f);
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
            dialogId = DialogObject.makeEncryptedDialogId(encryptedChat.id);
        } else if (user != null) {
            dialogId = user.id;
        } else {
            dialogId = -chat.id;
        }

        int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        boolean hasNonDiceMessages = false;
        if (selectedMessage != null) {
            hasNonDiceMessages = !selectedMessage.isDice() || Math.abs(currentDate - selectedMessage.messageOwner.date) > 24 * 60 * 60;
        } else {
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < selectedMessages[a].size(); b++) {
                    MessageObject msg = selectedMessages[a].valueAt(b);
                    if (!msg.isDice() || Math.abs(currentDate - msg.messageOwner.date) > 24 * 60 * 60) {
                        hasNonDiceMessages = true;
                        break;
                    }
                }
            }
        }

        final boolean[] deleteForAll = new boolean[1];
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
        if (chat != null && chat.megagroup && !scheduled && !isSavedMessages) {
            ArrayList<MessageObject> messages = new ArrayList<>();
            if (selectedMessage != null) {
                if (selectedMessage.messageOwner.action == null || selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionEmpty ||
                        selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser ||
                        selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink ||
                        selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    if (selectedGroup != null) {
                        messages.addAll(selectedGroup.messages);
                    } else {
                        messages.add(selectedMessage);
                    }
                }
                boolean hasOutgoing = !selectedMessage.isSendError() && selectedMessage.getDialogId() == mergeDialogId && (selectedMessage.messageOwner.action == null || selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) && selectedMessage.isOut() && (currentDate - selectedMessage.messageOwner.date) <= revokeTimeLimit;
                if (hasOutgoing) {
                    myMessagesCount++;
                }
            } else {
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
                        messages.add(msg);
                    }
                }
            }

            long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            ArrayList<TLObject> actionParticipants = messages
                    .stream()
                    .mapToLong(MessageObject::getFromChatId)
                    .distinct()
                    .mapToObj(fromId -> {
                        if (fromId > 0) {
                            return MessagesController.getInstance(currentAccount).getUser(fromId);
                        } else {
                            return MessagesController.getInstance(currentAccount).getChat(-fromId);
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(userOrChat -> {
                        if (userOrChat instanceof TLRPC.User) {
                            TLRPC.User user1 = (TLRPC.User) userOrChat;
                            return user1.id != clientUserId;
                        } else if (userOrChat instanceof TLRPC.Chat) {
                            TLRPC.Chat chat1 = (TLRPC.Chat) userOrChat;
                            return !ChatObject.hasAdminRights(chat1);
                        }
                        return false;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            if (!actionParticipants.isEmpty()) {
                if (channelParticipants == null) {
                    final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER)};

                    final int participantCount = actionParticipants.size();
                    TLRPC.ChannelParticipant[] channelParticipantsLoad = new TLRPC.ChannelParticipant[participantCount];
                    int[] requestIds = new int[participantCount];
                    int[] responseCounter = new int[1];

                    for (int i = 0; i < participantCount; i++) {
                        TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
                        req.channel = MessagesController.getInputChannel(chat);
                        req.participant = MessagesController.getInputPeer(actionParticipants.get(i));
                        final int index = i;
                        requestIds[i] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            responseCounter[0]++;
                            requestIds[index] = 0;

                            if (response != null) {
                                TLRPC.TL_channels_channelParticipant participant = (TLRPC.TL_channels_channelParticipant) response;
                                channelParticipantsLoad[index] = participant.participant;
                            }

                            if (responseCounter[0] == participantCount) {
                                try {
                                    progressDialog[0].dismiss();
                                } catch (Throwable ignore) {
                                }
                                progressDialog[0] = null;

                                createDeleteMessagesAlert(fragment, user, chat, encryptedChat, chatInfo, mergeDialogId, selectedMessage, selectedMessages, selectedGroup, topicId, mode, channelParticipantsLoad, onDelete, hideDim, resourcesProvider);
                            }
                        }));
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressDialog[0] == null) {
                            return;
                        }
                        progressDialog[0].setOnCancelListener(dialog -> {
                            for (int requestId : requestIds) {
                                if (requestId != 0) {
                                    ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
                                }
                            }
                            if (hideDim != null) {
                                hideDim.run();
                            }
                        });
                        fragment.showDialog(progressDialog[0]);
                    }, 1000);
                    return;
                }
                DeleteMessagesBottomSheet deleteMessagesBottomSheet = new DeleteMessagesBottomSheet(fragment, chat, messages, actionParticipants, channelParticipants, mergeDialogId, topicId, mode, onDelete);
                if (hideDim != null) {
                    deleteMessagesBottomSheet.setOnHideListener(i -> {
                        hideDim.run();
                    });
                }
                deleteMessagesBottomSheet.show();
                return;
            } else if (!hasNotOut && myMessagesCount > 0 && hasNonDiceMessages) {
                hasDeleteForAllCheck = true;
                FrameLayout frameLayout = new FrameLayout(activity);
                CheckBoxCell cell = new CheckBoxCell(activity, 1, resourcesProvider);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                if (chat != null && hasNotOut) {
                    cell.setText(LocaleController.getString(R.string.DeleteForAll), "", false, false);
                } else {
                    cell.setText(LocaleController.getString(R.string.DeleteMessagesOption), "", false, false);
                }
                cell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
                frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                cell.setOnClickListener(v -> {
                    CheckBoxCell cell12 = (CheckBoxCell) v;
                    deleteForAll[0] = !deleteForAll[0];
                    cell12.setChecked(deleteForAll[0], true);
                });
                builder.setView(frameLayout);
                builder.setCustomViewOffset(9);
            }
        } else if (!scheduled && !isSavedMessages && !ChatObject.isChannel(chat) && encryptedChat == null) {
            if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && (!user.bot || user.support) || chat != null) {
                if (selectedMessage != null) {
                    boolean hasOutgoing = !selectedMessage.isSendError() && (
                            selectedMessage.messageOwner.action == null ||
                                    selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionEmpty ||
                                    selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall ||
                                    selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                                    selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached ||
                                    selectedMessage.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) && (selectedMessage.isOut() || canRevokeInbox || ChatObject.hasAdminRights(chat)) && (currentDate - selectedMessage.messageOwner.date) <= revokeTimeLimit;
                    if (hasOutgoing) {
                        myMessagesCount++;
                    }
                    hasNotOut = !selectedMessage.isOut();
                } else {
                    for (int a = 1; a >= 0; a--) {
                        for (int b = 0; b < selectedMessages[a].size(); b++) {
                            MessageObject msg = selectedMessages[a].valueAt(b);
                            if (!(msg.messageOwner.action == null ||
                                    msg.messageOwner.action instanceof TLRPC.TL_messageActionEmpty ||
                                    msg.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall ||
                                    msg.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                                    msg.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached)) {
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
            if (myMessagesCount > 0 && hasNonDiceMessages && (user == null || !UserObject.isDeleted(user))) {
                hasDeleteForAllCheck = true;
                FrameLayout frameLayout = new FrameLayout(activity);
                CheckBoxCell cell = new CheckBoxCell(activity, 1, resourcesProvider);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                if (canDeleteInbox) {
                    cell.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
                } else if (chat != null && (hasNotOut || myMessagesCount == count)) {
                    cell.setText(LocaleController.getString(R.string.DeleteForAll), "", false, false);
                } else {
                    cell.setText(LocaleController.getString(R.string.DeleteMessagesOption), "", false, false);
                }
                cell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
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

        DialogInterface.OnClickListener deleteAction = (dialogInterface, i) -> {
            ArrayList<Integer> ids = null;
            long thisDialogId = dialogId;
            if (isSavedMessages) {
                thisDialogId = UserConfig.getInstance(currentAccount).getClientUserId();
            }
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
                if (mergeDialogId != 0 && selectedMessage.messageOwner.peer_id != null && selectedMessage.messageOwner.peer_id.chat_id == -mergeDialogId) {
                    thisDialogId = mergeDialogId;
                }
                MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids, encryptedChat, thisDialogId, topicId, deleteForAll[0], mode);
            } else {
                for (int a = 1; a >= 0; a--) {
                    ids = new ArrayList<>();
                    for (int b = 0; b < selectedMessages[a].size(); b++) {
                        ids.add(selectedMessages[a].keyAt(b));
                    }
                    ArrayList<Long> random_ids = null;
                    if (encryptedChat != null) {
                        random_ids = new ArrayList<>();
                        for (int b = 0; b < selectedMessages[a].size(); b++) {
                            MessageObject msg = selectedMessages[a].valueAt(b);
                            if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                random_ids.add(msg.messageOwner.random_id);
                            }
                        }
                    }
                    MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids, encryptedChat, (a == 1 && mergeDialogId != 0) ? mergeDialogId : thisDialogId, topicId, deleteForAll[0], mode);
                    selectedMessages[a].clear();
                }
            }
            if (onDelete != null) {
                onDelete.run();
            }
        };

        if (isSavedMessages) {
            if (count == 1) {
                builder.setTitle(LocaleController.getString(R.string.UnsaveSingleMessagesTitle));
            } else {
                builder.setTitle(LocaleController.formatString(R.string.UnsaveMessagesTitle, LocaleController.formatPluralString("messages", count)));
            }
        } else {
            if (count == 1) {
                builder.setTitle(LocaleController.getString(R.string.DeleteSingleMessagesTitle));
            } else {
                builder.setTitle(LocaleController.formatString(R.string.DeleteMessagesTitle, LocaleController.formatPluralString("messages", count)));
            }
        }

        if (isSavedMessages) {
            if (count == 1) {
                builder.setMessage(LocaleController.getString(R.string.AreYouSureUnsaveSingleMessage));
            } else {
                builder.setMessage(LocaleController.getString(R.string.AreYouSureUnsaveFewMessages));
            }
        } else if (chat != null && hasNotOut) {
            if (hasDeleteForAllCheck && myMessagesCount != count) {
                builder.setMessage(LocaleController.formatString(R.string.DeleteMessagesTextGroupPart, LocaleController.formatPluralString("messages", myMessagesCount)));
            } else if (count == 1) {
                builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteSingleMessage));
            } else {
                builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteFewMessages));
            }
        } else if (hasDeleteForAllCheck && !canDeleteInbox && myMessagesCount != count) {
            if (chat != null) {
                builder.setMessage(LocaleController.formatString("DeleteMessagesTextGroup", R.string.DeleteMessagesTextGroup, LocaleController.formatPluralString("messages", myMessagesCount)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteMessagesText", R.string.DeleteMessagesText, LocaleController.formatPluralString("messages", myMessagesCount), UserObject.getFirstName(user))));
            }
        } else {
            if (chat != null && chat.megagroup && !scheduled) {
                if (count == 1) {
                    builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteSingleMessageMega));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteFewMessagesMega));
                }
            } else {
                if (count == 1) {
                    builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteSingleMessage));
                } else {
                    builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteFewMessages));
                }
            }
        }

        boolean isActiveGiveawayAndOwner = false;
        String giveawayEndDate = null;
        if (selectedMessage != null) {
            isActiveGiveawayAndOwner = selectedMessage.isGiveaway() && !selectedMessage.isForwarded();
            if (isActiveGiveawayAndOwner) {
                TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) selectedMessage.messageOwner.media;
                long untilDate = giveaway.until_date * 1000L;
                giveawayEndDate = LocaleController.getInstance().getFormatterGiveawayMonthDayYear().format(new Date(untilDate));
                isActiveGiveawayAndOwner = System.currentTimeMillis() < untilDate;
            }
        } else if (count == 1) {
            for (int a = 1; a >= 0; a--) {
                for (int b = 0; b < selectedMessages[a].size(); b++) {
                    MessageObject msg = selectedMessages[a].valueAt(b);
                    isActiveGiveawayAndOwner = msg.isGiveaway() && !msg.isForwarded();
                    if (isActiveGiveawayAndOwner) {
                        TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) msg.messageOwner.media;
                        long untilDate = giveaway.until_date * 1000L;
                        giveawayEndDate = LocaleController.getInstance().getFormatterGiveawayMonthDayYear().format(new Date(untilDate));
                        isActiveGiveawayAndOwner = System.currentTimeMillis() < untilDate;
                    }
                }
            }
        }

        if (isActiveGiveawayAndOwner && !isSavedMessages) {
            builder.setTitle(LocaleController.getString(R.string.BoostingGiveawayDeleteMsgTitle));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingGiveawayDeleteMsgText", R.string.BoostingGiveawayDeleteMsgText, giveawayEndDate)));
            builder.setNeutralButton(LocaleController.getString(R.string.Delete), deleteAction);
        } else {
            builder.setPositiveButton(LocaleController.getString(isSavedMessages ? R.string.Remove : R.string.Delete), deleteAction);
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setOnPreDismissListener(di -> {
            if (hideDim != null) {
                hideDim.run();
            }
        });
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView positiveButton = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
        TextView neutralButton = (TextView) dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (neutralButton != null) {
            dialog.getButtonsLayout().setPadding(dp(12), dp(0), dp(8), dp(12));
            ((ViewGroup.MarginLayoutParams) dialog.getButtonsLayout().getLayoutParams()).topMargin = dp(-8);
            neutralButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void createThemeCreateDialog(BaseFragment fragment, int type, Theme.ThemeInfo switchToTheme, Theme.ThemeAccent switchToAccent) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setBackground(null);
        editText.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.NewTheme));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Create), (dialog, which) -> {

        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setView(linearLayout);

        final TextView message = new TextView(context);
        if (type != 0) {
            message.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EnterThemeNameEdit)));
        } else {
            message.setText(LocaleController.getString(R.string.EnterThemeName));
        }
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        message.setPadding(dp(23), dp(12), dp(23), dp(6));
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        linearLayout.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setGravity(Gravity.LEFT | Gravity.TOP);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setPadding(0, dp(4), 0, 0);
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));
        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            AndroidUtilities.hideKeyboard(textView);
            return false;
        });
        editText.setText(generateThemeName(switchToAccent));
        editText.setSelection(editText.length());

        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }));
        fragment.showDialog(alertDialog);
        editText.requestFocus();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (fragment.getParentActivity() == null) {
                return;
            }
            if (editText.length() == 0) {
                Vibrator vibrator = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
                AndroidUtilities.shakeView(editText);
                return;
            }
            if (fragment instanceof ThemePreviewActivity) {
                Theme.applyPreviousTheme();
                fragment.finishFragment();
            }
            if (switchToAccent != null) {
                switchToTheme.setCurrentAccentId(switchToAccent.id);
                Theme.refreshThemeColors();
                Utilities.searchQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> processCreate(editText, alertDialog, fragment)));
                return;
            }
            processCreate(editText, alertDialog, fragment);
        });
    }

    private static void processCreate(EditTextBoldCursor editText, AlertDialog alertDialog, BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AndroidUtilities.hideKeyboard(editText);
        Theme.ThemeInfo themeInfo = Theme.createNewTheme(editText.getText().toString());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated);

        ThemeEditorView themeEditorView = new ThemeEditorView();
        themeEditorView.show(fragment.getParentActivity(), themeInfo);
        alertDialog.dismiss();

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (preferences.getBoolean("themehint", false)) {
            return;
        }
        preferences.edit().putBoolean("themehint", true).commit();
        try {
            Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.CreateNewThemeHelp), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String generateThemeName(Theme.ThemeAccent accent) {
        List<String> adjectives = Arrays.asList(
                "Ancient",
                "Antique",
                "Autumn",
                "Baby",
                "Barely",
                "Baroque",
                "Blazing",
                "Blushing",
                "Bohemian",
                "Bubbly",
                "Burning",
                "Buttered",
                "Classic",
                "Clear",
                "Cool",
                "Cosmic",
                "Cotton",
                "Cozy",
                "Crystal",
                "Dark",
                "Daring",
                "Darling",
                "Dawn",
                "Dazzling",
                "Deep",
                "Deepest",
                "Delicate",
                "Delightful",
                "Divine",
                "Double",
                "Downtown",
                "Dreamy",
                "Dusky",
                "Dusty",
                "Electric",
                "Enchanted",
                "Endless",
                "Evening",
                "Fantastic",
                "Flirty",
                "Forever",
                "Frigid",
                "Frosty",
                "Frozen",
                "Gentle",
                "Heavenly",
                "Hyper",
                "Icy",
                "Infinite",
                "Innocent",
                "Instant",
                "Luscious",
                "Lunar",
                "Lustrous",
                "Magic",
                "Majestic",
                "Mambo",
                "Midnight",
                "Millenium",
                "Morning",
                "Mystic",
                "Natural",
                "Neon",
                "Night",
                "Opaque",
                "Paradise",
                "Perfect",
                "Perky",
                "Polished",
                "Powerful",
                "Rich",
                "Royal",
                "Sheer",
                "Simply",
                "Sizzling",
                "Solar",
                "Sparkling",
                "Splendid",
                "Spicy",
                "Spring",
                "Stellar",
                "Sugared",
                "Summer",
                "Sunny",
                "Super",
                "Sweet",
                "Tender",
                "Tenacious",
                "Tidal",
                "Toasted",
                "Totally",
                "Tranquil",
                "Tropical",
                "True",
                "Twilight",
                "Twinkling",
                "Ultimate",
                "Ultra",
                "Velvety",
                "Vibrant",
                "Vintage",
                "Virtual",
                "Warm",
                "Warmest",
                "Whipped",
                "Wild",
                "Winsome"
        );

        List<String> subjectives = Arrays.asList(
                "Ambrosia",
                "Attack",
                "Avalanche",
                "Blast",
                "Bliss",
                "Blossom",
                "Blush",
                "Burst",
                "Butter",
                "Candy",
                "Carnival",
                "Charm",
                "Chiffon",
                "Cloud",
                "Comet",
                "Delight",
                "Dream",
                "Dust",
                "Fantasy",
                "Flame",
                "Flash",
                "Fire",
                "Freeze",
                "Frost",
                "Glade",
                "Glaze",
                "Gleam",
                "Glimmer",
                "Glitter",
                "Glow",
                "Grande",
                "Haze",
                "Highlight",
                "Ice",
                "Illusion",
                "Intrigue",
                "Jewel",
                "Jubilee",
                "Kiss",
                "Lights",
                "Lollypop",
                "Love",
                "Luster",
                "Madness",
                "Matte",
                "Mirage",
                "Mist",
                "Moon",
                "Muse",
                "Myth",
                "Nectar",
                "Nova",
                "Parfait",
                "Passion",
                "Pop",
                "Rain",
                "Reflection",
                "Rhapsody",
                "Romance",
                "Satin",
                "Sensation",
                "Silk",
                "Shine",
                "Shadow",
                "Shimmer",
                "Sky",
                "Spice",
                "Star",
                "Sugar",
                "Sunrise",
                "Sunset",
                "Sun",
                "Twist",
                "Unbound",
                "Velvet",
                "Vibrant",
                "Waters",
                "Wine",
                "Wink",
                "Wonder",
                "Zone"
        );

        HashMap<Integer, String> colors = new HashMap<>();
        colors.put(0x8e0000, "Berry");
        colors.put(0xdec196, "Brandy");
        colors.put(0x800b47, "Cherry");
        colors.put(0xff7f50, "Coral");
        colors.put(0xdb5079, "Cranberry");
        colors.put(0xdc143c, "Crimson");
        colors.put(0xe0b0ff, "Mauve");
        colors.put(0xffc0cb, "Pink");
        colors.put(0xff0000, "Red");
        colors.put(0xff007f, "Rose");
        colors.put(0x80461b, "Russet");
        colors.put(0xff2400, "Scarlet");
        colors.put(0xf1f1f1, "Seashell");
        colors.put(0xff3399, "Strawberry");
        colors.put(0xffbf00, "Amber");
        colors.put(0xeb9373, "Apricot");
        colors.put(0xfbe7b2, "Banana");
        colors.put(0xa1c50a, "Citrus");
        colors.put(0xb06500, "Ginger");
        colors.put(0xffd700, "Gold");
        colors.put(0xfde910, "Lemon");
        colors.put(0xffa500, "Orange");
        colors.put(0xffe5b4, "Peach");
        colors.put(0xff6b53, "Persimmon");
        colors.put(0xe4d422, "Sunflower");
        colors.put(0xf28500, "Tangerine");
        colors.put(0xffc87c, "Topaz");
        colors.put(0xffff00, "Yellow");
        colors.put(0x384910, "Clover");
        colors.put(0x83aa5d, "Cucumber");
        colors.put(0x50c878, "Emerald");
        colors.put(0xb5b35c, "Olive");
        colors.put(0x00ff00, "Green");
        colors.put(0x00a86b, "Jade");
        colors.put(0x29ab87, "Jungle");
        colors.put(0xbfff00, "Lime");
        colors.put(0x0bda51, "Malachite");
        colors.put(0x98ff98, "Mint");
        colors.put(0xaddfad, "Moss");
        colors.put(0x315ba1, "Azure");
        colors.put(0x0000ff, "Blue");
        colors.put(0x0047ab, "Cobalt");
        colors.put(0x4f69c6, "Indigo");
        colors.put(0x017987, "Lagoon");
        colors.put(0x71d9e2, "Aquamarine");
        colors.put(0x120a8f, "Ultramarine");
        colors.put(0x000080, "Navy");
        colors.put(0x2f519e, "Sapphire");
        colors.put(0x76d7ea, "Sky");
        colors.put(0x008080, "Teal");
        colors.put(0x40e0d0, "Turquoise");
        colors.put(0x9966cc, "Amethyst");
        colors.put(0x4d0135, "Blackberry");
        colors.put(0x614051, "Eggplant");
        colors.put(0xc8a2c8, "Lilac");
        colors.put(0xb57edc, "Lavender");
        colors.put(0xccccff, "Periwinkle");
        colors.put(0x843179, "Plum");
        colors.put(0x660099, "Purple");
        colors.put(0xd8bfd8, "Thistle");
        colors.put(0xda70d6, "Orchid");
        colors.put(0x240a40, "Violet");
        colors.put(0x3f2109, "Bronze");
        colors.put(0x370202, "Chocolate");
        colors.put(0x7b3f00, "Cinnamon");
        colors.put(0x301f1e, "Cocoa");
        colors.put(0x706555, "Coffee");
        colors.put(0x796989, "Rum");
        colors.put(0x4e0606, "Mahogany");
        colors.put(0x782d19, "Mocha");
        colors.put(0xc2b280, "Sand");
        colors.put(0x882d17, "Sienna");
        colors.put(0x780109, "Maple");
        colors.put(0xf0e68c, "Khaki");
        colors.put(0xb87333, "Copper");
        colors.put(0xb94e48, "Chestnut");
        colors.put(0xeed9c4, "Almond");
        colors.put(0xfffdd0, "Cream");
        colors.put(0xb9f2ff, "Diamond");
        colors.put(0xa98307, "Honey");
        colors.put(0xfffff0, "Ivory");
        colors.put(0xeae0c8, "Pearl");
        colors.put(0xeff2f3, "Porcelain");
        colors.put(0xd1bea8, "Vanilla");
        colors.put(0xffffff, "White");
        colors.put(0x808080, "Gray");
        colors.put(0x000000, "Black");
        colors.put(0xe8f1d4, "Chrome");
        colors.put(0x36454f, "Charcoal");
        colors.put(0x0c0b1d, "Ebony");
        colors.put(0xc0c0c0, "Silver");
        colors.put(0xf5f5f5, "Smoke");
        colors.put(0x262335, "Steel");
        colors.put(0x4fa83d, "Apple");
        colors.put(0x80b3c4, "Glacier");
        colors.put(0xfebaad, "Melon");
        colors.put(0xc54b8c, "Mulberry");
        colors.put(0xa9c6c2, "Opal");
        colors.put(0x54a5f8, "Blue");

        int color;
        if (accent == null) {
            Theme.ThemeInfo themeInfo = Theme.getCurrentTheme();
            accent = themeInfo.getAccent(false);
        }
        if (accent != null && accent.accentColor != 0) {
            color = accent.accentColor;
        } else {
            color = AndroidUtilities.calcDrawableColor(Theme.getCachedWallpaper())[0];
        }

        String minKey = null;
        int minValue = Integer.MAX_VALUE;
        int r1 = Color.red(color);
        int g1 = Color.green(color);
        int b1 = Color.blue(color);

        for (HashMap.Entry<Integer, String> entry : colors.entrySet()) {
            Integer value = entry.getKey();
            int r2 = Color.red(value);
            int g2 = Color.green(value);
            int b2 = Color.blue(value);

            int rMean = (r1 + r2) / 2;
            int r = r1 - r2;
            int g = g1 - g2;
            int b = b1 - b2;
            int d = (((512 + rMean) * r * r) >> 8) + (4 * g * g) + (((767 - rMean) * b * b) >> 8);

            if (d < minValue) {
                minKey = entry.getValue();
                minValue = d;
            }
        }
        String result;
        if (Utilities.random.nextInt() % 2 == 0) {
            result = adjectives.get(Utilities.random.nextInt(adjectives.size())) + " " + minKey;
        } else {
            result = minKey + " " + subjectives.get(Utilities.random.nextInt(subjectives.size()));
        }
        return result;
    }

    @SuppressLint("ClickableViewAccessibility")
    public static ActionBarPopupWindow showPopupMenu(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, View anchorView, int offsetX, int offsetY) {
        Rect rect = new Rect();
        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setAnimationStyle(R.style.PopupAnimation);

        popupWindow.setAnimationEnabled(true);

        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);

        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.setFocusable(true);
        popupLayout.setFocusableInTouchMode(true);
        popupLayout.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && popupWindow.isShowing()) {
                popupWindow.dismiss();
                return true;
            }
            return false;
        });

        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - dp(40), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST));
        popupWindow.showAsDropDown(anchorView, offsetX, offsetY);

        popupLayout.updateRadialSelectors();
//        popupWindow.startAnimation();
        ActionBarPopupWindow.startAnimation(popupLayout);

        popupLayout.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (popupWindow != null && popupWindow.isShowing()) {
                    v.getHitRect(rect);
                    if (!rect.contains((int) event.getX(), (int) event.getY())) {
                        popupWindow.dismiss();
                    }
                }
            }
            return false;
        });
        return popupWindow;
    }

    public interface SoundFrequencyDelegate {
        void didSelectValues(int time, int minute);
    }
}
