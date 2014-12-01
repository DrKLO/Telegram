/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.android.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.android.ImageReceiver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

public class PhotoViewer implements NotificationCenter.NotificationCenterDelegate, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private int classGuid;
    private PhotoViewerProvider placeProvider;
    private boolean isVisible;

    private Activity parentActivity;

    private ActionBar actionBar;
    private boolean isActionBarVisible = true;

    private static Drawable[] progressDrawables = null;

    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayoutDrawer containerView;
    private FrameLayoutTouchListener windowView;
    private ClippingImageView animatingImageView;
    private FrameLayout bottomLayout;
    private TextView nameTextView;
    private TextView dateTextView;
    private ImageView deleteButton;
    private ActionBarMenuItem menuItem;
    private ColorDrawable backgroundDrawable = new ColorDrawable(0xff000000);
    private ImageView checkImageView;
    private View pickerView;
    private TextView doneButtonTextView;
    private TextView doneButtonBadgeTextView;
    private ImageView shareButton;
    private RadialProgressView radialProgressViews[] = new RadialProgressView[3];
    private boolean canShowBottom = true;

    private int animationInProgress = 0;
    private long transitionAnimationStartTime = 0;
    private Runnable animationEndRunnable = null;
    private PlaceProviderObject showAfterAnimation;
    private PlaceProviderObject hideAfterAnimation;
    private boolean disableShowCheck = false;
    private Animation.AnimationListener animationListener;

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
    private int totalImagesCount;
    private boolean isFirstLoading;
    private boolean needSearchImageInArr;
    private boolean loadingMoreImages;
    private boolean cacheEndReached;
    private boolean opennedFromMedia;

    private boolean draggingDown = false;
    private float dragY;
    private float translationX = 0;
    private float translationY = 0;
    private float scale = 1;
    private float animateToX;
    private float animateToY;
    private float animateToScale;
    private long animationDuration;
    private long animationStartTime;
    private GestureDetector gestureDetector;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private float pinchStartDistance = 0;
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
    private int switchImageAfterAnimation = 0;
    private VelocityTracker velocityTracker = null;
    private Scroller scroller = null;

    private ArrayList<MessageObject> imagesArrTemp = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> imagesByIdsTemp = new HashMap<Integer, MessageObject>();
    private ArrayList<MessageObject> imagesArr = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> imagesByIds = new HashMap<Integer, MessageObject>();
    private ArrayList<TLRPC.FileLocation> imagesArrLocations = new ArrayList<TLRPC.FileLocation>();
    private ArrayList<TLRPC.Photo> avatarsArr = new ArrayList<TLRPC.Photo>();
    private ArrayList<Integer> imagesArrLocationsSizes = new ArrayList<Integer>();
    private ArrayList<MediaController.PhotoEntry> imagesArrLocals = new ArrayList<MediaController.PhotoEntry>();
    private TLRPC.FileLocation currentUserAvatarLocation = null;

    private final static int gallery_menu_save = 1;
    private final static int gallery_menu_showall = 2;
    private final static int gallery_menu_send = 3;

    private final static int PAGE_SPACING = AndroidUtilities.dp(30);

    private static class RadialProgressView {

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

        private static DecelerateInterpolator decelerateInterpolator = null;
        private static Paint progressPaint = null;

        public RadialProgressView(Context context, View parentView) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator();
                progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                progressPaint.setStyle(Paint.Style.STROKE);
                progressPaint.setStrokeCap(Paint.Cap.ROUND);
                progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
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

        public void onDraw(Canvas canvas) {
            int x = (canvas.getWidth() - size) / 2;
            int y = (canvas.getHeight() - size) / 2;

            if (previousBackgroundState >= 0 && previousBackgroundState < 4) {
                Drawable drawable = progressDrawables[previousBackgroundState];
                if (drawable != null) {
                    drawable.setAlpha((int)(255 * animatedAlphaValue));
                    drawable.setBounds(x, y, x + size, y + size);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState >= 0 && backgroundState < 4) {
                Drawable drawable = progressDrawables[backgroundState];
                if (drawable != null) {
                    if (previousBackgroundState != -2) {
                        drawable.setAlpha((int)(255 * (1.0f - animatedAlphaValue)));
                    } else {
                        drawable.setAlpha(255);
                    }
                    drawable.setBounds(x, y, x + size, y + size);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState == 0 || backgroundState == 1 || previousBackgroundState == 0 || previousBackgroundState == 1) {
                int diff = AndroidUtilities.dp(1);
                if (previousBackgroundState != -2) {
                    progressPaint.setAlpha((int)(255 * animatedAlphaValue));
                } else {
                    progressPaint.setAlpha(255);
                }
                progressRect.set(x + diff, y + diff, x + size - diff, y + size - diff);
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
    }

    public static interface PhotoViewerProvider {
        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);
        public void willHidePhotoViewer();
        public boolean isPhotoChecked(int index);
        public void setPhotoChecked(int index);
        public void cancelButtonPressed();
        public void sendButtonPressed(int index);
        public int getSelectedCount();
    }

    private class FrameLayoutTouchListener extends FrameLayout {
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
    }

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onAnimationEnd() {
            super.onAnimationEnd();
            if (getInstance().animationListener != null) {
                getInstance().animationListener.onAnimationEnd(null);
            }
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
            String location = (String)args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    radialProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String location = (String)args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    radialProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String location = (String)args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    Float progress = (Float) args[1];
                    radialProgressViews[a].setProgress(progress, true);
                }
            }
        } else if (id == NotificationCenter.userPhotosLoaded) {
            int guid = (Integer)args[4];
            int uid = (Integer)args[0];
            if (avatarsUserId == uid && classGuid == guid) {
                boolean fromCache = (Boolean)args[3];

                int setToImage = -1;
                ArrayList<TLRPC.Photo> photos = (ArrayList<TLRPC.Photo>)args[5];
                if (photos.isEmpty()) {
                    return;
                }
                imagesArrLocations.clear();
                imagesArrLocationsSizes.clear();
                avatarsArr.clear();
                for (TLRPC.Photo photo : photos) {
                    if (photo instanceof TLRPC.TL_photoEmpty || photo.sizes == null) {
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
                    deleteButton.setVisibility(View.VISIBLE);
                } else {
                    deleteButton.setVisibility(View.GONE);
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
            long uid = (Long)args[0];
            if (uid == currentDialogId) {
                if ((int)currentDialogId != 0 && (Boolean)args[2]) {
                    MessagesController.getInstance().getMediaCount(currentDialogId, classGuid, false);
                }
                totalImagesCount = (Integer)args[1];
                if (needSearchImageInArr && isFirstLoading) {
                    isFirstLoading = false;
                    loadingMoreImages = true;
                    MessagesController.getInstance().loadMedia(currentDialogId, 0, 100, 0, true, classGuid);
                } else if (!imagesArr.isEmpty()) {
                    actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount - imagesArr.size()) + currentIndex + 1, totalImagesCount));
                }
            }
        } else if (id == NotificationCenter.mediaDidLoaded) {
            long uid = (Long)args[0];
            int guid = (Integer)args[4];
            if (uid == currentDialogId && guid == classGuid) {
                loadingMoreImages = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[2];
                boolean fromCache = (Boolean)args[3];
                cacheEndReached = !fromCache;
                if (needSearchImageInArr) {
                    if (arr.isEmpty()) {
                        needSearchImageInArr = false;
                        return;
                    }
                    int foundIndex = -1;

                    MessageObject currentMessage = imagesArr.get(currentIndex);

                    int added = 0;
                    for (MessageObject message : arr) {
                        if (!imagesByIdsTemp.containsKey(message.messageOwner.id)) {
                            added++;
                            imagesArrTemp.add(0, message);
                            imagesByIdsTemp.put(message.messageOwner.id, message);
                            if (message.messageOwner.id == currentMessage.messageOwner.id) {
                                foundIndex = arr.size() - added;
                            }
                        }
                    }
                    if (added == 0) {
                        totalImagesCount = imagesArr.size();
                    }

                    if (foundIndex != -1) {
                        imagesArr.clear();
                        imagesArr.addAll(imagesArrTemp);
                        imagesByIds.clear();
                        imagesByIds.putAll(imagesByIdsTemp);
                        imagesArrTemp.clear();
                        imagesByIdsTemp.clear();
                        needSearchImageInArr = false;
                        currentIndex = -1;
                        if (foundIndex >= imagesArr.size()) {
                            foundIndex = imagesArr.size() - 1;
                        }
                        setImageIndex(foundIndex, true);
                    } else {
                        if (!cacheEndReached || !arr.isEmpty() && added != 0) {
                            loadingMoreImages = true;
                            MessagesController.getInstance().loadMedia(currentDialogId, 0, 100, imagesArrTemp.get(0).messageOwner.id, true, classGuid);
                        }
                    }
                } else {
                    int added = 0;
                    for (MessageObject message : arr) {
                        if (!imagesByIds.containsKey(message.messageOwner.id)) {
                            added++;
                            imagesArr.add(0, message);
                            imagesByIds.put(message.messageOwner.id, message);
                        }
                    }
                    if (arr.isEmpty() && !fromCache) {
                        totalImagesCount = arr.size();
                    }
                    if (added != 0) {
                        int index = currentIndex;
                        currentIndex = -1;
                        setImageIndex(index + added, true);
                    } else {
                        totalImagesCount = imagesArr.size();
                    }
                }
            }
        }
    }

    public void setParentActivity(Activity activity) {
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;

        if (progressDrawables == null) {
            progressDrawables = new Drawable[4];
            progressDrawables[0] = parentActivity.getResources().getDrawable(R.drawable.circle_big);
            progressDrawables[1] = parentActivity.getResources().getDrawable(R.drawable.cancel_big);
            progressDrawables[2] = parentActivity.getResources().getDrawable(R.drawable.load_big);
            progressDrawables[3] = parentActivity.getResources().getDrawable(R.drawable.play_big);
        }

        scroller = new Scroller(activity);

        windowView = new FrameLayoutTouchListener(activity);
        windowView.setBackgroundDrawable(backgroundDrawable);
        windowView.setFocusable(false);

        animatingImageView = new ClippingImageView(windowView.getContext());
        windowView.addView(animatingImageView);

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)containerView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        actionBar = new ActionBar(activity);
        actionBar.setBackgroundColor(0x7F000000);
        actionBar.setOccupyStatusBar(false);
        actionBar.setItemsBackground(R.drawable.bar_selector_white);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        containerView.addView(actionBar);
        layoutParams = (FrameLayout.LayoutParams) actionBar.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto(true);
                } else if (id == gallery_menu_save) {
                    File f = null;
                    if (currentMessageObject != null) {
                        f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                    } else if (currentFileLocation != null) {
                        f = FileLoader.getPathToAttach(currentFileLocation, avatarsUserId != 0);
                    }

                    if (f != null && f.exists()) {
                        MediaController.saveFile(f.toString(), parentActivity, currentFileNames[0].endsWith("mp4") ? 1 : 0, null);
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(R.string.OK, null);
                        builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                        builder.show().setCanceledOnTouchOutside(true);
                    }
                } else if (id == gallery_menu_showall) {
                    if (opennedFromMedia) {
                        closePhoto(true);
                    } else if (currentDialogId != 0) {
                        closePhoto(false);
                        Bundle args2 = new Bundle();
                        args2.putLong("dialog_id", currentDialogId);
                        ((LaunchActivity)parentActivity).presentFragment(new MediaActivity(args2), false, true);
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
        menuItem.setNeedOffset(false);
        menuItem.addSubItem(gallery_menu_save, LocaleController.getString("SaveToGallery", R.string.SaveToGallery), 0);
        menuItem.addSubItem(gallery_menu_showall, LocaleController.getString("ShowAllMedia", R.string.ShowAllMedia), 0);

        bottomLayout = new FrameLayout(containerView.getContext());
        containerView.addView(bottomLayout);
        layoutParams = (FrameLayout.LayoutParams)bottomLayout.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        bottomLayout.setLayoutParams(layoutParams);
        bottomLayout.setBackgroundColor(0x7F000000);

        radialProgressViews[0] = new RadialProgressView(containerView.getContext(), containerView);
        radialProgressViews[0].setBackgroundState(0, false);
        radialProgressViews[1] = new RadialProgressView(containerView.getContext(), containerView);
        radialProgressViews[1].setBackgroundState(0, false);
        radialProgressViews[2] = new RadialProgressView(containerView.getContext(), containerView);
        radialProgressViews[2].setBackgroundState(0, false);

        shareButton = new ImageView(containerView.getContext());
        shareButton.setImageResource(R.drawable.ic_ab_share_white);
        shareButton.setScaleType(ImageView.ScaleType.CENTER);
        shareButton.setBackgroundResource(R.drawable.bar_selector_white);
        bottomLayout.addView(shareButton);
        layoutParams = (FrameLayout.LayoutParams) shareButton.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(50);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        shareButton.setLayoutParams(layoutParams);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (parentActivity == null) {
                    return;
                }
                try {
                    File f = null;

                    if (currentMessageObject != null) {
                        f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                    } else if (currentFileLocation != null) {
                        f = FileLoader.getPathToAttach(currentFileLocation, avatarsUserId != 0);
                    }

                    if (f.exists()) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        if (f.toString().endsWith("mp4")) {
                            intent.setType("video/mp4");
                        } else {
                            intent.setType("image/jpeg");
                        }
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        parentActivity.startActivity(intent);
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(R.string.OK, null);
                        builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                        builder.show().setCanceledOnTouchOutside(true);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });

        deleteButton = new ImageView(containerView.getContext());
        deleteButton.setImageResource(R.drawable.ic_ab_delete_white);
        deleteButton.setScaleType(ImageView.ScaleType.CENTER);
        deleteButton.setBackgroundResource(R.drawable.bar_selector_white);
        bottomLayout.addView(deleteButton);
        layoutParams = (FrameLayout.LayoutParams) deleteButton.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(50);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.RIGHT;
        deleteButton.setLayoutParams(layoutParams);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!imagesArr.isEmpty()) {
                    if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                        return;
                    }
                    MessageObject obj = imagesArr.get(currentIndex);
                    if (obj.isSent()) {
                        ArrayList<Integer> arr = new ArrayList<Integer>();
                        arr.add(obj.messageOwner.id);

                        ArrayList<Long> random_ids = null;
                        TLRPC.EncryptedChat encryptedChat = null;
                        if ((int)obj.getDialogId() == 0 && obj.messageOwner.random_id != 0) {
                            random_ids = new ArrayList<Long>();
                            random_ids.add(obj.messageOwner.random_id);
                            encryptedChat = MessagesController.getInstance().getEncryptedChat((int)(obj.getDialogId() >> 32));
                        }

                        MessagesController.getInstance().deleteMessages(arr, random_ids, encryptedChat);
                        closePhoto(false);
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
                        closePhoto(false);
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
                            closePhoto(false);
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

        nameTextView = new TextView(containerView.getContext());
        nameTextView.setTextSize(17);
        nameTextView.setSingleLine(true);
        nameTextView.setMaxLines(1);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setGravity(Gravity.CENTER);
        bottomLayout.addView(nameTextView);
        layoutParams = (FrameLayout.LayoutParams)nameTextView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.TOP;
        layoutParams.leftMargin = AndroidUtilities.dp(60);
        layoutParams.rightMargin = AndroidUtilities.dp(60);
        layoutParams.topMargin = AndroidUtilities.dp(2);
        nameTextView.setLayoutParams(layoutParams);

        dateTextView = new TextView(containerView.getContext());
        dateTextView.setTextSize(14);
        dateTextView.setSingleLine(true);
        dateTextView.setMaxLines(1);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setTextColor(0xffb8bdbe);
        dateTextView.setGravity(Gravity.CENTER);
        bottomLayout.addView(dateTextView);
        layoutParams = (FrameLayout.LayoutParams)dateTextView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.TOP;
        layoutParams.leftMargin = AndroidUtilities.dp(60);
        layoutParams.rightMargin = AndroidUtilities.dp(60);
        layoutParams.topMargin = AndroidUtilities.dp(26);
        dateTextView.setLayoutParams(layoutParams);

        pickerView = parentActivity.getLayoutInflater().inflate(R.layout.photo_picker_bottom_layout, null);
        containerView.addView(pickerView);
        Button cancelButton = (Button)pickerView.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (placeProvider != null) {
                    placeProvider.cancelButtonPressed();
                    closePhoto(false);
                }
            }
        });
        View doneButton = pickerView.findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (placeProvider != null) {
                    placeProvider.sendButtonPressed(currentIndex);
                    closePhoto(false);
                }
            }
        });

        layoutParams = (FrameLayout.LayoutParams)pickerView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM;
        pickerView.setLayoutParams(layoutParams);

        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonTextView = (TextView)doneButton.findViewById(R.id.done_button_text);
        doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonBadgeTextView = (TextView)doneButton.findViewById(R.id.done_button_badge);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        gestureDetector = new GestureDetector(containerView.getContext(), this);
        gestureDetector.setOnDoubleTapListener(this);

        centerImage.setParentView(containerView);
        leftImage.setParentView(containerView);
        rightImage.setParentView(containerView);

        checkImageView = new ImageView(containerView.getContext());
        containerView.addView(checkImageView);
        checkImageView.setVisibility(View.GONE);
        checkImageView.setScaleType(ImageView.ScaleType.CENTER);
        checkImageView.setImageResource(R.drawable.selectphoto_large);
        layoutParams = (FrameLayout.LayoutParams)checkImageView.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(46);
        layoutParams.height = AndroidUtilities.dp(46);
        layoutParams.gravity = Gravity.RIGHT;
        layoutParams.rightMargin = AndroidUtilities.dp(10);
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
            layoutParams.topMargin = AndroidUtilities.dp(48);
        } else {
            layoutParams.topMargin = AndroidUtilities.dp(58);
        }
        checkImageView.setLayoutParams(layoutParams);
        checkImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (placeProvider != null) {
                    placeProvider.setPhotoChecked(currentIndex);
                    if (placeProvider.isPhotoChecked(currentIndex)) {
                        checkImageView.setBackgroundColor(0xff42d1f6);
                    } else {
                        checkImageView.setBackgroundColor(0x801c1c1c);
                    }
                    updateSelectedCount();
                }
            }
        });
    }

    private void toggleActionBar(boolean show, boolean animated) {
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
            if (canShowBottom) {
                bottomLayout.setVisibility(View.VISIBLE);
            }
        }
        isActionBarVisible = show;
        actionBar.setEnabled(show);
        bottomLayout.setEnabled(show);

        if (animated) {
            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            animatorSet.playTogether(
                    ObjectAnimatorProxy.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f),
                    ObjectAnimatorProxy.ofFloat(bottomLayout, "alpha", show ? 1.0f : 0.0f)
            );
            if (!show) {
                animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        actionBar.setVisibility(View.GONE);
                        if (canShowBottom) {
                            bottomLayout.setVisibility(View.GONE);
                        }
                    }
                });
            }

            animatorSet.setDuration(200);
            animatorSet.start();
        } else {
            ViewProxy.setAlpha(actionBar, show ? 1.0f : 0.0f);
            ViewProxy.setAlpha(bottomLayout, show ? 1.0f : 0.0f);
            if (!show) {
                actionBar.setVisibility(View.GONE);
                if (canShowBottom) {
                    bottomLayout.setVisibility(View.GONE);
                }
            }
        }
    }

    private String getFileName(int index) {
        if (index < 0) {
            return null;
        }
        TLRPC.InputFileLocation file = getInputFileLocation(index);
        if (file == null) {
            return null;
        }
        if (!imagesArrLocations.isEmpty()) {
            return file.volume_id + "_" + file.local_id + ".jpg";
        } else if (!imagesArr.isEmpty()) {
            MessageObject message = imagesArr.get(index);
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                return file.volume_id + "_" + file.local_id + ".jpg";
            } else if (message.messageOwner.media != null) {
                if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                    return file.volume_id + "_" + file.id + ".mp4";
                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                    return file.volume_id + "_" + file.local_id + ".jpg";
                }
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
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.messageOwner.action.photo.sizes, AndroidUtilities.getPhotoSize());
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
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo != null) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.messageOwner.media.photo.sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    size[0] = sizeFull.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    return sizeFull.location;
                } else {
                    size[0] = -1;
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo && message.messageOwner.media.video != null && message.messageOwner.media.video.thumb != null) {
                size[0] = message.messageOwner.media.video.thumb.size;
                if (size[0] == 0) {
                    size[0] = -1;
                }
                return message.messageOwner.media.video.thumb.location;
            }
        }
        return null;
    }

    private TLRPC.InputFileLocation getInputFileLocation(int index) {
        if (index < 0) {
            return null;
        }
        if (!imagesArrLocations.isEmpty()) {
            if (index >= imagesArrLocations.size()) {
                return null;
            }
            TLRPC.FileLocation sizeFull = imagesArrLocations.get(index);
            TLRPC.TL_inputFileLocation location = new TLRPC.TL_inputFileLocation();
            location.local_id = sizeFull.local_id;
            location.volume_id = sizeFull.volume_id;
            location.id = sizeFull.dc_id;
            location.secret = sizeFull.secret;
            return location;
        } else if (!imagesArr.isEmpty()) {
            if (index >= imagesArr.size()) {
                return null;
            }
            MessageObject message = imagesArr.get(index);
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    TLRPC.FileLocation sizeFull = message.messageOwner.action.newUserPhoto.photo_big;
                    TLRPC.TL_inputFileLocation location = new TLRPC.TL_inputFileLocation();
                    location.local_id = sizeFull.local_id;
                    location.volume_id = sizeFull.volume_id;
                    location.id = sizeFull.dc_id;
                    location.secret = sizeFull.secret;
                    return location;
                } else {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.messageOwner.action.photo.sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        TLRPC.TL_inputFileLocation location = new TLRPC.TL_inputFileLocation();
                        location.local_id = sizeFull.location.local_id;
                        location.volume_id = sizeFull.location.volume_id;
                        location.id = sizeFull.location.dc_id;
                        location.secret = sizeFull.location.secret;
                        return location;
                    }
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.messageOwner.media.photo.sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    TLRPC.TL_inputFileLocation location = new TLRPC.TL_inputFileLocation();
                    location.local_id = sizeFull.location.local_id;
                    location.volume_id = sizeFull.location.volume_id;
                    location.id = sizeFull.location.dc_id;
                    location.secret = sizeFull.location.secret;
                    return location;
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                TLRPC.TL_inputVideoFileLocation location = new TLRPC.TL_inputVideoFileLocation();
                location.volume_id = message.messageOwner.media.video.dc_id;
                location.id = message.messageOwner.media.video.id;
                return location;
            }
        }
        return null;
    }

    private void updateSelectedCount() {
        if (placeProvider == null) {
            return;
        }
        int count = placeProvider.getSelectedCount();
        if (count == 0) {
            doneButtonTextView.setTextColor(0xffffffff);
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.selectphoto_small, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.GONE);
        } else {
            doneButtonTextView.setTextColor(0xffffffff);
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText("" + count);
        }
    }

    private void onPhotoShow(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<MediaController.PhotoEntry> photos, int index, final PlaceProviderObject object) {
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
        currentMessageObject = null;
        currentFileLocation = null;
        currentPathObject = null;
        currentIndex = -1;
        currentFileNames[0] = null;
        currentFileNames[1] = null;
        currentFileNames[2] = null;
        avatarsUserId = 0;
        currentDialogId = 0;
        totalImagesCount = 0;
        isFirstLoading = true;
        needSearchImageInArr = false;
        loadingMoreImages = false;
        cacheEndReached = false;
        opennedFromMedia = false;
        canShowBottom = true;
        imagesArr.clear();
        imagesArrLocations.clear();
        imagesArrLocationsSizes.clear();
        avatarsArr.clear();
        imagesArrLocals.clear();
        imagesByIds.clear();
        imagesArrTemp.clear();
        imagesByIdsTemp.clear();
        currentUserAvatarLocation = null;
        currentThumb = object.thumb;
        menuItem.setVisibility(View.VISIBLE);
        bottomLayout.setVisibility(View.VISIBLE);
        checkImageView.setVisibility(View.GONE);
        pickerView.setVisibility(View.GONE);
        for (int a = 0; a < 3; a++) {
            if (radialProgressViews[a] != null) {
                radialProgressViews[a].setBackgroundState(-1, false);
            }
        }

        if (messageObject != null && messages == null) {
            imagesArr.add(messageObject);
            if (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) {
                needSearchImageInArr = true;
                imagesByIds.put(messageObject.messageOwner.id, messageObject);
                if (messageObject.messageOwner.dialog_id != 0) {
                    currentDialogId = messageObject.messageOwner.dialog_id;
                } else {
                    if (messageObject.messageOwner.to_id.chat_id != 0) {
                        currentDialogId = -messageObject.messageOwner.to_id.chat_id;
                    } else {
                        if (messageObject.messageOwner.to_id.user_id == UserConfig.getClientUserId()) {
                            currentDialogId = messageObject.messageOwner.from_id;
                        } else {
                            currentDialogId = messageObject.messageOwner.to_id.user_id;
                        }
                    }
                }
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
            bottomLayout.setVisibility(View.GONE);
            shareButton.setVisibility(View.VISIBLE);
            menuItem.hideSubItem(gallery_menu_showall);
            setImageIndex(0, true);
            currentUserAvatarLocation = fileLocation;
        } else if (messages != null) {
            imagesArr.addAll(messages);
            Collections.reverse(imagesArr);
            for (MessageObject message : imagesArr) {
                imagesByIds.put(message.messageOwner.id, message);
            }
            index = imagesArr.size() - index - 1;

            if (messageObject.messageOwner.dialog_id != 0) {
                currentDialogId = messageObject.messageOwner.dialog_id;
            } else {
                if (messageObject.messageOwner.to_id == null) {
                    closePhoto(false);
                    return;
                }
                if (messageObject.messageOwner.to_id.chat_id != 0) {
                    currentDialogId = -messageObject.messageOwner.to_id.chat_id;
                } else {
                    if (messageObject.messageOwner.to_id.user_id == UserConfig.getClientUserId()) {
                        currentDialogId = messageObject.messageOwner.from_id;
                    } else {
                        currentDialogId = messageObject.messageOwner.to_id.user_id;
                    }
                }
            }
            opennedFromMedia = true;
            setImageIndex(index, true);
        } else if (photos != null) {
            checkImageView.setVisibility(View.VISIBLE);
            menuItem.setVisibility(View.GONE);
            imagesArrLocals.addAll(photos);
            setImageIndex(index, true);
            pickerView.setVisibility(View.VISIBLE);
            bottomLayout.setVisibility(View.GONE);
            shareButton.setVisibility(View.VISIBLE);
            canShowBottom = false;
            updateSelectedCount();
        }

        if (currentDialogId != 0 && totalImagesCount == 0) {
            MessagesController.getInstance().getMediaCount(currentDialogId, classGuid, true);
        } else if (avatarsUserId != 0) {
            MessagesController.getInstance().loadUserPhotos(avatarsUserId, 0, 80, 0, true, classGuid);
        }
    }

    public void setImageIndex(int index, boolean init) {
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

        boolean sameImage = false;

        if (!imagesArr.isEmpty()) {
            deleteButton.setVisibility(View.VISIBLE);
            currentMessageObject = imagesArr.get(currentIndex);
            TLRPC.User user = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
            if (user != null) {
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            } else {
                nameTextView.setText("");
            }
            if (currentFileNames[0] != null && currentFileNames[0].endsWith("mp4")) {
                dateTextView.setText(String.format("%s (%s)", LocaleController.formatterYearMax.format(((long) currentMessageObject.messageOwner.date) * 1000), Utilities.formatFileSize(currentMessageObject.messageOwner.media.video.size)));
            } else {
                dateTextView.setText(LocaleController.formatterYearMax.format(((long) currentMessageObject.messageOwner.date) * 1000));
            }

            if (totalImagesCount != 0 && !needSearchImageInArr) {
                if (imagesArr.size() < totalImagesCount && !loadingMoreImages && currentIndex < 5) {
                    MessageObject lastMessage = imagesArr.get(0);
                    MessagesController.getInstance().loadMedia(currentDialogId, 0, 100, lastMessage.messageOwner.id, !cacheEndReached, classGuid);
                    loadingMoreImages = true;
                }
                actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount - imagesArr.size()) + currentIndex + 1, totalImagesCount));
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
                deleteButton.setVisibility(View.VISIBLE);
            } else {
                deleteButton.setVisibility(View.GONE);
            }
            TLRPC.FileLocation old = currentFileLocation;
            currentFileLocation = imagesArrLocations.get(index);
            if (old != null && currentFileLocation != null && old.local_id == currentFileLocation.local_id && old.volume_id == currentFileLocation.volume_id) {
                sameImage = true;
            }
            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArrLocations.size()));
            menuItem.showSubItem(gallery_menu_save);
            shareButton.setVisibility(View.VISIBLE);
        } else if (!imagesArrLocals.isEmpty()) {
            currentPathObject = imagesArrLocals.get(index).path;
            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArrLocals.size()));

            if (placeProvider.isPhotoChecked(currentIndex)) {
                checkImageView.setBackgroundColor(0xff42d1f6);
            } else {
                checkImageView.setBackgroundColor(0x801c1c1c);
            }
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
            animationDuration = 0;
            animationStartTime = 0;

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
            canZoom = currentFileNames[0] != null && !currentFileNames[0].endsWith("mp4") && radialProgressViews[0].backgroundState != 0;
            updateMinMax(scale);
        }

        if (prevIndex == -1) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(rightImage, currentIndex + 1);
            setIndexToImage(leftImage, currentIndex - 1);

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

    private void checkProgress(int a, boolean animated) {
        if (currentFileNames[a] != null) {
            int index = currentIndex;
            if (a == 1) {
                index += 1;
            } else if (a == 2) {
                index -= 1;
            }
            File f = null;
            if (currentMessageObject != null) {
                MessageObject messageObject = imagesArr.get(index);
                f = FileLoader.getPathToMessage(messageObject.messageOwner);
            } else if (currentFileLocation != null) {
                TLRPC.FileLocation location = imagesArrLocations.get(index);
                f = FileLoader.getPathToAttach(location, avatarsUserId != 0);
            }
            if (f != null && f.exists()) {
                if (currentFileNames[a].endsWith("mp4")) {
                    radialProgressViews[a].setBackgroundState(3, animated);
                } else {
                    radialProgressViews[a].setBackgroundState(-1, animated);
                }
            } else {
                if (currentFileNames[a].endsWith("mp4")) {
                    if (!FileLoader.getInstance().isLoadingFile(currentFileNames[a])) {
                        radialProgressViews[a].setBackgroundState(2, false);
                    } else {
                        radialProgressViews[a].setBackgroundState(1, false);
                    }
                } else {
                    radialProgressViews[a].setBackgroundState(0, animated);
                }
                Float progress = FileLoader.getInstance().getFileProgress(currentFileNames[a]);
                if (progress == null) {
                    progress = 0.0f;
                }
                radialProgressViews[a].setProgress(progress, false);
            }
            if (a == 0) {
                canZoom = currentFileNames[0] != null && !currentFileNames[0].endsWith("mp4") && radialProgressViews[0].backgroundState != 0;
            }
        } else {
            radialProgressViews[a].setBackgroundState(-1, animated);
        }
    }

    private void setIndexToImage(ImageReceiver imageReceiver, int index) {
        if (!imagesArrLocals.isEmpty()) {
            if (index >= 0 && index < imagesArrLocals.size()) {
                MediaController.PhotoEntry photoEntry = imagesArrLocals.get(index);
                Bitmap placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                int size = (int)(AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                imageReceiver.setImage(photoEntry.path, String.format(Locale.US, "%d_%d", size, size), placeHolder != null ? new BitmapDrawable(null, placeHolder) : null);
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

                if (messageObject != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                    if (messageObject.imagePreview != null) {
                        imageReceiver.setImageBitmap(messageObject.imagePreview);
                    } else if (messageObject.messageOwner.media.video.thumb != null) {
                        Bitmap placeHolder = null;
                        if (currentThumb != null && imageReceiver == centerImage) {
                            placeHolder = currentThumb;
                        }
                        imageReceiver.setImage(fileLocation, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, 0, true);
                    } else {
                        imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                    }
                } else {
                    Bitmap placeHolder = null;
                    if (messageObject != null) {
                        placeHolder = messageObject.imagePreview;
                    }
                    if (currentThumb != null && imageReceiver == centerImage) {
                        placeHolder = currentThumb;
                    }
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    imageReceiver.setImage(fileLocation, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, size[0], avatarsUserId != 0);
                }
            } else {
                if (size[0] == 0) {
                    imageReceiver.setImageBitmap((Bitmap) null);
                } else {
                    imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                }
            }
        }
    }

    public boolean isShowingImage(MessageObject object) {
        return isVisible && !disableShowCheck && object != null && currentMessageObject != null && currentMessageObject.messageOwner.id == object.messageOwner.id;
    }

    public boolean isShowingImage(TLRPC.FileLocation object) {
        return isVisible && !disableShowCheck && object != null && currentFileLocation != null && object.local_id == currentFileLocation.local_id && object.volume_id == currentFileLocation.volume_id && object.dc_id == currentFileLocation.dc_id;
    }

    public boolean isShowingImage(String object) {
        return isVisible && !disableShowCheck && object != null && currentPathObject != null && object.equals(currentPathObject);
    }

    public void openPhoto(final MessageObject messageObject, final PhotoViewerProvider provider) {
        openPhoto(messageObject, null, null, null, 0, provider);
    }

    public void openPhoto(final TLRPC.FileLocation fileLocation, final PhotoViewerProvider provider) {
        openPhoto(null, fileLocation, null, null, 0, provider);
    }

    public void openPhoto(final ArrayList<MessageObject> messages, final int index, final PhotoViewerProvider provider) {
        openPhoto(messages.get(index), null, messages, null, index, provider);
    }

    public void openPhotoForSelect(final ArrayList<MediaController.PhotoEntry> photos, final int index, final PhotoViewerProvider provider) {
        openPhoto(null, null, null, photos, index, provider);
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

    public void openPhoto(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<MediaController.PhotoEntry> photos, final int index, final PhotoViewerProvider provider) {
        if (parentActivity == null || isVisible || provider == null || checkAnimation() || messageObject == null && fileLocation == null && messages == null && photos == null) {
            return;
        }
        final PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, fileLocation, index);
        if (object == null) {
            return;
        }

        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(windowView);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }


        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        try {
            wm.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return;
        }

        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.userPhotosLoaded);

        placeProvider = provider;

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        disableShowCheck = true;
        animationInProgress = 1;
        onPhotoShow(messageObject, fileLocation, messages, photos, index, object);
        isVisible = true;
        backgroundDrawable.setAlpha(255);
        toggleActionBar(true, false);

        AndroidUtilities.lockOrientation(parentActivity);

        final Rect drawRegion = object.imageReceiver.getDrawRegion();

        animatingImageView.setVisibility(View.VISIBLE);
        animatingImageView.setRadius(object.radius);
        animatingImageView.setNeedRadius(object.radius != 0);
        animatingImageView.setImageBitmap(object.thumb);

        ViewProxy.setAlpha(animatingImageView, 1.0f);
        ViewProxy.setPivotX(animatingImageView, 0.0f);
        ViewProxy.setPivotY(animatingImageView, 0.0f);
        ViewProxy.setScaleX(animatingImageView, 1.0f);
        ViewProxy.setScaleY(animatingImageView, 1.0f);
        ViewProxy.setTranslationX(animatingImageView, object.viewX + drawRegion.left);
        ViewProxy.setTranslationY(animatingImageView, object.viewY + drawRegion.top);
        final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
        layoutParams.width = drawRegion.right - drawRegion.left;
        layoutParams.height = drawRegion.bottom - drawRegion.top;
        animatingImageView.setLayoutParams(layoutParams);

        containerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                containerView.getViewTreeObserver().removeOnPreDrawListener(this);

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
                int clipTop = coords2[1] - AndroidUtilities.statusBarHeight - (object.viewY + drawRegion.top);
                if (clipTop < 0) {
                    clipTop = 0;
                }
                int clipBottom = (object.viewY + drawRegion.top + layoutParams.height) - (coords2[1] + object.parentView.getHeight() - AndroidUtilities.statusBarHeight);
                if (clipBottom < 0) {
                    clipBottom = 0;
                }
                clipTop = Math.max(clipTop, clipVertical);
                clipBottom = Math.max(clipBottom, clipVertical);

                AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "scaleX", scale),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "scaleY", scale),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "translationX", xPos),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "translationY", yPos),
                        ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0, 255),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "clipHorizontal", clipHorizontal, 0),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "clipTop", clipTop, 0),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "clipBottom", clipBottom, 0),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "radius", 0),
                        ObjectAnimatorProxy.ofFloat(containerView, "alpha", 0.0f, 1.0f)
                );

                animationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        animationInProgress = 0;
                        transitionAnimationStartTime = 0;
                        containerView.invalidate();
                        animatingImageView.setVisibility(View.GONE);
                        AndroidUtilities.unlockOrientation(parentActivity);
                        if (showAfterAnimation != null) {
                            showAfterAnimation.imageReceiver.setVisible(true, true);
                        }
                        if (hideAfterAnimation != null) {
                            hideAfterAnimation.imageReceiver.setVisible(false, true);
                        }
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

                    @Override
                    public void onAnimationCancel(Object animation) {
                        onAnimationEnd(animation);
                    }
                });
                transitionAnimationStartTime = System.currentTimeMillis();
                animatorSet.start();

                animatingImageView.setOnDrawListener(new ClippingImageView.onDrawListener() {
                    @Override
                    public void onDraw() {
                        disableShowCheck = false;
                        animatingImageView.setOnDrawListener(null);
                        object.imageReceiver.setVisible(false, true);
                    }
                });
                return true;
            }
        });
    }

    public void closePhoto(boolean animated) {
        if (parentActivity == null || !isVisible || checkAnimation()) {
            return;
        }

        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.userPhotosLoaded);
        ConnectionsManager.getInstance().cancelRpcsForClassGuid(classGuid);

        isVisible = false;
        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        ConnectionsManager.getInstance().cancelRpcsForClassGuid(classGuid);

        final PlaceProviderObject object = placeProvider.getPlaceForPhoto(currentMessageObject, currentFileLocation, currentIndex);

        if(animated) {
            AndroidUtilities.lockOrientation(parentActivity);

            animationInProgress = 1;
            int visibility = animatingImageView.getVisibility();
            animatingImageView.setVisibility(View.VISIBLE);
            containerView.invalidate();

            AnimatorSetProxy animatorSet = new AnimatorSetProxy();

            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            Rect drawRegion = null;
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
                int clipTop = coords2[1] - AndroidUtilities.statusBarHeight - (object.viewY + drawRegion.top);
                if (clipTop < 0) {
                    clipTop = 0;
                }
                int clipBottom = (object.viewY + drawRegion.top + (drawRegion.bottom - drawRegion.top)) - (coords2[1] + object.parentView.getHeight() - AndroidUtilities.statusBarHeight);
                if (clipBottom < 0) {
                    clipBottom = 0;
                }

                clipTop = Math.max(clipTop, clipVertical);
                clipBottom = Math.max(clipBottom, clipVertical);

                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "scaleX", 1),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "scaleY", 1),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "translationX", object.viewX + drawRegion.left),
                        ObjectAnimatorProxy.ofFloat(animatingImageView, "translationY", object.viewY + drawRegion.top),
                        ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "clipHorizontal", clipHorizontal),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "clipTop", clipTop),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "clipBottom", clipBottom),
                        ObjectAnimatorProxy.ofInt(animatingImageView, "radius", object.radius),
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
                    AndroidUtilities.unlockOrientation(parentActivity);
                    animationInProgress = 0;
                    onPhotoClosed(object);
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

                @Override
                public void onAnimationCancel(Object animation) {
                    onAnimationEnd(animation);
                }
            });
            transitionAnimationStartTime = System.currentTimeMillis();
            animatorSet.start();
        } else {
            AnimationSet animationSet = new AnimationSet(true);
            AlphaAnimation animation = new AlphaAnimation(1.0f, 0.0f);
            animation.setDuration(150);
            animation.setFillAfter(false);
            animationSet.addAnimation(animation);
            ScaleAnimation scaleAnimation = new ScaleAnimation(1.0f, 0.9f, 1.0f, 0.9f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnimation.setDuration(150);
            scaleAnimation.setFillAfter(false);
            animationSet.addAnimation(scaleAnimation);
            animationSet.setDuration(150);
            animationInProgress = 2;
            animationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (animationListener != null) {
                        animationInProgress = 0;
                        onPhotoClosed(object);
                        animationListener = null;
                    }
                }
            };
            animationSet.setAnimationListener(animationListener = new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (animationEndRunnable != null) {
                        animationEndRunnable.run();
                        animationEndRunnable = null;
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            transitionAnimationStartTime = System.currentTimeMillis();
            containerView.startAnimation(animationSet);
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
        Instance = null;
    }

    private void onPhotoClosed(PlaceProviderObject object) {
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
        centerImage.setImageBitmap((Bitmap)null);
        leftImage.setImageBitmap((Bitmap)null);
        rightImage.setImageBitmap((Bitmap)null);
        if (object != null) {
            object.imageReceiver.setVisible(true, true);
        }
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
    }

    public boolean isVisible() {
        return isVisible;
    }

    private void updateMinMax(float scale) {
        int maxW = (int) (centerImage.getImageWidth() * scale - containerView.getWidth()) / 2;
        int maxH = (int) (centerImage.getImageHeight() * scale - containerView.getHeight()) / 2;
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
    }

    private boolean onTouchEvent(MotionEvent ev) {
        if (animationInProgress != 0 || animationStartTime != 0) {
            if (animationStartTime == 0) {
                AndroidUtilities.unlockOrientation(parentActivity);
            }
            return false;
        }

        if(ev.getPointerCount() == 1 && gestureDetector.onTouchEvent(ev) && doubleTap) {
            doubleTap = false;
            moving = false;
            zooming = false;
            checkMinMax(false);
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
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
                    AndroidUtilities.lockOrientation(parentActivity);
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (canZoom && ev.getPointerCount() == 2 && !draggingDown && zooming && !changingPage) {
                scale = (float)Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - containerView.getWidth() / 2) - ((pinchCenterX - containerView.getWidth() / 2) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - containerView.getHeight() / 2) - ((pinchCenterY - containerView.getHeight() / 2) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                containerView.invalidate();
            } else if (ev.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - moveStartX);
                float dy = Math.abs(ev.getY() - dragY);
                if (canDragDown && !draggingDown && scale == 1 && dy >= AndroidUtilities.dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible && canShowBottom) {
                        toggleActionBar(false, true);
                    }
                    return true;
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    containerView.invalidate();
                } else if (!invalidCoords && animationStartTime == 0) {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || scale == 1 && Math.abs(moveDy) + AndroidUtilities.dp(12) < Math.abs(moveDx) || scale != 1) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }

                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX && !rightImage.hasImage() || translationX > maxX && !leftImage.hasImage()) {
                            moveDx /= 3.0f;
                        }
                        if (maxY == 0 && minY == 0) {
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
                        if (scale != 1) {
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
            if (zooming) {
                invalidCoords = true;
                if (scale < 1.0f) {
                    updateMinMax(1.0f);
                    animateTo(1.0f, 0, 0, true);
                } else if(scale > 3.0f) {
                    float atx = (pinchCenterX - containerView.getWidth() / 2) - ((pinchCenterX - containerView.getWidth() / 2) - pinchStartX) * (3.0f / pinchStartScale);
                    float aty = (pinchCenterY - containerView.getHeight() / 2) - ((pinchCenterY - containerView.getHeight() / 2) - pinchStartY) * (3.0f / pinchStartScale);
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
                if (Math.abs(dragY - ev.getY()) > containerView.getHeight() / 6.0f) {
                    closePhoto(true);
                } else {
                    animateTo(1, 0, 0);
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

                if((translationX < minX - containerView.getWidth() / 3 || velocity < -AndroidUtilities.dp(650)) && rightImage.hasImage()){
                    goToNext();
                    return true;
                }
                if((translationX > maxX + containerView.getWidth() / 3 || velocity > AndroidUtilities.dp(650)) && leftImage.hasImage()){
                    goToPrev();
                    return true;
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
                animateTo(scale, moveToX, moveToY);
            } else {
                AndroidUtilities.unlockOrientation(parentActivity);
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
            extra = (containerView.getWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - containerView.getWidth() - extra - PAGE_SPACING / 2, translationY);
    }

    private void goToPrev() {
        float extra = 0;
        if (scale != 1) {
            extra = (containerView.getWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + containerView.getWidth() + extra + PAGE_SPACING / 2, translationY);
    }

    private void animateTo(float newScale, float newTx, float newTy) {
        animateTo(newScale, newTx, newTy, false);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            AndroidUtilities.unlockOrientation(parentActivity);
            return;
        }
        zoomAnimation = isZoom;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        animationStartTime = System.currentTimeMillis();
        animationDuration = 250;
        containerView.postInvalidate();
        AndroidUtilities.lockOrientation(parentActivity);
    }

    private void onDraw(Canvas canvas) {
        if (animationInProgress == 1 || !isVisible && animationInProgress != 2) {
            return;
        }

        canvas.save();

        canvas.translate(containerView.getWidth() / 2, containerView.getHeight() / 2);
        float currentTranslationY;
        float currentTranslationX;

        float aty = -1;
        float ai = -1;
        if (System.currentTimeMillis() - animationStartTime < animationDuration) {
            ai = interpolator.getInterpolation((float)(System.currentTimeMillis() - animationStartTime) / animationDuration);
            if (ai >= 0.99f) {
                ai = -1;
            }
        }

        if (ai != -1) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }

            float ts = scale + (animateToScale - scale) * ai;
            float tx = translationX + (animateToX - translationX) * ai;
            float ty = translationY + (animateToY - translationY) * ai;

            if (animateToScale == 1 && scale == 1 && translationX == 0) {
                aty = ty;
            }
            canvas.translate(tx, ty);
            canvas.scale(ts, ts);
            currentTranslationY = ty / ts;
            currentTranslationX = tx;
            containerView.invalidate();
        } else {
            if (animationStartTime != 0) {
                translationX = animateToX;
                translationY = animateToY;
                scale = animateToScale;
                animationStartTime = 0;
                updateMinMax(scale);
                AndroidUtilities.unlockOrientation(parentActivity);
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

            canvas.translate(translationX, translationY);
            canvas.scale(scale, scale);
            currentTranslationY = translationY / scale;
            currentTranslationX = translationX;
            if (!moving) {
                aty = translationY;
            }
        }

        if (scale == 1 && aty != -1) {
            float maxValue = containerView.getHeight() / 4.0f;
            backgroundDrawable.setAlpha((int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(aty), maxValue) / maxValue))));
        } else {
            backgroundDrawable.setAlpha(255);
        }

        Bitmap bitmap = centerImage.getBitmap();
        if (bitmap != null) {
            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            float scaleX = (float) containerView.getWidth() / (float) bitmapWidth;
            float scaleY = (float) containerView.getHeight() / (float) bitmapHeight;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            int width = (int) (bitmapWidth * scale);
            int height = (int) (bitmapHeight * scale);

            centerImage.setImageCoords(-width / 2, -height / 2, width, height);
            centerImage.draw(canvas);
        }

        ImageReceiver sideImage = null;
        if (scale >= 1.0f) {
            float k = 1;
            if (currentTranslationX > maxX + AndroidUtilities.dp(20)) {
                k = -1;
                sideImage = leftImage;
            } else if (currentTranslationX < minX - AndroidUtilities.dp(20)) {
                sideImage = rightImage;
            }

            if (!zoomAnimation && !zooming && sideImage != null) {
                changingPage = true;
                canvas.translate(k * containerView.getWidth() / 2, -currentTranslationY);
                canvas.scale(1.0f / scale, 1.0f / scale);
                canvas.translate(k * (containerView.getWidth() + PAGE_SPACING) / 2, 0);

                bitmap = sideImage.getBitmap();
                if (bitmap != null) {
                    int bitmapWidth = bitmap.getWidth();
                    int bitmapHeight = bitmap.getHeight();

                    float scaleX = (float) containerView.getWidth() / (float) bitmapWidth;
                    float scaleY = (float) containerView.getHeight() / (float) bitmapHeight;
                    float scale = scaleX > scaleY ? scaleY : scaleX;
                    int width = (int) (bitmapWidth * scale);
                    int height = (int) (bitmapHeight * scale);

                    sideImage.setImageCoords(-width / 2, -height / 2, width, height);
                    sideImage.draw(canvas);
                }
            } else {
                changingPage = false;
            }
        }

        canvas.restore();

        canvas.save();
        canvas.translate(currentTranslationX, currentTranslationY);
        radialProgressViews[0].onDraw(canvas);

        if (!zoomAnimation) {
            if (sideImage == rightImage) {
                canvas.translate((canvas.getWidth() * (scale + 1) + PAGE_SPACING) / 2, -currentTranslationY);
                radialProgressViews[1].onDraw(canvas);
            } else if (sideImage == leftImage) {
                canvas.translate(-(canvas.getWidth() * (scale + 1) + PAGE_SPACING) / 2, -currentTranslationY);
                radialProgressViews[2].onDraw(canvas);
            }
        }
        canvas.restore();
    }

    @SuppressLint("DrawAllocation")
    private void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if(changed) {
            scale = 1;
            translationX = 0;
            translationY = 0;
            updateMinMax(scale);

            if (checkImageView != null) {
                checkImageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        checkImageView.getViewTreeObserver().removeOnPreDrawListener(this);
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)checkImageView.getLayoutParams();
                        WindowManager manager = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                        int rotation = manager.getDefaultDisplay().getRotation();
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            layoutParams.topMargin = AndroidUtilities.dp(48);
                        } else {
                            layoutParams.topMargin = AndroidUtilities.dp(58);
                        }
                        checkImageView.setLayoutParams(layoutParams);
                        return false;
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
                parentActivity.startActivity(intent);
            } else {
                loadFile = true;
            }
        } else {
            File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            if (cacheFile.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
                parentActivity.startActivity(intent);
            } else {
                loadFile = true;
            }
        }
        if (loadFile) {
            if (!FileLoader.getInstance().isLoadingFile(currentFileNames[0])) {
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.video, true);
            } else {
                FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.video);
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
        if (canShowBottom) {
            if (radialProgressViews[0] != null && containerView != null) {
                int state = radialProgressViews[0].backgroundState;
                if (state > 0 && state <= 3) {
                    float x = e.getX();
                    float y = e.getY();
                    if (x >= (containerView.getWidth() - AndroidUtilities.dp(64)) / 2.0f && x <= (containerView.getWidth() + AndroidUtilities.dp(64)) / 2.0f &&
                            y >= (containerView.getHeight() - AndroidUtilities.dp(64)) / 2.0f && y <= (containerView.getHeight() + AndroidUtilities.dp(64)) / 2.0f) {
                        onActionClick();
                        checkProgress(0, true);
                        return true;
                    }
                }
            }
            toggleActionBar(!isActionBarVisible, true);
        } else {
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
            float atx = (e.getX() - containerView.getWidth() / 2) - ((e.getX() - containerView.getWidth() / 2) - translationX) * (3.0f / scale);
            float aty = (e.getY() - containerView.getHeight() / 2) - ((e.getY() - containerView.getHeight() / 2) - translationY) * (3.0f / scale);
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
            animateTo(3.0f, atx, aty);
        } else {
            animateTo(1.0f, 0, 0);
        }
        doubleTap = true;
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }
}
