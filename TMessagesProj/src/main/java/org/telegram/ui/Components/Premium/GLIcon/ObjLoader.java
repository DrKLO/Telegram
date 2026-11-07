package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public final class ObjLoader {

    public int numFaces;
    public float[] normals;
    public float[] textureCoordinates;
    public float[] positions;

    public ObjLoader(Context context, String file, float scale) {
        // Assets otherwise perform a native read for every scalar. Buffer the BINOBJ
        // stream and keep primitive arrays to avoid thousands of boxed Floats on startup.
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(context.getAssets().open(file), 16384))) {
            float[] vertices = readFloats(input);
            float[] textures = readFloats(input);
            float[] sourceNormals = readFloats(input);
            numFaces = input.readInt();
            normals = new float[numFaces * 3];
            textureCoordinates = new float[numFaces * 2];
            positions = new float[numFaces * 3];
            for (int i = 0; i < numFaces; i++) {
                int position = 3 * input.readInt();
                for (int j = 0; j < 3; j++) positions[i * 3 + j] = vertices[position + j] * scale;
                int texture = 2 * input.readInt();
                textureCoordinates[i * 2] = texture < 0 || texture >= textures.length ? 0 : textures[texture];
                texture++;
                textureCoordinates[i * 2 + 1] = texture < 0 || texture >= textures.length ? 0 : 1 - textures[texture];
                int normal = 3 * input.readInt();
                for (int j = 0; j < 3; j++) normals[i * 3 + j] = sourceNormals[normal + j];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static float[] readFloats(DataInputStream input) throws IOException {
        float[] values = new float[input.readInt()];
        for (int i = 0; i < values.length; i++) values[i] = input.readFloat();
        return values;
    }
}
