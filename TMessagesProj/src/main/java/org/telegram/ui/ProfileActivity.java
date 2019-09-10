/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
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
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.IdenticonDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

public class ProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate {

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private BackupImageView avatarImage;
    private SimpleTextView[] nameTextView = new SimpleTextView[2];
    private SimpleTextView[] onlineTextView = new SimpleTextView[2];
    private ImageView writeButton;
    private AnimatorSet writeButtonAnimation;
    private ScamDrawable scamDrawable;
    private MediaActivity mediaActivity;
    private UndoView undoView;

    private boolean[] isOnline = new boolean[1];

    private AvatarDrawable avatarDrawable;
    private ActionBarMenuItem animatingItem;
    private ActionBarMenuItem callItem;
    private ActionBarMenuItem editItem;
    private TopView topView;
    private int user_id;
    private int chat_id;
    private long dialog_id;
    private boolean creatingChat;
    private boolean userBlocked;
    private boolean reportSpam;
    private long mergeDialogId;

    private int[] mediaCount = new int[]{-1, -1, -1, -1, -1};
    private int[] mediaMergeCount = new int[]{-1, -1, -1, -1, -1};
    private int[] lastMediaCount = new int[]{-1, -1, -1, -1, -1};
    private int[] prevMediaCount = new int[]{-1, -1, -1, -1, -1};

    private MediaActivity.SharedMediaData[] sharedMediaData;

    private boolean loadingUsers;
    private SparseArray<TLRPC.ChatParticipant> participantsMap = new SparseArray<>();
    private boolean usersEndReached;

    private int banFromGroup;
    private boolean openAnimationInProgress;
    private boolean recreateMenuAfterAnimation;
    private boolean playProfileAnimation;
    private boolean allowProfileAnimation = true;
    private int extraHeight;
    private int initialAnimationExtraHeight;
    private float animationProgress;

    private boolean isBot;

    private TLRPC.ChatFull chatInfo;
    private TLRPC.UserFull userInfo;

    private int selectedUser;
    private int onlineCount = -1;
    private ArrayList<Integer> sortedUsers;

    private TLRPC.EncryptedChat currentEncryptedChat;
    private TLRPC.Chat currentChat;
    private TLRPC.BotInfo botInfo;
    private TLRPC.ChannelParticipant currentChannelParticipant;

    private final static int add_contact = 1;
    private final static int block_contact = 2;
    private final static int share_contact = 3;
    private final static int edit_contact = 4;
    private final static int delete_contact = 5;
    private final static int leave_group = 7;
    private final static int invite_to_group = 9;
    private final static int share = 10;
    private final static int edit_channel = 12;
    private final static int add_shortcut = 14;
    private final static int call_item = 15;
    private final static int search_members = 17;
    private final static int add_member = 18;
    private final static int statistics = 19;

    private int rowCount;

    private int emptyRow;
    private int infoHeaderRow;
    private int phoneRow;
    private int locationRow;
    private int userInfoRow;
    private int channelInfoRow;
    private int usernameRow;
    private int notificationsDividerRow;
    private int notificationsRow;
    private int infoSectionRow;

    private int settingsTimerRow;
    private int settingsKeyRow;
    private int settingsSectionRow;

    private int membersHeaderRow;
    private int membersStartRow;
    private int membersEndRow;
    private int addMemberRow;
    private int subscribersRow;
    private int administratorsRow;
    private int blockedUsersRow;
    private int membersSectionRow;

    private int sharedHeaderRow;
    private int photosRow;
    private int filesRow;
    private int linksRow;
    private int audioRow;
    private int voiceRow;
    private int groupsInCommonRow;
    private int sharedSectionRow;

    private int unblockRow;
    private int startSecretChatRow;
    private int leaveChannelRow;
    private int joinRow;
    private int lastSectionRow;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (fileLocation == null) {
                return null;
            }

            TLRPC.FileLocation photoBig = null;
            if (user_id != 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                if (user != null && user.photo != null && user.photo.photo_big != null) {
                    photoBig = user.photo.photo_big;
                }
            } else if (chat_id != 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                    photoBig = chat.photo.photo_big;
                }
            }

            if (photoBig != null && photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int[] coords = new int[2];
                avatarImage.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                object.parentView = avatarImage;
                object.imageReceiver = avatarImage.getImageReceiver();
                if (user_id != 0) {
                    object.dialogId = user_id;
                } else if (chat_id != 0) {
                    object.dialogId = -chat_id;
                }
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.size = -1;
                object.radius = avatarImage.getImageReceiver().getRoundRadius();
                object.scale = avatarImage.getScaleX();
                return object;
            }
            return null;
        }

        @Override
        public void willHidePhotoViewer() {
            avatarImage.getImageReceiver().setVisible(true, true);
        }
    };

    private class TopView extends View {

        private int currentColor;
        private Paint paint = new Paint();

        public TopView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(91));
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
        chat_id = arguments.getInt("chat_id", 0);
        banFromGroup = arguments.getInt("ban_chat_id", 0);
        reportSpam = arguments.getBoolean("reportSpam", false);
        if (user_id != 0) {
            dialog_id = arguments.getLong("dialog_id", 0);
            if (dialog_id != 0) {
                currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
            if (user == null) {
                return false;
            }
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.encryptedChatCreated);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.encryptedChatUpdated);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.blockedUsersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botInfoDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
            userBlocked = MessagesController.getInstance(currentAccount).blockedUsers.indexOfKey(user_id) >= 0;
            if (user.bot) {
                isBot = true;
                MediaDataController.getInstance(currentAccount).loadBotInfo(user.id, true, classGuid);
            }
            userInfo = MessagesController.getInstance(currentAccount).getUserFull(user_id);
            MessagesController.getInstance(currentAccount).loadFullUser(MessagesController.getInstance(currentAccount).getUser(user_id), classGuid, true);
            participantsMap = null;
        } else if (chat_id != 0) {
            currentChat = MessagesController.getInstance(currentAccount).getChat(chat_id);
            if (currentChat == null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    currentChat = MessagesStorage.getInstance(currentAccount).getChat(chat_id);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (currentChat != null) {
                    MessagesController.getInstance(currentAccount).putChat(currentChat, true);
                } else {
                    return false;
                }
            }

            if (currentChat.megagroup) {
                getChannelParticipants(true);
            } else {
                participantsMap = null;
            }
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatOnlineCountDidLoad);

            sortedUsers = new ArrayList<>();
            updateOnlineCount();
            if (chatInfo == null) {
                chatInfo = getMessagesController().getChatFull(chat_id);
            }
            if (ChatObject.isChannel(currentChat)) {
                MessagesController.getInstance(currentAccount).loadFullChat(chat_id, classGuid, true);
            } else if (chatInfo == null) {
                chatInfo = getMessagesStorage().loadChatInfo(chat_id, null, false, false);
            }
        } else {
            return false;
        }

        sharedMediaData = new MediaActivity.SharedMediaData[5];
        for (int a = 0; a < sharedMediaData.length; a++) {
            sharedMediaData[a] = new MediaActivity.SharedMediaData();
            sharedMediaData[a].setMaxId(0, dialog_id != 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
        }

        loadMediaCounts();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaCountDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaCountsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        updateRowsIds();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaCountDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaCountsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDeleted);
        if (user_id != 0) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.encryptedChatCreated);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.encryptedChatUpdated);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.blockedUsersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botInfoDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
            MessagesController.getInstance(currentAccount).cancelLoadFullUser(user_id);
        } else if (chat_id != 0) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatOnlineCountDidLoad);
        }
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return super.onTouchEvent(event);
            }
        };
        actionBar.setItemsBackgroundColor(AvatarDrawable.getButtonColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), true);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21 && !AndroidUtilities.isTablet());
        return actionBar;
    }

    @Override
    public View createView(Context context) {
        Theme.createProfileResources(context);

        hasOwnBackground = true;
        extraHeight = AndroidUtilities.dp(88);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (getParentActivity() == null) {
                    return;
                }
                if (id == -1) {
                    finishFragment();
                } else if (id == block_contact) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                    if (user == null) {
                        return;
                    }
                    if (!isBot || MessagesController.isSupportUser(user)) {
                        if (userBlocked) {
                            MessagesController.getInstance(currentAccount).unblockUser(user_id);
                            AlertsCreator.showSimpleToast(ProfileActivity.this, LocaleController.getString("UserUnblocked", R.string.UserUnblocked));
                        } else {
                            if (reportSpam) {
                                AlertsCreator.showBlockReportSpamAlert(ProfileActivity.this, user_id, user, null, currentEncryptedChat, false, null, param -> {
                                    if (param == 1) {
                                        NotificationCenter.getInstance(currentAccount).removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                                        playProfileAnimation = false;
                                        finishFragment();
                                    } else {
                                        getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, (long) user_id);
                                    }
                                });
                            } else {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle(LocaleController.getString("BlockUser", R.string.BlockUser));
                                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureBlockContact2", R.string.AreYouSureBlockContact2, ContactsController.formatName(user.first_name, user.last_name))));
                                builder.setPositiveButton(LocaleController.getString("BlockContact", R.string.BlockContact), (dialogInterface, i) -> {
                                    MessagesController.getInstance(currentAccount).blockUser(user_id);
                                    AlertsCreator.showSimpleToast(ProfileActivity.this, LocaleController.getString("UserBlocked", R.string.UserBlocked));
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                AlertDialog dialog = builder.create();
                                showDialog(dialog);
                                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                                if (button != null) {
                                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                                }
                            }
                        }
                    } else {
                        if (!userBlocked) {
                            MessagesController.getInstance(currentAccount).blockUser(user_id);
                        } else {
                            MessagesController.getInstance(currentAccount).unblockUser(user_id);
                            SendMessagesHelper.getInstance(currentAccount).sendMessage("/start", user_id, null, null, false, null, null, null, true, 0);
                            finishFragment();
                        }
                    }
                } else if (id == add_contact) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                    Bundle args = new Bundle();
                    args.putInt("user_id", user.id);
                    args.putBoolean("addContact", true);
                    presentFragment(new ContactAddActivity(args));
                } else if (id == share_contact) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
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
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                    if (user == null || getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("DeleteContact", R.string.DeleteContact));
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteContact", R.string.AreYouSureDeleteContact));
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                        ArrayList<TLRPC.User> arrayList = new ArrayList<>();
                        arrayList.add(user);
                        ContactsController.getInstance(currentAccount).deleteContact(arrayList);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                } else if (id == leave_group) {
                    leaveChatPressed();
                } else if (id == edit_channel) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    ChatEditActivity fragment = new ChatEditActivity(args);
                    fragment.setInfo(chatInfo);
                    presentFragment(fragment);
                } else if (id == invite_to_group) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                    if (user == null) {
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 2);
                    args.putString("addToGroupAlertString", LocaleController.formatString("AddToTheGroupTitle", R.string.AddToTheGroupTitle, UserObject.getUserName(user), "%1$s"));
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate((fragment1, dids, message, param) -> {
                        long did = dids.get(0);
                        Bundle args1 = new Bundle();
                        args1.putBoolean("scrollToTopOnResume", true);
                        args1.putInt("chat_id", -(int) did);
                        if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment1)) {
                            return;
                        }

                        NotificationCenter.getInstance(currentAccount).removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                        MessagesController.getInstance(currentAccount).addUserToChat(-(int) did, user, null, 0, null, ProfileActivity.this, null);
                        presentFragment(new ChatActivity(args1), true);
                        removeSelfFromStack();
                    });
                    presentFragment(fragment);
                } else if (id == share) {
                    try {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                        if (user == null) {
                            return;
                        }
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        if (botInfo != null && userInfo != null && !TextUtils.isEmpty(userInfo.about)) {
                            intent.putExtra(Intent.EXTRA_TEXT, String.format("%s https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/%s", userInfo.about, user.username));
                        } else {
                            intent.putExtra(Intent.EXTRA_TEXT, String.format("https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/%s", user.username));
                        }
                        startActivityForResult(Intent.createChooser(intent, LocaleController.getString("BotShare", R.string.BotShare)), 500);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (id == add_shortcut) {
                    try {
                        long did;
                        if (currentEncryptedChat != null) {
                            did = ((long) currentEncryptedChat.id) << 32;
                        } else if (user_id != 0) {
                            did = user_id;
                        } else if (chat_id != 0) {
                            did = -chat_id;
                        } else {
                            return;
                        }
                        MediaDataController.getInstance(currentAccount).installShortcut(did);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (id == call_item) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                    if (user != null) {
                        VoIPHelper.startCall(user, getParentActivity(), userInfo);
                    }
                } else if (id == search_members) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    args.putInt("type", ChatUsersActivity.TYPE_USERS);
                    args.putBoolean("open_search", true);
                    ChatUsersActivity fragment = new ChatUsersActivity(args);
                    fragment.setInfo(chatInfo);
                    presentFragment(fragment);
                } else if (id == add_member) {
                    openAddMember();
                } else if (id == statistics) {
                    final int did;
                    if (user_id != 0) {
                        did = user_id;
                    } else {
                        did = -chat_id;
                    }
                    final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), 3)};
                    TLRPC.TL_messages_getStatsURL req = new TLRPC.TL_messages_getStatsURL();
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
                    req.dark = Theme.getCurrentTheme().isDark();
                    req.params = "";
                    int requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog[0].dismiss();
                        } catch (Throwable ignore) {

                        }
                        progressDialog[0] = null;
                        if (response != null) {
                            TLRPC.TL_statsURL url = (TLRPC.TL_statsURL) response;
                            presentFragment(new WebviewActivity(url.url, -chat_id));
                        }
                    }));
                    if (progressDialog[0] == null) {
                        return;
                    }
                    progressDialog[0].setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true));
                    showDialog(progressDialog[0]);
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

            private Paint paint = new Paint();

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            public void onDraw(Canvas c) {
                ViewHolder holder;
                if (lastSectionRow != -1) {
                    holder = findViewHolderForAdapterPosition(lastSectionRow);
                } else if (sharedSectionRow != -1 && (membersSectionRow == -1 || membersSectionRow < sharedSectionRow)) {
                    holder = findViewHolderForAdapterPosition(sharedSectionRow);
                } else if (membersSectionRow != -1 && (sharedSectionRow == -1 || membersSectionRow > sharedSectionRow)) {
                    holder = findViewHolderForAdapterPosition(membersSectionRow);
                } else if (settingsSectionRow != -1) {
                    holder = findViewHolderForAdapterPosition(settingsSectionRow);
                } else if (infoSectionRow != -1) {
                    holder = findViewHolderForAdapterPosition(infoSectionRow);
                } else {
                    holder = null;
                }
                int bottom;
                int height = getMeasuredHeight();
                if (holder != null) {
                    bottom = holder.itemView.getBottom();
                    if (holder.itemView.getBottom() >= height) {
                        bottom = height;
                    }
                } else {
                    bottom = height;
                }

                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                c.drawRect(0, 0, getMeasuredWidth(), bottom, paint);
                if (bottom != height) {
                    paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    c.drawRect(0, bottom, getMeasuredWidth(), height, paint);
                }
            }
        };
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
        listView.setGlowColor(AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == photosRow || position == filesRow || position == linksRow || position == audioRow || position == voiceRow) {
                int tab;
                if (position == photosRow) {
                    tab = MediaDataController.MEDIA_PHOTOVIDEO;
                } else if (position == filesRow) {
                    tab = MediaDataController.MEDIA_FILE;
                } else if (position == linksRow) {
                    tab = MediaDataController.MEDIA_URL;
                } else if (position == audioRow) {
                    tab = MediaDataController.MEDIA_MUSIC;
                } else {
                    tab = MediaDataController.MEDIA_AUDIO;
                }
                Bundle args = new Bundle();
                if (user_id != 0) {
                    args.putLong("dialog_id", dialog_id != 0 ? dialog_id : user_id);
                } else {
                    args.putLong("dialog_id", -chat_id);
                }
                int[] media = new int[MediaDataController.MEDIA_TYPES_COUNT];
                System.arraycopy(lastMediaCount, 0, media, 0, media.length);
                mediaActivity = new MediaActivity(args, media, sharedMediaData, tab);
                mediaActivity.setChatInfo(chatInfo);
                presentFragment(mediaActivity);
            } else if (position == groupsInCommonRow) {
                presentFragment(new CommonGroupsActivity(user_id));
            } else if (position == settingsKeyRow) {
                Bundle args = new Bundle();
                args.putInt("chat_id", (int) (dialog_id >> 32));
                presentFragment(new IdenticonActivity(args));
            } else if (position == settingsTimerRow) {
                showDialog(AlertsCreator.createTTLAlert(getParentActivity(), currentEncryptedChat).create());
            } else if (position == notificationsRow) {
                final long did;
                if (dialog_id != 0) {
                    did = dialog_id;
                } else if (user_id != 0) {
                    did = user_id;
                } else {
                    did = -chat_id;
                }

                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                    boolean checked = !checkCell.isChecked();

                    boolean defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(did);

                    if (checked) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        SharedPreferences.Editor editor = preferences.edit();
                        if (defaultEnabled) {
                            editor.remove("notify2_" + did);
                        } else {
                            editor.putInt("notify2_" + did, 0);
                        }
                        MessagesStorage.getInstance(currentAccount).setDialogFlags(did, 0);
                        editor.commit();
                        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(did);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                        }
                        NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did);
                    } else {
                        int untilTime = Integer.MAX_VALUE;
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        SharedPreferences.Editor editor = preferences.edit();
                        long flags;
                        if (!defaultEnabled) {
                            editor.remove("notify2_" + did);
                            flags = 0;
                        } else {
                            editor.putInt("notify2_" + did, 2);
                            flags = 1;
                        }
                        NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(did);
                        MessagesStorage.getInstance(currentAccount).setDialogFlags(did, flags);
                        editor.commit();
                        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(did);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                            if (defaultEnabled) {
                                dialog.notify_settings.mute_until = untilTime;
                            }
                        }
                        NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did);
                    }
                    checkCell.setChecked(checked);
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForPosition(notificationsRow);
                    if (holder != null) {
                        listAdapter.onBindViewHolder(holder, notificationsRow);
                    }
                    return;
                }
                AlertsCreator.showCustomNotificationsDialog(ProfileActivity.this, did, -1, null, currentAccount, param -> listAdapter.notifyItemChanged(notificationsRow));
            } else if (position == startSecretChatRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AreYouSureSecretChatTitle", R.string.AreYouSureSecretChatTitle));
                builder.setMessage(LocaleController.getString("AreYouSureSecretChat", R.string.AreYouSureSecretChat));
                builder.setPositiveButton(LocaleController.getString("Start", R.string.Start), (dialogInterface, i) -> {
                    creatingChat = true;
                    SecretChatHelper.getInstance(currentAccount).startSecretChat(getParentActivity(), MessagesController.getInstance(currentAccount).getUser(user_id));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == unblockRow) {
                MessagesController.getInstance(currentAccount).unblockUser(user_id);
                AlertsCreator.showSimpleToast(ProfileActivity.this, LocaleController.getString("UserUnblocked", R.string.UserUnblocked));
            } else if (position >= membersStartRow && position < membersEndRow) {
                int user_id;
                if (!sortedUsers.isEmpty()) {
                    user_id = chatInfo.participants.participants.get(sortedUsers.get(position - membersStartRow)).user_id;
                } else {
                    user_id = chatInfo.participants.participants.get(position - membersStartRow).user_id;
                }
                if (user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    return;
                }
                Bundle args = new Bundle();
                args.putInt("user_id", user_id);
                presentFragment(new ProfileActivity(args));
            } else if (position == addMemberRow) {
                openAddMember();
            } else if (position == usernameRow) {
                if (currentChat != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        if (!TextUtils.isEmpty(chatInfo.about)) {
                            intent.putExtra(Intent.EXTRA_TEXT, currentChat.title + "\n" + chatInfo.about + "\nhttps://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + currentChat.username);
                        } else {
                            intent.putExtra(Intent.EXTRA_TEXT, currentChat.title + "\nhttps://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + currentChat.username);
                        }
                        getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("BotShare", R.string.BotShare)), 500);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } else if (position == locationRow) {
                if (chatInfo.location instanceof TLRPC.TL_channelLocation) {
                    LocationActivity fragment = new LocationActivity(LocationActivity.LOCATION_TYPE_GROUP_VIEW);
                    fragment.setChatLocation(chat_id, (TLRPC.TL_channelLocation) chatInfo.location);
                    presentFragment(fragment);
                }
            } else if (position == leaveChannelRow) {
                leaveChatPressed();
            } else if (position == joinRow) {
                MessagesController.getInstance(currentAccount).addUserToChat(currentChat.id, UserConfig.getInstance(currentAccount).getCurrentUser(), null, 0, null, ProfileActivity.this, null);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeSearchByActiveAction);
            } else if (position == subscribersRow) {
                Bundle args = new Bundle();
                args.putInt("chat_id", chat_id);
                args.putInt("type", ChatUsersActivity.TYPE_USERS);
                ChatUsersActivity fragment = new ChatUsersActivity(args);
                fragment.setInfo(chatInfo);
                presentFragment(fragment);
            } else if (position == administratorsRow) {
                Bundle args = new Bundle();
                args.putInt("chat_id", chat_id);
                args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
                ChatUsersActivity fragment = new ChatUsersActivity(args);
                fragment.setInfo(chatInfo);
                presentFragment(fragment);
            } else if (position == blockedUsersRow) {
                Bundle args = new Bundle();
                args.putInt("chat_id", chat_id);
                args.putInt("type", ChatUsersActivity.TYPE_BANNED);
                ChatUsersActivity fragment = new ChatUsersActivity(args);
                fragment.setInfo(chatInfo);
                presentFragment(fragment);
            } else {
                processOnClickOrPress(position);
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            if (position >= membersStartRow && position < membersEndRow) {
                if (getParentActivity() == null) {
                    return false;
                }
                final TLRPC.ChatParticipant participant;
                if (!sortedUsers.isEmpty()) {
                    participant = chatInfo.participants.participants.get(sortedUsers.get(position - membersStartRow));
                } else {
                    participant = chatInfo.participants.participants.get(position - membersStartRow);
                }
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                if (user == null || participant.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    return false;
                }
                selectedUser = participant.user_id;
                boolean allowKick;
                boolean canEditAdmin;
                boolean canRestrict;
                boolean editingAdmin;
                final TLRPC.ChannelParticipant channelParticipant;

                if (ChatObject.isChannel(currentChat)) {
                    channelParticipant = ((TLRPC.TL_chatChannelParticipant) participant).channelParticipant;
                    TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                    canEditAdmin = ChatObject.canAddAdmins(currentChat);
                    if (canEditAdmin && (channelParticipant instanceof TLRPC.TL_channelParticipantCreator || channelParticipant instanceof TLRPC.TL_channelParticipantAdmin && !channelParticipant.can_edit)) {
                        canEditAdmin = false;
                    }
                    allowKick = canRestrict = ChatObject.canBlockUsers(currentChat) && (!(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit);
                    editingAdmin = channelParticipant instanceof TLRPC.TL_channelParticipantAdmin;
                } else {
                    channelParticipant = null;
                    allowKick = currentChat.creator || participant instanceof TLRPC.TL_chatParticipant && (ChatObject.canBlockUsers(currentChat) || participant.inviter_id == UserConfig.getInstance(currentAccount).getClientUserId());
                    canEditAdmin = currentChat.creator;
                    canRestrict = currentChat.creator;
                    editingAdmin = participant instanceof TLRPC.TL_chatParticipantAdmin;
                }

                ArrayList<String> items = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                boolean hasRemove = false;

                if (canEditAdmin) {
                    items.add(editingAdmin ? LocaleController.getString("EditAdminRights", R.string.EditAdminRights) : LocaleController.getString("SetAsAdmin", R.string.SetAsAdmin));
                    icons.add(R.drawable.actions_addadmin);
                    actions.add(0);
                }
                if (canRestrict) {
                    items.add(LocaleController.getString("ChangePermissions", R.string.ChangePermissions));
                    icons.add(R.drawable.actions_permissions);
                    actions.add(1);
                }
                if (allowKick) {
                    items.add(LocaleController.getString("KickFromGroup", R.string.KickFromGroup));
                    icons.add(R.drawable.actions_remove_user);
                    actions.add(2);
                    hasRemove = true;
                }

                if (items.isEmpty()) {
                    return false;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setItems(items.toArray(new CharSequence[0]), AndroidUtilities.toIntArray(icons), (dialogInterface, i) -> {
                    if (actions.get(i) == 2) {
                        kickUser(selectedUser);
                    } else {
                        int action = actions.get(i);
                        if (action == 1 && (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                            AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                            builder2.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder2.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)));
                            builder2.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                                if (channelParticipant != null) {
                                    openRightsEdit(action, user.id, participant, channelParticipant.admin_rights, channelParticipant.banned_rights, channelParticipant.rank);
                                } else {
                                    openRightsEdit(action, user.id, participant, null, null, "");
                                }
                            });
                            builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder2.create());
                        } else {
                            if (channelParticipant != null) {
                                openRightsEdit(action, user.id, participant, channelParticipant.admin_rights, channelParticipant.banned_rights, channelParticipant.rank);
                            } else {
                                openRightsEdit(action, user.id, participant, null, null, "");
                            }
                        }
                    }
                });
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                if (hasRemove) {
                    alertDialog.setItemColor(items.size() - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
                }
                return true;
            } else {
                return processOnClickOrPress(position);
            }
        });

        if (banFromGroup != 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(banFromGroup);
            if (currentChannelParticipant == null) {
                TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
                req.channel = MessagesController.getInputChannel(chat);
                req.user_id = MessagesController.getInstance(currentAccount).getInputUser(user_id);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(() -> currentChannelParticipant = ((TLRPC.TL_channels_channelParticipant) response).participant);
                    }
                });
            }
            FrameLayout frameLayout1 = new FrameLayout(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                    Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                    Theme.chat_composeShadowDrawable.draw(canvas);
                    canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
                }
            };
            frameLayout1.setWillNotDraw(false);

            frameLayout.addView(frameLayout1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.LEFT | Gravity.BOTTOM));
            frameLayout1.setOnClickListener(v -> {
                ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, banFromGroup, null, chat.default_banned_rights, currentChannelParticipant != null ? currentChannelParticipant.banned_rights : null, "", ChatRightsEditActivity.TYPE_BANNED, true, false);
                fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                    @Override
                    public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                        removeSelfFromStack();
                    }

                    @Override
                    public void didChangeOwner(TLRPC.User user) {
                        undoView.showWithAction(-chat_id, currentChat.megagroup ? UndoView.ACTION_OWNER_TRANSFERED_GROUP : UndoView.ACTION_OWNER_TRANSFERED_CHANNEL, user);
                    }
                });
                presentFragment(fragment);
            });

            TextView textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setText(LocaleController.getString("BanFromTheGroup", R.string.BanFromTheGroup));
            frameLayout1.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 1, 0, 0));

            listView.setPadding(0, AndroidUtilities.dp(88), 0, AndroidUtilities.dp(48));
            listView.setBottomGlowOffset(AndroidUtilities.dp(48));
        } else {
            listView.setPadding(0, AndroidUtilities.dp(88), 0, 0);
        }

        topView = new TopView(context);
        topView.setBackgroundColor(AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id));
        frameLayout.addView(topView);

        frameLayout.addView(actionBar);

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarImage.setPivotX(0);
        avatarImage.setPivotY(0);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.LEFT, 64, 0, 0, 0));
        avatarImage.setOnClickListener(v -> {
            if (user_id != 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                if (user.photo != null && user.photo.photo_big != null) {
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    if (user.photo.dc_id != 0) {
                        user.photo.photo_big.dc_id = user.photo.dc_id;
                    }
                    PhotoViewer.getInstance().openPhoto(user.photo.photo_big, provider);
                }
            } else if (chat_id != 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                if (chat.photo != null && chat.photo.photo_big != null) {
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    if (chat.photo.dc_id != 0) {
                        chat.photo.photo_big.dc_id = chat.photo.dc_id;
                    }
                    PhotoViewer.getInstance().openPhoto(chat.photo.photo_big, provider);
                }
            }
        });
        avatarImage.setContentDescription(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));

        for (int a = 0; a < 2; a++) {
            if (!playProfileAnimation && a == 0) {
                continue;
            }
            nameTextView[a] = new SimpleTextView(context);
            if (a == 1) {
                nameTextView[a].setTextColor(Theme.getColor(Theme.key_profile_title));
            } else {
                nameTextView[a].setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
            }
            nameTextView[a].setTextSize(18);
            nameTextView[a].setGravity(Gravity.LEFT);
            nameTextView[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView[a].setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
            nameTextView[a].setPivotX(0);
            nameTextView[a].setPivotY(0);
            nameTextView[a].setAlpha(a == 0 ? 0.0f : 1.0f);
            if (a == 1) {
                nameTextView[a].setScrollNonFitText(true);
                nameTextView[a].setBackgroundColor(AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id));
            }
            frameLayout.addView(nameTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 0, a == 0 ? 48 : 0, 0));

            onlineTextView[a] = new SimpleTextView(context);
            onlineTextView[a].setTextColor(AvatarDrawable.getProfileTextColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id));
            onlineTextView[a].setTextSize(14);
            onlineTextView[a].setGravity(Gravity.LEFT);
            onlineTextView[a].setAlpha(a == 0 ? 0.0f : 1.0f);
            frameLayout.addView(onlineTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 0, a == 0 ? 48 : 8, 0));
        }

        if (user_id != 0) {
            writeButton = new ImageView(context);
            Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_profile_actionBackground), Theme.getColor(Theme.key_profile_actionPressedBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                drawable = combinedDrawable;
            }
            writeButton.setBackgroundDrawable(drawable);
            writeButton.setImageResource(R.drawable.profile_newmsg);
            writeButton.setContentDescription(LocaleController.getString("AccDescrOpenChat", R.string.AccDescrOpenChat));
            writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon), PorterDuff.Mode.MULTIPLY));
            writeButton.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(writeButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(writeButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                writeButton.setStateListAnimator(animator);
                writeButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            frameLayout.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.RIGHT | Gravity.TOP, 0, 0, 16, 0));
            writeButton.setOnClickListener(v -> {
                if (playProfileAnimation && parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2) instanceof ChatActivity) {
                    finishFragment();
                } else {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                    if (user == null || user instanceof TLRPC.TL_userEmpty) {
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putInt("user_id", user_id);
                    if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ProfileActivity.this)) {
                        return;
                    }
                    NotificationCenter.getInstance(currentAccount).removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                    presentFragment(new ChatActivity(args), true);
                }
            });
        }
        needLayout();

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkListViewScroll();
                if (participantsMap != null && !usersEndReached && layoutManager.findLastVisibleItemPosition() > membersEndRow - 8) {
                    getChannelParticipants(false);
                }
            }
        });

        undoView = new UndoView(context);
        frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    private void openRightsEdit(int action, int user_id, TLRPC.ChatParticipant participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, String rank) {
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, chat_id, adminRights, currentChat.default_banned_rights, bannedRights, rank, action, true, false);
        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
            @Override
            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                if (action == 0) {
                    if (participant instanceof TLRPC.TL_chatChannelParticipant) {
                        TLRPC.TL_chatChannelParticipant channelParticipant1 = ((TLRPC.TL_chatChannelParticipant) participant);
                        if (rights == 1) {
                            channelParticipant1.channelParticipant = new TLRPC.TL_channelParticipantAdmin();
                            channelParticipant1.channelParticipant.flags |= 4;
                        } else {
                            channelParticipant1.channelParticipant = new TLRPC.TL_channelParticipant();
                        }
                        channelParticipant1.channelParticipant.inviter_id = UserConfig.getInstance(currentAccount).getClientUserId();
                        channelParticipant1.channelParticipant.user_id = participant.user_id;
                        channelParticipant1.channelParticipant.date = participant.date;
                        channelParticipant1.channelParticipant.banned_rights = rightsBanned;
                        channelParticipant1.channelParticipant.admin_rights = rightsAdmin;
                        channelParticipant1.channelParticipant.rank = rank;
                    } else if (participant instanceof TLRPC.ChatParticipant) {
                        TLRPC.ChatParticipant newParticipant;
                        if (rights == 1) {
                            newParticipant = new TLRPC.TL_chatParticipantAdmin();
                        } else {
                            newParticipant = new TLRPC.TL_chatParticipant();
                        }
                        newParticipant.user_id = participant.user_id;
                        newParticipant.date = participant.date;
                        newParticipant.inviter_id = participant.inviter_id;
                        int index = chatInfo.participants.participants.indexOf(participant);
                        if (index >= 0) {
                            chatInfo.participants.participants.set(index, newParticipant);
                        }
                    }
                } else if (action == 1) {
                    if (rights == 0) {
                        if (currentChat.megagroup && chatInfo != null && chatInfo.participants != null) {
                            boolean changed = false;
                            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                                TLRPC.ChannelParticipant p = ((TLRPC.TL_chatChannelParticipant) chatInfo.participants.participants.get(a)).channelParticipant;
                                if (p.user_id == participant.user_id) {
                                    if (chatInfo != null) {
                                        chatInfo.participants_count--;
                                    }
                                    chatInfo.participants.participants.remove(a);
                                    changed = true;
                                    break;
                                }
                            }
                            if (chatInfo != null && chatInfo.participants != null) {
                                for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                                    TLRPC.ChatParticipant p = chatInfo.participants.participants.get(a);
                                    if (p.user_id == participant.user_id) {
                                        chatInfo.participants.participants.remove(a);
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
                    }
                }
            }

            @Override
            public void didChangeOwner(TLRPC.User user) {
                undoView.showWithAction(-chat_id, currentChat.megagroup ? UndoView.ACTION_OWNER_TRANSFERED_GROUP : UndoView.ACTION_OWNER_TRANSFERED_CHANNEL, user);
            }
        });
        presentFragment(fragment);
    }

    private boolean processOnClickOrPress(final int position) {
        if (position == usernameRow) {
            final String username;
            if (user_id != 0) {
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                if (user == null || user.username == null) {
                    return false;
                }
                username = user.username;
            } else if (chat_id != 0) {
                final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                if (chat == null || chat.username == null) {
                    return false;
                }
                username = chat.username;
            } else {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, (dialogInterface, i) -> {
                if (i == 0) {
                    try {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", "@" + username);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
            showDialog(builder.create());
            return true;
        } else if (position == phoneRow) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
            if (user == null || user.phone == null || user.phone.length() == 0 || getParentActivity() == null) {
                return false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            ArrayList<CharSequence> items = new ArrayList<>();
            final ArrayList<Integer> actions = new ArrayList<>();
            if (userInfo != null && userInfo.phone_calls_available) {
                items.add(LocaleController.getString("CallViaTelegram", R.string.CallViaTelegram));
                actions.add(2);
            }
            items.add(LocaleController.getString("Call", R.string.Call));
            actions.add(0);
            items.add(LocaleController.getString("Copy", R.string.Copy));
            actions.add(1);
            builder.setItems(items.toArray(new CharSequence[0]), (dialogInterface, i) -> {
                i = actions.get(i);
                if (i == 0) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + user.phone));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getParentActivity().startActivityForResult(intent, 500);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (i == 1) {
                    try {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", "+" + user.phone);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getParentActivity(), LocaleController.getString("PhoneCopied", R.string.PhoneCopied), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (i == 2) {
                    VoIPHelper.startCall(user, getParentActivity(), userInfo);
                }
            });
            showDialog(builder.create());
            return true;
        } else if (position == channelInfoRow || position == userInfoRow || position == locationRow) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, (dialogInterface, i) -> {
                try {
                    String about;
                    if (position == locationRow) {
                        about = chatInfo != null && chatInfo.location instanceof TLRPC.TL_channelLocation ? ((TLRPC.TL_channelLocation) chatInfo.location).address : null;
                    } else if (position == channelInfoRow) {
                        about = chatInfo != null ? chatInfo.about : null;
                    } else {
                        about = userInfo != null ? userInfo.about : null;
                    }
                    if (TextUtils.isEmpty(about)) {
                        return;
                    }
                    AndroidUtilities.addToClipboard(about);
                    Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            showDialog(builder.create());
            return true;
        }
        return false;
    }

    private void leaveChatPressed() {
        AlertsCreator.createClearOrDeleteDialogAlert(ProfileActivity.this, false, currentChat, null, false, (param) -> {
            playProfileAnimation = false;
            NotificationCenter.getInstance(currentAccount).removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
            finishFragment();
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needDeleteDialog, (long) -currentChat.id, null, currentChat, param);
        });
    }

    private void getChannelParticipants(boolean reload) {
        if (loadingUsers || participantsMap == null || chatInfo == null) {
            return;
        }
        loadingUsers = true;
        final int delay = participantsMap.size() != 0 && reload ? 300 : 0;

        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat_id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = reload ? 0 : participantsMap.size();
        req.limit = 200;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                if (res.users.size() < 200) {
                    usersEndReached = true;
                }
                if (req.offset == 0) {
                    participantsMap.clear();
                    chatInfo.participants = new TLRPC.TL_chatParticipants();
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);
                    MessagesStorage.getInstance(currentAccount).updateChannelUsers(chat_id, res.participants);
                }
                for (int a = 0; a < res.participants.size(); a++) {
                    TLRPC.TL_chatChannelParticipant participant = new TLRPC.TL_chatChannelParticipant();
                    participant.channelParticipant = res.participants.get(a);
                    participant.inviter_id = participant.channelParticipant.inviter_id;
                    participant.user_id = participant.channelParticipant.user_id;
                    participant.date = participant.channelParticipant.date;
                    if (participantsMap.indexOfKey(participant.user_id) < 0) {
                        chatInfo.participants.participants.add(participant);
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
        }, delay));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    private void openAddMember() {
        Bundle args = new Bundle();
        args.putBoolean("addToGroup", true);
        args.putInt("chatId", currentChat.id);
        GroupCreateActivity fragment = new GroupCreateActivity(args);
        fragment.setInfo(chatInfo);
        if (chatInfo != null && chatInfo.participants != null) {
            SparseArray<TLObject> users = new SparseArray<>();
            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                users.put(chatInfo.participants.participants.get(a).user_id, null);
            }
            fragment.setIgnoreUsers(users);
        }
        fragment.setDelegate((users, fwdCount) -> {
            for (int a = 0, N = users.size(); a < N; a++) {
                TLRPC.User user = users.get(a);
                MessagesController.getInstance(currentAccount).addUserToChat(chat_id, user, chatInfo, fwdCount, null, ProfileActivity.this, null);
            }
        });
        presentFragment(fragment);
    }

    private void checkListViewScroll() {
        if (listView.getChildCount() <= 0 || openAnimationInProgress) {
            return;
        }

        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        if (extraHeight != newOffset) {
            extraHeight = newOffset;
            topView.invalidate();
            if (playProfileAnimation) {
                allowProfileAnimation = extraHeight != 0;
            }
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
                writeButton.setTranslationY((actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + extraHeight - AndroidUtilities.dp(29.5f));

                if (!openAnimationInProgress) {
                    final boolean setVisible = diff > 0.2f;
                    boolean currentVisible = writeButton.getTag() == null;
                    if (setVisible != currentVisible) {
                        if (setVisible) {
                            writeButton.setTag(null);
                        } else {
                            writeButton.setTag(0);
                        }
                        if (writeButtonAnimation != null) {
                            AnimatorSet old = writeButtonAnimation;
                            writeButtonAnimation = null;
                            old.cancel();
                        }
                        writeButtonAnimation = new AnimatorSet();
                        if (setVisible) {
                            writeButtonAnimation.setInterpolator(new DecelerateInterpolator());
                            writeButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 1.0f),
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 1.0f),
                                    ObjectAnimator.ofFloat(writeButton, View.ALPHA, 1.0f)
                            );
                        } else {
                            writeButtonAnimation.setInterpolator(new AccelerateInterpolator());
                            writeButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 0.2f),
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 0.2f),
                                    ObjectAnimator.ofFloat(writeButton, View.ALPHA, 0.0f)
                            );
                        }
                        writeButtonAnimation.setDuration(150);
                        writeButtonAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (writeButtonAnimation != null && writeButtonAnimation.equals(animation)) {
                                    writeButtonAnimation = null;
                                }
                            }
                        });
                        writeButtonAnimation.start();
                    }
                }
            }

            float avatarY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f * (1.0f + diff) - 21 * AndroidUtilities.density + 27 * AndroidUtilities.density * diff;
            avatarImage.setScaleX((42 + 18 * diff) / 42.0f);
            avatarImage.setScaleY((42 + 18 * diff) / 42.0f);
            avatarImage.setTranslationX(-AndroidUtilities.dp(47) * diff);
            avatarImage.setTranslationY((float) Math.ceil(avatarY));
            for (int a = 0; a < 2; a++) {
                if (nameTextView[a] == null) {
                    continue;
                }
                nameTextView[a].setTranslationX(-21 * AndroidUtilities.density * diff);
                nameTextView[a].setTranslationY((float) Math.floor(avatarY) + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7) * diff);
                onlineTextView[a].setTranslationX(-21 * AndroidUtilities.density * diff);
                onlineTextView[a].setTranslationY((float) Math.floor(avatarY) + AndroidUtilities.dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff);
                float scale = 1.0f + 0.12f * diff;
                nameTextView[a].setScaleX(scale);
                nameTextView[a].setScaleY(scale);
                if (a == 1 && !openAnimationInProgress) {
                    int viewWidth;
                    if (AndroidUtilities.isTablet()) {
                        viewWidth = AndroidUtilities.dp(490);
                    } else {
                        viewWidth = AndroidUtilities.displaySize.x;
                    }
                    int buttonsWidth = AndroidUtilities.dp(118 + 8 + (40 + (callItem != null || editItem != null ? 48 : 0)));
                    int minWidth = viewWidth - buttonsWidth;

                    int width = (int) (viewWidth - buttonsWidth * Math.max(0.0f, 1.0f - (diff != 1.0f ? diff * 0.15f / (1.0f - diff) : 1.0f)) - nameTextView[a].getTranslationX());
                    float width2 = nameTextView[a].getPaint().measureText(nameTextView[a].getText().toString()) * scale + nameTextView[a].getSideDrawablesSize();
                    layoutParams = (FrameLayout.LayoutParams) nameTextView[a].getLayoutParams();
                    if (width < width2) {
                        layoutParams.width = Math.max(minWidth, (int) Math.ceil((width - AndroidUtilities.dp(24)) / (scale + (1.12f - scale) * 7.0f)));
                    } else {
                        layoutParams.width = (int) Math.ceil(width2);
                    }
                    layoutParams.width = (int) Math.min((viewWidth - nameTextView[a].getX()) / scale - AndroidUtilities.dp(8), layoutParams.width);
                    nameTextView[a].setLayoutParams(layoutParams);

                    width2 = onlineTextView[a].getPaint().measureText(onlineTextView[a].getText().toString());
                    layoutParams = (FrameLayout.LayoutParams) onlineTextView[a].getLayoutParams();
                    layoutParams.rightMargin = (int) Math.ceil(onlineTextView[a].getTranslationX() + AndroidUtilities.dp(8) + AndroidUtilities.dp(40) * (1.0f - diff));
                    if (width < width2) {
                        layoutParams.width = (int) Math.ceil(width);
                    } else {
                        layoutParams.width = LayoutHelper.WRAP_CONTENT;
                    }
                    onlineTextView[a].setLayoutParams(layoutParams);
                }
            }
        }
    }

    private void loadMediaCounts() {
        if (dialog_id != 0) {
            MediaDataController.getInstance(currentAccount).getMediaCounts(dialog_id, classGuid);
        } else if (user_id != 0) {
            MediaDataController.getInstance(currentAccount).getMediaCounts(user_id, classGuid);
        } else if (chat_id > 0) {
            MediaDataController.getInstance(currentAccount).getMediaCounts(-chat_id, classGuid);
            if (mergeDialogId != 0) {
                MediaDataController.getInstance(currentAccount).getMediaCounts(mergeDialogId, classGuid);
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
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (user_id != 0) {
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateProfileData();
                }
                if ((mask & MessagesController.UPDATE_MASK_PHONE) != 0) {
                    if (listView != null) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForPosition(phoneRow);
                        if (holder != null) {
                            listAdapter.onBindViewHolder(holder, phoneRow);
                        }
                    }
                }
            } else if (chat_id != 0) {
                if ((mask & MessagesController.UPDATE_MASK_CHAT) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateOnlineCount();
                    updateProfileData();
                }
                if ((mask & MessagesController.UPDATE_MASK_CHAT) != 0) {
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
        } else if (id == NotificationCenter.chatOnlineCountDidLoad) {
            Integer chatId = (Integer) args[0];
            if (chatInfo == null || currentChat == null || currentChat.id != chatId) {
                return;
            }
            chatInfo.online_count = (Integer) args[1];
            updateOnlineCount();
            updateProfileData();
        } else if (id == NotificationCenter.contactsDidLoad) {
            createActionBarMenu();
        } else if (id == NotificationCenter.mediaDidLoad) {
            long uid = (Long) args[0];
            int guid = (Integer) args[3];
            if (guid == classGuid) {
                long did = dialog_id;
                if (did == 0) {
                    if (user_id != 0) {
                        did = user_id;
                    } else if (chat_id != 0) {
                        did = -chat_id;
                    }
                }

                int type = (Integer) args[4];
                sharedMediaData[type].setTotalCount((Integer) args[1]);
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                boolean enc = ((int) did) == 0;
                int loadIndex = uid == did ? 0 : 1;
                if (!arr.isEmpty()) {
                    sharedMediaData[type].setEndReached(loadIndex, (Boolean) args[5]);
                }
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject message = arr.get(a);
                    sharedMediaData[type].addMessage(message, loadIndex, false, enc);
                }
            }
        } else if (id == NotificationCenter.mediaCountsDidLoad) {
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
                int[] counts = (int[]) args[1];
                if (uid == did) {
                    mediaCount = counts;
                } else {
                    mediaMergeCount = counts;
                }
                System.arraycopy(lastMediaCount, 0, prevMediaCount, 0, prevMediaCount.length);
                for (int a = 0; a < lastMediaCount.length; a++) {
                    if (mediaCount[a] >= 0 && mediaMergeCount[a] >= 0) {
                        lastMediaCount[a] = mediaCount[a] + mediaMergeCount[a];
                    } else if (mediaCount[a] >= 0) {
                        lastMediaCount[a] = mediaCount[a];
                    } else if (mediaMergeCount[a] >= 0) {
                        lastMediaCount[a] = mediaMergeCount[a];
                    } else {
                        lastMediaCount[a] = 0;
                    }
                    if (uid == did && lastMediaCount[a] != 0) {
                        MediaDataController.getInstance(currentAccount).loadMedia(did, 50, 0, a, 2, classGuid);
                    }
                }
                updateSharedMediaRows();
            }
        } else if (id == NotificationCenter.mediaCountDidLoad) {
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
                int type = (Integer) args[3];
                int mCount = (Integer) args[1];
                if (uid == did) {
                    mediaCount[type] = mCount;
                } else {
                    mediaMergeCount[type] = mCount;
                }
                prevMediaCount[type] = lastMediaCount[type];
                if (mediaCount[type] >= 0 && mediaMergeCount[type] >= 0) {
                    lastMediaCount[type] = mediaCount[type] + mediaMergeCount[type];
                } else if (mediaCount[type] >= 0) {
                    lastMediaCount[type] = mediaCount[type];
                } else if (mediaMergeCount[type] >= 0) {
                    lastMediaCount[type] = mediaMergeCount[type];
                } else {
                    lastMediaCount[type] = 0;
                }
                updateSharedMediaRows();
            }
        } else if (id == NotificationCenter.encryptedChatCreated) {
            if (creatingChat) {
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).removeObserver(ProfileActivity.this, NotificationCenter.closeChats);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                    TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) args[0];
                    Bundle args2 = new Bundle();
                    args2.putInt("enc_id", encryptedChat.id);
                    presentFragment(new ChatActivity(args2), true);
                });
            }
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            boolean oldValue = userBlocked;
            userBlocked = MessagesController.getInstance(currentAccount).blockedUsers.indexOfKey(user_id) >= 0;
            if (oldValue != userBlocked) {
                createActionBarMenu();
                updateRowsIds();
                listAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chat_id) {
                boolean byChannelUsers = (Boolean) args[2];
                if (chatInfo instanceof TLRPC.TL_channelFull) {
                    if (chatFull.participants == null && chatInfo != null) {
                        chatFull.participants = chatInfo.participants;
                    }
                }
                boolean loadChannelParticipants = chatInfo == null && chatFull instanceof TLRPC.TL_channelFull;
                chatInfo = chatFull;
                if (mergeDialogId == 0 && chatInfo.migrated_from_chat_id != 0) {
                    mergeDialogId = -chatInfo.migrated_from_chat_id;
                    MediaDataController.getInstance(currentAccount).getMediaCount(mergeDialogId, MediaDataController.MEDIA_PHOTOVIDEO, classGuid, true);
                }
                fetchUsersFromChannelInfo();
                updateOnlineCount();
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                TLRPC.Chat newChat = MessagesController.getInstance(currentAccount).getChat(chat_id);
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
        } else if (id == NotificationCenter.botInfoDidLoad) {
            TLRPC.BotInfo info = (TLRPC.BotInfo) args[0];
            if (info.user_id == user_id) {
                botInfo = info;
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            int uid = (Integer) args[0];
            if (uid == user_id) {
                userInfo = (TLRPC.UserFull) args[1];
                if (!openAnimationInProgress && callItem == null) {
                    createActionBarMenu();
                } else {
                    recreateMenuAfterAnimation = true;
                }
                updateRowsIds();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            long did;
            if (dialog_id != 0) {
                did = dialog_id;
            } else if (user_id != 0) {
                did = user_id;
            } else {
                did = -chat_id;
            }
            if (did == (Long) args[0]) {
                boolean enc = ((int) did) == 0;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject obj = arr.get(a);
                    if (currentEncryptedChat != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction && obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL) obj.messageOwner.action.encryptedAction;
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    }

                    int type = MediaDataController.getMediaType(obj.messageOwner);
                    if (type == -1) {
                        return;
                    }
                    sharedMediaData[type].addMessage(obj, 0, true, enc);
                }
                loadMediaCounts();
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            int channelId = (Integer) args[1];
            if (ChatObject.isChannel(currentChat)) {
                if (!(channelId == 0 && mergeDialogId != 0 || channelId == currentChat.id)) {
                    return;
                }
            } else if (channelId != 0) {
                return;
            }

            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            boolean updated = false;
            for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                for (int b = 0; b < sharedMediaData.length; b++) {
                    if (sharedMediaData[b].deleteMessage(markAsDeletedMessages.get(a), 0)) {
                        updated = true;
                    }
                }
            }
            if (updated && mediaActivity != null) {
                mediaActivity.updateAdapters();
            }
            loadMediaCounts();
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
        if (nameTextView[1] != null) {
            setParentActivityTitle(nameTextView[1].getText());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    protected void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    public void setPlayProfileAnimation(boolean value) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (!AndroidUtilities.isTablet() && preferences.getBoolean("view_animations", true)) {
            playProfileAnimation = value;
        }
    }

    private void updateSharedMediaRows() {
        if (listAdapter == null) {
            return;
        }
        int sharedHeaderRowPrev = sharedHeaderRow;
        int photosRowPrev = photosRow;
        int filesRowPrev = filesRow;
        int linksRowPrev = linksRow;
        int audioRowPrev = audioRow;
        int voiceRowPrev = voiceRow;
        int groupsInCommonRowPrev = groupsInCommonRow;
        int sharedSectionRowPrev = sharedSectionRow;
        updateRowsIds();
        if (sharedHeaderRowPrev == -1 && sharedHeaderRow != -1) {
            int newRowsCount = 2;
            if (photosRow != -1) {
                newRowsCount++;
            }
            if (filesRow != -1) {
                newRowsCount++;
            }
            if (linksRow != -1) {
                newRowsCount++;
            }
            if (audioRow != -1) {
                newRowsCount++;
            }
            if (voiceRow != -1) {
                newRowsCount++;
            }
            if (groupsInCommonRow != -1) {
                newRowsCount++;
            }
            listAdapter.notifyItemRangeInserted(sharedHeaderRow, newRowsCount);
        } else if (sharedHeaderRowPrev != -1 && sharedHeaderRow != -1) {
            if (photosRowPrev != -1 && photosRow != -1 && prevMediaCount[MediaDataController.MEDIA_PHOTOVIDEO] != lastMediaCount[MediaDataController.MEDIA_PHOTOVIDEO]) {
                listAdapter.notifyItemChanged(photosRow);
            }
            if (filesRowPrev != -1 && filesRow != -1 && prevMediaCount[MediaDataController.MEDIA_FILE] != lastMediaCount[MediaDataController.MEDIA_FILE]) {
                listAdapter.notifyItemChanged(filesRow);
            }
            if (linksRowPrev != -1 && linksRow != -1 && prevMediaCount[MediaDataController.MEDIA_URL] != lastMediaCount[MediaDataController.MEDIA_URL]) {
                listAdapter.notifyItemChanged(linksRow);
            }
            if (audioRowPrev != -1 && audioRow != -1 && prevMediaCount[MediaDataController.MEDIA_MUSIC] != lastMediaCount[MediaDataController.MEDIA_MUSIC]) {
                listAdapter.notifyItemChanged(audioRow);
            }
            if (voiceRowPrev != -1 && voiceRow != -1 && prevMediaCount[MediaDataController.MEDIA_AUDIO] != lastMediaCount[MediaDataController.MEDIA_AUDIO]) {
                listAdapter.notifyItemChanged(voiceRow);
            }
            if (photosRowPrev == -1 && photosRow != -1) {
                listAdapter.notifyItemInserted(photosRow);
            } else if (photosRowPrev != -1 && photosRow == -1) {
                listAdapter.notifyItemRemoved(photosRowPrev);
            }
            if (filesRowPrev == -1 && filesRow != -1) {
                listAdapter.notifyItemInserted(filesRow);
            } else if (filesRowPrev != -1 && filesRow == -1) {
                listAdapter.notifyItemRemoved(filesRowPrev);
            }
            if (linksRowPrev == -1 && linksRow != -1) {
                listAdapter.notifyItemInserted(linksRow);
            } else if (linksRowPrev != -1 && linksRow == -1) {
                listAdapter.notifyItemRemoved(linksRowPrev);
            }
            if (audioRowPrev == -1 && audioRow != -1) {
                listAdapter.notifyItemInserted(audioRow);
            } else if (audioRowPrev != -1 && audioRow == -1) {
                listAdapter.notifyItemRemoved(audioRowPrev);
            }
            if (voiceRowPrev == -1 && voiceRow != -1) {
                listAdapter.notifyItemInserted(voiceRow);
            } else if (voiceRowPrev != -1 && voiceRow == -1) {
                listAdapter.notifyItemRemoved(voiceRowPrev);
            }
            if (groupsInCommonRowPrev == -1 && groupsInCommonRow != -1) {
                listAdapter.notifyItemInserted(groupsInCommonRow);
            } else if (groupsInCommonRowPrev != -1 && groupsInCommonRow == -1) {
                listAdapter.notifyItemRemoved(groupsInCommonRowPrev);
            }
        }
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if ((!isOpen && backward || isOpen && !backward) && playProfileAnimation && allowProfileAnimation) {
            openAnimationInProgress = true;
        }
        if (isOpen) {
            NotificationCenter.getInstance(currentAccount).setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoad, NotificationCenter.mediaCountsDidLoad});
            NotificationCenter.getInstance(currentAccount).setAnimationInProgress(true);
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            if (!backward && playProfileAnimation && allowProfileAnimation) {
                openAnimationInProgress = false;
                if (recreateMenuAfterAnimation) {
                    createActionBarMenu();
                }
            }
            NotificationCenter.getInstance(currentAccount).setAnimationInProgress(false);
        }
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    @Keep
    public void setAnimationProgress(float progress) {
        animationProgress = progress;

        listView.setAlpha(progress);

        listView.setTranslationX(AndroidUtilities.dp(48) - AndroidUtilities.dp(48) * progress);

        int color = AvatarDrawable.getProfileBackColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id);

        int actionBarColor = Theme.getColor(Theme.key_actionBarDefault);
        int r = Color.red(actionBarColor);
        int g = Color.green(actionBarColor);
        int b = Color.blue(actionBarColor);
        int a;

        int rD = (int) ((Color.red(color) - r) * progress);
        int gD = (int) ((Color.green(color) - g) * progress);
        int bD = (int) ((Color.blue(color) - b) * progress);
        int aD;
        topView.setBackgroundColor(Color.rgb(r + rD, g + gD, b + bD));

        color = AvatarDrawable.getIconColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id);
        int iconColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
        r = Color.red(iconColor);
        g = Color.green(iconColor);
        b = Color.blue(iconColor);

        rD = (int) ((Color.red(color) - r) * progress);
        gD = (int) ((Color.green(color) - g) * progress);
        bD = (int) ((Color.blue(color) - b) * progress);
        actionBar.setItemsColor(Color.rgb(r + rD, g + gD, b + bD), false);

        color = Theme.getColor(Theme.key_profile_title);
        int titleColor = Theme.getColor(Theme.key_actionBarDefaultTitle);
        r = Color.red(titleColor);
        g = Color.green(titleColor);
        b = Color.blue(titleColor);
        a = Color.alpha(titleColor);

        rD = (int) ((Color.red(color) - r) * progress);
        gD = (int) ((Color.green(color) - g) * progress);
        bD = (int) ((Color.blue(color) - b) * progress);
        aD = (int) ((Color.alpha(color) - a) * progress);
        for (int i = 0; i < 2; i++) {
            if (nameTextView[i] == null) {
                continue;
            }
            nameTextView[i].setTextColor(Color.argb(a + aD, r + rD, g + gD, b + bD));
        }

        color = isOnline[0] ? Theme.getColor(Theme.key_profile_status) : AvatarDrawable.getProfileTextColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id);
        int subtitleColor = Theme.getColor(isOnline[0] ? Theme.key_chat_status : Theme.key_actionBarDefaultSubtitle);
        r = Color.red(subtitleColor);
        g = Color.green(subtitleColor);
        b = Color.blue(subtitleColor);
        a = Color.alpha(subtitleColor);

        rD = (int) ((Color.red(color) - r) * progress);
        gD = (int) ((Color.green(color) - g) * progress);
        bD = (int) ((Color.blue(color) - b) * progress);
        aD = (int) ((Color.alpha(color) - a) * progress);
        for (int i = 0; i < 2; i++) {
            if (onlineTextView[i] == null) {
                continue;
            }
            onlineTextView[i].setTextColor(Color.argb(a + aD, r + rD, g + gD, b + bD));
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

    /*@Override
    protected AnimatorSet onCustomTransitionAnimation(final boolean isOpen, final Runnable callback) {
        if (playProfileAnimation && allowProfileAnimation) {
            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setDuration(180);
            listView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ActionBarMenu menu = actionBar.createMenu();
            if (menu.getItem(10) == null) {
                if (animatingItem == null) {
                    animatingItem = menu.addItem(10, R.drawable.ic_ab_other);
                }
            }
            ArrayList<Animator> animators = new ArrayList<>();
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

                animators.add(ObjectAnimator.ofFloat(this, "animationProgress", 0.0f, 1.0f));

                listView.setAlpha(0.0f);
                listView.setTranslationX(AndroidUtilities.dp(48));
                animators.add(FastAnimator.ofView(listView).alpha(1.0f).translationX(0));

                if (writeButton != null) {
                    writeButton.setScaleX(0.2f);
                    writeButton.setScaleY(0.2f);
                    writeButton.setAlpha(0.0f);
                    animators.add(FastAnimator.ofView(writeButton).scaleX(1.0f).scaleY(1.0f).alpha(1.0f));
                }
                for (int a = 0; a < 2; a++) {
                    onlineTextView[a].setAlpha(a == 0 ? 1.0f : 0.0f);
                    nameTextView[a].setAlpha(a == 0 ? 1.0f : 0.0f);
                    animators.add(FastAnimator.ofView(onlineTextView[a]).alpha(a == 0 ? 0.0f : 1.0f));
                    animators.add(FastAnimator.ofView(nameTextView[a]).alpha(a == 0 ? 0.0f : 1.0f));
                }
                if (animatingItem != null) {
                    animatingItem.setAlpha(1.0f);
                    animators.add(FastAnimator.ofView(animatingItem).alpha(0.0f));
                }
                if (callItem != null) {
                    callItem.setAlpha(0.0f);
                    animators.add(FastAnimator.ofView(callItem).alpha(1.0f));
                }
                if (editItem != null) {
                    editItem.setAlpha(0.0f);
                    animators.add(FastAnimator.ofView(editItem).alpha(1.0f));
                }
                animatorSet.playTogether(animators);
            } else {
                initialAnimationExtraHeight = extraHeight;

                animators.add(ObjectAnimator.ofFloat(this, "animationProgress", 1.0f, 0.0f));

                animators.add(FastAnimator.ofView(listView).alpha(0.0f).translationX(AndroidUtilities.dp(48)));

                if (writeButton != null) {
                    animators.add(FastAnimator.ofView(writeButton).scaleX(0.2f).scaleY(0.2f).alpha(0.0f));
                }
                for (int a = 0; a < 2; a++) {
                    animators.add(FastAnimator.ofView(onlineTextView[a]).alpha(a == 0 ? 1.0f : 0.0f));
                    animators.add(FastAnimator.ofView(nameTextView[a]).alpha(a == 0 ? 1.0f : 0.0f));
                }
                if (animatingItem != null) {
                    animatingItem.setAlpha(0.0f);
                    animators.add(FastAnimator.ofView(animatingItem).alpha(1.0f));
                }
                if (callItem != null) {
                    callItem.setAlpha(1.0f);
                    animators.add(FastAnimator.ofView(callItem).alpha(0.0f));
                }
                if (editItem != null) {
                    editItem.setAlpha(1.0f);
                    animators.add(FastAnimator.ofView(editItem).alpha(0.0f));
                }

            }
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.playTogether(animators);
            animators.get(0).addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    listView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (animatingItem != null) {
                        ActionBarMenu menu = actionBar.createMenu();
                        menu.clearItems();
                        animatingItem = null;
                    }
                    callback.run();
                }
            });

            AndroidUtilities.runOnUIThread(animatorSet::start, 50);
            return animatorSet;
        }
        return null;
    }*/

    @Override
    protected AnimatorSet onCustomTransitionAnimation(final boolean isOpen, final Runnable callback) {
        if (playProfileAnimation && allowProfileAnimation) {
            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setDuration(180);
            listView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(this, "animationProgress", 0.0f, 1.0f));
                if (writeButton != null) {
                    writeButton.setScaleX(0.2f);
                    writeButton.setScaleY(0.2f);
                    writeButton.setAlpha(0.0f);
                    animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(writeButton, View.ALPHA, 1.0f));
                }
                for (int a = 0; a < 2; a++) {
                    onlineTextView[a].setAlpha(a == 0 ? 1.0f : 0.0f);
                    nameTextView[a].setAlpha(a == 0 ? 1.0f : 0.0f);
                    animators.add(ObjectAnimator.ofFloat(onlineTextView[a], View.ALPHA, a == 0 ? 0.0f : 1.0f));
                    animators.add(ObjectAnimator.ofFloat(nameTextView[a], View.ALPHA, a == 0 ? 0.0f : 1.0f));
                }
                if (animatingItem != null) {
                    animatingItem.setAlpha(1.0f);
                    animators.add(ObjectAnimator.ofFloat(animatingItem, View.ALPHA, 0.0f));
                }
                if (callItem != null) {
                    callItem.setAlpha(0.0f);
                    animators.add(ObjectAnimator.ofFloat(callItem, View.ALPHA, 1.0f));
                }
                if (editItem != null) {
                    editItem.setAlpha(0.0f);
                    animators.add(ObjectAnimator.ofFloat(editItem, View.ALPHA, 1.0f));
                }
                animatorSet.playTogether(animators);
            } else {
                initialAnimationExtraHeight = extraHeight;
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(this, "animationProgress", 1.0f, 0.0f));
                if (writeButton != null) {
                    animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 0.2f));
                    animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 0.2f));
                    animators.add(ObjectAnimator.ofFloat(writeButton, View.ALPHA, 0.0f));
                }
                for (int a = 0; a < 2; a++) {
                    animators.add(ObjectAnimator.ofFloat(onlineTextView[a], View.ALPHA, a == 0 ? 1.0f : 0.0f));
                    animators.add(ObjectAnimator.ofFloat(nameTextView[a], View.ALPHA, a == 0 ? 1.0f : 0.0f));
                }
                if (animatingItem != null) {
                    animatingItem.setAlpha(0.0f);
                    animators.add(ObjectAnimator.ofFloat(animatingItem, View.ALPHA, 1.0f));
                }
                if (callItem != null) {
                    callItem.setAlpha(1.0f);
                    animators.add(ObjectAnimator.ofFloat(callItem, View.ALPHA, 0.0f));
                }
                if (editItem != null) {
                    editItem.setAlpha(1.0f);
                    animators.add(ObjectAnimator.ofFloat(editItem, View.ALPHA, 0.0f));
                }
                animatorSet.playTogether(animators);
            }
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    listView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (animatingItem != null) {
                        ActionBarMenu menu = actionBar.createMenu();
                        menu.clearItems();
                        animatingItem = null;
                    }
                    callback.run();
                }
            });
            animatorSet.setInterpolator(new DecelerateInterpolator());

            AndroidUtilities.runOnUIThread(animatorSet::start, 50);
            return animatorSet;
        }
        return null;
    }

    private void updateOnlineCount() {
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        sortedUsers.clear();
        if (chatInfo instanceof TLRPC.TL_chatFull || chatInfo instanceof TLRPC.TL_channelFull && chatInfo.participants_count <= 200 && chatInfo.participants != null) {
            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = chatInfo.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getInstance(currentAccount).getClientUserId()) && user.status.expires > 10000) {
                    onlineCount++;
                }
                sortedUsers.add(a);
            }

            try {
                Collections.sort(sortedUsers, (lhs, rhs) -> {
                    TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(chatInfo.participants.participants.get(rhs).user_id);
                    TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(chatInfo.participants.participants.get(lhs).user_id);
                    int status1 = 0;
                    int status2 = 0;
                    if (user1 != null) {
                        if (user1.bot) {
                            status1 = -110;
                        } else if (user1.self) {
                            status1 = currentTime + 50000;
                        } else if (user1.status != null) {
                            status1 = user1.status.expires;
                        }
                    }
                    if (user2 != null) {
                        if (user2.bot) {
                            status2 = -110;
                        } else if (user2.self) {
                            status2 = currentTime + 50000;
                        } else if (user2.status != null) {
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
                });
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (listAdapter != null && membersStartRow > 0) {
                listAdapter.notifyItemRangeChanged(membersStartRow, sortedUsers.size());
            }
        } else if (chatInfo instanceof TLRPC.TL_channelFull && chatInfo.participants_count > 200) {
            onlineCount = chatInfo.online_count;
        }
    }

    public void setChatInfo(TLRPC.ChatFull value) {
        chatInfo = value;
        if (chatInfo != null && chatInfo.migrated_from_chat_id != 0 && mergeDialogId == 0) {
            mergeDialogId = -chatInfo.migrated_from_chat_id;
            MediaDataController.getInstance(currentAccount).getMediaCounts(mergeDialogId, classGuid);
        }
        fetchUsersFromChannelInfo();
    }

    public void setUserInfo(TLRPC.UserFull value) {
        userInfo = value;
    }

    private void fetchUsersFromChannelInfo() {
        if (currentChat == null || !currentChat.megagroup) {
            return;
        }
        if (chatInfo instanceof TLRPC.TL_channelFull && chatInfo.participants != null) {
            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                TLRPC.ChatParticipant chatParticipant = chatInfo.participants.participants.get(a);
                participantsMap.put(chatParticipant.user_id, chatParticipant);
            }
        }
    }

    private void kickUser(int uid) {
        if (uid != 0) {
            MessagesController.getInstance(currentAccount).deleteUserFromChat(chat_id, MessagesController.getInstance(currentAccount).getUser(uid), chatInfo);
        } else {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
            if (AndroidUtilities.isTablet()) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats, -(long) chat_id);
            } else {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
            }
            MessagesController.getInstance(currentAccount).deleteUserFromChat(chat_id, MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId()), chatInfo);
            playProfileAnimation = false;
            finishFragment();
        }
    }

    public boolean isChat() {
        return chat_id != 0;
    }

    private void updateRowsIds() {
        rowCount = 0;

        emptyRow = -1;
        infoHeaderRow = -1;
        phoneRow = -1;
        userInfoRow = -1;
        locationRow = -1;
        channelInfoRow = -1;
        usernameRow = -1;
        settingsTimerRow = -1;
        settingsKeyRow = -1;
        notificationsDividerRow = -1;
        notificationsRow = -1;
        infoSectionRow = -1;
        settingsSectionRow = -1;

        membersHeaderRow = -1;
        membersStartRow = -1;
        membersEndRow = -1;
        addMemberRow = -1;
        subscribersRow = -1;
        administratorsRow = -1;
        blockedUsersRow = -1;
        membersSectionRow = -1;

        sharedHeaderRow = -1;
        photosRow = -1;
        filesRow = -1;
        linksRow = -1;
        audioRow = -1;
        voiceRow = -1;
        groupsInCommonRow = -1;
        sharedSectionRow = -1;

        unblockRow = -1;
        startSecretChatRow = -1;
        leaveChannelRow = -1;
        joinRow = -1;
        lastSectionRow = -1;

        boolean hasMedia = false;
        for (int a = 0; a < lastMediaCount.length; a++) {
            if (lastMediaCount[a] > 0) {
                hasMedia = true;
                break;
            }
        }

        if (user_id != 0 && LocaleController.isRTL) {
            emptyRow = rowCount++;
        }

        if (user_id != 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);

            boolean hasInfo = userInfo != null && !TextUtils.isEmpty(userInfo.about) || user != null && !TextUtils.isEmpty(user.username);
            boolean hasPhone = user != null && !TextUtils.isEmpty(user.phone);

            infoHeaderRow = rowCount++;
            if (!isBot && (hasPhone || !hasPhone && !hasInfo)) {
                phoneRow = rowCount++;
            }
            if (userInfo != null && !TextUtils.isEmpty(userInfo.about)) {
                userInfoRow = rowCount++;
            }
            if (user != null && !TextUtils.isEmpty(user.username)) {
                usernameRow = rowCount++;
            }
            if (phoneRow != -1 || userInfoRow != -1 || usernameRow != -1) {
                notificationsDividerRow = rowCount++;
            }
            if (user_id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                notificationsRow = rowCount++;
            }
            infoSectionRow = rowCount++;

            if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                settingsTimerRow = rowCount++;
                settingsKeyRow = rowCount++;
                settingsSectionRow = rowCount++;
            }

            if (hasMedia || userInfo != null && userInfo.common_chats_count != 0) {
                sharedHeaderRow = rowCount++;
                if (lastMediaCount[MediaDataController.MEDIA_PHOTOVIDEO] > 0) {
                    photosRow = rowCount++;
                } else {
                    photosRow = -1;
                }
                if (lastMediaCount[MediaDataController.MEDIA_FILE] > 0) {
                    filesRow = rowCount++;
                } else {
                    filesRow = -1;
                }
                if (lastMediaCount[MediaDataController.MEDIA_URL] > 0) {
                    linksRow = rowCount++;
                } else {
                    linksRow = -1;
                }
                if (lastMediaCount[MediaDataController.MEDIA_MUSIC] > 0) {
                    audioRow = rowCount++;
                } else {
                    audioRow = -1;
                }
                if (lastMediaCount[MediaDataController.MEDIA_AUDIO] > 0) {
                    voiceRow = rowCount++;
                } else {
                    voiceRow = -1;
                }
                if (userInfo != null && userInfo.common_chats_count != 0) {
                    groupsInCommonRow = rowCount++;
                }
                sharedSectionRow = rowCount++;
            }

            if (user != null && !isBot && currentEncryptedChat == null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                if (userBlocked) {
                    unblockRow = rowCount++;
                } else {
                    startSecretChatRow = rowCount++;
                }
                lastSectionRow = rowCount++;
            }
        } else if (chat_id != 0) {
            if (chat_id > 0) {
                if (chatInfo != null && (!TextUtils.isEmpty(chatInfo.about) || chatInfo.location instanceof TLRPC.TL_channelLocation) || !TextUtils.isEmpty(currentChat.username)) {
                    infoHeaderRow = rowCount++;
                    if (chatInfo != null) {
                        if (!TextUtils.isEmpty(chatInfo.about)) {
                            channelInfoRow = rowCount++;
                        }
                        if (chatInfo.location instanceof TLRPC.TL_channelLocation) {
                            locationRow = rowCount++;
                        }
                    }
                    if (!TextUtils.isEmpty(currentChat.username)) {
                        usernameRow = rowCount++;
                    }
                }
                if (infoHeaderRow != -1) {
                    notificationsDividerRow = rowCount++;
                }
                notificationsRow = rowCount++;
                infoSectionRow = rowCount++;

                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    if (chatInfo != null && (currentChat.creator || chatInfo.can_view_participants)) {
                        membersHeaderRow = rowCount++;
                        subscribersRow = rowCount++;
                        administratorsRow = rowCount++;
                        if (chatInfo.banned_count != 0 || chatInfo.kicked_count != 0) {
                            blockedUsersRow = rowCount++;
                        }
                        membersSectionRow = rowCount++;
                    }
                }

                if (hasMedia) {
                    sharedHeaderRow = rowCount++;
                    if (lastMediaCount[MediaDataController.MEDIA_PHOTOVIDEO] > 0) {
                        photosRow = rowCount++;
                    } else {
                        photosRow = -1;
                    }
                    if (lastMediaCount[MediaDataController.MEDIA_FILE] > 0) {
                        filesRow = rowCount++;
                    } else {
                        filesRow = -1;
                    }
                    if (lastMediaCount[MediaDataController.MEDIA_URL] > 0) {
                        linksRow = rowCount++;
                    } else {
                        linksRow = -1;
                    }
                    if (lastMediaCount[MediaDataController.MEDIA_MUSIC] > 0) {
                        audioRow = rowCount++;
                    } else {
                        audioRow = -1;
                    }
                    if (lastMediaCount[MediaDataController.MEDIA_AUDIO] > 0) {
                        voiceRow = rowCount++;
                    } else {
                        voiceRow = -1;
                    }
                    sharedSectionRow = rowCount++;
                }

                if (ChatObject.isChannel(currentChat)) {
                    if (!currentChat.creator && !currentChat.left && !currentChat.kicked && !currentChat.megagroup) {
                        leaveChannelRow = rowCount++;
                        lastSectionRow = rowCount++;
                    }
                    if (chatInfo != null && currentChat.megagroup && chatInfo.participants != null && !chatInfo.participants.participants.isEmpty()) {
                        if (!ChatObject.isNotInChat(currentChat) && currentChat.megagroup && ChatObject.canAddUsers(currentChat) && (chatInfo == null || chatInfo.participants_count < MessagesController.getInstance(currentAccount).maxMegagroupCount)) {
                            addMemberRow = rowCount++;
                        } else {
                            membersHeaderRow = rowCount++;
                        }
                        membersStartRow = rowCount;
                        rowCount += chatInfo.participants.participants.size();
                        membersEndRow = rowCount;
                        membersSectionRow = rowCount++;
                    }

                    if (lastSectionRow == -1 && currentChat.left && !currentChat.kicked) {
                        joinRow = rowCount++;
                        lastSectionRow = rowCount++;
                    }
                } else if (chatInfo != null) {
                    if (!(chatInfo.participants instanceof TLRPC.TL_chatParticipantsForbidden)) {
                        if (ChatObject.canAddUsers(currentChat) || currentChat.default_banned_rights == null || !currentChat.default_banned_rights.invite_users) {
                            addMemberRow = rowCount++;
                        } else {
                            membersHeaderRow = rowCount++;
                        }
                        membersStartRow = rowCount;
                        rowCount += chatInfo.participants.participants.size();
                        membersEndRow = rowCount;
                        membersSectionRow = rowCount++;
                    }
                }
            } else if (!ChatObject.isChannel(currentChat) && chatInfo != null && !(chatInfo.participants instanceof TLRPC.TL_chatParticipantsForbidden)) {
                membersHeaderRow = rowCount++;
                membersStartRow = rowCount;
                rowCount += chatInfo.participants.participants.size();
                membersEndRow = rowCount;
                membersSectionRow = rowCount++;
                addMemberRow = rowCount++;
            }
        }
    }

    private Drawable getScamDrawable() {
        if (scamDrawable == null) {
            scamDrawable = new ScamDrawable(11);
            scamDrawable.setColor(AvatarDrawable.getProfileTextColorForId(user_id != 0 || ChatObject.isChannel(chat_id, currentAccount) && !currentChat.megagroup ? 5 : chat_id));
        }
        return scamDrawable;
    }

    private void updateProfileData() {
        if (avatarImage == null || nameTextView == null) {
            return;
        }
        String onlineTextOverride;
        int currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            onlineTextOverride = LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            onlineTextOverride = LocaleController.getString("Connecting", R.string.Connecting);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            onlineTextOverride = LocaleController.getString("Updating", R.string.Updating);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            onlineTextOverride = LocaleController.getString("ConnectingToProxy", R.string.ConnectingToProxy);
        } else {
            onlineTextOverride = null;
        }

        if (user_id != 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
            TLRPC.FileLocation photoBig = null;
            if (user.photo != null) {
                photoBig = user.photo.photo_big;
            }
            avatarDrawable.setInfo(user);
            avatarImage.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);
            FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForUser(user, true), user, null, 0, 1);

            String newString = UserObject.getUserName(user);
            String newString2;
            if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                newString2 = LocaleController.getString("ChatYourSelf", R.string.ChatYourSelf);
                newString = LocaleController.getString("ChatYourSelfName", R.string.ChatYourSelfName);
            } else if (user.id == 333000 || user.id == 777000 || user.id == 42777) {
                newString2 = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
            } else if (MessagesController.isSupportUser(user)) {
                newString2 = LocaleController.getString("SupportStatus", R.string.SupportStatus);
            } else if (isBot) {
                newString2 = LocaleController.getString("Bot", R.string.Bot);
            } else {
                isOnline[0] = false;
                newString2 = LocaleController.formatUserStatus(currentAccount, user, isOnline);
                if (onlineTextView[1] != null) {
                    String key = isOnline[0] ? Theme.key_profile_status : Theme.key_avatar_subtitleInProfileBlue;
                    onlineTextView[1].setTag(key);
                    onlineTextView[1].setTextColor(Theme.getColor(key));
                }
            }
            for (int a = 0; a < 2; a++) {
                if (nameTextView[a] == null) {
                    continue;
                }
                if (a == 0 && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && user.id / 1000 != 777 && user.id / 1000 != 333 && user.phone != null && user.phone.length() != 0 && ContactsController.getInstance(currentAccount).contactsDict.get(user.id) == null &&
                        (ContactsController.getInstance(currentAccount).contactsDict.size() != 0 || !ContactsController.getInstance(currentAccount).isLoadingContacts())) {
                    String phoneString = PhoneFormat.getInstance().format("+" + user.phone);
                    if (!nameTextView[a].getText().equals(phoneString)) {
                        nameTextView[a].setText(phoneString);
                    }
                } else {
                    if (!nameTextView[a].getText().equals(newString)) {
                        nameTextView[a].setText(newString);
                    }
                }
                if (a == 0 && onlineTextOverride != null) {
                    onlineTextView[a].setText(onlineTextOverride);
                } else {
                    if (!onlineTextView[a].getText().equals(newString2)) {
                        onlineTextView[a].setText(newString2);
                    }
                }
                Drawable leftIcon = currentEncryptedChat != null ? Theme.chat_lockIconDrawable : null;
                Drawable rightIcon = null;
                if (a == 0) {
                    if (user.scam) {
                        rightIcon = getScamDrawable();
                    } else {
                        rightIcon = MessagesController.getInstance(currentAccount).isDialogMuted(dialog_id != 0 ? dialog_id : (long) user_id) ? Theme.chat_muteIconDrawable : null;
                    }
                } else if (user.scam) {
                    rightIcon = getScamDrawable();
                } else if (user.verified) {
                    rightIcon = new CombinedDrawable(Theme.profile_verifiedDrawable, Theme.profile_verifiedCheckDrawable);
                }
                nameTextView[a].setLeftDrawable(leftIcon);
                nameTextView[a].setRightDrawable(rightIcon);
            }

            avatarImage.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(photoBig), false);
        } else if (chat_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
            if (chat != null) {
                currentChat = chat;
            } else {
                chat = currentChat;
            }

            String newString;
            if (ChatObject.isChannel(chat)) {
                if (chatInfo == null || !currentChat.megagroup && (chatInfo.participants_count == 0 || ChatObject.hasAdminRights(currentChat) || chatInfo.can_view_participants)) {
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
                    if (currentChat.megagroup) {
                        if (onlineCount > 1 && chatInfo.participants_count != 0) {
                            newString = String.format("%s, %s", LocaleController.formatPluralString("Members", chatInfo.participants_count), LocaleController.formatPluralString("OnlineCount", Math.min(onlineCount, chatInfo.participants_count)));
                        } else {
                            if (chatInfo.participants_count == 0) {
                                if (chat.has_geo) {
                                    newString = LocaleController.getString("MegaLocation", R.string.MegaLocation).toLowerCase();
                                } else if (!TextUtils.isEmpty(chat.username)) {
                                    newString = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                                } else {
                                    newString = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                                }
                            } else {
                                newString = LocaleController.formatPluralString("Members", chatInfo.participants_count);
                            }
                        }
                    } else {
                        int[] result = new int[1];
                        String shortNumber = LocaleController.formatShortNumber(chatInfo.participants_count, result);
                        if (currentChat.megagroup) {
                            newString = LocaleController.formatPluralString("Members", chatInfo.participants_count);
                        } else {
                            newString = LocaleController.formatPluralString("Subscribers", chatInfo.participants_count);
                        }
                    }
                }
            } else {
                int count = chat.participants_count;
                if (chatInfo != null) {
                    count = chatInfo.participants.participants.size();
                }
                if (count != 0 && onlineCount > 1) {
                    newString = String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("OnlineCount", onlineCount));
                } else {
                    newString = LocaleController.formatPluralString("Members", count);
                }
            }

            boolean changed = false;
            for (int a = 0; a < 2; a++) {
                if (nameTextView[a] == null) {
                    continue;
                }
                if (chat.title != null && !nameTextView[a].getText().equals(chat.title)) {
                    if (nameTextView[a].setText(chat.title)) {
                        changed = true;
                    }
                }
                nameTextView[a].setLeftDrawable(null);
                if (a != 0) {
                    if (chat.scam) {
                        nameTextView[a].setRightDrawable(getScamDrawable());
                    } else if (chat.verified) {
                        nameTextView[a].setRightDrawable(new CombinedDrawable(Theme.profile_verifiedDrawable, Theme.profile_verifiedCheckDrawable));
                    } else {
                        nameTextView[a].setRightDrawable(null);
                    }
                } else {
                    if (chat.scam) {
                        nameTextView[a].setRightDrawable(getScamDrawable());
                    } else {
                        nameTextView[a].setRightDrawable(MessagesController.getInstance(currentAccount).isDialogMuted((long) -chat_id) ? Theme.chat_muteIconDrawable : null);
                    }
                }
                if (a == 0 && onlineTextOverride != null) {
                    onlineTextView[a].setText(onlineTextOverride);
                } else {
                    if (currentChat.megagroup && chatInfo != null && onlineCount > 0) {
                        if (!onlineTextView[a].getText().equals(newString)) {
                            onlineTextView[a].setText(newString);
                        }
                    } else if (a == 0 && ChatObject.isChannel(currentChat) && chatInfo != null && chatInfo.participants_count != 0 && (currentChat.megagroup || currentChat.broadcast)) {
                        int[] result = new int[1];
                        String shortNumber = LocaleController.formatShortNumber(chatInfo.participants_count, result);
                        if (currentChat.megagroup) {
                            if (chatInfo.participants_count == 0) {
                                if (chat.has_geo) {
                                    newString = LocaleController.getString("MegaLocation", R.string.MegaLocation).toLowerCase();
                                } else if (!TextUtils.isEmpty(chat.username)) {
                                    newString = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                                } else {
                                    newString = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                                }
                            } else {
                                onlineTextView[a].setText(LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber));
                            }
                        } else {
                            onlineTextView[a].setText(LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber));
                        }
                    } else {
                        if (!onlineTextView[a].getText().equals(newString)) {
                            onlineTextView[a].setText(newString);
                        }
                    }
                }
            }
            if (changed) {
                needLayout();
            }

            TLRPC.FileLocation photoBig = null;
            if (chat.photo != null) {
                photoBig = chat.photo.photo_big;
            }
            avatarDrawable.setInfo(chat);
            avatarImage.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable, chat);
            FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForChat(chat, true), chat, null, 0, 1);
            avatarImage.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(photoBig), false);
        }
    }

    private void createActionBarMenu() {
        ActionBarMenu menu = actionBar.createMenu();
        menu.clearItems();
        animatingItem = null;

        ActionBarMenuItem item = null;
        if (user_id != 0) {
            if (UserConfig.getInstance(currentAccount).getClientUserId() != user_id) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                if (user == null) {
                    return;
                }
                if (userInfo != null && userInfo.phone_calls_available) {
                    callItem = menu.addItem(call_item, R.drawable.ic_call);
                }
                if (isBot || ContactsController.getInstance(currentAccount).contactsDict.get(user_id) == null) {
                    item = menu.addItem(10, R.drawable.ic_ab_other);
                    if (MessagesController.isSupportUser(user)) {
                        if (userBlocked) {
                            item.addSubItem(block_contact, R.drawable.msg_block, LocaleController.getString("Unblock", R.string.Unblock));
                        }
                    } else {
                        if (isBot) {
                            if (!user.bot_nochats) {
                                item.addSubItem(invite_to_group, R.drawable.msg_addbot, LocaleController.getString("BotInvite", R.string.BotInvite));
                            }
                            item.addSubItem(share, R.drawable.msg_share, LocaleController.getString("BotShare", R.string.BotShare));
                        } else {
                            item.addSubItem(add_contact, R.drawable.msg_addcontact, LocaleController.getString("AddContact", R.string.AddContact));
                        }
                        if (!TextUtils.isEmpty(user.phone)) {
                            item.addSubItem(share_contact, R.drawable.msg_share, LocaleController.getString("ShareContact", R.string.ShareContact));
                        }
                        if (isBot) {
                            item.addSubItem(block_contact, !userBlocked ? R.drawable.msg_block : R.drawable.msg_retry, !userBlocked ? LocaleController.getString("BotStop", R.string.BotStop) : LocaleController.getString("BotRestart", R.string.BotRestart));
                        } else {
                            item.addSubItem(block_contact, !userBlocked ? R.drawable.msg_block : R.drawable.msg_block, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock));
                        }
                    }
                } else {
                    item = menu.addItem(10, R.drawable.ic_ab_other);
                    if (!TextUtils.isEmpty(user.phone)) {
                        item.addSubItem(share_contact, R.drawable.msg_share, LocaleController.getString("ShareContact", R.string.ShareContact));
                    }
                    item.addSubItem(block_contact, !userBlocked ? R.drawable.msg_block : R.drawable.msg_block, !userBlocked ? LocaleController.getString("BlockContact", R.string.BlockContact) : LocaleController.getString("Unblock", R.string.Unblock));
                    item.addSubItem(edit_contact, R.drawable.msg_edit, LocaleController.getString("EditContact", R.string.EditContact));
                    item.addSubItem(delete_contact, R.drawable.msg_delete, LocaleController.getString("DeleteContact", R.string.DeleteContact));
                }
            } else {
                item = menu.addItem(10, R.drawable.ic_ab_other);
                item.addSubItem(share_contact, R.drawable.msg_share, LocaleController.getString("ShareContact", R.string.ShareContact));
            }
        } else if (chat_id != 0) {
            if (chat_id > 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                if (ChatObject.isChannel(chat)) {
                    if (ChatObject.hasAdminRights(chat) || chat.megagroup && ChatObject.canChangeChatInfo(chat)) {
                        editItem = menu.addItem(edit_channel, R.drawable.group_edit_profile);
                    }
                    if (!chat.megagroup && chatInfo != null && chatInfo.can_view_stats) {
                        if (item == null) {
                            item = menu.addItem(10, R.drawable.ic_ab_other);
                        }
                        item.addSubItem(statistics, R.drawable.msg_stats, LocaleController.getString("Statistics", R.string.Statistics));
                    }
                    if (chat.megagroup) {
                        if (item == null) {
                            item = menu.addItem(10, R.drawable.ic_ab_other);
                        }
                        item.addSubItem(search_members, R.drawable.msg_search, LocaleController.getString("SearchMembers", R.string.SearchMembers));
                        if (!chat.creator && !chat.left && !chat.kicked) {
                            item.addSubItem(leave_group, R.drawable.msg_leave, LocaleController.getString("LeaveMegaMenu", R.string.LeaveMegaMenu));
                        }
                    }
                } else {
                    if (ChatObject.canChangeChatInfo(chat)) {
                        editItem = menu.addItem(edit_channel, R.drawable.group_edit_profile);
                    }
                    item = menu.addItem(10, R.drawable.ic_ab_other);
                    item.addSubItem(search_members, R.drawable.msg_search, LocaleController.getString("SearchMembers", R.string.SearchMembers));
                    item.addSubItem(leave_group, R.drawable.msg_leave, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
                }
            }
        }
        if (item == null) {
            item = menu.addItem(10, R.drawable.ic_ab_other);
        }
        item.addSubItem(add_shortcut, R.drawable.msg_home, LocaleController.getString("AddShortcut", R.string.AddShortcut));
        item.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        if (editItem != null) {
            editItem.setContentDescription(LocaleController.getString("Edit", R.string.Edit));
        }
        if (callItem != null) {
            callItem.setContentDescription(LocaleController.getString("Call", R.string.Call));
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    @Override
    public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
        long did = dids.get(0);
        Bundle args = new Bundle();
        args.putBoolean("scrollToTopOnResume", true);
        int lower_part = (int) did;
        if (lower_part != 0) {
            if (lower_part > 0) {
                args.putInt("user_id", lower_part);
            } else if (lower_part < 0) {
                args.putInt("chat_id", -lower_part);
            }
        } else {
            args.putInt("enc_id", (int) (did >> 32));
        }
        if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args, fragment)) {
            return;
        }

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
        presentFragment(new ChatActivity(args), true);
        removeSelfFromStack();
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
        SendMessagesHelper.getInstance(currentAccount).sendMessage(user, did, null, null, null, true, 0);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
            if (user == null) {
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPHelper.startCall(user, getParentActivity(), userInfo);
            } else {
                VoIPHelper.permissionDenied(getParentActivity(), null);
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 1: {
                    view = new HeaderCell(mContext, 23);
                    break;
                }
                case 2: {
                    view = new TextDetailCell(mContext);
                    break;
                }
                case 3: {
                    view = new AboutLinkCell(mContext) {
                        @Override
                        protected void didPressUrl(String url) {
                            if (url.startsWith("@")) {
                                MessagesController.getInstance(currentAccount).openByUserName(url.substring(1), ProfileActivity.this, 0);
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
                    };
                    break;
                }
                case 4: {
                    view = new TextCell(mContext);
                    break;
                }
                case 5: {
                    view = new DividerCell(mContext);
                    view.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), 0, 0);
                    break;
                }
                case 6: {
                    view = new NotificationsCheckCell(mContext, 23, 70);
                    break;
                }
                case 7: {
                    view = new ShadowSectionCell(mContext);
                    break;
                }
                case 8: {
                    view = new UserCell(mContext, addMemberRow == -1 ? 9 : 6, 0, true);
                    break;
                }
                case 11: {
                    view = new EmptyCell(mContext, 36);
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == infoHeaderRow) {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup && channelInfoRow != -1) {
                            headerCell.setText(LocaleController.getString("ReportChatDescription", R.string.ReportChatDescription));
                        } else {
                            headerCell.setText(LocaleController.getString("Info", R.string.Info));
                        }
                    } else if (position == sharedHeaderRow) {
                        headerCell.setText(LocaleController.getString("SharedContent", R.string.SharedContent));
                    } else if (position == membersHeaderRow) {
                        headerCell.setText(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
                    }
                    break;
                case 2:
                    TextDetailCell detailCell = (TextDetailCell) holder.itemView;
                    if (position == phoneRow) {
                        String text;
                        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                        if (!TextUtils.isEmpty(user.phone)) {
                            text = PhoneFormat.getInstance().format("+" + user.phone);
                        } else {
                            text = LocaleController.getString("PhoneHidden", R.string.PhoneHidden);
                        }
                        detailCell.setTextAndValue(text, LocaleController.getString("PhoneMobile", R.string.PhoneMobile), false);
                    } else if (position == usernameRow) {
                        String text;
                        if (user_id != 0) {
                            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                            if (user != null && !TextUtils.isEmpty(user.username)) {
                                text = "@" + user.username;
                            } else {
                                text = "-";
                            }
                            detailCell.setTextAndValue(text, LocaleController.getString("Username", R.string.Username), false);
                        } else if (currentChat != null) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                            detailCell.setTextAndValue(MessagesController.getInstance(currentAccount).linkPrefix + "/" + chat.username, LocaleController.getString("InviteLink", R.string.InviteLink), false);
                        }
                    } else if (position == locationRow) {
                        if (chatInfo != null && chatInfo.location instanceof TLRPC.TL_channelLocation) {
                            TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatInfo.location;
                            detailCell.setTextAndValue(location.address, LocaleController.getString("AttachLocation", R.string.AttachLocation), false);
                        }
                    }
                    break;
                case 3:
                    AboutLinkCell aboutLinkCell = (AboutLinkCell) holder.itemView;
                    if (position == userInfoRow) {
                        aboutLinkCell.setTextAndValue(userInfo.about, LocaleController.getString("UserBio", R.string.UserBio), isBot);
                    } else if (position == channelInfoRow) {
                        String text = chatInfo.about;
                        while (text.contains("\n\n\n")) {
                            text = text.replace("\n\n\n", "\n\n");
                        }
                        aboutLinkCell.setText(text, true);
                    }
                    break;
                case 4:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    if (position == photosRow) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString("SharedPhotosAndVideos", R.string.SharedPhotosAndVideos), String.format("%d", lastMediaCount[MediaDataController.MEDIA_PHOTOVIDEO]), R.drawable.profile_photos, position != sharedSectionRow - 1);
                    } else if (position == filesRow) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage), String.format("%d", lastMediaCount[MediaDataController.MEDIA_FILE]), R.drawable.profile_file, position != sharedSectionRow - 1);
                    } else if (position == linksRow) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString("SharedLinks", R.string.SharedLinks), String.format("%d", lastMediaCount[MediaDataController.MEDIA_URL]), R.drawable.profile_link, position != sharedSectionRow - 1);
                    } else if (position == audioRow) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString("SharedAudioFiles", R.string.SharedAudioFiles), String.format("%d", lastMediaCount[MediaDataController.MEDIA_MUSIC]), R.drawable.profile_audio, position != sharedSectionRow - 1);
                    } else if (position == voiceRow) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString("AudioAutodownload", R.string.AudioAutodownload), String.format("%d", lastMediaCount[MediaDataController.MEDIA_AUDIO]), R.drawable.profile_voice, position != sharedSectionRow - 1);
                    } else if (position == groupsInCommonRow) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString("GroupsInCommonTitle", R.string.GroupsInCommonTitle), String.format("%d", userInfo.common_chats_count), R.drawable.actions_viewmembers, position != sharedSectionRow - 1);
                    } else if (position == settingsTimerRow) {
                        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
                        String value;
                        if (encryptedChat.ttl == 0) {
                            value = LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever);
                        } else {
                            value = LocaleController.formatTTLString(encryptedChat.ttl);
                        }
                        textCell.setTextAndValue(LocaleController.getString("MessageLifetime", R.string.MessageLifetime), value, false);
                    } else if (position == unblockRow) {
                        textCell.setText(LocaleController.getString("Unblock", R.string.Unblock), false);
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteRedText5);
                    } else if (position == startSecretChatRow) {
                        textCell.setText(LocaleController.getString("StartEncryptedChat", R.string.StartEncryptedChat), false);
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteGreenText2);
                    } else if (position == settingsKeyRow) {
                        IdenticonDrawable identiconDrawable = new IdenticonDrawable();
                        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
                        identiconDrawable.setEncryptedChat(encryptedChat);
                        textCell.setTextAndValueDrawable(LocaleController.getString("EncryptionKey", R.string.EncryptionKey), identiconDrawable, false);
                    } else if (position == leaveChannelRow) {
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteRedText5);
                        textCell.setText(LocaleController.getString("LeaveChannel", R.string.LeaveChannel), false);
                    } else if (position == joinRow) {
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteBlueText2);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                        if (currentChat.megagroup) {
                            textCell.setText(LocaleController.getString("ProfileJoinGroup", R.string.ProfileJoinGroup), false);
                        } else {
                            textCell.setText(LocaleController.getString("ProfileJoinChannel", R.string.ProfileJoinChannel), false);
                        }
                    } else if (position == subscribersRow) {
                        if (chatInfo != null) {
                            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                                textCell.setTextAndValueAndIcon(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers), String.format("%d", chatInfo.participants_count), R.drawable.actions_viewmembers, position != membersSectionRow - 1);
                            } else {
                                textCell.setTextAndValueAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", chatInfo.participants_count), R.drawable.actions_viewmembers, position != membersSectionRow - 1);
                            }
                        } else {
                            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                                textCell.setTextAndIcon(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers), R.drawable.actions_viewmembers, position != membersSectionRow - 1);
                            } else {
                                textCell.setTextAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), R.drawable.actions_viewmembers, position != membersSectionRow - 1);
                            }
                        }
                    } else if (position == administratorsRow) {
                        if (chatInfo != null) {
                            textCell.setTextAndValueAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), String.format("%d", chatInfo.admins_count), R.drawable.actions_addadmin, position != membersSectionRow - 1);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), R.drawable.actions_addadmin, position != membersSectionRow - 1);
                        }
                    } else if (position == blockedUsersRow) {
                        if (chatInfo != null) {
                            textCell.setTextAndValueAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", Math.max(chatInfo.banned_count, chatInfo.kicked_count)), R.drawable.actions_removed, position != membersSectionRow - 1);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), R.drawable.actions_removed, position != membersSectionRow - 1);
                        }
                    } else if (position == addMemberRow) {
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        if (chat_id > 0) {
                            textCell.setTextAndIcon(LocaleController.getString("AddMember", R.string.AddMember), R.drawable.actions_addmember2, true);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("AddRecipient", R.string.AddRecipient), R.drawable.actions_addmember2, true);
                        }
                    }
                    break;
                case 6:
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == notificationsRow) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        long did;
                        if (dialog_id != 0) {
                            did = dialog_id;
                        } else if (user_id != 0) {
                            did = user_id;
                        } else {
                            did = -chat_id;
                        }

                        boolean enabled = false;
                        boolean custom = preferences.getBoolean("custom_" + did, false);
                        boolean hasOverride = preferences.contains("notify2_" + did);
                        int value = preferences.getInt("notify2_" + did, 0);
                        int delta = preferences.getInt("notifyuntil_" + did, 0);
                        String val;
                        if (value == 3 && delta != Integer.MAX_VALUE) {
                            delta -= ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            if (delta <= 0) {
                                if (custom) {
                                    val = LocaleController.getString("NotificationsCustom", R.string.NotificationsCustom);
                                } else {
                                    val = LocaleController.getString("NotificationsOn", R.string.NotificationsOn);
                                }
                                enabled = true;
                            } else if (delta < 60 * 60) {
                                val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60));
                            } else if (delta < 60 * 60 * 24) {
                                val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", (int) Math.ceil(delta / 60.0f / 60)));
                            } else if (delta < 60 * 60 * 24 * 365) {
                                val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", (int) Math.ceil(delta / 60.0f / 60 / 24)));
                            } else {
                                val = null;
                            }
                        } else {
                            if (value == 0) {
                                if (hasOverride) {
                                    enabled = true;
                                } else {
                                    enabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(did);
                                }
                            } else if (value == 1) {
                                enabled = true;
                            } else if (value == 2) {
                                enabled = false;
                            } else {
                                enabled = false;
                            }
                            if (enabled && custom) {
                                val = LocaleController.getString("NotificationsCustom", R.string.NotificationsCustom);
                            } else {
                                val = enabled ? LocaleController.getString("NotificationsOn", R.string.NotificationsOn) : LocaleController.getString("NotificationsOff", R.string.NotificationsOff);
                            }
                        }
                        if (val == null) {
                            val = LocaleController.getString("NotificationsOff", R.string.NotificationsOff);
                        }
                        checkCell.setTextAndValueAndCheck(LocaleController.getString("Notifications", R.string.Notifications), val, enabled, false);
                    }
                    break;
                case 7:
                    View sectionCell = holder.itemView;
                    sectionCell.setTag(position);
                    Drawable drawable;
                    if (position == infoSectionRow && sharedSectionRow == -1 && lastSectionRow == -1 && settingsSectionRow == -1 || position == settingsSectionRow && sharedSectionRow == -1 || position == sharedSectionRow && lastSectionRow == -1 || position == lastSectionRow || position == membersSectionRow && lastSectionRow == -1 && (sharedSectionRow == -1 || membersSectionRow > sharedSectionRow)) {
                        drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                    } else {
                        drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    }
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    sectionCell.setBackgroundDrawable(combinedDrawable);
                    break;
                case 8:
                    UserCell userCell = (UserCell) holder.itemView;
                    TLRPC.ChatParticipant part;
                    if (!sortedUsers.isEmpty()) {
                        part = chatInfo.participants.participants.get(sortedUsers.get(position - membersStartRow));
                    } else {
                        part = chatInfo.participants.participants.get(position - membersStartRow);
                    }
                    if (part != null) {
                        String role;
                        if (part instanceof TLRPC.TL_chatChannelParticipant) {
                            TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                            if (!TextUtils.isEmpty(channelParticipant.rank)) {
                                role = channelParticipant.rank;
                            } else {
                                if (channelParticipant instanceof TLRPC.TL_channelParticipantCreator) {
                                    role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                                } else if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin) {
                                    role = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                                } else {
                                    role = null;
                                }
                            }
                        } else {
                            if (part instanceof TLRPC.TL_chatParticipantCreator) {
                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (part instanceof TLRPC.TL_chatParticipantAdmin) {
                                role = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                            } else {
                                role = null;
                            }
                        }
                        userCell.setAdminRole(role);
                        userCell.setData(MessagesController.getInstance(currentAccount).getUser(part.user_id), null, null, 0, position != membersEndRow - 1);
                    }
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != 1 && type != 5 && type != 7 && type != 9 && type != 10 && type != 11;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == infoHeaderRow || i == sharedHeaderRow || i == membersHeaderRow) {
                return 1;
            } else if (i == phoneRow || i == usernameRow || i == locationRow) {
                return 2;
            } else if (i == userInfoRow || i == channelInfoRow) {
                return 3;
            } else if (i == settingsTimerRow || i == settingsKeyRow || i == photosRow || i == filesRow ||
                    i == linksRow || i == audioRow || i == voiceRow || i == groupsInCommonRow ||
                    i == startSecretChatRow || i == subscribersRow || i == administratorsRow || i == blockedUsersRow ||
                    i == leaveChannelRow || i == addMemberRow || i == joinRow || i == unblockRow) {
                return 4;
            } else if (i == notificationsDividerRow) {
                return 5;
            } else if (i == notificationsRow) {
                return 6;
            } else if (i == infoSectionRow || i == sharedSectionRow || i == lastSectionRow || i == membersSectionRow || i == settingsSectionRow) {
                return 7;
            } else if (i >= membersStartRow && i < membersEndRow) {
                return 8;
            } else if (i == emptyRow) {
                return 11;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(topView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue),
                new ThemeDescription(nameTextView[1], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_profile_title),
                new ThemeDescription(nameTextView[1], ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(onlineTextView[1], ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_profile_status),
                new ThemeDescription(onlineTextView[1], ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_avatar_subtitleInProfileBlue),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{avatarDrawable}, null, Theme.key_avatar_backgroundInProfileBlue),

                new ThemeDescription(writeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(writeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_profile_actionBackground),
                new ThemeDescription(writeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_profile_actionPressedBackground),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton),
                new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon),

                new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{UserCell.class}, new String[]{"adminTextView"}, null, null, null, Theme.key_profile_creatorIcon),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_undo_background),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AboutLinkCell.class}, Theme.profile_aboutTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{AboutLinkCell.class}, Theme.profile_aboutTextPaint, null, null, Theme.key_windowBackgroundWhiteLinkText),
                new ThemeDescription(listView, 0, new Class[]{AboutLinkCell.class}, Theme.linkSelectionPaint, null, null, Theme.key_windowBackgroundWhiteLinkSelection),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(nameTextView[1], 0, null, null, new Drawable[]{Theme.profile_verifiedCheckDrawable}, null, Theme.key_profile_verifiedCheck),
                new ThemeDescription(nameTextView[1], 0, null, null, new Drawable[]{Theme.profile_verifiedDrawable}, null, Theme.key_profile_verifiedBackground),
        };
    }
}
