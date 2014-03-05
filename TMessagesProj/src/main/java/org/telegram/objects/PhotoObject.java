/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.objects;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLoader;

import java.util.ArrayList;

public class PhotoObject {
    public TLRPC.PhotoSize photoOwner;
    public Bitmap image;

    public PhotoObject(TLRPC.PhotoSize photo) {
        photoOwner = photo;

        if (photo instanceof TLRPC.TL_photoCachedSize) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inDither = false;
            opts.outWidth = photo.w;
            opts.outHeight = photo.h;
            image = BitmapFactory.decodeByteArray(photoOwner.bytes, 0, photoOwner.bytes.length, opts);
            if (image != null && FileLoader.Instance.runtimeHack != null) {
                FileLoader.Instance.runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
            }
        }
    }

    public static PhotoObject getClosestImageWithSize(ArrayList<PhotoObject> arr, int width, int height) {
        int closestWidth = 9999;
        int closestHeight = 9999;
        PhotoObject closestObject = null;
        for (PhotoObject obj : arr) {
            if (obj == null || obj.photoOwner == null) {
                continue;
            }
            int diffW = Math.abs(obj.photoOwner.w - width);
            int diffH = Math.abs(obj.photoOwner.h - height);
            if (closestObject == null || closestWidth > diffW || closestHeight > diffH || closestObject.photoOwner instanceof TLRPC.TL_photoCachedSize) {
                closestObject = obj;
                closestWidth = diffW;
                closestHeight = diffH;
            }
        }
        return closestObject;
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int width, int height) {
        int closestWidth = 9999;
        int closestHeight = 9999;
        TLRPC.PhotoSize closestObject = null;
        for (TLRPC.PhotoSize obj : sizes) {
            int diffW = Math.abs(obj.w - width);
            int diffH = Math.abs(obj.h - height);
            if (closestObject == null || closestObject instanceof TLRPC.TL_photoCachedSize || closestWidth > diffW || closestHeight > diffH) {
                closestObject = obj;
                closestWidth = diffW;
                closestHeight = diffH;
            }
        }
        return closestObject;
    }
}
