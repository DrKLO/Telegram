/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.HashMap;

public class Action {
    public interface ActionDelegate {
        void ActionDidFinishExecution(Action action, HashMap<String, Object> params);
        void ActionDidFailExecution(Action action);
    }

    public ActionDelegate delegate;

    public void execute(HashMap params) {

    }

    public void cancel() {

    }

    public int state;
}
