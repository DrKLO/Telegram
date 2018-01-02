/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.support.customtabs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * A {@link ServiceConnection} for Custom Tabs providers to use while connecting to a
 * {@link PostMessageService} on the client side.
 */
public abstract class PostMessageServiceConnection implements ServiceConnection {
    private final Object mLock = new Object();
    private final ICustomTabsCallback mSessionBinder;
    private IPostMessageService mService;

    public PostMessageServiceConnection(CustomTabsSessionToken session) {
        mSessionBinder = ICustomTabsCallback.Stub.asInterface(session.getCallbackBinder());
    }

    /**
     * Binds the browser side to the client app through the given {@link PostMessageService} name.
     * After this, this {@link PostMessageServiceConnection} can be used for sending postMessage
     * related communication back to the client.
     * @param context A context to bind to the service.
     * @param packageName The name of the package to be bound to.
     * @return Whether the binding was successful.
     */
    public boolean bindSessionToPostMessageService(Context context, String packageName) {
        Intent intent = new Intent();
        intent.setClassName(packageName, PostMessageService.class.getName());
        return context.bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbinds this service connection from the given context.
     * @param context The context to be unbound from.
     */
    public void unbindFromContext(Context context) {
        context.unbindService(this);
    }

    @Override
    public final void onServiceConnected(ComponentName name, IBinder service) {
        mService = IPostMessageService.Stub.asInterface(service);
        onPostMessageServiceConnected();
    }

    @Override
    public final void onServiceDisconnected(ComponentName name) {
        mService = null;
        onPostMessageServiceDisconnected();
    }

    /**
     * Notifies the client that the postMessage channel requested with
     * {@link CustomTabsService#requestPostMessageChannel(
     * CustomTabsSessionToken, android.net.Uri)} is ready. This method should be
     * called when the browser binds to the client side {@link PostMessageService} and also readies
     * a connection to the web frame.
     *
     * @param extras Reserved for future use.
     * @return Whether the notification was sent to the remote successfully.
     */
    public final boolean notifyMessageChannelReady(Bundle extras) {
        if (mService == null) return false;
        synchronized (mLock) {
            try {
                mService.onMessageChannelReady(mSessionBinder, extras);
            } catch (RemoteException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Posts a message to the client. This should be called when a tab controlled by related
     * {@link CustomTabsSession} has sent a postMessage. If postMessage() is called from a single
     * thread, then the messages will be posted in the same order.
     *
     * @param message The message sent.
     * @param extras Reserved for future use.
     * @return Whether the postMessage was sent to the remote successfully.
     */
    public final boolean postMessage(String message, Bundle extras) {
        if (mService == null) return false;
        synchronized (mLock) {
            try {
                mService.onPostMessage(mSessionBinder, message, extras);
            } catch (RemoteException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called when the {@link PostMessageService} connection is established.
     */
    public void onPostMessageServiceConnected() {}

    /**
     * Called when the connection is lost with the {@link PostMessageService}.
     */
    public void onPostMessageServiceDisconnected() {}
}
