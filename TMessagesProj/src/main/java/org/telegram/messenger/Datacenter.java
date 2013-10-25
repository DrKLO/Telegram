/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.util.Log;

import org.telegram.TL.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Datacenter {
    public int datacenterId;
    public String address;
    public int port;
    public boolean authorized;
    public long authSessionId;
    public long authDownloadSessionId;
    public long authUploadSessionId;
    public byte[] authKey;
    public byte[] authKeyId;

    public TcpConnection connection;
    public TcpConnection downloadConnection;
    public TcpConnection uploadConnection;

    private ArrayList<ServerSalt> authServerSaltSet = new ArrayList<ServerSalt>();

    public Datacenter() {
        authServerSaltSet = new ArrayList<ServerSalt>();
    }

    public Datacenter(SerializedData data) {
        datacenterId = data.readInt32();
        address = data.readString();
        port = data.readInt32();
        if (port == 25) {
            port = 443;
        }
        int len = data.readInt32();
        if (len != 0) {
            authKey = data.readData(len);
        }
        len = data.readInt32();
        if (len != 0) {
            authKeyId = data.readData(len);
        }
        authorized = data.readInt32() != 0;
        len = data.readInt32();
        for (int a = 0; a < len; a++) {
            ServerSalt salt = new ServerSalt();
            salt.validSince = data.readInt32();
            salt.validUntil = data.readInt32();
            salt.value = data.readInt64();
            if (authServerSaltSet == null) {
                authServerSaltSet = new ArrayList<ServerSalt>();
            }
            authServerSaltSet.add(salt);
        }
    }

    public void SerializeToStream(SerializedData stream) {
        stream.writeInt32(datacenterId);
        stream.writeString(address);
        stream.writeInt32(port);
        if (authKey != null) {
            stream.writeInt32(authKey.length);
            stream.writeRaw(authKey);
        } else {
            stream.writeInt32(0);
        }
        if (authKeyId != null) {
            stream.writeInt32(authKeyId.length);
            stream.writeRaw(authKeyId);
        } else {
            stream.writeInt32(0);
        }
        stream.writeInt32(authorized ? 1 : 0);
        stream.writeInt32(authServerSaltSet.size());
        for (ServerSalt salt : authServerSaltSet) {
            stream.writeInt32(salt.validSince);
            stream.writeInt32(salt.validUntil);
            stream.writeInt64(salt.value);
        }
    }

    public void clear() {
        authKey = null;
        authKeyId = null;
        authorized = false;
        authServerSaltSet.clear();
    }

    public void clearServerSalts() {
        authServerSaltSet.clear();
    }

    public long selectServerSalt(int date) {
        boolean cleanupNeeded = false;

        long result = 0;
        int maxRemainingInterval = 0;

        for (ServerSalt salt : authServerSaltSet) {
            if (salt.validUntil < date || (salt.validSince == 0 && salt.validUntil == Integer.MAX_VALUE)) {
                cleanupNeeded = true;
            } else if (salt.validSince <= date && salt.validUntil > date) {
                if (maxRemainingInterval == 0 || Math.abs(salt.validUntil - date) > maxRemainingInterval) {
                    maxRemainingInterval = Math.abs(salt.validUntil - date);
                    result = salt.value;
                }
            }
        }

        if (cleanupNeeded) {
            for (int i = 0; i < authServerSaltSet.size(); i++) {
                ServerSalt salt = authServerSaltSet.get(i);
                if (salt.validUntil < date) {
                    authServerSaltSet.remove(i);
                    i--;
                }
            }
        }

        if (ConnectionsManager.DEBUG_VERSION) {
            if (result == 0) {
                Log.e("tmessages", "Valid salt not found", null);
            }
        }

        return result;
    }

    private class SaltComparator implements Comparator<ServerSalt> {
        @Override
        public int compare(ServerSalt o1, ServerSalt o2) {
            if (o1.validSince < o2.validSince) {
                return -1;
            } else if (o1.validSince > o2.validSince) {
                return 1;
            }
            return 0;
        }
    }

    public void mergeServerSalts(int date, ArrayList<TLRPC.TL_futureSalt> salts) {
        if (salts == null) {
            return;
        }
        ArrayList<Long> existingSalts = new ArrayList<Long>(authServerSaltSet.size());

        for (ServerSalt salt : authServerSaltSet) {
            existingSalts.add(salt.value);
        }
        for (TLRPC.TL_futureSalt saltDesc : salts) {
            long salt = saltDesc.salt;
            if (!existingSalts.contains(salt) && saltDesc.valid_until > date) {
                ServerSalt serverSalt = new ServerSalt();
                serverSalt.validSince = saltDesc.valid_since;
                serverSalt.validUntil = saltDesc.valid_until;
                serverSalt.value = salt;
                authServerSaltSet.add(serverSalt);
            }
        }
        Collections.sort(authServerSaltSet, new SaltComparator());
    }

    public void addServerSalt(ServerSalt serverSalt) {
        for (ServerSalt salt : authServerSaltSet) {
            if (salt.value == serverSalt.value) {
                return;
            }
        }
        authServerSaltSet.add(serverSalt);
        Collections.sort(authServerSaltSet, new SaltComparator());
    }

    boolean containsServerSalt(long value) {
        for (ServerSalt salt : authServerSaltSet) {
            if (salt.value == value) {
                return true;
            }
        }
        return false;
    }
}
