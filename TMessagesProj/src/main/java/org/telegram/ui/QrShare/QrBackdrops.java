package org.telegram.ui.QrShare;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * The palette of the QR themes: the backdrops of the collectible gifts, from {@link TLRPC.TL_messages_getShareThemeBackdrops}.
 * Kept in memory and in the settings of the account, and refreshed by the hash like the peer colors,
 * so the saved theme is drawn right away on the next opening.
 */
public final class QrBackdrops {

    private static final String KEY = "qrBackdrops";

    private static final QrBackdrops[] instances = new QrBackdrops[UserConfig.MAX_ACCOUNT_COUNT];

    private final int account;
    private @Nullable TLRPC.TL_shareThemeBackdrops backdrops;
    private boolean loading, refreshed;
    private final ArrayList<Runnable> callbacks = new ArrayList<>();

    public static QrBackdrops get(int account) {
        if (instances[account] == null) {
            instances[account] = new QrBackdrops(account);
        }
        return instances[account];
    }

    private QrBackdrops(int account) {
        this.account = account;
        final String saved = getPrefs().getString(KEY, null);
        if (!TextUtils.isEmpty(saved)) {
            try {
                final SerializedData data = new SerializedData(Utilities.hexToBytes(saved));
                final TLRPC.ShareThemeBackdrops result = TLRPC.ShareThemeBackdrops.TLdeserialize(data, data.readInt32(false), false);
                if (result instanceof TLRPC.TL_shareThemeBackdrops) {
                    backdrops = (TLRPC.TL_shareThemeBackdrops) result;
                }
                data.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private SharedPreferences getPrefs() {
        return MessagesController.getMainSettings(account);
    }

    /** The cached palette, possibly stale; null before the first load. */
    @Nullable
    public List<TLRPC.TL_shareThemeBackdropOption> getList() {
        return backdrops != null ? backdrops.backdrops : null;
    }

    @Nullable
    public TLRPC.TL_shareThemeBackdropOption find(int id) {
        if (backdrops == null) {
            return null;
        }
        for (TLRPC.TL_shareThemeBackdropOption option : backdrops.backdrops) {
            if (option.backdrop_id == id) {
                return option;
            }
        }
        return null;
    }

    /** Checks the palette against the server once per launch; {@code onDone} runs on the UI thread either way. */
    public void load(@Nullable Runnable onDone) {
        if (refreshed) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        if (onDone != null) {
            callbacks.add(onDone);
        }
        if (loading) {
            return;
        }
        loading = true;
        final TLRPC.TL_messages_getShareThemeBackdrops req = new TLRPC.TL_messages_getShareThemeBackdrops();
        req.hash = backdrops != null ? backdrops.hash : 0;
        ConnectionsManager.getInstance(account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            loading = false;
            if (res instanceof TLRPC.TL_shareThemeBackdrops) {
                backdrops = (TLRPC.TL_shareThemeBackdrops) res;
                save();
            }
            refreshed = res != null;
            final ArrayList<Runnable> done = new ArrayList<>(callbacks);
            callbacks.clear();
            for (Runnable r : done) {
                r.run();
            }
        });
    }

    private void save() {
        try {
            final SerializedData data = new SerializedData(backdrops.getObjectSize());
            backdrops.serializeToStream(data);
            getPrefs().edit().putString(KEY, Utilities.bytesToHex(data.toByteArray())).apply();
            data.cleanup();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
