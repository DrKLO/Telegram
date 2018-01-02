/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ReportOtherActivity;

public class AlertsCreator {

    public static Dialog processError(TLRPC.TL_error error, BaseFragment fragment, TLObject request, Object... args) {
        if (error.code == 406 || error.text == null) {
            return null;
        }
        if (request instanceof TLRPC.TL_channels_joinChannel ||
                request instanceof TLRPC.TL_channels_editAdmin ||
                request instanceof TLRPC.TL_channels_inviteToChannel ||
                request instanceof TLRPC.TL_messages_addChatUser ||
                request instanceof TLRPC.TL_messages_startBot ||
                request instanceof TLRPC.TL_channels_editBanned) {
            if (fragment != null) {
                AlertsCreator.showAddUserAlert(error.text, fragment, (Boolean) args[0]);
            } else {
                if (error.text.equals("PEER_FLOOD")) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 1);
                }
            }
        } else if (request instanceof TLRPC.TL_messages_createChat) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                AlertsCreator.showFloodWaitAlert(error.text, fragment);
            } else {
                AlertsCreator.showAddUserAlert(error.text, fragment, false);
            }
        } else if (request instanceof TLRPC.TL_channels_createChannel) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                AlertsCreator.showFloodWaitAlert(error.text, fragment);
            }
        } else if (request instanceof TLRPC.TL_messages_editMessage) {
            if (!error.text.equals("MESSAGE_NOT_MODIFIED")) {
                showSimpleAlert(fragment, LocaleController.getString("EditMessageError", R.string.EditMessageError));
            }
        } else if (request instanceof TLRPC.TL_messages_sendMessage ||
                request instanceof TLRPC.TL_messages_sendMedia ||
                request instanceof TLRPC.TL_geochats_sendMessage ||
                request instanceof TLRPC.TL_messages_sendBroadcast ||
                request instanceof TLRPC.TL_messages_sendInlineBotResult ||
                request instanceof TLRPC.TL_geochats_sendMedia ||
                request instanceof TLRPC.TL_messages_forwardMessages) {
            if (error.text.equals("PEER_FLOOD")) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 0);
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
        } else if (request instanceof TLRPC.TL_account_confirmPhone) {
            if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidCode", R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString("CodeExpired", R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else {
                showSimpleAlert(fragment, error.text);
            }
        } else if (request instanceof TLRPC.TL_auth_resendCode) {
            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                showSimpleAlert(fragment, LocaleController.getString("InvalidCode", R.string.InvalidCode));
            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                showSimpleAlert(fragment, LocaleController.getString("CodeExpired", R.string.CodeExpired));
            } else if (error.text.startsWith("FLOOD_WAIT")) {
                showSimpleAlert(fragment, LocaleController.getString("FloodWait", R.string.FloodWait));
            } else if (error.code != -1000) {
                showSimpleAlert(fragment, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
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
        if (text == null || baseFragment == null || baseFragment.getParentActivity() == null) {
            return null;
        }
        Toast toast = Toast.makeText(baseFragment.getParentActivity(), text, Toast.LENGTH_LONG);
        toast.show();
        return toast;
    }

    public static Dialog showSimpleAlert(BaseFragment baseFragment, final String text) {
        if (text == null || baseFragment == null || baseFragment.getParentActivity() == null) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        Dialog dialog = builder.create();
        baseFragment.showDialog(dialog);
        return dialog;
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
        builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        int untilTime = ConnectionsManager.getInstance().getCurrentTime();
                        if (i == 0) {
                            untilTime += 60 * 60;
                        } else if (i == 1) {
                            untilTime += 60 * 60 * 8;
                        } else if (i == 2) {
                            untilTime += 60 * 60 * 48;
                        } else if (i == 3) {
                            untilTime = Integer.MAX_VALUE;
                        }

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        long flags;
                        if (i == 3) {
                            editor.putInt("notify2_" + dialog_id, 2);
                            flags = 1;
                        } else {
                            editor.putInt("notify2_" + dialog_id, 3);
                            editor.putInt("notifyuntil_" + dialog_id, untilTime);
                            flags = ((long) untilTime << 32) | 1;
                        }
                        NotificationsController.getInstance().removeNotificationsForDialog(dialog_id);
                        MessagesStorage.getInstance().setDialogFlags(dialog_id, flags);
                        editor.commit();
                        TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                            dialog.notify_settings.mute_until = untilTime;
                        }
                        NotificationsController.updateServerNotificationsSettings(dialog_id);
                    }
                }
        );
        return builder.create();
    }

    public static Dialog createReportAlert(Context context, final long dialog_id, final BaseFragment parentFragment) {
        if (context == null || parentFragment == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setTitle(LocaleController.getString("ReportChat", R.string.ReportChat));
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("ReportChatSpam", R.string.ReportChatSpam),
                LocaleController.getString("ReportChatViolence", R.string.ReportChatViolence),
                LocaleController.getString("ReportChatPornography", R.string.ReportChatPornography),
                LocaleController.getString("ReportChatOther", R.string.ReportChatOther)
        };
        builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 3) {
                            Bundle args = new Bundle();
                            args.putLong("dialog_id", dialog_id);
                            parentFragment.presentFragment(new ReportOtherActivity(args));
                            return;
                        }
                        TLRPC.TL_account_reportPeer req = new TLRPC.TL_account_reportPeer();
                        req.peer = MessagesController.getInputPeer((int) dialog_id);
                        if (i == 0) {
                            req.reason = new TLRPC.TL_inputReportReasonSpam();
                        } else if (i == 1) {
                            req.reason = new TLRPC.TL_inputReportReasonViolence();
                        } else if (i == 2) {
                            req.reason = new TLRPC.TL_inputReportReasonPornography();
                        }
                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(TLObject response, TLRPC.TL_error error) {

                            }
                        });
                    }
                }
        );
        return builder.create();
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
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static void showAddUserAlert(String error, final BaseFragment fragment, boolean isChannel) {
        if (error == null || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        switch (error) {
            case "PEER_FLOOD":
                builder.setMessage(LocaleController.getString("NobodyLikesSpam2", R.string.NobodyLikesSpam2));
                builder.setNegativeButton(LocaleController.getString("MoreInfo", R.string.MoreInfo), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        MessagesController.openByUserName("spambot", fragment, 1);
                    }
                });
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
            default:
                builder.setMessage(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error);
                break;
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create(), true, null);
    }

    public static Dialog createColorSelectDialog(Activity parentActivity, final long dialog_id, final boolean globalGroup, final boolean globalAll, final Runnable onSelect) {
        int currentColor;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        if (globalGroup) {
            currentColor = preferences.getInt("GroupLed", 0xff0000ff);
        } else if (globalAll) {
            currentColor = preferences.getInt("MessagesLed", 0xff0000ff);
        } else {
            if (preferences.contains("color_" + dialog_id)) {
                currentColor = preferences.getInt("color_" + dialog_id, 0xff0000ff);
            } else {
                if ((int) dialog_id < 0) {
                    currentColor = preferences.getInt("GroupLed", 0xff0000ff);
                } else {
                    currentColor = preferences.getInt("MessagesLed", 0xff0000ff);
                }
            }
        }
        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        String descriptions[] = new String[] {LocaleController.getString("ColorRed", R.string.ColorRed),
                LocaleController.getString("ColorOrange", R.string.ColorOrange),
                LocaleController.getString("ColorYellow", R.string.ColorYellow),
                LocaleController.getString("ColorGreen", R.string.ColorGreen),
                LocaleController.getString("ColorCyan", R.string.ColorCyan),
                LocaleController.getString("ColorBlue", R.string.ColorBlue),
                LocaleController.getString("ColorViolet", R.string.ColorViolet),
                LocaleController.getString("ColorPink", R.string.ColorPink),
                LocaleController.getString("ColorWhite", R.string.ColorWhite)};
        final int selectedColor[] = new int[] {currentColor};
        for (int a = 0; a < 9; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(TextColorCell.colors[a], TextColorCell.colors[a]);
            cell.setTextAndValue(descriptions[a], currentColor == TextColorCell.colorsToSave[a]);
            linearLayout.addView(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int count = linearLayout.getChildCount();
                    for (int a = 0; a < count; a++) {
                        RadioColorCell cell = (RadioColorCell) linearLayout.getChildAt(a);
                        cell.setChecked(cell == v, true);
                    }
                    selectedColor[0] = TextColorCell.colorsToSave[(Integer) v.getTag()];
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("LedColor", R.string.LedColor));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Set", R.string.Set), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                if (globalAll) {
                    editor.putInt("MessagesLed", selectedColor[0]);
                } else if (globalGroup) {
                    editor.putInt("GroupLed", selectedColor[0]);
                } else {
                    editor.putInt("color_" + dialog_id, selectedColor[0]);
                }
                editor.commit();
                if (onSelect != null) {
                    onSelect.run();
                }
            }
        });
        builder.setNeutralButton(LocaleController.getString("LedDisabled", R.string.LedDisabled), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                if (globalAll) {
                    editor.putInt("MessagesLed", 0);
                } else if (globalGroup) {
                    editor.putInt("GroupLed", 0);
                } else {
                    editor.putInt("color_" + dialog_id, 0);
                }
                editor.commit();
                if (onSelect != null) {
                    onSelect.run();
                }
            }
        });
        if (!globalAll && !globalGroup) {
            builder.setNegativeButton(LocaleController.getString("Default", R.string.Default), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.remove("color_" + dialog_id);
                    editor.commit();
                    if (onSelect != null) {
                        onSelect.run();
                    }
                }
            });
        }
        return builder.create();
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final BaseFragment parentFragment, final long dialog_id, final boolean globalGroup, final boolean globalAll, final Runnable onSelect) {
        String prefix;
        if (dialog_id != 0) {
            prefix = "vibrate_";
        } else {
            prefix = globalGroup ? "vibrate_group" : "vibrate_messages";
        }
        return createVibrationSelectDialog(parentActivity, parentFragment, dialog_id, prefix, onSelect);
    }

    public static Dialog createVibrationSelectDialog(Activity parentActivity, final BaseFragment parentFragment, final long dialog_id, final String prefKeyPrefix, final Runnable onSelect) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        final int selected[] = new int[1];
        String descriptions[];
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

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selected[0] = (Integer) v.getTag();

                    final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
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
                    if (parentFragment != null) {
                        parentFragment.dismissCurrentDialig();
                    }
                    if (onSelect != null) {
                        onSelect.run();
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("Vibrate", R.string.Vibrate));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createLocationUpdateDialog(final Activity parentActivity, TLRPC.User user, final MessagesStorage.IntCallback callback) {
        final int selected[] = new int[1];

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
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int num = (Integer) v.getTag();
                    selected[0] = num;
                    int count = linearLayout.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = linearLayout.getChildAt(a);
                        if (child instanceof RadioColorCell) {
                            ((RadioColorCell) child).setChecked(child == v, true);
                        }
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTopImage(new ShareLocationDrawable(parentActivity, false), Theme.getColor(Theme.key_dialogTopBackground));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("ShareFile", R.string.ShareFile), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int time;
                if (selected[0] == 0) {
                    time = 15 * 60;
                } else if (selected[0] == 1) {
                    time = 60 * 60;
                } else {
                    time = 8 * 60 * 60;
                }
                callback.run(time);
            }
        });
        builder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createFreeSpaceDialog(final LaunchActivity parentActivity) {
        final int selected[] = new int[1];

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        int keepMedia = preferences.getInt("keep_media", 2);
        if (keepMedia == 2) {
            selected[0] = 3;
        } else if (keepMedia == 0) {
            selected[0] = 1;
        } else if (keepMedia == 1) {
            selected[0] = 2;
        } else if (keepMedia == 3) {
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
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
                    for (int a = 0; a < count; a++) {
                        View child = linearLayout.getChildAt(a);
                        if (child instanceof RadioColorCell) {
                            ((RadioColorCell) child).setChecked(child == v, true);
                        }
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("LowDiskSpaceTitle", R.string.LowDiskSpaceTitle));
        builder.setMessage(LocaleController.getString("LowDiskSpaceMessage", R.string.LowDiskSpaceMessage));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("keep_media", selected[0]).commit();
            }
        });
        builder.setNeutralButton(LocaleController.getString("ClearMediaCache", R.string.ClearMediaCache), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                parentActivity.presentFragment(new CacheControlActivity());
            }
        });
        return builder.create();
    }

    public static Dialog createPrioritySelectDialog(Activity parentActivity, final BaseFragment parentFragment, final long dialog_id, final boolean globalGroup, final boolean globalAll, final Runnable onSelect) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        final int selected[] = new int[1];
        String descriptions[];
        if (dialog_id != 0) {
            selected[0] = preferences.getInt("priority_" + dialog_id, 3);
            if (selected[0] == 3) {
                selected[0] = 0;
            } else {
                selected[0]++;
            }
            descriptions = new String[]{
                    LocaleController.getString("NotificationsPrioritySettings", R.string.NotificationsPrioritySettings),
                    LocaleController.getString("NotificationsPriorityDefault", R.string.NotificationsPriorityDefault),
                    LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh),
                    LocaleController.getString("NotificationsPriorityMax", R.string.NotificationsPriorityMax)
            };
        } else {
            if (globalAll) {
                selected[0] = preferences.getInt("priority_messages", 1);
            } else if (globalGroup) {
                selected[0] = preferences.getInt("priority_group", 1);
            }
            descriptions = new String[]{
                    LocaleController.getString("NotificationsPriorityDefault", R.string.NotificationsPriorityDefault),
                    LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh),
                    LocaleController.getString("NotificationsPriorityMax", R.string.NotificationsPriorityMax)
            };
        }

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selected[0] = (Integer) v.getTag();

                    final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    if (dialog_id != 0) {
                        if (selected[0] == 0) {
                            selected[0] = 3;
                        } else {
                            selected[0]--;
                        }
                        editor.putInt("priority_" + dialog_id, selected[0]);
                    } else {
                        editor.putInt(globalGroup ? "priority_group" : "priority_messages", selected[0]);
                    }
                    editor.commit();
                    if (parentFragment != null) {
                        parentFragment.dismissCurrentDialig();
                    }
                    if (onSelect != null) {
                        onSelect.run();
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createPopupSelectDialog(Activity parentActivity, final BaseFragment parentFragment, final boolean globalGroup, final boolean globalAll, final Runnable onSelect) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        final int selected[] = new int[1];
        if (globalAll) {
            selected[0] = preferences.getInt("popupAll", 0);
        } else if (globalGroup) {
            selected[0] = preferences.getInt("popupGroup", 0);
        }
        String descriptions[] = new String[]{
                LocaleController.getString("NoPopup", R.string.NoPopup),
                LocaleController.getString("OnlyWhenScreenOn", R.string.OnlyWhenScreenOn),
                LocaleController.getString("OnlyWhenScreenOff", R.string.OnlyWhenScreenOff),
                LocaleController.getString("AlwaysShowPopup", R.string.AlwaysShowPopup)
        };

        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setTag(a);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], selected[0] == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selected[0] = (Integer) v.getTag();

                    final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt(globalGroup ? "popupGroup" : "popupAll", selected[0]);
                    editor.commit();
                    if (parentFragment != null) {
                        parentFragment.dismissCurrentDialig();
                    }
                    if (onSelect != null) {
                        onSelect.run();
                    }
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("PopupNotification", R.string.PopupNotification));
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        return builder.create();
    }

    public static Dialog createSingleChoiceDialog(Activity parentActivity, final BaseFragment parentFragment, final String[] options, final String title, final int selected, final DialogInterface.OnClickListener listener) {
        final LinearLayout linearLayout = new LinearLayout(parentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        for (int a = 0; a < options.length; a++) {
            RadioColorCell cell = new RadioColorCell(parentActivity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options[a], selected == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int sel = (Integer) v.getTag();

                    if (parentFragment != null) {
                        parentFragment.dismissCurrentDialig();
                    }
                    listener.onClick(null, sel);
                }
            });
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
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
        numberPicker.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int value) {
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
            }
        });
        builder.setView(numberPicker);
        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
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
                    SecretChatHelper.getInstance().sendTTLMessage(encryptedChat, null);
                    MessagesStorage.getInstance().updateEncryptedChatTTL(encryptedChat);
                }
            }
        });
        return builder;
    }
//
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
}
