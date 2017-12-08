/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.drm.DrmInitData.SchemeData;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.OnEventListener;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import org.telegram.messenger.exoplayer2.extractor.mp4.PsshAtomUtil;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link DrmSessionManager} that supports playbacks using {@link MediaDrm}.
 */
@TargetApi(18)
public class DefaultDrmSessionManager<T extends ExoMediaCrypto> implements DrmSessionManager<T>,
    DrmSession<T> {

  /**
   * Listener of {@link DefaultDrmSessionManager} events.
   */
  public interface EventListener {

    /**
     * Called each time keys are loaded.
     */
    void onDrmKeysLoaded();

    /**
     * Called when a drm error occurs.
     *
     * @param e The corresponding exception.
     */
    void onDrmSessionManagerError(Exception e);

    /**
     * Called each time offline keys are restored.
     */
    void onDrmKeysRestored();

    /**
     * Called each time offline keys are removed.
     */
    void onDrmKeysRemoved();

  }

  /**
   * The key to use when passing CustomData to a PlayReady instance in an optional parameter map.
   */
  public static final String PLAYREADY_CUSTOM_DATA_KEY = "PRCustomData";

  /** Determines the action to be done after a session acquired. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_PLAYBACK, MODE_QUERY, MODE_DOWNLOAD, MODE_RELEASE})
  public @interface Mode {}
  /**
   * Loads and refreshes (if necessary) a license for playback. Supports streaming and offline
   * licenses.
   */
  public static final int MODE_PLAYBACK = 0;
  /**
   * Restores an offline license to allow its status to be queried. If the offline license is
   * expired sets state to {@link #STATE_ERROR}.
   */
  public static final int MODE_QUERY = 1;
  /** Downloads an offline license or renews an existing one. */
  public static final int MODE_DOWNLOAD = 2;
  /** Releases an existing offline license. */
  public static final int MODE_RELEASE = 3;

  private static final String TAG = "OfflineDrmSessionMngr";
  private static final String CENC_SCHEME_MIME_TYPE = "cenc";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;

  private static final int MAX_LICENSE_DURATION_TO_RENEW = 60;

  private final Handler eventHandler;
  private final EventListener eventListener;
  private final ExoMediaDrm<T> mediaDrm;
  private final HashMap<String, String> optionalKeyRequestParameters;

  /* package */ final MediaDrmCallback callback;
  /* package */ final UUID uuid;

  /* package */ MediaDrmHandler mediaDrmHandler;
  /* package */ PostResponseHandler postResponseHandler;

  private Looper playbackLooper;
  private HandlerThread requestHandlerThread;
  private Handler postRequestHandler;

  private int mode;
  private int openCount;
  private boolean provisioningInProgress;
  @DrmSession.State
  private int state;
  private T mediaCrypto;
  private DrmSessionException lastException;
  private byte[] schemeInitData;
  private String schemeMimeType;
  private byte[] sessionId;
  private byte[] offlineLicenseKeySetId;

  /**
   * Instantiates a new instance using the Widevine scheme.
   *
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static DefaultDrmSessionManager<FrameworkMediaCrypto> newWidevineInstance(
      MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters,
      Handler eventHandler, EventListener eventListener) throws UnsupportedDrmException {
    return newFrameworkInstance(C.WIDEVINE_UUID, callback, optionalKeyRequestParameters,
        eventHandler, eventListener);
  }

  /**
   * Instantiates a new instance using the PlayReady scheme.
   * <p>
   * Note that PlayReady is unsupported by most Android devices, with the exception of Android TV
   * devices, which do provide support.
   *
   * @param callback Performs key and provisioning requests.
   * @param customData Optional custom data to include in requests generated by the instance.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static DefaultDrmSessionManager<FrameworkMediaCrypto> newPlayReadyInstance(
      MediaDrmCallback callback, String customData, Handler eventHandler,
      EventListener eventListener) throws UnsupportedDrmException {
    HashMap<String, String> optionalKeyRequestParameters;
    if (!TextUtils.isEmpty(customData)) {
      optionalKeyRequestParameters = new HashMap<>();
      optionalKeyRequestParameters.put(PLAYREADY_CUSTOM_DATA_KEY, customData);
    } else {
      optionalKeyRequestParameters = null;
    }
    return newFrameworkInstance(C.PLAYREADY_UUID, callback, optionalKeyRequestParameters,
        eventHandler, eventListener);
  }

  /**
   * Instantiates a new instance.
   *
   * @param uuid The UUID of the drm scheme.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static DefaultDrmSessionManager<FrameworkMediaCrypto> newFrameworkInstance(
      UUID uuid, MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters,
      Handler eventHandler, EventListener eventListener) throws UnsupportedDrmException {
    return new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), callback,
        optionalKeyRequestParameters, eventHandler, eventListener);
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)}. May be null.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DefaultDrmSessionManager(UUID uuid, ExoMediaDrm<T> mediaDrm, MediaDrmCallback callback,
      HashMap<String, String> optionalKeyRequestParameters, Handler eventHandler,
      EventListener eventListener) {
    this.uuid = uuid;
    this.mediaDrm = mediaDrm;
    this.callback = callback;
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    mediaDrm.setOnEventListener(new MediaDrmEventListener());
    mode = MODE_PLAYBACK;
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

  /**
   * Sets the mode, which determines the role of sessions acquired from the instance. This must be
   * called before {@link #acquireSession(Looper, DrmInitData)} is called.
   *
   * <p>By default, the mode is {@link #MODE_PLAYBACK} and a streaming license is requested when
   * required.
   *
   * <p>{@code mode} must be one of these:
   * <ul>
   * <li>{@link #MODE_PLAYBACK}: If {@code offlineLicenseKeySetId} is null, a streaming license is
   *     requested otherwise the offline license is restored.
   * <li>{@link #MODE_QUERY}: {@code offlineLicenseKeySetId} can not be null. The offline license
   *     is restored.
   * <li>{@link #MODE_DOWNLOAD}: If {@code offlineLicenseKeySetId} is null, an offline license is
   *     requested otherwise the offline license is renewed.
   * <li>{@link #MODE_RELEASE}: {@code offlineLicenseKeySetId} can not be null. The offline license
   *     is released.
   * </ul>
   *
   * @param mode The mode to be set.
   * @param offlineLicenseKeySetId The key set id of the license to be used with the given mode.
   */
  public void setMode(@Mode int mode, byte[] offlineLicenseKeySetId) {
    Assertions.checkState(openCount == 0);
    if (mode == MODE_QUERY || mode == MODE_RELEASE) {
      Assertions.checkNotNull(offlineLicenseKeySetId);
    }
    this.mode = mode;
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
  }

  // DrmSessionManager implementation.

  @Override
  public boolean canAcquireSession(@NonNull DrmInitData drmInitData) {
    SchemeData schemeData = drmInitData.get(uuid);
    if (schemeData == null) {
      // No data for this manager's scheme.
      return false;
    }
    String schemeType = schemeData.type;
    if (schemeType == null || C.CENC_TYPE_cenc.equals(schemeType)) {
      // If there is no scheme information, assume patternless AES-CTR.
      return true;
    } else if (C.CENC_TYPE_cbc1.equals(schemeType) || C.CENC_TYPE_cbcs.equals(schemeType)
        || C.CENC_TYPE_cens.equals(schemeType)) {
      // AES-CBC and pattern encryption are supported on API 24 onwards.
      return Util.SDK_INT >= 24;
    }
    // Unknown schemes, assume one of them is supported.
    return true;
  }

  @Override
  public DrmSession<T> acquireSession(Looper playbackLooper, DrmInitData drmInitData) {
    Assertions.checkState(this.playbackLooper == null || this.playbackLooper == playbackLooper);
    if (++openCount != 1) {
      return this;
    }

    if (this.playbackLooper == null) {
      this.playbackLooper = playbackLooper;
      mediaDrmHandler = new MediaDrmHandler(playbackLooper);
      postResponseHandler = new PostResponseHandler(playbackLooper);
    }

    requestHandlerThread = new HandlerThread("DrmRequestHandler");
    requestHandlerThread.start();
    postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());

    if (offlineLicenseKeySetId == null) {
      SchemeData schemeData = drmInitData.get(uuid);
      if (schemeData == null) {
        onError(new IllegalStateException("Media does not support uuid: " + uuid));
        return this;
      }
      schemeInitData = schemeData.data;
      schemeMimeType = schemeData.mimeType;
      if (Util.SDK_INT < 21) {
        // Prior to L the Widevine CDM required data to be extracted from the PSSH atom.
        byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(schemeInitData, C.WIDEVINE_UUID);
        if (psshData == null) {
          // Extraction failed. schemeData isn't a Widevine PSSH atom, so leave it unchanged.
        } else {
          schemeInitData = psshData;
        }
      }
      if (Util.SDK_INT < 26 && C.CLEARKEY_UUID.equals(uuid)
          && (MimeTypes.VIDEO_MP4.equals(schemeMimeType)
          || MimeTypes.AUDIO_MP4.equals(schemeMimeType))) {
        // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
        schemeMimeType = CENC_SCHEME_MIME_TYPE;
      }
    }
    state = STATE_OPENING;
    openInternal(true);
    return this;
  }

  @Override
  public void releaseSession(DrmSession<T> session) {
    if (--openCount != 0) {
      return;
    }
    state = STATE_RELEASED;
    provisioningInProgress = false;
    mediaDrmHandler.removeCallbacksAndMessages(null);
    postResponseHandler.removeCallbacksAndMessages(null);
    postRequestHandler.removeCallbacksAndMessages(null);
    postRequestHandler = null;
    requestHandlerThread.quit();
    requestHandlerThread = null;
    schemeInitData = null;
    schemeMimeType = null;
    mediaCrypto = null;
    lastException = null;
    if (sessionId != null) {
      mediaDrm.closeSession(sessionId);
      sessionId = null;
    }
  }

  // DrmSession implementation.

  @Override
  @DrmSession.State
  public final int getState() {
    return state;
  }

  @Override
  public final DrmSessionException getError() {
    return state == STATE_ERROR ? lastException : null;
  }

  @Override
  public final T getMediaCrypto() {
    return mediaCrypto;
  }

  @Override
  public Map<String, String> queryKeyStatus() {
    return sessionId == null ? null : mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  public byte[] getOfflineLicenseKeySetId() {
    return offlineLicenseKeySetId;
  }

  // Internal methods.

  private void openInternal(boolean allowProvisioning) {
    try {
      sessionId = mediaDrm.openSession();
      mediaCrypto = mediaDrm.createMediaCrypto(uuid, sessionId);
      state = STATE_OPENED;
      doLicense();
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
        doLicense();
      }
    } catch (DeniedByServerException e) {
      onError(e);
    }
  }

  private void doLicense() {
    switch (mode) {
      case MODE_PLAYBACK:
      case MODE_QUERY:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(sessionId, MediaDrm.KEY_TYPE_STREAMING);
        } else {
          if (restoreKeys()) {
            long licenseDurationRemainingSec = getLicenseDurationRemainingSec();
            if (mode == MODE_PLAYBACK
                && licenseDurationRemainingSec <= MAX_LICENSE_DURATION_TO_RENEW) {
              Log.d(TAG, "Offline license has expired or will expire soon. "
                  + "Remaining seconds: " + licenseDurationRemainingSec);
              postKeyRequest(sessionId, MediaDrm.KEY_TYPE_OFFLINE);
            } else if (licenseDurationRemainingSec <= 0) {
              onError(new KeysExpiredException());
            } else {
              state = STATE_OPENED_WITH_KEYS;
              if (eventHandler != null && eventListener != null) {
                eventHandler.post(new Runnable() {
                  @Override
                  public void run() {
                    eventListener.onDrmKeysRestored();
                  }
                });
              }
            }
          }
        }
        break;
      case MODE_DOWNLOAD:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(sessionId, MediaDrm.KEY_TYPE_OFFLINE);
        } else {
          // Renew
          if (restoreKeys()) {
            postKeyRequest(sessionId, MediaDrm.KEY_TYPE_OFFLINE);
          }
        }
        break;
      case MODE_RELEASE:
        // It's not necessary to restore the key (and open a session to do that) before releasing it
        // but this serves as a good sanity/fast-failure check.
        if (restoreKeys()) {
          postKeyRequest(offlineLicenseKeySetId, MediaDrm.KEY_TYPE_RELEASE);
        }
        break;
    }
  }

  private boolean restoreKeys() {
    try {
      mediaDrm.restoreKeys(sessionId, offlineLicenseKeySetId);
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Error trying to restore Widevine keys.", e);
      onError(e);
    }
    return false;
  }

  private long getLicenseDurationRemainingSec() {
    if (!C.WIDEVINE_UUID.equals(uuid)) {
      return Long.MAX_VALUE;
    }
    Pair<Long, Long> pair = WidevineUtil.getLicenseDurationRemainingSec(this);
    return Math.min(pair.first, pair.second);
  }

  private void postKeyRequest(byte[] scope, int keyType) {
    try {
      KeyRequest keyRequest = mediaDrm.getKeyRequest(scope, schemeInitData, schemeMimeType, keyType,
          optionalKeyRequestParameters);
      postRequestHandler.obtainMessage(MSG_KEYS, keyRequest).sendToTarget();
    } catch (Exception e) {
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
      if (mode == MODE_RELEASE) {
        mediaDrm.provideKeyResponse(offlineLicenseKeySetId, (byte[]) response);
        if (eventHandler != null && eventListener != null) {
          eventHandler.post(new Runnable() {
            @Override
            public void run() {
              eventListener.onDrmKeysRemoved();
            }
          });
        }
      } else {
        byte[] keySetId = mediaDrm.provideKeyResponse(sessionId, (byte[]) response);
        if ((mode == MODE_DOWNLOAD || (mode == MODE_PLAYBACK && offlineLicenseKeySetId != null))
            && keySetId != null && keySetId.length != 0) {
          offlineLicenseKeySetId = keySetId;
        }
        state = STATE_OPENED_WITH_KEYS;
        if (eventHandler != null && eventListener != null) {
          eventHandler.post(new Runnable() {
            @Override
            public void run() {
              eventListener.onDrmKeysLoaded();
            }
          });
        }
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
    lastException = new DrmSessionException(e);
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
          doLicense();
          break;
        case MediaDrm.EVENT_KEY_EXPIRED:
          // When an already expired key is loaded MediaDrm sends this event immediately. Ignore
          // this event if the state isn't STATE_OPENED_WITH_KEYS yet which means we're still
          // waiting for key response.
          if (state == STATE_OPENED_WITH_KEYS) {
            state = STATE_OPENED;
            onError(new KeysExpiredException());
          }
          break;
        case MediaDrm.EVENT_PROVISION_REQUIRED:
          state = STATE_OPENED;
          postProvisionRequest();
          break;
      }
    }

  }

  private class MediaDrmEventListener implements OnEventListener<T> {

    @Override
    public void onEvent(ExoMediaDrm<? extends T> md, byte[] sessionId, int event, int extra,
        byte[] data) {
      if (mode == MODE_PLAYBACK) {
        mediaDrmHandler.sendEmptyMessage(event);
      }
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
          break;
        case MSG_KEYS:
          onKeyResponse(msg.obj);
          break;
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
