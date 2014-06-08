/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */
package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Collection;

public class ChangeAwareArrayList<T> extends ArrayList<T> {
    protected int oldModCount;

    public ChangeAwareArrayList() {
        oldModCount = modCount;
    }

    public ChangeAwareArrayList(int capacity) {
        super(capacity);
        oldModCount = modCount;
    }

    public ChangeAwareArrayList(Collection<? extends T> collection) {
        super(collection);
        oldModCount = modCount;
    }

    public boolean peekIsChanged() {
        return oldModCount != modCount;
    }

    public boolean isChanged() {
        boolean ret = peekIsChanged();
        oldModCount = modCount;
        return ret;
    }
}
