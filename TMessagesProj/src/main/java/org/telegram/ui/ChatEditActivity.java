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
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
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
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.concurrent.CountDownLatch;

public class ChatEditActivity extends BaseFragment implements ImageUpdater.ImageUpdaterDelegate, NotificationCenter.NotificationCenterDelegate {

    private View doneButton;

    private AlertDialog progressDialog;

    private LinearLayout avatarContainer;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private ImageView avatarEditor;
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
    private ShadowSectionCell settingsSectionCell;

    private TextCheckCell signCell;

    private FrameLayout stickersContainer;
    private TextSettingsCell stickersCell;
    private TextInfoPrivacyCell stickersInfoCell3;

    private LinearLayout infoContainer;
    private TextCell membersCell;
    private TextCell adminCell;
    private TextCell blockCell;
    private TextCell logCell;
    private ShadowSectionCell infoSectionCell;

    private FrameLayout deleteContainer;
    private TextSettingsCell deleteCell;
    private ShadowSectionCell deleteInfoCell;

    private TLRPC.FileLocation avatar;
    private TLRPC.FileLocation avatarBig;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private int chatId;
    private TLRPC.InputFile uploadedAvatar;
    private boolean signMessages;

    private boolean isChannel;

    private boolean historyHidden;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    public ChatEditActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        imageUpdater = new ImageUpdater();
        chatId = args.getInt("chat_id", 0);
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (currentChat == null) {
            currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId);
            if (currentChat != null) {
                MessagesController.getInstance(currentAccount).putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = MessagesStorage.getInstance(currentAccount).loadChatInfo(chatId, new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }

        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        imageUpdater.parentFragment = this;
        imageUpdater.delegate = this;
        signMessages = currentChat.signatures;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
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
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nameTextView != null) {
            nameTextView.onPause();
        }
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

                int keyboardSize = getKeyboardHeight();
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

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? nameTextView.getEmojiPadding() : 0;
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
                            childTop = getMeasuredHeight() + getKeyboardHeight() - child.getMeasuredHeight();
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
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

        if (ChatObject.canChangeChatInfo(currentChat)) {
            avatarDrawable.setInfo(5, null, null);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0x55000000);

            avatarOverlay = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                        paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, AndroidUtilities.dp(32), paint);
                    }
                }
            };
            frameLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));
            avatarOverlay.setOnClickListener(view -> imageUpdater.openMenu(avatar != null, () -> {
                avatar = null;
                avatarBig = null;
                uploadedAvatar = null;
                showAvatarProgress(false, true);
                avatarImage.setImage(null, null, avatarDrawable, currentChat);
            }));
            avatarOverlay.setContentDescription(LocaleController.getString("ChoosePhoto", R.string.ChoosePhoto));

            avatarEditor = new ImageView(context) {
                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    avatarOverlay.invalidate();
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    avatarOverlay.invalidate();
                }
            };
            avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
            avatarEditor.setImageResource(R.drawable.menu_camera_av);
            avatarEditor.setEnabled(false);
            avatarEditor.setClickable(false);
            frameLayout.addView(avatarEditor, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

            avatarProgressView = new RadialProgressView(context);
            avatarProgressView.setSize(AndroidUtilities.dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

            showAvatarProgress(false, false);
        } else {
            avatarDrawable.setInfo(5, currentChat.title, null);
        }

        nameTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT);
        if (isChannel) {
            nameTextView.setHint(LocaleController.getString("EnterChannelName", R.string.EnterChannelName));
        } else {
            nameTextView.setHint(LocaleController.getString("GroupName", R.string.GroupName));
        }
        nameTextView.setEnabled(ChatObject.canChangeChatInfo(currentChat));
        nameTextView.setFocusable(nameTextView.isEnabled());
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        nameTextView.setFilters(inputFilters);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));

        settingsContainer = new LinearLayout(context);
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(settingsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
        settingsContainer.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 23, 12, 23, 6));
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

                HeaderCell headerCell = new HeaderCell(context, true, 23, 15, false);
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
        blockCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        blockCell.setVisibility(ChatObject.isChannel(currentChat) || currentChat.creator ? View.VISIBLE : View.GONE);
        blockCell.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chat_id", chatId);
            args.putInt("type", !isChannel ? ChatUsersActivity.TYPE_KICKED : ChatUsersActivity.TYPE_BANNED);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(info);
            presentFragment(fragment);
        });

        adminCell = new TextCell(context);
        adminCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        adminCell.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chat_id", chatId);
            args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(info);
            presentFragment(fragment);
        });

        membersCell = new TextCell(context);
        membersCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        membersCell.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("chat_id", chatId);
            args.putInt("type", ChatUsersActivity.TYPE_USERS);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(info);
            presentFragment(fragment);
        });

        if (ChatObject.isChannel(currentChat)) {
            logCell = new TextCell(context);
            logCell.setTextAndIcon(LocaleController.getString("EventLog", R.string.EventLog), R.drawable.group_log, false);
            logCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            logCell.setOnClickListener(v -> presentFragment(new ChannelAdminLogActivity(currentChat)));
        }

        if (!isChannel) {
            infoContainer.addView(blockCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        infoContainer.addView(adminCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        infoContainer.addView(membersCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (isChannel) {
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
            } else if (currentChat.megagroup) {
                deleteCell.setText(LocaleController.getString("DeleteMega", R.string.DeleteMega), false);
            } else {
                deleteCell.setText(LocaleController.getString("DeleteAndExitButton", R.string.DeleteAndExitButton), false);
            }
            deleteContainer.addView(deleteCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            deleteCell.setOnClickListener(v -> AlertsCreator.createClearOrDeleteDialogAlert(ChatEditActivity.this, false, true, false, currentChat, null, false, (param) -> {
                if (AndroidUtilities.isTablet()) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats, -(long) chatId);
                } else {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                }
                MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId()), info, true, false);
                finishFragment();
            }));

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

        nameTextView.setText(currentChat.title);
        nameTextView.setSelection(nameTextView.length());
        if (info != null) {
            descriptionTextView.setText(info.about);
        }
        if (currentChat.photo != null) {
            avatar = currentChat.photo.photo_small;
            avatarBig = currentChat.photo.photo_big;
            avatarImage.setImage(ImageLocation.getForChat(currentChat, false), "50_50", avatarDrawable, currentChat);
        } else {
            avatarImage.setImageDrawable(avatarDrawable);
        }

        updateFields(true);

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null && descriptionTextView != null) {
                    descriptionTextView.setText(chatFull.about);
                }
                info = chatFull;
                historyHidden = !ChatObject.isChannel(currentChat) || info.hidden_prehistory;
                updateFields(false);
            }
        }
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize) {
        AndroidUtilities.runOnUIThread(() -> {
            if (file != null) {
                uploadedAvatar = file;
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
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, currentChat);
                showAvatarProgress(true, false);
            }
        });
    }

    @Override
    public String getInitialSearchString() {
        return nameTextView.getText().toString();
    }

    private boolean checkDiscard() {
        String about = info != null && info.about != null ? info.about : "";
        if (info != null && ChatObject.isChannel(currentChat) && info.hidden_prehistory != historyHidden ||
                imageUpdater.uploadingImage != null ||
                nameTextView != null && !currentChat.title.equals(nameTextView.getText().toString()) ||
                descriptionTextView != null && !about.equals(descriptionTextView.getText().toString()) ||
                signMessages != currentChat.signatures ||
                uploadedAvatar != null || avatar == null && currentChat.photo instanceof TLRPC.TL_chatPhoto) {
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
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                chatId = param;
                currentChat = MessagesController.getInstance(currentAccount).getChat(param);
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
                MessagesController.getInstance(currentAccount).toogleChannelInvitesHistory(chatId, historyHidden);
            }
        }

        if (imageUpdater.uploadingImage != null) {
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
            MessagesController.getInstance(currentAccount).changeChatTitle(chatId, nameTextView.getText().toString());
        }
        String about = info != null && info.about != null ? info.about : "";
        if (descriptionTextView != null && !about.equals(descriptionTextView.getText().toString())) {
            MessagesController.getInstance(currentAccount).updateChatAbout(chatId, descriptionTextView.getText().toString(), info);
        }
        if (signMessages != currentChat.signatures) {
            currentChat.signatures = true;
            MessagesController.getInstance(currentAccount).toogleChannelSignatures(chatId, signMessages);
        }
        if (uploadedAvatar != null) {
            MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, uploadedAvatar, avatar, avatarBig);
        } else if (avatar == null && currentChat.photo instanceof TLRPC.TL_chatPhoto) {
            MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, null, null, null);
        }
        finishFragment();
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarEditor == null) {
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

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
            } else {
                avatarEditor.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarEditor == null) {
                        return;
                    }
                    if (show) {
                        avatarEditor.setVisibility(View.INVISIBLE);
                    } else {
                        avatarProgressView.setVisibility(View.INVISIBLE);
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
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.INVISIBLE);
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
            } else {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.VISIBLE);
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
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
            if (text != null && text.length() != 0) {
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
                currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
            }
            historyHidden = !ChatObject.isChannel(currentChat) || info.hidden_prehistory;
        }
    }

    private void updateFields(boolean updateChat) {
        if (updateChat) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
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
            logCell.setVisibility(!currentChat.megagroup || info != null && info.participants_count > 200 ? View.VISIBLE : View.GONE);
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
                    link = String.format("https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/%s", currentChat.username);
                }
                typeCell.setTextAndValue(LocaleController.getString("TypeLocationGroup", R.string.TypeLocationGroup), link, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE);
            } else {
                String type;
                if (isChannel) {
                    type = isPrivate ? LocaleController.getString("TypePrivate", R.string.TypePrivate) : LocaleController.getString("TypePublic", R.string.TypePublic);
                } else {
                    type = isPrivate ? LocaleController.getString("TypePrivateGroup", R.string.TypePrivateGroup) : LocaleController.getString("TypePublicGroup", R.string.TypePublicGroup);
                }
                if (isChannel) {
                    typeCell.setTextAndValue(LocaleController.getString("ChannelType", R.string.ChannelType), type, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE);
                } else {
                    typeCell.setTextAndValue(LocaleController.getString("GroupType", R.string.GroupType), type, historyCell != null && historyCell.getVisibility() == View.VISIBLE || linkedCell != null && linkedCell.getVisibility() == View.VISIBLE);
                }
            }
        }

        if (info != null && historyCell != null) {
            String type = historyHidden ? LocaleController.getString("ChatHistoryHidden", R.string.ChatHistoryHidden) : LocaleController.getString("ChatHistoryVisible", R.string.ChatHistoryVisible);
            historyCell.setTextAndValue(LocaleController.getString("ChatHistory", R.string.ChatHistory), type, false);
        }

        if (stickersCell != null) {
            if (info.stickerset != null) {
                stickersCell.setTextAndValue(LocaleController.getString("GroupStickers", R.string.GroupStickers), info.stickerset.title, false);
            } else {
                stickersCell.setText(LocaleController.getString("GroupStickers", R.string.GroupStickers), false);
            }
        }

        if (membersCell != null) {
            if (info != null) {
                if (isChannel) {
                    membersCell.setTextAndValueAndIcon(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers), String.format("%d", info.participants_count), R.drawable.actions_viewmembers, true);
                    blockCell.setTextAndValueAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", Math.max(info.banned_count, info.kicked_count)), R.drawable.actions_removed, logCell != null && logCell.getVisibility() == View.VISIBLE);
                } else {
                    if (ChatObject.isChannel(currentChat)) {
                        membersCell.setTextAndValueAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants_count), R.drawable.actions_viewmembers, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    } else {
                        membersCell.setTextAndValueAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), String.format("%d", info.participants.participants.size()), R.drawable.actions_viewmembers, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    }
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
                adminCell.setTextAndValueAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), String.format("%d", ChatObject.isChannel(currentChat) ? info.admins_count : getAdminCount()), R.drawable.actions_addadmin, true);
            } else {
                if (isChannel) {
                    membersCell.setTextAndIcon(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers), R.drawable.actions_viewmembers, true);
                    blockCell.setTextAndIcon(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), R.drawable.actions_removed, logCell != null && logCell.getVisibility() == View.VISIBLE);
                } else {
                    membersCell.setTextAndIcon(LocaleController.getString("ChannelMembers", R.string.ChannelMembers), R.drawable.actions_viewmembers, logCell != null && logCell.getVisibility() == View.VISIBLE);
                    blockCell.setTextAndIcon(LocaleController.getString("ChannelPermissions", R.string.ChannelPermissions), R.drawable.actions_permissions, true);
                }
                adminCell.setTextAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), R.drawable.actions_addadmin, true);
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

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (avatarImage != null) {
                avatarDrawable.setInfo(5, null, null);
                avatarImage.invalidate();
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(membersCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(membersCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(membersCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(adminCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(adminCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(adminCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(blockCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(blockCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(blockCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(logCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(logCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(logCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),

                new ThemeDescription(typeCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(typeCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(typeCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(historyCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(historyCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(historyCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(locationCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(locationCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(locationCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),

                new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),
                new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(avatarContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(settingsContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(typeEditContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(deleteContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(stickersContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(infoContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(settingsTopSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(settingsSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(deleteInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(infoSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(signCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(signCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(deleteCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(deleteCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(stickersCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(stickersCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(stickersInfoCell3, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(stickersInfoCell3, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.avatar_savedDrawable}, cellDelegate, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),
        };
    }
}
