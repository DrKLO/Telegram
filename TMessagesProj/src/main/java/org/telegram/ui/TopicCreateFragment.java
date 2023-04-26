package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.Forum.ForumBubbleDrawable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.ReplaceableIconDrawable;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;

public class TopicCreateFragment extends BaseFragment {

    private final static int CREATE_ID = 1;
    private final static int EDIT_ID = 2;
    long chatId;
    long selectedEmojiDocumentId;
    int topicId;

    TextCheckCell2 checkBoxCell;
    EditTextBoldCursor editTextBoldCursor;
    SelectAnimatedEmojiDialog selectAnimatedEmojiDialog;
    BackupImageView[] backupImageView = new BackupImageView[2];

    String firstSymbol = "";
    boolean created;
    Drawable defaultIconDrawable;
    ReplaceableIconDrawable replaceableIconDrawable;
    TLRPC.TL_forumTopic topicForEdit;
    ForumBubbleDrawable forumBubbleDrawable;

    int iconColor;

    public static TopicCreateFragment create(long chatId, int topicId) {
        Bundle bundle = new Bundle();
        bundle.putLong("chat_id", chatId);
        bundle.putInt("topic_id", topicId);
        return new TopicCreateFragment(bundle);
    }

    private TopicCreateFragment(Bundle bundle) {
        super(bundle);
    }

    @Override
    public boolean onFragmentCreate() {
        chatId = arguments.getLong("chat_id");
        topicId = arguments.getInt("topic_id", 0);
        if (topicId != 0) {
            topicForEdit = getMessagesController().getTopicsController().findTopic(chatId, topicId);
            if (topicForEdit == null) {
                return false;
            }
            iconColor = topicForEdit.icon_color;
        } else {
            iconColor = ForumBubbleDrawable.serverSupportedColor[Math.abs(Utilities.random.nextInt() % ForumBubbleDrawable.serverSupportedColor.length)];
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        if (topicForEdit != null) {
            actionBar.setTitle(LocaleController.getString("EditTopic", R.string.EditTopic));
        } else {
            actionBar.setTitle(LocaleController.getString("NewTopic", R.string.NewTopic));
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    return;
                }
                if (id == CREATE_ID) {
                    String topicName = editTextBoldCursor.getText() == null ? null : editTextBoldCursor.getText().toString();
                    if (TextUtils.isEmpty(topicName)) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(editTextBoldCursor);
                        return;
                    }

                    if (created) {
                        return;
                    }

                    final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.showDelayed(500);
                    created = true;

                    TLRPC.TL_channels_createForumTopic reqSend = new TLRPC.TL_channels_createForumTopic();

                    reqSend.channel = getMessagesController().getInputChannel(chatId);
                    reqSend.title = topicName;
                    if (selectedEmojiDocumentId != 0) {
                        reqSend.icon_emoji_id = selectedEmojiDocumentId;
                        reqSend.flags |= 8;
                    }
                    long randomId = Utilities.random.nextLong();
                    reqSend.random_id = randomId;
                    reqSend.icon_color = iconColor;
                    reqSend.flags |= 1;

                    ConnectionsManager.getInstance(currentAccount).sendRequest(reqSend, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response != null) {
                            TLRPC.Updates updates = (TLRPC.Updates) response;
                            for (int i = 0; i < updates.updates.size(); i++) {
                                if (updates.updates.get(i) instanceof TLRPC.TL_updateMessageID) {
                                    TLRPC.TL_updateMessageID updateMessageID = (TLRPC.TL_updateMessageID) updates.updates.get(i);
                                    Bundle args = new Bundle();
                                    args.putLong("chat_id", chatId);
                                    args.putInt("message_id", 1);
                                    args.putInt("unread_count", 0);
                                    args.putBoolean("historyPreloaded", false);
                                    ChatActivity chatActivity = new ChatActivity(args);
                                    TLRPC.TL_messageActionTopicCreate actionMessage = new TLRPC.TL_messageActionTopicCreate();
                                    actionMessage.title = topicName;
                                    TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                                    message.action = actionMessage;
                                    message.peer_id = getMessagesController().getPeer(-chatId);
                                    message.dialog_id = -chatId;
                                    message.id = updateMessageID.id;
                                    message.date = (int) (System.currentTimeMillis() / 1000);

                                    ArrayList<MessageObject> messageObjects = new ArrayList<>();
                                    messageObjects.add(new MessageObject(currentAccount, message, false, false));
                                    TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
                                    TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();
                                    forumTopic.id = updateMessageID.id;
                                    if (selectedEmojiDocumentId != 0) {
                                        forumTopic.icon_emoji_id = selectedEmojiDocumentId;
                                        forumTopic.flags |= 1;
                                    }
                                    forumTopic.my = true;
                                    forumTopic.flags |= 2;
                                    forumTopic.topicStartMessage = message;
                                    forumTopic.title = topicName;
                                    forumTopic.top_message = message.id;
                                    forumTopic.topMessage = message;
                                    forumTopic.from_id = getMessagesController().getPeer(getUserConfig().clientUserId);
                                    forumTopic.notify_settings = new TLRPC.TL_peerNotifySettings();
                                    forumTopic.icon_color = iconColor;

                                    chatActivity.setThreadMessages(messageObjects, chatLocal, message.id, 1, 1, forumTopic);
                                    chatActivity.justCreatedTopic = true;
                                    getMessagesController().getTopicsController().onTopicCreated(-chatId, forumTopic, true);
                                    presentFragment(chatActivity);
                                }
                            }
                        }
                        progressDialog.dismiss();
                    }));
                } else if (id == EDIT_ID) {

                    String topicName = editTextBoldCursor.getText() == null ? null : editTextBoldCursor.getText().toString();
                    if (TextUtils.isEmpty(topicName)) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(editTextBoldCursor);
                        return;
                    }
                    if (!topicForEdit.title.equals(topicName) || topicForEdit.icon_emoji_id != selectedEmojiDocumentId) {
                        TLRPC.TL_channels_editForumTopic editForumRequest = new TLRPC.TL_channels_editForumTopic();
                        editForumRequest.channel = getMessagesController().getInputChannel(chatId);
                        editForumRequest.topic_id = topicForEdit.id;
                        if (!topicForEdit.title.equals(topicName)) {
                            editForumRequest.title = topicName;
                            editForumRequest.flags |= 1;
                        }
                        if (topicForEdit.icon_emoji_id != editForumRequest.icon_emoji_id) {
                            editForumRequest.icon_emoji_id = selectedEmojiDocumentId;
                            editForumRequest.flags |= 2;
                        }
//                        if (checkBoxCell != null ) {
//                            editForumRequest.hidden = !checkBoxCell.isChecked();
//                            editForumRequest.flags |= 8;
//                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequest(editForumRequest, (response, error) -> {

                        });
                    }
                    if (checkBoxCell != null && topicForEdit.id == 1 && !checkBoxCell.isChecked() != topicForEdit.hidden) {
                        TLRPC.TL_channels_editForumTopic editForumRequest = new TLRPC.TL_channels_editForumTopic();
                        editForumRequest.channel = getMessagesController().getInputChannel(chatId);
                        editForumRequest.topic_id = topicForEdit.id;
                        editForumRequest.hidden = !checkBoxCell.isChecked();
                        editForumRequest.flags |= 8;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(editForumRequest, (response, error) -> {

                        });
                    }

                    topicForEdit.icon_emoji_id = selectedEmojiDocumentId;
                    if (selectedEmojiDocumentId != 0) {
                        topicForEdit.flags |= 1;
                    } else {
                        topicForEdit.flags &= ~1;
                    }
                    topicForEdit.title = topicName;
                    if (checkBoxCell != null) {
                        topicForEdit.hidden = !checkBoxCell.isChecked();
                    }
                    getMessagesController().getTopicsController().onTopicEdited(-chatId, topicForEdit);
                    finishFragment();
                }
            }
        });
        if (topicForEdit == null) {
            actionBar.createMenu().addItem(CREATE_ID, LocaleController.getString("Create", R.string.Create).toUpperCase());
        } else {
            actionBar.createMenu().addItem(EDIT_ID, R.drawable.ic_ab_done);
        }

        FrameLayout contentView = new SizeNotifierFrameLayout(context) {
            boolean keyboardWasShown;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                measureKeyboardHeight();
                if (getKeyboardHeight() == 0 && !keyboardWasShown) {
                    SharedPreferences sharedPreferences = MessagesController.getGlobalEmojiSettings();
                    keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200));
                    setPadding(0, 0, 0, keyboardHeight);
                } else {
                    keyboardWasShown = true;
                    setPadding(0, 0, 0, 0);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        fragmentView = contentView;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(linearLayout);

        HeaderCell headerCell = new HeaderCell(context);
        if (topicForEdit != null && topicForEdit.id == 1) {
            headerCell.setText(LocaleController.getString("CreateGeneralTopicTitle", R.string.CreateGeneralTopicTitle));
        } else {
            headerCell.setText(LocaleController.getString("CreateTopicTitle", R.string.CreateTopicTitle));
        }

        FrameLayout editTextContainer = new FrameLayout(context);

        editTextBoldCursor = new EditTextBoldCursor(context);
        editTextBoldCursor.setHintText(LocaleController.getString("EnterTopicName", R.string.EnterTopicName));
        editTextBoldCursor.setHintColor(getThemedColor(Theme.key_chat_messagePanelHint));
        editTextBoldCursor.setTextColor(getThemedColor(Theme.key_chat_messagePanelText));
        editTextBoldCursor.setPadding(AndroidUtilities.dp(0), editTextBoldCursor.getPaddingTop(), AndroidUtilities.dp(0), editTextBoldCursor.getPaddingBottom());
        editTextBoldCursor.setBackgroundDrawable(null);
        editTextBoldCursor.setSingleLine(true);
        editTextBoldCursor.setInputType(editTextBoldCursor.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editTextContainer.addView(editTextBoldCursor, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 51, 4, 21, 4));

        editTextBoldCursor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String trimmedString = s.toString().trim();
                String oldFirstSymbol = firstSymbol;
                if (trimmedString.length() > 0) {
                    firstSymbol = trimmedString.substring(0, 1).toUpperCase();
                } else {
                    firstSymbol = "";
                }
                if (!oldFirstSymbol.equals(firstSymbol)) {
                    LetterDrawable letterDrawable = new LetterDrawable(null, LetterDrawable.STYLE_TOPIC_DRAWABLE);
                    letterDrawable.setTitle(firstSymbol);
                    if (replaceableIconDrawable != null) {
                        replaceableIconDrawable.setIcon(letterDrawable, true);
                    }
                }
            }
        });
        FrameLayout iconContainer = new FrameLayout(context) {

            ValueAnimator backAnimator;
            boolean pressed;
            float pressedProgress;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                float s = 0.8f + 0.2f * (1f - pressedProgress);
                canvas.save();
                canvas.scale(s, s, getMeasuredHeight() / 2f, getMeasuredWidth() / 2f);
                super.dispatchDraw(canvas);
                canvas.restore();
                updatePressedProgress();
            }

            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                if (this.pressed != pressed) {
                    this.pressed = pressed;
                    invalidate();
                    if (pressed) {
                        if (backAnimator != null) {
                            backAnimator.removeAllListeners();
                            backAnimator.cancel();
                        }
                    }
                    if (!pressed && pressedProgress != 0) {
                        backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                        backAnimator.addUpdateListener(animation -> {
                            pressedProgress = (float) animation.getAnimatedValue();
                            invalidate();
                        });
                        backAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                backAnimator = null;
                            }
                        });
                        backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                        backAnimator.setDuration(350);
                        backAnimator.start();
                    }
                }
            }

            public void updatePressedProgress() {
                if (isPressed() && pressedProgress != 1f) {
                    pressedProgress = Utilities.clamp(pressedProgress + 16f / 100f, 1f, 0);
                    invalidate();
                }
            }
        };
        iconContainer.setOnClickListener(v -> {
            if (selectedEmojiDocumentId == 0 && topicForEdit == null) {
                iconColor = forumBubbleDrawable.moveNexColor();
            }
        });
        for (int i = 0; i < 2; i++) {
            backupImageView[i] = new BackupImageView(context);
            iconContainer.addView(backupImageView[i], LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        }
        editTextContainer.addView(iconContainer, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        linearLayout.addView(headerCell);
        linearLayout.addView(editTextContainer);


        FrameLayout emojiContainer = new FrameLayout(context);
        Drawable shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_top, Theme.getColor(Theme.key_windowBackgroundGrayShadow));
        Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
        combinedDrawable.setFullsize(true);
        emojiContainer.setBackgroundDrawable(combinedDrawable);
        emojiContainer.setClipChildren(false);

        if (topicForEdit == null || topicForEdit.id != 1) {
            selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(this, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_TOPIC_ICON, null) {

                private boolean firstLayout = true;

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (firstLayout) {
                        firstLayout = false;
                        selectAnimatedEmojiDialog.onShow(null);
                    }
                }

                protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, Integer until) {
                    boolean setIsFree = false;
                    if (!TextUtils.isEmpty(UserConfig.getInstance(currentAccount).defaultTopicIcons)) {
                        TLRPC.TL_messages_stickerSet stickerSet = getMediaDataController().getStickerSetByEmojiOrName(UserConfig.getInstance(currentAccount).defaultTopicIcons);
                        long stickerSetId = stickerSet == null ? 0 : stickerSet.set.id;
                        long documentSetId = MediaDataController.getStickerSetId(document);
                        setIsFree = stickerSetId == documentSetId;
                    }

                    selectEmoji(documentId, setIsFree);
                }
            };

            selectAnimatedEmojiDialog.setAnimationsEnabled(fragmentBeginToShow);
            selectAnimatedEmojiDialog.setClipChildren(false);
            emojiContainer.addView(selectAnimatedEmojiDialog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 12, 12, 12, 12));

            Drawable drawable = ForumUtilities.createTopicDrawable("", iconColor);
            CombinedDrawable topicCombinedDrawable = (CombinedDrawable) drawable;
            forumBubbleDrawable = (ForumBubbleDrawable) topicCombinedDrawable.getBackgroundDrawable();

            replaceableIconDrawable = new ReplaceableIconDrawable(context);
            CombinedDrawable combinedDrawable2 = new CombinedDrawable(drawable, replaceableIconDrawable, 0, 0);
            combinedDrawable2.setFullsize(true);

            selectAnimatedEmojiDialog.setForumIconDrawable(combinedDrawable2);

            defaultIconDrawable = combinedDrawable2;

            replaceableIconDrawable.addView(backupImageView[0]);
            replaceableIconDrawable.addView(backupImageView[1]);

            backupImageView[0].setImageDrawable(defaultIconDrawable);
            AndroidUtilities.updateViewVisibilityAnimated(backupImageView[0], true, 1, false);
            AndroidUtilities.updateViewVisibilityAnimated(backupImageView[1], false, 1, false);

            forumBubbleDrawable.addParent(backupImageView[0]);
            forumBubbleDrawable.addParent(backupImageView[1]);
        } else {
            ImageView imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.msg_filled_general);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_inMenu), PorterDuff.Mode.MULTIPLY));
            iconContainer.addView(imageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));

            emojiContainer.addView(
                new ActionBarPopupWindow.GapView(context, getResourceProvider()),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8)
            );

            checkBoxCell = new TextCheckCell2(context);
            checkBoxCell.getCheckBox().setDrawIconType(0);
            checkBoxCell.setTextAndCheck(LocaleController.getString("EditTopicHide", R.string.EditTopicHide), !topicForEdit.hidden, false);
            checkBoxCell.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)));
            checkBoxCell.setOnClickListener(e -> {
                checkBoxCell.setChecked(!checkBoxCell.isChecked());
            });
            emojiContainer.addView(checkBoxCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP, 0, 8, 0, 0));

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
            infoCell.setText(LocaleController.getString("EditTopicHideInfo", R.string.EditTopicHideInfo));
            infoCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow, getResourceProvider()));
            emojiContainer.addView(infoCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 8 + 50, 0, 0));
        }
        linearLayout.addView(emojiContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (topicForEdit != null) {
            editTextBoldCursor.setText(topicForEdit.title);
            selectEmoji(topicForEdit.icon_emoji_id, true);
        } else {
            selectEmoji(0L, true);
        }

        return fragmentView;
    }

    private void selectEmoji(Long documentId, boolean free) {
        if (selectAnimatedEmojiDialog == null || replaceableIconDrawable == null) {
            return;
        }
        long docId = documentId == null ? 0L : documentId;
        selectAnimatedEmojiDialog.setSelected(docId);
        if (selectedEmojiDocumentId == docId) {
            return;
        }

        if (!free && docId != 0 && !getUserConfig().isPremium()) {
            TLRPC.Document emoji = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
            if (emoji != null) {
                BulletinFactory.of(this)
                        .createEmojiBulletin(
                                emoji,
                                AndroidUtilities.replaceTags(LocaleController.getString("UnlockPremiumEmojiHint", R.string.UnlockPremiumEmojiHint)),
                                LocaleController.getString("PremiumMore", R.string.PremiumMore),
                                () -> {
                                    new PremiumFeatureBottomSheet(this, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show();
                                }
                        ).show();
            }
            return;
        }

        selectedEmojiDocumentId = docId;

        if (docId != 0) {
            AnimatedEmojiDrawable animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC, currentAccount, docId);
            animatedEmojiDrawable.setColorFilter(Theme.chat_animatedEmojiTextColorFilter);
            backupImageView[1].setAnimatedEmojiDrawable(animatedEmojiDrawable);
            backupImageView[1].setImageDrawable(null);
        } else {
            LetterDrawable letterDrawable = new LetterDrawable(null, LetterDrawable.STYLE_TOPIC_DRAWABLE);
            letterDrawable.setTitle(firstSymbol);
            replaceableIconDrawable.setIcon(letterDrawable, false);
            backupImageView[1].setImageDrawable(defaultIconDrawable);
            backupImageView[1].setAnimatedEmojiDrawable(null);
        }

        BackupImageView tmp = backupImageView[0];
        backupImageView[0] = backupImageView[1];
        backupImageView[1] = tmp;

        AndroidUtilities.updateViewVisibilityAnimated(backupImageView[0], true, 0.5f, true);
        AndroidUtilities.updateViewVisibilityAnimated(backupImageView[1], false, 0.5f, true);
    }

    AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if (isOpen) {
            notificationsLocker.lock();
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (!isOpen && created) {
            removeSelfFromStack();
        }

        notificationsLocker.unlock();
        if (selectAnimatedEmojiDialog != null) {
            selectAnimatedEmojiDialog.setAnimationsEnabled(fragmentBeginToShow);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        editTextBoldCursor.requestFocus();
        AndroidUtilities.showKeyboard(editTextBoldCursor);
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    public void showKeyboard() {
        editTextBoldCursor.requestFocus();
        AndroidUtilities.showKeyboard(editTextBoldCursor);
    }
}
