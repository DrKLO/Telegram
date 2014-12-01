/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.SecretChatHelper;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.android.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.IdenticonDrawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class ProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate, PhotoViewer.PhotoViewerProvider {

    private ListView listView;
    private ListAdapter listAdapter;
    private BackupImageView avatarImage;
    private TextView nameTextView;
    private TextView onlineTextView;
    private ImageView writeButton;

    private int user_id;
    private int chat_id;
    private long dialog_id;
    private boolean creatingChat;
    private boolean userBlocked;

    private AvatarUpdater avatarUpdater;
    private TLRPC.ChatParticipants info;
    private TLRPC.TL_chatParticipant selectedUser;
    private int onlineCount = -1;
    private ArrayList<Integer> sortedUsers;

    private TLRPC.EncryptedChat currentEncryptedChat;
    private TLRPC.Chat currentChat;

    private int totalMediaCount = -1;

    private final static int add_contact = 1;
    private final static int block_contact = 2;
    private final static int share_contact = 3;
    private final static int edit_contact = 4;
    private final static int delete_contact = 5;
    private final static int add_member = 6;
    private final static int leave_group = 7;
    private final static int edit_name = 8;

    private int overscrollRow;
    private int emptyRow;
    private int emptyRowChat;
    private int emptyRowChat2;
    private int phoneRow;
    private int usernameRow;
    private int settingsTimerRow;
    private int settingsKeyRow;
    private int settingsNotificationsRow;
    private int sharedMediaRow;
    private int startSecretChatRow;
    private int sectionRow;
    private int membersSectionRow;
    private int membersEndRow;
    private int rowCount = 0;

    public ProfileActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        user_id = arguments.getInt("user_id", 0);
        chat_id = getArguments().getInt("chat_id", 0);
        if (user_id != 0) {
            dialog_id = arguments.getLong("dialog_id", 0);
            if (dialog_id != 0) {
                currentEncryptedChat = MessagesController.getInstance().getEncryptedChat((int) (dialog_id >> 32));
            }
            if (MessagesController.getInstance().getUser(user_id) == null) {
                return false;
            }
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatCreated);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatUpdated);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
            userBlocked = MessagesController.getInstance().blockedUsers.contains(user_id);

            MessagesController.getInstance().loadFullUser(MessagesController.getInstance().getUser(user_id), classGuid);
        } else if (chat_id != 0) {
            currentChat = MessagesController.getInstance().getChat(chat_id);
            if (currentChat == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentChat = MessagesStorage.getInstance().getChat(chat_id);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentChat != null) {
                    MessagesController.getInstance().putChat(currentChat, true);
                } else {
                    return false;
                }
            }


            NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);

            sortedUsers = new ArrayList<Integer>();
            updateOnlineCount();
            if (chat_id > 0) {
                MessagesController.getInstance().getMediaCount(-chat_id, classGuid, true);
            }

            avatarUpdater = new AvatarUpdater();
            avatarUpdater.delegate = new AvatarUpdater.AvatarUpdaterDelegate() {
                @Override
                public void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big) {
                    if (chat_id != 0) {
                        MessagesController.getInstance().changeChatAvatar(chat_id, file);
                    }
                }
            };
            avatarUpdater.parentFragment = this;
        } else {
            return false;
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        updateRowsIds();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        if (user_id != 0) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatCreated);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatUpdated);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
            MessagesController.getInstance().cancelLoadFullUser(user_id);
        } else if (chat_id != 0) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
            avatarUpdater.clear();
        }
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackgroundColor(AvatarDrawable.getProfileBackColorForId(user_id != 0 ? 5 : chat_id));
            actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(user_id != 0 ? 5 : chat_id));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setExtraHeight(AndroidUtilities.dp(88), false);
            if (AndroidUtilities.isTablet()) {
                actionBar.setOccupyStatusBar(false);
            }
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(final int id) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (id == -1) {
                        finishFragment();
                    } else if (id == block_contact) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        if (!userBlocked) {
                            builder.setMessage(LocaleController.getString("AreYouSureBlockContact", R.string.AreYouSureBlockContact));
                        } else {
                            builder.setMessage(LocaleController.getString("AreYouSureUnblockContact", R.string.AreYouSureUnblockContact));
                        }
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (!userBlocked) {
                                    MessagesController.getInstance().blockUser(user_id);
                                } else {
                                    MessagesController.getInstance().unblockUser(user_id);
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == add_contact) {
                        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        Bundle args = new Bundle();
                        args.putInt("user_id", user.id);
                        presentFragment(new ContactAddActivity(args));
                    } else if (id == share_contact) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putBoolean("serverOnly", true);
                        MessagesActivity fragment = new MessagesActivity(args);
                        fragment.setDelegate(ProfileActivity.this);
                        presentFragment(fragment);
                    } else if (id == edit_contact) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", user_id);
                        presentFragment(new ContactAddActivity(args));
                    } else if (id == delete_contact) {
                        final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        if (user == null || getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteContact", R.string.AreYouSureDeleteContact));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ArrayList<TLRPC.User> arrayList = new ArrayList<TLRPC.User>();
                                arrayList.add(user);
                                ContactsController.getInstance().deleteContact(arrayList);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == add_member) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("returnAsResult", true);
                        //args.putBoolean("allowUsernameSearch", false);
                        if (chat_id > 0) {
                            args.putString("selectAlertString", LocaleController.getString("AddToTheGroup", R.string.AddToTheGroup));
                        }
                        ContactsActivity fragment = new ContactsActivity(args);
                        fragment.setDelegate(new ContactsActivity.ContactsActivityDelegate() {
                            @Override
                            public void didSelectContact(TLRPC.User user, String param) {
                                MessagesController.getInstance().addUserToChat(chat_id, user, info, param != null ? Utilities.parseInt(param) : 0);
                            }
                        });
                        if (info != null) {
                            HashMap<Integer, TLRPC.User> users = new HashMap<Integer, TLRPC.User>();
                            for (TLRPC.TL_chatParticipant p : info.participants) {
                                users.put(p.user_id, null);
                            }
                            fragment.setIgnoreUsers(users);
                        }
                        presentFragment(fragment);
                    } else if (id == leave_group) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                kickUser(null);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == edit_name) {
                        Bundle args = new Bundle();
                        args.putInt("chat_id", chat_id);
                        presentFragment(new ChangeChatNameActivity(args));
                    }
                }
            });

            createActionBarMenu();

            listAdapter = new ListAdapter(getParentActivity());

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;

            avatarImage = new BackupImageView(getParentActivity());
            avatarImage.imageReceiver.setRoundRadius(AndroidUtilities.dp(30));
            avatarImage.processDetach = false;
            actionBar.addView(avatarImage);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
            layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM;
            layoutParams.width = AndroidUtilities.dp(60);
            layoutParams.height = AndroidUtilities.dp(60);
            layoutParams.leftMargin = LocaleController.isRTL ? 0 : AndroidUtilities.dp(17);
            layoutParams.rightMargin = LocaleController.isRTL ? AndroidUtilities.dp(17) : 0;
            layoutParams.bottomMargin = AndroidUtilities.dp(22);
            avatarImage.setLayoutParams(layoutParams);
            avatarImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (user_id != 0) {
                        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        if (user.photo != null && user.photo.photo_big != null) {
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            PhotoViewer.getInstance().openPhoto(user.photo.photo_big, ProfileActivity.this);
                        }
                    } else if (chat_id != 0) {
                        TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
                        if (chat.photo != null && chat.photo.photo_big != null) {
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            PhotoViewer.getInstance().openPhoto(chat.photo.photo_big, ProfileActivity.this);
                        }
                    }
                }
            });

            nameTextView = new TextView(getParentActivity());
            nameTextView.setTextColor(0xffffffff);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            nameTextView.setLines(1);
            nameTextView.setMaxLines(1);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            actionBar.addView(nameTextView);
            layoutParams = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 16 : 97);
            layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 97 : 16);
            layoutParams.bottomMargin = AndroidUtilities.dp(51);
            layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM;
            nameTextView.setLayoutParams(layoutParams);

            onlineTextView = new TextView(getParentActivity());
            onlineTextView.setTextColor(AvatarDrawable.getProfileTextColorForId(user_id != 0 ? 5 : chat_id));
            onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            onlineTextView.setLines(1);
            onlineTextView.setMaxLines(1);
            onlineTextView.setSingleLine(true);
            onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
            onlineTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            actionBar.addView(onlineTextView);
            layoutParams = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 16 : 97);
            layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 97 : 16);
            layoutParams.bottomMargin = AndroidUtilities.dp(30);
            layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM;
            onlineTextView.setLayoutParams(layoutParams);

            listView = new ListView(getParentActivity());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            AndroidUtilities.setListViewEdgeEffectColor(listView, AvatarDrawable.getProfileBackColorForId(user_id != 0 ? 5 : chat_id));
            frameLayout.addView(listView);
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            listView.setLayoutParams(layoutParams);

            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (i == sharedMediaRow) {
                        Bundle args = new Bundle();
                        if (user_id != 0) {
                            args.putLong("dialog_id", dialog_id != 0 ? dialog_id : user_id);
                        } else {
                            args.putLong("dialog_id", -chat_id);
                        }
                        presentFragment(new MediaActivity(args));
                    } else if (i == settingsKeyRow) {
                        Bundle args = new Bundle();
                        args.putInt("chat_id", (int)(dialog_id >> 32));
                        presentFragment(new IdenticonActivity(args));
                    } else if (i == settingsTimerRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        showAlertDialog(AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat));
                    } else if (i == settingsNotificationsRow) {
                        Bundle args = new Bundle();
                        if (user_id != 0) {
                            args.putLong("dialog_id", dialog_id == 0 ? user_id : dialog_id);
                        } else if (chat_id != 0) {
                            args.putLong("dialog_id", -chat_id);
                        }
                        presentFragment(new ProfileNotificationsActivity(args));
                    } else if (i == startSecretChatRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureSecretChat", R.string.AreYouSureSecretChat));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                creatingChat = true;
                                SecretChatHelper.getInstance().startSecretChat(getParentActivity(), MessagesController.getInstance().getUser(user_id));
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == phoneRow) {
                        final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        if (user == null || user.phone == null || user.phone.length() == 0 || getParentActivity() == null) {
                            return;
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setItems(new CharSequence[] {LocaleController.getString("Call", R.string.Call), LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + user.phone));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        getParentActivity().startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                } else if (i == 1) {
                                    if(Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                        clipboard.setText("+" + user.phone);
                                    } else {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", "+" + user.phone);
                                        clipboard.setPrimaryClip(clip);
                                    }
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i > emptyRowChat2 && i < membersEndRow) {
                        int user_id = info.participants.get(sortedUsers.get(i - emptyRowChat2 - 1)).user_id;
                        if (user_id == UserConfig.getClientUserId()) {
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putInt("user_id", user_id);
                        presentFragment(new ProfileActivity(args));
                    }
                }
            });
            if (chat_id != 0) {
                listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                        if (i > emptyRowChat2 && i < membersEndRow) {
                            if (getParentActivity() == null) {
                                return false;
                            }

                            TLRPC.TL_chatParticipant user = info.participants.get(sortedUsers.get(i - emptyRowChat2 - 1));
                            if (user.user_id == UserConfig.getClientUserId()) {
                                return false;
                            }
                            if (info.admin_id != UserConfig.getClientUserId() && user.inviter_id != UserConfig.getClientUserId()) {
                                return false;
                            }
                            selectedUser = user;

                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            CharSequence[] items = new CharSequence[]{chat_id > 0 ? LocaleController.getString("KickFromGroup", R.string.KickFromGroup) : LocaleController.getString("KickFromBroadcast", R.string.KickFromBroadcast)};

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
            }
            if (dialog_id != 0) {
                MessagesController.getInstance().getMediaCount(dialog_id, classGuid, true);
            } else {
                MessagesController.getInstance().getMediaCount(user_id, classGuid, true);
            }

            frameLayout.addView(actionBar);

            if (user_id != 0 || chat_id >= 0 && !currentChat.left) {
                writeButton = new ImageView(getParentActivity());
                if (user_id != 0) {
                    writeButton.setImageResource(R.drawable.floating_user_states);
                } else if (chat_id != 0) {
                    writeButton.setImageResource(R.drawable.floating_group_states);
                }
                frameLayout.addView(writeButton);
                layoutParams = (FrameLayout.LayoutParams) writeButton.getLayoutParams();
                layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
                layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
                layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 16 : 0);
                layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 16);
                layoutParams.gravity = (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                writeButton.setLayoutParams(layoutParams);
                writeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        if (user_id != 0) {
                            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                            if (user == null || user instanceof TLRPC.TL_userEmpty) {
                                return;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                            Bundle args = new Bundle();
                            args.putInt("user_id", user_id);
                            presentFragment(new ChatActivity(args), true);
                        } else if (chat_id != 0) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            CharSequence[] items;
                            TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
                            if (chat.photo == null || chat.photo.photo_big == null || chat.photo instanceof TLRPC.TL_chatPhotoEmpty) {
                                items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley)};
                            } else {
                                items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                            }

                            builder.setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    int action = 0;
                                    if (i == 0) {
                                        avatarUpdater.openCamera();
                                    } else if (i == 1) {
                                        avatarUpdater.openGallery();
                                    } else if (i == 2) {
                                        MessagesController.getInstance().changeChatAvatar(chat_id, null);
                                    }
                                }
                            });
                            showAlertDialog(builder);
                        }
                    }
                });
            }

            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    checkListViewScroll();
                }
            });

            updateProfileData();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (chat_id != 0) {
            if (avatarUpdater != null && avatarUpdater.currentPicturePath != null) {
                args.putString("path", avatarUpdater.currentPicturePath);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (chat_id != 0) {
            MessagesController.getInstance().loadChatInfo(chat_id, null);
            if (avatarUpdater != null) {
                avatarUpdater.currentPicturePath = args.getString("path");
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (chat_id != 0) {
            avatarUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void checkListViewScroll() {
        if (listView.getChildCount() == 0) {
            return;
        }
        int height = 0;
        View child = listView.getChildAt(0);
        if (child != null) {
            if (listView.getFirstVisiblePosition() == 0) {
                height = AndroidUtilities.dp(88) + (child.getTop() < 0 ? child.getTop() : 0);
            }
            if (actionBar.getExtraHeight() != height) {
                actionBar.setExtraHeight(height, true);
                needLayout();
            }
        }
    }

    private void needLayout() {
        FrameLayout.LayoutParams layoutParams;
        if (listView != null) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.topMargin = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.getCurrentActionBarHeight();
            listView.setLayoutParams(layoutParams);
        }

        if (avatarImage != null) {
            float diff = actionBar.getExtraHeight() / (float)AndroidUtilities.dp(88);
            float diffm = 1.0f - diff;

            int avatarSize = 42 + (int)(18 * diff);
            int avatarX = 17 + (int)(47 * diffm);
            int avatarY = AndroidUtilities.dp(22) - (int)((AndroidUtilities.dp(22) - (AndroidUtilities.getCurrentActionBarHeight() - AndroidUtilities.dp(42)) / 2) * (1.0f - diff));
            int nameX = 97 + (int)(21 * diffm);
            int nameEndX = 16 + (int)(32 * diffm);
            float nameFontSize = 20 - 2 * diffm;
            int nameY = avatarY + AndroidUtilities.dp(29 - 10 * diffm);
            int statusY = avatarY + AndroidUtilities.dp(8 - 7 * diffm);

            if (writeButton != null) {
                layoutParams = (FrameLayout.LayoutParams) writeButton.getLayoutParams();
                layoutParams.topMargin = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.getCurrentActionBarHeight() + actionBar.getExtraHeight() - AndroidUtilities.dp(29.5f);
                writeButton.setLayoutParams(layoutParams);
                ViewProxy.setAlpha(writeButton, diff);
                writeButton.setVisibility(diff <= 0.02 ? View.GONE : View.VISIBLE);
                if (writeButton.getVisibility() == View.GONE) {
                    writeButton.clearAnimation();
                }
            }

            avatarImage.imageReceiver.setRoundRadius(AndroidUtilities.dp(avatarSize / 2));
            layoutParams = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(avatarSize);
            layoutParams.height = AndroidUtilities.dp(avatarSize);
            layoutParams.leftMargin = LocaleController.isRTL ? 0 : AndroidUtilities.dp(avatarX);
            layoutParams.rightMargin = LocaleController.isRTL ? AndroidUtilities.dp(avatarX) : 0;
            layoutParams.bottomMargin = avatarY;
            avatarImage.setLayoutParams(layoutParams);

            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, nameFontSize);
            layoutParams = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameEndX : nameX);
            layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameX : nameEndX);
            layoutParams.bottomMargin = nameY;
            nameTextView.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameEndX : nameX);
            layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameX : nameEndX);
            layoutParams.bottomMargin = statusY;
            onlineTextView.setLayoutParams(layoutParams);
        }
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    needLayout();
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return false;
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public boolean needAddActionBar() {
        return false;
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if (user_id != 0) {
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateProfileData();
                }
                if ((mask & MessagesController.UPDATE_MASK_PHONE) != 0) {
                    if (listView != null) {
                        listView.invalidateViews();
                    }
                }
            } else if (chat_id != 0) {
                if ((mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateOnlineCount();
                    updateProfileData();
                }
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof UserCell) {
                            ((UserCell) child).update(mask);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.contactsDidLoaded) {
            createActionBarMenu();
        } else if (id == NotificationCenter.mediaCountDidLoaded) {
            long uid = (Long)args[0];
            if (user_id != 0) {
                if (uid > 0 && user_id == uid && dialog_id == 0 || dialog_id != 0 && dialog_id == uid) {
                    totalMediaCount = (Integer) args[1];
                    if (listView != null) {
                        listView.invalidateViews();
                    }
                }
            } else if (chat_id != 0) {
                int lower_part = (int)uid;
                if (lower_part < 0 && chat_id == -lower_part) {
                    totalMediaCount = (Integer)args[1];
                    if (listView != null) {
                        listView.invalidateViews();
                    }
                }
            }
        } else if (id == NotificationCenter.encryptedChatCreated) {
            if (creatingChat) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                        TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) args[0];
                        Bundle args2 = new Bundle();
                        args2.putInt("enc_id", encryptedChat.id);
                        presentFragment(new ChatActivity(args2), true);
                    }
                });
            }
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                    checkListViewScroll();
                }
            }
        } else if (id == NotificationCenter.blockedUsersDidLoaded) {
            boolean oldValue = userBlocked;
            userBlocked = MessagesController.getInstance().blockedUsers.contains(user_id);
            if (oldValue != userBlocked) {
                createActionBarMenu();
            }
        } else if (id == NotificationCenter.chatInfoDidLoaded) {
            int chatId = (Integer)args[0];
            if (chatId == chat_id) {
                info = (TLRPC.ChatParticipants)args[1];
                updateOnlineCount();
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                    checkListViewScroll();
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        fixLayout();
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (fileLocation == null) {
            return null;
        }

        TLRPC.FileLocation photoBig = null;
        if (user_id != 0) {
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            if (user != null && user.photo != null && user.photo.photo_big != null) {
                photoBig = user.photo.photo_big;
            }
        } else if (chat_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
            if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                photoBig = chat.photo.photo_big;
            }
        }


        if (photoBig != null && photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
            int coords[] = new int[2];
            avatarImage.getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
            object.parentView = avatarImage;
            object.imageReceiver = avatarImage.imageReceiver;
            object.user_id = user_id;
            object.thumb = object.imageReceiver.getBitmap();
            object.size = -1;
            object.radius = avatarImage.imageReceiver.getRoundRadius();
            return object;
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() {
        avatarImage.imageReceiver.setVisible(true, true);
    }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void updateOnlineCount() {
        if (info == null) {
            return;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        sortedUsers.clear();
        int i = 0;
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId()) && user.status.expires > 10000) {
                onlineCount++;
            }
            sortedUsers.add(i);
            i++;
        }

        Collections.sort(sortedUsers, new Comparator<Integer>() {
            @Override
            public int compare(Integer lhs, Integer rhs) {
                TLRPC.User user1 = MessagesController.getInstance().getUser(info.participants.get(rhs).user_id);
                TLRPC.User user2 = MessagesController.getInstance().getUser(info.participants.get(lhs).user_id);
                int status1 = 0;
                int status2 = 0;
                if (user1 != null && user1.status != null) {
                    if (user1.id == UserConfig.getClientUserId()) {
                        status1 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                    } else {
                        status1 = user1.status.expires;
                    }
                }
                if (user2 != null && user2.status != null) {
                    if (user2.id == UserConfig.getClientUserId()) {
                        status2 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                    } else {
                        status2 = user2.status.expires;
                    }
                }
                if (status1 > 0 && status2 > 0) {
                    if (status1 > status2) {
                        return 1;
                    } else if (status1 < status2) {
                        return -1;
                    }
                    return 0;
                } else if (status1 < 0 && status2 < 0) {
                    if (status1 > status2) {
                        return 1;
                    } else if (status1 < status2) {
                        return -1;
                    }
                    return 0;
                } else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
                    return -1;
                } else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
                    return 1;
                }
                return 0;
            }
        });

        if (listView != null) {
            listView.invalidateViews();
        }
    }

    public void setChatInfo(TLRPC.ChatParticipants chatParticipants) {
        info = chatParticipants;
    }

    private void kickUser(TLRPC.TL_chatParticipant user) {
        if (user != null) {
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().getUser(user.user_id), info);
        } else {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), info);
            MessagesController.getInstance().deleteDialog(-chat_id, 0, false);
            finishFragment();
        }
    }

    public boolean isChat() {
        return chat_id != 0;
    }

    private void updateRowsIds() {
        rowCount = 0;
        overscrollRow = rowCount++;
        if (user_id != 0) {
            emptyRow = rowCount++;
            phoneRow = rowCount++;
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            if (user != null && user.username != null && user.username.length() > 0) {
                usernameRow = rowCount++;
            } else {
                usernameRow = -1;
            }
            sectionRow = rowCount++;
            settingsNotificationsRow = rowCount++;
            sharedMediaRow = rowCount++;
            if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                settingsTimerRow = rowCount++;
                settingsKeyRow = rowCount++;
            } else {
                settingsTimerRow = -1;
                settingsKeyRow = -1;
            }
            if (currentEncryptedChat == null) {
                startSecretChatRow = rowCount++;
            } else {
                startSecretChatRow = -1;
            }
        } else if (chat_id != 0) {
            if (chat_id > 0) {
                emptyRow = rowCount++;
                settingsNotificationsRow = rowCount++;
                sharedMediaRow = rowCount++;
                emptyRowChat = rowCount++;
                membersSectionRow = rowCount++;
            }
            if (info != null && !(info instanceof TLRPC.TL_chatParticipantsForbidden)) {
                emptyRowChat2 = rowCount++;
                rowCount += info.participants.size();
                membersEndRow = rowCount;
                int maxCount = chat_id > 0 ? MessagesController.getInstance().maxGroupCount : MessagesController.getInstance().maxBroadcastCount;
            } else {
                membersEndRow = -1;
                membersSectionRow = -1;
                emptyRowChat2 = -1;
            }
        }
    }

    private void updateProfileData() {
        if (avatarImage == null) {
            return;
        }
        if (user_id != 0) {
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            TLRPC.FileLocation photo = null;
            TLRPC.FileLocation photoBig = null;
            if (user.photo != null) {
                photo = user.photo.photo_small;
                photoBig = user.photo.photo_big;
            }
            AvatarDrawable avatarDrawable = new AvatarDrawable(user);
            avatarImage.setImage(photo, "50_50", avatarDrawable);

            nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            onlineTextView.setText(LocaleController.formatUserStatus(user));

            avatarImage.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
        } else if (chat_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
            if (chat != null) {
                currentChat = chat;
            }
            nameTextView.setText(chat.title);

            int count = chat.participants_count;
            if (info != null) {
                count = info.participants.size();
            }

            if (count != 0 && onlineCount > 1) {
                onlineTextView.setText(Html.fromHtml(String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("Online", onlineCount))));
            } else {
                onlineTextView.setText(LocaleController.formatPluralString("Members", count));
            }

            TLRPC.FileLocation photo = null;
            TLRPC.FileLocation photoBig = null;
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
                photoBig = chat.photo.photo_big;
            }
            avatarImage.setImage(photo, "50_50", new AvatarDrawable(chat, true));

            avatarImage.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
        }
    }

    private void createActionBarMenu() {
        ActionBarMenu menu = actionBar.createMenu();
        menu.clearItems();

        if (user_id != 0) {
            if (ContactsController.getInstance().contactsDict.get(user_id) == null) {
                TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                if (user == null) {
                    return;
                }
                ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
                if (user.phone != null && user.phone.length() != 0) {
                    item.addSubItem(add_contact, LocaleController.getString("AddContact", R.string.AddContact), 0);
                    item.addSubItem(share_contact, LocaleController.getString("ShareContact", R.string.ShareContact), 0);
                    item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock), 0);
                } else {
                    item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock), 0);
                }
            } else {
                ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
                item.addSubItem(share_contact, LocaleController.getString("ShareContact", R.string.ShareContact), 0);
                item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock), 0);
                item.addSubItem(edit_contact, LocaleController.getString("EditContact", R.string.EditContact), 0);
                item.addSubItem(delete_contact, LocaleController.getString("DeleteContact", R.string.DeleteContact), 0);
            }
        } else if (chat_id != 0) {
            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            if (chat_id > 0) {
                item.addSubItem(add_member, LocaleController.getString("AddMember", R.string.AddMember), 0);
                item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName), 0);
                item.addSubItem(leave_group, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit), 0);
            } else {
                item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName), 0);
                item.addSubItem(add_member, LocaleController.getString("AddRecipient", R.string.AddRecipient), 0);
            }
        }
    }

    @Override
    protected void onDialogDismiss() {
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id, boolean param) {
        if (dialog_id != 0) {
            Bundle args = new Bundle();
            args.putBoolean("scrollToTopOnResume", true);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            int lower_part = (int)dialog_id;
            if (lower_part != 0) {
                if (lower_part > 0) {
                    args.putInt("user_id", lower_part);
                } else if (lower_part < 0) {
                    args.putInt("chat_id", -lower_part);
                }
            } else {
                args.putInt("enc_id", (int)(dialog_id >> 32));
            }
            presentFragment(new ChatActivity(args), true);
            removeSelfFromStack();
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            SendMessagesHelper.getInstance().sendMessage(user, dialog_id);
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
            if (user_id != 0) {
                return i == phoneRow || i == settingsTimerRow || i == settingsKeyRow || i == settingsNotificationsRow || i == sharedMediaRow || i == startSecretChatRow;
            } else if (chat_id != 0) {
                return i == settingsNotificationsRow || i == sharedMediaRow || i > emptyRowChat2 && i < membersEndRow;
            }
            return false;
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
                    view = new EmptyCell(mContext);
                }
                if (i == overscrollRow) {
                    ((EmptyCell) view).setHeight(88);
                } else if (i == emptyRowChat || i == emptyRowChat2) {
                    ((EmptyCell) view).setHeight(8);
                } else {
                    ((EmptyCell) view).setHeight(36);
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new DividerCell(mContext);
                    view.setPadding(AndroidUtilities.dp(72), 0, 0, 0);
                }
            } else if (type == 2) {
                final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                if (view == null) {
                    view = new TextDetailCell(mContext);
                }
                TextDetailCell textDetailCell = (TextDetailCell) view;

                if (i == phoneRow) {
                    String text;
                    if (user.phone != null && user.phone.length() != 0) {
                        text = PhoneFormat.getInstance().format("+" + user.phone);
                    } else {
                        text = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                    }
                    textDetailCell.setTextAndValueAndIcon(text, LocaleController.getString("PhoneMobile", R.string.PhoneMobile), R.drawable.phone_grey);
                } else if (i == usernameRow) {
                    String text;
                    if (user != null && user.username != null && user.username.length() != 0) {
                        text = "@" + user.username;
                    } else {
                        text = "-";
                    }
                    textDetailCell.setTextAndValue(text, LocaleController.getString("Username", R.string.Username));
                }
            } else if (type == 3) {
                if (view == null) {
                    view = new TextCell(mContext);
                }
                TextCell textCell = (TextCell) view;
                textCell.setTextColor(0xff212121);

                if (i == sharedMediaRow) {
                    String value;
                    if (totalMediaCount == -1) {
                        value = LocaleController.getString("Loading", R.string.Loading);
                    } else {
                        value = String.format("%d", totalMediaCount);
                    }
                    textCell.setTextAndValue(LocaleController.getString("SharedMedia", R.string.SharedMedia), value);
                } else if (i == settingsTimerRow) {
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat((int)(dialog_id >> 32));
                    String value;
                    if (encryptedChat.ttl == 0) {
                        value = LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever);
                    } else {
                        value = AndroidUtilities.formatTTLString(encryptedChat.ttl);
                    }
                    textCell.setTextAndValue(LocaleController.getString("MessageLifetime", R.string.MessageLifetime), value);
                } else if (i == settingsNotificationsRow) {
                    textCell.setTextAndIcon(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.profile_list);
                } else if (i == startSecretChatRow) {
                    textCell.setText(LocaleController.getString("StartEncryptedChat", R.string.StartEncryptedChat));
                    textCell.setTextColor(0xff37a919);
                } else if (i == settingsKeyRow) {
                    IdenticonDrawable identiconDrawable = new IdenticonDrawable();
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat((int)(dialog_id >> 32));
                    identiconDrawable.setEncryptedChat(encryptedChat);
                    textCell.setTextAndValueDrawable(LocaleController.getString("EncryptionKey", R.string.EncryptionKey), identiconDrawable);
                }
            } else if (type == 4) {
                if (view == null) {
                    view = new UserCell(mContext, 61);
                }

                TLRPC.TL_chatParticipant part = info.participants.get(sortedUsers.get(i - emptyRowChat2 - 1));
                ((UserCell)view).setData(MessagesController.getInstance().getUser(part.user_id), null, null, i == emptyRowChat2 + 1 ? R.drawable.menu_newgroup : 0);
            } else if (type == 5) {
                if (view == null) {
                    view = new ShadowSectionCell(mContext);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == emptyRow || i == overscrollRow || i == emptyRowChat || i == emptyRowChat2) {
                return 0;
            } else if (i == sectionRow) {
                return 1;
            } else if (i == phoneRow || i == usernameRow) {
                return 2;
            } else if (i == sharedMediaRow || i == settingsTimerRow || i == settingsNotificationsRow || i == startSecretChatRow || i == settingsKeyRow) {
                return 3;
            } else if (i > emptyRowChat2 && i < membersEndRow) {
                return 4;
            } else if (i == membersSectionRow) {
                return 5;
            }
            return 0;
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
