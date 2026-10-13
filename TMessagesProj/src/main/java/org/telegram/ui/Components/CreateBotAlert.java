package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLink;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLinkBold;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.LocaleController;
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
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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

        final Set<String> allowedBotSuffixes = new TreeSet<>();
        final Set<String> configuredSuffixes = AppGlobalConfig.getInstance(currentAccount).botAllowedSuffixes.get();
        if (configuredSuffixes != null) {
            for (String suffix : configuredSuffixes) {
                if (!TextUtils.isEmpty(suffix)) {
                    allowedBotSuffixes.add(suffix.toLowerCase(Locale.US));
                }
            }
        }
        // "bot" is always available for creation and is never a Premium suffix.
        allowedBotSuffixes.remove("bot");
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
        imageView.setRoundRadius(dp(40));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 22, 0, 16));

        final TextView titleTextView = new TextView(context);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setText(getString(R.string.CreateManagedBotTitle));
        titleTextView.setGravity(Gravity.CENTER);
        layout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 16, 0, 16, 5));

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
        layout.addView(nameEditInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 9, 0, 9, 0));

        final EditTextCell usernameEdit = new EditTextCell(context, getString(R.string.CreateManagedBotUsername), false, false, 32, resourcesProvider);
        final LinearLayout usernameLayout = new LinearLayout(context);
        usernameLayout.setOrientation(LinearLayout.HORIZONTAL);
        usernameEdit.removeView(usernameEdit.editText);
        usernameEdit.editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
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
        usernameEdit.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameEdit.editText.setSingleLine(true);
        String suggested_username = peerType.suggested_username;
        if (suggested_username != null) {
            suggested_username = suggested_username.trim();
        }
        // Keep the suggestion intact, including its explicit (possibly Premium) suffix.
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
        layout.addView(usernameEditInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 9, 0, 9, 0));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(12), dp(12), dp(12), dp(12));
        buttons.setClipToPadding(false);
        layout.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ButtonWithCounterView cancel = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        cancel.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.10f));
        cancel.setText(getString(R.string.Cancel));
        buttons.addView(cancel, LayoutHelper.createLinear(0, 48, Gravity.FILL, 1, 0, 0, 5, 0));

        final ButtonWithCounterView create = new ButtonWithCounterView(context, resourcesProvider).setRound();
        create.setText(getString(R.string.CreateManagedBotButton));
        buttons.addView(create, LayoutHelper.createLinear(0, 48, Gravity.FILL, 1, 5, 0, 0, 0));

        final BottomSheet sheet = b.create();
        sheet.useBackgroundTopPadding = false;
        sheet.smoothKeyboardAnimationEnabled = true;
        sheet.doNotOverlayNavigationBar = true;
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        sheet.fixNavigationBar();

        final boolean[] sent = new boolean[1];
        final boolean[] dismissed = new boolean[1];
        final String[] checkedUsername = new String[1];
        final int[] creatingRequestId = new int[] { -1 };
        final int[] checkingRequestId = new int[] { -1 };
        final int[] checkGeneration = new int[1];
        final Runnable[] pendingCheck = new Runnable[1];
        final int[] shake = new int[] { 4 };

        final Runnable openPremium = () -> {
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                sheet.dismiss();
                final PremiumPreviewFragment fragment = new PremiumPreviewFragment("create_bot");
                fragment.setCurrentAccount(currentAccount);
                lastFragment.presentFragment(fragment);
            }
        };
        final String visibleSuffixes = formatSuffixes(allowedBotSuffixes);
        final CharSequence usernameHint = TextUtils.isEmpty(visibleSuffixes)
                ? getString(R.string.CreateManagedBotUsernameInfo)
                : replaceSingleLink(
                formatString(R.string.CreateManagedBotUsernameSuffixesInfo, visibleSuffixes),
                Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider), openPremium);
        final Runnable showUsernameHint = () -> {
            usernameEditInfo.setText(usernameHint);
            usernameEditInfo.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        };
        final Runnable cancelUsernameCheck = () -> {
            ++checkGeneration[0];
            if (pendingCheck[0] != null) {
                AndroidUtilities.cancelRunOnUIThread(pendingCheck[0]);
                pendingCheck[0] = null;
            }
            if (checkingRequestId[0] >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkingRequestId[0], true);
                checkingRequestId[0] = -1;
            }
        };
        final Runnable checkRightText = () -> {
            final String usernameInput = usernameEdit.getText().toString();
            final String suffix = getSuffixToAdd(usernameInput, allowedBotSuffixes);
            usernameEdit.editText.setRightText(suffix);
            usernameEdit.editText.setPadding(0, dp(15), dp(TextUtils.isEmpty(suffix) ? 21 : 42 + 21), dp(15));
        };

        final Runnable checkUsername = () -> {
            if (dismissed[0] || creatingRequestId[0] >= 0) {
                return;
            }
            cancelUsernameCheck.run();
            checkedUsername[0] = null;
            create.setLoading(false);
            create.setEnabled(false);
            final String usernameInput = usernameEdit.getText().toString();
            final String username = usernameInput + getSuffixToAdd(usernameInput, allowedBotSuffixes);
            if (TextUtils.isEmpty(usernameInput)) {
                showUsernameHint.run();
                return;
            }
            if (username.length() < 4 || username.length() > 32) {
                usernameEditInfo.setText(getString(username.length() < 4 ? R.string.UsernameInvalidShort : R.string.UsernameInvalidLong));
                usernameEditInfo.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                return;
            }
            usernameEditInfo.setText(getString(R.string.UsernameChecking));
            usernameEditInfo.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
            final int generation = checkGeneration[0];
            pendingCheck[0] = () -> {
                pendingCheck[0] = null;
                if (dismissed[0] || generation != checkGeneration[0]) {
                    return;
                }
                final TL_bots.checkUsername req = new TL_bots.checkUsername();
                // Creation accepts both "bot" and configured suffixes; additional stays false.
                req.username = username;
                checkingRequestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                    if (dismissed[0] || generation != checkGeneration[0]) {
                        return;
                    }
                    checkingRequestId[0] = -1;
                    if (res instanceof TLRPC.TL_boolTrue) {
                        checkedUsername[0] = username;
                        create.setEnabled(true);
                        showUsernameHint.run();
                        return;
                    }
                    final CharSequence message = getUsernameError(currentAccount, username, allowedBotSuffixes,
                            err == null ? "USERNAME_OCCUPIED" : err.text, openPremium, resourcesProvider);
                    usernameEditInfo.setText(message != null ? message : getString(R.string.CreateManagedBotUsernameCheckFailed));
                    usernameEditInfo.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                    if (message == null && err != null) {
                        // Allow a retry without requiring the user to edit the name.
                        create.setEnabled(true);
                        BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(err);
                    }
                });
            };
            AndroidUtilities.runOnUIThread(pendingCheck[0], 300);
        };
        showUsernameHint.run();
        final Runnable createRunnable = () -> {
            if (dismissed[0] || creatingRequestId[0] >= 0) {
                return;
            }
            final String usernameInput = usernameEdit.getText().toString();
            final String username = usernameInput + getSuffixToAdd(usernameInput, allowedBotSuffixes);
            if (!TextUtils.equals(checkedUsername[0], username)) {
                checkUsername.run();
                return;
            }

            final String name = nameEdit.editText.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                AndroidUtilities.shakeViewSpring(nameEdit, shake[0] = -shake[0]);
                return;
            }

            cancelUsernameCheck.run();
            create.setLoading(true);
            create.setEnabled(false);
            nameEdit.editText.setEnabled(false);
            usernameEdit.editText.setEnabled(false);
            final TL_bots.createBot req = new TL_bots.createBot();
            req.via_deeplink = via_deeplink;
            req.username = checkedUsername[0];
            req.name = name;
            req.manager_id = MessagesController.getInstance(currentAccount).getInputUser(bot);
            creatingRequestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (newBot, err) -> {
                if (dismissed[0]) {
                    return;
                }
                creatingRequestId[0] = -1;
                create.setLoading(false);
                create.setEnabled(true);
                nameEdit.editText.setEnabled(true);
                usernameEdit.editText.setEnabled(true);
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
                    final CharSequence usernameError = getUsernameError(currentAccount, req.username, allowedBotSuffixes,
                            err.text, openPremium, resourcesProvider);
                    if (usernameError != null) {
                        checkedUsername[0] = null;
                        create.setEnabled(false);
                        usernameEditInfo.setText(usernameError);
                        usernameEditInfo.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                        AndroidUtilities.shakeViewSpring(usernameEditInfo, shake[0] = -shake[0]);
                    } else if ("BOT_CREATE_LIMIT_EXCEEDED".equalsIgnoreCase(err.text)) {
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
                    } else if (err.text != null && err.text.startsWith("FLOOD_WAIT_")) {
                        final int seconds = Integer.parseInt(err.text.substring("FLOOD_WAIT_".length()));
                        BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.error, getString(R.string.CreateManagedBotLimitTitle), formatString(R.string.CreateManagedBotLimitTextTime, LocaleController.formatDuration(seconds)))
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
            }, ConnectionsManager.RequestFlagDoNotWaitFloodWait);
        };
        usernameEdit.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                checkRightText.run();
                checkUsername.run();
            }
        });
        checkRightText.run();
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
            dismissed[0] = true;
            cancelUsernameCheck.run();
            if (!sent[0]) {
                sent[0] = true;
                if (onCreated != null) {
                    onCreated.run(null);
                }
            }

            if (creatingRequestId[0] >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(creatingRequestId[0], true);
                creatingRequestId[0] = -1;
            }
        });

        sheet.show();
    }

    private static String getSuffixToAdd(String input, Set<String> allowedSuffixes) {
        if (endsWithIgnoreCase(input, "bot") || getPremiumSuffix(input, allowedSuffixes) != null) {
            return "";
        }
        return "bot";
    }

    private static boolean endsWithIgnoreCase(String input, String suffix) {
        return input.length() >= suffix.length()
                && input.regionMatches(true, input.length() - suffix.length(), suffix, 0, suffix.length());
    }

    private static String getPremiumSuffix(String username, Set<String> allowedSuffixes) {
        if (endsWithIgnoreCase(username, "bot")) {
            return null;
        }
        String result = null;
        for (String suffix : allowedSuffixes) {
            if (!TextUtils.isEmpty(suffix) && endsWithIgnoreCase(username, suffix)
                    && (result == null || suffix.length() > result.length())) {
                result = suffix;
            }
        }
        return result;
    }

    private static String formatSuffixes(Set<String> suffixes) {
        final ArrayList<String> sorted = new ArrayList<>(suffixes.size());
        for (String suffix : suffixes) {
            // Easter egg: accepted by the server, but not advertised in suffix lists.
            if (!"gay".equalsIgnoreCase(suffix)) {
                sorted.add(suffix);
            }
        }
        Collections.sort(sorted);
        String result = "";
        for (int i = 0; i < sorted.size(); i++) {
            final String suffix = "'" + sorted.get(i) + "'";
            if (i == 0) {
                result = suffix;
            } else if (i == sorted.size() - 1) {
                result = formatString(R.string.CreateManagedBotUsernameSuffixesOr, result, suffix);
            } else {
                result = formatString(R.string.CreateManagedBotUsernameSuffixesComma, result, suffix);
            }
        }
        return result;
    }

    private static CharSequence getUsernameError(int currentAccount, String username, Set<String> allowedSuffixes,
                                                 String error, Runnable openPremium, Theme.ResourcesProvider resourcesProvider) {
        if (error == null) {
            return null;
        }
        switch (error.toUpperCase(Locale.US)) {
            case "PREMIUM_ACCOUNT_REQUIRED": {
                final String suffix = getPremiumSuffix(username, allowedSuffixes);
                final String text = suffix == null
                        ? getString(R.string.CreateManagedBotUsernamePremiumRequiredGeneric)
                        : formatString(R.string.CreateManagedBotUsernamePremiumRequired, suffix);
                return replaceSingleLink(text, Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider), openPremium);
            }
            case "OWNER_USERNAMES_LIMIT_EXCEEDED":
                return formatString(R.string.CreateManagedBotUsernameOwnerLimit,
                        AppGlobalConfig.getInstance(currentAccount).botAdditionalUsernamesLimit.get(), formatSuffixes(allowedSuffixes));
            case "BOT_USERNAMES_LIMIT_EXCEEDED":
                return getString(R.string.CreateManagedBotUsernameBotLimit);
            case "ADDITIONAL_USERNAME_SUFFIX_MISSING":
                return formatString(R.string.CreateManagedBotUsernameSuffixMissing, formatSuffixes(allowedSuffixes));
            case "USERNAME_INVALID":
                return getString(R.string.UsernameInvalid);
            case "USERNAME_OCCUPIED":
            case "USERNAME_PURCHASE_AVAILABLE":
                return getString(R.string.UsernameInUse);
            default:
                return null;
        }
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
