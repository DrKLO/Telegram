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
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
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
import org.telegram.objects.MessageObject;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class ChatProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate, PhotoViewer.PhotoViewerProvider {
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

    private int avatarRow;
    private int settingsSectionRow;
    private int settingsNotificationsRow;
    private int settingsVibrateRow;
    private int settingsSoundRow;
    private int sharedMediaSectionRow;
    private int sharedMediaRow;
    private int membersSectionRow;
    private int addMemberRow;
    private int leaveGroupRow;
    private int rowCount = 0;

    private static final int done_button = 1;

    public ChatProfileActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.closeChats);

        chat_id = getArguments().getInt("chat_id", 0);
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

        updateRowsIds();

        return true;
    }

    private void updateRowsIds() {
        rowCount = 0;
        avatarRow = rowCount++;
        settingsSectionRow = rowCount++;
        settingsNotificationsRow = rowCount++;
        settingsVibrateRow = rowCount++;
        settingsSoundRow = rowCount++;
        sharedMediaSectionRow = rowCount++;
        sharedMediaRow = rowCount++;
        if (info != null && !(info instanceof TLRPC.TL_chatParticipantsForbidden)) {
            membersSectionRow = rowCount++;
            rowCount += info.participants.size();
            if (info.participants.size() < 200) {
                addMemberRow = rowCount++;
            } else {
                addMemberRow = -1;
            }
        } else {
            addMemberRow = -1;
            membersSectionRow = -1;
        }
        leaveGroupRow = rowCount++;
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

    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true);
            actionBarLayer.setTitle(LocaleController.getString("GroupInfo", R.string.GroupInfo));
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        openAddMenu();
                    }
                }
            });
            ActionBarMenu menu = actionBarLayer.createMenu();
            View item = menu.addItemResource(done_button, R.layout.group_profile_add_member_layout);
            TextView textView = (TextView)item.findViewById(R.id.done_button);
            if (textView != null) {
                textView.setText(LocaleController.getString("AddMember", R.string.AddMember));
            }

            fragmentView = inflater.inflate(R.layout.chat_profile_layout, container, false);

            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listViewAdapter = new ListAdapter(getParentActivity()));
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i > membersSectionRow && i < addMemberRow) {
                        TLRPC.TL_chatParticipant user = info.participants.get(sortedUsers.get(i - membersSectionRow - 1));
                        if (user.user_id == UserConfig.clientUserId) {
                            return false;
                        }
                        if (info.admin_id != UserConfig.clientUserId && user.inviter_id != UserConfig.clientUserId) {
                            return false;
                        }
                        selectedUser = user;

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        CharSequence[] items = new CharSequence[] {LocaleController.getString("KickFromGroup", R.string.KickFromGroup)};

                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    kickUser(selectedUser);
                                }
                            }
                        });
                        showAlertDialog(builder);

                        return true;
                    }
                    return false;
                }
            });

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == settingsSoundRow) {
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

                            String path = preferences.getString("sound_chat_path_" + chat_id, defaultPath);
                            if (path != null && !path.equals("NoSound")) {
                                if (path.equals(defaultPath)) {
                                    currentSound = defaultUri;
                                } else {
                                    currentSound = Uri.parse(path);
                                }
                            }

                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                            getParentActivity().startActivityForResult(tmpIntent, 3);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (i == sharedMediaRow) {
                        Bundle args = new Bundle();
                        args.putLong("dialog_id", -chat_id);
                        presentFragment(new MediaActivity(args));
                    } else if (i == addMemberRow) {
                        openAddMenu();
                    } else if (i > membersSectionRow && i < addMemberRow) {
                        int user_id = info.participants.get(sortedUsers.get(i - membersSectionRow - 1)).user_id;
                        if (user_id == UserConfig.clientUserId) {
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putInt("user_id", user_id);
                        presentFragment(new UserProfileActivity(args));
                    } else if (i == settingsVibrateRow || i == settingsNotificationsRow) {
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
                                if (i == settingsVibrateRow) {
                                    editor.putInt("vibrate_" + (-chat_id), which);
                                } else if (i == settingsNotificationsRow) {
                                    editor.putInt("notify2_" + (-chat_id), which);
                                }
                                editor.commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
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
    public void didSelectContact(TLRPC.User user) {
        MessagesController.getInstance().addUserToChat(chat_id, user, info);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 3) {
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

    @Override
    public void saveSelfArgs(Bundle args) {
        if (avatarUpdater != null && avatarUpdater.currentPicturePath != null) {
            args.putString("path", avatarUpdater.currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (avatarUpdater != null) {
            avatarUpdater.currentPicturePath = args.getString("path");
        }
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation) {
        if (fileLocation == null) {
            return null;
        }
        TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
        if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
            TLRPC.FileLocation photoBig = chat.photo.photo_big;
            if (photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    BackupImageView avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                    if (avatarImage != null) {
                        int coords[] = new int[2];
                        avatarImage.getLocationInWindow(coords);
                        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                        object.viewX = coords[0];
                        object.viewY = coords[1] - Utilities.statusBarHeight;
                        object.parentView = listView;
                        object.imageReceiver = avatarImage.imageReceiver;
                        object.thumb = object.imageReceiver.getBitmap();
                        object.size = -1;
                        return object;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void willHidePhotoViewer() {

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
                updateRowsIds();
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
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    public void setChatInfo(TLRPC.ChatParticipants chatParticipants) {
        info = chatParticipants;
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
            TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
            if (chat.photo != null && chat.photo.photo_big != null) {
                PhotoViewer.getInstance().openPhoto(chat.photo.photo_big, this);
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
        Bundle args = new Bundle();
        args.putBoolean("onlyUsers", true);
        args.putBoolean("destroyAfterSelect", true);
        args.putBoolean("usersAsSections", true);
        args.putBoolean("returnAsResult", true);
        args.putString("selectAlertString", LocaleController.getString("AddToTheGroup", R.string.AddToTheGroup));
        ContactsActivity fragment = new ContactsActivity(args);
        fragment.setDelegate(this);
        if (info != null) {
            HashMap<Integer, TLRPC.User> users = new HashMap<Integer, TLRPC.User>();
            for (TLRPC.TL_chatParticipant p : info.participants) {
                users.put(p.user_id, null);
            }
            fragment.setIgnoreUsers(users);
        }
        presentFragment(fragment);
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
            return i == settingsNotificationsRow || i == settingsSoundRow || i == sharedMediaRow || i > membersSectionRow && i <= addMemberRow || i == settingsVibrateRow;
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
                            Bundle args = new Bundle();
                            args.putInt("chat_id", chat_id);
                            presentFragment(new ChatProfileChangeNameActivity(args));
                        }
                    });

                    final ImageButton button2 = (ImageButton)view.findViewById(R.id.settings_change_avatar_button);
                    button2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
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
                            showAlertDialog(builder);
                        }
                    });
                } else {
                    onlineText = (TextView)view.findViewById(R.id.settings_online);
                }
                avatarImage = (BackupImageView)view.findViewById(R.id.settings_avatar_image);
                avatarImage.processDetach = false;
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
                TLRPC.FileLocation photoBig = null;
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                    photoBig = chat.photo.photo_big;
                }
                avatarImage.setImage(photo, "50_50", Utilities.getGroupAvatarForId(chat.id));
                avatarImage.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
                return view;
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                if (i == settingsSectionRow) {
                    textView.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                } else if (i == sharedMediaSectionRow) {
                    textView.setText(LocaleController.getString("SHAREDMEDIA", R.string.SHAREDMEDIA));
                } else if (i == membersSectionRow) {
                    TLRPC.Chat chat = MessagesController.getInstance().chats.get(chat_id);
                    textView.setText(String.format("%d %s", chat.participants_count, LocaleController.getString("MEMBERS", R.string.MEMBERS)));
                }
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.user_profile_leftright_row_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == sharedMediaRow) {
                    textView.setText(LocaleController.getString("SharedMedia", R.string.SharedMedia));
                    if (totalMediaCount == -1) {
                        detailTextView.setText(LocaleController.getString("Loading", R.string.Loading));
                    } else {
                        detailTextView.setText(String.format("%d", totalMediaCount));
                    }
                    divider.setVisibility(View.INVISIBLE);
                } else if (i == settingsVibrateRow) {
                    textView.setText(LocaleController.getString("Vibrate", R.string.Vibrate));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int value = preferences.getInt("vibrate_" + (-chat_id), 0);
                    if (value == 0) {
                        detailTextView.setText(LocaleController.getString("Default", R.string.Default));
                    } else if (value == 1) {
                        detailTextView.setText(LocaleController.getString("Enabled", R.string.Enabled));
                    } else if (value == 2) {
                        detailTextView.setText(LocaleController.getString("Disabled", R.string.Disabled));
                    }
                } else if (i == settingsNotificationsRow) {
                    textView.setText(LocaleController.getString("Notifications", R.string.Notifications));
                    divider.setVisibility(View.VISIBLE);
                    SharedPreferences preferences = mContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    int value = preferences.getInt("notify2_" + (-chat_id), 0);
                    if (value == 0) {
                        detailTextView.setText(LocaleController.getString("Default", R.string.Default));
                    } else if (value == 1) {
                        detailTextView.setText(LocaleController.getString("Enabled", R.string.Enabled));
                    } else if (value == 2) {
                        detailTextView.setText(LocaleController.getString("Disabled", R.string.Disabled));
                    }
                }
            } else if (type == 3) {
                TLRPC.TL_chatParticipant part = info.participants.get(sortedUsers.get(i - membersSectionRow - 1));
                TLRPC.User user = MessagesController.getInstance().users.get(part.user_id);

                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                    ((ChatOrUserCell)view).usePadding = false;
                    ((ChatOrUserCell)view).useSeparator = true;
                }

                ((ChatOrUserCell)view).setData(user, null, null, null, null);

//                if (info.admin_id != UserConfig.clientUserId && part.inviter_id != UserConfig.clientUserId && part.user_id != UserConfig.clientUserId) {
//
//                } else {
//
//                }
            } else if (type == 4) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_profile_add_row, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.messages_list_row_name);
                    textView.setText(LocaleController.getString("AddMember", R.string.AddMember));
                }
            } else if (type == 5) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_logout_button, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                    textView.setText(LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
                    textView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    kickUser(null);
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showAlertDialog(builder);
                        }
                    });
                }
            } else if (type == 6) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_row_detail_layout, viewGroup, false);
                }
                TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
                TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
                View divider = view.findViewById(R.id.settings_row_divider);
                if (i == settingsSoundRow) {
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
            if (i == avatarRow) {
                return 0;
            } else if (i == settingsSectionRow || i == sharedMediaSectionRow || i == membersSectionRow) {
                return 1;
            } else if (i == sharedMediaRow || i == settingsVibrateRow || i == settingsNotificationsRow) {
                return 2;
            } else if (i == settingsSoundRow) {
                return 6;
            } else if (i == addMemberRow) {
                return 4;
            } else if (i == leaveGroupRow) {
                return 5;
            } else if (i > membersSectionRow && i < addMemberRow) {
                return 3;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 7;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
