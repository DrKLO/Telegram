/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.query.BotQuery;
import org.telegram.messenger.query.SharedMediaQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.IdenticonDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class ProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate, PhotoViewer.PhotoViewerProvider {

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private BackupImageView avatarImage;
    private SimpleTextView nameTextView[] = new SimpleTextView[2];
    private SimpleTextView onlineTextView[] = new SimpleTextView[2];
    private ImageView writeButton;
    private AnimatorSetProxy writeButtonAnimation;
    private AvatarDrawable avatarDrawable;
    private ActionBarMenuItem animatingItem;
    private TopView topView;
    private int user_id;
    private int chat_id;
    private long dialog_id;
    private boolean creatingChat;
    private boolean userBlocked;
    private long mergeDialogId;

    private boolean loadingUsers;
    private HashMap<Integer, TLRPC.ChatParticipant> participantsMap = new HashMap<>();
    private boolean usersEndReached;

    private boolean openAnimationInProgress;
    private boolean playProfileAnimation;
    private int extraHeight;
    private int initialAnimationExtraHeight;
    private float animationProgress;

    private AvatarUpdater avatarUpdater;
    private TLRPC.ChatFull info;
    private int selectedUser;
    private int onlineCount = -1;
    private ArrayList<Integer> sortedUsers;

    private TLRPC.EncryptedChat currentEncryptedChat;
    private TLRPC.Chat currentChat;
    private TLRPC.BotInfo botInfo;

    private int totalMediaCount = -1;
    private int totalMediaCountMerge = -1;

    private final static int add_contact = 1;
    private final static int block_contact = 2;
    private final static int share_contact = 3;
    private final static int edit_contact = 4;
    private final static int delete_contact = 5;
    private final static int leave_group = 7;
    private final static int edit_name = 8;
    private final static int invite_to_group = 9;
    private final static int share = 10;
    private final static int set_admins = 11;
    private final static int edit_channel = 12;
    private final static int convert_to_supergroup = 13;

    private int emptyRow;
    private int emptyRowChat;
    private int emptyRowChat2;
    private int phoneRow;
    private int usernameRow;
    private int channelInfoRow;
    private int channelNameRow;
    private int settingsTimerRow;
    private int settingsKeyRow;
    private int convertRow;
    private int convertHelpRow;
    private int settingsNotificationsRow;
    private int sharedMediaRow;
    private int membersRow;
    private int managementRow;
    private int blockedUsersRow;
    private int leaveChannelRow;
    private int startSecretChatRow;
    private int sectionRow;
    private int userSectionRow;
    private int userInfoRow;
    private int membersSectionRow;
    private int membersEndRow;
    private int loadMoreMembersRow;
    private int addMemberRow;
    private int rowCount = 0;

    private class TopView extends View {

        private int currentColor;
        private Paint paint = new Paint();

        public TopView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ActionBar.getCurrentActionBarHeight() + (!AndroidUtilities.isTablet() ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(91));
        }

        @Override
        public void setBackgroundColor(int color) {
            if (color != currentColor) {
                paint.setColor(color);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int height = getMeasuredHeight() - AndroidUtilities.dp(91);
            canvas.drawRect(0, 0, getMeasuredWidth(), height + extraHeight, paint);
            if (parentLayout != null) {
                parentLayout.drawHeaderShadow(canvas, height + extraHeight);
            }
        }
    }

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
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            if (user == null) {
                return false;
            }
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatCreated);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatUpdated);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.botInfoDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.userInfoDidLoaded);
            if (currentEncryptedChat != null) {
                NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedNewMessages);
            }
            userBlocked = MessagesController.getInstance().blockedUsers.contains(user_id);
            if (user.bot) {
                BotQuery.loadBotInfo(user.id, true, classGuid);
            }
            MessagesController.getInstance().loadFullUser(MessagesController.getInstance().getUser(user_id), classGuid, true);
            participantsMap = null;
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

            if (currentChat.megagroup) {
                getChannelParticipants(true);
            } else {
                participantsMap = null;
            }
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);

            sortedUsers = new ArrayList<>();
            updateOnlineCount();

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

            if (ChatObject.isChannel(currentChat)) {
                MessagesController.getInstance().loadFullChat(chat_id, classGuid, true);
            }
        } else {
            return false;
        }

        if (dialog_id != 0) {
            SharedMediaQuery.getMediaCount(dialog_id, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
        } else if (user_id != 0) {
            SharedMediaQuery.getMediaCount(user_id, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
        } else if (chat_id > 0) {
            SharedMediaQuery.getMediaCount(-chat_id, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
            if (mergeDialogId != 0) {
                SharedMediaQuery.getMediaCount(mergeDialogId, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
            }
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        updateRowsIds();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        if (user_id != 0) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatCreated);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatUpdated);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.botInfoDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.userInfoDidLoaded);
            MessagesController.getInstance().cancelLoadFullUser(user_id);
            if (currentEncryptedChat != null) {
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedNewMessages);
            }
        } else if (chat_id != 0) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
            avatarUpdater.clear();
        }
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return super.onTouchEvent(event); //TODO
            }
        };
        actionBar.setItemsBackgroundColor(AvatarDrawable.getButtonColorForId(user_id != 0 || ChatObject.isChannel(chat_id) && !currentChat.megagroup ? 5 : chat_id));
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());
        return actionBar;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        extraHeight = 88;
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (getParentActivity() == null) {
                    return;
                }
                if (id == -1) {
                    finishFragment();
                } else if (id == block_contact) {
                    TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                    if (user == null) {
                        return;
                    }
                    if (!user.bot) {
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
                        showDialog(builder.create());
                    } else {
                        if (!userBlocked) {
                            MessagesController.getInstance().blockUser(user_id);
                        } else {
                            MessagesController.getInstance().unblockUser(user_id);
                            SendMessagesHelper.getInstance().sendMessage("/start", user_id, null, null, false, false, null, null, null);
                            finishFragment();
                        }
                    }
                } else if (id == add_contact) {
                    TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                    Bundle args = new Bundle();
                    args.putInt("user_id", user.id);
                    args.putBoolean("addContact", true);
                    presentFragment(new ContactAddActivity(args));
                } else if (id == share_contact) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 1);
                    args.putString("selectAlertString", LocaleController.getString("SendContactTo", R.string.SendContactTo));
                    args.putString("selectAlertStringGroup", LocaleController.getString("SendContactToGroup", R.string.SendContactToGroup));
                    DialogsActivity fragment = new DialogsActivity(args);
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
                            ArrayList<TLRPC.User> arrayList = new ArrayList<>();
                            arrayList.add(user);
                            ContactsController.getInstance().deleteContact(arrayList);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (id == leave_group) {
                    leaveChatPressed();
                } else if (id == edit_name) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    presentFragment(new ChangeChatNameActivity(args));
                } else if (id == edit_channel) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    ChannelEditActivity fragment = new ChannelEditActivity(args);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                } else if (id == invite_to_group) {
                    final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                    if (user == null) {
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 2);
                    args.putString("addToGroupAlertString", LocaleController.formatString("AddToTheGroupTitle", R.string.AddToTheGroupTitle, UserObject.getUserName(user), "%1$s"));
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                        @Override
                        public void didSelectDialog(DialogsActivity fragment, long did, boolean param) {
                            Bundle args = new Bundle();
                            args.putBoolean("scrollToTopOnResume", true);
                            args.putInt("chat_id", -(int) did);
                            if (!MessagesController.checkCanOpenChat(args, fragment)) {
                                return;
                            }

                            NotificationCenter.getInstance().removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                            MessagesController.getInstance().addUserToChat(-(int) did, user, null, 0, null, ProfileActivity.this);
                            presentFragment(new ChatActivity(args), true);
                            removeSelfFromStack();
                        }
                    });
                    presentFragment(fragment);
                } else if (id == share) {
                    try {
                        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        if (user == null) {
                            return;
                        }
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        String about = MessagesController.getInstance().getUserAbout(botInfo.user_id);
                        if (botInfo != null && about != null) {
                            intent.putExtra(Intent.EXTRA_TEXT, String.format("%s https://telegram.me/%s", about, user.username));
                        } else {
                            intent.putExtra(Intent.EXTRA_TEXT, String.format("https://telegram.me/%s", user.username));
                        }
                        startActivityForResult(Intent.createChooser(intent, LocaleController.getString("BotShare", R.string.BotShare)), 500);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (id == set_admins) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    SetAdminsActivity fragment = new SetAdminsActivity(args);
                    fragment.setChatInfo(info);
                    presentFragment(fragment);
                } else if (id == convert_to_supergroup) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    presentFragment(new ConvertGroupActivity(args));
                }
            }
        });

        createActionBarMenu();

        listAdapter = new ListAdapter(context);
        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setProfile(true);

        fragmentView = new FrameLayout(context) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkListViewScroll();
            }
        };
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        listView.setPadding(0, AndroidUtilities.dp(88), 0, 0);
        listView.setBackgroundColor(0xffffffff);
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setClipToPadding(false);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        listView.setGlowColor(AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id) && !currentChat.megagroup ? 5 : chat_id));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, final int position) {
                if (getParentActivity() == null) {
                    return;
                }
                if (position == sharedMediaRow) {
                    Bundle args = new Bundle();
                    if (user_id != 0) {
                        args.putLong("dialog_id", dialog_id != 0 ? dialog_id : user_id);
                    } else {
                        args.putLong("dialog_id", -chat_id);
                    }
                    MediaActivity fragment = new MediaActivity(args);
                    fragment.setChatInfo(info);
                    presentFragment(fragment);
                } else if (position == settingsKeyRow) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", (int) (dialog_id >> 32));
                    presentFragment(new IdenticonActivity(args));
                } else if (position == settingsTimerRow) {
                    showDialog(AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat).create());
                } else if (position == settingsNotificationsRow) {
                    Bundle args = new Bundle();
                    if (user_id != 0) {
                        args.putLong("dialog_id", dialog_id == 0 ? user_id : dialog_id);
                    } else if (chat_id != 0) {
                        args.putLong("dialog_id", -chat_id);
                    }
                    presentFragment(new ProfileNotificationsActivity(args));
                } else if (position == startSecretChatRow) {
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
                    showDialog(builder.create());
                } else if (position > emptyRowChat2 && position < membersEndRow) {
                    int user_id;
                    if (!sortedUsers.isEmpty()) {
                        user_id = info.participants.participants.get(sortedUsers.get(position - emptyRowChat2 - 1)).user_id;
                    } else {
                        user_id = info.participants.participants.get(position - emptyRowChat2 - 1).user_id;
                    }
                    if (user_id == UserConfig.getClientUserId()) {
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putInt("user_id", user_id);
                    presentFragment(new ProfileActivity(args));
                } else if (position == addMemberRow) {
                    openAddMember();
                } else if (position == channelNameRow) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        if (info.about != null && info.about.length() > 0) {
                            intent.putExtra(Intent.EXTRA_TEXT, currentChat.title + "\n" + info.about + "\nhttps://telegram.me/" + currentChat.username);
                        } else {
                            intent.putExtra(Intent.EXTRA_TEXT, currentChat.title + "\nhttps://telegram.me/" + currentChat.username);
                        }
                        getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("BotShare", R.string.BotShare)), 500);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (position == leaveChannelRow) {
                    leaveChatPressed();
                } else if (position == membersRow || position == blockedUsersRow || position == managementRow) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    if (position == blockedUsersRow) {
                        args.putInt("type", 0);
                    } else if (position == managementRow) {
                        args.putInt("type", 1);
                    } else if (position == membersRow) {
                        args.putInt("type", 2);
                    }
                    presentFragment(new ChannelUsersActivity(args));
                } else if (position == convertRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("ConvertGroupAlert", R.string.ConvertGroupAlert));
                    builder.setTitle(LocaleController.getString("ConvertGroupAlertWarning", R.string.ConvertGroupAlertWarning));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            MessagesController.getInstance().convertToMegaGroup(getParentActivity(), chat_id);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else {
                    processOnClickOrPress(position);
                }
            }
        });

        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (position > emptyRowChat2 && position < membersEndRow) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    boolean allowKick = false;
                    boolean allowSetAdmin = false;

                    final TLRPC.ChatParticipant user;
                    if (!sortedUsers.isEmpty()) {
                        user = info.participants.participants.get(sortedUsers.get(position - emptyRowChat2 - 1));
                    } else {
                        user = info.participants.participants.get(position - emptyRowChat2 - 1);
                    }
                    selectedUser = user.user_id;

                    if (ChatObject.isChannel(currentChat)) {
                        TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) user).channelParticipant;
                        if (user.user_id != UserConfig.getClientUserId()) {
                            if (currentChat.creator) {
                                allowKick = true;
                            } else if (channelParticipant instanceof TLRPC.TL_channelParticipant) {
                                if (currentChat.editor || channelParticipant.inviter_id == UserConfig.getClientUserId()) {
                                    allowKick = true;
                                }
                            }
                        }
                        TLRPC.User u = MessagesController.getInstance().getUser(user.user_id);
                        allowSetAdmin = channelParticipant instanceof TLRPC.TL_channelParticipant && !u.bot;
                    } else {
                        if (user.user_id != UserConfig.getClientUserId()) {
                            if (currentChat.creator) {
                                allowKick = true;
                            } else if (user instanceof TLRPC.TL_chatParticipant) {
                                if (currentChat.admin && currentChat.admins_enabled || user.inviter_id == UserConfig.getClientUserId()) {
                                    allowKick = true;
                                }
                            }
                        }
                    }
                    if (!allowKick) {
                        return false;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    if (currentChat.megagroup && currentChat.creator && allowSetAdmin) {
                        CharSequence[] items = new CharSequence[]{LocaleController.getString("SetAsAdmin", R.string.SetAsAdmin), LocaleController.getString("KickFromGroup", R.string.KickFromGroup)};
                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    TLRPC.TL_chatChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) user);

                                    channelParticipant.channelParticipant = new TLRPC.TL_channelParticipantEditor();
                                    channelParticipant.channelParticipant.inviter_id = UserConfig.getClientUserId();
                                    channelParticipant.channelParticipant.user_id = user.user_id;
                                    channelParticipant.channelParticipant.date = user.date;

                                    TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
                                    req.channel = MessagesController.getInputChannel(chat_id);
                                    req.user_id = MessagesController.getInputUser(selectedUser);
                                    req.role = new TLRPC.TL_channelRoleEditor();
                                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                        @Override
                                        public void run(TLObject response, final TLRPC.TL_error error) {
                                            if (error == null) {
                                                MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                                                AndroidUtilities.runOnUIThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        MessagesController.getInstance().loadFullChat(chat_id, 0, true);
                                                    }
                                                }, 1000);
                                            } else {
                                                AndroidUtilities.runOnUIThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        AlertsCreator.showAddUserAlert(error.text, ProfileActivity.this, false);
                                                    }
                                                });
                                            }
                                        }
                                    });
                                } else if (i == 1) {
                                    kickUser(selectedUser);
                                }
                            }
                        });
                    } else {
                        CharSequence[] items = new CharSequence[]{chat_id > 0 ? LocaleController.getString("KickFromGroup", R.string.KickFromGroup) : LocaleController.getString("KickFromBroadcast", R.string.KickFromBroadcast)};
                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    kickUser(selectedUser);
                                }
                            }
                        });
                    }
                    showDialog(builder.create());
                    return true;
                } else {
                    return processOnClickOrPress(position);
                }
            }
        });

        topView = new TopView(context);
        topView.setBackgroundColor(AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id) && !currentChat.megagroup ? 5 : chat_id));
        frameLayout.addView(topView);

        frameLayout.addView(actionBar);

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        ViewProxy.setPivotX(avatarImage, 0);
        ViewProxy.setPivotY(avatarImage, 0);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.LEFT, 64, 0, 0, 0));
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

        for (int a = 0; a < 2; a++) {
            if (!playProfileAnimation && a == 0) {
                continue;
            }
            nameTextView[a] = new SimpleTextView(context);
            nameTextView[a].setTextColor(0xffffffff);
            nameTextView[a].setTextSize(18);
            nameTextView[a].setGravity(Gravity.LEFT);
            nameTextView[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView[a].setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
            nameTextView[a].setRightDrawableTopPadding(-AndroidUtilities.dp(1.3f));
            ViewProxy.setPivotX(nameTextView[a], 0);
            ViewProxy.setPivotY(nameTextView[a], 0);
            frameLayout.addView(nameTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 0, a == 0 ? 48 : 0, 0));

            onlineTextView[a] = new SimpleTextView(context);
            onlineTextView[a].setTextColor(AvatarDrawable.getProfileTextColorForId(user_id != 0 || ChatObject.isChannel(chat_id) && !currentChat.megagroup ? 5 : chat_id));
            onlineTextView[a].setTextSize(14);
            onlineTextView[a].setGravity(Gravity.LEFT);
            frameLayout.addView(onlineTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 0, a == 0 ? 48 : 8, 0));
        }

        if (user_id != 0 || chat_id >= 0 && !ChatObject.isLeftFromChat(currentChat)) {
            writeButton = new ImageView(context);
            try {
                writeButton.setBackgroundResource(R.drawable.floating_user_states);
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
            writeButton.setScaleType(ImageView.ScaleType.CENTER);
            if (user_id != 0) {
                writeButton.setImageResource(R.drawable.floating_message);
                writeButton.setPadding(0, AndroidUtilities.dp(3), 0, 0);
            } else if (chat_id != 0) {
                boolean isChannel = ChatObject.isChannel(currentChat);
                if (isChannel && !currentChat.creator && (!currentChat.megagroup || !currentChat.editor) || !isChannel && !currentChat.admin && !currentChat.creator && currentChat.admins_enabled) {
                    writeButton.setImageResource(R.drawable.floating_message);
                    writeButton.setPadding(0, AndroidUtilities.dp(3), 0, 0);
                } else {
                    writeButton.setImageResource(R.drawable.floating_camera);
                }
            }
            frameLayout.addView(writeButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 16, 0));
            if (Build.VERSION.SDK_INT >= 21) {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(writeButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(writeButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                writeButton.setStateListAnimator(animator);
                writeButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            writeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (user_id != 0) {
                        if (playProfileAnimation && parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2) instanceof ChatActivity) {
                            finishFragment();
                        } else {
                            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                            if (user == null || user instanceof TLRPC.TL_userEmpty) {
                                return;
                            }
                            Bundle args = new Bundle();
                            args.putInt("user_id", user_id);
                            if (!MessagesController.checkCanOpenChat(args, ProfileActivity.this)) {
                                return;
                            }
                            NotificationCenter.getInstance().removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                            presentFragment(new ChatActivity(args), true);
                        }
                    } else if (chat_id != 0) {
                        boolean isChannel = ChatObject.isChannel(currentChat);
                        if (isChannel && !currentChat.creator && (!currentChat.megagroup || !currentChat.editor) || !isChannel && !currentChat.admin && !currentChat.creator && currentChat.admins_enabled) {
                            if (playProfileAnimation && parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2) instanceof ChatActivity) {
                                finishFragment();
                            } else {
                                Bundle args = new Bundle();
                                args.putInt("chat_id", currentChat.id);
                                if (!MessagesController.checkCanOpenChat(args, ProfileActivity.this)) {
                                    return;
                                }
                                NotificationCenter.getInstance().removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                presentFragment(new ChatActivity(args), true);
                            }
                        } else {
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
                                    if (i == 0) {
                                        avatarUpdater.openCamera();
                                    } else if (i == 1) {
                                        avatarUpdater.openGallery();
                                    } else if (i == 2) {
                                        MessagesController.getInstance().changeChatAvatar(chat_id, null);
                                    }
                                }
                            });
                            showDialog(builder.create());
                        }
                    }
                }
            });
        }
        needLayout();

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkListViewScroll();
                if (participantsMap != null && loadMoreMembersRow != -1 && layoutManager.findLastVisibleItemPosition() > loadMoreMembersRow - 8) {
                    getChannelParticipants(false);
                }
            }
        });

        return fragmentView;
    }

    private boolean processOnClickOrPress(final int position) {
        if (position == usernameRow) {
            final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            if (user == null || user.username == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (i == 0) {
                        try {
                            if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setText("@" + user.username);
                            } else {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", "@" + user.username);
                                clipboard.setPrimaryClip(clip);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            });
            showDialog(builder.create());
            return true;
        } else if (position == phoneRow) {
            final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            if (user == null || user.phone == null || user.phone.length() == 0 || getParentActivity() == null) {
                return false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(new CharSequence[]{LocaleController.getString("Call", R.string.Call), LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (i == 0) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + user.phone));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            getParentActivity().startActivityForResult(intent, 500);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (i == 1) {
                        try {
                            if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setText("+" + user.phone);
                            } else {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", "+" + user.phone);
                                clipboard.setPrimaryClip(clip);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            });
            showDialog(builder.create());
            return true;
        } else if (position == channelInfoRow || position == userInfoRow) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        String about;
                        if (position == channelInfoRow) {
                            about = info.about;
                        } else {
                            about = MessagesController.getInstance().getUserAbout(botInfo.user_id);
                        }
                        if (about == null) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setText(about);
                        } else {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", about);
                            clipboard.setPrimaryClip(clip);
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            });
            showDialog(builder.create());
            return true;
        }
        return false;
    }

    private void leaveChatPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        if (ChatObject.isChannel(chat_id) && !currentChat.megagroup) {
            builder.setMessage(ChatObject.isChannel(chat_id) ? LocaleController.getString("ChannelLeaveAlert", R.string.ChannelLeaveAlert) : LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
        } else {
            builder.setMessage(LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
        }
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                kickUser(0);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
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
            MessagesController.getInstance().loadChatInfo(chat_id, null, false);
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

    private void getChannelParticipants(boolean reload) {
        if (loadingUsers || participantsMap == null || info == null) {
            return;
        }
        loadingUsers = true;
        final int delay = Build.VERSION.SDK_INT >= 11 && !participantsMap.isEmpty() && reload ? 300 : 0;

        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInputChannel(chat_id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = reload ? 0 : participantsMap.size();
        req.limit = 200;
        int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                            MessagesController.getInstance().putUsers(res.users, false);
                            if (res.users.size() != 200) {
                                usersEndReached = true;
                            }
                            if (req.offset == 0) {
                                participantsMap.clear();
                                info.participants = new TLRPC.TL_chatParticipants();
                                MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);
                                MessagesStorage.getInstance().updateChannelUsers(chat_id, res.participants);
                            }
                            for (int a = 0; a < res.participants.size(); a++) {
                                TLRPC.TL_chatChannelParticipant participant = new TLRPC.TL_chatChannelParticipant();
                                participant.channelParticipant = res.participants.get(a);
                                participant.inviter_id = participant.channelParticipant.inviter_id;
                                participant.user_id = participant.channelParticipant.user_id;
                                participant.date = participant.channelParticipant.date;
                                if (!participantsMap.containsKey(participant.user_id)) {
                                    info.participants.participants.add(participant);
                                    participantsMap.put(participant.user_id, participant);
                                }
                            }
                        }
                        updateOnlineCount();
                        loadingUsers = false;
                        updateRowsIds();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    }
                }, delay);
            }
        });
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
    }

    private void openAddMember() {
        Bundle args = new Bundle();
        args.putBoolean("onlyUsers", true);
        args.putBoolean("destroyAfterSelect", true);
        args.putBoolean("returnAsResult", true);
        args.putBoolean("needForwardCount", !ChatObject.isChannel(currentChat));
        //args.putBoolean("allowUsernameSearch", false);
        if (chat_id > 0) {
            if (currentChat.creator) {
                args.putInt("chat_id", currentChat.id);
            }
            args.putString("selectAlertString", LocaleController.getString("AddToTheGroup", R.string.AddToTheGroup));
        }
        ContactsActivity fragment = new ContactsActivity(args);
        fragment.setDelegate(new ContactsActivity.ContactsActivityDelegate() {
            @Override
            public void didSelectContact(TLRPC.User user, String param) {
                MessagesController.getInstance().addUserToChat(chat_id, user, info, param != null ? Utilities.parseInt(param) : 0, null, ProfileActivity.this);
            }
        });
        if (info != null && info.participants != null) {
            HashMap<Integer, TLRPC.User> users = new HashMap<>();
            for (int a = 0; a < info.participants.participants.size(); a++) {
                users.put(info.participants.participants.get(a).user_id, null);
            }
            fragment.setIgnoreUsers(users);
        }
        presentFragment(fragment);
    }

    private void checkListViewScroll() {
        if (listView.getChildCount() <= 0 || openAnimationInProgress) {
            return;
        }

        View child = listView.getChildAt(0);
        ListAdapter.Holder holder = (ListAdapter.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        if (extraHeight != newOffset) {
            extraHeight = newOffset;
            topView.invalidate();
            needLayout();
        }
    }

    private void needLayout() {
        FrameLayout.LayoutParams layoutParams;
        int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        if (listView != null && !openAnimationInProgress) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                listView.setLayoutParams(layoutParams);
            }
        }

        if (avatarImage != null) {
            float diff = extraHeight / (float) AndroidUtilities.dp(88);
            listView.setTopGlowOffset(extraHeight);

            if (writeButton != null) {
                if (Build.VERSION.SDK_INT < 11) {
                    layoutParams = (FrameLayout.LayoutParams) writeButton.getLayoutParams();
                    layoutParams.topMargin = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + extraHeight - AndroidUtilities.dp(29.5f);
                    writeButton.setLayoutParams(layoutParams);
                } else {
                    ViewProxy.setTranslationY(writeButton, (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + extraHeight - AndroidUtilities.dp(29.5f));
                }

                if (!openAnimationInProgress) {
                    final boolean setVisible = diff > 0.2f;
                    boolean currentVisible = writeButton.getTag() == null;
                    if (setVisible != currentVisible) {
                        if (setVisible) {
                            writeButton.setTag(null);
                            if (Build.VERSION.SDK_INT < 11) {
                                writeButton.setVisibility(View.VISIBLE);
                            }
                        } else {
                            writeButton.setTag(0);
                        }
                        if (writeButtonAnimation != null) {
                            AnimatorSetProxy old = writeButtonAnimation;
                            writeButtonAnimation = null;
                            old.cancel();
                        }
                        writeButtonAnimation = new AnimatorSetProxy();
                        if (setVisible) {
                            writeButtonAnimation.setInterpolator(new DecelerateInterpolator());
                            writeButtonAnimation.playTogether(
                                    ObjectAnimatorProxy.ofFloat(writeButton, "scaleX", 1.0f),
                                    ObjectAnimatorProxy.ofFloat(writeButton, "scaleY", 1.0f),
                                    ObjectAnimatorProxy.ofFloat(writeButton, "alpha", 1.0f)
                            );
                        } else {
                            writeButtonAnimation.setInterpolator(new AccelerateInterpolator());
                            writeButtonAnimation.playTogether(
                                    ObjectAnimatorProxy.ofFloat(writeButton, "scaleX", 0.2f),
                                    ObjectAnimatorProxy.ofFloat(writeButton, "scaleY", 0.2f),
                                    ObjectAnimatorProxy.ofFloat(writeButton, "alpha", 0.0f)
                            );
                        }
                        writeButtonAnimation.setDuration(150);
                        writeButtonAnimation.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (writeButtonAnimation != null && writeButtonAnimation.equals(animation)) {
                                    writeButton.clearAnimation();
                                    if (Build.VERSION.SDK_INT < 11) {
                                        writeButton.setVisibility(setVisible ? View.VISIBLE : View.GONE);
                                    }
                                    writeButtonAnimation = null;
                                }
                            }
                        });
                        writeButtonAnimation.start();
                    }
                }
            }

            float avatarY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f * (1.0f + diff) - 21 * AndroidUtilities.density + 27 * AndroidUtilities.density * diff;
            if (Build.VERSION.SDK_INT < 11) {
                layoutParams = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
                layoutParams.height = layoutParams.width = (int) Math.ceil(AndroidUtilities.dp(42) * (42 + 18 * diff) / 42.0f);
                layoutParams.leftMargin = (int) Math.ceil(AndroidUtilities.dp(64) - AndroidUtilities.dp(47) * diff);
                layoutParams.topMargin = (int) Math.ceil(avatarY);
                avatarImage.setLayoutParams(layoutParams);
                avatarImage.setRoundRadius(layoutParams.height / 2);
            } else {
                ViewProxy.setScaleX(avatarImage, (42 + 18 * diff) / 42.0f);
                ViewProxy.setScaleY(avatarImage, (42 + 18 * diff) / 42.0f);
                ViewProxy.setTranslationX(avatarImage, -AndroidUtilities.dp(47) * diff);
                ViewProxy.setTranslationY(avatarImage, (float) Math.ceil(avatarY));
            }
            for (int a = 0; a < 2; a++) {
                if (nameTextView[a] == null) {
                    continue;
                }
                ViewProxy.setTranslationX(nameTextView[a], -21 * AndroidUtilities.density * diff);
                ViewProxy.setTranslationY(nameTextView[a], (float) Math.floor(avatarY) + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7) * diff);
                ViewProxy.setTranslationX(onlineTextView[a], -21 * AndroidUtilities.density * diff);
                ViewProxy.setTranslationY(onlineTextView[a], (float) Math.floor(avatarY) + AndroidUtilities.dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff);
                ViewProxy.setScaleX(nameTextView[a], 1.0f + 0.12f * diff);
                ViewProxy.setScaleY(nameTextView[a], 1.0f + 0.12f * diff);
                if (a == 1 && !openAnimationInProgress) {
                    int width;
                    if (AndroidUtilities.isTablet()) {
                        width = AndroidUtilities.dp(490);
                    } else {
                        width = AndroidUtilities.displaySize.x;
                    }
                    width = (int) (width - AndroidUtilities.dp(118 + 8 + 40 * (1.0f - diff)) - ViewProxy.getTranslationX(nameTextView[a]));
                    float width2 = nameTextView[a].getPaint().measureText(nameTextView[a].getText().toString()) * ViewProxy.getScaleX(nameTextView[a]) + nameTextView[a].getSideDrawablesSize();
                    layoutParams = (FrameLayout.LayoutParams) nameTextView[a].getLayoutParams();
                    if (width < width2) {
                        layoutParams.width = (int) Math.ceil(width / ViewProxy.getScaleX(nameTextView[a]));
                    } else {
                        layoutParams.width = LayoutHelper.WRAP_CONTENT;
                    }
                    nameTextView[a].setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) onlineTextView[a].getLayoutParams();
                    layoutParams.rightMargin = (int) Math.ceil(ViewProxy.getTranslationX(onlineTextView[a]) + AndroidUtilities.dp(8) + AndroidUtilities.dp(40) * (1.0f - diff));
                    onlineTextView[a].setLayoutParams(layoutParams);
                }
            }
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
                    checkListViewScroll();
                    needLayout();
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (user_id != 0) {
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateProfileData();
                }
                if ((mask & MessagesController.UPDATE_MASK_PHONE) != 0) {
                    if (listView != null) {
                        ListAdapter.Holder holder = (ListAdapter.Holder) listView.findViewHolderForPosition(phoneRow);
                        if (holder != null) {
                            listAdapter.onBindViewHolder(holder, phoneRow);
                        }
                    }
                }
            } else if (chat_id != 0) {
                if ((mask & MessagesController.UPDATE_MASK_CHAT_ADMINS) != 0) {
                    TLRPC.Chat newChat = MessagesController.getInstance().getChat(chat_id);
                    if (newChat != null) {
                        currentChat = newChat;
                        createActionBarMenu();
                        updateRowsIds();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    }
                }
                if ((mask & MessagesController.UPDATE_MASK_CHANNEL) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateOnlineCount();
                    updateProfileData();
                }
                if ((mask & MessagesController.UPDATE_MASK_CHANNEL) != 0) {
                    updateRowsIds();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                }
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    if (listView != null) {
                        int count = listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = listView.getChildAt(a);
                            if (child instanceof UserCell) {
                                ((UserCell) child).update(mask);
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.contactsDidLoaded) {
            createActionBarMenu();
        } else if (id == NotificationCenter.mediaCountDidLoaded) {
            long uid = (Long) args[0];
            long did = dialog_id;
            if (did == 0) {
                if (user_id != 0) {
                    did = user_id;
                } else if (chat_id != 0) {
                    did = -chat_id;
                }
            }
            if (uid == did || uid == mergeDialogId) {
                if (uid == did) {
                    totalMediaCount = (Integer) args[1];
                } else {
                    totalMediaCountMerge = (Integer) args[1];
                }
                if (listView != null) {
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        ListAdapter.Holder holder = (ListAdapter.Holder) listView.getChildViewHolder(child);
                        if (holder.getAdapterPosition() == sharedMediaRow) {
                            listAdapter.onBindViewHolder(holder, sharedMediaRow);
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.encryptedChatCreated) {
            if (creatingChat) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                        TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) args[0];
                        Bundle args2 = new Bundle();
                        args2.putInt("enc_id", encryptedChat.id);
                        presentFragment(new ChatActivity(args2), true);
                    }
                });
            }
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) args[0];
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
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chat_id) {
                boolean byChannelUsers = (Boolean) args[2];
                if (info instanceof TLRPC.TL_channelFull) {
                    if (chatFull.participants == null && info != null) {
                        chatFull.participants = info.participants;
                    }
                }
                boolean loadChannelParticipants = info == null && chatFull instanceof TLRPC.TL_channelFull;
                info = chatFull;
                if (mergeDialogId == 0 && info.migrated_from_chat_id != 0) {
                    mergeDialogId = -info.migrated_from_chat_id;
                    SharedMediaQuery.getMediaCount(mergeDialogId, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
                }
                fetchUsersFromChannelInfo();
                updateOnlineCount();
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                    checkListViewScroll();
                }
                TLRPC.Chat newChat = MessagesController.getInstance().getChat(chat_id);
                if (newChat != null) {
                    currentChat = newChat;
                    createActionBarMenu();
                }
                if (currentChat.megagroup && (loadChannelParticipants || !byChannelUsers)) {
                    getChannelParticipants(true);
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.botInfoDidLoaded) {
            TLRPC.BotInfo info = (TLRPC.BotInfo) args[0];
            if (info.user_id == user_id) {
                botInfo = info;
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                    checkListViewScroll();
                }
            }
        } else if (id == NotificationCenter.userInfoDidLoaded) {
            int uid = (Integer) args[0];
            if (uid == user_id) {
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject obj = arr.get(a);
                    if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction && obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL) obj.messageOwner.action.encryptedAction;
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                            checkListViewScroll();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateProfileData();
        fixLayout();
    }

    public void setPlayProfileAnimation(boolean value) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (!AndroidUtilities.isTablet() && Build.VERSION.SDK_INT > 10 && preferences.getBoolean("view_animations", true)) {
            playProfileAnimation = value;
        }
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (!backward && playProfileAnimation) {
            openAnimationInProgress = true;
        }
        NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoaded});
        NotificationCenter.getInstance().setAnimationInProgress(true);
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (!backward && playProfileAnimation) {
            openAnimationInProgress = false;
        }
        NotificationCenter.getInstance().setAnimationInProgress(false);
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public void setAnimationProgress(float progress) {
        animationProgress = progress;
        ViewProxy.setAlpha(listView, progress);

        //ViewProxy.setTranslationY(listView, -AndroidUtilities.dp(48) + AndroidUtilities.dp(48) * progress);
        int color = AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id) && !currentChat.megagroup ? 5 : chat_id);

        int r = Color.red(Theme.ACTION_BAR_COLOR);
        int g = Color.green(Theme.ACTION_BAR_COLOR);
        int b = Color.blue(Theme.ACTION_BAR_COLOR);

        int rD = (int) ((Color.red(color) - r) * progress);
        int gD = (int) ((Color.green(color) - g) * progress);
        int bD = (int) ((Color.blue(color) - b) * progress);
        topView.setBackgroundColor(Color.rgb(r + rD, g + gD, b + bD));
        color = AvatarDrawable.getProfileTextColorForId(user_id != 0 || ChatObject.isChannel(chat_id) && !currentChat.megagroup ? 5 : chat_id);

        r = Color.red(Theme.ACTION_BAR_SUBTITLE_COLOR);
        g = Color.green(Theme.ACTION_BAR_SUBTITLE_COLOR);
        b = Color.blue(Theme.ACTION_BAR_SUBTITLE_COLOR);

        rD = (int) ((Color.red(color) - r) * progress);
        gD = (int) ((Color.green(color) - g) * progress);
        bD = (int) ((Color.blue(color) - b) * progress);
        for (int a = 0; a < 2; a++) {
            if (onlineTextView[a] == null) {
                continue;
            }
            onlineTextView[a].setTextColor(Color.rgb(r + rD, g + gD, b + bD));
        }
        extraHeight = (int) (initialAnimationExtraHeight * progress);
        color = AvatarDrawable.getProfileColorForId(user_id != 0 ? user_id : chat_id);
        int color2 = AvatarDrawable.getColorForId(user_id != 0 ? user_id : chat_id);
        if (color != color2) {
            rD = (int) ((Color.red(color) - Color.red(color2)) * progress);
            gD = (int) ((Color.green(color) - Color.green(color2)) * progress);
            bD = (int) ((Color.blue(color) - Color.blue(color2)) * progress);
            avatarDrawable.setColor(Color.rgb(Color.red(color2) + rD, Color.green(color2) + gD, Color.blue(color2) + bD));
            avatarImage.invalidate();
        }

        needLayout();
    }

    @Override
    protected AnimatorSetProxy onCustomTransitionAnimation(final boolean isOpen, final Runnable callback) {
        if (playProfileAnimation) {
            final AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            animatorSet.setDuration(180);
            if (Build.VERSION.SDK_INT > 15) {
                listView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            ActionBarMenu menu = actionBar.createMenu();
            if (menu.getItem(10) == null) {
                if (animatingItem == null) {
                    animatingItem = menu.addItem(10, R.drawable.ic_ab_other);
                }
            }
            if (isOpen) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) onlineTextView[1].getLayoutParams();
                layoutParams.rightMargin = (int) (-21 * AndroidUtilities.density + AndroidUtilities.dp(8));
                onlineTextView[1].setLayoutParams(layoutParams);

                int width = (int) Math.ceil(AndroidUtilities.displaySize.x - AndroidUtilities.dp(118 + 8) + 21 * AndroidUtilities.density);
                float width2 = nameTextView[1].getPaint().measureText(nameTextView[1].getText().toString()) * 1.12f + nameTextView[1].getSideDrawablesSize();
                layoutParams = (FrameLayout.LayoutParams) nameTextView[1].getLayoutParams();
                if (width < width2) {
                    layoutParams.width = (int) Math.ceil(width / 1.12f);
                } else {
                    layoutParams.width = LayoutHelper.WRAP_CONTENT;
                }
                nameTextView[1].setLayoutParams(layoutParams);

                initialAnimationExtraHeight = AndroidUtilities.dp(88);
                fragmentView.setBackgroundColor(0);
                setAnimationProgress(0);
                ArrayList<Object> animators = new ArrayList<>();
                animators.add(ObjectAnimatorProxy.ofFloat(this, "animationProgress", 0.0f, 1.0f));
                if (writeButton != null) {
                    ViewProxy.setScaleX(writeButton, 0.2f);
                    ViewProxy.setScaleY(writeButton, 0.2f);
                    ViewProxy.setAlpha(writeButton, 0.0f);
                    animators.add(ObjectAnimatorProxy.ofFloat(writeButton, "scaleX", 1.0f));
                    animators.add(ObjectAnimatorProxy.ofFloat(writeButton, "scaleY", 1.0f));
                    animators.add(ObjectAnimatorProxy.ofFloat(writeButton, "alpha", 1.0f));
                }
                for (int a = 0; a < 2; a++) {
                    ViewProxy.setAlpha(onlineTextView[a], a == 0 ? 1.0f : 0.0f);
                    ViewProxy.setAlpha(nameTextView[a], a == 0 ? 1.0f : 0.0f);
                    animators.add(ObjectAnimatorProxy.ofFloat(onlineTextView[a], "alpha", a == 0 ? 0.0f : 1.0f));
                    animators.add(ObjectAnimatorProxy.ofFloat(nameTextView[a], "alpha", a == 0 ? 0.0f : 1.0f));
                }
                if (animatingItem != null) {
                    ViewProxy.setAlpha(animatingItem, 1.0f);
                    animators.add(ObjectAnimatorProxy.ofFloat(animatingItem, "alpha", 0.0f));
                }
                animatorSet.playTogether(animators);
            } else {
                initialAnimationExtraHeight = extraHeight;
                ArrayList<Object> animators = new ArrayList<>();
                animators.add(ObjectAnimatorProxy.ofFloat(this, "animationProgress", 1.0f, 0.0f));
                if (writeButton != null) {
                    animators.add(ObjectAnimatorProxy.ofFloat(writeButton, "scaleX", 0.2f));
                    animators.add(ObjectAnimatorProxy.ofFloat(writeButton, "scaleY", 0.2f));
                    animators.add(ObjectAnimatorProxy.ofFloat(writeButton, "alpha", 0.0f));
                }
                for (int a = 0; a < 2; a++) {
                    animators.add(ObjectAnimatorProxy.ofFloat(onlineTextView[a], "alpha", a == 0 ? 1.0f : 0.0f));
                    animators.add(ObjectAnimatorProxy.ofFloat(nameTextView[a], "alpha", a == 0 ? 1.0f : 0.0f));
                }
                if (animatingItem != null) {
                    ViewProxy.setAlpha(animatingItem, 0.0f);
                    animators.add(ObjectAnimatorProxy.ofFloat(animatingItem, "alpha", 1.0f));
                }
                animatorSet.playTogether(animators);
            }
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    if (Build.VERSION.SDK_INT > 15) {
                        listView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    if (animatingItem != null) {
                        ActionBarMenu menu = actionBar.createMenu();
                        menu.clearItems();
                        animatingItem = null;
                    }
                    callback.run();
                }
            });
            animatorSet.setInterpolator(new DecelerateInterpolator());

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    animatorSet.start();
                }
            }, 50);
            return animatorSet;
        }
        return null;
    }

    @Override
    public void updatePhotoAtIndex(int index) {

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
            object.imageReceiver = avatarImage.getImageReceiver();
            object.user_id = user_id;
            object.thumb = object.imageReceiver.getBitmap();
            object.size = -1;
            object.radius = avatarImage.getImageReceiver().getRoundRadius();
            object.scale = ViewProxy.getScaleX(avatarImage);
            return object;
        }
        return null;
    }

    @Override
    public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() {
        avatarImage.getImageReceiver().setVisible(true, true);
    }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public boolean cancelButtonPressed() { return true; }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void updateOnlineCount() {
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        sortedUsers.clear();
        if (info instanceof TLRPC.TL_chatFull || info instanceof TLRPC.TL_channelFull && info.participants_count <= 200 && info.participants != null) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId()) && user.status.expires > 10000) {
                    onlineCount++;
                }
                sortedUsers.add(a);
            }

            try {
                Collections.sort(sortedUsers, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer lhs, Integer rhs) {
                        TLRPC.User user1 = MessagesController.getInstance().getUser(info.participants.participants.get(rhs).user_id);
                        TLRPC.User user2 = MessagesController.getInstance().getUser(info.participants.participants.get(lhs).user_id);
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
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            if (listAdapter != null) {
                listAdapter.notifyItemRangeChanged(emptyRowChat2 + 1, sortedUsers.size());
            }
        }
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (info != null && info.migrated_from_chat_id != 0) {
            mergeDialogId = -info.migrated_from_chat_id;
        }
        fetchUsersFromChannelInfo();
    }

    private void fetchUsersFromChannelInfo() {
        if (info instanceof TLRPC.TL_channelFull && info.participants != null) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant chatParticipant = info.participants.participants.get(a);
                participantsMap.put(chatParticipant.user_id, chatParticipant);
            }
        }
    }

    private void kickUser(int uid) {
        if (uid != 0) {
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().getUser(uid), info);
            if (currentChat.megagroup && info != null && info.participants != null) {
                boolean changed = false;
                for (int a = 0; a < info.participants.participants.size(); a++) {
                    TLRPC.ChannelParticipant p = ((TLRPC.TL_chatChannelParticipant) info.participants.participants.get(a)).channelParticipant;
                    if (p.user_id == uid) {
                        if (info != null) {
                            info.participants_count--;
                        }
                        info.participants.participants.remove(a);
                        changed = true;
                        break;
                    }
                }
                if (info != null && info.participants != null) {
                    for (int a = 0; a < info.participants.participants.size(); a++) {
                        TLRPC.ChatParticipant p = info.participants.participants.get(a);
                        if (p.user_id == uid) {
                            info.participants.participants.remove(a);
                            changed = true;
                            break;
                        }
                    }
                }
                if (changed) {
                    updateOnlineCount();
                    updateRowsIds();
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
            if (AndroidUtilities.isTablet()) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats, -(long) chat_id);
            } else {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            }
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), info);
            playProfileAnimation = false;
            finishFragment();
        }
    }

    public boolean isChat() {
        return chat_id != 0;
    }

    private void updateRowsIds() {
        emptyRow = -1;
        phoneRow = -1;
        userInfoRow = -1;
        userSectionRow = -1;
        sectionRow = -1;
        sharedMediaRow = -1;
        settingsNotificationsRow = -1;
        usernameRow = -1;
        settingsTimerRow = -1;
        settingsKeyRow = -1;
        startSecretChatRow = -1;
        membersEndRow = -1;
        emptyRowChat2 = -1;
        addMemberRow = -1;
        channelInfoRow = -1;
        channelNameRow = -1;
        convertRow = -1;
        convertHelpRow = -1;
        emptyRowChat = -1;
        membersSectionRow = -1;
        membersRow = -1;
        managementRow = -1;
        leaveChannelRow = -1;
        loadMoreMembersRow = -1;
        blockedUsersRow = -1;

        rowCount = 0;
        if (user_id != 0) {
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            emptyRow = rowCount++;
            if (user == null || !user.bot) {
                phoneRow = rowCount++;
            }
            if (user != null && user.username != null && user.username.length() > 0) {
                usernameRow = rowCount++;
            }
            String about = MessagesController.getInstance().getUserAbout(user.id);
            if (about != null) {
                userSectionRow = rowCount++;
                userInfoRow = rowCount++;
            } else {
                userSectionRow = -1;
                userInfoRow = -1;
            }
            sectionRow = rowCount++;
            settingsNotificationsRow = rowCount++;
            sharedMediaRow = rowCount++;
            if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                settingsTimerRow = rowCount++;
                settingsKeyRow = rowCount++;
            }
            if (user != null && !user.bot && currentEncryptedChat == null && user.id != UserConfig.getClientUserId()) {
                startSecretChatRow = rowCount++;
            }
        } else if (chat_id != 0) {
            if (chat_id > 0) {
                emptyRow = rowCount++;
                if (ChatObject.isChannel(currentChat) && (info != null && info.about != null && info.about.length() > 0 || currentChat.username != null && currentChat.username.length() > 0)) {
                    if (info != null && info.about != null && info.about.length() > 0) {
                        channelInfoRow = rowCount++;
                    }
                    if (currentChat.username != null && currentChat.username.length() > 0) {
                        channelNameRow = rowCount++;
                    }
                    sectionRow = rowCount++;
                }
                settingsNotificationsRow = rowCount++;
                sharedMediaRow = rowCount++;
                if (ChatObject.isChannel(currentChat)) {
                    if (!currentChat.megagroup && info != null && (currentChat.creator || info.can_view_participants)) {
                        membersRow = rowCount++;
                    }
                    if (!ChatObject.isNotInChat(currentChat) && !currentChat.megagroup && (currentChat.creator || currentChat.editor || currentChat.moderator)) {
                        managementRow = rowCount++;
                    }
                    if (!ChatObject.isNotInChat(currentChat) && currentChat.megagroup && (currentChat.editor || currentChat.creator)) {
                        blockedUsersRow = rowCount++;
                    }
                    if (!currentChat.creator && !currentChat.left && !currentChat.kicked && !currentChat.megagroup) {
                        leaveChannelRow = rowCount++;
                    }
                    if (currentChat.megagroup && (currentChat.editor || currentChat.creator || currentChat.democracy)) {
                        if (info == null || info.participants_count < MessagesController.getInstance().maxMegagroupCount) {
                            addMemberRow = rowCount++;
                        }
                    }
                    if (info != null && info.participants != null && !info.participants.participants.isEmpty()) {
                        emptyRowChat = rowCount++;
                        membersSectionRow = rowCount++;
                        emptyRowChat2 = rowCount++;
                        rowCount += info.participants.participants.size();
                        membersEndRow = rowCount;
                        if (!usersEndReached) {
                            loadMoreMembersRow = rowCount++;
                        }
                    }
                } else {
                    if (info != null) {
                        if (!(info.participants instanceof TLRPC.TL_chatParticipantsForbidden) &&
                                info.participants.participants.size() < MessagesController.getInstance().maxGroupCount &&
                                (currentChat.admin || currentChat.creator || !currentChat.admins_enabled)) {
                            addMemberRow = rowCount++;
                        }

                        if (currentChat.creator && info.participants.participants.size() >= MessagesController.getInstance().minGroupConvertSize) {
                            convertRow = rowCount++;
                        }
                    }
                    emptyRowChat = rowCount++;
                    if (convertRow != -1) {
                        convertHelpRow = rowCount++;
                    } else {
                        membersSectionRow = rowCount++;
                    }
                    if (info != null && !(info.participants instanceof TLRPC.TL_chatParticipantsForbidden)) {
                        emptyRowChat2 = rowCount++;
                        rowCount += info.participants.participants.size();
                        membersEndRow = rowCount;
                    }
                }
            } else {
                if (!ChatObject.isChannel(currentChat) && info != null && !(info.participants instanceof TLRPC.TL_chatParticipantsForbidden)) {
                    addMemberRow = rowCount++;
                    emptyRowChat2 = rowCount++;
                    rowCount += info.participants.participants.size();
                    membersEndRow = rowCount;
                }
            }
        }
    }

    private void updateProfileData() {
        if (avatarImage == null || nameTextView == null) {
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
            avatarDrawable.setInfo(user);
            avatarImage.setImage(photo, "50_50", avatarDrawable);

            String newString = UserObject.getUserName(user);
            String newString2;
            if (user.id == 333000 || user.id == 777000) {
                newString2 = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
            } else if (user.bot) {
                newString2 = LocaleController.getString("Bot", R.string.Bot);
            } else {
                newString2 = LocaleController.formatUserStatus(user);
            }
            for (int a = 0; a < 2; a++) {
                if (nameTextView[a] == null) {
                    continue;
                }
                if (a == 0 && user.phone != null && user.phone.length() != 0 && user.id / 1000 != 777 && user.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(user.id) == null &&
                        (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
                    String phoneString = PhoneFormat.getInstance().format("+" + user.phone);
                    if (!nameTextView[a].getText().equals(phoneString)) {
                        nameTextView[a].setText(phoneString);
                    }
                } else {
                    if (!nameTextView[a].getText().equals(newString)) {
                        nameTextView[a].setText(newString);
                    }
                }
                if (!onlineTextView[a].getText().equals(newString2)) {
                    onlineTextView[a].setText(newString2);
                }
                int leftIcon = currentEncryptedChat != null ? R.drawable.ic_lock_header : 0;
                int rightIcon = 0;
                if (a == 0) {
                    rightIcon = MessagesController.getInstance().isDialogMuted(dialog_id != 0 ? dialog_id : (long) user_id) ? R.drawable.mute_fixed : 0;
                } else if (user.verified) {
                    rightIcon = R.drawable.check_profile_fixed;
                }
                nameTextView[a].setLeftDrawable(leftIcon);
                nameTextView[a].setRightDrawable(rightIcon);
            }

            avatarImage.getImageReceiver().setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
        } else if (chat_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
            if (chat != null) {
                currentChat = chat;
            } else {
                chat = currentChat;
            }

            String newString;
            if (ChatObject.isChannel(chat)) {
                if (info == null || !currentChat.megagroup && (info.participants_count == 0 || (currentChat.admin || info.can_view_participants))) {
                    if (currentChat.megagroup) {
                        newString = LocaleController.getString("Loading", R.string.Loading).toLowerCase();
                    } else {
                        if ((chat.flags & TLRPC.CHAT_FLAG_IS_PUBLIC) != 0) {
                            newString = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
                        } else {
                            newString = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
                        }
                    }
                } else {
                    if (currentChat.megagroup && info.participants_count <= 200) {
                        if (onlineCount > 1 && info.participants_count != 0) {
                            newString = String.format("%s, %s", LocaleController.formatPluralString("Members", info.participants_count), LocaleController.formatPluralString("Online", onlineCount));
                        } else {
                            newString = LocaleController.formatPluralString("Members", info.participants_count);
                        }
                    } else {
                        int result[] = new int[1];
                        String shortNumber = LocaleController.formatShortNumber(info.participants_count, result);
                        newString = LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber);
                    }
                }
            } else {
                int count = chat.participants_count;
                if (info != null) {
                    count = info.participants.participants.size();
                }
                if (count != 0 && onlineCount > 1) {
                    newString = String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("Online", onlineCount));
                } else {
                    newString = LocaleController.formatPluralString("Members", count);
                }
            }

            for (int a = 0; a < 2; a++) {
                if (nameTextView[a] == null) {
                    continue;
                }
                if (chat.title != null && !nameTextView[a].getText().equals(chat.title)) {
                    nameTextView[a].setText(chat.title);
                }
                nameTextView[a].setLeftDrawable(null);
                if (a != 0) {
                    if (chat.verified) {
                        nameTextView[a].setRightDrawable(R.drawable.check_profile_fixed);
                    } else {
                        nameTextView[a].setRightDrawable(null);
                    }
                } else {
                    nameTextView[a].setRightDrawable(MessagesController.getInstance().isDialogMuted((long) -chat_id) ? R.drawable.mute_fixed : 0);
                }
                if (currentChat.megagroup && info != null && info.participants_count <= 200 && onlineCount > 0) {
                    if (!onlineTextView[a].getText().equals(newString)) {
                        onlineTextView[a].setText(newString);
                    }
                } else if (a == 0 && ChatObject.isChannel(currentChat) && info != null && info.participants_count != 0 && (currentChat.megagroup || currentChat.broadcast)) {
                    int result[] = new int[1];
                    String shortNumber = LocaleController.formatShortNumber(info.participants_count, result);
                    onlineTextView[a].setText(LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber));
                } else {
                    if (!onlineTextView[a].getText().equals(newString)) {
                        onlineTextView[a].setText(newString);
                    }
                }
            }

            TLRPC.FileLocation photo = null;
            TLRPC.FileLocation photoBig = null;
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
                photoBig = chat.photo.photo_big;
            }
            avatarDrawable.setInfo(chat);
            avatarImage.setImage(photo, "50_50", avatarDrawable);
            avatarImage.getImageReceiver().setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
        }
    }

    private void createActionBarMenu() {
        ActionBarMenu menu = actionBar.createMenu();
        menu.clearItems();
        animatingItem = null;

        if (user_id != 0) {
            if (ContactsController.getInstance().contactsDict.get(user_id) == null) {
                TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                if (user == null) {
                    return;
                }
                ActionBarMenuItem item = menu.addItem(10, R.drawable.ic_ab_other);
                if (user.bot) {
                    if (!user.bot_nochats) {
                        item.addSubItem(invite_to_group, LocaleController.getString("BotInvite", R.string.BotInvite), 0);
                    }
                    item.addSubItem(share, LocaleController.getString("BotShare", R.string.BotShare), 0);
                }
                if (user.phone != null && user.phone.length() != 0) {
                    item.addSubItem(add_contact, LocaleController.getString("AddContact", R.string.AddContact), 0);
                    item.addSubItem(share_contact, LocaleController.getString("ShareContact", R.string.ShareContact), 0);
                    item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock), 0);
                } else {
                    if (user.bot) {
                        item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BotStop", R.string.BotStop) : LocaleController.getString("BotRestart", R.string.BotRestart), 0);
                    } else {
                        item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock), 0);
                    }
                }
            } else {
                ActionBarMenuItem item = menu.addItem(10, R.drawable.ic_ab_other);
                item.addSubItem(share_contact, LocaleController.getString("ShareContact", R.string.ShareContact), 0);
                item.addSubItem(block_contact, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock), 0);
                item.addSubItem(edit_contact, LocaleController.getString("EditContact", R.string.EditContact), 0);
                item.addSubItem(delete_contact, LocaleController.getString("DeleteContact", R.string.DeleteContact), 0);
            }
        } else if (chat_id != 0) {
            if (chat_id > 0) {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(chat_id);
                if (writeButton != null) {
                    boolean isChannel = ChatObject.isChannel(currentChat);
                    if (isChannel && !currentChat.creator && (!currentChat.megagroup || !currentChat.editor) || !isChannel && !currentChat.admin && !currentChat.creator && currentChat.admins_enabled) {
                        writeButton.setImageResource(R.drawable.floating_message);
                        writeButton.setPadding(0, AndroidUtilities.dp(3), 0, 0);
                    } else {
                        writeButton.setImageResource(R.drawable.floating_camera);
                        writeButton.setPadding(0, 0, 0, 0);
                    }
                }
                if (ChatObject.isChannel(chat)) {
                    ActionBarMenuItem item = null;
                    if (chat.creator || chat.megagroup && chat.editor) {
                        item = menu.addItem(10, R.drawable.ic_ab_other);
                        item.addSubItem(edit_channel, LocaleController.getString("ChannelEdit", R.string.ChannelEdit), 0);
                    }
                    if (!chat.creator && !chat.left && !chat.kicked && chat.megagroup) {
                        if (item == null) {
                            item = menu.addItem(10, R.drawable.ic_ab_other);
                        }
                        item.addSubItem(leave_group, LocaleController.getString("LeaveMegaMenu", R.string.LeaveMegaMenu), 0);
                    }
                } else {
                    ActionBarMenuItem item = menu.addItem(10, R.drawable.ic_ab_other);
                    if (chat.creator && chat_id > 0) {
                        item.addSubItem(set_admins, LocaleController.getString("SetAdmins", R.string.SetAdmins), 0);
                    }
                    if (!chat.admins_enabled || chat.creator || chat.admin) {
                        item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName), 0);
                    }
                    if (chat.creator && (info == null || info.participants.participants.size() > 1)) {
                        item.addSubItem(convert_to_supergroup, LocaleController.getString("ConvertGroupMenu", R.string.ConvertGroupMenu), 0);
                    }
                    item.addSubItem(leave_group, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit), 0);
                }
            } else {
                ActionBarMenuItem item = menu.addItem(10, R.drawable.ic_ab_other);
                item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName), 0);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    @Override
    public void didSelectDialog(DialogsActivity fragment, long dialog_id, boolean param) {
        if (dialog_id != 0) {
            Bundle args = new Bundle();
            args.putBoolean("scrollToTopOnResume", true);
            int lower_part = (int) dialog_id;
            if (lower_part != 0) {
                if (lower_part > 0) {
                    args.putInt("user_id", lower_part);
                } else if (lower_part < 0) {
                    args.putInt("chat_id", -lower_part);
                }
            } else {
                args.putInt("enc_id", (int) (dialog_id >> 32));
            }
            if (!MessagesController.checkCanOpenChat(args, fragment)) {
                return;
            }

            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            presentFragment(new ChatActivity(args), true);
            removeSelfFromStack();
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            SendMessagesHelper.getInstance().sendMessage(user, dialog_id, null, true, null, null);
        }
    }

    private class ListAdapter extends RecyclerListView.Adapter {
        private Context mContext;

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new EmptyCell(mContext);
                    break;
                case 1:
                    view = new DividerCell(mContext);
                    view.setPadding(AndroidUtilities.dp(72), 0, 0, 0);
                    break;
                case 2:
                    view = new TextDetailCell(mContext) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                                    getBackground().setHotspot(event.getX(), event.getY());
                                }
                            }
                            return super.onTouchEvent(event);
                        }
                    };
                    break;
                case 3:
                    view = new TextCell(mContext) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                                    getBackground().setHotspot(event.getX(), event.getY());
                                }
                            }
                            return super.onTouchEvent(event);
                        }
                    };
                    break;
                case 4:
                    view = new UserCell(mContext, 61, 0, true) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                                    getBackground().setHotspot(event.getX(), event.getY());
                                }
                            }
                            return super.onTouchEvent(event);
                        }
                    };
                    break;
                case 5:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 6:
                    view = new TextInfoPrivacyCell(mContext);
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) view;
                    cell.setBackgroundResource(R.drawable.greydivider);
                    cell.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ConvertGroupInfo", R.string.ConvertGroupInfo, LocaleController.formatPluralString("Members", MessagesController.getInstance().maxMegagroupCount))));
                    break;
                case 7:
                    view = new LoadingCell(mContext);
                    break;
                case 8:
                    view = new AboutLinkCell(mContext);
                    ((AboutLinkCell) view).setDelegate(new AboutLinkCell.AboutLinkCellDelegate() {
                        @Override
                        public void didPressUrl(String url) {
                            if (url.startsWith("@")) {
                                MessagesController.openByUserName(url.substring(1), ProfileActivity.this, 0);
                            } else if (url.startsWith("#")) {
                                DialogsActivity fragment = new DialogsActivity(null);
                                fragment.setSearchString(url);
                                presentFragment(fragment);
                            } else if (url.startsWith("/")) {
                                if (parentLayout.fragmentsStack.size() > 1) {
                                    BaseFragment previousFragment = parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2);
                                    if (previousFragment instanceof ChatActivity) {
                                        finishFragment();
                                        ((ChatActivity) previousFragment).chatActivityEnterView.setCommand(null, url, false, false);
                                    }
                                }
                            }
                        }
                    });
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
            boolean checkBackground = true;
            switch (holder.getItemViewType()) {
                case 0:
                    if (i == emptyRowChat || i == emptyRowChat2) {
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(8));
                    } else {
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(36));
                    }
                    break;
                case 2:
                    TextDetailCell textDetailCell = (TextDetailCell) holder.itemView;
                    if (i == phoneRow) {
                        String text;
                        final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        if (user.phone != null && user.phone.length() != 0) {
                            text = PhoneFormat.getInstance().format("+" + user.phone);
                        } else {
                            text = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                        }
                        textDetailCell.setTextAndValueAndIcon(text, LocaleController.getString("PhoneMobile", R.string.PhoneMobile), R.drawable.phone_grey);
                    } else if (i == usernameRow) {
                        String text;
                        final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        if (user != null && user.username != null && user.username.length() != 0) {
                            text = "@" + user.username;
                        } else {
                            text = "-";
                        }
                        textDetailCell.setTextAndValue(text, LocaleController.getString("Username", R.string.Username));
                    } else if (i == channelNameRow) {
                        String text;
                        if (currentChat != null && currentChat.username != null && currentChat.username.length() != 0) {
                            text = "@" + currentChat.username;
                        } else {
                            text = "-";
                        }
                        textDetailCell.setTextAndValue(text, "telegram.me/" + currentChat.username);
                    }
                    break;
                case 3:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setTextColor(0xff212121);

                    if (i == sharedMediaRow) {
                        String value;
                        if (totalMediaCount == -1) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = String.format("%d", totalMediaCount + (totalMediaCountMerge != -1 ? totalMediaCountMerge : 0));
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
                    } else if (i == leaveChannelRow) {
                        textCell.setTextColor(0xffed3d39);
                        textCell.setText(LocaleController.getString("LeaveChannel", R.string.LeaveChannel));
                    } else if (i == convertRow) {
                        textCell.setText(LocaleController.getString("UpgradeGroup", R.string.UpgradeGroup));
                        textCell.setTextColor(0xff37a919);
                    } else if (i == membersRow) {
                        if (info != null) {
                            textCell.setTextAndValue(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants_count));
                        } else {
                            textCell.setText(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
                        }
                    } else if (i == managementRow) {
                        if (info != null) {
                            textCell.setTextAndValue(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), String.format("%d", info.admins_count));
                        } else {
                            textCell.setText(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators));
                        }
                    } else if (i == blockedUsersRow) {
                        if (info != null) {
                            textCell.setTextAndValue(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers), String.format("%d", info.kicked_count));
                        } else {
                            textCell.setText(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
                        }
                    } else if (i == addMemberRow) {
                        if (chat_id > 0) {
                            textCell.setText(LocaleController.getString("AddMember", R.string.AddMember));
                        } else {
                            textCell.setText(LocaleController.getString("AddRecipient", R.string.AddRecipient));
                        }
                    }
                    break;
                case 4:
                    UserCell userCell = ((UserCell) holder.itemView);
                    TLRPC.ChatParticipant part;
                    if (!sortedUsers.isEmpty()) {
                        part = info.participants.participants.get(sortedUsers.get(i - emptyRowChat2 - 1));
                    } else {
                        part = info.participants.participants.get(i - emptyRowChat2 - 1);
                    }
                    if (part != null) {
                        if (part instanceof TLRPC.TL_chatChannelParticipant) {
                            TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                            if (channelParticipant instanceof TLRPC.TL_channelParticipantCreator) {
                                userCell.setIsAdmin(1);
                            } else if (channelParticipant instanceof TLRPC.TL_channelParticipantEditor || channelParticipant instanceof TLRPC.TL_channelParticipantModerator) {
                                userCell.setIsAdmin(2);
                            } else {
                                userCell.setIsAdmin(0);
                            }
                        } else {
                            if (part instanceof TLRPC.TL_chatParticipantCreator) {
                                userCell.setIsAdmin(1);
                            } else if (currentChat.admins_enabled && part instanceof TLRPC.TL_chatParticipantAdmin) {
                                userCell.setIsAdmin(2);
                            } else {
                                userCell.setIsAdmin(0);
                            }
                        }
                        userCell.setData(MessagesController.getInstance().getUser(part.user_id), null, null, i == emptyRowChat2 + 1 ? R.drawable.menu_newgroup : 0);
                    }
                    break;
                case 8:
                    AboutLinkCell aboutLinkCell = (AboutLinkCell) holder.itemView;
                    if (i == userInfoRow) {
                        String about = MessagesController.getInstance().getUserAbout(user_id);
                        aboutLinkCell.setTextAndIcon(about, R.drawable.bot_info);
                    } else if (i == channelInfoRow) {
                        String text = info.about;
                        while (text.contains("\n\n\n")) {
                            text = text.replace("\n\n\n", "\n\n");
                        }
                        aboutLinkCell.setTextAndIcon(text, R.drawable.bot_info);
                    }
                    break;
                default:
                    checkBackground = false;
            }
            if (checkBackground) {
                boolean enabled = false;
                if (user_id != 0) {
                    enabled = i == phoneRow || i == settingsTimerRow || i == settingsKeyRow || i == settingsNotificationsRow ||
                            i == sharedMediaRow || i == startSecretChatRow || i == usernameRow || i == userInfoRow;
                } else if (chat_id != 0) {
                    enabled = i == convertRow || i == settingsNotificationsRow || i == sharedMediaRow || i > emptyRowChat2 && i < membersEndRow ||
                            i == addMemberRow || i == channelNameRow || i == leaveChannelRow || i == membersRow || i == managementRow ||
                            i == blockedUsersRow || i == channelInfoRow;
                }
                if (enabled) {
                    if (holder.itemView.getBackground() == null) {
                        holder.itemView.setBackgroundResource(R.drawable.list_selector);
                    }
                } else {
                    if (holder.itemView.getBackground() != null) {
                        holder.itemView.setBackgroundDrawable(null);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == emptyRow || i == emptyRowChat || i == emptyRowChat2) {
                return 0;
            } else if (i == sectionRow || i == userSectionRow) {
                return 1;
            } else if (i == phoneRow || i == usernameRow || i == channelNameRow) {
                return 2;
            } else if (i == leaveChannelRow || i == sharedMediaRow || i == settingsTimerRow || i == settingsNotificationsRow || i == startSecretChatRow || i == settingsKeyRow || i == membersRow || i == managementRow || i == blockedUsersRow || i == convertRow || i == addMemberRow) {
                return 3;
            } else if (i > emptyRowChat2 && i < membersEndRow) {
                return 4;
            } else if (i == membersSectionRow) {
                return 5;
            } else if (i == convertHelpRow) {
                return 6;
            } else if (i == loadMoreMembersRow) {
                return 7;
            } else if (i == userInfoRow || i == channelInfoRow) {
                return 8;
            }
            return 0;
        }
    }
}
