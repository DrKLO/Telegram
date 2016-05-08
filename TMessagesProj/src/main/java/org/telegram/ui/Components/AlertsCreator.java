/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ReportOtherActivity;

public class AlertsCreator {

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
                        TLRPC.Dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
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
        fragment.showDialog(builder.create(), true);
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
            default:
                builder.setMessage(error);
                break;
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create(), true);
    }
}
