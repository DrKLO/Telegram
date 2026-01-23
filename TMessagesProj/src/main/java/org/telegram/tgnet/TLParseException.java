package org.telegram.tgnet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;

public class TLParseException extends RuntimeException {
    private TLParseException(String message) {
        super(message);
    }

    public static void doThrowOrLog(InputSerializedData stream, String tlTypeName, int constructorId, boolean throwEnabled) {
        final TLDataSourceType dataSourceType = stream != null ? stream.getDataSourceType() : null;
        final String message = String.format("can't parse magic %x in %s. Source: %s", constructorId, tlTypeName, dataSourceType);
        final TLParseException tlParseException = new TLParseException(message);

        FileLog.e(tlParseException, constructorId != 0xcd78e586);
        if (BuildConfig.DEBUG_VERSION && constructorId != 0xcd78e586) {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getGlobalInstance()
                    .postNotificationName(NotificationCenter.tlSchemeParseException, tlParseException);
            });
        }

        if (throwEnabled) {
            throw tlParseException;
        }
    }
}
