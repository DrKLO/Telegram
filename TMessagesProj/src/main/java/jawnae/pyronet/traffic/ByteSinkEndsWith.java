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

public abstract class ByteSinkEndsWith implements ByteSink {
    private final ByteBuffer result;

    private final byte[] endsWith;

    private final boolean includeEndsWith;

    private int matchCount;

    private int filled;

    public ByteSinkEndsWith(byte[] endsWith, int capacity,
            boolean includeEndsWith) {
        if (endsWith == null || endsWith.length == 0)
            throw new IllegalStateException();
        this.result = ByteBuffer.allocate(capacity);
        this.endsWith = endsWith;
        this.includeEndsWith = includeEndsWith;

        this.reset();
    }

    @Override
    public void reset() {
        this.result.clear();
        this.matchCount = 0;
        this.filled = 0;
    }

    @Override
    public int feed(byte b) {
        if (this.endsWith[this.matchCount] == b) {
            this.matchCount++;
        } else {
            this.matchCount = 0;
        }

        this.result.put(this.filled, b);

        this.filled += 1;

        if (this.matchCount == this.endsWith.length) {
            int len = this.filled
                    - (this.includeEndsWith ? 0 : this.endsWith.length);
            this.result.limit(len);
            this.onReady(this.result);
            return FEED_ACCEPTED_LAST;
        }

        return ByteSink.FEED_ACCEPTED;
    }
}
