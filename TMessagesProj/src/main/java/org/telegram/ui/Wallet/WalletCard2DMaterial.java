package org.telegram.ui.Wallet;

import android.content.Context;
import android.graphics.*;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.FileLog;
import java.io.InputStream;
import java.util.Random;

/** Canvas material assembled from packaged lighting masks; no runtime pixel baking. */
final class WalletCard2DMaterial {
    private static volatile boolean prepared;
    private static final java.util.concurrent.atomic.AtomicBoolean preparing = new java.util.concurrent.atomic.AtomicBoolean();

    static boolean isPrepared(int icon) {
        return prepared && ICON_CACHE.get(icon) != null;
    }

    static void prewarm(Context context) {
        if (!preparing.compareAndSet(false, true)) return;
        ASSET_QUEUE.postRunnable(() -> new WalletCard2DMaterial(context.getApplicationContext(),
                org.telegram.messenger.R.drawable.wallet_card_qr, 46, () -> {}));
    }

    private static final float W = 336, H = 205, R = 25.2f;
    private static final int STEPS = 128;
    private static final float SECOND_LIGHT_WIDTH = 2f;
    private static final float QR_EXPOSURE = 6f;
    private static final float EDGE_BRIGHTNESS = 1.5f;
    static final org.telegram.messenger.DispatchQueue ASSET_QUEUE = new org.telegram.messenger.DispatchQueue("walletCardAssets", true, android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Runnable invalidate;

    private static Bitmap[] textures;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint screenPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF face = new RectF(0, 0, W, H), inset = new RectF();
    private final RectF faceTextureBounds = new RectF(W / 2 - 210, H / 2 - 210, W / 2 + 210, H / 2 + 210);
    private static final int DEPTH_POINTS = 76;
    private final float[] depthUv = new float[DEPTH_POINTS * 2];
    private final float[] depthNormals = new float[DEPTH_POINTS * 2];
    private final float[] depthFront = new float[DEPTH_POINTS * 2], depthBack = new float[DEPTH_POINTS * 2];
    private final float[] depthZ = new float[DEPTH_POINTS], stripZ = new float[DEPTH_POINTS];
    private final int[] depthOrder = new int[DEPTH_POINTS];
    private final Path depthPath = new Path();
    private float depthScale;
    private static final float FLAT_DEPTH_SIN = .01f;
    private boolean depthBackFacing;
    private final Path clip = new Path(), star = new Path(), reveal = new Path();
    private final SweepGradient reflection, finishMask;
    private final Shader[] finishes = new Shader[2];
    private Shader grainFinish;
    private int grooveTint;
    private PorterDuffColorFilter grooveColorFilter;
    private int reflectionTint;
    private PorterDuffColorFilter reflectionColorFilter;
    private final int[] reflectionTints = new int[2];
    private final Shader[] grooves = new Shader[2];
    private final SweepGradient[] grooveMasks = new SweepGradient[2];
    private static Bitmap[][] faceMasks;
    private final boolean[] surfaceGifts = new boolean[2];
    private final int[][] faceColors = new int[2][];
    private final PorterDuffColorFilter[][] faceFilters = new PorterDuffColorFilter[2][8];
    private static final int[] STOCK_FACE_COLORS = {0xff006edb,0xff0072df,0xff0076e3,0xff007ae6,0xff007ee8,0xff0081ed,0xff0084f1,0xff0087f4,0xff008af5};
    private final int[] darks = {0, 0}, lights = {0, 0};
    private final float[][] stars = new float[22][6];
    private final RectF qr;
    private final int[] qrColors = new int[65];
    private static final int ICON_DIRECTIONS = 8;
    private final Bitmap[] iconLight = new Bitmap[ICON_DIRECTIONS], iconDark = new Bitmap[ICON_DIRECTIONS];
    private Bitmap iconMask;
    private final Context context;
    private float pitch, yaw, sweep, shine, lx, ly, lz = 1, hx, hy, hz = 1;
    private SweepGradient qrGradient;
    private final SweepGradient qrBrushMask;
    private Shader qrBrush;
    private int iconRes;
    private int rimDark = 0xff2368d5, rimLight = 0xff278ff7;
    private boolean hasRotation;
    private static final android.util.LruCache<Integer, Bitmap[]> ICON_CACHE = new android.util.LruCache<>(2);

    WalletCard2DMaterial(Context context, int icon, int rightPadding, Runnable invalidate) {
        this.invalidate = invalidate;
        this.context = context;
        float radiusY = WalletCardGeometry.radiusY(H);
        for (int corner = 0; corner < 4; corner++) {
            float cx = corner < 2 ? W - R : R;
            float cy = corner == 0 || corner == 3 ? radiusY : H - radiusY;
            for (int step = 0; step <= 18; step++) {
                int i = corner * 19 + step;
                double a = Math.toRadians(-90 + corner * 90 + step * 5);
                float nx = (float)Math.cos(a), ny = (float)Math.sin(a);
                depthUv[i * 2] = (cx + R * nx) / W;
                depthUv[i * 2 + 1] = (cy + radiusY * ny) / H;
                depthNormals[i * 2] = nx; depthNormals[i * 2 + 1] = ny;
            }
        }
        qr = new RectF(W - rightPadding - 48, 74, W - rightPadding, 110);
        if (textures == null) {
            String[] names = {"finish_light", "finish_dark", "groove_along", "groove_across", "qr_brush_light", "qr_brush_dark", "grain"};
            textures = new Bitmap[names.length];
            for (int i = 0; i < names.length; i++) {
                try (InputStream input = context.getAssets().open("wallet_card_2d/" + names[i] + ".webp")) {
                    textures[i] = BitmapFactory.decodeStream(input);
                    if (textures[i] != null) textures[i].prepareToDraw();
                } catch (Exception e) { FileLog.e(e); }
            }
        }
        // Decode only: lighting masks are generated offline and shared by every palette.
        if (faceMasks == null) {
            Bitmap[][] decoded = new Bitmap[2][8];
            for (int group = 0; group < 2; group++) for (int j = 0; j < 8; j++) {
                try (InputStream input = context.getAssets().open("wallet_card_2d/face_" + group + "_" + j + ".webp")) {
                    decoded[group][j] = BitmapFactory.decodeStream(input);
                    if (decoded[group][j] != null) decoded[group][j].prepareToDraw();
                } catch (Exception e) { FileLog.e(e); }
            }
            faceMasks = decoded;
        }
        int[] colors = new int[STEPS + 1], mask = new int[STEPS + 1];
        for (int i = 0; i <= STEPS; i++) {
            double a = i * Math.PI * 2 / STEPS;
            colors[i] = Color.argb(Math.round(64 * secondary(a)), 255, 255, 255);
            mask[i] = Color.argb(Math.round(255 * smooth(.18f, .88f, (float)Math.abs(Math.cos(Math.atan2(Math.sin(a) / SECOND_LIGHT_WIDTH, Math.cos(a)))))), 255, 255, 255);
        }
        reflection = new SweepGradient(W / 2, H / 2, colors, null);
        finishMask = new SweepGradient(W / 2, H / 2, mask, null);
        if (textures[6] != null) {
            BitmapShader grain = new BitmapShader(textures[6], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(W / textures[6].getWidth(), H / textures[6].getHeight());
            grain.setLocalMatrix(matrix);
            grainFinish = new ComposeShader(grain, finishMask, PorterDuff.Mode.DST_IN);
        }
        for (int i = 0; i < 2; i++) {
            if (textures[i] != null) {
                BitmapShader finish = new BitmapShader(textures[i], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                matrix.setScale(W / textures[i].getWidth(), H / textures[i].getHeight());
                finish.setLocalMatrix(matrix);
                finishes[i] = new ComposeShader(finish, finishMask, PorterDuff.Mode.DST_IN);
            }
            if (textures[2 + i] == null) continue;
            BitmapShader texture = new BitmapShader(textures[2 + i], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(W / textures[2 + i].getWidth(), H / textures[2 + i].getHeight());
            texture.setLocalMatrix(matrix);
            int[] grooveColors = new int[STEPS + 1];
            for (int j = 0; j <= STEPS; j++) {
                double a = j * Math.PI * 2 / STEPS;
                float c = (float)Math.abs(Math.cos(Math.atan2(Math.sin(a) / SECOND_LIGHT_WIDTH, Math.cos(a))));
                float v = smooth((float)Math.cos(.86 * Math.PI * .5), (float)Math.cos(.86 * Math.PI * .5 * .28), c);
                v *= i == 0 ? c * c : 1 - c * c;
                grooveColors[j] = alpha(Color.WHITE, v);
            }
            grooveMasks[i] = new SweepGradient(W / 2, H / 2, grooveColors, null);
            grooves[i] = new ComposeShader(texture, grooveMasks[i], PorterDuff.Mode.DST_IN);
        }
        screenPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        stroke.setStyle(Paint.Style.STROKE);
        Random random = new Random(0x57414C4C45544CL);
        for (int i = 0; i < stars.length; i++) {
            float best = -1;
            for (int j = 0; j < (i == 0 ? 1 : 32); j++) {
                float x = random.nextFloat() * W, y = random.nextFloat() * H, dist = Float.MAX_VALUE;
                for (int k = 0; k < i; k++) {
                    float dx = Math.abs(x - stars[k][0]), dy = Math.abs(y - stars[k][1]);
                    dx = Math.min(dx, W - dx); dy = Math.min(dy, H - dy);
                    dist = Math.min(dist, dx * dx + dy * dy);
                }
                if (dist > best) { best = dist; stars[i][0] = x; stars[i][1] = y; }
            }
            stars[i][2] = (1 + random.nextFloat() * 6) * W / 512;
            double a = random.nextFloat() * Math.PI * 2;
            float t = .05f + random.nextFloat() * .45f, inv = 1 / (float)Math.sqrt(1 + t * t);
            stars[i][3] = (float)Math.cos(a) * t * inv;
            stars[i][4] = (float)Math.sin(a) * t * inv;
            stars[i][5] = inv;
        }
        int[] brushStops = new int[65];
        for (int i = 0; i <= 64; i++) brushStops[i] = alpha(Color.WHITE,
                .12f + .88f * (float)Math.pow(Math.abs(Math.cos(i * Math.PI * 2 / 64)), 8));
        qrBrushMask = new SweepGradient(qr.centerX(), qr.centerY(), brushStops, null);
        if (textures[4] != null) {
            BitmapShader brush = new BitmapShader(textures[4], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(qr.width() / textures[4].getWidth(), qr.height() / textures[4].getHeight());
            matrix.postTranslate(qr.left, qr.top); brush.setLocalMatrix(matrix);
            qrBrush = new ComposeShader(brush, qrBrushMask, PorterDuff.Mode.DST_IN);
        }
        installIcon(loadIcon(context, icon));
        iconRes = icon;
        setRotation(0, 0);
        prepared = true;
    }

    void setRotation(float x, float y) {
        if (hasRotation && pitch == x && yaw == y) return;
        hasRotation = true;
        pitch = x; yaw = y;
        double ax = Math.toRadians(x), ay = Math.toRadians(y);
        // Inverse card rotation of the study's fixed studio light and viewer.
        float vx = (float)(Math.cos(ax) * Math.sin(ay));
        float vy = (float)Math.sin(ax), vz = (float)(Math.cos(ax) * Math.cos(ay));
        lx = (float)(.175 * Math.cos(ay) + .83 * vx);
        ly = .83f * vy; lz = (float)(-.175 * Math.sin(ay) + .83 * vz);
        float n = (float)Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= n; ly /= n; lz /= n;
        hx = lx + vx; hy = ly + vy; hz = lz + vz;
        n = (float)Math.sqrt(hx * hx + hy * hy + hz * hz); hx /= n; hy /= n; hz /= n;
        sweep = (float)Math.toRadians(1.72 + y * 2 - x * 1.25);
        shine = -2 * (hx - .103713069f) + 4 * hy;
        matrix.setRotate((float)Math.toDegrees(shine), W / 2, H / 2);
        reflection.setLocalMatrix(matrix); finishMask.setLocalMatrix(matrix);
        for (SweepGradient mask : grooveMasks) if (mask != null) mask.setLocalMatrix(matrix);
        float ca = (float)Math.cos(ax), sa = (float)Math.sin(ax), cb = (float)Math.cos(ay), sb = -(float)Math.sin(ay);
        float reflectedX = -cb * vx + sb * vz;
        float reflectedY = -sa * sb * vx - ca * vy - sa * cb * vz;
        float reflectedZ = ca * sb * vx - sa * vy + ca * cb * vz;
        float environment = clamp((studio(reflectedX, reflectedY, reflectedZ) - .12f) / 1.8f);
        float energy = mix(.25f, 2.2f, environment), floor = .35f * mix(.9f, 1.2f, environment);
        for (int i = 0; i < qrColors.length; i++) {
            double c = Math.abs(Math.cos(i * Math.PI * 2 / 64));
            double value = (energy * (.72 + .40 * c * c) + .65 * c * c + 13 * Math.pow(c, 22)) * QR_EXPOSURE * (.65 + .35 * clamp(lz));
            float v = mix(floor, 1, (float)Math.pow(value / (1 + value), 2.1 / 2.2));
            qrColors[i] = Color.rgb(Math.round(v * .992f * 255), Math.round(v * .996f * 255), Math.round(v * 255));
        }
        qrGradient = new SweepGradient(qr.centerX(), qr.centerY(), qrColors, null);
        matrix.setRotate((float)Math.toDegrees(shine), qr.centerX(), qr.centerY());
        qrGradient.setLocalMatrix(matrix);
        qrBrushMask.setLocalMatrix(matrix);
    }

    void drawSurface(Canvas canvas, float[] style) {
        for (int pass = 0; pass < (style == null ? 1 : 2); pass++) {
            int d = 0xff2368d5, l = 0xff278ff7;
            if (style != null) {
                int offset = 10 + pass * 4;
                if (style.length >= 18 && style[offset] >= 0) {
                    d = WalletGiftPalette.surface2DEdge(0xff088fee, (int)style[offset], (int)style[offset + 1]);
                    l = WalletGiftPalette.surface2DEdge(0xff16abf9, (int)style[offset], (int)style[offset + 1]);
                } else {
                    d = WalletGiftPalette.tint(0xff2368d5, style, pass * 3);
                    l = WalletGiftPalette.tint(0xff278ff7, style, pass * 3);
                }
            }
            boolean gift = style != null && style.length >= 18 && style[10 + pass * 4] >= 0;
            if (faceColors[pass] == null || darks[pass] != d || lights[pass] != l || surfaceGifts[pass] != gift) {
                darks[pass] = d; lights[pass] = l; surfaceGifts[pass] = gift;
                faceColors[pass] = faceStops(d, l, gift);
                for (int j = 0; j < 8; j++) faceFilters[pass][j] =
                        new PorterDuffColorFilter(faceColors[pass][j + 1], PorterDuff.Mode.SRC_IN);
                reflectionTints[pass] = materialReflectionTint(d, l);

            }
            // Keep both palettes ready for the rim/reflection, but do not draw
            // a fully covered old face or a still-invisible incoming face.
            if (style != null && ((pass == 0 && style[8] >= 1f)
                    || (pass == 1 && style[8] <= 0f))) continue;
            int save = canvas.save();
            if (pass == 1 && style[9] < .5f && style[8] < 1) {
                reveal.rewind(); reveal.addCircle(style[6] * W, style[7] * H, style[8] * W * 1.2f, Path.Direction.CW); canvas.clipPath(reveal);
            }
            canvas.rotate((float)Math.toDegrees(sweep), W / 2, H / 2);
            int opacity = pass == 1 && style[9] > .5f ? Math.round(255 * style[8]) : 255;
            int layer = opacity < 255 ? canvas.saveLayerAlpha(faceTextureBounds, opacity) : -1;
            paint.setShader(null); paint.setColorFilter(null); paint.setColor(faceColors[pass][0]);
            canvas.drawRect(faceTextureBounds, paint);
            for (int j = 0; j < 8; j++) {
                Bitmap mask = faceMasks[gift ? 1 : 0][j];
                if (mask == null) continue;
                paint.setColorFilter(faceFilters[pass][j]);
                canvas.drawBitmap(mask, null, faceTextureBounds, paint);
            }
            paint.setColorFilter(null);
            if (layer != -1) canvas.restoreToCount(layer);
            paint.setAlpha(255); canvas.restoreToCount(save);
        }
        rimDark = style == null ? darks[0] : ColorUtils.blendARGB(darks[0], darks[1], style[8]);
        rimLight = style == null ? lights[0] : ColorUtils.blendARGB(lights[0], lights[1], style[8]);
        boolean gift0 = style != null && style.length >= 18 && style[10] >= 0;
        boolean gift1 = style != null && style.length >= 18 && style[14] >= 0;
        float progress = style == null || style.length < 18 ? 0 : clamp(style[8]);
        int tint0 = gift0 ? reflectionTints[0] : 0xff54acff;
        int tint1 = gift1 ? reflectionTints[1] : 0xff54acff;
        int reflectionColor = ColorUtils.blendARGB(tint0, tint1, progress);
        if (reflectionColorFilter == null || reflectionTint != reflectionColor) {
            reflectionTint = reflectionColor;
            reflectionColorFilter = new PorterDuffColorFilter(reflectionColor, PorterDuff.Mode.SRC_IN);
        }
        // Web prepareReflectionTexture: normalized sqrt(linear material / peak),
        // with .18 peak opacity for gift palettes instead of the stock .25.
        float strength = mix(gift0 ? .18f : .25f, gift1 ? .18f : .25f, progress);
        screenPaint.setColorFilter(reflectionColorFilter);
        screenPaint.setShader(reflection);
        screenPaint.setAlpha(Math.round(255 * (.72f + .28f * clamp(lz)) * strength / .25f));
        canvas.drawRect(face, screenPaint);
        screenPaint.setColorFilter(null);
        screenPaint.setShader(null); paint.setShader(null);
        if (android.os.Build.VERSION.SDK_INT >= 29 && grainFinish != null) {
            // Match both web soft-light passes, rather than whitening/darkening the face.
            paint.setBlendMode(BlendMode.SOFT_LIGHT);
            bitmap(canvas, textures[6], face, Math.round(255 * .055f * .70f));
            paint.setShader(grainFinish); paint.setAlpha(Math.round(255 * .30f * .70f));
            canvas.drawRect(face, paint);
            paint.setShader(null); paint.setAlpha(255); paint.setBlendMode(BlendMode.SRC_OVER);
        } else {
            // Older Canvas backends lack soft-light: keep the signed approximation subtle.
            bitmap(canvas, textures[0], face, 5); bitmap(canvas, textures[1], face, 5);
            for (Shader finish : finishes) if (finish != null) {
                paint.setShader(finish); paint.setAlpha(25); canvas.drawRect(face, paint);
            }
            paint.setShader(null); paint.setAlpha(255);
        }
        int tint = style == null ? 0xff3995ff : Color.rgb(
                Math.min(255, Math.round(Color.red(rimLight) * 1.12f)),
                Math.min(255, Math.round(Color.green(rimLight) * 1.12f)),
                Math.min(255, Math.round(Color.blue(rimLight) * 1.12f)));
        if (grooveColorFilter == null || grooveTint != tint) {
            grooveTint = tint;
            grooveColorFilter = new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN);
        }
        screenPaint.setColorFilter(grooveColorFilter);
        for (Shader groove : grooves) if (groove != null) {
            screenPaint.setShader(groove); screenPaint.setAlpha(Math.round(255 * .23f * .70f));
            canvas.drawRect(face, screenPaint);
        }
        screenPaint.setColorFilter(null); screenPaint.setShader(null); screenPaint.setAlpha(255);
    }

    private static int materialReflectionTint(int dark, int light) {
        float r = (linearChannel(Color.red(dark)) + linearChannel(Color.red(light))) * .5f;
        float g = (linearChannel(Color.green(dark)) + linearChannel(Color.green(light))) * .5f;
        float b = (linearChannel(Color.blue(dark)) + linearChannel(Color.blue(light))) * .5f;
        float peak = Math.max(.000001f, Math.max(r, Math.max(g, b)));
        return Color.rgb(Math.round(255 * (float)Math.sqrt(r / peak)),
                Math.round(255 * (float)Math.sqrt(g / peak)),
                Math.round(255 * (float)Math.sqrt(b / peak)));
    }

    private static float linearChannel(int channel) {
        float v = channel / 255f;
        return v <= .04045f ? v / 12.92f : (float)Math.pow((v + .055f) / 1.055f, 2.4);
    }

    private static float encodeSrgb(float value) {
        return value <= .0031308f ? value * 12.92f : 1.055f * (float)Math.pow(value, 1 / 2.4f) - .055f;
    }

    /** Nine scalar colors, matching the web mask experiment; no pixel loop or bitmap allocation. */
    private static int[] faceStops(int dark, int light, boolean gift) {
        if (!gift) return STOCK_FACE_COLORS;
        float[] darkChannels = new float[3], lightChannels = new float[3];
        for (int k = 0; k < 3; k++) {
            darkChannels[k] = encodeSrgb(linearChannel((dark >> (16 - k * 8)) & 255) * .73f);
            lightChannels[k] = encodeSrgb(linearChannel((light >> (16 - k * 8)) & 255) * .73f);
        }
        float roughness = .1066f;
        float environment = mix(.061f, .356f, smooth(-.45f, .72f, 0))
                + .2556f * (float)Math.pow(.9757, mix(3.6f, 1.25f, roughness))
                + .1133f * (float)Math.pow(.571, mix(5, 1.5f, roughness))
                + 2.5191f * (float)Math.pow(.72, mix(18, 3.5f, roughness))
                + .7832f * (float)Math.pow(.68, mix(34, 5.5f, roughness));
        float indirect = environment * .82f * mix(.92f, .60f, roughness) * (.8f * 1.375f) * 1.45f;
        float diffuse = .055f * (1.7f * .72f) * 1.4f + .055f * (.8f * 1.375f) * .7f;
        float lighting = .30f / .2788f * (mix(.91f, 1.06f, .825f) * indirect + diffuse) * 1.02925f;
        int[] colors = new int[9];
        for (int j = 0; j <= 8; j++) {
            float t = j / 8f;
            int color = 0xff000000;
            for (int k = 0; k < 3; k++) {
                float encoded = mix(darkChannels[k], lightChannels[k], t) * (.8f + .2f * t);
                float value = encoded <= .04045f ? encoded / 12.92f
                        : (float)Math.pow((encoded + .055f) / 1.055f, 2.4);
                value *= lighting * 1.3f * 1.765f;
                float aces = clamp(value * (2.51f * value + .03f) / (value * (2.43f * value + .59f) + .14f));
                int channel = Math.round(255 * (float)Math.pow(mix(aces, clamp(value), .65f), 1 / 2.2f));
                color |= channel << (16 - k * 8);
            }
            colors[j] = color;
        }
        return colors;
    }

    void drawStars(Canvas canvas, boolean hasCardNumber) {
        paint.setShader(null); paint.setColor(Color.WHITE);
        for (float[] s : stars) {
            if (qr.contains(s[0], s[1])) continue;
            // The two vertical address lines are centered at x=313. Exclude
            // whole stars (including their tips) from that strip, avoiding
            // clipped sparkles and light flashes through the number/shadow.
            if (hasCardNumber && s[0] + s[2] >= 298 && s[0] - s[2] <= 328) continue;
            double a = Math.atan2(s[1] - H / 2, s[0] - W / 2);
            float b = smooth(.25f, .85f, broad(a - sweep)), second = smooth(.12f, .85f, secondary(a - shine));
            float diffuse = Math.max(0, s[3] * lx + s[4] * ly + s[5] * lz);
            float spec = (float)Math.pow(Math.max(0, s[3] * hx + s[4] * hy + s[5] * hz), 10);
            paint.setAlpha(Math.round(255 * (1 - (1 - b) * (1 - second)) * mix(.16f, .96f, diffuse * spec)));
            float x = s[0], y = s[1], r = s[2], t = r * .15f;
            star.rewind(); star.moveTo(x-r,y); star.lineTo(x-t,y-t); star.lineTo(x,y-r); star.lineTo(x+t,y-t);
            star.lineTo(x+r,y); star.lineTo(x+t,y+t); star.lineTo(x,y+r); star.lineTo(x-t,y+t); star.close(); canvas.drawPath(star,paint);
        }
        paint.setAlpha(255);
    }

    void drawQr(Canvas canvas) {
        int save = canvas.save(); clip.rewind(); clip.addRoundRect(qr, 8, 8, Path.Direction.CW); canvas.clipPath(clip);
        paint.setShader(qrGradient); canvas.drawRect(qr, paint); paint.setShader(null);
        bitmap(canvas, textures[4], qr, 55); bitmap(canvas, textures[5], qr, 95);
        if (qrBrush != null) {
            paint.setShader(qrBrush); paint.setAlpha(95); canvas.drawRect(qr, paint);
            paint.setShader(null); paint.setAlpha(255);
        }
        if (iconMask != null) {
            float recess = .35f + .65f * smooth(.05f, .42f, -(float)Math.sin(Math.toRadians(pitch)));
            paint.setColorFilter(new PorterDuffColorFilter(0xff24313c, PorterDuff.Mode.SRC_IN));
            bitmap(canvas, iconMask, qr, Math.round(255 * (.10f + .495f * recess))); paint.setColorFilter(null);
            float direction = (float)((shine / (Math.PI * 2) % 1 + 1) % 1 * ICON_DIRECTIONS);
            int index = (int)direction; float fraction = direction - index;
            for (int j = 0; j < 2; j++) {
                float weight = j == 0 ? 1 - fraction : fraction;
                bitmap(canvas, iconDark[(index + j) % ICON_DIRECTIONS], qr, Math.round(204 * weight));
                bitmap(canvas, iconLight[(index + j) % ICON_DIRECTIONS], qr, Math.round(248 * weight));
            }
        }
        float ux = (float)Math.cos(shine - .7), uy = (float)Math.sin(shine - .7);
        stroke.setShader(new LinearGradient(qr.centerX()-ux*24,qr.centerY()-uy*18,qr.centerX()+ux*24,qr.centerY()+uy*18,
                new int[]{0x6c101926,0x0fffffff,0xa6ffffff},new float[]{0,.45f,1},Shader.TileMode.CLAMP));
        stroke.setStrokeWidth(.7f); inset.set(qr); inset.inset(.35f,.35f); canvas.drawRoundRect(inset,7.65f,7.65f,stroke);
        canvas.restoreToCount(save);
    }

    void drawRim(Canvas canvas) {
        double a = -Math.PI * .65 + Math.toRadians(yaw * 3.6 - pitch * 3.2);
        float dx=(float)Math.cos(a),dy=(float)Math.sin(a),span=(Math.abs(dx)*W+Math.abs(dy)*H)*.5f;
        for (int i=0;i<3;i++) {
            float dark=i==0?.66f:i==1?.80f:.92f, bright=i==0?.40f:i==1?.24f:.10f;
            int low=Color.rgb(Math.round(Color.red(rimDark)*dark),Math.round(Color.green(rimDark)*dark),Math.round(Color.blue(rimDark)*dark));
            int mid=ColorUtils.blendARGB(rimDark,rimLight,.45f), high=ColorUtils.blendARGB(rimLight,Color.WHITE,bright);
            stroke.setShader(new LinearGradient(W/2-dx*span,H/2-dy*span,W/2+dx*span,H/2+dy*span,new int[]{low,low,mid,high,high},new float[]{0,.32f,.53f,.76f,1},Shader.TileMode.CLAMP));
            float b=i==0?.30f:i==1?.88f:1.47f; stroke.setStrokeWidth(i==0?.60f:i==1?1:.43f);
            inset.set(b,b,W-b,H-b);canvas.drawRoundRect(inset,R-b,R-b,stroke);
        }
        float right=clamp((float)Math.sin(Math.toRadians(yaw))*1.7f),left=clamp(-(float)Math.sin(Math.toRadians(yaw))*1.7f);
        int silver=ColorUtils.blendARGB(rimLight,Color.WHITE,.38f);
        stroke.setShader(new LinearGradient(0,H/2,W,H/2,new int[]{alpha(silver,left*.8f),alpha(silver,left*.38f),alpha(silver,0),alpha(silver,0),alpha(silver,right*.38f),alpha(silver,right*.8f)},new float[]{0,.15f,.34f,.66f,.85f,1},Shader.TileMode.CLAMP));
        stroke.setStrokeWidth(1.05f);inset.set(.95f,.95f,W-.95f,H-.95f);canvas.drawRoundRect(inset,R-.95f,R-.95f,stroke);stroke.setShader(null);
    }

    void updateDepthProjection(WalletCardProjection projection, int width, int height, boolean backFacing) {
        depthBackFacing = backFacing;
        // These drawing buffers represent the visible and hidden faces, not
        // permanently the model's front and back. The face drawn afterward must
        // cover the near ends of the strips, including beyond a half turn.
        projection.projectDepthContour(width, height, depthUv,
                backFacing ? depthBack : depthFront,
                backFacing ? depthFront : depthBack, depthZ);
        depthScale = width / W;
        for (int i = 0; i < DEPTH_POINTS; i++) {
            stripZ[i] = (depthZ[i] + depthZ[(i + 1) % DEPTH_POINTS]) * .5f;
            depthOrder[i] = i;
        }
        // Far to near, including at edge-on poses; fixed-size insertion sort allocates nothing.
        for (int i = 1; i < DEPTH_POINTS; i++) {
            int value = depthOrder[i], j = i - 1;
            while (j >= 0 && stripZ[depthOrder[j]] < stripZ[value]) {
                depthOrder[j + 1] = depthOrder[j]; j--;
            }
            depthOrder[j + 1] = value;
        }
    }

    void drawDepth(Canvas canvas, float[] style) {
        if (depthScale == 0) return;
        // A face-on card exposes no edge; strokes would only leave a subpixel outline around the face.
        if (Math.abs(Math.sin(Math.toRadians(pitch))) < FLAT_DEPTH_SIN && Math.abs(Math.sin(Math.toRadians(yaw))) < FLAT_DEPTH_SIN) return;
        int dark = WalletGiftPalette.card2DEdgeTone(0xff088fee, style);
        int light = WalletGiftPalette.card2DEdgeTone(0xff16abf9, style);
        paint.setShader(null); paint.setAlpha(255);
        paint.setColor(depthColor(dark, light, .55f * EDGE_BRIGHTNESS, 0, 0));
        depthPath.rewind(); depthPath.moveTo(depthBack[0], depthBack[1]);
        for (int i = 1; i < DEPTH_POINTS; i++) depthPath.lineTo(depthBack[i * 2], depthBack[i * 2 + 1]);
        depthPath.close(); canvas.drawPath(depthPath, paint);
        stroke.setShader(null); stroke.setAlpha(255); stroke.setStrokeWidth(.6f * depthScale);
        for (int index : depthOrder) {
            int next = (index + 1) % DEPTH_POINTS, p = index * 2, q = next * 2;
            float nx = (depthNormals[p] + depthNormals[q]) * .5f;
            float ny = (depthNormals[p + 1] + depthNormals[q + 1]) * .5f;
            float inv = 1 / (float)Math.sqrt(nx * nx + ny * ny + .12f * .12f);
            nx *= inv; ny *= inv; float nz = (depthBackFacing ? -.12f : .12f) * inv;
            float diffuse = clamp(nx * lx + ny * ly + nz * lz);
            float specular = (float)Math.pow(clamp(nx * hx + ny * hy + nz * hz), 24);
            float edgeDiffuse = (.36f + .38f * diffuse) * EDGE_BRIGHTNESS;
            float edgeAmbient = .06f * EDGE_BRIGHTNESS;
            float edgeSpecular = .10f * specular * EDGE_BRIGHTNESS;
            int color = depthColor(dark, light, edgeDiffuse, edgeAmbient, edgeSpecular);
            paint.setColor(color); stroke.setColor(color);
            depthPath.rewind(); depthPath.moveTo(depthFront[p], depthFront[p + 1]);
            depthPath.lineTo(depthFront[q], depthFront[q + 1]);
            depthPath.lineTo(depthBack[q], depthBack[q + 1]);
            depthPath.lineTo(depthBack[p], depthBack[p + 1]); depthPath.close();
            stroke.setStrokeWidth(.6f * depthScale);
            canvas.drawPath(depthPath, paint); canvas.drawPath(depthPath, stroke);
            // Web depth pass finishes each strip with a 75%-brightness rear seam.
            stroke.setColor(depthColor(dark, light, edgeDiffuse * .75f, edgeAmbient * .75f, edgeSpecular * .75f));
            stroke.setStrokeWidth(.7f * depthScale);
            canvas.drawLine(depthBack[p], depthBack[p + 1], depthBack[q], depthBack[q + 1], stroke);
        }
    }

    private static int depthColor(int dark, int light, float diffuse, float ambient, float specular) {
        return Color.rgb(Math.min(255, Math.round(Color.red(dark) * diffuse + Color.red(light) * ambient + 255 * specular)),
                Math.min(255, Math.round(Color.green(dark) * diffuse + Color.green(light) * ambient + 255 * specular)),
                Math.min(255, Math.round(Color.blue(dark) * diffuse + Color.blue(light) * ambient + 255 * specular)));
    }

    void setIcon(int res) {
        if (res == iconRes) return;
        iconRes = res;
        Bitmap[] cached = ICON_CACHE.get(res);
        if (cached != null) {
            installIcon(cached);
            return;
        }
        ASSET_QUEUE.postRunnable(() -> {
            Bitmap[] decoded = loadIcon(context, res);
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                if (iconRes != res) return;
                installIcon(decoded);
                invalidate.run();
            });
        });
    }

    private void installIcon(Bitmap[] baked) {
        if (baked == null) return;
        iconMask = baked[0];
        System.arraycopy(baked, 1, iconLight, 0, ICON_DIRECTIONS);
        System.arraycopy(baked, 1 + ICON_DIRECTIONS, iconDark, 0, ICON_DIRECTIONS);
    }

    private static Bitmap[] loadIcon(Context context, int res) {
        Bitmap[] cached = ICON_CACHE.get(res);
        if (cached != null) return cached;
        String name;
        if (res == org.telegram.messenger.R.drawable.wallet_card_qr) name = "qr";
        else if (res == org.telegram.messenger.R.drawable.wallet_card_info) name = "info";
        else throw new IllegalArgumentException("Missing packaged wallet icon: " + res);
        Bitmap[] decoded = new Bitmap[1 + 2 * ICON_DIRECTIONS];
        for (int i = 0; i < decoded.length; i++) {
            String suffix = i == 0 ? "mask" : i <= ICON_DIRECTIONS ? "light_" + (i - 1) : "dark_" + (i - 1 - ICON_DIRECTIONS);
            try (InputStream input = context.getAssets().open("wallet_card_2d/icon_" + name + "_" + suffix + ".webp")) {
                decoded[i] = BitmapFactory.decodeStream(input);
                if (decoded[i] == null) throw new IllegalStateException("Invalid wallet icon texture");
                decoded[i].prepareToDraw();
            } catch (java.io.IOException e) { throw new IllegalStateException("Missing wallet icon texture", e); }
        }
        ICON_CACHE.put(res, decoded);
        return decoded;
    }

    private void bitmap(Canvas c,Bitmap b,RectF dest,int alpha){if(b==null)return;paint.setAlpha(alpha);c.drawBitmap(b,null,dest,paint);paint.setAlpha(255);}
    private static int alpha(int c,float a){return ColorUtils.setAlphaComponent(c,Math.round(clamp(a)*255));}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    private static float smooth(float a,float b,float x){float t=clamp((x-a)/(b-a));return t*t*(3-2*t);}
    private static float broad(double a){double c=Math.cos(a);return (float)Math.pow(Math.abs(c),c>=0?2/.649:5);}
    private static float secondary(double a){return (float)Math.pow(1+Math.pow(Math.sin(a)/(.32*SECOND_LIGHT_WIDTH),2),-2);}
    private static float studio(float x,float y,float z){return mix(.061f,.356f,smooth(-.45f,.72f,y))+.2556f*(float)Math.pow(Math.max(0,y*.2191+z*.9757),3.365)+.1133f*(float)Math.pow(Math.max(0,y*.821+z*.571),4.65)+2.5191f*(float)Math.pow(Math.max(0,-x*.52+y*.46+z*.72),16.55)+.7832f*(float)Math.pow(Math.max(0,x*.72+y*.12+z*.68),31.15);}
}
