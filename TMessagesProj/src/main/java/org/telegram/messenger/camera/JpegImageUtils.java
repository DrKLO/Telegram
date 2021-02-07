package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


@TargetApi(21)
public final class JpegImageUtils {
    private static final String TAG = "JpegImageUtils";
    static final int FLIP_NORMAL = 0;
    static final int FLIP_Y = 1;
    static final int FLIP_X = 2;

    private JpegImageUtils() {
    }

    public static float min(float value1, float value2, float value3, float value4) {
        return Math.min(Math.min(value1, value2), Math.min(value3, value4));
    }

    @Nullable
    public static byte[] imageToJpegByteArray(@NonNull ImageProxy image, int flipState)
            throws CodecFailedException {
        byte[] data = null;
        if (image.getFormat() == ImageFormat.JPEG) {
            data = jpegImageToJpegByteArray(image, flipState);
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            data = yuvImageToJpegByteArray(image, flipState);
        } else {
            Log.w(TAG, "Unrecognized image format: " + image.getFormat());
        }
        return data;
    }

    @NonNull
    public static byte[] yuv_420_888toNv21(@NonNull ImageProxy image) {
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();
        yBuffer.rewind();
        uBuffer.rewind();
        vBuffer.rewind();

        int ySize = yBuffer.remaining();

        int position = 0;
        byte[] nv21 = new byte[ySize + (image.getWidth() * image.getHeight() / 2)];

        // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
        for (int row = 0; row < image.getHeight(); row++) {
            yBuffer.get(nv21, position, image.getWidth());
            position += image.getWidth();
            yBuffer.position(
                    Math.min(ySize, yBuffer.position() - image.getWidth() + yPlane.getRowStride()));
        }

        int chromaHeight = image.getHeight() / 2;
        int chromaWidth = image.getWidth() / 2;
        int vRowStride = vPlane.getRowStride();
        int uRowStride = uPlane.getRowStride();
        int vPixelStride = vPlane.getPixelStride();
        int uPixelStride = uPlane.getPixelStride();

        // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
        // perform faster bulk gets from the byte buffers.
        byte[] vLineBuffer = new byte[vRowStride];
        byte[] uLineBuffer = new byte[uRowStride];
        for (int row = 0; row < chromaHeight; row++) {
            vBuffer.get(vLineBuffer, 0, Math.min(vRowStride, vBuffer.remaining()));
            uBuffer.get(uLineBuffer, 0, Math.min(uRowStride, uBuffer.remaining()));
            int vLineBufferPosition = 0;
            int uLineBufferPosition = 0;
            for (int col = 0; col < chromaWidth; col++) {
                nv21[position++] = vLineBuffer[vLineBufferPosition];
                nv21[position++] = uLineBuffer[uLineBufferPosition];
                vLineBufferPosition += vPixelStride;
                uLineBufferPosition += uPixelStride;
            }
        }

        return nv21;
    }

    /** Crops byte array with given {@link android.graphics.Rect}. */
    @NonNull
    public static byte[] flipByteArray(@NonNull byte[] data, int flipState)
            throws CodecFailedException {

        if(flipState == FLIP_NORMAL) return data;

        Bitmap source =  BitmapFactory.decodeByteArray(data, 0, data.length);
        Matrix matrix = new Matrix();
        if(flipState == FLIP_Y){
            matrix.postScale( 1 ,  -1, source.getWidth() / 2f, source.getHeight() / 2f);
        } else if(flipState == FLIP_X){
            matrix.postScale( -1 ,  1, source.getWidth() / 2f, source.getHeight() / 2f);
        }

        Bitmap flipped = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean success = flipped.compress(Bitmap.CompressFormat.JPEG, 100, out);
        if (!success) {
            throw new CodecFailedException("Encode bitmap failed.",
                    CodecFailedException.FailureType.ENCODE_FAILED);
        }
        flipped.recycle();

        return out.toByteArray();
    }

    /** Crops byte array with given {@link android.graphics.Rect}. */
    @NonNull
    public static byte[] cropByteArray(@NonNull byte[] data, @Nullable Rect cropRect)
            throws CodecFailedException {
        if (cropRect == null) {
            return data;
        }

        Bitmap bitmap = null;
        try {
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length,
                    false);
            bitmap = decoder.decodeRegion(cropRect, new BitmapFactory.Options());
            decoder.recycle();
        } catch (IllegalArgumentException e) {
            throw new CodecFailedException("Decode byte array failed with illegal argument." + e,
                    CodecFailedException.FailureType.DECODE_FAILED);
        } catch (IOException e) {
            throw new CodecFailedException("Decode byte array failed.",
                    CodecFailedException.FailureType.DECODE_FAILED);
        }

        if (bitmap == null) {
            throw new CodecFailedException("Decode byte array failed.",
                    CodecFailedException.FailureType.DECODE_FAILED);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        if (!success) {
            throw new CodecFailedException("Encode bitmap failed.",
                    CodecFailedException.FailureType.ENCODE_FAILED);
        }
        bitmap.recycle();

        return out.toByteArray();
    }

    private static byte[] nv21ToJpeg(byte[] nv21, int width, int height, @Nullable Rect cropRect, int flipState)
            throws CodecFailedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        boolean success =
                yuv.compressToJpeg(
                        cropRect == null ? new Rect(0, 0, width, height) : cropRect, 100, out);
        if (!success) {
            throw new CodecFailedException("YuvImage failed to encode jpeg.",
                    CodecFailedException.FailureType.ENCODE_FAILED);
        }
        byte[] data = out.toByteArray();
        if(flipState != FLIP_NORMAL){
            data = flipByteArray(data, flipState);
        }
        return data;
    }

    private static boolean shouldCropImage(ImageProxy image) {
        Size sourceSize = new Size(image.getWidth(), image.getHeight());
        Size targetSize = new Size(image.getCropRect().width(), image.getCropRect().height());

        return !targetSize.equals(sourceSize);
    }

    private static byte[] jpegImageToJpegByteArray(ImageProxy image, int flipState) throws CodecFailedException {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.rewind();
        buffer.get(data);
        if (shouldCropImage(image)) {
            data = cropByteArray(data, image.getCropRect());
        }
        if(flipState != FLIP_NORMAL){
            data = flipByteArray(data, flipState);
        }
        return data;
    }

    private static byte[] yuvImageToJpegByteArray(ImageProxy image, int flipState)
            throws CodecFailedException {
        return nv21ToJpeg(
                yuv_420_888toNv21(image),
                image.getWidth(),
                image.getHeight(),
                shouldCropImage(image) ? image.getCropRect() : null, flipState);
    }

    /** Exception for error during transcoding image. */
    public static final class CodecFailedException extends Exception {
        public enum FailureType {
            ENCODE_FAILED,
            DECODE_FAILED,
            UNKNOWN
        }

        private CodecFailedException.FailureType mFailureType;

        CodecFailedException(String message) {
            super(message);
            mFailureType = CodecFailedException.FailureType.UNKNOWN;
        }

        CodecFailedException(String message, CodecFailedException.FailureType failureType) {
            super(message);
            mFailureType = failureType;
        }

        @NonNull
        public CodecFailedException.FailureType getFailureType() {
            return mFailureType;
        }
    }
}
