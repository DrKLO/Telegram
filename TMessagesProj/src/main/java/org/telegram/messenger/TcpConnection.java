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
        public abstract void tcpConnectionReceivedData(TcpConnection connection, byte[] data);
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
    private ByteBuffer restOfTheData;
    private long lastMessageId = 0;
    private boolean hasSomeDataSinceLastConnect = false;
    private int willRetryConnectCount = 5;
    private boolean isNextPort = false;

    public int transportRequestClass;

    private boolean firstPacket;

    private Timer reconnectTimer;

    public TcpConnection(int did) {
        if (selector == null) {
            selector = new PyroSelector();
            selector.spawnNetworkThread("network thread");
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
                    try {
                        if (reconnectTimer != null) {
                            reconnectTimer.cancel();
                            reconnectTimer = null;
                        }
                    } catch (Exception e2) {
                        FileLog.e("tmessages", e2);
                    }
                    Datacenter datacenter = ConnectionsManager.Instance.datacenterWithId(datacenterId);
                    hostAddress = datacenter.getCurrentAddress();
                    hostPort = datacenter.getCurrentPort();
                    FileLog.d("tmessages", String.format(TcpConnection.this + " Connecting (%s:%d)", hostAddress, hostPort));
                    firstPacket = true;
                    restOfTheData = null;
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
                        client.setTimeout(35000);
                    }
                    selector.wakeup();
                } catch (Exception e) {
                    try {
                        if (reconnectTimer != null) {
                            reconnectTimer.cancel();
                            reconnectTimer = null;
                        }
                    } catch (Exception e2) {
                        FileLog.e("tmessages", e2);
                    }
                    connectionState =  TcpConnectionState.TcpConnectionStageReconnecting;
                    if (delegate != null) {
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                delegate.tcpConnectionClosed(TcpConnection.this);
                            }
                        });
                    }

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
                        if (failedConnectionCount > willRetryConnectCount) {
                            Datacenter datacenter = ConnectionsManager.Instance.datacenterWithId(datacenterId);
                            datacenter.nextAddressOrPort();
                            failedConnectionCount = 0;
                        }
                    }

                    FileLog.e("tmessages", e);
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
                                            if (reconnectTimer != null) {
                                                reconnectTimer.cancel();
                                                reconnectTimer = null;
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
            }
        });
    }

    private void suspendConnectionInternal() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
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
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.tcpConnectionClosed(TcpConnection.this);
                }
            });
        }
        firstPacket = true;
        restOfTheData = null;
        channelToken = 0;
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
        if (restOfTheData != null) {
            ByteBuffer newBuffer = ByteBuffer.allocate(restOfTheData.limit() + buffer.limit());
            newBuffer.put(restOfTheData);
            newBuffer.put(buffer);
            buffer = newBuffer;
            restOfTheData = null;
        }

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();

        while (buffer.hasRemaining()) {
            if (!hasSomeDataSinceLastConnect) {
                Datacenter datacenter = ConnectionsManager.Instance.datacenterWithId(datacenterId);
                datacenter.storeCurrentAddressAndPortNum();
                isNextPort = false;
                client.setTimeout(35000);
            }
            hasSomeDataSinceLastConnect = true;

            int currentPacketLength;
            buffer.mark();
            byte fByte = buffer.get();

            if ((fByte & (1 << 7)) != 0) {
                buffer.reset();
                if (buffer.remaining() < 4) {
                    restOfTheData = ByteBuffer.allocate(buffer.remaining());
                    restOfTheData.put(buffer);
                    restOfTheData.rewind();
                    break;
                }
                buffer.order(ByteOrder.BIG_ENDIAN);
                final int ackId = buffer.getInt() & (~(1 << 31));
                if (delegate != null) {
                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            delegate.tcpConnectionQuiackAckReceived(TcpConnection.this, ackId);
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
                    restOfTheData = ByteBuffer.allocate(buffer.remaining());
                    restOfTheData.put(buffer);
                    restOfTheData.rewind();
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
                        lastMessageId = ConnectionsManager.Instance.needsToDecodeMessageIdFromPartialData(TcpConnection.this, temp);
                    }
                    if (lastMessageId != -1 && lastMessageId != 0) {
                        if (delegate != null) {
                            final int arg2 = buffer.remaining();
                            final int arg3 = currentPacketLength;
                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    delegate.tcpConnectionProgressChanged(TcpConnection.this, lastMessageId, arg2, arg3);
                                }
                            });
                        }
                    }
                }
                buffer.reset();
                restOfTheData = ByteBuffer.allocate(buffer.remaining());
                restOfTheData.order(ByteOrder.LITTLE_ENDIAN);
                restOfTheData.put(buffer);
                restOfTheData.rewind();
                return;
            }

            final byte[] packetData = new byte[currentPacketLength];
            buffer.get(packetData);

            if (delegate != null) {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.tcpConnectionReceivedData(TcpConnection.this, packetData);
                    }
                });
            }
        }
    }

    public void handleDisconnect(PyroClient client, Exception e) {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
        if (e != null) {
            FileLog.d("tmessages", "Disconnected " + TcpConnection.this + " with error " + e);
        } else {
            FileLog.d("tmessages", "Disconnected " + TcpConnection.this);
        }
        firstPacket = true;
        restOfTheData = null;
        channelToken = 0;
        if (connectionState != TcpConnectionState.TcpConnectionStageSuspended && connectionState != TcpConnectionState.TcpConnectionStageIdle) {
            connectionState = TcpConnectionState.TcpConnectionStageIdle;
        }
        if (delegate != null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (delegate != null) {
                        delegate.tcpConnectionClosed(TcpConnection.this);
                    }
                }
            });
        }
        if (connectionState == TcpConnectionState.TcpConnectionStageIdle &&
                ((transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0 && (datacenterId == ConnectionsManager.Instance.currentDatacenterId || datacenterId == ConnectionsManager.Instance.movingToDatacenterId))) {
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
                if (failedConnectionCount > willRetryConnectCount) {
                    Datacenter datacenter = ConnectionsManager.Instance.datacenterWithId(datacenterId);
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
                                    if (reconnectTimer != null) {
                                        reconnectTimer.cancel();
                                        reconnectTimer = null;
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
        FileLog.d("tmessages", String.format(TcpConnection.this + " Connected (%s:%d)", hostAddress, hostPort));
        if (delegate != null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.tcpConnectionConnected(TcpConnection.this);
                }
            });
        }
    }

    @Override
    public void unconnectableClient(PyroClient client, Exception cause) {
        handleDisconnect(client, cause);
    }

    @Override
    public void droppedClient(PyroClient client, IOException cause) {
        super.droppedClient(client, cause);
        handleDisconnect(client, cause);
    }

    @Override
    public void disconnectedClient(PyroClient client) {
        handleDisconnect(client, null);
    }

    @Override
    public void receivedData(PyroClient client, ByteBuffer data) {
        try {
            failedConnectionCount = 0;
            readData(data);
        } catch (Exception e) {
            FileLog.d("tmessages", "read data error");
            reconnect();
        }
    }

    @Override
    public void sentData(PyroClient client, int bytes) {
        failedConnectionCount = 0;
        FileLog.d("tmessages", TcpConnection.this + " bytes sent " + bytes);
    }
}
