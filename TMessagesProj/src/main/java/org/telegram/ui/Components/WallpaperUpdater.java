/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;

public class WallpaperUpdater {

    private String currentPicturePath;
    private File picturePath = null;
    private Activity parentActivity;
    private WallpaperUpdaterDelegate delegate;
    private File currentWallpaperPath;

    public interface WallpaperUpdaterDelegate {
        void didSelectWallpaper(File file, Bitmap bitmap);
        void needOpenColorPicker();
    }

    public WallpaperUpdater(Activity activity, WallpaperUpdaterDelegate wallpaperUpdaterDelegate) {
        parentActivity = activity;
        delegate = wallpaperUpdaterDelegate;
        currentWallpaperPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.random.nextInt() + ".jpg");
    }

    public void showAlert(final boolean fromTheme) {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        CharSequence[] items;
        if (fromTheme) {
            items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("SelectColor", R.string.SelectColor), LocaleController.getString("Default", R.string.Default), LocaleController.getString("Cancel", R.string.Cancel)};
        } else {
            items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("Cancel", R.string.Cancel)};
        }
        builder.setItems(items, (dialogInterface, i) -> {
            try {
                if (i == 0) {
                    try {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        File image = AndroidUtilities.generatePicturePath();
                        if (image != null) {
                            if (Build.VERSION.SDK_INT >= 24) {
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(parentActivity, BuildConfig.APPLICATION_ID + ".provider", image));
                                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } else {
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                            }
                            currentPicturePath = image.getAbsolutePath();
                        }
                        parentActivity.startActivityForResult(takePictureIntent, 10);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (i == 1) {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    parentActivity.startActivityForResult(photoPickerIntent, 11);
                } else if (fromTheme) {
                    if (i == 2) {
                        delegate.needOpenColorPicker();
                    } else if (i == 3) {
                        delegate.didSelectWallpaper(null, null);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        builder.show();
    }

    public void cleanup() {
        currentWallpaperPath.delete();
    }

    public File getCurrentWallpaperPath() {
        return currentWallpaperPath;
    }

    public String getCurrentPicturePath() {
        return currentPicturePath;
    }

    public void setCurrentPicturePath(String value) {
        currentPicturePath = value;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 10) {
                AndroidUtilities.addMediaToGallery(currentPicturePath);
                FileOutputStream stream = null;
                try {
                    Point screenSize = AndroidUtilities.getRealScreenSize();
                    Bitmap bitmap = ImageLoader.loadBitmap(currentPicturePath, null, screenSize.x, screenSize.y, true);
                    stream = new FileOutputStream(currentWallpaperPath);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    delegate.didSelectWallpaper(currentWallpaperPath, bitmap);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                currentPicturePath = null;
            } else if (requestCode == 11) {
                if (data == null || data.getData() == null) {
                    return;
                }
                try {
                    Point screenSize = AndroidUtilities.getRealScreenSize();
                    Bitmap bitmap = ImageLoader.loadBitmap(null, data.getData(), screenSize.x, screenSize.y, true);
                    FileOutputStream stream = new FileOutputStream(currentWallpaperPath);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    delegate.didSelectWallpaper(currentWallpaperPath, bitmap);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }
}
