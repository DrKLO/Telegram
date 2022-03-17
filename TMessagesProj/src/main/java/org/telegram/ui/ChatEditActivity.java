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
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
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
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Collections;
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

    private LinearLayout settingsContainer;
    private EditTextBoldCursor descriptionTextView;

    private LinearLayout typeEditContainer;
    private ShadowSectionCell settingsTopSectionCell;
    private TextDetailCell locationCell;
    private TextDetailCell typeCell;
    private TextDetailCell linkedCell;
    private TextDetailCell historyCell;
    private TextCell reactionsCell;
    private ShadowSectionCell settingsSectionCell;

    private TextCheckCell signCell;

    private FrameLayout stickersContainer;
    private TextSettingsCell stickersCell;
    private TextInfoPrivacyCell stickersInfoCell3;

    private LinearLayout infoContainer;
    private TextCell membersCell;
    private TextCell memberRequestsCell;
    private TextCell inviteLinksCell;
    private TextCell adminCell;
    private TextCell blockCell;
    private TextCell logCell;
    private TextCell setAvatarCell;
    private ShadowSectionCell infoSectionCell;

    private FrameLayout deleteContainer;
    private TextSettingsCell deleteCell;
    private ShadowSectionCell deleteInfoCell;

    private TLRPC.FileLocation avatar;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private boolean signMessages;

    private boolean isChannel;

    private boolean historyHidden;
    private List<String> availableReactions = Collections.emptyList();

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (fileLocation == null) {
                return null;
            }

            TLRPC.FileLocation photoBig = null;
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                photoBig = chat.photo.photo_big;
            }

            if (photoBig != null && photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int[] coords = new int[2];
                avatarImage.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                object.parentView = avatarImage;
                object.imageReceiver = avatarImage.getImageReceiver();
                object.dialogId = -chatId;
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.size = -1;
                object.radius = avatarImage.getImageReceiver().getRoundRadius();
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
    };

    public ChatEditActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        imageUpdater = new ImageUpdater(true);
        chatId = args.getLong("chat_id", 0);
    }

    @Override
    public boolean onFragmentCreate() {
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

        avatarDrawable.setInfo(5, currentChat.title, null);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);
        signMessages = currentChat.signatures;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatAvailableReactionsUpdated);

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
                updateFields(false);
            }
        }));
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatAvailableReactionsUpdated);
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
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        updateFields(true);
        imageUpdater.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nameTextView != null) {
            nameTextView.onPause();
        }
        if (undoView != null) {
            undoView.hide(true, 0);
        }
        imageUpdater.onPause();
    }

    @Override
    protected void onBecomeFullyHidden() {
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
                if (keyboardSize > AndroidUtilities.dp(20)) {
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
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
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
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? nameTextView.getEmojiPadding() : 0;
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

        LinearLayout linearLayout1 = new LinearLayout(context);
        scrollView.addView(linearLayout1, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout1.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle(LocaleController.getString("ChannelEdit", R.string.ChannelEdit));

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
        avatarImage.setRoundRadius(AndroidUtilities.dp(32));

        if (ChatObject.canChangeChatInfo(currentChat)) {
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
            avatarProgressView.setSize(AndroidUtilities.dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            avatarProgressView.setNoProgress(false);
            frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

            showAvatarProgress(false, false);

            avatarContainer.setOnClickListener(v -> {
                if (imageUpdater.isUploadingImage()) {
                    return;
                }
                TLRPC.Chat chat = getMessagesController().getChat(chatId);
                if (chat.photo != null && chat.photo.photo_big != null) {
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
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

        nameTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT);
        if (isChannel) {
            nameTextView.setHint(LocaleController.getString("EnterChannelName", R.string.EnterChannelName));
        } else {
            nameTextView.setHint(LocaleController.getString("GroupName", R.string.GroupName));
        }
        nameTextView.setEnabled(ChatObject.canChangeChatInfo(currentChat));
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

        if (ChatObject.canChangeChatInfo(currentChat)) {
            setAvatarCell = new TextCell(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            };
            setAvatarCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            setAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            setAvatarCell.setOnClickListener(v -> {
                imageUpdater.openMenu(avatar != null, () -> {
                    avatar = null;
                    MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, null, null, null, 0, null, null, null, null);
                    showAvatarProgress(false, true);
                    avatarImage.setImage(null, null, avatarDrawable, currentChat);
                    cameraDrawable.setCurrentFrame(0);
                    setAvatarCell.imageView.playAnimation();
                }, dialogInterface -> {
                    if (!imageUpdater.isUploadingImage()) {
                        cameraDrawable.setCustomEndFrame(86);
                        setAvatarCell.imageView.playAnimation();
                    } else {
                        cameraDrawable.setCurrentFrame(0, false);
                    }

                });
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
        descriptionTextView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
        descriptionTextView.setBackgroundDrawable(null);
        descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        descriptionTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        descriptionTextView.setEnabled(ChatObject.canChangeChatInfo(currentChat));
        descriptionTextView.setFocusable(descriptionTextView.isEnabled());
        inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(255);
        descriptionTextView.setFilters(inputFilters);
        descriptionTextView.setHint(LocaleController.getString("DescriptionOptionalPlaceholder", R.string.DescriptionOptionalPlaceholder));
        descriptionTextView.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        descriptionTextView.setCursorSize(AndroidUtilities.dp(20));
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
        typeEditContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(typeEditContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentChat.megagroup && (info == null || info.can_set_location)) {
            locationCell = new TextDetailCell(context);
            locationCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            typeEditContainer.addView(locationCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            locationCell.setOnClickListener(v -> {
                if (!AndroidUtilities.isGoogleMapsInstalled(ChatEditActivity.this)) {
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
                    updateFields(false);
                    getMessagesController().loadFullChat(chatId, 0, true);
                });
                presentFragment(fragment);
            });
        }

        if (currentChat.creator && (info == null || info.can_set_username)) {
            typeCell = new TextDetailCell(context);
            typeCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            typeEditContainer.addView(typeCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            typeCell.setOnClickListener(v -> {
                ChatEditTypeActivity fragment = new ChatEditTypeActivity(chatId, locationCell != null && locationCell.getVisibility() == View.VISIBLE);
                fragment.setInfo(info);
                presentFragment(fragment);
            });
        }

        if (ChatObject.isChannel(currentChat) && (isChannel && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO) || !isChannel && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_PIN))) {
            linkedCell = new TextDetailCell(context);
            linkedCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            typeEditContainer.addView(linkedCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            linkedCell.setOnClickListener(v -> {
                ChatLinkActivity fragment = new ChatLinkActivity(chatId);
                fragment.setInfo(info);
                presentFragment(fragment);
            });
        }

        if (!isChannel && ChatObject.canBlockUsers(currentChat) && (ChatObject.isChannel(currentChat) || currentChat.creator)) {
            historyCell = new TextDetailCell(context);
            historyCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            typeEditContainer.addView(historyCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            historyCell.setOnClickListener(v -> {
                BottomSheet.Builder builder = new BottomSheet.Builder(context);
                builder.setApplyTopPadding(false);

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                HeaderCell headerCell = new HeaderCell(context, Theme.key_dialogTextBlue2, 23, 15, false);
                headerCell.setHeight(47);
                headerCell.setText(LocaleController.getString("ChatHistory", R.string.ChatHistory));
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
                        buttons[a].setTextAndValue(LocaleController.getString("ChatHistoryVisible", R.string.ChatHistoryVisible), LocaleController.getString("ChatHistoryVisibleInfo", R.string.ChatHistoryVisibleInfo), true, !historyHidden);
                    } else {
                        if (ChatObject.isChannel(currentChat)) {
                            buttons[a].setTextAndValue(LocaleController.getString("ChatHistoryHidden", R.string.ChatHistoryHidden), LocaleController.getString("ChatHistoryHiddenInfo", R.string.ChatHistoryHiddenInfo), false, historyHidden);
                        } else {
                            buttons[a].setTextAndValue(LocaleController.getString("ChatHistoryHidden", R.string.ChatHistoryHidden), LocaleController.getString("ChatHistoryHiddenInfo2", R.string.ChatHistoryHiddenInfo2), false, historyHidden);
                        }
                    }
                    linearLayoutInviteContainer.addView(buttons[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    buttons[a].setOnClickListener(v2 -> {
                        Integer tag = (Integer) v2.getTag();
                        buttons[0].setChecked(tag == 0, true);
                        buttons[1].setChecked(tag == 1, true);
                        historyHidden = tag == 1;
                        builder.getDismissRunnable().run();
                        updateFields(true);
                    });
                }

                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            });
        }

        if (isChannel) {
            signCell = new TextCheckCell(context);
            signCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            signCell.setTextAndValueAndCheck(LocaleController.getString("ChannelSignMessages", R.string.ChannelSignMessages), LocaleController.getString("ChannelSignMessagesInfo", R.string.ChannelSignMessagesInfo), signMessages, true, false);
            typeEditContainer.addView(signCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            signCell.setOnClickListener(v -> {
                signMessages = !signMessages;
                ((TextCheckCell) v).setChecked(signMessages);
            });
        }

        ActionBarMenu menu = actionBar.createMenu();
        if (ChatObject.canChangeChatInfo(currentChat) || signCell != null || historyCell != null) {
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
            doneButton.setContentDescription(LocaleController.getString("Done", R.string.Done));
        }

        if (locationCell != null || signCell != null || historyCell != null || typeCell != null || linkedCell != null) {
            settingsSectionCell = new ShadowSectionCell(context);
            linearLayout1.addView(settingsSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(infoContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
            Bundle args = new Bundle();
            args.putLong(ChatReactionsEditActivity.KEY_CHAT_ID, chatId);
            ChatReactionsEditActivity reactionsEditActivity = new ChatReactionsEditActivity(args);
            reactionsEditActivity.setInfo(info);
            presentFragment(reactionsEditActivity);
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
            logCell.setTextAndIcon(LocaleController.getString("EventLog", R.string.EventLog), R.drawable.group_log, false);
            logCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            logCell.setOnClickListener(v -> presentFragment(new ChannelAdminLogActivity(currentChat)));
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
        if (logCell != null) {
            infoContainer.addView(logCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        infoSectionCell = new ShadowSectionCell(context);
        linearLayout1.addView(infoSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!ChatObject.hasAdminRights(currentChat)) {
            infoContainer.setVisibility(View.GONE);
            settingsTopSectionCell.setVisibility(View.GONE);
        }

        if (!isChannel && info != null && info.can_set_stickers) {
            stickersContainer = new FrameLayout(context);
            stickersContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout1.addView(stickersContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            stickersCell = new TextSettingsCell(context);
            stickersCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            stickersCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            stickersContainer.addView(stickersCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            stickersCell.setOnClickListener(v -> {
                GroupStickersActivity groupStickersActivity = new GroupStickersActivity(currentChat.id);
                groupStickersActivity.setInfo(info);
                presentFragment(groupStickersActivity);
            });

            stickersInfoCell3 = new TextInfoPrivacyCell(context);
            stickersInfoCell3.setText(LocaleController.getString("GroupStickersInfo", R.string.GroupStickersInfo));
            linearLayout1.addView(stickersInfoCell3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (currentChat.creator) {
            deleteContainer = new FrameLayout(context);
            deleteContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout1.addView(deleteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            deleteCell = new TextSettingsCell(context);
            deleteCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
            deleteCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (isChannel) {
                deleteCell.setText(LocaleController.getString("ChannelDelete", R.string.ChannelDelete), false);
            } else {
                deleteCell.setText(LocaleController.getString("DeleteAndExitButton", R.string.DeleteAndExitButton), false);
            }
            deleteContainer.addView(deleteCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            deleteCell.setOnClickListener(v -> AlertsCreator.createClearOrDeleteDialogAlert(ChatEditActivity.this, false, true, false, currentChat, null, false, true, (param) -> {
                if (AndroidUtilities.isTablet()) {
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats, -chatId);
                } else {
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                }
                finishFragment();
                getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, -currentChat.id, null, currentChat, param);
            }, null));

            deleteInfoCell = new ShadowSectionCell(context);
            deleteInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout1.addView(deleteInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            if (!isChannel) {
                if (stickersInfoCell3 == null) {
                    infoSectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                }
            }
        }

        if (stickersInfoCell3 != null) {
            if (deleteInfoCell == null) {
                stickersInfoCell3.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            } else {
                stickersInfoCell3.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            }
        }

        undoView = new UndoView(context);
        sizeNotifierFrameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        nameTextView.setText(currentChat.title);
        nameTextView.setSelection(nameTextView.length());
        if (info != null) {
            descriptionTextView.setText(info.about);
        }
        setAvatar();
        updateFields(true);

        return fragmentView;
    }

    RLottieDrawable cameraDrawable;

    private void setAvatar() {
        if (avatarImage == null) {
            return;
        }
        TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat == null) {
            return;
        }
        currentChat = chat;
        boolean hasPhoto;
        if (currentChat.photo != null) {
            avatar = currentChat.photo.photo_small;
            ImageLocation location = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_SMALL);
            avatarImage.setForUserOrChat(currentChat, avatarDrawable);
            hasPhoto = location != null;
        } else {
            avatarImage.setImageDrawable(avatarDrawable);
            hasPhoto = false;
        }
        if (setAvatarCell != null) {
            if (hasPhoto || imageUpdater.isUploadingImage()) {
                setAvatarCell.setTextAndIcon(LocaleController.getString("ChatSetNewPhoto", R.string.ChatSetNewPhoto), R.drawable.menu_camera2, true);
            } else {
                setAvatarCell.setTextAndIcon(LocaleController.getString("ChatSetPhotoOrVideo", R.string.ChatSetPhotoOrVideo), R.drawable.menu_camera2, true);
            }
            if (cameraDrawable == null) {
                cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, AndroidUtilities.dp(50), AndroidUtilities.dp(50), false, null);
            }
            setAvatarCell.imageView.setTranslationY(-AndroidUtilities.dp(9));
            setAvatarCell.imageView.setTranslationX(-AndroidUtilities.dp(8));
            setAvatarCell.imageView.setAnimation(cameraDrawable);
        }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().checkCurrentImageVisibility();
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
                historyHidden = !ChatObject.isChannel(currentChat) || info.hidden_prehistory;
                updateFields(false);
                if (infoWasEmpty) {
                    loadLinksCount();
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                setAvatar();
            }
        } else if (id == NotificationCenter.chatAvailableReactionsUpdated) {
            long chatId = (long) args[0];
            if (chatId == this.chatId) {
                info = getMessagesController().getChatFull(chatId);
                if (info != null) {
                    availableReactions = info.available_reactions;
                }
                updateReactionsCell();
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
    public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize) {
        AndroidUtilities.runOnUIThread(() -> {
            avatar = smallSize.location;
            if (photo != null || video != null) {
                getMessagesController().changeChatAvatar(chatId, null, photo, video, videoStartTimestamp, videoPath, smallSize.location, bigSize.location, null);
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
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, currentChat);
                setAvatarCell.setTextAndIcon(LocaleController.getString("ChatSetNewPhoto", R.string.ChatSetNewPhoto), R.drawable.menu_camera2, true);
                if (cameraDrawable == null) {
                    cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, AndroidUtilities.dp(50), AndroidUtilities.dp(50), false, null);
                }
                setAvatarCell.imageView.setTranslationY(-AndroidUtilities.dp(9));
                setAvatarCell.imageView.setTranslationX(-AndroidUtilities.dp(8));
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
        String about = info != null && info.about != null ? info.about : "";
        if (info != null && ChatObject.isChannel(currentChat) && info.hidden_prehistory != historyHidden ||
                nameTextView != null && !currentChat.title.equals(nameTextView.getText().toString()) ||
                descriptionTextView != null && !about.equals(descriptionTextView.getText().toString()) ||
                signMessages != currentChat.signatures) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            if (isChannel) {
                builder.setMessage(LocaleController.getString("ChannelSettingsChangedAlert", R.string.ChannelSettingsChangedAlert));
            } else {
                builder.setMessage(LocaleController.getString("GroupSettingsChangedAlert", R.string.GroupSettingsChangedAlert));
            }
            builder.setPositiveButton(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
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
            AndroidUtilities.shakeView(nameTextView, 2, 0);
            return;
        }
        donePressed = true;
        if (!ChatObject.isChannel(currentChat) && !historyHidden) {
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
                getMessagesController().toogleChannelInvitesHistory(chatId, historyHidden);
            }
        }

        if (imageUpdater.isUploadingImage()) {
            createAfterUpload = true;
            progressDialog = new AlertDialog(getParentActivity(), 3);
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
        if (signMessages != currentChat.signatures) {
            currentChat.signatures = true;
            getMessagesController().toogleChannelSignatures(chatId, signMessages);
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

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (chatFull != null) {
            if (currentChat == null) {
                currentChat = getMessagesController().getChat(chatId);
            }
            historyHidden = !ChatObject.isChannel(currentChat) || info.hidden_prehistory;
            availableReactions = info.available_reactions;
        }
    }

    private void updateFields(boolean updateChat) {
        if (updateChat) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null) {
                currentChat = chat;
            }
        }
        boolean isPrivate = TextUtils.isEmpty(currentChat.username);

        if (historyCell != null) {
            if (info != null && info.location instanceof TLRPC.TL_channelLocation) {
                historyCell.setVisibility(View.GONE);
            } else {
                historyCell.setVisibility(isPrivate && (info == null || info.linked_chat_id == 0) ? View.VISIBLE : View.GONE);
            }
        }

        if (settingsSectionCell != null) {
            settingsSectionCell.setVisibility(signCell == null && typeCell == null && (linkedCell == null || linkedCell.getVisibility() != View.VISIBLE) && (historyCell == null || historyCell.getVisibility() != View.VISIBLE) && (locationCell == null || locationCell.getVisibility() != View.VISIBLE) ? View.GONE : View.VISIBLE);
        }

        if (logCell != null) {
            logCell.setVisibility(!currentChat.megagroup || currentChat.gigagroup || info != null && info.participants_count > 200 ? View.VISIBLE : View.GONE);
        }

        if (linkedCell != null) {
            if (info == null || !isChannel && info.linked_chat_id == 0) {
                linkedCell.setVisibility(View.GONE);
            } else {
                linkedCell.setVisibility(View.VISIBLE);
                if (info.linked_chat_id == 0) {
                    linkedCell.setTextAndValue(LocaleController.getString("Discussion", R.string.Discussion), LocaleController.getString("DiscussionInfo", R.string.DiscussionInfo), true);
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
                    if (chat == null) {
                        linkedCell.setVisibility(View.GONE);
                    } else {
                        if (isChannel) {
                            if (TextUtils.isEmpty(chat.username)) {
                                linkedCell.setTextAndValue(LocaleController.getString("Discussion", R.string.Discussion), chat.title, true);
                            } else {
                                linkedCell.setTextAndValue(LocaleController.getString("Discussion", R.string.Discussion), "@" + chat.username, true);
                            }
                        } else {
                            if (TextUtils.isEmpty(chat.username)) {
                                linkedCell.setTextAndValue(LocaleController.getString("LinkedChannel", R.string.LinkedChannel), chat.title, false);
                            } else {
                                linkedCell.setTextAndValue(LocaleController.getString("LinkedChannel", R.string.LinkedChannel), "@" + chat.username, false);
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
                    locationCell.setTextAndValue(LocaleController.getString("AttachLocation", R.string.AttachLocation), location.address, true);
                } else {
                    locationCell.setTextAndValue(LocaleController.getString("AttachLocation", R.string.AttachLocation), "Unknown address", true);
                }
            } else {
                locationCell.setVisibility(View.GONE);
            }
        }

        if (typeCell != null) {
            if (info != null && info.location instanceof TLRPC.TL_channelLocation) {
                String link;
                if (isPrivate) {
                    link = LocaleController.getString("TypeLocationGroupEdit", R.string.TypeLocationGroupEdit);
                } else {
                    link = String.format("https://" + getMessagesController().linkPrefix + "/%s", currentChat.username);
                }
                typeCell.setTextAndValue(LocaleController.getString("TypeLocationGroup", R.string.TypeLocationGroup), link, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE);
            } else {
                String type;
                boolean isRestricted = currentChat.noforwards;
                if (isChannel) {
                    type = isPrivate ? isRestricted ? LocaleController.getString("TypePrivateRestrictedForwards", R.string.TypePrivateRestrictedForwards) : LocaleController.getString("TypePrivate", R.string.TypePrivate) : LocaleController.getString("TypePublic", R.string.TypePublic);
                } else {
                    type = isPrivate ? isRestricted ? LocaleController.getString("TypePrivateGroupRestrictedForwards", R.string.TypePrivateGroupRestrictedForwards) : LocaleController.getString("TypePrivateGroup", R.string.TypePrivateGroup) : LocaleController.getString("TypePublicGroup", R.string.TypePublicGroup);
                }
                if (isChannel) {
                    typeCell.setTextAndValue(LocaleController.getString("ChannelType", R.string.ChannelType), type, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE);
                } else {
                    typeCell.setTextAndValue(LocaleController.getString("GroupType", R.string.GroupType), type, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE);
                }
            }
        }

        if (historyCell != null) {
            String type = historyHidden ? LocaleController.getString("ChatHistoryHidden", R.string.ChatHistoryHidden) : LocaleController.getString("ChatHistoryVisible", R.string.ChatHistoryVisible);
            historyCell.setTextAndValue(LocaleController.getString("ChatHistory", R.string.ChatHistory), type, false);
        }

        if (stickersCell != null) {
            if (info != null && info.stickerset != null) {
                stickersCell.setTextAndValue(LocaleController.getString("GroupStickers", R.string.GroupStickers), info.stickerset.title, false);
            } else {
                stickersCell.setText(LocaleController.getString("GroupStickers", R.string.GroupStickers), false);
            }
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
                    membersCell.setTextAndValueAndIcon(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers), String.format("%d", info.participants_count), R.drawable.actions_viewmembers, true);
                    blockCell.setTextAndValueAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", Math.max(info.banned_count, info.kicked_count)), R.drawable.actions_removed, logCell != null && logCell.getVisibility() == View.VISIBLE);
                } else {
                    if (ChatObject.isChannel(currentChat)) {
                        membersCell.setTextAndValueAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants_count), R.drawable.actions_viewmembers, true);
                    } else {
                        membersCell.setTextAndValueAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants.participants.size()), R.drawable.actions_viewmembers, memberRequestsCell.getVisibility() == View.VISIBLE);
                    }
                    if (currentChat.gigagroup) {
                        blockCell.setTextAndValueAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", Math.max(info.banned_count, info.kicked_count)), R.drawable.actions_removed, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    } else {
                        int count = 0;
                        if (currentChat.default_banned_rights != null) {
                            if (!currentChat.default_banned_rights.send_stickers) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.send_media) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.embed_links) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.send_messages) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.pin_messages) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.send_polls) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.invite_users) {
                                count++;
                            }
                            if (!currentChat.default_banned_rights.change_info) {
                                count++;
                            }
                        } else {
                            count = 8;
                        }
                        blockCell.setTextAndValueAndIcon(LocaleController.getString("ChannelPermissions", R.string.ChannelPermissions), String.format("%d/%d", count, 8), R.drawable.actions_permissions, true);
                    }
                    if (memberRequestsCell != null) {
                        memberRequestsCell.setTextAndValueAndIcon(LocaleController.getString("MemberRequests", R.string.MemberRequests), String.format("%d", info.requests_pending), R.drawable.actions_requests, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    }
                }
                adminCell.setTextAndValueAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), String.format("%d", ChatObject.isChannel(currentChat) ? info.admins_count : getAdminCount()), R.drawable.actions_addadmin, true);
            } else {
                if (isChannel) {
                    membersCell.setTextAndIcon(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers), R.drawable.actions_viewmembers, true);
                    blockCell.setTextAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), R.drawable.actions_removed, logCell != null && logCell.getVisibility() == View.VISIBLE);
                } else {
                    membersCell.setTextAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), R.drawable.actions_viewmembers, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    if (currentChat.gigagroup) {
                        blockCell.setTextAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), R.drawable.actions_removed, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    } else {
                        blockCell.setTextAndIcon(LocaleController.getString("ChannelPermissions", R.string.ChannelPermissions), R.drawable.actions_permissions, true);
                    }
                }
                adminCell.setTextAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), R.drawable.actions_addadmin, true);
            }
            reactionsCell.setVisibility(ChatObject.canChangeChatInfo(currentChat) ? View.VISIBLE : View.GONE);
            updateReactionsCell();
            if (info == null || !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) || (!isPrivate && currentChat.creator)) {
                inviteLinksCell.setVisibility(View.GONE);
            } else {
                if (info.invitesCount > 0) {
                    inviteLinksCell.setTextAndValueAndIcon(LocaleController.getString("InviteLinks", R.string.InviteLinks), Integer.toString(info.invitesCount), R.drawable.actions_link, true);
                } else {
                    inviteLinksCell.setTextAndValueAndIcon(LocaleController.getString("InviteLinks", R.string.InviteLinks), "1", R.drawable.actions_link, true);
                }
            }
        }

        if (stickersCell != null && info != null) {
            if (info.stickerset != null) {
                stickersCell.setTextAndValue(LocaleController.getString("GroupStickers", R.string.GroupStickers), info.stickerset.title, false);
            } else {
                stickersCell.setText(LocaleController.getString("GroupStickers", R.string.GroupStickers), false);
            }
        }
    }

    private void updateReactionsCell() {
        int count = 0;
        for (int i = 0; i < availableReactions.size(); i++) {
            TLRPC.TL_availableReaction reaction = getMediaDataController().getReactionsMap().get(availableReactions.get(i));
            if (reaction != null && !reaction.inactive) {
                 count++;
            }
        }
        int reacts = Math.min(getMediaDataController().getEnabledReactionsList().size(), count);
        reactionsCell.setTextAndValueAndIcon(LocaleController.getString("Reactions", R.string.Reactions), reacts == 0 ? LocaleController.getString("ReactionsOff", R.string.ReactionsOff) :
                LocaleController.formatString("ReactionsCount", R.string.ReactionsCount, reacts, getMediaDataController().getEnabledReactionsList().size()), R.drawable.actions_reactions, true);
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
        themeDescriptions.add(new ThemeDescription(infoSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(signCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(deleteCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(deleteCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(stickersCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(stickersCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(stickersInfoCell3, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(stickersInfoCell3, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

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

        return themeDescriptions;
    }
}
