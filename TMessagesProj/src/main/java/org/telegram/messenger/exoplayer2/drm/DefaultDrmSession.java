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
import android.media.NotProvisionedException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.drm.DefaultDrmSessionEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.DefaultKeyRequest;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link DrmSession} that supports playbacks using {@link ExoMediaDrm}.
 */
@TargetApi(18)
/* package */ class DefaultDrmSession<T extends ExoMediaCrypto> implements DrmSession<T> {

  /**
   * Manages provisioning requests.
   */
  public interface ProvisioningManager<T extends ExoMediaCrypto> {

    /**
     * Called when a session requires provisioning. The manager <em>may</em> call
     * {@link #provision()} to have this session perform the provisioning operation. The manager
     * <em>will</em> call {@link DefaultDrmSession#onProvisionCompleted()} when provisioning has
     * completed, or {@link DefaultDrmSession#onProvisionError} if provisioning fails.
     *
     * @param session The session.
     */
    void provisionRequired(DefaultDrmSession<T> session);

    /**
     * Called by a session when it fails to perform a provisioning operation.
     *
     * @param error The error that occurred.
     */
    void onProvisionError(Exception error);

    /**
     * Called by a session when it successfully completes a provisioning operation.
     */
    void onProvisionCompleted();

  }

  private static final String TAG = "DefaultDrmSession";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;
  private static final int MAX_LICENSE_DURATION_TO_RENEW = 60;

  private final ExoMediaDrm<T> mediaDrm;
  private final ProvisioningManager<T> provisioningManager;
  private final byte[] initData;
  private final String mimeType;
  private final @DefaultDrmSessionManager.Mode int mode;
  private final HashMap<String, String> optionalKeyRequestParameters;
  private final EventDispatcher eventDispatcher;
  private final int initialDrmRequestRetryCount;

  /* package */ final MediaDrmCallback callback;
  /* package */ final UUID uuid;
  /* package */ final PostResponseHandler postResponseHandler;

  private @DrmSession.State int state;
  private int openCount;
  private HandlerThread requestHandlerThread;
  private PostRequestHandler postRequestHandler;
  private T mediaCrypto;
  private DrmSessionException lastException;
  private byte[] sessionId;
  private byte[] offlineLicenseKeySetId;

  /**
   * Instantiates a new DRM session.
   *
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm The media DRM.
   * @param provisioningManager The manager for provisioning.
   * @param initData The DRM init data.
   * @param mode The DRM mode.
   * @param offlineLicenseKeySetId The offlineLicense KeySetId.
   * @param optionalKeyRequestParameters The optional key request parameters.
   * @param callback The media DRM callback.
   * @param playbackLooper The playback looper.
   * @param eventDispatcher The dispatcher for DRM session manager events.
   * @param initialDrmRequestRetryCount The number of times to retry for initial provisioning and
   *     key request before reporting error.
   */
  public DefaultDrmSession(
      UUID uuid,
      ExoMediaDrm<T> mediaDrm,
      ProvisioningManager<T> provisioningManager,
      byte[] initData,
      String mimeType,
      @DefaultDrmSessionManager.Mode int mode,
      byte[] offlineLicenseKeySetId,
      HashMap<String, String> optionalKeyRequestParameters,
      MediaDrmCallback callback,
      Looper playbackLooper,
      EventDispatcher eventDispatcher,
      int initialDrmRequestRetryCount) {
    this.uuid = uuid;
    this.provisioningManager = provisioningManager;
    this.mediaDrm = mediaDrm;
    this.mode = mode;
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.callback = callback;
    this.initialDrmRequestRetryCount = initialDrmRequestRetryCount;
    this.eventDispatcher = eventDispatcher;
    state = STATE_OPENING;

    postResponseHandler = new PostResponseHandler(playbackLooper);
    requestHandlerThread = new HandlerThread("DrmRequestHandler");
    requestHandlerThread.start();
    postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());

    if (offlineLicenseKeySetId == null) {
      this.initData = initData;
      this.mimeType = mimeType;
    } else {
      this.initData = null;
      this.mimeType = null;
    }
  }

  // Life cycle.

  public void acquire() {
    if (++openCount == 1) {
      if (state == STATE_ERROR) {
        return;
      }
      if (openInternal(true)) {
        doLicense(true);
      }
    }
  }

  /**
   * @return True if the session is closed and cleaned up, false otherwise.
   */
  public boolean release() {
    if (--openCount == 0) {
      state = STATE_RELEASED;
      postResponseHandler.removeCallbacksAndMessages(null);
      postRequestHandler.removeCallbacksAndMessages(null);
      postRequestHandler = null;
      requestHandlerThread.quit();
      requestHandlerThread = null;
      mediaCrypto = null;
      lastException = null;
      if (sessionId != null) {
        mediaDrm.closeSession(sessionId);
        sessionId = null;
      }
      return true;
    }
    return false;
  }

  public boolean hasInitData(byte[] initData) {
    return Arrays.equals(this.initData, initData);
  }

  public boolean hasSessionId(byte[] sessionId) {
    return Arrays.equals(this.sessionId, sessionId);
  }

  // Provisioning implementation.

  public void provision() {
    ProvisionRequest request = mediaDrm.getProvisionRequest();
    postRequestHandler.obtainMessage(MSG_PROVISION, request, true).sendToTarget();
  }

  public void onProvisionCompleted() {
    if (openInternal(false)) {
      doLicense(true);
    }
  }

  public void onProvisionError(Exception error) {
    onError(error);
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

  /**
   *  Try to open a session, do provisioning if necessary.
   *  @param allowProvisioning if provisioning is allowed, set this to false when calling from
   *      processing provision response.
   *  @return true on success, false otherwise.
   */
  private boolean openInternal(boolean allowProvisioning) {
    if (isOpen()) {
      // Already opened
      return true;
    }

    try {
      sessionId = mediaDrm.openSession();
      mediaCrypto = mediaDrm.createMediaCrypto(sessionId);
      state = STATE_OPENED;
      return true;
    } catch (NotProvisionedException e) {
      if (allowProvisioning) {
        provisioningManager.provisionRequired(this);
      } else {
        onError(e);
      }
    } catch (Exception e) {
      onError(e);
    }

    return false;
  }

  private void onProvisionResponse(Object response) {
    if (state != STATE_OPENING && !isOpen()) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      provisioningManager.onProvisionError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
    } catch (Exception e) {
      provisioningManager.onProvisionError(e);
      return;
    }

    provisioningManager.onProvisionCompleted();
  }

  private void doLicense(boolean allowRetry) {
    switch (mode) {
      case DefaultDrmSessionManager.MODE_PLAYBACK:
      case DefaultDrmSessionManager.MODE_QUERY:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(ExoMediaDrm.KEY_TYPE_STREAMING, allowRetry);
        } else if (state == STATE_OPENED_WITH_KEYS || restoreKeys()) {
          long licenseDurationRemainingSec = getLicenseDurationRemainingSec();
          if (mode == DefaultDrmSessionManager.MODE_PLAYBACK
              && licenseDurationRemainingSec <= MAX_LICENSE_DURATION_TO_RENEW) {
            Log.d(TAG, "Offline license has expired or will expire soon. "
                + "Remaining seconds: " + licenseDurationRemainingSec);
            postKeyRequest(ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
          } else if (licenseDurationRemainingSec <= 0) {
            onError(new KeysExpiredException());
          } else {
            state = STATE_OPENED_WITH_KEYS;
            eventDispatcher.drmKeysRestored();
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_DOWNLOAD:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
        } else {
          // Renew
          if (restoreKeys()) {
            postKeyRequest(ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_RELEASE:
        // It's not necessary to restore the key (and open a session to do that) before releasing it
        // but this serves as a good sanity/fast-failure check.
        if (restoreKeys()) {
          postKeyRequest(ExoMediaDrm.KEY_TYPE_RELEASE, allowRetry);
        }
        break;
      default:
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

  private void postKeyRequest(int type, boolean allowRetry) {
    byte[] scope = type == ExoMediaDrm.KEY_TYPE_RELEASE ? offlineLicenseKeySetId : sessionId;
    try {
      KeyRequest request = mediaDrm.getKeyRequest(scope, initData, mimeType, type,
          optionalKeyRequestParameters);
      if (C.CLEARKEY_UUID.equals(uuid)) {
        request = new DefaultKeyRequest(ClearKeyUtil.adjustRequestData(request.getData()),
            request.getDefaultUrl());
      }
      postRequestHandler.obtainMessage(MSG_KEYS, request, allowRetry).sendToTarget();
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeyResponse(Object response) {
    if (!isOpen()) {
      // This event is stale.
      return;
    }

    if (response instanceof Exception) {
      onKeysError((Exception) response);
      return;
    }

    try {
      byte[] responseData = (byte[]) response;
      if (C.CLEARKEY_UUID.equals(uuid)) {
        responseData = ClearKeyUtil.adjustResponseData(responseData);
      }
      if (mode == DefaultDrmSessionManager.MODE_RELEASE) {
        mediaDrm.provideKeyResponse(offlineLicenseKeySetId, responseData);
        eventDispatcher.drmKeysRemoved();
      } else {
        byte[] keySetId = mediaDrm.provideKeyResponse(sessionId, responseData);
        if ((mode == DefaultDrmSessionManager.MODE_DOWNLOAD
            || (mode == DefaultDrmSessionManager.MODE_PLAYBACK && offlineLicenseKeySetId != null))
            && keySetId != null && keySetId.length != 0) {
          offlineLicenseKeySetId = keySetId;
        }
        state = STATE_OPENED_WITH_KEYS;
        eventDispatcher.drmKeysLoaded();
      }
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeysExpired() {
    if (state == STATE_OPENED_WITH_KEYS) {
      state = STATE_OPENED;
      onError(new KeysExpiredException());
    }
  }

  private void onKeysError(Exception e) {
    if (e instanceof NotProvisionedException) {
      provisioningManager.provisionRequired(this);
    } else {
      onError(e);
    }
  }

  private void onError(final Exception e) {
    lastException = new DrmSessionException(e);
    eventDispatcher.drmSessionManagerError(e);
    if (state != STATE_OPENED_WITH_KEYS) {
      state = STATE_ERROR;
    }
  }

  private boolean isOpen() {
    return state == STATE_OPENED || state == STATE_OPENED_WITH_KEYS;
  }

  @SuppressWarnings("deprecation")
  public void onMediaDrmEvent(int what) {
    if (!isOpen()) {
      return;
    }
    switch (what) {
      case ExoMediaDrm.EVENT_KEY_REQUIRED:
        doLicense(false);
        break;
      case ExoMediaDrm.EVENT_KEY_EXPIRED:
        // When an already expired key is loaded MediaDrm sends this event immediately. Ignore
        // this event if the state isn't STATE_OPENED_WITH_KEYS yet which means we're still
        // waiting for key response.
        onKeysExpired();
        break;
      case ExoMediaDrm.EVENT_PROVISION_REQUIRED:
        state = STATE_OPENED;
        provisioningManager.provisionRequired(this);
        break;
      default:
        break;
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
        default:
          break;

      }
    }

  }

  @SuppressLint("HandlerLeak")
  private class PostRequestHandler extends Handler {

    public PostRequestHandler(Looper backgroundLooper) {
      super(backgroundLooper);
    }

    Message obtainMessage(int what, Object object, boolean allowRetry) {
      return obtainMessage(what, allowRetry ? 1 : 0 /* allow retry*/, 0 /* error count */,
          object);
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
        if (maybeRetryRequest(msg)) {
          return;
        }
        response = e;
      }
      postResponseHandler.obtainMessage(msg.what, response).sendToTarget();
    }

    private boolean maybeRetryRequest(Message originalMsg) {
      boolean allowRetry = originalMsg.arg1 == 1;
      if (!allowRetry) {
        return false;
      }
      int errorCount = originalMsg.arg2 + 1;
      if (errorCount > initialDrmRequestRetryCount) {
        return false;
      }
      Message retryMsg = Message.obtain(originalMsg);
      retryMsg.arg2 = errorCount;
      sendMessageDelayed(retryMsg, getRetryDelayMillis(errorCount));
      return true;
    }

    private long getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }

  }

}
