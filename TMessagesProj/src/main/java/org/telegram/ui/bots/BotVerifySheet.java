package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.bots.AffiliateProgramFragment.percents;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextSuggestionsFix;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class BotVerifySheet {

    public static void openVerify(int currentAccount, long botId, TL_bots.botVerifierSettings settings) {
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment == null) return;

        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_BOT_SELECT_VERIFY);
        args.putBoolean("resetDelegate", false);

        final DialogsActivity fragment = new DialogsActivity(args);
        fragment.setCurrentAccount(currentAccount);
        fragment.setDelegate((fragment1, dids, _message, param, notify, scheduleDate, topicsFragment) -> {
            if (dids.isEmpty()) {
                return false;
            }
            final long dialogId = dids.get(0).dialogId;
            openSheet(fragment.getContext(), currentAccount, botId, dialogId, settings, (removed) -> {
                if (topicsFragment != null) {
                    topicsFragment.finishFragment();
                    fragment.removeSelfFromStack();
                } else {
                    fragment.finishFragment();
                }
                BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                if (lastFragment2 == null) return;

                String dialogTitle;
                TLObject dialog;
                if (dialogId >= 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    dialogTitle = UserObject.getForcedFirstName(user);
                    dialog = user;
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    dialogTitle = chat == null ? "" : chat.title;
                    dialog = chat;
                }
                BulletinFactory.of(lastFragment2)
                    .createUsersBulletin(dialog, AndroidUtilities.replaceTags(formatString(removed ? R.string.BotSentRevokeVerifyRequest : R.string.BotSentVerifyRequest, dialogTitle)))
                    .show(false);
            });
            return true;
        });
        lastFragment.presentFragment(fragment);
    }

    public static void openSheet(Context context, int currentAccount, long botId, long dialogId, TL_bots.botVerifierSettings settings, Utilities.Callback<Boolean> whenSent) {
        if (context == null) {
            return;
        }

        TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(botId);
        String dialogTitle;
        TLObject dialog;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (dialogId >= 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            dialogTitle = UserObject.getForcedFirstName(user);
            dialog = user;
            if (user.bot_verification_icon == settings.icon) {
                openRemoveVerify(context, currentAccount, botId, dialogId, settings, whenSent);
                return;
            }
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            dialogTitle = chat == null ? "" : chat.title;
            dialog = chat;
            if (chat.bot_verification_icon == settings.icon) {
                openRemoveVerify(context, currentAccount, botId, dialogId, settings, whenSent);
                return;
            }
        }

        BottomSheet.Builder b = new BottomSheet.Builder(context, true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

//        FrameLayout topView = new FrameLayout(context);
//
//        BackupImageView imageView1 = new BackupImageView(context);
//        imageView1.setRoundRadius(dp(30));
//        AvatarDrawable avatarDrawable = new AvatarDrawable();
//        avatarDrawable.setInfo(botUser);
//        imageView1.setForUserOrChat(botUser, avatarDrawable);
//        topView.addView(imageView1, LayoutHelper.createFrame(60, 60, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, 0, 0));
//
//        ImageView arrowView = new ImageView(context);
//        arrowView.setImageResource(R.drawable.msg_arrow_avatar);
//        arrowView.setScaleType(ImageView.ScaleType.CENTER);
//        arrowView.setTranslationX(-dp(8.33f / 4));
//        arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
//        topView.addView(arrowView, LayoutHelper.createFrame(36, 60, Gravity.CENTER, 60, 0, 60, 0));
//
//        BackupImageView imageView2 = new BackupImageView(context);
//        imageView2.setRoundRadius(dp(30));
//        avatarDrawable = new AvatarDrawable();
//        avatarDrawable.setInfo(dialog);
//        imageView2.setForUserOrChat(dialog, avatarDrawable);
//        topView.addView(imageView2, LayoutHelper.createFrame(60, 60, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 5.66f, 0));
//
//        BackupImageView iconBgView = new BackupImageView(context);
////        iconBgView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, settings.icon));
////        iconBgView.setEmojiColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.SRC_IN));
//        iconBgView.setScaleX(1.25f);
//        iconBgView.setScaleY(1.25f);
//        iconBgView.setBackground(Theme.createCircleDrawable(dp(24), Theme.getColor(Theme.key_dialogBackground)));
//        topView.addView(iconBgView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 18, 1.66f, 0));
//
//        BackupImageView iconFgView = new BackupImageView(context);
//        iconFgView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, settings.icon));
//        iconFgView.setEmojiColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground), PorterDuff.Mode.SRC_IN));
//        topView.addView(iconFgView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 18, 1.66f, 0));
//
//        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        FrameLayout chipLayout = new FrameLayout(context);
        chipLayout.setBackground(Theme.createRoundRectDrawable(dp(28), dp(28), Theme.getColor(Theme.key_groupcreate_spanBackground)));

        BackupImageView chipAvatar = new BackupImageView(context);
        chipAvatar.setRoundRadius(dp(28));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(dialog);
        chipAvatar.setForUserOrChat(dialog, avatarDrawable);
        chipLayout.addView(chipAvatar, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP));

        BackupImageView badgeView = new BackupImageView(context);
        badgeView.setEmojiColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground), PorterDuff.Mode.SRC_IN));
        badgeView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, settings.icon));
        chipLayout.addView(badgeView, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.CENTER_VERTICAL, 34, 0, 0, 0));

        SimpleTextView chipText = new SimpleTextView(context);
        chipText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        chipText.setTextSize(13);
        chipText.setEllipsizeByGradient(true);
        chipText.setText(dialogTitle);
        chipText.setWidthWrapContent(true);
        chipLayout.addView(chipText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 37 + 20, 0, 10, 0));

        linearLayout.addView(chipLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 0, 16, 0));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        if (UserObject.isBot(user)) {
            titleView.setText(getString(R.string.BotVerifyBotTitle));
        } else if (user != null) {
            titleView.setText(getString(R.string.BotVerifyUserTitle));
        } else if (ChatObject.isChannelAndNotMegaGroup(chat)) {
            titleView.setText(getString(R.string.BotVerifyChannelTitle));
        } else {
            titleView.setText(getString(R.string.BotVerifyGroupTitle));
        }
        titleView.setTypeface(AndroidUtilities.bold());
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 21, 24, 8.33f));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        NotificationCenter.listenEmojiLoading(textView);
        textView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(formatString(R.string.BotVerifyText, dialogTitle)), textView.getPaint().getFontMetricsInt(), false));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 22));

        final int maxLength = MessagesController.getInstance(currentAccount).botVerificationDescriptionLengthLimit;
        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        final OutlineTextContainerView editTextContainer = new OutlineTextContainerView(context);
        editTextContainer.setForceForceUseCenter(true);
        editTextContainer.setText(getString(R.string.BotVerifyDescription));
        editTextContainer.setLeftPadding(dp(2));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackground(null);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setMaxLines(15);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setTypeface(Typeface.DEFAULT);
        editText.setSelectAllOnFocus(true);
        editText.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setOnFocusChangeListener((v, hasFocus) -> editTextContainer.animateSelection(hasFocus, !TextUtils.isEmpty(editText.getText())));
        editTextContainer.attachEditText(editText);
        editTextContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 12, 4, 12, 4));
        linearLayout.addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        editText.addTextChangedListener(new EditTextSuggestionsFix());
        editText.addTextChangedListener(new TextWatcher() {
            boolean ignoreEditText;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                CharSequence text = editText.getText();
                if (!ignoreEditText) {
                    if (text.length() > maxLength) {
                        ignoreEditText = true;
                        editText.setText(text = text.subSequence(0, maxLength));
                        editText.setSelection(editText.length());
                        ignoreEditText = false;
                    }
                }
                editTextContainer.animateSelection(editText.isFocused(), !TextUtils.isEmpty(text));
            }
        });
        if (!TextUtils.isEmpty(settings.custom_description)) {
            editText.setText(settings.custom_description);
            if (!settings.can_modify_custom_description) {
                editText.setEnabled(false);
                editText.setFocusable(false);
                editText.setFocusableInTouchMode(false);
            }
        } else if (!settings.can_modify_custom_description) {
            editTextContainer.setVisibility(View.GONE);
        }

        if (settings.can_modify_custom_description) {
            TextView editTextInfoView = new TextView(context);
            editTextInfoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            editTextInfoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            editTextInfoView.setText(getString(dialogId >= 0 ? R.string.BotVerifyDescriptionInfo : R.string.BotVerifyDescriptionInfoChat));
            editTextInfoView.setPadding(dp(14), dp(7), dp(14), dp(27));
            linearLayout.addView(editTextInfoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            linearLayout.addView(new View(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 12));
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, null);
        button.setText(titleView.getText(), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        b.setCustomView(linearLayout);
        BottomSheet sheet = b.create();

        button.setOnClickListener(v -> {
            if (button.isLoading()) return;

            if (settings.can_modify_custom_description && editText.getText().length() > maxLength) {
                editTextContainer.animateError(1.0f);
                AndroidUtilities.shakeViewSpring(editTextContainer, -6);
                return;
            }

            button.setLoading(true);

            TL_bots.setCustomVerification req = new TL_bots.setCustomVerification();
            req.enabled = true;
            req.flags |= 1;
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (settings.can_modify_custom_description) {
                req.custom_description = editText.getText().toString();
            } else {
                req.custom_description = settings.custom_description;
            }
            if (!TextUtils.isEmpty(req.custom_description)) {
                req.flags |= 4;
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                button.setLoading(false);
                if (res instanceof TLRPC.TL_boolTrue) {
                    sheet.dismiss();
                    whenSent.run(false);
                } else {

                }
            }));
        });

        sheet.smoothKeyboardAnimationEnabled = true;
        sheet.smoothKeyboardByBottom = true;
        sheet.show();
    }

    public static void openRemoveVerify(Context context, int currentAccount, long botId, long dialogId, TL_bots.botVerifierSettings settings, Utilities.Callback<Boolean> whenSent) {
        if (context == null) {
            return;
        }

        String dialogTitle;
        TLObject dialog;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (dialogId >= 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            dialogTitle = UserObject.getForcedFirstName(user);
            dialog = user;
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            dialogTitle = chat == null ? "" : chat.title;
            dialog = chat;
        }

        FrameLayout layoutView = new FrameLayout(context);

        FrameLayout chipLayout = new FrameLayout(context);
        chipLayout.setBackground(Theme.createRoundRectDrawable(dp(28), dp(28), Theme.getColor(Theme.key_groupcreate_spanBackground)));

        BackupImageView chipAvatar = new BackupImageView(context);
        chipAvatar.setRoundRadius(dp(28));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(dialog);
        chipAvatar.setForUserOrChat(dialog, avatarDrawable);
        chipLayout.addView(chipAvatar, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP));

        BackupImageView badgeView = new BackupImageView(context);
        badgeView.setEmojiColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground), PorterDuff.Mode.SRC_IN));
        badgeView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, settings.icon));
        chipLayout.addView(badgeView, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.CENTER_VERTICAL, 34, 0, 0, 0));

        SimpleTextView chipText = new SimpleTextView(context);
        chipText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        chipText.setTextSize(13);
        chipText.setEllipsizeByGradient(true);
        chipText.setText(dialogTitle);
        chipText.setWidthWrapContent(true);
        chipLayout.addView(chipText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 37 + 20, 0, 10, 0));

        layoutView.addView(chipLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));

        final boolean[] loading = new boolean[1];
        new AlertDialog.Builder(context)
            .setTitle(getString(R.string.BotRemoveVerificationTitle))
            .setMessage(getString(dialogId >= 0 ? R.string.BotRemoveVerificationText : R.string.BotRemoveVerificationChatText))
            .setView(layoutView)
            .setNegativeButton(getString(R.string.Cancel), null)
            .setPositiveButton(getString(R.string.Remove), (di, w) -> {
                if (loading[0]) return;
                loading[0] = true;

                TL_bots.setCustomVerification req = new TL_bots.setCustomVerification();
                req.enabled = false;
                req.flags |= 1;
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    loading[0] = false;
                    if (res instanceof TLRPC.TL_boolTrue) {
                        whenSent.run(true);
                    } else {

                    }
                }));
            })
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show();
    }

}
