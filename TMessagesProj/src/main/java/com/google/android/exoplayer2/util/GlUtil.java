/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static android.opengl.GLU.gluErrorString;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import javax.microedition.khronos.egl.EGL10;

/** OpenGL ES utilities. */
@SuppressWarnings("InlinedApi") // GLES constants are used safely based on the API version.
public final class GlUtil {

  /** Thrown when an OpenGL error occurs. */
  public static final class GlException extends Exception {
    /** Creates an instance with the specified error message. */
    public GlException(String message) {
      super(message);
    }
  }

  /** Number of elements in a 3d homogeneous coordinate vector describing a vertex. */
  public static final int HOMOGENEOUS_COORDINATE_VECTOR_SIZE = 4;

  /** Length of the normalized device coordinate (NDC) space, which spans from -1 to 1. */
  public static final float LENGTH_NDC = 2f;

  public static final int[] EGL_CONFIG_ATTRIBUTES_RGBA_8888 =
      new int[] {
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_RED_SIZE, /* redSize= */ 8,
        EGL14.EGL_GREEN_SIZE, /* greenSize= */ 8,
        EGL14.EGL_BLUE_SIZE, /* blueSize= */ 8,
        EGL14.EGL_ALPHA_SIZE, /* alphaSize= */ 8,
        EGL14.EGL_DEPTH_SIZE, /* depthSize= */ 0,
        EGL14.EGL_STENCIL_SIZE, /* stencilSize= */ 0,
        EGL14.EGL_NONE
      };
  public static final int[] EGL_CONFIG_ATTRIBUTES_RGBA_1010102 =
      new int[] {
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_RED_SIZE, /* redSize= */ 10,
        EGL14.EGL_GREEN_SIZE, /* greenSize= */ 10,
        EGL14.EGL_BLUE_SIZE, /* blueSize= */ 10,
        EGL14.EGL_ALPHA_SIZE, /* alphaSize= */ 2,
        EGL14.EGL_DEPTH_SIZE, /* depthSize= */ 0,
        EGL14.EGL_STENCIL_SIZE, /* stencilSize= */ 0,
        EGL14.EGL_NONE
      };

  // https://www.khronos.org/registry/EGL/extensions/EXT/EGL_EXT_protected_content.txt
  private static final String EXTENSION_PROTECTED_CONTENT = "EGL_EXT_protected_content";
  // https://www.khronos.org/registry/EGL/extensions/KHR/EGL_KHR_surfaceless_context.txt
  private static final String EXTENSION_SURFACELESS_CONTEXT = "EGL_KHR_surfaceless_context";
  // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt
  private static final String EXTENSION_YUV_TARGET = "GL_EXT_YUV_target";

  private static final int[] EGL_WINDOW_SURFACE_ATTRIBUTES_NONE = new int[] {EGL14.EGL_NONE};

  /** Class only contains static methods. */
  private GlUtil() {}

  /** Bounds of normalized device coordinates, commonly used for defining viewport boundaries. */
  public static float[] getNormalizedCoordinateBounds() {
    return new float[] {
      -1, -1, 0, 1,
      1, -1, 0, 1,
      -1, 1, 0, 1,
      1, 1, 0, 1
    };
  }

  /** Typical bounds used for sampling from textures. */
  public static float[] getTextureCoordinateBounds() {
    return new float[] {
      0, 0, 0, 1,
      1, 0, 0, 1,
      0, 1, 0, 1,
      1, 1, 0, 1
    };
  }

  /** Creates a 4x4 identity matrix. */
  public static float[] create4x4IdentityMatrix() {
    float[] matrix = new float[16];
    setToIdentity(matrix);
    return matrix;
  }

  /** Sets the input {@code matrix} to an identity matrix. */
  public static void setToIdentity(float[] matrix) {
    Matrix.setIdentityM(matrix, /* smOffset= */ 0);
  }

  /** Flattens the list of 4 element NDC coordinate vectors into a buffer. */
  public static float[] createVertexBuffer(List<float[]> vertexList) {
    float[] vertexBuffer = new float[HOMOGENEOUS_COORDINATE_VECTOR_SIZE * vertexList.size()];
    for (int i = 0; i < vertexList.size(); i++) {
      System.arraycopy(
          /* src= */ vertexList.get(i),
          /* srcPos= */ 0,
          /* dest= */ vertexBuffer,
          /* destPos= */ HOMOGENEOUS_COORDINATE_VECTOR_SIZE * i,
          /* length= */ HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    }
    return vertexBuffer;
  }

  /**
   * Returns whether creating a GL context with {@value #EXTENSION_PROTECTED_CONTENT} is possible.
   *
   * <p>If {@code true}, the device supports a protected output path for DRM content when using GL.
   */
  public static boolean isProtectedContentExtensionSupported(Context context) {
    if (Util.SDK_INT < 24) {
      return false;
    }
    if (Util.SDK_INT < 26 && ("samsung".equals(Util.MANUFACTURER) || "XT1650".equals(Util.MODEL))) {
      // Samsung devices running Nougat are known to be broken. See
      // https://github.com/google/ExoPlayer/issues/3373 and [Internal: b/37197802].
      // Moto Z XT1650 is also affected. See
      // https://github.com/google/ExoPlayer/issues/3215.
      return false;
    }
    if (Util.SDK_INT < 26
        && !context
            .getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
      // Pre API level 26 devices were not well tested unless they supported VR mode.
      return false;
    }

    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    @Nullable String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    return eglExtensions != null && eglExtensions.contains(EXTENSION_PROTECTED_CONTENT);
  }

  /**
   * Returns whether the {@value #EXTENSION_SURFACELESS_CONTEXT} extension is supported.
   *
   * <p>This extension allows passing {@link EGL14#EGL_NO_SURFACE} for both the write and read
   * surfaces in a call to {@link EGL14#eglMakeCurrent(EGLDisplay, EGLSurface, EGLSurface,
   * EGLContext)}.
   */
  public static boolean isSurfacelessContextExtensionSupported() {
    if (Util.SDK_INT < 17) {
      return false;
    }
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    @Nullable String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    return eglExtensions != null && eglExtensions.contains(EXTENSION_SURFACELESS_CONTEXT);
  }

  /**
   * Returns whether the {@value #EXTENSION_YUV_TARGET} extension is supported.
   *
   * <p>This extension allows sampling raw YUV values from an external texture, which is required
   * for HDR.
   */
  public static boolean isYuvTargetExtensionSupported() {
    if (Util.SDK_INT < 17) {
      return false;
    }

    @Nullable String glExtensions;
    if (Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT)) {
      // Create a placeholder context and make it current to allow calling GLES20.glGetString().
      try {
        EGLDisplay eglDisplay = createEglDisplay();
        EGLContext eglContext = createEglContext(eglDisplay);
        focusPlaceholderEglSurface(eglContext, eglDisplay);
        glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        destroyEglContext(eglDisplay, eglContext);
      } catch (GlException e) {
        return false;
      }
    } else {
      glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
    }

    return glExtensions != null && glExtensions.contains(EXTENSION_YUV_TARGET);
  }

  /** Returns an initialized default {@link EGLDisplay}. */
  @RequiresApi(17)
  public static EGLDisplay createEglDisplay() throws GlException {
    return Api17.createEglDisplay();
  }

  /**
   * Creates a new {@link EGLContext} for the specified {@link EGLDisplay}.
   *
   * <p>Configures the {@link EGLContext} with {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888} and OpenGL
   * ES 2.0.
   *
   * @param eglDisplay The {@link EGLDisplay} to create an {@link EGLContext} for.
   */
  @RequiresApi(17)
  public static EGLContext createEglContext(EGLDisplay eglDisplay) throws GlException {
    return createEglContext(eglDisplay, EGL_CONFIG_ATTRIBUTES_RGBA_8888);
  }

  /**
   * Creates a new {@link EGLContext} for the specified {@link EGLDisplay}.
   *
   * @param eglDisplay The {@link EGLDisplay} to create an {@link EGLContext} for.
   * @param configAttributes The attributes to configure EGL with. Accepts either {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_1010102}, which will request OpenGL ES 3.0, or {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_8888}, which will request OpenGL ES 2.0.
   */
  @RequiresApi(17)
  public static EGLContext createEglContext(EGLDisplay eglDisplay, int[] configAttributes)
      throws GlException {
    checkArgument(
        Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_8888)
            || Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_1010102));
    return Api17.createEglContext(
        eglDisplay,
        /* version= */ Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_1010102) ? 3 : 2,
        configAttributes);
  }

  /**
   * Returns a new {@link EGLSurface} wrapping the specified {@code surface}.
   *
   * <p>The {@link EGLSurface} will configure with {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888} and
   * OpenGL ES 2.0.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
   */
  @RequiresApi(17)
  public static EGLSurface getEglSurface(EGLDisplay eglDisplay, Object surface) throws GlException {
    return Api17.getEglSurface(
        eglDisplay, surface, EGL_CONFIG_ATTRIBUTES_RGBA_8888, EGL_WINDOW_SURFACE_ATTRIBUTES_NONE);
  }

  /**
   * Returns a new {@link EGLSurface} wrapping the specified {@code surface}.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
   * @param configAttributes The attributes to configure EGL with. Accepts {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_1010102} and {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
   */
  @RequiresApi(17)
  public static EGLSurface getEglSurface(
      EGLDisplay eglDisplay, Object surface, int[] configAttributes) throws GlException {
    return Api17.getEglSurface(
        eglDisplay, surface, configAttributes, EGL_WINDOW_SURFACE_ATTRIBUTES_NONE);
  }

  /**
   * Creates a new {@link EGLSurface} wrapping a pixel buffer.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param width The width of the pixel buffer.
   * @param height The height of the pixel buffer.
   * @param configAttributes EGL configuration attributes. Valid arguments include {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_8888} and {@link #EGL_CONFIG_ATTRIBUTES_RGBA_1010102}.
   */
  @RequiresApi(17)
  private static EGLSurface createPbufferSurface(
      EGLDisplay eglDisplay, int width, int height, int[] configAttributes) throws GlException {
    int[] pbufferAttributes =
        new int[] {
          EGL14.EGL_WIDTH, width,
          EGL14.EGL_HEIGHT, height,
          EGL14.EGL_NONE
        };
    return Api17.createEglPbufferSurface(eglDisplay, configAttributes, pbufferAttributes);
  }

  /**
   * Creates and focuses a placeholder {@link EGLSurface}.
   *
   * <p>This makes a {@link EGLContext} current when reading and writing to a surface is not
   * required, configured with {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
   *
   * @param eglContext The {@link EGLContext} to make current.
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @return {@link EGL14#EGL_NO_SURFACE} if supported and a 1x1 pixel buffer surface otherwise.
   */
  @RequiresApi(17)
  public static EGLSurface focusPlaceholderEglSurface(EGLContext eglContext, EGLDisplay eglDisplay)
      throws GlException {
    return createFocusedPlaceholderEglSurface(
        eglContext, eglDisplay, EGL_CONFIG_ATTRIBUTES_RGBA_8888);
  }

  /**
   * Creates and focuses a placeholder {@link EGLSurface}.
   *
   * <p>This makes a {@link EGLContext} current when reading and writing to a surface is not
   * required.
   *
   * @param eglContext The {@link EGLContext} to make current.
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param configAttributes The attributes to configure EGL with. Accepts {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_1010102} and {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
   * @return A placeholder {@link EGLSurface} that has been focused to allow rendering to take
   *     place, or {@link EGL14#EGL_NO_SURFACE} if the current context supports rendering without a
   *     surface.
   */
  @RequiresApi(17)
  public static EGLSurface createFocusedPlaceholderEglSurface(
      EGLContext eglContext, EGLDisplay eglDisplay, int[] configAttributes) throws GlException {
    EGLSurface eglSurface =
        isSurfacelessContextExtensionSupported()
            ? EGL14.EGL_NO_SURFACE
            : createPbufferSurface(eglDisplay, /* width= */ 1, /* height= */ 1, configAttributes);

    focusEglSurface(eglDisplay, eglContext, eglSurface, /* width= */ 1, /* height= */ 1);
    return eglSurface;
  }

  /**
   * Collects all OpenGL errors that occurred since this method was last called and throws a {@link
   * GlException} with the combined error message.
   */
  public static void checkGlError() throws GlException {
    StringBuilder errorMessageBuilder = new StringBuilder();
    boolean foundError = false;
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      if (foundError) {
        errorMessageBuilder.append('\n');
      }
      errorMessageBuilder.append("glError: ").append(gluErrorString(error));
      foundError = true;
    }
    if (foundError) {
      throw new GlException(errorMessageBuilder.toString());
    }
  }

  /**
   * Asserts the texture size is valid.
   *
   * @param width The width for a texture.
   * @param height The height for a texture.
   * @throws GlException If the texture width or height is invalid.
   */
  private static void assertValidTextureSize(int width, int height) throws GlException {
    // TODO(b/201293185): Consider handling adjustments for sizes > GL_MAX_TEXTURE_SIZE
    //  (ex. downscaling appropriately) in a texture processor instead of asserting incorrect
    //  values.
    // For valid GL sizes, see:
    // https://www.khronos.org/registry/OpenGL-Refpages/es2.0/xhtml/glTexImage2D.xml
    int[] maxTextureSizeBuffer = new int[1];
    GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSizeBuffer, 0);
    int maxTextureSize = maxTextureSizeBuffer[0];
    checkState(
        maxTextureSize > 0,
        "Create a OpenGL context first or run the GL methods on an OpenGL thread.");

    if (width < 0 || height < 0) {
      throw new GlException("width or height is less than 0");
    }
    if (width > maxTextureSize || height > maxTextureSize) {
      throw new GlException(
          "width or height is greater than GL_MAX_TEXTURE_SIZE " + maxTextureSize);
    }
  }

  /** Fills the pixels in the current output render target with (r=0, g=0, b=0, a=0). */
  public static void clearOutputFrame() throws GlException {
    GLES20.glClearColor(/* red= */ 0, /* green= */ 0, /* blue= */ 0, /* alpha= */ 0);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GlUtil.checkGlError();
  }

  /**
   * Makes the specified {@code eglSurface} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   */
  @RequiresApi(17)
  public static void focusEglSurface(
      EGLDisplay eglDisplay, EGLContext eglContext, EGLSurface eglSurface, int width, int height)
      throws GlException {
    Api17.focusRenderTarget(
        eglDisplay, eglContext, eglSurface, /* framebuffer= */ 0, width, height);
  }

  /**
   * Makes the specified {@code framebuffer} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   */
  @RequiresApi(17)
  public static void focusFramebuffer(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      int framebuffer,
      int width,
      int height)
      throws GlException {
    Api17.focusRenderTarget(eglDisplay, eglContext, eglSurface, framebuffer, width, height);
  }

  /**
   * Makes the specified {@code framebuffer} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   *
   * <p>The caller must ensure that there is a current OpenGL context before calling this method.
   *
   * @param framebuffer The identifier of the framebuffer object to bind as the output render
   *     target.
   * @param width The viewport width, in pixels.
   * @param height The viewport height, in pixels.
   */
  @RequiresApi(17)
  public static void focusFramebufferUsingCurrentContext(int framebuffer, int width, int height)
      throws GlException {
    Api17.focusFramebufferUsingCurrentContext(framebuffer, width, height);
  }

  /**
   * Deletes a GL texture.
   *
   * @param textureId The ID of the texture to delete.
   */
  public static void deleteTexture(int textureId) throws GlException {
    GLES20.glDeleteTextures(/* n= */ 1, new int[] {textureId}, /* offset= */ 0);
    checkGlError();
  }

  /**
   * Destroys the {@link EGLContext} identified by the provided {@link EGLDisplay} and {@link
   * EGLContext}.
   */
  @RequiresApi(17)
  public static void destroyEglContext(
      @Nullable EGLDisplay eglDisplay, @Nullable EGLContext eglContext) throws GlException {
    Api17.destroyEglContext(eglDisplay, eglContext);
  }

  /**
   * Allocates a FloatBuffer with the given data.
   *
   * @param data Used to initialize the new buffer.
   */
  public static FloatBuffer createBuffer(float[] data) {
    return (FloatBuffer) createBuffer(data.length).put(data).flip();
  }

  /**
   * Allocates a FloatBuffer.
   *
   * @param capacity The new buffer's capacity, in floats.
   */
  private static FloatBuffer createBuffer(int capacity) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(capacity * C.BYTES_PER_FLOAT);
    return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  /**
   * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   */
  public static int createExternalTexture() throws GlException {
    int texId = generateTexture();
    bindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
    return texId;
  }

  /**
   * Allocates a new RGBA texture with the specified dimensions and color component precision.
   *
   * @param width The width of the new texture in pixels.
   * @param height The height of the new texture in pixels.
   * @param useHighPrecisionColorComponents If {@code false}, uses 8-bit unsigned bytes. If {@code
   *     true}, use 16-bit (half-precision) floating-point.
   * @throws GlException If the texture allocation fails.
   * @return The texture identifier for the newly-allocated texture.
   */
  public static int createTexture(int width, int height, boolean useHighPrecisionColorComponents)
      throws GlException {
    // TODO(227624622): Implement a pixel test that confirms 16f has less posterization.
    if (useHighPrecisionColorComponents) {
      checkState(Util.SDK_INT >= 18, "GLES30 extensions are not supported below API 18.");
      return createTexture(width, height, GLES30.GL_RGBA16F, GLES30.GL_HALF_FLOAT);
    }
    return createTexture(width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE);
  }

  /**
   * Allocates a new RGBA texture with the specified dimensions and color component precision.
   *
   * @param width The width of the new texture in pixels.
   * @param height The height of the new texture in pixels.
   * @param internalFormat The number of color components in the texture, as well as their format.
   * @param type The data type of the pixel data.
   * @throws GlException If the texture allocation fails.
   * @return The texture identifier for the newly-allocated texture.
   */
  private static int createTexture(int width, int height, int internalFormat, int type)
      throws GlException {
    assertValidTextureSize(width, height);
    int texId = generateTexture();
    bindTexture(GLES20.GL_TEXTURE_2D, texId);
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(width * height * 4);
    GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        /* level= */ 0,
        internalFormat,
        width,
        height,
        /* border= */ 0,
        GLES20.GL_RGBA,
        type,
        byteBuffer);
    checkGlError();
    return texId;
  }

  /** Returns a new GL texture identifier. */
  private static int generateTexture() throws GlException {
    checkGlException(
        !Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT), "No current context");

    int[] texId = new int[1];
    GLES20.glGenTextures(/* n= */ 1, texId, /* offset= */ 0);
    checkGlError();
    return texId[0];
  }

  /**
   * Binds the texture of the given type with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   *
   * @param textureTarget The target to which the texture is bound, e.g. {@link
   *     GLES20#GL_TEXTURE_2D} for a two-dimensional texture or {@link
   *     GLES11Ext#GL_TEXTURE_EXTERNAL_OES} for an external texture.
   * @param texId The texture identifier.
   */
  public static void bindTexture(int textureTarget, int texId) throws GlException {
    GLES20.glBindTexture(textureTarget, texId);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
  }

  /**
   * Returns a new framebuffer for the texture.
   *
   * @param texId The identifier of the texture to attach to the framebuffer.
   */
  public static int createFboForTexture(int texId) throws GlException {
    checkGlException(
        !Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT), "No current context");

    int[] fboId = new int[1];
    GLES20.glGenFramebuffers(/* n= */ 1, fboId, /* offset= */ 0);
    checkGlError();
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
    checkGlError();
    GLES20.glFramebufferTexture2D(
        GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0);
    checkGlError();
    return fboId[0];
  }

  /**
   * Throws a {@link GlException} with the given message if {@code expression} evaluates to {@code
   * false}.
   */
  public static void checkGlException(boolean expression, String errorMessage) throws GlException {
    if (!expression) {
      throw new GlException(errorMessage);
    }
  }

  private static void checkEglException(String errorMessage) throws GlException {
    int error = EGL14.eglGetError();
    checkGlException(error == EGL14.EGL_SUCCESS, errorMessage + ", error code: " + error);
  }

  @RequiresApi(17)
  private static final class Api17 {
    private Api17() {}

    @DoNotInline
    public static EGLDisplay createEglDisplay() throws GlException {
      EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
      checkGlException(!eglDisplay.equals(EGL14.EGL_NO_DISPLAY), "No EGL display.");
      checkGlException(
          EGL14.eglInitialize(
              eglDisplay,
              /* unusedMajor */ new int[1],
              /* majorOffset= */ 0,
              /* unusedMinor */ new int[1],
              /* minorOffset= */ 0),
          "Error in eglInitialize.");
      checkGlError();
      return eglDisplay;
    }

    @DoNotInline
    public static EGLContext createEglContext(
        EGLDisplay eglDisplay, int version, int[] configAttributes) throws GlException {
      int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE};
      EGLContext eglContext =
          EGL14.eglCreateContext(
              eglDisplay,
              getEglConfig(eglDisplay, configAttributes),
              EGL14.EGL_NO_CONTEXT,
              contextAttributes,
              /* offset= */ 0);
      if (eglContext == null) {
        EGL14.eglTerminate(eglDisplay);
        throw new GlException(
            "eglCreateContext() failed to create a valid context. The device may not support EGL"
                + " version "
                + version);
      }
      checkGlError();
      return eglContext;
    }

    @DoNotInline
    public static EGLSurface getEglSurface(
        EGLDisplay eglDisplay,
        Object surface,
        int[] configAttributes,
        int[] windowSurfaceAttributes)
        throws GlException {
      EGLSurface eglSurface =
          EGL14.eglCreateWindowSurface(
              eglDisplay,
              getEglConfig(eglDisplay, configAttributes),
              surface,
              windowSurfaceAttributes,
              /* offset= */ 0);
      checkEglException("Error creating surface");
      return eglSurface;
    }

    @DoNotInline
    public static EGLSurface createEglPbufferSurface(
        EGLDisplay eglDisplay, int[] configAttributes, int[] pbufferAttributes) throws GlException {
      EGLSurface eglSurface =
          EGL14.eglCreatePbufferSurface(
              eglDisplay,
              getEglConfig(eglDisplay, configAttributes),
              pbufferAttributes,
              /* offset= */ 0);
      checkEglException("Error creating surface");
      return eglSurface;
    }

    @DoNotInline
    public static void focusRenderTarget(
        EGLDisplay eglDisplay,
        EGLContext eglContext,
        EGLSurface eglSurface,
        int framebuffer,
        int width,
        int height)
        throws GlException {
      EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
      checkEglException("Error making context current");
      focusFramebufferUsingCurrentContext(framebuffer, width, height);
    }

    @DoNotInline
    public static void focusFramebufferUsingCurrentContext(int framebuffer, int width, int height)
        throws GlException {
      checkGlException(
          !Util.areEqual(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT), "No current context");

      int[] boundFramebuffer = new int[1];
      GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFramebuffer, /* offset= */ 0);
      if (boundFramebuffer[0] != framebuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
      }
      checkGlError();
      GLES20.glViewport(/* x= */ 0, /* y= */ 0, width, height);
      checkGlError();
    }

    @DoNotInline
    public static void destroyEglContext(
        @Nullable EGLDisplay eglDisplay, @Nullable EGLContext eglContext) throws GlException {
      if (eglDisplay == null) {
        return;
      }
      EGL14.eglMakeCurrent(
          eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      checkEglException("Error releasing context");
      if (eglContext != null) {
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        checkEglException("Error destroying context");
      }
      EGL14.eglReleaseThread();
      checkEglException("Error releasing thread");
      EGL14.eglTerminate(eglDisplay);
      checkEglException("Error terminating display");
    }

    @DoNotInline
    private static EGLConfig getEglConfig(EGLDisplay eglDisplay, int[] attributes)
        throws GlException {
      EGLConfig[] eglConfigs = new EGLConfig[1];
      if (!EGL14.eglChooseConfig(
          eglDisplay,
          attributes,
          /* attrib_listOffset= */ 0,
          eglConfigs,
          /* configsOffset= */ 0,
          /* config_size= */ 1,
          /* unusedNumConfig */ new int[1],
          /* num_configOffset= */ 0)) {
        throw new GlException("eglChooseConfig failed.");
      }
      return eglConfigs[0];
    }
  }
}
