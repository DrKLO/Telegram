/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.ArrayList;

public class RPCRequest {
    public interface RPCRequestDelegate {
        void run(TLObject response, TLRPC.TL_error error);
    }
    public interface RPCQuickAckDelegate {
        void quickAck();
    }

    public static int RPCRequestClassGeneric = 1;
    public static int RPCRequestClassDownloadMedia = 2;
    public static int RPCRequestClassUploadMedia = 4;
    public static int RPCRequestClassEnableUnauthorized = 8;
    public static int RPCRequestClassFailOnServerErrors = 16;
    public static int RPCRequestClassCanCompress = 32;
    public static int RPCRequestClassPush = 64;
    public static int RPCRequestClassWithoutLogin = 128;
    public static int RPCRequestClassTryDifferentDc = 256;
    public static int RPCRequestClassForceDownload = 512;

    static int RPCRequestClassTransportMask = (RPCRequestClassGeneric | RPCRequestClassDownloadMedia | RPCRequestClassUploadMedia);

    long token;
    boolean cancelled;

    int serverFailureCount;
    int flags;
    boolean wait = false;
    boolean salt = false;
    protected int retryCount = 0;
    protected int lastResendTime = 0;
    protected boolean completed = false;

    TLObject rawRequest;
    TLObject rpcRequest;
    int serializedLength;

    RPCRequestDelegate completionBlock;
    RPCQuickAckDelegate quickAckBlock;

    boolean requiresCompletion;

    long runningMessageId;
    int runningMessageSeqNo;
    int runningDatacenterId;
    int transportChannelToken;

    int runningStartTime;
    int runningMinStartTime;

    boolean confirmed;

    boolean initRequest = false;

    ArrayList<Long> respondsToMessageIds = new ArrayList<>();

    public void addRespondMessageId(long messageId) {
        respondsToMessageIds.add(messageId);
    }

    boolean respondsToMessageId(long messageId) {
        return runningMessageId == messageId || respondsToMessageIds.contains(messageId);
    }
}

