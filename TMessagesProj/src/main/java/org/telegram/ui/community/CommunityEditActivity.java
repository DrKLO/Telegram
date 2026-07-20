package org.telegram.ui.community;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.messenger.utils.GradientProtectionDrawable;
import org.telegram.messenger.utils.TextWatcherImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_communities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatUsersActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class CommunityEditActivity extends BaseFragment implements ImageUpdater.ImageUpdaterDelegate,
        NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {
    private static final int ROW_ID_HEADER = 140;
    private static final int ROW_ID_SET_PHOTO = 141;
    private static final int ROW_ID_ADMINS = 142;
    private static final int ROW_ID_PENDING_REQUESTS = 143;
    private static final int ROW_ID_REMOVED_USERS = 144;
    private static final int ROW_ID_DELETE_COMMUNITY = 145;
    private static final int ROW_ID_ADD_CHAT = 146;

    private static final int ROW_ID_ADD_ALL_MEMBERS = 150;
    private static final int ROW_ID_ADD_ONLY_ADMINS = 151;

    private final BoolAnimator animatorDoneVisible = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320L);
    private long communityId;

    private FrameLayout containerView;
    private UniversalRecyclerView listView;
    private String communityNameOriginal;
    private boolean canAllManageLinkedPeersOriginal;
    private boolean canAllManageLinkedPeers;
    private EditTextCell editTextCell;
    private CommunityHeaderView communityHeaderView;

    private View avatarOverlay;
    private BackupImageView avatarImage;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private ImageUpdater imageUpdater;
    private TLRPC.FileLocation avatar;
    private TextView doneItem;
    private View fadeView;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;

    public CommunityEditActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        communityId = arguments.getLong("community_id", 0);
        currentChat = getMessagesController().getChat(communityId);
        canAllManageLinkedPeers = canAllManageLinkedPeersOriginal = currentChat != null
            && (currentChat.default_banned_rights == null
            || !currentChat.default_banned_rights.manage_linked_peers);

        info = getMessagesController().getChatFull(communityId);

        imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_COMMUNITY, true);
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);

        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);

        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setAddToContainer(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        BlurredBackgroundSourceColor sourceColor = new BlurredBackgroundSourceColor();
        sourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(sourceColor);
        actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider));
        actionBar.setGlassOnlyBack();

        containerView = new FrameLayout(context) {
            final GradientProtectionDrawable gradientProtectionDrawable = new GradientProtectionDrawable(WindowInsetsCompat.Side.TOP);
            final GradientProtectionDrawable gradientProtectionDrawableBottom = new GradientProtectionDrawable(WindowInsetsCompat.Side.BOTTOM);

            @Override
            protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                final boolean result =  super.drawChild(canvas, child, drawingTime);
                if (child == listView) {
                    gradientProtectionDrawable.setColor(getThemedColor(Theme.key_windowBackgroundGray));
                    gradientProtectionDrawable.setBounds(0, 0, getWidth(), AndroidUtilities.statusBarHeight);
                    gradientProtectionDrawable.draw(canvas);

                    float navbarAlpha = AndroidUtilities.getNavigationBarThirdButtonsFactor(AndroidUtilities.navigationBarHeight);
                    if (navbarAlpha > 0) {
                        gradientProtectionDrawableBottom.setAlpha((int) (navbarAlpha * 255));
                        gradientProtectionDrawableBottom.setColor(getThemedColor(Theme.key_windowBackgroundGray));
                        gradientProtectionDrawableBottom.setBounds(0, getHeight() - AndroidUtilities.navigationBarHeight, getWidth(), getHeight());
                        gradientProtectionDrawableBottom.draw(canvas);
                    }
                }
                return result;
            }
        };
        containerView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        avatarDrawable = new AvatarDrawable(currentChat);
        communityHeaderView = new CommunityHeaderView(context, resourceProvider);
        communityHeaderView.avatarView.setForUserOrChat(currentChat, avatarDrawable);
        avatarImage = communityHeaderView.avatarView;

        final String name = DialogObject.getName(currentChat);
        communityNameOriginal = name;

        editTextCell = new EditTextCell(context, resourceProvider);
        editTextCell.textView.setText(name);
        editTextCell.textView.setSelection(name.length());
        editTextCell.textView.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
                checkSaveButtonVisible();
            }
        });

        doneItem = new TextView(context) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                canvas.drawRoundRect(0, getHeight() / 2f - dp(14),
                        getWidth(), getHeight() / 2f + dp(14), dp(14), dp(14), p);
                super.onDraw(canvas);
            }
        };
        doneItem.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        doneItem.setText(getString(R.string.Save));
        doneItem.setTypeface(AndroidUtilities.bold());
        doneItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneItem.setGravity(Gravity.CENTER);
        doneItem.setVisibility(View.GONE);
        doneItem.setPadding(dp(12), 0, dp(12), 0);
        doneItem.setOnClickListener(v -> {
            processDone();
            finishFragment();
        });
        ScaleStateListAnimator.apply(doneItem);
        actionBar.addView(doneItem, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 12, 0));

        avatarOverlay = new View(context) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                    paint.setColor(0x55000000);
                    paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(20), dp(20), paint);
                }
            }
        };

        communityHeaderView.addView(avatarOverlay, LayoutHelper.createFrame(72, 72, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 28));

        avatarProgressView = new RadialProgressView(context);
        avatarProgressView.setSize(dp(30));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarProgressView.setNoProgress(false);
        communityHeaderView.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 32));


        showAvatarProgress(false, false);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setClipToPadding(false);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        actionBar.setBackground(null);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        ViewCompat.setOnApplyWindowInsetsListener(containerView, this::onApplyWindowInsets);

        return fragmentView = containerView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustomShadow(ROW_ID_HEADER, communityHeaderView));

        if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO)) {
            items.add(UItem.asButton(ROW_ID_SET_PHOTO, R.drawable.outline_profile_photo, getString(ChatObject.hasPhoto(currentChat) ?
                    R.string.CommunitySettingsChangePhoto :
                    R.string.CommunitySettingsSetPhoto
            )).accent());
            items.add(UItem.asSpace(2, dp(14)));
            items.add(UItem.asHeader(0, getString(R.string.CommunitySectionCommunityName)));
            items.add(UItem.asCustom(7, editTextCell));
            items.add(UItem.asSpace(1, dp(14)));
        }

        if (ChatObject.canBlockUsers(currentChat)) {
            items.add(UItem.asHeader(3, getString(R.string.CommunitySectionWhoCanAddChats)));
            items.add(UItem.asRadio2(ROW_ID_ADD_ALL_MEMBERS,
                    getString(R.string.CommunityWhoCanAddChatsAllMembers),
                    getString(R.string.CommunityWhoCanAddChatsAllMembersInfo)
            ).setChecked(canAllManageLinkedPeers));
            items.add(UItem.asRadio2(ROW_ID_ADD_ONLY_ADMINS,
                    getString(R.string.CommunityWhoCanAddChatsOnlyAdmins),
                    getString(R.string.CommunityWhoCanAddChatsOnlyAdminsInfo)
            ).setChecked(!canAllManageLinkedPeers));
            items.add(UItem.asSpace(4, dp(14)));
        }

        if (ChatObject.hasAdminRights(currentChat)) {
            items.add(UItem.asButton(ROW_ID_ADMINS, R.drawable.msg_admins, getString(R.string.CommunityAdministrators), info != null ? Integer.toString(info.admins_count) : ""));
            items.add(UItem.asButton(ROW_ID_PENDING_REQUESTS, R.drawable.community_requests_outline_24, getString(R.string.CommunityPendingRequests), info != null ? Integer.toString(info.requests_pending) : ""));
            items.add(UItem.asButton(ROW_ID_REMOVED_USERS, R.drawable.msg_user_remove, getString(R.string.CommunityRemovedUsers), info != null ? Integer.toString(info.kicked_count) : ""));
        }

        items.add(UItem.asSpace(5, dp(14)));
        items.add(UItem.asButton(ROW_ID_ADD_CHAT, R.drawable.msg_groups_create, getString(R.string.CommunityMenuAddChat)).accent());
        if (info != null && info.linked_peers != null) {
            for (TL_communities.CommunityPeer communityPeer : info.linked_peers) {
                final long peerId = DialogObject.getPeerDialogId(communityPeer.peer);
                items.add(UItem.asProfileCell(getMessagesController().getUserOrChat(peerId)));
            }
        }

        //items.add(UItem.asButton(ROW_ID_DELETE_COMMUNITY, getString(R.string.CommunityDelete)).red());
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        final int id = item.id;
        if (id == ROW_ID_HEADER) {
            if (imageUpdater.isUploadingImage()) {
                return;
            }

            TLRPC.Chat chat = getMessagesController().getChat(communityId);
            if (chat.photo != null && chat.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(CommunityEditActivity.this);
                if (chat.photo.dc_id != 0) {
                    chat.photo.photo_big.dc_id = chat.photo.dc_id;
                }
                ImageLocation videoLocation;
                if (info != null && (info.chat_photo instanceof TLRPC.TL_photo) && !info.chat_photo.video_sizes.isEmpty()) {
                    videoLocation = ImageLocation.getForPhoto(info.chat_photo.video_sizes.get(0), info.chat_photo);
                } else {
                    videoLocation = null;
                }
                PhotoViewer.getInstance().openPhotoWithVideo(chat.photo.photo_big, videoLocation, provider);
            }
        } else if (id == ROW_ID_SET_PHOTO) {
            openSetPhotoAlert();
        } else if (id == ROW_ID_ADMINS) {
            Bundle args = new Bundle();
            args.putLong("chat_id", communityId);
            args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(info);
            presentFragment(fragment);
        } else if (id == ROW_ID_REMOVED_USERS) {
            Bundle args = new Bundle();
            args.putLong("chat_id", communityId);
            args.putInt("type", ChatUsersActivity.TYPE_BANNED);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(info);
            presentFragment(fragment);
        } else if (id == ROW_ID_PENDING_REQUESTS) {
            Bundle args = new Bundle();
            args.putLong("community_id", communityId);
            presentFragment(new CommunityPendingRequestsActivity(args));
        } else if (id == ROW_ID_ADD_ALL_MEMBERS) {
            setAllowedManageLinkedPeers(true);
        } else if (id == ROW_ID_ADD_ONLY_ADMINS) {
            setAllowedManageLinkedPeers(false);
        } else if (id == ROW_ID_DELETE_COMMUNITY) {
            AlertsCreator.createClearOrDeleteDialogAlert(this, false, currentChat, null, false, true, true, false, (param) -> {
                finishFragment();
                getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, -communityId, null, currentChat, param);
            });
        } else if (id == ROW_ID_ADD_CHAT) {
            CommunityUtils.showChatsToAddToCommunity(progressDialog, this, currentAccount, currentChat);
        } else if (item.object instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) item.object;
            final long dialogId = -chat.id;
            presentFragment(ChatActivity.of(dialogId));
        } else if (item.object instanceof TLRPC.User) {
            final TLRPC.User user = (TLRPC.User) item.object;
            final long dialogId = user.id;
            presentFragment(ChatActivity.of(dialogId));
        }
    }

    private final AlertDialog[] progressDialog = new AlertDialog[1];

    private void setAllowedManageLinkedPeers(boolean allowed) {
        if (canAllManageLinkedPeers == allowed) {
            return;
        }

        final RadioButtonCell cell = (RadioButtonCell) listView.findViewByItemId(ROW_ID_ADD_ONLY_ADMINS);
        if (cell != null) {
            cell.setChecked(!allowed, true);
        }
        final RadioButtonCell cell2 = (RadioButtonCell) listView.findViewByItemId(ROW_ID_ADD_ALL_MEMBERS);
        if (cell2 != null) {
            cell2.setChecked(allowed, true);
        }

        canAllManageLinkedPeers = allowed;
        checkSaveButtonVisible();
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        final long dialogId;
        final boolean isChannel;
        final boolean isBot;
        final boolean canRemoveChatFromCommunity;

        if (item.object instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) item.object;
            dialogId = -chat.id;
            isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
            isBot = false;
            canRemoveChatFromCommunity = ChatObject.canRemoveChatFromCommunity(chat, currentChat);
        } else if (item.object instanceof TLRPC.User) {
            final TLRPC.User user = (TLRPC.User) item.object;
            dialogId = user.id;
            isChannel = false;
            isBot = UserObject.isBot(user);
            canRemoveChatFromCommunity = ChatObject.canRemoveBotFromCommunity(user, currentChat);
        } else {
            return false;
        }

        final CommunityChatType communityChatType = CommunityUtils.getCommunityChatType(currentAccount, dialogId);
        final boolean canViewChat = communityChatType == CommunityChatType.YouAreIn || communityChatType == CommunityChatType.YouCanView;
        if (!canRemoveChatFromCommunity && !canViewChat) {
            return false;
        }

        final ItemOptions io = ItemOptions.makeOptions(containerView, view);
        io.addIf(canViewChat, R.drawable.msg_viewintopic, getString(isBot ?
            R.string.CommunityMenuViewBot : isChannel ?
            R.string.CommunityMenuViewChannel :
            R.string.CommunityMenuViewGroup
        ), () -> presentFragment(ChatActivity.of(dialogId)));
        io.addIf(canRemoveChatFromCommunity, R.drawable.msg_cancel, getString(R.string.CommunityMenuRemoveFromCommunity), true, () -> {
            AlertsCreator.showSimpleConfirmAlert(this,
                getString(R.string.CommunityMenuRemoveFromCommunity),
                getString(isBot ?
                    R.string.CommunityMenuRemoveBotFromCommunityConfirm : isChannel ?
                    R.string.CommunityMenuRemoveChannelFromCommunityConfirm :
                    R.string.CommunityMenuRemoveGroupFromCommunityConfirm
                ), getString(R.string.Remove), true, () -> {
                    MessagesController.getInstance(currentAccount).unlinkCommunity(dialogId, communityId, (res, err) -> {
                        if (err != null) {
                            BulletinFactory.of(this).showForError(err);
                        }
                    });
                });
        });

        io.setScrimViewBackground(listView.getClipBackground(view, true));
        io.show();

        return true;

    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        listView.setPadding(0, systemInsets.top, 0, systemInsets.bottom);
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }


    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
    }

    private void processDone() {
        if (currentChat != null && !currentChat.title.equals(editTextCell.getText())) {
            getMessagesController().changeChatTitle(currentChat.id, editTextCell.getText(), () ->
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT));
        }

        if (currentChat != null && canAllManageLinkedPeers != canAllManageLinkedPeersOriginal) {
            if (currentChat.default_banned_rights == null) {
                currentChat.default_banned_rights = new TLRPC.TL_chatBannedRights();
            }
            currentChat.default_banned_rights.manage_linked_peers = !canAllManageLinkedPeers;
            getMessagesController().setDefaultBannedRole(communityId,
                    currentChat.default_banned_rights, false, this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        imageUpdater.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        imageUpdater.onPause();
    }

    @Override
    public void dismissCurrentDialog() {
        if (imageUpdater.dismissCurrentDialog(visibleDialog)) {
            return;
        }
        super.dismissCurrentDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return imageUpdater.dismissDialogOnPause(dialog) && super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        imageUpdater.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        imageUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (imageUpdater != null && imageUpdater.currentPicturePath != null) {
            args.putString("path", imageUpdater.currentPicturePath);
        }
        if (editTextCell != null) {
            String text = editTextCell.getText();
            if (!text.isEmpty()) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (imageUpdater != null) {
            imageUpdater.currentPicturePath = args.getString("path");
        }
    }

    public void openSetPhotoAlert() {
        imageUpdater.openMenu(avatar != null, () -> {
            avatar = null;
            MessagesController.getInstance(currentAccount).changeChatAvatar(communityId, null, null, null, null, 0, null, null, null, null);
            showAvatarProgress(false, true);
            avatarImage.setImage(null, null, avatarDrawable, currentChat);
        }, dialogInterface -> {}, 0);
    }







    @Override
    public void onUploadProgressChanged(float progress) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(progress);
    }

    @Override
    public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(0.0f);
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            avatar = smallSize.location;
            if (photo != null || video != null || emojiMarkup != null) {
                getMessagesController().changeChatAvatar(communityId, null, photo, video, emojiMarkup, videoStartTimestamp, videoPath, smallSize.location, bigSize.location, null);
                showAvatarProgress(false, true);
            } else {
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, currentChat);
                showAvatarProgress(true, false);
            }
            listView.adapter.update(true);
        });
    }

    @Override
    public String getInitialSearchString() {
        return editTextCell.getText();
    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == communityId) {
                info = chatFull;
                listView.adapter.update(true);
            }
        }
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        doneItem.setAlpha(factor);
        doneItem.setScaleX(lerp(0.75f, 1f, factor));
        doneItem.setScaleY(lerp(0.75f, 1f, factor));
        doneItem.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }

    private static class EditTextCell extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;
        public EditTextBoldCursor textView;

        public EditTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            textView = new EditTextBoldCursor(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!isEnabled()) {
                        return false;
                    }
                    return super.onTouchEvent(event);
                }
            };
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setMaxLines(Integer.MAX_VALUE);
            textView.setBackground(null);
            textView.setImeOptions(textView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            textView.setInputType(textView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
            textView.setPadding(dp(4), dp(10), dp(4), dp(11));
            textView.setMinHeight(dp(50));

            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,  13, 0, 13, 0));
        }

        public String getText() {
            return textView.getText().toString();
        }
    }

    private static class CommunityHeaderView extends FrameLayout implements Theme.Colorable {
        public final BackupImageView avatarView;
        private final Theme.ResourcesProvider resourcesProvider;
        private static final int AVATAR_SIZE = 72;

        public CommunityHeaderView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(20));
            addView(avatarView, LayoutHelper.createFrame(AVATAR_SIZE, AVATAR_SIZE,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 28));

            updateColors();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            DrawableUtils.drawCommunityCardDrawable(canvas, Theme.dialogs_communityCardsDrawable, avatarView.getLeft() + avatarView.getWidth() / 2f, avatarView.getTop() + avatarView.getHeight() / 2f, avatarView.getHeight());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(136), MeasureSpec.EXACTLY));
        }

        @Override
        public void updateColors() {

        }
    }

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (fileLocation == null) {
                return null;
            }

            TLRPC.FileLocation photoBig = null;
            TLRPC.Chat chat = getMessagesController().getChat(communityId);
            if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                photoBig = chat.photo.photo_big;
            }

            if (photoBig != null && photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int[] coords = new int[2];
                avatarImage.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = avatarImage;
                object.imageReceiver = avatarImage.getImageReceiver();
                object.dialogId = -communityId;
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.size = -1;
                object.radius = avatarImage.getImageReceiver().getRoundRadius(true);
                object.scale = 1;
                object.canEdit = true;
                return object;
            }
            return null;
        }

        @Override
        public void willHidePhotoViewer() {
            avatarImage.getImageReceiver().setVisible(true, true);
        }

        @Override
        public void openPhotoForEdit(String file, String thumb, boolean isVideo) {
            imageUpdater.openPhotoForEdit(file, thumb, 0, isVideo);
        }

        @Override
        public boolean onDeletePhoto(int index) {
            return true;
        }

        @Override
        public int getTotalImageCount() {
            return 1;
        }

        @Override
        public boolean canLoadMoreAvatars() {
            return false;
        }
    };

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarProgressView == null) {
            return;
        }
        if (avatarAnimation != null) {
            avatarAnimation.cancel();
            avatarAnimation = null;
        }
        if (animated) {
            avatarAnimation = new AnimatorSet();
            if (show) {
                avatarProgressView.setVisibility(View.VISIBLE);
                avatarOverlay.setVisibility(View.VISIBLE);
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 1.0f));
            } else {
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarProgressView == null) {
                        return;
                    }
                    if (!show) {
                        avatarProgressView.setVisibility(View.INVISIBLE);
                        avatarOverlay.setVisibility(View.INVISIBLE);
                    }
                    avatarAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    avatarAnimation = null;
                }
            });
            avatarAnimation.start();
        } else {
            if (show) {
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
                avatarOverlay.setAlpha(1.0f);
                avatarOverlay.setVisibility(View.VISIBLE);
            } else {
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
                avatarOverlay.setAlpha(0.0f);
                avatarOverlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void checkSaveButtonVisible() {
        final boolean visible = (canAllManageLinkedPeersOriginal != canAllManageLinkedPeers)
            || !TextUtils.equals(editTextCell.getText(), communityNameOriginal);

        animatorDoneVisible.setValue(visible, true);
    }
}
