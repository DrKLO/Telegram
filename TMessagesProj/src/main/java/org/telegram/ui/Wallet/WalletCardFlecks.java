package org.telegram.ui.Wallet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;

/** Batched star quads with small mip-aligned normal tiles; no full-size flecks map. */
final class WalletCardFlecks {
    static final int TILE_SIZE = 48;
    private static final int STRIDE = 8 * 4;
    private final int tilesTexture;
    private final int tailTexture;
    private final int vertexBuffer;
    private final int vertexCount;
    private static final int FLECKS_DESIGN_WIDTH = 512;
    private static final int FLECKS_DESIGN_HEIGHT = 512;
    private static final float FLECKS_CARD_DESIGN_HEIGHT = 312.5f;
    private static final int FLECKS_POINT_COUNT = 16;
    private static final int FLECKS_PLACEMENT_CANDIDATES = 32;
    private static final float FLECKS_MIN_RADIUS = 1f;
    private static final float FLECKS_MAX_RADIUS = 7.0f;
    private static final float FLECKS_MIN_NORMAL_TILT = 0.05f;
    private static final float FLECKS_MAX_NORMAL_TILT = 0.5f;
    private static final float FLECKS_NORMAL_GRADIENT_SPREAD = 0.6f;
    private static final float FLECKS_STAR_INSET = 0.85f;
    private static final long FLECKS_RANDOM_SEED = 0x57414C4C45544CL;


    WalletCardFlecks() {
        ArrayList<Rect> regions = new ArrayList<>();
        ArrayList<Star> stars = createStars(regions);
        int[] textures = new int[3];
        GLES20.glGenTextures(3, textures, 0);
        int scratchTexture = textures[0];
        tilesTexture = textures[1];
        tailTexture = textures[2];

        GLES20.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tilesTexture);
        textureParameters(GLES30.GL_TEXTURE_2D_ARRAY);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAX_LEVEL, 3);
        GLES30.glTexStorage3D(GLES30.GL_TEXTURE_2D_ARRAY, 4, GLES30.GL_RGBA8,
                TILE_SIZE, TILE_SIZE, regions.size());
        Bitmap tile = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888);
        ByteBuffer pixels = ByteBuffer.allocateDirect(TILE_SIZE * TILE_SIZE * 4);
        for (int layer = 0; layer < regions.size(); layer++) {
            Rect region = regions.get(layer);
            paintTile(tile, region.left, region.top, stars);
            pixels.clear();
            tile.copyPixelsToBuffer(pixels);
            pixels.position(0);
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer,
                    TILE_SIZE, TILE_SIZE, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
        }
        tile.recycle();
        GLES20.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tailTexture);
        textureParameters(GLES20.GL_TEXTURE_2D);
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 8, GLES30.GL_RGBA8, 128, 128);
        int[] framebuffer = new int[1], previousFramebuffer = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFramebuffer, 0);
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        // Preserve distant/steep-angle filtering with a small mip tail. Build it
        // in 64px blocks on the original pixel grid, never allocating a 512px
        // bitmap or texture. Level-2 reduction is local to each aligned block.
        Bitmap scratch = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scratchTexture);
        textureParameters(GLES20.GL_TEXTURE_2D);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAX_LEVEL, 2);
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 3, GLES30.GL_RGBA8, 64, 64);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, scratchTexture, 2);
        for (int y = 0; y < FLECKS_DESIGN_HEIGHT; y += 64) {
            for (int x = 0; x < FLECKS_DESIGN_WIDTH; x += 64) {
                paintTile(scratch, x, y, stars);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scratchTexture);
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, scratch);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tailTexture);
                GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0,
                        x / 4, y / 4, 0, 0, 16, 16);
            }
        }
        scratch.recycle();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFramebuffer[0]);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glDeleteFramebuffers(1, framebuffer, 0);
        GLES20.glDeleteTextures(1, new int[]{scratchTexture}, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0);

        vertexCount = regions.size() * 6;
        FloatBuffer vertices = ByteBuffer.allocateDirect(vertexCount * STRIDE)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        int[] corners = {0, 1, 2, 2, 1, 3};
        for (int layer = 0; layer < regions.size(); layer++) {
            Rect region = regions.get(layer);
            for (int corner : corners) {
                float cornerX = (corner & 1) == 0 ? -1.0f : 1.0f;
                float cornerY = (corner & 2) == 0 ? -1.0f : 1.0f;
                float px = cornerX < 0 ? region.left : region.right;
                float py = cornerY < 0 ? region.top : region.bottom;
                vertices.put(-WalletCardGeometry.HALF_THICKNESS);
                vertices.put((0.5f - py / FLECKS_CARD_DESIGN_HEIGHT) * 1.219512f);
                vertices.put(px / FLECKS_DESIGN_WIDTH * 2.0f - 1.0f);
                vertices.put(region.left).put(region.top).put(layer);
                vertices.put(cornerX).put(cornerY);
            }
        }
        vertices.position(0);
        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        vertexBuffer = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * STRIDE, vertices, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    void draw(int position, int normal, int region, int corner) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tilesTexture);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tailTexture);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
        if (normal >= 0) {
            GLES20.glDisableVertexAttribArray(normal);
            GLES20.glVertexAttrib3f(normal, -1.0f, 0.0f, 0.0f);
        }
        GLES20.glEnableVertexAttribArray(region);
        GLES20.glVertexAttribPointer(region, 3, GLES20.GL_FLOAT, false, STRIDE, 3 * 4);
        GLES20.glEnableVertexAttribArray(corner);
        GLES20.glVertexAttribPointer(corner, 2, GLES20.GL_FLOAT, false, STRIDE, 6 * 4);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        GLES20.glDisableVertexAttribArray(region);
        GLES20.glDisableVertexAttribArray(corner);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0);
    }

    void destroy() {
        GLES20.glDeleteTextures(2, new int[]{tilesTexture, tailTexture}, 0);
        GLES20.glDeleteBuffers(1, new int[]{vertexBuffer}, 0);
    }

    private static void textureParameters(int target) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private static final class Star {
        final Path path = new Path();
        final LinearGradient gradient;

        Star(float x, float y, float radius, float gradientX, float gradientY, int colorA, int colorB) {
            setParticleStarPath(path, x, y, radius);
            gradient = new LinearGradient(x - gradientX, y - gradientY,
                    x + gradientX, y + gradientY, colorA, colorB, Shader.TileMode.CLAMP);
        }
    }

    private static void paintTile(Bitmap bitmap, int left, int top, ArrayList<Star> stars) {
        bitmap.eraseColor(0);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-left, -top);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Star star : stars) {
            paint.setShader(star.gradient);
            canvas.drawPath(star.path, paint);
        }
    }

    private static ArrayList<Star> createStars(ArrayList<Rect> regions) {
        final int width = FLECKS_DESIGN_WIDTH;
        final float height = FLECKS_CARD_DESIGN_HEIGHT;
        ArrayList<Star> stars = new ArrayList<>();
        Random random = new Random(FLECKS_RANDOM_SEED);
        float[] pointCentersX = new float[FLECKS_POINT_COUNT];
        float[] pointCentersY = new float[FLECKS_POINT_COUNT];

        for (int point = 0; point < FLECKS_POINT_COUNT; point++) {
            float centerX = 0.0f;
            float centerY = 0.0f;
            float bestDistanceSquared = -1.0f;
            int candidateCount = point == 0
                    ? 1
                    : FLECKS_PLACEMENT_CANDIDATES;
            for (int candidate = 0; candidate < candidateCount; candidate++) {
                float candidateX = random.nextFloat() * width;
                float candidateY = random.nextFloat() * height;
                float nearestDistanceSquared = Float.MAX_VALUE;
                for (int previous = 0; previous < point; previous++) {
                    float distanceX = Math.abs(
                            candidateX - pointCentersX[previous]
                    );
                    float distanceY = Math.abs(
                            candidateY - pointCentersY[previous]
                    );
                    distanceX = Math.min(distanceX, width - distanceX);
                    distanceY = Math.min(distanceY, height - distanceY);
                    nearestDistanceSquared = Math.min(
                            nearestDistanceSquared,
                            distanceX * distanceX + distanceY * distanceY
                    );
                }
                if (nearestDistanceSquared > bestDistanceSquared) {
                    bestDistanceSquared = nearestDistanceSquared;
                    centerX = candidateX;
                    centerY = candidateY;
                }
            }
            pointCentersX[point] = centerX;
            pointCentersY[point] = centerY;
            float radius = FLECKS_MIN_RADIUS
                    + random.nextFloat()
                    * (FLECKS_MAX_RADIUS - FLECKS_MIN_RADIUS);
            float angle = random.nextFloat() * (float) (Math.PI * 2.0);
            float tilt = FLECKS_MIN_NORMAL_TILT
                    + random.nextFloat()
                    * (FLECKS_MAX_NORMAL_TILT - FLECKS_MIN_NORMAL_TILT);
            float directionX = (float) Math.cos(angle);
            float directionY = (float) Math.sin(angle);
            float gradientDirectionX = -directionY;
            float gradientDirectionY = directionX;
            float normalX = directionX * tilt;
            float normalY = directionY * tilt;
            float gradientNormalX = gradientDirectionX
                    * FLECKS_NORMAL_GRADIENT_SPREAD;
            float gradientNormalY = gradientDirectionY
                    * FLECKS_NORMAL_GRADIENT_SPREAD;
            float normalAX = normalX + gradientNormalX;
            float normalAY = normalY + gradientNormalY;
            float normalBX = normalX - gradientNormalX;
            float normalBY = normalY - gradientNormalY;
            float normalAZ = (float) Math.sqrt(Math.max(
                    0.0f,
                    1.0f - normalAX * normalAX - normalAY * normalAY
            ));
            float normalBZ = (float) Math.sqrt(Math.max(
                    0.0f,
                    1.0f - normalBX * normalBX - normalBY * normalBY
            ));
            int normalColorA = 0xFF000000
                    | encodeNormal(normalAX) << 16
                    | encodeNormal(normalAY) << 8
                    | encodeNormal(normalAZ);
            int normalColorB = 0xFF000000
                    | encodeNormal(normalBX) << 16
                    | encodeNormal(normalBY) << 8
                    | encodeNormal(normalBZ);
            float gradientX = gradientDirectionX * radius;
            float gradientY = gradientDirectionY * radius;
            for (int tileY = -1; tileY <= 1; tileY++) {
                for (int tileX = -1; tileX <= 1; tileX++) {
                    float wrappedX = centerX + tileX * width;
                    float wrappedY = centerY + tileY * height;
                    if (wrappedX + radius > 0 && wrappedX - radius < width
                            && wrappedY + radius > 0 && wrappedY - radius < FLECKS_DESIGN_HEIGHT) {
                        // Align to the original mip grid through level 3, leaving
                        // at least one empty level-3 texel around each star.
                        int left = Math.max(0, Math.min(width - TILE_SIZE,
                                Math.round((wrappedX - TILE_SIZE * 0.5f) / 8.0f) * 8));
                        int top = Math.max(0, Math.min(FLECKS_DESIGN_HEIGHT - TILE_SIZE,
                                Math.round((wrappedY - TILE_SIZE * 0.5f) / 8.0f) * 8));
                        Rect region = new Rect(left, top, left + TILE_SIZE, top + TILE_SIZE);
                        if (!regions.contains(region)) regions.add(region);
                    }
                    stars.add(new Star(wrappedX, wrappedY, radius, gradientX, gradientY,
                            normalColorA, normalColorB));
                }
            }
        }
        return stars;
    }

    /** Matches the four-point path used by StarParticlesView.Drawable. */
    private static void setParticleStarPath(
            Path path,
            float centerX,
            float centerY,
            float radius
    ) {
        float inner = radius * (1.0f - FLECKS_STAR_INSET);
        path.rewind();
        path.moveTo(centerX - radius, centerY);
        path.lineTo(centerX - inner, centerY - inner);
        path.lineTo(centerX, centerY - radius);
        path.lineTo(centerX + inner, centerY - inner);
        path.lineTo(centerX + radius, centerY);
        path.lineTo(centerX + inner, centerY + inner);
        path.lineTo(centerX, centerY + radius);
        path.lineTo(centerX - inner, centerY + inner);
        path.close();
    }

    private static int encodeNormal(float component) {
        return Math.max(
                0,
                Math.min(255, Math.round((component * 0.5f + 0.5f) * 255.0f))
        );
    }

}
