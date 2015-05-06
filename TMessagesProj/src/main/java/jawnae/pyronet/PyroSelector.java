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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PyroSelector {
    public static boolean DO_NOT_CHECK_NETWORK_THREAD = true;

    public static final int BUFFER_SIZE = 64 * 1024;

    Thread networkThread;

    final Selector nioSelector;

    final ByteBuffer networkBuffer;

    public PyroSelector() {
        this.networkBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        try {
            this.nioSelector = Selector.open();
        } catch (IOException exc) {
            throw new PyroException("Failed to open a selector?!", exc);
        }

        this.networkThread = Thread.currentThread();
    }

    //

    public final boolean isNetworkThread() {
        return DO_NOT_CHECK_NETWORK_THREAD || networkThread == Thread.currentThread();
    }

    public final Thread networkThread() {
        return this.networkThread;
    }

    public final void checkThread() {
        if (DO_NOT_CHECK_NETWORK_THREAD) {
            return;
        }

        if (!this.isNetworkThread()) {
            throw new PyroException("call from outside the network-thread, you must schedule tasks");
        }
    }

    public PyroClient connect(InetSocketAddress host) throws IOException {
        return this.connect(host, null);
    }

    public PyroClient connect(InetSocketAddress host, InetSocketAddress bind) throws IOException {
        return new PyroClient(this, bind, host);
    }

    public void select() {
        this.select(0);
    }

    public void select(long eventTimeout) {
        this.checkThread();

        //

        this.executePendingTasks();
        this.performNioSelect(eventTimeout);

        final long now = System.currentTimeMillis();
        this.handleSelectedKeys(now);
        this.handleSocketTimeouts(now);
    }

    private void executePendingTasks() {
        while (true) {
            Runnable task = this.tasks.poll();
            if (task == null)
                break;

            try {
                task.run();
            } catch (Throwable cause) {
                cause.printStackTrace();
            }
        }
    }

    private void performNioSelect(long timeout) {
        int selected;
        try {
            selected = nioSelector.select(timeout);
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

    private void handleSelectedKeys(long now) {
        Iterator<SelectionKey> keys = nioSelector.selectedKeys().iterator();

        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            keys.remove();

            if (key.channel() instanceof SocketChannel) {
                PyroClient client = (PyroClient) key.attachment();
                client.onInterestOp(now);
            }
        }
    }

    private void handleSocketTimeouts(long now) {
        for (SelectionKey key: nioSelector.keys()) {
            if (key.channel() instanceof SocketChannel) {
                PyroClient client = (PyroClient) key.attachment();

                if (client.didTimeout(now)) {
                    try {
                        throw new SocketTimeoutException(
                                "PyroNet detected NIO timeout");
                    } catch (SocketTimeoutException exc) {
                        client.onConnectionError(exc);
                    }
                }
            }
        }
    }

    public void spawnNetworkThread(final String name) {
        // now no thread can access this selector
        //
        // N.B.
        // -- updating this non-volatile field is thread-safe
        // -- because the current thread can see it (causing it
        // -- to become UNACCESSIBLE), and all other threads
        // -- that might not see the change will
        // -- (continue to) block access to this selector
        this.networkThread = null;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // spawned thread can access this selector
                //
                // N.B.
                // -- updating this non-volatile field is thread-safe
                // -- because the current thread can see it (causing it
                // -- to become ACCESSIBLE), and all other threads
                // -- that might not see the change will
                // -- (continue to) block access to this selector
                PyroSelector.this.networkThread = Thread.currentThread();

                // start select-loop
                try {
                    while (true) {
                        PyroSelector.this.select();
                    }
                } catch (Exception exc) {
                    // this never be caused by Pyro-code
                    throw new IllegalStateException(exc);
                }
            }
        }, name).start();
    }

    //

    private BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<Runnable>();

    public void scheduleTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }

        try {
            this.tasks.put(task);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        wakeup();
    }

    public void wakeup() {
        this.nioSelector.wakeup();
    }

    //

    final SelectionKey register(SelectableChannel channel, int ops)
            throws IOException {
        return channel.register(this.nioSelector, ops);
    }

    final boolean adjustInterestOp(SelectionKey key, int op, boolean state) {
        this.checkThread();

        try {
            int ops = key.interestOps();
            boolean changed = state != ((ops & op) == op);
            if (changed)
                key.interestOps(state ? (ops | op) : (ops & ~op));
            return changed;
        } catch (CancelledKeyException exc) {
            // ignore
            return false;
        }
    }
}
