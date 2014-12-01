/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
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
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationsController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ColorPickerView;

public class NotificationsSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView listView;
    private boolean reseting = false;

    private int notificationsServiceRow;
    private int messageSectionRow2;
    private int messageSectionRow;
    private int messageAlertRow;
    private int messagePreviewRow;
    private int messageVibrateRow;
    private int messageSoundRow;
    private int messageLedRow;
    private int messagePopupNotificationRow;
    private int messagePriorityRow;
    private int groupSectionRow2;
    private int groupSectionRow;
    private int groupAlertRow;
    private int groupPreviewRow;
    private int groupVibrateRow;
    private int groupSoundRow;
    private int groupLedRow;
    private int groupPopupNotificationRow;
    private int groupPriorityRow;
    private int inappSectionRow2;
    private int inappSectionRow;
    private int inappSoundRow;
    private int inappVibrateRow;
    private int inappPreviewRow;
    private int inappPriorityRow;
    private int eventsSectionRow2;
    private int eventsSectionRow;
    private int contactJoinedRow;
    private int otherSectionRow2;
    private int otherSectionRow;
    private int badgeNumberRow;
    private int pebbleAlertRow;
    private int repeatRow;
    private int resetSectionRow2;
    private int resetSectionRow;
    private int resetNotificationsRow;
    private int rowCount = 0;

    @Override
    public boolean onFragmentCreate() {
        notificationsServiceRow = rowCount++;
        messageSectionRow2 = rowCount++;
        messageSectionRow = rowCount++;
        messageAlertRow = rowCount++;
        messagePreviewRow = rowCount++;
        messageLedRow = rowCount++;
        messageVibrateRow = rowCount++;
        messagePopupNotificationRow = rowCount++;
        messageSoundRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            messagePriorityRow = rowCount++;
        } else {
            messagePriorityRow = -1;
        }
        groupSectionRow2 = rowCount++;
        groupSectionRow = rowCount++;
        groupAlertRow = rowCount++;
        groupPreviewRow = rowCount++;
        groupLedRow = rowCount++;
        groupVibrateRow = rowCount++;
        groupPopupNotificationRow = rowCount++;
        groupSoundRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            groupPriorityRow = rowCount++;
        } else {
            groupPriorityRow = -1;
        }
        inappSectionRow2 = rowCount++;
        inappSectionRow = rowCount++;
        inappSoundRow = rowCount++;
        inappVibrateRow = rowCount++;
        inappPreviewRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            inappPriorityRow = rowCount++;
        } else {
            inappPriorityRow = -1;
        }
        eventsSectionRow2 = rowCount++;
        eventsSectionRow = rowCount++;
        contactJoinedRow = rowCount++;
        otherSectionRow2 = rowCount++;
        otherSectionRow = rowCount++;
        badgeNumberRow = rowCount++;
        pebbleAlertRow = rowCount++;
        repeatRow = rowCount++;
        resetSectionRow2 = rowCount++;
        resetSectionRow = rowCount++;
        resetNotificationsRow = rowCount++;

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
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(new ListAdapter(getParentActivity()));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    boolean enabled = false;
                    if (i == messageAlertRow || i == groupAlertRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        if (i == messageAlertRow) {
                            enabled = preferences.getBoolean("EnableAll", true);
                            editor.putBoolean("EnableAll", !enabled);
                        } else if (i == groupAlertRow) {
                            enabled = preferences.getBoolean("EnableGroup", true);
                            editor.putBoolean("EnableGroup", !enabled);
                        }
                        editor.commit();
                        updateServerNotificationsSettings(i == groupAlertRow);
                    } else if (i == messagePreviewRow || i == groupPreviewRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        if (i == messagePreviewRow) {
                            enabled = preferences.getBoolean("EnablePreviewAll", true);
                            editor.putBoolean("EnablePreviewAll", !enabled);
                        } else if (i == groupPreviewRow) {
                            enabled = preferences.getBoolean("EnablePreviewGroup", true);
                            editor.putBoolean("EnablePreviewGroup", !enabled);
                        }
                        editor.commit();
                        updateServerNotificationsSettings(i == groupPreviewRow);
                    } else if (i == messageSoundRow || i == groupSoundRow) {
                        try {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                            Uri currentSound = null;

                            String defaultPath = null;
                            Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                            if (defaultUri != null) {
                                defaultPath = defaultUri.getPath();
                            }

                            if (i == messageSoundRow) {
                                String path = preferences.getString("GlobalSoundPath", defaultPath);
                                if (path != null && !path.equals("NoSound")) {
                                    if (path.equals(defaultPath)) {
                                        currentSound = defaultUri;
                                    } else {
                                        currentSound = Uri.parse(path);
                                    }
                                }
                            } else if (i == groupSoundRow) {
                                String path = preferences.getString("GroupSoundPath", defaultPath);
                                if (path != null && !path.equals("NoSound")) {
                                    if (path.equals(defaultPath)) {
                                        currentSound = defaultUri;
                                    } else {
                                        currentSound = Uri.parse(path);
                                    }
                                }
                            }
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                            startActivityForResult(tmpIntent, i);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (i == resetNotificationsRow) {
                        if (reseting) {
                            return;
                        }
                        reseting = true;
                        TLRPC.TL_account_resetNotifySettings req = new TLRPC.TL_account_resetNotifySettings();
                        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                            @Override
                            public void run(TLObject response, TLRPC.TL_error error) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MessagesController.getInstance().enableJoined = true;
                                        reseting = false;
                                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                        SharedPreferences.Editor editor = preferences.edit();
                                        editor.clear();
                                        editor.commit();
                                        if (listView != null) {
                                            listView.invalidateViews();
                                        }
                                        if (getParentActivity() != null) {
                                            Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("ResetNotificationsText", R.string.ResetNotificationsText), Toast.LENGTH_SHORT);
                                            toast.show();
                                        }
                                    }
                                });
                            }
                        });
                    } else if (i == inappSoundRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("EnableInAppSounds", true);
                        editor.putBoolean("EnableInAppSounds", !enabled);
                        editor.commit();
                    } else if (i == inappVibrateRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("EnableInAppVibrate", true);
                        editor.putBoolean("EnableInAppVibrate", !enabled);
                        editor.commit();
                    } else if (i == inappPreviewRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("EnableInAppPreview", true);
                        editor.putBoolean("EnableInAppPreview", !enabled);
                        editor.commit();
                    } else if (i == inappPriorityRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("EnableInAppPriority", false);
                        editor.putBoolean("EnableInAppPriority", !enabled);
                        editor.commit();
                    } else if (i == contactJoinedRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("EnableContactJoined", true);
                        MessagesController.getInstance().enableJoined = !enabled;
                        editor.putBoolean("EnableContactJoined", !enabled);
                        editor.commit();
                    } else if (i == pebbleAlertRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("EnablePebbleNotifications", false);
                        editor.putBoolean("EnablePebbleNotifications", !enabled);
                        editor.commit();
                    } else if (i == badgeNumberRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        enabled = preferences.getBoolean("badgeNumber", true);
                        editor.putBoolean("badgeNumber", !enabled);
                        editor.commit();
                        NotificationsController.getInstance().setBadgeEnabled(!enabled);
                    } else if (i == notificationsServiceRow) {
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        enabled = preferences.getBoolean("pushService", true);
                        if (!enabled) {
                            final SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean("pushService", !enabled);
                            editor.commit();
                            ApplicationLoader.startPushService();
                        } else {
                            if (getParentActivity() == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString("NotificationsServiceDisableInfo", R.string.NotificationsServiceDisableInfo));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    ApplicationLoader.stopPushService();
                                    final SharedPreferences.Editor editor = preferences.edit();
                                    editor.putBoolean("pushService", false);
                                    editor.commit();
                                    listView.invalidateViews();
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showAlertDialog(builder);
                        }
                    } else if (i == messageLedRow || i == groupLedRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.settings_color_dialog_layout, null, false);
                        final ColorPickerView colorPickerView = (ColorPickerView)view.findViewById(R.id.color_picker);

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        if (i == messageLedRow) {
                            colorPickerView.setOldCenterColor(preferences.getInt("MessagesLed", 0xff00ff00));
                        } else if (i == groupLedRow) {
                            colorPickerView.setOldCenterColor(preferences.getInt("GroupLed", 0xff00ff00));
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("LedColor", R.string.LedColor));
                        builder.setView(view);
                        builder.setPositiveButton(LocaleController.getString("Set", R.string.Set), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                if (i == messageLedRow) {
                                    editor.putInt("MessagesLed", colorPickerView.getColor());
                                } else if (i == groupLedRow) {
                                    editor.putInt("GroupLed", colorPickerView.getColor());
                                }
                                editor.commit();
                                listView.invalidateViews();
                            }
                        });
                        builder.setNeutralButton(LocaleController.getString("Disabled", R.string.Disabled), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                if (i == messageLedRow) {
                                    editor.putInt("MessagesLed", 0);
                                } else if (i == groupLedRow) {
                                    editor.putInt("GroupLed", 0);
                                }
                                editor.commit();
                                listView.invalidateViews();
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == messagePopupNotificationRow || i == groupPopupNotificationRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("PopupNotification", R.string.PopupNotification));
                        builder.setItems(new CharSequence[] {
                                LocaleController.getString("NoPopup", R.string.NoPopup),
                                LocaleController.getString("OnlyWhenScreenOn", R.string.OnlyWhenScreenOn),
                                LocaleController.getString("OnlyWhenScreenOff", R.string.OnlyWhenScreenOff),
                                LocaleController.getString("AlwaysShowPopup", R.string.AlwaysShowPopup)
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                if (i == messagePopupNotificationRow) {
                                    editor.putInt("popupAll", which);
                                } else if (i == groupPopupNotificationRow) {
                                    editor.putInt("popupGroup", which);
                                }
                                editor.commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == messageVibrateRow || i == groupVibrateRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("Vibrate", R.string.Vibrate));
                        builder.setItems(new CharSequence[] {
                                LocaleController.getString("Disabled", R.string.Disabled),
                                LocaleController.getString("Default", R.string.Default),
                                LocaleController.getString("Short", R.string.Short),
                                LocaleController.getString("Long", R.string.Long)
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                String param = "vibrate_messages";
                                if (i == groupVibrateRow) {
                                    param = "vibrate_group";
                                }
                                if (which == 0) {
                                    editor.putInt(param, 2);
                                } else if (which == 1) {
                                    editor.putInt(param, 0);
                                } else if (which == 2) {
                                    editor.putInt(param, 1);
                                } else if (which == 3) {
                                    editor.putInt(param, 3);
                                }
                                editor.commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == messagePriorityRow || i == groupPriorityRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority));
                        builder.setItems(new CharSequence[] {
                                LocaleController.getString("NotificationsPriorityDefault", R.string.NotificationsPriorityDefault),
                                LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh),
                                LocaleController.getString("NotificationsPriorityMax", R.string.NotificationsPriorityMax)
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                if (i == messagePriorityRow) {
                                    preferences.edit().putInt("priority_messages", which).commit();
                                } else if (i == groupPriorityRow) {
                                    preferences.edit().putInt("priority_group", which).commit();
                                }
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == repeatRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("RepeatNotifications", R.string.RepeatNotifications));
                        builder.setItems(new CharSequence[] {
                                LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever),
                                LocaleController.formatPluralString("Minutes", 5),
                                LocaleController.formatPluralString("Minutes", 10),
                                LocaleController.formatPluralString("Minutes", 30),
                                LocaleController.formatPluralString("Hours", 1),
                                LocaleController.formatPluralString("Hours", 2),
                                LocaleController.formatPluralString("Hours", 4)
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int minutes = 0;
                                if (which == 1) {
                                    minutes = 5;
                                } else if (which == 2) {
                                    minutes = 10;
                                } else if (which == 3) {
                                    minutes = 30;
                                } else if (which == 4) {
                                    minutes = 60;
                                } else if (which == 5) {
                                    minutes = 60 * 2;
                                } else if (which == 6) {
                                    minutes = 60 * 4;
                                }
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                preferences.edit().putInt("repeat_messages", minutes).commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    }
                    if (view instanceof TextCheckCell) {
                        ((TextCheckCell) view).setChecked(!enabled);
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

    public void updateServerNotificationsSettings(boolean group) {
        //disable global settings sync
        /*SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();
        req.settings.sound = "default";
        req.settings.events_mask = 0;
        if (!group) {
            req.peer = new TLRPC.TL_inputNotifyUsers();
            req.settings.mute_until = preferences.getBoolean("EnableAll", true) ? 0 : Integer.MAX_VALUE;
            req.settings.show_previews = preferences.getBoolean("EnablePreviewAll", true);
        } else {
            req.peer = new TLRPC.TL_inputNotifyChats();
            req.settings.mute_until = preferences.getBoolean("EnableGroup", true) ? 0 : Integer.MAX_VALUE;
            req.settings.show_previews = preferences.getBoolean("EnablePreviewGroup", true);
        }
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });*/
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null) {
                Ringtone rng = RingtoneManager.getRingtone(getParentActivity(), ringtone);
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

            if (requestCode == messageSoundRow) {
                if (name != null && ringtone != null) {
                    editor.putString("GlobalSound", name);
                    editor.putString("GlobalSoundPath", ringtone.toString());
                } else {
                    editor.putString("GlobalSound", "NoSound");
                    editor.putString("GlobalSoundPath", "NoSound");
                }
            } else if (requestCode == groupSoundRow) {
                if (name != null && ringtone != null) {
                    editor.putString("GroupSound", name);
                    editor.putString("GroupSoundPath", ringtone.toString());
                } else {
                    editor.putString("GroupSound", "NoSound");
                    editor.putString("GroupSoundPath", "NoSound");
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
            return !(i == messageSectionRow || i == groupSectionRow || i == inappSectionRow ||
                    i == eventsSectionRow || i == otherSectionRow || i == resetSectionRow ||
                    i == messageSectionRow2 || i == eventsSectionRow2 || i == groupSectionRow2 ||
                    i == inappSectionRow2 || i == otherSectionRow2 || i == resetSectionRow2);
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
                    view = new HeaderCell(mContext);
                }
                if (i == messageSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("MessageNotifications", R.string.MessageNotifications));
                } else if (i == groupSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("GroupNotifications", R.string.GroupNotifications));
                } else if (i == inappSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("InAppNotifications", R.string.InAppNotifications));
                } else if (i == eventsSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("Events", R.string.Events));
                } else if (i == otherSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("NotificationsOther", R.string.NotificationsOther));
                } else if (i == resetSectionRow) {
                    ((HeaderCell) view).setText(LocaleController.getString("Reset", R.string.Reset));
                }
            } if (type == 1) {
                if (view == null) {
                    view = new TextCheckCell(mContext);
                }
                TextCheckCell checkCell = (TextCheckCell) view;

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                if (i == messageAlertRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("Alert", R.string.Alert), preferences.getBoolean("EnableAll", true), true);
                } else if (i == groupAlertRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("Alert", R.string.Alert), preferences.getBoolean("EnableGroup", true), true);
                } else if (i == messagePreviewRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), preferences.getBoolean("EnablePreviewAll", true), true);
                } else if (i == groupPreviewRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), preferences.getBoolean("EnablePreviewGroup", true), true);
                } else if (i == inappSoundRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("InAppSounds", R.string.InAppSounds), preferences.getBoolean("EnableInAppSounds", true), true);
                } else if (i == inappVibrateRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("InAppVibrate", R.string.InAppVibrate), preferences.getBoolean("EnableInAppVibrate", true), true);
                } else if (i == inappPreviewRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("InAppPreview", R.string.InAppPreview), preferences.getBoolean("EnableInAppPreview", true), true);
                } else if (i == inappPriorityRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), preferences.getBoolean("EnableInAppPriority", false), false);
                } else if (i == contactJoinedRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("ContactJoined", R.string.ContactJoined), preferences.getBoolean("EnableContactJoined", true), false);
                } else if (i == pebbleAlertRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("Pebble", R.string.Pebble), preferences.getBoolean("EnablePebbleNotifications", false), true);
                } else if (i == notificationsServiceRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("NotificationsService", R.string.NotificationsService), preferences.getBoolean("pushService", true), false);
                } else if (i == badgeNumberRow) {
                    checkCell.setTextAndCheck(LocaleController.getString("BadgeNumber", R.string.BadgeNumber), preferences.getBoolean("badgeNumber", true), true);
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new TextDetailSettingsCell(mContext);
                }

                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);

                if (i == messageSoundRow || i == groupSoundRow) {
                    textCell.setMultilineDetail(false);
                    String value = null;
                    if (i == messageSoundRow) {
                        value = preferences.getString("GlobalSound", LocaleController.getString("Default", R.string.Default));
                    } else if (i == groupSoundRow) {
                        value = preferences.getString("GroupSound", LocaleController.getString("Default", R.string.Default));
                    }
                    if (value.equals("NoSound")) {
                        value = LocaleController.getString("NoSound", R.string.NoSound);
                    }
                    textCell.setTextAndValue(LocaleController.getString("Sound", R.string.Sound), value, true);
                } else if (i == resetNotificationsRow) {
                    textCell.setMultilineDetail(true);
                    textCell.setTextAndValue(LocaleController.getString("ResetAllNotifications", R.string.ResetAllNotifications), LocaleController.getString("UndoAllCustom", R.string.UndoAllCustom), false);
                } else if (i == messagePopupNotificationRow || i == groupPopupNotificationRow) {
                    textCell.setMultilineDetail(false);
                    int option = 0;
                    if (i == messagePopupNotificationRow) {
                        option = preferences.getInt("popupAll", 0);
                    } else if (i == groupPopupNotificationRow) {
                        option = preferences.getInt("popupGroup", 0);
                    }
                    String value;
                    if (option == 0) {
                        value = LocaleController.getString("NoPopup", R.string.NoPopup);
                    } else if (option == 1) {
                        value = LocaleController.getString("OnlyWhenScreenOn", R.string.OnlyWhenScreenOn);
                    } else if (option == 2) {
                        value = LocaleController.getString("OnlyWhenScreenOff", R.string.OnlyWhenScreenOff);
                    } else {
                        value = LocaleController.getString("AlwaysShowPopup", R.string.AlwaysShowPopup);
                    }
                    textCell.setTextAndValue(LocaleController.getString("PopupNotification", R.string.PopupNotification), value, true);
                } else if (i == messageVibrateRow || i == groupVibrateRow) {
                    textCell.setMultilineDetail(false);
                    int value = 0;
                    if (i == messageVibrateRow) {
                        value = preferences.getInt("vibrate_messages", 0);
                    } else if (i == groupVibrateRow) {
                        value = preferences.getInt("vibrate_group", 0);
                    }
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Default", R.string.Default), true);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Short", R.string.Short), true);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Disabled", R.string.Disabled), true);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Long", R.string.Long), true);
                    }
                } else if (i == repeatRow) {
                    textCell.setMultilineDetail(false);
                    int minutes = preferences.getInt("repeat_messages", 60);
                    String value;
                    if (minutes == 0) {
                        value = LocaleController.getString("RepeatNotificationsNever", R.string.RepeatNotificationsNever);
                    } else if (minutes < 60) {
                        value = LocaleController.formatPluralString("Minutes", minutes);
                    } else {
                        value = LocaleController.formatPluralString("Hours", minutes / 60);
                    }
                    textCell.setTextAndValue(LocaleController.getString("RepeatNotifications", R.string.RepeatNotifications), value, false);
                } else if (i == messagePriorityRow || i == groupPriorityRow) {
                    textCell.setMultilineDetail(false);
                    int value = 0;
                    if (i == messagePriorityRow) {
                        value = preferences.getInt("priority_messages", 1);
                    } else if (i == groupPriorityRow) {
                        value = preferences.getInt("priority_group", 1);
                    }
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("NotificationsPriorityDefault", R.string.NotificationsPriorityDefault), false);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh), false);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("NotificationsPriority", R.string.NotificationsPriority), LocaleController.getString("NotificationsPriorityMax", R.string.NotificationsPriorityMax), false);
                    }
                }
            } else if (type == 3) {
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                if (i == messageLedRow) {
                    textCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), preferences.getInt("MessagesLed", 0xff00ff00), true);
                } else if (i == groupLedRow) {
                    textCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), preferences.getInt("GroupLed", 0xff00ff00), true);
                }
            } else if (type == 4) {
                if (view == null) {
                    view = new ShadowSectionCell(mContext);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == messageSectionRow || i == groupSectionRow || i == inappSectionRow ||
                    i == eventsSectionRow || i == otherSectionRow || i == resetSectionRow) {
                return 0;
            } else if (i == messageAlertRow || i == messagePreviewRow || i == groupAlertRow ||
                    i == groupPreviewRow || i == inappSoundRow || i == inappVibrateRow ||
                    i == inappPreviewRow || i == contactJoinedRow || i == pebbleAlertRow ||
                    i == notificationsServiceRow || i == badgeNumberRow || i == inappPriorityRow) {
                return 1;
            } else if (i == messageLedRow || i == groupLedRow) {
                return 3;
            } else if (i == messageSectionRow2 || i == eventsSectionRow2 || i == groupSectionRow2 ||
                    i == inappSectionRow2 || i == otherSectionRow2 || i == resetSectionRow2) {
                return 4;
            } else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
