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
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
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
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPermissionCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MediaRecorder.ChatMediaRecorder;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.AlbumButton;

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

    private int currentSelectedCount;

    private boolean isHidden;

    protected ChatMediaRecorder mediaRecorder;

    private final float[] cameraViewLocation = new float[2];

    private float cameraViewOffsetX;
    private float cameraViewOffsetY;
    private float cameraViewOffsetBottomY;

    private TextView counterTextView;
    private TextView tooltipTextView;
    protected PhotoAttachCameraCell cameraCell;
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
            hideCamera(false);
            setCurrentSpoilerVisible(-1, true);
        }

        @Override
        public void onPreClose() {
            setCurrentSpoilerVisible(-1, false);
        }

        @Override
        public void onClose() {
            showCamera();
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
                    //openCamera(true);
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
            openPhotoViewer(null, false, false);
        });

        tooltipTextView = new TextView(context);
        tooltipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tooltipTextView.setTextColor(0xffffffff);
        tooltipTextView.setText(LocaleController.getString(R.string.TapForVideo));
        tooltipTextView.setShadowLayer(dp(3.33333f), 0, dp(0.666f), 0x4c000000);
        tooltipTextView.setPadding(dp(6), 0, dp(6), 0);

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
            return;
        }
        if (cameraPhotos.isEmpty()) {
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
            arrayList = getAllPhotosArray();
            index = cameraPhotos.size() - 1;
        }
        if (parentAlert.getAvatarFor() != null && entry != null) {
            parentAlert.getAvatarFor().isVideo = entry.isVideo;
        }
        PhotoViewer.getInstance().openPhotoForSelect(arrayList, index, type, false, new BasePhotoProvider() {

            @Override
            public void onOpen() {
                closeCamera(false);
            }

            @Override
            public void onClose() {
                openCamera(false);
            }

            public void onEditModeChanged(boolean isEditMode) {

            }

            @Override
            public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                return null;
            }

            @Override
            public boolean cancelButtonPressed() {
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
                    parentAlert.delegate.didPressedButton(0, true, true, 0, 0, parentAlert.isCaptionAbove(), false);
                    return;
                }
                if (!mediaRecorder.isExpanded()) {
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
                        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) cameraPhotos.get(a);
                        if (entry.ttl > 0) {
                            continue;
                        }
                        AndroidUtilities.addMediaToGallery(entry.path);
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
        if (!parentAlert.destroyed && parentAlert.isShowing() && deviceHasGoodCamera && parentAlert.getBackDrawable().getAlpha() != 0) {
            showCamera();
        }
    }

    boolean cameraExpanded;

    private void openCamera(boolean animated) {
        if (mediaRecorder == null || parentAlert.isDismissed()) {
            return;
        }

        final float targetWidth = parentAlert.getContainer().getWidth() - parentAlert.getLeftInset() - parentAlert.getRightInset();
        final float targetHeight = parentAlert.getContainer().getHeight();
        final float targetX = 0;
        final float targetY = 0;

        notificationsLocker.lock();
        mediaRecorder.expandView(animated, new RectF(targetX, targetY, targetX + targetWidth, targetY + targetHeight), parentAlert.getContainer(), () -> {
            notificationsLocker.unlock();
            parentAlert.delegate.onCameraOpened();
        });

        AndroidUtilities.hideKeyboard(this);
        AndroidUtilities.setLightNavigationBar(parentAlert.getWindow(), false);
        parentAlert.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
    }

    public void closeCamera(boolean animated) {
        if (mediaRecorder == null || parentAlert.isDismissed()) {
            return;
        }

        mediaRecorder.shrinkView(animated);
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

        if (mediaRecorder == null) {
            mediaRecorder = new ChatMediaRecorder((Activity)getContext(), this, parentAlert, true, resourcesProvider);

            mediaRecorder.setDelegate(new ChatMediaRecorder.Delegate() {
                @Override
                public void send(MediaController.PhotoEntry entry) {
                    parentAlert.sent = true;
                    addToSelectedPhotos(entry, -1);
                    parentAlert.delegate.didPressedButton(7, true, false, 0, 0, parentAlert.isCaptionAbove(), false);
                    selectedPhotos.clear();
                    cameraPhotos.clear();
                    selectedPhotosOrder.clear();
                    selectedPhotos.clear();
                }
            });
            parentAlert.getContainer().addView(mediaRecorder.getView(), new LayoutParams(itemSize, itemSize));
        }

        mediaRecorder.showCamera();
        checkCameraViewPosition();

        if (mediaRecorder.isShrink()) {
            mediaRecorder.getView().setTranslationX(cameraViewLocation[0]);
            mediaRecorder.getView().setTranslationY(cameraViewLocation[1] + currentPanTranslationY);
        }
    }

    public void hideCamera(boolean async) {
        if (!deviceHasGoodCamera || mediaRecorder == null) {
            return;
        }
        if (async) {
            mediaRecorder.saveLastCameraBitmap(() -> {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = gridView.getChildAt(a);
                    if (child instanceof PhotoAttachCameraCell) {
                        child.setVisibility(View.VISIBLE);
                        ((PhotoAttachCameraCell) child).updateBitmap();
                        break;
                    }
                }
            });

            AndroidUtilities.runOnUIThread(() -> {
                parentAlert.getContainer().removeView(mediaRecorder.getView());
                mediaRecorder.close();
                mediaRecorder = null;
            }, 300);
        } else {
            parentAlert.getContainer().removeView(mediaRecorder.getView());
            mediaRecorder.close();
            mediaRecorder = null;
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

    protected void checkCameraViewPosition() {
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().stickerMakerView != null && PhotoViewer.getInstance().stickerMakerView.isThanosInProgress) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 21) {
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

        invalidateMediaRecorderView();

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

                if (newCameraViewOffsetY != cameraViewOffsetY) {
                    cameraViewOffsetY = newCameraViewOffsetY;
                    mediaRecorder.setCameraViewOffsetY((int) cameraViewOffsetY);

                    invalidateMediaRecorderView();
                }

                int containerHeight = parentAlert.getSheetContainer().getMeasuredHeight();
                maxY = (int) (containerHeight - parentAlert.buttonsRecyclerView.getMeasuredHeight() + parentAlert.buttonsRecyclerView.getTranslationY());
                if (parentAlert.mentionContainer != null) {
                    maxY -= parentAlert.mentionContainer.clipBottom() - dp(6);
                }

                if (topLocal + child.getMeasuredHeight() > maxY) {
                    cameraViewOffsetBottomY = Math.min(-dp(5), topLocal - maxY) + child.getMeasuredHeight();
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

            mediaRecorder.setCameraViewOffsetX((int)cameraViewOffsetX);
            mediaRecorder.setCameraViewOffsetY((int) cameraViewOffsetY);

            invalidateMediaRecorderView();
        }

        cameraViewLocation[0] = dp(-400);
        cameraViewLocation[1] = 0;

        applyCameraViewPosition();
    }

    private void applyCameraViewPosition() {
        if (mediaRecorder != null && mediaRecorder.getView() != null) {
            if (mediaRecorder.isShrink()) {
                mediaRecorder.getView().setTranslationX(cameraViewLocation[0]);
                mediaRecorder.getView().setTranslationY(cameraViewLocation[1] + currentPanTranslationY);
            }

            int finalWidth = itemSize;
            int finalHeight = itemSize;

            LayoutParams layoutParams;
            if (mediaRecorder != null && mediaRecorder.getView() != null && mediaRecorder.isShrink()) {
                layoutParams = (LayoutParams) mediaRecorder.getView().getLayoutParams();
                if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                    layoutParams.width = finalWidth;
                    layoutParams.height = finalHeight;
                    mediaRecorder.getView().setLayoutParams(layoutParams);
                    final LayoutParams layoutParamsFinal = layoutParams;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (mediaRecorder != null && mediaRecorder.getView() != null) {
                            mediaRecorder.getView().setLayoutParams(layoutParamsFinal);
                        }
                    });
                }
            }
        }
    }

    private void invalidateMediaRecorderView() {
        if (mediaRecorder == null || mediaRecorder.getView() == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaRecorder.getView().invalidateOutline();
        } else {
            mediaRecorder.getView().invalidate();
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
        if (mediaRecorder != null) {
            mediaRecorder.onPause();
        }
    }

    @Override
    public void onResume() {
        if (parentAlert.isShowing() && !parentAlert.isDismissed() && !PhotoViewer.getInstance().isVisible() && mediaRecorder != null) {
            mediaRecorder.onResume();
            checkCamera(false);
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
        int textColor = forceDarkTheme ? Theme.key_voipgroup_actionBarItems : Theme.key_dialogTextBlack;
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
    }

    @Override
    public void onShown() {
        isHidden = false;
        if (mediaRecorder != null) {
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
    public void onHide() {
        isHidden = true;
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                PhotoAttachCameraCell cell = (PhotoAttachCameraCell) child;
                child.setVisibility(View.VISIBLE);
                if (mediaRecorder != null) {
                    mediaRecorder.saveLastCameraBitmap(cell::updateBitmap);
                }

                break;
            }
        }

        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        headerAnimator = dropDown.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH).withEndAction(() -> dropDownContainer.setVisibility(GONE));
        headerAnimator.start();
    }

    public void pauseCamera(boolean pause) {
        if (needCamera && !noCameraPermissions) {
            if (pause) {
                if (mediaRecorder != null) {
                    hideCamera(true);
                }
            } else {
                showCamera();
            }
        }
    }

    @Override
    public void onHidden() {
        if (mediaRecorder != null && mediaRecorder.getView() != null) {
            mediaRecorder.getView().setVisibility(GONE);
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
        return !mediaRecorder.isExpanded();
    }

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        super.onPanTransitionStart(keyboardVisible, contentHeight);
        checkCameraViewPosition();
        invalidateMediaRecorderView();
    }

    @Override
    public void onContainerTranslationUpdated(float currentPanTranslationY) {
        this.currentPanTranslationY = currentPanTranslationY;
        checkCameraViewPosition();
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
        if (mediaRecorder != null) {
            if (mediaRecorder.isAnimating()) {
                return true;
            }
            if (mediaRecorder.isExpanded() && !parentAlert.sent) {
                if (!mediaRecorder.processBackPressed()) {
                    closeCamera(true);
                }
                return true;
            }
        }

        hideCamera(true);
        return false;
    }

    @Override
    public boolean onContainerViewTouchEvent(MotionEvent event) {
        if (mediaRecorder != null) {
            if (mediaRecorder.isAnimating()) {
                return true;
            } else if (mediaRecorder.isExpanded()) {
                return mediaRecorder.processTouchEvent(event);
            }

        }

        return false;
    }

    @Override
    public boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == cameraPhotoRecyclerView) {
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
        } else if (mediaRecorder != null && view == mediaRecorder.getView()) {
            if (mediaRecorder.isExpanded()) {
                mediaRecorder.getView().measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height + parentAlert.getBottomInset(), View.MeasureSpec.EXACTLY));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        int width = (right - left);
        int height = (bottom - top);
        boolean isPortrait = width < height;
        if (view == counterTextView) {
            int cx;
            int cy;
            if (isPortrait) {
                cx = (width - counterTextView.getMeasuredWidth()) / 2;
                cy = bottom - dp(113 + 16 + 38);
                counterTextView.setRotation(0);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cy -= dp(96);
                }
            } else {
                cx = right - dp(113 + 16 + 38);
                cy = height / 2 + counterTextView.getMeasuredWidth() / 2;
                counterTextView.setRotation(-90);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cx -= dp(96);
                }
            }
            counterTextView.layout(cx, cy, cx + counterTextView.getMeasuredWidth(), cy + counterTextView.getMeasuredHeight());
            return true;
        } else if (view == cameraPhotoRecyclerView) {
            if (isPortrait) {
                int cy = height - dp(88);
                view.layout(0, cy, view.getMeasuredWidth(), cy + view.getMeasuredHeight());
            } else {
                int cx = left + width - dp(88);
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
                    if (parentAlert.avatarPicker != 0 || parentAlert.storyMediaPicker) {
                        cell.getCheckBox().setVisibility(GONE);
                    } else {
                        cell.getCheckBox().setVisibility(VISIBLE);
                    }

                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    if (photoEntry == null) {
                        return;
                    }
                    cell.setPhotoEntry(photoEntry, selectedPhotos.size() > 1, needCamera && selectedAlbumEntry == galleryAlbumEntry, position == getItemCount() - 1);
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
                    if (mediaRecorder != null && mediaRecorder.isInited() && !isHidden) {
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

    private static void log(String message) {
        Log.i("kirillNay", message);
    }

    private static void logTrace() {
        logTrace(3);
    }

    private static void logTrace(int level) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(trace.length, level); i++) {
            builder.append(trace[i]).append("\n");
        }

        log(builder.toString());
    }
}
