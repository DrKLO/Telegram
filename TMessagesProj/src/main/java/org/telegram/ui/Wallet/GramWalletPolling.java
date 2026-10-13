package org.telegram.ui.Wallet;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_wallet;

/** Polls one submitted message. Lifecycle and callbacks run on the UI thread. */
public class GramWalletPolling {
    private static final long[] DELAYS_MS = {500, 1000, 3000, 5000, 10000};
    private static final long REQUEST_TIMEOUT_MS = 30_000;

    public final int currentAccount;
    public final String messageHash;
    private final IGramWalletPollingSubscriber subscriber;
    private boolean running;
    private int generation;
    private int attempt;
    private int requestId = -1;

    public interface IGramWalletPollingSubscriber {
        /** Return true once a terminal transaction has been handled; false to keep polling. */
        boolean onTransactions(TL_wallet.walletTransactions result);
    }

    public GramWalletPolling(int currentAccount, String messageHash, IGramWalletPollingSubscriber subscriber) {
        this.currentAccount = currentAccount;
        this.messageHash = messageHash;
        this.subscriber = subscriber;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running || TextUtils.isEmpty(messageHash)) return;
        running = true;
        attempt = 0;
        schedule();
    }

    public void stop() {
        running = false;
        ++generation;
        AndroidUtilities.cancelRunOnUIThread(poll);
        AndroidUtilities.cancelRunOnUIThread(timeout);
        cancelRequest();
    }

    private ConnectionsManager connections() {
        return ConnectionsManager.getInstance(currentAccount);
    }

    private void cancelRequest() {
        if (requestId >= 0) {
            connections().cancelRequest(requestId, true);
            requestId = -1;
        }
    }

    private void schedule() {
        if (!running) return;
        long delay = DELAYS_MS[attempt];
        if (attempt < DELAYS_MS.length - 1) ++attempt;
        AndroidUtilities.runOnUIThread(poll, delay);
    }

    private final Runnable timeout = () -> {
        if (!running) return;
        ++generation;
        cancelRequest();
        schedule();
    };

    private final Runnable poll = this::requestTransactions;

    private void requestTransactions() {
        if (!running) return;
        final int requestGeneration = ++generation;
        final TL_wallet.getTransactionsByMsgHash request = new TL_wallet.getTransactionsByMsgHash();
        request.msg_hash.add(messageHash);
        AndroidUtilities.runOnUIThread(timeout, REQUEST_TIMEOUT_MS);
        requestId = connections().sendRequestTyped(request, AndroidUtilities::runOnUIThread, (result, error) -> {
            if (!running || generation != requestGeneration) return;
            requestId = -1;
            AndroidUtilities.cancelRunOnUIThread(timeout);
            FileLog.d("[gram-wallet-polling] account=" + currentAccount + " msg_hash=" + messageHash
                    + " transactions=" + (result == null ? "none" : result.transactions.size())
                    + " errorCode=" + (error == null ? "none" : error.code));
            if (error == null && result != null && subscriber.onTransactions(result)) {
                stop();
            } else if (running && generation == requestGeneration) {
                // Empty results and RPC errors are not transaction failures.
                schedule();
            }
        });
    }
}
