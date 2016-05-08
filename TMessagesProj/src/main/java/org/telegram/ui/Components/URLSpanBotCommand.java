/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;

import org.telegram.ui.ActionBar.Theme;

public class URLSpanBotCommand extends URLSpanNoUnderline {

    public static boolean enabled = true;

    public URLSpanBotCommand(String url) {
        super(url);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setColor(enabled ? Theme.MSG_LINK_TEXT_COLOR : Theme.MSG_TEXT_COLOR);
        ds.setUnderlineText(false);
    }
}
