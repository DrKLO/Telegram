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
import android.app.Dialog;
import android.content.DialogInterface;
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
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
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
    private ImageUpdaterDelegate delegate;
    private ChatAttachAlert chatAttachAlert;

    private int currentAccount = UserConfig.selectedAccount;
    private ImageReceiver imageReceiver;
    public String currentPicturePath;
    private TLRPC.PhotoSize bigPhoto;
    private TLRPC.PhotoSize smallPhoto;
    private String uploadingImage;
    private String uploadingVideo;
    private String videoPath;
    private MessageObject convertingVideo;
    private File picturePath = null;
    private String finalPath;
    private boolean clearAfterUpdate;
    private boolean useAttachMenu = true;
    private boolean openWithFrontfaceCamera;

    private boolean searchAvailable = true;
    private boolean uploadAfterSelect = true;

    private TLRPC.InputFile uploadedPhoto;
    private TLRPC.InputFile uploadedVideo;
    private double videoTimestamp;

    private boolean canSelectVideo;
    private boolean forceDarkTheme;
    private boolean showingFromDialog;

    private final static int attach_photo = 0;

    public interface ImageUpdaterDelegate {
        void didUploadPhoto(TLRPC.InputFile photo, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize);

        default String getInitialSearchString() {
            return null;
        }

        default void onUploadProgressChanged(float progress) {

        }

        default void didStartUpload(boolean isVideo) {

        }
    }

    public boolean isUploadingImage() {
        return uploadingImage != null || uploadingVideo != null || convertingVideo != null;
    }

    public void clear() {
        if (uploadingImage != null || uploadingVideo != null || convertingVideo != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            delegate = null;
        }
        if (chatAttachAlert != null) {
            chatAttachAlert.dismissInternal();
            chatAttachAlert.onDestroy();
        }
    }

    public void setOpenWithFrontfaceCamera(boolean value) {
        openWithFrontfaceCamera = value;
    }

    public ImageUpdater(boolean allowVideo) {
        imageReceiver = new ImageReceiver(null);
        canSelectVideo = allowVideo && Build.VERSION.SDK_INT > 18;
    }

    public void setDelegate(ImageUpdaterDelegate imageUpdaterDelegate) {
        delegate = imageUpdaterDelegate;
    }

    public void openMenu(boolean hasAvatar, Runnable onDeleteAvatar, DialogInterface.OnDismissListener onDismiss) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        if (useAttachMenu) {
            openAttachMenu(onDismiss);
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(parentFragment.getParentActivity());
        builder.setTitle(LocaleController.getString("ChoosePhoto", R.string.ChoosePhoto), true);

        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();

        items.add(LocaleController.getString("ChooseTakePhoto", R.string.ChooseTakePhoto));
        icons.add(R.drawable.menu_camera);
        ids.add(0);

        if (canSelectVideo) {
            items.add(LocaleController.getString("ChooseRecordVideo", R.string.ChooseRecordVideo));
            icons.add(R.drawable.msg_video);
            ids.add(4);
        }

        items.add(LocaleController.getString("ChooseFromGallery", R.string.ChooseFromGallery));
        icons.add(R.drawable.profile_photos);
        ids.add(1);

        if (searchAvailable) {
            items.add(LocaleController.getString("ChooseFromSearch", R.string.ChooseFromSearch));
            icons.add(R.drawable.menu_search);
            ids.add(2);
        }
        if (hasAvatar) {
            items.add(LocaleController.getString("DeletePhoto", R.string.DeletePhoto));
            icons.add(R.drawable.chats_delete);
            ids.add(3);
        }

        int[] iconsRes = new int[icons.size()];
        for (int i = 0, N = icons.size(); i < N; i++) {
            iconsRes[i] = icons.get(i);
        }

        builder.setItems(items.toArray(new CharSequence[0]), iconsRes, (dialogInterface, i) -> {
            int id = ids.get(i);
            if (id == 0) {
                openCamera();
            } else if (id == 1) {
                openGallery();
            } else if (id == 2) {
                openSearch();
            } else if (id == 3) {
                onDeleteAvatar.run();
            } else if (id == 4) {
                openVideoCamera();
            }
        });
        BottomSheet sheet = builder.create();
        sheet.setOnHideListener(onDismiss);
        parentFragment.showDialog(sheet);
        if (hasAvatar) {
            sheet.setItemColor(items.size() - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
        }
    }

    public void setSearchAvailable(boolean value) {
        useAttachMenu = searchAvailable = value;
    }

    public void setSearchAvailable(boolean value, boolean useAttachMenu) {
        this.useAttachMenu = useAttachMenu;
        searchAvailable = value;
    }

    public void setUploadAfterSelect(boolean value) {
        uploadAfterSelect = value;
    }

    public void onResume() {
        if (chatAttachAlert != null) {
            chatAttachAlert.onResume();
        }
    }

    public void onPause() {
        if (chatAttachAlert != null) {
            chatAttachAlert.onPause();
        }
    }

    public boolean dismissDialogOnPause(Dialog dialog) {
        return dialog != chatAttachAlert;
    }

    public boolean dismissCurrentDialog(Dialog dialog) {
        if (chatAttachAlert != null && dialog == chatAttachAlert) {
            chatAttachAlert.getPhotoLayout().closeCamera(false);
            chatAttachAlert.dismissInternal();
            chatAttachAlert.getPhotoLayout().hideCamera(true);
            return true;
        }
        return false;
    }

    public void openSearch() {
        if (parentFragment == null) {
            return;
        }
        final HashMap<Object, Object> photos = new HashMap<>();
        final ArrayList<Object> order = new ArrayList<>();
        PhotoPickerActivity fragment = new PhotoPickerActivity(0, null, photos, order, 1, false, null, forceDarkTheme);
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
                        info.videoEditedInfo = searchImage.editedInfo;
                        info.thumbPath = searchImage.thumbPath;
                        info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                        info.entities = searchImage.entities;
                        info.masks = searchImage.stickers;
                        info.ttl = searchImage.ttl;
                    }
                }
                didSelectPhotos(media);
            }

            @Override
            public void onCaptionChanged(CharSequence caption) {

            }
        });
        fragment.setMaxSelectedPhotos(1, false);
        fragment.setInitialSearchString(delegate.getInitialSearchString());
        if (showingFromDialog) {
            parentFragment.showAsSheet(fragment);
        } else {
            parentFragment.presentFragment(fragment);
        }
    }

    private void openAttachMenu(DialogInterface.OnDismissListener onDismissListener) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        createChatAttachView();
        chatAttachAlert.setOpenWithFrontFaceCamera(openWithFrontfaceCamera);
        chatAttachAlert.setMaxSelectedPhotos(1, false);
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            AndroidUtilities.hideKeyboard(parentFragment.getFragmentView().findFocus());
        }
        chatAttachAlert.init();
        chatAttachAlert.setOnHideListener(onDismissListener);
        parentFragment.showDialog(chatAttachAlert);
    }

    private void createChatAttachView() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        if (chatAttachAlert == null) {
            chatAttachAlert = new ChatAttachAlert(parentFragment.getParentActivity(), parentFragment, forceDarkTheme, showingFromDialog);
            chatAttachAlert.setAvatarPicker(canSelectVideo ? 2 : 1, searchAvailable);
            chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {

                @Override
                public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, boolean forceDocument) {
                    if (parentFragment == null || parentFragment.getParentActivity() == null || chatAttachAlert == null) {
                        return;
                    }
                    if (button == 8 || button == 7) {
                        HashMap<Object, Object> photos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                        ArrayList<Object> order = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();

                        ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
                        for (int a = 0; a < order.size(); a++) {
                            Object object = photos.get(order.get(a));
                            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                            media.add(info);
                            if (object instanceof MediaController.PhotoEntry) {
                                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                                if (photoEntry.imagePath != null) {
                                    info.path = photoEntry.imagePath;
                                } else {
                                    info.path = photoEntry.path;
                                }
                                info.thumbPath = photoEntry.thumbPath;
                                info.videoEditedInfo = photoEntry.editedInfo;
                                info.isVideo = photoEntry.isVideo;
                                info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                                info.entities = photoEntry.entities;
                                info.masks = photoEntry.stickers;
                                info.ttl = photoEntry.ttl;
                            } else if (object instanceof MediaController.SearchImage) {
                                MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                                if (searchImage.imagePath != null) {
                                    info.path = searchImage.imagePath;
                                } else {
                                    info.searchImage = searchImage;
                                }
                                info.thumbPath = searchImage.thumbPath;
                                info.videoEditedInfo = searchImage.editedInfo;
                                info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                                info.entities = searchImage.entities;
                                info.masks = searchImage.stickers;
                                info.ttl = searchImage.ttl;
                                if (searchImage.inlineResult != null && searchImage.type == 1) {
                                    info.inlineResult = searchImage.inlineResult;
                                    info.params = searchImage.params;
                                }

                                searchImage.date = (int) (System.currentTimeMillis() / 1000);
                            }
                        }
                        didSelectPhotos(media);

                        if (button != 8) {
                            chatAttachAlert.dismiss();
                        }
                        return;
                    } else {
                        chatAttachAlert.dismissWithButtonClick(button);
                    }
                    processSelectedAttach(button);
                }

                @Override
                public View getRevealView() {
                    return null;
                }

                @Override
                public void didSelectBot(TLRPC.User user) {

                }

                @Override
                public void onCameraOpened() {
                    AndroidUtilities.hideKeyboard(parentFragment.getFragmentView().findFocus());
                }

                @Override
                public boolean needEnterComment() {
                    return false;
                }

                @Override
                public void doOnIdle(Runnable runnable) {
                    runnable.run();
                }

                private void processSelectedAttach(int which) {
                    if (which == attach_photo) {
                        openCamera();
                    }
                }

                @Override
                public void openAvatarsSearch() {
                    openSearch();
                }
            });
        }
    }

    private void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos) {
        if (!photos.isEmpty()) {
            SendMessagesHelper.SendingMediaInfo info = photos.get(0);
            Bitmap bitmap = null;
            MessageObject avatarObject = null;
            if (info.isVideo || info.videoEditedInfo != null) {
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.id = 0;
                message.message = "";
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.action = new TLRPC.TL_messageActionEmpty();
                message.dialog_id = 0;
                avatarObject = new MessageObject(UserConfig.selectedAccount, message, false, false);
                avatarObject.messageOwner.attachPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_avatar.mp4").getAbsolutePath();
                avatarObject.videoEditedInfo = info.videoEditedInfo;
                bitmap = ImageLoader.loadBitmap(info.thumbPath, null, 800, 800, true);
            } else if (info.path != null) {
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
                            NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileLoaded);
                            NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileLoadFailed);
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
                }
            }
            processBitmap(bitmap, avatarObject);
        }
    }

    public void openCamera() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 20);
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

    public void openVideoCamera() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 19);
                return;
            }
            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            File video = AndroidUtilities.generateVideoPath();
            if (video != null) {
                if (Build.VERSION.SDK_INT >= 24) {
                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(parentFragment.getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", video));
                    takeVideoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takeVideoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else if (Build.VERSION.SDK_INT >= 18) {
                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                }
                takeVideoIntent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                takeVideoIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                takeVideoIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10);
                currentPicturePath = video.getAbsolutePath();
            }
            parentFragment.startActivityForResult(takeVideoIntent, 15);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 17 && chatAttachAlert != null) {
            chatAttachAlert.getPhotoLayout().checkCamera(false);
        }
    }

    public void openGallery() {
        if (parentFragment == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && parentFragment.getParentActivity() != null) {
            if (parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
        }
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(canSelectVideo ? PhotoAlbumPickerActivity.SELECT_TYPE_AVATAR_VIDEO : PhotoAlbumPickerActivity.SELECT_TYPE_AVATAR, false, false, null);
        fragment.setAllowSearchImages(searchAvailable);
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
        AndroidUtilities.runOnUIThread(() -> {
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
                processBitmap(bitmap, null);
            }
        });
    }

    public void openPhotoForEdit(String path, String thumb, int orientation, boolean isVideo) {
        final ArrayList<Object> arrayList = new ArrayList<>();
        MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, 0, 0, path, orientation, false, 0, 0, 0);
        photoEntry.isVideo = isVideo;
        photoEntry.thumbPath = thumb;
        arrayList.add(photoEntry);
        PhotoViewer.getInstance().setParentActivity(parentFragment.getParentActivity());
        PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, PhotoViewer.SELECT_TYPE_AVATAR, false, new PhotoViewer.EmptyPhotoViewerProvider() {
            @Override
            public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                String path = null;
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) arrayList.get(0);
                if (photoEntry.imagePath != null) {
                    path = photoEntry.imagePath;
                } else if (photoEntry.path != null) {
                    path = photoEntry.path;
                }
                MessageObject avatarObject = null;
                Bitmap bitmap;
                if (photoEntry.isVideo || photoEntry.editedInfo != null) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = 0;
                    message.message = "";
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.action = new TLRPC.TL_messageActionEmpty();
                    message.dialog_id = 0;
                    avatarObject = new MessageObject(UserConfig.selectedAccount, message, false, false);
                    avatarObject.messageOwner.attachPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_avatar.mp4").getAbsolutePath();
                    avatarObject.videoEditedInfo = photoEntry.editedInfo;
                    bitmap = ImageLoader.loadBitmap(photoEntry.thumbPath, null, 800, 800, true);
                } else {
                    bitmap = ImageLoader.loadBitmap(path, null, 800, 800, true);
                }
                processBitmap(bitmap, avatarObject);
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
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0 || requestCode == 2) {
                createChatAttachView();
                if (chatAttachAlert != null) {
                    chatAttachAlert.onActivityResultFragment(requestCode, data, currentPicturePath);
                }
                currentPicturePath = null;
            } else if (requestCode == 13) {
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
                openPhotoForEdit(currentPicturePath, null, orientation, false);
                AndroidUtilities.addMediaToGallery(currentPicturePath);
                currentPicturePath = null;
            } else if (requestCode == 14) {
                if (data == null || data.getData() == null) {
                    return;
                }
                startCrop(null, data.getData());
            } else if (requestCode == 15) {
                openPhotoForEdit(currentPicturePath, null, 0, true);
                AndroidUtilities.addMediaToGallery(currentPicturePath);
                currentPicturePath = null;
            }
        }
    }

    private void processBitmap(Bitmap bitmap, MessageObject avatarObject) {
        if (bitmap == null) {
            return;
        }
        uploadedVideo = null;
        uploadedPhoto = null;
        convertingVideo = null;
        videoPath = null;
        bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
        smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 150, 150, 80, false, 150, 150);
        if (smallPhoto != null) {
            try {
                Bitmap b = BitmapFactory.decodeFile(FileLoader.getPathToAttach(smallPhoto, true).getAbsolutePath());
                String key = smallPhoto.location.volume_id + "_" + smallPhoto.location.local_id + "@50_50";
                ImageLoader.getInstance().putImageToCache(new BitmapDrawable(b), key, true);
            } catch (Throwable ignore) {

            }
        }
        bitmap.recycle();
        if (bigPhoto != null) {
            UserConfig.getInstance(currentAccount).saveConfig(false);
            uploadingImage = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            if (uploadAfterSelect) {
                if (avatarObject != null && avatarObject.videoEditedInfo != null) {
                    convertingVideo = avatarObject;
                    long startTime = avatarObject.videoEditedInfo.startTime < 0 ? 0 : avatarObject.videoEditedInfo.startTime;
                    videoTimestamp = (avatarObject.videoEditedInfo.avatarStartTime - startTime) / 1000000.0;
                    NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.filePreparingStarted);
                    NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.filePreparingFailed);
                    NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileNewChunkAvailable);
                    MediaController.getInstance().scheduleVideoConvert(avatarObject, true);
                    uploadingImage = null;
                    if (delegate != null) {
                        delegate.didStartUpload(true);
                    }
                } else {
                    if (delegate != null) {
                        delegate.didStartUpload(false);
                    }
                }
                NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileUploaded);
                NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileUploadProgressChanged);
                NotificationCenter.getInstance(currentAccount).addObserver(ImageUpdater.this, NotificationCenter.fileUploadFailed);
                if (uploadingImage != null) {
                    FileLoader.getInstance(currentAccount).uploadFile(uploadingImage, false, true, ConnectionsManager.FileTypePhoto);
                }
            }
            if (delegate != null) {
                delegate.didUploadPhoto(null, null, 0, null, bigPhoto, smallPhoto);
            }
        }
    }

    @Override
    public void didFinishEdit(Bitmap bitmap) {
        processBitmap(bitmap, null);
    }

    private void cleanup() {
        uploadingImage = null;
        uploadingVideo = null;
        videoPath = null;
        convertingVideo = null;
        if (clearAfterUpdate) {
            imageReceiver.setImageBitmap((Drawable) null);
            parentFragment = null;
            delegate = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploaded || id == NotificationCenter.fileUploadFailed) {
            String location = (String) args[0];
            if (location.equals(uploadingImage)) {
                uploadingImage = null;
                if (id == NotificationCenter.fileUploaded) {
                    uploadedPhoto = (TLRPC.InputFile) args[1];
                }
            } else if (location.equals(uploadingVideo)) {
                uploadingVideo = null;
                if (id == NotificationCenter.fileUploaded) {
                    uploadedVideo = (TLRPC.InputFile) args[1];
                }
            } else {
                return;
            }

            if (uploadingImage == null && uploadingVideo == null && convertingVideo == null) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileUploaded);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileUploadProgressChanged);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileUploadFailed);
                if (id == NotificationCenter.fileUploaded) {
                    if (delegate != null) {
                        delegate.didUploadPhoto(uploadedPhoto, uploadedVideo, videoTimestamp, videoPath, bigPhoto, smallPhoto);
                    }
                }
                cleanup();
            }
        } else if (id == NotificationCenter.fileUploadProgressChanged) {
            String location = (String) args[0];
            String path = convertingVideo != null ? uploadingVideo : uploadingImage;
            if (delegate != null && location.equals(path)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float progress = Math.min(1f, loadedSize / (float) totalSize);
                delegate.onUploadProgressChanged(progress);
            }
        } else if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed || id == NotificationCenter.httpFileDidLoad || id == NotificationCenter.httpFileDidFailedLoad) {
            String path = (String) args[0];
            if (path.equals(uploadingImage)) {
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileLoaded);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileLoadFailed);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.httpFileDidLoad);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.httpFileDidFailedLoad);

                uploadingImage = null;
                if (id == NotificationCenter.fileLoaded || id == NotificationCenter.httpFileDidLoad) {
                    Bitmap bitmap = ImageLoader.loadBitmap(finalPath, null, 800, 800, true);
                    processBitmap(bitmap, null);
                } else {
                    imageReceiver.setImageBitmap((Drawable) null);
                }
            }
        } else if (id == NotificationCenter.filePreparingFailed) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject != convertingVideo || parentFragment == null) {
                return;
            }
            parentFragment.getSendMessagesHelper().stopVideoService(messageObject.messageOwner.attachPath);
            NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.filePreparingStarted);
            NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.filePreparingFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileNewChunkAvailable);
            cleanup();
        } else if (id == NotificationCenter.fileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject != convertingVideo || parentFragment == null) {
                return;
            }
            String finalPath = (String) args[1];
            long availableSize = (Long) args[2];
            long finalSize = (Long) args[3];
            parentFragment.getFileLoader().checkUploadNewDataAvailable(finalPath, false, availableSize, finalSize);
            if (finalSize != 0) {
                double lastFrameTimestamp = ((Long) args[5]) / 1000000.0;
                if (videoTimestamp > lastFrameTimestamp) {
                    videoTimestamp = lastFrameTimestamp;
                }

                Bitmap bitmap = SendMessagesHelper.createVideoThumbnailAtTime(finalPath, (long) (videoTimestamp * 1000), null, true);
                if (bitmap != null) {
                    File path = FileLoader.getPathToAttach(smallPhoto, true);
                    if (path != null) {
                        path.delete();
                    }
                    path = FileLoader.getPathToAttach(bigPhoto, true);
                    if (path != null) {
                        path.delete();
                    }
                    bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
                    smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 150, 150, 80, false, 150, 150);
                    if (smallPhoto != null) {
                        try {
                            Bitmap b = BitmapFactory.decodeFile(FileLoader.getPathToAttach(smallPhoto, true).getAbsolutePath());
                            String key = smallPhoto.location.volume_id + "_" + smallPhoto.location.local_id + "@50_50";
                            ImageLoader.getInstance().putImageToCache(new BitmapDrawable(b), key, true);
                        } catch (Throwable ignore) {

                        }
                    }
                }

                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.filePreparingStarted);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.filePreparingFailed);
                NotificationCenter.getInstance(currentAccount).removeObserver(ImageUpdater.this, NotificationCenter.fileNewChunkAvailable);
                parentFragment.getSendMessagesHelper().stopVideoService(messageObject.messageOwner.attachPath);
                uploadingVideo = videoPath = finalPath;
                convertingVideo = null;
            }
        } else if (id == NotificationCenter.filePreparingStarted) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject != convertingVideo || parentFragment == null) {
                return;
            }
            uploadingVideo = (String) args[1];
            parentFragment.getFileLoader().uploadFile(uploadingVideo, false, false, (int) convertingVideo.videoEditedInfo.estimatedSize, ConnectionsManager.FileTypeVideo, false);
        }
    }

    public void setForceDarkTheme(boolean forceDarkTheme) {
        this.forceDarkTheme = forceDarkTheme;
    }

    public void setShowingFromDialog(boolean b) {
        showingFromDialog = b;
    }
}
