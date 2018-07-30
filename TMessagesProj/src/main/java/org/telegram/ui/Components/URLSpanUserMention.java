/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

public class URLSpanUserMention extends URLSpanNoUnderline {

    private int currentType;

    public URLSpanUserMention(String url, int type) {
        super(url);
        currentType = type;
    }

    @Override
    public void onClick(View widget) {
        super.onClick(widget);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        if (currentType == 2) {
            ds.setColor(0xffffffff);
        } else if (currentType == 1) {
            ds.setColor(Theme.getColor(Theme.key_chat_messageLinkOut));
        } else {
            ds.setColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        }

        ds.setUnderlineText(false);
    }
}
