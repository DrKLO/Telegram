package org.telegram.ui.Wallet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.Premium.GLIcon.GLProgramCache;
import org.telegram.ui.Components.Premium.GLIcon.ObjLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

final class WalletCard3D {

    private static final int FLOAT_BYTES = 4;
    private static final LruCache<String, Bitmap> detailBitmaps = new LruCache<>(2);
    private MaterialProgram frontContentProgram;
    private int frontContentTexture;
    private int frontContentWidth, frontContentHeight;
    private MaterialProgram diamondProgram;
    private int diamondTexture;
    private float[] diamondBounds;
    private float diamondAlpha = 1f;
    private final int[] diamondScissor = new int[4];
    private final Context context;

    private final int finishLut;
    private final MaterialProgram bodyProgram;
    private final MaterialProgram engravingProgram;
    private final int[] engravingScissor = new int[4];
    private final float[] engravingUvBounds = new float[4];
    private boolean hasEngraving;
    private static final int ENGRAVING_WIDTH = 2048, ENGRAVING_HEIGHT = 1024;
    private final MaterialProgram qrProgram;
    private final MaterialProgram edgeProgram;
    private final Mesh frontMesh;
    private final Mesh edgeMesh;
    private int cardDetailTexture;
    private final int engravingTexture;
    private final WalletCardFlecks flecks;
    private final MaterialProgram flecksProgram;
    private final float[] uvFootprint = new float[2];
    private final float qrButtonRightPadding;
    private final int[] qrScissor = new int[4];

    WalletCard3D(
            Context context,
            int cardIconResId,
            int qrButtonRightPadding
    ) {
        this.context = context.getApplicationContext();
        this.qrButtonRightPadding = qrButtonRightPadding;
        String vertexSource = readAsset(context, "shaders/wallet_card_vertex.glsl");
        String fragmentSource = readAsset(context, "shaders/wallet_card_fragment.glsl");
        finishLut = createFinishLut(context, fragmentSource);
        String finishDefine = finishLut != 0 ? "#define WALLET_FINISH_LUT\n" : "";
        bodyProgram = new MaterialProgram(context, vertexSource, fragmentSource, "body", finishDefine + "#define WALLET_BODY_PASS\n");
        engravingProgram = new MaterialProgram(context, vertexSource, fragmentSource, "engraving", finishDefine + "#define WALLET_ENGRAVING_PASS\n");
        qrProgram = new MaterialProgram(context, vertexSource, fragmentSource, "qr", finishDefine + "#define WALLET_QR_PASS\n");
        edgeProgram = new MaterialProgram(context, vertexSource, fragmentSource, "edge", finishDefine + "#define WALLET_EDGE_PASS\n");
        flecksProgram = new MaterialProgram(context, vertexSource, fragmentSource, "flecks", finishDefine + "#define WALLET_FLECKS_PASS\n");

        cardDetailTexture = createCardDetailTexture(
                context,
                cardIconResId,
                qrButtonRightPadding
        );
        engravingTexture = createEmptyTexture();
        flecks = new WalletCardFlecks();

        frontMesh = new Mesh(context, "models/card_back.binobj");
        edgeMesh = new Mesh(context, "models/card_edge.binobj");
    }

    void setDiamondBounds(float[] bounds, float alpha) {
        diamondBounds = bounds;
        diamondAlpha = alpha;
    }

    void draw(
            float[] mvpMatrix,
            float[] modelViewMatrix,
            float lightDirectionX,
            float lightDirectionY,
            float lightDirectionZ,
            float cardGradientRotation,
            int surfaceWidth,
            int surfaceHeight
    ) {
        bodyProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, finishLut);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cardDetailTexture);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, engravingTexture);

        drawMesh(frontMesh, bodyProgram);

        if (hasEngraving) {
            engravingProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                    lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
            WalletCardQrBounds.compute(mvpMatrix, surfaceWidth, surfaceHeight,
                    engravingUvBounds[0], engravingUvBounds[1], engravingUvBounds[2], engravingUvBounds[3],
                    ENGRAVING_WIDTH, ENGRAVING_HEIGHT, true, engravingScissor, uvFootprint);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(engravingScissor[0], engravingScissor[1], engravingScissor[2], engravingScissor[3]);
            GLES20.glDepthFunc(GLES20.GL_EQUAL);
            GLES20.glDepthMask(false);
            drawMesh(frontMesh, engravingProgram);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glDepthMask(true);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        }

        qrProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
        WalletCardQrBounds.compute(mvpMatrix, surfaceWidth, surfaceHeight,
                (CARD_DESIGN_WIDTH - qrButtonRightPadding - QR_BUTTON_WIDTH) / CARD_DESIGN_WIDTH,
                QR_BUTTON_TOP / CARD_DESIGN_HEIGHT,
                (CARD_DESIGN_WIDTH - qrButtonRightPadding) / CARD_DESIGN_WIDTH,
                QR_BUTTON_BOTTOM / CARD_DESIGN_HEIGHT, qrScissor, uvFootprint);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(qrScissor[0], qrScissor[1], qrScissor[2], qrScissor[3]);
        GLES20.glDepthFunc(GLES20.GL_EQUAL);
        GLES20.glDepthMask(false);
        drawMesh(frontMesh, qrProgram);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        flecksProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
        GLES20.glUniform2f(flecksProgram.flecksPaddingUniform,
                Math.min(512.0f, uvFootprint[0] * 512.0f * 4.0f + 8.0f),
                Math.min(512.0f, uvFootprint[1] * 312.5f * 4.0f + 8.0f));
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
        GLES20.glPolygonOffset(1.0f, 2.0f);
        GLES20.glDepthFunc(GLES20.GL_GREATER);
        GLES20.glDepthMask(false);
        flecks.draw(flecksProgram.positionAttribute, flecksProgram.normalAttribute,
                flecksProgram.flecksRegionAttribute, flecksProgram.flecksCornerAttribute);
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);
        GLES20.glDepthMask(true);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        if (diamondAlpha > 0f && diamondBounds != null && diamondBounds[2] > 0 && diamondBounds[3] > 0) {
            if (diamondProgram == null) {
                diamondProgram = new MaterialProgram(context,
                        readAsset(context, "shaders/wallet_card_vertex.glsl"),
                        readAsset(context, "shaders/wallet_card_diamond_fragment.glsl"), "diamond", "");
                diamondTexture = createEmptyTexture();
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, diamondTexture);
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.wallet_card_diamond);
                setMipmappedTextureParameters();
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
                bitmap.recycle();
            }
            diamondProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                    lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
            GLES20.glUniform4fv(diamondProgram.diamondBoundsUniform, 1, diamondBounds, 0);
            GLES20.glUniform1f(diamondProgram.diamondAlphaUniform, diamondAlpha);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, diamondTexture);
            float top = 0.5f - (WalletCardGeometry.TOP
                    - diamondBounds[1] * (WalletCardGeometry.TOP - WalletCardGeometry.BOTTOM)) / 1.219512f;
            float height = diamondBounds[3] * (WalletCardGeometry.TOP - WalletCardGeometry.BOTTOM) / 1.219512f;
            WalletCardQrBounds.compute(mvpMatrix, surfaceWidth, surfaceHeight,
                    diamondBounds[0], top, diamondBounds[0] + diamondBounds[2], top + height,
                    diamondScissor, uvFootprint);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(diamondScissor[0], diamondScissor[1], diamondScissor[2], diamondScissor[3]);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthFunc(GLES20.GL_EQUAL);
            GLES20.glDepthMask(false);
            drawMesh(frontMesh, diamondProgram);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glDepthMask(true);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        }

        if (frontContentTexture != 0) {
            frontContentProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                    lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frontContentTexture);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthFunc(GLES20.GL_EQUAL);
            GLES20.glDepthMask(false);
            drawMesh(frontMesh, frontContentProgram);
            GLES20.glDepthMask(true);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glDisable(GLES20.GL_BLEND);
        }

        edgeProgram.bind(mvpMatrix, modelViewMatrix, lightDirectionX, lightDirectionY,
                lightDirectionZ, cardGradientRotation, qrButtonRightPadding);
        drawMesh(edgeMesh, edgeProgram);

        if (bodyProgram.positionAttribute >= 0) {
            GLES20.glDisableVertexAttribArray(bodyProgram.positionAttribute);
        }
        if (bodyProgram.normalAttribute >= 0) {
            GLES20.glDisableVertexAttribArray(bodyProgram.normalAttribute);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    void setEngravingBitmap(Bitmap bitmap) {
        final int mipWidth = ENGRAVING_WIDTH;
        final int mipHeight = ENGRAVING_HEIGHT;
        Bitmap mipBitmap = Bitmap.createBitmap(
                mipWidth,
                mipHeight,
                Bitmap.Config.ARGB_8888
        );
        Canvas mipCanvas = new Canvas(mipBitmap);
        Paint mipPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mipCanvas.drawBitmap(
                bitmap,
                null,
                new RectF(0.0f, 0.0f, mipWidth, mipHeight),
                mipPaint
        );

        int left = mipWidth, top = mipHeight, right = -1, bottom = -1;
        int[] row = new int[mipWidth];
        for (int y = 0; y < mipHeight; y++) {
            mipBitmap.getPixels(row, 0, mipWidth, 0, y, mipWidth, 1);
            for (int x = 0; x < mipWidth; x++) {
                if ((row[x] >>> 24) != 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = y;
                }
            }
        }
        hasEngraving = right >= left;
        if (hasEngraving) {
            engravingUvBounds[0] = left / (float) mipWidth;
            engravingUvBounds[1] = top / (float) mipHeight;
            engravingUvBounds[2] = (right + 1) / (float) mipWidth;
            engravingUvBounds[3] = (bottom + 1) / (float) mipHeight;
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, engravingTexture);
        setMipmappedTextureParameters();
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mipBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        engravingProgram.setEngravingTexel(1.0f / mipWidth, 1.0f / mipHeight);
        bodyProgram.setEngravingTexel(1.0f / mipWidth, 1.0f / mipHeight);
        qrProgram.setEngravingTexel(1.0f / mipWidth, 1.0f / mipHeight);
        flecksProgram.setEngravingTexel(1.0f / mipWidth, 1.0f / mipHeight);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        mipBitmap.recycle();
    }

    void setCardIcon(Context context, int drawableResId, int qrButtonRightPadding) {
        int previousTexture = cardDetailTexture;
        cardDetailTexture = createCardDetailTexture(
                context,
                drawableResId,
                qrButtonRightPadding
        );
        GLES20.glDeleteTextures(1, new int[]{previousTexture}, 0);
    }

    void setFrontBitmap(Bitmap bitmap) {
        if (frontContentProgram == null) {
            frontContentProgram = new MaterialProgram(context,
                    readAsset(context, "shaders/wallet_card_vertex.glsl"),
                    readAsset(context, "shaders/wallet_card_content_fragment.glsl"), "content", "");
            frontContentTexture = createEmptyTexture();
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frontContentTexture);
        if (frontContentWidth != bitmap.getWidth() || frontContentHeight != bitmap.getHeight()) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            frontContentWidth = bitmap.getWidth();
            frontContentHeight = bitmap.getHeight();
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap);
        }
    }

    void destroy() {
        if (frontContentProgram != null) {
            GLES20.glDeleteProgram(frontContentProgram.program);
            GLES20.glDeleteTextures(1, new int[]{frontContentTexture}, 0);
        }
        if (diamondProgram != null) {
            GLES20.glDeleteProgram(diamondProgram.program);
            GLES20.glDeleteTextures(1, new int[]{diamondTexture}, 0);
        }
        frontMesh.destroy();
        edgeMesh.destroy();
        GLES20.glDeleteTextures(1, new int[]{cardDetailTexture}, 0);
        GLES20.glDeleteTextures(1, new int[]{engravingTexture}, 0);
        GLES20.glDeleteTextures(1, new int[]{finishLut}, 0);
        flecks.destroy();
        GLES20.glDeleteProgram(bodyProgram.program);
        GLES20.glDeleteProgram(engravingProgram.program);
        GLES20.glDeleteProgram(qrProgram.program);
        GLES20.glDeleteProgram(edgeProgram.program);
        GLES20.glDeleteProgram(flecksProgram.program);
    }

    static void prepareTextures(Context context, int cardIconResId, int qrButtonRightPadding) {
        getCardDetailBitmap(context, cardIconResId, qrButtonRightPadding);
    }

    private static int createFinishLut(Context context, String fragmentSource) {
        int[] texture = new int[1], framebuffer = new int[1], previous = new int[1], viewport = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previous, 0);
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
        boolean depthTest = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST);
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA16F, 1024, 2);
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture[0], 0);
        int program = 0;
        try {
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                GLES20.glDeleteTextures(1, texture, 0);
                return 0;
            }
            String vertex = "#version 300 es\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}";
            program = GLProgramCache.create(context, "wallet-finish-lut", vertex,
                    MaterialProgram.specialize(fragmentSource, "#define WALLET_FINISH_BAKE_PASS\n"), "aPosition", "aNormal");
            GLES20.glUseProgram(program);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glViewport(0, 0, 1024, 2);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
            return texture[0];
        } catch (RuntimeException e) {
            GLES20.glDeleteTextures(1, texture, 0);
            FileLog.e(e);
            return 0;
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previous[0]);
            GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDeleteFramebuffers(1, framebuffer, 0);
            if (program != 0) GLES20.glDeleteProgram(program);
        }
    }

    private static int createEmptyTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        setTextureParameters();
        ByteBuffer emptyPixel = ByteBuffer.allocateDirect(4);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                1,
                1,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                emptyPixel
        );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture[0];
    }

    private static final float CARD_DESIGN_WIDTH = 336.0f;
    private static final float CARD_DESIGN_HEIGHT = 205.0f;
    private static final float QR_BUTTON_WIDTH = 50.0f;
    private static final float QR_BUTTON_TOP = 73.0f;
    private static final float QR_BUTTON_BOTTOM = 111.0f;
    private static final float QR_BUTTON_CENTER_Y =
            (QR_BUTTON_TOP + QR_BUTTON_BOTTOM) * 0.5f;
    private static final float QR_ICON_INSET_X = 13.0f;
    private static final float QR_ICON_INSET_Y = 7.0f;
    private static final float QR_BUTTON_CORNER_RADIUS = 9.0f;

    private static float qrCenterX(float qrButtonRightPadding) {
        return CARD_DESIGN_WIDTH
                - qrButtonRightPadding
                - QR_BUTTON_WIDTH * 0.5f;
    }

    private static int createCardDetailTexture(
            Context context,
            int cardIconResId,
            int qrButtonRightPadding
    ) {
        Bitmap bitmap = getCardDetailBitmap(context, cardIconResId, qrButtonRightPadding);
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        setTextureParameters();
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture[0];
    }

    private static synchronized Bitmap getCardDetailBitmap(Context context, int cardIconResId, int qrButtonRightPadding) {
        String key = cardIconResId + ":" + qrButtonRightPadding + ":" + context.getResources().getConfiguration().hashCode();
        Bitmap bitmap = detailBitmaps.get(key);
        if (bitmap == null) {
            bitmap = createCardDetailBitmap(context, cardIconResId, qrButtonRightPadding);
            detailBitmaps.put(key, bitmap);
        }
        return bitmap;
    }

    private static Bitmap createCardDetailBitmap(Context context, int cardIconResId, int qrButtonRightPadding) {
        final int width = 1024;
        final int height = 625;
        Bitmap bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(width / 336.0f, height / 205.0f);

        float buttonLeft = CARD_DESIGN_WIDTH - qrButtonRightPadding - QR_BUTTON_WIDTH;
        float buttonRight = CARD_DESIGN_WIDTH - qrButtonRightPadding;

        Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setColor(0xFFFF0000);
        canvas.drawRoundRect(
                new RectF(
                        buttonLeft,
                        QR_BUTTON_TOP,
                        buttonRight,
                        QR_BUTTON_BOTTOM
                ),
                QR_BUTTON_CORNER_RADIUS,
                QR_BUTTON_CORNER_RADIUS,
                buttonPaint
        );

        Drawable icon = ContextCompat.getDrawable(context, cardIconResId);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(0xFF00FF00);
            icon.setBounds(
                    (int) (buttonLeft + QR_ICON_INSET_X),
                    (int) (QR_BUTTON_TOP + QR_ICON_INSET_Y),
                    (int) (buttonRight - QR_ICON_INSET_X),
                    (int) (QR_BUTTON_BOTTOM - QR_ICON_INSET_Y)
            );
            icon.draw(canvas);
        }

        // Preserve the two masks while adding the same diagonal gray material
        // ramp used by qr-button-webgl.svg in the blue channel.
        float scaleX = width / 336.0f;
        float scaleY = height / 205.0f;
        // Everything outside the insert is already transparent. Process only
        // its bounds, including a two-pixel guard for antialiased coverage.
        int left = Math.max(0, (int) Math.floor(buttonLeft * scaleX) - 2);
        int top = Math.max(0, (int) Math.floor(QR_BUTTON_TOP * scaleY) - 2);
        int right = Math.min(width, (int) Math.ceil(buttonRight * scaleX) + 2);
        int bottom = Math.min(height, (int) Math.ceil(QR_BUTTON_BOTTOM * scaleY) + 2);
        if (right <= left || bottom <= top) return bitmap;
        int regionWidth = right - left, regionHeight = bottom - top;
        int[] pixels = new int[regionWidth * regionHeight];
        bitmap.getPixels(pixels, 0, regionWidth, left, top, regionWidth, regionHeight);
        float gradientX = 45.4f - 5.2f;
        float gradientY = 34.0f - 3.4f;
        float gradientLengthSquared =
                gradientX * gradientX + gradientY * gradientY;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int index = (y - top) * regionWidth + x - left;
                int source = pixels[index];
                // The composited RGB channels change around the glyph. Alpha
                // remains the accurate antialiased coverage of the button.
                int button = (source >>> 24) & 0xFF;
                int glyph = (source >>> 8) & 0xFF;
                if (button == 0) {
                    pixels[index] = 0;
                    continue;
                }
                float localX = x / scaleX - buttonLeft;
                float localY = y / scaleY - QR_BUTTON_TOP;
                float gradientPosition = (
                        (localX - 5.2f) * gradientX
                                + (localY - 3.4f) * gradientY
                ) / gradientLengthSquared;
                gradientPosition = Math.max(
                        0.0f,
                        Math.min(1.0f, gradientPosition)
                );
                float materialLuminance =
                        0.995f + (0.740f - 0.995f) * gradientPosition;
                materialLuminance *= 1.0f
                        - 0.50f * glyph / 255.0f;
                int material = Math.max(
                        0,
                        Math.min(255, Math.round(materialLuminance * 255.0f))
                );
                pixels[index] = button << 24
                        | button << 16
                        | glyph << 8
                        | material;
            }
        }
        bitmap.setPixels(pixels, 0, regionWidth, left, top, regionWidth, regionHeight);
        return bitmap;
    }

    private static void setTextureParameters() {
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
    }

    private static void setMipmappedTextureParameters() {
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
    }

    private static void drawQrFinder(
            Canvas canvas,
            Paint glyphPaint,
            Paint buttonPaint,
            float left,
            float top
    ) {
        final float offsetX = 241.0f;
        final float offsetY = 73.0f;
        canvas.drawRoundRect(
                new RectF(
                        offsetX + left,
                        offsetY + top,
                        offsetX + left + 8.3242f,
                        offsetY + top + 8.3242f
                ),
                2.44f,
                2.44f,
                glyphPaint
        );
        canvas.drawRoundRect(
                new RectF(
                        offsetX + left + 1.4668f,
                        offsetY + top + 1.4668f,
                        offsetX + left + 6.8574f,
                        offsetY + top + 6.8574f
                ),
                0.98f,
                0.98f,
                buttonPaint
        );
    }

    private static void drawQrModule(
            Canvas canvas,
            Paint glyphPaint,
            float left,
            float top
    ) {
        final float offsetX = 241.0f;
        final float offsetY = 73.0f;
        canvas.drawRoundRect(
                new RectF(
                        offsetX + left,
                        offsetY + top,
                        offsetX + left + 2.1425f,
                        offsetY + top + 2.1425f
                ),
                0.43f,
                0.43f,
                glyphPaint
        );
    }

    private void drawMesh(Mesh mesh, MaterialProgram material) {
        bindAttribute(material.positionAttribute, mesh.buffers[0], 3);
        bindAttribute(material.normalAttribute, mesh.buffers[1], 3);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);
    }

    private static void uploadBuffer(int buffer, float[] values) {
        FloatBuffer data = ByteBuffer.allocateDirect(values.length * FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        data.put(values).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                values.length * FLOAT_BYTES,
                data,
                GLES20.GL_STATIC_DRAW
        );
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private static void bindAttribute(int attribute, int buffer, int size) {
        // Material specialization can eliminate an attribute (edge normals).
        if (attribute < 0) return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
        GLES20.glEnableVertexAttribArray(attribute);
        GLES20.glVertexAttribPointer(
                attribute,
                size,
                GLES20.GL_FLOAT,
                false,
                0,
                0
        );
    }

    private static final class MaterialProgram {
        final int program;
        private final int positionAttribute;
        private final int normalAttribute;
        private final int mvpUniform;
        private final int modelViewUniform;
        private final int cardGradientRotationUniform;
        private final int cardDetailTextureUniform;
        private final int engravingTextureUniform;
        private final int engravingTexelUniform;
        private final int flecksRegionAttribute, flecksCornerAttribute, flecksPaddingUniform;
        private final int mainLightDirectionUniform;
        private final int qrCenterUniform;
        private final int diamondBoundsUniform;
        private final int diamondAlphaUniform;

        MaterialProgram(Context context, String vertexSource, String fragmentSource,
                        String material, String defines) {
            program = GLProgramCache.create(context, "wallet-card-" + material,
                    specialize(vertexSource, defines), specialize(fragmentSource, defines),
                    "aPosition", "aNormal");
            positionAttribute = GLES20.glGetAttribLocation(program, "aPosition");
            normalAttribute = GLES20.glGetAttribLocation(program, "aNormal");
            mvpUniform = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            modelViewUniform = GLES20.glGetUniformLocation(
                    program,
                    "uModelViewMatrix"
            );
            cardGradientRotationUniform = GLES20.glGetUniformLocation(program, "uCardGradientRotation");
            cardDetailTextureUniform = GLES20.glGetUniformLocation(
                    program,
                    "uCardDetailTexture"
            );
            engravingTextureUniform = GLES20.glGetUniformLocation(
                    program,
                    "uEngravingTexture"
            );
            engravingTexelUniform = GLES20.glGetUniformLocation(
                    program,
                    "uEngravingTexel"
            );
            flecksRegionAttribute = GLES20.glGetAttribLocation(program, "aFlecksRegion");
            flecksCornerAttribute = GLES20.glGetAttribLocation(program, "aFlecksCorner");
            flecksPaddingUniform = GLES20.glGetUniformLocation(program, "uFlecksPadding");
            mainLightDirectionUniform = GLES20.glGetUniformLocation(
                    program,
                    "uMainLightDirection"
            );
            qrCenterUniform = GLES20.glGetUniformLocation(
                    program,
                    "uQrCenter"
            );
            diamondBoundsUniform = GLES20.glGetUniformLocation(program, "uDiamondBounds");
            diamondAlphaUniform = GLES20.glGetUniformLocation(program, "uDiamondAlpha");
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uFinishLut"), 4);
            GLES20.glUniform1i(cardDetailTextureUniform, 0);
            GLES20.glUniform1i(engravingTextureUniform, 1);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uFlecksTiles"), 2);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uFlecksTail"), 3);
        }

        private static String specialize(String source, String defines) {
            int versionEnd = source.indexOf('\n') + 1;
            return source.substring(0, versionEnd) + defines + source.substring(versionEnd);
        }

        void setEngravingTexel(float x, float y) {
            GLES20.glUseProgram(program);
            GLES20.glUniform2f(engravingTexelUniform, x, y);
        }

        void bind(float[] mvpMatrix, float[] modelViewMatrix,
                  float lightDirectionX, float lightDirectionY, float lightDirectionZ,
                  float cardGradientRotation, float qrButtonRightPadding) {
            GLES20.glUseProgram(program);
            GLES20.glUniform1f(cardGradientRotationUniform, cardGradientRotation);
            GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(
                    modelViewUniform,
                    1,
                    false,
                    modelViewMatrix,
                    0
            );
            GLES20.glUniform3f(
                    mainLightDirectionUniform,
                    lightDirectionX,
                    lightDirectionY,
                    lightDirectionZ
            );
            GLES20.glUniform2f(
                    qrCenterUniform,
                    qrCenterX(qrButtonRightPadding) / CARD_DESIGN_WIDTH,
                    QR_BUTTON_CENTER_Y / CARD_DESIGN_HEIGHT
            );
        }
    }

    private static final class Mesh {
        final int vertexCount;
        final int[] buffers = new int[2];

        Mesh(Context context, String asset) {
            ObjLoader object = new ObjLoader(context, asset, 1.0f);
            vertexCount = object.numFaces;
            GLES20.glGenBuffers(buffers.length, buffers, 0);
            uploadBuffer(buffers[0], object.positions);
            uploadBuffer(buffers[1], object.normals);
        }

        void destroy() {
            GLES20.glDeleteBuffers(buffers.length, buffers, 0);
        }
    }

    private static String readAsset(Context context, String name) {
        try (
                InputStream input = context.getAssets().open(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + name, e);
        }
    }
}
