/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

public class DialogObject {

    public static boolean isChannel(TLRPC.TL_dialog dialog) {
        return dialog != null && (dialog.flags & 1) != 0;
    }
}
