package org.telegram.ui.Components.Paint;

import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Slice {
    private RectF bounds;
    private File file;

    public Slice(final ByteBuffer data, RectF rect, DispatchQueue queue) {
        bounds = rect;

        try {
            File outputDir = ApplicationLoader.applicationContext.getCacheDir();
            file = File.createTempFile("paint", ".bin", outputDir);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        if (file == null)
            return;

        storeData(data);
    }

    public void cleanResources() {
        if (file != null) {
            file.delete();
            file = null;
        }
    }

    private void storeData(ByteBuffer data) {
        try {
            final byte[] input = data.array();
            FileOutputStream fos = new FileOutputStream(file);

            final Deflater deflater = new Deflater(Deflater.BEST_SPEED, true);
            deflater.setInput(input, data.arrayOffset(), data.remaining());
            deflater.finish();

            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int byteCount = deflater.deflate(buf);
                fos.write(buf, 0, byteCount);
            }
            deflater.end();

            fos.close();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public ByteBuffer getData() {
        try {
            byte[] input = new byte[1024];
            byte[] output = new byte[1024];
            FileInputStream fin = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Inflater inflater = new Inflater(true);

            while (true) {
                int numRead = fin.read(input);
                if (numRead != -1) {
                    inflater.setInput(input, 0, numRead);
                }

                int numDecompressed;
                while ((numDecompressed = inflater.inflate(output, 0, output.length)) != 0) {
                    bos.write(output, 0, numDecompressed);
                }

                if (inflater.finished()) {
                    break;
                }
                else if (inflater.needsInput()) {
                    continue;
                }
            }

            inflater.end();
            ByteBuffer result = ByteBuffer.wrap(bos.toByteArray(), 0, bos.size());

            bos.close();
            fin.close();

            return result;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        return null;
    }

    public int getX() {
        return (int) bounds.left;
    }

    public int getY() {
        return (int) bounds.top;
    }

    public int getWidth() {
        return (int) bounds.width();
    }

    public int getHeight() {
        return (int) bounds.height();
    }

    public RectF getBounds() {
        return new RectF(bounds);
    }
}
