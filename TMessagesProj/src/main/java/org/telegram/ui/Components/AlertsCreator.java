/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.TimePicker;

import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;

import java.util.Calendar;

public class AlertsCreator {

    public static Dialog createMuteAlert(final Context context, final long dialog_id) {
        if (context == null) {
            return null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setTitle(LocaleController.getString("Notifications", R.string.Notifications));
        CharSequence[] items = new CharSequence[]{
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 1)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Hours", 8)),
                LocaleController.formatString("MuteFor", R.string.MuteFor, LocaleController.formatPluralString("Days", 2)),
                LocaleController.getString("MuteDisable", R.string.MuteDisable),
                LocaleController.getString("NotificationsOther", R.string.NotificationsOther)/*FIXME MuteOther?*/
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
                        } else if (i == 4) {
                            final Calendar userTime = Calendar.getInstance();
                            new TimePickerDialog(context, new TimePickerDialog.OnTimeSetListener() {
                                @Override
                                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                    userTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                    userTime.set(Calendar.MINUTE,minute);
                                }
                            }, userTime.get(Calendar.HOUR_OF_DAY), userTime.get(Calendar.MINUTE), true).show();
                            untilTime = (int) userTime.getTimeInMillis() / 1000;
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
}
