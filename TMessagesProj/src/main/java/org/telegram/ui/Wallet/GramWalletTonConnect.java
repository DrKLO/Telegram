package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Wallet.WalletImportFragment.formatAddressTwoLines;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.Wallet.GramWallet.SecretPhrase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class GramWalletTonConnect {

    public final int currentAccount;
    public final GramWallet wallet;
    private final HashSet<Long> disconnecting = new HashSet<>();

    public final ArrayList<TL_wallet.tonConnectSession> sessions = new ArrayList<>();

    public static final class ConnectRequest {
        final String json, manifestUrl, traceId, embedded;
        TL_wallet.tonConnectSession session;
        boolean busy;

        ConnectRequest(TL_wallet.tonConnectSession session, String json, String manifestUrl, String traceId, String embedded) {
            this.session = session;
            this.json = json;
            this.manifestUrl = manifestUrl;
            this.traceId = traceId;
            this.embedded = embedded;
        }
    }

    public GramWalletTonConnect(GramWallet wallet) {
        this.currentAccount = wallet.currentAccount;
        this.wallet = wallet;
        connections = ConnectionsManager.getInstance(wallet.currentAccount);
        requestSessions();
    }

    private int requestingSessionsReqId = -1;
    public boolean isLoadingSessions() {
        return requestingSessionsReqId >= 0;
    }
    private void requestSessions() {
        if (requestingSessionsReqId >= 0) return;
        requestingSessionsReqId = connections.sendRequestTyped(new TL_wallet.tonConnectGetSessions(), AndroidUtilities::runOnUIThread, (sessions, error) -> {
            requestingSessionsReqId = -1;
            if (error != null || sessions == null) {
                rpc("getSessions", error);
                wallet.notifyUpdate();
                return;
            }
            this.sessions.clear();
            if (sessions != null && sessions.sessions != null) {
                this.sessions.addAll(sessions.sessions);
            }
            wallet.notifyUpdate();
        });
    }
    public void createSession(String dappClientId, String manifestUrl, String requestJson,
            String traceId, String embeddedRequest, Utilities.Callback2<ConnectRequest, String> whenDone) {
        TL_wallet.tonConnectCreateSession request = new TL_wallet.tonConnectCreateSession();
        request.dapp_client_id = dappClientId;
        request.manifest_url = manifestUrl;
        connections.sendRequestTyped(request, AndroidUtilities::runOnUIThread, (session, error) -> {
            if (error != null || session == null) {
                whenDone.run(null, rpc("createSession", error));
                return;
            }
            TL_wallet.tonConnectSession current = session;
            for (TL_wallet.tonConnectSession cached : sessions) {
                if (cached.id == session.id && (cached.manifest != null || cached.manifest_error != null || cached.closed || cached.closing)) {
                    current = cached;
                    break;
                }
            }
            onSessionUpdate(current);
            whenDone.run(new ConnectRequest(current, requestJson, manifestUrl, traceId, embeddedRequest), null);
        });
    }

    public void onSessionUpdate(TL_wallet.tonConnectSession session) {
        sessions.removeIf(s -> s.id == session.id);
        if (!session.closed) sessions.add(session);
        updateOpenRequests(session);
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(
                org.telegram.messenger.NotificationCenter.walletUpdate, wallet, session);
    }

    public boolean requestsProof(ConnectRequest context) {
        if (context == null || context.json == null) return false;
        try {
            org.json.JSONArray items = new JSONObject(context.json).getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                if ("ton_proof".equals(items.getJSONObject(i).optString("name"))) return true;
            }
        } catch (Exception e) {
            exception("read proof request", e);
        }
        return false;
    }

    public void approveSession(ConnectRequest context, Utilities.Callback<String> whenDone) {
        AndroidUtilities.runOnUIThread(() -> approveSessionOnUIThread(context, failure -> {
            log("approve session", failure);
            whenDone.run(failure);
        }));
    }

    public void disconnectSession(TL_wallet.tonConnectSession session, Utilities.Callback<String /* error */> whenDone) {
        AndroidUtilities.runOnUIThread(() -> disconnectSessionOnUIThread(session, failure -> {
            log("disconnect session", failure);
            whenDone.run(failure);
        }));
    }

    private void disconnectSessionOnUIThread(TL_wallet.tonConnectSession session, Utilities.Callback<String> whenDone) {
        if (session == null || session.dapp_client_id == null || session.client_id == null || session.nonce == null) {
            whenDone.run("Missing TON Connect session data");
            return;
        }
        if (!disconnecting.add(session.id)) {
            whenDone.run("A TON Connect operation is already in progress");
            return;
        }
        Utilities.Callback<String> finish = error -> AndroidUtilities.runOnUIThread(() -> {
            if (!disconnecting.remove(session.id)) return;
            if (error == null) {
                sessions.removeIf(s -> s.id == session.id);
                wallet.notifyUpdate();
            }
            whenDone.run(error);
        });
        if (session.closed) {
            finish.run(null);
            return;
        }
        wallet.whenReady(() -> {
            String address = wallet.getAddress();
            byte[] walletKey = wallet.getPublicKey();
            byte[] publicKey = walletKey == null ? null : walletKey.clone();
            if (address == null || publicKey == null) {
                finish.run("Wallet is not ready");
                return;
            }
            wallet.getSecretPhrase(true, false, (borrowed, secretError) -> {
                if (secretError != null || borrowed == null) {
                    finish.run(secretError == null ? "Recovery phrase is unavailable" : secretError);
                    return;
                }

                final SecretPhrase secretPhrase = borrowed.copy();
                final Utilities.Callback<String> finishPhrase = failure -> {
                    secretPhrase.close();
                    finish.run(failure);
                };
                TL_wallet.tonConnectGetNextEventId next = new TL_wallet.tonConnectGetNextEventId();
                next.session_id = session.id;
                connections.sendRequestTyped(next, AndroidUtilities::runOnUIThread, (event, eventError) -> {
                    if (eventError != null || event == null) {
                        finishPhrase.run(rpc("getNextEventId", eventError));
                        return;
                    }
                    Utilities.globalQueue.postRunnable(() -> {
                        final Prepared prepared;
                        try {
                            prepared = prepareDisconnect(secretPhrase, session, address, publicKey, event.event_id);
                        } catch (Exception e) {
                            finishPhrase.run(exception("prepare disconnect", e));
                            return;
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (!TextUtils.equals(address, wallet.getAddress()) || !Arrays.equals(publicKey, wallet.getPublicKey())) {
                                finishPhrase.run("Wallet or TON Connect session changed. Open the request again.");
                                return;
                            }
                            TL_wallet.tonConnectCloseSession request = new TL_wallet.tonConnectCloseSession();
                            request.session_id = session.id;
                            request.body = prepared.body;
                            connections.sendRequestTyped(request, AndroidUtilities::runOnUIThread, (result, error) ->
                                    finishPhrase.run(error == null && result instanceof TLRPC.TL_boolTrue ? null : rpc("closeSession", error)));
                        });
                    });
                });
            });
        });
    }

    private void approveSessionOnUIThread(ConnectRequest context, Utilities.Callback<String> whenDone) {
        TL_wallet.tonConnectSession session = context.session;
        if (context.json == null) {
            whenDone.run("The original connection request is unavailable");
            return;
        }
        if (session.closed || session.closing || session.manifest_error != null || session.manifest == null) {
            whenDone.run("The dApp manifest is unavailable or the session is closed");
            return;
        }
        if (context.busy) {
            whenDone.run("A TON Connect operation is already in progress");
            return;
        }
        context.busy = true;
        wallet.whenReady(() -> {
            String address = wallet.getAddress();
            byte[] walletKey = wallet.getPublicKey();
            byte[] publicKey = walletKey == null ? null : walletKey.clone();
            Utilities.Callback<String> finish = error -> AndroidUtilities.runOnUIThread(() -> {
                context.busy = false;
                whenDone.run(error);
            });
            if (address == null || publicKey == null) {
                finish.run("Wallet is not ready");
                return;
            }
            wallet.getSecretPhrase(true, false, (borrowed, error) -> {
                if (error != null || borrowed == null) {
                    finish.run(error == null ? "Recovery phrase is unavailable" : error);
                    return;
                }

                final SecretPhrase secretPhrase = borrowed.copy();
                final Utilities.Callback<String> finishPhrase = failure -> {
                    secretPhrase.close();
                    finish.run(failure);
                };
                Utilities.globalQueue.postRunnable(() -> {
                    final Prepared identity;
                    try {
                        identity = prepareConnect(secretPhrase, session, address, publicKey,
                                context.json, context.manifestUrl, context.embedded, null, connections.getCurrentTime());
                    } catch (Exception e) {
                        String failure = exception("prepare connect", e);
                        AndroidUtilities.runOnUIThread(() -> finishPhrase.run(failure));
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!canSubmit(context, session, address, publicKey)
                                || context.session.client_id != null && !identity.clientId.equalsIgnoreCase(context.session.client_id)) {
                            finishPhrase.run("Wallet or TON Connect session changed. Open the request again.");
                            return;
                        }
                        TL_wallet.tonConnectRegisterKey register = new TL_wallet.tonConnectRegisterKey();
                        register.session_id = session.id;
                        register.client_id = identity.clientId;
                        connections.sendRequestTyped(register, AndroidUtilities::runOnUIThread, (challenge, registerError) -> {
                            if (registerError != null || challenge == null) {
                                finishPhrase.run(rpc("registerKey", registerError));
                                return;
                            }
                            Utilities.globalQueue.postRunnable(() -> {
                                final Prepared prepared;
                                try {
                                    prepared = prepareConnect(secretPhrase, session, address, publicKey,
                                            context.json, context.manifestUrl, context.embedded, challenge, connections.getCurrentTime());
                                } catch (Exception e) {
                                    String failure = exception("prepare connect challenge", e);
                                    AndroidUtilities.runOnUIThread(() -> finishPhrase.run(failure));
                                    return;
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (!canSubmit(context, session, address, publicKey)) {
                                        Arrays.fill(prepared.challengeAnswer, (byte) 0);
                                        finishPhrase.run("Wallet or TON Connect session changed. Open the request again.");
                                        return;
                                    }
                                    TL_wallet.tonConnectSubmitConnectResult submit = new TL_wallet.tonConnectSubmitConnectResult();
                                    submit.session_id = session.id;
                                    submit.challenge_answer = prepared.challengeAnswer;
                                    submit.body = prepared.body;
                                    submit.trace_id = context.traceId;
                                    connections.sendRequestTyped(submit, AndroidUtilities::runOnUIThread, (result, submitError) -> {
                                        Arrays.fill(prepared.challengeAnswer, (byte) 0);
                                        finishPhrase.run(submitError == null && result instanceof TLRPC.TL_boolTrue ? null : rpc("submitConnectResult", submitError));
                                        requestSessions();
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    /** Prepares an OAuth grant without activating the session or publishing a connect event. */
    private void prepareOauthSession(TLRPC.TL_urlAuthResultRequest auth, String address, byte[] publicKey,
            Utilities.Callback2<TL_wallet.inputTonConnectOauthSession, String> whenDone) {
        if (!auth.request_wallet || auth.bot == null || TextUtils.isEmpty(auth.domain)
                || !TLObject.hasFlag(auth.flags, TLObject.FLAG_9) || !TLObject.hasFlag(auth.flags, TLObject.FLAG_10)
                || TextUtils.isEmpty(auth.tc_client_id) || address == null || publicKey == null) {
            whenDone.run(null, "Missing wallet login request data");
            return;
        }
        final JSONArray items;
        try {
            // OAuth has already validated this domain against the bot's allowed origins.
            items = new JSONArray().put(new JSONObject().put("name", "ton_addr"))
                    .put(new JSONObject().put("name", "ton_proof").put("payload",
                            "tc-login:" + auth.bot.id + ":" + auth.tc_client_id + ":" + auth.tc_session_id));
        } catch (JSONException e) {
            whenDone.run(null, exception("prepare OAuth request", e));
            return;
        }
        TL_wallet.tonConnectCreateSession create = new TL_wallet.tonConnectCreateSession();
        create.dapp_client_id = auth.tc_client_id;
        create.manifest_url = "https://" + auth.domain + "/tonconnect-manifest.json";
        connections.sendRequestTyped(create, AndroidUtilities::runOnUIThread, (session, createError) -> {
            if (createError != null || session == null) {
                whenDone.run(null, rpc("createSession", createError));
                return;
            }
            if (session.id != auth.tc_session_id || !auth.tc_client_id.equals(session.dapp_client_id)
                    || session.closed || session.closing || session.nonce == null
                    || !TextUtils.equals(address, wallet.getAddress()) || !Arrays.equals(publicKey, wallet.getPublicKey())) {
                whenDone.run(null, "Wallet or TON Connect session changed. Open the request again.");
                return;
            }
            wallet.getSecretPhrase(true, false, (borrowed, error) -> {
                if (error != null || borrowed == null) {
                    whenDone.run(null, error == null ? "Recovery phrase is unavailable" : error);
                    return;
                }
                final SecretPhrase phrase = borrowed.copy();
                final Utilities.Callback2<TL_wallet.inputTonConnectOauthSession, String> finish = (result, failure) -> {
                    phrase.close();
                    AndroidUtilities.runOnUIThread(() -> whenDone.run(result, failure));
                };
                Utilities.globalQueue.postRunnable(() -> {
                    final Prepared identity;
                    try {
                        identity = prepareConnect(phrase, session, address, publicKey, items, auth.domain,
                                null, null, connections.getCurrentTime());
                    } catch (Exception e) {
                        finish.run(null, exception("prepare OAuth identity", e));
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!TextUtils.equals(address, wallet.getAddress()) || !Arrays.equals(publicKey, wallet.getPublicKey())
                                || session.client_id != null && !identity.clientId.equalsIgnoreCase(session.client_id)) {
                            finish.run(null, "Wallet or TON Connect session changed. Open the request again.");
                            return;
                        }
                        TL_wallet.tonConnectRegisterKey register = new TL_wallet.tonConnectRegisterKey();
                        register.session_id = session.id;
                        register.client_id = identity.clientId;
                        connections.sendRequestTyped(register, AndroidUtilities::runOnUIThread, (challenge, registerError) -> {
                            if (registerError != null || challenge == null) {
                                finish.run(null, rpc("registerKey", registerError));
                                return;
                            }
                            Utilities.globalQueue.postRunnable(() -> {
                                final Prepared prepared;
                                try {
                                    prepared = prepareConnect(phrase, session, address, publicKey, items, auth.domain,
                                            null, challenge, connections.getCurrentTime());
                                } catch (Exception e) {
                                    finish.run(null, exception("prepare OAuth connect", e));
                                    return;
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (!TextUtils.equals(address, wallet.getAddress()) || !Arrays.equals(publicKey, wallet.getPublicKey())) {
                                        Arrays.fill(prepared.challengeAnswer, (byte) 0);
                                        finish.run(null, "Wallet changed. Open the request again.");
                                        return;
                                    }
                                    TL_wallet.inputTonConnectOauthSession result = new TL_wallet.inputTonConnectOauthSession();
                                    result.session_id = session.id;
                                    result.challenge_answer = prepared.challengeAnswer;
                                    result.body = prepared.body;
                                    result.proof = prepared.proof;
                                    finish.run(result, null);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    static boolean sameIcon(TLRPC.WebDocument first, TLRPC.WebDocument second) {
        return first == second || first != null && second != null
                && java.util.Objects.equals(first.url, second.url);
    }

    private boolean canSubmit(ConnectRequest context, TL_wallet.tonConnectSession approved, String address, byte[] publicKey) {
        return !context.session.closed && !context.session.closing
                && context.session.manifest_error == null && context.session.manifest != null
                && java.util.Objects.equals(context.session.manifest.url, approved.manifest.url)
                && java.util.Objects.equals(context.session.manifest.name, approved.manifest.name)
                && sameIcon(context.session.manifest.icon, approved.manifest.icon)
                && Arrays.equals(context.session.nonce, approved.nonce)
                && java.util.Objects.equals(address, wallet.getAddress()) && Arrays.equals(publicKey, wallet.getPublicKey());
    }


    private static final class Prepared {
        final String clientId;
        final byte[] challengeAnswer, body;
        TL_wallet.walletOwnershipProof proof;
        Prepared(JSONObject json) throws JSONException {
            clientId = json.getString("clientId");
            challengeAnswer = json.has("challengeAnswer") ? Base64.decode(json.getString("challengeAnswer"), Base64.NO_WRAP) : null;
            body = json.has("body") ? Base64.decode(json.getString("body"), Base64.NO_WRAP) : null;
        }
    }

    private static String manifestHost(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Manifest URL must use HTTPS and include a host");
        }
        return uri.getHost().toLowerCase(java.util.Locale.ROOT);
    }

    private static Prepared prepareConnect(SecretPhrase phrase, TL_wallet.tonConnectSession session,
            String address, byte[] publicKey, String requestJson, String manifestUrl, String embedded,
            TL_wallet.tonConnectChallenge challenge, int now) throws JSONException {
        JSONObject request = new JSONObject(requestJson);
        if (!manifestUrl.equals(request.getString("manifestUrl"))) throw new IllegalArgumentException("Manifest URL changed");
        String domain = manifestHost(manifestUrl);
        if (session.manifest == null || !domain.equals(manifestHost(session.manifest.url))) {
            throw new IllegalArgumentException("Manifest domain does not match the connection request");
        }
        return prepareConnect(phrase, session, address, publicKey, request.getJSONArray("items"), domain,
                embedded, challenge, now);
    }

    private static Prepared prepareConnect(SecretPhrase phrase, TL_wallet.tonConnectSession session,
            String address, byte[] publicKey, JSONArray requested, String domain, String embedded,
            TL_wallet.tonConnectChallenge challenge, int now) throws JSONException {
        if (requested.length() == 0) throw new IllegalArgumentException("Empty connect request");
        JSONObject parameters = new JSONObject().put("account", challenge != null);
        HashSet<String> names = new HashSet<>();
        for (int i = 0; i < requested.length(); i++) {
            JSONObject item = requested.getJSONObject(i);
            if (!names.add(item.getString("name"))) throw new IllegalArgumentException("Duplicate connect item");
            if ("ton_addr".equals(item.getString("name")) && item.has("network") && !"-239".equals(item.getString("network"))) {
                throw new IllegalArgumentException("Requested network does not match the wallet network");
            }
            if ("ton_proof".equals(item.getString("name"))) {
                if (domain.replaceFirst("\\.$", "").equals("telegram.org")) {
                    throw new IllegalArgumentException("The telegram.org proof domain is reserved for wallet import");
                }
                item.getString("payload");
            }
        }
        if (!names.contains("ton_addr")) throw new IllegalArgumentException("Missing ton_addr item");
        if (challenge != null) parameters.put("challenge", Base64.encodeToString(challenge.challenge, Base64.NO_WRAP));
        JSONObject crypto = WalletEngine2.tonConnectCrypto(phrase, session, address, publicKey, parameters);
        if (challenge == null) return new Prepared(crypto);
        if (challenge.event_id < 0) throw new IllegalArgumentException("Invalid connect event ID");
        JSONArray items = new JSONArray();
        TL_wallet.walletOwnershipProof proof = null;
        for (int i = 0; i < requested.length(); i++) {
            JSONObject item = requested.getJSONObject(i);
            String name = item.getString("name");
            if ("ton_addr".equals(name)) {
                items.put(crypto.getJSONObject("account"));
            } else if ("ton_proof".equals(name)) {
                String payload = item.getString("payload");
                JSONObject signed = WalletEngine2.tonConnectCrypto(phrase, session, address, publicKey, new JSONObject()
                        .put("proofDomain", domain).put("proofPayload", payload).put("timestamp", now));
                proof = new TL_wallet.walletOwnershipProof();
                proof.timestamp = now;
                proof.signature = Base64.decode(signed.getString("signature"), Base64.NO_WRAP);
                items.put(new JSONObject().put("name", name).put("proof", new JSONObject()
                        .put("timestamp", now).put("domain", new JSONObject().put("lengthBytes", domain.getBytes(StandardCharsets.UTF_8).length).put("value", domain))
                        .put("payload", payload).put("signature", signed.getString("signature"))));
            } else {
                items.put(new JSONObject().put("name", name).put("error", protocolError(400, "Method is not supported")));
            }
        }
        JSONObject device = new JSONObject().put("platform", "android").put("appName", "gramwallet")
                .put("appVersion", BuildVars.BUILD_VERSION_STRING).put("maxProtocolVersion", 2)
                .put("features", supportedFeatures());
        JSONObject event = new JSONObject().put("event", "connect").put("id", challenge.event_id)
                .put("payload", new JSONObject().put("items", items).put("device", device));
        if (embedded != null) event.put("response", new JSONObject().put("error", protocolError(400, "Embedded request is not supported")));
        JSONObject encrypted = WalletEngine2.tonConnectCrypto(phrase, session, address, publicKey, new JSONObject().put("encrypt", event));
        encrypted.put("challengeAnswer", crypto.getString("challengeAnswer"));
        Prepared prepared = new Prepared(encrypted);
        prepared.proof = proof;
        return prepared;
    }

    static JSONArray supportedFeatures() throws JSONException {
        return new JSONArray()
                .put(new JSONObject().put("name", "SendTransaction").put("maxMessages", 255))
                .put(new JSONObject().put("name", "SignMessage").put("maxMessages", 255))
                .put(new JSONObject().put("name", "SignData").put("types", new JSONArray().put("text").put("binary").put("cell")));
    }

    private static JSONObject protocolError(int code, String message) throws JSONException {
        return new JSONObject().put("code", code).put("message", message);
    }

    private static Prepared prepareDisconnect(SecretPhrase phrase, TL_wallet.tonConnectSession session,
            String address, byte[] publicKey, long eventId) throws JSONException {
        if (eventId < 0 || session.client_id == null) throw new IllegalArgumentException("Invalid disconnect session or event ID");
        JSONObject event = new JSONObject().put("event", "disconnect").put("id", eventId).put("payload", new JSONObject());
        return new Prepared(WalletEngine2.tonConnectCrypto(phrase, session, address, publicKey, new JSONObject().put("encrypt", event)));
    }

    private static JSONObject decodeRequest(SecretPhrase phrase, TL_wallet.tonConnectSession session,
            String address, byte[] publicKey, byte[] body, int now) throws JSONException {
        if (session.client_id == null) throw new IllegalArgumentException("Missing registered session key");
        JSONObject parameters = new JSONObject().put("decrypt", Base64.encodeToString(body, Base64.NO_WRAP));
        JSONObject crypto = WalletEngine2.tonConnectCrypto(phrase, session, address, publicKey, parameters);
        return parseRequest(crypto.getString("plaintext"), session, address, now);
    }

    static JSONObject parseRequest(String plaintext, TL_wallet.tonConnectSession session, String address, int now) throws JSONException {
        JSONObject decoded = new JSONObject(plaintext);
        Object id = decoded.get("id");
        if (!(id instanceof String) || !((String) id).matches("[\\x20-\\x7e]{1,100}")) {
            throw new IllegalArgumentException("Request ID must contain 1–100 printable ASCII characters");
        }
        try {
            String method = decoded.getString("method");
            JSONArray params = decoded.getJSONArray("params");
            if ("disconnect".equals(method)) {
                if (params.length() != 0) throw new IllegalArgumentException("Disconnect takes no parameters");
            } else if ("sendTransaction".equals(method) || "signMessage".equals(method)) {
                if (params.length() != 1 || !(params.get(0) instanceof String)) throw new IllegalArgumentException("Expected one JSON transaction parameter");
                JSONObject transaction = new JSONObject(params.getString(0));
                validateTransaction(transaction, address, now);
                decoded.put("transaction", transaction);
            } else if ("signData".equals(method)) {
                if (params.length() != 1 || !(params.get(0) instanceof String)) throw new IllegalArgumentException("Expected one JSON data parameter");
                JSONObject payload = new JSONObject(params.getString(0));
                validateSignData(payload, address);
                decoded.put("data", payload);
            } else {
                decoded.put("errorCode", 400).put("errorMessage", "Method is not supported");
            }
        } catch (Exception e) {
            decoded.put("errorCode", 1).put("errorMessage", e.getMessage());
        }
        if (session.closed || session.closing || session.pending || session.manifest == null) decoded.put("errorCode", 100).put("errorMessage", "Unknown app");
        return decoded;
    }

    private static void validateTransaction(JSONObject transaction, String address, int now) throws JSONException {
        validateSigningAccount(transaction, address);
        if (transaction.has("valid_until")) {
            Object expiration = transaction.get("valid_until");
            if (!(expiration instanceof Number) || ((Number) expiration).doubleValue() != ((Number) expiration).longValue()
                    || ((Number) expiration).longValue() <= now || ((Number) expiration).longValue() > 0xffffffffL) {
                throw new IllegalArgumentException("Invalid or expired transaction");
            }
        }
        if (transaction.has("items")) throw new IllegalArgumentException("Structured transactions are not supported");
        new Transaction(transaction);
    }

    private static String requiredString(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be a string");
        return (String) value;
    }

    private static void validateSigningAccount(JSONObject payload, String address) throws JSONException {
        if (payload.has("network") && !"-239".equals(requiredString(payload, "network"))) throw new IllegalArgumentException("Wrong network");
        if (payload.has("from") && !WalletEngine2.sameTonConnectAddress(requiredString(payload, "from"), address)) throw new IllegalArgumentException("Wrong signer");
    }

    private static void validateSignData(JSONObject payload, String address) throws JSONException {
        validateSigningAccount(payload, address);
        switch (requiredString(payload, "type")) {
            case "text":
                requiredString(payload, "text");
                break;
            case "binary":
                String bytes = requiredString(payload, "bytes");
                if (!bytes.matches("[A-Za-z0-9+/]*={0,2}") || !Base64.encodeToString(Base64.decode(bytes, Base64.NO_WRAP), Base64.NO_WRAP)
                        .replace("=", "").equals(bytes.replace("=", ""))) throw new IllegalArgumentException("Invalid base64 data");
                break;
            case "cell":
                requiredString(payload, "schema");
                if (!WalletEngine2.isValidCellBoc(requiredString(payload, "cell"))) throw new IllegalArgumentException("Invalid data cell");
                break;
            default:
                throw new IllegalArgumentException("Unknown signing data type");
        }
    }

    public static final class Pending {
        public final TL_wallet.tonConnectSession session;
        public final int messageId, expires;
        public final String appRequestId, method;
        public final Transaction transaction;
        private final byte[] body, publicKey;
        private final String address, traceId, errorMessage, data;
        private final int errorCode;
        private boolean busy, processed, claimed, previewReady, invalidated;
        private Runnable onProcessed;

        private Pending(TL_wallet.tonConnectPending pending, TL_wallet.tonConnectRequest request,
                JSONObject decoded, String address, byte[] publicKey) throws JSONException {
            session = pending.session;
            messageId = request.msg_id;
            expires = request.expires;
            body = request.body.clone();
            traceId = request.trace_id;
            appRequestId = decoded.getString("id");
            method = decoded.optString("method");
            errorCode = decoded.optInt("errorCode", -1);
            errorMessage = decoded.optString("errorMessage", null);
            transaction = errorCode < 0 && decoded.has("transaction") ? new Transaction(decoded.getJSONObject("transaction")) : null;
            data = errorCode < 0 && decoded.has("data") ? decoded.getJSONObject("data").toString() : null;
            this.address = address;
            this.publicKey = publicKey.clone();
        }
        public boolean isProcessed() { return processed; }
        public boolean isBusy() { return busy; }
        public void setOnProcessed(Runnable callback) { onProcessed = callback; }
    }

    private final ConnectionsManager connections;
    // Only the currently displayed sheet. Pending requests and claims belong to the server.
    private Pending activeRequest;
    private boolean loadingRequest;

    private String key(long session, long message) { return "tonconnect_reply." + session + "." + message; }
    private boolean expired(Pending request) {
        long now = connections.getCurrentTime();
        return request.expires <= now || request.transaction != null
                && request.transaction.validUntil != 0 && request.transaction.validUntil <= now;
    }
    private boolean sameWallet(Pending request) {
        return !request.invalidated && Objects.equals(request.address, wallet.getAddress()) && Arrays.equals(request.publicKey, wallet.getPublicKey());
    }

    public void load(long sessionId, int messageId, Utilities.Callback2<Pending, String> callback) {
        if (loadingRequest || activeRequest != null) {
            callback.run(null, "A TON Connect request is already open");
            return;
        }
        String savedReply = MessagesController.getMainSettings(currentAccount).getString(key(sessionId, messageId), null);
        if (savedReply != null) {
            loadingRequest = true;
            retryResponse(sessionId, messageId, savedReply, error -> {
                loadingRequest = false;
                callback.run(null, error);
            });
            return;
        }
        loadingRequest = true;
        wallet.whenReady(() -> {
            TL_wallet.tonConnectGetPending get = new TL_wallet.tonConnectGetPending();
            get.session_id = sessionId;
            connections.sendRequestTyped(get, AndroidUtilities::runOnUIThread, (pending, error) -> {
                if (error != null || pending == null) {
                    loadingRequest = false;
                    callback.run(null, rpc("getPending", error));
                    return;
                }
                TL_wallet.tonConnectRequest found = null;
                for (TL_wallet.tonConnectRequest request : pending.requests) {
                    if (request.session_id == sessionId && request.msg_id == messageId) { found = request; break; }
                }
                if (found == null || pending.session == null || pending.session.id != sessionId || found.expires <= connections.getCurrentTime()) {
                    loadingRequest = false;
                    callback.run(null, "This request has expired or has already been answered");
                    return;
                }
                final TL_wallet.tonConnectRequest request = found;
                final String address = wallet.getAddress();
                final byte[] publicKey = wallet.getPublicKey() == null ? null : wallet.getPublicKey().clone();
                wallet.getSecretPhrase(true, false, (borrowed, secretError) -> {
                    if (borrowed == null || secretError != null) {
                        loadingRequest = false;
                        callback.run(null, secretError == null ? "Recovery phrase is unavailable" : secretError);
                        return;
                    }
                    final SecretPhrase phrase = borrowed.copy();
                    final Utilities.Callback2<Pending, String> finishPhrase = (value, failure) -> {
                        phrase.close();
                        callback.run(value, failure);
                    };
                    Utilities.globalQueue.postRunnable(() -> {
                        Pending decoded = null;
                        String failure = null;
                        try {
                            decoded = new Pending(pending, request, decodeRequest(phrase, pending.session, address, publicKey,
                                    request.body, connections.getCurrentTime()), address, publicKey);
                        } catch (Exception e) { failure = exception("decode request", e); }
                        final Pending loaded = decoded;
                        final String decodeError = failure;
                        AndroidUtilities.runOnUIThread(() -> {
                            loadingRequest = false;
                            if (loaded == null) { finishPhrase.run(null, decodeError); return; }
                            if (!sameWallet(loaded) || expired(loaded)) { finishPhrase.run(null, "Request expired or wallet changed"); return; }
                            activeRequest = loaded;
                            if ("sendTransaction".equals(loaded.method) && loaded.transaction != null && MessagesController.getMainSettings(currentAccount).contains(key(sessionId, messageId) + ".transfer")) {
                                loaded.busy = true;
                                sendTransaction(loaded, phrase, result -> {
                                    loaded.busy = false;
                                    release(loaded);
                                    finishPhrase.run(null, result);
                                });
                            } else if (loaded.errorCode >= 0 || "disconnect".equals(loaded.method)) {
                                phrase.close();
                                decide(loaded, false, result -> {
                                    release(loaded);
                                    finishPhrase.run(null, result);
                                });
                            } else {
                                finishPhrase.run(loaded, null);
                            }
                        });
                    });
                });
            });
        });
    }

    public void release(Pending request) {
        request.onProcessed = null;
        if (!request.busy && activeRequest == request) activeRequest = null;
    }

    public void markProcessed(long sessionId, int messageId) {
        Pending request = activeRequest;
        if (request != null && request.session.id == sessionId && request.messageId == messageId && !request.busy) {
            request.processed = true;
            if (request.onProcessed != null) request.onProcessed.run();
        }
    }

    private void updateOpenRequests(TL_wallet.tonConnectSession session) {
        Pending request = activeRequest;
        if (request == null || request.session.id != session.id) return;
        if (session.closed || session.closing || !Objects.equals(session.client_id, request.session.client_id)
                || !Arrays.equals(session.nonce, request.session.nonce)
                || session.manifest == null || request.session.manifest == null
                || !Objects.equals(session.manifest.url, request.session.manifest.url)
                || !Objects.equals(session.manifest.name, request.session.manifest.name)
                || !sameIcon(session.manifest.icon, request.session.manifest.icon)) {
            request.invalidated = true;
            markProcessed(session.id, request.messageId);
        }
    }

    public void preview(Pending request, Utilities.Callback2<TL_wallet.walletTransaction, String> callback) {
        WalletEngine2 engine = wallet.tonConnectEngine();
        if (request.processed || expired(request) || !sameWallet(request) || engine == null) {
            callback.run(null, "Request expired, was processed, or the wallet changed"); return;
        }
        if ("signMessage".equals(request.method)) {
            engine.previewSignMessage(request.transaction, failure -> {
                request.previewReady = !request.processed && !expired(request) && sameWallet(request) && failure == null;
                callback.run(null, failure);
            });
            return;
        }
        engine.previewTonConnect(request.transaction, (preview, failure) -> {
            request.previewReady = !request.processed && !expired(request) && sameWallet(request)
                    && preview != null && preview.traceSucceeded && !preview.traceIncomplete;
            callback.run(preview, failure);
        });
    }

    public void decide(Pending request, boolean declined, Utilities.Callback<String> callback) {
        if (request.busy) return;
        if (request.processed || expired(request) || !sameWallet(request)
                || !declined && request.errorCode < 0 && !"disconnect".equals(request.method) && !request.previewReady) {
            callback.run("Request expired, was processed, or the wallet changed"); return;
        }
        request.busy = true;
        Utilities.Callback<String> finish = error -> {
            request.busy = false;
            if (request.onProcessed == null && activeRequest == request) activeRequest = null;
            callback.run(error);
        };
        String saved = MessagesController.getMainSettings(currentAccount).getString(key(request.session.id, request.messageId), null);
        if (saved != null) {
            retryResponse(request.session.id, request.messageId, saved, finish);
            return;
        }
        wallet.getSecretPhrase(true, false, (borrowed, secretError) -> {
            if (borrowed == null || secretError != null) {
                finish.run(secretError == null ? "Recovery phrase is unavailable" : secretError); return;
            }
            final SecretPhrase phrase = borrowed.copy();
            final Utilities.Callback<String> finishPhrase = failure -> {
                phrase.close();
                finish.run(failure);
            };
            if ("sendTransaction".equals(request.method) && MessagesController.getMainSettings(currentAccount).contains(key(request.session.id, request.messageId) + ".transfer")) {
                sendTransaction(request, phrase, finishPhrase);
                return;
            }
            Utilities.Callback<byte[]> claim = answer -> {
                if (expired(request) || !sameWallet(request)) {
                    if (answer != null) Arrays.fill(answer, (byte) 0);
                    finishPhrase.run("Request expired or wallet changed"); return;
                }
                TL_wallet.tonConnectClaimRequest rpc = new TL_wallet.tonConnectClaimRequest();
                rpc.session_id = request.session.id;
                rpc.msg_id = request.messageId;
                rpc.app_request_id = request.appRequestId;
                rpc.declined = declined;
                rpc.challenge_answer = answer;
                connections.sendRequestTyped(rpc, AndroidUtilities::runOnUIThread, (claimed, claimError) -> {
                    if (answer != null) Arrays.fill(answer, (byte) 0);
                    if (claimError != null || !(claimed instanceof TLRPC.TL_boolTrue)) {
                        if (claimError != null && ("TONCONNECT_REQUEST_ALREADY_CLAIMED".equals(claimError.text)
                                || "TONCONNECT_REQUEST_EXPIRED".equals(claimError.text) || "TONCONNECT_REQUEST_NOT_FOUND".equals(claimError.text))) request.processed = true;
                        finishPhrase.run(rpc("claimRequest", claimError)); return;
                    }
                    request.claimed = true;
                    if (declined || request.errorCode >= 0) {
                        respond(request, phrase, null, declined ? 300 : request.errorCode, declined ? "User declined the request" : request.errorMessage, finishPhrase);
                    } else if ("disconnect".equals(request.method)) {
                        respond(request, phrase, new JSONObject(), -1, null, finishPhrase);
                    } else if ("signData".equals(request.method)) {
                        signData(request, phrase, finishPhrase);
                    } else if ("signMessage".equals(request.method)) {
                        signMessage(request, phrase, finishPhrase);
                    } else {
                        sendTransaction(request, phrase, finishPhrase);
                    }
                });
            };
            if (request.session.closed) {
                claim.run(null);
                return;
            }
            TL_wallet.tonConnectRegisterKey register = new TL_wallet.tonConnectRegisterKey();
            register.session_id = request.session.id;
            register.client_id = request.session.client_id;
            connections.sendRequestTyped(register, AndroidUtilities::runOnUIThread, (challenge, error) -> {
                if (challenge == null || error != null) { finishPhrase.run(rpc("registerKey", error)); return; }
                Utilities.globalQueue.postRunnable(() -> {
                    byte[] answer = null;
                    String failure = null;
                    try {
                        JSONObject crypto = WalletEngine2.tonConnectCrypto(phrase, request.session, request.address, request.publicKey,
                                new JSONObject().put("challenge", Base64.encodeToString(challenge.challenge, Base64.NO_WRAP)));
                        answer = Base64.decode(crypto.getString("challengeAnswer"), Base64.NO_WRAP);
                    } catch (Exception e) { failure = exception("decrypt challenge", e); }
                    final byte[] result = answer;
                    final String cryptoError = failure;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (result == null) finishPhrase.run(cryptoError);
                        else claim.run(result);
                    });
                });
            });
        });
    }

    private void signData(Pending request, SecretPhrase phrase, Utilities.Callback<String> callback) {
        if (!sameWallet(request) || expired(request)) { respond(request, phrase, null, 0, "Request expired or wallet changed", callback); return; }
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject signed = null;
            String error = null;
            try {
                signed = WalletEngine2.signData(phrase, request.session, request.address, request.publicKey,
                        new JSONObject(request.data), manifestHost(request.session.manifest.url), connections.getCurrentTime());
            } catch (Exception e) { error = exception("sign data", e); }
            final JSONObject result = signed;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (!sameWallet(request) || expired(request)) respond(request, phrase, null, 0, "Request expired or wallet changed", callback);
                else respond(request, phrase, result, result == null ? 0 : -1, failure, callback);
            });
        });
    }

    private void signMessage(Pending request, SecretPhrase phrase, Utilities.Callback<String> callback) {
        WalletEngine2 engine = wallet.tonConnectEngine();
        if (engine == null || !sameWallet(request) || expired(request)) {
            respond(request, phrase, null, 0, "Wallet unavailable or request expired", callback); return;
        }
        engine.signMessage(phrase, request.transaction, "ton-connect:" + request.session.id + ":" + request.messageId, (boc, error) -> {
            try {
                if (boc == null || !sameWallet(request) || expired(request)) {
                    respond(request, phrase, null, 0, error != null ? error : "Request expired or wallet changed", callback);
                } else {
                    respond(request, phrase, new JSONObject().put("internalBoc", boc), -1, null, callback);
                }
            } catch (JSONException e) { respond(request, phrase, null, 0, exception("sign message response", e), callback); }
        });
    }

    private void sendTransaction(Pending request, SecretPhrase phrase, Utilities.Callback<String> callback) {
        WalletEngine2 engine = wallet.tonConnectEngine();
        if (engine == null || !sameWallet(request) || expired(request)) {
            respond(request, phrase, null, 0, "Wallet unavailable or request expired", callback); return;
        }
        String saved = MessagesController.getMainSettings(currentAccount).getString(key(request.session.id, request.messageId) + ".transfer", null);
        if (saved != null) {
            try {
                JSONObject data = new JSONObject(saved);
                TL_wallet.sendTransfer transfer = new TL_wallet.sendTransfer();
                transfer.user_id = new TLRPC.TL_inputUserEmpty();
                transfer.data_normal = Base64.decode(data.getString("boc"), Base64.NO_WRAP);
                transfer.random_id = data.getLong("randomId");
                broadcast(request, phrase, transfer, callback);
            } catch (Exception e) { callback.run(exception("restore transfer", e)); }
            return;
        }
        engine.prepareTonConnectTransfer(phrase, request.transaction,
                "ton-connect:" + request.session.id + ":" + request.messageId, (transfer, error) -> {
            if (transfer == null || error != null || !sameWallet(request) || expired(request)) {
                respond(request, phrase, null, 0, error != null ? error : "Transfer unavailable or request expired", callback); return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                String failure = null;
                try {
                    // Retry exactly these signed bytes and this MTProto random ID after a restart.
                    String data = new JSONObject().put("boc", Base64.encodeToString(transfer.data_normal, Base64.NO_WRAP))
                            .put("randomId", transfer.random_id).toString();
                    if (!MessagesController.getMainSettings(currentAccount).edit().putString(key(request.session.id, request.messageId) + ".transfer", data).commit()) {
                        throw new IllegalStateException("Could not save signed transfer");
                    }
                } catch (Exception e) { failure = exception("save transfer", e); }
                final String saveError = failure;
                AndroidUtilities.runOnUIThread(() -> {
                    if (saveError != null) callback.run(saveError);
                    else if (!sameWallet(request) || expired(request)) callback.run("Request expired or wallet changed");
                    else broadcast(request, phrase, transfer, callback);
                });
            });
        });
    }

    private void broadcast(Pending request, SecretPhrase phrase, TL_wallet.sendTransfer transfer, Utilities.Callback<String> callback) {
        connections.sendRequestTyped(transfer, AndroidUtilities::runOnUIThread, (updates, error) -> {
            boolean sent = false;
            if (updates != null) {
                sent = updates.update instanceof TL_update.TL_updateSentWalletTransaction;
                for (TLRPC.Update update : updates.updates) sent |= update instanceof TL_update.TL_updateSentWalletTransaction;
                Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processUpdates(updates, false));
            }
            if (error != null && "WALLET_KEY_MISMATCH".equals(error.text)) wallet.invalidateState();
            if (error == null && !sent || error != null && (error.code < 0 || error.code >= 500)) {
                // Submission is uncertain: retain the prepared transfer for an idempotent retry.
                callback.run(rpc("sendTransfer", error));
                return;
            }
            respond(request, phrase, error == null ? Base64.encodeToString(transfer.data_normal, Base64.NO_WRAP) : null,
                    error == null ? -1 : 0, error == null ? null : rpc("sendTransfer", error), callback);
        });
    }

    private void respond(Pending request, SecretPhrase phrase, Object result, int code, String message, Utilities.Callback<String> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject saved = null;
            String failure = null;
            try {
                JSONObject response = new JSONObject().put("id", request.appRequestId);
                if (code < 0) response.put("result", result);
                else response.put("error", protocolError(code, message == null ? "Could not complete request" : message));
                JSONObject crypto = WalletEngine2.tonConnectCrypto(phrase, request.session, request.address, request.publicKey, new JSONObject().put("encrypt", response));
                saved = new JSONObject().put("body", crypto.getString("body")).put("traceId", request.traceId)
                        .put("disconnect", code < 0 && "disconnect".equals(request.method));
                // A declined request is absent from getPending: retain the reply's routing fields too.
                if (!MessagesController.getMainSettings(currentAccount).edit().putString(key(request.session.id, request.messageId), saved.toString()).commit()) {
                    throw new IllegalStateException("Could not save TON Connect response");
                }
            } catch (Exception e) { failure = exception("encrypt response", e); saved = null; }
            final JSONObject reply = saved;
            final String error = failure;
            AndroidUtilities.runOnUIThread(() -> {
                if (reply == null) callback.run(error);
                else submitResponse(request.session.id, request.messageId, reply, callback);
            });
        });
    }

    private void retryResponse(long sessionId, int messageId, String saved, Utilities.Callback<String> callback) {
        try {
            submitResponse(sessionId, messageId, new JSONObject(saved), callback);
        } catch (Exception e) { callback.run(exception("restore response", e)); }
    }

    private void submitResponse(long sessionId, int messageId, JSONObject reply, Utilities.Callback<String> callback) {
        TL_wallet.tonConnectSubmitResponse submit = new TL_wallet.tonConnectSubmitResponse();
        submit.session_id = sessionId;
        submit.msg_id = messageId;
        submit.trace_id = reply.optString("traceId", null);
        submit.body = Base64.decode(reply.optString("body"), Base64.NO_WRAP);
        connections.sendRequestTyped(submit, AndroidUtilities::runOnUIThread, (result, error) -> {
            if (error != null || !(result instanceof TLRPC.TL_boolTrue)) { callback.run(rpc("submitResponse", error)); return; }
            if (reply.optBoolean("disconnect")) {
                TL_wallet.tonConnectCloseSession close = new TL_wallet.tonConnectCloseSession();
                close.session_id = sessionId;
                connections.sendRequestTyped(close, AndroidUtilities::runOnUIThread, (closed, closeError) -> {
                    if (closeError != null || !(closed instanceof TLRPC.TL_boolTrue)) callback.run(rpc("closeSession", closeError));
                    else completeResponse(sessionId, messageId, callback);
                });
            } else completeResponse(sessionId, messageId, callback);
        });
    }

    private void completeResponse(long sessionId, int messageId, Utilities.Callback<String> callback) {
        MessagesController.getMainSettings(currentAccount).edit().remove(key(sessionId, messageId))
                .remove(key(sessionId, messageId) + ".transfer").apply();
        if (activeRequest != null && activeRequest.session.id == sessionId && activeRequest.messageId == messageId) activeRequest.processed = true;
        callback.run(null);
    }

    public static final class Transaction {
        public static final class Message {
            public final String address, payload, stateInit, comment;
            public final long amount;
            public final boolean bounce;
            private Message(JSONObject json) throws JSONException {
                address = json.getString("address");
                if (address.contains(":") || !WalletEngine2.isValidAddress(address)) throw new IllegalArgumentException("Expected a friendly destination address");
                bounce = (Base64.decode(address.replace('-', '+').replace('_', '/'), Base64.NO_WRAP)[0] & 0x7f) == 0x11;
                Object amountValue = json.get("amount");
                if (!(amountValue instanceof String) || !((String) amountValue).matches("[0-9]+")) throw new IllegalArgumentException("Invalid transfer amount");
                amount = Long.parseLong((String) amountValue);
                if (json.has("extra_currency") && json.getJSONObject("extra_currency").length() != 0) throw new IllegalArgumentException("Extra currencies are not supported");
                if (amount < 0) throw new IllegalArgumentException("Transfer amount must not be negative");
                payload = json.has("payload") ? json.getString("payload") : null;
                stateInit = json.has("stateInit") ? json.getString("stateInit") : null;
                if (payload != null && !WalletEngine2.isValidCellBoc(payload) || stateInit != null && !WalletEngine2.isValidCellBoc(stateInit)) {
                    throw new IllegalArgumentException("Invalid transaction cell");
                }
                comment = WalletEngine2.textCommentFromBody(payload);
            }
        }
        public final long validUntil;
        public final List<Message> messages;
        public final long total;

        Transaction(JSONObject json) throws JSONException {
            validUntil = json.optLong("valid_until", 0);
            JSONArray array = json.getJSONArray("messages");
            if (array.length() == 0 || array.length() > 255 || validUntil < 0) throw new JSONException("Invalid transaction");
            ArrayList<Message> values = new ArrayList<>();
            long sum = 0;
            for (int i = 0; i < array.length(); i++) {
                Message message = new Message(array.getJSONObject(i));
                sum = Math.addExact(sum, message.amount);
                values.add(message);
            }
            total = sum;
            messages = Collections.unmodifiableList(values);
        }


        String[] fields() {
            String[] result = new String[messages.size() * 5];
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                result[i * 5] = m.address;
                result[i * 5 + 1] = Long.toString(m.amount);
                result[i * 5 + 2] = m.payload;
                result[i * 5 + 3] = m.stateInit;
                result[i * 5 + 4] = m.bounce ? "1" : "0";
            }
            return result;
        }
    }

    static String exception(String operation, Throwable error) {
        FileLog.e("[gram-wallet] TON Connect " + operation, error);
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    static String rpc(String operation, TLRPC.TL_error error) {
        String message = error == null ? operation + " returned an empty or unsuccessful response"
                : error.text == null ? "RPC error " + error.code : error.text;
        FileLog.e("[gram-wallet] TON Connect " + operation + ": "
                + (error == null ? "" : "code=" + error.code + " ") + message);
        return message;
    }

    static void log(String operation, String message) {
        if (message != null) FileLog.e("[gram-wallet] TON Connect " + operation + ": " + message);
    }



    public static BottomSheet showConnectSheet(
        Context context,
        int currentAccount,
        ConnectRequest connection,
        Theme.ResourcesProvider resourcesProvider,
        Runnable onComplete
    ) {
        return showConnectSheet(context, currentAccount, connection, null, resourcesProvider, null, onComplete);
    }

    public static BottomSheet showOauthConnectSheet(Context context, int currentAccount,
            TLRPC.TL_urlAuthResultRequest auth, Theme.ResourcesProvider resourcesProvider,
            Utilities.Callback<TL_wallet.inputTonConnectOauthSession> onApproved, Runnable onCancel) {
        return showConnectSheet(context, currentAccount, null, auth, resourcesProvider, onApproved, onCancel);
    }

    private static BottomSheet showConnectSheet(Context context, int currentAccount, ConnectRequest connection,
            TLRPC.TL_urlAuthResultRequest auth, Theme.ResourcesProvider resourcesProvider,
            Utilities.Callback<TL_wallet.inputTonConnectOauthSession> onApproved, Runnable onComplete) {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        final boolean oauth = auth != null;
        final TL_wallet.tonConnectSession session = oauth ? null : connection.session;
        final TL_wallet.inputTonConnectOauthSession[] oauthResult = {null};
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout topLayout = new FrameLayout(context);

        final ImageView closeView = new ImageView(context);
        closeView.setImageResource(R.drawable.ic_ab_close);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        closeView.setContentDescription(LocaleController.getString(R.string.Close));
        ScaleStateListAnimator.apply(closeView);
        topLayout.addView(closeView, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 4, 0, 0, 0));

        final BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(38));
        if (oauth) {
            AvatarDrawable avatar = new AvatarDrawable();
            avatar.setInfo(auth.bot);
            avatarView.setForUserOrChat(auth.bot, avatar);
        } else if (session.manifest != null && session.manifest.icon != null) {
            avatarView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(session.manifest.icon)), "76_76", null, 0, session.manifest);
        }
        topLayout.addView(avatarView, LayoutHelper.createFrame(76, 76, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

        layout.addView(topLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        if (oauth) {
            titleView.setText(LocaleController.formatSpannable(R.string.WalletConnectToApp, UserObject.getUserName(auth.bot)));
        } else if (session.manifest != null) {
            titleView.setText(LocaleController.formatSpannable(R.string.WalletConnectToApp, session.manifest.name));
        } else {
            titleView.setText(LocaleController.getString(R.string.WalletConnectToDApp));
        }
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 16, 36, 0));

        String domain = oauth ? auth.domain : AndroidUtilities.getHostAuthority(connection.manifestUrl);
        if (!TextUtils.isEmpty(domain)) {
            final TextView linkView = domainView(context, domain, resourcesProvider);
            layout.addView(linkView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 6, 36, 0));
        }

        final TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(oauth || wallet.tonConnect.requestsProof(connection)
            ? LocaleController.getString(R.string.WalletConnectProofInfo)
            : LocaleController.getString(R.string.WalletConnectInfo));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 16, 36, 28));

        final FrameLayout cardContainer = new FrameLayout(context);
        layout.addView(cardContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -12, 0, -12));

        final WalletCardComponent card = new WalletCardComponent(context, false);
        card.update(wallet);
        card.listen(currentAccount, wallet);
        cardContainer.addView(card);

        final TextView footerView = new TextView(context);
        footerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        footerView.setGravity(Gravity.CENTER);
        footerView.setText(LocaleController.getString(R.string.WalletConnectPermissionInfo));
        footerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        layout.addView(footerView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 16, 36, 28));

        final LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(dp(16), 0, dp(16), 0);
        layout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        final ButtonWithCounterView cancel = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        cancel.setText(LocaleController.getString(R.string.WalletCancel));
        buttonsLayout.addView(cancel, LayoutHelper.createLinear(0, 44, 1.0f, 0, 0, 6, 0));

        final ButtonWithCounterView connect = new ButtonWithCounterView(context, resourcesProvider).setRound();
        connect.setText(LocaleController.getString(R.string.WalletConnect));
        buttonsLayout.addView(connect, LayoutHelper.createLinear(0, 44, 1.0f, 6, 0, 0, 0));

        final NewBottomSheet sheet = new NewBottomSheet(context, layout, resourcesProvider);
        sheet.setDelegate(new BottomSheet.BottomSheetDelegate() {
            @Override public boolean canDismiss() {
                return !connect.isLoading();
            }
        });
        Runnable updateManifest = () -> {
            if (oauth) {
                connect.setEnabled(wallet.isReady() && wallet.getAddress() != null && wallet.getPublicKey() != null && !connect.isLoading());
                return;
            }
            for (TL_wallet.tonConnectSession updated : wallet.tonConnect.sessions) {
                if (updated.id == session.id) { connection.session = updated; break; }
            }
            TL_wallet.tonConnectSession current = connection.session;
            boolean ready = current.manifest != null && current.manifest_error == null && !current.closed && !current.closing;
            connect.setEnabled(ready && !connection.busy);
            if (current.manifest != null) {
                titleView.setText(LocaleController.formatSpannable(R.string.WalletConnectToApp, current.manifest.name));
                if (current.manifest.icon != null) {
                    avatarView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(current.manifest.icon)), "76_76", null, 0, current.manifest);
                }
            }
            if (current.manifest_error != null) {
                subtitleView.setText(LocaleController.formatString(R.string.WalletTonConnectManifestLoadFailed, current.manifest_error));
            } else if (!ready) {
                subtitleView.setText(current.closed || current.closing ? LocaleController.getString(R.string.WalletTonConnectSessionClosed) : LocaleController.getString(R.string.Loading));
            } else {
                subtitleView.setText(LocaleController.getString(wallet.tonConnect.requestsProof(connection)
                        ? R.string.WalletConnectProofInfo : R.string.WalletConnectInfo));
            }
        };
        NotificationCenter.NotificationCenterDelegate observer = (id, account, args) -> {
            if (!oauth && args.length > 1 && args[1] instanceof TL_wallet.tonConnectSession) {
                TL_wallet.tonConnectSession updated = (TL_wallet.tonConnectSession) args[1];
                if (updated.id == session.id) connection.session = updated;
            }
            updateManifest.run();
        };
        NotificationCenter.getInstance(currentAccount).addObserver(observer, NotificationCenter.walletUpdate);
        updateManifest.run();
        final boolean[] returned = {false};
        sheet.setOnDismissListener(dialog -> {
            NotificationCenter.getInstance(currentAccount).removeObserver(observer, NotificationCenter.walletUpdate);
            if (!returned[0]) {
                returned[0] = true;
                if (oauth && oauthResult[0] != null) {
                    onApproved.run(oauthResult[0]);
                } else if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        sheet.show();

        closeView.setOnClickListener(v -> {
            sheet.dismiss();
        });
        cancel.setOnClickListener(v -> {
            if (connect.isLoading()) return;
            sheet.dismiss();
        });
        connect.setOnClickListener(v -> {
            if (connect.isLoading()) return;
            connect.setLoading(true);
            cancel.setEnabled(false);
            closeView.setEnabled(false);

            Utilities.Callback<String> finish = error -> {
                connect.setLoading(false);
                cancel.setEnabled(true);
                closeView.setEnabled(true);
                if (error == null) {
                    sheet.dismiss();
                } else {
                    BulletinFactory.showError(error, sheet.topBulletinContainer, resourcesProvider);
                    updateManifest.run();
                }
            };
            if (oauth) {
                String address = wallet.getAddress();
                byte[] publicKey = wallet.getPublicKey();
                wallet.tonConnect.prepareOauthSession(auth, address, publicKey == null ? null : publicKey.clone(), (result, error) -> {
                    if (error == null) {
                        oauthResult[0] = result;
                    }
                    finish.run(error);
                });
            } else {
                wallet.tonConnect.approveSession(connection, finish);
            }
        });

        return sheet;
    }

    public static void openTransactionRequest(Context context, int currentAccount, long sessionId, int messageId,
            Theme.ResourcesProvider resourcesProvider) {
        openTransactionRequest(context, currentAccount, sessionId, messageId, resourcesProvider, null);
    }

    public static void openTransactionRequest(Context context, int currentAccount, long sessionId, int messageId,
            Theme.ResourcesProvider resourcesProvider, Runnable onDismiss) {
        GramWallet wallet = GramWallet.getInstance(currentAccount);
        wallet.tonConnect.load(sessionId, messageId, (request, error) -> {
            if (request != null) {
                if (context instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) context;
                    if (activity.isFinishing() || activity.isDestroyed()
                            || !org.telegram.ui.LaunchActivity.isResumed
                            || org.telegram.messenger.SharedConfig.appLocked
                            || org.telegram.messenger.SharedConfig.isWaitingForPasscodeEnter
                            || org.telegram.messenger.UserConfig.selectedAccount != currentAccount) {
                        wallet.tonConnect.release(request);
                        return;
                    }
                }
                if ("signData".equals(request.method)) showSignDataSheet(context, currentAccount, request, resourcesProvider, onDismiss);
                else showTransactionSheet(context, currentAccount, request, resourcesProvider, onDismiss);
            }
            else if (error != null) {
                BulletinFactory.showError(error);
            }
        });
    }

    private static BottomSheet showSignDataSheet(Context context, int currentAccount, Pending request,
            Theme.ResourcesProvider resourcesProvider, Runnable onDismiss) {
        GramWalletTonConnect controller = GramWallet.getInstance(currentAccount).tonConnect;
        final JSONObject data;
        try { data = new JSONObject(request.data); }
        catch (JSONException e) {
            controller.release(request);
            BulletinFactory.showError(exception("display signing data", e));
            return null;
        }
        boolean text = "text".equals(data.optString("type"));
        NavBottomSheet sheet = new NavBottomSheet(context, resourcesProvider)
                .setBackgroundOverride(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.ic_ab_close);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        close.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
        close.setContentDescription(LocaleController.getString(R.string.Close));
        close.setOnClickListener(v -> { if (!request.isBusy()) sheet.dismiss(); });
        header.addView(close, LayoutHelper.createLinear(48, 48, 0f, Gravity.CENTER_VERTICAL, 4, 0, 12, 0));
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(22));
        if (request.session.manifest.icon != null) {
            avatar.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(request.session.manifest.icon)), "44_44", null, 0, request.session.manifest);
        }
        header.addView(avatar, LayoutHelper.createLinear(44, 44, 0f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = requestText(context, LocaleController.getString(R.string.WalletSignData), 18, resourcesProvider);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.LEFT);
        heading.addView(title, LayoutHelper.createLinear(-1, -2));
        TextView domain = requestText(context, AndroidUtilities.getHostAuthority(request.session.manifest.url), 14, resourcesProvider);
        domain.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        domain.setGravity(Gravity.LEFT);
        domain.setSingleLine(true);
        domain.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(domain, LayoutHelper.createLinear(-1, -2));
        header.addView(heading, LayoutHelper.createLinear(0, -2, 1f, 0, 0, 16, 0));
        layout.addView(header, LayoutHelper.createLinear(-1, 56));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        TextView content = requestText(context, text ? data.optString("text")
                : LocaleController.getString(R.string.WalletBinaryDataWarning), text ? 14 : 16, resourcesProvider);
        content.setGravity(Gravity.LEFT);
        content.setTextIsSelectable(text);
        if (text) {
            TextView label = requestText(context, LocaleController.getString(R.string.WalletSigningData), 14, resourcesProvider);
            label.setGravity(Gravity.LEFT);
            label.setTypeface(AndroidUtilities.bold());
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            card.addView(label, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 12));
            content.setTypeface(android.graphics.Typeface.MONOSPACE);
        } else {
            LinearLayout warning = new LinearLayout(context);
            warning.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = requestText(context, "!", 12, resourcesProvider);
            icon.setTypeface(AndroidUtilities.bold());
            icon.setTextColor(0xffffffff);
            icon.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_text_RedBold, resourcesProvider)));
            warning.addView(icon, LayoutHelper.createLinear(16, 16, 0f, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
            TextView label = requestText(context, LocaleController.getString(R.string.WalletBinaryData), 14, resourcesProvider);
            label.setGravity(Gravity.LEFT);
            label.setTypeface(AndroidUtilities.bold());
            warning.addView(label, LayoutHelper.createLinear(-1, -2));
            card.addView(warning, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 4));
        }
        card.addView(content, LayoutHelper.createLinear(-1, -2));
        if (text) {
            WalletAccessibility.setClickLabel(content, LocaleController.getString(R.string.Copy));
            content.setOnClickListener(v -> {
                if (AndroidUtilities.addToClipboard(data.optString("text"))) {
                    BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
                            .createCopyBulletin(LocaleController.getString(R.string.WalletSigningDataCopied)).show();
                }
            });
        }
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(heightMeasureSpec), dp(320)), MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(card);
        layout.addView(scroll, LayoutHelper.createLinear(-1, -2, 12, 4, 12, 0));
        NavBottomSheet.Page page = new NavBottomSheet.Page(context);
        page.getContent().addView(layout, LayoutHelper.createFrame(-1, -2));
        sheet.addPage(page);
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        if (text) {
            TextView hint = requestText(context, LocaleController.getString(R.string.WalletReviewSigningData), 14, resourcesProvider);
            hint.setGravity(Gravity.LEFT);
            hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            footer.addView(hint, LayoutHelper.createLinear(-1, -2, 36, 12, 36, 0));
        }
        LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(dp(16), dp(16), dp(16), dp(16));
        ButtonWithCounterView decline = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        decline.setText(LocaleController.getString(R.string.Cancel));
        ButtonWithCounterView sign = new ButtonWithCounterView(context, resourcesProvider).setRound();
        sign.setText(LocaleController.getString(R.string.WalletSign));
        sign.setEnabled(false);
        buttons.addView(decline, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 6, 0));
        buttons.addView(sign, LayoutHelper.createLinear(0, 44, 1f, 6, 0, 0, 0));
        footer.addView(buttons);
        sheet.setFixedFooter(footer);
        sheet.setDelegate(new BottomSheet.BottomSheetDelegate() {
            @Override public boolean canDismiss() { return !request.isBusy(); }
        });
        Runnable updateRead = () -> {
            if (!scroll.canScrollVertically(1)) request.previewReady = true;
            sign.setEnabled(request.previewReady && !request.isBusy() && !request.isProcessed());
        };
        scroll.getViewTreeObserver().addOnScrollChangedListener(updateRead::run);
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(updateRead::run);
        Utilities.Callback<Boolean> decide = declined -> {
            sign.setEnabled(false);
            decline.setEnabled(false);
            (declined ? decline : sign).setLoading(true);
            controller.decide(request, declined, error -> {
                sign.setLoading(false);
                decline.setLoading(false);
                if (error == null) sheet.dismiss();
                else {
                    BulletinFactory.showError(error, sheet.topBulletinContainer, resourcesProvider);
                    updateRead.run();
                    decline.setEnabled(!request.isProcessed() && !request.claimed);
                }
            });
        };
        sign.setOnClickListener(v -> decide.run(false));
        decline.setOnClickListener(v -> decide.run(true));
        final boolean[] dismissed = {false};
        Runnable expiry = new Runnable() {
            @Override public void run() {
                if (dismissed[0]) return;
                if (request.isBusy()) AndroidUtilities.runOnUIThread(this, 1000);
                else sheet.dismiss();
            }
        };
        request.setOnProcessed(sheet::dismiss);
        sheet.setOnDismissListener(dialog -> {
            dismissed[0] = true;
            AndroidUtilities.cancelRunOnUIThread(expiry);
            controller.release(request);
            if (onDismiss != null) onDismiss.run();
        });
        sheet.show();
        AndroidUtilities.runOnUIThread(expiry, Math.max(0L, request.expires - (long) controller.connections.getCurrentTime()) * 1000L);
        return sheet;
    }

    public static BottomSheet showTransactionSheet(Context context, int currentAccount,
            Pending request, Theme.ResourcesProvider resourcesProvider) {
        return showTransactionSheet(context, currentAccount, request, resourcesProvider, null);
    }

    private static BottomSheet showTransactionSheet(Context context, int currentAccount,
            Pending request, Theme.ResourcesProvider resourcesProvider, Runnable onDismiss) {
        GramWallet wallet = GramWallet.getInstance(currentAccount);
        GramWalletTonConnect controller = wallet.tonConnect;
        final boolean signMessage = "signMessage".equals(request.method);
        NavBottomSheet sheet = new NavBottomSheet(context, resourcesProvider)
                .setBackgroundOverride(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(38));
        if (request.session.manifest.icon != null) avatar.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(request.session.manifest.icon)), "76_76", null, 0, request.session.manifest);
        layout.addView(avatar, LayoutHelper.createLinear(76, 76, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));
        TextView title = requestText(context, LocaleController.formatSpannable(signMessage ? R.string.WalletAppRequestsSignature : R.string.WalletAppRequestsTransfer, request.session.manifest.name), 20, resourcesProvider);
        title.setTypeface(AndroidUtilities.bold());
        layout.addView(title, LayoutHelper.createLinear(-1, -2, 24, 12, 24, 0));
        String domain = AndroidUtilities.getHostAuthority(request.session.manifest.url);
        if (!TextUtils.isEmpty(domain)) {
            layout.addView(domainView(context, domain, resourcesProvider),
                    LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL, 36, 6, 36, 12));
        }
        WalletCardComponent card = new WalletCardComponent(context, false, R.drawable.wallet_card_info, 24);
        card.setEngravingBitmap(null);
        card.getCardHolderView().setMaxLines(2);
        card.getCardHolderView().setSingleLine(false);
        card.setCardHolder(formatAddressTwoLines(request.transaction.messages.get(0).address));
        card.getBalanceView().setText(formatCardDebit(request.transaction.total));
        card.getUsdBalanceView().setText(wallet.formatCurrency(request.transaction.total));
        layout.addView(card, LayoutHelper.createLinear(-1, -2));
        card.setCardOnClickListener(v -> sheet.openPage(1));
        NavBottomSheet.Page main = new NavBottomSheet.Page(context);
        main.getContent().addView(layout, LayoutHelper.createFrame(-1, -2));
        sheet.addPage(main);

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout actionBar = new LinearLayout(context);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        details.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        final ImageView backView = new ImageView(context);
        backView.setImageResource(R.drawable.ic_ab_back);
        backView.setScaleType(ImageView.ScaleType.CENTER);
        backView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
        backView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        backView.setContentDescription(LocaleController.getString(R.string.AccDescrGoBack));
        ScaleStateListAnimator.apply(backView);
        actionBar.addView(backView, LayoutHelper.createLinear(48, 48, 0f, Gravity.CENTER_VERTICAL | Gravity.LEFT, 4, 0, 0, 0));

        final BackupImageView avatarView2 = new BackupImageView(context);
        avatarView2.setRoundRadius(dp(22));
        if (request.session.manifest.icon != null) {
            avatarView2.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(request.session.manifest.icon)), "44_44", null, 0, request.session.manifest);
        }
        actionBar.addView(avatarView2, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL | Gravity.LEFT, 12, 0, 11, 0));

        final LinearLayout actionBarText = new LinearLayout(context);
        actionBarText.setOrientation(LinearLayout.VERTICAL);
        actionBar.addView(actionBarText, LayoutHelper.createLinear(0, 44, 1.0f, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 0, 0, 12, 0));

        final TextView detailTitle = new TextView(context);
        detailTitle.setTypeface(AndroidUtilities.bold());
        detailTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        detailTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        actionBarText.addView(detailTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        detailTitle.setText(LocaleController.getString(R.string.WalletConfirmAction));

        if (!TextUtils.isEmpty(domain)) {
            final TextView subtitle = new TextView(context);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            actionBarText.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            subtitle.setText(domain);
        }

        backView.setOnClickListener(v -> sheet.openPage(0));

        final LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout previewBlock = transactionBlock(context, LocaleController.getString(R.string.WalletPreview), resourcesProvider);
        final String destination = shortAddress(request.transaction.messages.get(0).address);
        previewBlock.addView(block(context, IconBackgroundColors.BLUE, R.drawable.mini_gram_24,
                GramWallet.formatGramsWithUnit(request.transaction.total, true),
                request.transaction.messages.size() > 1
                        ? LocaleController.formatSpannable(R.string.WalletTransferToMultiple, destination, request.transaction.messages.size() - 1)
                        : LocaleController.formatSpannable(R.string.WalletTransferTo, destination),
                null, null, resourcesProvider), LayoutHelper.createLinear(-1, -2));
        rows.addView(previewBlock, LayoutHelper.createLinear(-1, -2, 12, 4, 12, 6));
        final LinearLayout actionsBlock = transactionBlock(context, LocaleController.getString(R.string.WalletActions), resourcesProvider);
        for (Transaction.Message message : request.transaction.messages) {
            actionsBlock.addView(block(context, IconBackgroundColors.BLUE, R.drawable.mini_gram_24,
                    message.payload == null || message.comment != null ? LocaleController.getString(R.string.WalletTransfer) : LocaleController.getString(R.string.WalletContractCall),
                    LocaleController.formatSpannable(R.string.WalletTransferTo, shortAddress(message.address)), actionPrice(-message.amount),
                    message.comment, resourcesProvider),
                    LayoutHelper.createLinear(-1, -2));
        }
        rows.addView(actionsBlock, LayoutHelper.createLinear(-1, -2, 12, 6, 12, 12));
        final ScrollView scroll = new android.widget.ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(heightMeasureSpec), dp(360)), MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        scroll.addView(rows);
        details.addView(scroll, LayoutHelper.createLinear(-1, -2));
        NavBottomSheet.Page detailPage = new NavBottomSheet.Page(context);
        detailPage.getContent().addView(details, LayoutHelper.createFrame(-1, -2));
        sheet.addPage(detailPage);

        final LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        TextView status = requestText(context, null, 14, resourcesProvider);
        final SpannableStringBuilder loadingFee = new SpannableStringBuilder(" ");
        loadingFee.setSpan(new LoadingSpan(status, dp(80)), 0, loadingFee.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setText(LocaleController.formatSpannable(R.string.WalletFeeAmount, loadingFee));
        status.setVisibility(signMessage ? android.view.View.GONE : android.view.View.VISIBLE);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        footer.addView(status, LayoutHelper.createLinear(-1, -2, 20, 16, 20, 16));
        final LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(dp(16), 0, dp(16), dp(16));
        final ButtonWithCounterView decline = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        decline.setText(LocaleController.getString(R.string.WalletDecline));
        buttons.addView(decline, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 6, 0));
        final ButtonWithCounterView confirm = new ButtonWithCounterView(context, resourcesProvider).setRound();
        confirm.setText(LocaleController.getString(signMessage ? R.string.WalletSign : R.string.WalletConfirm));
        confirm.setEnabled(false);
        buttons.addView(confirm, LayoutHelper.createLinear(0, 44, 1f, 6, 0, 0, 0));
        footer.addView(buttons);
        sheet.setFixedFooter(footer);
        final boolean[] dismissed = {false};
        final boolean[] previewReady = {false};
        final Utilities.Callback<Boolean> decide = declined -> {
            if (request.isBusy() || request.isProcessed() || !declined && !previewReady[0]) return;
            confirm.setEnabled(false);
            decline.setEnabled(false);
            (declined ? decline : confirm).setLoading(true);
            controller.decide(request, declined, error -> {
                confirm.setLoading(false);
                decline.setLoading(false);
                if (error == null) sheet.dismiss();
                else if (dismissed[0]) {
                    BulletinFactory.showError(error);
                } else {
                    BulletinFactory.showError(error, sheet.topBulletinContainer, resourcesProvider);
                    confirm.setEnabled(!request.isProcessed() && previewReady[0]);
                    decline.setEnabled(!request.isProcessed() && !request.claimed);
                }
            });
        };
        confirm.setOnClickListener(v -> decide.run(false));
        decline.setOnClickListener(v -> decide.run(true));
        Runnable expiry = new Runnable() {
            @Override public void run() {
                if (dismissed[0]) return;
                if (request.isBusy()) AndroidUtilities.runOnUIThread(this, 1000);
                else sheet.dismiss();
            }
        };
        long deadline = Math.min(request.expires, request.transaction.validUntil == 0 ? Long.MAX_VALUE : request.transaction.validUntil);
        long delay = Math.max(0, deadline - org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).getCurrentTime()) * 1000L;
        request.setOnProcessed(sheet::dismiss);
        sheet.setOnDismissListener(dialog -> {
            dismissed[0] = true;
            AndroidUtilities.cancelRunOnUIThread(expiry);
            controller.release(request);
            if (onDismiss != null) onDismiss.run();
        });
        sheet.show();
        AndroidUtilities.runOnUIThread(expiry, delay);
        controller.preview(request, (preview, error) -> {
            if (dismissed[0] || request.isBusy() || request.isProcessed()) return;
            previewReady[0] = request.previewReady;
            confirm.setEnabled(previewReady[0]);
            if (previewReady[0] && !signMessage) {
                CharSequence amount = GramWallet.formatGramCapital(preview.fee, true);
                CharSequence currency = wallet.formatCurrency(preview.fee, true);
                status.setText(TextUtils.isEmpty(currency)
                        ? LocaleController.formatSpannable(R.string.WalletFeeAmount, amount)
                        : LocaleController.formatSpannable(R.string.WalletFeeAmountWithCurrency, amount, currency));
            } else if (!previewReady[0]) {
                String failure = error != null ? error : LocaleController.getString(R.string.WalletTransactionSimulationFailed);
                if (error == null) log("preview", failure);
                BulletinFactory.showError(failure, sheet.topBulletinContainer, resourcesProvider);
            }
        });
        return sheet;
    }

    private static LinearLayout transactionBlock(Context context, String title, Theme.ResourcesProvider resourcesProvider) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackground(Theme.createRoundRectDrawable(
                dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        HeaderCell header = new HeaderCell(context, resourcesProvider);
        header.setText(title);
        block.addView(header, LayoutHelper.createLinear(-1, -2));
        return block;
    }

    private static TextView domainView(Context context, String domain, Theme.ResourcesProvider resourcesProvider) {
        final TextView linkView = new TextView(context);
        linkView.setTypeface(AndroidUtilities.bold());
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linkView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        final SpannableStringBuilder ssb = new SpannableStringBuilder();
        // final Drawable verifiedIconBg = context.getResources().getDrawable(R.drawable.verified2_bg).mutate();
        // verifiedIconBg.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
        // final Drawable verifiedIconFg = context.getResources().getDrawable(R.drawable.verified2_fg).mutate();
        // verifiedIconFg.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        // final Drawable verifiedIcon = new CombinedDrawable(verifiedIconBg, verifiedIconFg);
        // ssb.append("✅");
        // verifiedIcon.setBounds(0, -dp(2), dp(16), dp(14));
        // ssb.setSpan(new ImageSpan(verifiedIcon), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // ssb.append(" ");
        ssb.append(domain);
        linkView.setText(ssb);
        return linkView;
    }

    private static TextView requestText(Context context, CharSequence text, int size, Theme.ResourcesProvider provider) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider));
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private static LinearLayout block(Context context, IconBackgroundColors color, int iconRes, CharSequence title, CharSequence subtitle, CharSequence price, CharSequence comment, Theme.ResourcesProvider resourcesProvider) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(5), dp(12), dp(5));

        final ImageView iconView = new ImageView(context);
        final GradientDrawable iconBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color.top, color.bottom}
        );
        iconBackground.setShape(GradientDrawable.OVAL);
        iconView.setBackground(iconBackground);
        iconView.setImageResource(iconRes);
        iconView.setPadding(dp(7), dp(7), dp(7), dp(7));
        row.addView(iconView, LayoutHelper.createLinear(46, 46, 0f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        final LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(subtitle == null && comment == null ? Gravity.CENTER_VERTICAL : Gravity.TOP);

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setSingleLine(subtitle != null);
        if (subtitle == null) {
            titleView.setGravity(Gravity.CENTER_VERTICAL);
        }
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setText(title);
        final LinearLayout.LayoutParams titleParams = subtitle == null
                ? LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(24), Gravity.CENTER_VERTICAL)
                : LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        titleParams.weight = 0;
        textLayout.addView(titleView, titleParams);

        if (!TextUtils.isEmpty(subtitle)) {
            final TextView subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setText(subtitle);
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }

        if (!TextUtils.isEmpty(comment)) {
            final TextView commentView = new TextView(context);
            final MessageDrawable bubble = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false);
            bubble.shadow = false;
            bubble.overrideColor = Theme.getColor(Theme.key_wallet_comment, resourcesProvider);
            commentView.setBackground(bubble);
            commentView.setPadding(dp(19), dp(9), dp(15), dp(8));
            commentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            commentView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            commentView.setMaxLines(2);
            commentView.setText(comment);
            textLayout.addView(commentView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        }

        row.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        if (!TextUtils.isEmpty(price)) {
            final TextView priceView = new TextView(context);
            priceView.setTypeface(AndroidUtilities.bold());
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            priceView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            priceView.setText(price);
            priceView.setTextColor(price.toString().startsWith("+")
                    ? Theme.getColor(Theme.key_color_green, resourcesProvider)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            row.addView(priceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        }

        return row;
    }

    private static CharSequence actionPrice(long nanotons) {
        final String text = GramWallet.formatGrams(nanotons, true);
        final SpannableStringBuilder amount = new SpannableStringBuilder(text);
        final int point = text.indexOf('.');
        if (point >= 0) {
            amount.setSpan(new RelativeSizeSpan(11.0f / 16.0f), point, amount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (amount.length() == 0 || amount.charAt(amount.length() - 1) != 'x') {
            amount.append(" x");
        }
        final ColoredImageSpan icon = new ColoredImageSpan(R.drawable.wallet_gram_small, ColoredImageSpan.ALIGN_CENTER);
        icon.recolorDrawable = false;
        icon.setSize(dp(16));
        amount.setSpan(icon, amount.length() - 1, amount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return amount;
    }

    private static String shortAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return LocaleController.getString(R.string.WalletUnknownAddress);
        }
        return address.length() <= 10
                ? address
                : address.substring(0, 4) + "…" + address.substring(address.length() - 4);
    }

    private static String formatCardAddress(String address) {
        int groupCount = (address.length() + 3) / 4;
        int groupsOnFirstLine = (groupCount + 1) / 2;
        StringBuilder formatted = new StringBuilder(address.length() + groupCount);
        for (int i = 0; i < address.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(i / 4 == groupsOnFirstLine ? '\n' : ' ');
            }
            formatted.append(address.charAt(i));
        }
        return formatted.toString();
    }

    private static CharSequence formatCardDebit(long nanotons) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        SpannableString icon = new SpannableString("GRAM");
        icon.setSpan(new ColoredImageSpan(R.drawable.wallet_gram_large), 0, icon.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.append(icon).append(" ");
        result.append(GramWallet.formatGrams(-nanotons, true)).append(" ");
        int labelStart = result.length();
        result.append(LocaleController.getString(R.string.GramCurrency));
        result.setSpan(new ForegroundColorSpan(0xFF6DDCFF), labelStart, result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }


}
