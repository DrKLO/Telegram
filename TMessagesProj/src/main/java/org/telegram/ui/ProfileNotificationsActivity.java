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
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.ColorPickerView;

public class ProfileNotificationsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListView listView;
    private long dialog_id;

    private int settingsNotificationsRow;
    private int settingsVibrateRow;
    private int settingsSoundRow;
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
        settingsLedRow = rowCount++;
        settingsSoundRow = rowCount++;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);

            actionBarLayer.setTitle(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds));

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });

            fragmentView = inflater.inflate(R.layout.settings_layout, container, false);

            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(new ListAdapter(getParentActivity()));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == settingsVibrateRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("Vibrate", R.string.Vibrate));
                        builder.setItems(new CharSequence[] {
                                LocaleController.getString("Disabled", R.string.Disabled),
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
                        builder.setItems(new CharSequence[] {
                                LocaleController.getString("Default", R.string.Default),
                                LocaleController.getString("Enabled", R.string.Enabled),
                                LocaleController.getString("Disabled", R.string.Disabled)
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("notify2_" + dialog_id, which);
                                MessagesStorage.getInstance().setDialogFlags(dialog_id, which == 2 ? 1 : 0);
                                editor.commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                                if (i == settingsNotificationsRow) {
                                    updateServerNotificationsSettings();
                                }
                            }
                        });
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

    public void updateServerNotificationsSettings() {
        if ((int)dialog_id == 0) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();
        req.settings.sound = "default";
        req.settings.events_mask = 0;
        req.settings.mute_until = preferences.getInt("notify2_" + dialog_id, 0) != 2 ? 0 : Integer.MAX_VALUE;
        req.settings.show_previews = preferences.getBoolean("preview_" + dialog_id, true);

        req.peer = new TLRPC.TL_inputNotifyPeer();

        if ((int)dialog_id < 0) {
            ((TLRPC.TL_inputNotifyPeer)req.peer).peer = new TLRPC.TL_inputPeerChat();
            ((TLRPC.TL_inputNotifyPeer)req.peer).peer.chat_id = -(int)dialog_id;
        } else {
            TLRPC.User user = MessagesController.getInstance().getUser((int)dialog_id);
            if (user == null) {
                return;
            }
            if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                ((TLRPC.TL_inputNotifyPeer)req.peer).peer = new TLRPC.TL_inputPeerForeign();
                ((TLRPC.TL_inputNotifyPeer)req.peer).peer.access_hash = user.access_hash;
            } else {
                ((TLRPC.TL_inputNotifyPeer)req.peer).peer = new TLRPC.TL_inputPeerContact();
            }
            ((TLRPC.TL_inputNotifyPeer)req.peer).peer.user_id = (int)dialog_id;
        }

        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
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
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);

                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == settingsVibrateRow) {
                    textView.setText(LocaleController.getString("Vibrate", R.string.Vibrate));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int value = preferences.getInt("vibrate_" + dialog_id, 0);
                    if (value == 0) {
                        detailTextView.setText(LocaleController.getString("SettingsDefault", R.string.SettingsDefault));
                    } else if (value == 1) {
                        detailTextView.setText(LocaleController.getString("Short", R.string.Short));
                    } else if (value == 2) {
                        detailTextView.setText(LocaleController.getString("Disabled", R.string.Disabled));
                    } else if (value == 3) {
                        detailTextView.setText(LocaleController.getString("Long", R.string.Long));
                    } else if (value == 4) {
                        detailTextView.setText(LocaleController.getString("SystemDefault", R.string.SystemDefault));
                    }
                } else if (i == settingsNotificationsRow) {
                    textView.setText(LocaleController.getString("Notifications", R.string.Notifications));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int value = preferences.getInt("notify2_" + dialog_id, 0);
                    if (value == 0) {
                        detailTextView.setText(LocaleController.getString("Default", R.string.Default));
                    } else if (value == 1) {
                        detailTextView.setText(LocaleController.getString("Enabled", R.string.Enabled));
                    } else if (value == 2) {
                        detailTextView.setText(LocaleController.getString("Disabled", R.string.Disabled));
                    }
                }
            } if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_detail_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);

                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == settingsSoundRow) {
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String name = preferences.getString("sound_" + dialog_id, LocaleController.getString("Default", R.string.Default));
                    if (name.equals("NoSound")) {
                        detailTextView.setText(LocaleController.getString("NoSound", R.string.NoSound));
                    } else {
                        detailTextView.setText(name);
                    }
                    textView.setText(LocaleController.getString("Sound", R.string.Sound));
                    divider.setVisibility(View.INVISIBLE);
                }
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_color_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View colorView = view.findViewById(R.id.settings_color);
                View divider = view.findViewById(R.id.settings_row_divider);
                textView.setText(LocaleController.getString("LedColor", R.string.LedColor));
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);

                if (preferences.contains("color_" + dialog_id)) {
                    colorView.setBackgroundColor(preferences.getInt("color_" + dialog_id, 0xff00ff00));
                } else {
                    if ((int)dialog_id < 0) {
                        colorView.setBackgroundColor(preferences.getInt("GroupLed", 0xff00ff00));
                    } else {
                        colorView.setBackgroundColor(preferences.getInt("MessagesLed", 0xff00ff00));
                    }
                }
                divider.setVisibility(View.VISIBLE);
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == settingsNotificationsRow || i == settingsVibrateRow) {
                return 0;
            } else if (i == settingsSoundRow) {
                return 1;
            } else if (i == settingsLedRow) {
                return 2;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
