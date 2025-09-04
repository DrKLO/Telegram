package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

public class ProfileMetaballView extends View {

    public static final DispatchQueue profileBlurQueue = new DispatchQueue("profileBlurQueue");

    private ProfileMetaballView.BlurBitmapHolder originalFrame = null;
    private ProfileMetaballView.BlurBitmapHolder nextFrame = null;
    private ProfileMetaballView.BlurBitmapHolder currentFrame = null;

    private boolean usingRenderNode = Build.VERSION.SDK_INT >= 31;
    private RenderNode blurNode;
    private final float renderNodeTop;
    private final float renderNodeSize;
    private float inset;
    private int installedRadius = -1;

    private final Object lock = new Object();
    private final Object drawLock = new Object();
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradientPaint = new Paint();
    private final LinearGradient gradientShader;
    private final Matrix matrix = new Matrix();

    private final ProfileActivity.AvatarImageView imageView;
    private final View view, storyView;

    private final Path path = new Path();
    private final Path clipPath = new Path();
    private final Point p1 = new Point(), p2 = new Point(), p3 = new Point(), p4 = new Point(), p5 = new Point();
    private final Point h1 = new Point(), h2 = new Point(), h3 = new Point(), h4 = new Point();
    private final Point c1 = new Point(), c2 = new Point(), a1 = new Point(), a2 = new Point();

    private volatile boolean isBluring = false;
    private int bgColor = Color.BLACK;
    private int prevOrgKey = -1;
    private int blurRadius = 4;
    private int currentFrameBlurRadius = -1;
    private int alpha;
    private int radius;
    private BitmapShader bitmapShader;

    private boolean needsNewFrame;

    private final Runnable blurTask = this::doBlur;

    public boolean isBackward;
    private float backwardProgress;
    private float backwardFromY;
    private float backwardFromR;
    private float backwardFromAlpha;
    private int backwardFromRadius;

    public ProfileMetaballView(
            View view,
            ProfileActivity.AvatarImageView imageView,
            View storyView
    ) {
        super(view.getContext());
        this.imageView = imageView;
        this.view = view;
        this.storyView = storyView;

        circlePaint.setColor(Color.BLACK);
        circlePaint.setStyle(Paint.Style.FILL);
        connectorPaint.setColor(Color.BLACK);
        connectorPaint.setStyle(Paint.Style.FILL);
        connectorPaint.setStrokeWidth(6f);
        shaderPaint.setColor(Color.BLACK);
        shaderPaint.setStyle(Paint.Style.FILL);
        gradientShader = new LinearGradient(
                0, 0, 0, AndroidUtilities.dpf2(16),
                new int[]{Color.BLACK, Color.TRANSPARENT},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        gradientPaint.setShader(gradientShader);

        usingRenderNode &= SharedConfig.useNewBlur;
        if (usingRenderNode) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        renderNodeTop = dp(24);
        renderNodeSize = dp(56);

        imageView.listenInvalidate(this::updateContent);
    }

    public void setFrameBackgroundColor(int color) {
        bgColor = color;
    }

    private void updateContent() {
        needsNewFrame = true;
        postInvalidateOnAnimation();
    }

    private void doBlur() {
        BlurBitmapHolder originalFrame = this.originalFrame;
        BlurBitmapHolder nextFrame = this.nextFrame;
        BlurBitmapHolder currentFrame = this.currentFrame;

        if (originalFrame != null && !originalFrame.destroying && !originalFrame.isBusy && originalFrame.hasContent) {
            if (prevOrgKey == originalFrame.key && currentFrameBlurRadius == blurRadius) {
                isBluring = false;
                return;
            }
            synchronized (drawLock) {
                originalFrame.lock();
                prevOrgKey = originalFrame.key;
                currentFrameBlurRadius = blurRadius;
                if (nextFrame == null || !nextFrame.canUse(originalFrame)) {
                    if (nextFrame != null) nextFrame.recycle();
                    this.nextFrame = nextFrame = new BlurBitmapHolder(originalFrame);
                } else {
                    nextFrame.clear();
                }
                nextFrame.canvas.drawBitmap(originalFrame.bitmap, 0, 0, null);
                originalFrame.unlock();
            }

            Bitmap blurFrame = nextFrame.bitmap;
            Utilities.stackBlurBitmap(blurFrame, currentFrameBlurRadius);
            if (!isBluring) {
                return;
            }
            synchronized (lock) {
                if (currentFrame == null || !currentFrame.canUse(nextFrame)) {
                    if (currentFrame != null) currentFrame.recycle();
                    this.currentFrame = currentFrame = new ProfileMetaballView.BlurBitmapHolder(nextFrame);
                } else {
                    currentFrame.clear();
                }

                currentFrame.canvas.drawBitmap(blurFrame, 0, 0, null);
                currentFrame.ready();
                applyShader();
            }
            if (isBluring) {
                postInvalidateOnAnimation();
            }
        }
        scheduleNextBlurTask();
    }

    private void scheduleNextBlurTask() {
        if (isBluring && originalFrame != null && originalFrame.hasContent) {
            if (originalFrame.isBusy || prevOrgKey != originalFrame.key || currentFrameBlurRadius != blurRadius) {
                profileBlurQueue.postRunnable(blurTask);
                return;
            }
        }
        isBluring = false;
    }

    private void captureNextFrame() {
        int w = dp(36);
        int h = (int) (w * (float) imageView.getHeight() / imageView.getWidth());
        if (h < w) {
            return;
        }

        if (originalFrame == null || !originalFrame.canUse(w, h)) {
            if (originalFrame != null) originalFrame.recycle();

            originalFrame = new ProfileMetaballView.BlurBitmapHolder(w, h);
        }

        synchronized (drawLock) {
            if (originalFrame.isBusy) {
                needsNewFrame = true;
                return;
            }

            originalFrame.lock();
            originalFrame.clear();

            Canvas canvas = originalFrame.canvas;
            canvas.save();
            if (bgColor != Color.BLACK) {
                canvas.drawColor(bgColor);
            }
            canvas.scale((float) w / imageView.getWidth(), (float) h / imageView.getHeight());
            radius = imageView.getRoundRadiusForExpand();
            imageView.setRoundRadiusForExpand(0);
            imageView.draw(canvas);
            imageView.setRoundRadiusForExpand(radius);
            canvas.restore();

            originalFrame.unlock();
            originalFrame.ready();
            needsNewFrame = false;
        }
    }

    public void destroy() {
        isBluring = false;
        if (originalFrame != null) {
            originalFrame.recycle();
            originalFrame = null;
        }
        if (nextFrame != null) {
            nextFrame.recycle();
            nextFrame = null;
        }
        if (currentFrame != null) {
            currentFrame.recycle();
            currentFrame = null;
        }
        if (blurNode != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blurNode.discardDisplayList();
            }
            blurNode = null;
        }
        alpha = 0;
        profileBlurQueue.cancelRunnable(blurTask);
        imageView.listenInvalidate(null);
    }

    private void applyShader() {
        bitmapShader = new BitmapShader(
                currentFrame.bitmap,
                Shader.TileMode.MIRROR,
                Shader.TileMode.MIRROR
        );
        shaderPaint.setShader(bitmapShader);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isBluring = false;
        alpha = 0;
        profileBlurQueue.cancelRunnable(blurTask);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != View.VISIBLE) {
            isBluring = false;
            alpha = 0;
            profileBlurQueue.cancelRunnable(blurTask);
            imageView.isMetaballWorking = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float r = getWidth();


        float vr, vy;
        if (isBackward) {
            vr = AndroidUtilities.lerp(dp(12), backwardFromR, backwardProgress);
            vy = AndroidUtilities.lerp(-dp(24), backwardFromY, backwardProgress);

            float alpha = lerp(1, backwardFromAlpha, backwardProgress);

            imageView.setRoundRadiusCollapse(lerp(radius, backwardFromRadius, Utilities.clamp01((backwardProgress - 0.5f) / 0.5f)));
            imageView.setAlpha(alpha);
            view.setAlpha(alpha);
        } else {
            vr = view.getWidth() * view.getScaleX() * 0.5f;
            vy = view.getY();
        }

        boolean isDrawing = vr <= dp(40);
        boolean isNear = vr <= dp(32);

        if (!isDrawing) {
            if (!isBackward) {
                imageView.setAlpha(1f);
                storyView.setAlpha(1f);
                imageView.isMetaballWorking = false;
            }
            return;
        }

        float fraction = Math.max(0f, vr - dp(18)) / dp(32 - 18);
        float alphaFraction = lerp(0f, 1f, fraction);
        if (isNear) {
            if (!isBackward) {
                storyView.setAlpha(Math.max(0f, vr - dp(32 - 8)) / dp(8));
                imageView.setAlpha(alphaFraction);
            }
            alpha = (int) (0xFF * alphaFraction);
            blurRadius = 2 + (int) ((1f - fraction) * 20);
        } else {
            alpha = 0xFF;
            blurRadius = 1;
        }

        if (!isBackward) {
            if (usingRenderNode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (canvas.isHardwareAccelerated()) {
                    initRenderNode();
                    drawWithRenderNode();
                } else {
                    blurNode = null;
                    usingRenderNode = false;
                }
            }

            if (!usingRenderNode) {
                boolean shouldCapture = needsNewFrame || bitmapShader == null;
                if (alpha > 0 && shouldCapture) {
                    captureNextFrame();

                    if (!isBluring && originalFrame != null) {
                        isBluring = true;
                        profileBlurQueue.cancelRunnable(blurTask);
                        profileBlurQueue.postRunnable(blurTask);
                    }
                }
                if (!isBluring && originalFrame != null) {
                    if (alpha > 0 && (currentFrameBlurRadius != blurRadius || prevOrgKey != originalFrame.key)) {
                        isBluring = true;
                        profileBlurQueue.cancelRunnable(blurTask);
                        profileBlurQueue.postRunnable(blurTask);
                    }
                }
            }
        } else if (isBluring) {
            isBluring = false;
            profileBlurQueue.cancelRunnable(blurTask);
        }

        boolean hasBlurDisplay = blurNode != null;
        if (hasBlurDisplay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasBlurDisplay = blurNode.hasDisplayList();
            }
        }
        boolean hasImage = hasBlurDisplay || bitmapShader != null;

        float c = clamp(vy / dp(24), 1, -1);
        float v = clamp((1f - c / 1.3f) / 2f, 0.8f, 0);
        float near = isNear ? 1f : 1f - (vr - dp(32)) / dp(40 - 32);
        float exit = Math.min((vy + vr * 2f) / dp(6), 1f);
        if (!isNear) {
            v = Math.min(lerp(0f, 0.2f, near), v);
        }

        c1.x = c2.x = cx;
        c1.y = -r + dp(1);
        c2.y = vy + vr;

        float scaledRadius;

        if (!isBackward && !imageView.hasStories) {
            imageView.setRoundRadiusCollapse(lerp(dp(22), radius, Utilities.clamp01((vr - dp(34)) / dp(6))));
            scaledRadius = vr / dp(22) * imageView.roundRadiusCollapse;
        } else {
            scaledRadius = vr / dp(21) * radius;
        }

        if (createMetaballPath(r, vr, v)) {
            float decelerateNear = isNear ? 1f : near * near;

            if ((hasImage || isBackward) && isNear) {
                AndroidUtilities.rectTmp.set(cx - vr, vy, cx + vr, vy + vr * 2f);
                path.addRoundRect(AndroidUtilities.rectTmp, scaledRadius, scaledRadius, Path.Direction.CCW);
            }
            canvas.save();
            if (!isNear) {
                clipPath.rewind();
                clipPath.addRect(0, 0, r, dp(10) * decelerateNear, Path.Direction.CW);
                clipPath.addCircle(c1.x, 0, dp(20) * decelerateNear, Path.Direction.CW);

                float clipRadius = vr + dp(12) * decelerateNear;
                clipPath.addCircle(c2.x, c2.y, clipRadius, Path.Direction.CW);
                clipPath.addCircle(c2.x, c2.y - clipRadius + dp(4) * decelerateNear, dp(8) * decelerateNear, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }
            canvas.drawPath(path, connectorPaint);
            canvas.drawCircle(cx, -r + dp(1) * Math.max(1f, near * 2f) * exit, r, circlePaint);
            canvas.restore();

            float contentScaleRN = usingRenderNode ? vr * 2f / renderNodeSize : 1f;
            float insetScaledRN = usingRenderNode ? inset * contentScaleRN : 0f;
            if (alpha > 0 && hasImage) {
                canvas.save();
                clipPath.rewind();
                if (isNear) {
                    float insetF = usingRenderNode ? AndroidUtilities.lerp(insetScaledRN, 0, alphaFraction) : 0f;
                    AndroidUtilities.rectTmp.set(cx - vr + insetF, vy + insetF, cx + vr - insetF, vy + vr * 2f - insetF);
                    clipPath.addRoundRect(AndroidUtilities.rectTmp, scaledRadius, scaledRadius, Path.Direction.CW);
                    clipPath.addRect(0f, 0, r, vy + dp(16), Path.Direction.CW);
                } else {
                    float clipRadius = vr + dp(12) * decelerateNear;
                    clipPath.addCircle(c2.x, c2.y, clipRadius, Path.Direction.CW);
                    clipPath.addCircle(c2.x, c2.y - clipRadius + dp(4) * decelerateNear, dp(8) * decelerateNear, Path.Direction.CW);
                }
                canvas.clipPath(clipPath);

                if (blurNode != null) {
                    canvas.save();
                    canvas.clipPath(path);
                    float scale = dp(1) * contentScaleRN;
                    canvas.translate(cx - vr + insetScaledRN, insetScaledRN + vy - renderNodeTop * contentScaleRN);
                    canvas.scale(scale, scale);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        blurNode.setAlpha(alphaFraction);
                        canvas.drawRenderNode(blurNode);
                    }
                    canvas.restore();
                } else {
                    synchronized (lock) {
                        matrix.setScale(vr * 2f / currentFrame.bitmap.getWidth(), vr * 2f / currentFrame.bitmap.getHeight());
                        matrix.postTranslate(cx - vr, vy);
                        bitmapShader.setLocalMatrix(matrix);
                        shaderPaint.setAlpha(alpha);
                        canvas.drawPath(path, shaderPaint);
                    }
                }
                matrix.setTranslate(0f, Math.max(0f, vy - dp(14f) - dp(15) * (1f - near)));
                gradientShader.setLocalMatrix(matrix);
                canvas.drawPath(path, gradientPaint);
                canvas.restore();
            }
        }

        /*if (!isClose) {
            float scale = 1f - (vr - dp(32)) / dp(40 - 32);
            float top = -dp(14);
            float r2 = dp(12) * scale;
            float r1 = r * 0.9f;
            c1.x = c2.x = cx;
            c1.y = -r1 + dp(1);
            c2.y = top + r2;
            if (createMetaballPath(r1, r2, 0.6f)) {
                path.addCircle(cx, top + r2, r2, Path.Direction.CCW);
                canvas.drawPath(path, connectorPaint);
                canvas.drawCircle(cx, -r1 + dp(1), r1, circlePaint);
            }
        }*/
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void initRenderNode() {
        if (blurNode == null) {
            blurNode = new RenderNode("profileMetaballBlurNode");
        }

        if (installedRadius != blurRadius) {
            installedRadius = blurRadius;
            float r = Math.max(blurRadius / 2f - 1, 0.5f);
            try {
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            } catch (Exception ignore) {}
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void drawWithRenderNode() {
        if (!needsNewFrame && blurNode.hasDisplayList()) {
            return;
        }

        needsNewFrame = false;
        float scale = dp(1);
        blurNode.setPosition(
                0, 0,
                (int) (renderNodeSize / scale),
                (int) ((renderNodeSize + renderNodeTop) / scale)
        );
        RecordingCanvas recordingCanvas = blurNode.beginRecording();
        recordingCanvas.scale(1f / scale, 1f / scale);

        boolean isEmoji = imageView.animatedEmojiDrawable != null;
        ImageReceiver imageReceiver = isEmoji
                ? imageView.animatedEmojiDrawable.getImageReceiver()
                : imageView.imageReceiver;
        radius = imageView.getRoundRadiusForExpand();
        imageView.setRoundRadiusForExpand(0);

        float oldW = imageReceiver.getImageWidth();
        float oldH = imageReceiver.getImageHeight();
        float oldX = imageReceiver.getImageX();
        float oldY = imageReceiver.getImageY();

        inset = oldX * renderNodeSize / oldW;
        float ns = renderNodeSize - inset * 2f;
        float sz = dp(100);
        imageReceiver.setImageCoords(0, 0, sz, sz);

        if (bgColor != Color.BLACK) {
            recordingCanvas.drawColor(bgColor);
        }

        recordingCanvas.save();
        recordingCanvas.translate(0f, renderNodeTop);
        recordingCanvas.scale(ns / sz, ns / sz);
        imageReceiver.draw(recordingCanvas);
        recordingCanvas.restore();

        if (!isEmoji) {
            recordingCanvas.scale(1f, -1.5f);
            recordingCanvas.translate(0f, -renderNodeTop / 1.5f);
            imageReceiver.draw(recordingCanvas);
        }

        imageReceiver.setImageCoords(oldX, oldY, oldW, oldH);
        imageView.setRoundRadiusForExpand(radius);

        blurNode.endRecording();
    }

    public boolean shouldStick() {
        float size = view.getWidth() * view.getScaleX();
        return size <= dp(64);
    }

    public void captureBackward() {
        float vr = view.getWidth() * view.getScaleX() * 0.5f;
        if (vr > dp(32)) {
            setVisibility(View.GONE);
            return;
        }
        isBackward = true;
        backwardFromRadius = imageView.roundRadiusCollapse <= 0
                ? radius : imageView.roundRadiusCollapse;
        backwardFromAlpha = alpha / 255f;
        backwardFromY = Math.min(-AndroidUtilities.dp(8), view.getY());
        backwardFromR = Math.min(AndroidUtilities.dp(16), vr);
    }

    public void updateBackward(float fraction) {
        backwardProgress = fraction;
        invalidate();
    }

    private static float radians(float degrees) {
        return degrees / 180 * (float) Math.PI;
    }
    private static float cos(float degrees) {
        return (float) Math.cos(degrees);
    }
    private static float sin(float degrees) {
        return (float) Math.sin(degrees);
    }
    private static float cos(double degrees) {
        return (float) Math.cos((float) degrees);
    }
    private static float sin(double degrees) {
        return (float) Math.sin((float) degrees);
    }

    private boolean createMetaballPath(float radius1, float radius2, float v) {
        final float HALF_PI = (float) (Math.PI / 2);
        final float HANDLE_SIZE = 20.0f;

        float d = dist(c1, c2);
        float maxDist = radius1 + radius2 * 2.25f;
        float u1 = 0f, u2 = 0f;

        if (radius1 == 0 || radius2 == 0 || d > maxDist || d <= Math.abs(radius1 - radius2)) {
            return false;
        }

        if (d < radius1 + radius2) {
            u1 = (float) Math.acos((radius1 * radius1 + d * d - radius2 * radius2) / (2 * radius1 * d));
            u2 = (float) Math.acos((radius2 * radius2 + d * d - radius1 * radius1) / (2 * radius2 * d));
        }

        float angleBetweenCenters = angle(c2, c1);
        float maxSpread = (float) Math.acos((radius1 - radius2) / d);

        float angle1 = angleBetweenCenters + u1 + (maxSpread - u1) * v;
        float angle2 = angleBetweenCenters - u1 - (maxSpread - u1) * v;
        float angle3 = angleBetweenCenters + (float) Math.PI - u2 - ((float) Math.PI - u2 - maxSpread) * v;
        float angle4 = angleBetweenCenters - (float) Math.PI + u2 + ((float) Math.PI - u2 - maxSpread) * v;

        getVector(c1, angle1, radius1, p1);
        getVector(c1, angle2, radius1, p2);
        getVector(c2, angle3, radius2, p3);
        getVector(c2, angle4, radius2, p4);

        float totalRadius = radius1 + radius2;
        float d2Base = Math.min(v * HANDLE_SIZE, dist(p1, p3) / totalRadius);
        float d2 = d2Base * Math.min(1, d * 2 / totalRadius);

        float r1 = radius1 * d2;
        float r2 = radius2 * d2;

        getVector(p1, angle1 - HALF_PI, r1, h1);
        getVector(p2, angle2 + HALF_PI, r1, h2);
        getVector(p3, angle3 + HALF_PI, r2, h3);
        getVector(p4, angle4 - HALF_PI, r2, h4);

        path.rewind();
        path.moveTo(p1.x, p1.y);
        path.cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y);

        getVector(p3, angle3 + HALF_PI, r2 * 0.55f, a1);
        getVector(p4, angle4 - HALF_PI, r2 * 0.55f, a2);
        path.cubicTo(a1.x, a1.y, a2.x, a2.y, p4.x, p4.y);

        path.cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y);
        path.close();
        return true;
    }

    private float dist(Point a, Point b) {
        return MathUtils.distance(a.x, a.y, b.x, b.y);
    }

    private float angle(Point a, Point b) {
        return (float) Math.atan2(a.y - b.y, a.x - b.x);
    }

    private void getVector(Point center, float angle, float radius, Point out) {
        out.x = (float) (center.x + radius * Math.cos(angle));
        out.y = (float) (center.y + radius * Math.sin(angle));
    }

    public static class BlurBitmapHolder {
        Canvas canvas;
        Bitmap bitmap;
        boolean destroying, destroyed;
        boolean isBusy, hasContent;
        int key = 0;

        public BlurBitmapHolder(BlurBitmapHolder holder) {
            bitmap = Bitmap.createBitmap(holder.bitmap.getWidth(), holder.bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }

        public BlurBitmapHolder(int w, int h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }

        public void clear() {
            if (destroyed) return;
            hasContent = false;
            bitmap.eraseColor(Color.TRANSPARENT);
        }

        public void ready() {
            hasContent = true;
            key++;
        }

        public boolean canUse(int w, int h) {
            if (destroyed) return false;
            return bitmap.getWidth() == w &&
                    bitmap.getHeight() == h;
        }

        public boolean canUse(BlurBitmapHolder holder) {
            if (destroyed) return false;
            return bitmap.getWidth() == holder.bitmap.getWidth() &&
                    bitmap.getHeight() == holder.bitmap.getHeight();
        }

        public void recycle() {
            destroying = true;
            if (!isBusy) {
                destroyed = true;
                bitmap.recycle();
            }
        }

        public void lock() {
            isBusy = true;
        }

        public void unlock() {
            isBusy = false;
            if (!destroyed && destroying) {
                destroyed = true;
                bitmap.recycle();
            }
        }
    }
}