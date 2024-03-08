package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

public final class ObjLoader {

    public int numFaces;

    public float[] normals;
    public float[] textureCoordinates;
    public float[] positions;

    public ObjLoader(Context context, String file) {
        ArrayList<Float> vertices = new ArrayList<>();
        ArrayList<Float> normals = new ArrayList<>();
        ArrayList<Float> textures = new ArrayList<>();

        try {
            DataInputStream inputStream = new DataInputStream(context.getAssets().open(file));
            int n = inputStream.readInt();
            for (int i = 0; i < n; i++) {
                vertices.add(inputStream.readFloat());
            }

            n = inputStream.readInt();
            for (int i = 0; i < n; i++) {
                textures.add(inputStream.readFloat());
            }

            n = inputStream.readInt();
            for (int i = 0; i < n; i++) {
                normals.add(inputStream.readFloat());
            }

            n = inputStream.readInt();

            numFaces = n;
            this.normals = new float[numFaces * 3];
            textureCoordinates = new float[numFaces * 2];
            positions = new float[numFaces * 3];
            int positionIndex = 0;
            int normalIndex = 0;
            int textureIndex = 0;

            for (int i = 0; i < n; i++) {
                int index = 3 * inputStream.readInt();
                positions[positionIndex++] = vertices.get(index++);
                positions[positionIndex++] = vertices.get(index++);
                positions[positionIndex++] = vertices.get(index);

                index = 2 * inputStream.readInt();
                textureCoordinates[normalIndex++] = index < 0 || index >= textures.size() ? 0 : textures.get(index);
                index++;
                textureCoordinates[normalIndex++] = index < 0 || index >= textures.size() ? 0 : 1 - textures.get(index);

                index = 3 * inputStream.readInt();
                this.normals[textureIndex++] = normals.get(index++);
                this.normals[textureIndex++] = normals.get(index++);
                this.normals[textureIndex++] = normals.get(index);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
