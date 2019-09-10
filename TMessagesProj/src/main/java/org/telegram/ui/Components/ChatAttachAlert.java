/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPermissionCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import androidx.annotation.Keep;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatAttachAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, BottomSheet.BottomSheetDelegateInterface {

    public interface ChatAttachViewDelegate {
        void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate);

        View getRevealView();

        void didSelectBot(TLRPC.User user);

        void onCameraOpened();

        void needEnterComment();
    }

    private class InnerAnimator {
        private AnimatorSet animatorSet;
        private float startRadius;
    }

    private BaseFragment baseFragment;
    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;
    private ActionBarMenuSubItem[] itemCells;

    private View shadow;

    private FrameLayout frameLayout2;
    private EditTextEmoji commentTextView;
    private FrameLayout writeButtonContainer;
    private ImageView writeButton;
    private Drawable writeButtonDrawable;
    private View selectedCountView;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AnimatorSet animatorSet;

    private MediaController.AlbumEntry selectedAlbumEntry;
    private MediaController.AlbumEntry galleryAlbumEntry;

    private float cornerRadius = 1.0f;

    private ActionBar actionBar;
    private ActionBarMenuItem dropDownContainer;
    private TextView dropDown;
    private Drawable dropDownDrawable;
    private View actionBarShadow;
    private AnimatorSet actionBarAnimation;
    private AnimatorSet menuAnimator;
    private ActionBarMenuItem selectedMenuItem;
    private EmptyTextProgressView progressView;
    private TextView selectedTextView;
    private boolean menuShowed;
    private int currentSelectedCount;
    private ArrayList<MediaController.AlbumEntry> dropDownAlbums;
    private Drawable cameraDrawable;
    private SizeNotifierFrameLayout sizeNotifierFrameLayout;

    private boolean enterCommentEventSent;

    private RecyclerListView gridView;
    private GridLayoutManager layoutManager;
    private PhotoAttachAdapter adapter;
    private RecyclerViewItemRangeSelector itemRangeSelector;
    private int gridExtraSpace;
    private boolean shouldSelect;
    private int alertOnlyOnce;

    private RecyclerListView cameraPhotoRecyclerView;
    private LinearLayoutManager cameraPhotoLayoutManager;
    private PhotoAttachAdapter cameraAttachAdapter;

    private RecyclerListView buttonsRecyclerView;
    private LinearLayoutManager buttonsLayoutManager;
    private ButtonsAdapter buttonsAdapter;

    private boolean cameraPhotoRecyclerViewIgnoreLayout;

    private boolean requestingPermissions;

    private MessageObject editingMessageObject;

    private boolean buttonPressed;

    private int currentAccount = UserConfig.selectedAccount;

    private boolean mediaEnabled = true;
    private boolean pollsEnabled = true;

    private AnimatorSet cameraInitAnimation;
    private CameraView cameraView;
    private FrameLayout cameraIcon;
    private TextView recordTime;
    private ImageView[] flashModeButton = new ImageView[2];
    private boolean flashAnimationInProgress;
    private int[] cameraViewLocation = new int[2];
    private int[] viewPosition = new int[2];
    private int cameraViewOffsetX;
    private int cameraViewOffsetY;
    private int cameraViewOffsetBottomY;
    private boolean cameraOpened;
    private boolean cameraInitied;
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
    private boolean mediaCaptured;
    private static boolean mediaFromExternalCamera;
    private static ArrayList<Object> cameraPhotos = new ArrayList<>();
    private static HashMap<Object, Object> selectedPhotos = new HashMap<>();
    private static ArrayList<Object> selectedPhotosOrder = new ArrayList<>();
    private static int lastImageId = -1;
    private boolean cancelTakingPhotos;

    private int maxSelectedPhotos = -1;
    private boolean allowOrder = true;
    private boolean openWithFrontFaceCamera;

    private float pinchStartDistance;
    private float cameraZoom;
    private boolean zooming;
    private boolean zoomWas;
    private android.graphics.Rect hitRect = new Rect();

    private float lastY;
    private boolean pressed;
    private boolean maybeStartDraging;
    private boolean dragging;

    private int itemSize = AndroidUtilities.dp(80);
    private int lastItemSize = itemSize;
    private int attachItemSize = AndroidUtilities.dp(85);
    private int itemsPerRow = 3;

    private boolean deviceHasGoodCamera;
    private boolean noCameraPermissions;
    private boolean noGalleryPermissions;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private boolean loading = true;

    private ChatAttachViewDelegate delegate;

    private int scrollOffsetY;

    private boolean paused;

    private final static int group = 0;
    private final static int compress = 1;

    private class BasePhotoProvider extends PhotoViewer.EmptyPhotoViewerProvider {
        @Override
        public boolean isPhotoChecked(int index) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            return photoEntry != null && selectedPhotos.containsKey(photoEntry.imageId);
        }

        @Override
        public int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo) {
            if (maxSelectedPhotos >= 0 && selectedPhotos.size() >= maxSelectedPhotos && !isPhotoChecked(index)) {
                return -1;
            }
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry == null) {
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
                        if (baseFragment instanceof ChatActivity && allowOrder) {
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
                        if (baseFragment instanceof ChatActivity && allowOrder) {
                            ((PhotoAttachPhotoCell) view).setChecked(num, add, false);
                        } else {
                            ((PhotoAttachPhotoCell) view).setChecked(-1, add, false);
                        }
                        break;
                    }
                }
            }
            updatePhotosButton(add ? 1 : 2);
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

    private PhotoViewer.PhotoViewerProvider photoViewerProvider = new BasePhotoProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                int[] coords = new int[2];
                cell.getImageView().getLocationInWindow(coords);
                if (Build.VERSION.SDK_INT < 26) {
                    coords[0] -= getLeftInset();
                }
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = gridView;
                object.imageReceiver = cell.getImageView().getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.getScale();
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
        public boolean cancelButtonPressed() {
            return false;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry != null) {
                photoEntry.editedInfo = videoEditedInfo;
            }
            if (selectedPhotos.isEmpty() && photoEntry != null) {
                addToSelectedPhotos(photoEntry, -1);
            }
            applyCaption();
            delegate.didPressedButton(7, true, notify, scheduleDate);
        }
    };

    private void updateCheckedPhotoIndices() {
        if (!(baseFragment instanceof ChatActivity)) {
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
        if (position < selectedAlbumEntry.photos.size()) {
            return selectedAlbumEntry.photos.get(position);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<Object> getAllPhotosArray() {
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

    private class AttachButton extends FrameLayout {

        private TextView textView;
        private ImageView imageView;

        public AttachButton(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                imageView.setImageDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1, AndroidUtilities.dp(25)));
            }
            addView(imageView, LayoutHelper.createFrame(50, 50, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));

            textView = new TextView(context);
            textView.setMaxLines(2);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setLineSpacing(-AndroidUtilities.dp(2), 1.0f);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 66, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(92), MeasureSpec.EXACTLY));
        }

        public void setTextAndIcon(CharSequence text, Drawable drawable) {
            textView.setText(text);
            imageView.setBackgroundDrawable(drawable);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private class AttachBotButton extends FrameLayout {

        private BackupImageView imageView;
        private TextView nameTextView;
        private AvatarDrawable avatarDrawable = new AvatarDrawable();

        private TLRPC.User currentUser;

        public AttachBotButton(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(25));
            addView(imageView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

            if (Build.VERSION.SDK_INT >= 21) {
                View selector = new View(context);
                selector.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1, AndroidUtilities.dp(25)));
                addView(selector, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
            }

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            nameTextView.setLines(1);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 66, 6, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
        }

        public void setUser(TLRPC.User user) {
            if (user == null) {
                return;
            }
            nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            currentUser = user;
            nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarDrawable.setInfo(user);
            imageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);
            requestLayout();
        }
    }

    public ChatAttachAlert(Context context, final BaseFragment parentFragment) {
        super(context, false, 1);
        openInterpolator = new OvershootInterpolator(0.7f);
        baseFragment = parentFragment;
        setDelegate(this);
        checkCamera(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.cameraInitied);

        cameraDrawable = context.getResources().getDrawable(R.drawable.instant_camera).mutate();

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private int lastNotifyWidth;
            private RectF rect = new RectF();
            private boolean ignoreLayout;
            private float initialTranslationY;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (cameraAnimationInProgress) {
                    return true;
                } else if (cameraOpened) {
                    return processTouchEvent(ev);
                } else if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY - AndroidUtilities.dp(36) && actionBar.getAlpha() == 0.0f) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (cameraAnimationInProgress) {
                    return true;
                } else if (cameraOpened) {
                    return processTouchEvent(event);
                }
                return !isDismissed() && super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();
                int keyboardSize = getKeyboardHeight();
                if (!AndroidUtilities.isInMultiwindow && keyboardSize <= AndroidUtilities.dp(20)) {
                    availableHeight -= commentTextView.getEmojiPadding();
                }
                int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - backgroundPaddingLeft * 2;
                if (AndroidUtilities.isTablet()) {
                    itemsPerRow = 4;
                    selectedMenuItem.setAdditionalYOffset(-AndroidUtilities.dp(3));
                } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    itemsPerRow = 4;
                    selectedMenuItem.setAdditionalYOffset(0);
                } else {
                    itemsPerRow = 3;
                    selectedMenuItem.setAdditionalYOffset(-AndroidUtilities.dp(3));
                }
                LayoutParams layoutParams = (LayoutParams) gridView.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                ignoreLayout = true;
                itemSize = (availableWidth - AndroidUtilities.dp(6 * 2) - AndroidUtilities.dp(5 * 2)) / itemsPerRow;
                int newSize = availableWidth / Math.min(4, buttonsAdapter.getItemCount());
                if (attachItemSize != newSize) {
                    attachItemSize = newSize;
                    AndroidUtilities.runOnUIThread(() -> buttonsAdapter.notifyDataSetChanged());
                }
                if (lastItemSize != itemSize) {
                    lastItemSize = itemSize;
                    AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
                }
                dropDown.setTextSize(!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 18 : 20);
                layoutManager.setSpanCount(itemSize * itemsPerRow + AndroidUtilities.dp(5) * (itemsPerRow - 1));
                int rows = (int) Math.ceil((adapter.getItemCount() - 1) / (float) itemsPerRow);
                int contentSize = rows * itemSize + (rows - 1) * AndroidUtilities.dp(5);
                newSize = Math.max(0, availableHeight - contentSize - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(48 + 12));
                if (gridExtraSpace != newSize) {
                    gridExtraSpace = newSize;
                    adapter.notifyDataSetChanged();
                }
                int padding;
                if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    padding = (availableHeight / 6);
                } else {
                    padding = (availableHeight / 5 * 2);
                }
                if (gridView.getPaddingTop() != padding) {
                    gridView.setPadding(AndroidUtilities.dp(6), padding, AndroidUtilities.dp(6), AndroidUtilities.dp(48));
                }
                ignoreLayout = false;
                onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                widthSize -= backgroundPaddingLeft * 2;

                int keyboardSize = getKeyboardHeight();
                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        heightSize -= commentTextView.getEmojiPadding();
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                } else {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (lastNotifyWidth != r - l) {
                    lastNotifyWidth = r - l;
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                }
                final int count = getChildCount();

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight() - backgroundPaddingLeft;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + getKeyboardHeight() - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
                updateLayout(false);
                checkCameraViewPosition();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int offset = AndroidUtilities.dp(13) + (selectedMenuItem != null ? AndroidUtilities.dp(selectedMenuItem.getAlpha() * 26) : 0);
                int top = scrollOffsetY - backgroundPaddingTop - offset;
                if (currentSheetAnimationType == 1) {
                    top += gridView.getTranslationY();
                }
                int y = top + AndroidUtilities.dp(20);

                int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
                float rad = 1.0f;

                if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                    float toMove = offset + AndroidUtilities.dp(11 - 7);
                    float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);
                    float availableToMove = ActionBar.getCurrentActionBarHeight() - toMove;

                    int diff = (int) (availableToMove * moveProgress);
                    top -= diff;
                    y -= diff;
                    height += diff;
                    rad = 1.0f - moveProgress;
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    y += AndroidUtilities.statusBarHeight;
                    height -= AndroidUtilities.statusBarHeight;
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (rad != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                }

                if ((selectedMenuItem == null || selectedMenuItem.getAlpha() != 1.0f) && rad != 0) {
                    float alphaProgress = selectedMenuItem == null ? 1.0f : 1.0f - selectedMenuItem.getAlpha();
                    int w = AndroidUtilities.dp(36);
                    rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                    int color = Theme.getColor(Theme.key_sheet_scrollUp);
                    int alpha = Color.alpha(color);
                    Theme.dialogs_onlineCirclePaint.setColor(color);
                    Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                }

                int color1 = Theme.getColor(Theme.key_dialogBackground);
                int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
            }

            @Override
            public void setTranslationY(float translationY) {
                if (currentSheetAnimationType == 0) {
                    initialTranslationY = translationY;
                }
                if (currentSheetAnimationType == 1) {
                    if (translationY < 0) {
                        gridView.setTranslationY(translationY);
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
                        translationY = 0;
                        buttonsRecyclerView.setTranslationY(0);
                    } else {
                        gridView.setTranslationY(0);
                        buttonsRecyclerView.setTranslationY(-translationY + buttonsRecyclerView.getMeasuredHeight() * (translationY / initialTranslationY));
                    }
                }
                super.setTranslationY(translationY);
                checkCameraViewPosition();
            }
        };
        containerView = sizeNotifierFrameLayout;
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        selectedTextView = new TextView(context);
        selectedTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        selectedTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        selectedTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        selectedTextView.setVisibility(View.INVISIBLE);
        selectedTextView.setAlpha(0.0f);
        containerView.addView(selectedTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 48, 0));

        actionBar = new ActionBar(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
                if (frameLayout2 != null && buttonsRecyclerView != null) {
                    if (frameLayout2.getTag() == null) {
                        buttonsRecyclerView.setAlpha(1.0f - alpha);
                        shadow.setAlpha(1.0f - alpha);
                        buttonsRecyclerView.setTranslationY(AndroidUtilities.dp(44) * alpha);
                        frameLayout2.setTranslationY(AndroidUtilities.dp(48) * alpha);
                        shadow.setTranslationY(AndroidUtilities.dp(92) * alpha);
                    } else {
                        float value = alpha == 0.0f ? 1.0f : 0.0f;
                        if (buttonsRecyclerView.getAlpha() != value) {
                            buttonsRecyclerView.setAlpha(value);
                        }
                    }
                }
            }
        };
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
        actionBar.setOccupyStatusBar(false);
        actionBar.setAlpha(0.0f);
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                } else {
                    if (id == group || id == compress) {
                        if (maxSelectedPhotos > 0 && selectedPhotosOrder.size() > 1 && baseFragment instanceof ChatActivity) {
                            ChatActivity chatActivity = (ChatActivity) baseFragment;
                            TLRPC.Chat chat = chatActivity.getCurrentChat();
                            if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                                AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSendError", R.string.SlowmodeSendError)).show();
                                return;
                            }
                        }
                    }
                    if (id == group) {
                        if (editingMessageObject == null && parentFragment instanceof ChatActivity && ((ChatActivity) parentFragment).isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(getContext(), UserObject.isUserSelf(((ChatActivity) parentFragment).getCurrentUser()), (notify, scheduleDate) -> {
                                applyCaption();
                                delegate.didPressedButton(7, false, notify, scheduleDate);
                            });
                        } else {
                            applyCaption();
                            delegate.didPressedButton(7, false, true, 0);
                        }
                    } else if (id == compress) {
                        if (editingMessageObject == null && parentFragment instanceof ChatActivity && ((ChatActivity) parentFragment).isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(getContext(), UserObject.isUserSelf(((ChatActivity) parentFragment).getCurrentUser()), (notify, scheduleDate) -> {
                                applyCaption();
                                delegate.didPressedButton(4, true, notify, scheduleDate);
                            });
                        } else {
                            applyCaption();
                            delegate.didPressedButton(4, true, true, 0);
                        }
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
            }
        });

        selectedMenuItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_dialogTextBlack)) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                updateSelectedPosition();
                containerView.invalidate();
            }
        };
        selectedMenuItem.setLongClickEnabled(false);
        selectedMenuItem.setIcon(R.drawable.ic_ab_other);
        selectedMenuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        selectedMenuItem.addSubItem(group, LocaleController.getString("SendWithoutGrouping", R.string.SendWithoutGrouping));
        selectedMenuItem.addSubItem(compress, LocaleController.getString("SendWithoutCompression", R.string.SendWithoutCompression));
        selectedMenuItem.setVisibility(View.INVISIBLE);
        selectedMenuItem.setAlpha(0.0f);
        selectedMenuItem.setSubMenuOpenSide(2);
        selectedMenuItem.setDelegate(id -> actionBar.getActionBarMenuOnItemClick().onItemClick(id));
        selectedMenuItem.setAdditionalYOffset(AndroidUtilities.dp(72));
        selectedMenuItem.setTranslationX(AndroidUtilities.dp(6));
        selectedMenuItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6));
        containerView.addView(selectedMenuItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
        selectedMenuItem.setOnClickListener(v -> selectedMenuItem.toggleSubMenu());

        gridView = new RecyclerListView(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                containerView.invalidate();
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < scrollOffsetY - AndroidUtilities.dp(44)) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < scrollOffsetY - AndroidUtilities.dp(44)) {
                    return false;
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        gridView.setAdapter(adapter = new PhotoAttachAdapter(context, true));
        gridView.setClipToPadding(false);
        gridView.setItemAnimator(null);
        gridView.setLayoutAnimation(null);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 11, 0, 0));
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (gridView.getChildCount() <= 0) {
                    return;
                }
                updateLayout(true);
                checkCameraViewPosition();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13) + (selectedMenuItem != null ? AndroidUtilities.dp(selectedMenuItem.getAlpha() * 26) : 0);
                    int top = scrollOffsetY - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        View child = gridView.getChildAt(0);
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
        gridView.setOnItemClickListener((view, position) -> {
            if (!mediaEnabled || baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                if (position == 0 && noCameraPermissions) {
                    try {
                        baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 18);
                    } catch (Exception ignore) {

                    }
                    return;
                } else if (noGalleryPermissions) {
                    try {
                        baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                    } catch (Exception ignore) {

                    }
                    return;
                }
            }
            if (position != 0 || selectedAlbumEntry != galleryAlbumEntry) {
                if (selectedAlbumEntry == galleryAlbumEntry) {
                    position--;
                }
                ArrayList<Object> arrayList = getAllPhotosArray();
                if (position < 0 || position >= arrayList.size()) {
                    return;
                }
                PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                PhotoViewer.getInstance().setParentAlert(ChatAttachAlert.this);
                PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                ChatActivity chatActivity;
                int type;
                if (baseFragment instanceof ChatActivity) {
                    chatActivity = (ChatActivity) baseFragment;
                    type = 0;
                } else {
                    type = 4;
                    chatActivity = null;
                }
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, type, photoViewerProvider, chatActivity);
                AndroidUtilities.hideKeyboard(baseFragment.getFragmentView().findFocus());
            } else {
                if (SharedConfig.inappCamera) {
                    openCamera(true);
                } else {
                    if (delegate != null) {
                        delegate.didPressedButton(0, false, true, 0);
                    }
                }
            }
        });
        gridView.setOnItemLongClickListener((view, position) -> {
            if (position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (delegate != null) {
                    delegate.didPressedButton(0, false, true, 0);
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
                gridView.hideSelector();
            }
        });
        gridView.addOnItemTouchListener(itemRangeSelector);

        ActionBarMenu menu = actionBar.createMenu();

        dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0);
        dropDownContainer.setSubMenuOpenSide(1);
        actionBar.addView(dropDownContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
        dropDownContainer.setOnClickListener(view -> dropDownContainer.toggleSubMenu());

        dropDown = new TextView(context);
        dropDown.setGravity(Gravity.LEFT);
        dropDown.setSingleLine(true);
        dropDown.setLines(1);
        dropDown.setMaxLines(1);
        dropDown.setEllipsize(TextUtils.TruncateAt.END);
        dropDown.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        dropDown.setText(LocaleController.getString("ChatGallery", R.string.ChatGallery));
        dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
        dropDownDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));

        progressView = new EmptyTextProgressView(context);
        progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        progressView.setOnTouchListener(null);
        progressView.setTextSize(20);
        containerView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }

        shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 92));

        buttonsRecyclerView = new RecyclerListView(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                checkCameraViewPosition();
            }
        };
        buttonsRecyclerView.setAdapter(buttonsAdapter = new ButtonsAdapter(context));
        buttonsRecyclerView.setLayoutManager(buttonsLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        buttonsRecyclerView.setVerticalScrollBarEnabled(false);
        buttonsRecyclerView.setHorizontalScrollBarEnabled(false);
        buttonsRecyclerView.setItemAnimator(null);
        buttonsRecyclerView.setLayoutAnimation(null);
        buttonsRecyclerView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        buttonsRecyclerView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        containerView.addView(buttonsRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 92, Gravity.BOTTOM | Gravity.LEFT));
        buttonsRecyclerView.setOnItemClickListener((view, position) -> {
            if (view instanceof AttachButton) {
                AttachButton attachButton = (AttachButton) view;
                delegate.didPressedButton((Integer) attachButton.getTag(), true, true, 0);
            } else if (view instanceof AttachBotButton) {
                AttachBotButton button = (AttachBotButton) view;
                delegate.didSelectBot(button.currentUser);
                dismiss();
            }
        });
        buttonsRecyclerView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof AttachBotButton) {
                AttachBotButton button = (AttachBotButton) view;
                if (baseFragment == null || button.currentUser == null) {
                    return false;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.formatString("ChatHintsDelete", R.string.ChatHintsDelete, ContactsController.formatName(button.currentUser.first_name, button.currentUser.last_name)));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> MediaDataController.getInstance(currentAccount).removeInline(button.currentUser.id));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.show();
                return true;
            }
            return false;
        });

        frameLayout2 = new FrameLayout(context);
        frameLayout2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        frameLayout2.setVisibility(View.INVISIBLE);
        frameLayout2.setAlpha(0.0f);
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener((v, event) -> true);

        commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (!enterCommentEventSent) {
                    delegate.needEnterComment();
                    ChatAttachAlert.this.setFocusable(true);
                    enterCommentEventSent = true;
                    AndroidUtilities.runOnUIThread(() -> commentTextView.openKeyboard());
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(MessagesController.getInstance(UserConfig.selectedAccount).maxCaptionLength);
        commentTextView.setFilters(inputFilters);
        commentTextView.setHint(LocaleController.getString("AddCaption", R.string.AddCaption));
        commentTextView.onResume();
        EditTextBoldCursor editText = commentTextView.getEditText();
        editText.setMaxLines(1);
        editText.setSingleLine(true);
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 84, 0));

        writeButtonContainer = new FrameLayout(context);
        writeButtonContainer.setVisibility(View.INVISIBLE);
        writeButtonContainer.setScaleX(0.2f);
        writeButtonContainer.setScaleY(0.2f);
        writeButtonContainer.setAlpha(0.0f);
        writeButtonContainer.setContentDescription(LocaleController.getString("Send", R.string.Send));
        containerView.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 10));
        writeButtonContainer.setOnClickListener(v -> {
            if (editingMessageObject == null && parentFragment instanceof ChatActivity && ((ChatActivity) parentFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), UserObject.isUserSelf(((ChatActivity) parentFragment).getCurrentUser()), this::sendPressed);
            } else {
                sendPressed(true, 0);
            }
        });
        writeButtonContainer.setOnLongClickListener(view -> {
            if (!(baseFragment instanceof ChatActivity) || editingMessageObject != null) {
                return false;
            }
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            TLRPC.Chat chat = chatActivity.getCurrentChat();
            TLRPC.User user = chatActivity.getCurrentUser();
            if (chatActivity.getCurrentEncryptedChat() != null || chatActivity.isInScheduleMode()) {
                return false;
            }

            sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
            sendPopupLayout.setAnimationEnabled(false);
            sendPopupLayout.setOnTouchListener(new View.OnTouchListener() {

                private android.graphics.Rect popupRect = new android.graphics.Rect();

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            v.getHitRect(popupRect);
                            if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                sendPopupWindow.dismiss();
                            }
                        }
                    }
                    return false;
                }
            });
            sendPopupLayout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                    sendPopupWindow.dismiss();
                }
            });
            sendPopupLayout.setShowedFromBotton(false);

            itemCells = new ActionBarMenuSubItem[2];
            int i = 0;
            for (int a = 0; a < 2; a++) {
                if (a == 0) {
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
                        continue;
                    }
                } else if (a == 1 && UserObject.isUserSelf(user)) {
                    continue;
                }
                int num = a;
                itemCells[a] = new ActionBarMenuSubItem(getContext());
                if (num == 0) {
                    if (UserObject.isUserSelf(user)) {
                        itemCells[a].setTextAndIcon(LocaleController.getString("SetReminder", R.string.SetReminder), R.drawable.msg_schedule);
                    } else {
                        itemCells[a].setTextAndIcon(LocaleController.getString("ScheduleMessage", R.string.ScheduleMessage), R.drawable.msg_schedule);
                    }
                } else if (num == 1) {
                    itemCells[a].setTextAndIcon(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound), R.drawable.input_notify_off);
                }
                itemCells[a].setMinimumWidth(AndroidUtilities.dp(196));

                sendPopupLayout.addView(itemCells[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 48 * i, 0, 0));
                itemCells[a].setOnClickListener(v -> {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                    if (num == 0) {
                        AlertsCreator.createScheduleDatePickerDialog(getContext(), UserObject.isUserSelf(user), this::sendPressed);
                    } else if (num == 1) {
                        sendPressed(true, 0);
                    }
                });
                i++;
            }

            sendPopupWindow = new ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            sendPopupWindow.setAnimationEnabled(false);
            sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
            sendPopupWindow.setOutsideTouchable(true);
            sendPopupWindow.setClippingEnabled(true);
            sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            sendPopupWindow.getContentView().setFocusableInTouchMode(true);

            sendPopupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
            sendPopupWindow.setFocusable(true);
            int[] location = new int[2];
            view.getLocationInWindow(location);
            sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + AndroidUtilities.dp(8), location[1] - sendPopupLayout.getMeasuredHeight() - AndroidUtilities.dp(2));
            sendPopupWindow.dimBehind();
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

            return false;
        });

        writeButton = new ImageView(context);
        writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_dialogFloatingButton), Theme.getColor(Theme.key_dialogFloatingButtonPressed));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, writeButtonDrawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            writeButtonDrawable = combinedDrawable;
        }
        writeButton.setBackgroundDrawable(writeButtonDrawable);
        writeButton.setImageResource(R.drawable.attach_send);
        writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            writeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.LEFT | Gravity.TOP, Build.VERSION.SDK_INT >= 21 ? 2 : 0, 0, 0, 0));

        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        selectedCountView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                String text = String.format("%d", Math.max(1, selectedPhotosOrder.size()));
                int textSize = (int) Math.ceil(textPaint.measureText(text));
                int size = Math.max(AndroidUtilities.dp(16) + textSize, AndroidUtilities.dp(24));
                int cx = getMeasuredWidth() / 2;
                int cy = getMeasuredHeight() / 2;

                textPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBoxCheck));
                paint.setColor(Theme.getColor(Theme.key_dialogBackground));
                rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

                paint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
                rect.set(cx - size / 2 + AndroidUtilities.dp(2), AndroidUtilities.dp(2), cx + size / 2 - AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);

                canvas.drawText(text, cx - textSize / 2, AndroidUtilities.dp(16.2f), textPaint);
            }
        };
        selectedCountView.setAlpha(0.0f);
        selectedCountView.setScaleX(0.2f);
        selectedCountView.setScaleY(0.2f);
        containerView.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -8, 9));

        recordTime = new TextView(context);
        recordTime.setBackgroundResource(R.drawable.system);
        recordTime.getBackground().setColorFilter(new PorterDuffColorFilter(0x66000000, PorterDuff.Mode.MULTIPLY));
        recordTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        recordTime.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        recordTime.setAlpha(0.0f);
        recordTime.setTextColor(0xffffffff);
        recordTime.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
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
                if (!(baseFragment instanceof ChatActivity) || mediaCaptured || takingPhoto || baseFragment == null || baseFragment.getParentActivity() == null || cameraView == null) {
                    return false;
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestingPermissions = true;
                        baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                        return false;
                    }
                }
                for (int a = 0; a < 2; a++) {
                    flashModeButton[a].setAlpha(0.0f);
                }
                switchCameraButton.setAlpha(0.0f);
                tooltipTextView.setAlpha(0.0f);
                outputFile = AndroidUtilities.generateVideoPath(baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).isSecretChat());
                recordTime.setAlpha(1.0f);
                recordTime.setText(String.format("%02d:%02d", 0, 0));
                videoRecordTime = 0;
                videoRecordRunnable = () -> {
                    if (videoRecordRunnable == null) {
                        return;
                    }
                    videoRecordTime++;
                    recordTime.setText(String.format("%02d:%02d", videoRecordTime / 60, videoRecordTime % 60));
                    AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000);
                };
                AndroidUtilities.lockOrientation(parentFragment.getParentActivity());
                CameraController.getInstance().recordVideo(cameraView.getCameraSession(), outputFile, (thumbPath, duration) -> {
                    if (outputFile == null || baseFragment == null) {
                        return;
                    }
                    mediaFromExternalCamera = false;
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, outputFile.getAbsolutePath(), 0, true);
                    photoEntry.duration = (int) duration;
                    photoEntry.thumbPath = thumbPath;
                    openPhotoViewer(photoEntry, false, false);
                }, () -> AndroidUtilities.runOnUIThread(videoRecordRunnable, 1000));
                shutterButton.setState(ShutterButton.State.RECORDING, true);
                return true;
            }

            @Override
            public void shutterCancel() {
                if (mediaCaptured) {
                    return;
                }
                if (outputFile != null) {
                    outputFile.delete();
                    outputFile = null;
                }
                resetRecordState();
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), true);
            }

            @Override
            public void shutterReleased() {
                if (takingPhoto || cameraView == null || mediaCaptured || cameraView.getCameraSession() == null) {
                    return;
                }
                mediaCaptured = true;
                if (shutterButton.getState() == ShutterButton.State.RECORDING) {
                    resetRecordState();
                    CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
                    shutterButton.setState(ShutterButton.State.DEFAULT, true);
                    return;
                }
                final File cameraFile = AndroidUtilities.generatePicturePath(baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).isSecretChat());
                final boolean sameTakePictureOrientation = cameraView.getCameraSession().isSameTakePictureOrientation();
                cameraView.getCameraSession().setFlipFront(parentFragment instanceof ChatActivity);
                takingPhoto = CameraController.getInstance().takePicture(cameraFile, cameraView.getCameraSession(), () -> {
                    takingPhoto = false;
                    if (cameraFile == null || baseFragment == null) {
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
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, cameraFile.getAbsolutePath(), orientation, false);
                    photoEntry.canDeleteAfter = true;
                    openPhotoViewer(photoEntry, sameTakePictureOrientation, false);
                });
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
            if (takingPhoto || cameraView == null || !cameraView.isInitied()) {
                return;
            }
            cameraInitied = false;
            cameraView.switchCamera();
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
                if (flashAnimationInProgress || cameraView == null || !cameraView.isInitied() || !cameraOpened) {
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
                animatorSet.setDuration(200);
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

        cameraPhotoRecyclerView = new RecyclerListView(context) {
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

    @Override
    public void show() {
        super.show();
        buttonPressed = false;
    }

    public void setEditingMessageObject(MessageObject messageObject) {
        if (editingMessageObject == messageObject) {
            return;
        }
        editingMessageObject = messageObject;
        if (editingMessageObject != null) {
            maxSelectedPhotos = 1;
            allowOrder = false;
        } else {
            maxSelectedPhotos = -1;
            allowOrder = true;
        }
        buttonsAdapter.notifyDataSetChanged();
    }

    public MessageObject getEditingMessageObject() {
        return editingMessageObject;
    }

    private void applyCaption() {
        if (commentTextView.length() <= 0) {
            return;
        }
        int imageId = (Integer) selectedPhotosOrder.get(0);
        Object entry = selectedPhotos.get(imageId);
        if (entry instanceof MediaController.PhotoEntry) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
            photoEntry.caption = commentTextView.getText().toString();
        } else if (entry instanceof MediaController.SearchImage) {
            MediaController.SearchImage searchImage = (MediaController.SearchImage) entry;
            searchImage.caption = commentTextView.getText().toString();
        }
    }

    private void sendPressed(boolean notify, int scheduleDate) {
        if (buttonPressed) {
            return;
        }
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            TLRPC.Chat chat = chatActivity.getCurrentChat();
            TLRPC.User user = chatActivity.getCurrentUser();
            if (user != null || ChatObject.isChannel(chat) && chat.megagroup || !ChatObject.isChannel(chat)) {
                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_" + chatActivity.getDialogId(), !notify).commit();
            }
        }
        applyCaption();
        buttonPressed = true;
        delegate.didPressedButton(7, true, notify, scheduleDate);
    }

    private void updatePhotosCounter(boolean added) {
        if (counterTextView == null) {
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
                selectedTextView.setText(LocaleController.formatPluralString("MediaSelected", newSelectedCount));
            }
        } else if (hasVideo) {
            counterTextView.setText(LocaleController.formatPluralString("Videos", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                selectedTextView.setText(LocaleController.formatPluralString("VideosSelected", newSelectedCount));
            }
        } else {
            counterTextView.setText(LocaleController.formatPluralString("Photos", selectedPhotos.size()).toUpperCase());
            if (newSelectedCount != currentSelectedCount || added) {
                selectedTextView.setText(LocaleController.formatPluralString("PhotosSelected", newSelectedCount));
            }
        }
        currentSelectedCount = newSelectedCount;
    }

    private boolean showCommentTextView(boolean show, boolean animated) {
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        if (show) {
            frameLayout2.setVisibility(View.VISIBLE);
            writeButtonContainer.setVisibility(View.VISIBLE);
        }
        if (animated) {
            animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, show ? 1.0f : 0.0f));
            if (actionBar.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(frameLayout2, View.TRANSLATION_Y, show ? 0.0f : AndroidUtilities.dp(48)));
                animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? AndroidUtilities.dp(44) : AndroidUtilities.dp(48 + 44)));
                animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            } else {
                animators.add(ObjectAnimator.ofFloat(buttonsRecyclerView, View.TRANSLATION_Y, show ? AndroidUtilities.dp(44) : 0));
                animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? AndroidUtilities.dp(44) : 0));
            }

            animatorSet.playTogether(animators);
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(180);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        if (!show) {
                            frameLayout2.setVisibility(View.INVISIBLE);
                            writeButtonContainer.setVisibility(View.INVISIBLE);
                        }
                        animatorSet = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        animatorSet = null;
                    }
                }
            });
            animatorSet.start();
        } else {
            frameLayout2.setAlpha(show ? 1.0f : 0.0f);
            writeButtonContainer.setScaleX(show ? 1.0f : 0.2f);
            writeButtonContainer.setScaleY(show ? 1.0f : 0.2f);
            writeButtonContainer.setAlpha(show ? 1.0f : 0.0f);
            selectedCountView.setScaleX(show ? 1.0f : 0.2f);
            selectedCountView.setScaleY(show ? 1.0f : 0.2f);
            selectedCountView.setAlpha(show ? 1.0f : 0.0f);
            if (actionBar.getTag() != null) {
                frameLayout2.setTranslationY(show ? 0.0f : AndroidUtilities.dp(48));
                shadow.setTranslationY(show ? AndroidUtilities.dp(44) : AndroidUtilities.dp(48 + 44));
                shadow.setAlpha(show ? 1.0f : 0.0f);
            } else {
                buttonsRecyclerView.setTranslationY(show ? AndroidUtilities.dp(44) : 0);
                shadow.setTranslationY(show ? AndroidUtilities.dp(44) : 0);
            }
            if (!show) {
                frameLayout2.setVisibility(View.INVISIBLE);
                writeButtonContainer.setVisibility(View.INVISIBLE);
            }
        }
        return true;
    }

    private final Property<ChatAttachAlert, Float> ATTACH_ALERT_PROGRESS = new AnimationProperties.FloatProperty<ChatAttachAlert>("openProgress") {

        private float openProgress;

        @Override
        public void setValue(ChatAttachAlert object, float value) {
            for (int a = 0, N = buttonsRecyclerView.getChildCount(); a < N; a++) {
                float startTime = 32.0f * (3 - a);
                View child = buttonsRecyclerView.getChildAt(a);
                float scale;
                if (value > startTime) {
                    float elapsedTime = value - startTime;
                    if (elapsedTime <= 200.0f) {
                        scale = 1.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(elapsedTime / 200.0f);
                        child.setAlpha(CubicBezierInterpolator.EASE_BOTH.getInterpolation(elapsedTime / 200.0f));
                    } else {
                        child.setAlpha(1.0f);
                        elapsedTime -= 200.0f;
                        if (elapsedTime <= 100.0f) {
                            scale = 1.1f - 0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation(elapsedTime / 100.0f);
                        } else {
                            scale = 1.0f;
                        }
                    }
                } else {
                    scale = 0;
                }
                if (child instanceof AttachButton) {
                    AttachButton attachButton = (AttachButton) child;
                    attachButton.textView.setScaleX(scale);
                    attachButton.textView.setScaleY(scale);
                    attachButton.imageView.setScaleX(scale);
                    attachButton.imageView.setScaleY(scale);
                } else if (child instanceof AttachBotButton) {
                    AttachBotButton attachButton = (AttachBotButton) child;
                    attachButton.nameTextView.setScaleX(scale);
                    attachButton.nameTextView.setScaleY(scale);
                    attachButton.imageView.setScaleX(scale);
                    attachButton.imageView.setScaleY(scale);
                }
            }
        }

        @Override
        public Float get(ChatAttachAlert object) {
            return openProgress;
        }
    };

    @Override
    protected boolean onCustomOpenAnimation() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, ATTACH_ALERT_PROGRESS, 0.0f, 400.0f));
        animatorSet.setDuration(400);
        animatorSet.setStartDelay(20);
        animatorSet.start();
        return false;
    }

    private void openPhotoViewer(MediaController.PhotoEntry entry, final boolean sameTakePictureOrientation, boolean external) {
        if (entry != null) {
            cameraPhotos.add(entry);
            selectedPhotos.put(entry.imageId, entry);
            selectedPhotosOrder.add(entry.imageId);
            updatePhotosButton(0);
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
            mediaCaptured = false;
            return;
        }
        if (cameraPhotos.isEmpty()) {
            return;
        }
        cancelTakingPhotos = true;
        PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
        PhotoViewer.getInstance().setParentAlert(ChatAttachAlert.this);
        PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);

        ChatActivity chatActivity;
        int type;
        if (baseFragment instanceof ChatActivity) {
            chatActivity = (ChatActivity) baseFragment;
            type = 2;
        } else {
            chatActivity = null;
            type = 5;
        }
        PhotoViewer.getInstance().openPhotoForSelect(getAllPhotosArray(), cameraPhotos.size() - 1, type, new BasePhotoProvider() {

            @Override
            public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                return null;
            }

            @Override
            public boolean cancelButtonPressed() {
                if (cameraOpened && cameraView != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cameraView != null && !isDismissed() && Build.VERSION.SDK_INT >= 21) {
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
                    updatePhotosButton(0);
                }
                return true;
            }

            @Override
            public void needAddMorePhotos() {
                cancelTakingPhotos = false;
                if (mediaFromExternalCamera) {
                    delegate.didPressedButton(0, true, true, 0);
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
            public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate) {
                if (cameraPhotos.isEmpty() || baseFragment == null) {
                    return;
                }
                if (videoEditedInfo != null && index >= 0 && index < cameraPhotos.size()) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                }
                if (!(baseFragment instanceof ChatActivity) || !((ChatActivity) baseFragment).isSecretChat()) {
                    for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                        AndroidUtilities.addMediaToGallery(((MediaController.PhotoEntry) cameraPhotos.get(a)).path);
                    }
                }
                applyCaption();
                delegate.didPressedButton(8, true, notify, scheduleDate);
                cameraPhotos.clear();
                selectedPhotosOrder.clear();
                selectedPhotos.clear();
                adapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
                closeCamera(false);
                dismiss();
            }

            @Override
            public boolean scaleToFill() {
                if (baseFragment == null || baseFragment.getParentActivity() == null) {
                    return false;
                }
                int locked = Settings.System.getInt(baseFragment.getParentActivity().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                return sameTakePictureOrientation || locked == 1;
            }

            @Override
            public void willHidePhotoViewer() {
                mediaCaptured = false;
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
                return maxSelectedPhotos != 1;
            }
        }, chatActivity);
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
                        float diff = (newDistance - pinchStartDistance) / AndroidUtilities.dp(100);
                        pinchStartDistance = newDistance;
                        cameraZoom += diff;
                        if (cameraZoom < 0.0f) {
                            cameraZoom = 0.0f;
                        } else if (cameraZoom > 1.0f) {
                            cameraZoom = 1.0f;
                        }
                        zoomControlView.setZoom(cameraZoom, false);
                        containerView.invalidate();
                        cameraView.setZoom(cameraZoom);
                        showZoomControls(true, true);
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
                                animatorSet.setDuration(200);
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

    @Override
    protected boolean onContainerTouchEvent(MotionEvent event) {
        return cameraOpened && processTouchEvent(event);
    }

    private void applyAttachButtonColors(View view) {
        if (view instanceof AttachButton) {
            AttachButton button = (AttachButton) view;
            button.textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        } else if (view instanceof AttachBotButton) {
            AttachBotButton button = (AttachBotButton) view;
            button.nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        }
    }

    public void checkColors() {
        if (buttonsRecyclerView == null) {
            return;
        }
        int count = buttonsRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            applyAttachButtonColors(buttonsRecyclerView.getChildAt(a));
        }
        selectedTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));

        selectedMenuItem.setIconColor(Theme.getColor(Theme.key_dialogTextBlack));
        Theme.setDrawableColor(selectedMenuItem.getBackground(), Theme.getColor(Theme.key_dialogButtonSelector));
        selectedMenuItem.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
        selectedMenuItem.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), true);
        selectedMenuItem.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        commentTextView.updateColors();

        if (sendPopupLayout != null) {
            sendPopupLayout.getBackgroundDrawable().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
            for (int a = 0; a < itemCells.length; a++) {
                itemCells[a].setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            }
        }

        Theme.setSelectorDrawableColor(writeButtonDrawable, Theme.getColor(Theme.key_dialogFloatingButton), false);
        Theme.setSelectorDrawableColor(writeButtonDrawable, Theme.getColor(Theme.key_dialogFloatingButtonPressed), true);
        writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));

        dropDown.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        dropDownContainer.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
        dropDownContainer.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), true);
        dropDownContainer.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        actionBarShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));

        progressView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));

        buttonsRecyclerView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        buttonsRecyclerView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        frameLayout2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        selectedCountView.invalidate();

        Theme.setDrawableColor(dropDownDrawable, Theme.getColor(Theme.key_dialogTextBlack));

        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));

        Theme.setDrawableColor(shadowDrawable, Theme.getColor(Theme.key_dialogBackground));
        Theme.setDrawableColor(cameraDrawable, Theme.getColor(Theme.key_dialogCameraIcon));
        if (cameraIcon != null) {
            cameraIcon.invalidate();
        }

        gridView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(0);
        if (holder != null && holder.itemView instanceof PhotoAttachCameraCell) {
            ((PhotoAttachCameraCell) holder.itemView).getImageView().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
        }

        containerView.invalidate();
    }

    private void resetRecordState() {
        if (baseFragment == null) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            flashModeButton[a].setAlpha(1.0f);
        }
        switchCameraButton.setAlpha(1.0f);
        tooltipTextView.setAlpha(1.0f);
        recordTime.setAlpha(0.0f);
        AndroidUtilities.cancelRunOnUIThread(videoRecordRunnable);
        videoRecordRunnable = null;
        AndroidUtilities.unlockOrientation(baseFragment.getParentActivity());
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

    @Override
    protected boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == cameraIcon) {
            cameraIcon.measure(View.MeasureSpec.makeMeasureSpec(itemSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(itemSize - cameraViewOffsetBottomY - cameraViewOffsetY, View.MeasureSpec.EXACTLY));
            return true;
        } else if (view == cameraView) {
            if (cameraOpened && !cameraAnimationInProgress) {
                cameraView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
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

    public void onPause() {
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
        paused = true;
    }

    public void onResume() {
        paused = false;
        if (isShowing() && !isDismissed()) {
            checkCamera(false);
        }
    }

    private void openCamera(boolean animated) {
        if (cameraView == null || cameraInitAnimation != null || !cameraView.isInitied()) {
            return;
        }
        if (cameraPhotos.isEmpty()) {
            counterTextView.setVisibility(View.INVISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
        } else {
            counterTextView.setVisibility(View.VISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
        }
        if (commentTextView.isKeyboardVisible() && isFocusable()) {
            commentTextView.closeKeyboard();
        }
        zoomControlView.setVisibility(View.VISIBLE);
        zoomControlView.setAlpha(0.0f);
        cameraPanel.setVisibility(View.VISIBLE);
        cameraPanel.setTag(null);
        animateCameraValues[0] = 0;
        animateCameraValues[1] = itemSize - cameraViewOffsetX;
        animateCameraValues[2] = itemSize - cameraViewOffsetY - cameraViewOffsetBottomY;
        if (animated) {
            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(ChatAttachAlert.this, "cameraOpenProgress", 0.0f, 1.0f));
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
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    cameraAnimationInProgress = false;
                    if (Build.VERSION.SDK_INT >= 21 && cameraView != null) {
                        cameraView.invalidateOutline();
                    }
                    if (cameraOpened) {
                        delegate.onCameraOpened();
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
            delegate.onCameraOpened();
        }
        if (Build.VERSION.SDK_INT >= 21) {
            cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        cameraOpened = true;
        cameraView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    public void onActivityResultFragment(int requestCode, Intent data, String currentPicturePath) {
        if (baseFragment == null || baseFragment.getParentActivity() == null) {
            return;
        }
        mediaFromExternalCamera = true;
        if (requestCode == 0) {
            PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
            PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
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
            MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation, false);
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
                if (!(baseFragment instanceof ChatActivity) || !((ChatActivity) baseFragment).isSecretChat()) {
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
            final Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
            String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
            final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            try {
                FileOutputStream stream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            SharedConfig.saveConfig();

            MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, lastImageId--, 0, videoPath, 0, true);
            entry.duration = (int) duration;
            entry.thumbPath = cacheFile.getAbsolutePath();
            openPhotoViewer(entry, false, true);
        }
    }

    public void closeCamera(boolean animated) {
        if (takingPhoto || cameraView == null) {
            return;
        }
        animateCameraValues[1] = itemSize - cameraViewOffsetX;
        animateCameraValues[2] = itemSize - cameraViewOffsetY - cameraViewOffsetBottomY;
        if (zoomControlHideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
            zoomControlHideRunnable = null;
        }
        if (animated) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
            animateCameraValues[0] = layoutParams.topMargin = (int) cameraView.getTranslationY();
            cameraView.setLayoutParams(layoutParams);
            cameraView.setTranslationY(0);

            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(ChatAttachAlert.this, "cameraOpenProgress", 0.0f));
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
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    cameraAnimationInProgress = false;
                    if (Build.VERSION.SDK_INT >= 21 && cameraView != null) {
                        cameraView.invalidateOutline();
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
                    if (Build.VERSION.SDK_INT >= 21 && cameraView != null) {
                        cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                    }
                }
            });
            animatorSet.start();
        } else {
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
            if (Build.VERSION.SDK_INT >= 21) {
                cameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }
        cameraView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (Build.VERSION.SDK_INT >= 19) {
            gridView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    }

    @Keep
    public void setCameraOpenProgress(float value) {
        if (cameraView == null) {
            return;
        }
        cameraOpenProgress = value;
        float startWidth = animateCameraValues[1];
        float startHeight = animateCameraValues[2];
        boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
        float endWidth;
        float endHeight;
        if (isPortrait) {
            endWidth = container.getWidth() - getLeftInset() - getRightInset();
            endHeight = container.getHeight();
        } else {
            endWidth = container.getWidth() - getLeftInset() - getRightInset();
            endHeight = container.getHeight();
        }
        if (value == 0) {
            cameraView.setClipTop(cameraViewOffsetY);
            cameraView.setClipBottom(cameraViewOffsetBottomY);
            cameraView.setTranslationX(cameraViewLocation[0]);
            cameraView.setTranslationY(cameraViewLocation[1]);
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1]);
        } else if (cameraView.getTranslationX() != 0 || cameraView.getTranslationY() != 0) {
            cameraView.setTranslationX(0);
            cameraView.setTranslationY(0);
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
        layoutParams.width = (int) (startWidth + (endWidth - startWidth) * value);
        layoutParams.height = (int) (startHeight + (endHeight - startHeight) * value);
        if (value != 0) {
            cameraView.setClipTop((int) (cameraViewOffsetY * (1.0f - value)));
            cameraView.setClipBottom((int) (cameraViewOffsetBottomY * (1.0f - value)));
            layoutParams.leftMargin = (int) (cameraViewLocation[0] * (1.0f - value));
            layoutParams.topMargin = (int) (animateCameraValues[0] + (cameraViewLocation[1] - animateCameraValues[0]) * (1.0f - value));
        } else {
            layoutParams.leftMargin = 0;
            layoutParams.topMargin = 0;
        }
        cameraView.setLayoutParams(layoutParams);
        if (value <= 0.5f) {
            cameraIcon.setAlpha(1.0f - value / 0.5f);
        } else {
            cameraIcon.setAlpha(0.0f);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            cameraView.invalidateOutline();
        }
    }

    @Keep
    public float getCameraOpenProgress() {
        return cameraOpenProgress;
    }

    private void checkCameraViewPosition() {
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
                child.getLocationInWindow(cameraViewLocation);
                cameraViewLocation[0] -= getLeftInset();
                float listViewX = gridView.getX() - getLeftInset();
                if (cameraViewLocation[0] < listViewX) {
                    cameraViewOffsetX = (int) (listViewX - cameraViewLocation[0]);
                    if (cameraViewOffsetX >= itemSize) {
                        cameraViewOffsetX = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-400);
                        cameraViewLocation[1] = 0;
                    } else {
                        cameraViewLocation[0] += cameraViewOffsetX;
                    }
                } else {
                    cameraViewOffsetX = 0;
                }
                int maxY = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                if (cameraViewLocation[1] < maxY) {
                    cameraViewOffsetY = maxY - cameraViewLocation[1];
                    if (cameraViewOffsetY >= itemSize) {
                        cameraViewOffsetY = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-400);
                        cameraViewLocation[1] = 0;
                    } else {
                        cameraViewLocation[1] += cameraViewOffsetY;
                    }
                } else {
                    cameraViewOffsetY = 0;
                }
                int containerHeight = containerView.getMeasuredHeight();
                int keyboardSize = sizeNotifierFrameLayout.getKeyboardHeight();
                if (!AndroidUtilities.isInMultiwindow && keyboardSize <= AndroidUtilities.dp(20)) {
                    containerHeight -= commentTextView.getEmojiPadding();
                }
                maxY = (int) (containerHeight - buttonsRecyclerView.getMeasuredHeight() + buttonsRecyclerView.getTranslationY() + containerView.getTranslationY());
                if (cameraViewLocation[1] + itemSize > maxY) {
                    cameraViewOffsetBottomY = cameraViewLocation[1] + itemSize - maxY;
                    if (cameraViewOffsetBottomY >= itemSize) {
                        cameraViewOffsetBottomY = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-400);
                        cameraViewLocation[1] = 0;
                    }
                } else {
                    cameraViewOffsetBottomY = 0;
                }
                applyCameraViewPosition();
                return;
            }
        }
        cameraViewOffsetX = 0;
        cameraViewOffsetY = 0;
        cameraViewLocation[0] = AndroidUtilities.dp(-400);
        cameraViewLocation[1] = 0;
        applyCameraViewPosition();
    }

    private void applyCameraViewPosition() {
        if (cameraView != null) {
            if (!cameraOpened) {
                cameraView.setTranslationX(cameraViewLocation[0]);
                cameraView.setTranslationY(cameraViewLocation[1]);
            }
            cameraIcon.setTranslationX(cameraViewLocation[0]);
            cameraIcon.setTranslationY(cameraViewLocation[1]);
            int finalWidth = itemSize - cameraViewOffsetX;
            int finalHeight = itemSize - cameraViewOffsetY - cameraViewOffsetBottomY;

            FrameLayout.LayoutParams layoutParams;
            if (!cameraOpened) {
                cameraView.setClipTop(cameraViewOffsetY);
                cameraView.setClipBottom(cameraViewOffsetBottomY);
                layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
                if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                    layoutParams.width = finalWidth;
                    layoutParams.height = finalHeight;
                    cameraView.setLayoutParams(layoutParams);
                    final FrameLayout.LayoutParams layoutParamsFinal = layoutParams;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cameraView != null) {
                            cameraView.setLayoutParams(layoutParamsFinal);
                        }
                    });
                }
            }

            layoutParams = (FrameLayout.LayoutParams) cameraIcon.getLayoutParams();
            if (layoutParams.height != finalHeight || layoutParams.width != finalWidth) {
                layoutParams.width = finalWidth;
                layoutParams.height = finalHeight;
                cameraIcon.setLayoutParams(layoutParams);
                final FrameLayout.LayoutParams layoutParamsFinal = layoutParams;
                AndroidUtilities.runOnUIThread(() -> {
                    if (cameraIcon != null) {
                        cameraIcon.setLayoutParams(layoutParamsFinal);
                    }
                });
            }
        }
    }

    public void showCamera() {
        if (paused || !mediaEnabled) {
            return;
        }
        if (cameraView == null) {
            cameraView = new CameraView(baseFragment.getParentActivity(), openWithFrontFaceCamera);
            cameraView.setFocusable(true);
            if (Build.VERSION.SDK_INT >= 21) {
                cameraView.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        if (cameraAnimationInProgress) {
                            int rad = AndroidUtilities.dp(8 * cornerRadius * cameraOpenProgress);
                            outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                        } else if (!cameraAnimationInProgress && !cameraOpened) {
                            int rad = AndroidUtilities.dp(8 * cornerRadius);
                            outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                        } else {
                            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                        }
                    }
                });
                cameraView.setClipToOutline(true);
            }
            cameraView.setContentDescription(LocaleController.getString("AccDescrInstantCamera", R.string.AccDescrInstantCamera));
            container.addView(cameraView, 1, new FrameLayout.LayoutParams(itemSize, itemSize));
            cameraView.setDelegate(new CameraView.CameraViewDelegate() {
                @Override
                public void onCameraCreated(Camera camera) {

                }

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
                                    cameraInitAnimation = null;
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
                cameraIcon = new FrameLayout(baseFragment.getParentActivity()) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        int w = cameraDrawable.getIntrinsicWidth();
                        int h = cameraDrawable.getIntrinsicHeight();
                        int x = (itemSize - w) / 2;
                        int y = (itemSize - h) / 2;
                        if (cameraViewOffsetY != 0) {
                            y -= cameraViewOffsetY;
                        }
                        cameraDrawable.setBounds(x, y, x + w, y + h);
                        cameraDrawable.draw(canvas);
                    }
                };
                cameraIcon.setWillNotDraw(false);
                cameraIcon.setClipChildren(true);
            }
            container.addView(cameraIcon, 2, new FrameLayout.LayoutParams(itemSize, itemSize));

            cameraView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraView.setEnabled(mediaEnabled);
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
            checkCameraViewPosition();
        }
        if (zoomControlView != null) {
            zoomControlView.setZoom(0.0f, false);
            cameraZoom = 0.0f;
        }
        cameraView.setTranslationX(cameraViewLocation[0]);
        cameraView.setTranslationY(cameraViewLocation[1]);
        cameraIcon.setTranslationX(cameraViewLocation[0]);
        cameraIcon.setTranslationY(cameraViewLocation[1]);
    }

    public void hideCamera(boolean async) {
        if (!deviceHasGoodCamera || cameraView == null) {
            return;
        }
        saveLastCameraBitmap();
        cameraView.destroy(async, null);
        if (cameraInitAnimation != null) {
            cameraInitAnimation.cancel();
            cameraInitAnimation = null;
        }
        container.removeView(cameraView);
        container.removeView(cameraIcon);
        cameraView = null;
        cameraIcon = null;
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gridView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                child.setVisibility(View.VISIBLE);
                return;
            }
        }
    }

    private void saveLastCameraBitmap() {
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
                }
            }
        } catch (Throwable ignore) {

        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            if (adapter != null) {
                if (baseFragment instanceof ChatActivity) {
                    galleryAlbumEntry = MediaController.allMediaAlbumEntry;
                } else {
                    galleryAlbumEntry = MediaController.allPhotosAlbumEntry;
                }
                if (selectedAlbumEntry == null) {
                    selectedAlbumEntry = galleryAlbumEntry;
                } else {
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
                        int imageId = (Integer) selectedPhotosOrder.get(a);
                        MediaController.PhotoEntry entry = galleryAlbumEntry.photosByIds.get(imageId);
                        if (entry != null) {
                            selectedPhotos.put(imageId, entry);
                        }
                    }
                }
                updateAlbumsDropDown();
            }
        } else if (id == NotificationCenter.reloadInlineHints) {
            if (buttonsAdapter != null) {
                buttonsAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.cameraInitied) {
            checkCamera(false);
        }
    }

    private void updateAlbumsDropDown() {
        dropDownContainer.removeAllSubItems();
        if (mediaEnabled) {
            ArrayList<MediaController.AlbumEntry> albums;
            if (baseFragment instanceof ChatActivity) {
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

    private void updateSelectedPosition() {
        float moveProgress;
        int t = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(39);
        if (t + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
            float toMove = AndroidUtilities.dp(43);
            moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - t - backgroundPaddingTop) / toMove);
            cornerRadius = 1.0f - moveProgress;
        } else {
            moveProgress = 0.0f;
            cornerRadius = 1.0f;
        }

        int finalMove;
        if (AndroidUtilities.isTablet()) {
            finalMove = 16;
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            finalMove = 6;
        } else {
            finalMove = 12;
        }

        float offset = actionBar.getAlpha() != 0 ? 0.0f : AndroidUtilities.dp(26 * (1.0f - selectedMenuItem.getAlpha()));
        selectedMenuItem.setTranslationY(scrollOffsetY - AndroidUtilities.dp(37 + finalMove * moveProgress) + offset);
        selectedTextView.setTranslationY(scrollOffsetY - AndroidUtilities.dp(25 + finalMove * moveProgress) + offset);
    }

    @SuppressLint("NewApi")
    private void updateLayout(boolean animated) {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(scrollOffsetY = gridView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = AndroidUtilities.dp(7);
        if (top >= AndroidUtilities.dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        boolean show = newOffset <= AndroidUtilities.dp(12);
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }
            actionBarAnimation = new AnimatorSet();
            actionBarAnimation.setDuration(180);
            actionBarAnimation.playTogether(
                    ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
            actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    actionBarAnimation = null;
                }
            });
            actionBarAnimation.start();
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) gridView.getLayoutParams();
        newOffset += layoutParams.topMargin - AndroidUtilities.dp(11);
        if (scrollOffsetY != newOffset) {
            gridView.setTopGlowOffset((scrollOffsetY = newOffset) - layoutParams.topMargin);
            updateSelectedPosition();
            containerView.invalidate();
        }
        progressView.setTranslationY(scrollOffsetY + (gridView.getMeasuredHeight() - scrollOffsetY - AndroidUtilities.dp(50) - progressView.getMeasuredHeight()) / 2);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void updatePhotosButton(int animated) {
        int count = selectedPhotos.size();

        if (count == 0) {
            selectedCountView.setPivotX(0);
            selectedCountView.setPivotY(0);
            showCommentTextView(false, animated != 0);
        } else {
            selectedCountView.invalidate();
            if (!showCommentTextView(true, animated != 0) && animated != 0) {
                selectedCountView.setPivotX(AndroidUtilities.dp(21));
                selectedCountView.setPivotY(AndroidUtilities.dp(12));
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, animated == 1 ? 1.1f : 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, animated == 1 ? 1.1f : 0.9f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
                animatorSet.setDuration(180);
                animatorSet.start();
            } else {
                selectedCountView.setPivotX(0);
                selectedCountView.setPivotY(0);
            }
            if (count == 1 || editingMessageObject != null) {
                selectedMenuItem.hideSubItem(group);
            } else {
                selectedMenuItem.showSubItem(group);
            }
        }

        if ((baseFragment instanceof ChatActivity) && (count == 0 && menuShowed || count != 0 && !menuShowed)) {
            menuShowed = count != 0;
            if (menuAnimator != null) {
                menuAnimator.cancel();
                menuAnimator = null;
            }
            if (menuShowed) {
                selectedMenuItem.setVisibility(View.VISIBLE);
                selectedTextView.setVisibility(View.VISIBLE);
            }
            if (animated == 0) {
                selectedMenuItem.setAlpha(menuShowed ? 1.0f : 0.0f);
                selectedTextView.setAlpha(menuShowed ? 1.0f : 0.0f);
            } else {
                menuAnimator = new AnimatorSet();
                menuAnimator.playTogether(
                        ObjectAnimator.ofFloat(selectedMenuItem, View.ALPHA, menuShowed ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(selectedTextView, View.ALPHA, menuShowed ? 1.0f : 0.0f));
                menuAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        menuAnimator = null;
                        if (!menuShowed) {
                            selectedMenuItem.setVisibility(View.INVISIBLE);
                            selectedTextView.setVisibility(View.INVISIBLE);
                        }
                    }
                });
                menuAnimator.setDuration(180);
                menuAnimator.start();
            }
        }
    }

    public void setDelegate(ChatAttachViewDelegate chatAttachViewDelegate) {
        delegate = chatAttachViewDelegate;
    }

    public void loadGalleryPhotos() {
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (albumEntry == null && Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    public void init() {
        if (baseFragment instanceof ChatActivity) {
            galleryAlbumEntry = MediaController.allMediaAlbumEntry;
            TLRPC.Chat chat = ((ChatActivity) baseFragment).getCurrentChat();
            if (chat != null) {
                mediaEnabled = ChatObject.canSendMedia(chat);
                pollsEnabled = ChatObject.canSendPolls(chat);
                if (mediaEnabled) {
                    progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
                } else {
                    if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) {
                        progressView.setText(LocaleController.getString("GlobalAttachMediaRestricted", R.string.GlobalAttachMediaRestricted));
                    } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                        progressView.setText(LocaleController.formatString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
                    } else {
                        progressView.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                    }
                }
                if (cameraView != null) {
                    cameraView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
                    cameraView.setEnabled(mediaEnabled);
                }
                if (cameraIcon != null) {
                    cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
                    cameraIcon.setEnabled(mediaEnabled);
                }
            } else {
                pollsEnabled = false;
            }
        } else {
            galleryAlbumEntry = MediaController.allPhotosAlbumEntry;
            commentTextView.setVisibility(View.INVISIBLE);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            noGalleryPermissions = baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
        }
        if (galleryAlbumEntry != null) {
            for (int a = 0; a < Math.min(100, galleryAlbumEntry.photos.size()); a++) {
                MediaController.PhotoEntry photoEntry = galleryAlbumEntry.photos.get(a);
                photoEntry.reset();
            }
        }
        commentTextView.hidePopup(true);
        enterCommentEventSent = false;
        setFocusable(false);
        selectedAlbumEntry = galleryAlbumEntry;
        if (selectedAlbumEntry != null) {
            loading = false;
            if (progressView != null) {
                progressView.showTextView();
            }
        }
        dropDown.setText(LocaleController.getString("ChatGallery", R.string.ChatGallery));
        clearSelectedPhotos();
        updatePhotosCounter(false);
        buttonsAdapter.notifyDataSetChanged();
        commentTextView.setText("");
        cameraPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        buttonsLayoutManager.scrollToPositionWithOffset(0, 1000000);
        layoutManager.scrollToPositionWithOffset(0, 1000000);
        updateAlbumsDropDown();
    }

    public HashMap<Object, Object> getSelectedPhotos() {
        return selectedPhotos;
    }

    public ArrayList<Object> getSelectedPhotosOrder() {
        return selectedPhotosOrder;
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.cameraInitied);
        baseFragment = null;
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        int count = gridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = gridView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                if ((Integer) cell.getImageView().getTag() == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    public void checkStorage() {
        if (noGalleryPermissions && Build.VERSION.SDK_INT >= 23) {
            noGalleryPermissions = baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (!noGalleryPermissions) {
                loadGalleryPhotos();
            }
            adapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
    }

    public void checkCamera(boolean request) {
        if (baseFragment == null) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        boolean old2 = noCameraPermissions;
        if (!SharedConfig.inappCamera) {
            deviceHasGoodCamera = false;
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                if (noCameraPermissions = (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
                    if (request) {
                        try {
                            baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 17);
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
        if (isShowing() && deviceHasGoodCamera && baseFragment != null && backDrawable.getAlpha() != 0 && !cameraOpened) {
            showCamera();
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        NotificationCenter.getInstance(currentAccount).setAnimationInProgress(false);
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (Build.VERSION.SDK_INT <= 19 && albumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
        checkCamera(true);
        AndroidUtilities.makeAccessibilityAnnouncement(LocaleController.getString("AccDescrAttachButton", R.string.AccDescrAttachButton));
    }

    @Override
    public void onOpenAnimationStart() {

    }

    @Override
    public boolean canDismiss() {
        return true;
    }

    @Override
    public void setAllowDrawContent(boolean value) {
        super.setAllowDrawContent(value);
        checkCameraViewPosition();
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        if (editingMessageObject != null) {
            return;
        }
        maxSelectedPhotos = value;
        allowOrder = order;
    }

    public void setOpenWithFrontFaceCamera(boolean value) {
        openWithFrontFaceCamera = value;
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
        updatePhotosButton(0);
        adapter.notifyDataSetChanged();
        cameraAttachAdapter.notifyDataSetChanged();
    }

    private class ButtonsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private int galleryButton;
        private int documentButton;
        private int musicButton;
        private int pollButton;
        private int contactButton;
        private int locationButton;
        private int buttonsCount;

        public ButtonsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new AttachButton(mContext);
                    break;
                case 1:
                default:
                    view = new AttachBotButton(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    AttachButton attachButton = (AttachButton) holder.itemView;
                    if (position == galleryButton) {
                        attachButton.setTextAndIcon(LocaleController.getString("ChatGallery", R.string.ChatGallery), Theme.chat_attachButtonDrawables[0]);
                        attachButton.setTag(1);
                    } else if (position == documentButton) {
                        attachButton.setTextAndIcon(LocaleController.getString("ChatDocument", R.string.ChatDocument), Theme.chat_attachButtonDrawables[2]);
                        attachButton.setTag(4);
                    } else if (position == locationButton) {
                        attachButton.setTextAndIcon(LocaleController.getString("ChatLocation", R.string.ChatLocation), Theme.chat_attachButtonDrawables[4]);
                        attachButton.setTag(6);
                    } else if (position == musicButton) {
                        attachButton.setTextAndIcon(LocaleController.getString("AttachMusic", R.string.AttachMusic), Theme.chat_attachButtonDrawables[1]);
                        attachButton.setTag(3);
                    } else if (position == pollButton) {
                        attachButton.setTextAndIcon(LocaleController.getString("Poll", R.string.Poll), Theme.chat_attachButtonDrawables[5]);
                        attachButton.setTag(9);
                    } else if (position == contactButton) {
                        attachButton.setTextAndIcon(LocaleController.getString("AttachContact", R.string.AttachContact), Theme.chat_attachButtonDrawables[3]);
                        attachButton.setTag(5);
                    }
                    break;
                case 1:
                    position -= buttonsCount;
                    AttachBotButton child = (AttachBotButton) holder.itemView;
                    child.setTag(position);
                    child.setUser(MessagesController.getInstance(currentAccount).getUser(MediaDataController.getInstance(currentAccount).inlineBots.get(position).peer.user_id));
                    break;
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            applyAttachButtonColors(holder.itemView);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            int count = buttonsCount;
            if (editingMessageObject == null && baseFragment instanceof ChatActivity) {
                count += MediaDataController.getInstance(currentAccount).inlineBots.size();
            }
            return count;
        }

        @Override
        public void notifyDataSetChanged() {
            buttonsCount = 0;
            galleryButton = -1;
            documentButton = -1;
            musicButton = -1;
            pollButton = -1;
            contactButton = -1;
            locationButton = -1;
            if (!(baseFragment instanceof ChatActivity)) {
                galleryButton = buttonsCount++;
                documentButton = buttonsCount++;
            } else if (editingMessageObject != null) {
                galleryButton = buttonsCount++;
                documentButton = buttonsCount++;
                musicButton = buttonsCount++;
            } else {
                if (mediaEnabled) {
                    galleryButton = buttonsCount++;
                    documentButton = buttonsCount++;
                }
                locationButton = buttonsCount++;
                if (pollsEnabled) {
                    pollButton = buttonsCount++;
                } else {
                    contactButton = buttonsCount++;
                }
                if (mediaEnabled) {
                    musicButton = buttonsCount++;
                }
            }
            super.notifyDataSetChanged();
        }

        public int getButtonsCount() {
            return buttonsCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < buttonsCount) {
                return 0;
            }
            return 1;
        }
    }

    private class PhotoAttachAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private boolean needCamera;
        private ArrayList<RecyclerListView.Holder> viewsCache = new ArrayList<>(8);
        private int itemsCount;

        public PhotoAttachAdapter(Context context, boolean camera) {
            mContext = context;
            needCamera = camera;
            for (int a = 0; a < 8; a++) {
                viewsCache.add(createHolder());
            }
        }

        public RecyclerListView.Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext);
            if (Build.VERSION.SDK_INT >= 21 && this == adapter) {
                cell.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        PhotoAttachPhotoCell photoCell = (PhotoAttachPhotoCell) view;
                        int position = (Integer) photoCell.getTag();
                        if (needCamera && selectedAlbumEntry == galleryAlbumEntry) {
                            position++;
                        }
                        if (position == 0) {
                            int rad = AndroidUtilities.dp(8 * cornerRadius);
                            outline.setRoundRect(0, 0, view.getMeasuredWidth() + rad, view.getMeasuredHeight() + rad, rad);
                        } else if (position == itemsPerRow - 1) {
                            int rad = AndroidUtilities.dp(8 * cornerRadius);
                            outline.setRoundRect(-rad, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + rad, rad);
                        } else {
                            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                        }
                    }
                });
                cell.setClipToOutline(true);
            }
            cell.setDelegate(v -> {
                if (!mediaEnabled) {
                    return;
                }
                int index = (Integer) v.getTag();
                MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                if (added && maxSelectedPhotos >= 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                    if (allowOrder && baseFragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) baseFragment;
                        TLRPC.Chat chat = chatActivity.getCurrentChat();
                        if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                            if (alertOnlyOnce != 2) {
                                AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSelectSendError", R.string.SlowmodeSelectSendError)).show();
                                if (alertOnlyOnce == 1) {
                                    alertOnlyOnce = 2;
                                }
                            }
                        }
                    }
                    return;
                }
                int num = added ? selectedPhotosOrder.size() : -1;
                if (baseFragment instanceof ChatActivity && allowOrder) {
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
                updatePhotosButton(added ? 1 : 2);
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
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                    if (this == adapter) {
                        cell.setItemSize(itemSize);
                    } else {
                        cell.setIsVertical(cameraPhotoLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL);
                    }

                    MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                    cell.setPhotoEntry(photoEntry, needCamera && selectedAlbumEntry == galleryAlbumEntry, position == getItemCount() - 1);
                    if (baseFragment instanceof ChatActivity && allowOrder) {
                        cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                    } else {
                        cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
                    }
                    cell.getImageView().setTag(position);
                    cell.setTag(position);
                    break;
                }
                case 1: {
                    PhotoAttachCameraCell cameraCell = (PhotoAttachCameraCell) holder.itemView;
                    if (cameraView != null && cameraView.isInitied()) {
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
                    PhotoAttachCameraCell cameraCell = new PhotoAttachCameraCell(mContext);
                    if (Build.VERSION.SDK_INT >= 21) {
                        cameraCell.setOutlineProvider(new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                int rad = AndroidUtilities.dp(8 * cornerRadius);
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
                    holder = new RecyclerListView.Holder(new PhotoAttachPermissionCell(mContext));
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
            if (noGalleryPermissions && this == adapter) {
                count++;
            }
            count += cameraPhotos.size();
            if (selectedAlbumEntry != null) {
                count += selectedAlbumEntry.photos.size();
            }
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
            if (needCamera && position == 0 && selectedAlbumEntry == galleryAlbumEntry) {
                if (noCameraPermissions) {
                    return 3;
                } else {
                    return 1;
                }
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
    }

    @Override
    public void dismissInternal() {
        if (containerView != null) {
            containerView.setVisibility(View.INVISIBLE);
        }
        super.dismissInternal();
    }

    @Override
    public void onBackPressed() {
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            commentTextView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void dismissWithButtonClick(int item) {
        super.dismissWithButtonClick(item);
        hideCamera(item != 0 && item != 2);
    }

    @Override
    protected boolean canDismissWithTouchOutside() {
        return !cameraOpened;
    }

    @Override
    public void dismiss() {
        if (cameraAnimationInProgress) {
            return;
        }
        if (cameraOpened) {
            closeCamera(true);
            return;
        }
        if (commentTextView != null) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        hideCamera(true);
        super.dismiss();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (cameraOpened && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            shutterButton.getDelegate().shutterReleased();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
