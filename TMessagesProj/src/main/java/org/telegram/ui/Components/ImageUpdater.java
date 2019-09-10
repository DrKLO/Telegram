/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import android.util.TypedValue;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoAlbumPickerActivity;
import org.telegram.ui.PhotoCropActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PhotoPickerActivity;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ImageUpdater implements NotificationCenter.NotificationCenterDelegate, PhotoCropActivity.PhotoEditActivityDelegate {

    public BaseFragment parentFragment;
    public ImageUpdaterDelegate delegate;

    private int currentAccount = UserConfig.selectedAccount;
    private ImageReceiver imageReceiver;
    public String currentPicturePath;
    private TLRPC.PhotoSize bigPhoto;
    private TLRPC.PhotoSize smallPhoto;
    public String uploadingImage;
    private File picturePath = null;
    private String finalPath;
    private boolean clearAfterUpdate;

    private boolean searchAvailable = true;
    private boolean uploadAfterSelect = true;

    public interface ImageUpdaterDelegate {
        void didUploadPhoto(TLRPC.InputFile file, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize);

        default String getInitialSearchString() {
            return null;
        }
    }

    public void clear() {
        if (uploadingImage != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            delegate = null;
        }
    }

    public ImageUpdater() {
        imageReceiver = new ImageReceiver(null);
    }

    public void openMenu(boolean hasAvatar, Runnable onDeleteAvatar) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(parentFragment.getParentActivity());
        builder.setTitle(LocaleController.getString("ChoosePhoto", R.string.ChoosePhoto));

        CharSequence[] items;
        int[] icons;

        if (searchAvailable) {
            if (hasAvatar) {
                items = new CharSequence[]{LocaleController.getString("ChooseTakePhoto", R.string.ChooseTakePhoto), LocaleController.getString("ChooseFromGallery", R.string.ChooseFromGallery), LocaleController.getString("ChooseFromSearch", R.string.ChooseFromSearch), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                icons = new int[]{R.drawable.menu_camera, R.drawable.profile_photos, R.drawable.menu_search, R.drawable.chats_delete};
            } else {
                items = new CharSequence[]{LocaleController.getString("ChooseTakePhoto", R.string.ChooseTakePhoto), LocaleController.getString("ChooseFromGallery", R.string.ChooseFromGallery), LocaleController.getString("ChooseFromSearch", R.string.ChooseFromSearch)};
                icons = new int[]{R.drawable.menu_camera, R.drawable.profile_photos, R.drawable.menu_search};
            }
        } else {
            if (hasAvatar) {
                items = new CharSequence[]{LocaleController.getString("ChooseTakePhoto", R.string.ChooseTakePhoto), LocaleController.getString("ChooseFromGallery", R.string.ChooseFromGallery), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                icons = new int[]{R.drawable.menu_camera, R.drawable.profile_photos, R.drawable.chats_delete};
            } else {
                items = new CharSequence[]{LocaleController.getString("ChooseTakePhoto", R.string.ChooseTakePhoto), LocaleController.getString("ChooseFromGallery", R.string.ChooseFromGallery)};
                icons = new int[]{R.drawable.menu_camera, R.drawable.profile_photos};
            }
        }

        builder.setItems(items, icons, (dialogInterface, i) -> {
            if (i == 0) {
                openCamera();
            } else if (i == 1) {
                openGallery();
            } else if (searchAvailable && i == 2) {
                openSearch();
            } else if (searchAvailable && i == 3 || i == 2) {
                onDeleteAvatar.run();
            }
        });
        BottomSheet sheet = builder.create();
        parentFragment.showDialog(sheet);
        TextView titleView = sheet.getTitleView();
        if (titleView != null) {
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
        sheet.setItemColor(searchAvailable ? 3 : 2, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
    }

    public void setSearchAvailable(boolean value) {
        searchAvailable = value;
    }

    public void setUploadAfterSelect(boolean value) {
        uploadAfterSelect = value;
    }

    public void openSearch() {
        if (parentFragment == null) {
            return;
        }
        final HashMap<Object, Object> photos = new HashMap<>();
        final ArrayList<Object> order = new ArrayList<>();
        PhotoPickerActivity fragment = new PhotoPickerActivity(0, null, photos, order, new ArrayList<>(), 1, false, null);
        fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {

            private boolean sendPressed;

            @Override
            public void selectedPhotosChanged() {

            }

            private void sendSelectedPhotos(HashMap<Object, Object> photos, ArrayList<Object> order, boolean notify, int scheduleDate) {

            }

            @Override
            public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
                if (photos.isEmpty() || delegate == null || sendPressed || canceled) {
                    return;
                }
                sendPressed = true;

                ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
                for (int a = 0; a < order.size(); a++) {
                    Object object = photos.get(order.get(a));
                    SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                    media.add(info);
                    if (object instanceof MediaController.SearchImage) {
                        MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                        if (searchImage.imagePath != null) {
                            info.path = searchImage.imagePath;
                        } else {
                            info.searchImage = searchImage;
                        }
                        info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                        info.entities = searchImage.entities;
                        info.masks = !searchImage.stickers.isEmpty() ? new ArrayList<>(searchImage.stickers) : null;
                        info.ttl = searchImage.ttl;
                    }
                }
                didSelectPhotos(media);
            }

            @Override
            public void onCaptionChanged(CharSequence caption) {

            }
        });
        fragment.setInitialSearchString(delegate.getInitialSearchString());
        parentFragment.presentFragment(fragment);
    }

    private void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos) {
        if (!photos.isEmpty()) {
            SendMessagesHelper.SendingMediaInfo info = photos.get(0);
            Bitmap bitmap = null;
            if (info.path != null) {
                bitmap = ImageLoader.loadBitmap(info.path, null, 800, 800, true);
            } else if (info.searchImage != null) {
                if (info.searchImage.photo != null) {
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(info.searchImage.photo.sizes, AndroidUtilities.getPhotoSize());
                    if (photoSize != null) {
                        File path = FileLoader.getPathToAttach(photoSize, true);
                        finalPath = path.getAbsolutePath();
                        if (!path.exists()) {
                            path = FileLoader.getPathToAttach(photoSize, false);
                            if (!path.exists()) {
                                path = null;
                            }
                        }
                        if (path != null) {
                            bitmap = ImageLoader.loadBitmap(path.getAbsolutePath(), null, 800, 800, true);
                        } else {
                            NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileDidLoad);
                            NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileDidFailToLoad);
                            uploadingImage = FileLoader.getAttachFileName(photoSize.location);
                            imageReceiver.setImage(ImageLocation.getForPhoto(photoSize, info.searchImage.photo), null, null, "jpg", null, 1);
                        }
                    }
                } else if (info.searchImage.imageUrl != null) {
                    String md5 = Utilities.MD5(info.searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.imageUrl, "jpg");
                    File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                    finalPath = cacheFile.getAbsolutePath();
                    if (cacheFile.exists() && cacheFile.length() != 0) {
                        bitmap = ImageLoader.loadBitmap(cacheFile.getAbsolutePath(), null, 800, 800, true);
                    } else {
                        uploadingImage = info.searchImage.imageUrl;
                        NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.httpFileDidLoad);
                        NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.httpFileDidFailedLoad);
                        imageReceiver.setImage(info.searchImage.imageUrl, null, null, "jpg", 1);
                    }
                } else {
                    bitmap = null;
                }
            }
            processBitmap(bitmap);
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
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(1, false, false, null);
        fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                ImageUpdater.this.didSelectPhotos(photos);
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
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, PhotoViewer.SELECT_TYPE_AVATAR, new PhotoViewer.EmptyPhotoViewerProvider() {
                    @Override
                    public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate) {
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
        bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
        smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 150, 150, 80, false, 150, 150);
        if (smallPhoto != null) {
            try {
                Bitmap b = BitmapFactory.decodeFile(FileLoader.getPathToAttach(smallPhoto, true).getAbsolutePath());
                String key = smallPhoto.location.volume_id + "_" + smallPhoto.location.local_id + "@50_50";
                ImageLoader.getInstance().putImageToCache(new BitmapDrawable(b), key);
            } catch (Throwable ignore) {

            }
        }
        bitmap.recycle();
        if (bigPhoto != null) {
            UserConfig.getInstance(currentAccount).saveConfig(false);
            uploadingImage = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            if (uploadAfterSelect) {
                NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.FileDidFailUpload);
                FileLoader.getInstance(currentAccount).uploadFile(uploadingImage, false, true, ConnectionsManager.FileTypePhoto);
            }
            if (delegate != null) {
                delegate.didUploadPhoto(null, bigPhoto, smallPhoto);
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
            if (location.equals(uploadingImage)) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidFailUpload);
                if (delegate != null) {
                    delegate.didUploadPhoto((TLRPC.InputFile) args[1], bigPhoto, smallPhoto);
                }
                uploadingImage = null;
                if (clearAfterUpdate) {
                    imageReceiver.setImageBitmap((Drawable) null);
                    parentFragment = null;
                    delegate = null;
                }
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            String location = (String) args[0];
            if (location.equals(uploadingImage)) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.FileDidFailUpload);
                uploadingImage = null;
                if (clearAfterUpdate) {
                    imageReceiver.setImageBitmap((Drawable) null);
                    parentFragment = null;
                    delegate = null;
                }
            }
        } else if (id == NotificationCenter.fileDidLoad || id == NotificationCenter.fileDidFailToLoad || id == NotificationCenter.httpFileDidLoad || id == NotificationCenter.httpFileDidFailedLoad) {
            String path = (String) args[0];
            if (path.equals(uploadingImage)) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileDidLoad);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileDidFailToLoad);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.httpFileDidLoad);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.httpFileDidFailedLoad);

                uploadingImage = null;
                if (id == NotificationCenter.fileDidLoad || id == NotificationCenter.httpFileDidLoad) {
                    Bitmap bitmap = ImageLoader.loadBitmap(finalPath, null, 800, 800, true);
                    processBitmap(bitmap);
                } else {
                    imageReceiver.setImageBitmap((Drawable) null);
                }
            }
        }
    }
}
