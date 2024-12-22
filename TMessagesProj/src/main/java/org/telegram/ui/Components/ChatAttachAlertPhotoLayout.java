/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.touchSlop;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.VideoEncodingService;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPermissionCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.AlbumButton;
import org.telegram.ui.Stories.recorder.CollageLayout;
import org.telegram.ui.Stories.recorder.CollageLayoutButton;
import org.telegram.ui.Stories.recorder.DownloadButton;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.GalleryListView;
import org.telegram.ui.Stories.recorder.HintTextView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.PhotoVideoSwitcherView;
import org.telegram.ui.Stories.recorder.RecordControl;
import org.telegram.ui.Stories.recorder.SliderView;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.Stories.recorder.ToggleButton;
import org.telegram.ui.Stories.recorder.ToggleButton2;
import org.telegram.ui.Stories.recorder.VideoTimerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatAttachAlertPhotoLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_AVATAR_CONSTRUCTOR = 4;
    private static final int SHOW_FAST_SCROLL_MIN_COUNT = 30;
    private final boolean needCamera;

    private RecyclerListView cameraPhotoRecyclerView;
    private LinearLayoutManager cameraPhotoLayoutManager;
    private PhotoAttachAdapter cameraAttachAdapter;

    private ActionBarMenuItem dropDownContainer;
    public TextView dropDown;
    private Drawable dropDownDrawable;

    public RecyclerListView gridView;
    private GridLayoutManager layoutManager;
    private PhotoAttachAdapter adapter;
    private EmptyTextProgressView progressView;
    private RecyclerViewItemRangeSelector itemRangeSelector;
    private int gridExtraSpace;
    private boolean shouldSelect;
    private int alertOnlyOnce;

    private Drawable cameraDrawable;

    private int currentSelectedCount;

    private boolean isHidden;

    ValueAnimator paddingAnimator;
    private int animateToPadding;

    private BlurringShader.BlurManager blurManager;
    private AnimatorSet cameraInitAnimation;
    protected DualCameraView cameraView;
    private CollageLayoutView2 collageLayoutView;
    private HintView2 dualHint;
    private HintView2 savedDualHint;
    private HintView2 removeCollageHint;
    private CollageLayoutButton collageButton;
    private ToggleButton2 collageRemoveButton;
    private CollageLayoutButton.CollageLayoutListView collageListView;
    private CollageLayout lastCollageLayout;
    private FlashViews.ImageViewInvertable closeButton;
    private int flashButtonResId;
    private ToggleButton2 flashButton;
    private ToggleButton dualButton;
    private RecordControl recordControl;
    private VideoTimerView videoTimerView;
    private HintTextView hintTextView;
    private HintTextView collageHintTextView;
    private HintView2 cameraHint;
    private PhotoVideoSwitcherView modeSwitcherView;
    private FlashViews flashViews;

    protected FrameLayout cameraIcon;
    protected PhotoAttachCameraCell cameraCell;
    private float[] collageViewLocation = new float[2];
    private float collageViewOffsetX;
    private float collageViewOffsetY;
    private float collageViewOffsetBottomY;
    public boolean cameraOpened;
    private boolean canSaveCameraPreview;
    private boolean cameraAnimationInProgress;
    private float cameraOpenProgress;
    private int[] animateCameraValues = new int[5];
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private FrameLayout controlContainer;
    private FrameLayout actionBarContainer;
    private FrameLayout navbarContainer;
    private ZoomControlView zoomControlView;
    private Boolean isCameraFrontfaceBeforeEnteringEditMode = null;
    private TextView counterTextView;
    private boolean takingPhoto;
    private boolean isVideo = false;
    private boolean takingVideo = false;
    private boolean stoppingTakingVideo = false;
    private static boolean mediaFromExternalCamera;
    private static ArrayList<Object> cameraPhotos = new ArrayList<>();
    public static HashMap<Object, Object> selectedPhotos = new HashMap<>();
    public static ArrayList<Object> selectedPhotosOrder = new ArrayList<>();
    public static int lastImageId = -1;
    private boolean cancelTakingPhotos;
    private boolean checkCameraWhenShown;

    private boolean mediaEnabled;
    private boolean videoEnabled;
    private boolean photoEnabled;
    private boolean documentsEnabled;

    private float cameraZoom;

    private float lastY;
    private boolean pressed;
    private boolean maybeStartDraging;
    private boolean dragging;

    private boolean cameraPhotoRecyclerViewIgnoreLayout;

    private int itemSize = dp(80);
    private int lastItemSize = itemSize;
    private int itemsPerRow = 3;

    private boolean deviceHasGoodCamera;
    private boolean noCameraPermissions;
    private boolean noGalleryPermissions;
    private boolean requestingPermissions;

    private boolean ignoreLayout;
    private int lastNotifyWidth;

    private MediaController.AlbumEntry selectedAlbumEntry;
    private MediaController.AlbumEntry galleryAlbumEntry;
    private ArrayList<MediaController.AlbumEntry> dropDownAlbums;
    private float currentPanTranslationY;

    private boolean loading = true;

    public final static int group = 0;
    public final static int compress = 1;
    public final static int spoiler = 2;
    public final static int open_in = 3;
    public final static int preview_gap = 4;
    public final static int media_gap = 5;
    public final static int preview = 6;
    public final static int caption = 7;
    public final static int stars = 8;

    private ActionBarMenuSubItem spoilerItem;
    private ActionBarMenuSubItem compressItem;
    private ActionBarMenuSubItem starsItem;
    protected ActionBarMenuSubItem previewItem;
    public MessagePreviewView.ToggleButton captionItem;

    boolean forceDarkTheme;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private boolean showAvatarConstructor;

    private File outputFile;
    private MediaController.PhotoEntry outputEntry;
    private boolean fromGallery;

    private GestureDetectorFixDoubleTap gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean scrollingY, scrollingX;

    public void updateAvatarPicker() {
        showAvatarConstructor = parentAlert.avatarPicker != 0 && !parentAlert.isPhotoPicker;
    }

    private class BasePhotoProvider extends PhotoViewer.EmptyPhotoViewerProvider {
        @Override
        public boolean isPhotoChecked(int index) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            return photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId);
        }

        @Override
        public int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo) {
            if (parentAlert.maxSelectedPhotos >= 0 && selectedPhotos.size() >= parentAlert.maxSelectedPhotos && !isPhotoChecked(index)) {
                return -1;
            }
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry == null) {
                return -1;
            }
            if (checkSendMediaEnabled(photoEntry)) {
                return -1;
            }
            if (selectedPhotos.size() + 1 > maxCount()) {
                return -1;
            }
            boolean add = true;
            int num;
            if ((num = addToSelectedPhotos(photoEntry, -1)) == -1) {
                num = selectedPhotosOrder.indexOf(photoEntry.imageId);
            } else {
                add = false;
                photoEntry.editedInfo = null;
            }
            photoEntry.editedInfo = videoEditedInfo;

            int count = gridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = gridView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    int tag = (Integer) view.getTag();
                    if (tag == index) {
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            ((PhotoAttachPhotoCell) view).setChecked(num, add, false);
                        } else {
                            ((PhotoAttachPhotoCell) view).setChecked(-1, add, false);
                        }
                        break;
                    }
                }
            }
            count = cameraPhotoRecyclerView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = cameraPhotoRecyclerView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    int tag = (Integer) view.getTag();
                    if (tag == index) {
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            ((PhotoAttachPhotoCell) view).setChecked(num, add, false);
                        } else {
                            ((PhotoAttachPhotoCell) view).setChecked(-1, add, false);
                        }
                        break;
                    }
                }
            }
            parentAlert.updateCountButton(add ? 1 : 2);
            return num;
        }

        @Override
        public int getSelectedCount() {
            return selectedPhotos.size();
        }

        @Override
        public ArrayList<Object> getSelectedPhotosOrder() {
            return selectedPhotosOrder;
        }

        @Override
        public HashMap<Object, Object> getSelectedPhotos() {
            return selectedPhotos;
        }

        @Override
        public int getPhotoIndex(int index) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry == null) {
                return -1;
            }
            return selectedPhotosOrder.indexOf(photoEntry.imageId);
        }
    }

    private void setCurrentSpoilerVisible(int i, boolean visible) {
        PhotoViewer photoViewer = PhotoViewer.getInstance();
        int index = i == -1 ? photoViewer.getCurrentIndex() : i;
        List<Object> photos = photoViewer.getImagesArrLocals();
        boolean hasSpoiler = photos != null && !photos.isEmpty() && index < photos.size() && photos.get(index) instanceof MediaController.PhotoEntry && ((MediaController.PhotoEntry) photos.get(index)).hasSpoiler;

        if (hasSpoiler) {
            MediaController.PhotoEntry entry = (MediaController.PhotoEntry) photos.get(index);

            gridView.forAllChild(view -> {
                if (view instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                    if (cell.getPhotoEntry() == entry) {
                        cell.setHasSpoiler(visible, 250f);
                        cell.setStarsPrice(getStarsPrice(), selectedPhotos.size() > 1);
                    }
                }
            });
        }
    }

    public PhotoViewer.PhotoViewerProvider photoViewerProvider = new BasePhotoProvider() {
        @Override
        public void onOpen() {
            pauseCameraPreview();
            setCurrentSpoilerVisible(-1, true);
        }

        @Override
        public void onPreClose() {
            setCurrentSpoilerVisible(-1, false);
        }

        @Override
        public void onClose() {
            resumeCameraPreview();
            AndroidUtilities.runOnUIThread(()-> setCurrentSpoilerVisible(-1, true), 150);
        }

        @Override
        public void onEditModeChanged(boolean isEditMode) {
            onPhotoEditModeChanged(isEditMode);
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                int[] coords = new int[2];
                cell.getImageView().getLocationInWindow(coords);
                if (Build.VERSION.SDK_INT < 26) {
                    coords[0] -= parentAlert.getLeftInset();
                }
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = gridView;
                object.imageReceiver = cell.getImageView().getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.getScale();
                object.clipBottomAddition = (int) parentAlert.getClipLayoutBottom();
                cell.showCheck(false);
                return object;
            }

            return null;
        }

        @Override
        public void updatePhotoAtIndex(int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                cell.getImageView().setOrientation(0, true);
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
                if (photoEntry == null) {
                    return;
                }
                if (photoEntry.thumbPath != null) {
                    cell.getImageView().setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable);
                } else if (photoEntry.path != null) {
                    cell.getImageView().setOrientation(photoEntry.orientation, photoEntry.invert, true);
                    if (photoEntry.isVideo) {
                        cell.getImageView().setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                    } else {
                        cell.getImageView().setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                    }
                } else {
                    cell.getImageView().setImageDrawable(Theme.chat_attachEmptyDrawable);
                }
            }
        }

        @Override
        public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                return cell.getImageView().getImageReceiver().getBitmapSafe();
            }
            return null;
        }

        @Override
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                cell.showCheck(true);
            }
        }

        @Override
        public void willHidePhotoViewer() {
            int count = gridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = gridView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                    cell.showCheck(true);
                }
            }
        }

        @Override
        public void onApplyCaption(CharSequence caption) {
            if (selectedPhotos.size() > 0 && selectedPhotosOrder.size() > 0) {
                Object o = selectedPhotos.get(selectedPhotosOrder.get(0));
                CharSequence firstPhotoCaption = null;
                ArrayList<TLRPC.MessageEntity> entities = null;
                if (o instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                    firstPhotoCaption = photoEntry1.caption;
                    entities = photoEntry1.entities;
                }
                if (o instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                    firstPhotoCaption = photoEntry1.caption;
                    entities = photoEntry1.entities;
                }
                if (firstPhotoCaption != null) {
                    if (entities != null) {
                        if (!(firstPhotoCaption instanceof Spannable)) {
                            firstPhotoCaption = new SpannableStringBuilder(firstPhotoCaption);
                        }
                        MessageObject.addEntitiesToText(firstPhotoCaption, entities, false, false, false, false);
                    }
                }
                parentAlert.getCommentView().setText(AnimatedEmojiSpan.cloneSpans(firstPhotoCaption, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW));
            }
        }

        @Override
        public boolean cancelButtonPressed() {
            return false;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
            parentAlert.sent = true;
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry != null) {
                photoEntry.editedInfo = videoEditedInfo;
            }
            if (selectedPhotos.isEmpty() && photoEntry != null) {
                addToSelectedPhotos(photoEntry, -1);
            }
            if (parentAlert.checkCaption(parentAlert.getCommentView().getText())) {
                return;
            }
            parentAlert.applyCaption();
            if (PhotoViewer.getInstance().hasCaptionForAllMedia) {
                HashMap<Object, Object> selectedPhotos = getSelectedPhotos();
                ArrayList<Object> selectedPhotosOrder = getSelectedPhotosOrder();
                if (!selectedPhotos.isEmpty()) {
                    for (int a = 0; a < selectedPhotosOrder.size(); a++) {
                        Object o = selectedPhotos.get(selectedPhotosOrder.get(a));
                        if (o instanceof MediaController.PhotoEntry) {
                            MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                            if (a == 0) {
                                CharSequence[] caption = new CharSequence[]{PhotoViewer.getInstance().captionForAllMedia};
                                photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(caption, false);
                                photoEntry1.caption = caption[0];
                                if (parentAlert.checkCaption(photoEntry1.caption)) {
                                    return;
                                }
                            } else {
                                photoEntry1.caption = null;
                            }
                        }
                    }
                }
            }
            parentAlert.delegate.didPressedButton(7, true, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), forceDocument);
            selectedPhotos.clear();
            cameraPhotos.clear();
            selectedPhotosOrder.clear();
            selectedPhotos.clear();
        }

        @Override
        public boolean allowCaption() {
            return !parentAlert.isPhotoPicker;
        }

        @Override
        public long getDialogId() {
            if (parentAlert.baseFragment instanceof ChatActivity)
                return ((ChatActivity) parentAlert.baseFragment).getDialogId();
            return super.getDialogId();
        }

        @Override
        public boolean canMoveCaptionAbove() {
            return parentAlert != null && parentAlert.baseFragment instanceof ChatActivity;
        }
        @Override
        public boolean isCaptionAbove() {
            return parentAlert != null && parentAlert.captionAbove;
        }
        @Override
        public void moveCaptionAbove(boolean above) {
            if (parentAlert == null || parentAlert.captionAbove == above) return;
            parentAlert.setCaptionAbove(above);
            captionItem.setState(!parentAlert.captionAbove, true);
        }
    };

    protected void updateCheckedPhotoIndices() {
        if (!(parentAlert.baseFragment instanceof ChatActivity)) {
            return;
        }
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition((Integer) cell.getTag());
                if (photoEntry != null) {
                    cell.setNum(selectedPhotosOrder.indexOf(photoEntry.imageId));
                }
            }
        }
        count = cameraPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = cameraPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition((Integer) cell.getTag());
                if (photoEntry != null) {
                    cell.setNum(selectedPhotosOrder.indexOf(photoEntry.imageId));
                }
            }
        }
    }

    protected void updateCheckedPhotos() {
        if (!(parentAlert.baseFragment instanceof ChatActivity)) {
            return;
        }
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int position = gridView.getChildAdapterPosition(view);
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                    position--;
                }
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                cell.setHasSpoiler(photoEntry != null && photoEntry.hasSpoiler);
                if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                    cell.setChecked(photoEntry != null ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                } else {
                    cell.setChecked(-1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                }
            }
        }
        count = cameraPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = cameraPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int position = cameraPhotoRecyclerView.getChildAdapterPosition(view);
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                    position--;
                }
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                cell.setHasSpoiler(photoEntry != null && photoEntry.hasSpoiler);
                if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                    cell.setChecked(photoEntry != null ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                } else {
                    cell.setChecked(-1, photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId), true);
                }
            }
        }
    }

    private MediaController.PhotoEntry getPhotoEntryAtPosition(int position) {
        if (position < 0) {
            return null;
        }
        int cameraCount = cameraPhotos.size();
        if (position < cameraCount) {
            return (MediaController.PhotoEntry) cameraPhotos.get(position);
        }
        position -= cameraCount;
        if (selectedAlbumEntry != null && position < selectedAlbumEntry.photos.size()) {
            return selectedAlbumEntry.photos.get(position);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected ArrayList<Object> getAllPhotosArray() {
        ArrayList<Object> arrayList;
        if (selectedAlbumEntry != null) {
            if (!cameraPhotos.isEmpty()) {
                arrayList = new ArrayList<>(selectedAlbumEntry.photos.size() + cameraPhotos.size());
                arrayList.addAll(cameraPhotos);
                arrayList.addAll(selectedAlbumEntry.photos);
            } else {
                arrayList = (ArrayList) selectedAlbumEntry.photos;
            }
        } else if (!cameraPhotos.isEmpty()) {
            arrayList = cameraPhotos;
        } else {
            arrayList = new ArrayList<>(0);
        }
        return arrayList;
    }

    public ChatAttachAlertPhotoLayout(ChatAttachAlert alert, Context context, boolean forceDarkTheme, boolean needCamera, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        this.forceDarkTheme = forceDarkTheme;
        this.needCamera = needCamera;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.cameraInitied);
        FrameLayout container = alert.getContainer();
        showAvatarConstructor = parentAlert.avatarPicker != 0;

        cameraDrawable = context.getResources().getDrawable(R.drawable.instant_camera).mutate();

        gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        blurManager = new BlurringShader.BlurManager(container);
        collageLayoutView = new CollageLayoutView2(context, blurManager, container, resourcesProvider) {

            Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return dp(160) + parentAlert.getBottomInset();
                }
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(collageLayoutView, bulletinDelegate);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(collageLayoutView);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return false;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (AndroidUtilities.makingGlobalBlurBitmap) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    super.dispatchDraw(canvas);
                } else {
                    int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - collageLayoutView.getTranslationY() - (parentAlert.mentionContainer != null ? parentAlert.mentionContainer.clipBottom() + dp(8) : 0), getMeasuredHeight());
                    if (cameraAnimationInProgress) {
                        AndroidUtilities.rectTmp.set(animationClipLeft + collageViewOffsetX * (1f - cameraOpenProgress), animationClipTop + collageViewOffsetY * (1f - cameraOpenProgress), animationClipRight, Math.min(maxY, animationClipBottom));
                    } else if (!cameraAnimationInProgress && !cameraOpened) {
                        AndroidUtilities.rectTmp.set(collageViewOffsetX, collageViewOffsetY, getMeasuredWidth(), Math.min(maxY, getMeasuredHeight()));
                    } else {
                        AndroidUtilities.rectTmp.set(0 , 0, getMeasuredWidth(), Math.min(maxY, getMeasuredHeight()));
                    }
                    canvas.save();
                    canvas.clipRect(AndroidUtilities.rectTmp);
                    super.dispatchDraw(canvas);
                    canvas.restore();
                }
            }

            @Override
            protected void onLayoutUpdate(CollageLayout layout) {
                collageListView.setVisible(false, true);
                if (layout != null && layout.parts.size() > 1) {
                    collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout = layout), true);
                    collageButton.setSelected(true, true);
                } else {
                    collageButton.setSelected(false, true);
                }
                updateActionBarButtons(true);
            }
        };
        collageLayoutView.setCancelGestures(this::cancelGestures);
        collageLayoutView.setResetState(() -> updateActionBarButtons(true));

        if (Build.VERSION.SDK_INT >= 21) {
            collageLayoutView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() - (parentAlert.mentionContainer != null ? parentAlert.mentionContainer.clipBottom() + dp(8) : 0) + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - collageLayoutView.getTranslationY(), view.getMeasuredHeight());
                    if (cameraOpened) {
                        maxY = view.getMeasuredHeight();
                    } else if (cameraAnimationInProgress) {
                        maxY = AndroidUtilities.lerp(maxY, view.getMeasuredHeight(), cameraOpenProgress);
                    }
                    if (cameraAnimationInProgress) {
                        AndroidUtilities.rectTmp.set(animationClipLeft + collageViewOffsetX * (1f - cameraOpenProgress), animationClipTop + collageViewOffsetY * (1f - cameraOpenProgress), animationClipRight,  animationClipBottom);
                        outline.setRect((int) AndroidUtilities.rectTmp.left,(int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, Math.min(maxY, (int) AndroidUtilities.rectTmp.bottom));
                    } else if (!cameraAnimationInProgress && !cameraOpened) {
                        int rad = dp(8 * parentAlert.cornerRadius);
                        outline.setRoundRect((int) collageViewOffsetX, (int) collageViewOffsetY, view.getMeasuredWidth() + rad, Math.min(maxY, view.getMeasuredHeight()) + rad, rad);
                    } else {
                        outline.setRect(0, 0, view.getMeasuredWidth(), Math.min(maxY, view.getMeasuredHeight()));
                    }
                }
            });
            collageLayoutView.setClipToOutline(true);
        }
        collageLayoutView.setContentDescription(LocaleController.getString(R.string.AccDescrInstantCamera));

        cameraIcon = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - collageLayoutView.getTranslationY(), getMeasuredHeight());
                if (cameraOpened) {
                    maxY = getMeasuredHeight();
                } else if (cameraAnimationInProgress) {
                    maxY = AndroidUtilities.lerp(maxY, getMeasuredHeight(), cameraOpenProgress);
                }
                int w = cameraDrawable.getIntrinsicWidth();
                int h = cameraDrawable.getIntrinsicHeight();
                int x = (itemSize - w) / 2;
                int y = (itemSize - h) / 2;
                if (collageViewOffsetY != 0) {
                    y -= collageViewOffsetY;
                }
                boolean clip = maxY < getMeasuredHeight();
                if (clip) {
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), maxY);
                }
                cameraDrawable.setBounds(x, y, x + w, y + h);
                cameraDrawable.draw(canvas);
                if (clip) {
                    canvas.restore();
                }
            }
        };
        cameraIcon.setWillNotDraw(false);
        cameraIcon.setClipChildren(true);

        flashViews = new FlashViews(context, null, parentAlert.getWindowView(), null);
        flashViews.add(new FlashViews.Invertable() {
            @Override
            public void setInvert(float invert) {
                AndroidUtilities.setLightNavigationBar(parentAlert.getWindowView(), invert > 0.5f);
                AndroidUtilities.setLightStatusBar(parentAlert.getWindowView(), invert > 0.5f);
            }
            @Override
            public void invalidate() {}
        });
        container.addView(flashViews.backgroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(flashViews.foregroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        container.addView(actionBarContainer = new FrameLayout(context) {

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int childCount = getChildCount();
                boolean isPortrait = getMeasuredHeight() == dp(150);

                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);

                    if (child == collageButton) {
                        if (isPortrait) {
                            child.layout(getMeasuredWidth() - dp(56), 0, getMeasuredWidth(), dp(56));
                        } else {
                            child.layout(0, 0, dp(56), dp(56));
                        }
                    } else if (child == collageRemoveButton) {
                        if (isPortrait) {
                            child.layout(getMeasuredWidth() - dp(56), 0, getMeasuredWidth(), dp(56));
                        } else {
                            child.layout(0, 0, dp(56), dp(56));
                        }
                    } else if (child == collageListView) {
                        if (isPortrait) {
                            child.layout(0, 0, getMeasuredWidth(), dp(56));
                            ((LinearLayoutManager) collageListView.listView.getLayoutManager()).setOrientation(LinearLayoutManager.HORIZONTAL);
                        } else {
                            child.layout(0, 0, dp(56), getMeasuredHeight());
                            ((LinearLayoutManager) collageListView.listView.getLayoutManager()).setOrientation(LinearLayoutManager.VERTICAL);
                        }
                    } else if (child == dualHint) {
                        if (isPortrait) {
                            child.layout(0, dp(52), getMeasuredWidth(), getMeasuredHeight());
                        } else {
                            child.layout(dp(52), 0, getMeasuredWidth(), getMeasuredHeight());
                        }
                    } else if (child == savedDualHint) {
                        if (isPortrait) {
                            child.layout(0, 0, getMeasuredWidth(), getMeasuredHeight() - dp(52));
                        } else {
                            child.layout(0, 0, getMeasuredWidth(), getMeasuredHeight() - dp(52));
                        }
                    } else if (child == removeCollageHint) {
                        if (isPortrait) {
                            child.layout(0, dp(52), getMeasuredWidth(), getMeasuredHeight());
                        } else {
                            child.layout(dp(52), 0, getMeasuredWidth(), getMeasuredHeight());
                        }
                    } else if (child == closeButton) {
                        if (isPortrait) {
                            child.layout(0, 0, dp(56), dp(56));
                        } else {
                            child.layout(0, getMeasuredHeight() - dp(56), dp(56), getMeasuredHeight());
                        }
                    } else if (child == flashButton) {
                        if (isPortrait) {
                            child.layout(getMeasuredWidth() - dp(56), 0, getMeasuredWidth(), dp(56));
                        } else {
                            child.layout(0, 0, dp(56), dp(56));
                        }
                    } else if (child == dualButton) {
                        if (isPortrait) {
                            child.layout(getMeasuredWidth() - dp(56), 0, getMeasuredWidth(), dp(56));
                        } else {
                            child.layout(0, 0, dp(56), dp(56));
                        }
                    }
                }

                updateActionBarButtonsOffsets();
            }
        }); // 150dp
        actionBarContainer.setVisibility(View.GONE);
        actionBarContainer.setAlpha(0.0f);

        collageButton = new CollageLayoutButton(context);
        collageButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        if (lastCollageLayout == null) {
            lastCollageLayout = CollageLayout.getLayouts().get(6);
        }
        collageButton.setOnClickListener(v -> {
            if (cameraView != null && cameraView.isDual()) {
                cameraView.toggleDual();
            }
            if (!collageListView.isVisible() && !collageLayoutView.hasLayout()) {
                collageLayoutView.setLayout(lastCollageLayout, true);
                collageListView.setSelected(lastCollageLayout);
                collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), true);
                collageButton.setSelected(true);
                if (cameraView != null) {
                    cameraView.recordHevc = !collageLayoutView.hasLayout();
                }
            }
            collageListView.setVisible(!collageListView.isVisible(), true);
            updateActionBarButtons(true);
        });
        collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), false);
        collageButton.setSelected(false);
        collageButton.setVisibility(View.VISIBLE);
        collageButton.setAlpha(1.0f);
        //actionBarContainer.addView(collageButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarContainer.addView(collageButton);
        flashViews.add(collageButton);

        collageRemoveButton = new ToggleButton2(context);
        collageRemoveButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        collageRemoveButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(new CollageLayout("../../.."), true), false);
        collageRemoveButton.setVisibility(View.GONE);
        collageRemoveButton.setAlpha(0.0f);
        collageRemoveButton.setOnClickListener(v -> {
            collageLayoutView.setLayout(null, true);
            collageLayoutView.clear(true);
            collageListView.setSelected(null);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageListView.setVisible(false, true);
            updateActionBarButtons(true);
        });
        //actionBarContainer.addView(collageRemoveButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarContainer.addView(collageRemoveButton);
        flashViews.add(collageRemoveButton);

        collageListView = new CollageLayoutButton.CollageLayoutListView(context, null);
        collageListView.listView.scrollToPosition(6);
        collageListView.setSelected(null);
        collageListView.setOnLayoutClick(layout -> {
            collageLayoutView.setLayout(lastCollageLayout = layout, true);
            collageListView.setSelected(layout);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageButton.setDrawable(new CollageLayoutButton.CollageLayoutDrawable(layout));
            setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), true);
            recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);
        });
        //actionBarContainer.addView(collageListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarContainer.addView(collageListView);

        dualHint = new HintView2(context, HintView2.DIRECTION_TOP)
                .setJoint(1, -20)
                .setDuration(5000)
                .setCloseButton(true)
                .setText(getString(R.string.StoryCameraDualHint))
                .setOnHiddenListener(() -> MessagesController.getGlobalMainSettings().edit().putInt("chatdualhint", MessagesController.getGlobalMainSettings().getInt("chatdualhint", 0) + 1).apply());
        dualHint.setPadding(dp(8), 0, dp(8), 0);
        //actionBarContainer.addView(dualHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));
        actionBarContainer.addView(dualHint);

        savedDualHint = new HintView2(context, HintView2.DIRECTION_RIGHT)
                .setJoint(0, 56 / 2)
                .setDuration(5000)
                .setMultilineText(true);
        //actionBarContainer.addView(savedDualHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 52, 0));
        actionBarContainer.addView(savedDualHint);

        removeCollageHint = new HintView2(context, HintView2.DIRECTION_TOP)
                .setJoint(1, -20)
                .setDuration(5000)
                .setText(LocaleController.getString(R.string.StoryCollageRemoveGrid));
        removeCollageHint.setPadding(dp(8), 0, dp(8), 0);
        //actionBarContainer.addView(removeCollageHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));
        actionBarContainer.addView(removeCollageHint);

        videoTimerView = new VideoTimerView(context);
        showVideoTimer(false, false);
        container.addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP | Gravity.FILL_HORIZONTAL, 56, 0, 56, 0));
        flashViews.add(videoTimerView);

        if (Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }

        closeButton = new FlashViews.ImageViewInvertable(context);
        closeButton.setContentDescription(getString(R.string.AccDescrGoBack));
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        closeButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        closeButton.setOnClickListener(e -> {
            closeCamera(true);
        });
        //actionBarContainer.addView(closeButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));
        actionBarContainer.addView(closeButton);
        flashViews.add(closeButton);

        flashButton = new ToggleButton2(context);
        flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        flashButton.setOnClickListener(e -> {
            if (cameraView == null) {
                return;
            }
            String current = getCurrentFlashMode();
            String next = getNextFlashMode();
            if (current == null || current.equals(next)) {
                return;
            }
            setCurrentFlashMode(next);
            setCameraFlashModeIcon(next, true);
        });
        flashButton.setOnLongClickListener(e -> {
            if (cameraView == null || !cameraView.isFrontface()) {
                return false;
            }

            checkFrontfaceFlashModes();
            flashButton.setSelected(true);
            flashViews.previewStart();
            ItemOptions.makeOptions(container, resourcesProvider, flashButton)
                    .addView(
                            new SliderView(getContext(), SliderView.TYPE_WARMTH)
                                    .setValue(flashViews.warmth)
                                    .setOnValueChange(v -> {
                                        flashViews.setWarmth(v);
                                    })
                    )
                    .addSpaceGap()
                    .addView(
                            new SliderView(getContext(), SliderView.TYPE_INTENSITY)
                                    .setMinMax(.65f, 1f)
                                    .setValue(flashViews.intensity)
                                    .setOnValueChange(v -> {
                                        flashViews.setIntensity(v);
                                    })
                    )
                    .setOnDismiss(() -> {
                        saveFrontFaceFlashMode();
                        flashViews.previewEnd();
                        flashButton.setSelected(false);
                    })
                    .setDimAlpha(0)
                    .setGravity(Gravity.RIGHT)
                    .translate(dp(46), -dp(4))
                    .setBackgroundColor(0xbb1b1b1b)
                    .show();
            return true;
        });
        //actionBarContainer.addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarContainer.addView(flashButton);
        flashViews.add(flashButton);

        dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
        dualButton.setOnClickListener(v -> {
            if (cameraView == null) {
                return;
            }
            cameraView.toggleDual();
            dualButton.setValue(cameraView.isDual());

            dualHint.hide();
            MessagesController.getGlobalMainSettings().edit().putInt("chatdualhint", 2).apply();
            if (savedDualHint.shown()) {
                MessagesController.getGlobalMainSettings().edit().putInt("chatsvddualhint", 2).apply();
            }
            savedDualHint.hide();
        });
        final boolean dualCameraAvailable = DualCameraView.dualAvailableStatic(context);
        dualButton.setVisibility(dualCameraAvailable ? View.VISIBLE : View.GONE);
        dualButton.setAlpha(dualCameraAvailable ? 1.0f : 0.0f);
        //actionBarContainer.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarContainer.addView(dualButton);
        flashViews.add(dualButton);

        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0, resourcesProvider) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(dropDown.getText());
            }
        };
        dropDownContainer.setSubMenuOpenSide(1);
        parentAlert.actionBar.addView(dropDownContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
        dropDownContainer.setOnClickListener(view -> dropDownContainer.toggleSubMenu());

        dropDown = new TextView(context);
        dropDown.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dropDown.setGravity(Gravity.LEFT);
        dropDown.setSingleLine(true);
        dropDown.setLines(1);
        dropDown.setMaxLines(1);
        dropDown.setEllipsize(TextUtils.TruncateAt.END);
        dropDown.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        dropDown.setText(LocaleController.getString(R.string.ChatGallery));
        dropDown.setTypeface(AndroidUtilities.bold());
        dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
        dropDownDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        dropDown.setCompoundDrawablePadding(dp(4));
        dropDown.setPadding(0, 0, dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        checkCamera(false);

        captionItem = new MessagePreviewView.ToggleButton(
            context,
            R.raw.position_below, getString(R.string.CaptionAbove),
            R.raw.position_above, getString(R.string.CaptionBelow),
            resourcesProvider
        );
        captionItem.setState(!parentAlert.captionAbove, false);

        previewItem = parentAlert.selectedMenuItem.addSubItem(preview, R.drawable.msg_view_file, LocaleController.getString(R.string.AttachMediaPreviewButton));

        parentAlert.selectedMenuItem.addColoredGap(preview_gap);
        parentAlert.selectedMenuItem.addSubItem(open_in, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp));
        compressItem = parentAlert.selectedMenuItem.addSubItem(compress, R.drawable.msg_filehq, LocaleController.getString(R.string.SendWithoutCompression));
        parentAlert.selectedMenuItem.addSubItem(group, R.drawable.msg_ungroup, LocaleController.getString(R.string.SendWithoutGrouping));
        parentAlert.selectedMenuItem.addColoredGap(media_gap);
        spoilerItem = parentAlert.selectedMenuItem.addSubItem(spoiler, R.drawable.msg_spoiler, LocaleController.getString(R.string.EnablePhotoSpoiler));
        parentAlert.selectedMenuItem.addSubItem(caption, captionItem);
        starsItem = parentAlert.selectedMenuItem.addSubItem(stars, R.drawable.menu_feature_paid, getString(R.string.PaidMediaButton));
        parentAlert.selectedMenuItem.setFitSubItems(true);

        gridView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - dp(80)) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - dp(80)) {
                    return false;
                }
                return super.onInterceptTouchEvent(e);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                PhotoViewer.getInstance().checkCurrentImageVisibility();
            }
        };
        gridView.setFastScrollEnabled(RecyclerListView.FastScroll.DATE_TYPE);
        gridView.setFastScrollVisible(true);
        gridView.getFastScroll().setAlpha(0f);
        gridView.getFastScroll().usePadding = false;
        gridView.setAdapter(adapter = new PhotoAttachAdapter(context, needCamera));
        adapter.createCache();
        gridView.setClipToPadding(false);
        gridView.setItemAnimator(null);
        gridView.setLayoutAnimation(null);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            boolean parentPinnedToTop;
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (gridView.getChildCount() <= 0) {
                    return;
                }
                parentAlert.updateLayout(ChatAttachAlertPhotoLayout.this, true, dy);
                if (adapter.getTotalItemsCount() > SHOW_FAST_SCROLL_MIN_COUNT) {
                    if (parentPinnedToTop != parentAlert.pinnedToTop) {
                        parentPinnedToTop = parentAlert.pinnedToTop;
                        gridView.getFastScroll().animate().alpha(parentPinnedToTop ? 1f : 0f).setDuration(100).start();
                    }
                } else {
                    gridView.getFastScroll().setAlpha(0);
                }
                if (dy != 0) {
                    checkCameraViewPosition();
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = dp(13) + (parentAlert.selectedMenuItem != null ? dp(parentAlert.selectedMenuItem.getAlpha() * 26) : 0);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() + parentAlert.topCommentContainer.getMeasuredHeight() * parentAlert.topCommentContainer.getAlpha()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > dp(7)) {
                            gridView.smoothScrollBy(0, holder.itemView.getTop() - dp(7));
                        }
                    }
                }
            }
        });
        layoutManager = new GridLayoutManager(context, itemSize) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (gridView.getPaddingTop() - dp(7));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        };
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == adapter.itemsCount - 1) {
                    return layoutManager.getSpanCount();
                }
                return itemSize + (position % itemsPerRow != itemsPerRow - 1 ? dp(5) : 0);
            }
        });
        gridView.setLayoutManager(layoutManager);
        gridView.setOnItemClickListener((view, position, x, y) -> {
            if (!mediaEnabled || parentAlert.destroyed) {
                return;
            }
            BaseFragment fragment = parentAlert.baseFragment;
            if (fragment == null) {
                fragment = LaunchActivity.getLastFragment();
            }
            if (fragment == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry && position == 0 && noCameraPermissions) {
                    try {
                        fragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 18);
                    } catch (Exception ignore) {

                    }
                    return;
                } else if (noGalleryPermissions) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        try {
                            fragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                        } catch (Exception ignore) {}
                    } else {
                        try {
                            fragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                        } catch (Exception ignore) {}
                    }
                    return;
                }
            }
            if (position != 0 || !needCamera || selectedAlbumEntry != galleryAlbumEntry) {
                if (selectedAlbumEntry == galleryAlbumEntry && needCamera) {
                    position--;
                }
                if (showAvatarConstructor) {
                    if (position == 0) {
                        if (!(view instanceof AvatarConstructorPreviewCell)) {
                            return;
                        }
                        showAvatarConstructorFragment((AvatarConstructorPreviewCell) view, null);
                        parentAlert.dismiss();
                    }
                    position--;
                }
                ArrayList<Object> arrayList = getAllPhotosArray();
                if (position < 0 || position >= arrayList.size()) {
                    return;
                }
                if (parentAlert.delegate != null && parentAlert.delegate.selectItemOnClicking() && arrayList.get(position) instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) arrayList.get(position);
                    selectedPhotos.clear();
                    if (photoEntry != null) {
                        addToSelectedPhotos(photoEntry, -1);
                    }
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(7, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    selectedPhotos.clear();
                    cameraPhotos.clear();
                    selectedPhotosOrder.clear();
                    selectedPhotos.clear();
                    return;
                }
                PhotoViewer.getInstance().setParentActivity(fragment, resourcesProvider);
                PhotoViewer.getInstance().setParentAlert(parentAlert);
                PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
                ChatActivity chatActivity;
                int type;
                if (parentAlert.isPhotoPicker && parentAlert.isStickerMode) {
                    type = PhotoViewer.SELECT_TYPE_STICKER;
                    if (parentAlert.baseFragment instanceof ChatActivity) {
                        chatActivity = (ChatActivity) parentAlert.baseFragment;
                    } else {
                        chatActivity = null;
                    }
                } else if (parentAlert.avatarPicker != 0) {
                    chatActivity = null;
                    type = PhotoViewer.SELECT_TYPE_AVATAR;
                } else if (parentAlert.baseFragment instanceof ChatActivity) {
                    chatActivity = (ChatActivity) parentAlert.baseFragment;
                    type = 0;
                } else if (parentAlert.allowEnterCaption) {
                    chatActivity = null;
                    type = 0;
                } else {
                    chatActivity = null;
                    type = 4;
                }
                if (!parentAlert.delegate.needEnterComment()) {
                    AndroidUtilities.hideKeyboard(fragment.getFragmentView().findFocus());
                    AndroidUtilities.hideKeyboard(parentAlert.getContainer().findFocus());
                }
                if (selectedPhotos.size() > 0 && selectedPhotosOrder.size() > 0) {
                    Object o = selectedPhotos.get(selectedPhotosOrder.get(0));
                    if (o instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                        photoEntry1.caption = parentAlert.getCommentView().getText();
                    }
                    if (o instanceof MediaController.SearchImage) {
                        MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                        photoEntry1.caption = parentAlert.getCommentView().getText();
                    }
                }
                if (parentAlert.getAvatarFor() != null) {
                    boolean isVideo = false;
                    if (arrayList.get(position) instanceof MediaController.PhotoEntry) {
                        isVideo = ((MediaController.PhotoEntry) arrayList.get(position)).isVideo;
                    }
                    parentAlert.getAvatarFor().isVideo = isVideo;
                }

                boolean hasSpoiler = arrayList.get(position) instanceof MediaController.PhotoEntry && ((MediaController.PhotoEntry) arrayList.get(position)).hasSpoiler;
                Object object = arrayList.get(position);
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                    if (checkSendMediaEnabled(photoEntry)) {
                        return;
                    }
                }
                if (hasSpoiler) {
                    setCurrentSpoilerVisible(position, false);
                }
                int finalPosition = position;
                BaseFragment finalFragment = fragment;
                AndroidUtilities.runOnUIThread(() -> {
                    int avatarType = type;
                    if (parentAlert.isPhotoPicker && !parentAlert.isStickerMode) {
                        PhotoViewer.getInstance().setParentActivity(finalFragment);
                        PhotoViewer.getInstance().setMaxSelectedPhotos(0, false);
                        avatarType = PhotoViewer.SELECT_TYPE_WALLPAPER;
                    }
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, finalPosition, avatarType, false, photoViewerProvider, chatActivity);
                    PhotoViewer.getInstance().setAvatarFor(parentAlert.getAvatarFor());
                    if (parentAlert.isPhotoPicker && !parentAlert.isStickerMode) {
                        PhotoViewer.getInstance().closePhotoAfterSelect = false;
                    }
                    if (parentAlert.isStickerMode) {
                        PhotoViewer.getInstance().enableStickerMode(null, false, parentAlert.customStickerHandler);
                    }
                    if (captionForAllMedia()) {
                        PhotoViewer.getInstance().setCaption(parentAlert.getCommentView().getText());
                    }
                }, hasSpoiler ? 250 : 0);
            } else {
                if (SharedConfig.inappCamera) {
                    openCamera(true);
                } else {
                    if (parentAlert.delegate != null) {
                        parentAlert.delegate.didPressedButton(0, false, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    }
                }
            }
        });
        gridView.setOnItemLongClickListener((view, position) -> {
            if (parentAlert.storyMediaPicker) {
                return false;
            }
            if (position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (parentAlert.delegate != null) {
                    parentAlert.delegate.didPressedButton(0, false, true, 0, 0, parentAlert.isCaptionAbove(), false);
                }
                return true;
            } else if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                itemRangeSelector.setIsActive(view, true, position, shouldSelect = !cell.isChecked());
            }
            return false;
        });
        itemRangeSelector = new RecyclerViewItemRangeSelector(new RecyclerViewItemRangeSelector.RecyclerViewItemRangeSelectorDelegate() {
            @Override
            public int getItemCount() {
                return adapter.getItemCount();
            }

            @Override
            public void setSelected(View view, int index, boolean selected) {
                if (selected != shouldSelect || !(view instanceof PhotoAttachPhotoCell)) {
                    return;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                cell.callDelegate();
            }

            @Override
            public boolean isSelected(int index) {
                MediaController.PhotoEntry entry = adapter.getPhoto(index);
                return entry != null && selectedPhotos.containsKey(entry.imageId);
            }

            @Override
            public boolean isIndexSelectable(int index) {
                return adapter.getItemViewType(index) == 0;
            }

            @Override
            public void onStartStopSelection(boolean start) {
                alertOnlyOnce = start ? 1 : 0;
                gridView.hideSelector(true);
            }
        });
        gridView.addOnItemTouchListener(itemRangeSelector);

        progressView = new EmptyTextProgressView(context, null, resourcesProvider);
        progressView.setText(LocaleController.getString(R.string.NoPhotos));
        progressView.setOnTouchListener(null);
        progressView.setTextSize(16);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }

        zoomControlView = new ZoomControlView(context);
        zoomControlView.enabledTouch = false;
        zoomControlView.setAlpha(0.0f);
        container.addView(zoomControlView); // 50dp
        zoomControlView.setDelegate(zoom -> {
            if (cameraView != null) {
                cameraView.setZoom(cameraZoom = zoom);
            }
            showZoomControls(true, true);
        });
        zoomControlView.setZoom(cameraZoom = 0, false);

        container.addView(controlContainer = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int childCount = getChildCount();
                boolean isPortrait = getMeasuredHeight() == dp(160);

                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);

                    if (child == recordControl) {
                        if (isPortrait) {
                            child.layout(0, getMeasuredHeight() - dp(100), getMeasuredWidth(), getMeasuredHeight());
                        } else {
                            child.layout(getMeasuredWidth() - dp(100), 0, getMeasuredWidth(), getMeasuredHeight());
                        }
                    } else if (child == cameraHint) {
                        if (isPortrait) {
                            child.layout(0, getMeasuredHeight() - dp(100) - child.getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight() - dp(100));
                            child.setRotation(0);
                        } else {
                            child.layout(getMeasuredWidth() - dp(100) - child.getMeasuredHeight(), 0, getMeasuredWidth() - dp(100), getMeasuredHeight());
                            child.setRotation(-90);
                        }
                    }
                }
            }
        }); // 160dp

        controlContainer.setVisibility(View.GONE);
        controlContainer.setAlpha(0.0f);

        recordControl = new RecordControl(context, false);
        recordControl.setDelegate(recordControlDelegate);
        recordControl.startAsVideo(isVideo);
        recordControl.setUnlimitedDuration();
        controlContainer.addView(recordControl);
        flashViews.add(recordControl);
        recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);

        cameraHint = new HintView2(context, HintView2.DIRECTION_BOTTOM)
                .setMultilineText(true)
                .setText(getString(R.string.TapForVideo))
                .setMaxWidth(320)
                .setDuration(5000L)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER);
        controlContainer.addView(cameraHint);

        container.addView(navbarContainer = new FrameLayout(context)); // 48dp
        navbarContainer.setAlpha(0.0f);
        navbarContainer.setVisibility(View.GONE);

        modeSwitcherView = new PhotoVideoSwitcherView(context) {
            @Override
            protected boolean allowTouch() {
                return !inCheck();
            }
        };
        modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {
            if (takingPhoto || takingVideo) {
                return;
            }

            isVideo = newIsVideo;
            showVideoTimer(isVideo && !collageListView.isVisible(), true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        });
        modeSwitcherView.setOnSwitchingModeListener(t -> recordControl.startAsVideoT(t));
        navbarContainer.addView(modeSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        flashViews.add(modeSwitcherView);

        hintTextView = new HintTextView(context);
        navbarContainer.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));
        flashViews.add(hintTextView);

        collageHintTextView = new HintTextView(context);
        collageHintTextView.setText(LocaleController.getString(R.string.StoryCollageReorderHint), false);
        collageHintTextView.setAlpha(0.0f);
        navbarContainer.addView(collageHintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));
        flashViews.add(collageHintTextView);

        counterTextView = new TextView(context);
        counterTextView.setBackgroundResource(R.drawable.photos_rounded);
        counterTextView.setVisibility(View.GONE);
        counterTextView.setTextColor(0xffffffff);
        counterTextView.setGravity(Gravity.CENTER);
        counterTextView.setPivotX(0);
        counterTextView.setPivotY(0);
        counterTextView.setTypeface(AndroidUtilities.bold());
        counterTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.photos_arrow, 0);
        counterTextView.setCompoundDrawablePadding(dp(4));
        counterTextView.setPadding(dp(16), 0, dp(16), 0);
        container.addView(counterTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 100 + 16));
        counterTextView.setOnClickListener(v -> {
            if (cameraView == null) {
                return;
            }
            openPhotoViewer(null, false, false);
            CameraController.getInstance().stopPreview(cameraView.getCameraSessionObject());
        });

        cameraPhotoRecyclerView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public void requestLayout() {
                if (cameraPhotoRecyclerViewIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        cameraPhotoRecyclerView.setVerticalScrollBarEnabled(true);
        cameraPhotoRecyclerView.setAdapter(cameraAttachAdapter = new PhotoAttachAdapter(context, false));
        cameraAttachAdapter.createCache();
        cameraPhotoRecyclerView.setClipToPadding(false);
        cameraPhotoRecyclerView.setPadding(dp(8), 0, dp(8), 0);
        cameraPhotoRecyclerView.setItemAnimator(null);
        cameraPhotoRecyclerView.setLayoutAnimation(null);
        cameraPhotoRecyclerView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        cameraPhotoRecyclerView.setVisibility(View.INVISIBLE);
        cameraPhotoRecyclerView.setAlpha(0.0f);
        container.addView(cameraPhotoRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        cameraPhotoLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        cameraPhotoRecyclerView.setLayoutManager(cameraPhotoLayoutManager);
        cameraPhotoRecyclerView.setOnItemClickListener((view, position) -> {
            if (view instanceof PhotoAttachPhotoCell) {
                ((PhotoAttachPhotoCell) view).callDelegate();
            }
        });

        updateActionBarButtonsOffsets();
    }

    private void updateActionBarButtons(boolean animated) {
        showVideoTimer(isVideo && !collageListView.isVisible() && !inCheck(), animated);
        collageButton.setSelected(collageLayoutView.hasLayout());
        setActionBarButtonVisible(closeButton, collageListView == null || !collageListView.isVisible(), animated);
        setActionBarButtonVisible(flashButton, !animatedRecording && flashButtonMode != null && !collageListView.isVisible() && !inCheck(), animated);
        setActionBarButtonVisible(dualButton, !animatedRecording && cameraView != null && cameraView.dualAvailable() && !collageListView.isVisible() && !collageLayoutView.hasLayout(), animated);
        setActionBarButtonVisible(collageButton, !collageListView.isVisible(), animated);
        setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), animated);
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        recordControl.setCollageProgress(collageProgress, animated);
        removeCollageHint.show(collageListView.isVisible());
        animateRecording(animatedRecording, animated);
    }

    public void setActionBarButtonVisible(View view, boolean visible, boolean animated) {
        if (view == null) return;
        if (animated) {
            view.setVisibility(View.VISIBLE);
            view.animate()
                    .alpha(visible ? 1.0f : 0.0f)
                    .setUpdateListener(animation -> updateActionBarButtonsOffsets())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            updateActionBarButtonsOffsets();
                            if (!visible) {
                                view.setVisibility(View.GONE);
                            }
                        }
                    })
                    .setDuration(320)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            view.animate().cancel();
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            view.setAlpha(visible ? 1.0f : 0.0f);
            updateActionBarButtonsOffsets();
        }
    }

    private void updateActionBarButtonsOffsets() {
        final boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
        if (isPortrait) {
            float right = 0;
            flashButton.setTranslationX(-right);
            right += dp(46) * flashButton.getAlpha();
            collageRemoveButton.setTranslationX(-right);
            right += dp(46) * collageRemoveButton.getAlpha();
            dualButton.setTranslationX(-right);
            right += dp(46) * dualButton.getAlpha();
            collageButton.setTranslationX(-right);
            right += dp(46) * collageButton.getAlpha();

            float left = 0;
            closeButton.setTranslationX(left);
            left += dp(46) * closeButton.getAlpha();

            collageListView.setBounds(left + dp(8), right + dp(8));
        } else {
            float top = 0;
            flashButton.setTranslationY(top);
            top += dp(46) * flashButton.getAlpha();
            collageRemoveButton.setTranslationY(top);
            top += dp(46) * collageRemoveButton.getAlpha();
            dualButton.setTranslationY(top);
            top += dp(46) * dualButton.getAlpha();
            collageButton.setTranslationY(top);
            top += dp(46) * collageButton.getAlpha();

            float bottom = 0;
            closeButton.setTranslationY(bottom);
            bottom += dp(46) * closeButton.getAlpha();

            collageListView.setBounds(dp(8), dp(8),0, bottom + dp(8));
        }
    }

    private boolean isDark;
    private void checkIsDark() {
        if (cameraView == null || cameraView.getTextureView() == null) {
            isDark = false;
            return;
        }
        final Bitmap bitmap = cameraView.getTextureView().getBitmap();
        if (bitmap == null) {
            isDark = false;
            return;
        }
        float l = 0;
        final int sx = bitmap.getWidth() / 12;
        final int sy = bitmap.getHeight() / 12;
        for (int x = 0; x < 10; ++x) {
            for (int y = 0; y < 10; ++y) {
                l += AndroidUtilities.computePerceivedBrightness(bitmap.getPixel((1 + x) * sx, (1 + y) * sy));
            }
        }
        l /= 100;
        bitmap.recycle();
        isDark = l < .22f;
    }

    private boolean useDisplayFlashlight() {
        return (takingPhoto || takingVideo) && (cameraView != null && cameraView.isFrontface()) && (frontfaceFlashMode == 2 || frontfaceFlashMode == 1 && isDark);
    }

    private boolean videoTimerShown = true;
    private void showVideoTimer(boolean show, boolean animated) {
        if (videoTimerShown == show) {
            return;
        }

        videoTimerShown = show;
        if (animated) {
            videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                if (!show) {
                    videoTimerView.setRecording(false, false);
                }
            }).start();
        } else {
            videoTimerView.clearAnimation();
            videoTimerView.setAlpha(show ? 1 : 0);
            if (!show) {
                videoTimerView.setRecording(false, false);
            }
        }
    }

    private boolean inCheck() {
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        return !animatedRecording && collageProgress >= 1.0f;
    }

    private AnimatorSet recordingAnimator;
    private boolean animatedRecording;
    private boolean animatedRecordingWasInCheck;
    private void animateRecording(boolean recording, boolean animated) {
        if (recording) {
            if (dualHint != null) {
                dualHint.hide();
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
        }
        if (animatedRecording == recording && animatedRecordingWasInCheck == inCheck()) {
            return;
        }
        if (recordingAnimator != null) {
            recordingAnimator.cancel();
            recordingAnimator = null;
        }
        animatedRecording = recording;
        animatedRecordingWasInCheck = inCheck();
        if (recording && collageListView != null && collageListView.isVisible()) {
            collageListView.setVisible(false, animated);
        }
        updateActionBarButtons(animated);
        if (animated) {
            recordingAnimator = new AnimatorSet();
            recordingAnimator.playTogether(
                    ObjectAnimator.ofFloat(hintTextView, View.ALPHA, recording && !inCheck() ? 1 : 0),
                    ObjectAnimator.ofFloat(hintTextView, View.TRANSLATION_Y, recording && !inCheck() ? 0 : dp(16)),
                    ObjectAnimator.ofFloat(collageHintTextView, View.ALPHA, !recording && inCheck() ? 0.6f : 0),
                    ObjectAnimator.ofFloat(collageHintTextView, View.TRANSLATION_Y, !recording && inCheck() ? 0 : dp(16)),
                    ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, recording || inCheck() ? 0 : 1),
                    ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, recording || inCheck() ? dp(16) : 0)
            );
            recordingAnimator.setDuration(260);
            recordingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            recordingAnimator.start();
        } else {
            hintTextView.setAlpha(recording && !inCheck() ? 1f : 0);
            hintTextView.setTranslationY(recording && !inCheck() ? 0 : dp(16));
            collageHintTextView.setAlpha(!recording && inCheck() ? 0.6f : 0);
            collageHintTextView.setTranslationY(!recording && inCheck() ? 0 : dp(16));
            modeSwitcherView.setAlpha(recording || inCheck() ? 0 : 1f);
            modeSwitcherView.setTranslationY(recording || inCheck() ? dp(16) : 0);
        }
    }

    private String flashButtonMode;
    private void setCameraFlashModeIcon(String mode, boolean animated) {
        flashButton.clearAnimation();
        if (cameraView != null && cameraView.isDual() || animatedRecording) {
            mode = null;
        }
        flashButtonMode = mode;
        if (mode == null) {
            setActionBarButtonVisible(flashButton, false, animated);
            return;
        }
        final int resId;
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_ON:
                resId = R.drawable.media_photo_flash_on2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                resId = R.drawable.media_photo_flash_auto2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                break;
            default:
            case Camera.Parameters.FLASH_MODE_OFF:
                resId = R.drawable.media_photo_flash_off2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                break;
        }
        flashButton.setIcon(flashButtonResId = resId, animated && flashButtonResId != resId);
        setActionBarButtonVisible(flashButton, !collageListView.isVisible() && flashButtonMode != null && !inCheck(), animated);
    }
    private final RecordControl.Delegate recordControlDelegate = new RecordControl.Delegate() {
        @Override
        public void onPhotoShoot() {
            if (takingPhoto || cameraView == null || !cameraView.isInited()) {
                return;
            }
            if (!photoEnabled) {
                BulletinFactory.of(collageLayoutView, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.GlobalAttachPhotoRestricted)).show();
                return;
            }

            cameraHint.hide();
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {}
                outputFile = null;
            }
            outputFile = AndroidUtilities.generatePicturePath(
                    parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat(),
                    null
            );

            takingPhoto = true;
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flash(ChatAttachAlertPhotoLayout.this::takePicture);
            } else {
                takePicture(null);
            }
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            if (takingVideo || stoppingTakingVideo || cameraView == null || cameraView.getCameraSession() == null) {
                return;
            }
            if (parentAlert.avatarPicker != 2 && !(parentAlert.baseFragment instanceof ChatActivity) || parentAlert.destroyed) {
                return;
            }
            if (parentAlert.isStickerMode) {
                return;
            }
            BaseFragment baseFragment = parentAlert.baseFragment;
            if (baseFragment == null) {
                baseFragment = LaunchActivity.getLastFragment();
            }
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }

            if (!videoEnabled) {
                BulletinFactory.of(collageLayoutView, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.GlobalAttachVideoRestricted)).show();
                return;
            }

            if (dualHint != null) {
                dualHint.hide();
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            cameraHint.hide();
            takingVideo = true;
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {}
                outputFile = null;
            }
            outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
            checkFrontfaceFlashModes();
            isDark = false;

            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }

            AndroidUtilities.lockOrientation(baseFragment.getParentActivity());
            if (useDisplayFlashlight()) {
                flashViews.flashIn(() -> startRecording(byLongPress, whenStarted));
            } else {
                startRecording(byLongPress, whenStarted);
            }
        }

        @Override
        public void onVideoRecordPause() {
            // Implement pause logic if needed
        }

        @Override
        public void onVideoRecordResume() {
            // Implement resume logic if needed
        }

        @Override
        public void onVideoRecordEnd(boolean byDuration) {
            if (stoppingTakingVideo || !takingVideo) {
                return;
            }
            stoppingTakingVideo = true;

            AndroidUtilities.runOnUIThread(() -> {
                if (takingVideo && stoppingTakingVideo && cameraView != null) {
                    showZoomControls(false, true);
                    CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionRecording(), false, false);
                }
            }, byDuration ? 0 : 400);

            BaseFragment baseFragment = parentAlert.baseFragment;
            if (baseFragment == null) {
                baseFragment = LaunchActivity.getLastFragment();
            }
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }
            AndroidUtilities.unlockOrientation(baseFragment.getParentActivity());
        }

        @Override
        public void onVideoDuration(long duration) {
            videoTimerView.setDuration(duration, true);
        }

        @Override
        public void onGalleryClick() {
            if (cameraPhotoRecyclerView == null) {
                return;
            }

            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();

            boolean close = cameraPhotoRecyclerView.getVisibility() == View.VISIBLE;
            if (close) {
                if (!collageLayoutView.hasLayout()) {
                    cameraPhotos.clear();
                    selectedPhotosOrder.clear();
                    selectedPhotos.clear();
                    counterTextView.setVisibility(View.INVISIBLE);
                    parentAlert.updateCountButton(0);
                }
                cameraPhotoRecyclerView.setVisibility(View.GONE);
            } else {
                if (!collageLayoutView.hasLayout()) {
                    counterTextView.setVisibility(View.VISIBLE);
                    counterTextView.setAlpha(1.0f);
                    updatePhotosCounter(false);
                }
                cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onFlipClick() {
            if (cameraView == null || takingPhoto || !cameraView.isInited()) {
                return;
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            if (useDisplayFlashlight() && frontfaceFlashModes != null && !frontfaceFlashModes.isEmpty()) {
                final String mode = frontfaceFlashModes.get(frontfaceFlashMode);
                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
                sharedPreferences.edit().putString("flashMode", mode).commit();
            }
            cameraView.switchCamera();
            saveCameraFace(cameraView.isFrontface());
            if (useDisplayFlashlight()) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
        }

        @Override
        public void onFlipLongClick() {
            if (cameraView != null) {
                cameraView.toggleDual();
            }
        }

        @Override
        public void onZoom(float zoom) {
            zoomControlView.setZoom(zoom, true);
            showZoomControls(false, true);
        }

        @Override
        public void onVideoRecordLocked() {
            hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);
        }

        @Override
        public boolean canRecordAudio() {
            BaseFragment baseFragment = parentAlert.baseFragment;
            if (baseFragment == null) {
                baseFragment = LaunchActivity.getLastFragment();
            }
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= 23) {
                if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestingPermissions = true;
                    baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                    return false;
                }
            }

            return true;
        }

        @Override
        public void onCheckClick() {
            ArrayList<MediaController.PhotoEntry> entries = collageLayoutView.getContent();
            if (entries.size() == 1) {
                outputEntry = entries.get(0);
            } else {
                outputEntry = MediaController.PhotoEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
            }
            isVideo = outputEntry != null && outputEntry.isVideo;
            if (modeSwitcherView != null) {
                modeSwitcherView.switchMode(isVideo);
            }

            if (toast != null) {
                toast.hide();
                toast = null;
            }
            if (buildingVideo != null) {
                buildingVideo.stop(true);
                buildingVideo = null;
            }

            if (outputEntry == null) {
                return;
            }

            if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                final Activity activity = AndroidUtilities.findActivity(getContext());
                if (activity != null) {
                    activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 113);
                }
                return;
            }

            final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();

            if (outputEntry.wouldBeVideo()) {
                toast = new DownloadButton.PreparingVideoToast(getContext());
                toast.setOnCancelListener(() -> {
                    if (buildingVideo != null) {
                        buildingVideo.stop(true);
                        buildingVideo = null;
                    }
                    if (toast != null) {
                        toast.hide();
                    }
                });
                parentAlert.getContainer().addView(toast);

                final File file = AndroidUtilities.generateVideoPath();
                buildingVideo = new BuildingVideo(parentAlert.currentAccount, outputEntry, file, () -> {
                    if (outputEntry == null) {
                        return;
                    }
                    toast.setDone(R.raw.done, LocaleController.getString("Proceed"), 3500);

                    String videoPath = file.getAbsolutePath();
                    MediaMetadataRetriever mediaMetadataRetriever = null;
                    long duration = 0;
                    try {
                        mediaMetadataRetriever = new MediaMetadataRetriever();
                        mediaMetadataRetriever.setDataSource(videoPath);
                        String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (d != null) {
                            duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        try {
                            if (mediaMetadataRetriever != null) {
                                mediaMetadataRetriever.release();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    final Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
                    final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                    outputEntry.buildPhoto(cacheFile);
                    SharedConfig.saveConfig();

                    MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, lastImageId--, 0, videoPath, 0, true, bitmap.getWidth(), bitmap.getHeight(), 0);
                    entry.duration = (int) duration;
                    entry.thumbPath = cacheFile.getAbsolutePath();
                    entry.canDeleteAfter = true;
                    openPhotoViewer(entry, sameTakePictureOrientation, true);
                }, progress -> {
                    if (toast != null) {
                        toast.setProgress(progress);
                    }
                }, () -> {
                    if (outputEntry == null) {
                        return;
                    }
                    toast.setDone(R.raw.error, LocaleController.getString("VideoConvertFail"), 3500);
                });
            } else {
                final File file = AndroidUtilities.generatePicturePath(false, "png");
                if (file == null) {
                    toast.setDone(R.raw.error, LocaleController.getString("UnknownError"), 3500);
                    return;
                }
                Utilities.themeQueue.postRunnable(() -> {
                    outputEntry.buildPhoto(file);

                    AndroidUtilities.runOnUIThread(() -> {
                        if (outputEntry == null) {
                            return;
                        }

                        if (toast != null) {
                            toast.hide();
                            toast = null;
                        }
                        toast = new DownloadButton.PreparingVideoToast(getContext());
                        toast.setDone(R.raw.done, LocaleController.getString("Proceed"), 2500);
                        parentAlert.getContainer().addView(toast);

                        String currentPicturePath = file.getAbsolutePath();
                        Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(currentPicturePath);
                        int width = 0, height = 0;
                        try {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(new File(currentPicturePath).getAbsolutePath(), options);
                            width = options.outWidth;
                            height = options.outHeight;
                        } catch (Exception ignore) {}
                        MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation.first, false, width, height, 0).setOrientation(orientation);
                        photoEntry.canDeleteAfter = true;
                        openPhotoViewer(photoEntry, sameTakePictureOrientation, true);
                    });
                });
            }
        }
    };

    private DownloadButton.PreparingVideoToast toast;
    private BuildingVideo buildingVideo;

    private static class BuildingVideo implements NotificationCenter.NotificationCenterDelegate {

        final int currentAccount;
        final MediaController.PhotoEntry entry;
        final File file;

        private MessageObject messageObject;
        private final Runnable onDone;
        private final Utilities.Callback<Float> onProgress;
        private final Runnable onCancel;

        public BuildingVideo(int account, MediaController.PhotoEntry entry, File file, @NonNull Runnable onDone, @Nullable Utilities.Callback<Float> onProgress, @NonNull Runnable onCancel) {
            this.currentAccount = account;
            this.entry = entry;
            this.file = file;
            this.onDone = onDone;
            this.onProgress = onProgress;
            this.onCancel = onCancel;

            start();
        }

        public void start() {
            if (messageObject != null) {
                return;
            }

            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingStarted);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileNewChunkAvailable);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingFailed);

            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = 1;
            message.attachPath = file.getAbsolutePath();
            messageObject = new MessageObject(currentAccount, message, (MessageObject) null, false, false);
            entry.getVideoEditedInfo(info -> {
                if (messageObject == null) {
                    return;
                }
                messageObject.videoEditedInfo = info;
                MediaController.getInstance().scheduleVideoConvert(messageObject);
            });
        }

        public void stop(boolean cancel) {
            if (messageObject == null) {
                return;
            }

            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingStarted);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingFailed);

            if (cancel) {
                MediaController.getInstance().cancelVideoConvert(messageObject);
            }
            messageObject = null;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.filePreparingStarted) {
                if ((MessageObject) args[0] == messageObject) {

                }
            } else if (id == NotificationCenter.fileNewChunkAvailable) {
                if ((MessageObject) args[0] == messageObject) {
                    String finalPath = (String) args[1];
                    long availableSize = (Long) args[2];
                    long finalSize = (Long) args[3];
                    float progress = (float) args[4];

                    if (onProgress != null) {
                        onProgress.run(progress);
                    }

                    if (finalSize > 0) {
                        onDone.run();
                        VideoEncodingService.stop();
                        stop(false);
                    }
                }
            } else if (id == NotificationCenter.filePreparingFailed) {
                if ((MessageObject) args[0] == messageObject) {
                    stop(false);
                    try {
                        if (file != null) {
                            file.delete();
                        }
                    } catch (Exception ignore) {}
                    onCancel.run();
                }
            }
        }
    }

    public void takePicture(Utilities.Callback<Runnable> done) {
        if (cameraView == null || parentAlert == null || parentAlert.isDismissed()) {
            return;
        }
        final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();
        cameraView.getCameraSession().setFlipFront(parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2);

        boolean savedFromTextureView = false;
        if (!useDisplayFlashlight()) {
            cameraView.startTakePictureAnimation(true);
        }

        if (cameraView.isDual() && TextUtils.equals(cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF) || collageLayoutView.hasLayout()) {
            if (!collageLayoutView.hasLayout()) {
                cameraView.pauseAsTakingPicture();
            }
            final Bitmap bitmap = cameraView.getTextureView().getBitmap();
            try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                savedFromTextureView = true;
            } catch (Exception e) {
                FileLog.e(e);
            }
            bitmap.recycle();
        }
        if (!savedFromTextureView) {
            takingPhoto = CameraController.getInstance().takePicture(outputFile, true, cameraView.getCameraSessionObject(), (orientation) -> {
                if (useDisplayFlashlight()) {
                    try {
                        parentAlert.getWindowView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                }
                takingPhoto = false;
                if (outputFile == null || parentAlert.destroyed) {
                    return;
                }
                mediaFromExternalCamera = false;

                int w = -1, h = -1;
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                    w = opts.outWidth;
                    h = opts.outHeight;
                } catch (Exception ignore) {}

                int rotate = orientation == -1 ? 0 : 90;
                if (orientation == -1) {
                    if (w > h) {
                        rotate = 270;
                    }
                } else if (h > w && rotate != 0) {
                    rotate = 0;
                }
                MediaController.PhotoEntry entry = MediaController.PhotoEntry.fromPhotoShoot(lastImageId--, outputFile, rotate);
                if (collageLayoutView.hasLayout()) {
                    outputFile = null;
                    if (collageLayoutView.push(entry)) {
                        outputEntry = MediaController.PhotoEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                        fromGallery = false;

                        if (done != null) {
                            done.run(null);
                        }

                    } else if (done != null) {
                        done.run(null);
                    }
                    updateActionBarButtons(true);
                } else {
                    outputEntry = entry;
                    fromGallery = false;

                    if (done != null) {
                        done.run(() -> {
                            openPhotoViewer(outputEntry, sameTakePictureOrientation, false);
                        });
                    } else {
                        openPhotoViewer(outputEntry, sameTakePictureOrientation, false);
                    }
                }
            });
        } else {
            takingPhoto = false;
            final MediaController.PhotoEntry entry = MediaController.PhotoEntry.fromPhotoShoot(lastImageId--, outputFile, 0);
            if (collageLayoutView.hasLayout()) {
                outputFile = null;
                if (collageLayoutView.push(entry)) {
                    outputEntry = MediaController.PhotoEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                    fromGallery = false;
                    if (done != null) {
                        done.run(null);
                    }

                } else if (done != null) {
                    done.run(null);
                }
                updateActionBarButtons(true);
            } else {
                outputEntry = entry;
                fromGallery = false;

                if (done != null) {
                    done.run(() -> openPhotoViewer(outputEntry, sameTakePictureOrientation, false));
                } else {
                    openPhotoViewer(outputEntry, sameTakePictureOrientation, false);
                }
            }
        }
    }

    private void startRecording(boolean byLongPress, Runnable whenStarted) {
        if (cameraView == null) {
            return;
        }
        CameraController.getInstance().recordVideo(cameraView.getCameraSessionObject(), outputFile, parentAlert.avatarPicker != 0, (thumbPath, duration) -> {
            if (recordControl != null) {
                recordControl.stopRecordingLoading(true);
            }
            if (useDisplayFlashlight()) {
                flashViews.flashOut();
            }
            if (outputFile == null || parentAlert.destroyed || cameraView == null) {
                return;
            }
            takingVideo = false;
            stoppingTakingVideo = false;

            if (duration <= 800) {
                animateRecording(false, true);
                videoTimerView.setRecording(false, true);
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                try {
                    outputFile.delete();
                    outputFile = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (thumbPath != null) {
                    try {
                        new File(thumbPath).delete();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                return;
            }

            showVideoTimer(false, true);

            mediaFromExternalCamera = false;

            MediaController.PhotoEntry photoEntry = MediaController.PhotoEntry.fromVideoShoot(lastImageId--, outputFile, thumbPath, (int) duration);

            animateRecording(false, true);
            videoTimerView.setRecording(false, true);
            if (recordControl != null) {
                recordControl.stopRecordingLoading(true);
            }

            if (parentAlert.avatarPicker != 0 && cameraView.isFrontface()) {
                photoEntry.cropState = new MediaController.CropState();
                photoEntry.cropState.mirrored = true;
                photoEntry.cropState.freeform = false;
                photoEntry.cropState.lockedAspectRatio = 1.0f;
            }

            if (collageLayoutView.hasLayout()) {
                outputFile = null;
                photoEntry.videoVolume = 1.0f;
                if (collageLayoutView.push(photoEntry)) {
                    outputEntry = MediaController.PhotoEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                    fromGallery = false;
                    int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
                    if (width > 0 && height > 0) {
                        outputEntry.width = width;
                        outputEntry.height = height;
                        outputEntry.setupMatrix();
                    }
                }
                updateActionBarButtons(true);
            } else {
                outputEntry = photoEntry;
                fromGallery = false;
                int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
                if (width > 0 && height > 0) {
                    outputEntry.width = width;
                    outputEntry.height = height;
                    outputEntry.setupMatrix();
                }

                openPhotoViewer(outputEntry, false, false);
            }
        }, () -> {
            whenStarted.run();

            hintTextView.setText(getString(byLongPress ? R.string.StoryHintSwipeToZoom : R.string.StoryHintPinchToZoom), false);
            animateRecording(true, true);

            collageListView.setVisible(false, true);
            videoTimerView.setRecording(true, true);
            showVideoTimer(true, true);
        }, cameraView, true);

        if (!isVideo) {
            isVideo = true;
            collageListView.setVisible(false, true);
            showVideoTimer(isVideo, true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        }

        cameraView.runHaptic();
    }

    private void saveCameraFace(boolean frontface) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("stories_camera", frontface).apply();
    }

    private boolean getCameraFace() {
        return MessagesController.getGlobalMainSettings().getBoolean("stories_camera", false);
    }

    private String getCurrentFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode);
        }
        return cameraView.getCameraSession().getCurrentFlashMode();
    }

    private String getNextFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode + 1 >= frontfaceFlashModes.size() ? 0 : frontfaceFlashMode + 1);
        }
        return cameraView.getCameraSession().getNextFlashMode();
    }

    private void setCurrentFlashMode(String mode) {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            int index = frontfaceFlashModes.indexOf(mode);
            if (index >= 0) {
                frontfaceFlashMode = index;
                MessagesController.getGlobalMainSettings().edit().putInt("frontflash", frontfaceFlashMode).apply();
            }
            return;
        }
        cameraView.getCameraSession().setCurrentFlashMode(mode);
    }

    private int frontfaceFlashMode = -1;
    private ArrayList<String> frontfaceFlashModes;
    private void checkFrontfaceFlashModes() {
        if (frontfaceFlashMode < 0) {
            frontfaceFlashMode = MessagesController.getGlobalMainSettings().getInt("frontflash", 1);
            frontfaceFlashModes = new ArrayList<>();
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_ON);

            flashViews.setWarmth(MessagesController.getGlobalMainSettings().getFloat("frontflash_warmth", .9f));
            flashViews.setIntensity(MessagesController.getGlobalMainSettings().getFloat("frontflash_intensity", 1));
        }
    }
    private void saveFrontFaceFlashMode() {
        if (frontfaceFlashMode >= 0) {
            MessagesController.getGlobalMainSettings().edit()
                    .putFloat("frontflash_warmth", flashViews.warmth)
                    .putFloat("frontflash_intensity", flashViews.intensity)
                    .apply();
        }
    }

    public void showAvatarConstructorFragment(AvatarConstructorPreviewCell view, TLRPC.VideoSize emojiMarkupStrat) {
        AvatarConstructorFragment avatarConstructorFragment = new AvatarConstructorFragment(parentAlert.parentImageUpdater, parentAlert.getAvatarFor());
        avatarConstructorFragment.finishOnDone = !(parentAlert.getAvatarFor() != null && parentAlert.getAvatarFor().type == ImageUpdater.TYPE_SUGGEST_PHOTO_FOR_USER);
        parentAlert.baseFragment.presentFragment(avatarConstructorFragment);
        if (view != null) {
            avatarConstructorFragment.startFrom(view);
        }
        if (emojiMarkupStrat != null) {
            avatarConstructorFragment.startFrom(emojiMarkupStrat);
        }
        avatarConstructorFragment.setDelegate((gradient, documentId, document, previewView) -> {
            selectedPhotos.clear();
            Bitmap bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            GradientTools gradientTools = new GradientTools();
            if (gradient != null) {
                gradientTools.setColors(gradient.color1, gradient.color2, gradient.color3, gradient.color4);
            } else {
                gradientTools.setColors(AvatarConstructorFragment.defaultColors[0][0], AvatarConstructorFragment.defaultColors[0][1],  AvatarConstructorFragment.defaultColors[0][2], AvatarConstructorFragment.defaultColors[0][3]);
            }
            gradientTools.setBounds(0, 0, 800, 800);
            canvas.drawRect(0, 0, 800, 800, gradientTools.paint);

            File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "avatar_background.png");
            try {
                file.createNewFile();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);
                byte[] bitmapdata = bos.toByteArray();

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bitmapdata);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            float scale = AvatarConstructorFragment.STICKER_DEFAULT_SCALE;
            int imageX, imageY;
            imageX = imageY = (int) (800 * (1f - scale) / 2f);
            int imageSize = (int) (800 * scale);

            ImageReceiver imageReceiver = previewView.getImageReceiver();
            if (imageReceiver.getAnimation() != null) {
                Bitmap firstFrame = imageReceiver.getAnimation().getFirstFrame(null);
                ImageReceiver firstFrameReceiver = new ImageReceiver();
                firstFrameReceiver.setImageBitmap(firstFrame);
                firstFrameReceiver.setImageCoords(imageX, imageY, imageSize, imageSize);
                firstFrameReceiver.setRoundRadius((int) (imageSize * AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS));
                firstFrameReceiver.draw(canvas);
                firstFrameReceiver.clearImage();
                firstFrame.recycle();
            } else {
                if (imageReceiver.getLottieAnimation() != null) {
                    imageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                }
                imageReceiver.setImageCoords(imageX, imageY, imageSize, imageSize);
                imageReceiver.setRoundRadius((int) (imageSize * AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS));
                imageReceiver.draw(canvas);
            }

            File thumb = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "avatar_background.png");
            try {
                thumb.createNewFile();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);
                byte[] bitmapdata = bos.toByteArray();

                FileOutputStream fos = new FileOutputStream(thumb);
                fos.write(bitmapdata);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            MediaController.PhotoEntry photoEntry;
            if (previewView.hasAnimation()) {
                photoEntry = new MediaController.PhotoEntry(0, 0, 0, file.getPath(), 0, false, 0, 0, 0);
                photoEntry.thumbPath = thumb.getPath();

                if (previewView.documentId != 0) {
                    TLRPC.TL_videoSizeEmojiMarkup emojiMarkup = new TLRPC.TL_videoSizeEmojiMarkup();
                    emojiMarkup.emoji_id = previewView.documentId;
                    emojiMarkup.background_colors.add(previewView.backgroundGradient.color1);
                    if (previewView.backgroundGradient.color2 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color2);
                    }
                    if (previewView.backgroundGradient.color3 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color3);
                    }
                    if (previewView.backgroundGradient.color4 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color4);
                    }
                    photoEntry.emojiMarkup = emojiMarkup;
                } else if (previewView.document != null) {
                    TLRPC.TL_videoSizeStickerMarkup emojiMarkup = new TLRPC.TL_videoSizeStickerMarkup();
                    emojiMarkup.sticker_id = previewView.document.id;
                    emojiMarkup.stickerset = MessageObject.getInputStickerSet(previewView.document);
                    emojiMarkup.background_colors.add(previewView.backgroundGradient.color1);
                    if (previewView.backgroundGradient.color2 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color2);
                    }
                    if (previewView.backgroundGradient.color3 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color3);
                    }
                    if (previewView.backgroundGradient.color4 != 0) {
                        emojiMarkup.background_colors.add(previewView.backgroundGradient.color4);
                    }
                    photoEntry.emojiMarkup = emojiMarkup;
                }

                photoEntry.editedInfo = new VideoEditedInfo();
                photoEntry.editedInfo.originalPath = file.getPath();
                photoEntry.editedInfo.resultWidth = 800;
                photoEntry.editedInfo.resultHeight = 800;
                photoEntry.editedInfo.originalWidth = 800;
                photoEntry.editedInfo.originalHeight = 800;
                photoEntry.editedInfo.isPhoto = true;
                photoEntry.editedInfo.bitrate = -1;
                photoEntry.editedInfo.muted = true;

                photoEntry.editedInfo.start = photoEntry.editedInfo.startTime = 0;
                photoEntry.editedInfo.endTime = previewView.getDuration();
                photoEntry.editedInfo.framerate = 30;

                photoEntry.editedInfo.avatarStartTime = 0;
                photoEntry.editedInfo.estimatedSize = (int) (photoEntry.editedInfo.endTime / 1000.0f * 115200);
                photoEntry.editedInfo.estimatedDuration = photoEntry.editedInfo.endTime;

                VideoEditedInfo.MediaEntity mediaEntity = new VideoEditedInfo.MediaEntity();
                mediaEntity.type = 0;

                if (document == null) {
                    document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, documentId);
                }
                if (document == null) {
                    return;
                }
                mediaEntity.viewWidth = (int) (800 * scale);
                mediaEntity.viewHeight = (int) (800 * scale);
                mediaEntity.width = scale;
                mediaEntity.height = scale;
                mediaEntity.x = (1f - scale) / 2f;
                mediaEntity.y = (1f - scale) / 2f;
                mediaEntity.document = document;
                mediaEntity.parentObject = null;
                mediaEntity.text = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true).getAbsolutePath();
                mediaEntity.roundRadius = AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS;
                if (MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isVideoStickerDocument(document)) {
                    boolean isAnimatedSticker = MessageObject.isAnimatedStickerDocument(document, true);
                    mediaEntity.subType |= isAnimatedSticker ? 1 : 4;
                }
                if (MessageObject.isTextColorEmoji(document)) {
                    mediaEntity.color = 0xFFFFFFFF;
                    mediaEntity.subType |= 8;
                }

                photoEntry.editedInfo.mediaEntities = new ArrayList<>();
                photoEntry.editedInfo.mediaEntities.add(mediaEntity);
            } else {
                photoEntry = new MediaController.PhotoEntry(0, 0, 0, thumb.getPath(), 0, false, 0, 0, 0);
            }
            selectedPhotos.put(-1, photoEntry);
            selectedPhotosOrder.add(-1);
            parentAlert.delegate.didPressedButton(7, true, false, 0, 0, parentAlert.isCaptionAbove(), false);
            if (!avatarConstructorFragment.finishOnDone) {
                if (parentAlert.baseFragment != null) {
                    parentAlert.baseFragment.removeSelfFromStack();
                }
                avatarConstructorFragment.finishFragment();
            }
        });
    }

    private boolean checkSendMediaEnabled(MediaController.PhotoEntry photoEntry) {
        if (!videoEnabled && photoEntry.isVideo) {
            if (parentAlert.checkCanRemoveRestrictionsByBoosts()) {
                return true;
            }
            BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(
                    LocaleController.getString(R.string.GlobalAttachVideoRestricted)
            ).show();
            return true;
        } else if (!photoEnabled && !photoEntry.isVideo) {
            if (parentAlert.checkCanRemoveRestrictionsByBoosts()) {
                return true;
            }
            BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(
                    LocaleController.getString(R.string.GlobalAttachPhotoRestricted)
            ).show();
            return true;
        }
        return false;
    }

    private int maxCount() {
        if (parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
            return parentAlert.baseFragment.getMessagesController().quickReplyMessagesLimit - ((ChatActivity) parentAlert.baseFragment).messages.size();
        }
        return Integer.MAX_VALUE;
    }

    private int addToSelectedPhotos(MediaController.PhotoEntry object, int index) {
        Object key = object.imageId;
        if (selectedPhotos.containsKey(key)) {
            object.starsAmount = 0;
            object.hasSpoiler = false;

            selectedPhotos.remove(key);
            int position = selectedPhotosOrder.indexOf(key);
            if (position >= 0) {
                selectedPhotosOrder.remove(position);
            }
            updatePhotosCounter(false);
            updateCheckedPhotoIndices();
            if (index >= 0) {
                object.reset();
                photoViewerProvider.updatePhotoAtIndex(index);
            }
            return position;
        } else {
            object.starsAmount = getStarsPrice();
            object.hasSpoiler = getStarsPrice() > 0;
            object.isChatPreviewSpoilerRevealed = false;
            object.isAttachSpoilerRevealed = false;

            boolean changed = checkSelectedCount(true);
            selectedPhotos.put(key, object);
            selectedPhotosOrder.add(key);
            if (changed) {
                updateCheckedPhotos();
            } else {
                updatePhotosCounter(true);
            }
            return -1;
        }
    }

    private boolean checkSelectedCount(boolean beforeAdding) {
        boolean changed = false;
        if (getStarsPrice() > 0) {
            while (selectedPhotos.size() > 10 - (beforeAdding ? 1 : 0) && !selectedPhotosOrder.isEmpty()) {
                Object key = selectedPhotosOrder.get(0);
                Object firstPhoto = selectedPhotos.get(key);
                if (!(firstPhoto instanceof MediaController.PhotoEntry)) {
                    break;
                }
                addToSelectedPhotos((MediaController.PhotoEntry) firstPhoto, -1);
                changed = true;
            }
        }
        return changed;
    }

    public long getStarsPrice() {
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
            return photoEntry.starsAmount;
        }
        return 0;
    }

    public void setStarsPrice(long stars) {
        if (!selectedPhotos.isEmpty()) {
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                photoEntry.starsAmount = stars;
                photoEntry.hasSpoiler = stars > 0;
                photoEntry.isChatPreviewSpoilerRevealed = false;
                photoEntry.isAttachSpoilerRevealed = false;
            }
        }
        onSelectedItemsCountChanged(getSelectedItemsCount());
        if (checkSelectedCount(false)) {
            updateCheckedPhotos();
        }
    }

    private void updatePhotoStarsPrice() {
        gridView.forAllChild(view -> {
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                cell.setHasSpoiler(cell.getPhotoEntry() != null && cell.getPhotoEntry().hasSpoiler, 250f);
                cell.setStarsPrice(cell.getPhotoEntry() != null ? cell.getPhotoEntry().starsAmount : 0, selectedPhotos.size() > 1);
            }
        });
    }

    public void clearSelectedPhotos() {
        spoilerItem.setText(LocaleController.getString(R.string.EnablePhotoSpoiler));
        spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
        parentAlert.selectedMenuItem.showSubItem(compress);
        if (!selectedPhotos.isEmpty()) {
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                photoEntry.reset();
            }
            selectedPhotos.clear();
            selectedPhotosOrder.clear();
        }
        if (!cameraPhotos.isEmpty()) {
            for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                if (photoEntry.isCollage()) {
                    photoEntry.destroy();
                } else {
                    new File(photoEntry.path).delete();
                    if (photoEntry.imagePath != null) {
                        new File(photoEntry.imagePath).delete();
                    }
                    if (photoEntry.thumbPath != null) {
                        new File(photoEntry.thumbPath).delete();
                    }
                }
            }
            cameraPhotos.clear();
        }
        adapter.notifyDataSetChanged();
        cameraAttachAdapter.notifyDataSetChanged();
    }

    private void updateAlbumsDropDown() {
        dropDownContainer.removeAllSubItems();
        if (mediaEnabled) {
            ArrayList<MediaController.AlbumEntry> albums;
            if (shouldLoadAllMedia()) {
                albums = MediaController.allMediaAlbums;
            } else {
                albums = MediaController.allPhotoAlbums;
            }
            dropDownAlbums = new ArrayList<>(albums);
            Collections.sort(dropDownAlbums, (o1, o2) -> {
                if (o1.bucketId == 0 && o2.bucketId != 0) {
                    return -1;
                } else if (o1.bucketId != 0 && o2.bucketId == 0) {
                    return 1;
                }
                int index1 = albums.indexOf(o1);
                int index2 = albums.indexOf(o2);
                if (index1 > index2) {
                    return 1;
                } else if (index1 < index2) {
                    return -1;
                } else {
                    return 0;
                }

            });
        } else {
            dropDownAlbums = new ArrayList<>();
        }
        if (dropDownAlbums.isEmpty()) {
            dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        } else {
            dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null);
            for (int a = 0, N = dropDownAlbums.size(); a < N; a++) {
                MediaController.AlbumEntry album = dropDownAlbums.get(a);
                AlbumButton btn = new AlbumButton(getContext(), album.coverPhoto, album.bucketName, album.photos.size(), resourcesProvider);
                dropDownContainer.getPopupLayout().addView(btn);
                final int i = a + 10;
                btn.setOnClickListener(v -> {
                    parentAlert.actionBar.getActionBarMenuOnItemClick().onItemClick(i);
                    dropDownContainer.toggleSubMenu();
                });
            }
        }
    }

    private void resetRecordState() {
        if (parentAlert.destroyed) {
            return;
        }

        AndroidUtilities.unlockOrientation(AndroidUtilities.findActivity(getContext()));
    }

    protected void openPhotoViewer(MediaController.PhotoEntry entry, final boolean sameTakePictureOrientation, boolean external) {
        if (entry != null) {
            cameraPhotos.add(entry);
            selectedPhotos.put(entry.imageId, entry);
            selectedPhotosOrder.add(entry.imageId);
            parentAlert.updateCountButton(0);
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }

        if (entry != null && !external && cameraPhotos.size() > 1) {
            updatePhotosCounter(false);
            if (cameraView != null) {
                zoomControlView.setZoom(0.0f, false);
                cameraZoom = 0.0f;
                cameraView.setZoom(0.0f);
                CameraController.getInstance().startPreview(cameraView.getCameraSessionObject());
            }
            return;
        }

        cancelTakingPhotos = true;

        BaseFragment fragment = parentAlert.baseFragment;
        if (fragment == null) {
            fragment = LaunchActivity.getLastFragment();
        }
        if (fragment == null) {
            return;
        }

        PhotoViewer.getInstance().setParentActivity(fragment.getParentActivity(), resourcesProvider);
        PhotoViewer.getInstance().setParentAlert(parentAlert);
        PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);

        ChatActivity chatActivity;
        int type;
        if (parentAlert.isPhotoPicker && parentAlert.isStickerMode) {
            type = PhotoViewer.SELECT_TYPE_STICKER;
            chatActivity = (ChatActivity) parentAlert.baseFragment;
        } else if (parentAlert.avatarPicker != 0) {
            type = PhotoViewer.SELECT_TYPE_AVATAR;
            chatActivity = null;
        } else if (parentAlert.baseFragment instanceof ChatActivity) {
            chatActivity = (ChatActivity) parentAlert.baseFragment;
            type = 2;
        } else {
            chatActivity = null;
            type = 5;
        }

        ArrayList<Object> arrayList;
        int index;

        if (parentAlert.avatarPicker != 0) {
            arrayList = new ArrayList<>();
            arrayList.add(entry);
            index = 0;
        } else {
            if (cameraPhotos.isEmpty() && !selectedPhotos.isEmpty()) {
                cameraPhotos.addAll(selectedPhotos.values());
                arrayList = new ArrayList<>(selectedPhotos.values());
                index = entry != null ? arrayList.size() - 1 : 0;
            } else {
                arrayList = getAllPhotosArray();
                index = cameraPhotos.size() - 1;
            }
        }

        if (arrayList.isEmpty()) {
            return;
        }

        if (parentAlert.getAvatarFor() != null && entry != null) {
            parentAlert.getAvatarFor().isVideo = entry.isVideo;
        }

        if (index < 0) {
            FileLog.e("openPhotoViewer: index < 0");
            return;
        }

        PhotoViewer.getInstance().openPhotoForSelect(arrayList, index, type, false, new BasePhotoProvider() {

            @Override
            public void onOpen() {
                pauseCameraPreview();
            }

            @Override
            public void onClose() {
                resumeCameraPreview();
            }

            public void onEditModeChanged(boolean isEditMode) {
                onPhotoEditModeChanged(isEditMode);
            }

            @Override
            public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                return null;
            }

            @Override
            public boolean cancelButtonPressed() {
                if (cameraOpened && cameraView != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (collageLayoutView != null && !parentAlert.isDismissed() && Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                        }
                    }, 1000);
                    zoomControlView.setZoom(0.0f, false);
                    cameraZoom = 0.0f;
                    cameraView.setZoom(0.0f);
                    CameraController.getInstance().startPreview(cameraView.getCameraSession());
                }
                if (cancelTakingPhotos && cameraPhotos.size() == 1) {
                    for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                        if (photoEntry.isCollage()) {
                            photoEntry.destroy();
                        } else {
                            new File(photoEntry.path).delete();
                        }
                        if (photoEntry.imagePath != null) {
                            new File(photoEntry.imagePath).delete();
                        }
                        if (photoEntry.thumbPath != null) {
                            new File(photoEntry.thumbPath).delete();
                        }
                    }
                    cameraPhotos.clear();
                    selectedPhotosOrder.clear();
                    selectedPhotos.clear();
                    counterTextView.setVisibility(View.INVISIBLE);
                    cameraPhotoRecyclerView.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                    cameraAttachAdapter.notifyDataSetChanged();
                    parentAlert.updateCountButton(0);
                }
                return true;
            }

            @Override
            public void needAddMorePhotos() {
                cancelTakingPhotos = false;
                if (mediaFromExternalCamera) {
                    parentAlert.delegate.didPressedButton(0, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    return;
                }
                if (!cameraOpened) {
                    openCamera(false);
                }
                counterTextView.setVisibility(View.VISIBLE);
                cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
                counterTextView.setAlpha(1.0f);
                updatePhotosCounter(false);
            }

            @Override
            public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                parentAlert.sent = true;
                if (cameraPhotos.isEmpty() || parentAlert.destroyed) {
                    return;
                }
                if (videoEditedInfo != null && index >= 0 && index < cameraPhotos.size()) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                }
                if (!(parentAlert.baseFragment instanceof ChatActivity) || !((ChatActivity) parentAlert.baseFragment).isSecretChat()) {
                    for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                        if (photoEntry.ttl > 0) {
                            continue;
                        }
                        AndroidUtilities.addMediaToGallery(photoEntry.path);
                    }
                }
                parentAlert.applyCaption();
                closeCamera(false);
                parentAlert.delegate.didPressedButton(forceDocument ? 4 : 8, true, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), forceDocument);
                cameraPhotos.clear();
                selectedPhotosOrder.clear();
                selectedPhotos.clear();
                adapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
                parentAlert.dismiss(true);
            }

            @Override
            public boolean scaleToFill() {
                if (parentAlert.destroyed) {
                    return false;
                }
                int locked = Settings.System.getInt(getContext().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                return sameTakePictureOrientation || locked == 1;
            }

            @Override
            public void willHidePhotoViewer() {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = gridView.getChildAt(a);
                    if (view instanceof PhotoAttachPhotoCell) {
                        PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                        cell.showImage();
                        cell.showCheck(true);
                    }
                }
            }

            @Override
            public boolean canScrollAway() {
                return false;
            }

            @Override
            public boolean canCaptureMorePhotos() {
                return parentAlert.maxSelectedPhotos != 1;
            }

            @Override
            public boolean allowCaption() {
                return !parentAlert.isPhotoPicker;
            }
        }, chatActivity);
        PhotoViewer.getInstance().setAvatarFor(parentAlert.getAvatarFor());
        if (parentAlert.isStickerMode) {
            PhotoViewer.getInstance().enableStickerMode(null, false, parentAlert.customStickerHandler);
            PhotoViewer.getInstance().prepareSegmentImage();
        }
    }

    private Runnable zoomControlHideRunnable;
    private AnimatorSet zoomControlAnimation;

    private void showZoomControls(boolean show, boolean animated) {
        if (zoomControlView.getTag() != null && show || zoomControlView.getTag() == null && !show) {
            if (show) {
                if (zoomControlHideRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                }
                AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                    showZoomControls(false, true);
                    zoomControlHideRunnable = null;
                }, 2000);
            }
            return;
        }
        if (zoomControlAnimation != null) {
            zoomControlAnimation.cancel();
        }
        zoomControlView.setTag(show ? 1 : null);
        zoomControlAnimation = new AnimatorSet();
        zoomControlAnimation.setDuration(180);
        if (show) {
            zoomControlView.setVisibility(View.VISIBLE);
        }
        zoomControlAnimation.playTogether(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, show ? 1.0f : 0.0f));
        zoomControlAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    zoomControlView.setVisibility(View.GONE);
                }
                zoomControlAnimation = null;
            }
        });
        zoomControlAnimation.start();
        if (show) {
            AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                showZoomControls(false, true);
                zoomControlHideRunnable = null;
            }, 2000);
        }
    }

    protected void updatePhotosCounter(boolean added) {
        if (counterTextView == null || parentAlert.avatarPicker != 0 || parentAlert.storyMediaPicker) {
            return;
        }
        boolean hasVideo = false;
        boolean hasPhotos = false;
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
            if (photoEntry.isVideo) {
                hasVideo = true;
            } else {
                hasPhotos = true;
            }
            if (hasVideo && hasPhotos) {
                break;
            }
        }
        int newSelectedCount = Math.max(1, selectedPhotos.size());
        if (hasVideo && hasPhotos) {
            counterTextView.setText(LocaleController.formatPluralString("Media", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                parentAlert.selectedTextView.setText(LocaleController.formatPluralString("MediaSelected", newSelectedCount));
            }
        } else if (hasVideo) {
            counterTextView.setText(LocaleController.formatPluralString("Videos", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                parentAlert.selectedTextView.setText(LocaleController.formatPluralString("VideosSelected", newSelectedCount));
            }
        } else {
            counterTextView.setText(LocaleController.formatPluralString("Photos", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                parentAlert.selectedTextView.setText(LocaleController.formatPluralString("PhotosSelected", newSelectedCount));
            }
        }
        parentAlert.setCanOpenPreview(newSelectedCount > 1);
        currentSelectedCount = newSelectedCount;
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view.getTop() >= gridView.getMeasuredHeight() - parentAlert.getClipLayoutBottom()) {
                continue;
            }
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                if (cell.getImageView().getTag() != null && (Integer) cell.getImageView().getTag() == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    public void checkCamera(boolean request) {
        if (parentAlert.destroyed || !needCamera) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        boolean old2 = noCameraPermissions;
        BaseFragment fragment = parentAlert.baseFragment;
        if (fragment == null) {
            fragment = LaunchActivity.getLastFragment();
        }
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (!SharedConfig.inappCamera) {
            deviceHasGoodCamera = false;
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                if (noCameraPermissions = (fragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
                    if (request) {
                        try {
                            parentAlert.baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, 17);
                        } catch (Exception ignore) {

                        }
                    }
                    deviceHasGoodCamera = false;
                } else {
                    if (request || SharedConfig.hasCameraCache) {
                        CameraController.getInstance().initCamera(null);
                    }
                    deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
                }
            } else {
                if (request || SharedConfig.hasCameraCache) {
                    CameraController.getInstance().initCamera(null);
                }
                deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
            }
        }
        if ((old != deviceHasGoodCamera || old2 != noCameraPermissions) && adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (!parentAlert.destroyed && parentAlert.isShowing() && deviceHasGoodCamera && parentAlert.getBackDrawable().getAlpha() != 0 && !cameraOpened) {
            showCamera();
        }
    }

    boolean cameraExpanded;
    private void openCamera(boolean animated) {
        if (cameraView == null || cameraInitAnimation != null || parentAlert.isDismissed()) {
            return;
        }

        cameraView.initTexture();
        if (cameraPhotos.isEmpty()) {
            counterTextView.setVisibility(View.INVISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
        } else {
            counterTextView.setVisibility(View.VISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
        }
        if (parentAlert.getCommentView().isKeyboardVisible() && isFocusable()) {
            parentAlert.getCommentView().closeKeyboard();
        }
        if (videoTimerShown) {
            videoTimerView.setVisibility(VISIBLE);
        }
        actionBarContainer.setVisibility(View.VISIBLE);
        controlContainer.setVisibility(View.VISIBLE);
        controlContainer.setTag(null);
        navbarContainer.setVisibility(View.VISIBLE);
        animateCameraValues[0] = 0;
        animateCameraValues[1] = itemSize;
        animateCameraValues[2] = itemSize;
        additionCloseCameraY = 0;
        cameraExpanded = true;

        AndroidUtilities.hideKeyboard(this);
        parentAlert.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
        if (animated) {
            setCameraOpenProgress(0);
            cameraAnimationInProgress = true;
            notificationsLocker.lock();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(actionBarContainer, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(videoTimerView, View.ALPHA, videoTimerShown ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(controlContainer, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(navbarContainer, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraCell, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(350);
            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    notificationsLocker.unlock();
                    cameraAnimationInProgress = false;
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.invalidateOutline();
                        } else {
                            collageLayoutView.invalidate();
                        }
                    }
                    if (cameraOpened) {
                        parentAlert.delegate.onCameraOpened();
                    }
                    if (Build.VERSION.SDK_INT >= 21 && collageLayoutView != null) {
                        collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                    }
                }
            });
            animatorSet.start();
        } else {
            setCameraOpenProgress(1.0f);
            actionBarContainer.setAlpha(1.0f);
            controlContainer.setAlpha(1.0f);
            navbarContainer.setAlpha(1.0f);
            counterTextView.setAlpha(1.0f);
            cameraPhotoRecyclerView.setAlpha(1.0f);
            parentAlert.delegate.onCameraOpened();
            if (collageLayoutView != null && Build.VERSION.SDK_INT >= 21) {
                collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }
        cameraOpened = true;
        if (collageLayoutView != null) {
            collageLayoutView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        if (collageLayoutView != null) {
            collageLayoutView.clear(true);
            recordControl.setCollageProgress(0.0f, false);
        }
        updateActionBarButtons(animated);

        if (!LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && cameraView != null && cameraView.isInited()) {
            cameraView.showTexture(true, animated);
        }

        setActionBarButtonVisible(dualButton, cameraView.dualAvailable(), true);
        collageButton.setTranslationX(cameraView.dualAvailable() ? 0 : dp(46));
//        collageLayoutView.getLast().addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        //collageLayoutView.setCameraView(cameraView);
        if (MessagesController.getGlobalMainSettings().getInt("chathint2", 0) < 1) {
            cameraHint.show();
            MessagesController.getGlobalMainSettings().edit().putInt("chathint2", MessagesController.getGlobalMainSettings().getInt("chathint2", 0) + 1).apply();
        } else if (!cameraView.isSavedDual() && cameraView.dualAvailable() && MessagesController.getGlobalMainSettings().getInt("chatdualhint", 0) < 2) {
            dualHint.show();
        }
    }

    public void loadGalleryPhotos() {
        MediaController.AlbumEntry albumEntry;
        if (shouldLoadAllMedia()) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (albumEntry == null && Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    private boolean shouldLoadAllMedia() {
        return !parentAlert.isPhotoPicker && (parentAlert.baseFragment instanceof ChatActivity || parentAlert.storyMediaPicker || parentAlert.avatarPicker == 2);
    }

    public void showCamera() {
        if (parentAlert.paused || !mediaEnabled) {
            return;
        }
        if (cameraView != null || getContext() == null) {
            return;
        }

        final boolean lazy = !LiteMode.isEnabled(LiteMode.FLAGS_CHAT);
        cameraView = new DualCameraView(getContext(), isCameraFrontfaceBeforeEnteringEditMode != null ? isCameraFrontfaceBeforeEnteringEditMode : parentAlert.openWithFrontFaceCamera, lazy) {

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                return false;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void toggleDual() {
                super.toggleDual();
                dualButton.setValue(isDual());
                // recordControl.setDual(isDual());
                setCameraFlashModeIcon(getCurrentFlashMode(), true);
            }

            @Override
            protected void onSavedDualCameraSuccess() {
                if (MessagesController.getGlobalMainSettings().getInt("chatsvddualhint", 0) < 2) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (takingVideo || takingPhoto || cameraView == null) {
                            return;
                        }
                        if (savedDualHint != null) {
                            CharSequence text = isFrontface() ? getString(R.string.StoryCameraSavedDualBackHint) : getString(R.string.StoryCameraSavedDualFrontHint);
                            savedDualHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, savedDualHint.getTextPaint()));
                            savedDualHint.setText(text);
                            savedDualHint.show();
                            MessagesController.getGlobalMainSettings().edit().putInt("chatsvddualhint", MessagesController.getGlobalMainSettings().getInt("chatsvddualhint", 0) + 1).apply();
                        }
                    }, 340);
                }
                dualButton.setValue(isDual());
            }

            @Override
            protected void receivedAmplitude(double amplitude) {
                if (recordControl != null) {
                    recordControl.setAmplitude(Utilities.clamp((float) (amplitude / WaveDrawable.MAX_AMPLITUDE), 1, 0), true);
                }
            }
        };
        if (recordControl != null) {
            recordControl.setAmplitude(0, false);
        }
        if (cameraCell != null && lazy) {
            cameraView.setThumbDrawable(cameraCell.getDrawable());
        }
        //parentAlert.getContainer().setFocusable(true);
        cameraView.recordHevc = !collageLayoutView.hasLayout();
        collageLayoutView.setCameraView(cameraView);
        cameraView.setDelegate(() -> {
            String current = cameraView.getCameraSession().getCurrentFlashMode();
            String next = cameraView.getCameraSession().getNextFlashMode();
            if (current == null || next == null) return;

            String currentFlashMode = getCurrentFlashMode();
            if (TextUtils.equals(currentFlashMode, getNextFlashMode())) {
                currentFlashMode = null;
            }
            setCameraFlashModeIcon(currentFlashMode, true);
            if (zoomControlView != null) {
                zoomControlView.setZoom(cameraZoom = 0, false);
            }
            updateActionBarButtons(true);

            if (!cameraOpened) {
                cameraInitAnimation = new AnimatorSet();
                cameraInitAnimation.playTogether(
                        ObjectAnimator.ofFloat(collageLayoutView, View.ALPHA, 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(cameraIcon, View.ALPHA, 0.0f, 1.0f));
                cameraInitAnimation.setDuration(180);
                cameraInitAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(cameraInitAnimation)) {
                            canSaveCameraPreview = true;
                            cameraInitAnimation = null;
                            if (!isHidden) {
                                int count = gridView.getChildCount();
                                for (int a = 0; a < count; a++) {
                                    View child = gridView.getChildAt(a);
                                    if (child instanceof PhotoAttachCameraCell) {
                                        child.setVisibility(View.INVISIBLE);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        cameraInitAnimation = null;
                    }
                });
                cameraInitAnimation.start();
            }
        });

        parentAlert.getContainer().addView(collageLayoutView, 1, new LayoutParams(itemSize, itemSize));
        parentAlert.getContainer().addView(cameraIcon, 2, new LayoutParams(itemSize, itemSize));

        collageLayoutView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
        collageLayoutView.setEnabled(mediaEnabled);
        cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
        cameraIcon.setEnabled(mediaEnabled);

        if (isHidden) {
            collageLayoutView.setVisibility(GONE);
            cameraIcon.setVisibility(GONE);
        }
        if (cameraOpened) {
            cameraIcon.setAlpha(0f);
        } else {
            checkCameraViewPosition();
        }
        invalidate();

        if (zoomControlView != null) {
            zoomControlView.setZoom(0.0f, false);
            cameraZoom = 0.0f;
        }
        if (!cameraOpened) {
            collageLayoutView.setTranslationX(collageViewLocation[0]);
            collageLayoutView.setTranslationY(collageViewLocation[1] + currentPanTranslationY);
            cameraIcon.setTranslationX(collageViewLocation[0]);
            cameraIcon.setTranslationY(collageViewLocation[1] + collageViewOffsetY + currentPanTranslationY);
        }
    }

    public void hideCamera(boolean async) {
        if (!deviceHasGoodCamera || cameraView == null) {
            return;
        }
        saveLastCameraBitmap();
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                child.setVisibility(View.VISIBLE);
                ((PhotoAttachCameraCell) child).updateBitmap();
                break;
            }
        }
        cameraView.destroy(async, null);
        if (cameraInitAnimation != null) {
            cameraInitAnimation.cancel();
            cameraInitAnimation = null;
        }
        AndroidUtilities.runOnUIThread(() -> {
            AndroidUtilities.removeFromParent(cameraView);
            parentAlert.getContainer().removeView(collageLayoutView);
            parentAlert.getContainer().removeView(cameraIcon);
            if (collageLayoutView != null) {
                collageLayoutView.setCameraView(null);
            }

            cameraView = null;
        }, 300);
        canSaveCameraPreview = false;
    }

    private void saveLastCameraBitmap() {
        if (!canSaveCameraPreview) {
            return;
        }
        try {
            TextureView textureView = cameraView.getTextureView();
            Bitmap bitmap = textureView.getBitmap();
            if (bitmap != null) {
                Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), collageLayoutView.getMatrix(), true);
                bitmap.recycle();
                bitmap = newBitmap;
                Bitmap lastBitmap = Bitmap.createScaledBitmap(bitmap, 80, (int) (bitmap.getHeight() / (bitmap.getWidth() / 80.0f)), true);
                if (lastBitmap != null) {
                    if (lastBitmap != bitmap) {
                        bitmap.recycle();
                    }
                    Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.getWidth(), lastBitmap.getHeight(), lastBitmap.getRowBytes());
                    File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
                    FileOutputStream stream = new FileOutputStream(file);
                    lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    lastBitmap.recycle();
                    stream.close();
                }
            }
        } catch (Throwable ignore) {

        }
    }

    public void onActivityResultFragment(int requestCode, Intent data, String currentPicturePath) {
        if (parentAlert.destroyed) {
            return;
        }
        mediaFromExternalCamera = true;
        if (requestCode == 0) {
            PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment.getParentActivity(), resourcesProvider);
            PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
            Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(currentPicturePath);
            int width = 0, height = 0;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(new File(currentPicturePath).getAbsolutePath(), options);
                width = options.outWidth;
                height = options.outHeight;
            } catch (Exception ignore) {}
            MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation.first, false, width, height, 0).setOrientation(orientation);
            photoEntry.canDeleteAfter = true;
            openPhotoViewer(photoEntry, false, true);
        } else if (requestCode == 2) {
            String videoPath = null;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("pic path " + currentPicturePath);
            }
            if (data != null && currentPicturePath != null) {
                if (new File(currentPicturePath).exists()) {
                    data = null;
                }
            }
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("video record uri " + uri.toString());
                    }
                    videoPath = AndroidUtilities.getPath(uri);
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("resolved path = " + videoPath);
                    }
                    if (videoPath == null || !(new File(videoPath).exists())) {
                        videoPath = currentPicturePath;
                    }
                } else {
                    videoPath = currentPicturePath;
                }
                if (!(parentAlert.baseFragment instanceof ChatActivity) || !((ChatActivity) parentAlert.baseFragment).isSecretChat()) {
                    AndroidUtilities.addMediaToGallery(currentPicturePath);
                }
                currentPicturePath = null;
            }
            if (videoPath == null && currentPicturePath != null) {
                File f = new File(currentPicturePath);
                if (f.exists()) {
                    videoPath = currentPicturePath;
                }
            }

            MediaMetadataRetriever mediaMetadataRetriever = null;
            long duration = 0;
            try {
                mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(videoPath);
                String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (d != null) {
                    duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (mediaMetadataRetriever != null) {
                        mediaMetadataRetriever.release();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            final Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
            String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
            final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            try {
                FileOutputStream stream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            SharedConfig.saveConfig();

            MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, lastImageId--, 0, videoPath, 0, true, bitmap.getWidth(), bitmap.getHeight(), 0);
            entry.duration = (int) duration;
            entry.thumbPath = cacheFile.getAbsolutePath();
            openPhotoViewer(entry, false, true);
        }
    }

    float additionCloseCameraY;

    public void closeCamera(boolean animated) {
        if (takingPhoto || cameraView == null) {
            return;
        }
        animateCameraValues[1] = itemSize;
        animateCameraValues[2] = itemSize;

        if (collageLayoutView.hasContent()) {
            collageLayoutView.clear(true);
            updateActionBarButtons(animated);
        }

        if (animated) {
            additionCloseCameraY = collageLayoutView.getTranslationY();

            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f));
            animators.add(ObjectAnimator.ofFloat(actionBarContainer, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(videoTimerView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(controlContainer, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(navbarContainer, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));

            notificationsLocker.lock();
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(220);
            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    notificationsLocker.unlock();
                    cameraExpanded = false;
                    parentAlert.getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
                    setCameraOpenProgress(0f);
                    cameraAnimationInProgress = false;
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.invalidateOutline();
                        } else {
                            collageLayoutView.invalidate();
                        }
                    }
                    cameraOpened = false;

                    if (actionBarContainer != null) {
                        actionBarContainer.setVisibility(View.GONE);
                    }
                    if (controlContainer != null) {
                        controlContainer.setVisibility(View.GONE);
                    }
                    if (navbarContainer != null) {
                        navbarContainer.setVisibility(View.GONE);
                    }
                    if (cameraPhotoRecyclerView != null) {
                        cameraPhotoRecyclerView.setVisibility(View.GONE);
                    }
                    if (videoTimerView != null) {
                        videoTimerView.setVisibility(View.GONE);
                    }
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_VISIBLE);
                        }
                    }
                }
            });
            animatorSet.start();
        } else {
            cameraExpanded = false;
            parentAlert.getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
            setCameraOpenProgress(0f);
            animateCameraValues[0] = 0;
            setCameraOpenProgress(0);
            actionBarContainer.setAlpha(0);
            actionBarContainer.setVisibility(View.GONE);
            videoTimerView.setAlpha(0);
            videoTimerView.setVisibility(View.GONE);
            controlContainer.setAlpha(0);
            controlContainer.setVisibility(View.GONE);
            navbarContainer.setAlpha(0);
            navbarContainer.setVisibility(View.GONE);
            cameraPhotoRecyclerView.setAlpha(0);
            counterTextView.setAlpha(0);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
            cameraOpened = false;
            if (collageLayoutView != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    collageLayoutView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_VISIBLE);
                }
            }
        }
        if (collageLayoutView != null) {
            collageLayoutView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        if (!LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && cameraView != null) {
            cameraView.showTexture(false, animated);
        }
    }

    float animationClipTop;
    float animationClipBottom;
    float animationClipRight;
    float animationClipLeft;

    @Keep
    public void setCameraOpenProgress(float value) {
        if (collageLayoutView == null) {
            return;
        }
        cameraOpenProgress = value;
        float startWidth = animateCameraValues[1];
        float startHeight = animateCameraValues[2];
        boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
        float endWidth = parentAlert.getContainer().getWidth() - parentAlert.getLeftInset() - parentAlert.getRightInset();
        float endHeight = parentAlert.getContainer().getHeight();

        float fromX = collageViewLocation[0];
        float fromY = collageViewLocation[1];
        float toX = 0;
        float toY = additionCloseCameraY;

        if (value == 0) {
            cameraIcon.setTranslationX(collageViewLocation[0]);
            cameraIcon.setTranslationY(collageViewLocation[1] + collageViewOffsetY);
        }


        int collageViewW, collageViewH;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) collageLayoutView.getLayoutParams();

        float textureStartHeight = cameraView.getTextureHeight(startWidth, startHeight);
        float textureEndHeight = cameraView.getTextureHeight(endWidth, endHeight);

        float fromScale = textureStartHeight / textureEndHeight;
        float fromScaleY = startHeight / endHeight;
        float fromScaleX = startWidth/ endWidth;

        if (cameraExpanded) {
            collageViewW = (int) endWidth;
            collageViewH = (int) endHeight;
            final float s = fromScale * (1f - value) + value;
            cameraView.getTextureView().setScaleX(s);
            cameraView.getTextureView().setScaleY(s);

            final float sX = fromScaleX * (1f - value) + value;
            final float sY = fromScaleY * (1f - value) + value;

            final float scaleOffsetY = (1 - sY) * endHeight / 2;
            final float scaleOffsetX =  (1 - sX) * endWidth / 2;

            collageLayoutView.setTranslationX(fromX * (1f - value) + toX * value - scaleOffsetX);
            collageLayoutView.setTranslationY(fromY * (1f - value) + toY * value - scaleOffsetY);
            animationClipTop = fromY * (1f - value) - collageLayoutView.getTranslationY();
            animationClipBottom =  ((fromY + startHeight) * (1f - value) - collageLayoutView.getTranslationY()) + endHeight * value;

            animationClipLeft = fromX * (1f - value) - collageLayoutView.getTranslationX();
            animationClipRight =  ((fromX + startWidth) * (1f - value) - collageLayoutView.getTranslationX()) + endWidth * value;
        } else {
            collageViewW = (int) startWidth;
            collageViewH = (int) startHeight;
            cameraView.getTextureView().setScaleX(1f);
            cameraView.getTextureView().setScaleY(1f);
            animationClipTop = 0;
            animationClipBottom = endHeight;
            animationClipLeft = 0;
            animationClipRight = endWidth;

            collageLayoutView.setTranslationX(fromX);
            collageLayoutView.setTranslationY(fromY);
        }

        if (value <= 0.5f) {
            cameraIcon.setAlpha(1.0f - value / 0.5f);
        } else {
            cameraIcon.setAlpha(0.0f);
        }

        if (layoutParams.width != collageViewW || layoutParams.height != collageViewH) {
            layoutParams.width = collageViewW;
            layoutParams.height = collageViewH;
            collageLayoutView.requestLayout();
        }
        if (Build.VERSION.SDK_INT >= 21) {
            collageLayoutView.invalidateOutline();
        } else {
            collageLayoutView.invalidate();
        }
    }

    @Keep
    public float getCameraOpenProgress() {
        return cameraOpenProgress;
    }

    protected void checkCameraViewPosition() {
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().stickerMakerView != null && PhotoViewer.getInstance().stickerMakerView.isThanosInProgress) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            if (collageLayoutView != null) {
                collageLayoutView.invalidateOutline();
            }
            RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(itemsPerRow - 1);
            if (holder != null) {
                holder.itemView.invalidateOutline();
            }
            if (!adapter.needCamera || !deviceHasGoodCamera || selectedAlbumEntry != galleryAlbumEntry) {
                holder = gridView.findViewHolderForAdapterPosition(0);
                if (holder != null) {
                    holder.itemView.invalidateOutline();
                }
            }
        }
        if (collageLayoutView != null) {
            collageLayoutView.invalidate();
        }

        if (!deviceHasGoodCamera) {
            return;
        }
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                if (Build.VERSION.SDK_INT >= 19) {
                    if (!child.isAttachedToWindow()) {
                        break;
                    }
                }

                float topLocal = child.getY() + gridView.getY() + getY();
                float top = topLocal + parentAlert.getSheetContainer().getY();
                float left = child.getX() + gridView.getX() + getX() + parentAlert.getSheetContainer().getX();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    left -= getRootWindowInsets().getSystemWindowInsetLeft();
                }

                float maxY = (Build.VERSION.SDK_INT >= 21 && !parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() + parentAlert.topCommentContainer.getMeasuredHeight() * parentAlert.topCommentContainer.getAlpha();
                if (parentAlert.mentionContainer != null && parentAlert.mentionContainer.isReversed()) {
                    maxY = Math.max(maxY, parentAlert.mentionContainer.getY() + parentAlert.mentionContainer.clipTop() - parentAlert.currentPanTranslationY);
                }
                float newCameraViewOffsetY;
                if (topLocal < maxY) {
                    newCameraViewOffsetY = maxY - topLocal;
                } else {
                    newCameraViewOffsetY = 0;
                }

                if (newCameraViewOffsetY != collageViewOffsetY) {
                    collageViewOffsetY = newCameraViewOffsetY;
                    if (collageLayoutView != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            collageLayoutView.invalidateOutline();
                        } else {
                            collageLayoutView.invalidate();
                        }
                    }
                    if (cameraIcon != null) {
                        cameraIcon.invalidate();
                    }
                }

                int containerHeight = parentAlert.getSheetContainer().getMeasuredHeight();
                maxY = (int) (containerHeight - parentAlert.buttonsRecyclerView.getMeasuredHeight() + parentAlert.buttonsRecyclerView.getTranslationY());
                if (parentAlert.mentionContainer != null) {
                    maxY -= parentAlert.mentionContainer.clipBottom() - dp(6);
                }

                if (topLocal + child.getMeasuredHeight() > maxY) {
                    collageViewOffsetBottomY = Math.min(-dp(5), topLocal - maxY) + child.getMeasuredHeight();
                } else {
                    collageViewOffsetBottomY = 0;
                }

                collageViewLocation[0] = left;
                collageViewLocation[1] = top;
                applyCameraViewPosition();
                return;
            }
        }


        if (collageViewOffsetY != 0 || collageViewOffsetX != 0) {
            collageViewOffsetX = 0;
            collageViewOffsetY = 0;
            if (collageLayoutView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    collageLayoutView.invalidateOutline();
                } else {
                    collageLayoutView.invalidate();
                }
            }
            if (cameraIcon != null) {
                cameraIcon.invalidate();
            }
        }

        collageViewLocation[0] = dp(-400);
        collageViewLocation[1] = 0;

        applyCameraViewPosition();
    }

    private void applyCameraViewPosition() {
        if (cameraView != null) {
            if (!cameraOpened) {
                collageLayoutView.setTranslationX(collageViewLocation[0]);
                collageLayoutView.setTranslationY(collageViewLocation[1] + currentPanTranslationY);
            }
            cameraIcon.setTranslationX(collageViewLocation[0]);
            cameraIcon.setTranslationY(collageViewLocation[1] + collageViewOffsetY + currentPanTranslationY);
            int finalWidth = itemSize;
            int finalHeight = itemSize;

            LayoutParams layoutParams;
            if (!cameraOpened) {
                layoutParams = (LayoutParams) collageLayoutView.getLayoutParams();
                if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                    layoutParams.width = finalWidth;
                    layoutParams.height = finalHeight;
                    collageLayoutView.setLayoutParams(layoutParams);
                    final LayoutParams layoutParamsFinal = layoutParams;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (collageLayoutView != null) {
                            collageLayoutView.setLayoutParams(layoutParamsFinal);
                        }
                    });
                }
            }

            finalWidth = (int) (itemSize - collageViewOffsetX);
            finalHeight = (int) (itemSize - collageViewOffsetY - collageViewOffsetBottomY);

            layoutParams = (LayoutParams) cameraIcon.getLayoutParams();
            if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                layoutParams.width = finalWidth;
                layoutParams.height = finalHeight;
                cameraIcon.setLayoutParams(layoutParams);
                final LayoutParams layoutParamsFinal = layoutParams;
                AndroidUtilities.runOnUIThread(() -> {
                    if (cameraIcon != null) {
                        cameraIcon.setLayoutParams(layoutParamsFinal);
                    }
                });
            }
        }
    }

    public HashMap<Object, Object> getSelectedPhotos() {
        return selectedPhotos;
    }

    public ArrayList<Object> getSelectedPhotosOrder() {
        return selectedPhotosOrder;
    }

    public void updateSelected(HashMap<Object, Object> newSelectedPhotos, ArrayList<Object> newPhotosOrder, boolean updateLayout) {
        selectedPhotos.clear();
        selectedPhotos.putAll(newSelectedPhotos);
        selectedPhotosOrder.clear();
        selectedPhotosOrder.addAll(newPhotosOrder);
        if (updateLayout) {
            updatePhotosCounter(false);
            updateCheckedPhotoIndices();

            final int count = gridView.getChildCount();
            for (int i = 0; i < count; ++i) {
                View child = gridView.getChildAt(i);
                if (child instanceof PhotoAttachPhotoCell) {
                    int position = gridView.getChildAdapterPosition(child);
                    if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                        position--;
                    }

                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) child;
                    if (parentAlert.avatarPicker != 0) {
                        cell.getCheckBox().setVisibility(GONE);
                    }
                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    if (photoEntry != null) {
                        cell.setPhotoEntry(photoEntry, selectedPhotos.size() > 1, adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry, position == adapter.getItemCount() - 1);
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                        } else {
                            cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
                        }
                    }
                }
            }
        }
    }

    private boolean isNoGalleryPermissions() {
        Activity activity = AndroidUtilities.findActivity(getContext());
        if (activity == null) {
            activity = parentAlert.baseFragment.getParentActivity();
        }
        return Build.VERSION.SDK_INT >= 23 && (
            activity == null ||
            Build.VERSION.SDK_INT >= 33 && (
                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                            activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
            ) ||
            Build.VERSION.SDK_INT < 33 && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        );
    }

    public void checkStorage() {
        if (noGalleryPermissions && Build.VERSION.SDK_INT >= 23) {
            final Activity activity = parentAlert.baseFragment.getParentActivity();

            noGalleryPermissions = isNoGalleryPermissions();
            if (!noGalleryPermissions) {
                loadGalleryPhotos();
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void scrollToTop() {
        gridView.smoothScrollToPosition(0);
    }

    @Override
    public int needsActionBar() {
        return 1;
    }

    @Override
    public void onMenuItemClick(int id) {
        if (id == caption) {
            parentAlert.setCaptionAbove(!parentAlert.captionAbove);
            captionItem.setState(!parentAlert.captionAbove, true);
            return;
        }
        if (id == group || id == compress) {
            if (parentAlert.maxSelectedPhotos > 0 && selectedPhotosOrder.size() > 1) {
                TLRPC.Chat chat = parentAlert.getChat();
                if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                    AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString(R.string.Slowmode), LocaleController.getString(R.string.SlowmodeSendError), resourcesProvider).show();
                    return;
                }
            }
        }
        if (id == group) {
            if (parentAlert.editingMessageObject == null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) parentAlert.baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(7, false, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), false);
                }, resourcesProvider);
            } else {
                parentAlert.applyCaption();
                parentAlert.delegate.didPressedButton(7, false, true, 0, 0, parentAlert.isCaptionAbove(), false);
            }
        } else if (id == compress) {
            if (parentAlert.editingMessageObject == null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) parentAlert.baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(4, true, notify, scheduleDate, 0, parentAlert.isCaptionAbove(), false);
                }, resourcesProvider);
            } else {
                parentAlert.applyCaption();
                parentAlert.delegate.didPressedButton(4, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
            }
        } else if (id == spoiler) {
            if (parentAlert.getPhotoPreviewLayout() != null) {
                parentAlert.getPhotoPreviewLayout().startMediaCrossfade();
            }

            boolean spoilersEnabled = false;
            for (Map.Entry<Object, Object> en : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry entry = (MediaController.PhotoEntry) en.getValue();
                if (entry.hasSpoiler) {
                    spoilersEnabled = true;
                    break;
                }
            }
            spoilersEnabled = !spoilersEnabled;
            boolean finalSpoilersEnabled = spoilersEnabled;
            AndroidUtilities.runOnUIThread(()-> {
                spoilerItem.setText(LocaleController.getString(finalSpoilersEnabled ? R.string.DisablePhotoSpoiler : R.string.EnablePhotoSpoiler));
                if (finalSpoilersEnabled) {
                    spoilerItem.setIcon(R.drawable.msg_spoiler_off);
                } else {
                    spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
                }
                if (finalSpoilersEnabled) {
                    parentAlert.selectedMenuItem.hideSubItem(compress);
                    if (getSelectedItemsCount() <= 1) {
                        parentAlert.selectedMenuItem.hideSubItem(media_gap);
                    }
                } else {
                    parentAlert.selectedMenuItem.showSubItem(compress);
                    if (getSelectedItemsCount() <= 1) {
                        parentAlert.selectedMenuItem.showSubItem(media_gap);
                    }
                }
            }, 200);

            List<Integer> selectedIds = new ArrayList<>();
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                if (entry.getValue() instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                    photoEntry.hasSpoiler = spoilersEnabled;
                    photoEntry.isChatPreviewSpoilerRevealed = false;
                    photoEntry.isAttachSpoilerRevealed = false;
                    selectedIds.add(photoEntry.imageId);
                }
            }

            gridView.forAllChild(view -> {
                if (view instanceof PhotoAttachPhotoCell) {
                    MediaController.PhotoEntry entry = ((PhotoAttachPhotoCell) view).getPhotoEntry();
                    ((PhotoAttachPhotoCell) view).setHasSpoiler(entry != null && selectedIds.contains(entry.imageId) && finalSpoilersEnabled);
                }
            });
            if (parentAlert.getCurrentAttachLayout() != this) {
                adapter.notifyDataSetChanged();
            }

            if (parentAlert.getPhotoPreviewLayout() != null) {
                parentAlert.getPhotoPreviewLayout().invalidateGroupsView();
            }
        } else if (id == open_in) {
            try {
                if (parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2) {
                    Intent videoPickerIntent = new Intent();
                    videoPickerIntent.setType("video/*");
                    videoPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
                    videoPickerIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, FileLoader.DEFAULT_MAX_FILE_SIZE);

                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    Intent chooserIntent = Intent.createChooser(photoPickerIntent, null);
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{videoPickerIntent});

                    if (parentAlert.avatarPicker != 0) {
                        parentAlert.baseFragment.startActivityForResult(chooserIntent, 14);
                    } else {
                        parentAlert.baseFragment.startActivityForResult(chooserIntent, 1);
                    }
                } else {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    if (parentAlert.avatarPicker != 0) {
                        parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 14);
                    } else {
                        parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 1);
                    }
                }
                parentAlert.dismiss(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (id == preview) {
            parentAlert.updatePhotoPreview(parentAlert.getCurrentAttachLayout() != parentAlert.getPhotoPreviewLayout());
        } else if (id == stars) {
            StarsIntroActivity.showMediaPriceSheet(getContext(), getStarsPrice(), true, (price, done) -> {
                done.run();
                setStarsPrice(price);
            }, resourcesProvider);
        } else if (id >= 10) {
            selectedAlbumEntry = dropDownAlbums.get(id - 10);
            if (selectedAlbumEntry == galleryAlbumEntry) {
                dropDown.setText(LocaleController.getString(R.string.ChatGallery));
            } else {
                dropDown.setText(selectedAlbumEntry.bucketName);
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
            layoutManager.scrollToPositionWithOffset(0, -gridView.getPaddingTop() + dp(7));
        }
    }

    @Override
    public int getSelectedItemsCount() {
        return selectedPhotosOrder.size();
    }

    @Override
    public void onSelectedItemsCountChanged(int count) {
        final boolean hasCompress;
        final boolean hasGroup;
        if (count <= 1 || parentAlert.editingMessageObject != null) {
            hasGroup = false;
            parentAlert.selectedMenuItem.hideSubItem(group);
            if (count == 0) {
                parentAlert.selectedMenuItem.showSubItem(open_in);
                hasCompress = false;
                parentAlert.selectedMenuItem.hideSubItem(compress);
            } else if (documentsEnabled && getStarsPrice() <= 0 && parentAlert.editingMessageObject == null) {
                hasCompress = true;
                parentAlert.selectedMenuItem.showSubItem(compress);
            } else {
                hasCompress = false;
                parentAlert.selectedMenuItem.hideSubItem(compress);
            }
        } else {
            if (getStarsPrice() <= 0) {
                hasGroup = true;
                parentAlert.selectedMenuItem.showSubItem(group);
            } else {
                hasGroup = false;
                parentAlert.selectedMenuItem.hideSubItem(group);
            }
            if (documentsEnabled && getStarsPrice() <= 0) {
                hasCompress = true;
                parentAlert.selectedMenuItem.showSubItem(compress);
            } else {
                hasCompress = false;
                parentAlert.selectedMenuItem.hideSubItem(compress);
            }
        }
        if (count != 0) {
            parentAlert.selectedMenuItem.hideSubItem(open_in);
        }
        if (count > 1) {
            parentAlert.selectedMenuItem.showSubItem(preview_gap);
            parentAlert.selectedMenuItem.showSubItem(preview);
            compressItem.setText(LocaleController.getString(R.string.SendAsFiles));
        } else {
            parentAlert.selectedMenuItem.hideSubItem(preview_gap);
            parentAlert.selectedMenuItem.hideSubItem(preview);
            if (count != 0) {
                compressItem.setText(LocaleController.getString(R.string.SendAsFile));
            }
        }
        final boolean hasSpoiler = count > 0 && getStarsPrice() <= 0 && (parentAlert == null || parentAlert.baseFragment instanceof ChatActivity && !((ChatActivity) parentAlert.baseFragment).isSecretChat());
        final boolean hasCaption = count > 0 && parentAlert != null && parentAlert.hasCaption() && parentAlert.baseFragment instanceof ChatActivity;
        final boolean hasStars = count > 0 && (parentAlert != null && parentAlert.baseFragment instanceof ChatActivity && ChatObject.isChannelAndNotMegaGroup(((ChatActivity) parentAlert.baseFragment).getCurrentChat()) && ((ChatActivity) parentAlert.baseFragment).getCurrentChatInfo() != null && ((ChatActivity) parentAlert.baseFragment).getCurrentChatInfo().paid_media_allowed);
        if (!hasSpoiler) {
            spoilerItem.setText(LocaleController.getString(R.string.EnablePhotoSpoiler));
            spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
            parentAlert.selectedMenuItem.hideSubItem(spoiler);
        } else if (parentAlert != null) {
            parentAlert.selectedMenuItem.showSubItem(spoiler);
        }
        if (hasCaption) {
            captionItem.setVisibility(View.VISIBLE);
        } else {
            captionItem.setVisibility(View.GONE);
        }
        if ((hasSpoiler || hasCaption) && (hasCompress || hasGroup)) {
            parentAlert.selectedMenuItem.showSubItem(media_gap);
        } else {
            parentAlert.selectedMenuItem.hideSubItem(media_gap);
        }
        if (hasStars) {
            updateStarsItem();
            updatePhotoStarsPrice();
            parentAlert.selectedMenuItem.showSubItem(stars);
        } else {
            parentAlert.selectedMenuItem.hideSubItem(stars);
        }
    }

    private void updateStarsItem() {
        if (starsItem == null) return;
        long amount = getStarsPrice();
        if (amount > 0) {
            starsItem.setText(getString(R.string.PaidMediaPriceButton));
            starsItem.setSubtext(formatPluralString("Stars", (int) amount));
        } else {
            starsItem.setText(getString(R.string.PaidMediaButton));
            starsItem.setSubtext(null);
        }
    }

    @Override
    public void applyCaption(CharSequence text) {
        for (int a = 0; a < selectedPhotosOrder.size(); a++) {
            if (a == 0) {
                final Object key = selectedPhotosOrder.get(a);
                Object o = selectedPhotos.get(key);
                if (o instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                    photoEntry1 = photoEntry1.clone();
                    CharSequence[] caption = new CharSequence[] { text };
                    photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(caption, false);
                    photoEntry1.caption = caption[0];
                    o = photoEntry1;
                } else if (o instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                    photoEntry1 = photoEntry1.clone();
                    CharSequence[] caption = new CharSequence[] { text };
                    photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(caption, false);
                    photoEntry1.caption = caption[0];
                    o = photoEntry1;
                }
                selectedPhotos.put(key, o);
            }
        }
    }

    public boolean captionForAllMedia() {
        int captionCount = 0;
        for (int a = 0; a < selectedPhotosOrder.size(); a++) {
            Object o = selectedPhotos.get(selectedPhotosOrder.get(a));
            CharSequence caption = null;
            if (o instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                caption = photoEntry1.caption;
            } else if (o instanceof MediaController.SearchImage) {
                MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                caption = photoEntry1.caption;
            }
            if (!TextUtils.isEmpty(caption)) {
                captionCount++;
            }
        }
        return captionCount <= 1;
    }

    @Override
    public void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.cameraInitied);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
    }

    @Override
    public void onPause() {
        if (!requestingPermissions) {
            if (cameraView != null) {
                resetRecordState();
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
            }
            if (cameraOpened) {
                closeCamera(false);
            }
            hideCamera(true);
        } else {
            requestingPermissions = false;
        }
    }

    @Override
    public void onResume() {
        if (parentAlert.isShowing() && !parentAlert.isDismissed() && !PhotoViewer.getInstance().isVisible()) {
            checkCamera(false);
            if (recordControl != null) {
                recordControl.updateGalleryImage(false);
            }
        }
    }

    @Override
    public int getListTopPadding() {
        return gridView.getPaddingTop();
    }

    public int currentItemTop = 0;

    @Override
    public int getCurrentItemTop() {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(currentItemTop = gridView.getPaddingTop());
            progressView.setTranslationY(0);
            return Integer.MAX_VALUE;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = dp(7);
        if (top >= dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        progressView.setTranslationY(newOffset + (getMeasuredHeight() - newOffset - dp(50) - progressView.getMeasuredHeight()) / 2);
        gridView.setTopGlowOffset(newOffset);
        return currentItemTop = newOffset;
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + dp(56);
    }

    @Override
    public void checkColors() {
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
        int textColor = forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_dialogTextBlack;
        Theme.setDrawableColor(cameraDrawable, getThemedColor(Theme.key_dialogCameraIcon));
        progressView.setTextColor(getThemedColor(Theme.key_emptyListPlaceholder));
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(0);
        if (holder != null && holder.itemView instanceof PhotoAttachCameraCell) {
            ((PhotoAttachCameraCell) holder.itemView).getImageView().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
        }

        dropDown.setTextColor(getThemedColor(textColor));
        dropDownContainer.setPopupItemsColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_actionBarDefaultSubmenuItem), false);
        dropDownContainer.setPopupItemsColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_actionBarItems :Theme.key_actionBarDefaultSubmenuItem), true);
        dropDownContainer.redrawPopup(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_actionBarUnscrolled : Theme.key_actionBarDefaultSubmenuBackground));
        Theme.setDrawableColor(dropDownDrawable, getThemedColor(textColor));
    }

    @Override
    public void onInit(boolean hasVideo, boolean hasPhoto, boolean hasDocuments) {
        mediaEnabled = hasVideo || hasPhoto;
        videoEnabled = hasVideo;
        photoEnabled = hasPhoto;
        documentsEnabled = hasDocuments;
        if (collageLayoutView != null) {
            collageLayoutView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            collageLayoutView.setEnabled(mediaEnabled);
        }
        if (cameraIcon != null) {
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
        }
        if ((parentAlert.baseFragment instanceof ChatActivity || parentAlert.getChat() != null) && parentAlert.avatarPicker == 0) {
            galleryAlbumEntry = MediaController.allMediaAlbumEntry;
            if (mediaEnabled) {
                progressView.setText(LocaleController.getString(R.string.NoPhotos));
                progressView.setLottie(0, 0, 0);
            } else {
                TLRPC.Chat chat = parentAlert.getChat();
                progressView.setLottie(R.raw.media_forbidden, 150, 150);
                if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) {
                    progressView.setText(LocaleController.getString(R.string.GlobalAttachMediaRestricted));
                } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                    progressView.setText(LocaleController.formatString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
                } else {
                    progressView.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                }
            }
        } else {
            if (shouldLoadAllMedia()) {
                galleryAlbumEntry = MediaController.allMediaAlbumEntry;
            } else {
                galleryAlbumEntry = MediaController.allPhotosAlbumEntry;
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            noGalleryPermissions = isNoGalleryPermissions();
        }
        if (galleryAlbumEntry != null) {
            for (int a = 0; a < Math.min(100, galleryAlbumEntry.photos.size()); a++) {
                MediaController.PhotoEntry photoEntry = galleryAlbumEntry.photos.get(a);
                photoEntry.reset();
            }
        }
        clearSelectedPhotos();
        updatePhotosCounter(false);
        cameraPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        layoutManager.scrollToPositionWithOffset(0, 1000000);

        dropDown.setText(LocaleController.getString(R.string.ChatGallery));

        selectedAlbumEntry = galleryAlbumEntry;
        if (selectedAlbumEntry != null) {
            loading = false;
            if (progressView != null) {
                progressView.showTextView();
            }
        }
        updateAlbumsDropDown();
    }

    @Override
    public boolean canScheduleMessages() {
        boolean hasTtl = false;
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            Object object = entry.getValue();
            if (object instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                if (photoEntry.ttl != 0) {
                    hasTtl = true;
                    break;
                }
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                if (searchImage.ttl != 0) {
                    hasTtl = true;
                    break;
                }
            }
        }
        if (hasTtl) {
            return false;
        }
        return true;
    }

    @Override
    public void onButtonsTranslationYUpdated() {
        checkCameraViewPosition();
        invalidate();
    }

    @Override
    public void setTranslationY(float translationY) {
        if (parentAlert.getSheetAnimationType() == 1) {
            float scale = -0.1f * (translationY / 40.0f);
            for (int a = 0, N = gridView.getChildCount(); a < N; a++) {
                View child = gridView.getChildAt(a);
                if (child instanceof PhotoAttachCameraCell) {
                    PhotoAttachCameraCell cell = (PhotoAttachCameraCell) child;
                    cell.getImageView().setScaleX(1.0f + scale);
                    cell.getImageView().setScaleY(1.0f + scale);
                } else if (child instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) child;
                    cell.getCheckBox().setScaleX(1.0f + scale);
                    cell.getCheckBox().setScaleY(1.0f + scale);
                }
            }
        }
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
        invalidate();
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    private ViewPropertyAnimator headerAnimator;

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        dropDownContainer.setVisibility(VISIBLE);
        if (!(previousLayout instanceof ChatAttachAlertPhotoLayoutPreview)) {
            clearSelectedPhotos();
            dropDown.setAlpha(1);
        } else {
            headerAnimator = dropDown.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            headerAnimator.start();
        }
        parentAlert.actionBar.setTitle("");

        layoutManager.scrollToPositionWithOffset(0, 0);
        if (previousLayout instanceof ChatAttachAlertPhotoLayoutPreview) {
            Runnable setScrollY = () -> {
                int currentItemTop = previousLayout.getCurrentItemTop(),
                        paddingTop = previousLayout.getListTopPadding();
                gridView.scrollBy(0, (currentItemTop > dp(8) ? paddingTop - currentItemTop : paddingTop));
            };
            gridView.post(setScrollY);
        }

        checkCameraViewPosition();

        resumeCameraPreview();
    }

    @Override
    public void onShown() {
        isHidden = false;
        if (collageLayoutView != null) {
            collageLayoutView.setVisibility(VISIBLE);
        }
        if (cameraIcon != null) {
            cameraIcon.setVisibility(VISIBLE);
        }
        if (cameraView != null) {
            int count = gridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = gridView.getChildAt(a);
                if (child instanceof PhotoAttachCameraCell) {
                    child.setVisibility(View.INVISIBLE);
                    break;
                }
            }
        }
        if (checkCameraWhenShown) {
            checkCameraWhenShown = false;
            checkCamera(true);
        }
    }

    public void setCheckCameraWhenShown(boolean checkCameraWhenShown) {
        this.checkCameraWhenShown = checkCameraWhenShown;
    }

    @Override
    public void onHideShowProgress(float progress) {
        if (collageLayoutView != null) {
            collageLayoutView.setAlpha(progress);
            cameraIcon.setAlpha(progress);
            if (progress != 0 && collageLayoutView.getVisibility() != VISIBLE) {
                collageLayoutView.setVisibility(VISIBLE);
                cameraIcon.setVisibility(VISIBLE);
            } else if (progress == 0 && collageLayoutView.getVisibility() != INVISIBLE) {
                collageLayoutView.setVisibility(INVISIBLE);
                cameraIcon.setVisibility(INVISIBLE);
            }
        }
    }

    @Override
    public void onHide() {
        isHidden = true;
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                PhotoAttachCameraCell cell = (PhotoAttachCameraCell) child;
                child.setVisibility(View.VISIBLE);
                saveLastCameraBitmap();
                cell.updateBitmap();
                break;
            }
        }

        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        headerAnimator = dropDown.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH).withEndAction(() -> dropDownContainer.setVisibility(GONE));
        headerAnimator.start();

        pauseCameraPreview();
    }

    private void pauseCameraPreview() {
        try {
            if (cameraView != null) {
                CameraController.getInstance().stopPreview(cameraView.getCameraSessionObject());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void resumeCameraPreview() {
        try {
            checkCamera(false);
            if (cameraView != null) {
                CameraController.getInstance().startPreview(cameraView.getCameraSessionObject());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void onPhotoEditModeChanged(boolean isEditMode) {
//        if (needCamera && !noCameraPermissions) {
//            if (isEditMode) {
//                if (cameraView != null) {
//                    isCameraFrontfaceBeforeEnteringEditMode = cameraView.isFrontface();
//                    hideCamera(true);
//                }
//            } else {
//                afterCameraInitRunnable = () -> {
//                    pauseCameraPreview();
//                    afterCameraInitRunnable = null;
//                    isCameraFrontfaceBeforeEnteringEditMode = null;
//                };
//                showCamera();
//            }
//        }
    }

    public void pauseCamera(boolean pause) {
        if (needCamera && !noCameraPermissions) {
            if (pause) {
                if (cameraView != null) {
                    isCameraFrontfaceBeforeEnteringEditMode = cameraView.isFrontface();
                    hideCamera(true);
                }
            } else {
//                afterCameraInitRunnable = () -> {
//                    pauseCameraPreview();
//                    afterCameraInitRunnable = null;
//                    isCameraFrontfaceBeforeEnteringEditMode = null;
//                };
                showCamera();
            }
        }
    }

    @Override
    public void onHidden() {
        if (collageLayoutView != null) {
            collageLayoutView.setVisibility(GONE);
            cameraIcon.setVisibility(GONE);
        }
        for (Map.Entry<Object, Object> en : selectedPhotos.entrySet()) {
            if (en.getValue() instanceof MediaController.PhotoEntry) {
                ((MediaController.PhotoEntry) en.getValue()).isAttachSpoilerRevealed = false;
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (lastNotifyWidth != right - left) {
            lastNotifyWidth = right - left;
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
        super.onLayout(changed, left, top, right, bottom);
        checkCameraViewPosition();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        ignoreLayout = true;
        if (AndroidUtilities.isTablet()) {
            itemsPerRow = 4;
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            itemsPerRow = 4;
        } else {
            itemsPerRow = 3;
        }
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

        itemSize = (availableWidth - dp(6 * 2) - dp(5 * 2)) / itemsPerRow;

        if (lastItemSize != itemSize) {
            lastItemSize = itemSize;
            AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
        }

        layoutManager.setSpanCount(Math.max(1, itemSize * itemsPerRow + dp(5) * (itemsPerRow - 1)));
        int rows = (int) Math.ceil((adapter.getItemCount() - 1) / (float) itemsPerRow);
        int contentSize = rows * itemSize + (rows - 1) * dp(5);
        int newSize = Math.max(0, availableHeight - contentSize - ActionBar.getCurrentActionBarHeight() - dp(48 + 12));
        if (gridExtraSpace != newSize) {
            gridExtraSpace = newSize;
            adapter.notifyDataSetChanged();
        }
        int paddingTop;
        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            paddingTop = (int) (availableHeight / 3.5f);
        } else {
            paddingTop = (availableHeight / 5 * 2);
        }
        paddingTop -= dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }
        if (gridView.getPaddingTop() != paddingTop) {
            gridView.setPadding(dp(6), paddingTop, dp(6), dp(48));
        }
        dropDown.setTextSize(!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 18 : 20);
        ignoreLayout = false;
    }

    @Override
    public boolean canDismissWithTouchOutside() {
        return !cameraOpened;
    }

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        super.onPanTransitionStart(keyboardVisible, contentHeight);
        checkCameraViewPosition();
        if (collageLayoutView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                collageLayoutView.invalidateOutline();
            } else {
                collageLayoutView.invalidate();
            }
        }
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
    }

    @Override
    public void onContainerTranslationUpdated(float currentPanTranslationY) {
        this.currentPanTranslationY = currentPanTranslationY;
        checkCameraViewPosition();
        if (collageLayoutView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                collageLayoutView.invalidateOutline();
            } else {
                collageLayoutView.invalidate();
            }
        }
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
        invalidate();
    }

    @Override
    public void onOpenAnimationEnd() {
        checkCamera(parentAlert != null && parentAlert.baseFragment instanceof ChatActivity);
    }

    @Override
    public void onDismissWithButtonClick(int item) {
        hideCamera(item != 0 && item != 2);
    }

    @Override
    public boolean onDismiss() {
        if (cameraAnimationInProgress) {
            return true;
        }
        if (cameraOpened) {
            closeCamera(true);
            return true;
        }
        hideCamera(true);
        return false;
    }

    private void animatedCloseCamera() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(controlContainer, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(navbarContainer, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(actionBarContainer, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(videoTimerView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));
        animatorSet.setDuration(220);
        animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorSet.start();
    }

    private void animatedOpenCamera() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(collageLayoutView, View.TRANSLATION_Y, 0.0f),
                ObjectAnimator.ofFloat(controlContainer, View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(navbarContainer, View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(actionBarContainer, View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(videoTimerView, View.ALPHA, videoTimerShown ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
        animatorSet.setDuration(250);
        animatorSet.setInterpolator(interpolator);
        animatorSet.start();
    }

    private int[] viewPosition = new int[2];

    private boolean processTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }

        if (takingVideo || takingPhoto) {
            return true;
        }

        if (recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch() || inCheck()) {
            return true;
        }

        if (!pressed && (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
            if (!takingPhoto && !dragging) {
                if (event.getPointerCount() != 2) {
                    maybeStartDraging = true;
                    lastY = event.getY();
                    if (collageLayoutView != null) {
                        collageLayoutView.setTag(null);
                    }
                }
                pressed = true;
            }
        } else if (pressed) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (event.getPointerCount() != 2 || dragging) {
                    float newY = event.getY();
                    float dy = (newY - lastY);
                    if (maybeStartDraging) {
                        if (Math.abs(dy) > AndroidUtilities.getPixelsInCM(0.4f, false)) {
                            maybeStartDraging = false;
                            dragging = true;
                        }
                    } else if (dragging) {
                        if (collageLayoutView != null) {
                            collageLayoutView.setTranslationY(collageLayoutView.getTranslationY() + dy);
                            lastY = newY;
                            zoomControlView.setTag(null);
                            if (zoomControlHideRunnable != null) {
                                AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                                zoomControlHideRunnable = null;
                            }
                            if (collageLayoutView.getTag() == null &&
                                    Math.abs(collageLayoutView.getTranslationY()) > AndroidUtilities.dp(20)) {
                                collageLayoutView.setTag(1);
                                animatedCloseCamera();
                            }
                        }
                    }
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                pressed = false;
                if (dragging) {
                    dragging = false;
                    if (collageLayoutView != null) {
                        if (Math.abs(collageLayoutView.getTranslationY()) > collageLayoutView.getMeasuredHeight() / 6.0f) {
                            closeCamera(true);
                        } else {
                            animatedOpenCamera();
                            collageLayoutView.setTag(null);
                        }
                    }
                } else if (cameraView != null) {
                    cameraView.getLocationOnScreen(viewPosition);
                    float viewX = event.getRawX() - viewPosition[0];
                    float viewY = event.getRawY() - viewPosition[1];
                    //cameraView.focusToPoint((int) viewX, (int) viewY);
                }
                maybeStartDraging = false;
                if (collageLayoutView != null) {
                    collageLayoutView.setTag(null);
                }
            }
        }

        return true;
    }

    private boolean flingDetected;
    private boolean touchInCollageList;

    @Override
    public boolean onContainerViewTouchEvent(MotionEvent event) {
        if (cameraAnimationInProgress) {
            return true;
        }
        if (cameraOpened) {
            cameraView.touchEvent(event);
            collageLayoutView.touchEvent(event);

            flingDetected = false;
            if (collageListView != null && collageListView.isVisible()) {
                final float y = parentAlert.getContainer().getY() + actionBarContainer.getY() + collageListView.getY();
                if (event.getY() >= y && event.getY() <= y + collageListView.getHeight() || touchInCollageList) {
                    touchInCollageList = event.getAction() != MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_CANCEL;
                    return processTouchEvent(event);
                } else {
                    collageListView.setVisible(false, true);
                    updateActionBarButtons(true);
                }
            }
            if (touchInCollageList && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                touchInCollageList = false;
            }

            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && !flingDetected) {
                allowModeScroll = true;
                modeSwitcherView.stopScroll(0);
                scrollingY = false;
                scrollingX = false;
            }

            return processTouchEvent(event);
        }
        return false;
    }

    public void cancelGestures() {
        scaleGestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
        gestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
    }

    private boolean scaling = false;
    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!scaling || cameraView == null || cameraView.isDualTouch() || collageLayoutView.getFilledProgress() >= 1) {
                return false;
            }
            final float deltaScaleFactor = (detector.getScaleFactor() - 1.0f) * .75f;
            cameraZoom += deltaScaleFactor;
            cameraZoom = Utilities.clamp(cameraZoom, 1, 0);
            cameraView.setZoom(cameraZoom);
            if (zoomControlView != null) {
                zoomControlView.setZoom(cameraZoom, false);
            }
            showZoomControls(true, true);
            return true;
        }

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            if (cameraView == null) {
                return false;
            }
            scaling = true;
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            scaling = false;

            //animateContainerBack();
            super.onScaleEnd(detector);
        }
    }

    private float ty, sty, stx;
    private boolean allowModeScroll = true;

    private final class GestureListener extends GestureDetectorFixDoubleTap.OnGestureListener {
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            sty = 0;
            stx = 0;
            return false;
        }

        @Override
        public void onShowPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            scrollingY = false;
            scrollingX = false;
            if (!hasDoubleTap(e)) {
                if (onSingleTapConfirmed(e)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (cameraAnimationInProgress || recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch() || inCheck()) {
                return false;
            }
            if (takingVideo || takingPhoto) {
                return false;
            }
            if (!scrollingX) {
                sty += distanceY;
                if (!scrollingY && Math.abs(sty) >= touchSlop) {
                    if (collageLayoutView != null) {
                        collageLayoutView.cancelTouch();
                    }
                    scrollingY = true;
                }
            }
            if (!scrollingY) {
                stx += distanceX;
                if (!scrollingX && Math.abs(stx) >= touchSlop) {
                    if (collageLayoutView != null) {
                        collageLayoutView.cancelTouch();
                    }
                    scrollingX = true;
                }
            }
            if (scrollingX) {
                modeSwitcherView.scrollX(distanceX);
            }
            return true;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (cameraAnimationInProgress || recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch() || inCheck()) {
                return false;
            }
            flingDetected = true;
            allowModeScroll = true;
            boolean r = false;
            if (scrollingX) {
                r = modeSwitcherView.stopScroll(velocityX) || r;
            }
            scrollingY = false;
            scrollingX = false;
            if (r && collageLayoutView != null) {
                collageLayoutView.cancelTouch();
            }
            return r;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (cameraView != null) {
                cameraView.allowToTapFocus();
                return true;
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (cameraView == null || takingPhoto || !cameraView.isInited()) {
                return false;
            }
            cameraView.switchCamera();
            recordControl.rotateFlip(180);
            saveCameraFace(cameraView.isFrontface());
            if (useDisplayFlashlight()) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (cameraView != null) {
                cameraView.clearTapFocus();
            }
            return false;
        }

        @Override
        public boolean hasDoubleTap(MotionEvent e) {
            return cameraView != null && cameraView.isInited() && !takingPhoto && !recordControl.isTouch();
        }
    }

    @Override
    public boolean onCustomMeasure(View view, int width, int height) {
        width += parentAlert.getLeftInset() + parentAlert.getRightInset();
        height += parentAlert.getBottomInset();
        boolean isPortrait = width < height;

        if (view == cameraIcon) {
            cameraIcon.measure(View.MeasureSpec.makeMeasureSpec(itemSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) (itemSize - collageViewOffsetBottomY - collageViewOffsetY), View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == collageLayoutView) {
            if (cameraOpened && !cameraAnimationInProgress) {
                collageLayoutView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                return true;
            }
        } else if (view == flashViews.backgroundView) {
            flashViews.backgroundView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == actionBarContainer) {
            if (isPortrait) {
                actionBarContainer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(150), View.MeasureSpec.EXACTLY));
            } else {
                actionBarContainer.measure(View.MeasureSpec.makeMeasureSpec(dp(150), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        } else if (view == controlContainer) {
            if (isPortrait) {
                controlContainer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(160), View.MeasureSpec.EXACTLY));
            } else {
                controlContainer.measure(View.MeasureSpec.makeMeasureSpec(dp(160), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        } else if (view == navbarContainer) {
            navbarContainer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            cameraPhotoRecyclerViewIgnoreLayout = true;
            if (isPortrait) {
                cameraPhotoRecyclerView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(80), View.MeasureSpec.EXACTLY));
                if (cameraPhotoLayoutManager.getOrientation() != LinearLayoutManager.HORIZONTAL) {
                    cameraPhotoRecyclerView.setPadding(dp(8), 0, dp(8), 0);
                    cameraPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    cameraAttachAdapter.notifyDataSetChanged();
                }
            } else {
                cameraPhotoRecyclerView.measure(View.MeasureSpec.makeMeasureSpec(dp(80), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                if (cameraPhotoLayoutManager.getOrientation() != LinearLayoutManager.VERTICAL) {
                    cameraPhotoRecyclerView.setPadding(0, dp(8), 0, dp(8));
                    cameraPhotoLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                    cameraAttachAdapter.notifyDataSetChanged();
                }
            }
            cameraPhotoRecyclerViewIgnoreLayout = false;
            return true;
        } else if (view == zoomControlView) {
            if (isPortrait) {
                zoomControlView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY));
            } else {
                zoomControlView.measure(View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        int width = (right - left);
        int height = (bottom - top);
        width += parentAlert.getLeftInset() + parentAlert.getRightInset();
        height += parentAlert.getBottomInset();
        boolean isPortrait = width < height;

        if (view == videoTimerView) {
            int cx = width / 2;
            videoTimerView.layout(cx - (videoTimerView.getMeasuredWidth() / 2), dp(8), cx + (videoTimerView.getMeasuredWidth() / 2), dp(8 + 45));
            return true;
        } else if (view == actionBarContainer) {
            if (isPortrait) {
                actionBarContainer.layout(0, dp(8), width, dp(150 + 8));
            } else {
                actionBarContainer.layout(dp(8), 0, dp(8 + 150), height);
            }
            return true;
        } else if (view == controlContainer) {
            if (isPortrait) {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    controlContainer.layout(0, height - dp(48 + 80 + 8 + 160), width, height - dp(48 + 80 + 8));
                } else {
                    controlContainer.layout(0, height - dp(48 + 8 + 160), width, height - dp(48 + 8));
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    controlContainer.layout(width - dp(8 + 80 + 8 + 160), 0, width - dp(8 + 80 + 8), height);
                } else {
                    controlContainer.layout(width - dp(8 + 160), 0, width - dp(8), height);
                }
            }
            return true;
        } else if (view == navbarContainer) {
            navbarContainer.layout(0, height - dp(48), width, height);
            return true;
        } else if (view == counterTextView) {
            int zoomViewOffset = zoomControlView.getAlpha() == 1 ? dp(50) : 0;
            if (isPortrait) {
                int cx = width / 2;
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    counterTextView.layout(cx - (counterTextView.getMeasuredWidth() / 2), height - dp(48 + 80 + 8 + 100 + 8) - zoomViewOffset - counterTextView.getMeasuredHeight(), cx + (counterTextView.getMeasuredWidth() / 2), height - dp(48 + 80 + 8 + 100 + 8) - zoomViewOffset);
                } else {
                    counterTextView.layout(cx - (counterTextView.getMeasuredWidth() / 2), height - dp(48 + 8 + 100 + 8) - zoomViewOffset - counterTextView.getMeasuredHeight(), cx + (counterTextView.getMeasuredWidth() / 2), height - dp(48 + 8 + 100 + 8) - zoomViewOffset);
                }
            } else {
                int cy = height / 2;
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    counterTextView.layout(width - dp(8 + 80 + 8 + 100 + 8) - counterTextView.getMeasuredWidth() - zoomViewOffset, cy - (counterTextView.getMeasuredHeight() / 2), width - dp(8 + 80 + 8 + 100 + 8) - zoomViewOffset, cy + (counterTextView.getMeasuredHeight() / 2));
                } else {
                    counterTextView.layout(width - dp(8 + 100 + 8) - counterTextView.getMeasuredWidth() - zoomViewOffset, cy - (counterTextView.getMeasuredHeight() / 2), width - dp(8 + 100 + 8) - zoomViewOffset, cy + (counterTextView.getMeasuredHeight() / 2));
                }
            }
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            if (isPortrait) {
                view.layout(0, height - dp( 48 +  80), width, height - dp( 48 ));
            } else {
                view.layout(width - dp( 8 + 80), 0, width - dp( 8 ), height);
            }
            return true;
        } else if (view == zoomControlView) {
            if (isPortrait) {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    zoomControlView.layout(0, height - dp(48 + 80 + 8 + 100 + 8 + 50), width, height - dp(48 + 80 + 8 + 100 + 8));
                } else {
                    zoomControlView.layout(0, height - dp(48 + 100 + 8 + 50), width, height - dp(48 + 100 + 8));
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    zoomControlView.layout(width - dp(80 + 8 + 100 + 8 + 50), 0, width - dp(80 + 8 + 100 + 8), height);
                } else {
                    zoomControlView.layout(width - dp(100 + 8 + 50), 0, width - dp(100 + 8), height);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            if (adapter != null) {
                if (shouldLoadAllMedia()) {
                    galleryAlbumEntry = MediaController.allMediaAlbumEntry;
                } else {
                    galleryAlbumEntry = MediaController.allPhotosAlbumEntry;
                }
                if (selectedAlbumEntry == null || parentAlert != null && parentAlert.isStickerMode) {
                    selectedAlbumEntry = galleryAlbumEntry;
                } else if (shouldLoadAllMedia()) {
                    for (int a = 0; a < MediaController.allMediaAlbums.size(); a++) {
                        MediaController.AlbumEntry entry = MediaController.allMediaAlbums.get(a);
                        if (entry.bucketId == selectedAlbumEntry.bucketId && entry.videoOnly == selectedAlbumEntry.videoOnly) {
                            selectedAlbumEntry = entry;
                            break;
                        }
                    }
                }
                loading = false;
                progressView.showTextView();
                adapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
                if (!selectedPhotosOrder.isEmpty() && galleryAlbumEntry != null) {
                    for (int a = 0, N = selectedPhotosOrder.size(); a < N; a++) {
                        Integer imageId = (Integer) selectedPhotosOrder.get(a);
                        Object currentEntry = selectedPhotos.get(imageId);
                        MediaController.PhotoEntry entry = galleryAlbumEntry.photosByIds.get(imageId);
                        if (entry != null) {
                            if (currentEntry instanceof MediaController.PhotoEntry) {
                                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) currentEntry;
                                entry.copyFrom(photoEntry);
                            }
                            selectedPhotos.put(imageId, entry);
                        }
                    }
                }
                updateAlbumsDropDown();
            }

            if (recordControl != null) {
                recordControl.updateGalleryImage(false);
            }
        } else if (id == NotificationCenter.cameraInitied) {
            checkCamera(false);
        }
    }

    private class PhotoAttachAdapter extends RecyclerListView.FastScrollAdapter {

        private Context mContext;
        private boolean needCamera;
        private ArrayList<RecyclerListView.Holder> viewsCache = new ArrayList<>(8);
        private int itemsCount;
        private int photosStartRow;
        private int photosEndRow;

        public PhotoAttachAdapter(Context context, boolean camera) {
            mContext = context;
            needCamera = camera;
        }

        public void createCache() {
            for (int a = 0; a < 8; a++) {
                viewsCache.add(createHolder());
            }
        }

        public RecyclerListView.Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext, resourcesProvider);
            if (Build.VERSION.SDK_INT >= 21 && this == adapter) {
                cell.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        PhotoAttachPhotoCell photoCell = (PhotoAttachPhotoCell) view;
                        if (photoCell.getTag() == null) {
                            return;
                        }
                        int position = (Integer) photoCell.getTag();
                        if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                            position++;
                        }
                        if (showAvatarConstructor) {
                            position++;
                        }
                        if (position == 0) {
                            int rad = dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                        } else if (position == itemsPerRow - 1) {
                            int rad = dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect(-rad, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + rad, rad);
                        } else {
                            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                        }
                    }
                });
                cell.setClipToOutline(true);
            }
            cell.setDelegate(v -> {
                if (!mediaEnabled || parentAlert.avatarPicker != 0) {
                    return;
                }
                int index = (Integer) v.getTag();
                MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                if (checkSendMediaEnabled(photoEntry)) {
                    return;
                }

                if (collageLayoutView.hasLayout()) {
                    fromGallery = true;
                    outputFile = null;
                    photoEntry.videoVolume = 1.0f;
                    photoEntry.file = new File(photoEntry.path);
                    if (collageLayoutView.push(photoEntry)) {
                        outputEntry = MediaController.PhotoEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                    }
                    updateActionBarButtons(true);
                    cameraPhotoRecyclerView.setVisibility(GONE);
                    return;
                }

                if (selectedPhotos.size() + 1 > maxCount()) {
                    BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BusinessRepliesToastLimit", parentAlert.baseFragment.getMessagesController().quickReplyMessagesLimit))).show();
                    return;
                }
                boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                if (added && parentAlert.maxSelectedPhotos >= 0 && selectedPhotos.size() >= parentAlert.maxSelectedPhotos) {
                    if (parentAlert.allowOrder && parentAlert.baseFragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
                        TLRPC.Chat chat = chatActivity.getCurrentChat();
                        if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                            if (alertOnlyOnce != 2) {
                                AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString(R.string.Slowmode), LocaleController.getString(R.string.SlowmodeSelectSendError), resourcesProvider).show();
                                if (alertOnlyOnce == 1) {
                                    alertOnlyOnce = 2;
                                }
                            }
                        }
                    }
                    return;
                }
                int num = added ? selectedPhotosOrder.size() : -1;
                if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                    v.setChecked(num, added, true);
                } else {
                    v.setChecked(-1, added, true);
                }
                addToSelectedPhotos(photoEntry, index);
                int updateIndex = index;
                if (PhotoAttachAdapter.this == cameraAttachAdapter) {
                    if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                        updateIndex++;
                    }
                    adapter.notifyItemChanged(updateIndex);
                } else {
                    cameraAttachAdapter.notifyItemChanged(updateIndex);
                }
                parentAlert.updateCountButton(added ? 1 : 2);
                cell.setHasSpoiler(photoEntry.hasSpoiler);
                cell.setStarsPrice(photoEntry.starsAmount, selectedPhotos.size() > 1);
            });
            return new RecyclerListView.Holder(cell);
        }

        private MediaController.PhotoEntry getPhoto(int position) {
            if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                position--;
            }
            return getPhotoEntryAtPosition(position);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                        position--;
                    }
                    if (showAvatarConstructor) {
                        position--;
                    }
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                    if (this == adapter) {
                        cell.setItemSize(itemSize);
                    } else {
                        cell.setIsVertical(cameraPhotoLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL);
                    }
                    if (parentAlert.avatarPicker != 0 || parentAlert.storyMediaPicker || collageLayoutView.hasLayout()) {
                        cell.getCheckBox().setVisibility(GONE);
                    } else {
                        cell.getCheckBox().setVisibility(VISIBLE);
                    }

                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    if (photoEntry == null) {
                        return;
                    }
                    cell.setPhotoEntry(photoEntry, selectedPhotos.size() > 1, needCamera && selectedAlbumEntry == galleryAlbumEntry, position == getItemCount() - 1);

                    if (!collageLayoutView.hasLayout()) {
                        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                            cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                        } else {
                            cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
                        }
                    }

                    if (!videoEnabled && photoEntry.isVideo) {
                        cell.setAlpha(0.3f);
                    } else if (!photoEnabled && !photoEntry.isVideo) {
                        cell.setAlpha(0.3f);
                    } else {
                        cell.setAlpha(1f);
                    }
                    cell.getImageView().setTag(position);
                    cell.setTag(position);
                    break;
                }
                case 1: {
                    cameraCell = (PhotoAttachCameraCell) holder.itemView;
                    if (cameraView != null && cameraView.isInited() && !isHidden) {
                        cameraCell.setVisibility(View.INVISIBLE);
                    } else {
                        cameraCell.setVisibility(View.VISIBLE);
                    }
                    cameraCell.setItemSize(itemSize);
                    break;
                }
                case 3: {
                    PhotoAttachPermissionCell cell = (PhotoAttachPermissionCell) holder.itemView;
                    cell.setItemSize(itemSize);
                    cell.setType(needCamera && noCameraPermissions && position == 0 ? 0 : 1);
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerListView.Holder holder;
            switch (viewType) {
                case 0:
                    if (!viewsCache.isEmpty()) {
                        holder = viewsCache.get(0);
                        viewsCache.remove(0);
                    } else {
                        holder = createHolder();
                    }
                    break;
                case 1:
                    cameraCell = new PhotoAttachCameraCell(mContext, resourcesProvider);
                    if (Build.VERSION.SDK_INT >= 21) {
                        cameraCell.setOutlineProvider(new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                int rad = dp(8 * parentAlert.cornerRadius);
                                outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                            }
                        });
                        cameraCell.setClipToOutline(true);
                    }
                    holder = new RecyclerListView.Holder(cameraCell);
                    break;
                case 2:
                    holder = new RecyclerListView.Holder(new View(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(gridExtraSpace, MeasureSpec.EXACTLY));
                        }
                    });
                    break;
                case 3:
                default:
                    holder = new RecyclerListView.Holder(new PhotoAttachPermissionCell(mContext, resourcesProvider));
                    break;
                case 4:
                    AvatarConstructorPreviewCell avatarConstructorPreviewCell = new AvatarConstructorPreviewCell(mContext, parentAlert.forUser) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY));
                        }
                    };
                    holder = new RecyclerListView.Holder(avatarConstructorPreviewCell);
                    break;
            }
            return holder;
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof PhotoAttachCameraCell) {
                PhotoAttachCameraCell cell = (PhotoAttachCameraCell) holder.itemView;
                cell.updateBitmap();
            }
        }

        @Override
        public int getItemCount() {
            if (!mediaEnabled) {
                return 1;
            }
            int count = 0;
            if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                count++;
            }
            if (showAvatarConstructor) {
                count++;
            }
            if (noGalleryPermissions && this == adapter) {
                count++;
            }
            photosStartRow = count;
            count += cameraPhotos.size();
            if (selectedAlbumEntry != null) {
                count += selectedAlbumEntry.photos.size();
            }
            photosEndRow = count;
            if (this == adapter) {
                count++;
            }
            return itemsCount = count;
        }

        @Override
        public int getItemViewType(int position) {
            if (!mediaEnabled) {
                return 2;
            }
            int localPosition = position;
            if (needCamera && position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (noCameraPermissions) {
                    return 3;
                } else {
                    return 1;
                }
            }
            if (needCamera) {
                localPosition--;
            }
            if (showAvatarConstructor && localPosition == 0) {
                return VIEW_TYPE_AVATAR_CONSTRUCTOR;
            }
            if (this == adapter && position == itemsCount - 1) {
                return 2;
            } else if (noGalleryPermissions) {
                return 3;
            }
            return 0;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (this == adapter) {
                progressView.setVisibility(getItemCount() == 1 && selectedAlbumEntry == null || !mediaEnabled ? View.VISIBLE : View.INVISIBLE);
            }
        }

        @Override
        public float getScrollProgress(RecyclerListView listView) {
            int parentCount = itemsPerRow;
            int cellCount = (int) Math.ceil(itemsCount / (float) parentCount);
            if (listView.getChildCount() == 0) {
                return 0;
            }
            int cellHeight = listView.getChildAt(0).getMeasuredHeight();
            View firstChild = listView.getChildAt(0);
            int firstPosition = listView.getChildAdapterPosition(firstChild);
            if (firstPosition < 0) {
                return 0;
            }
            float childTop = firstChild.getTop();
            float listH = listView.getMeasuredHeight();
            float scrollY = (firstPosition / parentCount) * cellHeight - childTop;
            return Utilities.clamp(scrollY / (((float) cellCount) * cellHeight - listH), 1f, 0f);
        }

        @Override
        public String getLetter(int position) {
            MediaController.PhotoEntry entry = getPhoto(position);
            if (entry == null) {
                if (position <= photosStartRow) {
                    if (!cameraPhotos.isEmpty()) {
                        entry = (MediaController.PhotoEntry) cameraPhotos.get(0);
                    } else if (selectedAlbumEntry != null && selectedAlbumEntry.photos != null) {
                        entry = selectedAlbumEntry.photos.get(0);
                    }
                } else if (!selectedAlbumEntry.photos.isEmpty()){
                    entry = selectedAlbumEntry.photos.get(selectedAlbumEntry.photos.size() - 1);
                }
            }
            if (entry != null) {
                long date = entry.dateTaken;
                if (Build.VERSION.SDK_INT <= 28) {
                    date /= 1000;
                }
                return LocaleController.formatYearMont(date, true);
            }
            return "";
        }

        @Override
        public boolean fastScrollIsVisible(RecyclerListView listView) {
            return (!cameraPhotos.isEmpty() || selectedAlbumEntry != null && !selectedAlbumEntry.photos.isEmpty()) && parentAlert.pinnedToTop && getTotalItemsCount() > SHOW_FAST_SCROLL_MIN_COUNT;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            int viewHeight = listView.getChildAt(0).getMeasuredHeight();
            int totalHeight = (int) (Math.ceil(getTotalItemsCount() / (float) itemsPerRow) * viewHeight);
            int listHeight = listView.getMeasuredHeight();
            position[0] = (int) ((progress * (totalHeight - listHeight)) / viewHeight) * itemsPerRow;
            position[1] = (int) ((progress * (totalHeight - listHeight)) % viewHeight) + listView.getPaddingTop();
            if (position[0] == 0 && position[1] < getListTopPadding()) {
                position[1] = getListTopPadding();
            }
        }
    }
}
