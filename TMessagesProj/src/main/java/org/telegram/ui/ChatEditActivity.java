/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.Reactions.ChatCustomReactionsEditActivity;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Stars.BotStarsActivity;
import org.telegram.ui.Stars.BotStarsController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ChatEditActivity extends BaseFragment implements ImageUpdater.ImageUpdaterDelegate, NotificationCenter.NotificationCenterDelegate {

    private View doneButton;

    private AlertDialog progressDialog;

    private UndoView undoView;
    private LinearLayout avatarContainer;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private ImageUpdater imageUpdater;
    private EditTextEmoji nameTextView;
    private LinearLayout linearLayout;

    private LinearLayout settingsContainer;
    private EditTextBoldCursor descriptionTextView;

    private LinearLayout typeEditContainer;
    private ShadowSectionCell settingsTopSectionCell;
    private TextCell locationCell;
    private TextCell typeCell;
    private TextCell linkedCell;
    private PeerColorActivity.ChangeNameColorCell colorCell;
    private TextCell historyCell;
    private TextCell reactionsCell;
    private TextInfoPrivacyCell settingsSectionCell;

//    private TextCell signCell;
    private TextCell forumsCell;

    private FrameLayout stickersContainer;
    private TextCell stickersCell;
    private TextInfoPrivacyCell stickersInfoCell;

    private LinearLayout infoContainer;
    private TextCell membersCell;
    private TextCell memberRequestsCell;
    private TextCell inviteLinksCell;
    private TextCell adminCell;
    private TextCell blockCell;
    private TextCell logCell;
    private TextCell statsAndBoosts;
    private TextCell setAvatarCell;
    private ShadowSectionCell infoSectionCell;

    private FrameLayout deleteContainer;
    private TextSettingsCell deleteCell;
    private ShadowSectionCell deleteInfoCell;

    private TextCell publicLinkCell;
    private TextCell balanceCell;
    private TextCell editIntroCell;
    private TextCell editCommandsCell;
    private TextCell changeBotSettingsCell;
    private TextInfoPrivacyCell botInfoCell;

    private TLRPC.FileLocation avatar;

    private long chatId;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;

    private long userId;
    private TLRPC.User currentUser;
    private TLRPC.UserFull userInfo;

//    private boolean signMessages;
    private boolean forum, canForum;

    private boolean isChannel;

    private boolean historyHidden;
    private TLRPC.ChatReactions availableReactions;
    private TL_stories.TL_premium_boostsStatus boostsStatus;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    private boolean hasUploadedPhoto;
    private final List<AnimatedEmojiDrawable> preloadedReactions = new ArrayList<>();

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (fileLocation == null) {
                return null;
            }

            TLRPC.FileLocation photoBig = null;
            if (currentUser != null) {
                TLRPC.User user = userId == 0 ? null : getMessagesController().getUser(userId);
                if (user != null && user.photo != null && user.photo.photo_big != null) {
                    photoBig = user.photo.photo_big;
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(chatId);
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
                object.dialogId = userId != 0 ? userId : -chatId;
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.size = -1;
                object.radius = avatarImage.getImageReceiver().getRoundRadius(true);
                object.scale = avatarContainer.getScaleX();
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
            if (userId == 0) {
                return true;
            }
            TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
            req.bot = getMessagesController().getInputUser(userId);
            req.flags |= 2;
            req.id = new TLRPC.TL_inputPhotoEmpty();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                avatarImage.setImageDrawable(avatarDrawable);
                setAvatarCell.setTextAndIcon(getString("ChatSetPhotoOrVideo", R.string.ChatSetPhotoOrVideo), R.drawable.msg_addphoto, true);

                if (currentUser != null) {
                    currentUser.photo = null;
                    getMessagesController().putUser(currentUser, true);
                }
                hasUploadedPhoto = true;

                if (cameraDrawable == null) {
                    cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, dp(50), dp(50), false, null);
                }
                setAvatarCell.imageView.setTranslationX(-dp(8));
                setAvatarCell.imageView.setAnimation(cameraDrawable);
            }));

            return false;
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

    public ChatEditActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        chatId = args.getLong("chat_id", 0);
        userId = args.getLong("user_id", 0);

        if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            imageUpdater = new ImageUpdater(true, chat != null && ChatObject.isChannelAndNotMegaGroup(chat) ? ImageUpdater.FOR_TYPE_CHANNEL : ImageUpdater.FOR_TYPE_GROUP, true);
        } else {
            imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, false);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        if (chatId != 0) {
            currentChat = getMessagesController().getChat(chatId);
            if (currentChat == null) {
                currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId);
                if (currentChat != null) {
                    getMessagesController().putChat(currentChat, true);
                } else {
                    return false;
                }
                if (info == null) {
                    info = MessagesStorage.getInstance(currentAccount).loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                    if (info == null) {
                        return false;
                    }
                }
            }
        } else {
            currentUser = userId == 0 ? null : getMessagesController().getUser(userId);
            if (currentUser == null) {
                currentUser = MessagesStorage.getInstance(currentAccount).getUserSync(userId);
                if (currentUser != null) {
                    getMessagesController().putUser(currentUser, true);
                } else {
                    return false;
                }
                if (userInfo == null) {
                    HashSet<Long> set = new HashSet<>();
                    set.add(userId);
                    List<TLRPC.UserFull> fulls = MessagesStorage.getInstance(currentAccount).loadUserInfos(set);
                    if (!fulls.isEmpty()) {
                        userInfo = fulls.get(0);
                    } else {
                        return false;
                    }
                }
            }
        }

        if (currentChat != null) {
            avatarDrawable.setInfo(5, currentChat.title, null);
            isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
//            signMessages = currentChat.signatures;
            forum = currentChat.forum;
            canForum = userId == 0 && (forum || Math.max(info == null ? 0 : info.participants_count, currentChat.participants_count) >= getMessagesController().forumUpgradeParticipantsMin) && (info == null || info.linked_chat_id == 0);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatAvailableReactionsUpdated);
        } else {
            avatarDrawable.setInfo(5, currentUser.first_name, null);
            isChannel = false;
//            signMessages = false;
            forum = false;
            canForum = false;
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
            if (currentUser.bot) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botStarsUpdated);
            }
        }
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);

        if (info != null) {
            loadLinksCount();
        }
        return super.onFragmentCreate();
    }

    private void loadLinksCount() {
        TLRPC.TL_messages_getExportedChatInvites req = new TLRPC.TL_messages_getExportedChatInvites();
        req.peer = getMessagesController().getInputPeer(-chatId);
        req.admin_id = getMessagesController().getInputUser(getUserConfig().getCurrentUser());
        req.limit = 0;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_exportedChatInvites invites = (TLRPC.TL_messages_exportedChatInvites) response;
                info.invitesCount = invites.count;
                getMessagesStorage().saveChatLinksCount(chatId, info.invitesCount);
                updateFields(false, false);
            }
        }));
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        if (currentChat != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatAvailableReactionsUpdated);
        } else {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
            if (currentUser.bot) {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botStarsUpdated);
            }
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nameTextView != null) {
            nameTextView.onResume();
            nameTextView.getEditText().requestFocus();
        }
        updateColorCell();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        updateFields(true, true);
        imageUpdater.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        ReactionsUtils.stopPreloadReactions(preloadedReactions);
        if (nameTextView != null) {
            nameTextView.onPause();
        }
        if (undoView != null) {
            undoView.hide(true, 0);
        }
        imageUpdater.onPause();
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
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
    public boolean onBackPressed() {
        if (nameTextView != null && nameTextView.isPopupShowing()) {
            nameTextView.hidePopup(true);
            return false;
        }
        return checkDiscard();
    }

    @Override
    public View createView(Context context) {
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = measureKeyboardHeight();
                if (keyboardSize > dp(20)) {
                    ignoreLayout = true;
                    nameTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (nameTextView != null && nameTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom = keyboardSize <= dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? nameTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (nameTextView != null && nameTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        sizeNotifierFrameLayout.setOnTouchListener((v, event) -> true);
        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        sizeNotifierFrameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout1 = linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout1, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout1.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle(getString("ChannelEdit", R.string.ChannelEdit));

        avatarContainer = new LinearLayout(context);
        avatarContainer.setOrientation(LinearLayout.VERTICAL);
        avatarContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(avatarContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout frameLayout = new FrameLayout(context);
        avatarContainer.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context) {
            @Override
            public void invalidate() {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate();
            }

            @Override
            public void invalidate(int l, int t, int r, int b) {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate(l, t, r, b);
            }
        };
        avatarImage.setRoundRadius(forum ? dp(16) : dp(32));

        if (currentUser != null || ChatObject.canChangeChatInfo(currentChat)) {
            frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0x55000000);

            avatarOverlay = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                        paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                        canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                    }
                }
            };
            frameLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

            avatarProgressView = new RadialProgressView(context);
            avatarProgressView.setSize(dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            avatarProgressView.setNoProgress(false);
            frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

            showAvatarProgress(false, false);

            avatarContainer.setOnClickListener(v -> {
                if (imageUpdater.isUploadingImage()) {
                    return;
                }
                TLRPC.User user = userId == 0 ? null : getMessagesController().getUser(userId);
                if (user != null) {
                    if (user.photo != null && user.photo.photo_big != null) {
                        PhotoViewer.getInstance().setParentActivity(ChatEditActivity.this);
                        if (user.photo.dc_id != 0) {
                            user.photo.photo_big.dc_id = user.photo.dc_id;
                        }
                        PhotoViewer.getInstance().openPhoto(user.photo.photo_big, provider);
                    }
                    return;
                }

                TLRPC.Chat chat = getMessagesController().getChat(chatId);
                if (chat.photo != null && chat.photo.photo_big != null) {
                    PhotoViewer.getInstance().setParentActivity(ChatEditActivity.this);
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
            });
        } else {
            frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));
        }

        nameTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false);
        if (userId != 0) {
            nameTextView.setHint(getString(R.string.BotName));
        } else if (isChannel) {
            nameTextView.setHint(getString("EnterChannelName", R.string.EnterChannelName));
        } else {
            nameTextView.setHint(getString("GroupName", R.string.GroupName));
        }
        nameTextView.setEnabled(currentChat != null || ChatObject.canChangeChatInfo(currentChat));
        nameTextView.setFocusable(nameTextView.isEnabled());
        nameTextView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                avatarDrawable.setInfo(5, nameTextView.getText().toString(), null);
                if (avatarImage != null) {
                    avatarImage.invalidate();
                }
            }
        });
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(128);
        nameTextView.setFilters(inputFilters);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));

        settingsContainer = new LinearLayout(context);
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(settingsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentUser != null || ChatObject.canChangeChatInfo(currentChat)) {
            setAvatarCell = new TextCell(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    canvas.drawLine(LocaleController.isRTL ? 0 : dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            };
            setAvatarCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            setAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            setAvatarCell.setOnClickListener(v -> {
                imageUpdater.openMenu(avatar != null, () -> {
                    avatar = null;
                    if (userId == 0) {
                        MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, null, null, null, null, 0, null, null, null, null);
                    } else {
                        TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
                        req.bot = getMessagesController().getInputUser(userId);
                        req.flags |= 2;
                        req.id = new TLRPC.TL_inputPhotoEmpty();
                        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            avatarImage.setImageDrawable(avatarDrawable);
                            setAvatarCell.setTextAndIcon(getString("ChatSetPhotoOrVideo", R.string.ChatSetPhotoOrVideo), R.drawable.msg_addphoto, true);

                            if (currentUser != null) {
                                currentUser.photo = null;
                                getMessagesController().putUser(currentUser, true);
                            }
                            hasUploadedPhoto = true;

                            if (cameraDrawable == null) {
                                cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, dp(50), dp(50), false, null);
                            }
                            setAvatarCell.imageView.setTranslationX(-dp(8));
                            setAvatarCell.imageView.setAnimation(cameraDrawable);
                        }));
                    }
                    showAvatarProgress(false, true);
                    avatarImage.setImage(null, null, avatarDrawable, currentUser != null ? currentUser : currentChat);
                    cameraDrawable.setCurrentFrame(0);
                    setAvatarCell.imageView.playAnimation();
                }, dialogInterface -> {
                    if (!imageUpdater.isUploadingImage()) {
                        cameraDrawable.setCustomEndFrame(86);
                        setAvatarCell.imageView.playAnimation();
                    } else {
                        cameraDrawable.setCurrentFrame(0, false);
                    }

                }, 0);
                cameraDrawable.setCurrentFrame(0);
                cameraDrawable.setCustomEndFrame(43);
                setAvatarCell.imageView.playAnimation();
            });
            settingsContainer.addView(setAvatarCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        descriptionTextView = new EditTextBoldCursor(context);
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        descriptionTextView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        descriptionTextView.setPadding(0, 0, 0, dp(6));
        descriptionTextView.setBackgroundDrawable(null);
        descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        descriptionTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        descriptionTextView.setEnabled(currentUser != null || ChatObject.canChangeChatInfo(currentChat));
        descriptionTextView.setFocusable(descriptionTextView.isEnabled());
        inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(255);
        descriptionTextView.setFilters(inputFilters);
        descriptionTextView.setHint(getString("DescriptionOptionalPlaceholder", R.string.DescriptionOptionalPlaceholder));
        descriptionTextView.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        descriptionTextView.setCursorSize(dp(20));
        descriptionTextView.setCursorWidth(1.5f);
        if (descriptionTextView.isEnabled()) {
            settingsContainer.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 23, 15, 23, 9));
        } else {
            settingsContainer.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 23, 12, 23, 6));
        }
        descriptionTextView.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                doneButton.performClick();
                return true;
            }
            return false;
        });
        descriptionTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        settingsTopSectionCell = new ShadowSectionCell(context);
        linearLayout1.addView(settingsTopSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        typeEditContainer = new LinearLayout(context);
        typeEditContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayout1.addView(typeEditContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentChat != null) {
            if (currentChat.megagroup && (info == null || info.can_set_location)) {
                locationCell = new TextCell(context);
                locationCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                typeEditContainer.addView(locationCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                locationCell.setOnClickListener(v -> {
                    if (!AndroidUtilities.isMapsInstalled(ChatEditActivity.this)) {
                        return;
                    }
                    LocationActivity fragment = new LocationActivity(LocationActivity.LOCATION_TYPE_GROUP);
                    fragment.setDialogId(-chatId);
                    if (info != null && info.location instanceof TLRPC.TL_channelLocation) {
                        fragment.setInitialLocation((TLRPC.TL_channelLocation) info.location);
                    }
                    fragment.setDelegate((location, live, notify, scheduleDate) -> {
                        TLRPC.TL_channelLocation channelLocation = new TLRPC.TL_channelLocation();
                        channelLocation.address = location.address;
                        channelLocation.geo_point = location.geo;

                        info.location = channelLocation;
                        info.flags |= 32768;
                        updateFields(false, true);
                        getMessagesController().loadFullChat(chatId, 0, true);
                    });
                    presentFragment(fragment);
                });
            }

            if (currentChat.creator && (info == null || info.can_set_username)) {
                typeCell = new TextCell(context);
                typeCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                typeEditContainer.addView(typeCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                typeCell.setOnClickListener(v -> {
                    ChatEditTypeActivity fragment = new ChatEditTypeActivity(chatId, locationCell != null && locationCell.getVisibility() == View.VISIBLE);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                });
            }

            if (ChatObject.isChannel(currentChat) && (isChannel && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO) || !isChannel && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_PIN))) {
                linkedCell = new TextCell(context);
                linkedCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                typeEditContainer.addView(linkedCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                linkedCell.setOnClickListener(v -> {
                    ChatLinkActivity fragment = new ChatLinkActivity(chatId);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                });
            }

            if (ChatObject.isChannelAndNotMegaGroup(currentChat) && ChatObject.canChangeChatInfo(currentChat)) {
                colorCell = new PeerColorActivity.ChangeNameColorCell(currentAccount, -currentChat.id, context, getResourceProvider());
                colorCell.setBackground(Theme.getSelectorDrawable(true));
                typeEditContainer.addView(colorCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                colorCell.setOnClickListener(v -> {
                    presentFragment(new ChannelColorActivity(-currentChat.id).setOnApplied(this));

                    MessagesController.getInstance(currentAccount).getMainSettings().edit().putInt("boostingappearance",
                        MessagesController.getInstance(currentAccount).getMainSettings().getInt("boostingappearance", 0) + 1
                    ).apply();
                });
            }

            if (!isChannel && ChatObject.canBlockUsers(currentChat) && (ChatObject.isChannel(currentChat) || currentChat.creator)) {
                historyCell = new TextCell(context);
                historyCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                typeEditContainer.addView(historyCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                historyCell.setOnClickListener(v -> {
                    BottomSheet.Builder builder = new BottomSheet.Builder(context);
                    builder.setApplyTopPadding(false);

                    LinearLayout linearLayout = new LinearLayout(context);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);

                    HeaderCell headerCell = new HeaderCell(context, Theme.key_dialogTextBlue2, 23, 15, false);
                    headerCell.setHeight(47);
                    headerCell.setText(getString("ChatHistory", R.string.ChatHistory));
                    linearLayout.addView(headerCell);

                    LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
                    linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
                    linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    RadioButtonCell[] buttons = new RadioButtonCell[2];

                    for (int a = 0; a < 2; a++) {
                        buttons[a] = new RadioButtonCell(context, true);
                        buttons[a].setTag(a);
                        buttons[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                        if (a == 0) {
                            buttons[a].setTextAndValue(getString("ChatHistoryVisible", R.string.ChatHistoryVisible), getString("ChatHistoryVisibleInfo", R.string.ChatHistoryVisibleInfo), true, !historyHidden);
                        } else {
                            if (ChatObject.isChannel(currentChat)) {
                                buttons[a].setTextAndValue(getString("ChatHistoryHidden", R.string.ChatHistoryHidden), getString("ChatHistoryHiddenInfo", R.string.ChatHistoryHiddenInfo), false, historyHidden);
                            } else {
                                buttons[a].setTextAndValue(getString("ChatHistoryHidden", R.string.ChatHistoryHidden), getString("ChatHistoryHiddenInfo2", R.string.ChatHistoryHiddenInfo2), false, historyHidden);
                            }
                        }
                        linearLayoutInviteContainer.addView(buttons[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        buttons[a].setOnClickListener(v2 -> {
                            Integer tag = (Integer) v2.getTag();
                            buttons[0].setChecked(tag == 0, true);
                            buttons[1].setChecked(tag == 1, true);
                            historyHidden = tag == 1;
                            builder.getDismissRunnable().run();
                            updateFields(true, true);
                        });
                    }

                    builder.setCustomView(linearLayout);
                    showDialog(builder.create());
                });
            }

            if (ChatObject.isMegagroup(currentChat) && ChatObject.hasAdminRights(currentChat)) {
                MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(-currentChat.id, boostsStatus -> this.boostsStatus = boostsStatus);
                colorCell = new PeerColorActivity.ChangeNameColorCell(currentAccount, -currentChat.id, context, getResourceProvider());
                colorCell.setBackground(Theme.getSelectorDrawable(true));
                typeEditContainer.addView(colorCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                colorCell.setOnClickListener(v -> {
                    GroupColorActivity activity = new GroupColorActivity(-currentChat.id);
                    activity.boostsStatus = boostsStatus;
                    activity.setOnApplied(this);
                    presentFragment(activity);
                });
            }

            if (isChannel) {
//                signCell = new TextCell(context, 23, false, true, null);
//                signCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
//                signCell.setTextAndCheckAndIcon(getString("ChannelSignMessages", R.string.ChannelSignMessages), signMessages, R.drawable.msg_signed, false);
//                typeEditContainer.addView(signCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
//                signCell.setOnClickListener(v -> {
//                    signMessages = !signMessages;
//                    ((TextCell) v).setChecked(signMessages);
//                });
            } else if (currentChat.creator) {
                forumsCell = new TextCell(context, 23, false, true, null);
                forumsCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                forumsCell.setTextAndCheckAndIcon(getString("ChannelTopics", R.string.ChannelTopics), forum, R.drawable.msg_topics, false);
                forumsCell.getCheckBox().setIcon(canForum ? 0 : R.drawable.permission_locked);
                typeEditContainer.addView(forumsCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                forumsCell.setOnClickListener(v -> {
                    if (!canForum) {
                        CharSequence text;
                        if (!(info == null || info.linked_chat_id == 0)) {
                            text = AndroidUtilities.replaceTags(getString("ChannelTopicsDiscussionForbidden", R.string.ChannelTopicsDiscussionForbidden));
                        } else {
                            text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("ChannelTopicsForbidden", getMessagesController().forumUpgradeParticipantsMin));
                        }
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.topics, text).show();
                        frameLayout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        return;
                    }
                    forum = !forum;
                    avatarImage.animateToRoundRadius(forum ? dp(16) : dp(32));
                    ((TextCell) v).setChecked(forum);
                    updateFields(false, true);
                });
            }

            updateColorCell();
        }

        ActionBarMenu menu = actionBar.createMenu();
        if (currentUser != null || ChatObject.canChangeChatInfo(currentChat) || /*signCell != null ||*/ historyCell != null) {
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, dp(56));
            doneButton.setContentDescription(getString("Done", R.string.Done));
        }

        if (locationCell != null || /*signCell != null ||*/ historyCell != null || typeCell != null || linkedCell != null || forumsCell != null) {
            settingsSectionCell = new TextInfoPrivacyCell(context);
            Drawable shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, getResourceProvider()));
            Drawable background = new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray));
            CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
            combinedDrawable.setFullsize(true);
            settingsSectionCell.setBackground(combinedDrawable);
            if (forumsCell != null) {
                settingsSectionCell.setText(getString("ForumToggleDescription", R.string.ForumToggleDescription));
            } else if (/*signCell != null*/ false) {
                settingsSectionCell.setText(getString("ChannelSignMessagesInfo", R.string.ChannelSignMessagesInfo));
            } else {
                settingsSectionCell.setFixedSize(12);
            }
            linearLayout1.addView(settingsSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(infoContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentChat != null) {
            blockCell = new TextCell(context);
            blockCell.setBackground(Theme.getSelectorDrawable(false));
            blockCell.setVisibility(ChatObject.isChannel(currentChat) || currentChat.creator || ChatObject.hasAdminRights(currentChat) && ChatObject.canChangeChatInfo(currentChat) ? View.VISIBLE : View.GONE);
            blockCell.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong("chat_id", chatId);
                args.putInt("type", !isChannel && !currentChat.gigagroup ? ChatUsersActivity.TYPE_KICKED : ChatUsersActivity.TYPE_BANNED);
                ChatUsersActivity fragment = new ChatUsersActivity(args);
                fragment.setInfo(info);
                presentFragment(fragment);
            });

            inviteLinksCell = new TextCell(context);
            inviteLinksCell.setBackground(Theme.getSelectorDrawable(false));
            inviteLinksCell.setOnClickListener(v -> {
                ManageLinksActivity fragment = new ManageLinksActivity(chatId, 0, 0);
                fragment.setInfo(info, info.exported_invite);
                presentFragment(fragment);
            });

            reactionsCell = new TextCell(context);
            reactionsCell.setBackground(Theme.getSelectorDrawable(false));
            reactionsCell.setOnClickListener(v -> {
                if (ChatObject.isChannelAndNotMegaGroup(currentChat)) {
                    presentFragment(new ChatCustomReactionsEditActivity(chatId, info));
                } else {
                    Bundle args = new Bundle();
                    args.putLong(ChatReactionsEditActivity.KEY_CHAT_ID, chatId);
                    ChatReactionsEditActivity reactionsEditActivity = new ChatReactionsEditActivity(args);
                    reactionsEditActivity.setInfo(info);
                    presentFragment(reactionsEditActivity);
                }
            });

            adminCell = new TextCell(context);
            adminCell.setBackground(Theme.getSelectorDrawable(false));
            adminCell.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong("chat_id", chatId);
                args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
                ChatUsersActivity fragment = new ChatUsersActivity(args);
                fragment.setInfo(info);
                presentFragment(fragment);
            });

            membersCell = new TextCell(context);
            membersCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            membersCell.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong("chat_id", chatId);
                args.putInt("type", ChatUsersActivity.TYPE_USERS);
                ChatUsersActivity fragment = new ChatUsersActivity(args);
                fragment.setInfo(info);
                presentFragment(fragment);
            });

            if (!ChatObject.isChannelAndNotMegaGroup(currentChat)) {
                memberRequestsCell = new TextCell(context);
                memberRequestsCell.setBackground(Theme.getSelectorDrawable(false));
                memberRequestsCell.setOnClickListener(v -> {
                    MemberRequestsActivity activity = new MemberRequestsActivity(chatId);
                    presentFragment(activity);
                });
            }

            if (ChatObject.isChannel(currentChat) || currentChat.gigagroup) {
                logCell = new TextCell(context);
                logCell.setTextAndIcon(LocaleController.getString(R.string.EventLog), R.drawable.msg_log, false);
                logCell.setBackground(Theme.getSelectorDrawable(false));
                logCell.setOnClickListener(v -> presentFragment(new ChannelAdminLogActivity(currentChat)));
            }

            if (ChatObject.isBoostSupported(currentChat)) {
                statsAndBoosts = new TextCell(context);
                statsAndBoosts.setTextAndIcon(getString(R.string.StatisticsAndBoosts), R.drawable.msg_stats, true);
                statsAndBoosts.setBackground(Theme.getSelectorDrawable(false));
                statsAndBoosts.setOnClickListener(v -> {
                    presentFragment(StatisticActivity.create(currentChat, false));
                });
            }

            infoContainer.addView(reactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            if (!isChannel && !currentChat.gigagroup) {
                infoContainer.addView(blockCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (!isChannel) {
                infoContainer.addView(inviteLinksCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            infoContainer.addView(adminCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            infoContainer.addView(membersCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (memberRequestsCell != null && info != null && info.requests_pending > 0) {
                infoContainer.addView(memberRequestsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (isChannel) {
                infoContainer.addView(inviteLinksCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (isChannel || currentChat.gigagroup) {
                infoContainer.addView(blockCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (statsAndBoosts != null) {
                infoContainer.addView(statsAndBoosts, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (logCell != null) {
                infoContainer.addView(logCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        if (currentUser != null) {
            publicLinkCell = new TextCell(context);
            publicLinkCell.setBackground(Theme.getSelectorDrawable(false));
            publicLinkCell.setPrioritizeTitleOverValue(true);
            infoContainer.addView(publicLinkCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            publicLinkCell.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong("bot_id", userId);
                presentFragment(new ChangeUsernameActivity(args));
            });

            if (currentUser.bot && currentUser.bot_can_edit) {
                balanceCell = new TextCell(context);
                balanceCell.setBackground(Theme.getSelectorDrawable(false));
                balanceCell.setPrioritizeTitleOverValue(true);
                infoContainer.addView(balanceCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                BotStarsController c = BotStarsController.getInstance(currentAccount);
                balanceCell.setOnClickListener(v -> {
                    if (!c.isBalanceAvailable(userId))
                        return;
                    presentFragment(new BotStarsActivity(userId));
                });
                if (!c.isBalanceAvailable(userId)) {
                    SpannableStringBuilder loadingStr = new SpannableStringBuilder("x");
                    loadingStr.setSpan(new LoadingSpan(balanceCell.valueTextView, dp(30)), 0, loadingStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    balanceCell.setTextAndValueAndIcon(getString(R.string.BotBalance), loadingStr, R.drawable.menu_premium_main, false);
                } else {
                    balanceCell.setTextAndValueAndIcon(getString(R.string.BotBalance), LocaleController.formatNumber(c.getBalance(userId), ' '), R.drawable.menu_premium_main, false);
                }
                balanceCell.setVisibility(c.hasStars(userId) ? View.VISIBLE : View.GONE);
            }
            updatePublicLinksCount();

            ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, getResourceProvider(), Theme.key_windowBackgroundGray);
            gap.setTag(R.id.fit_width_tag, 1);
            infoContainer.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

            editIntroCell = new TextCell(context);
            editIntroCell.setBackground(Theme.getSelectorDrawable(false));
            editIntroCell.setTextAndIcon(getString(R.string.BotEditIntro), R.drawable.msg_log, true);
            infoContainer.addView(editIntroCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            editIntroCell.setOnClickListener(v -> Browser.openUrl(v.getContext(), "https://t.me/BotFather?start=" + getActiveUsername(currentUser) + "-intro"));

            editCommandsCell = new TextCell(context);
            editCommandsCell.setBackground(Theme.getSelectorDrawable(false));
            editCommandsCell.setTextAndIcon(getString(R.string.BotEditCommands), R.drawable.msg_media, true);
            infoContainer.addView(editCommandsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            editCommandsCell.setOnClickListener(v -> Browser.openUrl(v.getContext(), "https://t.me/BotFather?start=" + getActiveUsername(currentUser) + "-commands"));

            changeBotSettingsCell = new TextCell(context);
            changeBotSettingsCell.setBackground(Theme.getSelectorDrawable(false));
            changeBotSettingsCell.setTextAndIcon(getString(R.string.BotChangeSettings), R.drawable.msg_bot, true);
            infoContainer.addView(changeBotSettingsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            changeBotSettingsCell.setOnClickListener(v -> Browser.openUrl(v.getContext(), "https://t.me/BotFather?start=" + getActiveUsername(currentUser)));
        }

        if (currentChat != null) {
            if (!ChatObject.hasAdminRights(currentChat)) {
                infoContainer.setVisibility(View.GONE);
                settingsTopSectionCell.setVisibility(View.GONE);
            }

            if (stickersCell == null) {
                infoSectionCell = new ShadowSectionCell(context);
                linearLayout1.addView(infoSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        } else {
            botInfoCell = new TextInfoPrivacyCell(context);
            String str = getString(R.string.BotManageInfo);
            SpannableString span = SpannableString.valueOf(str);
            int index = str.indexOf("@BotFather");
            if (index != -1) {
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        Browser.openUrl(widget.getContext(), "https://t.me/BotFather");
                    }

                    @Override
                    public void updateDrawState(TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                    }
                }, index, index + 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            botInfoCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            botInfoCell.setText(span);
            linearLayout1.addView(botInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (currentUser == null && currentChat.creator) {
            deleteContainer = new FrameLayout(context);
            deleteContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout1.addView(deleteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            deleteCell = new TextSettingsCell(context);
            deleteCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            deleteCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (currentUser != null) {
                deleteCell.setText(getString(R.string.DeleteBot), false);
            } else if (isChannel) {
                deleteCell.setText(getString("ChannelDelete", R.string.ChannelDelete), false);
            } else {
                deleteCell.setText(getString("DeleteAndExitButton", R.string.DeleteAndExitButton), false);
            }
            deleteContainer.addView(deleteCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            deleteCell.setOnClickListener(v -> AlertsCreator.createClearOrDeleteDialogAlert(ChatEditActivity.this, false, true, false, currentChat, null, false, true, false, (param) -> {
                if (AndroidUtilities.isTablet()) {
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats, -chatId);
                } else {
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                }
                finishFragment();
                getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, -currentChat.id, null, currentChat, param);
            }, null));

            deleteInfoCell = new ShadowSectionCell(context);
            deleteInfoCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout1.addView(deleteInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (stickersInfoCell != null) {
            if (deleteInfoCell == null) {
                stickersInfoCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            } else {
                stickersInfoCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            }
        }

        undoView = new UndoView(context);
        sizeNotifierFrameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        nameTextView.setText(Emoji.replaceEmoji(currentUser != null ? ContactsController.formatName(currentUser) : currentChat.title, nameTextView.getEditText().getPaint().getFontMetricsInt(), dp(16), true));
        nameTextView.setSelection(nameTextView.length());
        if (info != null) {
            descriptionTextView.setText(info.about);
        } else if (userInfo != null) {
            descriptionTextView.setText(userInfo.about);
        }
        setAvatar();
        updateFields(true, false);

        return fragmentView;
    }

    private void updatePublicLinksCount() {
        if (publicLinkCell == null) {
            return;
        }
        if (currentUser.usernames.size() > 1) {
            int usernamesActive = 0;
            for (TLRPC.TL_username username : currentUser.usernames) {
                if (username.active) {
                    usernamesActive++;
                }
            }

            publicLinkCell.setTextAndValueAndIcon(getString(R.string.BotPublicLinks), LocaleController.formatString(R.string.BotPublicLinksCount, usernamesActive, currentUser.usernames.size()), R.drawable.msg_link2, balanceCell != null && balanceCell.getVisibility() == View.VISIBLE);
        } else {
            publicLinkCell.setTextAndValueAndIcon(getString(R.string.BotPublicLink), "t.me/" + currentUser.username, R.drawable.msg_link2, balanceCell != null && balanceCell.getVisibility() == View.VISIBLE);
        }
    }

    private String getActiveUsername(TLRPC.User user) {
        if (user.username != null) {
            return user.username;
        }

        for (TLRPC.TL_username username : user.usernames) {
            if (username.active) {
                return username.username;
            }
        }

        return null;
    }

    RLottieDrawable cameraDrawable;

    private void setAvatar() {
        if (avatarImage == null || hasUploadedPhoto) {
            return;
        }
        TLRPC.Chat chat = getMessagesController().getChat(chatId);
        TLRPC.User user = userId == 0 ? null : getMessagesController().getUser(userId);
        if (chat == null && user == null) {
            return;
        }
        currentUser = user;
        currentChat = chat;
        boolean hasPhoto;
        if (currentUser != null ? currentUser.photo != null : currentChat.photo != null) {
            TLObject obj = currentUser != null ? currentUser : currentChat;
            avatar = currentUser != null ? currentUser.photo.photo_small : currentChat.photo.photo_small;
            ImageLocation location = ImageLocation.getForUserOrChat(obj, ImageLocation.TYPE_SMALL);
            avatarImage.setForUserOrChat(obj, avatarDrawable);
            hasPhoto = location != null;
        } else {
            avatarImage.setImageDrawable(avatarDrawable);
            hasPhoto = false;
        }
        if (setAvatarCell != null) {
            if (hasPhoto || imageUpdater.isUploadingImage()) {
                setAvatarCell.setTextAndIcon(getString("ChatSetNewPhoto", R.string.ChatSetNewPhoto), R.drawable.msg_addphoto, true);
            } else {
                setAvatarCell.setTextAndIcon(getString("ChatSetPhotoOrVideo", R.string.ChatSetPhotoOrVideo), R.drawable.msg_addphoto, true);
            }
            if (cameraDrawable == null) {
                cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, dp(50), dp(50), false, null);
            }
            setAvatarCell.imageView.setTranslationX(-dp(8));
            setAvatarCell.imageView.setAnimation(cameraDrawable);
        }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().checkCurrentImageVisibility();
        }
    }

    private void updateCanForum() {
        if (userId != 0) {
            canForum = false;
            return;
        }
        canForum = (forum || Math.max(info == null ? 0 : info.participants_count, currentChat.participants_count) >= getMessagesController().forumUpgradeParticipantsMin) && (info == null || info.linked_chat_id == 0);
        if (forumsCell != null) {
            forumsCell.getCheckBox().setIcon(canForum ? 0 : R.drawable.permission_locked);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null && descriptionTextView != null) {
                    descriptionTextView.setText(chatFull.about);
                }
                boolean infoWasEmpty = info == null;
                info = chatFull;
                updateCanForum();
                historyHidden = !ChatObject.isChannel(currentChat) || info.hidden_prehistory;
                updateFields(false, false);
                if (infoWasEmpty) {
                    loadLinksCount();
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                setAvatar();
            }
            if ((mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updatePublicLinksCount();
            }
        } else if (id == NotificationCenter.chatAvailableReactionsUpdated) {
            long chatId = (long) args[0];
            if (chatId == this.chatId) {
                info = getMessagesController().getChatFull(chatId);
                if (info != null) {
                    availableReactions = info.available_reactions;
                }
                updateReactionsCell(true);
            }
        } else if (id == NotificationCenter.botStarsUpdated) {
            if ((long) args[0] == userId) {
                if (balanceCell != null) {
                    BotStarsController c = BotStarsController.getInstance(currentAccount);
                    balanceCell.setVisibility(c.hasStars(userId) ? View.VISIBLE : View.GONE);
                    balanceCell.setValue(LocaleController.formatNumber(c.getBalance(userId), ' '), true);
                    if (publicLinkCell != null) {
                        publicLinkCell.setNeedDivider(c.hasStars(userId));
                    }
                }
            }
        }
    }

    @Override
    public void onUploadProgressChanged(float progress) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(progress);
    }

    @Override
    public void didStartUpload(boolean isVideo) {
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
                if (userId != 0) {
                    if (currentUser != null) {
                        currentUser.photo = new TLRPC.TL_userProfilePhoto();
                        currentUser.photo.photo_id = photo != null ? photo.id : video != null ? video.id : 0;
                        currentUser.photo.photo_big = bigSize.location;
                        currentUser.photo.photo_small = smallSize.location;
                        getMessagesController().putUser(currentUser, true);
                    }

                    TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                    if (photo != null) {
                        req.file = photo;
                        req.flags |= 1;
                    }
                    if (video != null) {
                        req.video = video;
                        req.flags |= 2;

                        req.video_start_ts = videoStartTimestamp;
                        req.flags |= 4;
                    }
                    if (emojiMarkup != null) {
                        req.video_emoji_markup = emojiMarkup;
                        req.flags |= 16;
                    }
                    req.bot = getMessagesController().getInputUser(currentUser);
                    req.flags |= 32;

                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        hasUploadedPhoto = true;

                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                    }));
                } else {
                    getMessagesController().changeChatAvatar(chatId, null, photo, video, emojiMarkup, videoStartTimestamp, videoPath, smallSize.location, bigSize.location, null);
                }
                if (createAfterUpload) {
                    try {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    donePressed = false;
                    doneButton.performClick();
                }
                showAvatarProgress(false, true);
            } else {
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, currentUser != null ? currentUser : currentChat);
                setAvatarCell.setTextAndIcon(getString("ChatSetNewPhoto", R.string.ChatSetNewPhoto), R.drawable.msg_addphoto, true);
                if (cameraDrawable == null) {
                    cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, dp(50), dp(50), false, null);
                }
                setAvatarCell.imageView.setTranslationX(-dp(8));
                setAvatarCell.imageView.setAnimation(cameraDrawable);
                showAvatarProgress(true, false);
            }
        });
    }

    @Override
    public String getInitialSearchString() {
        return nameTextView.getText().toString();
    }

    public void showConvertTooltip() {
        undoView.showWithAction(0, UndoView.ACTION_GIGAGROUP_SUCCESS, null);
    }

    private boolean checkDiscard() {
        if (userId != 0) {
            String about = userInfo != null && userInfo.about != null ? userInfo.about : "";
            if (nameTextView != null && !currentUser.first_name.equals(nameTextView.getText().toString()) ||
                    descriptionTextView != null && !about.equals(descriptionTextView.getText().toString())) {
                showDialog(new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges))
                        .setMessage(getString(R.string.BotSettingsChangedAlert))
                        .setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone())
                        .setNegativeButton(getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment())
                        .create());
                return false;
            }
            return true;
        }

        String about = info != null && info.about != null ? info.about : "";
        if (info != null && ChatObject.isChannel(currentChat) && info.hidden_prehistory != historyHidden ||
            nameTextView != null && !currentChat.title.equals(nameTextView.getText().toString()) ||
            descriptionTextView != null && !about.equals(descriptionTextView.getText().toString()) ||
            forum != currentChat.forum
        ) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            if (isChannel) {
                builder.setMessage(getString("ChannelSettingsChangedAlert", R.string.ChannelSettingsChangedAlert));
            } else {
                builder.setMessage(getString("GroupSettingsChangedAlert", R.string.GroupSettingsChangedAlert));
            }
            builder.setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    private int getAdminCount() {
        if (info == null) {
            return 1;
        }
        int count = 0;
        for (int a = 0, N = info.participants.participants.size(); a < N; a++) {
            TLRPC.ChatParticipant chatParticipant = info.participants.participants.get(a);
            if (chatParticipant instanceof TLRPC.TL_chatParticipantAdmin ||
                    chatParticipant instanceof TLRPC.TL_chatParticipantCreator) {
                count++;
            }
        }
        return count;
    }

    private void processDone() {
        if (donePressed || nameTextView == null) {
            return;
        }
        if (nameTextView.length() == 0) {
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(nameTextView);
            return;
        }
        donePressed = true;
        if (currentUser != null) {
            TL_bots.setBotInfo req = new TL_bots.setBotInfo();
            req.bot = getMessagesController().getInputUser(currentUser);
            req.flags |= 4;
            req.lang_code = "";

            if (!currentUser.first_name.equals(nameTextView.getText().toString())) {
                req.name = nameTextView.getText().toString();
                req.flags |= 8;
            }

            String about = userInfo != null && userInfo.about != null ? userInfo.about : "";
            if (descriptionTextView != null && !about.equals(descriptionTextView.getText().toString())) {
                req.about = descriptionTextView.getText().toString();
                req.flags |= 1;
            }

            progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                if (userInfo != null) {
                    userInfo.about = req.about;
                    getMessagesStorage().updateUserInfo(userInfo, false);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    finishFragment();
                });
            });
            progressDialog.setOnCancelListener(dialog -> {
                donePressed = false;
                progressDialog = null;
                getConnectionsManager().cancelRequest(reqId, true);
            });
            progressDialog.show();
            return;
        }
        if (!ChatObject.isChannel(currentChat) && (!historyHidden || forum)) {
            getMessagesController().convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                if (param == 0) {
                    donePressed = false;
                    return;
                }
                chatId = param;
                currentChat = getMessagesController().getChat(param);
                donePressed = false;
                if (info != null) {
                    info.hidden_prehistory = true;
                }
                processDone();
            });
            return;
        }

        if (info != null) {
            if (ChatObject.isChannel(currentChat) && info.hidden_prehistory != historyHidden) {
                info.hidden_prehistory = historyHidden;
                getMessagesController().toggleChannelInvitesHistory(chatId, historyHidden);
            }
        }

        if (imageUpdater.isUploadingImage()) {
            createAfterUpload = true;
            progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.setOnCancelListener(dialog -> {
                createAfterUpload = false;
                progressDialog = null;
                donePressed = false;
            });
            progressDialog.show();
            return;
        }

        if (!currentChat.title.equals(nameTextView.getText().toString())) {
            getMessagesController().changeChatTitle(chatId, nameTextView.getText().toString());
        }
        String about = info != null && info.about != null ? info.about : "";
        if (descriptionTextView != null && !about.equals(descriptionTextView.getText().toString())) {
            getMessagesController().updateChatAbout(chatId, descriptionTextView.getText().toString(), info);
        }
//        if (signMessages != currentChat.signatures) {
//            currentChat.signatures = true;
//            getMessagesController().toggleChannelSignatures(chatId, signMessages, false);
//        }
        if (forum != currentChat.forum) {
            getMessagesController().toggleChannelForum(chatId, forum);
            List<BaseFragment> fragments = getParentLayout().getFragmentStack();
            for (int i = 0; i < fragments.size(); i++) {
                if (fragments.get(i) instanceof ChatActivity) {
                    ChatActivity chatActivity = (ChatActivity) fragments.get(i);
                    if (chatActivity.getArguments().getLong("chat_id") == chatId) {
                        getParentLayout().removeFragmentFromStack(i);
                        Bundle bundle = new Bundle();
                        bundle.putLong("chat_id",chatId);
                        getParentLayout().addFragmentToStack(TopicsFragment.getTopicsOrChat(this, bundle), i);
                    }
                }
            }
        }
        finishFragment();
    }

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

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        imageUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (imageUpdater != null && imageUpdater.currentPicturePath != null) {
            args.putString("path", imageUpdater.currentPicturePath);
        }
        if (nameTextView != null) {
            String text = nameTextView.getText().toString();
            if (text.length() != 0) {
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

    public void setInfo(TLRPC.UserFull userFull) {
        userInfo = userFull;
        if (userFull != null) {
            if (currentUser == null) {
                currentUser = userId == 0 ? null : getMessagesController().getUser(userId);
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (chatFull != null) {
            if (currentChat == null) {
                currentChat = getMessagesController().getChat(chatId);
            }
            historyHidden = !ChatObject.isChannel(currentChat) || info.hidden_prehistory;
            availableReactions = info.available_reactions;
            preloadedReactions.clear();
            preloadedReactions.addAll(ReactionsUtils.startPreloadReactions(currentChat, info));
        }
    }

    private void updateFields(boolean updateChat, boolean animated) {
        if (updateChat) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null) {
                currentChat = chat;
            }
        }
        boolean isPrivate = !ChatObject.isPublic(currentChat);

        if (settingsSectionCell != null) {
            settingsSectionCell.setVisibility(/*signCell == null && */typeCell == null && (linkedCell == null || linkedCell.getVisibility() != View.VISIBLE) && (historyCell == null || historyCell.getVisibility() != View.VISIBLE) && (locationCell == null || locationCell.getVisibility() != View.VISIBLE) ? View.GONE : View.VISIBLE);
        }

        if (logCell != null) {
            logCell.setVisibility(ChatObject.isChannel(currentChat) ? View.VISIBLE : View.GONE);
        }

        if (linkedCell != null) {
            if (info == null || !isChannel && info.linked_chat_id == 0) {
                linkedCell.setVisibility(View.GONE);
            } else {
                linkedCell.setVisibility(View.VISIBLE);
                if (info.linked_chat_id == 0) {
                    linkedCell.setTextAndValueAndIcon(getString("Discussion", R.string.Discussion), getString("DiscussionInfoShort", R.string.DiscussionInfoShort), R.drawable.msg_discuss, true);
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
                    if (chat == null) {
                        linkedCell.setVisibility(View.GONE);
                    } else {
                        String username;
                        if (isChannel) {
                            if (TextUtils.isEmpty(username = ChatObject.getPublicUsername(chat))) {
                                linkedCell.setTextAndValueAndIcon(getString("Discussion", R.string.Discussion), chat.title, R.drawable.msg_discuss,true);
                            } else {
                                linkedCell.setTextAndValueAndIcon(getString("Discussion", R.string.Discussion), "@" + username, R.drawable.msg_discuss,true);
                            }
                        } else {
                            if (TextUtils.isEmpty(username = ChatObject.getPublicUsername(chat))) {
                                linkedCell.setTextAndValueAndIcon(getString("LinkedChannel", R.string.LinkedChannel), chat.title, R.drawable.msg_channel, forumsCell != null && forumsCell.getVisibility() == View.VISIBLE);
                            } else {
                                linkedCell.setTextAndValueAndIcon(getString("LinkedChannel", R.string.LinkedChannel), "@" + username,  R.drawable.msg_channel, forumsCell != null && forumsCell.getVisibility() == View.VISIBLE);
                            }
                        }
                    }
                }
            }
        }

        if (locationCell != null) {
            if (info != null && info.can_set_location) {
                locationCell.setVisibility(View.VISIBLE);
                if (info.location instanceof TLRPC.TL_channelLocation) {
                    TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) info.location;
                    locationCell.setTextAndValue(getString("AttachLocation", R.string.AttachLocation), location.address, animated, true);
                } else {
                    locationCell.setTextAndValue(getString("AttachLocation", R.string.AttachLocation), "Unknown address", animated, true);
                }
            } else {
                locationCell.setVisibility(View.GONE);
            }
        }

        if (typeCell != null) {
            if (info != null && info.location instanceof TLRPC.TL_channelLocation) {
                String link;
                if (isPrivate) {
                    link = getString("TypeLocationGroupEdit", R.string.TypeLocationGroupEdit);
                } else {
                    link = String.format("https://" + getMessagesController().linkPrefix + "/%s", ChatObject.getPublicUsername(currentChat));
                }
                typeCell.setTextAndValueAndIcon(getString("TypeLocationGroup", R.string.TypeLocationGroup), link, R.drawable.msg_channel, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE || forumsCell != null && forumsCell.getVisibility() == View.VISIBLE);
            } else {
                String type;
                boolean isRestricted = currentChat.noforwards;
                if (isChannel) {
                    type = isPrivate ? isRestricted ? getString("TypePrivateRestrictedForwards", R.string.TypePrivateRestrictedForwards) : getString("TypePrivate", R.string.TypePrivate) : getString("TypePublic", R.string.TypePublic);
                } else {
                    type = isPrivate ? isRestricted ? getString("TypePrivateGroupRestrictedForwards", R.string.TypePrivateGroupRestrictedForwards) : getString("TypePrivateGroup", R.string.TypePrivateGroup) : getString("TypePublicGroup", R.string.TypePublicGroup);
                }
                if (isChannel) {
                    typeCell.setTextAndValueAndIcon(getString("ChannelType", R.string.ChannelType), type, R.drawable.msg_channel, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE || forumsCell != null && forumsCell.getVisibility() == View.VISIBLE);
                } else {
                    typeCell.setTextAndValueAndIcon(getString("GroupType", R.string.GroupType), type, R.drawable.msg_groups, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE || forumsCell != null && forumsCell.getVisibility() == View.VISIBLE);
                }
            }
        }

        if (historyCell != null) {
            String type = historyHidden && !forum ? getString("ChatHistoryHidden", R.string.ChatHistoryHidden) : getString("ChatHistoryVisible", R.string.ChatHistoryVisible);
            historyCell.setTextAndValueAndIcon(getString("ChatHistoryShort", R.string.ChatHistoryShort), type, animated, R.drawable.msg_discuss, forumsCell != null);
            historyCell.setEnabled(!forum);
            updateHistoryShow(!forum && isPrivate && (info == null || info.linked_chat_id == 0) && !(info != null && info.location instanceof TLRPC.TL_channelLocation), animated);
        }

        if (membersCell != null) {
            if (info != null) {
                if (memberRequestsCell != null) {
                    if (memberRequestsCell.getParent() == null) {
                        int position = infoContainer.indexOfChild(membersCell) + 1;
                        infoContainer.addView(memberRequestsCell, position, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }
                    memberRequestsCell.setVisibility(info.requests_pending > 0 ? View.VISIBLE : View.GONE);
                }
                if (isChannel) {
                    membersCell.setTextAndValueAndIcon(getString("ChannelSubscribers", R.string.ChannelSubscribers), String.format("%d", info.participants_count), R.drawable.msg_groups, true);
                    blockCell.setTextAndValueAndIcon(getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", Math.max(info.banned_count, info.kicked_count)), R.drawable.msg_user_remove, logCell != null && logCell.getVisibility() == View.VISIBLE);
                } else {
                    if (ChatObject.isChannel(currentChat)) {
                        membersCell.setTextAndValueAndIcon(getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants_count), R.drawable.msg_groups, true);
                    } else {
                        membersCell.setTextAndValueAndIcon(getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants.participants.size()), R.drawable.msg_groups, memberRequestsCell.getVisibility() == View.VISIBLE);
                    }
                    if (currentChat.gigagroup) {
                        blockCell.setTextAndValueAndIcon(getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", Math.max(info.banned_count, info.kicked_count)), R.drawable.msg_user_remove, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    } else {
                        int count = 0;
                        if (currentChat.default_banned_rights != null) {
                            if (!currentChat.default_banned_rights.send_plain) {
                                count++;
                            }
                            count += ChatUsersActivity.getSendMediaSelectedCount(currentChat.default_banned_rights);
                            if (!currentChat.default_banned_rights.pin_messages) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.invite_users) {
                                count++;
                            }
                            if (forum && !currentChat.default_banned_rights.manage_topics) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.change_info) {
                                count++;
                            }
                        } else {
                            count = forum ? 14 : 13;
                        }
                        blockCell.setTextAndValueAndIcon(getString("ChannelPermissions", R.string.ChannelPermissions), String.format("%d/%d", count, forum ? 14 : 13), animated, R.drawable.msg_permissions, true);
                    }
                    if (memberRequestsCell != null) {
                        memberRequestsCell.setTextAndValueAndIcon(getString("MemberRequests", R.string.MemberRequests), String.format("%d", info.requests_pending), R.drawable.msg_requests, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    }
                }
                adminCell.setTextAndValueAndIcon(getString("ChannelAdministrators", R.string.ChannelAdministrators), String.format("%d", ChatObject.isChannel(currentChat) ? info.admins_count : getAdminCount()), R.drawable.msg_admins, true);
            } else {
                if (isChannel) {
                    membersCell.setTextAndIcon(getString("ChannelSubscribers", R.string.ChannelSubscribers), R.drawable.msg_groups, true);
                    blockCell.setTextAndIcon(getString("ChannelBlacklist", R.string.ChannelBlacklist), R.drawable.msg_chats_remove, logCell != null && logCell.getVisibility() == View.VISIBLE);
                } else {
                    membersCell.setTextAndIcon(getString("ChannelMembers", R.string.ChannelMembers), R.drawable.msg_groups, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    if (currentChat.gigagroup) {
                        blockCell.setTextAndIcon(getString("ChannelBlacklist", R.string.ChannelBlacklist), R.drawable.msg_chats_remove, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    } else {
                        blockCell.setTextAndIcon(getString("ChannelPermissions", R.string.ChannelPermissions), R.drawable.msg_permissions, true);
                    }
                }
                adminCell.setTextAndIcon(getString("ChannelAdministrators", R.string.ChannelAdministrators), R.drawable.msg_admins, true);
            }
            reactionsCell.setVisibility(ChatObject.canChangeChatInfo(currentChat) ? View.VISIBLE : View.GONE);
            updateReactionsCell(animated);
            if (info == null || !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) || (!isPrivate && currentChat.creator)) {
                inviteLinksCell.setVisibility(View.GONE);
            } else {
                if (info.invitesCount > 0) {
                    inviteLinksCell.setTextAndValueAndIcon(getString("InviteLinks", R.string.InviteLinks), Integer.toString(info.invitesCount), R.drawable.msg_link2, true);
                } else {
                    inviteLinksCell.setTextAndValueAndIcon(getString("InviteLinks", R.string.InviteLinks), "1", R.drawable.msg_link2, true);
                }
            }
        }

        if (stickersCell != null && info != null) {
            stickersCell.setTextAndValueAndIcon(getString(R.string.GroupStickers), info.stickerset != null ? info.stickerset.title : getString(R.string.Add), R.drawable.msg_sticker, false);
        }
    }

    public void updateColorCell() {
        if (colorCell != null) {
            colorCell.set(currentChat, (historyCell != null && historyCell.getVisibility() == View.VISIBLE) || /*(signCell != null && signCell.getVisibility() == View.VISIBLE) || */(forumsCell != null && forumsCell.getVisibility() == View.VISIBLE) || ChatObject.isMegagroup(currentChat) && ChatObject.hasAdminRights(currentChat));
        }
    }

    private ValueAnimator updateHistoryShowAnimator;
    private void updateHistoryShow(boolean show, boolean animated) {
        final boolean finalShow = show;
        if (updateHistoryShowAnimator != null) {
            updateHistoryShowAnimator.cancel();
        }
        if (historyCell.getAlpha() <= 0 && !show) {
            historyCell.setVisibility(View.GONE);
            updateColorCell();
            return;
        } else if (historyCell.getVisibility() == View.VISIBLE && historyCell.getAlpha() >= 1 && show) {
            return;
        }
        ArrayList<View> nextViews = new ArrayList<>();
        boolean afterme = false;
        for (int i = 0; i < typeEditContainer.getChildCount(); ++i) {
            if (!afterme && typeEditContainer.getChildAt(i) == historyCell) {
                afterme = true;
            } else if (afterme) {
                nextViews.add(typeEditContainer.getChildAt(i));
            }
        }
        afterme = false;
        for (int i = 0; i < linearLayout.getChildCount(); ++i) {
            if (!afterme && linearLayout.getChildAt(i) == typeEditContainer) {
                afterme = true;
            } else if (afterme) {
                nextViews.add(linearLayout.getChildAt(i));
            }
        }
        if (historyCell.getVisibility() != View.VISIBLE) {
            historyCell.setAlpha(0);
            historyCell.setTranslationY(-historyCell.getHeight() / 2f);
        }
        historyCell.setVisibility(View.VISIBLE);
        for (int i = 0; i < nextViews.size(); ++i) {
            nextViews.get(i).setTranslationY(-historyCell.getHeight() * (1f - historyCell.getAlpha()));
        }
        if (animated) {
            updateHistoryShowAnimator = ValueAnimator.ofFloat(historyCell.getAlpha(), show ? 1f : 0f);
            updateHistoryShowAnimator.addUpdateListener(anm -> {
                float t = (float) anm.getAnimatedValue();
                historyCell.setAlpha(t);
                historyCell.setTranslationY(-historyCell.getHeight() / 2f * (1f - t));
                historyCell.setScaleY(.2f + .8f * t);
                for (int i = 0; i < nextViews.size(); ++i) {
                    nextViews.get(i).setTranslationY(-historyCell.getHeight() * (1f - t));
                }
            });
            updateHistoryShowAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    historyCell.setVisibility(finalShow ? View.VISIBLE : View.GONE);
                    for (int i = 0; i < nextViews.size(); ++i) {
                        nextViews.get(i).setTranslationY(0);
                    }
                }
            });
            updateHistoryShowAnimator.setDuration(320);
            updateHistoryShowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            updateHistoryShowAnimator.start();
        } else {
            historyCell.setAlpha(show ? 1f : 0f);
            historyCell.setTranslationY(-historyCell.getHeight() / 2f * (show ? 0 : 1f));
            historyCell.setScaleY(.2f + .8f * (show ? 1f : 0f));
            historyCell.setVisibility(finalShow ? View.VISIBLE : View.GONE);
            for (int i = 0; i < nextViews.size(); ++i) {
                nextViews.get(i).setTranslationY(0);
            }
            updateHistoryShowAnimator = null;
        }
    }

    private void updateReactionsCell(boolean animated) {
        final TLRPC.ChatFull chat = getMessagesController().getChatFull(chatId);
        boolean isChannelAndNotMegaGroup = ChatObject.isChannelAndNotMegaGroup(currentChat);
        String finalString;
        if (availableReactions == null || availableReactions instanceof TLRPC.TL_chatReactionsNone) {
            finalString = getString(R.string.ReactionsOff);
            if (chat != null && chat.paid_reactions_available) {
                finalString = "1";
            }
        } else if (availableReactions instanceof TLRPC.TL_chatReactionsSome) {
            TLRPC.TL_chatReactionsSome someReactions = (TLRPC.TL_chatReactionsSome) availableReactions;
            int count = 0;
            for (int i = 0; i < someReactions.reactions.size(); i++) {
                TLRPC.Reaction someReaction = someReactions.reactions.get(i);
                if (someReaction instanceof TLRPC.TL_reactionEmoji) {
                    TLRPC.TL_reactionEmoji tl_reactionEmoji = (TLRPC.TL_reactionEmoji) someReaction;
                    TLRPC.TL_availableReaction reaction = getMediaDataController().getReactionsMap().get(tl_reactionEmoji.emoticon);
                    if (reaction != null && !reaction.inactive) {
                        count++;
                    }
                } else if (someReaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    count++;
                }
            }
            if (isChannelAndNotMegaGroup) {
                if (chat != null && chat.paid_reactions_available) {
                    count++;
                }
                finalString = count == 0 ? getString(R.string.ReactionsOff) : String.valueOf(count);
            } else {
                int reacts = Math.min(getMediaDataController().getEnabledReactionsList().size(), count);
                finalString = reacts == 0 ? getString(R.string.ReactionsOff) :
                        LocaleController.formatString(R.string.ReactionsCount, reacts, getMediaDataController().getEnabledReactionsList().size());
            }
        } else {
            finalString = getString(R.string.ReactionsAll);
        }
        reactionsCell.setTextAndValueAndIcon(getString(R.string.Reactions), finalString, animated, R.drawable.msg_reactions2, true);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (avatarImage != null) {
                avatarImage.invalidate();
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(setAvatarCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(setAvatarCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(setAvatarCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));
        themeDescriptions.add(new ThemeDescription(membersCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(membersCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(membersCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(adminCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(adminCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(adminCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(inviteLinksCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(inviteLinksCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(inviteLinksCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        if (memberRequestsCell != null) {
            themeDescriptions.add(new ThemeDescription(memberRequestsCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
            themeDescriptions.add(new ThemeDescription(memberRequestsCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            themeDescriptions.add(new ThemeDescription(memberRequestsCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        }

        themeDescriptions.add(new ThemeDescription(blockCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(blockCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(blockCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(logCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(logCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(logCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        themeDescriptions.add(new ThemeDescription(typeCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(typeCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(typeCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(historyCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(historyCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(historyCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(locationCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(locationCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(locationCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(avatarContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(settingsContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(typeEditContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(deleteContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(stickersContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(infoContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(settingsTopSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(settingsSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(deleteInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

//        themeDescriptions.add(new ThemeDescription(signCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
//        themeDescriptions.add(new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
//        themeDescriptions.add(new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
//        themeDescriptions.add(new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(deleteCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(deleteCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(stickersCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(stickersCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(stickersInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(stickersInfoCell, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(null, 0, null, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));

        themeDescriptions.add(new ThemeDescription(reactionsCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(reactionsCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(reactionsCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        if (statsAndBoosts != null) {
            themeDescriptions.add(new ThemeDescription(statsAndBoosts, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
            themeDescriptions.add(new ThemeDescription(statsAndBoosts, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            themeDescriptions.add(new ThemeDescription(statsAndBoosts, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        }

        return themeDescriptions;
    }
}
