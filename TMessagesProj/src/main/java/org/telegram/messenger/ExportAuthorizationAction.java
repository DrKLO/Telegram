/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.HashMap;

public class ExportAuthorizationAction extends Action {
    public Datacenter datacenter;
    TLRPC.TL_auth_exportedAuthorization exportedAuthorization;
    int retryCount;

    public ExportAuthorizationAction(Datacenter d) {
        datacenter = d;
    }

    public void execute(HashMap options) {
        if (datacenter == null) {
            delegate.ActionDidFailExecution(this);
            return;
        }
        beginExport();
    }

    void beginExport() {
        TLRPC.TL_auth_exportAuthorization exportAuthorization = new TLRPC.TL_auth_exportAuthorization();
        exportAuthorization.dc_id = datacenter.datacenterId;

        ConnectionsManager.getInstance().performRpc(exportAuthorization, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (delegate == null) {
                    return;
                }
                if (error == null) {
                    exportedAuthorization = (TLRPC.TL_auth_exportedAuthorization)response;
                    beginImport();
                } else {
                    retryCount++;
                    if (retryCount >= 3) {
                        delegate.ActionDidFailExecution(ExportAuthorizationAction.this);
                    } else {
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                beginExport();
                            }
                        }, retryCount * 1500);
                    }
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    void beginImport() {
        TLRPC.TL_auth_importAuthorization importAuthorization = new TLRPC.TL_auth_importAuthorization();
        importAuthorization.bytes = exportedAuthorization.bytes;
        importAuthorization.id = exportedAuthorization.id;

        ConnectionsManager.getInstance().performRpc(importAuthorization, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (delegate == null) {
                    return;
                }
                if (error == null) {
                    delegate.ActionDidFinishExecution(ExportAuthorizationAction.this, null);
                } else {
                    exportedAuthorization = null;
                    retryCount++;
                    if (retryCount >= 3) {
                        delegate.ActionDidFailExecution(ExportAuthorizationAction.this);
                    } else {
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                beginExport();
                            }
                        }, retryCount * 1500);
                    }
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassEnableUnauthorized, datacenter.datacenterId);
    }
}
