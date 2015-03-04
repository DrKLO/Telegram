/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColorPickerView;

public class ProfileNotificationsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListView listView;
    private long dialog_id;

    private int settingsNotificationsRow;
    private int settingsVibrateRow;
    private int settingsSoundRow;
    private int settingsPriorityRow;
    private int settingsSmartNotifyRow;
    private int settingsLedRow;
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
        if (dialog_id < 0) {
            settingsSmartNotifyRow = rowCount++;
        }
        else {
            settingsSmartNotifyRow = -1;
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
    public View createView(LayoutInflater inflater) {
        if (fragmentView == null) {
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

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;

            listView = new ListView(getParentActivity());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            AndroidUtilities.setListViewEdgeEffectColor(listView, AvatarDrawable.getProfileBackColorForId(5));
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(new ListAdapter(getParentActivity()));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == settingsVibrateRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("Vibrate", R.string.Vibrate));
                        builder.setItems(new CharSequence[] {
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
                        showAlertDialog(builder);
                    } else if (i == settingsNotificationsRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        if (dialog_id < 0) {
                            builder.setItems(new CharSequence[]{
                                    LocaleController.getString("Default", R.string.Default),
                                    LocaleController.getString("Enabled", R.string.Enabled),
                                    LocaleController.getString("NotificationsDisabled", R.string.NotificationsDisabled),
                                    LocaleController.getString("Smart Notification", R.string.SmartNotification)
                            }, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    if (which == 3) {
                                        which = 4;  //Leave space for "Mute group for D duration"
                                        editor.putBoolean("smart_notify_" + dialog_id, true);
                                    } else {
                                        editor.putBoolean("smart_notify_" + dialog_id, false);
                                    }
                                    editor.putInt("notify2_" + dialog_id, which);
                                    MessagesStorage.getInstance().setDialogFlags(dialog_id, which == 2 ? 1 : 0);
                                    editor.commit();
                                    TLRPC.TL_dialog tl_dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
                                    if (tl_dialog != null) {
                                        tl_dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                                        if (which == 2) {
                                            tl_dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                                        }
                                    }
                                    if (listView != null) {
                                        listView.invalidateViews();
                                    }
                                    NotificationsController.updateServerNotificationsSettings(dialog_id);
                                }
                            });
                        }
                        else {
                            builder.setItems(new CharSequence[]{
                                    LocaleController.getString("Default", R.string.Default),
                                    LocaleController.getString("Enabled", R.string.Enabled),
                                    LocaleController.getString("NotificationsDisabled", R.string.NotificationsDisabled),
                            }, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putInt("notify2_" + dialog_id, which);
                                    MessagesStorage.getInstance().setDialogFlags(dialog_id, which == 2 ? 1 : 0);
                                    editor.commit();
                                    TLRPC.TL_dialog tl_dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
                                    if (tl_dialog != null) {
                                        tl_dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                                        if (which == 2) {
                                            tl_dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                                        }
                                    }
                                    if (listView != null) {
                                        listView.invalidateViews();
                                    }
                                    NotificationsController.updateServerNotificationsSettings(dialog_id);
                                }
                            });
                        }
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
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

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.settings_color_dialog_layout, null, false);
                        final ColorPickerView colorPickerView = (ColorPickerView)view.findViewById(R.id.color_picker);

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        if (preferences.contains("color_" + dialog_id)) {
                            colorPickerView.setOldCenterColor(preferences.getInt("color_" + dialog_id, 0xff00ff00));
                        } else {
                            if ((int)dialog_id < 0) {
                                colorPickerView.setOldCenterColor(preferences.getInt("GroupLed", 0xff00ff00));
                            } else {
                                colorPickerView.setOldCenterColor(preferences.getInt("MessagesLed", 0xff00ff00));
                            }
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("LedColor", R.string.LedColor));
                        builder.setView(view);
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
                        builder.setNeutralButton(LocaleController.getString("Disabled", R.string.Disabled), new DialogInterface.OnClickListener() {
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
                        showAlertDialog(builder);
                    } else if (i == settingsPriorityRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority));
                        builder.setItems(new CharSequence[] {
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
                        showAlertDialog(builder);
                    } else if (i == settingsSmartNotifyRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.settings_smart_notify, null, false);

                        String[] timeUnits = {
                                LocaleController.getString("Seconds", R.string.TimeUnitSeconds),
                                LocaleController.getString("Minutes", R.string.TimeUnitMinutes),
                                LocaleController.getString("Hours", R.string.TimeUnitHours),
                                LocaleController.getString("Days", R.string.TimeUnitDays)
                        };
                        final Spinner timeframeUnitSpinner = (Spinner) view.findViewById(R.id.timeframeunitSpinner);
                        ArrayAdapter<String> adapter = new ArrayAdapter<String> (li.getContext(), android.R.layout.simple_spinner_item, timeUnits);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        timeframeUnitSpinner.setAdapter(adapter);

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);

                        final NumberPicker maxcountNumberPicker = (NumberPicker) view.findViewById(R.id.maxcountNumberPicker);
                        maxcountNumberPicker.setMinValue(1);
                        maxcountNumberPicker.setMaxValue(10);
                        maxcountNumberPicker.setValue(preferences.getInt ("smart_notify_max_count_" + dialog_id, 1));

                        final NumberPicker timeframeNumberPicker = (NumberPicker) view.findViewById(R.id.timeframeNumberPicker);
                        timeframeNumberPicker.setMinValue(1);
                        timeframeNumberPicker.setMaxValue(100);

                        long timeframe = preferences.getLong("smart_notify_timeframe_" + dialog_id, 1);
                        long multiplier = (timeframe % 86400L == 0L) ? 86400L : ((timeframe % 3600L == 0L) ? 3600L : ((timeframe % 60L == 0L) ? 60L : 1L));
                        timeframe = timeframe / multiplier;
                        timeframeUnitSpinner.setSelection((multiplier == 1L) ? 0 : ((multiplier == 60L) ? 1 : (multiplier == 3600L) ? 2 : 3));
                        timeframeNumberPicker.setValue((int)timeframe);

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("Smart Notification", R.string.SmartNotification));
                        builder.setView(view);
                        builder.setPositiveButton(LocaleController.getString("Set", R.string.Set), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                int unit = timeframeUnitSpinner.getSelectedItemPosition();
                                long multiplier = (unit == 0) ? 1L : ((unit == 1) ? 60L : ((unit == 2) ? 3600L : 86400L));
                                editor.putInt("smart_notify_max_count_" + dialog_id, maxcountNumberPicker.getValue());
                                editor.putLong("smart_notify_timeframe_" + dialog_id, timeframeNumberPicker.getValue() * multiplier);
                                editor.commit();
                                listView.invalidateViews();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Default", R.string.Default), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("smart_notify_max_count_" + dialog_id, 1);
                                editor.putLong("smart_notify_timeframe_" + dialog_id, 1);
                                editor.commit();
                                listView.invalidateViews();
                            }
                        });
                        showAlertDialog(builder);
                    }
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
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
                        name = LocaleController.getString("Default", R.string.Default);
                    } else {
                        name = rng.getTitle(getParentActivity());
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == 12) {
                if (name != null && ringtone != null) {
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
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            if (i < 0)
                return false;
            if (i == settingsSmartNotifyRow) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                if (!preferences.getBoolean("smart_notify_" + dialog_id, false))
                    return false;
            }
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
                    } else if (value == 4) {
                        textCell.setTextAndValue(LocaleController.getString("Notifications", R.string.Notifications), LocaleController.getString("Smart Notification", R.string.SmartNotification), true);
                    }
                } else if (i == settingsSoundRow) {
                    String value = preferences.getString("sound_" + dialog_id, LocaleController.getString("Default", R.string.Default));
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
                } else if (i == settingsSmartNotifyRow) {
                    String value = LocaleController.getString("Disabled", R.string.Disabled);
                    if (preferences.getBoolean("smart_notify_" + dialog_id, false)) {
                        long timeframe = preferences.getLong("smart_notify_timeframe_" + dialog_id, 1);
                        long multiplier = (timeframe % 86400L == 0L) ? 86400L : ((timeframe % 3600L == 0L) ? 3600L : ((timeframe % 60L == 0L) ? 60L : 1L));
                        timeframe = timeframe / multiplier;
                        String[] timeUnits = {
                                LocaleController.getString("Seconds", R.string.TimeUnitSeconds),
                                LocaleController.getString("Minutes", R.string.TimeUnitMinutes),
                                LocaleController.getString("Hours", R.string.TimeUnitHours),
                                LocaleController.getString("Days", R.string.TimeUnitDays)
                        };
                        value = LocaleController.getString("Sound at most", R.string.settings_smart_notify_begin);
                        value += " ";
                        value += preferences.getInt("smart_notify_max_count_" + dialog_id, 1);
                        value += " ";
                        value += LocaleController.getString("time(s)", R.string.settings_smart_notify_mid1);
                        value += " ";
                        value += LocaleController.getString("within", R.string.settings_smart_notify_mid2);
                        value += " ";
                        value += timeframe;
                        value += " ";
                        value += timeUnits [((multiplier == 1L)? 0: (multiplier == 60L)? 1 : (multiplier == 3600L)? 2 : 3)];
                        value += " ";
                        value += LocaleController.getString(".", R.string.settings_smart_notify_end);
                    }
                    textCell.setTextAndValue(LocaleController.getString("Smart Notification", R.string.SmartNotification), value, true);
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
            if (i == settingsNotificationsRow || i == settingsVibrateRow || i == settingsSoundRow || i == settingsPriorityRow) {
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
