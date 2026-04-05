package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLink;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLinkBold;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class CreateBotAlert {

    public static void show(
        Context context,
        int currentAccount,
        TLRPC.User bot,
        TLRPC.TL_requestPeerTypeCreateBot peerType,
        boolean via_deeplink,
        Utilities.Callback<TLRPC.User> onCreated,
        Theme.ResourcesProvider resourcesProvider,
        BulletinFactory bulletinFactory,
        boolean inWebApp
    ) {
        if (!bot.bot_can_manage_bots) {
            if (bulletinFactory == null) {
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null) {
                    if (onCreated != null) {
                        onCreated.run(null);
                    }
                    return;
                }
                bulletinFactory = BulletinFactory.of(lastFragment);
            }
            String botName;
            if (!TextUtils.isEmpty(UserObject.getPublicUsername(bot))) {
                botName = "@" + UserObject.getPublicUsername(bot);
            } else {
                botName = UserObject.getUserName(bot);
            }
            bulletinFactory
                .createSimpleBulletin(R.raw.error, replaceSingleLinkBold(formatString(R.string.CreateManagedBotUnsupported, botName), Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider)))
                .show();
            if (onCreated != null) {
                onCreated.run(null);
            }
            return;
        }

        final BottomSheet.Builder b = new BottomSheet.Builder(context, true, resourcesProvider);

        final LinearLayout layout = new LinearLayout(context);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        layout.setOrientation(LinearLayout.VERTICAL);
        b.setCustomView(layout);

        final BackupImageView imageView = new BackupImageView(context);
        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(bot);
        imageView.setForUserOrChat(bot, avatarDrawable);
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 22, 0, 16));

        final TextView titleTextView = new TextView(context);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setText(getString(R.string.CreateManagedBotTitle));
        titleTextView.setGravity(Gravity.CENTER);
        layout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 16, 0, 16, 8));

        final TextView subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleTextView.setText(
            replaceSingleLinkBold(
                formatString(R.string.CreateManagedBotText, UserObject.getUserName(bot)),
                Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider)
            )
        );
        subtitleTextView.setGravity(Gravity.CENTER);
        layout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 16, 0, 16, 22));

        final EditTextCell nameEdit = new EditTextCell(context, getString(R.string.CreateManagedBotName), false, false, -1, resourcesProvider);
        nameEdit.editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameEdit.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        nameEdit.setText(peerType.suggested_name);
        layout.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 0, 12, 0));

        final TextInfoPrivacyCell nameEditInfo = new TextInfoPrivacyCell(context, resourcesProvider);
        nameEditInfo.setText(getString(R.string.CreateManagedBotNameInfo));
        layout.addView(nameEditInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final EditTextCell usernameEdit = new EditTextCell(context, getString(R.string.CreateManagedBotUsername), false, false, 32 - 3, resourcesProvider);
        final LinearLayout usernameLayout = new LinearLayout(context);
        usernameLayout.setOrientation(LinearLayout.HORIZONTAL);
        usernameEdit.removeView(usernameEdit.editText);
        usernameEdit.editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        usernameEdit.editText.setRightText("bot");
        usernameEdit.editText.setPadding(0, dp(15), dp(42 + 21), dp(15));
        final TextView at = new TextView(context);
        at.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        at.setText("@");
        at.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        at.setGravity(Gravity.CENTER);
        at.setPadding(0, dp(15), 0, dp(15));
        usernameLayout.addView(at, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 21, -1, 0, 0));
        usernameLayout.addView(usernameEdit.editText, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, Gravity.FILL, 1, 0, 0, 0, 0));
        usernameEdit.addView(usernameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        usernameEdit.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        usernameEdit.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        String suggested_username = peerType.suggested_username;
        if (suggested_username != null) {
            suggested_username = suggested_username.trim();
        }
        if (suggested_username != null && suggested_username.toLowerCase().endsWith("bot")) {
            suggested_username = suggested_username.substring(0, suggested_username.length() - 3);
        }
        usernameEdit.setText(suggested_username);
        layout.addView(usernameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 0, 12, 0));

        nameEdit.editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                usernameEdit.editText.requestFocus();
                usernameEdit.editText.setSelection(usernameEdit.editText.length());
                return true;
            }
            return false;
        });

        final TextInfoPrivacyCell usernameEditInfo = new TextInfoPrivacyCell(context, resourcesProvider);
        usernameEditInfo.setText(getString(R.string.CreateManagedBotUsernameInfo));
        layout.addView(usernameEditInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(12), dp(12), dp(12), dp(12));
        buttons.setClipToPadding(false);
        layout.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ButtonWithCounterView cancel = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        cancel.setText(getString(R.string.Cancel));
        buttons.addView(cancel, LayoutHelper.createLinear(0, 48, Gravity.FILL, 1, 0, 0, 5, 0));

        final ButtonWithCounterView create = new ButtonWithCounterView(context, resourcesProvider).setRound();
        create.setText(getString(R.string.CreateManagedBotButton));
        buttons.addView(create, LayoutHelper.createLinear(0, 48, Gravity.FILL, 1, 5, 0, 0, 0));

        final BottomSheet sheet = b.create();
        sheet.useBackgroundTopPadding = false;
        sheet.smoothKeyboardAnimationEnabled = true;
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        sheet.fixNavigationBar();

        final boolean[] sent = new boolean[1];

        final String[] checkedUsername = new String[1];

        final String[] checking = new String[1];
        final int[] checkingRequestId = new int[] { -1 };
        final int[] shake = new int[] { 4 };
        final Runnable checkUsername = () -> {
            final String username = usernameEdit.getText() + "bot";
            if (username.length() < 4) {
                if (checkingRequestId[0] >= 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(checkingRequestId[0], true);
                    checkingRequestId[0] = -1;
                }
                checkedUsername[0] = checking[0] = null;
                create.setLoading(false);
                create.setEnabled(false);
                usernameEditInfo.setText(getString(R.string.UsernameInvalidShort));
                usernameEditInfo.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                AndroidUtilities.shakeViewSpring(usernameEditInfo, shake[0] = -shake[0]);
                return;
            }
            if (username.length() > 32) {
                if (checkingRequestId[0] >= 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(checkingRequestId[0], true);
                    checkingRequestId[0] = -1;
                }
                checkedUsername[0] = checking[0] = null;
                create.setLoading(false);
                create.setEnabled(false);
                usernameEditInfo.setText(getString(R.string.UsernameInvalidLong));
                usernameEditInfo.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                AndroidUtilities.shakeViewSpring(usernameEditInfo, shake[0] = -shake[0]);
                return;
            }
            if (TextUtils.equals(checking[0], username)) {
                return;
            }
            checking[0] = username;
            checkedUsername[0] = null;
            usernameEditInfo.setText(getString(R.string.UsernameChecking));
            usernameEditInfo.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));

            final TL_bots.checkUsername req = new TL_bots.checkUsername();
            req.username = username;
            create.setLoading(true);
            checkingRequestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                create.setLoading(false);
                checking[0] = null;
                if (res instanceof TLRPC.TL_boolTrue) {
                    checkedUsername[0] = username;
                    create.setEnabled(true);

                    usernameEditInfo.setText(formatString(R.string.UsernameAvailable, "@" + username));
                    usernameEditInfo.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider));
                    return;
                }

                checkedUsername[0] = null;
                create.setEnabled(false);
                usernameEditInfo.setText(getString(R.string.UsernameInUse));
                usernameEditInfo.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                AndroidUtilities.shakeViewSpring(usernameEditInfo, shake[0] = -shake[0]);
            });
        };
        final Runnable createRunnable = () -> {
            if (checkedUsername[0] == null) {
                checkUsername.run();
                return;
            }

            final String name = nameEdit.editText.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                AndroidUtilities.shakeViewSpring(nameEdit, shake[0] = -shake[0]);
                return;
            }

            create.setLoading(true);
            final TL_bots.createBot req = new TL_bots.createBot();
            req.via_deeplink = via_deeplink;
            req.username = checkedUsername[0];
            req.name = name;
            req.manager_id = MessagesController.getInstance(currentAccount).getInputUser(bot);
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (newBot, err) -> {
                create.setLoading(false);
                if (newBot != null) {
                    sent[0] = true;
                    MessagesController.getInstance(currentAccount).putUser(newBot, false);
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(newBot);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, false);
                    if (onCreated != null) {
                        onCreated.run(newBot);
                    }
                    sheet.dismiss();
                    return;
                }
                if (err != null) {
                    if ("BOT_CREATE_LIMIT_EXCEEDED".equalsIgnoreCase(err.text)) {
                        final MessagesController m = MessagesController.getInstance(currentAccount);
                        final boolean premium = UserConfig.getInstance(currentAccount).isPremium();
                        BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
                            .createSimpleBulletin(
                                R.raw.error,
                                getString(R.string.CreateManagedBotLimitTitle),
                                highlightBotFather(
                                    context,
                                    replaceSingleLink(
                                        premium ?
                                            formatString(R.string.CreateManagedBotLimitText, m.config.botsCreateLimitPremium.get()) :
                                            formatString(R.string.CreateManagedBotLimitTextPremium, m.config.botsCreateLimitPremium.get(), m.config.botsCreateLimitDefault.get()),
                                        Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider),
                                        () -> {
                                            sheet.dismiss();

                                            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                            if (lastFragment != null) {
                                                lastFragment.presentFragment(new PremiumPreviewFragment("create_bot"));
                                            }
                                        }
                                    ),
                                    () -> {
                                        sheet.dismiss();
                                        Browser.openUrl(context, "https://t.me/BotFather?start=deletebot");
                                    },
                                    resourcesProvider
                                )
                            )
                            .setDuration(8000)
                            .show();
                    } else if ("MANAGER_PERMISSION_MISSING".equalsIgnoreCase(err.text)) {
                        String botName;
                        if (!TextUtils.isEmpty(UserObject.getPublicUsername(bot))) {
                            botName = "@" + UserObject.getPublicUsername(bot);
                        } else {
                            botName = UserObject.getUserName(bot);
                        }
                        BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.error, replaceSingleLinkBold(formatString(R.string.CreateManagedBotUnsupported, botName), Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider)))
                            .show();
                    } else {
                        BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(err);
                    }
                    AndroidUtilities.hideKeyboard(sheet.getCurrentFocus());
                }
            });
        };
        usernameEdit.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                checkUsername.run();
            }
        });
        if (!TextUtils.isEmpty(suggested_username)) {
            checkUsername.run();
        }
        usernameEdit.editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                createRunnable.run();
                return true;
            }
            return false;
        });

        cancel.setOnClickListener(v -> {
            sheet.dismiss();
        });
        create.setEnabled(false);
        create.setOnClickListener(v -> {
            createRunnable.run();
        });
        sheet.setOnDismissListener(d -> {
            if (!sent[0]) {
                sent[0] = true;
                if (onCreated != null) {
                    onCreated.run(null);
                }
            }
        });

        sheet.show();
    }

    private static SpannableStringBuilder highlightBotFather(Context context, CharSequence c, Runnable onClick, Theme.ResourcesProvider resourcesProvider) {
        SpannableStringBuilder s;
        if (!(c instanceof SpannableStringBuilder))
            s = new SpannableStringBuilder(c);
        else
            s = (SpannableStringBuilder) c;
        final int index = AndroidUtilities.charSequenceIndexOf(s, "@BotFather");
        if (index >= 0) {
            s.setSpan(new ClickableSpan() {
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                    ds.setColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
                }

                @Override
                public void onClick(@NonNull View view) {
                    onClick.run();
                }
            }, index, index + "@BotFather".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }
}
