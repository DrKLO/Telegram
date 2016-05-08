/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Scroller;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.query.SharedMediaQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoCropView;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.PickerBottomLayout;
import org.telegram.ui.Components.PhotoViewerCaptionEnterView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayoutPhoto;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

@SuppressWarnings("unchecked")
public class PhotoViewer implements NotificationCenter.NotificationCenterDelegate, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private int classGuid;
    private PhotoViewerProvider placeProvider;
    private boolean isVisible;

    private Activity parentActivity;
    private Context actvityContext;

    private ActionBar actionBar;
    private boolean isActionBarVisible = true;

    private static Drawable[] progressDrawables;

    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayoutDrawer containerView;
    private FrameLayoutTouchListener windowView;
    private ClippingImageView animatingImageView;
    private FrameLayout bottomLayout;
    private TextView nameTextView;
    private TextView dateTextView;
    private ActionBarMenuItem menuItem;
    private ImageView shareButton;
    private BackgroundDrawable backgroundDrawable = new BackgroundDrawable(0xff000000);
    private CheckBox checkImageView;
    private PickerBottomLayout pickerView;
    private PickerBottomLayout editorDoneLayout;
    private RadialProgressView radialProgressViews[] = new RadialProgressView[3];
    private ActionBarMenuItem cropItem;
    private ActionBarMenuItem tuneItem;
    private ActionBarMenuItem captionItem;
    private ActionBarMenuItem captionDoneItem;
    private AnimatorSetProxy currentActionBarAnimation;
    private PhotoCropView photoCropView;
    private PhotoFilterView photoFilterView;
    private AlertDialog visibleDialog;
    private TextView captionTextView;
    private TextView captionTextViewOld;
    private TextView captionTextViewNew;
    private PhotoViewerCaptionEnterView captionEditText;
    private boolean canShowBottom = true;
    private int sendPhotoType = 0;
    private boolean needCaptionLayout;

    private float animationValues[][] = new float[2][8];

    private ChatActivity parentChatActivity;
    private MentionsAdapter mentionsAdapter;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private AnimatorSetProxy mentionListAnimation;
    private boolean allowMentions;

    private int animationInProgress = 0;
    private long transitionAnimationStartTime = 0;
    private Runnable animationEndRunnable = null;
    private PlaceProviderObject showAfterAnimation;
    private PlaceProviderObject hideAfterAnimation;
    private boolean disableShowCheck = false;

    private String lastTitle;

    private int currentEditMode;

    private ImageReceiver leftImage = new ImageReceiver();
    private ImageReceiver centerImage = new ImageReceiver();
    private ImageReceiver rightImage = new ImageReceiver();
    private int currentIndex;
    private MessageObject currentMessageObject;
    private TLRPC.FileLocation currentFileLocation;
    private String currentFileNames[] = new String[3];
    private PlaceProviderObject currentPlaceObject;
    private String currentPathObject;
    private Bitmap currentThumb = null;

    private int avatarsUserId;
    private long currentDialogId;
    private long mergeDialogId;
    private int totalImagesCount;
    private int totalImagesCountMerge;
    private boolean isFirstLoading;
    private boolean needSearchImageInArr;
    private boolean loadingMoreImages;
    private boolean endReached[] = new boolean[] {false, true};
    private boolean opennedFromMedia;

    private boolean draggingDown = false;
    private float dragY;
    private float translationX;
    private float translationY;
    private float scale = 1;
    private float animateToX;
    private float animateToY;
    private float animateToScale;
    private float animationValue;
    private int currentRotation;
    private long animationStartTime;
    private AnimatorSetProxy imageMoveAnimation;
    private AnimatorSetProxy changeModeAnimation;
    private GestureDetector gestureDetector;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private float pinchStartDistance;
    private float pinchStartScale = 1;
    private float pinchCenterX;
    private float pinchCenterY;
    private float pinchStartX;
    private float pinchStartY;
    private float moveStartX;
    private float moveStartY;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;
    private boolean canZoom = true;
    private boolean changingPage = false;
    private boolean zooming = false;
    private boolean moving = false;
    private boolean doubleTap = false;
    private boolean invalidCoords = false;
    private boolean canDragDown = true;
    private boolean zoomAnimation = false;
    private boolean discardTap = false;
    private int switchImageAfterAnimation = 0;
    private VelocityTracker velocityTracker = null;
    private Scroller scroller = null;

    private ArrayList<MessageObject> imagesArrTemp = new ArrayList<>();
    private HashMap<Integer, MessageObject>[] imagesByIdsTemp = new HashMap[] {new HashMap<>(), new HashMap<>()};
    private ArrayList<MessageObject> imagesArr = new ArrayList<>();
    private HashMap<Integer, MessageObject>[] imagesByIds = new HashMap[] {new HashMap<>(), new HashMap<>()};
    private ArrayList<TLRPC.FileLocation> imagesArrLocations = new ArrayList<>();
    private ArrayList<TLRPC.Photo> avatarsArr = new ArrayList<>();
    private ArrayList<Integer> imagesArrLocationsSizes = new ArrayList<>();
    private ArrayList<Object> imagesArrLocals = new ArrayList<>();
    private TLRPC.FileLocation currentUserAvatarLocation = null;

    private final static int gallery_menu_save = 1;
    private final static int gallery_menu_showall = 2;
    private final static int gallery_menu_send = 3;
    private final static int gallery_menu_crop = 4;
    private final static int gallery_menu_delete = 6;
    private final static int gallery_menu_tune = 7;
    private final static int gallery_menu_caption = 8;
    private final static int gallery_menu_caption_done = 9;

    private final static int PAGE_SPACING = AndroidUtilities.dp(30);

    private static DecelerateInterpolator decelerateInterpolator = null;
    private static Paint progressPaint = null;

    private class BackgroundDrawable extends ColorDrawable {

        private Runnable drawRunnable;

        public BackgroundDrawable(int color) {
            super(color);
        }

        @Override
        public void setAlpha(int alpha) {
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(!isVisible || alpha != 255);
            }
            super.setAlpha(alpha);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (getAlpha() != 0) {
                if (drawRunnable != null) {
                    drawRunnable.run();
                    drawRunnable = null;
                }
            }
        }
    }

    private class RadialProgressView {

        private long lastUpdateTime = 0;
        private float radOffset = 0;
        private float currentProgress = 0;
        private float animationProgressStart = 0;
        private long currentProgressTime = 0;
        private float animatedProgressValue = 0;
        private RectF progressRect = new RectF();
        private int backgroundState = -1;
        private View parent = null;
        private int size = AndroidUtilities.dp(64);
        private int previousBackgroundState = -2;
        private float animatedAlphaValue = 1.0f;
        private float alpha = 1.0f;
        private float scale = 1.0f;

        public RadialProgressView(Context context, View parentView) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator(1.5f);
                progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                progressPaint.setStyle(Paint.Style.STROKE);
                progressPaint.setStrokeCap(Paint.Cap.ROUND);
                progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
                progressPaint.setColor(0xffffffff);
            }
            parent = parentView;
        }

        private void updateAnimation() {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;

            if (animatedProgressValue != 1) {
                radOffset += 360 * dt / 3000.0f;
                float progressDiff = currentProgress - animationProgressStart;
                if (progressDiff > 0) {
                    currentProgressTime += dt;
                    if (currentProgressTime >= 300) {
                        animatedProgressValue = currentProgress;
                        animationProgressStart = currentProgress;
                        currentProgressTime = 0;
                    } else {
                        animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                    }
                }
                parent.invalidate();
            }
            if (animatedProgressValue >= 1 && previousBackgroundState != -2) {
                animatedAlphaValue -= dt / 200.0f;
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f;
                    previousBackgroundState = -2;
                }
                parent.invalidate();
            }
        }

        public void setProgress(float value, boolean animated) {
            if (!animated) {
                animatedProgressValue = value;
                animationProgressStart = value;
            } else {
                animationProgressStart = animatedProgressValue;
            }
            currentProgress = value;
            currentProgressTime = 0;
        }

        public void setBackgroundState(int state, boolean animated) {
            lastUpdateTime = System.currentTimeMillis();
            if (animated && backgroundState != state) {
                previousBackgroundState = backgroundState;
                animatedAlphaValue = 1.0f;
            } else {
                previousBackgroundState = -2;
            }
            backgroundState = state;
            parent.invalidate();
        }

        public void setAlpha(float value) {
            alpha = value;
        }

        public void setScale(float value) {
            scale = value;
        }

        public void onDraw(Canvas canvas) {
            int sizeScaled = (int) (size * scale);
            int x = (getContainerViewWidth() - sizeScaled) / 2;
            int y = (getContainerViewHeight() - sizeScaled) / 2;

            if (previousBackgroundState >= 0 && previousBackgroundState < 4) {
                Drawable drawable = progressDrawables[previousBackgroundState];
                if (drawable != null) {
                    drawable.setAlpha((int) (255 * animatedAlphaValue * alpha));
                    drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState >= 0 && backgroundState < 4) {
                Drawable drawable = progressDrawables[backgroundState];
                if (drawable != null) {
                    if (previousBackgroundState != -2) {
                        drawable.setAlpha((int) (255 * (1.0f - animatedAlphaValue) * alpha));
                    } else {
                        drawable.setAlpha((int) (255 * alpha));
                    }
                    drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState == 0 || backgroundState == 1 || previousBackgroundState == 0 || previousBackgroundState == 1) {
                int diff = AndroidUtilities.dp(4);
                if (previousBackgroundState != -2) {
                    progressPaint.setAlpha((int) (255 * animatedAlphaValue * alpha));
                } else {
                    progressPaint.setAlpha((int) (255 * alpha));
                }
                progressRect.set(x + diff, y + diff, x + sizeScaled - diff, y + sizeScaled - diff);
                canvas.drawArc(progressRect, -90 + radOffset, Math.max(4, 360 * animatedProgressValue), false, progressPaint);
                updateAnimation();
            }
        }
    }

    public static class PlaceProviderObject {
        public ImageReceiver imageReceiver;
        public int viewX;
        public int viewY;
        public View parentView;
        public Bitmap thumb;
        public int user_id;
        public int index;
        public int size;
        public int radius;
        public int clipBottomAddition;
        public int clipTopAddition;
        public float scale = 1.0f;
    }

    public static class EmptyPhotoViewerProvider implements PhotoViewerProvider {
        @Override
        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            return null;
        }

        @Override
        public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            return null;
        }

        @Override
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {

        }

        @Override
        public void willHidePhotoViewer() {

        }

        @Override
        public boolean isPhotoChecked(int index) {
            return false;
        }

        @Override
        public void setPhotoChecked(int index) {

        }

        @Override
        public boolean cancelButtonPressed() {
            return true;
        }

        @Override
        public void sendButtonPressed(int index) {

        }

        @Override
        public int getSelectedCount() {
            return 0;
        }

        @Override
        public void updatePhotoAtIndex(int index) {

        }
    }

    public interface PhotoViewerProvider {
        PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);

        Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);

        void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);

        void willHidePhotoViewer();

        boolean isPhotoChecked(int index);

        void setPhotoChecked(int index);

        boolean cancelButtonPressed();

        void sendButtonPressed(int index);

        int getSelectedCount();

        void updatePhotoAtIndex(int index);
    }

    private class FrameLayoutTouchListener extends FrameLayout {

        private boolean attachedToWindow;
        private Runnable attachRunnable;

        public FrameLayoutTouchListener(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return getInstance().onTouchEvent(event);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            getInstance().onLayout(changed, left, top, right, bottom);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachedToWindow = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attachedToWindow = false;
        }
    }

    private class FrameLayoutDrawer extends SizeNotifierFrameLayoutPhoto {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            if (heightSize > AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight) {
                heightSize = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight;
            }

            setMeasuredDimension(widthSize, heightSize);

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                if (captionEditText.isPopupView(child)) {
                    child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) ? captionEditText.getEmojiPadding() : 0;

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
                        childLeft = r - width - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = lp.topMargin;
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

                if (child == mentionListView) {
                    if (!captionEditText.isPopupShowing() && !captionEditText.isKeyboardVisible() && captionEditText.getEmojiPadding() == 0) {
                        childTop += AndroidUtilities.dp(400);
                    } else {
                        childTop -= captionEditText.getMeasuredHeight();
                    }
                } else if (child == captionEditText) {
                    if (!captionEditText.isPopupShowing() && !captionEditText.isKeyboardVisible() && captionEditText.getEmojiPadding() == 0) {
                        childTop += AndroidUtilities.dp(400);
                    }
                } else if (child == pickerView || child == captionTextViewNew || child == captionTextViewOld) {
                    if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
                        childTop += AndroidUtilities.dp(400);
                    }
                } else if (captionEditText.isPopupView(child)) {
                    childTop = captionEditText.getBottom();
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getInstance().onDraw(canvas);
        }
    }

    private static volatile PhotoViewer Instance = null;

    public static PhotoViewer getInstance() {
        PhotoViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new PhotoViewer();
                }
            }
        }
        return localInstance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    radialProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    radialProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    Float progress = (Float) args[1];
                    radialProgressViews[a].setProgress(progress, true);
                }
            }
        } else if (id == NotificationCenter.userPhotosLoaded) {
            int guid = (Integer) args[4];
            int uid = (Integer) args[0];
            if (avatarsUserId == uid && classGuid == guid) {
                boolean fromCache = (Boolean) args[3];

                int setToImage = -1;
                ArrayList<TLRPC.Photo> photos = (ArrayList<TLRPC.Photo>) args[5];
                if (photos.isEmpty()) {
                    return;
                }
                imagesArrLocations.clear();
                imagesArrLocationsSizes.clear();
                avatarsArr.clear();
                for (TLRPC.Photo photo : photos) {
                    if (photo == null || photo instanceof TLRPC.TL_photoEmpty || photo.sizes == null) {
                        continue;
                    }
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640);
                    if (sizeFull != null) {
                        if (currentFileLocation != null) {
                            for (TLRPC.PhotoSize size : photo.sizes) {
                                if (size.location.local_id == currentFileLocation.local_id && size.location.volume_id == currentFileLocation.volume_id) {
                                    setToImage = imagesArrLocations.size();
                                    break;
                                }
                            }
                        }
                        imagesArrLocations.add(sizeFull.location);
                        imagesArrLocationsSizes.add(sizeFull.size);
                        avatarsArr.add(photo);
                    }
                }
                if (!avatarsArr.isEmpty()) {
                    menuItem.showSubItem(gallery_menu_delete);
                } else {
                    menuItem.hideSubItem(gallery_menu_delete);
                }
                needSearchImageInArr = false;
                currentIndex = -1;
                if (setToImage != -1) {
                    setImageIndex(setToImage, true);
                } else {
                    avatarsArr.add(0, new TLRPC.TL_photoEmpty());
                    imagesArrLocations.add(0, currentFileLocation);
                    imagesArrLocationsSizes.add(0, 0);
                    setImageIndex(0, true);
                }
                if (fromCache) {
                    MessagesController.getInstance().loadUserPhotos(avatarsUserId, 0, 80, 0, false, classGuid);
                }
            }
        } else if (id == NotificationCenter.mediaCountDidLoaded) {
            long uid = (Long) args[0];
            if (uid == currentDialogId || uid == mergeDialogId) {
                if (uid == currentDialogId) {
                    totalImagesCount = (Integer) args[1];
                    /*if ((Boolean) args[2]) {
                        SharedMediaQuery.getMediaCount(currentDialogId, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, false);
                    }*/
                } else if (uid == mergeDialogId) {
                    totalImagesCountMerge = (Integer) args[1];
                    /*if ((Boolean) args[2]) {
                        SharedMediaQuery.getMediaCount(mergeDialogId, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, false);
                    }*/
                }
                if (needSearchImageInArr && isFirstLoading) {
                    isFirstLoading = false;
                    loadingMoreImages = true;
                    SharedMediaQuery.loadMedia(currentDialogId, 0, 80, 0, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                } else if (!imagesArr.isEmpty()) {
                    if (opennedFromMedia) {
                        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, totalImagesCount + totalImagesCountMerge));
                    } else {
                        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount + totalImagesCountMerge - imagesArr.size()) + currentIndex + 1, totalImagesCount + totalImagesCountMerge));
                    }
                }
            }
        } else if (id == NotificationCenter.mediaDidLoaded) {
            long uid = (Long) args[0];
            int guid = (Integer) args[3];
            if ((uid == currentDialogId || uid == mergeDialogId) && guid == classGuid) {
                loadingMoreImages = false;
                int loadIndex = uid == currentDialogId ? 0 : 1;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                endReached[loadIndex] = (Boolean) args[5];
                if (needSearchImageInArr) {
                    if (arr.isEmpty() && (loadIndex != 0 || mergeDialogId == 0)) {
                        needSearchImageInArr = false;
                        return;
                    }
                    int foundIndex = -1;

                    MessageObject currentMessage = imagesArr.get(currentIndex);

                    int added = 0;
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject message = arr.get(a);
                        if (!imagesByIdsTemp[loadIndex].containsKey(message.getId())) {
                            imagesByIdsTemp[loadIndex].put(message.getId(), message);
                            if (opennedFromMedia) {
                                imagesArrTemp.add(message);
                                if (message.getId() == currentMessage.getId()) {
                                    foundIndex = added;
                                }
                                added++;
                            } else {
                                added++;
                                imagesArrTemp.add(0, message);
                                if (message.getId() == currentMessage.getId()) {
                                    foundIndex = arr.size() - added;
                                }
                            }
                        }
                    }
                    if (added == 0 && (loadIndex != 0 || mergeDialogId == 0)) {
                        totalImagesCount = imagesArr.size();
                        totalImagesCountMerge = 0;
                    }

                    if (foundIndex != -1) {
                        imagesArr.clear();
                        imagesArr.addAll(imagesArrTemp);
                        for (int a = 0; a < 2; a++) {
                            imagesByIds[a].clear();
                            imagesByIds[a].putAll(imagesByIdsTemp[a]);
                            imagesByIdsTemp[a].clear();
                        }
                        imagesArrTemp.clear();
                        needSearchImageInArr = false;
                        currentIndex = -1;
                        if (foundIndex >= imagesArr.size()) {
                            foundIndex = imagesArr.size() - 1;
                        }
                        setImageIndex(foundIndex, true);
                    } else {
                        int loadFromMaxId;
                        if (opennedFromMedia) {
                            loadFromMaxId = imagesArrTemp.isEmpty() ? 0 : imagesArrTemp.get(imagesArrTemp.size() - 1).getId();
                            if (loadIndex == 0 && endReached[loadIndex] && mergeDialogId != 0) {
                                loadIndex = 1;
                                if (!imagesArrTemp.isEmpty() && imagesArrTemp.get(imagesArrTemp.size() - 1).getDialogId() != mergeDialogId) {
                                    loadFromMaxId = 0;
                                }
                            }
                        } else {
                            loadFromMaxId = imagesArrTemp.isEmpty() ? 0 : imagesArrTemp.get(0).getId();
                            if (loadIndex == 0 && endReached[loadIndex] && mergeDialogId != 0) {
                                loadIndex = 1;
                                if (!imagesArrTemp.isEmpty() && imagesArrTemp.get(0).getDialogId() != mergeDialogId) {
                                    loadFromMaxId = 0;
                                }
                            }
                        }

                        if (!endReached[loadIndex]) {
                            loadingMoreImages = true;
                            if (opennedFromMedia) {
                                SharedMediaQuery.loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 0, 80, loadFromMaxId, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                            } else {
                                SharedMediaQuery.loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 0, 80, loadFromMaxId, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                            }
                        }
                    }
                } else {
                    int added = 0;
                    for (MessageObject message : arr) {
                        if (!imagesByIds[loadIndex].containsKey(message.getId())) {
                            added++;
                            if (opennedFromMedia) {
                                imagesArr.add(message);
                            } else {
                                imagesArr.add(0, message);
                            }
                            imagesByIds[loadIndex].put(message.getId(), message);
                        }
                    }
                    if (opennedFromMedia) {
                        if (added == 0) {
                            totalImagesCount = imagesArr.size();
                            totalImagesCountMerge = 0;
                        }
                    } else {
                        if (added != 0) {
                            int index = currentIndex;
                            currentIndex = -1;
                            setImageIndex(index + added, true);
                        } else {
                            totalImagesCount = imagesArr.size();
                            totalImagesCountMerge = 0;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (captionTextView != null) {
                captionTextView.invalidate();
            }
        }
    }

    public void setParentActivity(final Activity activity) {
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;
        actvityContext = new ContextThemeWrapper(parentActivity, R.style.Theme_TMessages);

        if (progressDrawables == null) {
            progressDrawables = new Drawable[4];
            progressDrawables[0] = parentActivity.getResources().getDrawable(R.drawable.circle_big);
            progressDrawables[1] = parentActivity.getResources().getDrawable(R.drawable.cancel_big);
            progressDrawables[2] = parentActivity.getResources().getDrawable(R.drawable.load_big);
            progressDrawables[3] = parentActivity.getResources().getDrawable(R.drawable.play_big);
        }

        scroller = new Scroller(activity);

        windowView = new FrameLayoutTouchListener(activity) {

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
                        closeCaptionEnter(false);
                        return false;
                    }
                    PhotoViewer.getInstance().closePhoto(true, false);
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback, int type) {
                if (Build.VERSION.SDK_INT >= 23) {
                    View view = parentActivity.findViewById(android.R.id.content);
                    if (view instanceof ViewGroup) {
                        try {
                            return ((ViewGroup) view).startActionModeForChild(originalView, callback, type);
                        } catch (Throwable e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
                return super.startActionModeForChild(originalView, callback, type);
            }
        };
        windowView.setBackgroundDrawable(backgroundDrawable);
        windowView.setFocusable(false);
        if (Build.VERSION.SDK_INT >= 23) {
            windowView.setFitsSystemWindows(true);
        }

        animatingImageView = new ClippingImageView(activity);
        animatingImageView.setAnimationValues(animationValues);
        windowView.addView(animatingImageView, LayoutHelper.createFrame(40, 40));

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        actionBar = new ActionBar(activity);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setOccupyStatusBar(false);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (needCaptionLayout && (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible())) {
                        closeCaptionEnter(false);
                        return;
                    }
                    closePhoto(true, false);
                } else if (id == gallery_menu_save) {
                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                        return;
                    }

                    File f = null;
                    if (currentMessageObject != null) {
                        f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                    } else if (currentFileLocation != null) {
                        f = FileLoader.getPathToAttach(currentFileLocation, avatarsUserId != 0);
                    }

                    if (f != null && f.exists()) {
                        MediaController.saveFile(f.toString(), parentActivity, currentMessageObject != null && currentMessageObject.isVideo() ? 1 : 0, null, null);
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                        showAlertDialog(builder);
                    }
                } else if (id == gallery_menu_showall) {
                    if (opennedFromMedia) {
                        closePhoto(true, false);
                    } else if (currentDialogId != 0) {
                        disableShowCheck = true;
                        closePhoto(false, false);
                        Bundle args2 = new Bundle();
                        args2.putLong("dialog_id", currentDialogId);
                        ((LaunchActivity) parentActivity).presentFragment(new MediaActivity(args2), false, true);
                    }
                } else if (id == gallery_menu_send) {
                    /*Intent intent = new Intent(this, MessagesActivity.class);
                    intent.putExtra("onlySelect", true);
                    startActivityForResult(intent, 10);
                    if (requestCode == 10) {
                        int chatId = data.getIntExtra("chatId", 0);
                        int userId = data.getIntExtra("userId", 0);
                        int dialog_id = 0;
                        if (chatId != 0) {
                            dialog_id = -chatId;
                        } else if (userId != 0) {
                            dialog_id = userId;
                        }
                        TLRPC.FileLocation location = getCurrentFile();
                        if (dialog_id != 0 && location != null) {
                            Intent intent = new Intent(GalleryImageViewer.this, ChatActivity.class);
                            if (chatId != 0) {
                                intent.putExtra("chatId", chatId);
                            } else {
                                intent.putExtra("userId", userId);
                            }
                            startActivity(intent);
                            NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                            finish();
                            if (withoutBottom) {
                                MessagesController.getInstance().sendMessage(location, dialog_id);
                            } else {
                                int item = mViewPager.getCurrentItem();
                                MessageObject obj = localPagerAdapter.imagesArr.get(item);
                                MessagesController.getInstance().sendMessage(obj, dialog_id);
                            }
                        }
                    }*/
                } else if (id == gallery_menu_crop) {
                    switchToEditMode(1);
                } else if (id == gallery_menu_tune) {
                    switchToEditMode(2);
                } else if (id == gallery_menu_delete) {
                    if (parentActivity == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    if (currentMessageObject != null && currentMessageObject.isVideo()) {
                        builder.setMessage(LocaleController.formatString("AreYouSureDeleteVideo", R.string.AreYouSureDeleteVideo));
                    } else {
                        builder.setMessage(LocaleController.formatString("AreYouSureDeletePhoto", R.string.AreYouSureDeletePhoto));
                    }
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (!imagesArr.isEmpty()) {
                                if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                                    return;
                                }
                                MessageObject obj = imagesArr.get(currentIndex);
                                if (obj.isSent()) {
                                    ArrayList<Integer> arr = new ArrayList<>();
                                    arr.add(obj.getId());

                                    ArrayList<Long> random_ids = null;
                                    TLRPC.EncryptedChat encryptedChat = null;
                                    if ((int) obj.getDialogId() == 0 && obj.messageOwner.random_id != 0) {
                                        random_ids = new ArrayList<>();
                                        random_ids.add(obj.messageOwner.random_id);
                                        encryptedChat = MessagesController.getInstance().getEncryptedChat((int) (obj.getDialogId() >> 32));
                                    }

                                    MessagesController.getInstance().deleteMessages(arr, random_ids, encryptedChat, obj.messageOwner.to_id.channel_id);
                                    closePhoto(false, false);
                                }
                            } else if (!avatarsArr.isEmpty()) {
                                if (currentIndex < 0 || currentIndex >= avatarsArr.size()) {
                                    return;
                                }
                                TLRPC.Photo photo = avatarsArr.get(currentIndex);
                                TLRPC.FileLocation currentLocation = imagesArrLocations.get(currentIndex);
                                if (photo instanceof TLRPC.TL_photoEmpty) {
                                    photo = null;
                                }
                                boolean current = false;
                                if (currentUserAvatarLocation != null) {
                                    if (photo != null) {
                                        for (TLRPC.PhotoSize size : photo.sizes) {
                                            if (size.location.local_id == currentUserAvatarLocation.local_id && size.location.volume_id == currentUserAvatarLocation.volume_id) {
                                                current = true;
                                                break;
                                            }
                                        }
                                    } else if (currentLocation.local_id == currentUserAvatarLocation.local_id && currentLocation.volume_id == currentUserAvatarLocation.volume_id) {
                                        current = true;
                                    }
                                }
                                if (current) {
                                    MessagesController.getInstance().deleteUserPhoto(null);
                                    closePhoto(false, false);
                                } else if (photo != null) {
                                    TLRPC.TL_inputPhoto inputPhoto = new TLRPC.TL_inputPhoto();
                                    inputPhoto.id = photo.id;
                                    inputPhoto.access_hash = photo.access_hash;
                                    MessagesController.getInstance().deleteUserPhoto(inputPhoto);
                                    MessagesStorage.getInstance().clearUserPhoto(avatarsUserId, photo.id);
                                    imagesArrLocations.remove(currentIndex);
                                    imagesArrLocationsSizes.remove(currentIndex);
                                    avatarsArr.remove(currentIndex);
                                    if (imagesArrLocations.isEmpty()) {
                                        closePhoto(false, false);
                                    } else {
                                        int index = currentIndex;
                                        if (index >= avatarsArr.size()) {
                                            index = avatarsArr.size() - 1;
                                        }
                                        currentIndex = -1;
                                        setImageIndex(index, true);
                                    }
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                } else if (id == gallery_menu_caption) {
                    if (imageMoveAnimation != null || changeModeAnimation != null) {
                        return;
                    }
                    cropItem.setVisibility(View.GONE);
                    tuneItem.setVisibility(View.GONE);
                    captionItem.setVisibility(View.GONE);
                    checkImageView.setVisibility(View.GONE);
                    captionDoneItem.setVisibility(View.VISIBLE);
                    pickerView.setVisibility(View.GONE);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) captionEditText.getLayoutParams();
                    layoutParams.bottomMargin = 0;
                    captionEditText.setLayoutParams(layoutParams);
                    layoutParams = (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
                    layoutParams.bottomMargin = 0;
                    mentionListView.setLayoutParams(layoutParams);
                    captionTextView.clearAnimation();
                    captionTextView.setVisibility(View.INVISIBLE);
                    captionEditText.openKeyboard();
                    lastTitle = actionBar.getTitle();
                    actionBar.setTitle(LocaleController.getString("PhotoCaption", R.string.PhotoCaption));
                } else if (id == gallery_menu_caption_done) {
                    closeCaptionEnter(true);
                }
            }

            @Override
            public boolean canOpenMenu() {
                if (currentMessageObject != null) {
                    File f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                    if (f.exists()) {
                        return true;
                    }
                } else if (currentFileLocation != null) {
                    File f = FileLoader.getPathToAttach(currentFileLocation, avatarsUserId != 0);
                    if (f.exists()) {
                        return true;
                    }
                }
                return false;
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.addSubItem(gallery_menu_showall, LocaleController.getString("ShowAllMedia", R.string.ShowAllMedia), 0);
        menuItem.addSubItem(gallery_menu_save, LocaleController.getString("SaveToGallery", R.string.SaveToGallery), 0);
        menuItem.addSubItem(gallery_menu_delete, LocaleController.getString("Delete", R.string.Delete), 0);

        captionDoneItem = menu.addItemWithWidth(gallery_menu_caption_done, R.drawable.ic_done, AndroidUtilities.dp(56));
        captionItem = menu.addItemWithWidth(gallery_menu_caption, R.drawable.photo_text, AndroidUtilities.dp(56));
        cropItem = menu.addItemWithWidth(gallery_menu_crop, R.drawable.photo_crop, AndroidUtilities.dp(56));
        tuneItem = menu.addItemWithWidth(gallery_menu_tune, R.drawable.photo_tools, AndroidUtilities.dp(56));

        bottomLayout = new FrameLayout(actvityContext);
        bottomLayout.setBackgroundColor(0x7f000000);
        containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        captionTextViewOld = new TextView(actvityContext);
        captionTextViewOld.setMaxLines(10);
        captionTextViewOld.setBackgroundColor(0x7f000000);
        captionTextViewOld.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        captionTextViewOld.setLinkTextColor(0xffffffff);
        captionTextViewOld.setTextColor(0xffffffff);
        captionTextViewOld.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        captionTextViewOld.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        captionTextViewOld.setVisibility(View.INVISIBLE);
        containerView.addView(captionTextViewOld, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        captionTextView = captionTextViewNew = new TextView(actvityContext);
        captionTextViewNew.setMaxLines(10);
        captionTextViewNew.setBackgroundColor(0x7f000000);
        captionTextViewNew.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        captionTextViewNew.setLinkTextColor(0xffffffff);
        captionTextViewNew.setTextColor(0xffffffff);
        captionTextViewNew.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        captionTextViewNew.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        captionTextViewNew.setVisibility(View.INVISIBLE);
        containerView.addView(captionTextViewNew, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        radialProgressViews[0] = new RadialProgressView(containerView.getContext(), containerView);
        radialProgressViews[0].setBackgroundState(0, false);
        radialProgressViews[1] = new RadialProgressView(containerView.getContext(), containerView);
        radialProgressViews[1].setBackgroundState(0, false);
        radialProgressViews[2] = new RadialProgressView(containerView.getContext(), containerView);
        radialProgressViews[2].setBackgroundState(0, false);

        shareButton = new ImageView(containerView.getContext());
        shareButton.setImageResource(R.drawable.share);
        shareButton.setScaleType(ImageView.ScaleType.CENTER);
        shareButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        bottomLayout.addView(shareButton, LayoutHelper.createFrame(50, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (parentActivity == null) {
                    return;
                }
                try {
                    File f = null;
                    boolean isVideo = false;

                    if (currentMessageObject != null) {
                        isVideo = currentMessageObject.isVideo();
                        /*if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                            AndroidUtilities.openUrl(parentActivity, currentMessageObject.messageOwner.media.webpage.url);
                            return;
                        }*/
                        f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                    } else if (currentFileLocation != null) {
                        f = FileLoader.getPathToAttach(currentFileLocation, avatarsUserId != 0);
                    }

                    if (f.exists()) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        if (isVideo) {
                            intent.setType("video/mp4");
                        } else {
                            intent.setType("image/jpeg");
                        }
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));

                        parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                        showAlertDialog(builder);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });

        nameTextView = new TextView(containerView.getContext());
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setSingleLine(true);
        nameTextView.setMaxLines(1);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setGravity(Gravity.LEFT);
        bottomLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 5, 60, 0));

        dateTextView = new TextView(containerView.getContext());
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        dateTextView.setSingleLine(true);
        dateTextView.setMaxLines(1);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setTextColor(0xffffffff);
        dateTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        dateTextView.setGravity(Gravity.LEFT);
        bottomLayout.addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 25, 50, 0));

        pickerView = new PickerBottomLayout(actvityContext);
        pickerView.setBackgroundColor(0x7f000000);
        containerView.addView(pickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        pickerView.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (placeProvider != null) {
                    closePhoto(!placeProvider.cancelButtonPressed(), false);
                }
            }
        });
        pickerView.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (placeProvider != null) {
                    placeProvider.sendButtonPressed(currentIndex);
                    closePhoto(false, false);
                }
            }
        });

        editorDoneLayout = new PickerBottomLayout(actvityContext);
        editorDoneLayout.setBackgroundColor(0x7f000000);
        editorDoneLayout.updateSelectedCount(0, false);
        editorDoneLayout.setVisibility(View.GONE);
        containerView.addView(editorDoneLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        editorDoneLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentEditMode == 1) {
                    photoCropView.cancelAnimationRunnable();
                }
                switchToEditMode(0);
            }
        });
        editorDoneLayout.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentEditMode == 1) {
                    photoCropView.cancelAnimationRunnable();
                    if (imageMoveAnimation != null) {
                        return;
                    }
                }
                applyCurrentEditMode();
                switchToEditMode(0);
            }
        });

        ImageView rotateButton = new ImageView(actvityContext);
        rotateButton.setScaleType(ImageView.ScaleType.CENTER);
        rotateButton.setImageResource(R.drawable.tool_rotate);
        rotateButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        editorDoneLayout.addView(rotateButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
        rotateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                centerImage.setOrientation(centerImage.getOrientation() - 90, false);
                photoCropView.setOrientation(centerImage.getOrientation());
                containerView.invalidate();
            }
        });

        gestureDetector = new GestureDetector(containerView.getContext(), this);
        gestureDetector.setOnDoubleTapListener(this);

        centerImage.setParentView(containerView);
        centerImage.setCrossfadeAlpha((byte) 2);
        centerImage.setInvalidateAll(true);
        leftImage.setParentView(containerView);
        leftImage.setCrossfadeAlpha((byte) 2);
        leftImage.setInvalidateAll(true);
        rightImage.setParentView(containerView);
        rightImage.setCrossfadeAlpha((byte) 2);
        rightImage.setInvalidateAll(true);

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        checkImageView = new CheckBox(containerView.getContext(), R.drawable.selectphoto_large);
        checkImageView.setDrawBackground(true);
        checkImageView.setSize(45);
        checkImageView.setCheckOffset(AndroidUtilities.dp(1));
        checkImageView.setColor(0xff3ccaef);
        checkImageView.setVisibility(View.GONE);
        containerView.addView(checkImageView, LayoutHelper.createFrame(45, 45, Gravity.RIGHT | Gravity.TOP, 0, rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90 ? 58 : 68, 10, 0));
        checkImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (placeProvider != null) {
                    placeProvider.setPhotoChecked(currentIndex);
                    checkImageView.setChecked(placeProvider.isPhotoChecked(currentIndex), true);
                    updateSelectedCount();
                }
            }
        });

        captionEditText = new PhotoViewerCaptionEnterView(actvityContext, containerView, windowView);
        captionEditText.setDelegate(new PhotoViewerCaptionEnterView.PhotoViewerCaptionEnterViewDelegate() {
            @Override
            public void onCaptionEnter() {
                closeCaptionEnter(true);
            }

            @Override
            public void onTextChanged(CharSequence text) {
                if (mentionsAdapter != null && captionEditText != null && parentChatActivity != null && text != null) {
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), captionEditText.getCursorPosition(), parentChatActivity.messages);
                }
            }

            @Override
            public void onWindowSizeChanged(int size) {
                int height = AndroidUtilities.dp(36 * Math.min(3, mentionsAdapter.getItemCount()) + (mentionsAdapter.getItemCount() > 3 ? 18 : 0));
                if (size - ActionBar.getCurrentActionBarHeight() * 2 < height) {
                    allowMentions = false;
                    if (mentionListView != null && mentionListView.getVisibility() == View.VISIBLE) {
                        mentionListView.clearAnimation();
                        mentionListView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    allowMentions = true;
                    if (mentionListView != null && mentionListView.getVisibility() == View.INVISIBLE) {
                        mentionListView.clearAnimation();
                        mentionListView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        containerView.addView(captionEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, -400));

        mentionListView = new RecyclerListView(actvityContext);
        mentionLayoutManager = new LinearLayoutManager(actvityContext) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        mentionLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mentionListView.setLayoutManager(mentionLayoutManager);
        mentionListView.setBackgroundColor(0x7f000000);
        mentionListView.setVisibility(View.GONE);
        mentionListView.setClipToPadding(true);
        mentionListView.setOverScrollMode(ListView.OVER_SCROLL_NEVER);
        containerView.addView(mentionListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 110, Gravity.LEFT | Gravity.BOTTOM));

        mentionListView.setAdapter(mentionsAdapter = new MentionsAdapter(actvityContext, true, 0, new MentionsAdapter.MentionsAdapterDelegate() {
            @Override
            public void needChangePanelVisibility(boolean show) {
                if (show) {
                    FrameLayout.LayoutParams layoutParams3 = (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
                    int height = 36 * Math.min(3, mentionsAdapter.getItemCount()) + (mentionsAdapter.getItemCount() > 3 ? 18 : 0);
                    layoutParams3.height = AndroidUtilities.dp(height);
                    layoutParams3.topMargin = -AndroidUtilities.dp(height);
                    mentionListView.setLayoutParams(layoutParams3);

                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                        mentionListAnimation = null;
                    }

                    if (mentionListView.getVisibility() == View.VISIBLE) {
                        ViewProxy.setAlpha(mentionListView, 1.0f);
                        return;
                    } else {
                        mentionLayoutManager.scrollToPositionWithOffset(0, 10000);
                    }
                    if (allowMentions) {
                        mentionListView.setVisibility(View.VISIBLE);
                        mentionListAnimation = new AnimatorSetProxy();
                        mentionListAnimation.playTogether(
                                ObjectAnimatorProxy.ofFloat(mentionListView, "alpha", 0.0f, 1.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionListView.clearAnimation();
                                    mentionListAnimation = null;
                                }
                            }
                        });
                        mentionListAnimation.setDuration(200);
                        mentionListAnimation.start();
                    } else {
                        ViewProxy.setAlpha(mentionListView, 1.0f);
                        mentionListView.clearAnimation();
                        mentionListView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                        mentionListAnimation = null;
                    }

                    if (mentionListView.getVisibility() == View.GONE) {
                        return;
                    }
                    if (allowMentions) {
                        mentionListAnimation = new AnimatorSetProxy();
                        mentionListAnimation.playTogether(
                                ObjectAnimatorProxy.ofFloat(mentionListView, "alpha", 0.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionListView.clearAnimation();
                                    mentionListView.setVisibility(View.GONE);
                                    mentionListAnimation = null;
                                }
                            }
                        });
                        mentionListAnimation.setDuration(200);
                        mentionListAnimation.start();
                    } else {
                        mentionListView.clearAnimation();
                        mentionListView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onContextSearch(boolean searching) {

            }

            @Override
            public void onContextClick(TLRPC.BotInlineResult result) {

            }
        }));

        mentionListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Object object = mentionsAdapter.getItem(position);
                int start = mentionsAdapter.getResultStartPosition();
                int len = mentionsAdapter.getResultLength();
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user != null) {
                        captionEditText.replaceWithText(start, len, "@" + user.username + " ");
                    }
                } else if (object instanceof String) {
                    captionEditText.replaceWithText(start, len, object + " ");
                }
            }
        });

        mentionListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                Object object = mentionsAdapter.getItem(position);
                if (object instanceof String) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                    builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mentionsAdapter.clearRecentHashtags();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                    return true;
                }
                return false;
            }
        });
    }

    private void updateCaptionTextForCurrentPhoto(Object object) {
        CharSequence caption = null;
        if (object instanceof MediaController.PhotoEntry) {
            caption = ((MediaController.PhotoEntry) object).caption;
        } else if (object instanceof MediaController.SearchImage) {
            caption = ((MediaController.SearchImage) object).caption;
        }
        if (caption == null || caption.length() == 0) {
            captionEditText.setFieldText("");
        } else {
            captionEditText.setFieldText(caption);
        }
    }

    private void closeCaptionEnter(boolean apply) {
        Object object = imagesArrLocals.get(currentIndex);
        if (apply) {
            if (object instanceof MediaController.PhotoEntry) {
                ((MediaController.PhotoEntry) object).caption = captionEditText.getFieldCharSequence();
            } else if (object instanceof MediaController.SearchImage) {
                ((MediaController.SearchImage) object).caption = captionEditText.getFieldCharSequence();
            }

            if (captionEditText.getFieldCharSequence().length() != 0 && !placeProvider.isPhotoChecked(currentIndex)) {
                placeProvider.setPhotoChecked(currentIndex);
                checkImageView.setChecked(placeProvider.isPhotoChecked(currentIndex), true);
                updateSelectedCount();
            }
        }
        cropItem.setVisibility(View.VISIBLE);
        captionItem.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 16) {
            tuneItem.setVisibility(View.VISIBLE);
        }
        if (sendPhotoType == 0) {
            checkImageView.setVisibility(View.VISIBLE);
        }
        captionDoneItem.setVisibility(View.GONE);
        pickerView.setVisibility(View.VISIBLE);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) captionEditText.getLayoutParams();
        layoutParams.bottomMargin = -AndroidUtilities.dp(400);
        captionEditText.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
        layoutParams.bottomMargin = -AndroidUtilities.dp(400);
        mentionListView.setLayoutParams(layoutParams);

        if (lastTitle != null) {
            actionBar.setTitle(lastTitle);
            lastTitle = null;
        }

        updateCaptionTextForCurrentPhoto(object);
        setCurrentCaption(captionEditText.getFieldCharSequence());
        if (captionEditText.isPopupShowing()) {
            captionEditText.hidePopup();
        } else {
            captionEditText.closeKeyboard();
        }
    }

    private void showAlertDialog(AlertDialog.Builder builder) {
        if (parentActivity == null) {
            return;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            visibleDialog = builder.show();
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    visibleDialog = null;
                }
            });
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void applyCurrentEditMode() {
        Bitmap bitmap = null;
        if (currentEditMode == 1) {
            bitmap = photoCropView.getBitmap();
        } else if (currentEditMode == 2) {
            bitmap = photoFilterView.getBitmap();
        }
        if (bitmap != null) {
            TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 80, false, 101, 101);
            if (size != null) {
                Object object = imagesArrLocals.get(currentIndex);
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry entry = (MediaController.PhotoEntry) object;
                    entry.imagePath = FileLoader.getPathToAttach(size, true).toString();
                    size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.dp(120), AndroidUtilities.dp(120), 70, false, 101, 101);
                    if (size != null) {
                        entry.thumbPath = FileLoader.getPathToAttach(size, true).toString();
                    }
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage entry = (MediaController.SearchImage) object;
                    entry.imagePath = FileLoader.getPathToAttach(size, true).toString();
                    size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.dp(120), AndroidUtilities.dp(120), 70, false, 101, 101);
                    if (size != null) {
                        entry.thumbPath = FileLoader.getPathToAttach(size, true).toString();
                    }
                }
                if (sendPhotoType == 0 && placeProvider != null) {
                    placeProvider.updatePhotoAtIndex(currentIndex);
                    if (!placeProvider.isPhotoChecked(currentIndex)) {
                        placeProvider.setPhotoChecked(currentIndex);
                        checkImageView.setChecked(placeProvider.isPhotoChecked(currentIndex), true);
                        updateSelectedCount();
                    }
                }
                if (currentEditMode == 1) {
                    float scaleX = photoCropView.getRectSizeX() / (float) getContainerViewWidth();
                    float scaleY = photoCropView.getRectSizeY() / (float) getContainerViewHeight();
                    scale = scaleX > scaleY ? scaleX : scaleY;
                    translationX = photoCropView.getRectX() + photoCropView.getRectSizeX() / 2 - getContainerViewWidth() / 2;
                    translationY = photoCropView.getRectY() + photoCropView.getRectSizeY() / 2 - getContainerViewHeight() / 2;
                    zoomAnimation = true;
                }
                centerImage.setParentView(null);
                centerImage.setOrientation(0, true);
                centerImage.setImageBitmap(bitmap);
                centerImage.setParentView(containerView);
            }
        }
    }

    private void switchToEditMode(final int mode) {
        if (currentEditMode == mode || centerImage.getBitmap() == null || changeModeAnimation != null || imageMoveAnimation != null || radialProgressViews[0].backgroundState != -1) {
            return;
        }
        if (mode == 0) {
            if (currentEditMode == 2) {
                if (photoFilterView.getToolsView().getVisibility() != View.VISIBLE) {
                    photoFilterView.switchToOrFromEditMode();
                    return;
                }
            }
            Bitmap bitmap = centerImage.getBitmap();
            if (bitmap != null) {
                int bitmapWidth = centerImage.getBitmapWidth();
                int bitmapHeight = centerImage.getBitmapHeight();

                float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                float newScaleX = (float) getContainerViewWidth(0) / (float) bitmapWidth;
                float newScaleY = (float) getContainerViewHeight(0) / (float) bitmapHeight;
                float scale = scaleX > scaleY ? scaleY : scaleX;
                float newScale = newScaleX > newScaleY ? newScaleY : newScaleX;

                animateToScale = newScale / scale;
                animateToX = 0;
                if (currentEditMode == 1) {
                    animateToY = AndroidUtilities.dp(24);
                } else if (currentEditMode == 2) {
                    animateToY = AndroidUtilities.dp(62);
                }
                animationStartTime = System.currentTimeMillis();
                zoomAnimation = true;
            }

            imageMoveAnimation = new AnimatorSetProxy();
            if (currentEditMode == 1) {
                imageMoveAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(editorDoneLayout, "translationY", AndroidUtilities.dp(48)),
                        ObjectAnimatorProxy.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                        ObjectAnimatorProxy.ofFloat(photoCropView, "alpha", 0)
                );
            } else if (currentEditMode == 2) {
                photoFilterView.shutdown();
                imageMoveAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(photoFilterView.getToolsView(), "translationY", AndroidUtilities.dp(126)),
                        ObjectAnimatorProxy.ofFloat(PhotoViewer.this, "animationValue", 0, 1)
                );
            }
            imageMoveAnimation.setDuration(200);
            imageMoveAnimation.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    if (currentEditMode == 1) {
                        photoCropView.clearAnimation();
                        editorDoneLayout.clearAnimation();
                        editorDoneLayout.setVisibility(View.GONE);
                        photoCropView.setVisibility(View.GONE);
                    } else if (currentEditMode == 2) {
                        photoFilterView.getToolsView().clearAnimation();
                        containerView.removeView(photoFilterView);
                        photoFilterView = null;
                    }
                    imageMoveAnimation = null;
                    currentEditMode = mode;
                    animateToScale = 1;
                    animateToX = 0;
                    animateToY = 0;
                    scale = 1;
                    updateMinMax(scale);
                    containerView.invalidate();

                    AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                    ArrayList<Object> arrayList = new ArrayList<>();
                    arrayList.add(ObjectAnimatorProxy.ofFloat(pickerView, "translationY", 0));
                    arrayList.add(ObjectAnimatorProxy.ofFloat(actionBar, "translationY", 0));
                    if (needCaptionLayout) {
                        arrayList.add(ObjectAnimatorProxy.ofFloat(captionTextView, "translationY", 0));
                    }
                    if (sendPhotoType == 0) {
                        arrayList.add(ObjectAnimatorProxy.ofFloat(checkImageView, "alpha", 1));
                    }
                    animatorSet.playTogether(arrayList);
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationStart(Object animation) {
                            pickerView.setVisibility(View.VISIBLE);
                            actionBar.setVisibility(View.VISIBLE);
                            if (needCaptionLayout) {
                                captionTextView.setVisibility(captionTextView.getTag() != null ? View.VISIBLE : View.INVISIBLE);
                            }
                            if (sendPhotoType == 0) {
                                checkImageView.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        public void onAnimationEnd(Object animation) {
                            pickerView.clearAnimation();
                            actionBar.clearAnimation();
                            if (needCaptionLayout) {
                                captionTextView.clearAnimation();
                            }
                            if (sendPhotoType == 0) {
                                checkImageView.clearAnimation();
                            }
                        }
                    });
                    animatorSet.start();
                }
            });
            imageMoveAnimation.start();
        } else if (mode == 1) {
            if (photoCropView == null) {
                photoCropView = new PhotoCropView(actvityContext);
                photoCropView.setVisibility(View.GONE);
                containerView.addView(photoCropView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
                photoCropView.setDelegate(new PhotoCropView.PhotoCropViewDelegate() {
                    @Override
                    public void needMoveImageTo(float x, float y, float s, boolean animated) {
                        if (animated) {
                            animateTo(s, x, y, true);
                        } else {
                            translationX = x;
                            translationY = y;
                            scale = s;
                            containerView.invalidate();
                        }
                    }

                    @Override
                    public Bitmap getBitmap() {
                        return centerImage.getBitmap();
                    }
                });
            }

            editorDoneLayout.doneButtonTextView.setText(LocaleController.getString("Crop", R.string.Crop));
            changeModeAnimation = new AnimatorSetProxy();
            ArrayList<Object> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimatorProxy.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimatorProxy.ofFloat(actionBar, "translationY", 0, -actionBar.getHeight()));
            if (needCaptionLayout) {
                arrayList.add(ObjectAnimatorProxy.ofFloat(captionTextView, "translationY", 0, AndroidUtilities.dp(96)));
            }
            if (sendPhotoType == 0) {
                arrayList.add(ObjectAnimatorProxy.ofFloat(checkImageView, "alpha", 1, 0));
            }
            changeModeAnimation.playTogether(arrayList);
            changeModeAnimation.setDuration(200);
            changeModeAnimation.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    changeModeAnimation = null;
                    pickerView.clearAnimation();
                    actionBar.clearAnimation();
                    pickerView.setVisibility(View.GONE);
                    actionBar.setVisibility(View.GONE);
                    if (needCaptionLayout) {
                        captionTextView.clearAnimation();
                        captionTextView.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0) {
                        checkImageView.clearAnimation();
                        checkImageView.setVisibility(View.GONE);
                    }

                    Bitmap bitmap = centerImage.getBitmap();
                    if (bitmap != null) {
                        photoCropView.setBitmap(bitmap, centerImage.getOrientation(), sendPhotoType != 1);
                        int bitmapWidth = centerImage.getBitmapWidth();
                        int bitmapHeight = centerImage.getBitmapHeight();

                        float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                        float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                        float newScaleX = (float) getContainerViewWidth(1) / (float) bitmapWidth;
                        float newScaleY = (float) getContainerViewHeight(1) / (float) bitmapHeight;
                        float scale = scaleX > scaleY ? scaleY : scaleX;
                        float newScale = newScaleX > newScaleY ? newScaleY : newScaleX;

                        animateToScale = newScale / scale;
                        animateToX = 0;
                        animateToY = -AndroidUtilities.dp(24);
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSetProxy();
                    imageMoveAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(editorDoneLayout, "translationY", AndroidUtilities.dp(48), 0),
                            ObjectAnimatorProxy.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                            ObjectAnimatorProxy.ofFloat(photoCropView, "alpha", 0, 1)
                    );
                    imageMoveAnimation.setDuration(200);
                    imageMoveAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationStart(Object animation) {
                            editorDoneLayout.setVisibility(View.VISIBLE);
                            photoCropView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Object animation) {
                            imageMoveAnimation = null;
                            currentEditMode = mode;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            containerView.invalidate();
                            editorDoneLayout.clearAnimation();
                            photoCropView.clearAnimation();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        } else if (mode == 2) {
            if (photoFilterView == null) {
                photoFilterView = new PhotoFilterView(parentActivity, centerImage.getBitmap(), centerImage.getOrientation());
                containerView.addView(photoFilterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                photoFilterView.getDoneTextView().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyCurrentEditMode();
                        switchToEditMode(0);
                    }
                });
                photoFilterView.getCancelTextView().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (photoFilterView.hasChanges()) {
                            if (parentActivity == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    switchToEditMode(0);
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showAlertDialog(builder);
                        } else {
                            switchToEditMode(0);
                        }
                    }
                });
                //photoFilterView.setEditViewFirst();
                ViewProxy.setTranslationY(photoFilterView.getToolsView(), AndroidUtilities.dp(126));
            }

            changeModeAnimation = new AnimatorSetProxy();
            ArrayList<Object> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimatorProxy.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimatorProxy.ofFloat(actionBar, "translationY", 0, -actionBar.getHeight()));
            if (needCaptionLayout) {
                arrayList.add(ObjectAnimatorProxy.ofFloat(captionTextView, "translationY", 0, AndroidUtilities.dp(96)));
            }
            if (sendPhotoType == 0) {
                arrayList.add(ObjectAnimatorProxy.ofFloat(checkImageView, "alpha", 1, 0));
            }
            changeModeAnimation.playTogether(arrayList);
            changeModeAnimation.setDuration(200);
            changeModeAnimation.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    changeModeAnimation = null;
                    pickerView.clearAnimation();
                    actionBar.clearAnimation();
                    pickerView.setVisibility(View.GONE);
                    actionBar.setVisibility(View.GONE);
                    if (needCaptionLayout) {
                        captionTextView.clearAnimation();
                        captionTextView.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0) {
                        checkImageView.clearAnimation();
                        checkImageView.setVisibility(View.GONE);
                    }

                    Bitmap bitmap = centerImage.getBitmap();
                    if (bitmap != null) {
                        int bitmapWidth = centerImage.getBitmapWidth();
                        int bitmapHeight = centerImage.getBitmapHeight();

                        float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                        float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                        float newScaleX = (float) getContainerViewWidth(2) / (float) bitmapWidth;
                        float newScaleY = (float) getContainerViewHeight(2) / (float) bitmapHeight;
                        float scale = scaleX > scaleY ? scaleY : scaleX;
                        float newScale = newScaleX > newScaleY ? newScaleY : newScaleX;

                        animateToScale = newScale / scale;
                        animateToX = 0;
                        animateToY = -AndroidUtilities.dp(62);
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSetProxy();
                    imageMoveAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                            ObjectAnimatorProxy.ofFloat(photoFilterView.getToolsView(), "translationY", AndroidUtilities.dp(126), 0)
                    );
                    imageMoveAnimation.setDuration(200);
                    imageMoveAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationStart(Object animation) {

                        }

                        @Override
                        public void onAnimationEnd(Object animation) {
                            photoFilterView.init();
                            imageMoveAnimation = null;
                            currentEditMode = mode;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            containerView.invalidate();
                            photoFilterView.getToolsView().clearAnimation();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        }
    }

    private void toggleCheckImageView(boolean show) {
        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(ObjectAnimatorProxy.ofFloat(pickerView, "alpha", show ? 1.0f : 0.0f));
        if (needCaptionLayout) {
            arrayList.add(ObjectAnimatorProxy.ofFloat(captionTextView, "alpha", show ? 1.0f : 0.0f));
        }
        if (sendPhotoType == 0) {
            arrayList.add(ObjectAnimatorProxy.ofFloat(checkImageView, "alpha", show ? 1.0f : 0.0f));
        }
        animatorSet.playTogether(arrayList);
        animatorSet.setDuration(200);
        animatorSet.start();
    }

    private void toggleActionBar(boolean show, final boolean animated) {
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
            if (canShowBottom) {
                bottomLayout.setVisibility(View.VISIBLE);
                if (captionTextView.getTag() != null) {
                    captionTextView.setVisibility(View.VISIBLE);
                }
            }
        }
        isActionBarVisible = show;
        actionBar.setEnabled(show);
        bottomLayout.setEnabled(show);

        if (animated) {
            ArrayList<Object> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimatorProxy.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimatorProxy.ofFloat(bottomLayout, "alpha", show ? 1.0f : 0.0f));
            if (captionTextView.getTag() != null) {
                arrayList.add(ObjectAnimatorProxy.ofFloat(captionTextView, "alpha", show ? 1.0f : 0.0f));
            }
            currentActionBarAnimation = new AnimatorSetProxy();
            currentActionBarAnimation.playTogether(arrayList);
            if (!show) {
                currentActionBarAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (currentActionBarAnimation != null && currentActionBarAnimation.equals(animation)) {
                            actionBar.setVisibility(View.GONE);
                            if (canShowBottom) {
                                bottomLayout.clearAnimation();
                                bottomLayout.setVisibility(View.GONE);
                                if (captionTextView.getTag() != null) {
                                    captionTextView.clearAnimation();
                                    captionTextView.setVisibility(View.INVISIBLE);
                                }
                            }
                            currentActionBarAnimation = null;
                        }
                    }
                });
            }

            currentActionBarAnimation.setDuration(200);
            currentActionBarAnimation.start();
        } else {
            ViewProxy.setAlpha(actionBar, show ? 1.0f : 0.0f);
            ViewProxy.setAlpha(bottomLayout, show ? 1.0f : 0.0f);
            if (captionTextView.getTag() != null) {
                ViewProxy.setAlpha(captionTextView, show ? 1.0f : 0.0f);
            }
            if (!show) {
                actionBar.setVisibility(View.GONE);
                if (canShowBottom) {
                    bottomLayout.clearAnimation();
                    bottomLayout.setVisibility(View.GONE);
                    if (captionTextView.getTag() != null) {
                        captionTextView.clearAnimation();
                        captionTextView.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }
    }

    private String getFileName(int index) {
        if (index < 0) {
            return null;
        }
        if (!imagesArrLocations.isEmpty() || !imagesArr.isEmpty()) {
            if (!imagesArrLocations.isEmpty()) {
                if (index >= imagesArrLocations.size()) {
                    return null;
                }
                TLRPC.FileLocation location = imagesArrLocations.get(index);
                return location.volume_id + "_" + location.local_id + ".jpg";
            } else if (!imagesArr.isEmpty()) {
                if (index >= imagesArr.size()) {
                    return null;
                }
                return FileLoader.getMessageFileName(imagesArr.get(index).messageOwner);
            }
        } else if (!imagesArrLocals.isEmpty()) {
            if (index >= imagesArrLocals.size()) {
                return null;
            }
            Object object = imagesArrLocals.get(index);
            if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage searchImage = ((MediaController.SearchImage) object);
                if (searchImage.document != null) {
                    return FileLoader.getAttachFileName(searchImage.document);
                } else if (searchImage.type != 1 && searchImage.localUrl != null && searchImage.localUrl.length() > 0) {
                    File file = new File(searchImage.localUrl);
                    if (file.exists()) {
                        return file.getName();
                    } else {
                        searchImage.localUrl = "";
                    }
                }
                return Utilities.MD5(searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(searchImage.imageUrl, "jpg");
            }
        }
        return null;
    }

    private TLRPC.FileLocation getFileLocation(int index, int size[]) {
        if (index < 0) {
            return null;
        }
        if (!imagesArrLocations.isEmpty()) {
            if (index >= imagesArrLocations.size()) {
                return null;
            }
            size[0] = imagesArrLocationsSizes.get(index);
            return imagesArrLocations.get(index);
        } else if (!imagesArr.isEmpty()) {
            if (index >= imagesArr.size()) {
                return null;
            }
            MessageObject message = imagesArr.get(index);
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    return message.messageOwner.action.newUserPhoto.photo_big;
                } else {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        size[0] = sizeFull.size;
                        if (size[0] == 0) {
                            size[0] = -1;
                        }
                        return sizeFull.location;
                    } else {
                        size[0] = -1;
                    }
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo != null || message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage != null) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    size[0] = sizeFull.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    return sizeFull.location;
                } else {
                    size[0] = -1;
                }
            } else if (message.getDocument() != null && message.getDocument().thumb != null) {
                size[0] = message.getDocument().thumb.size;
                if (size[0] == 0) {
                    size[0] = -1;
                }
                return message.getDocument().thumb.location;
            }
        }
        return null;
    }

    private void updateSelectedCount() {
        if (placeProvider == null) {
            return;
        }
        pickerView.updateSelectedCount(placeProvider.getSelectedCount(), false);
    }

    private void onPhotoShow(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<Object> photos, int index, final PlaceProviderObject object) {
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
        currentMessageObject = null;
        currentFileLocation = null;
        currentPathObject = null;
        currentIndex = -1;
        currentFileNames[0] = null;
        currentFileNames[1] = null;
        currentFileNames[2] = null;
        avatarsUserId = 0;
        totalImagesCount = 0;
        totalImagesCountMerge = 0;
        currentEditMode = 0;
        isFirstLoading = true;
        needSearchImageInArr = false;
        loadingMoreImages = false;
        endReached[0] = false;
        endReached[1] = mergeDialogId == 0;
        opennedFromMedia = false;
        needCaptionLayout = false;
        canShowBottom = true;
        imagesArr.clear();
        imagesArrLocations.clear();
        imagesArrLocationsSizes.clear();
        avatarsArr.clear();
        imagesArrLocals.clear();
        for (int a = 0; a < 2; a++) {
            imagesByIds[a].clear();
            imagesByIdsTemp[a].clear();
        }
        imagesArrTemp.clear();
        currentUserAvatarLocation = null;
        containerView.setPadding(0, 0, 0, 0);
        currentThumb = object != null ? object.thumb : null;
        menuItem.setVisibility(View.VISIBLE);
        bottomLayout.setVisibility(View.VISIBLE);
        shareButton.setVisibility(View.GONE);
        menuItem.hideSubItem(gallery_menu_showall);
        ViewProxy.setTranslationY(actionBar, 0);
        ViewProxy.setTranslationY(pickerView, 0);
        ViewProxy.setAlpha(checkImageView, 1.0f);
        ViewProxy.setAlpha(pickerView, 1.0f);
        checkImageView.clearAnimation();
        pickerView.clearAnimation();
        editorDoneLayout.clearAnimation();
        checkImageView.setVisibility(View.GONE);
        pickerView.setVisibility(View.GONE);
        cropItem.setVisibility(View.GONE);
        tuneItem.setVisibility(View.GONE);
        captionItem.setVisibility(View.GONE);
        captionDoneItem.setVisibility(View.GONE);
        captionEditText.clearAnimation();
        captionEditText.setVisibility(View.GONE);
        mentionListView.setVisibility(View.GONE);
        editorDoneLayout.setVisibility(View.GONE);
        captionTextView.setTag(null);
        captionTextView.clearAnimation();
        captionTextView.setVisibility(View.INVISIBLE);
        if (photoCropView != null) {
            photoCropView.clearAnimation();
            photoCropView.setVisibility(View.GONE);
        }
        if (photoFilterView != null) {
            photoFilterView.clearAnimation();
            photoFilterView.setVisibility(View.GONE);
        }

        for (int a = 0; a < 3; a++) {
            if (radialProgressViews[a] != null) {
                radialProgressViews[a].setBackgroundState(-1, false);
            }
        }

        if (messageObject != null && messages == null) {
            imagesArr.add(messageObject);
            if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)) {
                needSearchImageInArr = true;
                imagesByIds[0].put(messageObject.getId(), messageObject);
                menuItem.showSubItem(gallery_menu_showall);
            } else {
                menuItem.hideSubItem(gallery_menu_showall);
            }
            setImageIndex(0, true);
        } else if (fileLocation != null) {
            avatarsUserId = object.user_id;
            imagesArrLocations.add(fileLocation);
            imagesArrLocationsSizes.add(object.size);
            avatarsArr.add(new TLRPC.TL_photoEmpty());
            bottomLayout.clearAnimation();
            shareButton.setVisibility(View.VISIBLE);
            menuItem.hideSubItem(gallery_menu_showall);
            setImageIndex(0, true);
            currentUserAvatarLocation = fileLocation;
        } else if (messages != null) {
            menuItem.showSubItem(gallery_menu_showall);
            opennedFromMedia = true;
            imagesArr.addAll(messages);
            if (!opennedFromMedia) {
                Collections.reverse(imagesArr);
                index = imagesArr.size() - index - 1;
            }
            for (int a = 0; a < imagesArr.size(); a++) {
                MessageObject message = imagesArr.get(a);
                imagesByIds[message.getDialogId() == currentDialogId ? 0 : 1].put(message.getId(), message);
            }
            setImageIndex(index, true);
        } else if (photos != null) {
            if (sendPhotoType == 0) {
                checkImageView.setVisibility(View.VISIBLE);
            }
            menuItem.setVisibility(View.GONE);
            imagesArrLocals.addAll(photos);
            setImageIndex(index, true);
            pickerView.setVisibility(View.VISIBLE);
            bottomLayout.clearAnimation();
            bottomLayout.setVisibility(View.GONE);
            canShowBottom = false;
            Object obj = imagesArrLocals.get(index);
            cropItem.setVisibility(obj instanceof MediaController.PhotoEntry || obj instanceof MediaController.SearchImage && ((MediaController.SearchImage) obj).type == 0 ? View.VISIBLE : View.GONE);
            if (parentChatActivity != null && (parentChatActivity.currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(parentChatActivity.currentEncryptedChat.layer) >= 46)) {
                mentionsAdapter.setChatInfo(parentChatActivity.info);
                mentionsAdapter.setNeedUsernames(parentChatActivity.currentChat != null);
                mentionsAdapter.setNeedBotContext(false);
                captionItem.setVisibility(cropItem.getVisibility());
                captionEditText.setVisibility(cropItem.getVisibility());
                needCaptionLayout = captionItem.getVisibility() == View.VISIBLE;
                if (needCaptionLayout) {
                    captionEditText.onCreate();
                }
            }
            if (Build.VERSION.SDK_INT >= 16) {
                tuneItem.setVisibility(cropItem.getVisibility());
            }
            updateSelectedCount();
        }

        if (currentDialogId != 0 && totalImagesCount == 0) {
            SharedMediaQuery.getMediaCount(currentDialogId, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
            if (mergeDialogId != 0) {
                SharedMediaQuery.getMediaCount(mergeDialogId, SharedMediaQuery.MEDIA_PHOTOVIDEO, classGuid, true);
            }
        } else if (avatarsUserId != 0) {
            MessagesController.getInstance().loadUserPhotos(avatarsUserId, 0, 80, 0, true, classGuid);
        }
    }

    private void setImages() {
        if (animationInProgress == 0) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(rightImage, currentIndex + 1);
            setIndexToImage(leftImage, currentIndex - 1);
        }
    }

    private void setImageIndex(int index, boolean init) {
        if (currentIndex == index) {
            return;
        }
        if (!init) {
            currentThumb = null;
        }
        currentFileNames[0] = getFileName(index);
        currentFileNames[1] = getFileName(index + 1);
        currentFileNames[2] = getFileName(index - 1);
        placeProvider.willSwitchFromPhoto(currentMessageObject, currentFileLocation, currentIndex);
        int prevIndex = currentIndex;
        currentIndex = index;
        boolean isVideo = false;
        boolean sameImage = false;

        if (!imagesArr.isEmpty()) {
            if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                closePhoto(false, false);
                return;
            }
            MessageObject newMessageObject = imagesArr.get(currentIndex);
            sameImage = currentMessageObject != null && currentMessageObject.getId() == newMessageObject.getId();
            currentMessageObject = newMessageObject;
            isVideo = currentMessageObject.isVideo();
            if (currentMessageObject.canDeleteMessage(null)) {
                menuItem.showSubItem(gallery_menu_delete);
            } else {
                menuItem.hideSubItem(gallery_menu_delete);
            }
            if (currentMessageObject.isFromUser()) {
                TLRPC.User user = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
                if (user != null) {
                    nameTextView.setText(UserObject.getUserName(user));
                } else {
                    nameTextView.setText("");
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(currentMessageObject.messageOwner.to_id.channel_id);
                if (chat != null) {
                    nameTextView.setText(chat.title);
                } else {
                    nameTextView.setText("");
                }
            }
            long date = (long) currentMessageObject.messageOwner.date * 1000;
            String dateString = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(date)), LocaleController.getInstance().formatterDay.format(new Date(date)));
            if (currentFileNames[0] != null && isVideo) {
                dateTextView.setText(String.format("%s (%s)", dateString, AndroidUtilities.formatFileSize(currentMessageObject.getDocument().size)));
            } else {
                dateTextView.setText(dateString);
            }
            CharSequence caption = currentMessageObject.caption;
            setCurrentCaption(caption);

            if (totalImagesCount + totalImagesCountMerge != 0 && !needSearchImageInArr) {
                if (opennedFromMedia) {
                    if (imagesArr.size() < totalImagesCount + totalImagesCountMerge && !loadingMoreImages && currentIndex > imagesArr.size() - 5) {
                        int loadFromMaxId = imagesArr.isEmpty() ? 0 : imagesArr.get(imagesArr.size() - 1).getId();
                        int loadIndex = 0;
                        if (endReached[loadIndex] && mergeDialogId != 0) {
                            loadIndex = 1;
                            if (!imagesArr.isEmpty() && imagesArr.get(imagesArr.size() - 1).getDialogId() != mergeDialogId) {
                                loadFromMaxId = 0;
                            }
                        }

                        SharedMediaQuery.loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 0, 80, loadFromMaxId, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                        loadingMoreImages = true;
                    }
                    actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, totalImagesCount + totalImagesCountMerge));
                } else {
                    if (imagesArr.size() < totalImagesCount + totalImagesCountMerge && !loadingMoreImages && currentIndex < 5) {
                        int loadFromMaxId = imagesArr.isEmpty() ? 0 : imagesArr.get(0).getId();
                        int loadIndex = 0;
                        if (endReached[loadIndex] && mergeDialogId != 0) {
                            loadIndex = 1;
                            if (!imagesArr.isEmpty() && imagesArr.get(0).getDialogId() != mergeDialogId) {
                                loadFromMaxId = 0;
                            }
                        }

                        SharedMediaQuery.loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 0, 80, loadFromMaxId, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                        loadingMoreImages = true;
                    }
                    actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount + totalImagesCountMerge - imagesArr.size()) + currentIndex + 1, totalImagesCount + totalImagesCountMerge));
                }
            } else if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
            }
            if (currentMessageObject.messageOwner.ttl != 0) {
                menuItem.hideSubItem(gallery_menu_save);
                shareButton.setVisibility(View.GONE);
            } else {
                menuItem.showSubItem(gallery_menu_save);
                shareButton.setVisibility(View.VISIBLE);
            }
        } else if (!imagesArrLocations.isEmpty()) {
            nameTextView.setText("");
            dateTextView.setText("");
            if (avatarsUserId == UserConfig.getClientUserId() && !avatarsArr.isEmpty()) {
                menuItem.showSubItem(gallery_menu_delete);
            } else {
                menuItem.hideSubItem(gallery_menu_delete);
            }
            TLRPC.FileLocation old = currentFileLocation;
            if (index < 0 || index >= imagesArrLocations.size()) {
                closePhoto(false, false);
                return;
            }
            currentFileLocation = imagesArrLocations.get(index);
            if (old != null && currentFileLocation != null && old.local_id == currentFileLocation.local_id && old.volume_id == currentFileLocation.volume_id) {
                sameImage = true;
            }
            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArrLocations.size()));
            menuItem.showSubItem(gallery_menu_save);
            shareButton.setVisibility(View.VISIBLE);
        } else if (!imagesArrLocals.isEmpty()) {
            Object object = imagesArrLocals.get(index);
            if (index < 0 || index >= imagesArrLocals.size()) {
                closePhoto(false, false);
                return;
            }
            boolean fromCamera = false;
            CharSequence caption = null;
            if (object instanceof MediaController.PhotoEntry) {
                currentPathObject = ((MediaController.PhotoEntry) object).path;
                fromCamera = ((MediaController.PhotoEntry) object).bucketId == 0 && ((MediaController.PhotoEntry) object).dateTaken == 0 && imagesArrLocals.size() == 1;
                caption = ((MediaController.PhotoEntry) object).caption;
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                if (searchImage.document != null) {
                    currentPathObject = FileLoader.getPathToAttach(searchImage.document, true).getAbsolutePath();
                } else {
                    currentPathObject = searchImage.imageUrl;
                }
                caption = searchImage.caption;
            }
            if (fromCamera) {
                actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
            } else {
                actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArrLocals.size()));
            }
            if (sendPhotoType == 0) {
                checkImageView.setChecked(placeProvider.isPhotoChecked(currentIndex), false);
            }

            setCurrentCaption(caption);
            updateCaptionTextForCurrentPhoto(object);
        }


        if (currentPlaceObject != null) {
            if (animationInProgress == 0) {
                currentPlaceObject.imageReceiver.setVisible(true, true);
            } else {
                showAfterAnimation = currentPlaceObject;
            }
        }
        currentPlaceObject = placeProvider.getPlaceForPhoto(currentMessageObject, currentFileLocation, currentIndex);
        if (currentPlaceObject != null) {
            if (animationInProgress == 0) {
                currentPlaceObject.imageReceiver.setVisible(false, true);
            } else {
                hideAfterAnimation = currentPlaceObject;
            }
        }

        if (!sameImage) {
            draggingDown = false;
            translationX = 0;
            translationY = 0;
            scale = 1;
            animateToX = 0;
            animateToY = 0;
            animateToScale = 1;
            animationStartTime = 0;
            imageMoveAnimation = null;
            changeModeAnimation = null;

            pinchStartDistance = 0;
            pinchStartScale = 1;
            pinchCenterX = 0;
            pinchCenterY = 0;
            pinchStartX = 0;
            pinchStartY = 0;
            moveStartX = 0;
            moveStartY = 0;
            zooming = false;
            moving = false;
            doubleTap = false;
            invalidCoords = false;
            canDragDown = true;
            changingPage = false;
            switchImageAfterAnimation = 0;
            canZoom = !imagesArrLocals.isEmpty() || (currentFileNames[0] != null && !isVideo && radialProgressViews[0].backgroundState != 0);
            updateMinMax(scale);
        }

        if (prevIndex == -1) {
            setImages();

            for (int a = 0; a < 3; a++) {
                checkProgress(a, false);
            }
        } else {
            checkProgress(0, false);
            if (prevIndex > currentIndex) {
                ImageReceiver temp = rightImage;
                rightImage = centerImage;
                centerImage = leftImage;
                leftImage = temp;

                RadialProgressView tempProgress = radialProgressViews[0];
                radialProgressViews[0] = radialProgressViews[2];
                radialProgressViews[2] = tempProgress;
                setIndexToImage(leftImage, currentIndex - 1);

                checkProgress(1, false);
                checkProgress(2, false);
            } else if (prevIndex < currentIndex) {
                ImageReceiver temp = leftImage;
                leftImage = centerImage;
                centerImage = rightImage;
                rightImage = temp;

                RadialProgressView tempProgress = radialProgressViews[0];
                radialProgressViews[0] = radialProgressViews[1];
                radialProgressViews[1] = tempProgress;
                setIndexToImage(rightImage, currentIndex + 1);

                checkProgress(1, false);
                checkProgress(2, false);
            }
        }
    }

    private void setCurrentCaption(final CharSequence caption) {
        if (caption != null && caption.length() > 0) {
            captionTextView = captionTextViewOld;
            captionTextViewOld = captionTextViewNew;
            captionTextViewNew = captionTextView;

            captionItem.setIcon(R.drawable.photo_text2);
            CharSequence str = Emoji.replaceEmoji(new SpannableStringBuilder(caption.toString()), MessageObject.getTextPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            captionTextView.setTag(str);
            captionTextView.setText(str);
            ViewProxy.setAlpha(captionTextView, bottomLayout.getVisibility() == View.VISIBLE || pickerView.getVisibility() == View.VISIBLE ? 1.0f : 0.0f);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    captionTextViewOld.setTag(null);
                    captionTextViewOld.clearAnimation();
                    captionTextViewOld.setVisibility(View.INVISIBLE);
                    captionTextViewNew.setVisibility(bottomLayout.getVisibility() == View.VISIBLE || pickerView.getVisibility() == View.VISIBLE ? View.VISIBLE : View.INVISIBLE);
                }
            });
        } else {
            captionItem.setIcon(R.drawable.photo_text);
            captionTextView.setTag(null);
            captionTextView.clearAnimation();
            captionTextView.setVisibility(View.INVISIBLE);
        }
    }

    private void checkProgress(int a, boolean animated) {
        if (currentFileNames[a] != null) {
            int index = currentIndex;
            if (a == 1) {
                index += 1;
            } else if (a == 2) {
                index -= 1;
            }
            File f = null;
            boolean isVideo = false;
            if (currentMessageObject != null) {
                MessageObject messageObject = imagesArr.get(index);
                f = FileLoader.getPathToMessage(messageObject.messageOwner);
                isVideo = messageObject.isVideo();
            } else if (currentFileLocation != null) {
                TLRPC.FileLocation location = imagesArrLocations.get(index);
                f = FileLoader.getPathToAttach(location, avatarsUserId != 0);
            } else if (currentPathObject != null) {
                f = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), currentFileNames[a]);
                if (!f.exists()) {
                    f = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFileNames[a]);
                }
            }
            if (f != null && f.exists()) {
                if (isVideo) {
                    radialProgressViews[a].setBackgroundState(3, animated);
                } else {
                    radialProgressViews[a].setBackgroundState(-1, animated);
                }
            } else {
                if (isVideo) {
                    if (!FileLoader.getInstance().isLoadingFile(currentFileNames[a])) {
                        radialProgressViews[a].setBackgroundState(2, false);
                    } else {
                        radialProgressViews[a].setBackgroundState(1, false);
                    }
                } else {
                    radialProgressViews[a].setBackgroundState(0, animated);
                }
                Float progress = ImageLoader.getInstance().getFileProgress(currentFileNames[a]);
                if (progress == null) {
                    progress = 0.0f;
                }
                radialProgressViews[a].setProgress(progress, false);
            }
            if (a == 0) {
                canZoom = !imagesArrLocals.isEmpty() || (currentFileNames[0] != null && !isVideo && radialProgressViews[0].backgroundState != 0);
            }
        } else {
            radialProgressViews[a].setBackgroundState(-1, animated);
        }
    }

    private void setIndexToImage(ImageReceiver imageReceiver, int index) {
        imageReceiver.setOrientation(0, false);
        if (!imagesArrLocals.isEmpty()) {
            imageReceiver.setParentMessageObject(null);
            if (index >= 0 && index < imagesArrLocals.size()) {
                Object object = imagesArrLocals.get(index);
                int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                Bitmap placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                if (placeHolder == null) {
                    placeHolder = placeProvider.getThumbForPhoto(null, null, index);
                }
                String path = null;
                TLRPC.Document document = null;
                int imageSize = 0;
                String filter = null;
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                    if (photoEntry.imagePath != null) {
                        path = photoEntry.imagePath;
                    } else {
                        imageReceiver.setOrientation(photoEntry.orientation, false);
                        path = photoEntry.path;
                    }
                    filter = String.format(Locale.US, "%d_%d", size, size);
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage photoEntry = (MediaController.SearchImage) object;
                    if (photoEntry.imagePath != null) {
                        path = photoEntry.imagePath;
                    } else if (photoEntry.document != null) {
                        document = photoEntry.document;
                        imageSize = photoEntry.document.size;
                    } else {
                        path = photoEntry.imageUrl;
                        imageSize = photoEntry.size;
                    }
                    filter = "d";
                }
                if (document != null) {
                    imageReceiver.setImage(document, null, "d", placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, placeHolder == null ? document.thumb.location : null, String.format(Locale.US, "%d_%d", size, size), imageSize, null, false);
                } else {
                    imageReceiver.setImage(path, filter, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, null, imageSize);
                }
            } else {
                imageReceiver.setImageBitmap((Bitmap) null);
            }
        } else {
            int size[] = new int[1];
            TLRPC.FileLocation fileLocation = getFileLocation(index, size);

            if (fileLocation != null) {
                MessageObject messageObject = null;
                if (!imagesArr.isEmpty()) {
                    messageObject = imagesArr.get(index);
                }
                imageReceiver.setParentMessageObject(messageObject);
                if (messageObject != null) {
                    imageReceiver.setShouldGenerateQualityThumb(true);
                }

                if (messageObject != null && messageObject.isVideo()) {
                    imageReceiver.setNeedsQualityThumb(true);
                    if (messageObject.photoThumbs != null && !messageObject.photoThumbs.isEmpty()) {
                        Bitmap placeHolder = null;
                        if (currentThumb != null && imageReceiver == centerImage) {
                            placeHolder = currentThumb;
                        }
                        TLRPC.PhotoSize thumbLocation = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
                        imageReceiver.setImage(null, null, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, thumbLocation.location, "b", 0, null, true);
                    } else {
                        imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                    }
                } else {
                    imageReceiver.setNeedsQualityThumb(false);
                    Bitmap placeHolder = null;
                    if (currentThumb != null && imageReceiver == centerImage) {
                        placeHolder = currentThumb;
                    }
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    TLRPC.PhotoSize thumbLocation = messageObject != null ? FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100) : null;
                    imageReceiver.setImage(fileLocation, null, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, thumbLocation != null ? thumbLocation.location : null, "b", size[0], null, avatarsUserId != 0);
                }
            } else {
                imageReceiver.setNeedsQualityThumb(false);
                imageReceiver.setParentMessageObject(null);
                if (size[0] == 0) {
                    imageReceiver.setImageBitmap((Bitmap) null);
                } else {
                    imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                }
            }
        }
    }

    public boolean isShowingImage(MessageObject object) {
        return isVisible && !disableShowCheck && object != null && currentMessageObject != null && currentMessageObject.getId() == object.getId();
    }

    public boolean isShowingImage(TLRPC.FileLocation object) {
        return isVisible && !disableShowCheck && object != null && currentFileLocation != null && object.local_id == currentFileLocation.local_id && object.volume_id == currentFileLocation.volume_id && object.dc_id == currentFileLocation.dc_id;
    }

    public boolean isShowingImage(String object) {
        return isVisible && !disableShowCheck && object != null && currentPathObject != null && object.equals(currentPathObject);
    }

    public void openPhoto(final MessageObject messageObject, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        openPhoto(messageObject, null, null, null, 0, provider, null, dialogId, mergeDialogId);
    }

    public void openPhoto(final TLRPC.FileLocation fileLocation, final PhotoViewerProvider provider) {
        openPhoto(null, fileLocation, null, null, 0, provider, null, 0, 0);
    }

    public void openPhoto(final ArrayList<MessageObject> messages, final int index, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        openPhoto(messages.get(index), null, messages, null, index, provider, null, dialogId, mergeDialogId);
    }

    public void openPhotoForSelect(final ArrayList<Object> photos, final int index, int type, final PhotoViewerProvider provider, ChatActivity chatActivity) {
        sendPhotoType = type;
        if (pickerView != null) {
            pickerView.doneButtonTextView.setText(sendPhotoType == 1 ? LocaleController.getString("Set", R.string.Set).toUpperCase() : LocaleController.getString("Send", R.string.Send).toUpperCase());
        }
        openPhoto(null, null, null, photos, index, provider, chatActivity, 0, 0);
    }

    private boolean checkAnimation() {
        if (animationInProgress != 0) {
            if (Math.abs(transitionAnimationStartTime - System.currentTimeMillis()) >= 500) {
                if (animationEndRunnable != null) {
                    animationEndRunnable.run();
                    animationEndRunnable = null;
                }
                animationInProgress = 0;
            }
        }
        return animationInProgress != 0;
    }

    public void openPhoto(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<Object> photos, final int index, final PhotoViewerProvider provider, ChatActivity chatActivity, long dialogId, long mDialogId) {
        if (parentActivity == null || isVisible || provider == null && checkAnimation() || messageObject == null && fileLocation == null && messages == null && photos == null) {
            return;
        }

        final PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, fileLocation, index);
        if (object == null && photos == null) {
            return;
        }

        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        if (windowView.attachedToWindow) {
            try {
                wm.removeView(windowView);
            } catch (Exception e) {
                //don't promt
            }
        }

        try {
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED;
            windowView.setFocusable(false);
            containerView.setFocusable(false);
            wm.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return;
        }

        parentChatActivity = chatActivity;

        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.userPhotosLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);

        placeProvider = provider;
        mergeDialogId = mDialogId;
        currentDialogId = dialogId;

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        isVisible = true;
        toggleActionBar(true, false);

        if (object != null) {
            disableShowCheck = true;
            animationInProgress = 1;
            onPhotoShow(messageObject, fileLocation, messages, photos, index, object);

            final Rect drawRegion = object.imageReceiver.getDrawRegion();
            int orientation = object.imageReceiver.getOrientation();

            animatingImageView.setVisibility(View.VISIBLE);
            animatingImageView.setRadius(object.radius);
            animatingImageView.setOrientation(orientation);
            animatingImageView.setNeedRadius(object.radius != 0);
            animatingImageView.setImageBitmap(object.thumb);

            ViewProxy.setAlpha(animatingImageView, 1.0f);
            ViewProxy.setPivotX(animatingImageView, 0.0f);
            ViewProxy.setPivotY(animatingImageView, 0.0f);
            ViewProxy.setScaleX(animatingImageView, object.scale);
            ViewProxy.setScaleY(animatingImageView, object.scale);
            ViewProxy.setTranslationX(animatingImageView, object.viewX + drawRegion.left * object.scale);
            ViewProxy.setTranslationY(animatingImageView, object.viewY + drawRegion.top * object.scale);
            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            layoutParams.width = (drawRegion.right - drawRegion.left);
            layoutParams.height = (drawRegion.bottom - drawRegion.top);
            animatingImageView.setLayoutParams(layoutParams);

            float scaleX = (float) AndroidUtilities.displaySize.x / layoutParams.width;
            float scaleY = (float) (AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight) / layoutParams.height;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            float width = layoutParams.width * scale;
            float height = layoutParams.height * scale;
            float xPos = (AndroidUtilities.displaySize.x - width) / 2.0f;
            float yPos = (AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - height) / 2.0f;
            int clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
            int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());

            int coords2[] = new int[2];
            object.parentView.getLocationInWindow(coords2);
            int clipTop = coords2[1] - AndroidUtilities.statusBarHeight - (object.viewY + drawRegion.top) + object.clipTopAddition;
            if (clipTop < 0) {
                clipTop = 0;
            }
            int clipBottom = (object.viewY + drawRegion.top + layoutParams.height) - (coords2[1] + object.parentView.getHeight() - AndroidUtilities.statusBarHeight) + object.clipBottomAddition;
            if (clipBottom < 0) {
                clipBottom = 0;
            }
            clipTop = Math.max(clipTop, clipVertical);
            clipBottom = Math.max(clipBottom, clipVertical);

            animationValues[0][0] = ViewProxy.getScaleX(animatingImageView);
            animationValues[0][1] = ViewProxy.getScaleY(animatingImageView);
            animationValues[0][2] = ViewProxy.getTranslationX(animatingImageView);
            animationValues[0][3] = ViewProxy.getTranslationY(animatingImageView);
            animationValues[0][4] = clipHorizontal * object.scale;
            animationValues[0][5] = clipTop * object.scale;
            animationValues[0][6] = clipBottom * object.scale;
            animationValues[0][7] = animatingImageView.getRadius();

            animationValues[1][0] = scale;
            animationValues[1][1] = scale;
            animationValues[1][2] = xPos;
            animationValues[1][3] = yPos;
            animationValues[1][4] = 0;
            animationValues[1][5] = 0;
            animationValues[1][6] = 0;
            animationValues[1][7] = 0;

            animatingImageView.setAnimationProgress(0);
            backgroundDrawable.setAlpha(0);
            ViewProxy.setAlpha(containerView, 0);

            final AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            animatorSet.playTogether(
                    ObjectAnimatorProxy.ofFloat(animatingImageView, "animationProgress", 0.0f, 1.0f),
                    ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0, 255),
                    ObjectAnimatorProxy.ofFloat(containerView, "alpha", 0.0f, 1.0f)
            );

            animationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (containerView == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 18) {
                        containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    animationInProgress = 0;
                    transitionAnimationStartTime = 0;
                    setImages();
                    containerView.invalidate();
                    animatingImageView.setVisibility(View.GONE);
                    if (showAfterAnimation != null) {
                        showAfterAnimation.imageReceiver.setVisible(true, true);
                    }
                    if (hideAfterAnimation != null) {
                        hideAfterAnimation.imageReceiver.setVisible(false, true);
                    }
                    if (photos != null) {
                        windowLayoutParams.flags = 0;
                        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
                        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                        wm.updateViewLayout(windowView, windowLayoutParams);
                        windowView.setFocusable(true);
                        containerView.setFocusable(true);
                    }
                }
            };

            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().setAnimationInProgress(false);
                            if (animationEndRunnable != null) {
                                animationEndRunnable.run();
                                animationEndRunnable = null;
                            }
                        }
                    });
                }
            });
            transitionAnimationStartTime = System.currentTimeMillis();
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoaded, NotificationCenter.mediaDidLoaded});
                    NotificationCenter.getInstance().setAnimationInProgress(true);
                    animatorSet.start();
                }
            });
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            backgroundDrawable.drawRunnable = new Runnable() {
                @Override
                public void run() {
                    disableShowCheck = false;
                    object.imageReceiver.setVisible(false, true);
                }
            };
        } else {
            if (photos != null) {
                windowLayoutParams.flags = 0;
                windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
                wm.updateViewLayout(windowView, windowLayoutParams);
                windowView.setFocusable(true);
                containerView.setFocusable(true);
            }

            backgroundDrawable.setAlpha(255);
            ViewProxy.setAlpha(containerView, 1.0f);
            onPhotoShow(messageObject, fileLocation, messages, photos, index, object);
        }
    }

    public void closePhoto(boolean animated, boolean fromEditMode) {
        if (!fromEditMode && currentEditMode != 0) {
            if (currentEditMode == 1) {
                photoCropView.cancelAnimationRunnable();
            }
            switchToEditMode(0);
            return;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        if (currentEditMode != 0) {
            if (currentEditMode == 2) {
                photoFilterView.shutdown();
                containerView.removeView(photoFilterView);
                photoFilterView = null;
            } else if (currentEditMode == 1) {
                editorDoneLayout.setVisibility(View.GONE);
                photoCropView.setVisibility(View.GONE);
            }
            currentEditMode = 0;
        }

        if (parentActivity == null || !isVisible || checkAnimation() || placeProvider == null) {
            return;
        }
        if (captionEditText.hideActionMode() && !fromEditMode) {
            return;
        }

        captionEditText.onDestroy();
        parentChatActivity = null;
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.userPhotosLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        ConnectionsManager.getInstance().cancelRequestsForGuid(classGuid);

        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        ConnectionsManager.getInstance().cancelRequestsForGuid(classGuid);

        final PlaceProviderObject object = placeProvider.getPlaceForPhoto(currentMessageObject, currentFileLocation, currentIndex);

        if (animated) {
            animationInProgress = 1;
            animatingImageView.setVisibility(View.VISIBLE);
            containerView.invalidate();

            AnimatorSetProxy animatorSet = new AnimatorSetProxy();

            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            Rect drawRegion = null;
            animatingImageView.setOrientation(centerImage.getOrientation());
            if (object != null) {
                animatingImageView.setNeedRadius(object.radius != 0);
                drawRegion = object.imageReceiver.getDrawRegion();
                layoutParams.width = drawRegion.right - drawRegion.left;
                layoutParams.height = drawRegion.bottom - drawRegion.top;
                animatingImageView.setImageBitmap(object.thumb);
            } else {
                animatingImageView.setNeedRadius(false);
                layoutParams.width = centerImage.getImageWidth();
                layoutParams.height = centerImage.getImageHeight();
                animatingImageView.setImageBitmap(centerImage.getBitmap());
            }
            animatingImageView.setLayoutParams(layoutParams);

            float scaleX = (float) AndroidUtilities.displaySize.x / layoutParams.width;
            float scaleY = (float) (AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight) / layoutParams.height;
            float scale2 = scaleX > scaleY ? scaleY : scaleX;
            float width = layoutParams.width * scale * scale2;
            float height = layoutParams.height * scale * scale2;
            float xPos = (AndroidUtilities.displaySize.x - width) / 2.0f;
            float yPos = (AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - height) / 2.0f;
            ViewProxy.setTranslationX(animatingImageView, xPos + translationX);
            ViewProxy.setTranslationY(animatingImageView, yPos + translationY);
            ViewProxy.setScaleX(animatingImageView, scale * scale2);
            ViewProxy.setScaleY(animatingImageView, scale * scale2);

            if (object != null) {
                object.imageReceiver.setVisible(false, true);
                int clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
                int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());

                int coords2[] = new int[2];
                object.parentView.getLocationInWindow(coords2);
                int clipTop = coords2[1] - AndroidUtilities.statusBarHeight - (object.viewY + drawRegion.top) + object.clipTopAddition;
                if (clipTop < 0) {
                    clipTop = 0;
                }
                int clipBottom = (object.viewY + drawRegion.top + (drawRegion.bottom - drawRegion.top)) - (coords2[1] + object.parentView.getHeight() - AndroidUtilities.statusBarHeight) + object.clipBottomAddition;
                if (clipBottom < 0) {
                    clipBottom = 0;
                }

                clipTop = Math.max(clipTop, clipVertical);
                clipBottom = Math.max(clipBottom, clipVertical);

                animationValues[0][0] = ViewProxy.getScaleX(animatingImageView);
                animationValues[0][1] = ViewProxy.getScaleY(animatingImageView);
                animationValues[0][2] = ViewProxy.getTranslationX(animatingImageView);
                animationValues[0][3] = ViewProxy.getTranslationY(animatingImageView);
                animationValues[0][4] = 0;
                animationValues[0][5] = 0;
                animationValues[0][6] = 0;
                animationValues[0][7] = 0;

                animationValues[1][0] = object.scale;
                animationValues[1][1] = object.scale;
                animationValues[1][2] = object.viewX + drawRegion.left * object.scale;
                animationValues[1][3] = object.viewY + drawRegion.top * object.scale;
                animationValues[1][4] = clipHorizontal * object.scale;
                animationValues[1][5] = clipTop * object.scale;
                animationValues[1][6] = clipBottom * object.scale;
                animationValues[1][7] = object.radius;

                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "animationProgress", 0.0f, 1.0f),
                        ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0),
                        ObjectAnimatorProxy.ofFloat(containerView, "alpha", 0.0f)
                );
            } else {
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "alpha", 0.0f),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "translationY", translationY >= 0 ? AndroidUtilities.displaySize.y : -AndroidUtilities.displaySize.y),
                        ObjectAnimatorProxy.ofFloat(containerView, "alpha", 0.0f)
                );
            }

            animationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 18) {
                        containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    animationInProgress = 0;
                    onPhotoClosed(object);
                }
            };

            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (animationEndRunnable != null) {
                                animationEndRunnable.run();
                                animationEndRunnable = null;
                            }
                        }
                    });
                }
            });
            transitionAnimationStartTime = System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            animatorSet.start();
        } else {
            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            animatorSet.playTogether(
                    ObjectAnimatorProxy.ofFloat(containerView, "scaleX", 0.9f),
                    ObjectAnimatorProxy.ofFloat(containerView, "scaleY", 0.9f),
                    ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0),
                    ObjectAnimatorProxy.ofFloat(containerView, "alpha", 0.0f)
            );
            animationInProgress = 2;
            animationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (containerView == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 18) {
                        containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    animationInProgress = 0;
                    onPhotoClosed(object);
                    ViewProxy.setScaleX(containerView, 1.0f);
                    ViewProxy.setScaleY(containerView, 1.0f);
                    containerView.clearAnimation();
                }
            };
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    if (animationEndRunnable != null) {
                        animationEndRunnable.run();
                        animationEndRunnable = null;
                    }
                }
            });
            transitionAnimationStartTime = System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            animatorSet.start();
        }
    }

    public void destroyPhotoViewer() {
        if (parentActivity == null || windowView == null) {
            return;
        }
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeViewImmediate(windowView);
            }
            windowView = null;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (captionEditText != null) {
            captionEditText.onDestroy();
        }
        Instance = null;
    }

    private void onPhotoClosed(PlaceProviderObject object) {
        isVisible = false;
        disableShowCheck = true;
        currentMessageObject = null;
        currentFileLocation = null;
        currentPathObject = null;
        currentThumb = null;
        for (int a = 0; a < 3; a++) {
            if (radialProgressViews[a] != null) {
                radialProgressViews[a].setBackgroundState(-1, false);
            }
        }
        centerImage.setImageBitmap((Bitmap) null);
        leftImage.setImageBitmap((Bitmap) null);
        rightImage.setImageBitmap((Bitmap) null);
        containerView.post(new Runnable() {
            @Override
            public void run() {
                animatingImageView.setImageBitmap(null);
                try {
                    if (windowView.getParent() != null) {
                        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeView(windowView);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
        if (placeProvider != null) {
            placeProvider.willHidePhotoViewer();
        }
        placeProvider = null;
        disableShowCheck = false;
        if (object != null) {
            object.imageReceiver.setVisible(true, true);
        }
    }

    private void redraw(final int count) {
        if (count < 6) {
            if (containerView != null) {
                containerView.invalidate();
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        redraw(count + 1);
                    }
                }, 100);
            }
        }
    }

    public void onResume() {
        redraw(0); //workaround for camera bug
    }

    public boolean isVisible() {
        return isVisible && placeProvider != null;
    }

    private void updateMinMax(float scale) {
        int maxW = (int) (centerImage.getImageWidth() * scale - getContainerViewWidth()) / 2;
        int maxH = (int) (centerImage.getImageHeight() * scale - getContainerViewHeight()) / 2;
        if (maxW > 0) {
            minX = -maxW;
            maxX = maxW;
        } else {
            minX = maxX = 0;
        }
        if (maxH > 0) {
            minY = -maxH;
            maxY = maxH;
        } else {
            minY = maxY = 0;
        }
        if (currentEditMode == 1) {
            maxX += photoCropView.getLimitX();
            maxY += photoCropView.getLimitY();
            minX -= photoCropView.getLimitWidth();
            minY -= photoCropView.getLimitHeight();
        }
    }

    private int getAdditionX() {
        if (currentEditMode != 0) {
            return AndroidUtilities.dp(14);
        }
        return 0;
    }

    private int getAdditionY() {
        if (currentEditMode != 0) {
            return AndroidUtilities.dp(14);
        }
        return 0;
    }

    private int getContainerViewWidth() {
        return getContainerViewWidth(currentEditMode);
    }

    private int getContainerViewWidth(int mode) {
        int width = containerView.getWidth();
        if (mode != 0) {
            width -= AndroidUtilities.dp(28);
        }
        return width;
    }

    private int getContainerViewHeight() {
        return getContainerViewHeight(currentEditMode);
    }

    private int getContainerViewHeight(int mode) {
        //int height = containerView.getHeight();
        int height = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight;
        if (mode == 1) {
            height -= AndroidUtilities.dp(76);
        } else if (mode == 2) {
            height -= AndroidUtilities.dp(154);
        }
        return height;
    }

    private boolean onTouchEvent(MotionEvent ev) {
        if (animationInProgress != 0 || animationStartTime != 0) {
            return false;
        }

        if (currentEditMode == 2) {
            photoFilterView.onTouch(ev);
            return true;
        }

        if (currentEditMode == 1) {
            if (ev.getPointerCount() == 1) {
                if (photoCropView.onTouch(ev)) {
                    updateMinMax(scale);
                    return true;
                }
            } else {
                photoCropView.onTouch(null);
            }
        }

        if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
            return true;
        }

        if (currentEditMode == 0 && ev.getPointerCount() == 1 && gestureDetector.onTouchEvent(ev)) {
            if (doubleTap) {
                doubleTap = false;
                moving = false;
                zooming = false;
                checkMinMax(false);
                return true;
            }
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (currentEditMode == 1) {
                photoCropView.cancelAnimationRunnable();
            }
            discardTap = false;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            if (!draggingDown && !changingPage) {
                if (canZoom && ev.getPointerCount() == 2) {
                    pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                    pinchStartScale = scale;
                    pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                    pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                    pinchStartX = translationX;
                    pinchStartY = translationY;
                    zooming = true;
                    moving = false;
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev.getPointerCount() == 1) {
                    moveStartX = ev.getX();
                    dragY = moveStartY = ev.getY();
                    draggingDown = false;
                    canDragDown = true;
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (currentEditMode == 1) {
                photoCropView.cancelAnimationRunnable();
            }
            if (canZoom && ev.getPointerCount() == 2 && !draggingDown && zooming && !changingPage) {
                discardTap = true;
                scale = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                containerView.invalidate();
            } else if (ev.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - moveStartX);
                float dy = Math.abs(ev.getY() - dragY);
                if (dx > AndroidUtilities.dp(3) || dy > AndroidUtilities.dp(3)) {
                    discardTap = true;
                }
                if (!(placeProvider instanceof EmptyPhotoViewerProvider) && currentEditMode == 0 && canDragDown && !draggingDown && scale == 1 && dy >= AndroidUtilities.dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible && canShowBottom) {
                        toggleActionBar(false, true);
                    } else if (pickerView.getVisibility() == View.VISIBLE) {
                        toggleActionBar(false, true);
                        toggleCheckImageView(false);
                    }
                    return true;
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    containerView.invalidate();
                } else if (!invalidCoords && animationStartTime == 0) {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || currentEditMode != 0 || scale == 1 && Math.abs(moveDy) + AndroidUtilities.dp(12) < Math.abs(moveDx) || scale != 1) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }

                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX && (currentEditMode != 0 || !rightImage.hasImage()) || translationX > maxX && (currentEditMode != 0 || !leftImage.hasImage())) {
                            moveDx /= 3.0f;
                        }
                        if (maxY == 0 && minY == 0 && currentEditMode == 0) {
                            if (translationY - moveDy < minY) {
                                translationY = minY;
                                moveDy = 0;
                            } else if (translationY - moveDy > maxY) {
                                translationY = maxY;
                                moveDy = 0;
                            }
                        } else {
                            if (translationY < minY || translationY > maxY) {
                                moveDy /= 3.0f;
                            }
                        }

                        translationX -= moveDx;
                        if (scale != 1 || currentEditMode != 0) {
                            translationY -= moveDy;
                        }

                        containerView.invalidate();
                    }
                } else {
                    invalidCoords = false;
                    moveStartX = ev.getX();
                    moveStartY = ev.getY();
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL || ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            if (currentEditMode == 1) {
                photoCropView.startAnimationRunnable();
            }
            if (zooming) {
                invalidCoords = true;
                if (scale < 1.0f) {
                    updateMinMax(1.0f);
                    animateTo(1.0f, 0, 0, true);
                } else if (scale > 3.0f) {
                    float atx = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (3.0f / pinchStartScale);
                    float aty = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (3.0f / pinchStartScale);
                    updateMinMax(3.0f);
                    if (atx < minX) {
                        atx = minX;
                    } else if (atx > maxX) {
                        atx = maxX;
                    }
                    if (aty < minY) {
                        aty = minY;
                    } else if (aty > maxY) {
                        aty = maxY;
                    }
                    animateTo(3.0f, atx, aty, true);
                } else {
                    checkMinMax(true);
                }
                zooming = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - ev.getY()) > getContainerViewHeight() / 6.0f) {
                    closePhoto(true, false);
                } else {
                    if (pickerView.getVisibility() == View.VISIBLE) {
                        toggleActionBar(true, true);
                        toggleCheckImageView(true);
                    }
                    animateTo(1, 0, 0, false);
                }
                draggingDown = false;
            } else if (moving) {
                float moveToX = translationX;
                float moveToY = translationY;
                updateMinMax(scale);
                moving = false;
                canDragDown = true;
                float velocity = 0;
                if (velocityTracker != null && scale == 1) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocity = velocityTracker.getXVelocity();
                }

                if (currentEditMode == 0) {
                    if ((translationX < minX - getContainerViewWidth() / 3 || velocity < -AndroidUtilities.dp(650)) && rightImage.hasImage()) {
                        goToNext();
                        return true;
                    }
                    if ((translationX > maxX + getContainerViewWidth() / 3 || velocity > AndroidUtilities.dp(650)) && leftImage.hasImage()) {
                        goToPrev();
                        return true;
                    }
                }

                if (translationX < minX) {
                    moveToX = minX;
                } else if (translationX > maxX) {
                    moveToX = maxX;
                }
                if (translationY < minY) {
                    moveToY = minY;
                } else if (translationY > maxY) {
                    moveToY = maxY;
                }
                animateTo(scale, moveToX, moveToY, false);
            }
        }
        return false;
    }

    private void checkMinMax(boolean zoom) {
        float moveToX = translationX;
        float moveToY = translationY;
        updateMinMax(scale);
        if (translationX < minX) {
            moveToX = minX;
        } else if (translationX > maxX) {
            moveToX = maxX;
        }
        if (translationY < minY) {
            moveToY = minY;
        } else if (translationY > maxY) {
            moveToY = maxY;
        }
        animateTo(scale, moveToX, moveToY, zoom);
    }

    private void goToNext() {
        float extra = 0;
        if (scale != 1) {
            extra = (getContainerViewWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - getContainerViewWidth() - extra - PAGE_SPACING / 2, translationY, false);
    }

    private void goToPrev() {
        float extra = 0;
        if (scale != 1) {
            extra = (getContainerViewWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + getContainerViewWidth() + extra + PAGE_SPACING / 2, translationY, false);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        animateTo(newScale, newTx, newTy, isZoom, 250);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom, int duration) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            return;
        }
        zoomAnimation = isZoom;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        animationStartTime = System.currentTimeMillis();
        imageMoveAnimation = new AnimatorSetProxy();
        imageMoveAnimation.playTogether(
                ObjectAnimatorProxy.ofFloat(this, "animationValue", 0, 1)
        );
        imageMoveAnimation.setInterpolator(interpolator);
        imageMoveAnimation.setDuration(duration);
        imageMoveAnimation.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animation) {
                imageMoveAnimation = null;
                containerView.invalidate();
            }
        });
        imageMoveAnimation.start();
    }

    public void setAnimationValue(float value) {
        animationValue = value;
        containerView.invalidate();
    }

    public float getAnimationValue() {
        return animationValue;
    }

    private void onDraw(Canvas canvas) {
        if (animationInProgress == 1 || !isVisible && animationInProgress != 2) {
            return;
        }

        float currentTranslationY;
        float currentTranslationX;
        float currentScale;
        float aty = -1;

        if (imageMoveAnimation != null) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }

            float ts = scale + (animateToScale - scale) * animationValue;
            float tx = translationX + (animateToX - translationX) * animationValue;
            float ty = translationY + (animateToY - translationY) * animationValue;
            if (currentEditMode == 1) {
                photoCropView.setAnimationProgress(animationValue);
            }

            if (animateToScale == 1 && scale == 1 && translationX == 0) {
                aty = ty;
            }
            currentScale = ts;
            currentTranslationY = ty;
            currentTranslationX = tx;
            containerView.invalidate();
        } else {
            if (animationStartTime != 0) {
                translationX = animateToX;
                translationY = animateToY;
                scale = animateToScale;
                animationStartTime = 0;
                if (currentEditMode == 1) {
                    photoCropView.setAnimationProgress(1);
                }
                updateMinMax(scale);
                zoomAnimation = false;
            }
            if (!scroller.isFinished()) {
                if (scroller.computeScrollOffset()) {
                    if (scroller.getStartX() < maxX && scroller.getStartX() > minX) {
                        translationX = scroller.getCurrX();
                    }
                    if (scroller.getStartY() < maxY && scroller.getStartY() > minY) {
                        translationY = scroller.getCurrY();
                    }
                    containerView.invalidate();
                }
            }
            if (switchImageAfterAnimation != 0) {
                if (switchImageAfterAnimation == 1) {
                    setImageIndex(currentIndex + 1, false);
                } else if (switchImageAfterAnimation == 2) {
                    setImageIndex(currentIndex - 1, false);
                }
                switchImageAfterAnimation = 0;
            }
            currentScale = scale;
            currentTranslationY = translationY;
            currentTranslationX = translationX;
            if (!moving) {
                aty = translationY;
            }
        }

        if (currentEditMode == 0 && scale == 1 && aty != -1 && !zoomAnimation) {
            float maxValue = getContainerViewHeight() / 4.0f;
            backgroundDrawable.setAlpha((int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(aty), maxValue) / maxValue))));
        } else {
            backgroundDrawable.setAlpha(255);
        }

        ImageReceiver sideImage = null;
        if (currentEditMode == 0) {
            if (scale >= 1.0f && !zoomAnimation && !zooming) {
                if (currentTranslationX > maxX + AndroidUtilities.dp(5)) {
                    sideImage = leftImage;
                } else if (currentTranslationX < minX - AndroidUtilities.dp(5)) {
                    sideImage = rightImage;
                }
            }
            changingPage = sideImage != null;
        }

        if (sideImage == rightImage) {
            float tranlateX = currentTranslationX;
            float scaleDiff = 0;
            float alpha = 1;
            if (!zoomAnimation && tranlateX < minX) {
                alpha = Math.min(1.0f, (minX - tranlateX) / canvas.getWidth());
                scaleDiff = (1.0f - alpha) * 0.3f;
                tranlateX = -canvas.getWidth() - PAGE_SPACING / 2;
            }

            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
                canvas.translate(canvas.getWidth() + PAGE_SPACING / 2 + tranlateX, 0);
                canvas.scale(1.0f - scaleDiff, 1.0f - scaleDiff);
                int bitmapWidth = sideImage.getBitmapWidth();
                int bitmapHeight = sideImage.getBitmapHeight();

                float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                float scale = scaleX > scaleY ? scaleY : scaleX;
                int width = (int) (bitmapWidth * scale);
                int height = (int) (bitmapHeight * scale);

                sideImage.setAlpha(alpha);
                sideImage.setImageCoords(-width / 2, -height / 2, width, height);
                sideImage.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            canvas.translate(tranlateX, currentTranslationY / currentScale);
            canvas.translate((canvas.getWidth() * (scale + 1) + PAGE_SPACING) / 2, -currentTranslationY / currentScale);
            radialProgressViews[1].setScale(1.0f - scaleDiff);
            radialProgressViews[1].setAlpha(alpha);
            radialProgressViews[1].onDraw(canvas);
            canvas.restore();
        }

        float tranlateX = currentTranslationX;
        float scaleDiff = 0;
        float alpha = 1;
        if (!zoomAnimation && tranlateX > maxX && currentEditMode == 0) {
            alpha = Math.min(1.0f, (tranlateX - maxX) / canvas.getWidth());
            scaleDiff = alpha * 0.3f;
            alpha = 1.0f - alpha;
            tranlateX = maxX;
        }
        if (centerImage.hasBitmapImage()) {
            canvas.save();
            canvas.translate(getContainerViewWidth() / 2 + getAdditionX(), getContainerViewHeight() / 2 + getAdditionY());
            canvas.translate(tranlateX, currentTranslationY);
            canvas.scale(currentScale - scaleDiff, currentScale - scaleDiff);

            if (currentEditMode == 1) {
                photoCropView.setBitmapParams(currentScale, tranlateX, currentTranslationY);
            }

            int bitmapWidth = centerImage.getBitmapWidth();
            int bitmapHeight = centerImage.getBitmapHeight();

            float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
            float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            int width = (int) (bitmapWidth * scale);
            int height = (int) (bitmapHeight * scale);

            centerImage.setAlpha(alpha);
            centerImage.setImageCoords(-width / 2, -height / 2, width, height);
            centerImage.draw(canvas);
            canvas.restore();
        }
        canvas.save();
        canvas.translate(tranlateX, currentTranslationY / currentScale);
        radialProgressViews[0].setScale(1.0f - scaleDiff);
        radialProgressViews[0].setAlpha(alpha);
        radialProgressViews[0].onDraw(canvas);
        canvas.restore();

        if (sideImage == leftImage) {
            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
                canvas.translate(-(canvas.getWidth() * (scale + 1) + PAGE_SPACING) / 2 + currentTranslationX, 0);
                int bitmapWidth = sideImage.getBitmapWidth();
                int bitmapHeight = sideImage.getBitmapHeight();

                float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                float scale = scaleX > scaleY ? scaleY : scaleX;
                int width = (int) (bitmapWidth * scale);
                int height = (int) (bitmapHeight * scale);

                sideImage.setAlpha(1.0f);
                sideImage.setImageCoords(-width / 2, -height / 2, width, height);
                sideImage.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            canvas.translate(currentTranslationX, currentTranslationY / currentScale);
            canvas.translate(-(canvas.getWidth() * (scale + 1) + PAGE_SPACING) / 2, -currentTranslationY / currentScale);
            radialProgressViews[2].setScale(1.0f);
            radialProgressViews[2].setAlpha(1.0f);
            radialProgressViews[2].onDraw(canvas);
            canvas.restore();
        }
    }

    @SuppressLint("DrawAllocation")
    private void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            scale = 1;
            translationX = 0;
            translationY = 0;
            updateMinMax(scale);

            if (checkImageView != null) {
                checkImageView.post(new Runnable() {
                    @Override
                    public void run() {
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) checkImageView.getLayoutParams();
                        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                        int rotation = manager.getDefaultDisplay().getRotation();
                        layoutParams.topMargin = AndroidUtilities.dp(rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90 ? 58 : 68);
                        checkImageView.setLayoutParams(layoutParams);
                    }
                });
            }
        }
    }

    private void onActionClick() {
        if (currentMessageObject == null || currentFileNames[0] == null) {
            return;
        }
        boolean loadFile = false;
        if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
            File f = new File(currentMessageObject.messageOwner.attachPath);
            if (f.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                parentActivity.startActivityForResult(intent, 500);
            } else {
                loadFile = true;
            }
        } else {
            File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            if (cacheFile.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
                parentActivity.startActivityForResult(intent, 500);
            } else {
                loadFile = true;
            }
        }
        if (loadFile) {
            if (!FileLoader.getInstance().isLoadingFile(currentFileNames[0])) {
                FileLoader.getInstance().loadFile(currentMessageObject.getDocument(), true, false);
            } else {
                FileLoader.getInstance().cancelLoadFile(currentMessageObject.getDocument());
            }
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (scale != 1) {
            scroller.abortAnimation();
            scroller.fling(Math.round(translationX), Math.round(translationY), Math.round(velocityX), Math.round(velocityY), (int) minX, (int) maxX, (int) minY, (int) maxY);
            containerView.postInvalidate();
        }
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (discardTap) {
            return false;
        }
        if (canShowBottom) {
            if (radialProgressViews[0] != null && containerView != null) {
                int state = radialProgressViews[0].backgroundState;
                if (state > 0 && state <= 3) {
                    float x = e.getX();
                    float y = e.getY();
                    if (x >= (getContainerViewWidth() - AndroidUtilities.dp(64)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(64)) / 2.0f &&
                            y >= (getContainerViewHeight() - AndroidUtilities.dp(64)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(64)) / 2.0f) {
                        onActionClick();
                        checkProgress(0, true);
                        return true;
                    }
                }
            }
            toggleActionBar(!isActionBarVisible, true);
        } else if (sendPhotoType == 0) {
            checkImageView.performClick();
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!canZoom || scale == 1.0f && (translationY != 0 || translationX != 0)) {
            return false;
        }
        if (animationStartTime != 0 || animationInProgress != 0) {
            return false;
        }
        if (scale == 1.0f) {
            float atx = (e.getX() - getContainerViewWidth() / 2) - ((e.getX() - getContainerViewWidth() / 2) - translationX) * (3.0f / scale);
            float aty = (e.getY() - getContainerViewHeight() / 2) - ((e.getY() - getContainerViewHeight() / 2) - translationY) * (3.0f / scale);
            updateMinMax(3.0f);
            if (atx < minX) {
                atx = minX;
            } else if (atx > maxX) {
                atx = maxX;
            }
            if (aty < minY) {
                aty = minY;
            } else if (aty > maxY) {
                aty = maxY;
            }
            animateTo(3.0f, atx, aty, true);
        } else {
            animateTo(1.0f, 0, 0, true);
        }
        doubleTap = true;
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }
}
