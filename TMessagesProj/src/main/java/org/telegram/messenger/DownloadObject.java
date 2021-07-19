/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;

public class DownloadObject {
    public TLObject object;
    public int type;
    public long id;
    public boolean secret;
    public boolean forceCache;
    public String parent;
}
