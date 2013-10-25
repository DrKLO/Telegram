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

import jawnae.pyronet.PyroClient;

public abstract class ByteSinkPacket16 implements ByteSink {
    public static void sendTo(PyroClient client, byte[] payload) {
        if (payload.length > 0x0000FFFF) {
            throw new IllegalStateException("packet bigger than 64K-1 bytes");
        }

        byte[] wrapped = new byte[2 + payload.length];
        wrapped[0] = (byte) (payload.length >> 8);
        wrapped[1] = (byte) (payload.length >> 0);
        System.arraycopy(payload, 0, wrapped, 2, payload.length);

        client.write(client.selector().malloc(wrapped));
    }

    //

    ByteSinkLength current;

    public ByteSinkPacket16() {
        this.reset();
    }

    @Override
    public void reset() {
        this.current = new ByteSinkLength(2) {
            @Override
            public void onReady(ByteBuffer buffer) {
                // header is received
                int len = buffer.getShort(0) & 0xFFFF;

                current = new ByteSinkLength(len) {
                    @Override
                    public void onReady(ByteBuffer buffer) {
                        // sometime we want do reset in
                        // ByteSinkPacket16.this.onReady, then add the sink back
                        // to feeder, in such process, onReady should be execute
                        // at last.
                        current = null;

                        // content is received
                        ByteSinkPacket16.this.onReady(buffer);
                    }
                };
            }
        };
    }

    @Override
    public int feed(byte b) {
        if (this.current == null) {
            throw new IllegalStateException();
        }

        int result = this.current.feed(b);

        if (result == FEED_ACCEPTED) {
            return result;
        }

        // 'current' will be replaced by now

        if (this.current == null) {
            return result;
        }

        else {
            return FEED_ACCEPTED;
        }
        // return this.current.feed(b);
    }
}
