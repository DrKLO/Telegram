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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import javax.microedition.khronos.egl.EGL10;

/** GL utilities. */
public final class GlUtil {

  /**
   * GL attribute, which can be attached to a buffer with {@link Attribute#setBuffer(float[], int)}.
   */
  public static final class Attribute {

    /** The name of the attribute in the GLSL sources. */
    public final String name;

    private final int index;
    private final int location;

    @Nullable private Buffer buffer;
    private int size;

    /**
     * Creates a new GL attribute.
     *
     * @param program The identifier of a compiled and linked GLSL shader program.
     * @param index The index of the attribute. After this instance has been constructed, the name
     *     of the attribute is available via the {@link #name} field.
     */
    public Attribute(int program, int index) {
      int[] len = new int[1];
      GLES20.glGetProgramiv(program, GLES20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, len, 0);

      int[] type = new int[1];
      int[] size = new int[1];
      byte[] nameBytes = new byte[len[0]];
      int[] ignore = new int[1];

      GLES20.glGetActiveAttrib(program, index, len[0], ignore, 0, size, 0, type, 0, nameBytes, 0);
      name = new String(nameBytes, 0, strlen(nameBytes));
      location = GLES20.glGetAttribLocation(program, name);
      this.index = index;
    }

    /**
     * Configures {@link #bind()} to attach vertices in {@code buffer} (each of size {@code size}
     * elements) to this {@link Attribute}.
     *
     * @param buffer Buffer to bind to this attribute.
     * @param size Number of elements per vertex.
     */
    public void setBuffer(float[] buffer, int size) {
      this.buffer = createBuffer(buffer);
      this.size = size;
    }

    /**
     * Sets the vertex attribute to whatever was attached via {@link #setBuffer(float[], int)}.
     *
     * <p>Should be called before each drawing call.
     */
    public void bind() {
      Buffer buffer = Assertions.checkNotNull(this.buffer, "call setBuffer before bind");
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
      GLES20.glVertexAttribPointer(
          location,
          size, // count
          GLES20.GL_FLOAT, // type
          false, // normalize
          0, // stride
          buffer);
      GLES20.glEnableVertexAttribArray(index);
      checkGlError();
    }
  }

  /**
   * GL uniform, which can be attached to a sampler using {@link Uniform#setSamplerTexId(int, int)}.
   */
  public static final class Uniform {

    /** The name of the uniform in the GLSL sources. */
    public final String name;

    private final int location;
    private final int type;
    private final float[] value;

    private int texId;
    private int unit;

    /**
     * Creates a new GL uniform.
     *
     * @param program The identifier of a compiled and linked GLSL shader program.
     * @param index The index of the uniform. After this instance has been constructed, the name of
     *     the uniform is available via the {@link #name} field.
     */
    public Uniform(int program, int index) {
      int[] len = new int[1];
      GLES20.glGetProgramiv(program, GLES20.GL_ACTIVE_UNIFORM_MAX_LENGTH, len, 0);

      int[] type = new int[1];
      int[] size = new int[1];
      byte[] name = new byte[len[0]];
      int[] ignore = new int[1];

      GLES20.glGetActiveUniform(program, index, len[0], ignore, 0, size, 0, type, 0, name, 0);
      this.name = new String(name, 0, strlen(name));
      location = GLES20.glGetUniformLocation(program, this.name);
      this.type = type[0];

      value = new float[1];
    }

    /**
     * Configures {@link #bind()} to use the specified {@code texId} for this sampler uniform.
     *
     * @param texId The GL texture identifier from which to sample.
     * @param unit The GL texture unit index.
     */
    public void setSamplerTexId(int texId, int unit) {
      this.texId = texId;
      this.unit = unit;
    }

    /** Configures {@link #bind()} to use the specified float {@code value} for this uniform. */
    public void setFloat(float value) {
      this.value[0] = value;
    }

    /**
     * Sets the uniform to whatever value was passed via {@link #setSamplerTexId(int, int)} or
     * {@link #setFloat(float)}.
     *
     * <p>Should be called before each drawing call.
     */
    public void bind() {
      if (type == GLES20.GL_FLOAT) {
        GLES20.glUniform1fv(location, 1, value, 0);
        checkGlError();
        return;
      }

      if (texId == 0) {
        throw new IllegalStateException("call setSamplerTexId before bind");
      }
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
      if (type == GLES11Ext.GL_SAMPLER_EXTERNAL_OES) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
      } else if (type == GLES20.GL_SAMPLER_2D) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
      } else {
        throw new IllegalStateException("unexpected uniform type: " + type);
      }
      GLES20.glUniform1i(location, unit);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      checkGlError();
    }
  }

  private static final String TAG = "GlUtil";

  private static final String EXTENSION_PROTECTED_CONTENT = "EGL_EXT_protected_content";
  private static final String EXTENSION_SURFACELESS_CONTEXT = "EGL_KHR_surfaceless_context";

  /** Class only contains static methods. */
  private GlUtil() {}

  /**
   * Returns whether creating a GL context with {@value EXTENSION_PROTECTED_CONTENT} is possible. If
   * {@code true}, the device supports a protected output path for DRM content when using GL.
   */
  @TargetApi(24)
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
   * Returns whether creating a GL context with {@value EXTENSION_SURFACELESS_CONTEXT} is possible.
   */
  @TargetApi(17)
  public static boolean isSurfacelessContextExtensionSupported() {
    if (Util.SDK_INT < 17) {
      return false;
    }
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    @Nullable String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    return eglExtensions != null && eglExtensions.contains(EXTENSION_SURFACELESS_CONTEXT);
  }

  /**
   * If there is an OpenGl error, logs the error and if {@link
   * ExoPlayerLibraryInfo#GL_ASSERTIONS_ENABLED} is true throws a {@link RuntimeException}.
   */
  public static void checkGlError() {
    int lastError = GLES20.GL_NO_ERROR;
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, "glError " + gluErrorString(error));
      lastError = error;
    }
    if (ExoPlayerLibraryInfo.GL_ASSERTIONS_ENABLED && lastError != GLES20.GL_NO_ERROR) {
      throw new RuntimeException("glError " + gluErrorString(lastError));
    }
  }

  /**
   * Builds a GL shader program from vertex and fragment shader code.
   *
   * @param vertexCode GLES20 vertex shader program as arrays of strings. Strings are joined by
   *     adding a new line character in between each of them.
   * @param fragmentCode GLES20 fragment shader program as arrays of strings. Strings are joined by
   *     adding a new line character in between each of them.
   * @return GLES20 program id.
   */
  public static int compileProgram(String[] vertexCode, String[] fragmentCode) {
    return compileProgram(TextUtils.join("\n", vertexCode), TextUtils.join("\n", fragmentCode));
  }

  /**
   * Builds a GL shader program from vertex and fragment shader code.
   *
   * @param vertexCode GLES20 vertex shader program.
   * @param fragmentCode GLES20 fragment shader program.
   * @return GLES20 program id.
   */
  public static int compileProgram(String vertexCode, String fragmentCode) {
    int program = GLES20.glCreateProgram();
    checkGlError();

    // Add the vertex and fragment shaders.
    addShader(GLES20.GL_VERTEX_SHADER, vertexCode, program);
    addShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode, program);

    // Link and check for errors.
    GLES20.glLinkProgram(program);
    int[] linkStatus = new int[] {GLES20.GL_FALSE};
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES20.GL_TRUE) {
      throwGlError("Unable to link shader program: \n" + GLES20.glGetProgramInfoLog(program));
    }
    checkGlError();

    return program;
  }

  /** Returns the {@link Attribute}s in the specified {@code program}. */
  public static Attribute[] getAttributes(int program) {
    int[] attributeCount = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_ACTIVE_ATTRIBUTES, attributeCount, 0);
    if (attributeCount[0] != 2) {
      throw new IllegalStateException("expected two attributes");
    }

    Attribute[] attributes = new Attribute[attributeCount[0]];
    for (int i = 0; i < attributeCount[0]; i++) {
      attributes[i] = new Attribute(program, i);
    }
    return attributes;
  }

  /** Returns the {@link Uniform}s in the specified {@code program}. */
  public static Uniform[] getUniforms(int program) {
    int[] uniformCount = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_ACTIVE_UNIFORMS, uniformCount, 0);

    Uniform[] uniforms = new Uniform[uniformCount[0]];
    for (int i = 0; i < uniformCount[0]; i++) {
      uniforms[i] = new Uniform(program, i);
    }

    return uniforms;
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
  public static FloatBuffer createBuffer(int capacity) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(capacity * C.BYTES_PER_FLOAT);
    return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  /**
   * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   */
  public static int createExternalTexture() {
    int[] texId = new int[1];
    GLES20.glGenTextures(1, IntBuffer.wrap(texId));
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0]);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
    return texId[0];
  }

  private static void addShader(int type, String source, int program) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);

    int[] result = new int[] {GLES20.GL_FALSE};
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
    if (result[0] != GLES20.GL_TRUE) {
      throwGlError(GLES20.glGetShaderInfoLog(shader) + ", source: " + source);
    }

    GLES20.glAttachShader(program, shader);
    GLES20.glDeleteShader(shader);
    checkGlError();
  }

  private static void throwGlError(String errorMsg) {
    Log.e(TAG, errorMsg);
    if (ExoPlayerLibraryInfo.GL_ASSERTIONS_ENABLED) {
      throw new RuntimeException(errorMsg);
    }
  }

  /** Returns the length of the null-terminated string in {@code strVal}. */
  private static int strlen(byte[] strVal) {
    for (int i = 0; i < strVal.length; ++i) {
      if (strVal[i] == '\0') {
        return i;
      }
    }
    return strVal.length;
  }
}
