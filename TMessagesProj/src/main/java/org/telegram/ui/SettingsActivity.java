/*
 * This is the source code of Telegram for Android v. 1.2.3.
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
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView listView;
    private ListAdapter listAdapter;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();

    int profileRow;
    int numberSectionRow;
    int numberRow;
    int settingsSectionRow;
    int textSizeRow;
    int enableAnimationsRow;
    int notificationRow;
    int blockedRow;
    int backgroundRow;
    int supportSectionRow;
    int askQuestionRow;
    int logoutRow;
    int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        avatarUpdater.parentFragment = this;
        avatarUpdater.delegate = new AvatarUpdater.AvatarUpdaterDelegate() {
            @Override
            public void didUploadedPhoto(TLRPC.TL_inputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.caption = "";
                req.crop = new TLRPC.TL_inputPhotoCropAuto();
                req.file = file;
                req.geo_point = new TLRPC.TL_inputGeoPointEmpty();
                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
            }
        };
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);


        rowCount = 0;
        profileRow = rowCount++;
        numberSectionRow = rowCount++;
        numberRow = rowCount++;
        settingsSectionRow = rowCount++;
        textSizeRow = rowCount++;
        enableAnimationsRow = rowCount++;
        notificationRow = rowCount++;
        blockedRow = rowCount++;
        backgroundRow = rowCount++;
        supportSectionRow = rowCount++;
        askQuestionRow = rowCount++;
        logoutRow = rowCount++;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        avatarUpdater.clear();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.settings_layout, container, false);
            listAdapter = new ListAdapter(parentActivity);
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == textSizeRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(getStringEntry(R.string.TextSize));
                        builder.setItems(new CharSequence[]{String.format("%d", 16), String.format("%d", 17), String.format("%d", 18), String.format("%d", 19), String.format("%d", 20)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("fons_size", 16 + which);
                                editor.commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(getStringEntry(R.string.Cancel), null);
                        builder.show().setCanceledOnTouchOutside(true);
                    } else if (i == enableAnimationsRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        boolean animations = preferences.getBoolean("view_animations", true);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("view_animations", !animations);
                        editor.commit();
                        if (listView != null) {
                            listView.invalidateViews();
                        }
                    } else if (i == notificationRow) {
                        ((ApplicationActivity)parentActivity).presentFragment(new SettingsNotificationsActivity(), "settings_notifications", false);
                    } else if (i == blockedRow) {
                        ((ApplicationActivity)parentActivity).presentFragment(new SettingsBlockedUsers(), "settings_blocked", false);
                    } else if (i == backgroundRow) {
                        ((ApplicationActivity)parentActivity).presentFragment(new SettingsWallpapersActivity(), "settings_wallpapers", false);
                    } else if (i == askQuestionRow) {
                        ChatActivity fragment = new ChatActivity();
                        Bundle bundle = new Bundle();
                        bundle.putInt("user_id", 333000);
                        fragment.setArguments(bundle);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_user_" + 333000, false);
                    }
//                else if (i == 6) {
//                    UserConfig.saveIncomingPhotos = !UserConfig.saveIncomingPhotos;
//                    listView.invalidateViews();
//                }
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
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            if (listView != null) {
                listView.invalidateViews();
            }
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
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setSubtitle(null);
        actionBar.setCustomView(null);
        actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.Settings) + "</font>"));

        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
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
        if (getSherlockActivity() == null) {
            return;
        }
        if (!firstStart && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    WindowManager manager = (WindowManager)parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    Display display = manager.getDefaultDisplay();
                    int rotation = display.getRotation();
                    int height;
                    int currentActionBarHeight = parentActivity.getSupportActionBar().getHeight();
                    float density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
                    if (currentActionBarHeight != 48 * density && currentActionBarHeight != 40 * density) {
                        height = currentActionBarHeight;
                    } else {
                        height = (int)(48.0f * density);
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            height = (int)(40.0f * density);
                        }
                    }
                    listView.setPadding(listView.getPaddingLeft(), height, listView.getPaddingRight(), listView.getPaddingBottom());

                    listView.getViewTreeObserver().removeOnPreDrawListener(this);

                    return false;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
        }
        return true;
    }

    private void sendLogs() {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -v time");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line).append("\r\n");
            }

            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("message/rfc822") ;
            i.putExtra(Intent.EXTRA_EMAIL, new String[]{"drklo.2kb@gmail.com"});
            i.putExtra(Intent.EXTRA_SUBJECT, "last logs");
            i.putExtra(Intent.EXTRA_TEXT, log.toString());
            startActivity(Intent.createChooser(i, "Select email application."));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            return i == textSizeRow || i == enableAnimationsRow || i == blockedRow || i == notificationRow || i == backgroundRow || i == askQuestionRow;
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
                    view = li.inflate(R.layout.settings_name_layout, viewGroup, false);

                    Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                    TextView onlineText = (TextView)view.findViewById(R.id.settings_online);
                    onlineText.setTypeface(typeface);

                    ImageButton button = (ImageButton)view.findViewById(R.id.settings_edit_name);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ((ApplicationActivity)parentActivity).presentFragment(new SettingsChangeNameActivity(), "change_name", false);
                        }
                    });

                    final ImageButton button2 = (ImageButton)view.findViewById(R.id.settings_change_avatar_button);
                    button2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {

                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

                            CharSequence[] items;

                            TLRPC.User user = MessagesController.Instance.users.get(UserConfig.clientUserId);
                            if (user == null) {
                                user = UserConfig.currentUser;
                            }
                            if (user == null) {
                                return;
                            }
                            if (user.photo != null && user.photo.photo_big != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty)) {
                                items = new CharSequence[] {getStringEntry(R.string.FromCamera), getStringEntry(R.string.FromGalley), getStringEntry(R.string.DeletePhoto)};
                            } else {
                                items = new CharSequence[] {getStringEntry(R.string.FromCamera), getStringEntry(R.string.FromGalley)};
                            }

                            builder.setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if (i == 0) {
                                        avatarUpdater.openCamera();
                                    } else if (i == 1) {
                                        avatarUpdater.openGallery();
                                    } else if (i == 2) {
                                        TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
                                        req.id = new TLRPC.TL_inputPhotoEmpty();
                                        req.crop = new TLRPC.TL_inputPhotoCropAuto();
                                        UserConfig.currentUser.photo = new TLRPC.TL_userProfilePhotoEmpty();
                                        TLRPC.User user = MessagesController.Instance.users.get(UserConfig.clientUserId);
                                        if (user == null) {
                                            user = UserConfig.currentUser;
                                        }
                                        if (user == null) {
                                            return;
                                        }
                                        if (user != null) {
                                            user.photo = UserConfig.currentUser.photo;
                                        }
                                        NotificationCenter.Instance.postNotificationName(MessagesController.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                                        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {

                                            }
                                        }, null, true, RPCRequest.RPCRequestClassGeneric);
                                    }
                                }
                            });
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    });
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_name);
                TLRPC.User user = MessagesController.Instance.users.get(UserConfig.clientUserId);
                if (user == null) {
                    user = UserConfig.currentUser;
                }
                if (user != null) {
                    textView.setText(Utilities.formatName(user.first_name, user.last_name));
                    BackupImageView avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    TLRPC.FileLocation photo = null;
                    if (user.photo != null) {
                        photo = user.photo.photo_small;
                    }
                    avatarImage.setImage(photo, null, Utilities.getUserAvatarForId(user.id));
                }
                return view;
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == numberSectionRow) {
                    textView.setText(getStringEntry(R.string.YourPhoneNumber));
                } else if (i == settingsSectionRow) {
                    textView.setText(getStringEntry(R.string.SETTINGS));
                } else if (i == supportSectionRow) {
                    textView.setText(getStringEntry(R.string.Support));
                }
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_button_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == numberRow) {
                    TLRPC.User user = UserConfig.currentUser;
                    if (user != null && user.phone != null && user.phone.length() != 0) {
                        textView.setText(PhoneFormat.Instance.format("+" + user.phone));
                    } else {
                        textView.setText("Unknown");
                    }
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == notificationRow) {
                    textView.setText(getStringEntry(R.string.NotificationsAndSounds));
                    divider.setVisibility(View.VISIBLE);
                } else if (i == blockedRow) {
                    textView.setText(getStringEntry(R.string.BlockedUsers));
                    divider.setVisibility(backgroundRow != 0 ? View.VISIBLE : View.INVISIBLE);
                } else if (i == backgroundRow) {
                    textView.setText(getStringEntry(R.string.ChatBackground));
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == askQuestionRow) {
                    textView.setText(getStringEntry(R.string.AskAQuestion));
                    divider.setVisibility(View.INVISIBLE);
                }
            } else if (type == 3) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_check_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == enableAnimationsRow) {
                    textView.setText(getStringEntry(R.string.EnableAnimations));
                    divider.setVisibility(View.VISIBLE);

                    ImageView checkButton = (ImageView)view.findViewById(R.id.settings_row_check_button);
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    boolean animations = preferences.getBoolean("view_animations", true);
                    if (animations) {
                        checkButton.setImageResource(R.drawable.btn_check_on);
                    } else {
                        checkButton.setImageResource(R.drawable.btn_check_off);
                    }
                }
//                if (i == 7) {
//                    textView.setText(getStringEntry(R.string.SaveIncomingPhotos));
//                    divider.setVisibility(View.INVISIBLE);
//
//                    ImageView checkButton = (ImageView)view.findViewById(R.id.settings_row_check_button);
//                    if (UserConfig.saveIncomingPhotos) {
//                        checkButton.setImageResource(R.drawable.btn_check_on);
//                    } else {
//                        checkButton.setImageResource(R.drawable.btn_check_off);
//                    }
//                }
            } else if (type == 4) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_logout_button, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                    textView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(getStringEntry(R.string.AreYouSure));
                            builder.setTitle(getStringEntry(R.string.AppName));
                            builder.setPositiveButton(getStringEntry(R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    NotificationCenter.Instance.postNotificationName(1234);
                                    MessagesController.Instance.unregistedPush();
                                    listView.setAdapter(null);
                                    UserConfig.clearConfig();
                                }
                            });
                            builder.setNegativeButton(getStringEntry(R.string.Cancel), null);
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    });
                }
            } else if (type == 5) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                detailTextView.setTypeface(typeface);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == textSizeRow) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int size = preferences.getInt("fons_size", 16);
                    detailTextView.setText(String.format("%d", size));
                    textView.setText(ApplicationLoader.applicationContext.getString(R.string.TextSize));
                    divider.setVisibility(View.VISIBLE);
                }
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == profileRow) {
                return 0;
            } else if (i == numberSectionRow || i == settingsSectionRow || i == supportSectionRow) {
                return 1;
            } else if (i == textSizeRow) {
                return 5;
            } else if (i == enableAnimationsRow) {
                return 3;
            } else if (i == numberRow || i == notificationRow || i == blockedRow || i == backgroundRow || i == askQuestionRow) {
                return 2;
            } else if (i == logoutRow) {
                return 4;
            } else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 6;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
