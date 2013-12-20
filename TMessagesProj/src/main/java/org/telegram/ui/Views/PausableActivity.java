/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.support.v7.app.ActionBarActivity;

import org.telegram.ui.ApplicationLoader;

public class PausableActivity extends ActionBarActivity {

    @Override
    protected void onPause() {
        super.onPause();
        ApplicationLoader.lastPauseTime = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApplicationLoader.resetLastPauseTime();
    }
}
