package org.telegram.messenger.chromecast;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaError;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.CastSync;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.Objects;

public class ChromecastController implements SessionManagerListener<CastSession> {
    private final static String CAST_CONTROLLER = "CAST_CONTROLLER";
    private final static String CAST_SESSION_TAG = "CAST_SESSION";
    private final static String CAST_CLIENT_TAG = "CAST_CLIENT";
    private final static String CAST_STATE = "CAST_STATE";

    private final ChromecastControllerState state;
    private final SessionManager sessionManager;

    private ChromecastController() {
        CastContext castContext = CastContext.getSharedInstance(ApplicationLoader.applicationContext);
        castContext.addCastStateListener(i -> Log.d(CAST_STATE, "onCastStateChanged " + i));  // ???

        state = new ChromecastControllerState();

        sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(this, CastSession.class);

        tryInitClient(sessionManager.getCurrentCastSession());
    }

    public boolean isCasting() {
        return state.getClient() != null;
    }

    public void setCurrentMediaAndCastIfNeeded(ChromecastMediaVariations newMedia) {
        Log.d(CAST_CONTROLLER, "set current media");
        ChromecastMediaVariations currentMedia = state.getMedia();
        if (CastSync.isActive() && eq(currentMedia, newMedia)) {
//            CastSync.syncInterface();
        } else {
            state.setMedia(newMedia);
        }
    }

    public String setCover(File file) {
        return state.setCoverFile(file);
    }

    public boolean isPlaying(ChromecastMediaVariations media) {
        return CastSync.isActive() && eq(state.getMedia(), media);
    }

    public static boolean eq(ChromecastMediaVariations a, ChromecastMediaVariations b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.getVariationsCount() != b.getVariationsCount()) return false;
        for (int i = 0; i < a.getVariationsCount(); ++i) {
            if (!eq(a.getVariation(i), b.getVariation(i)))
                return false;
        }
        return true;
    }

    public static boolean eq(ChromecastMedia a, ChromecastMedia b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return (
            Objects.equals(a.mimeType, b.mimeType) &&
            Objects.equals(a.mediaMetadata, b.mediaMetadata) &&
            Objects.equals(a.internalUri, b.internalUri) &&
            Objects.equals(a.externalPath, b.externalPath) &&
            a.width == b.width &&
            a.height == b.height
        );
    }

    private void tryInitClient(CastSession castSession) {
        if (castSession == null) {
            return;
        }

        final RemoteMediaClient client = castSession.getRemoteMediaClient();
        final String sessionId = castSession.getSessionId();

        if (TextUtils.isEmpty(sessionId) || client == null) {
            return;
        }

        final RemoteMediaClientHandler oldClient = state.getClient();
        if (oldClient != null && TextUtils.equals(oldClient.session.getSessionId(), sessionId)) {
            return;
        }

        state.setClient(new RemoteMediaClientHandler(castSession, sessionManager, client));

        final CastDevice device = castSession.getCastDevice();
        final String deviceName = device != null ? device.getFriendlyName() : null;

        PhotoViewer.getInstance().showChromecastBulletin(ChromecastFileServer.getHost(), deviceName);
    }


    /* * */

    private static volatile ChromecastController Instance = null;

    public static ChromecastController getInstance() {
        ChromecastController localInstance = Instance;
        if (localInstance == null) {
            synchronized (ChromecastController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ChromecastController();
                }
            }
        }
        return localInstance;
    }



    /* * */

    static class RemoteMediaClientHandler extends RemoteMediaClient.Callback {
        public final RemoteMediaClient client;
        public final SessionManager manager;
        public final CastSession session;

        public RemoteMediaClientHandler(CastSession session, SessionManager manager, RemoteMediaClient client) {
            this.session = session;
            this.manager = manager;
            this.client = client;
        }

        private ChromecastMediaVariations media;
        private int lastMediaErrorCode;
        private int lastIdleReason;
        private int index;
        private int attempt;

        public void load (ChromecastMediaVariations media) {
            this.media = media;
            this.index = 0;
            this.attempt = 0;
            this.loadImpl();
        }

        public void register() {
            client.registerCallback(this);
        }

        public void unregister() {
            client.unregisterCallback(this);
        }

        public void close () {
            manager.endCurrentSession(true);
        }

        private void loadNext (boolean inc) {
            if (inc) {
                index += 1;
            } else {
                attempt += 1;
                if (attempt > 3) {
                    attempt = 0;
                    index += 1;
                }
            }
            Log.e(CAST_CLIENT_TAG, "next attempt " + lastMediaErrorCode + " " + index + " " + attempt);
            loadImpl();
        }

        private void loadImpl () {
            lastMediaErrorCode = -1;

            if (media == null) {
                media = null;
                return;
            }

            final String host = ChromecastFileServer.getHost();
            final ChromecastMedia variation = index < media.getVariationsCount() ?
                media.getVariation(index) : ChromecastFileServer.ASSET_FALLBACK_FILE;

            this.client.load(variation.buildMediaInfo(host, "?index=" + index + "&attempt=" + attempt),
                    new MediaLoadOptions.Builder().setAutoplay(true).build());
        }

        @Override
        public void onAdBreakStatusUpdated() {
            Log.d(CAST_CLIENT_TAG, "onAdBreakStatusUpdated " + session.getSessionId());
        }

        @Override
        public void onMediaError(@NonNull MediaError mediaError) {
            Log.d(CAST_CLIENT_TAG, "onMediaError " + session.getSessionId() + " " + mediaError.getDetailedErrorCode() + " " + mediaError.getRequestId());

            final Integer errorCode = mediaError.getDetailedErrorCode();
            lastMediaErrorCode = errorCode != null ? errorCode : -1;
        }

        @Override
        public void onMetadataUpdated() {
            Log.d(CAST_CLIENT_TAG, "onMetadataUpdated " + session.getSessionId());
        }

        @Override
        public void onPreloadStatusUpdated() {
            Log.d(CAST_CLIENT_TAG, "onPreloadStatusUpdated " + session.getSessionId());
        }

        @Override
        public void onQueueStatusUpdated() {
            Log.d(CAST_CLIENT_TAG, "onQueueStatusUpdated " + session.getSessionId());
        }

        @Override
        public void onSendingRemoteMediaRequest() {
            Log.d(CAST_CLIENT_TAG, "onSendingRemoteMediaRequest " + session.getSessionId());
        }

        @Override
        public void onStatusUpdated() {
            Log.d(CAST_CLIENT_TAG, "onStatusUpdated " + session.getSessionId());

            final int idleReason = client.getIdleReason();
            if (idleReason != lastIdleReason) {
                Log.d(CAST_CLIENT_TAG, "idleReason " + idleReason);
                lastIdleReason = idleReason;
                if (idleReason == MediaStatus.IDLE_REASON_CANCELED) {
                    close();
                } else if (idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    if (lastMediaErrorCode == MediaError.DetailedErrorCode.MEDIA_SRC_NOT_SUPPORTED) {
                        loadNext(true);
                    } else if (lastMediaErrorCode == MediaError.DetailedErrorCode.MEDIA_DECODE) {
                        loadNext(false);
                    } else {
                        // close();
                    }
                }
            }
        }
    }



    /* Session Listener */

    @Override
    public void onSessionStarting(@NonNull CastSession castSession) {
        Log.d(CAST_SESSION_TAG, "onSessionStarting " + castSession.getSessionId());
        tryInitClient(castSession);
    }

    @Override
    public void onSessionStarted(@NonNull CastSession castSession, @NonNull String s) {
        Log.d(CAST_SESSION_TAG, "onSessionStarted " + castSession.getSessionId() + " " + s);
        tryInitClient(castSession);
    }

    @Override
    public void onSessionEnded(@NonNull CastSession castSession, int i) {
        Log.d(CAST_SESSION_TAG, "onSessionEnded " + castSession.getSessionId() + " " + i);
        state.setClient(null);
    }

    @Override
    public void onSessionEnding(@NonNull CastSession castSession) {
        Log.d(CAST_SESSION_TAG, "onSessionEnding " + castSession.getSessionId());
    }

    @Override
    public void onSessionResumeFailed(@NonNull CastSession castSession, int i) {
        Log.d(CAST_SESSION_TAG, "onSessionResumeFailed " + castSession.getSessionId() + " " + i);
    }

    @Override
    public void onSessionResumed(@NonNull CastSession castSession, boolean b) {
        Log.d(CAST_SESSION_TAG, "onSessionResumed " + castSession.getSessionId() + " " + b);
    }

    @Override
    public void onSessionResuming(@NonNull CastSession castSession, @NonNull String s) {
        Log.d(CAST_SESSION_TAG, "onSessionResuming " + castSession.getSessionId() + " " + s);
    }

    @Override
    public void onSessionStartFailed(@NonNull CastSession castSession, int i) {
        Log.d(CAST_SESSION_TAG, "onSessionStartFailed " + castSession.getSessionId() + " " + i);
    }

    @Override
    public void onSessionSuspended(@NonNull CastSession castSession, int i) {
        Log.d(CAST_SESSION_TAG, "onSessionStartSuspended " + castSession.getSessionId() + " " + i);
    }

}
