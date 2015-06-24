/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class Datacenter {
    private static final int DATA_VERSION = 5;

    public int datacenterId;
    public ArrayList<String> addressesIpv4 = new ArrayList<>();
    public ArrayList<String> addressesIpv6 = new ArrayList<>();
    public ArrayList<String> addressesIpv4Download = new ArrayList<>();
    public ArrayList<String> addressesIpv6Download = new ArrayList<>();
    public HashMap<String, Integer> ports = new HashMap<>();
    public int[] defaultPorts =   new int[] {-1, 80, -1, 443, -1, 443, -1, 80, -1, 443, -1};
    public int[] defaultPorts8888 = new int[] {-1, 8888, -1, 443, -1, 8888,  -1, 80, -1, 8888,  -1};
    public boolean authorized;
    public byte[] authKey;
    public long authKeyId;
    public int lastInitVersion = 0;
    public int overridePort = -1;

    private volatile int currentPortNumIpv4 = 0;
    private volatile int currentAddressNumIpv4 = 0;
    private volatile int currentPortNumIpv6 = 0;
    private volatile int currentAddressNumIpv6 = 0;
    private volatile int currentPortNumIpv4Download = 0;
    private volatile int currentAddressNumIpv4Download = 0;
    private volatile int currentPortNumIpv6Download = 0;
    private volatile int currentAddressNumIpv6Download = 0;

    public TcpConnection connection;
    private TcpConnection downloadConnection;
    private TcpConnection uploadConnection;
    public TcpConnection pushConnection;

    private ArrayList<ServerSalt> authServerSaltSet = new ArrayList<>();

    public Datacenter() {
        authServerSaltSet = new ArrayList<>();
    }

    public Datacenter(SerializedData data, int version) {
        if (version == 0) {
            datacenterId = data.readInt32(false);
            String address = data.readString(false);
            addressesIpv4.add(address);
            int port = data.readInt32(false);
            ports.put(address, port);
            int len = data.readInt32(false);
            if (len != 0) {
                authKey = data.readData(len, false);
            }
            len = data.readInt32(false);
            if (len != 0) {
                authKeyId = data.readInt64(false);
            }
            authorized = data.readInt32(false) != 0;
            len = data.readInt32(false);
            for (int a = 0; a < len; a++) {
                ServerSalt salt = new ServerSalt();
                salt.validSince = data.readInt32(false);
                salt.validUntil = data.readInt32(false);
                salt.value = data.readInt64(false);
                if (authServerSaltSet == null) {
                    authServerSaltSet = new ArrayList<>();
                }
                authServerSaltSet.add(salt);
            }
        } else if (version == 1) {
            int currentVersion = data.readInt32(false);
            if (currentVersion >= 2 && currentVersion <= 5) {
                datacenterId = data.readInt32(false);
                if (currentVersion >= 3) {
                    lastInitVersion = data.readInt32(false);
                }
                int len = data.readInt32(false);
                for (int a = 0; a < len; a++) {
                    String address = data.readString(false);
                    addressesIpv4.add(address);
                    ports.put(address, data.readInt32(false));
                }
                if (currentVersion >= 5) {
                    len = data.readInt32(false);
                    for (int a = 0; a < len; a++) {
                        String address = data.readString(false);
                        addressesIpv6.add(address);
                        ports.put(address, data.readInt32(false));
                    }
                    len = data.readInt32(false);
                    for (int a = 0; a < len; a++) {
                        String address = data.readString(false);
                        addressesIpv4Download.add(address);
                        ports.put(address, data.readInt32(false));
                    }
                    len = data.readInt32(false);
                    for (int a = 0; a < len; a++) {
                        String address = data.readString(false);
                        addressesIpv6Download.add(address);
                        ports.put(address, data.readInt32(false));
                    }
                }

                len = data.readInt32(false);
                if (len != 0) {
                    authKey = data.readData(len, false);
                }
                if (currentVersion >= 4) {
                    authKeyId = data.readInt64(false);
                } else {
                    len = data.readInt32(false);
                    if (len != 0) {
                        authKeyId = data.readInt64(false);
                    }
                }
                authorized = data.readInt32(false) != 0;
                len = data.readInt32(false);
                for (int a = 0; a < len; a++) {
                    ServerSalt salt = new ServerSalt();
                    salt.validSince = data.readInt32(false);
                    salt.validUntil = data.readInt32(false);
                    salt.value = data.readInt64(false);
                    if (authServerSaltSet == null) {
                        authServerSaltSet = new ArrayList<>();
                    }
                    authServerSaltSet.add(salt);
                }
            }
        } else if (version == 2) {

        }
        readCurrentAddressAndPortNum();
    }

    public void switchTo443Port() {
        for (int a = 0; a < addressesIpv4.size(); a++) {
            if (ports.get(addressesIpv4.get(a)) == 443) {
                currentAddressNumIpv4 = a;
                currentPortNumIpv4 = 0;
                break;
            }
        }
        for (int a = 0; a < addressesIpv6.size(); a++) {
            if (ports.get(addressesIpv6.get(a)) == 443) {
                currentAddressNumIpv6 = a;
                currentPortNumIpv6 = 0;
                break;
            }
        }
        for (int a = 0; a < addressesIpv4Download.size(); a++) {
            if (ports.get(addressesIpv4Download.get(a)) == 443) {
                currentAddressNumIpv4Download = a;
                currentPortNumIpv4Download = 0;
                break;
            }
        }
        for (int a = 0; a < addressesIpv6Download.size(); a++) {
            if (ports.get(addressesIpv6Download.get(a)) == 443) {
                currentAddressNumIpv6Download = a;
                currentPortNumIpv6Download = 0;
                break;
            }
        }
    }

    public String getCurrentAddress(int flags) {
        int currentAddressNum;
        ArrayList<String> addresses;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                currentAddressNum = currentAddressNumIpv6Download;
                addresses = addressesIpv6Download;
            } else {
                currentAddressNum = currentAddressNumIpv4Download;
                addresses = addressesIpv4Download;
            }
        } else {
            if ((flags & 1) != 0) {
                currentAddressNum = currentAddressNumIpv6;
                addresses = addressesIpv6;
            } else {
                currentAddressNum = currentAddressNumIpv4;
                addresses = addressesIpv4;
            }
        }
        if (addresses.isEmpty()) {
            return null;
        }
        if (currentAddressNum >= addresses.size()) {
            currentAddressNum = 0;
            if ((flags & 2) != 0) {
                if ((flags & 1) != 0) {
                    currentAddressNumIpv6Download = currentAddressNum;
                } else {
                    currentAddressNumIpv4Download = currentAddressNum;
                }
            } else {
                if ((flags & 1) != 0) {
                    currentAddressNumIpv6 = currentAddressNum;
                } else {
                    currentAddressNumIpv4 = currentAddressNum;
                }
            }
        }
        return addresses.get(currentAddressNum);
    }

    public int getCurrentPort(int flags) {
        if (ports.isEmpty()) {
            return overridePort == -1 ? 443 : overridePort;
        }

        int[] portsArray = defaultPorts;

        if (overridePort == 8888) {
            portsArray = defaultPorts8888;
        }

        int currentPortNum;
        ArrayList<String> addresses;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                currentPortNum = currentPortNumIpv6Download;
            } else {
                currentPortNum = currentPortNumIpv4Download;
            }
        } else {
            if ((flags & 1) != 0) {
                currentPortNum = currentPortNumIpv6;
            } else {
                currentPortNum = currentPortNumIpv4;
            }
        }

        if (currentPortNum >= defaultPorts.length) {
            currentPortNum = 0;
            if ((flags & 2) != 0) {
                if ((flags & 1) != 0) {
                    currentPortNumIpv6Download = currentPortNum;
                } else {
                    currentPortNumIpv4Download = currentPortNum;
                }
            } else {
                if ((flags & 1) != 0) {
                    currentPortNumIpv6 = currentPortNum;
                } else {
                    currentPortNumIpv4 = currentPortNum;
                }
            }
        }
        int port = portsArray[currentPortNum];
        if (port == -1) {
            if (overridePort != -1) {
                return overridePort;
            }
            String address = getCurrentAddress(flags);
            return ports.get(address);
        }
        return port;
    }

    public void addAddressAndPort(String address, int port, int flags) {
        ArrayList<String> addresses;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                addresses = addressesIpv6Download;
            } else {
                addresses = addressesIpv4Download;
            }
        } else {
            if ((flags & 1) != 0) {
                addresses = addressesIpv6;
            } else {
                addresses = addressesIpv4;
            }
        }
        if (addresses.contains(address)) {
            return;
        }
        addresses.add(address);
        ports.put(address, port);
    }

    public void nextAddressOrPort(int flags) {
        int currentPortNum;
        int currentAddressNum;
        ArrayList<String> addresses;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                currentPortNum = currentPortNumIpv6Download;
                currentAddressNum = currentAddressNumIpv6Download;
                addresses = addressesIpv6Download;
            } else {
                currentPortNum = currentPortNumIpv4Download;
                currentAddressNum = currentAddressNumIpv4Download;
                addresses = addressesIpv4Download;
            }
        } else {
            if ((flags & 1) != 0) {
                currentPortNum = currentPortNumIpv6;
                currentAddressNum = currentAddressNumIpv6;
                addresses = addressesIpv6;
            } else {
                currentPortNum = currentPortNumIpv4;
                currentAddressNum = currentAddressNumIpv4;
                addresses = addressesIpv4;
            }
        }
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
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                currentPortNumIpv6Download = currentPortNum;
                currentAddressNumIpv6Download = currentAddressNum;
            } else {
                currentPortNumIpv4Download = currentPortNum;
                currentAddressNumIpv4Download = currentAddressNum;
            }
        } else {
            if ((flags & 1) != 0) {
                currentPortNumIpv6 = currentPortNum;
                currentAddressNumIpv6 = currentAddressNum;
            } else {
                currentPortNumIpv4 = currentPortNum;
                currentAddressNumIpv4 = currentAddressNum;
            }
        }
    }

    public void storeCurrentAddressAndPortNum() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("dc" + datacenterId + "port", currentPortNumIpv4);
                editor.putInt("dc" + datacenterId + "address", currentAddressNumIpv4);
                editor.putInt("dc" + datacenterId + "port6", currentPortNumIpv6);
                editor.putInt("dc" + datacenterId + "address6", currentAddressNumIpv6);
                editor.putInt("dc" + datacenterId + "portD", currentPortNumIpv4Download);
                editor.putInt("dc" + datacenterId + "addressD", currentAddressNumIpv4Download);
                editor.putInt("dc" + datacenterId + "port6D", currentPortNumIpv6Download);
                editor.putInt("dc" + datacenterId + "address6D", currentAddressNumIpv6Download);
                editor.commit();
            }
        });
    }

    private void readCurrentAddressAndPortNum() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
        currentPortNumIpv4 = preferences.getInt("dc" + datacenterId + "port", 0);
        currentAddressNumIpv4 = preferences.getInt("dc" + datacenterId + "address", 0);
        currentPortNumIpv6 = preferences.getInt("dc" + datacenterId + "port6", 0);
        currentAddressNumIpv6 = preferences.getInt("dc" + datacenterId + "address6", 0);
        currentPortNumIpv4Download = preferences.getInt("dc" + datacenterId + "portD", 0);
        currentAddressNumIpv4Download = preferences.getInt("dc" + datacenterId + "addressD", 0);
        currentPortNumIpv6Download = preferences.getInt("dc" + datacenterId + "port6D", 0);
        currentAddressNumIpv6Download = preferences.getInt("dc" + datacenterId + "address6D", 0);
    }

    public void replaceAddressesAndPorts(ArrayList<String> newAddresses, HashMap<String, Integer> newPorts, int flags) {
        ArrayList<String> addresses;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                addresses = addressesIpv6Download;
            } else {
                addresses = addressesIpv4Download;
            }
        } else {
            if ((flags & 1) != 0) {
                addresses = addressesIpv6;
            } else {
                addresses = addressesIpv4;
            }
        }
        for (String address : addresses) {
            ports.remove(address);
        }
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                addressesIpv6Download = newAddresses;
            } else {
                addressesIpv4Download = newAddresses;
            }
        } else {
            if ((flags & 1) != 0) {
                addressesIpv6 = newAddresses;
            } else {
                addressesIpv4 = newAddresses;
            }
        }
        ports.putAll(newPorts);
    }

    public void SerializeToStream(SerializedData stream) {
        stream.writeInt32(DATA_VERSION);
        stream.writeInt32(datacenterId);
        stream.writeInt32(lastInitVersion);
        stream.writeInt32(addressesIpv4.size());
        for (String address : addressesIpv4) {
            stream.writeString(address);
            stream.writeInt32(ports.get(address));
        }
        stream.writeInt32(addressesIpv6.size());
        for (String address : addressesIpv6) {
            stream.writeString(address);
            stream.writeInt32(ports.get(address));
        }
        stream.writeInt32(addressesIpv4Download.size());
        for (String address : addressesIpv4Download) {
            stream.writeString(address);
            stream.writeInt32(ports.get(address));
        }
        stream.writeInt32(addressesIpv6Download.size());
        for (String address : addressesIpv6Download) {
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
        ArrayList<Long> existingSalts = new ArrayList<>(authServerSaltSet.size());

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

    public void suspendConnections() {
        if (connection != null) {
            connection.suspendConnection(true);
        }
        if (uploadConnection != null) {
            uploadConnection.suspendConnection(true);
        }
        if (downloadConnection != null) {
            downloadConnection.suspendConnection(true);
        }
    }

    public void getSessions(ArrayList<Long> sessions) {
        if (connection != null) {
            sessions.add(connection.getSissionId());
        }
        if (uploadConnection != null) {
            sessions.add(uploadConnection.getSissionId());
        }
        if (downloadConnection != null) {
            sessions.add(downloadConnection.getSissionId());
        }
    }

    public void recreateSessions() {
        if (connection != null) {
            connection.recreateSession();
        }
        if (uploadConnection != null) {
            uploadConnection.recreateSession();
        }
        if (downloadConnection != null) {
            downloadConnection.recreateSession();
        }
    }

    public TcpConnection getDownloadConnection(TcpConnection.TcpConnectionDelegate delegate) {
        if (authKey != null) {
            if (downloadConnection == null) {
                downloadConnection = new TcpConnection(datacenterId);
                downloadConnection.delegate = delegate;
                downloadConnection.transportRequestClass = RPCRequest.RPCRequestClassDownloadMedia;
            }
            downloadConnection.connect();
        }
        return downloadConnection;
    }

    public TcpConnection getUploadConnection(TcpConnection.TcpConnectionDelegate delegate) {
        if (authKey != null) {
            if (uploadConnection == null) {
                uploadConnection = new TcpConnection(datacenterId);
                uploadConnection.delegate = delegate;
                uploadConnection.transportRequestClass = RPCRequest.RPCRequestClassUploadMedia;
            }
            uploadConnection.connect();
        }
        return uploadConnection;
    }

    public TcpConnection getGenericConnection(TcpConnection.TcpConnectionDelegate delegate) {
        if (authKey != null) {
            if (connection == null) {
                connection = new TcpConnection(datacenterId);
                connection.delegate = delegate;
                connection.transportRequestClass = RPCRequest.RPCRequestClassGeneric;
            }
            connection.connect();
        }
        return connection;
    }
}
