package org.telegram.messenger.voip;

import org.telegram.tgnet.TLRPC;

public interface VoIPServiceState {

    public TLRPC.User getUser();
    public boolean isOutgoing();
    public int getCallState();
    public TLRPC.PhoneCall getPrivateCall();

    public default long getCallDuration() {
        return 0;
    }

    public void acceptIncomingCall();
    public void declineIncomingCall();
    public void stopRinging();

}
