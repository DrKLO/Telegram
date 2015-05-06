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

import org.telegram.messenger.BuffersStorage;
import org.telegram.messenger.ByteBufferDesc;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ByteStream {
    private final ArrayList<ByteBufferDesc> queue;

    public ByteStream() {
        this.queue = new ArrayList<>();
    }

    public void append(ByteBufferDesc buf) {
        if (buf == null) {
            throw new NullPointerException();
        }
        this.queue.add(buf);
    }

    public boolean hasData() {
        int size = this.queue.size();
        for (ByteBufferDesc aQueue : this.queue) {
            if (aQueue.hasRemaining()) {
                return true;
            }
        }
        return false;
    }

    public void get(ByteBuffer dst) {
        if (dst == null) {
            throw new NullPointerException();
        }

        for (ByteBufferDesc bufferDesc : this.queue) {
            ByteBuffer data = bufferDesc.buffer.slice();

            if (data.remaining() > dst.remaining()) {
                data.limit(dst.remaining());
                dst.put(data);
                break;
            }

            dst.put(data);

            if (!dst.hasRemaining()) {
                break;
            }
        }
    }

    public void discard(int count) {
        int original = count;

        while (count > 0) {
            ByteBufferDesc data = this.queue.get(0);

            if (count < data.buffer.remaining()) {
                data.position(data.position() + count);
                count = 0;
                break;
            }

            this.queue.remove(0);
            BuffersStorage.getInstance().reuseFreeBuffer(data);
            count -= data.buffer.remaining();
        }

        if (count != 0) {
            throw new PyroException("discarded " + (original - count) + "/" + original + " bytes");
        }
    }
}
