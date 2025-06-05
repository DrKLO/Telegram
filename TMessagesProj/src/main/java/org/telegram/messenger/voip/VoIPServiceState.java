package org.telegram.messenger.voip;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;

import java.util.ArrayList;

public interface VoIPServiceState {

    public TLRPC.User getUser();
    public boolean isOutgoing();
    public int getCallState();
    public TL_phone.PhoneCall getPrivateCall();
    public boolean isCallingVideo();

    public default long getCallDuration() {
        return 0;
    }

    public void acceptIncomingCall();
    public void declineIncomingCall();
    public void stopRinging();

    public boolean isConference();
    public TLRPC.GroupCall getGroupCall();
    public ArrayList<TLRPC.GroupCallParticipant> getGroupParticipants();

}
