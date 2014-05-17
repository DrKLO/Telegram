/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.objects.VibrationSpeed;
import org.telegram.ui.Dialog.VibrationCountDialog;
import org.telegram.ui.Dialog.VibrationSpeedDialog;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

public class SettingsNotificationsActivity extends BaseFragment {
    private static final int TYPE_HEADER           = 0;
    private static final int TYPE_BOOLEAN_SETTINGS = 1;
    private static final int TYPE_INNER_SETTINGS   = 2;

    private static int SETTINGS_COUNT = 0;
    private static final int SETTINGS_MESSAGE_NOTIFICATIONS = SETTINGS_COUNT++; // 0
    private static final int SETTINGS_MESSAGE_ALERT         = SETTINGS_COUNT++; // 1
    private static final int SETTINGS_MESSAGE_PREVIEW       = SETTINGS_COUNT++; // 2
    private static final int SETTINGS_MESSAGE_VIBRATE       = SETTINGS_COUNT++; // 3
    private static final int SETTINGS_MESSAGE_VIBRATE_SPD   = SETTINGS_COUNT++; // 4
    private static final int SETTINGS_MESSAGE_VIBRATE_CNT   = SETTINGS_COUNT++; // 5
    private static final int SETTINGS_MESSAGE_SOUND         = SETTINGS_COUNT++; // 6
    private static final int SETTINGS_GROUP_NOTIFICATIONS   = SETTINGS_COUNT++; // 7
    private static final int SETTINGS_GROUP_ALERT           = SETTINGS_COUNT++; // 8
    private static final int SETTINGS_GROUP_PREVIEW         = SETTINGS_COUNT++; // 9
    private static final int SETTINGS_GROUP_VIBRATE         = SETTINGS_COUNT++; // 10
    private static final int SETTINGS_GROUP_VIBRATE_SPD     = SETTINGS_COUNT++; // 11
    private static final int SETTINGS_GROUP_VIBRATE_CNT     = SETTINGS_COUNT++; // 12
    private static final int SETTINGS_GROUP_SOUND           = SETTINGS_COUNT++; // 13
    private static final int SETTINGS_INAPP_NOTIFICATIONS   = SETTINGS_COUNT++; // 14
    private static final int SETTINGS_INAPP_SOUND           = SETTINGS_COUNT++; // 15
    private static final int SETTINGS_INAPP_VIBRATE         = SETTINGS_COUNT++; // 16
    private static final int SETTINGS_INAPP_PREVIEW         = SETTINGS_COUNT++; // 17
    private static final int SETTINGS_EVENTS                = SETTINGS_COUNT++; // 18
    private static final int SETTINGS_CONTACT_JOINED        = SETTINGS_COUNT++; // 19
    private static final int SETTINGS_PEBBLE                = SETTINGS_COUNT++; // 20
    private static final int SETTINGS_PEBBLE_ALERT          = SETTINGS_COUNT++; // 21
    private static final int SETTINGS_RESET                 = SETTINGS_COUNT++; // 22
    private static final int SETTINGS_RESET_ALL             = SETTINGS_COUNT++; // 23

    private ListView listView;
    private boolean reseting = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.settings_layout, container, false);
            ListAdapter listAdapter = new ListAdapter(parentActivity);
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == SETTINGS_MESSAGE_ALERT || i == SETTINGS_GROUP_ALERT) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled;
                        if (i == SETTINGS_MESSAGE_ALERT) {
                            enabled = preferences.getBoolean("EnableAll", true);
                            editor.putBoolean("EnableAll", !enabled);
                        } else if (i == SETTINGS_GROUP_ALERT) {
                            enabled = preferences.getBoolean("EnableGroup", true);
                            editor.putBoolean("EnableGroup", !enabled);
                        }
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_MESSAGE_PREVIEW || i == SETTINGS_GROUP_PREVIEW) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled;
                        if (i == SETTINGS_MESSAGE_PREVIEW) {
                            enabled = preferences.getBoolean("EnablePreviewAll", true);
                            editor.putBoolean("EnablePreviewAll", !enabled);
                        } else if (i == SETTINGS_GROUP_PREVIEW) {
                            enabled = preferences.getBoolean("EnablePreviewGroup", true);
                            editor.putBoolean("EnablePreviewGroup", !enabled);
                        }
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_MESSAGE_VIBRATE || i == SETTINGS_GROUP_VIBRATE) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled;
                        if (i == SETTINGS_MESSAGE_VIBRATE) {
                            enabled = preferences.getBoolean("EnableVibrateAll", true);
                            editor.putBoolean("EnableVibrateAll", !enabled);
                        } else if (i == SETTINGS_GROUP_VIBRATE) {
                            enabled = preferences.getBoolean("EnableVibrateGroup", true);
                            editor.putBoolean("EnableVibrateGroup", !enabled);
                        }
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_MESSAGE_VIBRATE_SPD || i == SETTINGS_GROUP_VIBRATE_SPD) {
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        final int index = i;
                        VibrationSpeed speed = VibrationSpeed.getDefault();
                        if (index == SETTINGS_MESSAGE_VIBRATE_SPD) {
                            speed = VibrationSpeed.fromValue(preferences.getInt("VibrationSpeed", 0));
                        } else if (index == SETTINGS_GROUP_VIBRATE_SPD) {
                            speed = VibrationSpeed.fromValue(preferences.getInt("VibrationSpeedGroup", 0));
                        }
                        VibrationSpeedDialog vibrationSpeedDialog = new VibrationSpeedDialog();
                        Bundle args = new Bundle();
                        args.putSerializable(VibrationSpeedDialog.KEY_CURRENT_SPEED, speed);
                        args.putSerializable(VibrationSpeedDialog.KEY_LISTENER, new VibrationSpeedDialog.VibrationSpeedSelectionListener() {
                            @Override
                            public void onSpeedSelected(DialogFragment dialog, VibrationSpeed selectedSpeed) {
                                SharedPreferences.Editor editor = preferences.edit();
                                if (index == SETTINGS_MESSAGE_VIBRATE_SPD) {
                                    editor.putInt("VibrationSpeed", selectedSpeed.getValue());
                                } else if (index == SETTINGS_GROUP_VIBRATE_SPD) {
                                    editor.putInt("VibrationSpeedGroup", selectedSpeed.getValue());
                                }
                                editor.commit();
                                listView.invalidateViews();
                            }
                        });
                        vibrationSpeedDialog.setArguments(args);
                        vibrationSpeedDialog.show(getFragmentManager(), "VibrationSpeedDialog");
                    } else if (i == SETTINGS_MESSAGE_VIBRATE_CNT || i == SETTINGS_GROUP_VIBRATE_CNT) {
                        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        final int index = i;
                        int count = VibrationCountDialog.DEFAULT_VIBRATION_COUNT;
                        if (index == SETTINGS_MESSAGE_VIBRATE_CNT) {
                            count = preferences.getInt("VibrationCount", count);
                        } else if (index == SETTINGS_GROUP_VIBRATE_CNT) {
                            count = preferences.getInt("VibrationCountGroup", count);
                        }
                        VibrationCountDialog vibrationCountDialog = new VibrationCountDialog();
                        Bundle args = new Bundle();
                        args.putInt(VibrationCountDialog.KEY_CURRENT_COUNT, count);
                        args.putSerializable(VibrationCountDialog.KEY_LISTENER, new VibrationCountDialog.VibrationCountSelectionListener() {
                            @Override
                            public void onCountSelected(DialogFragment dialog, int selectedCount) {
                                SharedPreferences.Editor editor = preferences.edit();
                                if (index == SETTINGS_MESSAGE_VIBRATE_CNT) {
                                    editor.putInt("VibrationCount", selectedCount);
                                } else if (index == SETTINGS_GROUP_VIBRATE_CNT) {
                                    editor.putInt("VibrationCountGroup", selectedCount);
                                }
                                editor.commit();
                                listView.invalidateViews();
                            }
                        });
                        vibrationCountDialog.setArguments(args);
                        vibrationCountDialog.show(getFragmentManager(), "VibrateCountDialog");
                    } else if (i == SETTINGS_MESSAGE_SOUND || i == SETTINGS_GROUP_SOUND) {
                        if (parentActivity == null) {
                            return;
                        }
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

                            if (i == SETTINGS_MESSAGE_SOUND) {
                                String path = preferences.getString("GlobalSoundPath", defaultPath);
                                if (path != null && !path.equals("NoSound")) {
                                    if (path.equals(defaultPath)) {
                                        currentSound = defaultUri;
                                    } else {
                                        currentSound = Uri.parse(path);
                                    }
                                }
                            } else if (i == SETTINGS_GROUP_SOUND) {
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
                            parentActivity.startActivityForResult(tmpIntent, i);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (i == SETTINGS_RESET_ALL) {
                        if (reseting) {
                            return;
                        }
                        reseting = true;
                        TLRPC.TL_account_resetNotifySettings req = new TLRPC.TL_account_resetNotifySettings();
                        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                            @Override
                            public void run(TLObject response, TLRPC.TL_error error) {
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MessagesController.getInstance().enableJoined = true;
                                        ActionBarActivity inflaterActivity = parentActivity;
                                        if (inflaterActivity == null) {
                                            inflaterActivity = (ActionBarActivity)getActivity();
                                        }
                                        if (inflaterActivity == null) {
                                            return;
                                        }
                                        reseting = false;
                                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                        SharedPreferences.Editor editor = preferences.edit();
                                        editor.clear();
                                        editor.commit();
                                        listView.invalidateViews();
                                        Toast toast = Toast.makeText(inflaterActivity, R.string.ResetNotificationsText, Toast.LENGTH_SHORT);
                                        toast.show();
                                    }
                                });
                            }
                        }, null, true, RPCRequest.RPCRequestClassGeneric);
                    } else if (i == SETTINGS_INAPP_SOUND) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableInAppSounds", true);
                        editor.putBoolean("EnableInAppSounds", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_INAPP_VIBRATE) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableInAppVibrate", true);
                        editor.putBoolean("EnableInAppVibrate", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_INAPP_PREVIEW) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableInAppPreview", true);
                        editor.putBoolean("EnableInAppPreview", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_CONTACT_JOINED) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableContactJoined", true);
                        MessagesController.getInstance().enableJoined = !enabled;
                        editor.putBoolean("EnableContactJoined", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == SETTINGS_PEBBLE_ALERT) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnablePebbleNotifications", false);
                        editor.putBoolean("EnablePebbleNotifications", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    }
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
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
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null && parentActivity != null) {
                Ringtone rng = RingtoneManager.getRingtone(parentActivity, ringtone);
                if (rng != null) {
                    if(ringtone.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                        name = LocaleController.getString("Default", R.string.Default);
                    } else {
                        name = rng.getTitle(parentActivity);
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == SETTINGS_MESSAGE_SOUND) {
                if (name != null && ringtone != null) {
                    editor.putString("GlobalSound", name);
                    editor.putString("GlobalSoundPath", ringtone.toString());
                } else {
                    editor.putString("GlobalSound", "NoSound");
                    editor.putString("GlobalSoundPath", "NoSound");
                }
            } else if (requestCode == SETTINGS_GROUP_SOUND) {
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
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setSubtitle(null);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        actionBar.setTitle(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds));

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getActivity() == null) {
            return;
        }
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
        }
        return true;
    }

    private class ListAdapter extends BaseAdapter {
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
            if (i == SETTINGS_RESET_ALL || i == SETTINGS_CONTACT_JOINED) {
                return true;
            }

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            boolean enabledAll = preferences.getBoolean("EnableAll", true);
            boolean enabled =
                (enabledAll || i == SETTINGS_MESSAGE_ALERT || i == SETTINGS_INAPP_PREVIEW) &&
                (i > SETTINGS_MESSAGE_NOTIFICATIONS && i < SETTINGS_GROUP_NOTIFICATIONS || i > SETTINGS_GROUP_NOTIFICATIONS && i < SETTINGS_INAPP_NOTIFICATIONS || i > SETTINGS_INAPP_NOTIFICATIONS && i < SETTINGS_EVENTS) ||
                (i == SETTINGS_PEBBLE_ALERT);

            if(enabled) {
                if(i == SETTINGS_MESSAGE_VIBRATE_SPD) {
                    if(!preferences.getBoolean("EnableVibrateAll", true))
                        enabled = false;
                }
                else if(i == SETTINGS_GROUP_VIBRATE_SPD) {
                    if(!preferences.getBoolean("EnableVibrateGroup", true))
                        enabled = false;
                }
                else if(i == SETTINGS_MESSAGE_VIBRATE_CNT) {
                    if(!preferences.getBoolean("EnableVibrateAll", true) || preferences.getInt("VibrationSpeed", 0) == 0)
                        enabled = false;
                }
                else if(i == SETTINGS_GROUP_VIBRATE_CNT) {
                    if(!preferences.getBoolean("EnableVibrateGroup", true) || preferences.getInt("VibrationSpeedGroup", 0) == 0)
                        enabled = false;
                }
            }

            return enabled;
        }

        @Override
        public int getCount() {
            return SETTINGS_COUNT;
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
            if (type == TYPE_HEADER) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == SETTINGS_MESSAGE_NOTIFICATIONS) {
                    textView.setText(LocaleController.getString("MessageNotifications", R.string.MessageNotifications));
                } else if (i == SETTINGS_GROUP_NOTIFICATIONS) {
                    textView.setText(LocaleController.getString("GroupNotifications", R.string.GroupNotifications));
                } else if (i == SETTINGS_INAPP_NOTIFICATIONS) {
                    textView.setText(LocaleController.getString("InAppNotifications", R.string.InAppNotifications));
                } else if (i == SETTINGS_EVENTS) {
                    textView.setText(LocaleController.getString("Events", R.string.Events));
                } else if (i == SETTINGS_PEBBLE) {
                    textView.setText(LocaleController.getString("Pebble", R.string.Pebble));
                } else if (i == SETTINGS_RESET) {
                    textView.setText(LocaleController.getString("Reset", R.string.Reset));
                }
            } if (type == TYPE_BOOLEAN_SETTINGS) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_check_notify_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);

                ImageView checkButton = (ImageView)view.findViewById(R.id.settings_row_check_button);
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                boolean enabled = false;
                boolean enabledAll = preferences.getBoolean("EnableAll", true);

                if (i == SETTINGS_MESSAGE_ALERT || i == SETTINGS_GROUP_ALERT) {
                    if (i == SETTINGS_MESSAGE_ALERT) {
                        enabled = enabledAll;
                    } else if (i == SETTINGS_GROUP_ALERT) {
                        enabled = preferences.getBoolean("EnableGroup", true);
                    }
                    textView.setText(LocaleController.getString("Alert", R.string.Alert));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == SETTINGS_MESSAGE_PREVIEW || i == SETTINGS_GROUP_PREVIEW) {
                    if (i == SETTINGS_MESSAGE_PREVIEW) {
                        enabled = preferences.getBoolean("EnablePreviewAll", true);
                    } else if (i == SETTINGS_GROUP_PREVIEW) {
                        enabled = preferences.getBoolean("EnablePreviewGroup", true);
                    }
                    textView.setText(LocaleController.getString("MessagePreview", R.string.MessagePreview));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == SETTINGS_MESSAGE_VIBRATE || i == SETTINGS_GROUP_VIBRATE) {
                    if (i == SETTINGS_MESSAGE_VIBRATE) {
                        enabled = preferences.getBoolean("EnableVibrateAll", true);
                    } else if (i == SETTINGS_GROUP_VIBRATE) {
                        enabled = preferences.getBoolean("EnableVibrateGroup", true);
                    }
                    textView.setText(LocaleController.getString("Vibrate", R.string.Vibrate));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == SETTINGS_INAPP_SOUND) {
                    enabled = preferences.getBoolean("EnableInAppSounds", true);
                    textView.setText(LocaleController.getString("InAppSounds", R.string.InAppSounds));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == SETTINGS_INAPP_VIBRATE) {
                    enabled = preferences.getBoolean("EnableInAppVibrate", true);
                    textView.setText(LocaleController.getString("InAppVibrate", R.string.InAppVibrate));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == SETTINGS_INAPP_PREVIEW) {
                    enabled = preferences.getBoolean("EnableInAppPreview", true);
                    textView.setText(LocaleController.getString("InAppPreview", R.string.InAppPreview));
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == SETTINGS_CONTACT_JOINED) {
                    enabled = preferences.getBoolean("EnableContactJoined", true);
                    textView.setText(LocaleController.getString("ContactJoined", R.string.ContactJoined));
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == SETTINGS_PEBBLE_ALERT) {
                    enabled = preferences.getBoolean("EnablePebbleNotifications", false);
                    textView.setText(LocaleController.getString("Alert", R.string.Alert));
                    divider.setVisibility(View.INVISIBLE);
                }
                if (enabled) {
                    checkButton.setImageResource(R.drawable.btn_check_on);
                } else {
                    checkButton.setImageResource(R.drawable.btn_check_off);
                }
                if (i != SETTINGS_MESSAGE_ALERT && !enabledAll && i != SETTINGS_CONTACT_JOINED) {
                    view.setEnabled(false);
                    if(android.os.Build.VERSION.SDK_INT >= SETTINGS_INAPP_SOUND) {
                        checkButton.setAlpha(0.3f);
                        textView.setAlpha(0.3f);
                    }
                } else {
                    if(android.os.Build.VERSION.SDK_INT >= SETTINGS_INAPP_SOUND) {
                        checkButton.setAlpha(1.0f);
                        textView.setAlpha(1.0f);
                    }
                    view.setEnabled(true);
                }
            } else if (type == TYPE_INNER_SETTINGS) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_detail_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView textViewDetail = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                boolean enabledAll = preferences.getBoolean("EnableAll", true);
                if (i == SETTINGS_MESSAGE_SOUND || i == SETTINGS_GROUP_SOUND) {
                    if (i == SETTINGS_MESSAGE_SOUND) {
                        String name = preferences.getString("GlobalSound", LocaleController.getString("Default", R.string.Default));
                        if (name.equals("NoSound")) {
                            textViewDetail.setText(LocaleController.getString("NoSound", R.string.NoSound));
                        } else {
                            textViewDetail.setText(name);
                        }
                    } else if (i == SETTINGS_GROUP_SOUND) {
                        String name = preferences.getString("GroupSound", LocaleController.getString("Default", R.string.Default));
                        if (name.equals("NoSound")) {
                            textViewDetail.setText(LocaleController.getString("NoSound", R.string.NoSound));
                        } else {
                            textViewDetail.setText(name);
                        }
                    }
                    textView.setText(LocaleController.getString("Sound", R.string.Sound));
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == SETTINGS_MESSAGE_VIBRATE_SPD || i == SETTINGS_GROUP_VIBRATE_SPD) {
                    VibrationSpeed speed = VibrationSpeed.getDefault();
                    if (i == SETTINGS_MESSAGE_VIBRATE_SPD) {
                        speed = VibrationSpeed.fromValue(preferences.getInt("VibrationSpeed", 0));
                    } else if (i == SETTINGS_GROUP_VIBRATE_SPD) {
                        speed = VibrationSpeed.fromValue(preferences.getInt("VibrationSpeedGroup", 0));
                    }
                    textViewDetail.setText(LocaleController.getString(speed.getLocaleKey(), speed.getResourceId()));
                    textView.setText(LocaleController.getString("VibrateSpeed", R.string.VibrateSpeed));
                    divider.setVisibility(View.VISIBLE);
                }  else if (i == SETTINGS_MESSAGE_VIBRATE_CNT || i == SETTINGS_GROUP_VIBRATE_CNT) {
                    int count = VibrationCountDialog.DEFAULT_VIBRATION_COUNT;
                    if (i == SETTINGS_MESSAGE_VIBRATE_CNT) {
                        count = preferences.getInt("VibrationCount", count);
                    } else if (i == SETTINGS_GROUP_VIBRATE_CNT) {
                        count = preferences.getInt("VibrationCountGroup", count);
                    }
                    textViewDetail.setText(String.valueOf(count));
                    textView.setText(LocaleController.getString("VibrateCount", R.string.VibrateCount));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == SETTINGS_RESET_ALL) {
                    textView.setText(LocaleController.getString("ResetAllNotifications", R.string.ResetAllNotifications));
                    textViewDetail.setText(LocaleController.getString("UndoAllCustom", R.string.UndoAllCustom));
                    divider.setVisibility(View.INVISIBLE);
                }
                if (i != SETTINGS_RESET_ALL && !enabledAll) {
                    view.setEnabled(false);
                    if(android.os.Build.VERSION.SDK_INT >= SETTINGS_INAPP_SOUND) {
                        textView.setAlpha(0.3f);
                        textViewDetail.setAlpha(0.3f);
                        divider.setAlpha(0.3f);
                    }
                } else {
                    if(android.os.Build.VERSION.SDK_INT >= SETTINGS_INAPP_SOUND) {
                        textView.setAlpha(1.0f);
                        textViewDetail.setAlpha(1.0f);
                        divider.setAlpha(1.0f);
                    }
                    view.setEnabled(true);
                }
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == SETTINGS_MESSAGE_NOTIFICATIONS || i == SETTINGS_GROUP_NOTIFICATIONS || i == SETTINGS_INAPP_NOTIFICATIONS || i == SETTINGS_EVENTS || i == SETTINGS_PEBBLE || i == SETTINGS_RESET) {
                return TYPE_HEADER;
            } else if(i == SETTINGS_MESSAGE_VIBRATE_SPD || i == SETTINGS_GROUP_VIBRATE_SPD || i == SETTINGS_MESSAGE_VIBRATE_CNT || i == SETTINGS_GROUP_VIBRATE_CNT) {
                return TYPE_INNER_SETTINGS;
            } else if (i > SETTINGS_MESSAGE_NOTIFICATIONS && i < SETTINGS_MESSAGE_SOUND || i > SETTINGS_GROUP_NOTIFICATIONS && i < SETTINGS_GROUP_SOUND || i > SETTINGS_INAPP_NOTIFICATIONS && i < SETTINGS_EVENTS || i == SETTINGS_CONTACT_JOINED || i == SETTINGS_PEBBLE_ALERT) {
                return TYPE_BOOLEAN_SETTINGS;
            } else {
                return TYPE_INNER_SETTINGS;
            }
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
