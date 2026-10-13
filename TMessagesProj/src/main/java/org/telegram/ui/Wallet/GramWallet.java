package org.telegram.ui.Wallet;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.PasskeysController.ktxCallback;
import static org.telegram.ui.web.BotWebViewContainer.obj;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.ConferenceCall;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_toncenter;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PasscodeActivity;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class GramWallet implements GramWalletStreaming.IGramWalletStreamingSubscriber {

    public final int currentAccount;
    private WalletEngine2 engine;

    WalletEngine2 tonConnectEngine() { return engine; }
    public GramWalletStorage storage;
    private String engineAddress;
    private TL_wallet.WalletState state;
    private TL_update.TL_updateWalletGaslessInfo gasless;
    public final GramWalletTonConnect tonConnect;
    public final CurrenciesController currencies;
    private Boolean hasWaltBalance;
    private String waltBalanceLink;

    private long serverBalanceReceivedTime;
    private long streamingBalanceReceivedTime;
    private boolean balanceFromStreaming;

    private boolean usePasscodeSetting;

    private TransactionsList transactions;
    private CollectiblesList nfts;
    private ArrayList<TL_wallet.walletTransaction> pendingTransactions = new ArrayList<>();
    private ArrayList<PendingDisableBackup> pendingDisableBackups = new ArrayList<>();

    private class PendingDisableBackup {
        public String msg_hash;
        public SecretPhrase secretPhrase;
        public byte[] publicKey;

        public void disableBackup() {
            final SecretPhrase owned = secretPhrase;
            secretPhrase = null;
            try {
                proveChallenge(owned, (proof, error4) -> {
                    if (proof == null) {
                        err("disable backup: failed to prove challenge: " + (error4 == null ? "NULL_ERROR" : error4));
                        return;
                    }
                    final TL_wallet.disableBackup disableBackup = new TL_wallet.disableBackup();
                    disableBackup.new_public_key = publicKey;
                    disableBackup.proof = new TL_wallet.walletOwnershipProof();
                    disableBackup.proof.signature = proof.signature;
                    disableBackup.proof.timestamp = proof.timestamp;
                    getConnectionsManager().sendRequestTyped(disableBackup, AndroidUtilities::runOnUIThread, (state, error5) -> {
                        if (state == null) {
                            err("disable backup: failed to disable backup on server: " + (error5 == null ? "NULL_ERROR" : error5));
                            return;
                        }
                        updateState(state);
                        onWalletChanged();
                        AndroidUtilities.runOnUIThread(() -> {
                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            BulletinFactory.of(lastFragment)
                                    .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.WalletBackupDisabled), LocaleController.getString(R.string.WalletBackupDisabledInfo))
                                    .setDuration(Bulletin.DURATION_PROLONG)
                                    .show();
                        }, 1000);
                    });
                });
            } finally {
                owned.close();
            }
        }
    }

    @Nullable
    private GramWalletStreaming streaming;
    private final HashMap<TL_wallet.walletTransaction, GramWalletPolling> polling = new HashMap<>();
    private String pollingAddress;

    private static volatile GramWallet[] Instance = new GramWallet[UserConfig.MAX_ACCOUNT_COUNT];

    public static GramWallet getInstance(int num) {
        GramWallet localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (GramWallet.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new GramWallet(num);
                }
            }
        }
        return localInstance;
    }

    public static void ensureLocalWalletBalances(int currentAccount, Runnable whenDone) {
        if (!MessagesController.getInstance(currentAccount).config.walletAvailable.get()) {
            whenDone.run();
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            final GramWallet wallet = getInstance(currentAccount);
            final NotificationCenter notifications = NotificationCenter.getInstance(currentAccount);
            class WaitForBalances implements Runnable, NotificationCenter.NotificationCenterDelegate {
                private boolean finished;

                @Override
                public void run() {
                    if (finished) return;
                    finished = true;
                    AndroidUtilities.cancelRunOnUIThread(this);
                    notifications.removeObserver(this, NotificationCenter.walletUpdate);
                    whenDone.run();
                }

                private void check() {
                    if (finished || wallet.state == null) return;
                    for (PreviousWallet previous : wallet.previousWallets) {
                        if (previous.loading) return;
                    }
                    run();
                }

                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.walletUpdate) {
                        check();
                    }
                }
            }
            final WaitForBalances waiter = new WaitForBalances();
            notifications.addObserver(waiter, NotificationCenter.walletUpdate);
            AndroidUtilities.runOnUIThread(waiter, 10_000);
            if (wallet.state == null && wallet.requestingStateId < 0) {
                wallet.requestState();
            }
            wallet.refreshPreviousWallets();
            waiter.check();
        });
    }

    private void log(String s) {
        FileLog.d("[gram-wallet] " + s);
    }
    private void err(String s) {
        FileLog.e("[gram-wallet] " + s);
    }
    private void err(String s, Throwable e) {
        FileLog.e("[gram-wallet] " + s, e);
    }

    public GramWallet(int account) {
        currentAccount = account;
        loadPrefs();
        currencies = new CurrenciesController(this);
        requestState();
        requestGaslessInfo();
        requestWaltBalance();
        tonConnect = new GramWalletTonConnect(this);
        GramWalletStorage.addChangeListener(onLocalWalletsChanged);
        refreshPreviousWallets();
    }

    public boolean isReady() {
        return state instanceof TL_wallet.TL_walletState && engine != null && storage != null;
    }

    private ArrayList<WeakReference<Runnable>> whenReadyCallbacks = new ArrayList<>();
    private ArrayList<WeakReference<Runnable>> whenReadyGaslessCallbacks = new ArrayList<>();

    public void whenReady(Runnable callback) {
        if (isReady()) {
            callback.run();
            return;
        }
        whenReadyCallbacks.add(new WeakReference<>(callback));
    }

    public void whenReadyGasless(Runnable callback) {
        if (isReady() && gasless != null) {
            callback.run();
            return;
        }
        whenReadyGaslessCallbacks.add(new WeakReference<>(callback));
    }

    public String getAddress() {
        if (!(state instanceof TL_wallet.TL_walletState)) return null;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.address;
    }

    public byte[] getPublicKey() {
        if (!(state instanceof TL_wallet.TL_walletState)) return null;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.public_key;
    }

    public long getBalance() {
        if (!(state instanceof TL_wallet.TL_walletState)) return 0;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.balance;
    }

    public boolean isBackupEnabled() {
        if (!(state instanceof TL_wallet.TL_walletState)) return false;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.backup_enabled;
    }

    public boolean canEnableBackup() {
        if (!(state instanceof TL_wallet.TL_walletState)) return false;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.can_enable_backup && storage != null && storage.containsSecretPhrase(state.public_key);
    }

    public boolean canExportPhrase() {
        if (!(state instanceof TL_wallet.TL_walletState)) return false;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.can_export_phrase || storage != null && storage.containsSecretPhrase(state.public_key);
    }

    public boolean canSend() {
        if (!(state instanceof TL_wallet.TL_walletState)) return false;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return state.backup_enabled || storage != null && storage.containsSecretPhrase(state.public_key);
    }

    public boolean cantAccessWallet() {
        if (!(state instanceof TL_wallet.TL_walletState)) return false;
        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
        return !state.backup_enabled && !(storage != null && storage.containsSecretPhrase(state.public_key));
    }

    public boolean isGaslessAvailable() {
        return gasless != null && gasless.available && gasless.left > 0 && !TextUtils.isEmpty(gasless.relayer_address);
    }
    public TL_update.TL_updateWalletGaslessInfo getGasless() {
        return gasless;
    }
    public boolean hasWaltBalance() {
        return hasWaltBalance != null && hasWaltBalance && !TextUtils.isEmpty(waltBalanceLink);
    }
    public String getWaltUrl() {
        return waltBalanceLink;
    }

    public void updateState(TL_wallet.WalletState state) {
        if (state == null) return;
        final boolean balanceChanged = state instanceof TL_wallet.TL_walletState
                && (!(this.state instanceof TL_wallet.TL_walletState)
                || ((TL_wallet.TL_walletState) this.state).balance
                != ((TL_wallet.TL_walletState) state).balance);
        this.state = state;
        onStateUpdate();
        notifyUpdate();
        if (balanceChanged && transactions != null) {
            transactions.invalidate();
        }
    }

    public void onUpdate(TL_update.TL_updateWalletState update) {
        if (update == null) return;
        updateState(update.state);
    }

    public void onUpdate(TL_update.TL_updateWalletGaslessInfo update) {
        gasless = update;
        onStateUpdate();
        notifyUpdate();
    }

    public void onUpdate(TL_update.TL_updateSentWalletTransaction update) {
        if (!isValidMessageHash(update.msg_hash)) return;
        for (TL_wallet.walletTransaction pending : new ArrayList<>(pendingTransactions)) {
            if (sameMessageHash(pending.messageHash, update.msg_hash)) {
                applySentTransaction(pending, update);
                return;
            }
        }
        if (update.transaction != null) {
            TL_wallet.walletTransaction transaction = update.transaction;
            transaction.messageHash = update.msg_hash;
            transaction.gasless = update.gasless;
            transaction.pending = false;
            pendingTransactions.add(0, transaction);
            getTransactions().updatedTransactions();
            getTransactions().notifyUpdate();
        }
    }

    public static boolean messageMatchesTransaction(MessageObject message, TL_wallet.walletTransaction transaction) {
        if (message == null || message.messageOwner == null || transaction == null
                || !message.isOutOwner()
                || !(message.messageOwner.action instanceof TLRPC.TL_messageActionGramTransfer)) return false;
        TLRPC.TL_messageActionGramTransfer action = (TLRPC.TL_messageActionGramTransfer) message.messageOwner.action;
        return (transaction.random_id != 0 && message.messageOwner.random_id == transaction.random_id)
                || (transaction.localMessageId != 0 && message.getId() == transaction.localMessageId)
                || (!TextUtils.isEmpty(transaction.id) && TextUtils.equals(transaction.id, action.transaction_id));
    }

    public TL_wallet.walletTransaction findTransaction(MessageObject msg) {
        if (msg == null || msg.messageOwner == null) return null;
        if (!(msg.messageOwner.action instanceof TLRPC.TL_messageActionGramTransfer)) return null;
        final TLRPC.TL_messageActionGramTransfer action = (TLRPC.TL_messageActionGramTransfer) msg.messageOwner.action;
        if (!TextUtils.isEmpty(action.transaction_id)) {
            if (transactions != null) {
                for (int i = 0; i < transactions.allTransactions.size(); ++i) {
                    final TL_wallet.walletTransaction t = transactions.allTransactions.get(i);
                    if (TextUtils.equals(t.id, action.transaction_id))
                        return t;
                }
            }
        }
        if (msg.messageOwner.random_id != 0) {
            for (final TL_wallet.walletTransaction t : pendingTransactions) {
                if (t.random_id == msg.messageOwner.random_id) {
                    return t;
                }
            }
        }
        return null;
    }

    private void processTransferUpdates(TLRPC.Updates updates) {
        Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processUpdates(updates, false));
    }

    private TL_update.TL_updateSentWalletTransaction getSentTransaction(TLRPC.Updates updates) {
        if (updates == null) return null;
        if (updates.update instanceof TL_update.TL_updateSentWalletTransaction) {
            return (TL_update.TL_updateSentWalletTransaction) updates.update;
        }
        for (TLRPC.Update update : updates.updates) {
            if (update instanceof TL_update.TL_updateSentWalletTransaction) {
                return (TL_update.TL_updateSentWalletTransaction) update;
            }
        }
        return null;
    }

    private void applySentTransaction(TL_wallet.walletTransaction pending, TL_update.TL_updateSentWalletTransaction update) {
        pending.messageHash = update.msg_hash;
        pending.gasless = update.gasless;
        if (update.transaction != null) {
            applyConfirmedTransaction(pending, update.transaction);
        } else {
            onWalletPageUpdate();
        }
    }

    private void applyConfirmedTransaction(TL_wallet.walletTransaction pending, TL_wallet.walletTransaction remote) {
        pending.id = remote.id;
        pending.incoming = remote.incoming;
        pending.gasless = remote.gasless;
        pending.failed = remote.failed;
        pending.key_change = remote.key_change;
        pending.comment_encrypted = remote.comment_encrypted;
        pending.comment_encrypted_preparing = false;
        pending.amount = remote.amount;
        pending.fee = remote.fee;
        pending.date = remote.date;
        pending.peer = mergeRecipientPeer(pending.peer, remote.peer);
        pending.comment = remote.comment;
        pending.tx_hash = remote.tx_hash;
        if (remote.nft != null) pending.nft = remote.nft;
        for (int i = pendingTransactions.size() - 1; i >= 0; --i) {
            TL_wallet.walletTransaction other = pendingTransactions.get(i);
            if (other != pending && !other.pending && !TextUtils.isEmpty(remote.id) && TextUtils.equals(other.id, remote.id)) {
                pendingTransactions.remove(i);
            }
        }
        completePendingTransaction(pending, !remote.failed, remote.tx_hash);
        if (pending.nft != null && nfts != null) {
            nfts.onTransferUpdated(pending);
            nfts.invalidate();
        }
    }

    public void invalidateState() {
        if (transactions != null) {
            transactions.invalidate();
        }
        if (requestingStateId >= 0) return;
        requestState();
    }

    private int requestingStateId = -1;

    private void requestState() {
        log("requesting state");
        requestingStateId = getConnectionsManager().sendRequestTyped(new TL_wallet.getState(), AndroidUtilities::runOnUIThread, (state, err) -> {
            requestingStateId = -1;
            if (state == null) {
                err("requesting gasless info: " + (err != null ? err.text : "NULL_ERROR"));
                return;
            }
            final long now = System.currentTimeMillis();
            if (state instanceof TL_wallet.TL_walletState && this.state instanceof TL_wallet.TL_walletState && balanceFromStreaming) {
                final TL_wallet.TL_walletState oldState = (TL_wallet.TL_walletState) this.state;
                final TL_wallet.TL_walletState newState = (TL_wallet.TL_walletState) state;
                if (now - streamingBalanceReceivedTime < 30 * 1000) {
                    // preserve balance from streaming, server might return cached balance value
                    newState.balance = oldState.balance;
                } else {
                    serverBalanceReceivedTime = now;
                    balanceFromStreaming = false;
                }
            } else {
                serverBalanceReceivedTime = now;
                balanceFromStreaming = false;
            }
            this.state = state;
            onStateUpdate();
            notifyUpdate();
        });
    }

    private int requestingGaslessInfoId = -1;
    private void requestGaslessInfo() {
        log("requesting gasless info");
        requestingGaslessInfoId = getConnectionsManager().sendRequestTyped(new TL_wallet.getGaslessInfo(), AndroidUtilities::runOnUIThread, (gasless, err) -> {
            requestingGaslessInfoId = -1;
            if (gasless == null) {
                err("requesting gasless info: " + (err != null ? err.text : "NULL_ERROR"));
                return;
            }
            processTransferUpdates(gasless);
        });
    }

    private int requestingWaltId = -1;
    private void requestWaltBalance() {
        log("requesting has walt balance");
        requestingWaltId = getConnectionsManager().sendRequestTyped(new TL_wallet.getExistingWaltBalance(), AndroidUtilities::runOnUIThread, (balance, error) -> {
            requestingWaltId = -1;
            if (balance != null) {
                hasWaltBalance = balance.has_balance;
                waltBalanceLink = balance.url;
            }
            if (hasWaltBalance()) {
                notifyUpdate();
            }
        });
    }

    private void cancel() {
        if (requestingStateId >= 0) {
            getConnectionsManager().cancelRequest(requestingStateId, true);
            requestingStateId = -1;
        }
        if (requestingGaslessInfoId >= 0) {
            getConnectionsManager().cancelRequest(requestingGaslessInfoId, true);
            requestingGaslessInfoId = -1;
        }
        if (requestingWaltId >= 0) {
            getConnectionsManager().cancelRequest(requestingWaltId, true);
            requestingWaltId = -1;
        }
    }

    public static void cleanup(int currentAccount) {
        final GramWallet localInstance = Instance[currentAccount];
        if (localInstance != null) {
            localInstance.reset();
        }
    }

    public void reset() {
        GramWalletStorage.removeChangeListener(onLocalWalletsChanged);
        previousWallets.clear();
        cachedAddressInfos.clear();
        if (engine != null) {
            engine.close();
            engine = null;
            engineAddress = null;
        }
        if (nfts != null) nfts.clear();
        cancel();
        stopPolling();
        listeningTokens.clear();
        if (streaming != null) {
            streaming.stop();
            streaming = null;
        }
        AndroidUtilities.cancelRunOnUIThread(refreshStreamingHistory);
        synchronized (GramWallet.class) {
            if (Instance[currentAccount] == this) {
                Instance[currentAccount] = null;
            }
        }
    }

    private int lastListenToken = 0;
    private HashSet<Integer> listeningTokens = new HashSet<>();

    public Runnable listen() {
        final int token = lastListenToken++;
        final boolean wasEmpty = listeningTokens.isEmpty();
        listeningTokens.add(token);
        if (wasEmpty) onWalletPageUpdate();
        return () -> {
            listeningTokens.remove(token);
            if (listeningTokens.isEmpty()) onWalletPageUpdate();
        };
    }
    private void onWalletPageUpdate() {
        updatePolling();
        final boolean shouldOpen = isReady() && !TextUtils.isEmpty(getAddress()) && !listeningTokens.isEmpty();
        final boolean isOpen = streaming != null && streaming.isRunning();
        if (isOpen != shouldOpen || isOpen && streaming != null && !TextUtils.equals(streaming.address, getAddress())) {
            if (shouldOpen) {
                if (streaming != null && !TextUtils.equals(streaming.address, getAddress())) {
                    streaming.stop();
                    streaming = null;
                }
                if (streaming == null) {
                    streaming = new GramWalletStreaming(currentAccount, getAddress(), this);
                }
                if (!streaming.isRunning()) streaming.start();
            } else {
                if (streaming != null) {
                    streaming.stop();
                }
                AndroidUtilities.cancelRunOnUIThread(refreshStreamingHistory);
            }
        }

    }

    private void stopPolling() {
        for (GramWalletPolling poller : polling.values()) poller.stop();
        polling.clear();
    }

    private void updatePolling() {
        if (!TextUtils.equals(pollingAddress, getAddress())) {
            stopPolling();
            pollingAddress = getAddress();
        }
        if (!isReady() || TextUtils.isEmpty(getAddress())) {
            stopPolling();
            return;
        }
        for (TL_wallet.walletTransaction pending : new ArrayList<>(polling.keySet())) {
            if (!pending.pending
                    || !TextUtils.equals(polling.get(pending).messageHash, pending.messageHash)) {
                polling.remove(pending).stop();
            }
        }
        for (TL_wallet.walletTransaction pending : pendingTransactions) {
            if (!pending.pending || TextUtils.isEmpty(pending.messageHash) || polling.containsKey(pending)) continue;
            GramWalletPolling poller = new GramWalletPolling(currentAccount, pending.messageHash, result -> {
                for (TL_wallet.walletTransaction remote : result.transactions) {
                    MessagesController.getInstance(currentAccount).putUsers(result.users, false);
                    MessagesController.getInstance(currentAccount).putChats(result.chats, false);
                    applyConfirmedTransaction(pending, remote);
                    invalidateState();
                    return true;
                }
                return false;
            });
            polling.put(pending, poller);
            poller.start();
        }
    }

    @Override
    public void onEvent(JSONObject event) {
        if (streaming == null || !TextUtils.equals(streaming.address, getAddress())) return;
        final String type = event.optString("type");
        final String finality = event.optString("finality");
        log("streaming " + type + ", finality: " + finality);
        if ("account_state_change".equals(type)) {
            if ("finalized".equals(finality) && state instanceof TL_wallet.TL_walletState) {
                final JSONObject accountState = event.optJSONObject("state");
                if (accountState != null) {
                    try {
                        final long balance = Long.parseLong(accountState.getString("balance"));
                        if (balance >= 0 && ((TL_wallet.TL_walletState) state).balance != balance) {
                            log("streaming updates balance to " + balance);
                            streamingBalanceReceivedTime = System.currentTimeMillis();
                            balanceFromStreaming = true;
                            ((TL_wallet.TL_walletState) state).balance = balance;
                            onStateUpdate();
                            notifyUpdate();
                            if (transactions != null) {
                                transactions.invalidate();
                            }
                        }
                    } catch (Exception e) {
                        err("Invalid streaming balance");
                    }
                }
            }
        }
        if (transactions != null) {
            transactions.onStreamEvent(event);
        }
        if ("confirmed".equals(finality) || "finalized".equals(finality) || "trace_invalidated".equals(type)) {
            AndroidUtilities.cancelRunOnUIThread(refreshStreamingHistory);
            AndroidUtilities.runOnUIThread(refreshStreamingHistory, 250);
        }
    }

    private void clearPendingTransactions() {
        stopPolling();
        pendingTransactions.clear();
        for (PendingDisableBackup pending : pendingDisableBackups) {
            if (pending.secretPhrase != null) pending.secretPhrase.close();
        }
        pendingDisableBackups.clear();
        if (transactions != null) {
            transactions.clear();
            transactions.updatedTransactions();
            transactions.notifyUpdate();
        }
        onWalletPageUpdate();
    }

    private void completePendingTransaction(TL_wallet.walletTransaction pending, boolean success, String tx_hash) {
        if (!pending.pending) return;
        final TransactionsList transactions = getTransactions();
        pending.failed = !success;
        pending.pending = false;
        if (!TextUtils.isEmpty(tx_hash)) {
            pending.tx_hash = tx_hash;
        }
        if (pending.key_change) {
            PendingDisableBackup disableBackup = null;
            for (PendingDisableBackup p : pendingDisableBackups) {
                if (TextUtils.equals(p.msg_hash, pending.messageHash)) {
                    disableBackup = p;
                    break;
                }
            }
            if (disableBackup != null) {
                if (success) {
                    disableBackup.disableBackup();
                } else if (disableBackup.secretPhrase != null) {
                    disableBackup.secretPhrase.close();
                }
                pendingDisableBackups.remove(disableBackup);
            }
        }
        log("completed pending transaction: msg_hash=" + pending.messageHash
                + ", body_hash=" + pending.gaslessMessageBodyHash
                + ", transaction_id=" + pending.id
                + ", tx_hash=" + pending.tx_hash + ", success=" + success);
        transactions.updatedTransactions();
        transactions.notifyUpdate();
        onWalletPageUpdate();
    }

    private boolean isValidMessageHash(String hash) {
        if (TextUtils.isEmpty(hash)) return false;
        try {
            transactionHashBytes(hash);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean sameMessageHash(String first, String second) {
        return !TextUtils.isEmpty(first) && !TextUtils.isEmpty(second) && sameTransactionHash(first, second);
    }

    public static boolean addressEquals(String first, String second) {
        String a = transferRawAddress(first);
        String b = transferRawAddress(second);
        return a != null && a.equals(b);
    }

    private static String rawAddressToFriendly(String raw) {
        // Bounceable, url-safe, unpadded presentation matching the server and the streaming address_book.
        try {
            int colon = raw.indexOf(':');
            if (colon < 0) return raw;
            int wc = Integer.parseInt(raw.substring(0, colon));
            String hex = raw.substring(colon + 1);
            if (hex.length() != 64) return raw;
            byte[] bytes = new byte[36];
            bytes[0] = 0x51;
            bytes[1] = (byte) wc;
            for (int i = 0; i < 32; ++i) {
                bytes[2 + i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            int crc = 0;
            for (int i = 0; i < 34; ++i) {
                crc ^= (bytes[i] & 0xff) << 8;
                for (int b = 0; b < 8; ++b) {
                    crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
                }
            }
            bytes[34] = (byte) (crc >>> 8);
            bytes[35] = (byte) crc;
            return android.util.Base64.encodeToString(bytes,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return raw;
        }
    }

    public static boolean transactionMatches(TL_wallet.walletTransaction a, TL_wallet.walletTransaction b) {
        return (
            !TextUtils.isEmpty(a.id) && TextUtils.equals(a.id, b.id) ||
            sameTransactionHash(a.tx_hash, b.tx_hash) ||
            sameTransactionHash(a.messageHash, b.messageHash) ||
            !a.incoming && !b.incoming
                    && (sameTransactionHash(a.gaslessMessageBodyHash, b.gaslessMessageBodyHash)
                    || sameTransactionHash(a.normalMessageBodyHash, b.normalMessageBodyHash))
        );
    }

    public static boolean sameTransactionHash(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) return false;
        if (TextUtils.equals(first, second)) return true;
        try {
            return Arrays.equals(transactionHashBytes(first), transactionHashBytes(second));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] transactionHashBytes(String hash) {
        if (hash.matches("[0-9a-fA-F]{64}")) {
            byte[] bytes = new byte[32];
            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = (byte) Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        }
        byte[] bytes = android.util.Base64.decode(hash.replace('-', '+').replace('_', '/'), android.util.Base64.DEFAULT);
        if (bytes.length != 32) throw new IllegalArgumentException("Invalid transaction hash");
        return bytes;
    }

    private static String transferRawAddress(String address) {
        if (address == null) return null;
        if (address.matches("-?[0-9]+:[0-9a-fA-F]{64}")) return address.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = android.util.Base64.decode(address.replace('-', '+').replace('_', '/'), android.util.Base64.DEFAULT);
            if (bytes.length != 36) return null;
            // Compare workchain + account ID, ignoring friendly-address presentation flags.
            StringBuilder raw = new StringBuilder().append((int) bytes[1]).append(':');
            for (int i = 2; i < 34; ++i) {
                raw.append(Character.forDigit((bytes[i] >> 4) & 15, 16));
                raw.append(Character.forDigit(bytes[i] & 15, 16));
            }
            return raw.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private final Runnable refreshStreamingHistory = () -> {
        if (streaming != null && streaming.isRunning()) invalidateState();
    };

    public void notifyUpdate() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.walletUpdate, this);
    }

    private void onStateUpdate() {
        final String address;
        final byte[] public_key;
        if (state instanceof TL_wallet.TL_walletState) {
            final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
            address = state.address;
            public_key = state.public_key;
        } else {
            address = null;
            public_key = null;
        }
        if (storage != null && !TextUtils.equals(storage.getAddress(), address)) {
            if (address == null) {
                log("received empty state, leaving wallet storage of " + storage.getAddress());
            } else {
                log("received new state of " + address + ", leaving wallet storage of " + storage.getAddress());
            }
            storage = null;
        }
        if (!TextUtils.isEmpty(address) && storage == null) {
            log("setting up wallet storage of " + address);
            storage = new GramWalletStorage(ApplicationLoader.applicationContext, currentAccount, address);
            log("storage public keys = " + storage.getPublicKeys().length);
        }
        if (nfts != null) nfts.setAddress(address);
        if (engine != null && !TextUtils.equals(engineAddress, address)) {
            engine.close();
            engine = null;
            engineAddress = null;
        }
        if (!TextUtils.isEmpty(address) && public_key != null && (engine == null || !TextUtils.equals(engineAddress, address))) {
            if (engine != null) {
                log("closing wallet-engine of " + engineAddress);
                engine.close();
                engine = null;
            }
            log("creating wallet-engine for " + address);
            try {
                engine = new WalletEngine2(currentAccount, engineAddress = address, public_key);
            } catch (Exception e) {
                err("failed to create engine!", e);
                engineAddress = null;
            }
        }
        if (state instanceof TL_wallet.TL_walletState && engine != null) {
            for (WeakReference<Runnable> ref : whenReadyCallbacks) {
                final Runnable runnable = ref.get();
                if (runnable != null) {
                    AndroidUtilities.runOnUIThread(runnable);
                }
            }
            whenReadyCallbacks.clear();
        }
        if (state instanceof TL_wallet.TL_walletState && engine != null && gasless != null) {
            for (WeakReference<Runnable> ref : whenReadyGaslessCallbacks) {
                final Runnable runnable = ref.get();
                if (runnable != null) {
                    AndroidUtilities.runOnUIThread(runnable);
                }
            }
            whenReadyGaslessCallbacks.clear();
        }
        if (!TextUtils.equals(previousWalletAddress, address)) {
            refreshPreviousWallets();
        }
        onWalletPageUpdate();
    }

    public boolean needsPasscode() {
        return usePasscodeSetting && !SharedConfig.passcodeHash.isEmpty();
    }

    public boolean needsFingerprint() {
        if (!(state instanceof TL_wallet.TL_walletState)) return false;
        return storage != null && storage.isProtectedByFingerprint();
    }

    public void setNeedsPasscode(boolean value) {
        if (usePasscodeSetting == value) return;
        usePasscodeSetting = value;
        savePrefs();
    }

    public void setNeedsFingerprint(boolean value, Utilities.Callback<Boolean> whenDone) {
        if (storage == null) {
            if (whenDone != null) whenDone.run(false);
            return;
        }
        storage.setProtectedByFingerprint(value, error -> {
            if (whenDone != null) whenDone.run(error == null);
        });
    }

    public void emulateDisableBackup(Utilities.Callback2<Long, String> whenDone) {
        log("emulate disable backup: when ready");
        whenReadyGasless(() -> {
            log("emulate disable backup: ready");
            if (getBalance() <= 0) {
                whenDone.run(500_000L, null);
                return;
            }
//            if (gasless != null && gasless.available && gasless.left > 0) {
//                log("emulate disable backup: we have gasless");
//                whenDone.run(0L, null);
//                return;
//            }
            if (engine == null) {
                err("emulate disable backup: no engine!");
                whenDone.run(null, "NULL_ENGINE");
                return;
            }
            engine.emulateRotateKey((transaction, error) -> {
                if (transaction == null) {
                    err("emulate disable backup, no transaction: " + (error != null ? error : "NULL_ERROR"));
                    whenDone.run(null, error != null ? error : "NULL_ERROR");
                    return;
                }
                whenDone.run(transaction.fee, null);
            });
        });
    }

    public static class PreparedDisableBackup implements AutoCloseable {
        public TL_wallet.sendTransfer transfer;
        public SecretPhrase newSecretPhrase;
        public byte[] newPublicKey;

        @Override public void close() {
            if (newSecretPhrase != null) newSecretPhrase.close();
        }
    }

    public void prepareDisableBackup(Utilities.Callback2<PreparedDisableBackup, String /* error, null if successful */> whenDone) {
        log("prepare disable backup");
        getSecretPhrase(true, false, (secretPhrase, error) -> {
            if (secretPhrase == null) {
                err("prepare disable backup, cant get secret phrase: " + (error == null ? "NULL_ERROR" : error));
                whenDone.run(null, error == null ? "NULL_ERROR" : error);
                return;
            }
            final String gasless_address = gasless != null && gasless.available && !TextUtils.isEmpty(gasless.relayer_address) && false ? gasless.relayer_address : null;
            log("prepare disable backup: preparing transfer");
            engine.prepareRotateKey(secretPhrase, gasless_address, (transfer, newSecretPhrase, newPublicKey, error2) -> {
                if (transfer == null || newSecretPhrase == null || newPublicKey == null) {
                    err("prepare disable backup, failed to prepare transfer: " + (error2 == null ? "NULL_ERROR" : error2));
                    whenDone.run(null, error2 == null ? "NULL_ERROR" : error2);
                    return;
                }
                log("prepare disable backup: transfer ready, initing storage with new secret phrase!");
                final PreparedDisableBackup prepared = new PreparedDisableBackup();
                prepared.newPublicKey = newPublicKey;
                prepared.newSecretPhrase = newSecretPhrase.copy();
                prepared.transfer = transfer;
                storage.init(
                    UserConfig.getInstance(currentAccount).getClientUserId(),
                    newPublicKey,
                    newSecretPhrase,
                    error3 -> {
                        if (error3 != null) {
                            prepared.close();
                            err("prepare disable backup, failed to save secret phrase to local storage: " + error3);
                            whenDone.run(null, error3);
                        } else {
                            log("prepare disable backup: done!");
                            whenDone.run(prepared, null);
                        }
                    }
                );
            });
        });
    }
    public void disableBackup(PreparedDisableBackup prepared, Utilities.Callback<String> whenSent) {
        log("disable backup: getting phrase...");
        getSecretPhrase((currentPhrase, error) -> {
            if (currentPhrase == null) {
                err("disable backup: no current phrase " + (error == null ? "NULL_ERROR" : error));
                whenSent.run(error == null ? "NULL_ERROR" : error);
                return;
            }
            log("disable backup: ready!");
            if (engine == null) {
                err("disable backup: no engine");
                whenSent.run("NULL_ENGINE");
                return;
            }
            final String address = getAddress();
            final byte[] publicKey = getPublicKey();
            if (TextUtils.isEmpty(address)) {
                err("disable backup: empty address");
                whenSent.run("NULL_ADDRESS");
                return;
            }
            if (publicKey == null || publicKey.length <= 0) {
                err("disable backup: no public key");
                whenSent.run("NULL_PUBLIC_KEY");
                return;
            }

            final SecretPhrase replacement;
            try {
                replacement = prepared.newSecretPhrase.copy();
            } catch (IllegalStateException e) {
                whenSent.run("CANCELLED");
                return;
            }
            final TransactionsList transactions = getTransactions();
            final TL_wallet.walletTransaction transaction = new TL_wallet.walletTransaction();
            transaction.key_change = true;
            transaction.incoming = false;
            transaction.pending = true;
            transaction.comment = null;
            transaction.comment_encrypted = false;
            transaction.date = getConnectionsManager().getCurrentTime();
            transaction.amount = 0;
            final TL_wallet.walletTransactionPeerAddress peerAddress = new TL_wallet.walletTransactionPeerAddress();
            peerAddress.address = address;
            transaction.peer = peerAddress;

            final Runnable fail = () -> {
                replacement.close();
                log("disable backup: fail!");
                transaction.pending = false;
                transaction.failed = true;
                transactions.updatedTransactions();
                transactions.notifyUpdate();
                transactions.invalidate();
                onWalletPageUpdate();
            };

            transaction.gasless = prepared.transfer.data_gasless != null;
            transaction.random_id = prepared.transfer.random_id;
            transaction.gaslessMessageBodyHash = prepared.transfer.gaslessMessageBodyHash;
            transaction.normalMessageBodyHash = prepared.transfer.normalMessageBodyHash;
            pendingTransactions.add(0, transaction);
            transactions.updatedTransactions();
            transactions.notifyUpdate();
            onWalletPageUpdate();
            if (whenSent != null) {
                whenSent.run(null);
            }
            log("disable backup: prepared sendTransfer, sending");
            getConnectionsManager().sendRequestTyped(prepared.transfer, AndroidUtilities::runOnUIThread, (updates, error3) -> {
                final TL_update.TL_updateSentWalletTransaction sentTransfer = getSentTransaction(updates);
                if (updates != null) processTransferUpdates(updates);
                if (sentTransfer == null || !isValidMessageHash(sentTransfer.msg_hash)) {
                    fail.run();
                    err("disable backup: sendTransfer: " + (error3 != null && error3.text != null ? error3.text : "NULL_ERROR"));
                    return;
                }
                log("disable backup: transfer is sent, msg_hash=" + sentTransfer.msg_hash + ", gasless=" + sentTransfer.gasless);
                final PendingDisableBackup pending = new PendingDisableBackup();
                pending.msg_hash = sentTransfer.msg_hash;
                pending.publicKey = prepared.newPublicKey;
                pending.secretPhrase = replacement;
//                pending.publicKey = publicKey;
//                pending.secretPhrase = currentPhrase;
                if (sentTransfer.transaction != null) {
                    pending.disableBackup();
                } else {
                    pendingDisableBackups.add(pending);
                }
                applySentTransaction(transaction, sentTransfer);
            });
        });
    }
    public void disableBackupWithoutUpdatingPhrase(Utilities.Callback<String> whenDone) {
        log("disableBackupWithoutUpdatingPhrase");
        makeSureSecretPhraseBackedUpLocally(true, false, (error) -> {
            if (error != null) {
                err("disableBackupWithoutUpdatingPhrase: " + error);
                whenDone.run(error);
                return;
            }

            log("sending disableBackup to server");
            PasswordRequest.open(currentAccount, password -> {
                final TL_wallet.disableBackup req = new TL_wallet.disableBackup();
                req.password = password;
                return req;
            }, (state, error2, password) -> {
                if (error2 != null) {
                    err("failed to disable backup: " + (error2.text != null ? error2.text : "NULL_ERROR"));
                    whenDone.run(error2.text == null ? "NULL_ERROR" : error2.text);
                    return;
                }
                updateState(state);
                whenDone.run(null);
            }, true, true);
        });
    }

    private static final int MAX_MNEMONIC_BACKUP_SIZE = 215;

    private static byte[] padMnemonicBackup(byte[] phrase) {
        final byte[] padded = new byte[MAX_MNEMONIC_BACKUP_SIZE];
        Arrays.fill(padded, (byte) ' ');
        int offset = 0;
        boolean separator = false;
        try {
            // Join words with a single ASCII space without creating an immutable secret String.
            for (byte value : phrase) {
                if (SecretPhrase.isSeparator(value)) {
                    separator = offset > 0;
                    continue;
                }
                if (separator) {
                    if (offset == padded.length) throw new IllegalArgumentException("MNEMONIC_BACKUP_TOO_LONG");
                    padded[offset++] = ' ';
                    separator = false;
                }
                if (offset == padded.length) throw new IllegalArgumentException("MNEMONIC_BACKUP_TOO_LONG");
                padded[offset++] = value;
            }
            return padded;
        } catch (RuntimeException e) {
            Arrays.fill(padded, (byte) 0);
            throw e;
        }
    }

    private static byte[][] splitMnemonicBackup(byte[] padded, SecureRandom random) {
        if (padded.length != MAX_MNEMONIC_BACKUP_SIZE) {
            throw new IllegalArgumentException("INVALID_MNEMONIC_BACKUP_SIZE");
        }
        final byte[][] shares = new byte[3][MAX_MNEMONIC_BACKUP_SIZE];
        try {
            random.nextBytes(shares[0]);
            random.nextBytes(shares[1]);
            for (int i = 0; i < padded.length; ++i) {
                shares[2][i] = (byte) (padded[i] ^ shares[0][i] ^ shares[1][i]);
            }
            return shares;
        } catch (RuntimeException e) {
            for (byte[] share : shares) Arrays.fill(share, (byte) 0);
            throw e;
        }
    }

    public void enableBackup(Utilities.Callback<String> whenDone) {
        log("enable backup: when ready...");
        whenReady(() -> {
            log("enable backup: ready! getting secret phrase...");
            getSecretPhrase((borrowed, error3) -> {
                if (borrowed == null) {
                    err("enable backup: didnt get secret phrase: " + (error3 == null ? "NULL_ERROR" : error3));
                    whenDone.run(error3 == null ? "NULL_ERROR" : error3);
                    return;
                }
                final SecretPhrase phrase = borrowed.copy();
                final Utilities.Callback<String> finish = error -> {
                    phrase.close();
                    whenDone.run(error);
                };
                log("enable backup: got secret phrase");
                final byte[] public_key;
                try {
                    public_key = WalletEngine2.secretPhraseToPublicKey(phrase);
                } catch (Exception e) {
                    err("failed to get public key from secret phrase", e);
                    finish.run(e == null || e.getMessage() == null ? "NULL_ERROR" : e.getMessage());
                    return;
                }

                getConnectionsManager().sendRequestTyped(new TL_wallet.getBackupHolderDcs(), AndroidUtilities::runOnUIThread, (holders, error) -> {
                    if (holders == null) {
                        err("enable backup: didnt get backup holders: " + (error == null || error.text == null ? "NULL_ERROR" : error.text));
                        finish.run(error == null || error.text == null ? "NULL_ERROR" : error.text);
                        return;
                    }

                    final int holdersCount = holders.objects.size();
                    if (holdersCount != 3) {
                        err("enable backup: expected three backup holders");
                        finish.run("INVALID_BACKUP_HOLDERS_COUNT");
                        return;
                    }

                    final byte[][] parts = new byte[holdersCount][];
                    byte[] phraseBytes = null, secret = null;
                    byte[][] shares = null;
                    try {
                        phraseBytes = phrase.copyBytes();
                        secret = padMnemonicBackup(phraseBytes);
                        shares = splitMnemonicBackup(secret, new SecureRandom());
                        if (BuildVars.LOGS_ENABLED) {
                            final byte[] reconstructed = new byte[secret.length];
                            try {
                                for (byte[] share : shares) {
                                    for (int i = 0; i < share.length; ++i) {
                                        reconstructed[i] ^= share[i];
                                    }
                                }
                                log("enable backup: diagnostics sharesReconstructPaddedMnemonic=" + Arrays.equals(secret, reconstructed));
                            } finally {
                                Arrays.fill(reconstructed, (byte) 0);
                            }
                        }

                        for (int i = 0; i < holdersCount; ++i) {
                            final TL_wallet.holderDc holder = holders.objects.get(i);
                            Long privateKey = null, holderKey = null, sharedKey = null;
                            final SerializedData serialized = new SerializedData();
                            byte[] partBytes = null;
                            try {
                                final TL_wallet.mnemonic_decryptedKeyPart part = new TL_wallet.mnemonic_decryptedKeyPart();
                                part.share = shares[i];
                                part.serializeToStream(serialized);
                                partBytes = serialized.toByteArray();
                                privateKey = ConferenceCall.key_generate_temporary_private_key();
                                final byte[] publicKey = ConferenceCall.key_to_public_key(privateKey);
                                holderKey = ConferenceCall.key_from_public_key(holder.public_key);
                                sharedKey = ConferenceCall.key_from_ecdh(privateKey, holderKey);
                                final byte[] encrypted = ConferenceCall.encrypt_message_for_one(sharedKey, partBytes);
                                final byte[] envelope = new byte[32 + encrypted.length];
                                System.arraycopy(publicKey, 0, envelope, 0, 32);
                                System.arraycopy(encrypted, 0, envelope, 32, encrypted.length);
                                parts[i] = envelope;
                            } finally {
                                if (partBytes != null) Arrays.fill(partBytes, (byte) 0);
                                serialized.cleanup();
                                for (Long key : new Long[] {sharedKey, holderKey, privateKey}) {
                                    if (key != null) {
                                        try {
                                            ConferenceCall.key_destroy(key);
                                        } catch (Exception ignore) {
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        err("enable backup", e);
                        finish.run(e.getMessage() == null ? "NULL_ERROR" : e.getMessage());
                        return;
                    } finally {
                        if (phraseBytes != null) Arrays.fill(phraseBytes, (byte) 0);
                        if (secret != null) Arrays.fill(secret, (byte) 0);
                        if (shares != null) for (byte[] share : shares) Arrays.fill(share, (byte) 0);
                    }
                    for (int i = 0; i < parts.length; ++i) {
                        if (parts[i] == null) {
                            err("part " + i + " is null");
                            finish.run("PART_" + i + "_NULL");
                            return;
                        }
                    }

                    final TL_wallet.enableBackup request = new TL_wallet.enableBackup();
                    request.parts = new ArrayList<>(Arrays.asList(parts));
                    request.new_public_key = public_key;
                    proveChallenge(phrase, (proof, error4) -> {
                        if (proof == null) {
                            finish.run(error4 == null ? "NULL_ERROR" : error4);
                            return;
                        }

                        request.proof = new TL_wallet.walletOwnershipProof();
                        request.proof.timestamp = proof.timestamp;
                        request.proof.signature = proof.signature;
                        getConnectionsManager().sendRequestTyped(request, AndroidUtilities::runOnUIThread, (state, error5) -> {
                            if (error5 != null) {
                                err("enable backup: server rejected request code=" + error5.code + " error=" + error5.text);
                                finish.run(error5.text == null ? "NULL_ERROR" : error5.text);
                                return;
                            }
                            log("enable backup: server accepted request");
                            updateState(state);
                            finish.run(null);
                        });
                    });
//                    PasswordRequest.open(currentAccount, password -> {
//                        request.password = password;
//                        if (BuildVars.LOGS_ENABLED) {
//                            log("enable backup: diagnostics sending parts=" + request.parts.size()
//                                + " requestBytes=" + request.getObjectSize() + " passwordPresent=" + (password != null));
//                        }
//                        return request;
//                    }, (state, error4, password) -> {
//                        if (error4 != null) {
//                            err("enable backup: server rejected request code=" + error4.code + " error=" + error4.text);
//                            finish.run(error4.text == null ? "NULL_ERROR" : error4.text);
//                            return;
//                        }
//                        log("enable backup: server accepted request");
//                        updateState(state);
//                        finish.run(null);
//                    });
                });
            });
        });
    }

    public Runnable emulateSend(String address, long amount, String comment, byte[] comment_recipient_public_key, Utilities.Callback2<TL_wallet.walletTransaction, String> whenDone) {
        class EmulationRequest implements Runnable {
            private volatile boolean cancelled;
            private Runnable cancelEmulation;

            @Override
            public synchronized void run() {
                if (cancelled) return;
                cancelEmulation = engine.emulateSend(address, amount, comment, comment_recipient_public_key, (transaction, error) -> {
                    if (!cancelled) whenDone.run(transaction, error);
                });
            }

            public synchronized void cancel() {
                if (cancelled) return;
                cancelled = true;
                if (cancelEmulation != null) {
                    cancelEmulation.run();
                    cancelEmulation = null;
                }
            }
        }
        final EmulationRequest request = new EmulationRequest();
        whenReady(request);
        return request::cancel;
    }

    public Runnable emulateSendNFT(String address, TL_wallet.nftItem collectible, Utilities.Callback2<TL_wallet.walletTransaction, String> whenDone) {
        return emulateSendNFT(address, collectible, null, whenDone);
    }

    public Runnable emulateSendNFT(String address, TL_wallet.nftItem collectible, String comment, Utilities.Callback2<TL_wallet.walletTransaction, String> whenDone) {
        class EmulationRequest implements Runnable {
            private volatile boolean cancelled;
            private Runnable cancelEmulation;

            @Override
            public synchronized void run() {
                if (cancelled) return;
                cancelEmulation = engine.emulateSendNFT(address, collectible.address, comment, (transaction, error) -> {
                    if (transaction != null) {
                        transaction.nft = collectible;
                        transaction.comment = comment;
                        transaction.date = getConnectionsManager().getCurrentTime();
                    }
                    if (!cancelled) whenDone.run(transaction, error);
                });
            }

            public synchronized void cancel() {
                if (cancelled) return;
                cancelled = true;
                if (cancelEmulation != null) {
                    cancelEmulation.run();
                    cancelEmulation = null;
                }
            }
        }
        final EmulationRequest request = new EmulationRequest();
        whenReady(request);
        return request::cancel;
    }

    public void send(String address, long amount, String comment, Utilities.Callback<String> whenSent) {
        send(address, amount, comment, null, whenSent);
    }

    public void send(String address, long amount, String comment, byte[] comment_recipient_public_key, Utilities.Callback<String> whenSent) {
        send(null, address, amount, comment, comment_recipient_public_key, whenSent);
    }

    public void send(TLRPC.User user, String address, long amount, Utilities.Callback<String> whenSent) {
        send(user, address, amount, null, null, whenSent);
    }

    public void send(TLRPC.User user, String address, long amount, String comment, Utilities.Callback<String> whenSent) {
        send(user, address, amount, comment, null, whenSent);
    }

    public void send(TLRPC.User user, String address, long amount, String comment, byte[] comment_recipient_public_key, Utilities.Callback<String> whenSent) {
        send(user, address, amount, comment, comment_recipient_public_key, null, null, whenSent);
    }

    public void send(TLRPC.User user, String address, long amount, String comment, byte[] commentPublicKey,
                     String domain, Utilities.Callback<String> whenSent) {
        send(user, address, amount, comment, commentPublicKey, null, domain, whenSent);
    }

    public void send(TLRPC.User user, String address, long amount, String comment, byte[] commentPublicKey,
                     String domain, Utilities.Callback<String> whenSent,
                     Utilities.Callback<TL_wallet.walletTransaction> whenQueued) {
        send(user, address, amount, comment, commentPublicKey, null, domain, whenSent, whenQueued);
    }

    static TL_wallet.WalletTransactionPeer createRecipientPeer(TLRPC.User user, String address, String domain) {
        final TL_wallet.WalletTransactionPeer peer;
        if (user != null) {
            peer = new TL_wallet.walletTransactionPeerUser();
            peer.user_id = user.id;
        } else {
            peer = new TL_wallet.walletTransactionPeerAddress();
        }
        peer.address = address;
        peer.domain = TextUtils.isEmpty(domain) || "null".equals(domain) ? null : domain;
        return peer;
    }

    static TL_wallet.WalletTransactionPeer mergeRecipientPeer(TL_wallet.WalletTransactionPeer local,
                                                               TL_wallet.WalletTransactionPeer remote) {
        if (remote != null && local != null && addressEquals(local.address, remote.address)) {
            if (TextUtils.isEmpty(remote.domain) || "null".equals(remote.domain)) remote.domain = local.domain;
            if (remote instanceof TL_wallet.walletTransactionPeerAddress && local instanceof TL_wallet.walletTransactionPeerUser) {
                local.domain = remote.domain;
                return local;
            }
        }
        return remote;
    }

    public void sendNFT(String address, TL_wallet.nftItem collectible, Utilities.Callback<String> whenSent) {
        sendNFT(null, address, collectible, whenSent);
    }

    public void sendNFT(TLRPC.User user, String address, TL_wallet.nftItem collectible, Utilities.Callback<String> whenSent) {
        sendNFT(user, address, collectible, null, whenSent);
    }

    public void sendNFT(TLRPC.User user, String address, TL_wallet.nftItem collectible, String comment, Utilities.Callback<String> whenSent) {
        send(user, address, 0, comment, null, collectible, null, whenSent);
    }

    private void send(TLRPC.User user, String address, long amount, String comment, byte[] comment_recipient_public_key, TL_wallet.nftItem collectible, String domain, Utilities.Callback<String> whenSent) {
        send(user, address, amount, comment, comment_recipient_public_key, collectible, domain, whenSent, null);
    }

    private void send(TLRPC.User user, String address, long amount, String comment, byte[] comment_recipient_public_key,
                      TL_wallet.nftItem collectible, String domain, Utilities.Callback<String> whenSent,
                      Utilities.Callback<TL_wallet.walletTransaction> whenQueued) {
        final String sourceAddress = getAddress();
        log("send to " + address + ", amount " + amount + ", when ready...");
        whenReady(() -> {
            final Runnable runnable = () -> {
                log("send " + address + ": ready, get secret phrase");
                getSecretPhrase((phrase, error) -> {
                    if (error != null) {
                        if (whenSent != null) whenSent.run(error);
                        else BulletinFactory.showError(error);
                        return;
                    }
                    if (phrase == null) {
                        if (whenSent != null) whenSent.run("NULL_ERROR");
                        else BulletinFactory.showError("NULL_ERROR");
                        return;
                    }
                    if (engine == null) {
                        if (whenSent != null) whenSent.run("NULL_ENGINE");
                        else BulletinFactory.showError("NULL_ENGINE");
                        return;
                    }

                    if (collectible != null && !addressEquals(sourceAddress, getAddress())) {
                        if (whenSent != null) whenSent.run("WALLET_CHANGED");
                        return;
                    }
                    final TransactionsList transactions = getTransactions();
                    final TL_wallet.walletTransaction transaction = new TL_wallet.walletTransaction();
                    transaction.incoming = false;
                    transaction.pending = true;
                    transaction.comment = comment;
                    transaction.comment_encrypted = false;
                    transaction.comment_encrypted_preparing = collectible == null
                            && comment_recipient_public_key != null && !TextUtils.isEmpty(comment);
                    transaction.date = getConnectionsManager().getCurrentTime();
                    transaction.amount = amount;
                    transaction.nft = collectible;
                    transaction.peer = createRecipientPeer(user, address, domain);

                    final Runnable fail = () -> {
                        if (!transaction.pending && !transaction.failed && !TextUtils.isEmpty(transaction.id)) return;
                        transaction.pending = false;
                        transaction.failed = true;
                        if (collectible != null && nfts != null) nfts.onTransferUpdated(transaction);
                        transactions.updatedTransactions();
                        transactions.notifyUpdate();
                        transactions.invalidate();
                        onWalletPageUpdate();
                    };
                    final long random_id = Utilities.random.nextLong();
                    transaction.random_id = random_id;
                    pendingTransactions.add(0, transaction);
                    if (collectible != null) getCollectibles().onTransferQueued(transaction);
                    transactions.updatedTransactions();
                    transactions.notifyUpdate();
                    onWalletPageUpdate();

                    final SendMessagesHelper sendMessages = SendMessagesHelper.getInstance(currentAccount);
                    final MessageObject localMessage = user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && collectible == null
                            ? sendMessages.createSendingGramTransfer(user, address, amount, transaction.comment, transaction.comment_encrypted, transaction.comment_encrypted_preparing, random_id)
                            : null;
                    // Normal chat history does not restore random_id from storage.
                    transaction.localMessageId = localMessage == null ? 0 : localMessage.getId();

                    if (whenQueued != null) whenQueued.run(transaction);
                    if (whenSent != null) {
                        whenSent.run(null);
                    }

                    final String relayer_address = gasless == null || TextUtils.isEmpty(gasless.relayer_address) || !gasless.available ? null : gasless.relayer_address;

                    log("prepare sending");
                    final Utilities.Callback3<TL_wallet.sendTransfer, String, String> onPrepared = (sendTransfer, encrypted_comment, error2) -> {
                        if (sendTransfer == null) {
                            fail.run();
                            sendMessages.completeSendingGramTransfer(localMessage, null, false);
                            err("failed sending: " + (error2 != null ? error2 : "NULL_ERROR"));
                            return;
                        }
                        if (encrypted_comment != null) {
                            transaction.comment = encrypted_comment;
                            transaction.comment_encrypted = true;
                        }
                        if (addressEquals(getAddress(), address)) {
                            sendTransfer.data_gasless = null;
                        }
                        transaction.comment_encrypted_preparing = false;
                        sendMessages.updateSendingGramTransferComment(localMessage, transaction.comment, transaction.comment_encrypted);
                        transaction.gasless = sendTransfer.data_gasless != null;
                        transaction.gaslessMessageBodyHash = sendTransfer.gaslessMessageBodyHash;
                        transaction.normalMessageBodyHash = sendTransfer.normalMessageBodyHash;
                        transactions.updatedTransactions();
                        transactions.notifyUpdate();
                        sendTransfer.random_id = random_id;
                        sendTransfer.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
                        onWalletPageUpdate();
                        log("prepared sendTransfer, sending");
                        getConnectionsManager().sendRequestTyped(sendTransfer, AndroidUtilities::runOnUIThread, (updates, error3) -> {
                            final TL_update.TL_updateSentWalletTransaction sentTransfer = getSentTransaction(updates);
                            final boolean success = error3 == null && sentTransfer != null && isValidMessageHash(sentTransfer.msg_hash);
                            final boolean confirmed = !transaction.pending && !transaction.failed && !TextUtils.isEmpty(transaction.id);
                            sendMessages.completeSendingGramTransfer(localMessage, updates, success || confirmed);
                            if (updates != null) processTransferUpdates(updates);
                            if (!success) {
                                fail.run();
                                err("sendTransfer: " + (error3 != null && error3.text != null ? error3.text : "NULL_ERROR"));
                                return;
                            }

                            log("transfer is sent, msg_hash=" + sentTransfer.msg_hash + ", gasless=" + sentTransfer.gasless);

                            applySentTransaction(transaction, sentTransfer);
                        });
                    };
                    if (collectible != null) {
                        engine.prepareSendNFT(phrase, address, collectible.address, comment,
                                (transfer, prepareError) -> onPrepared.run(transfer, null, prepareError));
                    } else {
                        engine.prepareSend(phrase, address, amount, comment, comment_recipient_public_key, relayer_address, onPrepared);
                    }
                });
            };
            if (needsPasscode() && !needsFingerprint()) {
                log("send " + address + ": asking passcode");
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null) return;
                final BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                bottomSheetParams.transitionFromLeft = true;
                bottomSheetParams.allowNestedScroll = false;
                final boolean[] entered = new boolean[]{false};
                final PasscodeActivity fragment = new PasscodeActivity(PasscodeActivity.TYPE_ENTER_CODE_TO_GRAM_WALLET) {
                    @Override
                    public void onFragmentDestroy() {
                        super.onFragmentDestroy();
                        if (!entered[0] && whenSent != null) {
                            whenSent.run("PASSCODE_FAILED");
                        }
                    }
                };
                fragment.setOnEnteredCode(() -> {
                    if (entered[0]) return;
                    entered[0] = true;
                    runnable.run();
                });
                lastFragment.showAsSheet(fragment, bottomSheetParams);
            } else {
                runnable.run();
            }
        });
    }

    public void decryptTransactionComment(String encryptedComment, String senderAddress, Utilities.Callback2<String /* comment */, String /* error */> whenDone) {
        log("decrypting transaction comment");
        getSecretPhrase(true, false, (phrase, error) -> {
            if (phrase == null) {
                err("decrypting transaction comment, failed to get secret phrase");
                whenDone.run(null, error == null ? "NULL_ERROR" : error);
                return;
            }
            final String comment;
            try {
                comment = WalletEngine2.decryptComment(phrase, senderAddress, encryptedComment);
            } catch (Exception e) {
                err("failed to decrypt comment", e);
                whenDone.run(null, e == null || e.getMessage() == null ? "NULL_ERROR" : e.getMessage());
                return;
            }
            whenDone.run(comment, null);
        });
    }

    public static class PreviousWallet {
        public final String address;
        public int lastUsage;
        public long balance = -1;
        public byte[] publicKey;
        public boolean loading = true;
        public boolean outdated = false;

        private final GramWalletStorage storage;

        private PreviousWallet(GramWalletStorage storage) {
            this.storage = storage;
            address = storage.getAddress();
        }
    }

    private final ArrayList<PreviousWallet> previousWallets = new ArrayList<>();
    private final List<PreviousWallet> previousWalletsView = Collections.unmodifiableList(previousWallets);
    private String previousWalletAddress;
    private final Runnable onLocalWalletsChanged = () -> {
        refreshPreviousWallets();
        notifyUpdate();
    };

    public List<PreviousWallet> getPreviousWallets() {
        return previousWalletsView;
    }

    public long getBalanceOfPreviousWallets() {
        long balance = 0;
        for (PreviousWallet wallet : previousWallets) {
            if (!wallet.outdated && wallet.balance >= 0) balance += wallet.balance;
        }
        return balance;
    }

    private void refreshPreviousWallets() {
        previousWalletAddress = getAddress();
        final ArrayList<String> addresses = GramWalletStorage.getWallets(ApplicationLoader.applicationContext,
                UserConfig.getInstance(currentAccount).getClientUserId());
        addresses.remove(previousWalletAddress);
        final HashMap<String, PreviousWallet> existing = new HashMap<>();
        for (PreviousWallet wallet : previousWallets) existing.put(wallet.address, wallet);
        previousWallets.clear();
        for (String address : addresses) {
            PreviousWallet wallet = existing.get(address);
            if (wallet == null) {
                wallet = new PreviousWallet(new GramWalletStorage(ApplicationLoader.applicationContext, currentAccount, address));
            }
            wallet.lastUsage = wallet.storage.getLastUsage();
            previousWallets.add(wallet);
            getAddressBalanceAndPublicKey(address);
            updatePreviousWallet(cachedAddressInfos.get(address));
        }
        Collections.sort(previousWallets, (a, b) -> Integer.compare(b.lastUsage, a.lastUsage));
    }

    private void updatePreviousWallet(AddressInfo info) {
        if (info == null) return;
        for (PreviousWallet wallet : previousWallets) {
            if (!TextUtils.equals(wallet.address, info.address)) continue;
            wallet.balance = info.balance;
            wallet.publicKey = info.publicKey;
            wallet.loading = !info.done;
            wallet.outdated = info.publicKey != null && info.publicKey.length > 0 && !wallet.storage.containsSecretPhrase(info.publicKey);
        }
    }

    /** The phrase is valid only during received.run(); copy it before retaining it. */
    public void getSecretPhrase(Utilities.Callback2<SecretPhrase /* secretPhrase */, String /* error */> received) {
        getSecretPhrase(false, true, received);
    }

    public void getSecretPhrase(boolean sheet, boolean progress, Utilities.Callback2<SecretPhrase /* secretPhrase */, String /* error */> received) {
        log("getSecretPhrase: whenReady...");
        whenReady(() -> {
            log("getSecretPhrase: ready!");
            final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
            if (storage.containsSecretPhrase(state.public_key)) {
                log("getSecretPhrase: device has secured phrase, requesting");
                storage.getSecretPhrase(state.public_key, secretPhrase -> received.run(secretPhrase, secretPhrase == null ? "LOCAL_STORAGE_ERROR" : null));
                return;
            }
            if (!state.backup_enabled) {
                err("getSecretPhrase: no server backup!");
                received.run(null, "NO_LOCAL_BACKUP");
                return;
            }
            log("getSecretPhrase: exportSecretPhrase through password request");
            PasswordRequest.open(currentAccount, password -> {
                final TL_wallet.exportSecretPhrase req = new TL_wallet.exportSecretPhrase();
                req.password = password;
                return req;
            }, (secretParts, error, password) -> {
                if (secretParts == null) {
                    err("getSecretPhrase: no secret parts: " + (error != null && error.text != null ? error.text : "NULL_ERROR"));
                    received.run(null, error != null && error.text != null ? error.text : "NULL_ERROR");
                    return;
                }
                fetchSecretPhrase(secretParts, (phrase, error2) -> received.run(phrase, error2));
            }, sheet, progress);
        });
    }

    private void fetchSecretPhrase(TL_wallet.secretPhraseParts secretPhraseParts, Utilities.Callback2<SecretPhrase /* secretPhrase */, String /* error */> received) {
        final ArrayList<Integer> dcs = secretPhraseParts.dcs;
        if (dcs.size() <= 0) {
            err("getSecretPhrase: no secret parts, zero dcs");
            received.run(null, "WALLET_SECRET_PARTS_EMPTY");
            return;
        }

        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(500);

        final long private_key = ConferenceCall.key_generate_temporary_private_key();
        final byte[] public_key = ConferenceCall.key_to_public_key(private_key);

        final boolean[] loaded = new boolean[dcs.size()];
        final TL_wallet.encryptedSecretPhrasePart[] parts = new TL_wallet.encryptedSecretPhrasePart[dcs.size()];
        final TLRPC.TL_error[] errors = new TLRPC.TL_error[dcs.size()];

        for (int i = 0; i < dcs.size(); ++i) {
            final int index = i;
            final int dc = dcs.get(i);
            log("getSecretPhrase: requesting " + index + " part from dc" + dc);
            final TL_wallet.fetchEncryptedSecretPhrasePart request = new TL_wallet.fetchEncryptedSecretPhrasePart();
            request.token = secretPhraseParts.token;
            request.public_key = public_key;
            getConnectionsManager().sendRequestTyped(request, AndroidUtilities::runOnUIThread, (part, err) -> {
                loaded[index] = true;
                parts[index] = part;
                errors[index] = err;

                log("getSecretPhrase: received " + index + " part from dc" + dc);

                boolean allLoaded = true;
                for (int j = 0; j < loaded.length; ++j)
                    if (!loaded[j])
                        allLoaded = false;
                if (!allLoaded) return;

                log("getSecretPhrase: received all parts");
                completeFetchingSecretPhrase(private_key, parts, errors, received, progressDialog);
            }, dc, 0);
        }
    }

    private void completeFetchingSecretPhrase(long private_key, TL_wallet.encryptedSecretPhrasePart[] parts, TLRPC.TL_error[] errors, Utilities.Callback2<SecretPhrase /* secretPhrase */, String /* error */> received, AlertDialog progressDialog) {
        final Utilities.Callback2<SecretPhrase /* secretPhrase */, String /* error */> done = (phrase, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                SecretPhrase.deliver(phrase, value -> {
                    progressDialog.dismiss();
                    if (phrase != null && !phrase.isEmpty() && state instanceof TL_wallet.TL_walletState && storage != null && !storage.containsSecretPhrase(((TL_wallet.TL_walletState) state).public_key)) {
                        final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
                        storage.init(UserConfig.getInstance(currentAccount).getClientUserId(), state.public_key, phrase, null);
                    }
                    received.run(value, error);
                });
            });
        };
        for (int i = 0; i < parts.length; ++i) {
            if (parts[i] == null) {
                final TLRPC.TL_error error = errors[i];
                err("getSecretPhrase: part " + i + ": " + (errors != null && error.text != null ? error.text : "NULL_ERROR"));
                done.run(null, errors != null && error.text != null ? error.text : "NULL_ERROR");
                return;
            } else if (parts[i].data.length < 33) {
                err("getSecretPhrase: part " + i + " length < 33 (len=" + parts[i].data.length + ")");
                done.run(null, "WALLET_PART_" + i + "_INVALID");
                return;
            }
        }

        Utilities.stageQueue.postRunnable(() -> {
            SecretPhrase phrase = null;
            byte[] seed = null;
            try {
                for (int i = 0; i < parts.length; ++i) {
                    final int index = i;
                    final byte[] partFirst = Arrays.copyOfRange(parts[i].data, 0, 32);
                    final byte[] partSecond = Arrays.copyOfRange(parts[i].data, 32, parts[i].data.length);
                    final long pubId = ConferenceCall.key_from_public_key(partFirst);
                    final long symId = ConferenceCall.key_from_ecdh(private_key, pubId);
                    final byte[] plain = ConferenceCall.decrypt_message_for_one(symId, partSecond);
                    ConferenceCall.key_destroy(pubId);
                    final SerializedData data = new SerializedData(plain);
                    TL_wallet.mnemonic_decryptedKeyPart part = null;
                    try {
                        part = TL_wallet.mnemonic_decryptedKeyPart.TLdeserialize(data, data.readInt32(false), false);
                        if (part == null || part.share == null) {
                            err("getSecretPhrase: couldn't decrypt " + index + " part");
                            done.run(null, "WALLET_PART_" + index + "_CANT_DECRYPT");
                            return;
                        }
                        if (seed == null) {
                            seed = part.share.clone();
                        } else {
                            if (part.share.length != seed.length) {
                                err("getSecretPhrase: part " + index + " has length " + part.share.length + " while other had " + seed.length);
                                done.run(null, "WALLET_PART_SHARE_UNEQUAL_LENGHTS");
                                return;
                            }
                            for (int j = 0; j < seed.length; ++j) seed[j] ^= part.share[j];
                        }
                    } finally {
                        if (part != null && part.share != null) Arrays.fill(part.share, (byte) 0);
                        Arrays.fill(plain, (byte) 0);
                        data.cleanup();
                    }
                }
                if (seed == null) {
                    err("getSecretPhrase: no parts???");
                    done.run(null, "WALLET_NO_PARTS");
                    return;
                }

                phrase = new SecretPhrase(seed);
                Arrays.fill(seed, (byte) 0);
                final byte[] derivedPublicKey = WalletEngine2.secretPhraseToPublicKey(phrase);
                final byte[] serverPublicKey = state instanceof TL_wallet.TL_walletState ? ((TL_wallet.TL_walletState) state).public_key : null;
                final boolean mnemonicMatchesServerKey = derivedPublicKey != null && serverPublicKey != null && Arrays.equals(derivedPublicKey, serverPublicKey);
                if (!mnemonicMatchesServerKey) {
                    err("getSecretPhrase: mnemonic public key doesn't match one from server: server=" + Utilities.bytesToHex(serverPublicKey) + " derived=" + Utilities.bytesToHex(derivedPublicKey));
                    phrase.close();
                    done.run(null, "WALLET_WORDS_DONT_MATCH");
                    return;
                }
            } catch (Throwable e) {
                if (phrase != null) phrase.close();
                final String error = e == null || e.getMessage() == null ? "NULL_ERROR" : e.getMessage();
                done.run(null, error);
                err("getSecretPhrase: encryption error", e);
                return;
            } finally {
                if (seed != null) Arrays.fill(seed, (byte) 0);
            }

            log("getSecretPhrase, received the phrase!");
            done.run(phrase, null);
        });
    }

    public void makeSureSecretPhraseBackedUpLocally(Utilities.Callback<String /* error */> whenDone) {
        makeSureSecretPhraseBackedUpLocally(false, true, whenDone);
    }

    public void makeSureSecretPhraseBackedUpLocally(boolean sheet, boolean progress, Utilities.Callback<String /* error */> whenDone) {
        whenReady(() -> {
            final TL_wallet.TL_walletState state = (TL_wallet.TL_walletState) this.state;
            if (storage != null && TextUtils.equals(storage.getAddress(), state.address) && storage.containsSecretPhrase(state.public_key)) {
                whenDone.run(null);
                return;
            }
            getSecretPhrase(sheet, progress, (phrase, error) -> {
                if (error != null || phrase == null) {
                    whenDone.run(error != null ? error : "LOCAL_STORAGE_ERROR");
                    return;
                }
                if (storage == null) {
                    whenDone.run("STORAGE_NULL");
                    return;
                }
                if (!TextUtils.equals(storage.getAddress(), state.address)) {
                    whenDone.run("STORAGE_CHANGED");
                    return;
                }
                final SecretPhrase result = phrase.copy();
                storage.init(
                    UserConfig.getInstance(currentAccount).getClientUserId(),
                    state.public_key,
                    phrase,
                    error2 -> SecretPhrase.deliver(result, value -> whenDone.run(error2))
                );
            });
        });
    }

    public void createNewWallet(Utilities.Callback<String> whenDone) {
        PasswordRequest.open(currentAccount, password -> {
            final TL_wallet.replaceWallet req = new TL_wallet.replaceWallet();
            req.wallet = new TL_wallet.inputWalletNew();
            req.password = password;
            return req;
        }, (state, error, password) -> {
            if (error != null) {
                whenDone.run(error.text == null ? "NULL_ERROR" : error.text);
                return;
            }
            updateState(state);
            onWalletChanged();
            whenDone.run(null);
        });
    }

    public void importWallet(SecretPhrase phrase, Utilities.Callback<String /* error */> whenDone) {
        importWallet(false, true, phrase, whenDone);
    }

    public void importWallet(boolean sheet, boolean progress, SecretPhrase phrase, Utilities.Callback<String> whenDone) {
        try {
            if (!WalletEngine2.isTgWalletSecretPhrase(phrase)) {
                whenDone.run("WRONG_CONTRACT");
                return;
            }
        } catch (Exception e) {}
        final byte[] public_key, anchor_public_key;
        try {
            public_key = WalletEngine2.secretPhraseToPublicKey(phrase);
            anchor_public_key = WalletEngine2.secretPhraseToAnchorPublicKey(phrase);
        } catch (Exception e) {
            whenDone.run("INVALID_PHRASE");
            return;
        }
        final SecretPhrase owned = phrase.copy();
        log("import wallet: requesting proof challenge");
        PasswordRequest.<TL_wallet.WalletState>open(currentAccount, (password, send) -> {
            proveChallenge(owned, true, (proof, err2) -> {
                if (proof == null) {
                    whenDone.run(err2 == null ? "NULL_ERROR" : err2);
                    send.run(null);
                    return;
                }
                final TL_wallet.replaceWallet req = new TL_wallet.replaceWallet();
                final TL_wallet.inputWalletImported replacement = new TL_wallet.inputWalletImported();
                replacement.public_key = proof.publicKey;
                replacement.anchor_public_key = anchor_public_key;
                replacement.proof = new TL_wallet.walletOwnershipProof();
                replacement.proof.signature = proof.signature;
                replacement.proof.timestamp = proof.timestamp;
                req.wallet = replacement;
                req.password = password;
                send.run(req);
            });
        }, (state, error, password) -> {
            if (error != null && "WALLET_PROOF_EXPIRED".equalsIgnoreCase(error.text)) {
                return;
            } else if (error != null) {
                whenDone.run(error.text == null ? "NULL_ERROR" : error.text);
                return;
            }
            updateState(state);
            // Import completes only after local persistence; propagate storage/authentication failures.
            new GramWalletStorage(getContext(), currentAccount, getAddress())
                .init(UserConfig.getInstance(currentAccount).getClientUserId(), public_key, owned, whenDone);
            onWalletChanged();
        }, sheet, progress, owned::close);
    }

    private void onWalletChanged() {
        clearPendingTransactions();
        gasless = null;
        requestGaslessInfo();
    }

    private void proveChallenge(SecretPhrase phrase, Utilities.Callback2<WalletEngine2.ImportedWalletProof, String /* error */> whenDone) {
        proveChallenge(phrase, false, whenDone);
    }

    private void proveChallenge(SecretPhrase input, boolean forImport, Utilities.Callback2<WalletEngine2.ImportedWalletProof, String /* error */> whenDone) {
        final SecretPhrase phrase = input.copy();
        log("requesting proof challenge");
        getConnectionsManager().sendRequestTyped(new TL_wallet.getProofChallenge(), AndroidUtilities::runOnUIThread, (challenge, err) -> {
            if (challenge == null) {
                phrase.close();
                whenDone.run(null, err == null ? "NULL_ERROR" : err.text);
                return;
            }
            final int now = getConnectionsManager().getCurrentTime();
            log("received proof challenge, expires at " + challenge.expires + ", now = " + now + ", expires in " + (challenge.expires - now) + "s");
            Utilities.stageQueue.postRunnable(() -> {
                WalletEngine2.ImportedWalletProof _proof = null;
                Exception _exception = null;
                try {
                    _proof = forImport
                            ? WalletEngine2.createImportProof(phrase, challenge.domain, challenge.payload, now)
                            : WalletEngine2.createOwnershipProof(phrase, challenge.domain, challenge.payload, now);
                } catch (Exception e) {
                    err("createImportProof", e);
                    _exception = e;
                } finally {
                    phrase.close();
                }
                final WalletEngine2.ImportedWalletProof proof = _proof;
                final Exception exception = _exception;
                AndroidUtilities.runOnUIThread(() -> {
                    if (proof == null) {
                        err("failed to prove: " + (exception == null || exception.getMessage() == null ? "PROOF_ERROR" : exception.getMessage()), exception);
                        whenDone.run(null, exception == null || exception.getMessage() == null ? "PROOF_ERROR" : exception.getMessage());
                    } else {
                        log("proof done");
                        whenDone.run(proof, null);
                    }
                });
            });
        });
    }

    private final HashMap<String, TL_wallet.walletTransaction> transactionsCache = new HashMap<>();
    public void getTransaction(String id, Utilities.Callback<TL_wallet.walletTransaction> whenDone) {
        if (transactions != null) {
            for (TL_wallet.walletTransaction transaction : transactions.allTransactions) {
                if (TextUtils.equals(transaction.id, id)) {
                    whenDone.run(transaction);
                    return;
                }
            }
        }
        if (transactionsCache.get(id) != null) {
            whenDone.run(transactionsCache.get(id));
            return;
        }
        final TL_wallet.getTransactionsByIDs request = new TL_wallet.getTransactionsByIDs();
        request.id.add(id);
        getConnectionsManager().sendRequestTyped(request, AndroidUtilities::runOnUIThread, (transactions, error) -> {
            if (transactions == null || transactions.transactions.isEmpty()) {
                whenDone.run(null);
                return;
            }
            final TL_wallet.walletTransaction transaction = transactions.transactions.get(0);
            transactionsCache.put(id, transaction);
            whenDone.run(transaction);
        });
    }

    private final HashMap<Long, TL_wallet.walletUserAddress> cachedWalletResolvesByUser = new HashMap<>();
    private final HashMap<String, TL_wallet.walletUserAddress> cachedWalletResolvesByAddress = new HashMap<>();
    public void resolveWallet(TLRPC.User user, boolean force, Utilities.Callback2<TL_wallet.walletUserAddress, String /* error */> whenDone) {
        if (user == null) {
            whenDone.run(null, null);
            return;
        }
        if (cachedWalletResolvesByUser.containsKey(user.id)) {
            whenDone.run(cachedWalletResolvesByUser.get(user.id), null);
            return;
        }
        final TL_wallet.getUserAddresses req = new TL_wallet.getUserAddresses();
        req.force = force;
        req.id.add(MessagesController.getInstance(currentAccount).getInputUser(user));
        getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (res == null || res.addresses.size() <= 0) {
                whenDone.run(null, err == null ? null : err.text);
                return;
            }
            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            final TL_wallet.walletUserAddress resolved = res.addresses.get(0);
            if (resolved.user_id == 0 || resolved.user_id != user.id) {
                whenDone.run(null, err == null ? null : err.text);
                return;
            }
            cachedWalletResolvesByUser.put(user.id, resolved);
            cachedWalletResolvesByAddress.put(resolved.address, resolved);
            whenDone.run(resolved, null);
        });
    }
    public void resolveWallet(String address, Utilities.Callback2<TL_wallet.walletUserAddress, String /* error */> whenDone) {
        if (address == null) {
            whenDone.run(null, null);
            return;
        }
        if (cachedWalletResolvesByAddress.containsKey(address)) {
            whenDone.run(cachedWalletResolvesByAddress.get(address), null);
            return;
        }
        final TL_wallet.getUserAddresses req = new TL_wallet.getUserAddresses();
        req.addresses.add(address);
        getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (res == null || res.addresses.size() <= 0) {
                resolveWalletPublicKey(address, publicKey -> {
                    if (publicKey == null) {
                        whenDone.run(null, null);
                        return;
                    }
                    final TL_wallet.walletUserAddress walletAddress = new TL_wallet.walletUserAddress();
                    walletAddress.user_id = 0;
                    walletAddress.address = address;
                    walletAddress.public_key = publicKey;
                    cachedWalletResolvesByAddress.put(walletAddress.address, walletAddress);
                    whenDone.run(walletAddress, null);
                });
                return;
            }
            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            org.telegram.messenger.MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);
            final TL_wallet.walletUserAddress resolved = res.addresses.get(0);
            if (resolved.user_id != 0) {
                cachedWalletResolvesByUser.put(resolved.user_id, resolved);
            }
            cachedWalletResolvesByAddress.put(resolved.address, resolved);
            whenDone.run(resolved, err == null ? null : err.text);
        });
    }

    public static class AddressInfo {
        public String address;
        public long balance = -1;
        public byte[] publicKey;
        public int time;
        public boolean done;
        public ArrayList<Utilities.Callback<AddressInfo>> whenDone = new ArrayList<>();
    }

    private final HashMap<String, AddressInfo> cachedAddressInfos = new HashMap<>();

    public AddressInfo getAddressBalanceAndPublicKey(String address) {
        return getAddressBalanceAndPublicKey(address, null);
    }

    public AddressInfo getAddressBalanceAndPublicKey(String address, Utilities.Callback<AddressInfo> whenDone) {
        AddressInfo _info = cachedAddressInfos.get(address);
        if (_info != null && !_info.done) {
            if (whenDone != null) {
                _info.whenDone.add(whenDone);
            }
            return null;
        }
        if (_info != null && _info.done && now() < _info.time + 5 * 60) {
            if (whenDone != null) {
                whenDone.run(_info);
            }
            return _info;
        }

        final AddressInfo info = new AddressInfo();
        info.address = address;
        info.time = now();
        if (whenDone != null) {
            info.whenDone.add(whenDone);
        }
        cachedAddressInfos.put(address, info);

        Utilities.raceCallbacks(
            () -> {
                if (cachedAddressInfos.get(address) != info) return;
                info.done = true;
                updatePreviousWallet(info);
                for (Utilities.Callback<AddressInfo> callback : info.whenDone)
                    callback.run(info);
                info.whenDone.clear();
                notifyUpdate();
            },
            done -> {
                final TL_toncenter.performApiRequest request = new TL_toncenter.performApiRequest();
                request.endpoint = "/api/v3/walletInformation";
                request.query = "address=" + Uri.encode(address) + "&use_v2=false";
                getConnectionsManager().sendRequestTyped(request, AndroidUtilities::runOnUIThread, (response, error) -> {
                    if (response != null) {
                        try {
                            final JSONObject obj = new JSONObject(response.response.data);
                            final String balanceString = obj.getString("balance");
                            info.balance = Long.parseLong(balanceString);
                            if (cachedAddressInfos.get(address) == info) {
                                updatePreviousWallet(info);
                                notifyUpdate();
                            }
                        } catch (Exception e) {
                            err("failed to get balance of " + address, e);
                        }
                    }
                    done.run();
                }, MessagesController.getInstance(currentAccount).webFileDatacenterId, 0);
            },
            done -> {
                resolveWalletPublicKey(address, publicKey -> {
                    info.publicKey = publicKey;
                    if (cachedAddressInfos.get(address) == info) {
                        updatePreviousWallet(info);
                        notifyUpdate();
                    }
                    done.run();
                });
            }
        );

        return null;
    }
    private void resolveWalletPublicKey(String address, Utilities.Callback<byte[]> whenDone) {
        final TL_toncenter.performApiRequest request = new TL_toncenter.performApiRequest();
        request.post = true;
        request.endpoint = "/api/v2/runGetMethod";
        request.payload = obj("address", address, "method", "get_public_key", "stack", new JSONArray()).toString();
        getConnectionsManager().sendRequestTyped(request, AndroidUtilities::runOnUIThread, (response, error) -> {
            byte[] public_key = null;
            if (response != null) {
                try {
                    final JSONObject obj = new JSONObject(response.response.data);
                    final JSONArray stack;
                    if (obj.has("stack")) {
                        stack = obj.getJSONArray("stack");
                        if (obj.has("exit_code") && obj.getInt("exit_code") != 0) {
                            throw new Exception("received exit code: " + obj.getInt("exit_code"));
                        }
                    } else {
                        final JSONObject result = obj.getJSONObject("result");
                        if (result.has("exit_code") && result.getInt("exit_code") != 0) {
                            throw new Exception("received exit code: " + result.getInt("exit_code"));
                        }
                        stack = result.getJSONArray("stack");
                    }
                    final String valueString;
                    if (stack.get(0) instanceof JSONArray) {
                        valueString = stack.getJSONArray(0).getString(1);
                    } else if (stack.get(0) instanceof JSONObject) {
                        valueString = stack.getJSONObject(0).getString("value");
                    } else {
                        valueString = "";
                    }
                    if (valueString.startsWith("0x")) {
                        public_key = Utilities.hexToBytes(valueString.substring(2));
                    }
                } catch (Exception e) {
                    err("failed to get public key of " + address, e);
                }
            }
            whenDone.run(public_key);
        }, MessagesController.getInstance(currentAccount).webFileDatacenterId, 0);
    }

    public CollectiblesList getCollectibles() {
        if (nfts == null) {
            nfts = new CollectiblesList();
        }
        return nfts;
    }

    public class CollectiblesList {

        public final ArrayList<TL_wallet.nftItem> collectibles = new ArrayList<>();
        private final ArrayList<TL_wallet.nftItem> serverCollectibles = new ArrayList<>();
        private final ArrayList<TL_wallet.walletTransaction> collectibleTransfers = new ArrayList<>();
        private final ArrayList<Runnable> listeners = new ArrayList<>();
        private String address = getAddress();
        private String lastOffset = "";
        private boolean endReached;
        private boolean loaded;
        private boolean loading;
        private boolean refreshing;
        private String error;
        private int reqId = -1;

        private CollectiblesList() {}

        private void setAddress(String address) {
            if (!TextUtils.equals(this.address, address)) {
                this.address = address;
                clear();
            }
        }

        public boolean isLoading() {
            return loading;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public boolean isEndReached() {
            return endReached;
        }

        public String getError() {
            return error;
        }

        public void load() {
            if (loading || endReached) return;
            load(!loaded);
        }

        public void invalidate() {
            if (refreshing) return;
            cancelRequest();
            load(true);
        }

        private void load(boolean refresh) {
            if (TextUtils.isEmpty(address)) return;

            final TL_wallet.getNfts req = new TL_wallet.getNfts();
            req.offset = refresh ? "" : lastOffset;
            req.limit = TextUtils.isEmpty(req.offset) ? 5 : 20;
            loading = true;
            refreshing = refresh;
            error = null;
            notifyUpdate();

            reqId = getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (result, failure) -> {
                reqId = -1;
                loading = refreshing = false;
                if (result != null) {
                    if (refresh) serverCollectibles.clear();
                    for (TL_wallet.nftItem item : result.items) {
                        appendOrReplace(item);
                    }
                    lastOffset = result.next_offset;
                    endReached = TextUtils.isEmpty(lastOffset);
                    loaded = true;
                    // Retire successful sends only after the complete inventory agrees.
                    // Until then, the index may still return the previous owner's NFT.
                    if (endReached) {
                        for (int i = collectibleTransfers.size() - 1; i >= 0; --i) {
                            TL_wallet.walletTransaction transfer = collectibleTransfers.get(i);
                            if (!transfer.pending && !transfer.failed && !containsCollectible(transfer.nft.address)) {
                                collectibleTransfers.remove(i);
                            }
                        }
                    }
                    updateVisibleCollectibles();
                } else {
                    if (refresh) loaded = endReached = false;
                    error = failure == null ? "Could not load collectibles" : failure.text;
                }
                notifyUpdate();
            });
        }

        private void appendOrReplace(TL_wallet.nftItem item) {
            for (int i = 0; i < serverCollectibles.size(); ++i) {
                if (addressEquals(serverCollectibles.get(i).address, item.address)) {
                    serverCollectibles.set(i, item);
                    return;
                }
            }
            serverCollectibles.add(item);
        }

        private boolean containsCollectible(String address) {
            for (TL_wallet.nftItem item : serverCollectibles) {
                if (addressEquals(item.address, address)) return true;
            }
            return false;
        }

        private void onTransferQueued(TL_wallet.walletTransaction transfer) {
            collectibleTransfers.add(transfer);
            updateVisibleCollectibles();
            notifyUpdate();
        }

        private void onTransferUpdated(TL_wallet.walletTransaction transfer) {
            for (TL_wallet.walletTransaction tracked : collectibleTransfers) {
                if (tracked != transfer) continue;
                if (transfer.failed && !containsCollectible(transfer.nft.address)) {
                    serverCollectibles.add(0, transfer.nft);
                }
                updateVisibleCollectibles();
                notifyUpdate();
                return;
            }
        }

        private void updateVisibleCollectibles() {
            collectibles.clear();
            for (TL_wallet.nftItem item : serverCollectibles) {
                boolean sending = false;
                for (TL_wallet.walletTransaction transfer : collectibleTransfers) {
                    if (!transfer.failed && addressEquals(transfer.nft.address, item.address)) {
                        sending = true;
                        break;
                    }
                }
                if (!sending) collectibles.add(item);
            }
        }

        public void cancel() {
            cancelRequest();
            notifyUpdate();
        }

        private void cancelRequest() {
            if (reqId >= 0) {
                getConnectionsManager().cancelRequest(reqId, true);
                reqId = -1;
            }
            if (refreshing) loaded = endReached = false;
            loading = refreshing = false;
        }

        public void clear() {
            cancelRequest();
            collectibles.clear();
            serverCollectibles.clear();
            collectibleTransfers.clear();
            lastOffset = "";
            loaded = endReached = false;
            error = null;
            notifyUpdate();
        }

        public void subscribe(Runnable listener) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }

        public void unsubscribe(Runnable listener) {
            listeners.remove(listener);
        }

        private void notifyUpdate() {
            for (Runnable listener : new ArrayList<>(listeners)) listener.run();
        }
    }

    public TransactionsList getTransactions() {
        if (transactions == null) {
            transactions = new TransactionsList();
        }
        return transactions;
    }

    public class TransactionsList {

        public final TransactionCell.SmartWalletAddressFormatter addressFormatter = new TransactionCell.SmartWalletAddressFormatter();
        public ArrayList<TL_wallet.walletTransaction> transactions = new ArrayList<>();
        public ArrayList<TL_wallet.walletTransaction> allTransactions = new ArrayList<>();
        private final HashSet<String> streamTransactionIds = new HashSet<>();
        private final HashMap<String, String> streamTraces = new HashMap<>(); // transaction id -> trace, until finalized

        private void onStreamEvent(JSONObject event) {
            final String type = event.optString("type");
            if ("trace_invalidated".equals(type)) {
                invalidateStreamTrace(event.optString("trace_external_hash_norm"));
                return;
            }
            if (!"transactions".equals(type)) return;
            final String finality = event.optString("finality");
            final boolean finalized = "finalized".equals(finality);
            if (!finalized && !"confirmed".equals(finality)) return;
            final JSONArray array = event.optJSONArray("transactions");
            if (array == null) return;
            boolean updated = false;
            for (int i = 0; i < array.length(); ++i) {
                final JSONObject transaction = array.optJSONObject(i);
                if (transaction == null || transaction.optBoolean("emulated")
                        || "pending".equals(transaction.optString("finality"))) continue;
                final String id = streamTransactionId(transaction);
                if (id == null) continue;
                final TL_wallet.walletTransaction converted = fromStreamTransaction(transaction, event);
                if (converted == null) continue;
                final boolean matchedSigned = reconcileSignedTransaction(converted);
                final TL_wallet.walletTransaction existing = findTransactionById(transactions, id);
                if (existing != null) {
                    if (matchedSigned) {
                        existing.gasless = converted.gasless;
                        existing.gaslessMessageBodyHash = converted.gaslessMessageBodyHash;
                        existing.normalMessageBodyHash = converted.normalMessageBodyHash;
                        if (converted.nft != null) {
                            existing.nft = converted.nft;
                            existing.peer = converted.peer;
                            existing.amount = converted.amount;
                            existing.comment = converted.comment;
                        }
                        updated = true;
                    }
                    if (finalized) streamTraces.remove(id);
                    continue;
                }
                if (!matchedSigned && matchesPendingTransaction(converted)) {
                    // A pending send placeholder owns this row; the RPC update will complete it.
                    streamTransactionIds.remove(id);
                    streamTraces.remove(id);
                    continue;
                }
                insertStreamTransaction(converted);
                streamTransactionIds.add(id);
                if (finalized) streamTraces.remove(id);
                else streamTraces.put(id, event.optString("trace_external_hash_norm"));
                log("stream inserted " + id);
                updated = true;
                resolveStreamPeer(converted);
            }
            if (updated) {
                updatedTransactions();
                notifyUpdate();
            }
        }

        private String streamTransactionId(JSONObject transaction) {
            final String lt = transaction.optString("lt", "");
            final String hash = transaction.optString("hash", "");
            return TextUtils.isEmpty(lt) || TextUtils.isEmpty(hash) ? null : lt + ":" + hash;
        }

        @Nullable
        private TL_wallet.walletTransaction fromStreamTransaction(JSONObject transaction, JSONObject event) {
            try {
                if (!addressEquals(transaction.getString("account"), getAddress())) return null;
                final JSONObject description = transaction.getJSONObject("description");
                if (description.optBoolean("destroyed")) return null;
                final boolean aborted = description.optBoolean("aborted");
                final JSONObject compute = description.optJSONObject("compute_ph");
                final boolean executed = compute != null && !compute.optBoolean("skipped");
                // When the account executed code, a failed run (non-zero exit) is never a clean transfer.
                if (executed && !(compute.optBoolean("success") && compute.optInt("exit_code") == 0)) return null;

                final String address = getAddress();
                final JSONObject input = transaction.optJSONObject("in_msg");
                final ArrayList<JSONObject> transfers = new ArrayList<>();
                boolean bounced = false;
                final JSONArray outputs = transaction.optJSONArray("out_msgs");
                if (outputs != null) {
                    for (int i = 0; i < outputs.length(); ++i) {
                        final JSONObject output = outputs.optJSONObject(i);
                        if (output == null) continue;
                        if (output.optBoolean("bounced")) { bounced = true; continue; }
                        // External-out messages carry no TON value.
                        if (output.isNull("value")) continue;
                        final long value = Long.parseLong(output.getString("value"));
                        if (value > 0 && !addressEquals(output.getString("destination"), address)) transfers.add(output);
                    }
                }
                final boolean incoming = !bounced
                        && input != null
                        // External-in messages have neither an internal source nor a value.
                        && !input.isNull("source")
                        && !input.isNull("value")
                        && addressEquals(input.getString("destination"), address)
                        && !addressEquals(input.getString("source"), address)
                        && !input.optBoolean("bounced")
                        && Long.parseLong(input.getString("value")) > 0
                        && transfers.isEmpty();
                final boolean outgoing = !incoming && !bounced && !aborted && transfers.size() == 1;
                if (!incoming && !outgoing) return null;
                if (outgoing) {
                    // Only trust a send whose action phase actually succeeded; failed sends
                    // surface through the pending-transaction flow instead.
                    final JSONObject action = description.optJSONObject("action");
                    if (action == null || !action.optBoolean("success") || action.optBoolean("no_funds") || action.optInt("result_code") != 0) return null;
                }
                final JSONObject message = incoming ? input : transfers.get(0);
                final long amount = Long.parseLong(message.getString("value"));
                if (amount < MessagesController.getInstance(currentAccount).config.walletTransferMinNanos.get()) return null;

                final TL_wallet.walletTransaction result = new TL_wallet.walletTransaction();
                result.id = streamTransactionId(transaction);
                if (result.id == null) return null;
                result.tx_hash = transaction.getString("hash");
                result.messageHash = message.optString("hash", null);
                if (outgoing && input != null) {
                    final JSONObject inputContent = input.optJSONObject("message_content");
                    if (inputContent != null) {
                        if (input.isNull("source")) {
                            result.normalMessageBodyHash = inputContent.optString("hash", null);
                        } else {
                            result.gaslessMessageBodyHash = inputContent.optString("hash", null);
                        }
                    }
                }
                result.incoming = incoming;
                result.amount = incoming ? amount : -amount;
                result.date = transaction.getInt("now");
                result.fee = Long.parseLong(transaction.getString("total_fees"));
                result.feeUnknown = false;
                result.failed = false;
                result.pending = false;
                result.phase = WalletEngine2.SendPhase.CONFIRMED;
                result.comment_encrypted = WalletEngine2.isEncryptedComment(message);
                final JSONObject content = message.optJSONObject("message_content");
                final String body = content == null ? null : content.optString("body", null);
                final JSONObject decoded = content == null ? null : content.optJSONObject("decoded");
                if (result.comment_encrypted) {
                    try {
                        result.comment = WalletEngine2.encryptedCommentPayload(body);
                    } catch (Exception e) {
                        result.comment_encrypted = false;
                    }
                } else if (decoded != null && "text_comment".equals(decoded.optString("type"))) {
                    result.comment = decoded.optString("comment", null);
                } else {
                    result.comment = WalletEngine2.textCommentFromBody(body);
                }
                if (result.comment != null && result.comment.isEmpty()) result.comment = null;
                final String peerAddress = incoming ? input.getString("source") : message.getString("destination");
                final JSONObject info = addressBookInfo(event, peerAddress);
                final TL_wallet.walletTransactionPeerAddress peer = new TL_wallet.walletTransactionPeerAddress();
                peer.address = info == null ? rawAddressToFriendly(peerAddress) : info.optString("user_friendly", peerAddress);
                peer.domain = info == null || info.isNull("domain") ? null : info.optString("domain", null);
                if (!addressEquals(peer.address, peerAddress)) return null;
                result.peer = peer;
                return result;
            } catch (Exception e) {
                err("stream transaction parse failed", e);
                return null;
            }
        }

        private JSONObject addressBookInfo(JSONObject event, String address) {
            final JSONObject book = event.optJSONObject("address_book");
            if (book == null || address == null) return null;
            for (java.util.Iterator<String> it = book.keys(); it.hasNext();) {
                final String key = it.next();
                if (addressEquals(key, address)) return book.optJSONObject(key);
            }
            return null;
        }

        private boolean matchesPendingTransaction(TL_wallet.walletTransaction t) {
            for (TL_wallet.walletTransaction pending : pendingTransactions) {
                if (pending.incoming != t.incoming) continue;
                if (!TextUtils.isEmpty(pending.id) && TextUtils.equals(pending.id, t.id)
                        || sameTransactionHash(pending.tx_hash, t.tx_hash)
                        || sameTransactionHash(pending.messageHash, t.messageHash)) return true;
            }
            return false;
        }

        private boolean reconcileSignedTransaction(TL_wallet.walletTransaction remote) {
            if (remote.incoming) return false;
            boolean matched = false;
            for (TL_wallet.walletTransaction pending : new ArrayList<>(pendingTransactions)) {
                final boolean gasless = sameTransactionHash(pending.gaslessMessageBodyHash, remote.gaslessMessageBodyHash);
                final boolean normal = sameTransactionHash(pending.normalMessageBodyHash, remote.normalMessageBodyHash);
                if (pending.incoming || !(gasless || normal)) continue;
                remote.gasless = gasless;
                if (remote.nft == null && pending.nft != null) {
                    // The stream describes the TON message to the NFT contract, not the
                    // collectible transfer. Retain the signed send's display metadata.
                    remote.nft = pending.nft;
                    remote.peer = pending.peer;
                    remote.amount = pending.amount;
                    remote.comment = pending.comment;
                }
                // Update the object retained by the RPC callback too, including after an RPC failure.
                applyConfirmedTransaction(pending, remote);
                pending.pending = false;
                pending.failed = false;
                pending.phase = remote.phase;
                pendingTransactions.remove(pending);
                final GramWalletPolling poller = polling.remove(pending);
                if (poller != null) poller.stop();
                matched = true;
            }
            return matched;
        }

        private void invalidateStreamTrace(String trace) {
            if (TextUtils.isEmpty(trace)) return;
            boolean updated = false;
            for (String id : new ArrayList<>(streamTraces.keySet())) {
                if (!sameTransactionHash(streamTraces.get(id), trace)) continue;
                streamTraces.remove(id);
                streamTransactionIds.remove(id);
                for (int i = transactions.size() - 1; i >= 0; --i) {
                    if (TextUtils.equals(transactions.get(i).id, id)) {
                        transactions.remove(i);
                        updated = true;
                    }
                }
            }
            if (updated) {
                updatedTransactions();
                notifyUpdate();
            }
        }

        private void resolveStreamPeer(TL_wallet.walletTransaction t) {
            if (!(t.peer instanceof TL_wallet.walletTransactionPeerAddress)) return;
            final String address = t.peer.address;
            final String domain = t.peer.domain;
            final String id = t.id;
            resolveWallet(address, (resolved, error) -> {
                if (resolved == null || resolved.user_id == 0) return;
                if (MessagesController.getInstance(currentAccount).getUser(resolved.user_id) == null) return;
                boolean updated = false;
                for (TL_wallet.walletTransaction local : transactions) {
                    if (!TextUtils.equals(local.id, id) || !(local.peer instanceof TL_wallet.walletTransactionPeerAddress)) continue;
                    TL_wallet.walletTransactionPeerUser peer = new TL_wallet.walletTransactionPeerUser();
                    peer.user_id = resolved.user_id;
                    peer.address = address;
                    peer.domain = domain;
                    local.peer = peer;
                    updated = true;
                }
                if (updated) {
                    updatedTransactions();
                    notifyUpdate();
                }
            });
        }

        private void insertStreamTransaction(TL_wallet.walletTransaction t) {
            int index = 0;
            while (index < transactions.size() && transactions.get(index).date >= t.date) ++index;
            transactions.add(index, t);
        }

        private String lastOffset = "";
        private boolean endReached;

        private boolean loadingInvalidation;
        private int invalidationReqId = -1;

        private boolean loading;
        private int reqId = -1;

        private TransactionsList() {}

        public boolean isLoading() {
            return loading;
        }

        public boolean hasEndReached() {
            return endReached;
        }

        public void invalidate() {
            if (loadingInvalidation) return;

            // something happened, we are expecting new transaction
            // fetch first page of transactions, update ones that are already locally
            // if we see a gap, reset the list

            final TL_wallet.getTransactions req = new TL_wallet.getTransactions();
            req.inbound = true;
            req.outbound = true;
            req.limit = 10;
            req.offset = "";

            invalidationReqId = getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (result, error) -> {
                invalidationReqId = -1;
                loadingInvalidation = false;
                boolean updated = false;
                if (result != null) {
                    MessagesController.getInstance(currentAccount).putUsers(result.users, false);
                    MessagesController.getInstance(currentAccount).putChats(result.chats, false);

                    boolean hasTransactionsFromLocal = false;
                    for (TL_wallet.walletTransaction remoteTransaction : result.transactions) {
                        if (findTransactionById(this.transactions, remoteTransaction.id) != null) {
                            hasTransactionsFromLocal = true;
                            break;
                        }
                    }
                    if (!hasTransactionsFromLocal) {
                        updated = true;
                        final boolean wasLoading = loading;
                        if (loading) cancel();
                        final ArrayList<TL_wallet.walletTransaction> streamed = new ArrayList<>();
                        for (TL_wallet.walletTransaction local : this.transactions) {
                            if (streamTransactionIds.contains(local.id)) {
                                streamed.add(local);
                            }
                        }
                        for (TL_wallet.walletTransaction remote : result.transactions) {
                            streamTransactionIds.remove(remote.id);
                            streamTraces.remove(remote.id);
                        }
                        this.transactions.clear();
                        this.transactions.addAll(result.transactions);
                        // The wallet index can lag behind the stream; retain unmatched live transactions.
                        for (TL_wallet.walletTransaction entry : streamed) {
                            if (streamTransactionIds.contains(entry.id)) {
                                insertStreamTransaction(entry);
                            }
                        }
                        lastOffset = result.next_offset;
                        endReached = result.transactions.size() < req.limit;
                        if (wasLoading) load();
                    } else {
                        for (int j = result.transactions.size() - 1; j >= 0; --j) {
                            final TL_wallet.walletTransaction remoteTransaction = result.transactions.get(j);
                            streamTransactionIds.remove(remoteTransaction.id);
                            streamTraces.remove(remoteTransaction.id);
                            final TL_wallet.walletTransaction localTransaction = findTransactionById(this.transactions, remoteTransaction.id);
                            if (localTransaction != null) {
                                if (localTransaction.set(remoteTransaction)) {
                                    updated = true;
                                }
                            } else {
                                transactions.add(0, remoteTransaction);
                                updated = true;
                            }
                        }
                    }
                }
                if (updated) {
                    updatedTransactions();
                    notifyUpdate();
                }
            });
        }

        private void updatedTransactions() {
            for (int i = pendingTransactions.size() - 1; i >= 0; --i) {
                final TL_wallet.walletTransaction pending = pendingTransactions.get(i);
                if (TextUtils.isEmpty(pending.id) && TextUtils.isEmpty(pending.tx_hash) && TextUtils.isEmpty(pending.messageHash)) continue;
                for (TL_wallet.walletTransaction remote : transactions) {
                    if ((!TextUtils.isEmpty(pending.id) && TextUtils.equals(pending.id, remote.id))
                            || pending.incoming == remote.incoming && (sameTransactionHash(pending.tx_hash, remote.tx_hash)
                            || sameTransactionHash(pending.messageHash, remote.messageHash))) {
                        pendingTransactions.remove(i);
                        final GramWalletPolling poller = polling.remove(pending);
                        if (poller != null) poller.stop();
                        break;
                    }
                }
            }
            allTransactions.clear();
            allTransactions.addAll(pendingTransactions);
            allTransactions.addAll(transactions);
            allTransactions.sort((a, b) -> Integer.compare(b.date, a.date));
            addressFormatter.clear();
            for (TL_wallet.walletTransaction t : allTransactions) {
                if (t != null && t.peer != null && t.peer.address != null) {
                    addressFormatter.add(t.peer.address);
                }
            }
            addressFormatter.done();
        }

        public void load() {
            if (loading) return;
            if (endReached) return;

            final TL_wallet.getTransactions req = new TL_wallet.getTransactions();
            req.inbound = true;
            req.outbound = true;
            req.limit = 10;
            req.offset = lastOffset;

            reqId = getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (result, error) -> {
                reqId = -1;
                loading = false;

                if (result != null) {
                    MessagesController.getInstance(currentAccount).putUsers(result.users, false);
                    MessagesController.getInstance(currentAccount).putChats(result.chats, false);

                    for (TL_wallet.walletTransaction transaction : result.transactions) {
                        appendOrReplace(transaction);
                    }

                    lastOffset = result.next_offset;
                    if (result.transactions.size() < req.limit)
                        endReached = true;
                } else {
                    endReached = true;
                }

                updatedTransactions();
                notifyUpdate();
            });
        }

        public boolean isEndReached() {
            return endReached;
        }

        public void clear() {
            cancel();
            transactions.clear();
            streamTransactionIds.clear();
            streamTraces.clear();
            lastOffset = "";
            endReached = false;
            updatedTransactions();
        }

        @Nullable
        private TL_wallet.walletTransaction findTransactionById(ArrayList<TL_wallet.walletTransaction> list, String id) {
            for (int i = 0; i < list.size(); ++i)
                if (TextUtils.equals(list.get(i).id, id))
                    return list.get(i);
            return null;
        }

        public void cancel() {
            if (reqId >= 0) {
                getConnectionsManager().cancelRequest(reqId, true);
                reqId = -1;
            }
            if (invalidationReqId >= 0) {
                getConnectionsManager().cancelRequest(invalidationReqId, true);
                invalidationReqId = -1;
            }
            loading = false;
            loadingInvalidation = false;
        }

        private void appendOrReplace(TL_wallet.walletTransaction t) {
            if (t == null) return;
            for (int i = 0; i < transactions.size(); ++i) {
                final TL_wallet.walletTransaction tt = transactions.get(i);
                if (TextUtils.equals(tt.id, t.id)) {
                    streamTransactionIds.remove(t.id);
                    streamTraces.remove(t.id);
                    tt.set(t);
                    return;
                }
            }
            transactions.add(t);
        }

        private void notifyUpdate() {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.walletTransactionsUpdate, this, GramWallet.this);
        }
    }

    private ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(currentAccount);
    }

    private int now() {
        return getConnectionsManager().getCurrentTime();
    }

    private void loadPrefs() {
        try {
            final SharedPreferences prefs = getContext().getSharedPreferences("gram_wallet", Activity.MODE_PRIVATE);
            usePasscodeSetting = prefs.getBoolean("passcode", true);
        } catch (Exception e) {
            err("failed to load prefs", e);
        }
    }

    private void savePrefs() {
        try {
            final SharedPreferences prefs = getContext().getSharedPreferences("gram_wallet", Activity.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("passcode", usePasscodeSetting)
                    .apply();
        } catch (Exception e) {
            err("failed to save prefs", e);
        }
    }

    private static final long TWO_DECIMAL_THRESHOLD = 1_000_000_000L / 100;
    private static DecimalFormat gramsFormat;

    public static String formatGrams(long amount) {
        return formatGrams(amount, false);
    }
    public static String formatGrams(long amount, boolean manyZeros) {
        if (amount > -TWO_DECIMAL_THRESHOLD && amount < TWO_DECIMAL_THRESHOLD || manyZeros) {
            return StarsIntroActivity.formatTON(amount);
        }
        if (gramsFormat == null) {
            gramsFormat = new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.US));
        }
        return gramsFormat.format(amount / 1_000_000_000.0);
    }

    public static CharSequence formatGramsWithUnit(long amount) {
        return formatGramsWithUnit(amount, false);
    }

    public static CharSequence formatGramsWithUnit(long amount, boolean manyZeros) {
        return formatGramsWithUnit(formatGrams(amount, manyZeros));
    }

    public static CharSequence formatGramsWithUnit(CharSequence formattedAmount) {
        return formatAmountWithUnit("Grams", R.string.Grams_other, formattedAmount);
    }

    public static CharSequence formatGramCapital(long amount) {
        return formatGramCapital(amount, false);
    }

    public static CharSequence formatGramCapital(long amount, boolean manyZeros) {
        return formatGramCapital(formatGrams(amount, manyZeros));
    }

    public static CharSequence formatGramCapital(CharSequence formattedAmount) {
        return formatAmountWithUnit("GramCapital", R.string.GramCapital_other, formattedAmount);
    }

    private static CharSequence formatAmountWithUnit(String key, int otherRes, CharSequence formattedAmount) {
        // Wallet transfer amounts use an en dash for the outgoing sign.
        final String number = formattedAmount.toString().replace('\u2013', '-');
        String template = getString(otherRes);
        if (number.indexOf('.') < 0) {
            try {
                final long integer = Long.parseLong(number);
                final int plural = integer >= -Integer.MAX_VALUE && integer <= Integer.MAX_VALUE ? (int) Math.abs(integer) : (int) (100 + Math.abs(integer % 100));
                template = LocaleController.getPluralString(key, plural);
            } catch (NumberFormatException ignored) {}
        }
        return AndroidUtilities.replaceCharSequence("%1$s", template, formattedAmount);
    }

    public static SpannableStringBuilder formatGramsScaled(long amount) {
        return formatGramsScaled(amount, 0.8f);
    }

    public static SpannableStringBuilder formatGramsScaled(long amount, float scale) {
        return formatGramsScaled(formatGrams(amount), scale);
    }

    public static SpannableStringBuilder formatGramsScaled(String value, float scale) {
        final SpannableStringBuilder result = new SpannableStringBuilder(value);
        final int point = value.indexOf('.');
        if (point >= 0 && point + 1 < value.length()) {
            result.setSpan(
                    new RelativeSizeSpan(scale),
                    point + 1,
                    value.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return result;
    }

    public CharSequence formatCurrency(long nanogramsAmount) {
        return formatCurrency(nanogramsAmount, false);
    }

    public CharSequence formatCurrency(long nanogramsAmount, boolean manyZeros) {
        final String currency = currencies.getCurrency();
        final TL_wallet.currencyRate currencyRate = currencies.getSelectedCurrencyRate();
        if (currencyRate == null) return "";
        final String symbol = currencyRate.symbol;
        final String thousandsSeparator = currencyRate.thousandsSeparator;
        final String decimalSeparator = currencyRate.decimalSeparator;
        final boolean symbolLeft = currencyRate.symbolLeft;
        final boolean spaceBetween = currencyRate.spaceBetween;
        final boolean dropZeros = currencyRate.dropZeros;
        final int exp = currencyRate.exp;
        double rate = currencyRate.rate;
        if (rate <= 0 || Double.isNaN(rate) || Double.isInfinite(rate)) return "";

        rate *= MessagesController.getInstance(currentAccount).config.tonUsdRate.get();

        final int fractionDigits = Math.max(0, Math.min(exp, 20));
        final BigDecimal convertedAmount = BigDecimal.valueOf(nanogramsAmount)
                .multiply(BigDecimal.valueOf(rate))
                .movePointLeft(9);
        BigDecimal amount;
        int maximumFractionDigits = fractionDigits;
        if (manyZeros && convertedAmount.signum() != 0) {
            amount = convertedAmount
                    .round(new MathContext(3, RoundingMode.HALF_UP))
                    .stripTrailingZeros();
            maximumFractionDigits = Math.max(
                    fractionDigits,
                    Math.min(20, Math.max(0, amount.scale()))
            );
        } else {
            amount = convertedAmount.setScale(fractionDigits, RoundingMode.HALF_UP);
        }

        final DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.US);
        final boolean useGrouping = !TextUtils.isEmpty(thousandsSeparator);
        if (useGrouping) {
            formatSymbols.setGroupingSeparator(thousandsSeparator.charAt(0));
        }
        formatSymbols.setDecimalSeparator(
                TextUtils.isEmpty(decimalSeparator) ? '.' : decimalSeparator.charAt(0)
        );

        final DecimalFormat formatter = new DecimalFormat("#,##0", formatSymbols);
        formatter.setGroupingUsed(useGrouping);
        formatter.setMinimumFractionDigits(dropZeros ? 0 : fractionDigits);
        formatter.setMaximumFractionDigits(maximumFractionDigits);
        formatter.setRoundingMode(RoundingMode.HALF_UP);

        final boolean negative = amount.signum() < 0;
        final String number = formatter.format(amount.abs());
        final String displaySymbol = TextUtils.isEmpty(symbol) ? currency : symbol;
        final String spacing = spaceBetween ? "\u00A0" : "";
        final String value = symbolLeft
                ? displaySymbol + spacing + number
                : number + spacing + displaySymbol;
        final String result = negative ? "-" + value : value;

        int dirhamIndex = result.indexOf('\u20C3');
        if (dirhamIndex >= 0) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(result);
            ssb.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/gram.ttf")), dirhamIndex, dirhamIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ssb;
        }
        return result;
    }

    private Context getContext() {
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null && lastFragment.getContext() != null)
            return lastFragment.getContext();
        return ApplicationLoader.applicationContext;
    }

    public static final class SecretPhrase implements AutoCloseable {
        private final byte[] bytes;
        private boolean closed;

        public SecretPhrase(byte[] bytes) {
            if (bytes == null) throw new IllegalArgumentException("Missing recovery phrase");
            this.bytes = bytes.clone();
        }

        public static SecretPhrase fromWords(List<String> words) {
            int size = Math.max(0, words.size() - 1);
            ArrayList<byte[]> encoded = new ArrayList<>();
            try {
                for (String word : words) {
                    byte[] value = word.getBytes(StandardCharsets.UTF_8);
                    encoded.add(value);
                    size = Math.addExact(size, value.length);
                }
                byte[] bytes = new byte[size];
                try {
                    int offset = 0;
                    for (int i = 0; i < encoded.size(); i++) {
                        if (i > 0) bytes[offset++] = ' ';
                        byte[] word = encoded.get(i);
                        System.arraycopy(word, 0, bytes, offset, word.length);
                        offset += word.length;
                    }
                    return new SecretPhrase(bytes);
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
            } finally {
                for (byte[] word : encoded) Arrays.fill(word, (byte) 0);
            }
        }

        private void checkOpen() {
            if (closed) throw new IllegalStateException("Recovery phrase is closed");
        }

        private static boolean isSeparator(byte b) {
            return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '\f' || b == 11;
        }

        public synchronized int wordCount() {
            checkOpen();
            int count = 0;
            boolean inWord = false;
            for (byte b : bytes) {
                if (isSeparator(b)) inWord = false;
                else if (!inWord) { count++; inWord = true; }
            }
            return count;
        }

        public synchronized boolean isEmpty() { return wordCount() == 0; }
        public synchronized int byteCount() { checkOpen(); return bytes.length; }

        public synchronized byte[] copyBytes() { checkOpen(); return bytes.clone(); }
        public synchronized SecretPhrase copy() { checkOpen(); return new SecretPhrase(bytes); }

        public synchronized SecretPhrase firstWords(int count) {
            checkOpen();
            if (count <= 0) throw new IllegalArgumentException("Invalid word count");
            int start = 0;
            while (start < bytes.length && isSeparator(bytes[start])) start++;
            int end = start;
            for (int i = 0; i < count; i++) {
                if (end == bytes.length) throw new IllegalArgumentException("Not enough recovery words");
                while (end < bytes.length && !isSeparator(bytes[end])) end++;
                if (i + 1 < count) while (end < bytes.length && isSeparator(bytes[end])) end++;
            }
            byte[] selected = Arrays.copyOfRange(bytes, start, end);
            try { return new SecretPhrase(selected); }
            finally { Arrays.fill(selected, (byte) 0); }
        }

        public synchronized ArrayList<String> toWordsForDisplay() {
            checkOpen();
            ArrayList<String> words = new ArrayList<>();
            int offset = 0;
            while (offset < bytes.length) {
                while (offset < bytes.length && isSeparator(bytes[offset])) offset++;
                int start = offset;
                while (offset < bytes.length && !isSeparator(bytes[offset])) offset++;
                if (offset > start) words.add(new String(bytes, start, offset - start, StandardCharsets.UTF_8));
            }
            return words;
        }

        @Override public synchronized void close() {
            Arrays.fill(bytes, (byte) 0);
            closed = true;
        }

        @Override public String toString() { return "SecretPhrase[redacted]"; }

        static void deliver(SecretPhrase phrase, Utilities.Callback<SecretPhrase> callback) {
            try { if (callback != null) callback.run(phrase); }
            finally { if (phrase != null) phrase.close(); }
        }
    }

}
