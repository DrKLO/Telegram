package org.telegram.ui.Components.blur3;

import org.telegram.messenger.MediaDataController;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;

public class Blur3HashImpl implements IBlur3Hash {
    private long hash;
    private boolean unsupported;

    public void start() {
        hash = 0;
        unsupported = false;
    }

    public long get() {
        return unsupported ? -1 : hash;
    }

    public boolean isUnsupported() {
        return unsupported;
    }

    @Override
    public void add(long value) {
        hash = MediaDataController.calcHash(hash, value);
    }

    public void unsupported() {
        unsupported = true;
    }
}
