/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.objects.PhotoObject;
import org.telegram.ui.Views.ActionBar.ActionBar;
import org.telegram.ui.Views.ActionBar.ActionBarActivity;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Views.ClippingImageView;
import org.telegram.ui.Views.ImageReceiver;

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
    private ActionBarLayer actionBarLayer;
    private boolean isActionBarVisible = true;

    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayoutDrawer containerView;
    private FrameLayoutTouchListener windowView;
    private ClippingImageView animatingImageView;
    private FrameLayout bottomLayout;
    private TextView nameTextView;
    private TextView dateTextView;
    private ProgressBar progressBar;
    private ActionBarMenuItem menuItem;
    private ColorDrawable backgroundDrawable = new ColorDrawable(0xff000000);
    private OverlayView currentOverlay;
    private ImageView checkImageView;
    private View pickerView;
    private TextView doneButtonTextView;
    private TextView doneButtonBadgeTextView;
    private boolean canShowBottom = true;
    private boolean overlayViewVisible = true;

    private int animationInProgress = 0;
    private boolean disableShowCheck = false;

    private ImageReceiver leftImage = new ImageReceiver();
    private ImageReceiver centerImage = new ImageReceiver();
    private ImageReceiver rightImage = new ImageReceiver();
    private int currentIndex;
    private MessageObject currentMessageObject;
    private TLRPC.FileLocation currentFileLocation;
    private String currentFileName;
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
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();
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

    private ArrayList<MessageObject> imagesArrTemp = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> imagesByIdsTemp = new HashMap<Integer, MessageObject>();
    private ArrayList<MessageObject> imagesArr = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> imagesByIds = new HashMap<Integer, MessageObject>();
    private ArrayList<TLRPC.FileLocation> imagesArrLocations = new ArrayList<TLRPC.FileLocation>();
    private ArrayList<Integer> imagesArrLocationsSizes = new ArrayList<Integer>();
    private ArrayList<MediaController.PhotoEntry> imagesArrLocals = new ArrayList<MediaController.PhotoEntry>();

    private final static int gallery_menu_save = 1;
    private final static int gallery_menu_showall = 2;
    private final static int gallery_menu_send = 3;

    private final static int PAGE_SPACING = Utilities.dp(30);

    private static class OverlayView extends FrameLayout {

        public TextView actionButton;

        public OverlayView(Context context) {
            super(context);

            actionButton = new TextView(context);
            actionButton.setBackgroundResource(R.drawable.system_black);
            actionButton.setPadding(Utilities.dp(8), Utilities.dp(2), Utilities.dp(8), Utilities.dp(2));
            actionButton.setTextColor(0xffffffff);
            actionButton.setTextSize(26);
            actionButton.setGravity(Gravity.CENTER);
            addView(actionButton);
            LayoutParams layoutParams = (LayoutParams)actionButton.getLayoutParams();
            layoutParams.width = LayoutParams.WRAP_CONTENT;
            layoutParams.height = LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            actionButton.setLayoutParams(layoutParams);
            actionButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    getInstance().onActionClick(OverlayView.this);
                }
            });
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

    private static class FrameLayoutTouchListener extends FrameLayout {
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

    private static class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
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
        if (id == FileLoader.FileDidFailedLoad) {
            String location = (String)args[0];
            if (currentFileName != null && currentFileName.equals(location)) {
                progressBar.setVisibility(View.GONE);
                updateActionOverlays();
            }
        } else if (id == FileLoader.FileDidLoaded) {
            String location = (String)args[0];
            if (currentFileName != null && currentFileName.equals(location)) {
                progressBar.setVisibility(View.GONE);
                updateActionOverlays();
            }
        } else if (id == FileLoader.FileLoadProgressChanged) {
            String location = (String)args[0];
            if (currentFileName != null && currentFileName.equals(location)) {
                Float progress = (Float)args[1];
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress((int)(progress * 100));
            }
        } else if (id == MessagesController.userPhotosLoaded) {
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
                for (TLRPC.Photo photo : photos) {
                    if (photo instanceof TLRPC.TL_photoEmpty) {
                        continue;
                    }
                    TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(photo.sizes, 640, 640);
                    if (sizeFull != null) {
                        if (currentFileLocation != null && sizeFull.location.local_id == currentFileLocation.local_id && sizeFull.location.volume_id == currentFileLocation.volume_id) {
                            setToImage = imagesArrLocations.size();
                        }
                        imagesArrLocations.add(sizeFull.location);
                        imagesArrLocationsSizes.add(sizeFull.size);
                    }
                }
                needSearchImageInArr = false;
                currentIndex = -1;
                if (setToImage != -1) {
                    setImageIndex(setToImage, true);
                } else {
                    setImageIndex(0, true);
                }
                if (fromCache) {
                    MessagesController.getInstance().loadUserPhotos(avatarsUserId, 0, 30, 0, false, classGuid);
                }
            }
        } else if (id == MessagesController.mediaCountDidLoaded) {
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
                    actionBarLayer.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount - imagesArr.size()) + currentIndex + 1, totalImagesCount));
                }
            }
        } else if (id == MessagesController.mediaDidLoaded) {
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
        parentActivity = activity;

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
        windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        actionBar = new ActionBar(activity);
        containerView.addView(actionBar);
        actionBar.setBackgroundColor(0x7F000000);
        layoutParams = (FrameLayout.LayoutParams)actionBar.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);
        actionBarLayer = actionBar.createLayer();
        actionBarLayer.setItemsBackground(R.drawable.bar_selector_white);
        actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.photo_back);
        actionBarLayer.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        actionBar.setCurrentActionBarLayer(actionBarLayer);

        actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto(true);
                } else if (id == gallery_menu_save) {
                    if (currentFileName == null) {
                        return;
                    }
                    MediaController.saveFile(currentFileName, null, parentActivity, currentFileName.endsWith("mp4") ? 1 : 0, null);
                } else if (id == gallery_menu_showall) {
                    if (opennedFromMedia) {
                        closePhoto(true);
                    } else if (currentDialogId != 0) {
                        closePhoto(false);
                        Bundle args2 = new Bundle();
                        args2.putLong("dialog_id", currentDialogId);
                        ((ActionBarActivity)parentActivity).presentFragment(new MediaActivity(args2), false, true);
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
                if (currentFileName != null) {
                    File f = new File(Utilities.getCacheDir(), currentFileName);
                    if (f.exists()) {
                        return true;
                    }
                }
                return false;
            }
        });

        ActionBarMenu menu = actionBarLayer.createMenu();
        menuItem = menu.addItem(0, R.drawable.ic_ab_other_white);
        menuItem.addSubItem(gallery_menu_save, LocaleController.getString("SaveToGallery", R.string.SaveToGallery), 0);
        menuItem.addSubItem(gallery_menu_showall, LocaleController.getString("ShowAllMedia", R.string.ShowAllMedia), 0);

        bottomLayout = new FrameLayout(containerView.getContext());
        containerView.addView(bottomLayout);
        layoutParams = (FrameLayout.LayoutParams)bottomLayout.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = Utilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        bottomLayout.setLayoutParams(layoutParams);
        bottomLayout.setBackgroundColor(0x7F000000);

        ImageView shareButton = new ImageView(containerView.getContext());
        shareButton.setImageResource(R.drawable.ic_ab_share_white);
        shareButton.setScaleType(ImageView.ScaleType.CENTER);
        shareButton.setBackgroundResource(R.drawable.bar_selector_white);
        bottomLayout.addView(shareButton);
        layoutParams = (FrameLayout.LayoutParams) shareButton.getLayoutParams();
        layoutParams.width = Utilities.dp(50);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        shareButton.setLayoutParams(layoutParams);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (parentActivity == null) {
                    return;
                }
                try {
                    String fileName = getFileName(currentIndex, null);
                    if (fileName == null) {
                        return;
                    }
                    File f = new File(Utilities.getCacheDir(), fileName);
                    if (f.exists()) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        if (fileName.endsWith("mp4")) {
                            intent.setType("video/mp4");
                        } else {
                            intent.setType("image/jpeg");
                        }
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        parentActivity.startActivity(intent);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });

        ImageView deleteButton = new ImageView(containerView.getContext());
        deleteButton.setImageResource(R.drawable.ic_ab_delete_white);
        deleteButton.setScaleType(ImageView.ScaleType.CENTER);
        deleteButton.setBackgroundResource(R.drawable.bar_selector_white);
        bottomLayout.addView(deleteButton);
        layoutParams = (FrameLayout.LayoutParams) deleteButton.getLayoutParams();
        layoutParams.width = Utilities.dp(50);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.RIGHT;
        deleteButton.setLayoutParams(layoutParams);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                    return;
                }
                MessageObject obj = imagesArr.get(currentIndex);
                if (obj.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENT) {
                    ArrayList<Integer> arr = new ArrayList<Integer>();
                    arr.add(obj.messageOwner.id);
                    MessagesController.getInstance().deleteMessages(arr, null, null);
                    closePhoto(false);
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
        layoutParams.leftMargin = Utilities.dp(60);
        layoutParams.rightMargin = Utilities.dp(60);
        layoutParams.topMargin = Utilities.dp(2);
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
        layoutParams.leftMargin = Utilities.dp(60);
        layoutParams.rightMargin = Utilities.dp(60);
        layoutParams.topMargin = Utilities.dp(26);
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
        layoutParams.height = Utilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM;
        pickerView.setLayoutParams(layoutParams);

        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        doneButtonTextView = (TextView)doneButton.findViewById(R.id.done_button_text);
        doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        doneButtonBadgeTextView = (TextView)doneButton.findViewById(R.id.done_button_badge);

        progressBar = new ProgressBar(containerView.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        progressBar.setMax(100);
        progressBar.setProgressDrawable(parentActivity.getResources().getDrawable(R.drawable.photo_progress));
        containerView.addView(progressBar);
        layoutParams = (FrameLayout.LayoutParams)progressBar.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = Utilities.dp(3);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        layoutParams.leftMargin = Utilities.dp(6);
        layoutParams.rightMargin = Utilities.dp(6);
        layoutParams.bottomMargin = Utilities.dp(48);
        progressBar.setLayoutParams(layoutParams);

        gestureDetector = new GestureDetector(containerView.getContext(), this);
        gestureDetector.setOnDoubleTapListener(this);

        centerImage.parentView = containerView;
        leftImage.parentView = containerView;
        rightImage.parentView = containerView;

        currentOverlay = new OverlayView(containerView.getContext());
        containerView.addView(currentOverlay);
        currentOverlay.setVisibility(View.GONE);

        checkImageView = new ImageView(containerView.getContext());
        containerView.addView(checkImageView);
        checkImageView.setVisibility(View.GONE);
        checkImageView.setScaleType(ImageView.ScaleType.CENTER);
        checkImageView.setImageResource(R.drawable.selectphoto_large);
        layoutParams = (FrameLayout.LayoutParams)checkImageView.getLayoutParams();
        layoutParams.width = Utilities.dp(46);
        layoutParams.height = Utilities.dp(46);
        layoutParams.gravity = Gravity.RIGHT;
        layoutParams.rightMargin = Utilities.dp(10);
        WindowManager manager = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
            layoutParams.topMargin = Utilities.dp(48);
        } else {
            layoutParams.topMargin = Utilities.dp(58);
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

    private void toggleOverlayView(boolean show) {
        if (overlayViewVisible == show) {
            return;
        }
        overlayViewVisible = show;
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(currentOverlay, "alpha", show ? 1.0f : 0.0f)
            );
            animatorSet.setDuration(200);
            animatorSet.start();
        } else {
            AlphaAnimation animation = new AlphaAnimation(show ? 0.0f : 1.0f, show ? 1.0f : 0.0f);
            animation.setDuration(200);
            animation.setFillAfter(true);
            currentOverlay.startAnimation(animation);
        }
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
        actionBarLayer.setEnabled(show);
        bottomLayout.setEnabled(show);
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            if (animated) {
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(bottomLayout, "alpha", show ? 1.0f : 0.0f)
                );
                if (!show) {
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            actionBar.setVisibility(View.GONE);
                            if (canShowBottom) {
                                bottomLayout.setVisibility(View.GONE);
                            }
                        }
                    });
                }

                animatorSet.setDuration(250);
                animatorSet.start();
            } else {
                actionBar.setAlpha(show ? 1.0f : 0.0f);
                bottomLayout.setAlpha(show ? 1.0f : 0.0f);
                if (!show) {
                    actionBar.setVisibility(View.GONE);
                    if (canShowBottom) {
                        bottomLayout.setVisibility(View.GONE);
                    }
                }
            }
        } else {
            if (!show) {
                actionBar.setVisibility(View.GONE);
                if (canShowBottom) {
                    bottomLayout.setVisibility(View.GONE);
                }
            }
        }
    }

    private String getFileName(int index, TLRPC.InputFileLocation fileLocation) {
        if (index < 0) {
            return null;
        }
        TLRPC.InputFileLocation file = fileLocation != null ? fileLocation : getInputFileLocation(index);
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
                    TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(message.messageOwner.action.photo.sizes, 800, 800);
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
                TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(message.messageOwner.media.photo.sizes, 800, 800);
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
                    TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(message.messageOwner.action.photo.sizes, 800, 800);
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
                TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(message.messageOwner.media.photo.sizes, 800, 800);
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

    private void updateActionOverlays() {
        if (currentMessageObject == null || currentFileName == null) {
            currentOverlay.setVisibility(View.GONE);
            return;
        }
        if (currentFileName.endsWith("mp4")) {
            if (currentMessageObject.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENDING && currentMessageObject.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                currentOverlay.setVisibility(View.VISIBLE);
                boolean load = false;
                if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
                    File f = new File(currentMessageObject.messageOwner.attachPath);
                    if (f.exists()) {
                        currentOverlay.actionButton.setText(LocaleController.getString("ViewVideo", R.string.ViewVideo));
                    } else {
                        load = true;
                    }
                } else {
                    File cacheFile = new File(Utilities.getCacheDir(), currentFileName);
                    if (cacheFile.exists()) {
                        currentOverlay.actionButton.setText(LocaleController.getString("ViewVideo", R.string.ViewVideo));
                    } else {
                        load = true;
                    }
                }
                if (load) {
                    Float progress = FileLoader.getInstance().fileProgresses.get(currentFileName);
                    if (FileLoader.getInstance().isLoadingFile(currentFileName)) {
                        currentOverlay.actionButton.setText(LocaleController.getString("CancelDownload", R.string.CancelDownload));
                        progressBar.setVisibility(View.VISIBLE);
                    } else {
                        currentOverlay.actionButton.setText(String.format("%s %s", LocaleController.getString("DOWNLOAD", R.string.DOWNLOAD), Utilities.formatFileSize(currentMessageObject.messageOwner.media.video.size)));
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }
        } else {
            currentOverlay.setVisibility(View.GONE);
        }
    }

    private void onPhotoShow(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<MediaController.PhotoEntry> photos, int index, final PlaceProviderObject object) {
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
        currentMessageObject = null;
        currentFileLocation = null;
        currentPathObject = null;
        currentIndex = -1;
        currentFileName = null;
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
        imagesArrLocals.clear();
        imagesByIds.clear();
        imagesArrTemp.clear();
        imagesByIdsTemp.clear();
        currentThumb = object.thumb;
        menuItem.setVisibility(View.VISIBLE);
        bottomLayout.setVisibility(View.VISIBLE);
        checkImageView.setVisibility(View.GONE);
        pickerView.setVisibility(View.GONE);

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
                        if (messageObject.messageOwner.to_id.user_id == UserConfig.clientUserId) {
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
            bottomLayout.setVisibility(View.GONE);
            canShowBottom = false;
            menuItem.hideSubItem(gallery_menu_showall);
            setImageIndex(0, true);
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
                    if (messageObject.messageOwner.to_id.user_id == UserConfig.clientUserId) {
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
            canShowBottom = false;
            updateSelectedCount();
        }

        if (currentDialogId != 0 && totalImagesCount == 0) {
            MessagesController.getInstance().getMediaCount(currentDialogId, classGuid, true);
        } else if (avatarsUserId != 0) {
            MessagesController.getInstance().loadUserPhotos(avatarsUserId, 0, 30, 0, true, classGuid);
        }
    }

    public void setImageIndex(int index, boolean init) {
        if (currentIndex == index) {
            return;
        }
        if (!init) {
            currentThumb = null;
        }
        placeProvider.willSwitchFromPhoto(currentMessageObject, currentFileLocation, currentIndex);
        int prevIndex = currentIndex;
        currentIndex = index;
        currentFileName = getFileName(index, null);

        if (!imagesArr.isEmpty()) {
            currentMessageObject = imagesArr.get(currentIndex);
            TLRPC.User user = MessagesController.getInstance().users.get(currentMessageObject.messageOwner.from_id);
            if (user != null) {
                nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
            } else {
                nameTextView.setText("");
            }
            dateTextView.setText(LocaleController.formatterYearMax.format(((long) currentMessageObject.messageOwner.date) * 1000));

            if (totalImagesCount != 0 && !needSearchImageInArr) {
                if (imagesArr.size() < totalImagesCount && !loadingMoreImages && currentIndex < 5) {
                    MessageObject lastMessage = imagesArr.get(0);
                    MessagesController.getInstance().loadMedia(currentDialogId, 0, 100, lastMessage.messageOwner.id, !cacheEndReached, classGuid);
                    loadingMoreImages = true;
                }
                actionBarLayer.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount - imagesArr.size()) + currentIndex + 1, totalImagesCount));
            }
            updateActionOverlays();
        } else if (!imagesArrLocations.isEmpty()) {
            currentFileLocation = imagesArrLocations.get(index);
            actionBarLayer.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArrLocations.size()));
        } else if (!imagesArrLocals.isEmpty()) {
            currentPathObject = imagesArrLocals.get(index).path;
            actionBarLayer.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArrLocals.size()));

            if (placeProvider.isPhotoChecked(currentIndex)) {
                checkImageView.setBackgroundColor(0xff42d1f6);
            } else {
                checkImageView.setBackgroundColor(0x801c1c1c);
            }
        }

        if (!init) {
            if (android.os.Build.VERSION.SDK_INT >= 11 && currentPlaceObject != null) {
                currentPlaceObject.imageReceiver.setVisible(true, true);
            }
        }
        currentPlaceObject = placeProvider.getPlaceForPhoto(currentMessageObject, currentFileLocation, currentIndex);
        if (!init) {
            if (android.os.Build.VERSION.SDK_INT >= 11 && currentPlaceObject != null) {
                currentPlaceObject.imageReceiver.setVisible(false, true);
            }
        }

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
        canZoom = currentFileName == null || !currentFileName.endsWith("mp4");
        updateMinMax(scale);

        if (prevIndex == -1) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(rightImage, currentIndex + 1);
            setIndexToImage(leftImage, currentIndex - 1);
        } else {
            if (prevIndex > currentIndex) {
                ImageReceiver temp = rightImage;
                rightImage = centerImage;
                centerImage = leftImage;
                leftImage = temp;
                setIndexToImage(leftImage, currentIndex - 1);
            } else if (prevIndex < currentIndex) {
                ImageReceiver temp = leftImage;
                leftImage = centerImage;
                centerImage = rightImage;
                rightImage = temp;
                setIndexToImage(rightImage, currentIndex + 1);
            }
        }

        if (currentFileName != null) {
            File f = new File(Utilities.getCacheDir(), currentFileName);
            if (f.exists()) {
                progressBar.setVisibility(View.GONE);
            } else {
                if (currentFileName.endsWith("mp4")) {
                    if (!FileLoader.getInstance().isLoadingFile(currentFileName)) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
                Float progress = FileLoader.getInstance().fileProgresses.get(currentFileName);
                if (progress != null) {
                    progressBar.setProgress((int)(progress * 100));
                } else {
                    progressBar.setProgress(0);
                }
            }
        } else {
            progressBar.setVisibility(View.GONE);
        }
        updateActionOverlays();
    }

    private void setIndexToImage(ImageReceiver imageReceiver, int index) {
        if (!imagesArrLocals.isEmpty()) {
            if (index >= 0 && index < imagesArrLocals.size()) {
                MediaController.PhotoEntry photoEntry = imagesArrLocals.get(index);
                Bitmap placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                int size = (int)(800 / Utilities.density);
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
                        imageReceiver.setImage(fileLocation, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, size[0]);
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
                    imageReceiver.setImage(fileLocation, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, size[0]);
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
        return android.os.Build.VERSION.SDK_INT >= 11 && isVisible && !disableShowCheck && object != null && currentMessageObject != null && currentMessageObject.messageOwner.id == object.messageOwner.id;
    }

    public boolean isShowingImage(TLRPC.FileLocation object) {
        return android.os.Build.VERSION.SDK_INT >= 11 && isVisible && !disableShowCheck && object != null && currentFileLocation != null && object.local_id == currentFileLocation.local_id && object.volume_id == currentFileLocation.volume_id && object.dc_id == currentFileLocation.dc_id;
    }

    public boolean isShowingImage(String object) {
        return android.os.Build.VERSION.SDK_INT >= 11 && isVisible && !disableShowCheck && object != null && currentPathObject != null && object.equals(currentPathObject);
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

    public void openPhoto(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<MediaController.PhotoEntry> photos, final int index, final PhotoViewerProvider provider) {
        if (parentActivity == null || isVisible || provider == null || animationInProgress != 0 || messageObject == null && fileLocation == null && messages == null && photos == null) {
            return;
        }
        final PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, fileLocation, index);
        if (object == null) {
            return;
        }

        actionBarLayer.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.mediaDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.userPhotosLoaded);

        placeProvider = provider;
        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        wm.addView(windowView, windowLayoutParams);

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        disableShowCheck = true;
        onPhotoShow(messageObject, fileLocation, messages, photos, index, object);
        isVisible = true;
        backgroundDrawable.setAlpha(255);
        toggleActionBar(true, false);
        overlayViewVisible = true;

        if(android.os.Build.VERSION.SDK_INT >= 11) {
            Utilities.lockOrientation(parentActivity);

            animationInProgress = 1;

            animatingImageView.setVisibility(View.VISIBLE);
            animatingImageView.setImageBitmap(object.thumb);

            animatingImageView.setAlpha(1.0f);
            animatingImageView.setPivotX(0);
            animatingImageView.setPivotY(0);
            animatingImageView.setScaleX(1);
            animatingImageView.setScaleY(1);
            animatingImageView.setTranslationX(object.viewX + object.imageReceiver.drawRegion.left);
            animatingImageView.setTranslationY(object.viewY + object.imageReceiver.drawRegion.top);
            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            layoutParams.width = object.imageReceiver.drawRegion.right - object.imageReceiver.drawRegion.left;
            layoutParams.height = object.imageReceiver.drawRegion.bottom - object.imageReceiver.drawRegion.top;
            animatingImageView.setLayoutParams(layoutParams);

            containerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    containerView.getViewTreeObserver().removeOnPreDrawListener(this);

                    float scaleX = (float) Utilities.displaySize.x / layoutParams.width;
                    float scaleY = (float) (Utilities.displaySize.y - Utilities.statusBarHeight) / layoutParams.height;
                    float scale = scaleX > scaleY ? scaleY : scaleX;
                    float width = layoutParams.width * scale;
                    float height = layoutParams.height * scale;
                    float xPos = (Utilities.displaySize.x - width) / 2.0f;
                    float yPos = (Utilities.displaySize.y - Utilities.statusBarHeight - height) / 2.0f;
                    int clipHorizontal = Math.abs(object.imageReceiver.drawRegion.left - object.imageReceiver.imageX);
                    int clipVertical = Math.abs(object.imageReceiver.drawRegion.top - object.imageReceiver.imageY);

                    int coords2[] = new int[2];
                    object.parentView.getLocationInWindow(coords2);
                    int clipTop = coords2[1] - Utilities.statusBarHeight - (object.viewY + object.imageReceiver.drawRegion.top);
                    if (clipTop < 0) {
                        clipTop = 0;
                    }
                    int clipBottom = (object.viewY + object.imageReceiver.drawRegion.top + layoutParams.height) - (coords2[1] + object.parentView.getHeight() - Utilities.statusBarHeight);
                    if (clipBottom < 0) {
                        clipBottom = 0;
                    }
                    clipTop = Math.max(clipTop, clipVertical);
                    clipBottom = Math.max(clipBottom, clipVertical);

                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(animatingImageView, "scaleX", scale),
                            ObjectAnimator.ofFloat(animatingImageView, "scaleY", scale),
                            ObjectAnimator.ofFloat(animatingImageView, "translationX", xPos),
                            ObjectAnimator.ofFloat(animatingImageView, "translationY", yPos),
                            ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0, 255),
                            ObjectAnimator.ofInt(animatingImageView, "clipHorizontal", clipHorizontal, 0),
                            ObjectAnimator.ofInt(animatingImageView, "clipTop", clipTop, 0),
                            ObjectAnimator.ofInt(animatingImageView, "clipBottom", clipBottom, 0),
                            ObjectAnimator.ofFloat(containerView, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(currentOverlay, "alpha", 1.0f)
                    );

                    animatorSet.setDuration(250);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            animationInProgress = 0;
                            containerView.invalidate();
                            animatingImageView.setVisibility(View.GONE);
                            Utilities.unlockOrientation(parentActivity);
                        }
                    });
                    animatorSet.start();

                    animatingImageView.setOnDrawListener(new ClippingImageView.onDrawListener() {
                        @Override
                        public void onDraw() {
                            disableShowCheck = false;
                            animatingImageView.setOnDrawListener(null);
                            if (android.os.Build.VERSION.SDK_INT >= 11) {
                                object.imageReceiver.setVisible(false, true);
                            }
                        }
                    });
                    return true;
                }
            });
        } else {
            containerView.invalidate();
            AnimationSet animationSet = new AnimationSet(true);
            AlphaAnimation animation = new AlphaAnimation(0.0f, 1.0f);
            animation.setDuration(150);
            animation.setFillAfter(false);
            animationSet.addAnimation(animation);
            ScaleAnimation scaleAnimation = new ScaleAnimation(0.9f, 1.0f, 0.9f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnimation.setDuration(150);
            scaleAnimation.setFillAfter(false);
            animationSet.addAnimation(scaleAnimation);
            animationSet.setDuration(150);
            containerView.startAnimation(animationSet);
            disableShowCheck = false;
        }
    }

    public void closePhoto(boolean animated) {
        if (parentActivity == null || !isVisible || animationInProgress != 0) {
            return;
        }

        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.mediaDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.userPhotosLoaded);
        ConnectionsManager.getInstance().cancelRpcsForClassGuid(classGuid);

        isVisible = false;
        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        ConnectionsManager.getInstance().cancelRpcsForClassGuid(classGuid);

        final PlaceProviderObject object = placeProvider.getPlaceForPhoto(currentMessageObject, currentFileLocation, currentIndex);

        if(android.os.Build.VERSION.SDK_INT >= 11 && animated) {
            Utilities.lockOrientation(parentActivity);

            animationInProgress = 1;
            animatingImageView.setVisibility(View.VISIBLE);
            containerView.invalidate();

            AnimatorSet animatorSet = new AnimatorSet();

            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            if (object != null) {
                layoutParams.width = object.imageReceiver.drawRegion.right - object.imageReceiver.drawRegion.left;
                layoutParams.height = object.imageReceiver.drawRegion.bottom - object.imageReceiver.drawRegion.top;
                animatingImageView.setImageBitmap(object.thumb);
            } else {
                layoutParams.width = centerImage.imageW;
                layoutParams.height = centerImage.imageH;
                animatingImageView.setImageBitmap(centerImage.getBitmap());
            }
            animatingImageView.setLayoutParams(layoutParams);

            float scaleX = (float) Utilities.displaySize.x / layoutParams.width;
            float scaleY = (float) (Utilities.displaySize.y - Utilities.statusBarHeight) / layoutParams.height;
            float scale2 = scaleX > scaleY ? scaleY : scaleX;
            float width = layoutParams.width * scale * scale2;
            float height = layoutParams.height * scale * scale2;
            float xPos = (Utilities.displaySize.x - width) / 2.0f;
            float yPos = (Utilities.displaySize.y - Utilities.statusBarHeight - height) / 2.0f;
            animatingImageView.setTranslationX(xPos + translationX);
            animatingImageView.setTranslationY(yPos + translationY);
            animatingImageView.setScaleX(scale * scale2);
            animatingImageView.setScaleY(scale * scale2);

            if (object != null) {
                if (android.os.Build.VERSION.SDK_INT >= 11) {
                    object.imageReceiver.setVisible(false, true);
                }
                int clipHorizontal = Math.abs(object.imageReceiver.drawRegion.left - object.imageReceiver.imageX);
                int clipVertical = Math.abs(object.imageReceiver.drawRegion.top - object.imageReceiver.imageY);

                int coords2[] = new int[2];
                object.parentView.getLocationInWindow(coords2);
                int clipTop = coords2[1] - Utilities.statusBarHeight - (object.viewY + object.imageReceiver.drawRegion.top);
                if (clipTop < 0) {
                    clipTop = 0;
                }
                int clipBottom = (object.viewY + object.imageReceiver.drawRegion.top + (object.imageReceiver.drawRegion.bottom - object.imageReceiver.drawRegion.top)) - (coords2[1] + object.parentView.getHeight() - Utilities.statusBarHeight);
                if (clipBottom < 0) {
                    clipBottom = 0;
                }

                clipTop = Math.max(clipTop, clipVertical);
                clipBottom = Math.max(clipBottom, clipVertical);

                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(animatingImageView, "scaleX", 1),
                        ObjectAnimator.ofFloat(animatingImageView, "scaleY", 1),
                        ObjectAnimator.ofFloat(animatingImageView, "translationX", object.viewX + object.imageReceiver.drawRegion.left),
                        ObjectAnimator.ofFloat(animatingImageView, "translationY", object.viewY + object.imageReceiver.drawRegion.top),
                        ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofInt(animatingImageView, "clipHorizontal", clipHorizontal),
                        ObjectAnimator.ofInt(animatingImageView, "clipTop", clipTop),
                        ObjectAnimator.ofInt(animatingImageView, "clipBottom", clipBottom),
                        ObjectAnimator.ofFloat(containerView, "alpha", 0.0f)
                );
            } else {
                animatorSet.playTogether(
                        ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofFloat(animatingImageView, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(animatingImageView, "translationY", translationY >= 0 ? Utilities.displaySize.y : -Utilities.displaySize.y),
                        ObjectAnimator.ofFloat(containerView, "alpha", 0.0f)
                );
            }

            animatorSet.setDuration(250);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Utilities.unlockOrientation(parentActivity);
                    animationInProgress = 0;
                    onPhotoClosed(object);
                }
            });
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
            animationSet.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    animationInProgress = 0;
                    onPhotoClosed(object);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            containerView.startAnimation(animationSet);
        }
    }

    private void onPhotoClosed(PlaceProviderObject object) {
        disableShowCheck = true;
        currentMessageObject = null;
        currentFileLocation = null;
        currentPathObject = null;
        currentThumb = null;
        centerImage.setImageBitmap((Bitmap)null);
        leftImage.setImageBitmap((Bitmap) null);
        rightImage.setImageBitmap((Bitmap)null);
        if (android.os.Build.VERSION.SDK_INT >= 11 && object != null) {
            object.imageReceiver.setVisible(true, true);
        }
        containerView.post(new Runnable() {
            @Override
            public void run() {
                animatingImageView.setImageBitmap(null);
                try {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    if (windowView.getParent() != null) {
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
        int maxW = (int) (centerImage.imageW * scale - containerView.getWidth()) / 2;
        int maxH = (int) (centerImage.imageH * scale - containerView.getHeight()) / 2;
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
                Utilities.unlockOrientation(parentActivity);
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
                    Utilities.lockOrientation(parentActivity);
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
                if (canDragDown && !draggingDown && scale == 1 && Math.abs(ev.getY() - dragY) >= Utilities.dp(30)) {
                    draggingDown = true;
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible) {
                        toggleActionBar(false, true);
                    }
                    return true;
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    containerView.invalidate();
                    toggleOverlayView(false);
                } else if (!invalidCoords && animationStartTime == 0) {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || scale == 1 && Math.abs(moveDy) + Utilities.dp(12) < Math.abs(moveDx) || scale != 1) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }

                        toggleOverlayView(false);

                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX && !rightImage.hasImage() || translationX > maxX && !leftImage.hasImage()) {
                            moveDx /= 3.0f;
                        }
                        if (translationY < minY || translationY > maxY) {
                            moveDy /= 3.0f;
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

                if((translationX < minX - containerView.getWidth() / 3 || velocity < -Utilities.dp(650)) && rightImage.hasImage()){
                    goToNext();
                    return true;
                }
                if((translationX > maxX + containerView.getWidth() / 3 || velocity > Utilities.dp(650)) && leftImage.hasImage()){
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
                Utilities.unlockOrientation(parentActivity);
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
            extra = (containerView.getWidth() - centerImage.imageW) / 2 * scale;
        }
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - containerView.getWidth() - extra - PAGE_SPACING / 2, translationY);
    }

    private void goToPrev() {
        float extra = 0;
        if (scale != 1) {
            extra = (containerView.getWidth() - centerImage.imageW) / 2 * scale;
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + containerView.getWidth() + extra + PAGE_SPACING / 2, translationY);
    }

    private void animateTo(float newScale, float newTx, float newTy) {
        animateTo(newScale, newTx, newTy, false);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        if (switchImageAfterAnimation == 0) {
            toggleOverlayView(true);
        }
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            Utilities.unlockOrientation(parentActivity);
            return;
        }
        zoomAnimation = isZoom;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        animationStartTime = System.currentTimeMillis();
        animationDuration = 250;
        containerView.postInvalidate();
        Utilities.lockOrientation(parentActivity);
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
            if (ai >= 0.95) {
                ai = -1;
            }
        }

        if (ai != -1) {
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
                Utilities.unlockOrientation(parentActivity);
                zoomAnimation = false;
            }
            if (switchImageAfterAnimation != 0) {
                if (switchImageAfterAnimation == 1) {
                    setImageIndex(currentIndex + 1, false);
                } else if (switchImageAfterAnimation == 2) {
                    setImageIndex(currentIndex - 1, false);
                }
                switchImageAfterAnimation = 0;
                toggleOverlayView(true);
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

            centerImage.imageX = -width / 2;
            centerImage.imageY = -height / 2;
            centerImage.imageW = width;
            centerImage.imageH = height;
            centerImage.draw(canvas, centerImage.imageX, centerImage.imageY, centerImage.imageW, centerImage.imageH);
        }

        if (scale >= 1.0f) {
            ImageReceiver sideImage = null;
            float k = 1;
            if (currentTranslationX > maxX + Utilities.dp(20)) {
                k = -1;
                sideImage = leftImage;
            } else if (currentTranslationX < minX - Utilities.dp(20)) {
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

                    sideImage.imageX = -width / 2;
                    sideImage.imageY = -height / 2;
                    sideImage.imageW = width;
                    sideImage.imageH = height;
                    sideImage.draw(canvas, sideImage.imageX, sideImage.imageY, sideImage.imageW, sideImage.imageH);
                }
            } else {
                changingPage = false;
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
                            layoutParams.topMargin = Utilities.dp(48);
                        } else {
                            layoutParams.topMargin = Utilities.dp(58);
                        }
                        checkImageView.setLayoutParams(layoutParams);
                        return false;
                    }
                });
            }
        }
    }

    private void onActionClick(View view) {
        if (currentMessageObject == null || currentFileName == null) {
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
            File cacheFile = new File(Utilities.getCacheDir(), currentFileName);
            if (cacheFile.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
                parentActivity.startActivity(intent);
            } else {
                loadFile = true;
            }
        }
        if (loadFile) {
            if (!FileLoader.getInstance().isLoadingFile(currentFileName)) {
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.video, null, null, null);
            } else {
                FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.video, null, null, null);
            }
            updateActionOverlays();
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
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        toggleActionBar(!isActionBarVisible, true);
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!canZoom || scale == 1.0f && (translationY != 0 || translationX != 0)) {
            return false;
        }
        if (animationStartTime != 0) {
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
