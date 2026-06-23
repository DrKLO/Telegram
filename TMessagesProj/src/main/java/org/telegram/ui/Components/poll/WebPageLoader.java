package org.Tajgram.ui.Components.poll;

import androidx.collection.LongSparseArray;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.MessagesController;
import org.Tajgram.messenger.Utilities;
import org.Tajgram.tgnet.ConnectionsManager;
import org.Tajgram.tgnet.TLObject;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.tgnet.tl.TL_account;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WebPageLoader {
    private final int currentAccount;

    public WebPageLoader(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    private final HashMap<String, TLRPC.WebPage> pages = new HashMap<>();
    private final HashMap<String, ArrayList<Utilities.Callback2<TLRPC.WebPage, TLObject>>> callbacks = new HashMap<>();

    public boolean isLoading(String url) {
        return callbacks.containsKey(url);
    }

    public TLRPC.WebPage getWebPage(String url) {
        return pages.get(url);
    }

    public void get(String url, Utilities.Callback2<TLRPC.WebPage, TLObject> callback) {
        if (pages.containsKey(url)) {
            callback.run(pages.get(url), null);
            return;
        }

        final boolean loading = callbacks.containsKey(url);
        ArrayList<Utilities.Callback2<TLRPC.WebPage, TLObject>> callbacksList = callbacks.get(url);
        if (callbacksList == null) {
            callbacksList = new ArrayList<>();
            callbacks.put(url, callbacksList);
        }
        callbacksList.add(callback);
        if (loading) {
            return;
        }

        TL_account.getWebPagePreview req = new TL_account.getWebPagePreview();
        req.message = url;
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            TLRPC.WebPage webPage = null;
            if (res != null) {
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                if (res.media != null) {
                    webPage = res.media.webpage;
                }
            }
            pages.put(url, webPage);
            ArrayList<Utilities.Callback2<TLRPC.WebPage, TLObject>> cbs = callbacks.remove(url);
            if (cbs != null) {
                for (Utilities.Callback2<TLRPC.WebPage, TLObject> cb : cbs) {
                    cb.run(webPage, err);
                }
            }
        });
    }

    public void apply(LongSparseArray<TLRPC.WebPage> newPages) {
        for (Map.Entry<String, TLRPC.WebPage> entry : pages.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }

            final TLRPC.WebPage updated = newPages.get(entry.getValue().id);
            if (updated != null) {
                entry.setValue(updated);
            }
        }
    }
}
