/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;

public class URLSpanUserMentionPhotoViewer extends URLSpanUserMention {

    public URLSpanUserMentionPhotoViewer(String url, boolean isOutOwner) {
        super(url, 2);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setColor(0xffffffff);
        ds.setUnderlineText(false);
    }
}
