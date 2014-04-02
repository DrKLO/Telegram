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

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLoader;

import java.util.ArrayList;

public class PhotoObject extends AttachmentObject<TLRPC.PhotoSize> {
    static {
        new AttachmentObjectWrapper.AttachmentObjectFactory<PhotoObject>() {

            @Override
            public PhotoObject create(TLObject rawObject) {
                if (rawObject instanceof TLRPC.PhotoSize) {
                    return new PhotoObject((TLRPC.PhotoSize)rawObject);
                }
                throw new IllegalStateException();
            }
        };
    }

    public Bitmap image;

    public PhotoObject(TLRPC.PhotoSize photo) {
        super(TLRPC.PhotoSize.class, photo);

        if (photo instanceof TLRPC.TL_photoCachedSize) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inDither = false;
            opts.outWidth = photo.w;
            opts.outHeight = photo.h;
            image = BitmapFactory.decodeByteArray(rawObject.bytes, 0, rawObject.bytes.length, opts);
            if (image != null && FileLoader.getInstance().runtimeHack != null) {
                FileLoader.getInstance().runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
            }
        }
    }

    @Override
    public int getSize() {
        return rawObject.size;
    }

    @Override
    public String getAttachmentFileName() {
        return rawObject.location.volume_id + "_" + rawObject.location.local_id + getAttachmentFileExtension();
    }

    @Override
    public String getAttachmentFileExtension() {
        return ".jpg";
    }

    public static PhotoObject getClosestImageWithSize(ArrayList<PhotoObject> arr, int width, int height) {
        int closestWidth = 9999;
        int closestHeight = 9999;
        PhotoObject closestObject = null;
        for (PhotoObject obj : arr) {
            if (obj == null || obj.rawObject == null) {
                continue;
            }
            int diffW = Math.abs(obj.rawObject.w - width);
            int diffH = Math.abs(obj.rawObject.h - height);
            if (closestObject == null || closestWidth > diffW || closestHeight > diffH || closestObject.rawObject instanceof TLRPC.TL_photoCachedSize) {
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

    public TLRPC.FileLocation getLocation() {
        return rawObject.location;
    }

    public int getWidth() {
        return rawObject.w;
    }

    public int getHeight() {
        return rawObject.h;
    }
}
