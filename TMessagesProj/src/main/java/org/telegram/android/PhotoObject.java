/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class PhotoObject {

    public TLRPC.PhotoSize photoOwner;
    public Bitmap image;

    public PhotoObject(TLRPC.PhotoSize photo, int preview, boolean secret) {
        photoOwner = photo;

        if (preview != 0 && photo instanceof TLRPC.TL_photoCachedSize) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inDither = false;
            opts.outWidth = photo.w;
            opts.outHeight = photo.h;
            try {
                image = BitmapFactory.decodeByteArray(photoOwner.bytes, 0, photoOwner.bytes.length, opts);
                if (image != null) {
                    if (preview == 2) {
                        if (secret) {
                            Utilities.blurBitmap(image, 7);
                            Utilities.blurBitmap(image, 7);
                            Utilities.blurBitmap(image, 7);
                        } else {
                            Utilities.blurBitmap(image, 3);
                        }
                    }
                    if (ImageLoader.getInstance().runtimeHack != null) {
                        ImageLoader.getInstance().runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
                    }
                }
            } catch (Throwable throwable) {
                FileLog.e("tmessages", throwable);
            }
        }
    }

    public static PhotoObject getClosestImageWithSize(ArrayList<PhotoObject> arr, int side) {
        if (arr == null) {
            return null;
        }

        int lastSide = 0;
        PhotoObject closestObject = null;
        for (PhotoObject obj : arr) {
            if (obj == null || obj.photoOwner == null) {
                continue;
            }
            int currentSide = obj.photoOwner.w >= obj.photoOwner.h ? obj.photoOwner.w : obj.photoOwner.h;
            if (closestObject == null || closestObject.photoOwner instanceof TLRPC.TL_photoCachedSize || currentSide <= side && lastSide < currentSide) {
                closestObject = obj;
                lastSide = currentSide;
            }
        }
        return closestObject;
    }
}
