/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.tgnet;

import java.io.File;

public class FileLoadOperation {

    private int address;
    private boolean isForceRequest;
    private FileLoadOperationDelegate delegate;
    private boolean started;

    public FileLoadOperation(int dc_id, long id, long volume_id, long access_hash, int local_id, byte[] encKey, byte[] encIv, String extension, int version, int size, File dest, File temp, FileLoadOperationDelegate fileLoadOperationDelegate) {
        address = native_createLoadOpetation(dc_id, id, volume_id, access_hash, local_id, encKey, encIv, extension, version, size, dest.getAbsolutePath(), temp.getAbsolutePath(), fileLoadOperationDelegate);
        delegate = fileLoadOperationDelegate;
    }

    public void setForceRequest(boolean forceRequest) {
        isForceRequest = forceRequest;
    }

    public boolean isForceRequest() {
        return isForceRequest;
    }

    public void start() {
        if (started) {
            return;
        }
        if (address == 0) {
            delegate.onFailed(0);
            return;
        }
        started = true;
        native_startLoadOperation(address);
    }

    public void cancel() {
        if (!started || address == 0) {
            return;
        }
        native_cancelLoadOperation(address);
    }

    public boolean wasStarted() {
        return started;
    }

    public static native int native_createLoadOpetation(int dc_id, long id, long volume_id, long access_hash, int local_id, byte[] encKey, byte[] encIv, String extension, int version, int size, String dest, String temp, Object delegate);
    public static native void native_startLoadOperation(int address);
    public static native void native_cancelLoadOperation(int address);
}
