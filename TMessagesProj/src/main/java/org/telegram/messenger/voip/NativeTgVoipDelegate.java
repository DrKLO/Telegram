package org.telegram.messenger.voip;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

public final class NativeTgVoipDelegate implements TgVoip.Delegate {

    @Override
    public TgVoip.Instance makeInstance(TgVoip.Config config, String persistentStateFilePath, TgVoip.Endpoint[] endpoints, TgVoip.Proxy proxy, int networkType, TgVoip.EncryptionKey encryptionKey) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("create new tgvoip instance, version " + getVersion());
        }
        final NativeTgVoipInstance instance = new NativeTgVoipInstance(persistentStateFilePath);
        instance.setNativeInstanceId(makeNativeInstance(instance, config, persistentStateFilePath, endpoints, proxy, networkType, encryptionKey));
        return instance;
    }

    private native long makeNativeInstance(TgVoip.Instance instance, TgVoip.Config config, String persistentStateFilePath, TgVoip.Endpoint[] endpoints, TgVoip.Proxy proxy, int networkType, TgVoip.EncryptionKey encryptionKey);

    @Override
    public native void setGlobalServerConfig(String serverConfigJson);

    @Override
    public native void setBufferSize(int size);

    @Override
    public native int getConnectionMaxLayer();

    @Override
    public native String getVersion();
}
