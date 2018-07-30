/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.support.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoAlbumPickerActivity;
import org.telegram.ui.PhotoCropActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;

public class ImageUpdater implements NotificationCenter.NotificationCenterDelegate, PhotoCropActivity.PhotoEditActivityDelegate {

    public BaseFragment parentFragment;
    public ImageUpdaterDelegate delegate;

    private int currentAccount = UserConfig.selectedAccount;
    public String currentPicturePath;
    private TLRPC.PhotoSize smallPhoto;
    private TLRPC.PhotoSize bigPhoto;
    public String uploadingImage;
    File picturePath = null;
    private boolean clearAfterUpdate;
    public boolean returnOnly;
    public boolean encryptedDocument;

    public interface ImageUpdaterDelegate {
        void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big, TLRPC.TL_secureFile secureFile);
    }

    public void clear() {
        if (uploadingImage != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            delegate = null;
        }
    }

    public void openCamera() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 19);
                return;
            }
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File image = AndroidUtilities.generatePicturePath();
            if (image != null) {
                if (Build.VERSION.SDK_INT >= 24) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(parentFragment.getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", image));
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                }
                currentPicturePath = image.getAbsolutePath();
            }
            parentFragment.startActivityForResult(takePictureIntent, 13);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void openGallery() {
        if (parentFragment == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && parentFragment != null && parentFragment.getParentActivity() != null) {
            if (parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
        }
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(true, false, false, null);
        fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos) {
                if (!photos.isEmpty()) {
                    Bitmap bitmap = ImageLoader.loadBitmap(photos.get(0).path, null, 800, 800, true);
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
                    FileLog.e(e);
                }
            }
        });
        parentFragment.presentFragment(fragment);
    }

    private void startCrop(String path, Uri uri) {
        try {
            LaunchActivity activity = (LaunchActivity) parentFragment.getParentActivity();
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
            FileLog.e(e);
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
                    switch (exif) {
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
                    FileLog.e(e);
                }
                final ArrayList<Object> arrayList = new ArrayList<>();
                arrayList.add(new MediaController.PhotoEntry(0, 0, 0, currentPicturePath, orientation, false));
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, 1, new PhotoViewer.EmptyPhotoViewerProvider() {
                    @Override
                    public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
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

                    @Override
                    public boolean allowCaption() {
                        return false;
                    }

                    @Override
                    public boolean canScrollAway() {
                        return false;
                    }
                }, null);
                AndroidUtilities.addMediaToGallery(currentPicturePath);
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
        if (encryptedDocument) {
            bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 2048, 2048, 89, false, 320, 320);
            if (delegate != null) {
                TLRPC.TL_secureFile secureFile = new TLRPC.TL_secureFile();
                secureFile.dc_id = (int) bigPhoto.location.volume_id;
                secureFile.id = bigPhoto.location.local_id;
                secureFile.date = (int) (System.currentTimeMillis() / 1000);
                delegate.didUploadedPhoto(null, null, null, secureFile);
            }
            SharedConfig.saveConfig();
        } else {
            smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 100, 100, 80, false);
            bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
            bitmap.recycle();
            if (bigPhoto != null && smallPhoto != null) {
                if (returnOnly) {
                    if (delegate != null) {
                        delegate.didUploadedPhoto(null, smallPhoto, bigPhoto, null);
                    }
                } else {
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    uploadingImage = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
                    NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.FileDidUpload);
                    NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.FileDidFailUpload);
                    FileLoader.getInstance(currentAccount).uploadFile(uploadingImage, false, true, ConnectionsManager.FileTypePhoto);
                }
            }
        }
    }

    @Override
    public void didFinishEdit(Bitmap bitmap) {
        processBitmap(bitmap);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            String location = (String) args[0];
            if (uploadingImage != null && location.equals(uploadingImage)) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidFailUpload);
                if (delegate != null) {
                    delegate.didUploadedPhoto((TLRPC.InputFile) args[1], smallPhoto, bigPhoto, null);
                }
                uploadingImage = null;
                if (clearAfterUpdate) {
                    parentFragment = null;
                    delegate = null;
                }
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            String location = (String) args[0];
            if (uploadingImage != null && location.equals(uploadingImage)) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidFailUpload);
                uploadingImage = null;
                if (clearAfterUpdate) {
                    parentFragment = null;
                    delegate = null;
                }
            }
        }
    }
}
