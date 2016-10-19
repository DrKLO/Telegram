/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import org.telegram.messenger.exoplayer.drm.DrmInitData.SchemeInitData;
import org.telegram.messenger.exoplayer.drm.ExoMediaDrm.KeyRequest;
import org.telegram.messenger.exoplayer.drm.ExoMediaDrm.OnEventListener;
import org.telegram.messenger.exoplayer.drm.ExoMediaDrm.ProvisionRequest;
import org.telegram.messenger.exoplayer.extractor.mp4.PsshAtomUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.HashMap;
import java.util.UUID;

/**
 * A base class for {@link DrmSessionManager} implementations that support streaming playbacks
 * using {@link ExoMediaDrm}.
 */
@TargetApi(18)
public class StreamingDrmSessionManager<T extends ExoMediaCrypto> implements DrmSessionManager<T> {

  /**
   * Interface definition for a callback to be notified of {@link StreamingDrmSessionManager}
   * events.
   */
  public interface EventListener {

    /**
     * Invoked each time keys are loaded.
     */
    void onDrmKeysLoaded();

    /**
     * Invoked when a drm error occurs.
     *
     * @param e The corresponding exception.
     */
    void onDrmSessionManagerError(Exception e);

  }

  /**
   * UUID for the Widevine DRM scheme.
   */
  public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

  /**
   * UUID for the PlayReady DRM scheme.
   * <p>
   * Note that PlayReady is unsupported by most Android devices, with the exception of Android TV
   * devices, which do provide support.
   */
  public static final UUID PLAYREADY_UUID = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);

  /**
   * The key to use when passing CustomData to a PlayReady instance in an optional parameter map.
   */
  public static final String PLAYREADY_CUSTOM_DATA_KEY = "PRCustomData";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;

  private final Handler eventHandler;
  private final EventListener eventListener;
  private final ExoMediaDrm<T> mediaDrm;
  private final HashMap<String, String> optionalKeyRequestParameters;

  /* package */ final MediaDrmHandler mediaDrmHandler;
  /* package */ final MediaDrmCallback callback;
  /* package */ final PostResponseHandler postResponseHandler;
  /* package */ final UUID uuid;

  private HandlerThread requestHandlerThread;
  private Handler postRequestHandler;

  private int openCount;
  private boolean provisioningInProgress;
  private int state;
  private T mediaCrypto;
  private Exception lastException;
  private SchemeInitData schemeInitData;
  private byte[] sessionId;

  private static FrameworkMediaDrm createFrameworkDrm(UUID uuid) throws UnsupportedDrmException {
    try {
      return new FrameworkMediaDrm(uuid);
    } catch (UnsupportedSchemeException e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME, e);
    } catch (Exception e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_INSTANTIATION_ERROR, e);
    }
  }

  /**
   * Instantiates a new instance using the Widevine scheme.
   *
   * @param playbackLooper The looper associated with the media playback thread. Should usually be
   *     obtained using {@link org.telegram.messenger.exoplayer.ExoPlayer#getPlaybackLooper()}.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static StreamingDrmSessionManager<FrameworkMediaCrypto> newWidevineInstance(
      Looper playbackLooper, MediaDrmCallback callback,
      HashMap<String, String> optionalKeyRequestParameters,
      Handler eventHandler, EventListener eventListener) throws UnsupportedDrmException {
    return StreamingDrmSessionManager.newFrameworkInstance(WIDEVINE_UUID, playbackLooper, callback,
        optionalKeyRequestParameters, eventHandler, eventListener);
  }

  /**
   * Instantiates a new instance using the PlayReady scheme.
   * <p>
   * Note that PlayReady is unsupported by most Android devices, with the exception of Android TV
   * devices, which do provide support.
   *
   * @param playbackLooper The looper associated with the media playback thread. Should usually be
   *     obtained using {@link org.telegram.messenger.exoplayer.ExoPlayer#getPlaybackLooper()}.
   * @param callback Performs key and provisioning requests.
   * @param customData Optional custom data to include in requests generated by the instance.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static StreamingDrmSessionManager<FrameworkMediaCrypto> newPlayReadyInstance(
      Looper playbackLooper, MediaDrmCallback callback, String customData, Handler eventHandler,
      EventListener eventListener) throws UnsupportedDrmException {
    HashMap<String, String> optionalKeyRequestParameters;
    if (!TextUtils.isEmpty(customData)) {
      optionalKeyRequestParameters = new HashMap<>();
      optionalKeyRequestParameters.put(PLAYREADY_CUSTOM_DATA_KEY, customData);
    } else {
      optionalKeyRequestParameters = null;
    }
    return StreamingDrmSessionManager.newFrameworkInstance(PLAYREADY_UUID, playbackLooper, callback,
        optionalKeyRequestParameters, eventHandler, eventListener);
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param playbackLooper The looper associated with the media playback thread. Should usually be
   *     obtained using {@link org.telegram.messenger.exoplayer.ExoPlayer#getPlaybackLooper()}.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static StreamingDrmSessionManager<FrameworkMediaCrypto> newFrameworkInstance(
      UUID uuid, Looper playbackLooper, MediaDrmCallback callback,
      HashMap<String, String> optionalKeyRequestParameters, Handler eventHandler,
      EventListener eventListener) throws UnsupportedDrmException {
    return StreamingDrmSessionManager.newInstance(uuid, playbackLooper, callback,
        optionalKeyRequestParameters, eventHandler, eventListener, createFrameworkDrm(uuid));
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param playbackLooper The looper associated with the media playback thread. Should usually be
   *     obtained using {@link org.telegram.messenger.exoplayer.ExoPlayer#getPlaybackLooper()}.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static<T extends ExoMediaCrypto> StreamingDrmSessionManager<T> newInstance(
      UUID uuid, Looper playbackLooper, MediaDrmCallback callback,
      HashMap<String, String> optionalKeyRequestParameters, Handler eventHandler,
      EventListener eventListener, ExoMediaDrm<T> mediaDrm) throws UnsupportedDrmException {
    return new StreamingDrmSessionManager<>(uuid, playbackLooper, callback,
        optionalKeyRequestParameters, eventHandler, eventListener, mediaDrm);
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param playbackLooper The looper associated with the media playback thread. Should usually be
   *     obtained using {@link org.telegram.messenger.exoplayer.ExoPlayer#getPlaybackLooper()}.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  private StreamingDrmSessionManager(UUID uuid, Looper playbackLooper, MediaDrmCallback callback,
      HashMap<String, String> optionalKeyRequestParameters, Handler eventHandler,
      EventListener eventListener, ExoMediaDrm<T> mediaDrm) throws UnsupportedDrmException {
    this.uuid = uuid;
    this.callback = callback;
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.mediaDrm = mediaDrm;
    mediaDrm.setOnEventListener(new MediaDrmEventListener());
    mediaDrmHandler = new MediaDrmHandler(playbackLooper);
    postResponseHandler = new PostResponseHandler(playbackLooper);
    state = STATE_CLOSED;
  }

  @Override
  public final int getState() {
    return state;
  }

  @Override
  public final T getMediaCrypto() {
    if (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      throw new IllegalStateException();
    }
    return mediaCrypto;
  }

  @Override
  public boolean requiresSecureDecoderComponent(String mimeType) {
    if (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      throw new IllegalStateException();
    }
    return mediaCrypto.requiresSecureDecoderComponent(mimeType);
  }

  @Override
  public final Exception getError() {
    return state == STATE_ERROR ? lastException : null;
  }

  /**
   * Provides access to {@link MediaDrm#getPropertyString(String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The key to request.
   * @return The retrieved property.
   */
  public final String getPropertyString(String key) {
    return mediaDrm.getPropertyString(key);
  }

  /**
   * Provides access to {@link MediaDrm#setPropertyString(String, String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The property to write.
   * @param value The value to write.
   */
  public final void setPropertyString(String key, String value) {
    mediaDrm.setPropertyString(key, value);
  }

  /**
   * Provides access to {@link MediaDrm#getPropertyByteArray(String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The key to request.
   * @return The retrieved property.
   */
  public final byte[] getPropertyByteArray(String key) {
    return mediaDrm.getPropertyByteArray(key);
  }

  /**
   * Provides access to {@link MediaDrm#setPropertyByteArray(String, byte[])}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The property to write.
   * @param value The value to write.
   */
  public final void setPropertyByteArray(String key, byte[] value) {
    mediaDrm.setPropertyByteArray(key, value);
  }

  @Override
  public void open(DrmInitData drmInitData) {
    if (++openCount != 1) {
      return;
    }
    if (postRequestHandler == null) {
      requestHandlerThread = new HandlerThread("DrmRequestHandler");
      requestHandlerThread.start();
      postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());
    }
    if (schemeInitData == null) {
      schemeInitData = drmInitData.get(uuid);
      if (schemeInitData == null) {
        onError(new IllegalStateException("Media does not support uuid: " + uuid));
        return;
      }
      if (Util.SDK_INT < 21) {
        // Prior to L the Widevine CDM required data to be extracted from the PSSH atom.
        byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(schemeInitData.data, WIDEVINE_UUID);
        if (psshData == null) {
          // Extraction failed. schemeData isn't a Widevine PSSH atom, so leave it unchanged.
        } else {
          schemeInitData = new SchemeInitData(schemeInitData.mimeType, psshData);
        }
      }
    }
    state = STATE_OPENING;
    openInternal(true);
  }

  @Override
  public void close() {
    if (--openCount != 0) {
      return;
    }
    state = STATE_CLOSED;
    provisioningInProgress = false;
    mediaDrmHandler.removeCallbacksAndMessages(null);
    postResponseHandler.removeCallbacksAndMessages(null);
    postRequestHandler.removeCallbacksAndMessages(null);
    postRequestHandler = null;
    requestHandlerThread.quit();
    requestHandlerThread = null;
    schemeInitData = null;
    mediaCrypto = null;
    lastException = null;
    if (sessionId != null) {
      mediaDrm.closeSession(sessionId);
      sessionId = null;
    }
  }

  private void openInternal(boolean allowProvisioning) {
    try {
      sessionId = mediaDrm.openSession();
      mediaCrypto = mediaDrm.createMediaCrypto(uuid, sessionId);
      state = STATE_OPENED;
      postKeyRequest();
    } catch (NotProvisionedException e) {
      if (allowProvisioning) {
        postProvisionRequest();
      } else {
        onError(e);
      }
    } catch (Exception e) {
      onError(e);
    }
  }

  private void postProvisionRequest() {
    if (provisioningInProgress) {
      return;
    }
    provisioningInProgress = true;
    ProvisionRequest request = mediaDrm.getProvisionRequest();
    postRequestHandler.obtainMessage(MSG_PROVISION, request).sendToTarget();
  }

  private void onProvisionResponse(Object response) {
    provisioningInProgress = false;
    if (state != STATE_OPENING && state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
      if (state == STATE_OPENING) {
        openInternal(false);
      } else {
        postKeyRequest();
      }
    } catch (DeniedByServerException e) {
      onError(e);
    }
  }

  private void postKeyRequest() {
    KeyRequest keyRequest;
    try {
      keyRequest = mediaDrm.getKeyRequest(sessionId, schemeInitData.data, schemeInitData.mimeType,
          MediaDrm.KEY_TYPE_STREAMING, optionalKeyRequestParameters);
      postRequestHandler.obtainMessage(MSG_KEYS, keyRequest).sendToTarget();
    } catch (NotProvisionedException e) {
      onKeysError(e);
    }
  }

  private void onKeyResponse(Object response) {
    if (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onKeysError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideKeyResponse(sessionId, (byte[]) response);
      state = STATE_OPENED_WITH_KEYS;
      if (eventHandler != null && eventListener != null) {
        eventHandler.post(new Runnable() {
          @Override
          public void run() {
            eventListener.onDrmKeysLoaded();
          }
        });
      }
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeysError(Exception e) {
    if (e instanceof NotProvisionedException) {
      postProvisionRequest();
    } else {
      onError(e);
    }
  }

  private void onError(final Exception e) {
    lastException = e;
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onDrmSessionManagerError(e);
        }
      });
    }
    if (state != STATE_OPENED_WITH_KEYS) {
      state = STATE_ERROR;
    }
  }

  @SuppressLint("HandlerLeak")
  private class MediaDrmHandler extends Handler {

    public MediaDrmHandler(Looper looper) {
      super(looper);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleMessage(Message msg) {
      if (openCount == 0 || (state != STATE_OPENED && state != STATE_OPENED_WITH_KEYS)) {
        return;
      }
      switch (msg.what) {
        case MediaDrm.EVENT_KEY_REQUIRED:
          postKeyRequest();
          return;
        case MediaDrm.EVENT_KEY_EXPIRED:
          state = STATE_OPENED;
          onError(new KeysExpiredException());
          return;
        case MediaDrm.EVENT_PROVISION_REQUIRED:
          state = STATE_OPENED;
          postProvisionRequest();
          return;
      }
    }

  }

  private class MediaDrmEventListener implements OnEventListener<T> {

    @Override
    public void onEvent(ExoMediaDrm<? extends T> mediaDrm, byte[] sessionId, int event,
        int extra, byte[] data) {
      mediaDrmHandler.sendEmptyMessage(event);
    }
  }

  @SuppressLint("HandlerLeak")
  private class PostResponseHandler extends Handler {

    public PostResponseHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_PROVISION:
          onProvisionResponse(msg.obj);
          return;
        case MSG_KEYS:
          onKeyResponse(msg.obj);
          return;
      }
    }

  }

  @SuppressLint("HandlerLeak")
  private class PostRequestHandler extends Handler {

    public PostRequestHandler(Looper backgroundLooper) {
      super(backgroundLooper);
    }

    @Override
    public void handleMessage(Message msg) {
      Object response;
      try {
        switch (msg.what) {
          case MSG_PROVISION:
            response = callback.executeProvisionRequest(uuid, (ProvisionRequest) msg.obj);
            break;
          case MSG_KEYS:
            response = callback.executeKeyRequest(uuid, (KeyRequest) msg.obj);
            break;
          default:
            throw new RuntimeException();
        }
      } catch (Exception e) {
        response = e;
      }
      postResponseHandler.obtainMessage(msg.what, response).sendToTarget();
    }

  }

}
