/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColorPickerView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;

public class ProfileNotificationsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListView listView;
    private long dialog_id;

    private int settingsNotificationsRow;
    private int settingsVibrateRow;
    private int settingsSoundRow;
    private int settingsPriorityRow;
    private int settingsLedRow;
    private int smartRow;
    private int rowCount = 0;

    public ProfileNotificationsActivity(Bundle args) {
        super(args);
        dialog_id = args.getLong("dialog_id");
    }

    @Override
    public boolean onFragmentCreate() {
        settingsNotificationsRow = rowCount++;
        settingsVibrateRow = rowCount++;
        settingsSoundRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            settingsPriorityRow = rowCount++;
        } else {
            settingsPriorityRow = -1;
        }
        int lower_id = (int) dialog_id;
        if (lower_id < 0) {
            smartRow = rowCount++;
        } else {
            smartRow = 1;
        }
        settingsLedRow = rowCount++;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        AndroidUtilities.setListViewEdgeEffectColor(listView, AvatarDrawable.getProfileBackColorForId(5));
        frameLayout.addView(listView);
        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        listView.setLayoutParams(layoutParams);
        listView.setAdapter(new ListAdapter(context));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                if (i == settingsVibrateRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("Vibrate", R.string.Vibrate));
                    builder.setItems(new CharSequence[]{
                            LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled),
                            LocaleController.getString("SettingsDefault", R.string.SettingsDefault),
                            LocaleController.getString("SystemDefault", R.string.SystemDefault),
                            LocaleController.getString("Short", R.string.Short),
                            LocaleController.getString("Long", R.string.Long)
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            if (which == 0) {
                                editor.putInt("vibrate_" + dialog_id, 2);
                            } else if (which == 1) {
                                editor.putInt("vibrate_" + dialog_id, 0);
                            } else if (which == 2) {
                                editor.putInt("vibrate_" + dialog_id, 4);
                            } else if (which == 3) {
                                editor.putInt("vibrate_" + dialog_id, 1);
                            } else if (which == 4) {
                                editor.putInt("vibrate_" + dialog_id, 3);
                            }
                            editor.commit();
                            if (listView != null) {
                                listView.invalidateViews();
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (i == settingsNotificationsRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setItems(new CharSequence[]{
                            LocaleController.getString("Default", R.string.Default),
                            LocaleController.getString("Enabled", R.string.Enabled),
                            LocaleController.getString("NotificationsDisabled", R.string.NotificationsDisabled)
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("notify2_" + dialog_id, which);
                            if (which == 2) {
                                NotificationsController.getInstance().removeNotificationsForDialog(dialog_id);
                            }
                            MessagesStorage.getInstance().setDialogFlags(dialog_id, which == 2 ? 1 : 0);
                            editor.commit();
                            TLRPC.Dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
                            if (dialog != null) {
                                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                                if (which == 2) {
                                    dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                                }
                            }
                            if (listView != null) {
                                listView.invalidateViews();
                            }
                            NotificationsController.updateServerNotificationsSettings(dialog_id);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (i == settingsSoundRow) {
                    try {
                        Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        Uri currentSound = null;

                        String defaultPath = null;
                        Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                        if (defaultUri != null) {
                            defaultPath = defaultUri.getPath();
                        }

                        String path = preferences.getString("sound_path_" + dialog_id, defaultPath);
                        if (path != null && !path.equals("NoSound")) {
                            if (path.equals(defaultPath)) {
                                currentSound = defaultUri;
                            } else {
                                currentSound = Uri.parse(path);
                            }
                        }

                        tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                        startActivityForResult(tmpIntent, 12);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (i == settingsLedRow) {
                    if (getParentActivity() == null) {
                        return;
                    }

                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    final ColorPickerView colorPickerView = new ColorPickerView(getParentActivity());
                    linearLayout.addView(colorPickerView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    if (preferences.contains("color_" + dialog_id)) {
                        colorPickerView.setOldCenterColor(preferences.getInt("color_" + dialog_id, 0xff00ff00));
                    } else {
                        if ((int) dialog_id < 0) {
                            colorPickerView.setOldCenterColor(preferences.getInt("GroupLed", 0xff00ff00));
                        } else {
                            colorPickerView.setOldCenterColor(preferences.getInt("MessagesLed", 0xff00ff00));
                        }
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("LedColor", R.string.LedColor));
                    builder.setView(linearLayout);
                    builder.setPositiveButton(LocaleController.getString("Set", R.string.Set), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("color_" + dialog_id, colorPickerView.getColor());
                            editor.commit();
                            listView.invalidateViews();
                        }
                    });
                    builder.setNeutralButton(LocaleController.getString("LedDisabled", R.string.LedDisabled), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("color_" + dialog_id, 0);
                            editor.commit();
                            listView.invalidateViews();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Default", R.string.Default), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.remove("color_" + dialog_id);
                            editor.commit();
                            listView.invalidateViews();
                        }
                    });
                    showDialog(builder.create());
                } else if (i == settingsPriorityRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority));
                    builder.setItems(new CharSequence[]{
                            LocaleController.getString("SettingsDefault", R.string.SettingsDefault),
                            LocaleController.getString("NotificationsPriorityDefault", R.string.NotificationsPriorityDefault),
                            LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh),
                            LocaleController.getString("NotificationsPriorityMax", R.string.NotificationsPriorityMax)
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                which = 3;
                            } else {
                                which--;
                            }
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            preferences.edit().putInt("priority_" + dialog_id, which).commit();
                            if (listView != null) {
                                listView.invalidateViews();
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (i == smartRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int notifyMaxCount = preferences.getInt("smart_max_count_" + dialog_id, 2);
                    int notifyDelay = preferences.getInt("smart_delay_" + dialog_id, 3 * 60);
                    if (notifyMaxCount == 0) {
                        notifyMaxCount = 2;
                    }

                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);

                    LinearLayout linearLayout2 = new LinearLayout(getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2);
                    LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) linearLayout2.getLayoutParams();
                    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    linearLayout2.setLayoutParams(layoutParams1);

                    TextView textView = new TextView(getParentActivity());
                    textView.setText(LocaleController.getString("SmartNotificationsSoundAtMost", R.string.SmartNotificationsSoundAtMost));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    linearLayout2.addView(textView);
                    layoutParams1 = (LinearLayout.LayoutParams) textView.getLayoutParams();
                    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                    textView.setLayoutParams(layoutParams1);

                    final NumberPicker numberPickerTimes = new NumberPicker(getParentActivity());
                    numberPickerTimes.setMinValue(1);
                    numberPickerTimes.setMaxValue(10);
                    numberPickerTimes.setValue(notifyMaxCount);
                    linearLayout2.addView(numberPickerTimes);
                    layoutParams1 = (LinearLayout.LayoutParams) numberPickerTimes.getLayoutParams();
                    layoutParams1.width = AndroidUtilities.dp(50);
                    numberPickerTimes.setLayoutParams(layoutParams1);

                    textView = new TextView(getParentActivity());
                    textView.setText(LocaleController.getString("SmartNotificationsTimes", R.string.SmartNotificationsTimes));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    linearLayout2.addView(textView);
                    layoutParams1 = (LinearLayout.LayoutParams) textView.getLayoutParams();
                    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                    textView.setLayoutParams(layoutParams1);

                    linearLayout2 = new LinearLayout(getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2);
                    layoutParams1 = (LinearLayout.LayoutParams) linearLayout2.getLayoutParams();
                    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    linearLayout2.setLayoutParams(layoutParams1);

                    textView = new TextView(getParentActivity());
                    textView.setText(LocaleController.getString("SmartNotificationsWithin", R.string.SmartNotificationsWithin));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    linearLayout2.addView(textView);
                    layoutParams1 = (LinearLayout.LayoutParams) textView.getLayoutParams();
                    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                    textView.setLayoutParams(layoutParams1);

                    final NumberPicker numberPickerMinutes = new NumberPicker(getParentActivity());
                    numberPickerMinutes.setMinValue(1);
                    numberPickerMinutes.setMaxValue(10);
                    numberPickerMinutes.setValue(notifyDelay / 60);
                    linearLayout2.addView(numberPickerMinutes);
                    layoutParams1 = (LinearLayout.LayoutParams) numberPickerMinutes.getLayoutParams();
                    layoutParams1.width = AndroidUtilities.dp(50);
                    numberPickerMinutes.setLayoutParams(layoutParams1);

                    textView = new TextView(getParentActivity());
                    textView.setText(LocaleController.getString("SmartNotificationsMinutes", R.string.SmartNotificationsMinutes));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    linearLayout2.addView(textView);
                    layoutParams1 = (LinearLayout.LayoutParams) textView.getLayoutParams();
                    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
                    layoutParams1.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                    textView.setLayoutParams(layoutParams1);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("SmartNotifications", R.string.SmartNotifications));
                    builder.setView(linearLayout);
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            preferences.edit().putInt("smart_max_count_" + dialog_id, numberPickerTimes.getValue()).commit();
                            preferences.edit().putInt("smart_delay_" + dialog_id, numberPickerMinutes.getValue() * 60).commit();
                            if (listView != null) {
                                listView.invalidateViews();
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("SmartNotificationsDisabled", R.string.SmartNotificationsDisabled), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            preferences.edit().putInt("smart_max_count_" + dialog_id, 0).commit();
                            if (listView != null) {
                                listView.invalidateViews();
                            }
                        }
                    });
                    showDialog(builder.create());
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null) {
                Ringtone rng = RingtoneManager.getRingtone(ApplicationLoader.applicationContext, ringtone);
                if (rng != null) {
                    if(ringtone.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                        name = LocaleController.getString("SoundDefault", R.string.SoundDefault);
                    } else {
                        name = rng.getTitle(getParentActivity());
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == 12) {
                if (name != null) {
                    editor.putString("sound_" + dialog_id, name);
                    editor.putString("sound_path_" + dialog_id, ringtone.toString());
                } else {
                    editor.putString("sound_" + dialog_id, "NoSound");
                    editor.putString("sound_path_" + dialog_id, "NoSound");
                }
            }
            editor.commit();
            listView.invalidateViews();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.notificationsSettingsUpdated) {
            listView.invalidateViews();
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            return rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new TextDetailSettingsCell(mContext);
                }

                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;

                SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);

                if (i == settingsVibrateRow) {
                    int value = preferences.getInt("vibrate_" + dialog_id, 0);
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("SettingsDefault", R.string.SettingsDefault), true);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Short", R.string.Short), true);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled), true);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Long", R.string.Long), true);
                    } else if (value == 4) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("SystemDefault", R.string.SystemDefault), true);
                    }
                } else if (i == settingsNotificationsRow) {
                    int value = preferences.getInt("notify2_" + dialog_id, 0);
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("Notifications", R.string.Notifications), LocaleController.getString("Default", R.string.Default), true);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("Notifications", R.string.Notifications), LocaleController.getString("Enabled", R.string.Enabled), true);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("Notifications", R.string.Notifications), LocaleController.getString("NotificationsDisabled", R.string.NotificationsDisabled), true);
                    }  else if (value == 3) {
                        int delta = preferences.getInt("notifyuntil_" + dialog_id, 0) - ConnectionsManager.getInstance().getCurrentTime();
                        String val;
                        if (delta <= 0) {
                            val = LocaleController.getString("Enabled", R.string.Enabled);
                        } else if (delta < 60 * 60) {
                            val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60));
                        } else if (delta < 60 * 60 * 24) {
                            val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", (int) Math.ceil(delta / 60.0f / 60)));
                        } else if (delta < 60 * 60 * 24 * 365) {
                            val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", (int) Math.ceil(delta / 60.0f / 60 / 24)));
                        } else {
                            val = null;
                        }
                        if (val != null) {
                            textCell.setTextAndValue(LocaleController.getString("Notifications", R.string.Notifications), val, true);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("Notifications", R.string.Notifications), LocaleController.getString("NotificationsDisabled", R.string.NotificationsDisabled), true);
                        }
                    }
                } else if (i == settingsSoundRow) {
                    String value = preferences.getString("sound_" + dialog_id, LocaleController.getString("SoundDefault", R.string.SoundDefault));
                    if (value.equals("NoSound")) {
                        value = LocaleController.getString("NoSound", R.string.NoSound);
                    }
                    textCell.setTextAndValue(LocaleController.getString("Sound", R.string.Sound), value, true);
                } else if (i == settingsPriorityRow) {
                    int value = preferences.getInt("priority_" + dialog_id, 3);
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("NotificationsPriorityDefault", R.string.NotificationsPriorityDefault), true);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh), true);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("NotificationsPriorityMax", R.string.NotificationsPriorityMax), true);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("SettingsDefault", R.string.SettingsDefault), true);
                    }
                } else if (i == smartRow) {
                    int notifyMaxCount = preferences.getInt("smart_max_count_" + dialog_id, 2);
                    int notifyDelay = preferences.getInt("smart_delay_" + dialog_id, 3 * 60);
                    if (notifyMaxCount == 0) {
                        textCell.setTextAndValue(LocaleController.getString("SmartNotifications", R.string.SmartNotifications), LocaleController.getString("SmartNotificationsDisabled", R.string.SmartNotificationsDisabled), true);
                    } else {
                        String times = LocaleController.formatPluralString("Times", notifyMaxCount);
                        String minutes = LocaleController.formatPluralString("Minutes", notifyDelay / 60);
                        textCell.setTextAndValue(LocaleController.getString("SmartNotifications", R.string.SmartNotifications), LocaleController.formatString("SmartNotificationsInfo", R.string.SmartNotificationsInfo, times, minutes), true);
                    }
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);

                if (preferences.contains("color_" + dialog_id)) {
                    textCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), preferences.getInt("color_" + dialog_id, 0xff00ff00), false);
                } else {
                    if ((int)dialog_id < 0) {
                        textCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), preferences.getInt("GroupLed", 0xff00ff00), false);
                    } else {
                        textCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), preferences.getInt("MessagesLed", 0xff00ff00), false);
                    }
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == settingsNotificationsRow || i == settingsVibrateRow || i == settingsSoundRow || i == settingsPriorityRow || i == smartRow) {
                return 0;
            } else if (i == settingsLedRow) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
