/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerEnd;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
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
import android.text.method.Touch;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.util.FloatProperty;
import android.util.Property;
import android.util.Range;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BringAppForegroundService;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
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
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.video.VideoPlayerRewinder;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.PhotoPickerPhotoCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.Crop.CropTransform;
import org.telegram.ui.Components.Crop.CropView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FadingTextViewLayout;
import org.telegram.ui.Components.FilterShaders;
import org.telegram.ui.Components.FloatSeekBarAccessibilityDelegate;
import org.telegram.ui.Components.GestureDetector2;
import org.telegram.ui.Components.GroupedPhotosListView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.OtherDocumentPlaceholderDrawable;
import org.telegram.ui.Components.PaintingOverlay;
import org.telegram.ui.Components.PhotoCropView;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.PhotoPaintView;
import org.telegram.ui.Components.PhotoViewerCaptionEnterView;
import org.telegram.ui.Components.PhotoViewerWebView;
import org.telegram.ui.Components.PickerBottomLayoutViewer;
import org.telegram.ui.Components.PipVideoView;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayoutPhoto;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.Tooltip;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMentionPhotoViewer;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.VideoEditTextureView;
import org.telegram.ui.Components.VideoForwardDrawable;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.VideoPlayerSeekBar;
import org.telegram.ui.Components.VideoSeekPreviewImage;
import org.telegram.ui.Components.VideoTimelinePlayView;
import org.telegram.ui.Components.ViewHelper;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unchecked")
public class PhotoViewer implements NotificationCenter.NotificationCenterDelegate, GestureDetector2.OnGestureListener, GestureDetector2.OnDoubleTapListener {

    private int classGuid;
    private PhotoViewerProvider placeProvider;
    private boolean isVisible;
    private int maxSelectedPhotos = -1;
    private boolean allowOrder = true;

    private boolean muteVideo;

    private boolean inBubbleMode;

    private int slideshowMessageId;
    private String nameOverride;
    private int dateOverride;

    private AnimatorSet miniProgressAnimator;
    private Runnable miniProgressShowRunnable = () -> toggleMiniProgressInternal(true);

    private Activity parentActivity;
    private Context activityContext;

    private ActionBar actionBar;
    private boolean isActionBarVisible = true;
    private boolean isPhotosListViewVisible;
    private AnimatorSet actionBarAnimator;

    private int currentAccount;

    private static final int PROGRESS_NONE = -1;
    private static final int PROGRESS_EMPTY = 0;
    private static final int PROGRESS_CANCEL = 1;
    private static final int PROGRESS_LOAD = 2;
    private static final int PROGRESS_PLAY = 3;
    private static final int PROGRESS_PAUSE = 4;
    private static Drawable[] progressDrawables;

    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayoutDrawer containerView;
    private PhotoViewerWebView photoViewerWebView;
    private FrameLayout windowView;
    private ClippingImageView animatingImageView;
    private FrameLayout bottomLayout;
    private FadingTextViewLayout nameTextView;
    private FadingTextViewLayout dateTextView;
    private TextView docNameTextView;
    private TextView docInfoTextView;
    private ActionBarMenuItem menuItem;
    private ActionBarMenuItem menuItemSpeed;
    private ActionBarMenuSubItem allMediaItem;
    private ActionBarMenuSubItem speedItem;
    private ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[5];
    private View speedGap;
    private ActionBarMenuItem sendItem;
    private ActionBarMenuItem pipItem;
    private ActionBarMenuItem masksItem;
    private ActionBarMenuItem shareItem;
    private LinearLayout itemsLayout;
    private Map<View, Boolean> actionBarItemsVisibility = new HashMap<>(3);
    private LinearLayout bottomButtonsLayout;
    private ImageView shareButton;
    private ImageView paintButton;
    private BackgroundDrawable backgroundDrawable = new BackgroundDrawable(0xff000000);
    private Paint blackPaint = new Paint();
    private CheckBox checkImageView;
    private CounterView photosCounterView;
    private FrameLayout pickerView;
    private ImageView pickerViewSendButton;
    private PickerBottomLayoutViewer editorDoneLayout;
    private TextView resetButton;
    private PhotoProgressView[] photoProgressViews = new PhotoProgressView[3];
    private RadialProgressView miniProgressView;
    private ImageView paintItem;
    private ImageView cropItem;
    private ImageView mirrorItem;
    private ImageView rotateItem;
    private ImageView cameraItem;
    private ImageView tuneItem;
    private ImageView timeItem;
    private ImageView muteItem;
    private ImageView compressItem;
    private GroupedPhotosListView groupedPhotosListView;
    private Tooltip tooltip;
    private UndoView hintView;
    private SelectedPhotosListView selectedPhotosListView;
    private ListAdapter selectedPhotosAdapter;
    private AnimatorSet compressItemAnimation;
    private ImageReceiver sideImage;
    private boolean isCurrentVideo;

    private float currentVideoSpeed;

    private long lastPhotoSetTime;

    private GradientDrawable[] pressedDrawable = new GradientDrawable[2];
    private boolean[] drawPressedDrawable = new boolean[2];
    private float[] pressedDrawableAlpha = new float[2];
    private int touchSlop;

    private boolean useSmoothKeyboard;

    private VideoForwardDrawable videoForwardDrawable;

    private AnimatorSet currentListViewAnimation;
    private PhotoCropView photoCropView;
    private CropTransform cropTransform = new CropTransform();
    private PhotoFilterView photoFilterView;
    private PhotoPaintView photoPaintView;
    private AlertDialog visibleDialog;
    private CaptionTextViewSwitcher captionTextViewSwitcher;
    private CaptionScrollView captionScrollView;
    private FrameLayout captionContainer;
    private ChatAttachAlert parentAlert;
    private PhotoViewerCaptionEnterView captionEditText;
    private int sendPhotoType;
    private boolean cropInitied;
    private boolean isDocumentsPicker;
    private boolean needCaptionLayout;
    private AnimatedFileDrawable currentAnimation;
    private boolean allowShare;
    private boolean openedFullScreenVideo;
    private boolean dontChangeCaptionPosition;
    private boolean captionHwLayerEnabled;

    private Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private boolean pipAvailable;

    private Object lastInsets;
    private boolean padImageForHorizontalInsets;

    private boolean doneButtonPressed;
    boolean keyboardAnimationEnabled;
    private Theme.ResourcesProvider resourcesProvider;

    private Runnable setLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentMessageObject == null) {
                return;
            }
            FileLoader.getInstance(currentMessageObject.currentAccount).setLoadingVideo(currentMessageObject.getDocument(), true, false);
        }
    };

    private Runnable hideActionBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoPlayerControlVisible && isPlaying && !ApplicationLoader.mainInterfacePaused) {
                if (menuItem != null && menuItem.isSubMenuShowing() || menuItemSpeed != null && menuItemSpeed.isSubMenuShowing()) {
                    return;
                }
                if (captionScrollView != null && captionScrollView.getScrollY() != 0) {
                    return;
                }
                if (miniProgressView != null && miniProgressView.getVisibility() == View.VISIBLE) {
                    return;
                }
                if (PipInstance == PhotoViewer.this) {
                    return;
                }
                toggleActionBar(false, true);
            }
        }
    };

    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private View flashView;
    private AnimatorSet flashAnimator;
    private TextureView videoTextureView;
    private boolean firstFrameSet = false;
    private ImageView firstFrameView;
    private VideoPlayer videoPlayer;
    private boolean manuallyPaused;
    private Runnable videoPlayRunnable;
    private boolean previousHasTransform;
    private float previousCropPx;
    private float previousCropPy;
    private float previousCropPw;
    private float previousCropPh;
    private float previousCropScale;
    private float previousCropRotation;
    private boolean previousCropMirrored;
    private int previousCropOrientation;
    private VideoPlayer injectingVideoPlayer;
    private SurfaceTexture injectingVideoPlayerSurface;
    private boolean playerInjected;
    private boolean skipFirstBufferingProgress;
    private boolean playerWasReady;
    private boolean playerWasPlaying;
    private boolean playerAutoStarted;
    private boolean playerLooping;
    private float seekToProgressPending;
    private String shouldSavePositionForCurrentVideo;
    private String shouldSavePositionForCurrentVideoShortTerm;
    private ArrayMap<String, SavedVideoPosition> savedVideoPositions = new ArrayMap<>();
    private long lastSaveTime;
    private float seekToProgressPending2;
    private boolean streamingAlertShown;
    private long startedPlayTime;
    private boolean keepScreenOnFlagSet;
    private VideoPlayerControlFrameLayout videoPlayerControlFrameLayout;
    private Animator videoPlayerControlAnimator;
    private boolean videoPlayerControlVisible = true;
    private int[] videoPlayerCurrentTime = new int[2];
    private int[] videoPlayerTotalTime = new int[2];
    private SimpleTextView videoPlayerTime;
    private ImageView exitFullscreenButton;
    private VideoPlayerSeekBar videoPlayerSeekbar;
    private View videoPlayerSeekbarView;
    private VideoSeekPreviewImage videoPreviewFrame;
    private AnimatorSet videoPreviewFrameAnimation;
    private boolean needShowOnReady;
    private PipVideoView pipVideoView;
    private int waitingForDraw;
    private TextureView changedTextureView;
    private ImageView textureImageView;
    private ImageView[] fullscreenButton = new ImageView[3];
    private boolean allowShowFullscreenButton;
    private int[] pipPosition = new int[2];
    private boolean pipAnimationInProgress;
    private Bitmap currentBitmap;
    private boolean changingTextureView;
    private int waitingForFirstTextureUpload;
    private boolean textureUploaded;
    private boolean videoSizeSet;
    private boolean isInline;
    private boolean switchingInlineMode;
    private boolean videoCrossfadeStarted;
    private float videoCrossfadeAlpha;
    private long videoCrossfadeAlphaLastTime;
    private boolean isPlaying;
    private boolean isStreaming;
    private boolean firstAnimationDelay;
    private long lastBufferedPositionCheck;
    private View playButtonAccessibilityOverlay;
    private StickersAlert masksAlert;
    private int lastImageId = -1;

    private OrientationEventListener orientationEventListener;
    private int prevOrientation = -10;
    private int fullscreenedByButton;
    private boolean wasRotated;

    private int keyboardSize;

    private float currentPanTranslationY;

    public final static int SELECT_TYPE_AVATAR = 1;
    public final static int SELECT_TYPE_WALLPAPER = 3;
    public final static int SELECT_TYPE_QR = 10;

    VideoPlayerRewinder videoPlayerRewinder = new VideoPlayerRewinder() {
        @Override
        protected void onRewindCanceled() {
            onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
            videoForwardDrawable.setShowing(false);
        }

        @Override
        protected void updateRewindProgressUi(long timeDiff, float progress, boolean rewindByBackSeek) {
            videoForwardDrawable.setTime(Math.abs(timeDiff));
            if (rewindByBackSeek) {
                videoPlayerSeekbar.setProgress(progress);
                videoPlayerSeekbarView.invalidate();
            }
        }

        @Override
        protected void onRewindStart(boolean rewindForward) {
            videoForwardDrawable.setOneShootAnimation(false);
            videoForwardDrawable.setLeftSide(!rewindForward);
            videoForwardDrawable.setShowing(true);
            containerView.invalidate();
        }
    };

    public final Property<View, Float> FLASH_VIEW_VALUE = new AnimationProperties.FloatProperty<View>("flashViewAlpha") {
        @Override
        public void setValue(View object, float value) {
            object.setAlpha(value);
            if (photoCropView != null) {
                photoCropView.setVideoThumbFlashAlpha(value);
            }
        }

        @Override
        public Float get(View object) {
            return object.getAlpha();
        }
    };

    private TextView captionLimitView;
    private Drawable pickerViewSendDrawable;

    public void addPhoto(MessageObject message, int classGuid) {
        if (classGuid != this.classGuid) {
            return;
        }

        if (imagesByIds[0].indexOfKey(message.getId()) < 0) {
            if (opennedFromMedia) {
                imagesArr.add(message);
            } else {
                imagesArr.add(0, message);
            }
            imagesByIds[0].put(message.getId(), message);
        }
        endReached[0] = imagesArr.size() == totalImagesCount;
        setImages();
    }

    public int getClassGuid() {
        return classGuid;
    }

    public void setCaption(CharSequence caption) {
        hasCaptionForAllMedia = true;
        captionForAllMedia = caption;
    }

    private static class SavedVideoPosition {

        public final float position;
        public final long timestamp;

        public SavedVideoPosition(float position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }
    }

    private class CaptionLinkMovementMethod extends LinkMovementMethod {
        ClickableSpan selectedLink;
        TextView selectedWidget;
        Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedWidget != null && selectedLink instanceof URLSpan) {
                    onLongClick((URLSpan) selectedLink);
                    selectedLink = null;
                }
            }
        };

        @Override
        public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
            try {
                if (!imagesArrLocals.isEmpty()) {
                    return false;
                }
                int action = event.getAction();
                boolean result = false;

                if (action == MotionEvent.ACTION_CANCEL) {
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    selectedLink = null;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                    int x = (int) event.getX();
                    int y = (int) event.getY();

                    x -= widget.getTotalPaddingLeft();
                    y -= widget.getTotalPaddingTop();

                    x += widget.getScrollX();
                    y += widget.getScrollY();

                    Layout layout = widget.getLayout();
                    int line = layout.getLineForVertical(y);
                    int off = layout.getOffsetForHorizontal(line, x);

                    ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);

                    if (links.length != 0) {
                        ClickableSpan link = links[0];
                        if (action == MotionEvent.ACTION_UP) {
                            if (selectedLink != link) {
                                selectedLink = null;
                                return false;
                            }
                            onClick(link, widget);
                            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                            selectedLink = null;
                        } else { // action == MotionEvent.ACTION_DOWN
                            Selection.setSelection(buffer, buffer.getSpanStart(link), buffer.getSpanEnd(link));
                            AndroidUtilities.runOnUIThread(longPressRunnable,  ViewConfiguration.getLongPressTimeout());
                            selectedLink = link;
                            selectedWidget = widget;
                        }
                        result = true;
                    } else {
                        Selection.removeSelection(buffer);
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                        selectedLink = null;
                    }
                }

                return result || Touch.onTouchEvent(widget, buffer, event);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }

        private void onClick(ClickableSpan link, TextView widget) {
            if (widget != null && link instanceof URLSpan) {
                String url = ((URLSpan) link).getURL();
                if (url.startsWith("video")) {
                    if (videoPlayer != null && currentMessageObject != null) {
                        int seconds = Utilities.parseInt(url);
                        if (videoPlayer.getDuration() == C.TIME_UNSET) {
                            seekToProgressPending = seconds / (float) currentMessageObject.getDuration();
                        } else {
                            videoPlayer.seekTo(seconds * 1000L);
                            videoPlayerSeekbar.setProgress(seconds * 1000L / (float) videoPlayer.getDuration(), true);
                            videoPlayerSeekbarView.invalidate();
                        }
                    }
                } else if (url.startsWith("#")) {
                    if (parentActivity instanceof LaunchActivity) {
                        DialogsActivity fragment = new DialogsActivity(null);
                        fragment.setSearchString(url);
                        ((LaunchActivity) parentActivity).presentFragment(fragment, false, true);
                        closePhoto(false, false);
                    }
                } else if (parentChatActivity != null && (link instanceof URLSpanReplacement || AndroidUtilities.shouldShowUrlInAlert(url))) {
                    AlertsCreator.showOpenUrlAlert(parentChatActivity, url, true, true);
                } else {
                    link.onClick(widget);
                }
            } else {
                link.onClick(widget);
            }
        }

        private void onLongClick(URLSpan link) {
            int timestamp = -1;
            BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity, false, resourcesProvider);
            if (link.getURL().startsWith("video?")) {
                try {
                    String timestampStr = link.getURL().substring(link.getURL().indexOf('?') + 1);
                    timestamp = Integer.parseInt(timestampStr);
                } catch (Throwable ignore) {

                }
            }
            if (timestamp >= 0) {
                builder.setTitle(AndroidUtilities.formatDuration(timestamp, false));
            } else {
                builder.setTitle(link.getURL());
            }
            final int finalTimestamp = timestamp;
            builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
                if (which == 0) {
                    onClick(link, selectedWidget);
                } else if (which == 1) {
                    String url1 = link.getURL();
                    boolean tel = false;
                    if (url1.startsWith("mailto:")) {
                        url1 = url1.substring(7);
                    } else if (url1.startsWith("tel:")) {
                        url1 = url1.substring(4);
                        tel = true;
                    } else if (finalTimestamp >= 0) {
                        if (currentMessageObject != null && !currentMessageObject.scheduled) {
                            MessageObject messageObject1 = currentMessageObject;
                            boolean isMedia = currentMessageObject.isVideo() || currentMessageObject.isRoundVideo() || currentMessageObject.isVoice() || currentMessageObject.isMusic();
                            if (!isMedia && currentMessageObject.replyMessageObject != null) {
                                messageObject1 = currentMessageObject.replyMessageObject;
                            }
                            long dialogId = messageObject1.getDialogId();
                            int messageId = messageObject1.getId();

                            if (messageObject1.messageOwner.fwd_from != null) {
                                if (messageObject1.messageOwner.fwd_from.saved_from_peer != null) {
                                    dialogId = MessageObject.getPeerId(messageObject1.messageOwner.fwd_from.saved_from_peer);
                                    messageId = messageObject1.messageOwner.fwd_from.saved_from_msg_id;
                                } else if (messageObject1.messageOwner.fwd_from.from_id != null) {
                                    dialogId = MessageObject.getPeerId(messageObject1.messageOwner.fwd_from.from_id);
                                    messageId = messageObject1.messageOwner.fwd_from.channel_post;
                                }
                            }

                            if (DialogObject.isChatDialog(dialogId)) {
                                TLRPC.Chat currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                                if (currentChat != null && currentChat.username != null) {
                                    url1 = "https://t.me/" + currentChat.username + "/" + messageId + "?t=" + finalTimestamp;
                                }
                            } else {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                                if (user != null && user.username != null) {
                                    url1 = "https://t.me/" + user.username + "/" + messageId + "?t=" + finalTimestamp;
                                }
                            }
                        }
                    }
                    AndroidUtilities.addToClipboard(url1);
                    String bulletinMessage;
                    if (tel) {
                        bulletinMessage = LocaleController.getString("PhoneCopied", R.string.PhoneCopied);
                    } else if (url1.startsWith("#")) {
                        bulletinMessage = LocaleController.getString("HashtagCopied", R.string.HashtagCopied);
                    } else if (url1.startsWith("@")) {
                        bulletinMessage = LocaleController.getString("UsernameCopied", R.string.UsernameCopied);
                    } else {
                        bulletinMessage = LocaleController.getString("LinkCopied", R.string.LinkCopied);
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        BulletinFactory.of(containerView, resourcesProvider).createSimpleBulletin(R.raw.voip_invite, bulletinMessage).show();
                    }
                }
            });
            BottomSheet bottomSheet = builder.create();
            bottomSheet.show();
            bottomSheet.setItemColor(0,0xffffffff, 0xffffffff);
            bottomSheet.setItemColor(1,0xffffffff, 0xffffffff);
            bottomSheet.setBackgroundColor(0xff1C2229);
            bottomSheet.setTitleColor(0xff8A8A8A);
        }
    }

    private void cancelFlashAnimations() {
        if (flashView != null) {
            flashView.animate().setListener(null).cancel();
            flashView.setAlpha(0.0f);
        }
        if (flashAnimator != null) {
            flashAnimator.cancel();
            flashAnimator = null;
        }
        if (photoCropView != null) {
            photoCropView.cancelThumbAnimation();
        }
    }

    private void cancelVideoPlayRunnable() {
        if (videoPlayRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(videoPlayRunnable);
            videoPlayRunnable = null;
        }
    }

    private Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer != null) {
                if (isCurrentVideo) {
                    if (!videoTimelineView.isDragging()) {
                        float progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                        if (shownControlsByEnd && !actionBarWasShownBeforeByEnd) {
                            progress = 0;
                        }
                        if (!inPreview && (currentEditMode != 0 || videoTimelineView.getVisibility() == View.VISIBLE)) {
                            if (progress >= videoTimelineView.getRightProgress()) {
                                videoTimelineView.setProgress(videoTimelineView.getLeftProgress());
                                videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                                manuallyPaused = false;
                                cancelVideoPlayRunnable();
                                if (muteVideo || sendPhotoType == SELECT_TYPE_AVATAR || currentEditMode != 0 || switchingToMode > 0) {
                                    videoPlayer.play();
                                } else {
                                    videoPlayer.pause();
                                }
                                containerView.invalidate();
                            } else {
                                videoTimelineView.setProgress(progress);
                            }
                        } else if (sendPhotoType != SELECT_TYPE_AVATAR) {
                            videoTimelineView.setProgress(progress);
                        }
                        updateVideoPlayerTime();
                    }
                } else {
                    float progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                    if (shownControlsByEnd && !actionBarWasShownBeforeByEnd) {
                        progress = 0;
                    }
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
                            manuallyPaused = false;
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
                        if (seekToProgressPending == 0 && (videoPlayerRewinder.rewindCount == 0 || !videoPlayerRewinder.rewindByBackSeek)) {
                            videoPlayerSeekbar.setProgress(progress, false);
                        }
                        if (bufferedProgress != -1) {
                            videoPlayerSeekbar.setBufferedProgress(bufferedProgress);
                            if (pipVideoView != null) {
                                pipVideoView.setBufferedProgress(bufferedProgress);
                            }
                        }
                    }
                    videoPlayerSeekbarView.invalidate();
                    if (shouldSavePositionForCurrentVideo != null) {
                        float value = progress;
                        if (value >= 0 && SystemClock.elapsedRealtime() - lastSaveTime >= 1000) {
                            String saveFor = shouldSavePositionForCurrentVideo;
                            lastSaveTime = SystemClock.elapsedRealtime();
                            Utilities.globalQueue.postRunnable(() -> {
                                SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE).edit();
                                editor.putFloat(shouldSavePositionForCurrentVideo, value).commit();
                            });
                        }
                    }
                    updateVideoPlayerTime();
                }
                updateFirstFrameView();
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

            pipVideoView = new PipVideoView(false);
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
                        AndroidUtilities.runOnUIThread(() -> {
                            if (isInline) {
                                dismissInternal();
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

    private float[][] animationValues = new float[2][13];

    private ChatActivity parentChatActivity;
    private MentionsAdapter mentionsAdapter;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private AnimatorSet mentionListAnimation;
    private boolean allowMentions;

    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;

    private int animationInProgress;
    private boolean openAnimationInProgress;
    private long transitionAnimationStartTime;
    private Runnable animationEndRunnable;
    private PlaceProviderObject showAfterAnimation;
    private PlaceProviderObject hideAfterAnimation;
    private boolean disableShowCheck;

    private String lastTitle;

    private boolean isEmbedVideo;

    private int currentEditMode;

    private final Runnable updateContainerFlagsRunnable = () -> {
        if (isVisible && animationInProgress == 0) {
            updateContainerFlags(isActionBarVisible);
        }
    };

    private static class EditState {
        public String paintPath;
        public String croppedPaintPath;
        public MediaController.CropState cropState;
        public MediaController.SavedFilterState savedFilterState;
        public ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
        public ArrayList<VideoEditedInfo.MediaEntity> croppedMediaEntities;
        public long averageDuration;

        public void reset() {
            paintPath = null;
            cropState = null;
            savedFilterState = null;
            mediaEntities = null;
            croppedPaintPath = null;
            croppedMediaEntities = null;
            averageDuration = 0;
        }
    }

    private class SavedState {

        private int index;
        private ArrayList<MessageObject> messages;
        private PhotoViewerProvider provider;

        public SavedState(int index, ArrayList<MessageObject> messages, PhotoViewerProvider provider) {
            this.messages = messages;
            this.index = index;
            this.provider = provider;
        }

        public void restore() {
            placeProvider = provider;

            if (Build.VERSION.SDK_INT >= 21) {
                windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            } else {
                windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }
            windowLayoutParams.softInputMode = (useSmoothKeyboard ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
            windowView.setFocusable(false);
            containerView.setFocusable(false);
            backgroundDrawable.setAlpha(255);
            containerView.setAlpha(1.0f);

            onPhotoShow(null, null, null, null, messages, null, null, index, provider.getPlaceForPhoto(messages.get(index), null, index, true));
        }
    }

    private int currentImageHasFace;
    private String currentImageFaceKey;
    private PaintingOverlay paintingOverlay;
    private ImageReceiver leftImage = new ImageReceiver();
    private ImageReceiver centerImage = new ImageReceiver();
    private Paint videoFrameBitmapPaint = new Paint();
    private Bitmap videoFrameBitmap = null;
    private ImageReceiver rightImage = new ImageReceiver();
    private int currentIndex;
    private int switchingToIndex;
    private MessageObject currentMessageObject;
    private Uri currentPlayingVideoFile;
    private EditState editState = new EditState();
    private TLRPC.BotInlineResult currentBotInlineResult;
    private ImageLocation currentFileLocation;
    private ImageLocation currentFileLocationVideo;
    private SecureDocument currentSecureDocument;
    private String[] currentFileNames = new String[3];
    private PlaceProviderObject currentPlaceObject;
    private String currentPathObject;
    private String currentImagePath;
    private boolean currentVideoFinishedLoading;
    private TLRPC.PageBlock currentPageBlock;
    private ImageReceiver.BitmapHolder currentThumb;
    private boolean ignoreDidSetImage;
    private boolean dontAutoPlay;
    boolean fromCamera;

    private long avatarsDialogId;
    private boolean canEditAvatar;
    private boolean isEvent;
    private int sharedMediaType;
    private long currentDialogId;
    private long mergeDialogId;
    private int totalImagesCount;
    private int startOffset;
    private int totalImagesCountMerge;
    private boolean isFirstLoading;
    private boolean needSearchImageInArr;
    private boolean loadingMoreImages;
    private boolean[] endReached = new boolean[]{false, true};
    private boolean startReached = false;
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
    private int switchingToMode = -1;
    private AnimatorSet imageMoveAnimation;
    private AnimatorSet changeModeAnimation;
    private GestureDetector2 gestureDetector;
    private boolean doubleTapEnabled;
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
    private int paintViewTouched;
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
    private ArrayList<ImageLocation> imagesArrLocations = new ArrayList<>();
    private ArrayList<ImageLocation> imagesArrLocationsVideo = new ArrayList<>();
    private ArrayList<Integer> imagesArrLocationsSizes = new ArrayList<>();
    private ArrayList<TLRPC.Message> imagesArrMessages = new ArrayList<>();
    private ArrayList<SecureDocument> secureDocuments = new ArrayList<>();
    private ArrayList<TLRPC.Photo> avatarsArr = new ArrayList<>();
    private ArrayList<Object> imagesArrLocals = new ArrayList<>();
    private ImageLocation currentAvatarLocation = null;
    private SavedState savedState = null;

    private PageBlocksAdapter pageBlocksAdapter;

    public interface PageBlocksAdapter {
        int getItemsCount();
        TLRPC.PageBlock get(int index);
        List<TLRPC.PageBlock> getAll();
        boolean isVideo(int index);
        TLObject getMedia(int index);
        File getFile(int index);
        String getFileName(int index);
        CharSequence getCaption(int index);
        TLRPC.PhotoSize getFileLocation(TLObject media, int[] size);
        void updateSlideshowCell(TLRPC.PageBlock currentPageBlock);
        Object getParentObject();
    }

    private android.graphics.Rect hitRect = new android.graphics.Rect();

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
    private final static int gallery_menu_savegif = 14;
    private final static int gallery_menu_masks2 = 15;
    private final static int gallery_menu_set_as_main = 16;
    private final static int gallery_menu_edit_avatar = 17;
    private final static int gallery_menu_share2 = 18;
    private final static int gallery_menu_speed = 19;
    private final static int gallery_menu_gap = 20;
    private final static int gallery_menu_speed_veryslow = 21;
    private final static int gallery_menu_speed_slow = 22;
    private final static int gallery_menu_speed_normal = 23;
    private final static int gallery_menu_speed_fast = 24;
    private final static int gallery_menu_speed_veryfast = 25;

    private static DecelerateInterpolator decelerateInterpolator;
    private static Paint progressPaint;

    private int transitionIndex;

    private class BackgroundDrawable extends ColorDrawable {

        private final RectF rect = new RectF();
        private final RectF visibleRect = new RectF();

        private final Paint paint;

        private Runnable drawRunnable;
        private boolean allowDrawContent;

        public BackgroundDrawable(int color) {
            super(color);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
        }

        @Keep
        @Override
        public void setAlpha(int alpha) {
            if (parentActivity instanceof LaunchActivity) {
                allowDrawContent = !isVisible || alpha != 255;
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(allowDrawContent);
                if (parentAlert != null) {
                    if (!allowDrawContent) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (parentAlert != null) {
                                parentAlert.setAllowDrawContent(allowDrawContent);
                            }
                        }, 50);
                    } else {
                        parentAlert.setAllowDrawContent(true);
                    }
                }
            }
            super.setAlpha(alpha);
            paint.setAlpha(alpha);
        }

        @Override
        public void draw(Canvas canvas) {
            if (animationInProgress != 0 && !AndroidUtilities.isTablet() && currentPlaceObject != null && currentPlaceObject.animatingImageView != null) {
                animatingImageView.getClippedVisibleRect(visibleRect);
                if (!visibleRect.isEmpty()) {
                    visibleRect.inset(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f));

                    final Rect boundsRect = getBounds();
                    final float width = boundsRect.right;
                    final float height = boundsRect.bottom;

                    for (int i = 0; i < 4; i++) {
                        switch (i) {
                            case 0: // left rect
                                rect.set(0, visibleRect.top, visibleRect.left, visibleRect.bottom);
                                break;
                            case 1: // top rect
                                rect.set(0, 0, width, visibleRect.top);
                                break;
                            case 2: // right rect
                                rect.set(visibleRect.right, visibleRect.top, width, visibleRect.bottom);
                                break;
                            case 3: // bottom rect
                                rect.set(0, visibleRect.bottom, width, height);
                                break;
                        }
                        canvas.drawRect(rect, paint);
                    }
                }
            } else {
                super.draw(canvas);
            }
            if (getAlpha() != 0) {
                if (drawRunnable != null) {
                    AndroidUtilities.runOnUIThread(drawRunnable);
                    drawRunnable = null;
                }
            }
        }
    }

    private static class SelectedPhotosListView extends RecyclerListView {

        private Drawable arrowDrawable;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();

        public SelectedPhotosListView(Context context) {
            super(context);
            setWillNotDraw(false);

            setClipToPadding(false);
            setTranslationY(-AndroidUtilities.dp(10));
            DefaultItemAnimator defaultItemAnimator;
            setItemAnimator(defaultItemAnimator = new DefaultItemAnimator() {
                @Override
                protected void onMoveAnimationUpdate(ViewHolder holder) {
                    invalidate();
                }
            });
            defaultItemAnimator.setDelayAnimations(false);
            defaultItemAnimator.setSupportsChangeAnimations(false);
            setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
            paint.setColor(0x7f000000);

            arrowDrawable = context.getResources().getDrawable(R.drawable.photo_tooltip2).mutate();
        }

        @Override
        public void onDraw(Canvas c) {
            super.onDraw(c);

            int count = getChildCount();
            if (count > 0) {
                int x = getMeasuredWidth() - AndroidUtilities.dp(87);
                arrowDrawable.setBounds(x, 0, x + arrowDrawable.getIntrinsicWidth(), AndroidUtilities.dp(6));
                arrowDrawable.draw(c);

                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                for (int a = 0; a < count; a++) {
                    View v = getChildAt(a);
                    minX = (int) Math.min(minX, Math.floor(v.getX()));
                    maxX = (int) Math.max(maxX, Math.ceil(v.getX() + v.getMeasuredWidth()));
                }
                if (minX != Integer.MAX_VALUE && maxX != Integer.MIN_VALUE) {
                    rect.set(minX - AndroidUtilities.dp(6), AndroidUtilities.dp(6), maxX + AndroidUtilities.dp(6), AndroidUtilities.dp(6 + 85 + 12));
                    c.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);
                }
            }
        }
    }

    private static class CounterView extends View {

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
            textPaint.setTextSize(AndroidUtilities.dp(15));
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
                        ObjectAnimator.ofFloat(this, View.SCALE_X, 0.0f),
                        ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.0f),
                        ObjectAnimator.ofInt(paint, AnimationProperties.PAINT_ALPHA, 0),
                        ObjectAnimator.ofInt(textPaint, AnimationProperties.PAINT_ALPHA, 0));
                animatorSet.setInterpolator(new DecelerateInterpolator());
            } else if (currentCount == 0) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, View.SCALE_X, 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.0f, 1.0f),
                        ObjectAnimator.ofInt(paint, AnimationProperties.PAINT_ALPHA, 0, 255),
                        ObjectAnimator.ofInt(textPaint, AnimationProperties.PAINT_ALPHA, 0, 255));
                animatorSet.setInterpolator(new DecelerateInterpolator());
            } else if (value < currentCount) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, View.SCALE_X, 1.1f, 1.0f),
                        ObjectAnimator.ofFloat(this, View.SCALE_Y, 1.1f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
            } else {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, View.SCALE_X, 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.9f, 1.0f));
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
                cy -= AndroidUtilities.dp(5) * (1.0f - rotation);
                canvas.drawLine(cx + AndroidUtilities.dp(5), cy - AndroidUtilities.dp(5), cx - AndroidUtilities.dp(5), cy + AndroidUtilities.dp(5), paint);
                canvas.drawLine(cx - AndroidUtilities.dp(5), cy - AndroidUtilities.dp(5), cx + AndroidUtilities.dp(5), cy + AndroidUtilities.dp(5), paint);
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
        private float[] animAlphas = new float[3];
        private float[] alphas = new float[3];
        private float scale = 1.0f;
        private boolean visible;

        private final CombinedDrawable playDrawable;
        private final PlayPauseDrawable playPauseDrawable;

        public PhotoProgressView(View parentView) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator(1.5f);
                progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                progressPaint.setStyle(Paint.Style.STROKE);
                progressPaint.setStrokeCap(Paint.Cap.ROUND);
                progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
                progressPaint.setColor(0xffffffff);
            }
            parent = parentView;
            resetAlphas();

            playPauseDrawable = new PlayPauseDrawable(28);
            playPauseDrawable.setDuration(200);

            final Drawable circleDrawable = ContextCompat.getDrawable(parentActivity, R.drawable.circle_big);
            playDrawable = new CombinedDrawable(circleDrawable.mutate(), playPauseDrawable);
        }

        private void updateAnimation(boolean withProgressAnimation) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt > 18) {
                dt = 18;
            }
            lastUpdateTime = newTime;

            boolean postInvalidate = false;

            if (withProgressAnimation) {
                if (animatedProgressValue != 1 || currentProgress != 1) {
                    radOffset += 360 * dt / 3000.0f;
                    float progressDiff = currentProgress - animationProgressStart;
                    if (Math.abs(progressDiff) > 0) {
                        currentProgressTime += dt;
                        if (currentProgressTime >= 300) {
                            animatedProgressValue = currentProgress;
                            animationProgressStart = currentProgress;
                            currentProgressTime = 0;
                        } else {
                            animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                        }
                    }
                    postInvalidate = true;
                }

                if (animatedAlphaValue > 0 && previousBackgroundState != -2) {
                    animatedAlphaValue -= dt / 200.0f;
                    if (animatedAlphaValue <= 0) {
                        animatedAlphaValue = 0.0f;
                        previousBackgroundState = -2;
                    }
                    postInvalidate = true;
                }
            }

            for (int i = 0; i < alphas.length; i++) {
                if (alphas[i] > animAlphas[i]) {
                    animAlphas[i] = Math.min(1f, animAlphas[i] + dt / 200f);
                    postInvalidate = true;
                } else if (alphas[i] < animAlphas[i]) {
                    animAlphas[i] = Math.max(0f, animAlphas[i] - dt / 200f);
                    postInvalidate = true;
                }
            }

            if (postInvalidate) {
                parent.postInvalidateOnAnimation();
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
            parent.invalidate();
        }

        public void setBackgroundState(int state, boolean animated, boolean animateIcon) {
            if (backgroundState == state) {
                return;
            }
            if (playPauseDrawable != null) {
                boolean animatePlayPause = animateIcon && (backgroundState == 3 || backgroundState == 4);
                if (state == 3) {
                    playPauseDrawable.setPause(false, animatePlayPause);
                } else if (state == 4) {
                    playPauseDrawable.setPause(true, animatePlayPause);
                }
                playPauseDrawable.setParent(parent);
                playPauseDrawable.invalidateSelf();
            }
            lastUpdateTime = System.currentTimeMillis();
            if (animated && backgroundState != state) {
                previousBackgroundState = backgroundState;
                animatedAlphaValue = 1.0f;
            } else {
                previousBackgroundState = -2;
            }
            onBackgroundStateUpdated(backgroundState = state);
            parent.invalidate();
        }

        protected void onBackgroundStateUpdated(int state) {
        }

        public void setAlpha(float value) {
            setIndexedAlpha(0, value, false);
        }

        public void setScale(float value) {
            scale = value;
        }

        public void setIndexedAlpha(int index, float alpha, boolean animated) {
            if (alphas[index] != alpha) {
                alphas[index] = alpha;
                if (!animated) {
                    animAlphas[index] = alpha;
                }
                checkVisibility();
                parent.invalidate();
            }
        }

        public void resetAlphas() {
            for (int i = 0; i < alphas.length; i++) {
                alphas[i] = animAlphas[i] = 1.0f;
            }
            checkVisibility();
        }

        private float calculateAlpha() {
            float alpha = 1.0f;
            for (int i = 0; i < animAlphas.length; i++) {
                if (i == 2) {
                    alpha *= AndroidUtilities.accelerateInterpolator.getInterpolation(animAlphas[i]);
                } else {
                    alpha *= animAlphas[i];
                }
            }
            return alpha;
        }

        private void checkVisibility() {
            boolean newVisible = true;
            for (int i = 0; i < alphas.length; i++) {
                if (alphas[i] != 1.0f) {
                    newVisible = false;
                    break;
                }
            }
            if (newVisible != visible) {
                visible = newVisible;
                onVisibilityChanged(visible);
            }
        }

        protected void onVisibilityChanged(boolean visible) {
        }

        public boolean isVisible() {
            return visible;
        }

        public int getX() {
            return (containerView.getWidth() - (int) (size * scale)) / 2;
        }

        public int getY() {
            int y = ((AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0)) - (int) (size * scale)) / 2;
            y += currentPanTranslationY;
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                y -= AndroidUtilities.dp(38);
            }
            return y;
        }

        public void onDraw(Canvas canvas) {
            int sizeScaled = (int) (size * scale);
            int x = getX();
            int y = getY();

            final float alpha = calculateAlpha();

            if (previousBackgroundState >= 0 && previousBackgroundState < progressDrawables.length + 2) {
                Drawable drawable;
                if (previousBackgroundState < progressDrawables.length) {
                    drawable = progressDrawables[previousBackgroundState];
                } else {
                    drawable = playDrawable;
                }
                if (drawable != null) {
                    drawable.setAlpha((int) (255 * animatedAlphaValue * alpha));
                    drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState >= 0 && backgroundState < progressDrawables.length + 2) {
                Drawable drawable;
                if (backgroundState < progressDrawables.length) {
                    drawable = progressDrawables[backgroundState];
                } else {
                    drawable = playDrawable;
                }
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

            if (backgroundState == PROGRESS_EMPTY || backgroundState == PROGRESS_CANCEL || previousBackgroundState == PROGRESS_EMPTY || previousBackgroundState == PROGRESS_CANCEL) {
                int diff = AndroidUtilities.dp(4);
                if (previousBackgroundState != -2) {
                    progressPaint.setAlpha((int) (255 * animatedAlphaValue * alpha));
                } else {
                    progressPaint.setAlpha((int) (255 * alpha));
                }
                progressRect.set(x + diff, y + diff, x + sizeScaled - diff, y + sizeScaled - diff);
                canvas.drawArc(progressRect, -90 + radOffset, Math.max(4, 360 * animatedProgressValue), false, progressPaint);
                updateAnimation(true);
            } else {
                updateAnimation(false);
            }
        }
    }

    public static class PlaceProviderObject {
        public ImageReceiver imageReceiver;
        public int viewX;
        public int viewY;
        public int viewY2;
        public View parentView;
        public ImageReceiver.BitmapHolder thumb;
        public long dialogId;
        public int index;
        public int size;
        public int[] radius;
        public int clipBottomAddition;
        public int clipTopAddition;
        public float scale = 1.0f;
        public boolean isEvent;
        public ClippingImageView animatingImageView;
        public int animatingImageViewYOffset;
        public boolean allowTakeAnimation = true;
        public boolean canEdit;
        public int starOffset;
    }

    public static class EmptyPhotoViewerProvider implements PhotoViewerProvider {
        @Override
        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
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
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {

        }

        @Override
        public void replaceButtonPressed(int index, VideoEditedInfo videoEditedInfo) {

        }

        @Override
        public boolean canReplace(int index) {
            return false;
        }

        @Override
        public int getSelectedCount() {
            return 0;
        }

        @Override
        public void updatePhotoAtIndex(int index) {

        }

        @Override
        public boolean allowSendingSubmenu() {
            return true;
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

        @Override
        public void openPhotoForEdit(String file, String thumb, boolean isVideo) {

        }

        @Override
        public int getTotalImageCount() {
            return -1;
        }

        @Override
        public boolean loadMore() {

            return false;
        }

        @Override
        public CharSequence getTitleFor(int i) {
            return null;
        }

        @Override
        public CharSequence getSubtitleFor(int i) {
            return null;
        }

        @Override
        public MessageObject getEditingMessageObject() {
            return null;
        }

        @Override
        public void onCaptionChanged(CharSequence caption) {
        }

        @Override
        public boolean closeKeyboard() {
            return false;
        }

        @Override
        public boolean validateGroupId(long groupId) {
            return true;
        }

        @Override
        public void onApplyCaption(CharSequence caption) {

        }
    }

    public interface PhotoViewerProvider {
        PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview);
        ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);
        void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index);
        void willHidePhotoViewer();
        boolean isPhotoChecked(int index);
        int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo);
        int setPhotoUnchecked(Object photoEntry);
        boolean cancelButtonPressed();
        void needAddMorePhotos();
        void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument);
        void replaceButtonPressed(int index, VideoEditedInfo videoEditedInfo);
        boolean canReplace(int index);
        int getSelectedCount();
        void updatePhotoAtIndex(int index);
        boolean allowSendingSubmenu();
        boolean allowCaption();
        boolean scaleToFill();
        ArrayList<Object> getSelectedPhotosOrder();
        HashMap<Object, Object> getSelectedPhotos();
        boolean canScrollAway();
        int getPhotoIndex(int index);
        void deleteImageAtIndex(int index);
        String getDeleteMessageString();
        boolean canCaptureMorePhotos();
        void openPhotoForEdit(String file, String thumb, boolean isVideo);
        int getTotalImageCount();
        boolean loadMore();
        CharSequence getTitleFor(int index);
        CharSequence getSubtitleFor(int index);
        MessageObject getEditingMessageObject();
        void onCaptionChanged(CharSequence caption);
        boolean closeKeyboard();
        boolean validateGroupId(long groupId);
        void onApplyCaption(CharSequence caption);
    }

    private class FrameLayoutDrawer extends SizeNotifierFrameLayoutPhoto {

        private Paint paint = new Paint();
        private boolean ignoreLayout;
        private boolean captionAbove;

        AdjustPanLayoutHelper adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {
            @Override
            protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                currentPanTranslationY = y;
                if (currentEditMode != 3) {
                    actionBar.setTranslationY(y);
                }
                if (miniProgressView != null) {
                    miniProgressView.setTranslationY(y);
                }
                if (progressView != null) {
                    progressView.setTranslationY(y);
                }
                if (checkImageView != null) {
                    checkImageView.setTranslationY(y);
                }
                if (photosCounterView != null) {
                    photosCounterView.setTranslationY(y);
                }
                if (selectedPhotosListView != null) {
                    selectedPhotosListView.setTranslationY(y);
                }
                if (aspectRatioFrameLayout != null) {
                    aspectRatioFrameLayout.setTranslationY(y);
                }
                if (textureImageView != null) {
                    textureImageView.setTranslationY(y);
                }
                if (photoCropView != null) {
                    photoCropView.setTranslationY(y);
                }
                if (photoFilterView != null) {
                    photoFilterView.setTranslationY(y);
                }
//
                if (pickerView != null) {
                    pickerView.setTranslationY(y);
                }
                if (pickerViewSendButton != null) {
                    pickerViewSendButton.setTranslationY(y);
                }

                if (currentEditMode == 3) {
                    if (captionEditText != null) {
                        captionEditText.setTranslationY(y);
                    }

                    if (photoPaintView != null) {
                        photoPaintView.setTranslationY(0);
                        photoPaintView.getColorPicker().setTranslationY(y);
                        photoPaintView.getToolsView().setTranslationY(y);
                        photoPaintView.getCurtainView().setTranslationY(y);
                    }
                } else {

                    if (photoPaintView != null) {
                        photoPaintView.setTranslationY(y);
                    }
                    if (captionEditText != null) {
                        float p = progress < 0.5f ? 0 : (progress - 0.5f) / 0.5f;
                        captionEditText.setAlpha(p);
                        captionEditText.setTranslationY(y - this.keyboardSize + AndroidUtilities.dp(keyboardSize / 2) * (1f - progress));
                    }
                }
                if (muteItem != null) {
                    muteItem.setTranslationY(y);
                }
                if (cameraItem != null) {
                    cameraItem.setTranslationY(y);
                }
                if (captionLimitView != null) {
                    captionLimitView.setTranslationY(y);
                }
                invalidate();
            }

            @Override
            protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                windowView.setClipChildren(false);
                if (captionEditText.getTag() != null && keyboardVisible) {
                    if (isCurrentVideo) {
                        CharSequence title = muteVideo ? LocaleController.getString("GifCaption", R.string.GifCaption) : LocaleController.getString("VideoCaption", R.string.VideoCaption);
                        actionBar.setTitleAnimated(title, true, 220);
                    } else {
                        actionBar.setTitleAnimated(LocaleController.getString("PhotoCaption", R.string.PhotoCaption), true, 220);
                    }

                    captionEditText.setAlpha(0f);
                    checkImageView.animate().alpha(0f).setDuration(220).start();
                    photosCounterView.animate().alpha(0f).setDuration(220).start();
                    selectedPhotosListView.animate().alpha(0.0f).translationY(-AndroidUtilities.dp(10)).setDuration(220).start();
                } else {
                    checkImageView.animate().alpha(1f).setDuration(220).start();
                    photosCounterView.animate().alpha(1f).setDuration(220).start();
                    if (lastTitle != null) {
                        if (!isCurrentVideo) {
                            actionBar.setTitleAnimated(lastTitle, false, 220);
                            lastTitle = null;
                        }
                    }
                }
            }

            @Override
            protected void onTransitionEnd() {
                super.onTransitionEnd();
                windowView.setClipChildren(true);
                if (captionEditText.getTag() == null) {
                    captionEditText.setVisibility(View.GONE);
                }
                captionEditText.setTranslationY(0);
            }

            @Override
            protected boolean heightAnimationEnabled() {
                return !captionEditText.isPopupShowing() && keyboardAnimationEnabled;
            }
        };

        public FrameLayoutDrawer(Context context) {
            super(context, false);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            paint.setColor(0x33000000);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            if (getLayoutParams().height > 0) {
                heightSize = getLayoutParams().height;
            }

            setMeasuredDimension(widthSize, heightSize);

            if (!isCurrentVideo) {
                ignoreLayout = true;
                if (needCaptionLayout) {
                    final int maxLines = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 5 : 10;
                    captionTextViewSwitcher.getCurrentView().setMaxLines(maxLines);
                    captionTextViewSwitcher.getNextView().setMaxLines(maxLines);
                } else {
                    captionTextViewSwitcher.getCurrentView().setMaxLines(Integer.MAX_VALUE);
                    captionTextViewSwitcher.getNextView().setMaxLines(Integer.MAX_VALUE);
                }
                ignoreLayout = false;
            }

            measureChildWithMargins(captionEditText, widthMeasureSpec, 0, heightMeasureSpec, 0);
            int inputFieldHeight = captionEditText.getMeasuredHeight();

            final int bottomLayoutHeight = bottomLayout.getVisibility() != GONE ? AndroidUtilities.dp(48) : 0;

            final int groupedPhotosHeight;
            if (groupedPhotosListView != null && groupedPhotosListView.getVisibility() != GONE) {
                MarginLayoutParams lp = (MarginLayoutParams) groupedPhotosListView.getLayoutParams();
                lp.bottomMargin = bottomLayoutHeight;
                measureChildWithMargins(groupedPhotosListView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                groupedPhotosHeight = groupedPhotosListView.getMeasuredHeight();

                ignoreLayout = true;
                if (!AndroidUtilities.isTablet() && heightSize < widthSize) {
                    if (groupedPhotosListView.getVisibility() != INVISIBLE) {
                        groupedPhotosListView.setVisibility(INVISIBLE);
                    }
                } else {
                    if (groupedPhotosListView.getVisibility() != VISIBLE) {
                        groupedPhotosListView.setVisibility(VISIBLE);
                    }
                }
                ignoreLayout = false;
            } else {
                groupedPhotosHeight = 0;
            }

            if (videoPlayerControlFrameLayout != null) {
                videoPlayerControlFrameLayout.parentWidth = widthSize;
                videoPlayerControlFrameLayout.parentHeight = heightSize;
            }

            widthSize -= (getPaddingRight() + getPaddingLeft());
            heightSize -= getPaddingBottom();

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == captionEditText || child == groupedPhotosListView) {
                    continue;
                }
                if (child == aspectRatioFrameLayout) {
                    int heightSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0), MeasureSpec.EXACTLY);
                    child.measure(widthMeasureSpec, heightSpec);
                } else if (child == paintingOverlay) {
                    int width;
                    int height;
                    if (aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == VISIBLE) {
                        width = videoTextureView.getMeasuredWidth();
                        height = videoTextureView.getMeasuredHeight();
                    } else {
                        width = centerImage.getBitmapWidth();
                        height = centerImage.getBitmapHeight();
                    }
                    if (width == 0 || height == 0) {
                        width = widthSize;
                        height = heightSize;
                    }
                    paintingOverlay.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                } else if (captionEditText.isPopupView(child)) {
                    if (inBubbleMode) {
                        child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight, MeasureSpec.EXACTLY));
                    } else if (AndroidUtilities.isInMultiwindow) {
                        if (AndroidUtilities.isTablet()) {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(320), heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight), MeasureSpec.EXACTLY));
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
                        }
                    } else {
                        child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                    }
                } else if (child == captionScrollView) {
                    int bottomMargin = bottomLayoutHeight;
                    if (dontChangeCaptionPosition) {
                        if (captionAbove) {
                            bottomMargin += groupedPhotosHeight;
                        }
                    } else if (groupedPhotosListView.hasPhotos() && (AndroidUtilities.isTablet() || heightSize > widthSize)) {
                        bottomMargin += groupedPhotosHeight;
                        captionAbove = true;
                    } else {
                        captionAbove = false;
                    }
                    final int topMargin = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                    final int height = heightSize - topMargin - bottomMargin;
                    ((MarginLayoutParams) captionScrollView.getLayoutParams()).bottomMargin = bottomMargin;
                    child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int _l, int t, int _r, int _b) {
            final int count = getChildCount();
            int keyboardHeight = getKeyboardHeight();
            keyboardSize = keyboardHeight;
            int paddingBottom = keyboardHeight <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? captionEditText.getEmojiPadding() : 0;

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                int l, r, b;
                if (child == aspectRatioFrameLayout) {
                    l = _l;
                    r = _r;
                    b = _b;
                } else {
                    l = _l + getPaddingLeft();
                    r = _r - getPaddingRight();
                    b = _b - getPaddingBottom();
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
                    case Gravity.CENTER_VERTICAL:
                        childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                        break;
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
                    childTop = actionBar.getMeasuredHeight() + AndroidUtilities.dp(5);
                } else if (child == cameraItem || child == muteItem) {
                    final int top;

                    if (videoTimelineView != null && videoTimelineView.getVisibility() == VISIBLE) {
                        top = videoTimelineView.getTop();
                    } else {
                        top = pickerView.getTop();
                    }

                    childTop = top - AndroidUtilities.dp(sendPhotoType == 4 || sendPhotoType == 5 ? 40 : 15) - child.getMeasuredHeight();
                } else if (child == videoTimelineView) {
                    childTop -= pickerView.getHeight();
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        childTop -= AndroidUtilities.dp(52);
                    }
                } else if (child == videoAvatarTooltip) {
                    childTop -= pickerView.getHeight() + AndroidUtilities.dp(31);
                }
                child.layout(childLeft + l, childTop, childLeft + width + l, childTop + height);
            }

            notifyHeightChanged();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            PhotoViewer.this.onDraw(canvas);

            if (isStatusBarVisible() && AndroidUtilities.statusBarHeight != 0 && actionBar != null) {
                paint.setAlpha((int) (255 * actionBar.getAlpha() * 0.2f));
                canvas.drawRect(0, currentPanTranslationY, getMeasuredWidth(), currentPanTranslationY + AndroidUtilities.statusBarHeight, paint);
                paint.setAlpha((int) (255 * actionBar.getAlpha() * 0.498f));
                if (getPaddingRight() > 0) {
                    canvas.drawRect(getMeasuredWidth() - getPaddingRight(), 0, getMeasuredWidth(), getMeasuredHeight(), paint);
                }
                if (getPaddingLeft() > 0) {
                    canvas.drawRect(0, 0, getPaddingLeft(), getMeasuredHeight(), paint);
                }
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == mentionListView || child == captionEditText) {
                if (currentEditMode != 0 && currentPanTranslationY == 0) {
                    return false;
                } else if (AndroidUtilities.isInMultiwindow || AndroidUtilities.usingHardwareInput) {
                    if (!captionEditText.isPopupShowing() && captionEditText.getEmojiPadding() == 0 && captionEditText.getTag() == null) {
                        return false;
                    }
                } else if (!captionEditText.isPopupShowing() && captionEditText.getEmojiPadding() == 0 && getKeyboardHeight() == 0) {
                    if (currentPanTranslationY == 0) {
                        return false;
                    }
                }
                if (child == mentionListView) {
                    canvas.save();
                    canvas.clipRect(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight());
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
            } else if (child == cameraItem || child == muteItem || child == pickerView || child == videoTimelineView || child == pickerViewSendButton || child == captionLimitView || child == captionTextViewSwitcher || muteItem.getVisibility() == VISIBLE && child == bottomLayout) {
                if (captionEditText.isPopupAnimatig()) {
                    child.setTranslationY(captionEditText.getEmojiPadding());
                    bottomTouchEnabled = false;
                } else {
                    int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? captionEditText.getEmojiPadding() : 0;
                    if (captionEditText.isPopupShowing() || (AndroidUtilities.isInMultiwindow || AndroidUtilities.usingHardwareInput) && captionEditText.getTag() != null || getKeyboardHeight() > AndroidUtilities.dp(80) || paddingBottom != 0) {
                        bottomTouchEnabled = false;
                        return false;
                    } else {
                        bottomTouchEnabled = true;
                    }
                }
            } else if (child == checkImageView || child == photosCounterView) {
                if (captionEditText.getTag() != null) {
                    bottomTouchEnabled = false;
                    return child.getAlpha() > 0;
                } else {
                    bottomTouchEnabled = true;
                }
            } else if (child == miniProgressView) {
                return false;
            }

            if (child == videoTimelineView && videoTimelineView.getTranslationY() > 0 && pickerView.getTranslationY() == 0) {
                canvas.save();
                canvas.clipRect(videoTimelineView.getX(), videoTimelineView.getY(), videoTimelineView.getX() + videoTimelineView.getMeasuredWidth(), videoTimelineView.getBottom());
                boolean b = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return b;
            }
            try {
                return child != aspectRatioFrameLayout && child != paintingOverlay && super.drawChild(canvas, child, drawingTime);
            } catch (Throwable ignore) {
                return true;
            }
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            adjustPanLayoutHelper.setResizableView(windowView);
            adjustPanLayoutHelper.onAttach();
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    int offset = 0;
                    if (bottomLayout != null && bottomLayout.getVisibility() == VISIBLE) {
                        offset += bottomLayout.getHeight();
                    }
                    if (groupedPhotosListView != null && groupedPhotosListView.hasPhotos() && (AndroidUtilities.isTablet() || containerView.getMeasuredHeight() > containerView.getMeasuredWidth())) {
                        offset += groupedPhotosListView.getHeight();
                    }
                    return offset;
                }
            });
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            adjustPanLayoutHelper.onDetach();
            Bulletin.removeDelegate(this);
        }

        @Override
        public void notifyHeightChanged() {
            super.notifyHeightChanged();
            if (isCurrentVideo) {
                photoProgressViews[0].setIndexedAlpha(2, getKeyboardHeight() <= AndroidUtilities.dp(20) ? 1.0f : 0.0f, true);
            }
        }
    }

    private static final Property<VideoPlayerControlFrameLayout, Float> VPC_PROGRESS;

    static {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            VPC_PROGRESS = new FloatProperty<VideoPlayerControlFrameLayout>("progress") {
                @Override
                public void setValue(VideoPlayerControlFrameLayout object, float value) {
                    object.setProgress(value);
                }

                @Override
                public Float get(VideoPlayerControlFrameLayout object) {
                    return object.getProgress();
                }
            };
        } else {
            VPC_PROGRESS = new Property<VideoPlayerControlFrameLayout, Float>(Float.class, "progress") {
                @Override
                public void set(VideoPlayerControlFrameLayout object, Float value) {
                    object.setProgress(value);
                }

                @Override
                public Float get(VideoPlayerControlFrameLayout object) {
                    return object.getProgress();
                }
            };
        }
    }

    private class VideoPlayerControlFrameLayout extends FrameLayout {

        private float progress = 1f;
        private boolean seekBarTransitionEnabled;
        private boolean translationYAnimationEnabled = true;
        private boolean ignoreLayout;
        private int parentWidth;
        private int parentHeight;

        public VideoPlayerControlFrameLayout(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (progress < 1f) {
                return false;
            }
            if (videoPlayerSeekbar.onTouch(event.getAction(), event.getX() - AndroidUtilities.dp(2), event.getY())) {
                getParent().requestDisallowInterceptTouchEvent(true);
                videoPlayerSeekbarView.invalidate();
                return true;
            }
            return true;
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int extraWidth;
            ignoreLayout = true;
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoPlayerTime.getLayoutParams();
            if (parentWidth > parentHeight) {
                if (exitFullscreenButton.getVisibility() != VISIBLE) {
                    exitFullscreenButton.setVisibility(VISIBLE);
                }
                extraWidth = AndroidUtilities.dp(48);
                layoutParams.rightMargin = AndroidUtilities.dp(47);
            } else {
                if (exitFullscreenButton.getVisibility() != INVISIBLE) {
                    exitFullscreenButton.setVisibility(INVISIBLE);
                }
                extraWidth = 0;
                layoutParams.rightMargin = AndroidUtilities.dp(12);
            }
            ignoreLayout = false;
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
            int size = (int) Math.ceil(videoPlayerTime.getPaint().measureText(String.format(Locale.ROOT, "%02d:%02d / %02d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));
            videoPlayerSeekbar.setSize(getMeasuredWidth() - AndroidUtilities.dp(2 + 14) - size - extraWidth, getMeasuredHeight());
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            float progress = 0;
            if (videoPlayer != null) {
                progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
            }
            if (playerWasReady) {
                videoPlayerSeekbar.setProgress(progress);
            }
            videoTimelineView.setProgress(progress);
        }

        public float getProgress() {
            return progress;
        }

        public void setProgress(float progress) {
            if (this.progress != progress) {
                this.progress = progress;
                onProgressChanged(progress);
            }
        }

        private void onProgressChanged(float progress) {
            videoPlayerTime.setAlpha(progress);
            exitFullscreenButton.setAlpha(progress);
            if (seekBarTransitionEnabled) {
                videoPlayerTime.setPivotX(videoPlayerTime.getWidth());
                videoPlayerTime.setPivotY(videoPlayerTime.getHeight());
                videoPlayerTime.setScaleX(1f - 0.1f * (1f - progress));
                videoPlayerTime.setScaleY(1f - 0.1f * (1f - progress));
                videoPlayerSeekbar.setTransitionProgress(1f - progress);
            } else {
                if (translationYAnimationEnabled) {
                    setTranslationY(AndroidUtilities.dpf2(24) * (1f - progress));
                }
                videoPlayerSeekbarView.setAlpha(progress);
            }
        }

        public boolean isSeekBarTransitionEnabled() {
            return seekBarTransitionEnabled;
        }

        public void setSeekBarTransitionEnabled(boolean seekBarTransitionEnabled) {
            if (this.seekBarTransitionEnabled != seekBarTransitionEnabled) {
                this.seekBarTransitionEnabled = seekBarTransitionEnabled;
                if (seekBarTransitionEnabled) {
                    setTranslationY(0);
                    videoPlayerSeekbarView.setAlpha(1f);
                } else {
                    videoPlayerTime.setScaleX(1f);
                    videoPlayerTime.setScaleY(1f);
                    videoPlayerSeekbar.setTransitionProgress(0f);
                }
                onProgressChanged(progress);
            }
        }

        public void setTranslationYAnimationEnabled(boolean translationYAnimationEnabled) {
            if (this.translationYAnimationEnabled != translationYAnimationEnabled) {
                this.translationYAnimationEnabled = translationYAnimationEnabled;
                if (!translationYAnimationEnabled) {
                    setTranslationY(0);
                }
                onProgressChanged(progress);
            }
        }
    }

    private class CaptionTextViewSwitcher extends TextViewSwitcher {

        private boolean inScrollView = false;
        private float alpha = 1.0f;

        public CaptionTextViewSwitcher(Context context) {
            super(context);
        }

        @Override
        public void setVisibility(int visibility) {
            setVisibility(visibility, true);
        }

        public void setVisibility(int visibility, boolean withScrollView) {
            super.setVisibility(visibility);
            if (inScrollView && withScrollView) {
                captionScrollView.setVisibility(visibility);
            }
        }

        @Override
        public void setAlpha(float alpha) {
            this.alpha = alpha;
            if (inScrollView) {
                captionScrollView.setAlpha(alpha);
            } else {
                super.setAlpha(alpha);
            }
        }

        @Override
        public float getAlpha() {
            if (inScrollView) {
                return alpha;
            } else {
                return super.getAlpha();
            }
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            if (inScrollView) {
                captionScrollView.invalidate(); // invalidate background drawing
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (captionContainer != null && getParent() == captionContainer) {
                inScrollView = true;
                captionScrollView.setVisibility(getVisibility());
                captionScrollView.setAlpha(alpha);
                super.setAlpha(1.0f);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (inScrollView) {
                inScrollView = false;
                captionScrollView.setVisibility(View.GONE);
                super.setAlpha(alpha);
            }
        }
    }

    private class CaptionScrollView extends NestedScrollView {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final SpringAnimation springAnimation;

        private boolean nestedScrollStarted;
        private float overScrollY;
        private float velocitySign;
        private float velocityY;

        private Method abortAnimatedScrollMethod;
        private OverScroller scroller;

        private boolean isLandscape;
        private int textHash;
        private int prevHeight;

        private float backgroundAlpha = 1f;
        private boolean dontChangeTopMargin;
        private int pendingTopMargin = -1;

        public CaptionScrollView(@NonNull Context context) {
            super(context);
            setClipChildren(false);
            setOverScrollMode(View.OVER_SCROLL_NEVER);

            paint.setColor(Color.BLACK);
            setFadingEdgeLength(AndroidUtilities.dp(12));
            setVerticalFadingEdgeEnabled(true);
            setWillNotDraw(false);

            springAnimation = new SpringAnimation(captionTextViewSwitcher, DynamicAnimation.TRANSLATION_Y, 0);
            springAnimation.getSpring().setStiffness(100f);
            springAnimation.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
            springAnimation.addUpdateListener((animation, value, velocity) -> {
                overScrollY = value;
                velocityY = velocity;
            });
            springAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);

            try {
                abortAnimatedScrollMethod = NestedScrollView.class.getDeclaredMethod("abortAnimatedScroll");
                abortAnimatedScrollMethod.setAccessible(true);
            } catch (Exception e) {
                abortAnimatedScrollMethod = null;
                FileLog.e(e);
            }

            try {
                final Field scrollerField = NestedScrollView.class.getDeclaredField("mScroller");
                scrollerField.setAccessible(true);
                scroller = (OverScroller) scrollerField.get(this);
            } catch (Exception e) {
                scroller = null;
                FileLog.e(e);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < captionContainer.getTop() - getScrollY() + captionTextViewSwitcher.getTranslationY()) {
                return false;
            }
            return super.onTouchEvent(ev);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            updateTopMargin(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void applyPendingTopMargin() {
            dontChangeTopMargin = false;
            if (pendingTopMargin >= 0) {
                ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin = pendingTopMargin;
                pendingTopMargin = -1;
                requestLayout();
            }
        }

        public int getPendingMarginTopDiff() {
            if (pendingTopMargin >= 0) {
                return pendingTopMargin - ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin;
            } else {
                return 0;
            }
        }

        public void updateTopMargin() {
            updateTopMargin(getWidth(), getHeight());
        }

        private void updateTopMargin(int width, int height) {
            final int marginTop = calculateNewContainerMarginTop(width, height);
            if (marginTop >= 0) {
                if (dontChangeTopMargin) {
                    pendingTopMargin = marginTop;
                } else {
                    ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin = marginTop;
                    pendingTopMargin = -1;
                }
            }
        }

        public int calculateNewContainerMarginTop(int width, int height) {
            if (width == 0 || height == 0) {
                return -1;
            }

            final TextView textView = captionTextViewSwitcher.getCurrentView();
            final CharSequence text = textView.getText();

            final int textHash = text.hashCode();
            final boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;

            if (this.textHash == textHash && this.isLandscape == isLandscape && this.prevHeight == height) {
                return -1;
            }

            this.textHash = textHash;
            this.isLandscape = isLandscape;
            this.prevHeight = height;

            textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

            final Layout layout = textView.getLayout();
            final int lineCount = layout.getLineCount();

            if (isLandscape && lineCount <= 2 || !isLandscape && lineCount <= 5) {
                return height - textView.getMeasuredHeight();
            }

            int i = Math.min(isLandscape ? 2 : 5, lineCount);

            cycle:
            while (i > 1) {
                for (int j = layout.getLineStart(i - 1); j < layout.getLineEnd(i - 1); j++) {
                    if (Character.isLetterOrDigit(text.charAt(j))) {
                        break cycle;
                    }
                }
                i--;
            }

            final int lineHeight = textView.getPaint().getFontMetricsInt(null);
            return height - lineHeight * i - AndroidUtilities.dp(8);
        }

        public void reset() {
            scrollTo(0, 0);
        }

        public void stopScrolling() {
            if (abortAnimatedScrollMethod != null) {
                try {
                    abortAnimatedScrollMethod.invoke(this);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        @Override
        public void fling(int velocityY) {
            super.fling(velocityY);
            this.velocitySign = Math.signum(velocityY);
            this.velocityY = 0f;
        }

        @Override
        public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) {
            consumed[1] = 0;

            if (nestedScrollStarted && (overScrollY > 0 && dy > 0 || overScrollY < 0 && dy < 0)) {
                final float delta = overScrollY - dy;

                if (overScrollY > 0) {
                    if (delta < 0) {
                        overScrollY = 0;
                        consumed[1] += dy + delta;
                    } else {
                        overScrollY = delta;
                        consumed[1] += dy;
                    }
                } else {
                    if (delta > 0) {
                        overScrollY = 0;
                        consumed[1] += dy + delta;
                    } else {
                        overScrollY = delta;
                        consumed[1] += dy;
                    }
                }

                captionTextViewSwitcher.setTranslationY(overScrollY);
                return true;
            }

            return false;
        }

        @Override
        public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
            if (dyUnconsumed != 0) {
                final int topMargin = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                final int dy = Math.round(dyUnconsumed * (1f - Math.abs((-overScrollY / (captionContainer.getTop() - topMargin)))));

                if (dy != 0) {
                    if (!nestedScrollStarted) {
                        if (!springAnimation.isRunning()) {
                            int consumedY;
                            float velocity = scroller != null ? scroller.getCurrVelocity() : Float.NaN;
                            if (!Float.isNaN(velocity)) {
                                final float clampedVelocity = Math.min(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 3000 : 5000, velocity);
                                consumedY = (int) (dy * clampedVelocity / velocity);
                                velocity = clampedVelocity * -velocitySign;
                            } else {
                                consumedY = dy;
                                velocity = 0;
                            }
                            if (consumedY != 0) {
                                overScrollY -= consumedY;
                                captionTextViewSwitcher.setTranslationY(overScrollY);
                            }
                            startSpringAnimationIfNotRunning(velocity);
                        }
                    } else {
                        overScrollY -= dy;
                        captionTextViewSwitcher.setTranslationY(overScrollY);
                    }
                }
            }
        }

        private void startSpringAnimationIfNotRunning(float velocityY) {
            if (!springAnimation.isRunning()) {
                springAnimation.setStartVelocity(velocityY);
                springAnimation.start();
            }
        }

        @Override
        public boolean startNestedScroll(int axes, int type) {
            if (type == ViewCompat.TYPE_TOUCH) {
                springAnimation.cancel();
                nestedScrollStarted = true;
                overScrollY = captionTextViewSwitcher.getTranslationY();
            }
            return true;
        }

        @Override
        public void computeScroll() {
            super.computeScroll();
            if (!nestedScrollStarted && overScrollY != 0 && scroller != null && scroller.isFinished()) {
                startSpringAnimationIfNotRunning(0);
            }
        }

        @Override
        public void stopNestedScroll(int type) {
            if (nestedScrollStarted && type == ViewCompat.TYPE_TOUCH) {
                nestedScrollStarted = false;
                if (overScrollY != 0 && scroller != null && scroller.isFinished()) {
                    startSpringAnimationIfNotRunning(velocityY);
                }
            }
        }

        @Override
        protected float getTopFadingEdgeStrength() {
            return 1f;
        }

        @Override
        protected float getBottomFadingEdgeStrength() {
            return 1f;
        }

        @Override
        public void draw(Canvas canvas) {
            final int width = getWidth();
            final int height = getHeight();
            final int scrollY = getScrollY();

            final int saveCount = canvas.save();
            canvas.clipRect(0, scrollY, width, height + scrollY);

            paint.setAlpha((int) (backgroundAlpha * 127));
            canvas.drawRect(0, captionContainer.getTop() + captionTextViewSwitcher.getTranslationY(), width, height + scrollY, paint);

            super.draw(canvas);
            canvas.restoreToCount(saveCount);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (isActionBarVisible) {
                final int scrollY = getScrollY();
                final float translationY = captionTextViewSwitcher.getTranslationY();

                boolean buttonVisible = scrollY == 0 && translationY == 0;
                boolean enalrgeIconVisible = scrollY == 0 && translationY == 0;

                if (!buttonVisible) {
                    final int progressBottom = photoProgressViews[0].getY() + photoProgressViews[0].size;
                    final int topMargin = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                    final int captionTop = captionContainer.getTop() + (int) translationY - scrollY + topMargin - AndroidUtilities.dp(12);
                    final int enlargeIconTop = (int) fullscreenButton[0].getY();
                    enalrgeIconVisible = captionTop > enlargeIconTop + AndroidUtilities.dp(32);
                    buttonVisible = captionTop > progressBottom;
                }
                if (allowShowFullscreenButton) {
                    if (fullscreenButton[0].getTag() != null && ((Integer) fullscreenButton[0].getTag()) == 3 && enalrgeIconVisible) {
                        fullscreenButton[0].setTag(2);
                        fullscreenButton[0].animate().alpha(1).setDuration(150).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                fullscreenButton[0].setTag(null);
                            }
                        }).start();
                    } else if (fullscreenButton[0].getTag() == null && !enalrgeIconVisible) {
                        fullscreenButton[0].setTag(3);
                        fullscreenButton[0].animate().alpha(0).setListener(null).setDuration(150).start();
                    }

                }
                photoProgressViews[0].setIndexedAlpha(2, buttonVisible ? 1f : 0f, true);
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

    public boolean isOpenedFullScreenVideo() {
        return openedFullScreenVideo;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    public PhotoViewer() {
        blackPaint.setColor(0xff000000);
        videoFrameBitmapPaint.setColor(0xffffffff);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoadFailed) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    boolean animated = a == 0 || a == 1 && sideImage == rightImage || a == 2 && sideImage == leftImage;
                    photoProgressViews[a].setProgress(1.0f, animated);
                    checkProgress(a, false, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    boolean animated = a == 0 || a == 1 && sideImage == rightImage || a == 2 && sideImage == leftImage;
                    photoProgressViews[a].setProgress(1.0f, animated);
                    checkProgress(a, false, animated);
                    if (videoPlayer == null && a == 0 && (currentMessageObject != null && currentMessageObject.isVideo() || currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document)) || pageBlocksAdapter != null && pageBlocksAdapter.isVideo(currentIndex))) {
                        onActionClick(false);
                    }
                    if (a == 0 && videoPlayer != null) {
                        currentVideoFinishedLoading = true;
                    }
                    break;
                }
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    Long loadedSize = (Long) args[1];
                    Long totalSize = (Long) args[2];
                    float loadProgress = Math.min(1f, loadedSize / (float) totalSize);
                    boolean animated = a == 0 || a == 1 && sideImage == rightImage || a == 2 && sideImage == leftImage;
                    photoProgressViews[a].setProgress(loadProgress, animated);
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
                            videoPlayerSeekbarView.invalidate();
                        }
                        checkBufferedProgress(loadProgress);
                    }
                }
            }
        } else if (id == NotificationCenter.dialogPhotosLoaded) {
            int guid = (Integer) args[3];
            long did = (Long) args[0];
            if (avatarsDialogId == did && classGuid == guid) {
                boolean fromCache = (Boolean) args[2];

                int setToImage = -1;
                ArrayList<TLRPC.Photo> photos = (ArrayList<TLRPC.Photo>) args[4];
                if (photos.isEmpty()) {
                    return;
                }
                ArrayList<TLRPC.Message> messages = (ArrayList<TLRPC.Message>) args[5];
                imagesArrLocations.clear();
                imagesArrLocationsSizes.clear();
                imagesArrLocationsVideo.clear();
                imagesArrMessages.clear();
                avatarsArr.clear();
                for (int a = 0; a < photos.size(); a++) {
                    TLRPC.Photo photo = photos.get(a);
                    if (photo == null || photo instanceof TLRPC.TL_photoEmpty || photo.sizes == null) {
                        continue;
                    }
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640);
                    TLRPC.VideoSize videoSize = photo.video_sizes.isEmpty() ? null : photo.video_sizes.get(0);
                    if (sizeFull != null) {
                        if (setToImage == -1 && currentFileLocation != null) {
                            for (int b = 0; b < photo.sizes.size(); b++) {
                                TLRPC.PhotoSize size = photo.sizes.get(b);
                                if (size.location != null && size.location.local_id == currentFileLocation.location.local_id && size.location.volume_id == currentFileLocation.location.volume_id) {
                                    setToImage = imagesArrLocations.size();
                                    break;
                                }
                            }
                        }
                        if (photo.dc_id != 0) {
                            sizeFull.location.dc_id = photo.dc_id;
                            sizeFull.location.file_reference = photo.file_reference;
                        }
                        ImageLocation location = ImageLocation.getForPhoto(sizeFull, photo);
                        ImageLocation videoLocation = videoSize != null ? ImageLocation.getForPhoto(videoSize, photo) : location;
                        if (location != null) {
                            imagesArrLocations.add(location);
                            imagesArrLocationsSizes.add(videoLocation.currentSize);
                            imagesArrLocationsVideo.add(videoLocation);
                            if (messages != null) {
                                imagesArrMessages.add(messages.get(a));
                            } else {
                                imagesArrMessages.add(null);
                            }
                            avatarsArr.add(photo);
                        }
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
                    setImageIndex(setToImage);
                } else {
                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    if (avatarsDialogId > 0) {
                        user = MessagesController.getInstance(currentAccount).getUser(avatarsDialogId);
                    } else {
                        chat = MessagesController.getInstance(currentAccount).getChat(-avatarsDialogId);
                    }
                    if (user != null || chat != null) {
                        ImageLocation location;
                        if (user != null) {
                            location = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG);
                        } else {
                            location = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
                        }
                        if (location != null) {
                            if (!imagesArrLocations.isEmpty() && imagesArrLocations.get(0).photoId == location.photoId) {
                                imagesArrLocations.remove(0);
                                avatarsArr.remove(0);
                                imagesArrLocationsSizes.remove(0);
                                imagesArrLocationsVideo.remove(0);
                                imagesArrMessages.remove(0);
                            }
                            imagesArrLocations.add(0, location);
                            avatarsArr.add(0, new TLRPC.TL_photoEmpty());
                            imagesArrLocationsSizes.add(0, currentFileLocationVideo.currentSize);
                            imagesArrLocationsVideo.add(0, currentFileLocationVideo);
                            imagesArrMessages.add(0, null);
                            setImageIndex(0);
                        }
                    }
                }
                if (fromCache) {
                    MessagesController.getInstance(currentAccount).loadDialogPhotos(avatarsDialogId, 80, 0, false, classGuid);
                }
            }
        } else if (id == NotificationCenter.mediaCountDidLoad) {
            long uid = (Long) args[0];
            if (uid == currentDialogId || uid == mergeDialogId) {
                if (currentMessageObject == null || MediaDataController.getMediaType(currentMessageObject.messageOwner) == sharedMediaType) {
                    if (uid == currentDialogId) {
                        totalImagesCount = (Integer) args[1];
                    } else {
                        totalImagesCountMerge = (Integer) args[1];
                    }
                    if (needSearchImageInArr && isFirstLoading) {
                        isFirstLoading = false;
                        loadingMoreImages = true;
                        MediaDataController.getInstance(currentAccount).loadMedia(currentDialogId, 20, 0, 0, sharedMediaType, 1, classGuid, 0);
                    } else if (!imagesArr.isEmpty()) {
                        if (opennedFromMedia) {
                            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, startOffset + currentIndex + 1, totalImagesCount + totalImagesCountMerge));
                        } else {
                            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount + totalImagesCountMerge - imagesArr.size()) + currentIndex + 1, totalImagesCount + totalImagesCountMerge));
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.mediaDidLoad) {
            long uid = (Long) args[0];
            int guid = (Integer) args[3];
            if ((uid == currentDialogId || uid == mergeDialogId) && guid == classGuid) {
                loadingMoreImages = false;
                int loadIndex = uid == currentDialogId ? 0 : 1;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                endReached[loadIndex] = (Boolean) args[5];
                boolean fromStart = (boolean) args[6];
                if (needSearchImageInArr) {
                    if (arr.isEmpty() && (loadIndex != 0 || mergeDialogId == 0) || currentIndex < 0 || currentIndex >= imagesArr.size()) {
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
                        setImageIndex(foundIndex);
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
                            MediaDataController.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 40, loadFromMaxId, 0, sharedMediaType, 1, classGuid, 0);
                        }
                    }
                } else {
                    int added = 0;
                    for (int i = 0; i < arr.size(); i++) {
                        MessageObject message = arr.get(fromStart ? arr.size() - 1 - i : i);
                        if (imagesByIds[loadIndex].indexOfKey(message.getId()) < 0) {
                            added++;
                            if (opennedFromMedia) {
                                if (fromStart) {
                                    imagesArr.add(0, message);
                                    startOffset--;
                                    currentIndex++;
                                    if (startOffset < 0) {
                                        startOffset = 0;
                                    }
                                } else {
                                    imagesArr.add(message);
                                }
                            } else {
                                imagesArr.add(0, message);
                            }
                            imagesByIds[loadIndex].put(message.getId(), message);
                        }
                    }
                    if (opennedFromMedia) {
                        if (added == 0 && !fromStart) {
                            totalImagesCount = startOffset + imagesArr.size();
                            totalImagesCountMerge = 0;
                        }
                    } else {
                        if (added != 0) {
                            int index = currentIndex;
                            currentIndex = -1;
                            setImageIndex(index + added);
                        } else {
                            totalImagesCount = imagesArr.size();
                            totalImagesCountMerge = 0;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (captionTextViewSwitcher != null) {
                captionTextViewSwitcher.invalidateViews();
            }
        } else if (id == NotificationCenter.filePreparingFailed) {
            MessageObject messageObject = (MessageObject) args[0];
            if (loadInitialVideo) {
                loadInitialVideo = false;
                progressView.setVisibility(View.INVISIBLE);
                preparePlayer(currentPlayingVideoFile, false, false, editState.savedFilterState);
            } else if (tryStartRequestPreviewOnFinish) {
                releasePlayer(false);
                tryStartRequestPreviewOnFinish = !MediaController.getInstance().scheduleVideoConvert(videoPreviewMessageObject, true);
            } else if (messageObject == videoPreviewMessageObject) {
                requestingPreview = false;
                progressView.setVisibility(View.INVISIBLE);
            }
        } else if (id == NotificationCenter.fileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject == videoPreviewMessageObject) {
                String finalPath = (String) args[1];
                long finalSize = (Long) args[3];
                float progress = (float) args[4];
                photoProgressViews[0].setProgress(progress,true);
                if (finalSize != 0) {
                    requestingPreview = false;
                    photoProgressViews[0].setProgress(1f, true);
                    photoProgressViews[0].setBackgroundState(PROGRESS_PLAY, true, true);
                    preparePlayer(Uri.fromFile(new File(finalPath)), false, true, editState.savedFilterState);
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            long channelId = (Long) args[1];
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            boolean reset = false;
            boolean resetCurrent = false;
            for (int x = 0; x < 2; x++) {
                ArrayList<MessageObject> arr = x == 0 ? imagesArr : imagesArrTemp;
                SparseArray<MessageObject>[] ids = x == 0 ? imagesByIds : imagesByIdsTemp;
                if (!arr.isEmpty()) {
                    for (int b = 0; b < 2; b++) {
                        if (ids[b].size() > 0) {
                            MessageObject messageObject = ids[b].valueAt(0);
                            if (messageObject.messageOwner.peer_id.channel_id == channelId) {
                                for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                                    int mid = markAsDeletedMessages.get(a);
                                    MessageObject message = ids[b].get(markAsDeletedMessages.get(a));
                                    if (message != null) {
                                        ids[b].remove(mid);
                                        arr.remove(message);
                                        if (b == 0) {
                                            totalImagesCount--;
                                        } else {
                                            totalImagesCountMerge--;
                                        }
                                        if (message == currentMessageObject) {
                                            resetCurrent = true;
                                        }
                                        reset = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (reset) {
                if (resetCurrent && this == PipInstance) {
                    destroyPhotoViewer();
                } else {
                    if (!imagesArr.isEmpty()) {
                        int index = currentIndex;
                        currentIndex = -1;
                        if (index >= imagesArr.size()) {
                            index = imagesArr.size() - 1;
                        }
                        setImageIndex(index);
                    } else {
                        closePhoto(false, true);
                    }
                }
            }
        }
    }

    private void showDownloadAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        boolean alreadyDownloading = currentMessageObject != null && currentMessageObject.isVideo() && FileLoader.getInstance(currentMessageObject.currentAccount).isLoadingFile(currentFileNames[0]);
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
            } else if (currentFileLocationVideo != null) {
                f = FileLoader.getPathToAttach(getFileLocation(currentFileLocationVideo), getFileLocationExt(currentFileLocationVideo), avatarsDialogId != 0 || isEvent);
            } else if (pageBlocksAdapter != null) {
                f = pageBlocksAdapter.getFile(currentIndex);
            }

            if (f != null && f.exists()) {
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
        float bitmapHeight = centerImage.getBitmapHeight();
        if (bitmapWidth == 0 || bitmapHeight == 0) {
            return;
        }
        float containerWidth = getContainerViewWidth();
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
        setParentActivity(activity, null);
    }

    public void setParentActivity(final Activity activity, Theme.ResourcesProvider resourcesProvider) {
        Theme.createChatResources(activity, false);
        this.resourcesProvider = resourcesProvider;
        currentAccount = UserConfig.selectedAccount;
        centerImage.setCurrentAccount(currentAccount);
        leftImage.setCurrentAccount(currentAccount);
        rightImage.setCurrentAccount(currentAccount);
        if (parentActivity == activity || activity == null) {
            return;
        }
        inBubbleMode = activity instanceof BubbleActivity;
        parentActivity = activity;
        activityContext = new ContextThemeWrapper(parentActivity, R.style.Theme_TMessages);
        touchSlop = ViewConfiguration.get(parentActivity).getScaledTouchSlop();

        if (progressDrawables == null) {
            final Drawable circleDrawable = ContextCompat.getDrawable(parentActivity, R.drawable.circle_big);
            progressDrawables = new Drawable[] {
                    circleDrawable, // PROGRESS_EMPTY
                    ContextCompat.getDrawable(parentActivity, R.drawable.cancel_big), // PROGRESS_CANCEL
                    ContextCompat.getDrawable(parentActivity, R.drawable.load_big), // PROGRESS_LOAD
            };
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
            public boolean dispatchKeyEvent(KeyEvent event) {
                int keyCode = event.getKeyCode();
                if (!muteVideo && sendPhotoType != SELECT_TYPE_AVATAR && isCurrentVideo && videoPlayer != null && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
                    videoPlayer.setVolume(1.0f);
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (videoPlayerControlVisible && isPlaying) {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_POINTER_DOWN:
                            AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_POINTER_UP:
                            scheduleActionBarHide();
                            break;
                    }
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result;
                try {
                    result = super.drawChild(canvas, child, drawingTime);
                } catch (Throwable ignore) {
                    result = false;
                }
                return result;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    WindowInsets insets = (WindowInsets) lastInsets;
                    if (!inBubbleMode) {
                        if (AndroidUtilities.incorrectDisplaySizeFix) {
                            if (heightSize > AndroidUtilities.displaySize.y) {
                                heightSize = AndroidUtilities.displaySize.y;
                            }
                            heightSize += AndroidUtilities.statusBarHeight;
                        } else {
                            int insetBottom = insets.getStableInsetBottom();
                            if (insetBottom >= 0 && AndroidUtilities.statusBarHeight >= 0) {
                                int newSize = heightSize - AndroidUtilities.statusBarHeight - insets.getStableInsetBottom();
                                if (newSize > 0 && newSize < 4096) {
                                    AndroidUtilities.displaySize.y = newSize;
                                }
                            }
                        }
                    }
                    int bottomInsets = insets.getSystemWindowInsetBottom();
                    if (captionEditText.isPopupShowing()) {
                        bottomInsets -= containerView.getKeyboardHeight();
                    }
                    heightSize -= bottomInsets;
                } else {
                    if (heightSize > AndroidUtilities.displaySize.y) {
                        heightSize = AndroidUtilities.displaySize.y;
                    }
                }
                setMeasuredDimension(widthSize, heightSize);
                ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
                animatingImageView.measure(MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.AT_MOST));
                containerView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
            }

            @SuppressWarnings("DrawAllocation")
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                animatingImageView.layout(0, 0, animatingImageView.getMeasuredWidth(), animatingImageView.getMeasuredHeight());
                containerView.layout(0, 0, containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                wasLayout = true;
                if (changed) {
                    if (!dontResetZoomOnFirstLayout) {
                        scale = 1;
                        translationX = 0;
                        translationY = 0;
                        updateMinMax(scale);
                    }

                    if (checkImageView != null) {
                        checkImageView.post(() -> {
                            LayoutParams layoutParams = (LayoutParams) checkImageView.getLayoutParams();
                            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                            int rotation = manager.getDefaultDisplay().getRotation();
                            int newMargin = (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(34)) / 2 + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
                            if (newMargin != layoutParams.topMargin) {
                                layoutParams.topMargin = newMargin;
                                checkImageView.setLayoutParams(layoutParams);
                            }

                            layoutParams = (LayoutParams) photosCounterView.getLayoutParams();
                            newMargin = (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(40)) / 2 + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
                            if (layoutParams.topMargin != newMargin) {
                                layoutParams.topMargin = newMargin;
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
                        closeCaptionEnter(true);
                        return false;
                    }
                    PhotoViewer.getInstance().closePhoto(true, false);
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (Build.VERSION.SDK_INT >= 21 && isVisible && lastInsets != null) {
                    WindowInsets insets = (WindowInsets) lastInsets;
                    if (animationInProgress == 1) {
                        blackPaint.setAlpha((int) (255 * animatingImageView.getAnimationProgress()));
                    } else if (animationInProgress == 3) {
                        blackPaint.setAlpha((int) (255 * (1.0f - animatingImageView.getAnimationProgress())));
                    } else {
                        blackPaint.setAlpha(backgroundDrawable.getAlpha());
                    }
                    canvas.drawRect(0, getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight() + insets.getSystemWindowInsetBottom(), blackPaint);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (parentChatActivity != null) {
                    View undoView = parentChatActivity.getUndoView();
                    if (undoView.getVisibility() == View.VISIBLE) {
                        canvas.save();
                        View parent = (View) undoView.getParent();
                        canvas.clipRect(parent.getX(), parent.getY(), parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight());
                        canvas.translate(undoView.getX(), undoView.getY());
                        undoView.draw(canvas);
                        canvas.restore();
                        invalidate();
                    }
                }
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
            containerView.setOnApplyWindowInsetsListener((v, insets) -> {
                int newTopInset = insets.getSystemWindowInsetTop();
                if (parentActivity instanceof LaunchActivity && (newTopInset != 0 || AndroidUtilities.isInMultiwindow) && !inBubbleMode && AndroidUtilities.statusBarHeight != newTopInset) {
                    AndroidUtilities.statusBarHeight = newTopInset;
                    ((LaunchActivity) parentActivity).drawerLayoutContainer.requestLayout();
                }
                WindowInsets oldInsets = (WindowInsets) lastInsets;
                lastInsets = insets;
                if (oldInsets == null || !oldInsets.toString().equals(insets.toString())) {
                    if (animationInProgress == 1 || animationInProgress == 3) {
                        animatingImageView.setTranslationX(animatingImageView.getTranslationX() - getLeftInset());
                        animationValues[0][2] = animatingImageView.getTranslationX();
                    }
                    if (windowView != null) {
                        windowView.requestLayout();
                    }
                }
                containerView.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), 0);
                if (actionBar != null) {
                    AndroidUtilities.cancelRunOnUIThread(updateContainerFlagsRunnable);
                    if (isVisible && animationInProgress == 0) {
                        AndroidUtilities.runOnUIThread(updateContainerFlagsRunnable, 200);
                    }
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return insets.consumeSystemWindowInsets();
                }
            });
            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }

        paintingOverlay = new PaintingOverlay(parentActivity);
        containerView.addView(paintingOverlay, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        actionBar = new ActionBar(activity) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setOverlayTitleAnimation(true);
        actionBar.setTitleColor(0xffffffff);
        actionBar.setSubtitleColor(0xffffffff);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setOccupyStatusBar(isStatusBarVisible());
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
                    if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                        return;
                    }

                    File f = null;
                    final boolean isVideo;
                    if (currentMessageObject != null) {
                        if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && currentMessageObject.messageOwner.media.webpage != null && currentMessageObject.messageOwner.media.webpage.document == null) {
                            TLObject fileLocation = getFileLocation(currentIndex, null);
                            f = FileLoader.getPathToAttach(fileLocation, true);
                        } else {
                            f = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                        }
                        isVideo = currentMessageObject.isVideo();
                    } else if (currentFileLocationVideo != null) {
                        f = FileLoader.getPathToAttach(getFileLocation(currentFileLocationVideo), getFileLocationExt(currentFileLocationVideo), avatarsDialogId != 0 || isEvent);
                        isVideo = false;
                    } else if (pageBlocksAdapter != null) {
                        f = pageBlocksAdapter.getFile(currentIndex);
                        isVideo = pageBlocksAdapter.isVideo(currentIndex);
                    } else {
                        isVideo = false;
                    }

                    if (f != null && f.exists()) {
                        MediaController.saveFile(f.toString(), parentActivity, isVideo ? 1 : 0, null, null, () -> BulletinFactory.createSaveToGalleryBulletin(containerView, isVideo, 0xf9222222, 0xffffffff).show());
                    } else {
                        showDownloadAlert();
                    }
                } else if (id == gallery_menu_showall) {
                    if (currentDialogId != 0) {
                        disableShowCheck = true;
                        Bundle args2 = new Bundle();
                        args2.putLong("dialog_id", currentDialogId);
                        MediaActivity mediaActivity = new MediaActivity(args2, null);
                        if (parentChatActivity != null) {
                            mediaActivity.setChatInfo(parentChatActivity.getCurrentChatInfo());
                        }
                        closePhoto(false, false);
                        if (parentActivity instanceof LaunchActivity) {
                            ((LaunchActivity) parentActivity).presentFragment(mediaActivity, false, true);
                        }
                    }
                } else if (id == gallery_menu_showinchat) {
                    if (currentMessageObject == null) {
                        return;
                    }
                    Bundle args = new Bundle();
                    long dialogId = currentDialogId;
                    if (currentMessageObject != null) {
                        dialogId = currentMessageObject.getDialogId();
                    }
                    if (DialogObject.isEncryptedDialog(dialogId)) {
                        args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
                    } else if (DialogObject.isUserDialog(dialogId)) {
                        args.putLong("user_id", dialogId);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        if (chat != null && chat.migrated_to != null) {
                            args.putLong("migrated_to", dialogId);
                            dialogId = -chat.migrated_to.channel_id;
                        }
                        args.putLong("chat_id", -dialogId);
                    }
                    args.putInt("message_id", currentMessageObject.getId());
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                    if (parentActivity instanceof LaunchActivity) {
                        LaunchActivity launchActivity = (LaunchActivity) parentActivity;
                        boolean remove = launchActivity.getMainFragmentsCount() > 1 || AndroidUtilities.isTablet();
                        launchActivity.presentFragment(new ChatActivity(args), remove, true);
                    }
                    closePhoto(false, false);
                    currentMessageObject = null;
                } else if (id == gallery_menu_send) {
                    if (currentMessageObject == null || !(parentActivity instanceof LaunchActivity)) {
                        return;
                    }
                    ((LaunchActivity) parentActivity).switchToAccount(currentMessageObject.currentAccount, true);
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    final ArrayList<MessageObject> fmessages = new ArrayList<>();
                    fmessages.add(currentMessageObject);
                    final ChatActivity parentChatActivityFinal = parentChatActivity;
                    fragment.setDelegate((fragment1, dids, message, param) -> {
                        if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
                            for (int a = 0; a < dids.size(); a++) {
                                long did = dids.get(a);
                                if (message != null) {
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null);
                                }
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did, false, false, true, 0);
                            }
                            fragment1.finishFragment();
                            if (parentChatActivityFinal != null) {
                                if (dids.size() == 1) {
                                    parentChatActivityFinal.getUndoView().showWithAction(dids.get(0), UndoView.ACTION_FWD_MESSAGES, fmessages.size());
                                } else {
                                    parentChatActivityFinal.getUndoView().showWithAction(0, UndoView.ACTION_FWD_MESSAGES, fmessages.size(), dids.size(), null, null);
                                }
                            }
                        } else {
                            long did = dids.get(0);
                            Bundle args1 = new Bundle();
                            args1.putBoolean("scrollToTopOnResume", true);
                            if (DialogObject.isEncryptedDialog(did)) {
                                args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                            } else if (DialogObject.isUserDialog(did)) {
                                args1.putLong("user_id", did);
                            } else {
                                args1.putLong("chat_id", -did);
                            }
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                            ChatActivity chatActivity = new ChatActivity(args1);
                            if (((LaunchActivity) parentActivity).presentFragment(chatActivity, true, false)) {
                                chatActivity.showFieldPanelForForward(true, fmessages);
                            } else {
                                fragment1.finishFragment();
                            }
                        }
                    });
                    ((LaunchActivity) parentActivity).presentFragment(fragment, false, true);
                    closePhoto(false, false);
                } else if (id == gallery_menu_delete) {
                    if (parentActivity == null || placeProvider == null) {
                        return;
                    }
                    boolean isChannel = false;
                    if (currentMessageObject != null && !currentMessageObject.scheduled) {
                        long dialogId = currentMessageObject.getDialogId();
                        if (DialogObject.isChatDialog(dialogId)) {
                            isChannel = ChatObject.isChannel(MessagesController.getInstance(currentAccount).getChat(-dialogId));
                        }
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    String text = placeProvider.getDeleteMessageString();
                    if (text != null) {
                        builder.setTitle(LocaleController.getString("AreYouSureDeletePhotoTitle", R.string.AreYouSureDeletePhotoTitle));
                        builder.setMessage(text);
                    } else if (isEmbedVideo || currentFileLocationVideo != null && currentFileLocationVideo != currentFileLocation || currentMessageObject != null && currentMessageObject.isVideo()) {
                        builder.setTitle(LocaleController.getString("AreYouSureDeleteVideoTitle", R.string.AreYouSureDeleteVideoTitle));
                        if (isChannel) {
                            builder.setMessage(LocaleController.formatString("AreYouSureDeleteVideoEveryone", R.string.AreYouSureDeleteVideoEveryone));
                        } else {
                            builder.setMessage(LocaleController.formatString("AreYouSureDeleteVideo", R.string.AreYouSureDeleteVideo));
                        }
                    } else if (currentMessageObject != null && currentMessageObject.isGif()) {
                        builder.setTitle(LocaleController.getString("AreYouSureDeleteGIFTitle", R.string.AreYouSureDeleteGIFTitle));
                        if (isChannel) {
                            builder.setMessage(LocaleController.formatString("AreYouSureDeleteGIFEveryone", R.string.AreYouSureDeleteGIFEveryone));
                        } else {
                            builder.setMessage(LocaleController.formatString("AreYouSureDeleteGIF", R.string.AreYouSureDeleteGIF));
                        }
                    } else {
                        builder.setTitle(LocaleController.getString("AreYouSureDeletePhotoTitle", R.string.AreYouSureDeletePhotoTitle));
                        if (isChannel) {
                            builder.setMessage(LocaleController.formatString("AreYouSureDeletePhotoEveryone", R.string.AreYouSureDeletePhotoEveryone));
                        } else {
                            builder.setMessage(LocaleController.formatString("AreYouSureDeletePhoto", R.string.AreYouSureDeletePhoto));
                        }
                    }

                    final boolean[] deleteForAll = new boolean[1];
                    if (currentMessageObject != null && !currentMessageObject.scheduled) {
                        long dialogId = currentMessageObject.getDialogId();
                        if (!DialogObject.isEncryptedDialog(dialogId)) {
                            TLRPC.Chat currentChat;
                            TLRPC.User currentUser;
                            if (DialogObject.isUserDialog(dialogId)) {
                                currentUser = MessagesController.getInstance(currentAccount).getUser(dialogId);
                                currentChat = null;
                            } else {
                                currentUser = null;
                                currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                            }
                            if (currentUser != null || !ChatObject.isChannel(currentChat)) {
                                boolean hasOutgoing = false;
                                int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();

                                int revokeTimeLimit;
                                if (currentUser != null) {
                                    revokeTimeLimit = MessagesController.getInstance(currentAccount).revokeTimePmLimit;
                                } else {
                                    revokeTimeLimit = MessagesController.getInstance(currentAccount).revokeTimeLimit;
                                }

                                if (currentUser != null && currentUser.id != UserConfig.getInstance(currentAccount).getClientUserId() || currentChat != null) {
                                    boolean canRevokeInbox = currentUser != null && MessagesController.getInstance(currentAccount).canRevokePmInbox;
                                    if ((currentMessageObject.messageOwner.action == null || currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) && (currentMessageObject.isOut() || canRevokeInbox || ChatObject.hasAdminRights(currentChat)) && (currentDate - currentMessageObject.messageOwner.date) <= revokeTimeLimit) {
                                        FrameLayout frameLayout = new FrameLayout(parentActivity);
                                        CheckBoxCell cell = new CheckBoxCell(parentActivity, 1, resourcesProvider);
                                        cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                                        if (currentChat != null) {
                                            cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                                        } else {
                                            cell.setText(LocaleController.formatString("DeleteForUser", R.string.DeleteForUser, UserObject.getFirstName(currentUser)), "", false, false);
                                        }
                                        cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                                        frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                                        cell.setOnClickListener(v -> {
                                            CheckBoxCell cell1 = (CheckBoxCell) v;
                                            deleteForAll[0] = !deleteForAll[0];
                                            cell1.setChecked(deleteForAll[0], true);
                                        });
                                        builder.setView(frameLayout);
                                        builder.setCustomViewOffset(9);
                                    }
                                }
                            }
                        }
                    }
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
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
                                if (DialogObject.isEncryptedDialog(obj.getDialogId()) && obj.messageOwner.random_id != 0) {
                                    random_ids = new ArrayList<>();
                                    random_ids.add(obj.messageOwner.random_id);
                                    encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(obj.getDialogId()));
                                }

                                MessagesController.getInstance(currentAccount).deleteMessages(arr, random_ids, encryptedChat, obj.getDialogId(), deleteForAll[0], obj.scheduled);
                            }
                        } else if (!avatarsArr.isEmpty()) {
                            if (currentIndex < 0 || currentIndex >= avatarsArr.size()) {
                                return;
                            }
                            TLRPC.Message message = imagesArrMessages.get(currentIndex);
                            if (message != null) {
                                ArrayList<Integer> arr = new ArrayList<>();
                                arr.add(message.id);
                                MessagesController.getInstance(currentAccount).deleteMessages(arr, null, null, MessageObject.getDialogId(message), true, false);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadDialogPhotos);
                            }
                            if (isCurrentAvatarSet()) {
                                if (avatarsDialogId > 0) {
                                    MessagesController.getInstance(currentAccount).deleteUserPhoto(null);
                                } else {
                                    MessagesController.getInstance(currentAccount).changeChatAvatar(-avatarsDialogId, null, null, null, 0, null, null, null, null);
                                }
                                closePhoto(false, false);
                            } else {
                                TLRPC.Photo photo = avatarsArr.get(currentIndex);
                                if (photo == null) {
                                    return;
                                }
                                TLRPC.TL_inputPhoto inputPhoto = new TLRPC.TL_inputPhoto();
                                inputPhoto.id = photo.id;
                                inputPhoto.access_hash = photo.access_hash;
                                inputPhoto.file_reference = photo.file_reference;
                                if (inputPhoto.file_reference == null) {
                                    inputPhoto.file_reference = new byte[0];
                                }
                                if (avatarsDialogId > 0) {
                                    MessagesController.getInstance(currentAccount).deleteUserPhoto(inputPhoto);
                                }
                                MessagesStorage.getInstance(currentAccount).clearUserPhoto(avatarsDialogId, photo.id);
                                imagesArrLocations.remove(currentIndex);
                                imagesArrLocationsSizes.remove(currentIndex);
                                imagesArrLocationsVideo.remove(currentIndex);
                                imagesArrMessages.remove(currentIndex);
                                avatarsArr.remove(currentIndex);
                                if (imagesArrLocations.isEmpty()) {
                                    closePhoto(false, false);
                                } else {
                                    int index = currentIndex;
                                    if (index >= avatarsArr.size()) {
                                        index = avatarsArr.size() - 1;
                                    }
                                    currentIndex = -1;
                                    setImageIndex(index);
                                }
                                if (message == null) {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadDialogPhotos);
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
                                setImageIndex(index);
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    showAlertDialog(builder);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(getThemedColor(Theme.key_dialogTextRed2));
                    }
                } else if (id == gallery_menu_share || id == gallery_menu_share2) {
                    onSharePressed();
                } else if (id == gallery_menu_speed) {
                    menuItemSpeed.setVisibility(View.VISIBLE);
                    menuItemSpeed.toggleSubMenu();
                    for (int a = 0; a < speedItems.length; a++) {
                        if (a == 0 && Math.abs(currentVideoSpeed - 0.25f) < 0.001f ||
                                a == 1 && Math.abs(currentVideoSpeed - 0.5f) < 0.001f ||
                                a == 2 && Math.abs(currentVideoSpeed - 1.0f) < 0.001f ||
                                a == 3 && Math.abs(currentVideoSpeed - 1.5f) < 0.001f ||
                                a == 4 && Math.abs(currentVideoSpeed - 2.0f) < 0.001f) {
                            speedItems[a].setColors(0xff6BB6F9, 0xff6BB6F9);
                        } else {
                            speedItems[a].setColors(0xfffafafa, 0xfffafafa);
                        }
                    }
                } else if (id == gallery_menu_openin) {
                    try {
                        if (isEmbedVideo) {
                            Browser.openUrl(parentActivity, currentMessageObject.messageOwner.media.webpage.url);
                            closePhoto(false, false);
                        } else if (currentMessageObject != null) {
                            if (AndroidUtilities.openForView(currentMessageObject, parentActivity, resourcesProvider)) {
                                closePhoto(false, false);
                            } else {
                                showDownloadAlert();
                            }
                        } else if (pageBlocksAdapter != null) {
                            if (AndroidUtilities.openForView(pageBlocksAdapter.getMedia(currentIndex), parentActivity)) {
                                closePhoto(false, false);
                            } else {
                                showDownloadAlert();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (id == gallery_menu_masks || id == gallery_menu_masks2) {
                    if (parentActivity == null || currentMessageObject == null) {
                        return;
                    }
                    TLObject object;
                    if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                        object = currentMessageObject.messageOwner.media.photo;
                    } else if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        object = currentMessageObject.messageOwner.media.document;
                    } else {
                        return;
                    }
                    masksAlert = new StickersAlert(parentActivity, currentMessageObject, object, resourcesProvider) {
                        @Override
                        public void dismiss() {
                            super.dismiss();
                            if (masksAlert == this) {
                                masksAlert = null;
                            }
                        }
                    };
                    masksAlert.show();
                } else if (id == gallery_menu_pip) {
                    if (pipItem.getAlpha() != 1.0f) {
                        return;
                    }
                    if (isEmbedVideo) {
                        pipVideoView = photoViewerWebView.openInPip();
                        if (pipVideoView != null) {
                            if (PipInstance != null) {
                                PipInstance.destroyPhotoViewer();
                            }
                            isInline = true;
                            PipInstance = Instance;
                            Instance = null;
                            isVisible = false;
                            if (currentPlaceObject != null && !currentPlaceObject.imageReceiver.getVisible()) {
                                currentPlaceObject.imageReceiver.setVisible(true, true);
                            }
                            dismissInternal();
                        }
                    } else {
                        switchToPip(false);
                    }
                } else if (id == gallery_menu_cancel_loading) {
                    if (currentMessageObject == null) {
                        return;
                    }
                    FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
                    releasePlayer(false);
                    bottomLayout.setTag(1);
                    bottomLayout.setVisibility(View.VISIBLE);
                } else if (id == gallery_menu_savegif) {
                    if (currentMessageObject != null) {
                        TLRPC.Document document = currentMessageObject.getDocument();
                        if (parentChatActivity != null && parentChatActivity.chatActivityEnterView != null) {
                            parentChatActivity.chatActivityEnterView.addRecentGif(document);
                        } else {
                            MediaDataController.getInstance(currentAccount).addRecentGif(document, (int) (System.currentTimeMillis() / 1000));
                        }
                        MessagesController.getInstance(currentAccount).saveGif(currentMessageObject, document);
                    } else if (pageBlocksAdapter != null) {
                        TLObject object = pageBlocksAdapter.getMedia(currentIndex);
                        if (object instanceof TLRPC.Document) {
                            TLRPC.Document document = (TLRPC.Document) object;
                            MediaDataController.getInstance(currentAccount).addRecentGif(document, (int) (System.currentTimeMillis() / 1000));
                            MessagesController.getInstance(currentAccount).saveGif(pageBlocksAdapter.getParentObject(), document);
                        }
                    } else {
                        return;
                    }
                    if (containerView != null) {
                        BulletinFactory.of(containerView, resourcesProvider).createDownloadBulletin(BulletinFactory.FileType.GIF, resourcesProvider).show();
                    }
                } else if (id == gallery_menu_set_as_main) {
                    TLRPC.Photo photo = avatarsArr.get(currentIndex);
                    if (photo == null || photo.sizes.isEmpty()) {
                        return;
                    }
                    TLRPC.PhotoSize bigSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
                    TLRPC.PhotoSize smallSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90);
                    UserConfig userConfig = UserConfig.getInstance(currentAccount);
                    if (avatarsDialogId == userConfig.clientUserId) {
                        TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
                        req.id = new TLRPC.TL_inputPhoto();
                        req.id.id = photo.id;
                        req.id.access_hash = photo.access_hash;
                        req.id.file_reference = photo.file_reference;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response instanceof TLRPC.TL_photos_photo) {
                                TLRPC.TL_photos_photo photos_photo = (TLRPC.TL_photos_photo) response;
                                MessagesController.getInstance(currentAccount).putUsers(photos_photo.users, false);
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userConfig.clientUserId);
                                if (photos_photo.photo instanceof TLRPC.TL_photo) {
                                    int idx = avatarsArr.indexOf(photo);
                                    if (idx >= 0) {
                                        avatarsArr.set(idx, photos_photo.photo);
                                    }
                                    if (user != null) {
                                        user.photo.photo_id = photos_photo.photo.id;
                                        userConfig.setCurrentUser(user);
                                        userConfig.saveConfig(true);
                                    }
                                }
                            }
                        }));

                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userConfig.clientUserId);
                        if (user != null) {
                            user.photo.photo_id = photo.id;
                            user.photo.dc_id = photo.dc_id;
                            user.photo.photo_small = smallSize.location;
                            user.photo.photo_big = bigSize.location;
                            userConfig.setCurrentUser(user);
                            userConfig.saveConfig(true);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                        }
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-avatarsDialogId);
                        if (chat == null) {
                            return;
                        }
                        TLRPC.TL_inputChatPhoto inputChatPhoto = new TLRPC.TL_inputChatPhoto();
                        inputChatPhoto.id = new TLRPC.TL_inputPhoto();
                        inputChatPhoto.id.id = photo.id;
                        inputChatPhoto.id.access_hash = photo.access_hash;
                        inputChatPhoto.id.file_reference = photo.file_reference;
                        MessagesController.getInstance(currentAccount).changeChatAvatar(-avatarsDialogId, inputChatPhoto, null, null, 0, null, null, null, null);
                        chat.photo.dc_id = photo.dc_id;
                        chat.photo.photo_small = smallSize.location;
                        chat.photo.photo_big = bigSize.location;
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                    }
                    currentAvatarLocation = ImageLocation.getForPhoto(bigSize, photo);
                    avatarsArr.remove(currentIndex);
                    avatarsArr.add(0, photo);

                    ImageLocation location = imagesArrLocations.get(currentIndex);
                    imagesArrLocations.remove(currentIndex);
                    imagesArrLocations.add(0, location);

                    location = imagesArrLocationsVideo.get(currentIndex);
                    imagesArrLocationsVideo.remove(currentIndex);
                    imagesArrLocationsVideo.add(0, location);

                    Integer size = imagesArrLocationsSizes.get(currentIndex);
                    imagesArrLocationsSizes.remove(currentIndex);
                    imagesArrLocationsSizes.add(0, size);

                    TLRPC.Message message = imagesArrMessages.get(currentIndex);
                    imagesArrMessages.remove(currentIndex);
                    imagesArrMessages.add(0, message);

                    currentIndex = -1;
                    setImageIndex(0);

                    groupedPhotosListView.clear();
                    groupedPhotosListView.fillList();
                    hintView.showWithAction(avatarsDialogId, UndoView.ACTION_PROFILE_PHOTO_CHANGED, currentFileLocationVideo == currentFileLocation ? null : 1);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (menuItem == null) {
                            return;
                        }
                        menuItem.hideSubItem(gallery_menu_set_as_main);
                    }, 300);
                } else if (id == gallery_menu_edit_avatar) {
                    File f = FileLoader.getPathToAttach(getFileLocation(currentFileLocationVideo), getFileLocationExt(currentFileLocationVideo), true);
                    boolean isVideo = currentFileLocationVideo.imageType == FileLoader.IMAGE_TYPE_ANIMATION;
                    String thumb;
                    if (isVideo) {
                        thumb = FileLoader.getPathToAttach(getFileLocation(currentFileLocation), getFileLocationExt(currentFileLocation), true).getAbsolutePath();
                    } else {
                        thumb = null;
                    }
                    placeProvider.openPhotoForEdit(f.getAbsolutePath(), thumb, isVideo);
                }
            }

            @Override
            public boolean canOpenMenu() {
                menuItemSpeed.setVisibility(View.INVISIBLE);
                if (currentMessageObject != null || currentSecureDocument != null) {
                    return true;
                } else if (currentFileLocationVideo != null) {
                    File f = FileLoader.getPathToAttach(getFileLocation(currentFileLocationVideo), getFileLocationExt(currentFileLocationVideo), avatarsDialogId != 0 || isEvent);
                    return f.exists();
                } else if (pageBlocksAdapter != null) {
                    return true;
                }
                return false;
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        masksItem = menu.addItem(gallery_menu_masks, R.drawable.msg_mask);
        masksItem.setContentDescription(LocaleController.getString("Masks", R.string.Masks));
        pipItem = menu.addItem(gallery_menu_pip, R.drawable.ic_goinline);
        pipItem.setContentDescription(LocaleController.getString("AccDescrPipMode", R.string.AccDescrPipMode));
        sendItem = menu.addItem(gallery_menu_send, R.drawable.msg_forward);
        sendItem.setContentDescription(LocaleController.getString("Forward", R.string.Forward));
        shareItem = menu.addItem(gallery_menu_share2, R.drawable.share);
        shareItem.setContentDescription(LocaleController.getString("ShareFile", R.string.ShareFile));

        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItemSpeed = new ActionBarMenuItem(parentActivity, null, 0, 0, resourcesProvider);
        menuItemSpeed.setDelegate(id -> {
            if (id >= gallery_menu_speed_veryslow && id <= gallery_menu_speed_veryfast) {
                switch (id) {
                    case gallery_menu_speed_veryslow:
                        currentVideoSpeed = 0.25f;
                        break;
                    case gallery_menu_speed_slow:
                        currentVideoSpeed = 0.5f;
                        break;
                    case gallery_menu_speed_normal:
                        currentVideoSpeed = 1.0f;
                        break;
                    case gallery_menu_speed_fast:
                        currentVideoSpeed = 1.5f;
                        break;
                    case gallery_menu_speed_veryfast:
                        currentVideoSpeed = 2.0f;
                        break;
                }
                if (currentMessageObject != null) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("playback_speed", Activity.MODE_PRIVATE);
                    if (Math.abs(currentVideoSpeed - 1.0f) < 0.001f) {
                        preferences.edit().remove("speed" + currentMessageObject.getDialogId() + "_" + currentMessageObject.getId()).commit();
                    } else {
                        preferences.edit().putFloat("speed" + currentMessageObject.getDialogId() + "_" + currentMessageObject.getId(), currentVideoSpeed).commit();
                    }
                }
                if (videoPlayer != null) {
                    videoPlayer.setPlaybackSpeed(currentVideoSpeed);
                }
                if (photoViewerWebView != null) {
                    photoViewerWebView.setPlaybackSpeed(currentVideoSpeed);
                }
                setMenuItemIcon();
                menuItemSpeed.setVisibility(View.INVISIBLE);
            }
        });
        menuItem.addView(menuItemSpeed);
        menuItemSpeed.setVisibility(View.INVISIBLE);

        speedItem = menuItem.addSubItem(gallery_menu_speed, R.drawable.msg_speed, null, LocaleController.getString("Speed", R.string.Speed), true, false);
        speedItem.setSubtext(LocaleController.getString("SpeedNormal", R.string.SpeedNormal));
        speedItem.setItemHeight(56);
        speedItem.setTag(R.id.width_tag, 240);
        speedItem.setColors(0xfffafafa, 0xfffafafa);
        speedItem.setRightIcon(R.drawable.msg_arrowright);
        speedGap = menuItem.addGap(gallery_menu_gap);
        menuItem.getPopupLayout().setFitItems(true);

        speedItems[0] = menuItemSpeed.addSubItem(gallery_menu_speed_veryslow, R.drawable.msg_speed_0_2, LocaleController.getString("SpeedVerySlow", R.string.SpeedVerySlow)).setColors(0xfffafafa, 0xfffafafa);
        speedItems[1] = menuItemSpeed.addSubItem(gallery_menu_speed_slow, R.drawable.msg_speed_0_5, LocaleController.getString("SpeedSlow", R.string.SpeedSlow)).setColors(0xfffafafa, 0xfffafafa);
        speedItems[2] = menuItemSpeed.addSubItem(gallery_menu_speed_normal, R.drawable.msg_speed_1, LocaleController.getString("SpeedNormal", R.string.SpeedNormal)).setColors(0xfffafafa, 0xfffafafa);
        speedItems[3] = menuItemSpeed.addSubItem(gallery_menu_speed_fast, R.drawable.msg_speed_1_5, LocaleController.getString("SpeedFast", R.string.SpeedFast)).setColors(0xfffafafa, 0xfffafafa);
        speedItems[4] = menuItemSpeed.addSubItem(gallery_menu_speed_veryfast, R.drawable.msg_speed_2, LocaleController.getString("SpeedVeryFast", R.string.SpeedVeryFast)).setColors(0xfffafafa, 0xfffafafa);

        menuItem.addSubItem(gallery_menu_openin, R.drawable.msg_openin, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        allMediaItem = menuItem.addSubItem(gallery_menu_showall, R.drawable.msg_media, LocaleController.getString("ShowAllMedia", R.string.ShowAllMedia));
        allMediaItem.setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_savegif, R.drawable.msg_gif, LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_showinchat, R.drawable.msg_message, LocaleController.getString("ShowInChat", R.string.ShowInChat)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_masks2, R.drawable.msg_sticker, LocaleController.getString("ShowStickers", R.string.ShowStickers)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_share, R.drawable.msg_shareout, LocaleController.getString("ShareFile", R.string.ShareFile)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_save, R.drawable.msg_gallery, LocaleController.getString("SaveToGallery", R.string.SaveToGallery)).setColors(0xfffafafa, 0xfffafafa);
        //menuItem.addSubItem(gallery_menu_edit_avatar, R.drawable.photo_paint, LocaleController.getString("EditPhoto", R.string.EditPhoto)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_set_as_main, R.drawable.menu_private, LocaleController.getString("SetAsMain", R.string.SetAsMain)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_delete, R.drawable.msg_delete, LocaleController.getString("Delete", R.string.Delete)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.addSubItem(gallery_menu_cancel_loading, R.drawable.msg_cancel, LocaleController.getString("StopDownload", R.string.StopDownload)).setColors(0xfffafafa, 0xfffafafa);
        menuItem.redrawPopup(0xf9222222);
        menuItemSpeed.redrawPopup(0xf9222222);
        setMenuItemIcon();

        menuItem.setSubMenuDelegate(new ActionBarMenuItem.ActionBarSubMenuItemDelegate() {
            @Override
            public void onShowSubMenu() {
                if (videoPlayerControlVisible && isPlaying) {
                    AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
                }
            }

            @Override
            public void onHideSubMenu() {
                if (videoPlayerControlVisible && isPlaying) {
                    scheduleActionBarHide();
                }
            }
        });

        bottomLayout = new FrameLayout(activityContext) {
            @Override
            protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
                if (child == nameTextView || child == dateTextView) {
                    widthUsed = bottomButtonsLayout.getMeasuredWidth();
                }
                super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
            }
        };
        bottomLayout.setBackgroundColor(0x7f000000);
        containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        pressedDrawable[0] = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0x32000000, 0});
        pressedDrawable[0].setShape(GradientDrawable.RECTANGLE);
        pressedDrawable[1] = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[] {0x32000000, 0});
        pressedDrawable[1].setShape(GradientDrawable.RECTANGLE);

        groupedPhotosListView = new GroupedPhotosListView(activityContext, AndroidUtilities.dp(10));
        containerView.addView(groupedPhotosListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM | Gravity.LEFT));
        groupedPhotosListView.setDelegate(new GroupedPhotosListView.GroupedPhotosListViewDelegate() {
            @Override
            public int getCurrentIndex() {
                return currentIndex;
            }

            @Override
            public int getCurrentAccount() {
                return currentAccount;
            }

            @Override
            public long getAvatarsDialogId() {
                return avatarsDialogId;
            }

            @Override
            public int getSlideshowMessageId() {
                return slideshowMessageId;
            }

            @Override
            public ArrayList<ImageLocation> getImagesArrLocations() {
                return imagesArrLocations;
            }

            @Override
            public ArrayList<MessageObject> getImagesArr() {
                return imagesArr;
            }

            @Override
            public List<TLRPC.PageBlock> getPageBlockArr() {
                return pageBlocksAdapter != null ? pageBlocksAdapter.getAll() : null;
            }

            @Override
            public Object getParentObject() {
                return pageBlocksAdapter != null ? pageBlocksAdapter.getParentObject() : null;
            }

            @Override
            public void setCurrentIndex(int index) {
                currentIndex = -1;
                if (currentThumb != null) {
                    currentThumb.release();
                    currentThumb = null;
                }
                dontAutoPlay = true;
                setImageIndex(index);
                dontAutoPlay = false;
            }

            @Override
            public void onShowAnimationStart() {
                containerView.requestLayout();
            }

            @Override
            public void onStopScrolling() {
                if (shouldMessageObjectAutoPlayed(currentMessageObject)) {
                    playerAutoStarted = true;
                    onActionClick(true);
                    checkProgress(0, false, true);
                }
            }

            @Override
            public boolean validGroupId(long groupId) {
                if (placeProvider != null) {
                    return placeProvider.validateGroupId(groupId);
                }
                return true;
            }
        });

        for (int a = 0; a < 3; a++) {
            fullscreenButton[a] = new ImageView(parentActivity);
            fullscreenButton[a].setImageResource(R.drawable.msg_maxvideo);
            fullscreenButton[a].setContentDescription(LocaleController.getString("AccSwitchToFullscreen", R.string.AccSwitchToFullscreen));
            fullscreenButton[a].setScaleType(ImageView.ScaleType.CENTER);
            fullscreenButton[a].setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
            fullscreenButton[a].setVisibility(View.INVISIBLE);
            fullscreenButton[a].setAlpha(1.0f);
            containerView.addView(fullscreenButton[a], LayoutHelper.createFrame(48, 48));
            fullscreenButton[a].setOnClickListener(v -> {
                if (parentActivity == null) {
                    return;
                }
                wasRotated = false;
                fullscreenedByButton = 1;
                if (prevOrientation == -10) {
                    prevOrientation = parentActivity.getRequestedOrientation();
                }
                WindowManager manager = (WindowManager) parentActivity.getSystemService(Activity.WINDOW_SERVICE);
                int displayRotation = manager.getDefaultDisplay().getRotation();
                if (displayRotation == Surface.ROTATION_270) {
                    parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                } else {
                    parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                toggleActionBar(false, false);
            });
        }

        final LinkMovementMethod captionLinkMovementMethod = new CaptionLinkMovementMethod();
        captionTextViewSwitcher = new CaptionTextViewSwitcher(containerView.getContext());
        captionTextViewSwitcher.setFactory(() -> createCaptionTextView(captionLinkMovementMethod));
        captionTextViewSwitcher.setVisibility(View.INVISIBLE);
        setCaptionHwLayerEnabled(true);

        for (int a = 0; a < 3; a++) {
            photoProgressViews[a] = new PhotoProgressView(containerView) {
                @Override
                protected void onBackgroundStateUpdated(int state) {
                    if (this == photoProgressViews[0]) {
                        updateAccessibilityOverlayVisibility();
                    }
                }

                @Override
                protected void onVisibilityChanged(boolean visible) {
                    if (this == photoProgressViews[0]) {
                        updateAccessibilityOverlayVisibility();
                    }
                }
            };
            photoProgressViews[a].setBackgroundState(PROGRESS_EMPTY, false, true);
        }

        miniProgressView = new RadialProgressView(activityContext, resourcesProvider) {
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

        bottomButtonsLayout = new LinearLayout(containerView.getContext());
        bottomButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        bottomLayout.addView(bottomButtonsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        paintButton = new ImageView(containerView.getContext());
        paintButton.setImageResource(R.drawable.photo_paint);
        paintButton.setScaleType(ImageView.ScaleType.CENTER);
        paintButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        bottomButtonsLayout.addView(paintButton, LayoutHelper.createFrame(50, LayoutHelper.MATCH_PARENT));
        paintButton.setOnClickListener(v -> openCurrentPhotoInPaintModeForSelect());
        paintButton.setContentDescription(LocaleController.getString("AccDescrPhotoEditor", R.string.AccDescrPhotoEditor));

        shareButton = new ImageView(containerView.getContext());
        shareButton.setImageResource(R.drawable.share);
        shareButton.setScaleType(ImageView.ScaleType.CENTER);
        shareButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        bottomButtonsLayout.addView(shareButton, LayoutHelper.createFrame(50, LayoutHelper.MATCH_PARENT));
        shareButton.setOnClickListener(v -> onSharePressed());
        shareButton.setContentDescription(LocaleController.getString("ShareFile", R.string.ShareFile));

        nameTextView = new FadingTextViewLayout(containerView.getContext()) {
            @Override
            protected void onTextViewCreated(TextView textView) {
                super.onTextViewCreated(textView);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setTextColor(0xffffffff);
                textView.setGravity(Gravity.LEFT);
            }
        };

        bottomLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 5, 8, 0));

        dateTextView = new FadingTextViewLayout(containerView.getContext(), true) {

            private LocaleController.LocaleInfo lastLocaleInfo = null;
            private int staticCharsCount = 0;

            @Override
            protected void onTextViewCreated(TextView textView) {
                super.onTextViewCreated(textView);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setTextColor(0xffffffff);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setGravity(Gravity.LEFT);
            }

            @Override
            protected int getStaticCharsCount() {
                final LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().getCurrentLocaleInfo();
                if (lastLocaleInfo != localeInfo) {
                    lastLocaleInfo = localeInfo;
                    staticCharsCount = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date()), LocaleController.getInstance().formatterDay.format(new Date())).length();
                }
                return staticCharsCount;
            }

            @Override
            public void setText(CharSequence text, boolean animated) {
                if (animated) {
                    boolean dontAnimateUnchangedStaticChars = true;
                    if (LocaleController.isRTL) {
                        final int staticCharsCount = getStaticCharsCount();
                        if (staticCharsCount > 0) {
                            if (text.length() != staticCharsCount || getText() == null || getText().length() != staticCharsCount) {
                                dontAnimateUnchangedStaticChars = false;
                            }
                        }
                    }
                    setText(text, true, dontAnimateUnchangedStaticChars);
                } else {
                    setText(text, false, false);
                }
            }
        };

        bottomLayout.addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 25, 8, 0));

        createVideoControlsInterface();

        progressView = new RadialProgressView(parentActivity, resourcesProvider);
        progressView.setProgressColor(0xffffffff);
        progressView.setBackgroundResource(R.drawable.circle_big);
        progressView.setVisibility(View.INVISIBLE);
        containerView.addView(progressView, LayoutHelper.createFrame(54, 54, Gravity.CENTER));

        qualityPicker = new PickerBottomLayoutViewer(parentActivity);
        qualityPicker.setBackgroundColor(0x7f000000);
        qualityPicker.updateSelectedCount(0, false);
        qualityPicker.setTranslationY(AndroidUtilities.dp(120));
        qualityPicker.doneButton.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
        qualityPicker.doneButton.setTextColor(getThemedColor(Theme.key_dialogFloatingButton));
        containerView.addView(qualityPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        qualityPicker.cancelButton.setOnClickListener(view -> {
            selectedCompression = previousCompression;
            didChangedCompressionLevel(false);
            showQualityView(false);
            requestVideoPreview(2);
        });
        qualityPicker.doneButton.setOnClickListener(view -> {
            showQualityView(false);
            requestVideoPreview(2);
        });

        videoForwardDrawable = new VideoForwardDrawable(false);
        videoForwardDrawable.setDelegate(new VideoForwardDrawable.VideoForwardDrawableDelegate() {
            @Override
            public void onAnimationEnd() {

            }

            @Override
            public void invalidate() {
                containerView.invalidate();
            }
        });

        qualityChooseView = new QualityChooseView(parentActivity);
        qualityChooseView.setTranslationY(AndroidUtilities.dp(120));
        qualityChooseView.setVisibility(View.INVISIBLE);
        qualityChooseView.setBackgroundColor(0x7f000000);
        containerView.addView(qualityChooseView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        pickerView = new FrameLayout(activityContext) {
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

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (videoTimelineView != null && videoTimelineView.getVisibility() != GONE) {
                    videoTimelineView.setTranslationY(translationY);
                    videoAvatarTooltip.setTranslationY(translationY);
                }
                if (videoAvatarTooltip != null && videoAvatarTooltip.getVisibility() != GONE) {
                    videoAvatarTooltip.setTranslationY(translationY);
                }
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (videoTimelineView != null && videoTimelineView.getVisibility() != GONE) {
                    videoTimelineView.setAlpha(alpha);
                }
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (videoTimelineView != null && videoTimelineView.getVisibility() != GONE) {
                    videoTimelineView.setVisibility(visibility == VISIBLE ? VISIBLE : INVISIBLE);
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (itemsLayout.getVisibility() != GONE) {
                    int x = (right - left - AndroidUtilities.dp(70) - itemsLayout.getMeasuredWidth()) / 2;
                    itemsLayout.layout(x, itemsLayout.getTop(), x + itemsLayout.getMeasuredWidth(), itemsLayout.getTop() + itemsLayout.getMeasuredHeight());
                }
            }
        };
        pickerView.setBackgroundColor(0x7f000000);
        containerView.addView(pickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        docNameTextView = new TextView(containerView.getContext());
        docNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        docNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        docNameTextView.setSingleLine(true);
        docNameTextView.setMaxLines(1);
        docNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        docNameTextView.setTextColor(0xffffffff);
        docNameTextView.setGravity(Gravity.LEFT);
        pickerView.addView(docNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 23, 84, 0));

        docInfoTextView = new TextView(containerView.getContext());
        docInfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        docInfoTextView.setSingleLine(true);
        docInfoTextView.setMaxLines(1);
        docInfoTextView.setEllipsize(TextUtils.TruncateAt.END);
        docInfoTextView.setTextColor(0xffffffff);
        docInfoTextView.setGravity(Gravity.LEFT);
        pickerView.addView(docInfoTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 46, 84, 0));

        videoTimelineView = new VideoTimelinePlayView(parentActivity) {
            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    super.setTranslationY(translationY);
                    containerView.invalidate();
                }
            }
        };
        videoTimelineView.setDelegate(new VideoTimelinePlayView.VideoTimelineViewDelegate() {

            private Runnable seekToRunnable;
            private int seekTo;
            private boolean wasPlaying;

            @Override
            public void onLeftProgressChanged(float progress) {
                if (videoPlayer == null) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    manuallyPaused = false;
                    videoPlayer.pause();
                    containerView.invalidate();
                }
                updateAvatarStartTime(1);
                seekTo(progress);
                videoPlayerSeekbar.setProgress(0);
                videoTimelineView.setProgress(progress);
                updateVideoInfo();
            }

            @Override
            public void onRightProgressChanged(float progress) {
                if (videoPlayer == null) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    manuallyPaused = false;
                    videoPlayer.pause();
                    containerView.invalidate();
                }
                updateAvatarStartTime(2);
                seekTo(progress);
                videoPlayerSeekbar.setProgress(1f);
                videoTimelineView.setProgress(progress);
                updateVideoInfo();
            }

            @Override
            public void onPlayProgressChanged(float progress) {
                if (videoPlayer == null) {
                    return;
                }
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    updateAvatarStartTime(0);
                }
                seekTo(progress);
            }

            private void seekTo(float progress) {
                seekTo = (int) (videoDuration * progress);
                if (seekToRunnable == null) {
                    AndroidUtilities.runOnUIThread(seekToRunnable = () -> {
                        if (videoPlayer != null) {
                            videoPlayer.seekTo(seekTo);
                        }
                        if (sendPhotoType == SELECT_TYPE_AVATAR) {
                            needCaptureFrameReadyAtTime = seekTo;
                            if (captureFrameReadyAtTime != needCaptureFrameReadyAtTime) {
                                captureFrameReadyAtTime = -1;
                            }
                        }
                        seekToRunnable = null;
                    }, 100);
                }
            }

            private void updateAvatarStartTime(int fix) {
                if (sendPhotoType != SELECT_TYPE_AVATAR) {
                    return;
                }
                if (fix != 0) {
                    if (photoCropView != null && (videoTimelineView.getLeftProgress() > avatarStartProgress || videoTimelineView.getRightProgress() < avatarStartProgress)) {
                        photoCropView.setVideoThumbVisible(false);
                        if (fix == 1) {
                            avatarStartTime = (long) (videoDuration * 1000 * videoTimelineView.getLeftProgress());
                        } else {
                            avatarStartTime = (long) (videoDuration * 1000 * videoTimelineView.getRightProgress());
                        }
                        captureFrameAtTime = -1;
                    }
                } else {
                    avatarStartProgress = videoTimelineView.getProgress();
                    avatarStartTime = (long) (videoDuration * 1000 * avatarStartProgress);
                }
            }

            @Override
            public void didStartDragging(int type) {
                if (type == VideoTimelinePlayView.TYPE_PROGRESS) {
                    cancelVideoPlayRunnable();
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        cancelFlashAnimations();
                        captureFrameAtTime = -1;
                    }
                    if (wasPlaying = videoPlayer != null && videoPlayer.isPlaying()) {
                        manuallyPaused = false;
                        videoPlayer.pause();
                        containerView.invalidate();
                    }
                }
            }

            @Override
            public void didStopDragging(int type) {
                if (seekToRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(seekToRunnable);
                    seekToRunnable.run();
                }
                cancelVideoPlayRunnable();
                if (sendPhotoType == SELECT_TYPE_AVATAR && flashView != null && type == VideoTimelinePlayView.TYPE_PROGRESS) {
                    cancelFlashAnimations();
                    captureFrameAtTime = avatarStartTime;
                    if (captureFrameReadyAtTime == seekTo) {
                        captureCurrentFrame();
                    }
                } else {
                    if (sendPhotoType == SELECT_TYPE_AVATAR || wasPlaying) {
                        manuallyPaused = false;
                        if (videoPlayer != null) {
                            videoPlayer.play();
                        }
                    }
                }
            }
        });
        showVideoTimeline(false, false);
        videoTimelineView.setBackgroundColor(0x7f000000);
        containerView.addView(videoTimelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.BOTTOM, 0, 8, 0, 0));

        videoAvatarTooltip = new TextView(parentActivity);
        videoAvatarTooltip.setSingleLine(true);
        videoAvatarTooltip.setVisibility(View.GONE);
        videoAvatarTooltip.setText(LocaleController.getString("ChooseCover", R.string.ChooseCover));
        videoAvatarTooltip.setGravity(Gravity.CENTER_HORIZONTAL);
        videoAvatarTooltip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        videoAvatarTooltip.setTextColor(0xff8c8c8c);
        containerView.addView(videoAvatarTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 8, 0, 0));

        pickerViewSendButton = new ImageView(parentActivity) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return bottomTouchEnabled && super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return bottomTouchEnabled && super.onTouchEvent(event);
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (captionEditText.getCaptionLimitOffset() < 0) {
                    captionLimitView.setVisibility(visibility);
                } else {
                    captionLimitView.setVisibility(View.GONE);
                }
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                captionLimitView.setTranslationY(translationY);
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                captionLimitView.setAlpha(alpha);
            }
        };
        pickerViewSendButton.setScaleType(ImageView.ScaleType.CENTER);
        pickerViewSendDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), getThemedColor(Theme.key_dialogFloatingButton), getThemedColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton));
        pickerViewSendButton.setBackgroundDrawable(pickerViewSendDrawable);
        pickerViewSendButton.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
        pickerViewSendButton.setImageResource(R.drawable.attach_send);
        pickerViewSendButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        containerView.addView(pickerViewSendButton, LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 14, 14));
        pickerViewSendButton.setContentDescription(LocaleController.getString("Send", R.string.Send));
        pickerViewSendButton.setOnClickListener(v -> {
            if (captionEditText.getCaptionLimitOffset() < 0) {
                AndroidUtilities.shakeView(captionLimitView, 2, 0);
                Vibrator vibrator = (Vibrator) captionLimitView.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
                return;
            }
            if (parentChatActivity != null && parentChatActivity.isInScheduleMode() && !parentChatActivity.isEditingMessageMedia()) {
                showScheduleDatePickerDialog();
            } else {
                sendPressed(true, 0);
            }
        });
        pickerViewSendButton.setOnLongClickListener(view -> {
            if (placeProvider != null && !placeProvider.allowSendingSubmenu()) {
                return false;
            }
            if (parentChatActivity == null || parentChatActivity.isInScheduleMode()) {
                return false;
            }
            if (captionEditText.getCaptionLimitOffset() < 0) {
                return false;
            }
            TLRPC.Chat chat = parentChatActivity.getCurrentChat();
            TLRPC.User user = parentChatActivity.getCurrentUser();

            sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity);
            sendPopupLayout.setAnimationEnabled(false);
            sendPopupLayout.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        v.getHitRect(hitRect);
                        if (!hitRect.contains((int) event.getX(), (int) event.getY())) {
                            sendPopupWindow.dismiss();
                        }
                    }
                }
                return false;
            });
            sendPopupLayout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                    sendPopupWindow.dismiss();
                }
            });
            sendPopupLayout.setShownFromBotton(false);
            sendPopupLayout.setBackgroundColor(0xf9222222);

            final boolean canReplace = placeProvider != null && placeProvider.canReplace(currentIndex);
            final int[] order = {4, 3, 2, 0, 1};
            for (int i = 0; i < 5; i++) {
                final int a = order[i];
                if (a != 2 && a != 3 && canReplace) {
                    continue;
                }
                if (a == 0 && !parentChatActivity.canScheduleMessage()) {
                    continue;
                }
                if (a == 0 && placeProvider != null && placeProvider.getSelectedPhotos() != null) {
                    HashMap<Object, Object> hashMap = placeProvider.getSelectedPhotos();
                    boolean hasTtl = false;
                    for (HashMap.Entry<Object, Object> entry : hashMap.entrySet()) {
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
                } else if ((a == 2 || a == 3) && !canReplace) {
                    continue;
                } else if (a == 4 && (isCurrentVideo || timeItem.getColorFilter() != null)) {
                    continue;
                }
                ActionBarMenuSubItem cell = new ActionBarMenuSubItem(parentActivity, a == 0, a == 3, resourcesProvider);
                if (a == 0) {
                    if (UserObject.isUserSelf(user)) {
                        cell.setTextAndIcon(LocaleController.getString("SetReminder", R.string.SetReminder), R.drawable.msg_schedule);
                    } else {
                        cell.setTextAndIcon(LocaleController.getString("ScheduleMessage", R.string.ScheduleMessage), R.drawable.msg_schedule);
                    }
                } else if (a == 1) {
                    cell.setTextAndIcon(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound), R.drawable.input_notify_off);
                } else if (a == 2) {
                    cell.setTextAndIcon(LocaleController.getString("ReplacePhoto", R.string.ReplacePhoto), R.drawable.msg_replace);
                } else if (a == 3) {
                    cell.setTextAndIcon(LocaleController.getString("SendAsNewPhoto", R.string.SendAsNewPhoto), R.drawable.msg_sendphoto);
                } else if (a == 4) {
                    cell.setTextAndIcon(LocaleController.getString("SendWithoutCompression", R.string.SendWithoutCompression), R.drawable.msg_sendfile);
                }
                cell.setMinimumWidth(AndroidUtilities.dp(196));
                cell.setColors(0xffffffff, 0xffffffff);
                sendPopupLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                cell.setOnClickListener(v -> {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                    if (a == 0) {
                        showScheduleDatePickerDialog();
                    } else if (a == 1) {
                        sendPressed(false, 0);
                    } else if (a == 2) {
                        replacePressed();
                    } else if (a == 3) {
                        sendPressed(true, 0);
                    } else if (a == 4) {
                        sendPressed(true, 0, false, true);
                    }
                });
            }
            if (sendPopupLayout.getChildCount() == 0) {
                return false;
            }
            sendPopupLayout.setupRadialSelectors(0x24ffffff);

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
            sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + AndroidUtilities.dp(14), location[1] - sendPopupLayout.getMeasuredHeight() - AndroidUtilities.dp(18));
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

            return false;
        });


        captionLimitView = new TextView(parentActivity);
        captionLimitView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        captionLimitView.setTextColor(0xffEC7777);
        captionLimitView.setGravity(Gravity.CENTER);
        captionLimitView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        containerView.addView(captionLimitView, LayoutHelper.createFrame(56, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 14, 78));

        itemsLayout = new LinearLayout(parentActivity) {

            boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int visibleItemsCount = 0;
                int count = getChildCount();
                for (int a = 0; a < count; a++) {
                    View v = getChildAt(a);
                    if (v.getVisibility() != VISIBLE) {
                        continue;
                    }
                    visibleItemsCount++;
                }
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                if (visibleItemsCount != 0) {
                    int itemWidth = Math.min(AndroidUtilities.dp(70), width / visibleItemsCount);
                    if (compressItem.getVisibility() == VISIBLE) {
                        ignoreLayout = true;
                        int compressIconWidth;
                        if (selectedCompression < 2) {
                            compressIconWidth = 48;
                        } else {
                            compressIconWidth = 64;
                        }
                        int padding = Math.max(0, (itemWidth - AndroidUtilities.dp(compressIconWidth)) / 2);
                        compressItem.setPadding(padding, 0, padding, 0);
                        ignoreLayout = false;
                    }
                    for (int a = 0; a < count; a++) {
                        View v = getChildAt(a);
                        if (v.getVisibility() == GONE) {
                            continue;
                        }
                        v.measure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                    setMeasuredDimension(itemWidth * visibleItemsCount, height);
                } else {
                    setMeasuredDimension(width, height);
                }
            }
        };
        itemsLayout.setOrientation(LinearLayout.HORIZONTAL);
        pickerView.addView(itemsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 70, 0));

        cropItem = new ImageView(parentActivity);
        cropItem.setScaleType(ImageView.ScaleType.CENTER);
        cropItem.setImageResource(R.drawable.photo_crop);
        cropItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(cropItem, LayoutHelper.createLinear(48, 48));
        cropItem.setOnClickListener(v -> {
            if (captionEditText.getTag() != null) {
                return;
            }
            if (isCurrentVideo) {
                if (!videoConvertSupported) {
                    return;
                }
                if (videoTextureView instanceof VideoEditTextureView) {
                    VideoEditTextureView textureView = (VideoEditTextureView) videoTextureView;
                    if (textureView.getVideoWidth() <= 0 || textureView.getVideoHeight() <= 0) {
                        return;
                    }
                } else {
                    return;
                }
            }
            switchToEditMode(1);
        });
        cropItem.setContentDescription(LocaleController.getString("CropImage", R.string.CropImage));

        rotateItem = new ImageView(parentActivity);
        rotateItem.setScaleType(ImageView.ScaleType.CENTER);
        rotateItem.setImageResource(R.drawable.tool_rotate);
        rotateItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(rotateItem, LayoutHelper.createLinear(48, 48));
        rotateItem.setOnClickListener(v -> {
            if (photoCropView == null) {
                return;
            }
            if (photoCropView.rotate()) {
                rotateItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY));
            } else {
                rotateItem.setColorFilter(null);
            }
        });
        rotateItem.setContentDescription(LocaleController.getString("AccDescrRotate", R.string.AccDescrRotate));

        mirrorItem = new ImageView(parentActivity);
        mirrorItem.setScaleType(ImageView.ScaleType.CENTER);
        mirrorItem.setImageResource(R.drawable.photo_flip);
        mirrorItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(mirrorItem, LayoutHelper.createLinear(48, 48));
        mirrorItem.setOnClickListener(v -> {
            if (photoCropView == null) {
                return;
            }
            if (photoCropView.mirror()) {
                mirrorItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY));
            } else {
                mirrorItem.setColorFilter(null);
            }
        });
        mirrorItem.setContentDescription(LocaleController.getString("AccDescrMirror", R.string.AccDescrMirror));

        paintItem = new ImageView(parentActivity);
        paintItem.setScaleType(ImageView.ScaleType.CENTER);
        paintItem.setImageResource(R.drawable.photo_paint);
        paintItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(paintItem, LayoutHelper.createLinear(48, 48));
        paintItem.setOnClickListener(v -> {
            if (captionEditText.getTag() != null) {
                return;
            }
            if (isCurrentVideo) {
                if (!videoConvertSupported) {
                    return;
                }
                if (videoTextureView instanceof VideoEditTextureView) {
                    VideoEditTextureView textureView = (VideoEditTextureView) videoTextureView;
                    if (textureView.getVideoWidth() <= 0 || textureView.getVideoHeight() <= 0) {
                        return;
                    }
                } else {
                    return;
                }
            }
            switchToEditMode(3);
        });
        paintItem.setContentDescription(LocaleController.getString("AccDescrPhotoEditor", R.string.AccDescrPhotoEditor));

        muteItem = new ImageView(parentActivity);
        muteItem.setScaleType(ImageView.ScaleType.CENTER);
        muteItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        containerView.addView(muteItem, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 0));
        muteItem.setOnClickListener(v -> {
            if (captionEditText.getTag() != null) {
                return;
            }
            muteVideo = !muteVideo;
            updateMuteButton();
            updateVideoInfo();
            if (muteVideo && !checkImageView.isChecked()) {
                checkImageView.callOnClick();
            } else {
                Object object = imagesArrLocals.get(currentIndex);
                if (object instanceof MediaController.MediaEditState) {
                    ((MediaController.MediaEditState) object).editedInfo = getCurrentVideoEditedInfo();
                }
            }
        });

        cameraItem = new ImageView(parentActivity);
        cameraItem.setScaleType(ImageView.ScaleType.CENTER);
        cameraItem.setImageResource(R.drawable.photo_add);
        cameraItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        cameraItem.setContentDescription(LocaleController.getString("AccDescrTakeMorePics", R.string.AccDescrTakeMorePics));
        containerView.addView(cameraItem, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 16, 0));
        cameraItem.setOnClickListener(v -> {
            if (placeProvider == null || captionEditText.getTag() != null) {
                return;
            }
            placeProvider.needAddMorePhotos();
            closePhoto(true, false);
        });

        tuneItem = new ImageView(parentActivity);
        tuneItem.setScaleType(ImageView.ScaleType.CENTER);
        tuneItem.setImageResource(R.drawable.photo_tools);
        tuneItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        itemsLayout.addView(tuneItem, LayoutHelper.createLinear(48, 48));
        tuneItem.setOnClickListener(v -> {
            if (captionEditText.getTag() != null) {
                return;
            }
            if (isCurrentVideo) {
                if (!videoConvertSupported) {
                    return;
                }
                if (videoTextureView instanceof VideoEditTextureView) {
                    VideoEditTextureView textureView = (VideoEditTextureView) videoTextureView;
                    if (textureView.getVideoWidth() <= 0 || textureView.getVideoHeight() <= 0) {
                        return;
                    }
                } else {
                    return;
                }
            }
            switchToEditMode(2);
        });
        tuneItem.setContentDescription(LocaleController.getString("AccDescrPhotoAdjust", R.string.AccDescrPhotoAdjust));

        compressItem = new ImageView(parentActivity);
        compressItem.setTag(1);
        compressItem.setScaleType(ImageView.ScaleType.CENTER);
        compressItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));

        selectedCompression = selectCompression();
        int compressIconWidth;
        if (selectedCompression <= 1) {
            compressItem.setImageResource(R.drawable.video_quality1);
        } else if (selectedCompression == 2) {
            compressItem.setImageResource(R.drawable.video_quality2);
        } else {
            selectedCompression = compressionsCount - 1;
            compressItem.setImageResource(R.drawable.video_quality3);
        }
        compressItem.setContentDescription(LocaleController.getString("AccDescrVideoQuality", R.string.AccDescrVideoQuality));
        itemsLayout.addView(compressItem, LayoutHelper.createLinear(48, 48));
        compressItem.setOnClickListener(v -> {
            if (captionEditText.getTag() != null || muteVideo) {
                return;
            }
            if (compressItem.getTag() == null) {
                if (videoConvertSupported) {
                    if (tooltip == null) {
                        tooltip = new Tooltip(activity, containerView, 0xcc111111, Color.WHITE);
                    }
                    tooltip.setText(LocaleController.getString("VideoQualityIsTooLow", R.string.VideoQualityIsTooLow));
                    tooltip.show(compressItem);
                }
                return;
            }
            showQualityView(true);
            requestVideoPreview(1);
        });

        timeItem = new ImageView(parentActivity);
        timeItem.setScaleType(ImageView.ScaleType.CENTER);
        timeItem.setImageResource(R.drawable.photo_timer);
        timeItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        timeItem.setContentDescription(LocaleController.getString("SetTimer", R.string.SetTimer));
        itemsLayout.addView(timeItem, LayoutHelper.createLinear(48, 48));
        timeItem.setOnClickListener(v -> {
            if (parentActivity == null || captionEditText.getTag() != null) {
                return;
            }
            BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity, false, resourcesProvider);
            builder.setUseHardwareLayer(false);
            LinearLayout linearLayout = new LinearLayout(parentActivity);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setCustomView(linearLayout);

            TextView titleView = new TextView(parentActivity);
            titleView.setLines(1);
            titleView.setSingleLine(true);
            titleView.setText(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
            titleView.setTextColor(0xffffffff);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(4));
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            linearLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            titleView.setOnTouchListener((v13, event) -> true);

            titleView = new TextView(parentActivity);
            titleView.setText(isCurrentVideo ? LocaleController.getString("MessageLifetimeVideo", R.string.MessageLifetimeVideo) : LocaleController.getString("MessageLifetimePhoto", R.string.MessageLifetimePhoto));
            titleView.setTextColor(0xff808080);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            titleView.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), AndroidUtilities.dp(8));
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            linearLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            titleView.setOnTouchListener((v12, event) -> true);

            final BottomSheet bottomSheet = builder.create();
            final NumberPicker numberPicker = new NumberPicker(parentActivity, resourcesProvider);
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
                SharedPreferences preferences1 = MessagesController.getGlobalMainSettings();
                numberPicker.setValue(preferences1.getInt("self_destruct", 7));
            } else {
                if (currentTTL >= 0 && currentTTL < 21) {
                    numberPicker.setValue(currentTTL);
                } else {
                    numberPicker.setValue(21 + currentTTL / 5 - 5);
                }
            }
            numberPicker.setTextColor(0xffffffff);
            numberPicker.setSelectorColor(0xff4d4d4d);
            numberPicker.setFormatter(value -> {
                if (value == 0) {
                    return LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever);
                } else if (value >= 1 && value < 21) {
                    return LocaleController.formatTTLString(value);
                } else {
                    return LocaleController.formatTTLString((value - 16) * 5);
                }
            });
            linearLayout.addView(numberPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            FrameLayout buttonsLayout = new FrameLayout(parentActivity) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int count = getChildCount();
                    int width = right - left;
                    for (int a = 0; a < count; a++) {
                        View child = getChildAt(a);
                        if ((Integer) child.getTag() == Dialog.BUTTON_POSITIVE) {
                            child.layout(width - getPaddingRight() - child.getMeasuredWidth(), getPaddingTop(), width - getPaddingRight(), getPaddingTop() + child.getMeasuredHeight());
                        } else if ((Integer) child.getTag() == Dialog.BUTTON_NEGATIVE) {
                            int x = getPaddingLeft();
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
            textView.setTextColor(getThemedColor(Theme.key_dialogFloatingButton));
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
            textView.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(0xff49bcf2));
            textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
            textView.setOnClickListener(v1 -> {
                int value = numberPicker.getValue();
                SharedPreferences preferences1 = MessagesController.getGlobalMainSettings();
                SharedPreferences.Editor editor = preferences1.edit();
                editor.putInt("self_destruct", value);
                editor.commit();
                bottomSheet.dismiss();
                int seconds;
                if (value >= 0 && value < 21) {
                    seconds = value;
                } else {
                    seconds = (value - 16) * 5;
                }
                Object object1 = imagesArrLocals.get(currentIndex);
                if (object1 instanceof MediaController.PhotoEntry) {
                    ((MediaController.PhotoEntry) object1).ttl = seconds;
                } else if (object1 instanceof MediaController.SearchImage) {
                    ((MediaController.SearchImage) object1).ttl = seconds;
                }
                timeItem.setColorFilter(seconds != 0 ? new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY) : null);
                if (!checkImageView.isChecked()) {
                    checkImageView.callOnClick();
                }
            });

            textView = new TextView(parentActivity);
            textView.setMinWidth(AndroidUtilities.dp(64));
            textView.setTag(Dialog.BUTTON_NEGATIVE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(0xffffffff);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            textView.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(0xffffffff));
            textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
            textView.setOnClickListener(v14 -> bottomSheet.dismiss());
            bottomSheet.show();
            bottomSheet.setBackgroundColor(0xff000000);
        });

        editorDoneLayout = new PickerBottomLayoutViewer(activityContext);
        editorDoneLayout.setBackgroundColor(0xcc000000);
        editorDoneLayout.updateSelectedCount(0, false);
        editorDoneLayout.setVisibility(View.GONE);
        containerView.addView(editorDoneLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        editorDoneLayout.cancelButton.setOnClickListener(view -> {
            cropTransform.setViewTransform(previousHasTransform, previousCropPx, previousCropPy, previousCropRotation, previousCropOrientation, previousCropScale, 1.0f, 1.0f, previousCropPw, previousCropPh, 0, 0, previousCropMirrored);
            switchToEditMode(0);
        });
        editorDoneLayout.doneButton.setOnClickListener(view -> {
            if (currentEditMode == 1 && !photoCropView.isReady()) {
                return;
            }
            applyCurrentEditMode();
            switchToEditMode(0);
        });

        resetButton = new TextView(activityContext);
        resetButton.setVisibility(View.GONE);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetButton.setTextColor(0xffffffff);
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        resetButton.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        resetButton.setText(LocaleController.getString("Reset", R.string.CropReset).toUpperCase());
        resetButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        editorDoneLayout.addView(resetButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER));
        resetButton.setOnClickListener(v -> photoCropView.reset());

        gestureDetector = new GestureDetector2(containerView.getContext(), this);
        gestureDetector.setIsLongpressEnabled(false);
        setDoubleTapEnabled(true);

        ImageReceiver.ImageReceiverDelegate imageReceiverDelegate = (imageReceiver, set, thumb, memCache) -> {
            if (imageReceiver == centerImage && set && !thumb) {
                if (!isCurrentVideo && (currentEditMode == 1 || sendPhotoType == SELECT_TYPE_AVATAR) && photoCropView != null) {
                    Bitmap bitmap = imageReceiver.getBitmap();
                    if (bitmap != null) {
                        photoCropView.setBitmap(bitmap, imageReceiver.getOrientation(), sendPhotoType != SELECT_TYPE_AVATAR, true, paintingOverlay, cropTransform, null, null);
                    }
                }
                if (paintingOverlay.getVisibility() == View.VISIBLE) {
                    containerView.requestLayout();
                }
                detectFaces();
            }
            if (imageReceiver == centerImage && set && placeProvider != null && placeProvider.scaleToFill() && !ignoreDidSetImage && sendPhotoType != SELECT_TYPE_AVATAR) {
                if (!wasLayout) {
                    dontResetZoomOnFirstLayout = true;
                } else {
                    setScaleToFill();
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
        checkImageView.setSize(34);
        checkImageView.setCheckOffset(AndroidUtilities.dp(1));
        checkImageView.setColor(getThemedColor(Theme.key_dialogFloatingButton), 0xffffffff);
        checkImageView.setVisibility(View.GONE);
        containerView.addView(checkImageView, LayoutHelper.createFrame(34, 34, Gravity.RIGHT | Gravity.TOP, 0, rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90 ? 61 : 71, 11, 0));
        if (isStatusBarVisible()) {
            ((FrameLayout.LayoutParams) checkImageView.getLayoutParams()).topMargin += AndroidUtilities.statusBarHeight;
        }
        checkImageView.setOnClickListener(v -> {
            if (captionEditText.getTag() != null) {
                return;
            }
            setPhotoChecked();
        });

        photosCounterView = new CounterView(parentActivity);
        containerView.addView(photosCounterView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 0, rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90 ? 58 : 68, 64, 0));
        if (isStatusBarVisible()) {
            ((FrameLayout.LayoutParams) photosCounterView.getLayoutParams()).topMargin += AndroidUtilities.statusBarHeight;
        }
        photosCounterView.setOnClickListener(v -> {
            if (captionEditText.getTag() != null || placeProvider == null || placeProvider.getSelectedPhotosOrder() == null || placeProvider.getSelectedPhotosOrder().isEmpty()) {
                return;
            }
            togglePhotosListView(!isPhotosListViewVisible, true);
        });

        selectedPhotosListView = new SelectedPhotosListView(parentActivity);
        selectedPhotosListView.setVisibility(View.GONE);
        selectedPhotosListView.setAlpha(0.0f);
        selectedPhotosListView.setLayoutManager(new LinearLayoutManager(parentActivity, LinearLayoutManager.HORIZONTAL, true) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerEnd linearSmoothScroller = new LinearSmoothScrollerEnd(recyclerView.getContext()) {
                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return Math.max(180, super.calculateTimeForDeceleration(dx));
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        selectedPhotosListView.setAdapter(selectedPhotosAdapter = new ListAdapter(parentActivity));
        containerView.addView(selectedPhotosListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 103, Gravity.LEFT | Gravity.TOP));
        selectedPhotosListView.setOnItemClickListener((view, position) -> {
            if (!imagesArrLocals.isEmpty() && currentIndex >= 0 && currentIndex < imagesArrLocals.size()) {
                Object entry = imagesArrLocals.get(currentIndex);
                if (entry instanceof MediaController.MediaEditState) {
                    ((MediaController.MediaEditState) entry).editedInfo = getCurrentVideoEditedInfo();
                }
            }
            ignoreDidSetImage = true;
            int idx = imagesArrLocals.indexOf(view.getTag());
            if (idx >= 0) {
                currentIndex = -1;
                setImageIndex(idx);
            }
            ignoreDidSetImage = false;
        });

        captionEditText = new PhotoViewerCaptionEnterView(activityContext, containerView, windowView, resourcesProvider) {
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
                if (bottomTouchEnabled && event.getAction() == MotionEvent.ACTION_DOWN) {
                    keyboardAnimationEnabled = true;
                }
                return !bottomTouchEnabled && super.onTouchEvent(event);
            }

            @Override
            protected void extendActionMode(ActionMode actionMode, Menu menu) {
                if (parentChatActivity != null) {
                    parentChatActivity.extendActionMode(menu);
                }
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
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), captionEditText.getCursorPosition(), parentChatActivity.messages, false, false);
                }
                int color = getThemedColor(Theme.key_dialogFloatingIcon);
                if (captionEditText.getCaptionLimitOffset() < 0) {
                    captionLimitView.setText(Integer.toString(captionEditText.getCaptionLimitOffset()));
                    captionLimitView.setVisibility(pickerViewSendButton.getVisibility());
                    pickerViewSendButton.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * 0.58f)), PorterDuff.Mode.MULTIPLY));
                } else {
                    pickerViewSendButton.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    captionLimitView.setVisibility(View.GONE);
                }
                if (placeProvider != null) {
                    placeProvider.onCaptionChanged(text);
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

            @Override
            public void onEmojiViewCloseStart() {
                setOffset(captionEditText.getEmojiPadding());
                if (captionEditText.getTag() != null) {
                    if (isCurrentVideo) {
                        actionBar.setTitleAnimated(muteVideo ? LocaleController.getString("GifCaption", R.string.GifCaption) : LocaleController.getString("VideoCaption", R.string.VideoCaption),true, 220);
                    } else {
                        actionBar.setTitleAnimated(LocaleController.getString("PhotoCaption", R.string.PhotoCaption), true, 220);
                    }
                    checkImageView.animate().alpha(0f).setDuration(220).start();
                    photosCounterView.animate().alpha(0f).setDuration(220).start();
                    selectedPhotosListView.animate().alpha(0.0f).translationY(-AndroidUtilities.dp(10)).setDuration(220).start();
                } else {
                    checkImageView.animate().alpha(1f).setDuration(220).start();
                    photosCounterView.animate().alpha(1f).setDuration(220).start();
                    if (lastTitle != null) {
                        actionBar.setTitleAnimated(lastTitle, false, 220);
                        lastTitle = null;
                    }
                }
            }

            @Override
            public void onEmojiViewCloseEnd() {
                setOffset(0);
                captionEditText.setVisibility(View.GONE);
            }

            private void setOffset(int offset) {
                for (int i = 0; i < containerView.getChildCount(); i++) {
                    View child = containerView.getChildAt(i);
                    if (child == cameraItem || child == muteItem || child == pickerView || child == videoTimelineView || child == pickerViewSendButton || child == captionTextViewSwitcher || muteItem.getVisibility() == View.VISIBLE && child == bottomLayout) {
                        child.setTranslationY(offset);
                    }
                }
            }
        });
        if (Build.VERSION.SDK_INT >= 19) {
            captionEditText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        captionEditText.setVisibility(View.GONE);
        containerView.addView(captionEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        mentionListView = new RecyclerListView(activityContext, resourcesProvider) {
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
        mentionLayoutManager = new LinearLayoutManager(activityContext) {
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

        mentionListView.setAdapter(mentionsAdapter = new MentionsAdapter(activityContext, true, 0, 0, new MentionsAdapter.MentionsAdapterDelegate() {
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
                                ObjectAnimator.ofFloat(mentionListView, View.ALPHA, 0.0f, 1.0f)
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
                                ObjectAnimator.ofFloat(mentionListView, View.ALPHA, 0.0f)
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
        }, resourcesProvider));

        mentionListView.setOnItemClickListener((view, position) -> {
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
            } else if (object instanceof MediaDataController.KeywordResult) {
                String code = ((MediaDataController.KeywordResult) object).emoji;
                captionEditText.addEmojiToRecent(code);
                captionEditText.replaceWithText(start, len, code, true);
            }
        });

        mentionListView.setOnItemLongClickListener((view, position) -> {
            Object object = mentionsAdapter.getItem(position);
            if (object instanceof String) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> mentionsAdapter.clearRecentHashtags());
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showAlertDialog(builder);
                return true;
            }
            return false;
        });

        hintView = new UndoView(activityContext, null, false, resourcesProvider);
        hintView.setAdditionalTranslationY(AndroidUtilities.dp(112));
        hintView.setColors(0xf9222222, 0xffffffff);
        containerView.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        AccessibilityManager am = (AccessibilityManager) activityContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am.isEnabled()) {
            playButtonAccessibilityOverlay = new View(activityContext);
            playButtonAccessibilityOverlay.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            playButtonAccessibilityOverlay.setFocusable(true);
            containerView.addView(playButtonAccessibilityOverlay, LayoutHelper.createFrame(64, 64, Gravity.CENTER));
        }
    }

    private void showScheduleDatePickerDialog() {
        if (parentChatActivity == null) {
            return;
        }
        final AlertsCreator.ScheduleDatePickerColors colors = new AlertsCreator.ScheduleDatePickerColors(0xffffffff, 0xff252525, 0xffffffff, 0x1effffff, 0xffffffff, 0xf9222222, 0x24ffffff);
        AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentChatActivity.getDialogId(), this::sendPressed, colors);
    }

    private void sendPressed(boolean notify, int scheduleDate) {
        sendPressed(notify, scheduleDate, false, false);
    }

    private void replacePressed() {
        sendPressed(false, 0, true, false);
    }

    private void sendPressed(boolean notify, int scheduleDate, boolean replace, boolean forceDocument) {
        if (captionEditText.getTag() != null) {
            return;
        }
        if (placeProvider != null && !doneButtonPressed) {
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                applyCurrentEditMode();
            }
            if (!replace && parentChatActivity != null) {
                TLRPC.Chat chat = parentChatActivity.getCurrentChat();
                TLRPC.User user = parentChatActivity.getCurrentUser();
                if (user != null || ChatObject.isChannel(chat) && chat.megagroup || !ChatObject.isChannel(chat)) {
                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_" + parentChatActivity.getDialogId(), !notify).commit();
                }
            }
            VideoEditedInfo videoEditedInfo = getCurrentVideoEditedInfo();
            if (!imagesArrLocals.isEmpty() && currentIndex >= 0 && currentIndex < imagesArrLocals.size()) {
                Object entry = imagesArrLocals.get(currentIndex);
                if (entry instanceof MediaController.MediaEditState) {
                    ((MediaController.MediaEditState) entry).editedInfo = videoEditedInfo;
                }
            }
            doneButtonPressed = true;
            if (!replace) {
                placeProvider.sendButtonPressed(currentIndex, videoEditedInfo, notify, scheduleDate, forceDocument);
            } else {
                placeProvider.replaceButtonPressed(currentIndex, videoEditedInfo);
            }
            closePhoto(false, false);
        }
    }

    private void setMenuItemIcon() {
        if (speedItem.getVisibility() != View.VISIBLE) {
            menuItem.setIcon(R.drawable.ic_ab_other);
            return;
        }
        if (Math.abs(currentVideoSpeed - 0.25f) < 0.001f) {
            menuItem.setIcon(R.drawable.msg_more_0_2);
            speedItem.setSubtext(LocaleController.getString("SpeedVerySlow", R.string.SpeedVerySlow));
        } else if (Math.abs(currentVideoSpeed - 0.5f) < 0.001f) {
            menuItem.setIcon(R.drawable.msg_more_0_5);
            speedItem.setSubtext(LocaleController.getString("SpeedSlow", R.string.SpeedSlow));
        } else if (Math.abs(currentVideoSpeed - 1.0f) < 0.001f) {
            menuItem.setIcon(R.drawable.ic_ab_other);
            speedItem.setSubtext(LocaleController.getString("SpeedNormal", R.string.SpeedNormal));
        } else if (Math.abs(currentVideoSpeed - 1.5f) < 0.001f) {
            menuItem.setIcon(R.drawable.msg_more_1_5);
            speedItem.setSubtext(LocaleController.getString("SpeedFast", R.string.SpeedFast));
        } else {
            menuItem.setIcon(R.drawable.msg_more_2);
            speedItem.setSubtext(LocaleController.getString("SpeedVeryFast", R.string.SpeedVeryFast));
        }
    }

    private boolean checkInlinePermissions() {
        if (parentActivity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(parentActivity)) {
            return true;
        } else {
            AlertsCreator.createDrawOverlayPermissionDialog(parentActivity, null).show();
        }
        return false;
    }

    private void captureCurrentFrame() {
        if (captureFrameAtTime == -1 || videoTextureView == null) {
            return;
        }
        captureFrameAtTime = -1;
        Bitmap bitmap = videoTextureView.getBitmap();
        flashView.animate().alpha(1.0f).setInterpolator(CubicBezierInterpolator.EASE_BOTH).setDuration(85).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                photoCropView.setVideoThumb(bitmap, 0);
                flashAnimator = new AnimatorSet();
                flashAnimator.playTogether(ObjectAnimator.ofFloat(flashView, FLASH_VIEW_VALUE, 0.0f));
                flashAnimator.setDuration(85);
                flashAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                flashAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (flashAnimator == null) {
                            return;
                        }
                        AndroidUtilities.runOnUIThread(videoPlayRunnable = () -> {
                            manuallyPaused = false;
                            if (videoPlayer != null) {
                                videoPlayer.play();
                            }
                            videoPlayRunnable = null;
                        }, 860);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        flashAnimator = null;
                    }
                });
                flashAnimator.start();
            }
        }).start();
    }

    private TextView createCaptionTextView(LinkMovementMethod linkMovementMethod) {
        TextView textView = new SpoilersTextView(activityContext) {

            private boolean handleClicks;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    handleClicks = needCaptionLayout;
                }
                boolean b = super.onTouchEvent(event);
                return bottomTouchEnabled && b;
            }

            @Override
            public void scrollTo(int x, int y) {
                if (getParent().getParent() == pickerView) {
                    super.scrollTo(x, y);
                    handleClicks = false;
                }
            }

            @Override
            public boolean performClick() {
                return handleClicks && super.performClick();
            }

            @Override
            public void setPressed(boolean pressed) {
                final boolean needsRefresh = pressed != isPressed();
                super.setPressed(pressed);
                if (needsRefresh) {
                    invalidate();
                }
            }
        };
        textView.setMovementMethod(linkMovementMethod);
        ViewHelper.setPadding(textView, 16, 8, 16, 8);
        textView.setLinkTextColor(0xff76c2f1);
        textView.setTextColor(0xffffffff);
        textView.setHighlightColor(0x33ffffff);
        textView.setGravity(Gravity.CENTER_VERTICAL | LayoutHelper.getAbsoluteGravityStart());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setOnClickListener((v) -> openCaptionEnter());
        return textView;
    }

    private int getLeftInset() {
        if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
            return ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
        }
        return 0;
    }

	private int getRightInset() {
		if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
			return ((WindowInsets) lastInsets).getSystemWindowInsetRight();
		}
		return 0;
	}

    private void dismissInternal() {
        try {
            if (windowView.getParent() != null) {
                if (parentActivity instanceof LaunchActivity) {
                    ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(true);
                }
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(windowView);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void switchToPip(boolean fromGesture) {
        if (videoPlayer == null || !textureUploaded || !checkInlinePermissions() || changingTextureView || switchingInlineMode || isInline) {
            return;
        }
        if (PipInstance != null) {
            PipInstance.destroyPhotoViewer();
        }
        openedFullScreenVideo = false;
        PipInstance = Instance;
        Instance = null;
        switchingInlineMode = true;
        isVisible = false;
        AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
        if (currentPlaceObject != null && !currentPlaceObject.imageReceiver.getVisible()) {
            currentPlaceObject.imageReceiver.setVisible(true, true);
            AnimatedFileDrawable animation = currentPlaceObject.imageReceiver.getAnimation();
            if (animation != null) {
                Bitmap bitmap = animation.getAnimatedBitmap();
                if (bitmap != null) {
                    try {
                        Bitmap src = videoTextureView.getBitmap(bitmap.getWidth(), bitmap.getHeight());
                        Canvas canvas = new Canvas(bitmap);
                        canvas.drawBitmap(src, 0, 0, null);
                        src.recycle();
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                animation.seekTo(videoPlayer.getCurrentPosition(), true);
                if (fromGesture) {
                    currentPlaceObject.imageReceiver.setAlpha(0);
                    ImageReceiver imageReceiver = currentPlaceObject.imageReceiver;
                    ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                    valueAnimator.addUpdateListener((a) -> imageReceiver.setAlpha((Float) a.getAnimatedValue()));
                    valueAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            imageReceiver.setAlpha(1f);
                        }
                    });
                    valueAnimator.setDuration(250);
                    valueAnimator.start();
                }
                currentPlaceObject.imageReceiver.setAllowStartAnimation(true);
                currentPlaceObject.imageReceiver.startAnimation();
            }
        }
        if (Build.VERSION.SDK_INT >= 21) {
            pipAnimationInProgress = true;
            org.telegram.ui.Components.Rect rect = PipVideoView.getPipRect(aspectRatioFrameLayout.getAspectRatio());

            float scale = rect.width / videoTextureView.getWidth();

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            float fromX = videoTextureView.getTranslationX();
            float fromY = videoTextureView.getTranslationY() + translationY;
            float fromY2 = textureImageView.getTranslationY() + translationY;
            float toX = rect.x;
            float toX2 = rect.x - aspectRatioFrameLayout.getX() + getLeftInset();

            float toY = rect.y;
            float toY2 = rect.y - aspectRatioFrameLayout.getY();

            textureImageView.setTranslationY(fromY2);
            videoTextureView.setTranslationY(fromY);
            if (firstFrameView != null) {
                firstFrameView.setTranslationY(fromY);
            }

            translationY = 0;
            containerView.invalidate();

            CubicBezierInterpolator interpolator;
            if (fromGesture) {
                if (fromY < toY2) {
                    interpolator = new CubicBezierInterpolator(0.5, 0, 0.9, 0.9);
                } else {
                    interpolator = new CubicBezierInterpolator(0, 0.5, 0.9, 0.9);
                }
            } else {
                interpolator = null;
            }
            valueAnimator.addUpdateListener(animation -> {
                float xValue = (float) animation.getAnimatedValue();
                float yValue = interpolator == null ? xValue : interpolator.getInterpolation(xValue);
                textureImageView.setTranslationX(fromX * (1f - xValue) +  toX * xValue);
                textureImageView.setTranslationY(fromY2 * (1f - yValue) + toY * yValue);

                videoTextureView.setTranslationX(fromX * (1f - xValue) + (toX2) * xValue);
                videoTextureView.setTranslationY(fromY * (1f - yValue) + (toY2) * yValue);

                if (firstFrameView != null) {
                    firstFrameView.setTranslationX(videoTextureView.getTranslationX());
                    firstFrameView.setTranslationY(videoTextureView.getTranslationY());
                    firstFrameView.setScaleX(videoTextureView.getScaleX());
                    firstFrameView.setScaleY(videoTextureView.getScaleY());
                }
            });

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, scale),
                    ObjectAnimator.ofFloat(videoTextureView, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(videoTextureView, View.SCALE_Y, scale),
                    ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                    valueAnimator
            );
            if (fromGesture) {
                animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animatorSet.setDuration(300);
            } else {
                animatorSet.setInterpolator(new DecelerateInterpolator());
                animatorSet.setDuration(250);
            }
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pipAnimationInProgress = false;
                    switchToInlineRunnable.run();
                }
            });
            animatorSet.start();
            if (!fromGesture) {
                toggleActionBar(false, true, new ActionBarToggleParams().enableStatusBarAnimation(false).enableTranslationAnimation(false).animationDuration(250).animationInterpolator(new DecelerateInterpolator()));
            }
        } else {
            switchToInlineRunnable.run();
            dismissInternal();
        }
        if (parentChatActivity != null) {
            parentChatActivity.getFragmentView().invalidate();
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
        if (photoViewerWebView != null) {
            photoViewerWebView.exitFromPip();
        }
        Instance = PipInstance;
        PipInstance = null;
        if (photoViewerWebView == null) {
            switchingInlineMode = true;
            if (currentBitmap != null) {
                currentBitmap.recycle();
                currentBitmap = null;
            }
            changingTextureView = true;
        }

        isInline = false;

        if (photoViewerWebView == null && videoTextureView != null) {
            videoTextureView.setVisibility(View.INVISIBLE);
            aspectRatioFrameLayout.addView(videoTextureView);
        }

        if (ApplicationLoader.mainInterfacePaused) {
            try {
                parentActivity.startService(new Intent(ApplicationLoader.applicationContext, BringAppForegroundService.class));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        if (photoViewerWebView == null) {
            if (Build.VERSION.SDK_INT >= 21 && videoTextureView != null) {
                pipAnimationInProgress = true;
                org.telegram.ui.Components.Rect rect = PipVideoView.getPipRect(aspectRatioFrameLayout.getAspectRatio());

                float scale = rect.width / textureImageView.getLayoutParams().width;
                textureImageView.setScaleX(scale);
                textureImageView.setScaleY(scale);
                textureImageView.setTranslationX(rect.x);
                textureImageView.setTranslationY(rect.y);
                videoTextureView.setScaleX(scale);
                videoTextureView.setScaleY(scale);
                videoTextureView.setTranslationX(rect.x - aspectRatioFrameLayout.getX());
                videoTextureView.setTranslationY(rect.y - aspectRatioFrameLayout.getY());
                if (firstFrameView != null) {
                    firstFrameView.setScaleX(scale);
                    firstFrameView.setScaleY(scale);
                    firstFrameView.setTranslationX(videoTextureView.getTranslationX());
                    firstFrameView.setTranslationY(videoTextureView.getTranslationY());
                }
            } else {
                pipVideoView.close();
                pipVideoView = null;
            }
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

    private void updateVideoSeekPreviewPosition() {
        int x = videoPlayerSeekbar.getThumbX() + AndroidUtilities.dp(2) - videoPreviewFrame.getMeasuredWidth() / 2;
        int min = AndroidUtilities.dp(10);
        int max = videoPlayerControlFrameLayout.getMeasuredWidth() - AndroidUtilities.dp(10) - videoPreviewFrame.getMeasuredWidth() / 2;
        if (x < min) {
            x = min;
        } else if (x >= max) {
            x = max;
        }
        videoPreviewFrame.setTranslationX(x);
     }

    private void showVideoSeekPreviewPosition(boolean show) {
        if (show && videoPreviewFrame.getTag() != null || !show && videoPreviewFrame.getTag() == null) {
            return;
        }
        if (show && !videoPreviewFrame.isReady()) {
            needShowOnReady = true;
            return;
        }
        if (videoPreviewFrameAnimation != null) {
            videoPreviewFrameAnimation.cancel();
        }
        videoPreviewFrame.setTag(show ? 1 : null);
        videoPreviewFrameAnimation = new AnimatorSet();
        videoPreviewFrameAnimation.playTogether(ObjectAnimator.ofFloat(videoPreviewFrame, View.ALPHA, show ? 1.0f : 0.0f));
        videoPreviewFrameAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                videoPreviewFrameAnimation = null;
            }
        });
        videoPreviewFrameAnimation.setDuration(180);
        videoPreviewFrameAnimation.start();
    }

    private void createVideoControlsInterface() {
        videoPlayerControlFrameLayout = new VideoPlayerControlFrameLayout(containerView.getContext());
        containerView.addView(videoPlayerControlFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        final VideoPlayerSeekBar.SeekBarDelegate seekBarDelegate = new VideoPlayerSeekBar.SeekBarDelegate() {
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
                    showVideoSeekPreviewPosition(false);
                    needShowOnReady = false;
                }
            }

            @Override
            public void onSeekBarContinuousDrag(float progress) {
                if (videoPlayer != null && videoPreviewFrame != null) {
                    videoPreviewFrame.setProgress(progress, videoPlayerSeekbar.getWidth());
                }
                showVideoSeekPreviewPosition(true);
                updateVideoSeekPreviewPosition();
            }
        };

        final FloatSeekBarAccessibilityDelegate accessibilityDelegate = new FloatSeekBarAccessibilityDelegate() {
            @Override
            public float getProgress() {
                return videoPlayerSeekbar.getProgress();
            }

            @Override
            public void setProgress(float progress) {
                seekBarDelegate.onSeekBarDrag(progress);
                videoPlayerSeekbar.setProgress(progress);
                videoPlayerSeekbarView.invalidate();
            }

            @Override
            public String getContentDescription(View host) {
                final String time = LocaleController.formatPluralString("Minutes", videoPlayerCurrentTime[0]) + ' ' + LocaleController.formatPluralString("Seconds", videoPlayerCurrentTime[1]);
                final String totalTime = LocaleController.formatPluralString("Minutes", videoPlayerTotalTime[0]) + ' ' + LocaleController.formatPluralString("Seconds", videoPlayerTotalTime[1]);
                return LocaleController.formatString("AccDescrPlayerDuration", R.string.AccDescrPlayerDuration, time, totalTime);
            }
        };
        videoPlayerSeekbarView = new View(containerView.getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                videoPlayerSeekbar.draw(canvas, this);
            }
        };
        videoPlayerSeekbarView.setAccessibilityDelegate(accessibilityDelegate);
        videoPlayerSeekbarView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        videoPlayerControlFrameLayout.addView(videoPlayerSeekbarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        videoPlayerSeekbar = new VideoPlayerSeekBar(videoPlayerSeekbarView);
        videoPlayerSeekbar.setHorizontalPadding(AndroidUtilities.dp(2));
        videoPlayerSeekbar.setColors(0x33ffffff, 0x33ffffff, Color.WHITE, Color.WHITE, Color.WHITE, 0x59ffffff);
        videoPlayerSeekbar.setDelegate(seekBarDelegate);

        videoPreviewFrame = new VideoSeekPreviewImage(containerView.getContext(), () -> {
            if (needShowOnReady) {
                showVideoSeekPreviewPosition(true);
            }
        }) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateVideoSeekPreviewPosition();
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility == VISIBLE) {
                    updateVideoSeekPreviewPosition();
                }
            }
        };
        videoPreviewFrame.setAlpha(0.0f);
        containerView.addView(videoPreviewFrame, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48 + 10));

        videoPlayerTime = new SimpleTextView(containerView.getContext());
        videoPlayerTime.setTextColor(0xffffffff);
        videoPlayerTime.setGravity(Gravity.RIGHT | Gravity.TOP);
        videoPlayerTime.setTextSize(14);
        videoPlayerTime.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        videoPlayerControlFrameLayout.addView(videoPlayerTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 15, 12, 0));

        exitFullscreenButton = new ImageView(containerView.getContext());
        exitFullscreenButton.setImageResource(R.drawable.msg_minvideo);
        exitFullscreenButton.setContentDescription(LocaleController.getString("AccExitFullscreen", R.string.AccExitFullscreen));
        exitFullscreenButton.setScaleType(ImageView.ScaleType.CENTER);
        exitFullscreenButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        exitFullscreenButton.setVisibility(View.INVISIBLE);
        videoPlayerControlFrameLayout.addView(exitFullscreenButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
        exitFullscreenButton.setOnClickListener(v -> {
            if (parentActivity == null) {
                return;
            }
            wasRotated = false;
            fullscreenedByButton = 2;
            if (prevOrientation == -10) {
                prevOrientation = parentActivity.getRequestedOrientation();
            }
            parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });
    }

    private void openCaptionEnter() {
        if (imageMoveAnimation != null || changeModeAnimation != null || currentEditMode != 0 || sendPhotoType == SELECT_TYPE_AVATAR || sendPhotoType == SELECT_TYPE_WALLPAPER || sendPhotoType == SELECT_TYPE_QR) {
            return;
        }
        if (!windowView.isFocusable()) {
            makeFocusable();
        }
        keyboardAnimationEnabled = true;
        selectedPhotosListView.setEnabled(false);
        photosCounterView.setRotationX(0.0f);
        isPhotosListViewVisible = false;
        captionEditText.setTag(1);
        captionEditText.openKeyboard();
        captionEditText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        lastTitle = actionBar.getTitle();
        captionEditText.setVisibility(View.VISIBLE);
    }

    private int[] fixVideoWidthHeight(int w, int h) {
        int[] result = new int[]{w, h};
        if (Build.VERSION.SDK_INT >= 21) {
            MediaCodec encoder = null;
            try {
                encoder = MediaCodec.createEncoderByType(MediaController.VIDEO_MIME_TYPE);
                MediaCodecInfo.CodecCapabilities capabilities = encoder.getCodecInfo().getCapabilitiesForType(MediaController.VIDEO_MIME_TYPE);
                MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
                Range<Integer> widths = videoCapabilities.getSupportedWidths();
                Range<Integer> heights = videoCapabilities.getSupportedHeights();
                result[0] = Math.max(widths.getLower(), Math.round(w / 16.0f) * 16);
                result[1] = Math.max(heights.getLower(), Math.round(h / 16.0f) * 16);
            } catch (Exception ignore) {

            } finally {
                try {
                    if (encoder != null) {
                        encoder.release();
                    }
                } catch (Exception ignore) {

                }
            }
        }
        return result;
    }

    private VideoEditedInfo getCurrentVideoEditedInfo() {
        if (!isCurrentVideo && hasAnimatedMediaEntities() && centerImage.getBitmapWidth() > 0) {
            float maxSize = sendPhotoType == SELECT_TYPE_AVATAR ? 800 : 854;
            VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
            videoEditedInfo.start = videoEditedInfo.startTime = 0;
            videoEditedInfo.endTime = Math.min(3000, editState.averageDuration);
            while (videoEditedInfo.endTime > 0 && videoEditedInfo.endTime < 1000) {
                videoEditedInfo.endTime *= 2;
            }
            videoEditedInfo.end = videoEditedInfo.endTime;
            videoEditedInfo.rotationValue = 0;
            videoEditedInfo.originalPath = currentImagePath;
            videoEditedInfo.estimatedSize = (int) (videoEditedInfo.endTime / 1000.0f * 115200);
            videoEditedInfo.estimatedDuration = videoEditedInfo.endTime;
            videoEditedInfo.framerate = 30;
            videoEditedInfo.originalDuration = videoEditedInfo.endTime;
            videoEditedInfo.filterState = editState.savedFilterState;
            if (editState.croppedPaintPath != null) {
                videoEditedInfo.paintPath = editState.croppedPaintPath;
                videoEditedInfo.mediaEntities = editState.croppedMediaEntities != null && !editState.croppedMediaEntities.isEmpty() ? editState.croppedMediaEntities : null;
            } else {
                videoEditedInfo.paintPath = editState.paintPath;
                videoEditedInfo.mediaEntities = editState.mediaEntities;
            }
            videoEditedInfo.isPhoto = true;
            int width = centerImage.getBitmapWidth();
            int height = centerImage.getBitmapHeight();
            if (editState.cropState != null) {
                if (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270) {
                    int temp = width;
                    width = height;
                    height = temp;
                }
                width *= editState.cropState.cropPw;
                height *= editState.cropState.cropPh;
            }
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                width = height;
            }
            float scale = Math.max(width / maxSize, height / maxSize);
            if (scale < 1) {
                scale = 1;
            }
            width /= scale;
            height /= scale;
            if (width % 16 != 0) {
                width = Math.max(1, Math.round(width / 16.0f)) * 16;
            }
            if (height % 16 != 0) {
                height = Math.max(1, Math.round(height / 16.0f)) * 16;
            }
            videoEditedInfo.originalWidth = videoEditedInfo.resultWidth = width;
            videoEditedInfo.originalHeight = videoEditedInfo.resultHeight = height;
            videoEditedInfo.bitrate = -1;
            videoEditedInfo.muted = true;
            videoEditedInfo.avatarStartTime = 0;
            return videoEditedInfo;
        }
        if (!isCurrentVideo || currentPlayingVideoFile == null || compressionsCount == 0) {
            return null;
        }
        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = startTime;
        videoEditedInfo.endTime = endTime;
        videoEditedInfo.start = videoCutStart;
        videoEditedInfo.end = videoCutEnd;
        videoEditedInfo.rotationValue = rotationValue;
        videoEditedInfo.originalWidth = originalWidth;
        videoEditedInfo.originalHeight = originalHeight;
        videoEditedInfo.bitrate = bitrate;
        videoEditedInfo.originalPath = currentPathObject;
        videoEditedInfo.estimatedSize = estimatedSize != 0 ? estimatedSize : 1;
        videoEditedInfo.estimatedDuration = estimatedDuration;
        videoEditedInfo.framerate = videoFramerate;
        videoEditedInfo.originalDuration = (long) (videoDuration * 1000);
        videoEditedInfo.filterState = editState.savedFilterState;
        if (editState.croppedPaintPath != null) {
            videoEditedInfo.paintPath = editState.croppedPaintPath;
            videoEditedInfo.mediaEntities = editState.croppedMediaEntities != null && !editState.croppedMediaEntities.isEmpty() ? editState.croppedMediaEntities : null;
        } else {
            videoEditedInfo.paintPath = editState.paintPath;
            videoEditedInfo.mediaEntities = editState.mediaEntities != null && !editState.mediaEntities.isEmpty() ? editState.mediaEntities : null;
        }

        if (sendPhotoType != SELECT_TYPE_AVATAR && !muteVideo && (compressItem.getTag() == null || (videoEditedInfo.resultWidth == originalWidth && videoEditedInfo.resultHeight == originalHeight))) {
            videoEditedInfo.resultWidth = originalWidth;
            videoEditedInfo.resultHeight = originalHeight;
            videoEditedInfo.bitrate = muteVideo ? -1 : originalBitrate;
        } else {
            if (muteVideo || sendPhotoType == SELECT_TYPE_AVATAR) {
                selectedCompression = 1;
                updateWidthHeightBitrateForCompression();
            }
            videoEditedInfo.resultWidth = resultWidth;
            videoEditedInfo.resultHeight = resultHeight;
            videoEditedInfo.bitrate = muteVideo || sendPhotoType == SELECT_TYPE_AVATAR ? -1 : bitrate;
        }
        videoEditedInfo.cropState = editState.cropState;
        if (videoEditedInfo.cropState != null) {
            videoEditedInfo.rotationValue += videoEditedInfo.cropState.transformRotation;
            while (videoEditedInfo.rotationValue >= 360) {
                videoEditedInfo.rotationValue -= 360;
            }
            if (videoEditedInfo.rotationValue == 90 || videoEditedInfo.rotationValue == 270) {
                videoEditedInfo.cropState.transformWidth = (int) (videoEditedInfo.resultWidth * videoEditedInfo.cropState.cropPh);
                videoEditedInfo.cropState.transformHeight = (int) (videoEditedInfo.resultHeight * videoEditedInfo.cropState.cropPw);
            } else {
                videoEditedInfo.cropState.transformWidth = (int) (videoEditedInfo.resultWidth * videoEditedInfo.cropState.cropPw);
                videoEditedInfo.cropState.transformHeight = (int) (videoEditedInfo.resultHeight * videoEditedInfo.cropState.cropPh);
            }
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                if (videoEditedInfo.cropState.transformWidth > 800) {
                    videoEditedInfo.cropState.transformWidth = 800;
                }
                if (videoEditedInfo.cropState.transformHeight > 800) {
                    videoEditedInfo.cropState.transformHeight = 800;
                }
                videoEditedInfo.cropState.transformWidth = videoEditedInfo.cropState.transformHeight = Math.min(videoEditedInfo.cropState.transformWidth, videoEditedInfo.cropState.transformHeight);
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("original transformed w = " + videoEditedInfo.cropState.transformWidth + " h = " + videoEditedInfo.cropState.transformHeight + " r = " + videoEditedInfo.rotationValue);
            }
            int[] fixedSize = fixVideoWidthHeight(videoEditedInfo.cropState.transformWidth, videoEditedInfo.cropState.transformHeight);
            videoEditedInfo.cropState.transformWidth = fixedSize[0];
            videoEditedInfo.cropState.transformHeight = fixedSize[1];
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("fixed transformed w = " + videoEditedInfo.cropState.transformWidth + " h = " + videoEditedInfo.cropState.transformHeight);
            }
        }
        if (sendPhotoType == SELECT_TYPE_AVATAR) {
            videoEditedInfo.avatarStartTime = avatarStartTime;
            videoEditedInfo.originalBitrate = originalBitrate;
        }
        videoEditedInfo.muted = muteVideo || sendPhotoType == SELECT_TYPE_AVATAR;
        return videoEditedInfo;
    }

    private boolean supportsSendingNewEntities() {
        return parentChatActivity != null && (parentChatActivity.currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(parentChatActivity.currentEncryptedChat.layer) >= 101);
    }

    public boolean hasCaptionForAllMedia;
    public CharSequence captionForAllMedia;

    private void closeCaptionEnter(boolean apply) {
        if (currentIndex < 0 || currentIndex >= imagesArrLocals.size() || captionEditText.getTag() == null) {
            return;
        }
        Object object = imagesArrLocals.get(currentIndex);
        if (apply) {
            CharSequence caption = captionEditText.getFieldCharSequence();
            CharSequence[] result = new CharSequence[] {caption};

            if (hasCaptionForAllMedia && !TextUtils.equals(captionForAllMedia, caption) && placeProvider.getPhotoIndex(currentIndex) != 0 && placeProvider.getSelectedCount() > 0) {
                hasCaptionForAllMedia = false;
            }

            ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(result, supportsSendingNewEntities());
            captionForAllMedia = caption;

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
            if (placeProvider != null) {
                placeProvider.onApplyCaption(caption);
            }
            setCurrentCaption(null, result[0], false);
        }
        captionEditText.setTag(null);
        if (isCurrentVideo) {
            actionBar.setTitleAnimated(lastTitle, false, 220);
            actionBar.setSubtitle(muteVideo ? LocaleController.getString("SoundMuted", R.string.SoundMuted) : currentSubtitle);
        }
        updateCaptionTextForCurrentPhoto(object);
        if (captionEditText.isPopupShowing()) {
            captionEditText.hidePopup();
        }
        captionEditText.closeKeyboard();
        if (Build.VERSION.SDK_INT >= 19) {
            captionEditText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    private void updateVideoPlayerTime() {
        Arrays.fill(videoPlayerCurrentTime, 0);
        Arrays.fill(videoPlayerTotalTime, 0);
        if (videoPlayer != null) {
            long current = Math.max(0, videoPlayer.getCurrentPosition());
            if (shownControlsByEnd && !actionBarWasShownBeforeByEnd) {
                current = 0;
            }
            long total = Math.max(0, videoPlayer.getDuration());
            if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                total *= (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress());
                current -= videoTimelineView.getLeftProgress() * total;
                if (current > total) {
                    current = total;
                }
            }
            current /= 1000;
            total /= 1000;
            videoPlayerCurrentTime[0] = (int) (current / 60);
            videoPlayerCurrentTime[1] = (int) (current % 60);
            videoPlayerTotalTime[0] = (int) (total / 60);
            videoPlayerTotalTime[1] = (int) (total % 60);
        }
        videoPlayerTime.setText(String.format(Locale.ROOT, "%02d:%02d / %02d:%02d", videoPlayerCurrentTime[0], videoPlayerCurrentTime[1], videoPlayerTotalTime[0], videoPlayerTotalTime[1]));
    }

    private void checkBufferedProgress(float progress) {
        if (!isStreaming || parentActivity == null || streamingAlertShown || videoPlayer == null || currentMessageObject == null) {
            return;
        }
        TLRPC.Document document = currentMessageObject.getDocument();
        if (document == null) {
            return;
        }
        int innerDuration = currentMessageObject.getDuration();
        if (innerDuration < 20) {
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

    public void updateColors() {
        int color = getThemedColor(Theme.key_dialogFloatingButton);
        if (pickerViewSendButton != null) {
            Drawable drawable = pickerViewSendButton.getBackground();
            Theme.setSelectorDrawableColor(drawable, color, false);
            Theme.setSelectorDrawableColor(drawable, getThemedColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton), true);
            pickerViewSendButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        }
        if (checkImageView != null) {
            checkImageView.setColor(getThemedColor(Theme.key_dialogFloatingButton), 0xffffffff);
        }
        PorterDuffColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        if (timeItem != null && timeItem.getColorFilter() != null) {
            timeItem.setColorFilter(filter);
        }
        if (paintItem != null && paintItem.getColorFilter() != null) {
            paintItem.setColorFilter(filter);
        }
        if (cropItem != null && cropItem.getColorFilter() != null) {
            cropItem.setColorFilter(filter);
        }
        if (tuneItem != null && tuneItem.getColorFilter() != null) {
            tuneItem.setColorFilter(filter);
        }
        if (rotateItem != null && rotateItem.getColorFilter() != null) {
            rotateItem.setColorFilter(filter);
        }
        if (mirrorItem != null && mirrorItem.getColorFilter() != null) {
            mirrorItem.setColorFilter(filter);
        }
        if (editorDoneLayout != null) {
            editorDoneLayout.doneButton.setTextColor(color);
        }
        if (qualityPicker != null) {
            qualityPicker.doneButton.setTextColor(color);
        }
        if (photoPaintView != null) {
            photoPaintView.updateColors();
        }
        if (photoFilterView != null) {
            photoFilterView.updateColors();
        }
        if (captionEditText != null) {
            captionEditText.updateColors();
        }
        if (videoTimelineView != null) {
            videoTimelineView.invalidate();
        }
        if (selectedPhotosListView != null) {
            int count = selectedPhotosListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = selectedPhotosListView.getChildAt(a);
                if (view instanceof PhotoPickerPhotoCell) {
                    ((PhotoPickerPhotoCell) view).updateColors();
                }
            }
        }
        if (masksAlert != null) {
            masksAlert.updateColors(true);
        }
    }

    public void injectVideoPlayer(VideoPlayer player) {
        injectingVideoPlayer = player;
    }

    public void injectVideoPlayerSurface(SurfaceTexture surface) {
        injectingVideoPlayerSurface = surface;
    }

    public boolean isInjectingVideoPlayer() {
        return injectingVideoPlayer != null;
    }

    private void scheduleActionBarHide() {
        scheduleActionBarHide(3000);
    }

    private void scheduleActionBarHide(int delay) {
        if (!isAccessibilityEnabled()) {
            AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
            AndroidUtilities.runOnUIThread(hideActionBarRunnable, delay);
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager) activityContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
            return am.isEnabled() && am.isTouchExplorationEnabled();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private void updatePlayerState(boolean playWhenReady, int playbackState) {
        if (videoPlayer == null) {
            return;
        }
        if (isStreaming) {
            if (playbackState == ExoPlayer.STATE_BUFFERING && skipFirstBufferingProgress) {
                if (playWhenReady) {
                    skipFirstBufferingProgress = false;
                }
            } else {
                final boolean buffering = seekToProgressPending != 0 || playbackState == ExoPlayer.STATE_BUFFERING;
                if (buffering) {
                    AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
                } else {
                    scheduleActionBarHide();
                }
                toggleMiniProgress(buffering, true);
            }
        }
        if (aspectRatioFrameLayout != null) {
            aspectRatioFrameLayout.setKeepScreenOn(playWhenReady && (playbackState != ExoPlayer.STATE_ENDED && playbackState != ExoPlayer.STATE_IDLE));
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
        if (playbackState == ExoPlayer.STATE_READY || playbackState == ExoPlayer.STATE_IDLE) {
            if (currentMessageObject != null) {
                videoPreviewFrame.open(videoPlayer.getCurrentUri());
            }
            if (seekToProgressPending != 0) {
                int seekTo = (int) (videoPlayer.getDuration() * seekToProgressPending);
                videoPlayer.seekTo(seekTo);
                seekToProgressPending = 0;
                if (currentMessageObject != null && !FileLoader.getInstance(currentMessageObject.currentAccount).isLoadingVideoAny(currentMessageObject.getDocument())) {
                    skipFirstBufferingProgress = true;
                }
            }
        }
        if (playbackState == ExoPlayer.STATE_READY) {
            if (aspectRatioFrameLayout.getVisibility() != View.VISIBLE) {
                aspectRatioFrameLayout.setVisibility(View.VISIBLE);
            }
            if (!pipItem.isEnabled() && pipItem.getVisibility() == View.VISIBLE) {
                pipAvailable = true;
                pipItem.setEnabled(true);
                pipItem.animate().alpha(1.0f).setDuration(175).withEndAction(null).start();
            }
            playerWasReady = true;
            if (currentMessageObject != null && currentMessageObject.isVideo()) {
                AndroidUtilities.cancelRunOnUIThread(setLoadingRunnable);
                FileLoader.getInstance(currentMessageObject.currentAccount).removeLoadingVideo(currentMessageObject.getDocument(), true, false);
            }
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            if (playWhenReady && currentMessageObject != null && currentMessageObject.isVideo()) {
                if (playerWasReady) {
                    setLoadingRunnable.run();
                } else {
                    AndroidUtilities.runOnUIThread(setLoadingRunnable, 1000);
                }
            }
        }
        if (videoPlayer.isPlaying() && playbackState != ExoPlayer.STATE_ENDED) {
            if (!isPlaying) {
                isPlaying = true;
                photoProgressViews[0].setBackgroundState(isCurrentVideo ? PROGRESS_NONE : PROGRESS_PAUSE, false, true);
                photoProgressViews[0].setIndexedAlpha(1, !isCurrentVideo && (!isAccessibilityEnabled() || playerWasPlaying) && ((playerAutoStarted && !playerWasPlaying) || !isActionBarVisible) ? 0f : 1f, false);
                playerWasPlaying = true;
                AndroidUtilities.runOnUIThread(updateProgressRunnable);
            }
        } else if (isPlaying || playbackState == ExoPlayer.STATE_ENDED) {
            if (currentEditMode != 3) {
                photoProgressViews[0].setIndexedAlpha(1, 1f, playbackState == ExoPlayer.STATE_ENDED);
                photoProgressViews[0].setBackgroundState(PROGRESS_PLAY, false, photoProgressViews[0].animAlphas[1] > 0f);
            }
            isPlaying = false;
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
            if (playbackState == ExoPlayer.STATE_ENDED) {
                if (isCurrentVideo) {
                    if (!videoTimelineView.isDragging()) {
                        videoTimelineView.setProgress(videoTimelineView.getLeftProgress());
                        if (!inPreview && (currentEditMode != 0 || videoTimelineView.getVisibility() == View.VISIBLE)) {
                            videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                        } else {
                            videoPlayer.seekTo(0);
                        }
                        manuallyPaused = false;
                        cancelVideoPlayRunnable();
                        if (sendPhotoType != SELECT_TYPE_AVATAR && currentEditMode == 0 && switchingToMode <= 0) {
                            videoPlayer.pause();
                        } else {
                            videoPlayer.play();
                        }
                        containerView.invalidate();
                    }
                } else {
                    videoPlayerSeekbar.setProgress(0.0f);
                    videoPlayerSeekbarView.invalidate();
                    if (!inPreview && videoTimelineView.getVisibility() == View.VISIBLE) {
                        videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                    } else {
                        videoPlayer.seekTo(0);
                    }
                    manuallyPaused = false;
                    videoPlayer.pause();
                    if (!isActionBarVisible) {
                        toggleActionBar(true, true);
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

    private void preparePlayer(Uri uri, boolean playWhenReady, boolean preview) {
        preparePlayer(uri, playWhenReady, preview, null);
    }

    private void preparePlayer(Uri uri, boolean playWhenReady, boolean preview, MediaController.SavedFilterState savedFilterState) {
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
        firstAnimationDelay = true;
        inPreview = preview;
        releasePlayer(false);
        if (imagesArrLocals.isEmpty()) {
            createVideoTextureView(null);
        }
        if (Build.VERSION.SDK_INT >= 21 && textureImageView == null) {
            textureImageView = new ImageView(parentActivity);
            textureImageView.setBackgroundColor(0xffff0000);
            textureImageView.setPivotX(0);
            textureImageView.setPivotY(0);
            textureImageView.setVisibility(View.INVISIBLE);
            containerView.addView(textureImageView);
        }
        checkFullscreenButton();

        if (orientationEventListener == null) {
            orientationEventListener = new OrientationEventListener(ApplicationLoader.applicationContext) {
                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientationEventListener == null || aspectRatioFrameLayout == null || aspectRatioFrameLayout.getVisibility() != View.VISIBLE) {
                        return;
                    }
                    if (parentActivity != null && fullscreenedByButton != 0) {
                        if (fullscreenedByButton == 1) {
                            if (orientation >= 270 - 30 && orientation <= 270 + 30) {
                                wasRotated = true;
                            } else if (wasRotated && orientation > 0 && (orientation >= 330 || orientation <= 30)) {
                                parentActivity.setRequestedOrientation(prevOrientation);
                                fullscreenedByButton = 0;
                                wasRotated = false;
                            }
                        } else {
                            if (orientation > 0 && (orientation >= 330 || orientation <= 30)) {
                                wasRotated = true;
                            } else if (wasRotated && orientation >= 270 - 30 && orientation <= 270 + 30) {
                                parentActivity.setRequestedOrientation(prevOrientation);
                                fullscreenedByButton = 0;
                                wasRotated = false;
                            }
                        }
                    }
                }
            };

            if (orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable();
            } else {
                orientationEventListener.disable();
                orientationEventListener = null;
            }
        }

        textureUploaded = false;
        videoSizeSet = false;
        videoCrossfadeStarted = false;
        boolean newPlayerCreated = false;
        playerWasReady = false;
        playerWasPlaying = false;
        captureFrameReadyAtTime = -1;
        captureFrameAtTime = -1;
        needCaptureFrameReadyAtTime = -1;
        if (videoPlayer == null) {
            if (injectingVideoPlayer != null) {
                videoPlayer = injectingVideoPlayer;
                injectingVideoPlayer = null;
                playerInjected = true;
                updatePlayerState(videoPlayer.getPlayWhenReady(), videoPlayer.getPlaybackState());
            } else {
                videoPlayer = new VideoPlayer() {
                    @Override
                    public void play() {
                        super.play();
                        playOrStopAnimatedStickers(true);
                    }

                    @Override
                    public void pause() {
                        super.pause();
                        if (currentEditMode == 0) {
                            playOrStopAnimatedStickers(false);
                        }
                    }

                    @Override
                    public void seekTo(long positionMs) {
                        super.seekTo(positionMs);
                        if (isCurrentVideo) {
                            seekAnimatedStickersTo(positionMs);
                        }
                    }
                };
                newPlayerCreated = true;
            }
            if (videoTextureView != null) {
                videoPlayer.setTextureView(videoTextureView);
            }
            firstFrameSet = false;
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    updatePlayerState(playWhenReady, playbackState);
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {
                    if (videoPlayer != player) {
                        return;
                    }
                    FileLog.e(e);
                    if (!menuItem.isSubItemVisible(gallery_menu_openin)) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("CantPlayVideo", R.string.CantPlayVideo));
                    builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), (dialog, which) -> {
                        try {
                            AndroidUtilities.openForView(currentMessageObject, parentActivity, resourcesProvider);
                            closePhoto(false, false);
                        } catch (Exception e1) {
                            FileLog.e(e1);
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
                        if (videoTextureView instanceof VideoEditTextureView) {
                            ((VideoEditTextureView) videoTextureView).setVideoSize((int) (width * pixelWidthHeightRatio), height);
                            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                                setCropBitmap();
                            }
                        }
                        videoSizeSet = true;
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    if (!textureUploaded) {
                        textureUploaded = true;
                        containerView.invalidate();
                    }

                    if (videoTextureView != null && !firstFrameSet && videoPlayer.getCurrentPosition() <= 60) {
                        firstFrameView.setImageBitmap(videoTextureView.getBitmap());
                        firstFrameSet = true;
                    }
                }

                @Override
                public void onRenderedFirstFrame(AnalyticsListener.EventTime eventTime) {
                    if (eventTime.eventPlaybackPositionMs == needCaptureFrameReadyAtTime) {
                        captureFrameReadyAtTime = eventTime.eventPlaybackPositionMs;
                        needCaptureFrameReadyAtTime = -1;
                        captureCurrentFrame();
                    }
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    if (changingTextureView) {
                        changingTextureView = false;
                        if (isInline) {
                            waitingForFirstTextureUpload = 1;
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
                            //pipPosition[0] -= getLeftInset();
                            pipPosition[1] -= containerView.getTranslationY();
                            textureImageView.setTranslationX(textureImageView.getTranslationX() + getLeftInset());
                            videoTextureView.setTranslationX(videoTextureView.getTranslationX() + getLeftInset() - aspectRatioFrameLayout.getX());
                            if (firstFrameView != null) {
                                firstFrameView.setTranslationX(videoTextureView.getTranslationX());
                            }

                            AnimatorSet animatorSet = new AnimatorSet();
                            ArrayList<Animator> animators = new ArrayList<>();
                            animators.add(ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, pipPosition[0]));
                            animators.add(ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, pipPosition[1]));
                            animators.add(ObjectAnimator.ofFloat(videoTextureView, View.SCALE_X, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(videoTextureView, View.SCALE_Y, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(videoTextureView, View.TRANSLATION_X, pipPosition[0] - aspectRatioFrameLayout.getX()));
                            animators.add(ObjectAnimator.ofFloat(videoTextureView, View.TRANSLATION_Y, pipPosition[1] - aspectRatioFrameLayout.getY()));
                            animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 255));
                            if (firstFrameView != null) {
                                animators.add(ObjectAnimator.ofFloat(firstFrameView, View.SCALE_X, 1.0f));
                                animators.add(ObjectAnimator.ofFloat(firstFrameView, View.SCALE_Y, 1.0f));
                                animators.add(ObjectAnimator.ofFloat(firstFrameView, View.TRANSLATION_X, pipPosition[0] - aspectRatioFrameLayout.getX()));
                                animators.add(ObjectAnimator.ofFloat(firstFrameView, View.TRANSLATION_Y, pipPosition[1] - aspectRatioFrameLayout.getY()));
                            }
                            animatorSet.playTogether(animators);
                            final DecelerateInterpolator interpolator = new DecelerateInterpolator();
                            animatorSet.setInterpolator(interpolator);
                            animatorSet.setDuration(250);
                            animatorSet.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    pipAnimationInProgress = false;
                                }
                            });
                            animatorSet.start();
                            toggleActionBar(true, true, new ActionBarToggleParams().enableStatusBarAnimation(false).enableTranslationAnimation(false).animationDuration(250).animationInterpolator(interpolator));
                        } else {
                            toggleActionBar(true, false);
                            //containerView.setTranslationY(0);
                        }

                        waitingForFirstTextureUpload = 0;
                    }
                }
            });
        }
        if (!imagesArrLocals.isEmpty()) {
            createVideoTextureView(savedFilterState);
        }
        videoTextureView.setAlpha(videoCrossfadeAlpha = 0.0f);
        if (paintingOverlay != null) {
            paintingOverlay.setAlpha(videoCrossfadeAlpha);
        }

        shouldSavePositionForCurrentVideo = null;
        shouldSavePositionForCurrentVideoShortTerm = null;
        lastSaveTime = 0;
        if (newPlayerCreated) {
            seekToProgressPending = seekToProgressPending2;
            videoPlayerSeekbar.setProgress(0);
            videoTimelineView.setProgress(0);
            videoPlayerSeekbar.setBufferedProgress(0);

            if (currentMessageObject != null) {
                final int duration = currentMessageObject.getDuration();
                final String name = currentMessageObject.getFileName();
                if (!TextUtils.isEmpty(name)) {
                    if (duration >= 10 * 60) {
                        if (currentMessageObject.forceSeekTo < 0) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
                            float pos = preferences.getFloat(name, -1);
                            if (pos > 0 && pos < 0.999f) {
                                currentMessageObject.forceSeekTo = pos;
                                videoPlayerSeekbar.setProgress(pos);
                            }
                        }
                        shouldSavePositionForCurrentVideo = name;
                    } else if (duration >= 10) {
                        SavedVideoPosition videoPosition = null;
                        for (int i = savedVideoPositions.size() - 1; i >= 0; i--) {
                            final SavedVideoPosition item = savedVideoPositions.valueAt(i);
                            if (item.timestamp < SystemClock.elapsedRealtime() - 5 * 1000) {
                                savedVideoPositions.removeAt(i);
                            } else if (videoPosition == null && savedVideoPositions.keyAt(i).equals(name)) {
                                videoPosition = item;
                            }
                        }
                        if (currentMessageObject.forceSeekTo < 0 && videoPosition != null) {
                            float pos = videoPosition.position;
                            if (pos > 0 && pos < 0.999f) {
                                currentMessageObject.forceSeekTo = pos;
                                videoPlayerSeekbar.setProgress(pos);
                            }
                        }
                        shouldSavePositionForCurrentVideoShortTerm = name;
                    }
                }
            }

            videoPlayer.preparePlayer(uri, "other");
            videoPlayer.setPlayWhenReady(playWhenReady);
        }

        playerLooping = currentMessageObject != null && currentMessageObject.getDuration() <= 30;
        videoPlayerControlFrameLayout.setSeekBarTransitionEnabled(playerLooping);
        videoPlayer.setLooping(playerLooping);

        if (currentMessageObject != null && currentMessageObject.forceSeekTo >= 0) {
            seekToProgressPending = currentMessageObject.forceSeekTo;
            currentMessageObject.forceSeekTo = -1;
        }

        if (currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document))) {
            bottomLayout.setVisibility(View.VISIBLE);
            bottomLayout.setPadding(0, 0, AndroidUtilities.dp(84), 0);
            pickerView.setVisibility(View.GONE);
        } else {
            bottomLayout.setPadding(0, 0, 0, 0);
        }

        if (pageBlocksAdapter != null) {
            bottomLayout.setVisibility(View.VISIBLE);
        }

        setVideoPlayerControlVisible(!isCurrentVideo, true);
        if (!isCurrentVideo) {
            scheduleActionBarHide(playerAutoStarted ? 3000 : 1000);
        }
        if (currentMessageObject != null) {
            videoPlayer.setPlaybackSpeed(currentVideoSpeed);
        }

        inPreview = preview;
    }

    private void checkFullscreenButton() {
        if (imagesArr.isEmpty()) {
            for (int b = 0; b < 3; b++) {
                fullscreenButton[b].setVisibility(View.INVISIBLE);
            }
            return;
        }
        for (int b = 0; b < 3; b++) {
            int index = currentIndex;
            if (b == 1) {
                index += 1;
            } else if (b == 2) {
                index -= 1;
            }
            if (index < 0 || index >= imagesArr.size()) {
                fullscreenButton[b].setVisibility(View.INVISIBLE);
                continue;
            }
            MessageObject messageObject = imagesArr.get(index);
            if (!messageObject.isVideo()) {
                fullscreenButton[b].setVisibility(View.INVISIBLE);
                continue;
            }
            int w = b == 0 && videoTextureView != null ? videoTextureView.getMeasuredWidth() : 0;
            int h = b == 0 && videoTextureView != null ? videoTextureView.getMeasuredHeight() : 0;
            TLRPC.Document document = messageObject.getDocument();
            for (int a = 0, N = document.attributes.size(); a < N; a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    w = attribute.w;
                    h = attribute.h;
                    break;
                }
            }
            if (AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x && !(videoTextureView instanceof VideoEditTextureView) && w > h) {
                if (fullscreenButton[b].getVisibility() != View.VISIBLE) {
                    fullscreenButton[b].setVisibility(View.VISIBLE);
                }
                float scale = w / (float) containerView.getMeasuredWidth();
                int height = (int) (h / scale);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fullscreenButton[b].getLayoutParams();
                layoutParams.topMargin = (containerView.getMeasuredHeight() + height) / 2 - AndroidUtilities.dp(48);
            } else {
                if (fullscreenButton[b].getVisibility() != View.INVISIBLE) {
                    fullscreenButton[b].setVisibility(View.INVISIBLE);
                }
            }

            float currentTranslationX;
            if (imageMoveAnimation != null) {
                currentTranslationX = translationX + (animateToX - translationX) * animationValue;
            } else {
                currentTranslationX = translationX;
            }
            float offsetX;
            if (b == 1) {
                offsetX = 0;
            } else if (b == 2) {
                offsetX = -AndroidUtilities.displaySize.x - AndroidUtilities.dp(15) + (currentTranslationX - maxX);
            } else {
                offsetX = currentTranslationX < minX ? (currentTranslationX - minX) : 0;
            }
            fullscreenButton[b].setTranslationX(offsetX + AndroidUtilities.displaySize.x - AndroidUtilities.dp(48));
        }
    }

    private void createVideoTextureView(MediaController.SavedFilterState savedFilterState) {
        if (videoTextureView != null) {
            return;
        }
        aspectRatioFrameLayout = new AspectRatioFrameLayout(parentActivity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (textureImageView != null) {
                    ViewGroup.LayoutParams layoutParams = textureImageView.getLayoutParams();
                    layoutParams.width = getMeasuredWidth();
                    layoutParams.height = getMeasuredHeight();
                }
                if (videoTextureView instanceof VideoEditTextureView) {
                    videoTextureView.setPivotX(videoTextureView.getMeasuredWidth() / 2);
                    firstFrameView.setPivotX(videoTextureView.getMeasuredWidth() / 2);
                } else {
                    videoTextureView.setPivotX(0);
                    firstFrameView.setPivotX(0);
                }
                checkFullscreenButton();
            }
        };
        aspectRatioFrameLayout.setWillNotDraw(false);
        aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
        containerView.addView(aspectRatioFrameLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        if (imagesArrLocals.isEmpty()) {
            videoTextureView = new TextureView(parentActivity);
        } else {
            VideoEditTextureView videoEditTextureView = new VideoEditTextureView(parentActivity, videoPlayer);
            if (savedFilterState != null) {
                videoEditTextureView.setDelegate(thread -> thread.setFilterGLThreadDelegate(FilterShaders.getFilterShadersDelegate(savedFilterState)));
            }
            videoTextureView = videoEditTextureView;
        }
        if (injectingVideoPlayerSurface != null) {
            videoTextureView.setSurfaceTexture(injectingVideoPlayerSurface);
            textureUploaded = true;
            videoSizeSet = true;
            injectingVideoPlayerSurface = null;
        }
        videoTextureView.setPivotX(0);
        videoTextureView.setPivotY(0);
        videoTextureView.setOpaque(false);
        aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        firstFrameView = new ImageView(parentActivity);
        firstFrameView.setPivotX(0);
        firstFrameView.setPivotY(0);
        firstFrameView.setScaleType(ImageView.ScaleType.FIT_XY);
        aspectRatioFrameLayout.addView(firstFrameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        if (sendPhotoType == SELECT_TYPE_AVATAR) {
            flashView = new View(parentActivity);
            flashView.setBackgroundColor(0xffffffff);
            flashView.setAlpha(0.0f);
            aspectRatioFrameLayout.addView(flashView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }
    }

    private final static float firstFrameFadeDuration = 200;
    private boolean shownControlsByEnd = false;
    private boolean actionBarWasShownBeforeByEnd = false;
    private ViewPropertyAnimator firstFrameViewAnimator = null;
    private void updateFirstFrameView() {
        if (videoPlayer != null && firstFrameView != null) {
            long toDuration = videoPlayer.getDuration() - videoPlayer.getCurrentPosition();
            float alpha = 1f - Math.max(Math.min(toDuration / firstFrameFadeDuration, 1), 0);
            if (!videoPlayer.isPlaying()) {
                if (firstFrameViewAnimator != null) {
                    firstFrameViewAnimator.cancel();
                    firstFrameViewAnimator = null;
                }
                firstFrameView.clearAnimation();
                firstFrameView.setAlpha(alpha);
            } else {
                if (toDuration <= firstFrameFadeDuration) {
                    if (firstFrameViewAnimator == null) {
                        firstFrameView.setAlpha(alpha);
                        firstFrameViewAnimator = firstFrameView.animate().alpha(1).withEndAction(() -> {
                            if (firstFrameViewAnimator != null) {
                                firstFrameViewAnimator.cancel();
                                firstFrameViewAnimator = null;
                            }
                        }).setDuration(Math.max(0, toDuration));
                        firstFrameViewAnimator.start();
                    }
                } else {
                    firstFrameView.setAlpha(alpha);
                }
            }
            if (!videoPlayer.isLooping()) {
                if (!shownControlsByEnd && videoPlayer.getCurrentPosition() > videoPlayer.getDuration() - firstFrameFadeDuration) {
                    actionBarWasShownBeforeByEnd = isActionBarVisible;
                    shownControlsByEnd = true;
                    toggleActionBar(true, true);
                    checkProgress(0, false, false);
                } else if (videoPlayer.getCurrentPosition() < videoPlayer.getDuration() - firstFrameFadeDuration) {
                    shownControlsByEnd = false;
                    actionBarWasShownBeforeByEnd = false;
                }
            }
        }
    }

    private void releasePlayer(boolean onClose) {
        if (videoPlayer != null) {
            cancelVideoPlayRunnable();
            AndroidUtilities.cancelRunOnUIThread(setLoadingRunnable);
            AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
            if (shouldSavePositionForCurrentVideoShortTerm != null) {
                final float progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                savedVideoPositions.put(shouldSavePositionForCurrentVideoShortTerm, new SavedVideoPosition(progress, SystemClock.elapsedRealtime()));
            }
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        } else {
            playerWasPlaying = false;
        }

        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }

        videoPreviewFrame.close();
        toggleMiniProgress(false, false);
        pipAvailable = false;
        playerInjected = false;
        if (pipItem.isEnabled()) {
            pipItem.setEnabled(false);
            pipItem.animate().alpha(0.5f).setDuration(175).withEndAction(null).start();
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
            try {
                containerView.removeView(aspectRatioFrameLayout);
            } catch (Throwable ignore) {

            }
            aspectRatioFrameLayout = null;
        }
        cancelFlashAnimations();
        flashView = null;
        if (videoTextureView != null) {
            if (videoTextureView instanceof VideoEditTextureView) {
                ((VideoEditTextureView) videoTextureView).release();
            }
            videoTextureView = null;
        }
        if (isPlaying) {
            isPlaying = false;
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
        }
        if (!onClose && !inPreview && !requestingPreview) {
            setVideoPlayerControlVisible(false, true);
        }
        photoProgressViews[0].resetAlphas();
    }

    private void setVideoPlayerControlVisible(boolean visible, boolean animated) {
        if (videoPlayerControlVisible != visible) {
            if (videoPlayerControlAnimator != null) {
                videoPlayerControlAnimator.cancel();
            }
            videoPlayerControlVisible = visible;
            if (animated) {
                if (visible) {
                    videoPlayerControlFrameLayout.setVisibility(View.VISIBLE);
                } else {
                    dateTextView.setVisibility(View.VISIBLE);
                    nameTextView.setVisibility(View.VISIBLE);
                    if (allowShare) {
                        bottomButtonsLayout.setVisibility(View.VISIBLE);
                    }
                }
                final boolean shareWasAllowed = allowShare;
                final ValueAnimator anim = ValueAnimator.ofFloat(videoPlayerControlFrameLayout.getAlpha(), visible ? 1f : 0f);
                anim.setDuration(200);
                anim.addUpdateListener(a -> {
                    final float alpha = (float) a.getAnimatedValue();
                    videoPlayerControlFrameLayout.setAlpha(alpha);
                    dateTextView.setAlpha(1f - alpha);
                    nameTextView.setAlpha(1f - alpha);
                    if (shareWasAllowed) {
                        bottomButtonsLayout.setAlpha(1f - alpha);
                    }
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!visible) {
                            videoPlayerControlFrameLayout.setVisibility(View.GONE);
                        } else {
                            dateTextView.setVisibility(View.GONE);
                            nameTextView.setVisibility(View.GONE);
                            if (shareWasAllowed) {
                                bottomButtonsLayout.setVisibility(View.GONE);
                            }
                        }
                    }
                });
                videoPlayerControlAnimator = anim;
                anim.start();
            } else {
                videoPlayerControlFrameLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
                videoPlayerControlFrameLayout.setAlpha(visible ? 1f : 0f);
                dateTextView.setVisibility(visible ? View.GONE : View.VISIBLE);
                dateTextView.setAlpha(visible ? 0f : 1f);
                nameTextView.setVisibility(visible ? View.GONE : View.VISIBLE);
                nameTextView.setAlpha(visible ? 0f : 1f);
                if (allowShare) {
                    bottomButtonsLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
                    bottomButtonsLayout.setAlpha(visible ? 0f : 1f);
                }
            }
            if (allowShare && pageBlocksAdapter == null) {
                if (visible) {
                    menuItem.showSubItem(gallery_menu_share);
                } else {
                    menuItem.hideSubItem(gallery_menu_share);
                }
            }
        }
    }

    private void updateCaptionTextForCurrentPhoto(Object object) {
        CharSequence caption = null;
        if (hasCaptionForAllMedia) {
            caption = captionForAllMedia;
        } else if (object instanceof MediaController.PhotoEntry) {
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
        captionEditText.setAllowTextEntitiesIntersection(supportsSendingNewEntities());
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
            visibleDialog.setOnDismissListener(dialog -> visibleDialog = null);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static final int thumbSize = 512;

    private void mergeImages(String finalPath, String thumbPath, Bitmap thumb, Bitmap bitmap, float size, boolean reverse) {
        try {
            boolean recycle = false;
            if (thumb == null) {
                thumb = BitmapFactory.decodeFile(thumbPath);
                recycle = true;
            }
            int w = thumb.getWidth();
            int h = thumb.getHeight();
            if (w > size || h > size) {
                float scale = Math.max(w, h) / size;
                w /= scale;
                h /= scale;
            }
            Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dst);
            Rect dstRect = new Rect(0, 0, w, h);
            if (reverse) {
                canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint);
                canvas.drawBitmap(thumb, null, dstRect, bitmapPaint);
            } else {
                canvas.drawBitmap(thumb, null, dstRect, bitmapPaint);
                canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint);
            }
            FileOutputStream stream = new FileOutputStream(new File(finalPath));
            dst.compress(Bitmap.CompressFormat.JPEG, size == thumbSize ? 83 : 87, stream);
            try {
                stream.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (recycle) {
                thumb.recycle();
            }
            dst.recycle();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void seekAnimatedStickersTo(long ms) {
        if (editState.mediaEntities != null) {
            for (int a = 0, N = editState.mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = editState.mediaEntities.get(a);
                if (entity.type == 0 && (entity.subType & 1) != 0 && entity.view instanceof BackupImageView) {
                    ImageReceiver imageReceiver = ((BackupImageView) entity.view).getImageReceiver();
                    RLottieDrawable drawable = imageReceiver.getLottieAnimation();
                    if (drawable == null) {
                        continue;
                    }
                    drawable.setProgressMs(ms - (startTime > 0 ? startTime / 1000 : 0));
                }
            }
        }
    }

    private void playOrStopAnimatedStickers(boolean play) {
        if (editState.mediaEntities != null) {
            for (int a = 0, N = editState.mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = editState.mediaEntities.get(a);
                if (entity.type == 0 && (entity.subType & 1) != 0 && entity.view instanceof BackupImageView) {
                    ImageReceiver imageReceiver = ((BackupImageView) entity.view).getImageReceiver();
                    RLottieDrawable drawable = imageReceiver.getLottieAnimation();
                    if (drawable == null) {
                        continue;
                    }
                    if (play) {
                        drawable.start();
                    } else {
                        drawable.stop();
                    }
                }
            }
        }
    }

    private int getAnimatedMediaEntitiesCount(boolean single) {
        int count = 0;
        if (editState.mediaEntities != null) {
            for (int a = 0, N = editState.mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = editState.mediaEntities.get(a);
                if (entity.type == 0 && (entity.subType & 1) != 0) {
                    count++;
                    if (single) {
                        break;
                    }
                }
            }
        }
        return count;
    }

    private boolean hasAnimatedMediaEntities() {
        return getAnimatedMediaEntitiesCount(true) != 0;
    }

    private Bitmap createCroppedBitmap(Bitmap bitmap, MediaController.CropState cropState, int[] extraTransform, boolean mirror) {
        try {
            int tr = (cropState.transformRotation + (extraTransform != null ? extraTransform[0] : 0)) % 360;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int fw = w, rotatedW = w;
            int fh = h, rotatedH = h;
            if (tr == 90 || tr == 270) {
                int temp = fw;
                fw = rotatedW = fh;
                fh = rotatedH = temp;
            }
            fw *= cropState.cropPw;
            fh *= cropState.cropPh;
            Bitmap canvasBitmap = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
            Matrix matrix = new Matrix();
            matrix.postTranslate(-w / 2, -h / 2);
            if (mirror && cropState.mirrored) {
                if (tr == 90 || tr == 270) {
                    matrix.postScale(1, -1);
                } else {
                    matrix.postScale(-1, 1);
                }
            }
            matrix.postRotate(cropState.cropRotate + tr);
            matrix.postTranslate(cropState.cropPx * rotatedW, cropState.cropPy * rotatedH);
            matrix.postScale(cropState.cropScale, cropState.cropScale);
            matrix.postTranslate(fw / 2, fh / 2);
            Canvas canvas = new Canvas(canvasBitmap);
            canvas.drawBitmap(bitmap, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
            return canvasBitmap;
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private void applyCurrentEditMode() {
        if (currentIndex < 0 || currentIndex >= imagesArrLocals.size()) {
            return;
        }
        Object object = imagesArrLocals.get(currentIndex);
        if (!(object instanceof MediaController.MediaEditState)) {
            return;
        }
        Bitmap bitmap = null;
        Bitmap[] paintThumbBitmap = new Bitmap[1];
        ArrayList<TLRPC.InputDocument> stickers = null;
        MediaController.SavedFilterState savedFilterState = null;
        ArrayList<VideoEditedInfo.MediaEntity> entities = null;
        int[] orientation = null;
        boolean hasChanged = false;
        MediaController.MediaEditState entry = (MediaController.MediaEditState) imagesArrLocals.get(currentIndex);
        if (currentEditMode == 1 || currentEditMode == 0 && sendPhotoType == SELECT_TYPE_AVATAR) {
            photoCropView.makeCrop(entry);
            if (entry.cropState == null) {
                return;
            }
            if (isCurrentVideo) {
                if (!TextUtils.isEmpty(entry.filterPath)) {
                    bitmap = ImageLoader.loadBitmap(entry.filterPath, null, thumbSize, thumbSize, true);
                } else {
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        orientation = new int[1];
                        bitmap = SendMessagesHelper.createVideoThumbnailAtTime(entry.getPath(), avatarStartTime / 1000, orientation, true);
                    } else {
                        bitmap = SendMessagesHelper.createVideoThumbnailAtTime(entry.getPath(), (long) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration() * 1000L));
                    }
                }
            } else {
                bitmap = centerImage.getBitmap();
                orientation = new int[]{centerImage.getOrientation()};
            }
        } else if (currentEditMode == 2) {
            bitmap = photoFilterView.getBitmap();
            savedFilterState = photoFilterView.getSavedFilterState();
        } else if (currentEditMode == 3) {
            if (Build.VERSION.SDK_INT >= 18 && (sendPhotoType == 0 || sendPhotoType == SELECT_TYPE_AVATAR || sendPhotoType == 2)) {
                entities = new ArrayList<>();
            }
            hasChanged = photoPaintView.hasChanges();
            bitmap = photoPaintView.getBitmap(entities, paintThumbBitmap);
            stickers = photoPaintView.getMasks();
        }
        if (bitmap == null) {
            return;
        }

        if (entry.thumbPath != null) {
            new File(entry.thumbPath).delete();
            entry.thumbPath = null;
        }
        if (entry.imagePath != null) {
            new File(entry.imagePath).delete();
            entry.imagePath = null;
        }

        if (currentEditMode == 1 || currentEditMode == 0 && sendPhotoType == SELECT_TYPE_AVATAR) {
            editState.cropState = entry.cropState;
            editState.croppedPaintPath = entry.croppedPaintPath;
            editState.croppedMediaEntities = entry.croppedMediaEntities;

            Bitmap croppedBitmap = createCroppedBitmap(bitmap, entry.cropState, orientation, true);
            if (entry.paintPath != null) {
                Bitmap paintBitmap = BitmapFactory.decodeFile(entry.fullPaintPath);
                Bitmap croppedPaintBitmap = createCroppedBitmap(paintBitmap, entry.cropState, null, false);

                if (!isCurrentVideo) {
                    if (hasAnimatedMediaEntities()) {
                        TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(croppedBitmap, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
                        entry.imagePath = currentImagePath = FileLoader.getPathToAttach(size, true).toString();
                    } else {
                        File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_temp.jpg");
                        mergeImages(entry.imagePath = f.getAbsolutePath(), null, croppedPaintBitmap, croppedBitmap, AndroidUtilities.getPhotoSize(), true);
                    }
                }
                File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_temp.jpg");
                mergeImages(entry.thumbPath = f.getAbsolutePath(), null, croppedPaintBitmap, croppedBitmap, thumbSize, true);
                if (croppedPaintBitmap != null) {
                    croppedPaintBitmap.recycle();
                }
                if (paintBitmap != null) {
                    paintBitmap.recycle();
                }
            } else {
                if (!isCurrentVideo) {
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(croppedBitmap, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
                    entry.imagePath = currentImagePath = FileLoader.getPathToAttach(size, true).toString();
                }
                TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(croppedBitmap, thumbSize, thumbSize, 70, false, 101, 101);
                entry.thumbPath = FileLoader.getPathToAttach(size, true).toString();
            }
            if (currentEditMode == 0 && isCurrentVideo) {
                bitmap.recycle();
                bitmap = croppedBitmap;
            }
        } else if (currentEditMode == 2) {
            if (entry.filterPath != null) {
                new File(entry.filterPath).delete();
            }
            TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
            entry.filterPath = FileLoader.getPathToAttach(size, true).toString();
            Bitmap b = entry.cropState != null ? createCroppedBitmap(bitmap, entry.cropState, null, true) : bitmap;
            if (entry.paintPath == null) {
                if (!isCurrentVideo) {
                    size = ImageLoader.scaleAndSaveImage(b, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
                    entry.imagePath = currentImagePath = FileLoader.getPathToAttach(size, true).toString();
                }
                size = ImageLoader.scaleAndSaveImage(b, Bitmap.CompressFormat.JPEG, thumbSize, thumbSize, 83, false, 101, 101);
                entry.thumbPath = FileLoader.getPathToAttach(size, true).toString();
            } else {
                String path = entry.fullPaintPath;
                Bitmap fullPaintBitmap = entry.paintPath.equals(entry.fullPaintPath) ? paintingOverlay.getThumb() : null;
                Bitmap paintBitmap;
                boolean recyclePaint;
                if (entry.cropState != null) {
                    if (fullPaintBitmap == null) {
                        Bitmap b2 = BitmapFactory.decodeFile(entry.fullPaintPath);
                        paintBitmap = createCroppedBitmap(b2, entry.cropState, null, false);
                        b2.recycle();
                    } else {
                        paintBitmap = createCroppedBitmap(fullPaintBitmap, entry.cropState, null, false);
                    }
                    recyclePaint = true;
                } else {
                    paintBitmap = fullPaintBitmap;
                    recyclePaint = false;
                }
                if (!isCurrentVideo) {
                    if (hasAnimatedMediaEntities()) {
                        size = ImageLoader.scaleAndSaveImage(b, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
                        entry.imagePath = currentImagePath = FileLoader.getPathToAttach(size, true).toString();
                    } else {
                        File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_temp.jpg");
                        mergeImages(entry.imagePath = f.getAbsolutePath(), path, paintBitmap, b, AndroidUtilities.getPhotoSize(), true);
                    }
                }
                File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_temp.jpg");
                mergeImages(entry.thumbPath = f.getAbsolutePath(), path, paintBitmap, b, thumbSize, true);
                if (recyclePaint) {
                    paintBitmap.recycle();
                }
            }
            if (entry.cropState != null) {
                b.recycle();
            }
        } else if (currentEditMode == 3) {
            if (entry.paintPath != null) {
                new File(entry.paintPath).delete();
                if (!entry.paintPath.equals(entry.fullPaintPath)) {
                    new File(entry.fullPaintPath).delete();
                }
            }
            TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);

            entry.stickers = stickers;
            entry.paintPath = editState.paintPath = FileLoader.getPathToAttach(size, true).toString();
            paintingOverlay.setEntities(entry.mediaEntities = editState.mediaEntities = entities == null || entities.isEmpty() ? null : entities, isCurrentVideo, true);
            entry.averageDuration = editState.averageDuration = photoPaintView.getLcm();
            if (entry.mediaEntities != null && paintThumbBitmap[0] != null) {
                size = ImageLoader.scaleAndSaveImage(paintThumbBitmap[0], Bitmap.CompressFormat.PNG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
                entry.fullPaintPath = FileLoader.getPathToAttach(size, true).toString();
            } else {
                entry.fullPaintPath = entry.paintPath;
            }
            paintingOverlay.setBitmap(bitmap);

            if (entry.cropState != null && entry.cropState.initied) {
                String path = CropView.getCopy(editState.paintPath);
                if (editState.croppedPaintPath != null) {
                    new File(editState.croppedPaintPath).delete();
                    editState.croppedPaintPath = null;
                }
                editState.croppedPaintPath = path;
                if (editState.mediaEntities != null && !editState.mediaEntities.isEmpty()) {
                    editState.croppedMediaEntities = new ArrayList<>(editState.mediaEntities.size());
                    for (int a = 0, N = editState.mediaEntities.size(); a < N; a++) {
                        editState.croppedMediaEntities.add(editState.mediaEntities.get(a).copy());
                    }
                } else {
                    editState.croppedMediaEntities = null;
                }
                Bitmap resultBitmap = Bitmap.createBitmap(entry.cropState.width, entry.cropState.height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(resultBitmap);
                int w, h;
                if (isCurrentVideo) {
                    VideoEditTextureView videoEditTextureView = (VideoEditTextureView) videoTextureView;
                    w = videoEditTextureView.getVideoWidth();
                    h = videoEditTextureView.getVideoHeight();
                } else {
                    w = centerImage.getBitmapWidth();
                    h = centerImage.getBitmapHeight();
                }
                CropView.editBitmap(parentActivity, path, null, canvas, resultBitmap, Bitmap.CompressFormat.PNG, entry.cropState.matrix, w, h, entry.cropState.stateScale, entry.cropState.cropRotate, entry.cropState.transformRotation, entry.cropState.scale, false, editState.croppedMediaEntities, false);
                resultBitmap.recycle();

                entry.croppedPaintPath = editState.croppedPaintPath;
                entry.croppedMediaEntities = editState.croppedMediaEntities;
            }

            boolean recycle = false;
            Bitmap fullPaintBitmap = paintThumbBitmap[0] != null ? paintThumbBitmap[0] : bitmap;
            Bitmap paintBitmap = fullPaintBitmap;
            if (entry.cropState != null && entry.cropState.initied) {
                paintBitmap = createCroppedBitmap(fullPaintBitmap, entry.cropState, null, false);
                recycle = true;
            }

            boolean recycleCropped;
            Bitmap croppedBitmap;
            if (isCurrentVideo) {
                Bitmap originalBitmap;
                if (entry.filterPath == null) {
                    originalBitmap = SendMessagesHelper.createVideoThumbnailAtTime(entry.getPath(), (long) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration() * 1000L));
                } else {
                    originalBitmap = ImageLoader.loadBitmap(entry.filterPath, null, thumbSize, thumbSize, true);
                }
                croppedBitmap = entry.cropState != null ? createCroppedBitmap(originalBitmap, entry.cropState, null, true) : originalBitmap;
                if (entry.cropState != null) {
                    originalBitmap.recycle();
                }
                recycleCropped = true;
            } else {
                orientation = new int[]{centerImage.getOrientation()};
                if (entry.cropState != null) {
                    croppedBitmap = createCroppedBitmap(centerImage.getBitmap(), entry.cropState, orientation, true);
                    recycleCropped = true;
                } else {
                    croppedBitmap = centerImage.getBitmap();
                    if (orientation[0] != 0) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(orientation[0]);
                        croppedBitmap = Bitmaps.createBitmap(croppedBitmap, 0, 0, croppedBitmap.getWidth(), croppedBitmap.getHeight(), matrix, true);
                        recycleCropped = true;
                    } else {
                        recycleCropped = false;
                    }
                }
            }
            if (!isCurrentVideo) {
                if (hasAnimatedMediaEntities()) {
                    size = ImageLoader.scaleAndSaveImage(croppedBitmap, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 87, false, 101, 101);
                    entry.imagePath = currentImagePath = FileLoader.getPathToAttach(size, true).toString();
                } else {
                    File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_temp.jpg");
                    mergeImages(entry.imagePath = f.getAbsolutePath(), null, croppedBitmap, paintBitmap, AndroidUtilities.getPhotoSize(), false);
                }
            }
            File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), SharedConfig.getLastLocalId() + "_temp.jpg");
            mergeImages(entry.thumbPath = f.getAbsolutePath(), null, croppedBitmap, paintBitmap, thumbSize, false);

            if (recycle && paintBitmap != null) {
                paintBitmap.recycle();
            }
            if (recycleCropped && croppedBitmap != null) {
                croppedBitmap.recycle();
            }
            if (sendPhotoType == SELECT_TYPE_AVATAR && videoPlayer != null && isCurrentVideo && getAnimatedMediaEntitiesCount(false) == 1 && videoTimelineView.getLeftProgress() <= 0.0f && videoTimelineView.getRightProgress() >= 1.0f) {
                long duration = entry.averageDuration;
                long videoDuration = videoPlayer.getDuration();
                while (duration < 3000 && duration < videoDuration) {
                    duration += entry.averageDuration;
                }
                videoTimelineView.setRightProgress(Math.min(1.0f, duration / (float) videoDuration));
            }
        }

        SharedConfig.saveConfig();

        if (savedFilterState != null) {
            entry.savedFilterState = editState.savedFilterState = savedFilterState;
        }
        if (currentEditMode == 1) {
            entry.isCropped = true;
            cropItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY));
        } else if (currentEditMode == 2) {
            entry.isFiltered = true;
            tuneItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY));
        } else if (currentEditMode == 3) {
            if (hasChanged) {
                entry.isPainted = true;
                paintItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY));
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
            scale = Math.max(scaleX, scaleY);
            translationX = photoCropView.getRectX() + photoCropView.getRectSizeX() / 2 - getContainerViewWidth() / 2;
            translationY = photoCropView.getRectY() + photoCropView.getRectSizeY() / 2 - getContainerViewHeight() / 2;
            zoomAnimation = true;
            applying = true;

            photoCropView.onDisappear();
        }
        centerImage.setParentView(null);
        ignoreDidSetImage = true;
        boolean setImage;
        if (isCurrentVideo) {
            setImage = currentEditMode == 1 || currentEditMode == 0 && sendPhotoType == SELECT_TYPE_AVATAR;
        } else {
            setImage = currentEditMode == 2;
        }
        if (setImage) {
            centerImage.setImageBitmap(bitmap);
            centerImage.setOrientation(0, true);
            containerView.requestLayout();
        }
        ignoreDidSetImage = false;
        centerImage.setParentView(containerView);
    }

    private void setPhotoChecked() {
        if (placeProvider != null) {
            if (placeProvider.getSelectedPhotos() != null && maxSelectedPhotos > 0 && placeProvider.getSelectedPhotos().size() >= maxSelectedPhotos && !placeProvider.isPhotoChecked(currentIndex)) {
                if (allowOrder && parentChatActivity != null) {
                    TLRPC.Chat chat = parentChatActivity.getCurrentChat();
                    if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                        AlertsCreator.createSimpleAlert(parentActivity, LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSelectSendError", R.string.SlowmodeSelectSendError)).show();
                    }
                }
                return;
            }
            int num = placeProvider.setPhotoChecked(currentIndex, getCurrentVideoEditedInfo());
            boolean checked = placeProvider.isPhotoChecked(currentIndex);
            checkImageView.setChecked(checked, true);
            if (num >= 0) {
                if (checked) {
                    selectedPhotosAdapter.notifyItemInserted(num);
                    selectedPhotosListView.smoothScrollToPosition(num);
                } else {
                    selectedPhotosAdapter.notifyItemRemoved(num);
                    if (num == 0) {
                        selectedPhotosAdapter.notifyItemChanged(0);
                    }
                }
            }
            updateSelectedCount();
        }
    }

    private void createCropView() {
        if (photoCropView != null) {
            return;
        }
        photoCropView = new PhotoCropView(activityContext, resourcesProvider);
        photoCropView.setVisibility(View.GONE);
        photoCropView.onDisappear();
        int index = containerView.indexOfChild(videoTimelineView);
        containerView.addView(photoCropView, index - 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
        photoCropView.setDelegate(new PhotoCropView.PhotoCropViewDelegate() {
            @Override
            public void onChange(boolean reset) {
                resetButton.setVisibility(reset ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onUpdate() {
                containerView.invalidate();
            }

            @Override
            public void onTapUp() {
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    manuallyPaused = true;
                    toggleVideoPlayer();
                }
            }

            @Override
            public void onVideoThumbClick() {
                if (videoPlayer == null) {
                    return;
                }
                videoPlayer.seekTo((long) (videoPlayer.getDuration() * avatarStartProgress));
                videoPlayer.pause();
                videoTimelineView.setProgress(avatarStartProgress);
                cancelVideoPlayRunnable();
                AndroidUtilities.runOnUIThread(videoPlayRunnable = () -> {
                    manuallyPaused = false;
                    if (videoPlayer != null) {
                        videoPlayer.play();
                    }
                    videoPlayRunnable = null;
                }, 860);
            }

            @Override
            public int getVideoThumbX() {
                return (int) (AndroidUtilities.dp(16) + (videoTimelineView.getMeasuredWidth() - AndroidUtilities.dp(32)) * avatarStartProgress);
            }
        });
    }

    private void startVideoPlayer() {
        if (isCurrentVideo && videoPlayer != null && !videoPlayer.isPlaying()) {
            if (!muteVideo || sendPhotoType == SELECT_TYPE_AVATAR) {
                videoPlayer.setVolume(0);
            }
            manuallyPaused = false;
            toggleVideoPlayer();
        }
    }

    private void detectFaces() {
        if (centerImage.getAnimation() != null || imagesArrLocals.isEmpty() || sendPhotoType == SELECT_TYPE_AVATAR) {
            return;
        }
        String key = centerImage.getImageKey();
        if (currentImageFaceKey != null && currentImageFaceKey.equals(key)) {
            return;
        }
        currentImageHasFace = 0;
        ImageReceiver.BitmapHolder bitmap = centerImage.getBitmapSafe();
        detectFaces(key, bitmap, centerImage.getOrientation());
    }

    private void detectFaces(String key, ImageReceiver.BitmapHolder bitmap, int orientation) {
        if (key == null || bitmap == null || bitmap.bitmap == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
        	/*
            FaceDetector faceDetector = null;
            try {
                faceDetector = new FaceDetector.Builder(ApplicationLoader.applicationContext)
                        .setMode(FaceDetector.FAST_MODE)
                        .setLandmarkType(FaceDetector.NO_LANDMARKS)
                        .setTrackingEnabled(false).build();
                if (faceDetector.isOperational()) {
                    Frame frame = new Frame.Builder().setBitmap(bitmap.bitmap).setRotation(orientation).build();
                    SparseArray<Face> faces = faceDetector.detect(frame);
                    boolean hasFaces = faces != null && faces.size() != 0;
                    AndroidUtilities.runOnUIThread(() -> {
                        String imageKey = centerImage.getImageKey();
                        if (key.equals(imageKey)) {
                            currentImageHasFace = hasFaces ? 1 : 0;
                            currentImageFaceKey = key;
                        }
                    });
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("face detection is not operational");
                    }*/
                    AndroidUtilities.runOnUIThread(() -> {
                        bitmap.release();
                        String imageKey = centerImage.getImageKey();
                        if (key.equals(imageKey)) {
                            currentImageHasFace = 2;
                            currentImageFaceKey = key;
                        }
                    });/*
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (faceDetector != null) {
                    faceDetector.release();
                }
            }*/
        });
    }

    private void switchToEditMode(final int mode) {
        if (currentEditMode == mode || (isCurrentVideo && photoProgressViews[0].backgroundState != 3) && !isCurrentVideo && (centerImage.getBitmap() == null || photoProgressViews[0].backgroundState != -1) || changeModeAnimation != null || imageMoveAnimation != null || captionEditText.getTag() != null) {
            return;
        }
        switchingToMode = mode;
        if (mode == 0) { // no edit mode
            Bitmap bitmap = centerImage.getBitmap();
            if (bitmap != null) {
                int bitmapWidth = centerImage.getBitmapWidth();
                int bitmapHeight = centerImage.getBitmapHeight();

                float newScale;
                float oldScale;
                if (currentEditMode == 3) {
                    if (sendPhotoType != SELECT_TYPE_AVATAR) {
                        if (cropTransform.getOrientation() == 90 || cropTransform.getOrientation() == 270) {
                            int temp = bitmapWidth;
                            bitmapWidth = bitmapHeight;
                            bitmapHeight = temp;
                        }
                    } else {
                        if (editState.cropState != null) {
                            if (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270) {
                                int temp = bitmapWidth;
                                bitmapWidth = bitmapHeight;
                                bitmapHeight = temp;
                            }
                            bitmapWidth *= editState.cropState.cropPw;
                            bitmapHeight *= editState.cropState.cropPh;
                        }
                    }
                    newScale = Math.min(getContainerViewWidth(0) / (float) bitmapWidth, getContainerViewHeight(0) / (float) bitmapHeight);
                    oldScale = Math.min(getContainerViewWidth() / (float) bitmapWidth, getContainerViewHeight() / (float) bitmapHeight);
                } else {
                    if (currentEditMode != 1 && editState.cropState != null && (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270)) {
                        float scaleToFitX = getContainerViewWidth() / (float) bitmapHeight;
                        if (scaleToFitX * bitmapWidth > getContainerViewHeight()) {
                            scaleToFitX = getContainerViewHeight() / (float) bitmapWidth;
                        }
                        float sc = Math.min(getContainerViewWidth() / (float) bitmapWidth, getContainerViewHeight() / (float) bitmapHeight);
                        float cropScale = scaleToFitX / sc;
                        scale = 1.0f / cropScale;
                    } else if (sendPhotoType == SELECT_TYPE_AVATAR && (cropTransform.getOrientation() == 90 || cropTransform.getOrientation() == 270)) {
                        float scaleToFitX = getContainerViewWidth() / (float) bitmapHeight;
                        if (scaleToFitX * bitmapWidth > getContainerViewHeight()) {
                            scaleToFitX = getContainerViewHeight() / (float) bitmapWidth;
                        }
                        float sc = Math.min(getContainerViewWidth() / (float) bitmapWidth, getContainerViewHeight() / (float) bitmapHeight);
                        float cropScale = cropTransform.getScale() / cropTransform.getTrueCropScale() * scaleToFitX / sc / cropTransform.getMinScale();
                        scale = 1.0f / cropScale;
                    }
                    if (editState.cropState != null) {
                        if (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270) {
                            int temp = bitmapWidth;
                            bitmapWidth = bitmapHeight;
                            bitmapHeight = temp;
                        }
                        bitmapWidth *= editState.cropState.cropPw;
                        bitmapHeight *= editState.cropState.cropPh;
                    } else if (sendPhotoType == SELECT_TYPE_AVATAR && (cropTransform.getOrientation() == 90 || cropTransform.getOrientation() == 270)) {
                        int temp = bitmapWidth;
                        bitmapWidth = bitmapHeight;
                        bitmapHeight = temp;
                    }
                    oldScale = Math.min(getContainerViewWidth() / (float) bitmapWidth, getContainerViewHeight() / (float) bitmapHeight);
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        newScale = getCropFillScale(cropTransform.getOrientation() == 90 || cropTransform.getOrientation() == 270);
                    } else {
                        newScale = Math.min(getContainerViewWidth(0) / (float) bitmapWidth, getContainerViewHeight(0) / (float) bitmapHeight);
                    }
                }
                animateToScale = newScale / oldScale;
                animateToX = 0;
                translationX = getLeftInset() / 2 - getRightInset() / 2;
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    if (currentEditMode == 2) {
                        animateToY = AndroidUtilities.dp(36);
                    } else if (currentEditMode == 3) {
                        animateToY = -AndroidUtilities.dp(12);
                    }
                } else {
                    if (currentEditMode == 1) {
                        animateToY = AndroidUtilities.dp(24 + 32);
                    } else if (currentEditMode == 2) {
                        animateToY = AndroidUtilities.dp(93);
                    } else if (currentEditMode == 3) {
                        animateToY = AndroidUtilities.dp(44);
                    }
                    if (isStatusBarVisible()) {
                        animateToY -= AndroidUtilities.statusBarHeight / 2;
                    }
                }
                animationStartTime = System.currentTimeMillis();
                zoomAnimation = true;
            }
            padImageForHorizontalInsets = false;

            imageMoveAnimation = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>(4);
            if (currentEditMode == 1) {
                animators.add(ObjectAnimator.ofFloat(editorDoneLayout, View.TRANSLATION_Y, AndroidUtilities.dp(48)));
                animators.add(ObjectAnimator.ofFloat(PhotoViewer.this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1));
                animators.add(ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 0));
            } else if (currentEditMode == 2) {
                photoFilterView.shutdown();
                animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.TRANSLATION_Y, AndroidUtilities.dp(186)));
                animators.add(ObjectAnimator.ofFloat(photoFilterView.getCurveControl(), View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(photoFilterView.getBlurControl(), View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(PhotoViewer.this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1));
            } else if (currentEditMode == 3) {
                paintingOverlay.showAll();
                containerView.invalidate();
                photoPaintView.shutdown();
                animators.add(ObjectAnimator.ofFloat(photoPaintView.getToolsView(), View.TRANSLATION_Y, AndroidUtilities.dp(126)));
                animators.add(ObjectAnimator.ofFloat(photoPaintView.getColorPicker(), View.TRANSLATION_Y, AndroidUtilities.dp(126)));
                animators.add(ObjectAnimator.ofFloat(PhotoViewer.this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1));
            }
            imageMoveAnimation.playTogether(animators);
            imageMoveAnimation.setDuration(200);
            imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentEditMode == 1) {
                        photoCropView.onDisappear();
                        photoCropView.onHide();
                        editorDoneLayout.setVisibility(View.GONE);
                        photoCropView.setVisibility(View.GONE);
                    } else if (currentEditMode == 2) {
                        try {
                            containerView.removeView(photoFilterView);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        photoFilterView = null;
                    } else if (currentEditMode == 3) {
                        try {
                            containerView.removeView(photoPaintView);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        photoPaintView = null;
                    }
                    imageMoveAnimation = null;
                    currentEditMode = mode;
                    switchingToMode = -1;
                    applying = false;
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        photoCropView.setVisibility(View.VISIBLE);
                    }
                    animateToScale = 1;
                    animateToX = 0;
                    animateToY = 0;
                    scale = 1;
                    updateMinMax(scale);
                    containerView.invalidate();

                    if (savedState != null) {
                        savedState.restore();
                        savedState = null;
                        final ActionBarToggleParams toggleParams = new ActionBarToggleParams().enableStatusBarAnimation(false);
                        toggleActionBar(false, false, toggleParams);
                        toggleActionBar(true, true, toggleParams);
                        return;
                    }

                    AnimatorSet animatorSet = new AnimatorSet();
                    ArrayList<Animator> arrayList = new ArrayList<>();
                    arrayList.add(ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, 0));
                    arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, 0));
                    if (sendPhotoType != SELECT_TYPE_AVATAR) {
                        arrayList.add(ObjectAnimator.ofFloat(actionBar, View.TRANSLATION_Y, 0));
                    }
                    if (needCaptionLayout) {
                        arrayList.add(ObjectAnimator.ofFloat(captionTextViewSwitcher, View.TRANSLATION_Y, 0));
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4) {
                        arrayList.add(ObjectAnimator.ofFloat(checkImageView, View.ALPHA, 1));
                        arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.ALPHA, 1));
                    } else if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        arrayList.add(ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 1));
                    }
                    if (cameraItem.getTag() != null) {
                        cameraItem.setVisibility(View.VISIBLE);
                        arrayList.add(ObjectAnimator.ofFloat(cameraItem, View.ALPHA, 1));
                    }
                    if (muteItem.getTag() != null) {
                        muteItem.setVisibility(View.VISIBLE);
                        arrayList.add(ObjectAnimator.ofFloat(muteItem, View.ALPHA, 1));
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
                                captionTextViewSwitcher.setVisibility(captionTextViewSwitcher.getTag() != null ? View.VISIBLE : View.INVISIBLE);
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
        } else if (mode == 1) { // crop
            startVideoPlayer();
            createCropView();
            previousHasTransform = cropTransform.hasViewTransform();
            previousCropPx = cropTransform.getCropPx();
            previousCropPy = cropTransform.getCropPy();
            previousCropScale = cropTransform.getScale();
            previousCropRotation = cropTransform.getRotation();
            previousCropOrientation = cropTransform.getOrientation();
            previousCropPw = cropTransform.getCropPw();
            previousCropPh = cropTransform.getCropPh();
            previousCropMirrored = cropTransform.isMirrored();
            photoCropView.onAppear();

            editorDoneLayout.doneButton.setText(LocaleController.getString("Crop", R.string.Crop));
            editorDoneLayout.doneButton.setTextColor(getThemedColor(Theme.key_dialogFloatingButton));

            changeModeAnimation = new AnimatorSet();
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, 0, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, 0, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            arrayList.add(ObjectAnimator.ofFloat(actionBar, View.TRANSLATION_Y, 0, -actionBar.getHeight()));
            if (needCaptionLayout) {
                arrayList.add(ObjectAnimator.ofFloat(captionTextViewSwitcher, View.TRANSLATION_Y, 0, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            }
            if (sendPhotoType == 0 || sendPhotoType == 4) {
                arrayList.add(ObjectAnimator.ofFloat(checkImageView, View.ALPHA, 1, 0));
                arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.ALPHA, 1, 0));
            }
            if (selectedPhotosListView.getVisibility() == View.VISIBLE) {
                arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, View.ALPHA, 1, 0));
            }
            if (cameraItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(cameraItem, View.ALPHA, 1, 0));
            }
            if (muteItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(muteItem, View.ALPHA, 1, 0));
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
                    muteItem.setVisibility(View.GONE);
                    selectedPhotosListView.setVisibility(View.GONE);
                    selectedPhotosListView.setAlpha(0.0f);
                    selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
                    photosCounterView.setRotationX(0.0f);
                    selectedPhotosListView.setEnabled(false);
                    isPhotosListViewVisible = false;
                    if (needCaptionLayout) {
                        captionTextViewSwitcher.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                        checkImageView.setVisibility(View.GONE);
                        photosCounterView.setVisibility(View.GONE);
                    }

                    final Bitmap bitmap = centerImage.getBitmap();
                    if (bitmap != null || isCurrentVideo) {
                        photoCropView.setBitmap(bitmap, centerImage.getOrientation(), sendPhotoType != SELECT_TYPE_AVATAR, false, paintingOverlay, cropTransform, isCurrentVideo ? (VideoEditTextureView) videoTextureView : null, editState.cropState);
                        photoCropView.onDisappear();
                        int bitmapWidth = centerImage.getBitmapWidth();
                        int bitmapHeight = centerImage.getBitmapHeight();
                        if (editState.cropState != null) {
                            if (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270) {
                                int temp = bitmapWidth;
                                bitmapWidth = bitmapHeight;
                                bitmapHeight = temp;
                            }
                            bitmapWidth *= editState.cropState.cropPw;
                            bitmapHeight *= editState.cropState.cropPh;
                        }

                        float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                        float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                        float newScaleX = (float) getContainerViewWidth(1) / (float) bitmapWidth;
                        float newScaleY = (float) getContainerViewHeight(1) / (float) bitmapHeight;
                        float scale = Math.min(scaleX, scaleY);
                        float newScale = Math.min(newScaleX, newScaleY);
                        if (sendPhotoType == SELECT_TYPE_AVATAR) {
                            float minSide = Math.min(getContainerViewWidth(1), getContainerViewHeight(1));
                            newScaleX = minSide / (float) bitmapWidth;
                            newScaleY = minSide / (float) bitmapHeight;
                            newScale = Math.max(newScaleX, newScaleY);
                        }

                        animateToScale = newScale / scale;
                        animateToX = getLeftInset() / 2 - getRightInset() / 2;
                        animateToY = -AndroidUtilities.dp(24 + 32) + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight / 2 : 0);
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSet();
                    imageMoveAnimation.playTogether(
                            ObjectAnimator.ofFloat(editorDoneLayout, View.TRANSLATION_Y, AndroidUtilities.dp(48), 0),
                            ObjectAnimator.ofFloat(PhotoViewer.this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1),
                            ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 0, 1)
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
                            photoCropView.onShow();

                            imageMoveAnimation = null;
                            currentEditMode = mode;
                            switchingToMode = -1;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            padImageForHorizontalInsets = true;
                            containerView.invalidate();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        } else if (mode == 2) { // filter
            startVideoPlayer();
            if (photoFilterView == null) {
                MediaController.SavedFilterState state = null;
                Bitmap bitmap;
                String originalPath = null;
                int orientation = 0;
                if (!imagesArrLocals.isEmpty()) {
                    Object object = imagesArrLocals.get(currentIndex);
                    if (object instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) object;
                        orientation = entry.orientation;
                    }
                    MediaController.MediaEditState editState = (MediaController.MediaEditState) object;
                    state = editState.savedFilterState;
                    originalPath = editState.getPath();
                }
                if (videoTextureView != null) {
                    bitmap = null;
                } else {
                    if (state == null) {
                        bitmap = centerImage.getBitmap();
                        orientation = centerImage.getOrientation();
                    } else {
                        bitmap = BitmapFactory.decodeFile(originalPath);
                    }
                }

                int hasFaces;
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    hasFaces = 1;
                } else if (isCurrentVideo || currentImageHasFace == 2) {
                    hasFaces = 2;
                } else {
                    hasFaces = currentImageHasFace == 1 ? 1 : 0;
                }
                photoFilterView = new PhotoFilterView(parentActivity, videoTextureView != null ? (VideoEditTextureView) videoTextureView : null, bitmap, orientation, state, isCurrentVideo ? null : paintingOverlay, hasFaces, videoTextureView == null && (editState.cropState != null && editState.cropState.mirrored || cropTransform.isMirrored()), resourcesProvider);
                containerView.addView(photoFilterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                photoFilterView.getDoneTextView().setOnClickListener(v -> {
                    applyCurrentEditMode();
                    switchToEditMode(0);
                });
                photoFilterView.getCancelTextView().setOnClickListener(v -> {
                    if (photoFilterView.hasChanges()) {
                        if (parentActivity == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
                        builder.setMessage(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> switchToEditMode(0));
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else {
                        switchToEditMode(0);
                    }
                });
                photoFilterView.getToolsView().setTranslationY(AndroidUtilities.dp(186));
            }

            changeModeAnimation = new AnimatorSet();
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, 0, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, 0, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            arrayList.add(ObjectAnimator.ofFloat(actionBar, View.TRANSLATION_Y, 0, -actionBar.getHeight()));
            if (sendPhotoType == 0 || sendPhotoType == 4) {
                arrayList.add(ObjectAnimator.ofFloat(checkImageView, View.ALPHA, 1, 0));
                arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.ALPHA, 1, 0));
            } else if (sendPhotoType == SELECT_TYPE_AVATAR) {
                arrayList.add(ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 1, 0));
            }
            if (selectedPhotosListView.getVisibility() == View.VISIBLE) {
                arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, View.ALPHA, 1, 0));
            }
            if (cameraItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(cameraItem, View.ALPHA, 1, 0));
            }
            if (muteItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(muteItem, View.ALPHA, 1, 0));
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
                    muteItem.setVisibility(View.GONE);
                    if (photoCropView != null) {
                        photoCropView.setVisibility(View.INVISIBLE);
                    }
                    selectedPhotosListView.setVisibility(View.GONE);
                    selectedPhotosListView.setAlpha(0.0f);
                    selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
                    photosCounterView.setRotationX(0.0f);
                    selectedPhotosListView.setEnabled(false);
                    isPhotosListViewVisible = false;
                    if (needCaptionLayout) {
                        captionTextViewSwitcher.setVisibility(View.INVISIBLE);
                    }
                    if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                        checkImageView.setVisibility(View.GONE);
                        photosCounterView.setVisibility(View.GONE);
                    }

                    Bitmap bitmap = centerImage.getBitmap();
                    if (bitmap != null) {
                        int bitmapWidth = centerImage.getBitmapWidth();
                        int bitmapHeight = centerImage.getBitmapHeight();

                        float oldScale;
                        float newScale = Math.min(getContainerViewWidth(2) / (float) bitmapWidth, getContainerViewHeight(2) / (float) bitmapHeight);
                        if (sendPhotoType == SELECT_TYPE_AVATAR) {
                            animateToY = -AndroidUtilities.dp(36);
                            oldScale = getCropFillScale(false);
                        } else {
                            animateToY = -AndroidUtilities.dp(93) + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight / 2 : 0);
                            if (editState.cropState != null && (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270)) {
                                oldScale = Math.min(getContainerViewWidth() / (float) bitmapHeight, getContainerViewHeight() / (float) bitmapWidth);
                            } else {
                                oldScale = Math.min(getContainerViewWidth() / (float) bitmapWidth, getContainerViewHeight() / (float) bitmapHeight);
                            }
                        }
                        animateToScale = newScale / oldScale;

                        animateToX = getLeftInset() / 2 - getRightInset() / 2;
                        animationStartTime = System.currentTimeMillis();
                        zoomAnimation = true;
                    }

                    imageMoveAnimation = new AnimatorSet();
                    imageMoveAnimation.playTogether(
                            ObjectAnimator.ofFloat(PhotoViewer.this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1),
                            ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.TRANSLATION_Y, AndroidUtilities.dp(186), 0)
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
                            switchingToMode = -1;
                            animateToScale = 1;
                            animateToX = 0;
                            animateToY = 0;
                            scale = 1;
                            updateMinMax(scale);
                            padImageForHorizontalInsets = true;
                            containerView.invalidate();
                        }
                    });
                    imageMoveAnimation.start();
                }
            });
            changeModeAnimation.start();
        } else if (mode == 3) {
            startVideoPlayer();
            createPaintView();

            changeModeAnimation = new AnimatorSet();
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            arrayList.add(ObjectAnimator.ofFloat(actionBar, View.TRANSLATION_Y, -actionBar.getHeight()));

            if (needCaptionLayout) {
                arrayList.add(ObjectAnimator.ofFloat(captionTextViewSwitcher, View.TRANSLATION_Y, AndroidUtilities.dp(isCurrentVideo ? 154 : 96)));
            }
            if (sendPhotoType == 0 || sendPhotoType == 4) {
                arrayList.add(ObjectAnimator.ofFloat(checkImageView, View.ALPHA, 1, 0));
                arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.ALPHA, 1, 0));
            } else if (sendPhotoType == SELECT_TYPE_AVATAR) {
                arrayList.add(ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 1, 0));
            }
            if (selectedPhotosListView.getVisibility() == View.VISIBLE) {
                arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, View.ALPHA, 1, 0));
            }
            if (cameraItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(cameraItem, View.ALPHA, 1, 0));
            }
            if (muteItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(muteItem, View.ALPHA, 1, 0));
            }
            changeModeAnimation.playTogether(arrayList);
            changeModeAnimation.setDuration(200);
            changeModeAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    switchToPaintMode();
                }
            });
            changeModeAnimation.start();
        }
    }

    private void createPaintView() {
        if (photoPaintView == null) {
            int w;
            int h;
            if (videoTextureView != null) {
                VideoEditTextureView textureView = (VideoEditTextureView) videoTextureView;
                w = textureView.getVideoWidth();
                h = textureView.getVideoHeight();
                while (w > 1280 || h > 1280) {
                    w /= 2;
                    h /= 2;
                }
            } else {
                w = centerImage.getBitmapWidth();
                h = centerImage.getBitmapHeight();
            }
            Bitmap bitmap = paintingOverlay.getBitmap();
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            MediaController.CropState state;
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                state = new MediaController.CropState();
                state.transformRotation = cropTransform.getOrientation();
            } else {
                state = editState.cropState;
            }
            photoPaintView = new PhotoPaintView(parentActivity, bitmap, isCurrentVideo ? null : centerImage.getBitmap(), centerImage.getOrientation(), editState.mediaEntities, state, () -> paintingOverlay.hideBitmap(), resourcesProvider) {
                @Override
                protected void onOpenCloseStickersAlert(boolean open) {
                    if (videoPlayer == null) {
                        return;
                    }
                    manuallyPaused = false;
                    cancelVideoPlayRunnable();
                    if (open) {
                        videoPlayer.pause();
                    } else {
                        videoPlayer.play();
                    }
                }

                @Override
                protected void didSetAnimatedSticker(RLottieDrawable drawable) {
                    if (videoPlayer == null) {
                        return;
                    }
                    drawable.setProgressMs(videoPlayer.getCurrentPosition() - (startTime > 0 ? startTime / 1000 : 0));
                }

                @Override
                protected void onTextAdd() {
                    if (!windowView.isFocusable()) {
                        makeFocusable();
                    }
                }
            };
            containerView.addView(photoPaintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            photoPaintView.getDoneTextView().setOnClickListener(v -> {
                savedState = null;
                applyCurrentEditMode();
                switchToEditMode(0);
            });
            photoPaintView.getCancelTextView().setOnClickListener(v -> closePaintMode());
            photoPaintView.getColorPicker().setTranslationY(AndroidUtilities.dp(126));
            photoPaintView.getToolsView().setTranslationY(AndroidUtilities.dp(126));
        }
    }

    private void closePaintMode() {
        photoPaintView.maybeShowDismissalAlert(PhotoViewer.this, parentActivity, () -> switchToEditMode(0));
    }

    private void switchToPaintMode() {
        changeModeAnimation = null;
        pickerView.setVisibility(View.GONE);
        pickerViewSendButton.setVisibility(View.GONE);
        cameraItem.setVisibility(View.GONE);
        muteItem.setVisibility(View.GONE);
        if (photoCropView != null) {
            photoCropView.setVisibility(View.INVISIBLE);
        }
        selectedPhotosListView.setVisibility(View.GONE);
        selectedPhotosListView.setAlpha(0.0f);
        selectedPhotosListView.setTranslationY(-AndroidUtilities.dp(10));
        photosCounterView.setRotationX(0.0f);
        selectedPhotosListView.setEnabled(false);
        isPhotosListViewVisible = false;
        if (needCaptionLayout) {
            captionTextViewSwitcher.setVisibility(View.INVISIBLE);
        }
        if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
            checkImageView.setVisibility(View.GONE);
            photosCounterView.setVisibility(View.GONE);
        }

        Bitmap bitmap = centerImage.getBitmap();
        if (bitmap != null) {
            int bitmapWidth = centerImage.getBitmapWidth();
            int bitmapHeight = centerImage.getBitmapHeight();

            float oldScale;
            float newScale;
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                animateToY = AndroidUtilities.dp(12);
                if (cropTransform.getOrientation() == 90 || cropTransform.getOrientation() == 270) {
                    int temp = bitmapWidth;
                    bitmapWidth = bitmapHeight;
                    bitmapHeight = temp;
                }
            } else {
                animateToY = -AndroidUtilities.dp(44) + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight / 2 : 0);
                if (editState.cropState != null) {
                    if (editState.cropState.transformRotation == 90 || editState.cropState.transformRotation == 270) {
                        int temp = bitmapWidth;
                        bitmapWidth = bitmapHeight;
                        bitmapHeight = temp;
                    }
                    bitmapWidth *= editState.cropState.cropPw;
                    bitmapHeight *= editState.cropState.cropPh;
                }
            }
            oldScale = Math.min(getContainerViewWidth() / (float) bitmapWidth, getContainerViewHeight() / (float) bitmapHeight);
            newScale = Math.min(getContainerViewWidth(3) / (float) bitmapWidth, getContainerViewHeight(3) / (float) bitmapHeight);
            animateToScale = newScale / oldScale;
            animateToX = getLeftInset() / 2 - getRightInset() / 2;
            animationStartTime = System.currentTimeMillis();
            zoomAnimation = true;
        }

        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(
                ObjectAnimator.ofFloat(PhotoViewer.this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1),
                ObjectAnimator.ofFloat(photoPaintView.getColorPicker(), View.TRANSLATION_Y, AndroidUtilities.dp(126), 0),
                ObjectAnimator.ofFloat(photoPaintView.getToolsView(), View.TRANSLATION_Y, AndroidUtilities.dp(126), 0)
        );
        imageMoveAnimation.setDuration(200);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                photoPaintView.init();
                paintingOverlay.hideEntities();
                imageMoveAnimation = null;
                currentEditMode = 3;
                switchingToMode = -1;
                animateToScale = 1;
                animateToX = 0;
                animateToY = 0;
                scale = 1;
                updateMinMax(scale);
                padImageForHorizontalInsets = true;
                containerView.invalidate();
            }
        });
        imageMoveAnimation.start();
    }

    private void toggleCheckImageView(boolean show) {
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> arrayList = new ArrayList<>();
        final float offsetY = AndroidUtilities.dpf2(24);
        arrayList.add(ObjectAnimator.ofFloat(pickerView, View.ALPHA, show ? 1.0f : 0.0f));
        arrayList.add(ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, show ? 0.0f : offsetY));
        arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, View.ALPHA, show ? 1.0f : 0.0f));
        arrayList.add(ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, show ? 0.0f : offsetY));
        if (sendPhotoType == 0 || sendPhotoType == 4) {
            arrayList.add(ObjectAnimator.ofFloat(checkImageView, View.ALPHA, show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(checkImageView, View.TRANSLATION_Y, show ? 0.0f : -offsetY));
            arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.ALPHA, show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.TRANSLATION_Y, show ? 0.0f : -offsetY));
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
        miniProgressAnimator.playTogether(ObjectAnimator.ofFloat(miniProgressView, View.ALPHA, show ? 1.0f : 0.0f));
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
        AndroidUtilities.cancelRunOnUIThread(miniProgressShowRunnable);
        if (animated) {
            toggleMiniProgressInternal(show);
            if (show) {
                if (miniProgressAnimator != null) {
                    miniProgressAnimator.cancel();
                    miniProgressAnimator = null;
                }
                if (firstAnimationDelay) {
                    firstAnimationDelay = false;
                    toggleMiniProgressInternal(true);
                } else {
                    AndroidUtilities.runOnUIThread(miniProgressShowRunnable, 500);
                }
            } else {
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

    private void updateContainerFlags(boolean actionBarVisible) {
        if (Build.VERSION.SDK_INT >= 21 && sendPhotoType != SELECT_TYPE_AVATAR && containerView != null) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!actionBarVisible) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
                if (containerView.getPaddingLeft() > 0 || containerView.getPaddingRight() > 0) {
                    flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
            }
            containerView.setSystemUiVisibility(flags);
        }
    }

    private static class ActionBarToggleParams {

        public static final ActionBarToggleParams DEFAULT = new ActionBarToggleParams();

        public int animationDuration = 200;
        public Interpolator animationInterpolator;
        public boolean enableStatusBarAnimation = true;
        public boolean enableTranslationAnimation = true;

        public ActionBarToggleParams() {
        }

        public ActionBarToggleParams enableStatusBarAnimation(boolean val) {
            enableStatusBarAnimation = val;
            return this;
        }

        public ActionBarToggleParams enableTranslationAnimation(boolean val) {
            enableTranslationAnimation = val;
            return this;
        }

        public ActionBarToggleParams animationDuration(int val) {
            animationDuration = val;
            return this;
        }

        public ActionBarToggleParams animationInterpolator(Interpolator val) {
            animationInterpolator = val;
            return this;
        }
    }

    private void toggleActionBar(final boolean show, final boolean animated) {
        toggleActionBar(show, animated, ActionBarToggleParams.DEFAULT);
    }

    private void toggleActionBar(final boolean show, final boolean animated, @NonNull final ActionBarToggleParams params) {
        if (actionBarAnimator != null) {
            actionBarAnimator.cancel();
        }
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
            if (bottomLayout.getTag() != null) {
                bottomLayout.setVisibility(View.VISIBLE);
            }
            if (captionTextViewSwitcher.getTag() != null) {
                captionTextViewSwitcher.setVisibility(View.VISIBLE);
                if (videoPreviewFrame != null) {
                    videoPreviewFrame.requestLayout();
                }
            }
        }
        isActionBarVisible = show;

        if (params.enableStatusBarAnimation) {
            updateContainerFlags(show);
        }

        if (videoPlayerControlVisible && isPlaying && show) {
            scheduleActionBarHide();
        } else {
            AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
        }

        if (!show) {
            Bulletin.hide(containerView);
        }

        final float offsetY = AndroidUtilities.dpf2(24);

        videoPlayerControlFrameLayout.setSeekBarTransitionEnabled(params.enableTranslationAnimation && playerLooping);
        videoPlayerControlFrameLayout.setTranslationYAnimationEnabled(params.enableTranslationAnimation);

        if (animated) {
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f));
            if (params.enableTranslationAnimation) {
                arrayList.add(ObjectAnimator.ofFloat(actionBar, View.TRANSLATION_Y, show ? 0 : -offsetY));
            } else {
                actionBar.setTranslationY(0);
            }
            if (allowShowFullscreenButton) {
                arrayList.add(ObjectAnimator.ofFloat(fullscreenButton[0], View.ALPHA, show ? 1.0f : 0.0f));
            }
            for (int a = 1; a < 3; a++) {
                fullscreenButton[a].setTranslationY(show ? 0 : offsetY);
            }
            if (params.enableTranslationAnimation) {
                arrayList.add(ObjectAnimator.ofFloat(fullscreenButton[0], View.TRANSLATION_Y, show ? 0 : offsetY));
            } else {
                fullscreenButton[0].setTranslationY(0);
            }
            if (bottomLayout != null) {
                arrayList.add(ObjectAnimator.ofFloat(bottomLayout, View.ALPHA, show ? 1.0f : 0.0f));
                if (params.enableTranslationAnimation) {
                    arrayList.add(ObjectAnimator.ofFloat(bottomLayout, View.TRANSLATION_Y, show ? 0 : offsetY));
                } else {
                    bottomLayout.setTranslationY(0);
                }
            }
            if (videoPlayerControlVisible) {
                arrayList.add(ObjectAnimator.ofFloat(videoPlayerControlFrameLayout, VPC_PROGRESS, show ? 1.0f : 0.0f));
            } else {
                videoPlayerControlFrameLayout.setProgress(show ? 1.0f : 0.0f);
            }
            arrayList.add(ObjectAnimator.ofFloat(groupedPhotosListView, View.ALPHA, show ? 1.0f : 0.0f));
            if (params.enableTranslationAnimation) {
                arrayList.add(ObjectAnimator.ofFloat(groupedPhotosListView, View.TRANSLATION_Y, show ? 0 : offsetY));
            } else {
                groupedPhotosListView.setTranslationY(0);
            }
            if (!needCaptionLayout && captionScrollView != null) {
                arrayList.add(ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, show ? 1.0f : 0.0f));
                if (params.enableTranslationAnimation) {
                    arrayList.add(ObjectAnimator.ofFloat(captionScrollView, View.TRANSLATION_Y, show ? 0 : offsetY));
                } else {
                    captionScrollView.setTranslationY(0);
                }
            }
            if (videoPlayerControlVisible && isPlaying) {
                final ValueAnimator anim = ValueAnimator.ofFloat(photoProgressViews[0].animAlphas[1], show ? 1.0f : 0.0f);
                anim.addUpdateListener(a -> photoProgressViews[0].setIndexedAlpha(1, (float) a.getAnimatedValue(), false));
                arrayList.add(anim);
            }
            if (muteItem.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(muteItem, View.ALPHA, show ? 1.0f : 0.0f));
            }
            actionBarAnimator = new AnimatorSet();
            actionBarAnimator.playTogether(arrayList);
            actionBarAnimator.setDuration(params.animationDuration);
            actionBarAnimator.setInterpolator(params.animationInterpolator);
            actionBarAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(actionBarAnimator)) {
                        if (!show) {
                            actionBar.setVisibility(View.INVISIBLE);
                            if (bottomLayout.getTag() != null) {
                                bottomLayout.setVisibility(View.INVISIBLE);
                            }
                            if (captionTextViewSwitcher.getTag() != null) {
                                captionTextViewSwitcher.setVisibility(View.INVISIBLE);
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
            if (fullscreenButton[0].getTranslationX() != 0) {
                if (allowShowFullscreenButton) {
                    fullscreenButton[0].setAlpha(show ? 1.0f : 0.0f);
                }
            }
            for (int a = 0; a < 3; a++) {
                fullscreenButton[a].setTranslationY(show ? 0 : offsetY);
            }
            actionBar.setTranslationY(show ? 0 : -offsetY);
            bottomLayout.setAlpha(show ? 1.0f : 0.0f);
            bottomLayout.setTranslationY(show ? 0 : offsetY);
            groupedPhotosListView.setAlpha(show ? 1.0f : 0.0f);
            groupedPhotosListView.setTranslationY(show ? 0 : offsetY);
            if (!needCaptionLayout && captionScrollView != null) {
                captionScrollView.setAlpha(show ? 1.0f : 0.0f);
                captionScrollView.setTranslationY(show ? 0 : offsetY);
            }
            videoPlayerControlFrameLayout.setProgress(show ? 1.0f : 0.0f);
            if (muteItem.getTag() != null) {
                muteItem.setAlpha(show ? 1.0f : 0.0f);
            }
            if (videoPlayerControlVisible && isPlaying) {
                photoProgressViews[0].setIndexedAlpha(1, show ? 1f : 0f, false);
            }
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
            arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, View.ALPHA, show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(selectedPhotosListView, View.TRANSLATION_Y, show ? 0 : -AndroidUtilities.dp(10)));
            arrayList.add(ObjectAnimator.ofFloat(photosCounterView, View.ROTATION_X, show ? 1.0f : 0.0f));
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

    private void toggleVideoPlayer() {
        if (videoPlayer == null) {
            return;
        }
        cancelVideoPlayRunnable();
        AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
        if (isPlaying) {
            videoPlayer.pause();
        } else {
            if (isCurrentVideo) {
                if (Math.abs(videoTimelineView.getProgress() - videoTimelineView.getRightProgress()) < 0.01f || videoPlayer.getCurrentPosition() == videoPlayer.getDuration()) {
                    videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
                }
            } else {
                if (Math.abs(videoPlayerSeekbar.getProgress() - videoTimelineView.getRightProgress()) < 0.01f || videoPlayer.getCurrentPosition() == videoPlayer.getDuration()) {
                    videoPlayer.seekTo(0);
                }
                scheduleActionBarHide();
            }
            videoPlayer.play();
        }
        containerView.invalidate();
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
                ImageLocation location = imagesArrLocations.get(index);
                ImageLocation videoLocation = imagesArrLocationsVideo.get(index);
                if (location == null) {
                    return null;
                }
                if (videoLocation != location) {
                    return videoLocation.location.volume_id + "_" + videoLocation.location.local_id + ".mp4";
                } else {
                    return location.location.volume_id + "_" + location.location.local_id + ".jpg";
                }
            } else {
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
                    return Utilities.MD5(botInlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(botInlineResult.content.url, FileLoader.getMimeTypePart(botInlineResult.content.mime_type));
                }
            }
        } else if (pageBlocksAdapter != null) {
            return pageBlocksAdapter.getFileName(index);
        }
        return null;
    }

    private ImageLocation getImageLocation(int index, int[] size) {
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
            return ImageLocation.getForSecureDocument(secureDocuments.get(index));
        } else if (!imagesArrLocations.isEmpty()) {
            if (index >= imagesArrLocations.size()) {
                return null;
            }
            if (size != null) {
                size[0] = imagesArrLocationsSizes.get(index);
            }
            return imagesArrLocationsVideo.get(index);
        } else if (!imagesArr.isEmpty()) {
            if (index >= imagesArr.size()) {
                return null;
            }
            MessageObject message = imagesArr.get(index);
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    return null;
                } else {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        if (size != null) {
                            size[0] = sizeFull.size;
                            if (size[0] == 0) {
                                size[0] = -1;
                            }
                        }
                        return ImageLocation.getForObject(sizeFull, message.photoThumbsObject);
                    } else if (size != null) {
                        size[0] = -1;
                    }
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo != null || message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage != null) {
                if (message.isGif()) {
                    return ImageLocation.getForDocument(message.getDocument());
                } else {
                    TLRPC.FileLocation location;
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
                    if (sizeFull != null) {
                        if (size != null) {
                            size[0] = sizeFull.size;
                            if (size[0] == 0) {
                                size[0] = -1;
                            }
                        }
                        return ImageLocation.getForObject(sizeFull, message.photoThumbsObject);
                    } else if (size != null) {
                        size[0] = -1;
                    }
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                return ImageLocation.getForWebFile(WebFile.createWithWebDocument(((TLRPC.TL_messageMediaInvoice) message.messageOwner.media).photo));
            } else if (message.getDocument() != null) {
                TLRPC.Document document = message.getDocument();
                if (sharedMediaType == MediaDataController.MEDIA_GIF) {
                    return ImageLocation.getForDocument(document);
                } else if (MessageObject.isDocumentHasThumb(message.getDocument())) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    if (size != null) {
                        size[0] = thumb.size;
                        if (size[0] == 0) {
                            size[0] = -1;
                        }
                    }
                    return ImageLocation.getForDocument(thumb, document);
                }
            }
        }
        return null;
    }

    private TLObject getFileLocation(int index, int[] size) {
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
            return imagesArrLocationsVideo.get(index).location;
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
                        return sizeFull;
                    } else if (size != null) {
                        size[0] = -1;
                    }
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo != null || message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage != null) {
                TLRPC.FileLocation location;
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
                if (sizeFull != null) {
                    if (size != null) {
                        size[0] = sizeFull.size;
                        if (size[0] == 0) {
                            size[0] = -1;
                        }
                    }
                    return sizeFull;
                } else if (size != null) {
                    size[0] = -1;
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                return ((TLRPC.TL_messageMediaInvoice) message.messageOwner.media).photo;
            } else if (message.getDocument() != null && MessageObject.isDocumentHasThumb(message.getDocument())) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(message.getDocument().thumbs, 90);
                if (size != null) {
                    size[0] = thumb.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                }
                return thumb;
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

    private boolean isCurrentAvatarSet() {
        if (currentAvatarLocation == null || currentIndex < 0 || currentIndex >= avatarsArr.size()) {
            return false;
        }
        TLRPC.Photo photo = avatarsArr.get(currentIndex);
        ImageLocation currentLocation = imagesArrLocations.get(currentIndex);
        if (photo instanceof TLRPC.TL_photoEmpty) {
            photo = null;
        }
        if (photo != null) {
            for (int a = 0, N = photo.sizes.size(); a < N; a++) {
                TLRPC.PhotoSize size = photo.sizes.get(a);
                if (size.location != null && size.location.local_id == currentAvatarLocation.location.local_id && size.location.volume_id == currentAvatarLocation.location.volume_id) {
                    return true;
                }
            }
        } else if (currentLocation.location.local_id == currentAvatarLocation.location.local_id && currentLocation.location.volume_id == currentAvatarLocation.location.volume_id) {
            return true;
        }
        return false;
    }

    private void setItemVisible(View itemView, boolean visible, boolean animate) {
        setItemVisible(itemView, visible, animate, 1.0f);
    }

    private void setItemVisible(View itemView, boolean visible, boolean animate, float maxAlpha) {
        Boolean visibleNow = actionBarItemsVisibility.get(itemView);
        if (visibleNow == null || visibleNow != visible) {
            actionBarItemsVisibility.put(itemView, visible);
            itemView.animate().cancel();
            final float alpha = (visible ? 1f : 0f) * maxAlpha;
            if (animate && visibleNow != null) {
                if (visible) {
                    itemView.setVisibility(View.VISIBLE);
                }
                itemView.animate().alpha(alpha).setDuration(100).setInterpolator(new LinearInterpolator()).withEndAction(() -> {
                    if (!visible) {
                        itemView.setVisibility(View.GONE);
                    }
                }).start();
            } else {
                itemView.setVisibility(visible ? View.VISIBLE : View.GONE);
                itemView.setAlpha(alpha);
            }
        }
    }

    private void onPhotoShow(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, ImageLocation imageLocation, ImageLocation videoLocation, final ArrayList<MessageObject> messages, final ArrayList<SecureDocument> documents, final List<Object> photos, int index, final PlaceProviderObject object) {
        classGuid = ConnectionsManager.generateClassGuid();
        currentMessageObject = null;
        currentFileLocation = null;
        currentFileLocationVideo = null;
        currentSecureDocument = null;
        currentPathObject = null;
        currentPageBlock = null;
        fromCamera = false;
        currentBotInlineResult = null;
        avatarStartProgress = 0.0f;
        avatarStartTime = 0;
        currentIndex = -1;
        currentFileNames[0] = null;
        currentFileNames[1] = null;
        currentFileNames[2] = null;
        avatarsDialogId = 0;
        canEditAvatar = false;
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
        playerAutoStarted = false;
        isCurrentVideo = false;
        shownControlsByEnd = false;
        imagesArr.clear();
        imagesArrLocations.clear();
        imagesArrLocationsSizes.clear();
        imagesArrLocationsVideo.clear();
        imagesArrMessages.clear();
        avatarsArr.clear();
        secureDocuments.clear();
        imagesArrLocals.clear();
        for (int a = 0; a < 2; a++) {
            imagesByIds[a].clear();
            imagesByIdsTemp[a].clear();
        }
        imagesArrTemp.clear();
        currentAvatarLocation = null;
        containerView.setPadding(0, 0, 0, 0);
        if (currentThumb != null) {
            currentThumb.release();
        }
        if (videoTimelineView != null) {
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                videoTimelineView.setBackground(null);
            } else {
                videoTimelineView.setBackgroundColor(0x7f000000);
            }
        }
        currentThumb = object != null ? object.thumb : null;
        isEvent = object != null && object.isEvent;
        sharedMediaType = MediaDataController.MEDIA_PHOTOVIDEO;
        allMediaItem.setText(LocaleController.getString("ShowAllMedia", R.string.ShowAllMedia));
        setItemVisible(sendItem, false, false);
        setItemVisible(pipItem, false, true);
        cameraItem.setVisibility(View.GONE);
        cameraItem.setTag(null);
        bottomLayout.setVisibility(View.VISIBLE);
        bottomLayout.setTag(1);
        bottomLayout.setTranslationY(0);
        captionTextViewSwitcher.setTranslationY(0);
        bottomButtonsLayout.setVisibility(View.GONE);
        paintButton.setVisibility(View.GONE);
        if (qualityChooseView != null) {
            qualityChooseView.setVisibility(View.INVISIBLE);
            qualityPicker.setVisibility(View.INVISIBLE);
            qualityChooseView.setTag(null);
        }
        if (qualityChooseViewAnimation != null) {
            qualityChooseViewAnimation.cancel();
            qualityChooseViewAnimation = null;
        }
        setDoubleTapEnabled(true);
        allowShare = false;
        slideshowMessageId = 0;
        nameOverride = null;
        dateOverride = 0;
        menuItem.hideSubItem(gallery_menu_showall);
        menuItem.hideSubItem(gallery_menu_showinchat);
        menuItem.hideSubItem(gallery_menu_share);
        menuItem.hideSubItem(gallery_menu_openin);
        menuItem.hideSubItem(gallery_menu_savegif);
        menuItem.hideSubItem(gallery_menu_masks2);
        menuItem.hideSubItem(gallery_menu_edit_avatar);
        menuItem.hideSubItem(gallery_menu_set_as_main);
        menuItem.hideSubItem(gallery_menu_delete);
        menuItem.hideSubItem(gallery_menu_speed);
        speedGap.setVisibility(View.GONE);
        actionBar.setTranslationY(0);

        checkImageView.setAlpha(1.0f);
        checkImageView.setTranslationY(0.0f);
        checkImageView.setVisibility(View.GONE);
        actionBar.setTitleRightMargin(0);
        photosCounterView.setAlpha(1.0f);
        photosCounterView.setTranslationY(0.0f);
        photosCounterView.setVisibility(View.GONE);

        pickerView.setVisibility(View.GONE);
        pickerViewSendButton.setVisibility(View.GONE);
        pickerViewSendButton.setTranslationY(0);
        pickerView.setAlpha(1.0f);
        pickerViewSendButton.setAlpha(1.0f);
        pickerView.setTranslationY(0);

        paintItem.setVisibility(View.GONE);
        paintItem.setTag(null);
        cropItem.setVisibility(View.GONE);
        tuneItem.setVisibility(View.GONE);
        tuneItem.setTag(null);
        timeItem.setVisibility(View.GONE);
        rotateItem.setVisibility(View.GONE);
        mirrorItem.setVisibility(View.GONE);

        pickerView.getLayoutParams().height = LayoutHelper.WRAP_CONTENT;
        docInfoTextView.setVisibility(View.GONE);
        docNameTextView.setVisibility(View.GONE);
        showVideoTimeline(false, false);
        videoAvatarTooltip.setVisibility(View.GONE);
        compressItem.setVisibility(View.GONE);
        captionEditText.setVisibility(View.GONE);
        mentionListView.setVisibility(View.GONE);
        AndroidUtilities.updateViewVisibilityAnimated(muteItem, false, 1f, false);

        actionBar.setSubtitle(null);
        setItemVisible(masksItem, false, true);
        shareItem.setVisibility(View.GONE);
        muteVideo = false;
        muteItem.setImageResource(R.drawable.video_send_unmute);
        editorDoneLayout.setVisibility(View.GONE);
        captionTextViewSwitcher.setTag(null);
        captionTextViewSwitcher.setVisibility(View.INVISIBLE);
        if (photoCropView != null) {
            photoCropView.setVisibility(View.GONE);
        }
        if (photoFilterView != null) {
            photoFilterView.setVisibility(View.GONE);
        }
        for (int a = 0; a < 3; a++) {
            if (photoProgressViews[a] != null) {
                photoProgressViews[a].setBackgroundState(PROGRESS_NONE, false, true);
            }
        }
        if (groupedPhotosListView != null) {
            groupedPhotosListView.reset();
            groupedPhotosListView.setAnimateBackground(!ApplicationLoader.isNetworkOnline());
        }

        if (placeProvider != null && placeProvider.getTotalImageCount() > 0) {
            totalImagesCount = placeProvider.getTotalImageCount();
        }

        if (messageObject != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("playback_speed", Activity.MODE_PRIVATE);
            currentVideoSpeed = preferences.getFloat("speed" + messageObject.getDialogId() + "_" + messageObject.getId(), 1.0f);
        } else {
            currentVideoSpeed = 1.0f;
        }
        setMenuItemIcon();

        boolean noforwards = messageObject != null && (MessagesController.getInstance(currentAccount).isChatNoForwards(messageObject.getChatId()) || (messageObject.messageOwner != null && messageObject.messageOwner.noforwards));
        if (messageObject != null && messages == null) {
            if (messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null) {
                TLRPC.WebPage webPage = messageObject.messageOwner.media.webpage;
                String siteName = webPage.site_name;
                if (siteName != null) {
                    siteName = siteName.toLowerCase();
                    if (siteName.equals("instagram") || siteName.equals("twitter") || "telegram_album".equals(webPage.type)) {
                        if (!TextUtils.isEmpty(webPage.author)) {
                            nameOverride = webPage.author;
                        }
                        if (webPage.cached_page instanceof TLRPC.TL_page) {
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
                            setImageIndex(idx);
                        }
                    }
                }
            }
            if (messageObject.canPreviewDocument()) {
                sharedMediaType = MediaDataController.MEDIA_FILE;
                allMediaItem.setText(LocaleController.getString("ShowAllFiles", R.string.ShowAllFiles));
            } else if (messageObject.isGif()) {
                sharedMediaType = MediaDataController.MEDIA_GIF;
                allMediaItem.setText(LocaleController.getString("ShowAllGIFs", R.string.ShowAllGIFs));
            }
            if (isEmbedVideo) {
                bottomLayout.setTag(null);
                bottomLayout.setVisibility(View.GONE);
            }
            if (slideshowMessageId == 0) {
                imagesArr.add(messageObject);
                if (messageObject.eventId != 0) {
                    needSearchImageInArr = false;
                } else if (currentAnimation != null) {
                    needSearchImageInArr = false;
                    if (messageObject.canForwardMessage() && !noforwards) {
                        setItemVisible(sendItem, true, false);
                    }
                } else if (!messageObject.scheduled && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)) {
                    needSearchImageInArr = true;
                    imagesByIds[0].put(messageObject.getId(), messageObject);
                    if (parentChatActivity == null || !parentChatActivity.isThreadChat()) {
                        menuItem.showSubItem(gallery_menu_showinchat);
                        menuItem.showSubItem(gallery_menu_showall);
                    }
                    setItemVisible(sendItem, !noforwards, false);
                } else if (isEmbedVideo && messageObject.eventId == 0) {
                    setItemVisible(sendItem, true, false);
                }
                setImageIndex(0);
            }
        } else if (documents != null) {
            secureDocuments.addAll(documents);
            setImageIndex(index);
        } else if (fileLocation != null) {
            avatarsDialogId = object != null ? object.dialogId : 0;
            canEditAvatar = object != null && object.canEdit;
            if (imageLocation == null && avatarsDialogId != 0) {
                if (avatarsDialogId > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(avatarsDialogId);
                    imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-avatarsDialogId);
                    imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
                }
            }
            if (imageLocation == null) {
                return;
            }
            imagesArrLocations.add(imageLocation);
            imagesArrLocationsVideo.add(videoLocation != null ? videoLocation : imageLocation);
            currentAvatarLocation = imageLocation;
            imagesArrLocationsSizes.add(object != null ? object.size : 0);
            imagesArrMessages.add(null);
            avatarsArr.add(new TLRPC.TL_photoEmpty());
            bottomButtonsLayout.setVisibility(!videoPlayerControlVisible ? View.VISIBLE : View.GONE);
            allowShare = true;
            menuItem.hideSubItem(gallery_menu_showall);
            if (bottomButtonsLayout.getVisibility() == View.VISIBLE) {
                menuItem.hideSubItem(gallery_menu_share);
            } else {
                menuItem.showSubItem(gallery_menu_share);
            }
            setImageIndex(0);
        } else if (messages != null) {
            imagesArr.addAll(messages);
            for (int a = 0; a < imagesArr.size(); a++) {
                MessageObject message = imagesArr.get(a);
                imagesByIds[message.getDialogId() == currentDialogId ? 0 : 1].put(message.getId(), message);
            }
            MessageObject openingObject = imagesArr.get(index);
            if (!openingObject.scheduled && (parentChatActivity == null || !parentChatActivity.isThreadChat())) {
                opennedFromMedia = true;
                if (object != null) {
                    startOffset = object.starOffset;
                }
                menuItem.showSubItem(gallery_menu_showinchat);
                if (openingObject.canForwardMessage() && !noforwards) {
                    setItemVisible(sendItem, true, false);
                }
                if (openingObject.canPreviewDocument()) {
                    sharedMediaType = MediaDataController.MEDIA_FILE;
                    allMediaItem.setText(LocaleController.getString("ShowAllFiles", R.string.ShowAllFiles));
                } else if (openingObject.isGif()) {
                    sharedMediaType = MediaDataController.MEDIA_GIF;
                    allMediaItem.setText(LocaleController.getString("ShowAllGIFs", R.string.ShowAllGIFs));
                }
            } else {
                totalImagesCount = imagesArr.size();
            }
            setImageIndex(index);
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
                if (sendPhotoType == SELECT_TYPE_QR) {
                    cropItem.setVisibility(View.GONE);
                    rotateItem.setVisibility(View.GONE);
                    mirrorItem.setVisibility(View.GONE);
                } else if (isDocumentsPicker) {
                    cropItem.setVisibility(View.GONE);
                    rotateItem.setVisibility(View.GONE);
                    mirrorItem.setVisibility(View.GONE);
                    docInfoTextView.setVisibility(View.VISIBLE);
                    docNameTextView.setVisibility(View.VISIBLE);
                    pickerView.getLayoutParams().height = AndroidUtilities.dp(84);
                } else if (((MediaController.PhotoEntry) obj).isVideo) {
                    cropItem.setVisibility(View.GONE);
                    rotateItem.setVisibility(View.GONE);
                    mirrorItem.setVisibility(View.GONE);
                    bottomLayout.setVisibility(View.VISIBLE);
                    bottomLayout.setTag(1);
                    bottomLayout.setTranslationY(-AndroidUtilities.dp(48));
                } else {
                    cropItem.setVisibility(sendPhotoType != SELECT_TYPE_AVATAR ? View.VISIBLE : View.GONE);
                    rotateItem.setVisibility(sendPhotoType != SELECT_TYPE_AVATAR ? View.GONE : View.VISIBLE);
                    mirrorItem.setVisibility(sendPhotoType != SELECT_TYPE_AVATAR ? View.GONE : View.VISIBLE);
                }
                allowCaption = !isDocumentsPicker;
            } else if (obj instanceof TLRPC.BotInlineResult) {
                cropItem.setVisibility(View.GONE);
                rotateItem.setVisibility(View.GONE);
                mirrorItem.setVisibility(View.GONE);
                allowCaption = false;
            } else {
                cropItem.setVisibility(obj instanceof MediaController.SearchImage && ((MediaController.SearchImage) obj).type == 0 ? View.VISIBLE : View.GONE);
                rotateItem.setVisibility(View.GONE);
                mirrorItem.setVisibility(View.GONE);
                allowCaption = cropItem.getVisibility() == View.VISIBLE;
            }
            if (parentChatActivity != null) {
                mentionsAdapter.setChatInfo(parentChatActivity.chatInfo);
                mentionsAdapter.setNeedUsernames(parentChatActivity.currentChat != null);
                mentionsAdapter.setNeedBotContext(false);
                needCaptionLayout = allowCaption && (placeProvider == null || placeProvider.allowCaption());
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
            setImageIndex(index);
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                paintItem.setVisibility(View.VISIBLE);
                tuneItem.setVisibility(View.VISIBLE);
            } else if (sendPhotoType != 4 && sendPhotoType != 5) {
                paintItem.setVisibility(paintItem.getTag() != null ? View.VISIBLE : View.GONE);
                tuneItem.setVisibility(tuneItem.getTag() != null ? View.VISIBLE : View.GONE);
            } else {
                paintItem.setVisibility(View.GONE);
                tuneItem.setVisibility(View.GONE);
            }
            updateSelectedCount();
        } else {
            setImageIndex(index);
        }

        if (currentAnimation == null && !isEvent) {
            if (currentDialogId != 0 && totalImagesCount == 0 && currentMessageObject != null && !currentMessageObject.scheduled) {
                if (MediaDataController.getMediaType(currentMessageObject.messageOwner) == sharedMediaType) {
                    MediaDataController.getInstance(currentAccount).getMediaCount(currentDialogId, sharedMediaType, classGuid, true);
                    if (mergeDialogId != 0) {
                        MediaDataController.getInstance(currentAccount).getMediaCount(mergeDialogId, sharedMediaType, classGuid, true);
                    }
                }
            } else if (avatarsDialogId != 0) {
                MessagesController.getInstance(currentAccount).loadDialogPhotos(avatarsDialogId, 80, 0, true, classGuid);
            }
        }
        if (currentMessageObject != null && currentMessageObject.isVideo() || currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document)) || pageBlocksAdapter != null && pageBlocksAdapter.isVideo(index)) {
            playerAutoStarted = true;
            onActionClick(false);
        } else if (!imagesArrLocals.isEmpty()) {
            Object entry = imagesArrLocals.get(index);
            CharSequence caption = null;
            TLRPC.User user = parentChatActivity != null ? parentChatActivity.getCurrentUser() : null;
            boolean allowTimeItem = !isDocumentsPicker && parentChatActivity != null && !parentChatActivity.isSecretChat() && !parentChatActivity.isInScheduleMode() && user != null && !user.bot && !UserObject.isUserSelf(user) && !parentChatActivity.isEditingMessageMedia();
            if (placeProvider != null && placeProvider.getEditingMessageObject() != null) {
                allowTimeItem = false;
            }
            if (entry instanceof TLRPC.BotInlineResult) {
                allowTimeItem = false;
            } else if (entry instanceof MediaController.PhotoEntry) {

            } else if (allowTimeItem && entry instanceof MediaController.SearchImage) {
                allowTimeItem = ((MediaController.SearchImage) entry).type == 0;
            }
            if (allowTimeItem) {
                timeItem.setVisibility(View.VISIBLE);
            }
        }
        checkFullscreenButton();
    }

    private boolean canSendMediaToParentChatActivity() {
        return parentChatActivity != null && (parentChatActivity.currentUser != null || parentChatActivity.currentChat != null && !ChatObject.isNotInChat(parentChatActivity.currentChat) && ChatObject.canSendMedia(parentChatActivity.currentChat));
    }

    private void setDoubleTapEnabled(boolean value) {
        doubleTapEnabled = value;
        gestureDetector.setOnDoubleTapListener(value ? this : null);
    }

    private void setImages() {
        if (animationInProgress == 0) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(rightImage, currentIndex + 1);
            setIndexToImage(leftImage, currentIndex - 1);
        }
    }

    private void setIsAboutToSwitchToIndex(int index, boolean init, boolean animated) {
        if (!init && switchingToIndex == index) {
            return;
        }
        boolean animateCaption = animated;
        switchingToIndex = index;

        boolean isVideo = false;
        CharSequence caption = null;
        String newFileName = getFileName(index);
        MessageObject newMessageObject = null;

        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionSet transitionSet = new TransitionSet();
            transitionSet.addTransition(new Fade());
            transitionSet.addTransition(new ChangeBounds());
            transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transitionSet.setDuration(220);
            transitionSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
            TransitionManager.beginDelayedTransition(itemsLayout, transitionSet);

        }

        if (!imagesArr.isEmpty()) {
            if (switchingToIndex < 0 || switchingToIndex >= imagesArr.size()) {
                return;
            }
            newMessageObject = imagesArr.get(switchingToIndex);
            isVideo = newMessageObject.isVideo();
            boolean isInvoice = newMessageObject.isInvoice();
            boolean noforwards = MessagesController.getInstance(currentAccount).isChatNoForwards(newMessageObject.getChatId()) || (newMessageObject.messageOwner != null && newMessageObject.messageOwner.noforwards);
            if (isInvoice) {
                setItemVisible(masksItem, false, true);
                menuItem.hideSubItem(gallery_menu_delete);
                menuItem.hideSubItem(gallery_menu_openin);
                caption = newMessageObject.messageOwner.media.description;
                allowShare = false;
                bottomLayout.setTranslationY(AndroidUtilities.dp(48));
                captionTextViewSwitcher.setTranslationY(AndroidUtilities.dp(48));
                nameTextView.setText("");
                dateTextView.setText("");
            } else {
                allowShare = !noforwards;
                if (newMessageObject.isNewGif() && allowShare) {
                    menuItem.showSubItem(gallery_menu_savegif);
                }
                if (newMessageObject.canDeleteMessage(parentChatActivity != null && parentChatActivity.isInScheduleMode(), null) && slideshowMessageId == 0) {
                    menuItem.showSubItem(gallery_menu_delete);
                } else {
                    menuItem.hideSubItem(gallery_menu_delete);
                }
                menuItem.checkHideMenuItem();
                if (isEmbedVideo) {
                    menuItem.showSubItem(gallery_menu_openin);
                    setItemVisible(pipItem, true, false);
                } else if (isVideo) {
                    if (!noforwards || (slideshowMessageId == 0 ? newMessageObject.messageOwner.media.webpage != null && newMessageObject.messageOwner.media.webpage.url != null :
                            imagesArr.get(0).messageOwner.media.webpage != null && imagesArr.get(0).messageOwner.media.webpage.url != null)) {
                        menuItem.showSubItem(gallery_menu_openin);
                    } else {
                        menuItem.hideSubItem(gallery_menu_openin);
                    }
                    final boolean masksItemVisible = masksItem.getVisibility() == View.VISIBLE;
                    if (masksItemVisible) {
                        setItemVisible(masksItem, false, false);
                    }
                    if (!pipAvailable) {
                        pipItem.setEnabled(false);
                        setItemVisible(pipItem, true, !masksItemVisible, 0.5f);
                    } else {
                        setItemVisible(pipItem, true, !masksItemVisible);
                    }
                    if (newMessageObject.hasAttachedStickers() && !DialogObject.isEncryptedDialog(newMessageObject.getDialogId())) {
                        menuItem.showSubItem(gallery_menu_masks2);
                    } else {
                        menuItem.hideSubItem(gallery_menu_masks2);
                    }
                    menuItem.checkHideMenuItem();
                } else {
                    speedGap.setVisibility(View.GONE);
                    menuItem.hideSubItem(gallery_menu_openin);
                    menuItem.checkHideMenuItem();
                    final boolean pipItemVisible = pipItem.getVisibility() == View.VISIBLE;
                    final boolean shouldMasksItemBeVisible = newMessageObject.hasAttachedStickers() && !DialogObject.isEncryptedDialog(newMessageObject.getDialogId());
                    if (pipItemVisible) {
                        setItemVisible(pipItem, false, !shouldMasksItemBeVisible);
                    }
                    setItemVisible(masksItem, shouldMasksItemBeVisible, !pipItemVisible);
                }
                final boolean shouldAutoPlayed = shouldMessageObjectAutoPlayed(newMessageObject);
                if (!shouldAutoPlayed && TextUtils.isEmpty(placeProvider.getTitleFor(switchingToIndex))) {
                    final boolean animatedLocal = !playerWasPlaying;
                    if (nameOverride != null) {
                        nameTextView.setText(nameOverride);
                    } else {
                        if (newMessageObject.messageOwner.fwd_from != null && newMessageObject.messageOwner.fwd_from.from_name != null) {
                            nameTextView.setText(newMessageObject.messageOwner.fwd_from.from_name, animatedLocal);
                        } else if (newMessageObject.isFromUser()) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(newMessageObject.messageOwner.from_id.user_id);
                            if (user != null) {
                                nameTextView.setText(UserObject.getUserName(user), animatedLocal);
                            } else {
                                nameTextView.setText("", animatedLocal);
                            }
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-newMessageObject.getSenderId());
                            if (ChatObject.isChannel(chat) && chat.megagroup && newMessageObject.isForwardedChannelPost()) {
                                chat = MessagesController.getInstance(currentAccount).getChat(newMessageObject.messageOwner.fwd_from.from_id.channel_id);
                            }
                            if (chat != null) {
                                nameTextView.setText(chat.title, animatedLocal);
                            } else {
                                nameTextView.setText("", animatedLocal);
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
                        dateTextView.setText(String.format("%s (%s)", dateString, AndroidUtilities.formatFileSize(newMessageObject.getDocument().size)), animated);
                    } else {
                        dateTextView.setText(dateString, animatedLocal);
                    }
                } else if (!TextUtils.isEmpty(placeProvider.getTitleFor(switchingToIndex))) {
                    nameTextView.setText("");
                    dateTextView.setText("");
                }
                String restrictionReason = MessagesController.getRestrictionReason(newMessageObject.messageOwner.restriction_reason);
                if (!TextUtils.isEmpty(restrictionReason)) {
                    caption = restrictionReason;
                } else {
                    caption = newMessageObject.caption;
                }
            }

            if (currentAnimation != null) {
                menuItem.hideSubItem(gallery_menu_save);
                menuItem.hideSubItem(gallery_menu_share);
                if (!newMessageObject.canDeleteMessage(parentChatActivity != null && parentChatActivity.isInScheduleMode(), null)) {
                    menuItem.hideSubItem(gallery_menu_delete);
                }
                allowShare = !noforwards;
                bottomButtonsLayout.setVisibility(View.VISIBLE);
                paintButton.setVisibility(View.GONE);
                shareItem.setVisibility(allowShare ? View.VISIBLE : View.GONE);
                shareButton.setVisibility(allowShare && shareItem.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
                actionBar.setTitle(LocaleController.getString("AttachGif", R.string.AttachGif));
            } else {
                if (totalImagesCount + totalImagesCountMerge != 0 && !needSearchImageInArr) {
                    if (opennedFromMedia) {
                        if (startOffset + imagesArr.size() < totalImagesCount + totalImagesCountMerge && !loadingMoreImages && switchingToIndex > imagesArr.size() - 5) {
                            int loadFromMaxId = imagesArr.isEmpty() ? 0 : imagesArr.get(imagesArr.size() - 1).getId();
                            int loadIndex = 0;
                            if (endReached[loadIndex] && mergeDialogId != 0) {
                                loadIndex = 1;
                                if (!imagesArr.isEmpty() && imagesArr.get(imagesArr.size() - 1).getDialogId() != mergeDialogId) {
                                    loadFromMaxId = 0;
                                }
                            }

                            if (!placeProvider.loadMore()) {
                                MediaDataController.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 40, loadFromMaxId, 0, sharedMediaType, 1, classGuid, 0);
                                loadingMoreImages = true;
                            }
                        }

                        if (startOffset > 0 && switchingToIndex < 5 && !imagesArr.isEmpty()) {
                            int loadFromMinId = imagesArr.get(0).getId();
                            int loadIndex = 0;
                            if (!placeProvider.loadMore()) {
                                MediaDataController.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 40, 0, loadFromMinId, sharedMediaType, 1, classGuid, 0);
                                loadingMoreImages = true;
                            }
                        }
                        CharSequence title = placeProvider.getTitleFor(switchingToIndex);
                        if (title != null) {
                            actionBar.setTitle(title);
                            CharSequence subtitle = placeProvider.getSubtitleFor(switchingToIndex);
                            actionBar.setSubtitle(subtitle);
                            actionBar.setTitleScrollNonFitText(true);
                        } else {
                            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, startOffset + switchingToIndex + 1, totalImagesCount + totalImagesCountMerge));
                        }
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

                            MediaDataController.getInstance(currentAccount).loadMedia(loadIndex == 0 ? currentDialogId : mergeDialogId, 80, loadFromMaxId, 0, sharedMediaType, 1, classGuid, 0);
                            loadingMoreImages = true;
                        }
                        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, (totalImagesCount + totalImagesCountMerge - imagesArr.size()) + switchingToIndex + 1, totalImagesCount + totalImagesCountMerge));
                    }
                } else if (slideshowMessageId == 0 && newMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                    if (isEmbedVideo) {
                        actionBar.setTitle("YouTube");
                    } else if (newMessageObject.canPreviewDocument()) {
                        actionBar.setTitle(LocaleController.getString("AttachDocument", R.string.AttachDocument));
                    } else if (newMessageObject.isVideo()) {
                        actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
                    } else {
                        actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
                    }
                } else if (isInvoice) {
                    actionBar.setTitle(newMessageObject.messageOwner.media.title);
                } else if (newMessageObject.isVideo()) {
                    actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
                } else if (newMessageObject.getDocument() != null) {
                    actionBar.setTitle(LocaleController.getString("AttachDocument", R.string.AttachDocument));
                }
                if (DialogObject.isEncryptedDialog(currentDialogId) && !isEmbedVideo || noforwards) {
                    setItemVisible(sendItem, false, false);
                }
                if (isEmbedVideo || newMessageObject.messageOwner.ttl != 0 && newMessageObject.messageOwner.ttl < 60 * 60 || noforwards) {
                    allowShare = false;
                    menuItem.hideSubItem(gallery_menu_save);
                    bottomButtonsLayout.setVisibility(View.GONE);
                    menuItem.hideSubItem(gallery_menu_share);
                } else {
                    allowShare = true;
                    menuItem.showSubItem(gallery_menu_save);
                    boolean canPaint = newMessageObject.getDocument() == null || newMessageObject.canPreviewDocument() || newMessageObject.getMimeType().startsWith("video/");
                    paintButton.setVisibility(canPaint && canSendMediaToParentChatActivity() ? View.VISIBLE : View.GONE);
                    bottomButtonsLayout.setVisibility(!videoPlayerControlVisible ? View.VISIBLE : View.GONE);
                    if (bottomButtonsLayout.getVisibility() == View.VISIBLE) {
                        menuItem.hideSubItem(gallery_menu_share);
                    } else {
                        menuItem.showSubItem(gallery_menu_share);
                    }
                }
            }
            groupedPhotosListView.fillList();
        } else if (!secureDocuments.isEmpty()) {
            allowShare = false;
            menuItem.showSubItem(gallery_menu_delete);
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
            if (canEditAvatar && !avatarsArr.isEmpty()) {
                menuItem.showSubItem(gallery_menu_edit_avatar);
                boolean currentSet = isCurrentAvatarSet();
                if (currentSet) {
                    menuItem.hideSubItem(gallery_menu_set_as_main);
                } else {
                    menuItem.showSubItem(gallery_menu_set_as_main);
                }
                boolean canDelete;
                if (avatarsDialogId > 0) {
                    canDelete = true;
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-avatarsDialogId);
                    canDelete = isCurrentAvatarSet() || MessageObject.canDeleteMessage(currentAccount, false, imagesArrMessages.get(index), chat);
                }
                if (canDelete) {
                    menuItem.showSubItem(gallery_menu_delete);
                } else {
                    menuItem.hideSubItem(gallery_menu_delete);
                }
            } else {
                menuItem.hideSubItem(gallery_menu_edit_avatar);
                menuItem.hideSubItem(gallery_menu_set_as_main);
                menuItem.hideSubItem(gallery_menu_delete);
            }
            if (isEvent) {
                actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
            } else {
                actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, switchingToIndex + 1, imagesArrLocations.size()));
            }
            boolean noforwards = avatarsDialogId != 0 && MessagesController.getInstance(currentAccount).isChatNoForwards(-avatarsDialogId);
            if (noforwards) {
                menuItem.hideSubItem(gallery_menu_save);
            } else {
                menuItem.showSubItem(gallery_menu_save);
            }
            allowShare = !noforwards;
            shareButton.setVisibility(allowShare && shareItem.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);

            bottomButtonsLayout.setVisibility(!videoPlayerControlVisible ? View.VISIBLE : View.GONE);
            if (bottomButtonsLayout.getVisibility() == View.VISIBLE) {
                menuItem.hideSubItem(gallery_menu_share);
            } else {
                menuItem.showSubItem(gallery_menu_share);
            }
            menuItem.checkHideMenuItem();
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
            MediaController.CropState cropState = null;
            if (object instanceof TLRPC.BotInlineResult) {
                TLRPC.BotInlineResult botInlineResult = currentBotInlineResult = ((TLRPC.BotInlineResult) object);
                if (botInlineResult.document != null) {
                    isVideo = MessageObject.isVideoDocument(botInlineResult.document);
                } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                    isVideo = botInlineResult.type.equals("video");
                }
            } else {
                boolean isAnimation = false;
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                    currentPathObject = photoEntry.path;
                    isVideo = photoEntry.isVideo;
                    cropState = photoEntry.cropState;
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                    currentPathObject = searchImage.getPathToAttach();
                    cropState = searchImage.cropState;
                    if (searchImage.type == 1) {
                        isAnimation = true;
                    }
                }
                if (isVideo) {
                    if (!isCurrentVideo) {
                        animateCaption = false;
                    }
                    isCurrentVideo = true;
                    boolean isMuted = false;
                    float start = 0.0f;
                    float end = 1.0f;
                    if (object instanceof MediaController.PhotoEntry) {
                        MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                        if (photoEntry.editedInfo != null) {
                            isMuted = photoEntry.editedInfo.muted;
                            start = photoEntry.editedInfo.start;
                            end = photoEntry.editedInfo.end;
                        }
                    }
                    processOpenVideo(currentPathObject, isMuted, start, end);
                    if (isDocumentsPicker || Build.VERSION.SDK_INT < 18) {
                        showVideoTimeline(false, animated);
                        videoAvatarTooltip.setVisibility(View.GONE);
                        cropItem.setVisibility(View.GONE);
                        cropItem.setTag(null);
                        tuneItem.setVisibility(View.GONE);
                        tuneItem.setTag(null);
                        paintItem.setVisibility(View.GONE);
                        paintItem.setTag(null);
                        rotateItem.setVisibility(View.GONE);
                        rotateItem.setTag(null);
                        mirrorItem.setVisibility(View.GONE);
                        mirrorItem.setTag(null);
                        AndroidUtilities.updateViewVisibilityAnimated(muteItem, false, 1f, animated);
                        compressItem.setVisibility(View.GONE);
                    } else {
                        showVideoTimeline(true, animated);
                        if (sendPhotoType != SELECT_TYPE_AVATAR) {
                            videoAvatarTooltip.setVisibility(View.GONE);
                            cropItem.setVisibility(View.VISIBLE);
                            cropItem.setTag(1);
                            rotateItem.setVisibility(View.GONE);
                            rotateItem.setTag(null);
                            mirrorItem.setVisibility(View.GONE);
                            mirrorItem.setTag(null);
                            AndroidUtilities.updateViewVisibilityAnimated(muteItem, true, 1f, animated);
                            compressItem.setVisibility(View.VISIBLE);
                        } else {
                            videoAvatarTooltip.setVisibility(View.VISIBLE);
                            cropItem.setVisibility(View.GONE);
                            cropItem.setTag(null);
                            rotateItem.setVisibility(View.VISIBLE);
                            rotateItem.setTag(1);
                            mirrorItem.setVisibility(View.VISIBLE);
                            mirrorItem.setTag(1);
                            AndroidUtilities.updateViewVisibilityAnimated(muteItem, false, 1f, animated);
                            compressItem.setVisibility(View.GONE);
                        }
                        tuneItem.setVisibility(View.VISIBLE);
                        tuneItem.setTag(1);
                        paintItem.setVisibility(View.VISIBLE);
                        paintItem.setTag(1);
                    }
                } else {
                    showVideoTimeline(false, animated);
                    videoAvatarTooltip.setVisibility(View.GONE);
                    AndroidUtilities.updateViewVisibilityAnimated(muteItem, false, 1f, animated);
                    if (isCurrentVideo) {
                        animateCaption = false;
                    }
                    isCurrentVideo = false;
                    compressItem.setVisibility(View.GONE);
                    if (isAnimation || sendPhotoType == SELECT_TYPE_QR || isDocumentsPicker) {
                        paintItem.setVisibility(View.GONE);
                        paintItem.setTag(null);
                        cropItem.setVisibility(View.GONE);
                        rotateItem.setVisibility(View.GONE);
                        mirrorItem.setVisibility(View.GONE);
                        tuneItem.setVisibility(View.GONE);
                        tuneItem.setTag(null);
                    } else {
                        if (sendPhotoType == 4 || sendPhotoType == 5) {
                            paintItem.setVisibility(View.GONE);
                            paintItem.setTag(null);
                            tuneItem.setVisibility(View.GONE);
                            tuneItem.setTag(null);
                        } else {
                            paintItem.setVisibility(View.VISIBLE);
                            paintItem.setTag(1);
                            tuneItem.setVisibility(View.VISIBLE);
                            tuneItem.setTag(1);
                        }
                        cropItem.setVisibility(sendPhotoType != SELECT_TYPE_AVATAR ? View.VISIBLE : View.GONE);
                        rotateItem.setVisibility(sendPhotoType != SELECT_TYPE_AVATAR ? View.GONE : View.VISIBLE);
                        mirrorItem.setVisibility(sendPhotoType != SELECT_TYPE_AVATAR ? View.GONE : View.VISIBLE);
                    }
                    if (animated) {
                        actionBar.beginDelayedTransition();
                    }
                    actionBar.setSubtitle(null);
                }
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = ((MediaController.PhotoEntry) object);
                    fromCamera = photoEntry.bucketId == 0 && photoEntry.dateTaken == 0 && imagesArrLocals.size() == 1;
                    if (hasCaptionForAllMedia) {
                        caption = captionForAllMedia;
                    } else {
                        caption = photoEntry.caption;
                    }
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
                        if (user.self) {
                            actionBar.setTitle(LocaleController.getString("SavedMessages", R.string.SavedMessages));
                        } else {
                            actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                        }
                    }
                }
            }
            if (sendPhotoType == 0 || sendPhotoType == 4 || (sendPhotoType == 2 || sendPhotoType == 5) && imagesArrLocals.size() > 1) {
                checkImageView.setChecked(placeProvider.isPhotoChecked(switchingToIndex), false);
            }

            updateCaptionTextForCurrentPhoto(object);
            PorterDuffColorFilter filter = new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingButton), PorterDuff.Mode.MULTIPLY);
            timeItem.setColorFilter(ttl != 0 ?  filter : null);
            paintItem.setColorFilter(isPainted ? filter : null);
            cropItem.setColorFilter(isCropped ? filter : null);
            tuneItem.setColorFilter(isFiltered ? filter : null);
            if (fromCamera) {
                mirrorItem.setColorFilter(cropState != null && (isCurrentVideo && !cropState.mirrored || !isCurrentVideo && cropState.mirrored) ? filter : null);
            } else {
                mirrorItem.setColorFilter(cropState != null && cropState.mirrored ? filter : null);
            }
            rotateItem.setColorFilter(cropState != null && cropState.transformRotation != 0 ? filter : null);
        } else if (pageBlocksAdapter != null) {
            final int size = pageBlocksAdapter.getItemsCount();
            if (switchingToIndex < 0 || switchingToIndex >= size) {
                return;
            }
            allowShare = !MessagesController.getInstance(currentAccount).isChatNoForwards(-currentDialogId);
            TLRPC.PageBlock pageBlock = pageBlocksAdapter.get(switchingToIndex);
            caption = pageBlocksAdapter.getCaption(switchingToIndex);
            isVideo = pageBlocksAdapter.isVideo(switchingToIndex);
            if (isVideo) {
                if (allowShare) {
                    menuItem.showSubItem(gallery_menu_openin);
                } else {
                    menuItem.hideSubItem(gallery_menu_openin);
                }
                menuItem.checkHideMenuItem();

                if (!pipAvailable) {
                    pipItem.setEnabled(false);
                    setItemVisible(pipItem, true, true, 0.5f);
                } else {
                    setItemVisible(pipItem, true, true);
                }
            } else {
                menuItem.hideSubItem(gallery_menu_openin);
                setItemVisible(pipItem, false, true);
            }
            if (bottomLayout.getVisibility() != View.GONE) {
                bottomLayout.setVisibility(View.GONE);
            }
            bottomLayout.setTag(null);

            shareItem.setVisibility(allowShare ? View.VISIBLE : View.GONE);

            if (currentAnimation != null) {
                menuItem.hideSubItem(gallery_menu_save);
                if (allowShare) {
                    menuItem.showSubItem(gallery_menu_savegif);
                } else {
                    menuItem.hideSubItem(gallery_menu_savegif);
                }
                menuItem.checkHideMenuItem();
                actionBar.setTitle(LocaleController.getString("AttachGif", R.string.AttachGif));
            } else {
                if (size == 1) {
                    if (isVideo) {
                        actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
                    } else {
                        actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
                    }
                } else {
                    actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, switchingToIndex + 1, size));
                }
                menuItem.showSubItem(gallery_menu_save);
                menuItem.hideSubItem(gallery_menu_savegif);
                menuItem.checkHideMenuItem();
            }
            groupedPhotosListView.fillList();
            pageBlocksAdapter.updateSlideshowCell(pageBlock);
        }
        setCurrentCaption(newMessageObject, caption, animateCaption);
    }

    private void showVideoTimeline(boolean show, boolean animated) {
        if (!animated)  {
            videoTimelineView.animate().setListener(null).cancel();
            videoTimelineView.setVisibility(show ? View.VISIBLE : View.GONE);
            videoTimelineView.setTranslationY(0);
            videoTimelineView.setAlpha(pickerView.getAlpha());
        } else {
            if (show && videoTimelineView.getTag() == null) {
                if (videoTimelineView.getVisibility() != View.VISIBLE) {
                    videoTimelineView.setVisibility(View.VISIBLE);
                    videoTimelineView.setAlpha(pickerView.getAlpha());
                    videoTimelineView.setTranslationY(AndroidUtilities.dp(58));
                }
                if (videoTimelineAnimator != null) {
                    videoTimelineAnimator.removeAllListeners();
                    videoTimelineAnimator.cancel();
                }
                videoTimelineAnimator = ObjectAnimator.ofFloat(videoTimelineView, View.TRANSLATION_Y, videoTimelineView.getTranslationY(), 0);
                videoTimelineAnimator.setDuration(220);
                videoTimelineAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                videoTimelineAnimator.start();

            } else if (!show && videoTimelineView.getTag() != null) {
                if (videoTimelineAnimator != null) {
                    videoTimelineAnimator.removeAllListeners();
                    videoTimelineAnimator.cancel();
                }

                videoTimelineAnimator = ObjectAnimator.ofFloat(videoTimelineView, View.TRANSLATION_Y, videoTimelineView.getTranslationY(), AndroidUtilities.dp(58));
                videoTimelineAnimator.addListener(new HideViewAfterAnimation(videoTimelineView));
                videoTimelineAnimator.setDuration(220);
                videoTimelineAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                videoTimelineAnimator.start();
            }
        }
        videoTimelineView.setTag(show ? 1 : null);
    }

    public static TLRPC.FileLocation getFileLocation(ImageLocation location) {
        if (location == null) {
            return null;
        }
        return location.location;
    }

    public static String getFileLocationExt(ImageLocation location) {
        if (location == null || location.imageType != FileLoader.IMAGE_TYPE_ANIMATION) {
            return null;
        }
        return "mp4";
    }

    private void setImageIndex(int index) {
        setImageIndex(index, true, false);
    }

    private void setImageIndex(int index, boolean init, boolean animateCaption) {
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
        placeProvider.willSwitchFromPhoto(currentMessageObject, getFileLocation(currentFileLocation), currentIndex);
        lastPhotoSetTime = SystemClock.elapsedRealtime();

        int prevIndex = currentIndex;
        currentIndex = index;
        setIsAboutToSwitchToIndex(currentIndex, init, animateCaption);

        boolean isVideo = false;
        boolean sameImage = false;
        Uri videoPath = null;
        editState.reset();

        if (!imagesArr.isEmpty()) {
            if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                closePhoto(false, false);
                return;
            }
            MessageObject newMessageObject = imagesArr.get(currentIndex);
            sameImage = init && currentMessageObject != null && currentMessageObject.getId() == newMessageObject.getId();
            currentMessageObject = newMessageObject;
            isVideo = newMessageObject.isVideo();
            if (sharedMediaType == MediaDataController.MEDIA_FILE) {
                if (canZoom = newMessageObject.canPreviewDocument()) {
                    if (allowShare) {
                        menuItem.showSubItem(gallery_menu_save);
                    } else {
                        menuItem.hideSubItem(gallery_menu_save);
                    }
                    setDoubleTapEnabled(true);
                } else {
                    menuItem.hideSubItem(gallery_menu_save);
                    setDoubleTapEnabled(false);
                }
            }
            if (isVideo || isEmbedVideo) {
                speedGap.setVisibility(View.VISIBLE);
                menuItem.showSubItem(gallery_menu_speed);
            } else {
                menuItem.hideSubItem(gallery_menu_speed);
                speedGap.setVisibility(View.GONE);
                menuItem.checkHideMenuItem();
            }
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
            ImageLocation old = currentFileLocation;
            ImageLocation newLocation = imagesArrLocations.get(index);
            if (init && old != null && newLocation != null && old.location.local_id == newLocation.location.local_id && old.location.volume_id == newLocation.location.volume_id) {
                sameImage = true;
            }
            currentFileLocation = imagesArrLocations.get(index);
            currentFileLocationVideo = imagesArrLocationsVideo.get(index);
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
                    MediaController.PhotoEntry entry = ((MediaController.PhotoEntry) object);
                    currentPathObject = entry.path;
                    if (currentPathObject == null) {
                        closePhoto(false, false);
                        return;
                    }
                    isVideo = entry.isVideo;
                    editState.savedFilterState = entry.savedFilterState;
                    editState.paintPath = entry.paintPath;
                    editState.croppedPaintPath = entry.croppedPaintPath;
                    editState.croppedMediaEntities = entry.croppedMediaEntities;
                    editState.averageDuration = entry.averageDuration;
                    editState.mediaEntities = entry.mediaEntities;
                    editState.cropState = entry.cropState;
                    File file = new File(entry.path);
                    videoPath = Uri.fromFile(file);
                    if (isDocumentsPicker) {
                        StringBuilder builder = new StringBuilder();
                        if (entry.width != 0 && entry.height != 0) {
                            if (builder.length() > 0) {
                                builder.append(", ");
                            }
                            builder.append(String.format(Locale.US, "%dx%d", entry.width, entry.height));
                        }
                        if (entry.isVideo) {
                            if (builder.length() > 0) {
                                builder.append(", ");
                            }
                            builder.append(AndroidUtilities.formatShortDuration(entry.duration));
                        }
                        if (entry.size != 0) {
                            if (builder.length() > 0) {
                                builder.append(", ");
                            }
                            builder.append(AndroidUtilities.formatFileSize(entry.size));
                        }

                        docNameTextView.setText(file.getName());
                        docInfoTextView.setText(builder);
                    }
                    sameImage = savedState != null;
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                    currentPathObject = searchImage.getPathToAttach();
                    editState.savedFilterState = searchImage.savedFilterState;
                    editState.paintPath = searchImage.paintPath;
                    editState.croppedPaintPath = searchImage.croppedPaintPath;
                    editState.croppedMediaEntities = searchImage.croppedMediaEntities;
                    editState.averageDuration = searchImage.averageDuration;
                    editState.mediaEntities = searchImage.mediaEntities;
                    editState.cropState = searchImage.cropState;
                }
                if (object instanceof MediaController.MediaEditState) {
                    MediaController.MediaEditState state = (MediaController.MediaEditState) object;
                    if (hasAnimatedMediaEntities()) {
                        currentImagePath = state.imagePath;
                    } else if (state.filterPath != null) {
                        currentImagePath = state.filterPath;
                    } else {
                        currentImagePath = currentPathObject;
                    }
                }
            }
            if (editState.cropState != null) {
                previousHasTransform = true;
                previousCropPx = editState.cropState.cropPx;
                previousCropPy = editState.cropState.cropPy;
                previousCropScale = editState.cropState.cropScale;
                previousCropRotation = editState.cropState.cropRotate;
                previousCropOrientation = editState.cropState.transformRotation;
                previousCropPw = editState.cropState.cropPw;
                previousCropPh = editState.cropState.cropPh;
                previousCropMirrored = editState.cropState.mirrored;
                cropTransform.setViewTransform(previousHasTransform, previousCropPx, previousCropPy, previousCropRotation, previousCropOrientation, previousCropScale, 1.0f, 1.0f, previousCropPw, previousCropPh, 0, 0, previousCropMirrored);
            } else {
                previousHasTransform = false;
                cropTransform.setViewTransform(false, previousCropPx, previousCropPy, previousCropRotation, previousCropOrientation, previousCropScale, 1.0f, 1.0f, previousCropPw, previousCropPh, 0, 0, previousCropMirrored);
            }
        } else if (pageBlocksAdapter != null) {
            if (currentIndex < 0 || currentIndex >= pageBlocksAdapter.getItemsCount()) {
                closePhoto(false, false);
                return;
            }
            final TLRPC.PageBlock pageBlock = pageBlocksAdapter.get(currentIndex);
            sameImage = currentPageBlock != null && currentPageBlock == pageBlock;
            currentPageBlock = pageBlock;
            isVideo = pageBlocksAdapter.isVideo(currentIndex);
        }
        setMenuItemIcon();

        if (currentPlaceObject != null) {
            if (animationInProgress == 0) {
                currentPlaceObject.imageReceiver.setVisible(true, true);
            } else {
                showAfterAnimation = currentPlaceObject;
            }
        }
        currentPlaceObject = placeProvider.getPlaceForPhoto(currentMessageObject, getFileLocation(currentFileLocation), currentIndex, false);
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
            zoomAnimation = false;
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
            paintViewTouched = 0;
            doubleTap = false;
            invalidCoords = false;
            canDragDown = true;
            changingPage = false;
            switchImageAfterAnimation = 0;
            if (sharedMediaType != MediaDataController.MEDIA_FILE) {
                canZoom = !isEmbedVideo && (!imagesArrLocals.isEmpty() || (currentFileNames[0] != null && /*!isVideo && */photoProgressViews[0].backgroundState != 0));
            }
            updateMinMax(scale);
            releasePlayer(false);
        }
        if (isVideo && videoPath != null) {
            isStreaming = false;
            preparePlayer(videoPath, sendPhotoType == SELECT_TYPE_AVATAR, false, editState.savedFilterState);
        }

        if (!imagesArrLocals.isEmpty()) {
            paintingOverlay.setVisibility(View.VISIBLE);
            paintingOverlay.setData(editState.paintPath, editState.mediaEntities, isCurrentVideo, false);
        } else {
            paintingOverlay.setVisibility(View.GONE);
            editState.reset();
        }

        if (prevIndex == -1) {
            setImages();

            for (int a = 0; a < 3; a++) {
                checkProgress(a, false, false);
            }
        } else {
            checkProgress(0, true, false);
            if (prevIndex > currentIndex) {
                ImageReceiver temp = rightImage;
                rightImage = centerImage;
                centerImage = leftImage;
                leftImage = temp;

                PhotoProgressView tempProgress = photoProgressViews[0];
                photoProgressViews[0] = photoProgressViews[2];
                photoProgressViews[2] = tempProgress;

                ImageView tmp = fullscreenButton[0];
                fullscreenButton[0] = fullscreenButton[2];
                fullscreenButton[2] = tmp;
                fullscreenButton[0].setTranslationY(tmp.getTranslationY());

                setIndexToImage(leftImage, currentIndex - 1);
                updateAccessibilityOverlayVisibility();

                checkProgress(1, true, false);
                checkProgress(2, true, false);
            } else if (prevIndex < currentIndex) {
                ImageReceiver temp = leftImage;
                leftImage = centerImage;
                centerImage = rightImage;
                rightImage = temp;

                PhotoProgressView tempProgress = photoProgressViews[0];
                photoProgressViews[0] = photoProgressViews[1];
                photoProgressViews[1] = tempProgress;

                ImageView tmp = fullscreenButton[0];
                fullscreenButton[0] = fullscreenButton[1];
                fullscreenButton[1] = tmp;
                fullscreenButton[0].setTranslationY(tmp.getTranslationY());

                setIndexToImage(rightImage, currentIndex + 1);
                updateAccessibilityOverlayVisibility();

                checkProgress(1, true, false);
                checkProgress(2, true, false);
            }
            if (videoFrameBitmap != null) {
                videoFrameBitmap.recycle();
                videoFrameBitmap = null;
            }
        }
        detectFaces();
    }

    private void setCurrentCaption(MessageObject messageObject, final CharSequence caption, boolean animated) {
        if (needCaptionLayout) {
            if (captionTextViewSwitcher.getParent() != pickerView) {
                if (captionContainer != null) {
                    captionContainer.removeView(captionTextViewSwitcher);
                }
                captionTextViewSwitcher.setMeasureAllChildren(false);
                pickerView.addView(captionTextViewSwitcher, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 76, 48));
            }
        } else {
            if (captionScrollView == null) {
                captionScrollView = new CaptionScrollView(containerView.getContext());
                captionContainer = new FrameLayout(containerView.getContext());
                captionContainer.setClipChildren(false);
                captionScrollView.addView(captionContainer, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                containerView.addView(captionScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
            }
            if (captionTextViewSwitcher.getParent() != captionContainer) {
                pickerView.removeView(captionTextViewSwitcher);
                captionTextViewSwitcher.setMeasureAllChildren(true);
                captionContainer.addView(captionTextViewSwitcher, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                videoPreviewFrame.bringToFront();
            }
        }

        final boolean isCaptionEmpty = TextUtils.isEmpty(caption);
        final boolean isCurrentCaptionEmpty = TextUtils.isEmpty(captionTextViewSwitcher.getCurrentView().getText());

        TextView captionTextView = animated ? captionTextViewSwitcher.getNextView() : captionTextViewSwitcher.getCurrentView();

        if (isCurrentVideo) {
            if (captionTextView.getMaxLines() != 1) {
                captionTextViewSwitcher.getCurrentView().setMaxLines(1);
                captionTextViewSwitcher.getNextView().setMaxLines(1);
                captionTextViewSwitcher.getCurrentView().setSingleLine(true);
                captionTextViewSwitcher.getNextView().setSingleLine(true);
                captionTextViewSwitcher.getCurrentView().setEllipsize(TextUtils.TruncateAt.END);
                captionTextViewSwitcher.getNextView().setEllipsize(TextUtils.TruncateAt.END);
            }
        } else {
            final int maxLines = captionTextView.getMaxLines();
            if (maxLines == 1) {
                captionTextViewSwitcher.getCurrentView().setSingleLine(false);
                captionTextViewSwitcher.getNextView().setSingleLine(false);
            }
            final int newCount;
            if (needCaptionLayout) {
                newCount = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 5 : 10;
            } else {
                newCount = Integer.MAX_VALUE;
            }
            if (maxLines != newCount) {
                captionTextViewSwitcher.getCurrentView().setMaxLines(newCount);
                captionTextViewSwitcher.getNextView().setMaxLines(newCount);
                captionTextViewSwitcher.getCurrentView().setEllipsize(null);
                captionTextViewSwitcher.getNextView().setEllipsize(null);
            }
        }

        captionTextView.setScrollX(0);
        dontChangeCaptionPosition = !needCaptionLayout && animated && isCaptionEmpty;
        boolean withTransition = false;

        if (!needCaptionLayout) {
            captionScrollView.dontChangeTopMargin = false;
        }

        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            withTransition = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TransitionManager.endTransitions(needCaptionLayout ? pickerView : captionScrollView);
            }
            if (needCaptionLayout) {
                final TransitionSet transitionSet = new TransitionSet();
                transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
                transitionSet.addTransition(new ChangeBounds());
                transitionSet.addTransition(new Fade(Fade.OUT));
                transitionSet.addTransition(new Fade(Fade.IN));
                transitionSet.setDuration(200);
                TransitionManager.beginDelayedTransition(pickerView, transitionSet);
            } else {
                final TransitionSet transition = new TransitionSet()
                        .addTransition(new Fade(Fade.OUT) {
                            @Override
                            public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                                final Animator animator = super.onDisappear(sceneRoot, view, startValues, endValues);
                                if (!isCurrentCaptionEmpty && isCaptionEmpty && view == captionTextViewSwitcher) {
                                    animator.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            captionScrollView.setVisibility(View.INVISIBLE);
                                            captionScrollView.backgroundAlpha = 1f;
                                        }
                                    });
                                    ((ObjectAnimator) animator).addUpdateListener(animation -> {
                                        captionScrollView.backgroundAlpha = (float) animation.getAnimatedValue();
                                        captionScrollView.invalidate();
                                    });
                                }
                                return animator;
                            }
                        })
                        .addTransition(new Fade(Fade.IN) {
                            @Override
                            public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                                final Animator animator = super.onAppear(sceneRoot, view, startValues, endValues);
                                if (isCurrentCaptionEmpty && !isCaptionEmpty && view == captionTextViewSwitcher) {
                                    animator.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            captionScrollView.backgroundAlpha = 1f;
                                        }
                                    });
                                    ((ObjectAnimator) animator).addUpdateListener(animation -> {
                                        captionScrollView.backgroundAlpha = (float) animation.getAnimatedValue();
                                        captionScrollView.invalidate();
                                    });
                                }
                                return animator;
                            }
                        })
                        .setDuration(200);

                if (!isCurrentCaptionEmpty) {
                    captionScrollView.dontChangeTopMargin = true;
                    transition.addTransition(new Transition() {
                        @Override
                        public void captureStartValues(TransitionValues transitionValues) {
                            if (transitionValues.view == captionScrollView) {
                                transitionValues.values.put("scrollY", captionScrollView.getScrollY());
                            }
                        }

                        @Override
                        public void captureEndValues(TransitionValues transitionValues) {
                            if (transitionValues.view == captionTextViewSwitcher) {
                                transitionValues.values.put("translationY", captionScrollView.getPendingMarginTopDiff());
                            }
                        }

                        @Override
                        public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
                            if (startValues.view == captionScrollView) {
                                final ValueAnimator animator = ValueAnimator.ofInt((Integer) startValues.values.get("scrollY"), 0);
                                animator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        captionTextViewSwitcher.getNextView().setText(null);
                                        captionScrollView.applyPendingTopMargin();
                                    }

                                    @Override
                                    public void onAnimationStart(Animator animation) {
                                        captionScrollView.stopScrolling();
                                    }
                                });
                                animator.addUpdateListener(a -> captionScrollView.scrollTo(0, (Integer) a.getAnimatedValue()));
                                return animator;
                            } else if (endValues.view == captionTextViewSwitcher) {
                                final int endValue = (int) endValues.values.get("translationY");
                                if (endValue != 0) {
                                    final ObjectAnimator animator = ObjectAnimator.ofFloat(captionTextViewSwitcher, View.TRANSLATION_Y, 0, endValue);
                                    animator.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            captionTextViewSwitcher.setTranslationY(0);
                                        }
                                    });
                                    return animator;
                                }
                            }
                            return null;
                        }
                    });
                }

                if (isCurrentCaptionEmpty && !isCaptionEmpty) {
                    transition.addTarget(captionTextViewSwitcher);
                }

                TransitionManager.beginDelayedTransition(captionScrollView, transition);
            }
        } else {
            captionTextViewSwitcher.getCurrentView().setText(null);
            if (captionScrollView != null) {
                captionScrollView.scrollTo(0, 0);
            }
        }

        if (!isCaptionEmpty) {
            Theme.createChatResources(null, true);
            CharSequence str;
            if (messageObject != null && !messageObject.messageOwner.entities.isEmpty()) {
                Spannable spannableString = SpannableString.valueOf(caption);
                messageObject.addEntitiesToText(spannableString, true, false);
                if (messageObject.isVideo()) {
                    MessageObject.addUrlsByPattern(messageObject.isOutOwner(), spannableString, false, 3, messageObject.getDuration(), false);
                }
                str = Emoji.replaceEmoji(spannableString, captionTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            } else {
                str = Emoji.replaceEmoji(new SpannableStringBuilder(caption), captionTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            captionTextViewSwitcher.setTag(str);
            try {
                captionTextViewSwitcher.setText(str, animated);
                if (captionScrollView != null) {
                    captionScrollView.updateTopMargin();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            captionTextView.setScrollY(0);
            captionTextView.setTextColor(0xffffffff);
            boolean visible = isActionBarVisible && (bottomLayout.getVisibility() == View.VISIBLE || pickerView.getVisibility() == View.VISIBLE || pageBlocksAdapter != null);
            captionTextViewSwitcher.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        } else {
            if (needCaptionLayout) {
                captionTextViewSwitcher.setText(LocaleController.getString("AddCaption", R.string.AddCaption), animated);
                captionTextViewSwitcher.getCurrentView().setTextColor(0xb2ffffff);
                captionTextViewSwitcher.setTag("empty");
                captionTextViewSwitcher.setVisibility(View.VISIBLE);
            } else {
                captionTextViewSwitcher.setText(null, animated);
                captionTextViewSwitcher.getCurrentView().setTextColor(0xffffffff);
                captionTextViewSwitcher.setVisibility(View.INVISIBLE, !withTransition || isCurrentCaptionEmpty);
                captionTextViewSwitcher.setTag(null);
            }
        }
    }

    private void setCaptionHwLayerEnabled(boolean enabled) {
        if (captionHwLayerEnabled != enabled) {
            captionHwLayerEnabled = enabled;
            captionTextViewSwitcher.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            captionTextViewSwitcher.getCurrentView().setLayerType(View.LAYER_TYPE_HARDWARE, null);
            captionTextViewSwitcher.getNextView().setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    private void checkProgress(int a, boolean scroll, boolean animated) {
        int index = currentIndex;
        if (a == 1) {
            index += 1;
        } else if (a == 2) {
            index -= 1;
        }
        if (currentFileNames[a] != null) {
            File f1 = null;
            File f2 = null;
            boolean isVideo = false;
            boolean canStream = false;
            boolean canAutoPlay = false;
            MessageObject messageObject = null;
            if (currentMessageObject != null) {
                if (index < 0 || index >= imagesArr.size()) {
                    photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
                    return;
                }
                messageObject = imagesArr.get(index);
                canAutoPlay = shouldMessageObjectAutoPlayed(messageObject);
                if (sharedMediaType == MediaDataController.MEDIA_FILE && !messageObject.canPreviewDocument()) {
                    photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
                    return;
                }
                if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
                    f1 = new File(messageObject.messageOwner.attachPath);
                }
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.document == null) {
                    TLObject fileLocation = getFileLocation(index, null);
                    f2 = FileLoader.getPathToAttach(fileLocation, true);
                } else {
                    f2 = FileLoader.getPathToMessage(messageObject.messageOwner);
                }
                if (messageObject.isVideo()) {
                    canStream = SharedConfig.streamMedia && messageObject.canStreamVideo() && !DialogObject.isEncryptedDialog(messageObject.getDialogId());
                    isVideo = true;
                }
            } else if (currentBotInlineResult != null) {
                if (index < 0 || index >= imagesArrLocals.size()) {
                    photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
                    return;
                }
                TLRPC.BotInlineResult botInlineResult = (TLRPC.BotInlineResult) imagesArrLocals.get(index);
                if (botInlineResult.type.equals("video") || MessageObject.isVideoDocument(botInlineResult.document)) {
                    if (botInlineResult.document != null) {
                        f1 = FileLoader.getPathToAttach(botInlineResult.document);
                    } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                        f1 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(botInlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(botInlineResult.content.url, "mp4"));
                    }
                    isVideo = true;
                } else if (botInlineResult.document != null) {
                    f1 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), currentFileNames[a]);
                } else if (botInlineResult.photo != null) {
                    f1 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE), currentFileNames[a]);
                }
                f2 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFileNames[a]);
            } else if (currentFileLocation != null) {
                if (index < 0 || index >= imagesArrLocationsVideo.size()) {
                    photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
                    return;
                }
                ImageLocation location = imagesArrLocationsVideo.get(index);
                f1 = FileLoader.getPathToAttach(location.location, getFileLocationExt(location), avatarsDialogId != 0 || isEvent);
            } else if (currentSecureDocument != null) {
                if (index < 0 || index >= secureDocuments.size()) {
                    photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
                    return;
                }
                SecureDocument location = secureDocuments.get(index);
                f1 = FileLoader.getPathToAttach(location, true);
            } else if (currentPathObject != null) {
                f1 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), currentFileNames[a]);
                f2 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFileNames[a]);
            } else if (pageBlocksAdapter != null) {
                f1 = pageBlocksAdapter.getFile(index);
                isVideo = pageBlocksAdapter.isVideo(index);
                canAutoPlay = shouldIndexAutoPlayed(index);
            }
            File f1Final = f1;
            File f2Final = f2;
            MessageObject messageObjectFinal = messageObject;
            boolean canStreamFinal = canStream;
            boolean canAutoPlayFinal = !(a == 0 && dontAutoPlay) && canAutoPlay;
            boolean isVideoFinal = isVideo;
            Utilities.globalQueue.postRunnable(() -> {
                boolean exists = false;
                if (f1Final != null) {
                    exists = f1Final.exists();
                }
                if (!exists && f2Final != null) {
                    exists = f2Final.exists();
                }
                if (!exists && a != 0 && messageObjectFinal != null && canStreamFinal) {
                    if (DownloadController.getInstance(currentAccount).canDownloadMedia(messageObjectFinal.messageOwner) != 0) {
                        if ((parentChatActivity == null || parentChatActivity.getCurrentEncryptedChat() == null) && !messageObjectFinal.shouldEncryptPhotoOrVideo()) {
                            final TLRPC.Document document = messageObjectFinal.getDocument();
                            if (document != null) {
                                FileLoader.getInstance(currentAccount).loadFile(document, messageObjectFinal, 0, 10);
                            }
                        }
                    }
                }
                boolean existsFinal = exists;
                AndroidUtilities.runOnUIThread(() -> {
                    if (shownControlsByEnd && !actionBarWasShownBeforeByEnd && isPlaying) {
                        photoProgressViews[a].setBackgroundState(PROGRESS_PLAY, false, false);
                        return;
                    }
                    if ((f1Final != null || f2Final != null) && (existsFinal || canStreamFinal)) {
                        if (a != 0 || !isPlaying) {
                            if (isVideoFinal && (!canAutoPlayFinal || a == 0 && playerWasPlaying)) {
                                photoProgressViews[a].setBackgroundState(PROGRESS_PLAY, animated, true);
                            } else {
                                photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
                            }
                        }
                        if (a == 0) {
                            if (!existsFinal) {
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
                        if (isVideoFinal) {
                            if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[a])) {
                                photoProgressViews[a].setBackgroundState(PROGRESS_LOAD, false, true);
                            } else {
                                photoProgressViews[a].setBackgroundState(PROGRESS_CANCEL, false, true);
                            }
                        } else {
                            photoProgressViews[a].setBackgroundState(PROGRESS_EMPTY, animated, true);
                        }
                        Float progress = ImageLoader.getInstance().getFileProgress(currentFileNames[a]);
                        if (progress == null) {
                            progress = 0.0f;
                        }
                        photoProgressViews[a].setProgress(progress, false);
                    }
                    if (a == 0) {
                        canZoom = !isEmbedVideo && (!imagesArrLocals.isEmpty() || (currentFileNames[0] != null && photoProgressViews[0].backgroundState != 0));
                    }
                });
            });
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
                photoProgressViews[a].setBackgroundState(PROGRESS_PLAY, animated, true);
            } else {
                photoProgressViews[a].setBackgroundState(PROGRESS_NONE, animated, true);
            }
        }
    }

    public int getSelectiongLength() {
        return captionEditText != null ? captionEditText.getSelectionLength() : 0;
    }

    private void setIndexToImage(ImageReceiver imageReceiver, int index) {
        imageReceiver.setOrientation(0, false);
        if (!secureDocuments.isEmpty()) {
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
                imageReceiver.setImage(ImageLocation.getForSecureDocument(document), "d", null, null, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, imageSize, null, null, 0);
            }
        } else if (!imagesArrLocals.isEmpty()) {
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
                ImageLocation videoThumb = null;
                TLRPC.PhotoSize photo = null;
                TLObject photoObject = null;
                int imageSize = 0;
                String filter = null;
                boolean isVideo = false;
                int cacheType = 0;
                if (object instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                    isVideo = photoEntry.isVideo;
                    if (photoEntry.isVideo) {
                        if (photoEntry.thumbPath != null) {
                            if (fromCamera) {
                                Bitmap b = BitmapFactory.decodeFile(photoEntry.thumbPath);
                                if (b != null) {
                                    placeHolder = new ImageReceiver.BitmapHolder(b);
                                    photoEntry.thumbPath = null;
                                }
                            } else {
                                path = photoEntry.thumbPath;
                            }
                        } else {
                            path = "vthumb://" + photoEntry.imageId + ":" + photoEntry.path;
                        }
                    } else {
                        if (photoEntry.filterPath != null) {
                            path = photoEntry.filterPath;
                        } else {
                            imageReceiver.setOrientation(photoEntry.orientation, false);
                            path = photoEntry.path;
                        }
                        filter = String.format(Locale.US, "%d_%d", size, size);
                    }
                } else if (object instanceof TLRPC.BotInlineResult) {
                    cacheType = 1;
                    TLRPC.BotInlineResult botInlineResult = ((TLRPC.BotInlineResult) object);
                    if (botInlineResult.type.equals("video") || MessageObject.isVideoDocument(botInlineResult.document)) {
                        if (botInlineResult.document != null) {
                            photo = FileLoader.getClosestPhotoSizeWithSize(botInlineResult.document.thumbs, 90);
                            photoObject = botInlineResult.document;
                        } else if (botInlineResult.thumb instanceof TLRPC.TL_webDocument) {
                            webDocument = WebFile.createWithWebDocument(botInlineResult.thumb);
                        }
                    } else if (botInlineResult.type.equals("gif") && botInlineResult.document != null) {
                        document = botInlineResult.document;
                        imageSize = botInlineResult.document.size;
                        TLRPC.VideoSize videoSize = MessageObject.getDocumentVideoThumb(botInlineResult.document);
                        if (videoSize != null) {
                            videoThumb = ImageLocation.getForDocument(videoSize, document);
                        }
                        filter = "d";
                    } else if (botInlineResult.photo != null) {
                        TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(botInlineResult.photo.sizes, AndroidUtilities.getPhotoSize());
                        photo = sizeFull;
                        photoObject = botInlineResult.photo;
                        imageSize = sizeFull.size;
                        filter = String.format(Locale.US, "%d_%d", size, size);
                    } else if (botInlineResult.content instanceof TLRPC.TL_webDocument) {
                        if (botInlineResult.type.equals("gif")) {
                            filter = "d";
                            if (botInlineResult.thumb instanceof TLRPC.TL_webDocument && "video/mp4".equals(botInlineResult.thumb.mime_type)) {
                                videoThumb = ImageLocation.getForWebFile(WebFile.createWithWebDocument(botInlineResult.thumb));
                            }
                        } else {
                            filter = String.format(Locale.US, "%d_%d", size, size);
                        }
                        webDocument = WebFile.createWithWebDocument(botInlineResult.content);
                    }
                } else if (object instanceof MediaController.SearchImage) {
                    cacheType = 1;
                    MediaController.SearchImage photoEntry = (MediaController.SearchImage) object;
                    if (photoEntry.photoSize != null) {
                        photo = photoEntry.photoSize;
                        photoObject = photoEntry.photo;
                        imageSize = photoEntry.photoSize.size;
                    } else if (photoEntry.filterPath != null) {
                        path = photoEntry.filterPath;
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
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    if (videoThumb != null) {
                        imageReceiver.setImage(ImageLocation.getForDocument(document), "d", videoThumb, null, placeHolder == null ? ImageLocation.getForDocument(thumb, document) : null, String.format(Locale.US, "%d_%d", size, size), placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, imageSize, null, object, cacheType);
                    } else {
                        imageReceiver.setImage(ImageLocation.getForDocument(document), "d", placeHolder == null ? ImageLocation.getForDocument(thumb, document) : null, String.format(Locale.US, "%d_%d", size, size), placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, imageSize, null, object, cacheType);
                    }
                } else if (photo != null) {
                    imageReceiver.setImage(ImageLocation.getForObject(photo, photoObject), filter, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, imageSize, null, object, cacheType);
                } else if (webDocument != null) {
                    if (videoThumb != null) {
                        imageReceiver.setImage(ImageLocation.getForWebFile(webDocument), filter, videoThumb, null, (Drawable) null, object, cacheType);
                    } else {
                        imageReceiver.setImage(ImageLocation.getForWebFile(webDocument), filter, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : (isVideo && parentActivity != null ? parentActivity.getResources().getDrawable(R.drawable.nophotos) : null), null, object, cacheType);
                    }
                } else {
                    imageReceiver.setImage(path, filter, placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : (isVideo && parentActivity != null ? parentActivity.getResources().getDrawable(R.drawable.nophotos) : null), null, imageSize);
                }
            } else {
                imageReceiver.setImageBitmap((Bitmap) null);
            }
        } else if (pageBlocksAdapter != null) {
            int[] size = new int[1];
            TLObject media = pageBlocksAdapter.getMedia(index);
            TLRPC.PhotoSize fileLocation = pageBlocksAdapter.getFileLocation(media, size);
            if (fileLocation != null) {
                if (media instanceof TLRPC.Photo) {
                    TLRPC.Photo photo = (TLRPC.Photo) media;
                    ImageReceiver.BitmapHolder placeHolder = null;
                    if (currentThumb != null && imageReceiver == centerImage) {
                        placeHolder = currentThumb;
                    }
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    TLRPC.PhotoSize thumbLocation = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80);
                    imageReceiver.setImage(ImageLocation.getForPhoto(fileLocation, photo), null, ImageLocation.getForPhoto(thumbLocation, photo), "b", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, size[0], null, pageBlocksAdapter.getParentObject(), 1);
                } else if (pageBlocksAdapter.isVideo(index)) {
                    if (!(fileLocation.location instanceof TLRPC.TL_fileLocationUnavailable)) {
                        ImageReceiver.BitmapHolder placeHolder = null;
                        if (currentThumb != null && imageReceiver == centerImage) {
                            placeHolder = currentThumb;
                        }
                        imageReceiver.setImage(null, null, placeHolder == null ? ImageLocation.getForDocument(fileLocation, (TLRPC.Document) media) : null, "b", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, 0, null, pageBlocksAdapter.getParentObject(), 1);
                    } else {
                        imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                    }
                } else if (currentAnimation != null) {
                    imageReceiver.setImageBitmap(currentAnimation);
                    currentAnimation.addSecondParentView(containerView);
                }
            } else {
                if (size[0] == 0) {
                    imageReceiver.setImageBitmap((Bitmap) null);
                } else {
                    imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                }
            }
        } else {
            MessageObject messageObject;
            if (!imagesArr.isEmpty() && index >= 0 && index < imagesArr.size()) {
                messageObject = imagesArr.get(index);
                imageReceiver.setShouldGenerateQualityThumb(true);
            } else {
                messageObject = null;
            }

            if (messageObject != null) {
                String restrictionReason = MessagesController.getRestrictionReason(messageObject.messageOwner.restriction_reason);
                if (!TextUtils.isEmpty(restrictionReason)) {
                    imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                    return;
                } else if (messageObject.isVideo()) {
                    if (messageObject.photoThumbs != null && !messageObject.photoThumbs.isEmpty()) {
                        ImageReceiver.BitmapHolder placeHolder = null;
                        if (currentThumb != null && imageReceiver == centerImage) {
                            placeHolder = currentThumb;
                        }
                        TLRPC.PhotoSize thumbLocation = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320);
                        imageReceiver.setNeedsQualityThumb(thumbLocation.w < 100 && thumbLocation.h < 100);
                        imageReceiver.setImage(null, null, placeHolder == null ? ImageLocation.getForObject(thumbLocation, messageObject.photoThumbsObject) : null, "b", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, 0, null, messageObject, 1);
                        if (currentThumb != null) {
                            imageReceiver.setOrientation(currentThumb.orientation, false);
                        }
                    } else {
                        imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                    }
                    return;
                } else if (currentAnimation != null) {
                    currentAnimation.addSecondParentView(containerView);
                    imageReceiver.setImageBitmap(currentAnimation);
                    return;
                } else if (sharedMediaType == MediaDataController.MEDIA_FILE) {
                    if (messageObject.canPreviewDocument()) {
                        TLRPC.Document document = messageObject.getDocument();
                        imageReceiver.setNeedsQualityThumb(true);
                        ImageReceiver.BitmapHolder placeHolder = null;
                        if (currentThumb != null && imageReceiver == centerImage) {
                            placeHolder = currentThumb;
                        }
                        TLRPC.PhotoSize thumbLocation = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
                        int size = (int) (2048 / AndroidUtilities.density);
                        imageReceiver.setImage(ImageLocation.getForDocument(document), String.format(Locale.US, "%d_%d", size, size), placeHolder == null ? ImageLocation.getForDocument(thumbLocation, document) : null, "b", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, document.size, null, messageObject, 0);
                    } else {
                        OtherDocumentPlaceholderDrawable drawable = new OtherDocumentPlaceholderDrawable(parentActivity, containerView, messageObject);
                        imageReceiver.setImageBitmap(drawable);
                    }
                    return;
                }
            }
            int[] size = new int[1];
            ImageLocation imageLocation = getImageLocation(index, size);
            TLObject fileLocation = getFileLocation(index, size);
            imageReceiver.setNeedsQualityThumb(true);

            if (imageLocation != null) {
                ImageReceiver.BitmapHolder placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                if (size[0] == 0) {
                    size[0] = -1;
                }
                TLRPC.PhotoSize thumbLocation;
                TLObject photoObject;
                if (messageObject != null) {
                    thumbLocation = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
                    photoObject = messageObject.photoThumbsObject;
                } else {
                    thumbLocation = null;
                    photoObject = null;
                }
                if (thumbLocation != null && thumbLocation == fileLocation) {
                    thumbLocation = null;
                }
                boolean cacheOnly = messageObject != null && messageObject.isWebpage() || avatarsDialogId != 0 || isEvent;
                Object parentObject;
                ImageLocation videoThumb = null;
                if (messageObject != null) {
                    parentObject = messageObject;
                    if (sharedMediaType == MediaDataController.MEDIA_GIF) {
                        TLRPC.Document document = messageObject.getDocument();
                        TLRPC.VideoSize videoSize = MessageObject.getDocumentVideoThumb(document);
                        if (videoSize != null) {
                            videoThumb = ImageLocation.getForDocument(videoSize, document);
                        }
                    }
                } else if (avatarsDialogId != 0) {
                    if (avatarsDialogId > 0) {
                        parentObject = MessagesController.getInstance(currentAccount).getUser(avatarsDialogId);
                    } else {
                        parentObject = MessagesController.getInstance(currentAccount).getChat(-avatarsDialogId);
                    }
                } else {
                    parentObject = null;
                }
                if (videoThumb != null) {
                    imageReceiver.setImage(imageLocation, null, videoThumb, null, placeHolder == null ? ImageLocation.getForObject(thumbLocation, photoObject) : null, "b", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, size[0], null, parentObject, cacheOnly ? 1 : 0);
                } else {
                    String filter;
                    if (avatarsDialogId != 0) {
                        filter = imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
                    } else {
                        filter = null;
                    }
                    imageReceiver.setImage(imageLocation, filter, placeHolder == null ? ImageLocation.getForObject(thumbLocation, photoObject) : null, "b", placeHolder != null ? new BitmapDrawable(placeHolder.bitmap) : null, size[0], null, parentObject, cacheOnly ? 1 : 0);
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

    public static boolean isShowingImage(MessageObject object) {
        boolean result = false;
        if (Instance != null) {
            if (!Instance.pipAnimationInProgress && Instance.isVisible && !Instance.disableShowCheck && object != null) {
                MessageObject currentMessageObject = Instance.currentMessageObject;
                if (currentMessageObject == null && Instance.placeProvider != null) {
                    currentMessageObject = Instance.placeProvider.getEditingMessageObject();
                }
                result = currentMessageObject != null && currentMessageObject.getId() == object.getId() && currentMessageObject.getDialogId() == object.getDialogId();
            }
        }
        if (!result && PipInstance != null) {
            result = PipInstance.isVisible && !PipInstance.disableShowCheck && object != null && PipInstance.currentMessageObject != null && PipInstance.currentMessageObject.getId() == object.getId() && PipInstance.currentMessageObject.getDialogId() == object.getDialogId();
        }
        return result;
    }

    public static boolean isPlayingMessageInPip(MessageObject object) {
        return PipInstance != null && object != null && PipInstance.currentMessageObject != null && PipInstance.currentMessageObject.getId() == object.getId() && PipInstance.currentMessageObject.getDialogId() == object.getDialogId();
    }

    public static boolean isPlayingMessage(MessageObject object) {
        return Instance != null && !Instance.pipAnimationInProgress && Instance.isVisible && object != null && Instance.currentMessageObject != null && Instance.currentMessageObject.getId() == object.getId() && Instance.currentMessageObject.getDialogId() == object.getDialogId();
    }

    public static boolean isShowingImage(TLRPC.FileLocation object) {
        boolean result = false;
        if (Instance != null) {
            result = Instance.isVisible && !Instance.disableShowCheck && object != null &&
                    ((Instance.currentFileLocation != null && object.local_id == Instance.currentFileLocation.location.local_id && object.volume_id == Instance.currentFileLocation.location.volume_id && object.dc_id == Instance.currentFileLocation.dc_id) ||
                     (Instance.currentFileLocationVideo != null && object.local_id == Instance.currentFileLocationVideo.location.local_id && object.volume_id == Instance.currentFileLocationVideo.location.volume_id && object.dc_id == Instance.currentFileLocationVideo.dc_id));
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
            result = Instance.isVisible && !Instance.disableShowCheck && object != null && object.equals(Instance.currentPathObject);
        }
        return result;
    }

    public void setParentChatActivity(ChatActivity chatActivity) {
        parentChatActivity = chatActivity;
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        maxSelectedPhotos = value;
        allowOrder = order;
    }

    public void checkCurrentImageVisibility() {
        if (currentPlaceObject != null) {
            currentPlaceObject.imageReceiver.setVisible(true, true);
        }
        currentPlaceObject = placeProvider == null ? null : placeProvider.getPlaceForPhoto(currentMessageObject, getFileLocation(currentFileLocation), currentIndex, false);
        if (currentPlaceObject != null) {
            currentPlaceObject.imageReceiver.setVisible(false, true);
        }
    }

    public boolean openPhoto(final MessageObject messageObject, ChatActivity chatActivity, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        return openPhoto(messageObject, null, null, null, null, null, null, 0, provider, chatActivity, dialogId, mergeDialogId, true, null, null);
    }

    public boolean openPhoto(final MessageObject messageObject, int embedSeekTime, ChatActivity chatActivity, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        return openPhoto(messageObject, null, null, null, null, null, null, 0, provider, chatActivity, dialogId, mergeDialogId, true, null, embedSeekTime);
    }

    public boolean openPhoto(final MessageObject messageObject, long dialogId, long mergeDialogId, final PhotoViewerProvider provider, boolean fullScreenVideo) {
        return openPhoto(messageObject, null, null, null, null, null, null, 0, provider, null, dialogId, mergeDialogId, fullScreenVideo, null, null);
    }

    public boolean openPhoto(final TLRPC.FileLocation fileLocation, final PhotoViewerProvider provider) {
        return openPhoto(null, fileLocation, null, null, null, null, null, 0, provider, null, 0, 0, true, null, null);
    }

    public boolean openPhotoWithVideo(final TLRPC.FileLocation fileLocation, ImageLocation videoLocation, final PhotoViewerProvider provider) {
        return openPhoto(null, fileLocation, null, videoLocation, null, null, null, 0, provider, null, 0, 0, true, null, null);
    }

    public boolean openPhoto(final TLRPC.FileLocation fileLocation, final ImageLocation imageLocation, final PhotoViewerProvider provider) {
        return openPhoto(null, fileLocation, imageLocation, null, null, null, null, 0, provider, null, 0, 0, true, null, null);
    }

    public boolean openPhoto(final ArrayList<MessageObject> messages, final int index, long dialogId, long mergeDialogId, final PhotoViewerProvider provider) {
        return openPhoto(messages.get(index), null, null, null, messages, null, null, index, provider, null, dialogId, mergeDialogId, true, null, null);
    }

    public boolean openPhoto(final ArrayList<SecureDocument> documents, final int index, final PhotoViewerProvider provider) {
        return openPhoto(null, null, null, null, null, documents, null, index, provider, null, 0, 0, true, null, null);
    }

    public boolean openPhoto(int index, PageBlocksAdapter pageBlocksAdapter, PhotoViewerProvider provider) {
        return openPhoto(null, null, null, null, null, null, null, index, provider, null, 0, 0, true, pageBlocksAdapter, null);
    }

    public boolean openPhotoForSelect(final ArrayList<Object> photos, final int index, int type, boolean documentsPicker, final PhotoViewerProvider provider, ChatActivity chatActivity) {
        isDocumentsPicker = documentsPicker;
        if (pickerViewSendButton != null) {
            FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) pickerViewSendButton.getLayoutParams();
            if (type == 4 || type == 5) {
                pickerViewSendButton.setImageResource(R.drawable.attach_send);
                layoutParams2.bottomMargin = AndroidUtilities.dp(19);
            } else if (type == SELECT_TYPE_AVATAR || type == SELECT_TYPE_WALLPAPER || type == SELECT_TYPE_QR) {
                pickerViewSendButton.setImageResource(R.drawable.floating_check);
                pickerViewSendButton.setPadding(0, AndroidUtilities.dp(1), 0, 0);
                layoutParams2.bottomMargin = AndroidUtilities.dp(19);
            } else {
                pickerViewSendButton.setImageResource(R.drawable.attach_send);
                layoutParams2.bottomMargin = AndroidUtilities.dp(14);
            }
            pickerViewSendButton.setLayoutParams(layoutParams2);
        }
        if (sendPhotoType != SELECT_TYPE_AVATAR && type == SELECT_TYPE_AVATAR && isVisible) {
            sendPhotoType = type;
            doneButtonPressed = false;
            actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
            placeProvider = provider;
            mergeDialogId = 0;
            currentDialogId = 0;
            selectedPhotosAdapter.notifyDataSetChanged();
            pageBlocksAdapter = null;

            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }

            isVisible = true;

            togglePhotosListView(false, false);

            openedFullScreenVideo = false;
            createCropView();
            toggleActionBar(false, false);
            seekToProgressPending2 = 0;
            skipFirstBufferingProgress = false;
            playerInjected = false;

            makeFocusable();

            backgroundDrawable.setAlpha(255);
            containerView.setAlpha(1.0f);
            onPhotoShow(null, null, null, null, null, null, photos, index, null);
            initCropView();
            setCropBitmap();
            return true;
        }
        sendPhotoType = type;
        return openPhoto(null, null, null, null, null, null, photos, index, provider, chatActivity, 0, 0, true, null, null);
    }

    private void openCurrentPhotoInPaintModeForSelect() {
        if (!canSendMediaToParentChatActivity()) {
            return;
        }

        File file = null;
        boolean isVideo = false;
        boolean capReplace = false;
        MessageObject messageObject = null;

        if (currentMessageObject != null) {
            messageObject = currentMessageObject;
            capReplace = currentMessageObject.canEditMedia() && !currentMessageObject.isDocument();
            isVideo = currentMessageObject.isVideo();
            if (!TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
                file = new File(currentMessageObject.messageOwner.attachPath);
                if (!file.exists()) {
                    file = null;
                }
            }
            if (file == null) {
                file = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            }
        }

        if (file != null && file.exists()) {
            savedState = new SavedState(currentIndex, new ArrayList<>(imagesArr), placeProvider);

            final ActionBarToggleParams toggleParams = new ActionBarToggleParams().enableStatusBarAnimation(false);
            toggleActionBar(false, true, toggleParams);

            File finalFile = file;
            boolean finalIsVideo = isVideo;
            boolean finalCanReplace = capReplace;
            MessageObject finalMessageObject = messageObject;
            AndroidUtilities.runOnUIThread(() -> {
                int orientation = 0;
                try {
                    ExifInterface ei = new ExifInterface(finalFile.getAbsolutePath());
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
                final MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, lastImageId--, 0, finalFile.getAbsolutePath(), orientation, finalIsVideo, 0, 0, 0);

                sendPhotoType = 2;
                doneButtonPressed = false;
                final PhotoViewerProvider chatPhotoProvider = placeProvider;
                placeProvider = new EmptyPhotoViewerProvider() {

                    private final ImageReceiver.BitmapHolder thumbHolder = centerImage.getBitmapSafe();

                    @Override
                    public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
                        return chatPhotoProvider != null ? chatPhotoProvider.getPlaceForPhoto(finalMessageObject, null, 0, needPreview) : null;
                    }

                    @Override
                    public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                        return thumbHolder;
                    }

                    @Override
                    public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                        sendMedia(videoEditedInfo, notify, scheduleDate, false, forceDocument);
                    }

                    @Override
                    public void replaceButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
                        if (photoEntry.isCropped || photoEntry.isPainted || photoEntry.isFiltered || videoEditedInfo != null || !TextUtils.isEmpty(photoEntry.caption)) {
                            sendMedia(videoEditedInfo, false, 0, true, false);
                        }
                    }

                    @Override
                    public boolean canReplace(int index) {
                        return chatPhotoProvider != null && finalCanReplace;
                    }

                    @Override
                    public MessageObject getEditingMessageObject() {
                        return finalMessageObject;
                    }

                    @Override
                    public boolean canCaptureMorePhotos() {
                        return false;
                    }

                    private void sendMedia(VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean replace, boolean forceDocument) {
                        if (parentChatActivity != null) {
                            final MessageObject editingMessageObject = replace ? finalMessageObject : null;
                            if (editingMessageObject != null && !TextUtils.isEmpty(photoEntry.caption)) {
                                editingMessageObject.editingMessage = photoEntry.caption;
                                editingMessageObject.editingMessageEntities = photoEntry.entities;
                            }
                            if (photoEntry.isVideo) {
                                if (videoEditedInfo != null) {
                                    SendMessagesHelper.prepareSendingVideo(parentChatActivity.getAccountInstance(), photoEntry.path, videoEditedInfo, parentChatActivity.getDialogId(), parentChatActivity.getReplyMessage(), parentChatActivity.getThreadMessage(), photoEntry.caption, photoEntry.entities, photoEntry.ttl, editingMessageObject, notify, scheduleDate, forceDocument);
                                } else {
                                    SendMessagesHelper.prepareSendingVideo(parentChatActivity.getAccountInstance(), photoEntry.path, null, parentChatActivity.getDialogId(), parentChatActivity.getReplyMessage(), parentChatActivity.getThreadMessage(), photoEntry.caption, photoEntry.entities, photoEntry.ttl, editingMessageObject, notify, scheduleDate, forceDocument);
                                }
                            } else {
                                if (photoEntry.imagePath != null) {
                                    SendMessagesHelper.prepareSendingPhoto(parentChatActivity.getAccountInstance(), photoEntry.imagePath, photoEntry.thumbPath, null, parentChatActivity.getDialogId(), parentChatActivity.getReplyMessage(), parentChatActivity.getThreadMessage(), photoEntry.caption, photoEntry.entities, photoEntry.stickers, null, photoEntry.ttl, editingMessageObject, videoEditedInfo, notify, scheduleDate, forceDocument);
                                } else if (photoEntry.path != null) {
                                    SendMessagesHelper.prepareSendingPhoto(parentChatActivity.getAccountInstance(), photoEntry.path, photoEntry.thumbPath, null, parentChatActivity.getDialogId(), parentChatActivity.getReplyMessage(), parentChatActivity.getThreadMessage(), photoEntry.caption, photoEntry.entities, photoEntry.stickers, null, photoEntry.ttl, editingMessageObject, videoEditedInfo, notify, scheduleDate, forceDocument);
                                }
                            }
                        }
                    }
                };
                selectedPhotosAdapter.notifyDataSetChanged();

                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }

                togglePhotosListView(false, false);
                toggleActionBar(true, false);

                if (parentChatActivity != null && parentChatActivity.getChatActivityEnterView() != null && parentChatActivity.isKeyboardVisible()) {
                    parentChatActivity.getChatActivityEnterView().closeKeyboard();
                } else {
                    makeFocusable();
                }
                backgroundDrawable.setAlpha(255);
                containerView.setAlpha(1.0f);

                onPhotoShow(null, null, null, null, null, null, Collections.singletonList(photoEntry), 0, null);

                pickerView.setTranslationY(AndroidUtilities.dp(isCurrentVideo ? 154 : 96));
                pickerViewSendButton.setTranslationY(AndroidUtilities.dp(isCurrentVideo ? 154 : 96));
                actionBar.setTranslationY(-actionBar.getHeight());
                captionTextViewSwitcher.setTranslationY(AndroidUtilities.dp(isCurrentVideo ? 154 : 96));

                createPaintView();
                switchToPaintMode();
            }, toggleParams.animationDuration);
        } else {
            showDownloadAlert();
        }
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

    private void setCropBitmap() {
        if (cropInitied || sendPhotoType != SELECT_TYPE_AVATAR) {
            return;
        }
        if (isCurrentVideo) {
            VideoEditTextureView textureView = (VideoEditTextureView) videoTextureView;
            if (textureView == null || textureView.getVideoWidth() <= 0 || textureView.getVideoHeight() <= 0) {
                return;
            }
        }
        cropInitied = true;
        Bitmap bitmap = centerImage.getBitmap();
        int orientation = centerImage.getOrientation();
        if (bitmap == null) {
            bitmap = animatingImageView.getBitmap();
            orientation = animatingImageView.getOrientation();
        }
        if (bitmap != null || videoTextureView != null) {
            photoCropView.setBitmap(bitmap, orientation, false, false, paintingOverlay, cropTransform, isCurrentVideo ? (VideoEditTextureView) videoTextureView : null, editState.cropState);
        }
    }

    private void initCropView() {
        if (photoCropView == null) {
            return;
        }
        photoCropView.setBitmap(null, 0, false, false, null, null, null, null);
        if (sendPhotoType != SELECT_TYPE_AVATAR) {
            return;
        }
        photoCropView.onAppear();
        photoCropView.setVisibility(View.VISIBLE);
        photoCropView.setAlpha(1.0f);
        photoCropView.onAppeared();
        padImageForHorizontalInsets = true;
    }

    public boolean openPhoto(final MessageObject messageObject, final TLRPC.FileLocation fileLocation, final ImageLocation imageLocation, final ImageLocation videoLocation, final ArrayList<MessageObject> messages, final ArrayList<SecureDocument> documents, final ArrayList<Object> photos, final int index, final PhotoViewerProvider provider, ChatActivity chatActivity, long dialogId, long mDialogId, boolean fullScreenVideo, PageBlocksAdapter pageBlocksAdapter, Integer embedSeekTime) {
        if (parentActivity == null || isVisible || provider == null && checkAnimation() || messageObject == null && fileLocation == null && messages == null && photos == null && documents == null && imageLocation == null && pageBlocksAdapter == null) {
            return false;
        }

        final PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, fileLocation, index, true);
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
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            } else {
                windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }
            if (chatActivity != null && chatActivity.getCurrentEncryptedChat() != null ||
                    avatarsDialogId != 0 && MessagesController.getInstance(currentAccount).isChatNoForwards(-avatarsDialogId) ||
                    messageObject != null && (MessagesController.getInstance(currentAccount).isChatNoForwards(messageObject.getChatId()) || (messageObject.messageOwner != null && messageObject.messageOwner.noforwards))) {
                windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;
            } else {
                windowLayoutParams.flags &=~ WindowManager.LayoutParams.FLAG_SECURE;
            }
            windowLayoutParams.softInputMode = (useSmoothKeyboard ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
            windowView.setFocusable(false);
            containerView.setFocusable(false);
            wm.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }

        hasCaptionForAllMedia = false;
        doneButtonPressed = false;
        allowShowFullscreenButton = true;
        parentChatActivity = chatActivity;
        lastTitle = null;
        isEmbedVideo = embedSeekTime != null;

        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        actionBar.setTitleScrollNonFitText(false);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaCountDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileNewChunkAvailable);

        placeProvider = provider;
        mergeDialogId = mDialogId;
        currentDialogId = dialogId;
        selectedPhotosAdapter.notifyDataSetChanged();
        this.pageBlocksAdapter = pageBlocksAdapter;

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        isVisible = true;

        togglePhotosListView(false, false);

        openedFullScreenVideo = !fullScreenVideo;
        if (openedFullScreenVideo) {
            toggleActionBar(false, false);
        } else {
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                createCropView();
                toggleActionBar(false, false);
            } else {
                toggleActionBar(true, false);
            }
        }

        seekToProgressPending2 = 0;
        skipFirstBufferingProgress = false;
        playerInjected = false;
        if (object != null) {
            disableShowCheck = true;
            animationInProgress = 1;
            if (messageObject != null) {
                currentAnimation = object.allowTakeAnimation ? object.imageReceiver.getAnimation() : null;
                if (currentAnimation != null) {
                    if (messageObject.isVideo()) {
                        object.imageReceiver.setAllowStartAnimation(false);
                        object.imageReceiver.stopAnimation();
                        if (MediaController.getInstance().isPlayingMessage(messageObject)) {
                            seekToProgressPending2 = messageObject.audioProgress;
                        }
                        skipFirstBufferingProgress = injectingVideoPlayer == null && !FileLoader.getInstance(messageObject.currentAccount).isLoadingVideo(messageObject.getDocument(), true) && (currentAnimation.hasBitmap() || !FileLoader.getInstance(messageObject.currentAccount).isLoadingVideo(messageObject.getDocument(), false));
                        currentAnimation = null;
                    } else if (messageObject.getWebPagePhotos(null, null).size() > 1) {
                        currentAnimation = null;
                    }
                }
            } else if (pageBlocksAdapter != null) {
                currentAnimation = object.imageReceiver.getAnimation();
            }

            onPhotoShow(messageObject, fileLocation, imageLocation, videoLocation, messages, documents, photos, index, object);
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                photoCropView.setVisibility(View.VISIBLE);
                photoCropView.setAlpha(0.0f);
                photoCropView.setFreeform(false);
            }
            final RectF drawRegion = object.imageReceiver.getDrawRegion();
            float left = drawRegion.left;
            float top = drawRegion.top;
            int orientation = object.imageReceiver.getOrientation();
            int animatedOrientation = object.imageReceiver.getAnimatedOrientation();
            if (animatedOrientation != 0) {
                orientation = animatedOrientation;
            }

            final ClippingImageView[] animatingImageViews = getAnimatingImageViews(object);

            for (int i = 0; i < animatingImageViews.length; i++) {
                animatingImageViews[i].setAnimationValues(animationValues);
                animatingImageViews[i].setVisibility(View.VISIBLE);
                animatingImageViews[i].setRadius(object.radius);
                animatingImageViews[i].setOrientation(orientation);
                animatingImageViews[i].setImageBitmap(object.thumb);
            }

            initCropView();
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                photoCropView.setAspectRatio(1.0f);
            }

            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            layoutParams.width = (int) drawRegion.width();
            layoutParams.height = (int) drawRegion.height();
            if (layoutParams.width <= 0) {
                layoutParams.width = 100;
            }
            if (layoutParams.height <= 0) {
                layoutParams.height = 100;
            }

            for (int i = 0; i < animatingImageViews.length; i++) {
                if (animatingImageViews.length > 1) {
                    animatingImageViews[i].setAlpha(0.0f);
                } else {
                    animatingImageViews[i].setAlpha(1.0f);
                }
                animatingImageViews[i].setPivotX(0.0f);
                animatingImageViews[i].setPivotY(0.0f);
                animatingImageViews[i].setScaleX(object.scale);
                animatingImageViews[i].setScaleY(object.scale);
                animatingImageViews[i].setTranslationX(object.viewX + drawRegion.left * object.scale);
                animatingImageViews[i].setTranslationY(object.viewY + drawRegion.top * object.scale);
                animatingImageViews[i].setLayoutParams(layoutParams);
            }

            windowView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                @Override
                public boolean onPreDraw() {
                    if (animatingImageViews.length > 1) {
                        animatingImageViews[1].setAlpha(1.0f);
                        animatingImageViews[1].setAdditionalTranslationX(-getLeftInset());
                    }
                    animatingImageViews[0].setTranslationX(animatingImageViews[0].getTranslationX() + getLeftInset());
                    windowView.getViewTreeObserver().removeOnPreDrawListener(this);
                    float scaleX;
                    float scaleY;
                    float scale;
                    float yPos;
                    float xPos;
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        float statusBarHeight = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
                        float measuredHeight = (float) photoCropView.getMeasuredHeight() - AndroidUtilities.dp(64) - statusBarHeight;
                        float minSide = Math.min(photoCropView.getMeasuredWidth(), measuredHeight) - 2 * AndroidUtilities.dp(16);
                        float centerX = photoCropView.getMeasuredWidth() / 2.0f;
                        float centerY = statusBarHeight + measuredHeight / 2.0f;

                        float left = centerX - (minSide / 2.0f);
                        float top = centerY - (minSide / 2.0f);
                        float right = centerX + (minSide / 2.0f);
                        float bottom = centerY + (minSide / 2.0f);

                        scaleX = (right - left) / layoutParams.width;
                        scaleY = (bottom - top) / layoutParams.height;
                        scale = Math.max(scaleX, scaleY);
                        yPos = top + (bottom - top - layoutParams.height * scale) / 2;
                        xPos = (windowView.getMeasuredWidth() - getLeftInset() - getRightInset() - layoutParams.width * scale) / 2.0f + getLeftInset();
                    } else {
                        scaleX = (float) windowView.getMeasuredWidth() / layoutParams.width;
                        scaleY = (float) (AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0)) / layoutParams.height;
                        scale = Math.min(scaleX, scaleY);
                        yPos = ((AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0)) - (layoutParams.height * scale)) / 2.0f;
                        xPos = (windowView.getMeasuredWidth() - layoutParams.width * scale) / 2.0f;
                    }
                    int clipHorizontal = (int) Math.abs(left - object.imageReceiver.getImageX());
                    int clipVertical = (int) Math.abs(top - object.imageReceiver.getImageY());

                    if (pageBlocksAdapter != null && object.imageReceiver.isAspectFit()) {
                        clipHorizontal = 0;
                    }

                    int[] coords2 = new int[2];
                    object.parentView.getLocationInWindow(coords2);
                    int clipTop = (int) (coords2[1] - (Build.VERSION.SDK_INT >= 21 || inBubbleMode ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + top) + object.clipTopAddition);
                    if (clipTop < 0) {
                        clipTop = 0;
                    }
                    int clipBottom = (int) ((object.viewY + top + layoutParams.height) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 || inBubbleMode ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition);
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
                    int[] rad = animatingImageView.getRadius();
                    for (int a = 0; a < 4; a++) {
                        animationValues[0][7 + a] = rad != null ? rad[a] : 0;
                    }
                    animationValues[0][11] = clipVertical * object.scale;
                    animationValues[0][12] = clipHorizontal * object.scale;

                    animationValues[1][0] = scale;
                    animationValues[1][1] = scale;
                    animationValues[1][2] = xPos;
                    animationValues[1][3] = yPos;
                    animationValues[1][4] = 0;
                    animationValues[1][5] = 0;
                    animationValues[1][6] = 0;
                    animationValues[1][7] = 0;
                    animationValues[1][8] = 0;
                    animationValues[1][9] = 0;
                    animationValues[1][10] = 0;
                    animationValues[1][11] = 0;
                    animationValues[1][12] = 0;

                    for (int i = 0; i < animatingImageViews.length; i++) {
                        animatingImageViews[i].setAnimationProgress(0);
                    }
                    backgroundDrawable.setAlpha(0);
                    containerView.setAlpha(0);

                    animationEndRunnable = () -> {
                        animationEndRunnable = null;
                        if (containerView == null || windowView == null) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT >= 18) {
                            containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                        animationInProgress = 0;
                        transitionAnimationStartTime = 0;
                        setImages();
                        setCropBitmap();
                        containerView.invalidate();
                        for (int i = 0; i < animatingImageViews.length; i++) {
                            animatingImageViews[i].setVisibility(View.GONE);
                        }
                        if (showAfterAnimation != null) {
                            showAfterAnimation.imageReceiver.setVisible(true, true);
                        }
                        if (hideAfterAnimation != null) {
                            hideAfterAnimation.imageReceiver.setVisible(false, true);
                        }
                        if (photos != null && sendPhotoType != 3) {
                            if (placeProvider == null || !placeProvider.closeKeyboard()) {
                                makeFocusable();
                            }
                        }
                        if (videoPlayer != null && videoPlayer.isPlaying() && isCurrentVideo && !imagesArrLocals.isEmpty()) {
                            seekAnimatedStickersTo(videoPlayer.getCurrentPosition());
                            playOrStopAnimatedStickers(true);
                        }
                        if (isEmbedVideo) {
                            initEmbedVideo(embedSeekTime);
                        }
                    };

                    if (!openedFullScreenVideo) {
                        final AnimatorSet animatorSet = new AnimatorSet();
                        ArrayList<Animator> animators = new ArrayList<>((sendPhotoType == SELECT_TYPE_AVATAR ? 3 : 2) + animatingImageViews.length + (animatingImageViews.length > 1 ? 1 : 0));
                        for (int i = 0; i < animatingImageViews.length; i++) {
                            animators.add(ObjectAnimator.ofFloat(animatingImageViews[i], AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0.0f, 1.0f));
                        }
                        if (animatingImageViews.length > 1) {
                            animators.add(ObjectAnimator.ofFloat(animatingImageView, View.ALPHA, 0f, 1f));
                        }
                        animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0, 255));
                        animators.add(ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f, 1.0f));
                        if (sendPhotoType == SELECT_TYPE_AVATAR) {
                            animators.add(ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 0, 1.0f));
                        }
                        animatorSet.playTogether(animators);
                        animatorSet.setDuration(200);
                        int account = currentAccount;
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    NotificationCenter.getInstance(account).onAnimationFinish(transitionIndex);
                                    if (animationEndRunnable != null) {
                                        animationEndRunnable.run();
                                        animationEndRunnable = null;
                                    }
                                    setCaptionHwLayerEnabled(true);
                                });
                            }
                        });
                        if (Build.VERSION.SDK_INT >= 18) {
                            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        setCaptionHwLayerEnabled(false);
                        transitionAnimationStartTime = System.currentTimeMillis();
                        AndroidUtilities.runOnUIThread(() -> {
                            transitionIndex = NotificationCenter.getInstance(account).setAnimationInProgress(transitionIndex, new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats, NotificationCenter.mediaCountDidLoad, NotificationCenter.mediaDidLoad, NotificationCenter.dialogPhotosLoaded});
                            animatorSet.start();
                        });
                    } else {
                        if (animationEndRunnable != null) {
                            animationEndRunnable.run();
                            animationEndRunnable = null;
                        }
                        containerView.setAlpha(1.0f);
                        backgroundDrawable.setAlpha(255);
                        for (int i = 0; i < animatingImageViews.length; i++) {
                            animatingImageViews[i].setAnimationProgress(1.0f);
                        }
                        if (sendPhotoType == SELECT_TYPE_AVATAR) {
                            photoCropView.setAlpha(1.0f);
                        }
                    }
                    backgroundDrawable.drawRunnable = () -> {
                        disableShowCheck = false;
                        object.imageReceiver.setVisible(false, true);
                    };
                    if (parentChatActivity != null && parentChatActivity.getFragmentView() != null) {
                        parentChatActivity.getUndoView().hide(false, 1);
                        parentChatActivity.getFragmentView().invalidate();
                    }
                    return true;
                }
            });
        } else {
            if (photos != null && sendPhotoType != 3) {
                if (placeProvider == null || !placeProvider.closeKeyboard()) {
                    makeFocusable();
                }
            }

            //backgroundDrawable.setAlpha(255);
            containerView.setAlpha(1.0f);
            onPhotoShow(messageObject, fileLocation, imageLocation, videoLocation, messages, documents, photos, index, object);
            initCropView();
            setCropBitmap();
            if (parentChatActivity != null) {
                parentChatActivity.getUndoView().hide(false, 1);
                parentChatActivity.getFragmentView().invalidate();
            }
            windowView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    windowView.getViewTreeObserver().removeOnPreDrawListener(this);
                    actionBar.setTranslationY(-AndroidUtilities.dp(32));
                    actionBar.animate().alpha(1).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                    checkImageView.setTranslationY(-AndroidUtilities.dp(32));
                    checkImageView.animate().alpha(1).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                    photosCounterView.setTranslationY(-AndroidUtilities.dp(32));
                    photosCounterView.animate().alpha(1).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();


                    pickerView.setTranslationY(AndroidUtilities.dp(32));
                    pickerView.animate().alpha(1).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    pickerViewSendButton.setTranslationY(AndroidUtilities.dp(32));
                    pickerViewSendButton.setAlpha(0f);
                    pickerViewSendButton.animate().alpha(1).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                    cameraItem.setTranslationY(AndroidUtilities.dp(32));
                    cameraItem.animate().alpha(1).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                    videoPreviewFrame.setTranslationY(AndroidUtilities.dp(32));
                    videoPreviewFrame.animate().alpha(1).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                    containerView.setAlpha(0);
                    backgroundDrawable.setAlpha(0);

                    animationInProgress = 4;
                    containerView.invalidate();
                    AnimatorSet animatorSet = new AnimatorSet();
                    ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(containerView, View.ALPHA, 0f, 1f).setDuration(220);
                    ObjectAnimator a2 = ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, pickerView.getTranslationY(), 0f).setDuration(220);
                    a2.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    animatorSet.playTogether(
                            alphaAnimator,
                            a2
                    );
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            animationInProgress = 0;
                            backgroundDrawable.setAlpha(255);
                            containerView.invalidate();
                            pickerView.setTranslationY(0f);
                            if (isEmbedVideo) {
                                initEmbedVideo(embedSeekTime);
                            }
                        }
                    });
                    animatorSet.start();
                    return true;
                }
            });

        }

        AccessibilityManager am = (AccessibilityManager) parentActivity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am.isTouchExplorationEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            event.getText().add(LocaleController.getString("AccDescrPhotoViewer", R.string.AccDescrPhotoViewer));
            am.sendAccessibilityEvent(event);
        }

        return true;
    }

    private void initEmbedVideo(int embedSeekTime) {
        if (!isEmbedVideo) {
            return;
        }
        photoViewerWebView = new PhotoViewerWebView(parentActivity, pipItem) {

            Rect rect = new Rect();

            @Override
            protected void drawBlackBackground(Canvas canvas, int w, int h) {
                Bitmap bitmap = centerImage.getBitmap();
                if (bitmap != null) {
                    float minScale = Math.min(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
                    int width = (int) (bitmap.getWidth() * minScale);
                    int height = (int) (bitmap.getHeight() * minScale);
                    int top = (h - height) / 2;
                    int left = (w - width) / 2;
                    rect.set(left, top, left + width, top + height);
                    canvas.drawBitmap(bitmap, null, rect, null);
                }
            }

            @Override
            protected void processTouch(MotionEvent event) {
                gestureDetector.onTouchEvent(event);
            }
        };
        photoViewerWebView.init(embedSeekTime, currentMessageObject.messageOwner.media.webpage);
        photoViewerWebView.setPlaybackSpeed(currentVideoSpeed);
        containerView.addView(photoViewerWebView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void makeFocusable() {
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = 0;
        }
        windowLayoutParams.softInputMode = (useSmoothKeyboard ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
        WindowManager wm1 = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        try {
            wm1.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
        }
        windowView.setFocusable(true);
        containerView.setFocusable(true);
    }

    public void injectVideoPlayerToMediaController() {
        if (videoPlayer.isPlaying()) {
            if (playerLooping) {
                videoPlayer.setLooping(false);
            }
            MediaController.getInstance().injectVideoPlayer(videoPlayer, currentMessageObject);
            videoPlayer = null;
        }
    }

    public void closePhoto(boolean animated, boolean fromEditMode) {
        if (!fromEditMode && currentEditMode != 0) {
            if (currentEditMode == 3 && photoPaintView != null) {
                closePaintMode();
                return;
            }
            if (currentEditMode == 1) {
                cropTransform.setViewTransform(previousHasTransform, previousCropPx, previousCropPy, previousCropRotation, previousCropOrientation, previousCropScale, 1.0f, 1.0f, previousCropPw, previousCropPh, 0, 0, previousCropMirrored);
            }
            switchToEditMode(0);
            return;
        }
        if (qualityChooseView != null && qualityChooseView.getTag() != null) {
            qualityPicker.cancelButton.callOnClick();
            return;
        }
        openedFullScreenVideo = false;
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (Build.VERSION.SDK_INT >= 21 && containerView != null) {
            AndroidUtilities.cancelRunOnUIThread(updateContainerFlagsRunnable);
            updateContainerFlags(true);
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
                savedState = null;
            }
            currentEditMode = 0;
        }

        if (parentActivity == null || !isInline && !isVisible || checkAnimation() || placeProvider == null) {
            return;
        }
        if (captionEditText.hideActionMode() && !fromEditMode) {
            return;
        }
        if (parentActivity != null && fullscreenedByButton != 0) {
            parentActivity.setRequestedOrientation(prevOrientation);
            fullscreenedByButton = 0;
            wasRotated = false;
        }
        if (!doneButtonPressed && !imagesArrLocals.isEmpty() && currentIndex >= 0 && currentIndex < imagesArrLocals.size()) {
            Object entry = imagesArrLocals.get(currentIndex);
            if (entry instanceof MediaController.MediaEditState) {
                ((MediaController.MediaEditState) entry).editedInfo = getCurrentVideoEditedInfo();
            }
        }
        final PlaceProviderObject object = placeProvider.getPlaceForPhoto(currentMessageObject, getFileLocation(currentFileLocation), currentIndex, true);
        if (videoPlayer != null && object != null) {
            AnimatedFileDrawable animation = object.imageReceiver.getAnimation();
            if (animation != null) {
                if (textureUploaded) {
                    Bitmap bitmap = animation.getAnimatedBitmap();
                    if (bitmap != null) {
                        try {
                            Bitmap src = videoTextureView.getBitmap(bitmap.getWidth(), bitmap.getHeight());
                            Canvas canvas = new Canvas(bitmap);
                            canvas.drawBitmap(src, 0, 0, null);
                            src.recycle();
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }
                }
                if (currentMessageObject != null) {
                    long startTime = animation.getStartTime();
                    long seekTo = videoPlayer.getCurrentPosition() + (startTime > 0 ? startTime : 0);
                    animation.seekTo(seekTo, !FileLoader.getInstance(currentMessageObject.currentAccount).isLoadingVideo(currentMessageObject.getDocument(), true));
                }
                object.imageReceiver.setAllowStartAnimation(true);
                object.imageReceiver.startAnimation();
            }
        }
        if (!doneButtonPressed) {
            releasePlayer(true);
        }
        if (photoViewerWebView != null) {
            photoViewerWebView.release();
            containerView.removeView(photoViewerWebView);
            photoViewerWebView = null;
        }
        captionEditText.onDestroy();
        if (parentChatActivity != null && parentChatActivity.getFragmentView() != null) {
            parentChatActivity.getFragmentView().invalidate();
        }
        parentChatActivity = null;
        removeObservers();

        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }

        if (isInline) {
            isInline = false;
            animationInProgress = 0;
            onPhotoClosed(object);
            containerView.setScaleX(1.0f);
            containerView.setScaleY(1.0f);
        } else {
            if (animated) {
                final ClippingImageView[] animatingImageViews = getAnimatingImageViews(object);

                for (int i = 0; i < animatingImageViews.length; i++) {
                    animatingImageViews[i].setAnimationValues(animationValues);
                    animatingImageViews[i].setVisibility(View.VISIBLE);
                }

                animationInProgress = 3;
                containerView.invalidate();

                AnimatorSet animatorSet = new AnimatorSet();

                final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
                RectF drawRegion = null;
                if (object != null) {
                    drawRegion = object.imageReceiver.getDrawRegion();
                    layoutParams.width = (int) drawRegion.width();
                    layoutParams.height = (int) drawRegion.height();
                    int orientation = object.imageReceiver.getOrientation();
                    int animatedOrientation = object.imageReceiver.getAnimatedOrientation();
                    if (animatedOrientation != 0) {
                        orientation = animatedOrientation;
                    }
                    for (int i = 0; i < animatingImageViews.length; i++) {
                        animatingImageViews[i].setOrientation(orientation);
                        animatingImageViews[i].setImageBitmap(object.thumb);
                    }
                } else {
                    layoutParams.width = (int) centerImage.getImageWidth();
                    layoutParams.height = (int) centerImage.getImageHeight();
                    for (int i = 0; i < animatingImageViews.length; i++) {
                        animatingImageViews[i].setOrientation(centerImage.getOrientation());
                        animatingImageViews[i].setImageBitmap(centerImage.getBitmapSafe());
                    }
                }
                if (layoutParams.width <= 0) {
                    layoutParams.width = 100;
                }
                if (layoutParams.height <= 0) {
                    layoutParams.height = 100;
                }

                float scaleX;
                float scaleY;
                float scale2;
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    float statusBarHeight = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
                    float measuredHeight = (float) photoCropView.getMeasuredHeight() - AndroidUtilities.dp(64) - statusBarHeight;
                    float minSide = Math.min(photoCropView.getMeasuredWidth(), measuredHeight) - 2 * AndroidUtilities.dp(16);
                    scaleX = minSide / layoutParams.width;
                    scaleY = minSide / layoutParams.height;
                    scale2 = Math.max(scaleX, scaleY);
                } else {
                    scaleX = (float) windowView.getMeasuredWidth() / layoutParams.width;
                    scaleY = (float) (AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0)) / layoutParams.height;
                    scale2 = Math.min(scaleX, scaleY);
                }
                float width = layoutParams.width * scale * scale2;
                float height = layoutParams.height * scale * scale2;
                float xPos = (windowView.getMeasuredWidth() - width) / 2.0f;
                float yPos;
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    float statusBarHeight = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
                    float measuredHeight = (float) photoCropView.getMeasuredHeight() - statusBarHeight;
                    yPos = (measuredHeight - height) / 2.0f;
                } else {
                    yPos = ((AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0)) - height) / 2.0f;
                }
                for (int i = 0; i < animatingImageViews.length; i++) {
                    animatingImageViews[i].setLayoutParams(layoutParams);
                    animatingImageViews[i].setTranslationX(xPos + translationX);
                    animatingImageViews[i].setTranslationY(yPos + translationY);
                    animatingImageViews[i].setScaleX(scale * scale2);
                    animatingImageViews[i].setScaleY(scale * scale2);
                }

                if (object != null) {
                    object.imageReceiver.setVisible(false, true);
                    int clipHorizontal = (int) Math.abs(drawRegion.left - object.imageReceiver.getImageX());
                    int clipVertical = (int) Math.abs(drawRegion.top - object.imageReceiver.getImageY());

                    if (pageBlocksAdapter != null && object.imageReceiver.isAspectFit()) {
                        clipHorizontal = 0;
                    }

                    int[] coords2 = new int[2];
                    object.parentView.getLocationInWindow(coords2);
                    int clipTop = (int) (coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition);
                    if (clipTop < 0) {
                        clipTop = 0;
                    }
                    int clipBottom = (int) ((object.viewY + drawRegion.top + (drawRegion.bottom - drawRegion.top)) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition);
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
                    animationValues[0][8] = 0;
                    animationValues[0][9] = 0;
                    animationValues[0][10] = 0;
                    animationValues[0][11] = 0;
                    animationValues[0][12] = 0;

                    animationValues[1][0] = object.scale;
                    animationValues[1][1] = object.scale;
                    animationValues[1][2] = object.viewX + drawRegion.left * object.scale;
                    animationValues[1][3] = object.viewY + drawRegion.top * object.scale;
                    animationValues[1][4] = clipHorizontal * object.scale;
                    animationValues[1][5] = clipTop * object.scale;
                    animationValues[1][6] = clipBottom * object.scale;
                    for (int a = 0; a < 4; a++) {
                        animationValues[1][7 + a] = object.radius != null ? object.radius[a] : 0;
                    }
                    animationValues[1][11] = clipVertical * object.scale;
                    animationValues[1][12] = clipHorizontal * object.scale;

                    ArrayList<Animator> animators = new ArrayList<>((sendPhotoType == SELECT_TYPE_AVATAR ? 3 : 2) + animatingImageViews.length + (animatingImageViews.length > 1 ? 1 : 0));
                    for (int i = 0; i < animatingImageViews.length; i++) {
                        animators.add(ObjectAnimator.ofFloat(animatingImageViews[i], AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0.0f, 1.0f));
                    }
                    if (animatingImageViews.length > 1) {
                        animators.add(ObjectAnimator.ofFloat(animatingImageView, View.ALPHA, 0f));
                        animatingImageViews[1].setAdditionalTranslationX(-getLeftInset());
                    }
                    animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0));
                    animators.add(ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f));
                    if (sendPhotoType == SELECT_TYPE_AVATAR) {
                        animators.add(ObjectAnimator.ofFloat(photoCropView, View.ALPHA, 0.0f));
                    }
                    animatorSet.playTogether(animators);
                } else {
                    int h = (AndroidUtilities.displaySize.y + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0));
                    animatorSet.playTogether(
                            ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                            ObjectAnimator.ofFloat(animatingImageView, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(animatingImageView, View.TRANSLATION_Y, translationY >= 0 ? h : -h),
                            ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f)
                    );
                }

                animationEndRunnable = () -> {
                    animationEndRunnable = null;
                    if (Build.VERSION.SDK_INT >= 18) {
                        containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    animationInProgress = 0;
                    onPhotoClosed(object);
                };

                animatorSet.setDuration(200);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (animationEndRunnable != null) {
                                animationEndRunnable.run();
                                animationEndRunnable = null;
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
                        ObjectAnimator.ofFloat(containerView, View.SCALE_X, 0.9f),
                        ObjectAnimator.ofFloat(containerView, View.SCALE_Y, 0.9f),
                        ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                        ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f)
                );
                animationInProgress = 2;
                animationEndRunnable = () -> {
                    animationEndRunnable = null;
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
                };
                animatorSet.setDuration(200);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animationEndRunnable != null) {
                            ChatActivity chatActivity = parentChatActivity;
                            if (chatActivity == null && parentAlert != null) {
                                BaseFragment baseFragment = parentAlert.getBaseFragment();
                                if (baseFragment instanceof ChatActivity) {
                                    chatActivity = (ChatActivity) baseFragment;
                                }
                            }
                            if (chatActivity != null) {
                                chatActivity.doOnIdle(animationEndRunnable);
                            } else {
                                animationEndRunnable.run();
                                animationEndRunnable = null;
                            }
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
                currentAnimation.removeSecondParentView(containerView);
                currentAnimation = null;
                centerImage.setImageBitmap((Drawable) null);
            }
            if (placeProvider != null && !placeProvider.canScrollAway()) {
                placeProvider.cancelButtonPressed();
            }
        }
    }

    private ClippingImageView[] getAnimatingImageViews(PlaceProviderObject object) {
        final boolean hasSecondAnimatingImageView = !AndroidUtilities.isTablet() && object != null && object.animatingImageView != null;
        final ClippingImageView[] animatingImageViews = new ClippingImageView[1 + (hasSecondAnimatingImageView ? 1 : 0)];
        animatingImageViews[0] = animatingImageView;
        if (hasSecondAnimatingImageView) {
            animatingImageViews[1] = object.animatingImageView;
            object.animatingImageView.setAdditionalTranslationY(object.animatingImageViewYOffset);
        }
        return animatingImageViews;
    }

    private void removeObservers() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaCountDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
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
        releasePlayer(false);
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
        if (doneButtonPressed) {
            releasePlayer(true);
        }
        isVisible = false;
        cropInitied = false;
        disableShowCheck = true;
        currentMessageObject = null;
        currentBotInlineResult = null;
        currentFileLocation = null;
        currentFileLocationVideo = null;
        currentSecureDocument = null;
        currentPageBlock = null;
        currentPathObject = null;
        if (videoPlayerControlFrameLayout != null) {
            setVideoPlayerControlVisible(false, false);
        }
        if (captionScrollView != null) {
            captionScrollView.reset();
        }
        sendPhotoType = 0;
        isDocumentsPicker = false;
        if (currentThumb != null) {
            currentThumb.release();
            currentThumb = null;
        }
        parentAlert = null;
        if (currentAnimation != null) {
            currentAnimation.removeSecondParentView(containerView);
            currentAnimation = null;
        }
        for (int a = 0; a < 3; a++) {
            if (photoProgressViews[a] != null) {
                photoProgressViews[a].setBackgroundState(PROGRESS_NONE, false, true);
            }
        }
        requestVideoPreview(0);
        if (videoTimelineView != null) {
            videoTimelineView.setBackgroundColor(0x7f000000);
            videoTimelineView.destroy();
        }
        hintView.hide(false, 0);
        centerImage.setImageBitmap((Bitmap) null);
        leftImage.setImageBitmap((Bitmap) null);
        rightImage.setImageBitmap((Bitmap) null);
        containerView.post(() -> {
            animatingImageView.setImageBitmap(null);
            if (object != null && !AndroidUtilities.isTablet() && object.animatingImageView != null) {
                object.animatingImageView.setImageBitmap(null);
            }
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(windowView);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        if (placeProvider != null) {
            placeProvider.willHidePhotoViewer();
        }
        groupedPhotosListView.clear();
        placeProvider = null;
        selectedPhotosAdapter.notifyDataSetChanged();
        pageBlocksAdapter = null;
        disableShowCheck = false;
        shownControlsByEnd = false;
        videoCutStart = 0;
        videoCutEnd = 1f;
        if (object != null) {
            object.imageReceiver.setVisible(true, true);
        }
        if (parentChatActivity != null) {
            parentChatActivity.getFragmentView().invalidate();
        }
        if (videoFrameBitmap != null) {
            videoFrameBitmap.recycle();
            videoFrameBitmap = null;
        }
    }

    private void redraw(final int count) {
        if (count < 6) {
            if (containerView != null) {
                containerView.invalidate();
                AndroidUtilities.runOnUIThread(() -> redraw(count + 1), 100);
            }
        }
    }

    public void onResume() {
        redraw(0); //workaround for camera bug
        if (videoPlayer != null) {
            videoPlayer.seekTo(videoPlayer.getCurrentPosition() + 1);
            if (playerLooping) {
                videoPlayer.setLooping(true);
            }
        }
        if (photoPaintView != null) {
            photoPaintView.onResume();
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
        if (videoPlayer != null && playerLooping) {
            videoPlayer.setLooping(false);
        }
    }

    public boolean isVisible() {
        return isVisible && placeProvider != null;
    }

    private void updateMinMax(float scale) {
        if (currentEditMode == 3 && aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE && textureUploaded) {
            scale *= Math.min(getContainerViewWidth() / (float) videoTextureView.getMeasuredWidth(), getContainerViewHeight() / (float) videoTextureView.getMeasuredHeight());
        }
        float w = centerImage.getImageWidth();
        float h = centerImage.getImageHeight();
        if (editState.cropState != null) {
            w *= editState.cropState.cropPw;
            h *= editState.cropState.cropPh;
        }
        int maxW = (int) (w * scale - getContainerViewWidth()) / 2;
        int maxH = (int) (h * scale - getContainerViewHeight()) / 2;

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

    private int getAdditionX() {
        if (currentEditMode == 1 || currentEditMode == 0 && sendPhotoType == SELECT_TYPE_AVATAR) {
            return AndroidUtilities.dp(16);
        } else if (currentEditMode != 0 && currentEditMode != 3) {
            return AndroidUtilities.dp(14);
        }
        return 0;
    }

    private int getAdditionY() {
        if (currentEditMode == 1 || currentEditMode == 0 && sendPhotoType == SELECT_TYPE_AVATAR) {
            return AndroidUtilities.dp(16) + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
        } else if (currentEditMode == 3) {
            return AndroidUtilities.dp(8) + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
        } else if (currentEditMode != 0) {
            return AndroidUtilities.dp(14) + (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
        }
        return 0;
    }

    private int getContainerViewWidth() {
        return getContainerViewWidth(currentEditMode);
    }

    private int getContainerViewWidth(int mode) {
        int width = containerView.getWidth();
        if (mode == 1 || mode == 0 && sendPhotoType == SELECT_TYPE_AVATAR) {
            width -= AndroidUtilities.dp(32);
        } else if (mode != 0 && mode != 3) {
            width -= AndroidUtilities.dp(28);
        }
        return width;
    }

    private int getContainerViewHeight() {
        return getContainerViewHeight(currentEditMode);
    }

    private int getContainerViewHeight(int mode) {
        return getContainerViewHeight(false, mode);
    }

    private int getContainerViewHeight(boolean trueHeight, int mode) {
        int height;
        if (trueHeight || inBubbleMode) {
            height = containerView.getMeasuredHeight();
        } else {
            height = AndroidUtilities.displaySize.y;
            if (mode == 0 && sendPhotoType != SELECT_TYPE_AVATAR && isStatusBarVisible()) {
                height += AndroidUtilities.statusBarHeight;
            }
        }
        if (mode == 0 && sendPhotoType == SELECT_TYPE_AVATAR || mode == 1) {
            height -= AndroidUtilities.dp(48 + 32 + 64);
        } else if (mode == 2) {
            height -= AndroidUtilities.dp(154 + 60);
        } else if (mode == 3) {
            height -= AndroidUtilities.dp(48) + ActionBar.getCurrentActionBarHeight();
        }
        return height;
    }

    float longPressX;
    Runnable longPressRunnable = this::onLongPress;

    private boolean onTouchEvent(MotionEvent ev) {
        if (currentEditMode == 3 && animationStartTime != 0 && (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
            if (ev.getPointerCount() >= 2) {
                cancelMoveZoomAnimation();
            } else {
                return true;
            }
        }
        if (animationInProgress != 0 || animationStartTime != 0) {
            return false;
        }

        if (videoPlayerRewinder.rewindCount > 0) {
            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL ) {
                videoPlayerRewinder.cancelRewind();
                return false;
            }
            return true;
        }

        if (currentEditMode == 2) {
            photoFilterView.onTouch(ev);
            return true;
        } else if (currentEditMode == 1 || currentEditMode != 3 && sendPhotoType == SELECT_TYPE_AVATAR) {
            return true;
        }

        if (captionEditText.isPopupShowing() || captionEditText.isKeyboardVisible()) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                closeCaptionEnter(true);
            }
            return true;
        }

        if (currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR && ev.getPointerCount() == 1 && gestureDetector.onTouchEvent(ev)) {
            if (doubleTap) {
                doubleTap = false;
                moving = false;
                zooming = false;
                checkMinMax(false);
                return true;
            }
        }

        if (tooltip != null) {
            tooltip.hide();
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            discardTap = false;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            if (!draggingDown && !changingPage) {
                if (canZoom && ev.getPointerCount() == 2) {
                    if (paintViewTouched == 1) {
                        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        photoPaintView.onTouch(event);
                        event.recycle();
                        paintViewTouched = 2;
                    }
                    pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                    pinchStartScale = scale;
                    pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                    pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                    pinchStartX = translationX;
                    pinchStartY = translationY;
                    zooming = true;
                    moving = false;
                    if (currentEditMode == 3) {
                        moveStartX = pinchCenterX;
                        moveStartY = pinchCenterY;
                        draggingDown = false;
                        canDragDown = false;
                    }
                    hidePressedDrawables();
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev.getPointerCount() == 1) {
                    if (currentEditMode == 3) {
                        if (paintViewTouched == 0) {
                            photoPaintView.getHitRect(hitRect);
                            if (hitRect.contains((int) ev.getX(), (int) ev.getY())) {
                                MotionEvent event = MotionEvent.obtain(ev);
                                event.offsetLocation(-photoPaintView.getX(), -photoPaintView.getY());
                                photoPaintView.onTouch(event);
                                event.recycle();
                                paintViewTouched = 1;
                            }
                        }
                    } else {
                        moveStartX = ev.getX();
                        dragY = moveStartY = ev.getY();
                        draggingDown = false;
                        canDragDown = true;
                        if (velocityTracker != null) {
                            velocityTracker.clear();
                        }
                    }
                }
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                longPressX = ev.getX();
                AndroidUtilities.runOnUIThread(longPressRunnable, 300);
            } else {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {

            if (canZoom && ev.getPointerCount() == 2 && !draggingDown && zooming && !changingPage) {
                discardTap = true;
                if (currentEditMode == 3) {
                    float newPinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                    float newPinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                    float moveDx = moveStartX - newPinchCenterX;
                    float moveDy = moveStartY - newPinchCenterY;
                    moveStartX = newPinchCenterX;
                    moveStartY = newPinchCenterY;
                    if (translationX < minX || translationX > maxX) {
                        moveDx /= 3.0f;
                    }
                    if (translationY < minY || translationY > maxY) {
                        moveDy /= 3.0f;
                    }
                    pinchStartX = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - translationX) / (scale / pinchStartScale) - moveDx;
                    pinchStartY = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - translationY) / (scale / pinchStartScale) - moveDy;
                    pinchCenterX = newPinchCenterX;
                    pinchCenterY = newPinchCenterY;
                }
                scale = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                containerView.invalidate();
            } else if (ev.getPointerCount() == 1) {
                if (paintViewTouched == 1) {
                    MotionEvent event = MotionEvent.obtain(ev);
                    event.offsetLocation(-photoPaintView.getX(), -photoPaintView.getY());
                    photoPaintView.onTouch(event);
                    event.recycle();
                    return true;
                }
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - moveStartX);
                float dy = Math.abs(ev.getY() - dragY);
                if (dx > touchSlop || dy > touchSlop) {
                    discardTap = true;
                    hidePressedDrawables();
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    if (qualityChooseView != null && qualityChooseView.getVisibility() == View.VISIBLE) {
                        return true;
                    }
                }
                if (placeProvider.canScrollAway() && currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR && canDragDown && !draggingDown && scale == 1 && dy >= AndroidUtilities.dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    hidePressedDrawables();
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
                            hidePressedDrawables();
                        }

                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX && (currentEditMode != 0 || !rightImage.hasImageSet()) || translationX > maxX && (currentEditMode != 0 || !leftImage.hasImageSet())) {
                            moveDx /= 3.0f;
                        }
                        if (maxY == 0 && minY == 0 && currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR) {
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
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            if (paintViewTouched == 1) {
                if (photoPaintView != null) {
                    MotionEvent event = MotionEvent.obtain(ev);
                    event.offsetLocation(-photoPaintView.getX(), -photoPaintView.getY());
                    photoPaintView.onTouch(event);
                    event.recycle();
                }
                paintViewTouched = 0;
                return true;
            }
            paintViewTouched = 0;
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
                    if (currentEditMode == 3) {
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
                        animateTo(scale, moveToX, moveToY, false);
                    }
                }
                zooming = false;
                moving = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - ev.getY()) > getContainerViewHeight() / 6.0f) {
                    if (enableSwipeToPiP() && (dragY - ev.getY() > 0)) {
                        switchToPip(true);
                    } else {
                        closePhoto(true, false);
                    }
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

                if (currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR) {
                    if ((translationX < minX - getContainerViewWidth() / 3 || velocity < -AndroidUtilities.dp(650)) && rightImage.hasImageSet()) {
                        goToNext();
                        return true;
                    }
                    if ((translationX > maxX + getContainerViewWidth() / 3 || velocity > AndroidUtilities.dp(650)) && leftImage.hasImageSet()) {
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

    private void cancelMoveZoomAnimation() {
        if (imageMoveAnimation == null) {
            return;
        }

        float ts = scale + (animateToScale - scale) * animationValue;
        float tx = translationX + (animateToX - translationX) * animationValue;
        float ty = translationY + (animateToY - translationY) * animationValue;
        imageMoveAnimation.cancel();
        scale = ts;
        translationX = tx;
        translationY = ty;
        animationStartTime = 0;
        updateMinMax(scale);
        zoomAnimation = false;
        containerView.invalidate();
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
                ObjectAnimator.ofFloat(this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0, 1)
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

    private void switchToNextIndex(int add, boolean init) {
        if (currentMessageObject != null) {
            releasePlayer(false);
            FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
        } else if (currentPageBlock != null) {
            final TLObject media = pageBlocksAdapter.getMedia(currentIndex);
            if (media instanceof TLRPC.Document) {
                releasePlayer(false);
                FileLoader.getInstance(currentAccount).cancelLoadFile((TLRPC.Document) media);
            }
        }
        if (groupedPhotosListView != null) {
            groupedPhotosListView.setAnimateBackground(true);
        }
        playerAutoStarted = false;
        setImageIndex(currentIndex + add, init, true);
        if (shouldMessageObjectAutoPlayed(currentMessageObject) || shouldIndexAutoPlayed(currentIndex)) {
            playerAutoStarted = true;
            onActionClick(true);
            checkProgress(0, false, true);
        }
        checkFullscreenButton();
    }

    private boolean shouldMessageObjectAutoPlayed(MessageObject messageObject) {
        return messageObject != null && messageObject.isVideo() && (messageObject.mediaExists || messageObject.attachPathExists || messageObject.canStreamVideo() && SharedConfig.streamMedia) && SharedConfig.autoplayVideo;
    }

    private boolean shouldIndexAutoPlayed(int index) {
        if (pageBlocksAdapter != null) {
            if (pageBlocksAdapter.isVideo(index) && SharedConfig.autoplayVideo) {
                final File mediaFile = pageBlocksAdapter.getFile(index);
                if (mediaFile != null && mediaFile.exists()) {
                    return true;
                }
            }
        }
        return false;
    }

    private float getCropFillScale(boolean rotated) {
        int width = rotated ? centerImage.getBitmapHeight() : centerImage.getBitmapWidth();
        int height = rotated ? centerImage.getBitmapWidth() : centerImage.getBitmapHeight();
        float statusBarHeight = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0);
        float measuredHeight = (float) photoCropView.getMeasuredHeight() - AndroidUtilities.dp(64) - statusBarHeight;
        float minSide = Math.min(photoCropView.getMeasuredWidth(), measuredHeight) - 2 * AndroidUtilities.dp(16);
        return Math.max(minSide / width, minSide / height);
    }

    private boolean isStatusBarVisible() {
        return Build.VERSION.SDK_INT >= 21 && !inBubbleMode;
    }

    @SuppressLint({"NewApi", "DrawAllocation"})
    private void onDraw(Canvas canvas) {
        if (animationInProgress == 1 || animationInProgress == 3 || !isVisible && animationInProgress != 2 && !pipAnimationInProgress) {
            return;
        }

        if (padImageForHorizontalInsets) {
            canvas.save();
            canvas.translate(getLeftInset() / 2 - getRightInset() / 2, 0);
        }

        float currentTranslationY;
        float currentTranslationX;
        float currentScale;
        float aty = -1;
        long newUpdateTime = System.currentTimeMillis();
        long dt = newUpdateTime - videoCrossfadeAlphaLastTime;
        if (dt > 20) {
            dt = 17;
        }
        videoCrossfadeAlphaLastTime = newUpdateTime;

        if (imageMoveAnimation != null) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }

            float ts = scale + (animateToScale - scale) * animationValue;
            float tx = translationX + (animateToX - translationX) * animationValue;
            float ty = translationY + (animateToY - translationY) * animationValue;

            if (animateToScale == 1 && scale == 1 && translationX == 0) {
                aty = ty;
            }
            currentScale = ts;
            currentTranslationY = ty;
            currentTranslationX = tx;
            updateMinMax(currentScale);
            containerView.invalidate();
        } else {
            if (animationStartTime != 0) {
                translationX = animateToX;
                translationY = animateToY;
                scale = animateToScale;
                animationStartTime = 0;
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
                openedFullScreenVideo = false;
                if (!imagesArrLocals.isEmpty() && currentIndex >= 0 && currentIndex < imagesArrLocals.size()) {
                    Object object = imagesArrLocals.get(currentIndex);
                    if (object instanceof MediaController.MediaEditState) {
                        ((MediaController.MediaEditState) object).editedInfo = getCurrentVideoEditedInfo();
                    }
                }
                if (switchImageAfterAnimation == 1) {
                    AndroidUtilities.runOnUIThread(() -> switchToNextIndex(1, false));
                } else if (switchImageAfterAnimation == 2) {
                    AndroidUtilities.runOnUIThread(() -> switchToNextIndex(-1, false));
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

        if (photoViewerWebView != null) {
            photoViewerWebView.setTranslationY(currentTranslationY);
        }

        if (isActionBarVisible) {
            if (currentScale <= 1.0001f) {
                if (!allowShowFullscreenButton && fullscreenButton[0].getTag() == null) {
                    fullscreenButton[0].animate().alpha(1.0f).setDuration(120).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            fullscreenButton[0].setTag(null);
                        }
                    }).start();
                    fullscreenButton[0].setTag(1);
                    allowShowFullscreenButton = true;
                }
            } else {
                if (allowShowFullscreenButton) {
                    fullscreenButton[0].animate().alpha(0.0f).setDuration(120).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            fullscreenButton[0].setTag(null);
                        }
                    }).start();
                    fullscreenButton[0].setTag(1);
                    allowShowFullscreenButton = false;
                }
            }
        }

        int containerWidth = getContainerViewWidth();
        int containerHeight = getContainerViewHeight();
        if (animationInProgress != 2 && animationInProgress != 4 && !pipAnimationInProgress && !isInline) {
            if (currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR && scale == 1 && aty != -1 && !zoomAnimation) {
                float maxValue = containerWidth / 4.0f;
                backgroundDrawable.setAlpha((int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(aty), maxValue) / maxValue))));
            } else {
                backgroundDrawable.setAlpha(255);
            }
        } else if (animationInProgress == 4) {
            canvas.drawColor(0xff000000);
        }

        sideImage = null;
        if (currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR) {
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

        for (int a = 0; a < 3; a++) {
            float offsetX;
            if (a == 1) {
                offsetX = 0;
            } else if (a == 2) {
                offsetX = -containerView.getMeasuredWidth() - AndroidUtilities.dp(15) + (currentTranslationX - maxX);
            } else {
                offsetX = currentTranslationX < minX ? (currentTranslationX - minX) : 0;
            }
            fullscreenButton[a].setTranslationX(offsetX + containerView.getMeasuredWidth() - AndroidUtilities.dp(48));
        }

        if (sideImage == rightImage) {
            float translateX = currentTranslationX;
            float scaleDiff = 0;
            float alpha = 1;
            if (!zoomAnimation && translateX < minX) {
                alpha = Math.min(1.0f, (minX - translateX) / containerWidth);
                scaleDiff = (1.0f - alpha) * 0.3f;
                translateX = -containerWidth - AndroidUtilities.dp(30) / 2;
            }

            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(containerWidth / 2, containerHeight / 2);
                canvas.translate(containerWidth + AndroidUtilities.dp(30) / 2 + translateX, 0);
                canvas.scale(1.0f - scaleDiff, 1.0f - scaleDiff);
                int bitmapWidth = sideImage.getBitmapWidth();
                int bitmapHeight = sideImage.getBitmapHeight();

                float scaleX = containerWidth / (float) bitmapWidth;
                float scaleY = containerHeight / (float) bitmapHeight;
                float scale = Math.min(scaleX, scaleY);
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
            canvas.translate((containerWidth * (scale + 1) + AndroidUtilities.dp(30)) / 2, -currentTranslationY / currentScale);
            photoProgressViews[1].setScale(1.0f - scaleDiff);
            photoProgressViews[1].setAlpha(alpha);
            photoProgressViews[1].onDraw(canvas);

            if (isActionBarVisible) {
                fullscreenButton[1].setAlpha(alpha);
            }

            canvas.restore();
        } else {
            if (isActionBarVisible) {
                fullscreenButton[1].setAlpha(0.0f);
            }
        }

        float translateX = currentTranslationX;
        float scaleDiff = 0;
        float alpha = 1;
        if (!zoomAnimation && translateX > maxX && currentEditMode == 0 && sendPhotoType != SELECT_TYPE_AVATAR) {
            alpha = Math.min(1.0f, (translateX - maxX) / containerWidth);
            scaleDiff = alpha * 0.3f;
            alpha = 1.0f - alpha;
            translateX = maxX;
        }
        boolean drawTextureView = videoSizeSet && aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;
        if (centerImage.hasBitmapImage() || drawTextureView && textureUploaded) {
            canvas.save();
            canvas.translate(containerWidth / 2 + getAdditionX(), containerHeight / 2 + getAdditionY());
            canvas.translate(translateX, currentTranslationY + (currentEditMode != 3 ? currentPanTranslationY : currentPanTranslationY / 2));
            canvas.scale(currentScale - scaleDiff, currentScale - scaleDiff);
            if (currentEditMode == 3 && keyboardSize > AndroidUtilities.dp(20)) {
                int trueH = getContainerViewHeight(true, 0);
                int h = getContainerViewHeight(false, 0);
                if (trueH != h) {
                    canvas.translate(0, (trueH - h) / 2);
                }
            }

            boolean drawCenterImage = false;
            if (!pipAnimationInProgress && (!drawTextureView || !textureUploaded && !videoSizeSet || !videoCrossfadeStarted || videoCrossfadeAlpha != 1.0f)) {
                if (videoFrameBitmap != null && isCurrentVideo) {
                    int w = videoFrameBitmap.getWidth(), h = videoFrameBitmap.getHeight(), l = -w / 2, t = -h / 2;
                    if (alpha < 1f) {
                        canvas.saveLayerAlpha(l, t, l + w, t + h, (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                    }
                    canvas.drawBitmap(videoFrameBitmap, l, t, videoFrameBitmapPaint);
                    if (alpha < 1f) {
                        canvas.restore();
                    }
                } else {
                    centerImage.setAlpha(alpha);
                    int width = centerImage.getBitmapWidth();
                    int height = centerImage.getBitmapHeight();
                    float scale;
                    if (isCurrentVideo && currentEditMode == 0 && sendPhotoType == SELECT_TYPE_AVATAR) {
                        scale = getCropFillScale(false);
                    } else {
                        scale = Math.min(containerWidth / (float) width, containerHeight / (float) height);
                    }
                    width *= scale;
                    height *= scale;
                    centerImage.setImageCoords(-width / 2, -height / 2, width, height);
                    if (isCurrentVideo) {
                        centerImage.draw(canvas);
                    } else {
                        drawCenterImage = true;
                    }
                }
            }

            int bitmapWidth, originalWidth;
            int bitmapHeight, originalHeight;
            if (drawTextureView && textureUploaded && videoSizeSet) {
                originalWidth = bitmapWidth = videoTextureView.getMeasuredWidth();
                originalHeight = bitmapHeight = videoTextureView.getMeasuredHeight();
            } else {
                originalWidth = bitmapWidth = centerImage.getBitmapWidth();
                originalHeight = bitmapHeight = centerImage.getBitmapHeight();
            }

            float scale = Math.min(containerWidth / (float) originalWidth, containerHeight / (float) originalHeight);
            int width = (int) (originalWidth * scale);
            int height = (int) (originalHeight * scale);

            boolean applyCrop;
            float scaleToFitX = 1.0f;
            if (!imagesArrLocals.isEmpty()) {
                if (currentEditMode == 3 || switchingToMode == 3) {
                    applyCrop = true;
                } else if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    applyCrop = (switchingToMode == 0 || currentEditMode != 3 && currentEditMode != 2);
                } else {
                    applyCrop = imageMoveAnimation != null && switchingToMode != -1 || currentEditMode == 0 || currentEditMode == 1 || switchingToMode != -1;
                }
            } else {
                applyCrop = false;
            }
            if (applyCrop) {
                int rotatedWidth = originalWidth;
                int rotatedHeight = originalHeight;
                int orientation = cropTransform.getOrientation();
                if (orientation == 90 || orientation == 270) {
                    int temp = bitmapWidth;
                    bitmapWidth = bitmapHeight;
                    bitmapHeight = temp;

                    temp = rotatedWidth;
                    rotatedWidth = rotatedHeight;
                    rotatedHeight = temp;
                }
                float cropAnimationValue;
                if (sendPhotoType != SELECT_TYPE_AVATAR && (currentEditMode == 3 || switchingToMode == 3)) {
                    cropAnimationValue = 1.0f;
                } else if (imageMoveAnimation != null && switchingToMode != -1) {
                    if (currentEditMode == 1 || switchingToMode == 1 || (currentEditMode == 2 || currentEditMode == 3) && switchingToMode == -1) {
                        cropAnimationValue = 1.0f;
                    } else if (switchingToMode == 0) {
                        cropAnimationValue = animationValue;
                    } else {
                        cropAnimationValue = 1.0f - animationValue;
                    }
                } else {
                    cropAnimationValue = currentEditMode == 2 || currentEditMode == 3 ? 0.0f : 1.0f;
                }
                float cropPw = cropTransform.getCropPw();
                float cropPh = cropTransform.getCropPh();
                bitmapWidth *= cropPw + (1.0f - cropPw) * (1.0f - cropAnimationValue);
                bitmapHeight *= cropPh + (1.0f - cropPh) * (1.0f - cropAnimationValue);
                scaleToFitX = containerWidth / (float) bitmapWidth;
                if (scaleToFitX * bitmapHeight > containerHeight) {
                    scaleToFitX = containerHeight / (float) bitmapHeight;
                }
                if (sendPhotoType != SELECT_TYPE_AVATAR && (currentEditMode != 1 || switchingToMode == 0) && editState.cropState != null) {
                    float startW = bitmapWidth * scaleToFitX;
                    float startH = bitmapHeight * scaleToFitX;
                    float originalScaleToFitX = containerWidth / (float) originalWidth;
                    if (originalScaleToFitX * originalHeight > containerHeight) {
                        originalScaleToFitX = containerHeight / (float) originalHeight;
                    }
                    float finalW = originalWidth * originalScaleToFitX / (currentScale - scaleDiff);
                    float finalH = originalHeight * originalScaleToFitX / (currentScale - scaleDiff);

                    float w = startW + (finalW - startW) * (1.0f - cropAnimationValue);
                    float h = startH + (finalH - startH) * (1.0f - cropAnimationValue);

                    canvas.clipRect(-w / 2, -h / 2, w / 2, h / 2);
                }
                if (sendPhotoType == SELECT_TYPE_AVATAR || cropTransform.hasViewTransform()) {
                    float cropScale;
                    if (currentEditMode == 1 || sendPhotoType == SELECT_TYPE_AVATAR) {
                        if (videoTextureView != null) {
                            videoTextureView.setScaleX(cropTransform.isMirrored() ? -1.0f : 1.0f);
                            if (firstFrameView != null) {
                                firstFrameView.setScaleX(videoTextureView.getScaleX());
                            }
                        }
                        float trueScale = 1.0f + (cropTransform.getTrueCropScale() - 1.0f) * (1.0f - cropAnimationValue);
                        cropScale = cropTransform.getScale() / trueScale;
                        float scaleToFit = containerWidth / (float) rotatedWidth;
                        if (scaleToFit * rotatedHeight > containerHeight) {
                            scaleToFit = containerHeight / (float) rotatedHeight;
                        }
                        cropScale *= scaleToFit / scale;
                        if (sendPhotoType == SELECT_TYPE_AVATAR) {
                            if (currentEditMode == 3 || switchingToMode == 3) {
                                cropScale /= 1.0f + (cropTransform.getMinScale() - 1.0f) * (1.0f - cropAnimationValue);
                            } else if (switchingToMode == 0) {
                                cropScale /= cropTransform.getMinScale();
                            }
                        }
                    } else {
                        if (videoTextureView != null) {
                            videoTextureView.setScaleX(editState.cropState != null && editState.cropState.mirrored ? -1.0f : 1.0f);
                            if (firstFrameView != null) {
                                firstFrameView.setScaleX(videoTextureView.getScaleX());
                            }
                        }
                        cropScale = editState.cropState != null ? editState.cropState.cropScale : 1.0f;
                        float trueScale = 1.0f + (cropScale - 1.0f) * (1.0f - cropAnimationValue);
                        cropScale *= scaleToFitX / scale / trueScale;
                    }

                    canvas.translate(cropTransform.getCropAreaX() * cropAnimationValue, cropTransform.getCropAreaY() * cropAnimationValue);
                    canvas.scale(cropScale, cropScale);
                    canvas.translate(cropTransform.getCropPx() * rotatedWidth * scale * cropAnimationValue, cropTransform.getCropPy() * rotatedHeight * scale * cropAnimationValue);
                    float rotation = (cropTransform.getRotation() + orientation);
                    if (rotation > 180) {
                        rotation -= 360;
                    }
                    if (sendPhotoType == SELECT_TYPE_AVATAR && (currentEditMode == 3 || switchingToMode == 3)) {
                        canvas.rotate(rotation);
                    } else {
                        canvas.rotate(rotation * cropAnimationValue);
                    }
                } else {
                    if (videoTextureView != null) {
                        videoTextureView.setScaleX(1.0f);
                        videoTextureView.setScaleY(1.0f);
                        if (firstFrameView != null) {
                            firstFrameView.setScaleX(1);
                            firstFrameView.setScaleY(1);
                        }
                    }
                }
            }
            if (currentEditMode == 3) {
                float add = containerView.adjustPanLayoutHelper.animationInProgress() ? keyboardSize / 2f + currentPanTranslationY / 2 : 0;
                photoPaintView.setTransform(currentScale, currentTranslationX, currentTranslationY + add, bitmapWidth * scaleToFitX, bitmapHeight * scaleToFitX);
            }

            if (drawCenterImage) {
                boolean mirror = false;
                if (!imagesArrLocals.isEmpty()) {
                    if (currentEditMode == 1 || sendPhotoType == SELECT_TYPE_AVATAR) {
                        mirror = cropTransform.isMirrored();
                    } else {
                        mirror = editState.cropState != null && editState.cropState.mirrored;
                    }
                }
                if (mirror) {
                    canvas.save();
                    canvas.scale(-1, 1);
                }
                if (photoViewerWebView == null || !photoViewerWebView.isLoaded()) {
                    centerImage.draw(canvas);
                }
                if (mirror) {
                    canvas.restore();
                }
            }
            canvas.translate(-width / 2, -height / 2);
            if (drawTextureView || paintingOverlay.getVisibility() == View.VISIBLE) {
                canvas.scale(scale, scale);
            }
            if (drawTextureView) {
                if (!videoCrossfadeStarted && textureUploaded && videoSizeSet) {
                    videoCrossfadeStarted = true;
                    videoCrossfadeAlpha = 0.0f;
                    videoCrossfadeAlphaLastTime = System.currentTimeMillis();
                    containerView.getMeasuredHeight();
                }
                videoTextureView.setAlpha(alpha * videoCrossfadeAlpha);
                if (videoTextureView instanceof VideoEditTextureView) {
                    VideoEditTextureView videoEditTextureView = (VideoEditTextureView) videoTextureView;
                    videoEditTextureView.setViewRect((containerWidth - width) / 2 + getAdditionX() + translateX, (containerHeight - height) / 2 + getAdditionY() + currentTranslationY + currentPanTranslationY, width, height);
                }
                aspectRatioFrameLayout.draw(canvas);
                if (videoCrossfadeStarted && videoCrossfadeAlpha < 1.0f) {
                    videoCrossfadeAlpha += dt / (playerInjected ? 100.0f : 200.0f);
                    containerView.invalidate();
                    if (videoCrossfadeAlpha > 1.0f) {
                        videoCrossfadeAlpha = 1.0f;
                    }
                }
                paintingOverlay.setAlpha(alpha);
            }
            if (paintingOverlay.getVisibility() == View.VISIBLE && (isCurrentVideo || currentEditMode != 2 || switchingToMode != -1)) {
                canvas.clipRect(0, 0, paintingOverlay.getMeasuredWidth(), paintingOverlay.getMeasuredHeight());
                paintingOverlay.draw(canvas);
            }
            canvas.restore();
            for (int a = 0; a < pressedDrawable.length; a++) {
                if (drawPressedDrawable[a] || pressedDrawableAlpha[a] != 0) {
                    pressedDrawable[a].setAlpha((int) (pressedDrawableAlpha[a] * 255));
                    if (a == 0) {
                        pressedDrawable[a].setBounds(0, 0, containerView.getMeasuredWidth() / 5, containerView.getMeasuredHeight());
                    } else {
                        pressedDrawable[a].setBounds(containerView.getMeasuredWidth() - containerView.getMeasuredWidth() / 5, 0, containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                    }
                    pressedDrawable[a].draw(canvas);
                }
                if (drawPressedDrawable[a]) {
                    if (pressedDrawableAlpha[a] < 1.0f) {
                        pressedDrawableAlpha[a] += dt / 180.0f;
                        if (pressedDrawableAlpha[a] > 1.0f) {
                            pressedDrawableAlpha[a] = 1.0f;
                        }
                        containerView.invalidate();
                    }
                } else {
                    if (pressedDrawableAlpha[a] > 0.0f) {
                        pressedDrawableAlpha[a] -= dt / 180.0f;
                        if (pressedDrawableAlpha[a] < 0.0f) {
                            pressedDrawableAlpha[a] = 0.0f;
                        }
                        containerView.invalidate();
                    }
                }
            }
        }
        boolean drawProgress;
        if (isCurrentVideo) {
            drawProgress = (videoTimelineView == null || !videoTimelineView.isDragging()) && (sendPhotoType != SELECT_TYPE_AVATAR || manuallyPaused) && (videoPlayer == null || !videoPlayer.isPlaying());
        } else {
            drawProgress = true;
        }
        boolean drawMiniProgress = miniProgressView.getVisibility() == View.VISIBLE || miniProgressAnimator != null;
        if (drawProgress) {
            final float tx = !zoomAnimation && -translateX > maxX ? translateX + maxX : 0;
            float ty = currentScale == 1.0f ? currentTranslationY : 0;
            float progressAlpha = alpha;
            if (drawMiniProgress) {
                progressAlpha *= 1f - miniProgressView.getAlpha();
            }
            if (pipAnimationInProgress) {
                progressAlpha *= actionBar.getAlpha();
            } else if (photoProgressViews[0].backgroundState == PROGRESS_PAUSE) {
                ty += AndroidUtilities.dpf2(8) * (1f - actionBar.getAlpha());
            }
            canvas.save();
            canvas.translate(tx, ty);
            photoProgressViews[0].setScale(1.0f - scaleDiff);
            photoProgressViews[0].setAlpha(progressAlpha);
            photoProgressViews[0].onDraw(canvas);

            if (isActionBarVisible && allowShowFullscreenButton && fullscreenButton[0].getTag() == null) {
                fullscreenButton[0].setAlpha(Math.min(fullscreenButton[0].getAlpha(), alpha));
            }

            canvas.restore();
        }
        if (drawMiniProgress && !pipAnimationInProgress) {
            canvas.save();
            canvas.translate(miniProgressView.getLeft() + translateX, miniProgressView.getTop() + currentTranslationY / currentScale);
            miniProgressView.draw(canvas);
            canvas.restore();
        }

        if (sideImage == leftImage) {
            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(containerWidth / 2, containerHeight / 2);
                canvas.translate(-(containerWidth * (scale + 1) + AndroidUtilities.dp(30)) / 2 + currentTranslationX, 0);
                int bitmapWidth = sideImage.getBitmapWidth();
                int bitmapHeight = sideImage.getBitmapHeight();

                float scaleX = containerWidth / (float) bitmapWidth;
                float scaleY = containerHeight / (float) bitmapHeight;
                float scale = Math.min(scaleX, scaleY);
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
            canvas.translate(-(containerWidth * (scale + 1) + AndroidUtilities.dp(30)) / 2, -currentTranslationY / currentScale);
            photoProgressViews[2].setScale(1.0f);
            photoProgressViews[2].setAlpha(1.0f);
            photoProgressViews[2].onDraw(canvas);

            if (isActionBarVisible) {
                fullscreenButton[2].setAlpha(1.0f);
            }
            canvas.restore();
        } else {
            if (isActionBarVisible) {
                fullscreenButton[2].setAlpha(0.0f);
            }
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
                if (pipVideoView != null) {
                    pipVideoView.close();
                    pipVideoView = null;
                }
            } else {
                containerView.invalidate();
            }
        }

        if (padImageForHorizontalInsets) {
            canvas.restore();
        }

        if (aspectRatioFrameLayout != null && videoForwardDrawable.isAnimating()) {
            int h = (int) (aspectRatioFrameLayout.getMeasuredHeight() * (scale - 1.0f)) / 2;
            videoForwardDrawable.setBounds(aspectRatioFrameLayout.getLeft(), aspectRatioFrameLayout.getTop() - h + (int) (currentTranslationY / currentScale), aspectRatioFrameLayout.getRight(), aspectRatioFrameLayout.getBottom() + h + (int) (currentTranslationY / currentScale));
            videoForwardDrawable.draw(canvas);
        }
    }

    private void onActionClick(boolean download) {
        if (currentMessageObject == null && currentBotInlineResult == null && pageBlocksAdapter == null || currentFileNames[0] == null) {
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
                    if (SharedConfig.streamMedia && !DialogObject.isEncryptedDialog(currentMessageObject.getDialogId()) && currentMessageObject.isVideo() && currentMessageObject.canStreamVideo()) {
                        try {
                            int reference = FileLoader.getInstance(currentMessageObject.currentAccount).getFileReference(currentMessageObject);
                            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, 1, 0);
                            TLRPC.Document document = currentMessageObject.getDocument();
                            String params = "?account=" + currentMessageObject.currentAccount +
                                    "&id=" + document.id +
                                    "&hash=" + document.access_hash +
                                    "&dc=" + document.dc_id +
                                    "&size=" + document.size +
                                    "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                                    "&rid=" + reference +
                                    "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                                    "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
                            uri = Uri.parse("tg://" + currentMessageObject.getFileName() + params);
                            isStreaming = true;
                            checkProgress(0, false, false);
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
        } else if (pageBlocksAdapter != null) {
            TLObject media = pageBlocksAdapter.getMedia(currentIndex);
            if (!(media instanceof TLRPC.Document)) {
                return;
            }
            file = pageBlocksAdapter.getFile(currentIndex);
            if (file != null && !file.exists()) {
                file = null;
            }
        }
        if (file != null && uri == null) {
            uri = Uri.fromFile(file);
        }
        if (uri == null) {
            if (download) {
                if (currentMessageObject !=  null) {
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[0])) {
                        FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, 1, 0);
                    } else {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
                    }
                } else if (currentBotInlineResult != null) {
                    if (currentBotInlineResult.document != null) {
                        if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[0])) {
                            FileLoader.getInstance(currentAccount).loadFile(currentBotInlineResult.document, currentMessageObject, 1, 0);
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
                } else if (pageBlocksAdapter != null) {
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(currentFileNames[0])) {
                        FileLoader.getInstance(currentAccount).loadFile((TLRPC.Document) pageBlocksAdapter.getMedia(currentIndex), pageBlocksAdapter.getParentObject(), 1, 1);
                    } else {
                        FileLoader.getInstance(currentAccount).cancelLoadFile((TLRPC.Document) pageBlocksAdapter.getMedia(currentIndex));
                    }
                }
                Drawable drawable = centerImage.getStaticThumb();
                if (drawable instanceof OtherDocumentPlaceholderDrawable) {
                    ((OtherDocumentPlaceholderDrawable) drawable).checkFileExist();
                }
            }
        } else {
            if (sharedMediaType == MediaDataController.MEDIA_FILE && !currentMessageObject.canPreviewDocument()) {
                AndroidUtilities.openDocument(currentMessageObject, parentActivity, null);
                return;
            }
            preparePlayer(uri, true, false);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (!doubleTap && checkImageView.getVisibility() != View.VISIBLE && !drawPressedDrawable[0] && !drawPressedDrawable[1]) {
            float x = e.getX();
            int side = Math.min(135, containerView.getMeasuredWidth() / 8);
            if (x < side) {
                if (leftImage.hasImageSet()) {
                    drawPressedDrawable[0] = true;
                    containerView.invalidate();
                }
            } else if (x > containerView.getMeasuredWidth() - side) {
                if (rightImage.hasImageSet()) {
                    drawPressedDrawable[1] = true;
                    containerView.invalidate();
                }
            }
        }
        return false;
    }

    @Override
    public boolean canDoubleTap(MotionEvent e) {
        if (checkImageView.getVisibility() != View.VISIBLE && !drawPressedDrawable[0] && !drawPressedDrawable[1]) {
            float x = e.getX();
            int side = Math.min(135, containerView.getMeasuredWidth() / 8);
            if (x < side || x > containerView.getMeasuredWidth() - side) {
                return currentMessageObject == null || currentMessageObject.isVideo() && (SystemClock.elapsedRealtime() - lastPhotoSetTime) >= 500 && canDoubleTapSeekVideo(e);
            }
        }
        return true;
    }

    private void hidePressedDrawables() {
        drawPressedDrawable[0] = drawPressedDrawable[1] = false;
        containerView.invalidate();
    }

    @Override
    public void onUp(MotionEvent e) {
        hidePressedDrawables();
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (!canZoom && !doubleTapEnabled) {
            return onSingleTapConfirmed(e);
        }
        if (containerView.getTag() != null && photoProgressViews[0] != null && containerView != null) {
            float x = e.getX();
            float y = e.getY();
            boolean rez = false;
            if (x >= (getContainerViewWidth() - AndroidUtilities.dp(100)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(100)) / 2.0f &&
                    y >= (getContainerViewHeight() - AndroidUtilities.dp(100)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(100)) / 2.0f) {
                rez = onSingleTapConfirmed(e);
            }
            if (rez) {
                discardTap = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent ev) {

    }

    public void onLongPress() {
        if (videoPlayer != null && videoPlayerControlVisible && scale <= 1.1f) {
            long current = videoPlayer.getCurrentPosition();
            long total = videoPlayer.getDuration();
            if (current == C.TIME_UNSET || total < 15 * 1000) {
                return;
            }
            float x = longPressX;
            int width = getContainerViewWidth();
            boolean forward;
            if (x >= width / 3 * 2) {
                forward = true;
            } else if (x < width / 3) {
                forward = false;
            } else {
                return;
            }
            videoPlayerRewinder.startRewind(videoPlayer, forward, currentVideoSpeed);
        }
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
        float x = e.getX();
        float y = e.getY();
        if (checkImageView.getVisibility() != View.VISIBLE) {
            if (y > ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(40)) {
                int side = Math.min(135, containerView.getMeasuredWidth() / 8);
                if (x < side) {
                    if (leftImage.hasImageSet()) {
                        switchToNextIndex(-1, true);
                        return true;
                    }
                } else if (x > containerView.getMeasuredWidth() - side) {
                    if (rightImage.hasImageSet()) {
                        switchToNextIndex(1, true);
                        return true;
                    }
                }
            }
        }
        if (containerView.getTag() != null) {
            boolean drawTextureView = aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;

            if (sharedMediaType == MediaDataController.MEDIA_FILE && currentMessageObject != null) {
                if (!currentMessageObject.canPreviewDocument()) {
                    float vy = (getContainerViewHeight() - AndroidUtilities.dp(360)) / 2.0f;
                    if (y >= vy && y <= vy + AndroidUtilities.dp(360)) {
                        onActionClick(true);
                        return true;
                    }
                }
            } else {
                if (photoProgressViews[0] != null && containerView != null) {
                    int state = photoProgressViews[0].backgroundState;
                    if (x >= (getContainerViewWidth() - AndroidUtilities.dp(100)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(100)) / 2.0f &&
                            y >= (getContainerViewHeight() - AndroidUtilities.dp(100)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(100)) / 2.0f) {
                        if (!drawTextureView) {
                            if (state > PROGRESS_EMPTY && state <= PROGRESS_PLAY) {
                                onActionClick(true);
                                checkProgress(0, false, true);
                                return true;
                            }
                        } else {
                            if (state == PROGRESS_PLAY || state == PROGRESS_PAUSE) {
                                if (photoProgressViews[0].isVisible()) {
                                    manuallyPaused = true;
                                    toggleVideoPlayer();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            toggleActionBar(!isActionBarVisible, true);
        } else if (sendPhotoType == 0 || sendPhotoType == 4) {
            if (isCurrentVideo) {
                if (videoPlayer != null && !muteVideo && sendPhotoType != SELECT_TYPE_AVATAR) {
                    videoPlayer.setVolume(1.0f);
                }
                manuallyPaused = true;
                toggleVideoPlayer();
            } else {
                checkImageView.performClick();
            }
        } else if (currentBotInlineResult != null && (currentBotInlineResult.type.equals("video") || MessageObject.isVideoDocument(currentBotInlineResult.document))) {
            int state = photoProgressViews[0].backgroundState;
            if (state > 0 && state <= 3) {
                if (x >= (getContainerViewWidth() - AndroidUtilities.dp(100)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(100)) / 2.0f &&
                        y >= (getContainerViewHeight() - AndroidUtilities.dp(100)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(100)) / 2.0f) {
                    onActionClick(true);
                    checkProgress(0, false, true);
                    return true;
                }
            }
        } else if (sendPhotoType == 2) {
            if (isCurrentVideo) {
                manuallyPaused = true;
                toggleVideoPlayer();
            }
        }
        return true;
    }

    private boolean canDoubleTapSeekVideo(MotionEvent e) {
        if (videoPlayer == null) {
            return false;
        }
        int width = getContainerViewWidth();
        float x = e.getX();
        boolean forward = x >= width / 3 * 2;
        long current = videoPlayer.getCurrentPosition();
        long total = videoPlayer.getDuration();
        return current != C.TIME_UNSET && total > 15 * 1000 && (!forward || total - current > 10000);
    }

    long totalRewinding;

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (videoPlayer != null && videoPlayerControlVisible) {
            long current = videoPlayer.getCurrentPosition();
            long total = videoPlayer.getDuration();
            float x = e.getX();
            int width = getContainerViewWidth();
            boolean forward = x >= width / 3 * 2;
            if (canDoubleTapSeekVideo(e)) {
                long old = current;
                if (x >= width / 3 * 2) {
                    current += 10000;
                } else if (x < width / 3) {
                    current -= 10000;
                }
                if (old != current) {
                    boolean apply = true;
                    if (current > total) {
                        current = total;
                    } else if (current < 0) {
                        if (current < -9000) {
                            apply = false;
                        }
                        current = 0;
                    }
                    if (apply) {
                        videoForwardDrawable.setOneShootAnimation(true);
                        videoForwardDrawable.setLeftSide(x < width / 3);
                        videoForwardDrawable.addTime(10000);
                        videoPlayer.seekTo(current);
                        containerView.invalidate();
                        videoPlayerSeekbar.setProgress(current / (float) total, true);
                        videoPlayerSeekbarView.invalidate();
                    }
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
        hidePressedDrawables();
        return true;
    }

    private boolean enableSwipeToPiP() {
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            return false;
        }
        boolean permissionsEnabled = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(parentActivity);
        return pipAvailable && textureUploaded && videoPlayer != null && videoPlayer.getRepeatCount() == 0 && permissionsEnabled && !(changingTextureView || switchingInlineMode || isInline);
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
    private TextView videoAvatarTooltip;
    private AnimatorSet qualityChooseViewAnimation;
    private ObjectAnimator videoTimelineAnimator;

    private long captureFrameAtTime = -1;
    private long captureFrameReadyAtTime = -1;
    private long needCaptureFrameReadyAtTime = -1;

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
    private boolean videoConvertSupported;
    private long startTime;
    private long endTime;
    private float videoCutStart;
    private float videoCutEnd;
    private long audioFramesSize;
    private long videoFramesSize;
    private long estimatedSize;
    private long estimatedDuration;
    private long originalSize;
    private long avatarStartTime;
    private float avatarStartProgress;

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

        private String lowQualityDescription;
        private String hightQualityDescription;

        private int startMovingQuality;

        public QualityChooseView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(14));
            textPaint.setColor(0xffcdcdcd);

            lowQualityDescription = LocaleController.getString("AccDescrVideoCompressLow", R.string.AccDescrVideoCompressLow);
            hightQualityDescription = LocaleController.getString("AccDescrVideoCompressHigh", R.string.AccDescrVideoCompressHigh);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startMovingQuality = selectedCompression;
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {

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

            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (selectedCompression != startMovingQuality) {
                    requestVideoPreview(1);
                }
                moving = false;
            }
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            circleSize = AndroidUtilities.dp(8);
            gapSize = AndroidUtilities.dp(2);
            sideSide = AndroidUtilities.dp(18);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (compressionsCount != 1) {
                lineSize = (getMeasuredWidth() - circleSize * compressionsCount - gapSize * (compressionsCount * 2 - 2) - sideSide * 2) / (compressionsCount - 1);
            } else {
                lineSize = (getMeasuredWidth() - circleSize * compressionsCount - gapSize * 2 - sideSide * 2);
            }
            int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(6);
            for (int a = 0; a < compressionsCount; a++) {
                int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                if (a <= selectedCompression) {
                    paint.setColor(0xff53aeef);
                } else {
                    paint.setColor(0x66ffffff);
                }

                canvas.drawCircle(cx, cy, a == selectedCompression ? AndroidUtilities.dp(6) : circleSize / 2, paint);

                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    float startPadding = a == (selectedCompression + 1) ? AndroidUtilities.dpf2(2) : 0;
                    float endPadding = a == selectedCompression ? AndroidUtilities.dpf2(2) : 0;
                    canvas.drawRect(x + startPadding, cy - AndroidUtilities.dp(1), x + lineSize - endPadding, cy + AndroidUtilities.dp(2), paint);
                }
            }

            canvas.drawText(lowQualityDescription, sideSide, cy - AndroidUtilities.dp(16), textPaint);
            float width = textPaint.measureText(hightQualityDescription);
            canvas.drawText(hightQualityDescription, getMeasuredWidth() - sideSide - width, cy - AndroidUtilities.dp(16), textPaint);
        }
    }

    public void updateMuteButton() {
        if (videoPlayer != null) {
            videoPlayer.setMute(muteVideo);
        }
        if (!videoConvertSupported) {
            muteItem.setEnabled(false);
            muteItem.setClickable(false);
            muteItem.animate().alpha(0.5f).setDuration(180).start();
            videoTimelineView.setMode(VideoTimelinePlayView.MODE_VIDEO);
        } else {
            muteItem.setEnabled(true);
            muteItem.setClickable(true);
            muteItem.animate().alpha(1f).setDuration(180).start();
            if (muteVideo) {
                actionBar.setSubtitle(LocaleController.getString("SoundMuted", R.string.SoundMuted));
                muteItem.setImageResource(R.drawable.video_send_mute);
                if (compressItem.getTag() != null) {
                    compressItem.setAlpha(0.5f);
                    compressItem.setEnabled(false);
                }
                if (sendPhotoType == SELECT_TYPE_AVATAR) {
                    videoTimelineView.setMaxProgressDiff(9600.0f / videoDuration);
                    videoTimelineView.setMode(VideoTimelinePlayView.MODE_AVATAR);
                    updateVideoInfo();
                } else {
                    videoTimelineView.setMaxProgressDiff(1.0f);
                    videoTimelineView.setMode(VideoTimelinePlayView.MODE_VIDEO);
                }
                muteItem.setContentDescription(LocaleController.getString("NoSound", R.string.NoSound));
            } else {
                actionBar.setSubtitle(currentSubtitle);
                muteItem.setImageResource(R.drawable.video_send_unmute);
                muteItem.setContentDescription(LocaleController.getString("Sound", R.string.Sound));
                if (compressItem.getTag() != null) {
                    compressItem.setAlpha(1.0f);
                    compressItem.setEnabled(true);
                }
                videoTimelineView.setMaxProgressDiff(1.0f);
                videoTimelineView.setMode(VideoTimelinePlayView.MODE_VIDEO);
            }
        }
    }

    private void didChangedCompressionLevel(boolean request) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(String.format("compress_video_%d", compressionsCount), selectedCompression);
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

        int compressIconWidth = 64;
        if (selectedCompression < 2) {
            compressItem.setImageResource(R.drawable.video_quality1);
        } else if (selectedCompression == 2) {
            compressItem.setImageResource(R.drawable.video_quality2);
        } else if (selectedCompression == 3) {
            compressItem.setImageResource(R.drawable.video_quality3);
        }
        itemsLayout.requestLayout();

        estimatedDuration = (long) Math.ceil((videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * videoDuration);

        int width;
        int height;

        if (muteVideo) {
            width = rotationValue == 90 || rotationValue == 270 ? resultHeight : resultWidth;
            height = rotationValue == 90 || rotationValue == 270 ? resultWidth : resultHeight;
            int bitrate;
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                if (estimatedDuration <= 2000) {
                    bitrate = 2600000;
                } else if (estimatedDuration <= 5000) {
                    bitrate = 2200000;
                } else {
                    bitrate = 1560000;
                }
            } else {
                bitrate = 921600;
            }
            estimatedSize = (long) (bitrate / 8 * (estimatedDuration / 1000.0f));
            estimatedSize += estimatedSize / (32 * 1024) * 16;
        } else if (compressItem.getTag() == null) {
            width = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
            height = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
            estimatedSize = (long) (originalSize * ((float) estimatedDuration / videoDuration));
        } else {
            width = rotationValue == 90 || rotationValue == 270 ? resultHeight : resultWidth;
            height = rotationValue == 90 || rotationValue == 270 ? resultWidth : resultHeight;

            estimatedSize = (long) (((sendPhotoType == SELECT_TYPE_AVATAR ? 0 : audioFramesSize) + videoFramesSize) * ((float) estimatedDuration / videoDuration));
            estimatedSize += estimatedSize / (32 * 1024) * 16;
        }

        videoCutStart = videoTimelineView.getLeftProgress();
        videoCutEnd = videoTimelineView.getRightProgress();
        if (videoCutStart == 0) {
            startTime = -1;
        } else {
            startTime = (long) (videoCutStart * videoDuration) * 1000;
        }
        if (videoCutEnd == 1) {
            endTime = -1;
        } else {
            endTime = (long) (videoCutEnd * videoDuration) * 1000;
        }

        String videoDimension = String.format("%dx%d", width, height);
        String videoTimeSize = String.format("%s, ~%s", AndroidUtilities.formatShortDuration((int) (estimatedDuration / 1000)), AndroidUtilities.formatFileSize(estimatedSize));
        currentSubtitle = String.format("%s, %s", videoDimension, videoTimeSize);
        actionBar.beginDelayedTransition();
        actionBar.setSubtitle(muteVideo ? LocaleController.getString("SoundMuted", R.string.SoundMuted) : currentSubtitle);
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
            if (resultHeight == originalHeight && resultWidth == originalWidth) {
                tryStartRequestPreviewOnFinish = false;
                photoProgressViews[0].setProgress(0,photoProgressViews[0].backgroundState == 0 || photoProgressViews[0].previousBackgroundState == 0);
                photoProgressViews[0].setBackgroundState(PROGRESS_PLAY, false, true);
                if (!wasRequestingPreview) {
                    preparePlayer(currentPlayingVideoFile, false, false, editState.savedFilterState);
                    videoPlayer.seekTo((long) (videoTimelineView.getLeftProgress() * videoDuration));
                } else {
                    loadInitialVideo = true;
                }
            } else {
                releasePlayer(false);
                if (videoPreviewMessageObject == null) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = 0;
                    message.message = "";
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.action = new TLRPC.TL_messageActionEmpty();
                    message.dialog_id = currentDialogId;
                    videoPreviewMessageObject = new MessageObject(UserConfig.selectedAccount, message, false, false);
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
                videoPreviewMessageObject.videoEditedInfo.needUpdateProgress = true;
                videoPreviewMessageObject.videoEditedInfo.originalDuration = (long) (videoDuration * 1000);

                if (!MediaController.getInstance().scheduleVideoConvert(videoPreviewMessageObject, true)) {
                    tryStartRequestPreviewOnFinish = true;
                }
                requestingPreview = true;

                photoProgressViews[0].setProgress(0,photoProgressViews[0].backgroundState == 0 || photoProgressViews[0].previousBackgroundState == 0);
                photoProgressViews[0].setBackgroundState(PROGRESS_EMPTY, false, true);
            }
        } else {
            tryStartRequestPreviewOnFinish = false;
            photoProgressViews[0].setBackgroundState(PROGRESS_PLAY, false, true);
            if (request == 2) {
                preparePlayer(currentPlayingVideoFile, false, false, editState.savedFilterState);
                videoPlayer.seekTo((long) (videoTimelineView.getLeftProgress() * videoDuration));
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

        if (sendPhotoType == SELECT_TYPE_AVATAR) {
            float scale = Math.max(800.0f / originalWidth, 800.0f / originalHeight);
            resultWidth = Math.round(originalWidth * scale / 2) * 2;
            resultHeight = Math.round(originalHeight * scale / 2) * 2;
        } else {
            float maxSize;
            switch (selectedCompression) {
                case 0:
                    maxSize = 480.0f;
                    break;
                case 1:
                    maxSize = 854.0f;
                    break;
                case 2:
                    maxSize = 1280.0f;
                    break;
                case 3:
                default:
                    maxSize = 1920.0f;
                    break;
            }
            float scale = originalWidth > originalHeight ? maxSize / originalWidth : maxSize / originalHeight;
            if (selectedCompression == compressionsCount - 1 && scale >= 1f) {
                resultWidth = originalWidth;
                resultHeight = originalHeight;
            } else {
                resultWidth = Math.round(originalWidth * scale / 2) * 2;
                resultHeight = Math.round(originalHeight * scale / 2) * 2;
            }
        }

        if (bitrate != 0) {
            if (sendPhotoType == SELECT_TYPE_AVATAR) {
                bitrate = 1560000;
            } else if (resultWidth == originalWidth && resultHeight == originalHeight) {
                bitrate = originalBitrate;
            } else {
                bitrate = MediaController.makeVideoBitrate(originalHeight, originalWidth, originalBitrate, resultHeight, resultWidth);
            }
            videoFramesSize = (long) (bitrate / 8 * videoDuration / 1000);
        }
    }

    private void showQualityView(final boolean show) {
        if (show && textureUploaded && videoSizeSet && !changingTextureView) {
            videoFrameBitmap = videoTextureView.getBitmap();
        }

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
                    ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, 0, AndroidUtilities.dp(152)),
                    ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, 0, AndroidUtilities.dp(152)),
                    ObjectAnimator.ofFloat(bottomLayout, View.TRANSLATION_Y, -AndroidUtilities.dp(48), AndroidUtilities.dp(104))
            );
        } else {
            qualityChooseView.setTag(null);
            qualityChooseViewAnimation.playTogether(
                    ObjectAnimator.ofFloat(qualityChooseView, View.TRANSLATION_Y, 0, AndroidUtilities.dp(166)),
                    ObjectAnimator.ofFloat(qualityPicker, View.TRANSLATION_Y, 0, AndroidUtilities.dp(166)),
                    ObjectAnimator.ofFloat(bottomLayout, View.TRANSLATION_Y, -AndroidUtilities.dp(48), AndroidUtilities.dp(118))
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
                            ObjectAnimator.ofFloat(qualityChooseView, View.TRANSLATION_Y, 0),
                            ObjectAnimator.ofFloat(qualityPicker, View.TRANSLATION_Y, 0),
                            ObjectAnimator.ofFloat(bottomLayout, View.TRANSLATION_Y, -AndroidUtilities.dp(48))
                    );
                } else {
                    qualityChooseView.setVisibility(View.INVISIBLE);
                    qualityPicker.setVisibility(View.INVISIBLE);
                    qualityChooseViewAnimation.playTogether(
                            ObjectAnimator.ofFloat(pickerView, View.TRANSLATION_Y, 0),
                            ObjectAnimator.ofFloat(pickerViewSendButton, View.TRANSLATION_Y, 0),
                            ObjectAnimator.ofFloat(bottomLayout, View.TRANSLATION_Y, -AndroidUtilities.dp(48))
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
                qualityChooseViewAnimation.setInterpolator(AndroidUtilities.decelerateInterpolator);
                qualityChooseViewAnimation.start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                qualityChooseViewAnimation = null;
            }
        });
        qualityChooseViewAnimation.setDuration(200);
        qualityChooseViewAnimation.setInterpolator(AndroidUtilities.accelerateInterpolator);
        qualityChooseViewAnimation.start();

        if (cameraItem.getVisibility() == View.VISIBLE) {
            cameraItem.animate().scaleX(show ? 0.25f : 1f)
                    .scaleY(show ? 0.25f : 1f)
                    .alpha(show ? 0 : 1)
                    .setDuration(200);
        }
        if (muteItem.getVisibility() == View.VISIBLE) {
            muteItem.animate().scaleX(show ? 0.25f : 1f)
                    .scaleY(show ? 0.25f : 1f)
                    .alpha(show ? 0 : 1)
                    .setDuration(200);
        }
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

    private void processOpenVideo(final String videoPath, boolean muted, float start, float end) {
        if (currentLoadingVideoRunnable != null) {
            Utilities.globalQueue.cancelRunnable(currentLoadingVideoRunnable);
            currentLoadingVideoRunnable = null;
        }
        videoTimelineView.setVideoPath(videoPath, start, end);
        videoPreviewMessageObject = null;
        muteVideo = muted || sendPhotoType == SELECT_TYPE_AVATAR;

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
                int videoBitrate = MediaController.getVideoBitrate(videoPath);

                int[] params = new int[AnimatedFileDrawable.PARAM_NUM_COUNT];
                AnimatedFileDrawable.getVideoInfo(videoPath, params);
                if (currentLoadingVideoRunnable != this) {
                    return;
                }
                Runnable thisFinal = this;
                AndroidUtilities.runOnUIThread(() -> {
                    if (parentActivity == null || thisFinal != currentLoadingVideoRunnable) {
                        return;
                    }
                    currentLoadingVideoRunnable = null;
                    boolean hasAudio = params[AnimatedFileDrawable.PARAM_NUM_HAS_AUDIO] != 0;
                    videoConvertSupported = params[AnimatedFileDrawable.PARAM_NUM_SUPPORTED_VIDEO_CODEC] != 0 &&
                            (!hasAudio || params[AnimatedFileDrawable.PARAM_NUM_SUPPORTED_AUDIO_CODEC] != 0);
                    audioFramesSize = params[AnimatedFileDrawable.PARAM_NUM_AUDIO_FRAME_SIZE];
                    videoDuration = params[AnimatedFileDrawable.PARAM_NUM_DURATION];
                    if (videoBitrate == -1) {
                        originalBitrate = bitrate = params[AnimatedFileDrawable.PARAM_NUM_BITRATE];
                    } else {
                        originalBitrate = bitrate = videoBitrate;
                    }
                    videoFramerate = params[AnimatedFileDrawable.PARAM_NUM_FRAMERATE];
                    videoFramesSize = (long) (bitrate / 8 * videoDuration / 1000);

                    if (videoConvertSupported) {
                        rotationValue = params[AnimatedFileDrawable.PARAM_NUM_ROTATION];
                        resultWidth = originalWidth = params[AnimatedFileDrawable.PARAM_NUM_WIDTH];
                        resultHeight = originalHeight = params[AnimatedFileDrawable.PARAM_NUM_HEIGHT];

                        updateCompressionsCount(originalWidth, originalHeight);
                        selectedCompression = selectCompression();
                        updateWidthHeightBitrateForCompression();

                        if (selectedCompression > compressionsCount - 1) {
                            selectedCompression = compressionsCount - 1;
                        }

                        setCompressItemEnabled(compressionsCount > 1, true);
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("compressionsCount = " + compressionsCount + " w = " + originalWidth + " h = " + originalHeight + " r = " + rotationValue);
                        }
                        if (Build.VERSION.SDK_INT < 18 && compressItem.getTag() != null) {
                            videoConvertSupported = false;
                            setCompressItemEnabled(false, true);
                        }
                        qualityChooseView.invalidate();
                    } else {
                        setCompressItemEnabled(false, true);
                        compressionsCount = 0;
                    }

                    updateVideoInfo();
                    updateMuteButton();
                });
            }
        });
    }

    private int selectCompression() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        int compressionsCount = this.compressionsCount;
        int maxCompression = 2;
        while (compressionsCount < 5) {
            int selectedCompression = preferences.getInt(String.format(Locale.US, "compress_video_%d", compressionsCount), -1);
            if (selectedCompression >= 0) {
                return Math.min(selectedCompression, maxCompression);
            }
            compressionsCount++;
        }
        return Math.min(maxCompression, Math.round(DownloadController.getInstance(currentAccount).getMaxVideoBitrate() / (100f / compressionsCount)) - 1);
    }

    private void updateCompressionsCount(int h, int w) {
        int maxSize = Math.max(h, w);
        if (maxSize > 1280) {
            compressionsCount = 4;
        } else if (maxSize > 854) {
            compressionsCount = 3;
        } else if (maxSize > 640) {
            compressionsCount = 2;
        } else {
            compressionsCount = 1;
        }
    }

    private void setCompressItemEnabled(boolean enabled, boolean animated) {
        if (compressItem == null) {
            return;
        }
        if (enabled && compressItem.getTag() != null || !enabled && compressItem.getTag() == null) {
            return;
        }
        compressItem.setTag(enabled ? 1 : null);
        if (compressItemAnimation != null) {
            compressItemAnimation.cancel();
            compressItemAnimation = null;
        }
        if (animated) {
            compressItemAnimation = new AnimatorSet();
            compressItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(compressItem, View.ALPHA, enabled ? 1.0f : 0.5f),
                    ObjectAnimator.ofFloat(paintItem, View.ALPHA, videoConvertSupported ? 1.0f : 0.5f),
                    ObjectAnimator.ofFloat(tuneItem, View.ALPHA, videoConvertSupported ? 1.0f : 0.5f),
                    ObjectAnimator.ofFloat(cropItem, View.ALPHA, videoConvertSupported ? 1.0f : 0.5f));
            compressItemAnimation.setDuration(180);
            compressItemAnimation.setInterpolator(decelerateInterpolator);
            compressItemAnimation.start();
        } else {
            compressItem.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    private void updateAccessibilityOverlayVisibility() {
        if (playButtonAccessibilityOverlay != null) {
            final int state = photoProgressViews[0].backgroundState;
            if (photoProgressViews[0].isVisible() && (state == PROGRESS_PLAY || state == PROGRESS_PAUSE)) {
                if (state == PROGRESS_PLAY) {
                    playButtonAccessibilityOverlay.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
                } else {
                    playButtonAccessibilityOverlay.setContentDescription(LocaleController.getString("AccActionPause", R.string.AccActionPause));
                }
                playButtonAccessibilityOverlay.setVisibility(View.VISIBLE);
            } else {
                playButtonAccessibilityOverlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            if (placeProvider != null && placeProvider.getSelectedPhotosOrder() != null) {
                return placeProvider.getSelectedPhotosOrder().size();
            }
            return 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PhotoPickerPhotoCell cell = new PhotoPickerPhotoCell(mContext);
            cell.checkFrame.setOnClickListener(v -> {
                Object photoEntry = ((View) v.getParent()).getTag();
                int idx = imagesArrLocals.indexOf(photoEntry);
                if (idx >= 0) {
                    int num = placeProvider.setPhotoChecked(idx, getCurrentVideoEditedInfo());
                    boolean checked = placeProvider.isPhotoChecked(idx);
                    if (idx == currentIndex) {
                        checkImageView.setChecked(-1, false, true);
                    }
                    if (num >= 0) {
                        selectedPhotosAdapter.notifyItemRemoved(num);
                        if (num == 0) {
                            selectedPhotosAdapter.notifyItemChanged(0);
                        }
                    }
                    updateSelectedCount();
                } else {
                    int num = placeProvider.setPhotoUnchecked(photoEntry);
                    if (num >= 0) {
                        selectedPhotosAdapter.notifyItemRemoved(num);
                        if (num == 0) {
                            selectedPhotosAdapter.notifyItemChanged(0);
                        }
                        updateSelectedCount();
                    }
                }
            });
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) holder.itemView;
            cell.setItemWidth(AndroidUtilities.dp(85), position != 0 ? AndroidUtilities.dp(6) : 0);
            BackupImageView imageView = cell.imageView;
            boolean showing;
            imageView.setOrientation(0, true);
            ArrayList<Object> order = placeProvider.getSelectedPhotosOrder();
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
                        cell.videoTextView.setText(AndroidUtilities.formatShortDuration(photoEntry.duration));
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
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
