/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;

import com.evildayz.code.telegraher.ThePenisMightierThanTheSword;
import org.telegram.messenger.MessagesController;

public class URLSpanNoUnderlineBold extends URLSpanNoUnderline {

    public URLSpanNoUnderlineBold(String url) {
        super(url != null ? url.replace('\u202E', ' ') : url);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setTypeface(ThePenisMightierThanTheSword.getFont(MessagesController.getGlobalTelegraherUICustomFont("fonts/rmedium.ttf", "rmedium")));
        ds.setUnderlineText(false);
    }
}
