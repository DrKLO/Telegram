/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import com.actionbarsherlock.app.SherlockFragmentActivity;

import org.telegram.ui.ApplicationLoader;

public class PausableActivity extends SherlockFragmentActivity {

    @Override
    protected void onPause() {
        super.onPause();
        ApplicationLoader.lastPauseTime = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApplicationLoader.lastPauseTime = 0;
    }
}
