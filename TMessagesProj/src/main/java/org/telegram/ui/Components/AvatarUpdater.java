/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.android.MediaController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoAlbumPickerActivity;
import org.telegram.ui.PhotoCropActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;

public class AvatarUpdater implements NotificationCenter.NotificationCenterDelegate, PhotoCropActivity.PhotoEditActivityDelegate {

    public String currentPicturePath;
    private TLRPC.PhotoSize smallPhoto;
    private TLRPC.PhotoSize bigPhoto;
    public String uploadingAvatar = null;
    File picturePath = null;
    public BaseFragment parentFragment = null;
    public AvatarUpdaterDelegate delegate;
    private boolean clearAfterUpdate = false;
    public boolean returnOnly = false;

    public interface AvatarUpdaterDelegate {
        void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big);
    }

    public void clear() {
        if (uploadingAvatar != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            delegate = null;
        }
    }

    public void openCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File image = Utilities.generatePicturePath();
            if (image != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                currentPicturePath = image.getAbsolutePath();
            }
            parentFragment.startActivityForResult(takePictureIntent, 13);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void openGallery() {
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(true);
        fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<String> photos, ArrayList<MediaController.SearchImage> webPhotos) {
                if (!photos.isEmpty()) {
                    Bitmap bitmap = ImageLoader.loadBitmap(photos.get(0), null, 800, 800, true);
                    processBitmap(bitmap);
                }
            }

            @Override
            public void startPhotoSelectActivity() {
                try {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    photoPickerIntent.setType("image/*");
                    parentFragment.startActivityForResult(photoPickerIntent, 14);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
        parentFragment.presentFragment(fragment);
    }

    private void startCrop(String path, Uri uri) {
        try {
            LaunchActivity activity = (LaunchActivity)parentFragment.getParentActivity();
            if (activity == null) {
                return;
            }
            Bundle args = new Bundle();
            if (path != null) {
                args.putString("photoPath", path);
            } else if (uri != null) {
                args.putParcelable("photoUri", uri);
            }
            PhotoCropActivity photoCropActivity = new PhotoCropActivity(args);
            photoCropActivity.setDelegate(this);
            activity.presentFragment(photoCropActivity);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            Bitmap bitmap = ImageLoader.loadBitmap(path, uri, 800, 800, true);
            processBitmap(bitmap);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 13) {
                PhotoViewer.getInstance().setParentActivity(parentFragment.getParentActivity());
                int orientation = 0;
                try {
                    ExifInterface ei = new ExifInterface(currentPicturePath);
                    int exif = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch(exif) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            orientation = 90;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            orientation = 180;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            orientation = 270;
                            break;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                final ArrayList<Object> arrayList = new ArrayList<>();
                arrayList.add(new MediaController.PhotoEntry(0, 0, 0, currentPicturePath, orientation));
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, 1, new PhotoViewer.EmptyPhotoViewerProvider() {
                    @Override
                    public void sendButtonPressed(int index) {
                        String path = null;
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) arrayList.get(0);
                        if (photoEntry.imagePath != null) {
                            path = photoEntry.imagePath;
                        } else if (photoEntry.path != null) {
                            path = photoEntry.path;
                        }
                        Bitmap bitmap = ImageLoader.loadBitmap(path, null, 800, 800, true);
                        processBitmap(bitmap);
                    }
                });
                Utilities.addMediaToGallery(currentPicturePath);
                currentPicturePath = null;
            } else if (requestCode == 14) {
                if (data == null || data.getData() == null) {
                    return;
                }
                startCrop(null, data.getData());
            }
        }
    }

    private void processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 100, 100, 80, false);
        bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
        if (bigPhoto != null && smallPhoto != null) {
            if (returnOnly) {
                if (delegate != null) {
                    delegate.didUploadedPhoto(null, smallPhoto, bigPhoto);
                }
            } else {
                UserConfig.saveConfig(false);
                uploadingAvatar = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
                NotificationCenter.getInstance().addObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().addObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                FileLoader.getInstance().uploadFile(uploadingAvatar, false, true);
            }
        }
    }

    @Override
    public void didFinishEdit(Bitmap bitmap, Bundle args) {
        processBitmap(bitmap);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            String location = (String)args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                        NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                        if (delegate != null) {
                            delegate.didUploadedPhoto((TLRPC.InputFile)args[1], smallPhoto, bigPhoto);
                        }
                        uploadingAvatar = null;
                        if (clearAfterUpdate) {
                            parentFragment = null;
                            delegate = null;
                        }
                    }
                });
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            String location = (String)args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                        NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                        uploadingAvatar = null;
                        if (clearAfterUpdate) {
                            parentFragment = null;
                            delegate = null;
                        }
                    }
                });
            }
        }
    }
}
