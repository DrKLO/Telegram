/*
 * Copyright (c) 2008, https://code.google.com/p/pyronet/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the <ORGANIZATION> nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jawnae.pyronet;

import org.telegram.messenger.ByteBufferDesc;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PyroClient {
    private final PyroSelector selector;

    private final SelectionKey key;

    private final ByteStream outbound;

    // called by PyroSelector.connect()
    PyroClient(PyroSelector selector, InetSocketAddress bind,
            InetSocketAddress host) throws IOException {
        this(selector, PyroClient.bindAndConfigure(selector,
                SocketChannel.open(), bind));

        ((SocketChannel) this.key.channel()).connect(host);
    }

    // called by PyroClient and PyroServer
    PyroClient(PyroSelector selector, SelectionKey key) {
        this.selector = selector;
        this.selector.checkThread();

        this.key = key;
        this.key.attach(this);

        this.outbound = new ByteStream();
        this.listeners = new CopyOnWriteArrayList<PyroClientListener>();
        this.lastEventTime = System.currentTimeMillis();
    }

    //

    private final List<PyroClientListener> listeners;

    public void addListener(PyroClientListener listener) {
        this.selector.checkThread();

        this.listeners.add(listener);
    }

    public void removeListener(PyroClientListener listener) {
        this.selector.checkThread();

        this.listeners.remove(listener);
    }

    public void removeListeners() {
        this.selector.checkThread();

        this.listeners.clear();
    }

    /**
     * Returns the PyroSelector that created this client
     */

    public PyroSelector selector() {
        return this.selector;
    }

    //

    private Object attachment;

    /**
     * Attach any object to a client, for example to store session information
     */

    public void attach(Object attachment) {
        this.attachment = attachment;
    }

    /**
     * Returns the previously attached object, or <code>null</code> if none is
     * set
     */

    @SuppressWarnings("unchecked")
    public <T> T attachment() {
        return (T) this.attachment;
    }

    //

    /**
     * Returns the local socket address (host+port)
     */

    public InetSocketAddress getLocalAddress() {
        Socket s = ((SocketChannel) key.channel()).socket();
        return (InetSocketAddress) s.getLocalSocketAddress();
    }

    /**
     * Returns the remove socket address (host+port)
     */

    public InetSocketAddress getRemoteAddress() {
        Socket s = ((SocketChannel) key.channel()).socket();
        return (InetSocketAddress) s.getRemoteSocketAddress();
    }

    //

    public void setTimeout(int ms) throws IOException {
        this.selector.checkThread();

        ((SocketChannel) key.channel()).socket().setSoTimeout(ms);

        // prevent a call to setTimeout from immediately causing a timeout
        this.lastEventTime = System.currentTimeMillis();
        this.timeout = ms;
    }

    public void setLinger(boolean enabled, int seconds) throws IOException {
        this.selector.checkThread();

        ((SocketChannel) key.channel()).socket().setSoLinger(enabled, seconds);
    }

    public void setKeepAlive(boolean enabled) throws IOException {
        this.selector.checkThread();

        ((SocketChannel) key.channel()).socket().setKeepAlive(enabled);
    }

    private boolean doEagerWrite = false;

    /**
     * If enabled, causes calls to write() to make an attempt to write the
     * bytes, without waiting for the selector to signal writable state.
     */

    public void setEagerWrite(boolean enabled) {
        this.doEagerWrite = enabled;
    }

    /**
     * Will enqueue the bytes to send them<br>
     * 1. when the selector is ready to write, if eagerWrite is disabled
     * (default)<br>
     * 2. immediately, if eagerWrite is enabled<br>
     * The ByteBuffer instance is kept, not copied, and thus should not be
     * modified
     * 
     * @throws PyroException
     *             when shutdown() has been called.
     */

    public void write(ByteBufferDesc data) throws PyroException {
        this.selector.checkThread();

        if (!this.key.isValid()) {
            // graceful, as this is meant to be async
            return;
        }

        if (this.doShutdown) {
            throw new PyroException("shutting down");
        }

        this.outbound.append(data);

        if (this.doEagerWrite) {
            try {
                this.onReadyToWrite(System.currentTimeMillis());
            } catch (NotYetConnectedException exc) {
                this.adjustWriteOp();
            } catch (IOException exc) {
                this.onConnectionError(exc);
                key.cancel();
            }
        } else {
            this.adjustWriteOp();
        }
    }

    /**
     * Writes as many as possible bytes to the socket buffer
     */

    public int flush() {
        int total = 0;

        while (this.outbound.hasData()) {
            int written;

            try {
                written = this.onReadyToWrite(System.currentTimeMillis());
            } catch (IOException exc) {
                written = 0;
            }

            if (written == 0) {
                break;
            }

            total += written;
        }

        return total;
    }

    /**
     * Makes an attempt to write all outbound bytes, fails on failure.
     * 
     * @throws PyroException
     *             on failure
     */

    public int flushOrDie() throws PyroException {
        int total = 0;

        while (this.outbound.hasData()) {
            int written;

            try {
                written = this.onReadyToWrite(System.currentTimeMillis());
            } catch (IOException exc) {
                written = 0;
            }

            if (written == 0) {
                throw new PyroException("failed to flush, wrote " + total
                        + " bytes");
            }

            total += written;
        }

        return total;
    }

    /**
     * Returns whether there are bytes left in the outbound queue.
     */

    public boolean hasDataEnqueued() {
        this.selector.checkThread();

        return this.outbound.hasData();
    }

    private boolean doShutdown = false;

    /**
     * Gracefully shuts down the connection. The connection is closed after the
     * last outbound bytes are sent. Enqueuing new bytes after shutdown, is not
     * allowed and will throw an exception
     */

    public void shutdown() {
        this.selector.checkThread();

        this.doShutdown = true;

        if (!this.hasDataEnqueued()) {
            this.dropConnection();
        }
    }

    /**
     * Immediately drop the connection, regardless of any pending outbound
     * bytes. Actual behaviour depends on the socket linger settings.
     */

    public void dropConnection() {
        this.selector.checkThread();

        if (this.isDisconnected()) {
            return;
        }

        Runnable drop = new Runnable() {
            @Override
            @SuppressWarnings("synthetic-access")
            public void run() {
                try {
                    if (key.channel().isOpen()) {
                        (key.channel()).close();
                    }
                } catch (Exception exc) {
                    selector().scheduleTask(this);
                }
            }
        };

        drop.run();

        this.onConnectionError("local");
    }

    /**
     * Returns whether the connection is connected to a remote client.
     */

    public boolean isDisconnected() {
        this.selector.checkThread();

        return !this.key.channel().isOpen();
    }

    //

    void onInterestOp(long now) {
        if (!key.isValid()) {
            this.onConnectionError("remote");
        } else {
            try {
                if (key.isConnectable())
                    this.onReadyToConnect(now);
                if (key.isReadable())
                    this.onReadyToRead(now);
                if (key.isWritable())
                    this.onReadyToWrite(now);
            } catch (Exception exc) {
                this.onConnectionError(exc);
                key.cancel();
            }
        }
    }

    private long timeout = 0L;

    private long lastEventTime;

    boolean didTimeout(long now) {
        return this.timeout != 0 && (now - this.lastEventTime) > this.timeout;
    }

    private void onReadyToConnect(long now) throws IOException {
        this.selector.checkThread();
        this.lastEventTime = now;

        this.selector.adjustInterestOp(key, SelectionKey.OP_CONNECT, false);
        boolean result = ((SocketChannel) key.channel()).finishConnect();

        for (PyroClientListener listener: this.listeners)
            listener.connectedClient(this);
    }

    private void onReadyToRead(long now) throws IOException {
        this.selector.checkThread();
        this.lastEventTime = now;

        SocketChannel channel = (SocketChannel) key.channel();

        ByteBuffer buffer = this.selector.networkBuffer;

        // read from channel
        buffer.clear();
        int bytes = channel.read(buffer);
        if (bytes == -1)
            throw new EOFException();
        buffer.flip();

        for (PyroClientListener listener: this.listeners)
            listener.receivedData(this, buffer);
    }

    private int onReadyToWrite(long now) throws IOException {
        this.selector.checkThread();
        //this.lastEventTime = now;

        int sent = 0;

        // copy outbound bytes into network buffer
        ByteBuffer buffer = this.selector.networkBuffer;
        buffer.clear();
        this.outbound.get(buffer);
        buffer.flip();

        // write to channel
        if (buffer.hasRemaining()) {
            SocketChannel channel = (SocketChannel) key.channel();
            sent = channel.write(buffer);
        }

        if (sent > 0) {
            this.outbound.discard(sent);
        }

        for (PyroClientListener listener: this.listeners)
            listener.sentData(this, sent);

        this.adjustWriteOp();

        if (this.doShutdown && !this.outbound.hasData()) {
            this.dropConnection();
        }

        return sent;
    }

    void onConnectionError(final Object cause) {
        this.selector.checkThread();

        try {
            // if the key is invalid, the channel may remain open!!
            this.key.channel().close();
        } catch (Exception exc) {
            // type: java.io.IOException
            // message:
            // "A non-blocking socket operation could not be completed immediately"

            // try again later
            this.selector.scheduleTask(new Runnable() {
                @Override
                public void run() {
                    PyroClient.this.onConnectionError(cause);
                }
            });

            return;
        }

        if (cause instanceof ConnectException) {
            for (PyroClientListener listener: this.listeners)
                listener.unconnectableClient(this, (Exception)cause);
        } else if (cause instanceof EOFException) // after read=-1
        {
            for (PyroClientListener listener: this.listeners)
                listener.disconnectedClient(this);
        } else if (cause instanceof IOException) {
            for (PyroClientListener listener: this.listeners)
                listener.droppedClient(this, (IOException) cause);
        } else if (!(cause instanceof String)) {
            for (PyroClientListener listener: this.listeners)
                listener.unconnectableClient(this, null);
        } else if (cause.equals("local")) {
            for (PyroClientListener listener: this.listeners)
                listener.disconnectedClient(this);
        } else if (cause.equals("remote")) {
            for (PyroClientListener listener: this.listeners)
                listener.droppedClient(this, null);
        } else {
            throw new IllegalStateException("illegal cause: " + cause);
        }
    }

    public String toString() {
        return this.getClass().getSimpleName() + "[" + this.getAddressText()
                + "]";
    }

    private String getAddressText() {
        if (!this.key.channel().isOpen())
            return "closed";

        InetSocketAddress sockaddr = this.getRemoteAddress();
        if (sockaddr == null)
            return "connecting";
        InetAddress inetaddr = sockaddr.getAddress();
        return inetaddr.getHostAddress() + "@" + sockaddr.getPort();
    }

    //

    void adjustWriteOp() {
        this.selector.checkThread();

        boolean interested = this.outbound.hasData();

        this.selector.adjustInterestOp(this.key, SelectionKey.OP_WRITE,
                interested);
    }

    static SelectionKey bindAndConfigure(PyroSelector selector,
            SocketChannel channel, InetSocketAddress bind) throws IOException {
        selector.checkThread();

        channel.socket().bind(bind);

        return configure(selector, channel, true);
    }

    static SelectionKey configure(PyroSelector selector,
            SocketChannel channel, boolean connect) throws IOException {
        selector.checkThread();

        channel.configureBlocking(false);
        // channel.socket().setSoLinger(false, 0); // this will b0rk your
        // connections
        channel.socket().setSoLinger(true, 4);
        channel.socket().setReuseAddress(false);
        channel.socket().setKeepAlive(false);
        channel.socket().setTcpNoDelay(true);
        channel.socket().setReceiveBufferSize(PyroSelector.BUFFER_SIZE);
        channel.socket().setSendBufferSize(PyroSelector.BUFFER_SIZE);

        int ops = SelectionKey.OP_READ;
        if (connect)
            ops |= SelectionKey.OP_CONNECT;

        return selector.register(channel, ops);
    }
}
