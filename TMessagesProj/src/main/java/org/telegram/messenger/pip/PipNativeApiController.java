package org.telegram.messenger.pip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.telegram.messenger.AndroidUtilities;

import java.util.HashMap;

import javax.annotation.Nullable;

public class PipNativeApiController {
    public static final int PIP_DENIED_PIP = -2;
    public static final int PIP_DENIED_OVERLAY = -1;
    public static final int PIP_GRANTED_PIP = 1;
    public static final int PIP_GRANTED_OVERLAY = 2;

    private static final HashMap<String, PipSource> sources = new HashMap<>();

    static void register(PipSource source) {
        sources.put(source.tag, source);
        // Log.i(PipSource.TAG, "[LIFECYCLE] init " + source.tag + " " + sources.size());
        onUpdateSourcesMap();

    }

    static void unregister(PipSource source) {
        if (sources.remove(source.tag) != null) {
            // Log.i(PipSource.TAG, "[LIFECYCLE] destroy " + source.tag + " " + sources.size());
            onUpdateSourcesMap();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static @Nullable PipSource maxPrioritySource;

    private static MediaSessionCompat mediaSession;
    static MediaSessionConnector mediaSessionConnector;

    static boolean isMaxPrioritySource(String tag) {
        return TextUtils.equals(tag, getMaxPrioritySourceTag());
    }

    private static @Nullable String getMaxPrioritySourceTag() {
        PipSource source = maxPrioritySource;
        return source != null ? source.tag : null;
    }

    static void onUpdateSourcesMap() {
        final PipSource oldSource = maxPrioritySource;
        final String oldTag = oldSource != null ? oldSource.tag : null;
        maxPrioritySource = null;
        for (PipSource source : sources.values()) {
            if ((!source.isEnabled() || (source.player != null && (source.ratio.x == 0 || source.ratio.y == 0))) && !source.isAttachedToPictureInPicture()) {
                continue;
            }

            if (maxPrioritySource == null) {
                maxPrioritySource = source;
                continue;
            }

            if (source.priority >= maxPrioritySource.priority) {
                maxPrioritySource = source;
            }
        }

        if (!TextUtils.equals(oldTag, getMaxPrioritySourceTag())) {
            onMaxPrioritySourceChanged(oldSource, maxPrioritySource);
        }
    }

    private static void onMaxPrioritySourceChanged(PipSource oldSource, PipSource newSource) {
        final boolean oldMediaSession = oldSource != null && oldSource.needMediaSession;
        final boolean newMediaSession = newSource != null && newSource.needMediaSession;
        if (oldMediaSession != newMediaSession) {
            if (mediaSessionConnector != null) {
                mediaSessionConnector.setPlayer(null);
                mediaSessionConnector = null;
            }
            if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
                // Log.i(PipSource.TAG, "[MEDIA] stop media session");
            }

            if (newSource != null) {
                mediaSession = new MediaSessionCompat(newSource.activity, "pip-media-session");
                mediaSession.setActive(true);
                mediaSessionConnector = new MediaSessionConnector(mediaSession);
                // Log.i(PipSource.TAG, "[MEDIA] start media session");
            }
        }

        if (oldSource != null) {
            oldSource.detachFromPictureInPicture();
        }

        if (newSource != null) {
            newSource.applyPictureInPictureParams();
            if (mediaSessionConnector != null) {
                mediaSessionConnector.setPlayer(newSource.player);
            }
            if (AndroidUtilities.isInPictureInPictureMode(newSource.activity)) {
                newSource.attachToPictureInPicture();
            }
        } else if (oldSource != null) {
            AndroidUtilities.resetPictureInPictureParams(oldSource.activity);
            if (AndroidUtilities.isInPictureInPictureMode(oldSource.activity)) {
                oldSource.activity.moveTaskToBack(false);
            }
        }
    }




    /* Activity callbacks */

    public static void onUserLeaveHint(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return;     // always auto enabled == true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && maxPrioritySource != null) {
            activity.enterPictureInPictureMode(maxPrioritySource.buildPictureInPictureParams());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void onPictureInPictureModeChanged(Activity ignoredActivity, boolean isInPictureInPictureMode) {
        if (maxPrioritySource != null) {
            if (isInPictureInPictureMode) {
                maxPrioritySource.attachToPictureInPicture();
            } else {
                maxPrioritySource.detachFromPictureInPicture();
            }
        }
    }



    /* Utils */

    public static boolean checkAnyPipPermissions(Context context) {
        return checkPermissions(context) > 0;
    }

    public static @PipPermissions int checkPermissions(Context context) {
        if (AndroidUtilities.checkInlinePermissions(context)) {
            return PIP_GRANTED_OVERLAY;
//        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            if (AndroidUtilities.checkPipPermissions(context)) {
//                return PIP_GRANTED_PIP;
//            } else {
//                return PIP_DENIED_PIP;
//            }
        } else {
            return PIP_DENIED_OVERLAY;
        }
    }

    public static int getWindowLayoutParamsType(Context context, boolean inAppOnly) {
        if (!inAppOnly && AndroidUtilities.checkInlinePermissions(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        } else {
            return WindowManager.LayoutParams.TYPE_APPLICATION;
        }
    }

    public static WindowManager.LayoutParams createWindowLayoutParams(Context context, boolean inAppOnly) {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.type = PipNativeApiController.getWindowLayoutParamsType(context, inAppOnly);
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        return windowLayoutParams;
    }
}
