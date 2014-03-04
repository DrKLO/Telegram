/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

public class SettingsNotificationsActivity extends BaseFragment {
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
                    if (i == 1 || i == 6) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled;
                        if (i == 1) {
                            enabled = preferences.getBoolean("EnableAll", true);
                            editor.putBoolean("EnableAll", !enabled);
                        } else if (i == 6) {
                            enabled = preferences.getBoolean("EnableGroup", true);
                            editor.putBoolean("EnableGroup", !enabled);
                        }
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 2 || i == 7) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabledAll = true;
                        boolean enabled;
                        if (i == 2) {
                            enabled = preferences.getBoolean("EnablePreviewAll", true);
                            editor.putBoolean("EnablePreviewAll", !enabled);
                        } else if (i == 7) {
                            enabled = preferences.getBoolean("EnablePreviewGroup", true);
                            editor.putBoolean("EnablePreviewGroup", !enabled);
                        }
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 3 || i == 8) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled;
                        if (i == 3) {
                            enabled = preferences.getBoolean("EnableVibrateAll", true);
                            editor.putBoolean("EnableVibrateAll", !enabled);
                        } else if (i == 8) {
                            enabled = preferences.getBoolean("EnableVibrateGroup", true);
                            editor.putBoolean("EnableVibrateGroup", !enabled);
                        }
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 4 || i == 9) {
                        try {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                            Uri currentSound = null;

                            String defaultPath = null;
                            Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                            if (defaultUri != null) {
                                defaultPath = defaultUri.getPath();
                            }

                            if (i == 4) {
                                String path = preferences.getString("GlobalSoundPath", defaultPath);
                                if (path != null && !path.equals("NoSound")) {
                                    if (path.equals(defaultPath)) {
                                        currentSound = defaultUri;
                                    } else {
                                        currentSound = Uri.parse(path);
                                    }
                                }
                            } else if (i == 9) {
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
                    } else if (i == 17) {
                        if (reseting) {
                            return;
                        }
                        reseting = true;
                        TLRPC.TL_account_resetNotifySettings req = new TLRPC.TL_account_resetNotifySettings();
                        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                            @Override
                            public void run(TLObject response, TLRPC.TL_error error) {
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MessagesController.Instance.enableJoined = true;
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
                    } else if (i == 11) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableInAppSounds", true);
                        editor.putBoolean("EnableInAppSounds", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 12) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableInAppVibrate", true);
                        editor.putBoolean("EnableInAppVibrate", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 13) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableInAppPreview", true);
                        editor.putBoolean("EnableInAppPreview", !enabled);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 15) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        boolean enabled = preferences.getBoolean("EnableContactJoined", true);
                        MessagesController.Instance.enableJoined = !enabled;
                        editor.putBoolean("EnableContactJoined", !enabled);
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null && parentActivity != null) {
                Ringtone rng = RingtoneManager.getRingtone(parentActivity, ringtone);
                if (rng != null) {
                    name = rng.getTitle(parentActivity);
                    rng.stop();
                }
            }

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == 4) {
                if (name != null && ringtone != null) {
                    editor.putString("GlobalSound", name);
                    editor.putString("GlobalSoundPath", ringtone.toString());
                } else {
                    editor.putString("GlobalSound", "NoSound");
                    editor.putString("GlobalSoundPath", "NoSound");
                }
            } else if (requestCode == 9) {
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
        actionBar.setTitle(getStringEntry(R.string.NotificationsAndSounds));

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
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            boolean enabledAll = preferences.getBoolean("EnableAll", true);
            if (i == 17 || i == 15) {
                return true;
            }
            return !(i != 1 && !enabledAll && i != 13) && (i > 0 && i < 5 || i > 5 && i < 10 || i > 10 && i < 14);
        }

        @Override
        public int getCount() {
            return 18;
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
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == 0) {
                    textView.setText(getStringEntry(R.string.MessageNotifications));
                } else if (i == 5) {
                    textView.setText(getStringEntry(R.string.GroupNotifications));
                } else if (i == 10) {
                    textView.setText(getStringEntry(R.string.InAppNotifications));
                } else if (i == 14) {
                    textView.setText(getStringEntry(R.string.Events));
                } else if (i == 16) {
                    textView.setText(getStringEntry(R.string.Reset));
                }
            } if (type == 1) {
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

                if (i == 1 || i == 6) {
                    if (i == 1) {
                        enabled = enabledAll;
                    } else if (i == 6) {
                        enabled = preferences.getBoolean("EnableGroup", true);
                    }
                    textView.setText(getStringEntry(R.string.Alert));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == 2 || i == 7) {
                    if (i == 2) {
                        enabled = preferences.getBoolean("EnablePreviewAll", true);
                    } else if (i == 7) {
                        enabled = preferences.getBoolean("EnablePreviewGroup", true);
                    }
                    textView.setText(getStringEntry(R.string.MessagePreview));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == 3 || i == 8) {
                    if (i == 3) {
                        enabled = preferences.getBoolean("EnableVibrateAll", true);
                    } else if (i == 8) {
                        enabled = preferences.getBoolean("EnableVibrateGroup", true);
                    }
                    textView.setText(getStringEntry(R.string.Vibrate));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == 11) {
                    enabled = preferences.getBoolean("EnableInAppSounds", true);
                    textView.setText(getStringEntry(R.string.InAppSounds));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == 12) {
                    enabled = preferences.getBoolean("EnableInAppVibrate", true);
                    textView.setText(getStringEntry(R.string.InAppVibrate));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == 13) {
                    enabled = preferences.getBoolean("EnableInAppPreview", true);
                    textView.setText(getStringEntry(R.string.InAppPreview));
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == 15) {
                    enabled = preferences.getBoolean("EnableContactJoined", true);
                    textView.setText(getStringEntry(R.string.ContactJoined));
                    divider.setVisibility(View.INVISIBLE);
                }
                if (enabled) {
                    checkButton.setImageResource(R.drawable.btn_check_on);
                } else {
                    checkButton.setImageResource(R.drawable.btn_check_off);
                }
                if (i != 1 && !enabledAll && i != 15) {
                    view.setEnabled(false);
                    if(android.os.Build.VERSION.SDK_INT >= 11) {
                        checkButton.setAlpha(0.3f);
                        textView.setAlpha(0.3f);
                    }
                } else {
                    if(android.os.Build.VERSION.SDK_INT >= 11) {
                        checkButton.setAlpha(1.0f);
                        textView.setAlpha(1.0f);
                    }
                    view.setEnabled(true);
                }
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_detail_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView textViewDetail = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                boolean enabledAll = preferences.getBoolean("EnableAll", true);
                if (i == 4 || i == 9) {
                    if (i == 4) {
                        String name = preferences.getString("GlobalSound", getStringEntry(R.string.Default));
                        if (name.equals("NoSound")) {
                            textViewDetail.setText(getStringEntry(R.string.NoSound));
                        } else {
                            textViewDetail.setText(name);
                        }
                    } else if (i == 9) {
                        String name = preferences.getString("GroupSound", getStringEntry(R.string.Default));
                        if (name.equals("NoSound")) {
                            textViewDetail.setText(getStringEntry(R.string.NoSound));
                        } else {
                            textViewDetail.setText(name);
                        }
                    }
                    textView.setText(getStringEntry(R.string.Sound));
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == 17) {
                    textView.setText(getStringEntry(R.string.ResetAllNotifications));
                    textViewDetail.setText(getStringEntry(R.string.UndoAllCustom));
                    divider.setVisibility(View.INVISIBLE);
                }
                if (i != 17 && !enabledAll) {
                    view.setEnabled(false);
                    if(android.os.Build.VERSION.SDK_INT >= 11) {
                        textView.setAlpha(0.3f);
                        textViewDetail.setAlpha(0.3f);
                        divider.setAlpha(0.3f);
                    }
                } else {
                    if(android.os.Build.VERSION.SDK_INT >= 11) {
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
            if (i == 0 || i == 5 || i == 10 || i == 14 || i == 16) {
                return 0;
            } else if (i > 0 && i < 4 || i > 5 && i < 9 || i > 10 && i < 14 || i == 15) {
                return 1;
            } else {
                return 2;
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
