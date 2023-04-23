/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
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
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPermissionCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private AnimatorSet cameraInitAnimation;
    protected CameraView cameraView;
    protected FrameLayout cameraIcon;
    protected PhotoAttachCameraCell cameraCell;
    private TextView recordTime;
    private ImageView[] flashModeButton = new ImageView[2];
    private boolean flashAnimationInProgress;
    private float[] cameraViewLocation = new float[2];
    private int[] viewPosition = new int[2];
    private float cameraViewOffsetX;
    private float cameraViewOffsetY;
    private float cameraViewOffsetBottomY;
    private boolean cameraOpened;
    private boolean canSaveCameraPreview;
    private boolean cameraAnimationInProgress;
    private float cameraOpenProgress;
    private int[] animateCameraValues = new int[5];
    private int videoRecordTime;
    private Runnable videoRecordRunnable;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private FrameLayout cameraPanel;
    private ShutterButton shutterButton;
    private ZoomControlView zoomControlView;
    private AnimatorSet zoomControlAnimation;
    private Runnable zoomControlHideRunnable;
    private TextView counterTextView;
    private TextView tooltipTextView;
    private ImageView switchCameraButton;
    private boolean takingPhoto;
    private static boolean mediaFromExternalCamera;
    private static ArrayList<Object> cameraPhotos = new ArrayList<>();
    private static HashMap<Object, Object> selectedPhotos = new HashMap<>();
    private static ArrayList<Object> selectedPhotosOrder = new ArrayList<>();
    private static int lastImageId = -1;
    private boolean cancelTakingPhotos;
    private boolean checkCameraWhenShown;

    private boolean mediaEnabled;
    private boolean videoEnabled;
    private boolean photoEnabled;
    private boolean documentsEnabled;

    private float pinchStartDistance;
    private float cameraZoom;
    private boolean zooming;
    private boolean zoomWas;
    private android.graphics.Rect hitRect = new Rect();

    private float lastY;
    private boolean pressed;
    private boolean maybeStartDraging;
    private boolean dragging;

    private boolean cameraPhotoRecyclerViewIgnoreLayout;

    private int itemSize = AndroidUtilities.dp(80);
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
    public final static int preview = 5;

    private ActionBarMenuSubItem spoilerItem;
    private ActionBarMenuSubItem compressItem;
    protected ActionBarMenuSubItem previewItem;

    boolean forceDarkTheme;
    private int animationIndex = -1;
    private boolean showAvatarConstructor;

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
                    }
                }
            });
        }
    }

    private PhotoViewer.PhotoViewerProvider photoViewerProvider = new BasePhotoProvider() {
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
                    cell.getImageView().setOrientation(photoEntry.orientation, true);
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
                if (o instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                    firstPhotoCaption = photoEntry1.caption;
                }
                if (o instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                    firstPhotoCaption = photoEntry1.caption;
                }
                parentAlert.commentTextView.setText(firstPhotoCaption);
            }
        }

        @Override
        public boolean cancelButtonPressed() {
            return false;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry != null) {
                photoEntry.editedInfo = videoEditedInfo;
            }
            if (selectedPhotos.isEmpty() && photoEntry != null) {
                addToSelectedPhotos(photoEntry, -1);
            }
            if (parentAlert.checkCaption(parentAlert.commentTextView.getText())) {
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
                                photoEntry1.caption = PhotoViewer.getInstance().captionForAllMedia;
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
            parentAlert.delegate.didPressedButton(7, true, notify, scheduleDate, forceDocument);
            selectedPhotos.clear();
            cameraPhotos.clear();
            selectedPhotosOrder.clear();
            selectedPhotos.clear();
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
        dropDown.setText(LocaleController.getString("ChatGallery", R.string.ChatGallery));
        dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
        dropDownDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        checkCamera(false);

        compressItem = parentAlert.selectedMenuItem.addSubItem(compress, R.drawable.msg_filehq, LocaleController.getString("SendWithoutCompression", R.string.SendWithoutCompression));
        parentAlert.selectedMenuItem.addSubItem(group, R.drawable.msg_ungroup, LocaleController.getString("SendWithoutGrouping", R.string.SendWithoutGrouping));
        spoilerItem = parentAlert.selectedMenuItem.addSubItem(spoiler, R.drawable.msg_spoiler, LocaleController.getString("EnablePhotoSpoiler", R.string.EnablePhotoSpoiler));
        parentAlert.selectedMenuItem.addSubItem(open_in, R.drawable.msg_openin, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp));
        parentAlert.selectedMenuItem.addColoredGap(preview_gap);
        previewItem = parentAlert.selectedMenuItem.addSubItem(preview, R.drawable.msg_view_file, LocaleController.getString("AttachMediaPreviewButton", R.string.AttachMediaPreviewButton));
        parentAlert.selectedMenuItem.setFitSubItems(true);

        gridView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - AndroidUtilities.dp(80)) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < parentAlert.scrollOffsetY[0] - AndroidUtilities.dp(80)) {
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
                    int offset = AndroidUtilities.dp(13) + (parentAlert.selectedMenuItem != null ? AndroidUtilities.dp(parentAlert.selectedMenuItem.getAlpha() * 26) : 0);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(7)) {
                            gridView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(7));
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
                        dy -= (gridView.getPaddingTop() - AndroidUtilities.dp(7));
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
                return itemSize + (position % itemsPerRow != itemsPerRow - 1 ? AndroidUtilities.dp(5) : 0);
            }
        });
        gridView.setLayoutManager(layoutManager);
        gridView.setOnItemClickListener((view, position, x, y) -> {
            if (!mediaEnabled || parentAlert.baseFragment == null || parentAlert.baseFragment.getParentActivity() == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                if (adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry && position == 0 && noCameraPermissions) {
                    try {
                        parentAlert.baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 18);
                    } catch (Exception ignore) {

                    }
                    return;
                } else if (noGalleryPermissions) {
                    try {
                        parentAlert.baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                    } catch (Exception ignore) {

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
                PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment, resourcesProvider);
                PhotoViewer.getInstance().setParentAlert(parentAlert);
                PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
                ChatActivity chatActivity;
                int type;
                if (parentAlert.avatarPicker != 0) {
                    chatActivity = null;
                    type = PhotoViewer.SELECT_TYPE_AVATAR;
                } else if (parentAlert.baseFragment instanceof ChatActivity) {
                    chatActivity = (ChatActivity) parentAlert.baseFragment;
                    type = 0;
                } else {
                    chatActivity = null;
                    type = 4;
                }
                if (!parentAlert.delegate.needEnterComment()) {
                    AndroidUtilities.hideKeyboard(parentAlert.baseFragment.getFragmentView().findFocus());
                    AndroidUtilities.hideKeyboard(parentAlert.getContainer().findFocus());
                }
                if (selectedPhotos.size() > 0 && selectedPhotosOrder.size() > 0) {
                    Object o = selectedPhotos.get(selectedPhotosOrder.get(0));
                    if (o instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                        photoEntry1.caption = parentAlert.getCommentTextView().getText();
                    }
                    if (o instanceof MediaController.SearchImage) {
                        MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                        photoEntry1.caption = parentAlert.getCommentTextView().getText();
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
                AndroidUtilities.runOnUIThread(()-> {
                    int avatarType = type;
                    if (parentAlert.isPhotoPicker) {
                        PhotoViewer.getInstance().setParentActivity(parentAlert.getBaseFragment());
                        PhotoViewer.getInstance().setMaxSelectedPhotos(0, false);
                        avatarType = PhotoViewer.SELECT_TYPE_WALLPAPER;;
                    }
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, finalPosition, avatarType, false, photoViewerProvider, chatActivity);
                    PhotoViewer.getInstance().setAvatarFor(parentAlert.getAvatarFor());
                    if (parentAlert.isPhotoPicker) {
                        PhotoViewer.getInstance().closePhotoAfterSelect = false;
                    }
                    if (captionForAllMedia()) {
                        PhotoViewer.getInstance().setCaption(parentAlert.getCommentTextView().getText());
                    }
                }, hasSpoiler ? 250 : 0);
            } else {
                if (SharedConfig.inappCamera) {
                    openCamera(true);
                } else {
                    if (parentAlert.delegate != null) {
                        parentAlert.delegate.didPressedButton(0, false, true, 0, false);
                    }
                }
            }
        });
        gridView.setOnItemLongClickListener((view, position) -> {
            if (position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (parentAlert.delegate != null) {
                    parentAlert.delegate.didPressedButton(0, false, true, 0, false);
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
        progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        progressView.setOnTouchListener(null);
        progressView.setTextSize(16);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }

        Paint recordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        recordPaint.setColor(0xffda564d);
        recordTime = new TextView(context) {

            float alpha = 0f;
            boolean isIncr;

            @Override
            protected void onDraw(Canvas canvas) {

                recordPaint.setAlpha((int) (125 + 130 * alpha));

                if (!isIncr) {
                    alpha -= 16 / 600.0f;
                    if (alpha <= 0) {
                        alpha = 0;
                        isIncr = true;
                    }
                } else {
                    alpha += 16 / 600.0f;
                    if (alpha >= 1) {
                        alpha = 1;
                        isIncr = false;
                    }
                }
                super.onDraw(canvas);
                canvas.drawCircle(AndroidUtilities.dp(14), getMeasuredHeight() / 2, AndroidUtilities.dp(4), recordPaint);
                invalidate();
            }
        };
        AndroidUtilities.updateViewVisibilityAnimated(recordTime, false, 1f, false);
        recordTime.setBackgroundResource(R.drawable.system);
        recordTime.getBackground().setColorFilter(new PorterDuffColorFilter(0x66000000, PorterDuff.Mode.MULTIPLY));
        recordTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        recordTime.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        recordTime.setAlpha(0.0f);
        recordTime.setTextColor(0xffffffff);
        recordTime.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        container.addView(recordTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));

        cameraPanel = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int cx;
                int cy;
                int cx2;
                int cy2;
                int cx3;
                int cy3;

                if (getMeasuredWidth() == AndroidUtilities.dp(126)) {
                    cx = getMeasuredWidth() / 2;
                    cy = getMeasuredHeight() / 2;
                    cx3 = cx2 = getMeasuredWidth() / 2;
                    cy2 = cy + cy / 2 + AndroidUtilities.dp(17);
                    cy3 = cy / 2 - AndroidUtilities.dp(17);
                } else {
                    cx = getMeasuredWidth() / 2;
                    cy = getMeasuredHeight() / 2 - AndroidUtilities.dp(13);
                    cx2 = cx + cx / 2 + AndroidUtilities.dp(17);
                    cx3 = cx / 2 - AndroidUtilities.dp(17);
                    cy3 = cy2 = getMeasuredHeight() / 2 - AndroidUtilities.dp(13);
                }

                int y = getMeasuredHeight() - tooltipTextView.getMeasuredHeight() - AndroidUtilities.dp(12);
                if (getMeasuredWidth() == AndroidUtilities.dp(126)) {
                    tooltipTextView.layout(cx - tooltipTextView.getMeasuredWidth() / 2, getMeasuredHeight(), cx + tooltipTextView.getMeasuredWidth() / 2, getMeasuredHeight() + tooltipTextView.getMeasuredHeight());
                } else {
                    tooltipTextView.layout(cx - tooltipTextView.getMeasuredWidth() / 2, y, cx + tooltipTextView.getMeasuredWidth() / 2, y + tooltipTextView.getMeasuredHeight());
                }
                shutterButton.layout(cx - shutterButton.getMeasuredWidth() / 2, cy - shutterButton.getMeasuredHeight() / 2, cx + shutterButton.getMeasuredWidth() / 2, cy + shutterButton.getMeasuredHeight() / 2);
                switchCameraButton.layout(cx2 - switchCameraButton.getMeasuredWidth() / 2, cy2 - switchCameraButton.getMeasuredHeight() / 2, cx2 + switchCameraButton.getMeasuredWidth() / 2, cy2 + switchCameraButton.getMeasuredHeight() / 2);
                for (int a = 0; a < 2; a++) {
                    flashModeButton[a].layout(cx3 - flashModeButton[a].getMeasuredWidth() / 2, cy3 - flashModeButton[a].getMeasuredHeight() / 2, cx3 + flashModeButton[a].getMeasuredWidth() / 2, cy3 + flashModeButton[a].getMeasuredHeight() / 2);
                }
            }
        };
        cameraPanel.setVisibility(View.GONE);
        cameraPanel.setAlpha(0.0f);
        container.addView(cameraPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 126, Gravity.LEFT | Gravity.BOTTOM));

        counterTextView = new TextView(context);
        counterTextView.setBackgroundResource(R.drawable.photos_rounded);
        counterTextView.setVisibility(View.GONE);
        counterTextView.setTextColor(0xffffffff);
        counterTextView.setGravity(Gravity.CENTER);
        counterTextView.setPivotX(0);
        counterTextView.setPivotY(0);
        counterTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        counterTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.photos_arrow, 0);
        counterTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        counterTextView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        container.addView(counterTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 100 + 16));
        counterTextView.setOnClickListener(v -> {
            if (cameraView == null) {
                return;
            }
            openPhotoViewer(null, false, false);
            CameraController.getInstance().stopPreview(cameraView.getCameraSession());
        });

        zoomControlView = new ZoomControlView(context);
        zoomControlView.setVisibility(View.GONE);
        zoomControlView.setAlpha(0.0f);
        container.addView(zoomControlView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 100 + 16));
        zoomControlView.setDelegate(zoom -> {
            if (cameraView != null) {
                cameraView.setZoom(cameraZoom = zoom);
            }
            showZoomControls(true, true);
        });

        shutterButton = new ShutterButton(context);
        cameraPanel.addView(shutterButton, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
        shutterButton.setDelegate(new ShutterButton.ShutterButtonDelegate() {

            private File outputFile;
            private boolean zoomingWas;

            @Override
            public boolean shutterLongPressed() {
                if (parentAlert.avatarPicker != 2 && !(parentAlert.baseFragment instanceof ChatActivity) || takingPhoto || parentAlert.baseFragment == null || parentAlert.baseFragment.getParentActivity() == null || cameraView == null) {
                    return false;
                }
                if (!videoEnabled) {
                    BulletinFactory.of(cameraView, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.GlobalAttachVideoRestricted)).show();
                    return false;
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (parentAlert.baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestingPermissions = true;
                        parentAlert.baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                        return false;
                    }
                }
                for (int a = 0; a < 2; a++) {
                    flashModeButton[a].animate().alpha(0f).translationX(AndroidUtilities.dp(30)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                }
                switchCameraButton.animate().alpha(0f).translationX(-AndroidUtilities.dp(30)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                tooltipTextView.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                outputFile = AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat());
                AndroidUtilities.updateViewVisibilityAnimated(recordTime, true);
                recordTime.setText(AndroidUtilities.formatLongDuration(0));
                videoRecordTime = 0;
                videoRecordRunnable = () -> {
                    if (videoRecordRunnable == null) {
                        return;
                    }
                    videoRecordTime++;
                    recordTime.setText(AndroidUtilities.formatLongDuration(videoRecordTime));
                    AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000);
                };
                AndroidUtilities.lockOrientation(parentAlert.baseFragment.getParentActivity());
                CameraController.getInstance().recordVideo(cameraView.getCameraSession(), outputFile, parentAlert.avatarPicker != 0, (thumbPath, duration) -> {
                    if (outputFile == null || parentAlert.baseFragment == null || cameraView == null) {
                        return;
                    }
                    mediaFromExternalCamera = false;
                    int width = 0, height = 0;
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(new File(thumbPath).getAbsolutePath(), options);
                        width = options.outWidth;
                        height = options.outHeight;
                    } catch (Exception ignore) {}
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, true, width, height, 0);
                    photoEntry.duration = (int) duration;
                    photoEntry.thumbPath = thumbPath;
                    if (parentAlert.avatarPicker != 0 && cameraView.isFrontface()) {
                        photoEntry.cropState = new MediaController.CropState();
                        photoEntry.cropState.mirrored = true;
                        photoEntry.cropState.freeform = false;
                        photoEntry.cropState.lockedAspectRatio = 1.0f;
                    }
                    openPhotoViewer(photoEntry, false, false);
                }, () -> AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000), cameraView);
                shutterButton.setState(ShutterButton.State.RECORDING, true);
                cameraView.runHaptic();
                return true;
            }

            @Override
            public void shutterCancel() {
                if (outputFile != null) {
                    outputFile.delete();
                    outputFile = null;
                }
                resetRecordState();
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), true);
            }

            @Override
            public void shutterReleased() {
                if (takingPhoto || cameraView == null || cameraView.getCameraSession() == null) {
                    return;
                }
                if (shutterButton.getState() == ShutterButton.State.RECORDING) {
                    resetRecordState();
                    CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
                    shutterButton.setState(ShutterButton.State.DEFAULT, true);
                    return;
                }
                if (!photoEnabled) {
                    BulletinFactory.of(cameraView, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.GlobalAttachPhotoRestricted)).show();
                    return;
                }
                final File cameraFile = AndroidUtilities.generatePicturePath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat(), null);
                final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();
                cameraView.getCameraSession().setFlipFront(parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2);
                takingPhoto = CameraController.getInstance().takePicture(cameraFile, cameraView.getCameraSession(), () -> {
                    takingPhoto = false;
                    if (cameraFile == null || parentAlert.baseFragment == null) {
                        return;
                    }
                    int orientation = 0;
                    try {
                        ExifInterface ei = new ExifInterface(cameraFile.getAbsolutePath());
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
                    mediaFromExternalCamera = false;
                    int width = 0, height = 0;
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(new File(cameraFile.getAbsolutePath()).getAbsolutePath(), options);
                        width = options.outWidth;
                        height = options.outHeight;
                    } catch (Exception ignore) {}
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, cameraFile.getAbsolutePath(), orientation, false, width, height, 0);
                    photoEntry.canDeleteAfter = true;
                    openPhotoViewer(photoEntry, sameTakePictureOrientation, false);
                });
                cameraView.startTakePictureAnimation();
            }


            @Override
            public boolean onTranslationChanged(float x, float y) {
                boolean isPortrait = container.getWidth() < container.getHeight();
                float val1 = isPortrait ? x : y;
                float val2 = isPortrait ? y : x;
                if (!zoomingWas && Math.abs(val1) > Math.abs(val2)) {
                    return zoomControlView.getTag() == null;
                }
                if (val2 < 0) {
                    showZoomControls(true, true);
                    zoomControlView.setZoom(-val2 / AndroidUtilities.dp(200), true);
                    zoomingWas = true;
                    return false;
                }
                if (zoomingWas) {
                    zoomControlView.setZoom(0, true);
                }
                if (x == 0 && y == 0) {
                    zoomingWas = false;
                }
                return !zoomingWas && (x != 0 || y != 0);
            }
        });
        shutterButton.setFocusable(true);
        shutterButton.setContentDescription(LocaleController.getString("AccDescrShutter", R.string.AccDescrShutter));

        switchCameraButton = new ImageView(context);
        switchCameraButton.setScaleType(ImageView.ScaleType.CENTER);
        cameraPanel.addView(switchCameraButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        switchCameraButton.setOnClickListener(v -> {
            if (takingPhoto || cameraView == null || !cameraView.isInited()) {
                return;
            }
            canSaveCameraPreview = false;
            cameraView.switchCamera();
            cameraView.startSwitchingAnimation();
            ObjectAnimator animator = ObjectAnimator.ofFloat(switchCameraButton, View.SCALE_X, 0.0f).setDuration(100);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    switchCameraButton.setImageResource(cameraView != null && cameraView.isFrontface() ? R.drawable.camera_revert1 : R.drawable.camera_revert2);
                    ObjectAnimator.ofFloat(switchCameraButton, View.SCALE_X, 1.0f).setDuration(100).start();
                }
            });
            animator.start();

        });
        switchCameraButton.setContentDescription(LocaleController.getString("AccDescrSwitchCamera", R.string.AccDescrSwitchCamera));

        for (int a = 0; a < 2; a++) {
            flashModeButton[a] = new ImageView(context);
            flashModeButton[a].setScaleType(ImageView.ScaleType.CENTER);
            flashModeButton[a].setVisibility(View.INVISIBLE);
            cameraPanel.addView(flashModeButton[a], LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
            flashModeButton[a].setOnClickListener(currentImage -> {
                if (flashAnimationInProgress || cameraView == null || !cameraView.isInited() || !cameraOpened) {
                    return;
                }
                String current = cameraView.getCameraSession().getCurrentFlashMode();
                String next = cameraView.getCameraSession().getNextFlashMode();
                if (current.equals(next)) {
                    return;
                }
                cameraView.getCameraSession().setCurrentFlashMode(next);
                flashAnimationInProgress = true;
                ImageView nextImage = flashModeButton[0] == currentImage ? flashModeButton[1] : flashModeButton[0];
                nextImage.setVisibility(View.VISIBLE);
                setCameraFlashModeIcon(nextImage, next);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(currentImage, View.TRANSLATION_Y, 0, AndroidUtilities.dp(48)),
                        ObjectAnimator.ofFloat(nextImage, View.TRANSLATION_Y, -AndroidUtilities.dp(48), 0),
                        ObjectAnimator.ofFloat(currentImage, View.ALPHA, 1.0f, 0.0f),
                        ObjectAnimator.ofFloat(nextImage, View.ALPHA, 0.0f, 1.0f));
                animatorSet.setDuration(220);
                animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        flashAnimationInProgress = false;
                        currentImage.setVisibility(View.INVISIBLE);
                        nextImage.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    }
                });
                animatorSet.start();
            });
            flashModeButton[a].setContentDescription("flash mode " + a);
        }

        tooltipTextView = new TextView(context);
        tooltipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tooltipTextView.setTextColor(0xffffffff);
        tooltipTextView.setText(LocaleController.getString("TapForVideo", R.string.TapForVideo));
        tooltipTextView.setShadowLayer(AndroidUtilities.dp(3.33333f), 0, AndroidUtilities.dp(0.666f), 0x4c000000);
        tooltipTextView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        cameraPanel.addView(tooltipTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 16));

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
        cameraPhotoRecyclerView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
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

                photoEntry.editedInfo.mediaEntities = new ArrayList<>();
                photoEntry.editedInfo.mediaEntities.add(mediaEntity);
            } else {
                photoEntry = new MediaController.PhotoEntry(0, 0, 0, thumb.getPath(), 0, false, 0, 0, 0);
            }
            selectedPhotos.put(-1, photoEntry);
            selectedPhotosOrder.add(-1);
            parentAlert.delegate.didPressedButton(7, true, false, 0, false);
        });
    }

    private boolean checkSendMediaEnabled(MediaController.PhotoEntry photoEntry) {
        if (!videoEnabled && photoEntry.isVideo) {
            BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(
                    LocaleController.getString("GlobalAttachVideoRestricted", R.string.GlobalAttachVideoRestricted)
            ).show();
            return true;
        } else if (!photoEnabled && !photoEntry.isVideo) {
            BulletinFactory.of(parentAlert.sizeNotifierFrameLayout, resourcesProvider).createErrorBulletin(
                    LocaleController.getString("GlobalAttachPhotoRestricted", R.string.GlobalAttachPhotoRestricted)
            ).show();
            return true;
        }
        return false;
    }

    private int addToSelectedPhotos(MediaController.PhotoEntry object, int index) {
        Object key = object.imageId;
        if (selectedPhotos.containsKey(key)) {
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
            selectedPhotos.put(key, object);
            selectedPhotosOrder.add(key);
            updatePhotosCounter(true);
            return -1;
        }
    }

    private void clearSelectedPhotos() {
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
                new File(photoEntry.path).delete();
                if (photoEntry.imagePath != null) {
                    new File(photoEntry.imagePath).delete();
                }
                if (photoEntry.thumbPath != null) {
                    new File(photoEntry.thumbPath).delete();
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
                dropDownContainer.addSubItem(10 + a, dropDownAlbums.get(a).bucketName);
            }
        }
    }

    private boolean processTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        if (!pressed && event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            zoomControlView.getHitRect(hitRect);
            if (zoomControlView.getTag() != null && hitRect.contains((int) event.getX(), (int) event.getY())) {
                return false;
            }
            if (!takingPhoto && !dragging) {
                if (event.getPointerCount() == 2) {
                    pinchStartDistance = (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
                    zooming = true;
                } else {
                    maybeStartDraging = true;
                    lastY = event.getY();
                    zooming = false;
                }
                zoomWas = false;
                pressed = true;
            }
        } else if (pressed) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (zooming && event.getPointerCount() == 2 && !dragging) {
                    float newDistance = (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
                    if (!zoomWas) {
                        if (Math.abs(newDistance - pinchStartDistance) >= AndroidUtilities.getPixelsInCM(0.4f, false)) {
                            pinchStartDistance = newDistance;
                            zoomWas = true;
                        }
                    } else {
                        if (cameraView != null) {
                            float diff = (newDistance - pinchStartDistance) / AndroidUtilities.dp(100);
                            pinchStartDistance = newDistance;
                            cameraZoom += diff;
                            if (cameraZoom < 0.0f) {
                                cameraZoom = 0.0f;
                            } else if (cameraZoom > 1.0f) {
                                cameraZoom = 1.0f;
                            }
                            zoomControlView.setZoom(cameraZoom, false);
                            parentAlert.getSheetContainer().invalidate();
                            cameraView.setZoom(cameraZoom);
                            showZoomControls(true, true);
                        }
                    }
                } else {
                    float newY = event.getY();
                    float dy = (newY - lastY);
                    if (maybeStartDraging) {
                        if (Math.abs(dy) > AndroidUtilities.getPixelsInCM(0.4f, false)) {
                            maybeStartDraging = false;
                            dragging = true;
                        }
                    } else if (dragging) {
                        if (cameraView != null) {
                            cameraView.setTranslationY(cameraView.getTranslationY() + dy);
                            lastY = newY;
                            zoomControlView.setTag(null);
                            if (zoomControlHideRunnable != null) {
                                AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                                zoomControlHideRunnable = null;
                            }
                            if (cameraPanel.getTag() == null) {
                                cameraPanel.setTag(1);
                                AnimatorSet animatorSet = new AnimatorSet();
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(cameraPanel, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(flashModeButton[0], View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(flashModeButton[1], View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));
                                animatorSet.setDuration(220);
                                animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                                animatorSet.start();
                            }
                        }
                    }
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                pressed = false;
                zooming = false;
                if (zooming) {
                    zooming = false;
                } else if (dragging) {
                    dragging = false;
                    if (cameraView != null) {
                        if (Math.abs(cameraView.getTranslationY()) > cameraView.getMeasuredHeight() / 6.0f) {
                            closeCamera(true);
                        } else {
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(cameraView, View.TRANSLATION_Y, 0.0f),
                                    ObjectAnimator.ofFloat(cameraPanel, View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(flashModeButton[0], View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(flashModeButton[1], View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
                            animatorSet.setDuration(250);
                            animatorSet.setInterpolator(interpolator);
                            animatorSet.start();
                            cameraPanel.setTag(null);
                        }
                    }
                } else if (cameraView != null && !zoomWas) {
                    cameraView.getLocationOnScreen(viewPosition);
                    float viewX = event.getRawX() - viewPosition[0];
                    float viewY = event.getRawY() - viewPosition[1];
                    cameraView.focusToPoint((int) viewX, (int) viewY);
                }
            }
        }
        return true;
    }

    private void resetRecordState() {
        if (parentAlert.baseFragment == null) {
            return;
        }

        for (int a = 0; a < 2; a++) {
            flashModeButton[a].animate().alpha(1f).translationX(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        switchCameraButton.animate().alpha(1f).translationX(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        tooltipTextView.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        AndroidUtilities.updateViewVisibilityAnimated(recordTime, false);

        AndroidUtilities.cancelRunOnUIThread(videoRecordRunnable);
        videoRecordRunnable = null;
        AndroidUtilities.unlockOrientation(parentAlert.baseFragment.getParentActivity());
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
                CameraController.getInstance().startPreview(cameraView.getCameraSession());
            }
            return;
        }
        if (cameraPhotos.isEmpty()) {
            return;
        }
        cancelTakingPhotos = true;
        PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment.getParentActivity(), resourcesProvider);
        PhotoViewer.getInstance().setParentAlert(parentAlert);
        PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);

        ChatActivity chatActivity;
        int type;
        if (parentAlert.avatarPicker != 0) {
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
            arrayList = getAllPhotosArray();
            index = cameraPhotos.size() - 1;
        }
        if (parentAlert.getAvatarFor() != null && entry != null) {
            parentAlert.getAvatarFor().isVideo = entry.isVideo;
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

            @Override
            public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                return null;
            }

            @Override
            public boolean cancelButtonPressed() {
                if (cameraOpened && cameraView != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cameraView != null && !parentAlert.isDismissed() && Build.VERSION.SDK_INT >= 21) {
                            cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
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
                        new File(photoEntry.path).delete();
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
                    parentAlert.delegate.didPressedButton(0, true, true, 0, false);
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
                if (cameraPhotos.isEmpty() || parentAlert.baseFragment == null) {
                    return;
                }
                if (videoEditedInfo != null && index >= 0 && index < cameraPhotos.size()) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                }
                if (!(parentAlert.baseFragment instanceof ChatActivity) || !((ChatActivity) parentAlert.baseFragment).isSecretChat()) {
                    for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                        if (entry.ttl > 0) {
                            continue;
                        }
                        AndroidUtilities.addMediaToGallery(entry.path);
                    }
                }
                parentAlert.applyCaption();
                closeCamera(false);
                parentAlert.delegate.didPressedButton(forceDocument ? 4 : 8, true, notify, scheduleDate, forceDocument);
                cameraPhotos.clear();
                selectedPhotosOrder.clear();
                selectedPhotos.clear();
                adapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
                parentAlert.dismiss(true);
            }

            @Override
            public boolean scaleToFill() {
                if (parentAlert.baseFragment == null || parentAlert.baseFragment.getParentActivity() == null) {
                    return false;
                }
                int locked = Settings.System.getInt(parentAlert.baseFragment.getParentActivity().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
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
        }, chatActivity);
        PhotoViewer.getInstance().setAvatarFor(parentAlert.getAvatarFor());
    }

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
        zoomControlAnimation.playTogether(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, show ? 1.0f : 0.0f));
        zoomControlAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
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
        if (counterTextView == null || parentAlert.avatarPicker != 0) {
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

    private void setCameraFlashModeIcon(ImageView imageView, String mode) {
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_OFF:
                imageView.setImageResource(R.drawable.flash_off);
                imageView.setContentDescription(LocaleController.getString("AccDescrCameraFlashOff", R.string.AccDescrCameraFlashOff));
                break;
            case Camera.Parameters.FLASH_MODE_ON:
                imageView.setImageResource(R.drawable.flash_on);
                imageView.setContentDescription(LocaleController.getString("AccDescrCameraFlashOn", R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                imageView.setImageResource(R.drawable.flash_auto);
                imageView.setContentDescription(LocaleController.getString("AccDescrCameraFlashAuto", R.string.AccDescrCameraFlashAuto));
                break;
        }
    }

    public void checkCamera(boolean request) {
        if (parentAlert.baseFragment == null || parentAlert.baseFragment.getParentActivity() == null || !needCamera) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        boolean old2 = noCameraPermissions;
        if (!SharedConfig.inappCamera) {
            deviceHasGoodCamera = false;
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                if (noCameraPermissions = (parentAlert.baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
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
        if (parentAlert.isShowing() && deviceHasGoodCamera && parentAlert.baseFragment != null && parentAlert.getBackDrawable().getAlpha() != 0 && !cameraOpened) {
            showCamera();
        }
    }

    boolean cameraExpanded;
    private void openCamera(boolean animated) {
        if (cameraView == null || cameraInitAnimation != null || parentAlert.isDismissed()) {
            return;
        }
        cameraView.initTexture();
        if (shouldLoadAllMedia()) {
            tooltipTextView.setVisibility(VISIBLE);
        } else {
            tooltipTextView.setVisibility(GONE);
        }
        if (cameraPhotos.isEmpty()) {
            counterTextView.setVisibility(View.INVISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
        } else {
            counterTextView.setVisibility(View.VISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
        }
        if (parentAlert.commentTextView.isKeyboardVisible() && isFocusable()) {
            parentAlert.commentTextView.closeKeyboard();
        }
        zoomControlView.setVisibility(View.VISIBLE);
        zoomControlView.setAlpha(0.0f);
        cameraPanel.setVisibility(View.VISIBLE);
        cameraPanel.setTag(null);
        animateCameraValues[0] = 0;
        animateCameraValues[1] = itemSize;
        animateCameraValues[2] = itemSize;
        additionCloseCameraY = 0;
        cameraExpanded = true;
        if (cameraView != null) {
            cameraView.setFpsLimit(-1);
        }
        AndroidUtilities.hideKeyboard(this);
        AndroidUtilities.setLightNavigationBar(parentAlert.getWindow(), false);
        parentAlert.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
        if (animated) {
            setCameraOpenProgress(0);
            cameraAnimationInProgress = true;
            animationIndex = NotificationCenter.getInstance(parentAlert.currentAccount).setAnimationInProgress(animationIndex, null);
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPanel, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 1.0f));
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(flashModeButton[a], View.ALPHA, 1.0f));
                    break;
                }
            }
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(350);
            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    NotificationCenter.getInstance(parentAlert.currentAccount).onAnimationFinish(animationIndex);
                    cameraAnimationInProgress = false;
                    if (cameraView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            cameraView.invalidateOutline();
                        } else {
                            cameraView.invalidate();
                        }
                    }
                    if (cameraOpened) {
                        parentAlert.delegate.onCameraOpened();
                    }
                    if (Build.VERSION.SDK_INT >= 21 && cameraView != null) {
                        cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                    }
                }
            });
            animatorSet.start();
        } else {
            setCameraOpenProgress(1.0f);
            cameraPanel.setAlpha(1.0f);
            counterTextView.setAlpha(1.0f);
            cameraPhotoRecyclerView.setAlpha(1.0f);
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    flashModeButton[a].setAlpha(1.0f);
                    break;
                }
            }
            parentAlert.delegate.onCameraOpened();
            if (cameraView != null && Build.VERSION.SDK_INT >= 21) {
                cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        }
        cameraOpened = true;
        if (cameraView != null) {
            cameraView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        if (!LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && cameraView != null && cameraView.isInited()) {
            cameraView.showTexture(true, animated);
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
        return !parentAlert.isPhotoPicker && (parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2);
    }

    public void showCamera() {
        if (parentAlert.paused || !mediaEnabled) {
            return;
        }
        if (cameraView == null) {
            final boolean lazy = !LiteMode.isEnabled(LiteMode.FLAGS_CHAT);
            cameraView = new CameraView(parentAlert.baseFragment.getParentActivity(), parentAlert.openWithFrontFaceCamera, lazy) {

                Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
                    @Override
                    public int getBottomOffset(int tag) {
                        return AndroidUtilities.dp(126) + parentAlert.getBottomInset();
                    }
                };
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (Build.VERSION.SDK_INT >= 21) {
                        super.dispatchDraw(canvas);
                    } else {
                        int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - cameraView.getTranslationY(), getMeasuredHeight());
                        if (cameraAnimationInProgress) {
                            AndroidUtilities.rectTmp.set(animationClipLeft + cameraViewOffsetX * (1f - cameraOpenProgress), animationClipTop + cameraViewOffsetY * (1f - cameraOpenProgress), animationClipRight, Math.min(maxY, animationClipBottom));
                        } else if (!cameraAnimationInProgress && !cameraOpened) {
                            AndroidUtilities.rectTmp.set(cameraViewOffsetX, cameraViewOffsetY, getMeasuredWidth(), Math.min(maxY, getMeasuredHeight()));
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
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    Bulletin.addDelegate(cameraView, bulletinDelegate);
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    Bulletin.removeDelegate(cameraView);
                }
            };
            if (cameraCell != null && lazy) {
                cameraView.setThumbDrawable(cameraCell.getDrawable());
            }
            cameraView.setRecordFile(AndroidUtilities.generateVideoPath(parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat()));
            cameraView.setFocusable(true);
            cameraView.setFpsLimit(30);
            if (Build.VERSION.SDK_INT >= 21) {
                cameraView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - cameraView.getTranslationY(), view.getMeasuredHeight());
                        if (cameraOpened) {
                            maxY = view.getMeasuredHeight();
                        } else if (cameraAnimationInProgress) {
                            maxY = AndroidUtilities.lerp(maxY, view.getMeasuredHeight(), cameraOpenProgress);
                        }
                        if (cameraAnimationInProgress) {
                            AndroidUtilities.rectTmp.set(animationClipLeft + cameraViewOffsetX * (1f - cameraOpenProgress), animationClipTop + cameraViewOffsetY * (1f - cameraOpenProgress), animationClipRight,  animationClipBottom);
                            outline.setRect((int) AndroidUtilities.rectTmp.left,(int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, Math.min(maxY, (int) AndroidUtilities.rectTmp.bottom));
                        } else if (!cameraAnimationInProgress && !cameraOpened) {
                            int rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect((int) cameraViewOffsetX, (int) cameraViewOffsetY, view.getMeasuredWidth() + rad, Math.min(maxY, view.getMeasuredHeight()) + rad, rad);
                        } else {
                            outline.setRect(0, 0, view.getMeasuredWidth(), Math.min(maxY, view.getMeasuredHeight()));
                        }
                    }
                });
                cameraView.setClipToOutline(true);
            }
            cameraView.setContentDescription(LocaleController.getString("AccDescrInstantCamera", R.string.AccDescrInstantCamera));
            parentAlert.getContainer().addView(cameraView, 1, new FrameLayout.LayoutParams(itemSize, itemSize));
            cameraView.setDelegate(new CameraView.CameraViewDelegate() {
                @Override
                public void onCameraInit() {
                    String current = cameraView.getCameraSession().getCurrentFlashMode();
                    String next = cameraView.getCameraSession().getNextFlashMode();
                    if (current.equals(next)) {
                        for (int a = 0; a < 2; a++) {
                            flashModeButton[a].setVisibility(View.INVISIBLE);
                            flashModeButton[a].setAlpha(0.0f);
                            flashModeButton[a].setTranslationY(0.0f);
                        }
                    } else {
                        setCameraFlashModeIcon(flashModeButton[0], cameraView.getCameraSession().getCurrentFlashMode());
                        for (int a = 0; a < 2; a++) {
                            flashModeButton[a].setVisibility(a == 0 ? View.VISIBLE : View.INVISIBLE);
                            flashModeButton[a].setAlpha(a == 0 && cameraOpened ? 1.0f : 0.0f);
                            flashModeButton[a].setTranslationY(0.0f);
                        }
                    }
                    switchCameraButton.setImageResource(cameraView.isFrontface() ? R.drawable.camera_revert1 : R.drawable.camera_revert2);
                    switchCameraButton.setVisibility(cameraView.hasFrontFaceCamera() ? View.VISIBLE : View.INVISIBLE);
                    if (!cameraOpened) {
                        cameraInitAnimation = new AnimatorSet();
                        cameraInitAnimation.playTogether(
                                ObjectAnimator.ofFloat(cameraView, View.ALPHA, 0.0f, 1.0f),
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
                }
            });

            if (cameraIcon == null) {
                cameraIcon = new FrameLayout(parentAlert.baseFragment.getParentActivity()) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        int maxY = (int) Math.min(parentAlert.getCommentTextViewTop() + currentPanTranslationY + parentAlert.getContainerView().getTranslationY() - cameraView.getTranslationY(), getMeasuredHeight());
                        if (cameraOpened) {
                            maxY = getMeasuredHeight();
                        } else if (cameraAnimationInProgress) {
                            maxY = AndroidUtilities.lerp(maxY, getMeasuredHeight(), cameraOpenProgress);
                        }
                        int w = cameraDrawable.getIntrinsicWidth();
                        int h = cameraDrawable.getIntrinsicHeight();
                        int x = (itemSize - w) / 2;
                        int y = (itemSize - h) / 2;
                        if (cameraViewOffsetY != 0) {
                            y -= cameraViewOffsetY;
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
            }
            parentAlert.getContainer().addView(cameraIcon, 2, new FrameLayout.LayoutParams(itemSize, itemSize));

            cameraView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraView.setEnabled(mediaEnabled);
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
            if (isHidden) {
                cameraView.setVisibility(GONE);
                cameraIcon.setVisibility(GONE);
            }
            checkCameraViewPosition();
            invalidate();
        }
        if (zoomControlView != null) {
            zoomControlView.setZoom(0.0f, false);
            cameraZoom = 0.0f;
        }
        cameraView.setTranslationX(cameraViewLocation[0]);
        cameraView.setTranslationY(cameraViewLocation[1] + currentPanTranslationY);
        cameraIcon.setTranslationX(cameraViewLocation[0]);
        cameraIcon.setTranslationY(cameraViewLocation[1] + cameraViewOffsetY + currentPanTranslationY);
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
            parentAlert.getContainer().removeView(cameraView);
            parentAlert.getContainer().removeView(cameraIcon);
            cameraView = null;
            cameraIcon = null;
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
                Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), cameraView.getMatrix(), true);
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
        if (parentAlert.baseFragment == null || parentAlert.baseFragment.getParentActivity() == null) {
            return;
        }
        mediaFromExternalCamera = true;
        if (requestCode == 0) {
            PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment.getParentActivity(), resourcesProvider);
            PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
            final ArrayList<Object> arrayList = new ArrayList<>();
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
            int width = 0, height = 0;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(new File(currentPicturePath).getAbsolutePath(), options);
                width = options.outWidth;
                height = options.outHeight;
            } catch (Exception ignore) {}
            MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation, false, width, height, 0);
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
        if (zoomControlHideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
            zoomControlHideRunnable = null;
        }
        AndroidUtilities.setLightNavigationBar(parentAlert.getWindow(), AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_windowBackgroundGray)) > 0.721);
        if (animated) {
            additionCloseCameraY = cameraView.getTranslationY();

            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "cameraOpenProgress", 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPanel, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, View.ALPHA, 0.0f));
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(flashModeButton[a], View.ALPHA, 0.0f));
                    break;
                }
            }

            animationIndex = NotificationCenter.getInstance(parentAlert.currentAccount).setAnimationInProgress(animationIndex, null);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(220);
            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    NotificationCenter.getInstance(parentAlert.currentAccount).onAnimationFinish(animationIndex);
                    cameraExpanded = false;
                    parentAlert.getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
                    setCameraOpenProgress(0f);
                    cameraAnimationInProgress = false;
                    if (cameraView != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            cameraView.invalidateOutline();
                        } else {
                            cameraView.invalidate();
                        }
                    }
                    cameraOpened = false;

                    if (cameraPanel != null) {
                        cameraPanel.setVisibility(View.GONE);
                    }
                    if (zoomControlView != null) {
                        zoomControlView.setVisibility(View.GONE);
                        zoomControlView.setTag(null);
                    }
                    if (cameraPhotoRecyclerView != null) {
                        cameraPhotoRecyclerView.setVisibility(View.GONE);
                    }
                    if (cameraView != null) {
                        cameraView.setFpsLimit(30);
                        if (Build.VERSION.SDK_INT >= 21) {
                            cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
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
            cameraPanel.setAlpha(0);
            cameraPanel.setVisibility(View.GONE);
            zoomControlView.setAlpha(0);
            zoomControlView.setTag(null);
            zoomControlView.setVisibility(View.GONE);
            cameraPhotoRecyclerView.setAlpha(0);
            counterTextView.setAlpha(0);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    flashModeButton[a].setAlpha(0.0f);
                    break;
                }
            }
            cameraOpened = false;
            if (cameraView != null) {
                cameraView.setFpsLimit(30);
                if (Build.VERSION.SDK_INT >= 21) {
                    cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                }
            }
        }
        if (cameraView != null) {
            cameraView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
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
        if (cameraView == null) {
            return;
        }
        cameraOpenProgress = value;
        float startWidth = animateCameraValues[1];
        float startHeight = animateCameraValues[2];
        boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
        float endWidth = parentAlert.getContainer().getWidth() - parentAlert.getLeftInset() - parentAlert.getRightInset();
        float endHeight = parentAlert.getContainer().getHeight();

        float fromX = cameraViewLocation[0];
        float fromY = cameraViewLocation[1];
        float toX = 0;
        float toY = additionCloseCameraY;

        if (value == 0) {
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1] + cameraViewOffsetY);
        }


        int cameraViewW, cameraViewH;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();

        float textureStartHeight = cameraView.getTextureHeight(startWidth, startHeight);
        float textureEndHeight = cameraView.getTextureHeight(endWidth, endHeight);

        float fromScale = textureStartHeight / textureEndHeight;
        float fromScaleY = startHeight / endHeight;
        float fromScaleX = startWidth/ endWidth;

        if (cameraExpanded) {
            cameraViewW = (int) endWidth;
            cameraViewH = (int) endHeight;
            final float s = fromScale * (1f - value) + value;
            cameraView.getTextureView().setScaleX(s);
            cameraView.getTextureView().setScaleY(s);

            final float sX = fromScaleX * (1f - value) + value;
            final float sY = fromScaleY * (1f - value) + value;

            final float scaleOffsetY = (1 - sY) * endHeight / 2;
            final float scaleOffsetX =  (1 - sX) * endWidth / 2;

            cameraView.setTranslationX(fromX * (1f - value) + toX * value - scaleOffsetX);
            cameraView.setTranslationY(fromY * (1f - value) + toY * value - scaleOffsetY);
            animationClipTop = fromY * (1f - value) - cameraView.getTranslationY();
            animationClipBottom =  ((fromY + startHeight) * (1f - value) - cameraView.getTranslationY()) + endHeight * value;

            animationClipLeft = fromX * (1f - value) - cameraView.getTranslationX();
            animationClipRight =  ((fromX + startWidth) * (1f - value) - cameraView.getTranslationX()) + endWidth * value;
        } else {
            cameraViewW = (int) startWidth;
            cameraViewH = (int) startHeight;
            cameraView.getTextureView().setScaleX(1f);
            cameraView.getTextureView().setScaleY(1f);
            animationClipTop = 0;
            animationClipBottom = endHeight;
            animationClipLeft = 0;
            animationClipRight = endWidth;

            cameraView.setTranslationX(fromX);
            cameraView.setTranslationY(fromY);
        }

        if (value <= 0.5f) {
            cameraIcon.setAlpha(1.0f - value / 0.5f);
        } else {
            cameraIcon.setAlpha(0.0f);
        }

        if (layoutParams.width != cameraViewW || layoutParams.height != cameraViewH) {
            layoutParams.width = cameraViewW;
            layoutParams.height = cameraViewH;
            cameraView.requestLayout();
        }
        if (Build.VERSION.SDK_INT >= 21) {
            cameraView.invalidateOutline();
        } else {
            cameraView.invalidate();
        }
    }

    @Keep
    public float getCameraOpenProgress() {
        return cameraOpenProgress;
    }

    protected void checkCameraViewPosition() {
        if (Build.VERSION.SDK_INT >= 21) {
            if (cameraView != null) {
                cameraView.invalidateOutline();
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
        if (cameraView != null) {
            cameraView.invalidate();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && recordTime != null) {
            MarginLayoutParams params = (MarginLayoutParams) recordTime.getLayoutParams();
            params.topMargin = (getRootWindowInsets() == null ? AndroidUtilities.dp(16)  : getRootWindowInsets().getSystemWindowInsetTop() + AndroidUtilities.dp(2));
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

                float maxY = (Build.VERSION.SDK_INT >= 21 && !parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                float newCameraViewOffsetY;
                if (topLocal < maxY) {
                    newCameraViewOffsetY = maxY - topLocal;
                } else {
                    newCameraViewOffsetY = 0;
                }

                if (newCameraViewOffsetY != cameraViewOffsetY) {
                    cameraViewOffsetY = newCameraViewOffsetY;
                    if (cameraView != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cameraView.invalidateOutline();
                        } else {
                            cameraView.invalidate();
                        }
                    }
                    if (cameraIcon != null) {
                        cameraIcon.invalidate();
                    }
                }

                int containerHeight = parentAlert.getSheetContainer().getMeasuredHeight();
                maxY = (int) (containerHeight - parentAlert.buttonsRecyclerView.getMeasuredHeight() + parentAlert.buttonsRecyclerView.getTranslationY());

                if (topLocal + child.getMeasuredHeight() > maxY) {
                    cameraViewOffsetBottomY = topLocal + child.getMeasuredHeight() - maxY;
                } else {
                    cameraViewOffsetBottomY = 0;
                }

                cameraViewLocation[0] = left;
                cameraViewLocation[1] = top;
                applyCameraViewPosition();
                return;
            }
        }


        if (cameraViewOffsetY != 0 || cameraViewOffsetX != 0) {
            cameraViewOffsetX = 0;
            cameraViewOffsetY = 0;
            if (cameraView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cameraView.invalidateOutline();
                } else {
                    cameraView.invalidate();
                }
            }
            if (cameraIcon != null) {
                cameraIcon.invalidate();
            }
        }

        cameraViewLocation[0] = AndroidUtilities.dp(-400);
        cameraViewLocation[1] = 0;

        applyCameraViewPosition();
    }

    private void applyCameraViewPosition() {
        if (cameraView != null) {
            if (!cameraOpened) {
                cameraView.setTranslationX(cameraViewLocation[0]);
                cameraView.setTranslationY(cameraViewLocation[1] + currentPanTranslationY);
            }
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1] + cameraViewOffsetY + currentPanTranslationY);
            int finalWidth = itemSize;
            int finalHeight = itemSize;

            LayoutParams layoutParams;
            if (!cameraOpened) {
                cameraView.setClipTop((int) cameraViewOffsetY);
                cameraView.setClipBottom((int) cameraViewOffsetBottomY);
                layoutParams = (LayoutParams) cameraView.getLayoutParams();
                if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                    layoutParams.width = finalWidth;
                    layoutParams.height = finalHeight;
                    cameraView.setLayoutParams(layoutParams);
                    final LayoutParams layoutParamsFinal = layoutParams;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cameraView != null) {
                            cameraView.setLayoutParams(layoutParamsFinal);
                        }
                    });
                }
            }

            finalWidth = (int) (itemSize - cameraViewOffsetX);
            finalHeight = (int) (itemSize - cameraViewOffsetY - cameraViewOffsetBottomY);

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
                        cell.setPhotoEntry(photoEntry, adapter.needCamera && selectedAlbumEntry == galleryAlbumEntry, position == adapter.getItemCount() - 1);
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

    public void checkStorage() {
        if (noGalleryPermissions && Build.VERSION.SDK_INT >= 23) {
            noGalleryPermissions = parentAlert.baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (!noGalleryPermissions) {
                loadGalleryPhotos();
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
    }

    @Override
    void scrollToTop() {
        gridView.smoothScrollToPosition(0);
    }

    @Override
    int needsActionBar() {
        return 1;
    }

    @Override
    void onMenuItemClick(int id) {
        if (id == group || id == compress) {
            if (parentAlert.maxSelectedPhotos > 0 && selectedPhotosOrder.size() > 1 && parentAlert.baseFragment instanceof ChatActivity) {
                ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
                TLRPC.Chat chat = chatActivity.getCurrentChat();
                if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                    AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSendError", R.string.SlowmodeSendError), resourcesProvider).show();
                    return;
                }
            }
        }
        if (id == group) {
            if (parentAlert.editingMessageObject == null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) parentAlert.baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(7, false, notify, scheduleDate, false);
                }, resourcesProvider);
            } else {
                parentAlert.applyCaption();
                parentAlert.delegate.didPressedButton(7, false, true, 0, false);
            }
        } else if (id == compress) {
            if (parentAlert.editingMessageObject == null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) parentAlert.baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    parentAlert.applyCaption();
                    parentAlert.delegate.didPressedButton(4, true, notify, scheduleDate, false);
                }, resourcesProvider);
            } else {
                parentAlert.applyCaption();
                parentAlert.delegate.didPressedButton(4, true, true, 0, false);
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
                } else {
                    parentAlert.selectedMenuItem.showSubItem(compress);
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
        } else if (id >= 10) {
            selectedAlbumEntry = dropDownAlbums.get(id - 10);
            if (selectedAlbumEntry == galleryAlbumEntry) {
                dropDown.setText(LocaleController.getString("ChatGallery", R.string.ChatGallery));
            } else {
                dropDown.setText(selectedAlbumEntry.bucketName);
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
            layoutManager.scrollToPositionWithOffset(0, -gridView.getPaddingTop() + AndroidUtilities.dp(7));
        }
    }

    @Override
    int getSelectedItemsCount() {
        return selectedPhotosOrder.size();
    }

    @Override
    void onSelectedItemsCountChanged(int count) {
        if (count <= 1 || parentAlert.editingMessageObject != null) {
            parentAlert.selectedMenuItem.hideSubItem(group);
            if (count == 0) {
                parentAlert.selectedMenuItem.showSubItem(open_in);
                parentAlert.selectedMenuItem.hideSubItem(compress);
            } else {
                parentAlert.selectedMenuItem.showSubItem(compress);
            }
        } else {
            parentAlert.selectedMenuItem.showSubItem(group);
        }
        if (count != 0) {
            parentAlert.selectedMenuItem.hideSubItem(open_in);
        }
        compressItem.setVisibility(documentsEnabled ? View.VISIBLE : View.GONE);
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
        if (count == 0 || parentAlert != null && parentAlert.baseFragment instanceof ChatActivity && ((ChatActivity) parentAlert.baseFragment).isSecretChat()) {
            spoilerItem.setText(LocaleController.getString(R.string.EnablePhotoSpoiler));
            spoilerItem.setAnimatedIcon(R.raw.photo_spoiler);
            parentAlert.selectedMenuItem.hideSubItem(spoiler);
        } else {
            parentAlert.selectedMenuItem.showSubItem(spoiler);
        }
    }

    @Override
    void applyCaption(CharSequence text) {
        for (int a = 0; a < selectedPhotosOrder.size(); a++) {
            if (a == 0) {
                Object o = selectedPhotos.get(selectedPhotosOrder.get(a));
                if (o instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry1 = (MediaController.PhotoEntry) o;
                    photoEntry1.caption = text;
                    photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(new CharSequence[]{text}, false);
                } else if (o instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry1 = (MediaController.SearchImage) o;
                    photoEntry1.caption = text;
                    photoEntry1.entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(new CharSequence[]{text}, false);
                }
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
    void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.cameraInitied);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
    }

    @Override
    void onPause() {
        if (shutterButton == null) {
            return;
        }
        if (!requestingPermissions) {
            if (cameraView != null && shutterButton.getState() == ShutterButton.State.RECORDING) {
                resetRecordState();
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
                shutterButton.setState(ShutterButton.State.DEFAULT, true);
            }
            if (cameraOpened) {
                closeCamera(false);
            }
            hideCamera(true);
        } else {
            if (cameraView != null && shutterButton.getState() == ShutterButton.State.RECORDING) {
                shutterButton.setState(ShutterButton.State.DEFAULT, true);
            }
            requestingPermissions = false;
        }
    }

    @Override
    void onResume() {
        if (parentAlert.isShowing() && !parentAlert.isDismissed() && !PhotoViewer.getInstance().isVisible()) {
            checkCamera(false);
        }
    }

    @Override
    int getListTopPadding() {
        return gridView.getPaddingTop();
    }

    public int currentItemTop = 0;

    @Override
    int getCurrentItemTop() {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(currentItemTop = gridView.getPaddingTop());
            progressView.setTranslationY(0);
            return Integer.MAX_VALUE;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = AndroidUtilities.dp(7);
        if (top >= AndroidUtilities.dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        progressView.setTranslationY(newOffset + (getMeasuredHeight() - newOffset - AndroidUtilities.dp(50) - progressView.getMeasuredHeight()) / 2);
        gridView.setTopGlowOffset(newOffset);
        return currentItemTop = newOffset;
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(56);
    }

    @Override
    void checkColors() {
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
        String textColor = forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_dialogTextBlack;
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
    void onInit(boolean hasVideo, boolean hasPhoto, boolean hasDocuments) {
        mediaEnabled = hasVideo || hasPhoto;
        videoEnabled = hasVideo;
        photoEnabled = hasPhoto;
        documentsEnabled = hasDocuments;
        if (cameraView != null) {
            cameraView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraView.setEnabled(mediaEnabled);
        }
        if (cameraIcon != null) {
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
        }
        if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.avatarPicker == 0) {
            galleryAlbumEntry = MediaController.allMediaAlbumEntry;
            if (mediaEnabled) {
                progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
                progressView.setLottie(0, 0, 0);
            } else {
                TLRPC.Chat chat = ((ChatActivity) parentAlert.baseFragment).getCurrentChat();
                progressView.setLottie(R.raw.media_forbidden, 150, 150);
                if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) {
                    progressView.setText(LocaleController.getString("GlobalAttachMediaRestricted", R.string.GlobalAttachMediaRestricted));
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
            noGalleryPermissions = parentAlert.baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
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

        dropDown.setText(LocaleController.getString("ChatGallery", R.string.ChatGallery));

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
    boolean canScheduleMessages() {
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
    void onButtonsTranslationYUpdated() {
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
    void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
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
                gridView.scrollBy(0, (currentItemTop > AndroidUtilities.dp(8) ? paddingTop - currentItemTop : paddingTop));
            };
            gridView.post(setScrollY);
        }

        checkCameraViewPosition();

        resumeCameraPreview();
    }

    @Override
    void onShown() {
        isHidden = false;
        if (cameraView != null) {
            cameraView.setVisibility(VISIBLE);
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
    void onHideShowProgress(float progress) {
        if (cameraView != null) {
            cameraView.setAlpha(progress);
            cameraIcon.setAlpha(progress);
            if (progress != 0 && cameraView.getVisibility() != VISIBLE) {
                cameraView.setVisibility(VISIBLE);
                cameraIcon.setVisibility(VISIBLE);
            } else if (progress == 0 && cameraView.getVisibility() != INVISIBLE) {
                cameraView.setVisibility(INVISIBLE);
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
                CameraSession cameraSession = cameraView.getCameraSession();
                if (cameraSession != null) {
                    CameraController.getInstance().stopPreview(cameraSession);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void resumeCameraPreview() {
        try {
            checkCamera(false);
            if (cameraView != null) {
                CameraSession cameraSession = cameraView.getCameraSession();
                if (cameraSession != null) {
                    CameraController.getInstance().startPreview(cameraSession);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    void onHidden() {
        if (cameraView != null) {
            cameraView.setVisibility(GONE);
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

        itemSize = (availableWidth - AndroidUtilities.dp(6 * 2) - AndroidUtilities.dp(5 * 2)) / itemsPerRow;

        if (lastItemSize != itemSize) {
            lastItemSize = itemSize;
            AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
        }

        layoutManager.setSpanCount(Math.max(1, itemSize * itemsPerRow + AndroidUtilities.dp(5) * (itemsPerRow - 1)));
        int rows = (int) Math.ceil((adapter.getItemCount() - 1) / (float) itemsPerRow);
        int contentSize = rows * itemSize + (rows - 1) * AndroidUtilities.dp(5);
        int newSize = Math.max(0, availableHeight - contentSize - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(48 + 12));
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
        paddingTop -= AndroidUtilities.dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }
        if (gridView.getPaddingTop() != paddingTop) {
            gridView.setPadding(AndroidUtilities.dp(6), paddingTop, AndroidUtilities.dp(6), AndroidUtilities.dp(48));
        }
        dropDown.setTextSize(!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 18 : 20);
        ignoreLayout = false;
    }

    @Override
    boolean canDismissWithTouchOutside() {
        return !cameraOpened;
    }

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        super.onPanTransitionStart(keyboardVisible, contentHeight);
        checkCameraViewPosition();
        if (cameraView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cameraView.invalidateOutline();
            } else {
                cameraView.invalidate();
            }
        }
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
    }

    @Override
    void onContainerTranslationUpdated(float currentPanTranslationY) {
        this.currentPanTranslationY = currentPanTranslationY;
        checkCameraViewPosition();
        if (cameraView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cameraView.invalidateOutline();
            } else {
                cameraView.invalidate();
            }
        }
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }
        invalidate();
    }

    @Override
    void onOpenAnimationEnd() {
        checkCamera(parentAlert != null && parentAlert.baseFragment instanceof ChatActivity);
    }

    @Override
    void onDismissWithButtonClick(int item) {
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

    @Override
    public boolean onSheetKeyDown(int keyCode, KeyEvent event) {
        if (cameraOpened && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            shutterButton.getDelegate().shutterReleased();
            return true;
        }
        return false;
    }

    @Override
    public boolean onContainerViewTouchEvent(MotionEvent event) {
        if (cameraAnimationInProgress) {
            return true;
        } else if (cameraOpened) {
            return processTouchEvent(event);
        }
        return false;
    }

    @Override
    public boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == cameraIcon) {
            cameraIcon.measure(View.MeasureSpec.makeMeasureSpec(itemSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) (itemSize - cameraViewOffsetBottomY - cameraViewOffsetY), View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == cameraView) {
            if (cameraOpened && !cameraAnimationInProgress) {
                cameraView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height + parentAlert.getBottomInset(), View.MeasureSpec.EXACTLY));
                return true;
            }
        } else if (view == cameraPanel) {
            if (isPortrait) {
                cameraPanel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(126), View.MeasureSpec.EXACTLY));
            } else {
                cameraPanel.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(126), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        } else if (view == zoomControlView) {
            if (isPortrait) {
                zoomControlView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), View.MeasureSpec.EXACTLY));
            } else {
                zoomControlView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            cameraPhotoRecyclerViewIgnoreLayout = true;
            if (isPortrait) {
                cameraPhotoRecyclerView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), View.MeasureSpec.EXACTLY));
                if (cameraPhotoLayoutManager.getOrientation() != LinearLayoutManager.HORIZONTAL) {
                    cameraPhotoRecyclerView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
                    cameraPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    cameraAttachAdapter.notifyDataSetChanged();
                }
            } else {
                cameraPhotoRecyclerView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                if (cameraPhotoLayoutManager.getOrientation() != LinearLayoutManager.VERTICAL) {
                    cameraPhotoRecyclerView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                    cameraPhotoLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                    cameraAttachAdapter.notifyDataSetChanged();
                }
            }
            cameraPhotoRecyclerViewIgnoreLayout = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        int width = (right - left);
        int height = (bottom - top);
        boolean isPortrait = width < height;
        if (view == cameraPanel) {
            if (isPortrait) {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cameraPanel.layout(0, bottom - AndroidUtilities.dp(126 + 96), width, bottom - AndroidUtilities.dp(96));
                } else {
                    cameraPanel.layout(0, bottom - AndroidUtilities.dp(126), width, bottom);
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cameraPanel.layout(right - AndroidUtilities.dp(126 + 96), 0, right - AndroidUtilities.dp(96), height);
                } else {
                    cameraPanel.layout(right - AndroidUtilities.dp(126), 0, right, height);
                }
            }
            return true;
        } else if (view == zoomControlView) {
            if (isPortrait) {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    zoomControlView.layout(0, bottom - AndroidUtilities.dp(126 + 96 + 38 + 50), width, bottom - AndroidUtilities.dp(126 + 96 + 38));
                } else {
                    zoomControlView.layout(0, bottom - AndroidUtilities.dp(126 + 50), width, bottom - AndroidUtilities.dp(126));
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    zoomControlView.layout(right - AndroidUtilities.dp(126 + 96 + 38 + 50), 0, right - AndroidUtilities.dp(126 + 96 + 38), height);
                } else {
                    zoomControlView.layout(right - AndroidUtilities.dp(126 + 50), 0, right - AndroidUtilities.dp(126), height);
                }
            }
            return true;
        } else if (view == counterTextView) {
            int cx;
            int cy;
            if (isPortrait) {
                cx = (width - counterTextView.getMeasuredWidth()) / 2;
                cy = bottom - AndroidUtilities.dp(113 + 16 + 38);
                counterTextView.setRotation(0);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cy -= AndroidUtilities.dp(96);
                }
            } else {
                cx = right - AndroidUtilities.dp(113 + 16 + 38);
                cy = height / 2 + counterTextView.getMeasuredWidth() / 2;
                counterTextView.setRotation(-90);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cx -= AndroidUtilities.dp(96);
                }
            }
            counterTextView.layout(cx, cy, cx + counterTextView.getMeasuredWidth(), cy + counterTextView.getMeasuredHeight());
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            if (isPortrait) {
                int cy = height - AndroidUtilities.dp(88);
                view.layout(0, cy, view.getMeasuredWidth(), cy + view.getMeasuredHeight());
            } else {
                int cx = left + width - AndroidUtilities.dp(88);
                view.layout(cx, 0, cx + view.getMeasuredWidth(), view.getMeasuredHeight());
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
                if (selectedAlbumEntry == null) {
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
                            int rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius);
                            outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                        } else if (position == itemsPerRow - 1) {
                            int rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius);
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
                boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                if (added && parentAlert.maxSelectedPhotos >= 0 && selectedPhotos.size() >= parentAlert.maxSelectedPhotos) {
                    if (parentAlert.allowOrder && parentAlert.baseFragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
                        TLRPC.Chat chat = chatActivity.getCurrentChat();
                        if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                            if (alertOnlyOnce != 2) {
                                AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSelectSendError", R.string.SlowmodeSelectSendError), resourcesProvider).show();
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
                    if (parentAlert.avatarPicker != 0) {
                        cell.getCheckBox().setVisibility(GONE);
                    }

                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    if (photoEntry == null) {
                        return;
                    }
                    cell.setPhotoEntry(photoEntry, needCamera && selectedAlbumEntry == galleryAlbumEntry, position == getItemCount() - 1);
                    if (parentAlert.baseFragment instanceof ChatActivity && parentAlert.allowOrder) {
                        cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                    } else {
                        cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
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
                                int rad = AndroidUtilities.dp(8 * parentAlert.cornerRadius);
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
