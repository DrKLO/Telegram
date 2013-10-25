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

package jawnae.pyronet.events;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import jawnae.pyronet.PyroClient;
import jawnae.pyronet.PyroServer;

public class PyroLazyBastardAdapter implements PyroSelectorListener,
        PyroServerListener, PyroClientListener {
    // --------------- PyroSelectorListener

    public void executingTask(Runnable task) {
        //
    }

    public void taskCrashed(Runnable task, Throwable cause) {
        System.out.println(this.getClass().getSimpleName()
                + ".taskCrashed() caught exception:");
        cause.printStackTrace();
    }

    public void selectedKeys(int count) {
        //
    }

    public void selectFailure(IOException cause) {
        System.out.println(this.getClass().getSimpleName()
                + ".selectFailure() caught exception:");
        cause.printStackTrace();
    }

    public void serverSelected(PyroServer server) {
        //
    }

    public void clientSelected(PyroClient client, int readyOps) {
        //
    }

    // ------------- PyroServerListener

    public void acceptedClient(PyroClient client) {
        //
    }

    // ------------- PyroClientListener

    public void connectedClient(PyroClient client) {
        //
    }

    public void unconnectableClient(PyroClient client) {
        System.out.println(this.getClass().getSimpleName()
                + ".unconnectableClient()");
    }

    public void droppedClient(PyroClient client, IOException cause) {
        if (cause != null && !(cause instanceof EOFException)) {
            System.out.println(this.getClass().getSimpleName()
                    + ".droppedClient() caught exception: " + cause);
        }
    }

    public void disconnectedClient(PyroClient client) {
        //
    }

    //

    public void receivedData(PyroClient client, ByteBuffer data) {
        //
    }

    public void sentData(PyroClient client, int bytes) {
        //
    }

    @Override
    public void serverBindFailed(IOException cause) {
        System.out.println(this.getClass().getSimpleName()
                + ".serverBindFailed() caught exception:");
        cause.printStackTrace();
    }

    @Override
    public void clientBindFailed(IOException cause) {
        System.out.println(this.getClass().getSimpleName()
                + ".serverBindFailed() caught exception:");
        cause.printStackTrace();
    }
}
