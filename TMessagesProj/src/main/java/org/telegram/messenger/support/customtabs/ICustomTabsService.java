/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger.support.customtabs;

import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

public interface ICustomTabsService extends IInterface {
    boolean warmup(long var1) throws RemoteException;

    boolean newSession(ICustomTabsCallback var1) throws RemoteException;

    boolean mayLaunchUrl(ICustomTabsCallback var1, Uri var2, Bundle var3, List<Bundle> var4) throws RemoteException;

    Bundle extraCommand(String var1, Bundle var2) throws RemoteException;

    boolean updateVisuals(ICustomTabsCallback var1, Bundle var2) throws RemoteException;

    boolean requestPostMessageChannel(ICustomTabsCallback var1, Uri var2) throws RemoteException;

    int postMessage(ICustomTabsCallback var1, String var2, Bundle var3) throws RemoteException;

    abstract class Stub extends Binder implements ICustomTabsService {
        private static final String DESCRIPTOR = "android.support.customtabs.ICustomTabsService";
        static final int TRANSACTION_warmup = 2;
        static final int TRANSACTION_newSession = 3;
        static final int TRANSACTION_mayLaunchUrl = 4;
        static final int TRANSACTION_extraCommand = 5;
        static final int TRANSACTION_updateVisuals = 6;
        static final int TRANSACTION_requestPostMessageChannel = 7;
        static final int TRANSACTION_postMessage = 8;

        public Stub() {
            this.attachInterface(this, "android.support.customtabs.ICustomTabsService");
        }

        public static ICustomTabsService asInterface(IBinder obj) {
            if(obj == null) {
                return null;
            } else {
                IInterface iin = obj.queryLocalInterface("android.support.customtabs.ICustomTabsService");
                return (iin != null && iin instanceof ICustomTabsService?(ICustomTabsService)iin:new ICustomTabsService.Stub.Proxy(obj));
            }
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            ICustomTabsCallback _arg0;
            Bundle _arg2;
            Uri _arg11;
            Bundle _arg12;
            boolean _arg21;
            switch(code) {
                case 2:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    long _arg02 = data.readLong();
                    _arg21 = this.warmup(_arg02);
                    reply.writeNoException();
                    reply.writeInt(_arg21?1:0);
                    return true;
                case 3:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    _arg0 = ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
                    boolean _arg13 = this.newSession(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_arg13?1:0);
                    return true;
                case 4:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    _arg0 = ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
                    if(0 != data.readInt()) {
                        _arg11 = Uri.CREATOR.createFromParcel(data);
                    } else {
                        _arg11 = null;
                    }

                    if(0 != data.readInt()) {
                        _arg2 = Bundle.CREATOR.createFromParcel(data);
                    } else {
                        _arg2 = null;
                    }

                    ArrayList _result2 = data.createTypedArrayList(Bundle.CREATOR);
                    boolean _result1 = this.mayLaunchUrl(_arg0, _arg11, _arg2, _result2);
                    reply.writeNoException();
                    reply.writeInt(_result1?1:0);
                    return true;
                case 5:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    String _arg01 = data.readString();
                    if(0 != data.readInt()) {
                        _arg12 = Bundle.CREATOR.createFromParcel(data);
                    } else {
                        _arg12 = null;
                    }

                    _arg2 = this.extraCommand(_arg01, _arg12);
                    reply.writeNoException();
                    if(_arg2 != null) {
                        reply.writeInt(1);
                        _arg2.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }

                    return true;
                case 6:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    _arg0 = ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
                    if(0 != data.readInt()) {
                        _arg12 = Bundle.CREATOR.createFromParcel(data);
                    } else {
                        _arg12 = null;
                    }

                    _arg21 = this.updateVisuals(_arg0, _arg12);
                    reply.writeNoException();
                    reply.writeInt(_arg21?1:0);
                    return true;
                case 7:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    _arg0 = ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
                    if(0 != data.readInt()) {
                        _arg11 = Uri.CREATOR.createFromParcel(data);
                    } else {
                        _arg11 = null;
                    }

                    _arg21 = this.requestPostMessageChannel(_arg0, _arg11);
                    reply.writeNoException();
                    reply.writeInt(_arg21?1:0);
                    return true;
                case 8:
                    data.enforceInterface("android.support.customtabs.ICustomTabsService");
                    _arg0 = ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
                    String _arg1 = data.readString();
                    if(0 != data.readInt()) {
                        _arg2 = Bundle.CREATOR.createFromParcel(data);
                    } else {
                        _arg2 = null;
                    }

                    int _result = this.postMessage(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case 1598968902:
                    reply.writeString("android.support.customtabs.ICustomTabsService");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ICustomTabsService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return "android.support.customtabs.ICustomTabsService";
            }

            public boolean warmup(long flags) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                boolean _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeLong(flags);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }

            public boolean newSession(ICustomTabsCallback callback) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                boolean _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeStrongBinder(callback != null?callback.asBinder():null);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }

            public boolean mayLaunchUrl(ICustomTabsCallback callback, Uri url, Bundle extras, List<Bundle> otherLikelyBundles) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                boolean _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeStrongBinder(callback != null?callback.asBinder():null);
                    if(url != null) {
                        _data.writeInt(1);
                        url.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    if(extras != null) {
                        _data.writeInt(1);
                        extras.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    _data.writeTypedList(otherLikelyBundles);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }

            public Bundle extraCommand(String commandName, Bundle args) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                Bundle _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeString(commandName);
                    if(args != null) {
                        _data.writeInt(1);
                        args.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                    if(0 != _reply.readInt()) {
                        _result = Bundle.CREATOR.createFromParcel(_reply);
                    } else {
                        _result = null;
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }

            public boolean updateVisuals(ICustomTabsCallback callback, Bundle bundle) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                boolean _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeStrongBinder(callback != null?callback.asBinder():null);
                    if(bundle != null) {
                        _data.writeInt(1);
                        bundle.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }

            public boolean requestPostMessageChannel(ICustomTabsCallback callback, Uri postMessageOrigin) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                boolean _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeStrongBinder(callback != null?callback.asBinder():null);
                    if(postMessageOrigin != null) {
                        _data.writeInt(1);
                        postMessageOrigin.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }

            public int postMessage(ICustomTabsCallback callback, String message, Bundle extras) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();

                int _result;
                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsService");
                    _data.writeStrongBinder(callback != null?callback.asBinder():null);
                    _data.writeString(message);
                    if(extras != null) {
                        _data.writeInt(1);
                        extras.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }

                return _result;
            }
        }
    }
}
