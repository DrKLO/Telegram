package org.telegram.ui.iv;

import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.List;

/**
 * In-process registry of the media (Photo/Document TL objects) behind the last rich content copied to the
 * clipboard. The clipboard HTML only carries media ids (see {@link RichHtml}); when that HTML is pasted back
 * into a {@link RichEditor} the target may not already have the media loaded, so it resolves the id here.
 *
 * This deliberately avoids putting binary on the system clipboard: copy and paste both happen inside the same
 * app process, so a static map keyed by id is enough. It does not survive process death — acceptable, because
 * cross-process paste of Telegram media isn't meaningful anyway. Each copy replaces the previous contents.
 */
public final class RichMediaClipboard {

    private RichMediaClipboard() {}

    private static final HashMap<Long, TLRPC.Photo> photos = new HashMap<>();
    private static final HashMap<Long, TLRPC.Document> documents = new HashMap<>();

    /** Replaces the registry with the media behind a freshly-copied selection/message. Nulls are ignored. */
    public static synchronized void set(List<TLRPC.Photo> newPhotos, List<TLRPC.Document> newDocuments) {
        photos.clear();
        documents.clear();
        if (newPhotos != null) {
            for (TLRPC.Photo p : newPhotos) if (p != null) photos.put(p.id, p);
        }
        if (newDocuments != null) {
            for (TLRPC.Document d : newDocuments) if (d != null) documents.put(d.id, d);
        }
    }

    public static synchronized TLRPC.Photo photo(long id) {
        return id == 0 ? null : photos.get(id);
    }

    public static synchronized TLRPC.Document document(long id) {
        return id == 0 ? null : documents.get(id);
    }
}
