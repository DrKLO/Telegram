/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.SampleSizeBox;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.util.Matrix;
import com.googlecode.mp4parser.util.Path;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BringAppForegroundService;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiSuggestion;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SecureDocument;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.DefaultItemAnimator;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.LinearSmoothScrollerEnd;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.PhotoPickerPhotoCell;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.PhotoCropView;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.PhotoPaintView;
import org.telegram.ui.Components.PhotoViewerCaptionEnterView;
import org.telegram.ui.Components.PickerBottomLayoutViewer;
import org.telegram.ui.Components.PipVideoView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SizeNotifierFrameLayoutPhoto;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanUserMentionPhotoViewer;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.VideoTimelinePlayView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unchecked")
public class PhotoViewer implements NotificationCenter.NotificationCenterDelegate, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private int classGuid;
    private PhotoViewerProvider placeProvider;
    private boolean isVisible;
    private int maxSelectedPhotos = -1;

    private boolean muteVideo;

    private int slideshowMessageId;
    private String nameOverride;
    private int dateOverride;

    private AnimatorSet miniProgressAnimator;
    private Runnable miniProgressShowRunnable = new Runnable() {
        @Override
        public void run() {
            toggleMiniProgressInternal(true);
        }
    };

    private Activity parentActivity;
    private Context actvityContext;

    private ActionBar actionBar;
    private boolean isActionBarVisible = true;
    private boolean isPhotosListViewVisible;
    private AnimatorSet actionBarAnimator;

    private int currentAccount;

    private static Drawable[] progressDrawables;

    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayoutDrawer containerView;
    private FrameLayout windowView;
    private ClippingImageView animatingImageView;
    private FrameLayout bottomLayout;
    private TextView nameTextView;
    private TextView dateTextView;
    private ActionBarMenuItem menuItem;
    private ActionBarMenuItem sendItem;
    private ActionBarMenuItem pipItem;
    private ActionBarMenuItem masksItem;
    private ImageView shareButton;
    private BackgroundDrawable backgroundDrawable = new BackgroundDrawable(0xff000000);
    private Paint blackPaint = new Paint();
    private CheckBox checkImageView;
    private CounterView photosCounterView;
    private FrameLayout pickerView;
    private ImageView pickerViewSendButton;
    private PickerBottomLayoutViewer editorDoneLayout;
    private TextView resetButton;
    private PhotoProgressView photoProgressViews[] = new PhotoProgressView[3];
    private RadialProgressView miniProgressView;
    private ImageView paintItem;
    private ImageView cropItem;
    private ImageView cameraItem;
    private ImageView tuneItem;
    private ImageView timeItem;
    private ImageView muteItem;
    private ImageView compressItem;
    private GroupedPhotosListView groupedPhotosListView;
    private RecyclerListView selectedPhotosListView;
    private ListAdapter selectedPhotosAdapter;
    private AnimatorSet compressItemAnimation;
    private boolean isCurrentVideo;

    private AnimatorSet currentListViewAnimation;
    private PhotoCropView photoCropView;
    private PhotoFilterView photoFilterView;
    private PhotoPaintView photoPaintView;
    private AlertDialog visibleDialog;
    private TextView captionTextView;
    private TextView switchCaptionTextView;
    private AnimatorSet currentCaptionAnimation;
    private ChatAttachAlert parentAlert;
    private PhotoViewerCaptionEnterView captionEditText;
    private int sendPhotoType;
    private boolean needCaptionLayout;
    private AnimatedFileDrawable currentAnimation;
    private boolean allowShare;

    private boolean pipAvailable;

    private TextView hintTextView;
    private Runnable hintHideRunnable;
    private AnimatorSet hintAnimation;

    private Object lastInsets;

    private boolean doneButtonPressed;

    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;
    private VideoPlayer videoPlayer;
    private float seekToProgressPending;
    private boolean streamingAlertShown;
    private long startedPlayTime;
    private boolean keepScreenOnFlagSet;
    private FrameLayout videoPlayerControlFrameLayout;
    private ImageView videoPlayButton;
    private ImageView videoForwardButton;
    private ImageView videoBackwardButton;
    private SimpleTextView videoPlayerTime;
    private SeekBar videoPlayerSeekbar;
    private PipVideoView pipVideoView;
    private int waitingForDraw;
    private TextureView changedTextureView;
    private ImageView textureImageView;
    private int[] pipPosition = new int[2];
    private boolean pipAnimationInProgress;
    private Bitmap currentBitmap;
    private boolean changingTextureView;
    private int waitingForFirstTextureUpload;
    private boolean textureUploaded;
    private boolean isInline;
    private boolean switchingInlineMode;
    private boolean videoCrossfadeStarted;
    private float videoCrossfadeAlpha;
    private long videoCrossfadeAlphaLastTime;
    private boolean isPlaying;
    private boolean isStreaming;
    private boolean firstAnimationDelay;
    private long lastBufferedPositionCheck;

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
            try {
                boolean result = super.onTouchEvent(widget, buffer, event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    private Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer != null) {
                if (isCurrentVideo) {
                    if (!videoTimelineView.isDragging()) {
                        float progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                        if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                            if (progress >= videoTimelineView.getRightProgress()) {
                                videoPlayer.pause();
                                videoTimelineView.setProgress(0);
                                videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                                containerView.invalidate();
                            } else {
                                progress -= videoTimelineView.getLeftProgress();
                                if (progress < 0) {
                                    progress = 0;
                                }
                                progress /= (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress());
                                if (progress > 1) {
                                    progress = 1;
                                }
                                videoTimelineView.setProgress(progress);
                            }
                        } else {
                            videoTimelineView.setProgress(progress);
                        }
                        updateVideoPlayerTime();
                    }
                } else {
                    if (!videoPlayerSeekbar.isDragging()) {
                        float progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                        float bufferedProgress;
                        if (currentVideoFinishedLoading) {
                            bufferedProgress = 1.0f;
                        } else {
                            long newTime = SystemClock.elapsedRealtime();
                            if (Math.abs(newTime - lastBufferedPositionCheck) >= 500) {
                                bufferedProgress = isStreaming ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(seekToProgressPending != 0 ? seekToProgressPending : progress, currentFileNames[0]) : 1.0f;
                                lastBufferedPositionCheck = newTime;
                            } else {
                                bufferedProgress = -1;
                            }
                        }
                        if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                            if (progress >= videoTimelineView.getRightProgress()) {
                                videoPlayer.pause();
                                videoPlayerSeekbar.setProgress(0);
                                videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                                containerView.invalidate();
                            } else {
                                progress -= videoTimelineView.getLeftProgress();
                                if (progress < 0) {
                                    progress = 0;
                                }
                                progress /= (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress());
                                if (progress > 1) {
                                    progress = 1;
                                }
                                videoPlayerSeekbar.setProgress(progress);
                            }
                        } else {
                            if (seekToProgressPending == 0) {
                                videoPlayerSeekbar.setProgress(progress);
                            }
                            if (bufferedProgress != -1) {
                                videoPlayerSeekbar.setBufferedProgress(bufferedProgress);
                                if (pipVideoView != null) {
                                    pipVideoView.setBufferedProgress(bufferedProgress);
                                }
                            }
                        }
                        videoPlayerControlFrameLayout.invalidate();
                        updateVideoPlayerTime();
                    }
                }
            }
            if (isPlaying) {
                AndroidUtilities.runOnUIThread(updateProgressRunnable, 17);
            }
        }
    };

    private Runnable switchToInlineRunnable = new Runnable() {
        @Override
        public void run() {
            switchingInlineMode = false;
            if (currentBitmap != null) {
                currentBitmap.recycle();
                currentBitmap = null;
            }

            changingTextureView = true;
            if (textureImageView != null) {
                try {
                    currentBitmap = Bitmaps.createBitmap(videoTextureView.getWidth(), videoTextureView.getHeight(), Bitmap.Config.ARGB_8888);
                    videoTextureView.getBitmap(currentBitmap);
                } catch (Throwable e) {
                    if (currentBitmap != null) {
                        currentBitmap.recycle();
                        currentBitmap = null;
                    }
                    FileLog.e(e);
                }

                if (currentBitmap != null) {
                    textureImageView.setVisibility(View.VISIBLE);
                    textureImageView.setImageBitmap(currentBitmap);
                } else {
                    textureImageView.setImageDrawable(null);
                }
            }

            isInline = true;

            pipVideoView = new PipVideoView();
            changedTextureView = pipVideoView.show(parentActivity, PhotoViewer.this, aspectRatioFrameLayout.getAspectRatio(), aspectRatioFrameLayout.getVideoRotation());
            changedTextureView.setVisibility(View.INVISIBLE);
            aspectRatioFrameLayout.removeView(videoTextureView);
        }
    };

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (videoTextureView == null) {
                return true;
            }
            if (changingTextureView) {
                if (switchingInlineMode) {
                    waitingForFirstTextureUpload = 2;
                }
                videoTextureView.setSurfaceTexture(surface);
                videoTextureView.setVisibility(View.VISIBLE);
                changingTextureView = false;
                containerView.invalidate();
                return false;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            if (waitingForFirstTextureUpload == 1) {
                changedTextureView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        changedTextureView.getViewTreeObserver().removeOnPreDrawListener(this);
                        if (textureImageView != null) {
                            textureImageView.setVisibility(View.INVISIBLE);
                            textureImageView.setImageDrawable(null);
                            if (currentBitmap != null) {
                                currentBitmap.recycle();
                                currentBitmap = null;
                            }
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isInline) {
                                    dismissInternal();
                                }
                            }
                        });
                        waitingForFirstTextureUpload = 0;
                        return true;
                    }
                });
                changedTextureView.invalidate();
            }
        }
    };

    private float animationValues[][] = new float[2][8];

    private ChatActivity parentChatActivity;
    private MentionsAdapter mentionsAdapter;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private AnimatorSet mentionListAnimation;
    private boolean allowMentions;

    private int animationInProgress;
    private long transitionAnimationStartTime;
    private Runnable animationEndRunnable;
    private PlaceProviderObject showAfterAnimation;
    private PlaceProviderObject hideAfterAnimation;
    private boolean disableShowCheck;

    private String lastTitle;

    private int currentEditMode;

    private ImageReceiver leftImage = new ImageReceiver();
    private ImageReceiver centerImage = new ImageReceiver();
    private ImageReceiver rightImage = new ImageReceiver();
    private int currentIndex;
    private int switchingToIndex;
    private MessageObject currentMessageObject;
    private Uri currentPlayingVideoFile;
    private TLRPC.BotInlineResult currentBotInlineResult;
    private TLRPC.FileLocation currentFileLocation;
    private SecureDocument currentSecureDocument;
    private String currentFileNames[] = new String[3];
    private PlaceProviderObject currentPlaceObject;
    private String currentPathObject;
    private boolean currentVideoFinishedLoading;
    private ImageReceiver.BitmapHolder currentThumb;
    private boolean ignoreDidSetImage;
    boolean fromCamera;

    private int avatarsDialogId;
    private boolean isEvent;
    private long currentDialogId;
    private long mergeDialogId;
    private int totalImagesCount;
    private int totalImagesCountMerge;
    private boolean isFirstLoading;
    private boolean needSearchImageInArr;
    private boolean loadingMoreImages;
    private boolean endReached[] = new boolean[] {false, true};
    private boolean opennedFromMedia;

    private boolean attachedToWindow;

    private boolean wasLayout;
    private boolean dontResetZoomOnFirstLayout;

    private boolean draggingDown;
    private float dragY;
    private float translationX;
    private float translationY;
    private float scale = 1;
    private float animateToX;
    private float animateToY;
    private float animateToScale;
    private float animationValue;
    private boolean applying;
    private long animationStartTime;
    private AnimatorSet imageMoveAnimation;
    private AnimatorSet changeModeAnimation;
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
    private boolean changingPage;
    private boolean zooming;
    private boolean moving;
    private boolean doubleTap;
    private boolean invalidCoords;
    private boolean canDragDown = true;
    private boolean zoomAnimation;
    private boolean discardTap;
    private int switchImageAfterAnimation;
    private VelocityTracker velocityTracker;
    private Scroller scroller;

    private boolean bottomTouchEnabled = true;

    private ArrayList<MessageObject> imagesArrTemp = new ArrayList<>();
    private SparseArray<MessageObject>[] imagesByIdsTemp = new SparseArray[] {new SparseArray<>(), new SparseArray<>()};
    private ArrayList<MessageObject> imagesArr = new ArrayList<>();
    private SparseArray<MessageObject>[] imagesByIds = new SparseArray[] {new SparseArray<>(), new SparseArray<>()};
    private ArrayList<TLRPC.FileLocation> imagesArrLocations = new ArrayList<>();
    private ArrayList<SecureDocument> secureDocuments = new ArrayList<>();
    private ArrayList<TLRPC.Photo> avatarsArr = new ArrayList<>();
    private ArrayList<Integer> imagesArrLocationsSizes = new ArrayList<>();
    private ArrayList<Object> imagesArrLocals = new ArrayList<>();
    private TLRPC.FileLocation currentUserAvatarLocation = null;

    private final static int gallery_menu_save = 1;
    private final static int gallery_menu_showall = 2;
    private final static int gallery_menu_send = 3;
    private final static int gallery_menu_showinchat = 4;
    private final static int gallery_menu_pip = 5;
    private final static int gallery_menu_delete = 6;
    private final static int gallery_menu_cancel_loading = 7;
    private final static int gallery_menu_share = 10;
    private final static int gallery_menu_openin = 11;
    private final static int gallery_menu_masks = 13;

    private static DecelerateInterpolator decelerateInterpolator;
    private static Paint progressPaint;

    private class GroupedPhotosListView extends View implements GestureDetector.OnGestureListener {

        private Paint backgroundPaint = new Paint();
        private ArrayList<ImageReceiver> unusedReceivers = new ArrayList<>();
        private ArrayList<ImageReceiver> imagesToDraw = new ArrayList<>();
        private ArrayList<TLObject> currentPhotos = new ArrayList<>();
        private ArrayList<Object> currentObjects = new ArrayList<>();
        private int currentImage;
        private long currentGroupId;
        private int itemWidth;
        private int itemHeight;
        private int itemY;
        private int itemSpacing;
        private int drawDx;
        private float moveLineProgress;
        private float currentItemProgress = 1.0f;
        private float nextItemProgress = 0.0f;
        private int nextImage;
        private long lastUpdateTime;
        private boolean moving;
        private boolean animateAllLine;
        private int animateToDX;
        private int animateToDXStart;
        private int animateToItem = -1;
        private Scroller scroll;
        private GestureDetector gestureDetector;
        private boolean scrolling;
        private boolean stopedScrolling;
        private boolean ignoreChanges;
        private int nextPhotoScrolling = -1;

        public GroupedPhotosListView(Context context) {
            super(context);
            gestureDetector = new GestureDetector(context, this);
            scroll = new Scroller(context);
            itemWidth = AndroidUtilities.dp(42);
            itemHeight = AndroidUtilities.dp(56);
            itemSpacing = AndroidUtilities.dp(1);
            itemY = AndroidUtilities.dp(3);
            backgroundPaint.setColor(0x7f000000);
        }

        public void clear() {
            currentPhotos.clear();
            currentObjects.clear();
            imagesToDraw.clear();
        }

        public void fillList() {
            if (ignoreChanges) {
                ignoreChanges = false;
                return;
            }
            boolean changed = false;
            int newCount = 0;
            Object currentObject = null;
            if (!imagesArrLocations.isEmpty()) {
                TLRPC.FileLocation location = imagesArrLocations.get(currentIndex);
                newCount = imagesArrLocations.size();
                currentObject = location;
            } else if (!imagesArr.isEmpty()) {
                MessageObject messageObject = imagesArr.get(currentIndex);
                currentObject = messageObject;
                if (messageObject.getGroupIdForUse() != currentGroupId) {
                    changed = true;
                    currentGroupId = messageObject.getGroupIdForUse();
                } else {
                    int max = Math.min(currentIndex + 10, imagesArr.size());
                    for (int a = currentIndex; a < max; a++) {
                        MessageObject object = imagesArr.get(a);
                        if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                            newCount++;
                        } else {
                            break;
                        }
                    }
                    int min = Math.max(currentIndex - 10, 0);
                    for (int a = currentIndex - 1; a >= min; a--) {
                        MessageObject object = imagesArr.get(a);
                        if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                            newCount++;
                        } else {
                            break;
                        }
                    }
                }
            }
            if (currentObject == null) {
                return;
            }
            if (!changed) {
                if (newCount != currentPhotos.size() || currentObjects.indexOf(currentObject) == -1) {
                    changed = true;
                } else {
                    int newImageIndex = currentObjects.indexOf(currentObject);
                    if (currentImage != newImageIndex && newImageIndex != -1) {
                        if (animateAllLine) {
                            nextImage = animateToItem = newImageIndex;
                            animateToDX = (currentImage - newImageIndex) * (itemWidth + itemSpacing);
                            moving = true;
                            animateAllLine = false;
                            lastUpdateTime = System.currentTimeMillis();
                            invalidate();
                        } else {
                            fillImages(true, (currentImage - newImageIndex) * (itemWidth + itemSpacing));
                            currentImage = newImageIndex;
                            moving = false;
                        }
                        drawDx = 0;
                    }
                }
            }
            if (changed) {
                animateAllLine = false;
                currentPhotos.clear();
                currentObjects.clear();
                if (!imagesArrLocations.isEmpty()) {
                    currentObjects.addAll(imagesArrLocations);
                    currentPhotos.addAll(imagesArrLocations);
                    currentImage = currentIndex;
                    animateToItem = -1;
                } else if (!imagesArr.isEmpty()) {
                    if (currentGroupId != 0 || slideshowMessageId != 0) {
                        int max = Math.min(currentIndex + 10, imagesArr.size());
                        for (int a = currentIndex; a < max; a++) {
                            MessageObject object = imagesArr.get(a);
                            if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                                currentObjects.add(object);
                                currentPhotos.add(FileLoader.getClosestPhotoSizeWithSize(object.photoThumbs, 56, true));
                            } else {
                                break;
                            }
                        }
                        currentImage = 0;
                        animateToItem = -1;
                        int min = Math.max(currentIndex - 10, 0);
                        for (int a = currentIndex - 1; a >= min; a--) {
                            MessageObject object = imagesArr.get(a);
                            if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                                currentObjects.add(0, object);
                                currentPhotos.add(0, FileLoader.getClosestPhotoSizeWithSize(object.photoThumbs, 56, true));
                                currentImage++;
                            } else {
                                break;
                            }
                        }
                    }
                }
                if (currentPhotos.size() == 1) {
                    currentPhotos.clear();
                    currentObjects.clear();
                }
                fillImages(false, 0);
            }
        }

        public void setMoveProgress(float progress) {
            if (scrolling || animateToItem >= 0) {
                return;
            }
            if (progress > 0) {
                nextImage = currentImage - 1;
            } else {
                nextImage = currentImage + 1;
            }
            if (nextImage >= 0 && nextImage < currentPhotos.size()) {
                currentItemProgress = 1.0f - Math.abs(progress);
            } else {
                currentItemProgress = 1.0f;
            }
            nextItemProgress = 1.0f - currentItemProgress;
            moving = progress != 0;
            invalidate();
            if (currentPhotos.isEmpty() || progress < 0 && currentImage == currentPhotos.size() - 1 || progress > 0 && currentImage == 0) {
                return;
            }
            drawDx = (int) (progress * (itemWidth + itemSpacing));
            fillImages(true, drawDx);
        }

        private ImageReceiver getFreeReceiver() {
            ImageReceiver receiver;
            if (unusedReceivers.isEmpty()) {
                receiver = new ImageReceiver(this);
            } else {
                receiver = unusedReceivers.get(0);
                unusedReceivers.remove(0);
            }
            imagesToDraw.add(receiver);
            receiver.setCurrentAccount(currentAccount);
            return receiver;
        }

        private void fillImages(boolean move, int dx) {
            if (!move && !imagesToDraw.isEmpty()) {
                unusedReceivers.addAll(imagesToDraw);
                imagesToDraw.clear();
                moving = false;
                moveLineProgress = 1.0f;
                currentItemProgress = 1.0f;
                nextItemProgress = 0.0f;
            }
            invalidate();
            if (getMeasuredWidth() == 0 || currentPhotos.isEmpty()) {
                return;
            }
            int width = getMeasuredWidth();
            int startX = getMeasuredWidth() / 2 - itemWidth / 2;

            int addRightIndex;
            int addLeftIndex;
            if (move) {
                addRightIndex = Integer.MIN_VALUE;
                addLeftIndex = Integer.MAX_VALUE;
                int count = imagesToDraw.size();
                for (int a = 0; a < count; a++) {
                    ImageReceiver receiver = imagesToDraw.get(a);
                    int num = receiver.getParam();
                    int x = startX + (num - currentImage) * (itemWidth + itemSpacing) + dx;
                    if (x > width || x + itemWidth < 0) {
                        unusedReceivers.add(receiver);
                        imagesToDraw.remove(a);
                        count--;
                        a--;
                    }
                    addLeftIndex = Math.min(addLeftIndex, num - 1);
                    addRightIndex = Math.max(addRightIndex, num + 1);
                }
            } else {
                addRightIndex = currentImage;
                addLeftIndex = currentImage - 1;
            }

            if (addRightIndex != Integer.MIN_VALUE) {
                int count = currentPhotos.size();
                for (int a = addRightIndex; a < count; a++) {
                    int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx;
                    if (x < width) {
                        TLObject location = currentPhotos.get(a);
                        if (location instanceof TLRPC.PhotoSize) {
                            location = ((TLRPC.PhotoSize) location).location;
                        }
                        ImageReceiver receiver = getFreeReceiver();
                        receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                        receiver.setImage(null, null, null, null, (TLRPC.FileLocation) location, "80_80", 0, null, 1);
                        receiver.setParam(a);
                    } else {
                        break;
                    }
                }
            }
            if (addLeftIndex != Integer.MAX_VALUE) {
                for (int a = addLeftIndex; a >= 0; a--) {
                    int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx + itemWidth;
                    if (x > 0) {
                        TLObject location = currentPhotos.get(a);
                        if (location instanceof TLRPC.PhotoSize) {
                            location = ((TLRPC.PhotoSize) location).location;
                        }
                        ImageReceiver receiver = getFreeReceiver();
                        receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                        receiver.setImage(null, null, null, null, (TLRPC.FileLocation) location, "80_80", 0, null, 1);
                        receiver.setParam(a);
                    } else {
                        break;
                    }
                }
            }
        }

        @Override
        public boolean onDown(MotionEvent e) {
            if (!scroll.isFinished()) {
                scroll.abortAnimation();
            }
            animateToItem = -1;
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            stopScrolling();
            int count = imagesToDraw.size();
            for (int a = 0; a < count; a++) {
                ImageReceiver receiver = imagesToDraw.get(a);
                if (receiver.isInsideImage(e.getX(), e.getY())) {
                    int num = receiver.getParam();
                    if (num < 0 || num >= currentObjects.size()) {
                        return true;
                    }
                    if (!imagesArr.isEmpty()) {
                        MessageObject messageObject = (MessageObject) currentObjects.get(num);
                        int idx = imagesArr.indexOf(messageObject);
                        if (currentIndex == idx) {
                            return true;
                        }
                        moveLineProgress = 1.0f;
                        animateAllLine = true;
                        currentIndex = -1;
                        if (currentThumb != null) {
                            currentThumb.release();
                            currentThumb = null;
                        }
                        setImageIndex(idx, true);
                    } else if (!imagesArrLocations.isEmpty()) {
                        TLRPC.FileLocation location = (TLRPC.FileLocation) currentObjects.get(num);
                        int idx = imagesArrLocations.indexOf(location);
                        if (currentIndex == idx) {
                            return true;
                        }
                        moveLineProgress = 1.0f;
                        animateAllLine = true;
                        currentIndex = -1;
                        if (currentThumb != null) {
                            currentThumb.release();
                            currentThumb = null;
                        }
                        setImageIndex(idx, true);
                    }
                    break;
                }
            }
            return false;
        }

        private void updateAfterScroll() {
            int indexChange = 0;
            int dx = drawDx;
            if (Math.abs(dx) > itemWidth / 2 + itemSpacing) {
                if (dx > 0) {
                    dx -= itemWidth / 2 + itemSpacing;
                    indexChange++;
                } else {
                    dx += itemWidth / 2 + itemSpacing;
                    indexChange--;
                }
                indexChange += dx / (itemWidth + itemSpacing * 2);
            }
            nextPhotoScrolling = currentImage - indexChange;
            if (currentIndex != nextPhotoScrolling && nextPhotoScrolling >= 0 && nextPhotoScrolling < currentPhotos.size()) {
                Object photo = currentObjects.get(nextPhotoScrolling);
                int nextPhoto = -1;
                if (!imagesArr.isEmpty()) {
                    MessageObject messageObject = (MessageObject) photo;
                    nextPhoto = imagesArr.indexOf(messageObject);
                } else if (!imagesArrLocations.isEmpty()) {
                    TLRPC.FileLocation location = (TLRPC.FileLocation) photo;
                    nextPhoto = imagesArrLocations.indexOf(location);
                }
                if (nextPhoto >= 0) {
                    ignoreChanges = true;
                    currentIndex = -1;
                    if (currentThumb != null) {
                        currentThumb.release();
                        currentThumb = null;
                    }
                    setImageIndex(nextPhoto, true);
                }
            }
            if (!scrolling) {
                scrolling = true;
                stopedScrolling = false;
            }
            fillImages(true, drawDx);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            drawDx -= distanceX;
            int min = getMinScrollX();
            int max = getMaxScrollX();
            if (drawDx < min) {
                drawDx = min;
            } else if (drawDx > max) {
                drawDx = max;
            }
            updateAfterScroll();
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            scroll.abortAnimation();
            if (currentPhotos.size() >= 10) {
                scroll.fling(drawDx, 0, Math.round(velocityX), 0, getMinScrollX(), getMaxScrollX(), 0, 0);
            }
            return false;
        }

        private void stopScrolling() {
            scrolling = false;
            if (!scroll.isFinished()) {
                scroll.abortAnimation();
            }
            if (nextPhotoScrolling >= 0 && nextPhotoScrolling < currentObjects.size()) {
                stopedScrolling = true;

                nextImage = animateToItem = nextPhotoScrolling;
                animateToDX = (currentImage - nextPhotoScrolling) * (itemWidth + itemSpacing);
                animateToDXStart = drawDx;
                moveLineProgress = 1.0f;
                nextPhotoScrolling = -1;
            }
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (currentPhotos.isEmpty() || getAlpha() != 1.0f) {
                return false;
            }
            boolean result = gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
            if (scrolling && event.getAction() == MotionEvent.ACTION_UP && scroll.isFinished()) {
                stopScrolling();
            }
            return result;
        }

        private int getMinScrollX() {
            return -(currentPhotos.size() - currentImage - 1) * (itemWidth + itemSpacing * 2);
        }

        private int getMaxScrollX() {
            return currentImage * (itemWidth + itemSpacing * 2);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            fillImages(false, 0);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (imagesToDraw.isEmpty()) {
                return;
            }
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            int count = imagesToDraw.size();

            int moveX = drawDx;

            int maxItemWidth = (int) (itemWidth * 2.0f);
            int padding = AndroidUtilities.dp(8);

            TLObject object = currentPhotos.get(currentImage);
            int trueWidth;
            int currentPaddings;
            if (object instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) object;
                trueWidth = Math.max(itemWidth, (int) (photoSize.w * (itemHeight / (float) photoSize.h)));
            } else {
                trueWidth = itemHeight;
            }
            trueWidth = Math.min(maxItemWidth, trueWidth);
            currentPaddings = (int) (padding * 2 * currentItemProgress);
            trueWidth = itemWidth + (int) ((trueWidth - itemWidth) * currentItemProgress) + currentPaddings;

            int nextTrueWidth;
            int nextPaddings;
            if (nextImage >= 0 && nextImage < currentPhotos.size()) {
                object = currentPhotos.get(nextImage);
                if (object instanceof TLRPC.PhotoSize) {
                    TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) object;
                    nextTrueWidth = Math.max(itemWidth, (int) (photoSize.w * (itemHeight / (float) photoSize.h)));
                } else {
                    nextTrueWidth = itemHeight;
                }
            } else {
                nextTrueWidth = itemWidth;
            }
            nextTrueWidth = Math.min(maxItemWidth, nextTrueWidth);
            nextPaddings = (int) (padding * 2 * nextItemProgress);
            moveX += (nextTrueWidth + nextPaddings - itemWidth) / 2 * nextItemProgress * (nextImage > currentImage ? -1 : 1);
            nextTrueWidth = itemWidth + (int) ((nextTrueWidth - itemWidth) * nextItemProgress) + nextPaddings;

            int startX = (getMeasuredWidth() - trueWidth) / 2;
            for (int a = 0; a < count; a++) {
                ImageReceiver receiver = imagesToDraw.get(a);
                int num = receiver.getParam();
                if (num == currentImage) {
                    receiver.setImageX(startX + moveX + currentPaddings / 2);
                    receiver.setImageWidth(trueWidth - currentPaddings);
                } else {
                    if (nextImage < currentImage) {
                        if (num < currentImage) {
                            if (num <= nextImage) {
                                receiver.setImageX(startX + (receiver.getParam() - currentImage + 1) * (itemWidth + itemSpacing) - (nextTrueWidth + itemSpacing) + moveX);
                            } else {
                                receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                            }
                        } else {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                        }
                    } else {
                        if (num < currentImage) {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                        } else {
                            if (num <= nextImage) {
                                receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                            } else {
                                receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 2) * (itemWidth + itemSpacing) + (nextTrueWidth + itemSpacing) + moveX);
                            }
                        }
                    }
                    if (num == nextImage) {
                        receiver.setImageWidth(nextTrueWidth - nextPaddings);
                        receiver.setImageX(receiver.getImageX() + nextPaddings / 2);
                    } else {
                        receiver.setImageWidth(itemWidth);
                    }
                }
                receiver.draw(canvas);
            }

            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt > 17) {
                dt = 17;
            }
            lastUpdateTime = newTime;
            if (animateToItem >= 0) {
                if (moveLineProgress > 0.0f) {
                    moveLineProgress -= dt / 200.0f;
                    if (animateToItem == currentImage) {
                        if (currentItemProgress < 1.0f) {
                            currentItemProgress += dt / 200.0f;
                            if (currentItemProgress > 1.0f) {
                                currentItemProgress = 1.0f;
                            }
                        }
                        drawDx = animateToDXStart + (int) Math.ceil(currentItemProgress * (animateToDX - animateToDXStart));
                    } else {
                        nextItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f - moveLineProgress);
                        if (stopedScrolling) {
                            if (currentItemProgress > 0.0f) {
                                currentItemProgress -= dt / 200.0f;
                                if (currentItemProgress < 0.0f) {
                                    currentItemProgress = 0.0f;
                                }
                            }
                            drawDx = animateToDXStart + (int) Math.ceil(nextItemProgress * (animateToDX - animateToDXStart));
                        } else {
                            currentItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(moveLineProgress);
                            drawDx = (int) Math.ceil(nextItemProgress * animateToDX);
                        }
                    }
                    if (moveLineProgress <= 0) {
                        currentImage = animateToItem;
                        moveLineProgress = 1.0f;
                        currentItemProgress = 1.0f;
                        nextItemProgress = 0.0f;
                        moving = false;
                        stopedScrolling = false;
                        drawDx = 0;
                        animateToItem = -1;
                    }
                }
                fillImages(true, drawDx);
                invalidate();
            }
            if (scrolling && currentItemProgress > 0.0f) {
                currentItemProgress -= dt / 200.0f;
                if (currentItemProgress < 0.0f) {
                    currentItemProgress = 0.0f;
                }
                invalidate();
            }
            if (!scroll.isFinished()) {
                if (scroll.computeScrollOffset()) {
                    drawDx = scroll.getCurrX();
                    updateAfterScroll();
                    invalidate();
                }
                if (scroll.isFinished()) {
                    stopScrolling();
                }
            }
        }
    }

    private class BackgroundDrawable extends ColorDrawable {

        private Runnable drawRunnable;
        private boolean allowDrawContent;

        public BackgroundDrawable(int color) {
            super(color);
        }

        @Keep
        @Override
        public void setAlpha(int alpha) {
            if (parentActivity instanceof LaunchActivity) {
                allowDrawContent = !isVisible || alpha != 255;
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(allowDrawContent);
                if (parentAlert != null) {
                    if (!allowDrawContent) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (parentAlert != null) {
                                    parentAlert.setAllowDrawContent(allowDrawContent);
                                }
                            }
                        }, 50);
                    } else {
                        if (parentAlert != null) {
                            parentAlert.setAllowDrawContent(allowDrawContent);
                        }
                    }
                }
            }
            super.setAlpha(alpha);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (getAlpha() != 0) {
                if (drawRunnable != null) {
                    AndroidUtilities.runOnUIThread(drawRunnable);
                    drawRunnable = null;
                }
            }
        }
    }

    private class CounterView extends View {

        private StaticLayout staticLayout;
        private TextPaint textPaint;
        private Paint paint;
        private int width;
        private int height;
        private RectF rect;
        private int currentCount = 0;
        private float rotation;

        public CounterView(Context context) {
            super(context);
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(18));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textPaint.setColor(0xffffffff);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xffffffff);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);

            rect = new RectF();

            setCount(0);
        }

        @Keep
        @Override
        public void setScaleX(float scaleX) {
            super.setScaleX(scaleX);
            invalidate();
        }

        @Keep
        @Override
        public void setRotationX(float rotationX) {
            rotation = rotationX;
            invalidate();
        }

        @Override
        public float getRotationX() {
            return rotation;
        }

        public void setCount(int value) {
            staticLayout = new StaticLayout("" + Math.max(1, value), textPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            width = (int) Math.ceil(staticLayout.getLineWidth(0));
            height = staticLayout.getLineBottom(0);
            AnimatorSet animatorSet = new AnimatorSet();
            if (value == 0) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, "scaleX", 0.0f),
                        ObjectAnimator.ofFloat(this, "scaleY", 0.0f),
                        ObjectAnimator.ofInt(paint, "alpha", 0),
                        ObjectAnimator.ofInt(textPaint, "alpha", 0));
                animatorSet.setInterpolator(new DecelerateInterpolator());
            } else if (currentCount == 0) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, "scaleX", 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(this, "scaleY", 0.0f, 1.0f),
                        ObjectAnimator.ofInt(paint, "alpha", 0, 255),
                        ObjectAnimator.ofInt(textPaint, "alpha", 0, 255));
                animatorSet.setInterpolator(new DecelerateInterpolator());
            } else if (value < currentCount) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, "scaleX", 1.1f, 1.0f),
                        ObjectAnimator.ofFloat(this, "scaleY", 1.1f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
            } else {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, "scaleX", 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(this, "scaleY", 0.9f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
            }

            animatorSet.setDuration(180);
            animatorSet.start();
            requestLayout();
            currentCount = value;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(width + AndroidUtilities.dp(20), AndroidUtilities.dp(30)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cy = getMeasuredHeight() / 2;
            paint.setAlpha(255);
            rect.set(AndroidUtilities.dp(1), cy - AndroidUtilities.dp(14), getMeasuredWidth() - AndroidUtilities.dp(1), cy + AndroidUtilities.dp(14));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(15), AndroidUtilities.dp(15), paint);
            if (staticLayout != null) {
                textPaint.setAlpha((int) ((1.0f - rotation) * 255));
                canvas.save();
                canvas.translate((getMeasuredWidth() - width) / 2, (getMeasuredHeight() - height) / 2 + AndroidUtilities.dpf2(0.2f) + rotation * AndroidUtilities.dp(5));
                staticLayout.draw(canvas);
                canvas.restore();
                paint.setAlpha((int) (rotation * 255));
                int cx = (int) rect.centerX();
                cy = (int) rect.centerY();
                cy -= AndroidUtilities.dp(5) * (1.0f - rotation) + AndroidUtilities.dp(3.0f);
                canvas.drawLine(cx + AndroidUtilities.dp(0.5f), cy - AndroidUtilities.dp(0.5f), cx - AndroidUtilities.dp(6), cy + AndroidUtilities.dp(6), paint);
                canvas.drawLine(cx - AndroidUtilities.dp(0.5f), cy - AndroidUtilities.dp(0.5f), cx + AndroidUtilities.dp(6), cy + AndroidUtilities.dp(6), paint);
            }
        }
    }

    private class PhotoProgressView {

        private long lastUpdateTime = 0;
        private float radOffset = 0;
        private float currentProgress = 0;
        private float animationProgressStart = 0;
        private long currentProgressTime = 0;
        private float animatedProgressValue = 0;
        private RectF progressRect = new RectF();
        private int backgroundState = -1;
        private View parent;
        private int size = AndroidUtilities.dp(64);
        private int previousBackgroundState = -2;
        private float animatedAlphaValue = 1.0f;
        private float alpha = 1.0f;
        private float scale = 1.0f;

        public PhotoProgressView(Context context, View parentView) {
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
            if (dt > 18) {
                dt = 18;
            }
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
            if (backgroundState == state && animated) {
                return;
            }
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
        public ImageReceiver.BitmapHolder thumb;
        public int dialogId;
        public int index;
        public int size;
        public int radius;
        public int clipBottomAddition;
        public int clipTopAddition;
        public float scale = 1.0f;
        public boolean isEvent;
    }

    public static class EmptyPhotoViewerProvider implements PhotoViewerProvider {
        @Override
        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            return null;
        }

        @Override
        public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            return null;
        }

        @Override
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {

        }

        @Override
        public void willHidePhotoViewer() {

        }

        @Override
        public int setPhotoUnchecked(Object photoEntry) {
            return -1;
        }

        @Override
        public boolean isPhotoChecked(int index) {
            return false;
        }

        @Override
        public int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo) {
            return -1;
        }

        @Override
        public boolean cancelButtonPressed() {
            return true;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {

        }

        @Override
        public int getSelectedCount() {
            return 0;
        }

        @Override
        public void updatePhotoAtIndex(int index) {

        }

        @Override
        public boolean allowCaption() {
            return true;
        }

        @Override
        public boolean scaleToFill() {
            return false;
        }

        @Override
        public void toggleGroupPhotosEnabled() {

        }

        @Override
        public ArrayList<Object> getSelectedPhotosOrder() {
            return null;
        }

        @Override
        public HashMap<Object, Object> getSelectedPhotos() {
            return null;
        }

        @Override
        public boolean canScrollAway() {
            return true;
        }

        @Override
        public boolean allowGroupPhotos() {
            return true;
        }

        @Override
        public void needAddMorePhotos() {

        }

        @Override
        public int getPhotoIndex(int index) {
            return -1;
        }

        @Override
        public void deleteImageAtIndex(int index) {

        }

        @Override
        public String getDeleteMessageString() {
            return null;
        }

        @Override
        public boolean canCaptureMorePhotos() {
            return true;
        }
    }

    public interface PhotoViewerProvider {
        PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);

        ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);

        void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);

        void willHidePhotoViewer();

        boolean isPhotoChecked(int index);

        int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo);

        int setPhotoUnchecked(Object photoEntry);

        boolean cancelButtonPressed();

        void needAddMorePhotos();

        void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo);

        int getSelectedCount();

        void updatePhotoAtIndex(int index);

        boolean allowCaption();

        boolean scaleToFill();

        void toggleGroupPhotosEnabled();

        ArrayList<Object> getSelectedPhotosOrder();

        HashMap<Object, Object> getSelectedPhotos();

        boolean canScrollAway();

        boolean allowGroupPhotos();

        int getPhotoIndex(int index);

        void deleteImageAtIndex(int index);

        String getDeleteMessageString();

        boolean canCaptureMorePhotos();
    }

    private class FrameLayoutDrawer extends SizeNotifierFrameLayoutPhoto {

        private Paint paint = new Paint();

        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
            paint.setColor(0x33000000);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(widthSize, heightSize);

            measureChildWithMargins(captionEditText, widthMeasureSpec, 0, heightMeasureSpec, 0);
            int inputFieldHeight = captionEditText.getMeasuredHeight();

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == captionEditText) {
                    continue;
                }
                if (child == aspectRatioFrameLayout) {
                    int heightSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0), MeasureSpec.EXACTLY);
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightSpec, 0);
                } else if (captionEditText.isPopupView(child)) {
                    if (AndroidUtilities.isInMultiwindow) {
                        if (AndroidUtilities.isTablet()) {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(320), heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight), MeasureSpec.EXACTLY));
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
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
            final int count = getChildCount();
            int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? captionEditText.getEmojiPadding() : 0;

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
                        childLeft = (r - l - width) - lp.rightMargin;
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
                    childTop -= captionEditText.getMeasuredHeight();
                } else if (captionEditText.isPopupView(child)) {
                    if (AndroidUtilities.isInMultiwindow) {
                        childTop = captionEditText.getTop() - child.getMeasuredHeight() + AndroidUtilities.dp(1);
                    } else {
                        childTop = captionEditText.getBottom();
                    }
                } else if (child == selectedPhotosListView) {
                    childTop = actionBar.getMeasuredHeight();
                } else if (child == captionTextView || child == switchCaptionTextView) {
                    int offset = 0;
                    if (!groupedPhotosListView.currentPhotos.isEmpty()) {
                        offset += groupedPhotosListView.getMeasuredHeight();
                    }
                    childTop -= offset;
                } else if (hintTextView != null && child == hintTextView) {
                    childTop = selectedPhotosListView.getBottom() + AndroidUtilities.dp(3);
                } else if (child == cameraItem) {
                    childTop = pickerView.getTop() - AndroidUtilities.dp(sendPhotoType == 4 || sendPhotoType == 5 ? 40 : 15) - cameraItem.getMeasuredHeight();
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            PhotoViewer.this.onDraw(canvas);

            if (Build.VERSION.SDK_INT >= 21 && AndroidUtilities.statusBarHeight != 0 && actionBar != null) {
                paint.setAlpha((int) (255 * actionBar.getAlpha() * 0.2f));
                canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.statusBarHeight, paint);
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == mentionListView || child == captionEditText) {
                if (!captionEditText.isPopupShowing() && captionEditText.getEmojiPadding() == 0 && (AndroidUtilities.usingHardwareInput && captionEditText.getTag() == null || getKeyboardHeight() == 0)) {
                    return false;
                }
            } else if (child == cameraItem || child == pickerView || child == pickerViewSendButton || child == captionTextView || muteItem.getVisibility() == VISIBLE && child == bottomLayout) {
                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? captionEditText.getEmojiPadding() : 0;
                if (captionEditText.isPopupShowing() || AndroidUtilities.usingHardwareInput && captionEditText.getTag() != null || getKeyboardHeight() > 0 || paddingBottom != 0) {
                    bottomTouchEnabled = false;
                    return false;
                } else {
                    bottomTouchEnabled = true;
                }
            } else if (child == checkImageView || child == photosCounterView) {
                if (captionEditText.getTag() != null) {
                    bottomTouchEnabled = false;
                    return false;
                } else {
                    bottomTouchEnabled = true;
                }
            } else if (child == miniProgressView) {
                return false;
            }
            try {
                return child != aspectRatioFrameLayout && super.drawChild(canvas, child, drawingTime);
            } catch (Throwable ignore) {
                return true;
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static volatile PhotoViewer Instance = null;
    private static volatile PhotoViewer PipInstance = null;

    public static PhotoViewer getPipInstance() {
        return PipInstance;
    }

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

    public static boolean hasInstance() {
        return Instance != null;
    }

    public PhotoViewer() {
        blackPaint.setColor(0xff000000);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    photoProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    photoProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    if (videoPlayer == null && a == 0 && (currentMessageObject != null && currentMessageObject.isVideo() || currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document)))) {
                        onActionClick(false);
                    }
                    if (a == 0 && videoPlayer != null) {
                        currentVideoFinishedLoading = true;
                    }
                    break;
                }
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    Float loadProgress = (Float) args[1];
                    photoProgressViews[a].setProgress(loadProgress, true);
                    if (a == 0 && videoPlayer != null && videoPlayerSeekbar != null) {
                        float bufferedProgress;
                        if (currentVideoFinishedLoading) {
                            bufferedProgress = 1.0f;
                        } else {
                            long newTime = SystemClock.elapsedRealtime();
                            if (Math.abs(newTime - lastBufferedPositionCheck) >= 500) {
                                float progress;
                                if (seekToProgressPending == 0) {
                                    long duration = videoPlayer.getDuration();
                                    long position = videoPlayer.getCurrentPosition();
                                    if (duration >= 0 && duration != C.TIME_UNSET && position >= 0) {
                                        progress = position / (float) duration;
                                    } else {
                                        progress = 0.0f;
                                    }
                                } else {
                                    progress = seekToProgressPending;
                                }
                                bufferedProgress = isStreaming ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(progress, currentFileNames[0]) : 1.0f;
                                lastBufferedPositionCheck = newTime;
                            } else {
                                bufferedProgress = -1;
                            }
                        }
                        if (bufferedProgress != -1) {
                            videoPlayerSeekbar.setBufferedProgress(bufferedProgress);
                            if (pipVideoView != null) {
                                pipVideoView.setBufferedProgress(bufferedProgress);
                            }
                            videoPlayerControlFrameLayout.invalidate();
                        }
                        checkBufferedProgress(loadProgress);
                    }
                }
            }
        } else if (id == NotificationCenter.dialogPhotosLoaded) {
            int guid = (Integer) args[3];
            int did = (Integer) args[0];
            if (avatarsDialogId == did && classGuid == guid) {
                boolean fromCache = (Boolean) args[2];

                int setToImage = -1;
                ArrayList<TLRPC.Photo> photos = (ArrayList<TLRPC.Photo>) args[4];
                if (photos.isEmpty()) {
                    return;
                }
                imagesArrLocations.clear();
                imagesArrLocationsSizes.clear();
                avatarsArr.clear();
                for (int a = 0; a < photos.size(); a++) {
                    TLRPC.Photo photo = photos.get(a);
                    if (photo == null || photo instanceof TLRPC.TL_photoEmpty || photo.sizes == null) {
                        continue;
                    }
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640);
                    if (sizeFull != null) {
                        if (setToImage == -1 && currentFileLocation != null) {
                            for (int b = 0; b < photo.sizes.size(); b++) {
                                TLRPC.PhotoSize size = photo.sizes.get(b);
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
                    MessagesController.getInstance(currentAccount).loadDialogPhotos(avatarsDialogId, 80, 0, false, classGuid);
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
                    DataQuery.getInstance(currentAccount).loadMedia(currentDialogId, 80, 0, DataQuery.MEDIA_PHOTOVIDEO, true, classGuid);
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
                        if (imagesByIdsTemp[loadIndex].indexOfKey(message.getId()) < 0) {
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
                            imagesByIds[a] = imagesByIdsTemp[a].clone();
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
                                DataQuery.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 80, loadFromMaxId, DataQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                            } else {
                                DataQuery.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 80, loadFromMaxId, DataQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                            }
                        }
                    }
                } else {
                    int added = 0;
                    for (MessageObject message : arr) {
                        if (imagesByIds[loadIndex].indexOfKey(message.getId()) < 0) {
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
        } else if (id == NotificationCenter.FilePreparingFailed) {
            MessageObject messageObject = (MessageObject) args[0];
            if (loadInitialVideo) {
                loadInitialVideo = false;
                progressView.setVisibility(View.INVISIBLE);
                preparePlayer(currentPlayingVideoFile, false, false);
            } else if (tryStartRequestPreviewOnFinish) {
                releasePlayer();
                tryStartRequestPreviewOnFinish = !MediaController.getInstance().scheduleVideoConvert(videoPreviewMessageObject, true);
            } else if (messageObject == videoPreviewMessageObject) {
                requestingPreview = false;
                progressView.setVisibility(View.INVISIBLE);
            }
        } else if (id == NotificationCenter.FileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject == videoPreviewMessageObject) {
                String finalPath = (String) args[1];
                long finalSize = (Long) args[3];
                if (finalSize != 0) {
                    requestingPreview = false;
                    progressView.setVisibility(View.INVISIBLE);
                    preparePlayer(Uri.fromFile(new File(finalPath)), false, true);
                }
            }
        }
    }

    private void showDownloadAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        boolean alreadyDownloading;
        alreadyDownloading = currentMessageObject != null && currentMessageObject.isVideo() && FileLoader.getInstance(currentMessageObject.currentAccount).isLoadingFile(currentFileNames[0]);
        if (alreadyDownloading) {
            builder.setMessage(LocaleController.getString("PleaseStreamDownload", R.string.PleaseStreamDownload));
        } else {
            builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
        }
        showAlertDialog(builder);
    }

    private void onSharePressed() {
        if (parentActivity == null || !allowShare) {
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
                if (!TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
                    f = new File(currentMessageObject.messageOwner.attachPath);
                    if (!f.exists()) {
                        f = null;
                    }
                }
                if (f == null) {
                    f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                }
            } else if (currentFileLocation != null) {
                f = FileLoader.getPathToAttach(currentFileLocation, avatarsDialogId != 0 || isEvent);
            }

            if (f.exists()) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                if (isVideo) {
                    intent.setType("video/mp4");
                } else {
                    if (currentMessageObject != null) {
                        intent.setType(currentMessageObject.getMimeType());
                    } else {
                        intent.setType("image/jpeg");
                    }
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(parentActivity, BuildConfig.APPLICATION_ID + ".provider", f));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    }
                } else {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                }

                parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
            } else {
                showDownloadAlert();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void setScaleToFill() {
        float bitmapWidth = centerImage.getBitmapWidth();
        float containerWidth = getContainerViewWidth();
        float bitmapHeight = centerImage.getBitmapHeight();
        float containerHeight = getContainerViewHeight();
        float scaleFit = Math.min(containerHeight / bitmapHeight, containerWidth / bitmapWidth);
        float width = (int) (bitmapWidth * scaleFit);
        float height = (int) (bitmapHeight * scaleFit);
        scale = Math.max(containerWidth / width, containerHeight / height);
        updateMinMax(scale);
    }

    public void setParentAlert(ChatAttachAlert alert) {
        parentAlert = alert;
    }

    public void setParentActivity(final Activity activity) {
        currentAccount = UserConfig.selectedAccount;
        centerImage.setCurrentAccount(currentAccount);
        leftImage.setCurrentAccount(currentAccount);
        rightImage.setCurrentAccount(currentAccount);
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

        windowView = new FrameLayout(activity) {

            private Runnable attachRunnable;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return isVisible && super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return isVisible && PhotoViewer.this.onTouchEvent(event);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (Build.VERSION.SDK_INT >= 21 && child == animatingImageView && lastInsets != null) {
                    WindowInsets insets = (WindowInsets) lastInsets;
                    canvas.drawRect(0, getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight() + insets.getSystemWindowInsetBottom(), blackPaint);
                }
                return result;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    WindowInsets insets = (WindowInsets) lastInsets;
                    if (AndroidUtilities.incorrectDisplaySizeFix) {
                        if (heightSize > AndroidUtilities.displaySize.y) {
                            heightSize = AndroidUtilities.displaySize.y;
                        }
                        heightSize += AndroidUtilities.statusBarHeight;
                    }
                    heightSize -= insets.getSystemWindowInsetBottom();
                    widthSize -= insets.getSystemWindowInsetRight();
                } else {
                    if (heightSize > AndroidUtilities.displaySize.y) {
                        heightSize = AndroidUtilities.displaySize.y;
                    }
                }
                setMeasuredDimension(widthSize, heightSize);
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    widthSize -= ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
                }
                ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
                animatingImageView.measure(MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.AT_MOST));
                containerView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
            }

            @SuppressWarnings("DrawAllocation")
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int x = 0;
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    x += ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
                }
                animatingImageView.layout(x, 0, x + animatingImageView.getMeasuredWidth(), animatingImageView.getMeasuredHeight());
                containerView.layout(x, 0, x + containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                wasLayout = true;
                if (changed) {
                    if (!dontResetZoomOnFirstLayout) {
                        scale = 1;
                        translationX = 0;
                        translationY = 0;
                        updateMinMax(scale);
                    }

                    if (checkImageView != null) {
                        checkImageView.post(new Runnable() {
                            @Override
                            public void run() {
                                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) checkImageView.getLayoutParams();
                                WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                                int rotation = manager.getDefaultDisplay().getRotation();
                                layoutParams.topMargin = (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(40)) / 2 + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                                checkImageView.setLayoutParams(layoutParams);

                                layoutParams = (FrameLayout.LayoutParams) photosCounterView.getLayoutParams();
                                layoutParams.topMargin = (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(40)) / 2 + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                                photosCounterView.setLayoutParams(layoutParams);
                            }
                        });
                    }
                }
                if (dontResetZoomOnFirstLayout) {
                    setScaleToFill();
                    dontResetZoomOnFirstLayout = false;
                }
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
                wasLayout = false;
            }

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
                            FileLog.e(e);
                        }
                    }
                }
                return super.startActionModeForChild(originalView, callback, type);
            }
        };
        windowView.setBackgroundDrawable(backgroundDrawable);
        windowView.setClipChildren(true);
        windowView.setFocusable(false);

        animatingImageView = new ClippingImageView(activity);
        animatingImageView.setAnimationValues(animationValues);
        windowView.addView(animatingImageView, LayoutHelper.createFrame(40, 40));

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setFitsSystemWindows(true);
            containerView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @SuppressLint("NewApi")
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    WindowInsets oldInsets = (WindowInsets) lastInsets;
                    lastInsets = insets;
                    if (oldInsets == null || !oldInsets.toString().equals(insets.toString())) {
                        if (animationInProgress == 1) {
                            animatingImageView.setTranslationX(animatingImageView.getTranslationX() - getLeftInset());
                            animationValues[0][2] = animatingImageView.getTranslationX();
                        }
                        windowView.requestLayout();
                    }
                    return insets.consumeSystemWindowInsets();
                }
            });
            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        actionBar = new ActionBar(activity) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setTitleColor(0xffffffff);
        actionBar.setSubtitleColor(0xffffffff);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, false);
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
                        if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && currentMessageObject.messageOwner.media.webpage != null && currentMessageObject.messageOwner.media.webpage.document == null) {
                            TLObject fileLocation = getFileLocation(currentIndex, null);
                            f = FileLoader.getPathToAttach(fileLocation, true);
                        } else {
                            f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                        }
                    } else if (currentFileLocation != null) {
                        f = FileLoader.getPathToAttach(currentFileLocation, avatarsDialogId != 0 || isEvent);
                    }

                    if (f != null && f.exists()) {
                        MediaController.saveFile(f.toString(), parentActivity, currentMessageObject != null && currentMessageObject.isVideo() ? 1 : 0, null, null);
                    } else {
                        showDownloadAlert();
                    }
                } else if (id == gallery_menu_showall) {
                    if (currentDialogId != 0) {
                        disableShowCheck = true;
                        Bundle args2 = new Bundle();
                        args2.putLong("dialog_id", currentDialogId);
                        MediaActivity mediaActivity = new MediaActivity(args2, new int[]{-1, -1, -1, -1, -1});
                        if (parentChatActivity != null) {
                            mediaActivity.setChatInfo(parentChatActivity.getCurrentChatInfo());
                        }
                        closePhoto(false, false);
                        ((LaunchActivity) parentActivity).presentFragment(mediaActivity, false, true);
                    }
                } else if (id == gallery_menu_showinchat) {
                    if (currentMessageObject == null) {
                        return;
                    }
                    Bundle args = new Bundle();
                    int lower_part = (int) currentDialogId;
                    int high_id = (int) (currentDialogId >> 32);
                    if (lower_part != 0) {
                        if (high_id == 1) {
                            args.putInt("chat_id", lower_part);
                        } else {
                            if (lower_part > 0) {
                                args.putInt("user_id", lower_part);
                            } else if (lower_part < 0) {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_part);
                                if (chat != null && chat.migrated_to != null) {
                                    args.putInt("migrated_to", lower_part);
                                    lower_part = -chat.migrated_to.channel_id;
                                }
                                args.putInt("chat_id", -lower_part);
                            }
                        }
                    } else {
                        args.putInt("enc_id", high_id);
                    }
                    args.putInt("message_id", currentMessageObject.getId());
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                    LaunchActivity launchActivity = (LaunchActivity) parentActivity;
                    boolean remove = launchActivity.getMainFragmentsCount() > 1 || AndroidUtilities.isTablet();
                    launchActivity.presentFragment(new ChatActivity(args), remove, true);
                    currentMessageObject = null;
                    closePhoto(false, false);
                } else if (id == gallery_menu_send) {
                    if (currentMessageObject == null || parentActivity == null) {
                        return;
                    }
                    ((LaunchActivity) parentActivity).switchToAccount(currentMessageObject.currentAccount, true);
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    final ArrayList<MessageObject> fmessages = new ArrayList<>();
                    fmessages.add(currentMessageObject);
                    fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                        @Override
                        public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                            if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
                                for (int a = 0; a < dids.size(); a++) {
                                    long did = dids.get(a);
                                    if (message != null) {
                                        SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, true, null, null, null);
                                    }
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did);
                                }
                                fragment.finishFragment();
                            } else {
                                long did = dids.get(0);
                                int lower_part = (int) did;
                                int high_part = (int) (did >> 32);
                                Bundle args = new Bundle();
                                args.putBoolean("scrollToTopOnResume", true);
                                if (lower_part != 0) {
                                    if (lower_part > 0) {
                                        args.putInt("user_id", lower_part);
                                    } else if (lower_part < 0) {
                                        args.putInt("chat_id", -lower_part);
                                    }
                                } else {
                                    args.putInt("enc_id", high_part);
                                }
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                                ChatActivity chatActivity = new ChatActivity(args);
                                if (((LaunchActivity) parentActivity).presentFragment(chatActivity, true, false)) {
                                    chatActivity.showFieldPanelForForward(true, fmessages);
                                } else {
                                    fragment.finishFragment();
                                }
                            }
                        }
                    });
                    ((LaunchActivity) parentActivity).presentFragment(fragment, false, true);
                    closePhoto(false, false);
                } else if (id == gallery_menu_delete) {
                    if (parentActivity == null || placeProvider == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    String text = placeProvider.getDeleteMessageString();
                    if (text != null) {
                        builder.setMessage(text);
                    } else if (currentMessageObject != null && currentMessageObject.isVideo()) {
                        builder.setMessage(LocaleController.formatString("AreYouSureDeleteVideo", R.string.AreYouSureDeleteVideo));
                    } else if (currentMessageObject != null && currentMessageObject.isGif()) {
                        builder.setMessage(LocaleController.formatString("AreYouSure", R.string.AreYouSure));
                    } else {
                        builder.setMessage(LocaleController.formatString("AreYouSureDeletePhoto", R.string.AreYouSureDeletePhoto));
                    }
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));

                    final boolean deleteForAll[] = new boolean[1];
                    if (currentMessageObject != null) {
                        int lower_id = (int) currentMessageObject.getDialogId();
                        if (lower_id != 0) {
                            TLRPC.Chat currentChat;
                            TLRPC.User currentUser;
                            if (lower_id > 0) {
                                currentUser = MessagesController.getInstance(currentAccount).getUser(lower_id);
                                currentChat = null;
                            } else {
                                currentUser = null;
                                currentChat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                            }
                            if (currentUser != null || !ChatObject.isChannel(currentChat)) {
                                boolean hasOutgoing = false;
                                int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                                if (currentUser != null && currentUser.id != UserConfig.getInstance(currentAccount).getClientUserId() || currentChat != null) {
                                    if ((currentMessageObject.messageOwner.action == null || currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) && currentMessageObject.isOut() && (currentDate - currentMessageObject.messageOwner.date) <= 2 * 24 * 60 * 60) {
                                        FrameLayout frameLayout = new FrameLayout(parentActivity);
                                        CheckBoxCell cell = new CheckBoxCell(parentActivity, 1);
                                        cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                                        if (currentChat != null) {
                                            cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                                        } else {
                                            cell.setText(LocaleController.formatString("DeleteForUser", R.string.DeleteForUser, UserObject.getFirstName(currentUser)), "", false, false);
                                        }
                                        cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                                        frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                                        cell.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                CheckBoxCell cell = (CheckBoxCell) v;
                                                deleteForAll[0] = !deleteForAll[0];
                                                cell.setChecked(deleteForAll[0], true);
                                            }
                                        });
                                        builder.setView(frameLayout);
                                    }
                                }
                            }
                        }
                    }
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (!imagesArr.isEmpty()) {
                                if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                                    return;
                                }
                                MessageObject obj = imagesArr.get(currentIndex);
                                if (obj.isSent()) {
                                    closePhoto(false, false);
                                    ArrayList<Integer> arr = new ArrayList<>();
                                    if (slideshowMessageId != 0) {
                                        arr.add(slideshowMessageId);
                                    } else {
                                        arr.add(obj.getId());
                                    }

                                    ArrayList<Long> random_ids = null;
                                    TLRPC.EncryptedChat encryptedChat = null;
                                    if ((int) obj.getDialogId() == 0 && obj.messageOwner.random_id != 0) {
                                        random_ids = new ArrayList<>();
                                        random_ids.add(obj.messageOwner.random_id);
                                        encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (obj.getDialogId() >> 32));
                                    }

                                    MessagesController.getInstance(currentAccount).deleteMessages(arr, random_ids, encryptedChat, obj.messageOwner.to_id.channel_id, deleteForAll[0]);
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
                                    MessagesController.getInstance(currentAccount).deleteUserPhoto(null);
                                    closePhoto(false, false);
                                } else if (photo != null) {
                                    TLRPC.TL_inputPhoto inputPhoto = new TLRPC.TL_inputPhoto();
                                    inputPhoto.id = photo.id;
                                    inputPhoto.access_hash = photo.access_hash;
                                    MessagesController.getInstance(currentAccount).deleteUserPhoto(inputPhoto);
                                    MessagesStorage.getInstance(currentAccount).clearUserPhoto(avatarsDialogId, photo.id);
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
                            } else if (!secureDocuments.isEmpty()) {
                                if (placeProvider == null) {
                                    return;
                                }
                                secureDocuments.remove(currentIndex);
                                placeProvider.deleteImageAtIndex(currentIndex);
                                if (secureDocuments.isEmpty()) {
                                    closePhoto(false, false);
                                } else {
                                    int index = currentIndex;
                                    if (index >= secureDocuments.size()) {
                                        index = secureDocuments.size() - 1;
                                    }
                                    currentIndex = -1;
                                    setImageIndex(index, true);
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                } else if (id == gallery_menu_share) {
                    onSharePressed();
                } else if (id == gallery_menu_openin) {
                    try {
                        AndroidUtilities.openForView(currentMessageObject, parentActivity);
                        closePhoto(false, false);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (id == gallery_menu_masks) {
                    if (parentActivity == null || currentMessageObject == null || currentMessageObject.messageOwner.media == null || currentMessageObject.messageOwner.media.photo == null) {
                        return;
                    }
                    StickersAlert stickersAlert = new StickersAlert(parentActivity, currentMessageObject.messageOwner.media.photo);
                    stickersAlert.show();
                } else if (id == gallery_menu_pip) {
                    if (pipItem.getAlpha() != 1.0f) {
                        return;
                    }
                    switchToPip();
                } else if (id == gallery_menu_cancel_loading) {
                    if (currentMessageObject == null) {
                        return;
                    }
                    FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
                    releasePlayer();
                    bottomLayout.setTag(1);
                    bottomLayout.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean canOpenMenu() {
                if (currentMessageObject != null) {
                    File f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                    return f.exists();
                } else if (currentFileLocation != null) {
                    File f = FileLoader.getPathToAttach(currentFileLocation, avatarsDialogId != 0 || isEvent);
                    return f.exists();
                }
                return false;
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        masksItem = menu.addItem(gallery_menu_masks, R.drawable.ic_masks_msk1);
        pipItem = menu.addItem(gallery_menu_pip, R.drawable.ic_goinline);
        sendItem = menu.addItem(gallery_menu_send, R.drawable.msg_panel_reply);

        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.addSubItem(gallery_menu_openin, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp)).setTextColor(0xfffafafa);
        menuItem.addSubItem(gallery_menu_showall, LocaleController.getString("ShowAllMedia", R.string.ShowAllMedia)).setTextColor(0xfffafafa);
        menuItem.addSubItem(gallery_menu_showinchat, LocaleController.getString("ShowInChat", R.string.ShowInChat)).setTextColor(0xfffafafa);
        menuItem.addSubItem(gallery_menu_share, LocaleController.getString("ShareFile", R.string.ShareFile)).setTextColor(0xfffafafa);
        menuItem.addSubItem(gallery_menu_save, LocaleController.getString("SaveToGallery", R.string.SaveToGallery)).setTextColor(0xfffafafa);
        menuItem.addSubItem(gallery_menu_delete, LocaleController.getString("Delete", R.string.Delete)).setTextColor(0xfffafafa);
        menuItem.addSubItem(gallery_menu_cancel_loading, LocaleController.getString("StopDownload", R.string.StopDownload)).setTextColor(0xfffafafa);
        menuItem.redrawPopup(0xf9222222);

        bottomLayout = new FrameLayout(actvityContext);
        bottomLayout.setBackgroundColor(0x7f000000);
        containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        groupedPhotosListView = new GroupedPhotosListView(actvityContext);
        containerView.addView(groupedPhotosListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 62, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        captionTextView = createCaptionTextView();
        switchCaptionTextView = createCaptionTextView();

        for (int a = 0; a < 3; a++) {
            photoProgressViews[a] = new PhotoProgressView(containerView.getContext(), containerView);
            photoProgressViews[a].setBackgroundState(0, false);
        }

        miniProgressView = new RadialProgressView(actvityContext) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (containerView != null) {
                    containerView.invalidate();
                }
            }

            @Override
            public void invalidate() {
                super.invalidate();
                if (containerView != null) {
                    containerView.invalidate();
                }
            }
        };
        miniProgressView.setUseSelfAlpha(true);
        miniProgressView.setProgressColor(0xffffffff);
        miniProgressView.setSize(AndroidUtilities.dp(54));
        miniProgressView.setBackgroundResource(R.drawable.circle_big);
        miniProgressView.setVisibility(View.INVISIBLE);
        miniProgressView.setAlpha(0.0f);
        containerView.addView(miniProgressView, LayoutHelper.createFrame(64, 64, Gravity.CENTER));

        shareButton = new ImageView(containerView.getContext());
        shareButton.setImageResource(R.drawable.share);
        shareButton.setScaleType(ImageView.ScaleType.CENTER);
        shareButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        bottomLayout.addView(shareButton, LayoutHelper.createFrame(50, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSharePressed();
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

        createVideoControlsInterface();

        progressView = new RadialProgressView(parentActivity);
        progressView.setProgressColor(0xffffffff);
        progressView.setBackgroundResource(R.drawable.circle_big);
        progressView.setVisibility(View.INVISIBLE);
        containerView.addView(progressView, LayoutHelper.createFrame(54, 54, Gravity.CENTER));

        qualityPicker = new PickerBottomLayoutViewer(parentActivity);
        qualityPicker.setBackgroundColor(0x7f000000);
        qualityPicker.updateSelectedCount(0, false);
        qualityPicker.setTranslationY(AndroidUtilities.dp(120));
        qualityPicker.doneButton.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
        containerView.addView(qualityPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        qualityPicker.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedCompression = previousCompression;
                didChangedCompressionLevel(false);
                showQualityView(false);
                requestVideoPreview(2);
            }
        });
        qualityPicker.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showQualityView(false);
                requestVideoPreview(2);
            }
        });

        qualityChooseView = new QualityChooseView(parentActivity);
        qualityChooseView.setTranslationY(AndroidUtilities.dp(120));
        qualityChooseView.setVisibility(View.INVISIBLE);
        qualityChooseView.setBackgroundColor(0x7f000000);
        containerView.addView(qualityChooseView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        pickerView = new FrameLayout(actvityContext) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return bottomTouchEnabled && super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return bottomTouchEnabled && super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return bottomTouchEnabled && super.onTouchEvent(event);
            }
        };
        pickerView.setBackgroundColor(0x7f000000);
        containerView.addView(pickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        videoTimelineView = new VideoTimelinePlayView(parentActivity);
        videoTimelineView.setDelegate(new VideoTimelinePlayView.VideoTimelineViewDelegate() {
            @Override
            public void onLeftProgressChanged(float progress) {
                if (videoPlayer == null) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                    containerView.invalidate();
                }
                videoPlayer.seekTo((int) (videoDuration * progress));
                videoPlayerSeekbar.setProgress(0);
                videoTimelineView.setProgress(0);
                updateVideoInfo();
            }

            @Override
            public void onRightProgressChanged(float progress) {
                if (videoPlayer == null) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                    containerView.invalidate();
                }
                videoPlayer.seekTo((int) (videoDuration * progress));
                videoPlayerSeekbar.setProgress(0);
                videoTimelineView.setProgress(0);
                updateVideoInfo();
            }

            @Override
            public void onPlayProgressChanged(float progress) {
                if (videoPlayer == null) {
                    return;
                }
                videoPlayer.seekTo((int) (videoDuration * progress));
            }

            @Override
            public void didStartDragging() {

            }

            @Override
            public void didStopDragging() {

            }
        });
        pickerView.addView(videoTimelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 8, 0, 88));

        pickerViewSendButton = new ImageView(parentActivity);
        pickerViewSendButton.setScaleType(ImageView.ScaleType.CENTER);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), 0xff66bffa, 0xff66bffa);
        pickerViewSendButton.setBackgroundDrawable(drawable);
        pickerViewSendButton.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
        pickerViewSendButton.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
        pickerViewSendButton.setImageResource(R.drawable.ic_send);
        containerView.addView(pickerViewSendButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 14, 14));
        pickerViewSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                if (placeProvider != null && !doneButtonPressed) {
                    VideoEditedInfo videoEditedInfo = getCurrentVideoEditedInfo();
                    placeProvider.sendButtonPressed(currentIndex, videoEditedInfo);
                    doneButtonPressed = true;
                    closePhoto(false, false);
                }
            }
        });

        LinearLayout itemsLayout = new LinearLayout(parentActivity);
        itemsLayout.setOrientation(LinearLayout.HORIZONTAL);
        pickerView.addView(itemsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 34, 0));

        cropItem = new ImageView(parentActivity);
        cropItem.setScaleType(ImageView.ScaleType.CENTER);
        cropItem.setImageResource(R.drawable.photo_crop);
        cropItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(cropItem, LayoutHelper.createLinear(70, 48));
        cropItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                switchToEditMode(1);
            }
        });

        paintItem = new ImageView(parentActivity);
        paintItem.setScaleType(ImageView.ScaleType.CENTER);
        paintItem.setImageResource(R.drawable.photo_paint);
        paintItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(paintItem, LayoutHelper.createLinear(70, 48));
        paintItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                switchToEditMode(3);
            }
        });

        compressItem = new ImageView(parentActivity);
        compressItem.setTag(1);
        compressItem.setScaleType(ImageView.ScaleType.CENTER);
        compressItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        selectedCompression = preferences.getInt("compress_video2", 1);
        if (selectedCompression <= 0) {
            compressItem.setImageResource(R.drawable.video_240);
        } else if (selectedCompression == 1) {
            compressItem.setImageResource(R.drawable.video_360);
        } else if (selectedCompression == 2) {
            compressItem.setImageResource(R.drawable.video_480);
        } else if (selectedCompression == 3) {
            compressItem.setImageResource(R.drawable.video_720);
        } else if (selectedCompression == 4) {
            compressItem.setImageResource(R.drawable.video_1080);
        }
        itemsLayout.addView(compressItem, LayoutHelper.createLinear(70, 48));
        compressItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                showQualityView(true);
                requestVideoPreview(1);
            }
        });

        muteItem = new ImageView(parentActivity);
        muteItem.setScaleType(ImageView.ScaleType.CENTER);
        muteItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(muteItem, LayoutHelper.createLinear(70, 48));
        muteItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                muteVideo = !muteVideo;
                if (muteVideo && !checkImageView.isChecked()) {
                    checkImageView.callOnClick();
                } else {
                    Object object = imagesArrLocals.get(currentIndex);
                    if (object instanceof MediaController.PhotoEntry) {
                        ((MediaController.PhotoEntry) object).editedInfo = getCurrentVideoEditedInfo();
                    }
                }
                updateMuteButton();
            }
        });

        cameraItem = new ImageView(parentActivity);
        cameraItem.setScaleType(ImageView.ScaleType.CENTER);
        cameraItem.setImageResource(R.drawable.photo_add);
        cameraItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        containerView.addView(cameraItem, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 16, 0));
        cameraItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (placeProvider == null || captionEditText.getTag() != null) {
                    return;
                }
                placeProvider.needAddMorePhotos();
                closePhoto(true, false);
            }
        });

        tuneItem = new ImageView(parentActivity);
        tuneItem.setScaleType(ImageView.ScaleType.CENTER);
        tuneItem.setImageResource(R.drawable.photo_tools);
        tuneItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(tuneItem, LayoutHelper.createLinear(70, 48));
        tuneItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                switchToEditMode(2);
            }
        });

        timeItem = new ImageView(parentActivity);
        timeItem.setScaleType(ImageView.ScaleType.CENTER);
        timeItem.setImageResource(R.drawable.photo_timer);
        timeItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(timeItem, LayoutHelper.createLinear(70, 48));
        timeItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (parentActivity == null || captionEditText.getTag() != null) {
                    return;
                }
                BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
                builder.setUseHardwareLayer(false);
                LinearLayout linearLayout = new LinearLayout(parentActivity);
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setCustomView(linearLayout);

                TextView titleView = new TextView(parentActivity);
                titleView.setLines(1);
                titleView.setSingleLine(true);
                titleView.setText(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
                titleView.setTextColor(0xffffffff);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(4));
                titleView.setGravity(Gravity.CENTER_VERTICAL);
                linearLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                titleView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });

                titleView = new TextView(parentActivity);
                titleView.setText(isCurrentVideo ? LocaleController.getString("MessageLifetimeVideo", R.string.MessageLifetimeVideo) : LocaleController.getString("MessageLifetimePhoto", R.string.MessageLifetimePhoto));
                titleView.setTextColor(0xff808080);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                titleView.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), AndroidUtilities.dp(8));
                titleView.setGravity(Gravity.CENTER_VERTICAL);
                linearLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                titleView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });


                final BottomSheet bottomSheet = builder.create();
                final NumberPicker numberPicker = new NumberPicker(parentActivity);
                numberPicker.setMinValue(0);
                numberPicker.setMaxValue(28);
                Object object = imagesArrLocals.get(currentIndex);
                int currentTTL;
                if (object instanceof MediaController.PhotoEntry) {
                    currentTTL = ((MediaController.PhotoEntry) object).ttl;
                } else if (object instanceof MediaController.SearchImage) {
                    currentTTL = ((MediaController.SearchImage) object).ttl;
                } else {
                    currentTTL = 0;
                }
                if (currentTTL == 0) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    numberPicker.setValue(preferences.getInt("self_destruct", 7));
                } else {
                    if (currentTTL >= 0 && currentTTL < 21) {
                        numberPicker.setValue(currentTTL);
                    } else {
                        numberPicker.setValue(21 + currentTTL / 5 - 5);
                    }
                }
                numberPicker.setTextColor(0xffffffff);
                numberPicker.setSelectorColor(0xff4d4d4d);
                numberPicker.setFormatter(new NumberPicker.Formatter() {
                    @Override
                    public String format(int value) {
                        if (value == 0) {
                            return LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever);
                        } else if (value >= 1 && value < 21) {
                            return LocaleController.formatTTLString(value);
                        } else {
                            return LocaleController.formatTTLString((value - 16) * 5);
                        }
                    }
                });
                linearLayout.addView(numberPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                FrameLayout buttonsLayout = new FrameLayout(parentActivity) {
                    @Override
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                        int count = getChildCount();
                        View positiveButton = null;
                        int width = right - left;
                        for (int a = 0; a < count; a++) {
                            View child = getChildAt(a);
                            if ((Integer) child.getTag() == Dialog.BUTTON_POSITIVE) {
                                positiveButton = child;
                                child.layout(width - getPaddingRight() - child.getMeasuredWidth(), getPaddingTop(), width - getPaddingRight() + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                            } else if ((Integer) child.getTag() == Dialog.BUTTON_NEGATIVE) {
                                int x = width - getPaddingRight() - child.getMeasuredWidth();
                                if (positiveButton != null) {
                                    x -= positiveButton.getMeasuredWidth() + AndroidUtilities.dp(8);
                                }
                                child.layout(x, getPaddingTop(), x + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                            } else {
                                child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                            }
                        }
                    }
                };
                buttonsLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

                TextView textView = new TextView(parentActivity);
                textView.setMinWidth(AndroidUtilities.dp(64));
                textView.setTag(Dialog.BUTTON_POSITIVE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTextColor(0xff49bcf2);
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
                textView.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable());
                textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
                buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int value = numberPicker.getValue();
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("self_destruct", value);
                        editor.commit();
                        bottomSheet.dismiss();
                        int seconds;
                        if (value >= 0 && value < 21) {
                            seconds = value;
                        } else {
                            seconds = (value - 16) * 5;
                        }
                        Object object = imagesArrLocals.get(currentIndex);
                        if (object instanceof MediaController.PhotoEntry) {
                            ((MediaController.PhotoEntry) object).ttl = seconds;
                        } else if (object instanceof MediaController.SearchImage) {
                            ((MediaController.SearchImage) object).ttl = seconds;
                        }
                        timeItem.setColorFilter(seconds != 0 ? new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY) : null);
                        if (!checkImageView.isChecked()) {
                            checkImageView.callOnClick();
                        }
                    }
                });

                textView = new TextView(parentActivity);
                textView.setMinWidth(AndroidUtilities.dp(64));
                textView.setTag(Dialog.BUTTON_NEGATIVE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTextColor(0xff49bcf2);
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
                textView.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable());
                textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
                buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        bottomSheet.dismiss();
                    }
                });
                bottomSheet.show();
                bottomSheet.setBackgroundColor(0xff000000);
            }
        });

        editorDoneLayout = new PickerBottomLayoutViewer(actvityContext);
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
                if (currentEditMode == 1 && !photoCropView.isReady()) {
                    return;
                }
                applyCurrentEditMode();
                switchToEditMode(0);
            }
        });

        resetButton = new TextView(actvityContext);
        resetButton.setVisibility(View.GONE);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetButton.setTextColor(0xffffffff);
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        resetButton.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        resetButton.setText(LocaleController.getString("Reset", R.string.CropReset).toUpperCase());
        resetButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        editorDoneLayout.addView(resetButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER));
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                photoCropView.reset();
            }
        });

        gestureDetector = new GestureDetector(containerView.getContext(), this);
        gestureDetector.setOnDoubleTapListener(this);

        ImageReceiver.ImageReceiverDelegate imageReceiverDelegate = new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb) {
                if (imageReceiver == centerImage && set && !thumb && currentEditMode == 1 && photoCropView != null) {
                    Bitmap bitmap = imageReceiver.getBitmap();
                    if (bitmap != null) {
                        photoCropView.setBitmap(bitmap, imageReceiver.getOrientation(), sendPhotoType != 1);
                    }
                }
                if (imageReceiver == centerImage && set && placeProvider != null && placeProvider.scaleToFill() && !ignoreDidSetImage) {
                    if (!wasLayout) {
                        dontResetZoomOnFirstLayout = true;
                    } else {
                        setScaleToFill();
                    }
                }
            }
        };

        centerImage.setParentView(containerView);
        centerImage.setCrossfadeAlpha((byte) 2);
        centerImage.setInvalidateAll(true);
        centerImage.setDelegate(imageReceiverDelegate);
        leftImage.setParentView(containerView);
        leftImage.setCrossfadeAlpha((byte) 2);
        leftImage.setInvalidateAll(true);
        leftImage.setDelegate(imageReceiverDelegate);
        rightImage.setParentView(containerView);
        rightImage.setCrossfadeAlpha((byte) 2);
        rightImage.setInvalidateAll(true);
        rightImage.setDelegate(imageReceiverDelegate);

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        checkImageView = new CheckBox(containerView.getContext(), R.drawable.selectphoto_large) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return bottomTouchEnabled && super.onTouchEvent(event);
            }
        };
        checkImageView.setDrawBackground(true);
        checkImageView.setHasBorder(true);
        checkImageView.setSize(40);
        checkImageView.setCheckOffset(AndroidUtilities.dp(1));
        checkImageView.setColor(0xff66bffa, 0xffffffff);
        checkImageView.setVisibility(View.GONE);
        containerView.addView(checkImageView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 0, rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90 ? 58 : 68, 10, 0));
        if (Build.VERSION.SDK_INT >= 21) {
            ((FrameLayout.LayoutParams) checkImageView.getLayoutParams()).topMargin += AndroidUtilities.statusBarHeight;
        }
        checkImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null) {
                    return;
                }
                setPhotoChecked();
            }
        });

        photosCounterView = new CounterView(parentActivity);
        containerView.addView(photosCounterView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 0, rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90 ? 58 : 68, 66, 0));
        if (Build.VERSION.SDK_INT >= 21) {
            ((FrameLayout.LayoutParams) photosCounterView.getLayoutParams()).topMargin += AndroidUtilities.statusBarHeight;
        }
        photosCounterView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (captionEditText.getTag() != null || placeProvider == null || placeProvider.getSelectedPhotosOrder() == null || placeProvider.getSelectedPhotosOrder().isEmpty()) {
                    return;
                }
                togglePhotosListView(!isPhotosListViewVisible, true);
            }
        });

        selectedPhotosListView = new RecyclerListView(parentActivity);
        selectedPhotosListView.setVisibility(View.GONE);
        selectedPhotosListView.setAlpha(0.0f); //TODO
        selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
        selectedPhotosListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (view instanceof PhotoPickerPhotoCell && position == 0) {
                    outRect.left = AndroidUtilities.dp(3);
                } else {
                    outRect.left = 0;
                }
                outRect.right = AndroidUtilities.dp(3);
            }
        });
        ((DefaultItemAnimator) selectedPhotosListView.getItemAnimator()).setDelayAnimations(false);
        selectedPhotosListView.setBackgroundColor(0x7f000000);
        selectedPhotosListView.setPadding(0, AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3));
        selectedPhotosListView.setLayoutManager(new LinearLayoutManager(parentActivity, LinearLayoutManager.HORIZONTAL, false) {

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerEnd linearSmoothScroller = new LinearSmoothScrollerEnd(recyclerView.getContext());
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        selectedPhotosListView.setAdapter(selectedPhotosAdapter = new ListAdapter(parentActivity));
        containerView.addView(selectedPhotosListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 88, Gravity.LEFT | Gravity.TOP));
        selectedPhotosListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 0 && placeProvider.allowGroupPhotos()) {
                    boolean enabled = SharedConfig.groupPhotosEnabled;
                    SharedConfig.toggleGroupPhotosEnabled();
                    placeProvider.toggleGroupPhotosEnabled();
                    ImageView imageView = (ImageView) view;
                    imageView.setColorFilter(!enabled ? new PorterDuffColorFilter(0xff66bffa, PorterDuff.Mode.MULTIPLY) : null);
                    showHint(false, !enabled);
                } else {
                    ignoreDidSetImage = true;
                    int idx = imagesArrLocals.indexOf(view.getTag());
                    if (idx >= 0) {
                        currentIndex = -1;
                        setImageIndex(idx, true);
                    }
                    ignoreDidSetImage = false;
                }
            }
        });

        captionEditText = new PhotoViewerCaptionEnterView(actvityContext, containerView, windowView) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                try {
                    return !bottomTouchEnabled && super.dispatchTouchEvent(ev);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                try {
                    return !bottomTouchEnabled && super.onInterceptTouchEvent(ev);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return !bottomTouchEnabled && super.onTouchEvent(event);
            }
        };
        captionEditText.setDelegate(new PhotoViewerCaptionEnterView.PhotoViewerCaptionEnterViewDelegate() {
            @Override
            public void onCaptionEnter() {
                closeCaptionEnter(true);
            }

            @Override
            public void onTextChanged(CharSequence text) {
                if (mentionsAdapter != null && captionEditText != null && parentChatActivity != null && text != null) {
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), captionEditText.getCursorPosition(), parentChatActivity.messages, false);
                }
            }

            @Override
            public void onWindowSizeChanged(int size) {
                int height = AndroidUtilities.dp(36 * Math.min(3, mentionsAdapter.getItemCount()) + (mentionsAdapter.getItemCount() > 3 ? 18 : 0));
                if (size - ActionBar.getCurrentActionBarHeight() * 2 < height) {
                    allowMentions = false;
                    if (mentionListView != null && mentionListView.getVisibility() == View.VISIBLE) {
                        mentionListView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    allowMentions = true;
                    if (mentionListView != null && mentionListView.getVisibility() == View.INVISIBLE) {
                        mentionListView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        containerView.addView(captionEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        mentionListView = new RecyclerListView(actvityContext) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return !bottomTouchEnabled && super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return !bottomTouchEnabled && super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return !bottomTouchEnabled && super.onTouchEvent(event);
            }
        };
        mentionListView.setTag(5);
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
        mentionListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
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
                        mentionListView.setAlpha(1.0f);
                        return;
                    } else {
                        mentionLayoutManager.scrollToPositionWithOffset(0, 10000);
                    }
                    if (allowMentions) {
                        mentionListView.setVisibility(View.VISIBLE);
                        mentionListAnimation = new AnimatorSet();
                        mentionListAnimation.playTogether(
                                ObjectAnimator.ofFloat(mentionListView, "alpha", 0.0f, 1.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionListAnimation = null;
                                }
                            }
                        });
                        mentionListAnimation.setDuration(200);
                        mentionListAnimation.start();
                    } else {
                        mentionListView.setAlpha(1.0f);
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
                        mentionListAnimation = new AnimatorSet();
                        mentionListAnimation.playTogether(
                                ObjectAnimator.ofFloat(mentionListView, "alpha", 0.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionListView.setVisibility(View.GONE);
                                    mentionListAnimation = null;
                                }
                            }
                        });
                        mentionListAnimation.setDuration(200);
                        mentionListAnimation.start();
                    } else {
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
                    if (user.username != null) {
                        captionEditText.replaceWithText(start, len, "@" + user.username + " ", false);
                    } else {
                        String name = UserObject.getFirstName(user);
                        Spannable spannable = new SpannableString(name + " ");
                        spannable.setSpan(new URLSpanUserMentionPhotoViewer("" + user.id, true), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        captionEditText.replaceWithText(start, len, spannable, false);
                    }
                } else if (object instanceof String) {
                    captionEditText.replaceWithText(start, len, object + " ", false);
                } else if (object instanceof EmojiSuggestion) {
                    String code = ((EmojiSuggestion) object).emoji;
                    captionEditText.addEmojiToRecent(code);
                    captionEditText.replaceWithText(start, len, code, true);
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

    private boolean checkInlinePermissions() {
        if (parentActivity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(parentActivity)) {
            return true;
        } else {
            new AlertDialog.Builder(parentActivity).setTitle(LocaleController.getString("AppName", R.string.AppName))
                    .setMessage(LocaleController.getString("PermissionDrawAboveOtherApps", R.string.PermissionDrawAboveOtherApps))
                    .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
                        @TargetApi(Build.VERSION_CODES.M)
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (parentActivity != null) {
                                try {
                                    parentActivity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + parentActivity.getPackageName())));
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }
                    }).show();
        }
        return false;
    }

    private TextView createCaptionTextView() {
        TextView textView = new TextView(actvityContext) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return bottomTouchEnabled && super.onTouchEvent(event);
            }
        };
        textView.setMovementMethod(new LinkMovementMethodMy());
        textView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        textView.setLinkTextColor(0xffffffff);
        textView.setTextColor(0xffffffff);
        textView.setHighlightColor(0x33ffffff);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setVisibility(View.INVISIBLE);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!needCaptionLayout) {
                    return;
                }
                openCaptionEnter();
            }
        });
        return textView;
    }

    private int getLeftInset() {
        if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
            return ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
        }
        return 0;
    }

    private void dismissInternal() {
        try {
            if (windowView.getParent() != null) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(true);
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(windowView);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void switchToPip() {
        if (videoPlayer == null || !textureUploaded || !checkInlinePermissions() || changingTextureView || switchingInlineMode || isInline) {
            return;
        }
        if (PipInstance != null) {
            PipInstance.destroyPhotoViewer();
        }
        PipInstance = Instance;
        Instance = null;
        switchingInlineMode = true;
        isVisible = false;
        if (currentPlaceObject != null) {
            currentPlaceObject.imageReceiver.setVisible(true, true);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            pipAnimationInProgress = true;
            org.telegram.ui.Components.Rect rect = PipVideoView.getPipRect(aspectRatioFrameLayout.getAspectRatio());

            float scale = rect.width / videoTextureView.getWidth();
            rect.y += AndroidUtilities.statusBarHeight;

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(textureImageView, "scaleX", scale),
                    ObjectAnimator.ofFloat(textureImageView, "scaleY", scale),
                    ObjectAnimator.ofFloat(textureImageView, "translationX", rect.x),
                    ObjectAnimator.ofFloat(textureImageView, "translationY", rect.y),
                    ObjectAnimator.ofFloat(videoTextureView, "scaleX", scale),
                    ObjectAnimator.ofFloat(videoTextureView, "scaleY", scale),
                    ObjectAnimator.ofFloat(videoTextureView, "translationX", rect.x - aspectRatioFrameLayout.getX()),
                    ObjectAnimator.ofFloat(videoTextureView, "translationY", rect.y - aspectRatioFrameLayout.getY()),
                    ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                    ObjectAnimator.ofFloat(actionBar, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(bottomLayout, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(captionTextView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(groupedPhotosListView, "alpha", 0.0f)
            );
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(250);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pipAnimationInProgress = false;
                    switchToInlineRunnable.run();
                }
            });
            animatorSet.start();
        } else {
            switchToInlineRunnable.run();
            dismissInternal();
        }
    }

    public VideoPlayer getVideoPlayer() {
        return videoPlayer;
    }

    public void exitFromPip() {
        if (!isInline) {
            return;
        }
        if (Instance != null) {
            Instance.closePhoto(false, true);
        }
        Instance = PipInstance;
        PipInstance = null;
        switchingInlineMode = true;
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        changingTextureView = true;

        isInline = false;

        videoTextureView.setVisibility(View.INVISIBLE);
        aspectRatioFrameLayout.addView(videoTextureView);

        if (ApplicationLoader.mainInterfacePaused) {
            try {
                parentActivity.startService(new Intent(ApplicationLoader.applicationContext, BringAppForegroundService.class));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        if (Build.VERSION.SDK_INT >= 21) {
            pipAnimationInProgress = true;
            org.telegram.ui.Components.Rect rect = PipVideoView.getPipRect(aspectRatioFrameLayout.getAspectRatio());

            float scale = rect.width / textureImageView.getLayoutParams().width;
            rect.y += AndroidUtilities.statusBarHeight;
            textureImageView.setScaleX(scale);
            textureImageView.setScaleY(scale);
            textureImageView.setTranslationX(rect.x);
            textureImageView.setTranslationY(rect.y);
            videoTextureView.setScaleX(scale);
            videoTextureView.setScaleY(scale);
            videoTextureView.setTranslationX(rect.x - aspectRatioFrameLayout.getX());
            videoTextureView.setTranslationY(rect.y - aspectRatioFrameLayout.getY());
        } else {
            pipVideoView.close();
            pipVideoView = null;
        }

        try {
            isVisible = true;
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(windowView, windowLayoutParams);
            if (currentPlaceObject != null) {
                currentPlaceObject.imageReceiver.setVisible(false, false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            waitingForDraw = 4;
        }
    }

    private void createVideoControlsInterface() {
        videoPlayerSeekbar = new SeekBar(containerView.getContext());
        videoPlayerSeekbar.setLineHeight(AndroidUtilities.dp(4));
        videoPlayerSeekbar.setColors(0x66ffffff, 0x66ffffff, 0xffd5d0d7, 0xffffffff, 0xffffffff);
        videoPlayerSeekbar.setDelegate(new SeekBar.SeekBarDelegate() {
            @Override
            public void onSeekBarDrag(float progress) {
                if (videoPlayer != null) {
                    if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                        progress = videoTimelineView.getLeftProgress() + (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * progress;
                    }
                    long duration = videoPlayer.getDuration();
                    if (duration == C.TIME_UNSET) {
                        seekToProgressPending = progress;
                    } else {
                        videoPlayer.seekTo((int) (progress * duration));
                    }
                }
            }
        });

        videoPlayerControlFrameLayout = new FrameLayout(containerView.getContext()) {

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                if (videoPlayerSeekbar.onTouch(event.getAction(), event.getX() - AndroidUtilities.dp(48), event.getY())) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                }
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                long duration;
                if (videoPlayer != null) {
                    duration = videoPlayer.getDuration();
                    if (duration == C.TIME_UNSET) {
                        duration = 0;
                    }
                } else {
                    duration = 0;
                }
                duration /= 1000;
                int size = (int) Math.ceil(videoPlayerTime.getPaint().measureText(String.format("%02d:%02d / %02d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));
                videoPlayerSeekbar.setSize(getMeasuredWidth() - AndroidUtilities.dp(48 + 16) - size, getMeasuredHeight());
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                float progress = 0;
                if (videoPlayer != null) {
                    progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                    if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                        progress -= videoTimelineView.getLeftProgress();
                        if (progress < 0) {
                            progress = 0;
                        }
                        progress /= (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress());
                        if (progress > 1) {
                            progress = 1;
                        }
                    }
                }
                videoPlayerSeekbar.setProgress(progress);
                videoTimelineView.setProgress(progress);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(48), 0);
                videoPlayerSeekbar.draw(canvas);
                canvas.restore();
            }
        };
        videoPlayerControlFrameLayout.setWillNotDraw(false);
        bottomLayout.addView(videoPlayerControlFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        videoPlayButton = new ImageView(containerView.getContext());
        videoPlayButton.setScaleType(ImageView.ScaleType.CENTER);
        videoPlayerControlFrameLayout.addView(videoPlayButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 4, 0, 0, 0));
        videoPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoPlayer == null) {
                    return;
                }
                if (isPlaying) {
                    videoPlayer.pause();
                } else {
                    if (isCurrentVideo) {
                        if (Math.abs(videoTimelineView.getProgress() - 1.0f) < 0.01f || videoPlayer.getCurrentPosition() == videoPlayer.getDuration()) {
                            videoPlayer.seekTo(0);
                        }
                    } else {
                        if (Math.abs(videoPlayerSeekbar.getProgress() - 1.0f) < 0.01f || videoPlayer.getCurrentPosition() == videoPlayer.getDuration()) {
                            videoPlayer.seekTo(0);
                        }
                    }
                    videoPlayer.play();
                }
                containerView.invalidate();
            }
        });

        videoPlayerTime = new SimpleTextView(containerView.getContext());
        videoPlayerTime.setTextColor(0xffffffff);
        videoPlayerTime.setGravity(Gravity.RIGHT | Gravity.TOP);
        videoPlayerTime.setTextSize(13);
        videoPlayerControlFrameLayout.addView(videoPlayerTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 17, 7, 0));
    }

    private void openCaptionEnter() {
        if (imageMoveAnimation != null || changeModeAnimation != null || currentEditMode != 0) {
            return;
        }
        selectedPhotosListView.setVisibility(View.GONE);
        selectedPhotosListView.setEnabled(false);
        selectedPhotosListView.setAlpha(0.0f);
        selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
        photosCounterView.setRotationX(0.0f);
        isPhotosListViewVisible = false;
        captionEditText.setTag(1);
        captionEditText.openKeyboard();
        lastTitle = actionBar.getTitle();
        if (isCurrentVideo) {
            actionBar.setTitle(muteVideo ? LocaleController.getString("GifCaption", R.string.GifCaption) : LocaleController.getString("VideoCaption", R.string.VideoCaption));
            actionBar.setSubtitle(null);
        } else {
            actionBar.setTitle(LocaleController.getString("PhotoCaption", R.string.PhotoCaption));
        }
    }

    private VideoEditedInfo getCurrentVideoEditedInfo() {
        if (!isCurrentVideo || currentPlayingVideoFile == null || compressionsCount == 0) {
            return null;
        }
        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = startTime;
        videoEditedInfo.endTime = endTime;
        videoEditedInfo.rotationValue = rotationValue;
        videoEditedInfo.originalWidth = originalWidth;
        videoEditedInfo.originalHeight = originalHeight;
        videoEditedInfo.bitrate = bitrate;
        videoEditedInfo.originalPath = currentPlayingVideoFile.getPath();
        videoEditedInfo.estimatedSize = estimatedSize;
        videoEditedInfo.estimatedDuration = estimatedDuration;
        videoEditedInfo.framerate = videoFramerate;

        if (!muteVideo && (compressItem.getTag() == null || selectedCompression == compressionsCount - 1)) {
            videoEditedInfo.resultWidth = originalWidth;
            videoEditedInfo.resultHeight = originalHeight;
            videoEditedInfo.bitrate = muteVideo ? -1 : originalBitrate;
            videoEditedInfo.muted = muteVideo;
        } else {
            if (muteVideo) {
                selectedCompression = 1;
                updateWidthHeightBitrateForCompression();
            }
            videoEditedInfo.resultWidth = resultWidth;
            videoEditedInfo.resultHeight = resultHeight;
            videoEditedInfo.bitrate = muteVideo ? -1 : bitrate;
            videoEditedInfo.muted = muteVideo;
        }
        return videoEditedInfo;
    }

    private void closeCaptionEnter(boolean apply) {
        if (currentIndex < 0 || currentIndex >= imagesArrLocals.size()) {
            return;
        }
        Object object = imagesArrLocals.get(currentIndex);
        if (apply) {
            CharSequence caption = captionEditText.getFieldCharSequence();
            CharSequence[] result = new CharSequence[] {caption};

            ArrayList<TLRPC.MessageEntity> entities = DataQuery.getInstance(currentAccount).getEntities(result);
            if (object instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                photoEntry.caption = result[0];
                photoEntry.entities = entities;
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage photoEntry = (MediaController.SearchImage) object;
                photoEntry.caption = result[0];
                photoEntry.entities = entities;
            }

            if (captionEditText.getFieldCharSequence().length() != 0 && !placeProvider.isPhotoChecked(currentIndex)) {
                setPhotoChecked();
            }
            setCurrentCaption(null, result[0], false);
        }
        captionEditText.setTag(null);
        if (lastTitle != null) {
            actionBar.setTitle(lastTitle);
            lastTitle = null;
        }
        if (isCurrentVideo) {
            actionBar.setSubtitle(muteVideo ? null : currentSubtitle);
        }
        updateCaptionTextForCurrentPhoto(object);
        if (captionEditText.isPopupShowing()) {
            captionEditText.hidePopup();
        }
        captionEditText.closeKeyboard();
    }

    private void updateVideoPlayerTime() {
        String newText;
        if (videoPlayer == null) {
            newText = String.format("%02d:%02d / %02d:%02d", 0, 0, 0, 0);
        } else {
            long current = videoPlayer.getCurrentPosition();
            if (current < 0) {
                current = 0;
            }
            long total = videoPlayer.getDuration();
            if (total < 0) {
                total = 0;
            }
            if (total != C.TIME_UNSET && current != C.TIME_UNSET) {
                if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                    total *= (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress());
                    current -= videoTimelineView.getLeftProgress() * total;
                    if (current > total) {
                        current = total;
                    }
                }
                current /= 1000;
                total /= 1000;
                newText = String.format("%02d:%02d / %02d:%02d", current / 60, current % 60, total / 60, total % 60);
            } else {
                newText = String.format("%02d:%02d / %02d:%02d", 0, 0, 0, 0);
            }
        }
        videoPlayerTime.setText(newText);
    }

    private void checkBufferedProgress(float progress) {
        if (!isStreaming || parentActivity == null || streamingAlertShown || videoPlayer == null || currentMessageObject == null) {
            return;
        }
        TLRPC.Document document = currentMessageObject.getDocument();
        if (document == null) {
            return;
        }
        if (progress < 0.9f && (document.size * progress >= 5 * 1024 * 1024 || progress >= 0.5f && document.size >= 2 * 1024 * 1024) && Math.abs(SystemClock.elapsedRealtime() - startedPlayTime) >= 2000) {
            long duration = videoPlayer.getDuration();
            if (duration == C.TIME_UNSET) {
                Toast toast = Toast.makeText(parentActivity, LocaleController.getString("VideoDoesNotSupportStreaming", R.string.VideoDoesNotSupportStreaming), Toast.LENGTH_LONG);
                toast.show();
            }
            streamingAlertShown = true;
        }
    }

    private void preparePlayer(Uri uri, boolean playWhenReady, boolean preview) {
        if (!preview) {
            currentPlayingVideoFile = uri;
        }
        if (parentActivity == null) {
            return;
        }
        streamingAlertShown = false;
        startedPlayTime = SystemClock.elapsedRealtime();
        currentVideoFinishedLoading = false;
        lastBufferedPositionCheck = 0;
        seekToProgressPending = 0;
        firstAnimationDelay = true;
        inPreview = preview;
        releasePlayer();
        if (videoTextureView == null) {
            aspectRatioFrameLayout = new AspectRatioFrameLayout(parentActivity) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (textureImageView != null) {
                        ViewGroup.LayoutParams layoutParams = textureImageView.getLayoutParams();
                        layoutParams.width = getMeasuredWidth();
                        layoutParams.height = getMeasuredHeight();
                    }
                }
            };
            aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
            containerView.addView(aspectRatioFrameLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            videoTextureView = new TextureView(parentActivity);
            videoTextureView.setPivotX(0);
            videoTextureView.setPivotY(0);
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }
        if (Build.VERSION.SDK_INT >= 21 && textureImageView == null) {
            textureImageView = new ImageView(parentActivity);
            textureImageView.setBackgroundColor(0xffff0000);
            textureImageView.setPivotX(0);
            textureImageView.setPivotY(0);
            textureImageView.setVisibility(View.INVISIBLE);
            containerView.addView(textureImageView);
        }
        textureUploaded = false;
        videoCrossfadeStarted = false;
        videoTextureView.setAlpha(videoCrossfadeAlpha = 0.0f);
        videoPlayButton.setImageResource(R.drawable.inline_video_play);
        if (videoPlayer == null) {
            videoPlayer = new VideoPlayer();
            videoPlayer.setTextureView(videoTextureView);
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (videoPlayer == null) {
                        return;
                    }
                    if (isStreaming) {
                        toggleMiniProgress(playbackState == ExoPlayer.STATE_BUFFERING, true);
                    }
                    if (playWhenReady && (playbackState != ExoPlayer.STATE_ENDED && playbackState != ExoPlayer.STATE_IDLE)) {
                        try {
                            parentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            keepScreenOnFlagSet = true;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        try {
                            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            keepScreenOnFlagSet = false;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    if (seekToProgressPending != 0 && (playbackState == ExoPlayer.STATE_READY || playbackState == ExoPlayer.STATE_IDLE)) {
                        int seekTo = (int) (videoPlayer.getDuration() * seekToProgressPending);
                        videoPlayer.seekTo(seekTo);
                        seekToProgressPending = 0;
                    }
                    if (playbackState == ExoPlayer.STATE_READY) {
                        if (aspectRatioFrameLayout.getVisibility() != View.VISIBLE) {
                            aspectRatioFrameLayout.setVisibility(View.VISIBLE);
                        }
                        if (!pipItem.isEnabled()) {
                            pipAvailable = true;
                            pipItem.setEnabled(true);
                            pipItem.setAlpha(1.0f);
                        }
                    }
                    if (videoPlayer.isPlaying() && playbackState != ExoPlayer.STATE_ENDED) {
                        if (!isPlaying) {
                            isPlaying = true;
                            videoPlayButton.setImageResource(R.drawable.inline_video_pause);
                            AndroidUtilities.runOnUIThread(updateProgressRunnable);
                        }
                    } else if (isPlaying) {
                        isPlaying = false;
                        videoPlayButton.setImageResource(R.drawable.inline_video_play);
                        AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
                        if (playbackState == ExoPlayer.STATE_ENDED) {
                            if (isCurrentVideo) {
                                if (!videoTimelineView.isDragging()) {
                                    videoTimelineView.setProgress(0.0f);
                                    if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                                        videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                                    } else {
                                        videoPlayer.seekTo(0);
                                    }
                                    videoPlayer.pause();
                                    containerView.invalidate();
                                }
                            } else {
                                if (!isActionBarVisible) {
                                    toggleActionBar(true, true);
                                }
                                if (!videoPlayerSeekbar.isDragging()) {
                                    videoPlayerSeekbar.setProgress(0.0f);
                                    videoPlayerControlFrameLayout.invalidate();
                                    if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                                        videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                                    } else {
                                        videoPlayer.seekTo(0);
                                    }
                                    videoPlayer.pause();
                                }
                            }
                            if (pipVideoView != null) {
                                pipVideoView.onVideoCompleted();
                            }
                        }
                    }
                    if (pipVideoView != null) {
                        pipVideoView.updatePlayButton();
                    }
                    updateVideoPlayerTime();
                }

                @Override
                public void onError(Exception e) {
                    FileLog.e(e);
                    if (!menuItem.isSubItemVisible(gallery_menu_openin)) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("CantPlayVideo", R.string.CantPlayVideo));
                    builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                AndroidUtilities.openForView(currentMessageObject, parentActivity);
                                closePhoto(false, false);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    if (aspectRatioFrameLayout != null) {
                        if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
                        aspectRatioFrameLayout.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height, unappliedRotationDegrees);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    if (!textureUploaded) {
                        textureUploaded = true;
                        containerView.invalidate();
                    }
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    if (changingTextureView) {
                        changingTextureView = false;
                        if (isInline) {
                            if (isInline) {
                                waitingForFirstTextureUpload = 1;
                            }
                            changedTextureView.setSurfaceTexture(surfaceTexture);
                            changedTextureView.setSurfaceTextureListener(surfaceTextureListener);
                            changedTextureView.setVisibility(View.VISIBLE);
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                    if (waitingForFirstTextureUpload == 2) {
                        if (textureImageView != null) {
                            textureImageView.setVisibility(View.INVISIBLE);
                            textureImageView.setImageDrawable(null);
                            if (currentBitmap != null) {
                                currentBitmap.recycle();
                                currentBitmap = null;
                            }
                        }
                        switchingInlineMode = false;

                        if (Build.VERSION.SDK_INT >= 21) {
                            aspectRatioFrameLayout.getLocationInWindow(pipPosition);
                            pipPosition[0] -= getLeftInset();
                            pipPosition[1] -= containerView.getTranslationY();

                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(textureImageView, "scaleX", 1.0f),
                                    ObjectAnimator.ofFloat(textureImageView, "scaleY", 1.0f),
                                    ObjectAnimator.ofFloat(textureImageView, "translationX", pipPosition[0]),
                                    ObjectAnimator.ofFloat(textureImageView, "translationY", pipPosition[1]),
                                    ObjectAnimator.ofFloat(videoTextureView, "scaleX", 1.0f),
                                    ObjectAnimator.ofFloat(videoTextureView, "scaleY", 1.0f),
                                    ObjectAnimator.ofFloat(videoTextureView, "translationX", pipPosition[0] - aspectRatioFrameLayout.getX()),
                                    ObjectAnimator.ofFloat(videoTextureView, "translationY", pipPosition[1] - aspectRatioFrameLayout.getY()),
                                    ObjectAnimator.ofInt(backgroundDrawable, "alpha", 255),
                                    ObjectAnimator.ofFloat(actionBar, "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(bottomLayout, "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(captionTextView, "alpha", 1.0f),
                                    ObjectAnimator.ofFloat(groupedPhotosListView, "alpha", 1.0f)
                            );
                            animatorSet.setInterpolator(new DecelerateInterpolator());
                            animatorSet.setDuration(250);
                            animatorSet.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    pipAnimationInProgress = false;
                                }
                            });
                            animatorSet.start();
                        } else {
                            //containerView.setTranslationY(0);
                        }

                        waitingForFirstTextureUpload = 0;
                    }
                }
            });
        }

        videoPlayer.preparePlayer(uri, "other");
        videoPlayerSeekbar.setProgress(0);
        videoTimelineView.setProgress(0);
        videoPlayerSeekbar.setBufferedProgress(0);

        if (currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document))) {
            bottomLayout.setVisibility(View.VISIBLE);
            bottomLayout.setTranslationY(-AndroidUtilities.dp(48));
        }
        videoPlayerControlFrameLayout.setVisibility(isCurrentVideo ? View.GONE : View.VISIBLE);

        dateTextView.setVisibility(View.GONE);
        nameTextView.setVisibility(View.GONE);
        if (allowShare) {
            shareButton.setVisibility(View.GONE);
            menuItem.showSubItem(gallery_menu_share);
        }

        videoPlayer.setPlayWhenReady(playWhenReady);
        inPreview = preview;
    }

    private void releasePlayer() {
        if (videoPlayer != null) {
            videoPlayer.releasePlayer();
            videoPlayer = null;
        }
        toggleMiniProgress(false, false);
        pipAvailable = false;
        if (pipItem.isEnabled()) {
            pipItem.setEnabled(false);
            pipItem.setAlpha(0.5f);
        }
        if (keepScreenOnFlagSet) {
            try {
                parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                keepScreenOnFlagSet = false;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (aspectRatioFrameLayout != null) {
            containerView.removeView(aspectRatioFrameLayout);
            aspectRatioFrameLayout = null;
        }
        if (videoTextureView != null) {
            videoTextureView = null;
        }
        if (isPlaying) {
            isPlaying = false;
            videoPlayButton.setImageResource(R.drawable.inline_video_play);
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
        }
        if (!inPreview && !requestingPreview) {
            videoPlayerControlFrameLayout.setVisibility(View.GONE);
            dateTextView.setVisibility(View.VISIBLE);
            nameTextView.setVisibility(View.VISIBLE);
            if (allowShare) {
                shareButton.setVisibility(View.VISIBLE);
                menuItem.hideSubItem(gallery_menu_share);
            }
        }
    }

    private void updateCaptionTextForCurrentPhoto(Object object) {
        CharSequence caption = null;
        if (object instanceof MediaController.PhotoEntry) {
            caption = ((MediaController.PhotoEntry) object).caption;
        } else if (object instanceof TLRPC.BotInlineResult) {
            //caption = ((TLRPC.BotInlineResult) object).send_message.caption;
        } else if (object instanceof MediaController.SearchImage) {
            caption = ((MediaController.SearchImage) object).caption;
        }
        if (TextUtils.isEmpty(caption)) {
            captionEditText.setFieldText("");
        } else {
            captionEditText.setFieldText(caption);
        }
    }

    public void showAlertDialog(AlertDialog.Builder builder) {
        if (parentActivity == null) {
            return;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
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
            FileLog.e(e);
        }
    }

    private void applyCurrentEditMode() {
        Bitmap bitmap = null;
        ArrayList<TLRPC.InputDocument> stickers = null;
        MediaController.SavedFilterState savedFilterState = null;
        boolean removeSavedState = false;
        if (currentEditMode == 1) {
            bitmap = photoCropView.getBitmap();
            removeSavedState = true;
        } else if (currentEditMode == 2) {
            bitmap = photoFilterView.getBitmap();
            savedFilterState = photoFilterView.getSavedFilterState();
        } else if (currentEditMode == 3) {
            bitmap = photoPaintView.getBitmap();
            stickers = photoPaintView.getMasks();
            removeSavedState = true;
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
                    if (stickers != null) {
                        entry.stickers.addAll(stickers);
                    }
                    if (currentEditMode == 1) {
                        cropItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                        entry.isCropped = true;
                    } else if (currentEditMode == 2) {
                        tuneItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                        entry.isFiltered = true;
                    } else if (currentEditMode == 3) {
                        paintItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                        entry.isPainted = true;
                    }
                    if (savedFilterState != null) {
                        entry.savedFilterState = savedFilterState;
                    } else if (removeSavedState) {
                        entry.savedFilterState = null;
                    }
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage entry = (MediaController.SearchImage) object;
                    entry.imagePath = FileLoader.getPathToAttach(size, true).toString();
                    size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.dp(120), AndroidUtilities.dp(120), 70, false, 101, 101);
                    if (size != null) {
                        entry.thumbPath = FileLoader.getPathToAttach(size, true).toString();
                    }
                    if (stickers != null) {
                        entry.stickers.addAll(stickers);
                    }
                    if (currentEditMode == 1) {
                        cropItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                        entry.isCropped = true;
                    } else if (currentEditMode == 2) {
                        tuneItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                        entry.isFiltered = true;
                    } else if (currentEditMode == 3) {
                        paintItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                        entry.isPainted = true;
                    }
                    if (savedFilterState != null) {
                        entry.savedFilterState = savedFilterState;
                    } else if (removeSavedState) {
                        entry.savedFilterState = null;
                    }
                }
                if ((sendPhotoType == 0 || sendPhotoType == 4) && placeProvider != null) {
                    placeProvider.updatePhotoAtIndex(currentIndex);
                    if (!placeProvider.isPhotoChecked(currentIndex)) {
                        setPhotoChecked();
                    }
                }
                if (currentEditMode == 1) {
                    float scaleX = photoCropView.getRectSizeX() / (float) getContainerViewWidth();
                    float scaleY = photoCropView.getRectSizeY() / (float) getContainerViewHeight();
                    scale = scaleX > scaleY ? scaleX : scaleY;
                    translationX = photoCropView.getRectX() + photoCropView.getRectSizeX() / 2 - getContainerViewWidth() / 2;
                    translationY = photoCropView.getRectY() + photoCropView.getRectSizeY() / 2 - getContainerViewHeight() / 2;
                    zoomAnimation = true;
                    applying = true;

                    photoCropView.onDisappear();
                }
                centerImage.setParentView(null);
                centerImage.setOrientation(0, true);
                ignoreDidSetImage = true;
                centerImage.setImageBitmap(bitmap);
                ignoreDidSetImage = false;
                centerImage.setParentView(containerView);
            }
        }
    }

    private void setPhotoChecked() {
        if (placeProvider != null) {
            if (placeProvider.getSelectedPhotos() != null && maxSelectedPhotos >= 0 && placeProvider.getSelectedPhotos().size() >= maxSelectedPhotos && !placeProvider.isPhotoChecked(currentIndex)) {
                return;
            }
            int num = placeProvider.setPhotoChecked(currentIndex, getCurrentVideoEditedInfo());
            boolean checked = placeProvider.isPhotoChecked(currentIndex);
            checkImageView.setChecked(checked, true);
            if (num >= 0) {
                if (placeProvider.allowGroupPhotos()) {
                    num++;
                }
                if (checked) {
                    selectedPhotosAdapter.notifyItemInserted(num);
                    selectedPhotosListView.smoothScrollToPosition(num);
                } else {
                    selectedPhotosAdapter.notifyItemRemoved(num);
                }
            }
            updateSelectedCount();
        }
    }

    private void switchToEditMode(final int mode) {
        if (currentEditMode == mode || centerImage.getBitmap() == null || changeModeAnimation != null || imageMoveAnimation != null || photoProgressViews[0].backgroundState != -1 || captionEditText.getTag() != null) {
            return;
        }
        if (mode == 0) {
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

                if (sendPhotoType == 1 && !applying) {
                    float minSide = Math.min(getContainerViewWidth(), getContainerViewHeight());
                    scaleX = minSide / (float) bitmapWidth;
                    scaleY = minSide / (float) bitmapHeight;
                    float fillScale = scaleX > scaleY ? scaleX : scaleY;

                    this.scale = fillScale / scale;
                    animateToScale = this.scale * newScale / fillScale;
                } else {
                    animateToScale = newScale / scale;
                }

                animateToX = 0;
                if (currentEditMode == 1) {
                    animateToY = AndroidUtilities.dp(24 + 34);
                } else if (currentEditMode == 2) {
                    animateToY = AndroidUtilities.dp(92);
                } else if (currentEditMode == 3) {
                    animateToY = AndroidUtilities.dp(44);
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    animateToY -= AndroidUtilities.statusBarHeight / 2;
                }
                animationStartTime = System.currentTimeMillis();
                zoomAnimation = true;
            }

            imageMoveAnimation = new AnimatorSet();
            if (currentEditMode == 1) {
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofFloat(editorDoneLayout, "translationY", AndroidUtilities.dp(48)),
                        ObjectAnimator.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                        ObjectAnimator.ofFloat(photoCropView, "alpha", 0)
                );
            } else if (currentEditMode == 2) {
                photoFilterView.shutdown();
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofFloat(photoFilterView.getToolsView(), "translationY", AndroidUtilities.dp(186)),
                        ObjectAnimator.ofFloat(PhotoViewer.this, "animationValue", 0, 1)
                );
            } else if (currentEditMode == 3) {
                photoPaintView.shutdown();
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofFloat(photoPaintView.getToolsView(), "translationY", AndroidUtilities.dp(126)),
                        ObjectAnimator.ofFloat(photoPaintView.getColorPicker(), "translationY", AndroidUtilities.dp(126)),
                        ObjectAnimator.ofFloat(PhotoViewer.this, "animationValue", 0, 1)
                );
            }
            imageMoveAnimation.setDuration(200);
            imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentEditMode == 1) {
                        editorDoneLayout.setVisibility(View.GONE);
                        photoCropView.setVisibility(View.GONE);
                    } else if (currentEditMode == 2) {
                        containerView.removeView(photoFilterView);
                        photoFilterView = null;
                    } else if (currentEditMode == 3) {
                        containerView.removeView(photoPaintView);
                        photoPaintView = null;
                    }
                    imageMoveAnimation = null;
                    currentEditMode = mode;
                    applying = false;
                    animateToScale = 1;
                    animateToX = 0;
                    animateToY = 0;
                    scale = 1;
                    updateMinMax(scale);
                    containerView.invalidate();

                    AnimatorSet animatorSet = new AnimatorSet();
                    ArrayList<Animator> arrayList = new ArrayList<>();
                    arrayList.add(ObjectAnimator.ofFloat(pickerView, "translationY", 0));
                    arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, "translationY", 0));
                    arrayList.add(ObjectAnimator.ofFloat(actionBar, "translationY", 0));
                    if (needCaptionLayout) {
                        arrayList.add(ObjectAnimator.ofFloat(captionTextView, "translationY", 0));
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4) {
                        arrayList.add(ObjectAnimator.ofFloat(checkImageView, "alpha", 1));
                        arrayList.add(ObjectAnimator.ofFloat(photosCounterView, "alpha", 1));
                    }
                    if (cameraItem.getTag() != null) {
                        cameraItem.setVisibility(View.VISIBLE);
                        arrayList.add(ObjectAnimator.ofFloat(cameraItem, "alpha", 1));
                    }
                    animatorSet.playTogether(arrayList);
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            pickerView.setVisibility(View.VISIBLE);
                            pickerViewSendButton.setVisibility(View.VISIBLE);
                            actionBar.setVisibility(View.VISIBLE);
                            if (needCaptionLayout) {
                                captionTextView.setVisibility(captionTextView.getTag() != null ? View.VISIBLE : View.INVISIBLE);
                            }
                            if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                                checkImageView.setVisibility(View.VISIBLE);
                                photosCounterView.setVisibility(View.VISIBLE);
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

                    @Override
                    public void onChange(boolean reset) {
                        resetButton.setVisibility(reset ? View.GONE : View.VISIBLE);
                    }
                });
            }
            photoCropView.onAppear();

            editorDoneLayout.doneButton.setText(LocaleController.getString("Crop", R.string.Crop));
            editorDoneLayout.doneButton.setTextColor(0xff51bdf3);

            changeModeAnimation = new AnimatorSet();
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimator.ofFloat(actionBar, "translationY", 0, -actionBar.getHeight()));
            if (needCaptionLayout) {
                arrayList.add(ObjectAnimator.ofFloat(captionTextView, "translationY", 0, AndroidUtilities.dp(96)));
            }
            if (sendPhotoType == 0 || sendPhotoType == 4) {
                arrayList.add(ObjectAnimator.ofFloat(checkImageView, "alpha", 1, 0));
                arrayList.add(ObjectAnimator.ofFloat(photosCounterView, "alpha", 1, 0));
            }
            if (selectedPhotosListView.getVisibility() == View.VISIBLE) {
                arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, "alpha", 1, 0));
            }
            if (cameraItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(cameraItem, "alpha", 1, 0));
            }
            changeModeAnimation.playTogether(arrayList);
            changeModeAnimation.setDuration(200);
            changeModeAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    changeModeAnimation = null;
                    pickerView.setVisibility(View.GONE);
                    pickerViewSendButton.setVisibility(View.GONE);
                    cameraItem.setVisibility(View.GONE);
                    selectedPhotosListView.setVisibility(View.GONE);
                    selectedPhotosListView.setAlpha(0.0f);
                    selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
                    photosCounterView.setRotationX(0.0f);
                    selectedPhotosListView.setEnabled(false);
                    isPhotosListViewVisible = false;
                    if (needCaptionLayout) {
                        captionTextView.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                        checkImageView.setVisibility(View.GONE);
                        photosCounterView.setVisibility(View.GONE);
                    }

                    final Bitmap bitmap = centerImage.getBitmap();
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
                        if (sendPhotoType == 1) {
                            float minSide = Math.min(getContainerViewWidth(1), getContainerViewHeight(1));
                            newScaleX = minSide / (float) bitmapWidth;
                            newScaleY = minSide / (float) bitmapHeight;
                            newScale = newScaleX > newScaleY ? newScaleX : newScaleY;
                        }

                        animateToScale = newScale / scale;
                        animateToX = 0;
                        animateToY = -AndroidUtilities.dp(24 + 32) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight / 2 : 0);
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSet();
                    imageMoveAnimation.playTogether(
                            ObjectAnimator.ofFloat(editorDoneLayout, "translationY", AndroidUtilities.dp(48), 0),
                            ObjectAnimator.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                            ObjectAnimator.ofFloat(photoCropView, "alpha", 0, 1)
                    );
                    imageMoveAnimation.setDuration(200);
                    imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            editorDoneLayout.setVisibility(View.VISIBLE);
                            photoCropView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            photoCropView.onAppeared();

                            imageMoveAnimation = null;
                            currentEditMode = mode;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            containerView.invalidate();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        } else if (mode == 2) {
            if (photoFilterView == null) {
                MediaController.SavedFilterState state = null;
                Bitmap bitmap;
                String originalPath = null;
                int orientation = 0;
                if (!imagesArrLocals.isEmpty()) {
                    Object object = imagesArrLocals.get(currentIndex);
                    if (object instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) object;
                        if (entry.imagePath == null) {
                            originalPath = entry.path;
                            state = entry.savedFilterState;
                        }
                        orientation = entry.orientation;
                    } else if (object instanceof MediaController.SearchImage) {
                        MediaController.SearchImage entry = (MediaController.SearchImage) object;
                        state = entry.savedFilterState;
                        originalPath = entry.imageUrl;
                    }
                }
                if (state == null) {
                    bitmap = centerImage.getBitmap();
                    orientation = centerImage.getOrientation();
                } else {
                    bitmap = BitmapFactory.decodeFile(originalPath);
                }

                photoFilterView = new PhotoFilterView(parentActivity, bitmap, orientation, state);
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
                photoFilterView.getToolsView().setTranslationY(AndroidUtilities.dp(186));
            }

            changeModeAnimation = new AnimatorSet();
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimator.ofFloat(actionBar, "translationY", 0, -actionBar.getHeight()));
            if (sendPhotoType == 0 || sendPhotoType == 4) {
                arrayList.add(ObjectAnimator.ofFloat(checkImageView, "alpha", 1, 0));
                arrayList.add(ObjectAnimator.ofFloat(photosCounterView, "alpha", 1, 0));
            }
            if (selectedPhotosListView.getVisibility() == View.VISIBLE) {
                arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, "alpha", 1, 0));
            }
            if (cameraItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(cameraItem, "alpha", 1, 0));
            }
            changeModeAnimation.playTogether(arrayList);
            changeModeAnimation.setDuration(200);
            changeModeAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    changeModeAnimation = null;
                    pickerView.setVisibility(View.GONE);
                    pickerViewSendButton.setVisibility(View.GONE);
                    actionBar.setVisibility(View.GONE);
                    cameraItem.setVisibility(View.GONE);
                    selectedPhotosListView.setVisibility(View.GONE);
                    selectedPhotosListView.setAlpha(0.0f);
                    selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
                    photosCounterView.setRotationX(0.0f);
                    selectedPhotosListView.setEnabled(false);
                    isPhotosListViewVisible = false;
                    if (needCaptionLayout) {
                        captionTextView.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                        checkImageView.setVisibility(View.GONE);
                        photosCounterView.setVisibility(View.GONE);
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
                        animateToY = -AndroidUtilities.dp(92) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight / 2 : 0);
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSet();
                    imageMoveAnimation.playTogether(
                            ObjectAnimator.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                            ObjectAnimator.ofFloat(photoFilterView.getToolsView(), "translationY", AndroidUtilities.dp(186), 0)
                    );
                    imageMoveAnimation.setDuration(200);
                    imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            photoFilterView.init();
                            imageMoveAnimation = null;
                            currentEditMode = mode;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            containerView.invalidate();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        } else if (mode == 3) {
            if (photoPaintView == null) {
                photoPaintView = new PhotoPaintView(parentActivity, centerImage.getBitmap(), centerImage.getOrientation());
                containerView.addView(photoPaintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                photoPaintView.getDoneTextView().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyCurrentEditMode();
                        switchToEditMode(0);
                    }
                });
                photoPaintView.getCancelTextView().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        photoPaintView.maybeShowDismissalAlert(PhotoViewer.this, parentActivity, new Runnable() {
                            @Override
                            public void run() {
                                switchToEditMode(0);
                            }
                        });
                    }
                });
                photoPaintView.getColorPicker().setTranslationY(AndroidUtilities.dp(126));
                photoPaintView.getToolsView().setTranslationY(AndroidUtilities.dp(126));
            }

            changeModeAnimation = new AnimatorSet();
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, "translationY", 0, AndroidUtilities.dp(96)));
            arrayList.add(ObjectAnimator.ofFloat(actionBar, "translationY", 0, -actionBar.getHeight()));

            if (needCaptionLayout) {
                arrayList.add(ObjectAnimator.ofFloat(captionTextView, "translationY", 0, AndroidUtilities.dp(96)));
            }
            if (sendPhotoType == 0 || sendPhotoType == 4) {
                arrayList.add(ObjectAnimator.ofFloat(checkImageView, "alpha", 1, 0));
                arrayList.add(ObjectAnimator.ofFloat(photosCounterView, "alpha", 1, 0));
            }
            if (selectedPhotosListView.getVisibility() == View.VISIBLE) {
                arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, "alpha", 1, 0));
            }
            if (cameraItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(cameraItem, "alpha", 1, 0));
            }
            changeModeAnimation.playTogether(arrayList);
            changeModeAnimation.setDuration(200);
            changeModeAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    changeModeAnimation = null;
                    pickerView.setVisibility(View.GONE);
                    pickerViewSendButton.setVisibility(View.GONE);
                    cameraItem.setVisibility(View.GONE);
                    selectedPhotosListView.setVisibility(View.GONE);
                    selectedPhotosListView.setAlpha(0.0f);
                    selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
                    photosCounterView.setRotationX(0.0f);
                    selectedPhotosListView.setEnabled(false);
                    isPhotosListViewVisible = false;
                    if (needCaptionLayout) {
                        captionTextView.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                        checkImageView.setVisibility(View.GONE);
                        photosCounterView.setVisibility(View.GONE);
                    }

                    Bitmap bitmap = centerImage.getBitmap();
                    if (bitmap != null) {
                        int bitmapWidth = centerImage.getBitmapWidth();
                        int bitmapHeight = centerImage.getBitmapHeight();

                        float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                        float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                        float newScaleX = (float) getContainerViewWidth(3) / (float) bitmapWidth;
                        float newScaleY = (float) getContainerViewHeight(3) / (float) bitmapHeight;
                        float scale = scaleX > scaleY ? scaleY : scaleX;
                        float newScale = newScaleX > newScaleY ? newScaleY : newScaleX;

                        animateToScale = newScale / scale;
                        animateToX = 0;
                        animateToY = -AndroidUtilities.dp(44) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight / 2 : 0);
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSet();
                    imageMoveAnimation.playTogether(
                            ObjectAnimator.ofFloat(PhotoViewer.this, "animationValue", 0, 1),
                            ObjectAnimator.ofFloat(photoPaintView.getColorPicker(), "translationY", AndroidUtilities.dp(126), 0),
                            ObjectAnimator.ofFloat(photoPaintView.getToolsView(), "translationY", AndroidUtilities.dp(126), 0)
                    );
                    imageMoveAnimation.setDuration(200);
                    imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            photoPaintView.init();
                            imageMoveAnimation = null;
                            currentEditMode = mode;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            containerView.invalidate();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        }
    }

    private void toggleCheckImageView(boolean show) {
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> arrayList = new ArrayList<>();
        arrayList.add(ObjectAnimator.ofFloat(pickerView, "alpha", show ? 1.0f : 0.0f));
        arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, "alpha", show ? 1.0f : 0.0f));
        if (needCaptionLayout) {
            arrayList.add(ObjectAnimator.ofFloat(captionTextView, "alpha", show ? 1.0f : 0.0f));
        }
        if (sendPhotoType == 0 || sendPhotoType == 4) {
            arrayList.add(ObjectAnimator.ofFloat(checkImageView, "alpha", show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(photosCounterView, "alpha", show ? 1.0f : 0.0f));
        }
        animatorSet.playTogether(arrayList);
        animatorSet.setDuration(200);
        animatorSet.start();
    }

    private void toggleMiniProgressInternal(final boolean show) {
        if (show) {
            miniProgressView.setVisibility(View.VISIBLE);
        }
        miniProgressAnimator = new AnimatorSet();
        miniProgressAnimator.playTogether(ObjectAnimator.ofFloat(miniProgressView, "alpha", show ? 1.0f : 0.0f));
        miniProgressAnimator.setDuration(200);
        miniProgressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(miniProgressAnimator)) {
                    if (!show) {
                        miniProgressView.setVisibility(View.INVISIBLE);
                    }
                    miniProgressAnimator = null;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(miniProgressAnimator)) {
                    miniProgressAnimator = null;
                }
            }
        });
        miniProgressAnimator.start();
    }

    private void toggleMiniProgress(final boolean show, final boolean animated) {
        if (animated) {
            toggleMiniProgressInternal(show);
            if (show) {
                if (miniProgressAnimator != null) {
                    miniProgressAnimator.cancel();
                    miniProgressAnimator = null;
                }
                AndroidUtilities.cancelRunOnUIThread(miniProgressShowRunnable);
                if (firstAnimationDelay) {
                    firstAnimationDelay = false;
                    toggleMiniProgressInternal(true);
                } else {
                    AndroidUtilities.runOnUIThread(miniProgressShowRunnable, 500);
                }
            } else {
                AndroidUtilities.cancelRunOnUIThread(miniProgressShowRunnable);
                if (miniProgressAnimator != null) {
                    miniProgressAnimator.cancel();
                    toggleMiniProgressInternal(false);
                }
            }
        } else {
            if (miniProgressAnimator != null) {
                miniProgressAnimator.cancel();
                miniProgressAnimator = null;
            }
            miniProgressView.setAlpha(show ? 1.0f : 0.0f);
            miniProgressView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void toggleActionBar(final boolean show, final boolean animated) {
        if (actionBarAnimator != null) {
            actionBarAnimator.cancel();
        }
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
            if (bottomLayout.getTag() != null) {
                bottomLayout.setVisibility(View.VISIBLE);
            }
            if (captionTextView.getTag() != null) {
                captionTextView.setVisibility(View.VISIBLE);
            }
        }
        isActionBarVisible = show;
        if (Build.VERSION.SDK_INT >= 21) {
            if (show) {
                if ((windowLayoutParams.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                    windowLayoutParams.flags &=~ WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    if (windowView != null) {
                        try {
                            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                            wm.updateViewLayout(windowView, windowLayoutParams);
                        } catch (Exception ignore) {

                        }
                    }
                }
            } else {
                if ((windowLayoutParams.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0) {
                    windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    if (windowView != null) {
                        try {
                            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                            wm.updateViewLayout(windowView, windowLayoutParams);
                        } catch (Exception ignore) {

                        }
                    }
                }
            }
        }

        if (animated) {
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f));
            if (bottomLayout != null) {
                arrayList.add(ObjectAnimator.ofFloat(bottomLayout, "alpha", show ? 1.0f : 0.0f));
            }
            arrayList.add(ObjectAnimator.ofFloat(groupedPhotosListView, "alpha", show ? 1.0f : 0.0f));
            if (captionTextView.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(captionTextView, "alpha", show ? 1.0f : 0.0f));
            }
            actionBarAnimator = new AnimatorSet();
            actionBarAnimator.playTogether(arrayList);
            actionBarAnimator.setDuration(200);
            actionBarAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(actionBarAnimator)) {
                        if (!show) {
                            actionBar.setVisibility(View.INVISIBLE);
                            if (bottomLayout.getTag() != null) {
                                bottomLayout.setVisibility(View.INVISIBLE);
                            }
                            if (captionTextView.getTag() != null) {
                                captionTextView.setVisibility(View.INVISIBLE);
                            }
                        }
                        actionBarAnimator = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (animation.equals(actionBarAnimator)) {
                        actionBarAnimator = null;
                    }
                }
            });
            actionBarAnimator.start();
        } else {
            actionBar.setAlpha(show ? 1.0f : 0.0f);
            bottomLayout.setAlpha(show ? 1.0f : 0.0f);
            groupedPhotosListView.setAlpha(show ? 1.0f : 0.0f);
            captionTextView.setAlpha(show ? 1.0f : 0.0f);
        }
    }

    private void togglePhotosListView(boolean show, final boolean animated) {
        if (show == isPhotosListViewVisible) {
            return;
        }
        if (show) {
            selectedPhotosListView.setVisibility(View.VISIBLE);
        }
        isPhotosListViewVisible = show;
        selectedPhotosListView.setEnabled(show);

        if (animated) {
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, "alpha", show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, "translationY", show ? 0 : -AndroidUtilities.dp(10)));
            arrayList.add(ObjectAnimator.ofFloat(photosCounterView, "rotationX", show ? 1.0f : 0.0f));
            currentListViewAnimation = new AnimatorSet();
            currentListViewAnimation.playTogether(arrayList);
            if (!show) {
                currentListViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentListViewAnimation != null && currentListViewAnimation.equals(animation)) {
                            selectedPhotosListView.setVisibility(View.GONE);
                            currentListViewAnimation = null;
                        }
                    }
                });
            }
            currentListViewAnimation.setDuration(200);
            currentListViewAnimation.start();
        } else {
            selectedPhotosListView.setAlpha(show ? 1.0f : 0.0f);
            selectedPhotosListView.setTranslationY(show ? 0 : -AndroidUtilities.dp(10));
            photosCounterView.setRotationX(show ? 1.0f : 0.0f);
            if (!show) {
                selectedPhotosListView.setVisibility(View.GONE);
            }
        }
    }

    private String getFileName(int index) {
        if (index < 0) {
            return null;
        }
        if (!secureDocuments.isEmpty()) {
            if (index >= secureDocuments.size()) {
                return null;
            }
            SecureDocument location = secureDocuments.get(index);
            return location.secureFile.dc_id + "_" + location.secureFile.id + ".jpg";
        } else if (!imagesArrLocations.isEmpty() || !imagesArr.isEmpty()) {
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
                return searchImage.getAttachName();
            } else if (object instanceof TLRPC.BotInlineResult) {
                TLRPC.BotInlineResult botInlineResult = (TLRPC.BotInlineResult) object;
                if (botInlineResult.document != null) {
                    return FileLoader.getAttachFileName(botInlineResult.document);
                }  else if (botInlineResult.photo != null) {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(botInlineResult.photo.sizes, AndroidUtilities.getPhotoSize());
                    return FileLoader.getAttachFileName(sizeFull);
                } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                    return Utilities.MD5(botInlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(botInlineResult.content.url, FileLoader.getExtensionByMime(botInlineResult.content.mime_type));
                }
            }
        }
        return null;
    }

    private TLObject getFileLocation(int index, int size[]) {
        if (index < 0) {
            return null;
        }
        if (!secureDocuments.isEmpty()) {
            if (index >= secureDocuments.size()) {
                return null;
            }
            if (size != null) {
                size[0] = secureDocuments.get(index).secureFile.size;
            }
            return secureDocuments.get(index);
        } else if (!imagesArrLocations.isEmpty()) {
            if (index >= imagesArrLocations.size()) {
                return null;
            }
            if (size != null) {
                size[0] = imagesArrLocationsSizes.get(index);
            }
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
                        if (size != null) {
                            size[0] = sizeFull.size;
                            if (size[0] == 0) {
                                size[0] = -1;
                            }
                        }
                        return sizeFull.location;
                    } else if (size != null) {
                        size[0] = -1;
                    }
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo != null || message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage != null) {
                TLRPC.FileLocation location;
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    if (size != null) {
                        size[0] = sizeFull.size;
                        if (size[0] == 0) {
                            size[0] = -1;
                        }
                    }
                    return sizeFull.location;
                } else if (size != null) {
                    size[0] = -1;
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                return ((TLRPC.TL_messageMediaInvoice) message.messageOwner.media).photo;
            } else if (message.getDocument() != null && message.getDocument().thumb != null) {
                if (size != null) {
                    size[0] = message.getDocument().thumb.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
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
        int count = placeProvider.getSelectedCount();
        photosCounterView.setCount(count);
        if (count == 0) {
            togglePhotosListView(false, true);
        }
    }

    private void onPhotoShow(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<SecureDocument> documents, final ArrayList<Object> photos, int index, final PlaceProviderObject object) {
        classGuid = ConnectionsManager.generateClassGuid();
        currentMessageObject = null;
        currentFileLocation = null;
        currentSecureDocument = null;
        currentPathObject = null;
        fromCamera = false;
        currentBotInlineResult = null;
        currentIndex = -1;
        currentFileNames[0] = null;
        currentFileNames[1] = null;
        currentFileNames[2] = null;
        avatarsDialogId = 0;
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
        containerView.setTag(1);
        isCurrentVideo = false;
        imagesArr.clear();
        imagesArrLocations.clear();
        imagesArrLocationsSizes.clear();
        avatarsArr.clear();
        secureDocuments.clear();
        imagesArrLocals.clear();
        for (int a = 0; a < 2; a++) {
            imagesByIds[a].clear();
            imagesByIdsTemp[a].clear();
        }
        imagesArrTemp.clear();
        currentUserAvatarLocation = null;
        containerView.setPadding(0, 0, 0, 0);
        if (currentThumb != null) {
            currentThumb.release();
        }
        currentThumb = object != null ? object.thumb : null;
        isEvent = object != null && object.isEvent;
        menuItem.setVisibility(View.VISIBLE);
        sendItem.setVisibility(View.GONE);
        pipItem.setVisibility(View.GONE);
        cameraItem.setVisibility(View.GONE);
        cameraItem.setTag(null);
        bottomLayout.setVisibility(View.VISIBLE);
        bottomLayout.setTag(1);
        bottomLayout.setTranslationY(0);
        captionTextView.setTranslationY(0);
        shareButton.setVisibility(View.GONE);
        if (qualityChooseView != null) {
            qualityChooseView.setVisibility(View.INVISIBLE);
            qualityPicker.setVisibility(View.INVISIBLE);
            qualityChooseView.setTag(null);
        }
        if (qualityChooseViewAnimation != null) {
            qualityChooseViewAnimation.cancel();
            qualityChooseViewAnimation = null;
        }
        allowShare = false;
        slideshowMessageId = 0;
        nameOverride = null;
        dateOverride = 0;
        menuItem.hideSubItem(gallery_menu_showall);
        menuItem.hideSubItem(gallery_menu_showinchat);
        menuItem.hideSubItem(gallery_menu_share);
        menuItem.hideSubItem(gallery_menu_openin);
        actionBar.setTranslationY(0);

        checkImageView.setAlpha(1.0f);
        checkImageView.setVisibility(View.GONE);
        actionBar.setTitleRightMargin(0);
        photosCounterView.setAlpha(1.0f);
        photosCounterView.setVisibility(View.GONE);

        pickerView.setVisibility(View.GONE);
        pickerViewSendButton.setVisibility(View.GONE);
        pickerViewSendButton.setTranslationY(0);
        pickerView.setAlpha(1.0f);
        pickerViewSendButton.setAlpha(1.0f);
        pickerView.setTranslationY(0);

        paintItem.setVisibility(View.GONE);
        cropItem.setVisibility(View.GONE);
        tuneItem.setVisibility(View.GONE);
        timeItem.setVisibility(View.GONE);

        videoTimelineView.setVisibility(View.GONE);
        compressItem.setVisibility(View.GONE);
        captionEditText.setVisibility(View.GONE);
        mentionListView.setVisibility(View.GONE);
        muteItem.setVisibility(View.GONE);
        actionBar.setSubtitle(null);
        masksItem.setVisibility(View.GONE);
        muteVideo = false;
        muteItem.setImageResource(R.drawable.volume_on);
        editorDoneLayout.setVisibility(View.GONE);
        captionTextView.setTag(null);
        captionTextView.setVisibility(View.INVISIBLE);
        if (photoCropView != null) {
            photoCropView.setVisibility(View.GONE);
        }
        if (photoFilterView != null) {
            photoFilterView.setVisibility(View.GONE);
        }

        for (int a = 0; a < 3; a++) {
            if (photoProgressViews[a] != null) {
                photoProgressViews[a].setBackgroundState(-1, false);
            }
        }

        if (messageObject != null && messages == null) {
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null) {
                TLRPC.WebPage webPage = messageObject.messageOwner.media.webpage;
                String siteName = webPage.site_name;
                if (siteName != null) {
                    siteName = siteName.toLowerCase();
                    if (siteName.equals("instagram") || siteName.equals("twitter") || "telegram_album".equals(webPage.type)) {
                        if (!TextUtils.isEmpty(webPage.author)) {
                            nameOverride = webPage.author;
                        }
                        if (webPage.cached_page instanceof TLRPC.TL_pageFull) {
                            for (int a = 0; a < webPage.cached_page.blocks.size(); a++) {
                                TLRPC.PageBlock block = webPage.cached_page.blocks.get(a);
                                if (block instanceof TLRPC.TL_pageBlockAuthorDate) {
                                    dateOverride = ((TLRPC.TL_pageBlockAuthorDate) block).published_date;
                                    break;
                                }
                            }
                        }
                        ArrayList<MessageObject> arrayList = messageObject.getWebPagePhotos(null, null);
                        if (!arrayList.isEmpty()) {
                            slideshowMessageId = messageObject.getId();
                            needSearchImageInArr = false;
                            imagesArr.addAll(arrayList);
                            totalImagesCount = imagesArr.size();
                            int idx = imagesArr.indexOf(messageObject);
                            if (idx < 0) {
                                idx = 0;
                            }
                            setImageIndex(idx, true);
                        }
                    }
                }
            }
            if (slideshowMessageId == 0) {
                imagesArr.add(messageObject);
                if (currentAnimation != null || messageObject.eventId != 0) {
                    needSearchImageInArr = false;
                } else if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)) {
                    needSearchImageInArr = true;
                    imagesByIds[0].put(messageObject.getId(), messageObject);
                    menuItem.showSubItem(gallery_menu_showall);
                    sendItem.setVisibility(View.VISIBLE);
                }
                setImageIndex(0, true);
            }
        } else if (documents != null) {
            secureDocuments.addAll(documents);
            setImageIndex(index, true);
        } else if (fileLocation != null) {
            avatarsDialogId = object.dialogId;
            imagesArrLocations.add(fileLocation);
            imagesArrLocationsSizes.add(object.size);
            avatarsArr.add(new TLRPC.TL_photoEmpty());
            shareButton.setVisibility(videoPlayerControlFrameLayout.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
            allowShare = true;
            menuItem.hideSubItem(gallery_menu_showall);
            if (shareButton.getVisibility() == View.VISIBLE) {
                menuItem.hideSubItem(gallery_menu_share);
            } else {
                menuItem.showSubItem(gallery_menu_share);
            }
            setImageIndex(0, true);
            currentUserAvatarLocation = fileLocation;
        } else if (messages != null) {
            opennedFromMedia = true;
            menuItem.showSubItem(gallery_menu_showinchat);
            sendItem.setVisibility(View.VISIBLE);
            imagesArr.addAll(messages);
            for (int a = 0; a < imagesArr.size(); a++) {
                MessageObject message = imagesArr.get(a);
                imagesByIds[message.getDialogId() == currentDialogId ? 0 : 1].put(message.getId(), message);
            }
            setImageIndex(index, true);
        } else if (photos != null) {
            if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && photos.size() > 1) {
                checkImageView.setVisibility(View.VISIBLE);
                photosCounterView.setVisibility(View.VISIBLE);
                actionBar.setTitleRightMargin(AndroidUtilities.dp(100));
            }
            if ((sendPhotoType == 2 || sendPhotoType == 5) && placeProvider.canCaptureMorePhotos()) {
                cameraItem.setVisibility(View.VISIBLE);
                cameraItem.setTag(1);
            }
            menuItem.setVisibility(View.GONE);
            imagesArrLocals.addAll(photos);
            Object obj = imagesArrLocals.get(index);
            boolean allowCaption;
            if (obj instanceof MediaController.PhotoEntry) {
                if (((MediaController.PhotoEntry) obj).isVideo) {
                    cropItem.setVisibility(View.GONE);
                    bottomLayout.setVisibility(View.VISIBLE);
                    bottomLayout.setTag(1);
                    bottomLayout.setTranslationY(-AndroidUtilities.dp(48));
                } else {
                    cropItem.setVisibility(View.VISIBLE);
                }
                allowCaption = true;
            } else if (obj instanceof TLRPC.BotInlineResult) {
                cropItem.setVisibility(View.GONE);
                allowCaption = false;
            } else {
                cropItem.setVisibility(obj instanceof MediaController.SearchImage && ((MediaController.SearchImage) obj).type == 0 ? View.VISIBLE : View.GONE);
                allowCaption = cropItem.getVisibility() == View.VISIBLE;
            }
            if (parentChatActivity != null && (parentChatActivity.currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(parentChatActivity.currentEncryptedChat.layer) >= 46)) {
                mentionsAdapter.setChatInfo(parentChatActivity.info);
                mentionsAdapter.setNeedUsernames(parentChatActivity.currentChat != null);
                mentionsAdapter.setNeedBotContext(false);
                needCaptionLayout = allowCaption && (placeProvider == null || placeProvider != null && placeProvider.allowCaption());
                captionEditText.setVisibility(needCaptionLayout ? View.VISIBLE : View.GONE);
                if (needCaptionLayout) {
                    captionEditText.onCreate();
                }
            }
            pickerView.setVisibility(View.VISIBLE);
            pickerViewSendButton.setVisibility(View.VISIBLE);
            pickerViewSendButton.setTranslationY(0);
            pickerViewSendButton.setAlpha(1.0f);
            bottomLayout.setVisibility(View.GONE);
            bottomLayout.setTag(null);
            containerView.setTag(null);
            setImageIndex(index, true);
            if (sendPhotoType != 4 && sendPhotoType != 5) {
                paintItem.setVisibility(cropItem.getVisibility());
                tuneItem.setVisibility(cropItem.getVisibility());
            } else {
                paintItem.setVisibility(View.GONE);
                tuneItem.setVisibility(View.GONE);
            }
            updateSelectedCount();
        }

        if (currentAnimation == null && !isEvent) {
            if (currentDialogId != 0 && totalImagesCount == 0) {
                DataQuery.getInstance(currentAccount).getMediaCount(currentDialogId, DataQuery.MEDIA_PHOTOVIDEO, classGuid, true);
                if (mergeDialogId != 0) {
                    DataQuery.getInstance(currentAccount).getMediaCount(mergeDialogId, DataQuery.MEDIA_PHOTOVIDEO, classGuid, true);
                }
            } else if (avatarsDialogId != 0) {
                MessagesController.getInstance(currentAccount).loadDialogPhotos(avatarsDialogId, 80, 0, true, classGuid);
            }
        }
        if (currentMessageObject != null && currentMessageObject.isVideo() || currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document))) {
            onActionClick(false);
        } else if (!imagesArrLocals.isEmpty()) {
            Object entry = imagesArrLocals.get(index);
            CharSequence caption = null;
            TLRPC.User user = parentChatActivity != null ? parentChatActivity.getCurrentUser() : null;
            boolean allowTimeItem = parentChatActivity != null && !parentChatActivity.isSecretChat() && user != null && !user.bot && !parentChatActivity.isEditingMessageMedia();
            if (entry instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) entry);
                if (photoEntry.isVideo) {
                    preparePlayer(Uri.fromFile(new File(photoEntry.path)), false, false);
                }
            } else if (allowTimeItem && entry instanceof MediaController.SearchImage) {
                allowTimeItem = ((MediaController.SearchImage) entry).type == 0;
            }
            if (allowTimeItem) {
                timeItem.setVisibility(View.VISIBLE);
            }
        }
    }

    public boolean isMuteVideo() {
        return muteVideo;
    }

    private void setImages() {
        if (animationInProgress == 0) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(rightImage, currentIndex + 1);
            setIndexToImage(leftImage, currentIndex - 1);
        }
    }

    private void setIsAboutToSwitchToIndex(int index, boolean init) {
        if (!init && switchingToIndex == index) {
            return;
        }
        switchingToIndex = index;

        boolean isVideo = false;
        boolean sameImage = false;
        CharSequence caption = null;
        String newFileName = getFileName(index);
        MessageObject newMessageObject = null;

        if (!imagesArr.isEmpty()) {
            if (switchingToIndex < 0 || switchingToIndex >= imagesArr.size()) {
                return;
            }
            newMessageObject = imagesArr.get(switchingToIndex);
            isVideo = newMessageObject.isVideo();
            boolean isInvoice = newMessageObject.isInvoice();
            if (isInvoice) {
                masksItem.setVisibility(View.GONE);
                menuItem.hideSubItem(gallery_menu_delete);
                menuItem.hideSubItem(gallery_menu_openin);
                caption = newMessageObject.messageOwner.media.description;
                allowShare = false;
                bottomLayout.setTranslationY(AndroidUtilities.dp(48));
                captionTextView.setTranslationY(AndroidUtilities.dp(48));
            } else {
                masksItem.setVisibility(newMessageObject.hasPhotoStickers() && (int) newMessageObject.getDialogId() != 0 ? View.VISIBLE : View.GONE);
                if (newMessageObject.canDeleteMessage(null) && slideshowMessageId == 0) {
                    menuItem.showSubItem(gallery_menu_delete);
                } else {
                    menuItem.hideSubItem(gallery_menu_delete);
                }
                if (isVideo) {
                    menuItem.showSubItem(gallery_menu_openin);
                    if (pipItem.getVisibility() != View.VISIBLE) {
                        pipItem.setVisibility(View.VISIBLE);
                    }
                    if (!pipAvailable) {
                        pipItem.setEnabled(false);
                        pipItem.setAlpha(0.5f);
                    }
                } else {
                    menuItem.hideSubItem(gallery_menu_openin);
                    if (pipItem.getVisibility() != View.GONE) {
                        pipItem.setVisibility(View.GONE);
                    }
                }
                if (nameOverride != null) {
                    nameTextView.setText(nameOverride);
                } else {
                    if (newMessageObject.isFromUser()) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(newMessageObject.messageOwner.from_id);
                        if (user != null) {
                            nameTextView.setText(UserObject.getUserName(user));
                        } else {
                            nameTextView.setText("");
                        }
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(newMessageObject.messageOwner.to_id.channel_id);
                        if (chat != null) {
                            nameTextView.setText(chat.title);
                        } else {
                            nameTextView.setText("");
                        }
                    }
                }
                long date;
                if (dateOverride != 0) {
                    date = (long) dateOverride * 1000;
                } else {
                    date = (long) newMessageObject.messageOwner.date * 1000;
                }
                String dateString = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(date)), LocaleController.getInstance().formatterDay.format(new Date(date)));
                if (newFileName != null && isVideo) {
                    dateTextView.setText(String.format("%s (%s)", dateString, AndroidUtilities.formatFileSize(newMessageObject.getDocument().size)));
                } else {
                    dateTextView.setText(dateString);
                }
                caption = newMessageObject.caption;
            }

            if (currentAnimation != null) {
                menuItem.hideSubItem(gallery_menu_save);
                menuItem.hideSubItem(gallery_menu_share);
                if (!newMessageObject.canDeleteMessage(null)) {
                    menuItem.setVisibility(View.GONE);
                }
                allowShare = true;
                shareButton.setVisibility(View.VISIBLE);
                actionBar.setTitle(LocaleController.getString("AttachGif", R.string.AttachGif));
            } else {
                if (totalImagesCount + totalImagesCountMerge != 0 && !needSearchImageInArr) {
                    if (opennedFromMedia) {
                        if (imagesArr.size() < totalImagesCount + totalImagesCountMerge && !loadingMoreImages && switchingToIndex > imagesArr.size() - 5) {
                            int loadFromMaxId = imagesArr.isEmpty() ? 0 : imagesArr.get(imagesArr.size() - 1).getId();
                            int loadIndex = 0;
                            if (endReached[loadIndex] && mergeDialogId != 0) {
                                loadIndex = 1;
                                if (!imagesArr.isEmpty() && imagesArr.get(imagesArr.size() - 1).getDialogId() != mergeDialogId) {
                                    loadFromMaxId = 0;
                                }
                            }

                            DataQuery.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 80, loadFromMaxId, DataQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                            loadingMoreImages = true;
                        }
                        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, switchingToIndex + 1, totalImagesCount + totalImagesCountMerge));
                    } else {
                        if (imagesArr.size() < totalImagesCount + totalImagesCountMerge && !loadingMoreImages && switchingToIndex < 5) {
                            int loadFromMaxId = imagesArr.isEmpty() ? 0 : imagesArr.get(0).getId();
                            int loadIndex = 0;
                            if (endReached[loadIndex] && mergeDialogId != 0) {
                                loadIndex = 1;
                                if (!imagesArr.isEmpty() && imagesArr.get(0).getDialogId() != mergeDialogId) {
                                    loadFromMaxId = 0;
                                }
                            }

                            DataQuery.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 80, loadFromMaxId, DataQuery.MEDIA_PHOTOVIDEO, true, classGuid);
                            loadingMoreImages = true;
                        }
                        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount + totalImagesCountMerge - imagesArr.size()) + switchingToIndex + 1, totalImagesCount + totalImagesCountMerge));
                    }
                } else if (slideshowMessageId == 0 && newMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                    if (newMessageObject.isVideo()) {
                        actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
                    } else {
                        actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
                    }
                } else if (isInvoice) {
                    actionBar.setTitle(newMessageObject.messageOwner.media.title);
                }
                if ((int) currentDialogId == 0) {
                    sendItem.setVisibility(View.GONE);
                }
                if (newMessageObject.messageOwner.ttl != 0 && newMessageObject.messageOwner.ttl < 60 * 60) {
                    allowShare = false;
                    menuItem.hideSubItem(gallery_menu_save);
                    shareButton.setVisibility(View.GONE);
                    menuItem.hideSubItem(gallery_menu_share);
                } else {
                    allowShare = true;
                    menuItem.showSubItem(gallery_menu_save);
                    shareButton.setVisibility(videoPlayerControlFrameLayout.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
                    if (shareButton.getVisibility() == View.VISIBLE) {
                        menuItem.hideSubItem(gallery_menu_share);
                    } else {
                        menuItem.showSubItem(gallery_menu_share);
                    }
                }
            }
            groupedPhotosListView.fillList();
        } else if (!secureDocuments.isEmpty()) {
            allowShare = false;
            menuItem.hideSubItem(gallery_menu_save);
            nameTextView.setText("");
            dateTextView.setText("");
            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, switchingToIndex + 1, secureDocuments.size()));
        } else if (!imagesArrLocations.isEmpty()) {
            if (index < 0 || index >= imagesArrLocations.size()) {
                return;
            }
            nameTextView.setText("");
            dateTextView.setText("");
            if (avatarsDialogId == UserConfig.getInstance(currentAccount).getClientUserId() && !avatarsArr.isEmpty()) {
                menuItem.showSubItem(gallery_menu_delete);
            } else {
                menuItem.hideSubItem(gallery_menu_delete);
            }
            if (isEvent) {
                actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
            } else {
                actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, switchingToIndex + 1, imagesArrLocations.size()));
            }
            menuItem.showSubItem(gallery_menu_save);
            allowShare = true;
            shareButton.setVisibility(videoPlayerControlFrameLayout.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
            if (shareButton.getVisibility() == View.VISIBLE) {
                menuItem.hideSubItem(gallery_menu_share);
            } else {
                menuItem.showSubItem(gallery_menu_share);
            }
            groupedPhotosListView.fillList();
        } else if (!imagesArrLocals.isEmpty()) {
            if (index < 0 || index >= imagesArrLocals.size()) {
                return;
            }
            Object object = imagesArrLocals.get(index);
            int ttl = 0;
            boolean isFiltered = false;
            boolean isPainted = false;
            boolean isCropped = false;
            if (object instanceof TLRPC.BotInlineResult) {
                TLRPC.BotInlineResult botInlineResult = currentBotInlineResult = ((TLRPC.BotInlineResult) object);
                if (botInlineResult.document != null) {
                    isVideo = MessageObject.isVideoDocument(botInlineResult.document);
                } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                    isVideo = botInlineResult.type.equals("video");
                }
            } else {
                String pathObject = null;
                boolean isAnimation = false;
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                    pathObject = photoEntry.path;
                    isVideo = photoEntry.isVideo;
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                    pathObject = searchImage.getPathToAttach();
                    if (searchImage.type == 1) {
                        isAnimation = true;
                    }
                }
                if (isVideo) {
                    muteItem.setVisibility(View.VISIBLE);
                    compressItem.setVisibility(View.VISIBLE);
                    isCurrentVideo = true;
                    boolean isMuted = false;
                    if (object instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                        isMuted = photoEntry.editedInfo != null && photoEntry.editedInfo.muted;
                    }
                    processOpenVideo(pathObject, isMuted);
                    videoTimelineView.setVisibility(View.VISIBLE);
                    paintItem.setVisibility(View.GONE);
                    cropItem.setVisibility(View.GONE);
                    tuneItem.setVisibility(View.GONE);
                } else {
                    videoTimelineView.setVisibility(View.GONE);
                    muteItem.setVisibility(View.GONE);
                    isCurrentVideo = false;
                    compressItem.setVisibility(View.GONE);
                    if (isAnimation) {
                        paintItem.setVisibility(View.GONE);
                        cropItem.setVisibility(View.GONE);
                        tuneItem.setVisibility(View.GONE);
                    } else {
                        if (sendPhotoType == 4 || sendPhotoType == 5) {
                            paintItem.setVisibility(View.GONE);
                            tuneItem.setVisibility(View.GONE);
                        } else {
                            paintItem.setVisibility(View.VISIBLE);
                            tuneItem.setVisibility(View.VISIBLE);
                        }
                        cropItem.setVisibility(View.VISIBLE);
                    }
                    actionBar.setSubtitle(null);
                }
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                    fromCamera = photoEntry.bucketId == 0 && photoEntry.dateTaken == 0 && imagesArrLocals.size() == 1;
                    caption = photoEntry.caption;
                    ttl = photoEntry.ttl;
                    isFiltered = photoEntry.isFiltered;
                    isPainted = photoEntry.isPainted;
                    isCropped = photoEntry.isCropped;
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                    caption = searchImage.caption;
                    ttl = searchImage.ttl;
                    isFiltered = searchImage.isFiltered;
                    isPainted = searchImage.isPainted;
                    isCropped = searchImage.isCropped;
                }
            }
            if (bottomLayout.getVisibility() != View.GONE) {
                bottomLayout.setVisibility(View.GONE);
            }
            bottomLayout.setTag(null);
            if (fromCamera) {
                if (isVideo) {
                    actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
                } else {
                    actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
                }
            } else {
                actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, switchingToIndex + 1, imagesArrLocals.size()));
            }
            if (parentChatActivity != null) {
                TLRPC.Chat chat = parentChatActivity.getCurrentChat();
                if (chat != null) {
                    actionBar.setTitle(chat.title);
                } else {
                    TLRPC.User user = parentChatActivity.getCurrentUser();
                    if (user != null) {
                        actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                    }
                }
            }
            if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                checkImageView.setChecked(placeProvider.isPhotoChecked(switchingToIndex), false);
            }

            updateCaptionTextForCurrentPhoto(object);
            PorterDuffColorFilter filter = new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY);
            timeItem.setColorFilter(ttl != 0 ?  filter : null);
            paintItem.setColorFilter(isPainted ? filter : null);
            cropItem.setColorFilter(isCropped ? filter : null);
            tuneItem.setColorFilter(isFiltered ? filter : null);
        }
        setCurrentCaption(newMessageObject, caption, !init);
    }

    private void setImageIndex(int index, boolean init) {
        if (currentIndex == index || placeProvider == null) {
            return;
        }
        if (!init) {
            if (currentThumb != null) {
                currentThumb.release();
                currentThumb = null;
            }
        }
        currentFileNames[0] = getFileName(index);
        currentFileNames[1] = getFileName(index + 1);
        currentFileNames[2] = getFileName(index - 1);
        placeProvider.willSwitchFromPhoto(currentMessageObject, currentFileLocation, currentIndex);

        int prevIndex = currentIndex;
        currentIndex = index;
        setIsAboutToSwitchToIndex(currentIndex, init);

        boolean isVideo = false;
        boolean sameImage = false;
        Uri videoPath = null;

        if (!imagesArr.isEmpty()) {
            if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                closePhoto(false, false);
                return;
            }
            MessageObject newMessageObject = imagesArr.get(currentIndex);
            sameImage = init && currentMessageObject != null && currentMessageObject.getId() == newMessageObject.getId();
            currentMessageObject = newMessageObject;
            isVideo = newMessageObject.isVideo();
        } else if (!secureDocuments.isEmpty()) {
            if (index < 0 || index >= secureDocuments.size()) {
                closePhoto(false, false);
                return;
            }
            currentSecureDocument = secureDocuments.get(index);
        } else if (!imagesArrLocations.isEmpty()) {
            if (index < 0 || index >= imagesArrLocations.size()) {
                closePhoto(false, false);
                return;
            }
            TLRPC.FileLocation old = currentFileLocation;
            TLRPC.FileLocation newLocation = imagesArrLocations.get(index);
            if (init && old != null && newLocation != null && old.local_id == newLocation.local_id && old.volume_id == newLocation.volume_id) {
                sameImage = true;
            }
            currentFileLocation = imagesArrLocations.get(index);
        } else if (!imagesArrLocals.isEmpty()) {
            if (index < 0 || index >= imagesArrLocals.size()) {
                closePhoto(false, false);
                return;
            }
            Object object = imagesArrLocals.get(index);
            if (object instanceof TLRPC.BotInlineResult) {
                TLRPC.BotInlineResult botInlineResult = currentBotInlineResult = ((TLRPC.BotInlineResult) object);
                if (botInlineResult.document != null) {
                    currentPathObject = FileLoader.getPathToAttach(botInlineResult.document).getAbsolutePath();
                    isVideo = MessageObject.isVideoDocument(botInlineResult.document);
                } else if (botInlineResult.photo != null) {
                    currentPathObject = FileLoader.getPathToAttach(FileLoader.getClosestPhotoSizeWithSize(botInlineResult.photo.sizes, AndroidUtilities.getPhotoSize())).getAbsolutePath();
                } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                    currentPathObject = botInlineResult.content.url;
                    isVideo = botInlineResult.type.equals("video");
                }
            } else {
                boolean isAnimation = false;
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                    currentPathObject = photoEntry.path;
                    isVideo = photoEntry.isVideo;
                    videoPath = Uri.fromFile(new File(photoEntry.path));
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                    currentPathObject = searchImage.getPathToAttach();
                }
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
            animationStartTime = 0;
            imageMoveAnimation = null;
            changeModeAnimation = null;
            if (aspectRatioFrameLayout != null) {
                aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
            }

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
            canZoom = !imagesArrLocals.isEmpty() || (currentFileNames[0] != null && !isVideo && photoProgressViews[0].backgroundState != 0);
            updateMinMax(scale);
            releasePlayer();
        }
        if (isVideo && videoPath != null) {
            isStreaming = false;
            preparePlayer(videoPath, false, false);
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

                PhotoProgressView tempProgress = photoProgressViews[0];
                photoProgressViews[0] = photoProgressViews[2];
                photoProgressViews[2] = tempProgress;
                setIndexToImage(leftImage, currentIndex - 1);

                checkProgress(1, false);
                checkProgress(2, false);
            } else if (prevIndex < currentIndex) {
                ImageReceiver temp = leftImage;
                leftImage = centerImage;
                centerImage = rightImage;
                rightImage = temp;

                PhotoProgressView tempProgress = photoProgressViews[0];
                photoProgressViews[0] = photoProgressViews[1];
                photoProgressViews[1] = tempProgress;
                setIndexToImage(rightImage, currentIndex + 1);

                checkProgress(1, false);
                checkProgress(2, false);
            }
        }
    }

    private void setCurrentCaption(MessageObject messageObject, final CharSequence caption, boolean animated) {
        if (needCaptionLayout) {
            if (captionTextView.getParent() != pickerView) {
                captionTextView.setBackgroundDrawable(null);
                containerView.removeView(captionTextView);
                pickerView.addView(captionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 76, 48));
            }
        } else {
            if (captionTextView.getParent() != containerView) {
                captionTextView.setBackgroundColor(0x7f000000);
                pickerView.removeView(captionTextView);
                containerView.addView(captionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));
            }
        }
        if (isCurrentVideo) {
            captionTextView.setMaxLines(1);
            captionTextView.setSingleLine(true);
        } else {
            captionTextView.setSingleLine(false);
            captionTextView.setMaxLines(10);
        }
        boolean wasVisisble = captionTextView.getTag() != null;
        if (!TextUtils.isEmpty(caption)) {
            Theme.createChatResources(null, true);
            CharSequence str;
            if (messageObject != null && !messageObject.messageOwner.entities.isEmpty()) {
                Spannable spannableString = SpannableString.valueOf(caption.toString());
                messageObject.addEntitiesToText(spannableString, true, false);
                str = Emoji.replaceEmoji(spannableString, captionTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            } else {
                str = Emoji.replaceEmoji(new SpannableStringBuilder(caption), captionTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            captionTextView.setTag(str);
            if (currentCaptionAnimation != null) {
                currentCaptionAnimation.cancel();
                currentCaptionAnimation = null;
            }
            /*if (animated) {
                TextView temp = switchCaptionTextView;
                switchCaptionTextView = captionTextView;
                captionTextView = temp;
                try {
                    switchCaptionTextView.setText(str);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }*/
            try {
                captionTextView.setText(str);
            } catch (Exception e) {
                FileLog.e(e);
            }
            captionTextView.setTextColor(0xffffffff);
            boolean visible = isActionBarVisible && (bottomLayout.getVisibility() == View.VISIBLE || pickerView.getVisibility() == View.VISIBLE);
            if (visible) {
                captionTextView.setVisibility(View.VISIBLE);
                if (animated && !wasVisisble) {
                    currentCaptionAnimation = new AnimatorSet();
                    currentCaptionAnimation.setDuration(200);
                    currentCaptionAnimation.setInterpolator(decelerateInterpolator);
                    currentCaptionAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(currentCaptionAnimation)) {
                                currentCaptionAnimation = null;
                            }
                        }
                    });
                    currentCaptionAnimation.playTogether(
                            ObjectAnimator.ofFloat(captionTextView, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(captionTextView, "translationY", AndroidUtilities.dp(5), 0.0f));
                    currentCaptionAnimation.start();
                } else {
                    captionTextView.setAlpha(1.0f);
                }
            } else if (captionTextView.getVisibility() == View.VISIBLE) {
                captionTextView.setVisibility(View.INVISIBLE);
                captionTextView.setAlpha(0.0f);
            }
        } else {
            if (needCaptionLayout) {
                captionTextView.setText(LocaleController.getString("AddCaption", R.string.AddCaption));
                captionTextView.setTag("empty");
                captionTextView.setVisibility(View.VISIBLE);
                captionTextView.setTextColor(0xb2ffffff);
            } else {
                captionTextView.setTextColor(0xffffffff);
                captionTextView.setTag(null);
                if (animated && wasVisisble) {
                    currentCaptionAnimation = new AnimatorSet();
                    currentCaptionAnimation.setDuration(200);
                    currentCaptionAnimation.setInterpolator(decelerateInterpolator);
                    currentCaptionAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(currentCaptionAnimation)) {
                                captionTextView.setVisibility(View.INVISIBLE);
                                currentCaptionAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (animation.equals(currentCaptionAnimation)) {
                                currentCaptionAnimation = null;
                            }
                        }
                    });
                    currentCaptionAnimation.playTogether(
                            ObjectAnimator.ofFloat(captionTextView, "alpha", 0.0f),
                            ObjectAnimator.ofFloat(captionTextView, "translationY", AndroidUtilities.dp(5)));
                    currentCaptionAnimation.start();
                } else {
                    captionTextView.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void checkProgress(int a, boolean animated) {
        int index = currentIndex;
        if (a == 1) {
            index += 1;
        } else if (a == 2) {
            index -= 1;
        }
        if (currentFileNames[a] != null) {
            File f = null;
            boolean isVideo = false;
            boolean canStream = false;
            if (currentMessageObject != null) {
                if (index < 0 || index >= imagesArr.size()) {
                    photoProgressViews[a].setBackgroundState(-1, animated);
                    return;
                }
                MessageObject messageObject = imagesArr.get(index);
                if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
                    f = new File(messageObject.messageOwner.attachPath);
                    if (!f.exists()) {
                        f = null;
                    }
                }
                if (f == null) {
                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.document == null) {
                        TLObject fileLocation = getFileLocation(index, null);
                        f = FileLoader.getPathToAttach(fileLocation, true);
                    } else {
                        f = FileLoader.getPathToMessage(messageObject.messageOwner);
                    }
                }
                canStream = SharedConfig.streamMedia && messageObject.isVideo() && (int) messageObject.getDialogId() != 0;
                isVideo = messageObject.isVideo();
            } else if (currentBotInlineResult != null) {
                if (index < 0 || index >= imagesArrLocals.size()) {
                    photoProgressViews[a].setBackgroundState(-1, animated);
                    return;
                }
                TLRPC.BotInlineResult botInlineResult = (TLRPC.BotInlineResult) imagesArrLocals.get(index);
                if (botInlineResult.type.equals("video") || MessageObject.isVideoDocument(botInlineResult.document)) {
                    if (botInlineResult.document != null) {
                        f = FileLoader.getPathToAttach(botInlineResult.document);
                    } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                        f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(botInlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(botInlineResult.content.url, "mp4"));
                    }
                    isVideo = true;
                } else if (botInlineResult.document != null) {
                    f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), currentFileNames[a]);
                } else if (botInlineResult.photo != null) {
                    f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE), currentFileNames[a]);
                }
                if (f == null || !f.exists()) {
                    f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFileNames[a]);
                }
            } else if (currentFileLocation != null) {
                if (index < 0 || index >= imagesArrLocations.size()) {
                    photoProgressViews[a].setBackgroundState(-1, animated);
                    return;
                }
                TLRPC.FileLocation location = imagesArrLocations.get(index);
                f = FileLoader.getPathToAttach(location, avatarsDialogId != 0 || isEvent);
            } else if (currentSecureDocument != null) {
                if (index < 0 || index >= secureDocuments.size()) {
                    photoProgressViews[a].setBackgroundState(-1, animated);
                    return;
                }
                SecureDocument location = secureDocuments.get(index);
                f = FileLoader.getPathToAttach(location, true);
            } else if (currentPathObject != null) {
                f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), currentFileNames[a]);
                if (!f.exists()) {
                    f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFileNames[a]);
                }
            }
            boolean exists = f.exists();
            if (f != null && (exists || canStream)) {
                if (isVideo) {
                    photoProgressViews[a].setBackgroundState(3, animated);
                } else {
                    photoProgressViews[a].setBackgroundState(-1, animated);
                }
                if (a == 0) {
                    if (!exists) {
                        if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[a])) {
                            menuItem.hideSubItem(gallery_menu_cancel_loading);
                        } else {
                            menuItem.showSubItem(gallery_menu_cancel_loading);
                        }
                    } else {
                        menuItem.hideSubItem(gallery_menu_cancel_loading);
                    }
                }
            } else {
                if (isVideo) {
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[a])) {
                        photoProgressViews[a].setBackgroundState(2, false);
                    } else {
                        photoProgressViews[a].setBackgroundState(1, false);
                    }
                } else {
                    photoProgressViews[a].setBackgroundState(0, animated);
                }
                Float progress = ImageLoader.getInstance().getFileProgress(currentFileNames[a]);
                if (progress == null) {
                    progress = 0.0f;
                }
                photoProgressViews[a].setProgress(progress, false);
            }
            if (a == 0) {
                canZoom = !imagesArrLocals.isEmpty() || (currentFileNames[0] != null && !isVideo && photoProgressViews[0].backgroundState != 0);
            }
        } else {
            boolean isLocalVideo = false;
            if (!imagesArrLocals.isEmpty() && index >= 0 && index < imagesArrLocals.size()) {
                Object object = imagesArrLocals.get(index);
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                    isLocalVideo = photoEntry.isVideo;
                }
            }
            if (isLocalVideo) {
                photoProgressViews[a].setBackgroundState(3, animated);
            } else {
                photoProgressViews[a].setBackgroundState(-1, animated);
            }
        }
    }

    public int getSelectiongLength() {
        return captionEditText != null ? captionEditText.getSelectionLength() : 0;
    }

    private void setIndexToImage(ImageReceiver imageReceiver, int index) {
        imageReceiver.setOrientation(0, false);
        if (!secureDocuments.isEmpty()) {
            imageReceiver.setParentMessageObject(null);
            if (index >= 0 && index < secureDocuments.size()) {
                Object object = secureDocuments.get(index);
                int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                ImageReceiver.BitmapHolder placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                if (placeHolder == null) {
                    placeHolder = placeProvider.getThumbForPhoto(null, null, index);
                }
                SecureDocument document = secureDocuments.get(index);
                int imageSize = document.secureFile.size;
                imageReceiver.setImage(document, null, "d", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, null, null, imageSize, null, 0);
            }
        } else if (!imagesArrLocals.isEmpty()) {
            imageReceiver.setParentMessageObject(null);
            if (index >= 0 && index < imagesArrLocals.size()) {
                Object object = imagesArrLocals.get(index);
                int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                ImageReceiver.BitmapHolder placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                if (placeHolder == null) {
                    placeHolder = placeProvider.getThumbForPhoto(null, null, index);
                }
                String path = null;
                TLRPC.Document document = null;
                WebFile webDocument = null;
                TLRPC.FileLocation photo = null;
                int imageSize = 0;
                String filter = null;
                boolean isVideo = false;
                int cacheType = 0;
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                    isVideo = photoEntry.isVideo;
                    if (!photoEntry.isVideo) {
                        if (photoEntry.imagePath != null) {
                            path = photoEntry.imagePath;
                        } else {
                            imageReceiver.setOrientation(photoEntry.orientation, false);
                            path = photoEntry.path;
                        }
                        filter = String.format(Locale.US, "%d_%d", size, size);
                    } else {
                        if (photoEntry.thumbPath != null) {
                            path = photoEntry.thumbPath;
                        } else {
                            path = "vthumb://" + photoEntry.imageId + ":" + photoEntry.path;
                        }
                    }
                } else if (object instanceof TLRPC.BotInlineResult) {
                    cacheType = 1;
                    TLRPC.BotInlineResult botInlineResult = ((TLRPC.BotInlineResult) object);
                    if (botInlineResult.type.equals("video") || MessageObject.isVideoDocument(botInlineResult.document)) {
                        if (botInlineResult.document != null) {
                            photo = botInlineResult.document.thumb.location;
                        } else if (botInlineResult.thumb instanceof TLRPC.TL_webDocument) {
                            webDocument = WebFile.createWithWebDocument(botInlineResult.thumb);
                        }
                    } else if (botInlineResult.type.equals("gif") && botInlineResult.document != null) {
                        document = botInlineResult.document;
                        imageSize = botInlineResult.document.size;
                        filter = "d";
                    } else if (botInlineResult.photo != null) {
                        TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(botInlineResult.photo.sizes, AndroidUtilities.getPhotoSize());
                        photo = sizeFull.location;
                        imageSize = sizeFull.size;
                        filter = String.format(Locale.US, "%d_%d", size, size);
                    } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                        if (botInlineResult.type.equals("gif")) {
                            filter = "d";
                        } else {
                            filter = String.format(Locale.US, "%d_%d", size, size);
                        }
                        webDocument = WebFile.createWithWebDocument(botInlineResult.content);
                    }
                } else if (object instanceof MediaController.SearchImage) {
                    cacheType = 1;
                    MediaController.SearchImage photoEntry = (MediaController.SearchImage) object;
                    if (photoEntry.photoSize != null) {
                        photo = photoEntry.photoSize.location;
                        imageSize = photoEntry.photoSize.size;
                    } else if (photoEntry.imagePath != null) {
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
                    imageReceiver.setImage(document, null, "d", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, placeHolder == null ? document.thumb.location : null, String.format(Locale.US, "%d_%d", size, size), imageSize, null, cacheType);
                } else if (photo != null) {
                    imageReceiver.setImage(photo, null, filter, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, null, String.format(Locale.US, "%d_%d", size, size), imageSize, null, cacheType);
                } else if (webDocument != null) {
                    imageReceiver.setImage(webDocument, filter, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : (isVideo && parentActivity != null ? parentActivity.getResources().getDrawable(R.drawable.nophotos) : null), null, cacheType);
                } else {
                    imageReceiver.setImage(path, filter, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : (isVideo && parentActivity != null ? parentActivity.getResources().getDrawable(R.drawable.nophotos) : null), null, imageSize);
                }
            } else {
                imageReceiver.setImageBitmap((Bitmap) null);
            }
        } else {
            int size[] = new int[1];
            TLObject fileLocation = getFileLocation(index, size);

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
                        ImageReceiver.BitmapHolder placeHolder = null;
                        if (currentThumb != null && imageReceiver == centerImage) {
                            placeHolder = currentThumb;
                        }
                        TLRPC.PhotoSize thumbLocation = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
                        imageReceiver.setImage(null, null, null, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, thumbLocation.location, "b", 0, null, 1);
                    } else {
                        imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                    }
                } else if (messageObject != null && currentAnimation != null) {
                    imageReceiver.setImageBitmap(currentAnimation);
                    currentAnimation.setSecondParentView(containerView);
                } else {
                    imageReceiver.setNeedsQualityThumb(true);
                    ImageReceiver.BitmapHolder placeHolder = null;
                    if (currentThumb != null && imageReceiver == centerImage) {
                        placeHolder = currentThumb;
                    }
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    TLRPC.PhotoSize thumbLocation = messageObject != null ? FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100) : null;
                    if (thumbLocation != null && thumbLocation.location == fileLocation) {
                        thumbLocation = null;
                    }
                    boolean cacheOnly = messageObject != null && messageObject.isWebpage() || avatarsDialogId != 0 || isEvent;
                    imageReceiver.setImage(fileLocation, null, null, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, thumbLocation != null ? thumbLocation.location : null, "b", size[0], null, cacheOnly ? 1 : 0);
                }
            } else {
                imageReceiver.setNeedsQualityThumb(true);
                imageReceiver.setParentMessageObject(null);
                if (size[0] == 0) {
                    imageReceiver.setImageBitmap((Bitmap) null);
                } else {
                    imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                }
            }
        }
    }

    public static boolean isShowingImage(MessageObject object) {
        boolean result = false;
        if (Instance != null) {
            result = !Instance.pipAnimationInProgress && Instance.isVisible && !Instance.disableShowCheck && object != null && Instance.currentMessageObject != null && Instance.currentMessageObject.getId() == object.getId();
        }
        if (!result && PipInstance != null) {
            result = PipInstance.isVisible && !PipInstance.disableShowCheck && object != null && PipInstance.currentMessageObject != null && PipInstance.currentMessageObject.getId() == object.getId();
        }
        return result;
    }

    public static boolean isShowingImage(TLRPC.FileLocation object) {
        boolean result = false;
        if (Instance != null) {
            result = Instance.isVisible && !Instance.disableShowCheck && object != null && Instance.currentFileLocation != null && object.local_id == Instance.currentFileLocation.local_id && object.volume_id == Instance.currentFileLocation.volume_id && object.dc_id == Instance.currentFileLocation.dc_id;
        }
        return result;
    }

    public static boolean isShowingImage(TLRPC.BotInlineResult object) {
        boolean result = false;
        if (Instance != null) {
            result = Instance.isVisible && !Instance.disableShowCheck && object != null && Instance.currentBotInlineResult != null && object.id == Instance.currentBotInlineResult.id;
        }
        return result;
    }

    public static boolean isShowingImage(String object) {
        boolean result = false;
        if (Instance != null) {
            result = Instance.isVisible && !Instance.disableShowCheck && object != null && Instance.currentPathObject != null && object.equals(Instance.currentPathObject);
        }
        return result;
    }

    public void setParentChatActivity(ChatActivity chatActivity) {
        parentChatActivity = chatActivity;
    }

    public void setMaxSelectedPhotos(int value) {
        maxSelectedPhotos = value;
    }

    public boolean openPhoto(final MessageObject messageObject, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        return openPhoto(messageObject, null, null, null, null, 0, provider, null, dialogId, mergeDialogId);
    }

    public boolean openPhoto(final TLRPC.FileLocation fileLocation, final PhotoViewerProvider provider) {
        return openPhoto(null, fileLocation, null, null, null, 0, provider, null, 0, 0);
    }

    public boolean openPhoto(final ArrayList<MessageObject> messages, final int index, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        return openPhoto(messages.get(index), null, messages, null, null, index, provider, null, dialogId, mergeDialogId);
    }

    public boolean openPhoto(final ArrayList<SecureDocument> documents, final int index, final PhotoViewerProvider provider) {
        return openPhoto(null, null, null, documents, null, index, provider, null, 0, 0);
    }

    public boolean openPhotoForSelect(final ArrayList<Object> photos, final int index, int type, final PhotoViewerProvider provider, ChatActivity chatActivity) {
        sendPhotoType = type;
        if (pickerViewSendButton != null) {
            FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) pickerViewSendButton.getLayoutParams();
            if (sendPhotoType == 4 || sendPhotoType == 5) {
                pickerViewSendButton.setImageResource(R.drawable.ic_send);
                pickerViewSendButton.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
                layoutParams2.bottomMargin = AndroidUtilities.dp(19);
            } else if (sendPhotoType == 1) {
                pickerViewSendButton.setImageResource(R.drawable.bigcheck);
                pickerViewSendButton.setPadding(0, AndroidUtilities.dp(1), 0, 0);
                layoutParams2.bottomMargin = AndroidUtilities.dp(19);
            } else {
                pickerViewSendButton.setImageResource(R.drawable.ic_send);
                pickerViewSendButton.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
                layoutParams2.bottomMargin = AndroidUtilities.dp(14);
            }
            pickerViewSendButton.setLayoutParams(layoutParams2);
        }
        return openPhoto(null, null, null, null, photos, index, provider, chatActivity, 0, 0);
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

    public boolean openPhoto(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ArrayList<MessageObject> messages, final ArrayList<SecureDocument> documents, final ArrayList<Object> photos, final int index, final PhotoViewerProvider provider, ChatActivity chatActivity, long dialogId, long mDialogId) {
        if (parentActivity == null || isVisible || provider == null && checkAnimation() || messageObject == null && fileLocation == null && messages == null && photos == null && documents == null) {
            return false;
        }

        final PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, fileLocation, index);
        if (object == null && photos == null) {
            return false;
        }
        lastInsets = null;
        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        if (attachedToWindow) {
            try {
                wm.removeView(windowView);
            } catch (Exception e) {
                //don't promt
            }
        }

        try {
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            if (Build.VERSION.SDK_INT >= 21) {
                windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            } else {
                windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
            windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
            windowView.setFocusable(false);
            containerView.setFocusable(false);
            wm.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }

        doneButtonPressed = false;
        parentChatActivity = chatActivity;

        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FilePreparingFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileNewChunkAvailable);

        placeProvider = provider;
        mergeDialogId = mDialogId;
        currentDialogId = dialogId;
        selectedPhotosAdapter.notifyDataSetChanged();

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        isVisible = true;
        toggleActionBar(true, false);
        togglePhotosListView(false, false);

        if (object != null) {
            disableShowCheck = true;
            animationInProgress = 1;
            if (messageObject != null) {
                currentAnimation = object.imageReceiver.getAnimation();
            }

            onPhotoShow(messageObject, fileLocation, messages, documents, photos, index, object);

            final Rect drawRegion = object.imageReceiver.getDrawRegion();
            int orientation = object.imageReceiver.getOrientation();
            int animatedOrientation = object.imageReceiver.getAnimatedOrientation();
            if (animatedOrientation != 0) {
                orientation = animatedOrientation;
            }

            animatingImageView.setVisibility(View.VISIBLE);
            animatingImageView.setRadius(object.radius);
            animatingImageView.setOrientation(orientation);
            animatingImageView.setNeedRadius(object.radius != 0);
            animatingImageView.setImageBitmap(object.thumb);

            animatingImageView.setAlpha(1.0f);
            animatingImageView.setPivotX(0.0f);
            animatingImageView.setPivotY(0.0f);
            animatingImageView.setScaleX(object.scale);
            animatingImageView.setScaleY(object.scale);
            animatingImageView.setTranslationX(object.viewX + drawRegion.left * object.scale);
            animatingImageView.setTranslationY(object.viewY + drawRegion.top * object.scale);
            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            layoutParams.width = (drawRegion.right - drawRegion.left);
            layoutParams.height = (drawRegion.bottom - drawRegion.top);
            animatingImageView.setLayoutParams(layoutParams);

            float scaleX = (float) AndroidUtilities.displaySize.x / layoutParams.width;
            float scaleY = (float) (AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)) / layoutParams.height;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            float width = layoutParams.width * scale;
            float height = layoutParams.height * scale;
            float xPos = (AndroidUtilities.displaySize.x - width) / 2.0f;
            float yPos = ((AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)) - height) / 2.0f;
            int clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
            int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());

            int coords2[] = new int[2];
            object.parentView.getLocationInWindow(coords2);
            int clipTop = coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition;
            if (clipTop < 0) {
                clipTop = 0;
            }
            int clipBottom = (object.viewY + drawRegion.top + layoutParams.height) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition;
            if (clipBottom < 0) {
                clipBottom = 0;
            }
            clipTop = Math.max(clipTop, clipVertical);
            clipBottom = Math.max(clipBottom, clipVertical);

            animationValues[0][0] = animatingImageView.getScaleX();
            animationValues[0][1] = animatingImageView.getScaleY();
            animationValues[0][2] = animatingImageView.getTranslationX();
            animationValues[0][3] = animatingImageView.getTranslationY();
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
            containerView.setAlpha(0);

            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(animatingImageView, "animationProgress", 0.0f, 1.0f),
                    ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0, 255),
                    ObjectAnimator.ofFloat(containerView, "alpha", 0.0f, 1.0f)
            );

            animationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (containerView == null || windowView == null) {
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
                    if (photos != null && sendPhotoType != 3) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                        } else {
                            windowLayoutParams.flags = 0;
                        }
                        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
                        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                        wm.updateViewLayout(windowView, windowLayoutParams);
                        windowView.setFocusable(true);
                        containerView.setFocusable(true);
                    }
                }
            };

            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance(currentAccount).setAnimationInProgress(false);
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
                    NotificationCenter.getInstance(currentAccount).setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoaded, NotificationCenter.mediaDidLoaded, NotificationCenter.dialogPhotosLoaded});
                    NotificationCenter.getInstance(currentAccount).setAnimationInProgress(true);
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
            if (photos != null && sendPhotoType != 3) {
                if (Build.VERSION.SDK_INT >= 21) {
                    windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                } else {
                    windowLayoutParams.flags = 0;
                }
                windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
                wm.updateViewLayout(windowView, windowLayoutParams);
                windowView.setFocusable(true);
                containerView.setFocusable(true);
            }

            backgroundDrawable.setAlpha(255);
            containerView.setAlpha(1.0f);
            onPhotoShow(messageObject, fileLocation, messages, documents, photos, index, object);
        }
        return true;
    }

    public void closePhoto(boolean animated, boolean fromEditMode) {
        if (!fromEditMode && currentEditMode != 0) {
            if (currentEditMode == 3 && photoPaintView != null) {
                photoPaintView.maybeShowDismissalAlert(this, parentActivity, () -> switchToEditMode(0));
                return;
            }
            if (currentEditMode == 1) {
                photoCropView.cancelAnimationRunnable();
            }
            switchToEditMode(0);
            return;
        }
        if (qualityChooseView != null && qualityChooseView.getTag() != null) {
            qualityPicker.cancelButton.callOnClick();
            return;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (Build.VERSION.SDK_INT >= 21 && actionBar != null) {
            if ((windowLayoutParams.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                windowLayoutParams.flags &=~ WindowManager.LayoutParams.FLAG_FULLSCREEN;
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.updateViewLayout(windowView, windowLayoutParams);
            }
        }
        if (currentEditMode != 0) {
            if (currentEditMode == 2) {
                photoFilterView.shutdown();
                containerView.removeView(photoFilterView);
                photoFilterView = null;
            } else if (currentEditMode == 1) {
                editorDoneLayout.setVisibility(View.GONE);
                photoCropView.setVisibility(View.GONE);
            } else if (currentEditMode == 3) {
                photoPaintView.shutdown();
                containerView.removeView(photoPaintView);
                photoPaintView = null;
            }
            currentEditMode = 0;
        }

        if (parentActivity == null || !isInline && !isVisible || checkAnimation() || placeProvider == null) {
            return;
        }
        if (captionEditText.hideActionMode() && !fromEditMode) {
            return;
        }

        releasePlayer();
        captionEditText.onDestroy();
        parentChatActivity = null;
        removeObservers();

        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }

        final PlaceProviderObject object = placeProvider.getPlaceForPhoto(currentMessageObject, currentFileLocation, currentIndex);

        if (isInline) {
            isInline = false;
            animationInProgress = 0;
            onPhotoClosed(object);
            containerView.setScaleX(1.0f);
            containerView.setScaleY(1.0f);
        } else {
            if (animated) {
                animationInProgress = 1;
                animatingImageView.setVisibility(View.VISIBLE);
                containerView.invalidate();

                AnimatorSet animatorSet = new AnimatorSet();

                final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
                Rect drawRegion = null;
                int orientation = centerImage.getOrientation();
                int animatedOrientation = 0;
                if (object != null && object.imageReceiver != null) {
                    animatedOrientation = object.imageReceiver.getAnimatedOrientation();
                }
                if (animatedOrientation != 0) {
                    orientation = animatedOrientation;
                }
                animatingImageView.setOrientation(orientation);
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
                    animatingImageView.setImageBitmap(centerImage.getBitmapSafe());
                }
                animatingImageView.setLayoutParams(layoutParams);

                float scaleX = (float) AndroidUtilities.displaySize.x / layoutParams.width;
                float scaleY = (float) (AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)) / layoutParams.height;
                float scale2 = scaleX > scaleY ? scaleY : scaleX;
                float width = layoutParams.width * scale * scale2;
                float height = layoutParams.height * scale * scale2;
                float xPos = (AndroidUtilities.displaySize.x - width) / 2.0f;
                float yPos = ((AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)) - height) / 2.0f;
                animatingImageView.setTranslationX(xPos + translationX);
                animatingImageView.setTranslationY(yPos + translationY);
                animatingImageView.setScaleX(scale * scale2);
                animatingImageView.setScaleY(scale * scale2);

                if (object != null) {
                    object.imageReceiver.setVisible(false, true);
                    int clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
                    int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());

                    int coords2[] = new int[2];
                    object.parentView.getLocationInWindow(coords2);
                    int clipTop = coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition;
                    if (clipTop < 0) {
                        clipTop = 0;
                    }
                    int clipBottom = (object.viewY + drawRegion.top + (drawRegion.bottom - drawRegion.top)) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition;
                    if (clipBottom < 0) {
                        clipBottom = 0;
                    }

                    clipTop = Math.max(clipTop, clipVertical);
                    clipBottom = Math.max(clipBottom, clipVertical);

                    animationValues[0][0] = animatingImageView.getScaleX();
                    animationValues[0][1] = animatingImageView.getScaleY();
                    animationValues[0][2] = animatingImageView.getTranslationX();
                    animationValues[0][3] = animatingImageView.getTranslationY();
                    animationValues[0][4] = 0;
                    animationValues[0][5] = 0;
                    animationValues[0][6] = 0;
                    animationValues[0][7] = 0;

                    animationValues[1][0] = object.scale;
                    animationValues[1][1] = object.scale;
                    animationValues[1][2] = object.viewX + drawRegion.left * object.scale - getLeftInset();
                    animationValues[1][3] = object.viewY + drawRegion.top * object.scale;
                    animationValues[1][4] = clipHorizontal * object.scale;
                    animationValues[1][5] = clipTop * object.scale;
                    animationValues[1][6] = clipBottom * object.scale;
                    animationValues[1][7] = object.radius;

                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(animatingImageView, "animationProgress", 0.0f, 1.0f),
                            ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                            ObjectAnimator.ofFloat(containerView, "alpha", 0.0f)
                    );
                } else {
                    int h = (AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));
                    animatorSet.playTogether(
                            ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                            ObjectAnimator.ofFloat(animatingImageView, "alpha", 0.0f),
                            ObjectAnimator.ofFloat(animatingImageView, "translationY", translationY >= 0 ? h : -h),
                            ObjectAnimator.ofFloat(containerView, "alpha", 0.0f)
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
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
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
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(containerView, "scaleX", 0.9f),
                        ObjectAnimator.ofFloat(containerView, "scaleY", 0.9f),
                        ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofFloat(containerView, "alpha", 0.0f)
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
                        containerView.setScaleX(1.0f);
                        containerView.setScaleY(1.0f);
                    }
                };
                animatorSet.setDuration(200);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
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
            if (currentAnimation != null) {
                currentAnimation.setSecondParentView(null);
                currentAnimation = null;
                centerImage.setImageBitmap((Drawable) null);
            }
            if (placeProvider != null && !placeProvider.canScrollAway()) {
                placeProvider.cancelButtonPressed();
            }
        }
    }

    private void removeObservers() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaCountDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FilePreparingFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileNewChunkAvailable);
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(classGuid);
    }

    public void destroyPhotoViewer() {
        if (parentActivity == null || windowView == null) {
            return;
        }
        if (pipVideoView != null) {
            pipVideoView.close();
            pipVideoView = null;
        }
        removeObservers();
        releasePlayer();
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeViewImmediate(windowView);
            }
            windowView = null;
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (currentThumb != null) {
            currentThumb.release();
            currentThumb = null;
        }
        animatingImageView.setImageBitmap(null);
        if (captionEditText != null) {
            captionEditText.onDestroy();
        }
        if (this == PipInstance) {
            PipInstance = null;
        } else {
            Instance = null;
        }
    }

    private void onPhotoClosed(PlaceProviderObject object) {
        isVisible = false;
        disableShowCheck = true;
        currentMessageObject = null;
        currentBotInlineResult = null;
        currentFileLocation = null;
        currentSecureDocument = null;
        currentPathObject = null;
        if (currentThumb != null) {
            currentThumb.release();
            currentThumb = null;
        }
        parentAlert = null;
        if (currentAnimation != null) {
            currentAnimation.setSecondParentView(null);
            currentAnimation = null;
        }
        for (int a = 0; a < 3; a++) {
            if (photoProgressViews[a] != null) {
                photoProgressViews[a].setBackgroundState(-1, false);
            }
        }
        requestVideoPreview(0);
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
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
                    FileLog.e(e);
                }
            }
        });
        if (placeProvider != null) {
            placeProvider.willHidePhotoViewer();
        }
        groupedPhotosListView.clear();
        placeProvider = null;
        selectedPhotosAdapter.notifyDataSetChanged();
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
        if (videoPlayer != null) {
            videoPlayer.seekTo(videoPlayer.getCurrentPosition() + 1);
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (pipVideoView != null) {
            pipVideoView.onConfigurationChanged();
        }
    }

    public void onPause() {
        if (currentAnimation != null) {
            closePhoto(false, false);
            return;
        }
        if (lastTitle != null) {
            closeCaptionEnter(true);
        }
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
        if (currentEditMode != 0 && currentEditMode != 3) {
            return AndroidUtilities.dp(14);
        }
        return 0;
    }

    private int getAdditionY() {
        if (currentEditMode == 3) {
            return AndroidUtilities.dp(8) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        } else if (currentEditMode != 0) {
            return AndroidUtilities.dp(14) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        }
        return 0;
    }

    private int getContainerViewWidth() {
        return getContainerViewWidth(currentEditMode);
    }

    private int getContainerViewWidth(int mode) {
        int width = containerView.getWidth();
        if (mode != 0 && mode != 3) {
            width -= AndroidUtilities.dp(28);
        }
        return width;
    }

    private int getContainerViewHeight() {
        return getContainerViewHeight(currentEditMode);
    }

    private int getContainerViewHeight(int mode) {
        int height = AndroidUtilities.displaySize.y;
        if (mode == 0 && Build.VERSION.SDK_INT >= 21) {
            height += AndroidUtilities.statusBarHeight;
        }
        if (mode == 1) {
            height -= AndroidUtilities.dp(48 + 32 + 64);
        } else if (mode == 2) {
            height -= AndroidUtilities.dp(154 + 60);
        } else if (mode == 3) {
            height -= AndroidUtilities.dp(48) + ActionBar.getCurrentActionBarHeight();
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
            return true;
        }

        if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                closeCaptionEnter(true);
            }
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
                    if (qualityChooseView != null && qualityChooseView.getVisibility() == View.VISIBLE) {
                        return true;
                    }
                }
                if (placeProvider.canScrollAway() && currentEditMode == 0 && canDragDown && !draggingDown && scale == 1 && dy >= AndroidUtilities.dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible && containerView.getTag() != null) {
                        toggleActionBar(false, true);
                    } else if (pickerView.getVisibility() == View.VISIBLE) {
                        toggleActionBar(false, true);
                        togglePhotosListView(false, true);
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
        animateTo(scale, minX - getContainerViewWidth() - extra - AndroidUtilities.dp(30) / 2, translationY, false);
    }

    private void goToPrev() {
        float extra = 0;
        if (scale != 1) {
            extra = (getContainerViewWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + getContainerViewWidth() + extra + AndroidUtilities.dp(30) / 2, translationY, false);
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
        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(
                ObjectAnimator.ofFloat(this, "animationValue", 0, 1)
        );
        imageMoveAnimation.setInterpolator(interpolator);
        imageMoveAnimation.setDuration(duration);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageMoveAnimation = null;
                containerView.invalidate();
            }
        });
        imageMoveAnimation.start();
    }

    @Keep
    public void setAnimationValue(float value) {
        animationValue = value;
        containerView.invalidate();
    }

    @Keep
    public float getAnimationValue() {
        return animationValue;
    }

    private void hideHint() {
        hintAnimation = new AnimatorSet();
        hintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f)
        );
        hintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintAnimation = null;
                    hintHideRunnable = null;
                    if (hintTextView != null) {
                        hintTextView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintHideRunnable = null;
                    hintHideRunnable = null;
                }
            }
        });
        hintAnimation.setDuration(300);
        hintAnimation.start();
    }

    private void showHint(boolean hide, boolean enabled) {
        if (containerView == null || hide && hintTextView == null) {
            return;
        }
        if (hintTextView == null) {
            hintTextView = new TextView(containerView.getContext());
            hintTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), 0xcc111111));
            hintTextView.setTextColor(0xffffffff);
            hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            hintTextView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
            hintTextView.setGravity(Gravity.CENTER_VERTICAL);
            hintTextView.setAlpha(0.0f);
            containerView.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 5, 0, 5, 3));
        }
        if (hide) {
            if (hintAnimation != null) {
                hintAnimation.cancel();
                hintAnimation = null;
            }
            AndroidUtilities.cancelRunOnUIThread(hintHideRunnable);
            hintHideRunnable = null;
            hideHint();
            return;
        }

        hintTextView.setText(enabled ? LocaleController.getString("GroupPhotosHelp", R.string.GroupPhotosHelp) : LocaleController.getString("SinglePhotosHelp", R.string.SinglePhotosHelp));

        if (hintHideRunnable != null) {
            if (hintAnimation != null) {
                hintAnimation.cancel();
                hintAnimation = null;
            } else {
                AndroidUtilities.cancelRunOnUIThread(hintHideRunnable);
                AndroidUtilities.runOnUIThread(hintHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        hideHint();
                    }
                }, 2000);
                return;
            }
        } else if (hintAnimation != null) {
            return;
        }

        hintTextView.setVisibility(View.VISIBLE);
        hintAnimation = new AnimatorSet();
        hintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 1.0f)
        );
        hintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintAnimation = null;
                    AndroidUtilities.runOnUIThread(hintHideRunnable = new Runnable() {
                        @Override
                        public void run() {
                            hideHint();
                        }
                    }, 2000);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintAnimation = null;
                }
            }
        });
        hintAnimation.setDuration(300);
        hintAnimation.start();
    }

    @SuppressLint({"NewApi", "DrawAllocation"})
    private void onDraw(Canvas canvas) {
        if (animationInProgress == 1 || !isVisible && animationInProgress != 2 && !pipAnimationInProgress) {
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            setImageIndex(currentIndex + 1, false);
                        }
                    });
                } else if (switchImageAfterAnimation == 2) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            setImageIndex(currentIndex - 1, false);
                        }
                    });
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

        if (animationInProgress != 2 && !pipAnimationInProgress && !isInline) {
            if (currentEditMode == 0 && scale == 1 && aty != -1 && !zoomAnimation) {
                float maxValue = getContainerViewHeight() / 4.0f;
                backgroundDrawable.setAlpha((int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(aty), maxValue) / maxValue))));
            } else {
                backgroundDrawable.setAlpha(255);
            }
        }

        ImageReceiver sideImage = null;
        if (currentEditMode == 0) {
            if (scale >= 1.0f && !zoomAnimation && !zooming) {
                if (currentTranslationX > maxX + AndroidUtilities.dp(5)) {
                    sideImage = leftImage;
                } else if (currentTranslationX < minX - AndroidUtilities.dp(5)) {
                    sideImage = rightImage;
                } else {
                    groupedPhotosListView.setMoveProgress(0.0f);
                }
            }
            changingPage = sideImage != null;
        }

        if (sideImage == rightImage) {
            float translateX = currentTranslationX;
            float scaleDiff = 0;
            float alpha = 1;
            if (!zoomAnimation && translateX < minX) {
                alpha = Math.min(1.0f, (minX - translateX) / canvas.getWidth());
                scaleDiff = (1.0f - alpha) * 0.3f;
                translateX = -canvas.getWidth() - AndroidUtilities.dp(30) / 2;
            }

            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
                canvas.translate(canvas.getWidth() + AndroidUtilities.dp(30) / 2 + translateX, 0);
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
            groupedPhotosListView.setMoveProgress(-alpha);

            canvas.save();
            canvas.translate(translateX, currentTranslationY / currentScale);
            canvas.translate((canvas.getWidth() * (scale + 1) + AndroidUtilities.dp(30)) / 2, -currentTranslationY / currentScale);
            photoProgressViews[1].setScale(1.0f - scaleDiff);
            photoProgressViews[1].setAlpha(alpha);
            photoProgressViews[1].onDraw(canvas);
            canvas.restore();
        }

        float translateX = currentTranslationX;
        float scaleDiff = 0;
        float alpha = 1;
        if (!zoomAnimation && translateX > maxX && currentEditMode == 0) {
            alpha = Math.min(1.0f, (translateX - maxX) / canvas.getWidth());
            scaleDiff = alpha * 0.3f;
            alpha = 1.0f - alpha;
            translateX = maxX;
        }
        boolean drawTextureView = aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;
        if (centerImage.hasBitmapImage()) {
            canvas.save();
            canvas.translate(getContainerViewWidth() / 2 + getAdditionX(), getContainerViewHeight() / 2 + getAdditionY());
            canvas.translate(translateX, currentTranslationY);
            canvas.scale(currentScale - scaleDiff, currentScale - scaleDiff);

            if (currentEditMode == 1) {
                photoCropView.setBitmapParams(currentScale, translateX, currentTranslationY);
            }

            int bitmapWidth = centerImage.getBitmapWidth();
            int bitmapHeight = centerImage.getBitmapHeight();
            if (drawTextureView && textureUploaded) {
                float scale1 = bitmapWidth / (float) bitmapHeight;
                float scale2 = videoTextureView.getMeasuredWidth() / (float) videoTextureView.getMeasuredHeight();
                if (Math.abs(scale1 - scale2) > 0.01f) {
                    bitmapWidth = videoTextureView.getMeasuredWidth();
                    bitmapHeight = videoTextureView.getMeasuredHeight();
                }
            }

            float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
            float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            int width = (int) (bitmapWidth * scale);
            int height = (int) (bitmapHeight * scale);

            if (!drawTextureView || !textureUploaded || !videoCrossfadeStarted || videoCrossfadeAlpha != 1.0f) {
                centerImage.setAlpha(alpha);
                centerImage.setImageCoords(-width / 2, -height / 2, width, height);
                centerImage.draw(canvas);
            }
            if (drawTextureView) {
                if (!videoCrossfadeStarted && textureUploaded) {
                    videoCrossfadeStarted = true;
                    videoCrossfadeAlpha = 0.0f;
                    videoCrossfadeAlphaLastTime = System.currentTimeMillis();
                }
                canvas.translate(-width / 2, -height / 2);
                videoTextureView.setAlpha(alpha * videoCrossfadeAlpha);
                aspectRatioFrameLayout.draw(canvas);
                if (videoCrossfadeStarted && videoCrossfadeAlpha < 1.0f) {
                    long newUpdateTime = System.currentTimeMillis();
                    long dt = newUpdateTime - videoCrossfadeAlphaLastTime;
                    videoCrossfadeAlphaLastTime = newUpdateTime;
                    videoCrossfadeAlpha += dt / 200.0f;
                    containerView.invalidate();
                    if (videoCrossfadeAlpha > 1.0f) {
                        videoCrossfadeAlpha = 1.0f;
                    }
                }
            }
            canvas.restore();
        }
        boolean drawProgress;
        if (isCurrentVideo) {
            drawProgress = progressView.getVisibility() != View.VISIBLE && (videoPlayer == null || !videoPlayer.isPlaying());
        } else {
            drawProgress = !drawTextureView && videoPlayerControlFrameLayout.getVisibility() != View.VISIBLE;
        }
        if (drawProgress) {
            canvas.save();
            canvas.translate(translateX, currentTranslationY / currentScale);
            photoProgressViews[0].setScale(1.0f - scaleDiff);
            photoProgressViews[0].setAlpha(alpha);
            photoProgressViews[0].onDraw(canvas);
            canvas.restore();
        }
        if (!pipAnimationInProgress && (miniProgressView.getVisibility() == View.VISIBLE || miniProgressAnimator != null)) {
            canvas.save();
            canvas.translate(miniProgressView.getLeft() + translateX, miniProgressView.getTop() + currentTranslationY / currentScale);
            miniProgressView.draw(canvas);
            canvas.restore();
        }

        if (sideImage == leftImage) {
            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
                canvas.translate(-(canvas.getWidth() * (scale + 1) + AndroidUtilities.dp(30)) / 2 + currentTranslationX, 0);
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
            groupedPhotosListView.setMoveProgress(1.0f - alpha);

            canvas.save();
            canvas.translate(currentTranslationX, currentTranslationY / currentScale);
            canvas.translate(-(canvas.getWidth() * (scale + 1) + AndroidUtilities.dp(30)) / 2, -currentTranslationY / currentScale);
            photoProgressViews[2].setScale(1.0f);
            photoProgressViews[2].setAlpha(1.0f);
            photoProgressViews[2].onDraw(canvas);
            canvas.restore();
        }

        if (waitingForDraw != 0) {
            waitingForDraw--;
            if (waitingForDraw == 0) {
                if (textureImageView != null) {
                    try {
                        currentBitmap = Bitmaps.createBitmap(videoTextureView.getWidth(), videoTextureView.getHeight(), Bitmap.Config.ARGB_8888);
                        changedTextureView.getBitmap(currentBitmap);
                    } catch (Throwable e) {
                        if (currentBitmap != null) {
                            currentBitmap.recycle();
                            currentBitmap = null;
                        }
                        FileLog.e(e);
                    }
                    if (currentBitmap != null) {
                        textureImageView.setVisibility(View.VISIBLE);
                        textureImageView.setImageBitmap(currentBitmap);
                    } else {
                        textureImageView.setImageDrawable(null);
                    }
                }
                pipVideoView.close();
                pipVideoView = null;
            } else {
                containerView.invalidate();
            }
        }
    }

    private void onActionClick(boolean download) {
        if (currentMessageObject == null && currentBotInlineResult == null || currentFileNames[0] == null) {
            return;
        }
        Uri uri = null;
        File file = null;
        isStreaming = false;
        if (currentMessageObject != null) {
            if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
                file = new File(currentMessageObject.messageOwner.attachPath);
                if (!file.exists()) {
                    file = null;
                }
            }
            if (file == null) {
                file = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                if (!file.exists()) {
                    file = null;
                    if (SharedConfig.streamMedia && (int) currentMessageObject.getDialogId() != 0 && currentMessageObject.isVideo() && currentMessageObject.canStreamVideo()) {
                        try {
                            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), true, 0);
                            TLRPC.Document document = currentMessageObject.getDocument();
                            String params = "?account=" + currentMessageObject.currentAccount + "&id=" + document.id + "&hash=" + document.access_hash + "&dc=" + document.dc_id + "&size=" + document.size + "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") + "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8");
                            uri = Uri.parse("tg://" + currentMessageObject.getFileName() + params);
                            isStreaming = true;
                            checkProgress(0, false);
                        } catch (Exception ignore) {

                        }
                    }
                }
            }
        } else if (currentBotInlineResult != null) {
            if (currentBotInlineResult.document != null) {
                file = FileLoader.getPathToAttach(currentBotInlineResult.document);
                if (!file.exists()) {
                    file = null;
                }
            } else if (currentBotInlineResult.content instanceof TLRPC.TL_webDocument) {
                file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(currentBotInlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(currentBotInlineResult.content.url, "mp4"));
                if (!file.exists()) {
                    file = null;
                }
            }
        }
        if (file != null && uri == null) {
            uri = Uri.fromFile(file);
        }
        if (uri == null) {
            if (download) {
                if (currentMessageObject !=  null) {
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[0])) {
                        FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), true, 0);
                    } else {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
                    }
                } else if (currentBotInlineResult != null) {
                    if (currentBotInlineResult.document != null) {
                        if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[0])) {
                            FileLoader.getInstance(currentAccount).loadFile(currentBotInlineResult.document, true, 0);
                        } else {
                            FileLoader.getInstance(currentAccount).cancelLoadFile(currentBotInlineResult.document);
                        }
                    } else if (currentBotInlineResult.content instanceof TLRPC.TL_webDocument) {
                        if (!ImageLoader.getInstance().isLoadingHttpFile(currentBotInlineResult.content.url)) {
                            ImageLoader.getInstance().loadHttpFile(currentBotInlineResult.content.url, "mp4", currentAccount);
                        } else {
                            ImageLoader.getInstance().cancelLoadHttpFile(currentBotInlineResult.content.url);
                        }
                    }
                }
            }
        } else {
            preparePlayer(uri, true, false);
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
        if (containerView.getTag() != null) {
            boolean drawTextureView = aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;
            float x = e.getX();
            float y = e.getY();
            if (photoProgressViews[0] != null && containerView != null && !drawTextureView) {
                int state = photoProgressViews[0].backgroundState;
                if (state > 0 && state <= 3) {
                    if (x >= (getContainerViewWidth() - AndroidUtilities.dp(100)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(100)) / 2.0f &&
                            y >= (getContainerViewHeight() - AndroidUtilities.dp(100)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(100)) / 2.0f) {
                        onActionClick(true);
                        checkProgress(0, true);
                        return true;
                    }
                }
            }
            toggleActionBar(!isActionBarVisible, true);
        } else if (sendPhotoType == 0 || sendPhotoType == 4) {
            if (isCurrentVideo) {
                videoPlayButton.callOnClick();
            } else {
                checkImageView.performClick();
            }
        } else if (currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document))) {
            int state = photoProgressViews[0].backgroundState;
            if (state > 0 && state <= 3) {
                float x = e.getX();
                float y = e.getY();
                if (x >= (getContainerViewWidth() - AndroidUtilities.dp(100)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(100)) / 2.0f &&
                        y >= (getContainerViewHeight() - AndroidUtilities.dp(100)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(100)) / 2.0f) {
                    onActionClick(true);
                    checkProgress(0, true);
                    return true;
                }
            }
        } else if (sendPhotoType == 2) {
            if (isCurrentVideo) {
                videoPlayButton.callOnClick();
            }
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (videoPlayer != null && videoPlayerControlFrameLayout.getVisibility() == View.VISIBLE) {
            long current = videoPlayer.getCurrentPosition();
            long total = videoPlayer.getDuration();
            if (total >= 0 && current >= 0 && total != C.TIME_UNSET && current != C.TIME_UNSET) {
                int width = getContainerViewWidth();
                float x = e.getX();
                long old = current;
                if (x >= width / 3 * 2) {
                    current += 10000;
                } else if (x < width / 3) {
                    current -= 10000;
                }
                if (old != current) {
                    if (current > total) {
                        current = total;
                    } else if (current < 0) {
                        current = 0;
                    }
                    videoPlayer.seekTo(current);
                    containerView.invalidate();
                    videoPlayerSeekbar.setProgress(current / (float) total);
                    videoPlayerControlFrameLayout.invalidate();
                    return true;
                }
            }
        }
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

    // video edit start
    private QualityChooseView qualityChooseView;
    private PickerBottomLayoutViewer qualityPicker;
    private RadialProgressView progressView;
    private VideoTimelinePlayView videoTimelineView;
    private AnimatorSet qualityChooseViewAnimation;

    private int selectedCompression;
    private int compressionsCount = -1;
    private int previousCompression;

    private int rotationValue;
    private int originalWidth;
    private int originalHeight;
    private int resultWidth;
    private int resultHeight;
    private int bitrate;
    private int originalBitrate;
    private float videoDuration;
    private int videoFramerate;
    private boolean videoHasAudio;
    private long startTime;
    private long endTime;
    private long audioFramesSize;
    private long videoFramesSize;
    private int estimatedSize;
    private long estimatedDuration;
    private long originalSize;

    private Runnable currentLoadingVideoRunnable;
    private MessageObject videoPreviewMessageObject;
    private boolean tryStartRequestPreviewOnFinish;
    private boolean loadInitialVideo;
    private boolean inPreview;
    private int previewViewEnd;
    private boolean requestingPreview;

    private String currentSubtitle;

    private class QualityChooseView extends View {

        private Paint paint;
        private TextPaint textPaint;

        private int circleSize;
        private int gapSize;
        private int sideSide;
        private int lineSize;

        private boolean moving;
        private boolean startMoving;
        private float startX;

        private int startMovingQuality;

        public QualityChooseView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(12));
            textPaint.setColor(0xffcdcdcd);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                for (int a = 0; a < compressionsCount; a++) {
                    int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                    if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                        startMoving = a == selectedCompression;
                        startX = x;
                        startMovingQuality = selectedCompression;
                        break;
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (startMoving) {
                    if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.5f, true)) {
                        moving = true;
                        startMoving = false;
                    }
                } else if (moving) {
                    for (int a = 0; a < compressionsCount; a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        int diff = lineSize / 2 + circleSize / 2 + gapSize;
                        if (x > cx - diff && x < cx + diff) {
                            if (selectedCompression != a) {
                                selectedCompression = a;
                                didChangedCompressionLevel(false);
                                invalidate();
                            }
                            break;
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!moving) {
                    for (int a = 0; a < compressionsCount; a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                            if (selectedCompression != a) {
                                selectedCompression = a;
                                didChangedCompressionLevel(true);
                                invalidate();
                            }
                            break;
                        }
                    }
                } else {
                    if (selectedCompression != startMovingQuality) {
                        requestVideoPreview(1);
                    }
                }
                startMoving = false;
                moving = false;
            }
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            circleSize = AndroidUtilities.dp(12);
            gapSize = AndroidUtilities.dp(2);
            sideSide = AndroidUtilities.dp(18);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (compressionsCount != 1) {
                lineSize = (getMeasuredWidth() - circleSize * compressionsCount - gapSize * 8 - sideSide * 2) / (compressionsCount - 1);
            } else {
                lineSize = (getMeasuredWidth() - circleSize * compressionsCount - gapSize * 8 - sideSide * 2);
            }
            int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(6);
            for (int a = 0; a < compressionsCount; a++) {
                int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                if (a <= selectedCompression) {
                    paint.setColor(0xff53aeef);
                } else {
                    paint.setColor(0x66ffffff);
                }
                String text;
                if (a == compressionsCount - 1) {
                    text = Math.min(originalWidth, originalHeight) + "p";
                } else if (a == 0) {
                    text = "240p";
                } else if (a == 1) {
                    text = "360p";
                } else if (a == 2) {
                    text = "480p";
                } else {
                    text = "720p";
                }
                float width = textPaint.measureText(text);
                canvas.drawCircle(cx, cy, a == selectedCompression ? AndroidUtilities.dp(8) : circleSize / 2, paint);
                canvas.drawText(text, cx - width / 2, cy - AndroidUtilities.dp(16), textPaint);
                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    canvas.drawRect(x, cy - AndroidUtilities.dp(1), x + lineSize, cy + AndroidUtilities.dp(2), paint);
                }
            }
        }
    }

    public void updateMuteButton() {
        if (videoPlayer != null) {
            videoPlayer.setMute(muteVideo);
        }
        if (!videoHasAudio) {
            muteItem.setEnabled(false);
            muteItem.setClickable(false);
            muteItem.setAlpha(0.5f);
        } else {
            muteItem.setEnabled(true);
            muteItem.setClickable(true);
            muteItem.setAlpha(1.0f);
            if (muteVideo) {
                actionBar.setSubtitle(null);
                muteItem.setImageResource(R.drawable.volume_off);
                muteItem.setColorFilter(new PorterDuffColorFilter(0xff3dadee, PorterDuff.Mode.MULTIPLY));
                if (compressItem.getTag() != null) {
                    compressItem.setClickable(false);
                    compressItem.setAlpha(0.5f);
                    compressItem.setEnabled(false);
                }
                videoTimelineView.setMaxProgressDiff(30000.0f / videoDuration);
            } else {
                muteItem.setColorFilter(null);
                actionBar.setSubtitle(currentSubtitle);
                muteItem.setImageResource(R.drawable.volume_on);
                if (compressItem.getTag() != null) {
                    compressItem.setClickable(true);
                    compressItem.setAlpha(1.0f);
                    compressItem.setEnabled(true);
                }
                videoTimelineView.setMaxProgressDiff(1.0f);
            }
        }
    }

    private void didChangedCompressionLevel(boolean request) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("compress_video2", selectedCompression);
        editor.commit();
        updateWidthHeightBitrateForCompression();
        updateVideoInfo();
        if (request) {
            requestVideoPreview(1);
        }
    }

    private void updateVideoInfo() {
        if (actionBar == null) {
            return;
        }
        if (compressionsCount == 0) {
            actionBar.setSubtitle(null);
            return;
        }

        if (selectedCompression == 0) {
            compressItem.setImageResource(R.drawable.video_240);
        } else if (selectedCompression == 1) {
            compressItem.setImageResource(R.drawable.video_360);
        } else if (selectedCompression == 2) {
            compressItem.setImageResource(R.drawable.video_480);
        } else if (selectedCompression == 3) {
            compressItem.setImageResource(R.drawable.video_720);
        } else if (selectedCompression == 4) {
            compressItem.setImageResource(R.drawable.video_1080);
        }

        estimatedDuration = (long) Math.ceil((videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * videoDuration);

        int width;
        int height;

        if (compressItem.getTag() == null || selectedCompression == compressionsCount - 1) {
            width = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
            height = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
            estimatedSize = (int) (originalSize * ((float) estimatedDuration / videoDuration));
        } else {
            width = rotationValue == 90 || rotationValue == 270 ? resultHeight : resultWidth;
            height = rotationValue == 90 || rotationValue == 270 ? resultWidth : resultHeight;

            estimatedSize = (int) ((audioFramesSize + videoFramesSize) * ((float) estimatedDuration / videoDuration));
            estimatedSize += estimatedSize / (32 * 1024) * 16;
        }

        if (videoTimelineView.getLeftProgress() == 0) {
            startTime = -1;
        } else {
            startTime = (long) (videoTimelineView.getLeftProgress() * videoDuration) * 1000;
        }
        if (videoTimelineView.getRightProgress() == 1) {
            endTime = -1;
        } else {
            endTime = (long) (videoTimelineView.getRightProgress() * videoDuration) * 1000;
        }

        String videoDimension = String.format("%dx%d", width, height);
        int minutes = (int) (estimatedDuration / 1000 / 60);
        int seconds = (int) Math.ceil(estimatedDuration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, ~%s", minutes, seconds, AndroidUtilities.formatFileSize(estimatedSize));
        currentSubtitle = String.format("%s, %s", videoDimension, videoTimeSize);
        actionBar.setSubtitle(muteVideo ? null : currentSubtitle);
    }

    private void requestVideoPreview(int request) {
        if (videoPreviewMessageObject != null) {
            MediaController.getInstance().cancelVideoConvert(videoPreviewMessageObject);
        }
        boolean wasRequestingPreview = requestingPreview && !tryStartRequestPreviewOnFinish;
        requestingPreview = false;
        loadInitialVideo = false;
        progressView.setVisibility(View.INVISIBLE);
        if (request == 1) {
            if (selectedCompression == compressionsCount - 1) {
                tryStartRequestPreviewOnFinish = false;
                if (!wasRequestingPreview) {
                    preparePlayer(currentPlayingVideoFile, false, false);
                } else {
                    progressView.setVisibility(View.VISIBLE);
                    loadInitialVideo = true;
                }
            } else {
                requestingPreview = true;
                releasePlayer();
                if (videoPreviewMessageObject == null) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = 0;
                    message.message = "";
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.action = new TLRPC.TL_messageActionEmpty();
                    videoPreviewMessageObject = new MessageObject(UserConfig.selectedAccount, message, false);
                    videoPreviewMessageObject.messageOwner.attachPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "video_preview.mp4").getAbsolutePath();
                    videoPreviewMessageObject.videoEditedInfo = new VideoEditedInfo();
                    videoPreviewMessageObject.videoEditedInfo.rotationValue = rotationValue;
                    videoPreviewMessageObject.videoEditedInfo.originalWidth = originalWidth;
                    videoPreviewMessageObject.videoEditedInfo.originalHeight = originalHeight;
                    videoPreviewMessageObject.videoEditedInfo.framerate = videoFramerate;
                    videoPreviewMessageObject.videoEditedInfo.originalPath = currentPlayingVideoFile.getPath();
                }
                long start = videoPreviewMessageObject.videoEditedInfo.startTime = startTime;
                long end = videoPreviewMessageObject.videoEditedInfo.endTime = endTime;
                if (start == -1) {
                    start = 0;
                }
                if (end == -1) {
                    end = (long) (videoDuration * 1000);
                }
                if (end - start > 5000000) {
                    videoPreviewMessageObject.videoEditedInfo.endTime = start + 5000000;
                }
                videoPreviewMessageObject.videoEditedInfo.bitrate = bitrate;
                videoPreviewMessageObject.videoEditedInfo.resultWidth = resultWidth;
                videoPreviewMessageObject.videoEditedInfo.resultHeight = resultHeight;
                if (!MediaController.getInstance().scheduleVideoConvert(videoPreviewMessageObject, true)) {
                    tryStartRequestPreviewOnFinish = true;
                }
                requestingPreview = true;
                progressView.setVisibility(View.VISIBLE);
            }
        } else {
            tryStartRequestPreviewOnFinish = false;
            if (request == 2) {
                preparePlayer(currentPlayingVideoFile, false, false);
            }
        }
        containerView.invalidate();
    }

    private void updateWidthHeightBitrateForCompression() {
        if (compressionsCount <= 0) {
            return;
        }
        if (selectedCompression >= compressionsCount) {
            selectedCompression = compressionsCount - 1;
        }
        if (selectedCompression != compressionsCount - 1) {
            float maxSize;
            int targetBitrate;
            switch (selectedCompression) {
                case 0:
                    maxSize = 426.0f;
                    targetBitrate = 400000;
                    break;
                case 1:
                    maxSize = 640.0f;
                    targetBitrate = 900000;
                    break;
                case 2:
                    maxSize = 854.0f;
                    targetBitrate = 1100000;
                    break;
                case 3:
                default:
                    targetBitrate = 2500000;
                    maxSize = 1280.0f;
                    break;
            }
            float scale = originalWidth > originalHeight ? maxSize / originalWidth : maxSize / originalHeight;
            resultWidth = Math.round(originalWidth * scale / 2) * 2;
            resultHeight = Math.round(originalHeight * scale / 2) * 2;
            if (bitrate != 0) {
                bitrate = Math.min(targetBitrate, (int) (originalBitrate / scale));
                videoFramesSize = (long) (bitrate / 8 * videoDuration / 1000);
            }
        }
    }

    private void showQualityView(final boolean show) {
        if (show) {
            previousCompression = selectedCompression;
        }
        if (qualityChooseViewAnimation != null) {
            qualityChooseViewAnimation.cancel();
        }
        qualityChooseViewAnimation = new AnimatorSet();
        if (show) {
            qualityChooseView.setTag(1);
            qualityChooseViewAnimation.playTogether(
                    ObjectAnimator.ofFloat(pickerView, "translationY", 0, AndroidUtilities.dp(152)),
                    ObjectAnimator.ofFloat(pickerViewSendButton, "translationY", 0, AndroidUtilities.dp(152)),
                    ObjectAnimator.ofFloat(bottomLayout, "translationY", -AndroidUtilities.dp(48), AndroidUtilities.dp(104))
            );
        } else {
            qualityChooseView.setTag(null);
            qualityChooseViewAnimation.playTogether(
                    ObjectAnimator.ofFloat(qualityChooseView, "translationY", 0, AndroidUtilities.dp(166)),
                    ObjectAnimator.ofFloat(qualityPicker, "translationY", 0, AndroidUtilities.dp(166)),
                    ObjectAnimator.ofFloat(bottomLayout, "translationY", -AndroidUtilities.dp(48), AndroidUtilities.dp(118))
            );
        }
        qualityChooseViewAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!animation.equals(qualityChooseViewAnimation)) {
                    return;
                }
                qualityChooseViewAnimation = new AnimatorSet();
                if (show) {
                    qualityChooseView.setVisibility(View.VISIBLE);
                    qualityPicker.setVisibility(View.VISIBLE);
                    qualityChooseViewAnimation.playTogether(
                            ObjectAnimator.ofFloat(qualityChooseView, "translationY", 0),
                            ObjectAnimator.ofFloat(qualityPicker, "translationY", 0),
                            ObjectAnimator.ofFloat(bottomLayout, "translationY", -AndroidUtilities.dp(48))
                    );
                } else {
                    qualityChooseView.setVisibility(View.INVISIBLE);
                    qualityPicker.setVisibility(View.INVISIBLE);
                    qualityChooseViewAnimation.playTogether(
                            ObjectAnimator.ofFloat(pickerView, "translationY", 0),
                            ObjectAnimator.ofFloat(pickerViewSendButton, "translationY", 0),
                            ObjectAnimator.ofFloat(bottomLayout, "translationY", -AndroidUtilities.dp(48))
                    );
                }
                qualityChooseViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(qualityChooseViewAnimation)) {
                            qualityChooseViewAnimation = null;
                        }
                    }
                });
                qualityChooseViewAnimation.setDuration(200);
                qualityChooseViewAnimation.setInterpolator(new AccelerateInterpolator());
                qualityChooseViewAnimation.start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                qualityChooseViewAnimation = null;
            }
        });
        qualityChooseViewAnimation.setDuration(200);
        qualityChooseViewAnimation.setInterpolator(new DecelerateInterpolator());
        qualityChooseViewAnimation.start();
    }

    private ByteArrayInputStream cleanBuffer(byte[] data) {
        byte[] output = new byte[data.length];
        int inPos = 0;
        int outPos = 0;
        while (inPos < data.length) {
            if (data[inPos] == 0 && data[inPos + 1] == 0 && data[inPos + 2] == 3) {
                output[outPos] = 0;
                output[outPos + 1] = 0;
                inPos += 3;
                outPos += 2;
            } else {
                output[outPos] = data[inPos];
                inPos++;
                outPos++;
            }
        }
        return new ByteArrayInputStream(output, 0, outPos);
    }

    private void processOpenVideo(final String videoPath, boolean muted) {
        if (currentLoadingVideoRunnable != null) {
            Utilities.globalQueue.cancelRunnable(currentLoadingVideoRunnable);
            currentLoadingVideoRunnable = null;
        }
        videoPreviewMessageObject = null;
        setCompressItemEnabled(false, true);
        muteVideo = muted;
        videoTimelineView.setVideoPath(videoPath);
        compressionsCount = -1;
        rotationValue = 0;
        videoFramerate = 25;
        File file = new File(videoPath);
        originalSize = file.length();

        Utilities.globalQueue.postRunnable(currentLoadingVideoRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentLoadingVideoRunnable != this) {
                    return;
                }
                TrackHeaderBox trackHeaderBox = null;
                boolean isAvc = true;
                try {
                    IsoFile isoFile = new IsoFile(videoPath);
                    List<Box> boxes = Path.getPaths(isoFile, "/moov/trak/");

                    Box boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/mp4a/");
                    if (boxTest == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("video hasn't mp4a atom");
                        }
                    }

                    boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/avc1/");
                    if (boxTest == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("video hasn't avc1 atom");
                        }

                        isAvc = false;
                    }

                    audioFramesSize = 0;
                    videoFramesSize = 0;
                    for (int b = 0; b < boxes.size(); b++) {
                        if (currentLoadingVideoRunnable != this) {
                            return;
                        }
                        Box box = boxes.get(b);
                        TrackBox trackBox = (TrackBox) box;
                        long sampleSizes = 0;
                        long trackBitrate = 0;
                        MediaBox mediaBox = null;
                        MediaHeaderBox mediaHeaderBox = null;
                        try {
                            mediaBox = trackBox.getMediaBox();
                            mediaHeaderBox = mediaBox.getMediaHeaderBox();

                            SampleSizeBox sampleSizeBox = mediaBox.getMediaInformationBox().getSampleTableBox().getSampleSizeBox();
                            long[] sizes = sampleSizeBox.getSampleSizes();
                            for (int a = 0; a < sizes.length; a++) {
                                if (currentLoadingVideoRunnable != this) {
                                    return;
                                }
                                sampleSizes += sizes[a];
                            }
                            videoDuration = (float) mediaHeaderBox.getDuration() / (float) mediaHeaderBox.getTimescale();
                            trackBitrate = (int) (sampleSizes * 8 / videoDuration);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (currentLoadingVideoRunnable != this) {
                            return;
                        }
                        TrackHeaderBox headerBox = trackBox.getTrackHeaderBox();
                        if (headerBox.getWidth() != 0 && headerBox.getHeight() != 0) {
                            if (trackHeaderBox == null || trackHeaderBox.getWidth() < headerBox.getWidth() || trackHeaderBox.getHeight() < headerBox.getHeight()) {
                                trackHeaderBox = headerBox;
                                originalBitrate = bitrate = (int) (trackBitrate / 100000 * 100000);
                                if (bitrate > 900000) {
                                    bitrate = 900000;
                                }
                                videoFramesSize += sampleSizes;

                                if (mediaBox != null && mediaHeaderBox != null) {
                                    TimeToSampleBox timeToSampleBox = mediaBox.getMediaInformationBox().getSampleTableBox().getTimeToSampleBox();
                                    if (timeToSampleBox != null) {
                                        List<TimeToSampleBox.Entry> entries = timeToSampleBox.getEntries();
                                        long delta = 0;
                                        int size = Math.min(entries.size(), 11);
                                        for (int a = 1; a < size; a++) {
                                            delta += entries.get(a).getDelta();
                                        }
                                        if (delta != 0) {
                                            videoFramerate = (int) ((double) mediaHeaderBox.getTimescale() / (delta / (size - 1)));
                                        }
                                    }
                                }
                            }
                        } else {
                            audioFramesSize += sampleSizes;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    isAvc = false;
                }
                if (trackHeaderBox == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("video hasn't trackHeaderBox atom");
                    }
                    isAvc = false;
                }
                final boolean isAvcFinal = isAvc;
                final TrackHeaderBox trackHeaderBoxFinal = trackHeaderBox;
                if (currentLoadingVideoRunnable != this) {
                    return;
                }
                currentLoadingVideoRunnable = null;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (parentActivity == null) {
                            return;
                        }
                        videoHasAudio = isAvcFinal;
                        if (isAvcFinal) {
                            Matrix matrix = trackHeaderBoxFinal.getMatrix();
                            if (matrix.equals(Matrix.ROTATE_90)) {
                                rotationValue = 90;
                            } else if (matrix.equals(Matrix.ROTATE_180)) {
                                rotationValue = 180;
                            } else if (matrix.equals(Matrix.ROTATE_270)) {
                                rotationValue = 270;
                            } else {
                                rotationValue = 0;
                            }
                            resultWidth = originalWidth = (int) trackHeaderBoxFinal.getWidth();
                            resultHeight = originalHeight = (int) trackHeaderBoxFinal.getHeight();

                            videoDuration *= 1000;

                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            selectedCompression = preferences.getInt("compress_video2", 1);
                            if (originalWidth > 1280 || originalHeight > 1280) {
                                compressionsCount = 5;
                            } else if (originalWidth > 854 || originalHeight > 854) {
                                compressionsCount = 4;
                            } else if (originalWidth > 640 || originalHeight > 640) {
                                compressionsCount = 3;
                            } else if (originalWidth > 480 || originalHeight > 480) {
                                compressionsCount = 2;
                            } else {
                                compressionsCount = 1;
                            }
                            updateWidthHeightBitrateForCompression();

                            setCompressItemEnabled(compressionsCount > 1, true);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("compressionsCount = " + compressionsCount + " w = " + originalWidth + " h = " + originalHeight);
                            }
                            if (Build.VERSION.SDK_INT < 18 && compressItem.getTag() != null) {
                                try {
                                    MediaCodecInfo codecInfo = MediaController.selectCodec(MediaController.MIME_TYPE);
                                    if (codecInfo == null) {
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.d("no codec info for " + MediaController.MIME_TYPE);
                                        }
                                        setCompressItemEnabled(false, true);
                                    } else {
                                        String name = codecInfo.getName();
                                        if (name.equals("OMX.google.h264.encoder") ||
                                                name.equals("OMX.ST.VFM.H264Enc") ||
                                                name.equals("OMX.Exynos.avc.enc") ||
                                                name.equals("OMX.MARVELL.VIDEO.HW.CODA7542ENCODER") ||
                                                name.equals("OMX.MARVELL.VIDEO.H264ENCODER") ||
                                                name.equals("OMX.k3.video.encoder.avc") ||
                                                name.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                                            if (BuildVars.LOGS_ENABLED) {
                                                FileLog.d("unsupported encoder = " + name);
                                            }
                                            setCompressItemEnabled(false, true);
                                        } else {
                                            if (MediaController.selectColorFormat(codecInfo, MediaController.MIME_TYPE) == 0) {
                                                if (BuildVars.LOGS_ENABLED) {
                                                    FileLog.d("no color format for " + MediaController.MIME_TYPE);
                                                }
                                                setCompressItemEnabled(false, true);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    setCompressItemEnabled(false, true);
                                    FileLog.e(e);
                                }
                            }
                            qualityChooseView.invalidate();
                        } else {
                            compressionsCount = 0;
                        }

                        updateVideoInfo();
                        updateMuteButton();
                    }
                });
            }
        });
    }

    private void setCompressItemEnabled(boolean enabled, boolean animated) {
        if (compressItem == null) {
            return;
        }
        if (enabled && compressItem.getTag() != null || !enabled && compressItem.getTag() == null) {
            return;
        }
        compressItem.setTag(enabled ? 1 : null);
        compressItem.setEnabled(enabled);
        compressItem.setClickable(enabled);
        if (compressItemAnimation != null) {
            compressItemAnimation.cancel();
            compressItemAnimation = null;
        }
        if (animated) {
            compressItemAnimation = new AnimatorSet();
            compressItemAnimation.playTogether(ObjectAnimator.ofFloat(compressItem, "alpha", enabled ? 1.0f : 0.5f));
            compressItemAnimation.setDuration(180);
            compressItemAnimation.setInterpolator(decelerateInterpolator);
            compressItemAnimation.start();
        } else {
            compressItem.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            if (placeProvider != null && placeProvider.getSelectedPhotosOrder() != null) {
                if (placeProvider.allowGroupPhotos()) {
                    return 1 + placeProvider.getSelectedPhotosOrder().size();
                } else {
                    return placeProvider.getSelectedPhotosOrder().size();
                }
            }
            return 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    PhotoPickerPhotoCell cell = new PhotoPickerPhotoCell(mContext, false);
                    cell.checkFrame.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Object photoEntry = ((View) v.getParent()).getTag();
                            int idx = imagesArrLocals.indexOf(photoEntry);
                            if (idx >= 0) {
                                int num = placeProvider.setPhotoChecked(idx, getCurrentVideoEditedInfo());
                                boolean checked = placeProvider.isPhotoChecked(idx);
                                if (idx == currentIndex) {
                                    checkImageView.setChecked(-1, false, true);
                                }
                                if (num >= 0) {
                                    if (placeProvider.allowGroupPhotos()) {
                                        num++;
                                    }
                                    selectedPhotosAdapter.notifyItemRemoved(num);
                                }
                                updateSelectedCount();
                            } else {
                                int num = placeProvider.setPhotoUnchecked(photoEntry);
                                if (num >= 0) {
                                    if (placeProvider.allowGroupPhotos()) {
                                        num++;
                                    }
                                    selectedPhotosAdapter.notifyItemRemoved(num);
                                    updateSelectedCount();
                                }
                            }
                        }
                    });
                    view = cell;
                    break;
                case 1:
                default:
                    ImageView imageView = new ImageView(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(66), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
                        }
                    };
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setImageResource(R.drawable.photos_group);
                    view = imageView;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) holder.itemView;
                    cell.itemWidth = AndroidUtilities.dp(82);
                    BackupImageView imageView = cell.photoImage;
                    boolean showing;
                    imageView.setOrientation(0, true);
                    ArrayList<Object> order = placeProvider.getSelectedPhotosOrder();
                    if (placeProvider.allowGroupPhotos()) {
                        position--;
                    }
                    Object object = placeProvider.getSelectedPhotos().get(order.get(position));
                    if (object instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                        cell.setTag(photoEntry);
                        cell.videoInfoContainer.setVisibility(View.INVISIBLE);
                        if (photoEntry.thumbPath != null) {
                            imageView.setImage(photoEntry.thumbPath, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                        } else if (photoEntry.path != null) {
                            imageView.setOrientation(photoEntry.orientation, true);
                            if (photoEntry.isVideo) {
                                cell.videoInfoContainer.setVisibility(View.VISIBLE);
                                int minutes = photoEntry.duration / 60;
                                int seconds = photoEntry.duration - minutes * 60;
                                cell.videoTextView.setText(String.format("%d:%02d", minutes, seconds));
                                imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                            } else {
                                imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                            }
                        } else {
                            imageView.setImageResource(R.drawable.nophotos);
                        }
                        cell.setChecked(-1, true, false);
                        cell.checkBox.setVisibility(View.VISIBLE);
                    } else if (object instanceof MediaController.SearchImage) {
                        MediaController.SearchImage photoEntry = (MediaController.SearchImage) object;
                        cell.setTag(photoEntry);
                        cell.setImage(photoEntry);
                        cell.videoInfoContainer.setVisibility(View.INVISIBLE);
                        cell.setChecked(-1, true, false);
                        cell.checkBox.setVisibility(View.VISIBLE);
                    }
                    break;
                }
                case 1: {
                    ImageView imageView = (ImageView) holder.itemView;
                    imageView.setColorFilter(SharedConfig.groupPhotosEnabled ? new PorterDuffColorFilter(0xff66bffa, PorterDuff.Mode.MULTIPLY) : null);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == 0 && placeProvider.allowGroupPhotos()) {
                return 1;
            }
            return 0;
        }
    }
}
