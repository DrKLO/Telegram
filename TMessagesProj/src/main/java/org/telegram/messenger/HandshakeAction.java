/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import org.telegram.TL.TLClassStore;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class HandshakeAction extends Action implements TcpConnection.TcpConnectionDelegate {

    private ArrayList<Long> processedMessageIds;

    private byte[] authNonce;
    private byte[] authServerNonce;
    private byte[] authNewNonce;

    private byte[] authKey;
    private byte[] authKeyId;

    private boolean processedPQRes;

    private byte[] reqPQMsgData;
    private byte[] reqDHMsgData;
    private byte[] setClientDHParamsMsgData;
    private boolean wasDisconnect = false;

    private long lastOutgoingMessageId;

    int timeDifference;
    ServerSalt serverSalt;
    public Datacenter datacenter;

    public HandshakeAction(Datacenter datacenter) {
        this.datacenter = datacenter;
    }

    public void execute(HashMap params) {
        FileLog.d("tmessages", String.format(Locale.US, "Begin handshake with DC%d", datacenter.datacenterId));
        beginHandshake(true);
    }

    void beginHandshake(boolean dropConnection) {
        if (datacenter.connection == null) {
            datacenter.connection = new TcpConnection(datacenter.datacenterId);
            datacenter.connection.delegate = this;
            datacenter.connection.transportRequestClass = RPCRequest.RPCRequestClassGeneric;
        }

        processedMessageIds = new ArrayList<Long>();
        authNonce = null;
        authServerNonce = null;
        authNewNonce = null;
        authKey = null;
        authKeyId = null;
        processedPQRes = false;
        reqPQMsgData = null;
        reqDHMsgData = null;
        setClientDHParamsMsgData = null;

        if (dropConnection) {
            datacenter.connection.suspendConnection(true);
            datacenter.connection.connect();
        }

        TLRPC.TL_req_pq reqPq = new TLRPC.TL_req_pq();
        byte[] nonceBytes = new byte[16];
        for (int a = 0; a < 16; a++) {
            nonceBytes[a] = (byte)(MessagesController.random.nextDouble() * 255);
        }
        authNonce = reqPq.nonce = nonceBytes;
        reqPQMsgData = sendMessageData(reqPq, generateMessageId());
    }

    final Integer lock = 1;
    static ArrayList<HashMap<String, Object>> serverPublicKeys = null;
    HashMap<String, Object> selectPublicKey(ArrayList<Long> fingerprints) {
        synchronized (lock) {
            if (serverPublicKeys == null) {
                serverPublicKeys = new ArrayList<HashMap<String, Object>>();
                HashMap<String, Object> map;

                map = new HashMap<String, Object>();
                map.put("key", new BigInteger[]{
                        new BigInteger("c150023e2f70db7985ded064759cfecf0af328e69a41daf4d6f01b538135" +
                                "a6f91f8f8b2a0ec9ba9720ce352efcf6c5680ffc424bd634864902de0b4bd6d49f4e580230e" +
                                "3ae97d95c8b19442b3c0a10d8f5633fecedd6926a7f6dab0ddb7d457f9ea81b8465fcd6fffe" +
                                "ed114011df91c059caedaf97625f6c96ecc74725556934ef781d866b34f011fce4d835a0901" +
                                "96e9a5f0e4449af7eb697ddb9076494ca5f81104a305b6dd27665722c46b60e5df680fb16b2" +
                                "10607ef217652e60236c255f6a28315f4083a96791d7214bf64c1df4fd0db1944fb26a2a570" +
                                "31b32eee64ad15a8ba68885cde74a5bfc920f6abf59ba5c75506373e7130f9042da922179251f", 16),
                        new BigInteger("010001", 16)});
                map.put("fingerprint", 0xc3b42b026ce86b21L);
                serverPublicKeys.add(map);
            }
        }

        for (HashMap<String, Object> keyDesc : serverPublicKeys) {
            long keyFingerprint = (Long)keyDesc.get("fingerprint");
            for (long nFingerprint : fingerprints) {
                if (nFingerprint == keyFingerprint) {
                    return keyDesc;
                }
            }
        }
        return null;
    }

    long generateMessageId() {
        long messageId = (long)((((double)System.currentTimeMillis()) * 4294967296.0) / 1000.0);
        if (messageId <= lastOutgoingMessageId) {
            messageId = lastOutgoingMessageId + 1;
        }
        while (messageId % 4 != 0) {
            messageId++;
        }

        lastOutgoingMessageId = messageId;
        return messageId;
    }

    byte[] sendMessageData(TLObject message, long messageId) {
        byte[] messageData;
        SerializedData innerOs = new SerializedData();
        message.serializeToStream(innerOs);
        messageData = innerOs.toByteArray();

        SerializedData messageOs = new SerializedData();
        messageOs.writeInt64(0);
        messageOs.writeInt64(messageId);
        messageOs.writeInt32(messageData.length);
        messageOs.writeRaw(messageData);

        byte[] transportData = messageOs.toByteArray();

        datacenter.connection.sendData(transportData, false, false);

        return transportData;
    }

    void processMessage(TLObject message, long messageId) {
        if (message instanceof TLRPC.TL_resPQ) {
            if (processedPQRes) {
                TLRPC.TL_msgs_ack msgsAck = new TLRPC.TL_msgs_ack();
                msgsAck.msg_ids = new ArrayList<Long>();
                msgsAck.msg_ids.add(messageId);
                sendMessageData(msgsAck, generateMessageId());
                return;
            }

            processedPQRes = true;
            final TLRPC.TL_resPQ resPq = (TLRPC.TL_resPQ)message;
            if (Arrays.equals(authNonce, resPq.nonce)) {
                final HashMap<String, Object> publicKey = selectPublicKey(resPq.server_public_key_fingerprints);
                if (publicKey == null) {
                    FileLog.e("tmessages", "***** Couldn't find valid server public key");
                    beginHandshake(false);
                    return;
                }

                authServerNonce = resPq.server_nonce;

                ByteBuffer data = ByteBuffer.wrap(resPq.pq);
                final long pqf = data.getLong();
                final long messageIdf = messageId;
                Utilities.globalQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {

                        final Utilities.TPFactorizedValue factorizedPq = Utilities.getFactorizedValue(pqf);

                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                ByteBuffer pBytes = ByteBuffer.allocate(4);
                                pBytes.putInt((int)factorizedPq.p);
                                byte[] pData = pBytes.array();

                                ByteBuffer qBytes = ByteBuffer.allocate(4);
                                qBytes.putInt((int)factorizedPq.q);
                                byte[] qData = qBytes.array();

                                TLRPC.TL_req_DH_params reqDH = new TLRPC.TL_req_DH_params();
                                reqDH.nonce = authNonce;
                                reqDH.server_nonce = authServerNonce;
                                reqDH.p = pData;
                                reqDH.q = qData;
                                reqDH.public_key_fingerprint = (Long)publicKey.get("fingerprint");

                                SerializedData os = new SerializedData();

                                TLRPC.TL_p_q_inner_data innerData = new TLRPC.TL_p_q_inner_data();
                                innerData.nonce = authNonce;
                                innerData.server_nonce = authServerNonce;
                                innerData.pq = resPq.pq;
                                innerData.p = reqDH.p;
                                innerData.q = reqDH.q;

                                byte[] nonceBytes = new byte[32];
                                for (int a = 0; a < 32; a++) {
                                    nonceBytes[a] = (byte)(MessagesController.random.nextDouble() * 255);
                                }
                                innerData.new_nonce = authNewNonce = nonceBytes;
                                innerData.serializeToStream(os);

                                byte[] innerDataBytes = os.toByteArray();

                                SerializedData dataWithHash = new SerializedData();
                                dataWithHash.writeRaw(Utilities.computeSHA1(innerDataBytes));
                                dataWithHash.writeRaw(innerDataBytes);
                                byte[] b = new byte[1];
                                while (dataWithHash.length() < 255) {
                                    MessagesController.random.nextBytes(b);
                                    dataWithHash.writeByte(b[0]);
                                }

                                byte[] encryptedBytes = Utilities.encryptWithRSA((BigInteger[])publicKey.get("key"), dataWithHash.toByteArray());
                                SerializedData encryptedData = new SerializedData();
                                encryptedData.writeRaw(encryptedBytes);
                                if (encryptedData.length() < 256) {
                                    SerializedData newEncryptedData = new SerializedData();
                                    for (int i = 0; i < 256 - encryptedData.length(); i++) {
                                        newEncryptedData.writeByte(0);
                                    }
                                    newEncryptedData.writeRaw(encryptedData.toByteArray());
                                    encryptedData = newEncryptedData;
                                }
                                reqDH.encrypted_data = encryptedData.toByteArray();

                                TLRPC.TL_msgs_ack msgsAck = new TLRPC.TL_msgs_ack();
                                msgsAck.msg_ids = new ArrayList<Long>();
                                msgsAck.msg_ids.add(messageIdf);
                                sendMessageData(msgsAck, generateMessageId());

                                reqPQMsgData = null;
                                reqDHMsgData = sendMessageData(reqDH, generateMessageId());
                            }
                        });
                    }
                });
            } else {
                FileLog.e("tmessages", "***** Error: invalid handshake nonce");
                beginHandshake(false);
            }
        } else if (message instanceof TLRPC.Server_DH_Params) {
            if (message instanceof TLRPC.TL_server_DH_params_ok) {
                TLRPC.TL_server_DH_params_ok serverDhParams = (TLRPC.TL_server_DH_params_ok)message;

                SerializedData tmpAesKey = new SerializedData();

                SerializedData newNonceAndServerNonce = new SerializedData();
                newNonceAndServerNonce.writeRaw(authNewNonce);
                newNonceAndServerNonce.writeRaw(authServerNonce);

                SerializedData serverNonceAndNewNonce = new SerializedData();
                serverNonceAndNewNonce.writeRaw(authServerNonce);
                serverNonceAndNewNonce.writeRaw(authNewNonce);
                tmpAesKey.writeRaw(Utilities.computeSHA1(newNonceAndServerNonce.toByteArray()));

                byte[] serverNonceAndNewNonceHash = Utilities.computeSHA1(serverNonceAndNewNonce.toByteArray());
                byte[] serverNonceAndNewNonceHash0_12 = new byte[12];
                System.arraycopy(serverNonceAndNewNonceHash, 0, serverNonceAndNewNonceHash0_12, 0, 12);

                tmpAesKey.writeRaw(serverNonceAndNewNonceHash0_12);

                SerializedData tmpAesIv = new SerializedData();

                byte[] serverNonceAndNewNonceHash12_8 = new byte[8];
                System.arraycopy(serverNonceAndNewNonceHash, 12, serverNonceAndNewNonceHash12_8, 0, 8);
                tmpAesIv.writeRaw(serverNonceAndNewNonceHash12_8);

                SerializedData newNonceAndNewNonce = new SerializedData();
                newNonceAndNewNonce.writeRaw(authNewNonce);
                newNonceAndNewNonce.writeRaw(authNewNonce);
                tmpAesIv.writeRaw(Utilities.computeSHA1(newNonceAndNewNonce.toByteArray()));

                byte[] newNonce0_4 = new byte[4];
                System.arraycopy(authNewNonce, 0, newNonce0_4, 0, 4);
                tmpAesIv.writeRaw(newNonce0_4);

                byte[] answerWithHash = Utilities.aesIgeEncryption(serverDhParams.encrypted_answer, tmpAesKey.toByteArray(), tmpAesIv.toByteArray(), false, false);
                byte[] answerHash = new byte[20];
                System.arraycopy(answerWithHash, 0, answerHash, 0, 20);

                byte[] answerData = new byte[answerWithHash.length - 20];
                System.arraycopy(answerWithHash, 20, answerData, 0, answerWithHash.length - 20);
                boolean hashVerified = false;
                for (int i = 0; i < 16; i++) {
                    byte[] computedAnswerHash = Utilities.computeSHA1(answerData);
                    if (Arrays.equals(computedAnswerHash, answerHash)) {
                        hashVerified = true;
                        break;
                    }
                    byte[] answerData2 = new byte[answerData.length - 1];
                    System.arraycopy(answerData, 0, answerData2, 0, answerData.length - 1);
                    answerData = answerData2;
                }

                if (!hashVerified) {
                    FileLog.e("tmessages", "***** Couldn't decode DH params");
                    beginHandshake(false);
                    return;
                }

                SerializedData answerIs = new SerializedData(answerData);
                int constructor = answerIs.readInt32();
                TLRPC.TL_server_DH_inner_data dhInnerData = (TLRPC.TL_server_DH_inner_data)TLClassStore.Instance().TLdeserialize(answerIs, constructor);

                if (!(dhInnerData instanceof TLRPC.TL_server_DH_inner_data)) {
                    FileLog.e("tmessages", "***** Couldn't parse decoded DH params");
                    beginHandshake(false);
                    return;
                }

                if (!Utilities.isGoodPrime(dhInnerData.dh_prime)) {
                    throw new RuntimeException("bad prime");
                }

                if (!Arrays.equals(authNonce, dhInnerData.nonce)) {
                    FileLog.e("tmessages", "***** Invalid DH nonce");
                    beginHandshake(false);
                    return;
                }
                if (!Arrays.equals(authServerNonce, dhInnerData.server_nonce)) {
                    FileLog.e("tmessages", "***** Invalid DH server nonce");
                    beginHandshake(false);
                    return;
                }

                byte[] b = new byte[256];
                for (int a = 0; a < 256; a++) {
                    b[a] = (byte)(MessagesController.random.nextDouble() * 255);
                }

                BigInteger dhBI = new BigInteger(1, dhInnerData.dh_prime);
                BigInteger i_g_b = BigInteger.valueOf(dhInnerData.g);
                i_g_b = i_g_b.modPow(new BigInteger(1, b), dhBI);
                byte[] g_b = i_g_b.toByteArray();

                BigInteger i_authKey = new BigInteger(1, dhInnerData.g_a);
                i_authKey = i_authKey.modPow(new BigInteger(1, b), dhBI);

                authKey = i_authKey.toByteArray();
                if (authKey.length > 256) {
                    byte[] correctedAuth = new byte[256];
                    System.arraycopy(authKey, 1, correctedAuth, 0, 256);
                    authKey = correctedAuth;
                } else if (authKey.length < 256) {
                    byte[] correctedAuth = new byte[256];
                    System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
                    for (int a = 0; a < 256 - authKey.length; a++) {
                        authKey[a] = 0;
                    }
                    authKey = correctedAuth;
                }
                byte[] authKeyHash = Utilities.computeSHA1(authKey);
                authKeyId = new byte[8];
                System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);

                SerializedData serverSaltData = new SerializedData();
                for (int i = 7; i >= 0; i--) {
                    byte a_ = authNewNonce[i];
                    byte b_ = authServerNonce[i];
                    byte x = (byte)(a_ ^ b_);
                    serverSaltData.writeByte(x);
                }
                ByteBuffer saltBuffer = ByteBuffer.wrap(serverSaltData.toByteArray());

                timeDifference = dhInnerData.server_time - (int)(System.currentTimeMillis() / 1000);

                serverSalt = new ServerSalt();
                serverSalt.validSince = (int)(System.currentTimeMillis() / 1000) + timeDifference;
                serverSalt.validUntil = (int)(System.currentTimeMillis() / 1000) + timeDifference + 30 * 60;
                serverSalt.value = saltBuffer.getLong();

                FileLog.d("tmessages", String.format(Locale.US, "===== Time difference: %d", timeDifference));

                TLRPC.TL_client_DH_inner_data clientInnerData = new TLRPC.TL_client_DH_inner_data();
                clientInnerData.nonce = authNonce;
                clientInnerData.server_nonce = authServerNonce;
                clientInnerData.g_b = g_b;
                clientInnerData.retry_id = 0;
                SerializedData os = new SerializedData();
                clientInnerData.serializeToStream(os);
                byte[] clientInnerDataBytes = os.toByteArray();

                SerializedData clientDataWithHash = new SerializedData();
                clientDataWithHash.writeRaw(Utilities.computeSHA1(clientInnerDataBytes));
                clientDataWithHash.writeRaw(clientInnerDataBytes);
                byte[] bb = new byte[1];
                while (clientDataWithHash.length() % 16 != 0) {
                    MessagesController.random.nextBytes(bb);
                    clientDataWithHash.writeByte(bb[0]);
                }

                TLRPC.TL_set_client_DH_params setClientDhParams = new TLRPC.TL_set_client_DH_params();
                setClientDhParams.nonce = authNonce;
                setClientDhParams.server_nonce = authServerNonce;
                setClientDhParams.encrypted_data = Utilities.aesIgeEncryption(clientDataWithHash.toByteArray(), tmpAesKey.toByteArray(), tmpAesIv.toByteArray(), true, false);

                TLRPC.TL_msgs_ack msgsAck = new TLRPC.TL_msgs_ack();
                msgsAck.msg_ids = new ArrayList<Long>();
                msgsAck.msg_ids.add(messageId);
                sendMessageData(msgsAck, generateMessageId());

                reqDHMsgData = null;
                setClientDHParamsMsgData = sendMessageData(setClientDhParams, generateMessageId());
            } else {
                FileLog.e("tmessages", "***** Couldn't set DH params");
                beginHandshake(false);
            }
        } else if (message instanceof TLRPC.Set_client_DH_params_answer) {
            TLRPC.Set_client_DH_params_answer dhAnswer = (TLRPC.Set_client_DH_params_answer)message;

            if (!Arrays.equals(authNonce, dhAnswer.nonce)) {
                FileLog.e("tmessages", "***** Invalid DH answer nonce");
                beginHandshake(false);
                return;
            }
            if (!Arrays.equals(authServerNonce, dhAnswer.server_nonce)) {
                FileLog.e("tmessages", "***** Invalid DH answer server nonce");
                beginHandshake(false);
                return;
            }

            reqDHMsgData = null;

            TLRPC.TL_msgs_ack msgsAck = new TLRPC.TL_msgs_ack();
            msgsAck.msg_ids = new ArrayList<Long>();
            msgsAck.msg_ids.add(messageId);
            sendMessageData(msgsAck, generateMessageId());

            byte[] authKeyAuxHashFull = Utilities.computeSHA1(authKey);
            byte[] authKeyAuxHash = new byte[8];
            System.arraycopy(authKeyAuxHashFull, 0, authKeyAuxHash, 0, 8);

            SerializedData newNonce1 = new SerializedData();
            newNonce1.writeRaw(authNewNonce);
            newNonce1.writeByte(1);
            newNonce1.writeRaw(authKeyAuxHash);
            byte[] newNonceHash1Full = Utilities.computeSHA1(newNonce1.toByteArray());
            byte[] newNonceHash1 = new byte[16];
            System.arraycopy(newNonceHash1Full, newNonceHash1Full.length - 16, newNonceHash1, 0, 16);

            SerializedData newNonce2 = new SerializedData();
            newNonce2.writeRaw(authNewNonce);
            newNonce2.writeByte(2);
            newNonce2.writeRaw(authKeyAuxHash);
            byte[] newNonceHash2Full = Utilities.computeSHA1(newNonce2.toByteArray());
            byte[] newNonceHash2 = new byte[16];
            System.arraycopy(newNonceHash2Full, newNonceHash2Full.length - 16, newNonceHash2, 0, 16);

            SerializedData newNonce3 = new SerializedData();
            newNonce3.writeRaw(authNewNonce);
            newNonce3.writeByte(3);
            newNonce3.writeRaw(authKeyAuxHash);
            byte[] newNonceHash3Full = Utilities.computeSHA1(newNonce3.toByteArray());
            byte[] newNonceHash3 = new byte[16];
            System.arraycopy(newNonceHash3Full, newNonceHash3Full.length - 16, newNonceHash3, 0, 16);

            if (message instanceof TLRPC.TL_dh_gen_ok) {
                TLRPC.TL_dh_gen_ok dhGenOk = (TLRPC.TL_dh_gen_ok)message;
                if (!Arrays.equals(newNonceHash1, dhGenOk.new_nonce_hash1)) {
                    FileLog.e("tmessages", "***** Invalid DH answer nonce hash 1");
                    beginHandshake(false);
                    return;
                }

                FileLog.d("tmessages", String.format("Handshake with DC%d completed", datacenter.datacenterId));
                datacenter.connection.delegate = null;

                final Action parent = this;
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        datacenter.authKey = authKey;
                        datacenter.authKeyId = authKeyId;
                        datacenter.addServerSalt(serverSalt);
                        HashMap<String, Object> resultDict = new HashMap<String, Object>();
                        resultDict.put("timeDifference", timeDifference);
                        if (delegate != null) {
                            delegate.ActionDidFinishExecution(parent, resultDict);
                        }
                    }
                });
            } else if (message instanceof TLRPC.TL_dh_gen_retry) {
                TLRPC.TL_dh_gen_retry dhRetry = (TLRPC.TL_dh_gen_retry)message;
                if (!Arrays.equals(newNonceHash2, dhRetry.new_nonce_hash2)) {
                    FileLog.e("tmessages", "***** Invalid DH answer nonce hash 2");
                    beginHandshake(false);
                    return;
                }
                FileLog.d("tmessages", "***** Retry DH");
                beginHandshake(false);
            } else if (message instanceof TLRPC.TL_dh_gen_fail) {
                TLRPC.TL_dh_gen_fail dhFail = (TLRPC.TL_dh_gen_fail)message;
                if (!Arrays.equals(newNonceHash3, dhFail.new_nonce_hash3)) {
                    FileLog.e("tmessages", "***** Invalid DH answer nonce hash 3");
                    beginHandshake(false);
                    return;
                }
                FileLog.d("tmessages", "***** Server declined DH params");
                beginHandshake(false);
            } else {
                FileLog.e("tmessages", "***** Unknown DH params response");
                beginHandshake(false);
            }
        } else {
            TLRPC.TL_msgs_ack msgsAck = new TLRPC.TL_msgs_ack();
            msgsAck.msg_ids = new ArrayList<Long>();
            msgsAck.msg_ids.add(messageId);
            sendMessageData(msgsAck, generateMessageId());
        }
    }

    @Override
    public void tcpConnectionProgressChanged(TcpConnection connection, long messageId, int currentSize, int length) {

    }

    @Override
    public void tcpConnectionClosed(TcpConnection connection) {
        wasDisconnect = true;
    }

    @Override
    public void tcpConnectionConnected(TcpConnection connection) {
        if (!wasDisconnect) {
            return;
        }
        if (reqPQMsgData != null) {
            datacenter.connection.sendData(reqPQMsgData, false, false);
        } else if (reqDHMsgData != null) {
            datacenter.connection.sendData(reqDHMsgData, false, false);
        } else if (setClientDHParamsMsgData != null) {
            datacenter.connection.sendData(setClientDHParamsMsgData, false, false);
        }
    }

    @Override
    public void tcpConnectionQuiackAckReceived(TcpConnection connection, int ack) {

    }

    @Override
    public void tcpConnectionReceivedData(TcpConnection connection, byte[] data) {
        SerializedData is = new SerializedData(data);

        long keyId = is.readInt64();

        if (keyId == 0) {
            long messageId = is.readInt64();
            if (processedMessageIds.contains(messageId)) {
                FileLog.d("tmessages", String.format("===== Duplicate message id %d received, ignoring", messageId));
                return;
            }
            int messageLength = is.readInt32();

            int constructor = is.readInt32();
            TLObject object = TLClassStore.Instance().TLdeserialize(is, constructor);

            if (object != null) {
                processedMessageIds.add(messageId);
            }
            processMessage(object, messageId);
        } else {
            FileLog.d("tmessages", "***** Received encrypted message while in handshake, restarting");
            beginHandshake(true);
        }
    }
}
