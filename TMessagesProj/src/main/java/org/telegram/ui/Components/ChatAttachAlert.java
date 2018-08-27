/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.media.ExifInterface;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.*;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class ChatAttachAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, BottomSheet.BottomSheetDelegateInterface {

    public interface ChatAttachViewDelegate {
        void didPressedButton(int button);
        View getRevealView();
        void didSelectBot(TLRPC.User user);
        void onCameraOpened();
        boolean allowGroupPhotos();
    }

    private class InnerAnimator {
        private AnimatorSet animatorSet;
        private float startRadius;
    }

    private BaseFragment baseFragment;
    private AttachButton sendPhotosButton;
    private AttachButton sendDocumentsButton;
    private View views[] = new View[20];
    private RecyclerListView attachPhotoRecyclerView;
    private LinearLayoutManager attachPhotoLayoutManager;
    private PhotoAttachAdapter photoAttachAdapter;
    private RecyclerListView cameraPhotoRecyclerView;
    private boolean cameraPhotoRecyclerViewIgnoreLayout;
    private LinearLayoutManager cameraPhotoLayoutManager;
    private PhotoAttachAdapter cameraAttachAdapter;
    private View lineView;
    private EmptyTextProgressView progressView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Drawable shadowDrawable;
    private ViewGroup attachView;
    private ArrayList<AttachButton> attachButtons = new ArrayList<>();
    private ListAdapter adapter;
    private TextView hintTextView;
    private ArrayList<InnerAnimator> innerAnimators = new ArrayList<>();
    private boolean requestingPermissions;
    private Paint ciclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private MessageObject editingMessageObject;

    private boolean buttonPressed;

    private int currentAccount = UserConfig.selectedAccount;

    private CorrectlyMeasuringTextView mediaBanTooltip;

    private boolean mediaEnabled = true;

    private CameraView cameraView;
    private FrameLayout cameraIcon;
    private ImageView cameraImageView;
    private TextView recordTime;
    private ImageView[] flashModeButton = new ImageView[2];
    private boolean flashAnimationInProgress;
    private int[] cameraViewLocation = new int[2];
    private int[] viewPosition = new int[2];
    private int cameraViewOffsetX;
    private int cameraViewOffsetY;
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
    private TextView counterTextView;
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
    private boolean openWithFrontFaceCamera;

    private float lastY;
    private boolean pressed;
    private boolean maybeStartDraging;
    private boolean dragging;

    private AnimatorSet currentHintAnimation;
    private boolean hintShowed;
    private Runnable hideHintRunnable;

    private boolean deviceHasGoodCamera;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private boolean loading = true;

    private ChatAttachViewDelegate delegate;

    private int scrollOffsetY;
    private boolean ignoreLayout;

    private boolean useRevealAnimation;
    private float revealRadius;
    private int revealX;
    private int revealY;
    private boolean revealAnimationInProgress;

    private boolean paused;

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

            int count = attachPhotoRecyclerView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = attachPhotoRecyclerView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    int tag = (Integer) view.getTag();
                    if (tag == index) {
                        if (baseFragment instanceof ChatActivity && maxSelectedPhotos < 0) {
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
                        if (baseFragment instanceof ChatActivity && maxSelectedPhotos < 0) {
                            ((PhotoAttachPhotoCell) view).setChecked(num, add, false);
                        } else {
                            ((PhotoAttachPhotoCell) view).setChecked(-1, add, false);
                        }
                        break;
                    }
                }
            }
            updatePhotosButton();
            return num;
        }

        @Override
        public boolean allowGroupPhotos() {
            return delegate.allowGroupPhotos();
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
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                int coords[] = new int[2];
                cell.getImageView().getLocationInWindow(coords);
                if (Build.VERSION.SDK_INT < 26) {
                    coords[0] -= getLeftInset();
                }
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = attachPhotoRecyclerView;
                object.imageReceiver = cell.getImageView().getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.getImageView().getScaleX();
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
                    cell.getImageView().setImage(photoEntry.thumbPath, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
                } else if (photoEntry.path != null) {
                    cell.getImageView().setOrientation(photoEntry.orientation, true);
                    if (photoEntry.isVideo) {
                        cell.getImageView().setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
                    } else {
                        cell.getImageView().setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
                    }
                } else {
                    cell.getImageView().setImageResource(R.drawable.nophotos);
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
            int count = attachPhotoRecyclerView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = attachPhotoRecyclerView.getChildAt(a);
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
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
            MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(index);
            if (photoEntry != null) {
                photoEntry.editedInfo = videoEditedInfo;
            }
            if (selectedPhotos.isEmpty() && photoEntry != null) {
                addToSelectedPhotos(photoEntry, -1);
            }
            delegate.didPressedButton(7);
        }
    };

    private void updateCheckedPhotoIndices() {
        if (!(baseFragment instanceof ChatActivity)) {
            return;
        }
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
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
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (position < albumEntry.photos.size()) {
            return albumEntry.photos.get(position);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<Object> getAllPhotosArray() {
        ArrayList<Object> arrayList;
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (albumEntry != null) {
            if (!cameraPhotos.isEmpty()) {
                arrayList = new ArrayList<>(albumEntry.photos.size() + cameraPhotos.size());
                arrayList.addAll(cameraPhotos);
                arrayList.addAll(albumEntry.photos);
            } else {
                arrayList = (ArrayList) albumEntry.photos;
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
            addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 64, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90), MeasureSpec.EXACTLY));
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
        private boolean pressed;

        private boolean checkingForLongPress = false;
        private CheckForLongPress pendingCheckForLongPress = null;
        private int pressCount = 0;
        private CheckForTap pendingCheckForTap = null;

        private TLRPC.User currentUser;

        private final class CheckForTap implements Runnable {
            public void run() {
                if (pendingCheckForLongPress == null) {
                    pendingCheckForLongPress = new CheckForLongPress();
                }
                pendingCheckForLongPress.currentPressCount = ++pressCount;
                postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
            }
        }

        class CheckForLongPress implements Runnable {
            public int currentPressCount;

            public void run() {
                if (checkingForLongPress && getParent() != null && currentPressCount == pressCount) {
                    checkingForLongPress = false;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    onLongPress();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                }
            }
        }

        public AttachBotButton(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(27));
            addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            nameTextView.setMaxLines(2);
            nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            nameTextView.setLines(2);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 65, 6, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
        }

        private void onLongPress() {
            if (baseFragment == null || currentUser == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.formatString("ChatHintsDelete", R.string.ChatHintsDelete, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> DataQuery.getInstance(currentAccount).removeInline(currentUser.id));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        }

        public void setUser(TLRPC.User user) {
            if (user == null) {
                return;
            }
            nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            currentUser = user;
            TLRPC.FileLocation photo = null;
            nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarDrawable.setInfo(user);
            if (user != null && user.photo != null) {
                photo = user.photo.photo_small;
            }
            imageView.setImage(photo, "50_50", avatarDrawable);
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean result = false;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
                result = true;
            } else if (pressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    pressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    delegate.didSelectBot(MessagesController.getInstance(currentAccount).getUser(DataQuery.getInstance(currentAccount).inlineBots.get((Integer) getTag()).peer.user_id));
                    setUseRevealAnimation(false);
                    dismiss();
                    setUseRevealAnimation(true);
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    pressed = false;
                    invalidate();
                }
            }
            if (!result) {
                result = super.onTouchEvent(event);
            } else {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startCheckLongPress();
                }
            }
            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }

            return result;
        }

        protected void startCheckLongPress() {
            if (checkingForLongPress) {
                return;
            }
            checkingForLongPress = true;
            if (pendingCheckForTap == null) {
                pendingCheckForTap = new CheckForTap();
            }
            postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
        }

        protected void cancelCheckLongPress() {
            checkingForLongPress = false;
            if (pendingCheckForLongPress != null) {
                removeCallbacks(pendingCheckForLongPress);
            }
            if (pendingCheckForTap != null) {
                removeCallbacks(pendingCheckForTap);
            }
        }
    }

    public ChatAttachAlert(Context context, final BaseFragment parentFragment) {
        super(context, false);
        baseFragment = parentFragment;
        ciclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
        setDelegate(this);
        setUseRevealAnimation(true);
        checkCamera(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.cameraInitied);
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow).mutate();
        Theme.setDrawableColor(shadowDrawable, Theme.getColor(Theme.key_dialogBackground));

        containerView = listView = new RecyclerListView(context) {

            private int lastWidth;
            private int lastHeight;

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (cameraAnimationInProgress) {
                    return true;
                } else if (cameraOpened) {
                    return processTouchEvent(ev);
                } else if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
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
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    height -= AndroidUtilities.statusBarHeight;
                }
                int h;
                if (baseFragment instanceof ChatActivity) {
                    h = 294;
                } else {
                    h = 199;
                }
                int contentSize = backgroundPaddingTop + AndroidUtilities.dp(h) + (DataQuery.getInstance(currentAccount).inlineBots.isEmpty() ? 0 : ((int) Math.ceil(DataQuery.getInstance(currentAccount).inlineBots.size() / 4.0f) * AndroidUtilities.dp(100) + AndroidUtilities.dp(12)));
                int padding = contentSize == AndroidUtilities.dp(h) ? 0 : Math.max(0, (height - AndroidUtilities.dp(h)));
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (getPaddingTop() != padding) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, padding, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int height = bottom - top;

                int newPosition = -1;
                int newTop = 0;

                int count = listView.getChildCount();
                int lastVisibleItemPosition = -1;
                int lastVisibleItemPositionTop = 0;
                if (count > 0) {
                    View child = listView.getChildAt(listView.getChildCount() - 1);
                    Holder holder = (Holder) listView.findContainingViewHolder(child);
                    if (holder != null) {
                        lastVisibleItemPosition = holder.getAdapterPosition();
                        lastVisibleItemPositionTop = child.getTop();
                    }
                }

                if (lastVisibleItemPosition >= 0 && height - lastHeight != 0) {
                    newPosition = lastVisibleItemPosition;
                    newTop = lastVisibleItemPositionTop + height - lastHeight - getPaddingTop();
                }

                super.onLayout(changed, left, top, right, bottom);

                if (newPosition != -1) {
                    ignoreLayout = true;
                    layoutManager.scrollToPositionWithOffset(newPosition, newTop);
                    super.onLayout(false, left, top, right, bottom);
                    ignoreLayout = false;
                }

                lastHeight = height;
                lastWidth = width;

                updateLayout();
                checkCameraViewPosition();
            }

            @Override
            public void onDraw(Canvas canvas) {
                if (useRevealAnimation && Build.VERSION.SDK_INT <= 19) {
                    canvas.save();
                    canvas.clipRect(backgroundPaddingLeft, scrollOffsetY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight());
                    if (revealAnimationInProgress) {
                        canvas.drawCircle(revealX, revealY, revealRadius, ciclePaint);
                    } else {
                        canvas.drawRect(backgroundPaddingLeft, scrollOffsetY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight(), ciclePaint);
                    }
                    canvas.restore();
                } else {
                    shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                }
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                checkCameraViewPosition();
            }
        };

        nestedScrollChild = listView;
        listView.setWillNotDraw(false);
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = 0;
                outRect.right = 0;
                outRect.top = 0;
                outRect.bottom = 0;
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getChildCount() <= 0) {
                    return;
                }
                if (hintShowed) {
                    if (layoutManager.findLastVisibleItemPosition() > 1) {
                        hideHint();
                        hintShowed = false;
                        MessagesController.getGlobalMainSettings().edit().putBoolean("bothint", true).commit();
                    }
                }
                updateLayout();
                checkCameraViewPosition();
            }
        });
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        attachView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (baseFragment instanceof ChatActivity) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(294), MeasureSpec.EXACTLY));
                } else {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(199), MeasureSpec.EXACTLY));
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int height = bottom - top;
                int t = AndroidUtilities.dp(8);
                attachPhotoRecyclerView.layout(0, t, width, t + attachPhotoRecyclerView.getMeasuredHeight());
                progressView.layout(0, t, width, t + progressView.getMeasuredHeight());
                lineView.layout(0, AndroidUtilities.dp(96), width, AndroidUtilities.dp(96) + lineView.getMeasuredHeight());
                hintTextView.layout(width - hintTextView.getMeasuredWidth() - AndroidUtilities.dp(5), height - hintTextView.getMeasuredHeight() - AndroidUtilities.dp(5), width - AndroidUtilities.dp(5), height - AndroidUtilities.dp(5));
                int x = (width - mediaBanTooltip.getMeasuredWidth()) / 2;
                int y = t + (attachPhotoRecyclerView.getMeasuredHeight() - mediaBanTooltip.getMeasuredHeight()) / 2;
                mediaBanTooltip.layout(x, y, x + mediaBanTooltip.getMeasuredWidth(), y + mediaBanTooltip.getMeasuredHeight());

                int diff = (width - AndroidUtilities.dp(85 * 4 + 20)) / 3;
                int num = 0;
                for (int a = 0; a < 8; a++) {
                    if (views[a] == null) {
                        continue;
                    }
                    y = AndroidUtilities.dp(105 + 95 * (num / 4));
                    x = AndroidUtilities.dp(10) + (num % 4) * (AndroidUtilities.dp(85) + diff);
                    views[a].layout(x, y, x + views[a].getMeasuredWidth(), y + views[a].getMeasuredHeight());
                    num++;
                }
            }
        };

        views[8] = attachPhotoRecyclerView = new RecyclerListView(context);
        attachPhotoRecyclerView.setVerticalScrollBarEnabled(true);
        attachPhotoRecyclerView.setAdapter(photoAttachAdapter = new PhotoAttachAdapter(context, true));
        attachPhotoRecyclerView.setClipToPadding(false);
        attachPhotoRecyclerView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        attachPhotoRecyclerView.setItemAnimator(null);
        attachPhotoRecyclerView.setLayoutAnimation(null);
        attachPhotoRecyclerView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        attachView.addView(attachPhotoRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        attachPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        attachPhotoRecyclerView.setLayoutManager(attachPhotoLayoutManager);
        attachPhotoRecyclerView.setOnItemClickListener((view, position) -> {
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }
            if (!deviceHasGoodCamera || position != 0) {
                if (deviceHasGoodCamera) {
                    position--;
                }
                ArrayList<Object> arrayList = getAllPhotosArray();
                if (position < 0 || position >= arrayList.size()) {
                    return;
                }
                PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                PhotoViewer.getInstance().setParentAlert(ChatAttachAlert.this);
                PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos);
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
                openCamera(true);
            }
        });
        attachPhotoRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkCameraViewPosition();
            }
        });

        views[11] = mediaBanTooltip = new CorrectlyMeasuringTextView(context);
        mediaBanTooltip.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), 0xff464646));
        mediaBanTooltip.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        mediaBanTooltip.setGravity(Gravity.CENTER_VERTICAL);
        mediaBanTooltip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        mediaBanTooltip.setTextColor(0xffffffff);
        mediaBanTooltip.setVisibility(View.INVISIBLE);
        attachView.addView(mediaBanTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 14, 0, 14, 0));

        views[9] = progressView = new EmptyTextProgressView(context);
        if (Build.VERSION.SDK_INT >= 23 && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            progressView.setText(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            progressView.setTextSize(16);
        } else {
            progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            progressView.setTextSize(20);
        }
        attachView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoRecyclerView.setEmptyView(progressView);

        views[10] = lineView = new View(getContext()) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        lineView.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine));
        attachView.addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT));
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("ChatCamera", R.string.ChatCamera),
                LocaleController.getString("ChatGallery", R.string.ChatGallery),
                LocaleController.getString("ChatVideo", R.string.ChatVideo),
                LocaleController.getString("AttachMusic", R.string.AttachMusic),
                LocaleController.getString("ChatDocument", R.string.ChatDocument),
                LocaleController.getString("AttachContact", R.string.AttachContact),
                LocaleController.getString("ChatLocation", R.string.ChatLocation),
                ""
        };
        for (int a = 0; a < 8; a++) {
            if (!(baseFragment instanceof ChatActivity)) {
                if (a == 2 || a == 3 || a == 5 || a == 6) {
                    continue;
                }
            }
            AttachButton attachButton = new AttachButton(context);
            attachButtons.add(attachButton);
            attachButton.setTextAndIcon(items[a], Theme.chat_attachButtonDrawables[a]);
            attachView.addView(attachButton, LayoutHelper.createFrame(85, 90, Gravity.LEFT | Gravity.TOP));
            attachButton.setTag(a);
            views[a] = attachButton;
            if (a == 7) {
                sendPhotosButton = attachButton;
                sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            } else if (a == 4) {
                sendDocumentsButton = attachButton;
            }
            attachButton.setOnClickListener(v -> {
                if (buttonPressed) {
                    return;
                }
                buttonPressed = true;
                delegate.didPressedButton((Integer) v.getTag());
            });
        }

        hintTextView = new TextView(context);
        hintTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
        hintTextView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintTextView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        hintTextView.setText(LocaleController.getString("AttachBotsHelp", R.string.AttachBotsHelp));
        hintTextView.setGravity(Gravity.CENTER_VERTICAL);
        hintTextView.setVisibility(View.INVISIBLE);
        hintTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.scroll_tip, 0, 0, 0);
        hintTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        attachView.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.RIGHT | Gravity.BOTTOM, 5, 0, 5, 5));

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }

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
                int cx = getMeasuredWidth() / 2;
                int cy = getMeasuredHeight() / 2;
                int cx2;
                int cy2;
                shutterButton.layout(cx - shutterButton.getMeasuredWidth() / 2, cy - shutterButton.getMeasuredHeight() / 2, cx + shutterButton.getMeasuredWidth() / 2, cy + shutterButton.getMeasuredHeight() / 2);
                if (getMeasuredWidth() == AndroidUtilities.dp(100)) {
                    cx = cx2 = getMeasuredWidth() / 2;
                    cy2 = cy + cy / 2 + AndroidUtilities.dp(17);
                    cy = cy / 2 - AndroidUtilities.dp(17);
                } else {
                    cx2 = cx + cx / 2 + AndroidUtilities.dp(17);
                    cx = cx / 2 - AndroidUtilities.dp(17);
                    cy = cy2 = getMeasuredHeight() / 2;
                }
                switchCameraButton.layout(cx2 - switchCameraButton.getMeasuredWidth() / 2, cy2 - switchCameraButton.getMeasuredHeight() / 2, cx2 + switchCameraButton.getMeasuredWidth() / 2, cy2 + switchCameraButton.getMeasuredHeight() / 2);
                for (int a = 0; a < 2; a++) {
                    flashModeButton[a].layout(cx - flashModeButton[a].getMeasuredWidth() / 2, cy - flashModeButton[a].getMeasuredHeight() / 2, cx + flashModeButton[a].getMeasuredWidth() / 2, cy + flashModeButton[a].getMeasuredHeight() / 2);
                }
            }
        };
        cameraPanel.setVisibility(View.GONE);
        cameraPanel.setAlpha(0.0f);
        container.addView(cameraPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.LEFT | Gravity.BOTTOM));

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

        shutterButton = new ShutterButton(context);
        cameraPanel.addView(shutterButton, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
        shutterButton.setDelegate(new ShutterButton.ShutterButtonDelegate() {

            private File outputFile;

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
                outputFile = AndroidUtilities.generateVideoPath();
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
                final File cameraFile = AndroidUtilities.generatePicturePath();
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
                    openPhotoViewer(new MediaController.PhotoEntry(0, lastImageId--, 0, cameraFile.getAbsolutePath(), orientation, false), sameTakePictureOrientation, false);
                });
            }
        });

        switchCameraButton = new ImageView(context);
        switchCameraButton.setScaleType(ImageView.ScaleType.CENTER);
        cameraPanel.addView(switchCameraButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        switchCameraButton.setOnClickListener(v -> {
            if (takingPhoto || cameraView == null || !cameraView.isInitied()) {
                return;
            }
            cameraInitied = false;
            cameraView.switchCamera();
            ObjectAnimator animator = ObjectAnimator.ofFloat(switchCameraButton, "scaleX", 0.0f).setDuration(100);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    switchCameraButton.setImageResource(cameraView != null && cameraView.isFrontface() ? R.drawable.camera_revert1 : R.drawable.camera_revert2);
                    ObjectAnimator.ofFloat(switchCameraButton, "scaleX", 1.0f).setDuration(100).start();
                }
            });
            animator.start();
        });

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
                        ObjectAnimator.ofFloat(currentImage, "translationY", 0, AndroidUtilities.dp(48)),
                        ObjectAnimator.ofFloat(nextImage, "translationY", -AndroidUtilities.dp(48), 0),
                        ObjectAnimator.ofFloat(currentImage, "alpha", 1.0f, 0.0f),
                        ObjectAnimator.ofFloat(nextImage, "alpha", 0.0f, 1.0f));
                animatorSet.setDuration(200);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        flashAnimationInProgress = false;
                        currentImage.setVisibility(View.INVISIBLE);
                    }
                });
                animatorSet.start();
            });
        }

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
        } else {
            maxSelectedPhotos = -1;
        }
        adapter.notifyDataSetChanged();
        for (int a = 0; a < 4; a++) {
            boolean enabled;
            if (a < 2) {
                enabled = editingMessageObject == null || !editingMessageObject.hasValidGroupId();
            } else {
                enabled = editingMessageObject == null;
            }
            attachButtons.get(3 + a).setEnabled(enabled);
            attachButtons.get(3 + a).setAlpha(enabled ? 1.0f : 0.2f);
        }
    }

    public MessageObject getEditingMessageObject() {
        return editingMessageObject;
    }

    private void updatePhotosCounter() {
        if (counterTextView == null) {
            return;
        }
        boolean hasVideo = false;
        for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
            if (photoEntry.isVideo) {
                hasVideo = true;
                break;
            }
        }
        if (hasVideo) {
            counterTextView.setText(LocaleController.formatPluralString("Media", selectedPhotos.size()).toUpperCase());
        } else {
            counterTextView.setText(LocaleController.formatPluralString("Photos", selectedPhotos.size()).toUpperCase());
        }
    }

    private void openPhotoViewer(MediaController.PhotoEntry entry, final boolean sameTakePictureOrientation, boolean external) {
        if (entry != null) {
            cameraPhotos.add(entry);
            selectedPhotos.put(entry.imageId, entry);
            selectedPhotosOrder.add(entry.imageId);
            updatePhotosButton();
            photoAttachAdapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
        if (entry != null && !external && cameraPhotos.size() > 1) {
            updatePhotosCounter();
            CameraController.getInstance().startPreview(cameraView.getCameraSession());
            mediaCaptured = false;
            return;
        }
        if (cameraPhotos.isEmpty()) {
            return;
        }
        cancelTakingPhotos = true;
        PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
        PhotoViewer.getInstance().setParentAlert(ChatAttachAlert.this);
        PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos);

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
                    photoAttachAdapter.notifyDataSetChanged();
                    cameraAttachAdapter.notifyDataSetChanged();
                    updatePhotosButton();
                }
                return true;
            }

            @Override
            public void needAddMorePhotos() {
                cancelTakingPhotos = false;
                if (mediaFromExternalCamera) {
                    delegate.didPressedButton(0);
                    return;
                }
                if (!cameraOpened) {
                    openCamera(false);
                }
                counterTextView.setVisibility(View.VISIBLE);
                cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
                counterTextView.setAlpha(1.0f);
                updatePhotosCounter();
            }

            @Override
            public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
                if (cameraPhotos.isEmpty() || baseFragment == null) {
                    return;
                }
                if (videoEditedInfo != null && index >= 0 && index < cameraPhotos.size()) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) cameraPhotos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                }
                for (int a = 0, size = cameraPhotos.size(); a < size; a++) {
                    AndroidUtilities.addMediaToGallery(((MediaController.PhotoEntry) cameraPhotos.get(a)).path);
                }
                delegate.didPressedButton(8);
                cameraPhotos.clear();
                selectedPhotosOrder.clear();
                selectedPhotos.clear();
                photoAttachAdapter.notifyDataSetChanged();
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
                int count = attachPhotoRecyclerView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = attachPhotoRecyclerView.getChildAt(a);
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

    private boolean processTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        if (!pressed && event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (!takingPhoto) {
                pressed = true;
                maybeStartDraging = true;
                lastY = event.getY();
            }
        } else if (pressed) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
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
                        if (cameraPanel.getTag() == null) {
                            cameraPanel.setTag(1);
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(cameraPanel, "alpha", 0.0f),
                                    ObjectAnimator.ofFloat(counterTextView, "alpha", 0.0f),
                                    ObjectAnimator.ofFloat(flashModeButton[0], "alpha", 0.0f),
                                    ObjectAnimator.ofFloat(flashModeButton[1], "alpha", 0.0f),
                                    ObjectAnimator.ofFloat(cameraPhotoRecyclerView, "alpha", 0.0f));
                            animatorSet.setDuration(200);
                            animatorSet.start();
                        }
                    }
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                pressed = false;
                if (dragging) {
                    dragging = false;
                    if (cameraView != null) {
                        if (Math.abs(cameraView.getTranslationY()) > cameraView.getMeasuredHeight() / 6.0f) {
                            closeCamera(true);
                        } else {
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(cameraView, "translationY", 0.0f),
                                    ObjectAnimator.ofFloat(cameraPanel, "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(counterTextView, "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(flashModeButton[0], "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(flashModeButton[1], "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(cameraPhotoRecyclerView, "alpha", 1.0f));
                            animatorSet.setDuration(250);
                            animatorSet.setInterpolator(interpolator);
                            animatorSet.start();
                            cameraPanel.setTag(null);
                        }
                    }
                } else if (cameraView != null) {
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

    public void checkColors() {
        for (int a = 0, count = attachButtons.size(); a < count; a++) {
            attachButtons.get(a).textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        }
        lineView.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine));
        Theme.setDrawableColor(hintTextView.getBackground(), Theme.getColor(Theme.key_chat_gifSaveHintBackground));
        if (hintTextView != null) {
            hintTextView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        }
        if (listView != null) {
            listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(1);
            if (holder != null) {
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
            }
        }
        if (ciclePaint != null) {
            ciclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
        }
        Theme.setDrawableColor(shadowDrawable, Theme.getColor(Theme.key_dialogBackground));
        if (cameraImageView != null) {
            cameraImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
        }
        if (attachPhotoRecyclerView != null) {
            RecyclerView.ViewHolder holder = attachPhotoRecyclerView.findViewHolderForAdapterPosition(0);
            if (holder != null && holder.itemView instanceof PhotoAttachCameraCell) {
                ((PhotoAttachCameraCell) holder.itemView).getImageView().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
            }
        }
    }

    private void resetRecordState() {
        if (baseFragment == null) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            flashModeButton[a].setAlpha(1.0f);
        }
        switchCameraButton.setAlpha(1.0f);
        recordTime.setAlpha(0.0f);
        AndroidUtilities.cancelRunOnUIThread(videoRecordRunnable);
        videoRecordRunnable = null;
        AndroidUtilities.unlockOrientation(baseFragment.getParentActivity());
    }

    private void setCameraFlashModeIcon(ImageView imageView, String mode) {
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_OFF:
                imageView.setImageResource(R.drawable.flash_off);
                break;
            case Camera.Parameters.FLASH_MODE_ON:
                imageView.setImageResource(R.drawable.flash_on);
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                imageView.setImageResource(R.drawable.flash_auto);
                break;
        }
    }

    @Override
    protected boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == cameraView) {
            if (cameraOpened && !cameraAnimationInProgress) {
                cameraView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                return true;
            }
        } else if (view == cameraPanel) {
            if (isPortrait) {
                cameraPanel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), View.MeasureSpec.EXACTLY));
            } else {
                cameraPanel.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
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
                    cameraPanel.layout(0, bottom - AndroidUtilities.dp(100 + 96), width, bottom - AndroidUtilities.dp(96));
                } else {
                    cameraPanel.layout(0, bottom - AndroidUtilities.dp(100), width, bottom);
                }
            } else {
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cameraPanel.layout(right - AndroidUtilities.dp(100 + 96), 0, right - AndroidUtilities.dp(96), height);
                } else {
                    cameraPanel.layout(right - AndroidUtilities.dp(100), 0, right, height);
                }
            }
            return true;
        } else if (view == counterTextView) {
            int cx;
            int cy;
            if (isPortrait) {
                cx = (width - counterTextView.getMeasuredWidth()) / 2;
                cy = bottom - AndroidUtilities.dp(100 + 16 + 38);
                counterTextView.setRotation(0);
                if (cameraPhotoRecyclerView.getVisibility() == View.VISIBLE) {
                    cy -= AndroidUtilities.dp(96);
                }
            } else {
                cx = right - AndroidUtilities.dp(100 + 16 + 38);
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

    private void hideHint() {
        if (hideHintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideHintRunnable);
            hideHintRunnable = null;
        }
        if (hintTextView == null) {
            return;
        }
        currentHintAnimation = new AnimatorSet();
        currentHintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f)
        );
        currentHintAnimation.setInterpolator(decelerateInterpolator);
        currentHintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentHintAnimation == null || !currentHintAnimation.equals(animation)) {
                    return;
                }
                currentHintAnimation = null;
                if (hintTextView != null) {
                    hintTextView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentHintAnimation != null && currentHintAnimation.equals(animation)) {
                    currentHintAnimation = null;
                }
            }
        });
        currentHintAnimation.setDuration(300);
        currentHintAnimation.start();
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
        if (cameraView == null) {
            return;
        }
        if (cameraPhotos.isEmpty()) {
            counterTextView.setVisibility(View.INVISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.GONE);
        } else {
            counterTextView.setVisibility(View.VISIBLE);
            cameraPhotoRecyclerView.setVisibility(View.VISIBLE);
        }
        cameraPanel.setVisibility(View.VISIBLE);
        cameraPanel.setTag(null);
        animateCameraValues[0] = 0;
        animateCameraValues[1] = AndroidUtilities.dp(80) - cameraViewOffsetX;
        animateCameraValues[2] = AndroidUtilities.dp(80) - cameraViewOffsetY;
        if (animated) {
            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(ChatAttachAlert.this, "cameraOpenProgress", 0.0f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPanel, "alpha", 1.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, "alpha", 1.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, "alpha", 1.0f));
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(flashModeButton[a], "alpha", 1.0f));
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
    }

    public void onActivityResultFragment(int requestCode, Intent data, String currentPicturePath) {
        if (baseFragment == null || baseFragment.getParentActivity() == null) {
            return;
        }
        mediaFromExternalCamera = true;
        if (requestCode == 0) {
            PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
            PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos);
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
            openPhotoViewer(new MediaController.PhotoEntry(0, lastImageId--, 0, currentPicturePath, orientation, false), false, true);
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
                AndroidUtilities.addMediaToGallery(currentPicturePath);
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
        animateCameraValues[1] = AndroidUtilities.dp(80) - cameraViewOffsetX;
        animateCameraValues[2] = AndroidUtilities.dp(80) - cameraViewOffsetY;
        if (animated) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
            animateCameraValues[0] = layoutParams.topMargin = (int) cameraView.getTranslationY();
            cameraView.setLayoutParams(layoutParams);
            cameraView.setTranslationY(0);

            cameraAnimationInProgress = true;
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(ChatAttachAlert.this, "cameraOpenProgress", 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPanel, "alpha", 0.0f));
            animators.add(ObjectAnimator.ofFloat(counterTextView, "alpha", 0.0f));
            animators.add(ObjectAnimator.ofFloat(cameraPhotoRecyclerView, "alpha", 0.0f));
            for (int a = 0; a < 2; a++) {
                if (flashModeButton[a].getVisibility() == View.VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(flashModeButton[a], "alpha", 0.0f));
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
                    cameraOpened = false;
                    if (cameraPanel != null) {
                        cameraPanel.setVisibility(View.GONE);
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
            cameraPhotoRecyclerView.setAlpha(0);
            counterTextView.setAlpha(0);
            cameraPanel.setVisibility(View.GONE);
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
            endWidth = container.getWidth();
            endHeight = container.getHeight()/* - AndroidUtilities.dp(100)*/;
        } else {
            endWidth = container.getWidth()/* - AndroidUtilities.dp(100)*/;
            endHeight = container.getHeight();
        }
        if (value == 0) {
            cameraView.setClipLeft(cameraViewOffsetX);
            cameraView.setClipTop(cameraViewOffsetY);
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
            cameraView.setClipLeft((int) (cameraViewOffsetX * (1.0f - value)));
            cameraView.setClipTop((int) (cameraViewOffsetY * (1.0f - value)));
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
    }

    @Keep
    public float getCameraOpenProgress() {
        return cameraOpenProgress;
    }

    private void checkCameraViewPosition() {
        if (!deviceHasGoodCamera) {
            return;
        }
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = attachPhotoRecyclerView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                if (Build.VERSION.SDK_INT >= 19) {
                    if (!child.isAttachedToWindow()) {
                        break;
                    }
                }
                child.getLocationInWindow(cameraViewLocation);
                cameraViewLocation[0] -= getLeftInset();
                float listViewX = listView.getX() + backgroundPaddingLeft - getLeftInset();
                if (cameraViewLocation[0] < listViewX) {
                    cameraViewOffsetX = (int) (listViewX - cameraViewLocation[0]);
                    if (cameraViewOffsetX >= AndroidUtilities.dp(80)) {
                        cameraViewOffsetX = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-150);
                        cameraViewLocation[1] = 0;
                    } else {
                        cameraViewLocation[0] += cameraViewOffsetX;
                    }
                } else {
                    cameraViewOffsetX = 0;
                }
                if (Build.VERSION.SDK_INT >= 21 && cameraViewLocation[1] < AndroidUtilities.statusBarHeight) {
                    cameraViewOffsetY = AndroidUtilities.statusBarHeight - cameraViewLocation[1];
                    if (cameraViewOffsetY >= AndroidUtilities.dp(80)) {
                        cameraViewOffsetY = 0;
                        cameraViewLocation[0] = AndroidUtilities.dp(-150);
                        cameraViewLocation[1] = 0;
                    } else {
                        cameraViewLocation[1] += cameraViewOffsetY;
                    }
                } else {
                    cameraViewOffsetY = 0;
                }
                applyCameraViewPosition();
                return;
            }
        }
        cameraViewOffsetX = 0;
        cameraViewOffsetY = 0;
        cameraViewLocation[0] = AndroidUtilities.dp(-150);
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
            int finalWidth = AndroidUtilities.dp(80) - cameraViewOffsetX;
            int finalHeight = AndroidUtilities.dp(80) - cameraViewOffsetY;

            FrameLayout.LayoutParams layoutParams;
            if (!cameraOpened) {
                cameraView.setClipLeft(cameraViewOffsetX);
                cameraView.setClipTop(cameraViewOffsetY);
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
            container.addView(cameraView, 1, LayoutHelper.createFrame(80, 80));
            cameraView.setDelegate(new CameraView.CameraViewDelegate() {
                @Override
                public void onCameraCreated(Camera camera) {

                }

                @Override
                public void onCameraInit() {
                    int count = attachPhotoRecyclerView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = attachPhotoRecyclerView.getChildAt(a);
                        if (child instanceof PhotoAttachCameraCell) {
                            child.setVisibility(View.INVISIBLE);
                            break;
                        }
                    }

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
                }
            });

            if (cameraIcon == null) {
                cameraIcon = new FrameLayout(baseFragment.getParentActivity());

                cameraImageView = new ImageView(baseFragment.getParentActivity());
                cameraImageView.setScaleType(ImageView.ScaleType.CENTER);
                cameraImageView.setImageResource(R.drawable.instant_camera);
                cameraImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
                cameraIcon.addView(cameraImageView, LayoutHelper.createFrame(80, 80, Gravity.RIGHT | Gravity.BOTTOM));
            }
            container.addView(cameraIcon, 2, LayoutHelper.createFrame(80, 80));

            cameraView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraView.setEnabled(mediaEnabled);
            cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
            cameraIcon.setEnabled(mediaEnabled);
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
        cameraView.destroy(async, null);
        container.removeView(cameraView);
        container.removeView(cameraIcon);
        cameraView = null;
        cameraIcon = null;
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = attachPhotoRecyclerView.getChildAt(a);
            if (child instanceof PhotoAttachCameraCell) {
                child.setVisibility(View.VISIBLE);
                return;
            }
        }
    }

    private void showHint() {
        if (editingMessageObject != null || !(baseFragment instanceof ChatActivity) || DataQuery.getInstance(currentAccount).inlineBots.isEmpty()) {
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (preferences.getBoolean("bothint", false)) {
            return;
        }
        hintShowed = true;

        hintTextView.setVisibility(View.VISIBLE);
        currentHintAnimation = new AnimatorSet();
        currentHintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f, 1.0f)
        );
        currentHintAnimation.setInterpolator(decelerateInterpolator);
        currentHintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentHintAnimation == null || !currentHintAnimation.equals(animation)) {
                    return;
                }
                currentHintAnimation = null;
                AndroidUtilities.runOnUIThread(hideHintRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (hideHintRunnable != this) {
                            return;
                        }
                        hideHintRunnable = null;
                        hideHint();
                    }
                }, 2000);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentHintAnimation != null && currentHintAnimation.equals(animation)) {
                    currentHintAnimation = null;
                }
            }
        });
        currentHintAnimation.setDuration(300);
        currentHintAnimation.start();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoaded) {
            if (photoAttachAdapter != null) {
                loading = false;
                progressView.showTextView();
                photoAttachAdapter.notifyDataSetChanged();
                cameraAttachAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.reloadInlineHints) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.cameraInitied) {
            checkCamera(false);
        }
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            listView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            listView.invalidate();
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void updatePhotosButton() {
        int count = selectedPhotos.size();
        if (count == 0) {
            sendPhotosButton.imageView.setBackgroundDrawable(Theme.chat_attachButtonDrawables[7]);
            sendPhotosButton.textView.setText("");
            if (baseFragment instanceof ChatActivity) {
                sendDocumentsButton.textView.setText(LocaleController.getString("ChatDocument", R.string.ChatDocument));
            }
        } else {
            sendPhotosButton.imageView.setBackgroundDrawable(Theme.chat_attachButtonDrawables[8]);
            if (baseFragment instanceof ChatActivity) {
                sendPhotosButton.textView.setText(LocaleController.formatString("SendItems", R.string.SendItems, String.format("(%d)", count)));
                if (editingMessageObject == null || !editingMessageObject.hasValidGroupId()) {
                    sendDocumentsButton.textView.setText(count == 1 ? LocaleController.getString("SendAsFile", R.string.SendAsFile) : LocaleController.getString("SendAsFiles", R.string.SendAsFiles));
                }
            } else {
                sendPhotosButton.textView.setText(LocaleController.formatString("UploadItems", R.string.UploadItems, String.format("(%d)", count)));
            }
        }

        if (Build.VERSION.SDK_INT >= 23 && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            progressView.setText(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            progressView.setTextSize(16);
        } else {
            progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            progressView.setTextSize(20);
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
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (albumEntry != null) {
            for (int a = 0; a < Math.min(100, albumEntry.photos.size()); a++) {
                MediaController.PhotoEntry photoEntry = albumEntry.photos.get(a);
                photoEntry.reset();
            }
        }
        if (currentHintAnimation != null) {
            currentHintAnimation.cancel();
            currentHintAnimation = null;
        }
        hintTextView.setAlpha(0.0f);
        hintTextView.setVisibility(View.INVISIBLE);
        attachPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        cameraPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        clearSelectedPhotos();
        layoutManager.scrollToPositionWithOffset(0, 1000000);
        updatePhotosButton();
    }

    public HashMap<Object, Object> getSelectedPhotos() {
        return selectedPhotos;
    }

    public ArrayList<Object> getSelectedPhotosOrder() {
        return selectedPhotosOrder;
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.cameraInitied);
        baseFragment = null;
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                if ((Integer) cell.getImageView().getTag() == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    private void onRevealAnimationEnd(boolean open) {
        NotificationCenter.getInstance(currentAccount).setAnimationInProgress(false);
        revealAnimationInProgress = false;
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (open && Build.VERSION.SDK_INT <= 19 && albumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
        if (open) {
            checkCamera(true);
            showHint();
        }
    }

    public void checkCamera(boolean request) {
        if (baseFragment == null) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        if (!SharedConfig.inappCamera) {
            deviceHasGoodCamera = false;
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                if (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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
        if (old != deviceHasGoodCamera && photoAttachAdapter != null) {
            photoAttachAdapter.notifyDataSetChanged();
        }
        if (isShowing() && deviceHasGoodCamera && baseFragment != null && backDrawable.getAlpha() != 0 && !revealAnimationInProgress && !cameraOpened) {
            showCamera();
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        onRevealAnimationEnd(true);
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = attachView;
                    break;
                case 1: {
                    FrameLayout frameLayout = new FrameLayout(mContext);
                    frameLayout.addView(new ShadowSectionCell(mContext), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    view = frameLayout;
                    break;
                }
                default:
                    FrameLayout frameLayout = new FrameLayout(mContext) {
                        @Override
                        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                            int diff = (right - left - AndroidUtilities.dp(85 * 4 + 20)) / 3;
                            for (int a = 0; a < 4; a++) {
                                int x = AndroidUtilities.dp(10) + (a % 4) * (AndroidUtilities.dp(85) + diff);
                                View child = getChildAt(a);
                                child.layout(x, 0, x + child.getMeasuredWidth(), child.getMeasuredHeight());
                            }
                        }
                    };
                    for (int a = 0; a < 4; a++) {
                        frameLayout.addView(new AttachBotButton(mContext));
                    }
                    view = frameLayout;
                    frameLayout.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(100)));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position == 1) {
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
            } else if (position > 1) {
                position -= 2;
                position *= 4;
                FrameLayout frameLayout = (FrameLayout) holder.itemView;
                for (int a = 0; a < 4; a++) {
                    AttachBotButton child = (AttachBotButton) frameLayout.getChildAt(a);
                    if (position + a >= DataQuery.getInstance(currentAccount).inlineBots.size()) {
                        child.setVisibility(View.INVISIBLE);
                    } else {
                        child.setVisibility(View.VISIBLE);
                        child.setTag(position + a);
                        child.setUser(MessagesController.getInstance(currentAccount).getUser(DataQuery.getInstance(currentAccount).inlineBots.get(position + a).peer.user_id));
                    }
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            if (editingMessageObject == null && baseFragment instanceof ChatActivity) {
                return 1 + (!DataQuery.getInstance(currentAccount).inlineBots.isEmpty() ? 1 + (int) Math.ceil(DataQuery.getInstance(currentAccount).inlineBots.size() / 4.0f) : 0);
            } else {
                return 1;
            }
        }

        @Override
        public int getItemViewType(int position) {
            switch (position) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                default:
                    return 2;
            }
        }
    }

    public void setMaxSelectedPhotos(int value) {
        maxSelectedPhotos = value;
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
            updatePhotosCounter();
            updateCheckedPhotoIndices();
            if (index >= 0) {
                object.reset();
                photoViewerProvider.updatePhotoAtIndex(index);
            }
            return position;
        } else {
            selectedPhotos.put(key, object);
            selectedPhotosOrder.add(key);
            updatePhotosCounter();
            return -1;
        }
    }

    private void clearSelectedPhotos() {
        boolean changed = false;
        if (!selectedPhotos.isEmpty()) {
            for (HashMap.Entry<Object, Object> entry : selectedPhotos.entrySet()) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry.getValue();
                photoEntry.reset();
            }
            selectedPhotos.clear();
            selectedPhotosOrder.clear();
            updatePhotosButton();
            changed = true;
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
            changed = true;
        }
        if (changed) {
            photoAttachAdapter.notifyDataSetChanged();
            cameraAttachAdapter.notifyDataSetChanged();
        }
    }

    private class PhotoAttachAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private boolean needCamera;
        private ArrayList<RecyclerListView.Holder> viewsCache = new ArrayList<>(8);

        public PhotoAttachAdapter(Context context, boolean camera) {
            mContext = context;
            needCamera = camera;
            for (int a = 0; a < 8; a++) {
                viewsCache.add(createHolder());
            }
        }

        public RecyclerListView.Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext);
            cell.setDelegate(v -> {
                if (!mediaEnabled) {
                    return;
                }
                int index = (Integer) v.getTag();
                MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                if (added && maxSelectedPhotos >= 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                    return;
                }
                int num = added ? selectedPhotosOrder.size() : -1;
                if (baseFragment instanceof ChatActivity && maxSelectedPhotos < 0) {
                    v.setChecked(num, added, true);
                } else {
                    v.setChecked(-1, added, true);
                }
                addToSelectedPhotos(photoEntry, index);
                int updateIndex = index;
                if (PhotoAttachAdapter.this == cameraAttachAdapter) {
                    if (photoAttachAdapter.needCamera && deviceHasGoodCamera) {
                        updateIndex++;
                    }
                    photoAttachAdapter.notifyItemChanged(updateIndex);
                } else {
                    cameraAttachAdapter.notifyItemChanged(updateIndex);
                }
                updatePhotosButton();
            });
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!needCamera || !deviceHasGoodCamera || position != 0) {
                if (needCamera && deviceHasGoodCamera) {
                    position--;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                MediaController.PhotoEntry photoEntry = getPhotoEntryAtPosition(position);
                cell.setPhotoEntry(photoEntry, needCamera, position == getItemCount() - 1);
                if (baseFragment instanceof ChatActivity && maxSelectedPhotos < 0) {
                    cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId), selectedPhotos.containsKey(photoEntry.imageId), false);
                } else {
                    cell.setChecked(-1, selectedPhotos.containsKey(photoEntry.imageId), false);
                }
                cell.getImageView().setTag(position);
                cell.setTag(position);
                cell.setIsVertical(this == cameraAttachAdapter && cameraPhotoLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL);
            } else if (needCamera && deviceHasGoodCamera && position == 0) {
                if (cameraView != null && cameraView.isInitied()) {
                    holder.itemView.setVisibility(View.INVISIBLE);
                } else {
                    holder.itemView.setVisibility(View.VISIBLE);
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
                case 1:
                    holder = new RecyclerListView.Holder(new PhotoAttachCameraCell(mContext));
                    break;
                default:
                    if (!viewsCache.isEmpty()) {
                        holder = viewsCache.get(0);
                        viewsCache.remove(0);
                    } else {
                        holder = createHolder();
                    }
                    break;
            }

            return holder;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (needCamera && deviceHasGoodCamera) {
                count++;
            }
            count += cameraPhotos.size();
            MediaController.AlbumEntry albumEntry;
            if (baseFragment instanceof ChatActivity) {
                albumEntry = MediaController.allMediaAlbumEntry;
            } else {
                albumEntry = MediaController.allPhotosAlbumEntry;
            }
            if (albumEntry != null) {
                count += albumEntry.photos.size();
            }
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if (needCamera && deviceHasGoodCamera && position == 0) {
                return 1;
            }
            return 0;
        }
    }

    private void setUseRevealAnimation(boolean value) {
        if (!value || value && Build.VERSION.SDK_INT >= 18 && !AndroidUtilities.isTablet() && (Build.VERSION.SDK_INT < 26 || Build.VERSION.SDK_INT >= 28) && baseFragment instanceof ChatActivity) {
            useRevealAnimation = value;
        }
    }

    @Keep
    @SuppressLint("NewApi")
    protected void setRevealRadius(float radius) {
        revealRadius = radius;
        if (Build.VERSION.SDK_INT <= 19) {
            listView.invalidate();
        }
        if (!isDismissed()) {
            for (int a = 0; a < innerAnimators.size(); a++) {
                InnerAnimator innerAnimator = innerAnimators.get(a);
                if (innerAnimator.startRadius > radius) {
                    continue;
                }
                innerAnimator.animatorSet.start();
                innerAnimators.remove(a);
                a--;
            }
        }
    }

    @Keep
    protected float getRevealRadius() {
        return revealRadius;
    }

    @SuppressLint("NewApi")
    private void startRevealAnimation(final boolean open) {
        containerView.setTranslationY(0);

        final AnimatorSet animatorSet = new AnimatorSet();

        View view = delegate.getRevealView();
        if (view.getVisibility() == View.VISIBLE && ((ViewGroup) view.getParent()).getVisibility() == View.VISIBLE) {
            final int coords[] = new int[2];
            view.getLocationInWindow(coords);
            float top;
            if (Build.VERSION.SDK_INT <= 19) {
                top = AndroidUtilities.displaySize.y - containerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight;
            } else {
                top = containerView.getY();
            }
            revealX = coords[0] + view.getMeasuredWidth() / 2;
            revealY = (int) (coords[1] + view.getMeasuredHeight() / 2 - top);
            if (Build.VERSION.SDK_INT <= 19) {
                revealY -= AndroidUtilities.statusBarHeight;
            }
        } else {
            revealX = AndroidUtilities.displaySize.x / 2 + backgroundPaddingLeft;
            revealY = (int) (AndroidUtilities.displaySize.y - containerView.getY());
        }

        int corners[][] = new int[][]{
                {0, 0},
                {0, AndroidUtilities.dp(304)},
                {containerView.getMeasuredWidth(), 0},
                {containerView.getMeasuredWidth(), AndroidUtilities.dp(304)}
        };
        int finalRevealRadius = 0;
        int y = revealY - scrollOffsetY + backgroundPaddingTop;
        for (int a = 0; a < 4; a++) {
            finalRevealRadius = Math.max(finalRevealRadius, (int) Math.ceil(Math.sqrt((revealX - corners[a][0]) * (revealX - corners[a][0]) + (y - corners[a][1]) * (y - corners[a][1]))));
        }
        int finalRevealX = revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth();

        ArrayList<Animator> animators = new ArrayList<>(3);
        animators.add(ObjectAnimator.ofFloat(this, "revealRadius", open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
        animators.add(ObjectAnimator.ofInt(backDrawable, "alpha", open ? 51 : 0));
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                animators.add(ViewAnimationUtils.createCircularReveal(containerView, finalRevealX, revealY, open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
            } catch (Exception e) {
                FileLog.e(e);
            }
            animatorSet.setDuration(320);
        } else {
            if (!open) {
                animatorSet.setDuration(200);
                containerView.setPivotX(revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth());
                containerView.setPivotY(revealY);
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleX", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleY", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "alpha", 0.0f));
            } else {
                animatorSet.setDuration(250);
                containerView.setScaleX(1);
                containerView.setScaleY(1);
                containerView.setAlpha(1);
                if (Build.VERSION.SDK_INT <= 19) {
                    animatorSet.setStartDelay(20);
                }
            }
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    onRevealAnimationEnd(open);
                    containerView.invalidate();
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (!open) {
                        try {
                            dismissInternal();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentSheetAnimation != null && animatorSet.equals(animation)) {
                    currentSheetAnimation = null;
                }
            }
        });

        if (open) {
            innerAnimators.clear();
            NotificationCenter.getInstance(currentAccount).setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload});
            NotificationCenter.getInstance(currentAccount).setAnimationInProgress(true);
            revealAnimationInProgress = true;

            int count = Build.VERSION.SDK_INT <= 19 ? 12 : 8;
            for (int a = 0; a < count; a++) {
                if (views[a] == null) {
                    continue;
                }
                if (Build.VERSION.SDK_INT <= 19) {
                    if (a < 8) {
                        views[a].setScaleX(0.1f);
                        views[a].setScaleY(0.1f);
                    }
                    views[a].setAlpha(0.0f);
                } else {
                    views[a].setScaleX(0.7f);
                    views[a].setScaleY(0.7f);
                }

                InnerAnimator innerAnimator = new InnerAnimator();

                int buttonX = views[a].getLeft() + views[a].getMeasuredWidth() / 2;
                int buttonY = views[a].getTop() + attachView.getTop() + views[a].getMeasuredHeight() / 2;
                float dist = (float) Math.sqrt((revealX - buttonX) * (revealX - buttonX) + (revealY - buttonY) * (revealY - buttonY));
                float vecX = (revealX - buttonX) / dist;
                float vecY = (revealY - buttonY) / dist;
                views[a].setPivotX(views[a].getMeasuredWidth() / 2 + vecX * AndroidUtilities.dp(20));
                views[a].setPivotY(views[a].getMeasuredHeight() / 2 + vecY * AndroidUtilities.dp(20));
                innerAnimator.startRadius = dist - AndroidUtilities.dp(27 * 3);

                views[a].setTag(R.string.AppName, 1);
                animators = new ArrayList<>();
                final AnimatorSet animatorSetInner;
                if (a < 8) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleX", 0.7f, 1.05f));
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleY", 0.7f, 1.05f));

                    animatorSetInner = new AnimatorSet();
                    animatorSetInner.playTogether(
                            ObjectAnimator.ofFloat(views[a], "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(views[a], "scaleY", 1.0f));
                    animatorSetInner.setDuration(100);
                    animatorSetInner.setInterpolator(decelerateInterpolator);
                } else {
                    animatorSetInner = null;
                }
                if (Build.VERSION.SDK_INT <= 19) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "alpha", 1.0f));
                }
                innerAnimator.animatorSet = new AnimatorSet();
                innerAnimator.animatorSet.playTogether(animators);
                innerAnimator.animatorSet.setDuration(150);
                innerAnimator.animatorSet.setInterpolator(decelerateInterpolator);
                innerAnimator.animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animatorSetInner != null) {
                            animatorSetInner.start();
                        }
                    }
                });
                innerAnimators.add(innerAnimator);
            }
        }
        currentSheetAnimation = animatorSet;
        animatorSet.start();
    }

    @Override
    public void dismissInternal() {
        if (containerView != null) {
            containerView.setVisibility(View.INVISIBLE);
        }
        super.dismissInternal();
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        if (baseFragment instanceof ChatActivity) {
            TLRPC.Chat chat = ((ChatActivity) baseFragment).getCurrentChat();
            if (ChatObject.isChannel(chat)) {
                mediaEnabled = chat.banned_rights == null || !chat.banned_rights.send_media;
                for (int a = 0; a < 5; a++) {
                    boolean enabled;
                    if (a > 2 && editingMessageObject != null && editingMessageObject.hasValidGroupId()) {
                        attachButtons.get(3 + a).setEnabled(false);
                        attachButtons.get(3 + a).setAlpha(0.2f);
                    } else {
                        attachButtons.get(a).setAlpha(mediaEnabled ? 1.0f : 0.2f);
                        attachButtons.get(a).setEnabled(mediaEnabled);
                    }
                }
                attachPhotoRecyclerView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
                attachPhotoRecyclerView.setEnabled(mediaEnabled);
                if (!mediaEnabled) {
                    if (AndroidUtilities.isBannedForever(chat.banned_rights.until_date)) {
                        mediaBanTooltip.setText(LocaleController.formatString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
                    } else {
                        mediaBanTooltip.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                    }
                }
                mediaBanTooltip.setVisibility(mediaEnabled ? View.INVISIBLE : View.VISIBLE);
                if (cameraView != null) {
                    cameraView.setAlpha(mediaEnabled ? 1.0f : 0.2f);
                    cameraView.setEnabled(mediaEnabled);
                }
                if (cameraIcon != null) {
                    cameraIcon.setAlpha(mediaEnabled ? 1.0f : 0.2f);
                    cameraIcon.setEnabled(mediaEnabled);
                }
            }
        }
        if (useRevealAnimation) {
            startRevealAnimation(true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomCloseAnimation() {
        if (useRevealAnimation) {
            backDrawable.setAlpha(51);
            startRevealAnimation(false);
            return true;
        }
        return false;
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
