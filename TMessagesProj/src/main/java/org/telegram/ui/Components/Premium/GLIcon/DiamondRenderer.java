package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** GLES port of InteractiveDiamond2, including perspective and silhouette compensation. */
final class DiamondRenderer {
    private static final String MODEL = "models/diamond_ios/";
    private static final String SHADERS = "shaders/diamond/";
    private static final String[] LIGHTS = {"crownGradient", "pavilionGradient", "lightSweep",
            "crownSweep", "rightCrownSweep", "leftCrownSweep", "pavilionSweep",
            "rightPavilionSweep", "leftPavilionSweep"};
    private final int[] programs = new int[3];
    private final int[] vaos = new int[4], buffers = new int[3], counts = new int[3];
    private final int[] targets = new int[2], renderbuffers = new int[2], texture = new int[1];
    private final HashMap<String, Integer> locations = new HashMap<>();
    private final float[] model = new float[16], inverse = new float[16], projection = new float[16];
    private final float[] frame = new float[42];
    private float[] frames, planes, anchors, silhouetteWidths, facetProjection, camera;
    private float horizontalScale = 1;
    private int program, width, height, samples;
    private double clock;
    private float previousYaw, previousPitch;
    private boolean hasPreviousYaw;

    DiamondRenderer(Context context) {
        this(context, 4);
    }

    DiamondRenderer(Context context, int maxSamples) {
        try {
            String[] names = {"diamond", "sparkle", "copy"};
            for (int n = 0; n < names.length; n++) {
                String vertex = n == 2 ? "fullscreenVertex" : names[n] + "Vertex";
                programs[n] = DiamondProgram.create(context, source(context, vertex), source(context, names[n] + "Fragment"));
            }
            frames = data(context, "frames");
            planes = data(context, "planes");
            anchors = data(context, "anchors");
            silhouetteWidths = data(context, "widths");
            facetProjection = data(context, "facetProjection");
            camera = data(context, "camera");
            if (anchors.length != 64 || silhouetteWidths.length != 91 || facetProjection.length != 4 || camera.length != 3) {
                throw new IllegalStateException("Invalid Swift diamond camera data");
            }
            if (frames.length != 1441 * 42 || planes.length != 17 * 4) {
                throw new IllegalStateException("Invalid Swift diamond animation data");
            }
            GLES30.glGenVertexArrays(vaos.length, vaos, 0);
            GLES30.glGenBuffers(buffers.length, buffers, 0);
            String[] meshes = {"vertices", "main", "small"};
            for (int n = 0; n < meshes.length; n++) {
                float[] mesh = data(context, meshes[n]);
                int attributes = n == 0 ? 3 : 2;
                counts[n] = mesh.length / (attributes * 4);
                GLES30.glBindVertexArray(vaos[n]);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[n]);
                FloatBuffer floats = ByteBuffer.allocateDirect(mesh.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                floats.put(mesh).position(0);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.length * 4, floats, GLES30.GL_STATIC_DRAW);
                for (int a = 0; a < attributes; a++) {
                    GLES30.glEnableVertexAttribArray(a);
                    GLES30.glVertexAttribPointer(a, 4, GLES30.GL_FLOAT, false, attributes * 16, a * 16);
                }
            }
            GLES30.glGenFramebuffers(2, targets, 0);
            GLES30.glGenRenderbuffers(2, renderbuffers, 0);
            GLES30.glGenTextures(1, texture, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            int[] maximum = new int[1];
            GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, maximum, 0);
            samples = Math.min(maxSamples, maximum[0]);
        } catch (Exception e) {
            destroy();
            throw new IllegalStateException("Cannot load Swift diamond", e);
        }
    }

    private static byte[] read(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) != -1) out.write(chunk, 0, n);
            return out.toByteArray();
        }
    }

    private static String source(Context context, String name) throws Exception {
        return new String(read(context, SHADERS + name + ".glsl"), StandardCharsets.UTF_8);
    }

    private static float[] data(Context context, String name) throws Exception {
        byte[] bytes = read(context, MODEL + name + ".bin");
        float[] values = new float[bytes.length / 4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return values;
    }

    private void use(int value) { program = value; GLES30.glUseProgram(value); }
    private int loc(String name) {
        String key = program + ":" + name;
        Integer value = locations.get(key);
        if (value == null) { value = GLES30.glGetUniformLocation(program, name); locations.put(key, value); }
        return value;
    }
    private void vector(String name, float x, float y, float z, float w) {
        GLES30.glUniform4f(loc("u." + name), x, y, z, w);
    }
    private void matrix(String name, float[] values) {
        GLES30.glUniformMatrix4fv(loc("u." + name), 1, false, values, 0);
    }

    private void resize(int w, int h) {
        if (w == width && h == height) return;
        width = w; height = h;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targets[0]);
        for (int n = 0; n < 2; n++) {
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, renderbuffers[n]);
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples,
                    n == 0 ? GLES30.GL_RGBA8 : GLES30.GL_DEPTH_COMPONENT16, w, h);
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                    n == 0 ? GLES30.GL_COLOR_ATTACHMENT0 : GLES30.GL_DEPTH_ATTACHMENT,
                    GLES30.GL_RENDERBUFFER, renderbuffers[n]);
        }
        checkTarget();
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targets[1]);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, texture[0], 0);
        checkTarget();
    }

    private static void checkTarget() {
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete diamond framebuffer");
        }
    }

    private void projection(float aspect, float zoom) {
        float h = 1.52f / zoom * Math.max(1, 1 / aspect);
        Matrix.setIdentityM(projection, 0);
        projection[0] = 1 / (h * aspect);
        projection[5] = 1 / h;
        // Metal's perspective matrix; the vertex shader converts clip depth to GLES.
        float w0 = 1 / camera[2], wz = -w0 / camera[1];
        projection[0] *= horizontalScale;
        projection[9] = .12f / h * wz;
        projection[10] = -w0 / 6;
        projection[11] = wz;
        projection[13] = .12f / h * w0;
        projection[14] = .5f * w0;
        projection[15] = w0;
    }

    /** Same interpolated silhouette correction as Swift's DiamondSilhouette. */
    private float horizontalScale(float yaw, float pitch) {
        float quadrant = Math.abs((float) Math.IEEEremainder(yaw, Math.PI / 2));
        float sample = Math.min(90, quadrant * 360 / (float) Math.PI);
        int i = Math.min(89, (int) sample);
        float t = sample - i, a = silhouetteWidths[i], b = silhouetteWidths[i + 1];
        float da = i == 0 ? 0 : (b - silhouetteWidths[i - 1]) / 2;
        float db = i == 89 ? 0 : (silhouetteWidths[i + 2] - a) / 2;
        float width = a + t * (da + t * (3 * (b - a) - 2 * da - db + t * (2 * (a - b) + da + db)));
        float turn = (float) Math.sin(2 * quadrant);
        float target = silhouetteWidths[0] * (1 - .035f * turn * turn);
        float tilt = (float) ((Math.abs(Math.sin(pitch)) - Math.sin(.25)) / (Math.sin(.96) - Math.sin(.25)));
        return 1 + (target / width - 1) * (1 - smooth(tilt));
    }

    private void setup(int id, float time, float angle, float visibility) {
        use(id);
        matrix("model", model); matrix("inverseModel", inverse); matrix("projection", projection);
        vector("parameters", time, .72f, 1, 1);
        GLES30.glUniform4fv(loc("u.facetProjection"), 1, facetProjection, 0);
        vector("appearance", appearance, 0, 0, 0);
        vector("referenceCrownFlash", 0, 0, 0, 0);
        vector("referencePavilionFlash", 0, 0, 0, 0);
        for (int n = 0; n < 8; n++) {
            GLES30.glUniform4fv(loc("anchors[" + n + "].position"), 1, anchors, n * 8);
            GLES30.glUniform4fv(loc("anchors[" + n + "].normal"), 1, anchors, n * 8 + 4);
        }
        vector("viewport", width, height, 17, 0);
        GLES30.glUniform4fv(loc("u.sparkleShape"), 1, frame, 36);
        vector("sparkleHalo", frame[40], angle, horizontalScale, visibility);
        for (int n = 0; n < LIGHTS.length; n++) GLES30.glUniform4fv(loc("u." + LIGHTS[n]), 1, frame, n * 4);
        GLES30.glUniform4fv(loc("planes[0]"), 17, planes, 0);
    }

    float resolutionScale = 1f;
    int appearance;

    void draw(int w, int h, float yaw, float pitch, float roll, float dt, float alpha, float white) {
        draw(w, h, yaw, pitch, roll, dt, alpha, white, false);
    }

    /** Renders once into the resolved texture, ready for GPU composition or readback. */
    void drawToTexture(int side, float yaw, float pitch, float dt) {
        draw(side, side, yaw, pitch, 0, dt, 1, 0, true);
    }

    private void draw(int w, int h, float yaw, float pitch, float roll, float dt, float alpha, float white, boolean readback) {
        if (w <= 0 || h <= 0) return;
        dt = Math.max(0, Math.min(dt, .1f));
        clock += dt;
        float time = (float) clock;
        double sample = (clock * 240) % 1440;
        int index = (int) sample;
        float fraction = (float) (sample - index);
        for (int n = 0; n < frame.length; n++) {
            float a = frames[index * 42 + n];
            frame[n] = a + (frames[(index + 1) * 42 + n] - a) * fraction;
        }
        // Only these offscreen targets change during enlargement. The window's
        // buffer and its compositor transform stay independent of GL resolution.
        resize(Math.max(1, Math.round(w * resolutionScale)), Math.max(1, Math.round(h * resolutionScale)));
        pitch += camera[0];
        horizontalScale = horizontalScale(yaw, pitch);
        Matrix.setIdentityM(model, 0);
        // Apply screen-facing roll before pitch/yaw; positive matches View rotation.
        Matrix.rotateM(model, 0, -(float) Math.toDegrees(roll), 0, 0, 1);
        Matrix.rotateM(model, 0, (float) Math.toDegrees(pitch), 1, 0, 0);
        Matrix.rotateM(model, 0, (float) Math.toDegrees(yaw), 0, 1, 0);
        Matrix.transposeM(inverse, 0, model, 0);
        float best = -1, angle = 0;
        for (int n = 0; n < 4; n++) {
            float a = n * (float) Math.PI / 2;
            float facing = (float) (Math.cos(yaw + a) * Math.cos(pitch - camera[0]));
            if (facing > best) { best = facing; angle = a; }
        }
        float delta = (float) Math.atan2(Math.sin(yaw - previousYaw), Math.cos(yaw - previousYaw));
        float rate = hasPreviousYaw && dt > 0 ? (float) Math.hypot(delta, pitch - previousPitch) / dt : .596f;
        previousYaw = yaw; previousPitch = pitch; hasPreviousYaw = true;
        float visibility = smooth(1 - (float) Math.acos(Math.max(-1, Math.min(1, best))) / ((float) Math.PI / 15))
                * smooth((((float) Math.PI * 2 / 15) / Math.max(rate, .001f) - .06f) / .34f);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targets[0]);
        GLES30.glViewport(0, 0, width, height);
        GLES30.glDepthMask(true);
        GLES30.glClearColor(0, 0, 0, 0);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        // Preserve the requested 336px front-facing body width on a 512px canvas.
        projection((float) w / h, (336f / 512) * 1.52f / silhouetteWidths[0]);
        setup(programs[0], time, angle, visibility);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glBindVertexArray(vaos[0]);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, counts[0]);
        setup(programs[1], time, angle, visibility);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        for (int n = 1; n <= 2; n++) {
            GLES30.glBindVertexArray(vaos[n]);
            GLES30.glUniform1ui(loc("baseInstance"), n - 1);
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, counts[n], n == 1 ? 1 : 7);
        }
        // Resolve MSAA before fading the entire premultiplied group, including sparkles.
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, targets[0]);
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, targets[1]);
        GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
        if (readback) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targets[1]);
            GLES30.glBindVertexArray(0);
            return;
        }
        drawTexture(w, h, alpha, white);
    }

    /** Composites the last rendered image into the current EGL window surface. */
    void drawTexture(int w, int h, float alpha) {
        drawTexture(w, h, alpha, 0);
    }

    private void drawTexture(int w, int h, float alpha, float white) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, w, h);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        use(programs[2]);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
        GLES30.glUniform1i(loc("image"), 0);
        GLES30.glUniform1f(loc("opacity"), alpha);
        GLES30.glUniform1f(loc("white"), white);
        GLES30.glBindVertexArray(vaos[3]);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    private static float smooth(float value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
    }

    void destroy() {
        for (int p : programs) if (p != 0) GLES30.glDeleteProgram(p);
        GLES30.glDeleteBuffers(buffers.length, buffers, 0);
        GLES30.glDeleteVertexArrays(vaos.length, vaos, 0);
        GLES30.glDeleteTextures(texture.length, texture, 0);
        GLES30.glDeleteFramebuffers(targets.length, targets, 0);
        GLES30.glDeleteRenderbuffers(renderbuffers.length, renderbuffers, 0);
    }
}
