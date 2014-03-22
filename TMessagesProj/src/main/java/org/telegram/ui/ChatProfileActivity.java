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
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBar;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class ChatProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private int chat_id;
    private String selectedPhone;
    private TLRPC.ChatParticipants info;
    private TLRPC.TL_chatParticipant selectedUser;
    private AvatarUpdater avatarUpdater = new AvatarUpdater();
    private int totalMediaCount = -1;
    private int onlineCount = -1;
    private ArrayList<Integer> sortedUsers = new ArrayList<Integer>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.closeChats);

        chat_id = getArguments().getInt("chat_id", 0);
        info = (TLRPC.ChatParticipants)NotificationCenter.getInstance().getFromMemCache(5);
        updateOnlineCount();
        MessagesController.getInstance().getMediaCount(-chat_id, classGuid, true);
        avatarUpdater.delegate = new AvatarUpdater.AvatarUpdaterDelegate() {
            @Override
            public void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big) {
                if (chat_id != 0) {
                    MessagesController.getInstance().changeChatAvatar(chat_id, file);
                }
            }
        };
        avatarUpdater.parentFragment = this;
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.closeChats);
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
            fragmentView = inflater.inflate(R.layout.chat_profile_layout, container, false);

            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listViewAdapter = new ListAdapter(parentActivity));
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    int size = 0;
                    if (info != null) {
                        size += info.participants.size();
                    }
                    if (i > 6 && i < size + 7) {
                        TLRPC.TL_chatParticipant user = info.participants.get(sortedUsers.get(i - 7));
                        if (user.user_id == UserConfig.clientUserId) {
                            return false;
                        }
                        if (info.admin_id != UserConfig.clientUserId && user.inviter_id != UserConfig.clientUserId) {
                            return false;
                        }
                        selectedUser = user;

                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        CharSequence[] items = new CharSequence[] {LocaleController.getString("KickFromGroup", R.string.KickFromGroup)};

                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    kickUser(selectedUser);
                                }
                            }
                        });
                        builder.show().setCanceledOnTouchOutside(true);

                        return true;
                    }
                    return false;
                }
            });

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == 2) {
                        SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        String key = "notify_" + (-chat_id);
                        boolean value = preferences.getBoolean(key, true);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(key, !value);
                        editor.commit();
                        listView.invalidateViews();
                    } else if (i == 3) {
                        try {
                            Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                            SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            Uri currentSound = null;

                            String defaultPath = null;
                            Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                            if (defaultUri != null) {
                                defaultPath = defaultUri.getPath();
                            }

                            String path = preferences.getString("sound_chat_path_" + chat_id, defaultPath);
                            if (path != null && !path.equals("NoSound")) {
                                if (path.equals(defaultPath)) {
                                    currentSound = defaultUri;
                                } else {
                                    currentSound = Uri.parse(path);
                                }
                            }

                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                            startActivityForResult(tmpIntent, 15);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (i == 5) {
                        MediaActivity fragment = new MediaActivity();
                        Bundle bundle = new Bundle();
                        bundle.putLong("dialog_id", -chat_id);
                        fragment.setArguments(bundle);
                        ((LaunchActivity)parentActivity).presentFragment(fragment, "media_chat_" + chat_id, false);
                    } else {
                        int size = 0;
                        if (info != null) {
                            size += info.participants.size();
                        }
                        if (i > 6 && i < size + 7) {
                            int user_id = info.participants.get(sortedUsers.get(i - 7)).user_id;
                            if (user_id == UserConfig.clientUserId) {
                                return;
                            }
                            UserProfileActivity fragment = new UserProfileActivity();
                            Bundle args = new Bundle();
                            args.putInt("user_id", user_id);
                            fragment.setArguments(args);
                            ((LaunchActivity)parentActivity).presentFragment(fragment, "user_" + user_id, false);
                        } else {
                            if (size + 7 == i) {
                                if (info.participants.size() < 200) {
                                    openAddMenu();
                                } else {
                                    kickUser(null);
                                }
                            } else if (size + 7 == i + 1) {
                                kickUser(null);
                            }
                        }
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
    public void didSelectContact(TLRPC.User user) {
        MessagesController.getInstance().addUserToChat(chat_id, user, info);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 15) {
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

                SharedPreferences preferences = parentActivity.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();

                if (name != null && ringtone != null) {
                    editor.putString("sound_chat_" + chat_id, name);
                    editor.putString("sound_chat_path_" + chat_id, ringtone.toString());
                } else {
                    editor.putString("sound_chat_" + chat_id, "NoSound");
                    editor.putString("sound_chat_path_" + chat_id, "NoSound");
                }
                editor.commit();
                listView.invalidateViews();
            }
        }
    }

    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateOnlineCount();
            }
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == MessagesController.chatInfoDidLoaded) {
            int chatId = (Integer)args[0];
            if (chatId == chat_id) {
                info = (TLRPC.ChatParticipants)args[1];
                updateOnlineCount();
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == MessagesController.mediaCountDidLoaded) {
            long uid = (Long)args[0];
            int lower_part = (int)uid;
            if (lower_part < 0 && chat_id == -lower_part) {
                totalMediaCount = (Integer)args[1];
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == MessagesController.closeChats) {
            removeSelfFromStack();
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
        actionBar.setCustomView(null);
        actionBar.setTitle(LocaleController.getString("GroupInfo", R.string.GroupInfo));
        actionBar.setSubtitle(null);

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
        ((LaunchActivity)parentActivity).fixBackButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) {
            return;
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
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

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
            }
        }
    }

    private void updateOnlineCount() {
        if (info == null) {
            return;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        sortedUsers.clear();
        int i = 0;
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.getInstance().users.get(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.clientUserId) && user.status.expires > 10000) {
                onlineCount++;
            }
            sortedUsers.add(i);
            i++;
        }

        Collections.sort(sortedUsers, new Comparator<Integer>() {
            @Override
            public int compare(Integer lhs, Integer rhs) {
                TLRPC.User user1 = MessagesController.getInstance().users.get(info.participants.get(rhs).user_id);
                TLRPC.User user2 = MessagesController.getInstance().users.get(info.participants.get(lhs).user_id);
                Integer status1 = 0;
                Integer status2 = 0;
                if (user1 != null && user1.status != null) {
                    if (user1.id == UserConfig.clientUserId) {
                        status1 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                    } else {
                        status1 = user1.status.expires;
                    }
                }
                if (user2 != null && user2.status != null) {
                    if (user2.id == UserConfig.clientUserId) {
                        status2 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                    } else {
                        status2 = user2.status.expires;
                    }
                }
                return status1.compareTo(status2);
            }
        });

        if (listView != null) {
            listView.invalidateViews();
        }
    }

    private void processPhotoMenu(int action) {
        if (action == 0) {
            if (parentActivity == null) {
                return;
            }
            TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
            if (chat.photo != null && chat.photo.photo_big != null) {
                NotificationCenter.getInstance().addToMemCache(53, chat.photo.photo_big);
                Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                startActivity(intent);
            }
        } else if (action == 1) {
            avatarUpdater.openCamera();
        } else if (action == 2) {
            avatarUpdater.openGallery();
        } else if (action == 3) {
            MessagesController.getInstance().changeChatAvatar(chat_id, null);
        }
    }

    private void openAddMenu() {
        ContactsActivity fragment = new ContactsActivity();
        fragment.animationType = 1;
        Bundle bundle = new Bundle();
        bundle.putBoolean("onlyUsers", true);
        bundle.putBoolean("destroyAfterSelect", true);
        bundle.putBoolean("usersAsSections", true);
        bundle.putBoolean("returnAsResult", true);
        fragment.selectAlertString = R.string.AddToTheGroup;
        fragment.selectAlertStringDesc = "AddToTheGroup";
        fragment.delegate = this;
        if (info != null) {
            HashMap<Integer, TLRPC.User> users = new HashMap<Integer, TLRPC.User>();
            for (TLRPC.TL_chatParticipant p : info.participants) {
                users.put(p.user_id, null);
            }
            NotificationCenter.getInstance().addToMemCache(7, users);
        }
        fragment.setArguments(bundle);
        ((LaunchActivity)parentActivity).presentFragment(fragment, "contacts_block", false);
    }

    private void kickUser(TLRPC.TL_chatParticipant user) {
        if (user != null) {
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().users.get(user.user_id), info);
        } else {
            NotificationCenter.getInstance().removeObserver(this, MessagesController.closeChats);
            NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().users.get(UserConfig.clientUserId), info);
            MessagesController.getInstance().deleteDialog(-chat_id, 0, false);
            finishFragment();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_profile_menu, menu);
        SupportMenuItem doneItem = (SupportMenuItem)menu.findItem(R.id.block_user);
        TextView doneTextView = (TextView)doneItem.getActionView().findViewById(R.id.done_button);
        doneTextView.setText(LocaleController.getString("AddMember", R.string.AddMember));
        doneTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAddMenu();
            }
        });
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
            return (i == 2 || i == 3 || i == 5 || i > 6) && i != getCount() - 1;
        }

        @Override
        public int getCount() {
            int count = 6;
            if (info != null && !(info instanceof TLRPC.TL_chatParticipantsForbidden)) {
                count += info.participants.size() + 2;
                if (info.participants.size() < 200) {
                    count++;
                }
            }
            return count;
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
                BackupImageView avatarImage;
                TextView onlineText;
                TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_profile_avatar_layout, viewGroup, false);
                    onlineText = (TextView)view.findViewById(R.id.settings_online);

                    ImageButton button = (ImageButton)view.findViewById(R.id.settings_edit_name);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ChatProfileChangeNameActivity fragment = new ChatProfileChangeNameActivity();
                            Bundle bundle = new Bundle();
                            bundle.putInt("chat_id", chat_id);
                            fragment.setArguments(bundle);
                            ((LaunchActivity)parentActivity).presentFragment(fragment, "chat_name_" + chat_id, false);
                        }
                    });

                    final ImageButton button2 = (ImageButton)view.findViewById(R.id.settings_change_avatar_button);
                    button2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            CharSequence[] items;
                            int type;
                            TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
                            if (chat.photo == null || chat.photo.photo_big == null || chat.photo instanceof TLRPC.TL_chatPhotoEmpty) {
                                items = new CharSequence[] {LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley)};
                                type = 0;
                            } else {
                                items = new CharSequence[] {LocaleController.getString("OpenPhoto", R.string.OpenPhoto), LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                                type = 1;
                            }

                            final int arg0 = type;
                            builder.setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    int action = 0;
                                    if (arg0 == 1) {
                                        if (i == 0) {
                                            action = 0;
                                        } else if (i == 1) {
                                            action = 1;
                                        } else if (i == 2) {
                                            action = 2;
                                        } else if (i == 3) {
                                            action = 3;
                                        }
                                    } else if (arg0 == 0) {
                                        if (i == 0) {
                                            action = 1;
                                        } else if (i == 1) {
                                            action = 2;
                                        }
                                    }
                                    processPhotoMenu(action);
                                }
                            });
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    });
                } else {
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                }
                avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                TextView textView = (TextView)view.findViewById(R.id.settings_name);
                Typeface typeface = Utilities.getTypeface("fonts/rmedium.ttf");
                textView.setTypeface(typeface);

                textView.setText(chat.title);

                if (chat.participants_count != 0 && onlineCount > 0) {
                    onlineText.setText(Html.fromHtml(String.format("%d %s, <font color='#357aa8'>%d %s</font>", chat.participants_count, LocaleController.getString("Members", R.string.Members), onlineCount, LocaleController.getString("Online", R.string.Online))));
                } else {
                    onlineText.setText(String.format("%d %s", chat.participants_count, LocaleController.getString("Members", R.string.Members)));
                }

                TLRPC.FileLocation photo = null;
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                }
                avatarImage.setImage(photo, "50_50", Utilities.getGroupAvatarForId(chat.id));
                return view;
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == 1) {
                    textView.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                } else if (i == 4) {
                    textView.setText(LocaleController.getString("SHAREDMEDIA", R.string.SHAREDMEDIA));
                } else if (i == 6) {
                    TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
                    textView.setText(String.format("%d %s", chat.participants_count, LocaleController.getString("MEMBERS", R.string.MEMBERS)));
                }
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_check_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 2) {
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String key = "notify_" + (-chat_id);
                    boolean value = preferences.getBoolean(key, true);
                    ImageView checkButton = (ImageView)view.findViewById(R.id.settings_row_check_button);
                    if (value) {
                        checkButton.setImageResource(R.drawable.btn_check_on);
                    } else {
                        checkButton.setImageResource(R.drawable.btn_check_off);
                    }
                    textView.setText(LocaleController.getString("Notifications", R.string.Notifications));
                    divider.setVisibility(View.VISIBLE);
                }
            } else if (type == 3) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 5) {
                    textView.setText(LocaleController.getString("SharedMedia", R.string.SharedMedia));
                    if (totalMediaCount == -1) {
                        detailTextView.setText(LocaleController.getString("Loading", R.string.Loading));
                    } else {
                        detailTextView.setText(String.format("%d", totalMediaCount));
                    }
                    divider.setVisibility(View.INVISIBLE);
                }
            } else if (type == 4) {
                TLRPC.TL_chatParticipant part = info.participants.get(sortedUsers.get(i - 7));
                TLRPC.User user = MessagesController.getInstance().users.get(part.user_id);

                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                    ((ChatOrUserCell)view).useBoldFont = true;
                    ((ChatOrUserCell)view).usePadding = false;
                    ((ChatOrUserCell)view).useSeparator = true;
                }

                ((ChatOrUserCell)view).setData(user, null, null, null, null);

//                if (info.admin_id != UserConfig.clientUserId && part.inviter_id != UserConfig.clientUserId && part.user_id != UserConfig.clientUserId) {
//
//                } else {
//
//                }
            } else if (type == 5) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_profile_add_row, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.messages_list_row_name);
                    textView.setText(LocaleController.getString("AddMember", R.string.AddMember));
                }
            } else if (type == 6) {
                if (view == null) {
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.settings_logout_button, viewGroup, false);
                        TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                        textView.setText(LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
                        textView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        kickUser(null);
                                    }
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                builder.show().setCanceledOnTouchOutside(true);
                            }
                        });
                    }
                }
            } else if (type == 7) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_detail_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == 3) {
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    String name = preferences.getString("sound_chat_" + chat_id, LocaleController.getString("Default", R.string.Default));
                    if (name.equals("NoSound")) {
                        detailTextView.setText(LocaleController.getString("NoSound", R.string.NoSound));
                    } else {
                        detailTextView.setText(name);
                    }
                    textView.setText(LocaleController.getString("Sound", R.string.Sound));
                    divider.setVisibility(View.INVISIBLE);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == 0) {
                return 0;
            } else if (i == 1 || i == 4 || i == 6) {
                return 1;
            } else if (i == 2) {
                return 2;
            } else if (i == 5) {
                return 3;
            } else if (i == 3) {
                return 7;
            } else if (i > 6) {
                int size = 0;
                if (info != null) {
                    size += info.participants.size();
                }
                if (i > 6 && i < size + 7) {
                    return 4;
                } else {
                    if (size + 7 == i) {
                        if (info != null && info.participants.size() < 200) {
                            return 5;
                        } else {
                            return 6;
                        }
                    } else if (size + 8 == i) {
                        return 6;
                    }
                }
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 8;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
