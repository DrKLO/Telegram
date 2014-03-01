/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ApplicationActivity;
import org.telegram.ui.PhotoCropActivity;

import java.io.File;

public class AvatarUpdater implements NotificationCenter.NotificationCenterDelegate, PhotoCropActivity.PhotoCropActivityDelegate {
    public String currentPicturePath;
    private TLRPC.PhotoSize smallPhoto;
    private TLRPC.PhotoSize bigPhoto;
    public String uploadingAvatar = null;
    File picturePath = null;
    public Activity parentActivity = null;
    public BaseFragment parentFragment = null;
    public AvatarUpdaterDelegate delegate;
    private boolean clearAfterUpdate = false;
    public boolean returnOnly = false;

    public static abstract interface AvatarUpdaterDelegate {
        public abstract void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big);
    }

    public void clear() {
        if (uploadingAvatar != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            parentActivity = null;
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
            if (parentFragment != null) {
                parentFragment.startActivityForResult(takePictureIntent, 0);
            } else if (parentActivity != null) {
                parentActivity.startActivityForResult(takePictureIntent, 0);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void openGallery() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            if (parentFragment != null) {
                parentFragment.startActivityForResult(photoPickerIntent, 1);
            } else if (parentActivity != null) {
                parentActivity.startActivityForResult(photoPickerIntent, 1);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void startCrop(String path) {
        try {
            if (parentFragment != null) {
                ApplicationActivity activity = (ApplicationActivity)parentFragment.parentActivity;
                if (activity == null) {
                    activity = (ApplicationActivity)parentFragment.getActivity();
                }
                if (activity == null) {
                    return;
                }
                Bundle params = new Bundle();
                params.putString("photoPath", path);
                PhotoCropActivity photoCropActivity = new PhotoCropActivity();
                photoCropActivity.delegate = this;
                photoCropActivity.setArguments(params);
                activity.presentFragment(photoCropActivity, "crop", false);
            } else {
                Intent cropIntent = new Intent("com.android.camera.action.CROP");
                cropIntent.setDataAndType(Uri.fromFile(new File(path)), "image/*");
                cropIntent.putExtra("crop", "true");
                cropIntent.putExtra("aspectX", 1);
                cropIntent.putExtra("aspectY", 1);
                cropIntent.putExtra("outputX", 800);
                cropIntent.putExtra("outputY", 800);
                cropIntent.putExtra("scale", true);
                cropIntent.putExtra("return-data", false);
                picturePath = Utilities.generatePicturePath();
                cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(picturePath));
                cropIntent.putExtra("output", Uri.fromFile(picturePath));
                if (parentFragment != null) {
                    parentFragment.startActivityForResult(cropIntent, 2);
                } else if (parentActivity != null) {
                    parentActivity.startActivityForResult(cropIntent, 2);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            Bitmap bitmap = FileLoader.loadBitmap(path, 800, 800);
            processBitmap(bitmap);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {
                Utilities.addMediaToGallery(currentPicturePath);
                startCrop(currentPicturePath);

                currentPicturePath = null;
            } else if (requestCode == 1) {
                if (data == null) {
                    return;
                }
                try {
                    Uri imageUri = data.getData();
                    if (imageUri != null) {
                        String imageFilePath = Utilities.getPath(imageUri);
                        startCrop(imageFilePath);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            } else if (requestCode == 2) {
                Bitmap bitmap = FileLoader.loadBitmap(picturePath.getAbsolutePath(), 800, 800);
                processBitmap(bitmap);
            }
        }
    }

    private void processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        smallPhoto = FileLoader.scaleAndSaveImage(bitmap, 100, 100, 87, false);
        bigPhoto = FileLoader.scaleAndSaveImage(bitmap, 800, 800, 87, false);
        if (bigPhoto != null && smallPhoto != null) {
            if (returnOnly) {
                if (delegate != null) {
                    delegate.didUploadedPhoto(null, smallPhoto, bigPhoto);
                }
            } else {
                UserConfig.saveConfig(false);
                uploadingAvatar = Utilities.getCacheDir() + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
                NotificationCenter.Instance.addObserver(AvatarUpdater.this, FileLoader.FileDidUpload);
                NotificationCenter.Instance.addObserver(AvatarUpdater.this, FileLoader.FileDidFailUpload);
                FileLoader.Instance.uploadFile(uploadingAvatar, null, null);
            }
        }
    }

    @Override
    public void didFinishCrop(Bitmap bitmap) {
        processBitmap(bitmap);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == FileLoader.FileDidUpload) {
            String location = (String)args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.Instance.removeObserver(AvatarUpdater.this, FileLoader.FileDidUpload);
                        NotificationCenter.Instance.removeObserver(AvatarUpdater.this, FileLoader.FileDidFailUpload);
                        if (delegate != null) {
                            delegate.didUploadedPhoto((TLRPC.InputFile)args[1], smallPhoto, bigPhoto);
                        }
                        uploadingAvatar = null;
                        if (clearAfterUpdate) {
                            parentFragment = null;
                            parentActivity = null;
                            delegate = null;
                        }
                    }
                });
            }
        } else if (id == FileLoader.FileDidFailUpload) {
            String location = (String)args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.Instance.removeObserver(AvatarUpdater.this, FileLoader.FileDidUpload);
                        NotificationCenter.Instance.removeObserver(AvatarUpdater.this, FileLoader.FileDidFailUpload);
                        uploadingAvatar = null;
                        //delegate.didUploadedPhoto(null, null, null);
                        if (clearAfterUpdate) {
                            parentFragment = null;
                            parentActivity = null;
                            delegate = null;
                        }
                    }
                });
            }
        }
    }
}
