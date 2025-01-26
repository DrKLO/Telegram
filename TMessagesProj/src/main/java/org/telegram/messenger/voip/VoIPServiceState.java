package org.telegram.messenger.voip;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;

public interface VoIPServiceState {

    public TLRPC.User getUser();
    public boolean isOutgoing();
    public int getCallState();
    public TL_phone.PhoneCall getPrivateCall();

    public default long getCallDuration() {
        return 0;
    }

    public void acceptIncomingCall();
    public void declineIncomingCall();
    public void stopRinging();

}
