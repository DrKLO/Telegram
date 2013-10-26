/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import jawnae.pyronet.PyroClient;
import jawnae.pyronet.PyroSelector;
import jawnae.pyronet.events.PyroClientAdapter;

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
    }

    private static PyroSelector selector;
    private PyroClient client;
    public TcpConnectionState connectionState;
    private Queue<byte[]> packetsQueue;
    public volatile int channelToken = 0;
    private String hostAddress;
    private int hostPort;
    public int datacenterId;
    private int failedConnectionCount;
    public TcpConnectionDelegate delegate;
    private ByteBuffer restOfTheData;

    public int transportRequestClass;

    private boolean firstPacket;

    private Timer reconnectTimer;

    public TcpConnection(String ip, int port) {
        if (selector == null) {
            selector = new PyroSelector();
            selector.spawnNetworkThread("network thread");
        }
        packetsQueue = new LinkedList<byte[]>();
        hostAddress = ip;
        hostPort = port;
        connectionState = TcpConnectionState.TcpConnectionStageIdle;
    }

    static volatile Integer nextChannelToken = 1;
    static int generateChannelToken() {
        return nextChannelToken++;
    }

    public void connect() {
        selector.scheduleTask(new Runnable() {
            @Override
            public void run() {
                if (connectionState == TcpConnectionState.TcpConnectionStageConnected || connectionState == TcpConnectionState.TcpConnectionStageConnecting) {
                    return;
                }

                connectionState = TcpConnectionState.TcpConnectionStageConnecting;
                try {
                    if (ConnectionsManager.DEBUG_VERSION) {
                        Log.d("tmessages", String.format(this + " Connecting (%s:%d)", hostAddress, hostPort));
                    }
                    firstPacket = true;
                    restOfTheData = null;
                    if (client != null) {
                        client.removeListener(TcpConnection.this);
                        client.dropConnection();
                    }
                    client = selector.connect(new InetSocketAddress(hostAddress, hostPort));
                    client.addListener(TcpConnection.this);
                    client.setTimeout(35000);
                    selector.wakeup();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void suspendConnection(boolean task) {
        if (task) {
            selector.scheduleTask(new Runnable() {
                @Override
                public void run() {
                    if (reconnectTimer != null) {
                        reconnectTimer.cancel();
                        reconnectTimer = null;
                    }
                    if (connectionState == TcpConnectionState.TcpConnectionStageIdle || connectionState == TcpConnectionState.TcpConnectionStageSuspended) {
                        return;
                    }
                    if (ConnectionsManager.DEBUG_VERSION) {
                        Log.d("tmessages", "suspend connnection " + this);
                    }
                    connectionState = TcpConnectionState.TcpConnectionStageSuspended;
                    if (client != null) {
                        client.dropConnection();
                        client = null;
                    }
                }
            });
        } else {
            if (reconnectTimer != null) {
                reconnectTimer.cancel();
                reconnectTimer = null;
            }
            if (connectionState == TcpConnectionState.TcpConnectionStageIdle || connectionState == TcpConnectionState.TcpConnectionStageSuspended) {
                return;
            }
            if (ConnectionsManager.DEBUG_VERSION) {
                Log.d("tmessages", "suspend connnection " + this);
            }
            connectionState = TcpConnectionState.TcpConnectionStageSuspended;
            if (client != null) {
                client.dropConnection();
                client = null;
            }
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
                        connectionState == TcpConnectionState.TcpConnectionStageSuspended) {
                    connect();
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

                if (client != null && !client.isDisconnected()) {
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
                } else {
                    packetsQueue.add(packet);
                }
            }
        });
    }

    private void readData(ByteBuffer buffer) throws Exception {
        if (ConnectionsManager.DEBUG_VERSION) {
            Log.d("tmessages", "received data = " + buffer.limit());
        }

        if (restOfTheData != null) {
            if (ConnectionsManager.DEBUG_VERSION) {
                Log.d("tmessages", "there is rest of data " + restOfTheData.limit());
            }
            ByteBuffer newBuffer = ByteBuffer.allocate(restOfTheData.limit() + buffer.limit());
            newBuffer.put(restOfTheData);
            newBuffer.put(buffer);
            buffer = newBuffer;
            restOfTheData = null;
        }

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();

        while (buffer.hasRemaining()) {

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

            if (currentPacketLength < buffer.remaining()) {
                if (ConnectionsManager.DEBUG_VERSION) {
                    Log.d("tmessages", this + " Received message len " + currentPacketLength + " but packet larger " + buffer.remaining());
                }
            } else if (currentPacketLength == buffer.remaining()) {
                if (ConnectionsManager.DEBUG_VERSION) {
                    Log.d("tmessages", this + " Received message len " + currentPacketLength + " equal to packet size");
                }
            } else {
                if (ConnectionsManager.DEBUG_VERSION) {
                    Log.d("tmessages", this + " Received packet size less(" + buffer.remaining() + ") then message size(" + currentPacketLength + ")");
                }
                buffer.reset();
                restOfTheData = ByteBuffer.allocate(buffer.remaining());
                restOfTheData.order(ByteOrder.LITTLE_ENDIAN);
                restOfTheData.put(buffer);
                restOfTheData.rewind();
                return;
            }

            if (currentPacketLength % 4 != 0 || currentPacketLength > 2 * 1024 * 1024) {
                if (ConnectionsManager.DEBUG_VERSION) {
                    Log.e("tmessages", "Invalid packet length");
                }
                reconnect();
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

    public void handleDisconnect(PyroClient client, IOException e) {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
        if (ConnectionsManager.DEBUG_VERSION) {
            if (e != null) {
                Log.d("tmessages", "Disconnected " + this + " with error " + e);
            } else {
                Log.d("tmessages", "Disconnected " + this);
            }
        }
        firstPacket = true;
        restOfTheData = null;
        packetsQueue.clear();
        channelToken = 0;
        if (connectionState != TcpConnectionState.TcpConnectionStageSuspended && connectionState != TcpConnectionState.TcpConnectionStageIdle) {
            connectionState = TcpConnectionState.TcpConnectionStageIdle;
        }
        if (delegate != null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.tcpConnectionClosed(TcpConnection.this);
                }
            });
        }
        if (connectionState == TcpConnectionState.TcpConnectionStageIdle && (!packetsQueue.isEmpty() ||
                (transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0 && (datacenterId == ConnectionsManager.Instance.currentDatacenterId || datacenterId == ConnectionsManager.Instance.movingToDatacenterId))) {
            failedConnectionCount++;
            if (ConnectionsManager.DEBUG_VERSION) {
                Log.d("tmessages", "Reconnect " + hostAddress + ":" + hostPort + " " + this);
            }
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
                                    e2.printStackTrace();
                                }
                                connect();
                            }
                        });
                    }
                }, failedConnectionCount > 10 ? 500 : 200, failedConnectionCount > 10 ? 500 : 200);
            } catch (Exception e3) {
                e3.printStackTrace();
            }
        }
    }

    @Override
    public void connectedClient(PyroClient client) {
        //if (!ConnectionsManager.isNetworkOnline()) {
        //    return;
        //}
        connectionState = TcpConnectionState.TcpConnectionStageConnected;
        channelToken = generateChannelToken();
        if (ConnectionsManager.DEBUG_VERSION) {
            Log.d("tmessages", String.format(this + " Connected (%s:%d)", hostAddress, hostPort));
        }
        if (delegate != null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.tcpConnectionConnected(TcpConnection.this);
                }
            });
        }
        while (packetsQueue.size() != 0) {
            byte[] packet = packetsQueue.poll();
            ByteBuffer sendBuffer = ByteBuffer.allocate((firstPacket ? 1 : 0) + packet.length);
            sendBuffer.order(ByteOrder.LITTLE_ENDIAN);
            if (firstPacket) {
                sendBuffer.put((byte)0xef);
                firstPacket = false;
            }
            sendBuffer.put(packet);
            sendBuffer.rewind();
            client.write(sendBuffer);
        }
    }

    @Override
    public void unconnectableClient(PyroClient client) {
        handleDisconnect(client, null);
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
            if (ConnectionsManager.DEBUG_VERSION) {
                Log.d("tmessages", "read data error");
            }
            reconnect();
        }
    }

    @Override
    public void sentData(PyroClient client, int bytes) {
        failedConnectionCount = 0;
        if (ConnectionsManager.DEBUG_VERSION) {
            Log.d("tmessages", this + " bytes sent " + bytes);
        }
    }
}
