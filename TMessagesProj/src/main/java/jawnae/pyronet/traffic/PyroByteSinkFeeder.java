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

package jawnae.pyronet.traffic;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import jawnae.pyronet.PyroClient;
import jawnae.pyronet.PyroSelector;
import jawnae.pyronet.events.PyroClientAdapter;

public class PyroByteSinkFeeder extends PyroClientAdapter {
    private final PyroSelector selector;

    private final ByteStream inbound;

    private final LinkedList<ByteSink> sinks;

    public PyroByteSinkFeeder(PyroClient client) {
        this(client.selector());
    }

    public PyroByteSinkFeeder(PyroSelector selector) {
        this(selector, 8 * 1024);
    }

    public PyroByteSinkFeeder(PyroSelector selector, int bufferSize) {
        this.selector = selector;
        this.inbound = new ByteStream();
        this.sinks = new LinkedList<ByteSink>();
    }

    //

    @Override
    public void receivedData(PyroClient client, ByteBuffer data) {
        this.feed(data);
    }

    //

    public ByteBuffer shutdown() {
        int bytes = this.inbound.getByteCount();
        ByteBuffer tmp = this.selector.malloc(bytes);
        this.inbound.get(tmp);
        this.inbound.discard(bytes);
        tmp.flip();
        return tmp;
    }

    public void addByteSink(ByteSink sink) {
        this.selector.checkThread();

        this.register(sink);
    }

    public void feed(ByteBuffer data) {
        ByteBuffer copy = this.selector.copy(data);

        this.inbound.append(copy);

        this.fill();
    }

    final void register(ByteSink sink) {
        this.sinks.addLast(sink);

        this.fill();
    }

    private final void fill() {
        if (this.sinks.isEmpty()) {
            return;
        }

        ByteSink currentSink = this.sinks.removeFirst();

        while (currentSink != null && inbound.hasData()) {
            switch (currentSink.feed(inbound.read())) {
                case ByteSink.FEED_ACCEPTED:
                    continue; // continue to next feed

                case ByteSink.FEED_ACCEPTED_LAST:
                    break; // break out switch, not while

                case ByteSink.FEED_REJECTED:
                    break; // break out switch, not while
            }

            if (this.sinks.isEmpty()) {
                currentSink = null;
                break;
            }

            currentSink = this.sinks.removeFirst();
        }

        if (currentSink != null) {
            this.sinks.addFirst(currentSink);
        }

    }
}
