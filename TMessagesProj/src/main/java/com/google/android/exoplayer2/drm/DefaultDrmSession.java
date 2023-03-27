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
package com.google.android.exoplayer2.drm;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.media.NotProvisionedException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Consumer;
import com.google.android.exoplayer2.util.CopyOnWriteMultiset;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A {@link DrmSession} that supports playbacks using {@link ExoMediaDrm}. */
@RequiresApi(18)
/* package */ class DefaultDrmSession implements DrmSession {

  /** Thrown when an unexpected exception or error is thrown during provisioning or key requests. */
  public static final class UnexpectedDrmSessionException extends IOException {

    public UnexpectedDrmSessionException(@Nullable Throwable cause) {
      super(cause);
    }
  }

  /** Manages provisioning requests. */
  public interface ProvisioningManager {

    /**
     * Called when a session requires provisioning. The manager <em>may</em> call {@link
     * #provision()} to have this session perform the provisioning operation. The manager
     * <em>will</em> call {@link DefaultDrmSession#onProvisionCompleted()} when provisioning has
     * completed, or {@link DefaultDrmSession#onProvisionError} if provisioning fails.
     *
     * @param session The session.
     */
    void provisionRequired(DefaultDrmSession session);

    /**
     * Called by a session when it fails to perform a provisioning operation.
     *
     * @param error The error that occurred.
     * @param thrownByExoMediaDrm Whether the error originated in an {@link ExoMediaDrm} operation.
     *     False when the error originated in the provisioning request.
     */
    void onProvisionError(Exception error, boolean thrownByExoMediaDrm);

    /** Called by a session when it successfully completes a provisioning operation. */
    void onProvisionCompleted();
  }

  /** Callback to be notified when the reference count of this session changes. */
  public interface ReferenceCountListener {

    /**
     * Called when the internal reference count of this session is incremented.
     *
     * @param session This session.
     * @param newReferenceCount The reference count after being incremented.
     */
    void onReferenceCountIncremented(DefaultDrmSession session, int newReferenceCount);

    /**
     * Called when the internal reference count of this session is decremented.
     *
     * <p>{@code newReferenceCount == 0} indicates this session is in {@link #STATE_RELEASED}.
     *
     * @param session This session.
     * @param newReferenceCount The reference count after being decremented.
     */
    void onReferenceCountDecremented(DefaultDrmSession session, int newReferenceCount);
  }

  private static final String TAG = "DefaultDrmSession";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;
  private static final int MAX_LICENSE_DURATION_TO_RENEW_SECONDS = 60;

  /** The DRM scheme datas, or null if this session uses offline keys. */
  @Nullable public final List<SchemeData> schemeDatas;

  private final ExoMediaDrm mediaDrm;
  private final ProvisioningManager provisioningManager;
  private final ReferenceCountListener referenceCountListener;
  private final @DefaultDrmSessionManager.Mode int mode;
  private final boolean playClearSamplesWithoutKeys;
  private final boolean isPlaceholderSession;
  private final HashMap<String, String> keyRequestParameters;
  private final CopyOnWriteMultiset<DrmSessionEventListener.EventDispatcher> eventDispatchers;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final PlayerId playerId;

  /* package */ final MediaDrmCallback callback;
  /* package */ final UUID uuid;
  /* package */ final ResponseHandler responseHandler;

  private @DrmSession.State int state;
  private int referenceCount;
  @Nullable private HandlerThread requestHandlerThread;
  @Nullable private RequestHandler requestHandler;
  @Nullable private CryptoConfig cryptoConfig;
  @Nullable private DrmSessionException lastException;
  @Nullable private byte[] sessionId;
  private byte @MonotonicNonNull [] offlineLicenseKeySetId;

  @Nullable private KeyRequest currentKeyRequest;
  @Nullable private ProvisionRequest currentProvisionRequest;

  /**
   * Instantiates a new DRM session.
   *
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm The media DRM.
   * @param provisioningManager The manager for provisioning.
   * @param referenceCountListener The {@link ReferenceCountListener}.
   * @param schemeDatas DRM scheme datas for this session, or null if an {@code
   *     offlineLicenseKeySetId} is provided or if {@code isPlaceholderSession} is true.
   * @param mode The DRM mode. Ignored if {@code isPlaceholderSession} is true.
   * @param isPlaceholderSession Whether this session is not expected to acquire any keys.
   * @param offlineLicenseKeySetId The offline license key set identifier, or null when not using
   *     offline keys.
   * @param keyRequestParameters Key request parameters.
   * @param callback The media DRM callback.
   * @param playbackLooper The playback looper.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy} for key and provisioning
   *     requests.
   */
  public DefaultDrmSession(
      UUID uuid,
      ExoMediaDrm mediaDrm,
      ProvisioningManager provisioningManager,
      ReferenceCountListener referenceCountListener,
      @Nullable List<SchemeData> schemeDatas,
      @DefaultDrmSessionManager.Mode int mode,
      boolean playClearSamplesWithoutKeys,
      boolean isPlaceholderSession,
      @Nullable byte[] offlineLicenseKeySetId,
      HashMap<String, String> keyRequestParameters,
      MediaDrmCallback callback,
      Looper playbackLooper,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      PlayerId playerId) {
    if (mode == DefaultDrmSessionManager.MODE_QUERY
        || mode == DefaultDrmSessionManager.MODE_RELEASE) {
      Assertions.checkNotNull(offlineLicenseKeySetId);
    }
    this.uuid = uuid;
    this.provisioningManager = provisioningManager;
    this.referenceCountListener = referenceCountListener;
    this.mediaDrm = mediaDrm;
    this.mode = mode;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    this.isPlaceholderSession = isPlaceholderSession;
    if (offlineLicenseKeySetId != null) {
      this.offlineLicenseKeySetId = offlineLicenseKeySetId;
      this.schemeDatas = null;
    } else {
      this.schemeDatas = Collections.unmodifiableList(Assertions.checkNotNull(schemeDatas));
    }
    this.keyRequestParameters = keyRequestParameters;
    this.callback = callback;
    this.eventDispatchers = new CopyOnWriteMultiset<>();
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.playerId = playerId;
    state = STATE_OPENING;
    responseHandler = new ResponseHandler(playbackLooper);
  }

  public boolean hasSessionId(byte[] sessionId) {
    return Arrays.equals(this.sessionId, sessionId);
  }

  public void onMediaDrmEvent(int what) {
    switch (what) {
      case ExoMediaDrm.EVENT_KEY_REQUIRED:
        onKeysRequired();
        break;
      default:
        break;
    }
  }

  // Provisioning implementation.

  public void provision() {
    currentProvisionRequest = mediaDrm.getProvisionRequest();
    Util.castNonNull(requestHandler)
        .post(
            MSG_PROVISION,
            Assertions.checkNotNull(currentProvisionRequest),
            /* allowRetry= */ true);
  }

  public void onProvisionCompleted() {
    if (openInternal()) {
      doLicense(true);
    }
  }

  public void onProvisionError(Exception error, boolean thrownByExoMediaDrm) {
    onError(
        error,
        thrownByExoMediaDrm
            ? DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM
            : DrmUtil.ERROR_SOURCE_PROVISIONING);
  }

  // DrmSession implementation.

  @Override
  public final @DrmSession.State int getState() {
    return state;
  }

  @Override
  public boolean playClearSamplesWithoutKeys() {
    return playClearSamplesWithoutKeys;
  }

  @Override
  @Nullable
  public final DrmSessionException getError() {
    return state == STATE_ERROR ? lastException : null;
  }

  @Override
  public final UUID getSchemeUuid() {
    return uuid;
  }

  @Override
  @Nullable
  public final CryptoConfig getCryptoConfig() {
    return cryptoConfig;
  }

  @Override
  @Nullable
  public Map<String, String> queryKeyStatus() {
    return sessionId == null ? null : mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  @Nullable
  public byte[] getOfflineLicenseKeySetId() {
    return offlineLicenseKeySetId;
  }

  @Override
  public boolean requiresSecureDecoder(String mimeType) {
    return mediaDrm.requiresSecureDecoder(checkStateNotNull(sessionId), mimeType);
  }

  @Override
  public void acquire(@Nullable DrmSessionEventListener.EventDispatcher eventDispatcher) {
    if (referenceCount < 0) {
      Log.e(TAG, "Session reference count less than zero: " + referenceCount);
      referenceCount = 0;
    }
    if (eventDispatcher != null) {
      eventDispatchers.add(eventDispatcher);
    }
    if (++referenceCount == 1) {
      checkState(state == STATE_OPENING);
      requestHandlerThread = new HandlerThread("ExoPlayer:DrmRequestHandler");
      requestHandlerThread.start();
      requestHandler = new RequestHandler(requestHandlerThread.getLooper());
      if (openInternal()) {
        doLicense(true);
      }
    } else if (eventDispatcher != null
        && isOpen()
        && eventDispatchers.count(eventDispatcher) == 1) {
      // If the session is already open and this is the first instance of eventDispatcher we've
      // seen, then send the acquire event only to the provided dispatcher.
      eventDispatcher.drmSessionAcquired(state);
    }
    referenceCountListener.onReferenceCountIncremented(this, referenceCount);
  }

  @Override
  public void release(@Nullable DrmSessionEventListener.EventDispatcher eventDispatcher) {
    if (referenceCount <= 0) {
      Log.e(TAG, "release() called on a session that's already fully released.");
      return;
    }
    if (--referenceCount == 0) {
      // Assigning null to various non-null variables for clean-up.
      state = STATE_RELEASED;
      Util.castNonNull(responseHandler).removeCallbacksAndMessages(null);
      Util.castNonNull(requestHandler).release();
      requestHandler = null;
      Util.castNonNull(requestHandlerThread).quit();
      requestHandlerThread = null;
      cryptoConfig = null;
      lastException = null;
      currentKeyRequest = null;
      currentProvisionRequest = null;
      if (sessionId != null) {
        mediaDrm.closeSession(sessionId);
        sessionId = null;
      }
    }
    if (eventDispatcher != null) {
      eventDispatchers.remove(eventDispatcher);
      if (eventDispatchers.count(eventDispatcher) == 0) {
        // Release events are only sent to the last-attached instance of each EventDispatcher.
        eventDispatcher.drmSessionReleased();
      }
    }
    referenceCountListener.onReferenceCountDecremented(this, referenceCount);
  }

  // Internal methods.

  /**
   * Try to open a session, do provisioning if necessary.
   *
   * @return true on success, false otherwise.
   */
  @EnsuresNonNullIf(result = true, expression = "sessionId")
  private boolean openInternal() {
    if (isOpen()) {
      // Already opened
      return true;
    }

    try {
      sessionId = mediaDrm.openSession();
      mediaDrm.setPlayerIdForSession(sessionId, playerId);
      cryptoConfig = mediaDrm.createCryptoConfig(sessionId);
      state = STATE_OPENED;
      // Capture state into a local so a consistent value is seen by the lambda.
      int localState = state;
      dispatchEvent(eventDispatcher -> eventDispatcher.drmSessionAcquired(localState));
      Assertions.checkNotNull(sessionId);
      return true;
    } catch (NotProvisionedException e) {
      provisioningManager.provisionRequired(this);
    } catch (Exception e) {
      onError(e, DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM);
    }

    return false;
  }

  private void onProvisionResponse(Object request, Object response) {
    if (request != currentProvisionRequest || (state != STATE_OPENING && !isOpen())) {
      // This event is stale.
      return;
    }
    currentProvisionRequest = null;

    if (response instanceof Exception) {
      provisioningManager.onProvisionError((Exception) response, /* thrownByExoMediaDrm= */ false);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
    } catch (Exception e) {
      provisioningManager.onProvisionError(e, /* thrownByExoMediaDrm= */ true);
      return;
    }

    provisioningManager.onProvisionCompleted();
  }

  @RequiresNonNull("sessionId")
  private void doLicense(boolean allowRetry) {
    if (isPlaceholderSession) {
      return;
    }
    byte[] sessionId = Util.castNonNull(this.sessionId);
    switch (mode) {
      case DefaultDrmSessionManager.MODE_PLAYBACK:
      case DefaultDrmSessionManager.MODE_QUERY:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_STREAMING, allowRetry);
        } else if (state == STATE_OPENED_WITH_KEYS || restoreKeys()) {
          long licenseDurationRemainingSec = getLicenseDurationRemainingSec();
          if (mode == DefaultDrmSessionManager.MODE_PLAYBACK
              && licenseDurationRemainingSec <= MAX_LICENSE_DURATION_TO_RENEW_SECONDS) {
            Log.d(
                TAG,
                "Offline license has expired or will expire soon. "
                    + "Remaining seconds: "
                    + licenseDurationRemainingSec);
            postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
          } else if (licenseDurationRemainingSec <= 0) {
            onError(new KeysExpiredException(), DrmUtil.ERROR_SOURCE_LICENSE_ACQUISITION);
          } else {
            state = STATE_OPENED_WITH_KEYS;
            dispatchEvent(DrmSessionEventListener.EventDispatcher::drmKeysRestored);
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_DOWNLOAD:
        if (offlineLicenseKeySetId == null || restoreKeys()) {
          postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
        }
        break;
      case DefaultDrmSessionManager.MODE_RELEASE:
        Assertions.checkNotNull(offlineLicenseKeySetId);
        Assertions.checkNotNull(this.sessionId);
        postKeyRequest(offlineLicenseKeySetId, ExoMediaDrm.KEY_TYPE_RELEASE, allowRetry);
        break;
      default:
        break;
    }
  }

  @RequiresNonNull({"sessionId", "offlineLicenseKeySetId"})
  private boolean restoreKeys() {
    try {
      mediaDrm.restoreKeys(sessionId, offlineLicenseKeySetId);
      return true;
    } catch (Exception e) {
      onError(e, DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM);
    }
    return false;
  }

  private long getLicenseDurationRemainingSec() {
    if (!C.WIDEVINE_UUID.equals(uuid)) {
      return Long.MAX_VALUE;
    }
    Pair<Long, Long> pair =
        Assertions.checkNotNull(WidevineUtil.getLicenseDurationRemainingSec(this));
    return min(pair.first, pair.second);
  }

  private void postKeyRequest(byte[] scope, int type, boolean allowRetry) {
    try {
      currentKeyRequest = mediaDrm.getKeyRequest(scope, schemeDatas, type, keyRequestParameters);
      Util.castNonNull(requestHandler)
          .post(MSG_KEYS, Assertions.checkNotNull(currentKeyRequest), allowRetry);
    } catch (Exception e) {
      onKeysError(e, /* thrownByExoMediaDrm= */ true);
    }
  }

  private void onKeyResponse(Object request, Object response) {
    if (request != currentKeyRequest || !isOpen()) {
      // This event is stale.
      return;
    }
    currentKeyRequest = null;

    if (response instanceof Exception) {
      onKeysError((Exception) response, /* thrownByExoMediaDrm= */ false);
      return;
    }

    try {
      byte[] responseData = (byte[]) response;
      if (mode == DefaultDrmSessionManager.MODE_RELEASE) {
        mediaDrm.provideKeyResponse(Util.castNonNull(offlineLicenseKeySetId), responseData);
        dispatchEvent(DrmSessionEventListener.EventDispatcher::drmKeysRemoved);
      } else {
        byte[] keySetId = mediaDrm.provideKeyResponse(sessionId, responseData);
        if ((mode == DefaultDrmSessionManager.MODE_DOWNLOAD
                || (mode == DefaultDrmSessionManager.MODE_PLAYBACK
                    && offlineLicenseKeySetId != null))
            && keySetId != null
            && keySetId.length != 0) {
          offlineLicenseKeySetId = keySetId;
        }
        state = STATE_OPENED_WITH_KEYS;
        dispatchEvent(DrmSessionEventListener.EventDispatcher::drmKeysLoaded);
      }
    } catch (Exception e) {
      onKeysError(e, /* thrownByExoMediaDrm= */ true);
    }
  }

  private void onKeysRequired() {
    if (mode == DefaultDrmSessionManager.MODE_PLAYBACK && state == STATE_OPENED_WITH_KEYS) {
      Util.castNonNull(sessionId);
      doLicense(/* allowRetry= */ false);
    }
  }

  private void onKeysError(Exception e, boolean thrownByExoMediaDrm) {
    if (e instanceof NotProvisionedException) {
      provisioningManager.provisionRequired(this);
    } else {
      onError(
          e,
          thrownByExoMediaDrm
              ? DrmUtil.ERROR_SOURCE_EXO_MEDIA_DRM
              : DrmUtil.ERROR_SOURCE_LICENSE_ACQUISITION);
    }
  }

  private void onError(Exception e, @DrmUtil.ErrorSource int errorSource) {
    lastException =
        new DrmSessionException(e, DrmUtil.getErrorCodeForMediaDrmException(e, errorSource));
    Log.e(TAG, "DRM session error", e);
    dispatchEvent(eventDispatcher -> eventDispatcher.drmSessionManagerError(e));
    if (state != STATE_OPENED_WITH_KEYS) {
      state = STATE_ERROR;
    }
  }

  @EnsuresNonNullIf(result = true, expression = "sessionId")
  @SuppressWarnings("nullness:contracts.conditional.postcondition")
  private boolean isOpen() {
    return state == STATE_OPENED || state == STATE_OPENED_WITH_KEYS;
  }

  private void dispatchEvent(Consumer<DrmSessionEventListener.EventDispatcher> event) {
    for (DrmSessionEventListener.EventDispatcher eventDispatcher : eventDispatchers.elementSet()) {
      event.accept(eventDispatcher);
    }
  }

  // Internal classes.

  @SuppressLint("HandlerLeak")
  private class ResponseHandler extends Handler {

    public ResponseHandler(Looper looper) {
      super(looper);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMessage(Message msg) {
      Pair<Object, Object> requestAndResponse = (Pair<Object, Object>) msg.obj;
      Object request = requestAndResponse.first;
      Object response = requestAndResponse.second;
      switch (msg.what) {
        case MSG_PROVISION:
          onProvisionResponse(request, response);
          break;
        case MSG_KEYS:
          onKeyResponse(request, response);
          break;
        default:
          break;
      }
    }
  }

  @SuppressLint("HandlerLeak")
  private class RequestHandler extends Handler {

    @GuardedBy("this")
    private boolean isReleased;

    public RequestHandler(Looper backgroundLooper) {
      super(backgroundLooper);
    }

    void post(int what, Object request, boolean allowRetry) {
      RequestTask requestTask =
          new RequestTask(
              LoadEventInfo.getNewId(),
              allowRetry,
              /* startTimeMs= */ SystemClock.elapsedRealtime(),
              request);
      obtainMessage(what, requestTask).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg) {
      RequestTask requestTask = (RequestTask) msg.obj;
      Object response;
      try {
        switch (msg.what) {
          case MSG_PROVISION:
            response =
                callback.executeProvisionRequest(uuid, (ProvisionRequest) requestTask.request);
            break;
          case MSG_KEYS:
            response = callback.executeKeyRequest(uuid, (KeyRequest) requestTask.request);
            break;
          default:
            throw new RuntimeException();
        }
      } catch (MediaDrmCallbackException e) {
        if (maybeRetryRequest(msg, e)) {
          return;
        }
        response = e;
      } catch (Exception e) {
        Log.w(TAG, "Key/provisioning request produced an unexpected exception. Not retrying.", e);
        response = e;
      }
      loadErrorHandlingPolicy.onLoadTaskConcluded(requestTask.taskId);
      synchronized (this) {
        if (!isReleased) {
          responseHandler
              .obtainMessage(msg.what, Pair.create(requestTask.request, response))
              .sendToTarget();
        }
      }
    }

    private boolean maybeRetryRequest(Message originalMsg, MediaDrmCallbackException exception) {
      RequestTask requestTask = (RequestTask) originalMsg.obj;
      if (!requestTask.allowRetry) {
        return false;
      }
      requestTask.errorCount++;
      if (requestTask.errorCount
          > loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_DRM)) {
        return false;
      }
      LoadEventInfo loadEventInfo =
          new LoadEventInfo(
              requestTask.taskId,
              exception.dataSpec,
              exception.uriAfterRedirects,
              exception.responseHeaders,
              SystemClock.elapsedRealtime(),
              /* loadDurationMs= */ SystemClock.elapsedRealtime() - requestTask.startTimeMs,
              exception.bytesLoaded);
      MediaLoadData mediaLoadData = new MediaLoadData(C.DATA_TYPE_DRM);
      IOException loadErrorCause =
          exception.getCause() instanceof IOException
              ? (IOException) exception.getCause()
              : new UnexpectedDrmSessionException(exception.getCause());
      long retryDelayMs =
          loadErrorHandlingPolicy.getRetryDelayMsFor(
              new LoadErrorInfo(
                  loadEventInfo, mediaLoadData, loadErrorCause, requestTask.errorCount));
      if (retryDelayMs == C.TIME_UNSET) {
        // The error is fatal.
        return false;
      }
      synchronized (this) {
        if (!isReleased) {
          sendMessageDelayed(Message.obtain(originalMsg), retryDelayMs);
          return true;
        }
      }
      return false;
    }

    public synchronized void release() {
      removeCallbacksAndMessages(/* token= */ null);
      isReleased = true;
    }
  }

  private static final class RequestTask {

    public final long taskId;
    public final boolean allowRetry;
    public final long startTimeMs;
    public final Object request;
    public int errorCount;

    public RequestTask(long taskId, boolean allowRetry, long startTimeMs, Object request) {
      this.taskId = taskId;
      this.allowRetry = allowRetry;
      this.startTimeMs = startTimeMs;
      this.request = request;
    }
  }
}
