/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.ui.ApplicationLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class Datacenter {
    private static final int DATA_VERSION = 4;

    public int datacenterId;
    public ArrayList<String> addresses = new ArrayList<String>();
    public HashMap<String, Integer> ports = new HashMap<String, Integer>();
    public int[] defaultPorts =   new int[] {-1, 80, -1, 443, -1, 443, -1, 80, -1, 443, -1};
    public int[] defaultPorts14 = new int[] {-1, 14, -1, 443, -1, 14,  -1, 80, -1, 14,  -1};
    public boolean authorized;
    public long authSessionId;
    public long authDownloadSessionId;
    public long authUploadSessionId;
    public byte[] authKey;
    public long authKeyId;
    public int lastInitVersion = 0;
    public int overridePort = -1;
    private volatile int currentPortNum = 0;
    private volatile int currentAddressNum = 0;

    public TcpConnection connection;
    public TcpConnection downloadConnection;
    public TcpConnection uploadConnection;

    private ArrayList<ServerSalt> authServerSaltSet = new ArrayList<ServerSalt>();

    public Datacenter() {
        authServerSaltSet = new ArrayList<ServerSalt>();
    }

    public Datacenter(SerializedData data, int version) {
        if (version == 0) {
            datacenterId = data.readInt32();
            String address = data.readString();
            addresses.add(address);
            int port = data.readInt32();
            ports.put(address, port);
            int len = data.readInt32();
            if (len != 0) {
                authKey = data.readData(len);
            }
            len = data.readInt32();
            if (len != 0) {
                authKeyId = data.readInt64();
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
        } else if (version == 1) {
            int currentVersion = data.readInt32();
            if (currentVersion == 2 || currentVersion == 3 || currentVersion == 4) {
                datacenterId = data.readInt32();
                if (currentVersion >= 3) {
                    lastInitVersion = data.readInt32();
                }
                int len = data.readInt32();
                for (int a = 0; a < len; a++) {
                    String address = data.readString();
                    addresses.add(address);
                    ports.put(address, data.readInt32());
                }

                len = data.readInt32();
                if (len != 0) {
                    authKey = data.readData(len);
                }
                if (currentVersion == 4) {
                    authKeyId = data.readInt64();
                } else {
                    len = data.readInt32();
                    if (len != 0) {
                        authKeyId = data.readInt64();
                    }
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
        } else if (version == 2) {

        }
        readCurrentAddressAndPortNum();
    }

    public String getCurrentAddress() {
        if (addresses.isEmpty()) {
            return null;
        }
        if (currentAddressNum >= addresses.size()) {
            currentAddressNum = 0;
        }
        return addresses.get(currentAddressNum);
    }

    public int getCurrentPort() {
        if (ports.isEmpty()) {
            return overridePort == -1 ? 443 : overridePort;
        }

        int[] portsArray = defaultPorts;

        if (overridePort == 14) {
            portsArray = defaultPorts14;
        }

        if (currentPortNum >= defaultPorts.length) {
            currentPortNum = 0;
        }
        int port = portsArray[currentPortNum];
        if (port == -1) {
            if (overridePort != -1) {
                return overridePort;
            }
            String address = getCurrentAddress();
            return ports.get(address);
        }
        return port;
    }

    public void addAddressAndPort(String address, int port) {
        if (addresses.contains(address)) {
            return;
        }
        addresses.add(address);
        ports.put(address, port);
    }

    public void nextAddressOrPort() {
        if (currentPortNum + 1 < defaultPorts.length) {
            currentPortNum++;
        } else {
            if (currentAddressNum + 1 < addresses.size()) {
                currentAddressNum++;
            } else {
                currentAddressNum = 0;
            }
            currentPortNum = 0;
        }
    }

    public void storeCurrentAddressAndPortNum() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("dc" + datacenterId + "port", currentPortNum);
                editor.putInt("dc" + datacenterId + "address", currentAddressNum);
                editor.commit();
            }
        });
    }

    private void readCurrentAddressAndPortNum() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
        currentPortNum = preferences.getInt("dc" + datacenterId + "port", 0);
        currentAddressNum = preferences.getInt("dc" + datacenterId + "address", 0);
    }

    public void replaceAddressesAndPorts(ArrayList<String> newAddresses, HashMap<String, Integer> newPorts) {
        addresses = newAddresses;
        ports = newPorts;
    }

    public void SerializeToStream(SerializedData stream) {
        stream.writeInt32(DATA_VERSION);
        stream.writeInt32(datacenterId);
        stream.writeInt32(lastInitVersion);
        stream.writeInt32(addresses.size());
        for (String address : addresses) {
            stream.writeString(address);
            stream.writeInt32(ports.get(address));
        }
        if (authKey != null) {
            stream.writeInt32(authKey.length);
            stream.writeRaw(authKey);
        } else {
            stream.writeInt32(0);
        }
        stream.writeInt64(authKeyId);
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
        authKeyId = 0;
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

        if (result == 0) {
            FileLog.e("tmessages", "Valid salt not found");
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
