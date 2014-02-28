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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteStream {
    private final List<ByteBuffer> queue;

    public ByteStream() {
        // the queue is expected to be relatively small, and iterated often.
        // hence removing the first element will be fast, even when using an
        // ArrayList
        this.queue = new ArrayList<ByteBuffer>();
    }

    /**
     * Appends the ByteBuffer instance to the ByteStream. The bytes are not
     * copied, so do not modify the contents of the ByteBuffer.
     */

    public void append(ByteBuffer buf) {
        if (buf == null)
            throw new NullPointerException();
        this.queue.add(buf);
    }

    /**
     * Returns whether there are any bytes pending in this stream
     */

    public boolean hasData() {
        int size = this.queue.size();
        for (ByteBuffer aQueue : this.queue) {
            if (aQueue.hasRemaining()) {
                return true;
            }
        }
        return false;
    }

    public int getByteCount() {
        int size = this.queue.size();

        int sum = 0;
        for (ByteBuffer aQueue : this.queue) {
            sum += aQueue.remaining();
        }
        return sum;
    }

    /**
     * Fills the specified buffer with as much bytes as possible. When N bytes
     * are read, the buffer position will be increased by N
     */

    public void get(ByteBuffer dst) {
        if (dst == null) {
            throw new NullPointerException();
        }

        for (ByteBuffer data: this.queue) {
            // data pos/lim must not be modified
            data = data.slice();

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

    /**
     * Discards the specified amount of bytes from the stream.
     * 
     * @throws PyroException
     *             if it failed to discard the specified number of bytes
     */

    public void discard(int count) {
        int original = count;

        while (count > 0) {
            // peek at the first buffer
            ByteBuffer data = this.queue.get(0);

            if (count < data.remaining()) {
                // discarding less bytes than remaining in buffer
                data.position(data.position() + count);
                count = 0;
                break;
            }

            // discard the first buffer
            this.queue.remove(0);
            count -= data.remaining();
        }

        if (count != 0) {
            // apparantly we cannot discard the amount of bytes
            // the user demanded, this is a bug in other code
            throw new PyroException("discarded " + (original - count) + "/"
                    + original + " bytes");
        }
    }

    public byte read() {
        ByteBuffer data = this.queue.get(0);
        byte result = data.get();
        if (!data.hasRemaining()) {
            // discard the first buffer
            this.queue.remove(0);
        }
        return result;
    }

}
