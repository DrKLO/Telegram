package org.telegram.ui.Wallet;

import android.os.SystemClock;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.neovisionaries.ws.client.OpeningHandshakeException;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_toncenter;

import java.util.List;
import java.util.Map;

/** Main wallet and optional additional transactions. Lifecycle and callbacks run on the UI thread. */
public class GramWalletStreaming {

    private static final long PING_INTERVAL_MS = 15_000;
    private static final long TIMEOUT_MS = 30_000;

    public final int currentAccount;
    public final String address;
    private final IGramWalletStreamingSubscriber subscriber;

    private boolean running;
    private boolean subscribed;
    private String subscriptionId;
    private int subscriptionVersion;
    private int generation;
    private int requestId = -1;
    private int reconnectAttempt;
    private WebSocket socket;
    private long lastPong;
    private void sendSubscription() {
        try {
            JSONArray addresses = new JSONArray().put(address);
            subscriptionId = "wallet-" + (++subscriptionVersion);
            JSONObject request = new JSONObject()
                .put("operation", "subscribe")
                .put("id", subscriptionId)
                .put("types", new JSONArray().put("transactions")
                    .put("account_state_change"))
                .put("include_address_book", true)
                .put("addresses", addresses)
                .put("min_finality", "pending");
            AndroidUtilities.cancelRunOnUIThread(timeout);
            AndroidUtilities.runOnUIThread(timeout, TIMEOUT_MS);
            sendText(request.toString());
        } catch (JSONException e) {
            retry("could not encode subscription");
        }
    }

    public interface IGramWalletStreamingSubscriber {
        void onEvent(JSONObject event);
    }

    private final Runnable reconnect = this::requestUrl;
    private final Runnable expire = () -> {
        if (!running) return;
        log("URL expired; renewing connection");
        disconnect();
        requestUrl();
    };
    private final Runnable timeout = () -> retry("connection or subscription timed out");
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (!running || !subscribed || socket == null) return;
            if (SystemClock.elapsedRealtime() - lastPong >= TIMEOUT_MS) {
                retry("heartbeat timed out");
                return;
            }
            log("sending ping; last pong " + (SystemClock.elapsedRealtime() - lastPong) + " ms ago");
            sendText("{\"operation\":\"ping\",\"id\":\"heartbeat\"}");
            AndroidUtilities.runOnUIThread(this, PING_INTERVAL_MS);
        }
    };

    public GramWalletStreaming(int currentAccount, String address, IGramWalletStreamingSubscriber subscriber) {
        this.currentAccount = currentAccount;
        this.address = address;
        this.subscriber = subscriber;
    }

    /** True while started, including while connecting or waiting to retry. */
    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running || TextUtils.isEmpty(address)) {
            log("start ignored: running=" + running + ", emptyAddress=" + TextUtils.isEmpty(address));
            return;
        }
        log("starting; address=" + address);
        running = true;
        reconnectAttempt = 0;
        requestUrl();
    }

    public void stop() {
        if (!running) return;
        log("stopping");
        running = false;
        disconnect();
    }

    private ConnectionsManager connections() {
        return ConnectionsManager.getInstance(currentAccount);
    }

    private void requestUrl() {
        if (!running) return;
        AndroidUtilities.cancelRunOnUIThread(reconnect);
        final int attempt = ++generation;
        final long requestedAt = SystemClock.elapsedRealtime();
        log("requesting streaming URL; retry=" + reconnectAttempt);
        AndroidUtilities.runOnUIThread(timeout, TIMEOUT_MS);
        requestId = connections().sendRequestTyped(new TL_toncenter.getStreamingUrl(),
            AndroidUtilities::runOnUIThread, (result, error) -> {
                if (!running || generation != attempt) {
                    log("ignoring stale URL response; attempt=" + attempt);
                    return;
                }
                log("URL response after " + (SystemClock.elapsedRealtime() - requestedAt)
                    + " ms; errorCode=" + (error == null ? "none" : error.code));
                requestId = -1;
                AndroidUtilities.cancelRunOnUIThread(timeout);
                if (result == null || TextUtils.isEmpty(result.url)) {
                    retry("could not obtain streaming URL");
                    return;
                }
                long expiresInMs = ((long) result.expires - connections().getCurrentTime()) * 1000L;
                log("URL expires=" + result.expires + ", remaining=" + expiresInMs + " ms");
                if (expiresInMs <= 0) {
                    retry("received expired streaming URL");
                    return;
                }
                AndroidUtilities.runOnUIThread(expire, expiresInMs);
                connect(result.url, attempt);
            });
    }

    private void connect(String url, int attempt) {
        final long connectingAt = SystemClock.elapsedRealtime();
        log("opening websocket; attempt=" + attempt);
        try {
            // Preserve the server-issued URL, including its authentication parameters.
            socket = new WebSocketFactory().setConnectionTimeout(10_000).createSocket(url);
            socket.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket ws, Map<String, List<String>> headers) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!isCurrent(ws, attempt)) {
                            log("closing stale connected websocket; attempt=" + attempt);
                            ws.disconnect();
                            return;
                        }
                        log("websocket connected after " + (SystemClock.elapsedRealtime() - connectingAt) + " ms");
                        sendSubscription();
                    });
                }

                @Override
                public void onTextMessage(WebSocket ws, String text) {
                    // Capture the complete payload before parsing, stale-event checks or address filtering.
                    FileLog.d("[gram-wallet-streaming] account=" + currentAccount
                        + " generation=" + attempt + " receive <- " + text);
                    final JSONObject event;
                    try {
                        event = new JSONObject(text);
                    } catch (JSONException e) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (isCurrent(ws, attempt)) retry("invalid streaming JSON");
                        });
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (isCurrent(ws, attempt)) handleMessage(event);
                    });
                }

                @Override
                public void onConnectError(WebSocket ws, WebSocketException exception) {
                    failed(ws, attempt, "connect error: " + errorDescription(exception));
                }

                @Override
                public void onError(WebSocket ws, WebSocketException exception) {
                    failed(ws, attempt, "websocket error: " + errorDescription(exception));
                }

                @Override
                public void onDisconnected(WebSocket ws, WebSocketFrame serverCloseFrame,
                                           WebSocketFrame clientCloseFrame, boolean closedByServer) {
                    failed(ws, attempt, "websocket disconnected; closedByServer=" + closedByServer
                        + ", serverCloseCode=" + (serverCloseFrame == null ? "none" : serverCloseFrame.getCloseCode())
                        + ", clientCloseCode=" + (clientCloseFrame == null ? "none" : clientCloseFrame.getCloseCode()));
                }
            });
            AndroidUtilities.runOnUIThread(timeout, TIMEOUT_MS);
            socket.connectAsynchronously();
        } catch (Exception e) {
            // Exception messages can include the authenticated URL. Do not log them.
            retry("could not open websocket: " + e.getClass().getSimpleName());
        }
    }

    private boolean isCurrent(WebSocket ws, int attempt) {
        return running && socket == ws && generation == attempt;
    }

    private static String errorDescription(WebSocketException exception) {
        String description = String.valueOf(exception.getError());
        if (exception instanceof OpeningHandshakeException) {
            OpeningHandshakeException handshake = (OpeningHandshakeException) exception;
            if (handshake.getStatusLine() != null) {
                description += ", HTTP " + handshake.getStatusLine().getStatusCode();
            }
        }
        return description;
    }

    private void failed(WebSocket ws, int attempt, String reason) {
        AndroidUtilities.runOnUIThread(() -> {
            if (isCurrent(ws, attempt)) {
                retry(reason);
            } else {
                log("ignoring stale socket callback; attempt=" + attempt + ", " + reason);
            }
        });
    }

    private void handleMessage(JSONObject event) {
        String status = event.optString("status");
        if ((event.has("error") && !event.isNull("error")) || "error".equals(status)) {
            log("subscription error; id=" + event.optString("id")
                + ", code=" + event.optString("code") + ", status=" + status
                + ", error=" + event.opt("error") + ", message=" + event.optString("message"));
            retry("subscription rejected");
            return;
        }
        if ("subscribed".equals(status)) {
            log("subscription acknowledgement: " + event);
            if (!TextUtils.equals(subscriptionId, event.optString("id"))) return;
            subscribed = true;
            reconnectAttempt = 0;
            lastPong = SystemClock.elapsedRealtime();
            AndroidUtilities.cancelRunOnUIThread(timeout);
            AndroidUtilities.cancelRunOnUIThread(heartbeat);
            AndroidUtilities.runOnUIThread(heartbeat, PING_INTERVAL_MS);
            log("subscription acknowledged; requested transactions and account state");
            return;
        }
        if ("pong".equals(status) && "heartbeat".equals(event.optString("id"))) {
            log("received pong");
            lastPong = SystemClock.elapsedRealtime();
            return;
        }
        if (!subscribed) {
            log("ignoring message before subscription; type=" + event.optString("type") + ", status=" + status);
            return;
        }
        String type = event.optString("type");
        // Never expose another account's balance, including events queued before removal.
        if ("account_state_change".equals(type)
                && !GramWallet.addressEquals(address, event.optString("account"))) return;
        if ("transactions".equals(type) || "account_state_change".equals(type)
                || "trace_invalidated".equals(type)) {
            JSONArray transactions = event.optJSONArray("transactions");
            JSONObject state = event.optJSONObject("state");
            log("event type=" + type + ", finality=" + event.optString("finality")
                + ", trace=" + event.optString("trace_external_hash_norm")
                + (transactions == null ? "" : ", transactions=" + transactions.length())
                + (state == null ? "" : ", balance=" + state.optString("balance")));
            if ("transactions".equals(type)) {
                deliverTransactions(event, address);
            } else {
                subscriber.onEvent(event);
            }
        } else {
            log("ignoring message; type=" + type + ", status=" + status);
        }
    }

    private void deliverTransactions(JSONObject event, String target) {
        if (target == null) return;
        JSONArray transactions = event.optJSONArray("transactions");
        if (transactions == null) return;
        JSONArray matching = new JSONArray();
        for (int i = 0; i < transactions.length(); ++i) {
            JSONObject transaction = transactions.optJSONObject(i);
            if (transaction != null && GramWallet.addressEquals(target, transaction.optString("account"))) {
                matching.put(transaction);
            }
        }
        if (matching.length() == 0) return;
        try {
            JSONObject filtered = new JSONObject(event.toString());
            filtered.put("transactions", matching);
            subscriber.onEvent(filtered);
        } catch (JSONException e) {
            log("could not filter streaming transactions");
        }
    }

    private void retry(String reason) {
        if (!running) return;
        disconnect();
        long delay = Math.min(30_000L, 1000L << Math.min(reconnectAttempt++, 5));
        delay += (long) (Math.random() * 500);
        log(reason + "; retrying in " + delay + " ms");
        AndroidUtilities.runOnUIThread(reconnect, delay);
    }

    private void disconnect() {
        log("disconnecting; socket=" + (socket != null) + ", subscribed=" + subscribed + ", URL request=" + requestId);
        ++generation; // Ignore queued events from the previous socket or URL request.
        subscribed = false;
        AndroidUtilities.cancelRunOnUIThread(reconnect);
        AndroidUtilities.cancelRunOnUIThread(expire);
        AndroidUtilities.cancelRunOnUIThread(timeout);
        AndroidUtilities.cancelRunOnUIThread(heartbeat);
        if (requestId >= 0) {
            connections().cancelRequest(requestId, true);
            requestId = -1;
        }
        WebSocket previous = socket;
        socket = null;
        if (previous != null) previous.disconnect();
    }

    private void sendText(String text) {
        log("send -> " + text);
        socket.sendText(text);
    }

    private void log(String message) {
        FileLog.d("[gram-wallet-streaming] account=" + currentAccount + " generation=" + generation + " " + message);
    }
}
