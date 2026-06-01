package org.telegram.messenger;

import com.google.android.exoplayer2.util.Util;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.tl.TL_aicompose;

import java.util.ArrayList;
import java.util.Base64;

public final class AiTonesController {

    public final int currentAccount;

    public final ArrayList<TL_aicompose.AiComposeTone> tones = new ArrayList<>();
    public long hash;

    public boolean open;
    private boolean loadedLocal;

    public AiTonesController(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public void invalidate() {
        requestedTime = 0;
        if (open) {
            load();
        }
    }

    private int requestId = -1;
    private long requestedTime = 0;
    public void request() {
        if (requestId >= 0) return;
        if (System.currentTimeMillis() - requestedTime < 30 * 60 * 1000) return;
        final TL_aicompose.getTones req = new TL_aicompose.getTones();
        req.hash = hash;
        requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (tones, error) -> {
            requestId = -1;
            requestedTime = System.currentTimeMillis();
            if (tones instanceof TL_aicompose.TL_tones) {
                MessagesController.getInstance(currentAccount).putUsers(tones.users, false);
                this.tones.clear();
                this.tones.addAll(((TL_aicompose.TL_tones) tones).tones);
                this.hash = ((TL_aicompose.TL_tones) tones).hash;
                save();
                notifyUpdate();
            } else if (tones instanceof TL_aicompose.TL_tonesNotModified) {

            } else {

            }
        });
    }

    public void cancel() {
        if (requestId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
            requestId = -1;
        }
    }

    public boolean isLoading() {
        return requestId >= 0;
    }

    public void notifyUpdate() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.loadedAiComposeTones, this);
    }

    public void load() {
        if (!loadedLocal) {
            loadedLocal = true;
            try {
                final String base64 = MessagesController.getInstance(currentAccount).getMainSettings().getString("ai_styles", null);
                if (base64 != null) {
                    final SerializedData data = new SerializedData(Base64.getDecoder().decode(base64));
                    final TL_aicompose.Tones tones = TL_aicompose.Tones.TLdeserialize(data, data.readInt32(true), true);
                    if (tones instanceof TL_aicompose.TL_tones) {
                        hash = ((TL_aicompose.TL_tones) tones).hash;
                        this.tones.clear();
                        this.tones.addAll(((TL_aicompose.TL_tones) tones).tones);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        request();
    }

    private void save() {
        final TL_aicompose.TL_tones tones = new TL_aicompose.TL_tones();
        tones.hash = hash;
        tones.tones.addAll(this.tones);
        final SerializedData data = new SerializedData(tones.getObjectSize());
        tones.serializeToStream(data);
        MessagesController.getInstance(currentAccount).getMainSettings().edit()
            .putString("ai_styles", Base64.getEncoder().encodeToString(data.toByteArray()))
            .apply();
    }

    public void edit(TL_aicompose.TL_aiComposeTone tone) {
        for (int i = 0; i < tones.size(); ++i) {
            if (tones.get(i) instanceof TL_aicompose.TL_aiComposeTone && ((TL_aicompose.TL_aiComposeTone) tones.get(i)).id == tone.id) {
                tones.set(i, tone);
                notifyUpdate();
                break;
            }
        }
    }

    public int getSavedTonesCount() {
        int count = 0;
        for (int i = 0; i < tones.size(); ++i) {
            if (tones.get(i) instanceof TL_aicompose.TL_aiComposeTone) {
                count++;
            }
        }
        return count;
    }

    public void remove(TL_aicompose.AiComposeTone tone) {
        tones.remove(tone);
        save();
        notifyUpdate();
    }

    public void unsave(TL_aicompose.AiComposeTone tone) {
        remove(tone);

        final TL_aicompose.saveTone req = new TL_aicompose.saveTone();
        req.tone = TL_aicompose.InputAiComposeTone.from(tone);
        req.unsave = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
    }

    public void add(TL_aicompose.AiComposeTone tone) {
        boolean has = false;
        if (tone instanceof TL_aicompose.TL_aiComposeTone) {
            final long id = ((TL_aicompose.TL_aiComposeTone) tone).id;
            for (int i = 0; i < tones.size(); ++i) {
                if (tones.get(i) instanceof TL_aicompose.TL_aiComposeTone && ((TL_aicompose.TL_aiComposeTone) tones.get(i)).id == id) {
                    has = true;
                    break;
                }
            }
        }
        if (!has) {
            tones.add(0, tone);
            save();
            notifyUpdate();
        }
    }
}
