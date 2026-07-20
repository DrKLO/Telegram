package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.gms.wearable.Wearable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class WearAuthSheet {

    private static final String PATH_ANSWER = "/tg-wear-auth/answer";
    private static final String PATH_TOKEN = "/tg-wear-auth/token";
    private static final int AES_GCM_IV_LEN = 12;

    private static final BigInteger DH_P = new BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
        "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
        "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
        "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
        "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
        "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
        "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF",
        16);
    private static final BigInteger DH_G = BigInteger.valueOf(2);
    private static final int DH_PUBKEY_LEN = 256;

    private static final int SESSION_ID_LEN = 16;
    private static final int NONCE_LEN = 16;

    private static BottomSheet currentSheet;
    private static AuthSession currentSession;

    private static class AuthSession {
        final byte[] sessionId;
        final byte[] peerPub;
        final String originNodeId;

        BigInteger privateExponent;
        byte[] sharedKey;
        byte[] noncePhone;
        List<String> emojis;

        AuthSession(byte[] sessionId, byte[] peerPub, String originNodeId) {
            this.sessionId = sessionId;
            this.peerPub = peerPub;
            this.originNodeId = originNodeId;
        }

        byte[] acceptAndBuildAnswer() {
            final SecureRandom random = new SecureRandom();
            final BigInteger x = new BigInteger(2048, random);
            final BigInteger gx = DH_G.modPow(x, DH_P);
            if (!isValidPub(gx))
                throw new IllegalStateException("our pubkey invalid (extremely unlikely)");
            final byte[] pkPhone = encode256(gx);

            final BigInteger peer = new BigInteger(1, peerPub);
            if (!isValidPub(peer))
                throw new IllegalArgumentException("peer pubkey out of range");
            final byte[] ss = encode256(peer.modPow(x, DH_P));

            final byte[] nonceP = new byte[NONCE_LEN];
            random.nextBytes(nonceP);

            final byte[] k = sha256(ss, sessionId, nonceP);
            final byte[] sasBytes = sha256(ss, peerPub);

            privateExponent = x;
            sharedKey = k;
            noncePhone = nonceP;
            emojis = emojify(sasBytes, 4);

            FileLog.d("wear-auth: built answer; session " + hex(sessionId)
                + " emojis=" + emojis);

            final byte[] answer = new byte[SESSION_ID_LEN + NONCE_LEN + DH_PUBKEY_LEN];
            System.arraycopy(sessionId, 0, answer, 0, SESSION_ID_LEN);
            System.arraycopy(nonceP, 0, answer, SESSION_ID_LEN, NONCE_LEN);
            System.arraycopy(pkPhone, 0, answer, SESSION_ID_LEN + NONCE_LEN, DH_PUBKEY_LEN);
            return answer;
        }
    }

    public static void onOfferReceived(byte[] data, String sourceNodeId) {
        if (data == null || data.length != SESSION_ID_LEN + DH_PUBKEY_LEN) {
            FileLog.d("wear-auth: malformed offer (" + (data == null ? -1 : data.length) + ")");
            return;
        }
        final byte[] sid = Arrays.copyOfRange(data, 0, SESSION_ID_LEN);
        final byte[] pub = Arrays.copyOfRange(data, SESSION_ID_LEN, data.length);
        if (currentSession != null && Arrays.equals(currentSession.sessionId, sid)) {
            FileLog.d("wear-auth: duplicate offer (same sessionId) — ignoring");
            return;
        }
        FileLog.d("wear-auth: new session " + hex(sid) + " from " + sourceNodeId);
        currentSession = new AuthSession(sid, pub, sourceNodeId);
        show();
    }

    public static void onCancelReceived() {
        FileLog.d("wear-auth: cancel received; dropping session and dismissing sheet");
        currentSession = null;
        cancel();
    }

    private static String hex(byte[] b) {
        final StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }

    private static String[] getEmojis() {
        return new String[] {
            "👋", "👍", "👎", "👌", "👊", "🤟", "🫵", "👏", "🤝", "✍", "💪", "👀", "👅", "🥶",
            "🤡", "💀", "👽", "😈", "😎", "🤠", "🤩", "😍", "🤯", "🦄", "🐶", "🐷", "🐔", "🐥",
            "🦊", "🐙", "🐸", "🐳", "🦉", "🦆", "🐢", "🦖", "🐵", "🐝", "🦁", "🐧", "🦋", "🐬",
            "🦀", "🐌", "🦠", "🐠", "🌵", "💐", "💐", "🎄", "🍄", "🍔", "🍕", "☕", "🍩", "🍪",
            "🎂", "🍫", "🍭", "🍎", "🥥", "🍒", "🌶", "🥒", "🥦", "🍇", "🍋", "🍓", "🍌", "🍍",
            "🍆", "🌽", "🍺", "🍷", "🍾", "🍦", "🍰", "🍞", "🍖", "🌭", "🧊", "🍳", "⭐", "☁",
            "🚀", "🎈", "💎", "💡", "🔑", "❄", "🔎", "👠", "👕", "👗", "👖", "👙", "👜", "👓",
            "🎀", "💄", "💍", "♠", "❤", "♦", "♣", "🌈", "🌊", "🎃", "👻", "🎁", "🔮", "🎥",
            "💿", "💻", "📡", "🔉", "⏳", "🔒", "🚗", "🔱", "🔗", "🎲", "🎮", "⚽", "🎳", "🏁",
            "🏆", "🎸", "💣", "🚽", "🎹", "🎤", "🎨", "🔫", "💊", "💰", "📦", "📅", "📚", "❗",
            "❓", "💯", "💦", "💤", "🌍", "🏝", "🚂", "🛢", "🛹", "🚢", "✈", "🛎", "🧳", "🌖",
            "🌞", "🔥", "🏓", "🎰", "🧸", "🪩", "🎭", "👑", "🎩", "🧢", "🔈", "🔋", "🕯", "✏",
            "💼", "📌", "✂", "🗑", "🛡", "⚙", "🧲", "🪏", "⚖", "🧪", "🚪", "🫧", "🛒", "🪑",
            "🗿", "🏁", "🏴‍☠", "📊", "🥁", "🎧", "🎵", "🧩", "⛳", "🥇", "🥈", "🥈", "🌪", "⛺",
            "🧭", "🫆", "🧠", "💋"
        };
    }

    public static void show() {
        Context context = LaunchActivity.instance;
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return;

        Theme.ResourcesProvider resourcesProvider = null;
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            resourcesProvider = fragment.getResourceProvider();
        }

        cancel();

        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        final FrameLayout container = new FrameLayout(context);
        b.setCustomView(container);

        int nonTestAccount = UserConfig.selectedAccount;
        final ArrayList<Integer> accountNumbers = new ArrayList<>();
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                if (!ConnectionsManager.getInstance(a).isTestBackend()) {
                    nonTestAccount = a;
                }
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
        if (accountNumbers.isEmpty())
            return;

        final FrameLayout accountSelectorLayout = new FrameLayout(context);
        final FrameLayout accountSelectorInnerLayout = new FrameLayout(context);
        accountSelectorInnerLayout.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider)));
        final BackupImageView accountImageView = new BackupImageView(context);
        accountImageView.setRoundRadius(dp(14));
        accountImageView.getImageReceiver().setCrossfadeWithOldImage(true);
        final AvatarDrawable accountAvatarDrawable = new AvatarDrawable();

        final int[] selectedAccount = new int[] { UserConfig.selectedAccount };
        final TLRPC.User self = UserConfig.getInstance(selectedAccount[0]).getCurrentUser();
        accountAvatarDrawable.setInfo(self);
        accountImageView.setForUserOrChat(self, accountAvatarDrawable);

        accountSelectorInnerLayout.addView(accountImageView, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.FILL_VERTICAL));
        final ImageView accountSelectorIconView = new ImageView(context);
        accountSelectorIconView.setScaleType(ImageView.ScaleType.CENTER);
        accountSelectorIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
        accountSelectorIconView.setImageResource(R.drawable.arrows_select);
        accountSelectorInnerLayout.addView(accountSelectorIconView, LayoutHelper.createFrame(18, 18, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        accountSelectorLayout.addView(accountSelectorInnerLayout, LayoutHelper.createFrame(52, 28, Gravity.CENTER));
        accountSelectorLayout.setPadding(dp(8), dp(4), dp(8), 0);
        container.addView(accountSelectorLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.TOP, 6, 4, 6, 0));
        ScaleStateListAnimator.apply(accountSelectorLayout);
        if (accountNumbers.size() <= 1) {
            accountSelectorLayout.setVisibility(View.GONE);
        }

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        container.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        final BackupImageView stickerView = new BackupImageView(context);
        layout.addView(stickerView, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 32, 32, 9.66f));
        MediaDataController.getInstance(nonTestAccount).setPlaceholderImage(stickerView, "Utya3D", "😎", "130_130");

        final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_dialogTextBlack, true, resourcesProvider);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.WearAuthTitle));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 24, 32, 9.66f));

        final TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_dialogTextBlack, false);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(getString(R.string.WearAuthText));
        layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 0, 32, 24));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.Next));
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 12, 12, 12, 8));

        final BottomSheet sheet = b.create();
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        accountSelectorLayout.setOnClickListener(v -> {
            ItemOptions i = ItemOptions.makeOptions(sheet.container, sheet.getResourcesProvider(), accountSelectorInnerLayout);
            for (int account : accountNumbers) {
                final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
                if (user == null) continue;
                i.addAccount(account, selectedAccount[0] == account, () -> {
                    selectedAccount[0] = account;
                    accountAvatarDrawable.setInfo(user);
                    accountImageView.setForUserOrChat(user, accountAvatarDrawable);
                });
            }
            i
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .setDimAlpha(0)
                .setGravity(Gravity.LEFT)
                .translate(-dp(8), -dp(8))
                .show();
        });

        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            final AuthSession session = currentSession;
            if (session == null) return;
            button.setLoading(true);
            final Context ctx = v.getContext().getApplicationContext();
            final byte[] payload;
            try {
                payload = session.acceptAndBuildAnswer();
            } catch (Exception e) {
                FileLog.e(e);
                button.setLoading(false);
                return;
            }
            Wearable.getMessageClient(ctx)
                .sendMessage(session.originNodeId, PATH_ANSWER, payload)
                .addOnSuccessListener(id -> {
                    FileLog.d("wear-auth: /answer delivered to " + session.originNodeId);
                    button.setLoading(false);
                    showEmojis(selectedAccount[0], session.emojis);
                })
                .addOnFailureListener(e -> {
                    FileLog.e("wear-auth: /answer send failed: " + e.getMessage());
                    button.setLoading(false);
                });
        });

        currentSheet = sheet;
        sheet.show();
    }

    public static void showEmojis(final int account, List<String> emojis) {
        if (emojis == null || emojis.isEmpty()) return;

        Context context = LaunchActivity.instance;
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return;

        Theme.ResourcesProvider resourcesProvider = null;
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            resourcesProvider = fragment.getResourceProvider();
        }

        cancel();

        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        final FrameLayout container = new FrameLayout(context);
        b.setCustomView(container);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        container.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_dialogTextBlack, true, resourcesProvider);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.WearAuthEmojis));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 24, 32, 9.66f));

        final LinearLayout emojisLayout = new LinearLayout(context);
        emojisLayout.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < emojis.size(); ++i) {
            final String emoji = emojis.get(i);
            final Drawable emojiDrawable = Emoji.getEmojiBigDrawable(emoji);
            final ImageView imageView = new ImageView(context);
            imageView.setImageDrawable(emojiDrawable);
            NotificationCenter.listenEmojiLoading(imageView);
            imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .15f)));
            emojisLayout.addView(imageView, LayoutHelper.createLinear(80, 80, i == 0 ? 0 : 5, 0, 0, 0));
        }
        layout.addView(emojisLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 12, 32, 12));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.WearAuthEmojisLogIn));
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 12, 12, 12, 8));

        final BottomSheet sheet = b.create();
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        currentSheet = sheet;
        sheet.show();

        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            final AuthSession session = currentSession;
            if (session == null || session.sharedKey == null) {
                FileLog.d("wear-auth: login pressed with no session/key");
                return;
            }

            button.setLoading(true);
            final TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
            req.flags |= TLObject.FLAG_2;
            req.url = "https://web.telegram.org/";
            ConnectionsManager.getInstance(account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                button.setLoading(false);
                if (res instanceof TLRPC.TL_urlAuthResultAccepted) {
                    final Uri uri = Uri.parse(((TLRPC.TL_urlAuthResultAccepted) res).url);
                    final Uri query = Uri.parse("?" + uri.getFragment());
                    final String tgWebAuthToken = query.getQueryParameter("tgWebAuthToken");
                    if (tgWebAuthToken == null) {
                        BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider())
                            .showForError("NO_TOKEN");
                        return;
                    }

                    final int dcId = ConnectionsManager.getInstance(account).getCurrentDatacenterId();
                    final boolean isTest = ConnectionsManager.getInstance(account).isTestBackend();
                    FileLog.d("wear-auth: sending /token account=" + account
                            + " dcId=" + dcId + " isTest=" + isTest);
                    final Context ctx = v.getContext().getApplicationContext();
                    final byte[] wire;
                    try {
                        wire = buildEncryptedTokenWire(session, tgWebAuthToken, dcId, isTest);
                    } catch (Exception e) {
                        FileLog.e(e);
                        BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider())
                            .showForError(e.getMessage());
                        return;
                    }
                    Wearable.getMessageClient(ctx)
                        .sendMessage(session.originNodeId, PATH_TOKEN, wire)
                        .addOnSuccessListener(id -> {
                            FileLog.d("wear-auth: /token delivered to " + session.originNodeId);
                            button.setLoading(false);
                            currentSession = null;
                            cancel();
                        })
                        .addOnFailureListener(e -> {
                            FileLog.e("wear-auth: /token send failed: " + e.getMessage());
                            button.setLoading(false);
                        });
                    sheet.dismiss();
                } else if (err != null) {
                    BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider())
                        .showForError(err);
                } else {
                    BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider())
                        .showForError("NO_TOKEN");
                }
            });
        });
    }

    private static byte[] buildEncryptedTokenWire(AuthSession session, String token,
                                                  int dcId, boolean isTest) throws Exception {
        byte[] tokenBytes = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4 + tokenBytes.length + 4 + 1);
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);
        buf.putInt(tokenBytes.length);
        buf.put(tokenBytes);
        buf.putInt(dcId);
        buf.put((byte) (isTest ? 1 : 0));
        byte[] plain = buf.array();

        byte[] iv = new byte[AES_GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);
        byte[] ct = aeadEncrypt(session.sharedKey, iv, null, plain);

        byte[] wire = new byte[SESSION_ID_LEN + AES_GCM_IV_LEN + ct.length];
        System.arraycopy(session.sessionId, 0, wire, 0, SESSION_ID_LEN);
        System.arraycopy(iv, 0, wire, SESSION_ID_LEN, AES_GCM_IV_LEN);
        System.arraycopy(ct, 0, wire, SESSION_ID_LEN + AES_GCM_IV_LEN, ct.length);
        return wire;
    }

    public static void cancel() {
        if (currentSheet != null) {
            currentSheet.dismiss();
            currentSheet = null;
        }
    }

    private static boolean isValidPub(BigInteger v) {
        return v.compareTo(BigInteger.ONE) > 0 && v.compareTo(DH_P.subtract(BigInteger.ONE)) < 0;
    }

    private static byte[] encode256(BigInteger v) {
        byte[] raw = v.toByteArray();
        if (raw.length == DH_PUBKEY_LEN) return raw;
        if (raw.length == DH_PUBKEY_LEN + 1 && raw[0] == 0) {
            return Arrays.copyOfRange(raw, 1, raw.length);
        }
        if (raw.length < DH_PUBKEY_LEN) {
            byte[] padded = new byte[DH_PUBKEY_LEN];
            System.arraycopy(raw, 0, padded, DH_PUBKEY_LEN - raw.length, raw.length);
            return padded;
        }
        throw new IllegalStateException("unexpected DH value size " + raw.length);
    }

    private static byte[] sha256(byte[]... parts) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) md.update(p);
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] aeadEncrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        if (aad != null && aad.length > 0) cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static List<String> emojify(byte[] sas, int count) {
        final String[] arr = WearAuthSheet.getEmojis();
        final ArrayList<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(arr[(int)(bytesToLong(sas, i * 8) % arr.length)]);
        }
        return out;
    }

    private static long bytesToLong(byte[] a, int off) {
        return (((long) a[off] & 0x7F) << 56)
            | (((long) a[off + 1] & 0xFF) << 48)
            | (((long) a[off + 2] & 0xFF) << 40)
            | (((long) a[off + 3] & 0xFF) << 32)
            | (((long) a[off + 4] & 0xFF) << 24)
            | (((long) a[off + 5] & 0xFF) << 16)
            | (((long) a[off + 6] & 0xFF) << 8)
            | ((long) a[off + 7] & 0xFF);
    }

}
