package org.telegram.ui.Wallet;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_toncenter;

import java.util.Locale;

final class GramWalletDns {
    private GramWalletDns() { }

    static String normalizeDomain(String value) {
        if (value == null || value.length() > 126) return null;
        final String domain = value.toLowerCase(Locale.ROOT);
        return domain.matches("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*\\.ton") ? domain : null;
    }

    static int resolve(int account, String domain, Utilities.Callback<String> callback) {
        final TL_toncenter.performApiRequest request = new TL_toncenter.performApiRequest();
        request.endpoint = "/api/v3/dns/records";
        request.query = "domain=" + Uri.encode(domain) + "&limit=2";
        return ConnectionsManager.getInstance(account).sendRequestTyped(request, AndroidUtilities::runOnUIThread, (response, error) -> {
            String address = null;
            if (error == null && response != null && response.response != null) {
                try {
                    address = parseWalletAddress(domain, new JSONObject(response.response.data));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
            }
            callback.run(address);
        }, MessagesController.getInstance(account).webFileDatacenterId, 0);
    }

    static String parseWalletAddress(String domain, JSONObject response) throws JSONException {
        final JSONArray records = response.optJSONArray("records");
        if (records == null || records.length() != 1) return null;
        final JSONObject record = records.getJSONObject(0);
        if (!domain.equals(record.optString("domain"))) return null;
        // Only the wallet record is a payment destination, never the NFT or its owner.
        final String address = record.optString("dns_wallet", "");
        return WalletEngine2.isValidAddress(address) ? address : null;
    }
}
