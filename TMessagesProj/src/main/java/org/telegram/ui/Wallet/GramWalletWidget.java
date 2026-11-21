package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Wallet.WalletImportFragment.formatAddressTwoLines;

import android.content.Context;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Wallet.GramWallet.SecretPhrase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * A widget's wallet call (widget-sandbox docs/sdk-v1.md §2.18, §5.13): TON Connect's `sendTransaction` or `signData`
 * from a widget message, shown in the wallet's own window with the bot of the message in its header (never anything the
 * widget says about itself). There is no TON Connect connection: on confirmation the transfer goes out the wallet's usual
 * way (`wallet.sendTransfer`) or the payload is signed for the domain `bot<bot_id>`, and the outcome, with the device's
 * wallet (`userWallet`), goes to the bot as the widget's query ({@link Delegate#sendOutcome}). The widget gets the bot's
 * answer, never the signed message. UI thread.
 */
public final class GramWalletWidget {

    /** Where the call came from: the widget's message. Exactly one of these is called, once. */
    public interface Delegate {
        /** Confirmed and done: send `queryType` to the bot as the widget's query with the call's `data`. */
        void sendOutcome(TLRPC.WidgetQueryType queryType);

        /** The user declined or closed the window. */
        void rejected();

        /** The wallet refused the call: network, sender, cells, an expired `valid_until`, no wallet here. */
        void badRequest();

        /** The call could not be completed for another reason (the transfer's fate is unknown, for one). */
        void failed();
    }

    /** Taps on the window in its first ms are ignored: the second tap of a double tap on the widget must not confirm. */
    private static final long TAP_GUARD_MS = 700;
    /** How long a wallet that is still loading is waited for before the call is refused. */
    private static final long READY_WAIT_MS = 10_000;
    /** Wallets being waited for: GramWallet#whenReady keeps its callbacks weakly. */
    private static final ArrayList<Runnable> waiting = new ArrayList<>();

    private GramWalletWidget() {
    }

    /**
     * Shows the window for `call` ({"method":"sendTransaction"|"signData","transaction"|"payload",…}, TON Connect's field
     * names, checked by the framework) from a widget of `bot`, and settles it through `delegate`.
     */
    public static void open(Context context, int account, TLRPC.User bot, JSONObject call,
            Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        Delegate once = once(delegate);
        GramWallet wallet = GramWallet.getInstance(account);
        boolean[] started = {false};
        Runnable start = () -> {
            if (started[0]) return;
            started[0] = true;
            show(context, account, bot, call, resourcesProvider, once);
        };
        if (wallet.isReady()) {
            start.run();
            return;
        }
        waiting.add(start);
        wallet.whenReady(start);
        AndroidUtilities.runOnUIThread(() -> {
            waiting.remove(start);
            if (started[0]) return;
            started[0] = true;
            BulletinFactory.showError(LocaleController.getString(R.string.WalletWidgetNoWallet));
            once.badRequest();
        }, READY_WAIT_MS);
    }

    private static void show(Context context, int account, TLRPC.User bot, JSONObject call,
            Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        GramWallet wallet = GramWallet.getInstance(account);
        String address = wallet.getAddress();
        byte[] publicKey = wallet.getPublicKey();
        if (address == null || publicKey == null || wallet.tonConnectEngine() == null) {
            BulletinFactory.showError(LocaleController.getString(R.string.WalletWidgetNoWallet));
            delegate.badRequest();
            return;
        }
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        try {
            if ("signData".equals(call.optString("method"))) {
                JSONObject payload = call.getJSONObject("payload");
                GramWalletTonConnect.validateSignData(payload, address);
                showSignData(context, account, bot, payload, address, publicKey.clone(), resourcesProvider, delegate);
            } else {
                JSONObject json = call.getJSONObject("transaction");
                GramWalletTonConnect.validateTransaction(json, address, now);
                GramWalletTonConnect.Transaction transaction = new GramWalletTonConnect.Transaction(json);
                showTransaction(context, account, bot, transaction, address, publicKey.clone(), resourcesProvider, delegate);
            }
        } catch (Exception e) {
            BulletinFactory.showError(GramWalletTonConnect.exception("widget call", e));
            delegate.badRequest();
        }
    }

    // ── the transaction ──

    private static void showTransaction(Context context, int account, TLRPC.User bot, GramWalletTonConnect.Transaction transaction,
            String address, byte[] publicKey, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        GramWallet wallet = GramWallet.getInstance(account);
        NavBottomSheet sheet = new NavBottomSheet(context, resourcesProvider)
                .setBackgroundOverride(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        addBotHeader(context, layout, bot, LocaleController.formatSpannable(R.string.WalletAppRequestsTransfer, UserObject.getUserName(bot)), resourcesProvider);
        WalletCardComponent card = new WalletCardComponent(context, false, R.drawable.wallet_card_info, 24);
        card.getCardHolderView().setMaxLines(2);
        card.getCardHolderView().setSingleLine(false);
        card.setCardHolder(formatAddressTwoLines(transaction.messages.get(0).address));
        card.getBalanceView().setText(GramWalletTonConnect.formatCardDebit(transaction.total));
        card.getUsdBalanceView().setText(wallet.formatCurrency(transaction.total));
        layout.addView(card, LayoutHelper.createLinear(-1, -2));
        LinearLayout actions = GramWalletTonConnect.transactionBlock(context, LocaleController.getString(R.string.WalletActions), resourcesProvider);
        for (GramWalletTonConnect.Transaction.Message message : transaction.messages) {
            actions.addView(GramWalletTonConnect.block(context, IconBackgroundColors.BLUE, R.drawable.mini_gram_24,
                    message.payload == null || message.comment != null ? LocaleController.getString(R.string.WalletTransfer) : LocaleController.getString(R.string.WalletContractCall),
                    LocaleController.formatSpannable(R.string.WalletTransferTo, GramWalletTonConnect.shortAddress(message.address)),
                    GramWalletTonConnect.actionPrice(-message.amount), message.comment, resourcesProvider), LayoutHelper.createLinear(-1, -2));
        }
        ScrollView scroll = limited(context, 240);
        scroll.addView(actions);
        layout.addView(scroll, LayoutHelper.createLinear(-1, -2, 12, 6, 12, 0));
        NavBottomSheet.Page page = new NavBottomSheet.Page(context);
        page.getContent().addView(layout, LayoutHelper.createFrame(-1, -2));
        sheet.addPage(page);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        TextView status = GramWalletTonConnect.requestText(context, null, 14, resourcesProvider);
        ThemeBind.textColor(status, Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
        footer.addView(status, LayoutHelper.createLinear(-1, -2, 20, 16, 20, 16));
        LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(dp(16), 0, dp(16), dp(16));
        ButtonWithCounterView decline = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        decline.setText(LocaleController.getString(R.string.WalletDecline));
        buttons.addView(decline, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 6, 0));
        ButtonWithCounterView confirm = new ButtonWithCounterView(context, resourcesProvider).setRound();
        confirm.setText(LocaleController.getString(R.string.WalletConfirm));
        buttons.addView(confirm, LayoutHelper.createLinear(0, 44, 1f, 6, 0, 0, 0));
        footer.addView(buttons);
        sheet.setFixedFooter(footer);

        Window window = new Window(sheet, delegate);
        // 0: previewing, 1: ready to send, 2: not enough Gram (the button tops the wallet up), 3: the preview failed.
        int[] state = {0};
        Runnable updateButtons = () -> {
            boolean guard = window.guarded();
            confirm.setText(LocaleController.getString(state[0] == 2 ? R.string.WalletTopUp : R.string.WalletConfirm));
            confirm.setEnabled(!guard && !window.busy && (state[0] == 1 || state[0] == 2));
            decline.setEnabled(!guard && !window.busy);
        };
        Runnable[] preview = new Runnable[1];
        preview[0] = () -> {
            if (window.closed || window.busy) return;
            state[0] = 0;
            SpannableStringBuilder loading = new SpannableStringBuilder(" ");
            loading.setSpan(new LoadingSpan(status, dp(80)), 0, loading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            status.setText(LocaleController.formatSpannable(R.string.WalletFeeAmount, loading));
            updateButtons.run();
            WalletEngine2 engine = wallet.tonConnectEngine();
            if (engine == null) {
                state[0] = 3;
                status.setText(LocaleController.getString(R.string.WalletTransactionSimulationFailed));
                updateButtons.run();
                return;
            }
            engine.previewTonConnect(transaction, (result, failure) -> {
                if (window.closed || window.busy) return;
                if (result != null && result.traceSucceeded && !result.traceIncomplete) {
                    state[0] = 1;
                    CharSequence amount = GramWallet.formatGramCapital(result.fee, true);
                    CharSequence currency = wallet.formatCurrency(result.fee, true);
                    status.setText(TextUtils.isEmpty(currency)
                            ? LocaleController.formatSpannable(R.string.WalletFeeAmount, amount)
                            : LocaleController.formatSpannable(R.string.WalletFeeAmountWithCurrency, amount, currency));
                } else if (wallet.getBalance() < transaction.total) {
                    // An empty or short wallet: top it up over the chat, then the preview runs again with the new balance.
                    state[0] = 2;
                    status.setText(LocaleController.getString(R.string.WalletNotEnoughGram));
                } else {
                    state[0] = 3;
                    status.setText(LocaleController.getString(R.string.WalletTransactionSimulationFailed));
                    if (failure != null) GramWalletTonConnect.log("widget preview", failure);
                }
                updateButtons.run();
            });
        };
        NotificationCenter.NotificationCenterDelegate balance = (id, a, args) -> {
            if (state[0] == 2 && wallet.getBalance() >= transaction.total) preview[0].run();
        };
        NotificationCenter.getInstance(account).addObserver(balance, NotificationCenter.walletUpdate);
        window.onClose = () -> NotificationCenter.getInstance(account).removeObserver(balance, NotificationCenter.walletUpdate);
        window.onGuardPassed = updateButtons;

        decline.setOnClickListener(v -> {
            if (window.guarded() || window.busy) return;
            window.finish(Delegate::rejected);
        });
        confirm.setOnClickListener(v -> {
            if (window.guarded() || window.busy) return;
            if (state[0] == 2) {
                WalletActivity.openOnramp(account, transaction.total - wallet.getBalance(), error -> {});
                return;
            }
            if (state[0] != 1) return;
            ConnectionsManager connections = ConnectionsManager.getInstance(account);
            if (transaction.validUntil != 0 && transaction.validUntil <= connections.getCurrentTime()) {
                BulletinFactory.showError(LocaleController.getString(R.string.WalletWidgetRequestExpired));
                window.finish(Delegate::badRequest);
                return;
            }
            window.busy = true;
            confirm.setLoading(true);
            updateButtons.run();
            Utilities.Callback<String> retry = error -> {
                window.busy = false;
                confirm.setLoading(false);
                if (error != null) BulletinFactory.showError(error, sheet.topBulletinContainer, resourcesProvider);
                updateButtons.run();
            };
            wallet.getSecretPhrase(true, false, (borrowed, secretError) -> {
                if (window.closed) return;
                if (borrowed == null || secretError != null) {
                    retry.run(secretError);
                    return;
                }
                if (!sameWallet(wallet, address, publicKey)) {
                    BulletinFactory.showError(LocaleController.getString(R.string.WalletWidgetNoWallet));
                    window.finish(Delegate::badRequest);
                    return;
                }
                WalletEngine2 engine = wallet.tonConnectEngine();
                if (engine == null) {
                    retry.run(LocaleController.getString(R.string.WalletWidgetNoWallet));
                    return;
                }
                SecretPhrase phrase = borrowed.copy();
                Utilities.globalQueue.postRunnable(() -> {
                    byte[] stateInit = stateInit(phrase, address, publicKey);
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (window.closed) return;
                            // The engine copies the phrase before it returns.
                            engine.prepareTonConnectTransfer(phrase, transaction, "widget:" + bot.id + ":" + Utilities.random.nextLong(),
                                    (transfer, error) -> send(account, wallet, sheet, window, transfer, error, address, publicKey, stateInit, resourcesProvider));
                        } finally {
                            phrase.close();
                        }
                    });
                });
            });
        });

        showGuarded(sheet, window);
        preview[0].run();
    }

    /** The prepared transfer goes out the wallet's usual way; its message hash goes to the bot. */
    private static void send(int account, GramWallet wallet, BottomSheet sheet, Window window, TL_wallet.sendTransfer transfer,
            String error, String address, byte[] publicKey, byte[] stateInit, Theme.ResourcesProvider resourcesProvider) {
        if (window.closed && !window.busy) return;
        if (transfer == null) {
            BulletinFactory.showError(error != null ? error : LocaleController.getString(R.string.WalletTransactionSimulationFailed));
            window.finish(Delegate::badRequest);
            return;
        }
        ConnectionsManager.getInstance(account).sendRequestTyped(transfer, AndroidUtilities::runOnUIThread, (updates, rpcError) -> {
            TL_update.TL_updateSentWalletTransaction sent = null;
            if (updates != null) {
                if (updates.update instanceof TL_update.TL_updateSentWalletTransaction) sent = (TL_update.TL_updateSentWalletTransaction) updates.update;
                for (TLRPC.Update update : updates.updates) {
                    if (update instanceof TL_update.TL_updateSentWalletTransaction) sent = (TL_update.TL_updateSentWalletTransaction) update;
                }
                Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(account).processUpdates(updates, false));
            }
            if (rpcError != null && "WALLET_KEY_MISMATCH".equals(rpcError.text)) wallet.invalidateState();
            if (sent != null && !TextUtils.isEmpty(sent.msg_hash)) {
                TLRPC.TL_widgetQueryTypeSentTransaction outcome = new TLRPC.TL_widgetQueryTypeSentTransaction();
                outcome.wallet = userWallet(wallet, address, publicKey, stateInit);
                outcome.msg_hash = botMessageHash(transfer, sent.msg_hash);
                window.finish(d -> d.sendOutcome(outcome));
            } else {
                String message = GramWalletTonConnect.rpc("widget sendTransfer", rpcError);
                BulletinFactory.showError(message);
                // A refusal is the wallet's; anything else leaves the transfer's fate unknown.
                window.finish(rpcError != null && rpcError.code == 400 ? Delegate::badRequest : Delegate::failed);
            }
        });
    }

    /**
     * The normalised hash the bot looks the transaction up by, failing that the server's: in base64, as the server's own
     * (it refuses any other form with WIDGET_MSG_HASH_INVALID and gives the bot the hash in hex).
     */
    private static String botMessageHash(TL_wallet.sendTransfer transfer, String sent) {
        String normalized = WalletMessageHash.normalized(transfer.data_normal);
        return normalized != null ? Base64.encodeToString(Utilities.hexToBytes(normalized), Base64.NO_WRAP) : sent;
    }

    // ── signData ──

    private static void showSignData(Context context, int account, TLRPC.User bot, JSONObject payload, String address,
            byte[] publicKey, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        GramWallet wallet = GramWallet.getInstance(account);
        boolean text = "text".equals(payload.optString("type"));
        NavBottomSheet sheet = new NavBottomSheet(context, resourcesProvider)
                .setBackgroundOverride(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        addBotHeader(context, layout, bot, LocaleController.formatSpannable(R.string.WalletAppRequestsSignature, UserObject.getUserName(bot)), resourcesProvider);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        ThemeBind.fillBackground(card, Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)), ThemeBind.of(Theme.key_windowBackgroundWhite), resourcesProvider);
        TextView label = GramWalletTonConnect.requestText(context, LocaleController.getString(text ? R.string.WalletSigningData : R.string.WalletBinaryData), 14, resourcesProvider);
        label.setGravity(Gravity.LEFT);
        label.setTypeface(AndroidUtilities.bold());
        ThemeBind.textColor(label, text ? Theme.key_windowBackgroundWhiteBlueHeader : Theme.key_text_RedBold, resourcesProvider);
        card.addView(label, LayoutHelper.createLinear(-1, -2, 0, 0, 0, text ? 12 : 4));
        // Bytes and cells cannot be shown: the user signs them on the bot's word, and is warned so.
        TextView content = GramWalletTonConnect.requestText(context, text ? payload.optString("text")
                : LocaleController.getString(R.string.WalletBinaryDataWarning), text ? 14 : 16, resourcesProvider);
        content.setGravity(Gravity.LEFT);
        if (text) content.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(content, LayoutHelper.createLinear(-1, -2));
        ScrollView scroll = limited(context, 320);
        scroll.addView(card);
        layout.addView(scroll, LayoutHelper.createLinear(-1, -2, 12, 4, 12, 0));
        NavBottomSheet.Page page = new NavBottomSheet.Page(context);
        page.getContent().addView(layout, LayoutHelper.createFrame(-1, -2));
        sheet.addPage(page);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        if (text) {
            TextView hint = GramWalletTonConnect.requestText(context, LocaleController.getString(R.string.WalletReviewSigningData), 14, resourcesProvider);
            hint.setGravity(Gravity.LEFT);
            ThemeBind.textColor(hint, Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
            footer.addView(hint, LayoutHelper.createLinear(-1, -2, 36, 12, 36, 0));
        }
        LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(dp(16), dp(16), dp(16), dp(16));
        ButtonWithCounterView decline = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        decline.setText(LocaleController.getString(R.string.Cancel));
        ButtonWithCounterView sign = new ButtonWithCounterView(context, resourcesProvider).setRound();
        sign.setText(LocaleController.getString(R.string.WalletSign));
        buttons.addView(decline, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 6, 0));
        buttons.addView(sign, LayoutHelper.createLinear(0, 44, 1f, 6, 0, 0, 0));
        footer.addView(buttons);
        sheet.setFixedFooter(footer);

        Window window = new Window(sheet, delegate);
        Runnable updateButtons = () -> {
            boolean guard = window.guarded();
            sign.setEnabled(!guard && !window.busy);
            decline.setEnabled(!guard && !window.busy);
        };
        window.onGuardPassed = updateButtons;
        decline.setOnClickListener(v -> {
            if (window.guarded() || window.busy) return;
            window.finish(Delegate::rejected);
        });
        sign.setOnClickListener(v -> {
            if (window.guarded() || window.busy) return;
            window.busy = true;
            sign.setLoading(true);
            updateButtons.run();
            wallet.getSecretPhrase(true, false, (borrowed, secretError) -> {
                if (window.closed) return;
                if (borrowed == null || secretError != null) {
                    window.busy = false;
                    sign.setLoading(false);
                    if (secretError != null) BulletinFactory.showError(secretError, sheet.topBulletinContainer, resourcesProvider);
                    updateButtons.run();
                    return;
                }
                if (!sameWallet(wallet, address, publicKey)) {
                    BulletinFactory.showError(LocaleController.getString(R.string.WalletWidgetNoWallet));
                    window.finish(Delegate::badRequest);
                    return;
                }
                SecretPhrase phrase = borrowed.copy();
                int timestamp = ConnectionsManager.getInstance(account).getCurrentTime();
                String domain = "bot" + bot.id;
                Utilities.globalQueue.postRunnable(() -> {
                    JSONObject signed = null;
                    String failure = null;
                    byte[] stateInit = null;
                    try {
                        signed = WalletEngine2.signData(phrase, signingSession(), address, publicKey, payload, domain, timestamp);
                        stateInit = stateInit(phrase, address, publicKey);
                    } catch (Exception e) {
                        failure = GramWalletTonConnect.exception("widget sign data", e);
                    } finally {
                        phrase.close();
                    }
                    JSONObject result = signed;
                    byte[] resultStateInit = stateInit;
                    String error = failure;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (result == null) {
                            BulletinFactory.showError(error);
                            window.finish(Delegate::badRequest);
                            return;
                        }
                        TLRPC.TL_widgetQueryTypeSignData outcome = new TLRPC.TL_widgetQueryTypeSignData();
                        outcome.wallet = userWallet(wallet, address, publicKey, resultStateInit);
                        outcome.sign_data = new TLRPC.TL_dataJSON();
                        outcome.sign_data.data = result.toString();
                        window.finish(d -> d.sendOutcome(outcome));
                    });
                });
            });
        });
        showGuarded(sheet, window);
        updateButtons.run();
    }

    /**
     * signData has no TON Connect connection: the engine derives a session for any call, so a throwaway one is enough
     * (the signature is over the payload, the domain, the time and the wallet's own key; the session takes no part).
     */
    private static TL_wallet.tonConnectSession signingSession() {
        TL_wallet.tonConnectSession session = new TL_wallet.tonConnectSession();
        byte[] client = new byte[32];
        Utilities.random.nextBytes(client);
        session.dapp_client_id = Utilities.bytesToHex(client).toLowerCase(java.util.Locale.ROOT);
        session.nonce = new byte[32];
        Utilities.random.nextBytes(session.nonce);
        return session;
    }

    // ── shared ──

    /**
     * The device's wallet, the one that pays or signs (with the backup off it can differ from the profile's card). The key
     * and the StateInit go together (one flag): without the StateInit neither goes.
     */
    private static TLRPC.TL_userWallet userWallet(GramWallet wallet, String address, byte[] publicKey, byte[] stateInit) {
        TLRPC.TL_userWallet result = new TLRPC.TL_userWallet();
        result.address = address;
        result.balance = wallet.getBalance();
        if (publicKey != null && stateInit != null) {
            result.public_key = publicKey;
            result.state_init = stateInit;
        }
        return result;
    }

    /**
     * The wallet contract's StateInit (a bag of cells), the `walletStateInit` of TON Connect's `ton_addr`: with it the bot
     * checks the key of a wallet that is not deployed yet. Off the UI thread; null if the engine can't give it.
     */
    private static byte[] stateInit(SecretPhrase phrase, String address, byte[] publicKey) {
        try {
            JSONObject crypto = WalletEngine2.tonConnectCrypto(phrase, signingSession(), address, publicKey, new JSONObject().put("account", true));
            return Base64.decode(crypto.getJSONObject("account").getString("walletStateInit"), Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static boolean sameWallet(GramWallet wallet, String address, byte[] publicKey) {
        return Objects.equals(address, wallet.getAddress()) && Arrays.equals(publicKey, wallet.getPublicKey());
    }

    /** The bot of the message: its avatar, the title, its username. */
    private static void addBotHeader(Context context, LinearLayout layout, TLRPC.User bot, CharSequence title, Theme.ResourcesProvider resourcesProvider) {
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(38));
        AvatarDrawable placeholder = new AvatarDrawable();
        placeholder.setInfo(bot);
        avatar.setForUserOrChat(bot, placeholder);
        layout.addView(avatar, LayoutHelper.createLinear(76, 76, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));
        TextView titleView = GramWalletTonConnect.requestText(context, title, 20, resourcesProvider);
        titleView.setTypeface(AndroidUtilities.bold());
        layout.addView(titleView, LayoutHelper.createLinear(-1, -2, 24, 12, 24, 0));
        String username = UserObject.getPublicUsername(bot);
        if (!TextUtils.isEmpty(username)) {
            layout.addView(GramWalletTonConnect.domainView(context, "@" + username, resourcesProvider),
                    LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL, 36, 6, 36, 12));
        } else {
            layout.addView(new View(context), LayoutHelper.createLinear(-1, 12));
        }
    }

    private static ScrollView limited(Context context, int maxHeightDp) {
        return new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(heightMeasureSpec), dp(maxHeightDp)), MeasureSpec.AT_MOST));
            }
        };
    }

    /** Shown with taps ignored for its first {@link #TAP_GUARD_MS}: no button, no tap outside. */
    private static void showGuarded(BottomSheet sheet, Window window) {
        sheet.setCanDismissWithTouchOutside(false);
        sheet.setDelegate(new BottomSheet.BottomSheetDelegate() {
            @Override
            public boolean canDismiss() {
                return !window.busy;
            }
        });
        sheet.setOnDismissListener(dialog -> window.close());
        sheet.show();
        AndroidUtilities.runOnUIThread(() -> {
            if (window.closed) return;
            sheet.setCanDismissWithTouchOutside(true);
            if (window.onGuardPassed != null) window.onGuardPassed.run();
        }, TAP_GUARD_MS);
    }

    /** One window: settles its delegate once; closing it without a decision declines. */
    private static final class Window {
        final BottomSheet sheet;
        final Delegate delegate;
        final long opened = SystemClock.uptimeMillis();
        boolean busy, closed, settled;
        Runnable onGuardPassed, onClose;

        Window(BottomSheet sheet, Delegate delegate) {
            this.sheet = sheet;
            this.delegate = delegate;
        }

        boolean guarded() {
            return SystemClock.uptimeMillis() - opened < TAP_GUARD_MS;
        }

        void finish(Utilities.Callback<Delegate> outcome) {
            busy = false;
            if (!settled) {
                settled = true;
                outcome.run(delegate);
            }
            if (!closed) sheet.dismiss();
        }

        void close() {
            if (closed) return;
            closed = true;
            if (onClose != null) onClose.run();
            if (!settled) {
                settled = true;
                delegate.rejected();
            }
        }
    }

    private static Delegate once(Delegate delegate) {
        boolean[] done = {false};
        return new Delegate() {
            @Override
            public void sendOutcome(TLRPC.WidgetQueryType queryType) {
                if (done[0]) return;
                done[0] = true;
                delegate.sendOutcome(queryType);
            }

            @Override
            public void rejected() {
                if (done[0]) return;
                done[0] = true;
                delegate.rejected();
            }

            @Override
            public void badRequest() {
                if (done[0]) return;
                done[0] = true;
                delegate.badRequest();
            }

            @Override
            public void failed() {
                if (done[0]) return;
                done[0] = true;
                delegate.failed();
            }
        };
    }
}
