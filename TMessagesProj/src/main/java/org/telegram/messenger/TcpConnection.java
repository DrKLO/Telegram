/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;

import jawnae.pyronet.PyroClient;
import jawnae.pyronet.PyroSelector;
import jawnae.pyronet.PyroClientAdapter;

public class TcpConnection extends PyroClientAdapter {
    public enum TcpConnectionState {
        TcpConnectionStageIdle,
        TcpConnectionStageConnecting,
        TcpConnectionStageReconnecting,
        TcpConnectionStageConnected,
        TcpConnectionStageSuspended
    }

    public abstract static interface TcpConnectionDelegate {
        public abstract void tcpConnectionClosed(TcpConnection connection);
        public abstract void tcpConnectionConnected(TcpConnection connection);
        public abstract void tcpConnectionQuiackAckReceived(TcpConnection connection, int ack);
        public abstract void tcpConnectionReceivedData(TcpConnection connection, ByteBufferDesc data, int length);
        public abstract void tcpConnectionProgressChanged(TcpConnection connection, long messageId, int currentSize, int length);
    }

    private static PyroSelector selector;
    private PyroClient client;
    public TcpConnectionState connectionState;
    public volatile int channelToken = 0;
    private String hostAddress;
    private int hostPort;
    private int datacenterId;
    private int failedConnectionCount;
    public TcpConnectionDelegate delegate;
    private ByteBufferDesc restOfTheData;
    private long lastMessageId = 0;
    private boolean hasSomeDataSinceLastConnect = false;
    private int willRetryConnectCount = 5;
    private boolean isNextPort = false;
    private final Integer timerSync = 1;
    private boolean wasConnected;
    private int lastPacketLength;

    public int transportRequestClass;

    private boolean firstPacket;

    private Timer reconnectTimer;

    public TcpConnection(int did) {
        if (selector == null) {
            selector = new PyroSelector();
            selector.spawnNetworkThread("network thread");
            BuffersStorage storage = BuffersStorage.getInstance();
        }
        datacenterId = did;
        connectionState = TcpConnectionState.TcpConnectionStageIdle;
    }

    static volatile Integer nextChannelToken = 1;
    static int generateChannelToken() {
        return nextChannelToken++;
    }

    public int getDatacenterId() {
        return datacenterId;
    }

    public void connect() {
        selector.scheduleTask(new Runnable() {
            @Override
            public void run() {
                if ((connectionState == TcpConnectionState.TcpConnectionStageConnected || connectionState == TcpConnectionState.TcpConnectionStageConnecting) && client != null) {
                    return;
                }

                connectionState = TcpConnectionState.TcpConnectionStageConnecting;
                try {
                    if (!ConnectionsManager.isNetworkOnline()) {
                        handleConnectionError(null);
                        return;
                    }
                    try {
                        synchronized (timerSync) {
                            if (reconnectTimer != null) {
                                reconnectTimer.cancel();
                                reconnectTimer = null;
                            }
                        }
                    } catch (Exception e2) {
                        FileLog.e("tmessages", e2);
                    }
                    Datacenter datacenter = ConnectionsManager.getInstance().datacenterWithId(datacenterId);
                    hostAddress = datacenter.getCurrentAddress();
                    hostPort = datacenter.getCurrentPort();
                    FileLog.d("tmessages", String.format(TcpConnection.this + " Connecting (%s:%d)", hostAddress, hostPort));
                    firstPacket = true;
                    if (restOfTheData != null) {
                        BuffersStorage.getInstance().reuseFreeBuffer(restOfTheData);
                        restOfTheData = null;
                    }
                    lastPacketLength = 0;
                    wasConnected = false;
                    hasSomeDataSinceLastConnect = false;
                    if (client != null) {
                        client.removeListener(TcpConnection.this);
                        client.dropConnection();
                        client = null;
                    }
                    client = selector.connect(new InetSocketAddress(hostAddress, hostPort));
                    client.addListener(TcpConnection.this);
                    if (isNextPort) {
                        client.setTimeout(8000);
                    } else {
                        client.setTimeout(15000);
                    }
                    selector.wakeup();
                } catch (Exception e) {
                    handleConnectionError(e);
                }
            }
        });
    }

    private void handleConnectionError(Exception e) {
        try {
            synchronized (timerSync) {
                if (reconnectTimer != null) {
                    reconnectTimer.cancel();
                    reconnectTimer = null;
                }
            }
        } catch (Exception e2) {
            FileLog.e("tmessages", e2);
        }
        connectionState =  TcpConnectionState.TcpConnectionStageReconnecting;
        if (delegate != null) {
            final TcpConnectionDelegate finalDelegate = delegate;
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    finalDelegate.tcpConnectionClosed(TcpConnection.this);
                }
            });
        }

        failedConnectionCount++;
        if (failedConnectionCount == 1) {
            if (hasSomeDataSinceLastConnect) {
                willRetryConnectCount = 3;
            } else {
                willRetryConnectCount = 1;
            }
        }
        if (ConnectionsManager.isNetworkOnline()) {
            isNextPort = true;
            if (failedConnectionCount > willRetryConnectCount) {
                Datacenter datacenter = ConnectionsManager.getInstance().datacenterWithId(datacenterId);
                datacenter.nextAddressOrPort();
                failedConnectionCount = 0;
            }
        }

        if (e != null) {
            FileLog.e("tmessages", e);
        }
        FileLog.d("tmessages", "Reconnect " + hostAddress + ":" + hostPort + " " + TcpConnection.this);
        try {
            reconnectTimer = new Timer();
            reconnectTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    selector.scheduleTask(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                synchronized (timerSync) {
                                    if (reconnectTimer != null) {
                                        reconnectTimer.cancel();
                                        reconnectTimer = null;
                                    }
                                }
                            } catch (Exception e2) {
                                FileLog.e("tmessages", e2);
                            }
                            connect();
                        }
                    });
                }
            }, failedConnectionCount >= 3 ? 500 : 300, failedConnectionCount >= 3 ? 500 : 300);
        } catch (Exception e3) {
            FileLog.e("tmessages", e3);
        }
    }

    private void suspendConnectionInternal() {
        synchronized (timerSync) {
            if (reconnectTimer != null) {
                reconnectTimer.cancel();
                reconnectTimer = null;
            }
        }
        if (connectionState == TcpConnectionState.TcpConnectionStageIdle || connectionState == TcpConnectionState.TcpConnectionStageSuspended) {
            return;
        }
        FileLog.d("tmessages", "suspend connnection " + TcpConnection.this);
        connectionState = TcpConnectionState.TcpConnectionStageSuspended;
        if (client != null) {
            client.removeListener(TcpConnection.this);
            client.dropConnection();
            client = null;
        }
        if (delegate != null) {
            final TcpConnectionDelegate finalDelegate = delegate;
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    finalDelegate.tcpConnectionClosed(TcpConnection.this);
                }
            });
        }
        firstPacket = true;
        if (restOfTheData != null) {
            BuffersStorage.getInstance().reuseFreeBuffer(restOfTheData);
            restOfTheData = null;
        }
        lastPacketLength = 0;
        channelToken = 0;
        wasConnected = false;
    }

    public void suspendConnection(boolean task) {
        if (task) {
            selector.scheduleTask(new Runnable() {
                @Override
                public void run() {
                    suspendConnectionInternal();
                }
            });
        } else {
            suspendConnectionInternal();
        }
    }

    public void resumeConnection() {

    }

    private void reconnect() {
        suspendConnection(false);
        connectionState = TcpConnectionState.TcpConnectionStageReconnecting;
        connect();
    }

    public void sendData(final byte[] data, final boolean reportAck, final boolean startResponseTimeout) {
        selector.scheduleTask(new Runnable() {
            @Override
            public void run() {
                if (connectionState == TcpConnectionState.TcpConnectionStageIdle ||
                        connectionState == TcpConnectionState.TcpConnectionStageReconnecting ||
                        connectionState == TcpConnectionState.TcpConnectionStageSuspended || client == null) {
                    connect();
                }

                if (client == null || client.isDisconnected()) {
                    return;
                }

                int packetLength = data.length / 4;

                SerializedData buffer = new SerializedData();
                if (packetLength < 0x7f) {
                    if (reportAck) {
                        packetLength |= (1 << 7);
                    }
                    buffer.writeByte(packetLength);
                } else {
                    packetLength = (packetLength << 8) + 0x7f;
                    if (reportAck) {
                        packetLength |= (1 << 7);
                    }
                    buffer.writeInt32(packetLength);
                }
                buffer.writeRaw(data);

                final byte[] packet = buffer.toByteArray();

                ByteBuffer sendBuffer = ByteBuffer.allocate((firstPacket ? 1 : 0) + packet.length);
                sendBuffer.rewind();
                sendBuffer.order(ByteOrder.LITTLE_ENDIAN);
                if (firstPacket) {
                    sendBuffer.put((byte)0xef);
                    firstPacket = false;
                }
                sendBuffer.put(packet);
                sendBuffer.rewind();
                client.write(sendBuffer);
            }
        });
    }

    private void readData(ByteBuffer buffer) throws Exception {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();

        ByteBuffer parseLaterBuffer = null;
        if (restOfTheData != null) {
            if (lastPacketLength == 0) {
                //FileLog.e("tmessages", this +  " write addition data to restOfTheData");
                if (restOfTheData.capacity() - restOfTheData.position() >= buffer.limit()) {
                    restOfTheData.limit(restOfTheData.position() + buffer.limit());
                    restOfTheData.put(buffer);
                    buffer = restOfTheData.buffer;
                    //FileLog.e("tmessages", this +  " no need to recreate buffer");
                } else {
                    ByteBufferDesc newBuffer = BuffersStorage.getInstance().getFreeBuffer(restOfTheData.limit() + buffer.limit());
                    restOfTheData.rewind();
                    newBuffer.put(restOfTheData.buffer);
                    newBuffer.put(buffer);
                    buffer = newBuffer.buffer;
                    BuffersStorage.getInstance().reuseFreeBuffer(restOfTheData);
                    restOfTheData = newBuffer;
                    //FileLog.e("tmessages", this +  " NEED to recreate buffer");
                }
            } else {
                //FileLog.e("tmessages", this +  " write buffer to restOfTheData buffer of len = " + lastPacketLength);
                int len = 0;
                if (lastPacketLength - restOfTheData.position() <= buffer.limit()) {
                    len = lastPacketLength - restOfTheData.position();
                    //FileLog.e("tmessages", this +  " received buffer - OK!");
                } else {
                    len = buffer.limit();
                    //FileLog.e("tmessages", this +  " received buffer less than need");
                }
                int oldLimit = buffer.limit();
                buffer.limit(len);
                restOfTheData.put(buffer);
                buffer.limit(oldLimit);
                if (restOfTheData.position() != lastPacketLength) {
                    //FileLog.e("tmessages", this +  " don't get much data to restOfTheData");
                    if (lastMessageId != -1 && lastMessageId != 0) {
                        if (delegate != null) {
                            final TcpConnectionDelegate finalDelegate = delegate;
                            final int arg2 = restOfTheData.position();
                            final int arg3 = lastPacketLength;
                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    finalDelegate.tcpConnectionProgressChanged(TcpConnection.this, lastMessageId, arg2, arg3);
                                }
                            });
                        }
                    }
                    return;
                } else {
                    //FileLog.e("tmessages", this +  " get much data to restOfTheData - OK!");
                    if (buffer.hasRemaining()) {
                        parseLaterBuffer = buffer;
                        //FileLog.e("tmessages", this +  " something remain in the received buffer");
                    } else {
                        parseLaterBuffer = null;
                    }
                    buffer = restOfTheData.buffer;
                }
            }
        }

        buffer.rewind();

        while (buffer.hasRemaining()) {
            if (!hasSomeDataSinceLastConnect) {
                Datacenter datacenter = ConnectionsManager.getInstance().datacenterWithId(datacenterId);
                datacenter.storeCurrentAddressAndPortNum();
                isNextPort = false;
                client.setTimeout(25000);
            }
            hasSomeDataSinceLastConnect = true;

            int currentPacketLength;
            buffer.mark();
            byte fByte = buffer.get();

            if ((fByte & (1 << 7)) != 0) {
                buffer.reset();
                if (buffer.remaining() < 4) {
                    ByteBufferDesc reuseLater = restOfTheData;
                    restOfTheData = BuffersStorage.getInstance().getFreeBuffer(16384);
                    restOfTheData.put(buffer);
                    restOfTheData.limit(restOfTheData.position());
                    lastPacketLength = 0;
                    //FileLog.e("tmessages", this +  " 1 - size less than 4 bytes - write to free buffer");
                    if (reuseLater != null) {
                        BuffersStorage.getInstance().reuseFreeBuffer(reuseLater);
                        //FileLog.e("tmessages", this +  " 1 - reuse later buffer1");
                    }
                    break;
                }
                buffer.order(ByteOrder.BIG_ENDIAN);
                final int ackId = buffer.getInt() & (~(1 << 31));
                if (delegate != null) {
                    final TcpConnectionDelegate finalDelegate = delegate;
                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            finalDelegate.tcpConnectionQuiackAckReceived(TcpConnection.this, ackId);
                        }
                    });
                }
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                continue;
            }

            if (fByte != 0x7f) {
                currentPacketLength = ((int)fByte) * 4;
            } else {
                buffer.reset();
                if (buffer.remaining() < 4) {
                    //FileLog.e("tmessages", this +  " 2 - size less than 4 bytes - write to free buffer");
                    if (restOfTheData == null || restOfTheData != null && restOfTheData.position() != 0) {
                        ByteBufferDesc reuseLater = restOfTheData;
                        restOfTheData = BuffersStorage.getInstance().getFreeBuffer(16384);
                        restOfTheData.put(buffer);
                        restOfTheData.limit(restOfTheData.position());
                        lastPacketLength = 0;
                        if (reuseLater != null) {
                            BuffersStorage.getInstance().reuseFreeBuffer(reuseLater);
                            //FileLog.e("tmessages", this +  " 2 - reuse later buffer1");
                        }
                    } else {
                        restOfTheData.position(restOfTheData.limit());
                    }
                    break;
                }
                currentPacketLength = (buffer.getInt() >> 8) * 4;
            }

            if (currentPacketLength % 4 != 0 || currentPacketLength > 2 * 1024 * 1024) {
                FileLog.e("tmessages", "Invalid packet length");
                reconnect();
                return;
            }

            if (currentPacketLength < buffer.remaining()) {
                FileLog.d("tmessages", TcpConnection.this + " Received message len " + currentPacketLength + " but packet larger " + buffer.remaining());
                lastMessageId = 0;
            } else if (currentPacketLength == buffer.remaining()) {
                FileLog.d("tmessages", TcpConnection.this + " Received message len " + currentPacketLength + " equal to packet size");
                lastMessageId = 0;
            } else {
                FileLog.d("tmessages", TcpConnection.this + " Received packet size less(" + buffer.remaining() + ") then message size(" + currentPacketLength + ")");
                if (buffer.remaining() >= 152 && (transportRequestClass & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    if (lastMessageId == 0) {
                        byte[] temp = new byte[152];
                        buffer.get(temp);
                        lastMessageId = ConnectionsManager.getInstance().needsToDecodeMessageIdFromPartialData(TcpConnection.this, temp);
                    }
                    if (lastMessageId != -1 && lastMessageId != 0) {
                        if (delegate != null) {
                            final TcpConnectionDelegate finalDelegate = delegate;
                            final int arg2 = buffer.remaining();
                            final int arg3 = currentPacketLength;
                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    finalDelegate.tcpConnectionProgressChanged(TcpConnection.this, lastMessageId, arg2, arg3);
                                }
                            });
                        }
                    }
                }

                ByteBufferDesc reuseLater = null;
                int len = currentPacketLength + (fByte != 0x7f ? 1 : 4);
                if (restOfTheData != null && restOfTheData.capacity() < len) {
                    reuseLater = restOfTheData;
                    restOfTheData = null;
                    //FileLog.e("tmessages", this +  " not enough space for len, recreate buffer = " + len);
                }
                if (restOfTheData == null) {
                    //FileLog.e("tmessages", this +  " write to restOfTheData, get buffer len = " + len);
                    buffer.reset();
                    restOfTheData = BuffersStorage.getInstance().getFreeBuffer(len);
                    restOfTheData.put(buffer);
                } else {
                    restOfTheData.position(restOfTheData.limit());
                    restOfTheData.limit(len);
                }
                lastPacketLength = len;
                if (reuseLater != null) {
                    BuffersStorage.getInstance().reuseFreeBuffer(reuseLater);
                    //FileLog.e("tmessages", this +  " 3 - reuse later buffer1");
                }
                return;
            }

            final int length = currentPacketLength;
            final ByteBufferDesc toProceed = BuffersStorage.getInstance().getFreeBuffer(currentPacketLength);
            int old = buffer.limit();
            buffer.limit(buffer.position() + currentPacketLength);
            toProceed.put(buffer);
            buffer.limit(old);
            toProceed.rewind();

            if (delegate != null) {
                final TcpConnectionDelegate finalDelegate = delegate;
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        finalDelegate.tcpConnectionReceivedData(TcpConnection.this, toProceed, length);
                        BuffersStorage.getInstance().reuseFreeBuffer(toProceed);
                    }
                });
            }

            if (restOfTheData != null) {
                if (lastPacketLength != 0 && restOfTheData.position() == lastPacketLength || lastPacketLength == 0 && !restOfTheData.hasRemaining()) {
                    BuffersStorage.getInstance().reuseFreeBuffer(restOfTheData);
                    restOfTheData = null;
                    //FileLog.e("tmessages", this +  " restOfTheData parsed null it");
                } else {
                    restOfTheData.compact();
                    restOfTheData.limit(restOfTheData.position());
                    restOfTheData.position(0);
                    //FileLog.e("tmessages", this +  " restOfTheData NOT parsed, compact");
                }
            }

            if (parseLaterBuffer != null) {
                //FileLog.e("tmessages", this +  " there is parseLaterBuffer");
                buffer = parseLaterBuffer;
                parseLaterBuffer = null;
            }
        }
    }

    public void handleDisconnect(PyroClient client, Exception e, boolean timedout) {
        synchronized (timerSync) {
            if (reconnectTimer != null) {
                reconnectTimer.cancel();
                reconnectTimer = null;
            }
        }
        if (e != null) {
            FileLog.d("tmessages", "Disconnected " + TcpConnection.this + " with error " + e);
        } else {
            FileLog.d("tmessages", "Disconnected " + TcpConnection.this);
        }
        boolean switchToNextPort = wasConnected && !hasSomeDataSinceLastConnect && timedout;
        firstPacket = true;
        if (restOfTheData != null) {
            BuffersStorage.getInstance().reuseFreeBuffer(restOfTheData);
            restOfTheData = null;
        }
        channelToken = 0;
        lastPacketLength = 0;
        wasConnected = false;
        if (connectionState != TcpConnectionState.TcpConnectionStageSuspended && connectionState != TcpConnectionState.TcpConnectionStageIdle) {
            connectionState = TcpConnectionState.TcpConnectionStageIdle;
        }
        if (delegate != null) {
            final TcpConnectionDelegate finalDelegate = delegate;
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    finalDelegate.tcpConnectionClosed(TcpConnection.this);
                }
            });
        }
        if (connectionState == TcpConnectionState.TcpConnectionStageIdle &&
                ((transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0 && (datacenterId == ConnectionsManager.getInstance().currentDatacenterId || datacenterId == ConnectionsManager.getInstance().movingToDatacenterId))) {
            failedConnectionCount++;
            if (failedConnectionCount == 1) {
                if (hasSomeDataSinceLastConnect) {
                    willRetryConnectCount = 5;
                } else {
                    willRetryConnectCount = 1;
                }
            }
            if (ConnectionsManager.isNetworkOnline()) {
                isNextPort = true;
                if (failedConnectionCount > willRetryConnectCount || switchToNextPort) {
                    Datacenter datacenter = ConnectionsManager.getInstance().datacenterWithId(datacenterId);
                    datacenter.nextAddressOrPort();
                    failedConnectionCount = 0;
                }
            }
            FileLog.d("tmessages", "Reconnect " + hostAddress + ":" + hostPort + " " + TcpConnection.this);
            try {
                reconnectTimer = new Timer();
                reconnectTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        selector.scheduleTask(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    synchronized (timerSync) {
                                        if (reconnectTimer != null) {
                                            reconnectTimer.cancel();
                                            reconnectTimer = null;
                                        }
                                    }
                                } catch (Exception e2) {
                                    FileLog.e("tmessages", e2);
                                }
                                connect();
                            }
                        });
                    }
                }, failedConnectionCount > 3 ? 500 : 300, failedConnectionCount > 3 ? 500 : 300);
            } catch (Exception e3) {
                FileLog.e("tmessages", e3);
            }
        }
    }

    @Override
    public void connectedClient(PyroClient client) {
        connectionState = TcpConnectionState.TcpConnectionStageConnected;
        channelToken = generateChannelToken();
        wasConnected = true;
        FileLog.d("tmessages", String.format(TcpConnection.this + " Connected (%s:%d)", hostAddress, hostPort));
        if (delegate != null) {
            final TcpConnectionDelegate finalDelegate = delegate;
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    finalDelegate.tcpConnectionConnected(TcpConnection.this);
                }
            });
        }
    }

    @Override
    public void unconnectableClient(PyroClient client, Exception cause) {
        handleDisconnect(client, cause, false);
    }

    @Override
    public void droppedClient(PyroClient client, IOException cause) {
        super.droppedClient(client, cause);
        handleDisconnect(client, cause, (cause instanceof SocketTimeoutException));
    }

    @Override
    public void disconnectedClient(PyroClient client) {
        handleDisconnect(client, null, false);
    }

    @Override
    public void receivedData(PyroClient client, ByteBuffer data) {
        try {
            failedConnectionCount = 0;
            readData(data);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            reconnect();
        }
    }

    @Override
    public void sentData(PyroClient client, int bytes) {
    }
}
