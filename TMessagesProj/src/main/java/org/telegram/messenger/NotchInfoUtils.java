package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;

import androidx.core.graphics.PathParser;

public class NotchInfoUtils {
    private static final String BOTTOM_MARKER = "@bottom";
    private static final String DP_MARKER = "@dp";
    private static final String RIGHT_MARKER = "@right";
    private static final String LEFT_MARKER = "@left";

    @SuppressLint("DiscouragedApi")
    public static NotchInfo getInfo(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null;
        }

        NotchInfo info = new NotchInfo();
        int r = ctx.getResources().getIdentifier("config_mainBuiltInDisplayCutout", "string", "android");
        if (r != 0) {
            String spec = ctx.getString(r);
            if (spec.isEmpty()) return null;
            spec = spec.trim();

            Resources res = ctx.getResources();
            DisplayMetrics m = res.getDisplayMetrics();
            int displayWidth = m.widthPixels;
            float density = m.density;

            int gravity;

            float offsetX;
            if (spec.endsWith(RIGHT_MARKER)) {
                offsetX = displayWidth;
                spec = spec.substring(0, spec.length() - RIGHT_MARKER.length()).trim();
                gravity = Gravity.RIGHT;
            } else if (spec.endsWith(LEFT_MARKER)) {
                offsetX = 0;
                spec = spec.substring(0, spec.length() - LEFT_MARKER.length()).trim();
                gravity = Gravity.LEFT;
            } else {
                offsetX = displayWidth / 2f;
                gravity = Gravity.CENTER;
            }
            boolean inDp = spec.endsWith(DP_MARKER);
            if (inDp) {
                spec = spec.substring(0, spec.length() - DP_MARKER.length());
            }

            if (spec.contains(BOTTOM_MARKER)) {
                String[] splits = spec.split(BOTTOM_MARKER, 2);
                spec = splits[0].trim();
            }

            Path p;
            try {
                PathParser.PathDataNode[] n = PathParser.createNodesFromPathData(spec);

                p = new Path();
                PathParser.PathDataNode.nodesToPath(n, p);
            } catch (Throwable e) {
                FileLog.e("Failed to parse notch info", e);
                return null;
            }

            Matrix matrix = new Matrix();
            if (inDp) {
                matrix.postScale(density, density);
            }
            matrix.postTranslate(offsetX, 0);
            p.transform(matrix);

            info.path = p;

            RectF bounds = new RectF();
            p.computeBounds(bounds, true);
            info.bounds = bounds;

            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            if (gravity != Gravity.CENTER && Math.abs(bounds.centerX() - metrics.widthPixels / 2f) <= dp(2)) {
                gravity = Gravity.CENTER;
            }
            if (gravity == Gravity.CENTER && bounds.left < metrics.widthPixels / 4f) {
                gravity = Gravity.LEFT;
            }
            if (gravity == Gravity.CENTER && bounds.right > metrics.widthPixels / 4f * 3f) {
                gravity = Gravity.RIGHT;
            }

            info.gravity = gravity;
            info.rawPath = spec;
            // Only curved is accurate, otherwise it's REALLY likely to be just a square/rectangle
            info.isAccurate = spec.contains("C") || spec.contains("S") || spec.contains("Q");
            info.isLikelyCircle = bounds.width() <= dp(32) || bounds.width() <= bounds.height();

            return info;
        }
        return null;
    }

    public final static class NotchInfo {
        public int gravity;
        public boolean isAccurate;
        public boolean isLikelyCircle;
        public Path path;
        public RectF bounds;
        public String rawPath;
    }
}
