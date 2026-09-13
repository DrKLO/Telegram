package org.telegram.messenger.gramify;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * FIXED Bubble Service
 * Old problem: Service restart pe bubble gayab, ya multiple bubble ban jate the
 */
public class GramifyBubbleService_FIXED extends Service {

    public static final String ACTION_SHOW = "org.telegram.messenger.gramify.BUBBLE_SHOW";
    public static final String ACTION_HIDE = "org.telegram.messenger.gramify.BUBBLE_HIDE";
    public static final String PREF = "gramify_voice";
    public static final String PREF_BUBBLE = "bubble_on";

    private static volatile boolean running = false;
    private WindowManager windowManager;
    private GramifyBubble_FIXED bubble;

    public static boolean isRunning() { return running; }

    public static boolean isEnabled(Context ctx) {
        return ctx != null && ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(PREF_BUBBLE, false);
    }

    public static boolean canShow(Context ctx) {
        if (ctx == null) return false;
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return Settings.canDrawOverlays(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void show(Context ctx) {
        if (ctx == null) return;
        try {
            Intent i = new Intent(ctx, GramifyBubbleService_FIXED.class);
            i.setAction(ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Throwable t) {
            Toast.makeText(ctx, "Bubble start nahi hui: " + t.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void stop(Context ctx) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(PREF_BUBBLE, false).apply();
        try {
            ctx.startService(new Intent(ctx, GramifyBubbleService_FIXED.class).setAction(ACTION_HIDE));
        } catch (Throwable ignore) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent != null ? intent.getAction() : ACTION_SHOW;
        if (ACTION_HIDE.equals(action)) {
            hideBubble();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!canShow(this)) {
            Toast.makeText(this, "Bubble ke liye permission do: Settings -> Apps -> Devgram -> Display over other apps", Toast.LENGTH_LONG).show();
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean(PREF_BUBBLE, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }

        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean(PREF_BUBBLE, true).apply();
        showBubble();
        return START_STICKY;
    }

    private void showBubble() {
        // FIX: Agar pehle se hai to dubara mat banao, warna gayab hone ka bug aata hai
        if (bubble != null && bubble.isShown()) {
            return;
        }
        // Agar purana bubble hai to pehle hatao
        if (bubble != null) {
            try { bubble.hide(); } catch (Throwable ignore) {}
            bubble = null;
        }
        try {
            bubble = new GramifyBubble_FIXED(this, windowManager);
            bubble.show();
            running = bubble.isShown();
        } catch (Throwable t) {
            running = false;
            Toast.makeText(this, "Bubble nahi dikh paayi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void hideBubble() {
        if (bubble != null) {
            bubble.hide();
            bubble = null;
        }
        running = false;
    }

    @Override
    public void onDestroy() {
        hideBubble();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
