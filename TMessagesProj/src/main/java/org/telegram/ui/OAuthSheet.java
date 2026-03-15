package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLink;
import static org.telegram.messenger.AndroidUtilities.replaceSingleLinkBold;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.web.BotWebViewContainer.obj;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.web.BotWebViewContainer;

import java.util.ArrayList;
import java.util.Collections;

public class OAuthSheet {

    private static BottomSheet showing;

    public static void handle(boolean external, int currentAccount, TLRPC.TL_messages_requestUrlAuth request, TLRPC.UrlAuthResult result) {
        handle(external, currentAccount, request, result, null, null, null, false, null);
    }

    public static void handle(boolean external, int currentAccount, TLRPC.TL_messages_requestUrlAuth request, TLRPC.UrlAuthResult result, String originUrl, TLRPC.UrlAuthResult prevResult, String matchCode, boolean sentPhoneNumber, BotWebViewContainer webView) {
        if (result instanceof TLRPC.TL_urlAuthResultAccepted) {
            final TLRPC.TL_urlAuthResultAccepted r = (TLRPC.TL_urlAuthResultAccepted) result;
            if (webView != null) {
                if (TextUtils.isEmpty(request.in_app_origin)) return;
                if (!TextUtils.equals(webView.getOriginHost(), request.in_app_origin)) return;
            }
            if (TextUtils.isEmpty(r.url)) {
                String domain = null;
                if (prevResult instanceof TLRPC.TL_urlAuthResultRequest) {
                    domain = ((TLRPC.TL_urlAuthResultRequest) prevResult).domain;
                }
                if (!TextUtils.isEmpty(domain)) {
                    final boolean withoutPhoneNumber = prevResult instanceof TLRPC.TL_urlAuthResultRequest && ((TLRPC.TL_urlAuthResultRequest) prevResult).request_phone_number && !sentPhoneNumber;
                    getBulletinFactory()
                        .createSimpleBulletin(R.raw.contact_check, getString(R.string.BotAuthLoggedInSuccessTitle), replaceSingleLinkBold(formatString(withoutPhoneNumber ? R.string.BotAuthLoggedInSuccessWithoutPhoneNumber : R.string.BotAuthLoggedInSuccess, domain), Theme.getColor(Theme.key_undo_cancelColor)))
                        .show();
                }
                if (webView != null) {
                    webView.notifyEvent("oauth_result_confirmed", obj("result_url", null));
                } else if (external) {
                    final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                    if (fragment == null) return;
                    final Context context = fragment.getContext();
                    if (context == null) return;
                    AndroidUtilities.runOnUIThread(() -> {
                        Activity activity = AndroidUtilities.findActivity(context);
                        if (activity == null) activity = LaunchActivity.instance;
                        if (activity != null && !activity.isFinishing()) {
                            activity.moveTaskToBack(true);
                        }
                    }, 800);
                }
            } else if (webView != null) {
                webView.notifyEvent("oauth_result_confirmed", obj("result_url", r.url));
            } else {
                final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment == null) return;
                Browser.openUrlInSystemBrowser(fragment.getContext(), r.url);
            }
            return;
        } else if (result instanceof TLRPC.TL_urlAuthResultDefault) {
            if (webView != null) return;
            if (!TextUtils.isEmpty(request.url)) {
                final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment == null) return;
                AlertsCreator.showOpenUrlAlert(fragment, request.url, false, prevResult == null);
            } else if (!TextUtils.isEmpty(originUrl)) {
                final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment == null) return;
                AlertsCreator.showOpenUrlAlert(fragment, originUrl, false, prevResult == null);
            }
            return;
        }

        if (!(result instanceof TLRPC.TL_urlAuthResultRequest))
            return;
        final TLRPC.TL_urlAuthResultRequest r = (TLRPC.TL_urlAuthResultRequest) result;

        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return;
        final Context context = fragment.getContext();
        if (context == null) return;

        final Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();
        BottomSheet.Builder b = new BottomSheet.Builder(context, false, fragment.getResourceProvider());

        FrameLayout container = new FrameLayout(context);
        b.setCustomView(container);

        final ArrayList<Integer> accountNumbers = new ArrayList<>();
        final boolean testBackend = ConnectionsManager.getInstance(currentAccount).isTestBackend();
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && ConnectionsManager.getInstance(a).isTestBackend() == testBackend) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        final boolean bot = request.peer != null;

        final FrameLayout accountSelectorLayout = new FrameLayout(context);
        final FrameLayout accountSelectorInnerLayout = new FrameLayout(context);
        accountSelectorInnerLayout.setBackground(Theme.createRoundRectDrawable(dp(14), fragment.getThemedColor(Theme.key_dialogBackgroundGray)));
        final BackupImageView accountImageView = new BackupImageView(context);
        accountImageView.setRoundRadius(dp(14));
        accountImageView.getImageReceiver().setCrossfadeWithOldImage(true);
        final AvatarDrawable accountAvatarDrawable = new AvatarDrawable();

        final int[] selectedAccount = new int[] { currentAccount };
        final TLRPC.User self = UserConfig.getInstance(selectedAccount[0]).getCurrentUser();
        accountAvatarDrawable.setInfo(self);
        accountImageView.setForUserOrChat(self, accountAvatarDrawable);

        accountSelectorInnerLayout.addView(accountImageView, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.FILL_VERTICAL));
        final ImageView accountSelectorIconView = new ImageView(context);
        accountSelectorIconView.setScaleType(ImageView.ScaleType.CENTER);
        accountSelectorIconView.setColorFilter(new PorterDuffColorFilter(fragment.getThemedColor(Theme.key_dialogTextGray3), PorterDuff.Mode.SRC_IN));
        accountSelectorIconView.setImageResource(R.drawable.arrows_select);
        accountSelectorInnerLayout.addView(accountSelectorIconView, LayoutHelper.createFrame(18, 18, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        accountSelectorLayout.addView(accountSelectorInnerLayout, LayoutHelper.createFrame(52, 28, Gravity.CENTER));
        accountSelectorLayout.setPadding(dp(8), dp(4), dp(8), 0);
        container.addView(accountSelectorLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.TOP, 6, 4, 6, 0));
        ScaleStateListAnimator.apply(accountSelectorLayout);
        if (accountNumbers.size() <= 1 || request.peer != null) {
            accountSelectorLayout.setVisibility(View.GONE);
        }

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        container.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        final BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(40));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(r.bot);
        imageView.setForUserOrChat(r.bot, avatarDrawable);
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 21, 0, 16));

        final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_dialogTextBlack, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(AndroidUtilities.replaceSingleLink(formatString(R.string.BotAuthTitle, r.domain), fragment.getThemedColor(Theme.key_featuredStickers_addButton)));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 0, 32, 9.66f));

        final TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_dialogTextBlack, false);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(AndroidUtilities.replaceTags(getString(bot ? R.string.BotAuthBotSubtitle : R.string.BotAuthSiteSubtitle)));
        layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 0, 32, 24));

        if (!TextUtils.isEmpty(r.platform) || !TextUtils.isEmpty(r.browser) || !TextUtils.isEmpty(r.region) || !TextUtils.isEmpty(r.ip)) {

            LinearLayout layout2 = new LinearLayout(context);
            layout2.setClipToPadding(false);
            layout2.setClipChildren(false);
            layout2.setOrientation(LinearLayout.VERTICAL);
            layout2.setBackground(Theme.createRoundRectDrawableShadowed(dp(16), fragment.getThemedColor(Theme.key_windowBackgroundWhite)));
            layout.addView(layout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 9, -3, 9, -3));

            if (!TextUtils.isEmpty(r.platform) || !TextUtils.isEmpty(r.browser)) {
                LinearLayout cell1 = new LinearLayout(context);
                cell1.setOrientation(LinearLayout.HORIZONTAL);

                ImageView imageView1 = new ImageView(context);
                imageView1.setImageResource(R.drawable.msg2_devices);
                imageView1.setColorFilter(new PorterDuffColorFilter(fragment.getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN));
                cell1.addView(imageView1, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 17, 0, 20, 0));

                LinearLayout textLayout = new LinearLayout(context);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                cell1.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 10.66f, 20, 11));

                TextView text1 = TextHelper.makeTextView(context, 16, Theme.key_dialogTextBlack, false);
                text1.setText(TextUtils.isEmpty(r.platform) ? "—" : r.platform);
                textLayout.addView(text1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 4.33f));

                TextView text2 = TextHelper.makeTextView(context, 13, Theme.key_windowBackgroundWhiteGrayText, false);
                text2.setText(TextUtils.isEmpty(r.browser) ? "—" : r.browser);
                textLayout.addView(text2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

                layout2.addView(cell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (!TextUtils.isEmpty(r.region) || !TextUtils.isEmpty(r.ip)) {
                LinearLayout cell1 = new LinearLayout(context);
                cell1.setOrientation(LinearLayout.HORIZONTAL);

                ImageView imageView1 = new ImageView(context);
                imageView1.setImageResource(R.drawable.msg2_language);
                imageView1.setColorFilter(new PorterDuffColorFilter(fragment.getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN));
                cell1.addView(imageView1, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 17, 0, 20, 0));

                LinearLayout textLayout = new LinearLayout(context);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                cell1.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 10.66f, 20, 11));

                TextView text1 = TextHelper.makeTextView(context, 16, Theme.key_dialogTextBlack, false);
                text1.setText(TextUtils.isEmpty(r.region) ? "—" : r.region);
                textLayout.addView(text1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 4.33f));

                TextView text2 = TextHelper.makeTextView(context, 13, Theme.key_windowBackgroundWhiteGrayText, false);
                text2.setText(TextUtils.isEmpty(r.ip) ? "—" : formatString(R.string.BotAuthBasedOnIP, r.ip));
                textLayout.addView(text2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

                layout2.addView(cell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            TextView underTextView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText, false);
            underTextView.setText(getString(R.string.BotAuthInfo));
            layout.addView(underTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 5, 22, 20));
        }

        final TextCheckCell allowMessages;
        if (r.request_write_access) {
            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setBackground(Theme.createRoundRectDrawableShadowed(dp(16), fragment.getThemedColor(Theme.key_windowBackgroundWhite)));
            allowMessages = new TextCheckCell(context, fragment.getResourceProvider());
            allowMessages.setTextAndCheck(getString(R.string.BotAuthAllowMessages), true, false);
            allowMessages.setBackground(Theme.createRadSelectorDrawable(fragment.getThemedColor(Theme.key_windowBackgroundWhite), fragment.getThemedColor(Theme.key_listSelector), 16, 16));
            allowMessages.setOnClickListener(v -> {
                allowMessages.setChecked(!allowMessages.isChecked());
            });
            frameLayout.addView(allowMessages, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            layout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 9, -3, 9, -3));

            TextView underTextView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText, false);
            underTextView.setText(formatString(R.string.BotAuthAllowMessagesInfo, UserObject.getUserName(r.bot)));
            layout.addView(underTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 6, 22, 20));
        } else {
            allowMessages = null;
        }

        final LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);

        final ButtonWithCounterView cancel = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();//.setNeutral();
        cancel.setColor(fragment.getThemedColor(Theme.key_text_RedRegular));
        cancel.setText(getString(R.string.Decline));
        buttonsLayout.addView(cancel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL, 0, 0, 5, 0));

        final ButtonWithCounterView login = new ButtonWithCounterView(context, fragment.getResourceProvider()).setRound();
        login.setText(getString(R.string.BotAuthLogin));
        buttonsLayout.addView(login, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL, 5, 0, 0, 0));

        layout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 12, 12, 12, 8));

        final BottomSheet sheet = b.create();
        sheet.setBackgroundColor(fragment.getThemedColor(Theme.key_windowBackgroundGray));

        final String[] selectedMatchCode = new String[] { matchCode };
        final Utilities.Callback<Integer> switchAccount = account -> {
            if (selectedAccount[0] == account) return;

            final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(200);

            ConnectionsManager.getInstance(account).sendRequestTyped(request, AndroidUtilities::runOnUIThread, (res, err) -> {
                progressDialog.dismiss();
                if (res != null) {
                    sheet.dismiss();
                    handle(external, account, request, res, originUrl, prevResult, selectedMatchCode[0], sentPhoneNumber, webView);
                } else if (err != null) {
                    if ("URL_EXPIRED".equalsIgnoreCase(err.text)) {
                        sheet.dismiss();
                        getBulletinFactory()
                            .createSimpleBulletin(R.raw.error, getString(R.string.BotAuthLoggedInFailTitle), TextUtils.isEmpty(r.domain) ? getString(R.string.BotAuthLoggedInFailNoDomain) : replaceSingleLinkBold(formatString(R.string.BotAuthLoggedInFail, r.domain), Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider)))
                            .show();
                    } else {
                        BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider())
                            .showForError(err);
                    }
                }
            });
        };
        if (r.user_id_hint != 0 && UserConfig.getInstance(currentAccount).getClientUserId() != r.user_id_hint) {
            for (int account : accountNumbers) {
                if (UserConfig.getInstance(account).getClientUserId() == r.user_id_hint) {
                    switchAccount.run(account);
                    break;
                }
            }
        }
        accountSelectorLayout.setOnClickListener(v -> {
            ItemOptions i = ItemOptions.makeOptions(sheet.container, sheet.getResourcesProvider(), accountSelectorInnerLayout);
            for (int account : accountNumbers) {
                final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
                if (user == null) continue;
                i.addAccount(account, selectedAccount[0] == account, () -> switchAccount.run(account));
            }
            i
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .setDimAlpha(0)
                .setGravity(Gravity.LEFT)
                .translate(-dp(8), -dp(8))
                .show();
        });
        final boolean[] processed = new boolean[1];
        cancel.setOnClickListener(v -> {
            if (request == null || TextUtils.isEmpty(request.url)) {
                processed[0] = true;
                sheet.dismiss();
                return;
            }

            if (cancel.isLoading()) return;
            cancel.setLoading(true);

            if (webView != null) {
                webView.notifyEvent("oauth_result_failed", obj());
            }

            final TLRPC.TL_messages_declineUrlAuth req = new TLRPC.TL_messages_declineUrlAuth();
            req.url = request.url;
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                processed[0] = true;
                sheet.dismiss();
            });
        });
        final boolean[] allowPhoneNumber = new boolean[1];
        final BottomSheet[] showingMatchCodes = new BottomSheet[1];
        final Runnable accept = () -> {
            if (login.isLoading()) return;
            if (cancel.isLoading()) return;

            login.setLoading(true);

            final TLRPC.TL_messages_acceptUrlAuth req = new TLRPC.TL_messages_acceptUrlAuth();
            if (TLObject.hasFlag(request.flags, TLObject.FLAG_1)) {
                req.flags |= TLObject.FLAG_1;
                req.peer = request.peer;
                req.msg_id = request.msg_id;
                req.button_id = request.button_id;
            }
            if (TLObject.hasFlag(request.flags, TLObject.FLAG_2)) {
                req.flags |= TLObject.FLAG_2;
                req.url = request.url;
            }
            if (selectedMatchCode[0] != null) {
                req.match_code = selectedMatchCode[0];
            }

            req.write_allowed = allowMessages != null && allowMessages.isChecked();
            req.share_phone_number = allowPhoneNumber[0];

            ConnectionsManager.getInstance(selectedAccount[0]).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                processed[0] = true;
                sheet.dismiss();
                if (err != null) {
                    if ("URL_EXPIRED".equalsIgnoreCase(err.text)) {
                        getBulletinFactory()
                            .createSimpleBulletin(R.raw.error, getString(R.string.BotAuthLoggedInFailTitle), TextUtils.isEmpty(r.domain) ? getString(R.string.BotAuthLoggedInFailNoDomain) : replaceSingleLinkBold(formatString(R.string.BotAuthLoggedInFail, r.domain), Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider)))
                            .show();
                    } else {
                        getBulletinFactory().showForError(err);
                    }
                } else {
                    handle(external, selectedAccount[0], request, res, originUrl, r, null, req.share_phone_number, webView);
                }
            });
        };
        final Runnable beforeAccept = () -> {
            if (!r.match_codes.isEmpty() && TextUtils.isEmpty(selectedMatchCode[0])) {
                showMatchCodeSheet(context, currentAccount, r.match_codes, r.domain, code -> {
                    selectedMatchCode[0] = code;
                    accept.run();
                }, true, () -> {}, fragment.getResourceProvider());
            } else {
                accept.run();
            }
        };
        login.setOnClickListener(v -> {
            if (login.isLoading() || cancel.isLoading()) return;
            if (r.request_phone_number) {
                final TLRPC.User selectedSelf = UserConfig.getInstance(selectedAccount[0]).getCurrentUser();
                new AlertDialog.Builder(context, fragment.getResourceProvider())
                    .setTitle(getString(R.string.BotAuthPhoneNumber))
                    .setMessage(AndroidUtilities.replaceTags(formatString(R.string.BotAuthPhoneNumberText, bot ? UserObject.getUserName(r.bot) : r.domain, PhoneFormat.getInstance().format("+" + selectedSelf.phone).replaceAll(" ", " "))))
                    .setNegativeButton(getString(R.string.BotAuthPhoneNumberDeny), (di, w) -> {
                        allowPhoneNumber[0] = false;
                        beforeAccept.run();
                    })
                    .setPositiveButton(getString(R.string.BotAuthPhoneNumberAccept), (di, w) -> {
                        allowPhoneNumber[0] = true;
                        beforeAccept.run();
                    })
                    .makeRed(AlertDialog.BUTTON_NEGATIVE)
                    .show();
            } else {
                beforeAccept.run();
            }
        });
        sheet.setOnDismissListener(d -> {
            showing = null;
            if (showingMatchCodes[0] != null) {
                showingMatchCodes[0].dismiss();
                showingMatchCodes[0] = null;
            }
        });

        if (showing != null) {
            showing.dismiss();
            showing = null;
        }

        if (!r.match_codes_first || r.match_codes.isEmpty() || !TextUtils.isEmpty(selectedMatchCode[0])) {
            showing = sheet;
            sheet.show();
        } else {
            showing = showMatchCodeSheet(context, currentAccount, r.match_codes, r.domain, code -> {
                final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(200);

                final TLRPC.TL_messages_checkUrlAuthMatchCode req = new TLRPC.TL_messages_checkUrlAuthMatchCode();
                req.match_code = selectedMatchCode[0] = code;
                req.url = request.url;
                ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                    progressDialog.dismiss();
                    if (res instanceof TLRPC.TL_boolTrue) {
                        showing = sheet;
                        sheet.show();
                    } else {
                        if (showing != null) {
                            showing.dismiss();
                            showing = null;
                        }
                        getBulletinFactory()
                            .createSimpleBulletin(R.raw.error, getString(R.string.BotAuthLoggedInFailTitle), TextUtils.isEmpty(r.domain) ? getString(R.string.BotAuthLoggedInFailNoDomain) : replaceSingleLinkBold(formatString(R.string.BotAuthLoggedInFail, r.domain), Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider)))
                            .show();
                    }
                });
            }, false, () -> {
                showing = null;
                if (!processed[0]) {
                    processed[0] = true;

                    if (webView != null) {
                        webView.notifyEvent("oauth_result_failed", obj());
                    }

                    if (request != null && !TextUtils.isEmpty(request.url)) {
                        final TLRPC.TL_messages_declineUrlAuth req = new TLRPC.TL_messages_declineUrlAuth();
                        req.url = request.url;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                    }
                }
            }, fragment.getResourceProvider());
        }
    }

    public static BottomSheet showMatchCodeSheet(
        Context context,
        int currentAccount,
        ArrayList<String> match_codes,
        String domain,
        Utilities.Callback<String> onSelected,
        boolean cancel,
        Runnable onCancel,
        Theme.ResourcesProvider resourcesProvider
    ) {
        final BottomSheet[] sheet = new BottomSheet[1];
        final BottomSheet.Builder b = new BottomSheet.Builder(context);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        b.setCustomView(layout);

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 25, 0, 19));

        final LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER);
        buttonsLayout.setPadding(0, dp(19), 0, dp(19));
        layout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        int accountForEmoji = currentAccount;
        if (ConnectionsManager.getInstance(accountForEmoji).isTestBackend()) {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                if (UserConfig.getInstance(i).isClientActivated() && !ConnectionsManager.getInstance(i).isTestBackend()) {
                    accountForEmoji = i;
                    break;
                }
            }
        }
        boolean emojis = true;
        final BackupImageView[] buttons = new BackupImageView[match_codes.size()];
        for (int i = 0; i < match_codes.size(); ++i) {
            final String code = match_codes.get(i);
            final FrameLayout buttonLayout = new FrameLayout(context);
            buttonLayout.setBackground(Theme.createCircleDrawable(dp(70), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.05f)));

            Drawable thumb = Emoji.getEmojiBigDrawable(code);
            if (thumb == null) {
                final Text text = new Text(code, 30, AndroidUtilities.bold());
                thumb = new Drawable() {
                    @Override
                    public void draw(@NonNull Canvas canvas) {
                        text.draw(canvas, getBounds().centerX() - text.getCurrentWidth() / 2f, getBounds().centerY(), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 1.0f);
                    }
                    @Override
                    public int getOpacity() {
                        return PixelFormat.TRANSPARENT;
                    }
                    @Override
                    public void setAlpha(int alpha) {}
                    @Override
                    public void setColorFilter(@Nullable ColorFilter colorFilter) {}
                };
                emojis = false;
            }
            final BackupImageView buttonImage = buttons[i] = new BackupImageView(context);
            buttonImage.getImageReceiver().setCurrentAccount(accountForEmoji);
            buttonImage.setImage(null, null, null, null, thumb, null);
            NotificationCenter.listenEmojiLoading(buttonImage);
            buttonLayout.addView(buttonImage, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

            buttonsLayout.addView(buttonLayout, LayoutHelper.createLinear(70, 70, Gravity.CENTER_VERTICAL, i == 0 ? 0 : 24, 0, 0, 0));
            ScaleStateListAnimator.apply(buttonLayout);
            buttonLayout.setOnClickListener(v -> {
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                    sheet[0] = null;
                    onSelected.run(code);
                }
            });
        }
        final TLRPC.TL_inputStickerSetShortName inputStickerSetShortName = new TLRPC.TL_inputStickerSetShortName();
        inputStickerSetShortName.short_name = "RestrictedEmoji";
        MediaDataController.getInstance(accountForEmoji).getStickerSet(inputStickerSetShortName, null, false, set -> {
            if (set == null || set.set == null) return;
            for (int i = 0; i < match_codes.size(); ++i) {
                final String code = match_codes.get(i);

                TLRPC.Document document = null;
                for (int k = 0; k < set.packs.size(); ++k) {
                    if (!set.packs.get(k).documents.isEmpty() && TextUtils.equals(set.packs.get(k).emoticon, code)) {
                        long documentId = set.packs.get(k).documents.get(0);
                        for (int j = 0; j < set.documents.size(); ++j) {
                            if (set.documents.get(j).id == documentId) {
                                document = set.documents.get(j);
                                break;
                            }
                        }
                        break;
                    }
                }

                if (document != null) {
                    final int size = 40;
                    final Drawable thumb = Emoji.getEmojiBigDrawable(code);
                    final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, size);
                    buttons[i].setImage(
                        ImageLocation.getForDocument(document),
                        size + "_" + size,
                        ImageLocation.getForDocument(photoSize, document),
                        size + "_" + size,
                        thumb,
                        null
                    );
                }
            }
        });

        titleView.setText(getString(emojis ? R.string.BotAuthSelectEmoji : R.string.BotAuthSelectCode));

        final TextView footerView = new TextView(context);
        footerView.setGravity(Gravity.CENTER);
        footerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        footerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        footerView.setText(AndroidUtilities.replaceSingleLink(formatString(R.string.BotAuthLoginRequestFrom, domain), Theme.getColor(Theme.key_featuredStickers_addButton)));
        layout.addView(footerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 23, 0, 11));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        if (cancel) {
            button.setNeutral();
            button.setText(getString(R.string.Cancel));
        } else {
            button.setColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
            button.setText(getString(R.string.Decline));
        }
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 12, 12, 12, 12));
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            if (sheet[0] != null) {
                sheet[0].dismiss();
                sheet[0] = null;
                onCancel.run();
            }
        });

        return sheet[0] = b.show();
    }

    public static BulletinFactory getBulletinFactory() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!BotWebViewSheet.activeSheets.isEmpty()) {
            BotWebViewSheet sheet = null;
            for (BotWebViewSheet s : BotWebViewSheet.activeSheets) {
                if (s.attached) {
                    sheet = s;
                }
            }
            if (sheet != null) {
                final Context context = fragment != null && fragment.getParentActivity() != null ? fragment.getParentActivity() : LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
                return BulletinFactory.of(Bulletin.BulletinWindow.make(context), null);
//                return sheet.getBulletinFactory();
            }
        }
        if (!ArticleViewer.activeSheets.isEmpty()) {
            ArticleViewer sheet = null;
            for (ArticleViewer s : ArticleViewer.activeSheets) {
                if (s.isVisible()) {
                    sheet = s;
                }
            }
//            if (sheet != null && sheet.sheet != null && sheet.sheet.getBulletinFactory() != null) {
//                return sheet.sheet.getBulletinFactory();
//            }
            if (sheet != null) {
                final Context context = fragment != null && fragment.getParentActivity() != null ? fragment.getParentActivity() : LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
                return BulletinFactory.of(Bulletin.BulletinWindow.make(context), null);
            }
        }
        if (fragment != null && fragment.getLastSheet() != null && fragment.getLastSheet().getBulletinFactory() != null)
            return fragment.getLastSheet().getBulletinFactory();
        return BulletinFactory.of(fragment);
    }

}
