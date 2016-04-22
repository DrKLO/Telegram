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

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ICustomTabsCallback extends IInterface {
    void onNavigationEvent(int var1, Bundle var2) throws RemoteException;

    void extraCallback(String var1, Bundle var2) throws RemoteException;

    abstract class Stub extends Binder implements ICustomTabsCallback {
        private static final String DESCRIPTOR = "android.support.customtabs.ICustomTabsCallback";
        static final int TRANSACTION_onNavigationEvent = 2;
        static final int TRANSACTION_extraCallback = 3;

        public Stub() {
            this.attachInterface(this, "android.support.customtabs.ICustomTabsCallback");
        }

        public static ICustomTabsCallback asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            } else {
                IInterface iin = obj.queryLocalInterface("android.support.customtabs.ICustomTabsCallback");
                return (iin != null && iin instanceof ICustomTabsCallback ? (ICustomTabsCallback) iin : new ICustomTabsCallback.Stub.Proxy(obj));
            }
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Bundle _arg1;
            switch (code) {
                case 2:
                    data.enforceInterface("android.support.customtabs.ICustomTabsCallback");
                    int _arg01 = data.readInt();
                    if (0 != data.readInt()) {
                        _arg1 = Bundle.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }

                    this.onNavigationEvent(_arg01, _arg1);
                    return true;
                case 3:
                    data.enforceInterface("android.support.customtabs.ICustomTabsCallback");
                    String _arg0 = data.readString();
                    if (0 != data.readInt()) {
                        _arg1 = Bundle.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }

                    this.extraCallback(_arg0, _arg1);
                    return true;
                case 1598968902:
                    reply.writeString("android.support.customtabs.ICustomTabsCallback");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ICustomTabsCallback {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return "android.support.customtabs.ICustomTabsCallback";
            }

            public void onNavigationEvent(int navigationEvent, Bundle extras) throws RemoteException {
                Parcel _data = Parcel.obtain();

                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsCallback");
                    _data.writeInt(navigationEvent);
                    if (extras != null) {
                        _data.writeInt(1);
                        extras.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    this.mRemote.transact(2, _data, null, 1);
                } finally {
                    _data.recycle();
                }

            }

            public void extraCallback(String callbackName, Bundle args) throws RemoteException {
                Parcel _data = Parcel.obtain();

                try {
                    _data.writeInterfaceToken("android.support.customtabs.ICustomTabsCallback");
                    _data.writeString(callbackName);
                    if (args != null) {
                        _data.writeInt(1);
                        args.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }

                    this.mRemote.transact(3, _data, null, 1);
                } finally {
                    _data.recycle();
                }

            }
        }
    }
}
