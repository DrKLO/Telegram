package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;
import android.opengl.GLES30;
import android.util.AtomicFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Driver-specific binaries are only a cache: source compilation is always the fallback. */
public final class GLProgramCache {
    private static final int MAGIC = 0x44475031;
    private static final int MAX_BINARY_SIZE = 16 * 1024 * 1024;

    public static int create(Context context, String namespace, String vertexSource, String fragmentSource,
                             String positionAttribute, String normalAttribute) {
        int program = GLES30.glCreateProgram();
        int vertex = 0, fragment = 0;
        AtomicFile cache = cacheFile(context, namespace, vertexSource, fragmentSource, positionAttribute, normalAttribute);
        try {
            if (cache != null && restore(program, cache)) return program;
            if (cache != null) GLES30.glProgramParameteri(program, GLES30.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GLES30.GL_TRUE);
            vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource);
            fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
            GLES30.glAttachShader(program, vertex);
            GLES30.glAttachShader(program, fragment);
            GLES30.glBindAttribLocation(program, 0, positionAttribute);
            GLES30.glBindAttribLocation(program, 1, normalAttribute);
            GLES30.glLinkProgram(program);
            int[] status = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) throw new IllegalStateException(GLES30.glGetProgramInfoLog(program));
            if (cache != null) save(program, cache);
            return program;
        } catch (RuntimeException e) {
            GLES30.glDeleteProgram(program);
            throw e;
        } finally {
            if (vertex != 0) GLES30.glDeleteShader(vertex);
            if (fragment != 0) GLES30.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String error = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    private static AtomicFile cacheFile(Context context, String namespace, String vertex, String fragment,
                                        String positionAttribute, String normalAttribute) {
        String version = GLES30.glGetString(GLES30.GL_VERSION);
        if (version == null || !version.startsWith("OpenGL ES 3.")) return null;
        int[] count = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_NUM_PROGRAM_BINARY_FORMATS, count, 0);
        if (count[0] == 0) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Binaries must never cross driver updates, GPUs or source changes.
            for (String value : new String[]{"gl-program-v1", namespace, vertex, fragment, positionAttribute, normalAttribute,
                    GLES30.glGetString(GLES30.GL_VENDOR), GLES30.glGetString(GLES30.GL_RENDERER),
                    GLES30.glGetString(GLES30.GL_VERSION), GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION)}) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            StringBuilder name = new StringBuilder();
            for (byte b : digest.digest()) name.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            File directory = new File(context.getCodeCacheDir(), "gl-programs");
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            return new AtomicFile(new File(directory, name + ".bin"));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean restore(int program, AtomicFile file) {
        if (!file.getBaseFile().isFile()) return false;
        try (DataInputStream input = new DataInputStream(file.openRead())) {
            if (input.readInt() != MAGIC) throw new IllegalStateException("Invalid program cache");
            int format = input.readInt(), size = input.readInt();
            if (size <= 0 || size > MAX_BINARY_SIZE) throw new IllegalStateException("Invalid binary length");
            // Some drivers (e.g. the emulator) return formats from glGetProgramBinary that are absent from
            // GL_PROGRAM_BINARY_FORMATS; glProgramBinary itself rejects unusable formats via LINK_STATUS.
            int[] count = new int[1];
            byte[] data = new byte[size];
            input.readFully(data);
            if (input.read() != -1) throw new IllegalStateException("Invalid binary size");
            ByteBuffer binary = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            binary.put(data).position(0);
            while (GLES30.glGetError() != GLES30.GL_NO_ERROR) ; // don't blame stale errors on the restore
            GLES30.glProgramBinary(program, format, binary, size);
            int error = GLES30.glGetError();
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, count, 0);
            if (error == GLES30.GL_NO_ERROR && count[0] != 0) return true;
        } catch (Exception ignore) {
            // A truncated/stale cache is harmless; recompile the source below.
        }
        file.delete();
        return false;
    }

    private static void save(int program, AtomicFile file) {
        FileOutputStream stream = null;
        try {
            int[] size = new int[1], format = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_PROGRAM_BINARY_LENGTH, size, 0);
            if (size[0] <= 0 || size[0] > MAX_BINARY_SIZE) return;
            ByteBuffer binary = ByteBuffer.allocateDirect(size[0]).order(ByteOrder.nativeOrder());
            GLES30.glGetProgramBinary(program, size[0], size, 0, format, 0, binary);
            if (size[0] <= 0) return;
            byte[] data = new byte[size[0]];
            binary.position(0);
            binary.get(data);
            stream = file.startWrite();
            DataOutputStream output = new DataOutputStream(stream);
            output.writeInt(MAGIC);
            output.writeInt(format[0]);
            output.writeInt(data.length);
            output.write(data);
            output.flush();
            file.finishWrite(stream);
        } catch (Exception ignore) {
            if (stream != null) file.failWrite(stream);
        }
    }
}
