/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

public class UpdateDatacenterListAction extends Action {
    public int datacenterId;

    public UpdateDatacenterListAction(int id) {
        datacenterId = id;
    }

    public void execute(HashMap params) {
        TLRPC.TL_help_getConfig getConfig = new TLRPC.TL_help_getConfig();

        ConnectionsManager.Instance.performRpc(getConfig, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (delegate == null) {
                    return;
                }
                if (error == null) {
                    TLRPC.TL_config config = (TLRPC.TL_config)response;
                    ArrayList<Datacenter> datacenters = new ArrayList<Datacenter>();
                    for (TLRPC.TL_dcOption datacenterDesc : config.dc_options) {
                        Datacenter datacenter = new Datacenter();
                        datacenter.datacenterId = datacenterDesc.id;

                        datacenter.authSessionId = (long)(MessagesController.random.nextDouble() * Long.MAX_VALUE);

                        datacenter.address = datacenterDesc.ip_address;
                        datacenter.port = datacenterDesc.port;
                        datacenters.add(datacenter);
                    }
                    HashMap<String, Object> result = new HashMap<String, Object>();
                    result.put("datacenters", datacenters);
                    delegate.ActionDidFinishExecution(UpdateDatacenterListAction.this, result);
                } else {
                    delegate.ActionDidFailExecution(UpdateDatacenterListAction.this);
                }
            }
        }, null, true, RPCRequest.RPCRequestClassEnableUnauthorized | RPCRequest.RPCRequestClassGeneric);
    }
}
