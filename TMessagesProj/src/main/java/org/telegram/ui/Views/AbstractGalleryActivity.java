/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import org.telegram.objects.MessageObject;

public abstract class AbstractGalleryActivity extends PausableActivity {
	public abstract void topBtn();
    public abstract void didShowMessageObject(MessageObject obj);
}