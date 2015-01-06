/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import java.util.ArrayList;

import jawnae.pyronet.PyroClientAdapter;

public class ConnectionContext extends PyroClientAdapter {

    public static final boolean isDebugSession = false;
    private long sessionId;

    private ArrayList<Long> processedMessageIds = new ArrayList<>();
    private ArrayList<Long> messagesIdsForConfirmation = new ArrayList<>();
    private ArrayList<Long> processedSessionChanges = new ArrayList<>();
    private int nextSeqNo = 0;

    public ConnectionContext() {
        genereateNewSessionId();
    }

    public void recreateSession() {
        processedMessageIds.clear();
        messagesIdsForConfirmation.clear();
        processedSessionChanges.clear();
        nextSeqNo = 0;

        genereateNewSessionId();
    }

    private void genereateNewSessionId() {
        long newSessionId = Utilities.random.nextLong();
        sessionId = isDebugSession ? (0xabcd000000000000L | (newSessionId & 0x0000ffffffffffffL)) : newSessionId;
    }

    public void setSessionId(long id) {
        sessionId = id;
    }

    public long getSissionId() {
        return sessionId;
    }

    public int generateMessageSeqNo(boolean increment) {
        int value = nextSeqNo;
        if (increment) {
            nextSeqNo++;
        }
        return value * 2 + (increment ? 1 : 0);
    }

    boolean isMessageIdProcessed(long messageId) {
        return processedMessageIds.contains(messageId);
    }

    public void addProcessedMessageId(long messageId) {
        if (processedMessageIds.size() > 1000 + 224) {
            for (int a = 0; a < Math.min(processedMessageIds.size(), 225); a++) {
                processedMessageIds.remove(0);
            }
        }
        processedMessageIds.add(messageId);
    }

    public boolean hasMessagesToConfirm() {
        return !messagesIdsForConfirmation.isEmpty();
    }

    public void addMessageToConfirm(long messageId) {
        if (messagesIdsForConfirmation.contains(messageId)) {
            return;
        }
        messagesIdsForConfirmation.add(messageId);
    }

    public NetworkMessage generateConfirmationRequest() {
        NetworkMessage networkMessage = null;

        if (!messagesIdsForConfirmation.isEmpty()) {
            TLRPC.TL_msgs_ack msgAck = new TLRPC.TL_msgs_ack();
            msgAck.msg_ids = new ArrayList<>();
            msgAck.msg_ids.addAll(messagesIdsForConfirmation);

            ByteBufferDesc os = new ByteBufferDesc(true);
            msgAck.serializeToStream(os);

            networkMessage = new NetworkMessage();
            networkMessage.protoMessage = new TLRPC.TL_protoMessage();

            networkMessage.protoMessage.msg_id = ConnectionsManager.getInstance().generateMessageId();
            networkMessage.protoMessage.seqno = generateMessageSeqNo(false);

            networkMessage.protoMessage.bytes = os.length();
            networkMessage.protoMessage.body = msgAck;

            messagesIdsForConfirmation.clear();
        }

        return networkMessage;
    }

    public boolean isSessionProcessed(long sessionId) {
        return processedSessionChanges.contains(sessionId);
    }

    public void addProcessedSession(long sessionId) {
        processedSessionChanges.add(sessionId);
    }
}
